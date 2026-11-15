package com.example.ui.auth

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.GetCredentialProviderConfigurationException
import androidx.credentials.exceptions.GetCredentialUnsupportedException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.TimeoutCancellationException

enum class PlayServicesStatus {
    AVAILABLE,
    UPDATE_REQUIRED,
    UNAVAILABLE
}

object GoogleAuthErrorMapper {

    fun isNetworkAvailable(context: Context): Boolean {
        return runCatching {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return true
            val network = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(network) ?: return false
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        }.getOrDefault(true)
    }

    fun checkPlayServices(context: Context): PlayServicesStatus {
        if (Build.FINGERPRINT.equals("robolectric", ignoreCase = true)) {
            return PlayServicesStatus.AVAILABLE
        }
        return runCatching {
            val code = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context)
            when (code) {
                ConnectionResult.SUCCESS -> PlayServicesStatus.AVAILABLE
                ConnectionResult.SERVICE_VERSION_UPDATE_REQUIRED,
                ConnectionResult.SERVICE_UPDATING -> PlayServicesStatus.UPDATE_REQUIRED
                else -> PlayServicesStatus.UNAVAILABLE
            }
        }.getOrDefault(PlayServicesStatus.AVAILABLE)
    }

    fun mapAuthExceptionToUserMessage(throwable: Throwable): String {
        return when (throwable) {
            is NoCredentialException ->
                "No Google account was found on this device. Add a Google/Gmail account in Android Settings and try again."
            is GetCredentialCancellationException ->
                "Google Sign-In was cancelled. Tap Continue with Google whenever you're ready."
            is GetCredentialUnsupportedException ->
                "Google Play Services Credential Manager is unavailable or outdated on this device. Please update Google Play Services."
            is GetCredentialProviderConfigurationException ->
                "Google Sign-In configuration mismatch. Verify your SHA-1 fingerprint and Web Client ID."
            is GetCredentialException ->
                "Unable to open Google account picker (${throwable.errorMessage ?: "please try again"})."
            is FirebaseAuthInvalidCredentialsException ->
                "Invalid or expired Google ID token. Please re-select your Google account and try again."
            is FirebaseAuthUserCollisionException ->
                "An account conflict occurred with this Google email. Sign in using your existing linked account."
            is FirebaseNetworkException ->
                "Network error while contacting Firebase Authentication. Check your internet connection and retry."
            is FirebaseAuthException ->
                "Firebase sign-in failed (${throwable.errorCode}): ${throwable.localizedMessage ?: "Please try again."}"
            is TimeoutCancellationException ->
                "Google Sign-In timed out. Please check your internet connection and try again."
            is FirebaseFirestoreException -> when (throwable.code) {
                FirebaseFirestoreException.Code.UNAVAILABLE ->
                    "Cloud Firestore is temporarily unreachable. Check your internet connection and retry."
                FirebaseFirestoreException.Code.DEADLINE_EXCEEDED ->
                    "Cloud Firestore request timed out. Please try again."
                FirebaseFirestoreException.Code.PERMISSION_DENIED ->
                    "Insufficient permissions for this account action. Please sign in again."
                else ->
                    "Database error (${throwable.code.name}): ${throwable.localizedMessage ?: "Please try again."}"
            }
            else ->
                throwable.localizedMessage?.takeIf { it.isNotBlank() }
                    ?: "Google Sign-In could not be completed. Please try again."
        }
    }
}
