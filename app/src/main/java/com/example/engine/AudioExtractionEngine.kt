package com.example.engine

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.max

data class AudioExtractionResult(
    val pcmFile: File,
    val durationMs: Long,
    val sampleRate: Int,
    val channels: Int,
    val waveformPeaks: FloatArray
)

class AudioExtractionEngine(private val context: Context) {

    suspend fun extractAndDecodeAudio(
        videoUri: Uri,
        outputPcmFile: File,
        onProgress: (percent: Int, decodedSamples: Long) -> Unit
    ): AudioExtractionResult = withContext(Dispatchers.IO) {
        val extractor = MediaExtractor()
        val pfd = context.contentResolver.openFileDescriptor(videoUri, "r")
            ?: throw IllegalArgumentException("Could not open file descriptor for URI: $videoUri")

        try {
            extractor.setDataSource(pfd.fileDescriptor)
            var audioTrackIndex = -1
            var inputFormat: MediaFormat? = null

            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) {
                    audioTrackIndex = i
                    inputFormat = format
                    break
                }
            }

            if (audioTrackIndex == -1 || inputFormat == null) {
                throw IllegalStateException("No audio track found in media file: $videoUri")
            }

            extractor.selectTrack(audioTrackIndex)
            val durationUs = if (inputFormat.containsKey(MediaFormat.KEY_DURATION)) {
                inputFormat.getLong(MediaFormat.KEY_DURATION)
            } else 0L
            val durationMs = durationUs / 1000L

            val mime = inputFormat.getString(MediaFormat.KEY_MIME)!!
            val decoder = MediaCodec.createDecoderByType(mime)
            decoder.configure(inputFormat, null, null, 0)
            decoder.start()

            val fos = FileOutputStream(outputPcmFile)
            val bufferInfo = MediaCodec.BufferInfo()

            var isExtractorEOS = false
            var isDecoderEOS = false
            var totalSamplesWritten = 0L

            val targetSampleRate = 44100
            val targetChannels = 2

            // Temporary list to store sample amplitudes for peak extraction
            // We sample amplitude envelope dynamically across the duration
            val rawAmplitudes = ArrayList<Float>(20000)

            val timeoutUs = 10000L

            try {
                while (!isDecoderEOS) {
                    if (!isExtractorEOS) {
                        val inputBufferId = decoder.dequeueInputBuffer(timeoutUs)
                        if (inputBufferId >= 0) {
                            val inputBuffer = decoder.getInputBuffer(inputBufferId)!!
                            val sampleSize = extractor.readSampleData(inputBuffer, 0)
                            if (sampleSize < 0) {
                                decoder.queueInputBuffer(
                                    inputBufferId,
                                    0,
                                    0,
                                    0L,
                                    MediaCodec.BUFFER_FLAG_END_OF_STREAM
                                )
                                isExtractorEOS = true
                            } else {
                                decoder.queueInputBuffer(
                                    inputBufferId,
                                    0,
                                    sampleSize,
                                    extractor.sampleTime,
                                    0
                                )
                                extractor.advance()
                            }
                        }
                    }

                    val outputBufferId = decoder.dequeueOutputBuffer(bufferInfo, timeoutUs)
                    if (outputBufferId >= 0) {
                        val outputBuffer = decoder.getOutputBuffer(outputBufferId)!!
                        if (bufferInfo.size > 0) {
                            outputBuffer.position(bufferInfo.offset)
                            outputBuffer.limit(bufferInfo.offset + bufferInfo.size)

                            val outFormat = decoder.outputFormat
                            val srcChannels = if (outFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                                outFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                            } else {
                                2
                            }
                            val srcSampleRate = if (outFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                                outFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                            } else {
                                targetSampleRate
                            }

                            val pcmBytes = ByteArray(bufferInfo.size)
                            outputBuffer.get(pcmBytes)

                            // Read shorts (16-bit PCM little-endian)
                            val shortBuffer = ByteBuffer.wrap(pcmBytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                            val numShorts = shortBuffer.remaining()

                            // Convert to 44.1kHz stereo
                            val convertedBytes = convertToStandardStereo44100(
                                shortBuffer,
                                numShorts,
                                srcChannels,
                                srcSampleRate,
                                targetChannels,
                                targetSampleRate
                            )

                            fos.write(convertedBytes)
                            val framesWritten = convertedBytes.size / (targetChannels * 2)
                            totalSamplesWritten += framesWritten

                            // Collect downsampled amplitudes for envelope
                            val convertedShorts = ByteBuffer.wrap(convertedBytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                            var maxAmpInChunk = 0f
                            val step = max(1, convertedShorts.remaining() / 100)
                            var idx = 0
                            while (idx < convertedShorts.remaining()) {
                                val s = convertedShorts.get(idx).toInt()
                                val norm = abs(s) / 32768.0f
                                if (norm > maxAmpInChunk) maxAmpInChunk = norm
                                idx += step
                            }
                            rawAmplitudes.add(maxAmpInChunk)

                            val progressPct = if (durationUs > 0) {
                                ((bufferInfo.presentationTimeUs * 100L) / durationUs).toInt().coerceIn(0, 99)
                            } else 0
                            onProgress(progressPct, totalSamplesWritten)
                        }

                        decoder.releaseOutputBuffer(outputBufferId, false)
                        if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            isDecoderEOS = true
                        }
                    }
                }
            } finally {
                fos.flush()
                fos.close()
                decoder.stop()
                decoder.release()
                extractor.release()
            }

            // Generate exactly 2000 normalized peak points
            val waveformPeaks = resamplePeakEnvelope(rawAmplitudes, 2000)
            onProgress(100, totalSamplesWritten)

            AudioExtractionResult(
                pcmFile = outputPcmFile,
                durationMs = durationMs,
                sampleRate = targetSampleRate,
                channels = targetChannels,
                waveformPeaks = waveformPeaks
            )
        } finally {
            pfd.close()
        }
    }

    private fun convertToStandardStereo44100(
        srcBuffer: java.nio.ShortBuffer,
        totalShorts: Int,
        srcChannels: Int,
        srcRate: Int,
        dstChannels: Int,
        dstRate: Int
    ): ByteArray {
        // If already 44100 stereo, write directly
        if (srcChannels == dstChannels && srcRate == dstRate) {
            val byteBuffer = ByteBuffer.allocate(totalShorts * 2).order(ByteOrder.LITTLE_ENDIAN)
            while (srcBuffer.hasRemaining()) {
                byteBuffer.putShort(srcBuffer.get())
            }
            return byteBuffer.array()
        }

        // Handle mono to stereo and simple rate matching
        val srcFrames = totalShorts / srcChannels
        val dstFrames = ((srcFrames.toLong() * dstRate) / srcRate).toInt()
        val outBytes = ByteArray(dstFrames * dstChannels * 2)
        val outBuffer = ByteBuffer.wrap(outBytes).order(ByteOrder.LITTLE_ENDIAN)

        for (i in 0 until dstFrames) {
            val srcIndex = ((i.toLong() * srcRate) / dstRate).toInt().coerceIn(0, srcFrames - 1)
            val l: Short
            val r: Short
            if (srcChannels == 1) {
                val sample = srcBuffer.get(srcIndex)
                l = sample
                r = sample
            } else {
                l = srcBuffer.get(srcIndex * srcChannels)
                r = srcBuffer.get(srcIndex * srcChannels + 1)
            }
            outBuffer.putShort(l)
            outBuffer.putShort(r)
        }
        return outBytes
    }

    fun loadPeaksFromPcm(file: File, targetSize: Int = 2000): FloatArray {
        if (!file.exists()) return FloatArray(0)
        val rawAmps = mutableListOf<Float>()
        try {
            java.io.FileInputStream(file).use { fis ->
                val buffer = ByteArray(65536)
                var bytesRead: Int
                while (fis.read(buffer).also { bytesRead = it } != -1) {
                    val shortBuffer = ByteBuffer.wrap(buffer, 0, bytesRead).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                    val step = max(1, shortBuffer.remaining() / 100)
                    var i = 0
                    while (i < shortBuffer.remaining()) {
                        val sample = shortBuffer.get(i).toInt()
                        rawAmps.add(abs(sample) / 32768f)
                        i += step
                    }
                }
            }
        } catch (e: Exception) {
            return FloatArray(0)
        }
        return resamplePeakEnvelope(rawAmps, targetSize)
    }

    fun loadPeaksFromWav(file: File, targetSize: Int = 2000): FloatArray {
        if (!file.exists()) return FloatArray(0)
        val rawAmps = mutableListOf<Float>()
        try {
            java.io.FileInputStream(file).use { fis ->
                // Skip 44 bytes header
                fis.skip(44)
                val buffer = ByteArray(65536)
                var bytesRead: Int
                while (fis.read(buffer).also { bytesRead = it } != -1) {
                    val shortBuffer = ByteBuffer.wrap(buffer, 0, bytesRead).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                    val step = max(1, shortBuffer.remaining() / 100)
                    var i = 0
                    while (i < shortBuffer.remaining()) {
                        val sample = shortBuffer.get(i).toInt()
                        rawAmps.add(abs(sample) / 32768f)
                        i += step
                    }
                }
            }
        } catch (e: Exception) {
            return FloatArray(0)
        }
        return resamplePeakEnvelope(rawAmps, targetSize)
    }

    private fun resamplePeakEnvelope(rawAmps: List<Float>, targetSize: Int): FloatArray {
        val result = FloatArray(targetSize)
        if (rawAmps.isEmpty()) return result
        if (rawAmps.size == targetSize) {
            for (i in 0 until targetSize) result[i] = rawAmps[i].coerceIn(0f, 1f)
            return result
        }

        val step = rawAmps.size.toDouble() / targetSize.toDouble()
        for (i in 0 until targetSize) {
            val startIdx = (i * step).toInt().coerceIn(0, rawAmps.size - 1)
            val endIdx = ((i + 1) * step).toInt().coerceIn(startIdx + 1, rawAmps.size)
            var maxVal = 0f
            for (k in startIdx until endIdx) {
                if (rawAmps[k] > maxVal) maxVal = rawAmps[k]
            }
            result[i] = maxVal.coerceIn(0f, 1f)
        }
        return result
    }
}
