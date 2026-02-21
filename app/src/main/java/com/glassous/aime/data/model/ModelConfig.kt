package com.glassous.aime.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.gson.annotations.SerializedName

/**
 * 模型配置分组
 */
@Entity(tableName = "model_groups")
data class ModelGroup(
    @PrimaryKey
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String,
    @SerializedName("baseUrl") val baseUrl: String,
    @SerializedName("apiKey") val apiKey: String,
    @SerializedName("providerUrl") val providerUrl: String? = null,
    @SerializedName("createdAt") val createdAt: Long = System.currentTimeMillis(),
    @SerializedName("isDeleted") val isDeleted: Boolean = false,
    @SerializedName("deletedAt") val deletedAt: Long? = null
)

/**
 * 模型配置
 */
@Entity(tableName = "models")
data class Model(
    @PrimaryKey
    @SerializedName("id") val id: String,
    @SerializedName("groupId") val groupId: String,
    @SerializedName("name") val name: String,
    @SerializedName("modelName") val modelName: String, // 实际的模型名称，如 gpt-3.5-turbo
    @SerializedName("remark") val remark: String? = null,
    @SerializedName("createdAt") val createdAt: Long = System.currentTimeMillis(),
    @SerializedName("isDeleted") val isDeleted: Boolean = false,
    @SerializedName("deletedAt") val deletedAt: Long? = null
)

/**
 * 完整的模型配置信息（用于UI显示）
 */
data class ModelConfigInfo(
    val group: ModelGroup,
    val models: List<Model>
)

object BuiltInModels {
    const val AIME_MODEL_ID = "builtin_aime"
    const val AIME_GROUP_ID = "builtin_aime_group"
    const val BUILTIN_API_KEY_PLACEHOLDER = "sk-builtin-aime"

    val aimeModel = Model(
        id = AIME_MODEL_ID,
        groupId = AIME_GROUP_ID,
        name = "AIme",
        modelName = "内置免费模型", // Will be replaced by env var in proxy
        remark = "内置AI模型",
        createdAt = 0L
    )

    val aimeGroup = ModelGroup(
        id = AIME_GROUP_ID,
        name = "AIme",
        baseUrl = "https://api.openai.com/v1", // Dummy URL, proxy will replace
        apiKey = BUILTIN_API_KEY_PLACEHOLDER
    )
}
