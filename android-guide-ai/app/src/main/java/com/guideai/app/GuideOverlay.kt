package com.guideai.app

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.os.CountDownTimer
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.bottomsheet.BottomSheetDialog
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

import com.guideai.app.GuideSettings
import com.guideai.app.ScreenCapture
import com.guideai.app.GuideApi

object GuideOverlay {
    private var windowManager: WindowManager? = null
    private var bubbleView: View? = null
    var isBusy = false
    private var pauseTimer: CountDownTimer? = null
    var isPaused = false

    fun show(context: Context, stuck: Boolean = false) {
        if (bubbleView != null || isPaused) return
        if (!GuideSettings.isActive(context)) return

        windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

        // Floating Bubble Icon (Zero Focus, Never Blocks Navigation Bar)
        val icon = ImageView(context).apply {
            setImageResource(android.R.drawable.ic_dialog_info)
            setBackgroundColor(Color.argb(220, 247, 185, 85))
            setPadding(20, 20, 20, 20)
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = 30
            y = 300
        }

        // Drag & Tap Listener for Floating Bubble
        icon.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0
            private var initialY = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params.x
                        initialY = params.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        params.x = initialX - (event.rawX - initialTouchX).toInt()
                        params.y = initialY + (event.rawY - initialTouchY).toInt()
                        windowManager?.updateViewLayout(bubbleView, params)
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        val diffX = Math.abs(event.rawX - initialTouchX)
                        val diffY = Math.abs(event.rawY - initialTouchY)
                        if (diffX < 10 && diffY < 10) {
                            showGuideDialog(context)
                        }
                        return true
                    }
                }
                return false
            }
        })

        try {
            windowManager?.addView(icon, params)
            bubbleView = icon
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun showGuideDialog(context: Context) {
        val dialog = BottomSheetDialog(context)
        
        lateinit var speaker: TextToSpeech
        speaker = TextToSpeech(context, { status ->
            if (status == TextToSpeech.SUCCESS) {
                val indianEnglish = Locale("en", "IN")
                if (speaker.setLanguage(indianEnglish) == TextToSpeech.LANG_MISSING_DATA) {
                    speaker.language = Locale.getDefault()
                }
                speaker.setSpeechRate(0.95f)
            }
        }, "com.google.android.tts")

        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 40, 40, 40)
            setBackgroundColor(Color.argb(255, 22, 29, 39))
        }

        val title = TextView(context).apply {
            text = "Guide AI"
            setTextColor(Color.rgb(247, 185, 85))
            textSize = 16f
            setTypeface(null, Typeface.BOLD)
        }
        layout.addView(title)

        val guidance = TextView(context).apply {
            text = "Ask a question or process this screen."
            setTextColor(Color.WHITE)
            textSize = 13f
            setPadding(0, 10, 0, 20)
        }
        layout.addView(guidance)

        val questionInput = EditText(context).apply {
            hint = "Type your question here (optional)"
            setTextColor(Color.WHITE)
            setHintTextColor(Color.LTGRAY)
            textSize = 13f
            setPadding(24, 24, 24, 24)
            setBackgroundColor(Color.argb(60, 255, 255, 255))
        }
        layout.addView(questionInput)

        val btnRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 20, 0, 0)
        }

        val askBtn = Button(context).apply {
            text = "Ask About Screen"
            textSize = 11f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener {
                val userQuestion = questionInput.text.toString().trim()
                text = "Thinking..."
                isEnabled = false

                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val image = ScreenCapture.capture(context)
                        if (image != null) {
                            GuideApi.explainVision(userQuestion, image)
                                .onSuccess { answer ->
                                    var clean = answer.trim().replace(Regex("\\*\\*(.*?)\\*\\*"), "($1)")
                                    CoroutineScope(Dispatchers.Main).launch {
                                        guidance.text = clean
                                        guidance.setTypeface(null, Typeface.BOLD)
                                        if (GuideSettings.voiceEnabled(context)) {
                                            speaker.speak(clean, TextToSpeech.QUEUE_FLUSH, null, "vision")
                                        }
                                        text = "Ask Again"
                                        isEnabled = true
                                    }
                                }
                                .onFailure {
                                    CoroutineScope(Dispatchers.Main).launch {
                                        guidance.text = "Failed to load guidance."
                                        text = "Ask Again"
                                        isEnabled = true
                                    }
                                }
                        }
                    } catch (e: Exception) {
                        CoroutineScope(Dispatchers.Main).launch {
                            guidance.text = "Something went wrong."
                            text = "Ask Again"
                            isEnabled = true
                        }
                    }
                }
            }
        }

        val closeBtn = Button(context).apply {
            text = "Close"
            textSize = 11f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener { dialog.dismiss() }
        }

        btnRow.addView(askBtn)
        btnRow.addView(closeBtn)
        layout.addView(btnRow)

        dialog.setContentView(layout)
        dialog.window?.setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        dialog.show()
    }

    fun hide() {
        bubbleView?.let {
            try {
                windowManager?.removeView(it)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        bubbleView = null
    }

    fun forceHide() {
        hide()
        windowManager = null
    }
}
