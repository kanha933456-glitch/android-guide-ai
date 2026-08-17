package com.guideai.app

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class GuideAccessibilityService : AccessibilityService() {
    private var lastContext = ""
    private var lastShownAt = 0L
    private val protectedPackages = setOf("com.android.settings", "com.google.android.apps.walletnfcrel")

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val root = rootInActiveWindow ?: return
        if (root.packageName?.toString() in protectedPackages) return
        val visibleText = StringBuilder()
        collectText(root, visibleText)
        val context = visibleText.toString().trim().take(4000)
        val now = System.currentTimeMillis()
        if (context.isNotEmpty() && context != lastContext && now - lastShownAt > 2500) {
            lastContext = context
            lastShownAt = now
            GuideOverlay.show(this, context)
        }
    }

    private fun collectText(node: AccessibilityNodeInfo, output: StringBuilder) {
        node.text?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let { output.append(it).append(' ') }
        for (index in 0 until node.childCount) node.getChild(index)?.let { collectText(it, output) }
    }

    override fun onInterrupt() = Unit
}
