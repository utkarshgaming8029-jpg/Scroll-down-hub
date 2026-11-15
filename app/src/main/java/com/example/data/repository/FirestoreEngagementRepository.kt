package com.example.data.repository

import android.net.Uri
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
import java.io.File
import java.util.UUID

class FirestoreEngagementRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth,
    private val socialRepository: FirestoreSocialRepository,
    private val mediaStorageHelper: MediaStorageHelper
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

    fun observeCommentsForPost(
        postId: String,
        viewerId: String,
        sharedProfilesFlow: Flow<List<UserProfile>>? = null
    ): Flow<List<CommentUiModel>> {
        val commentsFlow = db.collection("posts").document(postId).collection("comments")
            .snapshots()
            .map { snap ->
                snap.documents.mapNotNull {
                    it.toObject(FirestoreCommentDoc::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }
            }.handleErrors(viewerId, "posts/$postId/comments")

        return combine(commentsFlow, sharedProfilesFlow ?: socialRepository.observeAllUserProfiles(viewerId)) { docs, profiles ->
            val userMap = profiles.associateBy { it.id }
            val allComments = docs.mapNotNull { c ->
                val author = userMap[c.authorId] ?: return@mapNotNull null
                CommentUiModel(
                    id = c.id,
                    postId = c.postId,
                    author = author,
                    parentCommentId = c.parentCommentId,
                    text = c.text,
                    createdAt = c.createdAt?.toDate()?.time ?: System.currentTimeMillis()
                )
            }.sortedBy { it.createdAt }

            val repliesByParent = allComments.filter { it.parentCommentId != null }.groupBy { it.parentCommentId }
            allComments.filter { it.parentCommentId == null }.map { root ->
                root.copy(replies = repliesByParent[root.id].orEmpty())
            }
        }
    }

    suspend fun addComment(postId: String, text: String, parentCommentId: String? = null): Result<Unit> {
        val uid = requireUserId()
        val cleanText = text.trim()
        if (cleanText.isEmpty()) return Result.failure(IllegalArgumentException("Comment cannot be empty."))
        val commentId = "cmt_${UUID.randomUUID()}"
        val commentRef = db.collection("posts").document(postId).collection("comments").document(commentId)
        val postRef = db.collection("posts").document(postId)
        return try {
            val payload = mutableMapOf<String, Any>(
                "postId" to postId,
                "authorId" to uid,
                "text" to cleanText.take(500),
                "createdAt" to FieldValue.serverTimestamp()
            )
            if (!parentCommentId.isNullOrBlank()) {
                payload["parentCommentId"] = parentCommentId
            }
            commentRef.set(payload).await()
            postRef.update(
                mapOf(
                    "commentsCount" to FieldValue.increment(1L),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            val postSnap = postRef.get().await()
            val postAuthorId = postSnap.getString("authorId")
            if (!postAuthorId.isNullOrBlank() && postAuthorId != uid) {
                socialRepository.sendNotification(
                    postAuthorId,
                    uid,
                    if (parentCommentId == null) NotificationTypes.COMMENT else NotificationTypes.REPLY,
                    postId,
                    if (parentCommentId == null) "commented: \"${cleanText.take(50)}\"" else "replied: \"${cleanText.take(50)}\""
                )
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, commentRef.path)
            Result.failure(e)
        }
    }

    fun observeConversations(
        viewerId: String,
        sharedProfilesFlow: Flow<List<UserProfile>>? = null
    ): Flow<List<ConversationUiModel>> {
        val convsFlow = db.collection("conversations")
            .whereArrayContains("participantIds", viewerId)
            .snapshots()
            .map { snap ->
                snap.documents.mapNotNull {
                    it.toObject(FirestoreConversationDoc::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }
            }.handleErrors(viewerId, "conversations")

        return combine(convsFlow, sharedProfilesFlow ?: socialRepository.observeAllUserProfiles(viewerId)) { convs, profiles ->
            val userMap = profiles.associateBy { it.id }
            convs.mapNotNull { c ->
                val participants = c.participantIds.mapNotNull { userMap[it] }
                val peerId = c.participantIds.firstOrNull { it != viewerId }
                val peer = peerId?.let { userMap[it] }
                if (!c.isGroup && peer?.isBlockedByMe == true) return@mapNotNull null
                if (!c.isGroup && peer == null) return@mapNotNull null

                val lastMsgMs = c.lastMessageAt?.toDate()?.time ?: System.currentTimeMillis()
                val myReadMs = c.lastReadMs[viewerId] ?: 0L
                val isUnread = c.lastSenderId != null && c.lastSenderId != viewerId && lastMsgMs > myReadMs

                ConversationUiModel(
                    conversationId = c.id,
                    peer = peer,
                    participants = participants,
                    isGroup = c.isGroup,
                    groupName = c.groupName.orEmpty(),
                    lastMessage = c.lastMessage,
                    lastSenderId = c.lastSenderId,
                    lastMessageMediaType = c.lastMessageMediaType,
                    lastMessageAt = lastMsgMs,
                    unreadCount = if (isUnread) 1 else 0,
                    isPeerTyping = c.typingUids.any { it != viewerId }
                )
            }.sortedByDescending { it.lastMessageAt }
        }
    }

    fun observeConversation(
        convId: String,
        viewerId: String,
        sharedConversationsFlow: Flow<List<ConversationUiModel>>? = null
    ): Flow<ConversationUiModel?> =
        (sharedConversationsFlow ?: observeConversations(viewerId)).map { list -> list.firstOrNull { it.conversationId == convId } }

    fun observeMessages(convId: String, viewerId: String): Flow<List<MessageUiModel>> =
        db.collection("conversations").document(convId).collection("messages")
            .whereArrayContains("participantIds", viewerId)
            .snapshots()
            .map { snap ->
                snap.documents.mapNotNull {
                    val m = it.toObject(FirestoreMessageDoc::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        ?: return@mapNotNull null
                    MessageUiModel(
                        id = m.id,
                        conversationId = convId,
                        senderId = m.senderId,
                        recipientId = m.recipientId,
                        text = m.text,
                        mediaUri = m.mediaUrl,
                        mediaType = m.mediaType,
                        reactions = m.reactions,
                        isSeen = m.isSeen,
                        createdAt = m.createdAt?.toDate()?.time ?: System.currentTimeMillis()
                    )
                }.sortedBy { it.createdAt }
            }
            .handleErrors(viewerId, "conversations/$convId/messages")

    fun observeMessagesWithPeer(viewerId: String, peerId: String): Flow<List<MessageUiModel>> =
        observeMessages(SecurityUtils.buildConversationId(viewerId, peerId), viewerId)

    fun observePeerTyping(viewerId: String, peerId: String): Flow<Boolean> =
        observeConversation(SecurityUtils.buildConversationId(viewerId, peerId), viewerId)
            .map { it?.isPeerTyping == true }

    suspend fun createGroupConversation(groupName: String, memberIds: List<String>): Result<String> {
        val creatorId = requireUserId()
        val cleanName = groupName.trim().ifBlank { "Group Chat" }.take(80)
        val participants = (memberIds + creatorId).distinct().take(25).sorted()
        if (participants.size < 2) {
            return Result.failure(IllegalArgumentException("Select at least one member for the group chat."))
        }
        val convId = "grp_${UUID.randomUUID()}"
        val convRef = db.collection("conversations").document(convId)
        val msgId = "msg_${UUID.randomUUID()}"
        val msgRef = convRef.collection("messages").document(msgId)
        val nowMs = System.currentTimeMillis()
        return try {
            convRef.set(
                mapOf(
                    "participantIds" to participants,
                    "isGroup" to true,
                    "groupName" to cleanName,
                    "groupAdminId" to creatorId,
                    "lastSenderId" to creatorId,
                    "lastReadMs" to mapOf(creatorId to nowMs),
                    "lastMessage" to "Created group \"$cleanName\"",
                    "lastMessageMediaType" to MediaTypes.TEXT,
                    "typingUids" to emptyList<String>(),
                    "lastMessageAt" to FieldValue.serverTimestamp(),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            msgRef.set(
                mapOf(
                    "participantIds" to participants,
                    "senderId" to creatorId,
                    "recipientId" to participants.first { it != creatorId },
                    "text" to "Created group \"$cleanName\"",
                    "mediaType" to MediaTypes.TEXT,
                    "reactions" to emptyMap<String, String>(),
                    "isSeen" to false,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(convId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, convRef.path)
            Result.failure(e)
        }
    }

    suspend fun sendMessage(
        recipientId: String,
        text: String,
        mediaUri: Uri? = null,
        isVideo: Boolean = false,
        voiceFile: File? = null
    ): Result<Unit> {
        val senderId = requireUserId()
        val convId = SecurityUtils.buildConversationId(senderId, recipientId)
        return sendMessageToConversation(
            conversationId = convId,
            directPeerId = recipientId,
            text = text,
            mediaUri = mediaUri,
            isVideo = isVideo,
            voiceFile = voiceFile
        )
    }

    suspend fun sendMessageToConversation(
        conversationId: String,
        directPeerId: String? = null,
        text: String,
        mediaUri: Uri? = null,
        isVideo: Boolean = false,
        voiceFile: File? = null
    ): Result<Unit> {
        val senderId = requireUserId()
        val cleanText = text.trim()
        if (cleanText.isEmpty() && mediaUri == null && voiceFile == null) {
            return Result.failure(IllegalArgumentException("Message cannot be empty."))
        }
        val convRef = db.collection("conversations").document(conversationId)
        val msgId = "msg_${UUID.randomUUID()}"
        val msgRef = convRef.collection("messages").document(msgId)

        return try {
            val convSnap = convRef.get().await()
            val participants: List<String> = if (convSnap.exists()) {
                @Suppress("UNCHECKED_CAST")
                (convSnap.get("participantIds") as? List<String>).orEmpty()
            } else {
                val peer = directPeerId
                    ?: return Result.failure(IllegalStateException("Conversation not found."))
                listOf(senderId, peer).sorted()
            }
            if (participants.size < 2 || senderId !in participants) {
                return Result.failure(IllegalStateException("You are not a participant in this chat."))
            }

            val persistedMedia = when {
                voiceFile != null -> mediaStorageHelper.persistVoiceNote(voiceFile).getOrThrow()
                mediaUri == null -> null
                isVideo -> mediaStorageHelper.persistVideo(mediaUri).getOrThrow()
                else -> mediaStorageHelper.persistCompressedImage(mediaUri, maxDim = 540, quality = 70).getOrThrow()
            }
            val mediaType = when {
                voiceFile != null -> MediaTypes.AUDIO
                persistedMedia == null -> MediaTypes.TEXT
                isVideo -> MediaTypes.VIDEO
                else -> MediaTypes.PHOTO
            }
            val preview = cleanText.ifBlank {
                when (mediaType) {
                    MediaTypes.AUDIO -> "Sent a voice message"
                    MediaTypes.VIDEO -> "Sent a video"
                    else -> "Sent a photo"
                }
            }.take(500)
            val nowMs = System.currentTimeMillis()

            if (!convSnap.exists()) {
                convRef.set(
                    mapOf(
                        "participantIds" to participants,
                        "isGroup" to false,
                        "lastSenderId" to senderId,
                        "lastReadMs" to mapOf(senderId to nowMs),
                        "lastMessage" to preview,
                        "lastMessageMediaType" to mediaType,
                        "typingUids" to emptyList<String>(),
                        "lastMessageAt" to FieldValue.serverTimestamp(),
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                convRef.update(
                    mapOf(
                        "lastSenderId" to senderId,
                        "lastReadMs.$senderId" to nowMs,
                        "lastMessage" to preview,
                        "lastMessageMediaType" to mediaType,
                        "typingUids" to FieldValue.arrayRemove(senderId),
                        "lastMessageAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }

            val primaryRecipient = participants.firstOrNull { it != senderId } ?: senderId
            val msgPayload = mutableMapOf<String, Any>(
                "participantIds" to participants,
                "senderId" to senderId,
                "recipientId" to primaryRecipient,
                "text" to cleanText.take(2000),
                "mediaType" to mediaType,
                "reactions" to emptyMap<String, String>(),
                "isSeen" to false,
                "createdAt" to FieldValue.serverTimestamp()
            )
            if (!persistedMedia.isNullOrBlank()) {
                msgPayload["mediaUrl"] = persistedMedia
            }
            msgRef.set(msgPayload).await()

            participants.filter { it != senderId }.take(10).forEach { targetUid ->
                socialRepository.sendNotification(
                    targetUid,
                    senderId,
                    NotificationTypes.MESSAGE,
                    conversationId,
                    "sent a message: \"${preview.take(40)}\""
                )
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, msgRef.path)
            Result.failure(e)
        }
    }

    suspend fun toggleMessageReaction(conversationId: String, messageId: String, emoji: String): Result<Unit> {
        val uid = requireUserId()
        val msgRef = db.collection("conversations").document(conversationId).collection("messages").document(messageId)
        return try {
            val snap = msgRef.get().await()
            @Suppress("UNCHECKED_CAST")
            val current = (snap.get("reactions") as? Map<String, String>).orEmpty()
            val updateVal: Any = if (current[uid] == emoji) FieldValue.delete() else emoji.take(8)
            msgRef.update("reactions.$uid", updateVal).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, msgRef.path)
            Result.failure(e)
        }
    }

    suspend fun setTypingStatus(peerId: String, typing: Boolean) {
        val uid = auth.currentUser?.uid ?: return
        setTypingStatusByConvId(SecurityUtils.buildConversationId(uid, peerId), typing)
    }

    suspend fun setTypingStatusByConvId(conversationId: String, typing: Boolean) {
        val uid = auth.currentUser?.uid ?: return
        val convRef = db.collection("conversations").document(conversationId)
        try {
            val snap = convRef.get().await()
            if (snap.exists()) {
                convRef.update(
                    mapOf(
                        "typingUids" to if (typing) FieldValue.arrayUnion(uid) else FieldValue.arrayRemove(uid),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, convRef.path)
        }
    }

    suspend fun markConversationRead(peerId: String) {
        val uid = auth.currentUser?.uid ?: return
        markConversationReadByConvId(SecurityUtils.buildConversationId(uid, peerId))
    }

    suspend fun markConversationReadByConvId(conversationId: String) {
        val uid = auth.currentUser?.uid ?: return
        val convRef = db.collection("conversations").document(conversationId)
        try {
            val snap = convRef.get().await()
            if (snap.exists()) {
                convRef.update(
                    mapOf(
                        "lastReadMs.$uid" to System.currentTimeMillis(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            val msgs = convRef.collection("messages")
                .whereArrayContains("participantIds", uid)
                .get()
                .await()
            for (doc in msgs.documents) {
                if (doc.getString("senderId") != uid && doc.getBoolean("isSeen") == false) {
                    doc.reference.update("isSeen", true).await()
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "conversations/$conversationId/messages")
        }
    }

    fun observeNotifications(
        uid: String,
        sharedProfilesFlow: Flow<List<UserProfile>>? = null,
        sharedFollowsFlow: Flow<List<FollowEntity>>? = null
    ): Flow<List<NotificationUiModel>> {
        val notifsFlow = db.collection("users").document(uid).collection("notifications")
            .snapshots()
            .map { snap ->
                snap.documents.mapNotNull {
                    it.toObject(FirestoreNotificationDoc::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }
            }.handleErrors(uid, "users/$uid/notifications")

        return combine(
            notifsFlow,
            sharedProfilesFlow ?: socialRepository.observeAllUserProfiles(uid),
            sharedFollowsFlow ?: socialRepository.observeFollowRelations(uid)
        ) { docs, profiles, follows ->
            val userMap = profiles.associateBy { it.id }
            docs.mapNotNull { n ->
                val actor = userMap[n.actorId] ?: return@mapNotNull null
                val pendingReq = n.type == NotificationTypes.FOLLOW_REQUEST &&
                    follows.any { it.followerId == n.actorId && it.followingId == uid && it.status == FollowStatus.PENDING }
                NotificationUiModel(
                    id = n.id,
                    actor = actor,
                    type = n.type,
                    referenceId = n.referenceId,
                    message = n.message,
                    isRead = n.isRead,
                    isPendingFollowRequest = pendingReq,
                    createdAt = n.createdAt?.toDate()?.time ?: System.currentTimeMillis()
                )
            }.sortedByDescending { it.createdAt }
        }
    }

    suspend fun markAllNotificationsRead() {
        val uid = auth.currentUser?.uid ?: return
        try {
            val snap = db.collection("users").document(uid).collection("notifications").get().await()
            for (doc in snap.documents) {
                if (doc.getBoolean("isRead") == false) {
                    doc.reference.update("isRead", true).await()
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "users/$uid/notifications")
        }
    }

    suspend fun submitReport(targetType: String, targetId: String, reason: String): Result<Unit> {
        val reporterId = requireUserId()
        val cleanReason = reason.trim()
        if (cleanReason.length < 2) {
            return Result.failure(IllegalArgumentException("Please describe why you are reporting this."))
        }
        val reportId = "rep_${UUID.randomUUID()}"
        val ref = db.collection("reports").document(reportId)
        return try {
            ref.set(
                mapOf(
                    "reporterId" to reporterId,
                    "targetType" to targetType,
                    "targetId" to targetId,
                    "targetSummary" to "$targetType ($targetId)".take(200),
                    "reason" to cleanReason.take(500),
                    "status" to ReportStatus.OPEN,
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

    // --- Super Admin Operations ---
    fun observeAdminAnalytics(
        viewerId: String,
        sharedProfilesFlow: Flow<List<UserProfile>>? = null,
        sharedPostsFlow: Flow<List<PostUiModel>>? = null,
        sharedReportsFlow: Flow<List<ReportUiModel>>? = null
    ): Flow<AdminAnalytics> =
        combine(
            sharedProfilesFlow ?: socialRepository.observeAllUserProfiles(viewerId),
            sharedPostsFlow ?: socialRepository.observeAllPostsUi(viewerId, includeArchived = true),
            sharedReportsFlow ?: observeAdminReports(viewerId, sharedProfilesFlow)
        ) { users, posts, reports ->
            AdminAnalytics(
                totalUsers = users.size.toLong(),
                totalPosts = posts.count { !it.isReel }.toLong(),
                totalReels = posts.count { it.isReel }.toLong(),
                totalComments = posts.sumOf { it.commentsCount },
                openReports = reports.count { it.status == ReportStatus.OPEN }.toLong(),
                verifiedUsers = users.count { it.isGoldenVerified }.toLong(),
                bannedUsers = users.count { it.isBanned }.toLong()
            )
        }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun observeAdminReports(
        viewerId: String,
        sharedProfilesFlow: Flow<List<UserProfile>>? = null
    ): Flow<List<ReportUiModel>> {
        return (sharedProfilesFlow ?: socialRepository.observeAllUserProfiles(viewerId)).flatMapLatest { profiles ->
            val me = profiles.firstOrNull { it.id == viewerId }
            val query = if (me?.isSuperAdmin == true) {
                db.collection("reports")
            } else {
                db.collection("reports").whereEqualTo("reporterId", viewerId)
            }
            query.snapshots().map { snap ->
                val userMap = profiles.associateBy { it.id }
                snap.documents.mapNotNull { doc ->
                    val r = doc.toObject(FirestoreReportDoc::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        ?: return@mapNotNull null
                    val reporter = userMap[r.reporterId] ?: return@mapNotNull null
                    ReportUiModel(
                        id = r.id,
                        reporter = reporter,
                        targetType = r.targetType,
                        targetId = r.targetId,
                        targetSummary = r.targetSummary,
                        reason = r.reason,
                        status = r.status,
                        createdAt = r.createdAt?.toDate()?.time ?: System.currentTimeMillis()
                    )
                }.sortedByDescending { it.createdAt }
            }.handleErrors(viewerId, "reports")
        }
    }

    suspend fun adminSetGoldenVerification(targetUserId: String, verified: Boolean): Result<Unit> {
        val adminId = requireUserId()
        val ref = db.collection("users").document(targetUserId)
        return try {
            val targetSnap = ref.get().await()
            if (!targetSnap.exists()) {
                return Result.failure(IllegalStateException("Target user account was not found."))
            }
            val pendingSnap = runCatching {
                db.collection("verification_requests")
                    .whereEqualTo("userId", targetUserId)
                    .get()
                    .await()
            }.getOrNull()
            val batch = db.batch()
            batch.update(
                ref,
                mapOf(
                    "isGoldenVerified" to verified,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            pendingSnap?.documents.orEmpty()
                .filter { it.getString("status") == VerificationStatus.PENDING }
                .forEach { doc ->
                    batch.update(
                        doc.reference,
                        mapOf(
                            "status" to if (verified) VerificationStatus.APPROVED else VerificationStatus.REJECTED,
                            "adminNote" to if (verified) "Granted directly by Super Admin." else "Declined by Super Admin.",
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }
            batch.commit().await()
            socialRepository.sendNotification(
                targetUserId,
                adminId,
                NotificationTypes.VERIFICATION,
                targetUserId,
                if (verified) "Congratulations! Super Admin granted you the Golden Verification badge."
                else "Your Golden Verification badge was removed by Super Admin."
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, ref.path)
            Result.failure(e)
        }
    }

    suspend fun adminSetUserBan(targetUserId: String, banned: Boolean): Result<Unit> {
        requireUserId()
        val ref = db.collection("users").document(targetUserId)
        return try {
            ref.update(
                mapOf(
                    "isBanned" to banned,
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

    suspend fun adminDeletePost(postId: String): Result<Unit> {
        requireUserId()
        val ref = db.collection("posts").document(postId)
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

    suspend fun adminUpdateReportStatus(reportId: String, status: String): Result<Unit> {
        requireUserId()
        val ref = db.collection("reports").document(reportId)
        return try {
            ref.update(
                mapOf(
                    "status" to status,
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

    suspend fun adminBroadcastAnnouncement(message: String): Result<Unit> {
        val adminId = requireUserId()
        val clean = message.trim()
        if (clean.isEmpty()) return Result.failure(IllegalArgumentException("Announcement cannot be empty."))
        return try {
            val usersSnap = db.collection("users").get().await()
            for (doc in usersSnap.documents) {
                val uid = doc.id
                if (uid != adminId) {
                    socialRepository.sendNotification(
                        uid,
                        adminId,
                        NotificationTypes.ADMIN_ANNOUNCEMENT,
                        "",
                        clean
                    )
                }
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "users")
            Result.failure(e)
        }
    }
}
