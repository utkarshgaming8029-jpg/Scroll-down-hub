package com.example.ui.stories

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.data.local.MediaTypes
import com.example.data.repository.MediaStorageHelper
import com.example.data.repository.SecurityUtils
import com.example.ui.components.GoldenVerifiedBadge
import com.example.ui.components.ScrollDownVideoPlayer
import com.example.ui.components.UserAvatar
import com.example.ui.main.MainViewModel
import com.example.ui.theme.*
import kotlinx.coroutines.delay

@Composable
fun StoryViewerScreen(
    authorId: String,
    viewModel: MainViewModel,
    onClose: () -> Unit,
    onOpenProfile: (String) -> Unit
) {
    BackHandler { onClose() }

    val storyGroups by viewModel.storyGroups.collectAsStateWithLifecycle()
    val deletingStoryId by viewModel.deletingStoryId.collectAsStateWithLifecycle()
    val nowTick by produceState(initialValue = System.currentTimeMillis()) {
        while (true) {
            delay(15_000L)
            value = System.currentTimeMillis()
        }
    }
    val group = storyGroups.firstOrNull { it.author.id == authorId }
    val stories = remember(group, nowTick) {
        group?.stories?.filter { !it.isArchived && it.expiresAt > nowTick }.orEmpty()
    }

    var currentIndex by remember(authorId) { mutableIntStateOf(0) }
    var replyText by remember { mutableStateOf("") }
    var showViewersSheet by remember { mutableStateOf(false) }
    var showHighlightDialog by remember { mutableStateOf(false) }
    var showOptionsMenu by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var deleteErrorMessage by remember { mutableStateOf<String?>(null) }

    if (stories.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(ObsidianBlack),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("This story is no longer active.", color = WarmGrayText)
                Button(
                    onClick = onClose,
                    colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary, contentColor = ObsidianBlack)
                ) { Text("Back") }
            }
        }
        return
    }

    val safeIndex = currentIndex.coerceIn(0, stories.lastIndex)
    val activeStory = stories[safeIndex]
    val isOwner = activeStory.author.id == viewModel.currentUserId
    val isDeletingCurrent = deletingStoryId == activeStory.id
    val viewers by viewModel.observeStoryViewers(activeStory.id).collectAsStateWithLifecycle(initialValue = emptyList())
    val storyModel = remember(activeStory.mediaUri) { MediaStorageHelper.resolveCoilModel(activeStory.mediaUri) }

    LaunchedEffect(activeStory.id) {
        viewModel.recordStoryView(activeStory.id)
    }

    LaunchedEffect(
        activeStory.id,
        showViewersSheet,
        showHighlightDialog,
        showOptionsMenu,
        showDeleteConfirmDialog,
        deletingStoryId
    ) {
        if (!showViewersSheet &&
            !showHighlightDialog &&
            !showOptionsMenu &&
            !showDeleteConfirmDialog &&
            deletingStoryId == null &&
            activeStory.mediaType == MediaTypes.PHOTO
        ) {
            delay(5000L)
            if (safeIndex < stories.lastIndex) {
                currentIndex = safeIndex + 1
            } else {
                onClose()
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBlack)
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        // Media Content
        if (activeStory.mediaType == MediaTypes.VIDEO) {
            ScrollDownVideoPlayer(
                videoUri = activeStory.mediaUri,
                isActive = true,
                fillScreen = true,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            AsyncImage(
                model = storyModel,
                contentDescription = "Story photo",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }

        // Tap zones for Prev / Next
        Row(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(0.75f)
                    .clickable {
                        if (safeIndex > 0) currentIndex = safeIndex - 1
                    }
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(0.75f)
                    .clickable {
                        if (safeIndex < stories.lastIndex) currentIndex = safeIndex + 1 else onClose()
                    }
            )
        }

        // Top Bar + Progress
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .background(ObsidianBlack.copy(alpha = 0.45f))
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                stories.forEachIndexed { idx, _ ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(3.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(if (idx <= safeIndex) GoldBright else IvoryText.copy(alpha = 0.3f))
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                UserAvatar(
                    avatarUri = activeStory.author.avatarUri,
                    displayName = activeStory.author.displayName,
                    size = 36.dp,
                    onClick = { onOpenProfile(activeStory.author.id) }
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "@${activeStory.author.username}",
                            style = MaterialTheme.typography.titleSmall,
                            color = IvoryText
                        )
                        if (activeStory.author.isGoldenVerified) {
                            GoldenVerifiedBadge(size = 14.dp)
                        }
                    }
                    Text(
                        text = SecurityUtils.formatRelativeTime(activeStory.createdAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = WarmGrayText
                    )
                }
                if (isOwner) {
                    IconButton(
                        onClick = { showHighlightDialog = true },
                        enabled = !isDeletingCurrent
                    ) {
                        Icon(Icons.Outlined.AutoAwesome, contentDescription = "Save to Highlight", tint = GoldBright)
                    }
                    Box {
                        IconButton(
                            onClick = { showOptionsMenu = true },
                            enabled = !isDeletingCurrent,
                            modifier = Modifier.testTag("story_options_menu_button")
                        ) {
                            Icon(Icons.Outlined.MoreVert, contentDescription = "Story options", tint = IvoryText)
                        }
                        DropdownMenu(
                            expanded = showOptionsMenu,
                            onDismissRequest = { showOptionsMenu = false },
                            containerColor = ObsidianCard
                        ) {
                            DropdownMenuItem(
                                text = { Text("Save to Highlight", color = IvoryText) },
                                leadingIcon = { Icon(Icons.Outlined.AutoAwesome, contentDescription = null, tint = GoldBright) },
                                onClick = {
                                    showOptionsMenu = false
                                    showHighlightDialog = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Archive Story", color = IvoryText) },
                                leadingIcon = { Icon(Icons.Outlined.Archive, contentDescription = null, tint = GoldBright) },
                                onClick = {
                                    showOptionsMenu = false
                                    viewModel.setStoryArchived(activeStory.id, true)
                                    if (stories.size <= 1) onClose() else currentIndex = (safeIndex - 1).coerceAtLeast(0)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Delete Story", color = CrimsonHeart, fontWeight = FontWeight.Bold) },
                                leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null, tint = CrimsonHeart) },
                                onClick = {
                                    showOptionsMenu = false
                                    deleteErrorMessage = null
                                    showDeleteConfirmDialog = true
                                },
                                modifier = Modifier.testTag("story_delete_option")
                            )
                        }
                    }
                }
                IconButton(onClick = onClose, enabled = !isDeletingCurrent) {
                    Icon(Icons.Filled.Close, contentDescription = "Close story", tint = IvoryText)
                }
            }

            deleteErrorMessage?.let { err ->
                Surface(
                    color = CrimsonHeart.copy(alpha = 0.22f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(err, color = IvoryText, style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
                        TextButton(onClick = { deleteErrorMessage = null }) {
                            Text("Dismiss", color = GoldBright)
                        }
                    }
                }
            }
        }

        // Bottom Controls
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(ObsidianBlack.copy(alpha = 0.65f))
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (activeStory.caption.isNotBlank()) {
                Text(
                    text = activeStory.caption,
                    style = MaterialTheme.typography.bodyMedium,
                    color = IvoryText
                )
            }

            if (isOwner) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showViewersSheet = true }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Outlined.Visibility, contentDescription = null, tint = GoldBright)
                    Text(
                        text = "${activeStory.viewsCount} viewers • Tap to inspect reactions",
                        style = MaterialTheme.typography.labelLarge,
                        color = GoldBright
                    )
                }
            } else {
                // Quick Emoji Reactions
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    listOf("🔥", "👑", "✨", "❤️", "👏").forEach { emoji ->
                        Surface(
                            onClick = { viewModel.recordStoryView(activeStory.id, emoji) },
                            color = if (activeStory.myReaction == emoji) GoldTintSurface else ObsidianCard,
                            shape = RoundedCornerShape(20.dp)
                        ) {
                            Text(
                                text = emoji,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }
                }

                // Story Reply
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = replyText,
                        onValueChange = { replyText = it },
                        placeholder = { Text("Reply to @${activeStory.author.username}...") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = {
                            if (replyText.isNotBlank()) {
                                viewModel.replyToStory(activeStory.author.id, activeStory.caption, replyText)
                                replyText = ""
                            }
                        }
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send story reply", tint = GoldBright)
                    }
                }
            }
        }

        if (isDeletingCurrent) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(ObsidianBlack.copy(alpha = 0.7f)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    CircularProgressIndicator(color = GoldBright)
                    Text("Deleting story...", color = IvoryText, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }

    if (showDeleteConfirmDialog && isOwner) {
        AlertDialog(
            onDismissRequest = { if (!isDeletingCurrent) showDeleteConfirmDialog = false },
            containerColor = ObsidianSurface,
            title = { Text("Delete Story?", color = GoldBright, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "This story will be permanently deleted from your profile and feed for everyone.",
                    color = WarmGrayText,
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    enabled = !isDeletingCurrent,
                    onClick = {
                        val remaining = stories.size - 1
                        val targetId = activeStory.id
                        viewModel.deleteStory(
                            storyId = targetId,
                            onSuccess = {
                                showDeleteConfirmDialog = false
                                if (remaining <= 0) {
                                    onClose()
                                } else if (safeIndex >= remaining) {
                                    currentIndex = (remaining - 1).coerceAtLeast(0)
                                }
                            },
                            onError = { msg ->
                                showDeleteConfirmDialog = false
                                deleteErrorMessage = msg
                            }
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CrimsonHeart, contentColor = IvoryText),
                    modifier = Modifier.testTag("confirm_delete_story_button")
                ) {
                    if (isDeletingCurrent) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = IvoryText, strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Deleting...")
                    } else {
                        Text("Delete Story", fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !isDeletingCurrent,
                    onClick = { showDeleteConfirmDialog = false }
                ) {
                    Text("Cancel", color = GoldBright)
                }
            }
        )
    }

    if (showViewersSheet) {
        AlertDialog(
            onDismissRequest = { showViewersSheet = false },
            containerColor = ObsidianSurface,
            title = { Text("Story Viewers (${activeStory.viewsCount})", color = GoldBright) },
            text = {
                if (viewers.isEmpty()) {
                    Text("No views yet.", color = WarmGrayText)
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                        items(viewers, key = { it.user.id }) { viewer ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                UserAvatar(
                                    avatarUri = viewer.user.avatarUri,
                                    displayName = viewer.user.displayName,
                                    size = 34.dp
                                )
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = "@${viewer.user.username}",
                                        color = IvoryText
                                    )
                                    if (viewer.user.isGoldenVerified) {
                                        GoldenVerifiedBadge(size = 13.dp)
                                    }
                                }
                                viewer.reactionEmoji?.let {
                                    Text(it)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showViewersSheet = false }) {
                    Text("Close", color = GoldBright)
                }
            }
        )
    }

    if (showHighlightDialog) {
        var title by remember { mutableStateOf(activeStory.highlightTitle ?: "Highlights") }
        AlertDialog(
            onDismissRequest = { showHighlightDialog = false },
            containerColor = ObsidianSurface,
            title = { Text("Save to Story Highlight", color = GoldBright) },
            text = {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Highlight Title") },
                    singleLine = true
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.addStoryToHighlight(activeStory.id, title)
                        showHighlightDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary, contentColor = ObsidianBlack)
                ) { Text("Save", fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showHighlightDialog = false }) { Text("Cancel", color = WarmGrayText) }
            }
        )
    }
}
