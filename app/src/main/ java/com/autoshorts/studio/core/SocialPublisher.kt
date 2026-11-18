package com.autoshorts.studio.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

data class PublishReport(
    val platform: String,
    val success: Boolean,
    val message: String
)

class SocialPublisher {
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(180, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    suspend fun publishToTikTok(accessToken: String, videoFile: File, title: String): PublishReport =
        withContext(Dispatchers.IO) {
            if (accessToken.isBlank()) {
                return@withContext PublishReport("TikTok", false, "Токен TikTok не подключен")
            }
            try {
                val initPayload = JSONObject().apply {
                    put("post_info", JSONObject().apply {
                        put("title", title)
                        put("privacy_level", "PUBLIC_TO_EVERYONE")
                    })
                    put("source_info", JSONObject().apply {
                        put("source", "FILE_UPLOAD")
                        put("video_size", videoFile.length())
                        put("chunk_size", videoFile.length())
                        put("total_chunk_count", 1)
                    })
                }

                val initReq = Request.Builder()
                    .url("https://open.tiktokapis.com/v2/post/publish/video/init/")
                    .addHeader("Authorization", "Bearer $accessToken")
                    .addHeader("Content-Type", "application/json; charset=UTF-8")
                    .post(initPayload.toString().toRequestBody("application/json".toMediaType()))
                    .build()

                val uploadUrl = httpClient.newCall(initReq).execute().use { resp ->
                    val body = resp.body?.string() ?: ""
                    if (!resp.isSuccessful) {
                        return@withContext PublishReport("TikTok", false, "Init Error (${resp.code}): $body")
                    }
                    JSONObject(body).getJSONObject("data").getString("upload_url")
                }

                val uploadReq = Request.Builder()
                    .url(uploadUrl)
                    .addHeader("Content-Range", "bytes 0-${videoFile.length() - 1}/${videoFile.length()}")
                    .put(videoFile.asRequestBody("video/mp4".toMediaType()))
                    .build()

                httpClient.newCall(uploadReq).execute().use { uploadResp ->
                    if (uploadResp.isSuccessful) {
                        PublishReport("TikTok", true, "Видео успешно опубликовано в TikTok")
                    } else {
                        PublishReport("TikTok", false, "Upload Error: ${uploadResp.code}")
                    }
                }
            } catch (e: Exception) {
                PublishReport("TikTok", false, "Ошибка сети TikTok: ${e.localizedMessage}")
            }
        }

    suspend fun publishToYouTubeShorts(accessToken: String, videoFile: File, title: String): PublishReport =
        withContext(Dispatchers.IO) {
            if (accessToken.isBlank()) {
                return@withContext PublishReport("YouTube", false, "Токен YouTube не подключен")
            }
            try {
                val metaJson = JSONObject().apply {
                    put("snippet", JSONObject().apply {
                        put("title", if (title.contains("#shorts", true)) title else "$title #shorts")
                        put("description", "Generated automatically via AutoShorts AI #shorts")
                        put("categoryId", "24")
                    })
                    put("status", JSONObject().apply {
                        put("privacyStatus", "public")
                        put("selfDeclaredMadeForKids", false)
                    })
                }

                val initReq = Request.Builder()
                    .url("https://www.googleapis.com/upload/youtube/v3/videos?uploadType=resumable&part=snippet,status")
                    .addHeader("Authorization", "Bearer $accessToken")
                    .addHeader("X-Upload-Content-Length", videoFile.length().toString())
                    .addHeader("X-Upload-Content-Type", "video/mp4")
                    .post(metaJson.toString().toRequestBody("application/json; charset=UTF-8".toMediaType()))
                    .build()

                val uploadLocation = httpClient.newCall(initReq).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        return@withContext PublishReport("YouTube", false, "Init Error (${resp.code})")
                    }
                    resp.header("Location")
                        ?: return@withContext PublishReport("YouTube", false, "Missing Resumable Location header")
                }

                val uploadReq = Request.Builder()
                    .url(uploadLocation)
                    .put(videoFile.asRequestBody("video/mp4".toMediaType()))
                    .build()

                httpClient.newCall(uploadReq).execute().use { putResp ->
                    if (putResp.isSuccessful) {
                        PublishReport("YouTube", true, "Видео успешно загружено в YouTube Shorts")
                    } else {
                        PublishReport("YouTube", false, "Upload Error (${putResp.code})")
                    }
                }
            } catch (e: Exception) {
                PublishReport("YouTube", false, "Ошибка сети YouTube: ${e.localizedMessage}")
            }
        }
}

