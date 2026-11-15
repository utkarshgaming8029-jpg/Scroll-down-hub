package com.example.ui.reels

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.ModeComment
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.PostUiModel
import com.example.data.repository.SecurityUtils
import com.example.ui.components.*
import com.example.ui.feed.CommentsBottomSheet
import com.example.ui.main.MainViewModel
import com.example.ui.theme.*

@Composable
fun ReelsScreen(
    viewModel: MainViewModel,
    onOpenUserProfile: (String) -> Unit,
    onOpenCreateReel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val reels by viewModel.reels.collectAsStateWithLifecycle()
    var commentsReel by remember { mutableStateOf<PostUiModel?>(null) }

    if (reels.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(ObsidianBlack),
            contentAlignment = Alignment.Center
        ) {
            EmptyStateView(
                icon = Icons.Rounded.VideoLibrary,
                title = "No Reels Published Yet",
                subtitle = "Swipe-ready vertical Reels with full-screen playback and soundtrack credits live here.",
                actionLabel = "Upload First Reel",
                onAction = onOpenCreateReel
            )
        }
        return
    }

    val pagerState = rememberPagerState(pageCount = { reels.size })
    val countedViews = remember { mutableStateMapOf<String, Boolean>() }

    LaunchedEffect(pagerState.currentPage, reels.size) {
        val current = reels.getOrNull(pagerState.currentPage)
        if (current != null && countedViews[current.id] != true) {
            countedViews[current.id] = true
            viewModel.incrementReelView(current.id)
            viewModel.recordWatchHistory(current.id, current.mediaType)
        }
    }

    VerticalPager(
        state = pagerState,
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianBlack)
            .testTag("reels_vertical_pager")
    ) { page ->
        val reel = reels[page]
        val isActive = page == pagerState.currentPage
        ReelPageItem(
            reel = reel,
            isActive = isActive,
            isOwnReel = reel.author.id == viewModel.currentUserId,
            onLike = { viewModel.toggleLike(reel.id) },
            onComment = { commentsReel = reel },
            onFollow = { viewModel.toggleFollow(reel.author.id) },
            onOpenProfile = { onOpenUserProfile(reel.author.id) }
        )
    }

    commentsReel?.let { reel ->
        CommentsBottomSheet(
            post = reel,
            viewModel = viewModel,
            onDismiss = { commentsReel = null },
            onUserClick = { uid ->
                commentsReel = null
                onOpenUserProfile(uid)
            }
        )
    }
}

@Composable
private fun ReelPageItem(
    reel: PostUiModel,
    isActive: Boolean,
    isOwnReel: Boolean,
    onLike: () -> Unit,
    onComment: () -> Unit,
    onFollow: () -> Unit,
    onOpenProfile: () -> Unit
) {
    val context = LocalContext.current

    Box(modifier = Modifier.fillMaxSize().background(ObsidianBlack)) {
        if (reel.mediaUris.isNotEmpty()) {
            ScrollDownVideoPlayer(
                videoUri = reel.mediaUris.first(),
                isActive = isActive,
                fillScreen = true,
                modifier = Modifier.fillMaxSize()
            )
        }

        // Bottom Scrim
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(260.dp)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, ObsidianBlack.copy(alpha = 0.88f))
                    )
                )
        )

        // Right Action Column
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 14.dp, bottom = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                IconButton(
                    onClick = onLike,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(ObsidianBlack.copy(alpha = 0.45f))
                ) {
                    Icon(
                        imageVector = if (reel.isLiked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = "Like reel",
                        tint = if (reel.isLiked) CrimsonHeart else IvoryText,
                        modifier = Modifier.size(28.dp)
                    )
                }
                Text(
                    text = SecurityUtils.formatCompactCount(reel.likesCount),
                    style = MaterialTheme.typography.labelSmall,
                    color = IvoryText
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                IconButton(
                    onClick = onComment,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(ObsidianBlack.copy(alpha = 0.45f))
                ) {
                    Icon(
                        imageVector = Icons.Outlined.ModeComment,
                        contentDescription = "Comment on reel",
                        tint = IvoryText,
                        modifier = Modifier.size(26.dp)
                    )
                }
                Text(
                    text = SecurityUtils.formatCompactCount(reel.commentsCount),
                    style = MaterialTheme.typography.labelSmall,
                    color = IvoryText
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                IconButton(
                    onClick = {
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, "Watch @${reel.author.username}'s Reel on SCROLL DOWN: ${reel.caption}")
                        }
                        context.startActivity(Intent.createChooser(intent, "Share Reel"))
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(ObsidianBlack.copy(alpha = 0.45f))
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.Send,
                        contentDescription = "Share reel",
                        tint = IvoryText,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Text("Share", style = MaterialTheme.typography.labelSmall, color = IvoryText)
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Outlined.Visibility,
                    contentDescription = "Views",
                    tint = GoldBright,
                    modifier = Modifier.size(22.dp)
                )
                Text(
                    text = SecurityUtils.formatCompactCount(reel.viewsCount),
                    style = MaterialTheme.typography.labelSmall,
                    color = GoldBright
                )
            }
        }

        // Bottom-Left Creator & Caption Info
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth(0.78f)
                .padding(start = 16.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                UserAvatar(
                    avatarUri = reel.author.avatarUri,
                    displayName = reel.author.displayName,
                    size = 40.dp,
                    onClick = onOpenProfile
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.clickable(onClick = onOpenProfile)
                ) {
                    Text(
                        text = "@${reel.author.username}",
                        style = MaterialTheme.typography.titleSmall,
                        color = IvoryText
                    )
                    if (reel.author.isGoldenVerified) {
                        GoldenVerifiedBadge(size = 15.dp)
                    }
                }
                if (!isOwnReel) {
                    OutlinedButton(
                        onClick = onFollow,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp),
                        shape = RoundedCornerShape(10.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, GoldPrimary),
                        modifier = Modifier.height(30.dp)
                    ) {
                        Text(
                            text = when {
                                reel.author.isFollowing -> "Following"
                                reel.author.isFollowRequested -> "Requested"
                                else -> "Follow"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = GoldBright,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            if (reel.caption.isNotBlank()) {
                Text(
                    text = reel.caption,
                    style = MaterialTheme.typography.bodyMedium,
                    color = IvoryText,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Surface(
                color = ObsidianBlack.copy(alpha = 0.55f),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.border(0.8.dp, GoldPrimary.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.Rounded.MusicNote, contentDescription = null, tint = GoldBright, modifier = Modifier.size(14.dp))
                    Text(
                        text = reel.musicTitle.ifBlank { "Original Audio • @${reel.author.username}" },
                        style = MaterialTheme.typography.labelSmall,
                        color = GoldBright,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
