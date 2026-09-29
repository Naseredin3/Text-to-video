package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.ApiKeyManager
import com.example.data.remote.VeoOperationParser
import com.example.data.repository.VideoRepository
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read app name and verify default cryptic wall dialogue prompt`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Veo Dialogue Studio", appName)
        assertTrue(VideoRepository.DEFAULT_CRYPTIC_WALL_PROMPT.contains("This must be it. That's the secret code."))
        assertTrue(VideoRepository.DEFAULT_CRYPTIC_WALL_PROMPT.contains("What did you find?"))
    }

    @Test
    fun `save and retrieve permanent Gemini API key via ApiKeyManager`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val manager = ApiKeyManager(context)
        manager.clearSavedApiKey()

        manager.savePermanentApiKey("AIzaSyTestPermanentKey123456789")
        assertTrue(manager.hasValidApiKey())
        assertEquals("AIzaSyTestPermanentKey123456789", manager.getActiveApiKey())

        manager.clearSavedApiKey()
        assertFalse(manager.hasCustomSavedKey())
    }

    @Test
    fun `parse completed Veo operation JSON with generatedVideos array`() {
        val sampleJson = """
            {
              "name": "models/veo-3.1-generate-preview/operations/op-98765",
              "done": true,
              "response": {
                "generatedVideos": [
                  {
                    "video": {
                      "uri": "https://generativelanguage.googleapis.com/v1beta/files/vid123:download?alt=media",
                      "mimeType": "video/mp4"
                    }
                  }
                ]
              }
            }
        """.trimIndent()
        val parsed = VeoOperationParser.parse(Json.parseToJsonElement(sampleJson).jsonObject)
        assertTrue(parsed.done)
        assertEquals("models/veo-3.1-generate-preview/operations/op-98765", parsed.name)
        assertEquals(
            "https://generativelanguage.googleapis.com/v1beta/files/vid123:download?alt=media",
            parsed.videoUri
        )
    }
}
