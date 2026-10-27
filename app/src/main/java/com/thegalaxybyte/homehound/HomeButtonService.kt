package com.thegalaxybyte.homehound

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent

/**
 * Fire OS and Google TV always route Home to their own launcher. This service takes it back in two
 * ways:
 *
 * - It catches the Home key before the system does and opens homehound directly. This has to
 *   happen first: once the system handles Home it blocks other apps from opening for 5 seconds.
 * - It watches for a stock launcher's home screen reaching the front by other routes (boot, the
 *   last app closing) and opens homehound over it. The stock launcher can re-assert itself while
 *   its opening animation finishes, so it checks again a moment later and repeats if needed.
 */
class HomeButtonService : AccessibilityService() {
    private val handler = Handler(Looper.getMainLooper())
    private var frontIsStockHome = false
    private var retries = 0

    private val recheck = Runnable {
        if (frontIsStockHome && retries < MAX_RETRIES) {
            retries++
            openHomehound()
        }
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode != KeyEvent.KEYCODE_HOME) return false
        // Swallow both halves of the press so the system never sees it; open on release.
        if (event.action == KeyEvent.ACTION_UP && !event.isCanceled) {
            Log.i(TAG, "Home key caught")
            retries = 0
            openHomehound()
        }
        return true
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        val homeMarker = STOCK_LAUNCHERS[pkg]
        if (homeMarker == null) {
            // Something else came to the front: homehound itself, an app, or a system dialog.
            // Overlays (keyboard, toasts) also land here but never cover the home screen for long.
            if (pkg == packageName) retries = 0
            frontIsStockHome = false
            return
        }
        // Only the home screen; the launcher's other windows (dialogs, pickers) are left alone.
        val cls = event.className?.toString().orEmpty()
        if (cls.isNotEmpty() && !cls.contains(homeMarker) && !cls.startsWith("android.")) return
        frontIsStockHome = true
        openHomehound()
    }

    private fun openHomehound() {
        startActivity(
            Intent(this, HomeActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION),
        )
        handler.removeCallbacks(recheck)
        handler.postDelayed(recheck, RECHECK_MS)
    }

    override fun onServiceConnected() {
        Log.i(TAG, "Home button service connected")
    }

    override fun onInterrupt() {}

    companion object {
        private const val TAG = "homehound"
        private const val RECHECK_MS = 700L
        private const val MAX_RETRIES = 3

        // Stock launcher package -> part of its home activity's class name.
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
