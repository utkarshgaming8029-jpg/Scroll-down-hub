package com.example.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import com.example.base.FirestoreEmulatorTestBase
import com.example.data.ai.FirebaseAiRepository
import com.example.data.local.SessionDataStore
import com.example.data.media.VoiceAudioHelper
import com.example.data.repository.FirestoreAccountRepository
import com.example.data.repository.FirestoreEngagementRepository
import com.example.data.repository.FirestoreLiveAndVerificationRepository
import com.example.data.repository.FirestoreSocialRepository
import com.example.data.repository.MediaStorageHelper
import com.example.ui.admin.AdminPanelScreen
import com.example.ui.feed.HomeFeedScreen
import com.example.ui.main.MainViewModel
import com.example.ui.stories.StoryViewerScreen
import com.example.ui.theme.ProvideAppStrings
import com.example.ui.theme.ScrollDownTheme
import com.google.firebase.firestore.FieldValue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import org.robolectric.shadows.ShadowLooper
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
@Config(sdk = [36])
@LooperMode(LooperMode.Mode.PAUSED)
class ScrollDownUiFixesTest : FirestoreEmulatorTestBase() {

    @get:Rule
    val composeRule = createComposeRule()

    private fun <T> runWithMainLooper(timeoutMs: Long = 12_000L, block: suspend () -> T): T {
        val result = CompletableDeferred<Result<T>>()
        val job = CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            result.complete(runCatching { withTimeout(timeoutMs) { block() } })
        }
        val deadline = System.currentTimeMillis() + timeoutMs + 1000L
        while (!result.isCompleted && System.currentTimeMillis() < deadline) {
            ShadowLooper.idleMainLooper()
            Thread.sleep(15L)
        }
        job.cancel()
        check(result.isCompleted) { "Timed out waiting for Firebase setup while idling main looper." }
        return result.getCompleted().getOrThrow()
    }

    private fun waitForComposeAndLooper(timeoutMs: Long = 12_000L, description: String = "condition", condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            ShadowLooper.idleMainLooper()
            composeRule.waitForIdle()
            if (condition()) return
            Thread.sleep(25L)
        }
        ShadowLooper.idleMainLooper()
        composeRule.waitForIdle()
        assertTrue("Timed out after ${timeoutMs}ms waiting for $description", condition())
    }

    private inline fun withMainViewModel(uid: String, block: (MainViewModel) -> Unit) {
        val store = ViewModelStore()
        val mediaHelper = MediaStorageHelper(appContext)
        val sessionDataStore = SessionDataStore(appContext)
        val account = FirestoreAccountRepository(firestore, auth, sessionDataStore, mediaHelper)
        val social = FirestoreSocialRepository(firestore, auth, mediaHelper)
        val engagement = FirestoreEngagementRepository(firestore, auth, social, mediaHelper)
        val liveVer = FirestoreLiveAndVerificationRepository(firestore, auth, social)
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return MainViewModel(
                    currentUserId = uid,
                    accountRepository = account,
                    socialRepository = social,
                    engagementRepository = engagement,
                    liveAndVerificationRepository = liveVer,
                    aiRepository = FirebaseAiRepository(),
                    voiceAudioHelper = VoiceAudioHelper(appContext),
                    onSignOutAction = {}
                ) as T
            }
        }
        val vm = ViewModelProvider(store, factory)[MainViewModel::class.java]
        try {
            block(vm)
        } finally {
            store.clear()
            ShadowLooper.idleMainLooper()
        }
    }

    @Test
    fun homeFeedStoriesTray_currentUserStoryRendersOnce_noDuplicateRing() {
        val suffix = UUID.randomUUID().toString().take(8).lowercase()
        val mediaHelper = MediaStorageHelper(appContext)
        val account = FirestoreAccountRepository(firestore, auth, SessionDataStore(appContext), mediaHelper)

        val bobUsername = "uibob_$suffix"
        val aliceUsername = "uialice_$suffix"

        val (aliceUid, bobUid) = runWithMainLooper {
            val future = System.currentTimeMillis() + 3600_000L
            val bUid = signInTestUser("ui_bob_$suffix@test.com")
            account.completeRegistration("Bob Peer", bobUsername, "", "", null, false).getOrThrow()
            firestore.collection("stories").document("st_bob_$suffix").set(
                mapOf(
                    "authorId" to bUid,
                    "mediaUrl" to "data:image/png;base64,iVBORw0KGgo=",
                    "mediaType" to "PHOTO",
                    "caption" to "Bob Story",
                    "audience" to "ALL",
                    "allowedViewerIds" to emptyList<String>(),
                    "isArchived" to false,
                    "expiresAtMs" to future,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()

            val aUid = signInTestUser("ui_alice_$suffix@test.com")
            account.completeRegistration("Alice Self", aliceUsername, "", "", null, false).getOrThrow()
            firestore.collection("stories").document("st_alice_$suffix").set(
                mapOf(
                    "authorId" to aUid,
                    "mediaUrl" to "data:image/png;base64,iVBORw0KGgo=",
                    "mediaType" to "PHOTO",
                    "caption" to "Self Story",
                    "audience" to "ALL",
                    "allowedViewerIds" to emptyList<String>(),
                    "isArchived" to false,
                    "expiresAtMs" to future,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()
            aUid to bUid
        }

        withMainViewModel(aliceUid) { vm ->
            composeRule.setContent {
                ScrollDownTheme(isDarkTheme = true) {
                    ProvideAppStrings("en") {
                        HomeFeedScreen(
                            viewModel = vm,
                            onOpenUserProfile = {},
                            onOpenHashtagSearch = {},
                            onOpenStoryViewer = {},
                            onOpenCreateStory = {},
                            onOpenCreatePost = {}
                        )
                    }
                }
            }

            waitForComposeAndLooper(description = "home feed stories for Alice and Bob") {
                val groups = vm.storyGroups.value
                groups.any { it.author.id == aliceUid } && groups.any { it.author.id == bobUid }
            }

            composeRule.onNodeWithTag("add_story_button").assertIsDisplayed()
            composeRule.onNodeWithText("Your Story").assertIsDisplayed()
            composeRule.onNodeWithTag("story_ring_$bobUsername").assertIsDisplayed()
            assertEquals(
                "Current user must NOT appear twice in HomeFeed StoriesTray",
                0,
                composeRule.onAllNodesWithTag("story_ring_$aliceUsername").fetchSemanticsNodes().size
            )
        }
    }

    @Test
    fun storyViewer_ownerOpensOptionsMenu_canConfirmAndDeleteStory() {
        val suffix = UUID.randomUUID().toString().take(8).lowercase()
        val mediaHelper = MediaStorageHelper(appContext)
        val account = FirestoreAccountRepository(firestore, auth, SessionDataStore(appContext), mediaHelper)
        val storyId = "sv_st_$suffix"

        val ownerUid = runWithMainLooper {
            val oUid = signInTestUser("sv_owner_$suffix@test.com")
            account.completeRegistration("Story Owner", "svown_$suffix", "", "", null, false).getOrThrow()
            firestore.collection("stories").document(storyId).set(
                mapOf(
                    "authorId" to oUid,
                    "mediaUrl" to "data:image/png;base64,iVBORw0KGgo=",
                    "mediaType" to "PHOTO",
                    "caption" to "Deletable UI Story",
                    "audience" to "ALL",
                    "allowedViewerIds" to emptyList<String>(),
                    "isArchived" to false,
                    "expiresAtMs" to (System.currentTimeMillis() + 3600_000L),
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()
            oUid
        }

        withMainViewModel(ownerUid) { vm ->
            var closedCalled = false
            composeRule.setContent {
                ScrollDownTheme(isDarkTheme = true) {
                    ProvideAppStrings("en") {
                        StoryViewerScreen(
                            authorId = ownerUid,
                            viewModel = vm,
                            onClose = { closedCalled = true },
                            onOpenProfile = {}
                        )
                    }
                }
            }

            waitForComposeAndLooper(description = "story groups for owner $ownerUid") {
                vm.storyGroups.value.any { it.author.id == ownerUid }
            }

            composeRule.onNodeWithTag("story_options_menu_button").performClick()
            composeRule.waitForIdle()
            composeRule.onNodeWithTag("story_delete_option").performClick()
            composeRule.waitForIdle()
            composeRule.onNodeWithTag("confirm_delete_story_button").performClick()

            waitForComposeAndLooper(description = "story delete completion & viewer close") {
                closedCalled && vm.storyGroups.value.none { it.author.id == ownerUid }
            }

            val deletedDoc = runWithMainLooper {
                firestore.collection("stories").document(storyId).get().await()
            }
            assertFalse("Deleted story must be removed from Firestore", deletedDoc.exists())
            assertTrue("StoryViewer should close after deleting last story", closedCalled)
        }
    }

    @Test
    fun adminPanel_usersTabSearchAndVerificationSwitch_togglesAndPersists() {
        val suffix = UUID.randomUUID().toString().take(8).lowercase()
        val mediaHelper = MediaStorageHelper(appContext)
        val account = FirestoreAccountRepository(firestore, auth, SessionDataStore(appContext), mediaHelper)
        val targetUsername = "admtarget_$suffix"

        val (targetUid, adminUid) = runWithMainLooper {
            val tUid = signInTestUser("admtarget_$suffix@test.com")
            account.completeRegistration("Admin Target", targetUsername, "", "", null, false).getOrThrow()
            val aUid = signInVerifiedSuperAdmin("utkarshgaming8029@gmail.com")
            account.ensureOfficialAdminBootstrap().getOrThrow()
            val adminSnap = firestore.collection("users").document(aUid).get().await()
            if (!adminSnap.exists() || adminSnap.getString("role") != "SUPER_ADMIN") {
                account.completeRegistration("Utkarsh Rai", "utkarshrai", "", "", null, false).getOrThrow()
            }
            tUid to aUid
        }

        withMainViewModel(adminUid) { vm ->
            composeRule.setContent {
                ScrollDownTheme(isDarkTheme = true) {
                    ProvideAppStrings("en") {
                        AdminPanelScreen(viewModel = vm, onBack = {})
                    }
                }
            }

            waitForComposeAndLooper(
                timeoutMs = 15_000L,
                description = "admin and target users loaded"
            ) {
                val users = vm.allUsers.value
                users.any { it.id == adminUid && it.isSuperAdmin } && users.any { it.id == targetUid }
            }

            composeRule.onNodeWithText("Users & Badges").performScrollTo().performClick()
            composeRule.waitForIdle()
            composeRule.onNodeWithTag("admin_user_search_input").performTextInput(targetUsername)
            composeRule.waitForIdle()

            composeRule.onNodeWithTag("admin_verify_status_$targetUsername").assertTextContains("Not Verified • OFF")
            composeRule.onNodeWithTag("admin_verify_$targetUsername").performClick()

            waitForComposeAndLooper(description = "target user verified = true") {
                vm.allUsers.value.any { it.id == targetUid && it.isGoldenVerified }
            }
            composeRule.onNodeWithTag("admin_verify_status_$targetUsername").assertTextContains("Verified • ON")

            composeRule.onNodeWithTag("admin_verify_$targetUsername").performClick()
            waitForComposeAndLooper(description = "target user verified = false") {
                vm.allUsers.value.any { it.id == targetUid && !it.isGoldenVerified }
            }
            composeRule.onNodeWithTag("admin_verify_status_$targetUsername").assertTextContains("Not Verified • OFF")
        }
    }
}
