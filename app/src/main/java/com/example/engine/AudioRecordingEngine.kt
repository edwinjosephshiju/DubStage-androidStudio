package com.example.engine

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sqrt

data class TakeRecordingResult(
    val pcmFile: File,
    val durationMs: Long,
    val peaks: FloatArray
)

class AudioRecordingEngine(private val context: Context) {

    private var audioRecord: AudioRecord? = null
    @Volatile
    private var isRecording = false

    companion object {
        const val SAMPLE_RATE = 44100
        const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_STEREO
        const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    }

    @SuppressLint("MissingPermission")
    suspend fun recordTake(
        outputFile: File,
        maxDurationMs: Long = Long.MAX_VALUE,
        onRmsDecibels: (db: Float) -> Unit,
        onPeakSample: (normalizedAmp: Float) -> Unit
    ): TakeRecordingResult = withContext(Dispatchers.IO) {
        val minBufSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
        if (minBufSize <= 0) {
            throw IllegalStateException("AudioRecord hardware buffer initialization failed (minBufSize=$minBufSize)")
        }

        val bufferSize = max(minBufSize, 8192)
        val recorder = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            CHANNEL_CONFIG,
            AUDIO_FORMAT,
            bufferSize
        )

        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            throw IllegalStateException("AudioRecord failed to initialize. Device microphone may be busy or unavailable.")
        }

        audioRecord = recorder
        isRecording = true
        recorder.startRecording()

        val fos = FileOutputStream(outputFile)
        val audioData = ShortArray(bufferSize / 2)
        val byteBuffer = ByteBuffer.allocate(audioData.size * 2).order(ByteOrder.LITTLE_ENDIAN)

        val recordedPeaksList = ArrayList<Float>(2000)
        var totalFramesRecorded = 0L
        val startTime = System.currentTimeMillis()

        try {
            while (isRecording) {
                val shortsRead = recorder.read(audioData, 0, audioData.size)
                if (shortsRead > 0) {
                    byteBuffer.clear()
                    var sumSquares = 0.0
                    var maxAmpInChunk = 0f

                    for (i in 0 until shortsRead) {
                        val s = audioData[i]
                        byteBuffer.putShort(s)
                        val norm = abs(s.toInt()) / 32768.0f
                        sumSquares += (norm * norm)
                        if (norm > maxAmpInChunk) maxAmpInChunk = norm
                    }

                    fos.write(byteBuffer.array(), 0, shortsRead * 2)
                    val frames = shortsRead / 2 // stereo
                    totalFramesRecorded += frames

                    recordedPeaksList.add(maxAmpInChunk)
                    onPeakSample(maxAmpInChunk)

                    val rms = sqrt(sumSquares / shortsRead).toFloat()
                    val db = if (rms > 0.00001f) (20f * log10(rms)).coerceIn(-60f, 0f) else -60f
                    onRmsDecibels(db)

                    val elapsedMs = (totalFramesRecorded * 1000L) / SAMPLE_RATE
                    val wallElapsedMs = System.currentTimeMillis() - startTime
                    if (maxDurationMs != Long.MAX_VALUE && (elapsedMs >= maxDurationMs || wallElapsedMs >= maxDurationMs)) {
                        isRecording = false
                        break
                    }
                } else {
                    val wallElapsedMs = System.currentTimeMillis() - startTime
                    if (maxDurationMs != Long.MAX_VALUE && wallElapsedMs >= maxDurationMs) {
                        isRecording = false
                        break
                    }
                    if (shortsRead < 0) {
                        // Avoid spin on mic buffer read error
                        Thread.sleep(10)
                    }
                }
            }
        } finally {
            try {
                recorder.stop()
            } catch (_: Exception) {}
            recorder.release()
            audioRecord = null
            fos.flush()
            fos.close()
        }

        val durationMs = System.currentTimeMillis() - startTime
        val resampledPeaks = resampleRecordedPeaks(recordedPeaksList, 200)

        TakeRecordingResult(
            pcmFile = outputFile,
            durationMs = durationMs,
            peaks = resampledPeaks
        )
    }

    fun stopRecording() {
        isRecording = false
    }

    private fun resampleRecordedPeaks(raw: List<Float>, targetSize: Int): FloatArray {
        val result = FloatArray(targetSize)
        if (raw.isEmpty()) return result
        val step = raw.size.toDouble() / targetSize.toDouble()
        for (i in 0 until targetSize) {
            val startIdx = (i * step).toInt().coerceIn(0, raw.size - 1)
            val endIdx = ((i + 1) * step).toInt().coerceIn(startIdx + 1, raw.size)
            var maxVal = 0f
            for (k in startIdx until endIdx) {
                if (raw[k] > maxVal) maxVal = raw[k]
            }
            result[i] = maxVal.coerceIn(0f, 1f)
        }
        return result
    }
}
