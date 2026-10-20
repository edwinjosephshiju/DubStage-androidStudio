package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.model.ClipEntity
import com.example.ui.theme.StatusRecording
import com.example.ui.theme.StemVocal
import com.example.ui.theme.StudioAccent
import com.example.ui.theme.StudioBorder
import com.example.ui.theme.StudioCanvas
import com.example.ui.theme.StudioElevated
import kotlin.math.max
import kotlin.math.min

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.StateFlow

@Composable
fun WaveformCanvas(
    peaks: FloatArray,
    durationMs: Long,
    playheadMs: Long = 0L,
    playheadFlow: StateFlow<Long>? = null,
    clips: List<ClipEntity>,
    activeClipId: String? = null,
    waveformColor: Color = StemVocal,
    modifier: Modifier = Modifier,
    onSeek: (positionMs: Long) -> Unit = {},
    onClipSelected: (clipId: String) -> Unit = {},
    onClipBoundaryDragged: (clipId: String, newStartMs: Long, newEndMs: Long) -> Unit = { _, _, _ -> }
) {
    val currentPlayheadMs = if (playheadFlow != null) {
        val observed by playheadFlow.collectAsStateWithLifecycle()
        observed
    } else {
        playheadMs
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(130.dp)
            .background(StudioCanvas, RoundedCornerShape(8.dp))
            .border(1.dp, StudioBorder, RoundedCornerShape(8.dp))
            .testTag("waveform_canvas")
            .pointerInput(durationMs) {
                detectTapGestures { offset ->
                    if (durationMs > 0) {
                        val fraction = (offset.x / size.width).coerceIn(0f, 1f)
                        val seekMs = (fraction * durationMs).toLong()
                        onSeek(seekMs)

                        // Check if tap hit any clip
                        val clickedClip = clips.firstOrNull { seekMs in it.startMs..it.endMs }
                        if (clickedClip != null) {
                            onClipSelected(clickedClip.id)
                        }
                    }
                }
            }
            .pointerInput(durationMs, clips) {
                detectDragGestures { change, _ ->
                    if (durationMs > 0) {
                        val fraction = (change.position.x / size.width).coerceIn(0f, 1f)
                        val seekMs = (fraction * durationMs).toLong()
                        onSeek(seekMs)
                    }
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height
            val midY = height / 2f

            // Draw center baseline
            drawLine(
                color = StudioBorder,
                start = Offset(0f, midY),
                end = Offset(width, midY),
                strokeWidth = 1.dp.toPx()
            )

            // 1. Draw Clip Regions (Background highlights & boundary slice bars)
            if (durationMs > 0) {
                for (clip in clips) {
                    val startX = (clip.startMs.toFloat() / durationMs.toFloat()) * width
                    val endX = (clip.endMs.toFloat() / durationMs.toFloat()) * width
                    val clipWidth = max(2f, endX - startX)
                    val isActive = clip.id == activeClipId

                    // Clip area highlight
                    drawRoundRect(
                        color = if (isActive) StudioAccent.copy(alpha = 0.22f) else StudioElevated.copy(alpha = 0.5f),
                        topLeft = Offset(startX, 4.dp.toPx()),
                        size = Size(clipWidth, height - 8.dp.toPx()),
                        cornerRadius = CornerRadius(4.dp.toPx())
                    )

                    // Left start cut handle
                    drawLine(
                        color = if (isActive) StudioAccent else StudioBorder,
                        start = Offset(startX, 0f),
                        end = Offset(startX, height),
                        strokeWidth = if (isActive) 2.5.dp.toPx() else 1.5.dp.toPx()
                    )

                    // Right end cut handle
                    drawLine(
                        color = if (isActive) StudioAccent else StudioBorder,
                        start = Offset(endX, 0f),
                        end = Offset(endX, height),
                        strokeWidth = if (isActive) 2.5.dp.toPx() else 1.5.dp.toPx()
                    )
                }
            }

            // 2. Draw Peaks Envelope
            if (peaks.isNotEmpty()) {
                val totalBars = min(peaks.size, 1000)
                val barWidth = width / totalBars
                val barStep = max(1, peaks.size / totalBars)

                for (i in 0 until totalBars) {
                    val peakIdx = (i * barStep).coerceIn(0, peaks.size - 1)
                    val peakNorm = peaks[peakIdx].coerceIn(0.02f, 1.0f)
                    val barHeight = peakNorm * (height * 0.85f)
                    val topY = midY - (barHeight / 2f)
                    val x = i * barWidth

                    // Determine if within active clip
                    val sampleTimeMs = if (durationMs > 0) ((i.toFloat() / totalBars) * durationMs).toLong() else 0L
                    val isInActiveClip = activeClipId != null && clips.any { it.id == activeClipId && sampleTimeMs in it.startMs..it.endMs }

                    val barColor = if (isInActiveClip) waveformColor else waveformColor.copy(alpha = 0.65f)

                    drawRoundRect(
                        color = barColor,
                        topLeft = Offset(x, topY),
                        size = Size(max(1f, barWidth - 1f), barHeight),
                        cornerRadius = CornerRadius(1.dp.toPx())
                    )
                }
            }

            // 3. Draw Playhead Line
            if (durationMs > 0) {
                val playheadX = ((currentPlayheadMs.toFloat() / durationMs.toFloat()) * width).coerceIn(0f, width)

                // Vertical cursor
                drawLine(
                    color = Color.White,
                    start = Offset(playheadX, 0f),
                    end = Offset(playheadX, height),
                    strokeWidth = 2.dp.toPx()
                )

                // Playhead top triangle/dot
                drawCircle(
                    color = StudioAccent,
                    radius = 4.dp.toPx(),
                    center = Offset(playheadX, 4.dp.toPx())
                )
            }
        }
    }
}
