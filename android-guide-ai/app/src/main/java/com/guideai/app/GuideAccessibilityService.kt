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
            GuideOverlay.forceHide()
            return
        }

        val root = rootInActiveWindow ?: return
        val pkg = root.packageName?.toString() ?: return

        if (pkg in protectedPackages) {
            GuideOverlay.forceHide()
            return
        }

        if (!GuideSettings.hasConsent(this)) return
        if (GuideOverlay.isPaused) return

        val now = System.currentTimeMillis()

        // Handle Package Switching & Stuck State Properly
        if (pkg != lastPackage) {
            lastPackage = pkg
            samePackageCount = 1
        } else {
            samePackageCount++
        }

        val stuck = samePackageCount >= 5

        // Show floating icon reliably
        if (now - lastShownAt > 2000) {
            lastShownAt = now
            GuideOverlay.show(this, stuck)
        }
    }

    private fun collectText(node: AccessibilityNodeInfo, output: StringBuilder) {
        node.text?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let { output.append(it).append(' ') }
        for (index in 0 until node.childCount) {
            node.getChild(index)?.let { collectText(it, output) }
        }
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        super.onDestroy()
        GuideOverlay.forceHide()
    }
}
