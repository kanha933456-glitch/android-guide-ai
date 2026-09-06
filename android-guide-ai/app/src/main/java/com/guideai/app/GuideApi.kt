package com.guideai.app

import android.util.Base64
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
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

            val jsonPayload = JsonObject()
            val contentsArray = JsonArray()

            for ((role, content) in conversationHistory) {
                val turnObj = JsonObject()
                turnObj.addProperty("role", if (role == "user") "user" else "model")
                
                val partsArr = JsonArray()
                val textPart = JsonObject()
                textPart.addProperty("text", content)
                partsArr.add(textPart)
                
                turnObj.add("parts", partsArr)
                contentsArray.add(turnObj)
            }

            val currentContentObject = JsonObject()
            currentContentObject.addProperty("role", "user")
            
            val partsArray = JsonArray()
            val textPart = JsonObject()
            textPart.addProperty("text", "$systemInstruction\n\nUser Question: $promptText")
            partsArray.add(textPart)

            if (cleanImage.isNotBlank()) {
                val imagePart = JsonObject()
                val inlineData = JsonObject()
                inlineData.addProperty("mime_type", "image/jpeg")
                inlineData.addProperty("data", cleanImage)
                imagePart.add("inline_data", inlineData)
                partsArray.add(imagePart)
            }

            currentContentObject.add("parts", partsArray)
            contentsArray.add(currentContentObject)
            jsonPayload.add("contents", contentsArray)

            val mediaType = "application/json; charset=utf-8".toMediaType()
            val body = jsonPayload.toString().toRequestBody(mediaType)

            val request = Request.Builder()
                .url(url)
                .post(body)
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()

            if (response.isSuccessful && responseBody != null) {
                val jsonResponse = gson.fromJson(responseBody, JsonObject::class.java)
                val candidates = jsonResponse.getAsJsonArray("candidates")

                if (candidates != null && candidates.size() > 0) {
                    val firstCandidate = candidates.get(0).asJsonObject
                    val contentObj = firstCandidate.getAsJsonObject("content")
                    val parts = contentObj.getAsJsonArray("parts")
                    val textResult = parts.get(0).asJsonObject.get("text").asString

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
                Result.failure(Exception("API Error Code: ${response.code}"))
            }

        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
