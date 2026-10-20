package com.example.ui.stage

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.engine.StudioAuditionMode
import com.example.ui.DubStageViewModel
import com.example.ui.PlaybackTarget
import com.example.ui.components.DualWaveformSyncView
import com.example.ui.components.VideoMonitor
import com.example.ui.components.VuMeter
import com.example.ui.components.formatTimecode
import com.example.ui.components.parseTimecode
import kotlin.math.max
import kotlin.math.min
import com.example.ui.theme.StatusRecording
import com.example.ui.theme.StemBacking
import com.example.ui.theme.StemUserTake
import com.example.ui.theme.StemVocal
import com.example.ui.theme.StudioAccent
import com.example.ui.theme.StudioBorder
import com.example.ui.theme.StudioCanvas
import com.example.ui.theme.StudioContainer
import com.example.ui.theme.StudioElevated
import com.example.ui.theme.WarningAmber
import java.io.File

@Composable
fun StageTabScreen(
    viewModel: DubStageViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activeProject by viewModel.activeProject.collectAsStateWithLifecycle()
    val clips by viewModel.clips.collectAsStateWithLifecycle()
    val activeClip by viewModel.activeClip.collectAsStateWithLifecycle()
    val takes by viewModel.takes.collectAsStateWithLifecycle()
    val activeTake by viewModel.activeTake.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val activePlaybackTarget by viewModel.activePlaybackTarget.collectAsStateWithLifecycle()
    val isPlayingOriginal = isPlaying && (activePlaybackTarget == PlaybackTarget.CLIP_ORIGINAL_VOCAL)
    val isPlayingRecorded = isPlaying && (activePlaybackTarget == PlaybackTarget.CLIP_RECORDED_VOCAL)

    val isRecording by viewModel.isRecording.collectAsStateWithLifecycle()
    val countdownNumber by viewModel.countdownNumber.collectAsStateWithLifecycle()
    val liveMicDb by viewModel.liveMicDecibels.collectAsStateWithLifecycle()
    val liveMicPeak by viewModel.liveMicPeak.collectAsStateWithLifecycle()

    val vocalPeaks by viewModel.vocalsWaveformPeaks.collectAsStateWithLifecycle()
    val activeTakePeaks by viewModel.activeTakePeaks.collectAsStateWithLifecycle()

    val auditionMode by viewModel.auditionMode.collectAsStateWithLifecycle()
    val backingGainDb by viewModel.backingGainDb.collectAsStateWithLifecycle()

    val isExporting by viewModel.isExporting.collectAsStateWithLifecycle()
    val exportProgress by viewModel.exportProgress.collectAsStateWithLifecycle()
    val exportedFilePath by viewModel.exportedFilePath.collectAsStateWithLifecycle()

    var isEditingStageTiming by remember(activeClip?.id) { mutableStateOf(false) }
    var stageStartTimeText by remember(activeClip?.id, activeClip?.startMs) { mutableStateOf(activeClip?.let { formatTimecode(it.startMs) } ?: "") }
    var stageEndTimeText by remember(activeClip?.id, activeClip?.endMs) { mutableStateOf(activeClip?.let { formatTimecode(it.endMs) } ?: "") }

    // Microphone Permission Launcher
    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.startPreRollAndRecord()
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(StudioCanvas)
            .padding(horizontal = 14.dp),
        contentPadding = PaddingValues(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Stage Header & Master Export Action
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "DUBSTAGE RECORDING DESK",
                        style = MaterialTheme.typography.labelSmall,
                        color = StemUserTake
                    )
                    Text(
                        text = activeProject?.title ?: "Dub Session",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Button(
                    onClick = { viewModel.exportMaster() },
                    enabled = activeProject != null && !isExporting,
                    colors = ButtonDefaults.buttonColors(containerColor = StudioAccent),
                    modifier = Modifier.testTag("export_master_button")
                ) {
                    Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (isExporting) "Exporting..." else "Export MP4")
                }
            }
        }

        // 2. Video Monitor with Synchronized Subtitle Overlay (Zero-Flicker)
        item {
            VideoMonitor(
                videoUri = activeProject?.videoUri,
                exoPlayer = viewModel.exoPlayer,
                isPlaying = isPlaying,
                playheadFlow = viewModel.playheadMs,
                durationMs = activeProject?.videoDurationMs ?: 0L,
                activeSubtitle = activeClip?.scriptText,
                speakerName = activeClip?.speakerName,
                onTogglePlay = { viewModel.togglePlay() },
                onSeek = { viewModel.seekTo(it) }
            )
        }

        // 3. Line-by-Line Teleprompter & Cue Stepper Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = StudioContainer),
                border = androidx.compose.foundation.BorderStroke(1.dp, StudioBorder)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    // Line stepper header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = { viewModel.previousClip() },
                                enabled = clips.isNotEmpty(),
                                modifier = Modifier.size(32.dp).testTag("prev_line_button")
                            ) {
                                Icon(Icons.Default.ArrowBack, contentDescription = "Previous Cue", tint = MaterialTheme.colorScheme.onSurface)
                            }

                            Text(
                                text = if (activeClip != null) "CUE #${activeClip?.clipIndex} OF ${clips.size}" else "NO CUES DETECTED",
                                style = MaterialTheme.typography.labelSmall,
                                color = StudioAccent,
                                modifier = Modifier.padding(horizontal = 8.dp)
                            )

                            IconButton(
                                onClick = { viewModel.nextClip() },
                                enabled = clips.isNotEmpty(),
                                modifier = Modifier.size(32.dp).testTag("next_line_button")
                            ) {
                                Icon(Icons.Default.ArrowForward, contentDescription = "Next Cue", tint = MaterialTheme.colorScheme.onSurface)
                            }
                        }

                        if (activeClip != null) {
                            val isCuePlaying = isPlaying && (activeClip?.id == viewModel.playbackClipId.collectAsStateWithLifecycle().value)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    onClick = {
                                        isEditingStageTiming = !isEditingStageTiming
                                        if (isEditingStageTiming) {
                                            stageStartTimeText = formatTimecode(activeClip!!.startMs)
                                            stageEndTimeText = formatTimecode(activeClip!!.endMs)
                                        }
                                    },
                                    shape = RoundedCornerShape(4.dp),
                                    color = if (isEditingStageTiming) StudioAccent.copy(alpha = 0.2f) else StudioElevated,
                                    border = androidx.compose.foundation.BorderStroke(1.dp, if (isEditingStageTiming) StudioAccent else StudioBorder),
                                    modifier = Modifier.testTag("stage_clip_timing_chip")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "${formatTimecode(activeClip!!.startMs)} - ${formatTimecode(activeClip!!.endMs)}",
                                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold),
                                            color = if (isEditingStageTiming) StudioAccent else MaterialTheme.colorScheme.onSurface
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Icon(
                                            imageVector = Icons.Default.Edit,
                                            contentDescription = "Edit Timing",
                                            tint = if (isEditingStageTiming) StudioAccent else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(12.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                Button(
                                    onClick = { viewModel.playClipVad(activeClip!!) },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isCuePlaying) StudioAccent else StudioElevated
                                    ),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, StudioAccent),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                    modifier = Modifier.height(28.dp).testTag("play_cue_button")
                                ) {
                                    Icon(
                                        imageVector = if (isCuePlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                        contentDescription = if (isCuePlaying) "Pause Cue" else "Play Cue",
                                        tint = if (isCuePlaying) Color.Black else StudioAccent,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (isCuePlaying) "Pause" else "Play Cue",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 11.sp),
                                        color = if (isCuePlaying) Color.Black else StudioAccent
                                    )
                                }
                            }
                        }
                    }

                    // Expandable Precision Timing Editor for active cue in DubStage
                    AnimatedVisibility(visible = isEditingStageTiming && activeClip != null) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                                .background(StudioElevated, RoundedCornerShape(8.dp))
                                .border(1.dp, StudioAccent.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                                .padding(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "FINE-TUNE CUE #${activeClip!!.clipIndex} SPAN (mm:ss.SS)",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 10.sp),
                                    color = StudioAccent
                                )
                                Text(
                                    text = "Span: %.2fs".format((activeClip!!.endMs - activeClip!!.startMs) / 1000f),
                                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    OutlinedTextField(
                                        value = stageStartTimeText,
                                        onValueChange = { input ->
                                            stageStartTimeText = input
                                            val parsed = parseTimecode(input)
                                            if (parsed != null && parsed < activeClip!!.endMs) {
                                                viewModel.updateClipTiming(activeClip!!.id, parsed, activeClip!!.endMs)
                                            }
                                        },
                                        label = { Text("Start (e.g. 00:10.00)", style = MaterialTheme.typography.labelSmall) },
                                        singleLine = true,
                                        textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = StudioAccent,
                                            unfocusedBorderColor = StudioBorder
                                        ),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        StageTimingNudge("-1s") {
                                            viewModel.nudgeClipTiming(activeClip!!.id, -1000L, 0L)
                                            stageStartTimeText = formatTimecode(max(0L, activeClip!!.startMs - 1000L))
                                        }
                                        StageTimingNudge("-0.1s") {
                                            viewModel.nudgeClipTiming(activeClip!!.id, -100L, 0L)
                                            stageStartTimeText = formatTimecode(max(0L, activeClip!!.startMs - 100L))
                                        }
                                        StageTimingNudge("+0.1s") {
                                            if (activeClip!!.startMs + 100L < activeClip!!.endMs) {
                                                viewModel.nudgeClipTiming(activeClip!!.id, 100L, 0L)
                                                stageStartTimeText = formatTimecode(activeClip!!.startMs + 100L)
                                            }
                                        }
                                        StageTimingNudge("+1s") {
                                            if (activeClip!!.startMs + 1000L < activeClip!!.endMs) {
                                                viewModel.nudgeClipTiming(activeClip!!.id, 1000L, 0L)
                                                stageStartTimeText = formatTimecode(activeClip!!.startMs + 1000L)
                                            }
                                        }
                                    }
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    OutlinedTextField(
                                        value = stageEndTimeText,
                                        onValueChange = { input ->
                                            stageEndTimeText = input
                                            val parsed = parseTimecode(input)
                                            if (parsed != null && parsed > activeClip!!.startMs) {
                                                viewModel.updateClipTiming(activeClip!!.id, activeClip!!.startMs, parsed)
                                            }
                                        },
                                        label = { Text("End (e.g. 00:20.50)", style = MaterialTheme.typography.labelSmall) },
                                        singleLine = true,
                                        textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = StudioAccent,
                                            unfocusedBorderColor = StudioBorder
                                        ),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        StageTimingNudge("-1s") {
                                            if (activeClip!!.endMs - 1000L > activeClip!!.startMs) {
                                                viewModel.nudgeClipTiming(activeClip!!.id, 0L, -1000L)
                                                stageEndTimeText = formatTimecode(activeClip!!.endMs - 1000L)
                                            }
                                        }
                                        StageTimingNudge("-0.1s") {
                                            if (activeClip!!.endMs - 100L > activeClip!!.startMs) {
                                                viewModel.nudgeClipTiming(activeClip!!.id, 0L, -100L)
                                                stageEndTimeText = formatTimecode(activeClip!!.endMs - 100L)
                                            }
                                        }
                                        StageTimingNudge("+0.1s") {
                                            viewModel.nudgeClipTiming(activeClip!!.id, 0L, 100L)
                                            stageEndTimeText = formatTimecode(activeClip!!.endMs + 100L)
                                        }
                                        StageTimingNudge("+1s") {
                                            viewModel.nudgeClipTiming(activeClip!!.id, 0L, 1000L)
                                            stageEndTimeText = formatTimecode(activeClip!!.endMs + 1000L)
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    OutlinedButton(
                                        onClick = {
                                            val current = viewModel.playheadMs.value
                                            if (current < activeClip!!.endMs) {
                                                viewModel.updateClipTiming(activeClip!!.id, current, activeClip!!.endMs)
                                                stageStartTimeText = formatTimecode(current)
                                            }
                                        },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                        modifier = Modifier.height(28.dp)
                                    ) {
                                        Text("Start = Playhead", style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp))
                                    }

                                    OutlinedButton(
                                        onClick = {
                                            val current = viewModel.playheadMs.value
                                            if (current > activeClip!!.startMs) {
                                                viewModel.updateClipTiming(activeClip!!.id, activeClip!!.startMs, current)
                                                stageEndTimeText = formatTimecode(current)
                                            }
                                        },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                        modifier = Modifier.height(28.dp)
                                    ) {
                                        Text("End = Playhead", style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp))
                                    }
                                }

                                Button(
                                    onClick = { isEditingStageTiming = false },
                                    colors = ButtonDefaults.buttonColors(containerColor = StudioAccent),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                    modifier = Modifier.height(28.dp)
                                ) {
                                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Done", style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp))
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Prompter Box
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(StudioElevated, RoundedCornerShape(8.dp))
                            .border(1.dp, StudioBorder, RoundedCornerShape(8.dp))
                            .padding(16.dp)
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .background(StemVocal, CircleShape)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = activeClip?.speakerName?.uppercase() ?: "SPEAKER 1",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = StemVocal
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = if (activeClip?.scriptText.isNullOrBlank())
                                    "[No script text for this cue. Type dialogue in the Forge tab or read along with original audio.]"
                                else
                                    "\"${activeClip!!.scriptText}\"",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 18.sp,
                                    lineHeight = 26.sp
                                ),
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }

        // 4. Pre-roll Countdown Ring Overlay
        if (countdownNumber != null) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(StatusRecording.copy(alpha = 0.15f), RoundedCornerShape(10.dp))
                        .border(2.dp, StatusRecording, RoundedCornerShape(10.dp))
                        .padding(20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "${countdownNumber}",
                            style = MaterialTheme.typography.displayMedium.copy(
                                fontSize = 48.sp,
                                fontWeight = FontWeight.Black
                            ),
                            color = StatusRecording
                        )
                        Text(
                            text = "PRE-ROLL COUNTDOWN — GET READY TO RECORD",
                            style = MaterialTheme.typography.labelSmall,
                            color = StatusRecording
                        )
                    }
                }
            }
        }

        // 5. Dual Waveform Synchronization View
        item {
            DualWaveformSyncView(
                clip = activeClip,
                originalVocalPeaks = vocalPeaks,
                activeTake = activeTake,
                activeTakePeaks = activeTakePeaks,
                playheadFlow = viewModel.playheadMs,
                isRecording = isRecording,
                liveAmplitude = liveMicPeak,
                isPlayingOriginal = isPlayingOriginal,
                onPlayOriginalClick = { viewModel.playOriginalWithResidual(activeClip) },
                isPlayingRecorded = isPlayingRecorded,
                onPlayRecordedClick = { viewModel.playRecordedWithResidual(activeClip) }
            )
        }

        // 6. Mic Input VU Meter & Recording Controls
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(StudioContainer, RoundedCornerShape(10.dp))
                    .border(1.dp, StudioBorder, RoundedCornerShape(10.dp))
                    .padding(14.dp)
            ) {
                VuMeter(decibels = liveMicDb)

                Spacer(modifier = Modifier.height(14.dp))

                // Big Recording Action Button
                if (!isRecording) {
                    Button(
                        onClick = { micPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO) },
                        colors = ButtonDefaults.buttonColors(containerColor = StatusRecording),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("record_take_button")
                    ) {
                        Icon(Icons.Default.Mic, contentDescription = null, modifier = Modifier.size(22.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (activeTake != null) "RECORD NEW TAKE FOR LINE #${activeClip?.clipIndex ?: 1}" else "RECORD FIRST TAKE",
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                } else {
                    Button(
                        onClick = { viewModel.stopRecordTake() },
                        colors = ButtonDefaults.buttonColors(containerColor = WarningAmber),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("stop_recording_button")
                    ) {
                        Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(22.dp), tint = Color.Black)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "STOP RECORDING TAKE",
                            style = MaterialTheme.typography.labelLarge,
                            color = Color.Black
                        )
                    }
                }
            }
        }

        // 7. Audition Deck & Take Comping
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = StudioContainer),
                border = androidx.compose.foundation.BorderStroke(1.dp, StudioBorder)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "AUDITION DECK & MONITORING MODES",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Audition Mode Chips
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        item {
                            AuditionChip(
                                label = "Original + Residual",
                                selected = auditionMode == StudioAuditionMode.ORIGINAL_WITH_RESIDUAL,
                                activeColor = StemVocal,
                                onClick = { viewModel.setAuditionMode(StudioAuditionMode.ORIGINAL_WITH_RESIDUAL) }
                            )
                        }
                        item {
                            AuditionChip(
                                label = "Take + Residual",
                                selected = auditionMode == StudioAuditionMode.RECORDED_WITH_RESIDUAL,
                                activeColor = StemUserTake,
                                onClick = { viewModel.setAuditionMode(StudioAuditionMode.RECORDED_WITH_RESIDUAL) }
                            )
                        }
                        item {
                            AuditionChip(
                                label = "Full Dub Mix",
                                selected = auditionMode == StudioAuditionMode.FULL_DUB_MIX,
                                activeColor = StudioAccent,
                                onClick = { viewModel.setAuditionMode(StudioAuditionMode.FULL_DUB_MIX) }
                            )
                        }
                        item {
                            AuditionChip(
                                label = "Solo Voice",
                                selected = auditionMode == StudioAuditionMode.SOLO_ORIGINAL,
                                activeColor = StemVocal,
                                onClick = { viewModel.setAuditionMode(StudioAuditionMode.SOLO_ORIGINAL) }
                            )
                        }
                        item {
                            AuditionChip(
                                label = "Solo Take",
                                selected = auditionMode == StudioAuditionMode.SOLO_ACTIVE_TAKE,
                                activeColor = StemUserTake,
                                onClick = { viewModel.setAuditionMode(StudioAuditionMode.SOLO_ACTIVE_TAKE) }
                            )
                        }
                        item {
                            AuditionChip(
                                label = "Residual Only",
                                selected = auditionMode == StudioAuditionMode.BACKING_ONLY,
                                activeColor = StemBacking,
                                onClick = { viewModel.setAuditionMode(StudioAuditionMode.BACKING_ONLY) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Take Gain & Slip Offset Adjustments (for active take)
                    if (activeTake != null) {
                        Text(
                            text = "TAKE #${activeTake!!.takeNumber} TUNING (MICRO-TIMING SLIP & GAIN)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        // Timing Slip Offset (-250ms to +250ms)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Timing Slip Offset: ${activeTake!!.timeOffsetMs} ms",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            TextButton(onClick = { viewModel.updateTakeSlipOffset(0L) }) {
                                Text("Reset (0ms)", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        Slider(
                            value = activeTake!!.timeOffsetMs.toFloat(),
                            onValueChange = { viewModel.updateTakeSlipOffset(it.toLong()) },
                            valueRange = -250f..250f,
                            colors = SliderDefaults.colors(
                                thumbColor = StemUserTake,
                                activeTrackColor = StemUserTake,
                                inactiveTrackColor = StudioBorder
                            ),
                            modifier = Modifier.height(24.dp).testTag("take_slip_slider")
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Gain Slider (-12dB to +12dB)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Take Gain: %+.1f dB".format(activeTake!!.gainDb),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            TextButton(onClick = { viewModel.updateTakeGain(0.0f) }) {
                                Text("Unity (0dB)", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        Slider(
                            value = activeTake!!.gainDb,
                            onValueChange = { viewModel.updateTakeGain(it) },
                            valueRange = -12.0f..12.0f,
                            colors = SliderDefaults.colors(
                                thumbColor = StemUserTake,
                                activeTrackColor = StemUserTake,
                                inactiveTrackColor = StudioBorder
                            ),
                            modifier = Modifier.height(24.dp).testTag("take_gain_slider")
                        )
                    }

                    // Takes list for this clip
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "RECORDED TAKES (${takes.size})",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    if (takes.isEmpty()) {
                        Text(
                            text = "No takes recorded for this cue yet. Tap [Record Take] above to record.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            takes.forEach { take ->
                                val isSelected = take.id == activeTake?.id
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(if (isSelected) StudioElevated else StudioCanvas, RoundedCornerShape(6.dp))
                                        .border(1.dp, if (isSelected) StemUserTake else StudioBorder, RoundedCornerShape(6.dp))
                                        .clickable { viewModel.selectActiveTake(take.id) }
                                        .padding(horizontal = 12.dp, vertical = 8.dp)
                                        .testTag("take_item_${take.takeNumber}"),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = if (isSelected) Icons.Default.Star else Icons.Outlined.StarBorder,
                                            contentDescription = "Active Take",
                                            tint = if (isSelected) StemUserTake else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "Take #${take.takeNumber}",
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                            color = if (isSelected) StemUserTake else MaterialTheme.colorScheme.onSurface
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "${take.durationMs}ms • %+.1fdB".format(take.gainDb),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    IconButton(
                                        onClick = { viewModel.deleteTake(take) },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Delete Take",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(16.dp)
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

    // Export In-Progress Dialog
    if (isExporting) {
        val prog = exportProgress
        AlertDialog(
            onDismissRequest = { viewModel.cancelExport() },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.FileDownload, contentDescription = null, tint = StudioAccent)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Exporting Master Video & Subtitles")
                }
            },
            text = {
                Column {
                    Text(
                        text = if (prog != null) "${prog.stage} (${prog.percent}%)" else "Preparing video and audio streams...",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    LinearProgressIndicator(
                        progress = { if (prog != null) prog.percent / 100f else 0f },
                        modifier = Modifier.fillMaxWidth().height(6.dp),
                        color = StudioAccent,
                        trackColor = StudioBorder
                    )
                }
            },
            confirmButton = {},
            dismissButton = {
                Button(
                    onClick = { viewModel.cancelExport() },
                    colors = ButtonDefaults.buttonColors(containerColor = StudioElevated),
                    border = androidx.compose.foundation.BorderStroke(1.dp, StudioBorder),
                    modifier = Modifier.testTag("cancel_export_button")
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Cancel", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Cancel Export")
                }
            }
        )
    }

    // Export Complete Modal
    if (exportedFilePath != null) {
        val file = File(exportedFilePath!!)
        AlertDialog(
            onDismissRequest = { viewModel.dismissExportDialog() },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = StemUserTake)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Master Video Exported")
                }
            },
            text = {
                Column {
                    Text(
                        text = "Your dubbed video has been muxed with AAC audio into standard MP4 container.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "File: ${file.name} (${file.length() / (1024 * 1024)} MB)",
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                        color = StudioAccent
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                        val intent = Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(uri, "video/mp4")
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(intent, "Play Exported Video"))
                    }
                ) {
                    Text("Play Video")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissExportDialog() }) {
                    Text("Close")
                }
            }
        )
    }
}

@Composable
fun AuditionChip(
    label: String,
    selected: Boolean,
    activeColor: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .background(
                if (selected) activeColor.copy(alpha = 0.2f) else StudioElevated,
                RoundedCornerShape(6.dp)
            )
            .border(
                1.dp,
                if (selected) activeColor else StudioBorder,
                RoundedCornerShape(6.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) activeColor else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun StageTimingNudge(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .background(StudioCanvas, RoundedCornerShape(4.dp))
            .border(1.dp, StudioBorder, RoundedCornerShape(4.dp))
            .clickable { onClick() }
            .padding(horizontal = 6.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace, fontSize = 9.sp),
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
