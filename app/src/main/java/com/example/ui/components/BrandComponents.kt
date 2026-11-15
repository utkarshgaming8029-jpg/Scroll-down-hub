package com.example.ui.components

import android.net.Uri
import androidx.annotation.OptIn
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.example.R
import com.example.data.local.MediaTypes
import com.example.data.repository.MediaStorageHelper
import com.example.ui.theme.*
import kotlin.math.cos
import kotlin.math.sin

val GoldBrush = Brush.linearGradient(
    colors = listOf(GoldBright, GoldPrimary, GoldDeep)
)

@Composable
fun ScrollDownLogo(
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(if (compact) 32.dp else 44.dp)
                .clip(RoundedCornerShape(if (compact) 9.dp else 13.dp))
                .background(ObsidianBlack)
                .border(1.2.dp, GoldBrush, RoundedCornerShape(if (compact) 9.dp else 13.dp)),
            contentAlignment = Alignment.Center
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
            style = TextStyle(
                fontFamily = SyneFontFamily,
                fontWeight = FontWeight.ExtraBold,
                fontSize = if (compact) 17.sp else 22.sp,
                letterSpacing = 1.6.sp,
                brush = GoldBrush
            )
        )
    }
}

@Composable
fun GoldenVerifiedBadge(
    modifier: Modifier = Modifier,
    size: Dp = 17.dp
) {
    Canvas(
        modifier = modifier
            .size(size)
            .testTag("golden_verified_badge")
    ) {
        val radius = this.size.minDimension / 2f
        val center = Offset(this.size.width / 2f, this.size.height / 2f)
        val points = 16
        val outerR = radius
        val innerR = radius * 0.80f
        val sealPath = Path()
        for (i in 0 until points) {
            val angle = Math.toRadians((i * (360.0 / points)) - 90.0)
            val r = if (i % 2 == 0) outerR else innerR
            val x = center.x + (r * cos(angle)).toFloat()
            val y = center.y + (r * sin(angle)).toFloat()
            if (i == 0) sealPath.moveTo(x, y) else sealPath.lineTo(x, y)
        }
        sealPath.close()
        drawPath(
            path = sealPath,
            brush = Brush.linearGradient(
                colors = listOf(GoldBright, GoldPrimary, GoldDeep),
                start = Offset.Zero,
                end = Offset(this.size.width, this.size.height)
            )
        )
        val checkPath = Path().apply {
            moveTo(this@Canvas.size.width * 0.30f, this@Canvas.size.height * 0.52f)
            lineTo(this@Canvas.size.width * 0.45f, this@Canvas.size.height * 0.67f)
            lineTo(this@Canvas.size.width * 0.72f, this@Canvas.size.height * 0.36f)
        }
        drawPath(
            path = checkPath,
            color = ObsidianBlack,
            style = Stroke(
                width = this.size.minDimension * 0.13f,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )
    }
}

@Composable
fun UserAvatar(
    avatarUri: String?,
    displayName: String,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    hasStoryRing: Boolean = false,
    isStoryUnseen: Boolean = false,
    isOnline: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    val ringModifier = when {
        hasStoryRing && isStoryUnseen -> Modifier.border(2.2.dp, GoldBrush, CircleShape).padding(3.dp)
        hasStoryRing -> Modifier.border(1.5.dp, MaterialTheme.colorScheme.outline, CircleShape).padding(3.dp)
        else -> Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
    }

    val resolvedModel = remember(avatarUri) { MediaStorageHelper.resolveCoilModel(avatarUri) }

    Box(
        modifier = modifier
            .size(size)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .then(ringModifier)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            when {
                avatarUri == "drawable://img_admin_avatar" -> {
                    Image(
                        painter = painterResource(id = R.drawable.img_admin_avatar),
                        contentDescription = "$displayName avatar",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
                resolvedModel != null -> {
                    AsyncImage(
                        model = resolvedModel,
                        contentDescription = "$displayName avatar",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
                else -> {
                    val initials = displayName.trim()
                        .split(" ")
                        .filter { it.isNotBlank() }
                        .take(2)
                        .joinToString("") { it.first().uppercaseChar().toString() }
                        .ifBlank { "SD" }
                    Text(
                        text = initials,
                        style = MaterialTheme.typography.titleSmall.copy(
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = (size.value * 0.34f).sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            }
        }
        if (isOnline) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size((size.value * 0.28f).coerceAtLeast(10f).dp)
                    .clip(CircleShape)
                    .background(EmeraldOnline)
                    .border(2.dp, MaterialTheme.colorScheme.background, CircleShape)
            )
        }
    }
}

@Composable
fun RichCaptionText(
    username: String,
    isVerified: Boolean,
    caption: String,
    onHashtagClick: (String) -> Unit,
    onMentionClick: (String) -> Unit,
    onAuthorClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val textColor = MaterialTheme.colorScheme.onBackground
    val accentColor = MaterialTheme.colorScheme.primary
    val annotated = remember(username, isVerified, caption, textColor, accentColor) {
        buildAnnotatedString {
            withLink(
                LinkAnnotation.Clickable(
                    tag = "AUTHOR",
                    styles = TextLinkStyles(SpanStyle(color = textColor, fontWeight = FontWeight.Bold)),
                    linkInteractionListener = { onAuthorClick() }
                )
            ) {
                append("@$username")
            }
            if (isVerified) {
                append(" ")
                appendInlineContent("verified_badge", "[verified]")
            }
            append(" ")

            val tokenRegex = Regex("(#\\w+|@[a-zA-Z0-9_.]+)")
            var lastIdx = 0
            for (match in tokenRegex.findAll(caption)) {
                if (match.range.first > lastIdx) {
                    withStyle(SpanStyle(color = textColor.copy(alpha = 0.92f))) {
                        append(caption.substring(lastIdx, match.range.first))
                    }
                }
                val token = match.value
                if (token.startsWith("#")) {
                    val tagValue = token.drop(1).lowercase()
                    withLink(
                        LinkAnnotation.Clickable(
                            tag = "HASHTAG",
                            styles = TextLinkStyles(SpanStyle(color = accentColor, fontWeight = FontWeight.SemiBold)),
                            linkInteractionListener = { onHashtagClick(tagValue) }
                        )
                    ) {
                        append(token)
                    }
                } else {
                    val mentionValue = token.drop(1).lowercase()
                    withLink(
                        LinkAnnotation.Clickable(
                            tag = "MENTION",
                            styles = TextLinkStyles(SpanStyle(color = accentColor, fontWeight = FontWeight.SemiBold)),
                            linkInteractionListener = { onMentionClick(mentionValue) }
                        )
                    ) {
                        append(token)
                    }
                }
                lastIdx = match.range.last + 1
            }
            if (lastIdx < caption.length) {
                withStyle(SpanStyle(color = textColor.copy(alpha = 0.92f))) {
                    append(caption.substring(lastIdx))
                }
            }
        }
    }

    val inlineContent = remember(isVerified) {
        if (!isVerified) emptyMap()
        else mapOf(
            "verified_badge" to InlineTextContent(
                Placeholder(
                    width = 14.sp,
                    height = 14.sp,
                    placeholderVerticalAlign = PlaceholderVerticalAlign.TextCenter
                )
            ) {
                GoldenVerifiedBadge(size = 13.dp)
            }
        )
    }

    Text(
        text = annotated,
        inlineContent = inlineContent,
        style = MaterialTheme.typography.bodyMedium,
        modifier = modifier
    )
}

@Composable
fun PostMediaViewer(
    mediaUris: List<String>,
    mediaType: String,
    modifier: Modifier = Modifier,
    isActivePlayback: Boolean = true
) {
    if (mediaUris.isEmpty()) return
    when (mediaType) {
        MediaTypes.VIDEO, MediaTypes.REEL -> {
            ScrollDownVideoPlayer(
                videoUri = mediaUris.first(),
                isActive = isActivePlayback,
                modifier = modifier
                    .fillMaxWidth()
                    .aspectRatio(4f / 5f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(ObsidianBlack)
            )
        }
        MediaTypes.CAROUSEL -> {
            val pagerState = rememberPagerState(pageCount = { mediaUris.size })
            Box(
                modifier = modifier
                    .fillMaxWidth()
                    .aspectRatio(4f / 4.5f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(ObsidianBlack)
            ) {
                HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                    val model = remember(mediaUris[page]) { MediaStorageHelper.resolveCoilModel(mediaUris[page]) }
                    AsyncImage(
                        model = model,
                        contentDescription = "Post image ${page + 1}",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
                Surface(
                    color = ObsidianBlack.copy(alpha = 0.72f),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp)
                ) {
                    Text(
                        text = "${pagerState.currentPage + 1}/${mediaUris.size}",
                        style = MaterialTheme.typography.labelSmall.copy(color = GoldBright),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    repeat(mediaUris.size) { index ->
                        val active = index == pagerState.currentPage
                        val width by animateFloatAsState(
                            targetValue = if (active) 18f else 6f,
                            animationSpec = spring(),
                            label = "dot"
                        )
                        Box(
                            modifier = Modifier
                                .height(6.dp)
                                .width(width.dp)
                                .clip(CircleShape)
                                .background(if (active) GoldPrimary else IvoryText.copy(alpha = 0.4f))
                        )
                    }
                }
            }
        }
        else -> {
            val model = remember(mediaUris.first()) { MediaStorageHelper.resolveCoilModel(mediaUris.first()) }
            AsyncImage(
                model = model,
                contentDescription = "Post photo",
                modifier = modifier
                    .fillMaxWidth()
                    .aspectRatio(4f / 4.5f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(ObsidianBlack),
                contentScale = ContentScale.Crop
            )
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
fun ScrollDownVideoPlayer(
    videoUri: String,
    isActive: Boolean,
    modifier: Modifier = Modifier,
    fillScreen: Boolean = false
) {
    val context = LocalContext.current
    var isMuted by remember { mutableStateOf(false) }
    var isPlaying by remember(isActive) { mutableStateOf(isActive) }
    val resolvedUri = remember(videoUri) { MediaStorageHelper.resolvePlayableUri(context, videoUri) }

    val exoPlayer = remember(resolvedUri) {
        ExoPlayer.Builder(context).build().apply {
            repeatMode = Player.REPEAT_MODE_ONE
            setMediaItem(MediaItem.fromUri(resolvedUri))
            prepare()
        }
    }

    LaunchedEffect(isPlaying) { exoPlayer.playWhenReady = isPlaying }
    LaunchedEffect(isMuted) { exoPlayer.volume = if (isMuted) 0f else 1f }
    DisposableEffect(exoPlayer) { onDispose { exoPlayer.release() } }

    Box(modifier = modifier.clickable { isPlaying = !isPlaying }) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false
                    resizeMode = if (fillScreen) AspectRatioFrameLayout.RESIZE_MODE_ZOOM else AspectRatioFrameLayout.RESIZE_MODE_FIT
                }
            },
            update = { view -> if (view.player !== exoPlayer) view.player = exoPlayer },
            modifier = Modifier.fillMaxSize()
        )

        if (!isPlaying) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(ObsidianBlack.copy(alpha = 0.65f))
                    .border(1.dp, GoldPrimary, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = "Resume video",
                    tint = GoldBright,
                    modifier = Modifier.size(34.dp)
                )
            }
        }

        IconButton(
            onClick = { isMuted = !isMuted },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(10.dp)
                .size(36.dp)
                .clip(CircleShape)
                .background(ObsidianBlack.copy(alpha = 0.65f))
        ) {
            Icon(
                imageVector = if (isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                contentDescription = if (isMuted) "Unmute" else "Mute",
                tint = IvoryText,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
fun EmptyStateView(
    icon: ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 28.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(68.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer)
                .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(30.dp)
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        if (actionLabel != null && onAction != null) {
            Spacer(modifier = Modifier.height(4.dp))
            Button(
                onClick = onAction,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(actionLabel, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun GoldenPulseLoader(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "golden_pulse")
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        listOf(0, 160, 320).forEach { delayMs ->
            val scale by transition.animateFloat(
                initialValue = 0.55f,
                targetValue = 1.15f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 580, delayMillis = delayMs, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "dot_$delayMs"
            )
            Box(
                modifier = Modifier
                    .size((10f * scale).dp)
                    .clip(CircleShape)
                    .background(GoldBrush)
            )
        }
    }
}
