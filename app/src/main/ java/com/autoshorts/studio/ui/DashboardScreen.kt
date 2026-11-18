package com.autoshorts.studio.ui

import android.app.Activity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.autoshorts.studio.billing.SubscriptionState
import com.autoshorts.studio.data.CartoonStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    val subState by viewModel.billingManager.subState.collectAsState()
    val topic by viewModel.topic.collectAsState()
    val videosPerDay by viewModel.videosPerDay.collectAsState()
    val geminiKey by viewModel.geminiKey.collectAsState()
    val selectedStyle by viewModel.selectedStyle.collectAsState()
    val tiktokConnected by viewModel.tiktokConnected.collectAsState()
    val youtubeConnected by viewModel.youtubeConnected.collectAsState()
    val statusLog by viewModel.statusLog.collectAsState()

    var styleDropdownExpanded by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AutoShorts AI Studio", fontWeight = FontWeight.Bold) }
            )
        }
    ) { padding ->
        if (subState == SubscriptionState.EXPIRED_OR_NONE) {
            PaywallContent(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                onSubscribeClick = {
                    (context as? Activity)?.let { act ->
                        viewModel.billingManager.launchSubscriptionFlow(act)
                    }
                },
                onDemoBypassClick = {
                    viewModel.prefs.isSubscriptionActiveCached = true
                    viewModel.billingManager.refreshPurchasesAsync()
                }
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Тема канала
                OutlinedTextField(
                    value = topic,
                    onValueChange = viewModel::updateTopic,
                    label = { Text("Тема / Ниша канала") },
                    modifier = Modifier.fillMaxWidth()
                )

                // Выбор стиля мультфильма
                ExposedDropdownMenuBox(
                    expanded = styleDropdownExpanded,
                    onExpandedChange = { styleDropdownExpanded = !styleDropdownExpanded }
                ) {
                    OutlinedTextField(
                        value = selectedStyle.displayName,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Стиль рисовки (Cartoon Style)") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = styleDropdownExpanded) },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = styleDropdownExpanded,
                        onDismissRequest = { styleDropdownExpanded = false }
                    ) {
                        CartoonStyle.entries.forEach { style ->
                            DropdownMenuItem(
                                text = { Text(style.displayName) },
                                onClick = {
                                    viewModel.updateStyle(style)
                                    styleDropdownExpanded = false
                                }
                            )
                        }
                    }
                }

                // Расписание (Количество видео в день -> 24 / N часов)
                OutlinedTextField(
                    value = videosPerDay.toString(),
                    onValueChange = viewModel::updateVideosPerDay,
                    label = { Text("Видео в день (интервал: каждые ${24 / videosPerDay} ч.)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )

                // Gemini API Key
                OutlinedTextField(
                    value = geminiKey,
                    onValueChange = viewModel::updateGeminiKey,
                    label = { Text("Gemini & Veo API Key") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )

                // One-Click Social Auth
                Text("Подключение соцсетей (OAuth 2.0):", style = MaterialTheme.typography.titleMedium)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = { viewModel.startOAuthFlow(context, "tiktok") },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(if (tiktokConnected) "TikTok ✓" else "Вход TikTok")
                    }
                    Button(
                        onClick = { viewModel.startOAuthFlow(context, "youtube") },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(if (youtubeConnected) "YouTube ✓" else "Вход YouTube")
                    }
                }

                HorizontalDivider()

                // Кнопки управления конвейером
                Button(
                    onClick = viewModel::scheduleAutomatedPipeline,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Включить автопилот (24/${videosPerDay} ч.)")
                }

                OutlinedButton(
                    onClick = viewModel::runSingleVideoNow,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Сгенерировать и опубликовать 1 видео сейчас")
                }

                Card(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = statusLog,
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

@Composable
private fun PaywallContent(
    modifier: Modifier = Modifier,
    onSubscribeClick: () -> Unit,
    onDemoBypassClick: () -> Unit
) {
    Column(
        modifier = modifier.padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "AutoShorts AI Pro",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Автоматическая генерация 30-секундных мультфильмов с Frame-Chaining склейкой и автопостингом в TikTok и YouTube Shorts доступна по подписке (включая бесплатный пробный период).",
            style = MaterialTheme.typography.bodyLarge
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = onSubscribeClick,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Оформить подписку / Бесплатный Trial (Google Play)")
        }
        Spacer(modifier = Modifier.height(12.dp))
        TextButton(onClick = onDemoBypassClick) {
            Text("Режим разработчика (тестовый доступ)")
        }
    }
}

