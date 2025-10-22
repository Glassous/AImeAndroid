package com.glassous.aime.ui.viewmodel

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.glassous.aime.AIMeApplication
import com.glassous.aime.data.ChatMessage
import com.glassous.aime.data.Conversation
import com.glassous.aime.data.dao.ModelConfigDao
import com.glassous.aime.data.ChatDao
import com.glassous.aime.data.model.Model
import com.glassous.aime.data.model.ModelGroup
import com.glassous.aime.data.preferences.ModelPreferences
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.util.Date

/**
 * 本地数据同步（导入/导出）ViewModel
 * 导出：模型分组与模型、全部会话及消息
 * 导入：覆盖/追加模型分组与模型；会话作为新会话导入，消息关联到新会话
 */
class DataSyncViewModel(application: Application) : AndroidViewModel(application) {
    private val app = (application as AIMeApplication)
    private val chatDao: ChatDao = app.database.chatDao()
    private val modelDao: ModelConfigDao = app.database.modelConfigDao()
    private val modelPreferences: ModelPreferences = app.modelPreferences

    private val gson: Gson = GsonBuilder().create()

    /** 导出到指定Uri（SAF CreateDocument返回） */
    fun exportToUri(context: Context, uri: Uri, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
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

                val backup = BackupData(
                    version = 1,
                    exportedAt = System.currentTimeMillis(),
                    modelGroups = groups,
                    models = models,
                    selectedModelId = selectedModelId,
                    conversations = backupConversations
                )

                context.contentResolver.openOutputStream(uri)?.use { os ->
                    OutputStreamWriter(os, Charsets.UTF_8).use { writer ->
                        gson.toJson(backup, writer)
                    }
                } ?: throw IllegalStateException("无法打开导出文件")

                onResult(true, "导出成功")
            } catch (e: Exception) {
                onResult(false, "导出失败：${e.message}")
            }
        }
    }

    /** 从指定Uri导入（SAF OpenDocument返回） */
    fun importFromUri(context: Context, uri: Uri, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val backup: BackupData = context.contentResolver.openInputStream(uri)?.use { ins ->
                    InputStreamReader(ins, Charsets.UTF_8).use { reader ->
                        gson.fromJson(reader, BackupData::class.java)
                    }
                } ?: throw IllegalStateException("无法读取导入文件")

                // Upsert 模型分组与模型
                backup.modelGroups.forEach { modelDao.insertGroup(it) }
                backup.models.forEach { modelDao.insertModel(it) }

                // 可选：恢复选中模型
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
                    // 插入消息
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

                onResult(true, "导入成功")
            } catch (e: Exception) {
                onResult(false, "导入失败：${e.message}")
            }
        }
    }
}

// 备份数据结构（使用Long表示时间戳，避免Date的序列化差异）
data class BackupData(
    val version: Int,
    val exportedAt: Long,
    val modelGroups: List<ModelGroup>,
    val models: List<Model>,
    val selectedModelId: String?,
    val conversations: List<BackupConversation>
)

data class BackupConversation(
    val title: String,
    val lastMessage: String,
    val lastMessageTime: Long,
    val messageCount: Int,
    val messages: List<BackupMessage>
)

data class BackupMessage(
    val content: String,
    val isFromUser: Boolean,
    val timestamp: Long,
    val isError: Boolean
)

class DataSyncViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(DataSyncViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return DataSyncViewModel(application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}