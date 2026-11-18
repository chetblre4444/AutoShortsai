package com.autoshorts.studio.core

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.autoshorts.studio.billing.BillingManager
import com.autoshorts.studio.data.SecurePrefsManager

class VideoPipelineWorker(
    private val appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val prefs = SecurePrefsManager(appContext)
        val billingManager = BillingManager(appContext, prefs)

        // Шаг 4.1: Проверка активной подписки перед запуском генерации
        val hasSubscription = billingManager.verifyActiveSubscriptionBlocking()
        if (!hasSubscription) {
            sendNotification(
                "Генерация приостановлена",
                "Подписка или пробный период истекли. Продлите доступ в приложении."
            )
            return Result.failure()
        }

        val apiKey = prefs.geminiApiKey
        if (apiKey.isBlank()) {
            sendNotification("Ошибка конфигурации", "Не указан ключ Gemini API.")
            return Result.failure()
        }

        return try {
            sendNotification("AutoShorts AI", "Шаг 1/3: Генерация сценария (3x10 сек)...")
            val scriptEngine = ScriptEngine()
            val script = scriptEngine.generateThreePartScript(
                apiKey = apiKey,
                topic = prefs.channelTopic,
                style = prefs.selectedStyle
            )

            sendNotification("AutoShorts AI", "Шаг 2/3: Frame-Chaining рендер 30-сек видео...")
            val videoEngine = VideoChainEngine(appContext)
            val finalVideoFile = videoEngine.generateChained30sVideo(apiKey, script)

            sendNotification("AutoShorts AI", "Шаг 3/3: Публикация в TikTok и YouTube Shorts...")
            val publisher = SocialPublisher()
            val reports = mutableListOf<PublishReport>()

            if (prefs.tiktokAccessToken.isNotBlank()) {
                reports.add(publisher.publishToTikTok(prefs.tiktokAccessToken, finalVideoFile, script.title))
            }
            if (prefs.youtubeAccessToken.isNotBlank()) {
                reports.add(publisher.publishToYouTubeShorts(prefs.youtubeAccessToken, finalVideoFile, script.title))
            }

            if (finalVideoFile.exists()) {
                finalVideoFile.delete()
            }

            val summary = if (reports.isEmpty()) {
                "Видео создано, но соцсети не подключены."
            } else {
                reports.joinToString("\n") { "${it.platform}: ${it.message}" }
            }
            sendNotification("Публикация завершена", summary)
            Result.success()
        } catch (e: Exception) {
            sendNotification("Сбой пайплайна", e.localizedMessage ?: "Неизвестная ошибка")
            Result.retry()
        }
    }

    private fun sendNotification(title: String, message: String) {
        val channelId = "autoshorts_pipeline_channel"
        val manager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "AutoShorts Фоновый Конвейер",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            manager.createNotificationChannel(channel)
        }
        val notification = NotificationCompat.Builder(appContext, channelId)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setAutoCancel(true)
            .build()

        manager.notify(System.currentTimeMillis().toInt(), notification)
    }
}

