package com.guideai.app

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object GuideOverlay {
    private var windowManager: WindowManager? = null
    private var overlay: View? = null

    fun show(context: Context, screenText: String) {
        if (overlay != null) return
        windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val card = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; setPadding(28, 22, 28, 22); setBackgroundColor(Color.rgb(22, 29, 39)) }
        card.addView(TextView(context).apply { text = "Guide AI is ready"; setTextColor(Color.rgb(247, 185, 85)); textSize = 18f })
        val guidance = TextView(context).apply { text = "Screen text captured safely. Tap below for Gemini guidance."; setTextColor(Color.WHITE); textSize = 14f }
        card.addView(guidance)
        card.addView(Button(context).apply {
            text = "Explain next step"
            setOnClickListener {
                text = "Thinking…"
                CoroutineScope(Dispatchers.Main).launch {
              GuideApi.explain("Hindi", screenText).onSuccess { guidance.text = it }.onFailure { guidance.text = "Error: ${it.message}" }      
                    text = "Explain again"
                }
            }
        })
        card.addView(Button(context).apply { text = "Close"; setOnClickListener { hide() } })
        val params = WindowManager.LayoutParams(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT).apply { gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL; y = 80 }
        windowManager?.addView(card, params)
        overlay = card
    }

    fun hide() { overlay?.let { windowManager?.removeView(it) }; overlay = null }
}
