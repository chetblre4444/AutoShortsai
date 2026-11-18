package com.autoshorts.studio.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.autoshorts.studio.data.SecurePrefsManager
import com.autoshorts.studio.billing.BillingManager

class MainViewModel(application: Application) : AndroidViewModel(application) {
    val prefs = SecurePrefsManager(application)
    val billingManager = BillingManager(application)

    init {
        try {
            billingManager.startConnection()
        } catch (e: Exception) {
            // Безопасный перехват при недоступности Play Services
        }
    }

    fun startVideoPipeline() {
        // Логика запуска фоновой генерации через WorkManager
    }

    override fun onCleared() {
        super.onCleared()
        try {
            billingManager.endConnection()
        } catch (e: Exception) {}
    }
}
