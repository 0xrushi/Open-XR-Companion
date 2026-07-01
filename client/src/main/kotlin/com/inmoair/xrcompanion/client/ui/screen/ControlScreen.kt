package com.inmoair.xrcompanion.client.ui.screen

import android.app.Activity
import android.graphics.Bitmap
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size as GeomSize
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.inmoair.xrcompanion.client.screenshot.LocalScreenshotCapturer
import com.inmoair.xrcompanion.client.ui.component.HorizontalScrollStrip
import com.inmoair.xrcompanion.client.ui.component.TouchpadMode
import com.inmoair.xrcompanion.client.ui.component.TouchpadSurface
import com.inmoair.xrcompanion.client.ui.component.VerticalScrollStrip
import com.inmoair.xrcompanion.client.ui.theme.*
import com.inmoair.xrcompanion.client.data.CustomButton
import com.inmoair.xrcompanion.client.ui.viewmodel.ControlTab
import com.inmoair.xrcompanion.client.ui.viewmodel.ControlUiState
import com.inmoair.xrcompanion.client.ui.viewmodel.ControlViewModel
import com.inmoair.xrcompanion.client.ui.viewmodel.ScreenshotSource
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ControlScreen(
    uiState: ControlUiState,
    viewModel: ControlViewModel,
    onBack: () -> Unit,
) {
    var isKeyboardActive by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusRequester     = remember { FocusRequester() }
    val localScreenshotLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val data = result.data
        if (result.resultCode != Activity.RESULT_OK || data == null) {
            viewModel.onLocalScreenshotFailed("Phone screenshot permission was cancelled")
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            try {
                val bitmap = LocalScreenshotCapturer.capture(context, result.resultCode, data)
                viewModel.onLocalScreenshotCaptured(bitmap)
            } catch (e: Exception) {
                viewModel.onLocalScreenshotFailed(e.message ?: "Phone screenshot failed")
            }
        }
    }

    // Sentinel: we always keep this string in the hidden field with the cursor at the end.
    // Because the field is never empty, backspace ALWAYS shortens it — even if the glasses
    // text box already had text that was typed directly on the device. After every event
    // we reset back to the sentinel so the buffer never drains or grows unboundedly.
    val sentinel = "   " // 3 spaces — invisible in the 1dp alpha=0 field
    fun sentinelValue() = TextFieldValue(text = sentinel, selection = TextRange(sentinel.length))

    var keyboardText by remember { mutableStateOf(sentinelValue()) }

    // Buffered mode: when true a visible text field accumulates text locally; pressing Send
    // sends the whole string to Core at once. When false (default) every keystroke is
    // forwarded immediately via the invisible sentinel field.
    var bufferedMode by remember { mutableStateOf(false) }
    var bufferedText by remember { mutableStateOf("") }
    val bufferedFocusRequester = remember { FocusRequester() }

    fun keepKeyboardOpen() {
        keyboardController?.show()
    }

    fun sendBufferedText() {
        if (bufferedText.isNotEmpty()) {
            viewModel.sendText(bufferedText)
            bufferedText = ""
        }
    }

    fun sendEnterFromKeyboard() {
        Log.i("ControlKeyboard", "Phone keyboard IME action -> XR enter")
        if (bufferedMode) sendBufferedText()
        keyboardText = sentinelValue()
        viewModel.sendEnter()
        keepKeyboardOpen()
    }

    val enterKeyboardActions = KeyboardActions(
        onDone = { sendEnterFromKeyboard() },
        onGo = { sendEnterFromKeyboard() },
        onSearch = { sendEnterFromKeyboard() },
        onSend = { sendEnterFromKeyboard() },
        onNext = { sendEnterFromKeyboard() },
    )

    // Show/hide the system keyboard and focus the right field whenever either flag changes.
    LaunchedEffect(isKeyboardActive, bufferedMode) {
        when {
            !isKeyboardActive -> {
                keyboardController?.hide()
                bufferedText = ""
            }
            bufferedMode -> {
                kotlinx.coroutines.delay(50) // let the field enter composition before requesting focus
                runCatching { bufferedFocusRequester.requestFocus() }
                keyboardController?.show()
            }
            else -> {
                keyboardText = sentinelValue()
                focusRequester.requestFocus()
                keyboardController?.show()
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
            ControlHeader(onBack = onBack)
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                PowerButton(
                    icon = Icons.Default.Bedtime,
                    label = "Sleep",
                    onClick = { viewModel.pressSleep() },
                    modifier = Modifier.weight(1f),
                )
                PowerButton(
                    icon = Icons.Default.PowerSettingsNew,
                    label = "Shutdown",
                    onClick = { viewModel.pressShutdown() },
                    modifier = Modifier.weight(1f),
                )
            }
            ControlSegmentedTabs(
                activeTab = uiState.activeTab,
                onTouchpad = { viewModel.setTab(ControlTab.TOUCHPAD) },
                onRemote = { viewModel.setTab(ControlTab.REMOTE) },
            )

            // Main content
            when (uiState.activeTab) {
                ControlTab.TOUCHPAD  -> TouchpadTab(
                    uiState          = uiState,
                    viewModel        = viewModel,
                    onToggleKeyboard = { isKeyboardActive = !isKeyboardActive },
                )
                ControlTab.REMOTE    -> RemoteTab(
                    viewModel        = viewModel,
                    onToggleKeyboard = { isKeyboardActive = !isKeyboardActive },
                )
            }

            // ── Keyboard input row (only while the keyboard is active) ────────
            AnimatedVisibility(visible = isKeyboardActive) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(CardDark, RoundedCornerShape(10.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // Mode toggle
                    Text(
                        "Buffer",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextSecondary,
                    )
                    Switch(
                        checked = bufferedMode,
                        onCheckedChange = { on ->
                            bufferedMode = on
                            if (!on) bufferedText = ""
                        },
                    )

                    if (bufferedMode) {
                        // Visible accumulation field
                        OutlinedTextField(
                            value = bufferedText,
                            onValueChange = { value ->
                                if ('\n' in value) {
                                    bufferedText = value.replace("\n", "")
                                    sendEnterFromKeyboard()
                                } else {
                                    bufferedText = value
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .focusRequester(bufferedFocusRequester)
                                .onPreviewKeyEvent { event ->
                                    if (event.type == KeyEventType.KeyUp && event.key == Key.Enter) {
                                        sendEnterFromKeyboard()
                                        true
                                    } else {
                                        false
                                    }
                                },
                            placeholder = {
                                Text("Type here…", style = MaterialTheme.typography.bodySmall)
                            },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyMedium,
                            keyboardOptions = KeyboardOptions.Default.copy(
                                imeAction = ImeAction.Done,
                            ),
                            keyboardActions = enterKeyboardActions,
                        )
                        Button(
                            onClick = { sendBufferedText() },
                            enabled = bufferedText.isNotEmpty(),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                        ) {
                            Text("Send")
                        }
                    } else {
                        Text(
                            "Streaming",
                            style = MaterialTheme.typography.labelMedium,
                            color = AccentBlue,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            // ── Invisible sentinel field (streaming mode only) ─────────────────
            // 1 dp, fully transparent. Tapping the Keyboard button requests focus
            // here which shows the system keyboard. Every keystroke is forwarded
            // to the glasses immediately. The sentinel string keeps the field
            // non-empty so backspace always fires even when the glasses text box
            // already had pre-existing text.
            if (!bufferedMode) {
                BasicTextField(
                    value = keyboardText,
                    onValueChange = { new ->
                        val oldLen = keyboardText.text.length
                        val newLen = new.text.length
                        when {
                            newLen < oldLen -> {
                                // Backspace (or multi-delete): fire once per deleted character
                                repeat(oldLen - newLen) { viewModel.sendBackspace() }
                            }
                            newLen > oldLen -> {
                                val added = new.text.drop(oldLen)
                                if (added.isNotEmpty()) {
                                    val text = added.replace("\n", "")
                                    if (text.isNotEmpty()) viewModel.sendText(text)
                                    if ('\n' in added) {
                                        viewModel.sendEnter()
                                        keepKeyboardOpen()
                                    }
                                }
                            }
                        }
                        // Keep the local hidden field stable for the whole keyboard session.
                        // Resetting it after every key makes Gboard leave the 123/symbol layer.
                        keyboardText = new.copy(
                            text = new.text.replace("\n", ""),
                            selection = TextRange(new.text.replace("\n", "").length),
                        )
                    },
                    modifier = Modifier
                        .size(1.dp)
                        .alpha(0f)
                        .focusRequester(focusRequester)
                        .onPreviewKeyEvent { event ->
                            if (event.type == KeyEventType.KeyUp && event.key == Key.Enter) {
                                sendEnterFromKeyboard()
                                true
                            } else {
                                false
                            }
                        },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions.Default.copy(
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = enterKeyboardActions,
                )
            }
    }

    // ── Screenshot viewer ─────────────────────────────────────────────────────
    uiState.screenshotBitmap?.let { bmp ->
        val isLocalPhone = uiState.screenshotSource == ScreenshotSource.LOCAL_PHONE
        ScreenshotDialog(
            bitmap    = bmp,
            title     = if (isLocalPhone) "Crop phone screenshot" else "Crop glasses screenshot",
            saveLabel = if (isLocalPhone) "Crop & Send" else "Crop & Save",
            onSave    = { l, t, r, b ->
                if (isLocalPhone) viewModel.sendScreenshotToDevice(l, t, r, b)
                else viewModel.saveScreenshotToGallery(l, t, r, b)
                viewModel.dismissScreenshot()
            },
            onDiscard = { viewModel.dismissScreenshot() },
        )
    }
    uiState.screenshotError?.let { err ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissScreenshot() },
            title = { Text("Screenshot failed") },
            text  = { Text(err) },
            confirmButton = {
                TextButton(onClick = { viewModel.dismissScreenshot() }) { Text("OK") }
            },
        )
    }
}

@Composable
private fun ControlHeader(onBack: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(46.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack, modifier = Modifier.size(44.dp)) {
            Icon(
                Icons.Default.ArrowBack,
                contentDescription = "Back",
                tint = TextPrimary,
                modifier = Modifier.size(32.dp),
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            "Control Mode",
            style = MaterialTheme.typography.headlineLarge.copy(
                fontSize = 31.sp,
                fontWeight = FontWeight.Bold,
            ),
        )
    }
}

@Composable
private fun PowerButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(58.dp),
        shape = RoundedCornerShape(18.dp),
        colors = ButtonDefaults.buttonColors(containerColor = ControlSurface),
        contentPadding = PaddingValues(horizontal = 14.dp),
    ) {
        Icon(icon, contentDescription = null, tint = TextPrimary.copy(alpha = 0.9f), modifier = Modifier.size(27.dp))
        Spacer(Modifier.width(14.dp))
        Text(
            label,
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
            color = TextPrimary.copy(alpha = 0.88f),
        )
    }
}

@Composable
private fun ControlSegmentedTabs(
    activeTab: ControlTab,
    onTouchpad: () -> Unit,
    onRemote: () -> Unit,
) {
    val items = listOf(
        "Touchpad" to onTouchpad,
        "Remote" to onRemote,
    )
    val activeIndex = if (activeTab == ControlTab.REMOTE) 1 else 0
    Row(
        Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(18.dp))
            .border(1.dp, TextSecondary, RoundedCornerShape(18.dp)),
    ) {
        items.forEachIndexed { index, (label, action) ->
            val selected = index == activeIndex
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(if (selected) ControlSurface else Color.Transparent)
                    .clickable(onClick = action),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (selected && index == 0) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        tint = TextPrimary,
                        modifier = Modifier.size(25.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                }
                Text(
                    label,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = TextPrimary.copy(alpha = if (selected) 0.95f else 0.88f),
                )
            }
            if (index < items.lastIndex) {
                Box(
                    Modifier
                        .width(1.dp)
                        .fillMaxHeight()
                        .background(TextSecondary),
                )
            }
        }
    }
}

private enum class CropHandle { NONE, TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT, BODY }

@Composable
private fun ScreenshotDialog(
    bitmap: Bitmap,
    title: String,
    saveLabel: String,
    onSave: (l: Float, t: Float, r: Float, b: Float) -> Unit,
    onDiscard: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDiscard,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp))
                .background(Color(0xFF1A1F26))
                .padding(horizontal = 16.dp, vertical = 22.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "Screen capture",
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
            )
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = title,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(255.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.Black),
            )

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                OutlinedButton(
                    onClick = onDiscard,
                    modifier = Modifier.weight(1f).height(58.dp),
                    shape = RoundedCornerShape(28.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentBlue),
                ) {
                    Text("Cancel", style = MaterialTheme.typography.titleMedium)
                }
                Button(
                    onClick = { onSave(0f, 0f, 1f, 1f) },
                    modifier = Modifier.weight(1f).height(58.dp),
                    shape = RoundedCornerShape(28.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF171D24)),
                ) {
                    Icon(Icons.Default.Download, contentDescription = null, tint = AccentBlue, modifier = Modifier.size(25.dp))
                    Spacer(Modifier.width(14.dp))
                    Text("Download", style = MaterialTheme.typography.titleMedium, color = AccentBlue)
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.TouchpadTab(
    uiState: ControlUiState,
    viewModel: ControlViewModel,
    onToggleKeyboard: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(CardDark, RoundedCornerShape(16.dp))
            .border(1.dp, DividerColor, RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (uiState.cursorMode) Icons.Default.Mouse else Icons.Default.TouchApp,
            contentDescription = null,
            tint = AccentBlue,
            modifier = Modifier.size(24.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                if (uiState.cursorMode) "Cursor mode" else "Touch mode",
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
            )
            Text(
                if (uiState.cursorMode) "Relative trackpad movement" else "Direct touch input",
                style = MaterialTheme.typography.labelMedium,
                color = TextSecondary,
            )
        }
        Switch(
            checked = uiState.cursorMode,
            onCheckedChange = { viewModel.toggleCursorMode() },
        )
    }

    val mode = if (uiState.cursorMode) TouchpadMode.CURSOR else TouchpadMode.DIRECT

    Row(
        Modifier
            .weight(1f)
            .fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        VerticalScrollStrip(onScroll = { viewModel.onScrollV(it) })
        TouchpadSurface(
            modifier          = Modifier.weight(1f),
            mode              = mode,
            onTap             = { nx, ny -> viewModel.onTap(nx, ny) },
            onDoubleTap       = { nx, ny -> viewModel.onDoubleTap(nx, ny) },
            onLongPress       = { nx, ny -> viewModel.onLongPress(nx, ny) },
            onSwipe           = { x1, y1, x2, y2 -> viewModel.onSwipe(x1, y1, x2, y2) },
            onCursorMove      = { dx, dy -> viewModel.moveCursorBy(dx, dy) },
            onCursorTap       = { viewModel.cursorTap() },
            onCursorDoubleTap = { viewModel.cursorDoubleTap() },
            onCursorLongPress = { viewModel.cursorLongPress() },
        )
        VerticalScrollStrip(onScroll = { viewModel.onScrollV(it) })
    }

    HorizontalScrollStrip(onScroll = { viewModel.onScrollH(it) })

    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        LargeControlButton(
            icon = Icons.Default.Mouse,
            label = "Left",
            modifier = Modifier.weight(1f),
        ) {
            if (uiState.cursorMode) viewModel.cursorTap() else viewModel.onTap(0.5f, 0.5f)
        }
        LargeControlButton(
            icon = Icons.Default.Mouse,
            label = "Right",
            modifier = Modifier.weight(1f),
        ) {
            viewModel.onLongPress(0.5f, 0.5f)
        }
    }
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        LargeControlButton(
            icon = Icons.Default.Keyboard,
            label = "Keyboard",
            modifier = Modifier.weight(3f),
        ) { onToggleKeyboard() }
        LargeControlButton(
            icon = Icons.Default.Backspace,
            label = "Del",
            modifier = Modifier.weight(1f),
        ) { viewModel.sendBackspace() }
    }
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        LargeControlButton(Icons.Default.ArrowBack, "Back", Modifier.weight(1f)) { viewModel.pressBack() }
        LargeControlButton(Icons.Default.Home, "Home", Modifier.weight(1f)) { viewModel.pressHome() }
        LargeControlButton(Icons.Default.GridView, "Recents", Modifier.weight(1f)) { viewModel.pressRecents() }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RemoteTab(viewModel: ControlViewModel, onToggleKeyboard: () -> Unit) {
    val customButtons by viewModel.customButtons.collectAsState()
    data class RemoteAction(
        val icon: ImageVector,
        val label: String,
        val onClick: () -> Unit,
    )
    val systemActions = listOf(
        RemoteAction(Icons.Default.ArrowBack, "Back") { viewModel.pressBack() },
        RemoteAction(Icons.Default.Home, "Home") { viewModel.pressHome() },
        RemoteAction(Icons.Default.GridView, "Recents") { viewModel.pressRecents() },
        RemoteAction(Icons.Default.VolumeUp, "Vol+") { viewModel.pressVolumeUp() },
        RemoteAction(Icons.Default.VolumeDown, "Vol-") { viewModel.pressVolumeDown() },
        RemoteAction(Icons.Default.VolumeOff, "Mute") { viewModel.pressMute() },
        RemoteAction(Icons.Default.WbSunny, "Bright+") { viewModel.pressBrightnessUp() },
        RemoteAction(Icons.Default.BrightnessLow, "Bright-") { viewModel.pressBrightnessDown() },
        RemoteAction(Icons.Default.Bedtime, "Sleep") { viewModel.pressSleep() },
        RemoteAction(Icons.Default.Keyboard, "Keyboard") { onToggleKeyboard() },
        RemoteAction(Icons.Default.Check, "Select") { viewModel.onTap(0.5f, 0.5f) },
        RemoteAction(Icons.Default.Backspace, "Delete") { viewModel.sendBackspace() },
    )

    // Dialog state: null = closed, non-null = editing (new button has empty id)
    var editingButton by remember { mutableStateOf<CustomButton?>(null) }
    var showDeleteConfirm by remember { mutableStateOf<CustomButton?>(null) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // ── Fixed system buttons ──────────────────────────────────────────────
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            systemActions.chunked(3).forEach { row ->
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    row.forEach { action ->
                        RemoteGridButton(
                            icon = action.icon,
                            label = action.label,
                            onClick = action.onClick,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }

        // ── Custom buttons section ────────────────────────────────────────────
        HorizontalDivider(color = DividerColor, thickness = 0.5.dp)
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Custom Macros",
                style = MaterialTheme.typography.labelLarge,
                color = TextSecondary,
                modifier = Modifier.weight(1f),
            )
            // Add button
            IconButton(onClick = {
                editingButton = CustomButton(id = "", name = "", macro = "", type = "text")
            }) {
                Icon(Icons.Default.Add, contentDescription = "Add macro", tint = AccentBlue)
            }
        }

        if (customButtons.isEmpty()) {
            Text(
                "No custom macros yet.\nTap + to add one.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        } else {
            // 3-column grid — height auto-expands inside the scrollable Column
            val rows = (customButtons.size + 2) / 3
            val gridHeight = (rows * 92).dp
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = gridHeight), // avoid nested infinite height crash
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                userScrollEnabled = false,
            ) {
                items(customButtons, key = { it.id }) { btn ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(2.dp),
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(CardDark)
                                .combinedClickable(
                                    onClick = { viewModel.executeCustomButton(btn) },
                                    onLongClick = { editingButton = btn },
                                )
                                .padding(horizontal = 6.dp, vertical = 4.dp),
                        ) {
                            Text(
                                btn.name.take(14),
                                style = MaterialTheme.typography.labelMedium,
                                color = AccentBlue,
                                maxLines = 2,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            )
                        }
                        Text(
                            "hold to edit",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }

    // ── Add / Edit dialog ─────────────────────────────────────────────────────
    editingButton?.let { editing ->
        CustomButtonDialog(
            initial = editing,
            onSave = { name, macro, type ->
                viewModel.saveCustomButton(
                    name = name,
                    macro = macro,
                    type = type,
                    id = editing.id.ifEmpty { null },
                )
                editingButton = null
            },
            onDelete = if (editing.id.isNotEmpty()) {
                { showDeleteConfirm = editing; editingButton = null }
            } else null,
            onDismiss = { editingButton = null },
        )
    }

    // ── Delete confirmation dialog ────────────────────────────────────────────
    showDeleteConfirm?.let { btn ->
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = null },
            title = { Text("Delete \"${btn.name}\"?") },
            text  = { Text("This macro will be permanently removed.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteCustomButton(btn.id)
                    showDeleteConfirm = null
                }) { Text("Delete", color = StatusRed) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = null }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun CustomButtonDialog(
    initial: CustomButton,
    onSave: (name: String, macro: String, type: String) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    var name  by remember { mutableStateOf(initial.name) }
    var macro by remember { mutableStateOf(initial.macro) }
    var type  by remember { mutableStateOf(initial.type) }
    val isNew = initial.id.isEmpty()

    val macroTypes = listOf("text", "enter", "tab", "esc")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isNew) "New Macro Button" else "Edit Macro Button") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Button name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                // Type selector
                Text("Action type", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    macroTypes.forEach { t ->
                        FilterChip(
                            selected = type == t,
                            onClick = { type = t },
                            label = { Text(t) },
                        )
                    }
                }
                // Only show macro text field for "text" type
                if (type == "text") {
                    OutlinedTextField(
                        value = macro,
                        onValueChange = { macro = it },
                        label = { Text("Text to type") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (onDelete != null) {
                    TextButton(onClick = onDelete) {
                        Text("Delete", color = StatusRed)
                    }
                }
                TextButton(
                    onClick = {
                        if (name.isNotBlank()) onSave(name, macro, type)
                    },
                    enabled = name.isNotBlank(),
                ) { Text("Save") }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
        containerColor = CardDark,
    )
}

@Composable
private fun RemoteGridButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(78.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = CardDark),
        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(icon, contentDescription = null, tint = AccentBlue, modifier = Modifier.size(28.dp))
            Spacer(Modifier.height(6.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                color = TextPrimary,
                maxLines = 1,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
}

@Composable
private fun RemoteBtn(icon: ImageVector, label: String, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        FilledIconButton(
            onClick = onClick,
            modifier = Modifier.size(64.dp),
            shape = RoundedCornerShape(14.dp),
            colors = IconButtonDefaults.filledIconButtonColors(containerColor = CardDark),
        ) {
            Icon(icon, contentDescription = label, tint = AccentBlue, modifier = Modifier.size(26.dp))
        }
        Text(label, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 2.dp))
    }
}

@Composable
private fun LargeControlButton(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(64.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = ControlSurface),
        contentPadding = PaddingValues(horizontal = 8.dp),
    ) {
        Icon(icon, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(30.dp))
        Spacer(Modifier.width(14.dp))
        Text(
            label,
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
            color = TextPrimary.copy(alpha = 0.9f),
            maxLines = 1,
        )
    }
}

@Composable
private fun CtrlButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(icon, contentDescription = label, tint = AccentBlue, modifier = Modifier.size(24.dp))
    }
}
