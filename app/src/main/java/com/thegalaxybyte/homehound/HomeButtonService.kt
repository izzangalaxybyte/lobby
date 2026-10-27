package com.thegalaxybyte.homehound

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent

/**
 * Fire OS always routes Home to Amazon's launcher, so this service watches for that launcher's home
 * screen coming to the front and immediately opens homehound over it. The service config only
 * delivers window changes from com.amazon.tv.launcher.
 */
class HomeButtonService : AccessibilityService() {
    private var lastLaunch = 0L

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        if (event.packageName != AMAZON_LAUNCHER) return
        // Only the home screen; other launcher windows (e.g. its own dialogs) are left alone.
        val cls = event.className?.toString().orEmpty()
        if (cls.isNotEmpty() && !cls.contains("HomeActivity")) return
        val now = SystemClock.elapsedRealtime()
        if (now - lastLaunch < 500) return
        lastLaunch = now
        startActivity(
            Intent(this, HomeActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION),
        )
    }

    override fun onInterrupt() {}

    companion object {
        const val AMAZON_LAUNCHER = "com.amazon.tv.launcher"
    }
}
