package com.glassous.aime.ui.viewmodel

import android.app.Application
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
import com.glassous.aime.data.preferences.ModelPreferences
import com.glassous.aime.data.preferences.OssPreferences
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.alibaba.sdk.android.oss.OSS
import com.alibaba.sdk.android.oss.OSSClient
import com.alibaba.sdk.android.oss.common.auth.OSSCredentialProvider
import com.alibaba.sdk.android.oss.common.auth.OSSPlainTextAKSKCredentialProvider
import com.alibaba.sdk.android.oss.model.GetObjectRequest
import com.alibaba.sdk.android.oss.model.PutObjectRequest
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

    /** 上传备份到OSS（固定对象名：AImeBackup.json） */
    fun uploadBackup(onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
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
                onResult(true, "上传成功")
            } catch (e: Exception) {
                onResult(false, "上传失败：${e.message}")
            }
        }
    }

    /** 从OSS下载并导入（固定对象名：AImeBackup.json） */
    fun downloadAndImport(onResult: (Boolean, String) -> Unit) {
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
                val json = reader.readText()
                reader.close()

                val backup = gson.fromJson(json, BackupData::class.java)

                // Upsert 模型分组与模型
                backup.modelGroups.forEach { modelDao.insertGroup(it) }
                backup.models.forEach { modelDao.insertModel(it) }

                // 恢复选中模型（可选）
                if (backup.selectedModelId != null) {
                    modelPreferences.setSelectedModelId(backup.selectedModelId)
                }

                // 导入会话与消息（作为新会话）
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

                onResult(true, "从云端导入成功")
            } catch (e: Exception) {
                onResult(false, "从云端导入失败：${e.message}")
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