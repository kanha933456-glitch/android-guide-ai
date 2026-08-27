package com.guideai.app

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.os.CountDownTimer
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
    var isBusy = false
    private var pauseTimer: CountDownTimer? = null
    var isPaused = false

    fun show(context: Context, stuck: Boolean = false) {
        if (overlay != null) return
        if (isPaused) return
        if (!GuideSettings.isActive(context)) return

        lateinit var speaker: TextToSpeech
        speaker = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                speaker.language = Locale.getDefault()
                speaker.setSpeechRate(0.85f)
            }
        }
        windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 24, 32, 24)
            setBackgroundColor(Color.argb(245, 22, 29, 39))
        }

        card.addView(TextView(context).apply {
            text = if (stuck) "Guide AI — You seem stuck" else "Guide AI is ready"
            setTextColor(Color.rgb(247, 185, 85))
            textSize = 16f
            setTypeface(null, Typeface.BOLD)
        })

        val guidanceLabel = TextView(context).apply {
            text = "Guide AI:"
            setTextColor(Color.rgb(247, 185, 85))
            textSize = 12f
            setTypeface(null, Typeface.BOLD)
            visibility = View.GONE
        }
        card.addView(guidanceLabel)

        val guidance = TextView(context).apply {
            text = "Ask a question or tap the button below."
            setTextColor(Color.WHITE)
            textSize = 13f
        }
        card.addView(guidance)

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
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN
        }

        val userLabel = TextView(context).apply {
            text = "Your question:"
            setTextColor(Color.rgb(150, 220, 255))
            textSize = 12f
            setTypeface(null, Typeface.BOLD)
            visibility = View.GONE
        }
        card.addView(userLabel)

        val userQuestionDisplay = TextView(context).apply {
            setTextColor(Color.rgb(180, 230, 255))
            textSize = 13f
            setPadding(0, 4, 0, 8)
            visibility = View.GONE
        }
        card.addView(userQuestionDisplay)

        fun openKeyboard() {
            params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
            windowManager?.updateViewLayout(card, params)
        }

        fun closeKeyboard(question: EditText) {
            val imm = context.getSystemService(InputMethodManager::class.java)
            imm?.hideSoftInputFromWindow(question.windowToken, 0)
            params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            windowManager?.updateViewLayout(card, params)
        }

        val question = EditText(context).apply {
            hint = "Type your question here (optional)"
            setTextColor(Color.WHITE)
            setHintTextColor(Color.LTGRAY)
            textSize = 13f
            setPadding(0, 8, 0, 8)
            background = null
            isFocusable = true
            isFocusableInTouchMode = true
        }
        question.setOnFocusChangeListener { view, hasFocus ->
            if (hasFocus) {
                view.post {
                    val imm = context.getSystemService(InputMethodManager::class.java)
                    imm?.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
                }
            }
        }
        question.setOnClickListener {
            openKeyboard()
            it.post {
                it.requestFocus()
                val imm = context.getSystemService(InputMethodManager::class.java)
                imm?.showSoftInput(it, InputMethodManager.SHOW_IMPLICIT)
            }
        }
        card.addView(question)

        val buttonRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 8, 0, 0)
        }

        val askButton = Button(context).apply {
            text = "Ask about this page"
            textSize = 11f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener {
                closeKeyboard(question)

                val userQuestion = question.text.toString().trim()

                if (userQuestion.isNotEmpty()) {
                    userLabel.visibility = View.VISIBLE
                    userQuestionDisplay.text = userQuestion
                    userQuestionDisplay.visibility = View.VISIBLE
                } else {
                    userLabel.visibility = View.GONE
                    userQuestionDisplay.visibility = View.GONE
                }

                text = "Thinking..."
                isEnabled = false
                isBusy = true

                CoroutineScope(Dispatchers.Main).launch {
                    val image = ScreenCapture.capture(context)
                    if (image == null) {
                        guidanceLabel.visibility = View.GONE
                        guidance.text = "Screen capture failed. Open Guide AI app and tap 'Allow Screen Capture'."
                        guidance.setTypeface(null, Typeface.NORMAL)
                    } else {
                        GuideApi.explainVision(userQuestion, image)
                            .onSuccess { answer ->
                                val clean = answer.trim().replace(Regex("^1\\.\\s*(?![\\s\\S]*\\n\\d+\\.)"), "")
                                guidanceLabel.visibility = View.VISIBLE
                                guidance.text = clean
                                guidance.setTypeface(null, Typeface.BOLD)

                                if (GuideSettings.voiceEnabled(context)) {
                                    val speakText = clean.replace(Regex("(\\d+\\.\\s)"), ". ").replace("\n", ". ")
                                    speaker.speak(speakText, TextToSpeech.QUEUE_FLUSH, null, "vision")
                                }
                            }
                            .onFailure {
                                guidanceLabel.visibility = View.GONE
                                guidance.text = "Guidance not available. Check internet and try again."
                                guidance.setTypeface(null, Typeface.NORMAL)
                            }
                    }
                    text = "Ask again"
                    isEnabled = true
                    isBusy = false
                }
            }
        }

        val pauseButton = Button(context).apply {
            text = "Pause for 2 min"
            textSize = 11f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener {
                closeKeyboard(question)
                isBusy = false
                isPaused = true
                hide()

                pauseTimer?.cancel()
                pauseTimer = object : CountDownTimer(2 * 60 * 1000L, 1000) {
                    override fun onTick(millisUntilFinished: Long) {}
                    override fun onFinish() { isPaused = false }
                }.start()
            }
        }

        buttonRow.addView(askButton)
        buttonRow.addView(pauseButton)
        card.addView(buttonRow)

        windowManager?.addView(card, params)
        overlay = card
    }

    fun hide() {
        if (isBusy) return
        overlay?.let { windowManager?.removeView(it) }
        overlay = null
    }

    fun forceHide() {
        isBusy = false
        pauseTimer?.cancel()
        isPaused = false
        overlay?.let { windowManager?.removeView(it) }
        overlay = null
    }
}
