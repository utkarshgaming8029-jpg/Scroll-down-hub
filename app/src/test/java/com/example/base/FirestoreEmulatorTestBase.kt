package com.example.base

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.repository.unwrapFirestoreException
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.MemoryCacheSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
@LooperMode(LooperMode.Mode.INSTRUMENTATION_TEST)
abstract class FirestoreEmulatorTestBase {

    protected lateinit var firestore: FirebaseFirestore
    protected lateinit var auth: FirebaseAuth
    protected lateinit var databaseId: String
    protected lateinit var appContext: Context

    @Before
    open fun setUpFirebase() {
        appContext = ApplicationProvider.getApplicationContext()
        val resId = appContext.resources.getIdentifier("firestore_database_id", "string", appContext.packageName)
        databaseId = if (resId != 0) appContext.getString(resId) else "ai-studio-android-scrolldo-f46e85d5-94b1-4b23-94f0-40d827b43eaa"

        val app = if (FirebaseApp.getApps(appContext).isEmpty()) {
            val newApp = FirebaseApp.initializeApp(
                appContext,
                FirebaseOptions.Builder()
                    .setApplicationId("com.aistudio.scrolldown.xkqmzv")
                    .setProjectId(PROJECT_ID)
                    .setApiKey("fake-api-key-for-emulator")
                    .build()
            )
            val newFirestore = FirebaseFirestore.getInstance(newApp, databaseId)
            newFirestore.useEmulator(EMULATOR_HOST, FIRESTORE_PORT)
            newFirestore.firestoreSettings = FirebaseFirestoreSettings.Builder()
                .setLocalCacheSettings(MemoryCacheSettings.newBuilder().build())
                .build()

            val newAuth = FirebaseAuth.getInstance(newApp)
            newAuth.useEmulator(EMULATOR_HOST, AUTH_PORT)
            newApp
        } else {
            FirebaseApp.getInstance()
        }

        firestore = FirebaseFirestore.getInstance(app, databaseId)
        auth = FirebaseAuth.getInstance(app)
    }

    @After
    open fun tearDownFirebase() {
        auth.signOut()
        runCatching { org.robolectric.shadows.ShadowLooper.idleMainLooper() }
    }

    // Local unit test helper ONLY (under app/src/test/). Production code uses Credential Manager Google Sign-In.
    protected suspend fun signInTestUser(email: String): String = withContext(Dispatchers.IO) {
        withTimeout(AUTH_TIMEOUT_MS) {
            val result = try {
                auth.signInWithEmailAndPassword(email, DEFAULT_PASSWORD).await()
            } catch (unused: FirebaseAuthInvalidUserException) {
                auth.createUserWithEmailAndPassword(email, DEFAULT_PASSWORD).await()
            }
            checkNotNull(result.user?.uid) { "User auth failed" }
        }
    }

    protected suspend fun signInVerifiedSuperAdmin(
        email: String = "utkarshgaming8029@gmail.com"
    ): String = withContext(Dispatchers.IO) {
        withTimeout(AUTH_TIMEOUT_MS * 2) {
            val uid = signInTestUser(email)
            val user = checkNotNull(auth.currentUser)
            if (!user.isEmailVerified) {
                user.sendEmailVerification().await()
                val oobUrl = java.net.URI(
                    "http://$EMULATOR_HOST:$AUTH_PORT/emulator/v1/projects/$PROJECT_ID/oobCodes"
                ).toURL()
                val json = oobUrl.readText()
                val oobMatch = Regex("\"oobLink\"\\s*:\\s*\"([^\"]+)\"[^}]*\"email\"\\s*:\\s*\"${Regex.escape(email)}\"|" +
                    "\"email\"\\s*:\\s*\"${Regex.escape(email)}\"[^}]*\"oobLink\"\\s*:\\s*\"([^\"]+)\"")
                    .findAll(json)
                    .lastOrNull()
                val link = oobMatch?.groupValues?.drop(1)?.firstOrNull { it.isNotBlank() }
                if (link != null) {
                    val conn = java.net.URI(link).toURL().openConnection() as java.net.HttpURLConnection
                    conn.instanceFollowRedirects = false
                    conn.responseCode
                    conn.disconnect()
                }
                user.reload().await()
                user.getIdToken(true).await()
            }
            uid
        }
    }

    protected fun Throwable.unwrapFirestore(): FirebaseFirestoreException? = this.unwrapFirestoreException()

    private companion object {
        const val EMULATOR_HOST = "127.0.0.1"
        const val FIRESTORE_PORT = 8085
        const val AUTH_PORT = 9099
        const val PROJECT_ID = "demo-no-project"
        const val DEFAULT_PASSWORD = "password123"
        const val AUTH_TIMEOUT_MS = 5000L
    }
}
