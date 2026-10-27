package com.thegalaxybyte.homehound

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.ImageView
import androidx.core.content.ContextCompat
import java.io.File

/**
 * The last frame homehound drew before going to the background; the cover shows it. Kept on disk
 * too, so the first Home press after a reboot isn't a blank screen.
 */
object HomeSnapshot {
    private var bitmap: Bitmap? = null

    fun save(context: Context, frame: Bitmap) {
        bitmap = frame
        val file = File(context.cacheDir, "home.jpg")
        Thread { runCatching { file.outputStream().use { frame.compress(Bitmap.CompressFormat.JPEG, 85, it) } } }.start()
    }

    fun load(context: Context): Bitmap? =
        bitmap ?: runCatching { BitmapFactory.decodeFile(File(context.cacheDir, "home.jpg").path) }.getOrNull()?.also { bitmap = it }
}

/**
 * Fire OS and Google TV always route Home to their own launcher, and after a Home press the system
 * holds back every other app for 5 seconds, so homehound can't simply open over it. Instead:
 *
 * - On the Home press (the system's "homekey" broadcast) or when the stock launcher's home screen
 *   shows up some other way (boot, the last app closing), the service covers the screen with
 *   homehound's last frame straight away and asks for homehound to open.
 * - The system opens homehound once its hold ends; the cover comes off as soon as it is in front.
 *   Remote presses are swallowed meanwhile so they can't drive the stock launcher underneath.
 */
class HomeButtonService : AccessibilityService() {
    private val handler = Handler(Looper.getMainLooper())
    private var cover: View? = null
    private var frontIsStockHome = false

    private val homeKeyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.getStringExtra("reason") == "homekey") takeOver()
        }
    }

    // Asks again in case the request made during the hold was dropped.
    private val recheck = Runnable { if (frontIsStockHome) openHomehound() }
    private val coverTimeout = Runnable { removeCover() }

    override fun onServiceConnected() {
        instance = this
        ContextCompat.registerReceiver(
            this,
            homeKeyReceiver,
            IntentFilter(Intent.ACTION_CLOSE_SYSTEM_DIALOGS),
            ContextCompat.RECEIVER_EXPORTED,
        )
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        val cls = event.className?.toString().orEmpty()
        val homeMarker = STOCK_LAUNCHERS[pkg]
        if (homeMarker != null) {
            // Only the home screen; the launcher's other windows (dialogs, pickers) are left alone.
            if (cls.isNotEmpty() && !cls.contains(homeMarker) && !cls.startsWith("android.")) return
            frontIsStockHome = true
            takeOver()
            return
        }
        // Other windows (the app being left, system overlays) don't end the cover: only homehound
        // resuming does, via onHomehoundShown().
        frontIsStockHome = false
    }

    private fun onHomehoundShown() {
        frontIsStockHome = false
        handler.removeCallbacks(recheck)
        // A few frames later, so the real screen is drawn under the cover before it goes.
        handler.postDelayed({ removeCover() }, 150)
    }

    private fun takeOver() {
        showCover()
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

    @SuppressLint("ClickableViewAccessibility")
    private fun showCover() {
        handler.removeCallbacks(coverTimeout)
        handler.postDelayed(coverTimeout, COVER_MAX_MS)
        if (cover != null) return
        val view = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_XY
            setBackgroundColor(0xFF0B0C10.toInt())
            HomeSnapshot.load(context)?.let { setImageBitmap(it) }
            isFocusable = true
            isFocusableInTouchMode = true
            setOnKeyListener { _, _, _ -> true }
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_FULLSCREEN,
            PixelFormat.OPAQUE,
        )
        runCatching {
            getSystemService(WindowManager::class.java).addView(view, params)
            view.requestFocus()
            cover = view
        }
    }

    private fun removeCover() {
        handler.removeCallbacks(coverTimeout)
        val view = cover ?: return
        cover = null
        runCatching { getSystemService(WindowManager::class.java).removeView(view) }
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        if (instance === this) instance = null
        removeCover()
        runCatching { unregisterReceiver(homeKeyReceiver) }
        super.onDestroy()
    }

    companion object {
        // The system's hold after Home is 5s; ask again just after it, and never cover for longer.
        private const val RECHECK_MS = 5_500L
        private const val COVER_MAX_MS = 8_000L

        // HomeActivity runs in the same process and tells the running service when it resumes.
        private var instance: HomeButtonService? = null

        fun homehoundShown() {
            instance?.onHomehoundShown()
        }

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
