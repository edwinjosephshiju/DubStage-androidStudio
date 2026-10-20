package com.example.engine

import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

object WavUtils {

    /**
     * Writes standard 44-byte RIFF WAV header for 16-bit PCM.
     */
    fun writeWavHeader(
        out: FileOutputStream,
        totalAudioLen: Long,
        sampleRate: Int = 44100,
        channels: Int = 2
    ) {
        val totalDataLen = totalAudioLen + 36
        val byteRate = sampleRate * channels * 2
        val blockAlign = channels * 2

        val header = ByteArray(44)
        val buf = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)

        // RIFF chunk
        buf.put("RIFF".toByteArray())
        buf.putInt(totalDataLen.toInt())
        buf.put("WAVE".toByteArray())

        // fmt chunk
        buf.put("fmt ".toByteArray())
        buf.putInt(16) // Subchunk1Size for PCM
        buf.putShort(1.toShort()) // AudioFormat 1 = PCM
        buf.putShort(channels.toShort())
        buf.putInt(sampleRate)
        buf.putInt(byteRate)
        buf.putShort(blockAlign.toShort())
        buf.putShort(16.toShort()) // BitsPerSample

        // data chunk
        buf.put("data".toByteArray())
        buf.putInt(totalAudioLen.toInt())

        out.write(header, 0, 44)
    }

    /**
     * Updates RIFF and data chunk sizes in existing WAV file once all PCM bytes are written.
     */
    fun updateWavHeaderSizes(file: File) {
        val totalAudioLen = file.length() - 44
        val totalDataLen = totalAudioLen + 36

        RandomAccessFile(file, "rw").use { raf ->
            raf.seek(4)
            val buf = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN)
            buf.putInt(totalDataLen.toInt())
            raf.write(buf.array())

            raf.seek(40)
            buf.clear()
            buf.putInt(totalAudioLen.toInt())
            raf.write(buf.array())
        }
    }

    /**
     * Converts a raw PCM file to a standard RIFF WAV file with canonical 44-byte header.
     */
    fun writeWavFromPcm(
        pcmFile: File,
        wavFile: File,
        sampleRate: Int = 44100,
        channels: Int = 2
    ) {
        val pcmLen = pcmFile.length()
        if (wavFile.exists()) wavFile.delete()
        FileOutputStream(wavFile).use { fos ->
            writeWavHeader(fos, pcmLen, sampleRate, channels)
            java.io.FileInputStream(pcmFile).use { fis ->
                val buffer = ByteArray(65536)
                var read: Int
                while (fis.read(buffer).also { read = it } != -1) {
                    fos.write(buffer, 0, read)
                }
            }
            fos.flush()
        }
    }
}
