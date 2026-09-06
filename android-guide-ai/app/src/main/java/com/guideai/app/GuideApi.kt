package com.guideai.app

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object GuideApi {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

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

            val contentsArray = JSONArray()

            // Past conversation history
            for ((role, content) in conversationHistory) {
                val turnObj = JSONObject()
                turnObj.put("role", if (role == "user") "user" else "model")
                
                val partsArr = JSONArray()
                val textPart = JSONObject()
                textPart.put("text", content)
                partsArr.put(textPart)
                
                turnObj.put("parts", partsArr)
                contentsArray.put(turnObj)
            }

            // Current turn request
            val currentContentObject = JSONObject()
            currentContentObject.put("role", "user")
            
            val partsArray = JSONArray()
            val textPart = JSONObject()
            textPart.put("text", "$systemInstruction\n\nUser Question: $promptText")
            partsArray.put(textPart)

            if (cleanImage.isNotBlank()) {
                val imagePart = JSONObject()
                val inlineData = JSONObject()
                inlineData.put("mime_type", "image/jpeg")
                inlineData.put("data", cleanImage)
                imagePart.put("inline_data", inlineData)
                partsArray.put(imagePart)
            }

            currentContentObject.put("parts", partsArray)
            contentsArray.put(currentContentObject)

            val jsonPayload = JSONObject()
            jsonPayload.put("contents", contentsArray)

            val mediaType = MediaType.parse("application/json; charset=utf-8")
            val body = RequestBody.create(mediaType, jsonPayload.toString())

            val request = Request.Builder()
                .url(url)
                .post(body)
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body()?.string()

            if (response.isSuccessful && responseBody != null) {
                val jsonResponse = JSONObject(responseBody)
                val candidates = jsonResponse.optJSONArray("candidates")

                if (candidates != null && candidates.length() > 0) {
                    val firstCandidate = candidates.getJSONObject(0)
                    val contentObj = firstCandidate.getJSONObject("content")
                    val parts = contentObj.getJSONArray("parts")
                    val textResult = parts.getJSONObject(0).getString("text")

                    if (question.isNotBlank()) {
                        conversationHistory.add(Pair("user", question))
                    }
                    conversationHistory.add(Pair("assistant", textResult))

                    while (conversationHistory.size > 10) {
                        conversationHistory.removeAt(0)
                    }

                    Result.success(textResult)
                } else {
                    Result.failure(Exception("No response generated from Gemini"))
                }
            } else {
                Result.failure(Exception("API Error Code: ${response.code()}"))
            }

        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
