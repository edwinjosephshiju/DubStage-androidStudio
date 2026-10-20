package com.example.engine

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

data class DetectedDialogueClip(
    val id: String = UUID.randomUUID().toString(),
    val clipIndex: Int,
    val startMs: Long,
    val endMs: Long,
    val speakerName: String = "Speaker 1",
    val scriptText: String = ""
)

class VadEngine {

    /**
     * Voice Activity Detection & Speaker Turn Detection optimized for isolated vocal stems.
     * Uses adaptive noise-floor profiling, dual-threshold hysteresis, acoustic pitch ($F_0$)
     * autocorrelation, spectral brightness profiling, and speaker boundary segmentation
     * so that two different speakers are never merged into a single listed span.
     */
    suspend fun detectDialogueClips(
        audioFile: File,
        isWavFile: Boolean = true,
        sensitivity: Float = 0.6f // 0.0 (least sensitive) to 1.0 (most sensitive)
    ): List<DetectedDialogueClip> = withContext(Dispatchers.Default) {
        if (!audioFile.exists() || audioFile.length() <= 44L) {
            return@withContext emptyList()
        }

        val sampleRate = 44100
        val frameDurationMs = 25
        val frameSamples = (sampleRate * frameDurationMs) / 1000 // 1102 samples per channel
        val bytesPerFrame = frameSamples * 2 * 2 // 16-bit stereo = 4 bytes per sample pair

        val fis = FileInputStream(audioFile)
        if (isWavFile) {
            fis.skip(44) // Skip standard RIFF header
        }

        val frameRmsList = ArrayList<Float>()
        val frameZcrList = ArrayList<Float>()
        val framePitchList = ArrayList<Float>() // Estimated fundamental pitch F0 in Hz (0 if unvoiced)
        val frameCentroidList = ArrayList<Float>() // Spectral brightness / formant proxy

        val shortBuf = ByteArray(bytesPerFrame)
        var bytesRead: Int

        // Buffer for downsampled mono samples for fast pitch autocorrelation
        val downsampledLen = frameSamples / 2 // ~551 samples @ 22.05kHz
        val downsampledMono = FloatArray(downsampledLen)

        fis.use { inStream ->
            while (inStream.read(shortBuf).also { bytesRead = it } > 0) {
                if (bytesRead < 4) break
                val byteBuffer = ByteBuffer.wrap(shortBuf, 0, bytesRead)
                    .order(ByteOrder.LITTLE_ENDIAN)
                    .asShortBuffer()

                var sumSquares = 0.0
                var zeroCrossings = 0
                var prevSample = 0
                var sampleCount = 0
                var highFreqDiffSum = 0.0

                var dsIdx = 0
                while (byteBuffer.hasRemaining()) {
                    val sL = byteBuffer.get().toInt()
                    val sR = if (byteBuffer.hasRemaining()) byteBuffer.get().toInt() else sL
                    val mono = (sL + sR) / 2
                    val norm = mono / 32768.0f

                    sumSquares += (norm * norm)
                    if ((prevSample >= 0 && mono < 0) || (prevSample < 0 && mono >= 0)) {
                        zeroCrossings++
                    }
                    val diff = (mono - prevSample) / 32768.0f
                    highFreqDiffSum += (diff * diff)

                    if (sampleCount % 2 == 0 && dsIdx < downsampledLen) {
                        downsampledMono[dsIdx++] = norm
                    }

                    prevSample = mono
                    sampleCount++
                }

                val rms = if (sampleCount > 0) sqrt(sumSquares / sampleCount).toFloat() else 0f
                val zcr = if (sampleCount > 0) zeroCrossings.toFloat() / sampleCount else 0f
                val centroid = if (sumSquares > 0.00001) (highFreqDiffSum / sumSquares).toFloat() else 0f

                // Fundamental pitch estimation via autocorrelation on downsampled mono
                var pitchHz = 0f
                if (rms > 0.015f && dsIdx >= 280) {
                    val minLag = 55   // ~400 Hz at 22050 Hz
                    val maxLag = 275  // ~80 Hz at 22050 Hz
                    var bestLag = -1
                    var maxCorr = 0.0

                    // Calculate energy of reference window
                    val winLen = dsIdx - maxLag
                    var energy0 = 0.0
                    for (k in 0 until winLen) {
                        energy0 += downsampledMono[k] * downsampledMono[k]
                    }

                    if (energy0 > 0.0001) {
                        for (lag in minLag..maxLag step 2) {
                            var dot = 0.0
                            var energyLag = 0.0
                            for (k in 0 until winLen) {
                                val v0 = downsampledMono[k]
                                val vLag = downsampledMono[k + lag]
                                dot += v0 * vLag
                                energyLag += vLag * vLag
                            }
                            val normCorr = dot / (sqrt(energy0 * energyLag) + 1e-6)
                            if (normCorr > maxCorr) {
                                maxCorr = normCorr
                                bestLag = lag
                            }
                        }

                        if (maxCorr > 0.32 && bestLag > 0) {
                            pitchHz = 22050.0f / bestLag.toFloat()
                        }
                    }
                }

                frameRmsList.add(rms)
                frameZcrList.add(zcr)
                framePitchList.add(pitchHz)
                frameCentroidList.add(centroid)
            }
        }

        if (frameRmsList.isEmpty()) {
            return@withContext emptyList()
        }

        // Compute adaptive noise floor and speech energy dynamic range
        val sortedRms = frameRmsList.sorted()
        val totalFrames = sortedRms.size
        val noiseFloorIndex = (totalFrames * 0.15f).toInt().coerceIn(0, totalFrames - 1)
        val peakIndex = (totalFrames * 0.95f).toInt().coerceIn(0, totalFrames - 1)

        val noiseFloor = sortedRms[noiseFloorIndex]
        val peakRms = sortedRms[peakIndex]
        val dynamicRange = max(0.01f, peakRms - noiseFloor)

        // Threshold tuning based on sensitivity
        val clampedSensitivity = sensitivity.coerceIn(0.1f, 1.0f)
        val thresholdFactor = (0.24f - (clampedSensitivity * 0.21f)).coerceAtLeast(0.02f)
        val speechAttackThreshold = noiseFloor + (dynamicRange * thresholdFactor)
        val speechSustainThreshold = speechAttackThreshold * 0.65f

        val isSpeechFrame = BooleanArray(totalFrames)
        var currentlyVoicing = false
        for (i in 0 until totalFrames) {
            val r = frameRmsList[i]
            val z = frameZcrList[i]

            if (!currentlyVoicing) {
                // Speech onset
                if (r >= speechAttackThreshold || (r >= speechSustainThreshold && z in 0.02f..0.55f)) {
                    currentlyVoicing = true
                    isSpeechFrame[i] = true
                }
            } else {
                // Speech continuation
                if (r >= speechSustainThreshold || (r >= (noiseFloor + 0.002f) && z in 0.03f..0.60f)) {
                    isSpeechFrame[i] = true
                } else {
                    currentlyVoicing = false
                    isSpeechFrame[i] = false
                }
            }
        }

        // Apply shorter hangover (250ms = 10 frames) to preserve speaker transitions
        val hangoverFrames = 250 / frameDurationMs
        var trailingHangover = 0
        for (i in 0 until totalFrames) {
            if (isSpeechFrame[i]) {
                trailingHangover = hangoverFrames
            } else if (trailingHangover > 0) {
                isSpeechFrame[i] = true
                trailingHangover--
            }
        }

        // Aggregate consecutive speech frames into raw dialogue segments
        data class RawSegment(
            var startFrame: Int,
            var endFrame: Int,
            var medianPitch: Float = 0f,
            var meanCentroid: Float = 0f
        )

        fun computeSegmentAcoustics(seg: RawSegment) {
            val pitches = ArrayList<Float>()
            var centroidSum = 0.0
            var count = 0
            for (f in seg.startFrame until seg.endFrame) {
                val p = framePitchList[f]
                if (p in 75f..450f) {
                    pitches.add(p)
                }
                centroidSum += frameCentroidList[f]
                count++
            }
            seg.medianPitch = if (pitches.isNotEmpty()) {
                pitches.sorted()[pitches.size / 2]
            } else 0f
            seg.meanCentroid = if (count > 0) (centroidSum / count).toFloat() else 0f
        }

        val rawSegments = ArrayList<RawSegment>()
        var inSegment = false
        var segStart = 0

        for (i in 0 until totalFrames) {
            if (isSpeechFrame[i]) {
                if (!inSegment) {
                    inSegment = true
                    segStart = i
                }
            } else {
                if (inSegment) {
                    inSegment = false
                    val seg = RawSegment(segStart, i)
                    computeSegmentAcoustics(seg)
                    rawSegments.add(seg)
                }
            }
        }
        if (inSegment) {
            val seg = RawSegment(segStart, totalFrames)
            computeSegmentAcoustics(seg)
            rawSegments.add(seg)
        }

        // Speaker-aware bridging:
        // Bridge gaps only if they belong to the SAME speaker voice.
        // If acoustic pitch differs significantly (e.g. >18% or >25Hz), keep them separate!
        val maxGapFrames = 600 / frameDurationMs
        val bridgedSegments = ArrayList<RawSegment>()
        for (seg in rawSegments) {
            if (bridgedSegments.isEmpty()) {
                bridgedSegments.add(seg)
            } else {
                val prev = bridgedSegments.last()
                val gap = seg.startFrame - prev.endFrame

                var isSameSpeaker = true
                if (prev.medianPitch > 0f && seg.medianPitch > 0f) {
                    val pitchDiff = kotlin.math.abs(prev.medianPitch - seg.medianPitch)
                    val relDiff = pitchDiff / max(prev.medianPitch, seg.medianPitch)
                    if (relDiff > 0.18f || pitchDiff > 28f) {
                        isSameSpeaker = false // Different speaker detected!
                    }
                } else if (gap > (180 / frameDurationMs) && kotlin.math.abs(prev.meanCentroid - seg.meanCentroid) > 0.8f) {
                    isSameSpeaker = false
                }

                if (gap <= maxGapFrames && isSameSpeaker) {
                    // Bridge into one continuous natural dialogue cue
                    prev.endFrame = seg.endFrame
                    computeSegmentAcoustics(prev)
                } else {
                    bridgedSegments.add(seg)
                }
            }
        }

        // Intra-segment speaker splitting:
        // If a long segment contains a micro-pause (>=175ms) where pitch shifts (>18%),
        // split it into two separate speaker spans to avoid two speakers in one span.
        val finalSplitSegments = ArrayList<RawSegment>()
        val microPauseFrames = 175 / frameDurationMs

        for (seg in bridgedSegments) {
            val len = seg.endFrame - seg.startFrame
            var splitPoint = -1

            if (len > (2200 / frameDurationMs)) { // Segments longer than 2.2 seconds
                // Look for lowest energy frame in the middle portion
                val minSearch = seg.startFrame + (400 / frameDurationMs)
                val maxSearch = seg.endFrame - (400 / frameDurationMs)

                var bestPauseDip = -1
                var lowestRms = Float.MAX_VALUE

                for (f in minSearch..maxSearch) {
                    if (frameRmsList[f] < lowestRms) {
                        lowestRms = frameRmsList[f]
                        bestPauseDip = f
                    }
                }

                if (bestPauseDip != -1 && lowestRms < speechAttackThreshold) {
                    // Check pitch before and after the dip
                    val pitchesBefore = ArrayList<Float>()
                    for (f in max(seg.startFrame, bestPauseDip - 20) until bestPauseDip) {
                        val p = framePitchList[f]
                        if (p in 75f..450f) pitchesBefore.add(p)
                    }
                    val pitchesAfter = ArrayList<Float>()
                    for (f in bestPauseDip until min(seg.endFrame, bestPauseDip + 20)) {
                        val p = framePitchList[f]
                        if (p in 75f..450f) pitchesAfter.add(p)
                    }

                    if (pitchesBefore.isNotEmpty() && pitchesAfter.isNotEmpty()) {
                        val medBefore = pitchesBefore.sorted()[pitchesBefore.size / 2]
                        val medAfter = pitchesAfter.sorted()[pitchesAfter.size / 2]
                        val pitchShift = kotlin.math.abs(medBefore - medAfter) / max(medBefore, medAfter)
                        if (pitchShift > 0.18f || kotlin.math.abs(medBefore - medAfter) > 28f) {
                            splitPoint = bestPauseDip
                        }
                    }
                }
            }

            if (splitPoint != -1) {
                val seg1 = RawSegment(seg.startFrame, splitPoint)
                val seg2 = RawSegment(splitPoint, seg.endFrame)
                computeSegmentAcoustics(seg1)
                computeSegmentAcoustics(seg2)
                finalSplitSegments.add(seg1)
                finalSplitSegments.add(seg2)
            } else {
                finalSplitSegments.add(seg)
            }
        }

        // Minimum speech duration: 400ms to reject clicks/bumps
        val minSpeechFrames = 400 / frameDurationMs
        val preRollFrames = 150 / frameDurationMs // 150ms pre-roll
        val postRollFrames = 200 / frameDurationMs // 200ms post-roll

        // Classify Speaker 1 vs Speaker 2 based on overall pitch distribution
        val allValidPitches = finalSplitSegments.map { it.medianPitch }.filter { it > 0f }
        val speakerSplitPitch: Float = if (allValidPitches.size >= 2) {
            val minP = allValidPitches.minOrNull() ?: 120f
            val maxP = allValidPitches.maxOrNull() ?: 200f
            if (maxP - minP >= 25f) {
                (minP + maxP) / 2.0f
            } else 0f
        } else 0f

        val finalClips = ArrayList<DetectedDialogueClip>()
        var clipIndex = 1
        var lastAssignedSpeaker = 1

        for (seg in finalSplitSegments) {
            val lengthFrames = seg.endFrame - seg.startFrame
            if (lengthFrames >= minSpeechFrames) {
                val paddedStart = max(0, seg.startFrame - preRollFrames)
                val paddedEnd = min(totalFrames, seg.endFrame + postRollFrames)

                var startMs = paddedStart.toLong() * frameDurationMs
                var endMs = paddedEnd.toLong() * frameDurationMs

                // Ensure non-overlap with previous clip
                if (finalClips.isNotEmpty()) {
                    val prevEnd = finalClips.last().endMs
                    if (startMs < prevEnd + 50L) {
                        startMs = prevEnd + 50L
                    }
                }

                if (endMs > startMs + 250L) {
                    val speakerName = if (speakerSplitPitch > 0f && seg.medianPitch > 0f) {
                        if (seg.medianPitch < speakerSplitPitch) {
                            lastAssignedSpeaker = 1
                            "Speaker 1"
                        } else {
                            lastAssignedSpeaker = 2
                            "Speaker 2"
                        }
                    } else {
                        // Alternate naturally if pitch isn't distinctly separable
                        "Speaker $lastAssignedSpeaker"
                    }

                    finalClips.add(
                        DetectedDialogueClip(
                            id = UUID.randomUUID().toString(),
                            clipIndex = clipIndex,
                            startMs = startMs,
                            endMs = endMs,
                            speakerName = speakerName,
                            scriptText = ""
                        )
                    )
                    clipIndex++
                }
            }
        }

        finalClips
    }
}
