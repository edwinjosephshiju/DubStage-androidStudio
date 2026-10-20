package com.example.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.StatusRecording
import com.example.ui.theme.StemUserTake
import com.example.ui.theme.StudioBorder
import com.example.ui.theme.StudioContainer
import com.example.ui.theme.WarningAmber

@Composable
fun VuMeter(
    decibels: Float, // -60f to 0f
    modifier: Modifier = Modifier,
    isStereo: Boolean = false
) {
    val animatedDb by animateFloatAsState(
        targetValue = decibels.coerceIn(-60f, 0f),
        label = "vu_meter_db"
    )

    // Normalize -60dB -> 0f, 0dB -> 1f
    val norm = ((animatedDb + 60f) / 60f).coerceIn(0f, 1f)

    Column(
        modifier = modifier
            .background(StudioContainer, RoundedCornerShape(6.dp))
            .border(1.dp, StudioBorder, RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .testTag("vu_meter")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "MIC INPUT LEVEL",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = if (decibels <= -59f) "-INF dB" else "%.1f dB".format(decibels),
                style = MaterialTheme.typography.labelSmall,
                color = if (decibels >= -2f) StatusRecording else StemUserTake
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(14.dp)
                .padding(top = 4.dp)
        ) {
            Canvas(modifier = Modifier.fillMaxWidth().fillMaxHeight()) {
                val totalSegments = 24
                val spacing = 2.dp.toPx()
                val totalWidth = size.width
                val segWidth = (totalWidth - (spacing * (totalSegments - 1))) / totalSegments
                val activeSegments = (norm * totalSegments).toInt()

                for (i in 0 until totalSegments) {
                    val segFraction = i.toFloat() / totalSegments
                    val segColor = when {
                        segFraction >= 0.88f -> StatusRecording
                        segFraction >= 0.70f -> WarningAmber
                        else -> StemUserTake
                    }

                    val color = if (i < activeSegments) segColor else segColor.copy(alpha = 0.15f)
                    val x = i * (segWidth + spacing)

                    drawRoundRect(
                        color = color,
                        topLeft = Offset(x, 0f),
                        size = Size(segWidth, size.height),
                        cornerRadius = CornerRadius(2.dp.toPx())
                    )
                }
            }
        }
    }
}
