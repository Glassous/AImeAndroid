package com.glassous.aime.data.model

import com.google.gson.annotations.SerializedName
import com.google.gson.Gson

/**
 * 兼容性备份数据结构
 * 支持导入 AImeBackup.json 格式的数据
 */
data class CompatibleBackupData(
    val success: Boolean,
    val data: CompatibleBackupContent,
    val filename: String? = null
)

data class CompatibleBackupContent(
    val conversations: List<CompatibleConversation>,
    val exportedAt: Long,
    val modelGroups: List<CompatibleModelGroup>,
    val models: List<CompatibleModel>,
    val selectedModelId: String?,
    val version: Int
)

data class CompatibleConversation(
    val title: String,
    val messages: List<CompatibleMessage>,
    val messageCount: Int,
    val lastMessage: String,
    val lastMessageTime: Long
)

data class CompatibleMessage(
    val content: String,
    val isError: Boolean,
    val isFromUser: Boolean,
    val timestamp: Long
)

data class CompatibleModelGroup(
    val apiKey: String,
    val baseUrl: String,
    val createdAt: Long,
    val id: String,
    val name: String
)

data class CompatibleModel(
    val createdAt: Long,
    val groupId: String,
    val id: String,
    val modelName: String,
    val name: String
)

/**
 * 数据转换器
 * 将兼容格式转换为应用内部格式
 */
object BackupDataConverter {
    
    /**
     * 将兼容格式转换为内部备份格式
     */
    fun convertToInternalFormat(compatibleData: CompatibleBackupData): BackupData {
        val content = compatibleData.data
        
        // 转换模型分组
        val modelGroups = content.modelGroups.map { group ->
            ModelGroup(
                id = group.id,
                name = group.name,
                baseUrl = group.baseUrl,
                apiKey = group.apiKey,
                createdAt = group.createdAt
            )
        }
        
        // 转换模型
        val models = content.models.map { model ->
            Model(
                id = model.id,
                groupId = model.groupId,
                name = model.name,
                modelName = model.modelName,
                createdAt = model.createdAt
            )
        }
        
        // 转换会话
        val conversations = content.conversations.map { conversation ->
            val messages = conversation.messages.map { message ->
                BackupMessage(
                    content = message.content,
                    isFromUser = message.isFromUser,
                    timestamp = message.timestamp,
                    isError = message.isError
                )
            }
            
            BackupConversation(
                title = conversation.title,
                lastMessage = conversation.lastMessage,
                lastMessageTime = conversation.lastMessageTime,
                messageCount = conversation.messageCount,
                messages = messages
            )
        }
        
        return BackupData(
            version = content.version,
            exportedAt = content.exportedAt,
            modelGroups = modelGroups,
            models = models,
            selectedModelId = content.selectedModelId,
            conversations = conversations
        )
    }
    
    /**
     * 检测备份文件格式
     */
    fun detectBackupFormat(jsonContent: String): BackupFormat {
        return try {
            // 尝试解析为兼容格式
            if (jsonContent.contains("\"success\"") && jsonContent.contains("\"data\"")) {
                BackupFormat.COMPATIBLE
            } else if (jsonContent.contains("\"version\"") && jsonContent.contains("\"exportedAt\"")) {
                BackupFormat.INTERNAL
            } else {
                BackupFormat.UNKNOWN
            }
        } catch (e: Exception) {
            BackupFormat.UNKNOWN
        }
    }
    
    /**
     * 验证兼容格式数据的完整性
     */
    fun validateCompatibleData(data: CompatibleBackupData): ValidationResult {
        val errors = mutableListOf<String>()
        
        // 检查基本结构
        if (!data.success) {
            errors.add("备份文件标记为失败状态")
        }
        
        // 检查模型分组
        data.data.modelGroups.forEach { group ->
            if (group.id.isBlank()) errors.add("模型分组ID不能为空")
            if (group.name.isBlank()) errors.add("模型分组名称不能为空")
            if (group.baseUrl.isBlank()) errors.add("模型分组BaseUrl不能为空")
        }
        
        // 检查模型
        data.data.models.forEach { model ->
            if (model.id.isBlank()) errors.add("模型ID不能为空")
            if (model.groupId.isBlank()) errors.add("模型分组ID不能为空")
            if (model.name.isBlank()) errors.add("模型名称不能为空")
            if (model.modelName.isBlank()) errors.add("模型实际名称不能为空")
        }
        
        // 检查会话
        data.data.conversations.forEach { conversation ->
            if (conversation.title.isBlank()) errors.add("会话标题不能为空")
            if (conversation.messages.isEmpty()) errors.add("会话不能没有消息")
            
            conversation.messages.forEach { message ->
                if (message.content.isBlank()) errors.add("消息内容不能为空")
                if (message.timestamp <= 0) errors.add("消息时间戳无效")
            }
        }
        
        return if (errors.isEmpty()) {
            ValidationResult.Success
        } else {
            ValidationResult.Error(errors)
        }
    }
    
    /**
     * 数据清理和修复
     */
    fun sanitizeCompatibleData(data: CompatibleBackupData): CompatibleBackupData {
        val sanitizedContent = data.data.copy(
            // 移除空的模型分组
            modelGroups = data.data.modelGroups.filter { 
                it.id.isNotBlank() && it.name.isNotBlank() && it.baseUrl.isNotBlank() 
            },
            // 移除无效的模型
            models = data.data.models.filter { 
                it.id.isNotBlank() && it.groupId.isNotBlank() && 
                it.name.isNotBlank() && it.modelName.isNotBlank() 
            },
            // 清理会话数据
            conversations = data.data.conversations.filter { 
                it.title.isNotBlank() && it.messages.isNotEmpty() 
            }.map { conversation ->
                conversation.copy(
                    messages = conversation.messages.filter { 
                        it.content.isNotBlank() && it.timestamp > 0 
                    }
                )
            }.filter { it.messages.isNotEmpty() }
        )
        
        return data.copy(data = sanitizedContent)
    }
}

/**
 * 验证结果
 */
sealed class ValidationResult {
    object Success : ValidationResult()
    data class Error(val errors: List<String>) : ValidationResult()
}

enum class BackupFormat {
    INTERNAL,    // 应用内部格式
    COMPATIBLE,  // 兼容格式 (AImeBackup.json)
    UNKNOWN      // 未知格式
}