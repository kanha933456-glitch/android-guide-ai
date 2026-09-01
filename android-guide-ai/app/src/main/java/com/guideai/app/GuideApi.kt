package com.guideai.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object GuideApi {
    suspend fun explainVision(question: String, image: String): Result<String> = withContext(Dispatchers.IO) {
        val endpoint = BuildConfig.GUIDE_API_URL.replace("/api/guide", "/api/guide/vision")
        if (endpoint.isBlank() || image.isBlank()) return@withContext Result.failure(IllegalStateException("Vision capture is not configured"))
        
        runCatching {
            val formattedImage = if (!image.startsWith("data:image")) {
                "data:image/jpeg;base64,$image"
            } else {
                image
            }

            val connection = URL(endpoint).openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.connectTimeout = 15_000
            connection.readTimeout = 30_000
            connection.doOutput = true

            val jsonPayload = JSONObject().apply { 
                put("image", formattedImage)
                put("question", question.take(1000)) 
            }.toString()

            connection.outputStream.use { stream -> stream.write(jsonPayload.toByteArray(Charsets.UTF_8)) }

            val responseCode = connection.responseCode
            val body = (if (responseCode in 200..299) connection.inputStream else connection.errorStream)?.bufferedReader()?.use { it.readText() } ?: ""

            if (responseCode !in 200..299) error("Vision API error $responseCode: $body")
            
            val jsonResponse = JSONObject(body)
            jsonResponse.optString("guidance").ifBlank { 
                jsonResponse.optString("text").ifBlank { 
                    error("Vision API returned no guidance") 
                } 
            }
        }
    }
}
