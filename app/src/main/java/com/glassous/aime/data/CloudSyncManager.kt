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
            syncOnce()
            while (isActive) {
                delay(10 * 60 * 1000L)
                syncOnce()
            }
        }
    }

    suspend fun syncOnce() {
        val token = authPreferences.accessToken.first() ?: return
        kotlinx.coroutines.withContext(Dispatchers.IO) {
        mutex.withLock {
            val localBackup = collectLocalBackup()

            val (okDownload, _, remoteJson) = supabaseService.downloadBackup(token)
            var remoteBefore: BackupData? = null
            if (okDownload && !remoteJson.isNullOrBlank()) {
                remoteBefore = try { gson.fromJson(remoteJson, BackupData::class.java) } catch (_: Exception) { null }
                remoteBefore = remoteBefore?.let { deduplicateRemote(it) }
                if (remoteBefore != null) {
                    val isLocalEmpty = localBackup.modelGroups.isEmpty() && localBackup.conversations.isEmpty()
                    if (isLocalEmpty) {
                        importRemoteIntoLocal(remoteBefore)
                    } else {
                        mergeRemoteIntoLocal(remoteBefore)
                    }
                }
            }
            val localAfterMerge = collectLocalBackup()
            val adjustedForUpload = if (remoteBefore != null) adjustConversationsForUpload(localAfterMerge, remoteBefore) else localAfterMerge
            val localBackupJson = gson.toJson(adjustedForUpload)
            val uploadHistory = syncPreferences.uploadHistoryEnabled.first()
            val uploadModelConfig = syncPreferences.uploadModelConfigEnabled.first()
            val uploadSelectedModel = syncPreferences.uploadSelectedModelEnabled.first()
            val uploadApiKey = syncPreferences.uploadApiKeyEnabled.first()
            val (okUpload, uploadMsg) = supabaseService.uploadBackup(
                token = token,
                backupDataJson = localBackupJson,
                syncHistory = uploadHistory,
                syncModelConfig = uploadModelConfig,
                syncSelectedModel = uploadSelectedModel,
                syncApiKey = uploadApiKey
            )
            if (!okUpload) throw IllegalStateException(uploadMsg)

            val (okDownloadAfter, _, remoteJsonAfter) = supabaseService.downloadBackup(token)
            if (!okDownloadAfter || remoteJsonAfter.isNullOrBlank()) {
                throw IllegalStateException("下载失败：远端数据为空或会话无效")
            }
            var remoteFinal = try { gson.fromJson(remoteJsonAfter, BackupData::class.java) } catch (e: Exception) { throw IllegalStateException("下载数据解析失败：" + (e.message ?: "未知错误")) }
            remoteFinal = deduplicateRemote(remoteFinal)
            overwriteLocalWithRemote(remoteFinal)
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
        val localGroups = modelDao.getAllGroups().first().toMutableList()
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
                if (nameToModel[rm.name] == null) {
                    val newModel = rm.copy(groupId = targetGroupId)
                    modelDao.insertModel(newModel)
                    nameToModel[rm.name] = newModel
                }
            }
        }

        val localConversations = chatDao.getAllConversations().first().toMutableList()
        remote.conversations.forEach { rc ->
            val matched = localConversations.firstOrNull { lc ->
                lc.title == rc.title && abs(lc.lastMessageTime.time - rc.lastMessageTime) < 2000
            }
            val convId = if (matched == null) {
                val newId = chatDao.insertConversation(
                    Conversation(
                        title = rc.title,
                        lastMessage = rc.lastMessage,
                        lastMessageTime = Date(rc.lastMessageTime),
                        messageCount = rc.messageCount
                    )
                )
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
            } else matched.id

            val localMessages = chatDao.getMessagesForConversation(convId).first()
            rc.messages.forEach { rm ->
                val exists = localMessages.any { lm ->
                    lm.content == rm.content && abs(lm.timestamp.time - rm.timestamp) < 2000
                }
                if (!exists) {
                    chatDao.insertMessage(
                        ChatMessage(
                            conversationId = convId,
                            content = rm.content,
                            isFromUser = rm.isFromUser,
                            timestamp = Date(rm.timestamp),
                            isError = rm.isError ?: false
                        )
                    )
                }
            }

            val last = chatDao.getLastMessage(convId)
            val count = chatDao.getMessageCount(convId)
            val current = chatDao.getConversation(convId)
            if (current != null && last != null) {
                chatDao.updateConversation(
                    current.copy(
                        lastMessage = last.content,
                        lastMessageTime = last.timestamp,
                        messageCount = count
                    )
                )
            }
        }
    }

    private suspend fun importRemoteIntoLocal(remote: BackupData) {
        remote.modelGroups.forEach { rg ->
            modelDao.insertGroup(rg)
        }
        remote.models.forEach { rm ->
            modelDao.insertModel(rm)
        }
        remote.selectedModelId?.let { sel ->
            try { modelPreferences.setSelectedModelId(sel) } catch (_: Exception) { }
        }

        remote.conversations.forEach { rc ->
            val newConvId = chatDao.insertConversation(
                Conversation(
                    title = rc.title,
                    lastMessage = rc.lastMessage,
                    lastMessageTime = Date(rc.lastMessageTime),
                    messageCount = rc.messageCount
                )
            )
            rc.messages.forEach { rm ->
                chatDao.insertMessage(
                    ChatMessage(
                        conversationId = newConvId,
                        content = rm.content,
                        isFromUser = rm.isFromUser,
                        timestamp = Date(rm.timestamp),
                        isError = rm.isError ?: false
                    )
                )
            }
            val last = chatDao.getLastMessage(newConvId)
            val count = chatDao.getMessageCount(newConvId)
            val current = chatDao.getConversation(newConvId)
            if (current != null && last != null) {
                chatDao.updateConversation(
                    current.copy(
                        lastMessage = last.content,
                        lastMessageTime = last.timestamp,
                        messageCount = count
                    )
                )
            }
        }
    }

    private fun deduplicateRemote(remote: BackupData): BackupData {
        val convByTitle = mutableMapOf<String, MutableList<BackupConversation>>()
        remote.conversations.forEach { c ->
            convByTitle.getOrPut(c.title) { mutableListOf() }.add(c)
        }
        val mergedConversations = mutableListOf<BackupConversation>()
        convByTitle.forEach { (title, list) ->
            val latest = list.maxByOrNull { it.lastMessageTime } ?: list.first()
            val allMessages = list.flatMap { it.messages }
            val uniq = mutableListOf<BackupMessage>()
            allMessages.forEach { m ->
                val exists = uniq.any { u -> u.content == m.content && kotlin.math.abs(u.timestamp - m.timestamp) < 2000 }
                if (!exists) uniq.add(m)
            }
            mergedConversations.add(
                BackupConversation(
                    title = title,
                    lastMessage = latest.lastMessage,
                    lastMessageTime = latest.lastMessageTime,
                    messageCount = uniq.size,
                    messages = uniq
                )
            )
        }
        return remote.copy(conversations = mergedConversations)
    }

    private fun adjustConversationsForUpload(local: BackupData, remote: BackupData): BackupData {
        val remoteTimeByTitle = remote.conversations.associateBy({ it.title }, { it.lastMessageTime })
        val adjustedConvs = local.conversations.map { c ->
            val remoteTime = remoteTimeByTitle[c.title]
            if (remoteTime != null) c.copy(lastMessageTime = remoteTime) else c
        }
        return local.copy(conversations = adjustedConvs)
    }

    private suspend fun overwriteLocalWithRemote(remote: BackupData) {
        chatDao.deleteAllMessages()
        chatDao.deleteAllConversations()
        modelDao.deleteAllModels()
        modelDao.deleteAllGroups()
        remote.modelGroups.forEach { modelDao.insertGroup(it) }
        remote.models.forEach { modelDao.insertModel(it) }
        remote.selectedModelId?.let { sel ->
            try { modelPreferences.setSelectedModelId(sel) } catch (_: Exception) { }
        }
        remote.conversations.forEach { rc ->
            val newConvId = chatDao.insertConversation(
                Conversation(
                    title = rc.title,
                    lastMessage = rc.lastMessage,
                    lastMessageTime = Date(rc.lastMessageTime),
                    messageCount = rc.messageCount
                )
            )
            rc.messages.forEach { rm ->
                chatDao.insertMessage(
                    ChatMessage(
                        conversationId = newConvId,
                        content = rm.content,
                        isFromUser = rm.isFromUser,
                        timestamp = Date(rm.timestamp),
                        isError = rm.isError ?: false
                    )
                )
            }
            val last = chatDao.getLastMessage(newConvId)
            val count = chatDao.getMessageCount(newConvId)
            val current = chatDao.getConversation(newConvId)
            if (current != null && last != null) {
                chatDao.updateConversation(
                    current.copy(
                        lastMessage = last.content,
                        lastMessageTime = last.timestamp,
                        messageCount = count
                    )
                )
            }
        }
    }
}
