package com.guideai.app

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
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

    override fun onServiceConnected() {
        super.onServiceConnected()
        // Service connect hote hi overlay dikhao agar active hai
        if (GuideSettings.isActive(this)) {
            GuideOverlay.showFloatingBubble(this)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!GuideSettings.isActive(this)) {
            GuideOverlay.removeFloatingBubble()
            return
        }

        val root = rootInActiveWindow
        val pkg = root?.packageName?.toString() ?: event?.packageName?.toString() ?: return

        // Sensitive/System Settings screens par auto-hide karo
        if (pkg in protectedPackages) {
            GuideOverlay.removeFloatingBubble()
            return
        }

        if (!GuideSettings.hasConsent(this)) return
        if (GuideOverlay.isPaused) return

        val now = System.currentTimeMillis()

        if (pkg != lastPackage) {
            lastPackage = pkg
            samePackageCount = 1
        } else {
            samePackageCount++
        }

        val stuck = samePackageCount >= 5

        // Screen event hone par floating icon ko active rakho
        if (now - lastShownAt > 1500) {
            lastShownAt = now
            GuideOverlay.showFloatingBubble(this)
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
        // Service band hote hi overlay aur bubble ko complete cleanup karo
        GuideOverlay.removeFloatingBubble()
    }

    companion object {
        // App settings ya overlay OFF button se service stop karne ke liye trigger
        fun stopService(context: Context) {
            GuideSettings.setActive(context, false)
            val intent = Intent(context, GuideAccessibilityService::class.java)
            context.stopService(intent)
        }
    }
}
