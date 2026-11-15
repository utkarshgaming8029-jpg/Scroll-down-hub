package com.example.ui.create

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Collections
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.data.ai.AiCaptionSuggestionResult
import com.example.data.local.PostAudience
import com.example.ui.components.GoldenPulseLoader
import com.example.ui.components.ScrollDownVideoPlayer
import com.example.ui.main.MainViewModel
import com.example.ui.theme.*

enum class CreateMode(val label: String) {
    POST("Post"),
    REEL("Reel"),
    STORY("Story")
}

@Composable
fun CreateScreen(
    viewModel: MainViewModel,
    initialMode: CreateMode = CreateMode.POST,
    onPublished: (CreateMode) -> Unit,
    modifier: Modifier = Modifier
) {
    val allUsers by viewModel.allUsers.collectAsStateWithLifecycle()
    var mode by remember(initialMode) { mutableStateOf(initialMode) }
    var caption by remember { mutableStateOf("") }
    var musicTitle by remember { mutableStateOf("") }
    var highlightTitle by remember { mutableStateOf("") }
    var selectedUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var isVideo by remember { mutableStateOf(false) }
    var isPublishing by remember { mutableStateOf(false) }
    var audience by remember { mutableStateOf(PostAudience.ALL) }
    var scheduleOffsetMs by remember { mutableStateOf(0L) }
    var aiLoading by remember { mutableStateOf(false) }
    var aiError by remember { mutableStateOf<String?>(null) }
    var aiResult by remember { mutableStateOf<AiCaptionSuggestionResult?>(null) }

    val multiPhotoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(5)
    ) { uris ->
        if (uris.isNotEmpty()) {
            selectedUris = uris
            isVideo = false
        }
    }

    val singlePhotoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            selectedUris = listOf(uri)
            isVideo = false
        }
    }

    val videoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            selectedUris = listOf(uri)
            isVideo = true
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianBlack)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Create Studio",
            style = MaterialTheme.typography.headlineMedium,
            color = GoldBright
        )

        // Mode Switcher
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(ObsidianCard)
                .border(1.dp, ObsidianSubtleBorder, RoundedCornerShape(14.dp))
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            CreateMode.entries.forEach { tab ->
                val selected = mode == tab
                Surface(
                    onClick = {
                        mode = tab
                        selectedUris = emptyList()
                        isVideo = (tab == CreateMode.REEL)
                    },
                    color = if (selected) GoldPrimary else ObsidianCard,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f).height(42.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = tab.label,
                            style = MaterialTheme.typography.labelLarge,
                            color = if (selected) ObsidianBlack else WarmGrayText,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }
        }

        // Media Picker Actions
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (mode != CreateMode.REEL) {
                OutlinedButton(
                    onClick = {
                        if (mode == CreateMode.POST) {
                            multiPhotoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        } else {
                            singlePhotoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        }
                    },
                    border = androidx.compose.foundation.BorderStroke(1.dp, GoldPrimary),
                    modifier = Modifier.weight(1f).height(48.dp).testTag("pick_photo_button")
                ) {
                    Icon(Icons.Rounded.Collections, contentDescription = null, tint = GoldBright)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (mode == CreateMode.POST) "Photos (1-5)" else "Story Photo",
                        color = GoldBright
                    )
                }
            }

            OutlinedButton(
                onClick = {
                    videoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
                },
                border = androidx.compose.foundation.BorderStroke(1.dp, GoldPrimary),
                modifier = Modifier.weight(1f).height(48.dp).testTag("pick_video_button")
            ) {
                Icon(Icons.Rounded.Movie, contentDescription = null, tint = GoldBright)
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (mode == CreateMode.REEL) "Select Reel Video" else "Select Video",
                    color = GoldBright
                )
            }
        }

        // Preview Box
        Surface(
            color = ObsidianSurface,
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(250.dp)
                .border(1.dp, ObsidianOutline, RoundedCornerShape(18.dp))
        ) {
            if (selectedUris.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = if (mode == CreateMode.REEL) Icons.Outlined.VideoFile else Icons.Outlined.AddPhotoAlternate,
                        contentDescription = null,
                        tint = GoldPrimary,
                        modifier = Modifier.size(44.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Pick media using Android Photo Picker",
                        style = MaterialTheme.typography.bodyMedium,
                        color = WarmGrayText
                    )
                    Text(
                        text = "Images are automatically compressed for speed",
                        style = MaterialTheme.typography.bodySmall,
                        color = DimGrayText
                    )
                }
            } else if (isVideo) {
                ScrollDownVideoPlayer(
                    videoUri = selectedUris.first().toString(),
                    isActive = true,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                LazyRow(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(selectedUris) { uri ->
                        AsyncImage(
                            model = uri,
                            contentDescription = "Selected preview",
                            modifier = Modifier
                                .fillMaxHeight()
                                .aspectRatio(4f / 5f)
                                .clip(RoundedCornerShape(12.dp)),
                            contentScale = ContentScale.Crop
                        )
                    }
                }
            }
        }

        OutlinedTextField(
            value = caption,
            onValueChange = { caption = it },
            label = { Text(if (mode == CreateMode.STORY) "Story Caption (optional)" else "Caption, #hashtags & @mentions") },
            minLines = 3,
            maxLines = 5,
            modifier = Modifier.fillMaxWidth().testTag("create_caption_input")
        )

        // AI Caption & Hashtag Assistant (Firebase AI Logic)
        Surface(
            color = ObsidianCard,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, GoldPrimary.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = GoldBright, modifier = Modifier.size(18.dp))
                        Text("AI Caption & Hashtag Studio", style = MaterialTheme.typography.titleSmall, color = GoldBright)
                    }
                    TextButton(
                        enabled = !aiLoading,
                        onClick = {
                            aiLoading = true
                            aiError = null
                            viewModel.suggestCaptionsWithAi(
                                draftOrTopic = caption,
                                isReel = mode == CreateMode.REEL,
                                isStory = mode == CreateMode.STORY
                            ) { res ->
                                aiLoading = false
                                res.onSuccess { aiResult = it }
                                    .onFailure { aiError = it.message ?: "Unable to generate suggestions." }
                            }
                        },
                        modifier = Modifier.testTag("ai_suggest_captions_button")
                    ) {
                        Text(if (aiLoading) "Thinking..." else "Suggest", color = GoldBright, fontWeight = FontWeight.Bold)
                    }
                }
                if (aiLoading) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        GoldenPulseLoader()
                        Text("Crafting luxury captions & tags...", style = MaterialTheme.typography.bodySmall, color = WarmGrayText)
                    }
                }
                aiError?.let { err ->
                    Text(err, style = MaterialTheme.typography.bodySmall, color = CrimsonHeart)
                }
                aiResult?.let { res ->
                    if (res.moodTip.isNotBlank()) {
                        Text("Tip: ${res.moodTip}", style = MaterialTheme.typography.labelSmall, color = GoldPrimary)
                    }
                    res.captions.forEach { idea ->
                        Surface(
                            onClick = {
                                val tags = res.hashtags.take(5).joinToString(" ") { "#$it" }
                                caption = if (tags.isBlank()) idea else "$idea\n\n$tags"
                            },
                            color = ObsidianSurface,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = idea,
                                style = MaterialTheme.typography.bodySmall,
                                color = IvoryText,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }
                    if (res.hashtags.isNotEmpty()) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(res.hashtags) { tag ->
                                AssistChip(
                                    onClick = { caption = if (caption.isBlank()) "#$tag" else "$caption #$tag" },
                                    label = { Text("#$tag", color = GoldBright) }
                                )
                            }
                        }
                    }
                }
            }
        }

        // Quick Tagging Chips
        if (mode != CreateMode.STORY && allUsers.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Quick tag creators or hashtags", style = MaterialTheme.typography.labelSmall, color = WarmGrayText)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(listOf("#scrolldown", "#luxury", "#editorial", "#reels")) { tag ->
                        AssistChip(
                            onClick = { caption = if (caption.isBlank()) "$tag " else "$caption $tag" },
                            label = { Text(tag, color = GoldBright) }
                        )
                    }
                    items(allUsers.take(6), key = { it.id }) { user ->
                        AssistChip(
                            onClick = { caption = if (caption.isBlank()) "@${user.username} " else "$caption @${user.username}" },
                            label = { Text("@${user.username}", color = IvoryText) }
                        )
                    }
                }
            }
        }

        // Audience Selector (Everyone vs Close Friends)
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Audience", style = MaterialTheme.typography.labelSmall, color = WarmGrayText)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FilterChip(
                    selected = audience == PostAudience.ALL,
                    onClick = { audience = PostAudience.ALL },
                    label = { Text("Everyone") }
                )
                FilterChip(
                    selected = audience == PostAudience.CLOSE_FRIENDS,
                    onClick = { audience = PostAudience.CLOSE_FRIENDS },
                    label = { Text("Close Friends ★") }
                )
            }
        }

        if (mode != CreateMode.STORY) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Publishing Schedule", style = MaterialTheme.typography.labelSmall, color = WarmGrayText)
                val presets = listOf(
                    0L to "Publish Now",
                    15L * 60_000L to "In 15m",
                    60L * 60_000L to "In 1h",
                    24L * 60L * 60_000L to "Tomorrow"
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(presets) { (offset, label) ->
                        FilterChip(
                            selected = scheduleOffsetMs == offset,
                            onClick = { scheduleOffsetMs = offset },
                            label = { Text(label) }
                        )
                    }
                }
            }
        }

        if (mode == CreateMode.REEL || (mode == CreateMode.POST && isVideo)) {
            OutlinedTextField(
                value = musicTitle,
                onValueChange = { musicTitle = it },
                label = { Text("Soundtrack / Music Title (optional)") },
                leadingIcon = { Icon(Icons.Outlined.MusicNote, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }

        if (mode == CreateMode.STORY) {
            OutlinedTextField(
                value = highlightTitle,
                onValueChange = { highlightTitle = it },
                label = { Text("Save to Highlight (optional, e.g. Travel)") },
                leadingIcon = { Icon(Icons.Rounded.AutoAwesome, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Button(
            onClick = {
                if (selectedUris.isEmpty()) return@Button
                isPublishing = true
                val scheduledAt = if (scheduleOffsetMs > 0L) System.currentTimeMillis() + scheduleOffsetMs else 0L
                when (mode) {
                    CreateMode.POST -> viewModel.createPostOrReel(
                        caption = caption,
                        mediaUris = selectedUris,
                        isVideo = isVideo,
                        isReel = false,
                        musicTitle = musicTitle,
                        scheduledAtMs = scheduledAt,
                        audience = audience,
                        onSuccess = {
                            isPublishing = false
                            caption = ""
                            musicTitle = ""
                            selectedUris = emptyList()
                            scheduleOffsetMs = 0L
                            aiResult = null
                            onPublished(CreateMode.POST)
                        }
                    )
                    CreateMode.REEL -> viewModel.createPostOrReel(
                        caption = caption,
                        mediaUris = selectedUris,
                        isVideo = true,
                        isReel = true,
                        musicTitle = musicTitle,
                        scheduledAtMs = scheduledAt,
                        audience = audience,
                        onSuccess = {
                            isPublishing = false
                            caption = ""
                            musicTitle = ""
                            selectedUris = emptyList()
                            scheduleOffsetMs = 0L
                            aiResult = null
                            onPublished(CreateMode.REEL)
                        }
                    )
                    CreateMode.STORY -> viewModel.createStory(
                        mediaUri = selectedUris.first(),
                        isVideo = isVideo,
                        caption = caption,
                        highlightTitle = highlightTitle.takeIf { it.isNotBlank() },
                        audience = audience,
                        onSuccess = {
                            isPublishing = false
                            caption = ""
                            highlightTitle = ""
                            selectedUris = emptyList()
                            aiResult = null
                            onPublished(CreateMode.STORY)
                        }
                    )
                }
            },
            enabled = selectedUris.isNotEmpty() && !isPublishing,
            colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary, contentColor = ObsidianBlack),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("publish_content_button")
        ) {
            if (isPublishing) {
                CircularProgressIndicator(color = ObsidianBlack, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
            } else {
                Text("Publish ${mode.label}", fontWeight = FontWeight.Bold)
            }
        }
    }
}
