package com.guideai.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

object GuideApi {

    private var previousInteractionId: String = ""

    fun clearHistory() {
        previousInteractionId = ""
    }

    suspend fun explainVision(
        question: String,
        image: String,
        onChunk: (String) -> Unit = {}
    ): Result<String> = withContext(Dispatchers.IO) {

        val endpoint = BuildConfig.GUIDE_API_URL.replace("/api/guide", "/api/guide/chat")

        if (endpoint.isBlank()) {
            return@withContext Result.failure(Exception("URL missing in BuildConfig"))
        }
        if (image.isBlank()) {
            return@withContext Result.failure(Exception("Image frame is empty"))
        }

        val result: Result<String> = runCatching {
            val formattedImage = if (image.startsWith("data:image")) image else "data:image/jpeg;base64,$image"

            val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Accept", "application/x-ndjson")
                connectTimeout = 15_000
                readTimeout = 60_000
                doOutput = true
            }

            val jsonPayload = JSONObject().apply {
                put("image", formattedImage)
                put("question", question)
                put("previousInteractionId", previousInteractionId)
            }.toString()

            connection.outputStream.use { os ->
                os.write(jsonPayload.toByteArray(Charsets.UTF_8))
            }

            val responseCode = connection.responseCode

            if (responseCode !in 200..299) {
                val errorText = connection.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                val errJson = runCatching { JSONObject(errorText) }.getOrNull()
                val serverMsg = errJson?.optString("message") ?: errorText
                error("HTTP $responseCode: $serverMsg")
            }

            val reader = BufferedReader(InputStreamReader(connection.inputStream))
            var fullText = ""
            var newInteractionId = ""

            reader.use { br ->
                var line: String?
                while (br.readLine().also { line = it } != null) {
                    val trimmed = line?.trim() ?: continue
                    if (trimmed.isEmpty()) continue

                    try {
                        val json = JSONObject(trimmed)
                        when {
                            json.has("chunk") -> {
                                val chunk = json.getString("chunk")
                                if (chunk.isNotEmpty()) {
                                    fullText += chunk
                                    onChunk(fullText)
                                }
                                val id = json.optString("interactionId")
                                if (id.isNotBlank()) newInteractionId = id
                            }
                            json.optBoolean("done") -> {
                                val finalText = json.optString("fullText")
                                if (finalText.isNotBlank()) fullText = finalText
                                val id = json.optString("interactionId")
                                if (id.isNotBlank()) newInteractionId = id
                            }
                        }
                    } catch (e: Exception) {
                        // skip
                    }
                }
            }

            if (fullText.isBlank()) {
                error("Server returned empty guidance")
            }

            if (newInteractionId.isNotBlank()) {
                previousInteractionId = newInteractionId
            }

            fullText
        }

        result
    }
}
