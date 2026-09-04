package com.guideai.app

import android.animation.ObjectAnimator
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.text.Editable
import android.text.Html
import android.text.TextWatcher
import android.util.TypedValue
import android.view.*
import android.view.inputmethod.InputMethodManager
import android.widget.*
import kotlinx.coroutines.*
import java.util.Locale

object GuideOverlay {

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var floatingBubble: View? = null
    private var tts: TextToSpeech? = null
    private var isOverlayOpen = false
    private val scope = CoroutineScope(Dispatchers.Main + Job())

    fun showFloatingBubble(context: Context) {
        if (floatingBubble != null) return
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        windowManager = wm

        val bubble = ImageView(context).apply {
            setImageResource(R.mipmap.ic_launcher) // Guide AI icon
            setBackgroundColor(Color.TRANSPARENT)
            setPadding(10, 10, 10, 10)
        }

        val params = WindowManager.LayoutParams(
            140, 140,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLATION_MODIFIED
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = 30
            y = 300
        }

        bubble.setOnClickListener {
            toggleOverlay(context)
        }

        floatingBubble = bubble
        wm.addView(bubble, params)
        initTTS(context)
    }

    fun removeFloatingBubble() {
        floatingBubble?.let {
            windowManager?.removeView(it)
            floatingBubble = null
        }
        removeOverlay()
        stopTTS()
    }

    private fun toggleOverlay(context: Context) {
        if (isOverlayOpen) {
            removeOverlay()
        } else {
            showOverlay(context)
        }
    }

    private fun showOverlay(context: Context) {
        if (overlayView != null) return
        val wm = windowManager ?: context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

        val inflater = LayoutInflater.from(context)
        val view = FrameLayout(context)

        // Strict Fixed Height Layout with Scrollable Output Container
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#121926"))
            setPadding(32, 24, 32, 24)
        }

        // Header Section
        val header = RelativeLayout(context).apply {
            val title = TextView(context).apply {
                text = "Guide AI"
                setTextColor(Color.parseColor("#FFB703"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
                id = View.generateViewId()
            }
            val offBtn = Button(context).apply {
                text = "OFF"
                setBackgroundColor(Color.parseColor("#E63946"))
                setTextColor(Color.WHITE)
                setOnClickListener {
                    // Turn OFF Master Switch
                    GuideAccessibilityService.stopService(context)
                    removeFloatingBubble()
                }
            }
            val offParams = RelativeLayout.LayoutParams(160, 90).apply {
                addRule(RelativeLayout.ALIGN_PARENT_END)
            }
            addView(title)
            addView(offBtn, offParams)
        }

        // Fixed Height Output Response Area (Scrollable)
        val scrollView = ScrollView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 120f, context.resources.displayMetrics).toInt()
            )
            isVerticalScrollBarEnabled = true
        }

        val responseText = TextView(context).apply {
            text = "Hello! How can I help you today?"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setPadding(0, 16, 0, 16)
        }
        scrollView.addView(responseText)

        // Input Question Box
        val input = EditText(context).apply {
            hint = "Ask a question..."
            setHintTextColor(Color.GRAY)
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#1E293B"))
            setPadding(20, 16, 20, 16)
        }

        // Action Buttons
        val btnContainer = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 16, 0, 0)
        }
        val actionBtn = Button(context).apply {
            text = "ASK ABOUT SCREEN"
            setBackgroundColor(Color.parseColor("#FFB703"))
            setTextColor(Color.BLACK)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val closeBtn = Button(context).apply {
            text = "CLOSE"
            setBackgroundColor(Color.parseColor("#334155"))
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        btnContainer.addView(actionBtn)
        btnContainer.addView(closeBtn)

        card.addView(header)
        card.addView(scrollView)
        card.addView(input)
        card.addView(btnContainer)

        view.addView(card)

        // Window Layout Parameters allowing Touch Pass-Through
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM
        }

        // Touch Listener for Dismiss On Outside Touch
        view.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_OUTSIDE) {
                removeOverlay()
                true
            } else false
        }

        // CLOSE button logic: Hide Keyboard if open, otherwise hide Overlay
        closeBtn.setOnClickListener {
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            if (imm.isAcceptingText) {
                imm.hideSoftInputFromWindow(input.windowToken, 0)
            } else {
                removeOverlay()
            }
        }

        input.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                actionBtn.text = if (s.isNull_or_Blank()) "ASK ABOUT SCREEN" else "ASK AGAIN"
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        actionBtn.setOnClickListener {
            val query = input.text.toString().trim()
            responseText.text = "Thinking..."
            
            scope.launch(Dispatchers.IO) {
                val imageBase64 = ScreenCapture.capture(context) ?: ""
                val result = GuideApi.explainVision(query, imageBase64)

                withContext(Dispatchers.Main) {
                    result.onSuccess { rawText ->
                        val formattedHtml = formatResponseText(rawText)
                        responseText.text = Html.fromHtml(formattedHtml, Html.FROM_HTML_MODE_LEGACY)
                        speakCleanText(cleanForSpeech(rawText))
                    }.onFailure { err ->
                        responseText.text = "Error: ${err.message}"
                    }
                }
            }
        }

        overlayView = view
        isOverlayOpen = true
        wm.addView(view, params)
    }

    private fun removeOverlay() {
        overlayView?.let {
            windowManager?.removeView(it)
            overlayView = null
            isOverlayOpen = false
        }
    }

    private fun CharSequence?.isNull_or_Blank(): Boolean = this == null || this.isBlank()

    // Formatting: Highlight Important Words in Gold and Parentheses
    private fun formatResponseText(text: String): String {
        var processed = text
        val regex = Regex("\\b([A-Z][a-zA-Z0-9]*|Assam|Kaveri|Priestley)\\b")
        processed = regex.replace(processed) { match ->
            "<font color='#FFD700'><b>(${match.value})</b></font>"
        }
        return processed.replace("\n", "<br/>")
    }

    // Voice Engine Duplicate Text Cleanup Fix
    private fun cleanForSpeech(text: String): String {
        val words = text.split(" ")
        val cleaned = mutableListOf<String>()
        for (i in words.indices) {
            if (i == 0 || !words[i].equals(words[i - 1], ignoreCase = true)) {
                cleaned.add(words[i])
            }
        }
        return cleaned.joinToString(" ")
            .replace("•", "")
            .replace("*", "")
            .replace("(", "")
            .replace(")", "")
    }

    private fun initTTS(context: Context) {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale("hi", "IN")
            }
        }
    }

    private fun speakCleanText(text: String) {
        tts?.stop()
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "GuideTTS")
    }

    private fun stopTTS() {
        tts?.stop()
        tts?.shutdown()
        tts = null
    }
}
