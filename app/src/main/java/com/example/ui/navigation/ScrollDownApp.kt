package com.example.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.data.repository.SecurityUtils
import com.example.ui.admin.AdminPanelScreen
import com.example.ui.components.ScrollDownLogo
import com.example.ui.create.CreateMode
import com.example.ui.create.CreateScreen
import com.example.ui.feed.HomeFeedScreen
import com.example.ui.main.MainViewModel
import com.example.ui.messages.ActiveCallOverlay
import com.example.ui.messages.ChatScreen
import com.example.ui.messages.LiveStreamScreen
import com.example.ui.messages.MessagesListScreen
import com.example.ui.notifications.NotificationsScreen
import com.example.ui.profile.ArchiveScreen
import com.example.ui.profile.CloseFriendsScreen
import com.example.ui.profile.CreatorDashboardScreen
import com.example.ui.profile.MemoriesScreen
import com.example.ui.profile.ProfileScreen
import com.example.ui.profile.ScheduledPostsScreen
import com.example.ui.profile.SettingsScreen
import com.example.ui.profile.WatchHistoryScreen
import com.example.ui.profile.YourActivityScreen
import com.example.ui.reels.ReelsScreen
import com.example.ui.search.SearchScreen
import com.example.ui.stories.StoryViewerScreen
import com.example.ui.theme.*
import kotlinx.coroutines.flow.collectLatest

private data class BottomNavTab(
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val route: Any,
    val testTag: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScrollDownApp(
    viewModel: MainViewModel
) {
    val strings = LocalAppStrings.current
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val unreadNotifs by viewModel.unreadNotificationsCount.collectAsStateWithLifecycle()
    val unreadMsgs by viewModel.unreadMessagesCount.collectAsStateWithLifecycle()
    val activeCalls by viewModel.activeCalls.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel) {
        viewModel.feedback.collectLatest { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    val navTabs = remember(strings) {
        listOf(
            BottomNavTab(strings.navHome, Icons.Filled.Home, Icons.Outlined.Home, HomeRoute, "nav_home"),
            BottomNavTab(strings.navSearch, Icons.Filled.Search, Icons.Outlined.Search, SearchRoute(), "nav_search"),
            BottomNavTab(strings.navReels, Icons.Filled.VideoLibrary, Icons.Outlined.VideoLibrary, ReelsRoute, "nav_reels"),
            BottomNavTab(strings.navCreate, Icons.Filled.AddBox, Icons.Outlined.AddBox, CreateRoute(), "nav_create"),
            BottomNavTab(strings.navProfile, Icons.Filled.Person, Icons.Outlined.Person, ProfileRoute, "nav_profile")
        )
    }

    val isTopLevelTab = currentDestination?.let { dest ->
        dest.hasRoute<HomeRoute>() ||
            dest.hasRoute<SearchRoute>() ||
            dest.hasRoute<ReelsRoute>() ||
            dest.hasRoute<CreateRoute>() ||
            dest.hasRoute<ProfileRoute>()
    } ?: true

    val showTopBar = currentDestination?.hasRoute<HomeRoute>() == true

    fun navigateTopTab(route: Any) {
        val alreadyOnTarget = when (route) {
            is HomeRoute -> currentDestination?.hasRoute<HomeRoute>() == true
            is SearchRoute -> currentDestination?.hasRoute<SearchRoute>() == true
            is ReelsRoute -> currentDestination?.hasRoute<ReelsRoute>() == true
            is CreateRoute -> currentDestination?.hasRoute<CreateRoute>() == true
            is ProfileRoute -> currentDestination?.hasRoute<ProfileRoute>() == true
            else -> false
        }
        if (alreadyOnTarget) return
        navController.navigate(route) {
            popUpTo(HomeRoute) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        val isCompactWidth = maxWidth < 600.dp

        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            snackbarHost = {
                SnackbarHost(hostState = snackbarHostState) { data ->
                    Snackbar(
                        snackbarData = data,
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.primary
                    )
                }
            },
            topBar = {
                if (showTopBar) {
                    TopAppBar(
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.background,
                            titleContentColor = MaterialTheme.colorScheme.onBackground
                        ),
                        title = { ScrollDownLogo(compact = true) },
                        actions = {
                            IconButton(
                                onClick = { navController.navigate(LiveStreamRoute("")) },
                                modifier = Modifier.testTag("top_go_live_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.LiveTv,
                                    contentDescription = "Go Live",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            IconButton(
                                onClick = { navController.navigate(NotificationsRoute) },
                                modifier = Modifier.testTag("top_notifications_button")
                            ) {
                                BadgedBox(
                                    badge = {
                                        if (unreadNotifs > 0) {
                                            Badge(
                                                containerColor = GoldPrimary,
                                                contentColor = ObsidianBlack
                                            ) {
                                                Text("$unreadNotifs")
                                            }
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Notifications,
                                        contentDescription = "Notifications",
                                        tint = MaterialTheme.colorScheme.onBackground
                                    )
                                }
                            }
                            IconButton(
                                onClick = { navController.navigate(MessagesRoute) },
                                modifier = Modifier.testTag("top_messages_button")
                            ) {
                                BadgedBox(
                                    badge = {
                                        if (unreadMsgs > 0) {
                                            Badge(
                                                containerColor = GoldPrimary,
                                                contentColor = ObsidianBlack
                                            ) {
                                                Text("$unreadMsgs")
                                            }
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.ChatBubbleOutline,
                                        contentDescription = "Direct Messages",
                                        tint = MaterialTheme.colorScheme.onBackground
                                    )
                                }
                            }
                        }
                    )
                }
            },
            bottomBar = {
                if (isCompactWidth && isTopLevelTab) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.primary
                    ) {
                        navTabs.forEach { tab ->
                            val selected = when (tab.route) {
                                is HomeRoute -> currentDestination?.hasRoute<HomeRoute>() == true
                                is SearchRoute -> currentDestination?.hasRoute<SearchRoute>() == true
                                is ReelsRoute -> currentDestination?.hasRoute<ReelsRoute>() == true
                                is CreateRoute -> currentDestination?.hasRoute<CreateRoute>() == true
                                is ProfileRoute -> currentDestination?.hasRoute<ProfileRoute>() == true
                                else -> false
                            }
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navigateTopTab(tab.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = tab.label
                                    )
                                },
                                label = { Text(tab.label, style = MaterialTheme.typography.labelSmall) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.primary,
                                    selectedTextColor = MaterialTheme.colorScheme.primary,
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    indicatorColor = MaterialTheme.colorScheme.primaryContainer
                                ),
                                modifier = Modifier.testTag(tab.testTag)
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (!isCompactWidth && isTopLevelTab) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.fillMaxHeight()
                    ) {
                        Spacer(modifier = Modifier.height(12.dp))
                        navTabs.forEach { tab ->
                            val selected = when (tab.route) {
                                is HomeRoute -> currentDestination?.hasRoute<HomeRoute>() == true
                                is SearchRoute -> currentDestination?.hasRoute<SearchRoute>() == true
                                is ReelsRoute -> currentDestination?.hasRoute<ReelsRoute>() == true
                                is CreateRoute -> currentDestination?.hasRoute<CreateRoute>() == true
                                is ProfileRoute -> currentDestination?.hasRoute<ProfileRoute>() == true
                                else -> false
                            }
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateTopTab(tab.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = tab.label
                                    )
                                },
                                label = { Text(tab.label) },
                                colors = NavigationRailItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.primary,
                                    selectedTextColor = MaterialTheme.colorScheme.primary,
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    indicatorColor = MaterialTheme.colorScheme.primaryContainer
                                )
                            )
                        }
                    }
                }

                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    NavHost(
                        navController = navController,
                        startDestination = HomeRoute
                    ) {
                        composable<HomeRoute> {
                            HomeFeedScreen(
                                viewModel = viewModel,
                                onOpenUserProfile = { uid ->
                                    if (uid == viewModel.currentUserId) navigateTopTab(ProfileRoute)
                                    else navController.navigate(UserProfileRoute(uid))
                                },
                                onOpenHashtagSearch = { tag ->
                                    navController.navigate(SearchRoute(initialQuery = "#$tag"))
                                },
                                onOpenStoryViewer = { authorId ->
                                    navController.navigate(StoryViewerRoute(authorId))
                                },
                                onOpenCreateStory = {
                                    navController.navigate(CreateRoute(initialMode = "STORY"))
                                },
                                onOpenCreatePost = {
                                    navigateTopTab(CreateRoute())
                                },
                                onOpenLiveStream = { streamId ->
                                    navController.navigate(LiveStreamRoute(streamId))
                                }
                            )
                        }

                        composable<SearchRoute> { entry ->
                            val args = entry.toRoute<SearchRoute>()
                            SearchScreen(
                                viewModel = viewModel,
                                initialQuery = args.initialQuery,
                                onOpenUserProfile = { uid ->
                                    if (uid == viewModel.currentUserId) navigateTopTab(ProfileRoute)
                                    else navController.navigate(UserProfileRoute(uid))
                                },
                                onOpenReels = { navigateTopTab(ReelsRoute) }
                            )
                        }

                        composable<ReelsRoute> {
                            ReelsScreen(
                                viewModel = viewModel,
                                onOpenUserProfile = { uid ->
                                    if (uid == viewModel.currentUserId) navigateTopTab(ProfileRoute)
                                    else navController.navigate(UserProfileRoute(uid))
                                },
                                onOpenCreateReel = {
                                    navController.navigate(CreateRoute(initialMode = "REEL"))
                                }
                            )
                        }

                        composable<CreateRoute> { entry ->
                            val args = entry.toRoute<CreateRoute>()
                            val mode = when (args.initialMode) {
                                "REEL" -> CreateMode.REEL
                                "STORY" -> CreateMode.STORY
                                else -> CreateMode.POST
                            }
                            CreateScreen(
                                viewModel = viewModel,
                                initialMode = mode,
                                onPublished = { publishedMode ->
                                    when (publishedMode) {
                                        CreateMode.REEL -> navigateTopTab(ReelsRoute)
                                        CreateMode.STORY -> {
                                            if (args.initialMode == "STORY" && navController.previousBackStackEntry != null) {
                                                navController.popBackStack()
                                            } else {
                                                navigateTopTab(HomeRoute)
                                            }
                                        }
                                        else -> navigateTopTab(HomeRoute)
                                    }
                                }
                            )
                        }

                        composable<ProfileRoute> {
                            ProfileScreen(
                                targetUserId = viewModel.currentUserId,
                                viewModel = viewModel,
                                isSecondaryProfile = false,
                                onBack = {},
                                onOpenMessage = { peerId ->
                                    val convId = SecurityUtils.buildConversationId(viewModel.currentUserId, peerId)
                                    navController.navigate(ChatRoute(convId, peerId))
                                },
                                onOpenUserProfile = { uid ->
                                    if (uid != viewModel.currentUserId) navController.navigate(UserProfileRoute(uid))
                                },
                                onOpenAdminPanel = { navController.navigate(AdminPanelRoute) },
                                onOpenReels = { navigateTopTab(ReelsRoute) },
                                onOpenSettings = { section -> navController.navigate(SettingsRoute(section)) },
                                onOpenYourActivity = { navController.navigate(YourActivityRoute) },
                                onOpenArchive = { navController.navigate(ArchiveRoute) },
                                onOpenCreatorDashboard = { navController.navigate(CreatorDashboardRoute) },
                                onOpenCloseFriends = { navController.navigate(CloseFriendsRoute) },
                                onOpenMemories = { navController.navigate(MemoriesRoute) },
                                onOpenWatchHistory = { navController.navigate(WatchHistoryRoute) },
                                onOpenScheduledPosts = { navController.navigate(ScheduledPostsRoute) },
                                onOpenLiveStream = { navController.navigate(LiveStreamRoute("")) },
                                onOpenStoryViewer = { uid -> navController.navigate(StoryViewerRoute(uid)) }
                            )
                        }

                        composable<UserProfileRoute> { entry ->
                            val args = entry.toRoute<UserProfileRoute>()
                            ProfileScreen(
                                targetUserId = args.userId,
                                viewModel = viewModel,
                                isSecondaryProfile = true,
                                onBack = { navController.popBackStack() },
                                onOpenMessage = { peerId ->
                                    val convId = SecurityUtils.buildConversationId(viewModel.currentUserId, peerId)
                                    navController.navigate(ChatRoute(convId, peerId))
                                },
                                onOpenUserProfile = { uid ->
                                    if (uid == viewModel.currentUserId) navigateTopTab(ProfileRoute)
                                    else navController.navigate(UserProfileRoute(uid))
                                },
                                onOpenAdminPanel = { navController.navigate(AdminPanelRoute) },
                                onOpenReels = { navigateTopTab(ReelsRoute) },
                                onOpenStoryViewer = { uid -> navController.navigate(StoryViewerRoute(uid)) }
                            )
                        }

                        composable<SettingsRoute> { entry ->
                            val args = entry.toRoute<SettingsRoute>()
                            SettingsScreen(
                                viewModel = viewModel,
                                initialSection = args.initialSection,
                                onBack = { navController.popBackStack() }
                            )
                        }

                        composable<YourActivityRoute> {
                            YourActivityScreen(
                                viewModel = viewModel,
                                onBack = { navController.popBackStack() }
                            )
                        }

                        composable<ArchiveRoute> {
                            ArchiveScreen(
                                viewModel = viewModel,
                                onBack = { navController.popBackStack() }
                            )
                        }

                        composable<CreatorDashboardRoute> {
                            CreatorDashboardScreen(
                                viewModel = viewModel,
                                onBack = { navController.popBackStack() },
                                onOpenLiveStream = { navController.navigate(LiveStreamRoute("")) },
                                onOpenReels = { navigateTopTab(ReelsRoute) }
                            )
                        }

                        composable<CloseFriendsRoute> {
                            CloseFriendsScreen(
                                viewModel = viewModel,
                                onBack = { navController.popBackStack() }
                            )
                        }

                        composable<MemoriesRoute> {
                            MemoriesScreen(
                                viewModel = viewModel,
                                onBack = { navController.popBackStack() }
                            )
                        }

                        composable<WatchHistoryRoute> {
                            WatchHistoryScreen(
                                viewModel = viewModel,
                                onBack = { navController.popBackStack() },
                                onOpenReels = { navigateTopTab(ReelsRoute) }
                            )
                        }

                        composable<ScheduledPostsRoute> {
                            ScheduledPostsScreen(
                                viewModel = viewModel,
                                onBack = { navController.popBackStack() }
                            )
                        }

                        composable<LiveStreamRoute> { entry ->
                            val args = entry.toRoute<LiveStreamRoute>()
                            LiveStreamScreen(
                                streamId = args.streamId.takeIf { it.isNotBlank() },
                                viewModel = viewModel,
                                onBack = { navController.popBackStack() }
                            )
                        }

                        composable<NotificationsRoute> {
                            NotificationsScreen(
                                viewModel = viewModel,
                                onBack = { navController.popBackStack() },
                                onOpenUserProfile = { uid ->
                                    if (uid == viewModel.currentUserId) navigateTopTab(ProfileRoute)
                                    else navController.navigate(UserProfileRoute(uid))
                                }
                            )
                        }

                        composable<MessagesRoute> {
                            MessagesListScreen(
                                viewModel = viewModel,
                                onBack = { navController.popBackStack() },
                                onOpenChat = { convId, peerId -> navController.navigate(ChatRoute(convId, peerId)) }
                            )
                        }

                        composable<ChatRoute> { entry ->
                            val args = entry.toRoute<ChatRoute>()
                            ChatScreen(
                                conversationId = args.conversationId,
                                peerId = args.peerId,
                                viewModel = viewModel,
                                onBack = { navController.popBackStack() },
                                onOpenProfile = { uid ->
                                    if (uid == viewModel.currentUserId) navigateTopTab(ProfileRoute)
                                    else navController.navigate(UserProfileRoute(uid))
                                }
                            )
                        }

                        composable<StoryViewerRoute> { entry ->
                            val args = entry.toRoute<StoryViewerRoute>()
                            StoryViewerScreen(
                                authorId = args.authorId,
                                viewModel = viewModel,
                                onClose = { navController.popBackStack() },
                                onOpenProfile = { uid ->
                                    navController.popBackStack()
                                    if (uid == viewModel.currentUserId) navigateTopTab(ProfileRoute)
                                    else navController.navigate(UserProfileRoute(uid))
                                }
                            )
                        }

                        composable<AdminPanelRoute> {
                            AdminPanelScreen(
                                viewModel = viewModel,
                                onBack = { navController.popBackStack() }
                            )
                        }
                    }
                }
            }
        }

        // Global Voice/Video Call Signaling Overlay
        activeCalls.firstOrNull()?.let { currentCall ->
            ActiveCallOverlay(
                call = currentCall,
                currentUserId = viewModel.currentUserId,
                viewModel = viewModel
            )
        }
    }
}
