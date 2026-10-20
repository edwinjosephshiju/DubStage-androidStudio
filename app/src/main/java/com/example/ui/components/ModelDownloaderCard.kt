package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.engine.ExecutionBackend
import com.example.engine.KaraokeMode
import com.example.engine.ModelPreset
import com.example.engine.ModelStatus
import com.example.ui.theme.StatusRecording
import com.example.ui.theme.StemBacking
import com.example.ui.theme.StemUserTake
import com.example.ui.theme.StudioAccent
import com.example.ui.theme.StudioBorder
import com.example.ui.theme.StudioContainer
import com.example.ui.theme.StudioElevated
import com.example.ui.theme.WarningAmber

@Composable
fun ModelDownloaderCard(
    status: ModelStatus,
    activePreset: ModelPreset,
    activeBackend: ExecutionBackend,
    activeKaraokeMode: KaraokeMode,
    persistBackendDefault: Boolean = true,
    persistAlgorithmDefault: Boolean = true,
    onPersistBackendDefaultChange: ((Boolean) -> Unit)? = null,
    onPersistAlgorithmDefaultChange: ((Boolean) -> Unit)? = null,
    onSelectPreset: (ModelPreset) -> Unit,
    onSelectBackend: (ExecutionBackend) -> Unit,
    onSelectKaraokeMode: (KaraokeMode) -> Unit,
    onDownloadClick: () -> Unit,
    onSideloadClick: () -> Unit,
    onRetryClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(StudioContainer, RoundedCornerShape(10.dp))
            .border(1.dp, StudioBorder, RoundedCornerShape(10.dp))
            .padding(14.dp)
            .testTag("model_downloader_card")
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Memory,
                    contentDescription = "Demucs Model",
                    tint = StudioAccent,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "DEMUCS v4 ENGINE CONFIG",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            when (status) {
                is ModelStatus.Ready -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(StemUserTake, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "VERIFIED READY",
                            style = MaterialTheme.typography.labelSmall,
                            color = StemUserTake
                        )
                    }
                }
                is ModelStatus.Downloading -> {
                    Text(
                        text = "ACQUIRING...",
                        style = MaterialTheme.typography.labelSmall,
                        color = WarningAmber
                    )
                }
                is ModelStatus.Error -> {
                    Text(
                        text = "INTEGRITY ERROR",
                        style = MaterialTheme.typography.labelSmall,
                        color = StatusRecording
                    )
                }
                ModelStatus.Missing -> {
                    Text(
                        text = "NOT INSTALLED",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Preset Selector (Vocal Specialist vs 4-Stem)
        Text(
            text = "MODEL ARCHITECTURE PRESET",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ModelPreset.values().forEach { preset ->
                val isSelected = preset == activePreset
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(if (isSelected) StudioAccent.copy(alpha = 0.2f) else StudioElevated, RoundedCornerShape(6.dp))
                        .border(1.dp, if (isSelected) StudioAccent else StudioBorder, RoundedCornerShape(6.dp))
                        .clickable { onSelectPreset(preset) }
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Column {
                        Text(
                            text = if (preset.isVocalsOnlyTrained) "Vocals Specialist" else "4-Stem Full Model",
                            style = MaterialTheme.typography.labelSmall.copy(color = if (isSelected) StudioAccent else MaterialTheme.colorScheme.onSurface)
                        )
                        Text(
                            text = if (preset.isVocalsOnlyTrained) "Fine-Tuned FT • Residual" else "Drums, Bass, Other, Vocals",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Hardware Acceleration Backend Selector (CPU Low-RAM, QNN NPU, QNN GPU)
        Text(
            text = "HARDWARE EXECUTION BACKEND (OOM PROTECTED)",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            ExecutionBackend.values().forEach { backend ->
                val isSelected = backend == activeBackend
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(if (isSelected) StemUserTake.copy(alpha = 0.2f) else StudioElevated, RoundedCornerShape(6.dp))
                        .border(1.dp, if (isSelected) StemUserTake else StudioBorder, RoundedCornerShape(6.dp))
                        .clickable { onSelectBackend(backend) }
                        .padding(horizontal = 6.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = backend.label,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = if (isSelected) StemUserTake else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        if (onPersistBackendDefaultChange != null) {
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onPersistBackendDefaultChange(!persistBackendDefault) }
                    .padding(vertical = 2.dp)
            ) {
                Checkbox(
                    checked = persistBackendDefault,
                    onCheckedChange = { onPersistBackendDefaultChange(it) },
                    colors = CheckboxDefaults.colors(
                        checkedColor = StemUserTake,
                        uncheckedColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Set persistent default backend",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Karaoke / Backing Mode Selector
        Text(
            text = "BACKING EXTRACTION ALGORITHM",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            KaraokeMode.values().forEach { mode ->
                val isSelected = mode == activeKaraokeMode
                val isEnabled = !activePreset.isVocalsOnlyTrained || mode == KaraokeMode.RESIDUAL_SUBTRACTION
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(if (isSelected) StemBacking.copy(alpha = 0.2f) else StudioElevated, RoundedCornerShape(6.dp))
                        .border(1.dp, if (isSelected) StemBacking else StudioBorder, RoundedCornerShape(6.dp))
                        .clickable(enabled = isEnabled) { onSelectKaraokeMode(mode) }
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Column {
                        Text(
                            text = if (mode == KaraokeMode.RESIDUAL_SUBTRACTION) "Residual (Orig - Vocals)" else "Neural Stem Sum",
                            style = MaterialTheme.typography.labelSmall.copy(color = if (isSelected) StemBacking else if (isEnabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                        )
                        Text(
                            text = if (mode == KaraokeMode.RESIDUAL_SUBTRACTION) "Phase-perfect zero-drone" else "4-stem trained models only",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        if (onPersistAlgorithmDefaultChange != null) {
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onPersistAlgorithmDefaultChange(!persistAlgorithmDefault) }
                    .padding(vertical = 2.dp)
            ) {
                Checkbox(
                    checked = persistAlgorithmDefault,
                    onCheckedChange = { onPersistAlgorithmDefaultChange(it) },
                    colors = CheckboxDefaults.colors(
                        checkedColor = StemBacking,
                        uncheckedColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Set persistent default extraction algorithm",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Download / Sideload / Status Area
        when (status) {
            is ModelStatus.Ready -> {
                val sizeMb = status.fileSizeBytes / (1024L * 1024L)
                val shaPreview = if (status.sha256Hex.length >= 10) status.sha256Hex.substring(0, 10) + "..." else status.sha256Hex
                Text(
                    text = "Active Model: ${status.preset.displayName} ($sizeMb MB • SHA-256: $shaPreview). Backing mode: ${activeKaraokeMode.label}.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            is ModelStatus.Downloading -> {
                val downloadedMb = status.bytesRead / (1024L * 1024L)
                val totalMb = if (status.totalBytes > 0) status.totalBytes / (1024L * 1024L) else 166L
                val speedMb = (status.speedBytesPerSec / 1024.0 / 1024.0)
                val speedStr = "%.1f".format(java.util.Locale.US, speedMb)

                Column {
                    Text(
                        text = "${status.statusMessage} ($downloadedMb MB / $totalMb MB • $speedStr MB/s)",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { (status.percent / 100f).coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = StudioAccent,
                        trackColor = StudioBorder
                    )
                }
            }

            is ModelStatus.Error -> {
                Column {
                    Text(
                        text = status.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = StatusRecording
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = onRetryClick,
                            colors = ButtonDefaults.buttonColors(containerColor = StudioElevated),
                            modifier = Modifier.testTag("retry_model_button")
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Retry Download")
                        }
                        OutlinedButton(onClick = onSideloadClick) {
                            Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Sideload Local .onnx")
                        }
                    }
                }
            }

            ModelStatus.Missing -> {
                Column {
                    Text(
                        text = "Demucs v4 model is required for on-device voice & accompaniment stem extraction. Download directly from Hugging Face or sideload your local .onnx file.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = onDownloadClick,
                            colors = ButtonDefaults.buttonColors(containerColor = StudioAccent),
                            modifier = Modifier.weight(1f).testTag("download_model_button")
                        ) {
                            Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Download (${if (activePreset.isVocalsOnlyTrained) "166MB" else "166MB"})")
                        }
                        OutlinedButton(
                            onClick = onSideloadClick,
                            modifier = Modifier.weight(1f).testTag("sideload_model_button")
                        ) {
                            Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Sideload .onnx")
                        }
                    }
                }
            }
        }
    }
}
