package com.guideai.app

import android.content.Context

object GuideSettings {
    private const val PREFS = "guide_ai_settings"
    private const val LANGUAGE = "language"
    private const val VOICE = "voice_enabled"
    private const val ACTIVE = "active"
    private const val CONSENT = "privacy_consent"

    fun language(context: Context): String = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(LANGUAGE, "Hindi") ?: "Hindi"
    fun setLanguage(context: Context, value: String) { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(LANGUAGE, value).apply() }
    
    fun voiceEnabled(context: Context): Boolean = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(VOICE, true)
    fun setVoiceEnabled(context: Context, value: Boolean) { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(VOICE, value).apply() }
    
    // Default ko FALSE kiya hai taaki pehli baar app khulne par OFF rahe
    fun isActive(context: Context): Boolean = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(ACTIVE, false)
    fun setActive(context: Context, value: Boolean) { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(ACTIVE, value).apply() }
    
    // Check karta hai ki active key pehle se saved hai ya nahi
    fun hasActiveKey(context: Context): Boolean = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).contains(ACTIVE)

    fun hasConsent(context: Context): Boolean = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(CONSENT, false)
    fun setConsent(context: Context, value: Boolean) { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(CONSENT, value).apply() }
}
