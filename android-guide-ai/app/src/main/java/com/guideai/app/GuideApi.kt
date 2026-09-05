package com.guideai.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object GuideApi {

    // Conversation history — app session me yaad rahega, band hone par reset
    private val conversationHistory = mutableListOf<Pair<String, String>>() // role, content

    fun clearHistory() {
        conversationHistory.clear()
    }

    suspend fun explainVision(question: String, image: String): Result<String> = withContext(Dispatchers.IO) {
        // Naya /api/guide/chat endpoint use kar rahe hain
        val endpoint = BuildConfig.GUIDE_API_URL.replace("/api/guide", "/api/guide/chat")

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

            // History array build karo
            val historyArray = JSONArray()
            for ((role, content) in conversationHistory) {
                val turn = JSONObject().apply {
                    put("role", role)
                    put("content", content)
                }
                historyArray.put(turn)
            }

            val jsonPayload = JSONObject().apply {
                put("image", formattedImage)
                put("question", question)
                put("history", historyArray)
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

            // History me current turn save karo
            if (question.isNotBlank()) {
                conversationHistory.add(Pair("user", question))
            }
            conversationHistory.add(Pair("assistant", resultText))

            // History zyada badi na ho — last 10 turns hi rakho
            while (conversationHistory.size > 10) {
                conversationHistory.removeAt(0)
            }

            resultText
        }
    }
}
