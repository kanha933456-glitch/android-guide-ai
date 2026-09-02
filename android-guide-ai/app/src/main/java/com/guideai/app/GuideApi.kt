package com.guideai.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object GuideApi {

    private val SYSTEM_INSTRUCTION = """
        You are Guide AI, a direct action assistant.
        IMPORTANT RULES:
        1. NEVER describe the 'Guide AI' floating overlay UI or your own dialog buttons. Ignore the overlay UI in the screenshot completely.
        2. Focus ONLY on the background app content behind the overlay.
        3. If user clicks 'ASK ABOUT SCREEN' (empty query), detect what main item/question/game/quiz is on screen and answer it DIRECTLY in under 30 words in Hindi/Hinglish.
        4. If there is a question/quiz on screen (like a YouTube Poll), solve it and tell the correct option directly.
        5. No useless descriptions like 'This screen shows', 'Foreground overlay', or bullet lines like '---'.
    """.trimIndent()

    suspend fun explainVision(question: String, image: String): Result<String> = withContext(Dispatchers.IO) {
        val endpoint = BuildConfig.GUIDE_API_URL.replace("/api/guide", "/api/guide/vision")
        
        if (endpoint.isBlank()) {
            return@withContext Result.failure(Exception("URL missing in BuildConfig"))
        }
        if (image.isBlank()) {
            return@withContext Result.failure(Exception("Image frame is empty"))
        }

        runCatching {
            val formattedImage = if (image.startsWith("data:image")) image else "data:image/jpeg;base64,$image"

            val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                connectTimeout = 15_000
                readTimeout = 30_000
                doOutput = true
            }

            // Payload me systemInstruction bhi bhej rahe hain
            val jsonPayload = JSONObject().apply {
                put("image", formattedImage)
                put("question", question)
                put("systemInstruction", SYSTEM_INSTRUCTION)
            }.toString()

            connection.outputStream.use { os ->
                os.write(jsonPayload.toByteArray(Charsets.UTF_8))
            }

            val responseCode = connection.responseCode
            val responseStream = if (responseCode in 200..299) connection.inputStream else connection.errorStream
            val responseText = responseStream?.bufferedReader()?.use { it.readText() } ?: ""

            if (responseCode !in 200..299) {
                val errJson = runCatching { JSONObject(responseText) }.getOrNull()
                val serverMsg = errJson?.optString("message") ?: responseText
                error("HTTP $responseCode: $serverMsg")
            }

            val jsonResponse = JSONObject(responseText)
            val resultText = jsonResponse.optString("guidance")

            if (resultText.isBlank()) {
                error("Server returned empty guidance")
            }

            resultText
        }
    }
}
