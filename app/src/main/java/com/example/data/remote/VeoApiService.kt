package com.example.data.remote

import android.util.Base64
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface VeoApiService {
    // Primary Veo endpoint as documented in gemini-api skill Example 11
    @POST("v1beta/models/{model}:generateVideos")
    suspend fun generateVideos(
        @Path("model") model: String,
        @Query("key") apiKey: String,
        @Body request: GenerateVideosRequest
    ): JsonObject

    // Fallback Veo long-running operation endpoint for REST compatibility
    @POST("v1beta/models/{model}:predictLongRunning")
    suspend fun predictLongRunning(
        @Path("model") model: String,
        @Query("key") apiKey: String,
        @Body request: PredictLongRunningRequest
    ): JsonObject

    // Poll operation status: matches ai.operations.getVideosOperation({ operation })
    @GET("v1beta/{operationName}")
    suspend fun getVideosOperation(
        @Path(value = "operationName", encoded = true) operationName: String,
        @Query("key") apiKey: String
    ): JsonObject

    // Gemini 3.5 Flash endpoint for dialogue script enhancement
    @POST("v1beta/models/gemini-3.5-flash:generateContent")
    suspend fun generateContent(
        @Query("key") apiKey: String,
        @Body request: GenerateContentRequest
    ): GenerateContentResponse
}

object VeoRetrofitClient {
    private const val BASE_URL = "https://generativelanguage.googleapis.com/"

    val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    val service: VeoApiService by lazy {
        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
        retrofit.create(VeoApiService::class.java)
    }

    /**
     * Downloads the generated video (matching `ai.files.download({ file, downloadPath })`)
     * either from a remote file URI or from inline base64 bytes, and saves it to targetFile.
     */
    suspend fun downloadGeneratedVideo(
        parsedOp: ParsedVeoOperation,
        apiKey: String,
        targetFile: File
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            targetFile.parentFile?.mkdirs()

            // Case 1: Inline base64 video bytes
            if (!parsedOp.videoBase64Bytes.isNullOrBlank()) {
                val bytes = Base64.decode(parsedOp.videoBase64Bytes, Base64.DEFAULT)
                FileOutputStream(targetFile).use { it.write(bytes) }
                return@withContext Result.success(targetFile)
            }

            // Case 2: Remote URI download (e.g., https://generativelanguage.googleapis.com/v1beta/files/...)
            val rawUri = parsedOp.videoUri
                ?: return@withContext Result.failure(
                    IllegalStateException("Operation completed, but no video URI or byte payload was returned.")
                )

            val downloadUrl = if (rawUri.contains("generativelanguage.googleapis.com") && !rawUri.contains("key=")) {
                val separator = if (rawUri.contains("?")) "&" else "?"
                "${rawUri}${separator}key=$apiKey"
            } else {
                rawUri
            }

            val request = Request.Builder()
                .url(downloadUrl)
                .header("x-goog-api-key", apiKey)
                .get()
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        IllegalStateException("Video download failed with HTTP ${response.code}: ${response.message}")
                    )
                }
                val body = response.body
                    ?: return@withContext Result.failure(IllegalStateException("Empty video response body"))

                body.byteStream().use { input ->
                    FileOutputStream(targetFile).use { output ->
                        input.copyTo(output)
                    }
                }
            }

            Result.success(targetFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
