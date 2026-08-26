package com.guideai.app

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
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

    fun show(context: Context, stuck: Boolean = false) {
        if (overlay != null) return
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
            setPadding(32, 24, 32, 24)
            setBackgroundColor(Color.argb(245, 22, 29, 39))
        }

        card.addView(TextView(context).apply {
            text = if (stuck) "Guide AI — Aap shayad stuck hain" else "Guide AI"
            setTextColor(Color.rgb(247, 185, 85))
            textSize = 16f
        })

        val guidance = TextView(context).apply {
            text = "Sawaal likhein ya seedha button dabayein — main screenshot lekar exact guidance dunga."
            setTextColor(Color.WHITE)
            textSize = 13f
        }
        card.addView(guidance)

        val question = EditText(context).apply {
            hint = "Jaise: is page par mujhe kya karna hai?"
            setTextColor(Color.WHITE)
            setHintTextColor(Color.LTGRAY)
            textSize = 13f
            setPadding(0, 8, 0, 8)
            background = null
            isFocusable = true
            isFocusableInTouchMode = true
        }
        card.addView(question)

        val buttonRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 8, 0, 0)
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM
            y = 0
        }

        val askButton = Button(context).apply {
            text = "Is page ke baare me poochho"
            textSize = 11f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener {
                val imm = context.getSystemService(InputMethodManager::class.java)
                imm?.hideSoftInputFromWindow(question.windowToken, 0)
                params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                windowManager?.updateViewLayout(card, params)

                text = "Screenshot le rahe hain…"
                isEnabled = false
                val userQuestion = question.text.toString().trim()

                CoroutineScope(Dispatchers.Main).launch {
                    val image = ScreenCapture.capture(context)
                    if (image == null) {
                        guidance.text = "Screen capture nahi hua. Guide AI app kholkar 'Allow Screen Capture' dabayein."
                    } else {
                        GuideApi.explainVision(language, image, userQuestion)
                            .onSuccess { answer ->
                                guidance.text = answer
                                if (GuideSettings.voiceEnabled(context)) {
                                    speaker.speak(answer, TextToSpeech.QUEUE_FLUSH, null, "vision")
                                }
                            }
                            .onFailure {
                                guidance.text = "Guidance nahi mili. Internet check karein aur dobara try karein."
                            }
                    }
                    text = "Dobara poochho"
                    isEnabled = true
                }
            }
        }

        question.setOnClickListener {
            params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
            windowManager?.updateViewLayout(card, params)
            question.requestFocus()
            val imm = context.getSystemService(InputMethodManager::class.java)
            imm?.showSoftInput(question, InputMethodManager.SHOW_IMPLICIT)
        }

        val closeButton = Button(context).apply {
            text = "Band karo"
            textSize = 11f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener {
                val imm = context.getSystemService(InputMethodManager::class.java)
                imm?.hideSoftInputFromWindow(question.windowToken, 0)
                hide()
            }
        }

        buttonRow.addView(askButton)
        buttonRow.addView(closeButton)
        card.addView(buttonRow)

        windowManager?.addView(card, params)
        overlay = card
    }

    fun hide() {
        overlay?.let { windowManager?.removeView(it) }
        overlay = null
    }
}
