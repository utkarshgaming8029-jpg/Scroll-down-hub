package com.example.ui.notifications

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.NotificationTypes
import com.example.data.repository.SecurityUtils
import com.example.ui.components.EmptyStateView
import com.example.ui.components.GoldenVerifiedBadge
import com.example.ui.components.UserAvatar
import com.example.ui.main.MainViewModel
import com.example.ui.theme.*

@Composable
fun NotificationsScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
    onOpenUserProfile: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler { onBack() }

    val notifications by viewModel.notifications.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.markAllNotificationsRead()
    }

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
            Text(
                text = "Activity & Alerts",
                style = MaterialTheme.typography.headlineSmall,
                color = GoldBright,
                modifier = Modifier.weight(1f)
            )
        }
        HorizontalDivider(color = ObsidianSubtleBorder)

        if (notifications.isEmpty()) {
            EmptyStateView(
                icon = Icons.Outlined.NotificationsNone,
                title = "All Caught Up",
                subtitle = "Followers, likes, comments, replies, mentions, story reactions, follow requests, and admin announcements appear here."
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(notifications, key = { it.id }) { item ->
                    val isAdminAlert = item.type == NotificationTypes.ADMIN_ANNOUNCEMENT
                    Surface(
                        color = if (isAdminAlert) GoldTintSurface else ObsidianCard,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                0.8.dp,
                                if (isAdminAlert) GoldPrimary.copy(alpha = 0.5f) else ObsidianSubtleBorder,
                                RoundedCornerShape(16.dp)
                            )
                            .clickable { onOpenUserProfile(item.actor.id) }
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                if (isAdminAlert) {
                                    Icon(Icons.Rounded.Campaign, contentDescription = null, tint = GoldBright, modifier = Modifier.size(36.dp))
                                } else {
                                    UserAvatar(
                                        avatarUri = item.actor.avatarUri,
                                        displayName = item.actor.displayName,
                                        size = 42.dp
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(
                                            text = "@${item.actor.username}",
                                            style = MaterialTheme.typography.labelLarge,
                                            color = GoldBright
                                        )
                                        if (item.actor.isGoldenVerified) GoldenVerifiedBadge(size = 13.dp)
                                        Text(
                                            text = "• ${SecurityUtils.formatRelativeTime(item.createdAt)}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = DimGrayText
                                        )
                                    }
                                    Text(
                                        text = item.message,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = IvoryText
                                    )
                                }
                            }

                            if (item.isPendingFollowRequest) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(
                                        onClick = { viewModel.respondToFollowRequest(item.actor.id, accept = true) },
                                        colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary, contentColor = ObsidianBlack),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.weight(1f).height(38.dp)
                                    ) { Text("Accept", fontWeight = FontWeight.Bold) }
                                    OutlinedButton(
                                        onClick = { viewModel.respondToFollowRequest(item.actor.id, accept = false) },
                                        border = androidx.compose.foundation.BorderStroke(1.dp, ObsidianOutline),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.weight(1f).height(38.dp)
                                    ) { Text("Decline", color = WarmGrayText) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
