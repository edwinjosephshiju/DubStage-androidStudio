package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.ui.DubStageViewModel
import com.example.ui.theme.StemBacking
import com.example.ui.theme.StemUserTake
import com.example.ui.theme.StemVocal
import com.example.ui.theme.StudioAccent
import com.example.ui.theme.StudioBorder
import com.example.ui.theme.StudioCanvas
import com.example.ui.theme.StudioContainer
import com.example.ui.theme.StudioElevated

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StudioSettingsModal(
    viewModel: DubStageViewModel,
    onDismissRequest: () -> Unit,
    onSideloadClick: () -> Unit
) {
    val modelStatus by viewModel.modelManager.status.collectAsStateWithLifecycle()
    val activePreset by viewModel.modelManager.activePresetFlow.collectAsStateWithLifecycle()
    val activeBackend by viewModel.modelManager.activeBackendFlow.collectAsStateWithLifecycle()
    val activeKaraokeMode by viewModel.modelManager.activeKaraokeModeFlow.collectAsStateWithLifecycle()
    val persistBackendDefault by viewModel.modelManager.persistBackendDefault.collectAsStateWithLifecycle()
    val persistAlgorithmDefault by viewModel.modelManager.persistAlgorithmDefault.collectAsStateWithLifecycle()
    val vadSensitivity by viewModel.vadSensitivity.collectAsStateWithLifecycle()

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(StudioContainer)
                .border(1.dp, StudioBorder, RoundedCornerShape(20.dp))
                .testTag("settings_modal_dialog")
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Header Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(StudioElevated)
                        .border(1.dp, StudioBorder, RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .background(StudioAccent.copy(alpha = 0.2f), CircleShape)
                                .border(1.dp, StudioAccent, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = null,
                                tint = StudioAccent,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "STUDIO SETTINGS & ENGINE CONFIG",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.8.sp
                                ),
                                color = Color.White
                            )
                            Text(
                                text = "Demucs v4 local AI runtime, default configs, and credits",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismissRequest,
                        modifier = Modifier
                            .size(32.dp)
                            .background(StudioCanvas, CircleShape)
                            .border(1.dp, StudioBorder, CircleShape)
                            .testTag("close_settings_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                // Scrollable Content
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // 1. DEMUCS v4 ENGINE CONFIG section
                    ModelDownloaderCard(
                        status = modelStatus,
                        activePreset = activePreset,
                        activeBackend = activeBackend,
                        activeKaraokeMode = activeKaraokeMode,
                        persistBackendDefault = persistBackendDefault,
                        persistAlgorithmDefault = persistAlgorithmDefault,
                        onPersistBackendDefaultChange = { viewModel.modelManager.setPersistBackendDefault(it) },
                        onPersistAlgorithmDefaultChange = { viewModel.modelManager.setPersistAlgorithmDefault(it) },
                        onSelectPreset = { viewModel.setModelPreset(it) },
                        onSelectBackend = { viewModel.setExecutionBackend(it) },
                        onSelectKaraokeMode = { viewModel.setKaraokeMode(it) },
                        onDownloadClick = { viewModel.downloadModel() },
                        onSideloadClick = onSideloadClick,
                        onRetryClick = { viewModel.downloadModel() }
                    )

                    // 2. Audio & VAD Calibration
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = StudioElevated),
                        border = androidx.compose.foundation.BorderStroke(1.dp, StudioBorder)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Tune,
                                    contentDescription = null,
                                    tint = StemUserTake,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "VAD DETECTION & STUDIO CALIBRATION",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = Color.White
                                )
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "VAD Sensitivity (Voice Activity Detection)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "%.2f".format(vadSensitivity),
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = StemUserTake
                                )
                            }
                            Slider(
                                value = vadSensitivity,
                                onValueChange = { viewModel.setVadSensitivity(it) },
                                valueRange = 0.1f..0.9f,
                                colors = SliderDefaults.colors(
                                    thumbColor = StemUserTake,
                                    activeTrackColor = StemUserTake,
                                    inactiveTrackColor = StudioBorder
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                            Text(
                                text = "Higher sensitivity detects whisper and breath pauses. Two-speaker acoustic pitch detection separates dialogue turns into distinct scene clips.",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // 3. ABOUT SECTION
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = StudioElevated),
                        border = androidx.compose.foundation.BorderStroke(1.dp, StudioBorder)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_github),
                                        contentDescription = "GitHub",
                                        tint = Color.White,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = "ABOUT DUBSTAGE",
                                            style = MaterialTheme.typography.titleMedium.copy(
                                                fontWeight = FontWeight.Bold,
                                                letterSpacing = 1.sp
                                            ),
                                            color = Color.White
                                        )
                                        Text(
                                            text = "Professional On-Device Dubbing Studio",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = StudioAccent
                                        )
                                    }
                                }

                                Box(
                                    modifier = Modifier
                                        .background(StudioCanvas, RoundedCornerShape(12.dp))
                                        .border(1.dp, StudioBorder, RoundedCornerShape(12.dp))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = "v1.4.0 Studio",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 10.sp
                                        ),
                                        color = StemVocal
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            // Developers Brief
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(StudioCanvas, RoundedCornerShape(8.dp))
                                    .border(1.dp, StudioBorder, RoundedCornerShape(8.dp))
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = null,
                                    tint = StemUserTake,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = "Edwin Joseph Shiju",
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = FontWeight.Bold
                                        ),
                                        color = Color.White
                                    )
                                    Text(
                                        text = "Lead Developer & Audio Engine Architect",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(StudioCanvas, RoundedCornerShape(8.dp))
                                    .border(1.dp, StudioBorder, RoundedCornerShape(8.dp))
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Code,
                                    contentDescription = null,
                                    tint = StudioAccent,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = "Google AI Studio",
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = FontWeight.Bold
                                        ),
                                        color = Color.White
                                    )
                                    Text(
                                        text = "Development Platform & AI Coding Engine",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            // About description
                            Text(
                                text = "DubStage is a native Android workstation for dialogue dubbing, automated voice activity detection (VAD), and Demucs v4 neural stem separation. It provides line-by-line vocal recording with automatic stop on vocal completion, sample-accurate dual waveform time alignment, drag-and-drop cue reordering, and lossless MP4 remuxing with original vocal fallback.",
                                style = MaterialTheme.typography.bodySmall.copy(lineHeight = 18.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Spacer(modifier = Modifier.height(14.dp))

                            // Feature Badges
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                TechBadge("Demucs v4 ONNX", StemVocal)
                                TechBadge("Low-RAM CPU / QNN", StemUserTake)
                                TechBadge("Residual Phase Audio", StemBacking)
                                TechBadge("2-Speaker VAD Turn DSP", StudioAccent)
                                TechBadge("Room SQLite Persistence", Color.White)
                                TechBadge("AAC / H.264 Master Export", Color(0xFF64B5F6))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TechBadge(text: String, color: Color) {
    Box(
        modifier = Modifier
            .background(color.copy(alpha = 0.12f), RoundedCornerShape(6.dp))
            .border(1.dp, color.copy(alpha = 0.35f), RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            color = color
        )
    }
}
