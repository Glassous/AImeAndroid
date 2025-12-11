package com.glassous.aime.data

import com.glassous.aime.data.dao.ModelConfigDao
import com.glassous.aime.data.model.BackupConversation
import com.glassous.aime.data.model.BackupData
import com.glassous.aime.data.model.BackupMessage
import com.glassous.aime.data.model.Model
import com.glassous.aime.data.model.ModelGroup
import com.glassous.aime.data.preferences.AuthPreferences
import com.glassous.aime.data.preferences.ModelPreferences
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
import java.util.Date
import kotlin.math.abs

class CloudSyncManager(
    private val chatDao: ChatDao,
    private val modelDao: ModelConfigDao,
    private val modelPreferences: ModelPreferences,
    private val authPreferences: AuthPreferences,
    private val syncPreferences: com.glassous.aime.data.preferences.SyncPreferences,
    private val supabaseService: SupabaseAuthService = SupabaseAuthService()
) {
    private val scope = CoroutineScope(Dispatchers.IO)
    private val mutex = Mutex()
    private val gson = Gson()

    @Volatile
    private var runningJob: Job? = null

    fun onTokenChanged(token: String?) {
        runningJob?.cancel()
        // 自动同步已移除，仅保留手动同步
    }

    // 手动同步方法，用于测试和用户主动同步
    suspend fun manualSync(): Pair<Boolean, String> {
        return try {
            android.util.Log.d("CloudSyncManager", "Manual sync initiated")
            val result = syncOnce()
            android.util.Log.d("CloudSyncManager", "Manual sync completed with result: $result")
            result
        } catch (e: Exception) {
            android.util.Log.e("CloudSyncManager", "Manual sync failed", e)
            false to "同步失败：${e.message}"
        }
    }

    suspend fun syncUploadOnly(forceAll: Boolean = false): Pair<Boolean, String> {
        val token = authPreferences.accessToken.first()
        if (token.isNullOrBlank()) {
            return false to "未登录，无法上传"
        }

        return try {
             kotlinx.coroutines.withContext(Dispatchers.IO) {
                mutex.withLock {
                     android.util.Log.d("CloudSyncManager", "Starting Upload Only... Token length: ${token.length}")
                     
                     // 3. 收集目前最新的本地数据
                     val localAfterMerge = collectLocalBackup()
                     val localBackupJson = gson.toJson(localAfterMerge)
                     
                    // 4. 获取同步开关设置
                    val uploadHistory = if (forceAll) true else syncPreferences.uploadHistoryEnabled.first()
                    val uploadModelConfig = if (forceAll) true else syncPreferences.uploadModelConfigEnabled.first()
                    val uploadSelectedModel = if (forceAll) true else syncPreferences.uploadSelectedModelEnabled.first()
                    val uploadApiKey = if (forceAll) true else syncPreferences.uploadApiKeyEnabled.first()

                    // 5. 上传
                    val (okUpload, uploadMsg) = supabaseService.uploadBackup(
                        token = token,
                        backupDataJson = localBackupJson,
                        syncHistory = uploadHistory,
                        syncModelConfig = uploadModelConfig,
                        syncSelectedModel = uploadSelectedModel,
                        syncApiKey = uploadApiKey
                    )
                    
                    if (okUpload) {
                        true to "仅上传成功"
                    } else {
                        if (uploadMsg.contains("无效会话") || uploadMsg.contains("Token过期")) {
                            false to "登录失效，请退出并重新登录"
                        } else {
                            false to "上传失败: $uploadMsg"
                        }
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("CloudSyncManager", "Upload Only failed", e)
            false to "上传异常: ${e.message}"
        }
    }

    suspend fun syncDownloadOnly(): Pair<Boolean, String> {
        val token = authPreferences.accessToken.first()
        if (token.isNullOrBlank()) {
            return false to "未登录，无法下载"
        }

        return try {
            kotlinx.coroutines.withContext(Dispatchers.IO) {
                mutex.withLock {
                    android.util.Log.d("CloudSyncManager", "Starting Download Only... Token length: ${token.length}")
                    
                    // 1. 下载云端备份
                    val (okDownload, downloadMsg, remoteJson) = supabaseService.downloadBackup(token)
                    
                    if (okDownload && !remoteJson.isNullOrBlank()) {
                         // 2. 合并
                        val remoteBackup = try {
                            gson.fromJson(remoteJson, BackupData::class.java)
                        } catch (e: Exception) {
                            null
                        }
                        
                        if (remoteBackup != null) {
                            mergeRemoteIntoLocal(remoteBackup)
                            true to "仅下载并合并成功"
                        } else {
                            false to "下载成功但解析失败"
                        }
                    } else {
                        if (downloadMsg.contains("无效会话") || downloadMsg.contains("Token过期")) {
                            false to "登录失效，请退出并重新登录"
                        } else {
                            if (!okDownload) false to "下载失败: $downloadMsg" else true to "云端无数据"
                        }
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("CloudSyncManager", "Download Only failed", e)
            false to "下载异常: ${e.message}"
        }
    }

    suspend fun syncOnce(forceAll: Boolean = false): Pair<Boolean, String> {
        val token = authPreferences.accessToken.first()
        if (token.isNullOrBlank()) {
            android.util.Log.w("CloudSyncManager", "Sync skipped: No access token available")
            return false to "未登录"
        }

        android.util.Log.d("CloudSyncManager", "Starting sync process")

        // 使用 IO 线程执行，避免阻塞主线程
        return kotlinx.coroutines.withContext(Dispatchers.IO) {
            mutex.withLock {
                try {
                    // 1. 下载云端备份
                    android.util.Log.d("CloudSyncManager", "Step 1: Downloading remote backup")
                    val (okDownload, downloadMsg, remoteJson) = supabaseService.downloadBackup(token)
                    android.util.Log.d("CloudSyncManager", "Download result: ok=$okDownload, message=$downloadMsg, hasData=${!remoteJson.isNullOrBlank()}")

                    if (!okDownload && (downloadMsg.contains("无效会话") || downloadMsg.contains("Token过期"))) {
                        return@withLock false to "设备登录已过期，只能同时登录6个设备，请重新登录"
                    }

                    // 2. 如果下载成功且有数据，执行"增量合并"到本地数据库
                    // 关键点：这里不再清空本地数据，而是将云端的新数据插入本地
                    if (okDownload && !remoteJson.isNullOrBlank()) {
                        android.util.Log.d("CloudSyncManager", "Step 2: Parsing and merging remote data")
                        val remoteBackup = try {
                            gson.fromJson(remoteJson, BackupData::class.java)
                        } catch (e: Exception) {
                            android.util.Log.e("CloudSyncManager", "Failed to parse remote backup JSON", e)
                            null
                        }
                        if (remoteBackup != null) {
                            android.util.Log.d("CloudSyncManager", "Merging remote data: conversations=${remoteBackup.conversations.size}, modelGroups=${remoteBackup.modelGroups.size}, models=${remoteBackup.models.size}")

                            // 记录合并前的数据库状态
                            val groupsBefore = modelDao.getAllGroups().first().size
                            val conversationsBefore = chatDao.getAllConversations().first().size
                            android.util.Log.d("CloudSyncManager", "Database before merge: groups=$groupsBefore, conversations=$conversationsBefore")

                            mergeRemoteIntoLocal(remoteBackup)

                            // 记录合并后的数据库状态
                            val groupsAfter = modelDao.getAllGroups().first().size
                            val conversationsAfter = chatDao.getAllConversations().first().size
                            android.util.Log.d("CloudSyncManager", "Database after merge: groups=$groupsAfter, conversations=$conversationsAfter")
                            android.util.Log.d("CloudSyncManager", "Merge result: groups added=${groupsAfter - groupsBefore}, conversations added=${conversationsAfter - conversationsBefore}")

                            android.util.Log.d("CloudSyncManager", "Remote data merged successfully")
                        } else {
                            android.util.Log.w("CloudSyncManager", "Skipping merge due to JSON parsing failure")
                        }
                    } else {
                        android.util.Log.w("CloudSyncManager", "Skipping merge: download failed or no data")
                    }

                    // 3. 收集目前最新的本地数据（包含了刚才合并进来的云端数据 + 用户刚才新产生的操作）
                     android.util.Log.d("CloudSyncManager", "Step 3: Collecting local backup data")

                     // 验证数据库中是否有数据
                     val dbGroups = modelDao.getAllGroups().first()
                     val dbConversations = chatDao.getAllConversations().first()
                     android.util.Log.d("CloudSyncManager", "Database state before collect: groups=${dbGroups.size}, conversations=${dbConversations.size}")

                     val localAfterMerge = collectLocalBackup()
                     val localBackupJson = gson.toJson(localAfterMerge)
                     android.util.Log.d("CloudSyncManager", "Local data collected: conversations=${localAfterMerge.conversations.size}, modelGroups=${localAfterMerge.modelGroups.size}, models=${localAfterMerge.models.size}")
                     android.util.Log.d("CloudSyncManager", "Local backup JSON length: ${localBackupJson.length}")
                     android.util.Log.d("CloudSyncManager", "Local backup JSON preview: ${localBackupJson.take(500)}")

                     // 再次验证数据库状态
                     val dbGroupsAfter = modelDao.getAllGroups().first()
                     val dbConversationsAfter = chatDao.getAllConversations().first()
                     android.util.Log.d("CloudSyncManager", "Database state after collect: groups=${dbGroupsAfter.size}, conversations=${dbConversationsAfter.size}")

                    // 4. 获取同步开关设置
                    val uploadHistory = if (forceAll) true else syncPreferences.uploadHistoryEnabled.first()
                    val uploadModelConfig = if (forceAll) true else syncPreferences.uploadModelConfigEnabled.first()
                    val uploadSelectedModel = if (forceAll) true else syncPreferences.uploadSelectedModelEnabled.first()
                    val uploadApiKey = if (forceAll) true else syncPreferences.uploadApiKeyEnabled.first()
                    android.util.Log.d("CloudSyncManager", "Sync settings: history=$uploadHistory, modelConfig=$uploadModelConfig, selectedModel=$uploadSelectedModel, apiKey=$uploadApiKey")

                    // 5. 上传合并后的完整数据到云端（作为新的基准）
                    android.util.Log.d("CloudSyncManager", "Step 4: Uploading backup to cloud")
                    val (okUpload, uploadMsg) = supabaseService.uploadBackup(
                        token = token,
                        backupDataJson = localBackupJson,
                        syncHistory = uploadHistory,
                        syncModelConfig = uploadModelConfig,
                        syncSelectedModel = uploadSelectedModel,
                        syncApiKey = uploadApiKey
                    )
                    android.util.Log.d("CloudSyncManager", "Upload result: ok=$okUpload, message=$uploadMsg")

                    if (okUpload) {
                        android.util.Log.i("CloudSyncManager", "Sync completed successfully")
                        true to "同步完成"
                    } else {
                        android.util.Log.w("CloudSyncManager", "Sync completed with upload failure: $uploadMsg")
                        if (uploadMsg.contains("无效会话") || uploadMsg.contains("Token过期")) {
                            false to "设备登录已过期，只能同时登录6个设备，请重新登录"
                        } else {
                            false to "同步失败: 上传出错 ($uploadMsg)"
                        }
                    }

                    // 移除：原来的 "upload 后再次 download 并 overwrite" 的逻辑
                    // 这样就避免了用户在上传间隙的操作被覆盖
                } catch (e: Exception) {
                    android.util.Log.e("CloudSyncManager", "Sync failed with exception", e)
                    // 可以在这里添加日志或不需要处理，保持静默失败以免打扰用户
                    false to "同步异常: ${e.message}"
                }
            }
        }
    }

    // ... collectLocalBackup 方法保持不变 ...
    private suspend fun collectLocalBackup(): BackupData {
        android.util.Log.d("CloudSyncManager", "Collecting local backup data...")

        val groups = modelDao.getAllGroupsIncludingDeleted().first()
        android.util.Log.d("CloudSyncManager", "Found ${groups.size} model groups")

        val models = mutableListOf<Model>()
        groups.forEach { g ->
            val ms = modelDao.getModelsByGroupIdIncludingDeleted(g.id).first()
            models.addAll(ms)
            android.util.Log.d("CloudSyncManager", "Group ${g.name}: ${ms.size} models")
        }
        android.util.Log.d("CloudSyncManager", "Total models: ${models.size}")

        val selectedModelId = modelPreferences.selectedModelId.first()
        android.util.Log.d("CloudSyncManager", "Selected model ID: $selectedModelId")

        val conversations = chatDao.getAllConversationsIncludingDeleted().first()
        android.util.Log.d("CloudSyncManager", "Found ${conversations.size} conversations")

        val backupConversations = mutableListOf<BackupConversation>()
        for (c in conversations) {
            val msgs = chatDao.getMessagesForConversationIncludingDeleted(c.id).first()
            android.util.Log.d("CloudSyncManager", "Conversation '${c.title}': ${msgs.size} messages")

            val backupMessages = msgs.map { m ->
                BackupMessage(
                    content = m.content,
                    isFromUser = m.isFromUser,
                    timestamp = m.timestamp.time,
                    isError = m.isError,
                    modelDisplayName = m.modelDisplayName
                )
            }
            backupConversations.add(
                BackupConversation(
                    title = c.title,
                    uuid = c.uuid,
                    lastMessage = c.lastMessage,
                    lastMessageTime = c.lastMessageTime.time,
                    messageCount = backupMessages.size,
                    messages = backupMessages,
                    isDeleted = c.isDeleted,
                    deletedAt = c.deletedAt?.time
                )
            )
        }

        val apiKeys = groups.map { com.glassous.aime.data.model.ApiKey(platform = it.name, apiKey = it.apiKey) }
            .filter { it.apiKey.isNotBlank() }

        val backupData = BackupData(
            version = 1,
            exportedAt = System.currentTimeMillis(),
            modelGroups = groups,
            models = models,
            selectedModelId = selectedModelId,
            conversations = backupConversations,
            apiKeys = apiKeys
        )

        android.util.Log.d("CloudSyncManager", "Local backup collected: version=${backupData.version}, exportedAt=${backupData.exportedAt}")
        return backupData
    }

    // 优化后的合并逻辑：只增加，不删除，不覆盖已存在的（除非我们引入版本号对比）
    private suspend fun mergeRemoteIntoLocal(remote: BackupData) {
        android.util.Log.d("CloudSyncManager", "Starting merge remote into local: remote conversations=${remote.conversations.size}, modelGroups=${remote.modelGroups.size}, models=${remote.models.size}")

        val localGroupsAll = modelDao.getAllGroupsIncludingDeleted().first()
        val deletedGroupIds = localGroupsAll.filter { it.isDeleted }.map { it.id }.toSet()
        val deletedModelIds = mutableSetOf<String>()
        localGroupsAll.forEach { g ->
            val msAll = modelDao.getModelsByGroupIdIncludingDeleted(g.id).first()
            msAll.filter { it.isDeleted }.forEach { deletedModelIds.add(it.id) }
        }
        val localConversationsAll = chatDao.getAllConversationsIncludingDeleted().first()
        val deletedConversationTitles = localConversationsAll.filter { it.isDeleted }.map { it.title }.toSet()

        // 1. 合并模型分组
        val localGroups = modelDao.getAllGroups().first().toMutableList()
        android.util.Log.d("CloudSyncManager", "Local groups before merge: ${localGroups.size}")
        val nameToGroup = localGroups.associateBy { it.name }.toMutableMap()

        remote.modelGroups.forEach { rg ->
            if (deletedGroupIds.contains(rg.id)) {
                android.util.Log.d("CloudSyncManager", "Skip remote group due to local tombstone: ${rg.name}")
                return@forEach
            }
            val existing = nameToGroup[rg.name]
            val targetGroupId = if (existing == null) {
                // 本地没有这个组，插入
                android.util.Log.d("CloudSyncManager", "Inserting new model group: ${rg.name}")
                modelDao.insertGroup(rg)
                nameToGroup[rg.name] = rg
                rg.id
            } else {
                // 本地已有同名组，使用本地的 ID
                android.util.Log.d("CloudSyncManager", "Using existing model group: ${rg.name} (id: ${existing.id})")
                existing.id
            }

            // 合并模型
            val localModels = modelDao.getModelsByGroupId(targetGroupId).first()
            android.util.Log.d("CloudSyncManager", "Local models in group ${rg.name}: ${localModels.size}")
            val nameToModel = localModels.associateBy { it.name }.toMutableMap() // 使用 name 或 modelName 判重

            remote.models.filter { it.groupId == rg.id }.forEach { rm ->
                if (deletedModelIds.contains(rm.id)) {
                    android.util.Log.d("CloudSyncManager", "Skip remote model due to local tombstone: ${rm.name}")
                    return@forEach
                }
                // 只有当本地没有这个模型时才插入
                if (nameToModel[rm.name] == null && nameToModel.values.none { it.modelName == rm.modelName }) {
                    android.util.Log.d("CloudSyncManager", "Inserting new model: ${rm.name} (${rm.modelName})")
                    val newModel = rm.copy(groupId = targetGroupId)
                    modelDao.insertModel(newModel)
                    nameToModel[rm.name] = newModel
                } else {
                    android.util.Log.d("CloudSyncManager", "Skipping existing model: ${rm.name}")
                }
            }
        }

        if (remote.apiKeys != null) {
            val currentGroups = modelDao.getAllGroups().first()
            remote.apiKeys.forEach { ak ->
                val grp = currentGroups.find { it.name == ak.platform }
                if (grp != null && grp.apiKey.isBlank()) {
                    modelDao.updateGroup(grp.copy(apiKey = ak.apiKey))
                }
            }
        }

        // 2. 合并选中的模型 (仅当本地未选择时才采纳云端，避免覆盖用户刚切换的模型)
        val currentSelected = modelPreferences.selectedModelId.first()
        if (currentSelected.isNullOrBlank() && !remote.selectedModelId.isNullOrBlank()) {
             // 注意：这里需要确保 remote.selectedModelId 对应的模型已经在上面被插入到数据库了
             // 简单起见，如果云端有记录且本地为空，尝试恢复
             try { modelPreferences.setSelectedModelId(remote.selectedModelId) } catch (_: Exception) { }
        }

        // 3. 合并会话
        val localConversations = chatDao.getAllConversations().first().toMutableList()

        remote.conversations.forEach { rc ->
            if (deletedConversationTitles.contains(rc.title)) {
                android.util.Log.d("CloudSyncManager", "Skip remote conversation due to local tombstone: ${rc.title}")
                return@forEach
            }
            // 尝试通过UUID匹配，如果云端没有UUID（旧版备份），则降级到标题匹配
            val matched = localConversations.firstOrNull { lc ->
                if (!rc.uuid.isNullOrBlank()) {
                    lc.uuid == rc.uuid
                } else {
                    lc.title == rc.title
                }
            }

            val convId = if (matched == null) {
                // 本地完全没有这个会话 -> 插入新会话
                // 只插入有消息的会话，避免应用启动时产生空白对话记录
                if (rc.messageCount > 0 || rc.messages.isNotEmpty()) {
                    android.util.Log.d("CloudSyncManager", "Inserting new conversation: ${rc.title}")
                    val newUuid = rc.uuid ?: java.util.UUID.randomUUID().toString()
                    val newId = chatDao.insertConversation(
                        Conversation(
                            title = rc.title,
                            uuid = newUuid,
                            lastMessage = rc.lastMessage,
                            lastMessageTime = Date(rc.lastMessageTime),
                            messageCount = rc.messageCount
                        )
                    )
                    android.util.Log.d("CloudSyncManager", "Conversation inserted with ID: $newId")

                    // 验证插入是否成功
                    val insertedConv = chatDao.getConversation(newId)
                    if (insertedConv == null) {
                        android.util.Log.e("CloudSyncManager", "Failed to insert conversation: ${rc.title}")
                        return@forEach
                    } else {
                        android.util.Log.d("CloudSyncManager", "Conversation insertion verified: ${insertedConv.title}")
                    }

                    // 更新内存中的列表避免重复插入
                    localConversations.add(
                        Conversation(
                            id = newId,
                            title = rc.title,
                            uuid = newUuid,
                            lastMessage = rc.lastMessage,
                            lastMessageTime = Date(rc.lastMessageTime),
                            messageCount = rc.messageCount
                        )
                    )
                    newId
                } else {
                    // 跳过空会话，避免插入空白对话
                    android.util.Log.d("CloudSyncManager", "Skipping empty remote conversation: ${rc.title}")
                    return@forEach
                }
            } else {
                android.util.Log.d("CloudSyncManager", "Using existing conversation: ${matched.title} (id: ${matched.id})")
                matched.id
            }

            // 合并消息：只插入本地缺失的消息
            val localMessages = chatDao.getMessagesForConversation(convId).first()
            // 使用更可靠的去重方法：内容 + 时间戳（秒级） + 发送方
            val localMessageKeys = localMessages.map { msg ->
                "${msg.content.trim()}_${msg.timestamp.time / 1000}_${msg.isFromUser}"
            }.toSet()
            val localDeletedMessageKeys = chatDao.getMessagesForConversationIncludingDeleted(convId).first()
                .filter { it.isDeleted }
                .map { msg -> "${msg.content.trim()}_${msg.timestamp.time / 1000}_${msg.isFromUser}" }
                .toSet()

            var hasNewMessage = false
            rc.messages.forEach { rm ->
                val rmKey = "${rm.content.trim()}_${rm.timestamp / 1000}_${rm.isFromUser}"

                // 检查是否已存在相同的消息
                if (rmKey !in localMessageKeys && rmKey !in localDeletedMessageKeys) {
                    try {
                        android.util.Log.d("CloudSyncManager", "Attempting to insert message: ${rm.content.take(50)}...")
                        val messageId = chatDao.insertMessage(
                            ChatMessage(
                                conversationId = convId,
                                content = rm.content,
                                isFromUser = rm.isFromUser,
                                timestamp = Date(rm.timestamp),
                                isError = rm.isError ?: false,
                                modelDisplayName = rm.modelDisplayName
                            )
                        )
                        android.util.Log.d("CloudSyncManager", "Message inserted with ID: $messageId")

                        // 验证消息插入是否成功
                        val insertedMsg = chatDao.getMessageById(messageId)
                        if (insertedMsg == null) {
                            android.util.Log.e("CloudSyncManager", "Failed to verify message insertion: ID $messageId")
                        } else {
                            android.util.Log.d("CloudSyncManager", "Message insertion verified: ${insertedMsg.content.take(50)}...")
                            hasNewMessage = true
                        }
                    } catch (e: Exception) {
                        android.util.Log.w("CloudSyncManager", "Failed to insert remote message", e)
                    }
                } else {
                    android.util.Log.d("CloudSyncManager", "Skipped duplicate message: ${rm.content.take(50)}...")
                }
            }

            // 如果插入了新历史消息，可能需要刷新会话的 LastMessage
            if (hasNewMessage) {
                val last = chatDao.getLastMessage(convId)
                val count = chatDao.getMessageCount(convId)
                val current = chatDao.getConversation(convId)
                if (current != null && last != null) {
                    // 只有当云端的最后一条消息比本地现在的更晚时，才更新会话预览
                    // 这样防止把用户刚发的“新消息”的预览覆盖成旧的
                    if (last.timestamp.time > current.lastMessageTime.time) {
                        chatDao.updateConversation(
                            current.copy(
                                lastMessage = last.content,
                                lastMessageTime = last.timestamp,
                                messageCount = count
                            )
                        )
                    } else {
                        // 仅更新计数
                        chatDao.updateConversation(current.copy(messageCount = count))
                    }
                }
            }
        }
    }
}
