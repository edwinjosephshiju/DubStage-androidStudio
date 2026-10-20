package com.example.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.example.data.database.DubStageDatabase
import com.example.data.model.ClipEntity
import com.example.data.model.ProjectEntity
import com.example.data.model.TakeEntity
import com.example.data.repository.DubStageRepository
import com.example.engine.AudioExtractionEngine
import com.example.engine.AudioRecordingEngine
import com.example.engine.DemucsOnnxSeparationEngine
import com.example.engine.DemucsSeparationProgress
import com.example.engine.ExecutionBackend
import com.example.engine.KaraokeMode
import com.example.engine.MixTakeTrack
import com.example.engine.ModelManager
import com.example.engine.ModelPreset
import com.example.engine.ModelStatus
import com.example.engine.Mp4ExportMuxer
import com.example.engine.MuxerExportProgress
import com.example.engine.PcmSoftwareMixerEngine
import com.example.engine.StudioAuditionMode
import com.example.engine.VadEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import kotlin.math.max

enum class PlaybackTarget {
    FULL_PROJECT,
    CLIP_VAD_ORIGINAL,
    CLIP_ORIGINAL_VOCAL,
    CLIP_RECORDED_VOCAL
}

class DubStageViewModel(application: Application) : AndroidViewModel(application) {

    private val db = DubStageDatabase.getDatabase(application)
    private val repository = DubStageRepository(application, db.dubStageDao())
    val modelManager = ModelManager(application)

    private val audioExtractionEngine = AudioExtractionEngine(application)
    private val demucsSeparationEngine = DemucsOnnxSeparationEngine(application)
    private val vadEngine = VadEngine()
    private val recordingEngine = AudioRecordingEngine(application)
    private val mixerEngine = PcmSoftwareMixerEngine()
    private val mp4Muxer = Mp4ExportMuxer(application)

    // ExoPlayer for video display
    val exoPlayer: ExoPlayer = ExoPlayer.Builder(application).build().apply {
        repeatMode = Player.REPEAT_MODE_OFF
    }

    // UI States
    private val _activeProject = MutableStateFlow<ProjectEntity?>(null)
    val activeProject: StateFlow<ProjectEntity?> = _activeProject.asStateFlow()

    private val _clips = MutableStateFlow<List<ClipEntity>>(emptyList())
    val clips: StateFlow<List<ClipEntity>> = _clips.asStateFlow()

    private val _activeClip = MutableStateFlow<ClipEntity?>(null)
    val activeClip: StateFlow<ClipEntity?> = _activeClip.asStateFlow()

    private val _takes = MutableStateFlow<List<TakeEntity>>(emptyList())
    val takes: StateFlow<List<TakeEntity>> = _takes.asStateFlow()

    private val _activeTake = MutableStateFlow<TakeEntity?>(null)
    val activeTake: StateFlow<TakeEntity?> = _activeTake.asStateFlow()

    private val _playheadMs = MutableStateFlow(0L)
    val playheadMs: StateFlow<Long> = _playheadMs.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _activePlaybackTarget = MutableStateFlow<PlaybackTarget?>(null)
    val activePlaybackTarget: StateFlow<PlaybackTarget?> = _activePlaybackTarget.asStateFlow()

    private val _playbackClipId = MutableStateFlow<String?>(null)
    val playbackClipId: StateFlow<String?> = _playbackClipId.asStateFlow()

    private val _isSeparating = MutableStateFlow(false)
    val isSeparating: StateFlow<Boolean> = _isSeparating.asStateFlow()

    private val _separationProgress = MutableStateFlow<DemucsSeparationProgress?>(null)
    val separationProgress: StateFlow<DemucsSeparationProgress?> = _separationProgress.asStateFlow()

    private val _isExtractingAudio = MutableStateFlow(false)
    val isExtractingAudio: StateFlow<Boolean> = _isExtractingAudio.asStateFlow()

    private val _extractionPercent = MutableStateFlow(0)
    val extractionPercent: StateFlow<Int> = _extractionPercent.asStateFlow()

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _countdownNumber = MutableStateFlow<Int?>(null)
    val countdownNumber: StateFlow<Int?> = _countdownNumber.asStateFlow()

    private val _liveMicDecibels = MutableStateFlow(-60.0f)
    val liveMicDecibels: StateFlow<Float> = _liveMicDecibels.asStateFlow()

    private val _liveMicPeak = MutableStateFlow(0.0f)
    val liveMicPeak: StateFlow<Float> = _liveMicPeak.asStateFlow()

    private val _auditionMode = MutableStateFlow(StudioAuditionMode.FULL_DUB_MIX)
    val auditionMode: StateFlow<StudioAuditionMode> = _auditionMode.asStateFlow()

    private val _backingGainDb = MutableStateFlow(0.0f)
    val backingGainDb: StateFlow<Float> = _backingGainDb.asStateFlow()

    private val _vadSensitivity = MutableStateFlow(0.6f)
    val vadSensitivity: StateFlow<Float> = _vadSensitivity.asStateFlow()

    private val _exportProgress = MutableStateFlow<MuxerExportProgress?>(null)
    val exportProgress: StateFlow<MuxerExportProgress?> = _exportProgress.asStateFlow()

    private val _isExporting = MutableStateFlow(false)
    val isExporting: StateFlow<Boolean> = _isExporting.asStateFlow()

    private val _exportedFilePath = MutableStateFlow<String?>(null)
    val exportedFilePath: StateFlow<String?> = _exportedFilePath.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    // Waveform envelope data
    private val _originalWaveformPeaks = MutableStateFlow(FloatArray(0))
    val originalWaveformPeaks: StateFlow<FloatArray> = _originalWaveformPeaks.asStateFlow()

    private val _vocalsWaveformPeaks = MutableStateFlow(FloatArray(0))
    val vocalsWaveformPeaks: StateFlow<FloatArray> = _vocalsWaveformPeaks.asStateFlow()

    private val _backingWaveformPeaks = MutableStateFlow(FloatArray(0))
    val backingWaveformPeaks: StateFlow<FloatArray> = _backingWaveformPeaks.asStateFlow()

    private val _activeTakePeaks = MutableStateFlow(FloatArray(0))
    val activeTakePeaks: StateFlow<FloatArray> = _activeTakePeaks.asStateFlow()

    private var playbackSyncJob: Job? = null
    private var mixerJob: Job? = null
    private var exportJob: Job? = null

    fun setModelPreset(preset: ModelPreset) {
        modelManager.setModelPreset(preset)
    }

    fun setKaraokeMode(mode: KaraokeMode) {
        modelManager.setKaraokeMode(mode)
    }

    fun setExecutionBackend(backend: ExecutionBackend) {
        modelManager.setExecutionBackend(backend)
    }

    fun downloadModel(preset: ModelPreset = modelManager.activePreset) {
        viewModelScope.launch {
            modelManager.downloadModel(preset)
        }
    }

    fun sideloadModel(uri: Uri) {
        viewModelScope.launch {
            modelManager.sideloadModel(uri)
        }
    }

    init {
        // Observe ExoPlayer playback state
        exoPlayer.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                _isPlaying.value = playing || mixerEngine.isPlaying()
                if (playing && !mixerEngine.isPlaying()) {
                    startPlayheadSyncLoop()
                } else if (!playing && !mixerEngine.isPlaying()) {
                    stopPlayheadSyncLoop()
                }
            }
        })
    }

    fun ingestVideo(videoUri: Uri, title: String, durationMs: Long, width: Int, height: Int) {
        viewModelScope.launch {
            try {
                _isExtractingAudio.value = true
                _extractionPercent.value = 0
                val project = repository.createProject(
                    title = title,
                    videoUri = videoUri.toString(),
                    videoDurationMs = durationMs,
                    width = width,
                    height = height
                )
                _activeProject.value = project

                // Setup ExoPlayer
                withContext(Dispatchers.Main) {
                    exoPlayer.setMediaItem(MediaItem.fromUri(videoUri))
                    exoPlayer.prepare()
                }

                // Extract audio via MediaExtractor & MediaCodec to 44.1kHz stereo PCM
                val pcmFile = File(project.originalPcmPath!!)
                val result = audioExtractionEngine.extractAndDecodeAudio(videoUri, pcmFile) { pct, _ ->
                    _extractionPercent.value = pct
                }

                _originalWaveformPeaks.value = result.waveformPeaks
                val updatedProj = project.copy(
                    status = ProjectEntity.STATUS_AUDIO_EXTRACTED,
                    videoDurationMs = result.durationMs
                )
                repository.updateProject(updatedProj)
                _activeProject.value = updatedProj

                // Refresh clips
                loadClips(updatedProj.id)
            } catch (e: Exception) {
                _errorMessage.value = "Video extraction failed: ${e.message}"
            } finally {
                _isExtractingAudio.value = false
            }
        }
    }

    fun startDemucsSeparation() {
        val proj = _activeProject.value ?: return
        val pcmPath = proj.originalPcmPath ?: return
        val pcmFile = File(pcmPath)
        val modelFile = modelManager.getModelFile()

        if (!modelFile.exists() || modelFile.length() < 50_000_000L) {
            _errorMessage.value = "Demucs ONNX model not found or incomplete (<50MB). Download or sideload it first."
            return
        }

        viewModelScope.launch {
            try {
                _isSeparating.value = true
                val vocalsFile = File(proj.vocalsWavPath!!)
                val backingFile = File(proj.backingWavPath!!)

                val result = demucsSeparationEngine.separateStems(
                    modelFile = modelFile,
                    inputPcmFile = pcmFile,
                    outputVocalsWav = vocalsFile,
                    outputBackingWav = backingFile,
                    preset = modelManager.activePreset,
                    karaokeMode = modelManager.activeKaraokeMode,
                    backend = modelManager.activeBackend
                ) { progress: DemucsSeparationProgress ->
                    _separationProgress.value = progress
                }

                _vocalsWaveformPeaks.value = result.vocalPeaks
                _backingWaveformPeaks.value = result.backingPeaks

                val updatedProj = proj.copy(status = ProjectEntity.STATUS_SEPARATED)
                repository.updateProject(updatedProj)
                _activeProject.value = updatedProj

                // Run automatic initial VAD detection to find dialogue lines
                runAutoVad()
            } catch (e: Exception) {
                _errorMessage.value = "Demucs v4 separation failed: ${e.message}"
            } finally {
                _isSeparating.value = false
            }
        }
    }

    fun runAutoVad() {
        val proj = _activeProject.value ?: return
        val vocalsFile = proj.vocalsWavPath?.let { File(it) }
        val pcmFile = proj.originalPcmPath?.let { File(it) }

        val targetAudioFile: File
        val isWav: Boolean

        if (vocalsFile != null && vocalsFile.exists() && vocalsFile.length() > 44L) {
            targetAudioFile = vocalsFile
            isWav = true
        } else if (pcmFile != null && pcmFile.exists() && pcmFile.length() > 0L) {
            targetAudioFile = pcmFile
            isWav = false
        } else {
            _errorMessage.value = "No audio available for VAD. Extract video audio or separate vocal stems first."
            return
        }

        viewModelScope.launch {
            try {
                val detected = vadEngine.detectDialogueClips(
                    audioFile = targetAudioFile,
                    isWavFile = isWav,
                    sensitivity = _vadSensitivity.value
                )

                if (detected.isNotEmpty()) {
                    val entities = detected.map { d ->
                        ClipEntity(
                            id = d.id,
                            projectId = proj.id,
                            clipIndex = d.clipIndex,
                            startMs = d.startMs,
                            endMs = d.endMs,
                            speakerName = d.speakerName,
                            scriptText = d.scriptText
                        )
                    }
                    // Cleanly replace old clips in database without duplicate entries
                    repository.replaceClipsForProject(proj.id, entities)
                    _clips.value = entities
                    _activeClip.value = entities.first()
                    _takes.value = emptyList()
                    _activeTake.value = null
                    selectClip(entities.first().id)
                } else {
                    _errorMessage.value = "VAD did not detect dialogue cues at current sensitivity. Try increasing sensitivity."
                }
            } catch (e: Exception) {
                _errorMessage.value = "VAD Detection error: ${e.message}"
            }
        }
    }

    private fun loadClips(projectId: String) {
        viewModelScope.launch {
            val clipsList = repository.getClipsForProjectDirect(projectId)
            _clips.value = clipsList
            if (clipsList.isNotEmpty()) {
                val currentActiveId = _activeClip.value?.id
                val matching = clipsList.firstOrNull { it.id == currentActiveId }
                if (matching != null) {
                    _activeClip.value = matching
                } else if (_activeClip.value == null) {
                    selectClip(clipsList.first().id)
                }
            } else {
                _activeClip.value = null
            }
        }
    }

    fun selectClip(clipId: String) {
        val clip = _clips.value.firstOrNull { it.id == clipId } ?: return
        _activeClip.value = clip
        seekTo(clip.startMs)
        loadTakesForClip(clip.id)
    }

    fun updateClipTiming(clipId: String, startMs: Long, endMs: Long) {
        val clip = _clips.value.firstOrNull { it.id == clipId } ?: return
        val validStart = startMs.coerceAtLeast(0L)
        val validEnd = max(validStart + 50L, endMs)
        val updated = clip.copy(startMs = validStart, endMs = validEnd)
        updateClip(updated)
    }

    fun nudgeClipTiming(clipId: String, deltaStartMs: Long, deltaEndMs: Long) {
        val clip = _clips.value.firstOrNull { it.id == clipId } ?: return
        val newStart = (clip.startMs + deltaStartMs).coerceAtLeast(0L)
        val newEnd = max(newStart + 50L, clip.endMs + deltaEndMs)
        val updated = clip.copy(startMs = newStart, endMs = newEnd)
        updateClip(updated)
    }

    fun nextClip() {
        val currentClips = _clips.value
        val current = _activeClip.value ?: return
        val idx = currentClips.indexOfFirst { it.id == current.id }
        if (idx in 0 until currentClips.size - 1) {
            selectClip(currentClips[idx + 1].id)
        }
    }

    fun previousClip() {
        val currentClips = _clips.value
        val current = _activeClip.value ?: return
        val idx = currentClips.indexOfFirst { it.id == current.id }
        if (idx > 0) {
            selectClip(currentClips[idx - 1].id)
        }
    }

    /**
     * Slices the currently selected clip at the current playhead position.
     * The selected clip's end timing is updated to the split point.
     * The new split span is inserted into the list directly following the selected clip,
     * maintaining synchronous sequence and re-indexing all subsequent spans.
     */
    fun sliceClipAtPlayhead() {
        val active = _activeClip.value ?: return
        val proj = _activeProject.value ?: return
        val playhead = _playheadMs.value

        viewModelScope.launch {
            val currentClips = repository.getClipsForProjectDirect(proj.id).toMutableList()
            val targetIdx = currentClips.indexOfFirst { it.id == active.id }
            if (targetIdx == -1) return@launch

            // Clamp split point safely inside selected clip
            val splitMs = if (playhead in (active.startMs + 50L)..(active.endMs - 50L)) {
                playhead
            } else {
                (active.startMs + active.endMs) / 2L
            }

            val originalEndMs = active.endMs
            val updatedFirst = active.copy(endMs = splitMs)
            currentClips[targetIdx] = updatedFirst

            val newClipId = UUID.randomUUID().toString()
            val newSecond = ClipEntity(
                id = newClipId,
                projectId = proj.id,
                clipIndex = updatedFirst.clipIndex + 1,
                startMs = splitMs,
                endMs = originalEndMs,
                speakerName = active.speakerName,
                scriptText = "",
                activeTakeId = null
            )

            // Add the split new span to the list following the selected clip
            currentClips.add(targetIdx + 1, newSecond)

            // Re-index all clips sequentially
            val reindexedClips = currentClips.mapIndexed { index, clip ->
                clip.copy(clipIndex = index + 1)
            }

            repository.replaceClipsForProject(proj.id, reindexedClips)
            _clips.value = reindexedClips
            _activeClip.value = reindexedClips[targetIdx + 1]
            loadTakesForClip(newClipId)
        }
    }

    /**
     * Reorders a clip up (-1) or down (+1) in the span list, maintaining
     * correct 1-based sequential indices and persisting the new order.
     */
    fun moveClip(clipId: String, delta: Int) {
        val proj = _activeProject.value ?: return
        viewModelScope.launch {
            val currentClips = repository.getClipsForProjectDirect(proj.id).toMutableList()
            val currentIndex = currentClips.indexOfFirst { it.id == clipId }
            if (currentIndex == -1) return@launch
            val newIndex = (currentIndex + delta).coerceIn(0, currentClips.size - 1)
            if (newIndex == currentIndex) return@launch

            val item = currentClips.removeAt(currentIndex)
            currentClips.add(newIndex, item)

            val reindexedClips = currentClips.mapIndexed { index, clip ->
                clip.copy(clipIndex = index + 1)
            }

            repository.replaceClipsForProject(proj.id, reindexedClips)
            _clips.value = reindexedClips
            _activeClip.value = reindexedClips.firstOrNull { it.id == clipId }
        }
    }

    fun addManualClip(startMs: Long, endMs: Long) {
        val proj = _activeProject.value ?: return
        viewModelScope.launch {
            val nextIdx = (_clips.value.maxOfOrNull { it.clipIndex } ?: 0) + 1
            val clip = ClipEntity(
                id = UUID.randomUUID().toString(),
                projectId = proj.id,
                clipIndex = nextIdx,
                startMs = startMs,
                endMs = endMs,
                speakerName = "Speaker 1",
                scriptText = ""
            )
            repository.insertClips(listOf(clip))
            loadClips(proj.id)
            selectClip(clip.id)
        }
    }

    fun updateClip(clip: ClipEntity) {
        viewModelScope.launch {
            repository.updateClip(clip)
            _activeClip.value = clip
            loadClips(clip.projectId)
        }
    }

    fun deleteClip(clip: ClipEntity) {
        viewModelScope.launch {
            repository.deleteClip(clip)
            loadClips(clip.projectId)
            _activeClip.value = _clips.value.firstOrNull()
        }
    }

    private fun loadTakesForClip(clipId: String) {
        viewModelScope.launch {
            val takesList = repository.getTakesForClipDirect(clipId)
            _takes.value = takesList
            val active = takesList.firstOrNull { it.id == _activeClip.value?.activeTakeId }
                ?: takesList.lastOrNull()
            _activeTake.value = active
        }
    }

    fun startPreRollAndRecord() {
        val clip = _activeClip.value ?: return
        val proj = _activeProject.value ?: return

        viewModelScope.launch {
            // Seek 1.5s prior to clip for context
            val cueStart = max(0L, clip.startMs - 1500L)
            seekTo(cueStart)

            // 3-second pre-roll countdown
            for (count in 3 downTo 1) {
                _countdownNumber.value = count
                delay(1000)
            }
            _countdownNumber.value = null

            // Start hardware AudioRecord
            _isRecording.value = true
            val takeId = UUID.randomUUID().toString()
            val takesDir = repository.getTakesDir(proj.id)
            val takePcmFile = File(takesDir, "take_${takeId}.pcm")

            // Play backing audio simultaneously during recording so actor hears context
            launch(Dispatchers.IO) {
                mixerEngine.playMix(
                    backingWavFile = proj.backingWavPath?.let { File(it) },
                    originalVocalsWavFile = null,
                    takes = emptyList(),
                    startPositionMs = clip.startMs,
                    endPositionMs = clip.endMs + 500L,
                    auditionMode = StudioAuditionMode.BACKING_ONLY,
                    onPlayheadUpdate = { _playheadMs.value = it },
                    onPlaybackFinished = {}
                )
            }

            try {
                val recordResult = recordingEngine.recordTake(
                    outputFile = takePcmFile,
                    onRmsDecibels = { _liveMicDecibels.value = it },
                    onPeakSample = { _liveMicPeak.value = it }
                )

                _activeTakePeaks.value = recordResult.peaks
                val nextTakeNumber = (_takes.value.maxOfOrNull { it.takeNumber } ?: 0) + 1
                val newTake = TakeEntity(
                    id = takeId,
                    clipId = clip.id,
                    takeNumber = nextTakeNumber,
                    pcmFilePath = recordResult.pcmFile.absolutePath,
                    durationMs = recordResult.durationMs,
                    gainDb = 0f,
                    timeOffsetMs = 0L
                )
                repository.insertTake(newTake)

                // Set as active take on clip
                val updatedClip = clip.copy(activeTakeId = newTake.id)
                repository.updateClip(updatedClip)
                _activeClip.value = updatedClip

                loadTakesForClip(clip.id)
            } catch (e: Exception) {
                _errorMessage.value = "Recording error: ${e.message}"
            } finally {
                _isRecording.value = false
                _liveMicDecibels.value = -60f
                _liveMicPeak.value = 0f
            }
        }
    }

    fun stopRecordTake() {
        recordingEngine.stopRecording()
        mixerEngine.stop()
    }

    fun selectActiveTake(takeId: String) {
        val clip = _activeClip.value ?: return
        viewModelScope.launch {
            val updatedClip = clip.copy(activeTakeId = takeId)
            repository.updateClip(updatedClip)
            _activeClip.value = updatedClip
            val take = _takes.value.firstOrNull { it.id == takeId }
            _activeTake.value = take
        }
    }

    fun updateTakeGain(gainDb: Float) {
        val take = _activeTake.value ?: return
        viewModelScope.launch {
            val updated = take.copy(gainDb = gainDb)
            repository.updateTake(updated)
            _activeTake.value = updated
            loadTakesForClip(take.clipId)
        }
    }

    fun updateTakeSlipOffset(offsetMs: Long) {
        val take = _activeTake.value ?: return
        viewModelScope.launch {
            val updated = take.copy(timeOffsetMs = offsetMs)
            repository.updateTake(updated)
            _activeTake.value = updated
            loadTakesForClip(take.clipId)
        }
    }

    fun deleteTake(take: TakeEntity) {
        viewModelScope.launch {
            repository.deleteTake(take)
            loadTakesForClip(take.clipId)
        }
    }

    fun setAuditionMode(mode: StudioAuditionMode) {
        _auditionMode.value = mode
        if (mixerEngine.isPlaying()) {
            togglePlayMixer()
        }
    }

    fun setVadSensitivity(sensitivity: Float) {
        _vadSensitivity.value = sensitivity
    }

    fun setBackingGain(gainDb: Float) {
        _backingGainDb.value = gainDb
    }

    fun playClipVad(clip: ClipEntity) {
        if (_isPlaying.value && _playbackClipId.value == clip.id && _activePlaybackTarget.value == PlaybackTarget.CLIP_VAD_ORIGINAL) {
            stopPlayback()
            return
        }
        _activeClip.value = clip
        loadTakesForClip(clip.id)

        val startMs = clip.startMs
        val endMs = clip.endMs
        val currentPlayhead = _playheadMs.value

        val playStart = if (currentPlayhead >= endMs || currentPlayhead < startMs) {
            startMs
        } else {
            currentPlayhead
        }

        _playbackClipId.value = clip.id
        _activePlaybackTarget.value = PlaybackTarget.CLIP_VAD_ORIGINAL

        playRange(
            startMs = playStart,
            endMs = endMs,
            auditionMode = StudioAuditionMode.ORIGINAL_WITH_RESIDUAL,
            clipId = clip.id
        )
    }

    fun playOriginalWithResidual(clip: ClipEntity? = _activeClip.value) {
        if (_isPlaying.value && _activePlaybackTarget.value == PlaybackTarget.CLIP_ORIGINAL_VOCAL) {
            stopPlayback()
            return
        }
        val targetClip = clip ?: _clips.value.firstOrNull()
        if (targetClip == null) {
            _errorMessage.value = "Select or create a dialogue cue first."
            return
        }
        _activeClip.value = targetClip
        loadTakesForClip(targetClip.id)

        val startMs = targetClip.startMs
        val endMs = targetClip.endMs
        val currentPlayhead = _playheadMs.value

        val playStart = if (currentPlayhead >= endMs || currentPlayhead < startMs) {
            startMs
        } else {
            currentPlayhead
        }

        _playbackClipId.value = targetClip.id
        _activePlaybackTarget.value = PlaybackTarget.CLIP_ORIGINAL_VOCAL

        playRange(
            startMs = playStart,
            endMs = endMs,
            auditionMode = StudioAuditionMode.ORIGINAL_WITH_RESIDUAL,
            clipId = targetClip.id
        )
    }

    fun playRecordedWithResidual(clip: ClipEntity? = _activeClip.value) {
        if (_isPlaying.value && _activePlaybackTarget.value == PlaybackTarget.CLIP_RECORDED_VOCAL) {
            stopPlayback()
            return
        }
        val targetClip = clip ?: _clips.value.firstOrNull()
        if (targetClip == null) {
            _errorMessage.value = "Select or create a dialogue cue first."
            return
        }
        _activeClip.value = targetClip
        loadTakesForClip(targetClip.id)

        val activeTake = _activeTake.value ?: _takes.value.firstOrNull { it.clipId == targetClip.id }
        if (activeTake == null) {
            _errorMessage.value = "No take recorded for Line #${targetClip.clipIndex} yet. Record a take first."
            return
        }

        val startMs = targetClip.startMs
        val endMs = targetClip.endMs
        val currentPlayhead = _playheadMs.value

        val playStart = if (currentPlayhead >= endMs || currentPlayhead < startMs) {
            startMs
        } else {
            currentPlayhead
        }

        _playbackClipId.value = targetClip.id
        _activePlaybackTarget.value = PlaybackTarget.CLIP_RECORDED_VOCAL

        playRange(
            startMs = playStart,
            endMs = endMs,
            auditionMode = StudioAuditionMode.RECORDED_WITH_RESIDUAL,
            clipId = targetClip.id
        )
    }

    fun stopPlayback() {
        mixerEngine.stop()
        stopPlayheadSyncLoop()
        viewModelScope.launch(Dispatchers.Main) {
            exoPlayer.pause()
            _isPlaying.value = false
        }
    }

    fun playRange(
        startMs: Long,
        endMs: Long,
        auditionMode: StudioAuditionMode,
        clipId: String?
    ) {
        val proj = _activeProject.value ?: return
        stopPlayback()
        _playheadMs.value = startMs

        val hasStems = proj.backingWavPath?.let { File(it).exists() } == true

        if (!hasStems) {
            // No stems separated yet: play directly through ExoPlayer with bounds
            viewModelScope.launch(Dispatchers.Main) {
                exoPlayer.volume = 1.0f
                exoPlayer.seekTo(startMs)
                exoPlayer.play()
                _isPlaying.value = true
                startPlayheadSyncLoopWithBound(endMs)
            }
            return
        }

        // Stems exist: mixerEngine plays synchronized stems, ExoPlayer plays video muted in sync
        viewModelScope.launch(Dispatchers.IO) {
            val allClips = repository.getClipsForProjectDirect(proj.id)
            val allTakes = repository.getAllTakesForProjectDirect(proj.id)

            val mixTracks = allTakes.mapNotNull { take ->
                val parentClip = allClips.firstOrNull { it.id == take.clipId }
                if (parentClip != null && (take.id == _activeTake.value?.id || parentClip.activeTakeId == take.id)) {
                    MixTakeTrack(
                        takeId = take.id,
                        pcmFile = File(take.pcmFilePath),
                        startMs = parentClip.startMs,
                        durationMs = take.durationMs,
                        gainDb = take.gainDb,
                        timeOffsetMs = take.timeOffsetMs
                    )
                } else null
            }

            withContext(Dispatchers.Main) {
                exoPlayer.volume = 0.0f
                exoPlayer.seekTo(startMs)
                exoPlayer.play()
                _isPlaying.value = true
            }

            mixerEngine.playMix(
                backingWavFile = proj.backingWavPath?.let { File(it) },
                originalVocalsWavFile = proj.vocalsWavPath?.let { File(it) },
                takes = mixTracks,
                startPositionMs = startMs,
                endPositionMs = endMs,
                auditionMode = auditionMode,
                activeTakeId = _activeTake.value?.id,
                backingGainDb = _backingGainDb.value,
                onPlayheadUpdate = { ms ->
                    _playheadMs.value = ms
                },
                onPlaybackFinished = {
                    viewModelScope.launch(Dispatchers.Main) {
                        exoPlayer.pause()
                        _playheadMs.value = endMs
                        _isPlaying.value = false
                    }
                }
            )
        }
    }

    fun togglePlay() {
        if (mixerEngine.isPlaying() || exoPlayer.isPlaying) {
            stopPlayback()
        } else {
            val clip = _activeClip.value
            if (clip != null) {
                val currentPlayhead = _playheadMs.value
                val playStart = if (currentPlayhead >= clip.endMs || currentPlayhead < clip.startMs) {
                    clip.startMs
                } else {
                    currentPlayhead
                }
                _playbackClipId.value = clip.id
                _activePlaybackTarget.value = when (_auditionMode.value) {
                    StudioAuditionMode.ORIGINAL_WITH_RESIDUAL -> PlaybackTarget.CLIP_ORIGINAL_VOCAL
                    StudioAuditionMode.RECORDED_WITH_RESIDUAL -> PlaybackTarget.CLIP_RECORDED_VOCAL
                    else -> PlaybackTarget.CLIP_VAD_ORIGINAL
                }
                playRange(
                    startMs = playStart,
                    endMs = clip.endMs,
                    auditionMode = _auditionMode.value,
                    clipId = clip.id
                )
            } else {
                togglePlayMixer()
            }
        }
    }

    private fun togglePlayMixer() {
        val proj = _activeProject.value ?: return
        val currentPlayhead = _playheadMs.value
        _activePlaybackTarget.value = PlaybackTarget.FULL_PROJECT

        val hasStems = proj.backingWavPath?.let { File(it).exists() } == true

        if (!hasStems) {
            // No stems separated yet: play directly through ExoPlayer
            stopPlayheadSyncLoop()
            viewModelScope.launch(Dispatchers.Main) {
                exoPlayer.volume = 1.0f
                exoPlayer.seekTo(currentPlayhead)
                exoPlayer.play()
                _isPlaying.value = true
                startPlayheadSyncLoop()
            }
            return
        }

        // Stems exist: mixerEngine drives audio, ExoPlayer video is muted
        stopPlayheadSyncLoop()
        viewModelScope.launch(Dispatchers.IO) {
            val allClips = repository.getClipsForProjectDirect(proj.id)
            val allTakes = repository.getAllTakesForProjectDirect(proj.id)

            val mixTracks = allTakes.mapNotNull { take ->
                val parentClip = allClips.firstOrNull { it.id == take.clipId }
                if (parentClip != null && (parentClip.activeTakeId == take.id || _auditionMode.value == StudioAuditionMode.SOLO_ACTIVE_TAKE)) {
                    MixTakeTrack(
                        takeId = take.id,
                        pcmFile = File(take.pcmFilePath),
                        startMs = parentClip.startMs,
                        durationMs = take.durationMs,
                        gainDb = take.gainDb,
                        timeOffsetMs = take.timeOffsetMs
                    )
                } else null
            }

            withContext(Dispatchers.Main) {
                exoPlayer.volume = 0.0f
                exoPlayer.seekTo(currentPlayhead)
                exoPlayer.play()
                _isPlaying.value = true
            }

            mixerEngine.playMix(
                backingWavFile = proj.backingWavPath?.let { File(it) },
                originalVocalsWavFile = proj.vocalsWavPath?.let { File(it) },
                takes = mixTracks,
                startPositionMs = currentPlayhead,
                auditionMode = _auditionMode.value,
                activeTakeId = _activeTake.value?.id,
                backingGainDb = _backingGainDb.value,
                onPlayheadUpdate = { ms ->
                    _playheadMs.value = ms
                },
                onPlaybackFinished = {
                    viewModelScope.launch(Dispatchers.Main) {
                        exoPlayer.pause()
                        _isPlaying.value = false
                    }
                }
            )
        }
    }

    fun seekTo(positionMs: Long) {
        _playheadMs.value = positionMs
        viewModelScope.launch(Dispatchers.Main) {
            exoPlayer.seekTo(positionMs)
        }
        if (mixerEngine.isPlaying() || exoPlayer.isPlaying) {
            val clip = _activeClip.value
            if (clip != null && _activePlaybackTarget.value != PlaybackTarget.FULL_PROJECT && positionMs in clip.startMs until clip.endMs) {
                playRange(
                    startMs = positionMs,
                    endMs = clip.endMs,
                    auditionMode = _auditionMode.value,
                    clipId = clip.id
                )
            } else {
                togglePlayMixer()
            }
        }
    }

    private fun startPlayheadSyncLoop() {
        playbackSyncJob?.cancel()
        playbackSyncJob = viewModelScope.launch(Dispatchers.Main) {
            while (exoPlayer.isPlaying) {
                _playheadMs.value = exoPlayer.currentPosition
                delay(40)
            }
        }
    }

    private fun startPlayheadSyncLoopWithBound(endMs: Long) {
        playbackSyncJob?.cancel()
        playbackSyncJob = viewModelScope.launch(Dispatchers.Main) {
            while (exoPlayer.isPlaying) {
                val current = exoPlayer.currentPosition
                _playheadMs.value = current
                if (current >= endMs) {
                    exoPlayer.pause()
                    _playheadMs.value = endMs
                    _isPlaying.value = false
                    break
                }
                delay(40)
            }
        }
    }

    private fun stopPlayheadSyncLoop() {
        playbackSyncJob?.cancel()
        playbackSyncJob = null
    }

    fun exportMaster() {
        val proj = _activeProject.value ?: return
        exportJob?.cancel()
        exportJob = viewModelScope.launch {
            try {
                _isExporting.value = true
                val exportsDir = repository.getExportsDir(proj.id)
                val outMp4 = File(exportsDir, "DubStage_${proj.title.replace(" ", "_")}.mp4")
                val outSrt = File(exportsDir, "DubStage_${proj.title.replace(" ", "_")}.srt")
                val outVtt = File(exportsDir, "DubStage_${proj.title.replace(" ", "_")}.vtt")

                val allClips = repository.getClipsForProjectDirect(proj.id)
                val allTakes = repository.getAllTakesForProjectDirect(proj.id)

                val mixTracks = allTakes.mapNotNull { take ->
                    val parentClip = allClips.firstOrNull { it.id == take.clipId }
                    if (parentClip != null && parentClip.activeTakeId == take.id) {
                        MixTakeTrack(
                            takeId = take.id,
                            pcmFile = File(take.pcmFilePath),
                            startMs = parentClip.startMs,
                            durationMs = take.durationMs,
                            gainDb = take.gainDb,
                            timeOffsetMs = take.timeOffsetMs
                        )
                    } else null
                }

                // Export SRT and WebVTT
                mp4Muxer.exportSrt(allClips, outSrt)
                mp4Muxer.exportVtt(allClips, outVtt)

                // Export MP4: uses original vocal audio for scenes with no recorded vocal
                val finalFile = mp4Muxer.exportDubbedVideo(
                    sourceVideoUri = Uri.parse(proj.videoUri),
                    backingWavFile = proj.backingWavPath?.let { File(it) },
                    originalVocalsWavFile = proj.vocalsWavPath?.let { File(it) } ?: proj.originalPcmPath?.let { File(it) },
                    takes = mixTracks,
                    totalDurationMs = proj.videoDurationMs,
                    outputMp4File = outMp4
                ) { progress ->
                    _exportProgress.value = progress
                }

                _exportedFilePath.value = finalFile.absolutePath
            } catch (e: CancellationException) {
                _errorMessage.value = "Export was cancelled."
            } catch (e: Exception) {
                _errorMessage.value = "Export failed: ${e.message}"
            } finally {
                _isExporting.value = false
            }
        }
    }

    fun cancelExport() {
        exportJob?.cancel()
        exportJob = null
        _isExporting.value = false
        _exportProgress.value = null
        _errorMessage.value = "Export cancelled."
    }

    fun dismissExportDialog() {
        _exportedFilePath.value = null
        _exportProgress.value = null
    }

    fun dismissError() {
        _errorMessage.value = null
    }

    override fun onCleared() {
        super.onCleared()
        mixerEngine.stop()
        recordingEngine.stopRecording()
        exoPlayer.release()
    }
}
