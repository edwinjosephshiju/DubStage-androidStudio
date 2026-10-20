package com.example.engine

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtException
import ai.onnxruntime.OrtSession
import ai.onnxruntime.TensorInfo
import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.max

data class SeparationResult(
    val vocalsWavFile: File,
    val backingWavFile: File,
    val vocalPeaks: FloatArray,
    val backingPeaks: FloatArray
)

class DemucsOnnxSeparationEngine(private val context: Context) {

    companion object {
        const val TAG = "DemucsSeparationEngine"
        const val SAMPLE_RATE = 44100
        const val CHANNELS = 2
        // Standard Demucs v4 segment frames: 343980 (~7.80 seconds at 44.1kHz stereo)
        const val DEMUCS_SEGMENT_FRAMES = 343980
        // Standard Demucs 25% overlap for artifact-free crossfades
        const val OVERLAP_FRAMES = 85995
        const val HOP_FRAMES = DEMUCS_SEGMENT_FRAMES - OVERLAP_FRAMES // 257985 frames (~5.85s hop)
    }

    private var ortEnv: OrtEnvironment? = null
    private var ortSession: OrtSession? = null

    fun closeSession() {
        try {
            ortSession?.close()
        } catch (t: Throwable) {
            Log.w(TAG, "Error closing ortSession", t)
        }
        ortSession = null
    }

    private fun configureQnnIfAvailable(
        sessionOptions: OrtSession.SessionOptions,
        backend: ExecutionBackend
    ): Boolean {
        try {
            val addQnnMethod = sessionOptions.javaClass.methods.firstOrNull {
                it.name == "addQnn" && it.parameterTypes.size == 1
            }
            if (addQnnMethod == null) {
                Log.w(TAG, "addQnn method not found on SessionOptions class, using CPU.")
                return false
            }

            when (backend) {
                ExecutionBackend.QNN_NPU -> {
                    sessionOptions.addConfigEntry("ep.context_enable", "1")
                    sessionOptions.addConfigEntry("ep.context_file_path", "${context.cacheDir.absolutePath}/qnn_htp_ctx.onnx")
                    val qnnMap = mapOf(
                        "backend_path" to "libQnnHtp.so",
                        "enable_htp_fp16_precision" to "1",
                        "htp_performance_mode" to "burst",
                        "htp_graph_finalization_optimization_mode" to "1"
                    )
                    addQnnMethod.invoke(sessionOptions, qnnMap)
                    Log.i(TAG, "QNN HTP NPU execution provider enabled (FP16).")
                    return true
                }
                ExecutionBackend.QNN_GPU -> {
                    sessionOptions.addConfigEntry("ep.context_enable", "1")
                    sessionOptions.addConfigEntry("ep.context_file_path", "${context.cacheDir.absolutePath}/qnn_gpu_ctx.onnx")
                    val qnnMap = mapOf(
                        "backend_path" to "libQnnGpu.so"
                    )
                    addQnnMethod.invoke(sessionOptions, qnnMap)
                    Log.i(TAG, "QNN Adreno GPU execution provider enabled.")
                    return true
                }
                else -> return false
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to enable QNN execution provider (${backend.label}): ${t.message}. Falling back to CPU Low-RAM.")
            return false
        }
    }

    private fun getOrCreateSession(
        modelFile: File,
        backend: ExecutionBackend
    ): Pair<OrtEnvironment, OrtSession> {
        if (ortEnv == null) {
            ortEnv = OrtEnvironment.getEnvironment()
        }
        val env = ortEnv!!

        closeSession()
        System.gc()

        val sessionOptions = OrtSession.SessionOptions().apply {
            // OOM Prevention: Disable CPU Arena Allocator to stop 3GB+ RAM spikes on Android
            setCPUArenaAllocator(false)
            setMemoryPatternOptimization(false)
            setOptimizationLevel(OrtSession.SessionOptions.OptLevel.BASIC_OPT)
            setExecutionMode(OrtSession.SessionOptions.ExecutionMode.SEQUENTIAL)
            setIntraOpNumThreads(2)
            setInterOpNumThreads(1)
            addConfigEntry("session.intra_op.allow_spinning", "0")
            addConfigEntry("session.use_device_allocator_for_initializers", "1")
            addConfigEntry("session.disable_prepacking", "1")

            if (backend == ExecutionBackend.QNN_NPU || backend == ExecutionBackend.QNN_GPU) {
                val qnnSuccess = configureQnnIfAvailable(this, backend)
                if (!qnnSuccess) {
                    Log.i(TAG, "Running on CPU (Low-RAM Safe) SessionOptions.")
                }
            } else {
                Log.i(TAG, "Configured CPU (Low-RAM Safe) SessionOptions.")
            }
        }

        Log.i(TAG, "Creating OrtSession from ${modelFile.absolutePath} (${modelFile.length()} bytes)")
        ortSession = env.createSession(modelFile.absolutePath, sessionOptions)
        return Pair(env, ortSession!!)
    }

    suspend fun separateStems(
        modelFile: File,
        inputPcmFile: File,
        outputVocalsWav: File,
        outputBackingWav: File,
        preset: ModelPreset = ModelPreset.HTDEMUCS_FT_VOCALS,
        karaokeMode: KaraokeMode = KaraokeMode.RESIDUAL_SUBTRACTION,
        backend: ExecutionBackend = ExecutionBackend.CPU_LOW_RAM,
        onProgress: (DemucsSeparationProgress) -> Unit
    ): SeparationResult = withContext(Dispatchers.IO) {
        if (!modelFile.exists() || modelFile.length() < 50_000_000L) {
            throw IllegalArgumentException("HTDemucs ONNX model not found or incomplete (<50MB): ${modelFile.absolutePath}")
        }
        if (!inputPcmFile.exists() || inputPcmFile.length() == 0L) {
            throw IllegalArgumentException("Input audio file is missing or empty.")
        }

        val totalBytes = inputPcmFile.length()
        val totalFrames = totalBytes / (CHANNELS * 2L)
        if (totalFrames <= 0) {
            throw IllegalArgumentException("Invalid audio duration: totalFrames is $totalFrames")
        }

        // Calculate chunk boundaries: strictly DEMUCS_SEGMENT_FRAMES (343980) per chunk
        val chunkOffsets = mutableListOf<Long>()
        var offset = 0L
        while (offset < totalFrames) {
            chunkOffsets.add(offset)
            if (offset + DEMUCS_SEGMENT_FRAMES >= totalFrames) break
            offset += HOP_FRAMES
        }
        val totalChunks = max(1, chunkOffsets.size)

        // Initialize ONNX Session with OOM guard and backend options
        val (env, session) = try {
            getOrCreateSession(modelFile, backend)
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to load session on $backend, falling back to CPU_LOW_RAM: ${t.message}")
            getOrCreateSession(modelFile, ExecutionBackend.CPU_LOW_RAM)
        }

        val startTime = System.currentTimeMillis()

        // Intermediate raw PCM files on disk (prevents holding 30M+ floats in RAM)
        val tempVocPcm = File(context.cacheDir, "temp_voc_${System.currentTimeMillis()}.pcm")
        val tempBackPcm = File(context.cacheDir, "temp_back_${System.currentTimeMillis()}.pcm")

        val sourceRaf = RandomAccessFile(inputPcmFile, "r")
        val vocRaf = RandomAccessFile(tempVocPcm, "rw")
        val backRaf = RandomAccessFile(tempBackPcm, "rw")

        vocRaf.setLength(totalBytes)
        backRaf.setLength(totalBytes)

        // Native direct buffer for zero-copy tensor feed: [1, 2, DEMUCS_SEGMENT_FRAMES]
        val directInputByteBuffer = ByteBuffer.allocateDirect(1 * CHANNELS * DEMUCS_SEGMENT_FRAMES * 4)
            .order(ByteOrder.nativeOrder())
        val directInputFloatBuffer = directInputByteBuffer.asFloatBuffer()

        val chunkInputShorts = ShortArray(DEMUCS_SEGMENT_FRAMES * CHANNELS)
        val shortByteBuffer = ByteBuffer.allocate(DEMUCS_SEGMENT_FRAMES * CHANNELS * 2)
            .order(ByteOrder.LITTLE_ENDIAN)

        val inputName = session.inputNames.iterator().next()

        val trailingOverlapVocL = FloatArray(OVERLAP_FRAMES)
        val trailingOverlapVocR = FloatArray(OVERLAP_FRAMES)
        val trailingOverlapKarL = FloatArray(OVERLAP_FRAMES)
        val trailingOverlapKarR = FloatArray(OVERLAP_FRAMES)

        var lastInferenceMs = 0L

        // Envelope collectors (1200 points)
        val peakBucketCount = 1200
        val vocalPeaks = FloatArray(peakBucketCount)
        val backingPeaks = FloatArray(peakBucketCount)

        try {
            for (chunkIdx in 0 until totalChunks) {
                if (!currentCoroutineContext().isActive) {
                    tempVocPcm.delete()
                    tempBackPcm.delete()
                    throw InterruptedException("Separation cancelled by user")
                }

                val frameOffset = chunkOffsets[chunkIdx]
                val framesToRead = (totalFrames - frameOffset).coerceAtMost(DEMUCS_SEGMENT_FRAMES.toLong()).toInt()

                val elapsedSec = (System.currentTimeMillis() - startTime) / 1000L
                val etaSec = if (chunkIdx > 0) {
                    val avg = elapsedSec.toDouble() / chunkIdx.toDouble()
                    ((totalChunks - chunkIdx) * avg).toLong()
                } else 0L

                onProgress(
                    DemucsSeparationProgress(
                        currentChunk = chunkIdx + 1,
                        totalChunks = totalChunks,
                        percent = ((chunkIdx * 100) / totalChunks).coerceIn(0, 99),
                        statusText = "Separating Chunk ${chunkIdx + 1} of $totalChunks",
                        activeBackend = backend.label,
                        lastChunkInferenceMs = lastInferenceMs,
                        elapsedSeconds = elapsedSec,
                        etaSeconds = etaSec
                    )
                )

                // 1. Read real 16-bit interleaved PCM frames directly from disk
                sourceRaf.seek(frameOffset * 4L)
                val bytesToRead = framesToRead * 4
                sourceRaf.readFully(shortByteBuffer.array(), 0, bytesToRead)
                shortByteBuffer.position(0)
                shortByteBuffer.asShortBuffer().get(chunkInputShorts, 0, framesToRead * CHANNELS)

                // 2. Exact Planar Stereo De-interleaving into direct FloatBuffer: Shape [1, 2, DEMUCS_SEGMENT_FRAMES]
                directInputFloatBuffer.clear()
                // Left channel
                for (f in 0 until framesToRead) {
                    directInputFloatBuffer.put(chunkInputShorts[f * 2].toFloat() / 32768.0f)
                }
                for (f in framesToRead until DEMUCS_SEGMENT_FRAMES) {
                    directInputFloatBuffer.put(0.0f)
                }
                // Right channel
                for (f in 0 until framesToRead) {
                    directInputFloatBuffer.put(chunkInputShorts[f * 2 + 1].toFloat() / 32768.0f)
                }
                for (f in framesToRead until DEMUCS_SEGMENT_FRAMES) {
                    directInputFloatBuffer.put(0.0f)
                }
                directInputFloatBuffer.rewind()

                // 3. Inference with immediate Native Tensor deallocation
                val chunkRunStartTime = System.currentTimeMillis()

                OnnxTensor.createTensor(
                    env,
                    directInputFloatBuffer,
                    longArrayOf(1, CHANNELS.toLong(), DEMUCS_SEGMENT_FRAMES.toLong())
                ).use { inputTensor ->
                    val results = session.run(mapOf(inputName to inputTensor))
                    results.use { res ->
                        lastInferenceMs = System.currentTimeMillis() - chunkRunStartTime
                        val outputTensor = res.get(0) as OnnxTensor
                        val outTensorInfo = outputTensor.info as TensorInfo
                        val outputShape = outTensorInfo.shape
                        val outFloatBuffer = outputTensor.floatBuffer

                        val is4Stem = (outputShape.size == 4 && outputShape[1] == 4L) || (outputShape.size == 3 && outputShape[0] == 4L)
                        val is2Stem = (outputShape.size == 4 && outputShape[1] == 2L) || (outputShape.size == 3 && outputShape[0] == 2L)
                        val useNeuralSum = is4Stem && !preset.isVocalsOnlyTrained && (karaokeMode == KaraokeMode.NEURAL_SUM)

                        val isLastChunk = (chunkIdx == totalChunks - 1)
                        val finalizedFramesCount = if (isLastChunk) framesToRead else minOf(HOP_FRAMES, framesToRead)

                        val outVocBytes = ByteBuffer.allocate(finalizedFramesCount * 4).order(ByteOrder.LITTLE_ENDIAN)
                        val outBackBytes = ByteBuffer.allocate(finalizedFramesCount * 4).order(ByteOrder.LITTLE_ENDIAN)

                        for (f in 0 until finalizedFramesCount) {
                            val rawVocL: Float
                            val rawVocR: Float
                            val rawBackL: Float
                            val rawBackR: Float

                            if (is4Stem) {
                                // In Demucs 4-stem: Stem 0=Drums, 1=Bass, 2=Other, 3=Vocals
                                rawVocL = outFloatBuffer.get((3 * 2 + 0) * DEMUCS_SEGMENT_FRAMES + f)
                                rawVocR = outFloatBuffer.get((3 * 2 + 1) * DEMUCS_SEGMENT_FRAMES + f)

                                if (useNeuralSum) {
                                    val drumsL = outFloatBuffer.get((0 * 2 + 0) * DEMUCS_SEGMENT_FRAMES + f)
                                    val drumsR = outFloatBuffer.get((0 * 2 + 1) * DEMUCS_SEGMENT_FRAMES + f)
                                    val bassL = outFloatBuffer.get((1 * 2 + 0) * DEMUCS_SEGMENT_FRAMES + f)
                                    val bassR = outFloatBuffer.get((1 * 2 + 1) * DEMUCS_SEGMENT_FRAMES + f)
                                    val otherL = outFloatBuffer.get((2 * 2 + 0) * DEMUCS_SEGMENT_FRAMES + f)
                                    val otherR = outFloatBuffer.get((2 * 2 + 1) * DEMUCS_SEGMENT_FRAMES + f)
                                    rawBackL = drumsL + bassL + otherL
                                    rawBackR = drumsR + bassR + otherR
                                } else {
                                    rawBackL = 0f
                                    rawBackR = 0f
                                }
                            } else if (is2Stem) {
                                // 2-stem model (Stem 0=Karaoke/Backing, Stem 1=Vocals)
                                rawVocL = outFloatBuffer.get((1 * 2 + 0) * DEMUCS_SEGMENT_FRAMES + f)
                                rawVocR = outFloatBuffer.get((1 * 2 + 1) * DEMUCS_SEGMENT_FRAMES + f)
                                rawBackL = outFloatBuffer.get((0 * 2 + 0) * DEMUCS_SEGMENT_FRAMES + f)
                                rawBackR = outFloatBuffer.get((0 * 2 + 1) * DEMUCS_SEGMENT_FRAMES + f)
                            } else {
                                // 1-stem Vocal Specialist model (Stem 0=Vocals)
                                rawVocL = outFloatBuffer.get(0 * DEMUCS_SEGMENT_FRAMES + f)
                                rawVocR = outFloatBuffer.get(1 * DEMUCS_SEGMENT_FRAMES + f)
                                rawBackL = 0f
                                rawBackR = 0f
                            }

                            // Overlap-Add Linear Crossfade for Vocals (and Neural Sum if selected)
                            val finalVocL: Float
                            val finalVocR: Float
                            val finalBackL: Float
                            val finalBackR: Float

                            if (chunkIdx > 0 && f < OVERLAP_FRAMES) {
                                val fadeWeight = f.toFloat() / OVERLAP_FRAMES.toFloat()
                                finalVocL = (trailingOverlapVocL[f] + rawVocL * fadeWeight).coerceIn(-1f, 1f)
                                finalVocR = (trailingOverlapVocR[f] + rawVocR * fadeWeight).coerceIn(-1f, 1f)

                                if (useNeuralSum) {
                                    finalBackL = (trailingOverlapKarL[f] + rawBackL * fadeWeight).coerceIn(-1f, 1f)
                                    finalBackR = (trailingOverlapKarR[f] + rawBackR * fadeWeight).coerceIn(-1f, 1f)
                                } else {
                                    // Phase-perfect residual method: Original Decoded PCM minus Finalized Vocal
                                    val origL = chunkInputShorts[f * 2].toFloat() / 32768.0f
                                    val origR = chunkInputShorts[f * 2 + 1].toFloat() / 32768.0f
                                    finalBackL = (origL - finalVocL).coerceIn(-1f, 1f)
                                    finalBackR = (origR - finalVocR).coerceIn(-1f, 1f)
                                }
                            } else {
                                finalVocL = rawVocL.coerceIn(-1f, 1f)
                                finalVocR = rawVocR.coerceIn(-1f, 1f)

                                if (useNeuralSum) {
                                    finalBackL = rawBackL.coerceIn(-1f, 1f)
                                    finalBackR = rawBackR.coerceIn(-1f, 1f)
                                } else {
                                    // Phase-perfect residual method: Original Decoded PCM minus Finalized Vocal
                                    val origL = chunkInputShorts[f * 2].toFloat() / 32768.0f
                                    val origR = chunkInputShorts[f * 2 + 1].toFloat() / 32768.0f
                                    finalBackL = (origL - finalVocL).coerceIn(-1f, 1f)
                                    finalBackR = (origR - finalVocR).coerceIn(-1f, 1f)
                                }
                            }

                            // Write 16-bit PCM little-endian
                            val vShortL = (finalVocL * 32767f).toInt().coerceIn(-32768, 32767).toShort()
                            val vShortR = (finalVocR * 32767f).toInt().coerceIn(-32768, 32767).toShort()
                            val bShortL = (finalBackL * 32767f).toInt().coerceIn(-32768, 32767).toShort()
                            val bShortR = (finalBackR * 32767f).toInt().coerceIn(-32768, 32767).toShort()

                            outVocBytes.putShort(vShortL)
                            outVocBytes.putShort(vShortR)
                            outBackBytes.putShort(bShortL)
                            outBackBytes.putShort(bShortR)

                            // True Peak Envelope Collection
                            val globalFrame = frameOffset + f
                            val peakIdx = ((globalFrame * peakBucketCount) / totalFrames).toInt().coerceIn(0, peakBucketCount - 1)
                            vocalPeaks[peakIdx] = max(vocalPeaks[peakIdx], abs(finalVocL))
                            backingPeaks[peakIdx] = max(backingPeaks[peakIdx], abs(finalBackL))
                        }

                        // Store trailing overlap region with fade-out weight for next chunk crossfade
                        if (!isLastChunk && framesToRead > HOP_FRAMES) {
                            val overlapEnd = minOf(DEMUCS_SEGMENT_FRAMES, framesToRead)
                            for (f in HOP_FRAMES until overlapEnd) {
                                val relIdx = f - HOP_FRAMES
                                if (relIdx < OVERLAP_FRAMES) {
                                    val fadeOutWeight = 1.0f - (relIdx.toFloat() / OVERLAP_FRAMES.toFloat())
                                    val vocL = outFloatBuffer.get((if (is4Stem) 3 * 2 + 0 else if (is2Stem) 1 * 2 + 0 else 0) * DEMUCS_SEGMENT_FRAMES + f)
                                    val vocR = outFloatBuffer.get((if (is4Stem) 3 * 2 + 1 else if (is2Stem) 1 * 2 + 1 else 1) * DEMUCS_SEGMENT_FRAMES + f)
                                    trailingOverlapVocL[relIdx] = vocL * fadeOutWeight
                                    trailingOverlapVocR[relIdx] = vocR * fadeOutWeight

                                    if (useNeuralSum) {
                                        val dL = outFloatBuffer.get((0 * 2 + 0) * DEMUCS_SEGMENT_FRAMES + f)
                                        val dR = outFloatBuffer.get((0 * 2 + 1) * DEMUCS_SEGMENT_FRAMES + f)
                                        val bL = outFloatBuffer.get((1 * 2 + 0) * DEMUCS_SEGMENT_FRAMES + f)
                                        val bR = outFloatBuffer.get((1 * 2 + 1) * DEMUCS_SEGMENT_FRAMES + f)
                                        val oL = outFloatBuffer.get((2 * 2 + 0) * DEMUCS_SEGMENT_FRAMES + f)
                                        val oR = outFloatBuffer.get((2 * 2 + 1) * DEMUCS_SEGMENT_FRAMES + f)
                                        trailingOverlapKarL[relIdx] = (dL + bL + oL) * fadeOutWeight
                                        trailingOverlapKarR[relIdx] = (dR + bR + oR) * fadeOutWeight
                                    }
                                }
                            }
                        }

                        // Write finalized frames directly to disk for both stems
                        vocRaf.seek(frameOffset * 4L)
                        vocRaf.write(outVocBytes.array(), 0, finalizedFramesCount * 4)
                        backRaf.seek(frameOffset * 4L)
                        backRaf.write(outBackBytes.array(), 0, finalizedFramesCount * 4)
                    }
                }
            }
        } finally {
            sourceRaf.close()
            vocRaf.close()
            backRaf.close()
        }

        // Package intermediate PCM files into standard 44.1kHz stereo RIFF WAV files
        onProgress(
            DemucsSeparationProgress(
                currentChunk = totalChunks,
                totalChunks = totalChunks,
                percent = 99,
                statusText = "Packaging WAV stems...",
                activeBackend = backend.label,
                lastChunkInferenceMs = lastInferenceMs,
                elapsedSeconds = (System.currentTimeMillis() - startTime) / 1000L
            )
        )

        WavUtils.writeWavFromPcm(tempVocPcm, outputVocalsWav, SAMPLE_RATE, CHANNELS)
        WavUtils.writeWavFromPcm(tempBackPcm, outputBackingWav, SAMPLE_RATE, CHANNELS)

        tempVocPcm.delete()
        tempBackPcm.delete()

        onProgress(
            DemucsSeparationProgress(
                currentChunk = totalChunks,
                totalChunks = totalChunks,
                percent = 100,
                statusText = "Stem Separation Complete",
                activeBackend = backend.label,
                lastChunkInferenceMs = lastInferenceMs,
                elapsedSeconds = (System.currentTimeMillis() - startTime) / 1000L
            )
        )

        SeparationResult(
            vocalsWavFile = outputVocalsWav,
            backingWavFile = outputBackingWav,
            vocalPeaks = vocalPeaks,
            backingPeaks = backingPeaks
        )
    }
}
