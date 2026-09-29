package com.example.data.remote

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull

// --- Veo 3.1 Request & Config Models (Matches gemini-api skill Example 11) ---

@Serializable
data class GenerateVideosRequest(
    val prompt: String,
    val config: VeoConfig? = null
)

@Serializable
data class VeoConfig(
    val numberOfVideos: Int = 1,
    val resolution: String = "1080p",
    val aspectRatio: String = "16:9"
)

// Fallback structure for predictLongRunning endpoint compatibility
@Serializable
data class PredictLongRunningRequest(
    val instances: List<VeoPromptInstance>,
    val parameters: VeoParameters? = null
)

@Serializable
data class VeoPromptInstance(
    val prompt: String
)

@Serializable
data class VeoParameters(
    val aspectRatio: String = "16:9",
    val sampleCount: Int = 1
)

// --- Gemini 3.5 Flash Text Models (for Dialogue Prompt Polishing) ---

@Serializable
data class GenerateContentRequest(
    val contents: List<Content>,
    val generationConfig: GenerationConfig? = null,
    val systemInstruction: Content? = null
)

@Serializable
data class Content(
    val parts: List<Part>
)

@Serializable
data class Part(
    val text: String? = null
)

@Serializable
data class GenerationConfig(
    val temperature: Float? = null,
    val topP: Float? = null,
    val topK: Int? = null
)

@Serializable
data class GenerateContentResponse(
    val candidates: List<Candidate> = emptyList()
)

@Serializable
data class Candidate(
    val content: Content? = null
)

// --- Parsed Veo Operation Status ---

data class ParsedVeoOperation(
    val name: String,
    val done: Boolean,
    val errorMessage: String? = null,
    val videoUri: String? = null,
    val videoBase64Bytes: String? = null,
    val mimeType: String = "video/mp4",
    val rawJson: String = ""
)

object VeoOperationParser {
    private fun JsonElement?.asObj(): JsonObject? = this as? JsonObject
    private fun JsonElement?.asArr(): JsonArray? = this as? JsonArray
    private fun JsonElement?.asPrim(): JsonPrimitive? = this as? JsonPrimitive
    private fun JsonElement?.asStr(): String? = (this as? JsonPrimitive)?.content

    fun parse(jsonObject: JsonObject): ParsedVeoOperation {
        val name = jsonObject["name"].asStr() ?: ""
        val done = jsonObject["done"].asPrim()?.booleanOrNull ?: false

        val errorObj = jsonObject["error"].asObj()
        val errorMessage = if (errorObj != null) {
            val code = errorObj["code"].asPrim()?.intOrNull
            val msg = errorObj["message"].asStr() ?: "Operation failed"
            if (code != null) "API Error ($code): $msg" else msg
        } else {
            null
        }

        var videoUri: String? = null
        var videoBase64: String? = null
        var mimeType = "video/mp4"

        val responseObj = jsonObject["response"].asObj()
        if (responseObj != null) {
            // 1. Check SDK style: operation.response.generatedVideos[0].video
            val generatedVideos = responseObj["generatedVideos"].asArr()
            val firstGenVideo = generatedVideos?.firstOrNull().asObj()
            val videoObj1 = firstGenVideo?.get("video").asObj() ?: firstGenVideo

            // 2. Check REST predictLongRunning style: operation.response.generateVideoResponse.generatedSamples[0].video
            val genVideoResponse = responseObj["generateVideoResponse"].asObj()
            val generatedSamples = genVideoResponse?.get("generatedSamples").asArr()
                ?: responseObj["generatedSamples"].asArr()
                ?: responseObj["videos"].asArr()
            val firstSample = generatedSamples?.firstOrNull().asObj()
            val videoObj2 = firstSample?.get("video").asObj() ?: firstSample

            val targetVideoObj = videoObj1 ?: videoObj2
            if (targetVideoObj != null) {
                videoUri = targetVideoObj["uri"].asStr()
                    ?: targetVideoObj["videoUri"].asStr()
                    ?: targetVideoObj["fileUri"].asStr()
                videoBase64 = targetVideoObj["videoBytes"].asStr()
                    ?: targetVideoObj["bytesBase64Encoded"].asStr()
                mimeType = targetVideoObj["mimeType"].asStr() ?: "video/mp4"
            }
        }

        return ParsedVeoOperation(
            name = name,
            done = done,
            errorMessage = errorMessage,
            videoUri = videoUri,
            videoBase64Bytes = videoBase64,
            mimeType = mimeType,
            rawJson = jsonObject.toString()
        )
    }
}
