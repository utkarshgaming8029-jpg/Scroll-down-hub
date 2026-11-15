package com.example.ui.main

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ai.AiCaptionSuggestionResult
import com.example.data.ai.FirebaseAiRepository
import com.example.data.local.*
import com.example.data.media.VoiceAudioHelper
import com.example.data.model.*
import com.example.data.repository.FirestoreAccountRepository
import com.example.data.repository.FirestoreEngagementRepository
import com.example.data.repository.FirestoreLiveAndVerificationRepository
import com.example.data.repository.FirestoreSocialRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(
    val currentUserId: String,
    private val accountRepository: FirestoreAccountRepository,
    private val socialRepository: FirestoreSocialRepository,
    private val engagementRepository: FirestoreEngagementRepository,
    private val liveAndVerificationRepository: FirestoreLiveAndVerificationRepository,
    private val aiRepository: FirebaseAiRepository,
    private val voiceAudioHelper: VoiceAudioHelper,
    private val onSignOutAction: (Context) -> Unit
) : ViewModel() {

    private val feedPageLimit = MutableStateFlow(12)

    private val _feedback = MutableSharedFlow<String>(extraBufferCapacity = 10)
    val feedback: SharedFlow<String> = _feedback.asSharedFlow()

    private val _isLiveActionInProgress = MutableStateFlow(false)
    val isLiveActionInProgress: StateFlow<Boolean> = _isLiveActionInProgress.asStateFlow()

    private val _liveActionError = MutableStateFlow<String?>(null)
    val liveActionError: StateFlow<String?> = _liveActionError.asStateFlow()

    private val _activeHostedStreamId = MutableStateFlow<String?>(null)
    val activeHostedStreamId: StateFlow<String?> = _activeHostedStreamId.asStateFlow()

    private val _endedLiveSummary = MutableStateFlow<LiveStreamSummaryUiModel?>(null)
    val endedLiveSummary: StateFlow<LiveStreamSummaryUiModel?> = _endedLiveSummary.asStateFlow()

    private val _verifyingUserIds = MutableStateFlow<Set<String>>(emptySet())
    val verifyingUserIds: StateFlow<Set<String>> = _verifyingUserIds.asStateFlow()

    private val _deletingStoryId = MutableStateFlow<String?>(null)
    val deletingStoryId: StateFlow<String?> = _deletingStoryId.asStateFlow()

    init {
        viewModelScope.launch {
            liveAndVerificationRepository.endOrphanedLiveStreamsForHost(currentUserId)
        }
    }

    val appPreferences: StateFlow<AppPreferences> = accountRepository.preferences
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppPreferences())

    val allUsers: StateFlow<List<UserProfile>> = socialRepository.observeAllUserProfiles(currentUserId)
        .catch { if (it is CancellationException) throw it; emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val currentUser: StateFlow<UserProfile?> = allUsers
        .map { users -> users.firstOrNull { it.id == currentUserId } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val followRelations: StateFlow<List<FollowEntity>> = socialRepository.observeFollowRelations(currentUserId)
        .catch { if (it is CancellationException) throw it; emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val savedPostIds: StateFlow<Set<String>> = socialRepository.observeSavedPostIds(currentUserId)
        .catch { if (it is CancellationException) throw it; emit(emptySet()) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    val likedPostIds: StateFlow<Set<String>> = socialRepository.observeLikedPostIds(currentUserId)
        .catch { if (it is CancellationException) throw it; emit(emptySet()) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    val allPostsWithArchive: StateFlow<List<PostUiModel>> = socialRepository.observeAllPostsUi(
        viewerId = currentUserId,
        includeArchived = true,
        includeFutureScheduledForOwner = true,
        sharedProfilesFlow = allUsers,
        sharedLikedIdsFlow = likedPostIds,
        sharedSavedIdsFlow = savedPostIds
    )
        .catch { if (it is CancellationException) throw it; emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val allPosts: StateFlow<List<PostUiModel>> = allPostsWithArchive
        .map { list ->
            val now = System.currentTimeMillis()
            list.filter { !it.isArchived && it.scheduledAtMs <= now }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val homeFeed: StateFlow<List<PostUiModel>> = combine(
        allPosts,
        followRelations,
        feedPageLimit
    ) { posts, follows, limit ->
        val followingIds = follows
            .filter { it.followerId == currentUserId && it.status == FollowStatus.ACCEPTED }
            .map { it.followingId }
            .toSet()
        val prioritized = posts.filter { post ->
            post.author.id == currentUserId ||
                post.author.id in followingIds ||
                post.author.isSuperAdmin
        }
        (if (prioritized.isNotEmpty()) prioritized else posts).take(limit)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val hasMoreFeedPosts: StateFlow<Boolean> = combine(
        allPosts,
        followRelations,
        feedPageLimit
    ) { posts, follows, limit ->
        val followingIds = follows
            .filter { it.followerId == currentUserId && it.status == FollowStatus.ACCEPTED }
            .map { it.followingId }
            .toSet()
        val prioritizedCount = posts.count { post ->
            post.author.id == currentUserId ||
                post.author.id in followingIds ||
                post.author.isSuperAdmin
        }
        val baseSize = if (prioritizedCount > 0) prioritizedCount else posts.size
        baseSize > limit
    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val scheduledPosts: StateFlow<List<PostUiModel>> = allPostsWithArchive
        .map { list ->
            val now = System.currentTimeMillis()
            list.filter { !it.isArchived && it.author.id == currentUserId && it.scheduledAtMs > now }
                .sortedBy { it.scheduledAtMs }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val reels: StateFlow<List<PostUiModel>> = allPosts
        .map { posts -> posts.filter { it.isReel } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val storyGroups: StateFlow<List<StoryGroupUiModel>> = socialRepository.observeActiveStoryGroups(
        viewerId = currentUserId,
        sharedProfilesFlow = allUsers
    )
        .catch { if (it is CancellationException) throw it; emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val myStoriesArchive: StateFlow<List<StoryItemUiModel>> = socialRepository.observeAllStoriesForUser(
        userId = currentUserId,
        sharedProfilesFlow = allUsers
    )
        .catch { if (it is CancellationException) throw it; emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val activeNotes: StateFlow<List<NoteUiModel>> = socialRepository.observeActiveNotes(
        viewerId = currentUserId,
        sharedProfilesFlow = allUsers
    )
        .catch { if (it is CancellationException) throw it; emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val watchHistory: StateFlow<List<WatchHistoryUiModel>> = socialRepository.observeWatchHistory(
        viewerId = currentUserId,
        sharedPostsFlow = allPosts
    )
        .catch { if (it is CancellationException) throw it; emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val memories: StateFlow<List<PostUiModel>> = allPostsWithArchive
        .map { list ->
            list.filter { it.author.id == currentUserId && !it.isScheduledPending }
                .sortedBy { it.createdAt }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val conversations: StateFlow<List<ConversationUiModel>> = engagementRepository.observeConversations(
        viewerId = currentUserId,
        sharedProfilesFlow = allUsers
    )
        .catch { if (it is CancellationException) throw it; emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val activeCalls: StateFlow<List<CallUiModel>> = liveAndVerificationRepository.observeActiveCalls(
        uid = currentUserId,
        sharedProfilesFlow = allUsers
    )
        .catch { if (it is CancellationException) throw it; emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val activeLiveStreams: StateFlow<List<LiveStreamUiModel>> = liveAndVerificationRepository.observeActiveLiveStreams(
        viewerId = currentUserId,
        sharedProfilesFlow = allUsers
    )
        .catch { if (it is CancellationException) throw it; emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val myVerificationRequests: StateFlow<List<VerificationRequestUiModel>> =
        liveAndVerificationRepository.observeMyVerificationRequests(currentUserId)
            .catch { if (it is CancellationException) throw it; emit(emptyList()) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val allVerificationRequests: StateFlow<List<VerificationRequestUiModel>> =
        liveAndVerificationRepository.observeAllVerificationRequests(
            viewerId = currentUserId,
            sharedProfilesFlow = allUsers
        )
            .catch { if (it is CancellationException) throw it; emit(emptyList()) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val notifications: StateFlow<List<NotificationUiModel>> = engagementRepository.observeNotifications(
        uid = currentUserId,
        sharedProfilesFlow = allUsers,
        sharedFollowsFlow = followRelations
    )
        .catch { if (it is CancellationException) throw it; emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val unreadNotificationsCount: StateFlow<Int> = notifications
        .map { list -> list.count { !it.isRead } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    val unreadMessagesCount: StateFlow<Int> = conversations
        .map { list -> list.sumOf { it.unreadCount } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    val loginActivity: StateFlow<List<LoginActivityUiModel>> = accountRepository.observeLoginActivity(currentUserId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val supportTickets: StateFlow<List<SupportTicketUiModel>> = accountRepository.observeSupportTickets(currentUserId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val adminReports: StateFlow<List<ReportUiModel>> = engagementRepository.observeAdminReports(
        viewerId = currentUserId,
        sharedProfilesFlow = allUsers
    )
        .catch { if (it is CancellationException) throw it; emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val adminAnalytics: StateFlow<AdminAnalytics> = combine(
        engagementRepository.observeAdminAnalytics(
            viewerId = currentUserId,
            sharedProfilesFlow = allUsers,
            sharedPostsFlow = allPostsWithArchive,
            sharedReportsFlow = adminReports
        ).catch { if (it is CancellationException) throw it; emit(AdminAnalytics()) },
        allVerificationRequests,
        activeLiveStreams
    ) { base, verReqs, streams ->
        base.copy(
            pendingVerifications = verReqs.count { it.status == VerificationStatus.PENDING }.toLong(),
            activeLiveStreams = streams.size.toLong()
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, AdminAnalytics())

    val creatorAnalytics: StateFlow<CreatorAnalyticsUiModel> = combine(allPostsWithArchive, currentUser) { posts, me ->
        val mine = posts.filter { it.author.id == currentUserId && !it.isScheduledPending }
        val totalLikes = mine.sumOf { it.likesCount }
        val totalComments = mine.sumOf { it.commentsCount }
        val totalViews = mine.sumOf { it.viewsCount }
        val followers = (me?.actualFollowersCount ?: 0L).coerceAtLeast(1L)
        val engagement = ((totalLikes + totalComments).toDouble() / followers.toDouble()) * 100.0
        CreatorAnalyticsUiModel(
            totalPosts = mine.count { !it.isReel },
            totalReels = mine.count { it.isReel },
            totalLikesReceived = totalLikes,
            totalCommentsReceived = totalComments,
            totalVideoViews = totalViews,
            engagementRatePercent = engagement.coerceAtMost(999.0),
            topPosts = mine.sortedByDescending { it.likesCount + it.viewsCount + it.commentsCount }.take(5)
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, CreatorAnalyticsUiModel())

    val creatorAchievements: StateFlow<List<AchievementUiModel>> = combine(
        creatorAnalytics,
        currentUser,
        myStoriesArchive
    ) { stats, me, stories ->
        val totalMedia = stats.totalPosts + stats.totalReels
        val followers = me?.actualFollowersCount ?: 0L
        listOf(
            AchievementUiModel(
                id = "first_post",
                title = "Debut Creator",
                description = "Publish your first photo, carousel, or Reel on SCROLL DOWN.",
                isUnlocked = totalMedia >= 1,
                progressLabel = "${totalMedia.coerceAtMost(1)}/1"
            ),
            AchievementUiModel(
                id = "prolific_5",
                title = "Golden Gallery",
                description = "Publish at least 5 posts or Reels.",
                isUnlocked = totalMedia >= 5,
                progressLabel = "${totalMedia.coerceAtMost(5)}/5"
            ),
            AchievementUiModel(
                id = "likes_10",
                title = "Audience Spark",
                description = "Earn 10 total likes across your content.",
                isUnlocked = stats.totalLikesReceived >= 10,
                progressLabel = "${stats.totalLikesReceived.coerceAtMost(10)}/10"
            ),
            AchievementUiModel(
                id = "stories_3",
                title = "Daily Storyteller",
                description = "Share 3 Stories or Highlights.",
                isUnlocked = stories.size >= 3,
                progressLabel = "${stories.size.coerceAtMost(3)}/3"
            ),
            AchievementUiModel(
                id = "followers_5",
                title = "Rising Influence",
                description = "Reach 5 organic followers or hold Golden Verification.",
                isUnlocked = followers >= 5 || me?.isGoldenVerified == true,
                progressLabel = if (me?.isGoldenVerified == true) "Verified ★" else "${followers.coerceAtMost(5)}/5"
            )
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun loadMoreFeed() {
        if (hasMoreFeedPosts.value) {
            feedPageLimit.value += 10
        }
    }

    fun observeComments(postId: String): Flow<List<CommentUiModel>> =
        engagementRepository.observeCommentsForPost(postId, currentUserId, sharedProfilesFlow = allUsers)
            .catch { if (it is CancellationException) throw it; emit(emptyList()) }

    fun observeHighlights(userId: String): Flow<List<StoryItemUiModel>> =
        socialRepository.observeUserHighlights(userId, currentUserId, sharedProfilesFlow = allUsers)
            .catch { if (it is CancellationException) throw it; emit(emptyList()) }

    fun observeStoryViewers(storyId: String): Flow<List<StoryViewerDetail>> =
        socialRepository.observeStoryViewers(storyId, currentUserId, sharedProfilesFlow = allUsers)
            .catch { if (it is CancellationException) throw it; emit(emptyList()) }

    fun observeConversation(convId: String): Flow<ConversationUiModel?> =
        engagementRepository.observeConversation(convId, currentUserId, sharedConversationsFlow = conversations)
            .catch { if (it is CancellationException) throw it; emit(null) }

    fun observeMessagesByConvId(convId: String): Flow<List<MessageUiModel>> =
        engagementRepository.observeMessages(convId, currentUserId)
            .catch { if (it is CancellationException) throw it; emit(emptyList()) }

    fun observeLiveComments(streamId: String): Flow<List<LiveCommentUiModel>> =
        liveAndVerificationRepository.observeLiveComments(streamId, currentUserId, sharedProfilesFlow = allUsers)
            .catch { if (it is CancellationException) throw it; emit(emptyList()) }

    fun observeLiveStreamById(streamId: String): Flow<LiveStreamUiModel?> =
        liveAndVerificationRepository.observeLiveStreamById(streamId, currentUserId, sharedProfilesFlow = allUsers)
            .catch { if (it is CancellationException) throw it; emit(null) }

    fun getFollowersFor(userId: String): List<UserProfile> {
        val followerIds = followRelations.value
            .filter { it.followingId == userId && it.status == FollowStatus.ACCEPTED }
            .map { it.followerId }
            .toSet()
        return allUsers.value.filter { it.id in followerIds }
    }

    fun getFollowingFor(userId: String): List<UserProfile> {
        val followingIds = followRelations.value
            .filter { it.followerId == userId && it.status == FollowStatus.ACCEPTED }
            .map { it.followingId }
            .toSet()
        return allUsers.value.filter { it.id in followingIds }
    }

    fun getMutualFollowers(targetUserId: String): List<UserProfile> {
        val myFollowing = followRelations.value
            .filter { it.followerId == currentUserId && it.status == FollowStatus.ACCEPTED }
            .map { it.followingId }.toSet()
        val targetFollowers = followRelations.value
            .filter { it.followingId == targetUserId && it.status == FollowStatus.ACCEPTED }
            .map { it.followerId }.toSet()
        val mutualIds = myFollowing intersect targetFollowers
        return allUsers.value.filter { it.id in mutualIds }
    }

    private fun launchResult(block: suspend () -> Result<Any?>, successMsg: String? = null) {
        viewModelScope.launch {
            val result = block()
            if (result.isSuccess) {
                successMsg?.let { _feedback.tryEmit(it) }
            } else {
                _feedback.tryEmit(result.exceptionOrNull()?.message ?: "Operation failed.")
            }
        }
    }

    fun toggleLike(postId: String) = launchResult({ socialRepository.toggleLike(postId) })

    fun toggleSave(postId: String) = launchResult({ socialRepository.toggleSavePost(postId) })

    fun setPostArchived(postId: String, archived: Boolean) = launchResult(
        { socialRepository.toggleArchivePost(postId, archived) },
        if (archived) "Post moved to Archive." else "Post restored to Profile."
    )

    fun setStoryArchived(storyId: String, archived: Boolean) = launchResult(
        { socialRepository.toggleArchiveStory(storyId, archived) },
        if (archived) "Story moved to Archive." else "Story restored."
    )

    fun addComment(postId: String, text: String, parentCommentId: String? = null) =
        launchResult({ engagementRepository.addComment(postId, text, parentCommentId) })

    fun incrementReelView(postId: String) {
        viewModelScope.launch { socialRepository.incrementReelView(postId) }
    }

    fun recordWatchHistory(postId: String, mediaType: String) {
        viewModelScope.launch { socialRepository.recordWatchHistory(postId, mediaType) }
    }

    fun clearWatchHistory() =
        launchResult({ socialRepository.clearWatchHistory() }, "Watch history cleared.")

    fun toggleCloseFriend(targetId: String) =
        launchResult({ socialRepository.toggleCloseFriend(targetId) }, "Close Friends list updated.")

    fun publishNote(text: String, musicTitle: String, audience: String, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            val res = socialRepository.publishNote(text, musicTitle, audience)
            if (res.isSuccess) {
                _feedback.tryEmit("Note shared for 24h.")
                onDone()
            } else {
                _feedback.tryEmit(res.exceptionOrNull()?.message ?: "Could not share note.")
            }
        }
    }

    fun deleteOwnNote() = launchResult({ socialRepository.deleteOwnNote() }, "Note removed.")

    fun publishScheduledPostNow(postId: String) =
        launchResult({ socialRepository.publishScheduledPostNow(postId) }, "Scheduled post published now!")

    fun toggleFollow(targetUserId: String) =
        launchResult({ socialRepository.toggleFollow(targetUserId) })

    fun respondToFollowRequest(requesterId: String, accept: Boolean) =
        launchResult(
            { socialRepository.respondToFollowRequest(requesterId, accept) },
            if (accept) "Follow request accepted." else "Follow request declined."
        )

    fun createPostOrReel(
        caption: String,
        mediaUris: List<Uri>,
        isVideo: Boolean,
        isReel: Boolean,
        musicTitle: String,
        scheduledAtMs: Long = 0L,
        audience: String = PostAudience.ALL,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            val res = socialRepository.createPostOrReel(
                caption = caption,
                mediaUris = mediaUris,
                isVideo = isVideo,
                isReel = isReel,
                musicTitle = musicTitle,
                scheduledAtMs = scheduledAtMs,
                audience = audience
            )
            if (res.isSuccess) {
                val msg = when {
                    scheduledAtMs > System.currentTimeMillis() -> "Post scheduled!"
                    isReel -> "Reel published!"
                    else -> "Post shared!"
                }
                _feedback.tryEmit(msg)
                onSuccess()
            } else {
                _feedback.tryEmit(res.exceptionOrNull()?.message ?: "Failed to publish.")
            }
        }
    }

    fun createStory(
        mediaUri: Uri,
        isVideo: Boolean,
        caption: String,
        highlightTitle: String?,
        audience: String = PostAudience.ALL,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            val res = socialRepository.createStory(mediaUri, isVideo, caption, highlightTitle, audience)
            if (res.isSuccess) {
                _feedback.tryEmit("Story live for 24 hours!")
                onSuccess()
            } else {
                _feedback.tryEmit(res.exceptionOrNull()?.message ?: "Failed to post story.")
            }
        }
    }

    fun recordStoryView(storyId: String, reactionEmoji: String? = null) {
        viewModelScope.launch { socialRepository.recordStoryView(storyId, reactionEmoji) }
    }

    fun replyToStory(authorId: String, storyCaption: String, replyText: String) {
        launchResult(
            {
                val prefix = if (storyCaption.isNotBlank()) "Replied to story (\"$storyCaption\"): " else "Replied to your story: "
                engagementRepository.sendMessage(authorId, "$prefix$replyText")
            },
            "Reply sent to direct messages."
        )
    }

    fun addStoryToHighlight(storyId: String, title: String) =
        launchResult({ socialRepository.toggleStoryHighlight(storyId, title) }, "Added to Highlights.")

    fun deleteStory(
        storyId: String,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        if (_deletingStoryId.value != null) return
        viewModelScope.launch {
            _deletingStoryId.value = storyId
            try {
                val res = withTimeout(12_000L) {
                    socialRepository.deleteStory(storyId)
                }
                if (res.isSuccess) {
                    _feedback.tryEmit("Story deleted.")
                    onSuccess()
                } else {
                    val msg = res.exceptionOrNull()?.message ?: "Could not delete story."
                    _feedback.tryEmit(msg)
                    onError(msg)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val msg = e.message ?: "Story deletion timed out. Check connection and retry."
                _feedback.tryEmit(msg)
                onError(msg)
            } finally {
                if (_deletingStoryId.value == storyId) {
                    _deletingStoryId.value = null
                }
            }
        }
    }

    // --- Messaging, Group Chats, Voice Notes & Reactions ---
    fun sendMessageToConversation(
        conversationId: String,
        directPeerId: String? = null,
        text: String,
        mediaUri: Uri? = null,
        isVideo: Boolean = false,
        voiceFile: File? = null
    ) = launchResult({
        engagementRepository.sendMessageToConversation(
            conversationId = conversationId,
            directPeerId = directPeerId,
            text = text,
            mediaUri = mediaUri,
            isVideo = isVideo,
            voiceFile = voiceFile
        )
    })

    fun createGroupConversation(groupName: String, memberIds: List<String>, onCreated: (String) -> Unit) {
        viewModelScope.launch {
            val res = engagementRepository.createGroupConversation(groupName, memberIds)
            if (res.isSuccess) {
                onCreated(res.getOrThrow())
            } else {
                _feedback.tryEmit(res.exceptionOrNull()?.message ?: "Could not create group.")
            }
        }
    }

    fun toggleMessageReaction(conversationId: String, messageId: String, emoji: String) =
        launchResult({ engagementRepository.toggleMessageReaction(conversationId, messageId, emoji) })

    fun setTypingByConvId(conversationId: String, typing: Boolean) {
        viewModelScope.launch { engagementRepository.setTypingStatusByConvId(conversationId, typing) }
    }

    fun markConversationReadByConvId(conversationId: String) {
        viewModelScope.launch { engagementRepository.markConversationReadByConvId(conversationId) }
    }

    fun startVoiceRecording(): Result<File> = voiceAudioHelper.startRecording()

    fun stopVoiceRecording(): Pair<File, Int>? = voiceAudioHelper.stopRecording()

    fun toggleVoicePlayback(uriOrDataUri: String, onFinished: () -> Unit): Boolean =
        voiceAudioHelper.togglePlayback(uriOrDataUri, onFinished)

    fun stopVoicePlayback() = voiceAudioHelper.stopPlayback()

    // --- Voice & Video Calls ---
    fun startCall(calleeId: String, callType: String) =
        launchResult({ liveAndVerificationRepository.startCall(calleeId, callType) })

    fun respondToCall(callId: String, accept: Boolean) =
        launchResult({ liveAndVerificationRepository.respondToCall(callId, accept) })

    fun endCall(callId: String) =
        launchResult({ liveAndVerificationRepository.endCall(callId) })

    // --- Live Streaming ---
    fun clearLiveActionError() {
        _liveActionError.value = null
    }

    fun clearEndedLiveSummary() {
        _endedLiveSummary.value = null
    }

    fun startLiveStream(title: String, onStarted: (String) -> Unit) {
        if (_isLiveActionInProgress.value) return
        viewModelScope.launch {
            _isLiveActionInProgress.value = true
            _liveActionError.value = null
            _endedLiveSummary.value = null
            try {
                val res = withTimeout(12_000L) {
                    liveAndVerificationRepository.startLiveStream(title)
                }
                if (res.isSuccess) {
                    val newId = res.getOrThrow()
                    _activeHostedStreamId.value = newId
                    _feedback.tryEmit("You are LIVE!")
                    onStarted(newId)
                } else {
                    val msg = res.exceptionOrNull()?.message ?: "Could not start live stream."
                    _liveActionError.value = msg
                    _feedback.tryEmit(msg)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val msg = e.message ?: "Live broadcast timed out. Check connection and retry."
                _liveActionError.value = msg
                _feedback.tryEmit(msg)
            } finally {
                _isLiveActionInProgress.value = false
            }
        }
    }

    fun joinLiveStream(streamId: String, onEnded: () -> Unit = {}) {
        viewModelScope.launch {
            val res = liveAndVerificationRepository.joinLiveStream(streamId)
            if (res.isFailure) {
                _feedback.tryEmit(res.exceptionOrNull()?.message ?: "This live broadcast has ended.")
                onEnded()
            }
        }
    }

    fun leaveLiveStream(streamId: String) {
        viewModelScope.launch { liveAndVerificationRepository.leaveLiveStream(streamId) }
    }

    fun endLiveStream(streamId: String, onEnded: (LiveStreamSummaryUiModel) -> Unit = {}) {
        if (_isLiveActionInProgress.value) return
        viewModelScope.launch {
            _isLiveActionInProgress.value = true
            _liveActionError.value = null
            voiceAudioHelper.stopPlayback()
            try {
                val res = withTimeout(12_000L) {
                    liveAndVerificationRepository.endLiveStream(streamId)
                }
                if (res.isSuccess) {
                    val summary = res.getOrThrow()
                    if (_activeHostedStreamId.value == streamId) {
                        _activeHostedStreamId.value = null
                    }
                    _endedLiveSummary.value = summary
                    _feedback.tryEmit("Live stream ended.")
                    onEnded(summary)
                } else {
                    val msg = res.exceptionOrNull()?.message ?: "Failed to end live stream. Check connection and retry."
                    _liveActionError.value = msg
                    _feedback.tryEmit(msg)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val msg = e.message ?: "Ending live stream timed out. Tap Retry to finish ending."
                _liveActionError.value = msg
                _feedback.tryEmit(msg)
            } finally {
                _isLiveActionInProgress.value = false
            }
        }
    }

    fun sendLiveComment(streamId: String, text: String) =
        launchResult({ liveAndVerificationRepository.sendLiveComment(streamId, text) })

    // --- Golden Verification Requests ---
    fun submitVerificationRequest(category: String, reason: String, portfolioLink: String, onDone: () -> Unit) {
        viewModelScope.launch {
            val res = liveAndVerificationRepository.submitVerificationRequest(category, reason, portfolioLink)
            if (res.isSuccess) {
                _feedback.tryEmit("Verification request submitted for Super Admin review.")
                onDone()
            } else {
                _feedback.tryEmit(res.exceptionOrNull()?.message ?: "Could not submit request.")
            }
        }
    }

    fun adminReviewVerificationRequest(
        requestId: String,
        targetUserId: String,
        approved: Boolean,
        note: String? = null
    ) = launchResult(
        { liveAndVerificationRepository.adminReviewVerificationRequest(requestId, targetUserId, approved, note) },
        if (approved) "Golden Verification approved!" else "Verification request declined."
    )

    // --- Firebase AI Logic Features ---
    fun suggestCaptionsWithAi(
        draftOrTopic: String,
        isReel: Boolean,
        isStory: Boolean,
        onResult: (Result<AiCaptionSuggestionResult>) -> Unit
    ) {
        viewModelScope.launch {
            onResult(aiRepository.suggestCaptionsAndHashtags(draftOrTopic, isReel, isStory))
        }
    }

    fun translatePostCaption(
        sourceText: String,
        targetLanguageCode: String,
        onResult: (Result<String>) -> Unit
    ) {
        val languageLabel = when (targetLanguageCode) {
            "hi" -> "Hindi"
            "es" -> "Spanish"
            else -> "English"
        }
        viewModelScope.launch {
            onResult(aiRepository.translateContent(sourceText, languageLabel))
        }
    }

    fun markAllNotificationsRead() {
        viewModelScope.launch { engagementRepository.markAllNotificationsRead() }
    }

    fun updateProfile(displayName: String, bio: String, isPrivate: Boolean, newAvatarUri: Uri?, onDone: () -> Unit) {
        viewModelScope.launch {
            val res = socialRepository.updateProfile(displayName, bio, isPrivate, newAvatarUri)
            if (res.isSuccess) {
                _feedback.tryEmit("Profile updated.")
                onDone()
            } else {
                _feedback.tryEmit(res.exceptionOrNull()?.message ?: "Update failed.")
            }
        }
    }

    fun setPrivateAccount(isPrivate: Boolean) {
        val current = currentUser.value ?: return
        launchResult(
            { socialRepository.updateProfile(current.displayName, current.bio, isPrivate, null) },
            if (isPrivate) "Account set to Private." else "Account set to Public."
        )
    }

    fun toggleBlock(targetId: String) =
        launchResult({ socialRepository.toggleBlockOrRestrict(targetId, BlockType.BLOCKED) }, "Block status updated.")

    fun toggleRestrict(targetId: String) =
        launchResult({ socialRepository.toggleBlockOrRestrict(targetId, BlockType.RESTRICTED) }, "Restrict status updated.")

    fun reportContent(targetType: String, targetId: String, reason: String) =
        launchResult({ engagementRepository.submitReport(targetType, targetId, reason) }, "Report submitted for admin review.")

    fun deletePost(postId: String) =
        launchResult({ socialRepository.deleteOwnPost(postId) }, "Post deleted.")

    // --- Settings & Security Controls ---
    fun setDarkTheme(isDark: Boolean) {
        viewModelScope.launch { accountRepository.setDarkTheme(isDark) }
    }

    fun setLanguageCode(code: String) {
        viewModelScope.launch {
            accountRepository.setLanguageCode(code)
            _feedback.tryEmit("Language updated.")
        }
    }

    fun setNotificationSettings(
        likes: Boolean? = null,
        comments: Boolean? = null,
        follows: Boolean? = null,
        messages: Boolean? = null,
        admin: Boolean? = null
    ) {
        viewModelScope.launch {
            accountRepository.setNotificationSettings(likes, comments, follows, messages, admin)
        }
    }

    fun toggleTwoFactor(enabled: Boolean) = launchResult(
        { accountRepository.toggleTwoFactor(enabled) },
        if (enabled) "Two-Factor Authentication enabled with backup codes." else "Two-Factor Authentication disabled."
    )

    fun updateSecurityPin(newPin: String, onDone: () -> Unit) {
        viewModelScope.launch {
            val res = accountRepository.updateSecurityPin(newPin)
            if (res.isSuccess) {
                _feedback.tryEmit("Security passcode updated.")
                onDone()
            } else {
                _feedback.tryEmit(res.exceptionOrNull()?.message ?: "Failed to update passcode.")
            }
        }
    }

    fun verifySecurityChallenge(inputCode: String, onResult: (Result<String>) -> Unit) {
        viewModelScope.launch {
            onResult(accountRepository.verifySecurityChallenge(inputCode))
        }
    }

    fun submitSupportTicket(subject: String, message: String, onDone: () -> Unit) {
        viewModelScope.launch {
            val res = accountRepository.submitSupportTicket(subject, message)
            if (res.isSuccess) {
                _feedback.tryEmit("Support request submitted.")
                onDone()
            } else {
                _feedback.tryEmit(res.exceptionOrNull()?.message ?: "Failed to submit ticket.")
            }
        }
    }

    fun exportUserDataJson(onReady: (String) -> Unit) {
        viewModelScope.launch {
            val res = accountRepository.exportUserInformationJson()
            if (res.isSuccess) {
                onReady(res.getOrThrow())
            } else {
                _feedback.tryEmit(res.exceptionOrNull()?.message ?: "Export failed.")
            }
        }
    }

    fun logout(context: Context) {
        viewModelScope.launch {
            _activeHostedStreamId.value?.let { hostedId ->
                liveAndVerificationRepository.endLiveStream(hostedId)
                _activeHostedStreamId.value = null
            }
            liveAndVerificationRepository.endOrphanedLiveStreamsForHost(currentUserId)
            onSignOutAction(context)
        }
    }

    fun deleteAccount(context: Context) {
        viewModelScope.launch {
            _activeHostedStreamId.value?.let { hostedId ->
                liveAndVerificationRepository.endLiveStream(hostedId)
                _activeHostedStreamId.value = null
            }
            val res = accountRepository.deleteAccount()
            if (res.isSuccess) {
                onSignOutAction(context)
            } else {
                _feedback.tryEmit(res.exceptionOrNull()?.message ?: "Deletion failed.")
            }
        }
    }

    // --- Super Admin Operations ---
    fun adminSetVerification(targetUserId: String, verified: Boolean) {
        if (targetUserId in _verifyingUserIds.value) return
        viewModelScope.launch {
            _verifyingUserIds.update { it + targetUserId }
            try {
                val res = withTimeout(12_000L) {
                    engagementRepository.adminSetGoldenVerification(targetUserId, verified)
                }
                if (res.isSuccess) {
                    _feedback.tryEmit(if (verified) "Golden Verification enabled." else "Golden Verification removed.")
                } else {
                    _feedback.tryEmit(res.exceptionOrNull()?.message ?: "Failed to update Golden Verification.")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _feedback.tryEmit(e.message ?: "Verification update timed out. Retry.")
            } finally {
                _verifyingUserIds.update { it - targetUserId }
            }
        }
    }

    fun adminSetBan(targetUserId: String, banned: Boolean) =
        launchResult(
            { engagementRepository.adminSetUserBan(targetUserId, banned) },
            if (banned) "User banned." else "User unbanned."
        )

    fun adminDeletePost(postId: String) =
        launchResult({ engagementRepository.adminDeletePost(postId) }, "Post removed by moderation.")

    fun adminResolveReport(reportId: String, status: String) =
        launchResult({ engagementRepository.adminUpdateReportStatus(reportId, status) }, "Report marked $status.")

    fun adminBroadcastAnnouncement(message: String) =
        launchResult({ engagementRepository.adminBroadcastAnnouncement(message) }, "Announcement broadcast to all users.")

    override fun onCleared() {
        voiceAudioHelper.releaseAll()
        val pendingHosted = _activeHostedStreamId.value
        if (pendingHosted != null) {
            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                withContext(NonCancellable) {
                    liveAndVerificationRepository.endLiveStream(pendingHosted)
                }
            }
        }
        super.onCleared()
    }
}
