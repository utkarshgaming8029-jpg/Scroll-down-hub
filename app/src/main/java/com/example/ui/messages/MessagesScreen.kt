package com.example.ui.messages

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.data.local.CallType
import com.example.data.local.MediaTypes
import com.example.data.local.PostAudience
import com.example.data.model.NoteUiModel
import com.example.data.repository.MediaStorageHelper
import com.example.data.repository.SecurityUtils
import com.example.ui.components.EmptyStateView
import com.example.ui.components.GoldenVerifiedBadge
import com.example.ui.components.ScrollDownVideoPlayer
import com.example.ui.components.UserAvatar
import com.example.ui.feed.ReportDialog
import com.example.ui.main.MainViewModel
import com.example.ui.theme.*
import kotlinx.coroutines.delay

@Composable
fun MessagesListScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
    onOpenChat: (conversationId: String, peerId: String) -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler { onBack() }

    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
    val conversations by viewModel.conversations.collectAsStateWithLifecycle()
    val allUsers by viewModel.allUsers.collectAsStateWithLifecycle()
    val activeNotes by viewModel.activeNotes.collectAsStateWithLifecycle()

    var showNewChatDialog by remember { mutableStateOf(false) }
    var showNoteDialog by remember { mutableStateOf(false) }

    val ownNote = remember(activeNotes, viewModel.currentUserId) {
        activeNotes.firstOrNull { it.author.id == viewModel.currentUserId }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianBlack)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = IvoryText)
            }
            Text(
                text = "Direct Messages",
                style = MaterialTheme.typography.headlineSmall,
                color = GoldBright,
                modifier = Modifier.weight(1f)
            )
            IconButton(
                onClick = { showNewChatDialog = true },
                modifier = Modifier.testTag("new_message_button")
            ) {
                Icon(Icons.Outlined.AddComment, contentDescription = "New message or group", tint = GoldBright)
            }
        }
        HorizontalDivider(color = ObsidianSubtleBorder)

        // 24h Status Notes Tray
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item(key = "own_note") {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .width(82.dp)
                        .clickable { showNoteDialog = true }
                        .testTag("leave_note_button")
                ) {
                    Surface(
                        color = GoldTintSurface,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .border(0.8.dp, GoldPrimary.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                    ) {
                        Text(
                            text = ownNote?.text ?: "Share note...",
                            style = MaterialTheme.typography.labelSmall,
                            color = GoldBright,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    UserAvatar(
                        avatarUri = currentUser?.avatarUri,
                        displayName = currentUser?.displayName ?: "You",
                        size = 54.dp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Your Note", style = MaterialTheme.typography.labelSmall, color = WarmGrayText, maxLines = 1)
                }
            }

            items(activeNotes.filter { it.author.id != viewModel.currentUserId }, key = { it.id }) { note ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .width(82.dp)
                        .clickable {
                            val convId = SecurityUtils.buildConversationId(viewModel.currentUserId, note.author.id)
                            onOpenChat(convId, note.author.id)
                        }
                ) {
                    Surface(
                        color = ObsidianCard,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.border(0.8.dp, ObsidianOutline, RoundedCornerShape(12.dp))
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                            Text(
                                text = note.text,
                                style = MaterialTheme.typography.labelSmall,
                                color = IvoryText,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (note.musicTitle.isNotBlank()) {
                                Text(
                                    text = "♪ ${note.musicTitle}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = GoldBright,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    UserAvatar(
                        avatarUri = note.author.avatarUri,
                        displayName = note.author.displayName,
                        size = 54.dp,
                        isOnline = note.author.isOnline
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("@${note.author.username}", style = MaterialTheme.typography.labelSmall, color = IvoryText, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        HorizontalDivider(color = ObsidianSubtleBorder)

        if (conversations.isEmpty()) {
            EmptyStateView(
                icon = Icons.Rounded.ChatBubbleOutline,
                title = "No Messages Yet",
                subtitle = "Start a 1:1 or group chat with photos, videos, voice notes, reactions, and live calling.",
                actionLabel = "Start Chat",
                onAction = { showNewChatDialog = true }
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(conversations, key = { it.conversationId }) { conv ->
                    Surface(
                        onClick = { onOpenChat(conv.conversationId, conv.peer?.id ?: "") },
                        color = ObsidianCard,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            if (conv.isGroup) {
                                Box(
                                    modifier = Modifier
                                        .size(50.dp)
                                        .clip(CircleShape)
                                        .background(GoldTintSurface)
                                        .border(1.dp, GoldPrimary, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Rounded.Groups, contentDescription = "Group chat", tint = GoldBright)
                                }
                            } else {
                                UserAvatar(
                                    avatarUri = conv.peer?.avatarUri,
                                    displayName = conv.displayTitle,
                                    size = 50.dp,
                                    isOnline = conv.peer?.isOnline == true
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(conv.displayTitle, style = MaterialTheme.typography.titleSmall, color = IvoryText, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    if (!conv.isGroup && conv.peer?.isGoldenVerified == true) GoldenVerifiedBadge(size = 14.dp)
                                    if (conv.isGroup) {
                                        Text("(${conv.participants.size})", style = MaterialTheme.typography.labelSmall, color = GoldBright)
                                    }
                                }
                                Text(
                                    text = if (conv.isPeerTyping) "Typing..." else conv.lastMessage,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (conv.isPeerTyping) GoldBright else WarmGrayText,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = SecurityUtils.formatRelativeTime(conv.lastMessageAt),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = DimGrayText
                                )
                                if (conv.unreadCount > 0) {
                                    Badge(containerColor = GoldPrimary, contentColor = ObsidianBlack) {
                                        Text("${conv.unreadCount}", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showNoteDialog) {
        LeaveNoteDialog(
            existingNote = ownNote,
            onDismiss = { showNoteDialog = false },
            onPublish = { text, music, audience ->
                viewModel.publishNote(text, music, audience) { showNoteDialog = false }
            },
            onDelete = {
                viewModel.deleteOwnNote()
                showNoteDialog = false
            }
        )
    }

    if (showNewChatDialog) {
        val candidates = allUsers.filter { it.id != viewModel.currentUserId && !it.isBanned && !it.isBlockedByMe }
        var isGroupMode by remember { mutableStateOf(false) }
        var groupName by remember { mutableStateOf("") }
        var selectedIds by remember { mutableStateOf<Set<String>>(emptySet()) }

        AlertDialog(
            onDismissRequest = { showNewChatDialog = false },
            containerColor = ObsidianSurface,
            title = { Text(if (isGroupMode) "Create Group Chat" else "Start New Chat", color = GoldBright) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = !isGroupMode, onClick = { isGroupMode = false }, label = { Text("1:1 Chat") })
                        FilterChip(selected = isGroupMode, onClick = { isGroupMode = true }, label = { Text("Group Chat") })
                    }
                    if (isGroupMode) {
                        OutlinedTextField(
                            value = groupName,
                            onValueChange = { groupName = it },
                            label = { Text("Group Name") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("group_name_input")
                        )
                    }
                    if (candidates.isEmpty()) {
                        Text("No other accounts available to message yet.", color = WarmGrayText)
                    } else {
                        LazyColumn(modifier = Modifier.heightIn(max = 280.dp)) {
                            items(candidates, key = { it.id }) { user ->
                                val isChecked = user.id in selectedIds
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            if (isGroupMode) {
                                                selectedIds = if (isChecked) selectedIds - user.id else selectedIds + user.id
                                            } else {
                                                showNewChatDialog = false
                                                val convId = SecurityUtils.buildConversationId(viewModel.currentUserId, user.id)
                                                onOpenChat(convId, user.id)
                                            }
                                        }
                                        .padding(vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    if (isGroupMode) {
                                        Checkbox(checked = isChecked, onCheckedChange = {
                                            selectedIds = if (it) selectedIds + user.id else selectedIds - user.id
                                        })
                                    }
                                    UserAvatar(avatarUri = user.avatarUri, displayName = user.displayName, size = 36.dp)
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Text(user.displayName, color = IvoryText, style = MaterialTheme.typography.titleSmall)
                                            if (user.isGoldenVerified) GoldenVerifiedBadge(size = 13.dp)
                                        }
                                        Text("@${user.username}", color = WarmGrayText, style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                if (isGroupMode) {
                    Button(
                        onClick = {
                            viewModel.createGroupConversation(groupName, selectedIds.toList()) { convId ->
                                showNewChatDialog = false
                                onOpenChat(convId, "")
                            }
                        },
                        enabled = selectedIds.isNotEmpty() && groupName.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary, contentColor = ObsidianBlack),
                        modifier = Modifier.testTag("create_group_confirm")
                    ) { Text("Create Group", fontWeight = FontWeight.Bold) }
                } else {
                    TextButton(onClick = { showNewChatDialog = false }) { Text("Close", color = WarmGrayText) }
                }
            },
            dismissButton = {
                if (isGroupMode) {
                    TextButton(onClick = { showNewChatDialog = false }) { Text("Cancel", color = WarmGrayText) }
                }
            }
        )
    }
}

@Composable
private fun LeaveNoteDialog(
    existingNote: NoteUiModel?,
    onDismiss: () -> Unit,
    onPublish: (String, String, String) -> Unit,
    onDelete: () -> Unit
) {
    var text by remember { mutableStateOf(existingNote?.text ?: "") }
    var music by remember { mutableStateOf(existingNote?.musicTitle ?: "") }
    var audience by remember { mutableStateOf(existingNote?.audience ?: PostAudience.ALL) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ObsidianSurface,
        title = { Text("Share 24h Note", color = GoldBright) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { if (it.length <= 60) text = it },
                    label = { Text("Thought (${text.length}/60)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("note_text_input")
                )
                OutlinedTextField(
                    value = music,
                    onValueChange = { music = it },
                    label = { Text("Music status (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = audience == PostAudience.ALL,
                        onClick = { audience = PostAudience.ALL },
                        label = { Text("Followers") }
                    )
                    FilterChip(
                        selected = audience == PostAudience.CLOSE_FRIENDS,
                        onClick = { audience = PostAudience.CLOSE_FRIENDS },
                        label = { Text("Close Friends") }
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onPublish(text, music, audience) },
                enabled = text.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary, contentColor = ObsidianBlack),
                modifier = Modifier.testTag("publish_note_button")
            ) { Text("Share Note", fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            Row {
                if (existingNote != null) {
                    TextButton(onClick = onDelete) { Text("Remove", color = CrimsonHeart) }
                }
                TextButton(onClick = onDismiss) { Text("Cancel", color = WarmGrayText) }
            }
        }
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChatScreen(
    conversationId: String,
    peerId: String,
    viewModel: MainViewModel,
    onBack: () -> Unit,
    onOpenProfile: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler { onBack() }
    val context = LocalContext.current

    val allUsers by viewModel.allUsers.collectAsStateWithLifecycle()
    val conv by viewModel.observeConversation(conversationId).collectAsStateWithLifecycle(initialValue = null)
    val messages by viewModel.observeMessagesByConvId(conversationId).collectAsStateWithLifecycle(initialValue = emptyList())

    val resolvedPeer = remember(conv, allUsers, peerId) {
        conv?.peer ?: allUsers.firstOrNull { it.id == peerId }
    }
    val isGroup = conv?.isGroup == true
    val isPeerTyping = conv?.isPeerTyping == true

    var textInput by remember { mutableStateOf("") }
    var showMenu by remember { mutableStateOf(false) }
    var showReportDialog by remember { mutableStateOf(false) }
    var reactionTargetMsgId by remember { mutableStateOf<String?>(null) }
    var isRecordingVoice by remember { mutableStateOf(false) }
    var playingAudioMsgId by remember { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            viewModel.sendMessageToConversation(conversationId, resolvedPeer?.id, "", mediaUri = uri, isVideo = false)
        }
    }
    val videoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            viewModel.sendMessageToConversation(conversationId, resolvedPeer?.id, "", mediaUri = uri, isVideo = true)
        }
    }
    val audioPermLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            val res = viewModel.startVoiceRecording()
            if (res.isSuccess) isRecordingVoice = true
        }
    }

    LaunchedEffect(messages.size) {
        viewModel.markConversationReadByConvId(conversationId)
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    LaunchedEffect(textInput) {
        if (textInput.isNotBlank()) {
            viewModel.setTypingByConvId(conversationId, true)
            delay(3000L)
            viewModel.setTypingByConvId(conversationId, false)
        } else {
            viewModel.setTypingByConvId(conversationId, false)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            if (isRecordingVoice) viewModel.stopVoiceRecording()
            viewModel.stopVoicePlayback()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianBlack)
    ) {
        // Chat Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(ObsidianSurface)
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = IvoryText)
            }
            if (!isGroup && resolvedPeer != null) {
                UserAvatar(
                    avatarUri = resolvedPeer.avatarUri,
                    displayName = resolvedPeer.displayName,
                    size = 40.dp,
                    isOnline = resolvedPeer.isOnline,
                    onClick = { onOpenProfile(resolvedPeer.id) }
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onOpenProfile(resolvedPeer.id) }
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(resolvedPeer.displayName, style = MaterialTheme.typography.titleSmall, color = IvoryText)
                        if (resolvedPeer.isGoldenVerified) GoldenVerifiedBadge(size = 14.dp)
                    }
                    Text(
                        text = when {
                            isPeerTyping -> "Typing..."
                            resolvedPeer.isOnline -> "Online"
                            else -> "@${resolvedPeer.username}"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = when {
                            isPeerTyping -> GoldBright
                            resolvedPeer.isOnline -> EmeraldOnline
                            else -> WarmGrayText
                        }
                    )
                }
                IconButton(
                    onClick = { viewModel.startCall(resolvedPeer.id, CallType.VOICE) },
                    modifier = Modifier.testTag("start_voice_call_button")
                ) {
                    Icon(Icons.Rounded.Call, contentDescription = "Voice Call", tint = GoldBright)
                }
                IconButton(
                    onClick = { viewModel.startCall(resolvedPeer.id, CallType.VIDEO) },
                    modifier = Modifier.testTag("start_video_call_button")
                ) {
                    Icon(Icons.Rounded.Videocam, contentDescription = "Video Call", tint = GoldBright)
                }
            } else {
                Column(modifier = Modifier.weight(1f).padding(start = 6.dp)) {
                    Text(conv?.displayTitle ?: "Group Chat", style = MaterialTheme.typography.titleSmall, color = IvoryText)
                    Text(
                        text = if (isPeerTyping) "Someone is typing..." else "${conv?.participants?.size ?: 0} members",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isPeerTyping) GoldBright else WarmGrayText
                    )
                }
            }

            if (!isGroup && resolvedPeer != null) {
                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Outlined.MoreVert, contentDescription = "Chat options", tint = IvoryText)
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                        containerColor = ObsidianCard
                    ) {
                        DropdownMenuItem(
                            text = { Text(if (resolvedPeer.isBlockedByMe) "Unblock User" else "Block User", color = CrimsonHeart) },
                            onClick = {
                                showMenu = false
                                viewModel.toggleBlock(resolvedPeer.id)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Report User", color = AmberWarning) },
                            onClick = {
                                showMenu = false
                                showReportDialog = true
                            }
                        )
                    }
                }
            }
        }
        HorizontalDivider(color = ObsidianSubtleBorder)

        // Messages
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(messages, key = { it.id }) { msg ->
                val isMine = msg.senderId == viewModel.currentUserId
                val senderProfile = if (!isMine && isGroup) allUsers.firstOrNull { it.id == msg.senderId } else null

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = if (isMine) Alignment.End else Alignment.Start
                ) {
                    if (senderProfile != null) {
                        Text(
                            text = "@${senderProfile.username}",
                            style = MaterialTheme.typography.labelSmall,
                            color = GoldBright,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
                    Surface(
                        color = if (isMine) GoldTintSurface else ObsidianCard,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .widthIn(max = 280.dp)
                            .border(
                                width = 0.8.dp,
                                color = if (isMine) GoldPrimary.copy(alpha = 0.5f) else ObsidianSubtleBorder,
                                shape = RoundedCornerShape(16.dp)
                            )
                            .combinedClickable(
                                onClick = { reactionTargetMsgId = if (reactionTargetMsgId == msg.id) null else msg.id },
                                onLongClick = { reactionTargetMsgId = msg.id }
                            )
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (msg.mediaUri != null) {
                                when (msg.mediaType) {
                                    MediaTypes.VIDEO -> {
                                        ScrollDownVideoPlayer(
                                            videoUri = msg.mediaUri,
                                            isActive = false,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(180.dp)
                                                .clip(RoundedCornerShape(10.dp))
                                        )
                                    }
                                    MediaTypes.AUDIO -> {
                                        val isPlayingThis = playingAudioMsgId == msg.id
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            modifier = Modifier
                                                .clickable {
                                                    val nowPlaying = viewModel.toggleVoicePlayback(msg.mediaUri) {
                                                        playingAudioMsgId = null
                                                    }
                                                    playingAudioMsgId = if (nowPlaying) msg.id else null
                                                }
                                                .padding(vertical = 4.dp)
                                        ) {
                                            Icon(
                                                imageVector = if (isPlayingThis) Icons.Rounded.PauseCircle else Icons.Rounded.PlayCircle,
                                                contentDescription = "Play voice message",
                                                tint = GoldBright,
                                                modifier = Modifier.size(30.dp)
                                            )
                                            Text(
                                                text = if (isPlayingThis) "Playing voice note..." else (msg.text.ifBlank { "Voice Note" }),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = IvoryText
                                            )
                                        }
                                    }
                                    else -> {
                                        val msgModel = remember(msg.mediaUri) { MediaStorageHelper.resolveCoilModel(msg.mediaUri) }
                                        AsyncImage(
                                            model = msgModel,
                                            contentDescription = "Chat image",
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .heightIn(max = 220.dp)
                                                .clip(RoundedCornerShape(10.dp)),
                                            contentScale = ContentScale.Crop
                                        )
                                    }
                                }
                            }
                            if (msg.text.isNotBlank() && msg.mediaType != MediaTypes.AUDIO) {
                                Text(msg.text, style = MaterialTheme.typography.bodyMedium, color = IvoryText)
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = SecurityUtils.formatRelativeTime(msg.createdAt),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = DimGrayText
                                )
                                if (isMine) {
                                    Icon(
                                        Icons.Rounded.DoneAll,
                                        contentDescription = if (msg.isSeen) "Seen" else "Sent",
                                        tint = if (msg.isSeen) GoldBright else DimGrayText,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Existing message emoji reactions
                    if (msg.reactions.isNotEmpty()) {
                        val grouped = msg.reactions.values.groupingBy { it }.eachCount()
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(top = 3.dp)
                        ) {
                            grouped.forEach { (emoji, count) ->
                                Surface(
                                    color = ObsidianSurface,
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(0.6.dp, GoldPrimary.copy(alpha = 0.5f))
                                ) {
                                    Text(
                                        text = if (count > 1) "$emoji $count" else emoji,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = IvoryText,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Quick Reaction Picker
                    if (reactionTargetMsgId == msg.id) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(top = 4.dp)
                        ) {
                            listOf("❤️", "🔥", "😂", "👏", "✨").forEach { emoji ->
                                Surface(
                                    onClick = {
                                        viewModel.toggleMessageReaction(conversationId, msg.id, emoji)
                                        reactionTargetMsgId = null
                                    },
                                    color = ObsidianCard,
                                    shape = CircleShape
                                ) {
                                    Text(emoji, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                                }
                            }
                        }
                    }
                }
            }
        }

        // Recording indicator banner
        if (isRecordingVoice) {
            Surface(
                color = CrimsonHeart.copy(alpha = 0.18f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Recording voice note...", color = CrimsonHeart, style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = {
                            viewModel.stopVoiceRecording()
                            isRecordingVoice = false
                        }) { Text("Discard", color = WarmGrayText) }
                        Button(
                            onClick = {
                                val recorded = viewModel.stopVoiceRecording()
                                isRecordingVoice = false
                                if (recorded != null) {
                                    val (file, sec) = recorded
                                    viewModel.sendMessageToConversation(
                                        conversationId = conversationId,
                                        directPeerId = resolvedPeer?.id,
                                        text = "Voice note (${sec}s)",
                                        voiceFile = file
                                    )
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary, contentColor = ObsidianBlack)
                        ) { Text("Send Voice", fontWeight = FontWeight.Bold) }
                    }
                }
            }
        }

        // Input Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(ObsidianSurface)
                .padding(horizontal = 6.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            IconButton(onClick = { photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                Icon(Icons.Outlined.Image, contentDescription = "Send photo", tint = GoldBright)
            }
            IconButton(onClick = { videoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)) }) {
                Icon(Icons.Outlined.Videocam, contentDescription = "Send video", tint = GoldBright)
            }
            IconButton(
                onClick = {
                    if (isRecordingVoice) {
                        val recorded = viewModel.stopVoiceRecording()
                        isRecordingVoice = false
                        if (recorded != null) {
                            val (file, sec) = recorded
                            viewModel.sendMessageToConversation(
                                conversationId = conversationId,
                                directPeerId = resolvedPeer?.id,
                                text = "Voice note (${sec}s)",
                                voiceFile = file
                            )
                        }
                    } else {
                        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                        if (granted) {
                            val res = viewModel.startVoiceRecording()
                            if (res.isSuccess) isRecordingVoice = true
                        } else {
                            audioPermLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    }
                },
                modifier = Modifier.testTag("chat_voice_record_button")
            ) {
                Icon(
                    imageVector = if (isRecordingVoice) Icons.Rounded.StopCircle else Icons.Rounded.Mic,
                    contentDescription = "Record voice note",
                    tint = if (isRecordingVoice) CrimsonHeart else GoldBright
                )
            }
            OutlinedTextField(
                value = textInput,
                onValueChange = { textInput = it },
                placeholder = { Text("Message...") },
                singleLine = true,
                modifier = Modifier.weight(1f).testTag("chat_message_input")
            )
            IconButton(
                onClick = {
                    if (textInput.isNotBlank()) {
                        viewModel.sendMessageToConversation(conversationId, resolvedPeer?.id, textInput)
                        textInput = ""
                    }
                },
                modifier = Modifier.testTag("chat_send_button")
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send message", tint = GoldPrimary)
            }
        }
    }

    if (showReportDialog && resolvedPeer != null) {
        ReportDialog(
            title = "Report @${resolvedPeer.username}",
            onDismiss = { showReportDialog = false },
            onSubmit = { reason ->
                viewModel.reportContent("USER", resolvedPeer.id, reason)
                showReportDialog = false
            }
        )
    }
}
