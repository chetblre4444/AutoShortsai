package com.autoshorts.studio.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.work.*
import com.autoshorts.studio.billing.BillingManager
import com.autoshorts.studio.core.VideoPipelineWorker
import com.autoshorts.studio.data.CartoonStyle
import com.autoshorts.studio.data.SecurePrefsManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.TimeUnit

class MainViewModel(application: Application) : AndroidViewModel(application) {
    val prefs = SecurePrefsManager(application)
    val billingManager = BillingManager(application, prefs)

    private val _topic = MutableStateFlow(prefs.channelTopic)
    val topic: StateFlow<String> = _topic

    private val _videosPerDay = MutableStateFlow(prefs.videosPerDay)
    val videosPerDay: StateFlow<Int> = _videosPerDay

    private val _geminiKey = MutableStateFlow(prefs.geminiApiKey)
    val geminiKey: StateFlow<String> = _geminiKey

    private val _selectedStyle = MutableStateFlow(prefs.selectedStyle)
    val selectedStyle: StateFlow<CartoonStyle> = _selectedStyle

    private val _tiktokConnected = MutableStateFlow(prefs.tiktokAccessToken.isNotBlank())
    val tiktokConnected: StateFlow<Boolean> = _tiktokConnected

    private val _youtubeConnected = MutableStateFlow(prefs.youtubeAccessToken.isNotBlank())
    val youtubeConnected: StateFlow<Boolean> = _youtubeConnected

    private val _statusLog = MutableStateFlow("Готов к запуску. Интервал: ${24 / prefs.videosPerDay} ч.")
    val statusLog: StateFlow<String> = _statusLog

    init {
        billingManager.startConnection()
    }

    fun updateTopic(newTopic: String) {
        _topic.value = newTopic
        prefs.channelTopic = newTopic
    }

    fun updateVideosPerDay(countStr: String) {
        val count = countStr.toIntOrNull()?.coerceIn(1, 24) ?: 1
        _videosPerDay.value = count
        prefs.videosPerDay = count
        _statusLog.value = "Расписание обновлено: $count видео/день (каждые ${24 / count} ч.)"
    }

    fun updateGeminiKey(key: String) {
        _geminiKey.value = key
        prefs.geminiApiKey = key
    }

    fun updateStyle(style: CartoonStyle) {
        _selectedStyle.value = style
        prefs.selectedStyle = style
    }

    fun startOAuthFlow(context: Context, platform: String) {
        val authUrl = if (platform == "tiktok") {
            "https://www.tiktok.com/v2/auth/authorize/?client_key=YOUR_TIKTOK_CLIENT_KEY&scope=user.info.basic,video.publish&response_type=token&redirect_uri=autoshorts://oauth2callback"
        } else {
            "https://accounts.google.com/o/oauth2/v2/auth?client_id=YOUR_GOOGLE_CLIENT_ID&redirect_uri=autoshorts://oauth2callback&response_type=token&scope=https://www.googleapis.com/auth/youtube.upload"
        }
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(authUrl)))
    }

    fun handleOAuthCallback(uri: Uri) {
        val token = uri.getQueryParameter("access_token") ?: uri.fragment?.substringAfter("access_token=")?.substringBefore("&")
        val platform = uri.getQueryParameter("state") ?: "youtube"
        if (!token.isNullOrBlank()) {
            if (platform.contains("tiktok", true)) {
                prefs.tiktokAccessToken = token
                _tiktokConnected.value = true
            } else {
                prefs.youtubeAccessToken = token
                _youtubeConnected.value = true
            }
            _statusLog.value = "OAuth токен для $platform успешно сохранен!"
        }
    }

    fun scheduleAutomatedPipeline() {
        val intervalHours = (24 / _videosPerDay.value).coerceAtLeast(1).toLong()
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val periodicWork = PeriodicWorkRequestBuilder<VideoPipelineWorker>(
            intervalHours, TimeUnit.HOURS
        )
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(getApplication()).enqueueUniquePeriodicWork(
            "AutoShortsPeriodicPipeline",
            ExistingPeriodicWorkPolicy.UPDATE,
            periodicWork
        )
        _statusLog.value = "Автопилот активирован: генерация каждые $intervalHours ч."
    }

    fun runSingleVideoNow() {
        val oneTimeWork = OneTimeWorkRequestBuilder<VideoPipelineWorker>().build()
        WorkManager.getInstance(getApplication()).enqueue(oneTimeWork)
        _statusLog.value = "Запущена мгновенная генерация 30-сек ролика в фоне..."
    }
}

