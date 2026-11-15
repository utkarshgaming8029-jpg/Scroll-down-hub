package com.example.data.repository

import android.net.Uri
import com.example.data.local.*
import com.example.data.model.*
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.tasks.await
import java.util.UUID

class FirestoreSocialRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth,
    private val mediaStorageHelper: MediaStorageHelper
) {
    private val storyViewRefreshTrigger = MutableStateFlow(0)

    private fun requireUserId(): String =
        auth.currentUser?.uid ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")

    private fun <T> Flow<T>.handleListenerErrors(uid: String, path: String, op: OperationType = OperationType.LIST): Flow<T> =
        this.catch { error ->
            val firestoreError = error.unwrapFirestoreException()
            if (firestoreError == null && error is CancellationException) throw error
            if (auth.currentUser?.uid == uid) {
                handleFirestoreError(firestoreError ?: error, op, path)
            }
            throw error
        }

    fun observeFollowRelations(uid: String): Flow<List<FollowEntity>> =
        db.collection("follows")
            .snapshots()
            .map { snap ->
                snap.documents.mapNotNull { doc ->
                    val m = doc.toObject(FirestoreFollowDoc::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        ?: return@mapNotNull null
                    FollowEntity(
                        followerId = m.followerId,
                        followingId = m.followingId,
                        status = m.status,
                        createdAt = m.createdAt?.toDate()?.time ?: 0L
                    )
                }
            }
            .handleListenerErrors(uid, "follows")

    fun observeBlocks(uid: String): Flow<List<FirestoreBlockDoc>> =
        db.collection("users").document(uid).collection("blocks")
            .snapshots()
            .map { snap ->
                snap.documents.mapNotNull {
                    it.toObject(FirestoreBlockDoc::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }
            }
            .handleListenerErrors(uid, "users/$uid/blocks")

    fun observeSavedPostIds(uid: String): Flow<Set<String>> =
        db.collection("users").document(uid).collection("saved")
            .snapshots()
            .map { snap -> snap.documents.map { it.id }.toSet() }
            .handleListenerErrors(uid, "users/$uid/saved")

    fun observeLikedPostIds(uid: String): Flow<Set<String>> =
        db.collection("users").document(uid).collection("liked")
            .snapshots()
            .map { snap -> snap.documents.map { it.id }.toSet() }
            .handleListenerErrors(uid, "users/$uid/liked")

    fun observeCloseFriendIds(uid: String): Flow<Set<String>> =
        db.collection("users").document(uid).collection("close_friends")
            .snapshots()
            .map { snap -> snap.documents.map { it.id }.toSet() }
            .handleListenerErrors(uid, "users/$uid/close_friends")

    fun observeWatchHistoryRecords(uid: String): Flow<List<FirestoreWatchHistoryDoc>> =
        db.collection("users").document(uid).collection("watch_history")
            .snapshots()
            .map { snap ->
                snap.documents.mapNotNull {
                    it.toObject(FirestoreWatchHistoryDoc::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }
            }
            .handleListenerErrors(uid, "users/$uid/watch_history")

    fun observeAllUserProfiles(viewerId: String): Flow<List<UserProfile>> {
        val usersFlow = db.collection("users").snapshots().map { snap ->
            snap.documents.mapNotNull {
                it.toObject(FirestoreUserDoc::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            }
        }.handleListenerErrors(viewerId, "users")

        val privateSelfFlow = db.collection("users").document(viewerId)
            .collection("private").document("info")
            .snapshots()
            .map { it.toObject(FirestorePrivateInfoDoc::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
            .catch { emit(null) }

        return combine(
            usersFlow,
            observeFollowRelations(viewerId),
            observeBlocks(viewerId),
            observeCloseFriendIds(viewerId),
            privateSelfFlow
        ) { users, follows, blocks, closeFriendIds, privateSelf ->
            val now = System.currentTimeMillis()
            users.map { doc ->
                val isFollowing = follows.any {
                    it.followerId == viewerId && it.followingId == doc.userId && it.status == FollowStatus.ACCEPTED
                }
                val isRequested = follows.any {
                    it.followerId == viewerId && it.followingId == doc.userId && it.status == FollowStatus.PENDING
                }
                val isFollowedBy = follows.any {
                    it.followerId == doc.userId && it.followingId == viewerId && it.status == FollowStatus.ACCEPTED
                }
                val block = blocks.firstOrNull { it.targetId == doc.userId }
                val lastActive = doc.lastActiveAt?.toDate()?.time ?: 0L
                val isSelf = doc.userId == viewerId
                UserProfile(
                    id = doc.userId,
                    username = doc.username,
                    displayName = doc.displayName,
                    email = if (isSelf) privateSelf?.email.orEmpty() else "",
                    bio = doc.bio,
                    avatarUri = doc.avatarUrl,
                    role = doc.role,
                    isGoldenVerified = doc.isGoldenVerified,
                    isPrivate = doc.isPrivate,
                    isBanned = doc.isBanned,
                    demoFollowersDisplay = doc.demoFollowersDisplay,
                    actualFollowersCount = doc.followersCount,
                    followingCount = doc.followingCount,
                    postsCount = doc.postsCount,
                    isFollowing = isFollowing,
                    isFollowRequested = isRequested,
                    isFollowedByTarget = isFollowedBy,
                    isBlockedByMe = block?.type == BlockType.BLOCKED,
                    isRestrictedByMe = block?.type == BlockType.RESTRICTED,
                    isCloseFriend = doc.userId in closeFriendIds,
                    isOnline = (now - lastActive) < 5 * 60_000L,
                    dateOfBirth = if (isSelf) privateSelf?.dateOfBirth.orEmpty() else "",
                    twoFactorEnabled = if (isSelf) privateSelf?.twoFactorEnabled == true else false,
                    backupCodes = if (isSelf) privateSelf?.backupCodes.orEmpty() else emptyList(),
                    hasSecurityPin = if (isSelf) !privateSelf?.securityPinHash.isNullOrBlank() else false,
                    createdAt = doc.createdAt?.toDate()?.time ?: 0L
                )
            }
        }
    }

    fun observeAllPostsUi(
        viewerId: String,
        includeArchived: Boolean = false,
        includeFutureScheduledForOwner: Boolean = false,
        sharedProfilesFlow: Flow<List<UserProfile>>? = null,
        sharedLikedIdsFlow: Flow<Set<String>>? = null,
        sharedSavedIdsFlow: Flow<Set<String>>? = null
    ): Flow<List<PostUiModel>> {
        val postsFlow = db.collection("posts").snapshots().map { snap ->
            snap.documents.mapNotNull {
                it.toObject(FirestorePostDoc::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            }
        }.handleListenerErrors(viewerId, "posts")

        return combine(
            postsFlow,
            sharedProfilesFlow ?: observeAllUserProfiles(viewerId),
            sharedLikedIdsFlow ?: observeLikedPostIds(viewerId),
            sharedSavedIdsFlow ?: observeSavedPostIds(viewerId)
        ) { posts, profiles, likedIds, savedIds ->
            val now = System.currentTimeMillis()
            val userMap = profiles.associateBy { it.id }
            val blockedIds = profiles.filter { it.isBlockedByMe }.map { it.id }.toSet()

            posts.mapNotNull { post ->
                if (!includeArchived && post.isArchived) return@mapNotNull null
                val isFutureScheduled = post.scheduledAtMs > now
                if (isFutureScheduled && !(includeFutureScheduledForOwner && post.authorId == viewerId)) {
                    return@mapNotNull null
                }
                val author = userMap[post.authorId] ?: return@mapNotNull null
                if (author.isBanned || author.id in blockedIds) return@mapNotNull null
                if (author.isPrivate && author.id != viewerId && !author.isFollowing) return@mapNotNull null
                if (post.audience == PostAudience.CLOSE_FRIENDS &&
                    post.authorId != viewerId &&
                    viewerId !in post.allowedViewerIds
                ) {
                    return@mapNotNull null
                }

                PostUiModel(
                    id = post.id,
                    author = author,
                    caption = post.caption,
                    mediaUris = post.mediaUrls,
                    mediaType = post.mediaType,
                    hashtags = post.hashtags,
                    taggedUsernames = post.taggedUsernames,
                    musicTitle = post.musicTitle,
                    likesCount = post.likesCount,
                    commentsCount = post.commentsCount,
                    viewsCount = post.viewsCount,
                    isLiked = post.id in likedIds,
                    isSaved = post.id in savedIds,
                    isReel = post.isReel,
                    isArchived = post.isArchived,
                    scheduledAtMs = post.scheduledAtMs,
                    audience = post.audience,
                    allowedViewerIds = post.allowedViewerIds,
                    createdAt = (post.createdAt ?: Timestamp.now()).toDate().time
                )
            }.sortedByDescending { it.createdAt }
        }
    }

    fun observeScheduledPosts(ownerId: String): Flow<List<PostUiModel>> =
        observeAllPostsUi(ownerId, includeArchived = false, includeFutureScheduledForOwner = true)
            .map { list ->
                val now = System.currentTimeMillis()
                list.filter { it.author.id == ownerId && it.scheduledAtMs > now }
                    .sortedBy { it.scheduledAtMs }
            }

    fun observeWatchHistory(
        viewerId: String,
        sharedPostsFlow: Flow<List<PostUiModel>>? = null
    ): Flow<List<WatchHistoryUiModel>> =
        combine(
            observeWatchHistoryRecords(viewerId),
            sharedPostsFlow ?: observeAllPostsUi(viewerId, includeArchived = false)
        ) { records, posts ->
            val postMap = posts.associateBy { it.id }
            records.mapNotNull { rec ->
                val post = postMap[rec.postId] ?: return@mapNotNull null
                WatchHistoryUiModel(
                    post = post,
                    watchedAt = rec.watchedAt?.toDate()?.time ?: System.currentTimeMillis()
                )
            }.sortedByDescending { it.watchedAt }
        }

    fun observeHomeFeed(viewerId: String, limit: Int): Flow<List<PostUiModel>> =
        combine(
            observeAllPostsUi(viewerId, includeArchived = false),
            observeFollowRelations(viewerId)
        ) { allPosts, follows ->
            val followingIds = follows
                .filter { it.followerId == viewerId && it.status == FollowStatus.ACCEPTED }
                .map { it.followingId }
                .toSet()

            val prioritized = allPosts.filter { post ->
                post.author.id == viewerId ||
                    post.author.id in followingIds ||
                    post.author.isSuperAdmin
            }
            (if (prioritized.isNotEmpty()) prioritized else allPosts).take(limit)
        }

    fun observeActiveNotes(
        viewerId: String,
        sharedProfilesFlow: Flow<List<UserProfile>>? = null
    ): Flow<List<NoteUiModel>> {
        val notesFlow = db.collection("notes")
            .snapshots()
            .map { snap ->
                snap.documents.mapNotNull {
                    it.toObject(FirestoreNoteDoc::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }
            }.handleListenerErrors(viewerId, "notes")

        return combine(notesFlow, sharedProfilesFlow ?: observeAllUserProfiles(viewerId)) { docs, profiles ->
            val now = System.currentTimeMillis()
            val userMap = profiles.associateBy { it.id }
            docs.filter { it.expiresAtMs > now }.mapNotNull { n ->
                val author = userMap[n.authorId] ?: return@mapNotNull null
                if (author.isBanned || author.isBlockedByMe) return@mapNotNull null
                if (author.isPrivate && author.id != viewerId && !author.isFollowing) return@mapNotNull null
                if (n.audience == PostAudience.CLOSE_FRIENDS &&
                    author.id != viewerId &&
                    viewerId !in n.allowedViewerIds
                ) {
                    return@mapNotNull null
                }
                NoteUiModel(
                    id = n.id,
                    author = author,
                    text = n.text,
                    musicTitle = n.musicTitle,
                    audience = n.audience,
                    createdAt = n.createdAt?.toDate()?.time ?: now,
                    expiresAt = n.expiresAtMs
                )
            }.sortedByDescending { it.author.id == viewerId }
        }
    }

    fun observeActiveStoryGroups(
        viewerId: String,
        sharedProfilesFlow: Flow<List<UserProfile>>? = null
    ): Flow<List<StoryGroupUiModel>> {
        val storiesFlow = db.collection("stories").snapshots().map { snap ->
            snap.documents.mapNotNull {
                it.toObject(FirestoreStoryDoc::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            }
        }.handleListenerErrors(viewerId, "stories")

        // Periodic 30-second ticker so stories crossing 24h expiry drop automatically without new writes
        val expiryTickFlow = flow {
            while (true) {
                emit(System.currentTimeMillis())
                delay(30_000L)
            }
        }

        // Only query view records when stories change or a view is recorded (not on unrelated profile changes)
        val storiesWithViewsFlow = combine(storiesFlow, storyViewRefreshTrigger) { stories, _ ->
            val now = System.currentTimeMillis()
            val visibleStories = stories.filter {
                !it.isArchived && it.expiresAtMs > now &&
                    (it.audience != PostAudience.CLOSE_FRIENDS || it.authorId == viewerId || viewerId in it.allowedViewerIds)
            }
            val viewsByStory = coroutineScope {
                visibleStories.map { s ->
                    async {
                        val viewsSnap = runCatching {
                            db.collection("stories").document(s.id).collection("views").get().await()
                        }.getOrNull()
                        val viewDocs = viewsSnap?.documents?.mapNotNull {
                            it.toObject(FirestoreStoryViewDoc::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        }.orEmpty()
                        s.id to viewDocs
                    }
                }.awaitAll().toMap()
            }
            visibleStories to viewsByStory
        }

        return combine(
            storiesWithViewsFlow,
            sharedProfilesFlow ?: observeAllUserProfiles(viewerId),
            expiryTickFlow
        ) { (activeStories, viewsByStory), profiles, tickNow ->
            val userMap = profiles.associateBy { it.id }
            val active = activeStories.filter { !it.isArchived && it.expiresAtMs > tickNow }
            active.groupBy { it.authorId }.mapNotNull { (authorId, authorStories) ->
                val author = userMap[authorId] ?: return@mapNotNull null
                if (author.isBanned || author.isBlockedByMe) return@mapNotNull null
                if (author.isPrivate && author.id != viewerId && !author.isFollowing) return@mapNotNull null

                val items = authorStories
                    .distinctBy { it.id }
                    .sortedBy { it.createdAt?.toDate()?.time ?: 0L }
                    .map { s ->
                        val viewDocs = viewsByStory[s.id].orEmpty()
                        val myView = viewDocs.firstOrNull { it.viewerId == viewerId }
                        StoryItemUiModel(
                            id = s.id,
                            author = author,
                            mediaUri = s.mediaUrl,
                            mediaType = s.mediaType,
                            caption = s.caption,
                            highlightTitle = s.highlightTitle,
                            audience = s.audience,
                            viewsCount = viewDocs.size,
                            myReaction = myView?.reactionEmoji,
                            hasViewedByMe = myView != null || s.authorId == viewerId,
                            viewers = viewDocs.mapNotNull { v ->
                                val u = userMap[v.viewerId] ?: return@mapNotNull null
                                StoryViewerDetail(u, v.reactionEmoji, v.viewedAt?.toDate()?.time ?: tickNow)
                            },
                            isArchived = s.isArchived,
                            createdAt = s.createdAt?.toDate()?.time ?: tickNow,
                            expiresAt = s.expiresAtMs
                        )
                    }
                if (items.isEmpty()) return@mapNotNull null
                StoryGroupUiModel(
                    author = author,
                    stories = items,
                    hasUnseen = items.any { !it.hasViewedByMe }
                )
            }.sortedWith(
                compareByDescending<StoryGroupUiModel> { it.author.id == viewerId }
                    .thenByDescending { it.hasUnseen }
                    .thenByDescending { group -> group.stories.maxOfOrNull { it.createdAt } ?: 0L }
            )
        }
    }

    fun observeAllStoriesForUser(
        userId: String,
        sharedProfilesFlow: Flow<List<UserProfile>>? = null
    ): Flow<List<StoryItemUiModel>> {
        val storiesFlow = db.collection("stories")
            .whereEqualTo("authorId", userId)
            .snapshots()
            .map { snap ->
                snap.documents.mapNotNull {
                    it.toObject(FirestoreStoryDoc::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }
            }.handleListenerErrors(userId, "stories")

        return combine(storiesFlow, sharedProfilesFlow ?: observeAllUserProfiles(userId)) { stories, profiles ->
            val author = profiles.firstOrNull { it.id == userId } ?: return@combine emptyList()
            stories.sortedByDescending { it.createdAt?.toDate()?.time ?: 0L }.map { s ->
                StoryItemUiModel(
                    id = s.id,
                    author = author,
                    mediaUri = s.mediaUrl,
                    mediaType = s.mediaType,
                    caption = s.caption,
                    highlightTitle = s.highlightTitle,
                    viewsCount = 0,
                    myReaction = null,
                    viewers = emptyList(),
                    isArchived = s.isArchived,
                    createdAt = s.createdAt?.toDate()?.time ?: System.currentTimeMillis(),
                    expiresAt = s.expiresAtMs
                )
            }
        }
    }

    fun observeUserHighlights(
        targetUserId: String,
        viewerId: String,
        sharedProfilesFlow: Flow<List<UserProfile>>? = null
    ): Flow<List<StoryItemUiModel>> =
        observeAllStoriesForUser(targetUserId, sharedProfilesFlow).map { list ->
            list.filter { !it.highlightTitle.isNullOrBlank() && !it.isArchived }
        }

    fun observeStoryViewers(
        storyId: String,
        viewerId: String,
        sharedProfilesFlow: Flow<List<UserProfile>>? = null
    ): Flow<List<StoryViewerDetail>> {
        val viewsFlow = db.collection("stories").document(storyId).collection("views")
            .snapshots()
            .map { snap ->
                snap.documents.mapNotNull {
                    it.toObject(FirestoreStoryViewDoc::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }
            }.handleListenerErrors(viewerId, "stories/$storyId/views")

        return combine(viewsFlow, sharedProfilesFlow ?: observeAllUserProfiles(viewerId)) { views, profiles ->
            val userMap = profiles.associateBy { it.id }
            views.mapNotNull { v ->
                val user = userMap[v.viewerId] ?: return@mapNotNull null
                StoryViewerDetail(
                    user = user,
                    reactionEmoji = v.reactionEmoji,
                    viewedAt = v.viewedAt?.toDate()?.time ?: System.currentTimeMillis()
                )
            }.sortedByDescending { it.viewedAt }
        }
    }

    private suspend fun fetchCloseFriendIds(ownerId: String): List<String> =
        db.collection("users").document(ownerId).collection("close_friends")
            .get().await().documents.map { it.id }.take(100)

    suspend fun createPostOrReel(
        caption: String,
        mediaUris: List<Uri>,
        isVideo: Boolean,
        isReel: Boolean,
        musicTitle: String,
        scheduledAtMs: Long = 0L,
        audience: String = PostAudience.ALL
    ): Result<String> {
        val authorId = requireUserId()
        if (!SecurityUtils.checkRateLimit("post_$authorId", maxActions = 10)) {
            return Result.failure(IllegalStateException("Posting too fast. Please wait a moment."))
        }
        if (mediaUris.isEmpty()) {
            return Result.failure(IllegalArgumentException("Please select at least one photo or video."))
        }
        return try {
            val persisted = if (isVideo) {
                listOf(mediaStorageHelper.persistVideo(mediaUris.first()).getOrThrow())
            } else {
                val maxDim = if (mediaUris.size > 1) 480 else 720
                val quality = if (mediaUris.size > 1) 62 else 72
                mediaUris.take(8).map { uri ->
                    mediaStorageHelper.persistCompressedImage(uri, maxDim, quality).getOrThrow()
                }
            }

            val mediaType = when {
                isReel -> MediaTypes.REEL
                isVideo -> MediaTypes.VIDEO
                persisted.size > 1 -> MediaTypes.CAROUSEL
                else -> MediaTypes.PHOTO
            }

            val postId = "post_${UUID.randomUUID()}"
            val hashtags = SecurityUtils.extractHashtags(caption)
            val taggedUsernames = SecurityUtils.extractMentions(caption)
            val allowedViewers = if (audience == PostAudience.CLOSE_FRIENDS) fetchCloseFriendIds(authorId) else emptyList()

            val payload = mapOf(
                "authorId" to authorId,
                "caption" to caption.trim().take(2200),
                "mediaUrls" to persisted,
                "mediaType" to mediaType,
                "hashtags" to hashtags,
                "taggedUsernames" to taggedUsernames,
                "musicTitle" to musicTitle.trim().take(120),
                "likesCount" to 0L,
                "commentsCount" to 0L,
                "viewsCount" to 0L,
                "isReel" to isReel,
                "isArchived" to false,
                "scheduledAtMs" to scheduledAtMs.coerceAtLeast(0L),
                "audience" to audience,
                "allowedViewerIds" to allowedViewers,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )

            db.collection("posts").document(postId).set(payload).await()
            db.collection("users").document(authorId).update(
                mapOf(
                    "postsCount" to FieldValue.increment(1L),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            Result.success(postId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, "posts")
            Result.failure(e)
        }
    }

    suspend fun publishScheduledPostNow(postId: String): Result<Unit> {
        requireUserId()
        val ref = db.collection("posts").document(postId)
        return try {
            ref.update(
                mapOf(
                    "scheduledAtMs" to 0L,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, ref.path)
            Result.failure(e)
        }
    }

    suspend fun createStory(
        mediaUri: Uri,
        isVideo: Boolean,
        caption: String,
        highlightTitle: String?,
        audience: String = PostAudience.ALL
    ): Result<String> {
        val authorId = requireUserId()
        return try {
            val persisted = if (isVideo) {
                mediaStorageHelper.persistVideo(mediaUri).getOrThrow()
            } else {
                mediaStorageHelper.persistCompressedImage(mediaUri, maxDim = 640, quality = 70).getOrThrow()
            }
            val storyId = "story_${UUID.randomUUID()}"
            val allowedViewers = if (audience == PostAudience.CLOSE_FRIENDS) fetchCloseFriendIds(authorId) else emptyList()
            val payload = mutableMapOf<String, Any>(
                "authorId" to authorId,
                "mediaUrl" to persisted,
                "mediaType" to if (isVideo) MediaTypes.VIDEO else MediaTypes.PHOTO,
                "caption" to caption.trim().take(300),
                "audience" to audience,
                "allowedViewerIds" to allowedViewers,
                "isArchived" to false,
                "expiresAtMs" to System.currentTimeMillis() + 24L * 60L * 60L * 1000L,
                "createdAt" to FieldValue.serverTimestamp()
            )
            if (!highlightTitle.isNullOrBlank()) {
                payload["highlightTitle"] = highlightTitle.trim().take(50)
            }
            db.collection("stories").document(storyId).set(payload).await()
            Result.success(storyId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, "stories")
            Result.failure(e)
        }
    }

    suspend fun toggleLike(postId: String): Result<Unit> {
        val uid = requireUserId()
        val likeRef = db.collection("posts").document(postId).collection("likes").document(uid)
        val userLikedRef = db.collection("users").document(uid).collection("liked").document(postId)
        val postRef = db.collection("posts").document(postId)
        return try {
            val addedLike = db.runTransaction { tx ->
                val postSnap = tx.get(postRef)
                val likeSnap = tx.get(likeRef)
                val currentLikes = (postSnap.getLong("likesCount") ?: 0L).coerceAtLeast(0L)
                val ts = FieldValue.serverTimestamp()
                if (likeSnap.exists()) {
                    tx.delete(likeRef)
                    tx.delete(userLikedRef)
                    tx.update(
                        postRef,
                        mapOf(
                            "likesCount" to (currentLikes - 1L).coerceAtLeast(0L),
                            "updatedAt" to ts
                        )
                    )
                    false
                } else {
                    tx.set(likeRef, mapOf("userId" to uid, "createdAt" to ts))
                    tx.set(userLikedRef, mapOf("postId" to postId, "createdAt" to ts))
                    tx.update(
                        postRef,
                        mapOf(
                            "likesCount" to currentLikes + 1L,
                            "updatedAt" to ts
                        )
                    )
                    true
                }
            }.await()
            if (addedLike) {
                val postSnap = postRef.get().await()
                val authorId = postSnap.getString("authorId")
                if (!authorId.isNullOrBlank() && authorId != uid) {
                    sendNotification(authorId, uid, NotificationTypes.LIKE, postId, "liked your post.")
                }
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "posts/$postId/likes/$uid")
            Result.failure(e)
        }
    }

    suspend fun toggleSavePost(postId: String): Result<Unit> {
        val uid = requireUserId()
        val ref = db.collection("users").document(uid).collection("saved").document(postId)
        return try {
            val snap = ref.get().await()
            if (snap.exists()) {
                ref.delete().await()
            } else {
                ref.set(mapOf("postId" to postId, "createdAt" to FieldValue.serverTimestamp())).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, ref.path)
            Result.failure(e)
        }
    }

    suspend fun toggleArchivePost(postId: String, archived: Boolean): Result<Unit> {
        requireUserId()
        val ref = db.collection("posts").document(postId)
        return try {
            ref.update(
                mapOf(
                    "isArchived" to archived,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, ref.path)
            Result.failure(e)
        }
    }

    suspend fun toggleArchiveStory(storyId: String, archived: Boolean): Result<Unit> {
        requireUserId()
        val ref = db.collection("stories").document(storyId)
        return try {
            ref.update(mapOf("isArchived" to archived)).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, ref.path)
            Result.failure(e)
        }
    }

    suspend fun deleteStory(storyId: String): Result<Unit> {
        val uid = requireUserId()
        val storyRef = db.collection("stories").document(storyId)
        return try {
            val snap = storyRef.get().await()
            if (!snap.exists()) {
                return Result.failure(IllegalStateException("Story not found or already deleted."))
            }
            val authorId = snap.getString("authorId").orEmpty()
            if (authorId != uid) {
                val isAdmin = runCatching {
                    db.collection("admins").document(uid).get().await().exists() ||
                        db.collection("users").document(uid).get().await().getString("role") == UserRoles.SUPER_ADMIN
                }.getOrDefault(false)
                if (!isAdmin) {
                    return Result.failure(SecurityException("Only the story owner can delete this story."))
                }
            }
            val mediaUrl = snap.getString("mediaUrl")

            // Clean up subcollection view records before deleting parent doc (rules verify parent authorId)
            val viewsSnap = runCatching { storyRef.collection("views").get().await() }.getOrNull()
            if (viewsSnap != null && !viewsSnap.isEmpty) {
                viewsSnap.documents.chunked(400).forEach { chunk ->
                    runCatching {
                        val batch = db.batch()
                        chunk.forEach { batch.delete(it.reference) }
                        batch.commit().await()
                    }
                }
            }

            storyRef.delete().await()
            mediaStorageHelper.deleteStoredMediaIfLocal(mediaUrl)
            storyViewRefreshTrigger.value += 1
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, storyRef.path)
            Result.failure(e)
        }
    }

    suspend fun toggleStoryHighlight(storyId: String, title: String?): Result<Unit> {
        requireUserId()
        val ref = db.collection("stories").document(storyId)
        return try {
            ref.update(mapOf("highlightTitle" to title?.trim()?.take(50))).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, ref.path)
            Result.failure(e)
        }
    }

    suspend fun recordStoryView(storyId: String, reactionEmoji: String?): Result<Unit> {
        val viewerId = requireUserId()
        val ref = db.collection("stories").document(storyId).collection("views").document(viewerId)
        return try {
            val payload = mutableMapOf<String, Any>(
                "viewerId" to viewerId,
                "viewedAt" to FieldValue.serverTimestamp()
            )
            if (!reactionEmoji.isNullOrBlank()) {
                payload["reactionEmoji"] = reactionEmoji.take(16)
            }
            ref.set(payload).await()
            storyViewRefreshTrigger.value += 1
            if (!reactionEmoji.isNullOrBlank()) {
                val storySnap = db.collection("stories").document(storyId).get().await()
                val authorId = storySnap.getString("authorId")
                if (!authorId.isNullOrBlank() && authorId != viewerId) {
                    sendNotification(
                        authorId,
                        viewerId,
                        NotificationTypes.STORY_REACTION,
                        storyId,
                        "reacted $reactionEmoji to your story."
                    )
                }
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, ref.path)
            Result.failure(e)
        }
    }

    suspend fun incrementReelView(postId: String) {
        if (auth.currentUser == null) return
        val ref = db.collection("posts").document(postId)
        try {
            ref.update(
                mapOf(
                    "viewsCount" to FieldValue.increment(1L),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, ref.path)
        }
    }

    suspend fun recordWatchHistory(postId: String, mediaType: String) {
        val uid = auth.currentUser?.uid ?: return
        val type = if (mediaType == MediaTypes.REEL) MediaTypes.REEL else MediaTypes.VIDEO
        val ref = db.collection("users").document(uid).collection("watch_history").document(postId)
        try {
            ref.set(
                mapOf(
                    "postId" to postId,
                    "mediaType" to type,
                    "watchedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
        }
    }

    suspend fun clearWatchHistory(): Result<Unit> {
        val uid = requireUserId()
        return try {
            val snap = db.collection("users").document(uid).collection("watch_history").get().await()
            for (doc in snap.documents) {
                doc.reference.delete().await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, "users/$uid/watch_history")
            Result.failure(e)
        }
    }

    suspend fun toggleCloseFriend(targetId: String): Result<Unit> {
        val uid = requireUserId()
        if (uid == targetId) return Result.success(Unit)
        val ref = db.collection("users").document(uid).collection("close_friends").document(targetId)
        return try {
            val snap = ref.get().await()
            if (snap.exists()) {
                ref.delete().await()
            } else {
                ref.set(
                    mapOf(
                        "targetId" to targetId,
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, ref.path)
            Result.failure(e)
        }
    }

    suspend fun publishNote(text: String, musicTitle: String, audience: String): Result<Unit> {
        val uid = requireUserId()
        val cleanText = text.trim()
        if (cleanText.isEmpty()) return Result.failure(IllegalArgumentException("Note cannot be empty."))
        val ref = db.collection("notes").document(uid)
        return try {
            val allowedViewers = if (audience == PostAudience.CLOSE_FRIENDS) fetchCloseFriendIds(uid) else emptyList()
            ref.set(
                mapOf(
                    "authorId" to uid,
                    "text" to cleanText.take(120),
                    "musicTitle" to musicTitle.trim().take(80),
                    "audience" to audience,
                    "allowedViewerIds" to allowedViewers,
                    "expiresAtMs" to System.currentTimeMillis() + 24L * 60L * 60L * 1000L,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, ref.path)
            Result.failure(e)
        }
    }

    suspend fun deleteOwnNote(): Result<Unit> {
        val uid = requireUserId()
        val ref = db.collection("notes").document(uid)
        return try {
            ref.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, ref.path)
            Result.failure(e)
        }
    }

    suspend fun toggleFollow(targetUserId: String): Result<Unit> {
        val followerId = requireUserId()
        if (followerId == targetUserId) return Result.success(Unit)
        val followId = "${followerId}_${targetUserId}"
        val followRef = db.collection("follows").document(followId)
        val targetRef = db.collection("users").document(targetUserId)
        val followerRef = db.collection("users").document(followerId)
        return try {
            val outcomeStatus = db.runTransaction { tx ->
                val existing = tx.get(followRef)
                val targetDoc = tx.get(targetRef)
                val followerDoc = tx.get(followerRef)
                val isPrivate = targetDoc.getBoolean("isPrivate") == true
                val targetFollowers = (targetDoc.getLong("followersCount") ?: 0L).coerceAtLeast(0L)
                val myFollowing = (followerDoc.getLong("followingCount") ?: 0L).coerceAtLeast(0L)
                val ts = FieldValue.serverTimestamp()

                if (existing.exists()) {
                    val prevStatus = existing.getString("status")
                    tx.delete(followRef)
                    if (prevStatus == FollowStatus.ACCEPTED) {
                        tx.update(
                            targetRef,
                            mapOf("followersCount" to (targetFollowers - 1L).coerceAtLeast(0L), "updatedAt" to ts)
                        )
                        tx.update(
                            followerRef,
                            mapOf("followingCount" to (myFollowing - 1L).coerceAtLeast(0L), "updatedAt" to ts)
                        )
                    }
                    null
                } else {
                    val status = if (isPrivate) FollowStatus.PENDING else FollowStatus.ACCEPTED
                    tx.set(
                        followRef,
                        mapOf(
                            "followerId" to followerId,
                            "followingId" to targetUserId,
                            "status" to status,
                            "createdAt" to ts,
                            "updatedAt" to ts
                        )
                    )
                    if (status == FollowStatus.ACCEPTED) {
                        tx.update(targetRef, mapOf("followersCount" to targetFollowers + 1L, "updatedAt" to ts))
                        tx.update(followerRef, mapOf("followingCount" to myFollowing + 1L, "updatedAt" to ts))
                    }
                    status
                }
            }.await()

            when (outcomeStatus) {
                FollowStatus.ACCEPTED -> sendNotification(
                    targetUserId, followerId, NotificationTypes.FOLLOW, followerId, "started following you."
                )
                FollowStatus.PENDING -> sendNotification(
                    targetUserId, followerId, NotificationTypes.FOLLOW_REQUEST, followerId, "requested to follow you."
                )
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, followRef.path)
            Result.failure(e)
        }
    }

    suspend fun respondToFollowRequest(requesterId: String, accept: Boolean): Result<Unit> {
        val targetId = requireUserId()
        val followRef = db.collection("follows").document("${requesterId}_${targetId}")
        return try {
            if (accept) {
                followRef.update(
                    mapOf(
                        "status" to FollowStatus.ACCEPTED,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
                db.collection("users").document(targetId).update(
                    mapOf("followersCount" to FieldValue.increment(1L), "updatedAt" to FieldValue.serverTimestamp())
                ).await()
                db.collection("users").document(requesterId).update(
                    mapOf("followingCount" to FieldValue.increment(1L), "updatedAt" to FieldValue.serverTimestamp())
                ).await()
                sendNotification(requesterId, targetId, NotificationTypes.FOLLOW, targetId, "accepted your follow request.")
            } else {
                followRef.delete().await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, followRef.path)
            Result.failure(e)
        }
    }

    suspend fun updateProfile(
        displayName: String,
        bio: String,
        isPrivate: Boolean,
        newAvatarUri: Uri?
    ): Result<Unit> {
        val uid = requireUserId()
        val userRef = db.collection("users").document(uid)
        return try {
            val updates = mutableMapOf<String, Any>(
                "displayName" to displayName.trim().ifBlank { "SCROLL DOWN User" }.take(60),
                "bio" to bio.trim().take(300),
                "isPrivate" to isPrivate,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (newAvatarUri != null) {
                updates["avatarUrl"] = mediaStorageHelper.persistCompressedImage(newAvatarUri, maxDim = 480, quality = 75).getOrThrow()
            }
            userRef.update(updates).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, userRef.path)
            Result.failure(e)
        }
    }

    suspend fun toggleBlockOrRestrict(targetId: String, type: String): Result<Unit> {
        val uid = requireUserId()
        val ref = db.collection("users").document(uid).collection("blocks").document(targetId)
        return try {
            val existing = ref.get().await()
            if (existing.exists() && existing.getString("type") == type) {
                ref.delete().await()
            } else {
                ref.set(
                    mapOf(
                        "targetId" to targetId,
                        "type" to type,
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, ref.path)
            Result.failure(e)
        }
    }

    suspend fun deleteOwnPost(postId: String): Result<Unit> {
        val uid = requireUserId()
        val ref = db.collection("posts").document(postId)
        return try {
            ref.delete().await()
            db.collection("users").document(uid).update(
                mapOf(
                    "postsCount" to FieldValue.increment(-1L),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, ref.path)
            Result.failure(e)
        }
    }

    internal suspend fun sendNotification(
        recipientId: String,
        actorId: String,
        type: String,
        referenceId: String,
        message: String
    ) {
        val notifId = "ntf_${UUID.randomUUID()}"
        val ref = db.collection("users").document(recipientId).collection("notifications").document(notifId)
        try {
            ref.set(
                mapOf(
                    "recipientId" to recipientId,
                    "actorId" to actorId,
                    "type" to type,
                    "referenceId" to referenceId,
                    "message" to message.take(400),
                    "isRead" to false,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, ref.path)
        }
    }
}
