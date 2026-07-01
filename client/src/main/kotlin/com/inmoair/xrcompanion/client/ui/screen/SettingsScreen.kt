package com.inmoair.xrcompanion.client.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.inmoair.xrcompanion.client.data.DeviceRepository
import com.inmoair.xrcompanion.client.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClientSettingsScreen(
    deviceRepository: DeviceRepository,
    onBack: () -> Unit,
) {
    val touchSensitivity by deviceRepository.touchSensitivity.collectAsState(1f)
    val scrollSpeed      by deviceRepository.scrollSpeed.collectAsState(1f)
    val themeKey by deviceRepository.themeKey.collectAsState(ClientThemeChoice.DARK.key)
    val showSpaceWalkerSettings by deviceRepository.showSpaceWalkerSettings.collectAsState(true)
    val selectedTheme = ClientThemeChoice.fromKey(themeKey)
    val scope = rememberCoroutineScope()

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
                SettingsCard("Appearance") {
                    ClientThemeChoice.entries.forEach { choice ->
                        ThemeOptionRow(
                            choice = choice,
                            selected = choice == selectedTheme,
                            onClick = {
                                scope.launch { deviceRepository.setThemeKey(choice.key) }
                            },
                        )
                    }
                    SettingsToggle(
                        "Show SpaceWalker settings",
                        showSpaceWalkerSettings,
                    ) { show ->
                        scope.launch { deviceRepository.setShowSpaceWalkerSettings(show) }
                    }
                }
            }
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
private fun ThemeOptionRow(
    choice: ClientThemeChoice,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = colorsFor(choice)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .background(if (selected) AccentBlue.copy(alpha = 0.14f) else androidx.compose.ui.graphics.Color.Transparent)
            .border(
                1.dp,
                if (selected) AccentBlue else DividerColor,
                RoundedCornerShape(12.dp),
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy((-4).dp)) {
            ThemeSwatch(colors.darkBackground)
            ThemeSwatch(colors.cardDark)
            ThemeSwatch(colors.accentBlue)
        }
        Spacer(Modifier.width(14.dp))
        Text(
            choice.displayName,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        RadioButton(
            selected = selected,
            onClick = onClick,
            colors = RadioButtonDefaults.colors(selectedColor = AccentBlue),
        )
    }
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun ThemeSwatch(color: androidx.compose.ui.graphics.Color) {
    Box(
        Modifier
            .size(24.dp)
            .clip(CircleShape)
            .background(color)
            .border(1.dp, TextSecondary.copy(alpha = 0.32f), CircleShape),
    )
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
