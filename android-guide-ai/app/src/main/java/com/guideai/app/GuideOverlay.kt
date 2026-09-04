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

        val dialog = BottomSheetDialog(context)
        activeDialog = dialog

        val mainLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 24, 32, 28)
            setBackgroundColor(Color.parseColor("#121824"))
        }

        val titleRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val title = TextView(context).apply {
            text = "Guide AI"
            setTextColor(Color.parseColor("#F7B955"))
            textSize = 16f
            setTypeface(null, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val offButton = Button(context).apply {
            text = "OFF"
            textSize = 10f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#D32F2F"))
            layoutParams = LinearLayout.LayoutParams(
                (60 * context.resources.displayMetrics.density).toInt(),
                (32 * context.resources.displayMetrics.density).toInt()
            )
            setOnClickListener {
                hideKeyboard(context, this)
                dialog.dismiss()
                activeDialog = null
                GuideSettings.setActive(context, false)
                forceHide()
            }
        }

        titleRow.addView(title)
        titleRow.addView(offButton)
        mainLayout.addView(titleRow)

        // Height kam ki gayi hai (120dp max)
        val scrollContainer = ScrollView(context).apply {
            val maxHeight = (120 * context.resources.displayMetrics.density).toInt()
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                height = maxHeight
            }
            isVerticalScrollBarEnabled = true
        }

        val guidance = TextView(context).apply {
            text = "Hello! How can I help you today?"
            setTextColor(Color.WHITE)
            textSize = 13f
            setPadding(0, 12, 0, 12)
        }
        scrollContainer.addView(guidance)
        mainLayout.addView(scrollContainer)

        val userQuestionContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 12, 16, 12)
            setBackgroundColor(Color.parseColor("#1E2A38"))
            visibility = View.GONE
        }
        
        val userQuestionHeader = TextView(context).apply {
            text = "YOUR QUESTION:"
            setTextColor(Color.parseColor("#00E5FF"))
            textSize = 10f
            setTypeface(null, Typeface.BOLD)
        }
        
        val userQuestionText = TextView(context).apply {
            setTextColor(Color.parseColor("#E0F7FA"))
            textSize = 12f
            setTypeface(null, Typeface.BOLD_ITALIC)
        }
        
        userQuestionContainer.addView(userQuestionHeader)
        userQuestionContainer.addView(userQuestionText)
        mainLayout.addView(userQuestionContainer)

        val questionInput = EditText(context).apply {
            hint = "Ask a question..."
            setTextColor(Color.WHITE)
            setHintTextColor(Color.parseColor("#8A99AD"))
            textSize = 13f
            setPadding(20, 20, 20, 20)
            setBackgroundColor(Color.parseColor("#263344"))
        }
        mainLayout.addView(questionInput)

        val btnRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 16, 0, 0)
        }

        val askBtn = Button(context).apply {
            text = "ASK ABOUT SCREEN"
            textSize = 11f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#121824"))
            setBackgroundColor(Color.parseColor("#F7B955"))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = 8
            }
        }

        questionInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (!isBusy) {
                    val input = s?.toString()?.trim() ?: ""
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

            if (inputQuery.isNotEmpty()) {
                userQuestionText.text = inputQuery
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
                                    if (GuideSettings.voiceEnabled(context)) {
                                        speakText(formattedAnswer)
                                    }
                                    hasAnsweredOnce = true
                                    askBtn.text = "ASK AGAIN"
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
            text = "CANCEL"
            textSize = 11f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#37474F"))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener {
                val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                if (imm.isAcceptingText) {
                    // Jab Cancel par tap ho aur keyboard open ho, sirf keyboard band hoga
                    hideKeyboard(context, questionInput)
                } else {
                    // Agar keyboard pehle se band hai, tab overlay dialog band hoga
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
            
            window.addFlags(
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
            )
            
            // Touch outside handler
            window.decorView.setOnTouchListener { _, event ->
                if (event.action == MotionEvent.ACTION_OUTSIDE) {
                    val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                    if (imm.isAcceptingText) {
                        // Jab keyboard open rahe tab screen par kahin tap hone par keyboard + overlay dono hatein
                        hideKeyboard(context, questionInput)
                        dialog.dismiss()
                        activeDialog = null
                    }
                }
                false
            }
        }
        
        // Jab keyboard off ho, screen par tap hone par overlay na hate aur screen kaam kare
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
        
        // Bracket aur bakwas formatting clean karein
        text = text.replace(Regex("###\\s*\\d+\\.\\s*"), "")
            .replace(Regex("###\\s*"), "")
            .replace(Regex("---|___|\\*\\*\\*"), "")
            .replace(Regex("••+"), "•")
            .replace(Regex("··+"), "•")
            .replace(Regex("\\*\\s*\"?"), "• ")
            .replace(Regex("\\*\\*(.*?)\\*\\*"), "$1")
            .replace(Regex("[()]"), "") // Faltu brackets ko hatane ke liye
            .replace(Regex("(?i)Foreground Overlay.*?\n\n"), "")
            .replace(Regex("(?i)This screenshot shows.*?\n\n"), "")
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
