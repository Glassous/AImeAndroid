package com.glassous.aime.ui.viewmodel

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.glassous.aime.AIMeApplication
import com.glassous.aime.data.ChatMessage
import com.glassous.aime.data.Conversation
import com.glassous.aime.data.dao.ModelConfigDao
import com.glassous.aime.data.ChatDao
import com.glassous.aime.data.model.Model
import com.glassous.aime.data.model.ModelGroup
import com.glassous.aime.data.model.CompatibleBackupData
import com.glassous.aime.data.model.BackupDataConverter
import com.glassous.aime.data.model.BackupFormat
import com.glassous.aime.data.model.ValidationResult
import com.glassous.aime.data.model.BackupData
import com.glassous.aime.data.model.BackupConversation
import com.glassous.aime.data.model.BackupMessage
import com.glassous.aime.data.preferences.ModelPreferences
import com.glassous.aime.data.preferences.ThemePreferences
import com.glassous.aime.data.preferences.ContextPreferences
import com.glassous.aime.data.preferences.UpdatePreferences
import com.glassous.aime.data.model.AppSettings
import com.glassous.aime.data.model.ThemeSettings
import com.glassous.aime.data.model.ContextSettings
import com.glassous.aime.data.model.UpdateSettings
import com.glassous.aime.data.model.SystemPromptSettings
import com.glassous.aime.data.model.ToolSettings
import com.glassous.aime.data.model.TitleGenerationSettings
import com.glassous.aime.data.model.ToolType
import com.glassous.aime.data.preferences.ToolPreferences
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.util.Date

/**
 * 本地数据同步（导入/导出）ViewModel
 * 导出：模型分组与模型、全部会话及消息、应用设置
 * 导入：覆盖/追加模型分组与模型；会话作为新会话导入，消息关联到新会话；恢复应用设置
 */
class DataSyncViewModel(application: Application) : AndroidViewModel(application) {
    private val app = (application as AIMeApplication)
    private val chatDao: ChatDao = app.database.chatDao()
    private val modelDao: ModelConfigDao = app.database.modelConfigDao()
    private val modelPreferences: ModelPreferences = app.modelPreferences
    private val themePreferences = ThemePreferences(application)
    private val contextPreferences = app.contextPreferences
    private val updatePreferences = app.updatePreferences
    private val toolPreferences = app.toolPreferences

    private val gson: Gson = GsonBuilder().create()

    /** 导出到指定Uri（SAF CreateDocument返回） */
    fun exportToUri(context: Context, uri: Uri, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val groups = modelDao.getAllGroups().first()
                val models = mutableListOf<Model>()
                for (g in groups) {
                    val m = modelDao.getModelsByGroupId(g.id).first()
                    models.addAll(m)
                }
                val selectedModelId = modelPreferences.selectedModelId.first()

                // Collect App Settings
                val themeSettings = ThemeSettings(
                    selectedTheme = themePreferences.selectedTheme.first(),
                    monochromeTheme = themePreferences.monochromeTheme.first(),
                    htmlCodeBlockCardEnabled = themePreferences.htmlCodeBlockCardEnabled.first(),
                    minimalMode = themePreferences.minimalMode.first(),
                    replyBubbleEnabled = themePreferences.replyBubbleEnabled.first(),
                    chatFontSize = themePreferences.chatFontSize.first(),
                    chatUiOverlayAlpha = themePreferences.chatUiOverlayAlpha.first(),
                    topBarHamburgerAlpha = themePreferences.topBarHamburgerAlpha.first(),
                    topBarModelTextAlpha = themePreferences.topBarModelTextAlpha.first(),
                    chatInputInnerAlpha = themePreferences.chatInputInnerAlpha.first(),
                    minimalModeFullscreen = themePreferences.minimalModeFullscreen.first(),
                    chatFullscreen = themePreferences.chatFullscreen.first(),
                    hideImportSharedButton = themePreferences.hideImportSharedButton.first(),
                    themeAdvancedExpanded = themePreferences.themeAdvancedExpanded.first(),
                    minimalModeConfig = themePreferences.minimalModeConfig.first()
                )

                val contextSettings = ContextSettings(
                    maxContextMessages = contextPreferences.maxContextMessages.first()
                )

                val updateSettings = UpdateSettings(
                    autoCheckUpdateEnabled = updatePreferences.autoCheckUpdateEnabled.first()
                )

                val systemPromptSettings = SystemPromptSettings(
                    systemPrompt = modelPreferences.systemPrompt.first(),
                    enableDynamicDate = modelPreferences.enableDynamicDate.first(),
                    enableDynamicTimestamp = modelPreferences.enableDynamicTimestamp.first(),
                    enableDynamicLocation = modelPreferences.enableDynamicLocation.first(),
                    enableDynamicDeviceModel = modelPreferences.enableDynamicDeviceModel.first(),
                    enableDynamicLanguage = modelPreferences.enableDynamicLanguage.first(),
                    useCloudProxy = modelPreferences.useCloudProxy.first()
                )

                val toolSettings = ToolSettings(
                    webSearchEnabled = toolPreferences.webSearchEnabled.first(),
                    webSearchResultCount = toolPreferences.webSearchResultCount.first(),
                    webSearchEngine = toolPreferences.webSearchEngine.first(),
                    tavilyApiKey = toolPreferences.tavilyApiKey.first(),
                    tavilyUseProxy = toolPreferences.tavilyUseProxy.first(),
                    musicSearchSource = toolPreferences.musicSearchSource.first(),
                    musicSearchResultCount = toolPreferences.musicSearchResultCount.first(),
                    toolVisibilities = ToolType.getAllTools().associate { it.name to toolPreferences.getToolVisibility(it.name).first() }
                )

                val titleGenerationSettings = TitleGenerationSettings(
                    modelId = modelPreferences.titleGenerationModelId.first(),
                    contextStrategy = modelPreferences.titleGenerationContextStrategy.first(),
                    contextN = modelPreferences.titleGenerationContextN.first(),
                    autoGenerate = modelPreferences.titleGenerationAutoGenerate.first()
                )

                val appSettings = AppSettings(
                    theme = themeSettings,
                    context = contextSettings,
                    update = updateSettings,
                    systemPrompt = systemPromptSettings,
                    tool = toolSettings,
                    titleGeneration = titleGenerationSettings
                )

                val conversations = chatDao.getAllConversations().first()
                val backupConversations = mutableListOf<BackupConversation>()
                val imagesToZip = mutableSetOf<String>()

                for (c in conversations) {
                    val msgs = chatDao.getMessagesForConversation(c.id).first()
                    val backupMessages = msgs.map { m ->
                        val relativeImagePaths = m.imagePaths.mapNotNull { absolutePath ->
                            val file = java.io.File(absolutePath)
                            if (file.exists()) {
                                imagesToZip.add(absolutePath)
                                "images/${file.name}"
                            } else null
                        }

                        BackupMessage(
                            content = m.content,
                            isFromUser = m.isFromUser,
                            timestamp = m.timestamp.time,
                            isError = m.isError,
                            modelDisplayName = m.modelDisplayName,
                            imagePaths = relativeImagePaths.ifEmpty { null }
                        )
                    }
                    backupConversations.add(
                        BackupConversation(
                            title = c.title,
                            lastMessage = c.lastMessage,
                            lastMessageTime = c.lastMessageTime.time,
                            messageCount = c.messageCount,
                            messages = backupMessages
                        )
                    )
                }

                val backup = BackupData(
                    version = 2, // Incremented version
                    exportedAt = System.currentTimeMillis(),
                    modelGroups = groups,
                    models = models,
                    selectedModelId = selectedModelId,
                    conversations = backupConversations,
                    appSettings = appSettings
                )

                // Create temp zip file
                val tempZipFile = java.io.File.createTempFile("backup", ".zip", context.cacheDir)
                val zipOut = java.util.zip.ZipOutputStream(java.io.FileOutputStream(tempZipFile))

                // Write backup.json
                val jsonEntry = java.util.zip.ZipEntry("backup.json")
                zipOut.putNextEntry(jsonEntry)
                val writer = java.io.OutputStreamWriter(zipOut, Charsets.UTF_8)
                gson.toJson(backup, writer)
                writer.flush()
                zipOut.closeEntry()

                // Write images
                imagesToZip.forEach { absolutePath ->
                    val file = java.io.File(absolutePath)
                    val entry = java.util.zip.ZipEntry("images/${file.name}")
                    zipOut.putNextEntry(entry)
                    java.io.FileInputStream(file).use { it.copyTo(zipOut) }
                    zipOut.closeEntry()
                }

                zipOut.close()

                // Copy temp zip to uri
                context.contentResolver.openOutputStream(uri)?.use { os ->
                    java.io.FileInputStream(tempZipFile).use { it.copyTo(os) }
                }
                
                tempZipFile.delete()
                onResult(true, "导出成功")
            } catch (e: Exception) {
                onResult(false, "导出失败：${e.message}")
            }
        }
    }

    /** 从指定Uri导入（SAF OpenDocument返回） - 支持多种格式 */
    fun importFromUri(context: Context, uri: Uri, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Copy to temp file first to handle both ZIP and JSON
                val tempFile = java.io.File.createTempFile("import", ".tmp", context.cacheDir)
                context.contentResolver.openInputStream(uri)?.use { input ->
                    java.io.FileOutputStream(tempFile).use { output ->
                        input.copyTo(output)
                    }
                }

                var jsonContent: String? = null
                val imagesDir = java.io.File(context.filesDir, "images")
                if (!imagesDir.exists()) imagesDir.mkdirs()

                // Try to read as ZIP
                try {
                    java.util.zip.ZipFile(tempFile).use { zipFile ->
                        val entries = zipFile.entries()
                        while (entries.hasMoreElements()) {
                            val entry = entries.nextElement()
                            if (entry.name == "backup.json") {
                                // Read JSON content
                                zipFile.getInputStream(entry).use { input ->
                                    jsonContent = input.bufferedReader().readText()
                                }
                            } else if (entry.name.startsWith("images/") && !entry.isDirectory) {
                                // Extract image
                                val fileName = java.io.File(entry.name).name
                                if (fileName.isNotEmpty()) {
                                    val targetFile = java.io.File(imagesDir, fileName)
                                    zipFile.getInputStream(entry).use { input ->
                                        java.io.FileOutputStream(targetFile).use { output ->
                                            input.copyTo(output)
                                        }
                                    }
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    // Not a valid zip or error reading zip
                    e.printStackTrace()
                }

                // If not zip or failed, try reading as plain JSON
                if (jsonContent == null) {
                    try {
                        jsonContent = tempFile.readText()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                if (jsonContent == null) {
                    throw IllegalArgumentException("无法读取备份文件内容")
                }

                // 检测文件格式
                val format = BackupDataConverter.detectBackupFormat(jsonContent!!)
                
                val backup: BackupData = when (format) {
                    BackupFormat.COMPATIBLE -> {
                        // 解析兼容格式 (AImeBackup.json)
                        val compatibleData = gson.fromJson(jsonContent, CompatibleBackupData::class.java)
                        
                        // 验证数据完整性
                        val validationResult = BackupDataConverter.validateCompatibleData(compatibleData)
                        if (validationResult is ValidationResult.Error) {
                            throw IllegalArgumentException("数据验证失败: ${validationResult.errors.joinToString(", ")}")
                        }
                        
                        // 清理和修复数据
                        val sanitizedData = BackupDataConverter.sanitizeCompatibleData(compatibleData)
                        BackupDataConverter.convertToInternalFormat(sanitizedData)
                    }
                    BackupFormat.INTERNAL -> {
                        // 解析内部格式
                        gson.fromJson(jsonContent, BackupData::class.java)
                    }
                    BackupFormat.UNKNOWN -> {
                        throw IllegalArgumentException("不支持的备份文件格式")
                    }
                }

                // 清空现有数据（覆盖模式）
                chatDao.deleteAllMessages()
                chatDao.deleteAllConversations()
                modelDao.deleteAllModels()
                modelDao.deleteAllGroups()

                // 导入模型分组与模型
                backup.modelGroups.forEach { modelDao.insertGroup(it) }
                backup.models.forEach { modelDao.insertModel(it) }

                // 恢复选中模型
                if (backup.selectedModelId != null) {
                    modelPreferences.setSelectedModelId(backup.selectedModelId)
                }

                // 恢复应用设置
                backup.appSettings?.let { settings ->
                    settings.theme?.let { theme ->
                        theme.selectedTheme?.let { themePreferences.setTheme(it) }
                        theme.monochromeTheme?.let { themePreferences.setMonochromeTheme(it) }
                        theme.htmlCodeBlockCardEnabled?.let { themePreferences.setHtmlCodeBlockCardEnabled(it) }
                        theme.minimalMode?.let { themePreferences.setMinimalMode(it) }
                        theme.replyBubbleEnabled?.let { themePreferences.setReplyBubbleEnabled(it) }
                        theme.chatFontSize?.let { themePreferences.setChatFontSize(it) }
                        theme.chatUiOverlayAlpha?.let { themePreferences.setChatUiOverlayAlpha(it) }
                        theme.topBarHamburgerAlpha?.let { themePreferences.setTopBarHamburgerAlpha(it) }
                        theme.topBarModelTextAlpha?.let { themePreferences.setTopBarModelTextAlpha(it) }
                        theme.chatInputInnerAlpha?.let { themePreferences.setChatInputInnerAlpha(it) }
                        theme.minimalModeFullscreen?.let { themePreferences.setMinimalModeFullscreen(it) }
                        theme.chatFullscreen?.let { themePreferences.setChatFullscreen(it) }
                        theme.hideImportSharedButton?.let { themePreferences.setHideImportSharedButton(it) }
                        theme.themeAdvancedExpanded?.let { themePreferences.setThemeAdvancedExpanded(it) }
                        theme.minimalModeConfig?.let { themePreferences.setMinimalModeConfig(it) }
                    }
                    settings.context?.let { ctx ->
                        ctx.maxContextMessages?.let { contextPreferences.setMaxContextMessages(it) }
                    }
                    settings.update?.let { upd ->
                        upd.autoCheckUpdateEnabled?.let { updatePreferences.setAutoCheckUpdateEnabled(it) }
                    }
                    settings.systemPrompt?.let { sp ->
                        sp.systemPrompt?.let { modelPreferences.setSystemPrompt(it) }
                        sp.enableDynamicDate?.let { modelPreferences.setEnableDynamicDate(it) }
                        sp.enableDynamicTimestamp?.let { modelPreferences.setEnableDynamicTimestamp(it) }
                        sp.enableDynamicLocation?.let { modelPreferences.setEnableDynamicLocation(it) }
                        sp.enableDynamicDeviceModel?.let { modelPreferences.setEnableDynamicDeviceModel(it) }
                        sp.enableDynamicLanguage?.let { modelPreferences.setEnableDynamicLanguage(it) }
                        sp.useCloudProxy?.let { modelPreferences.setUseCloudProxy(it) }
                    }
                    settings.tool?.let { tool ->
                        tool.webSearchEnabled?.let { toolPreferences.setWebSearchEnabled(it) }
                        tool.webSearchResultCount?.let { toolPreferences.setWebSearchResultCount(it) }
                        tool.webSearchEngine?.let { toolPreferences.setWebSearchEngine(it) }
                        tool.tavilyApiKey?.let { toolPreferences.setTavilyApiKey(it) }
                        tool.tavilyUseProxy?.let { toolPreferences.setTavilyUseProxy(it) }
                        tool.musicSearchSource?.let { toolPreferences.setMusicSearchSource(it) }
                        tool.musicSearchResultCount?.let { toolPreferences.setMusicSearchResultCount(it) }
                        tool.toolVisibilities?.forEach { (name, visible) ->
                            toolPreferences.setToolVisibility(name, visible)
                        }
                    }
                    settings.titleGeneration?.let { tg ->
                        tg.modelId?.let { modelPreferences.setTitleGenerationModelId(it) }
                        tg.contextStrategy?.let { modelPreferences.setTitleGenerationContextStrategy(it) }
                        tg.contextN?.let { modelPreferences.setTitleGenerationContextN(it) }
                        tg.autoGenerate?.let { modelPreferences.setTitleGenerationAutoGenerate(it) }
                    }
                }

                // 导入会话与消息
                backup.conversations.forEach { bc ->
                    val newConversationId = chatDao.insertConversation(
                        Conversation(
                            title = bc.title,
                            lastMessage = bc.lastMessage,
                            lastMessageTime = Date(bc.lastMessageTime),
                            messageCount = bc.messages.size
                        )
                    )
                    // 插入消息
                    bc.messages.forEach { bm ->
                        // Restore absolute image paths
                        val restoredImagePaths = bm.imagePaths?.map { relativePath ->
                            val fileName = java.io.File(relativePath).name
                            java.io.File(imagesDir, fileName).absolutePath
                        } ?: emptyList()

                        chatDao.insertMessage(
                            ChatMessage(
                                conversationId = newConversationId,
                                content = bm.content,
                                isFromUser = bm.isFromUser,
                                timestamp = Date(bm.timestamp),
                                isError = bm.isError ?: false,
                                modelDisplayName = bm.modelDisplayName,
                                imagePaths = restoredImagePaths
                            )
                        )
                    }
                }

                tempFile.delete()
                
                val formatName = when (format) {
                    BackupFormat.COMPATIBLE -> "兼容格式"
                    BackupFormat.INTERNAL -> "标准格式"
                    BackupFormat.UNKNOWN -> "未知格式"
                }
                onResult(true, "导入成功 ($formatName)")
            } catch (e: Exception) {
                e.printStackTrace()
                onResult(false, "导入失败：${e.message}")
            }
        }
    }

    fun importSharedConversation(input: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // 1. Extract UUID
                val uuid = parseUuidFromInput(input)
                if (uuid == null) {
                    onResult(false, "无效的链接或ID")
                    return@launch
                }

                // 2. Fetch data
                val data = com.glassous.aime.data.repository.SupabaseShareRepository.getSharedConversation(uuid)

                // 3. Create Conversation
                val now = Date()
                val newConv = Conversation(
                    title = data.title,
                    lastMessage = data.messages.lastOrNull()?.content ?: "",
                    lastMessageTime = now,
                    messageCount = data.messages.size
                )
                val convId = chatDao.insertConversation(newConv)

                // 4. Create Messages
                data.messages.forEachIndexed { index, msgDto ->
                    // Parse base64 images from content
                    val (cleanContent, imagePaths) = parseAndSaveImages(msgDto.content)

                    // Add slight delay to timestamp to preserve order
                    val msgTime = Date(now.time + index * 100)
                    val chatMsg = ChatMessage(
                        conversationId = convId,
                        content = cleanContent,
                        isFromUser = msgDto.role == "user",
                        timestamp = msgTime,
                        modelDisplayName = if (msgDto.role != "user") data.model else null,
                        imagePaths = imagePaths
                    )
                    chatDao.insertMessage(chatMsg)
                }

                onResult(true, "导入成功\n${data.title}")
            } catch (e: Exception) {
                e.printStackTrace()
                onResult(false, "导入失败: ${e.message}")
            }
        }
    }

    private fun parseAndSaveImages(content: String): Pair<String, List<String>> {
        val imagePaths = mutableListOf<String>()
        // Regex to match <img src="data:image/jpeg;base64,..."/>
        val imgRegex = "<img src=\"data:image/\\w+;base64,([^\"]+)\".*?/>".toRegex()
        
        val matches = imgRegex.findAll(content)
        matches.forEach { match ->
            val base64 = match.groupValues[1]
            try {
                val imageBytes = android.util.Base64.decode(base64, android.util.Base64.DEFAULT)
                val imagesDir = java.io.File(app.filesDir, "images")
                if (!imagesDir.exists()) imagesDir.mkdirs()
                
                val fileName = "img_${System.currentTimeMillis()}_${java.util.UUID.randomUUID()}.jpg"
                val file = java.io.File(imagesDir, fileName)
                
                java.io.FileOutputStream(file).use { output ->
                    output.write(imageBytes)
                }
                
                imagePaths.add(file.absolutePath)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        
        // Remove all img tags from content and trim whitespace
        val cleanContent = imgRegex.replace(content, "").trim()
        
        return Pair(cleanContent, imagePaths)
    }

    private fun parseUuidFromInput(input: String): String? {
        val uuidRegex = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}".toRegex(RegexOption.IGNORE_CASE)
        val match = uuidRegex.find(input)
        return match?.value
    }
}

class DataSyncViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(DataSyncViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return DataSyncViewModel(application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
