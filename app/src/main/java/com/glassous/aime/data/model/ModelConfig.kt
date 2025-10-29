package com.glassous.aime.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 模型配置分组
 */
@Entity(tableName = "model_groups")
data class ModelGroup(
    @PrimaryKey
    val id: String,
    val name: String,
    val baseUrl: String,
    val apiKey: String,
    val providerUrl: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * 模型配置
 */
@Entity(tableName = "models")
data class Model(
    @PrimaryKey
    val id: String,
    val groupId: String,
    val name: String,
    val modelName: String, // 实际的模型名称，如 gpt-3.5-turbo
    val remark: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * 完整的模型配置信息（用于UI显示）
 */
data class ModelConfigInfo(
    val group: ModelGroup,
    val models: List<Model>
)