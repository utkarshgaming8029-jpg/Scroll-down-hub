package com.example.ui.profile

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.data.local.MediaTypes
import com.example.data.model.UserProfile
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
fun YourActivityScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    BackHandler { onBack() }
    val strings = LocalAppStrings.current
    val allPosts by viewModel.allPosts.collectAsStateWithLifecycle()
    val likedIds by viewModel.likedPostIds.collectAsStateWithLifecycle()
    val savedIds by viewModel.savedPostIds.collectAsStateWithLifecycle()
    val loginActivity by viewModel.loginActivity.collectAsStateWithLifecycle()

    val likedPosts = remember(allPosts, likedIds) { allPosts.filter { it.id in likedIds } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        TopBackHeader(title = strings.yourActivity, onBack = onBack)

        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ActivitySummaryCard("Liked Posts", likedPosts.size.toString(), Modifier.weight(1f))
                    ActivitySummaryCard("Saved Posts", savedIds.size.toString(), Modifier.weight(1f))
                    ActivitySummaryCard("Sign-Ins", loginActivity.size.toString(), Modifier.weight(1f))
                }
            }

            item {
                Text(
                    text = "Recently Liked Posts",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            if (likedPosts.isEmpty()) {
                item {
                    EmptyStateView(
                        icon = Icons.Outlined.FavoriteBorder,
                        title = "No Liked Posts Yet",
                        subtitle = "Posts and Reels you like appear here."
                    )
                }
            } else {
                items(likedPosts, key = { it.id }) { post ->
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            UserAvatar(avatarUri = post.author.avatarUri, displayName = post.author.displayName, size = 40.dp)
                            Column(modifier = Modifier.weight(1f)) {
                                Text("@${post.author.username}", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                                Text(post.caption.ifBlank { post.mediaType }, maxLines = 1, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Icon(Icons.Rounded.Favorite, contentDescription = null, tint = CrimsonHeart)
                        }
                    }
                }
            }

            item {
                Text(
                    text = "Recent Login Activity",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            items(loginActivity.take(5), key = { it.id }) { act ->
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.Outlined.Devices, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(act.deviceModel, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                            Text(
                                "${act.authMethod} • ${DateFormat.getDateTimeInstance().format(Date(act.createdAt))}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ArchiveScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    BackHandler { onBack() }
    val strings = LocalAppStrings.current
    val allPostsWithArchive by viewModel.allPostsWithArchive.collectAsStateWithLifecycle()
    val myStories by viewModel.myStoriesArchive.collectAsStateWithLifecycle()

    val archivedPosts = remember(allPostsWithArchive) {
        allPostsWithArchive.filter { it.author.id == viewModel.currentUserId && it.isArchived }
    }
    val archivedStories = remember(myStories) {
        val now = System.currentTimeMillis()
        myStories.filter { it.isArchived || it.expiresAt <= now }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        TopBackHeader(title = strings.archive, onBack = onBack)

        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text("Archived Posts", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            }
            if (archivedPosts.isEmpty()) {
                item {
                    EmptyStateView(
                        icon = Icons.Outlined.Inventory2,
                        title = "No Archived Posts",
                        subtitle = "Archive posts from your feed or profile to hide them privately without deleting."
                    )
                }
            } else {
                items(archivedPosts, key = { it.id }) { post ->
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.surface)
                            ) {
                                if (post.mediaType == MediaTypes.PHOTO || post.mediaType == MediaTypes.CAROUSEL) {
                                    AsyncImage(
                                        model = MediaStorageHelper.resolveCoilModel(post.mediaUris.firstOrNull()),
                                        contentDescription = post.caption,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Icon(Icons.Rounded.PlayCircle, contentDescription = null, tint = GoldPrimary, modifier = Modifier.align(Alignment.Center))
                                }
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(post.caption.ifBlank { "Archived ${post.mediaType}" }, maxLines = 2, style = MaterialTheme.typography.titleSmall)
                                Text(SecurityUtils.formatRelativeTime(post.createdAt), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            OutlinedButton(
                                onClick = { viewModel.setPostArchived(post.id, false) },
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                            ) {
                                Text("Restore", color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(6.dp))
                Text("Stories Archive", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            }
            if (archivedStories.isEmpty()) {
                item {
                    Text(
                        "Expired or archived 24-hour stories appear here.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                items(archivedStories, key = { it.id }) { story ->
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            AsyncImage(
                                model = MediaStorageHelper.resolveCoilModel(story.mediaUri),
                                contentDescription = story.caption,
                                modifier = Modifier.size(52.dp).clip(RoundedCornerShape(10.dp))
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(story.caption.ifBlank { "Story" }, style = MaterialTheme.typography.titleSmall)
                                Text(SecurityUtils.formatRelativeTime(story.createdAt), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (story.isArchived) {
                                OutlinedButton(onClick = { viewModel.setStoryArchived(story.id, false) }) {
                                    Text("Restore", color = MaterialTheme.colorScheme.primary)
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
fun QrCodeDialog(
    profile: UserProfile,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val profileUrl = "https://scrolldown.app/@${profile.username}"
    var copied by remember { mutableStateOf(false) }

    // Deterministic 21x21 QR-style matrix generated from profile username
    val matrix = remember(profile.username) { buildDeterministicQrMatrix(profileUrl) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Profile QR Code", color = MaterialTheme.colorScheme.primary)
                if (profile.isGoldenVerified) GoldenVerifiedBadge(size = 16.dp)
            }
        },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Surface(
                    color = Color.White,
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier
                        .size(210.dp)
                        .border(2.dp, GoldPrimary, RoundedCornerShape(18.dp))
                        .padding(14.dp)
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val n = matrix.size
                        val cell = size.minDimension / n
                        for (r in 0 until n) {
                            for (c in 0 until n) {
                                if (matrix[r][c]) {
                                    drawRect(
                                        color = Color(0xFF0A0A0D),
                                        topLeft = Offset(c * cell, r * cell),
                                        size = Size(cell + 0.5f, cell + 0.5f)
                                    )
                                }
                            }
                        }
                    }
                }
                Text(
                    "@${profile.username}",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    profileUrl,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = {
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cm.setPrimaryClip(ClipData.newPlainText("Profile Link", profileUrl))
                            copied = true
                        },
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(if (copied) "Copied ✓" else "Copy Link", color = MaterialTheme.colorScheme.primary)
                    }
                    Button(
                        onClick = {
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, "Follow @${profile.username} on SCROLL DOWN: $profileUrl")
                            }
                            context.startActivity(Intent.createChooser(intent, "Share SCROLL DOWN Profile"))
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary, contentColor = ObsidianBlack),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Share", fontWeight = FontWeight.Bold)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done", color = MaterialTheme.colorScheme.primary) }
        }
    )
}

@Composable
fun TopBackHeader(title: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onBackground)
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun ActivitySummaryCard(label: String, count: String, modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(14.dp),
        modifier = modifier.border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
    ) {
        Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(count, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun buildDeterministicQrMatrix(seedText: String): Array<BooleanArray> {
    val n = 21
    val grid = Array(n) { BooleanArray(n) }
    fun drawFinder(row: Int, col: Int) {
        for (r in 0 until 7) {
            for (c in 0 until 7) {
                val border = r == 0 || r == 6 || c == 0 || c == 6
                val center = r in 2..4 && c in 2..4
                grid[row + r][col + c] = border || center
            }
        }
    }
    drawFinder(0, 0)
    drawFinder(0, n - 7)
    drawFinder(n - 7, 0)
    var hash = seedText.hashCode()
    for (r in 0 until n) {
        for (c in 0 until n) {
            val inFinder = (r < 8 && c < 8) || (r < 8 && c >= n - 8) || (r >= n - 8 && c < 8)
            if (!inFinder) {
                hash = hash * 1103515245 + 12345
                grid[r][c] = ((hash ushr 16) and 1) == 1
            }
        }
    }
    return grid
}
