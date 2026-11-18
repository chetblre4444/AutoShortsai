package com.autoshorts.studio.core

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.util.concurrent.TimeUnit

class VideoChainEngine(private val context: Context) {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .build()

    suspend fun generateChained30sVideo(
        apiKey: String,
        script: ThreePartScript
    ): File = withContext(Dispatchers.IO) {
        val workDir = context.cacheDir
        val seg1File = File(workDir, "segment_1.mp4")
        val seg2File = File(workDir, "segment_2.mp4")
        val seg3File = File(workDir, "segment_3.mp4")

        val frame1File = File(workDir, "temp_last_frame_1.jpg")
        val frame2File = File(workDir, "temp_last_frame_2.jpg")
        val finalOutputFile = File(workDir, "final_autoshorts_${System.currentTimeMillis()}.mp4")

        try {
            // Сегмент 1 (0-10 сек): Генерация по тексту + извлечение последнего кадра
            requestTenSecondSegment(
                apiKey = apiKey,
                prompt = script.part1Prompt,
                referenceFrame = null,
                outputFile = seg1File
            )
            extractLastFrameToJpeg(seg1File, frame1File)

            // Сегмент 2 (10-20 сек): Генерация по тексту + temp_last_frame_1.jpg
            requestTenSecondSegment(
                apiKey = apiKey,
                prompt = script.part2Prompt,
                referenceFrame = frame1File,
                outputFile = seg2File
            )
            extractLastFrameToJpeg(seg2File, frame2File)

            // Сегмент 3 (20-30 сек): Генерация по тексту + temp_last_frame_2.jpg
            requestTenSecondSegment(
                apiKey = apiKey,
                prompt = script.part3Prompt,
                referenceFrame = frame2File,
                outputFile = seg3File
            )

            // Склейка трех 10-секундных сегментов в один 30-секундный MP4
            stitchMp4Segments(listOf(seg1File, seg2File, seg3File), finalOutputFile)

            return@withContext finalOutputFile
        } finally {
            // Обязательная очистка временных файлов и кадров
            listOf(seg1File, seg2File, seg3File, frame1File, frame2File).forEach { file ->
                if (file.exists()) file.delete()
            }
        }
    }

    private suspend fun requestTenSecondSegment(
        apiKey: String,
        prompt: String,
        referenceFrame: File?,
        outputFile: File
    ) {
        val instanceObj = JSONObject().apply {
            put("prompt", prompt)
            if (referenceFrame != null && referenceFrame.exists()) {
                val base64Frame = Base64.encodeToString(referenceFrame.readBytes(), Base64.NO_WRAP)
                put("image", JSONObject().apply {
                    put("bytesBase64Encoded", base64Frame)
                    put("mimeType", "image/jpeg")
                })
            }
        }

        val payload = JSONObject().apply {
            put("instances", JSONArray().put(instanceObj))
            put("parameters", JSONObject().apply {
                put("aspectRatio", "9:16")
                put("durationSeconds", 10)
            })
        }

        val startUrl = "https://generativelanguage.googleapis.com/v1beta/models/veo-2.0-generate-001:predictLongRunning?key=$apiKey"
        val startReq = Request.Builder()
            .url(startUrl)
            .post(payload.toString().toRequestBody("application/json".toMediaType()))
            .build()

        val operationName = httpClient.newCall(startReq).execute().use { resp ->
            val body = resp.body?.string() ?: throw IOException("Empty Veo response")
            if (!resp.isSuccessful) throw IOException("Veo start error (${resp.code}): $body")
            JSONObject(body).getString("name")
        }

        // Опрос статуса долгой операции генерации видео
        val pollUrl = "https://generativelanguage.googleapis.com/v1beta/$operationName?key=$apiKey"
        repeat(40) {
            delay(6000)
            val pollReq = Request.Builder().url(pollUrl).get().build()
            httpClient.newCall(pollReq).execute().use { pollResp ->
                val pollBody = pollResp.body?.string() ?: throw IOException("Empty poll response")
                val json = JSONObject(pollBody)
                if (json.optBoolean("done", false)) {
                    val videoUri = json.getJSONObject("response")
                        .getJSONObject("generateVideoResponse")
                        .getJSONArray("generatedSamples")
                        .getJSONObject(0)
                        .getJSONObject("video")
                        .getString("uri")

                    downloadVideoFile("$videoUri&key=$apiKey", outputFile)
                    return
                }
            }
        }
        throw IOException("Таймаут ожидания рендера 10-секундного сегмента")
    }

    private fun downloadVideoFile(url: String, destination: File) {
        val req = Request.Builder().url(url).get().build()
        httpClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("Не удалось скачать сегмент: ${resp.code}")
            val stream = resp.body?.byteStream() ?: throw IOException("Empty video stream")
            FileOutputStream(destination).use { out ->
                stream.copyTo(out)
            }
        }
    }

    private fun extractLastFrameToJpeg(videoFile: File, outputJpeg: File) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(videoFile.absolutePath)
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 10000L
            // Извлекаем кадр за 100 мс до конца ролика в микросекундах
            val targetTimeUs = ((durationMs - 100).coerceAtLeast(0)) * 1000L
            val bitmap = retriever.getFrameAtTime(targetTimeUs, MediaMetadataRetriever.OPTION_CLOSEST)
                ?: throw IOException("Не удалось извлечь последний кадр из ${videoFile.name}")

            FileOutputStream(outputJpeg).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
            }
            bitmap.recycle()
        } finally {
            retriever.release()
        }
    }

    private fun stitchMp4Segments(segments: List<File>, outputFile: File) {
        val muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var muxerVideoTrackIndex = -1
        var muxerAudioTrackIndex = -1
        var muxerStarted = false

        var videoPtsOffsetUs = 0L
        var audioPtsOffsetUs = 0L
        val buffer = ByteBuffer.allocate(1024 * 1024)
        val bufferInfo = MediaCodec.BufferInfo()

        try {
            for (segment in segments) {
                val extractor = MediaExtractor()
                extractor.setDataSource(segment.absolutePath)

                var segVideoTrack = -1
                var segAudioTrack = -1

                for (i in 0 until extractor.trackCount) {
                    val format = extractor.getTrackFormat(i)
                    val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                    if (mime.startsWith("video/") && segVideoTrack == -1) {
                        segVideoTrack = i
                        if (!muxerStarted && muxerVideoTrackIndex == -1) {
                            muxerVideoTrackIndex = muxer.addTrack(format)
                        }
                    } else if (mime.startsWith("audio/") && segAudioTrack == -1) {
                        segAudioTrack = i
                        if (!muxerStarted && muxerAudioTrackIndex == -1) {
                            muxerAudioTrackIndex = muxer.addTrack(format)
                        }
                    }
                }

                if (!muxerStarted) {
                    muxer.start()
                    muxerStarted = true
                }

                var maxVideoPtsInSegment = 0L
                if (segVideoTrack != -1) {
                    extractor.selectTrack(segVideoTrack)
                    while (true) {
                        val sampleSize = extractor.readSampleData(buffer, 0)
                        if (sampleSize < 0) break
                        val sampleTime = extractor.sampleTime
                        bufferInfo.offset = 0
                        bufferInfo.size = sampleSize
                        bufferInfo.presentationTimeUs = videoPtsOffsetUs + sampleTime
                        bufferInfo.flags = extractor.sampleFlags
                        if (bufferInfo.presentationTimeUs > maxVideoPtsInSegment) {
                            maxVideoPtsInSegment = bufferInfo.presentationTimeUs
                        }
                        muxer.writeSampleData(muxerVideoTrackIndex, buffer, bufferInfo)
                        extractor.advance()
                    }
                    extractor.unselectTrack(segVideoTrack)
                }

                var maxAudioPtsInSegment = 0L
                if (segAudioTrack != -1 && muxerAudioTrackIndex != -1) {
                    extractor.selectTrack(segAudioTrack)
                    while (true) {
                        val sampleSize = extractor.readSampleData(buffer, 0)
                        if (sampleSize < 0) break
                        val sampleTime = extractor.sampleTime
                        bufferInfo.offset = 0
                        bufferInfo.size = sampleSize
                        bufferInfo.presentationTimeUs = audioPtsOffsetUs + sampleTime
                        bufferInfo.flags = extractor.sampleFlags
                        if (bufferInfo.presentationTimeUs > maxAudioPtsInSegment) {
                            maxAudioPtsInSegment = bufferInfo.presentationTimeUs
                        }
                        muxer.writeSampleData(muxerAudioTrackIndex, buffer, bufferInfo)
                        extractor.advance()
                    }
                    extractor.unselectTrack(segAudioTrack)
                }

                videoPtsOffsetUs = maxVideoPtsInSegment + 33_333L
                audioPtsOffsetUs = maxAudioPtsInSegment + 23_219L
                extractor.release()
            }
        } finally {
            if (muxerStarted) {
                muxer.stop()
            }
            muxer.release()
        }
    }
}

