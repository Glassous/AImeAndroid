package com.glassous.aime.data.dao

import androidx.room.*
import com.glassous.aime.data.model.Model
import com.glassous.aime.data.model.ModelGroup
import kotlinx.coroutines.flow.Flow

@Dao
interface ModelConfigDao {
    
    // 模型分组操作
    @Query("SELECT * FROM model_groups ORDER BY createdAt DESC")
    fun getAllGroups(): Flow<List<ModelGroup>>
    
    @Query("SELECT * FROM model_groups WHERE id = :groupId")
    suspend fun getGroupById(groupId: String): ModelGroup?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGroup(group: ModelGroup)
    
    @Update
    suspend fun updateGroup(group: ModelGroup)
    
    @Delete
    suspend fun deleteGroup(group: ModelGroup)
    
    // 模型操作
    @Query("SELECT * FROM models WHERE groupId = :groupId ORDER BY createdAt DESC")
    fun getModelsByGroupId(groupId: String): Flow<List<Model>>
    
    @Query("SELECT * FROM models WHERE id = :modelId")
    suspend fun getModelById(modelId: String): Model?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertModel(model: Model)
    
    @Update
    suspend fun updateModel(model: Model)
    
    @Delete
    suspend fun deleteModel(model: Model)
    
    @Query("DELETE FROM models WHERE groupId = :groupId")
    suspend fun deleteModelsByGroupId(groupId: String)
}