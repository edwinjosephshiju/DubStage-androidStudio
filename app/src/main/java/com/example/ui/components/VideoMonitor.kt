package com.example.ui.components

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.example.ui.theme.StudioAccent
import com.example.ui.theme.StudioBorder
import com.example.ui.theme.StudioCanvas
import com.example.ui.theme.StudioContainer
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.max
import kotlin.math.min

@Composable
fun VideoMonitor(
    videoUri: String?,
    exoPlayer: ExoPlayer?,
    isPlaying: Boolean,
    playheadFlow: StateFlow<Long>,
    durationMs: Long,
    activeSubtitle: String?,
    speakerName: String?,
    modifier: Modifier = Modifier,
    onTogglePlay: () -> Unit = {},
    onSeek: (positionMs: Long) -> Unit = {}
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(StudioContainer, RoundedCornerShape(12.dp))
            .border(1.dp, StudioBorder, RoundedCornerShape(12.dp))
            .padding(10.dp)
            .testTag("video_monitor")
    ) {
        // Isolated Video Player Surface (Stable Surface, zero detachment)
        VideoSurfaceView(
            videoUri = videoUri,
            exoPlayer = exoPlayer,
            activeSubtitle = activeSubtitle,
            speakerName = speakerName
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Isolated Transport Control Bar (Self-contained state, zero flickering)
        VideoPlaybackControls(
            isPlaying = isPlaying,
            playheadFlow = playheadFlow,
            durationMs = durationMs,
            onTogglePlay = onTogglePlay,
            onSeek = onSeek
        )
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun VideoSurfaceView(
    videoUri: String?,
    exoPlayer: ExoPlayer?,
    activeSubtitle: String?,
    speakerName: String?
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(8.dp))
            .background(StudioCanvas)
            .border(1.dp, StudioBorder, RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center
    ) {
        if (videoUri != null && exoPlayer != null) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        useController = false
                        layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        player = exoPlayer
                    }
                },
                update = { playerView ->
                    if (playerView.player != exoPlayer) {
                        playerView.player = exoPlayer
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            // Subtitle Prompter Overlay
            if (!activeSubtitle.isNullOrBlank()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .background(Color.Black.copy(alpha = 0.82f), RoundedCornerShape(8.dp))
                        .border(1.dp, StudioBorder, RoundedCornerShape(8.dp))
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = if (speakerName != null) "[$speakerName] $activeSubtitle" else activeSubtitle,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp
                        ),
                        color = Color.White
                    )
                }
            }

            // Resolution Badge
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
                    .background(StudioCanvas.copy(alpha = 0.85f), RoundedCornerShape(4.dp))
                    .border(1.dp, StudioBorder, RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "STUDIO MONITOR • 44.1kHz",
                    style = MaterialTheme.typography.labelSmall,
                    color = StudioAccent
                )
            }
        } else {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Videocam,
                    contentDescription = "No Video Loaded",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(44.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "No Video Ingested",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Load an MP4 / MKV video to begin segmentation and dubbing",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
private fun VideoPlaybackControls(
    isPlaying: Boolean,
    playheadFlow: StateFlow<Long>,
    durationMs: Long,
    onTogglePlay: () -> Unit,
    onSeek: (positionMs: Long) -> Unit
) {
    val playheadMs by playheadFlow.collectAsStateWithLifecycle()

    var isDragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }

    val safeDuration = durationMs.coerceAtLeast(1L)
    val currentProgress = if (isDragging) {
        dragFraction
    } else {
        (playheadMs.toFloat() / safeDuration.toFloat()).coerceIn(0f, 1f)
    }

    val displayPositionMs = if (isDragging) {
        (dragFraction * safeDuration).toLong()
    } else {
        playheadMs
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${formatTimecode(displayPositionMs)} / ${formatTimecode(durationMs)}",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold
                ),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.testTag("timecode_display")
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = { onSeek(max(0L, playheadMs - 3000L)) },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.FastRewind,
                        contentDescription = "Rewind 3s",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                IconButton(
                    onClick = onTogglePlay,
                    modifier = Modifier
                        .size(42.dp)
                        .background(StudioAccent, CircleShape)
                        .testTag("play_pause_button")
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = Color.White
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                IconButton(
                    onClick = { onSeek(min(durationMs, playheadMs + 3000L)) },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.FastForward,
                        contentDescription = "Forward 3s",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        if (durationMs > 0) {
            Slider(
                value = currentProgress,
                onValueChange = {
                    isDragging = true
                    dragFraction = it
                },
                onValueChangeFinished = {
                    val targetMs = (dragFraction * safeDuration).toLong()
                    onSeek(targetMs)
                    isDragging = false
                },
                valueRange = 0f..1f,
                colors = SliderDefaults.colors(
                    thumbColor = StudioAccent,
                    activeTrackColor = StudioAccent,
                    inactiveTrackColor = StudioBorder
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(28.dp)
                    .testTag("video_seek_slider")
            )
        }
    }
}

fun formatTimecode(ms: Long): String {
    val totalSeconds = (ms / 1000L).coerceAtLeast(0L)
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    val hundredths = ((ms % 1000L) / 10L).coerceAtLeast(0L)
    return String.format("%02d:%02d.%02d", minutes, seconds, hundredths)
}

fun parseTimecode(timeStr: String): Long? {
    val clean = timeStr.trim()
    if (clean.isEmpty()) return null
    return try {
        if (clean.contains(":")) {
            val parts = clean.split(":")
            if (parts.size == 2) {
                val mins = parts[0].toLongOrNull() ?: return null
                val secParts = parts[1].split(".")
                val secs = secParts[0].toLongOrNull() ?: return null
                val hundredths = if (secParts.size > 1) {
                    val fracStr = secParts[1].padEnd(2, '0').take(2)
                    fracStr.toLongOrNull() ?: 0L
                } else 0L
                (mins * 60L * 1000L) + (secs * 1000L) + (hundredths * 10L)
            } else null
        } else {
            val secParts = clean.split(".")
            val secs = secParts[0].toLongOrNull() ?: return null
            val hundredths = if (secParts.size > 1) {
                val fracStr = secParts[1].padEnd(2, '0').take(2)
                fracStr.toLongOrNull() ?: 0L
            } else 0L
            (secs * 1000L) + (hundredths * 10L)
        }
    } catch (_: Exception) {
        null
    }
}

