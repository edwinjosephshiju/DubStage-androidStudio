package com.example.engine

import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

sealed class ModelStatus {
    object Missing : ModelStatus()
    data class Downloading(
        val bytesRead: Long,
        val totalBytes: Long,
        val speedBytesPerSec: Long,
        val percent: Int,
        val statusMessage: String
    ) : ModelStatus()
    data class Ready(
        val fileSizeBytes: Long,
        val sha256Hex: String,
        val modelPath: String,
        val preset: ModelPreset
    ) : ModelStatus()
    data class Error(val message: String) : ModelStatus()
}

class ModelManager(private val context: Context) {

    companion object {
        const val TAG = "ModelManager"
        const val MIN_MODEL_SIZE_BYTES = 50_000_000L // 50 MB
    }

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val _activePreset = MutableStateFlow(ModelPreset.HTDEMUCS_FT_VOCALS)
    val activePresetFlow: StateFlow<ModelPreset> = _activePreset.asStateFlow()
    val activePreset: ModelPreset get() = _activePreset.value

    private val _activeKaraokeMode = MutableStateFlow(KaraokeMode.RESIDUAL_SUBTRACTION)
    val activeKaraokeModeFlow: StateFlow<KaraokeMode> = _activeKaraokeMode.asStateFlow()
    val activeKaraokeMode: KaraokeMode get() = _activeKaraokeMode.value

    private val _activeBackend = MutableStateFlow(ExecutionBackend.CPU_LOW_RAM)
    val activeBackendFlow: StateFlow<ExecutionBackend> = _activeBackend.asStateFlow()
    val activeBackend: ExecutionBackend get() = _activeBackend.value

    private val _status = MutableStateFlow<ModelStatus>(ModelStatus.Missing)
    val status: StateFlow<ModelStatus> = _status.asStateFlow()

    init {
        checkExistingModel()
    }

    fun setModelPreset(preset: ModelPreset) {
        _activePreset.value = preset
        if (preset.isVocalsOnlyTrained) {
            _activeKaraokeMode.value = KaraokeMode.RESIDUAL_SUBTRACTION
        }
        checkExistingModel()
    }

    fun setKaraokeMode(mode: KaraokeMode) {
        _activeKaraokeMode.value = if (_activePreset.value.isVocalsOnlyTrained) {
            KaraokeMode.RESIDUAL_SUBTRACTION
        } else mode
    }

    fun setExecutionBackend(backend: ExecutionBackend) {
        _activeBackend.value = backend
    }

    fun getModelFile(preset: ModelPreset = _activePreset.value): File {
        val specificFile = File(context.filesDir, preset.fileName)
        if (specificFile.exists() && specificFile.length() >= MIN_MODEL_SIZE_BYTES) {
            return specificFile
        }
        // Legacy file names and aliases
        val aliases = listOf(
            preset.fileName,
            "htdemucs_ft_vocals_fp16weights.onnx",
            "htdemucs_fp16weights.onnx",
            "htdemucs_ft.onnx",
            "htdemucs_v4.onnx",
            "models/htdemucs_v4.onnx",
            "demucs.onnx"
        )
        for (name in aliases) {
            val f = File(context.filesDir, name)
            if (f.exists() && f.length() >= MIN_MODEL_SIZE_BYTES) {
                return f
            }
        }
        return specificFile
    }

    fun checkExistingModel() {
        val file = getModelFile(_activePreset.value)
        if (file.exists() && file.length() >= MIN_MODEL_SIZE_BYTES) {
            val length = file.length()
            val sha = try {
                computeSha256(file)
            } catch (_: Throwable) {
                "verified"
            }
            _status.value = ModelStatus.Ready(length, sha, file.absolutePath, _activePreset.value)
        } else {
            // Check if alternative preset exists
            val altPreset = if (_activePreset.value == ModelPreset.HTDEMUCS_FT_VOCALS) {
                ModelPreset.HTDEMUCS_4STEM
            } else {
                ModelPreset.HTDEMUCS_FT_VOCALS
            }
            val altFile = getModelFile(altPreset)
            if (altFile.exists() && altFile.length() >= MIN_MODEL_SIZE_BYTES) {
                _activePreset.value = altPreset
                val sha = try { computeSha256(altFile) } catch (_: Throwable) { "verified" }
                _status.value = ModelStatus.Ready(altFile.length(), sha, altFile.absolutePath, altPreset)
            } else {
                _status.value = ModelStatus.Missing
            }
        }
    }

    suspend fun sideloadModel(sourceUri: Uri) = withContext(Dispatchers.IO) {
        val targetFileName = _activePreset.value.fileName
        val destFile = File(context.filesDir, targetFileName)
        val tempFile = File(context.filesDir, "${targetFileName}.sideload")

        try {
            _status.value = ModelStatus.Downloading(0, 0, 0, 0, "Sideloading ONNX model from storage...")

            val inputStream = context.contentResolver.openInputStream(sourceUri)
                ?: throw IllegalArgumentException("Cannot open stream for selected URI: $sourceUri")

            if (tempFile.exists()) tempFile.delete()

            inputStream.use { inStream ->
                FileOutputStream(tempFile).use { outStream ->
                    val buffer = ByteArray(65536)
                    var bytesRead: Int
                    var totalRead = 0L
                    var lastUpdateTime = System.currentTimeMillis()
                    while (inStream.read(buffer).also { bytesRead = it } != -1) {
                        outStream.write(buffer, 0, bytesRead)
                        totalRead += bytesRead
                        val now = System.currentTimeMillis()
                        if (now - lastUpdateTime > 400) {
                            _status.value = ModelStatus.Downloading(
                                bytesRead = totalRead,
                                totalBytes = 0L,
                                speedBytesPerSec = 0L,
                                percent = 0,
                                statusMessage = "Reading local file (${totalRead / (1024 * 1024)} MB)..."
                            )
                            lastUpdateTime = now
                        }
                    }
                    outStream.flush()
                }
            }

            val size = tempFile.length()
            if (size < MIN_MODEL_SIZE_BYTES) {
                val sizeMb = size / (1024 * 1024)
                tempFile.delete()
                _status.value = ModelStatus.Error("Sideloaded file too small ($sizeMb MB). Minimum 50 MB required for Demucs v4.")
                return@withContext
            }

            if (destFile.exists()) destFile.delete()
            if (!tempFile.renameTo(destFile)) {
                tempFile.copyTo(destFile, overwrite = true)
                tempFile.delete()
            }

            System.gc()
            val sha = try { computeSha256(destFile) } catch (_: Throwable) { "sideloaded" }
            _status.value = ModelStatus.Ready(
                fileSizeBytes = destFile.length(),
                sha256Hex = sha,
                modelPath = destFile.absolutePath,
                preset = _activePreset.value
            )
            Log.i(TAG, "Sideload complete: ${destFile.absolutePath} (${destFile.length()} bytes)")
        } catch (t: Throwable) {
            tempFile.delete()
            Log.e(TAG, "Sideload error", t)
            _status.value = ModelStatus.Error("Sideload failed: ${t.localizedMessage ?: t.message ?: "Unknown storage error"}")
        }
    }

    suspend fun downloadModel(preset: ModelPreset = _activePreset.value) = withContext(Dispatchers.IO) {
        _activePreset.value = preset
        val destFile = File(context.filesDir, preset.fileName)
        val tempFile = File(context.filesDir, "${preset.fileName}.download")

        // List of candidate URLs to prevent 404
        val candidateUrls = listOf(
            preset.downloadUrl,
            preset.downloadUrl.replace("/resolve/", "/raw/"),
            if (preset == ModelPreset.HTDEMUCS_FT_VOCALS) {
                "https://huggingface.co/StemSplitio/htdemucs-ft-vocals-onnx/resolve/main/htdemucs_ft_vocals_fp16weights.onnx"
            } else {
                "https://huggingface.co/StemSplitio/htdemucs-onnx/resolve/main/htdemucs_fp16weights.onnx"
            }
        ).distinct()

        var downloadedSuccessfully = false
        var lastErrorMessage = ""

        for (urlStr in candidateUrls) {
            try {
                _status.value = ModelStatus.Downloading(0, 0, 0, 0, "Connecting to Hugging Face...")
                val request = Request.Builder()
                    .url(urlStr)
                    .header("User-Agent", "DubStage-DemucsStudio/1.0 (Android)")
                    .build()

                val response = okHttpClient.newCall(request).execute()
                if (!response.isSuccessful) {
                    lastErrorMessage = "HTTP ${response.code}: ${response.message}"
                    response.close()
                    continue
                }

                val body = response.body
                if (body == null) {
                    lastErrorMessage = "Empty response body"
                    response.close()
                    continue
                }

                val totalBytes = body.contentLength()
                val inputStream = body.byteStream()

                if (tempFile.exists()) tempFile.delete()
                val outputStream = FileOutputStream(tempFile)

                val buffer = ByteArray(65536)
                var bytesReadTotal = 0L
                var lastTime = System.currentTimeMillis()
                var bytesSinceLastTime = 0L

                inputStream.use { inStream ->
                    outputStream.use { outStream ->
                        var read: Int
                        while (inStream.read(buffer).also { read = it } != -1) {
                            outStream.write(buffer, 0, read)
                            bytesReadTotal += read
                            bytesSinceLastTime += read

                            val now = System.currentTimeMillis()
                            val delta = now - lastTime
                            if (delta >= 400) {
                                val speed = if (delta > 0) (bytesSinceLastTime * 1000L) / delta else 0L
                                val pct = if (totalBytes > 0) {
                                    ((bytesReadTotal * 100) / totalBytes).toInt().coerceIn(0, 100)
                                } else 0
                                _status.value = ModelStatus.Downloading(
                                    bytesRead = bytesReadTotal,
                                    totalBytes = totalBytes,
                                    speedBytesPerSec = speed,
                                    percent = pct,
                                    statusMessage = "Downloading ${preset.displayName}: $pct%"
                                )
                                lastTime = now
                                bytesSinceLastTime = 0
                            }
                        }
                        outStream.flush()
                    }
                }
                response.close()

                val size = tempFile.length()
                if (size < MIN_MODEL_SIZE_BYTES) {
                    tempFile.delete()
                    lastErrorMessage = "Incomplete download (${size / (1024 * 1024)} MB < 50 MB)"
                    continue
                }

                if (destFile.exists()) destFile.delete()
                if (!tempFile.renameTo(destFile)) {
                    tempFile.copyTo(destFile, overwrite = true)
                    tempFile.delete()
                }

                System.gc()
                val sha = try { computeSha256(destFile) } catch (_: Throwable) { "downloaded" }
                _status.value = ModelStatus.Ready(
                    fileSizeBytes = destFile.length(),
                    sha256Hex = sha,
                    modelPath = destFile.absolutePath,
                    preset = preset
                )
                downloadedSuccessfully = true
                break
            } catch (t: Throwable) {
                tempFile.delete()
                lastErrorMessage = t.localizedMessage ?: t.message ?: "Network error"
                Log.w(TAG, "Attempt failed for $urlStr: $lastErrorMessage")
            }
        }

        if (!downloadedSuccessfully) {
            _status.value = ModelStatus.Error("Download failed: $lastErrorMessage")
        }
    }

    private fun computeSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { fis ->
            val buf = ByteArray(65536)
            var n: Int
            while (fis.read(buf).also { n = it } != -1) {
                digest.update(buf, 0, n)
            }
        }
        val bytes = digest.digest()
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
