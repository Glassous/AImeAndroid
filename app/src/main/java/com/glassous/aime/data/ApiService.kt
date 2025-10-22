package com.glassous.aime.data

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

data class ChatRequest(
    val message: String,
    val conversationId: Long? = null
)

data class ChatResponse(
    val message: String,
    val conversationId: Long,
    val timestamp: Long = System.currentTimeMillis()
)

interface ApiService {
    @POST("chat")
    suspend fun sendMessage(@Body request: ChatRequest): Response<ChatResponse>
}

// Mock API Service Implementation
class MockApiService : ApiService {
    override suspend fun sendMessage(request: ChatRequest): Response<ChatResponse> {
        // Simulate network delay
        kotlinx.coroutines.delay(1000)
        
        val responses = listOf(
            "这是一个很有趣的问题！让我来为您详细解答。",
            "根据您的描述，我建议您可以尝试以下几种方法。",
            "感谢您的提问，这确实是一个值得深入探讨的话题。",
            "我理解您的困惑，让我为您提供一些实用的建议。",
            "这个问题涉及多个方面，我会尽量为您全面分析。"
        )
        
        val response = ChatResponse(
            message = responses.random(),
            conversationId = request.conversationId ?: 1L
        )
        
        return Response.success(response)
    }
}