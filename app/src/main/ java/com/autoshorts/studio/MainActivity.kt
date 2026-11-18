package com.autoshorts.studio

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import com.autoshorts.studio.ui.DashboardScreen
import com.autoshorts.studio.ui.MainViewModel

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        intent?.data?.let { uri ->
            if (uri.scheme == "autoshorts") {
                viewModel.handleOAuthCallback(uri)
            }
        }
        setContent {
            MaterialTheme {
                DashboardScreen(viewModel = viewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.data?.let { uri ->
            if (uri.scheme == "autoshorts") {
                viewModel.handleOAuthCallback(uri)
            }
        }
    }
}

