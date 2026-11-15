package com.example.ui.profile

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.example.ui.components.*
import com.example.ui.feed.ReportDialog
import com.example.ui.main.MainViewModel
import com.example.ui.search.UserSearchRow
import com.example.ui.theme.*

enum class ProfileMediaTab { PHOTOS, VIDEOS, SAVED }
enum class FollowListMode { FOLLOWERS, FOLLOWING, MUTUALS }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    targetUserId: String,
    viewModel: MainViewModel,
    isSecondaryProfile: Boolean,
    onBack: () -> Unit,
    onOpenMessage: (String) -> Unit,
    onOpenUserProfile: (String) -> Unit,
    onOpenAdminPanel: () -> Unit,
    onOpenReels: () -> Unit,
    onOpenSettings: (String) -> Unit = {},
    onOpenYourActivity: () -> Unit = {},
    onOpenArchive: () -> Unit = {},
    onOpenCreatorDashboard: () -> Unit = {},
    onOpenCloseFriends: () -> Unit = {},
    onOpenMemories: () -> Unit = {},
    onOpenWatchHistory: () -> Unit = {},
    onOpenScheduledPosts: () -> Unit = {},
    onOpenLiveStream: () -> Unit = {},
    onOpenStoryViewer: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    if (isSecondaryProfile) {
        BackHandler { onBack() }
    }

    val strings = LocalAppStrings.current
    val allUsers by viewModel.allUsers.collectAsStateWithLifecycle()
    val allPosts by viewModel.allPosts.collectAsStateWithLifecycle()
    val savedPostIds by viewModel.savedPostIds.collectAsStateWithLifecycle()
    val storyGroups by viewModel.storyGroups.collectAsStateWithLifecycle()
    val highlights by viewModel.observeHighlights(targetUserId).collectAsStateWithLifecycle(initialValue = emptyList())
    val myVerRequests by viewModel.myVerificationRequests.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    val profile = allUsers.firstOrNull { it.id == targetUserId }
    val isOwnProfile = targetUserId == viewModel.currentUserId
    val userStoryGroup = remember(storyGroups, targetUserId) {
        storyGroups.firstOrNull { it.author.id == targetUserId }
    }

    var selectedTab by rememberSaveable { mutableStateOf(ProfileMediaTab.PHOTOS) }
    var followListMode by remember { mutableStateOf<FollowListMode?>(null) }
    var showEditDialog by remember { mutableStateOf(false) }
    var showProfileMenuSheet by remember { mutableStateOf(false) }
    var showQrDialog by remember { mutableStateOf(false) }
    var showReportDialog by remember { mutableStateOf(false) }
    var showVerificationDialog by remember { mutableStateOf(false) }
    var showUserMenu by remember { mutableStateOf(false) }
    var selectedPostPreview by remember { mutableStateOf<PostUiModel?>(null) }

    if (profile == null) {
        Box(
            modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
        return
    }

    val canViewPrivateContent = isOwnProfile || !profile.isPrivate || profile.isFollowing
    val userPosts = remember(allPosts, targetUserId) { allPosts.filter { it.author.id == targetUserId } }
    val photoPosts = remember(userPosts) { userPosts.filter { it.mediaType == MediaTypes.PHOTO || it.mediaType == MediaTypes.CAROUSEL } }
    val videoPosts = remember(userPosts) { userPosts.filter { it.mediaType == MediaTypes.VIDEO || it.mediaType == MediaTypes.REEL } }
    val savedPosts = remember(allPosts, savedPostIds) { allPosts.filter { it.id in savedPostIds } }

    val displayedPosts = when (selectedTab) {
        ProfileMediaTab.PHOTOS -> photoPosts
        ProfileMediaTab.VIDEOS -> videoPosts
        ProfileMediaTab.SAVED -> if (isOwnProfile) savedPosts else emptyList()
    }

    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(bottom = 32.dp)
    ) {
        item(key = "profile_topbar") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = if (isSecondaryProfile) 8.dp else 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isSecondaryProfile) {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                }
                Text(
                    text = "@${profile.username}",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.Bold
                )
                if (profile.isGoldenVerified) {
                    Spacer(modifier = Modifier.width(6.dp))
                    GoldenVerifiedBadge(size = 16.dp)
                }
                Spacer(modifier = Modifier.weight(1f))
                if (isOwnProfile) {
                    IconButton(
                        onClick = { showQrDialog = true },
                        modifier = Modifier.testTag("profile_qr_quick_button")
                    ) {
                        Icon(
                            Icons.Outlined.QrCode2,
                            contentDescription = strings.qrCode,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(
                        onClick = { showProfileMenuSheet = true },
                        modifier = Modifier.testTag("profile_menu_button")
                    ) {
                        Icon(
                            Icons.Rounded.Menu,
                            contentDescription = "Profile menu",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                }
            }
        }

        // Header Section
        item(key = "profile_header") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    UserAvatar(
                        avatarUri = profile.avatarUri,
                        displayName = profile.displayName,
                        size = 84.dp,
                        hasStoryRing = canViewPrivateContent && userStoryGroup != null,
                        isStoryUnseen = userStoryGroup?.hasUnseen == true,
                        isOnline = profile.isOnline,
                        onClick = if (canViewPrivateContent && userStoryGroup != null) {
                            { onOpenStoryViewer(profile.id) }
                        } else {
                            null
                        }
                    )

                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        ProfileStatColumn(
                            count = SecurityUtils.formatCompactCount(profile.postsCount),
                            label = "Posts",
                            onClick = {}
                        )
                        ProfileStatColumn(
                            count = SecurityUtils.formatCompactCount(profile.displayedFollowersCount),
                            label = "Followers",
                            onClick = { if (canViewPrivateContent) followListMode = FollowListMode.FOLLOWERS }
                        )
                        ProfileStatColumn(
                            count = SecurityUtils.formatCompactCount(profile.followingCount),
                            label = "Following",
                            onClick = { if (canViewPrivateContent) followListMode = FollowListMode.FOLLOWING }
                        )
                    }
                }

                // Name, Username, Golden Tick & Role Pill
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = profile.displayName,
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        if (profile.isGoldenVerified) {
                            GoldenVerifiedBadge(size = 18.dp)
                        }
                        if (profile.isSuperAdmin) {
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.border(0.8.dp, GoldPrimary, RoundedCornerShape(8.dp))
                            ) {
                                Text(
                                    text = "SUPER ADMIN",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "@${profile.username}" + if (profile.isPrivate) " • Private" else "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        if (profile.isGoldenVerified) {
                            GoldenVerifiedBadge(size = 13.dp)
                        }
                    }

                    if (profile.demoFollowersDisplay > 0L) {
                        Text(
                            text = "Demo display: 300,000 • Real followers: ${profile.actualFollowersCount}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (profile.bio.isNotBlank()) {
                        Text(
                            text = profile.bio,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.9f)
                        )
                    }

                    if (canViewPrivateContent && userStoryGroup != null) {
                        val activeStoriesCount = userStoryGroup.stories.size
                        Surface(
                            onClick = { onOpenStoryViewer(profile.id) },
                            color = GoldTintSurface,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .padding(top = 4.dp)
                                .border(0.8.dp, GoldPrimary, RoundedCornerShape(10.dp))
                                .testTag("profile_active_story_pill")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    Icons.Rounded.AutoStories,
                                    contentDescription = null,
                                    tint = GoldBright,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = if (activeStoriesCount == 1) "View Active Story" else "View $activeStoriesCount Active Stories",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = GoldBright,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }

                // Action Buttons
                if (isOwnProfile) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = { showEditDialog = true },
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f).testTag("edit_profile_button")
                        ) {
                            Icon(
                                Icons.Outlined.Edit,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(strings.editProfile, color = MaterialTheme.colorScheme.onBackground)
                        }
                        OutlinedButton(
                            onClick = { onOpenSettings("") },
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f).testTag("privacy_settings_button")
                        ) {
                            Icon(
                                Icons.Outlined.Settings,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(strings.settingsTitle, color = MaterialTheme.colorScheme.onBackground)
                        }
                    }

                    if (profile.isSuperAdmin) {
                        Button(
                            onClick = onOpenAdminPanel,
                            colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary, contentColor = ObsidianBlack),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().height(48.dp).testTag("open_admin_panel_button")
                        ) {
                            Icon(Icons.Rounded.AdminPanelSettings, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Open Super Admin Panel", fontWeight = FontWeight.Bold)
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = { viewModel.toggleFollow(profile.id) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (profile.isFollowing || profile.isFollowRequested) MaterialTheme.colorScheme.surfaceVariant else GoldPrimary,
                                contentColor = if (profile.isFollowing || profile.isFollowRequested) MaterialTheme.colorScheme.onBackground else ObsidianBlack
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f).testTag("profile_follow_button")
                        ) {
                            Text(
                                text = when {
                                    profile.isFollowing -> "Following"
                                    profile.isFollowRequested -> "Requested"
                                    profile.isFollowedByTarget -> "Follow Back"
                                    else -> "Follow"
                                },
                                fontWeight = FontWeight.Bold
                            )
                        }

                        OutlinedButton(
                            onClick = { onOpenMessage(profile.id) },
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f).testTag("profile_message_button")
                        ) {
                            Text("Message", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                        }

                        Box {
                            IconButton(onClick = { showUserMenu = true }) {
                                Icon(Icons.Outlined.MoreVert, contentDescription = "User options", tint = MaterialTheme.colorScheme.onBackground)
                            }
                            DropdownMenu(
                                expanded = showUserMenu,
                                onDismissRequest = { showUserMenu = false },
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Mutual Followers", color = MaterialTheme.colorScheme.onBackground) },
                                    onClick = {
                                        showUserMenu = false
                                        followListMode = FollowListMode.MUTUALS
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(if (profile.isRestrictedByMe) "Unrestrict User" else "Restrict User", color = MaterialTheme.colorScheme.onBackground) },
                                    onClick = {
                                        showUserMenu = false
                                        viewModel.toggleRestrict(profile.id)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(if (profile.isBlockedByMe) "Unblock User" else "Block User", color = CrimsonHeart) },
                                    onClick = {
                                        showUserMenu = false
                                        viewModel.toggleBlock(profile.id)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Report Account", color = AmberWarning) },
                                    onClick = {
                                        showUserMenu = false
                                        showReportDialog = true
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        // Story Highlights Strip
        if (canViewPrivateContent && highlights.isNotEmpty()) {
            item(key = "story_highlights") {
                Column(
                    modifier = Modifier.padding(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Story Highlights",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(highlights, key = { it.id }) { highlight ->
                            val highlightModel = remember(highlight.mediaUri) {
                                MediaStorageHelper.resolveCoilModel(highlight.mediaUri)
                            }
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.width(72.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(62.dp)
                                        .clip(CircleShape)
                                        .border(1.5.dp, GoldBrush, CircleShape)
                                        .background(MaterialTheme.colorScheme.surfaceVariant),
                                    contentAlignment = Alignment.Center
                                ) {
                                    AsyncImage(
                                        model = highlightModel,
                                        contentDescription = highlight.highlightTitle,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = highlight.highlightTitle ?: "Highlight",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onBackground,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
        }

        // Media Grid Tabs
        if (!canViewPrivateContent) {
            item(key = "private_lock") {
                EmptyStateView(
                    icon = Icons.Rounded.Lock,
                    title = "This Account Is Private",
                    subtitle = "Follow @${profile.username} to view their photos, videos, and story highlights once approved."
                )
            }
        } else {
            item(key = "profile_tabs") {
                TabRow(
                    selectedTabIndex = selectedTab.ordinal,
                    containerColor = MaterialTheme.colorScheme.background,
                    contentColor = MaterialTheme.colorScheme.primary
                ) {
                    Tab(
                        selected = selectedTab == ProfileMediaTab.PHOTOS,
                        onClick = { selectedTab = ProfileMediaTab.PHOTOS },
                        icon = { Icon(Icons.Rounded.GridOn, contentDescription = "Photos") }
                    )
                    Tab(
                        selected = selectedTab == ProfileMediaTab.VIDEOS,
                        onClick = { selectedTab = ProfileMediaTab.VIDEOS },
                        icon = { Icon(Icons.Rounded.VideoLibrary, contentDescription = "Videos") }
                    )
                    if (isOwnProfile) {
                        Tab(
                            selected = selectedTab == ProfileMediaTab.SAVED,
                            onClick = { selectedTab = ProfileMediaTab.SAVED },
                            icon = { Icon(Icons.Rounded.Bookmark, contentDescription = strings.savedPosts) }
                        )
                    }
                }
            }

            if (displayedPosts.isEmpty()) {
                item(key = "empty_grid") {
                    EmptyStateView(
                        icon = when (selectedTab) {
                            ProfileMediaTab.PHOTOS -> Icons.Rounded.GridOn
                            ProfileMediaTab.VIDEOS -> Icons.Rounded.VideoLibrary
                            ProfileMediaTab.SAVED -> Icons.Rounded.Bookmark
                        },
                        title = when (selectedTab) {
                            ProfileMediaTab.PHOTOS -> "No Photos Yet"
                            ProfileMediaTab.VIDEOS -> "No Videos or Reels Yet"
                            ProfileMediaTab.SAVED -> "No Saved Posts"
                        },
                        subtitle = when (selectedTab) {
                            ProfileMediaTab.SAVED -> "Bookmarked posts appear privately here."
                            else -> "Published media will display in this grid."
                        }
                    )
                }
            } else {
                items(displayedPosts.chunked(3)) { rowPosts ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 2.dp, vertical = 1.dp),
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        rowPosts.forEach { post ->
                            val firstMediaModel = remember(post.mediaUris) {
                                post.mediaUris.firstOrNull()?.let { MediaStorageHelper.resolveCoilModel(it) }
                            }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(1f)
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .clickable {
                                        if (post.isReel) onOpenReels() else selectedPostPreview = post
                                    }
                            ) {
                                if (post.mediaType == MediaTypes.VIDEO || post.mediaType == MediaTypes.REEL) {
                                    Box(
                                        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Rounded.PlayCircle, contentDescription = "Video", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(34.dp))
                                    }
                                } else if (firstMediaModel != null) {
                                    AsyncImage(
                                        model = firstMediaModel,
                                        contentDescription = post.caption,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                }
                            }
                        }
                        repeat(3 - rowPosts.size) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }

    // Three-line Profile Menu Sheet
    if (showProfileMenuSheet) {
        val latestVerReq = myVerRequests.firstOrNull()
        ModalBottomSheet(
            onDismissRequest = { showProfileMenuSheet = false },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 540.dp)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                item {
                    Text(
                        text = "Profile Menu",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
                item {
                    ProfileMenuSheetRow(
                        icon = Icons.Outlined.Settings,
                        label = strings.settingsTitle,
                        testTag = "menu_settings_privacy"
                    ) {
                        showProfileMenuSheet = false
                        onOpenSettings("")
                    }
                }
                item {
                    ProfileMenuSheetRow(
                        icon = Icons.Outlined.Analytics,
                        label = "Creator Studio & Badges",
                        testTag = "menu_creator_studio"
                    ) {
                        showProfileMenuSheet = false
                        onOpenCreatorDashboard()
                    }
                }
                item {
                    ProfileMenuSheetRow(
                        icon = Icons.Rounded.LiveTv,
                        label = "Go Live Broadcast",
                        testTag = "menu_go_live"
                    ) {
                        showProfileMenuSheet = false
                        onOpenLiveStream()
                    }
                }
                item {
                    ProfileMenuSheetRow(
                        icon = Icons.Outlined.StarOutline,
                        label = "Close Friends",
                        testTag = "menu_close_friends"
                    ) {
                        showProfileMenuSheet = false
                        onOpenCloseFriends()
                    }
                }
                item {
                    ProfileMenuSheetRow(
                        icon = Icons.Outlined.Schedule,
                        label = "Scheduled Posts",
                        testTag = "menu_scheduled_posts"
                    ) {
                        showProfileMenuSheet = false
                        onOpenScheduledPosts()
                    }
                }
                item {
                    ProfileMenuSheetRow(
                        icon = Icons.Outlined.AutoStories,
                        label = "Memories Timeline",
                        testTag = "menu_memories"
                    ) {
                        showProfileMenuSheet = false
                        onOpenMemories()
                    }
                }
                item {
                    ProfileMenuSheetRow(
                        icon = Icons.Outlined.PlayCircleOutline,
                        label = "Watch History",
                        testTag = "menu_watch_history"
                    ) {
                        showProfileMenuSheet = false
                        onOpenWatchHistory()
                    }
                }
                item {
                    ProfileMenuSheetRow(
                        icon = Icons.Outlined.History,
                        label = strings.yourActivity,
                        testTag = "menu_your_activity"
                    ) {
                        showProfileMenuSheet = false
                        onOpenYourActivity()
                    }
                }
                item {
                    ProfileMenuSheetRow(
                        icon = Icons.Outlined.BookmarkBorder,
                        label = strings.savedPosts,
                        testTag = "menu_saved_posts"
                    ) {
                        showProfileMenuSheet = false
                        selectedTab = ProfileMediaTab.SAVED
                    }
                }
                item {
                    ProfileMenuSheetRow(
                        icon = Icons.Outlined.Archive,
                        label = strings.archive,
                        testTag = "menu_archive"
                    ) {
                        showProfileMenuSheet = false
                        onOpenArchive()
                    }
                }
                if (!profile.isGoldenVerified) {
                    item {
                        val badgeLabel = when (latestVerReq?.status) {
                            com.example.data.local.VerificationStatus.PENDING -> "Golden Verification (Pending)"
                            else -> "Request Golden Verification"
                        }
                        ProfileMenuSheetRow(
                            icon = Icons.Outlined.Verified,
                            label = badgeLabel,
                            testTag = "menu_request_verification"
                        ) {
                            showProfileMenuSheet = false
                            showVerificationDialog = true
                        }
                    }
                }
                item {
                    ProfileMenuSheetRow(
                        icon = Icons.Outlined.QrCode2,
                        label = strings.qrCode,
                        testTag = "menu_qr_code"
                    ) {
                        showProfileMenuSheet = false
                        showQrDialog = true
                    }
                }
                item {
                    ProfileMenuSheetRow(
                        icon = Icons.AutoMirrored.Outlined.HelpOutline,
                        label = strings.help,
                        testTag = "menu_help"
                    ) {
                        showProfileMenuSheet = false
                        onOpenSettings("HELP")
                    }
                }
                item {
                    ProfileMenuSheetRow(
                        icon = Icons.Outlined.Info,
                        label = strings.about,
                        testTag = "menu_about"
                    ) {
                        showProfileMenuSheet = false
                        onOpenSettings("ABOUT")
                    }
                    Spacer(modifier = Modifier.height(16.dp))
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

    if (showQrDialog) {
        QrCodeDialog(
            profile = profile,
            onDismiss = { showQrDialog = false }
        )
    }

    // Followers / Following / Mutuals Dialog
    followListMode?.let { mode ->
        val list = when (mode) {
            FollowListMode.FOLLOWERS -> viewModel.getFollowersFor(targetUserId)
            FollowListMode.FOLLOWING -> viewModel.getFollowingFor(targetUserId)
            FollowListMode.MUTUALS -> viewModel.getMutualFollowers(targetUserId)
        }
        AlertDialog(
            onDismissRequest = { followListMode = null },
            containerColor = MaterialTheme.colorScheme.surface,
            title = {
                Text(
                    when (mode) {
                        FollowListMode.FOLLOWERS -> "Followers"
                        FollowListMode.FOLLOWING -> "Following"
                        FollowListMode.MUTUALS -> "Mutual Followers"
                    },
                    color = MaterialTheme.colorScheme.primary
                )
            },
            text = {
                if (list.isEmpty()) {
                    Text("No accounts in this list yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 360.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(list, key = { it.id }) { u ->
                            UserSearchRow(
                                user = u,
                                isSelf = u.id == viewModel.currentUserId,
                                onFollow = { viewModel.toggleFollow(u.id) },
                                onClick = {
                                    followListMode = null
                                    onOpenUserProfile(u.id)
                                }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { followListMode = null }) { Text("Close", color = MaterialTheme.colorScheme.primary) }
            }
        )
    }

    if (showEditDialog) {
        EditProfileDialog(
            profile = profile,
            onDismiss = { showEditDialog = false },
            onSave = { name, bio, isPriv, avatar ->
                viewModel.updateProfile(name, bio, isPriv, avatar) { showEditDialog = false }
            }
        )
    }

    if (showReportDialog) {
        ReportDialog(
            title = "Report @${profile.username}",
            onDismiss = { showReportDialog = false },
            onSubmit = { reason ->
                viewModel.reportContent("USER", profile.id, reason)
                showReportDialog = false
            }
        )
    }

    selectedPostPreview?.let { post ->
        AlertDialog(
            onDismissRequest = { selectedPostPreview = null },
            containerColor = MaterialTheme.colorScheme.surface,
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Text("@${post.author.username}", color = MaterialTheme.colorScheme.primary)
                    if (post.author.isGoldenVerified) GoldenVerifiedBadge(size = 15.dp)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    PostMediaViewer(mediaUris = post.mediaUris, mediaType = post.mediaType)
                    if (post.caption.isNotBlank()) {
                        Text(post.caption, color = MaterialTheme.colorScheme.onBackground, style = MaterialTheme.typography.bodyMedium)
                    }
                    Text(
                        "${post.likesCount} likes • ${post.commentsCount} comments",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (post.author.id == viewModel.currentUserId) {
                        TextButton(onClick = {
                            viewModel.setPostArchived(post.id, true)
                            selectedPostPreview = null
                        }) {
                            Text("Archive", color = MaterialTheme.colorScheme.primary)
                        }
                    }
                    TextButton(onClick = { selectedPostPreview = null }) {
                        Text("Close", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        )
    }
}

@Composable
private fun ProfileMenuSheetRow(
    icon: ImageVector,
    label: String,
    testTag: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 14.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f)
        )
        Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ProfileStatColumn(count: String, label: String, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(count, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun EditProfileDialog(
    profile: UserProfile,
    onDismiss: () -> Unit,
    onSave: (String, String, Boolean, Uri?) -> Unit
) {
    var displayName by remember { mutableStateOf(profile.displayName) }
    var bio by remember { mutableStateOf(profile.bio) }
    var isPrivate by remember { mutableStateOf(profile.isPrivate) }
    var pickedAvatar by remember { mutableStateOf<Uri?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) pickedAvatar = uri
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text("Edit Profile", color = MaterialTheme.colorScheme.primary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (pickedAvatar == null) "Change Profile Photo" else "New Photo Selected ✓", color = MaterialTheme.colorScheme.primary)
                }
                OutlinedTextField(
                    value = displayName,
                    onValueChange = { displayName = it },
                    label = { Text("Display Name") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = bio,
                    onValueChange = { bio = it },
                    label = { Text("Bio") },
                    maxLines = 3
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Private Account", color = MaterialTheme.colorScheme.onBackground)
                    Switch(checked = isPrivate, onCheckedChange = { isPrivate = it })
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(displayName, bio, isPrivate, pickedAvatar) },
                colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary, contentColor = ObsidianBlack)
            ) { Text("Save", fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    )
}
