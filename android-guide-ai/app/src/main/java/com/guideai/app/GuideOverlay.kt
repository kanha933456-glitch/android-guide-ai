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
import android.view.ViewTreeObserver
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
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

    private const val ARROW_PREFIX = "➤ "
    private const val ARROW_COLOR = "#F7B955"
    private const val HIGHLIGHT_COLOR = "#FFD54F"

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
                size, size, layoutType,
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

    private fun isKeyboardVisible(rootView: View): Boolean {
        val rect = android.graphics.Rect()
        rootView.getWindowVisibleDisplayFrame(rect)
        val screenHeight = rootView.rootView.height
        val keypadHeight = screenHeight - rect.bottom
        return keypadHeight > screenHeight * 0.15
    }

    private fun showGuideDialog(context: Context) {
        activeDialog?.dismiss()
        activeDialog = null
        hasAnsweredOnce = false

        val dialog = BottomSheetDialog(context)
        activeDialog = dialog

        // isKeyboardShowing — ViewTreeObserver se track hoga, manually set nahi karenge
        // taaki state hamesha accurate rahe
        var keyboardCurrentlyVisible = false

        val mainLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            // Height kam ki — chhota overlay
            setPadding(36, 24, 36, 28)
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
                (65 * context.resources.displayMetrics.density).toInt(),
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

        // ScrollView height kam ki — 130dp, chhota overlay ke liye
        val scrollContainer = ScrollView(context).apply {
            val maxHeight = (130 * context.resources.displayMetrics.density).toInt()
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                maxHeight
            )
            isVerticalScrollBarEnabled = true
        }

        val guidance = TextView(context).apply {
            text = buildFormattedSpannable("Hello! How can I help you today?")
            setTextColor(Color.WHITE)
            textSize = 13f
            setPadding(0, 10, 0, 10)
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
            setPadding(20, 18, 20, 18)
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
                marginEnd = 10
            }
        }

        questionInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (!isBusy) {
                    val input = s?.toString()?.trim() ?: ""
                    askBtn.text = when {
                        hasAnsweredOnce -> "ASK AGAIN"
                        input.isNotEmpty() -> "ASK ANYTHING"
                        else -> "ASK ABOUT SCREEN"
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
                                CoroutineScope(Dispatchers.Main).launch {
                                    guidance.text = buildFormattedSpannable(answer)
                                    if (GuideSettings.voiceEnabled(context)) {
                                        speakText(answer)
                                    }
                                    hasAnsweredOnce = true
                                    askBtn.text = "ASK AGAIN"
                                    askBtn.isEnabled = true
                                    isBusy = false
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

        // CANCEL button — behaviour:
        // Keyboard khula hai → sirf keyboard band karo, overlay rakho
        // Keyboard band hai → overlay band karo
        val closeBtn = Button(context).apply {
            text = "CANCEL"
            textSize = 11f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#37474F"))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener {
                // State directly check karo — variable par depend mat karo
                val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                if (imm.isAcceptingText) {
                    // Keyboard khula hai — sirf keyboard band karo
                    hideKeyboard(context, questionInput)
                    questionInput.clearFocus()
                } else {
                    // Keyboard band hai — overlay band karo
                    GuideApi.clearHistory()
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

            // FLAG_NOT_TOUCH_MODAL — overlay ke bahar ka touch screen tak jaaye (pass-through)
            // FLAG_WATCH_OUTSIDE_TOUCH — bahar ke touch ka pata chale
            window.addFlags(
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
            )

            // Keyboard state track karo ViewTreeObserver se — ye sabse reliable tarika hai
            window.decorView.viewTreeObserver.addOnGlobalLayoutListener(object :
                ViewTreeObserver.OnGlobalLayoutListener {
                override fun onGlobalLayout() {
                    keyboardCurrentlyVisible = isKeyboardVisible(window.decorView)
                }
            })

            // Bahar tap hone par:
            // - Keyboard khula hai → keyboard + overlay dono band karo
            // - Keyboard band hai → kuch mat karo (touch pass-through hoga screen tak)
            window.decorView.setOnTouchListener { _, event ->
                if (event.action == MotionEvent.ACTION_OUTSIDE) {
                    val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                    if (imm.isAcceptingText) {
                        // Keyboard khula hai — dono band karo
                        hideKeyboard(context, questionInput)
                        questionInput.clearFocus()
                        GuideApi.clearHistory()
                        dialog.dismiss()
                        activeDialog = null
                    }
                    // Keyboard band hai — kuch nahi, touch pass through karega
                }
                false // false return karo taaki touch aage jaaye screen tak
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
                ttsEngine?.setSpeechRate(0.92f)
                ttsEngine?.setPitch(1.0f)
            }
        }, "com.google.android.tts")
    }

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

            if (!charIsDev && !charIsLatinLetter) {
                sb.append(c)
                continue
            }

            when {
                currentIsDevanagari == null -> {
                    currentIsDevanagari = charIsDev
                    sb.append(c)
                }
                currentIsDevanagari == charIsDev -> sb.append(c)
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

    private fun buildFormattedSpannable(rawText: String): SpannableString {
        val fullText = ARROW_PREFIX + rawText
        val spannable = SpannableString(fullText)
 
        spannable.setSpan(
            ForegroundColorSpan(Color.parseColor(ARROW_COLOR)),
            0, ARROW_PREFIX.length,
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )
 
        val highlightColor = Color.parseColor(HIGHLIGHT_COLOR)
        val pattern = Regex("\\(([^()]+)\\)")
        for (match in pattern.findAll(fullText)) {
            val start = match.range.first
            val end = match.range.last + 1
            spannable.setSpan(ForegroundColorSpan(highlightColor), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            spannable.setSpan(StyleSpan(Typeface.BOLD), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
 
        return spannable
    }
 
    private fun hideBubbleOnly() {
        bubbleView?.let {
            try { windowManager?.removeView(it) } catch (e: Exception) { e.printStackTrace() }
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
        GuideApi.clearHistory()
        activeDialog?.dismiss()
        activeDialog = null
        hideBubbleOnly()
        windowManager = null
    }
}
