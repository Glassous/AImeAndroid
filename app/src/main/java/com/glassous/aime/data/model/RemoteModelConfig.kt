package com.glassous.aime.data.model

import com.google.gson.annotations.SerializedName

/**
 * 从云端获取的远程模型配置数据模型
 */
data class RemoteModelConfig(
    @SerializedName("groups") val groups: List<RemoteModelGroup>
)

data class RemoteModelGroup(
    @SerializedName("name") val name: String,
    @SerializedName("baseUrl") val baseUrl: String,
    @SerializedName("providerUrl") val providerUrl: String?,
    @SerializedName("models") val models: List<RemoteModel>
)

data class RemoteModel(
    @SerializedName("name") val name: String,
    @SerializedName("modelName") val modelName: String,
    @SerializedName("remark") val remark: String?
)
