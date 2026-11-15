package com.example.data.repository

import android.net.Uri
import android.os.Build
import com.example.data.local.AppPreferences
import com.example.data.local.SessionDataStore
import com.example.data.local.UserRoles
import com.example.data.model.FirestoreLoginActivityDoc
import com.example.data.model.FirestorePrivateInfoDoc
import com.example.data.model.FirestoreSupportTicketDoc
import com.example.data.model.FirestoreUserDoc
import com.example.data.model.LoginActivityUiModel
import com.example.data.model.SupportTicketUiModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

sealed interface ProfileLookupResult {
    data object Exists : ProfileLookupResult
    data object Missing : ProfileLookupResult
    data class Error(val message: String) : ProfileLookupResult
}

class FirestoreAccountRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth,
    private val sessionDataStore: SessionDataStore,
    private val mediaStorageHelper: MediaStorageHelper
) {
    val preferences: Flow<AppPreferences> = sessionDataStore.preferences

    private fun requireUserId(): String =
        auth.currentUser?.uid ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")

    suspend fun setDarkTheme(isDark: Boolean) = sessionDataStore.setDarkTheme(isDark)

    suspend fun setLanguageCode(code: String) = sessionDataStore.setLanguageCode(code)

    suspend fun setNotificationSettings(
        likes: Boolean? = null,
        comments: Boolean? = null,
        follows: Boolean? = null,
        messages: Boolean? = null,
        admin: Boolean? = null
    ) = sessionDataStore.setNotificationSettings(likes, comments, follows, messages, admin)

    fun observeHasProfileResult(uid: String): Flow<ProfileLookupResult> =
        db.collection("users").document(uid)
            .snapshots()
            .map<DocumentSnapshot, ProfileLookupResult> { snapshot ->
                if (snapshot.exists()) ProfileLookupResult.Exists else ProfileLookupResult.Missing
            }
            .catch { error ->
                val firestoreError = error.unwrapFirestoreException()
                if (firestoreError == null && error is CancellationException) throw error
                if (auth.currentUser?.uid == uid) {
                    handleFirestoreError(firestoreError ?: error, OperationType.GET, "users/$uid")
                }
                val ex = firestoreError ?: error
                val msg = when ((ex as? FirebaseFirestoreException)?.code) {
                    FirebaseFirestoreException.Code.UNAVAILABLE,
                    FirebaseFirestoreException.Code.DEADLINE_EXCEEDED ->
                        "Unable to verify your profile right now. Check your internet connection and retry."
                    FirebaseFirestoreException.Code.PERMISSION_DENIED ->
                        "Account permission check failed. Please sign in again."
                    else -> ex.localizedMessage ?: "Failed to load profile state from Cloud Firestore."
                }
                emit(ProfileLookupResult.Error(msg))
            }

    fun observeHasProfile(uid: String): Flow<Boolean> =
        observeHasProfileResult(uid).map { it is ProfileLookupResult.Exists }

    fun observePrivateInfo(uid: String): Flow<FirestorePrivateInfoDoc?> =
        db.collection("users").document(uid).collection("private").document("info")
            .snapshots()
            .map { snap ->
                snap.toObject(FirestorePrivateInfoDoc::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            }
            .catch { error ->
                val firestoreError = error.unwrapFirestoreException()
                if (firestoreError == null && error is CancellationException) throw error
                if (auth.currentUser?.uid == uid) {
                    handleFirestoreError(firestoreError ?: error, OperationType.GET, "users/$uid/private/info")
                }
                emit(null)
            }

    fun observeLoginActivity(uid: String): Flow<List<LoginActivityUiModel>> =
        db.collection("users").document(uid).collection("login_activity")
            .snapshots()
            .map { snap ->
                snap.documents.mapNotNull { doc ->
                    val model = doc.toObject(
                        FirestoreLoginActivityDoc::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ) ?: return@mapNotNull null
                    LoginActivityUiModel(
                        id = model.id,
                        deviceModel = model.deviceModel,
                        authMethod = model.authMethod,
                        createdAt = model.createdAt?.toDate()?.time ?: System.currentTimeMillis()
                    )
                }.sortedByDescending { it.createdAt }
            }
            .catch { error ->
                val firestoreError = error.unwrapFirestoreException()
                if (firestoreError == null && error is CancellationException) throw error
                if (auth.currentUser?.uid == uid) {
                    handleFirestoreError(firestoreError ?: error, OperationType.LIST, "users/$uid/login_activity")
                }
                emit(emptyList())
            }

    fun observeSupportTickets(uid: String): Flow<List<SupportTicketUiModel>> =
        db.collection("support_tickets")
            .whereEqualTo("userId", uid)
            .snapshots()
            .map { snap ->
                snap.documents.mapNotNull { doc ->
                    val model = doc.toObject(
                        FirestoreSupportTicketDoc::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ) ?: return@mapNotNull null
                    SupportTicketUiModel(
                        id = model.id,
                        subject = model.subject,
                        message = model.message,
                        status = model.status,
                        createdAt = model.createdAt?.toDate()?.time ?: System.currentTimeMillis()
                    )
                }.sortedByDescending { it.createdAt }
            }
            .catch { error ->
                val firestoreError = error.unwrapFirestoreException()
                if (firestoreError == null && error is CancellationException) throw error
                if (auth.currentUser?.uid == uid) {
                    handleFirestoreError(firestoreError ?: error, OperationType.LIST, "support_tickets")
                }
                emit(emptyList())
            }

    suspend fun ensureOfficialAdminBootstrap(): Result<Unit> {
        val currentUser = auth.currentUser ?: return Result.success(Unit)
        val uid = currentUser.uid
        val email = currentUser.email.orEmpty()
        if (!email.equals(OFFICIAL_ADMIN_EMAIL, ignoreCase = true) || !currentUser.isEmailVerified) {
            return Result.success(Unit)
        }
        return try {
            val adminRef = db.collection("admins").document(uid)
            val adminSnap = adminRef.get().await()
            if (!adminSnap.exists()) {
                adminRef.set(
                    mapOf(
                        "userId" to uid,
                        "email" to email,
                        "role" to UserRoles.SUPER_ADMIN,
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }

            val userRef = db.collection("users").document(uid)
            val userSnap = userRef.get().await()
            if (!userSnap.exists()) {
                val unameRef = db.collection("usernames").document(OFFICIAL_ADMIN_USERNAME)
                val unameSnap = unameRef.get().await()
                if (!unameSnap.exists()) {
                    unameRef.set(
                        mapOf(
                            "userId" to uid,
                            "createdAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                }
                val profilePayload = mapOf(
                    "userId" to uid,
                    "username" to OFFICIAL_ADMIN_USERNAME,
                    "displayName" to OFFICIAL_ADMIN_NAME,
                    "bio" to "Official Founder & Super Admin of SCROLL DOWN.",
                    "avatarUrl" to "drawable://img_admin_avatar",
                    "role" to UserRoles.SUPER_ADMIN,
                    "isGoldenVerified" to true,
                    "isPrivate" to false,
                    "isBanned" to false,
                    "demoFollowersDisplay" to OFFICIAL_ADMIN_DEMO_FOLLOWERS,
                    "followersCount" to 0L,
                    "followingCount" to 0L,
                    "postsCount" to 0L,
                    "lastActiveAt" to FieldValue.serverTimestamp(),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                userRef.set(profilePayload).await()

                db.collection("users").document(uid).collection("private").document("info").set(
                    mapOf(
                        "userId" to uid,
                        "email" to email,
                        "dateOfBirth" to "",
                        "twoFactorEnabled" to false,
                        "backupCodes" to emptyList<String>(),
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else if (
                userSnap.getString("role") != UserRoles.SUPER_ADMIN ||
                userSnap.getBoolean("isGoldenVerified") != true
            ) {
                userRef.update(
                    mapOf(
                        "role" to UserRoles.SUPER_ADMIN,
                        "isGoldenVerified" to true,
                        "demoFollowersDisplay" to OFFICIAL_ADMIN_DEMO_FOLLOWERS,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "admins/$uid")
            Result.failure(e)
        }
    }

    suspend fun checkUsernameAvailability(rawUsername: String): Result<Boolean> {
        val normalized = SecurityUtils.normalizeUsername(rawUsername)
        if (!SecurityUtils.isValidUsername(normalized)) {
            return Result.failure(IllegalArgumentException("Use 3-24 lowercase letters, numbers, underscores, or dots."))
        }
        val path = "usernames/$normalized"
        return try {
            val uid = auth.currentUser?.uid
            val snap = db.collection("usernames").document(normalized).get().await()
            val available = !snap.exists() || snap.getString("userId") == uid
            Result.success(available)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, path)
            Result.failure(e)
        }
    }

    suspend fun suggestAvailableUsernames(baseRaw: String): List<String> {
        val cleaned = SecurityUtils.normalizeUsername(baseRaw)
            .trim('.', '_')
            .ifBlank { "scrolldown" }
            .take(17)
        val stem = if (cleaned.length < 3) "${cleaned}sd" else cleaned
        val candidates = listOf(
            "${stem}_1",
            "${stem}_sd",
            "${stem}.app",
            "${stem}_${(10..99).random()}",
            "${stem}_${(100..999).random()}",
            "the_${stem}"
        )
            .map { SecurityUtils.normalizeUsername(it).take(24) }
            .distinct()
            .filter { SecurityUtils.isValidUsername(it) }

        val available = mutableListOf<String>()
        for (candidate in candidates) {
            if (available.size >= 3) break
            val ok = checkUsernameAvailability(candidate).getOrDefault(false)
            if (ok) available.add(candidate)
        }
        return available
    }

    suspend fun completeRegistration(
        displayName: String,
        rawUsername: String,
        dateOfBirth: String,
        bio: String,
        avatarUri: Uri?,
        isPrivate: Boolean
    ): Result<Unit> {
        val uid = requireUserId()
        val email = auth.currentUser?.email ?: ""
        val cleanName = displayName.trim()
        val normalizedUsername = SecurityUtils.normalizeUsername(rawUsername)
        val cleanDob = dateOfBirth.trim()

        if (cleanName.length < 2) {
            return Result.failure(IllegalArgumentException("Please enter your full name."))
        }
        if (!SecurityUtils.isValidUsername(normalizedUsername)) {
            return Result.failure(IllegalArgumentException("Username must be 3-24 chars (a-z, 0-9, _, .)."))
        }
        if (cleanDob.isNotBlank() && !SecurityUtils.isValidDob(cleanDob)) {
            return Result.failure(IllegalArgumentException("Date of birth must be YYYY-MM-DD (e.g. 2000-05-14)."))
        }

        val isOfficialAdmin = email.equals(OFFICIAL_ADMIN_EMAIL, ignoreCase = true) &&
            auth.currentUser?.isEmailVerified == true &&
            normalizedUsername == OFFICIAL_ADMIN_USERNAME

        return try {
            val unameRef = db.collection("usernames").document(normalizedUsername)
            val unameSnap = unameRef.get().await()
            if (unameSnap.exists() && unameSnap.getString("userId") != uid) {
                return Result.failure(IllegalStateException("@$normalizedUsername is already taken."))
            }

            var avatarUrl: String? = when {
                avatarUri != null ->
                    mediaStorageHelper.persistCompressedImage(avatarUri, maxDim = 480, quality = 75).getOrThrow()
                isOfficialAdmin -> "drawable://img_admin_avatar"
                else -> auth.currentUser?.photoUrl?.toString()?.take(500)
            }

            if (isOfficialAdmin) {
                db.collection("admins").document(uid).set(
                    mapOf(
                        "userId" to uid,
                        "email" to email,
                        "role" to UserRoles.SUPER_ADMIN,
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }

            if (!unameSnap.exists()) {
                unameRef.set(
                    mapOf(
                        "userId" to uid,
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }

            val userRef = db.collection("users").document(uid)
            val existingUserSnap = userRef.get().await()
            val existingCreatedAt = existingUserSnap.getTimestamp("createdAt")
            val profileMap = mutableMapOf<String, Any>(
                "userId" to uid,
                "username" to normalizedUsername,
                "displayName" to cleanName.take(60),
                "bio" to bio.trim().take(300),
                "role" to if (isOfficialAdmin) UserRoles.SUPER_ADMIN else UserRoles.USER,
                "isGoldenVerified" to isOfficialAdmin,
                "isPrivate" to isPrivate,
                "isBanned" to false,
                "demoFollowersDisplay" to if (isOfficialAdmin) OFFICIAL_ADMIN_DEMO_FOLLOWERS else 0L,
                "followersCount" to (existingUserSnap.getLong("followersCount") ?: 0L),
                "followingCount" to (existingUserSnap.getLong("followingCount") ?: 0L),
                "postsCount" to (existingUserSnap.getLong("postsCount") ?: 0L),
                "lastActiveAt" to FieldValue.serverTimestamp(),
                "createdAt" to (existingCreatedAt ?: FieldValue.serverTimestamp()),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (!avatarUrl.isNullOrBlank()) {
                profileMap["avatarUrl"] = avatarUrl
            }

            userRef.set(profileMap).await()

            val privateRef = userRef.collection("private").document("info")
            val existingPrivateSnap = runCatching { privateRef.get().await() }.getOrNull()
            privateRef.set(
                mapOf(
                    "userId" to uid,
                    "email" to email.ifBlank { "$normalizedUsername@scrolldown.app" },
                    "dateOfBirth" to cleanDob,
                    "twoFactorEnabled" to (existingPrivateSnap?.getBoolean("twoFactorEnabled") ?: false),
                    "backupCodes" to (existingPrivateSnap?.get("backupCodes") ?: emptyList<String>()),
                    "createdAt" to (existingPrivateSnap?.getTimestamp("createdAt") ?: FieldValue.serverTimestamp()),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            recordLoginActivity("GOOGLE_REGISTRATION")
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, "users/$uid")
            Result.failure(e)
        }
    }

    suspend fun recordLoginActivity(authMethod: String = "GOOGLE_CREDENTIAL_MANAGER"): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.success(Unit)
        val actId = "act_${UUID.randomUUID()}"
        val path = "users/$uid/login_activity/$actId"
        val device = "${Build.MANUFACTURER} ${Build.MODEL}".trim().ifBlank { "Android Device" }.take(100)
        return try {
            db.collection("users").document(uid)
                .collection("login_activity").document(actId)
                .set(
                    mapOf(
                        "userId" to uid,
                        "deviceModel" to device,
                        "authMethod" to authMethod.take(50),
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun toggleTwoFactor(enabled: Boolean): Result<List<String>> {
        val uid = requireUserId()
        val path = "users/$uid/private/info"
        return try {
            val codes = if (enabled) SecurityUtils.generateBackupCodes(6) else emptyList()
            db.document(path).update(
                mapOf(
                    "twoFactorEnabled" to enabled,
                    "backupCodes" to codes,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(codes)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateSecurityPin(newPin: String): Result<Unit> {
        val uid = requireUserId()
        if (newPin.trim().length < 4) {
            return Result.failure(IllegalArgumentException("Security passcode must be at least 4 characters."))
        }
        val path = "users/$uid/private/info"
        val salt = SecurityUtils.generateSalt()
        val hash = SecurityUtils.hashPassword(newPin.trim(), salt)
        return try {
            db.document(path).update(
                mapOf(
                    "securityPinHash" to "$salt:$hash",
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun verifySecurityChallenge(inputCode: String): Result<String> {
        val uid = requireUserId()
        val trimmed = inputCode.trim()
        if (trimmed.isEmpty()) {
            return Result.failure(IllegalArgumentException("Enter your security passcode or 2FA backup code."))
        }
        val path = "users/$uid/private/info"
        return try {
            val snap = db.document(path).get().await()
            val privateDoc = snap.toObject(FirestorePrivateInfoDoc::class.java)
                ?: return Result.failure(IllegalStateException("Private security record not found."))
            val pinPair = privateDoc.securityPinHash?.split(":", limit = 2)
            val pinValid = pinPair != null && pinPair.size == 2 &&
                SecurityUtils.verifyPassword(trimmed, pinPair[0], pinPair[1])
            val backupValid = privateDoc.backupCodes.any { it.equals(trimmed, ignoreCase = true) }
            when {
                pinValid -> Result.success("Security passcode verified ✓")
                backupValid -> Result.success("2FA backup recovery code verified ✓")
                else -> Result.failure(IllegalArgumentException("Passcode or recovery code does not match."))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, path)
            Result.failure(e)
        }
    }

    suspend fun submitSupportTicket(subject: String, message: String): Result<Unit> {
        val uid = requireUserId()
        val cleanSubject = subject.trim()
        val cleanMessage = message.trim()
        if (cleanSubject.length < 2 || cleanMessage.length < 4) {
            return Result.failure(IllegalArgumentException("Please enter a subject and detailed message."))
        }
        val ticketId = "tkt_${UUID.randomUUID()}"
        val path = "support_tickets/$ticketId"
        return try {
            db.document(path).set(
                mapOf(
                    "userId" to uid,
                    "subject" to cleanSubject.take(140),
                    "message" to cleanMessage.take(2000),
                    "status" to "OPEN",
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun exportUserInformationJson(): Result<String> {
        val uid = requireUserId()
        return try {
            val userSnap = db.collection("users").document(uid).get().await()
            val privateSnap = db.collection("users").document(uid).collection("private").document("info").get().await()
            val postsSnap = db.collection("posts").whereEqualTo("authorId", uid).get().await()
            val savedSnap = db.collection("users").document(uid).collection("saved").get().await()
            val activitySnap = db.collection("users").document(uid).collection("login_activity").get().await()

            val root = JSONObject().apply {
                put("app", "SCROLL DOWN")
                put("exportedAt", System.currentTimeMillis())
                put("profile", JSONObject(userSnap.data ?: emptyMap<String, Any>()))
                put("privateAccountInfo", JSONObject(privateSnap.data ?: emptyMap<String, Any>()))
                put("posts", JSONArray(postsSnap.documents.map { JSONObject(it.data ?: emptyMap<String, Any>()) }))
                put("savedPostIds", JSONArray(savedSnap.documents.map { it.id }))
                put("loginActivityCount", activitySnap.size())
            }
            Result.success(root.toString(2))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, "users/$uid")
            Result.failure(e)
        }
    }

    suspend fun deleteAccount(): Result<Unit> {
        val uid = requireUserId()
        return try {
            val userDoc = db.collection("users").document(uid).get().await()
            val username = userDoc.getString("username")

            // Clean up user-owned records
            val postsSnap = db.collection("posts").whereEqualTo("authorId", uid).get().await()
            for (doc in postsSnap.documents) {
                doc.reference.delete().await()
            }
            val storiesSnap = db.collection("stories").whereEqualTo("authorId", uid).get().await()
            for (doc in storiesSnap.documents) {
                doc.reference.delete().await()
            }
            val followingSnap = db.collection("follows").whereEqualTo("followerId", uid).get().await()
            for (doc in followingSnap.documents) {
                doc.reference.delete().await()
            }
            val followersSnap = db.collection("follows").whereEqualTo("followingId", uid).get().await()
            for (doc in followersSnap.documents) {
                doc.reference.delete().await()
            }
            val savedSnap = db.collection("users").document(uid).collection("saved").get().await()
            for (doc in savedSnap.documents) {
                doc.reference.delete().await()
            }
            val likedSnap = db.collection("users").document(uid).collection("liked").get().await()
            for (doc in likedSnap.documents) {
                doc.reference.delete().await()
            }
            val blocksSnap = db.collection("users").document(uid).collection("blocks").get().await()
            for (doc in blocksSnap.documents) {
                doc.reference.delete().await()
            }
            val actSnap = db.collection("users").document(uid).collection("login_activity").get().await()
            for (doc in actSnap.documents) {
                doc.reference.delete().await()
            }
            val notifSnap = db.collection("users").document(uid).collection("notifications").get().await()
            for (doc in notifSnap.documents) {
                doc.reference.delete().await()
            }
            val cfSnap = db.collection("users").document(uid).collection("close_friends").get().await()
            for (doc in cfSnap.documents) {
                doc.reference.delete().await()
            }
            val whSnap = db.collection("users").document(uid).collection("watch_history").get().await()
            for (doc in whSnap.documents) {
                doc.reference.delete().await()
            }
            runCatching { db.collection("notes").document(uid).delete().await() }
            db.collection("users").document(uid).collection("private").document("info").delete().await()
            if (!username.isNullOrBlank()) {
                db.collection("usernames").document(username).delete().await()
            }
            db.collection("users").document(uid).delete().await()
            try {
                auth.currentUser?.delete()?.await()
            } catch (_: FirebaseAuthRecentLoginRequiredException) {
                // Firestore user profile & data have been purged; caller will sign out cleanly.
            } catch (_: Exception) {
                // Keep cleanup idempotent even if Auth token expired offline.
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, "users/$uid")
            Result.failure(e)
        }
    }

    companion object {
        const val OFFICIAL_ADMIN_EMAIL = "utkarshgaming8029@gmail.com"
        const val OFFICIAL_ADMIN_USERNAME = "utkarshrai"
        const val OFFICIAL_ADMIN_NAME = "Utkarsh Rai"
        const val OFFICIAL_ADMIN_DEMO_FOLLOWERS = 300_000L
    }
}
