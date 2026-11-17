package com.plasticcheese.endoscopeviewer

import android.widget.Toast
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemGestures
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val viewerColors = darkColorScheme(
    primary = Color(0xFFA1D2CA),
    onPrimary = Color(0xFF003731),
    surface = Color(0xFF161C1B),
    surfaceContainer = Color(0xFF222B2A),
)

@Composable
internal fun ViewerScreen(session: CameraSession, onCameraPermission: () -> Unit) {
    var turns by rememberSaveable { mutableIntStateOf(0) }
    var mirrored by rememberSaveable { mutableStateOf(false) }
    var zoom by rememberSaveable { mutableFloatStateOf(1f) }
    var showControls by rememberSaveable { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    var preview by remember { mutableStateOf<CameraPreview?>(null) }
    val transform = PreviewTransform(turns, mirrored, zoom)
    val status = session.status
    val streaming = status == CameraStatus.Streaming
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val toggleLabel = stringResource(
        if (showControls) R.string.hide_controls else R.string.show_controls,
    )
    MaterialTheme(colorScheme = viewerColors) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            AndroidView(
                factory = { viewContext -> CameraPreview(viewContext, session).also { preview = it } },
                modifier = Modifier.fillMaxSize(),
                update = { it.update(session.resolution, transform) },
            )
            if (streaming) {
                Box(
                    Modifier.fillMaxSize()
                        .clickable(onClickLabel = toggleLabel) { showControls = !showControls }
                        .pointerInput(Unit) {
                            detectTransformGestures { _, _, zoomChange, _ ->
                                zoom = (zoom * zoomChange).coerceIn(1f, 4f)
                            }
                        },
                )
            } else {
                CameraMessage(
                    status = status,
                    onCameraPermission = onCameraPermission,
                    onRetry = session::retry,
                )
            }
            if (streaming && showControls) {
                CameraControls(
                    session = session,
                    mirrored = mirrored,
                    zoom = zoom,
                    saving = saving,
                    onRotate = { turns = (turns + 1) % 4 },
                    onMirror = { mirrored = !mirrored },
                    onZoom = { zoom = it },
                    onPhoto = {
                        val photo = preview?.snapshot()
                        if (photo == null) {
                            Toast.makeText(context, R.string.photo_failed, Toast.LENGTH_SHORT).show()
                        } else {
                            saving = true
                            scope.launch {
                                try {
                                    val saved = withContext(Dispatchers.IO) {
                                        runCatching { savePhoto(context.contentResolver, photo) }.isSuccess
                                    }
                                    val message = if (saved) R.string.photo_saved else R.string.photo_failed
                                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                                } finally {
                                    photo.recycle()
                                    saving = false
                                }
                            }
                        }
                    },
                    modifier = Modifier.align(Alignment.BottomCenter).windowInsetsPadding(
                        WindowInsets.displayCutout.union(WindowInsets.systemGestures),
                    ).padding(8.dp),
                )
            } else if (status == CameraStatus.Failed && session.resolutions.isNotEmpty()) {
                ResolutionPicker(
                    session,
                    Modifier.align(Alignment.BottomCenter).windowInsetsPadding(
                        WindowInsets.displayCutout.union(WindowInsets.systemGestures),
                    ).padding(8.dp),
                )
            }
        }
    }
}

@Composable
private fun CameraControls(
    session: CameraSession,
    mirrored: Boolean,
    zoom: Float,
    saving: Boolean,
    onRotate: () -> Unit,
    onMirror: () -> Unit,
    onZoom: (Float) -> Unit,
    onPhoto: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.widthIn(max = 520.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilledTonalButton(onClick = onRotate) { Text(stringResource(R.string.rotate)) }
                FilterChip(selected = mirrored, onClick = onMirror, label = {
                    Text(stringResource(R.string.mirror))
                })
                Button(onClick = onPhoto, enabled = !saving) { Text(stringResource(R.string.capture)) }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ResolutionPicker(session)
                val zoomLabel = stringResource(R.string.zoom)
                Slider(
                    value = zoom,
                    onValueChange = onZoom,
                    valueRange = 1f..4f,
                    modifier = Modifier.weight(1f).semantics { contentDescription = zoomLabel },
                )
                Text(
                    stringResource(R.string.zoom_value, zoom),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.widthIn(min = 40.dp),
                )
            }
        }
    }
}

@Composable
private fun ResolutionPicker(session: CameraSession, modifier: Modifier = Modifier) {
    var menuOpen by remember { mutableStateOf(false) }
    val resolution = session.resolution
    val label = stringResource(R.string.resolution)
    Box(modifier) {
        FilledTonalButton(
            onClick = { menuOpen = true },
            enabled = session.resolutions.size > 1 || session.status == CameraStatus.Failed,
            modifier = Modifier.semantics { contentDescription = label },
        ) {
            Text(
                if (resolution == null) label
                else stringResource(R.string.dimensions, resolution.width, resolution.height),
                maxLines = 1,
            )
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            session.resolutions.forEach {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.dimensions, it.width, it.height)) },
                    onClick = {
                        menuOpen = false
                        session.changeResolution(it)
                    },
                )
            }
        }
    }
}

@Composable
private fun CameraMessage(status: CameraStatus, onCameraPermission: () -> Unit, onRetry: () -> Unit) {
    @StringRes val title: Int
    @StringRes var message: Int? = null
    @StringRes var button: Int? = null
    when (status) {
        CameraStatus.Disconnected -> {
            title = R.string.connect_camera
            message = R.string.connect_help
        }
        CameraStatus.Connecting -> title = R.string.connecting
        CameraStatus.CameraPermission -> {
            title = R.string.camera_permission
            message = R.string.camera_permission_help
            button = R.string.continue_permission
        }
        CameraStatus.UsbPermission -> {
            title = R.string.usb_permission
            message = R.string.usb_permission_help
            button = R.string.retry
        }
        CameraStatus.Failed -> {
            title = R.string.camera_failed
            message = R.string.camera_failed_help
            button = R.string.retry
        }
        CameraStatus.Streaming -> return
    }

    Surface(color = Color.Black, modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier.windowInsetsPadding(WindowInsets.displayCutout)
                .padding(horizontal = 24.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier.widthIn(max = 360.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (status != CameraStatus.Failed) SplashBranding()
                if (status == CameraStatus.Connecting) CircularProgressIndicator(Modifier.size(24.dp))
                Text(
                    stringResource(title),
                    style = if (status == CameraStatus.Failed) {
                        MaterialTheme.typography.headlineSmall
                    } else {
                        MaterialTheme.typography.titleMedium
                    },
                    textAlign = TextAlign.Center,
                )
                message?.let {
                    Text(
                        text = stringResource(it),
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                button?.let {
                    val action = if (status == CameraStatus.CameraPermission) onCameraPermission else onRetry
                    Button(onClick = action) { Text(stringResource(it)) }
                }
            }
        }
    }
}
