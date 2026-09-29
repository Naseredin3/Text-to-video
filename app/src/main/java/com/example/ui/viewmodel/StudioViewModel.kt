package com.example.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.local.VideoGenerationEntity
import com.example.data.repository.DialogueCue
import com.example.data.repository.ScenePreset
import com.example.data.repository.VideoRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class StudioTab {
    DIRECTOR,
    VAULT,
    SDK_TELEMETRY
}

data class TelemetryLogEntry(
    val id: String = UUID.randomUUID().toString(),
    val timestamp: String,
    val message: String,
    val isError: Boolean = false,
    val isSuccess: Boolean = false
)

data class StudioUiState(
    val currentTab: StudioTab = StudioTab.DIRECTOR,
    val sceneTitle: String = "Cryptic Wall Discovery",
    val visualSetting: String = "A close up of two people staring at a cryptic drawing on a wall, torchlight flickering.",
    val dialogueCues: List<DialogueCue> = VideoRepository.DEFAULT_DIALOGUE_CUES,
    val prompt: String = VideoRepository.DEFAULT_CRYPTIC_WALL_PROMPT,
    val selectedModel: String = "veo-3.1-generate-preview",
    val aspectRatio: String = "16:9",
    val resolution: String = "1080p",
    val downloadPath: String = "dialogue_example.mp4",
    val isGenerating: Boolean = false,
    val isPolishingPrompt: Boolean = false,
    val activeOperationName: String = "",
    val pollCount: Int = 0,
    val secondsUntilNextPoll: Int = 0,
    val elapsedSeconds: Int = 0,
    val consoleLogs: List<TelemetryLogEntry> = listOf(
        TelemetryLogEntry(
            timestamp = "00:00:00",
            message = "Veo 3.1 Dialogue Studio ready. Default model: veo-3.1-generate-preview"
        ),
        TelemetryLogEntry(
            timestamp = "00:00:00",
            message = "Loaded prompt: Cryptic Wall Discovery -> dialogue_example.mp4"
        )
    ),
    val selectedTake: VideoGenerationEntity? = null,
    val bannerMessage: String? = null,
    val errorBanner: String? = null,
    val isApiKeyConfigured: Boolean = true
)

class StudioViewModel(
    private val repository: VideoRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        StudioUiState(isApiKeyConfigured = repository.isApiKeyConfigured())
    )
    val uiState: StateFlow<StudioUiState> = _uiState.asStateFlow()

    val savedGenerations: StateFlow<List<VideoGenerationEntity>> = repository.allGenerations
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    private var generationJob: Job? = null
    private val timeFormatter = SimpleDateFormat("HH:mm:ss", Locale.US)

    init {
        viewModelScope.launch {
            repository.allGenerations.collect { list ->
                val currentSelected = _uiState.value.selectedTake
                if (currentSelected == null) {
                    val latestCompleted = list.firstOrNull {
                        it.status == VideoGenerationEntity.STATUS_COMPLETED && !it.localFilePath.isNullOrBlank()
                    }
                    if (latestCompleted != null) {
                        _uiState.update { it.copy(selectedTake = latestCompleted) }
                    }
                } else {
                    val updatedMatch = list.firstOrNull { it.id == currentSelected.id }
                    if (updatedMatch != null) {
                        _uiState.update { it.copy(selectedTake = updatedMatch) }
                    }
                }
            }
        }
    }

    fun selectTab(tab: StudioTab) {
        _uiState.update { it.copy(currentTab = tab) }
    }

    fun updateSceneTitle(title: String) {
        _uiState.update { it.copy(sceneTitle = title) }
    }

    fun updatePrompt(newPrompt: String) {
        _uiState.update { it.copy(prompt = newPrompt, errorBanner = null) }
    }

    fun updateVisualSetting(setting: String) {
        _uiState.update { state ->
            val rebuilt = buildFullPrompt(setting, state.dialogueCues)
            state.copy(visualSetting = setting, prompt = rebuilt)
        }
    }

    fun addDialogueCue(speaker: String, delivery: String, line: String) {
        if (speaker.isBlank() || line.isBlank()) return
        val newCue = DialogueCue(
            id = UUID.randomUUID().toString(),
            speaker = speaker.trim(),
            delivery = delivery.trim().ifBlank { "says" },
            line = line.trim()
        )
        _uiState.update { state ->
            val updatedCues = state.dialogueCues + newCue
            val rebuilt = buildFullPrompt(state.visualSetting, updatedCues)
            state.copy(dialogueCues = updatedCues, prompt = rebuilt)
        }
        appendLog("Added dialogue cue for '${newCue.speaker}'")
    }

    fun removeDialogueCue(cueId: String) {
        _uiState.update { state ->
            val updatedCues = state.dialogueCues.filterNot { it.id == cueId }
            val rebuilt = buildFullPrompt(state.visualSetting, updatedCues)
            state.copy(dialogueCues = updatedCues, prompt = rebuilt)
        }
    }

    fun appendCameraKeyword(keyword: String) {
        _uiState.update { state ->
            val currentPrompt = state.prompt.trim()
            val updated = if (currentPrompt.contains(keyword, ignoreCase = true)) {
                currentPrompt
            } else {
                "$currentPrompt\nCamera & Atmosphere: $keyword."
            }
            state.copy(prompt = updated)
        }
    }

    fun updateModel(model: String) {
        _uiState.update { it.copy(selectedModel = model) }
        appendLog("Switched Veo model to $model")
    }

    fun updateAspectRatio(aspectRatio: String) {
        _uiState.update { it.copy(aspectRatio = aspectRatio) }
    }

    fun updateResolution(resolution: String) {
        _uiState.update { it.copy(resolution = resolution) }
    }

    fun updateDownloadPath(path: String) {
        _uiState.update { it.copy(downloadPath = path) }
    }

    fun applyPreset(preset: ScenePreset) {
        val extractedVisual = preset.prompt.lineSequence().firstOrNull()?.trim() ?: preset.cameraCue
        val extractedCues = if (preset.id == "cryptic_wall") {
            VideoRepository.DEFAULT_DIALOGUE_CUES
        } else {
            parseDialogueCuesFromPrompt(preset.prompt)
        }
        _uiState.update {
            it.copy(
                sceneTitle = preset.title,
                visualSetting = extractedVisual,
                dialogueCues = extractedCues,
                prompt = preset.prompt,
                downloadPath = preset.defaultFileName,
                errorBanner = null,
                bannerMessage = "Loaded preset: ${preset.title}"
            )
        }
        appendLog("Loaded preset '${preset.title}' -> ${preset.defaultFileName}")
    }

    fun restoreDefaultCrypticWallPrompt() {
        val defaultPreset = VideoRepository.PRESET_SCENES.first()
        applyPreset(defaultPreset)
    }

    fun dismissBanners() {
        _uiState.update { it.copy(bannerMessage = null, errorBanner = null) }
    }

    fun polishPromptWithAi() {
        val currentPrompt = _uiState.value.prompt.trim()
        if (currentPrompt.isBlank() || _uiState.value.isPolishingPrompt) return

        _uiState.update { it.copy(isPolishingPrompt = true, errorBanner = null, bannerMessage = null) }
        appendLog("Polishing dialogue script with gemini-3.5-flash...")

        viewModelScope.launch {
            val result = repository.polishDialoguePrompt(currentPrompt)
            result.fold(
                onSuccess = { polished ->
                    _uiState.update {
                        it.copy(
                            isPolishingPrompt = false,
                            prompt = polished,
                            bannerMessage = "Dialogue script enhanced with cinematic framing & vocal delivery cues."
                        )
                    }
                    appendLog("Prompt polished successfully via gemini-3.5-flash.", isSuccess = true)
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            isPolishingPrompt = false,
                            errorBanner = err.message ?: "Failed to polish prompt"
                        )
                    }
                    appendLog("Prompt polish failed: ${err.message}", isError = true)
                }
            )
        }
    }

    fun generateVideo() {
        val state = _uiState.value
        if (state.isGenerating) return
        if (state.prompt.isBlank()) {
            _uiState.update { it.copy(errorBanner = "Please enter a scene & dialogue prompt before generating.") }
            return
        }

        generationJob?.cancel()
        _uiState.update {
            it.copy(
                isGenerating = true,
                pollCount = 0,
                secondsUntilNextPoll = 10,
                elapsedSeconds = 0,
                activeOperationName = "Starting operation...",
                errorBanner = null,
                bannerMessage = null
            )
        }

        generationJob = viewModelScope.launch {
            try {
                val result = repository.generateAndDownloadVideo(
                    title = state.sceneTitle,
                    prompt = state.prompt,
                    model = state.selectedModel,
                    aspectRatio = state.aspectRatio,
                    resolution = state.resolution,
                    downloadPath = state.downloadPath,
                    onLog = { msg ->
                        val isErr = msg.startsWith("ERROR:")
                        val isOk = msg.startsWith("Generated video saved")
                        appendLog(msg, isError = isErr, isSuccess = isOk)
                    },
                    onPollProgress = { pollCount, secondsRemaining, elapsed, opName ->
                        _uiState.update { current ->
                            current.copy(
                                pollCount = pollCount,
                                secondsUntilNextPoll = secondsRemaining,
                                elapsedSeconds = elapsed,
                                activeOperationName = opName
                            )
                        }
                    }
                )

                result.fold(
                    onSuccess = { completedTake ->
                        _uiState.update {
                            it.copy(
                                isGenerating = false,
                                secondsUntilNextPoll = 0,
                                selectedTake = completedTake,
                                bannerMessage = "Generated video saved to ${completedTake.downloadPath}"
                            )
                        }
                    },
                    onFailure = { error ->
                        _uiState.update {
                            it.copy(
                                isGenerating = false,
                                secondsUntilNextPoll = 0,
                                errorBanner = error.message ?: "Video generation failed"
                            )
                        }
                    }
                )
            } catch (ce: CancellationException) {
                _uiState.update {
                    it.copy(
                        isGenerating = false,
                        secondsUntilNextPoll = 0,
                        bannerMessage = "Video generation polling cancelled."
                    )
                }
            }
        }
    }

    fun cancelGeneration() {
        generationJob?.cancel()
        generationJob = null
    }

    fun selectTakeForPlayback(take: VideoGenerationEntity) {
        _uiState.update {
            it.copy(
                selectedTake = take,
                currentTab = StudioTab.DIRECTOR,
                bannerMessage = "Loaded take: ${take.title} (${take.downloadPath})"
            )
        }
    }

    fun loadTakePromptIntoEditor(take: VideoGenerationEntity) {
        _uiState.update {
            it.copy(
                sceneTitle = take.title,
                prompt = take.prompt,
                selectedModel = take.model,
                aspectRatio = take.aspectRatio,
                resolution = take.resolution,
                downloadPath = take.downloadPath,
                currentTab = StudioTab.DIRECTOR,
                bannerMessage = "Restored prompt from '${take.title}' into Director Studio."
            )
        }
    }

    fun deleteTake(takeId: Long) {
        viewModelScope.launch {
            repository.deleteGeneration(takeId)
            _uiState.update { state ->
                val clearedSelected = if (state.selectedTake?.id == takeId) null else state.selectedTake
                state.copy(selectedTake = clearedSelected)
            }
            appendLog("Deleted take #$takeId from local vault.")
        }
    }

    fun exportTakeToMovies(take: VideoGenerationEntity) {
        val localPath = take.localFilePath
        if (localPath.isNullOrBlank()) {
            _uiState.update { it.copy(errorBanner = "No local MP4 file available for this take.") }
            return
        }
        viewModelScope.launch {
            val res = repository.exportVideoToDeviceMovies(localPath, take.downloadPath)
            res.fold(
                onSuccess = { publicPath ->
                    _uiState.update {
                        it.copy(bannerMessage = "Exported to device gallery: $publicPath")
                    }
                    appendLog("Exported MP4 to $publicPath", isSuccess = true)
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(errorBanner = "Export failed: ${err.message}")
                    }
                    appendLog("Export failed: ${err.message}", isError = true)
                }
            )
        }
    }

    fun clearLogs() {
        _uiState.update {
            it.copy(
                consoleLogs = listOf(
                    TelemetryLogEntry(
                        timestamp = timeFormatter.format(Date()),
                        message = "Console cleared. Ready for next Veo 3.1 operation."
                    )
                )
            )
        }
    }

    private fun appendLog(message: String, isError: Boolean = false, isSuccess: Boolean = false) {
        val stamp = timeFormatter.format(Date())
        _uiState.update { state ->
            val updated = (state.consoleLogs + TelemetryLogEntry(
                timestamp = stamp,
                message = message,
                isError = isError,
                isSuccess = isSuccess
            )).takeLast(80)
            state.copy(consoleLogs = updated)
        }
    }

    private fun buildFullPrompt(visualSetting: String, cues: List<DialogueCue>): String {
        val dialoguePart = cues.joinToString(separator = " ") { cue ->
            "${cue.speaker} ${cue.delivery}, '${cue.line}'"
        }
        return if (dialoguePart.isBlank()) {
            visualSetting.trim()
        } else {
            "${visualSetting.trim()}\n$dialoguePart"
        }
    }

    private fun parseDialogueCuesFromPrompt(prompt: String): List<DialogueCue> {
        val regex = Regex("""([A-Za-z][A-Za-z\s-]{1,25}?)\s+([^',.]+?),\s*'([^']+)'""")
        val matches = regex.findAll(prompt).toList()
        if (matches.isEmpty()) return emptyList()
        return matches.mapIndexed { index, matchResult ->
            DialogueCue(
                id = "parsed_$index",
                speaker = matchResult.groupValues[1].trim(),
                delivery = matchResult.groupValues[2].trim(),
                line = matchResult.groupValues[3].trim()
            )
        }
    }

    companion object {
        fun provideFactory(context: Context): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    val db = AppDatabase.getInstance(context.applicationContext)
                    val repo = VideoRepository(db.videoGenerationDao(), context.applicationContext)
                    return StudioViewModel(repo) as T
                }
            }
    }
}
