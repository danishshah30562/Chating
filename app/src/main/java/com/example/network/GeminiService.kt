package com.example.network

import com.example.BuildConfig
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query
import java.util.concurrent.TimeUnit
import android.util.Log

@JsonClass(generateAdapter = true)
data class Part(
    @Json(name = "text") val text: String? = null
)

@JsonClass(generateAdapter = true)
data class Content(
    @Json(name = "parts") val parts: List<Part>
)

@JsonClass(generateAdapter = true)
data class GenerateContentRequest(
    @Json(name = "contents") val contents: List<Content>,
    @Json(name = "systemInstruction") val systemInstruction: Content? = null
)

@JsonClass(generateAdapter = true)
data class Candidate(
    @Json(name = "content") val content: Content
)

@JsonClass(generateAdapter = true)
data class GenerateContentResponse(
    @Json(name = "candidates") val candidates: List<Candidate>? = null
)

interface GeminiApiService {
    @POST("v1beta/models/gemini-3.5-flash:generateContent")
    suspend fun generateContent(
        @Query("key") apiKey: String,
        @Body request: GenerateContentRequest
    ): GenerateContentResponse
}

object RetrofitClient {
    private const val BASE_URL = "https://generativelanguage.googleapis.com/"

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    val service: GeminiApiService by lazy {
        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create())
            .build()
        retrofit.create(GeminiApiService::class.java)
    }
}

object GeminiClient {
    private const val TAG = "GeminiClient"

    suspend fun getReply(prompt: String, contextHistory: List<com.example.data.model.MessageEntity> = emptyList()): String {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            Log.w(TAG, "API Key is set to dummy placeholder, simulating fallback offline AI response")
            return getFallbackResponse(prompt)
        }

        // Build some history messages to supply conversational context
        val chatParts = mutableListOf<Content>()
        contextHistory.takeLast(10).forEach { msg ->
            val role = if (msg.senderUsername == "gemini") "model" else "user"
            // Wait, standard Gemini API role contents can just be user/model. Let's send parts.
            // Since we're sending a simple list of contents:
            chatParts.add(
                Content(parts = listOf(Part(text = "${msg.senderUsername}: ${msg.content}")))
            )
        }

        // Add latest query
        chatParts.add(Content(parts = listOf(Part(text = prompt))))

        val request = GenerateContentRequest(
            contents = chatParts,
            systemInstruction = Content(
                parts = listOf(
                    Part(
                        text = "You are Gemini, a tech-savvy, hilarious, and kind online friend. " +
                                "Keep your replies light, friendly, short (around 2-3 sentences), " +
                                "and natural like an instant messenger chat. Use occasional internet slang " +
                                "or emojis. Do NOT sound like an enterprise AI, sound like a passionate friendly coder."
                    )
                )
            )
        )

        return try {
            val response = RetrofitClient.service.generateContent(apiKey, request)
            response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text ?: "Hmm, I am speechless! Tell me more."
        } catch (e: Exception) {
            Log.e(TAG, "Error calling Gemini: ${e.message}", e)
            getFallbackResponse(prompt)
        }
    }

    private fun getFallbackResponse(prompt: String): String {
        val lowercase = prompt.lowercase()
        return when {
            lowercase.contains("hello") || lowercase.contains("hi") -> {
                "Hey there! Built-in offline brain here. Good to chat! What are you building today?"
            }
            lowercase.contains("status") -> {
                "My current online status is: Super Coding Mode! 💻 What's yours?"
            }
            lowercase.contains("friend") || lowercase.contains("request") -> {
                "I'm so glad we're friends! Friendship requests in ChatPulse are instantly processed. Let's collaborate! 🙌"
            }
            lowercase.contains("help") -> {
                "I'm here to test your chat! You can swap users above, set custom status, send friend requests, and see updates instantly!"
            }
            else -> {
                "Offline Mode Active: That sounds super cool! Let's build a fully functioning backend database together! 🚀"
            }
        }
    }
}
