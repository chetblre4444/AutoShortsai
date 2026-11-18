package com.autoshorts.studio

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.autoshorts.studio.data.CartoonStyle
import com.autoshorts.studio.ui.MainViewModel

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen(viewModel = viewModel)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: MainViewModel) {
    var apiKey by remember { mutableStateOf(viewModel.prefs.geminiApiKey) }
    var topic by remember { mutableStateOf(viewModel.prefs.channelTopic) }
    var selectedStyle by remember { mutableStateOf(viewModel.prefs.selectedStyle) }
    var expanded by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AutoShorts AI Studio") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedTextField(
                value = apiKey,
                onValueChange = { 
                    apiKey = it
                    viewModel.prefs.geminiApiKey = it
                },
                label = { Text("Gemini API Key") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = topic,
                onValueChange = { 
                    topic = it
                    viewModel.prefs.channelTopic = it
                },
                label = { Text("Тема канала / ролика") },
                modifier = Modifier.fillMaxWidth()
            )

            Text("Стиль анимации:", style = MaterialTheme.typography.titleMedium)
            
            Box {
                OutlinedButton(
                    onClick = { expanded = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(selectedStyle.displayName)
                }
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    CartoonStyle.entries.forEach { style ->
                        DropdownMenuItem(
                            text = { Text(style.displayName) },
                            onClick = {
                                selectedStyle = style
                                viewModel.prefs.selectedStyle = style
                                expanded = false
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = {
                    viewModel.startVideoPipeline()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Сгенерировать видео сейчас")
            }
        }
    }
}
