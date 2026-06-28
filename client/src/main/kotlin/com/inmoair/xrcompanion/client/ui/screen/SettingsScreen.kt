package com.inmoair.xrcompanion.client.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.inmoair.xrcompanion.client.data.DeviceRepository
import com.inmoair.xrcompanion.client.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClientSettingsScreen(
    deviceRepository: DeviceRepository,
    onBack: () -> Unit,
) {
    val touchSensitivity by deviceRepository.touchSensitivity.collectAsState(1f)
    val scrollSpeed      by deviceRepository.scrollSpeed.collectAsState(1f)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceDark),
            )
        },
        containerColor = DarkBackground,
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 16.dp),
        ) {
            item {
                SettingsCard("Touchpad") {
                    SettingsSlider("Touch Sensitivity", touchSensitivity, 0.2f, 3f) {
                        // deviceRepository.setTouchSensitivity(it)
                    }
                    SettingsSlider("Scroll Speed", scrollSpeed, 0.2f, 3f) {
                        // deviceRepository.setScrollSpeed(it)
                    }
                }
            }
            item {
                SettingsCard("Connection") {
                    SettingsToggle("Auto Reconnect", true) {}
                    SettingsToggle("Background Scanning", false) {}
                }
            }
            item {
                SettingsCard("Voice Input") {
                    SettingsToggle("Dictation Mode", true) {}
                    SettingsToggle("Command Mode", false) {}
                }
            }
        }
    }
}

@Composable
private fun SettingsCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardDark),
        elevation = CardDefaults.cardElevation(0.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun SettingsSlider(label: String, value: Float, min: Float, max: Float, onChange: (Float) -> Unit) {
    Column {
        Row {
            Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text("${(value * 10).toInt() / 10f}x", style = MaterialTheme.typography.labelLarge, color = AccentBlue)
        }
        Slider(
            value = value, onValueChange = onChange, valueRange = min..max,
            colors = SliderDefaults.colors(thumbColor = AccentBlue, activeTrackColor = AccentBlue, inactiveTrackColor = DividerColor),
        )
    }
}

@Composable
private fun SettingsToggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(
            checked = checked, onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = AccentBlue),
        )
    }
}
