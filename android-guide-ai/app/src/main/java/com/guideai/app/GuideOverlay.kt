package com.guideai.app

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.os.Build
import android.os.CountDownTimer
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

import com.guideai.app.GuideSettings
import com.guideai.app.ScreenCapture
import com.guideai.app.GuideApi

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
        speaker = TextToSpeech(context, { status ->
            if (status == TextToSpeech.SUCCESS) {
                val indianEnglish = Locale("en", "IN")
                val result = speaker.setLanguage(indianEnglish)
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    val hindi = Locale("hi", "IN")
                    val resultHi = speaker.setLanguage(hindi)
                    if (resultHi == TextToSpeech.LANG_MISSING_DATA || resultHi == TextToSpeech.LANG_NOT_SUPPORTED) {
                        speaker.language = Locale.getDefault()
                    }
                }
                speaker.setSpeechRate(0.95f)
                speaker.setPitch(1.0f)
            }
        }, "com.google.android.tts")

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

        var userCustomQuestion = ""

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

        // Strict non-focus window to preserve Navigation Bar completely
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

        val typeButton = Button(context).apply {
            text = "✍️ Type Question (Optional)"
            textSize = 11f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.argb(100, 50, 60, 80))
            setOnClickListener {
                showTypeQuestionDialog(context) { typedText ->
                    userCustomQuestion = typedText
                    if (typedText.isNotEmpty()) {
                        userLabel.visibility = View.VISIBLE
                        userQuestionDisplay.text = typedText
                        userQuestionDisplay.visibility = View.VISIBLE
                    } else {
                        userLabel.visibility = View.GONE
                        userQuestionDisplay.visibility = View.GONE
                    }
                }
            }
        }
        card.addView(typeButton)

        val buttonRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 12, 0, 0)
        }

        val askButton = Button(context).apply {
            text = "Ask about this page"
            textSize = 11f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener {
                text = "Thinking..."
                isEnabled = false
                isBusy = true

                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val image = ScreenCapture.capture(context)
                        if (image == null) {
                            CoroutineScope(Dispatchers.Main).launch {
                                guidanceLabel.visibility = View.GONE
                                guidance.text = "Screen capture failed. Please reopen Guide AI app."
                                guidance.setTypeface(null, Typeface.NORMAL)
                                text = "Ask again"
                                isEnabled = true
                                isBusy = false
                            }
                        } else {
                            GuideApi.explainVision(userCustomQuestion, image)
                                .onSuccess { answer ->
                                    var clean = answer.trim().replace(Regex("^1\\.\\s*(?![\\s\\S]*\\n\\d+\\.)"), "")
                                    clean = clean.replace(Regex("\\*\\*(.*?)\\*\\*"), "($1)")

                                    CoroutineScope(Dispatchers.Main).launch {
                                        guidanceLabel.visibility = View.VISIBLE
                                        guidance.text = clean
                                        guidance.setTypeface(null, Typeface.BOLD)

                                        if (GuideSettings.voiceEnabled(context)) {
                                            val speakText = clean.replace("(", "").replace(")", "").replace(Regex("(\\d+\\.\\s)"), ". ").replace("\n", ". ")
                                            speaker.speak(speakText, TextToSpeech.QUEUE_FLUSH, null, "vision")
                                        }
                                        text = "Ask again"
                                        isEnabled = true
                                        isBusy = false
                                    }
                                }
                                .onFailure {
                                    CoroutineScope(Dispatchers.Main).launch {
                                        guidanceLabel.visibility = View.GONE
                                        guidance.text = "Guidance not available. Check internet and try again."
                                        guidance.setTypeface(null, Typeface.NORMAL)
                                        text = "Ask again"
                                        isEnabled = true
                                        isBusy = false
                                    }
                                }
                        }
                    } catch (e: Exception) {
                        CoroutineScope(Dispatchers.Main).launch {
                            guidanceLabel.visibility = View.GONE
                            guidance.text = "Something went wrong. Please try again."
                            guidance.setTypeface(null, Typeface.NORMAL)
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
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener {
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

    private fun showTypeQuestionDialog(context: Context, onQuestionSet: (String) -> Unit) {
        val input = EditText(context).apply {
            hint = "Type your question..."
            setPadding(32, 32, 32, 32)
        }

        val dialog = AlertDialog.Builder(context)
            .setTitle("Ask Guide AI")
            .setView(input)
            .setPositiveButton("OK") { d, _ ->
                onQuestionSet(input.text.toString().trim())
                d.dismiss()
            }
            .setNegativeButton("Cancel") { d, _ -> d.dismiss() }
            .create()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            dialog.window?.setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
        } else {
            @Suppress("DEPRECATION")
            dialog.window?.setType(WindowManager.LayoutParams.TYPE_SYSTEM_ALERT)
        }
        dialog.show()
    }

    fun hide() {
        if (isBusy) return
        overlay?.let {
            try {
                windowManager?.removeView(it)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        overlay = null
    }

    fun forceHide() {
        isBusy = false
        pauseTimer?.cancel()
        isPaused = false
        overlay?.let {
            try {
                windowManager?.removeView(it)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        overlay = null
        windowManager = null
    }
}
