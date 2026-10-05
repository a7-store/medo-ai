package com.medo.aiassistant.utils

import android.content.Context
import android.content.SharedPreferences

class PrefsManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var apiKey: String
        get() = prefs.getString(KEY_API_KEY, "") ?: ""
        set(value) = prefs.edit().putString(KEY_API_KEY, value.trim()).apply()

    var lastSummaryText: String
        get() = prefs.getString(KEY_LAST_SUMMARY, "") ?: ""
        set(value) = prefs.edit().putString(KEY_LAST_SUMMARY, value).apply()

    var lastSummaryTimestamp: Long
        get() = prefs.getLong(KEY_LAST_SUMMARY_TIME, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_SUMMARY_TIME, value).apply()

    companion object {
        private const val PREFS_NAME = "medo_assistant_prefs"
        private const val KEY_API_KEY = "gemini_api_key"
        private const val KEY_LAST_SUMMARY = "last_summary_text"
        private const val KEY_LAST_SUMMARY_TIME = "last_summary_time"
    }
}
