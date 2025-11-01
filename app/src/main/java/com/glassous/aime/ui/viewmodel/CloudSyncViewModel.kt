package com.glassous.aime.ui.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.glassous.aime.AIMeApplication
import com.glassous.aime.data.ChatMessage
import com.glassous.aime.data.Conversation
import com.glassous.aime.data.ChatDao
import com.glassous.aime.data.dao.ModelConfigDao
import com.glassous.aime.data.model.Model
import com.glassous.aime.data.model.ModelGroup
import com.glassous.aime.data.model.CompatibleBackupData
import com.glassous.aime.data.model.BackupDataConverter
import com.glassous.aime.data.model.BackupFormat
import com.glassous.aime.data.model.ValidationResult
import com.glassous.aime.data.model.BackupData
import com.glassous.aime.data.model.BackupConversation
import com.glassous.aime.data.model.BackupMessage
import com.glassous.aime.data.preferences.ModelPreferences
import com.glassous.aime.data.preferences.OssPreferences
import com.glassous.aime.data.model.ModelConfigBackup
import com.glassous.aime.data.model.HistoryIndex
import com.glassous.aime.data.model.HistoryEntry
import com.glassous.aime.data.model.HistoryRecord
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.alibaba.sdk.android.oss.OSS
import com.alibaba.sdk.android.oss.OSSClient
import com.alibaba.sdk.android.oss.common.auth.OSSCredentialProvider
import com.alibaba.sdk.android.oss.common.auth.OSSPlainTextAKSKCredentialProvider
import com.alibaba.sdk.android.oss.model.GetObjectRequest
import com.alibaba.sdk.android.oss.model.PutObjectRequest
import com.alibaba.sdk.android.oss.model.DeleteObjectRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.util.Date

/**
 * 云端同步（阿里云OSS）ViewModel
 * 逻辑与本地同步一致，使用相同的JSON结构
 */
class CloudSyncViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as AIMeApplication
    private val chatDao: ChatDao = app.database.chatDao()
    private val modelDao: ModelConfigDao = app.database.modelConfigDao()
    private val modelPreferences: ModelPreferences = app.modelPreferences
    private val ossPreferences = OssPreferences(application)

    private val gson: Gson = GsonBuilder().create()

    // 对象键（多文件增量方案）
    private val modelConfigKey = "AIme/model_config.json"
    private val historyIndexKey = "AIme/history/index.json"
    private fun historyRecordKey(id: Long) = "AIme/history/${id}.json"

    private suspend fun createBackupData(): BackupData {
        val groups = modelDao.getAllGroups().first()
        val models = mutableListOf<Model>()
        for (g in groups) {
            val m = modelDao.getModelsByGroupId(g.id).first()
            models.addAll(m)
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
                    messageCount = c.messageCount,
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

    // 构建模型配置备份（增量结构）
    private suspend fun buildModelConfigBackup(): ModelConfigBackup {
        val groups = modelDao.getAllGroups().first()
        val models = mutableListOf<Model>()
        for (g in groups) {
            val m = modelDao.getModelsByGroupId(g.id).first()
            models.addAll(m)
        }
        val selectedModelId = modelPreferences.selectedModelId.first()
        return ModelConfigBackup(
            exportedAt = System.currentTimeMillis(),
            modelGroups = groups,
            models = models,
            selectedModelId = selectedModelId
        )
    }

    // 构建历史索引（增量结构）
    private suspend fun buildHistoryIndex(): HistoryIndex {
        val conversations = chatDao.getAllConversations().first()
        val items = conversations.map { c ->
            HistoryEntry(
                id = c.id,
                title = c.title,
                updatedAt = c.lastMessageTime.time,
                messageCount = c.messageCount,
                lastMessage = c.lastMessage
            )
        }
        return HistoryIndex(
            generatedAt = System.currentTimeMillis(),
            items = items
        )
    }

    // 构建单条历史记录文件（增量结构）
    private suspend fun buildHistoryRecord(conversationId: Long): HistoryRecord {
        val conv = chatDao.getConversation(conversationId)
            ?: throw IllegalArgumentException("会话不存在: $conversationId")
        val msgs = chatDao.getMessagesForConversation(conversationId).first()
        val backupMessages = msgs.map { m ->
            BackupMessage(
                content = m.content,
                isFromUser = m.isFromUser,
                timestamp = m.timestamp.time,
                isError = m.isError
            )
        }
        val createdAt = msgs.minByOrNull { it.timestamp.time }?.timestamp?.time
            ?: conv.lastMessageTime.time
        val updatedAt = conv.lastMessageTime.time
        return HistoryRecord(
            id = conv.id,
            title = conv.title,
            createdAt = createdAt,
            updatedAt = updatedAt,
            messages = backupMessages
        )
    }

    /**
     * 智能上传：优先使用增量多文件方案；不可用时回退旧单文件方案
     */
    fun uploadBackup(onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val endpoint = ossPreferences.endpoint.first() ?: throw IllegalStateException("请先配置Endpoint")
                val bucket = ossPreferences.bucket.first() ?: throw IllegalStateException("请先配置Bucket名称")
                val ak = ossPreferences.accessKeyId.first() ?: throw IllegalStateException("请先配置AccessKey ID")
                val sk = ossPreferences.accessKeySecret.first() ?: throw IllegalStateException("请先配置AccessKey Secret")

                val credentialProvider: OSSCredentialProvider = OSSPlainTextAKSKCredentialProvider(ak, sk)
                val oss: OSS = OSSClient(getApplication(), endpoint, credentialProvider)

                // 1) 构建本地模型配置与历史索引
                val modelBackup = buildModelConfigBackup()
                val localIndex = buildHistoryIndex()

                // 2) 拉取远端索引（若不存在视为空以便首次迁移）
                val remoteIndex: HistoryIndex? = try {
                    val getIndex = GetObjectRequest(bucket, historyIndexKey)
                    val idxObj = oss.getObject(getIndex)
                    BufferedReader(InputStreamReader(idxObj.objectContent, StandardCharsets.UTF_8)).use { r ->
                        val json = r.readText()
                        gson.fromJson(json, HistoryIndex::class.java)
                    }
                } catch (e: Exception) {
                    null
                }

                val remoteMap = remoteIndex?.items?.associateBy({ it.id }, { it }) ?: emptyMap()
                val localMap = localIndex.items.associateBy({ it.id }, { it })

                // 3) 计算需要上传的记录（新增或更新）、需要删除的远端记录
                val toUpload = mutableListOf<Long>()
                localMap.forEach { (id, local) ->
                    val remote = remoteMap[id]
                    if (remote == null || local.updatedAt > remote.updatedAt || local.messageCount != remote.messageCount || local.lastMessage != remote.lastMessage || local.title != remote.title) {
                        toUpload.add(id)
                    }
                }
                val toDeleteRemote = remoteMap.keys.filter { it !in localMap.keys }

                // 4) 上传模型配置（始终覆盖，体量小）
                val modelJson = gson.toJson(modelBackup)
                oss.putObject(PutObjectRequest(bucket, modelConfigKey, modelJson.toByteArray(StandardCharsets.UTF_8)))

                // 5) 删除远端多余记录文件
                toDeleteRemote.forEach { id ->
                    try {
                        oss.deleteObject(DeleteObjectRequest(bucket, historyRecordKey(id)))
                    } catch (_: Exception) { /* 忽略不存在或权限问题 */ }
                }

                // 6) 上传变更的记录文件
                toUpload.forEach { id ->
                    val record = buildHistoryRecord(id)
                    val recJson = gson.toJson(record)
                    oss.putObject(PutObjectRequest(bucket, historyRecordKey(id), recJson.toByteArray(StandardCharsets.UTF_8)))
                }

                // 7) 上传最新索引
                val idxJson = gson.toJson(localIndex)
                oss.putObject(PutObjectRequest(bucket, historyIndexKey, idxJson.toByteArray(StandardCharsets.UTF_8)))

                onResult(true, "增量上传成功")
            } catch (e: Exception) {
                // 回退：旧单文件备份
                try {
                    val endpoint = ossPreferences.endpoint.first() ?: throw IllegalStateException("请先配置Endpoint")
                    val bucket = ossPreferences.bucket.first() ?: throw IllegalStateException("请先配置Bucket名称")
                    val ak = ossPreferences.accessKeyId.first() ?: throw IllegalStateException("请先配置AccessKey ID")
                    val sk = ossPreferences.accessKeySecret.first() ?: throw IllegalStateException("请先配置AccessKey Secret")

                    val credentialProvider: OSSCredentialProvider = OSSPlainTextAKSKCredentialProvider(ak, sk)
                    val oss: OSS = OSSClient(getApplication(), endpoint, credentialProvider)

                    val backup = createBackupData()
                    val jsonStr = gson.toJson(backup)
                    val objectKey = "AImeBackup.json"

                    val put = PutObjectRequest(bucket, objectKey, jsonStr.toByteArray(StandardCharsets.UTF_8))
                    oss.putObject(put)
                    onResult(true, "回退上传成功（旧格式）")
                } catch (e2: Exception) {
                    onResult(false, "上传失败：${e.message}; 回退也失败：${e2.message}")
                }
            }
        }
    }

    /**
     * 智能下载并导入：优先增量；无增量结构则回退旧AImeBackup.json
     */
    fun downloadAndImport(onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val endpoint = ossPreferences.endpoint.first() ?: throw IllegalStateException("请先配置Endpoint")
                val bucket = ossPreferences.bucket.first() ?: throw IllegalStateException("请先配置Bucket名称")
                val ak = ossPreferences.accessKeyId.first() ?: throw IllegalStateException("请先配置AccessKey ID")
                val sk = ossPreferences.accessKeySecret.first() ?: throw IllegalStateException("请先配置AccessKey Secret")

                val credentialProvider: OSSCredentialProvider = OSSPlainTextAKSKCredentialProvider(ak, sk)
                val oss: OSS = OSSClient(getApplication(), endpoint, credentialProvider)
                // 优先尝试增量结构
                val indexContent: String? = try {
                    val idxObj = oss.getObject(GetObjectRequest(bucket, historyIndexKey))
                    BufferedReader(InputStreamReader(idxObj.objectContent, StandardCharsets.UTF_8)).use { it.readText() }
                } catch (e: Exception) { null }

                if (indexContent != null) {
                    val remoteIndex = gson.fromJson(indexContent, HistoryIndex::class.java)

                    // 1) 下载并导入模型配置（覆盖）
                    try {
                        val mcObj = oss.getObject(GetObjectRequest(bucket, modelConfigKey))
                        val mcJson = BufferedReader(InputStreamReader(mcObj.objectContent, StandardCharsets.UTF_8)).use { it.readText() }
                        val mc = gson.fromJson(mcJson, ModelConfigBackup::class.java)

                        // 覆盖现有模型与分组
                        modelDao.deleteAllModels()
                        modelDao.deleteAllGroups()
                        mc.modelGroups.forEach { modelDao.insertGroup(it) }
                        mc.models.forEach { modelDao.insertModel(it) }
                        if (mc.selectedModelId != null) modelPreferences.setSelectedModelId(mc.selectedModelId)
                    } catch (_: Exception) { /* 若没有模型文件则跳过 */ }

                    // 2) 按索引迭代会话记录：存在同ID则覆盖更新，不存在则创建新会话
                    remoteIndex.items.forEach { entry ->
                        val local = chatDao.getConversation(entry.id)
                        val recordJson = try {
                            val recObj = oss.getObject(GetObjectRequest(bucket, historyRecordKey(entry.id)))
                            BufferedReader(InputStreamReader(recObj.objectContent, StandardCharsets.UTF_8)).use { it.readText() }
                        } catch (_: Exception) { null }

                        if (recordJson == null) return@forEach
                        val record = gson.fromJson(recordJson, HistoryRecord::class.java)

                        if (local == null) {
                            // 新建本地会话
                            val newId = chatDao.insertConversation(
                                Conversation(
                                    title = record.title,
                                    lastMessage = entry.lastMessage,
                                    lastMessageTime = Date(entry.updatedAt),
                                    messageCount = record.messages.size
                                )
                            )
                            record.messages.forEach { bm ->
                                chatDao.insertMessage(
                                    ChatMessage(
                                        conversationId = newId,
                                        content = bm.content,
                                        isFromUser = bm.isFromUser,
                                        timestamp = Date(bm.timestamp),
                                        isError = bm.isError
                                    )
                                )
                            }
                        } else {
                            // 更新现有会话（覆盖消息）
                            chatDao.deleteMessagesForConversation(local.id)
                            record.messages.forEach { bm ->
                                chatDao.insertMessage(
                                    ChatMessage(
                                        conversationId = local.id,
                                        content = bm.content,
                                        isFromUser = bm.isFromUser,
                                        timestamp = Date(bm.timestamp),
                                        isError = bm.isError
                                    )
                                )
                            }
                            val updated = local.copy(
                                title = record.title,
                                lastMessage = entry.lastMessage,
                                lastMessageTime = Date(entry.updatedAt),
                                messageCount = record.messages.size
                            )
                            chatDao.updateConversation(updated)
                        }
                    }

                    onResult(true, "增量下载并导入成功")
                    return@launch
                }

                // 回退：下载旧AImeBackup.json
                val get = GetObjectRequest(bucket, "AImeBackup.json")
                val result = oss.getObject(get)
                val reader = BufferedReader(InputStreamReader(result.objectContent, StandardCharsets.UTF_8))
                val jsonContent = reader.readText()
                reader.close()

                // 检测文件格式
                val format = BackupDataConverter.detectBackupFormat(jsonContent)
                
                val backup: BackupData = when (format) {
                    BackupFormat.COMPATIBLE -> {
                        val compatibleData = gson.fromJson(jsonContent, CompatibleBackupData::class.java)
                        val validationResult = BackupDataConverter.validateCompatibleData(compatibleData)
                        if (validationResult is ValidationResult.Error) {
                            throw IllegalArgumentException("数据验证失败: ${validationResult.errors.joinToString(", ")}")
                        }
                        val sanitizedData = BackupDataConverter.sanitizeCompatibleData(compatibleData)
                        BackupDataConverter.convertToInternalFormat(sanitizedData)
                    }
                    BackupFormat.INTERNAL -> gson.fromJson(jsonContent, BackupData::class.java)
                    BackupFormat.UNKNOWN -> throw IllegalArgumentException("不支持的备份文件格式")
                }

                // 清空现有数据（覆盖模式）
                chatDao.deleteAllMessages()
                chatDao.deleteAllConversations()
                modelDao.deleteAllModels()
                modelDao.deleteAllGroups()

                // 导入模型分组与模型
                backup.modelGroups.forEach { modelDao.insertGroup(it) }
                backup.models.forEach { modelDao.insertModel(it) }
                if (backup.selectedModelId != null) {
                    modelPreferences.setSelectedModelId(backup.selectedModelId)
                }

                // 导入会话与消息（覆盖模式）
                backup.conversations.forEach { bc ->
                    val newConversationId = chatDao.insertConversation(
                        Conversation(
                            title = bc.title,
                            lastMessage = bc.lastMessage,
                            lastMessageTime = Date(bc.lastMessageTime),
                            messageCount = bc.messages.size
                        )
                    )
                    bc.messages.forEach { bm ->
                        chatDao.insertMessage(
                            ChatMessage(
                                conversationId = newConversationId,
                                content = bm.content,
                                isFromUser = bm.isFromUser,
                                timestamp = Date(bm.timestamp),
                                isError = bm.isError
                            )
                        )
                    }
                }

                val formatName = when (format) {
                    BackupFormat.COMPATIBLE -> "兼容格式"
                    BackupFormat.INTERNAL -> "标准格式"
                    BackupFormat.UNKNOWN -> "未知格式"
                }
                onResult(true, "从云端导入成功 ($formatName)")
            } catch (e: Exception) {
                onResult(false, "从云端导入失败：${e.message}")
            }
        }
    }

    /** 从OSS下载到指定Uri（固定对象名：AImeBackup.json） */
    fun downloadToUri(uri: Uri, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val endpoint = ossPreferences.endpoint.first() ?: throw IllegalStateException("请先配置Endpoint")
                val bucket = ossPreferences.bucket.first() ?: throw IllegalStateException("请先配置Bucket名称")
                val ak = ossPreferences.accessKeyId.first() ?: throw IllegalStateException("请先配置AccessKey ID")
                val sk = ossPreferences.accessKeySecret.first() ?: throw IllegalStateException("请先配置AccessKey Secret")

                val credentialProvider: OSSCredentialProvider = OSSPlainTextAKSKCredentialProvider(ak, sk)
                val oss: OSS = OSSClient(getApplication(), endpoint, credentialProvider)

                val get = GetObjectRequest(bucket, "AImeBackup.json")
                val result = oss.getObject(get)
                val reader = BufferedReader(InputStreamReader(result.objectContent, StandardCharsets.UTF_8))
                val jsonContent = reader.readText()
                reader.close()

                val resolver = getApplication<Application>().contentResolver
                resolver.openOutputStream(uri)?.use { os ->
                    os.write(jsonContent.toByteArray(StandardCharsets.UTF_8))
                } ?: throw IllegalStateException("无法打开目标文件")

                onResult(true, "从云端下载成功")
            } catch (e: Exception) {
                onResult(false, "从云端下载失败：${e.message}")
            }
        }
    }
}

class CloudSyncViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(CloudSyncViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return CloudSyncViewModel(application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}