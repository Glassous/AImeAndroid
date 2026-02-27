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

    suspend fun uploadConversation(title: String, model: String, messages: List<ChatMessage>): String {
        return withContext(Dispatchers.IO) {
            val dtoList = messages
                .filter { !it.isError && (it.content.isNotBlank() || it.imagePaths.isNotEmpty()) }
                .map { message ->
                    var content = message.content
                    if (message.imagePaths.isNotEmpty()) {
                        val imageTags = message.imagePaths.mapNotNull { path ->
                            encodeImageToBase64(path)?.let { base64 ->
                                "<img src=\"data:image/jpeg;base64,$base64\"/>"
                            }
                        }.joinToString("\n")
                        
                        if (imageTags.isNotBlank()) {
                            content = if (content.isBlank()) imageTags else "$content\n$imageTags"
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
