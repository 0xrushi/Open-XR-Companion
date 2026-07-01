package com.inmoair.xrcompanion.client

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.inmoair.xrcompanion.client.data.DeviceRepository
import com.inmoair.xrcompanion.client.ui.navigation.AppNavigation
import com.inmoair.xrcompanion.client.ui.theme.ClientThemeChoice
import com.inmoair.xrcompanion.client.ui.theme.DarkBackground
import com.inmoair.xrcompanion.client.ui.theme.XRClientTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var deviceRepository: DeviceRepository

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* handle if needed */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestPermissionsIfNeeded()

        setContent {
            val themeKey by deviceRepository.themeKey.collectAsState(initial = ClientThemeChoice.DARK.key)
            XRClientTheme(themeChoice = ClientThemeChoice.fromKey(themeKey)) {
                Surface(modifier = Modifier.fillMaxSize(), color = DarkBackground) {
                    val navController = rememberNavController()
                    AppNavigation(
                        navController    = navController,
                        deviceRepository = deviceRepository,
                    )
                }
            }
        }
    }

    private fun requestPermissionsIfNeeded() {
        val toRequest = mutableListOf<String>()
        val perms = mutableListOf(
            Manifest.permission.RECORD_AUDIO,
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms += Manifest.permission.POST_NOTIFICATIONS
        }
        perms.forEach { perm ->
            if (checkSelfPermission(perm) != PackageManager.PERMISSION_GRANTED) {
                toRequest.add(perm)
            }
        }
        if (toRequest.isNotEmpty()) {
            permissionLauncher.launch(toRequest.toTypedArray())
        }
    }
}
