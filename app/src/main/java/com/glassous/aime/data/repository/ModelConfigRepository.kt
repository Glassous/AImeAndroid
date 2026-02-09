package com.glassous.aime.data.repository

import com.glassous.aime.data.dao.ModelConfigDao
import com.glassous.aime.data.model.Model
import com.glassous.aime.data.model.ModelGroup
import com.glassous.aime.data.model.ModelConfigInfo
 
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import java.util.UUID

import com.glassous.aime.BuildConfig
import com.google.gson.Gson
import okhttp3.OkHttpClient
import okhttp3.Request
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

data class RemoteModelConfig(
    val groups: List<RemoteModelGroup>
)

data class RemoteModelGroup(
    val name: String,
    val baseUrl: String,
    val providerUrl: String?,
    val models: List<RemoteModel>
)

data class RemoteModel(
    val name: String,
    val modelName: String,
    val remark: String?
)

sealed class FetchStatus {
    object Idle : FetchStatus()
    object Fetching : FetchStatus()
    data class Merging(val current: Int, val total: Int) : FetchStatus()
    data class Success(val newModelsCount: Int) : FetchStatus()
    data class Error(val message: String) : FetchStatus()
}

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

    // 从云端获取并合并最新模型配置
    fun fetchAndMergeModels(): Flow<FetchStatus> = flow {
        emit(FetchStatus.Fetching)
        val url = BuildConfig.DEFAULT_MODELS_URL
        if (url.isBlank()) {
            emit(FetchStatus.Error("Default models URL not configured"))
            return@flow
        }

        try {
            val remoteConfig = withContext(Dispatchers.IO) {
                val client = OkHttpClient()
                val request = Request.Builder().url(url).build()
                val response = client.newCall(request).execute()
                
                if (!response.isSuccessful) {
                    throw IOException("Fetch models failed: ${response.code}")
                }
                
                val body = response.body?.string() ?: throw IOException("Empty response body")
                Gson().fromJson(body, RemoteModelConfig::class.java)
            }

            // 开始合并逻辑
            val existingGroups = modelConfigDao.getAllGroups().first()
            var newModelsCount = 0
            val totalGroups = remoteConfig.groups.size
            
            remoteConfig.groups.forEachIndexed { index, remoteGroup ->
                emit(FetchStatus.Merging(index + 1, totalGroups))
                
                // 查找或创建分组
                var group = existingGroups.find { it.name == remoteGroup.name }
                val groupId: String
                
                if (group == null) {
                    // 创建新分组
                    groupId = UUID.randomUUID().toString()
                    group = ModelGroup(
                        id = groupId,
                        name = remoteGroup.name,
                        baseUrl = remoteGroup.baseUrl,
                        apiKey = "",
                        providerUrl = remoteGroup.providerUrl
                    )
                    modelConfigDao.insertGroup(group)
                } else {
                    groupId = group.id
                }

                // 处理该分组下的模型
                val existingModels = modelConfigDao.getModelsByGroupId(groupId).first()
                
                remoteGroup.models.forEach { remoteModel ->
                    val modelExists = existingModels.any { it.modelName == remoteModel.modelName }
                    if (!modelExists) {
                        // 插入新模型
                        modelConfigDao.insertModel(
                            Model(
                                id = UUID.randomUUID().toString(),
                                groupId = groupId,
                                name = remoteModel.name,
                                modelName = remoteModel.modelName,
                                remark = remoteModel.remark
                            )
                        )
                        newModelsCount++
                    }
                }
            }
            emit(FetchStatus.Success(newModelsCount))
        } catch (e: Exception) {
            e.printStackTrace()
            emit(FetchStatus.Error(e.message ?: "Unknown error"))
        }
    }.flowOn(Dispatchers.IO)
}
