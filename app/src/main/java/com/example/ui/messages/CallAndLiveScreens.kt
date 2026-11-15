package com.example.ui.messages

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.CallStatus
import com.example.data.local.CallType
import com.example.data.local.LiveStreamStatus
import com.example.data.model.CallUiModel
import com.example.data.model.LiveStreamSummaryUiModel
import com.example.ui.components.GoldenPulseLoader
import com.example.ui.components.GoldenVerifiedBadge
import com.example.ui.components.UserAvatar
import com.example.ui.main.MainViewModel
import com.example.ui.theme.*
import kotlinx.coroutines.delay
import java.util.Locale

@Composable
private fun rememberNetworkConnected(): Boolean {
    val context = LocalContext.current
    val cm = remember(context) {
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    }

    fun checkCurrentlyConnected(): Boolean {
        val activeNetwork = cm?.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    var isConnected by remember { mutableStateOf(checkCurrentlyConnected()) }

    DisposableEffect(cm) {
        if (cm == null) {
            return@DisposableEffect onDispose {}
        }
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                isConnected = true
            }

            override fun onLost(network: Network) {
                isConnected = checkCurrentlyConnected()
            }

            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                isConnected = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            }
        }
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        runCatching { cm.registerNetworkCallback(request, callback) }
        onDispose {
            runCatching { cm.unregisterNetworkCallback(callback) }
        }
    }
    return isConnected
}

@Composable
fun CameraPreviewSurface(
    modifier: Modifier = Modifier,
    useFrontCamera: Boolean = true,
    isActive: Boolean = true
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraProviderFuture = remember(context) { ProcessCameraProvider.getInstance(context) }
    var boundProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    var bindError by remember { mutableStateOf<String?>(null) }

    DisposableEffect(lifecycleOwner, isActive) {
        onDispose {
            runCatching { boundProvider?.unbindAll() }
        }
    }

    LaunchedEffect(isActive) {
        if (!isActive) {
            runCatching { boundProvider?.unbindAll() }
        }
    }

    if (!isActive) {
        Box(modifier = modifier.background(ObsidianBlack))
    } else if (bindError != null) {
        Box(
            modifier = modifier.background(ObsidianCard),
            contentAlignment = Alignment.Center
        ) {
            Text(bindError ?: "Camera unavailable", color = WarmGrayText, style = MaterialTheme.typography.bodySmall)
        }
    } else {
        AndroidView(
            modifier = modifier,
            factory = { ctx ->
                PreviewView(ctx).apply {
                    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                }
            },
            update = { previewView ->
                cameraProviderFuture.addListener({
                    runCatching {
                        val provider = cameraProviderFuture.get()
                        boundProvider = provider
                        val preview = Preview.Builder().build().apply {
                            setSurfaceProvider(previewView.surfaceProvider)
                        }
                        val selector = if (useFrontCamera) {
                            CameraSelector.DEFAULT_FRONT_CAMERA
                        } else {
                            CameraSelector.DEFAULT_BACK_CAMERA
                        }
                        provider.unbindAll()
                        provider.bindToLifecycle(lifecycleOwner, selector, preview)
                    }.onFailure {
                        bindError = "Camera preview inactive on this device"
                    }
                }, ContextCompat.getMainExecutor(context))
            }
        )
    }
}

@Composable
fun LiveStreamScreen(
    streamId: String?,
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val isConnected = rememberNetworkConnected()
    val isLiveActionInProgress by viewModel.isLiveActionInProgress.collectAsStateWithLifecycle()
    val liveActionError by viewModel.liveActionError.collectAsStateWithLifecycle()
    val endedSummary by viewModel.endedLiveSummary.collectAsStateWithLifecycle()
    val activeHostedStreamId by viewModel.activeHostedStreamId.collectAsStateWithLifecycle()
    val activeLiveStreams by viewModel.activeLiveStreams.collectAsStateWithLifecycle()

    val existingHostStreamId = if (streamId.isNullOrBlank()) {
        activeHostedStreamId ?: activeLiveStreams.firstOrNull { it.host.id == viewModel.currentUserId }?.id
    } else null

    var createdStreamId by remember(streamId, existingHostStreamId) {
        mutableStateOf(streamId?.takeIf { it.isNotBlank() } ?: existingHostStreamId)
    }
    var streamTitle by remember { mutableStateOf("Live Session with SCROLL DOWN") }
    var commentInput by remember { mutableStateOf("") }
    var useFrontCamera by remember { mutableStateOf(true) }
    var isMicMuted by remember { mutableStateOf(false) }
    var showEndConfirmDialog by remember { mutableStateOf(false) }
    var viewerStreamEnded by remember(streamId) { mutableStateOf(false) }
    var hasResolvedInitialStream by remember(streamId) { mutableStateOf(false) }

    var hasCameraPerm by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    var hasMicPerm by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
        hasCameraPerm = results[Manifest.permission.CAMERA] == true || hasCameraPerm
        hasMicPerm = results[Manifest.permission.RECORD_AUDIO] == true || hasMicPerm
    }

    val activeId = createdStreamId
    val observedStreamFlow = remember(activeId) {
        if (activeId != null) viewModel.observeLiveStreamById(activeId)
        else kotlinx.coroutines.flow.flowOf(null)
    }
    val liveStream by observedStreamFlow.collectAsStateWithLifecycle(initialValue = null)

    LaunchedEffect(activeId, liveStream) {
        if (activeId != null && liveStream != null) {
            hasResolvedInitialStream = true
        } else if (activeId != null && !streamId.isNullOrBlank()) {
            delay(1800L)
            hasResolvedInitialStream = true
        }
    }

    val isHost = (liveStream?.host?.id == viewModel.currentUserId) ||
        (streamId.isNullOrBlank() && activeId != null && liveStream == null)
    val isStreamCurrentlyLive = liveStream != null &&
        liveStream?.isLive == true &&
        liveStream?.status != LiveStreamStatus.ENDED

    val isEndedForViewerInitial = !isHost && activeId != null && (
        viewerStreamEnded ||
            (liveStream != null && (!liveStream!!.isLive || liveStream!!.status == LiveStreamStatus.ENDED)) ||
            (hasResolvedInitialStream && liveStream == null)
        )

    val comments by remember(activeId, isEndedForViewerInitial, endedSummary) {
        if (activeId != null && !isEndedForViewerInitial && endedSummary == null) {
            viewModel.observeLiveComments(activeId)
        } else {
            kotlinx.coroutines.flow.flowOf(emptyList())
        }
    }.collectAsStateWithLifecycle(initialValue = emptyList())

    // Release microphone mute state cleanly on dispose
    DisposableEffect(Unit) {
        onDispose {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            runCatching {
                if (audioManager?.isMicrophoneMute == true) {
                    audioManager.isMicrophoneMute = false
                }
            }
            viewModel.clearLiveActionError()
        }
    }

    // Intercept Android Back for active host so live stream never stays orphaned
    BackHandler {
        when {
            endedSummary != null -> {
                viewModel.clearEndedLiveSummary()
                onBack()
            }
            activeId != null && isHost && isStreamCurrentlyLive -> {
                showEndConfirmDialog = true
            }
            else -> onBack()
        }
    }

    // Viewer presence join & cleanup + reject joining ended streams
    DisposableEffect(activeId, isHost, isConnected) {
        val id = activeId
        if (id != null && !streamId.isNullOrBlank() && !isHost) {
            viewModel.joinLiveStream(id) {
                viewerStreamEnded = true
            }
        }
        onDispose {
            if (id != null && !streamId.isNullOrBlank() && !isHost) {
                viewModel.leaveLiveStream(id)
            }
        }
    }

    // Auto-exit viewer shortly after host ends the stream
    val isEndedForViewer = !isHost && activeId != null && (
        viewerStreamEnded ||
            (liveStream != null && (!liveStream!!.isLive || liveStream!!.status == LiveStreamStatus.ENDED)) ||
            (hasResolvedInitialStream && liveStream == null)
        )

    LaunchedEffect(isEndedForViewer) {
        if (isEndedForViewer) {
            delay(2200L)
            onBack()
        }
    }

    // Elapsed broadcast timer
    var elapsedSeconds by remember(activeId, liveStream?.startedAt) { mutableLongStateOf(0L) }
    LaunchedEffect(activeId, liveStream?.startedAt, isStreamCurrentlyLive) {
        val started = liveStream?.startedAt
        if (activeId != null && isStreamCurrentlyLive && started != null) {
            while (true) {
                elapsedSeconds = ((System.currentTimeMillis() - started) / 1000L).coerceAtLeast(0L)
                delay(1000L)
            }
        }
    }

    fun triggerEndLiveConfirmed() {
        val id = activeId ?: return
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        runCatching { audioManager?.isMicrophoneMute = false }
        viewModel.endLiveStream(id) {
            showEndConfirmDialog = false
            createdStreamId = null
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBlack)
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        if (!isConnected) {
            Surface(
                color = AmberWarning.copy(alpha = 0.2f),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("live_offline_banner")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Rounded.WifiOff, contentDescription = null, tint = AmberWarning, modifier = Modifier.size(18.dp))
                    Text(
                        text = "Connection lost — reconnecting to live server...",
                        style = MaterialTheme.typography.labelMedium,
                        color = AmberWarning
                    )
                }
            }
        }

        liveActionError?.let { err ->
            Surface(
                color = CrimsonHeart.copy(alpha = 0.2f),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("live_error_banner")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Rounded.ErrorOutline, contentDescription = null, tint = CrimsonHeart, modifier = Modifier.size(18.dp))
                    Text(
                        text = err,
                        style = MaterialTheme.typography.labelMedium,
                        color = IvoryText,
                        modifier = Modifier.weight(1f)
                    )
                    if (activeId != null && isHost) {
                        TextButton(onClick = { triggerEndLiveConfirmed() }) {
                            Text("Retry", color = GoldBright, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        TextButton(onClick = { viewModel.clearLiveActionError() }) {
                            Text("Dismiss", color = GoldBright)
                        }
                    }
                }
            }
        }

        when {
            endedSummary != null -> {
                LiveSummaryContent(
                    summary = endedSummary!!,
                    onDone = {
                        viewModel.clearEndedLiveSummary()
                        onBack()
                    }
                )
            }

            activeId == null -> {
                // Host Go Live Setup Screen
                Row(
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = IvoryText)
                    }
                    Text("Go Live on SCROLL DOWN", style = MaterialTheme.typography.titleLarge, color = GoldBright)
                }
                Column(
                    modifier = Modifier.fillMaxSize().padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    if (hasCameraPerm) {
                        CameraPreviewSurface(
                            useFrontCamera = useFrontCamera,
                            isActive = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(260.dp)
                                .clip(RoundedCornerShape(18.dp))
                                .border(1.dp, GoldPrimary, RoundedCornerShape(18.dp))
                        )
                    } else {
                        Surface(
                            color = ObsidianCard,
                            shape = RoundedCornerShape(18.dp),
                            modifier = Modifier.fillMaxWidth().height(180.dp)
                        ) {
                            Column(
                                modifier = Modifier.fillMaxSize().padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(Icons.Rounded.Videocam, contentDescription = null, tint = GoldBright, modifier = Modifier.size(38.dp))
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("Enable Camera & Mic for live video preview", color = WarmGrayText, style = MaterialTheme.typography.bodySmall)
                                Spacer(modifier = Modifier.height(8.dp))
                                OutlinedButton(
                                    onClick = {
                                        permLauncher.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO))
                                    }
                                ) {
                                    Text("Grant Camera & Mic", color = GoldBright)
                                }
                            }
                        }
                    }

                    OutlinedTextField(
                        value = streamTitle,
                        onValueChange = { streamTitle = it },
                        label = { Text("Live Stream Title") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("live_title_input")
                    )

                    Button(
                        onClick = {
                            viewModel.startLiveStream(streamTitle) { newId ->
                                createdStreamId = newId
                            }
                        },
                        enabled = !isLiveActionInProgress,
                        colors = ButtonDefaults.buttonColors(containerColor = CrimsonHeart, contentColor = IvoryText),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth().height(52.dp).testTag("start_live_button")
                    ) {
                        if (isLiveActionInProgress) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = IvoryText, strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Starting Broadcast...", fontWeight = FontWeight.Bold)
                        } else {
                            Icon(Icons.Rounded.LiveTv, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Start Live Broadcast", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            isEndedForViewer -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(28.dp).testTag("viewer_live_ended_screen"),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier.widthIn(max = 380.dp)
                    ) {
                        Icon(Icons.Rounded.LiveTv, contentDescription = null, tint = GoldBright, modifier = Modifier.size(54.dp))
                        Text("This Live Broadcast Has Ended", color = IvoryText, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                        Text(
                            text = liveStream?.host?.let { "@${it.username} ended \"${liveStream?.title.orEmpty()}\"." }
                                ?: "The host has ended this broadcast.",
                            color = WarmGrayText,
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center
                        )
                        Text("Returning you to the feed...", color = GoldBright, style = MaterialTheme.typography.labelMedium)
                        Button(
                            onClick = onBack,
                            colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary, contentColor = ObsidianBlack),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth().height(48.dp)
                        ) { Text("Back to Feed", fontWeight = FontWeight.Bold) }
                    }
                }
            }

            liveStream == null -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        GoldenPulseLoader()
                        Text("Connecting to Live Broadcast...", color = WarmGrayText, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            else -> {
                val stream = liveStream!!
                // Active Broadcast / Viewer Stage
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    if (isHost && hasCameraPerm) {
                        CameraPreviewSurface(
                            useFrontCamera = useFrontCamera,
                            isActive = isStreamCurrentlyLive && !isLiveActionInProgress && endedSummary == null,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Box(
                            modifier = Modifier.fillMaxSize().background(ObsidianSurface),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                UserAvatar(avatarUri = stream.host.avatarUri, displayName = stream.host.displayName, size = 86.dp)
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                    Text("@${stream.host.username} is LIVE", style = MaterialTheme.typography.titleLarge, color = GoldBright)
                                    if (stream.host.isGoldenVerified) GoldenVerifiedBadge(size = 18.dp)
                                }
                                Text(stream.title, style = MaterialTheme.typography.bodyMedium, color = IvoryText)
                            }
                        }
                    }

                    // Live Top Header
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.TopCenter)
                            .background(ObsidianBlack.copy(alpha = 0.62f))
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(color = CrimsonHeart, shape = RoundedCornerShape(6.dp)) {
                            Text(
                                text = String.format(Locale.US, "LIVE %02d:%02d", elapsedSeconds / 60, elapsedSeconds % 60),
                                color = IvoryText,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                        Surface(color = ObsidianCard, shape = RoundedCornerShape(6.dp)) {
                            Text(
                                "${stream.viewerCount} watching",
                                color = GoldBright,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                "@${stream.host.username}",
                                color = IvoryText,
                                style = MaterialTheme.typography.labelLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (stream.host.isGoldenVerified) GoldenVerifiedBadge(size = 13.dp)
                        }
                        if (isHost) {
                            if (hasMicPerm) {
                                IconButton(
                                    onClick = {
                                        isMicMuted = !isMicMuted
                                        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                                        runCatching { audioManager?.isMicrophoneMute = isMicMuted }
                                    }
                                ) {
                                    Icon(
                                        if (isMicMuted) Icons.Rounded.MicOff else Icons.Rounded.Mic,
                                        contentDescription = if (isMicMuted) "Unmute mic" else "Mute mic",
                                        tint = if (isMicMuted) CrimsonHeart else IvoryText
                                    )
                                }
                            }
                            if (hasCameraPerm) {
                                IconButton(onClick = { useFrontCamera = !useFrontCamera }) {
                                    Icon(Icons.Rounded.Cameraswitch, contentDescription = "Switch camera", tint = IvoryText)
                                }
                            }
                            Button(
                                onClick = { showEndConfirmDialog = true },
                                enabled = !isLiveActionInProgress,
                                colors = ButtonDefaults.buttonColors(containerColor = CrimsonHeart, contentColor = IvoryText),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                modifier = Modifier.heightIn(min = 40.dp).testTag("end_live_button")
                            ) {
                                if (isLiveActionInProgress) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = IvoryText, strokeWidth = 2.dp)
                                } else {
                                    Text("End Live", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                }
                            }
                        } else {
                            IconButton(onClick = onBack, modifier = Modifier.testTag("viewer_leave_live_button")) {
                                Icon(Icons.Rounded.Close, contentDescription = "Leave live", tint = IvoryText)
                            }
                        }
                    }

                    // Live Comments Overlay
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 220.dp)
                            .align(Alignment.BottomStart)
                            .background(ObsidianBlack.copy(alpha = 0.5f))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(comments, key = { it.id }) { c ->
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("@${c.author.username}", style = MaterialTheme.typography.labelMedium, color = GoldBright, fontWeight = FontWeight.Bold)
                                if (c.author.isGoldenVerified) GoldenVerifiedBadge(size = 11.dp)
                                Text(c.text, style = MaterialTheme.typography.bodySmall, color = IvoryText)
                            }
                        }
                    }
                }

                // Live Comment Input
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(ObsidianSurface)
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = commentInput,
                        onValueChange = { commentInput = it },
                        placeholder = { Text("Say something live...") },
                        singleLine = true,
                        enabled = isStreamCurrentlyLive && isConnected,
                        modifier = Modifier.weight(1f).testTag("live_comment_input")
                    )
                    IconButton(
                        enabled = isStreamCurrentlyLive && isConnected && commentInput.isNotBlank(),
                        onClick = {
                            if (commentInput.isNotBlank()) {
                                viewModel.sendLiveComment(activeId, commentInput)
                                commentInput = ""
                            }
                        }
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send live comment", tint = GoldBright)
                    }
                }
            }
        }
    }

    if (showEndConfirmDialog && activeId != null && isHost) {
        AlertDialog(
            onDismissRequest = { if (!isLiveActionInProgress) showEndConfirmDialog = false },
            containerColor = ObsidianSurface,
            title = { Text("End Live Broadcast?", color = GoldBright, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Your camera and microphone will stop immediately, active viewers will be notified, and you will see your broadcast summary.",
                    color = WarmGrayText,
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = { triggerEndLiveConfirmed() },
                    enabled = !isLiveActionInProgress,
                    colors = ButtonDefaults.buttonColors(containerColor = CrimsonHeart, contentColor = IvoryText),
                    modifier = Modifier.testTag("confirm_end_live_button")
                ) {
                    if (isLiveActionInProgress) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = IvoryText, strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Ending...")
                    } else {
                        Text("End Live Now", fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !isLiveActionInProgress,
                    onClick = { showEndConfirmDialog = false },
                    modifier = Modifier.testTag("cancel_end_live_button")
                ) {
                    Text("Continue Live", color = GoldBright)
                }
            }
        )
    }
}

@Composable
private fun LiveSummaryContent(
    summary: LiveStreamSummaryUiModel,
    onDone: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .testTag("live_summary_screen"),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            color = ObsidianCard,
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .widthIn(max = 420.dp)
                .fillMaxWidth()
                .border(1.2.dp, GoldPrimary.copy(alpha = 0.6f), RoundedCornerShape(24.dp))
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(GoldTintSurface)
                        .border(1.dp, GoldPrimary, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.LiveTv, contentDescription = null, tint = GoldBright, modifier = Modifier.size(32.dp))
                }

                Text(
                    text = "Live Broadcast Ended",
                    style = MaterialTheme.typography.headlineSmall,
                    color = GoldBright,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = summary.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = IvoryText,
                    textAlign = TextAlign.Center
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    LiveStatBox(
                        label = "Duration",
                        value = String.format(Locale.US, "%02d:%02d", summary.durationSeconds / 60, summary.durationSeconds % 60),
                        modifier = Modifier.weight(1f)
                    )
                    LiveStatBox(
                        label = "Peak Viewers",
                        value = summary.peakViewerCount.toString(),
                        modifier = Modifier.weight(1f)
                    )
                    LiveStatBox(
                        label = "Comments",
                        value = summary.commentsCount.toString(),
                        modifier = Modifier.weight(1f)
                    )
                }

                Button(
                    onClick = onDone,
                    colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary, contentColor = ObsidianBlack),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("live_summary_done_button")
                ) {
                    Text("Back to Feed", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun LiveStatBox(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        color = ObsidianSurface,
        shape = RoundedCornerShape(14.dp),
        modifier = modifier.border(0.8.dp, ObsidianOutline, RoundedCornerShape(14.dp))
    ) {
        Column(
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(value, style = MaterialTheme.typography.titleLarge, color = GoldBright, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.labelSmall, color = WarmGrayText)
        }
    }
}

@Composable
fun ActiveCallOverlay(
    call: CallUiModel,
    currentUserId: String,
    viewModel: MainViewModel
) {
    val context = LocalContext.current
    val isIncoming = call.callee.id == currentUserId && call.status == CallStatus.RINGING
    val peer = if (call.caller.id == currentUserId) call.callee else call.caller
    var isMuted by remember(call.id) { mutableStateOf(false) }
    var elapsedSec by remember(call.id, call.status) { mutableIntStateOf(0) }
    var hasCameraPerm by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    val camPermLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasCameraPerm = granted
    }

    LaunchedEffect(call.id, call.status) {
        if (call.status == CallStatus.ACCEPTED) {
            if (call.callType == CallType.VIDEO && !hasCameraPerm) {
                camPermLauncher.launch(Manifest.permission.CAMERA)
            }
            while (true) {
                delay(1000L)
                elapsedSec += 1
            }
        }
    }

    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(ObsidianBlack)
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            if (call.callType == CallType.VIDEO && call.status == CallStatus.ACCEPTED && hasCameraPerm) {
                CameraPreviewSurface(
                    useFrontCamera = true,
                    isActive = true,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(24.dp))
                )
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier
                    .background(ObsidianBlack.copy(alpha = 0.65f), RoundedCornerShape(24.dp))
                    .padding(28.dp)
            ) {
                UserAvatar(avatarUri = peer.avatarUri, displayName = peer.displayName, size = 96.dp)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(peer.displayName, style = MaterialTheme.typography.headlineSmall, color = IvoryText)
                    if (peer.isGoldenVerified) GoldenVerifiedBadge(size = 18.dp)
                }
                Text(
                    text = when (call.status) {
                        CallStatus.RINGING -> if (isIncoming) "Incoming ${call.callType.lowercase()} call..." else "Ringing @${peer.username}..."
                        CallStatus.ACCEPTED -> String.format(Locale.US, "%s Call • %02d:%02d", call.callType, elapsedSec / 60, elapsedSec % 60)
                        else -> "Ending call..."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = GoldBright
                )

                Spacer(modifier = Modifier.height(12.dp))

                if (isIncoming) {
                    Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                        IconButton(
                            onClick = { viewModel.respondToCall(call.id, accept = false) },
                            modifier = Modifier.size(64.dp).clip(CircleShape).background(CrimsonHeart)
                        ) {
                            Icon(Icons.Rounded.CallEnd, contentDescription = "Decline call", tint = IvoryText, modifier = Modifier.size(30.dp))
                        }
                        IconButton(
                            onClick = { viewModel.respondToCall(call.id, accept = true) },
                            modifier = Modifier.size(64.dp).clip(CircleShape).background(EmeraldOnline)
                        ) {
                            Icon(Icons.Rounded.Call, contentDescription = "Accept call", tint = ObsidianBlack, modifier = Modifier.size(30.dp))
                        }
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                        IconButton(
                            onClick = { isMuted = !isMuted },
                            modifier = Modifier.size(56.dp).clip(CircleShape).background(ObsidianCard)
                        ) {
                            Icon(
                                if (isMuted) Icons.Rounded.MicOff else Icons.Rounded.Mic,
                                contentDescription = "Toggle mute",
                                tint = if (isMuted) CrimsonHeart else GoldBright
                            )
                        }
                        IconButton(
                            onClick = { viewModel.endCall(call.id) },
                            modifier = Modifier.size(64.dp).clip(CircleShape).background(CrimsonHeart).testTag("end_call_button")
                        ) {
                            Icon(Icons.Rounded.CallEnd, contentDescription = "End call", tint = IvoryText, modifier = Modifier.size(30.dp))
                        }
                    }
                }
            }
        }
    }
}
