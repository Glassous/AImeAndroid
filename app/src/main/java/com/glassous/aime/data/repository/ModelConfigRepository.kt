package com.glassous.aime.data.repository

import com.glassous.aime.data.dao.ModelConfigDao
import com.glassous.aime.data.model.Model
import com.glassous.aime.data.model.ModelGroup
import com.glassous.aime.data.model.ModelConfigInfo
 
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import java.util.UUID

class ModelConfigRepository(
    private val modelConfigDao: ModelConfigDao
) {
    // Auto-sync removed

    
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
        return groupId
    }
    
    // 更新分组
    suspend fun updateGroup(group: ModelGroup) {
        modelConfigDao.updateGroup(group)
    }
    
    // 删除分组（同时删除组内所有模型）
    suspend fun deleteGroup(group: ModelGroup) {
        val now = System.currentTimeMillis()
        modelConfigDao.markModelsDeletedByGroupId(group.id, now)
        modelConfigDao.markGroupDeleted(group.id, now)
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
        return modelId
    }
    
    // 更新模型
    suspend fun updateModel(model: Model, onSyncResult: ((Boolean, String) -> Unit)? = null) {
        modelConfigDao.updateModel(model)
    }
    
    // 删除模型
    suspend fun deleteModel(model: Model, onSyncResult: ((Boolean, String) -> Unit)? = null) {
        val now = System.currentTimeMillis()
        modelConfigDao.markModelDeleted(model.id, now)
    }
    
    // 获取分组详情
    suspend fun getGroupById(groupId: String): ModelGroup? = 
        modelConfigDao.getGroupById(groupId)
    
    // 获取模型详情
    suspend fun getModelById(modelId: String): Model? = 
        modelConfigDao.getModelById(modelId)

    // 预设数据种子：插入几组默认模型分组与模型（可删除）
    suspend fun seedDefaultPresets() {
        val existingGroups = modelConfigDao.getAllGroupsIncludingDeleted().first()

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
            val existingModels = modelConfigDao.getModelsByGroupIdIncludingDeleted(groupId).first()
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
        ensureModel(openRouter.id, "Gemini 3 Pro", "google/gemini-3-pro-preview", "$2/M input tokens $12/M output tokens")
        ensureModel(openRouter.id, "Gemini 3 Flash", "google/gemini-3-flash-preview", "$0.50/M input tokens $3/M output tokens")
        ensureModel(openRouter.id, "Gemini 2.5 flash Lite", "google/gemini-2.5-flash-lite", "$0.10/M input tokens $0.40/M output tokens")
        ensureModel(openRouter.id, "GPT 5.2", "openai/gpt-5.2", "$1.75/M input tokens $14/M output tokens")
        ensureModel(openRouter.id, "GPT 5.2 Chat", "openai/gpt-5.2-chat", "$1.75/M input tokens $14/M output tokens")
        ensureModel(openRouter.id, "GPT 5.2 Pro", "openai/gpt-5.2-pro", "$21/M input tokens $168/M output tokens")
        ensureModel(openRouter.id, "GPT 5.1", "openai/gpt-5.1", "$1.25/M input tokens $10/M output tokens")
        ensureModel(openRouter.id, "GPT 5 Mini", "openai/gpt-5-mini", "$0.25/M input tokens $2/M output tokens")
        ensureModel(openRouter.id, "GPT 5 Nano", "openai/gpt-5-nano", "$0.05/M input tokens $0.40/M output tokens")
        ensureModel(openRouter.id, "Claude Sonnet 4.5", "anthropic/claude-sonnet-4.5", "$3/M input tokens $15/M output tokens")
        ensureModel(openRouter.id, "Claude Opus 4.6", "anthropic/claude-opus-4.6", "$5/M input tokens $25/M output tokens")
        ensureModel(openRouter.id, "Claude haiku 4.5", "anthropic/claude-haiku-4.5", "$1/M input tokens $5/M output tokens")
        ensureModel(openRouter.id, "Grok 4", "x-ai/grok-4", "$3/M input tokens $15/M output tokens")
        ensureModel(openRouter.id, "Grok 4.1 Fast", "x-ai/grok-4.1-fast", "$0.20/M input tokens $0.50/M output tokens")
        ensureModel(openRouter.id, "Grok Code Fast 1", "x-ai/grok-code-fast-1", "$0.20/M input tokens $1.50/M output tokens")
        ensureModel(openRouter.id, "DeepSeek V3.2", "deepseek/deepseek-v3.2", "$0.25/M input tokens $0.38/M output tokens")
        ensureModel(openRouter.id, "DeepSeek R1 0528", "deepseek/deepseek-r1-0528", "$0.40/M input tokens $1.75/M output tokens")
        ensureModel(openRouter.id, "Qwen3 Max", "qwen/qwen3-max", "$3/M input tokens $15/M output tokens")
        ensureModel(openRouter.id, "Kimi K2.5", "moonshotai/kimi-k2.5", "$0.60/M input tokens $3/M output tokens")
        ensureModel(openRouter.id, "Kimi K2 Thinking", "moonshotai/kimi-k2-thinking", "$0.40/M input tokens $1.75/M output tokens")
        ensureModel(openRouter.id, "Kimi K2", "moonshotai/kimi-k2-0905", "$0.39/M input tokens $1.90/M output tokens")
        ensureModel(openRouter.id, "MiniMax M2.1", "minimax/minimax-m2.1", "$0.27/M input tokens $1.10/M output tokens")
        ensureModel(openRouter.id, "GLM 4.7", "z-ai/glm-4.7", "$0.40/M input tokens $1.50/M output tokens")
        ensureModel(openRouter.id, "GLM 4.7 Flash", "z-ai/glm-4.7-flash", "$0.07/M input tokens $0.40/M output tokens")
        ensureModel(openRouter.id, "MiMo V2 Flash", "xiaomi/mimo-v2-flash", "$0.40/M input tokens $1.50/M output tokens")
        ensureModel(openRouter.id, "Devstral 2 2512", "mistralai/devstral-2512", "$0.40/M input tokens $1.50/M output tokens")

        // 2. 阿里云
        val aliyun = ensureGroup(
            name = "阿里云",
            baseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1",
            providerUrl = "https://bailian.console.aliyun.com/?tab=model#/model-market/all"
        )
        ensureModel(aliyun.id, "Qwen3-MAX", "qwen3-max-2026-01-23")
        ensureModel(aliyun.id, "Deepseek V3.2", "deepseek-v3.2")
        ensureModel(aliyun.id, "GLM 4.7", "glm-4.7")
        ensureModel(aliyun.id, "MiniMax M2.1", "MiniMax-M2.1")
        ensureModel(aliyun.id, "Kimi K2 Thinking", "kimi-k2-thinking")
        ensureModel(aliyun.id, "Qwen-flash", "qwen-flash")
        ensureModel(aliyun.id, "Qwen-plus", "qwen-plus")

        // 3. DeepSeek
        val deepseek = ensureGroup(
            name = "DeepSeek",
            baseUrl = "https://api.deepseek.com",
            providerUrl = "https://platform.deepseek.com/usage"
        )
        ensureModel(deepseek.id, "Deepseek-reasoner", "deepseek-reasoner")
        ensureModel(deepseek.id, "Deepseek-chat", "deepseek-chat")

        // 4. 豆包
        val doubao = ensureGroup(
            name = "豆包",
            baseUrl = "https://ark.cn-beijing.volces.com/api/v3",
            providerUrl = "https://www.volcengine.com/product/ark"
        )
        ensureModel(doubao.id, "豆包 Seed 1.8", "doubao-seed-1-8-251228")
        ensureModel(doubao.id, "豆包 Seed 1.6", "doubao-seed-1-6-251015")
        ensureModel(doubao.id, "豆包 Seed 1.6 Flash", "doubao-seed-1-6-flash-250828")
        ensureModel(doubao.id, "豆包 Seed 1.6 Lite", "doubao-seed-1-6-lite-251015")
        ensureModel(doubao.id, "Deepseek V3.2", "deepseek-v3-2-251201")
        ensureModel(doubao.id, "GLM 4.7", "glm-4-7-251222")

        // 5. Moonshot
        val moonshot = ensureGroup(
            name = "Moonshot",
            baseUrl = "https://api.moonshot.cn",
            providerUrl = "https://platform.moonshot.cn/docs/overview"
        )
        ensureModel(moonshot.id, "Kimi K2.5", "kimi-k2.5")
        ensureModel(moonshot.id, "Kimi K2", "kimi-k2-0905-preview")
        ensureModel(moonshot.id, "Kimi K2 Turbo", "kimi-k2-turbo-preview")
        ensureModel(moonshot.id, "Kimi K2 Thinking", "kimi-k2-thinking")
        ensureModel(moonshot.id, "Kimi K2 Thinking Turbo", "kimi-k2-thinking-turbo")

        // 6. 智谱
        val zhipu = ensureGroup(
            name = "智谱",
            baseUrl = "https://open.bigmodel.cn/api/paas/v4",
            providerUrl = "https://bigmodel.cn/"
        )
        ensureModel(zhipu.id, "GLM 4.7", "glm-4.7")
        ensureModel(zhipu.id, "GLM 4.7 Flash", "glm-4.7-flash")
        ensureModel(zhipu.id, "GLM 4.7 FlashX", "glm-4.7-flashx")
        ensureModel(zhipu.id, "GLM 4.6", "glm-4.6")

        // 7. MiniMax
        val minimax = ensureGroup(
            name = "MiniMax",
            baseUrl = "https://api.minimaxi.com/v1",
            providerUrl = "https://platform.minimaxi.com/user-center/basic-information"
        )
        ensureModel(minimax.id, "MiniMax M2.1", "MiniMax-M2.1")
        ensureModel(minimax.id, "MiniMax M2.1 Lightning", "MiniMax-M2.1-lightning")
        ensureModel(minimax.id, "MiniMax M2", "MiniMax-M2")
    }

    // 恢复为预设模型：删除所有现有数据并重新插入预设数据
    suspend fun resetToDefaultPresets() {
        // 删除所有现有数据
        modelConfigDao.deleteAllModels()
        modelConfigDao.deleteAllGroups()
        
        // 重新插入预设数据
        seedDefaultPresets()
    }
}
