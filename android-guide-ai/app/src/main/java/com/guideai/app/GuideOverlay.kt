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
            setBackgroundColor(Color.argb(240, 22, 29, 39))
        }

        card.addView(TextView(context).apply {
            text = if (stuck) "Guide AI: Aap stuck lag rahe hain" else "Guide AI"
            setTextColor(Color.rgb(247, 185, 85))
            textSize = 16f
        })

        val guidance = TextView(context).apply {
            text = "Tap karo guidance ke liye"
            setTextColor(Color.WHITE)
            textSize = 13f
        }
        card.addView(guidance)

        val question = EditText(context).apply {
            hint = "Sawaal likhein (optional)"
            setTextColor(Color.WHITE)
            setHintTextColor(Color.LTGRAY)
            textSize = 13f
            isFocusable = true
            isFocusableInTouchMode = true
        }
        card.addView(question)

        fun cleanGuidance(raw: String): String {
            return raw
                .replace(Regex("```json\\s*"), "")
                .replace(Regex("```\\s*"), "")
                .replace(Regex("\\{\"guidance\":\\s*\""), "")
                .replace(Regex("\",\\s*\"action\".*"), "")
                .replace(Regex("\"\\}.*"), "")
                .replace("\\n", "\n")
                .replace(Regex("(\\d+\\.)"), "\n$1")
                .trim()
        }

        val buttonRow1 = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        val askButton = Button(context).apply {
            text = "Poochho"
            textSize = 11f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener {
                val query = question.text.toString().trim()
                if (query.isEmpty()) return@setOnClickListener
                text = "..."
                CoroutineScope(Dispatchers.Main).launch {
                    GuideApi.explain(language, "$screenText\nUser question: $query")
                        .onSuccess { answer ->
                            guidance.text = cleanGuidance(answer)
                            if (GuideSettings.voiceEnabled(context)) speaker.speak(cleanGuidance(answer), TextToSpeech.QUEUE_FLUSH, null, "guide")
                        }
                        .onFailure { guidance.text = "Error. Dobara try karein." }
                    text = "Poochho"
                }
            }
        }

        val explainButton = Button(context).apply {
            text = "Explain"
            textSize = 11f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener {
                text = "..."
                CoroutineScope(Dispatchers.Main).launch {
                    GuideApi.explain(language, screenText)
                        .onSuccess { answer ->
                            guidance.text = cleanGuidance(answer)
                            if (GuideSettings.voiceEnabled(context)) speaker.speak(cleanGuidance(answer), TextToSpeech.QUEUE_FLUSH, null, "guide")
                        }
                        .onFailure { guidance.text = "Error. Dobara try karein." }
                    text = "Explain"
                }
            }
        }

        buttonRow1.addView(askButton)
        buttonRow1.addView(explainButton)
        card.addView(buttonRow1)

        val buttonRow2 = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        val analyzeButton = Button(context).apply {
            text = "Screenshot"
            textSize = 11f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener {
                text = "..."
                CoroutineScope(Dispatchers.Main).launch {
                    val image = ScreenCapture.capture(context)
                    if (image == null) {
                        guidance.text = "Screen capture permission dein."
                    } else {
                        GuideApi.explainVision(language, image, question.text.toString())
                            .onSuccess { answer ->
                                guidance.text = cleanGuidance(answer)
                                if (GuideSettings.voiceEnabled(context)) speaker.speak(cleanGuidance(answer), TextToSpeech.QUEUE_FLUSH, null, "vision")
                            }
                            .onFailure { guidance.text = "Visual guidance error." }
                    }
                    text = "Screenshot"
                }
            }
        }

        val closeButton = Button(context).apply {
            text = "Band karo"
            textSize = 11f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener { hide() }
        }

        buttonRow2.addView(analyzeButton)
        buttonRow2.addView(closeButton)
        card.addView(buttonRow2)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM
            y = 0
        }

        windowManager?.addView(card, params)
        overlay = card
    }

    fun hide() {
        overlay?.let { windowManager?.removeView(it) }
        overlay = null
    }
}
