package com.example.ui.auth

import android.app.Activity
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.R
import com.example.data.repository.FirestoreAccountRepository
import com.example.data.repository.ProfileLookupResult
import com.example.data.repository.SecurityUtils
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

sealed interface ProfileGateState {
    data object Loading : ProfileGateState
    data object NeedsRegistration : ProfileGateState
    data object Ready : ProfileGateState
    data class Error(val message: String) : ProfileGateState
}

enum class UsernameCheckState {
    IDLE, INVALID, CHECKING, AVAILABLE, TAKEN
}

data class AuthUiState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val infoMessage: String? = null,
    val displayName: String = "",
    val username: String = "",
    val dateOfBirth: String = "",
    val bio: String = "",
    val isPrivate: Boolean = false,
    val avatarUri: Uri? = null,
    val googlePhotoUrl: String? = null,
    val usernameCheckState: UsernameCheckState = UsernameCheckState.IDLE,
    val suggestedUsernames: List<String> = emptyList()
)

fun FirebaseAuth.authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
    val listener = FirebaseAuth.AuthStateListener { auth ->
        trySend(auth.currentUser)
    }
    addAuthStateListener(listener)
    awaitClose { removeAuthStateListener(listener) }
}

@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModel(
    private val auth: FirebaseAuth,
    private val accountRepository: FirestoreAccountRepository
) : ViewModel() {

    val currentUser: StateFlow<FirebaseUser?> = auth.authStateFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), auth.currentUser)

    private val profileGateRetryTrigger = MutableStateFlow(0)

    val profileGateState: StateFlow<ProfileGateState> = combine(
        currentUser,
        profileGateRetryTrigger
    ) { user, retryCount -> user to retryCount }
        .flatMapLatest { (user, _) ->
            if (user == null) {
                flowOf(ProfileGateState.Loading)
            } else {
                flow<ProfileGateState> {
                    emit(ProfileGateState.Loading)
                    accountRepository.ensureOfficialAdminBootstrap()
                    accountRepository.recordLoginActivity("GOOGLE_SIGN_IN")
                    emitAll(
                        accountRepository.observeHasProfileResult(user.uid).map { result ->
                            when (result) {
                                is ProfileLookupResult.Exists -> ProfileGateState.Ready
                                is ProfileLookupResult.Missing -> ProfileGateState.NeedsRegistration
                                is ProfileLookupResult.Error -> ProfileGateState.Error(result.message)
                            }
                        }
                    )
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), ProfileGateState.Loading)

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    private var usernameCheckJob: Job? = null
    private var autoSignInAttempted = false

    fun retryProfileGate() {
        profileGateRetryTrigger.value += 1
    }

    fun prefillFromGoogleUser(user: FirebaseUser) {
        _uiState.update { state ->
            val suggestedUsername = (user.email?.substringBefore("@") ?: user.displayName.orEmpty())
                .lowercase()
                .replace(Regex("[^a-z0-9_.]"), "")
                .take(20)
            state.copy(
                displayName = if (state.displayName.isBlank()) user.displayName.orEmpty() else state.displayName,
                username = if (state.username.isBlank()) suggestedUsername else state.username,
                googlePhotoUrl = user.photoUrl?.toString()
            )
        }
        val currentUname = _uiState.value.username
        if (currentUname.isNotBlank()) onUsernameChange(currentUname)
    }

    fun onDisplayNameChange(v: String) = _uiState.update { it.copy(displayName = v, errorMessage = null) }
    fun onDateOfBirthChange(v: String) = _uiState.update { it.copy(dateOfBirth = v, errorMessage = null) }
    fun onBioChange(v: String) = _uiState.update { it.copy(bio = v, errorMessage = null) }
    fun onPrivateChange(v: Boolean) = _uiState.update { it.copy(isPrivate = v) }
    fun onAvatarSelected(uri: Uri?) = _uiState.update { it.copy(avatarUri = uri) }
    fun clearError() = _uiState.update { it.copy(errorMessage = null, infoMessage = null) }

    fun onUsernameChange(raw: String) {
        val normalized = SecurityUtils.normalizeUsername(raw)
        _uiState.update {
            it.copy(
                username = raw.lowercase().replace(" ", ""),
                errorMessage = null,
                suggestedUsernames = emptyList(),
                usernameCheckState = when {
                    normalized.isBlank() -> UsernameCheckState.IDLE
                    !SecurityUtils.isValidUsername(normalized) -> UsernameCheckState.INVALID
                    else -> UsernameCheckState.CHECKING
                }
            )
        }
        usernameCheckJob?.cancel()
        if (!SecurityUtils.isValidUsername(normalized)) return
        usernameCheckJob = viewModelScope.launch {
            delay(350L)
            val res = accountRepository.checkUsernameAvailability(normalized)
            val available = res.getOrDefault(false)
            val suggestions = if (res.isSuccess && !available) {
                accountRepository.suggestAvailableUsernames(normalized)
            } else {
                emptyList()
            }
            _uiState.update { st ->
                if (SecurityUtils.normalizeUsername(st.username) != normalized) st
                else st.copy(
                    usernameCheckState = when {
                        res.isFailure -> UsernameCheckState.IDLE
                        available -> UsernameCheckState.AVAILABLE
                        else -> UsernameCheckState.TAKEN
                    },
                    suggestedUsernames = suggestions
                )
            }
        }
    }

    fun suggestAlternativeUsernames() {
        val base = _uiState.value.username.ifBlank { _uiState.value.displayName }
        viewModelScope.launch {
            val suggestions = accountRepository.suggestAvailableUsernames(base)
            _uiState.update { it.copy(suggestedUsernames = suggestions) }
        }
    }

    fun attemptAutoSignIn(context: Context) {
        if (autoSignInAttempted || auth.currentUser != null) return
        autoSignInAttempted = true
        if (!GoogleAuthErrorMapper.isNetworkAvailable(context)) return
        if (GoogleAuthErrorMapper.checkPlayServices(context) != PlayServicesStatus.AVAILABLE) return

        val clientId = try {
            context.getString(R.string.default_web_client_id)
        } catch (_: Exception) {
            return
        }
        val credentialManager = CredentialManager.create(context)
        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(true)
            .setServerClientId(clientId)
            .setAutoSelectEnabled(true)
            .build()
        val request = GetCredentialRequest.Builder().addCredentialOption(googleIdOption).build()

        viewModelScope.launch {
            try {
                val result = withTimeout(12_000L) {
                    credentialManager.getCredential(context, request)
                }
                val credential = result.credential
                if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    val token = GoogleIdTokenCredential.createFrom(credential.data).idToken
                    withTimeout(15_000L) {
                        auth.signInWithCredential(GoogleAuthProvider.getCredential(token, null)).await()
                    }
                }
            } catch (t: TimeoutCancellationException) {
                Log.d("Auth", "Silent auto-sign-in timed out: ${t.message}")
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Silent auto-sign-in fallback: interactive Google Sign-In buttons stay ready
            }
        }
    }

    fun onGoogleSignInClicked(context: Context) {
        if (!GoogleAuthErrorMapper.isNetworkAvailable(context)) {
            _uiState.update {
                it.copy(
                    isLoading = false,
                    infoMessage = null,
                    errorMessage = "No internet connection detected. Connect to Wi-Fi or mobile data and try again."
                )
            }
            return
        }

        when (GoogleAuthErrorMapper.checkPlayServices(context)) {
            PlayServicesStatus.UPDATE_REQUIRED -> {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        infoMessage = null,
                        errorMessage = "Google Play Services is outdated on this device. Please update Google Play Services to sign in with Google."
                    )
                }
                return
            }
            PlayServicesStatus.UNAVAILABLE -> {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        infoMessage = null,
                        errorMessage = "Google Play Services is unavailable on this device. Google Sign-In requires Google Play Services."
                    )
                }
                return
            }
            PlayServicesStatus.AVAILABLE -> Unit
        }

        val clientId = try {
            context.getString(R.string.default_web_client_id)
        } catch (_: Exception) {
            _uiState.update {
                it.copy(
                    isLoading = false,
                    errorMessage = "Google Sign-In configuration missing: default_web_client_id not found."
                )
            }
            return
        }

        val credentialManager = CredentialManager.create(context)
        val signInOption = GetSignInWithGoogleOption.Builder(serverClientId = clientId).build()
        val request = GetCredentialRequest.Builder().addCredentialOption(signInOption).build()

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null, infoMessage = null) }
            try {
                val activity = context as? Activity
                    ?: throw IllegalStateException("Google Sign-In requires an active screen context.")
                val result = withTimeout(25_000L) {
                    credentialManager.getCredential(activity, request)
                }
                val credential = result.credential
                if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
                    val authCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
                    withTimeout(20_000L) {
                        auth.signInWithCredential(authCredential).await()
                    }
                    _uiState.update { it.copy(isLoading = false, errorMessage = null, infoMessage = null) }
                } else {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = "Unexpected credential type returned from Google."
                        )
                    }
                }
            } catch (e: GetCredentialCancellationException) {
                Log.w("Auth", "Google Sign-In flow cancelled or dismissed")
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = null,
                        infoMessage = GoogleAuthErrorMapper.mapAuthExceptionToUserMessage(e)
                    )
                }
            } catch (e: TimeoutCancellationException) {
                Log.w("Auth", "Google Sign-In timed out", e)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = GoogleAuthErrorMapper.mapAuthExceptionToUserMessage(e)
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("Auth", "Google Sign-In failed", e)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = GoogleAuthErrorMapper.mapAuthExceptionToUserMessage(e)
                    )
                }
            }
        }
    }

    fun submitRegistration() {
        val state = _uiState.value
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val result = accountRepository.completeRegistration(
                displayName = state.displayName,
                rawUsername = state.username,
                dateOfBirth = state.dateOfBirth,
                bio = state.bio,
                avatarUri = state.avatarUri,
                isPrivate = state.isPrivate
            )
            _uiState.update {
                if (result.isSuccess) {
                    it.copy(isLoading = false, errorMessage = null)
                } else {
                    val ex = result.exceptionOrNull()
                    it.copy(
                        isLoading = false,
                        errorMessage = if (ex != null) {
                            GoogleAuthErrorMapper.mapAuthExceptionToUserMessage(ex)
                        } else {
                            "Profile creation failed. Please try again."
                        }
                    )
                }
            }
        }
    }

    fun signOut(context: Context) {
        val credentialManager = CredentialManager.create(context.applicationContext)
        viewModelScope.launch {
            withContext(NonCancellable) {
                try {
                    credentialManager.clearCredentialState(ClearCredentialStateRequest())
                } catch (e: Exception) {
                    Log.w("Auth", "Failed to clear credential state: ${e.message}")
                } finally {
                    _uiState.value = AuthUiState()
                    auth.signOut()
                }
            }
        }
    }
}
