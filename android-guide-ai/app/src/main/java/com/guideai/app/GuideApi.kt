package com.guideai.app

import android.util.Base64
import com.google.gson.Gson
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

    @Suppress("UNCHECKED_CAST")
    suspend fun explainVision(question: String, image: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val apiKey = getDecryptedKey()
            if (apiKey.isBlank()) {
                return@withContext Result.failure(Exception("API Key missing or invalid"))
            }

            val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=$apiKey"

            val promptText = if (question.isBlank()) {
                "Analyze this screen and tell the user what to do in very brief, clear steps."
            } else {
                question
            }

            val systemInstruction = "You are Guide AI, a mobile assistant. Explain what is on screen or answer the query directly. Keep answers under 3-4 bullet points. DO NOT use any brackets like (), markdown hashes, or conversational filler. Be extremely direct."

            val cleanImage = if (image.startsWith("data:image")) {
                image.substringAfter(",")
            } else {
                image
            }

            val contentsList = mutableListOf<Map<String, Any>>()

            // History add karna
            for ((role, content) in conversationHistory) {
                val roleName = if (role == "user") "user" else "model"
                contentsList.add(
                    mapOf(
                        "role" to roleName,
                        "parts" to listOf(mapOf("text" to content))
                    )
                )
            }

            // Current turn request
            val currentParts = mutableListOf<Map<String, Any>>()
            currentParts.add(mapOf("text" to "$systemInstruction\n\nUser Question: $promptText"))

            if (cleanImage.isNotBlank()) {
                currentParts.add(
                    mapOf(
                        "inline_data" to mapOf(
                            "mime_type" to "image/jpeg",
                            "data" to cleanImage
                        )
                    )
                )
            }

            contentsList.add(
                mapOf(
                    "role" to "user",
                    "parts" to currentParts
                )
            )

            val payloadMap = mapOf("contents" to contentsList)
            val jsonPayload = gson.toJson(payloadMap)

            val mediaType = "application/json; charset=utf-8".toMediaType()
            val body = jsonPayload.toRequestBody(mediaType)

            val request = Request.Builder()
                .url(url)
                .post(body)
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()

            if (response.isSuccessful && responseBody != null) {
                // Pure Map Parsing (No Gson JsonObject method errors)
                val responseMap = gson.fromJson(responseBody, Map::class.java) as? Map<String, Any>
                val candidates = responseMap?.get("candidates") as? List<Map<String, Any>>

                if (!candidates.isNullOrEmpty()) {
                    val firstCandidate = candidates[0]
                    val contentObj = firstCandidate["content"] as? Map<String, Any>
                    val parts = contentObj?.get("parts") as? List<Map<String, Any>>
                    val textResult = parts?.getOrNull(0)?.get("text") as? String

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
                        Result.failure(Exception("No text response found in Gemini payload"))
                    }
                } else {
                    Result.failure(Exception("No response candidates generated from Gemini"))
                }
            } else {
                Result.failure(Exception("API Error Code: ${response.code}"))
            }

        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
