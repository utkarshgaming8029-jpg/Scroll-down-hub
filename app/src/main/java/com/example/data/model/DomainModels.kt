package com.example.data.model

import com.example.data.local.LiveStreamStatus
import com.example.data.local.PostAudience
import com.example.data.local.UserRoles

data class UserProfile(
    val id: String,
    val username: String,
    val displayName: String,
    val email: String,
    val bio: String,
    val avatarUri: String?,
    val role: String,
    val isGoldenVerified: Boolean,
    val isPrivate: Boolean,
    val isBanned: Boolean,
    val demoFollowersDisplay: Long,
    val actualFollowersCount: Long,
    val followingCount: Long,
    val postsCount: Long,
    val isFollowing: Boolean = false,
    val isFollowRequested: Boolean = false,
    val isFollowedByTarget: Boolean = false,
    val isBlockedByMe: Boolean = false,
    val isRestrictedByMe: Boolean = false,
    val isCloseFriend: Boolean = false,
    val isOnline: Boolean = false,
    val dateOfBirth: String = "",
    val twoFactorEnabled: Boolean = false,
    val backupCodes: List<String> = emptyList(),
    val hasSecurityPin: Boolean = false,
    val createdAt: Long = 0L
) {
    val isSuperAdmin: Boolean get() = role == UserRoles.SUPER_ADMIN
    val displayedFollowersCount: Long
        get() = if (demoFollowersDisplay > 0L) demoFollowersDisplay + actualFollowersCount else actualFollowersCount
}

data class PostUiModel(
    val id: String,
    val author: UserProfile,
    val caption: String,
    val mediaUris: List<String>,
    val mediaType: String,
    val hashtags: List<String>,
    val taggedUsernames: List<String>,
    val musicTitle: String,
    val likesCount: Long,
    val commentsCount: Long,
    val viewsCount: Long,
    val isLiked: Boolean,
    val isSaved: Boolean,
    val isReel: Boolean,
    val isArchived: Boolean = false,
    val scheduledAtMs: Long = 0L,
    val audience: String = PostAudience.ALL,
    val allowedViewerIds: List<String> = emptyList(),
    val createdAt: Long
) {
    val isScheduledPending: Boolean
        get() = scheduledAtMs > System.currentTimeMillis()
}

data class CommentUiModel(
    val id: String,
    val postId: String,
    val author: UserProfile,
    val parentCommentId: String?,
    val text: String,
    val createdAt: Long,
    val replies: List<CommentUiModel> = emptyList()
)

data class StoryItemUiModel(
    val id: String,
    val author: UserProfile,
    val mediaUri: String,
    val mediaType: String,
    val caption: String,
    val highlightTitle: String?,
    val audience: String = PostAudience.ALL,
    val viewsCount: Int,
    val myReaction: String?,
    val hasViewedByMe: Boolean = false,
    val viewers: List<StoryViewerDetail>,
    val isArchived: Boolean = false,
    val createdAt: Long,
    val expiresAt: Long
)

data class StoryViewerDetail(
    val user: UserProfile,
    val reactionEmoji: String?,
    val viewedAt: Long
)

data class StoryGroupUiModel(
    val author: UserProfile,
    val stories: List<StoryItemUiModel>,
    val hasUnseen: Boolean
)

data class NoteUiModel(
    val id: String,
    val author: UserProfile,
    val text: String,
    val musicTitle: String,
    val audience: String,
    val createdAt: Long,
    val expiresAt: Long
)

data class ConversationUiModel(
    val conversationId: String,
    val peer: UserProfile?,
    val participants: List<UserProfile> = emptyList(),
    val isGroup: Boolean = false,
    val groupName: String = "",
    val lastMessage: String,
    val lastSenderId: String? = null,
    val lastMessageMediaType: String,
    val lastMessageAt: Long,
    val unreadCount: Int,
    val isPeerTyping: Boolean
) {
    val displayTitle: String
        get() = if (isGroup) groupName.ifBlank { participants.joinToString(", ") { it.displayName }.take(28) }
        else peer?.displayName ?: "Direct Chat"
}

data class MessageUiModel(
    val id: String,
    val conversationId: String,
    val senderId: String,
    val recipientId: String,
    val text: String,
    val mediaUri: String?,
    val mediaType: String,
    val reactions: Map<String, String> = emptyMap(),
    val isSeen: Boolean,
    val createdAt: Long
)

data class CallUiModel(
    val id: String,
    val caller: UserProfile,
    val callee: UserProfile,
    val callType: String,
    val status: String,
    val createdAt: Long
)

data class LiveStreamUiModel(
    val id: String,
    val host: UserProfile,
    val title: String,
    val isLive: Boolean,
    val status: String = LiveStreamStatus.LIVE,
    val viewerCount: Long,
    val peakViewerCount: Long = viewerCount,
    val startedAt: Long,
    val endedAt: Long? = null
)

data class LiveStreamSummaryUiModel(
    val streamId: String,
    val title: String,
    val hostUsername: String,
    val durationSeconds: Long,
    val peakViewerCount: Long,
    val commentsCount: Int,
    val endedAt: Long
)

data class LiveCommentUiModel(
    val id: String,
    val author: UserProfile,
    val text: String,
    val createdAt: Long
)

data class VerificationRequestUiModel(
    val id: String,
    val userId: String,
    val username: String,
    val displayName: String,
    val category: String,
    val reason: String,
    val portfolioLink: String,
    val status: String,
    val adminNote: String?,
    val createdAt: Long
)

data class WatchHistoryUiModel(
    val post: PostUiModel,
    val watchedAt: Long
)

data class NotificationUiModel(
    val id: String,
    val actor: UserProfile,
    val type: String,
    val referenceId: String,
    val message: String,
    val isRead: Boolean,
    val isPendingFollowRequest: Boolean,
    val createdAt: Long
)

data class ReportUiModel(
    val id: String,
    val reporter: UserProfile,
    val targetType: String,
    val targetId: String,
    val targetSummary: String,
    val reason: String,
    val status: String,
    val createdAt: Long
)

data class LoginActivityUiModel(
    val id: String,
    val deviceModel: String,
    val authMethod: String,
    val createdAt: Long
)

data class SupportTicketUiModel(
    val id: String,
    val subject: String,
    val message: String,
    val status: String,
    val createdAt: Long
)

data class AdminAnalytics(
    val totalUsers: Long = 0L,
    val totalPosts: Long = 0L,
    val totalReels: Long = 0L,
    val totalComments: Long = 0L,
    val openReports: Long = 0L,
    val pendingVerifications: Long = 0L,
    val verifiedUsers: Long = 0L,
    val bannedUsers: Long = 0L,
    val activeLiveStreams: Long = 0L
)

data class CreatorAnalyticsUiModel(
    val totalPosts: Int = 0,
    val totalReels: Int = 0,
    val totalLikesReceived: Long = 0L,
    val totalCommentsReceived: Long = 0L,
    val totalVideoViews: Long = 0L,
    val engagementRatePercent: Double = 0.0,
    val topPosts: List<PostUiModel> = emptyList()
)

data class AchievementUiModel(
    val id: String,
    val title: String,
    val description: String,
    val isUnlocked: Boolean,
    val progressLabel: String
)
