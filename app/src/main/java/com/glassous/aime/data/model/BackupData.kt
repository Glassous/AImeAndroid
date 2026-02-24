package com.glassous.aime.data.model

import com.google.gson.annotations.SerializedName

/**
 * 备份数据结构（使用Long表示时间戳，避免Date的序列化差异）
 */
data class BackupData(
    @SerializedName("version") val version: Int,
    @SerializedName("exportedAt") val exportedAt: Long,
    @SerializedName("modelGroups") val modelGroups: List<ModelGroup>,
    @SerializedName("models") val models: List<Model>,
    @SerializedName("selectedModelId") val selectedModelId: String?,
    @SerializedName("conversations") val conversations: List<BackupConversation>,
    @SerializedName("appSettings") val appSettings: AppSettings? = null
)

data class AppSettings(
    @SerializedName("theme") val theme: ThemeSettings? = null,
    @SerializedName("context") val context: ContextSettings? = null,
    @SerializedName("update") val update: UpdateSettings? = null,
    @SerializedName("systemPrompt") val systemPrompt: SystemPromptSettings? = null,
    @SerializedName("tool") val tool: ToolSettings? = null,
    @SerializedName("titleGeneration") val titleGeneration: TitleGenerationSettings? = null
)

data class ToolSettings(
    @SerializedName("webSearchEnabled") val webSearchEnabled: Boolean? = null,
    @SerializedName("webSearchResultCount") val webSearchResultCount: Int? = null,
    @SerializedName("webSearchEngine") val webSearchEngine: String? = null,
    @SerializedName("tavilyApiKey") val tavilyApiKey: String? = null,
    @SerializedName("tavilyUseProxy") val tavilyUseProxy: Boolean? = null,
    @SerializedName("musicSearchSource") val musicSearchSource: String? = null,
    @SerializedName("toolVisibilities") val toolVisibilities: Map<String, Boolean>? = null
)

data class TitleGenerationSettings(
    @SerializedName("modelId") val modelId: String? = null,
    @SerializedName("contextStrategy") val contextStrategy: Int? = null,
    @SerializedName("contextN") val contextN: Int? = null,
    @SerializedName("autoGenerate") val autoGenerate: Boolean? = null
)

data class SystemPromptSettings(
    @SerializedName("systemPrompt") val systemPrompt: String? = null,
    @SerializedName("enableDynamicDate") val enableDynamicDate: Boolean? = null,
    @SerializedName("enableDynamicTimestamp") val enableDynamicTimestamp: Boolean? = null,
    @SerializedName("enableDynamicLocation") val enableDynamicLocation: Boolean? = null,
    @SerializedName("enableDynamicDeviceModel") val enableDynamicDeviceModel: Boolean? = null,
    @SerializedName("enableDynamicLanguage") val enableDynamicLanguage: Boolean? = null,
    @SerializedName("useCloudProxy") val useCloudProxy: Boolean? = null
)

data class ThemeSettings(
    @SerializedName("selectedTheme") val selectedTheme: String? = null,
    @SerializedName("monochromeTheme") val monochromeTheme: Boolean? = null,
    @SerializedName("htmlCodeBlockCardEnabled") val htmlCodeBlockCardEnabled: Boolean? = null,
    @SerializedName("minimalMode") val minimalMode: Boolean? = null,
    @SerializedName("replyBubbleEnabled") val replyBubbleEnabled: Boolean? = null,
    @SerializedName("chatFontSize") val chatFontSize: Float? = null,
    @SerializedName("chatUiOverlayAlpha") val chatUiOverlayAlpha: Float? = null,
    @SerializedName("topBarHamburgerAlpha") val topBarHamburgerAlpha: Float? = null,
    @SerializedName("topBarModelTextAlpha") val topBarModelTextAlpha: Float? = null,
    @SerializedName("chatInputInnerAlpha") val chatInputInnerAlpha: Float? = null,
    @SerializedName("minimalModeFullscreen") val minimalModeFullscreen: Boolean? = null,
    @SerializedName("chatFullscreen") val chatFullscreen: Boolean? = null,
    @SerializedName("hideImportSharedButton") val hideImportSharedButton: Boolean? = null,
    @SerializedName("themeAdvancedExpanded") val themeAdvancedExpanded: Boolean? = null,
    @SerializedName("minimalModeConfig") val minimalModeConfig: MinimalModeConfig? = null
)

data class ContextSettings(
    @SerializedName("maxContextMessages") val maxContextMessages: Int? = null
)

data class UpdateSettings(
    @SerializedName("autoCheckUpdateEnabled") val autoCheckUpdateEnabled: Boolean? = null
)

data class BackupConversation(
    @SerializedName("title") val title: String,
    @SerializedName("uuid") val uuid: String? = null,
    @SerializedName("lastMessage") val lastMessage: String,
    @SerializedName("lastMessageTime") val lastMessageTime: Long,
    @SerializedName("messageCount") val messageCount: Int,
    @SerializedName("messages") val messages: List<BackupMessage>,
    @SerializedName("isDeleted") val isDeleted: Boolean = false,
    @SerializedName("deletedAt") val deletedAt: Long? = null
)

data class BackupMessage(
    @SerializedName("uuid") val uuid: String? = null,
    @SerializedName("content") val content: String,
    @SerializedName("isFromUser") val isFromUser: Boolean,
    @SerializedName("timestamp") val timestamp: Long,
    @SerializedName("isError") val isError: Boolean?,
    @SerializedName("modelDisplayName") val modelDisplayName: String? = null
)
