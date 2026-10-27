package com.thegalaxybyte.homehound

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.graphics.drawable.Drawable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.edit
import androidx.core.graphics.drawable.toBitmap

class AppEntry(
    val pkg: String,
    val label: String,
    val banner: ImageBitmap?,
    val icon: ImageBitmap?,
    val hidden: Boolean,
)

/** The apps the user installed, in their saved order, plus the order/hidden prefs that shape it. */
class AppStore(private val context: Context) {
    private val prefs = context.getSharedPreferences("apps", Context.MODE_PRIVATE)
    private val pm = context.packageManager

    var order: List<String>
        get() = prefs.getString("order", "")!!.split(',').filter { it.isNotEmpty() }
        set(value) = prefs.edit { putString("order", value.joinToString(",")) }

    var hidden: Set<String>
        get() = prefs.getStringSet("hidden", emptySet())!!.toSet()
        set(value) = prefs.edit { putStringSet("hidden", value) }

    fun load(): List<AppEntry> {
        // TV apps first (they have banners); phone-only apps with a regular launcher icon after.
        val seen = LinkedHashMap<String, Pair<String, Boolean>>()
        for ((category, isTv) in listOf(Intent.CATEGORY_LEANBACK_LAUNCHER to true, Intent.CATEGORY_LAUNCHER to false)) {
            val intent = Intent(Intent.ACTION_MAIN).addCategory(category)
            for (ri in pm.queryIntentActivities(intent, 0)) {
                val pkg = ri.activityInfo.packageName
                if (pkg in seen || isSystemBloat(pkg) || pkg == context.packageName) continue
                // Built-in phone leftovers (Files, Downloads); built-in TV apps like YouTube stay.
                if (!isTv && ri.activityInfo.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0) continue
                seen[pkg] = ri.loadLabel(pm).toString() to isTv
            }
        }
        val hiddenSet = hidden
        val saved = order
        val sorted = seen.keys.sortedWith(
            compareBy<String> { saved.indexOf(it).let { i -> if (i < 0) Int.MAX_VALUE else i } }
                .thenBy { seen[it]!!.first.lowercase() },
        )
        return sorted.map { pkg ->
            val (label, isTv) = seen[pkg]!!
            AppEntry(
                pkg = pkg,
                label = label,
                banner = if (isTv) runCatching { pm.getApplicationBanner(pkg) ?: pm.getActivityBanner(launchIntent(pkg)!!) }.getOrNull()?.toImage(320, 180) else null,
                icon = runCatching { pm.getApplicationIcon(pkg) }.getOrNull()?.toImage(128, 128),
                hidden = pkg in hiddenSet,
            )
        }
    }

    fun launchIntent(pkg: String): Intent? =
        (pm.getLeanbackLaunchIntentForPackage(pkg) ?: pm.getLaunchIntentForPackage(pkg))
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** Moves [pkg] by [delta] places within [current] (the list as shown) and saves the new order. */
    fun move(current: List<String>, pkg: String, delta: Int) {
        val list = current.toMutableList()
        val from = list.indexOf(pkg)
        val to = (from + delta).coerceIn(0, list.lastIndex)
        if (from < 0 || from == to) return
        list.add(to, list.removeAt(from))
        order = list
    }

    fun setHidden(pkg: String, hide: Boolean) {
        hidden = if (hide) hidden + pkg else hidden - pkg
    }

    private fun isSystemBloat(pkg: String) = pkg.startsWith("com.amazon.") || pkg.startsWith("com.ivona.")

    private fun Drawable.toImage(w: Int, h: Int): ImageBitmap = toBitmap(w, h).asImageBitmap()
}
