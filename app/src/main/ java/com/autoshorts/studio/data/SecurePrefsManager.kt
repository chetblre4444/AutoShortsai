package com.autoshorts.studio.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

enum class CartoonStyle(val displayName: String, val promptModifier: String) {
    FLAT_VECTOR("2D Flat Vector", "in 2D flat vector cartoon style, bold outlines, vibrant colors, Kurzgesagt aesthetic"),
    RETRO_90S("Retro 90s Cartoon", "in classic 1990s Saturday morning cartoon style, cel-shaded, VHS grain, hand-drawn animation look"),
    CHIBI_ANIME("Chibi Anime", "in cute Chibi anime style, expressive oversized eyes, dynamic pastel cel-shading, high quality anime production")
}

class SecurePrefsManager(context: Context) {
    private val prefs: SharedPreferences = try {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        EncryptedSharedPreferences.create(
            context,
            "autoshorts_secure_store",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        // Резервный вариант: если аппаратный Keystore недоступен, используем обычные SharedPreferences без краша
        context.getSharedPreferences("autoshorts_fallback_store", Context.MODE_PRIVATE)
    }

    var geminiApiKey: String
        get() = prefs.getString("gemini_api_key", "") ?: ""
        set(value) = prefs.edit().putString("gemini_api_key", value.trim()).apply()

    var channelTopic: String
        get() = prefs.getString("channel_topic", "Интересные факты о космосе") ?: ""
        set(value) = prefs.edit().putString("channel_topic", value).apply()

    var videosPerDay: Int
        get() = prefs.getInt("videos_per_day", 3).coerceIn(1, 24)
        set(value) = prefs.edit().putInt("videos_per_day", value.coerceIn(1, 24)).apply()

    var selectedStyle: CartoonStyle
        get() {
            val name = prefs.getString("cartoon_style", CartoonStyle.FLAT_VECTOR.name)
            return CartoonStyle.entries.find { it.name == name } ?: CartoonStyle.FLAT_VECTOR
        }
        set(value) = prefs.edit().putString("cartoon_style", value.name).apply()

    var tiktokAccessToken: String
        get() = prefs.getString("tiktok_token", "") ?: ""
        set(value) = prefs.edit().putString("tiktok_token", value).apply()

    var youtubeAccessToken: String
        get() = prefs.getString("youtube_token", "") ?: ""
        set(value) = prefs.edit().putString("youtube_token", value).apply()

    var isSubscriptionActiveCached: Boolean
        get() = prefs.getBoolean("sub_active_cached", false)
        set(value) = prefs.edit().putBoolean("sub_active_cached", value).apply()
}
