package com.glassous.aime.data.repository

import com.glassous.aime.data.dao.ModelConfigDao
import com.glassous.aime.data.model.Model
import com.glassous.aime.data.model.ModelGroup
import com.glassous.aime.data.model.ModelConfigInfo
import com.glassous.aime.data.preferences.AutoSyncPreferences
import com.glassous.aime.ui.viewmodel.CloudSyncViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import java.util.UUID

class ModelConfigRepository(
    private val modelConfigDao: ModelConfigDao,
    private val autoSyncPreferences: AutoSyncPreferences,
    private val cloudSyncViewModel: CloudSyncViewModel
) {
    
    // 获取所有分组
    fun getAllGroups(): Flow<List<ModelGroup>> = modelConfigDao.getAllGroups()
    
    // 获取完整的模型配置信息
    fun getAllModelConfigs(): Flow<List<ModelConfigInfo>> {
        return combine(
            modelConfigDao.getAllGroups()
        ) { groupsList ->
            groupsList[0].map { group ->
                // 这里暂时返回空的模型列表，实际使用时需要异步获取
                ModelConfigInfo(group, emptyList())
            }
        }
    }
    
    // 获取指定分组的模型
    fun getModelsByGroupId(groupId: String): Flow<List<Model>> = 
        modelConfigDao.getModelsByGroupId(groupId)
    
    // 创建新分组
    suspend fun createGroup(name: String, baseUrl: String, apiKey: String, providerUrl: String?, onSyncResult: ((Boolean, String) -> Unit)? = null): String {
        val groupId = UUID.randomUUID().toString()
        val group = ModelGroup(
            id = groupId,
            name = name,
            baseUrl = baseUrl,
            apiKey = apiKey,
            providerUrl = providerUrl
        )
        modelConfigDao.insertGroup(group)
        
        // 如果开启了自动同步，触发上传
        if (autoSyncPreferences.isAutoSyncEnabled()) {
            cloudSyncViewModel.uploadBackup { success, message ->
                onSyncResult?.invoke(success, message)
            }
        }
        
        return groupId
    }
    
    // 更新分组
    suspend fun updateGroup(group: ModelGroup, onSyncResult: ((Boolean, String) -> Unit)? = null) {
        modelConfigDao.updateGroup(group)
        
        // 如果开启了自动同步，触发上传
        if (autoSyncPreferences.isAutoSyncEnabled()) {
            cloudSyncViewModel.uploadBackup { success, message ->
                onSyncResult?.invoke(success, message)
            }
        }
    }
    
    // 删除分组（同时删除组内所有模型）
    suspend fun deleteGroup(group: ModelGroup, onSyncResult: ((Boolean, String) -> Unit)? = null) {
        modelConfigDao.deleteModelsByGroupId(group.id)
        modelConfigDao.deleteGroup(group)
        
        // 如果开启了自动同步，触发上传
        if (autoSyncPreferences.isAutoSyncEnabled()) {
            cloudSyncViewModel.uploadBackup { success, message ->
                onSyncResult?.invoke(success, message)
            }
        }
    }
    
    // 添加模型到分组
    suspend fun addModelToGroup(groupId: String, name: String, modelName: String, remark: String?, onSyncResult: ((Boolean, String) -> Unit)? = null): String {
        val modelId = UUID.randomUUID().toString()
        val model = Model(
            id = modelId,
            groupId = groupId,
            name = name,
            modelName = modelName,
            remark = remark
        )
        modelConfigDao.insertModel(model)
        
        // 如果开启了自动同步，触发上传
        if (autoSyncPreferences.isAutoSyncEnabled()) {
            cloudSyncViewModel.uploadBackup { success, message ->
                onSyncResult?.invoke(success, message)
            }
        }
        
        return modelId
    }
    
    // 更新模型
    suspend fun updateModel(model: Model, onSyncResult: ((Boolean, String) -> Unit)? = null) {
        modelConfigDao.updateModel(model)
        
        // 如果开启了自动同步，触发上传
        if (autoSyncPreferences.isAutoSyncEnabled()) {
            cloudSyncViewModel.uploadBackup { success, message ->
                onSyncResult?.invoke(success, message)
            }
        }
    }
    
    // 删除模型
    suspend fun deleteModel(model: Model, onSyncResult: ((Boolean, String) -> Unit)? = null) {
        modelConfigDao.deleteModel(model)
        
        // 如果开启了自动同步，触发上传
        if (autoSyncPreferences.isAutoSyncEnabled()) {
            cloudSyncViewModel.uploadBackup { success, message ->
                onSyncResult?.invoke(success, message)
            }
        }
    }
    
    // 获取分组详情
    suspend fun getGroupById(groupId: String): ModelGroup? = 
        modelConfigDao.getGroupById(groupId)
    
    // 获取模型详情
    suspend fun getModelById(modelId: String): Model? = 
        modelConfigDao.getModelById(modelId)

    // 预设数据种子：插入几组默认模型分组与模型（可删除）
    suspend fun seedDefaultPresets() {
        val existingGroups = modelConfigDao.getAllGroups().first()

        // Helper to find or create group by name
        suspend fun ensureGroup(
            name: String,
            baseUrl: String,
            apiKey: String = "",
            providerUrl: String? = null
        ): ModelGroup {
            val found = existingGroups.find { it.name == name }
            if (found != null) return found
            val group = ModelGroup(
                id = UUID.randomUUID().toString(),
                name = name,
                baseUrl = baseUrl,
                apiKey = apiKey,
                providerUrl = providerUrl
            )
            modelConfigDao.insertGroup(group)
            return group
        }

        // Helper to insert model if missing by modelName
        suspend fun ensureModel(groupId: String, name: String, modelName: String, remark: String? = null) {
            val existingModels = modelConfigDao.getModelsByGroupId(groupId).first()
            val exists = existingModels.any { it.modelName == modelName }
            if (exists) return
            modelConfigDao.insertModel(
                Model(
                    id = UUID.randomUUID().toString(),
                    groupId = groupId,
                    name = name,
                    modelName = modelName,
                    remark = remark
                )
            )
        }

        // 1. OpenRouter
        val openRouter = ensureGroup(
            name = "OpenRouter",
            baseUrl = "https://openrouter.ai/api/v1/chat/completions",
            providerUrl = "https://openrouter.ai/"
        )
        ensureModel(openRouter.id, "GPT-5", "openai/gpt-5", "$1.25/M input tokens $10/M output tokens")
        ensureModel(openRouter.id, "GPT-5-mini", "openai/gpt-5-mini", "$0.25/M input tokens $2/M output tokens")
        ensureModel(openRouter.id, "Grok-4-fast", "x-ai/grok-4-fast", "$0.20/M input tokens $0.50/M output tokens")
        ensureModel(openRouter.id, "Gemini-2.5-flash", "google/gemini-2.5-flash", "$0.30/M input tokens $2.50/M output tokens $1.238/K input imgs")
        ensureModel(openRouter.id, "Claude-haiku-4.5", "anthropic/claude-haiku-4.5", "$1/M input tokens $5/M output tokens")

        // 2. 阿里云
        val aliyun = ensureGroup(
            name = "阿里云",
            baseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1",
            providerUrl = "https://bailian.console.aliyun.com/?tab=model#/model-market/all"
        )
        ensureModel(aliyun.id, "Qwen-flash", "qwen-flash")
        ensureModel(aliyun.id, "Qwen3-MAX", "qwen3-max")
        ensureModel(aliyun.id, "Qwen-plus", "qwen-plus")

        // 3. DeepSeek
        val deepseek = ensureGroup(
            name = "DeepSeek",
            baseUrl = "https://api.deepseek.com",
            providerUrl = "https://platform.deepseek.com/usage"
        )
        ensureModel(deepseek.id, "Deepseek-reasoner", "deepseek-reasoner")
        ensureModel(deepseek.id, "Deepseek-chat", "deepseek-chat")
    }
}