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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Control") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    // Screenshot button — shows spinner while capture is in progress
                    if (uiState.isCapturingScreenshot) {
                        Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(Modifier.size(20.dp), color = AccentBlue, strokeWidth = 2.dp)
                        }
                    } else {
                        IconButton(onClick = { viewModel.requestScreenshot() }) {
                            Icon(Icons.Default.CameraAlt, contentDescription = "Glasses screenshot", tint = AccentBlue)
                        }
                        IconButton(
                            onClick = {
                                viewModel.prepareLocalScreenshotCapture()
                                localScreenshotLauncher.launch(
                                    LocalScreenshotCapturer.createCaptureIntent(context),
                                )
                            },
                        ) {
                            Icon(Icons.Default.PhoneAndroid, contentDescription = "Phone screenshot", tint = AccentBlue)
                        }
                    }
                    IconButton(onClick = { viewModel.pressSleep() }) {
                        if (uiState.isSleeping) {
                            Icon(
                                Icons.Default.WbSunny,
                                contentDescription = "Wake",
                                tint = AccentBlue,
                                modifier = Modifier.size(20.dp),
                            )
                        } else {
                            Text(
                                text = "ZZZ",
                                color = StatusYellow,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = (-0.5).sp,
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceDark),
            )
        },
        containerColor = DarkBackground,
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Tab row
            TabRow(
                selectedTabIndex = uiState.activeTab.ordinal,
                containerColor = CardDark,
                contentColor = AccentBlue,
                modifier = Modifier.clip(RoundedCornerShape(10.dp)),
            ) {
                ControlTab.values().forEach { tab ->
                    Tab(
                        selected = uiState.activeTab == tab,
                        onClick = { viewModel.setTab(tab) },
                        text = {
                            Text(
                                tab.name.replace('_', ' '),
                                fontSize = 13.sp,
                                fontWeight = if (uiState.activeTab == tab) FontWeight.SemiBold else FontWeight.Normal,
                            )
                        }
                    )
                }
            }

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

private enum class CropHandle { NONE, TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT, BODY }

@Composable
private fun ScreenshotDialog(
    bitmap: Bitmap,
    title: String,
    saveLabel: String,
    onSave: (l: Float, t: Float, r: Float, b: Float) -> Unit,
    onDiscard: () -> Unit,
) {
    // Crop rect in normalized [0,1] coords
    var cl by remember { mutableFloatStateOf(0f) }
    var ct by remember { mutableFloatStateOf(0f) }
    var cr by remember { mutableFloatStateOf(1f) }
    var cb by remember { mutableFloatStateOf(1f) }

    Dialog(
        onDismissRequest = onDiscard,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.88f)
                .clip(RoundedCornerShape(16.dp))
                .background(CardDark)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)

            // Crop area — image + interactive overlay
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp)),
            ) {
                var boxSize by remember { mutableStateOf(IntSize.Zero) }
                var activeHandle by remember { mutableStateOf(CropHandle.NONE) }
                val handlePx = 30f   // touch radius in pixels

                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Screenshot",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .onSizeChanged { boxSize = it },
                )

                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { pos ->
                                    val w = boxSize.width.toFloat()
                                    val h = boxSize.height.toFloat()
                                    val corners = listOf(
                                        CropHandle.TOP_LEFT     to Offset(cl * w, ct * h),
                                        CropHandle.TOP_RIGHT    to Offset(cr * w, ct * h),
                                        CropHandle.BOTTOM_LEFT  to Offset(cl * w, cb * h),
                                        CropHandle.BOTTOM_RIGHT to Offset(cr * w, cb * h),
                                    )
                                    activeHandle = corners
                                        .minByOrNull { (_, p) -> (pos - p).getDistance() }
                                        ?.takeIf { (_, p) -> (pos - p).getDistance() < handlePx * 2.5f }
                                        ?.first
                                        ?: CropHandle.BODY
                                },
                                onDragEnd   = { activeHandle = CropHandle.NONE },
                                onDragCancel = { activeHandle = CropHandle.NONE },
                                onDrag = { change, delta ->
                                    change.consume()
                                    val w = boxSize.width.toFloat().coerceAtLeast(1f)
                                    val h = boxSize.height.toFloat().coerceAtLeast(1f)
                                    val dx = delta.x / w
                                    val dy = delta.y / h
                                    val minSize = 0.05f
                                    when (activeHandle) {
                                        CropHandle.TOP_LEFT -> {
                                            cl = (cl + dx).coerceIn(0f, cr - minSize)
                                            ct = (ct + dy).coerceIn(0f, cb - minSize)
                                        }
                                        CropHandle.TOP_RIGHT -> {
                                            cr = (cr + dx).coerceIn(cl + minSize, 1f)
                                            ct = (ct + dy).coerceIn(0f, cb - minSize)
                                        }
                                        CropHandle.BOTTOM_LEFT -> {
                                            cl = (cl + dx).coerceIn(0f, cr - minSize)
                                            cb = (cb + dy).coerceIn(ct + minSize, 1f)
                                        }
                                        CropHandle.BOTTOM_RIGHT -> {
                                            cr = (cr + dx).coerceIn(cl + minSize, 1f)
                                            cb = (cb + dy).coerceIn(ct + minSize, 1f)
                                        }
                                        CropHandle.BODY -> {
                                            val rw = cr - cl; val rh = cb - ct
                                            cl = (cl + dx).coerceIn(0f, 1f - rw)
                                            ct = (ct + dy).coerceIn(0f, 1f - rh)
                                            cr = cl + rw; cb = ct + rh
                                        }
                                        else -> {}
                                    }
                                },
                            )
                        },
                ) {
                    val w = size.width; val h = size.height
                    val lx = cl * w;  val ty = ct * h
                    val rx = cr * w;  val by_ = cb * h

                    // Dark overlay outside selection
                    val dim = Color(0x99000000)
                    drawRect(dim, size = GeomSize(w, ty))                                   // top
                    drawRect(dim, Offset(0f, by_), GeomSize(w, h - by_))                    // bottom
                    drawRect(dim, Offset(0f, ty), GeomSize(lx, by_ - ty))                   // left
                    drawRect(dim, Offset(rx, ty), GeomSize(w - rx, by_ - ty))               // right

                    // Selection border
                    drawRect(
                        color = Color.White,
                        topLeft = Offset(lx, ty),
                        size = GeomSize(rx - lx, by_ - ty),
                        style = Stroke(width = 2f),
                    )

                    // Rule-of-thirds grid
                    val grid = Color(0x55FFFFFF)
                    for (i in 1..2) {
                        val gx = lx + (rx - lx) / 3 * i
                        val gy = ty + (by_ - ty) / 3 * i
                        drawLine(grid, Offset(gx, ty), Offset(gx, by_), 1f)
                        drawLine(grid, Offset(lx, gy), Offset(rx, gy), 1f)
                    }

                    // Corner handles
                    listOf(Offset(lx, ty), Offset(rx, ty), Offset(lx, by_), Offset(rx, by_))
                        .forEach { p ->
                            drawCircle(Color.White, handlePx * 0.7f, p)
                            drawCircle(Color(0xFF1E88E5.toInt()), handlePx * 0.4f, p)
                        }
                }
            }

            // Buttons
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedButton(
                    onClick = onDiscard,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = StatusRed),
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Discard")
                }
                Button(
                    onClick = { onSave(cl, ct, cr, cb) },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentBlue),
                ) {
                    Icon(Icons.Default.Crop, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(saveLabel)
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
    // Mode toggle row: Cursor (relative trackpad) vs Direct (absolute mobile touch)
    Row(
        Modifier
            .fillMaxWidth()
            .background(CardDark, RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (uiState.cursorMode) Icons.Default.Mouse else Icons.Default.TouchApp,
            contentDescription = null,
            tint = AccentBlue,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(
                if (uiState.cursorMode) "Cursor mode" else "Direct touch",
                style = MaterialTheme.typography.labelLarge,
            )
            Text(
                if (uiState.cursorMode) "Trackpad — moves the glasses cursor"
                else "Absolute — mirrors a phone screen 1:1",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
            )
        }
        Switch(
            checked = uiState.cursorMode,
            onCheckedChange = { viewModel.toggleCursorMode() },
        )
    }

    val mode = if (uiState.cursorMode) TouchpadMode.CURSOR else TouchpadMode.DIRECT

    // Central area: scroll strips + touchpad
    Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        // Left scroll strip
        VerticalScrollStrip(onScroll = { viewModel.onScrollV(it) })

        // Touchpad — behaviour depends on mode
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

        // Right scroll strip
        VerticalScrollStrip(onScroll = { viewModel.onScrollV(it) })
    }

    // Horizontal scroll strip
    HorizontalScrollStrip(onScroll = { viewModel.onScrollH(it) })

    // Bottom action bar
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CtrlButton(Icons.Default.ArrowBack, "Back")    { viewModel.pressBack() }
        CtrlButton(Icons.Default.Home, "Home")         { viewModel.pressHome() }
        CtrlButton(Icons.Default.GridView, "Recents")  { viewModel.pressRecents() }
        CtrlButton(Icons.Default.Keyboard, "Keyboard") { onToggleKeyboard() }
        CtrlButton(Icons.Default.BackHand, "Click") {
            if (uiState.cursorMode) viewModel.cursorTap() else viewModel.onTap(0.5f, 0.5f)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RemoteTab(viewModel: ControlViewModel, onToggleKeyboard: () -> Unit) {
    val customButtons by viewModel.customButtons.collectAsState()

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
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            RemoteBtn(Icons.Default.ArrowBack, "Back") { viewModel.pressBack() }
            RemoteBtn(Icons.Default.Home, "Home") { viewModel.pressHome() }
            RemoteBtn(Icons.Default.GridView, "Recents") { viewModel.pressRecents() }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            RemoteBtn(Icons.Default.VolumeUp, "Vol+") { viewModel.pressVolumeUp() }
            RemoteBtn(Icons.Default.VolumeDown, "Vol-") { viewModel.pressVolumeDown() }
            RemoteBtn(Icons.Default.Bedtime, "Sleep") { viewModel.pressSleep() }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            RemoteBtn(Icons.Default.WbSunny, "Bright+") { /* brightness up */ }
            RemoteBtn(Icons.Default.BrightnessLow, "Bright-") { /* brightness down */ }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            RemoteBtn(Icons.Default.Keyboard, "Keyboard") { onToggleKeyboard() }
            RemoteBtn(Icons.Default.Check, "Select") { viewModel.onTap(0.5f, 0.5f) }
            RemoteBtn(Icons.Default.Backspace, "Delete") { viewModel.sendBackspace() }
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
private fun CtrlButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(icon, contentDescription = label, tint = AccentBlue, modifier = Modifier.size(24.dp))
    }
}
