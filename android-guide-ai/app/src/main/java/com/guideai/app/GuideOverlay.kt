package com.guideai.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.ShapeDrawable
import android.graphics.drawable.shapes.OvalShape
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
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
    private var ttsEngine: TextToSpeech? = null

    fun show(context: Context, stuck: Boolean = false) {
        if (bubbleView != null) return
        if (!GuideSettings.isActive(context)) return

        windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        initTTS(context)

        // Premium Floating 'G' Badge Creation
        val size = (54 * context.resources.displayMetrics.density).toInt()
        val gIconDrawable = object : ShapeDrawable(OvalShape()) {
            override fun draw(canvas: Canvas) {
                paint.color = Color.parseColor("#1A1F2C")
                super.draw(canvas)

                val borderPaint = Paint().apply {
                    color = Color.parseColor("#F7B955")
                    style = Paint.Style.STROKE
                    strokeWidth = 6f
                    isAntiAlias = true
                }
                canvas.drawCircle(bounds.exactCenterX(), bounds.exactCenterY(), bounds.width() / 2f - 3, borderPaint)

                val textPaint = Paint().apply {
                    color = Color.parseColor("#F7B955")
                    textSize = bounds.height() * 0.55f
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    textAlign = Paint.Align.CENTER
                    isAntiAlias = true
                }
                val yPos = bounds.exactCenterY() - ((textPaint.descent() + textPaint.ascent()) / 2)
                canvas.drawText("G", bounds.exactCenterX(), yPos, textPaint)
            }
        }

        val icon = ImageView(context).apply {
            setImageDrawable(gIconDrawable)
            setPadding(6, 6, 6, 6)
        }

        val params = WindowManager.LayoutParams(
            size,
            size,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = 40
            y = 350
        }

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

        val mainLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 32, 40, 40)
            setBackgroundColor(Color.parseColor("#121824"))
        }

        // Header Section
        val titleRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val title = TextView(context).apply {
            text = "Guide AI"
            setTextColor(Color.parseColor("#F7B955"))
            textSize = 17f
            setTypeface(null, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val offButton = Button(context).apply {
            text = "OFF"
            textSize = 10f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#D32F2F"))
            layoutParams = LinearLayout.LayoutParams(
                (65 * context.resources.displayMetrics.density).toInt(),
                (36 * context.resources.displayMetrics.density).toInt()
            )
            setOnClickListener {
                hideKeyboard(context, this)
                dialog.dismiss()
                forceHide()
            }
        }

        titleRow.addView(title)
        titleRow.addView(offButton)
        mainLayout.addView(titleRow)

        // Guidance Output Box
        val guidance = TextView(context).apply {
            text = "Hello! How can I help you today?"
            setTextColor(Color.WHITE)
            textSize = 14f
            setPadding(0, 16, 0, 16)
        }
        mainLayout.addView(guidance)

        // User Question Highlight Box (Premium Cyan Theme)
        val userQuestionContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 16, 20, 16)
            setBackgroundColor(Color.parseColor("#1E2A38"))
            visibility = View.GONE
        }
        
        val userQuestionHeader = TextView(context).apply {
            text = "YOUR QUESTION:"
            setTextColor(Color.parseColor("#00E5FF"))
            textSize = 11f
            setTypeface(null, Typeface.BOLD)
        }
        
        val userQuestionText = TextView(context).apply {
            setTextColor(Color.parseColor("#E0F7FA"))
            textSize = 13f
            setTypeface(null, Typeface.BOLD_ITALIC)
        }
        
        userQuestionContainer.addView(userQuestionHeader)
        userQuestionContainer.addView(userQuestionText)
        mainLayout.addView(userQuestionContainer)

        // Typing Box
        val questionInput = EditText(context).apply {
            hint = "Ask a question..."
            setTextColor(Color.WHITE)
            setHintTextColor(Color.parseColor("#8A99AD"))
            textSize = 14f
            setPadding(24, 24, 24, 24)
            setBackgroundColor(Color.parseColor("#263344"))
        }
        mainLayout.addView(questionInput)

        // Action Buttons Row
        val btnRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 24, 0, 0)
        }

        val askBtn = Button(context).apply {
            text = "ASK AGAIN"
            textSize = 12f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#121824"))
            setBackgroundColor(Color.parseColor("#F7B955"))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = 12
            }
            setOnClickListener {
                val inputQuery = questionInput.text.toString().trim()
                hideKeyboard(context, questionInput)

                if (inputQuery.isNotEmpty()) {
                    userQuestionText.text = "\"$inputQuery\""
                    userQuestionContainer.visibility = View.VISIBLE
                }

                text = "THINKING..."
                isEnabled = false
                isBusy = true

                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val image = ScreenCapture.capture(context)
                        if (image != null) {
                            GuideApi.explainVision(inputQuery, image)
                                .onSuccess { answer ->
                                    val formattedAnswer = formatAIResponse(answer)
                                    CoroutineScope(Dispatchers.Main).launch {
                                        guidance.text = formattedAnswer
                                        speakText(formattedAnswer)
                                        text = "ASK AGAIN"
                                        isEnabled = true
                                        isBusy = false
                                        dialog.dismiss()
                                    }
                                }
                                .onFailure {
                                    CoroutineScope(Dispatchers.Main).launch {
                                        guidance.text = "Unable to process screen content. Try again."
                                        text = "ASK AGAIN"
                                        isEnabled = true
                                        isBusy = false
                                    }
                                }
                        } else {
                            CoroutineScope(Dispatchers.Main).launch {
                                guidance.text = "Screen capture failed. Check permissions."
                                text = "ASK AGAIN"
                                isEnabled = true
                                isBusy = false
                            }
                        }
                    } catch (e: Exception) {
                        CoroutineScope(Dispatchers.Main).launch {
                            guidance.text = "Error occurred. Please try again."
                            text = "ASK AGAIN"
                            isEnabled = true
                            isBusy = false
                        }
                    }
                }
            }
        }

        val closeBtn = Button(context).apply {
            text = "CLOSE"
            textSize = 12f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#37474F"))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener {
                hideKeyboard(context, questionInput)
                dialog.dismiss()
            }
        }

        btnRow.addView(askBtn)
        btnRow.addView(closeBtn)
        mainLayout.addView(btnRow)

        dialog.setContentView(mainLayout)
        dialog.window?.setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        dialog.show()
    }

    private fun hideKeyboard(context: Context, view: View) {
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(view.windowToken, 0)
    }

    private fun initTTS(context: Context) {
        if (ttsEngine != null) return
        ttsEngine = TextToSpeech(context, { status ->
            if (status == TextToSpeech.SUCCESS) {
                val locale = Locale("en", "IN")
                ttsEngine?.language = locale
                
                // Select Google High-Quality Voice if available
                val voices = ttsEngine?.voices
                if (voices != null) {
                    for (voice in voices) {
                        if (voice.name.contains("google", ignoreCase = true) && voice.locale.language == "en") {
                            ttsEngine?.voice = voice
                            break
                        }
                    }
                }
                ttsEngine?.setSpeechRate(0.92f)
                ttsEngine?.setPitch(1.0f)
            }
        }, "com.google.android.tts")
    }

    private fun speakText(text: String) {
        val cleanSpeech = text.replace(Regex("[\\*\\#\\[\\]\\(\\)]"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
        val params = Bundle()
        params.putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
        ttsEngine?.speak(cleanSpeech, TextToSpeech.QUEUE_FLUSH, params, "GUIDE_AI_TTS")
    }

    private fun formatAIResponse(rawText: String): String {
        var text = rawText.trim()
        text = text.replace(Regex("\\*\\*(.*?)\\*\\*"), "“$1”")
        text = text.replace(Regex("^1\\.\\s*"), "")
        return text
    }

    fun hide() {
        if (isBusy) return
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
        isBusy = false
        ttsEngine?.stop()
        ttsEngine?.shutdown()
        ttsEngine = null
        hide()
        windowManager = null
    }
}
