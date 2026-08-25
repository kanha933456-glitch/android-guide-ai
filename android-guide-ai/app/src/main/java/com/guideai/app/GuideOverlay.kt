package com.guideai.app

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.speech.tts.TextToSpeech
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object GuideOverlay {
    private var windowManager: WindowManager? = null
    private var overlay: View? = null
    private var currentParams: WindowManager.LayoutParams? = null

    fun show(context: Context, screenText: String, stuck: Boolean = false) {
        if (overlay != null) return
        val protected = screenText.contains(Regex("password|otp|pin|cvv|card number|bank", RegexOption.IGNORE_CASE))
        if (protected) return
        lateinit var speaker: TextToSpeech
        val language = GuideSettings.language(context)
        val locale = when (language) {
            "English" -> Locale.US
            "اردو" -> Locale("ur", "PK")
            "বাংলা" -> Locale("bn", "BD")
            else -> Locale("hi", "IN")
        }
        speaker = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                speaker.language = locale
                speaker.setSpeechRate(0.75f)
            }
        }
        windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 22, 28, 22)
            setBackgroundColor(Color.argb(245, 22, 29, 39))
        }

        card.addView(TextView(context).apply {
            text = if (stuck) "Guide AI: Aap stuck lag rahe hain" else "Guide AI"
            setTextColor(Color.rgb(247, 185, 85))
            textSize = 16f
        })

        val guidance = TextView(context).apply {
            text = "Apna sawaal likho jahan aap fanse hain — main aapko exact next step bataunga."
            setTextColor(Color.WHITE)
            textSize = 13f
        }
        card.addView(guidance)

        fun setWindowFocusable(focusable: Boolean) {
            val params = currentParams ?: return
            if (focusable) {
                params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
            } else {
                params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            }
            windowManager?.updateViewLayout(card, params)
        }

        val question = EditText(context).apply {
            hint = "Yahan apna sawaal likhein"
            setTextColor(Color.WHITE)
            setHintTextColor(Color.LTGRAY)
            textSize = 13f
            setOnFocusChangeListener { _, hasFocus ->
                if (!hasFocus) setWindowFocusable(false)
            }
            setOnClickListener { setWindowFocusable(true); requestFocus() }
        }
        card.addView(question)

        fun cleanGuidance(raw: String): String {
            return raw
                .replace(Regex("```json\\s*"), "")
                .replace(Regex("```\\s*"), "")
                .replace(Regex("\\{\\s*\"guidance\"\\s*:\\s*\""), "")
                .replace(Regex("\"\\s*,\\s*\"action\".*"), "")
                .replace(Regex("\"\\s*\\}\\s*$"), "")
                .replace("\\n", "\n")
                .replace(Regex("(?<!^)(\\d+\\.\\s)"), "\n$1")
                .trim()
        }

        val buttonRow = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }

        val askButton = Button(context).apply {
            text = "Ask Guide AI"
            textSize = 12f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener {
                val query = question.text.toString().trim()
                val effectiveQuery = if (query.isEmpty()) "User is stuck. Give the single safest, most specific next step." else query
                text = "Sochte hain…"
                setWindowFocusable(false)
                CoroutineScope(Dispatchers.Main).launch {
                    GuideApi.explain(language, "$screenText\nUser question: $effectiveQuery")
                        .onSuccess { answer ->
                            val clean = cleanGuidance(answer)
                            guidance.text = clean
                            if (GuideSettings.voiceEnabled(context)) speaker.speak(clean, TextToSpeech.QUEUE_FLUSH, null, "guide")
                        }
                        .onFailure { guidance.text = "Guidance nahi mil payi. Dobara try karein." }
                    text = "Ask Guide AI"
                }
            }
        }

        val closeButton = Button(context).apply {
            text = "Band karo"
            textSize = 12f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener { hide() }
        }

        buttonRow.addView(askButton)
        buttonRow.addView(closeButton)
        card.addView(buttonRow)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM
            y = 0
        }
        currentParams = params

        windowManager?.addView(card, params)
        overlay = card
    }

    fun hide() {
        overlay?.let { windowManager?.removeView(it) }
        overlay = null
        currentParams = null
    }
}
