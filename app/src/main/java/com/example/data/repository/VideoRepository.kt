package com.example.data.repository

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.example.BuildConfig
import com.example.data.local.VideoGenerationDao
import com.example.data.local.VideoGenerationEntity
import com.example.data.remote.Content
import com.example.data.remote.GenerateContentRequest
import com.example.data.remote.GenerateVideosRequest
import com.example.data.remote.GenerationConfig
import com.example.data.remote.Part
import com.example.data.remote.ParsedVeoOperation
import com.example.data.remote.PredictLongRunningRequest
import com.example.data.remote.VeoConfig
import com.example.data.remote.VeoOperationParser
import com.example.data.remote.VeoParameters
import com.example.data.remote.VeoPromptInstance
import com.example.data.remote.VeoRetrofitClient
import java.io.File
import java.io.FileInputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

data class ScenePreset(
    val id: String,
    val title: String,
    val badge: String,
    val defaultFileName: String,
    val prompt: String,
    val cameraCue: String,
    val audioCue: String
)

data class DialogueCue(
    val id: String,
    val speaker: String,
    val delivery: String,
    val line: String
)

class VideoRepository(
    private val dao: VideoGenerationDao,
    private val appContext: Context
) {
    val allGenerations: Flow<List<VideoGenerationEntity>> = dao.getAllGenerations()

    suspend fun deleteGeneration(id: Long) {
        val existing = dao.getGenerationById(id)
        existing?.localFilePath?.let { path ->
            runCatching { File(path).delete() }
        }
        dao.deleteGenerationById(id)
    }

    fun isApiKeyConfigured(): Boolean {
        val key = BuildConfig.GEMINI_API_KEY
        return key.isNotBlank() && key != "MY_GEMINI_API_KEY" && key != "null"
    }

    /**
     * Executes the full Veo 3.1 generation + 10-second polling + file download flow,
     * matching the exact @google/genai workflow:
     * 1. ai.models.generateVideos({ model: "veo-3.1-generate-preview", prompt })
     * 2. while (!operation.done) { delay(10000); ai.operations.getVideosOperation({ operation }) }
     * 3. ai.files.download({ file: operation.response.generatedVideos[0].video, downloadPath })
     */
    suspend fun generateAndDownloadVideo(
        title: String,
        prompt: String,
        model: String,
        aspectRatio: String,
        resolution: String,
        downloadPath: String,
        onLog: (String) -> Unit,
        onPollProgress: (pollCount: Int, secondsUntilNextPoll: Int, elapsedSeconds: Int, operationName: String) -> Unit
    ): Result<VideoGenerationEntity> = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (!isApiKeyConfigured()) {
            val missingKeyMsg =
                "GEMINI_API_KEY is not configured. Please add your Gemini API key in the AI Studio Secrets panel."
            onLog("ERROR: $missingKeyMsg")
            return@withContext Result.failure(IllegalStateException(missingKeyMsg))
        }

        val sanitizedFileName = sanitizeFileName(downloadPath)
        val initialEntity = VideoGenerationEntity(
            title = title.ifBlank { "Scene Take" },
            prompt = prompt,
            model = model,
            aspectRatio = aspectRatio,
            resolution = resolution,
            downloadPath = sanitizedFileName,
            status = VideoGenerationEntity.STATUS_QUEUED
        )
        val entityId = dao.insertGeneration(initialEntity)
        var currentEntity = initialEntity.copy(id = entityId)

        try {
            onLog("Initializing Veo request -> model: \"$model\"")
            onLog("Prompt (${prompt.length} chars) queued for generation...")

            val initialJson = try {
                val request = GenerateVideosRequest(
                    prompt = prompt,
                    config = VeoConfig(
                        numberOfVideos = 1,
                        resolution = resolution,
                        aspectRatio = aspectRatio
                    )
                )
                VeoRetrofitClient.service.generateVideos(
                    model = model,
                    apiKey = apiKey,
                    request = request
                )
            } catch (primaryError: Exception) {
                if (primaryError is CancellationException) throw primaryError
                onLog("Note: :generateVideos returned (${primaryError.message?.take(60)}), trying :predictLongRunning...")
                val fallbackReq = PredictLongRunningRequest(
                    instances = listOf(VeoPromptInstance(prompt = prompt)),
                    parameters = VeoParameters(aspectRatio = aspectRatio, sampleCount = 1)
                )
                VeoRetrofitClient.service.predictLongRunning(
                    model = model,
                    apiKey = apiKey,
                    request = fallbackReq
                )
            }

            var operation: ParsedVeoOperation = VeoOperationParser.parse(initialJson)
            if (operation.errorMessage != null) {
                throw IllegalStateException(operation.errorMessage)
            }
            if (operation.name.isBlank() && !operation.done) {
                throw IllegalStateException("Veo API did not return an operation name: ${operation.rawJson.take(200)}")
            }

            onLog("Operation started: ${operation.name.ifBlank { "inline-operation" }}")
            var pollCount = 0
            var elapsedSeconds = 0

            currentEntity = currentEntity.copy(
                operationName = operation.name,
                status = VideoGenerationEntity.STATUS_POLLING
            )
            dao.updateGeneration(currentEntity)

            // Poll operation status every 10 seconds until operation.done is true
            while (!operation.done) {
                onLog("Waiting for video generation to complete...")
                for (remaining in 10 downTo 1) {
                    onPollProgress(pollCount, remaining, elapsedSeconds, operation.name)
                    delay(1_000L)
                    elapsedSeconds += 1
                }

                pollCount += 1
                onPollProgress(pollCount, 0, elapsedSeconds, operation.name)
                val cleanOpPath = operation.name.removePrefix("/")
                val polledJson = VeoRetrofitClient.service.getVideosOperation(
                    operationName = cleanOpPath,
                    apiKey = apiKey
                )
                operation = VeoOperationParser.parse(polledJson)

                currentEntity = currentEntity.copy(
                    pollCount = pollCount,
                    elapsedSeconds = elapsedSeconds
                )
                dao.updateGeneration(currentEntity)

                if (operation.errorMessage != null) {
                    throw IllegalStateException(operation.errorMessage)
                }
            }

            onLog("Operation completed after $pollCount poll(s) (${elapsedSeconds}s). Downloading video...")
            currentEntity = currentEntity.copy(
                status = VideoGenerationEntity.STATUS_DOWNLOADING,
                remoteVideoUri = operation.videoUri
            )
            dao.updateGeneration(currentEntity)

            // Save to local app videos folder using the requested filename (e.g., dialogue_example.mp4)
            val videosDir = File(appContext.filesDir, "veo_videos").apply { mkdirs() }
            // Keep both the exact filename requested (e.g., dialogue_example.mp4) and a unique copy per take
            val primaryFile = File(videosDir, sanitizedFileName)
            val uniqueTakeFile = File(videosDir, "take_${entityId}_$sanitizedFileName")

            val downloadResult = VeoRetrofitClient.downloadGeneratedVideo(
                parsedOp = operation,
                apiKey = apiKey,
                targetFile = uniqueTakeFile
            )
            val savedFile = downloadResult.getOrThrow()
            runCatching { savedFile.copyTo(primaryFile, overwrite = true) }

            onLog("Generated video saved to $sanitizedFileName (${savedFile.length() / 1024} KB)")

            val completedEntity = currentEntity.copy(
                status = VideoGenerationEntity.STATUS_COMPLETED,
                localFilePath = savedFile.absolutePath,
                remoteVideoUri = operation.videoUri,
                pollCount = pollCount,
                elapsedSeconds = elapsedSeconds,
                errorMessage = null
            )
            dao.updateGeneration(completedEntity)
            Result.success(completedEntity)
        } catch (ce: CancellationException) {
            onLog("Operation polling cancelled by user.")
            val cancelled = currentEntity.copy(
                status = VideoGenerationEntity.STATUS_CANCELLED,
                errorMessage = "Cancelled by user"
            )
            dao.updateGeneration(cancelled)
            throw ce
        } catch (e: Exception) {
            val errMsg = e.message ?: "Unknown error during Veo video generation"
            onLog("ERROR: $errMsg")
            val failed = currentEntity.copy(
                status = VideoGenerationEntity.STATUS_FAILED,
                errorMessage = errMsg
            )
            dao.updateGeneration(failed)
            Result.failure(e)
        }
    }

    /**
     * Uses gemini-3.5-flash to polish a scene description & spoken dialogue prompt for Veo 3.1.
     */
    suspend fun polishDialoguePrompt(rawPrompt: String): Result<String> = withContext(Dispatchers.IO) {
        if (!isApiKeyConfigured()) {
            return@withContext Result.failure(
                IllegalStateException("Configure GEMINI_API_KEY in the AI Studio Secrets panel to use AI Prompt Polishing.")
            )
        }
        try {
            val systemInstruction = Content(
                parts = listOf(
                    Part(
                        text = """
                        You are an expert cinematography & dialogue prompt director for Veo 3.1 (veo-3.1-generate-preview).
                        Rewrite the user's prompt into a concise, vivid 2-to-4 sentence video generation prompt that includes:
                        1. Specific camera framing and lighting cues (e.g., close up, flickering torchlight, shallow depth of field).
                        2. Expressive character actions and spoken dialogue enclosed in single quotes ('...') with clear vocal delivery verbs (e.g., murmurs, whispers excitedly, calls out).
                        Output ONLY the polished prompt text without markdown quotes or preamble.
                        """.trimIndent()
                    )
                )
            )
            val request = GenerateContentRequest(
                contents = listOf(Content(parts = listOf(Part(text = rawPrompt)))),
                generationConfig = GenerationConfig(temperature = 0.7f),
                systemInstruction = systemInstruction
            )
            val response = VeoRetrofitClient.service.generateContent(
                apiKey = BuildConfig.GEMINI_API_KEY,
                request = request
            )
            val text = response.candidates.firstOrNull()?.content?.parts?.firstOrNull()?.text?.trim()
            if (text.isNullOrBlank()) {
                Result.failure(IllegalStateException("Empty response from gemini-3.5-flash"))
            } else {
                Result.success(text)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Exports a downloaded MP4 file to the device's public Movies/VeoDialogueStudio directory via MediaStore.
     */
    suspend fun exportVideoToDeviceMovies(localFilePath: String, fileName: String): Result<String> =
        withContext(Dispatchers.IO) {
            try {
                val sourceFile = File(localFilePath)
                if (!sourceFile.exists()) {
                    return@withContext Result.failure(IllegalStateException("Local MP4 file not found at $localFilePath"))
                }
                val cleanName = sanitizeFileName(fileName)
                val resolver = appContext.contentResolver
                val contentValues = ContentValues().apply {
                    put(MediaStore.Video.Media.DISPLAY_NAME, cleanName)
                    put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        put(
                            MediaStore.Video.Media.RELATIVE_PATH,
                            "${Environment.DIRECTORY_MOVIES}/VeoDialogueStudio"
                        )
                        put(MediaStore.Video.Media.IS_PENDING, 1)
                    }
                }

                val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                } else {
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                }

                val itemUri = resolver.insert(collection, contentValues)
                    ?: return@withContext Result.failure(IllegalStateException("Could not create MediaStore entry"))

                resolver.openOutputStream(itemUri)?.use { outStream ->
                    FileInputStream(sourceFile).use { inStream ->
                        inStream.copyTo(outStream)
                    }
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val publishValues = ContentValues().apply {
                        put(MediaStore.Video.Media.IS_PENDING, 0)
                    }
                    resolver.update(itemUri, publishValues, null, null)
                }

                Result.success("Movies/VeoDialogueStudio/$cleanName")
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    private fun sanitizeFileName(input: String): String {
        val trimmed = input.trim().ifBlank { "dialogue_example.mp4" }
        val base = trimmed.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        return if (base.endsWith(".mp4", ignoreCase = true)) base else "$base.mp4"
    }

    companion object {
        // Exact prompt from the user's code snippet
        val DEFAULT_CRYPTIC_WALL_PROMPT = """
            A close up of two people staring at a cryptic drawing on a wall, torchlight flickering.
            A man murmurs, 'This must be it. That's the secret code.' The woman looks at him and whispering excitedly, 'What did you find?'
        """.trimIndent()

        val DEFAULT_DIALOGUE_CUES = listOf(
            DialogueCue(
                id = "cue_1",
                speaker = "A man",
                delivery = "murmurs",
                line = "This must be it. That's the secret code."
            ),
            DialogueCue(
                id = "cue_2",
                speaker = "The woman",
                delivery = "looks at him and whispering excitedly",
                line = "What did you find?"
            )
        )

        val PRESET_SCENES = listOf(
            ScenePreset(
                id = "cryptic_wall",
                title = "Cryptic Wall Discovery",
                badge = "Default SDK Scene",
                defaultFileName = "dialogue_example.mp4",
                prompt = DEFAULT_CRYPTIC_WALL_PROMPT,
                cameraCue = "Close up, flickering torchlight",
                audioCue = "Murmured & whispered dialogue in stone cavern"
            ),
            ScenePreset(
                id = "noir_cipher",
                title = "Rain-Slicked Rooftop Cipher",
                badge = "Neo-Noir Dialogue",
                defaultFileName = "rooftop_cipher.mp4",
                prompt = """
                    Medium two-shot on a rain-slicked neon rooftop at midnight, anamorphic lens flare.
                    A detective holds up a glowing data drive and says gravelly, 'They never meant for this frequency to leave the tower.' His partner steps into the light, replying urgently, 'Then broadcast it before the drones lock on.'
                """.trimIndent(),
                cameraCue = "Medium two-shot, anamorphic neon rain",
                audioCue = "Gravelly voice + urgent reply over rain"
            ),
            ScenePreset(
                id = "orbital_airlock",
                title = "Orbital Airlock Signal",
                badge = "Sci-Fi Suspense",
                defaultFileName = "orbital_signal.mp4",
                prompt = """
                    Tight over-the-shoulder shot inside a dimly lit spacecraft observation deck, blue telemetry reflections on helmet visors.
                    The commander points to the viewport and whispers tensely, 'That beacon has been silent for forty years.' The engineer exhales softly, 'It's not just pinging us—it's speaking our callsign.'
                """.trimIndent(),
                cameraCue = "Tight over-the-shoulder, visor reflections",
                audioCue = "Tense comms whisper + ambient hull hum"
            ),
            ScenePreset(
                id = "clockwork_observatory",
                title = "Clockwork Brass Observatory",
                badge = "Steampunk Mystery",
                defaultFileName = "observatory_take.mp4",
                prompt = """
                    Warm golden hour tracking shot through spinning brass astrolabe gears in a high glass dome.
                    An elderly cartographer taps the star chart and declares softly, 'The meridian shifted by three degrees last night.' His apprentice leans in and asks in awe, 'Does that mean the hidden archipelago is rising?'
                """.trimIndent(),
                cameraCue = "Golden hour tracking shot, brass gears",
                audioCue = "Soft declaration + awestruck question"
            )
        )
    }
}
