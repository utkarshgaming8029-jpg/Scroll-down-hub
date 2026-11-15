package com.example.ui.navigation

import kotlinx.serialization.Serializable

@Serializable
data object HomeRoute

@Serializable
data class SearchRoute(val initialQuery: String = "")

@Serializable
data object ReelsRoute

@Serializable
data class CreateRoute(val initialMode: String = "POST")

@Serializable
data object ProfileRoute

@Serializable
data class UserProfileRoute(val userId: String)

@Serializable
data object NotificationsRoute

@Serializable
data object MessagesRoute

@Serializable
data class ChatRoute(val conversationId: String, val peerId: String = "")

@Serializable
data class StoryViewerRoute(val authorId: String)

@Serializable
data object AdminPanelRoute

@Serializable
data class SettingsRoute(val initialSection: String = "")

@Serializable
data object YourActivityRoute

@Serializable
data object ArchiveRoute

@Serializable
data object CreatorDashboardRoute

@Serializable
data object CloseFriendsRoute

@Serializable
data object MemoriesRoute

@Serializable
data object WatchHistoryRoute

@Serializable
data object ScheduledPostsRoute

@Serializable
data class LiveStreamRoute(val streamId: String = "")
