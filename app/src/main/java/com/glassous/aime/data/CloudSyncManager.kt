package com.glassous.aime.data

import com.glassous.aime.data.dao.ModelConfigDao
import com.glassous.aime.data.model.BackupConversation
import com.glassous.aime.data.model.BackupData
import com.glassous.aime.data.model.BackupMessage
import com.glassous.aime.data.model.Model
import com.glassous.aime.data.model.ModelGroup
import com.glassous.aime.data.preferences.AuthPreferences
import com.glassous.aime.data.preferences.ModelPreferences
import com.glassous.aime.data.preferences.SyncPreferences
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.Date

class CloudSyncManager(
    private val chatDao: ChatDao,
    private val modelDao: ModelConfigDao,
    private val modelPreferences: ModelPreferences,
    private val authPreferences: AuthPreferences,
    private val syncPreferences: SyncPreferences,
    private val supabaseService: SupabaseAuthService = SupabaseAuthService()
) {
    private val scope = CoroutineScope(Dispatchers.IO)
    private val mutex = Mutex()
    private val gson = Gson()

    @Volatile
    private var runningJob: Job? = null

    fun onTokenChanged(token: String?) {
        runningJob?.cancel()
        if (token.isNullOrBlank()) return
        runningJob = scope.launch {
            // 启动时稍微延迟一点点，避免和应用启动时的密集IO抢资源
            delay(1000)
            syncOnce()
            while (isActive) {
                delay(10 * 60 * 1000L) // 每10分钟自动同步
                syncOnce()
            }
        }
    }

    // 手动同步方法，用于测试和用户主动同步
    suspend fun manualSync(): Pair<Boolean, String> {
        return try {
            android.util.Log.d("CloudSyncManager", "Manual sync initiated")
            syncOnce()
            android.util.Log.d("CloudSyncManager", "Manual sync completed")
            true to "同步完成"
        } catch (e: Exception) {
            android.util.Log.e("CloudSyncManager", "Manual sync failed", e)
            false to "同步失败：${e.message}"
        }
    }

    suspend fun syncOnce() {
        val token = authPreferences.accessToken.first()
        if (token.isNullOrBlank()) {
            android.util.Log.w("CloudSyncManager", "Sync skipped: No access token available")
            return
        }

        android.util.Log.d("CloudSyncManager", "Starting sync process")

        withContext(Dispatchers.IO) {
            mutex.withLock {
                try {
                    // 1. 下载云端备份
                    android.util.Log.d("CloudSyncManager", "Step 1: Downloading remote backup")
                    val downloadResult = supabaseService.downloadBackup(token)
                    val okDownload = downloadResult.first
                    val remoteJson = downloadResult.third

                    // 2. 如果下载成功且有数据，执行"增量合并"
                    if (okDownload && !remoteJson.isNullOrBlank()) {
                        val remoteBackup = try {
                            gson.fromJson(remoteJson, BackupData::class.java)
                        } catch (e: Exception) {
                            null
                        }
                        if (remoteBackup != null) {
                            mergeRemoteIntoLocal(remoteBackup)
                        }
                    }

                    // 3. 收集目前最新的本地数据
                    val localAfterMerge = collectLocalBackup()
                    val localBackupJson = gson.toJson(localAfterMerge)

                    // 4. 获取同步开关设置
                    val uploadHistory = syncPreferences.uploadHistoryEnabled.first()
                    val uploadModelConfig = syncPreferences.uploadModelConfigEnabled.first()
                    val uploadSelectedModel = syncPreferences.uploadSelectedModelEnabled.first()
                    val uploadApiKey = syncPreferences.uploadApiKeyEnabled.first()

                    // 5. 上传合并后的完整数据到云端
                    android.util.Log.d("CloudSyncManager", "Step 4: Uploading backup to cloud")
                    val uploadResult = supabaseService.uploadBackup(
                        token = token,
                        backupDataJson = localBackupJson,
                        syncHistory = uploadHistory,
                        syncModelConfig = uploadModelConfig,
                        syncSelectedModel = uploadSelectedModel,
                        syncApiKey = uploadApiKey
                    )

                    if (uploadResult.first) {
                        android.util.Log.i("CloudSyncManager", "Sync completed successfully")
                    } else {
                        android.util.Log.w("CloudSyncManager", "Sync upload failed: ${uploadResult.second}")
                    }

                } catch (e: Exception) {
                    android.util.Log.e("CloudSyncManager", "Sync failed with exception", e)
                }
            }
        }
    }

    private suspend fun collectLocalBackup(): BackupData {
        val groups = modelDao.getAllGroups().first()
        val models = mutableListOf<Model>()
        groups.forEach { g ->
            val ms = modelDao.getModelsByGroupId(g.id).first()
            models.addAll(ms)
        }

        val selectedModelId = modelPreferences.selectedModelId.first()
        val conversations = chatDao.getAllConversations().first()
        val backupConversations = mutableListOf<BackupConversation>()

        for (c in conversations) {
            val msgs = chatDao.getMessagesForConversation(c.id).first()
            val backupMessages = msgs.map { m ->
                BackupMessage(
                    content = m.content,
                    isFromUser = m.isFromUser,
                    timestamp = m.timestamp.time,
                    isError = m.isError
                )
            }
            backupConversations.add(
                BackupConversation(
                    title = c.title,
                    lastMessage = c.lastMessage,
                    lastMessageTime = c.lastMessageTime.time,
                    messageCount = backupMessages.size,
                    messages = backupMessages
                )
            )
        }

        return BackupData(
            version = 1,
            exportedAt = System.currentTimeMillis(),
            modelGroups = groups,
            models = models,
            selectedModelId = selectedModelId,
            conversations = backupConversations
        )
    }

    private suspend fun mergeRemoteIntoLocal(remote: BackupData) {
        // 1. 合并模型分组
        val localGroups = modelDao.getAllGroups().first()
        val nameToGroup = localGroups.associateBy { it.name }.toMutableMap()

        remote.modelGroups.forEach { rg ->
            val existing = nameToGroup[rg.name]
            val targetGroupId = if (existing == null) {
                modelDao.insertGroup(rg)
                nameToGroup[rg.name] = rg
                rg.id
            } else {
                existing.id
            }

            val localModels = modelDao.getModelsByGroupId(targetGroupId).first()
            val nameToModel = localModels.associateBy { it.name }.toMutableMap()

            remote.models.filter { it.groupId == rg.id }.forEach { rm ->
                if (nameToModel[rm.name] == null && nameToModel.values.none { it.modelName == rm.modelName }) {
                    val newModel = rm.copy(groupId = targetGroupId)
                    modelDao.insertModel(newModel)
                    nameToModel[rm.name] = newModel
                }
            }
        }

        // 2. 合并选中的模型
        val currentSelected = modelPreferences.selectedModelId.first()
        if (currentSelected.isNullOrBlank() && !remote.selectedModelId.isNullOrBlank()) {
            try { modelPreferences.setSelectedModelId(remote.selectedModelId) } catch (_: Exception) { }
        }

        // 3. 合并会话
        val localConversations = chatDao.getAllConversations().first().toMutableList()

        remote.conversations.forEach { rc ->
            if (rc.messageCount == 0 && rc.messages.isEmpty()) return@forEach

            val matched = localConversations.firstOrNull { lc -> lc.title == rc.title }

            val convId = if (matched == null) {
                val newId = chatDao.insertConversation(
                    com.glassous.aime.data.Conversation(
                        title = rc.title,
                        lastMessage = rc.lastMessage,
                        lastMessageTime = Date(rc.lastMessageTime),
                        messageCount = rc.messageCount
                    )
                )
                localConversations.add(
                    com.glassous.aime.data.Conversation(
                        id = newId,
                        title = rc.title,
                        lastMessage = rc.lastMessage,
                        lastMessageTime = Date(rc.lastMessageTime),
                        messageCount = rc.messageCount
                    )
                )
                newId
            } else {
                matched.id
            }

            val localMessages = chatDao.getMessagesForConversation(convId).first()
            val localMessageKeys = localMessages.map { msg ->
                "${msg.content.trim()}_${msg.timestamp.time / 1000}_${msg.isFromUser}"
            }.toSet()

            var hasNewMessage = false
            rc.messages.forEach { rm ->
                val rmKey = "${rm.content.trim()}_${rm.timestamp / 1000}_${rm.isFromUser}"
                if (rmKey !in localMessageKeys) {
                    chatDao.insertMessage(
                        com.glassous.aime.data.ChatMessage(
                            conversationId = convId,
                            content = rm.content,
                            isFromUser = rm.isFromUser,
                            timestamp = Date(rm.timestamp),
                            isError = rm.isError ?: false
                        )
                    )
                    hasNewMessage = true
                }
            }

            if (hasNewMessage) {
                val last = chatDao.getLastMessage(convId)
                val count = chatDao.getMessageCount(convId)
                val current = chatDao.getConversation(convId)
                if (current != null && last != null) {
                    if (last.timestamp.time > current.lastMessageTime.time) {
                        chatDao.updateConversation(
                            current.copy(
                                lastMessage = last.content,
                                lastMessageTime = last.timestamp,
                                messageCount = count
                            )
                        )
                    } else {
                        chatDao.updateConversation(current.copy(messageCount = count))
                    }
                }
            }
        }
    }
}