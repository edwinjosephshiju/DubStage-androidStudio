package com.example.engine

import android.content.Context
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import com.example.data.model.ClipEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.coroutines.coroutineContext
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.tanh

data class MuxerExportProgress(
    val stage: String,
    val percent: Int,
    val currentFrameOrSample: Long,
    val totalFramesOrSamples: Long
)

class Mp4ExportMuxer(private val context: Context) {

    companion object {
        const val SAMPLE_RATE = 44100
        const val CHANNELS = 2
        const val AAC_BITRATE = 192000
    }

    suspend fun exportDubbedVideo(
        sourceVideoUri: Uri,
        backingWavFile: File?,
        originalVocalsWavFile: File? = null,
        takes: List<MixTakeTrack>,
        totalDurationMs: Long,
        outputMp4File: File,
        onProgress: (MuxerExportProgress) -> Unit
    ): File = withContext(Dispatchers.IO) {
        if (outputMp4File.exists()) outputMp4File.delete()

        val videoExtractor = MediaExtractor()
        val pfd = context.contentResolver.openFileDescriptor(sourceVideoUri, "r")
            ?: throw IllegalArgumentException("Cannot open video descriptor for URI: $sourceVideoUri")

        var muxer: MediaMuxer? = null
        var aacEncoder: MediaCodec? = null

        try {
            videoExtractor.setDataSource(pfd.fileDescriptor)
            var videoTrackIdx = -1
            var videoFormat: MediaFormat? = null

            for (i in 0 until videoExtractor.trackCount) {
                val format = videoExtractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("video/")) {
                    videoTrackIdx = i
                    videoFormat = format
                    break
                }
            }

            if (videoTrackIdx == -1 || videoFormat == null) {
                throw IllegalStateException("Source media has no video track.")
            }

            videoExtractor.selectTrack(videoTrackIdx)

            // Setup AAC Audio Encoder
            val audioFormat = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, SAMPLE_RATE, CHANNELS)
            audioFormat.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            audioFormat.setInteger(MediaFormat.KEY_BIT_RATE, AAC_BITRATE)
            audioFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384)

            val encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
            encoder.configure(audioFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            encoder.start()
            aacEncoder = encoder

            muxer = MediaMuxer(outputMp4File.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

            // Video track is added immediately from extractor format
            val muxerVideoTrackIdx = muxer.addTrack(videoFormat)
            var muxerAudioTrackIdx = -1
            var muxerStarted = false

            // Pre-render mixed audio PCM in blocks and feed to AAC encoder
            val totalAudioFrames = (totalDurationMs * SAMPLE_RATE) / 1000L
            val framesPerBlock = 4096
            var currentFrame = 0L

            val backingRaf = if (backingWavFile != null && backingWavFile.exists()) RandomAccessFile(backingWavFile, "r") else null
            val originalVocalsRaf = if (originalVocalsWavFile != null && originalVocalsWavFile.exists()) RandomAccessFile(originalVocalsWavFile, "r") else null
            val takeRafs = HashMap<String, RandomAccessFile>()
            for (take in takes) {
                if (take.pcmFile.exists()) {
                    takeRafs[take.takeId] = RandomAccessFile(take.pcmFile, "r")
                }
            }

            val pcmReadBuf = ByteArray(framesPerBlock * 4)
            val vocalsReadBuf = ByteArray(framesPerBlock * 4)
            val audioBufferInfo = MediaCodec.BufferInfo()
            var audioInputDone = false
            var audioEncoderDone = false
            var consecutiveInputTimeouts = 0

            try {
                // Audio encoding & draining loop
                while (!audioEncoderDone) {
                    if (!coroutineContext.isActive) {
                        throw CancellationException("Export was cancelled")
                    }

                    // Feed PCM into encoder
                    if (!audioInputDone) {
                        val inputBufIdx = encoder.dequeueInputBuffer(10000L)
                        if (inputBufIdx >= 0) {
                            consecutiveInputTimeouts = 0
                            val inputBuffer = encoder.getInputBuffer(inputBufIdx)!!
                            inputBuffer.clear()

                            if (currentFrame >= totalAudioFrames) {
                                encoder.queueInputBuffer(inputBufIdx, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                audioInputDone = true
                            } else {
                                val framesToMix = min(framesPerBlock.toLong(), totalAudioFrames - currentFrame).toInt()
                                val mixL = FloatArray(framesToMix)
                                val mixR = FloatArray(framesToMix)
                                val hasTakeCovered = BooleanArray(framesToMix)

                                // 1. Read backing (residual audio)
                                if (backingRaf != null) {
                                    val offset = 44L + (currentFrame * 4L)
                                    if (offset < backingRaf.length()) {
                                        backingRaf.seek(offset)
                                        val read = backingRaf.read(pcmReadBuf, 0, min(framesToMix * 4, (backingRaf.length() - offset).toInt()))
                                        if (read > 0) {
                                            val sb = ByteBuffer.wrap(pcmReadBuf, 0, read).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                                            var idx = 0
                                            while (sb.hasRemaining() && idx < framesToMix) {
                                                val sL = sb.get().toFloat() / 32768.0f
                                                val sR = if (sb.hasRemaining()) sb.get().toFloat() / 32768.0f else sL
                                                mixL[idx] += sL
                                                mixR[idx] += sR
                                                idx++
                                            }
                                        }
                                    }
                                }

                                // 2. Read recorded user takes
                                for (take in takes) {
                                    val raf = takeRafs[take.takeId] ?: continue
                                    val takeStartFrame = ((take.startMs + take.timeOffsetMs) * SAMPLE_RATE) / 1000L
                                    val takeEndFrame = takeStartFrame + ((take.durationMs * SAMPLE_RATE) / 1000L)
                                    val chunkEndFrame = currentFrame + framesToMix

                                    if (chunkEndFrame > takeStartFrame && currentFrame < takeEndFrame) {
                                        val overlapStart = max(currentFrame, takeStartFrame)
                                        val overlapEnd = min(chunkEndFrame, takeEndFrame)
                                        val takeReadStartByte = (overlapStart - takeStartFrame) * 4L
                                        val takeReadBytes = (overlapEnd - overlapStart).toInt() * 4

                                        if (takeReadStartByte < raf.length()) {
                                            raf.seek(takeReadStartByte)
                                            val read = raf.read(pcmReadBuf, 0, min(takeReadBytes, (raf.length() - takeReadStartByte).toInt()))
                                            if (read > 0) {
                                                val sb = ByteBuffer.wrap(pcmReadBuf, 0, read).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                                                val localOffset = (overlapStart - currentFrame).toInt()
                                                val takeGainLinear = 10f.pow(take.gainDb / 20f)
                                                var idx = localOffset
                                                while (sb.hasRemaining() && idx < framesToMix) {
                                                    val sL = (sb.get().toFloat() / 32768.0f) * takeGainLinear
                                                    val sR = ((if (sb.hasRemaining()) sb.get().toFloat() else 0f) / 32768.0f) * takeGainLinear
                                                    mixL[idx] += sL
                                                    mixR[idx] += sR
                                                    hasTakeCovered[idx] = true
                                                    idx++
                                                }
                                            }
                                        }
                                    }
                                }

                                // 3. Use original vocal audio for scenes/segments with no recorded vocal!
                                if (originalVocalsRaf != null) {
                                    val offset = 44L + (currentFrame * 4L)
                                    if (offset < originalVocalsRaf.length()) {
                                        originalVocalsRaf.seek(offset)
                                        val read = originalVocalsRaf.read(vocalsReadBuf, 0, min(framesToMix * 4, (originalVocalsRaf.length() - offset).toInt()))
                                        if (read > 0) {
                                            val sb = ByteBuffer.wrap(vocalsReadBuf, 0, read).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                                            var idx = 0
                                            while (sb.hasRemaining() && idx < framesToMix) {
                                                val vL = sb.get().toFloat() / 32768.0f
                                                val vR = if (sb.hasRemaining()) sb.get().toFloat() / 32768.0f else vL
                                                // If not covered by an active recorded take, use original vocal
                                                if (!hasTakeCovered[idx]) {
                                                    mixL[idx] += vL
                                                    mixR[idx] += vR
                                                }
                                                idx++
                                            }
                                        }
                                    }
                                }

                                // Write soft-clipped PCM into input buffer
                                for (i in 0 until framesToMix) {
                                    val l = (tanh(mixL[i].toDouble()).toFloat().coerceIn(-1f, 1f) * 32767f).toInt().toShort()
                                    val r = (tanh(mixR[i].toDouble()).toFloat().coerceIn(-1f, 1f) * 32767f).toInt().toShort()
                                    inputBuffer.putShort(l)
                                    inputBuffer.putShort(r)
                                }

                                val presentationTimeUs = (currentFrame * 1000000L) / SAMPLE_RATE
                                encoder.queueInputBuffer(inputBufIdx, 0, framesToMix * 4, presentationTimeUs, 0)
                                currentFrame += framesToMix

                                val pct = ((currentFrame * 40L) / max(1L, totalAudioFrames)).toInt().coerceIn(0, 40)
                                onProgress(MuxerExportProgress("Encoding Dubbed Audio (AAC)", pct, currentFrame, totalAudioFrames))
                            }
                        } else {
                            consecutiveInputTimeouts++
                            if (consecutiveInputTimeouts > 150) {
                                // Safeguard against non-responsive encoder
                                break
                            }
                        }
                    }

                    // Drain encoder output buffers properly
                    while (true) {
                        if (!coroutineContext.isActive) throw CancellationException("Export was cancelled")
                        val outBufIdx = encoder.dequeueOutputBuffer(audioBufferInfo, 10000L)
                        if (outBufIdx == MediaCodec.INFO_TRY_AGAIN_LATER) {
                            break
                        } else if (outBufIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                            if (!muxerStarted) {
                                val newFormat = encoder.outputFormat
                                muxerAudioTrackIdx = muxer.addTrack(newFormat)
                                muxer.start()
                                muxerStarted = true
                            }
                        } else if (outBufIdx >= 0) {
                            if (muxerStarted && audioBufferInfo.size > 0 && (audioBufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
                                val encodedBuffer = encoder.getOutputBuffer(outBufIdx)!!
                                encodedBuffer.position(audioBufferInfo.offset)
                                encodedBuffer.limit(audioBufferInfo.offset + audioBufferInfo.size)
                                muxer.writeSampleData(muxerAudioTrackIdx, encodedBuffer, audioBufferInfo)
                            }
                            encoder.releaseOutputBuffer(outBufIdx, false)
                            if ((audioBufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                                audioEncoderDone = true
                                break
                            }
                        }
                    }
                }
            } finally {
                backingRaf?.close()
                originalVocalsRaf?.close()
                takeRafs.values.forEach { try { it.close() } catch (_: Exception) {} }
            }

            if (!muxerStarted) {
                val fallbackFormat = encoder.outputFormat
                muxerAudioTrackIdx = muxer.addTrack(fallbackFormat)
                muxer.start()
                muxerStarted = true
            }

            // Remux Video Track
            val videoBuf = ByteBuffer.allocate(1024 * 1024)
            val videoBufInfo = MediaCodec.BufferInfo()
            var videoSamplesMuxed = 0L

            while (true) {
                if (!coroutineContext.isActive) throw CancellationException("Export was cancelled")
                videoBuf.clear()
                val sampleSize = videoExtractor.readSampleData(videoBuf, 0)
                if (sampleSize < 0) break

                videoBufInfo.offset = 0
                videoBufInfo.size = sampleSize
                videoBufInfo.presentationTimeUs = videoExtractor.sampleTime
                videoBufInfo.flags = videoExtractor.sampleFlags

                muxer.writeSampleData(muxerVideoTrackIdx, videoBuf, videoBufInfo)
                videoSamplesMuxed++
                videoExtractor.advance()

                if (videoSamplesMuxed % 60 == 0L) {
                    val progressPct = (40 + ((videoBufInfo.presentationTimeUs * 60L) / max(1L, totalDurationMs * 1000L))).toInt().coerceIn(40, 99)
                    onProgress(MuxerExportProgress("Muxing Video & Subtitle Streams", progressPct, videoSamplesMuxed, 0))
                }
            }

            onProgress(MuxerExportProgress("Finalizing MP4 Master", 100, videoSamplesMuxed, videoSamplesMuxed))
            outputMp4File
        } catch (e: Exception) {
            if (outputMp4File.exists()) {
                try { outputMp4File.delete() } catch (_: Exception) {}
            }
            throw e
        } finally {
            try { aacEncoder?.stop() } catch (_: Exception) {}
            aacEncoder?.release()
            try {
                if (muxer != null) {
                    muxer.stop()
                    muxer.release()
                }
            } catch (_: Exception) {}
            videoExtractor.release()
            pfd.close()
        }
    }

    /**
     * Generates standard SubRip (.srt) subtitle file from dialogue clips.
     */
    fun exportSrt(clips: List<ClipEntity>, outputFile: File): File {
        if (outputFile.exists()) outputFile.delete()
        val writer = outputFile.bufferedWriter()
        writer.use { out ->
            var counter = 1
            for (clip in clips.sortedBy { it.startMs }) {
                if (clip.scriptText.isBlank()) continue
                out.write("$counter\n")
                out.write("${formatSrtTimecode(clip.startMs)} --> ${formatSrtTimecode(clip.endMs)}\n")
                out.write("[${clip.speakerName}] ${clip.scriptText.trim()}\n\n")
                counter++
            }
        }
        return outputFile
    }

    /**
     * Generates WebVTT (.vtt) subtitle file from dialogue clips.
     */
    fun exportVtt(clips: List<ClipEntity>, outputFile: File): File {
        if (outputFile.exists()) outputFile.delete()
        val writer = outputFile.bufferedWriter()
        writer.use { out ->
            out.write("WEBVTT - DubStage Subtitles\n\n")
            for (clip in clips.sortedBy { it.startMs }) {
                if (clip.scriptText.isBlank()) continue
                out.write("${formatVttTimecode(clip.startMs)} --> ${formatVttTimecode(clip.endMs)}\n")
                out.write("<v ${clip.speakerName}>${clip.scriptText.trim()}\n\n")
            }
        }
        return outputFile
    }

    private fun formatSrtTimecode(ms: Long): String {
        val hours = ms / 3600000L
        val minutes = (ms % 3600000L) / 60000L
        val seconds = (ms % 60000L) / 1000L
        val millis = ms % 1000L
        return "%02d:%02d:%02d,%03d".format(hours, minutes, seconds, millis)
    }

    private fun formatVttTimecode(ms: Long): String {
        val hours = ms / 3600000L
        val minutes = (ms % 3600000L) / 60000L
        val seconds = (ms % 60000L) / 1000L
        val millis = ms % 1000L
        return "%02d:%02d:%02d.%03d".format(hours, minutes, seconds, millis)
    }
}
