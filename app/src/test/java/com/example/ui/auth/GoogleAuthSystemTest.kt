package com.example.ui.auth

import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialProviderConfigurationException
import androidx.credentials.exceptions.GetCredentialUnsupportedException
import androidx.credentials.exceptions.NoCredentialException
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class GoogleAuthSystemTest {

    @Test
    fun `error mapper handles NoCredentialException with actionable device account message`() {
        val msg = GoogleAuthErrorMapper.mapAuthExceptionToUserMessage(NoCredentialException("no google account"))
        assertTrue(msg.contains("No Google account", ignoreCase = true))
        assertTrue(msg.contains("Settings", ignoreCase = true))
    }

    @Test
    fun `error mapper handles GetCredentialCancellationException gracefully`() {
        val msg = GoogleAuthErrorMapper.mapAuthExceptionToUserMessage(GetCredentialCancellationException("cancelled"))
        assertTrue(msg.contains("cancelled", ignoreCase = true))
    }

    @Test
    fun `error mapper handles GetCredentialUnsupportedException for Play Services`() {
        val msg = GoogleAuthErrorMapper.mapAuthExceptionToUserMessage(GetCredentialUnsupportedException("unsupported"))
        assertTrue(msg.contains("Play Services", ignoreCase = true))
    }

    @Test
    fun `error mapper handles ProviderConfigurationException with SHA-1 hint`() {
        val msg = GoogleAuthErrorMapper.mapAuthExceptionToUserMessage(
            GetCredentialProviderConfigurationException("misconfigured")
        )
        assertTrue(msg.contains("SHA-1", ignoreCase = true))
    }

    @Test
    fun `error mapper handles invalid Google token and duplicate account collision`() {
        val invalidMsg = GoogleAuthErrorMapper.mapAuthExceptionToUserMessage(
            FirebaseAuthInvalidCredentialsException("ERROR_INVALID_CREDENTIAL", "invalid id token")
        )
        assertTrue(invalidMsg.contains("Google ID token", ignoreCase = true))

        val collisionMsg = GoogleAuthErrorMapper.mapAuthExceptionToUserMessage(
            FirebaseAuthUserCollisionException("ERROR_ACCOUNT_EXISTS_WITH_DIFFERENT_CREDENTIAL", "collision")
        )
        assertTrue(collisionMsg.contains("conflict", ignoreCase = true))
    }

    @Test
    fun `error mapper handles network error, timeout, and Firestore UNAVAILABLE`() = runBlocking {
        val netMsg = GoogleAuthErrorMapper.mapAuthExceptionToUserMessage(FirebaseNetworkException("offline"))
        assertTrue(netMsg.contains("Network", ignoreCase = true))

        var timeoutEx: TimeoutCancellationException? = null
        try {
            withTimeout(1L) {
                delay(100L)
            }
        } catch (e: TimeoutCancellationException) {
            timeoutEx = e
        }
        val timeoutMsg = GoogleAuthErrorMapper.mapAuthExceptionToUserMessage(timeoutEx!!)
        assertTrue(timeoutMsg.contains("timed out", ignoreCase = true))

        val fsMsg = GoogleAuthErrorMapper.mapAuthExceptionToUserMessage(
            FirebaseFirestoreException("offline", FirebaseFirestoreException.Code.UNAVAILABLE)
        )
        assertTrue(fsMsg.contains("Firestore", ignoreCase = true))
    }
}
