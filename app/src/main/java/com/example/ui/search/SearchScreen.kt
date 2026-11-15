package com.example.ui.search

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Tag
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.example.data.model.PostUiModel
import com.example.data.model.UserProfile
import com.example.data.repository.MediaStorageHelper
import com.example.data.repository.SecurityUtils
import com.example.ui.components.EmptyStateView
import com.example.ui.components.GoldenVerifiedBadge
import com.example.ui.components.UserAvatar
import com.example.ui.main.MainViewModel
import com.example.ui.theme.*

enum class SearchFilterTab(val label: String) {
    EXPLORE("Explore"),
    USERS("Accounts"),
    HASHTAGS("Hashtags"),
    POSTS("Posts")
}

@Composable
fun SearchScreen(
    viewModel: MainViewModel,
    initialQuery: String = "",
    onOpenUserProfile: (String) -> Unit,
    onOpenReels: () -> Unit,
    modifier: Modifier = Modifier
) {
    val allUsers by viewModel.allUsers.collectAsStateWithLifecycle()
    val allPosts by viewModel.allPosts.collectAsStateWithLifecycle()

    var query by rememberSaveable(initialQuery) { mutableStateOf(initialQuery) }
    var selectedTab by rememberSaveable(initialQuery) {
        mutableStateOf(
            if (initialQuery.startsWith("#")) SearchFilterTab.HASHTAGS else SearchFilterTab.EXPLORE
        )
    }
    val exploreListState = rememberLazyListState()
    val usersListState = rememberLazyListState()
    val hashtagsListState = rememberLazyListState()

    val cleanQuery = query.trim().removePrefix("#").removePrefix("@").lowercase()

    val matchedUsers = remember(allUsers, cleanQuery) {
        allUsers.filter { !it.isBanned && !it.isBlockedByMe }.filter {
            cleanQuery.isBlank() ||
                it.username.lowercase().contains(cleanQuery) ||
                it.displayName.lowercase().contains(cleanQuery)
        }.sortedByDescending { it.displayedFollowersCount }
    }

    val trendingHashtags = remember(allPosts, cleanQuery) {
        allPosts.flatMap { it.hashtags }
            .groupingBy { it }
            .eachCount()
            .entries
            .filter { cleanQuery.isBlank() || it.key.contains(cleanQuery) }
            .sortedByDescending { it.value }
            .map { it.key to it.value }
    }

    val matchedPosts = remember(allPosts, cleanQuery) {
        allPosts.filter { post ->
            cleanQuery.isBlank() ||
                post.caption.lowercase().contains(cleanQuery) ||
                post.hashtags.any { it.contains(cleanQuery) } ||
                post.author.username.lowercase().contains(cleanQuery)
        }.sortedByDescending { it.likesCount + it.viewsCount }
    }

    val trendingReels = remember(allPosts) {
        allPosts.filter { it.isReel }.sortedByDescending { it.viewsCount + it.likesCount }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianBlack)
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Search accounts, #hashtags, posts...") },
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null, tint = GoldBright) },
            trailingIcon = {
                if (query.isNotBlank()) {
                    IconButton(onClick = { query = "" }) {
                        Icon(Icons.Outlined.Close, contentDescription = "Clear search")
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp)
                .testTag("search_input")
        )

        TabRow(
            selectedTabIndex = selectedTab.ordinal,
            containerColor = ObsidianBlack,
            contentColor = GoldBright
        ) {
            SearchFilterTab.entries.forEach { tab ->
                Tab(
                    selected = selectedTab == tab,
                    onClick = { selectedTab = tab },
                    text = {
                        Text(
                            tab.label,
                            color = if (selectedTab == tab) GoldBright else WarmGrayText,
                            fontWeight = if (selectedTab == tab) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                )
            }
        }

        when (selectedTab) {
            SearchFilterTab.EXPLORE -> {
                LazyColumn(
                    state = exploreListState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    // Trending Hashtags Strip
                    if (trendingHashtags.isNotEmpty()) {
                        item(key = "trending_hashtags_strip") {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = "Trending Hashtags",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = GoldBright,
                                    modifier = Modifier.padding(horizontal = 16.dp)
                                )
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    items(trendingHashtags.take(10), key = { it.first }) { (tag, count) ->
                                        AssistChip(
                                            onClick = {
                                                query = "#$tag"
                                                selectedTab = SearchFilterTab.POSTS
                                            },
                                            label = { Text("#$tag ($count)", color = GoldBright) }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Suggested Accounts
                    item(key = "suggested_accounts") {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                text = "Suggested Accounts",
                                style = MaterialTheme.typography.titleMedium,
                                color = IvoryText,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(matchedUsers.take(10), key = { it.id }) { user ->
                                    SuggestedUserCard(
                                        user = user,
                                        isSelf = user.id == viewModel.currentUserId,
                                        onFollow = { viewModel.toggleFollow(user.id) },
                                        onClick = { onOpenUserProfile(user.id) }
                                    )
                                }
                            }
                        }
                    }

                    // Trending Reels Strip
                    if (trendingReels.isNotEmpty()) {
                        item(key = "trending_reels") {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(
                                    text = "Trending Reels",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = GoldBright,
                                    modifier = Modifier.padding(horizontal = 16.dp)
                                )
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    items(trendingReels.take(8), key = { it.id }) { reel ->
                                        Box(
                                            modifier = Modifier
                                                .width(130.dp)
                                                .height(200.dp)
                                                .clip(RoundedCornerShape(14.dp))
                                                .background(ObsidianCard)
                                                .border(1.dp, ObsidianOutline, RoundedCornerShape(14.dp))
                                                .clickable(onClick = onOpenReels)
                                        ) {
                                            Icon(
                                                Icons.Rounded.PlayCircle,
                                                contentDescription = "Play Reel",
                                                tint = GoldBright,
                                                modifier = Modifier
                                                    .align(Alignment.Center)
                                                    .size(40.dp)
                                            )
                                            Row(
                                                modifier = Modifier
                                                    .align(Alignment.BottomStart)
                                                    .padding(8.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Text(
                                                    text = "@${reel.author.username} • ${SecurityUtils.formatCompactCount(reel.viewsCount)} views",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = IvoryText,
                                                    modifier = Modifier.weight(1f, fill = false)
                                                )
                                                if (reel.author.isGoldenVerified) GoldenVerifiedBadge(size = 11.dp)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Explore Posts Grid Preview
                    item(key = "explore_posts_header") {
                        Text(
                            text = "Explore Content",
                            style = MaterialTheme.typography.titleMedium,
                            color = IvoryText,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }

                    if (matchedPosts.isEmpty()) {
                        item(key = "explore_empty") {
                            EmptyStateView(
                                icon = Icons.AutoMirrored.Rounded.TrendingUp,
                                title = "Nothing Trending Yet",
                                subtitle = "As creators share posts and reels on SCROLL DOWN, trending content appears here."
                            )
                        }
                    } else {
                        items(matchedPosts.chunked(3)) { rowPosts ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                rowPosts.forEach { post ->
                                    ExploreTile(
                                        post = post,
                                        onClick = {
                                            if (post.isReel) onOpenReels() else onOpenUserProfile(post.author.id)
                                        },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                repeat(3 - rowPosts.size) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }

            SearchFilterTab.USERS -> {
                if (matchedUsers.isEmpty()) {
                    EmptyStateView(
                        icon = Icons.Outlined.Search,
                        title = "No matching accounts",
                        subtitle = "Try another name or @username."
                    )
                } else {
                    LazyColumn(
                        state = usersListState,
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(matchedUsers, key = { it.id }) { user ->
                            UserSearchRow(
                                user = user,
                                isSelf = user.id == viewModel.currentUserId,
                                onFollow = { viewModel.toggleFollow(user.id) },
                                onClick = { onOpenUserProfile(user.id) }
                            )
                        }
                    }
                }
            }

            SearchFilterTab.HASHTAGS -> {
                if (trendingHashtags.isEmpty()) {
                    EmptyStateView(
                        icon = Icons.Outlined.Tag,
                        title = "No hashtags found",
                        subtitle = "Add #tags to your posts and reels to make them discoverable."
                    )
                } else {
                    LazyColumn(
                        state = hashtagsListState,
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(trendingHashtags, key = { it.first }) { (tag, count) ->
                            Surface(
                                onClick = {
                                    query = "#$tag"
                                    selectedTab = SearchFilterTab.POSTS
                                },
                                color = ObsidianCard,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Icon(Icons.Outlined.Tag, contentDescription = null, tint = GoldBright)
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("#$tag", style = MaterialTheme.typography.titleSmall, color = IvoryText)
                                        Text("$count posts", style = MaterialTheme.typography.bodySmall, color = WarmGrayText)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            SearchFilterTab.POSTS -> {
                if (matchedPosts.isEmpty()) {
                    EmptyStateView(
                        icon = Icons.Outlined.Search,
                        title = "No matching posts",
                        subtitle = "Try another keyword or #hashtag."
                    )
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        contentPadding = PaddingValues(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(matchedPosts, key = { it.id }) { post ->
                            ExploreTile(
                                post = post,
                                onClick = {
                                    if (post.isReel) onOpenReels() else onOpenUserProfile(post.author.id)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SuggestedUserCard(
    user: UserProfile,
    isSelf: Boolean,
    onFollow: () -> Unit,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = ObsidianCard,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .width(156.dp)
            .border(1.dp, ObsidianOutline, RoundedCornerShape(16.dp))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            UserAvatar(avatarUri = user.avatarUri, displayName = user.displayName, size = 56.dp)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = user.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    color = IvoryText,
                    maxLines = 1
                )
                if (user.isGoldenVerified) GoldenVerifiedBadge(size = 14.dp)
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = "@${user.username}",
                    style = MaterialTheme.typography.labelSmall,
                    color = WarmGrayText,
                    maxLines = 1
                )
                if (user.isGoldenVerified) GoldenVerifiedBadge(size = 12.dp)
            }
            Text(
                text = "${SecurityUtils.formatCompactCount(user.displayedFollowersCount)} followers",
                style = MaterialTheme.typography.labelSmall,
                color = GoldBright
            )
            if (!isSelf) {
                Button(
                    onClick = onFollow,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (user.isFollowing) ObsidianElevated else GoldPrimary,
                        contentColor = if (user.isFollowing) IvoryText else ObsidianBlack
                    ),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    modifier = Modifier.fillMaxWidth().height(34.dp)
                ) {
                    Text(
                        text = when {
                            user.isFollowing -> "Following"
                            user.isFollowRequested -> "Requested"
                            else -> "Follow"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun UserSearchRow(
    user: UserProfile,
    isSelf: Boolean,
    onFollow: () -> Unit,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = ObsidianCard,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            UserAvatar(avatarUri = user.avatarUri, displayName = user.displayName, size = 46.dp, isOnline = user.isOnline)
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(user.displayName, style = MaterialTheme.typography.titleSmall, color = IvoryText)
                    if (user.isGoldenVerified) GoldenVerifiedBadge(size = 15.dp)
                }
                Text(
                    text = "@${user.username} • ${SecurityUtils.formatCompactCount(user.displayedFollowersCount)} followers",
                    style = MaterialTheme.typography.bodySmall,
                    color = WarmGrayText
                )
            }
            if (!isSelf) {
                Button(
                    onClick = onFollow,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (user.isFollowing) ObsidianElevated else GoldPrimary,
                        contentColor = if (user.isFollowing) IvoryText else ObsidianBlack
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        text = when {
                            user.isFollowing -> "Following"
                            user.isFollowRequested -> "Requested"
                            else -> "Follow"
                        },
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun ExploreTile(
    post: PostUiModel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(10.dp))
            .background(ObsidianCard)
            .clickable(onClick = onClick)
    ) {
        if (post.mediaType == MediaTypes.VIDEO || post.mediaType == MediaTypes.REEL) {
            Box(
                modifier = Modifier.fillMaxSize().background(ObsidianElevated),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.PlayCircle, contentDescription = "Video", tint = GoldBright, modifier = Modifier.size(34.dp))
            }
        } else if (post.mediaUris.isNotEmpty()) {
            val tileModel = remember(post.mediaUris) { MediaStorageHelper.resolveCoilModel(post.mediaUris.first()) }
            AsyncImage(
                model = tileModel,
                contentDescription = post.caption,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }
    }
}
