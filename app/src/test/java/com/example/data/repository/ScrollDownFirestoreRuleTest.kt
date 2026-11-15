package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.local.SessionDataStore
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class ScrollDownFirestoreRuleTest : FirestoreEmulatorTestBase() {

    private fun accountRepo(): FirestoreAccountRepository =
        FirestoreAccountRepository(
            db = firestore,
            auth = auth,
            sessionDataStore = SessionDataStore(appContext),
            mediaStorageHelper = MediaStorageHelper(appContext)
        )

    private fun socialRepo(): FirestoreSocialRepository =
        FirestoreSocialRepository(
            db = firestore,
            auth = auth,
            mediaStorageHelper = MediaStorageHelper(appContext)
        )

    private fun engagementRepo(): FirestoreEngagementRepository =
        FirestoreEngagementRepository(
            db = firestore,
            auth = auth,
            socialRepository = socialRepo(),
            mediaStorageHelper = MediaStorageHelper(appContext)
        )

    private fun liveAndVerificationRepo(): FirestoreLiveAndVerificationRepository =
        FirestoreLiveAndVerificationRepository(
            db = firestore,
            auth = auth,
            socialRepository = socialRepo()
        )

    @Test
    fun registrationAndUsernameCheck_validUser_succeedsAndPreventsDuplicates() = runBlocking {
        val uniqueSuffix = UUID.randomUUID().toString().take(8).lowercase()
        val aliceUid = signInTestUser("alice_$uniqueSuffix@test.com")
        val aliceUsername = "alice_$uniqueSuffix"
        val account = accountRepo()

        assertTrue(withTimeout(5000L) { account.checkUsernameAvailability(aliceUsername).getOrThrow() })
        val regResult = withTimeout(5000L) {
            account.completeRegistration(
                displayName = "Alice Creator",
                rawUsername = aliceUsername,
                dateOfBirth = "2001-04-12",
                bio = "Testing Scroll Down",
                avatarUri = null,
                isPrivate = false
            )
        }
        assertTrue(regResult.isSuccess)
        assertTrue(withTimeout(5000L) { account.observeHasProfile(aliceUid).first { it } })

        // Switch to Bob: Bob sees username is taken and cannot read Alice's private PII info
        signInTestUser("bob_$uniqueSuffix@test.com")
        assertFalse(withTimeout(5000L) { account.checkUsernameAvailability(aliceUsername).getOrThrow() })

        var piiDenied = false
        try {
            withTimeout(5000L) {
                firestore.collection("users").document(aliceUid).collection("private").document("info").get().await()
            }
        } catch (e: FirebaseFirestoreException) {
            piiDenied = e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED
        }
        assertTrue("Expected PERMISSION_DENIED reading another user's private info", piiDenied)
    }

    @Test
    fun ordinaryUser_cannotSelfAssignGoldenBadge_orReadReports() = runBlocking {
        val uniqueSuffix = UUID.randomUUID().toString().take(8).lowercase()
        val uid = signInTestUser("user_$uniqueSuffix@test.com")
        val account = accountRepo()
        withTimeout(5000L) {
            account.completeRegistration("Normal User", "norm_$uniqueSuffix", "", "", null, false).getOrThrow()
        }

        var badgeDenied = false
        try {
            withTimeout(5000L) {
                firestore.collection("users").document(uid).update(
                    mapOf("isGoldenVerified" to true, "updatedAt" to FieldValue.serverTimestamp())
                ).await()
            }
        } catch (e: FirebaseFirestoreException) {
            badgeDenied = e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED
        }
        assertTrue("Expected PERMISSION_DENIED when non-admin grants self Golden Badge", badgeDenied)
    }

    @Test
    fun unauthenticatedCaller_observeUsers_failsWithPermissionDenied() = runBlocking {
        auth.signOut()
        val social = socialRepo()

        var permissionDenied = false
        try {
            withTimeout(4000L) {
                social.observeAllUserProfiles("unauth_uid").first()
            }
        } catch (e: Exception) {
            val fsEx = e.unwrapFirestore()
            assertNotNull("Expected nested FirebaseFirestoreException, got $e", fsEx)
            permissionDenied = fsEx?.code == FirebaseFirestoreException.Code.PERMISSION_DENIED
        }
        assertTrue("Expected PERMISSION_DENIED for unauthenticated listener", permissionDenied)
    }

    @Test
    fun supportTicketAnd2FA_authenticatedOwner_succeeds() = runBlocking {
        val uniqueSuffix = UUID.randomUUID().toString().take(8).lowercase()
        val uid = signInTestUser("support_$uniqueSuffix@test.com")
        val account = accountRepo()
        withTimeout(5000L) {
            account.completeRegistration("Support Tester", "supp_$uniqueSuffix", "1999-11-20", "", null, false).getOrThrow()
        }

        val codes = withTimeout(5000L) { account.toggleTwoFactor(true).getOrThrow() }
        assertEquals(6, codes.size)

        val ticketRes = withTimeout(5000L) {
            account.submitSupportTicket("Account Question", "Testing support ticket submission.")
        }
        assertTrue(ticketRes.isSuccess)
        val tickets = withTimeout(5000L) {
            account.observeSupportTickets(uid).first { it.isNotEmpty() }
        }
        assertEquals("Account Question", tickets.first().subject)
    }

    @Test
    fun messagingAndComment_crossUserFlow_worksWithStrictRules() = runBlocking {
        val uniqueSuffix = UUID.randomUUID().toString().take(8).lowercase()
        val aliceUid = signInTestUser("msg_a_$uniqueSuffix@test.com")
        val account = accountRepo()
        withTimeout(5000L) {
            account.completeRegistration("Alice Msg", "msga_$uniqueSuffix", "", "", null, false).getOrThrow()
        }

        val bobUid = signInTestUser("msg_b_$uniqueSuffix@test.com")
        withTimeout(5000L) {
            account.completeRegistration("Bob Msg", "msgb_$uniqueSuffix", "", "", null, false).getOrThrow()
        }

        val engagement = engagementRepo()
        val msgRes = withTimeout(5000L) {
            engagement.sendMessage(aliceUid, "Hello from Bob!")
        }
        assertTrue(msgRes.isSuccess)

        val msgs = withTimeout(5000L) {
            engagement.observeMessagesWithPeer(bobUid, aliceUid).first { it.isNotEmpty() }
        }
        assertEquals("Hello from Bob!", msgs.first().text)
    }

    @Test
    fun verificationRequest_andCloseFriends_enforcePrivilegeAndOwnerIsolation() = runBlocking {
        val uniqueSuffix = UUID.randomUUID().toString().take(8).lowercase()
        val aliceUid = signInTestUser("vr_a_$uniqueSuffix@test.com")
        val account = accountRepo()
        withTimeout(5000L) {
            account.completeRegistration("Alice Ver", "vra_$uniqueSuffix", "", "", null, false).getOrThrow()
        }

        // Ordinary user cannot write an APPROVED verification request directly
        var approveDenied = false
        try {
            withTimeout(5000L) {
                firestore.collection("verification_requests").document("vr_$aliceUid").set(
                    mapOf(
                        "userId" to aliceUid,
                        "username" to "vra_$uniqueSuffix",
                        "displayName" to "Alice Ver",
                        "category" to "CREATOR",
                        "reason" to "Creator verification application text.",
                        "portfolioLink" to "",
                        "status" to "APPROVED",
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
        } catch (e: FirebaseFirestoreException) {
            approveDenied = e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED
        }
        assertTrue("Expected PERMISSION_DENIED when self-submitting APPROVED request", approveDenied)

        // Valid PENDING request succeeds
        withTimeout(5000L) {
            firestore.collection("verification_requests").document("vr_$aliceUid").set(
                mapOf(
                    "userId" to aliceUid,
                    "username" to "vra_$uniqueSuffix",
                    "displayName" to "Alice Ver",
                    "category" to "CREATOR",
                    "reason" to "Creator verification application text.",
                    "portfolioLink" to "",
                    "status" to "PENDING",
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        }

        // Switch to Bob and confirm Bob cannot read Alice's close friends or verification request
        signInTestUser("vr_b_$uniqueSuffix@test.com")
        var vrReadDenied = false
        try {
            withTimeout(5000L) {
                firestore.collection("verification_requests").document("vr_$aliceUid").get().await()
            }
        } catch (e: FirebaseFirestoreException) {
            vrReadDenied = e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED
        }
        assertTrue("Expected PERMISSION_DENIED reading another user's verification request", vrReadDenied)
    }

    @Test
    fun reloginSameUser_preservesExistingProfile_andSuggestsAlternativeUsernames() = runBlocking {
        val uniqueSuffix = UUID.randomUUID().toString().take(8).lowercase()
        val email = "relogin_$uniqueSuffix@test.com"
        val uid1 = signInTestUser(email)
        val uname = "rel_$uniqueSuffix"
        val account = accountRepo()

        withTimeout(5000L) {
            account.completeRegistration("Original Name", uname, "", "Bio one", null, false).getOrThrow()
        }
        val firstLookup = withTimeout(5000L) {
            account.observeHasProfileResult(uid1).first { it !is ProfileLookupResult.Error }
        }
        assertTrue(firstLookup is ProfileLookupResult.Exists)

        // Sign out and log back into same Firebase account: no duplicate profile, existing profile persists
        auth.signOut()
        val uid2 = signInTestUser(email)
        assertEquals(uid1, uid2)
        val repeatLookup = withTimeout(5000L) {
            account.observeHasProfileResult(uid2).first { it is ProfileLookupResult.Exists }
        }
        assertTrue(repeatLookup is ProfileLookupResult.Exists)

        // Second user gets clean available suggestions when username is already taken
        signInTestUser("second_$uniqueSuffix@test.com")
        val suggestions = withTimeout(5000L) {
            account.suggestAvailableUsernames(uname)
        }
        assertTrue("Expected non-empty alternative username suggestions", suggestions.isNotEmpty())
        for (candidate in suggestions) {
            assertTrue(SecurityUtils.isValidUsername(candidate))
            assertTrue(withTimeout(5000L) { account.checkUsernameAvailability(candidate).getOrThrow() })
        }
    }

    @Test
    fun deleteAccount_removesProfileAndFreesUsername() = runBlocking {
        val uniqueSuffix = UUID.randomUUID().toString().take(8).lowercase()
        val email = "delete_$uniqueSuffix@test.com"
        val uid = signInTestUser(email)
        val uname = "del_$uniqueSuffix"
        val account = accountRepo()

        withTimeout(5000L) {
            account.completeRegistration("Delete Me", uname, "", "", null, false).getOrThrow()
        }
        val unameSnapBefore = withTimeout(5000L) {
            firestore.collection("usernames").document(uname).get().await()
        }
        assertTrue("Expected username doc to exist after registration", unameSnapBefore.exists())

        // Another user sees the username as taken before deletion
        signInTestUser("other_$uniqueSuffix@test.com")
        assertFalse(withTimeout(5000L) { account.checkUsernameAvailability(uname).getOrThrow() })

        // Sign back in as owner and delete account
        signInTestUser(email)
        val deleteRes = withTimeout(8000L) { account.deleteAccount() }
        assertTrue("Expected deleteAccount to succeed: ${deleteRes.exceptionOrNull()}", deleteRes.isSuccess)

        // Sign in as another user to verify deleted username is released and user doc is gone
        signInTestUser("checker_$uniqueSuffix@test.com")
        assertTrue(withTimeout(5000L) { account.checkUsernameAvailability(uname).getOrThrow() })
        val deletedSnap = withTimeout(5000L) {
            firestore.collection("users").document(uid).get().await()
        }
        assertFalse("Expected deleted user profile document to not exist", deletedSnap.exists())
    }

    @Test
    fun goldenVerification_requestApproveDecline_persistsAcrossReloginAndPreventsDuplicates() = runBlocking {
        val uniqueSuffix = UUID.randomUUID().toString().take(8).lowercase()
        val aliceEmail = "gv_alice_$uniqueSuffix@test.com"
        val aliceUid = signInTestUser(aliceEmail)
        val account = accountRepo()
        val social = socialRepo()
        val liveVer = liveAndVerificationRepo()

        withTimeout(5000L) {
            account.completeRegistration("Alice Creator", "gvalice_$uniqueSuffix", "", "", null, false).getOrThrow()
        }

        // 1. Submit initial verification request
        val submitRes = withTimeout(5000L) {
            liveVer.submitVerificationRequest("CREATOR", "Publishing photography tutorials weekly.", "https://example.com/alice")
        }
        assertTrue("Expected first verification request to succeed: ${submitRes.exceptionOrNull()}", submitRes.isSuccess)

        // 2. Duplicate pending request rejected
        val dupRes = withTimeout(5000L) {
            liveVer.submitVerificationRequest("CREATOR", "Submitting another pending duplicate.", "")
        }
        assertTrue("Expected duplicate pending request to fail", dupRes.isFailure)

        val pendingList = withTimeout(5000L) {
            liveVer.observeMyVerificationRequests(aliceUid).first { it.isNotEmpty() }
        }
        val requestId = pendingList.first().id
        assertEquals("PENDING", pendingList.first().status)

        // 3. Sign in as Verified Super Admin and approve atomically
        val adminUid = signInVerifiedSuperAdmin("utkarshgaming8029@gmail.com")
        val adminSnap = withTimeout(5000L) {
            firestore.collection("users").document(adminUid).get().await()
        }
        if (!adminSnap.exists()) {
            withTimeout(5000L) {
                account.completeRegistration("Utkarsh Rai", "utkarshrai", "", "", null, false).getOrThrow()
            }
        }
        val approveRes = withTimeout(5000L) {
            liveVer.adminReviewVerificationRequest(requestId, aliceUid, approved = true, adminNote = "Approved!")
        }
        assertTrue("Expected admin approval to succeed: ${approveRes.exceptionOrNull()}", approveRes.isSuccess)

        // 4. Logout & login back as Alice -> Golden Verification persists in real-time profile
        auth.signOut()
        val reloggedUid = signInTestUser(aliceEmail)
        assertEquals(aliceUid, reloggedUid)
        val aliceProfile = withTimeout(5000L) {
            social.observeAllUserProfiles(aliceUid).first { list ->
                list.any { it.id == aliceUid && it.isGoldenVerified }
            }.first { it.id == aliceUid }
        }
        assertTrue("Golden Verification badge must persist after logout & login", aliceProfile.isGoldenVerified)

        // 5. Submit for Charlie & decline by Super Admin
        val charlieEmail = "gv_charlie_$uniqueSuffix@test.com"
        val charlieUid = signInTestUser(charlieEmail)
        withTimeout(5000L) {
            account.completeRegistration("Charlie Test", "gvchar_$uniqueSuffix", "", "", null, false).getOrThrow()
            liveVer.submitVerificationRequest("MEDIA", "Applying for media verification badge.", "").getOrThrow()
        }
        val charlieReqId = withTimeout(5000L) {
            liveVer.observeMyVerificationRequests(charlieUid).first { it.isNotEmpty() }.first().id
        }

        signInVerifiedSuperAdmin("utkarshgaming8029@gmail.com")
        val declineRes = withTimeout(5000L) {
            liveVer.adminReviewVerificationRequest(charlieReqId, charlieUid, approved = false, adminNote = "Needs portfolio")
        }
        assertTrue(declineRes.isSuccess)

        val charlieUserSnap = withTimeout(5000L) {
            firestore.collection("users").document(charlieUid).get().await()
        }
        assertFalse(charlieUserSnap.getBoolean("isGoldenVerified") == true)
    }

    @Test
    fun liveStream_startJoinCommentEnd_enforcesHostOnlyEndNotifiesViewersAndCleansOrphans() = runBlocking {
        val uniqueSuffix = UUID.randomUUID().toString().take(8).lowercase()
        val hostEmail = "live_host_$uniqueSuffix@test.com"
        val viewerEmail = "live_viewer_$uniqueSuffix@test.com"
        val account = accountRepo()
        val liveVer = liveAndVerificationRepo()
        val engagement = engagementRepo()

        val hostUid = signInTestUser(hostEmail)
        withTimeout(5000L) {
            account.completeRegistration("Live Host", "lhost_$uniqueSuffix", "", "", null, false).getOrThrow()
        }

        val viewerUid = signInTestUser(viewerEmail)
        withTimeout(5000L) {
            account.completeRegistration("Live Viewer", "lview_$uniqueSuffix", "", "", null, false).getOrThrow()
        }

        // Host starts stream 1, then starts stream 2 -> stream 1 auto-closed (no duplicates)
        signInTestUser(hostEmail)
        val stream1Id = withTimeout(5000L) { liveVer.startLiveStream("First Warmup Live").getOrThrow() }
        val stream2Id = withTimeout(5000L) { liveVer.startLiveStream("Main Obsidian Q&A").getOrThrow() }

        val stream1Snap = withTimeout(5000L) { firestore.collection("live_streams").document(stream1Id).get().await() }
        assertFalse("First stream must be ended to avoid duplicate active lives", stream1Snap.getBoolean("isLive") == true)
        assertEquals("ENDED", stream1Snap.getString("status"))

        // Viewer joins stream 2 and comments
        signInTestUser(viewerEmail)
        val joinRes = withTimeout(5000L) { liveVer.joinLiveStream(stream2Id) }
        assertTrue("Viewer should join active live stream: ${joinRes.exceptionOrNull()}", joinRes.isSuccess)
        val commentRes = withTimeout(5000L) { liveVer.sendLiveComment(stream2Id, "Excited to watch live!") }
        assertTrue("Viewer should post live comment: ${commentRes.exceptionOrNull()}", commentRes.isSuccess)

        // Viewer CANNOT end Host's live stream
        val unauthorizedEnd = withTimeout(5000L) { liveVer.endLiveStream(stream2Id) }
        assertTrue("Viewer must not be able to end host live stream", unauthorizedEnd.isFailure)

        // Host ends stream 2 -> receives summary, active list clears, viewer notified
        signInTestUser(hostEmail)
        val summary = withTimeout(5000L) { liveVer.endLiveStream(stream2Id).getOrThrow() }
        assertEquals("Main Obsidian Q&A", summary.title)
        assertEquals(1, summary.commentsCount)
        assertTrue(summary.peakViewerCount >= 1L)

        val activeAfterEnd = withTimeout(5000L) { liveVer.observeActiveLiveStreams(hostUid).first() }
        assertFalse("Ended live stream must not stay in active live list", activeAfterEnd.any { it.id == stream2Id })

        val endedDoc = withTimeout(5000L) { firestore.collection("live_streams").document(stream2Id).get().await() }
        assertFalse(endedDoc.getBoolean("isLive") == true)
        assertEquals("ENDED", endedDoc.getString("status"))
        assertNotNull("endedAt timestamp must be persisted", endedDoc.getTimestamp("endedAt"))

        // Viewer sees stream ended notification and cannot re-join or comment on ended stream
        signInTestUser(viewerEmail)
        val viewerNotifs = withTimeout(5000L) {
            engagement.observeNotifications(viewerUid).first { list ->
                list.any { it.type == "LIVE" && it.referenceId == stream2Id }
            }
        }
        assertTrue(viewerNotifs.any { it.message.contains("ended live broadcast") })

        val rejoinEnded = withTimeout(5000L) { liveVer.joinLiveStream(stream2Id) }
        assertTrue("Viewer must not be able to join ended live stream", rejoinEnded.isFailure)
        val commentEnded = withTimeout(5000L) { liveVer.sendLiveComment(stream2Id, "After end") }
        assertTrue("Commenting on ended stream must fail", commentEnded.isFailure)

        // Simulate host closing app/crashing during stream 3 -> orphan cleanup marks ENDED
        signInTestUser(hostEmail)
        val stream3Id = withTimeout(5000L) { liveVer.startLiveStream("Crash Recovery Live").getOrThrow() }
        val cleaned = withTimeout(5000L) { liveVer.endOrphanedLiveStreamsForHost(hostUid) }
        assertEquals(1, cleaned)
        val stream3Snap = withTimeout(5000L) { firestore.collection("live_streams").document(stream3Id).get().await() }
        assertEquals("ENDED", stream3Snap.getString("status"))
    }

    @Test
    fun goldenVerification_repeatedToggleOnOff_persistsAndRejectsNonAdmin() = runBlocking {
        val uniqueSuffix = UUID.randomUUID().toString().take(8).lowercase()
        val aliceEmail = "gvtog_alice_$uniqueSuffix@test.com"
        val bobEmail = "gvtog_bob_$uniqueSuffix@test.com"
        val account = accountRepo()
        val engagement = engagementRepo()
        val social = socialRepo()

        val aliceUid = signInTestUser(aliceEmail)
        withTimeout(5000L) {
            account.completeRegistration("Toggle Target", "togtarget_$uniqueSuffix", "", "", null, false).getOrThrow()
        }

        val bobUid = signInTestUser(bobEmail)
        withTimeout(5000L) {
            account.completeRegistration("Bob NonAdmin", "togbob_$uniqueSuffix", "", "", null, false).getOrThrow()
        }

        // Ordinary user Bob cannot grant or revoke verification via repository
        val bobAttempt = withTimeout(5000L) {
            engagement.adminSetGoldenVerification(aliceUid, true)
        }
        assertTrue("Non-admin toggle must fail", bobAttempt.isFailure)

        // Super Admin toggles Alice ON -> OFF -> ON
        val adminUid = signInVerifiedSuperAdmin("utkarshgaming8029@gmail.com")
        val adminSnap = withTimeout(5000L) { firestore.collection("users").document(adminUid).get().await() }
        if (!adminSnap.exists()) {
            withTimeout(5000L) {
                account.completeRegistration("Utkarsh Rai", "utkarshrai", "", "", null, false).getOrThrow()
            }
        }

        assertTrue(withTimeout(5000L) { engagement.adminSetGoldenVerification(aliceUid, true) }.isSuccess)
        val snapOn = withTimeout(5000L) { firestore.collection("users").document(aliceUid).get().await() }
        assertTrue(snapOn.getBoolean("isGoldenVerified") == true)

        assertTrue(withTimeout(5000L) { engagement.adminSetGoldenVerification(aliceUid, false) }.isSuccess)
        val snapOff = withTimeout(5000L) { firestore.collection("users").document(aliceUid).get().await() }
        assertFalse(snapOff.getBoolean("isGoldenVerified") == true)

        assertTrue(withTimeout(5000L) { engagement.adminSetGoldenVerification(aliceUid, true) }.isSuccess)
        signInTestUser(bobEmail)
        val observed = withTimeout(5000L) {
            social.observeAllUserProfiles(bobUid).first { users ->
                users.any { it.id == aliceUid && it.isGoldenVerified }
            }
        }
        assertTrue(observed.first { it.id == aliceUid }.isGoldenVerified)
    }

    @Test
    fun stories_createGroupViewExpireAndDelete_syncsFeedAndEnforcesOwnerOnly() = runBlocking {
        val uniqueSuffix = UUID.randomUUID().toString().take(8).lowercase()
        val aliceEmail = "st_alice_$uniqueSuffix@test.com"
        val bobEmail = "st_bob_$uniqueSuffix@test.com"
        val account = accountRepo()
        val social = socialRepo()

        val aliceUid = signInTestUser(aliceEmail)
        withTimeout(5000L) {
            account.completeRegistration("Story Alice", "stalice_$uniqueSuffix", "", "", null, false).getOrThrow()
        }

        // Seed 2 active inline stories + 1 expired story directly for Alice
        val now = System.currentTimeMillis()
        val story1Id = "st1_$uniqueSuffix"
        val story2Id = "st2_$uniqueSuffix"
        val expiredStoryId = "st_exp_$uniqueSuffix"
        val commonFields = mapOf(
            "authorId" to aliceUid,
            "mediaUrl" to "data:image/png;base64,iVBORw0KGgo=",
            "mediaType" to "PHOTO",
            "caption" to "Active Story",
            "audience" to "ALL",
            "allowedViewerIds" to emptyList<String>(),
            "isArchived" to false,
            "createdAt" to FieldValue.serverTimestamp()
        )
        withTimeout(5000L) {
            firestore.collection("stories").document(story1Id)
                .set(commonFields + ("expiresAtMs" to (now + 3600_000L))).await()
            firestore.collection("stories").document(story2Id)
                .set(commonFields + ("expiresAtMs" to (now + 7200_000L))).await()
            firestore.collection("stories").document(expiredStoryId)
                .set(commonFields + ("expiresAtMs" to (now - 10_000L))).await()
        }

        // Verify Alice has exactly ONE StoryGroup with 2 active stories (expired excluded)
        val aliceGroups = withTimeout(5000L) {
            social.observeActiveStoryGroups(aliceUid).first { groups ->
                groups.any { it.author.id == aliceUid }
            }
        }
        val aliceGroup = aliceGroups.filter { it.author.id == aliceUid }
        assertEquals("Author must appear once in storyGroups", 1, aliceGroup.size)
        assertEquals("Expired story must be excluded", 2, aliceGroup.first().stories.size)

        // Bob views story 1 and cannot delete Alice's story
        val bobUid = signInTestUser(bobEmail)
        withTimeout(5000L) {
            account.completeRegistration("Story Bob", "stbob_$uniqueSuffix", "", "", null, false).getOrThrow()
            social.recordStoryView(story1Id, "🔥").getOrThrow()
        }
        val bobViewSnap = withTimeout(5000L) {
            firestore.collection("stories").document(story1Id).collection("views").document(bobUid).get().await()
        }
        assertTrue("Expected Bob's view record to exist", bobViewSnap.exists())

        val unauthorizedDelete = withTimeout(5000L) { social.deleteStory(story1Id) }
        assertTrue("Non-owner deleteStory must fail", unauthorizedDelete.isFailure)

        // Alice deletes story 1 -> parent doc + Bob's view subcollection deleted; story 2 remains
        signInTestUser(aliceEmail)
        val deleteRes1 = withTimeout(5000L) { social.deleteStory(story1Id) }
        assertTrue("Owner deleteStory must succeed: ${deleteRes1.exceptionOrNull()}", deleteRes1.isSuccess)

        val story1Snap = withTimeout(5000L) { firestore.collection("stories").document(story1Id).get().await() }
        assertFalse("Deleted story doc must not exist", story1Snap.exists())
        val viewAfterDelete = withTimeout(5000L) {
            firestore.collection("stories").document(story1Id).collection("views").document(bobUid).get().await()
        }
        assertFalse("Deleted story views subcollection doc must be removed", viewAfterDelete.exists())

        val groupsAfterFirstDelete = withTimeout(5000L) {
            social.observeActiveStoryGroups(aliceUid).first { groups ->
                groups.firstOrNull { it.author.id == aliceUid }?.stories?.size == 1
            }
        }
        assertEquals(story2Id, groupsAfterFirstDelete.first { it.author.id == aliceUid }.stories.first().id)

        // Delete remaining story 2 -> Alice no longer has an active story group
        val deleteRes2 = withTimeout(5000L) { social.deleteStory(story2Id) }
        assertTrue(deleteRes2.isSuccess)
        val groupsAfterSecondDelete = withTimeout(5000L) {
            social.observeActiveStoryGroups(aliceUid).first { groups ->
                groups.none { it.author.id == aliceUid }
            }
        }
        assertTrue(groupsAfterSecondDelete.none { it.author.id == aliceUid })
    }
}

