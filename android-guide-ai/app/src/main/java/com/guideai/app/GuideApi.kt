package com.guideai.app

import android.util.Base64
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

object GuideApi {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    // Aapki Base64 Key Yahan Paste Kar Di Gayi Hai
    private const val ENCODED_KEY = "QVEuQWI4Uk42SjVtby1qS3Zhb1hnaXpLRm9aTE0xbEtNVWJuWHZMRGN2d2ltUk42T1ZQSXc="

    private val conversationHistory = mutableListOf<Pair<String, String>>()

    fun clearHistory() {
        conversationHistory.clear()
    }

    private fun getDecryptedKey(): String {
        return try {
            String(Base64.decode(ENCODED_KEY, Base64.DEFAULT)).trim()
        } catch (e: Exception) {
            ""
        }
    }

    suspend fun explainVision(question: String, image: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val apiKey = getDecryptedKey()
            if (apiKey.isBlank()) {
                return@withContext Result.failure(Exception("API Key missing or invalid"))
            }

            val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash-lite:generateContent?key=$apiKey"

            val promptText = if (question.isBlank()) {
                "Analyze this screen and tell the user what to do in very brief, clear steps."
            } else {
                question
            }

            val systemInstruction = "You are Guide AI, a mobile assistant. Explain what is on screen or answer the query directly. Keep answers under 3-4 bullet points.

            val cleanImage = if (image.startsWith("data:image")) {
                image.substringAfter(",")
            } else {
                image
            }

            val contentsList = mutableListOf<Content>()

            // Conversation history add karein
            for ((role, contentText) in conversationHistory) {
                val turnRole = if (role == "user") "user" else "model"
                contentsList.add(
                    Content(
                        role = turnRole,
                        parts = listOf(Part(text = contentText))
                    )
                )
            }

            // Current request parts
            val currentParts = mutableListOf<Part>()
            currentParts.add(Part(text = "$systemInstruction\n\nUser Question: $promptText"))

            if (cleanImage.isNotBlank()) {
                currentParts.add(
                    Part(
                        inlineData = InlineData(
                            mimeType = "image/jpeg",
                            data = cleanImage
                        )
                    )
                )
            }

            contentsList.add(Content(role = "user", parts = currentParts))

            val requestBodyObj = GeminiRequest(contents = contentsList)
            val jsonString = gson.toJson(requestBodyObj)

            val mediaType = "application/json; charset=utf-8".toMediaType()
            val body = jsonString.toRequestBody(mediaType)

            val request = Request.Builder()
                .url(url)
                .post(body)
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()

            if (response.isSuccessful && responseBody != null) {
                val geminiResponse = gson.fromJson(responseBody, GeminiResponse::class.java)
                val textResult = geminiResponse.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text

                if (!textResult.isNullOrEmpty()) {
                    if (question.isNotBlank()) {
                        conversationHistory.add(Pair("user", question))
                    }
                    conversationHistory.add(Pair("assistant", textResult))

                    while (conversationHistory.size > 10) {
                        conversationHistory.removeAt(0)
                    }

                    Result.success(textResult)
                } else {
                    Result.failure(Exception("No response text found in Gemini response"))
                }
            } else {
                Result.failure(Exception("API Error Code: ${response.code}"))
            }

        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // --- Request Models ---
    private data class GeminiRequest(
        val contents: List<Content>
    )

    private data class Content(
        val role: String,
        val parts: List<Part>
    )

    private data class Part(
        val text: String? = null,
        @SerializedName("inline_data") val inlineData: InlineData? = null
    )

    private data class InlineData(
        @SerializedName("mime_type") val mimeType: String,
        val data: String
    )

    // --- Response Models ---
    private data class GeminiResponse(
        val candidates: List<Candidate>?
    )

    private data class Candidate(
        val content: ResponseContent?
    )

    private data class ResponseContent(
        val parts: List<ResponsePart>?
    )

    private data class ResponsePart(
        val text: String?
    )
}
