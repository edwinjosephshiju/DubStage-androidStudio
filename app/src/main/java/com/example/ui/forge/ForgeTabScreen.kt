package com.example.ui.forge

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
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.engine.DemucsSeparationProgress
import com.example.engine.ModelStatus
import com.example.ui.DubStageViewModel
import com.example.ui.components.ModelDownloaderCard
import com.example.ui.components.VideoMonitor
import com.example.ui.components.WaveformCanvas
import com.example.ui.components.formatTimecode
import com.example.ui.components.parseTimecode
import kotlin.math.max
import kotlin.math.min
import com.example.ui.theme.StemBacking
import com.example.ui.theme.StemVocal
import com.example.ui.theme.StudioAccent
import com.example.ui.theme.StudioBorder
import com.example.ui.theme.StudioBorderBright
import com.example.ui.theme.StudioCanvas
import com.example.ui.theme.StudioContainer
import com.example.ui.theme.StudioElevated
import com.example.ui.theme.WarningAmber

@Composable
fun ForgeTabScreen(
    viewModel: DubStageViewModel,
    modifier: Modifier = Modifier
) {
    val activeProject by viewModel.activeProject.collectAsStateWithLifecycle()
    val clips by viewModel.clips.collectAsStateWithLifecycle()
    val activeClip by viewModel.activeClip.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val modelStatus by viewModel.modelManager.status.collectAsStateWithLifecycle()
    val activePreset by viewModel.modelManager.activePresetFlow.collectAsStateWithLifecycle()
    val activeBackend by viewModel.modelManager.activeBackendFlow.collectAsStateWithLifecycle()
    val activeKaraokeMode by viewModel.modelManager.activeKaraokeModeFlow.collectAsStateWithLifecycle()
    val isSeparating by viewModel.isSeparating.collectAsStateWithLifecycle()
    val separationProgress by viewModel.separationProgress.collectAsStateWithLifecycle()
    val isExtractingAudio by viewModel.isExtractingAudio.collectAsStateWithLifecycle()
    val extractionPercent by viewModel.extractionPercent.collectAsStateWithLifecycle()
    val vadSensitivity by viewModel.vadSensitivity.collectAsStateWithLifecycle()

    val originalPeaks by viewModel.originalWaveformPeaks.collectAsStateWithLifecycle()
    val vocalPeaks by viewModel.vocalsWaveformPeaks.collectAsStateWithLifecycle()

    // SAF Video Picker
    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            val fileName = uri.lastPathSegment?.substringAfterLast('/') ?: "Scene_01"
            viewModel.ingestVideo(
                videoUri = uri,
                title = fileName.substringBeforeLast('.'),
                durationMs = 0L,
                width = 1920,
                height = 1080
            )
        }
    }

    // SAF Model Sideload Picker
    val modelPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.sideloadModel(uri)
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
        // 1. Ingest Header / File Status
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "DUBFORGE WORKSPACE",
                        style = MaterialTheme.typography.labelSmall,
                        color = StudioAccent
                    )
                    Text(
                        text = activeProject?.title ?: "No Scene Loaded",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Button(
                    onClick = { videoPickerLauncher.launch(arrayOf("video/*")) },
                    colors = ButtonDefaults.buttonColors(containerColor = StudioElevated),
                    border = androidx.compose.foundation.BorderStroke(1.dp, StudioBorder),
                    modifier = Modifier.testTag("ingest_video_button")
                ) {
                    Icon(Icons.Default.Movie, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (activeProject == null) "Load Video" else "Change Video")
                }
            }
        }

        // 2. Video Monitor with Real ExoPlayer (Zero-Flicker)
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

        // 3. Audio Extraction In-Progress Indicator
        if (isExtractingAudio) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = StudioContainer),
                    border = androidx.compose.foundation.BorderStroke(1.dp, StudioBorder)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = StudioAccent,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Demuxing Video Audio to 44.1kHz Stereo PCM (MediaCodec)... $extractionPercent%",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { extractionPercent / 100f },
                            modifier = Modifier.fillMaxWidth().height(4.dp),
                            color = StudioAccent,
                            trackColor = StudioBorder
                        )
                    }
                }
            }
        }

        // 4. Demucs v4 ONNX Model Card
        item {
            ModelDownloaderCard(
                status = modelStatus,
                activePreset = activePreset,
                activeBackend = activeBackend,
                activeKaraokeMode = activeKaraokeMode,
                onSelectPreset = { viewModel.setModelPreset(it) },
                onSelectBackend = { viewModel.setExecutionBackend(it) },
                onSelectKaraokeMode = { viewModel.setKaraokeMode(it) },
                onDownloadClick = { viewModel.downloadModel() },
                onSideloadClick = { modelPickerLauncher.launch(arrayOf("*/*")) },
                onRetryClick = { viewModel.downloadModel() }
            )
        }

        // 5. Demucs Separation Control & Progress
        if (activeProject != null) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = StudioContainer),
                    border = androidx.compose.foundation.BorderStroke(1.dp, StudioBorder)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "AI STEM SEPARATION (HTDemucs v4)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = StemBacking
                                )
                                Text(
                                    text = if (vocalPeaks.isNotEmpty()) "Vocals & Backing Stems Isolated" else "Demucs v4 Stem Extractor",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            if (vocalPeaks.isEmpty()) {
                                Button(
                                    onClick = { viewModel.startDemucsSeparation() },
                                    enabled = !isSeparating && modelStatus is ModelStatus.Ready,
                                    colors = ButtonDefaults.buttonColors(containerColor = StemBacking),
                                    modifier = Modifier.testTag("run_separation_button")
                                ) {
                                    Icon(Icons.Default.GraphicEq, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(if (isSeparating) "Separating..." else "Run Separation")
                                }
                            } else {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = StemVocal, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Stems Ready", style = MaterialTheme.typography.labelSmall, color = StemVocal)
                                }
                            }
                        }

                        if (isSeparating && separationProgress != null) {
                            val sp = separationProgress!!
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "Processing Chunk [${sp.currentChunk}/${sp.totalChunks}] — ${sp.percent}% • ${sp.activeBackend} • Elapsed: ${sp.elapsedSeconds}s • ETA: ${sp.etaSeconds}s" +
                                        if (sp.lastChunkInferenceMs > 0) " (${sp.lastChunkInferenceMs}ms/chunk)" else "",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            LinearProgressIndicator(
                                progress = { sp.percent / 100f },
                                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                                color = StemBacking,
                                trackColor = StudioBorder
                            )
                        }
                    }
                }
            }
        }

        // 6. Waveform Timeline & Cut Slicer
        if (originalPeaks.isNotEmpty() || vocalPeaks.isNotEmpty()) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(StudioContainer, RoundedCornerShape(10.dp))
                        .border(1.dp, StudioBorder, RoundedCornerShape(10.dp))
                        .padding(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (vocalPeaks.isNotEmpty()) "ISOLATED VOCALS WAVEFORM (SLICED CUES)" else "MASTER AUDIO TRACK WAVEFORM",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (vocalPeaks.isNotEmpty()) StemVocal else StudioAccent
                        )
                        Text(
                            text = "${clips.size} Dialogue Cues",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    WaveformCanvas(
                        peaks = if (vocalPeaks.isNotEmpty()) vocalPeaks else originalPeaks,
                        durationMs = activeProject?.videoDurationMs ?: 0L,
                        playheadFlow = viewModel.playheadMs,
                        clips = clips,
                        activeClipId = activeClip?.id,
                        waveformColor = if (vocalPeaks.isNotEmpty()) StemVocal else StudioAccent,
                        onSeek = { viewModel.seekTo(it) },
                        onClipSelected = { viewModel.selectClip(it) },
                        onClipBoundaryDragged = { id, s, e ->
                            viewModel.updateClipTiming(id, s, e)
                        }
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Slicing Action Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { viewModel.runAutoVad() },
                            colors = ButtonDefaults.buttonColors(containerColor = StudioElevated),
                            border = androidx.compose.foundation.BorderStroke(1.dp, StudioBorder),
                            modifier = Modifier.weight(1f).testTag("auto_vad_button")
                        ) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp), tint = StudioAccent)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Auto-Detect (VAD)")
                        }

                        val canSlice = activeClip != null
                        Button(
                            onClick = { viewModel.sliceClipAtPlayhead() },
                            enabled = canSlice,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (canSlice) StudioElevated else StudioContainer.copy(alpha = 0.5f),
                                contentColor = if (canSlice) Color.White else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                disabledContainerColor = StudioContainer.copy(alpha = 0.4f),
                                disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                            ),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (canSlice) StudioAccent else StudioBorder.copy(alpha = 0.3f)
                            ),
                            modifier = Modifier.weight(1f).testTag("slice_cue_button")
                        ) {
                            Icon(
                                Icons.Default.ContentCut,
                                contentDescription = "Slice at Playhead",
                                modifier = Modifier.size(16.dp),
                                tint = if (canSlice) StudioAccent else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (canSlice) "Slice at Playhead" else "Select Clip to Slice",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
                            )
                        }
                    }

                    // VAD Sensitivity Slider
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "VAD Sensitivity: %.0f%%".format(vadSensitivity * 100f),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Slider(
                        value = vadSensitivity,
                        onValueChange = { viewModel.setVadSensitivity(it) },
                        valueRange = 0.1f..1.0f,
                        colors = SliderDefaults.colors(
                            thumbColor = StudioAccent,
                            activeTrackColor = StudioAccent,
                            inactiveTrackColor = StudioBorder
                        ),
                        modifier = Modifier.height(24.dp)
                    )
                }
            }
        }

        // 7. Dialogue Cues List & Script Editor
        item {
            Text(
                text = "DIALOGUE SCRIPT & CUE EDITOR",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        if (clips.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(StudioContainer, RoundedCornerShape(10.dp))
                        .border(1.dp, StudioBorder, RoundedCornerShape(10.dp))
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No dialogue cues sliced yet. Tap [Auto-Detect (VAD)] or [Slice at Playhead] to create line cues for voice dubbing.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            itemsIndexed(clips, key = { _, c -> c.id }) { index, clip ->
                val isActive = clip.id == activeClip?.id
                var speakerInput by remember(clip.id) { mutableStateOf(clip.speakerName) }
                var scriptInput by remember(clip.id) { mutableStateOf(clip.scriptText) }
                var isEditingTiming by remember(clip.id) { mutableStateOf(false) }
                var startTimeText by remember(clip.id, clip.startMs) { mutableStateOf(formatTimecode(clip.startMs)) }
                var endTimeText by remember(clip.id, clip.endMs) { mutableStateOf(formatTimecode(clip.endMs)) }
                var dragAccumulator by remember(clip.id) { mutableFloatStateOf(0f) }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.selectClip(clip.id) }
                        .testTag("clip_item_${clip.clipIndex}"),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isActive) StudioElevated else StudioContainer
                    ),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (isActive) StudioAccent else StudioBorder
                    )
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                // Drag & Reorder Controls
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(end = 4.dp)
                                ) {
                                    // Touch drag handle: drag vertically to swap adjacent clips
                                    Box(
                                        modifier = Modifier
                                            .size(24.dp)
                                            .pointerInput(clip.id) {
                                                detectVerticalDragGestures(
                                                    onDragStart = { dragAccumulator = 0f },
                                                    onDragEnd = { dragAccumulator = 0f },
                                                    onDragCancel = { dragAccumulator = 0f },
                                                    onVerticalDrag = { _, amount ->
                                                        dragAccumulator += amount
                                                        if (dragAccumulator < -32f) {
                                                            dragAccumulator = 0f
                                                            viewModel.moveClip(clip.id, -1)
                                                        } else if (dragAccumulator > 32f) {
                                                            dragAccumulator = 0f
                                                            viewModel.moveClip(clip.id, +1)
                                                        }
                                                    }
                                                )
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.DragHandle,
                                            contentDescription = "Drag to Reorder",
                                            tint = if (isActive) StudioAccent else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }

                                    // Quick Move Up button
                                    IconButton(
                                        onClick = { viewModel.moveClip(clip.id, -1) },
                                        enabled = index > 0,
                                        modifier = Modifier.size(20.dp).testTag("move_up_clip_${clip.clipIndex}")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.KeyboardArrowUp,
                                            contentDescription = "Move Up",
                                            tint = if (index > 0) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }

                                    // Quick Move Down button
                                    IconButton(
                                        onClick = { viewModel.moveClip(clip.id, +1) },
                                        enabled = index < clips.size - 1,
                                        modifier = Modifier.size(20.dp).testTag("move_down_clip_${clip.clipIndex}")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.KeyboardArrowDown,
                                            contentDescription = "Move Down",
                                            tint = if (index < clips.size - 1) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }

                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .background(if (isActive) StudioAccent else StudioBorder, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "${clip.clipIndex}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White
                                    )
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    onClick = {
                                        isEditingTiming = !isEditingTiming
                                        if (isEditingTiming) {
                                            startTimeText = formatTimecode(clip.startMs)
                                            endTimeText = formatTimecode(clip.endMs)
                                        }
                                    },
                                    shape = RoundedCornerShape(4.dp),
                                    color = if (isEditingTiming) StudioAccent.copy(alpha = 0.2f) else StudioElevated,
                                    border = androidx.compose.foundation.BorderStroke(1.dp, if (isEditingTiming) StudioAccent else StudioBorder),
                                    modifier = Modifier.testTag("clip_timing_${clip.clipIndex}")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "${formatTimecode(clip.startMs)} → ${formatTimecode(clip.endMs)}",
                                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold),
                                            color = if (isEditingTiming) StudioAccent else MaterialTheme.colorScheme.onSurface
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Icon(
                                            imageVector = Icons.Default.Edit,
                                            contentDescription = "Edit Span Length",
                                            tint = if (isEditingTiming) StudioAccent else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(12.dp)
                                        )
                                    }
                                }
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                val isThisClipPlaying = isPlaying && (activeClip?.id == clip.id)
                                Button(
                                    onClick = { viewModel.playClipVad(clip) },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isThisClipPlaying) StudioAccent else StudioElevated
                                    ),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, StudioAccent),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                    modifier = Modifier.height(28.dp).testTag("play_clip_${clip.clipIndex}_button")
                                ) {
                                    Icon(
                                        imageVector = if (isThisClipPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                        contentDescription = if (isThisClipPlaying) "Pause Span" else "Play Span",
                                        tint = if (isThisClipPlaying) Color.Black else StudioAccent,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (isThisClipPlaying) "Pause" else "Play Span",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 11.sp),
                                        color = if (isThisClipPlaying) Color.Black else StudioAccent
                                    )
                                }

                                Spacer(modifier = Modifier.width(4.dp))

                                IconButton(
                                    onClick = { viewModel.deleteClip(clip) },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Delete Cut",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }

                        // Expandable Point-Precision Timing Editor (e.g. 00:20.50)
                        AnimatedVisibility(visible = isEditingTiming) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp)
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
                                        text = "EDIT SPAN LENGTH (POINT PRECISION: mm:ss.SS)",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 10.sp),
                                        color = StudioAccent
                                    )
                                    Text(
                                        text = "Span: %.2fs".format((clip.endMs - clip.startMs) / 1000f),
                                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    // Start Time Point Precision Input
                                    Column(modifier = Modifier.weight(1f)) {
                                        OutlinedTextField(
                                            value = startTimeText,
                                            onValueChange = { input ->
                                                startTimeText = input
                                                val parsed = parseTimecode(input)
                                                if (parsed != null && parsed < clip.endMs) {
                                                    viewModel.updateClipTiming(clip.id, parsed, clip.endMs)
                                                }
                                            },
                                            label = { Text("Start (e.g. 00:10.00)", style = MaterialTheme.typography.labelSmall) },
                                            singleLine = true,
                                            textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedBorderColor = StudioAccent,
                                                unfocusedBorderColor = StudioBorder
                                            ),
                                            modifier = Modifier.fillMaxWidth().testTag("clip_start_input_${clip.clipIndex}")
                                        )

                                        Spacer(modifier = Modifier.height(4.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            TimingNudgeButton("-1s") {
                                                viewModel.nudgeClipTiming(clip.id, -1000L, 0L)
                                                startTimeText = formatTimecode(max(0L, clip.startMs - 1000L))
                                            }
                                            TimingNudgeButton("-0.1s") {
                                                viewModel.nudgeClipTiming(clip.id, -100L, 0L)
                                                startTimeText = formatTimecode(max(0L, clip.startMs - 100L))
                                            }
                                            TimingNudgeButton("+0.1s") {
                                                if (clip.startMs + 100L < clip.endMs) {
                                                    viewModel.nudgeClipTiming(clip.id, 100L, 0L)
                                                    startTimeText = formatTimecode(clip.startMs + 100L)
                                                }
                                            }
                                            TimingNudgeButton("+1s") {
                                                if (clip.startMs + 1000L < clip.endMs) {
                                                    viewModel.nudgeClipTiming(clip.id, 1000L, 0L)
                                                    startTimeText = formatTimecode(clip.startMs + 1000L)
                                                }
                                            }
                                        }
                                    }

                                    // End Time Point Precision Input (e.g. 00:20.50)
                                    Column(modifier = Modifier.weight(1f)) {
                                        OutlinedTextField(
                                            value = endTimeText,
                                            onValueChange = { input ->
                                                endTimeText = input
                                                val parsed = parseTimecode(input)
                                                if (parsed != null && parsed > clip.startMs) {
                                                    viewModel.updateClipTiming(clip.id, clip.startMs, parsed)
                                                }
                                            },
                                            label = { Text("End (e.g. 00:20.50)", style = MaterialTheme.typography.labelSmall) },
                                            singleLine = true,
                                            textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedBorderColor = StudioAccent,
                                                unfocusedBorderColor = StudioBorder
                                            ),
                                            modifier = Modifier.fillMaxWidth().testTag("clip_end_input_${clip.clipIndex}")
                                        )

                                        Spacer(modifier = Modifier.height(4.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            TimingNudgeButton("-1s") {
                                                if (clip.endMs - 1000L > clip.startMs) {
                                                    viewModel.nudgeClipTiming(clip.id, 0L, -1000L)
                                                    endTimeText = formatTimecode(clip.endMs - 1000L)
                                                }
                                            }
                                            TimingNudgeButton("-0.1s") {
                                                if (clip.endMs - 100L > clip.startMs) {
                                                    viewModel.nudgeClipTiming(clip.id, 0L, -100L)
                                                    endTimeText = formatTimecode(clip.endMs - 100L)
                                                }
                                            }
                                            TimingNudgeButton("+0.1s") {
                                                viewModel.nudgeClipTiming(clip.id, 0L, 100L)
                                                endTimeText = formatTimecode(clip.endMs + 100L)
                                            }
                                            TimingNudgeButton("+1s") {
                                                viewModel.nudgeClipTiming(clip.id, 0L, 1000L)
                                                endTimeText = formatTimecode(clip.endMs + 1000L)
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
                                                if (current < clip.endMs) {
                                                    viewModel.updateClipTiming(clip.id, current, clip.endMs)
                                                    startTimeText = formatTimecode(current)
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
                                                if (current > clip.startMs) {
                                                    viewModel.updateClipTiming(clip.id, clip.startMs, current)
                                                    endTimeText = formatTimecode(current)
                                                }
                                            },
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                            modifier = Modifier.height(28.dp)
                                        ) {
                                            Text("End = Playhead", style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp))
                                        }
                                    }

                                    Button(
                                        onClick = { isEditingTiming = false },
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

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = speakerInput,
                                onValueChange = {
                                    speakerInput = it
                                    viewModel.updateClip(clip.copy(speakerName = it))
                                },
                                label = { Text("Speaker", style = MaterialTheme.typography.labelSmall) },
                                modifier = Modifier.width(130.dp),
                                textStyle = MaterialTheme.typography.bodyMedium,
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = StudioAccent,
                                    unfocusedBorderColor = StudioBorder
                                )
                            )

                            OutlinedTextField(
                                value = scriptInput,
                                onValueChange = {
                                    scriptInput = it
                                    viewModel.updateClip(clip.copy(scriptText = it))
                                },
                                label = { Text("Dialogue / Teleprompter Script", style = MaterialTheme.typography.labelSmall) },
                                modifier = Modifier.weight(1f),
                                textStyle = MaterialTheme.typography.bodyMedium,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = StudioAccent,
                                    unfocusedBorderColor = StudioBorder
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TimingNudgeButton(label: String, onClick: () -> Unit) {
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
