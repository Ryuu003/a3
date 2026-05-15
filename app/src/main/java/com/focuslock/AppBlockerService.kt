package com.focuslock

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent

class AppBlockerService : AccessibilityService() {

    private lateinit var prefs: PrefsManager
    private var lastBlockedPkg = ""
    private var lastBlockTime = 0L

    override fun onServiceConnected() {
        prefs = PrefsManager(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return

        val pkg = event.packageName?.toString() ?: return

        // Avoid spamming the block screen for the same package
        val now = System.currentTimeMillis()
        if (pkg == lastBlockedPkg && now - lastBlockTime < 2000) return

        if (prefs.isBlocked(pkg)) {
            lastBlockedPkg = pkg
            lastBlockTime = now

            // Try to get the app label for display
            val label = try {
                packageManager.getApplicationLabel(
                    packageManager.getApplicationInfo(pkg, 0)
                ).toString()
            } catch (e: Exception) { pkg }

            val intent = Intent(this, BlockedActivity::class.java).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
                )
                putExtra(BlockedActivity.EXTRA_PKG, pkg)
                putExtra(BlockedActivity.EXTRA_LABEL, label)
            }
            startActivity(intent)
        }
    }

    override fun onInterrupt() {}
}
