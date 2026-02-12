package com.glassous.aime.data.repository

import com.glassous.aime.BuildConfig
import com.glassous.aime.data.ChatMessage
import com.glassous.aime.data.model.SharedConversationRequest
import com.glassous.aime.data.model.SharedConversationResponse
import com.glassous.aime.data.model.SharedMessageDto
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.serializer.KotlinXSerializer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

import kotlinx.serialization.json.Json

object SupabaseShareRepository {

    private val supabase: SupabaseClient = createSupabaseClient(
        supabaseUrl = BuildConfig.SUPABASE_URL,
        supabaseKey = BuildConfig.SUPABASE_KEY
    ) {
        install(Postgrest)
        defaultSerializer = KotlinXSerializer(Json { ignoreUnknownKeys = true })
    }

    suspend fun uploadConversation(title: String, model: String, messages: List<ChatMessage>): String {
        return withContext(Dispatchers.IO) {
            val dtoList = messages
                .filter { !it.isError && it.content.isNotBlank() }
                .map { message ->
                    SharedMessageDto(
                        role = if (message.isFromUser) "user" else "model",
                        content = message.content
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
}
