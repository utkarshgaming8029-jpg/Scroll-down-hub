package com.example.ui.admin

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.LiveTv
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.ReportStatus
import com.example.data.local.VerificationStatus
import com.example.data.repository.SecurityUtils
import com.example.ui.components.EmptyStateView
import com.example.ui.components.GoldenVerifiedBadge
import com.example.ui.components.UserAvatar
import com.example.ui.main.MainViewModel
import com.example.ui.theme.*

enum class AdminTab(val label: String) {
    ANALYTICS("Analytics"),
    USERS("Users & Badges"),
    VERIFICATION("Verification"),
    LIVE("Live Streams"),
    REPORTS("Reports"),
    ANNOUNCE("Announce")
}

@Composable
fun AdminPanelScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler { onBack() }

    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
    val analytics by viewModel.adminAnalytics.collectAsStateWithLifecycle()
    val allUsers by viewModel.allUsers.collectAsStateWithLifecycle()
    val reports by viewModel.adminReports.collectAsStateWithLifecycle()
    val verRequests by viewModel.allVerificationRequests.collectAsStateWithLifecycle()
    val liveStreams by viewModel.activeLiveStreams.collectAsStateWithLifecycle()
    val allPosts by viewModel.allPosts.collectAsStateWithLifecycle()
    val verifyingUserIds by viewModel.verifyingUserIds.collectAsStateWithLifecycle()

    if (currentUser?.isSuperAdmin != true) {
        Column(modifier = modifier.fillMaxSize().background(ObsidianBlack)) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = IvoryText)
            }
            EmptyStateView(
                icon = Icons.Rounded.Lock,
                title = "Restricted Access",
                subtitle = "Only authorized Super Administrators can access the SCROLL DOWN Admin Dashboard."
            )
        }
        return
    }

    var selectedTab by rememberSaveable { mutableStateOf(AdminTab.ANALYTICS) }
    var userSearchQuery by rememberSaveable { mutableStateOf("") }
    var announcementText by rememberSaveable { mutableStateOf("") }
    val usersListState = rememberLazyListState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianBlack)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = IvoryText)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text("SCROLL DOWN Admin", style = MaterialTheme.typography.headlineSmall, color = GoldBright)
                Text("Authorized Super Admin: @${currentUser?.username}", style = MaterialTheme.typography.labelSmall, color = WarmGrayText)
            }
        }

        ScrollableTabRow(
            selectedTabIndex = selectedTab.ordinal,
            containerColor = ObsidianBlack,
            contentColor = GoldBright,
            edgePadding = 12.dp
        ) {
            AdminTab.entries.forEach { tab ->
                Tab(
                    selected = selectedTab == tab,
                    onClick = { selectedTab = tab },
                    text = {
                        Text(
                            tab.label,
                            color = if (selectedTab == tab) GoldBright else WarmGrayText,
                            fontWeight = if (selectedTab == tab) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                )
            }
        }

        when (selectedTab) {
            AdminTab.ANALYTICS -> {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            AnalyticsCard("Registered Users", analytics.totalUsers.toString(), Modifier.weight(1f))
                            AnalyticsCard("Total Posts", analytics.totalPosts.toString(), Modifier.weight(1f))
                        }
                    }
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            AnalyticsCard("Total Reels", analytics.totalReels.toString(), Modifier.weight(1f))
                            AnalyticsCard("Total Comments", analytics.totalComments.toString(), Modifier.weight(1f))
                        }
                    }
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            AnalyticsCard("Golden Verified", analytics.verifiedUsers.toString(), Modifier.weight(1f))
                            AnalyticsCard("Open Reports", analytics.openReports.toString(), Modifier.weight(1f))
                        }
                    }
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            AnalyticsCard("Pending Badges", analytics.pendingVerifications.toString(), Modifier.weight(1f))
                            AnalyticsCard("Active Lives", analytics.activeLiveStreams.toString(), Modifier.weight(1f))
                        }
                    }
                }
            }

            AdminTab.USERS -> {
                val cleanAdminQuery = userSearchQuery.trim().removePrefix("@").lowercase()
                val filteredUsers = remember(allUsers, cleanAdminQuery) {
                    if (cleanAdminQuery.isBlank()) allUsers
                    else allUsers.filter { u ->
                        u.username.lowercase().contains(cleanAdminQuery) ||
                            u.displayName.lowercase().contains(cleanAdminQuery) ||
                            u.role.lowercase().contains(cleanAdminQuery)
                    }
                }
                Column(modifier = Modifier.fillMaxSize()) {
                    OutlinedTextField(
                        value = userSearchQuery,
                        onValueChange = { userSearchQuery = it },
                        placeholder = { Text("Search users by @username or name...") },
                        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null, tint = GoldBright) },
                        trailingIcon = {
                            if (userSearchQuery.isNotBlank()) {
                                IconButton(onClick = { userSearchQuery = "" }) {
                                    Icon(Icons.Outlined.Close, contentDescription = "Clear user search", tint = WarmGrayText)
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                            .testTag("admin_user_search_input")
                    )

                    Text(
                        text = "Showing ${filteredUsers.size} of ${allUsers.size} registered accounts",
                        style = MaterialTheme.typography.labelSmall,
                        color = WarmGrayText,
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 2.dp)
                    )

                    if (filteredUsers.isEmpty()) {
                        EmptyStateView(
                            icon = Icons.Outlined.Search,
                            title = "No matching accounts",
                            subtitle = "Try another username or display name."
                        )
                    } else {
                        LazyColumn(
                            state = usersListState,
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(filteredUsers, key = { it.id }) { user ->
                                val isUpdatingVerification = user.id in verifyingUserIds
                                Surface(
                                    color = ObsidianCard,
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .border(
                                            0.8.dp,
                                            if (user.isGoldenVerified) GoldPrimary.copy(alpha = 0.55f) else ObsidianOutline,
                                            RoundedCornerShape(16.dp)
                                        )
                                ) {
                                    Column(
                                        modifier = Modifier.padding(14.dp),
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            UserAvatar(avatarUri = user.avatarUri, displayName = user.displayName, size = 44.dp)
                                            Column(modifier = Modifier.weight(1f)) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                ) {
                                                    Text(user.displayName, style = MaterialTheme.typography.titleSmall, color = IvoryText)
                                                    if (user.isGoldenVerified) GoldenVerifiedBadge(size = 15.dp)
                                                }
                                                Text(
                                                    "@${user.username} • ${user.role}" + if (user.isBanned) " • BANNED" else "",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = if (user.isBanned) CrimsonHeart else WarmGrayText
                                                )
                                            }
                                            Surface(
                                                color = if (user.isGoldenVerified) GoldTintSurface else ObsidianElevated,
                                                shape = RoundedCornerShape(8.dp),
                                                modifier = Modifier.border(
                                                    0.8.dp,
                                                    if (user.isGoldenVerified) GoldPrimary else ObsidianOutline,
                                                    RoundedCornerShape(8.dp)
                                                )
                                            ) {
                                                Text(
                                                    text = if (user.isGoldenVerified) "Verified • ON" else "Not Verified • OFF",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = if (user.isGoldenVerified) GoldBright else WarmGrayText,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier
                                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                                        .testTag("admin_verify_status_${user.username}")
                                                )
                                            }
                                        }

                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(ObsidianSurface, RoundedCornerShape(12.dp))
                                                .padding(horizontal = 12.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = "Golden Verification Badge",
                                                    style = MaterialTheme.typography.labelLarge,
                                                    color = IvoryText
                                                )
                                                Text(
                                                    text = if (isUpdatingVerification) "Saving to database..." else if (user.isGoldenVerified) "Enabled across Feed, Profile & Live" else "Disabled",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = if (isUpdatingVerification) GoldBright else WarmGrayText
                                                )
                                            }
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                if (isUpdatingVerification) {
                                                    CircularProgressIndicator(
                                                        modifier = Modifier.size(18.dp),
                                                        color = GoldBright,
                                                        strokeWidth = 2.dp
                                                    )
                                                }
                                                Switch(
                                                    checked = user.isGoldenVerified,
                                                    enabled = !isUpdatingVerification,
                                                    onCheckedChange = { checked ->
                                                        viewModel.adminSetVerification(user.id, checked)
                                                    },
                                                    colors = SwitchDefaults.colors(
                                                        checkedThumbColor = ObsidianBlack,
                                                        checkedTrackColor = GoldPrimary,
                                                        uncheckedThumbColor = IvoryText,
                                                        uncheckedTrackColor = ObsidianElevated
                                                    ),
                                                    modifier = Modifier.testTag("admin_verify_${user.username}")
                                                )
                                            }
                                        }

                                        if (!user.isSuperAdmin) {
                                            OutlinedButton(
                                                onClick = { viewModel.adminSetBan(user.id, !user.isBanned) },
                                                border = BorderStroke(1.dp, if (user.isBanned) EmeraldOnline else CrimsonHeart),
                                                shape = RoundedCornerShape(10.dp),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Text(
                                                    if (user.isBanned) "Unban User" else "Ban User",
                                                    color = if (user.isBanned) EmeraldOnline else CrimsonHeart,
                                                    style = MaterialTheme.typography.labelMedium
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            AdminTab.VERIFICATION -> {
                if (verRequests.isEmpty()) {
                    EmptyStateView(
                        icon = Icons.Rounded.Verified,
                        title = "No Verification Requests",
                        subtitle = "Creator & public figure Golden Verification applications appear here."
                    )
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(verRequests, key = { it.id }) { req ->
                            Surface(
                                color = ObsidianCard,
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.fillMaxWidth().border(0.8.dp, ObsidianOutline, RoundedCornerShape(16.dp))
                            ) {
                                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                            Text("${req.displayName} (@${req.username})", style = MaterialTheme.typography.titleSmall, color = IvoryText)
                                            if (req.status == VerificationStatus.APPROVED) GoldenVerifiedBadge(size = 14.dp)
                                        }
                                        Text(
                                            req.status,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = when (req.status) {
                                                VerificationStatus.APPROVED -> EmeraldOnline
                                                VerificationStatus.REJECTED -> CrimsonHeart
                                                else -> GoldBright
                                            },
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Text("Category: ${req.category} • ${SecurityUtils.formatRelativeTime(req.createdAt)}", style = MaterialTheme.typography.labelSmall, color = GoldBright)
                                    Text(req.reason, style = MaterialTheme.typography.bodySmall, color = WarmGrayText)
                                    if (req.portfolioLink.isNotBlank()) {
                                        Text("Link: ${req.portfolioLink}", style = MaterialTheme.typography.labelSmall, color = IvoryText)
                                    }
                                    if (req.status == VerificationStatus.PENDING) {
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                                            Button(
                                                onClick = {
                                                    viewModel.adminReviewVerificationRequest(req.id, req.userId, approved = true, note = "Verified by @utkarshrai")
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary, contentColor = ObsidianBlack),
                                                modifier = Modifier.weight(1f).testTag("admin_approve_ver_${req.id}")
                                            ) { Text("Approve Gold Tick", fontWeight = FontWeight.Bold) }
                                            OutlinedButton(
                                                onClick = {
                                                    viewModel.adminReviewVerificationRequest(req.id, req.userId, approved = false, note = "Criteria not yet met")
                                                },
                                                border = BorderStroke(1.dp, CrimsonHeart),
                                                modifier = Modifier.weight(1f).testTag("admin_reject_ver_${req.id}")
                                            ) { Text("Decline", color = CrimsonHeart) }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            AdminTab.LIVE -> {
                if (liveStreams.isEmpty()) {
                    EmptyStateView(
                        icon = Icons.Rounded.LiveTv,
                        title = "No Active Live Streams",
                        subtitle = "Active broadcasts can be monitored or closed by Super Admin here."
                    )
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(liveStreams, key = { it.id }) { stream ->
                            Surface(
                                color = ObsidianCard,
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.fillMaxWidth().border(0.8.dp, CrimsonHeart.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    UserAvatar(avatarUri = stream.host.avatarUri, displayName = stream.host.displayName, size = 40.dp)
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Text("@${stream.host.username}", style = MaterialTheme.typography.titleSmall, color = IvoryText)
                                            if (stream.host.isGoldenVerified) GoldenVerifiedBadge(size = 14.dp)
                                        }
                                        Text("${stream.title} • ${stream.viewerCount} viewers", style = MaterialTheme.typography.bodySmall, color = WarmGrayText)
                                    }
                                    Button(
                                        onClick = { viewModel.endLiveStream(stream.id) },
                                        colors = ButtonDefaults.buttonColors(containerColor = CrimsonHeart, contentColor = IvoryText)
                                    ) { Text("End Live") }
                                }
                            }
                        }
                    }
                }
            }

            AdminTab.REPORTS -> {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (reports.isEmpty()) {
                        item {
                            EmptyStateView(
                                icon = Icons.Rounded.Campaign,
                                title = "No Moderation Reports",
                                subtitle = "User and post reports submitted by the community appear here for review."
                            )
                        }
                    } else {
                        items(reports, key = { it.id }) { report ->
                            Surface(
                                color = ObsidianCard,
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.fillMaxWidth().border(0.8.dp, ObsidianOutline, RoundedCornerShape(16.dp))
                            ) {
                                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(
                                        text = "${report.targetType} REPORT • ${report.status}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (report.status == ReportStatus.OPEN) AmberWarning else EmeraldOnline
                                    )
                                    Text("Target: ${report.targetSummary}", color = IvoryText, style = MaterialTheme.typography.titleSmall)
                                    Text("Reason: ${report.reason}", color = WarmGrayText, style = MaterialTheme.typography.bodySmall)
                                    Text("Reported by @${report.reporter.username}", color = DimGrayText, style = MaterialTheme.typography.labelSmall)

                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        if (report.targetType == "POST") {
                                            Button(
                                                onClick = {
                                                    viewModel.adminDeletePost(report.targetId)
                                                    viewModel.adminResolveReport(report.id, ReportStatus.RESOLVED)
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = CrimsonHeart, contentColor = IvoryText),
                                                modifier = Modifier.weight(1f)
                                            ) { Text("Delete Post", style = MaterialTheme.typography.labelSmall) }
                                        }
                                        OutlinedButton(
                                            onClick = { viewModel.adminResolveReport(report.id, ReportStatus.RESOLVED) },
                                            modifier = Modifier.weight(1f)
                                        ) { Text("Resolve", color = EmeraldOnline, style = MaterialTheme.typography.labelSmall) }
                                        OutlinedButton(
                                            onClick = { viewModel.adminResolveReport(report.id, ReportStatus.DISMISSED) },
                                            modifier = Modifier.weight(1f)
                                        ) { Text("Dismiss", color = WarmGrayText, style = MaterialTheme.typography.labelSmall) }
                                    }
                                }
                            }
                        }
                    }

                    if (allPosts.isNotEmpty()) {
                        item {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text("Quick Content Moderation", style = MaterialTheme.typography.titleMedium, color = GoldBright)
                        }
                        items(allPosts.take(15), key = { "mod_${it.id}" }) { post ->
                            Surface(
                                color = ObsidianCard,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("@${post.author.username} • ${post.mediaType}", style = MaterialTheme.typography.labelLarge, color = GoldBright)
                                        Text(post.caption.ifBlank { "(No caption)" }, maxLines = 1, style = MaterialTheme.typography.bodySmall, color = IvoryText)
                                    }
                                    TextButton(onClick = { viewModel.adminDeletePost(post.id) }) {
                                        Text("Remove", color = CrimsonHeart)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            AdminTab.ANNOUNCE -> {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Broadcast Platform Announcement", style = MaterialTheme.typography.titleMedium, color = IvoryText)
                    Text(
                        "Send an official SCROLL DOWN alert to every active user's notification center.",
                        style = MaterialTheme.typography.bodySmall,
                        color = WarmGrayText
                    )
                    OutlinedTextField(
                        value = announcementText,
                        onValueChange = { announcementText = it },
                        label = { Text("Official Announcement") },
                        minLines = 3,
                        maxLines = 5,
                        modifier = Modifier.fillMaxWidth().testTag("admin_announcement_input")
                    )
                    Button(
                        onClick = {
                            if (announcementText.isNotBlank()) {
                                viewModel.adminBroadcastAnnouncement(announcementText)
                                announcementText = ""
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary, contentColor = ObsidianBlack),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth().height(50.dp).testTag("admin_broadcast_button")
                    ) {
                        Icon(Icons.Rounded.Campaign, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Send Official Announcement", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun AnalyticsCard(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        color = ObsidianCard,
        shape = RoundedCornerShape(16.dp),
        modifier = modifier.border(1.dp, GoldPrimary.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(value, style = MaterialTheme.typography.displaySmall, color = GoldBright)
            Text(label, style = MaterialTheme.typography.labelMedium, color = WarmGrayText)
        }
    }
}
