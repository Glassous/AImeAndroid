package com.glassous.aime.data.repository

import com.glassous.aime.data.dao.ModelConfigDao
import com.glassous.aime.data.model.Model
import com.glassous.aime.data.model.ModelGroup
import com.glassous.aime.data.model.ModelConfigInfo
import com.glassous.aime.data.preferences.AutoSyncPreferences
import com.glassous.aime.ui.viewmodel.CloudSyncViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
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
    suspend fun createGroup(name: String, baseUrl: String, apiKey: String, onSyncResult: ((Boolean, String) -> Unit)? = null): String {
        val groupId = UUID.randomUUID().toString()
        val group = ModelGroup(
            id = groupId,
            name = name,
            baseUrl = baseUrl,
            apiKey = apiKey
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
    suspend fun addModelToGroup(groupId: String, name: String, modelName: String, onSyncResult: ((Boolean, String) -> Unit)? = null): String {
        val modelId = UUID.randomUUID().toString()
        val model = Model(
            id = modelId,
            groupId = groupId,
            name = name,
            modelName = modelName
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
}