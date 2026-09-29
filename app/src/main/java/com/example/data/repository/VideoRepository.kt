package com.example.data.repository

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.example.data.local.ApiKeyManager
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
import com.example.ui.theme.AppLanguage
import java.io.File
import java.io.FileInputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import retrofit2.HttpException

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
    private val apiKeyManager = ApiKeyManager(appContext)
    val allGenerations: Flow<List<VideoGenerationEntity>> = dao.getAllGenerations()

    suspend fun deleteGeneration(id: Long) {
        val existing = dao.getGenerationById(id)
        existing?.localFilePath?.let { path ->
            runCatching { File(path).delete() }
        }
        dao.deleteGenerationById(id)
    }

    fun isApiKeyConfigured(): Boolean = apiKeyManager.hasValidApiKey()

    fun getActiveApiKey(): String = apiKeyManager.getActiveApiKey()

    fun getSavedBackupKey(): String = apiKeyManager.getSavedBackupKey()

    fun getMaskedKeyPreview(): String = apiKeyManager.getMaskedKeyPreview()

    fun savePermanentApiKey(apiKey: String, backupKey: String = "") {
        apiKeyManager.savePermanentApiKey(apiKey, backupKey)
    }

    fun clearSavedApiKey() {
        apiKeyManager.clearSavedApiKey()
    }

    fun getSavedLanguage(): AppLanguage = apiKeyManager.getSavedLanguage()

    fun saveLanguage(language: AppLanguage) {
        apiKeyManager.saveLanguage(language)
    }

    fun isAutoFallback429Enabled(): Boolean = apiKeyManager.isAutoFallback429Enabled()

    fun setAutoFallback429Enabled(enabled: Boolean) {
        apiKeyManager.setAutoFallback429Enabled(enabled)
    }

    /**
     * Executes the full Veo 3.1 generation + 10-second polling + file download flow,
     * with Smart HTTP 429 Exponential Backoff, Multi-Key Rotation, and Model Fallback.
     */
    suspend fun generateAndDownloadVideo(
        title: String,
        prompt: String,
        model: String,
        aspectRatio: String,
        resolution: String,
        downloadPath: String,
        autoFallback429: Boolean,
        onLog: (String) -> Unit,
        onPollProgress: (pollCount: Int, secondsUntilNextPoll: Int, elapsedSeconds: Int, operationName: String) -> Unit
    ): Result<VideoGenerationEntity> = withContext(Dispatchers.IO) {
        val availableKeys = apiKeyManager.getAvailableApiKeys()
        if (availableKeys.isEmpty()) {
            val missingKeyMsg =
                "کلید API تنظیم نشده است. لطفاً کلید Gemini API خود را در بخش «تنظیم دائمی کلید API» بالای صفحه وارد و ذخیره کنید."
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
            // Candidate models in priority order when autoFallback429 is enabled
            val candidateModels = if (autoFallback429) {
                listOf(
                    model,
                    "veo-3.1-fast-generate-preview",
                    "veo-3.1-generate-preview"
                ).distinct()
            } else {
                listOf(model)
            }

            var activeKey = availableKeys.first()
            var usedModel = model
            var initialJson: JsonObject? = null
            var lastError: Exception? = null
            var elapsedSeconds = 0

            outerLoop@ for ((modelIndex, candidateModel) in candidateModels.withIndex()) {
                for ((keyIndex, candidateKey) in availableKeys.withIndex()) {
                    val maxAttempts = if (autoFallback429) 2 else 1
                    for (attempt in 1..maxAttempts) {
                        try {
                            usedModel = candidateModel
                            activeKey = candidateKey
                            onLog("ارسال درخواست ساخت ویدیو به مدل \"$candidateModel\" (تلاش $attempt)...")

                            initialJson = startVeoOperation(
                                model = candidateModel,
                                apiKey = candidateKey,
                                prompt = prompt,
                                aspectRatio = aspectRatio,
                                resolution = resolution,
                                onLog = onLog
                            )
                            break@outerLoop
                        } catch (e: Exception) {
                            if (e is CancellationException) throw e
                            lastError = e
                            val is429 = isHttp429Error(e)

                            if (!is429) {
                                throw e
                            }

                            val detailed429 = extractHttpErrorDetail(e)
                            onLog("هشدار HTTP 429 (محدودیت نرخ درخواست): $detailed429")

                            // If we have another API key, rotate immediately!
                            if (keyIndex + 1 < availableKeys.size) {
                                onLog("چرخش خودکار به کلید API پشتیبان شماره ${keyIndex + 2}...")
                                break
                            }

                            // Otherwise wait with exponential backoff before retrying or switching model
                            val hasMoreAttempts = attempt < maxAttempts || (modelIndex + 1 < candidateModels.size)
                            if (hasMoreAttempts && autoFallback429) {
                                val waitSecs = 12 * attempt
                                val nextStepLabel = if (attempt < maxAttempts) {
                                    "تلاش مجدد روی $candidateModel"
                                } else {
                                    "جایگزینی خودکار با مدل ${candidateModels[modelIndex + 1]}"
                                }
                                onLog("مکث هوشمند $waitSecs ثانیه‌ای برای رفع محدودیت HTTP 429 ($nextStepLabel)...")
                                for (rem in waitSecs downTo 1) {
                                    onPollProgress(0, rem, elapsedSeconds, "HTTP 429 Cooldown ($rem s)")
                                    delay(1_000L)
                                    elapsedSeconds += 1
                                }
                            }
                        }
                    }
                }
            }

            val resolvedJson = initialJson ?: throw buildFriendlyError(lastError)

            var operation: ParsedVeoOperation = VeoOperationParser.parse(resolvedJson)
            if (operation.errorMessage != null) {
                throw IllegalStateException(operation.errorMessage)
            }
            if (operation.name.isBlank() && !operation.done) {
                throw IllegalStateException("Veo API did not return an operation name: ${operation.rawJson.take(200)}")
            }

            onLog("Operation started ($usedModel): ${operation.name.ifBlank { "inline-operation" }}")
            var pollCount = 0

            currentEntity = currentEntity.copy(
                model = usedModel,
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

                try {
                    val polledJson = VeoRetrofitClient.service.getVideosOperation(
                        operationName = cleanOpPath,
                        apiKey = activeKey
                    )
                    operation = VeoOperationParser.parse(polledJson)
                } catch (pollErr: Exception) {
                    if (pollErr is CancellationException) throw pollErr
                    if (isHttp429Error(pollErr)) {
                        onLog("هشدار HTTP 429 حین استعلام وضعیت؛ مکث ۱۵ ثانیه‌ای و ادامه خودکار...")
                        for (rem in 15 downTo 1) {
                            onPollProgress(pollCount, rem, elapsedSeconds, "Cooldown 429 (${rem}s)")
                            delay(1_000L)
                            elapsedSeconds += 1
                        }
                        continue
                    } else {
                        throw buildFriendlyError(pollErr)
                    }
                }

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
            val primaryFile = File(videosDir, sanitizedFileName)
            val uniqueTakeFile = File(videosDir, "take_${entityId}_$sanitizedFileName")

            val downloadResult = VeoRetrofitClient.downloadGeneratedVideo(
                parsedOp = operation,
                apiKey = activeKey,
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
            val friendly = buildFriendlyError(e)
            val errMsg = friendly.message ?: "Unknown error during Veo video generation"
            onLog("ERROR: $errMsg")
            val failed = currentEntity.copy(
                status = VideoGenerationEntity.STATUS_FAILED,
                errorMessage = errMsg
            )
            dao.updateGeneration(failed)
            Result.failure(friendly)
        }
    }

    private suspend fun startVeoOperation(
        model: String,
        apiKey: String,
        prompt: String,
        aspectRatio: String,
        resolution: String,
        onLog: (String) -> Unit
    ): JsonObject {
        return try {
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
            // IMPORTANT: Do NOT immediately call fallback if primaryError is HTTP 429 (Too Many Requests),
            // because firing a second request immediately aggravates the 429 rate limit!
            if (isHttp429Error(primaryError)) {
                throw primaryError
            }
            // Only fall back to :predictLongRunning if :generateVideos returned HTTP 400 / 404 schema error
            onLog("Endpoint :generateVideos returned non-429 (${primaryError.message?.take(45)}), trying :predictLongRunning...")
            delay(1_500L)
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
    }

    private fun isHttp429Error(e: Throwable?): Boolean {
        if (e == null) return false
        if (e is HttpException && e.code() == 429) return true
        val msg = e.message.orEmpty()
        return msg.contains("429") ||
            msg.contains("RESOURCE_EXHAUSTED", ignoreCase = true) ||
            msg.contains("Too Many Requests", ignoreCase = true)
    }

    private fun extractHttpErrorDetail(e: Throwable?): String {
        if (e is HttpException) {
            val rawBody = runCatching { e.response()?.errorBody()?.string() }.getOrNull().orEmpty()
            if (rawBody.isNotBlank()) {
                val msgMatch = Regex(""""message"\s*:\s*"([^"]+)"""").find(rawBody)
                if (msgMatch != null) {
                    return msgMatch.groupValues[1]
                }
                return rawBody.take(160)
            }
        }
        return e?.message ?: "HTTP 429 Too Many Requests"
    }

    private fun buildFriendlyError(e: Exception?): Exception {
        if (e == null) return IllegalStateException("خطای نامشخص در ارتباط با سرور Veo")
        if (isHttp429Error(e)) {
            val serverDetail = extractHttpErrorDetail(e)
            return IllegalStateException(
                "خطای HTTP 429 (اتمام سهمیه یا محدودیت تعداد درخواست در دقیقه): " +
                    "سهمیه مدل Veo روی این کلید API پر شده است ($serverDetail). " +
                    "راه‌حل: ۱) چند ثانیه صبر کنید یا مدل veo-3.1-fast را انتخاب نمایید. " +
                    "۲) در تنظیمات کلید API، یک کلید پشتیبان اضافه کنید یا از فعال بودن طرح Paid/Billing روی پروژه Google AI Studio برای مدل ویدیویی Veo اطمینان حاصل فرمایید."
            )
        }
        return e
    }

    /**
     * Uses gemini-3.5-flash to polish a scene description & spoken dialogue prompt for Veo 3.1.
     */
    suspend fun polishDialoguePrompt(rawPrompt: String): Result<String> = withContext(Dispatchers.IO) {
        val activeKey = apiKeyManager.getActiveApiKey()
        if (!apiKeyManager.hasValidApiKey()) {
            return@withContext Result.failure(
                IllegalStateException("لطفاً ابتدا کلید Gemini API را در بخش «تنظیم دائمی کلید API» وارد و ذخیره کنید.")
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
                apiKey = activeKey,
                request = request
            )
            val text = response.candidates.firstOrNull()?.content?.parts?.firstOrNull()?.text?.trim()
            if (text.isNullOrBlank()) {
                Result.failure(IllegalStateException("Empty response from gemini-3.5-flash"))
            } else {
                Result.success(text)
            }
        } catch (e: Exception) {
            Result.failure(buildFriendlyError(e))
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
