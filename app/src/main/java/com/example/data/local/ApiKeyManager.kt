package com.example.data.local

import android.content.Context
import com.example.BuildConfig

class ApiKeyManager(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    /**
     * Returns the permanently saved API key from device storage if set,
     * otherwise falls back to BuildConfig.GEMINI_API_KEY (from .env / Secrets).
     */
    fun getActiveApiKey(): String {
        val savedKey = prefs.getString(KEY_SAVED_GEMINI_API_KEY, null)?.trim()
        if (!savedKey.isNullOrBlank()) {
            return savedKey
        }
        val buildConfigKey = BuildConfig.GEMINI_API_KEY.trim()
        return if (isValidKey(buildConfigKey)) buildConfigKey else ""
    }

    fun hasValidApiKey(): Boolean {
        return isValidKey(getActiveApiKey())
    }

    fun savePermanentApiKey(apiKey: String) {
        prefs.edit()
            .putString(KEY_SAVED_GEMINI_API_KEY, apiKey.trim())
            .apply()
    }

    fun clearSavedApiKey() {
        prefs.edit()
            .remove(KEY_SAVED_GEMINI_API_KEY)
            .apply()
    }

    fun hasCustomSavedKey(): Boolean {
        return !prefs.getString(KEY_SAVED_GEMINI_API_KEY, null)?.trim().isNullOrBlank()
    }

    fun getMaskedKeyPreview(): String {
        val key = getActiveApiKey()
        if (key.length <= 8) return if (key.isEmpty()) "Not Set" else "••••••••"
        return "${key.take(4)}••••••••${key.takeLast(4)}"
    }

    private fun isValidKey(key: String?): Boolean {
        if (key.isNullOrBlank()) return false
        return key != "MY_GEMINI_API_KEY" &&
            key != "null" &&
            key != "YOUR_API_KEY" &&
            !key.startsWith("PLACEHOLDER")
    }

    companion object {
        private const val PREFS_NAME = "veo_studio_permanent_config"
        private const val KEY_SAVED_GEMINI_API_KEY = "permanent_gemini_api_key"
    }
}
