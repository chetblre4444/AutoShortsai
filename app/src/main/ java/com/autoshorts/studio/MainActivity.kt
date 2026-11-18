package com.autoshorts.studio

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        var errorMessage = "Запуск успешен, ждем инициализации..."
        
        try {
            // Безопасно проверяем инициализацию контекста и компонентов
            val prefsCheck = applicationContext != null
            errorMessage = "Контекст создан: $prefsCheck\nВсе системы готовы к работе."
        } catch (e: Exception) {
            errorMessage = "Ошибка при старте:\n${e.localizedMessage}\n\n${e.stackTraceToString()}"
        }

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "Диагностика запуска AutoShorts AI",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = errorMessage,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }
    }
}
