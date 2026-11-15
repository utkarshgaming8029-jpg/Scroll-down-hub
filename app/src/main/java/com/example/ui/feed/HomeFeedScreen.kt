package com.example.ui.feed

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.DynamicFeed
import androidx.compose.material.icons.rounded.LiveTv
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.PostAudience
import com.example.data.model.CommentUiModel
import com.example.data.model.LiveStreamUiModel
import com.example.data.model.PostUiModel
import com.example.data.model.StoryGroupUiModel
import com.example.data.model.UserProfile
import com.example.data.repository.SecurityUtils
import com.example.ui.components.*
import com.example.ui.main.MainViewModel
import com.example.ui.theme.*

@Composable
fun HomeFeedScreen(
    viewModel: MainViewModel,
    onOpenUserProfile: (String) -> Unit,
    onOpenHashtagSearch: (String) -> Unit,
    onOpenStoryViewer: (String) -> Unit,
    onOpenCreateStory: () -> Unit,
    onOpenCreatePost: () -> Unit,
    onOpenLiveStream: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
    val posts by viewModel.homeFeed.collectAsStateWithLifecycle()
    val storyGroups by viewModel.storyGroups.collectAsStateWithLifecycle()
    val activeLiveStreams by viewModel.activeLiveStreams.collectAsStateWithLifecycle()
    val appPreferences by viewModel.appPreferences.collectAsStateWithLifecycle()
    val hasMoreFeedPosts by viewModel.hasMoreFeedPosts.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    var commentsSheetPost by remember { mutableStateOf<PostUiModel?>(null) }
    var reportTargetPost by remember { mutableStateOf<PostUiModel?>(null) }

    // Infinite scroll pagination trigger
    val shouldLoadMore by remember {
        derivedStateOf {
            val total = listState.layoutInfo.totalItemsCount
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            total > 4 && lastVisible >= total - 2
        }
    }
    LaunchedEffect(shouldLoadMore, hasMoreFeedPosts) {
        if (shouldLoadMore && hasMoreFeedPosts) viewModel.loadMoreFeed()
    }

    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item(key = "stories_tray") {
            StoriesTray(
                currentUser = currentUser,
                storyGroups = storyGroups,
                onAddStory = onOpenCreateStory,
                onOpenStory = onOpenStoryViewer
            )
            HorizontalDivider(color = ObsidianSubtleBorder, thickness = 0.8.dp)
        }

        if (activeLiveStreams.isNotEmpty()) {
            item(key = "live_now_strip") {
                LiveStreamsStrip(
                    streams = activeLiveStreams,
                    onJoinLive = onOpenLiveStream
                )
                HorizontalDivider(color = ObsidianSubtleBorder, thickness = 0.8.dp)
            }
        }

        if (posts.isEmpty()) {
            item(key = "empty_feed") {
                EmptyStateView(
                    icon = Icons.Rounded.DynamicFeed,
                    title = "Your Luxury Feed Awaits",
                    subtitle = "Publish your first photo, carousel, or video—or follow creators in Search to populate your feed.",
                    actionLabel = "Create First Post",
                    onAction = onOpenCreatePost
                )
            }
        } else {
            items(posts, key = { it.id }) { post ->
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    PostCard(
                        post = post,
                        isOwnPost = post.author.id == viewModel.currentUserId,
                        isAdmin = currentUser?.isSuperAdmin == true,
                        onLike = { viewModel.toggleLike(post.id) },
                        onSave = { viewModel.toggleSave(post.id) },
                        onComment = { commentsSheetPost = post },
                        onReport = { reportTargetPost = post },
                        onArchive = { viewModel.setPostArchived(post.id, true) },
                        onDelete = { viewModel.deletePost(post.id) },
                        onAuthorClick = { onOpenUserProfile(post.author.id) },
                        onHashtagClick = onOpenHashtagSearch,
                        onMentionClick = { username ->
                            val found = viewModel.allUsers.value.firstOrNull {
                                it.username.equals(username, ignoreCase = true)
                            }
                            if (found != null) onOpenUserProfile(found.id)
                        },
                        onTranslateCaption = { captionText, onDone ->
                            viewModel.translatePostCaption(captionText, appPreferences.languageCode, onDone)
                        },
                        modifier = Modifier.widthIn(max = 600.dp)
                    )
                }
            }
        }
    }

    commentsSheetPost?.let { post ->
        CommentsBottomSheet(
            post = post,
            viewModel = viewModel,
            onDismiss = { commentsSheetPost = null },
            onUserClick = { uid ->
                commentsSheetPost = null
                onOpenUserProfile(uid)
            }
        )
    }

    reportTargetPost?.let { post ->
        ReportDialog(
            title = "Report Post",
            onDismiss = { reportTargetPost = null },
            onSubmit = { reason ->
                viewModel.reportContent("POST", post.id, reason)
                reportTargetPost = null
            }
        )
    }
}

@Composable
private fun StoriesTray(
    currentUser: UserProfile?,
    storyGroups: List<StoryGroupUiModel>,
    onAddStory: () -> Unit,
    onOpenStory: (String) -> Unit
) {
    val myStoryGroup = remember(storyGroups, currentUser?.id) {
        currentUser?.id?.let { uid -> storyGroups.firstOrNull { it.author.id == uid } }
    }
    val otherStoryGroups = remember(storyGroups, currentUser?.id) {
        val uid = currentUser?.id
        storyGroups.filter { it.author.id != uid }.distinctBy { it.author.id }
    }

    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item(key = "add_story") {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .width(74.dp)
                    .clickable {
                        if (myStoryGroup != null) onOpenStory(myStoryGroup.author.id) else onAddStory()
                    }
                    .testTag("add_story_button")
            ) {
                Box(modifier = Modifier.size(66.dp)) {
                    UserAvatar(
                        avatarUri = currentUser?.avatarUri,
                        displayName = currentUser?.displayName ?: "You",
                        size = 64.dp,
                        hasStoryRing = myStoryGroup != null,
                        isStoryUnseen = myStoryGroup?.hasUnseen == true
                    )
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(GoldPrimary)
                            .border(2.dp, ObsidianBlack, CircleShape)
                            .clickable(onClick = onAddStory)
                            .testTag("add_story_plus_badge"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Rounded.Add,
                            contentDescription = "Add Story",
                            tint = ObsidianBlack,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = if (myStoryGroup != null) "Your Story" else "Add Story",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (myStoryGroup != null) IvoryText else WarmGrayText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (currentUser?.isGoldenVerified == true) {
                        GoldenVerifiedBadge(size = 11.dp)
                    }
                }
            }
        }

        items(otherStoryGroups, key = { it.author.id }) { group ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .width(72.dp)
                    .clickable { onOpenStory(group.author.id) }
                    .testTag("story_ring_${group.author.username}")
            ) {
                UserAvatar(
                    avatarUri = group.author.avatarUri,
                    displayName = group.author.displayName,
                    size = 66.dp,
                    hasStoryRing = true,
                    isStoryUnseen = group.hasUnseen
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = group.author.username,
                        style = MaterialTheme.typography.labelSmall,
                        color = IvoryText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (group.author.isGoldenVerified) {
                        GoldenVerifiedBadge(size = 11.dp)
                    }
                }
            }
        }
    }
}

@Composable
private fun LiveStreamsStrip(
    streams: List<LiveStreamUiModel>,
    onJoinLive: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(Icons.Rounded.LiveTv, contentDescription = null, tint = CrimsonHeart, modifier = Modifier.size(16.dp))
            Text(
                text = "LIVE NOW",
                style = MaterialTheme.typography.labelMedium,
                color = CrimsonHeart,
                fontWeight = FontWeight.Bold
            )
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(streams, key = { it.id }) { stream ->
                Surface(
                    onClick = { onJoinLive(stream.id) },
                    color = ObsidianCard,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .border(1.dp, CrimsonHeart.copy(alpha = 0.7f), RoundedCornerShape(14.dp))
                        .testTag("live_banner_${stream.id}")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        UserAvatar(
                            avatarUri = stream.host.avatarUri,
                            displayName = stream.host.displayName,
                            size = 36.dp
                        )
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("@${stream.host.username}", style = MaterialTheme.typography.labelLarge, color = IvoryText)
                                if (stream.host.isGoldenVerified) GoldenVerifiedBadge(size = 12.dp)
                            }
                            Text(
                                text = "${stream.title} • ${stream.viewerCount} watching",
                                style = MaterialTheme.typography.labelSmall,
                                color = WarmGrayText,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Surface(color = CrimsonHeart, shape = RoundedCornerShape(6.dp)) {
                            Text(
                                text = "JOIN",
                                style = MaterialTheme.typography.labelSmall,
                                color = IvoryText,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PostCard(
    post: PostUiModel,
    isOwnPost: Boolean,
    isAdmin: Boolean,
    onLike: () -> Unit,
    onSave: () -> Unit,
    onComment: () -> Unit,
    onReport: () -> Unit,
    onArchive: () -> Unit,
    onDelete: () -> Unit,
    onAuthorClick: () -> Unit,
    onHashtagClick: (String) -> Unit,
    onMentionClick: (String) -> Unit,
    onTranslateCaption: (String, (Result<String>) -> Unit) -> Unit = { _, cb -> cb(Result.failure(IllegalStateException("Unavailable"))) },
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showMenu by remember { mutableStateOf(false) }
    var isTranslating by remember(post.id) { mutableStateOf(false) }
    var translatedCaption by remember(post.id) { mutableStateOf<String?>(null) }
    var translationError by remember(post.id) { mutableStateOf<String?>(null) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .background(MaterialTheme.colorScheme.surface)
            .border(0.8.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            UserAvatar(
                avatarUri = post.author.avatarUri,
                displayName = post.author.displayName,
                size = 38.dp,
                isOnline = post.author.isOnline,
                onClick = onAuthorClick
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onAuthorClick)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Text(
                        text = "@${post.author.username}",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    if (post.author.isGoldenVerified) {
                        GoldenVerifiedBadge(size = 15.dp)
                    }
                    if (post.audience == PostAudience.CLOSE_FRIENDS) {
                        Surface(
                            color = EmeraldOnline.copy(alpha = 0.16f),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.border(0.6.dp, EmeraldOnline, RoundedCornerShape(6.dp))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Icon(Icons.Rounded.Star, contentDescription = null, tint = EmeraldOnline, modifier = Modifier.size(10.dp))
                                Text("Close Friends", style = MaterialTheme.typography.labelSmall, color = EmeraldOnline)
                            }
                        }
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = SecurityUtils.formatRelativeTime(post.createdAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = WarmGrayText
                    )
                    if (post.musicTitle.isNotBlank()) {
                        Icon(
                            Icons.Rounded.MusicNote,
                            contentDescription = null,
                            tint = GoldPrimary,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = post.musicTitle,
                            style = MaterialTheme.typography.labelSmall,
                            color = GoldBright,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Outlined.MoreVert, contentDescription = "Post options", tint = WarmGrayText)
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                    containerColor = ObsidianCard
                ) {
                    DropdownMenuItem(
                        text = { Text("Report Post", color = IvoryText) },
                        leadingIcon = { Icon(Icons.Outlined.Flag, contentDescription = null, tint = AmberWarning) },
                        onClick = {
                            showMenu = false
                            onReport()
                        }
                    )
                    if (isOwnPost) {
                        DropdownMenuItem(
                            text = { Text("Archive Post", color = MaterialTheme.colorScheme.onBackground) },
                            leadingIcon = { Icon(Icons.Outlined.Archive, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                            onClick = {
                                showMenu = false
                                onArchive()
                            }
                        )
                    }
                    if (isOwnPost || isAdmin) {
                        DropdownMenuItem(
                            text = { Text("Delete Post", color = CrimsonHeart) },
                            leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null, tint = CrimsonHeart) },
                            onClick = {
                                showMenu = false
                                onDelete()
                            }
                        )
                    }
                }
            }
        }

        // Media
        Box(modifier = Modifier.padding(horizontal = 10.dp)) {
            PostMediaViewer(
                mediaUris = post.mediaUris,
                mediaType = post.mediaType,
                isActivePlayback = false
            )
        }

        // Action Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onLike, modifier = Modifier.testTag("like_button_${post.id}")) {
                Icon(
                    imageVector = if (post.isLiked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                    contentDescription = if (post.isLiked) "Unlike" else "Like",
                    tint = if (post.isLiked) CrimsonHeart else MaterialTheme.colorScheme.onBackground
                )
            }
            IconButton(onClick = onComment, modifier = Modifier.testTag("comment_button_${post.id}")) {
                Icon(Icons.Outlined.ModeComment, contentDescription = "Comments", tint = MaterialTheme.colorScheme.onBackground)
            }
            IconButton(onClick = {
                val shareText = "@${post.author.username} on SCROLL DOWN: ${post.caption}"
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, shareText)
                }
                context.startActivity(Intent.createChooser(intent, "Share Post"))
            }) {
                Icon(Icons.AutoMirrored.Outlined.Send, contentDescription = "Share post", tint = MaterialTheme.colorScheme.onBackground)
            }
            Spacer(modifier = Modifier.weight(1f))
            IconButton(onClick = onSave, modifier = Modifier.testTag("save_button_${post.id}")) {
                Icon(
                    imageVector = if (post.isSaved) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                    contentDescription = if (post.isSaved) "Unsave" else "Save",
                    tint = if (post.isSaved) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground
                )
            }
        }

        // Counts & Caption
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Text(
                text = "${SecurityUtils.formatCompactCount(post.likesCount)} likes",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onBackground
            )
            if (post.caption.isNotBlank()) {
                RichCaptionText(
                    username = post.author.username,
                    isVerified = post.author.isGoldenVerified,
                    caption = post.caption,
                    onHashtagClick = onHashtagClick,
                    onMentionClick = onMentionClick,
                    onAuthorClick = onAuthorClick
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .clickable(enabled = !isTranslating) {
                            if (translatedCaption != null) {
                                translatedCaption = null
                                translationError = null
                            } else {
                                isTranslating = true
                                translationError = null
                                onTranslateCaption(post.caption) { res ->
                                    isTranslating = false
                                    if (res.isSuccess) {
                                        translatedCaption = res.getOrNull()
                                    } else {
                                        translationError = res.exceptionOrNull()?.message ?: "Translation unavailable"
                                    }
                                }
                            }
                        }
                        .padding(vertical = 2.dp)
                        .testTag("translate_caption_${post.id}")
                ) {
                    Icon(
                        imageVector = if (isTranslating) Icons.Rounded.AutoAwesome else Icons.Rounded.Translate,
                        contentDescription = null,
                        tint = GoldBright,
                        modifier = Modifier.size(13.dp)
                    )
                    Text(
                        text = when {
                            isTranslating -> "Translating with AI..."
                            translatedCaption != null -> "Hide AI Translation"
                            else -> "See AI Translation"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = GoldBright
                    )
                }

                translatedCaption?.let { translated ->
                    Surface(
                        color = GoldTintSurface,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = translated,
                            style = MaterialTheme.typography.bodySmall,
                            color = IvoryText,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
                translationError?.let { err ->
                    Text(
                        text = err,
                        style = MaterialTheme.typography.labelSmall,
                        color = AmberWarning
                    )
                }
            }
            if (post.commentsCount > 0L) {
                Text(
                    text = "View all ${post.commentsCount} comments",
                    style = MaterialTheme.typography.bodySmall,
                    color = WarmGrayText,
                    modifier = Modifier
                        .clickable(onClick = onComment)
                        .padding(vertical = 2.dp)
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommentsBottomSheet(
    post: PostUiModel,
    viewModel: MainViewModel,
    onDismiss: () -> Unit,
    onUserClick: (String) -> Unit
) {
    val comments by viewModel.observeComments(post.id).collectAsStateWithLifecycle(initialValue = emptyList())
    var commentText by remember { mutableStateOf("") }
    var replyingTo by remember { mutableStateOf<CommentUiModel?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = ObsidianSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 320.dp, max = 560.dp)
                .padding(horizontal = 16.dp)
        ) {
            Text(
                text = "Comments",
                style = MaterialTheme.typography.titleLarge,
                color = GoldBright,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            HorizontalDivider(color = ObsidianSubtleBorder)

            if (comments.isEmpty()) {
                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No comments yet. Start the conversation.", color = WarmGrayText)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(comments, key = { it.id }) { comment ->
                        CommentRow(
                            comment = comment,
                            onReply = { replyingTo = comment },
                            onUserClick = { onUserClick(comment.author.id) }
                        )
                        comment.replies.forEach { reply ->
                            CommentRow(
                                comment = reply,
                                onReply = { replyingTo = comment },
                                onUserClick = { onUserClick(reply.author.id) },
                                modifier = Modifier.padding(start = 36.dp, top = 8.dp)
                            )
                        }
                    }
                }
            }

            replyingTo?.let { target ->
                Surface(
                    color = GoldTintSurface,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Replying to @${target.author.username}",
                            style = MaterialTheme.typography.labelMedium,
                            color = GoldBright,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { replyingTo = null }) {
                            Text("Cancel", color = WarmGrayText)
                        }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = commentText,
                    onValueChange = { commentText = it },
                    placeholder = { Text("Add a comment...") },
                    singleLine = true,
                    modifier = Modifier.weight(1f).testTag("comment_input_field")
                )
                Button(
                    onClick = {
                        if (commentText.isNotBlank()) {
                            viewModel.addComment(post.id, commentText, replyingTo?.id)
                            commentText = ""
                            replyingTo = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary, contentColor = ObsidianBlack),
                    modifier = Modifier.testTag("send_comment_button")
                ) {
                    Text("Post", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun CommentRow(
    comment: CommentUiModel,
    onReply: () -> Unit,
    onUserClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top
    ) {
        UserAvatar(
            avatarUri = comment.author.avatarUri,
            displayName = comment.author.displayName,
            size = 32.dp,
            onClick = onUserClick
        )
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "@${comment.author.username}",
                    style = MaterialTheme.typography.labelLarge,
                    color = IvoryText,
                    modifier = Modifier.clickable(onClick = onUserClick)
                )
                if (comment.author.isGoldenVerified) {
                    GoldenVerifiedBadge(size = 13.dp)
                }
                Text(
                    text = "• ${SecurityUtils.formatRelativeTime(comment.createdAt)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = DimGrayText
                )
            }
            Text(
                text = comment.text,
                style = MaterialTheme.typography.bodyMedium,
                color = IvoryText.copy(alpha = 0.92f)
            )
            Text(
                text = "Reply",
                style = MaterialTheme.typography.labelSmall,
                color = GoldPrimary,
                modifier = Modifier
                    .clickable(onClick = onReply)
                    .padding(top = 3.dp)
            )
        }
    }
}

@Composable
fun ReportDialog(
    title: String,
    onDismiss: () -> Unit,
    onSubmit: (String) -> Unit
) {
    val reasons = listOf("Spam or misleading", "Harassment or hate speech", "Inappropriate content", "Impersonation")
    var selectedReason by remember { mutableStateOf(reasons.first()) }
    var extraDetails by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ObsidianSurface,
        title = { Text(title, color = GoldBright) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                reasons.forEach { reason ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedReason = reason }
                    ) {
                        RadioButton(
                            selected = selectedReason == reason,
                            onClick = { selectedReason = reason }
                        )
                        Text(reason, color = IvoryText, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                OutlinedTextField(
                    value = extraDetails,
                    onValueChange = { extraDetails = it },
                    label = { Text("Additional details (optional)") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val combined = if (extraDetails.isBlank()) selectedReason else "$selectedReason: $extraDetails"
                    onSubmit(combined)
                },
                colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary, contentColor = ObsidianBlack)
            ) {
                Text("Submit Report", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = WarmGrayText)
            }
        }
    )
}
