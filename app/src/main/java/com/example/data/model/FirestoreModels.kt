package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.PropertyName
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class FirestoreUserDoc(
    @DocumentId val id: String = "",
    val userId: String = "",
    val username: String = "",
    val displayName: String = "",
    val bio: String = "",
    val avatarUrl: String? = null,
    val role: String = "USER",
    @get:PropertyName("isGoldenVerified") @field:PropertyName("isGoldenVerified")
    val isGoldenVerified: Boolean = false,
    @get:PropertyName("isPrivate") @field:PropertyName("isPrivate")
    val isPrivate: Boolean = false,
    @get:PropertyName("isBanned") @field:PropertyName("isBanned")
    val isBanned: Boolean = false,
    val demoFollowersDisplay: Long = 0L,
    val followersCount: Long = 0L,
    val followingCount: Long = 0L,
    val postsCount: Long = 0L,
    @ServerTimestamp val lastActiveAt: Timestamp? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class FirestorePrivateInfoDoc(
    @DocumentId val id: String = "",
    val userId: String = "",
    val email: String = "",
    val dateOfBirth: String = "",
    val twoFactorEnabled: Boolean = false,
    val backupCodes: List<String> = emptyList(),
    val securityPinHash: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class FirestoreLoginActivityDoc(
    @DocumentId val id: String = "",
    val userId: String = "",
    val deviceModel: String = "",
    val authMethod: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@IgnoreExtraProperties
data class FirestoreFollowDoc(
    @DocumentId val id: String = "",
    val followerId: String = "",
    val followingId: String = "",
    val status: String = "ACCEPTED",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class FirestorePostDoc(
    @DocumentId val id: String = "",
    val authorId: String = "",
    val caption: String = "",
    val mediaUrls: List<String> = emptyList(),
    val mediaType: String = "PHOTO",
    val hashtags: List<String> = emptyList(),
    val taggedUsernames: List<String> = emptyList(),
    val musicTitle: String = "",
    val likesCount: Long = 0L,
    val commentsCount: Long = 0L,
    val viewsCount: Long = 0L,
    @get:PropertyName("isReel") @field:PropertyName("isReel")
    val isReel: Boolean = false,
    @get:PropertyName("isArchived") @field:PropertyName("isArchived")
    val isArchived: Boolean = false,
    val scheduledAtMs: Long = 0L,
    val audience: String = "ALL",
    val allowedViewerIds: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class FirestoreCommentDoc(
    @DocumentId val id: String = "",
    val postId: String = "",
    val authorId: String = "",
    val parentCommentId: String? = null,
    val text: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@IgnoreExtraProperties
data class FirestoreStoryDoc(
    @DocumentId val id: String = "",
    val authorId: String = "",
    val mediaUrl: String = "",
    val mediaType: String = "PHOTO",
    val caption: String = "",
    val highlightTitle: String? = null,
    val audience: String = "ALL",
    val allowedViewerIds: List<String> = emptyList(),
    @get:PropertyName("isArchived") @field:PropertyName("isArchived")
    val isArchived: Boolean = false,
    val expiresAtMs: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null
)

@IgnoreExtraProperties
data class FirestoreStoryViewDoc(
    @DocumentId val id: String = "",
    val viewerId: String = "",
    val reactionEmoji: String? = null,
    @ServerTimestamp val viewedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class FirestoreNoteDoc(
    @DocumentId val id: String = "",
    val authorId: String = "",
    val text: String = "",
    val musicTitle: String = "",
    val audience: String = "ALL",
    val allowedViewerIds: List<String> = emptyList(),
    val expiresAtMs: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class FirestoreConversationDoc(
    @DocumentId val id: String = "",
    val participantIds: List<String> = emptyList(),
    @get:PropertyName("isGroup") @field:PropertyName("isGroup")
    val isGroup: Boolean = false,
    val groupName: String? = null,
    val groupAdminId: String? = null,
    val lastSenderId: String? = null,
    val lastReadMs: Map<String, Long> = emptyMap(),
    val lastMessage: String = "",
    val lastMessageMediaType: String = "TEXT",
    val typingUids: List<String> = emptyList(),
    @ServerTimestamp val lastMessageAt: Timestamp? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class FirestoreMessageDoc(
    @DocumentId val id: String = "",
    val participantIds: List<String> = emptyList(),
    val senderId: String = "",
    val recipientId: String = "",
    val text: String = "",
    val mediaUrl: String? = null,
    val mediaType: String = "TEXT",
    val reactions: Map<String, String> = emptyMap(),
    @get:PropertyName("isSeen") @field:PropertyName("isSeen")
    val isSeen: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null
)

@IgnoreExtraProperties
data class FirestoreCallDoc(
    @DocumentId val id: String = "",
    val callerId: String = "",
    val calleeId: String = "",
    val participantIds: List<String> = emptyList(),
    val callType: String = "VOICE",
    val status: String = "RINGING",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class FirestoreLiveStreamDoc(
    @DocumentId val id: String = "",
    val hostId: String = "",
    val title: String = "",
    @get:PropertyName("isLive") @field:PropertyName("isLive")
    val isLive: Boolean = true,
    val status: String = "LIVE",
    val viewerIds: List<String> = emptyList(),
    val viewerCount: Long = 0L,
    val peakViewerCount: Long = 1L,
    @ServerTimestamp val startedAt: Timestamp? = null,
    @ServerTimestamp val endedAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class FirestoreLiveCommentDoc(
    @DocumentId val id: String = "",
    val streamId: String = "",
    val authorId: String = "",
    val text: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@IgnoreExtraProperties
data class FirestoreVerificationRequestDoc(
    @DocumentId val id: String = "",
    val userId: String = "",
    val username: String = "",
    val displayName: String = "",
    val category: String = "CREATOR",
    val reason: String = "",
    val portfolioLink: String = "",
    val status: String = "PENDING",
    val adminNote: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class FirestoreWatchHistoryDoc(
    @DocumentId val id: String = "",
    val postId: String = "",
    val mediaType: String = "REEL",
    @ServerTimestamp val watchedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class FirestoreNotificationDoc(
    @DocumentId val id: String = "",
    val recipientId: String = "",
    val actorId: String = "",
    val type: String = "",
    val referenceId: String = "",
    val message: String = "",
    @get:PropertyName("isRead") @field:PropertyName("isRead")
    val isRead: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null
)

@IgnoreExtraProperties
data class FirestoreReportDoc(
    @DocumentId val id: String = "",
    val reporterId: String = "",
    val targetType: String = "",
    val targetId: String = "",
    val targetSummary: String = "",
    val reason: String = "",
    val status: String = "OPEN",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class FirestoreSupportTicketDoc(
    @DocumentId val id: String = "",
    val userId: String = "",
    val subject: String = "",
    val message: String = "",
    val status: String = "OPEN",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@IgnoreExtraProperties
data class FirestoreBlockDoc(
    @DocumentId val id: String = "",
    val targetId: String = "",
    val type: String = "BLOCKED",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@IgnoreExtraProperties
data class FirestoreSavedPostDoc(
    @DocumentId val id: String = "",
    val postId: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)
