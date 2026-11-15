package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.R
import com.example.data.AppContainer
import com.example.data.ai.configureAppCheck
import com.example.data.local.AppPreferences
import com.example.ui.auth.AuthScreen
import com.example.ui.auth.AuthViewModel
import com.example.ui.auth.ProfileGateState
import com.example.ui.auth.RegistrationScreen
import com.example.ui.components.GoldBrush
import com.example.ui.components.GoldenPulseLoader
import com.example.ui.main.MainViewModel
import com.example.ui.navigation.ScrollDownApp
import com.example.ui.theme.*
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureAppCheck(this, intent)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )

        val container = AppContainer(applicationContext)

        setContent {
            val appContainer = remember { container }
            val prefs by appContainer.sessionDataStore.preferences.collectAsStateWithLifecycle(
                initialValue = AppPreferences()
            )
            var showSplash by remember { mutableStateOf(true) }

            LaunchedEffect(Unit) {
                delay(1150L)
                showSplash = false
            }

            ScrollDownTheme(isDarkTheme = prefs.isDarkTheme) {
                ProvideAppStrings(languageCode = prefs.languageCode) {
                    val authViewModel: AuthViewModel = viewModel(
                        factory = viewModelFactory {
                            initializer {
                                AuthViewModel(
                                    auth = appContainer.firebaseAuth,
                                    accountRepository = appContainer.accountRepository
                                )
                            }
                        }
                    )

                    val firebaseUser by authViewModel.currentUser.collectAsStateWithLifecycle()
                    val gateState by authViewModel.profileGateState.collectAsStateWithLifecycle()

                    Box(modifier = Modifier.fillMaxSize()) {
                        if (firebaseUser == null) {
                            AuthScreen(viewModel = authViewModel)
                        } else {
                            val user = firebaseUser!!
                            key(user.uid) {
                                when (gateState) {
                                    ProfileGateState.Loading -> {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(MaterialTheme.colorScheme.background),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            GoldenPulseLoader()
                                        }
                                    }
                                    ProfileGateState.NeedsRegistration -> {
                                        RegistrationScreen(
                                            firebaseUser = user,
                                            viewModel = authViewModel
                                        )
                                    }
                                    is ProfileGateState.Error -> {
                                        val ctx = LocalContext.current
                                        ProfileErrorGateScreen(
                                            message = (gateState as ProfileGateState.Error).message,
                                            onRetry = { authViewModel.retryProfileGate() },
                                            onSignOut = { authViewModel.signOut(ctx) }
                                        )
                                    }
                                    ProfileGateState.Ready -> {
                                        val mainViewModel: MainViewModel = viewModel(
                                            key = user.uid,
                                            factory = viewModelFactory {
                                                initializer {
                                                    MainViewModel(
                                                        currentUserId = user.uid,
                                                        accountRepository = appContainer.accountRepository,
                                                        socialRepository = appContainer.socialRepository,
                                                        engagementRepository = appContainer.engagementRepository,
                                                        liveAndVerificationRepository = appContainer.liveAndVerificationRepository,
                                                        aiRepository = appContainer.aiRepository,
                                                        voiceAudioHelper = appContainer.voiceAudioHelper,
                                                        onSignOutAction = { ctx -> authViewModel.signOut(ctx) }
                                                    )
                                                }
                                            }
                                        )
                                        val currentProfile by mainViewModel.currentUser.collectAsStateWithLifecycle()
                                        if (currentProfile?.isBanned == true) {
                                            BannedAccountScreen(
                                                username = currentProfile?.username.orEmpty(),
                                                onSignOut = { mainViewModel.logout(it) }
                                            )
                                        } else {
                                            ScrollDownApp(viewModel = mainViewModel)
                                        }
                                    }
                                }
                            }
                        }

                        AnimatedVisibility(
                            visible = showSplash,
                            exit = fadeOut(animationSpec = tween(400))
                        ) {
                            BrandedSplashOverlay()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BrandedSplashOverlay() {
    var started by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { started = true }
    val scale by animateFloatAsState(
        targetValue = if (started) 1f else 0.84f,
        animationSpec = tween(durationMillis = 650, easing = FastOutSlowInEasing),
        label = "splash_scale"
    )
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBlack),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.scale(scale)
        ) {
            Box(
                modifier = Modifier
                    .size(104.dp)
                    .clip(RoundedCornerShape(26.dp))
                    .border(2.dp, GoldBrush, RoundedCornerShape(26.dp))
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_logo_crown_s_play),
                    contentDescription = "SCROLL DOWN emblem",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
            Text(
                text = "SCROLL DOWN",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontFamily = SyneFontFamily,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 3.sp,
                    brush = GoldBrush
                )
            )
            GoldenPulseLoader()
        }
    }
}

@Composable
private fun BannedAccountScreen(
    username: String,
    onSignOut: (android.content.Context) -> Unit
) {
    val context = LocalContext.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBlack)
            .padding(28.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Icon(Icons.Rounded.Block, contentDescription = null, tint = CrimsonHeart, modifier = Modifier.size(56.dp))
            Text("Account Suspended", style = MaterialTheme.typography.headlineSmall, color = CrimsonHeart, fontWeight = FontWeight.Bold)
            Text(
                text = "@$username has been suspended by SCROLL DOWN moderation for violating community guidelines.",
                style = MaterialTheme.typography.bodyMedium,
                color = WarmGrayText,
                textAlign = TextAlign.Center
            )
            Text(
                text = "If you believe this is an error, contact support via your registered email.",
                style = MaterialTheme.typography.bodySmall,
                color = DimGrayText,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = { onSignOut(context) },
                border = BorderStroke(1.dp, GoldPrimary),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text("Sign Out", color = GoldBright, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun ProfileErrorGateScreen(
    message: String,
    onRetry: () -> Unit,
    onSignOut: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBlack)
            .padding(28.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.widthIn(max = 420.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Icon(
                Icons.Rounded.CloudOff,
                contentDescription = null,
                tint = GoldBright,
                modifier = Modifier.size(54.dp)
            )
            Text(
                text = "Could Not Load Account",
                style = MaterialTheme.typography.headlineSmall,
                color = IvoryText,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = WarmGrayText,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(6.dp))
            Button(
                onClick = onRetry,
                colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary, contentColor = ObsidianBlack),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Text("Retry", fontWeight = FontWeight.Bold)
            }
            OutlinedButton(
                onClick = onSignOut,
                border = BorderStroke(1.dp, GoldPrimary),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Text("Switch / Sign Out", color = GoldBright, fontWeight = FontWeight.Bold)
            }
        }
    }
}
