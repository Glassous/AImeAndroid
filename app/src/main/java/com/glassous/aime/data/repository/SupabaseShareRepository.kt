package com.glassous.aime.data.repository

import com.glassous.aime.BuildConfig
import com.glassous.aime.data.ChatMessage
import com.glassous.aime.data.model.SharedConversationRequest
import com.glassous.aime.data.model.SharedConversationResponse
import com.glassous.aime.data.model.SharedMessageDto
import com.glassous.aime.data.model.SharedConversationRow
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.serializer.KotlinXSerializer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

import kotlinx.serialization.json.Json
import java.io.File
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import java.io.ByteArrayOutputStream

import android.content.Context
import android.net.Uri
import com.abedelazizshe.lightcompressorlibrary.CompressionListener
import com.abedelazizshe.lightcompressorlibrary.VideoCompressor
import com.abedelazizshe.lightcompressorlibrary.VideoQuality
import com.abedelazizshe.lightcompressorlibrary.config.Configuration
import com.abedelazizshe.lightcompressorlibrary.config.SaveLocation
import com.abedelazizshe.lightcompressorlibrary.config.SharedStorageConfiguration
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

object SupabaseShareRepository {

    private val supabase: SupabaseClient = createSupabaseClient(
        supabaseUrl = BuildConfig.SUPABASE_URL,
        supabaseKey = BuildConfig.SUPABASE_KEY
    ) {
        install(Postgrest)
        defaultSerializer = KotlinXSerializer(Json { ignoreUnknownKeys = true })
    }

    suspend fun getSharedConversation(uuid: String): SharedConversationRow {
        return withContext(Dispatchers.IO) {
            supabase.from("aime_shared_conversations")
                .select(columns = Columns.list("id", "title", "model", "messages")) {
                    filter {
                        eq("id", uuid)
                    }
                    single()
                }
                .decodeAs<SharedConversationRow>()
        }
    }

    suspend fun uploadConversation(context: Context, title: String, model: String, messages: List<ChatMessage>): String {
        return withContext(Dispatchers.IO) {
            var currentTotalSize = 0L
            val maxTotalSize = 3 * 1024 * 1024L // 3MB limit

            val dtoList = messages
                .filter { !it.isError && (it.content.isNotBlank() || it.imagePaths.isNotEmpty()) }
                .map { message ->
                    var content = message.content
                    currentTotalSize += content.length
                    
                    if (currentTotalSize > maxTotalSize) {
                        throw Exception("分享内容总大小超过 3MB")
                    }

                    if (message.imagePaths.isNotEmpty()) {
                        val mediaTags = message.imagePaths.mapNotNull { path ->
                            val isVideo = path.endsWith(".mp4", ignoreCase = true)
                            val isAudio = path.endsWith(".m4a", ignoreCase = true) || path.endsWith(".mp3", ignoreCase = true) || path.endsWith(".wav", ignoreCase = true)

                            val base64 = when {
                                isVideo -> encodeVideoToBase64(context, path)
                                isAudio -> encodeAudioToBase64(context, path)
                                else -> encodeImageToBase64(path)
                            }

                            if (base64 != null) {
                                currentTotalSize += base64.length
                                if (currentTotalSize > maxTotalSize) {
                                    throw Exception("分享内容总大小超过 3MB")
                                }

                                when {
                                    isVideo -> "<video src=\"data:video/mp4;base64,$base64\" controls></video>"
                                    isAudio -> {
                                        val mimeType = if (path.endsWith(".mp3", ignoreCase = true)) "audio/mpeg" else "audio/mp4"
                                        "<audio src=\"data:$mimeType;base64,$base64\" controls></audio>"
                                    }
                                    else -> "<img src=\"data:image/jpeg;base64,$base64\"/>"
                                }
                            } else {
                                null
                            }
                        }.joinToString("\n")
                        
                        if (mediaTags.isNotBlank()) {
                            content = if (content.isBlank()) mediaTags else "$content\n$mediaTags"
                        }
                    }

                    SharedMessageDto(
                        role = if (message.isFromUser) "user" else "model",
                        content = content
                    )
                }

            val request = SharedConversationRequest(
                title = title,
                model = model,
                messages = dtoList
            )

            val result = supabase.from("aime_shared_conversations")
                .insert(request) {
                    select()
                    single()
                }
                .decodeAs<SharedConversationResponse>()

            val baseUrl = BuildConfig.SHARE_BASE_URL.trimEnd('/')
            "$baseUrl/${result.id}"
        }
    }

    private suspend fun encodeAudioToBase64(context: Context, path: String): String? {
        return try {
            val file = File(path)
            if (!file.exists()) return null
            
            val fileSizeInBytes = file.length()
            val fileSizeInMB = fileSizeInBytes / (1024.0 * 1024.0)
            
            // Audio compression is not implemented yet, so we just skip if too large
            if (fileSizeInMB > 3.0) {
                return null
            }

            val bytes = file.readBytes()
            Base64.encodeToString(bytes, Base64.NO_WRAP)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private suspend fun encodeVideoToBase64(context: Context, path: String): String? {
        return try {
            val file = File(path)
            if (!file.exists()) return null
            
            // Check size. If > 3MB, try to compress to help reduce size.
            val fileSizeInBytes = file.length()
            val fileSizeInMB = fileSizeInBytes / (1024.0 * 1024.0)
            
            var videoFile = file
            
            if (fileSizeInMB > 3.0) {
                // Compress
                val compressedPath = compressVideo(context, path)
                if (compressedPath != null) {
                    videoFile = File(compressedPath)
                }
            }

            val bytes = videoFile.readBytes()
            Base64.encodeToString(bytes, Base64.NO_WRAP)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private suspend fun compressVideo(context: Context, path: String): String? = suspendCancellableCoroutine { continuation ->
        val uris = listOf(Uri.fromFile(File(path)))
        
        VideoCompressor.start(
            context = context,
            uris = uris,
            isStreamable = false,
            sharedStorageConfiguration = SharedStorageConfiguration(
                saveAt = SaveLocation.movies,
                subFolderName = "AIme-Compress"
            ),
            listener = object : CompressionListener {
                override fun onProgress(index: Int, percent: Float) {}

                override fun onStart(index: Int) {}

                override fun onSuccess(index: Int, size: Long, path: String?) {
                    if (continuation.isActive) {
                        continuation.resume(path)
                    }
                }

                override fun onFailure(index: Int, failureMessage: String) {
                    if (continuation.isActive) {
                        continuation.resume(null)
                    }
                }

                override fun onCancelled(index: Int) {
                    if (continuation.isActive) {
                        continuation.resume(null)
                    }
                }
            },
            configureWith = Configuration(
                quality = VideoQuality.MEDIUM,
                isMinBitrateCheckEnabled = true,
                videoBitrateInMbps = 2,
                disableAudio = false,
                keepOriginalResolution = false,
                videoNames = uris.map { uri -> 
                    "compressed_${System.currentTimeMillis()}_${File(path).name}" 
                }
            )
        )
    }

    private fun encodeImageToBase64(path: String): String? {
        return try {
            val file = File(path)
            if (!file.exists()) return null
            
            val options = BitmapFactory.Options()
            options.inJustDecodeBounds = true
            BitmapFactory.decodeFile(path, options)
            
            val maxDim = 1024
            var scale = 1
            if (options.outWidth > maxDim || options.outHeight > maxDim) {
                val widthRatio = Math.round(options.outWidth.toFloat() / maxDim.toFloat())
                val heightRatio = Math.round(options.outHeight.toFloat() / maxDim.toFloat())
                scale = if (widthRatio < heightRatio) widthRatio else heightRatio
            }
            
            val decodeOptions = BitmapFactory.Options()
            decodeOptions.inSampleSize = scale
            
            val bitmap = BitmapFactory.decodeFile(path, decodeOptions) ?: return null
            
            val outputStream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
            val bytes = outputStream.toByteArray()
            
            if (!bitmap.isRecycled) {
                bitmap.recycle()
            }
            
            Base64.encodeToString(bytes, Base64.NO_WRAP)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
