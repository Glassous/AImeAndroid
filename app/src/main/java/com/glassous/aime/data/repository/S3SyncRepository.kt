package com.glassous.aime.data.repository

import android.content.Context
import com.glassous.aime.data.ChatDao
import com.glassous.aime.data.ChatMessage
import com.glassous.aime.data.Conversation
import com.glassous.aime.data.dao.ModelConfigDao
import com.glassous.aime.data.model.*
import com.glassous.aime.data.preferences.*
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import aws.sdk.kotlin.services.s3.S3Client
import aws.sdk.kotlin.services.s3.model.PutObjectRequest
import aws.sdk.kotlin.services.s3.model.GetObjectRequest
import aws.sdk.kotlin.services.s3.model.ListObjectsV2Request
import aws.sdk.kotlin.runtime.auth.credentials.StaticCredentialsProvider
import aws.smithy.kotlin.runtime.content.ByteStream
import aws.smithy.kotlin.runtime.content.decodeToString
import aws.smithy.kotlin.runtime.net.url.Url
import java.security.MessageDigest
import java.io.File
import java.util.Date

class S3SyncRepository(
    private val context: Context,
    private val s3Preferences: S3Preferences,
    private val s3UploadRepository: S3UploadRepository,
    private val chatDao: ChatDao,
    private val modelConfigDao: ModelConfigDao,
    private val modelPreferences: ModelPreferences,
    private val themePreferences: ThemePreferences,
    private val contextPreferences: ContextPreferences,
    private val updatePreferences: UpdatePreferences,
    private val toolPreferences: ToolPreferences,
    private val privacyPreferences: PrivacyPreferences
) {
    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()

    suspend fun sync(onProgress: (String) -> Unit) = withContext(Dispatchers.IO) {
        val endpoint = s3Preferences.s3Endpoint.first()
        val region = s3Preferences.s3Region.first().ifBlank { "us-east-1" }
        val accessKey = s3Preferences.s3AccessKey.first()
        val secretKey = s3Preferences.s3SecretKey.first()
        val bucketName = s3Preferences.s3BucketName.first()
        val forcePathStyle = s3Preferences.s3ForcePathStyle.first()

        if (endpoint.isBlank() || accessKey.isBlank() || secretKey.isBlank() || bucketName.isBlank()) {
            throw IllegalStateException("S3 configuration incomplete")
        }

        val normalizedEndpoint = if (endpoint.startsWith("http://") || endpoint.startsWith("https://")) {
            endpoint
        } else {
            "https://$endpoint"
        }

        S3Client {
            this.region = region
            this.endpointUrl = Url.parse(normalizedEndpoint)
            this.credentialsProvider = StaticCredentialsProvider {
                this.accessKeyId = accessKey
                this.secretAccessKey = secretKey
            }
            this.forcePathStyle = forcePathStyle
        }.use { client ->
            // 1. 获取远程清单
            onProgress("正在获取同步清单...")
            val remoteManifestJson = downloadString(client, bucketName, "data/manifest.json")
            val remoteManifest = remoteManifestJson?.let { 
                try { gson.fromJson(it, SyncManifest::class.java) } catch (e: Exception) { null }
            } ?: SyncManifest(emptyMap(), 0, 0, 0)

            onProgress("正在同步设置...")
            val finalSettingsVersion = syncSettings(client, bucketName, remoteManifest.settingsVersion)

            onProgress("正在同步模型配置...")
            val finalModelsVersion = syncModels(client, bucketName, remoteManifest.modelsVersion)

            onProgress("正在同步对话记录...")
            syncConversations(client, bucketName, remoteManifest, finalSettingsVersion, finalModelsVersion, onProgress)
            
            onProgress("同步完成")
        }
    }

    private suspend fun syncSettings(client: S3Client, bucketName: String, remoteVersionRaw: Long): Long {
        val appSettings = collectLocalSettings()
        val currentJson = gson.toJson(appSettings)
        val currentHash = calculateHash(currentJson)
        val lastSyncedHash = s3Preferences.s3SettingsHash.first()
        val localVersionRaw = s3Preferences.s3SettingsVersion.first()
        
        // 矫正机制：强制本地版本号至少为 1
        val localVersion = maxOf(1L, localVersionRaw)
        val remoteVersion = maxOf(0L, remoteVersionRaw)

        return when {
            remoteVersion > localVersion -> {
                // 1. 云端领先：下载云端设置覆盖本地
                try {
                    val remoteJson = downloadString(client, bucketName, "data/settings.json")
                    if (remoteJson != null) {
                        val remoteSettings = gson.fromJson(remoteJson, AppSettings::class.java)
                        applySettings(remoteSettings)
                        
                        val updatedJson = gson.toJson(remoteSettings)
                        val updatedHash = calculateHash(updatedJson)
                        s3Preferences.setS3SettingsVersion(remoteVersion)
                        s3Preferences.setS3SettingsHash(updatedHash)
                        remoteVersion
                    } else localVersion
                } catch (e: Exception) {
                    e.printStackTrace()
                    localVersion
                }
            }
            localVersion > remoteVersion -> {
                // 2. 本地领先：上传本地设置到云端（补传）
                uploadString(client, bucketName, "data/settings.json", currentJson)
                if (localVersion != localVersionRaw) {
                    s3Preferences.setS3SettingsVersion(localVersion)
                }
                localVersion
            }
            currentHash != lastSyncedHash -> {
                // 3. 版本相等但内容有变动：递增上传
                val newVersion = remoteVersion + 1
                uploadString(client, bucketName, "data/settings.json", currentJson)
                s3Preferences.setS3SettingsVersion(newVersion)
                s3Preferences.setS3SettingsHash(currentHash)
                newVersion
            }
            else -> {
                // 4. 完全同步
                localVersion
            }
        }
    }

    private suspend fun applySettings(settings: AppSettings) {
        settings.theme?.let { s ->
            s.selectedTheme?.let { themePreferences.setTheme(it) }
            s.monochromeTheme?.let { themePreferences.setMonochromeTheme(it) }
            s.htmlCodeBlockCardEnabled?.let { themePreferences.setHtmlCodeBlockCardEnabled(it) }
            s.minimalMode?.let { themePreferences.setMinimalMode(it) }
            s.replyBubbleEnabled?.let { themePreferences.setReplyBubbleEnabled(it) }
            s.chatFontSize?.let { themePreferences.setChatFontSize(it) }
            s.chatUiOverlayAlpha?.let { themePreferences.setChatUiOverlayAlpha(it) }
            s.topBarHamburgerAlpha?.let { themePreferences.setTopBarHamburgerAlpha(it) }
            s.topBarModelTextAlpha?.let { themePreferences.setTopBarModelTextAlpha(it) }
            s.chatInputInnerAlpha?.let { themePreferences.setChatInputInnerAlpha(it) }
            s.minimalModeFullscreen?.let { themePreferences.setMinimalModeFullscreen(it) }
            s.chatFullscreen?.let { themePreferences.setChatFullscreen(it) }
            s.hideImportSharedButton?.let { themePreferences.setHideImportSharedButton(it) }
            s.themeAdvancedExpanded?.let { themePreferences.setThemeAdvancedExpanded(it) }
            s.minimalModeConfig?.let { themePreferences.setMinimalModeConfig(it) }
        }
        
        settings.context?.let { c ->
            c.maxContextMessages?.let { contextPreferences.setMaxContextMessages(it) }
        }
        
        settings.update?.let { u ->
            u.autoCheckUpdateEnabled?.let { updatePreferences.setAutoCheckUpdateEnabled(it) }
        }
        
        settings.systemPrompt?.let { sp ->
            sp.systemPrompt?.let { v: String -> modelPreferences.setSystemPrompt(v) }
            sp.enableDynamicDate?.let { v: Boolean -> modelPreferences.setEnableDynamicDate(v) }
            sp.enableDynamicTimestamp?.let { v: Boolean -> modelPreferences.setEnableDynamicTimestamp(v) }
            sp.enableDynamicLocation?.let { v: Boolean -> modelPreferences.setEnableDynamicLocation(v) }
            sp.enableDynamicDeviceModel?.let { v: Boolean -> modelPreferences.setEnableDynamicDeviceModel(v) }
            sp.enableDynamicLanguage?.let { v: Boolean -> modelPreferences.setEnableDynamicLanguage(v) }
            sp.useCloudProxy?.let { v: Boolean -> modelPreferences.setUseCloudProxy(v) }
        }
        
        settings.tool?.let { t ->
            t.webSearchEnabled?.let { v: Boolean -> toolPreferences.setWebSearchEnabled(v) }
            t.webSearchResultCount?.let { v: Int -> toolPreferences.setWebSearchResultCount(v) }
            t.webSearchEngine?.let { v: String -> toolPreferences.setWebSearchEngine(v) }
            t.tavilyApiKey?.let { v: String -> toolPreferences.setTavilyApiKey(v) }
            t.tavilyUseProxy?.let { v: Boolean -> toolPreferences.setTavilyUseProxy(v) }
            t.musicSearchSource?.let { v: String -> toolPreferences.setMusicSearchSource(v) }
            t.musicSearchResultCount?.let { v: Int -> toolPreferences.setMusicSearchResultCount(v) }
            t.imageGenBaseUrl?.let { v: String -> toolPreferences.setImageGenBaseUrl(v) }
            t.imageGenApiKey?.let { v: String -> toolPreferences.setImageGenApiKey(v) }
            t.imageGenModel?.let { v: String -> toolPreferences.setImageGenModel(v) }
            t.imageGenModelName?.let { v: String -> toolPreferences.setImageGenModelName(v) }
            t.openaiImageGenApiKey?.let { v: String -> toolPreferences.setOpenaiImageGenApiKey(v) }
            t.openaiImageGenModel?.let { v: String -> toolPreferences.setOpenaiImageGenModel(v) }
            t.openaiImageGenModelName?.let { v: String -> toolPreferences.setOpenaiImageGenModelName(v) }
            t.openaiImageGenBaseUrl?.let { v: String -> toolPreferences.setOpenaiImageGenBaseUrl(v) }
            
            t.toolVisibilities?.forEach { (toolName: String, visible: Boolean) ->
                toolPreferences.setToolVisibility(toolName, visible)
            }
        }
        
        settings.titleGeneration?.let { tg ->
            tg.modelId?.let { modelPreferences.setTitleGenerationModelId(it) }
            tg.contextStrategy?.let { modelPreferences.setTitleGenerationContextStrategy(it) }
            tg.contextN?.let { modelPreferences.setTitleGenerationContextN(it) }
            tg.autoGenerate?.let { modelPreferences.setTitleGenerationAutoGenerate(it) }
        }
    }

    private fun calculateHash(content: String): String {
        return MessageDigest.getInstance("SHA-256")
            .digest(content.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }

    private suspend fun collectLocalSettings(): AppSettings {
        val visibleTools = toolPreferences.visibleTools.first()
        val toolVisibilities = ToolType.values().associate { it.name to (it.name in visibleTools) }

        return AppSettings(
            theme = ThemeSettings(
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
            ),
            context = ContextSettings(
                maxContextMessages = contextPreferences.maxContextMessages.first()
            ),
            update = UpdateSettings(
                autoCheckUpdateEnabled = updatePreferences.autoCheckUpdateEnabled.first()
            ),
            systemPrompt = SystemPromptSettings(
                systemPrompt = modelPreferences.systemPrompt.first(),
                enableDynamicDate = modelPreferences.enableDynamicDate.first(),
                enableDynamicTimestamp = modelPreferences.enableDynamicTimestamp.first(),
                enableDynamicLocation = modelPreferences.enableDynamicLocation.first(),
                enableDynamicDeviceModel = modelPreferences.enableDynamicDeviceModel.first(),
                enableDynamicLanguage = modelPreferences.enableDynamicLanguage.first(),
                useCloudProxy = modelPreferences.useCloudProxy.first()
            ),
            tool = ToolSettings(
                webSearchEnabled = toolPreferences.webSearchEnabled.first(),
                webSearchResultCount = toolPreferences.webSearchResultCount.first(),
                webSearchEngine = toolPreferences.webSearchEngine.first(),
                tavilyApiKey = toolPreferences.tavilyApiKey.first(),
                tavilyUseProxy = toolPreferences.tavilyUseProxy.first(),
                musicSearchSource = toolPreferences.musicSearchSource.first(),
                musicSearchResultCount = toolPreferences.musicSearchResultCount.first(),
                toolVisibilities = toolVisibilities,
                imageGenBaseUrl = toolPreferences.imageGenBaseUrl.first(),
                imageGenApiKey = toolPreferences.imageGenApiKey.first(),
                imageGenModel = toolPreferences.imageGenModel.first(),
                imageGenModelName = toolPreferences.imageGenModelName.first(),
                openaiImageGenApiKey = toolPreferences.openaiImageGenApiKey.first(),
                openaiImageGenModel = toolPreferences.openaiImageGenModel.first(),
                openaiImageGenModelName = toolPreferences.openaiImageGenModelName.first(),
                openaiImageGenBaseUrl = toolPreferences.openaiImageGenBaseUrl.first()
            ),
            titleGeneration = TitleGenerationSettings(
                modelId = modelPreferences.titleGenerationModelId.first(),
                contextStrategy = modelPreferences.titleGenerationContextStrategy.first(),
                contextN = modelPreferences.titleGenerationContextN.first(),
                autoGenerate = modelPreferences.titleGenerationAutoGenerate.first()
            )
        )
    }

    private suspend fun syncModels(client: S3Client, bucketName: String, remoteVersionRaw: Long): Long {
        val groups = modelConfigDao.getAllModelGroups().first()
        val models = modelConfigDao.getAllModels().first()
        val selectedModelId = modelPreferences.selectedModelId.first()
        
        val backupData = BackupData(
            version = 1,
            exportedAt = System.currentTimeMillis(),
            modelGroups = groups,
            models = models,
            selectedModelId = selectedModelId,
            conversations = emptyList(),
            appSettings = null
        )
        val currentJson = gson.toJson(backupData)
        val currentHash = calculateHash(currentJson)
        val lastSyncedHash = s3Preferences.s3ModelsHash.first()
        val localVersionRaw = s3Preferences.s3ModelsVersion.first()

        // 矫正机制：强制本地版本号至少为 1
        val localVersion = maxOf(1L, localVersionRaw)
        val remoteVersion = maxOf(0L, remoteVersionRaw)

        return when {
            remoteVersion > localVersion -> {
                // 1. 云端领先：下载
                try {
                    val remoteJson = downloadString(client, bucketName, "data/models.json")
                    if (remoteJson != null) {
                        val remoteData = gson.fromJson(remoteJson, BackupData::class.java)
                        remoteData.modelGroups.forEach { modelConfigDao.insertGroup(it) }
                        remoteData.models.forEach { modelConfigDao.insertModel(it) }
                        
                        // 应用远程选中的模型 ID
                        remoteData.selectedModelId?.let { 
                            modelPreferences.setSelectedModelId(it) 
                        }
                        
                        val updatedJson = gson.toJson(remoteData)
                        val updatedHash = calculateHash(updatedJson)
                        s3Preferences.setS3ModelsVersion(remoteVersion)
                        s3Preferences.setS3ModelsHash(updatedHash)
                        remoteVersion
                    } else localVersion
                } catch (e: Exception) {
                    e.printStackTrace()
                    localVersion
                }
            }
            localVersion > remoteVersion -> {
                // 2. 本地领先：上传
                uploadString(client, bucketName, "data/models.json", currentJson)
                if (localVersion != localVersionRaw) {
                    s3Preferences.setS3ModelsVersion(localVersion)
                }
                localVersion
            }
            currentHash != lastSyncedHash -> {
                // 3. 相等但有变动：递增
                val newVersion = remoteVersion + 1
                uploadString(client, bucketName, "data/models.json", currentJson)
                s3Preferences.setS3ModelsVersion(newVersion)
                s3Preferences.setS3ModelsHash(currentHash)
                newVersion
            }
            else -> localVersion
        }
    }

    private suspend fun syncConversations(
        client: S3Client, 
        bucketName: String, 
        remoteManifest: SyncManifest,
        finalSettingsVersion: Long,
        finalModelsVersion: Long,
        onProgress: (String) -> Unit
    ) {
        // 2. 获取本地对话数据 (包含已标记删除的)
        val localConversations = chatDao.getAllConversationsIncludingDeleted().first()
        val localMetaMap = localConversations.associate { it.uuid to ConversationMeta(
            uuid = it.uuid,
            lastMessageTime = it.lastMessageTime.time,
            messageCount = it.messageCount,
            isDeleted = it.isDeleted,
            deletedAt = it.deletedAt?.time,
            version = it.version
        ) }

        val toUpload = mutableListOf<Conversation>()
        val toDownload = mutableListOf<String>()

        // 3. 确定需要上传的 (本地独有，或本地较新)
        localConversations.forEach { local ->
            val remoteMeta = remoteManifest.conversations[local.uuid]
            if (remoteMeta == null) {
                // 本地独有 -> 上传
                toUpload.add(local)
            } else {
                // 两边都有 -> 改用 version 比对
                if (local.version > remoteMeta.version) {
                    toUpload.add(local)
                } else if (local.version == remoteMeta.version) {
                     // Fallback: compare timestamps
                    val localTime = maxOf(local.lastMessageTime.time, local.deletedAt?.time ?: 0L)
                    val remoteTime = maxOf(remoteMeta.lastMessageTime, remoteMeta.deletedAt ?: 0L)
                    if (localTime > remoteTime) {
                        toUpload.add(local)
                    }
                }
            }
        }

        // 4. 确定需要下载的 (云端独有，或云端较新)
        remoteManifest.conversations.forEach { (uuid, remoteMeta) ->
            val localMeta = localMetaMap[uuid]
            if (localMeta == null) {
                // 云端独有 -> 下载
                toDownload.add(uuid)
            } else {
                // 两边都有 -> 改用 version 比对
                if (remoteMeta.version > localMeta.version) {
                    toDownload.add(uuid)
                } else if (remoteMeta.version == localMeta.version) {
                    // Fallback
                    val localTime = maxOf(localMeta.lastMessageTime, localMeta.deletedAt ?: 0L)
                    val remoteTime = maxOf(remoteMeta.lastMessageTime, remoteMeta.deletedAt ?: 0L)
                    if (remoteTime > localTime) {
                        toDownload.add(uuid)
                    }
                }
            }
        }

        // 5. 执行上传
        for (c in toUpload) {
            val messages = try {
                chatDao.getMessagesForConversationIncludingDeleted(c.id).first()
            } catch (e: Exception) {
                if (e.message?.contains("Row too big", ignoreCase = true) == true) {
                    onProgress("对话 ${c.title} 数据过大，跳过同步此对话")
                }
                e.printStackTrace()
                continue
            }
            
            val backupMessages = mutableListOf<BackupMessage>()
            
            for (m in messages) {
                var updatedContent = m.content
                val updatedImagePaths = m.imagePaths.toMutableList()
                var wasModified = false

                // 1. 处理 imagePaths 中的本地图片
                m.imagePaths.forEachIndexed { index, path ->
                    if (!path.startsWith("url:") && !path.startsWith("http")) {
                        val file = File(path)
                        if (file.exists()) {
                            try {
                                val result = s3UploadRepository.uploadFile(file) { }
                                if (result.isSuccess) {
                                    val s3Url = result.getOrThrow()
                                    val imgTag = "\n<img src=\"$s3Url\" />"
                                    if (!updatedContent.contains(s3Url)) {
                                        updatedContent += imgTag
                                    }
                                    // 修复：直接使用 S3 URL，不再添加干扰前缀
                                    updatedImagePaths[index] = s3Url
                                    file.delete()
                                    wasModified = true
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    }
                }

                // 2. 处理 content 中的 Base64 数据 (如果存在)
                // 匹配 data:image/xxx;base64,xxxx
                val base64Regex = Regex("""data:image/[^;]+;base64,[A-Za-z0-9+/=]+""")
                val matches = base64Regex.findAll(updatedContent).toList()
                
                for (match in matches) {
                    val base64Data = match.value
                    try {
                        // 将 Base64 转为临时文件上传
                        val parts = base64Data.split(",")
                        if (parts.size == 2) {
                            val header = parts[0]
                            val data = parts[1]
                            val extension = header.substringAfter("image/").substringBefore(";")
                            val bytes = android.util.Base64.decode(data, android.util.Base64.DEFAULT)
                            
                            val tempFile = File.createTempFile("sync_b64_", ".$extension", context.cacheDir)
                            tempFile.writeBytes(bytes)
                            
                            val result = s3UploadRepository.uploadFile(tempFile) { }
                            if (result.isSuccess) {
                                val s3Url = result.getOrThrow()
                                // 替换 content 中的 base64 为 img 标签
                                updatedContent = updatedContent.replace(base64Data, "<img src=\"$s3Url\" />")
                                wasModified = true
                            }
                            tempFile.delete()
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                val finalMsg = if (wasModified) {
                    val updatedMsg = m.copy(content = updatedContent, imagePaths = updatedImagePaths)
                    chatDao.updateMessage(updatedMsg)
                    updatedMsg
                } else {
                    m
                }

                backupMessages.add(
                     BackupMessage(
                         uuid = finalMsg.uuid,
                         content = finalMsg.content,
                         isFromUser = finalMsg.isFromUser,
                         timestamp = finalMsg.timestamp.time,
                         isError = finalMsg.isError,
                         modelDisplayName = finalMsg.modelDisplayName,
                         imagePaths = finalMsg.imagePaths,
                         metadata = finalMsg.metadata
                     )
                 )
             }
             
             // 如果对话中的消息被修改（如图片转为 S3 链接），更新对话的 lastMessage 以保持 UI 一致
             val lastMsg = backupMessages.lastOrNull()
             if (lastMsg != null) {
                 val conversation = chatDao.getConversation(c.id)
                 if (conversation != null && conversation.lastMessage != lastMsg.content) {
                     chatDao.updateConversation(conversation.copy(lastMessage = lastMsg.content))
                 }
             }
             
             val backupConv = BackupConversation(
                title = c.title,
                uuid = c.uuid,
                lastMessage = c.lastMessage,
                lastMessageTime = c.lastMessageTime.time,
                messageCount = c.messageCount,
                messages = backupMessages,
                isDeleted = c.isDeleted,
                deletedAt = c.deletedAt?.time,
                version = c.version
            )
            
            val json = gson.toJson(backupConv)
            uploadString(client, bucketName, "data/conversations/${c.uuid}.json", json)
        }

        // 6. 执行下载与合并
        for (uuid in toDownload) {
            val json = downloadString(client, bucketName, "data/conversations/$uuid.json") ?: continue
            val remoteConv = gson.fromJson(json, BackupConversation::class.java)
            
            val localConv = chatDao.getConversationByUuid(remoteConv.uuid ?: "")
            
            if (localConv == null) {
                // 本地不存在 -> 插入新对话
                val newConv = Conversation(
                    title = remoteConv.title,
                    uuid = remoteConv.uuid ?: java.util.UUID.randomUUID().toString(),
                    lastMessage = remoteConv.lastMessage,
                    lastMessageTime = Date(remoteConv.lastMessageTime),
                    messageCount = remoteConv.messageCount,
                    isDeleted = remoteConv.isDeleted,
                    deletedAt = remoteConv.deletedAt?.let { Date(it) },
                    version = remoteConv.version
                )
                val newId = chatDao.insertConversation(newConv)
                
                // 插入该对话的所有消息
                remoteConv.messages.forEach { msg ->
                    chatDao.insertMessage(
                        ChatMessage(
                            conversationId = newId,
                            uuid = msg.uuid ?: java.util.UUID.randomUUID().toString(),
                            content = msg.content,
                            isFromUser = msg.isFromUser,
                            timestamp = Date(msg.timestamp),
                            isError = msg.isError ?: false,
                            modelDisplayName = msg.modelDisplayName,
                            imagePaths = msg.imagePaths ?: emptyList(),
                            metadata = msg.metadata
                        )
                    )
                }
            } else {
                // 本地已存在 -> 更新对话元数据与消息列表
                val updatedConv = localConv.copy(
                    title = remoteConv.title,
                    lastMessage = remoteConv.lastMessage,
                    lastMessageTime = Date(remoteConv.lastMessageTime),
                    messageCount = remoteConv.messageCount,
                    isDeleted = remoteConv.isDeleted,
                    deletedAt = remoteConv.deletedAt?.let { Date(it) },
                    version = remoteConv.version
                )
                chatDao.updateConversation(updatedConv)
                
                // 重写所有消息以保持与云端一致
                chatDao.deleteMessagesByConversationId(localConv.id)
                remoteConv.messages.forEach { msg ->
                    chatDao.insertMessage(
                        ChatMessage(
                            conversationId = localConv.id,
                            uuid = msg.uuid ?: java.util.UUID.randomUUID().toString(),
                            content = msg.content,
                            isFromUser = msg.isFromUser,
                            timestamp = Date(msg.timestamp),
                            isError = msg.isError ?: false,
                            modelDisplayName = msg.modelDisplayName,
                            imagePaths = msg.imagePaths ?: emptyList(),
                            metadata = msg.metadata
                        )
                    )
                }
            }
        }

        // 7. 更新并上传最终合并清单
        val finalConversations = chatDao.getAllConversationsIncludingDeleted().first()
        val finalMetaMap = finalConversations.associate { 
            it.uuid to ConversationMeta(
                uuid = it.uuid,
                lastMessageTime = it.lastMessageTime.time,
                messageCount = it.messageCount,
                isDeleted = it.isDeleted,
                deletedAt = it.deletedAt?.time,
                version = it.version
            ) 
        }
        val finalManifest = SyncManifest(
            conversations = finalMetaMap, 
            settingsVersion = finalSettingsVersion,
            modelsVersion = finalModelsVersion,
            updatedAt = System.currentTimeMillis()
        )
        uploadString(client, bucketName, "data/manifest.json", gson.toJson(finalManifest))
    }

    private suspend fun uploadString(client: S3Client, bucketName: String, key: String, content: String) {
        val bytes = content.toByteArray()
        val request = PutObjectRequest {
            bucket = bucketName
            this.key = key
            body = ByteStream.fromBytes(bytes)
            contentType = "application/json"
        }
        client.putObject(request)
    }

    private suspend fun downloadString(client: S3Client, bucketName: String, key: String): String? {
        return try {
            val request = GetObjectRequest {
                bucket = bucketName
                this.key = key
            }
            client.getObject(request) { response ->
                response.body?.decodeToString()
            }
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun listObjects(client: S3Client, bucketName: String, prefix: String): List<String> {
        val request = ListObjectsV2Request {
            bucket = bucketName
            this.prefix = prefix
        }
        val response = client.listObjectsV2(request)
        return response.contents?.mapNotNull { it.key } ?: emptyList()
    }
}
