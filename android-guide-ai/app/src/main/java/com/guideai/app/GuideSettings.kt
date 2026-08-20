package com.guideai.app

import android.content.Context

object GuideSettings {
    private const val PREFS = "guide_ai_settings"
    private const val LANGUAGE = "language"
    private const val VOICE = "voice_enabled"
    private const val ACTIVE = "active"

    fun language(context: Context): String = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(LANGUAGE, "Hindi") ?: "Hindi"
    fun setLanguage(context: Context, value: String) { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(LANGUAGE, value).apply() }
    fun voiceEnabled(context: Context): Boolean = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(VOICE, true)
    fun setVoiceEnabled(context: Context, value: Boolean) { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(VOICE, value).apply() }
    fun isActive(context: Context): Boolean = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(ACTIVE, true)
    fun setActive(context: Context, value: Boolean) { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(ACTIVE, value).apply() }
}
