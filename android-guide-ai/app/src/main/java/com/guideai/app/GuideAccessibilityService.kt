package com.guideai.app

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class GuideAccessibilityService : AccessibilityService() {
    private var lastPackage = ""
    private var lastShownAt = 0L
    private var samePackageCount = 0
    private val protectedPackages = setOf(
        "com.android.settings",
        "com.google.android.apps.walletnfcrel"
    )

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!GuideSettings.isActive(this)) {
            GuideOverlay.hide()
            return
        }
        val root = rootInActiveWindow ?: return
        val pkg = root.packageName?.toString() ?: return
        if (pkg in protectedPackages) return
        if (!GuideSettings.hasConsent(this)) return

        val now = System.currentTimeMillis()
        if (pkg != lastPackage) {
            GuideOverlay.hide()
            samePackageCount = 1
        } else {
            samePackageCount++
        }
        lastPackage = pkg

        val stuck = samePackageCount >= 5
        if (now - lastShownAt > 3000) {
            lastShownAt = now
            GuideOverlay.show(this, stuck)
        }
    }

    private fun collectText(node: AccessibilityNodeInfo, output: StringBuilder) {
        node.text?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let { output.append(it).append(' ') }
        for (index in 0 until node.childCount) node.getChild(index)?.let { collectText(it, output) }
    }

    override fun onInterrupt() = Unit
}
