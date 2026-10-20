package com.example.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ClipEntity
import com.example.data.model.TakeEntity
import com.example.ui.theme.StatusRecording
import com.example.ui.theme.StemBacking
import com.example.ui.theme.StemUserTake
import com.example.ui.theme.StemVocal
import com.example.ui.theme.StudioAccent
import com.example.ui.theme.StudioBorder
import com.example.ui.theme.StudioCanvas
import com.example.ui.theme.StudioContainer
import com.example.ui.theme.StudioElevated
import kotlin.math.max
import kotlin.math.min

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.StateFlow

@Composable
fun DualWaveformSyncView(
    clip: ClipEntity?,
    originalVocalPeaks: FloatArray,
    activeTake: TakeEntity?,
    activeTakePeaks: FloatArray,
    playheadMs: Long = 0L,
    playheadFlow: StateFlow<Long>? = null,
    isRecording: Boolean,
    liveAmplitude: Float = 0f,
    isPlayingOriginal: Boolean = false,
    onPlayOriginalClick: () -> Unit = {},
    isPlayingRecorded: Boolean = false,
    onPlayRecordedClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val currentPlayheadMs = if (playheadFlow != null) {
        val observed by playheadFlow.collectAsStateWithLifecycle()
        observed
    } else {
        playheadMs
    }
    val infiniteTransition = rememberInfiniteTransition(label = "rec_pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(500),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(StudioContainer, RoundedCornerShape(10.dp))
            .border(1.dp, StudioBorder, RoundedCornerShape(10.dp))
            .padding(12.dp)
            .testTag("dual_waveform_sync_view")
    ) {
        // Lane 1 Header: Original Vocal Stem + Dedicated Play Button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(StemVocal, CircleShape)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Column {
                    Text(
                        text = "ORIGINAL VOCALS (SEPARATED)",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = StemVocal
                    )
                    Text(
                        text = "+ Synchronized Residual Audio",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = StemBacking
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (clip != null) {
                    Text(
                        text = "${clip.speakerName} • ${clip.endMs - clip.startMs}ms",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Button(
                    onClick = onPlayOriginalClick,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isPlayingOriginal) StemVocal else StudioElevated
                    ),
                    border = BorderStroke(1.dp, StemVocal),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                    modifier = Modifier
                        .height(30.dp)
                        .testTag("play_original_waveform_button")
                ) {
                    Icon(
                        imageVector = if (isPlayingOriginal) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlayingOriginal) "Pause Original" else "Play Original",
                        tint = if (isPlayingOriginal) Color.Black else StemVocal,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isPlayingOriginal) "Pause" else "Play Original",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = if (isPlayingOriginal) Color.Black else StemVocal
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Lane 1 Canvas: Original Vocal Waveform
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .background(StudioCanvas, RoundedCornerShape(6.dp))
                .border(1.dp, StudioBorder, RoundedCornerShape(6.dp))
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                val midY = h / 2f

                drawLine(StudioBorder, Offset(0f, midY), Offset(w, midY), 1.dp.toPx())

                if (originalVocalPeaks.isNotEmpty()) {
                    val count = min(originalVocalPeaks.size, 160)
                    val barWidth = w / count
                    for (i in 0 until count) {
                        val norm = originalVocalPeaks[i].coerceIn(0.02f, 1f)
                        val barH = norm * (h * 0.85f)
                        val x = i * barWidth
                        drawRoundRect(
                            color = StemVocal,
                            topLeft = Offset(x, midY - (barH / 2f)),
                            size = Size(max(1f, barWidth - 1f), barH),
                            cornerRadius = CornerRadius(1.dp.toPx())
                        )
                    }
                }

                // Playhead on Lane 1
                if (clip != null && clip.endMs > clip.startMs) {
                    val clipDuration = clip.endMs - clip.startMs
                    val relPlayhead = (currentPlayheadMs - clip.startMs).coerceIn(0L, clipDuration)
                    val playheadX = (relPlayhead.toFloat() / clipDuration.toFloat()) * w
                    drawLine(Color.White, Offset(playheadX, 0f), Offset(playheadX, h), 2.dp.toPx())
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Lane 2 Header: Active Dub Take + Dedicated Play Button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(if (isRecording) StatusRecording.copy(alpha = pulseAlpha) else StemUserTake, CircleShape)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Column {
                    Text(
                        text = if (isRecording) "RECORDING IN PROGRESS..." else "RECORDED VOCAL (ACTIVE TAKE)",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = if (isRecording) StatusRecording else StemUserTake
                    )
                    Text(
                        text = "+ Synchronized Residual Audio",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = StemBacking
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (activeTake != null) {
                    Text(
                        text = "Take #${activeTake.takeNumber}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Button(
                    onClick = onPlayRecordedClick,
                    enabled = activeTake != null && !isRecording,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isPlayingRecorded) StemUserTake else StudioElevated,
                        disabledContainerColor = StudioElevated.copy(alpha = 0.5f)
                    ),
                    border = BorderStroke(1.dp, if (activeTake != null) StemUserTake else StudioBorder),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                    modifier = Modifier
                        .height(30.dp)
                        .testTag("play_recorded_waveform_button")
                ) {
                    Icon(
                        imageVector = if (isPlayingRecorded) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlayingRecorded) "Pause Take" else "Play Recorded",
                        tint = if (isPlayingRecorded) Color.Black else if (activeTake != null) StemUserTake else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isPlayingRecorded) "Pause" else if (activeTake != null) "Play Take #${activeTake.takeNumber}" else "No Take",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = if (isPlayingRecorded) Color.Black else if (activeTake != null) StemUserTake else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Lane 2 Canvas: Active Take Waveform or Live Recording Stream
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .background(StudioCanvas, RoundedCornerShape(6.dp))
                .border(
                    1.dp,
                    if (isRecording) StatusRecording.copy(alpha = pulseAlpha) else StudioBorder,
                    RoundedCornerShape(6.dp)
                )
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                val midY = h / 2f

                drawLine(StudioBorder, Offset(0f, midY), Offset(w, midY), 1.dp.toPx())

                if (isRecording) {
                    // Draw live animated recording wave reacting to real microphone amplitude
                    val liveBars = 50
                    val barWidth = w / liveBars
                    for (i in 0 until liveBars) {
                        val decay = (i.toFloat() / liveBars)
                        val amp = (liveAmplitude * decay).coerceIn(0.05f, 1f)
                        val barH = amp * (h * 0.85f)
                        val x = i * barWidth
                        drawRoundRect(
                            color = StatusRecording,
                            topLeft = Offset(x, midY - (barH / 2f)),
                            size = Size(max(1f, barWidth - 1f), barH),
                            cornerRadius = CornerRadius(1.dp.toPx())
                        )
                    }
                } else if (activeTakePeaks.isNotEmpty()) {
                    val count = min(activeTakePeaks.size, 160)
                    val barWidth = w / count

                    // Shift by timeOffsetMs
                    val offsetFraction = if (clip != null && (clip.endMs > clip.startMs)) {
                        (activeTake?.timeOffsetMs ?: 0L).toFloat() / (clip.endMs - clip.startMs).toFloat()
                    } else 0f
                    val pixelOffset = offsetFraction * w

                    for (i in 0 until count) {
                        val norm = activeTakePeaks[i].coerceIn(0.02f, 1f)
                        val barH = norm * (h * 0.85f)
                        val x = (i * barWidth) + pixelOffset
                        if (x in 0f..w) {
                            drawRoundRect(
                                color = StemUserTake,
                                topLeft = Offset(x, midY - (barH / 2f)),
                                size = Size(max(1f, barWidth - 1f), barH),
                                cornerRadius = CornerRadius(1.dp.toPx())
                            )
                        }
                    }
                }

                // Playhead on Lane 2
                if (clip != null && clip.endMs > clip.startMs) {
                    val clipDuration = clip.endMs - clip.startMs
                    val relPlayhead = (currentPlayheadMs - clip.startMs).coerceIn(0L, clipDuration)
                    val playheadX = (relPlayhead.toFloat() / clipDuration.toFloat()) * w
                    drawLine(Color.White, Offset(playheadX, 0f), Offset(playheadX, h), 2.dp.toPx())
                }
            }
        }
    }
}
