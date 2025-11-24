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

    suspend fun syncOnce() {
        val token = authPreferences.accessToken.first() ?: return
        
        // 使用 IO 线程执行，避免阻塞主线程
        kotlinx.coroutines.withContext(Dispatchers.IO) {
            mutex.withLock {
                try {
                    // 1. 下载云端备份
                    val (okDownload, _, remoteJson) = supabaseService.downloadBackup(token)
                    
                    // 2. 如果下载成功且有数据，执行“增量合并”到本地数据库
                    // 关键点：这里不再清空本地数据，而是将云端的新数据插入本地
                    if (okDownload && !remoteJson.isNullOrBlank()) {
                        val remoteBackup = try { gson.fromJson(remoteJson, BackupData::class.java) } catch (_: Exception) { null }
                        if (remoteBackup != null) {
                            mergeRemoteIntoLocal(remoteBackup)
                        }
                    }

                    // 3. 收集目前最新的本地数据（包含了刚才合并进来的云端数据 + 用户刚才新产生的操作）
                    val localAfterMerge = collectLocalBackup()
                    val localBackupJson = gson.toJson(localAfterMerge)

                    // 4. 获取同步开关设置
                    val uploadHistory = syncPreferences.uploadHistoryEnabled.first()
                    val uploadModelConfig = syncPreferences.uploadModelConfigEnabled.first()
                    val uploadSelectedModel = syncPreferences.uploadSelectedModelEnabled.first()
                    val uploadApiKey = syncPreferences.uploadApiKeyEnabled.first()

                    // 5. 上传合并后的完整数据到云端（作为新的基准）
                    supabaseService.uploadBackup(
                        token = token,
                        backupDataJson = localBackupJson,
                        syncHistory = uploadHistory,
                        syncModelConfig = uploadModelConfig,
                        syncSelectedModel = uploadSelectedModel,
                        syncApiKey = uploadApiKey
                    )
                    
                    // 移除：原来的 "upload 后再次 download 并 overwrite" 的逻辑
                    // 这样就避免了用户在上传间隙的操作被覆盖
                } catch (e: Exception) {
                    e.printStackTrace()
                    // 可以在这里添加日志或不需要处理，保持静默失败以免打扰用户
                }
            }
        }
    }

    // ... collectLocalBackup 方法保持不变 ...
    private suspend fun collectLocalBackup(): BackupData {
        // (保持原代码内容不变)
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

    // 优化后的合并逻辑：只增加，不删除，不覆盖已存在的（除非我们引入版本号对比）
    private suspend fun mergeRemoteIntoLocal(remote: BackupData) {
        // 1. 合并模型分组
        val localGroups = modelDao.getAllGroups().first().toMutableList()
        val nameToGroup = localGroups.associateBy { it.name }.toMutableMap()

        remote.modelGroups.forEach { rg ->
            val existing = nameToGroup[rg.name]
            val targetGroupId = if (existing == null) {
                // 本地没有这个组，插入
                modelDao.insertGroup(rg)
                nameToGroup[rg.name] = rg
                rg.id
            } else {
                // 本地已有同名组，使用本地的 ID
                existing.id
            }

            // 合并模型
            val localModels = modelDao.getModelsByGroupId(targetGroupId).first()
            val nameToModel = localModels.associateBy { it.name }.toMutableMap() // 使用 name 或 modelName 判重
            
            remote.models.filter { it.groupId == rg.id }.forEach { rm ->
                // 只有当本地没有这个模型时才插入
                if (nameToModel[rm.name] == null && nameToModel.values.none { it.modelName == rm.modelName }) {
                    val newModel = rm.copy(groupId = targetGroupId)
                    modelDao.insertModel(newModel)
                    nameToModel[rm.name] = newModel
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
            // 尝试通过标题和最后消息时间模糊匹配找到本地对应的会话
            val matched = localConversations.firstOrNull { lc ->
                // 匹配规则：标题相同，或者（如果标题是默认的）内容高度重叠
                // 这里简化为标题相同且时间相差不大，或者是完全一样的内容
                lc.title == rc.title
            }
            
            val convId = if (matched == null) {
                // 本地完全没有这个会话 -> 插入新会话
                val newId = chatDao.insertConversation(
                    Conversation(
                        title = rc.title,
                        lastMessage = rc.lastMessage,
                        lastMessageTime = Date(rc.lastMessageTime),
                        messageCount = rc.messageCount
                    )
                )
                // 更新内存中的列表避免重复插入
                localConversations.add(
                    Conversation(
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

            // 合并消息：只插入本地缺失的消息
            val localMessages = chatDao.getMessagesForConversation(convId).first()
            // 建立本地消息特征指纹 (内容 + 时间戳 + 是否来自用户) 用于去重
            val localFingerprints = localMessages.map { 
                "${it.content.hashCode()}_${it.timestamp.time / 1000}_${it.isFromUser}" 
            }.toSet()

            var hasNewMessage = false
            rc.messages.forEach { rm ->
                val rmFingerprint = "${rm.content.hashCode()}_${rm.timestamp / 1000}_${rm.isFromUser}"
                
                // 放宽时间戳匹配精度（除以1000忽略毫秒差异），或者只比对内容和发送方
                if (rmFingerprint !in localFingerprints) {
                    chatDao.insertMessage(
                        ChatMessage(
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