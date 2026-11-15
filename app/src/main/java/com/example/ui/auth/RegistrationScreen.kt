package com.example.ui.auth

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.Cake
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Mail
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.ui.components.GoldBrush
import com.example.ui.components.ScrollDownLogo
import com.example.ui.theme.*
import com.google.firebase.auth.FirebaseUser
import java.text.DateFormat
import java.util.Date

@Composable
fun RegistrationScreen(
    firebaseUser: FirebaseUser,
    viewModel: AuthViewModel
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(firebaseUser.uid) {
        viewModel.prefillFromGoogleUser(firebaseUser)
    }

    val avatarPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        viewModel.onAvatarSelected(uri)
    }

    val effectivePhotoModel: Any? = state.avatarUri ?: state.googlePhotoUrl ?: firebaseUser.photoUrl
    val createdDateLabel = remember(firebaseUser.metadata?.creationTimestamp) {
        val ts = firebaseUser.metadata?.creationTimestamp?.takeIf { it > 0L } ?: System.currentTimeMillis()
        DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(ts))
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().widthIn(max = 520.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            ScrollDownLogo(compact = true)
            TextButton(
                onClick = { viewModel.signOut(context) },
                modifier = Modifier.testTag("registration_sign_out_button")
            ) {
                Icon(Icons.AutoMirrored.Outlined.Logout, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(6.dp))
                Text("Switch Account", color = MaterialTheme.colorScheme.primary)
            }
        }

        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(22.dp),
            modifier = Modifier
                .widthIn(max = 520.dp)
                .fillMaxWidth()
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(22.dp))
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "Complete Your SCROLL DOWN Account",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Review your verified Google identity and choose a unique @username.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Profile Photo Picker (prefilled from Google profile photo if present)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            avatarPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        }
                ) {
                    Box(
                        modifier = Modifier
                            .size(66.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .border(1.5.dp, GoldBrush, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        if (effectivePhotoModel != null) {
                            AsyncImage(
                                model = effectivePhotoModel,
                                contentDescription = "Selected profile picture",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Icon(Icons.Outlined.CameraAlt, contentDescription = "Add profile photo", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = when {
                                state.avatarUri != null -> "Custom Photo Selected ✓"
                                effectivePhotoModel != null -> "Google Profile Photo Loaded ✓"
                                else -> "Upload Profile Picture"
                            },
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Tap to choose via Android Photo Picker",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Unique User ID + Account Creation Date metadata badge
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            Icons.Outlined.Badge,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "User ID: ${firebaseUser.uid}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Account Created: $createdDateLabel",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Verified Email
                OutlinedTextField(
                    value = firebaseUser.email.orEmpty(),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Verified Google Email") },
                    leadingIcon = { Icon(Icons.Outlined.Mail, contentDescription = null) },
                    trailingIcon = {
                        Icon(Icons.Rounded.CheckCircle, contentDescription = "Verified", tint = EmeraldOnline)
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Full Name
                OutlinedTextField(
                    value = state.displayName,
                    onValueChange = viewModel::onDisplayNameChange,
                    label = { Text("Full Name") },
                    leadingIcon = { Icon(Icons.Outlined.Person, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("reg_name_input")
                )

                // Unique Username with live availability check
                OutlinedTextField(
                    value = state.username,
                    onValueChange = viewModel::onUsernameChange,
                    label = { Text("Unique Username") },
                    prefix = { Text("@", color = MaterialTheme.colorScheme.primary) },
                    trailingIcon = {
                        when (state.usernameCheckState) {
                            UsernameCheckState.CHECKING -> CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp
                            )
                            UsernameCheckState.AVAILABLE -> Icon(
                                Icons.Rounded.CheckCircle,
                                contentDescription = "Available",
                                tint = EmeraldOnline
                            )
                            UsernameCheckState.TAKEN, UsernameCheckState.INVALID -> Icon(
                                Icons.Rounded.Error,
                                contentDescription = "Unavailable",
                                tint = CrimsonHeart
                            )
                            UsernameCheckState.IDLE -> Unit
                        }
                    },
                    supportingText = {
                        val msg = when (state.usernameCheckState) {
                            UsernameCheckState.CHECKING -> "Checking availability..."
                            UsernameCheckState.AVAILABLE -> "@${state.username} is available ✓"
                            UsernameCheckState.TAKEN -> "@${state.username} is already taken — pick one below"
                            UsernameCheckState.INVALID -> "Use 3-24 lowercase letters, numbers, _ or ."
                            UsernameCheckState.IDLE -> "Choose a unique handle"
                        }
                        val col = when (state.usernameCheckState) {
                            UsernameCheckState.AVAILABLE -> EmeraldOnline
                            UsernameCheckState.TAKEN, UsernameCheckState.INVALID -> CrimsonHeart
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                        Text(msg, color = col)
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("reg_username_input")
                )

                if (state.usernameCheckState == UsernameCheckState.TAKEN || state.suggestedUsernames.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Available Username Suggestions:",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            TextButton(onClick = viewModel::suggestAlternativeUsernames) {
                                Text("Refresh", color = MaterialTheme.colorScheme.primary)
                            }
                        }
                        if (state.suggestedUsernames.isNotEmpty()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                state.suggestedUsernames.forEach { suggestion ->
                                    AssistChip(
                                        onClick = { viewModel.onUsernameChange(suggestion) },
                                        label = { Text("@$suggestion", color = GoldBright) },
                                        border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.6f))
                                    )
                                }
                            }
                        }
                    }
                }

                // Date of Birth (Optional)
                OutlinedTextField(
                    value = state.dateOfBirth,
                    onValueChange = viewModel::onDateOfBirthChange,
                    label = { Text("Date of Birth (Optional • YYYY-MM-DD)") },
                    placeholder = { Text("2000-05-14") },
                    leadingIcon = { Icon(Icons.Outlined.Cake, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("reg_dob_input")
                )

                // Bio
                OutlinedTextField(
                    value = state.bio,
                    onValueChange = viewModel::onBioChange,
                    label = { Text("Bio (optional)") },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth().testTag("reg_bio_input")
                )

                // Private Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Private Account", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                        Text(
                            "Approve followers before they view your posts",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = state.isPrivate, onCheckedChange = viewModel::onPrivateChange)
                }

                AnimatedVisibility(visible = state.errorMessage != null) {
                    Surface(
                        color = CrimsonHeart.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = state.errorMessage.orEmpty(),
                            color = CrimsonHeart,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }

                Button(
                    onClick = { viewModel.submitRegistration() },
                    enabled = !state.isLoading &&
                        state.usernameCheckState != UsernameCheckState.TAKEN &&
                        state.usernameCheckState != UsernameCheckState.INVALID,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = GoldPrimary,
                        contentColor = ObsidianBlack
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("reg_submit_button")
                ) {
                    if (state.isLoading) {
                        CircularProgressIndicator(
                            color = ObsidianBlack,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(22.dp)
                        )
                    } else {
                        Text("Enter SCROLL DOWN", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
