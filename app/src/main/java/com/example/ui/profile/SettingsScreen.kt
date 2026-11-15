package com.example.ui.profile

import android.content.Intent
import androidx.core.net.toUri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.UserProfile
import com.example.ui.components.GoldenVerifiedBadge
import com.example.ui.components.UserAvatar
import com.example.ui.main.MainViewModel
import com.example.ui.theme.*
import java.text.DateFormat
import java.util.Date

private enum class SettingsModal {
    EDIT_PROFILE,
    ACCOUNT_INFO,
    PRIVACY_OVERVIEW,
    SECURITY_PIN,
    TWO_FACTOR,
    ACCOUNT_RECOVERY,
    NOTIFICATIONS,
    LANGUAGE,
    BLOCKED_ACCOUNTS,
    LOGIN_ACTIVITY,
    DOWNLOAD_INFO,
    DELETE_ACCOUNT,
    HELP_CENTER,
    CONTACT_SUPPORT,
    ABOUT,
    TERMS,
    PRIVACY_POLICY
}

@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    initialSection: String,
    onBack: () -> Unit
) {
    BackHandler { onBack() }
    val context = LocalContext.current
    val strings = LocalAppStrings.current
    val profile by viewModel.currentUser.collectAsStateWithLifecycle()
    val prefs by viewModel.appPreferences.collectAsStateWithLifecycle()
    val allUsers by viewModel.allUsers.collectAsStateWithLifecycle()
    val loginActivity by viewModel.loginActivity.collectAsStateWithLifecycle()
    val supportTickets by viewModel.supportTickets.collectAsStateWithLifecycle()

    var activeModal by remember(initialSection) {
        mutableStateOf(
            when (initialSection) {
                "HELP" -> SettingsModal.HELP_CENTER
                "ABOUT" -> SettingsModal.ABOUT
                else -> null
            }
        )
    }
    var exportedJson by remember { mutableStateOf<String?>(null) }

    val me = profile
    if (me == null) {
        Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        TopBackHeader(title = strings.settingsTitle, onBack = onBack)

        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item { SettingsSectionTitle("Account") }
            item {
                SettingsRow(Icons.Outlined.Edit, "1. Edit Profile", "Name, bio, avatar, and privacy") {
                    activeModal = SettingsModal.EDIT_PROFILE
                }
            }
            item {
                SettingsRow(Icons.Outlined.Badge, "2. Account Information", "ID, email, DOB, role, verification status") {
                    activeModal = SettingsModal.ACCOUNT_INFO
                }
            }
            item {
                SettingsRow(Icons.Outlined.PrivacyTip, "3. Privacy Settings", "Interaction, comments & block controls") {
                    activeModal = SettingsModal.PRIVACY_OVERVIEW
                }
            }
            item {
                SettingsSwitchRow(
                    icon = Icons.Outlined.Lock,
                    title = "4. Private Account",
                    subtitle = if (me.isPrivate) "Followers require your approval" else "Public profile visible to members",
                    checked = me.isPrivate,
                    onCheckedChange = { viewModel.setPrivateAccount(it) },
                    testTag = "settings_private_toggle"
                )
            }

            item { SettingsSectionTitle("Security & Credentials") }
            item {
                SettingsRow(Icons.Outlined.Key, "5. Security Passcode & Password", "Manage Google credential lock & app PIN") {
                    activeModal = SettingsModal.SECURITY_PIN
                }
            }
            item {
                SettingsRow(
                    Icons.Outlined.VerifiedUser,
                    "6. Two-Factor Authentication",
                    if (me.twoFactorEnabled) "Enabled (${me.backupCodes.size} backup codes)" else "Tap to enable 2FA backup codes"
                ) {
                    activeModal = SettingsModal.TWO_FACTOR
                }
            }
            item {
                SettingsRow(
                    Icons.Outlined.LockReset,
                    "Account Recovery & Google Access",
                    "Recover your Google account or verify 2FA codes"
                ) {
                    activeModal = SettingsModal.ACCOUNT_RECOVERY
                }
            }
            item {
                SettingsRow(Icons.Outlined.Devices, "11. Login Activity", "${loginActivity.size} recorded sessions") {
                    activeModal = SettingsModal.LOGIN_ACTIVITY
                }
            }

            item { SettingsSectionTitle("Preferences") }
            item {
                SettingsRow(Icons.Outlined.Notifications, "7. Notification Settings", "Configure likes, comments, DMs & admin alerts") {
                    activeModal = SettingsModal.NOTIFICATIONS
                }
            }
            item {
                SettingsSwitchRow(
                    icon = Icons.Outlined.DarkMode,
                    title = "8. Dark Theme",
                    subtitle = if (prefs.isDarkTheme) "Obsidian Black & Gold" else "Ivory & Royal Gold Light",
                    checked = prefs.isDarkTheme,
                    onCheckedChange = { viewModel.setDarkTheme(it) },
                    testTag = "settings_theme_toggle"
                )
            }
            item {
                SettingsRow(Icons.Outlined.Language, "9. Language Selection", "Current: ${strings.languageLabel}") {
                    activeModal = SettingsModal.LANGUAGE
                }
            }

            item { SettingsSectionTitle("Safety & Data") }
            item {
                val blockedCount = allUsers.count { it.isBlockedByMe }
                SettingsRow(Icons.Outlined.Block, "10. Blocked Accounts", "$blockedCount blocked users") {
                    activeModal = SettingsModal.BLOCKED_ACCOUNTS
                }
            }
            item {
                SettingsRow(Icons.Outlined.Download, "12. Download Your Information", "Export your Cloud Firestore data as JSON") {
                    activeModal = SettingsModal.DOWNLOAD_INFO
                    viewModel.exportUserDataJson { json -> exportedJson = json }
                }
            }
            item {
                SettingsRow(
                    icon = Icons.Outlined.DeleteForever,
                    title = "13. Delete Account",
                    subtitle = "Permanently delete your account & profile data",
                    tint = CrimsonHeart
                ) {
                    activeModal = SettingsModal.DELETE_ACCOUNT
                }
            }

            item { SettingsSectionTitle("Support & Legal") }
            item {
                SettingsRow(Icons.AutoMirrored.Outlined.HelpOutline, "14. Help Center", "FAQs on verification, Reels & privacy") {
                    activeModal = SettingsModal.HELP_CENTER
                }
            }
            item {
                SettingsRow(Icons.Outlined.SupportAgent, "15. Contact Support", "Submit & track support tickets (${supportTickets.size})") {
                    activeModal = SettingsModal.CONTACT_SUPPORT
                }
            }
            item {
                SettingsRow(Icons.Outlined.Info, "16. About SCROLL DOWN", "Version 1.0 • Official Super Admin @utkarshrai") {
                    activeModal = SettingsModal.ABOUT
                }
            }
            item {
                SettingsRow(Icons.Outlined.Description, "17. Terms and Conditions", "Community rules & platform agreement") {
                    activeModal = SettingsModal.TERMS
                }
            }
            item {
                SettingsRow(Icons.Outlined.Policy, "18. Privacy Policy", "Data isolation & security commitments") {
                    activeModal = SettingsModal.PRIVACY_POLICY
                }
            }

            item {
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedButton(
                    onClick = { viewModel.logout(context) },
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().height(50.dp).testTag("settings_logout_button")
                ) {
                    Icon(Icons.AutoMirrored.Outlined.Logout, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(strings.logout, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    SettingsModalHost(
        modal = activeModal,
        profile = me,
        viewModel = viewModel,
        exportedJson = exportedJson,
        onDismiss = { activeModal = null },
        onSelectModal = { activeModal = it },
        onOpenSupport = { activeModal = SettingsModal.CONTACT_SUPPORT }
    )
}

@Composable
private fun SettingsModalHost(
    modal: SettingsModal?,
    profile: UserProfile,
    viewModel: MainViewModel,
    exportedJson: String?,
    onDismiss: () -> Unit,
    onSelectModal: (SettingsModal) -> Unit,
    onOpenSupport: () -> Unit
) {
    if (modal == null) return
    val context = LocalContext.current
    val prefs by viewModel.appPreferences.collectAsStateWithLifecycle()
    val allUsers by viewModel.allUsers.collectAsStateWithLifecycle()
    val loginActivity by viewModel.loginActivity.collectAsStateWithLifecycle()
    val supportTickets by viewModel.supportTickets.collectAsStateWithLifecycle()

    when (modal) {
        SettingsModal.EDIT_PROFILE -> {
            EditProfileDialog(
                profile = profile,
                onDismiss = onDismiss,
                onSave = { name, bio, isPriv, avatar ->
                    viewModel.updateProfile(name, bio, isPriv, avatar) { onDismiss() }
                }
            )
        }
        SettingsModal.ACCOUNT_INFO -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                containerColor = MaterialTheme.colorScheme.surface,
                title = { Text("Account Information", color = MaterialTheme.colorScheme.primary) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        InfoLine("Unique User ID", profile.id)
                        InfoLine("Full Name", profile.displayName)
                        InfoLine("Username", "@${profile.username}")
                        InfoLine("Verified Email", profile.email.ifBlank { "Google Verified" })
                        InfoLine("Date of Birth", profile.dateOfBirth.ifBlank { "Not specified" })
                        InfoLine("Account Role", profile.role)
                        InfoLine("Golden Badge", if (profile.isGoldenVerified) "Active ✓" else "Standard")
                        InfoLine("Created", DateFormat.getDateInstance().format(Date(profile.createdAt.takeIf { it > 0 } ?: System.currentTimeMillis())))
                    }
                },
                confirmButton = { TextButton(onClick = onDismiss) { Text("Close", color = MaterialTheme.colorScheme.primary) } }
            )
        }
        SettingsModal.PRIVACY_OVERVIEW -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                containerColor = MaterialTheme.colorScheme.surface,
                title = { Text("Privacy Settings", color = MaterialTheme.colorScheme.primary) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            "• Private Account: ${if (profile.isPrivate) "Enabled" else "Off"}\n" +
                                "• Personal PII (email, DOB, 2FA backup codes) is isolated in private Cloud Firestore rules.\n" +
                                "• Saved posts & archive items are visible only to you.",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Private Account", style = MaterialTheme.typography.titleSmall)
                            Switch(checked = profile.isPrivate, onCheckedChange = { viewModel.setPrivateAccount(it) })
                        }
                    }
                },
                confirmButton = { TextButton(onClick = onDismiss) { Text("Done", color = MaterialTheme.colorScheme.primary) } }
            )
        }
        SettingsModal.SECURITY_PIN -> {
            var pin by remember { mutableStateOf("") }
            var checkCode by remember { mutableStateOf("") }
            var checkMsg by remember { mutableStateOf<String?>(null) }
            AlertDialog(
                onDismissRequest = onDismiss,
                containerColor = MaterialTheme.colorScheme.surface,
                title = { Text("Security Passcode & Password", color = MaterialTheme.colorScheme.primary) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            "Your primary sign-in is protected by Google Credential Manager. Set or verify your hashed SCROLL DOWN security passcode below:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedTextField(
                            value = pin,
                            onValueChange = { pin = it },
                            label = { Text("New Security Passcode (min 4 chars)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (profile.hasSecurityPin) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            OutlinedTextField(
                                value = checkCode,
                                onValueChange = { checkCode = it },
                                label = { Text("Test / Verify Existing Passcode") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            TextButton(onClick = {
                                viewModel.verifySecurityChallenge(checkCode) { res ->
                                    checkMsg = res.getOrElse { it.message ?: "Verification failed" }
                                }
                            }) {
                                Text("Verify Passcode Now", color = GoldBright)
                            }
                            checkMsg?.let { msg ->
                                Text(msg, style = MaterialTheme.typography.labelSmall, color = GoldBright)
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = { viewModel.updateSecurityPin(pin) { onDismiss() } },
                        colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary, contentColor = ObsidianBlack)
                    ) { Text("Save Passcode", fontWeight = FontWeight.Bold) }
                },
                dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
            )
        }
        SettingsModal.TWO_FACTOR -> {
            var recoveryTest by remember { mutableStateOf("") }
            var recoveryStatus by remember { mutableStateOf<String?>(null) }
            AlertDialog(
                onDismissRequest = onDismiss,
                containerColor = MaterialTheme.colorScheme.surface,
                title = { Text("Two-Factor Authentication", color = MaterialTheme.colorScheme.primary) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Enable 2FA Protection", style = MaterialTheme.typography.titleSmall)
                            Switch(
                                checked = profile.twoFactorEnabled,
                                onCheckedChange = { viewModel.toggleTwoFactor(it) }
                            )
                        }
                        if (profile.twoFactorEnabled && profile.backupCodes.isNotEmpty()) {
                            Text("Backup Recovery Codes:", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = profile.backupCodes.joinToString("\n"),
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                            OutlinedTextField(
                                value = recoveryTest,
                                onValueChange = { recoveryTest = it },
                                label = { Text("Verify Backup Code") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            TextButton(onClick = {
                                viewModel.verifySecurityChallenge(recoveryTest) { res ->
                                    recoveryStatus = res.getOrElse { it.message ?: "Code invalid" }
                                }
                            }) {
                                Text("Test Backup Code", color = GoldBright)
                            }
                            recoveryStatus?.let { status ->
                                Text(status, style = MaterialTheme.typography.labelSmall, color = GoldBright)
                            }
                        }
                    }
                },
                confirmButton = { TextButton(onClick = onDismiss) { Text("Done", color = MaterialTheme.colorScheme.primary) } }
            )
        }
        SettingsModal.ACCOUNT_RECOVERY -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                containerColor = MaterialTheme.colorScheme.surface,
                title = { Text("Account Recovery", color = MaterialTheme.colorScheme.primary) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            "Your SCROLL DOWN identity is secured via Google Account (${profile.email.ifBlank { "Verified Google Sign-In" }}).",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            "• Use Google Account Recovery if you forgot your Google password or changed devices.\n• Use your SCROLL DOWN Security Passcode or 2FA backup codes for in-app verification.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        TextButton(onClick = { onSelectModal(SettingsModal.TWO_FACTOR) }) {
                            Text("Verify 2FA Backup Code →", color = GoldBright)
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            onDismiss()
                            runCatching {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, "https://accounts.google.com/signin/recovery".toUri())
                                )
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary, contentColor = ObsidianBlack)
                    ) { Text("Google Recovery", fontWeight = FontWeight.Bold) }
                },
                dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } }
            )
        }
        SettingsModal.NOTIFICATIONS -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                containerColor = MaterialTheme.colorScheme.surface,
                title = { Text("Notification Settings", color = MaterialTheme.colorScheme.primary) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        NotifToggleRow("Likes", prefs.notifyLikes) { viewModel.setNotificationSettings(likes = it) }
                        NotifToggleRow("Comments & Replies", prefs.notifyComments) { viewModel.setNotificationSettings(comments = it) }
                        NotifToggleRow("Follows & Requests", prefs.notifyFollows) { viewModel.setNotificationSettings(follows = it) }
                        NotifToggleRow("Direct Messages", prefs.notifyMessages) { viewModel.setNotificationSettings(messages = it) }
                        NotifToggleRow("Admin Announcements", prefs.notifyAdmin) { viewModel.setNotificationSettings(admin = it) }
                    }
                },
                confirmButton = { TextButton(onClick = onDismiss) { Text("Done", color = MaterialTheme.colorScheme.primary) } }
            )
        }
        SettingsModal.LANGUAGE -> {
            val languages = listOf("en" to "English", "hi" to "हिन्दी (Hindi)", "es" to "Español (Spanish)")
            AlertDialog(
                onDismissRequest = onDismiss,
                containerColor = MaterialTheme.colorScheme.surface,
                title = { Text("Select Language", color = MaterialTheme.colorScheme.primary) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        languages.forEach { (code, label) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable {
                                        viewModel.setLanguageCode(code)
                                        onDismiss()
                                    }
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(selected = prefs.languageCode == code, onClick = {
                                    viewModel.setLanguageCode(code)
                                    onDismiss()
                                })
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(label, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                },
                confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
            )
        }
        SettingsModal.BLOCKED_ACCOUNTS -> {
            val blocked = allUsers.filter { it.isBlockedByMe }
            AlertDialog(
                onDismissRequest = onDismiss,
                containerColor = MaterialTheme.colorScheme.surface,
                title = { Text("Blocked Accounts", color = MaterialTheme.colorScheme.primary) },
                text = {
                    if (blocked.isEmpty()) {
                        Text("You haven't blocked any accounts.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            blocked.forEach { u ->
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    UserAvatar(avatarUri = u.avatarUri, displayName = u.displayName, size = 36.dp)
                                    Text("@${u.username}", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                                    TextButton(onClick = { viewModel.toggleBlock(u.id) }) {
                                        Text("Unblock", color = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
            )
        }
        SettingsModal.LOGIN_ACTIVITY -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                containerColor = MaterialTheme.colorScheme.surface,
                title = { Text("Login Activity", color = MaterialTheme.colorScheme.primary) },
                text = {
                    if (loginActivity.isEmpty()) {
                        Text("No device sign-ins recorded yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        LazyColumn(modifier = Modifier.heightIn(max = 280.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(loginActivity, key = { it.id }) { act ->
                                Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(10.dp)) {
                                    Column(modifier = Modifier.fillMaxWidth().padding(10.dp)) {
                                        Text(act.deviceModel, style = MaterialTheme.typography.titleSmall)
                                        Text(
                                            "${act.authMethod} • ${DateFormat.getDateTimeInstance().format(Date(act.createdAt))}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
            )
        }
        SettingsModal.DOWNLOAD_INFO -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                containerColor = MaterialTheme.colorScheme.surface,
                title = { Text("Download Your Information", color = MaterialTheme.colorScheme.primary) },
                text = {
                    if (exportedJson == null) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            Text("Packaging your Cloud Firestore archive...")
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Your data export is ready (${exportedJson.length} chars JSON):", style = MaterialTheme.typography.bodySmall)
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth().heightIn(max = 180.dp)
                            ) {
                                Text(exportedJson.take(700) + "...", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(10.dp))
                            }
                        }
                    }
                },
                confirmButton = {
                    if (exportedJson != null) {
                        Button(
                            onClick = {
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = "application/json"
                                    putExtra(Intent.EXTRA_SUBJECT, "SCROLL DOWN Account Export")
                                    putExtra(Intent.EXTRA_TEXT, exportedJson)
                                }
                                context.startActivity(Intent.createChooser(intent, "Export Account JSON"))
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary, contentColor = ObsidianBlack)
                        ) { Text("Export / Share JSON", fontWeight = FontWeight.Bold) }
                    }
                },
                dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } }
            )
        }
        SettingsModal.DELETE_ACCOUNT -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                containerColor = MaterialTheme.colorScheme.surface,
                title = { Text("Delete Account", color = CrimsonHeart) },
                text = {
                    Text(
                        if (profile.isSuperAdmin) {
                            "Official Super Admin (@utkarshrai) cannot be permanently deleted while active."
                        } else {
                            "This permanently removes your profile, posts, saved items, login activity, and private information from Cloud Firestore."
                        },
                        style = MaterialTheme.typography.bodySmall
                    )
                },
                confirmButton = {
                    if (!profile.isSuperAdmin) {
                        Button(
                            onClick = {
                                onDismiss()
                                viewModel.deleteAccount(context)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = CrimsonHeart, contentColor = IvoryText)
                        ) { Text("Confirm Delete") }
                    }
                },
                dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
            )
        }
        SettingsModal.HELP_CENTER -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                containerColor = MaterialTheme.colorScheme.surface,
                title = { Text("Help Center", color = MaterialTheme.colorScheme.primary) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        FaqItem("How do I get the Golden Verification Badge?", "Golden Verification badges are managed exclusively by authorized Super Admins (@utkarshrai).")
                        FaqItem("How long do Stories remain active?", "Stories stay live for 24 hours and can be saved permanently to Story Highlights or restored in Archive.")
                        FaqItem("How does Private Account work?", "When enabled, new followers remain in Pending Requests until you approve them in Notifications.")
                        TextButton(onClick = onOpenSupport) {
                            Text("Still need help? Contact Support →", color = MaterialTheme.colorScheme.primary)
                        }
                    }
                },
                confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
            )
        }
        SettingsModal.CONTACT_SUPPORT -> {
            var subject by remember { mutableStateOf("") }
            var body by remember { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = onDismiss,
                containerColor = MaterialTheme.colorScheme.surface,
                title = { Text("Contact Support", color = MaterialTheme.colorScheme.primary) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = subject,
                            onValueChange = { subject = it },
                            label = { Text("Subject") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = body,
                            onValueChange = { body = it },
                            label = { Text("Describe your issue") },
                            minLines = 3,
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (supportTickets.isNotEmpty()) {
                            Text("Your Recent Tickets (${supportTickets.size}):", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                            supportTickets.take(3).forEach { t ->
                                Text("• ${t.subject} [${t.status}]", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = { viewModel.submitSupportTicket(subject, body) { onDismiss() } },
                        colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary, contentColor = ObsidianBlack)
                    ) { Text("Submit Ticket", fontWeight = FontWeight.Bold) }
                },
                dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } }
            )
        }
        SettingsModal.ABOUT -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                containerColor = MaterialTheme.colorScheme.surface,
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("About SCROLL DOWN", color = MaterialTheme.colorScheme.primary)
                        GoldenVerifiedBadge(size = 16.dp)
                    }
                },
                text = {
                    Text(
                        "SCROLL DOWN v1.0\n\nA luxury social photography, Stories, Reels, and direct messaging platform crafted in dark obsidian & 24K gold.\n\nOfficial Super Admin: Utkarsh Rai (@utkarshrai)",
                        style = MaterialTheme.typography.bodySmall
                    )
                },
                confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
            )
        }
        SettingsModal.TERMS, SettingsModal.PRIVACY_POLICY -> {
            val isTerms = modal == SettingsModal.TERMS
            AlertDialog(
                onDismissRequest = onDismiss,
                containerColor = MaterialTheme.colorScheme.surface,
                title = { Text(if (isTerms) "Terms & Conditions" else "Privacy Policy", color = MaterialTheme.colorScheme.primary) },
                text = {
                    Text(
                        if (isTerms) {
                            "1. Account Integrity: Use an authentic username and respect intellectual property.\n2. Community Safety: Harassment, impersonation, and abuse may lead to content removal or account suspension.\n3. Verification: Only authorized Super Admins may grant or revoke Golden Verification badges."
                        } else {
                            "1. Zero-Trust Data Isolation: Private email, date of birth, 2FA backup codes, and login sessions are isolated under /users/{uid}/private.\n2. Control: You can toggle Public/Private visibility, export a JSON copy of your data, or delete your account anytime."
                        },
                        style = MaterialTheme.typography.bodySmall
                    )
                },
                confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
            )
        }
    }
}

@Composable
private fun SettingsSectionTitle(title: String) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
    )
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().border(0.8.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(icon, contentDescription = null, tint = if (tint == CrimsonHeart) CrimsonHeart else MaterialTheme.colorScheme.primary)
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = tint)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SettingsSwitchRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().border(0.8.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = checked, onCheckedChange = onCheckedChange, modifier = Modifier.testTag(testTag))
        }
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun NotifToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun FaqItem(q: String, a: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(q, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        Text(a, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
