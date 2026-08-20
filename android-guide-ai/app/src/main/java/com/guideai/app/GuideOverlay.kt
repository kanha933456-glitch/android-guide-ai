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

    fun show(context: Context, screenText: String) {
        if (overlay != null) return
        val protected = screenText.contains(Regex("password|otp|pin|cvv|card number|bank", RegexOption.IGNORE_CASE))
        if (protected) return
        lateinit var speaker: TextToSpeech
        speaker = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                speaker.language = Locale("hi", "IN")
                speaker.setSpeechRate(0.75f)
            }
        }
        windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val card = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; setPadding(28, 22, 28, 22); setBackgroundColor(Color.rgb(22, 29, 39)) }
        card.addView(TextView(context).apply { text = "Guide AI is ready"; setTextColor(Color.rgb(247, 185, 85)); textSize = 18f })
        val guidance = TextView(context).apply { text = "Screen text captured safely. Tap below for Gemini guidance."; setTextColor(Color.WHITE); textSize = 14f }
        card.addView(guidance)
        val question = EditText(context).apply { hint = "Apna sawaal likhein"; setTextColor(Color.WHITE); setHintTextColor(Color.LTGRAY) }
        card.addView(question)
        card.addView(Button(context).apply {
            text = "Ask Guide AI"
            setOnClickListener {
                val query = question.text.toString().trim()
                if (query.isEmpty()) return@setOnClickListener
                text = "Thinking…"
                CoroutineScope(Dispatchers.Main).launch {
                    GuideApi.explain("Hindi", "$screenText\nUser question: $query").onSuccess { answer -> guidance.text = answer; speaker.speak(answer, TextToSpeech.QUEUE_FLUSH, null, "guide") }.onFailure { guidance.text = "API URL configure karein, phir dobara try karein." }
                    text = "Ask again"
                }
            }
        })
        card.addView(Button(context).apply {
            text = "Analyze screenshot"
            setOnClickListener {
                text = "Analyzing…"
                CoroutineScope(Dispatchers.Main).launch {
                    val image = ScreenCapture.capture(context)
                    if (image == null) guidance.text = "Pehle app me 'Allow Screen Capture' permission dein."
                    else GuideApi.explainVision("Hindi", image, question.text.toString()).onSuccess { answer -> guidance.text = answer; speaker.speak(answer, TextToSpeech.QUEUE_FLUSH, null, "vision") }.onFailure { guidance.text = "Visual guidance connect nahi ho paayi." }
                    text = "Analyze again"
                }
            }
        })
        card.addView(Button(context).apply {
            text = "Explain next step"
            setOnClickListener {
                text = "Thinking…"
                CoroutineScope(Dispatchers.Main).launch {
                    GuideApi.explain("Hindi", screenText).onSuccess { guidance.text = it; speaker.speak(it, TextToSpeech.QUEUE_FLUSH, null, "guide") }.onFailure { guidance.text = "API URL configure karein, phir dobara try karein." }
                    text = "Explain again"
                }
            }
        })
        card.addView(Button(context).apply { text = "Close"; setOnClickListener { hide() } })
        val params = WindowManager.LayoutParams(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, 0, PixelFormat.TRANSLUCENT).apply { gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL; y = 80 }
        windowManager?.addView(card, params)
        overlay = card
    }

    fun hide() { overlay?.let { windowManager?.removeView(it) }; overlay = null }
}
