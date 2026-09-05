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
import android.text.Spannable
import android.text.SpannableString
import android.text.TextWatcher
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.google.android.material.bottomsheet.BottomSheetDialog
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object GuideOverlay {
    private var windowManager: WindowManager? = null
    private var bubbleView: View? = null
    private var activeDialog: BottomSheetDialog? = null
    var isBusy = false
    var isPaused = false
    private var ttsEngine: TextToSpeech? = null
    private var hasAnsweredOnce = false

    // ---- Style constants ----
    private const val ARROW_PREFIX = "➤ "
    private const val ARROW_COLOR = "#F7B955"
    private const val HIGHLIGHT_COLOR = "#FFD54F" // premium gold for ( ) important words

    fun show(context: Context, stuck: Boolean = false) {
        if (!GuideSettings.isActive(context)) {
            forceHide()
            return
        }

        val appContext = context.applicationContext
        hideBubbleOnly()

        try {
            windowManager = appContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            if (GuideSettings.voiceEnabled(appContext)) {
                initTTS(appContext)
            }

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
        activeDialog?.dismiss()
        activeDialog = null
        hasAnsweredOnce = false

        // Tracks whether the soft keyboard is currently visible for THIS dialog session.
        // Drives the tap-outside / Cancel-button close behaviour described by the user.
        var isKeyboardShowing = false

        val dialog = BottomSheetDialog(context)
        activeDialog = dialog

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
                isKeyboardShowing = false
                dialog.dismiss()
                activeDialog = null
                GuideSettings.setActive(context, false)
                forceHide()
            }
        }

        titleRow.addView(title)
        titleRow.addView(offButton)
        mainLayout.addView(titleRow)

        // Response Container ko Scrollable banaya hai max height limit ke sath
        val scrollContainer = ScrollView(context).apply {
            val maxHeight = (220 * context.resources.displayMetrics.density).toInt()
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                height = maxHeight
            }
            isVerticalScrollBarEnabled = true
        }

        val guidance = TextView(context).apply {
            text = buildFormattedSpannable("Hello! How can I help you today?")
            setTextColor(Color.WHITE)
            textSize = 14f
            setPadding(0, 16, 0, 16)
        }
        scrollContainer.addView(guidance)
        mainLayout.addView(scrollContainer)

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

        // Keyboard opens whenever the input gets focus (this is how the user reaches
        // the "overlay + keyboard both open" state shown in Image 1).
        questionInput.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                isKeyboardShowing = true
            }
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

        questionInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (!isBusy) {
                    val input = s?.toString()?.trim() ?: ""
                    // Dynamic state sync
                    if (hasAnsweredOnce) {
                        askBtn.text = "ASK AGAIN"
                    } else if (input.isNotEmpty()) {
                        askBtn.text = "ASK ANYTHING"
                    } else {
                        askBtn.text = "ASK ABOUT SCREEN"
                    }
                }
            }
        })

        askBtn.setOnClickListener {
            val inputQuery = questionInput.text.toString().trim()
            hideKeyboard(context, questionInput)
            isKeyboardShowing = false

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
                        val promptToSend = buildLanguageAwarePrompt(inputQuery)
                        GuideApi.explainVision(promptToSend, imageStr)
                            .onSuccess { answer ->
                                val formattedAnswer = formatAIResponse(answer)
                                CoroutineScope(Dispatchers.Main).launch {
                                    guidance.text = buildFormattedSpannable(formattedAnswer)
                                    if (GuideSettings.voiceEnabled(context)) {
                                        speakText(formattedAnswer)
                                    }
                                    hasAnsweredOnce = true
                                    askBtn.text = "ASK AGAIN"
                                    askBtn.isEnabled = true
                                    isBusy = false
                                    hideKeyboard(context, questionInput)
                                    isKeyboardShowing = false
                                }
                            }
                            .onFailure { exception ->
                                CoroutineScope(Dispatchers.Main).launch {
                                    guidance.text = buildFormattedSpannable("ERROR: ${exception.message}")
                                    askBtn.text = "ASK AGAIN"
                                    askBtn.isEnabled = true
                                    isBusy = false
                                }
                            }
                    } else {
                        CoroutineScope(Dispatchers.Main).launch {
                            guidance.text = buildFormattedSpannable("ERROR: Screen capture frame empty")
                            askBtn.text = "ASK AGAIN"
                            askBtn.isEnabled = true
                            isBusy = false
                        }
                    }
                } catch (e: Exception) {
                    CoroutineScope(Dispatchers.Main).launch {
                        guidance.text = buildFormattedSpannable("ERROR: ${e.localizedMessage}")
                        askBtn.text = "ASK AGAIN"
                        askBtn.isEnabled = true
                        isBusy = false
                    }
                }
            }
        }

        // Renamed to CANCEL to match the on-screen behaviour described by the user.
        // Behaviour:
        //  - Overlay + keyboard both open, 1st tap  -> hides ONLY the keyboard, overlay stays.
        //  - Overlay + keyboard both open, 2nd tap  -> now keyboard already hidden, so this
        //                                              tap closes the overlay too.
        //  - Only overlay open (no keyboard)         -> single tap closes the overlay directly.
        val closeBtn = Button(context).apply {
            text = "CANCEL"
            textSize = 12f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#37474F"))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener {
                if (isKeyboardShowing) {
                    hideKeyboard(context, questionInput)
                    isKeyboardShowing = false
                } else {
                    dialog.dismiss()
                    activeDialog = null
                }
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

        dialog.window?.let { window ->
            window.setType(dialogType)
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

            // System flags for Background touch pass-through & Auto keyboard dismissal
            window.addFlags(
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
            )

            // Intercept touches outside the overlay:
            //  - If the keyboard is open (overlay + keyboard both visible), a tap ANYWHERE
            //    on the screen closes BOTH the keyboard and the overlay in one go.
            //  - If only the overlay is open (no keyboard), a tap outside does NOTHING —
            //    the overlay stays open and the touch passes through to the app underneath
            //    (this already happens because we return false / FLAG_NOT_TOUCH_MODAL).
            window.decorView.setOnTouchListener { _, event ->
                if (event.action == MotionEvent.ACTION_OUTSIDE) {
                    if (isKeyboardShowing) {
                        hideKeyboard(context, questionInput)
                        isKeyboardShowing = false
                        dialog.dismiss()
                        activeDialog = null
                    }
                    // else: only overlay open -> intentionally do nothing, let the tap
                    // pass through to the underlying screen.
                }
                false
            }
        }

        dialog.setCanceledOnTouchOutside(false)
        dialog.setCancelable(false)

        dialog.setOnDismissListener {
            if (activeDialog == dialog) {
                activeDialog = null
            }
        }

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

    /**
     * Speaks [text] by splitting it into Devanagari vs Latin-script segments and switching
     * the TTS locale per segment. This fixes cases like an English letter "A" being read out
     * as "अ" when the engine is stuck on the Hindi locale for the whole sentence — each
     * segment now gets the locale that matches its actual script.
     */
    private fun speakText(text: String) {
        val cleanSpeech = text.replace(Regex("[➤\\*\\#\\[\\]\\(\\)]"), "")
            .replace(Regex("\\s+"), " ")
            .trim()

        if (cleanSpeech.isEmpty()) return

        val segments = splitByScript(cleanSpeech)
        if (segments.isEmpty()) return

        val params = Bundle()
        params.putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)

        segments.forEachIndexed { index, segment ->
            val trimmed = segment.text.trim()
            if (trimmed.isEmpty()) return@forEachIndexed

            val locale = if (segment.isDevanagari) Locale("hi", "IN") else Locale("en", "IN")
            ttsEngine?.language = locale

            val queueMode = if (index == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
            ttsEngine?.speak(trimmed, queueMode, params, "GUIDE_AI_TTS_$index")
        }
    }

    private data class TextSegment(val text: String, val isDevanagari: Boolean)

    /**
     * Groups [text] into alternating chunks of Devanagari script and everything else
     * (Latin letters, digits, punctuation, symbols), so each chunk can be spoken with the
     * correct TTS locale.
     */
    private fun splitByScript(text: String): List<TextSegment> {
        val segments = mutableListOf<TextSegment>()
        if (text.isEmpty()) return segments

        fun isDevanagariChar(c: Char) = c in '\u0900'..'\u097F'
        fun isLatinLetter(c: Char) = c.isLetter() && !isDevanagariChar(c)

        val sb = StringBuilder()
        var currentIsDevanagari: Boolean? = null

        for (c in text) {
            val charIsDev = isDevanagariChar(c)
            val charIsLatinLetter = isLatinLetter(c)

            // Neutral characters (spaces, digits, punctuation) stay attached to whichever
            // segment is currently being built.
            if (!charIsDev && !charIsLatinLetter) {
                sb.append(c)
                continue
            }

            when {
                currentIsDevanagari == null -> {
                    currentIsDevanagari = charIsDev
                    sb.append(c)
                }
                currentIsDevanagari == charIsDev -> {
                    sb.append(c)
                }
                else -> {
                    segments.add(TextSegment(sb.toString(), currentIsDevanagari!!))
                    sb.clear()
                    sb.append(c)
                    currentIsDevanagari = charIsDev
                }
            }
        }

        if (sb.isNotEmpty()) {
            segments.add(TextSegment(sb.toString(), currentIsDevanagari ?: false))
        }

        return segments
    }

    /**
     * Wraps the raw user query with an instruction telling the model to reply strictly in the
     * same language/script the question was asked in, and to keep the ( ) important-word
     * highlighting convention (without wrapping the single final correct answer).
     *
     * Note: this only shapes the prompt sent from this file. For fully reliable language
     * matching you may also want to align GuideApi.kt's system prompt with the same rule.
     */
    private fun buildLanguageAwarePrompt(userQuery: String): String {
        return "$userQuery\n\n" +
            "[System instruction: Reply strictly in the exact same language and script the " +
            "user used in the question above. If the question is Hindi written in Devanagari " +
            "script, reply fully in Devanagari Hindi. If the question is Hinglish (Hindi words " +
            "typed in Roman/English letters), reply fully in Hinglish using Roman letters only " +
            "— do not switch to Devanagari. If the question is in English, reply fully in " +
            "English. Never mix multiple languages or scripts within a single response. " +
            "Wrap important key words or terms in parentheses like (word), but do not wrap the " +
            "single final correct answer in parentheses.]"
    }

    /**
     * Turns raw AI text into a styled Spannable:
     *  - Every message starts with a gold ➤ arrow.
     *  - Any (word or phrase) segment is highlighted in a premium gold colour and bolded.
     */
    private fun buildFormattedSpannable(rawText: String): SpannableString {
        val fullText = ARROW_PREFIX + rawText
        val spannable = SpannableString(fullText)

        spannable.setSpan(
            ForegroundColorSpan(Color.parseColor(ARROW_COLOR)),
            0,
            ARROW_PREFIX.length,
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )

        val highlightColor = Color.parseColor(HIGHLIGHT_COLOR)
        val pattern = Regex("\\(([^()]+)\\)")
        for (match in pattern.findAll(fullText)) {
            val start = match.range.first
            val end = match.range.last + 1
            spannable.setSpan(
                ForegroundColorSpan(highlightColor),
                start, end,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            spannable.setSpan(
                StyleSpan(Typeface.BOLD),
                start, end,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }

        return spannable
    }

    private fun formatAIResponse(rawText: String): String {
        var text = rawText.trim()

        // Remove markdown artifacts, double bullets, divider lines, and any leaked
        // system-instruction text that the model might echo back.
        text = text.replace(Regex("###\\s*\\d+\\.\\s*"), "")
            .replace(Regex("###\\s*"), "")
            .replace(Regex("---|___|\\*\\*\\*"), "")
            .replace(Regex("••+"), "•")
            .replace(Regex("··+"), "•")
            .replace(Regex("\\*\\s*\"?"), "• ")
            .replace(Regex("\\*\\*(.*?)\\*\\*"), "$1")
            .replace(Regex("(?i)Foreground Overlay.*?\n\n"), "")
            .replace(Regex("(?i)This screenshot shows.*?\n\n"), "")
            .replace(Regex("(?is)\\[System instruction.*?\\]"), "")
            .replace(Regex("\n{3,}"), "\n\n")
            .trim()

        return if (text.isEmpty()) rawText.trim() else text
    }

    private fun hideBubbleOnly() {
        bubbleView?.let {
            try {
                windowManager?.removeView(it)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        bubbleView = null
    }

    fun hide() {
        if (isBusy) return
        activeDialog?.dismiss()
        activeDialog = null
        hideBubbleOnly()
    }

    fun forceHide() {
        isBusy = false
        isPaused = false
        ttsEngine?.stop()
        ttsEngine?.shutdown()
        ttsEngine = null
        activeDialog?.dismiss()
        activeDialog = null
        hideBubbleOnly()
        windowManager = null
    }
}
