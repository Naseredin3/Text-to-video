package com.example.data.local

import android.content.Context
import com.example.BuildConfig
import com.example.ui.theme.AppLanguage

class ApiKeyManager(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    /**
     * Returns the primary active API key from permanent device storage if set,
     * otherwise falls back to BuildConfig.GEMINI_API_KEY (from .env / Secrets).
     */
    fun getActiveApiKey(): String {
        val allKeys = getAvailableApiKeys()
        return allKeys.firstOrNull() ?: ""
    }

    /**
     * Returns all valid configured API keys (Primary + optional Backup keys + BuildConfig key)
     * so VideoRepository can automatically rotate keys when HTTP 429 occurs.
     */
    fun getAvailableApiKeys(): List<String> {
        val result = mutableListOf<String>()
        val savedPrimary = prefs.getString(KEY_SAVED_GEMINI_API_KEY, null)
        val savedBackup = prefs.getString(KEY_BACKUP_GEMINI_API_KEY, null)

        savedPrimary?.split(",", "\n", " ")
            ?.map { it.trim() }
            ?.filter { isValidKey(it) }
            ?.forEach { if (!result.contains(it)) result.add(it) }

        savedBackup?.split(",", "\n", " ")
            ?.map { it.trim() }
            ?.filter { isValidKey(it) }
            ?.forEach { if (!result.contains(it)) result.add(it) }

        val buildConfigKey = BuildConfig.GEMINI_API_KEY.trim()
        if (isValidKey(buildConfigKey) && !result.contains(buildConfigKey)) {
            result.add(buildConfigKey)
        }
        return result
    }

    fun hasValidApiKey(): Boolean {
        return getAvailableApiKeys().isNotEmpty()
    }

    fun savePermanentApiKey(apiKey: String, backupKey: String = "") {
        val editor = prefs.edit()
            .putString(KEY_SAVED_GEMINI_API_KEY, apiKey.trim())
        if (backupKey.isNotBlank()) {
            editor.putString(KEY_BACKUP_GEMINI_API_KEY, backupKey.trim())
        }
        editor.apply()
    }

    fun getSavedBackupKey(): String {
        return prefs.getString(KEY_BACKUP_GEMINI_API_KEY, "") ?: ""
    }

    fun clearSavedApiKey() {
        prefs.edit()
            .remove(KEY_SAVED_GEMINI_API_KEY)
            .remove(KEY_BACKUP_GEMINI_API_KEY)
            .apply()
    }

    fun hasCustomSavedKey(): Boolean {
        return !prefs.getString(KEY_SAVED_GEMINI_API_KEY, null)?.trim().isNullOrBlank()
    }

    fun getMaskedKeyPreview(): String {
        val keys = getAvailableApiKeys()
        val key = keys.firstOrNull() ?: return "Not Set"
        val baseMask = if (key.length <= 8) "••••••••" else "${key.take(4)}••••${key.takeLast(4)}"
        return if (keys.size > 1) "$baseMask (+${keys.size - 1})" else baseMask
    }

    fun getSavedLanguage(): AppLanguage {
        val code = prefs.getString(KEY_APP_LANGUAGE, AppLanguage.FA.code)
        return AppLanguage.fromCode(code)
    }

    fun saveLanguage(language: AppLanguage) {
        prefs.edit()
            .putString(KEY_APP_LANGUAGE, language.code)
            .apply()
    }

    fun isAutoFallback429Enabled(): Boolean {
        return prefs.getBoolean(KEY_AUTO_FALLBACK_429, true)
    }

    fun setAutoFallback429Enabled(enabled: Boolean) {
        prefs.edit()
            .putBoolean(KEY_AUTO_FALLBACK_429, enabled)
            .apply()
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
        private const val KEY_BACKUP_GEMINI_API_KEY = "permanent_backup_gemini_api_key"
        private const val KEY_APP_LANGUAGE = "app_language_code"
        private const val KEY_AUTO_FALLBACK_429 = "auto_fallback_http_429"
    }
}
