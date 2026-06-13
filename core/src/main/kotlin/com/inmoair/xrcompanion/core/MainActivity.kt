package com.inmoair.xrcompanion.core

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.core.content.ContextCompat
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.inmoair.xrcompanion.core.service.CoreForegroundService
import com.inmoair.xrcompanion.core.ui.screen.CoreSettingsScreen
import com.inmoair.xrcompanion.core.ui.screen.DashboardScreen
import com.inmoair.xrcompanion.core.ui.theme.DarkBackground
import com.inmoair.xrcompanion.core.ui.theme.XRCoreTheme
import com.inmoair.xrcompanion.core.ui.viewmodel.CoreViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: CoreViewModel by viewModels()

    private val pairingReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val deviceId   = intent.getStringExtra("deviceId")   ?: return
            val deviceName = intent.getStringExtra("deviceName") ?: return
            viewModel.onPairingRequest(deviceId, deviceName)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Start server on first launch
        CoreForegroundService.start(this)
        viewModel.setServerRunning(true)

        // ContextCompat.registerReceiver handles the RECEIVER_NOT_EXPORTED flag for all API levels.
        val pairingFilter = IntentFilter("com.inmoair.xrcompanion.PAIRING_REQUEST")
        ContextCompat.registerReceiver(
            this, pairingReceiver, pairingFilter, ContextCompat.RECEIVER_NOT_EXPORTED
        )

        setContent {
            XRCoreTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = DarkBackground) {
                    val navController = rememberNavController()
                    val uiState by viewModel.uiState.collectAsState()

                    NavHost(navController, startDestination = "dashboard") {
                        composable("dashboard") {
                            DashboardScreen(
                                uiState     = uiState,
                                viewModel   = viewModel,
                                onStartServer = {
                                    CoreForegroundService.start(this@MainActivity)
                                    viewModel.setServerRunning(true)
                                },
                                onStopServer  = {
                                    CoreForegroundService.stop(this@MainActivity)
                                    viewModel.setServerRunning(false)
                                    viewModel.setConnectedClient(null)
                                },
                            )
                        }
                        composable("settings") {
                            CoreSettingsScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshPermissions()
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(pairingReceiver)
    }
}
