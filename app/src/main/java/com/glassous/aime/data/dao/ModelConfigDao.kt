package com.glassous.aime.data.dao

import androidx.room.*
import com.glassous.aime.data.model.Model
import com.glassous.aime.data.model.ModelGroup
import kotlinx.coroutines.flow.Flow

@Dao
interface ModelConfigDao {
    
    // 模型分组操作
    @Query("SELECT * FROM model_groups WHERE isDeleted = 0 ORDER BY createdAt ASC")
    fun getAllGroups(): Flow<List<ModelGroup>>
    
    @Query("SELECT * FROM model_groups WHERE id = :groupId AND isDeleted = 0")
    suspend fun getGroupById(groupId: String): ModelGroup?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGroup(group: ModelGroup)
    
    @Update
    suspend fun updateGroup(group: ModelGroup)
    
    @Query("UPDATE model_groups SET isDeleted = 1, deletedAt = :deletedAt WHERE id = :groupId")
    suspend fun markGroupDeleted(groupId: String, deletedAt: Long)
    
    // 模型操作
    @Query("SELECT * FROM models WHERE groupId = :groupId AND isDeleted = 0 ORDER BY createdAt ASC")
    fun getModelsByGroupId(groupId: String): Flow<List<Model>>
    
    @Query("SELECT * FROM models WHERE id = :modelId")
    suspend fun getModelById(modelId: String): Model?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertModel(model: Model)
    
    @Update
    suspend fun updateModel(model: Model)
    
    @Query("UPDATE models SET isDeleted = 1, deletedAt = :deletedAt WHERE id = :modelId")
    suspend fun markModelDeleted(modelId: String, deletedAt: Long)
    
    @Query("UPDATE models SET isDeleted = 1, deletedAt = :deletedAt WHERE groupId = :groupId")
    suspend fun markModelsDeletedByGroupId(groupId: String, deletedAt: Long)

    // Added: delete all data methods for import override mode
    @Query("DELETE FROM models")
    suspend fun deleteAllModels()

    @Query("DELETE FROM model_groups")
    suspend fun deleteAllGroups()

    @Query("SELECT * FROM model_groups ORDER BY createdAt DESC")
    fun getAllGroupsIncludingDeleted(): Flow<List<ModelGroup>>

    @Query("SELECT * FROM models WHERE groupId = :groupId ORDER BY createdAt DESC")
    fun getModelsByGroupIdIncludingDeleted(groupId: String): Flow<List<Model>>
}
