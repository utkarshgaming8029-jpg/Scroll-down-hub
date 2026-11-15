package com.example.ui.profile

import java.util.Locale
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.data.local.MediaTypes
import com.example.data.local.VerificationStatus
import com.example.data.repository.MediaStorageHelper
import com.example.data.repository.SecurityUtils
import com.example.ui.components.EmptyStateView
import com.example.ui.components.GoldenVerifiedBadge
import com.example.ui.components.UserAvatar
import com.example.ui.main.MainViewModel
import com.example.ui.theme.*
import java.text.DateFormat
import java.util.Date

@Composable
fun CreatorDashboardScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
    onOpenLiveStream: () -> Unit,
    onOpenReels: () -> Unit
) {
    BackHandler { onBack() }
    val me by viewModel.currentUser.collectAsStateWithLifecycle()
    val analytics by viewModel.creatorAnalytics.collectAsStateWithLifecycle()
    val achievements by viewModel.creatorAchievements.collectAsStateWithLifecycle()
    val verRequests by viewModel.myVerificationRequests.collectAsStateWithLifecycle()
    var showVerificationDialog by remember { mutableStateOf(false) }

    val latestRequest = verRequests.firstOrNull()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        TopBackHeader(title = "Creator Studio", onBack = onBack)

        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CreatorMetricTile("Posts", analytics.totalPosts.toString(), Modifier.weight(1f))
                    CreatorMetricTile("Reels", analytics.totalReels.toString(), Modifier.weight(1f))
                    CreatorMetricTile("Likes", SecurityUtils.formatCompactCount(analytics.totalLikesReceived), Modifier.weight(1f))
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CreatorMetricTile("Comments", SecurityUtils.formatCompactCount(analytics.totalCommentsReceived), Modifier.weight(1f))
                    CreatorMetricTile("Views", SecurityUtils.formatCompactCount(analytics.totalVideoViews), Modifier.weight(1f))
                    CreatorMetricTile("Engagement", String.format(Locale.US, "%.1f%%", analytics.engagementRatePercent), Modifier.weight(1f))
                }
            }

            item {
                Surface(
                    color = GoldTintSurface,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, GoldPrimary.copy(alpha = 0.55f), RoundedCornerShape(16.dp))
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            GoldenVerifiedBadge(size = 20.dp)
                            Text(
                                text = when {
                                    me?.isGoldenVerified == true -> "Golden Verification Active"
                                    latestRequest?.status == VerificationStatus.PENDING -> "Verification Under Super Admin Review"
                                    else -> "Apply for Golden Verification"
                                },
                                style = MaterialTheme.typography.titleSmall,
                                color = GoldBright,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = when {
                                me?.isGoldenVerified == true -> "Your official Golden Tick appears across posts, comments, Reels, and Live Streams."
                                latestRequest?.status == VerificationStatus.PENDING -> "Category: ${latestRequest.category} • Submitted ${SecurityUtils.formatRelativeTime(latestRequest.createdAt)}"
                                latestRequest?.status == VerificationStatus.REJECTED -> "Previous request declined${latestRequest.adminNote?.let { ": $it" } ?: ""}. You may apply again."
                                else -> "Eligible creators can submit their portfolio for official review by Super Admin @utkarshrai."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = WarmGrayText
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (me?.isGoldenVerified != true && latestRequest?.status != VerificationStatus.PENDING) {
                                Button(
                                    onClick = { showVerificationDialog = true },
                                    colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary, contentColor = ObsidianBlack),
                                    modifier = Modifier.weight(1f).testTag("apply_verification_button")
                                ) {
                                    Text("Request Gold Tick", fontWeight = FontWeight.Bold)
                                }
                            }
                            OutlinedButton(
                                onClick = onOpenLiveStream,
                                border = BorderStroke(1.dp, GoldPrimary),
                                modifier = Modifier.weight(1f).testTag("creator_go_live_button")
                            ) {
                                Icon(Icons.Rounded.LiveTv, contentDescription = null, tint = GoldBright, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Go Live", color = GoldBright, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            item {
                Text(
                    "Creator Achievements",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            items(achievements, key = { it.id }) { ach ->
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            0.8.dp,
                            if (ach.isUnlocked) GoldPrimary.copy(alpha = 0.65f) else MaterialTheme.colorScheme.outlineVariant,
                            RoundedCornerShape(14.dp)
                        )
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = if (ach.isUnlocked) Icons.Rounded.EmojiEvents else Icons.Outlined.EmojiEvents,
                            contentDescription = null,
                            tint = if (ach.isUnlocked) GoldBright else DimGrayText,
                            modifier = Modifier.size(28.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(ach.title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                            Text(ach.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Surface(
                            color = if (ach.isUnlocked) GoldPrimary else ObsidianBlack,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = ach.progressLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (ach.isUnlocked) ObsidianBlack else WarmGrayText,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }

            if (analytics.topPosts.isNotEmpty()) {
                item {
                    Text(
                        "Top Performing Content",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                items(analytics.topPosts, key = { it.id }) { post ->
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { if (post.isReel) onOpenReels() }
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(ObsidianBlack),
                                contentAlignment = Alignment.Center
                            ) {
                                val thumb = MediaStorageHelper.resolveCoilModel(post.mediaUris.firstOrNull())
                                if (thumb != null && post.mediaType != MediaTypes.VIDEO && post.mediaType != MediaTypes.REEL) {
                                    AsyncImage(model = thumb, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                } else {
                                    Icon(Icons.Rounded.PlayCircle, contentDescription = null, tint = GoldBright)
                                }
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(post.caption.ifBlank { post.mediaType }, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    "${post.likesCount} likes • ${post.commentsCount} comments • ${post.viewsCount} views",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = WarmGrayText
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showVerificationDialog) {
        VerificationRequestDialog(
            onDismiss = { showVerificationDialog = false },
            onSubmit = { category, reason, link ->
                viewModel.submitVerificationRequest(category, reason, link) {
                    showVerificationDialog = false
                }
            }
        )
    }
}

@Composable
fun CloseFriendsScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    BackHandler { onBack() }
    val allUsers by viewModel.allUsers.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    val candidates = remember(allUsers, query) {
        allUsers.filter {
            it.id != viewModel.currentUserId && !it.isBanned &&
                (query.isBlank() || it.username.contains(query, ignoreCase = true) || it.displayName.contains(query, ignoreCase = true))
        }.sortedByDescending { it.isCloseFriend }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        TopBackHeader(title = "Close Friends", onBack = onBack)
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Search users...") },
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null, tint = GoldBright) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)
        )
        if (candidates.isEmpty()) {
            EmptyStateView(
                icon = Icons.Rounded.StarOutline,
                title = "No Users Found",
                subtitle = "Add trusted members to your Close Friends circle for exclusive posts, stories, and notes."
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(candidates, key = { it.id }) { user ->
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            UserAvatar(avatarUri = user.avatarUri, displayName = user.displayName, size = 42.dp)
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(user.displayName, style = MaterialTheme.typography.titleSmall)
                                    if (user.isGoldenVerified) GoldenVerifiedBadge(size = 13.dp)
                                }
                                Text("@${user.username}", style = MaterialTheme.typography.labelSmall, color = WarmGrayText)
                            }
                            IconButton(
                                onClick = { viewModel.toggleCloseFriend(user.id) },
                                modifier = Modifier.testTag("close_friend_toggle_${user.username}")
                            ) {
                                Icon(
                                    imageVector = if (user.isCloseFriend) Icons.Rounded.Star else Icons.Rounded.StarOutline,
                                    contentDescription = if (user.isCloseFriend) "Remove Close Friend" else "Add Close Friend",
                                    tint = if (user.isCloseFriend) GoldBright else WarmGrayText
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MemoriesScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    BackHandler { onBack() }
    val memories by viewModel.memories.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        TopBackHeader(title = "Memories Timeline", onBack = onBack)
        if (memories.isEmpty()) {
            EmptyStateView(
                icon = Icons.Rounded.AutoStories,
                title = "No Memories Yet",
                subtitle = "Your published posts and Reels build your chronological SCROLL DOWN archive."
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(memories, key = { it.id }) { post ->
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(0.8.dp, GoldPrimary.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(post.createdAt)),
                                style = MaterialTheme.typography.labelMedium,
                                color = GoldBright
                            )
                            Text(post.caption.ifBlank { post.mediaType }, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                text = "${post.likesCount} likes • ${post.commentsCount} comments" + if (post.isArchived) " • Archived" else "",
                                style = MaterialTheme.typography.labelSmall,
                                color = WarmGrayText
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun WatchHistoryScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
    onOpenReels: () -> Unit
) {
    BackHandler { onBack() }
    val history by viewModel.watchHistory.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(end = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.weight(1f)) {
                TopBackHeader(title = "Watch History", onBack = onBack)
            }
            if (history.isNotEmpty()) {
                TextButton(onClick = { viewModel.clearWatchHistory() }) {
                    Text("Clear All", color = CrimsonHeart, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        if (history.isEmpty()) {
            EmptyStateView(
                icon = Icons.Rounded.History,
                title = "No Watch History",
                subtitle = "Reels and videos you watch appear here for easy replay."
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(history, key = { "${it.post.id}_${it.watchedAt}" }) { item ->
                    Surface(
                        onClick = onOpenReels,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(Icons.Rounded.PlayCircle, contentDescription = null, tint = GoldBright, modifier = Modifier.size(34.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("@${item.post.author.username}", style = MaterialTheme.typography.titleSmall, color = GoldBright)
                                Text(item.post.caption.ifBlank { item.post.mediaType }, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                            }
                            Text(SecurityUtils.formatRelativeTime(item.watchedAt), style = MaterialTheme.typography.labelSmall, color = WarmGrayText)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ScheduledPostsScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    BackHandler { onBack() }
    val scheduled by viewModel.scheduledPosts.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        TopBackHeader(title = "Scheduled Queue", onBack = onBack)
        if (scheduled.isEmpty()) {
            EmptyStateView(
                icon = Icons.Rounded.Schedule,
                title = "No Scheduled Posts",
                subtitle = "Use Create -> Publishing Schedule to line up upcoming posts or Reels."
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(scheduled, key = { it.id }) { post ->
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth().border(0.8.dp, GoldPrimary.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "Scheduled for ${DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(post.scheduledAtMs))}",
                                style = MaterialTheme.typography.labelMedium,
                                color = GoldBright
                            )
                            Text(post.caption.ifBlank { "Scheduled ${post.mediaType}" }, style = MaterialTheme.typography.bodyMedium)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = { viewModel.publishScheduledPostNow(post.id) },
                                    colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary, contentColor = ObsidianBlack),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Publish Now", fontWeight = FontWeight.Bold)
                                }
                                OutlinedButton(
                                    onClick = { viewModel.deletePost(post.id) },
                                    border = BorderStroke(1.dp, CrimsonHeart),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Cancel", color = CrimsonHeart)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun VerificationRequestDialog(
    onDismiss: () -> Unit,
    onSubmit: (String, String, String) -> Unit
) {
    val categories = listOf("CREATOR", "PUBLIC_FIGURE", "BRAND", "MEDIA")
    var category by remember { mutableStateOf(categories.first()) }
    var reason by remember { mutableStateOf("") }
    var link by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                GoldenVerifiedBadge(size = 18.dp)
                Text("Golden Verification", color = GoldBright)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Select category:", style = MaterialTheme.typography.labelMedium, color = WarmGrayText)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    categories.take(2).forEach { cat ->
                        FilterChip(
                            selected = category == cat,
                            onClick = { category = cat },
                            label = { Text(cat, style = MaterialTheme.typography.labelSmall) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    categories.drop(2).forEach { cat ->
                        FilterChip(
                            selected = category == cat,
                            onClick = { category = cat },
                            label = { Text(cat, style = MaterialTheme.typography.labelSmall) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("Why should this account be verified? (min 10 chars)") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth().testTag("verification_reason_input")
                )
                OutlinedTextField(
                    value = link,
                    onValueChange = { link = it },
                    label = { Text("Portfolio / Website URL (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSubmit(category, reason, link) },
                enabled = reason.trim().length >= 10,
                colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary, contentColor = ObsidianBlack),
                modifier = Modifier.testTag("submit_verification_button")
            ) { Text("Submit Request", fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = WarmGrayText) }
        }
    )
}

@Composable
private fun CreatorMetricTile(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(14.dp),
        modifier = modifier.border(0.8.dp, GoldPrimary.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
    ) {
        Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, style = MaterialTheme.typography.titleLarge, color = GoldBright, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.labelSmall, color = WarmGrayText)
        }
    }
}
