package com.example.engine

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.tanh

data class MixTakeTrack(
    val takeId: String,
    val pcmFile: File,
    val startMs: Long,
    val durationMs: Long,
    val gainDb: Float = 0.0f,
    val timeOffsetMs: Long = 0L
)

enum class StudioAuditionMode {
    FULL_DUB_MIX,           // Backing + Recorded User Takes
    ORIGINAL_WITH_RESIDUAL, // Separated Vocals + Synchronized Residual Audio (Backing)
    RECORDED_WITH_RESIDUAL, // Recorded Take Vocals + Synchronized Residual Audio (Backing)
    SOLO_ORIGINAL,          // Original Vocals Only
    SOLO_ACTIVE_TAKE,       // Current Take Only
    BACKING_ONLY            // Backing track without any dialogue
}

class PcmSoftwareMixerEngine {

    companion object {
        const val SAMPLE_RATE = 44100
        const val CHANNELS = 2
        const val BYTES_PER_FRAME = CHANNELS * 2 // 4 bytes (16-bit stereo)
    }

    private var audioTrack: AudioTrack? = null
    @Volatile
    private var isPlaying = false

    @Volatile
    var playbackPositionMs: Long = 0L
        private set

    suspend fun playMix(
        backingWavFile: File?,
        originalVocalsWavFile: File?,
        takes: List<MixTakeTrack>,
        startPositionMs: Long = 0L,
        endPositionMs: Long = Long.MAX_VALUE,
        auditionMode: StudioAuditionMode = StudioAuditionMode.FULL_DUB_MIX,
        activeTakeId: String? = null,
        backingGainDb: Float = 0f,
        onPlayheadUpdate: (positionMs: Long) -> Unit,
        onPlaybackFinished: () -> Unit
    ) = withContext(Dispatchers.IO) {
        stop()

        val minBuf = AudioTrack.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_STEREO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val bufSize = max(minBuf, 8192)

        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                    .build()
            )
            .setBufferSizeInBytes(bufSize)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        audioTrack = track
        isPlaying = true
        track.play()

        val framesPerBuffer = bufSize / BYTES_PER_FRAME
        val backingRaf = if (backingWavFile != null && backingWavFile.exists()) RandomAccessFile(backingWavFile, "r") else null
        val vocalsRaf = if (originalVocalsWavFile != null && originalVocalsWavFile.exists()) RandomAccessFile(originalVocalsWavFile, "r") else null

        // Cache take file handles
        val takeRafs = HashMap<String, RandomAccessFile>()
        for (take in takes) {
            if (take.pcmFile.exists()) {
                takeRafs[take.takeId] = RandomAccessFile(take.pcmFile, "r")
            }
        }

        var currentFrame = (startPositionMs * SAMPLE_RATE) / 1000L
        val endFrame = if (endPositionMs != Long.MAX_VALUE) (endPositionMs * SAMPLE_RATE) / 1000L else Long.MAX_VALUE

        val readByteBuffer = ByteArray(bufSize)
        val outShortBuffer = ByteBuffer.allocate(bufSize).order(ByteOrder.LITTLE_ENDIAN)

        val backingGainLinear = 10f.pow(backingGainDb / 20f)

        try {
            while (isPlaying && currentFrame < endFrame) {
                outShortBuffer.clear()
                val chunkFrames = min(framesPerBuffer.toLong(), endFrame - currentFrame).toInt()
                if (chunkFrames <= 0) break

                val mixL = FloatArray(chunkFrames)
                val mixR = FloatArray(chunkFrames)

                // 1. Backing track (Synchronized residual audio)
                val shouldPlayBacking = (
                    auditionMode == StudioAuditionMode.FULL_DUB_MIX ||
                    auditionMode == StudioAuditionMode.BACKING_ONLY ||
                    auditionMode == StudioAuditionMode.ORIGINAL_WITH_RESIDUAL ||
                    auditionMode == StudioAuditionMode.RECORDED_WITH_RESIDUAL
                )
                if (shouldPlayBacking && backingRaf != null) {
                    val fileOffset = 44L + (currentFrame * BYTES_PER_FRAME)
                    if (fileOffset < backingRaf.length()) {
                        backingRaf.seek(fileOffset)
                        val bytesToRead = min(chunkFrames * BYTES_PER_FRAME, (backingRaf.length() - fileOffset).toInt())
                        val read = backingRaf.read(readByteBuffer, 0, bytesToRead)
                        if (read > 0) {
                            val sb = ByteBuffer.wrap(readByteBuffer, 0, read).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                            var idx = 0
                            while (sb.hasRemaining() && idx < chunkFrames) {
                                val sL = sb.get().toFloat() / 32768.0f
                                val sR = if (sb.hasRemaining()) sb.get().toFloat() / 32768.0f else sL
                                mixL[idx] += sL * backingGainLinear
                                mixR[idx] += sR * backingGainLinear
                                idx++
                            }
                        }
                    }
                }

                // 2. Original Separated Vocals
                val shouldPlayOriginalVocals = (
                    auditionMode == StudioAuditionMode.SOLO_ORIGINAL ||
                    auditionMode == StudioAuditionMode.ORIGINAL_WITH_RESIDUAL
                )
                if (shouldPlayOriginalVocals && vocalsRaf != null) {
                    val fileOffset = 44L + (currentFrame * BYTES_PER_FRAME)
                    if (fileOffset < vocalsRaf.length()) {
                        vocalsRaf.seek(fileOffset)
                        val bytesToRead = min(chunkFrames * BYTES_PER_FRAME, (vocalsRaf.length() - fileOffset).toInt())
                        val read = vocalsRaf.read(readByteBuffer, 0, bytesToRead)
                        if (read > 0) {
                            val sb = ByteBuffer.wrap(readByteBuffer, 0, read).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                            var idx = 0
                            while (sb.hasRemaining() && idx < chunkFrames) {
                                val sL = sb.get().toFloat() / 32768.0f
                                val sR = if (sb.hasRemaining()) sb.get().toFloat() / 32768.0f else sL
                                mixL[idx] += sL
                                mixR[idx] += sR
                                idx++
                            }
                        }
                    }
                }

                // 3. User Recorded Takes (Only recorded vocal when RECORDED_WITH_RESIDUAL or SOLO_ACTIVE_TAKE)
                val shouldPlayTakes = (
                    auditionMode == StudioAuditionMode.FULL_DUB_MIX ||
                    auditionMode == StudioAuditionMode.SOLO_ACTIVE_TAKE ||
                    auditionMode == StudioAuditionMode.RECORDED_WITH_RESIDUAL
                )
                if (shouldPlayTakes) {
                    val activeTakes = if (auditionMode == StudioAuditionMode.SOLO_ACTIVE_TAKE || auditionMode == StudioAuditionMode.RECORDED_WITH_RESIDUAL) {
                        takes.filter { it.takeId == activeTakeId }
                    } else takes

                    for (take in activeTakes) {
                        val raf = takeRafs[take.takeId] ?: continue
                        val takeStartFrame = ((take.startMs + take.timeOffsetMs) * SAMPLE_RATE) / 1000L
                        val takeFrames = (take.durationMs * SAMPLE_RATE) / 1000L
                        val takeEndFrame = takeStartFrame + takeFrames

                        val chunkEndFrame = currentFrame + chunkFrames
                        // Check overlap
                        if (chunkEndFrame > takeStartFrame && currentFrame < takeEndFrame) {
                            val overlapStart = max(currentFrame, takeStartFrame)
                            val overlapEnd = min(chunkEndFrame, takeEndFrame)

                            val takeReadStartByte = (overlapStart - takeStartFrame) * BYTES_PER_FRAME
                            val takeReadLengthBytes = (overlapEnd - overlapStart).toInt() * BYTES_PER_FRAME

                            if (takeReadStartByte < raf.length()) {
                                raf.seek(takeReadStartByte)
                                val read = raf.read(readByteBuffer, 0, min(takeReadLengthBytes, (raf.length() - takeReadStartByte).toInt()))
                                if (read > 0) {
                                    val sb = ByteBuffer.wrap(readByteBuffer, 0, read).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                                    val localOffset = (overlapStart - currentFrame).toInt()
                                    val takeGainLinear = 10f.pow(take.gainDb / 20f)
                                    var idx = localOffset
                                    while (sb.hasRemaining() && idx < chunkFrames) {
                                        val sL = sb.get().toFloat() / 32768.0f
                                        val sR = if (sb.hasRemaining()) sb.get().toFloat() / 32768.0f else sL
                                        mixL[idx] += sL * takeGainLinear
                                        mixR[idx] += sR * takeGainLinear
                                        idx++
                                    }
                                }
                            }
                        }
                    }
                }

                // 4. Soft-knee limiter y_out = tanh(y) and write to AudioTrack
                for (i in 0 until chunkFrames) {
                    val limitedL = tanh(mixL[i].toDouble()).toFloat()
                    val limitedR = tanh(mixR[i].toDouble()).toFloat()

                    val shortL = (limitedL.coerceIn(-1.0f, 1.0f) * 32767.0f).toInt().toShort()
                    val shortR = (limitedR.coerceIn(-1.0f, 1.0f) * 32767.0f).toInt().toShort()

                    outShortBuffer.putShort(shortL)
                    outShortBuffer.putShort(shortR)
                }

                track.write(outShortBuffer.array(), 0, chunkFrames * BYTES_PER_FRAME)
                currentFrame += chunkFrames

                playbackPositionMs = (currentFrame * 1000L) / SAMPLE_RATE
                if (endPositionMs != Long.MAX_VALUE && playbackPositionMs > endPositionMs) {
                    playbackPositionMs = endPositionMs
                }
                onPlayheadUpdate(playbackPositionMs)
            }
        } finally {
            backingRaf?.close()
            vocalsRaf?.close()
            takeRafs.values.forEach { try { it.close() } catch (_: Exception) {} }
            try {
                track.stop()
            } catch (_: Exception) {}
            track.release()
            audioTrack = null
            isPlaying = false
            onPlaybackFinished()
        }
    }

    fun stop() {
        isPlaying = false
    }

    fun isPlaying(): Boolean = isPlaying
}
