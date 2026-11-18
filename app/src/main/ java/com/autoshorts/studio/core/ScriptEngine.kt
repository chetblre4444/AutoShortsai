package com.autoshorts.studio.core

import com.autoshorts.studio.data.CartoonStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

data class ThreePartScript(
    val part1Prompt: String,
    val part2Prompt: String,
    val part3Prompt: String,
    val title: String
)

class ScriptEngine {
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(45, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    suspend fun generateThreePartScript(
        apiKey: String,
        topic: String,
        style: CartoonStyle
    ): ThreePartScript = withContext(Dispatchers.IO) {
        require(apiKey.isNotBlank()) { "Gemini API Key не задан" }

        val systemInstruction = """
            You are a short-form cartoon director. Create a cohesive 30-second video script about "$topic".
            Divide the visual action strictly into 3 chronological 10-second segments.
            Every segment description must be a self-contained visual prompt ${style.promptModifier}.
            Respond ONLY with valid JSON matching this schema:
            {
              "title": "Short catchy video title with #shorts",
              "part1": "Visual prompt for seconds 0-10...",
              "part2": "Visual prompt for seconds 10-20 continuing smoothly from part 1...",
              "part3": "Visual prompt for seconds 20-30 concluding the story..."
            }
        """.trimIndent()

        val payload = JSONObject().apply {
            put("contents", JSONArray().put(
                JSONObject().put("parts", JSONArray().put(
                    JSONObject().put("text", systemInstruction)
                ))
            ))
            put("generationConfig", JSONObject().apply {
                put("responseMimeType", "application/json")
                put("temperature", 0.7)
            })
        }

        val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$apiKey"
        val request = Request.Builder()
            .url(url)
            .post(payload.toString().toRequestBody("application/json".toMediaType()))
            .build()

        httpClient.newCall(request).execute().use { response ->
            val bodyStr = response.body?.string() ?: throw IOException("Пустой ответ от Gemini API")
            if (!response.isSuccessful) {
                throw IOException("Ошибка Gemini API (${response.code}): $bodyStr")
            }

            val rootJson = JSONObject(bodyStr)
            val contentText = rootJson
                .getJSONArray("candidates")
                .getJSONObject(0)
                .getJSONObject("content")
                .getJSONArray("parts")
                .getJSONObject(0)
                .getString("text")

            val parsed = JSONObject(contentText)
            ThreePartScript(
                title = parsed.optString("title", "AutoShorts Video #shorts"),
                part1Prompt = "${parsed.getString("part1")}, ${style.promptModifier}",
                part2Prompt = "${parsed.getString("part2")}, ${style.promptModifier}",
                part3Prompt = "${parsed.getString("part3")}, ${style.promptModifier}"
            )
        }
    }
}

