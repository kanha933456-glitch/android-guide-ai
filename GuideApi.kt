package com.guideai.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object GuideApi {
    suspend fun explain(language: String, screenText: String): Result<String> = withContext(Dispatchers.IO) {
        val endpoint = BuildConfig.GUIDE_API_URL
        if (endpoint.isBlank()) return@withContext Result.failure(IllegalStateException("Guide API URL is not configured"))
        runCatching {
            val connection = URL(endpoint).openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.connectTimeout = 10_000
            connection.readTimeout = 20_000
            connection.doOutput = true
            JSONObject().apply { put("language", language); put("screenContext", screenText.take(4000)) }.toString().also { connection.outputStream.use { stream -> stream.write(it.toByteArray()) } }
            val body = (if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream).bufferedReader().use { it.readText() }
            if (connection.responseCode !in 200..299) error("Guide API error ${connection.responseCode}")
            JSONObject(body).optString("guidance").ifBlank { error("Guide API returned no guidance") }
        }
    }
}
