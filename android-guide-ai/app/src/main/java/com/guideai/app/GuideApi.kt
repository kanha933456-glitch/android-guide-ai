package com.guideai.app

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

object GuideApi {

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

            val urlString = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash-lite:generateContent?key=$apiKey"

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

            // History
            for ((role, content) in conversationHistory) {
                val turnObj = JSONObject()
                turnObj.put("role", if (role == "user") "user" else "model")
                val partsArr = JSONArray()
                partsArr.put(JSONObject().put("text", content))
                turnObj.put("parts", partsArr)
                contentsArray.put(turnObj)
            }

            // Current prompt
            val currentObj = JSONObject()
            currentObj.put("role", "user")
            val partsArr = JSONArray()
            partsArr.put(JSONObject().put("text", "$systemInstruction\n\nUser Question: $promptText"))

            if (cleanImage.isNotBlank()) {
                val inlineData = JSONObject()
                inlineData.put("mime_type", "image/jpeg")
                inlineData.put("data", cleanImage)
                partsArr.put(JSONObject().put("inline_data", inlineData))
            }

            currentObj.put("parts", partsArr)
                contentsArray.put(currentObj)

            val payload = JSONObject()
            payload.put("contents", contentsArray)

            // Pure Java HttpURLConnection (Zero OkHttp dependency)
            val url = URL(urlString)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            connection.connectTimeout = 15000
            connection.readTimeout = 20000
            connection.doOutput = true

            val writer = OutputStreamWriter(connection.outputStream)
            writer.write(payload.toString())
            writer.flush()
            writer.close()

            val responseCode = connection.responseCode

            if (responseCode == HttpURLConnection.HTTP_OK) {
                val reader = BufferedReader(InputStreamReader(connection.inputStream))
                val responseBuilder = StringBuilder()
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    responseBuilder.append(line)
                }
                reader.close()

                val jsonResponse = JSONObject(responseBuilder.toString())
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
                    Result.failure(Exception("No candidate text found in response"))
                }
            } else {
                Result.failure(Exception("API Error Code: $responseCode"))
            }

        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
