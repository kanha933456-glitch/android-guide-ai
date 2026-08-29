package com.guideai.app

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.os.Build
import android.os.CountDownTimer
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object GuideOverlay {
    private var windowManager: WindowManager? = null
    private var overlay: View? = null

    var isBusy = false
    var isPaused = false
    var isKeyboardOpen = false

    private var pauseTimer: CountDownTimer? = null

    fun show(context: Context, stuck: Boolean = false) {
        if (overlay != null) return
        if (isPaused) return
        if (isKeyboardOpen) return
        if (!GuideSettings.isActive(context)) return

        lateinit var speaker: TextToSpeech

        speaker = TextToSpeech(context, { status ->
            if (status == TextToSpeech.SUCCESS) {
                val indianEnglish = Locale("en", "IN")
                val result = speaker.setLanguage(indianEnglish)

                if (
                    result == TextToSpeech.LANG_MISSING_DATA ||
                    result == TextToSpeech.LANG_NOT_SUPPORTED
                ) {
                    val hindi = Locale("hi", "IN")
                    val resultHindi = speaker.setLanguage(hindi)

                    if (
                        resultHindi == TextToSpeech.LANG_MISSING_DATA ||
                        resultHindi == TextToSpeech.LANG_NOT_SUPPORTED
                    ) {
                        speaker.language = Locale.getDefault()
                    }
                }

                speaker.setSpeechRate(0.95f)
                speaker.setPitch(1.0f)
            }
        }, "com.google.android.tts")

        windowManager =
            context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 24, 32, 24)
            setBackgroundColor(Color.argb(245, 22, 29, 39))
        }

        card.addView(TextView(context).apply {
            text = if (stuck) {
                "Guide AI — You seem stuck"
            } else {
                "Guide AI is ready"
            }

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

        /*
         * Important:
         * ADJUST_NOTHING use kiya gaya hai taaki keyboard open hone par
         * Android navigation bar hide ya resize na ho.
         */
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM
            y = 0

            softInputMode =
                WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                navigationBarColor = Color.BLACK
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                navigationBarDividerColor = Color.BLACK
            }
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
            isKeyboardOpen = true

            params.flags = params.flags and
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()

            params.softInputMode =
                WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING or
                        WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE

            windowManager?.updateViewLayout(card, params)
        }

        fun closeKeyboard(question: EditText) {
            isKeyboardOpen = false

            val inputMethodManager =
                context.getSystemService(InputMethodManager::class.java)

            inputMethodManager?.hideSoftInputFromWindow(
                question.windowToken,
                0
            )

            params.flags = params.flags or
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE

            params.softInputMode =
                WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING

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

        question.setOnTouchListener { view, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                val isNotFocusable =
                    (params.flags and
                            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE) != 0

                if (isNotFocusable) {
                    openKeyboard()
                }
            }

            false
        }

        question.setOnFocusChangeListener { view, hasFocus ->
            if (hasFocus) {
                view.postDelayed({
                    val inputMethodManager =
                        context.getSystemService(InputMethodManager::class.java)

                    inputMethodManager?.showSoftInput(
                        view,
                        InputMethodManager.SHOW_IMPLICIT
                    )
                }, 150)
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
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )

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

                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val image = ScreenCapture.capture(context)

                        if (image == null) {
                            CoroutineScope(Dispatchers.Main).launch {
                                guidanceLabel.visibility = View.GONE
                                guidance.text =
                                    "Screen capture failed. Please reopen Guide AI app and tap 'Allow Screen Capture' again."
                                guidance.setTypeface(null, Typeface.NORMAL)
                                text = "Ask again"
                                isEnabled = true
                                isBusy = false
                            }
                        } else {
                            GuideApi.explainVision(userQuestion, image)
                                .onSuccess { answer ->
                                    var clean = answer
                                        .trim()
                                        .replace(
                                            Regex(
                                                "^1\\.\\s*(?![\\s\\S]*\\n\\d+\\.)"
                                            ),
                                            ""
                                        )

                                    clean = clean.replace(
                                        Regex("\\*\\*(.*?)\\*\\*"),
                                        "($1)"
                                    )

                                    CoroutineScope(Dispatchers.Main).launch {
                                        guidanceLabel.visibility = View.VISIBLE
                                        guidance.text = clean
                                        guidance.setTypeface(
                                            null,
                                            Typeface.BOLD
                                        )

                                        if (
                                            GuideSettings.voiceEnabled(context)
                                        ) {
                                            val speakText = clean
                                                .replace("(", "")
                                                .replace(")", "")
                                                .replace(
                                                    Regex("(\\d+\\.\\s)"),
                                                    ". "
                                                )
                                                .replace("\\n", ". ")

                                            speaker.speak(
                                                speakText,
                                                TextToSpeech.QUEUE_FLUSH,
                                                null,
                                                "vision"
                                            )
                                        }

                                        text = "Ask again"
                                        isEnabled = true
                                        isBusy = false
                                    }
                                }
                                .onFailure {
                                    CoroutineScope(Dispatchers.Main).launch {
                                        guidanceLabel.visibility = View.GONE
                                        guidance.text =
                                            "Guidance not available. Check internet and try again."
                                        guidance.setTypeface(
                                            null,
                                            Typeface.NORMAL
                                        )
                                        text = "Ask again"
                                        isEnabled = true
                                        isBusy = false
                                    }
                                }
                        }
                    } catch (exception: Exception) {
                        CoroutineScope(Dispatchers.Main).launch {
                            guidanceLabel.visibility = View.GONE
                            guidance.text =
                                "Something went wrong. Please try again."
                            guidance.setTypeface(
                                null,
                                Typeface.NORMAL
                            )
                            text = "Ask again"
                            isEnabled = true
                            isBusy = false
                        }
                    }
                }
            }
        }

        val pauseButton = Button(context).apply {
            text = "Pause for 2 min"
            textSize = 11f
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )

            setOnClickListener {
                closeKeyboard(question)

                isBusy = false
                isPaused = true
                hide()

                pauseTimer?.cancel()

                pauseTimer = object : CountDownTimer(
                    2 * 60 * 1000L,
                    1000
                ) {
                    override fun onTick(millisUntilFinished: Long) = Unit

                    override fun onFinish() {
                        isPaused = false
                    }
                }.start()
            }
        }

        buttonRow.addView(askButton)
        buttonRow.addView(pauseButton)
        card.addView(buttonRow)

        try {
            windowManager?.addView(card, params)
            overlay = card
        } catch (exception: Exception) {
            overlay = null
            isKeyboardOpen = false
        }
    }

    fun hide() {
        if (isBusy) return
        if (isKeyboardOpen) return

        overlay?.let { currentOverlay ->
            try {
                windowManager?.removeView(currentOverlay)
            } catch (exception: Exception) {
                exception.printStackTrace()
            }
        }

        overlay = null
    }

    fun forceHide() {
        isBusy = false
        isKeyboardOpen = false

        pauseTimer?.cancel()
        isPaused = false

        overlay?.let { currentOverlay ->
            try {
                windowManager?.removeView(currentOverlay)
            } catch (exception: Exception) {
                exception.printStackTrace()
            }
        }

        overlay = null
        windowManager = null
    }
}
