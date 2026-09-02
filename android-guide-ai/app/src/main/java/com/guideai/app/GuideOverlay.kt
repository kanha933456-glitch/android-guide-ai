package com.guideai.app

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.bottomsheet.BottomSheetDialog
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object GuideOverlay {
    private var windowManager: WindowManager? = null
    private var bubbleView: View? = null
    var isBusy = false
    var isPaused = false
    private var ttsEngine: TextToSpeech? = null
    private var hasAnsweredOnce = false

    fun show(context: Context, stuck: Boolean = false) {
        if (!GuideSettings.isActive(context)) return

        val appContext = context.applicationContext

        if (bubbleView != null) {
            try {
                windowManager?.removeView(bubbleView)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            bubbleView = null
        }

        try {
            windowManager = appContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            initTTS(appContext)

            val size = (56 * appContext.resources.displayMetrics.density).toInt()

            val icon = Button(appContext).apply {
                text = "G"
                setTextColor(Color.parseColor("#121824"))
                textSize = 22f
                setTypeface(null, Typeface.BOLD)
                gravity = Gravity.CENTER
                setPadding(0, 0, 0, 0)
                
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.parseColor("#F7B955"))
                    setStroke(6, Color.parseColor("#FFFFFF"))
                }
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    elevation = 16f
                }
            }

            val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }

            val params = WindowManager.LayoutParams(
                size,
                size,
                layoutType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.END
                x = 40
                y = 400
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
                            if (diffX < 15 && diffY < 15) {
                                showGuideDialog(context)
                            }
                            return true
                        }
                    }
                    return false
                }
            })

            windowManager?.addView(icon, params)
            bubbleView = icon

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun showGuideDialog(context: Context) {
        val dialog = BottomSheetDialog(context)
        hasAnsweredOnce = false

        val mainLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 32, 40, 40)
            setBackgroundColor(Color.parseColor("#121824"))
        }

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
                GuideSettings.setActive(context, false)
                forceHide()
            }
        }

        titleRow.addView(title)
        titleRow.addView(offButton)
        mainLayout.addView(titleRow)

        val guidance = TextView(context).apply {
            text = "Hello! How can I help you today?"
            setTextColor(Color.WHITE)
            textSize = 14f
            setPadding(0, 16, 0, 16)
        }
        mainLayout.addView(guidance)

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

        val questionInput = EditText(context).apply {
            hint = "Ask a question..."
            setTextColor(Color.WHITE)
            setHintTextColor(Color.parseColor("#8A99AD"))
            textSize = 14f
            setPadding(24, 24, 24, 24)
            setBackgroundColor(Color.parseColor("#263344"))
        }
        mainLayout.addView(questionInput)

        val btnRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 24, 0, 0)
        }

        val askBtn = Button(context).apply {
            text = "ASK ABOUT SCREEN"
            textSize = 12f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#121824"))
            setBackgroundColor(Color.parseColor("#F7B955"))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = 12
            }
        }

        // Dynamic text watcher for button label
        questionInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (!isBusy) {
                    val input = s?.toString()?.trim() ?: ""
                    if (input.isNotEmpty()) {
                        askBtn.text = "ASK ANYTHING"
                    } else if (hasAnsweredOnce) {
                        askBtn.text = "ASK AGAIN"
                    } else {
                        askBtn.text = "ASK ABOUT SCREEN"
                    }
                }
            }
        })

        askBtn.setOnClickListener {
            val inputQuery = questionInput.text.toString().trim()
            hideKeyboard(context, questionInput)

            if (inputQuery.isNotEmpty()) {
                userQuestionText.text = "\"$inputQuery\""
                userQuestionContainer.visibility = View.VISIBLE
            }

            askBtn.text = "THINKING..."
            askBtn.isEnabled = false
            isBusy = true

            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val imageStr = ScreenCapture.capture(context)
                    if (!imageStr.isNullOrEmpty()) {
                        GuideApi.explainVision(inputQuery, imageStr)
                            .onSuccess { answer ->
                                val formattedAnswer = formatAIResponse(answer)
                                CoroutineScope(Dispatchers.Main).launch {
                                    guidance.text = formattedAnswer
                                    speakText(formattedAnswer)
                                    hasAnsweredOnce = true
                                    askBtn.text = if (questionInput.text.toString().trim().isNotEmpty()) "ASK ANYTHING" else "ASK AGAIN"
                                    askBtn.isEnabled = true
                                    isBusy = false
                                    hideKeyboard(context, questionInput)
                                }
                            }
                            .onFailure { exception ->
                                CoroutineScope(Dispatchers.Main).launch {
                                    guidance.text = "ERROR: ${exception.message}"
                                    askBtn.text = "ASK AGAIN"
                                    askBtn.isEnabled = true
                                    isBusy = false
                                }
                            }
                    } else {
                        CoroutineScope(Dispatchers.Main).launch {
                            guidance.text = "ERROR: Screen capture frame empty"
                            askBtn.text = "ASK AGAIN"
                            askBtn.isEnabled = true
                            isBusy = false
                        }
                    }
                } catch (e: Exception) {
                    CoroutineScope(Dispatchers.Main).launch {
                        guidance.text = "ERROR: ${e.localizedMessage}"
                        askBtn.text = "ASK AGAIN"
                        askBtn.isEnabled = true
                        isBusy = false
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
        val dialogType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
        dialog.window?.setType(dialogType)
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        
        // Touch outside dismiss behavior
        dialog.setCanceledOnTouchOutside(true)
        
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
        isPaused = false
        ttsEngine?.stop()
        ttsEngine?.shutdown()
        ttsEngine = null
        hide()
        windowManager = null
    }
}
