package com.example.engine

import java.io.File

enum class ModelPreset(
    val id: String,
    val displayName: String,
    val fileName: String,
    val downloadUrl: String,
    val description: String,
    val isVocalsOnlyTrained: Boolean
) {
    HTDEMUCS_FT_VOCALS(
        id = "htdemucs_ft_vocals",
        displayName = "HT-Demucs FT Vocals Specialist (166 MB)",
        fileName = "htdemucs_ft_vocals_fp16weights.onnx",
        downloadUrl = "https://huggingface.co/StemSplitio/htdemucs-ft-vocals-onnx/resolve/main/htdemucs_ft_vocals_fp16weights.onnx",
        description = "Fine-tuned vocal extraction specialist. Uses phase-perfect residual (Original - Vocals) for pristine backing.",
        isVocalsOnlyTrained = true
    ),
    HTDEMUCS_4STEM(
        id = "htdemucs_4stem",
        displayName = "HT-Demucs 4-Stem All-Trained (166 MB)",
        fileName = "htdemucs_fp16weights.onnx",
        downloadUrl = "https://huggingface.co/StemSplitio/htdemucs-onnx/resolve/main/htdemucs_fp16weights.onnx",
        description = "Trained on all 4 stems (Drums, Bass, Other, Vocals). Supports both Residual and Neural Sum backing.",
        isVocalsOnlyTrained = false
    )
}

enum class ExecutionBackend(
    val id: String,
    val label: String,
    val subtitle: String
) {
    CPU_LOW_RAM(
        id = "cpu",
        label = "CPU (Low-RAM)",
        subtitle = "Low-memory sequential (no BFCArena OOM)"
    ),
    QNN_NPU(
        id = "qnn_npu",
        label = "QNN NPU (HTP)",
        subtitle = "Snapdragon Hexagon NPU FP16 (with CPU fallback)"
    ),
    QNN_GPU(
        id = "qnn_gpu",
        label = "QNN GPU (Adreno)",
        subtitle = "Qualcomm Adreno GPU (with CPU fallback)"
    )
}

enum class KaraokeMode(
    val id: String,
    val label: String,
    val description: String
) {
    RESIDUAL_SUBTRACTION(
        id = "residual",
        label = "Residual Backing (Original - Vocals)",
        description = "Phase-perfect cancellation. Zero noise/drone. Mandatory for Vocals Specialist model."
    ),
    NEURAL_SUM(
        id = "neural_sum",
        label = "Neural Sum (Drums + Bass + Other)",
        description = "Sums non-vocal stems. Only available for 4-Stem models."
    )
}

data class DemucsSeparationProgress(
    val currentChunk: Int = 0,
    val totalChunks: Int = 0,
    val percent: Int = 0,
    val statusText: String = "Ready",
    val activeBackend: String = "CPU (Low-RAM)",
    val lastChunkInferenceMs: Long = 0L,
    val elapsedSeconds: Long = 0L,
    val etaSeconds: Long = 0L,
    val tensorShapeInfo: String = ""
)
