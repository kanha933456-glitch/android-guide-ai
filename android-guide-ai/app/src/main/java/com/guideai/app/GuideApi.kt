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
            val connection = URL(endpoint).openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.connectTimeout = 10_000
            connection.readTimeout = 30_000
            connection.doOutput = true
            JSONObject().apply { put("image", image); put("question", question.take(1000)) }.toString().also { connection.outputStream.use { stream -> stream.write(it.toByteArray()) } }
            val body = (if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream).bufferedReader().use { it.readText() }
            if (connection.responseCode !in 200..299) error("Vision API error ${connection.responseCode}")
            JSONObject(body).optString("guidance").ifBlank { error("Vision API returned no guidance") }
        }
    }
}
