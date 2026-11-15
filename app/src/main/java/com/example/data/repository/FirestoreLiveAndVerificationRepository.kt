package com.example.data.repository

import com.example.data.local.*
import com.example.data.model.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.tasks.await
import java.util.UUID

class FirestoreLiveAndVerificationRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth,
    private val socialRepository: FirestoreSocialRepository
) {
    private fun requireUserId(): String =
        auth.currentUser?.uid ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")

    private fun <T> Flow<T>.handleErrors(uid: String, path: String): Flow<T> =
        this.catch { error ->
            val firestoreError = error.unwrapFirestoreException()
            if (firestoreError == null && error is CancellationException) throw error
            if (auth.currentUser?.uid == uid) {
                handleFirestoreError(firestoreError ?: error, OperationType.LIST, path)
            }
            throw error
        }

    // --- Voice & Video Call Signaling ---

    fun observeActiveCalls(
        uid: String,
        sharedProfilesFlow: Flow<List<UserProfile>>? = null
    ): Flow<List<CallUiModel>> {
        val callsFlow = db.collection("calls")
            .whereArrayContains("participantIds", uid)
            .snapshots()
            .map { snap ->
                snap.documents.mapNotNull {
                    it.toObject(FirestoreCallDoc::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }
            }.handleErrors(uid, "calls")

        return combine(callsFlow, sharedProfilesFlow ?: socialRepository.observeAllUserProfiles(uid)) { docs, profiles ->
            val userMap = profiles.associateBy { it.id }
            docs.filter { it.status == CallStatus.RINGING || it.status == CallStatus.ACCEPTED }
                .mapNotNull { c ->
                    val caller = userMap[c.callerId] ?: return@mapNotNull null
                    val callee = userMap[c.calleeId] ?: return@mapNotNull null
                    CallUiModel(
                        id = c.id,
                        caller = caller,
                        callee = callee,
                        callType = c.callType,
                        status = c.status,
                        createdAt = c.createdAt?.toDate()?.time ?: System.currentTimeMillis()
                    )
                }.sortedByDescending { it.createdAt }
        }
    }

    suspend fun startCall(calleeId: String, callType: String): Result<String> {
        val callerId = requireUserId()
        if (callerId == calleeId) return Result.failure(IllegalArgumentException("Cannot call yourself."))
        val callId = "call_${UUID.randomUUID()}"
        val ref = db.collection("calls").document(callId)
        return try {
            val payload = mapOf(
                "callerId" to callerId,
                "calleeId" to calleeId,
                "participantIds" to listOf(callerId, calleeId),
                "callType" to callType,
                "status" to CallStatus.RINGING,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            ref.set(payload).await()
            socialRepository.sendNotification(
                calleeId,
                callerId,
                NotificationTypes.CALL,
                callId,
                if (callType == CallType.VIDEO) "started a video call with you." else "is calling you."
            )
            Result.success(callId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, ref.path)
            Result.failure(e)
        }
    }

    suspend fun respondToCall(callId: String, accept: Boolean): Result<Unit> {
        requireUserId()
        val ref = db.collection("calls").document(callId)
        return try {
            ref.update(
                mapOf(
                    "status" to if (accept) CallStatus.ACCEPTED else CallStatus.DECLINED,
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

    suspend fun endCall(callId: String): Result<Unit> {
        requireUserId()
        val ref = db.collection("calls").document(callId)
        return try {
            ref.update(
                mapOf(
                    "status" to CallStatus.ENDED,
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

    // --- Live Streaming ---

    fun observeActiveLiveStreams(
        viewerId: String,
        sharedProfilesFlow: Flow<List<UserProfile>>? = null
    ): Flow<List<LiveStreamUiModel>> {
        val streamsFlow = db.collection("live_streams")
            .snapshots()
            .map { snap ->
                snap.documents.mapNotNull {
                    it.toObject(FirestoreLiveStreamDoc::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }
            }.handleErrors(viewerId, "live_streams")

        return combine(streamsFlow, sharedProfilesFlow ?: socialRepository.observeAllUserProfiles(viewerId)) { docs, profiles ->
            val userMap = profiles.associateBy { it.id }
            docs.filter { it.isLive && it.status != LiveStreamStatus.ENDED }
                .mapNotNull { s ->
                    val host = userMap[s.hostId] ?: return@mapNotNull null
                    if (host.isBanned || host.isBlockedByMe) return@mapNotNull null
                    if (host.isPrivate && host.id != viewerId && !host.isFollowing) return@mapNotNull null
                    val currentCount = s.viewerCount.coerceAtLeast(s.viewerIds.size.toLong())
                    LiveStreamUiModel(
                        id = s.id,
                        host = host,
                        title = s.title,
                        isLive = s.isLive,
                        status = s.status,
                        viewerCount = currentCount,
                        peakViewerCount = s.peakViewerCount.coerceAtLeast(currentCount),
                        startedAt = s.startedAt?.toDate()?.time ?: System.currentTimeMillis(),
                        endedAt = s.endedAt?.toDate()?.time
                    )
                }.sortedByDescending { it.startedAt }
        }
    }

    fun observeLiveStreamById(
        streamId: String,
        viewerId: String,
        sharedProfilesFlow: Flow<List<UserProfile>>? = null
    ): Flow<LiveStreamUiModel?> {
        val streamFlow = db.collection("live_streams").document(streamId)
            .snapshots()
            .map { snap ->
                if (!snap.exists()) null
                else snap.toObject(FirestoreLiveStreamDoc::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            }.handleErrors(viewerId, "live_streams/$streamId")

        return combine(streamFlow, sharedProfilesFlow ?: socialRepository.observeAllUserProfiles(viewerId)) { doc, profiles ->
            if (doc == null) return@combine null
            val userMap = profiles.associateBy { it.id }
            val host = userMap[doc.hostId] ?: return@combine null
            val currentCount = doc.viewerCount.coerceAtLeast(doc.viewerIds.size.toLong())
            LiveStreamUiModel(
                id = doc.id,
                host = host,
                title = doc.title,
                isLive = doc.isLive,
                status = doc.status,
                viewerCount = currentCount,
                peakViewerCount = doc.peakViewerCount.coerceAtLeast(currentCount),
                startedAt = doc.startedAt?.toDate()?.time ?: System.currentTimeMillis(),
                endedAt = doc.endedAt?.toDate()?.time
            )
        }
    }

    suspend fun endOrphanedLiveStreamsForHost(hostId: String): Int {
        if (auth.currentUser?.uid != hostId) return 0
        return try {
            val snap = db.collection("live_streams")
                .whereEqualTo("hostId", hostId)
                .get()
                .await()
            val activeDocs = snap.documents.filter { it.getBoolean("isLive") == true }
            for (doc in activeDocs) {
                doc.reference.update(
                    mapOf(
                        "isLive" to false,
                        "status" to LiveStreamStatus.ENDED,
                        "viewerIds" to emptyList<String>(),
                        "viewerCount" to 0L,
                        "endedAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            activeDocs.size
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            0
        }
    }

    suspend fun startLiveStream(title: String): Result<String> {
        val hostId = requireUserId()
        val cleanTitle = title.trim().ifBlank { "Live on SCROLL DOWN" }.take(120)
        if (cleanTitle.length < 2) {
            return Result.failure(IllegalArgumentException("Live stream title must be at least 2 characters."))
        }
        val streamId = "live_${UUID.randomUUID()}"
        val ref = db.collection("live_streams").document(streamId)
        return try {
            // Prevent duplicate/orphaned active streams for the same host
            endOrphanedLiveStreamsForHost(hostId)

            val payload = mapOf(
                "hostId" to hostId,
                "title" to cleanTitle,
                "isLive" to true,
                "status" to LiveStreamStatus.LIVE,
                "viewerIds" to emptyList<String>(),
                "viewerCount" to 1L,
                "peakViewerCount" to 1L,
                "startedAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            ref.set(payload).await()

            // Notify followers
            val followersSnap = db.collection("follows")
                .whereEqualTo("followingId", hostId)
                .get()
                .await()
            followersSnap.documents
                .filter { it.getString("status") == FollowStatus.ACCEPTED }
                .take(25)
                .forEach { doc ->
                    val followerUid = doc.getString("followerId")
                    if (!followerUid.isNullOrBlank() && followerUid != hostId) {
                        socialRepository.sendNotification(
                            followerUid,
                            hostId,
                            NotificationTypes.LIVE,
                            streamId,
                            "went LIVE: \"$cleanTitle\""
                        )
                    }
                }
            Result.success(streamId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, ref.path)
            Result.failure(e)
        }
    }

    suspend fun joinLiveStream(streamId: String): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in."))
        val ref = db.collection("live_streams").document(streamId)
        return try {
            val joined = db.runTransaction { tx ->
                val snap = tx.get(ref)
                if (!snap.exists()) return@runTransaction false
                val isLive = snap.getBoolean("isLive") == true
                val status = snap.getString("status") ?: LiveStreamStatus.LIVE
                if (!isLive || status == LiveStreamStatus.ENDED) {
                    return@runTransaction false
                }
                @Suppress("UNCHECKED_CAST")
                val existingIds = (snap.get("viewerIds") as? List<String>).orEmpty()
                val existingPeak = (snap.getLong("peakViewerCount") ?: 1L).coerceAtLeast(1L)
                if (uid !in existingIds) {
                    val updated = (existingIds + uid).take(500)
                    val updatedCount = updated.size.toLong().coerceAtLeast(1L)
                    tx.update(
                        ref,
                        mapOf(
                            "viewerIds" to updated,
                            "viewerCount" to updatedCount,
                            "peakViewerCount" to maxOf(existingPeak, updatedCount),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }
                true
            }.await()
            if (joined) Result.success(Unit)
            else Result.failure(IllegalStateException("This live broadcast has already ended."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun leaveLiveStream(streamId: String) {
        val uid = auth.currentUser?.uid ?: return
        val ref = db.collection("live_streams").document(streamId)
        try {
            db.runTransaction { tx ->
                val snap = tx.get(ref)
                if (!snap.exists()) return@runTransaction
                if (snap.getBoolean("isLive") != true) return@runTransaction
                @Suppress("UNCHECKED_CAST")
                val existingIds = (snap.get("viewerIds") as? List<String>).orEmpty()
                if (uid in existingIds) {
                    val updated = existingIds.filterNot { it == uid }
                    tx.update(
                        ref,
                        mapOf(
                            "viewerIds" to updated,
                            "viewerCount" to updated.size.toLong().coerceAtLeast(1L),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }
            }.await()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
        }
    }

    suspend fun endLiveStream(streamId: String): Result<LiveStreamSummaryUiModel> {
        val uid = requireUserId()
        val ref = db.collection("live_streams").document(streamId)
        return try {
            val snap = ref.get().await()
            if (!snap.exists()) {
                return Result.failure(IllegalStateException("Live stream not found."))
            }
            val hostId = snap.getString("hostId").orEmpty()
            val isCallerAdmin = if (hostId != uid) {
                db.collection("admins").document(uid).get().await().exists() ||
                    db.collection("users").document(uid).get().await().getString("role") == UserRoles.SUPER_ADMIN
            } else {
                false
            }
            if (hostId != uid && !isCallerAdmin) {
                return Result.failure(SecurityException("Only the live host can end this broadcast."))
            }

            val title = snap.getString("title") ?: "Live Broadcast"
            val hostUsername = runCatching {
                db.collection("users").document(hostId).get().await().getString("username").orEmpty()
            }.getOrDefault("")
            val startedAtMs = snap.getTimestamp("startedAt")?.toDate()?.time ?: System.currentTimeMillis()
            val endedAtMs = snap.getTimestamp("endedAt")?.toDate()?.time ?: System.currentTimeMillis()
            @Suppress("UNCHECKED_CAST")
            val viewerIds = (snap.get("viewerIds") as? List<String>).orEmpty()
            val peakViewers = (snap.getLong("peakViewerCount")
                ?: snap.getLong("viewerCount")
                ?: viewerIds.size.toLong()).coerceAtLeast(1L)
            val commentsCount = runCatching {
                ref.collection("comments").get().await().size()
            }.getOrDefault(0)

            val alreadyEnded = snap.getBoolean("isLive") == false || snap.getString("status") == LiveStreamStatus.ENDED
            if (!alreadyEnded) {
                ref.update(
                    mapOf(
                        "isLive" to false,
                        "status" to LiveStreamStatus.ENDED,
                        "viewerIds" to emptyList<String>(),
                        "viewerCount" to 0L,
                        "peakViewerCount" to peakViewers,
                        "endedAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()

                val notifyTargets = viewerIds.filter { it != hostId && it != uid }.distinct().take(50)
                notifyTargets.forEach { viewerUid ->
                    runCatching {
                        socialRepository.sendNotification(
                            viewerUid,
                            hostId.ifBlank { uid },
                            NotificationTypes.LIVE,
                            streamId,
                            "ended live broadcast: \"$title\""
                        )
                    }
                }
            }

            val nowMs = System.currentTimeMillis()
            val durationSeconds = ((if (alreadyEnded) endedAtMs else nowMs) - startedAtMs).coerceAtLeast(1000L) / 1000L
            Result.success(
                LiveStreamSummaryUiModel(
                    streamId = streamId,
                    title = title,
                    hostUsername = hostUsername,
                    durationSeconds = durationSeconds,
                    peakViewerCount = peakViewers,
                    commentsCount = commentsCount,
                    endedAt = if (alreadyEnded) endedAtMs else nowMs
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, ref.path)
            Result.failure(e)
        }
    }

    fun observeLiveComments(
        streamId: String,
        viewerId: String,
        sharedProfilesFlow: Flow<List<UserProfile>>? = null
    ): Flow<List<LiveCommentUiModel>> {
        val commentsFlow = db.collection("live_streams").document(streamId).collection("comments")
            .snapshots()
            .map { snap ->
                snap.documents.mapNotNull {
                    it.toObject(FirestoreLiveCommentDoc::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }
            }.handleErrors(viewerId, "live_streams/$streamId/comments")

        return combine(commentsFlow, sharedProfilesFlow ?: socialRepository.observeAllUserProfiles(viewerId)) { docs, profiles ->
            val userMap = profiles.associateBy { it.id }
            docs.mapNotNull { c ->
                val author = userMap[c.authorId] ?: return@mapNotNull null
                LiveCommentUiModel(
                    id = c.id,
                    author = author,
                    text = c.text,
                    createdAt = c.createdAt?.toDate()?.time ?: System.currentTimeMillis()
                )
            }.sortedBy { it.createdAt }
        }
    }

    suspend fun sendLiveComment(streamId: String, text: String): Result<Unit> {
        val uid = requireUserId()
        val cleanText = text.trim()
        if (cleanText.isEmpty()) return Result.failure(IllegalArgumentException("Comment cannot be empty."))
        val commentId = "lcmt_${UUID.randomUUID()}"
        val ref = db.collection("live_streams").document(streamId).collection("comments").document(commentId)
        return try {
            val streamSnap = db.collection("live_streams").document(streamId).get().await()
            if (!streamSnap.exists() || streamSnap.getBoolean("isLive") != true) {
                return Result.failure(IllegalStateException("Live broadcast has ended."))
            }
            ref.set(
                mapOf(
                    "streamId" to streamId,
                    "authorId" to uid,
                    "text" to cleanText.take(300),
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, ref.path)
            Result.failure(e)
        }
    }

    // --- Golden Verification Requests ---

    fun observeMyVerificationRequests(uid: String): Flow<List<VerificationRequestUiModel>> =
        db.collection("verification_requests")
            .whereEqualTo("userId", uid)
            .snapshots()
            .map { snap ->
                snap.documents.mapNotNull { doc ->
                    doc.toObject(FirestoreVerificationRequestDoc::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        ?.toUi()
                }.sortedByDescending { it.createdAt }
            }
            .handleErrors(uid, "verification_requests")

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun observeAllVerificationRequests(
        viewerId: String,
        sharedProfilesFlow: Flow<List<UserProfile>>? = null
    ): Flow<List<VerificationRequestUiModel>> =
        (sharedProfilesFlow ?: socialRepository.observeAllUserProfiles(viewerId)).flatMapLatest { profiles ->
            val me = profiles.firstOrNull { it.id == viewerId }
            val query = if (me?.isSuperAdmin == true) {
                db.collection("verification_requests")
            } else {
                db.collection("verification_requests").whereEqualTo("userId", viewerId)
            }
            query.snapshots().map { snap ->
                snap.documents.mapNotNull { doc ->
                    doc.toObject(FirestoreVerificationRequestDoc::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        ?.toUi()
                }.sortedByDescending { it.createdAt }
            }.handleErrors(viewerId, "verification_requests")
        }

    suspend fun submitVerificationRequest(
        category: String,
        reason: String,
        portfolioLink: String
    ): Result<Unit> {
        val uid = requireUserId()
        val cleanReason = reason.trim()
        if (cleanReason.length < 10) {
            return Result.failure(IllegalArgumentException("Please explain why you qualify (at least 10 characters)."))
        }
        val reqId = "vr_${UUID.randomUUID()}"
        val ref = db.collection("verification_requests").document(reqId)
        return try {
            val userSnap = db.collection("users").document(uid).get().await()
            val username = userSnap.getString("username") ?: return Result.failure(IllegalStateException("Profile not found."))
            if (userSnap.getBoolean("isGoldenVerified") == true) {
                return Result.failure(IllegalStateException("Your account already has an active Golden Verification badge."))
            }
            val existingReqs = db.collection("verification_requests")
                .whereEqualTo("userId", uid)
                .get()
                .await()
            if (existingReqs.documents.any { it.getString("status") == VerificationStatus.PENDING }) {
                return Result.failure(IllegalStateException("You already have a pending verification request under review."))
            }
            val displayName = userSnap.getString("displayName") ?: username
            ref.set(
                mapOf(
                    "userId" to uid,
                    "username" to username,
                    "displayName" to displayName.take(60),
                    "category" to category,
                    "reason" to cleanReason.take(500),
                    "portfolioLink" to portfolioLink.trim().take(200),
                    "status" to VerificationStatus.PENDING,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, ref.path)
            Result.failure(e)
        }
    }

    suspend fun adminReviewVerificationRequest(
        requestId: String,
        targetUserId: String,
        approved: Boolean,
        adminNote: String? = null
    ): Result<Unit> {
        val adminId = requireUserId()
        val reqRef = db.collection("verification_requests").document(requestId)
        val userRef = db.collection("users").document(targetUserId)
        return try {
            val updates = mutableMapOf<String, Any>(
                "status" to if (approved) VerificationStatus.APPROVED else VerificationStatus.REJECTED,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (!adminNote.isNullOrBlank()) {
                updates["adminNote"] = adminNote.trim().take(300)
            }
            val batch = db.batch()
            batch.update(reqRef, updates)
            batch.update(
                userRef,
                mapOf(
                    "isGoldenVerified" to approved,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
            socialRepository.sendNotification(
                targetUserId,
                adminId,
                NotificationTypes.VERIFICATION,
                requestId,
                if (approved) "Congratulations! Your Golden Verification badge has been approved."
                else "Your Golden Verification request was reviewed and declined."
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, reqRef.path)
            Result.failure(e)
        }
    }

    private fun FirestoreVerificationRequestDoc.toUi() = VerificationRequestUiModel(
        id = id,
        userId = userId,
        username = username,
        displayName = displayName,
        category = category,
        reason = reason,
        portfolioLink = portfolioLink,
        status = status,
        adminNote = adminNote,
        createdAt = createdAt?.toDate()?.time ?: System.currentTimeMillis()
    )
}
