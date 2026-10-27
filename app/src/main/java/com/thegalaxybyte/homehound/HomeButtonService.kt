package com.thegalaxybyte.homehound

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent

/**
 * Fire OS and Google TV always route Home to their own launcher, so this service watches for a
 * stock launcher's home screen coming to the front and immediately opens homehound over it. The
 * service config only delivers window changes from the packages in [STOCK_LAUNCHERS].
 */
class HomeButtonService : AccessibilityService() {
    private var lastLaunch = 0L

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val homeMarker = STOCK_LAUNCHERS[event.packageName?.toString()] ?: return
        // Only the home screen; the launcher's other windows (dialogs, pickers) are left alone.
        val cls = event.className?.toString().orEmpty()
        if (cls.isNotEmpty() && !cls.contains(homeMarker)) return
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
        // Stock launcher package -> part of its home activity's class name. Keep in sync with
        // android:packageNames in res/xml/home_button_service.xml.
        private val STOCK_LAUNCHERS = mapOf(
            "com.amazon.tv.launcher" to "HomeActivity", // Fire TV
            "com.google.android.apps.tv.launcherx" to "HomeActivity", // Google TV
            "com.google.android.tvlauncher" to "MainActivity", // Android TV 8+
            "com.google.android.leanbacklauncher" to "MainActivity", // Android TV 5-7
        )

        fun isEnabled(context: Context): Boolean {
            val me = ComponentName(context, HomeButtonService::class.java).flattenToString()
            val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
            return enabled.split(':').any { it.equals(me, ignoreCase = true) }
        }
    }
}
