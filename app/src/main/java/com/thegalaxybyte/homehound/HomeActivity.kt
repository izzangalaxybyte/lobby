package com.thegalaxybyte.homehound

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import androidx.tv.material3.darkColorScheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.Date

private val Accent = Color(0xFFE8A33D)
private val Bg = Color(0xFF0B0C10)
private val Tile = Color(0xFF1A1C22)

class HomeActivity : ComponentActivity() {
    // Bumped whenever the app list may have changed; the UI reloads on each bump.
    private var refresh by mutableIntStateOf(0)

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) { refresh++ }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val store = AppStore(this)
        ContextCompat.registerReceiver(
            this,
            packageReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_PACKAGE_ADDED)
                addAction(Intent.ACTION_PACKAGE_REMOVED)
                addAction(Intent.ACTION_PACKAGE_CHANGED)
                addDataScheme("package")
            },
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = Accent, background = Bg, surface = Tile)) {
                Home(store, refresh, ::launch)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refresh++
    }

    override fun onDestroy() {
        unregisterReceiver(packageReceiver)
        super.onDestroy()
    }

    private fun launch(intent: Intent?) {
        intent ?: return
        runCatching { startActivity(intent) }
    }
}

@Composable
private fun Home(store: AppStore, refresh: Int, launch: (Intent?) -> Unit) {
    var apps by remember { mutableStateOf<List<AppEntry>>(emptyList()) }
    var showHidden by remember { mutableStateOf(false) }
    var menuFor by remember { mutableStateOf<AppEntry?>(null) }
    var edits by remember { mutableIntStateOf(0) }
    val firstTile = remember { FocusRequester() }
    var focusedOnce by remember { mutableStateOf(false) }

    LaunchedEffect(refresh, edits) {
        apps = withContext(Dispatchers.IO) { store.load() }
    }
    // Home never closes on Back.
    BackHandler { if (showHidden) showHidden = false }

    val shown = apps.filter { showHidden || !it.hidden }
    val hiddenCount = apps.count { it.hidden }

    Column(
        Modifier
            .fillMaxSize()
            .background(Bg)
            .padding(horizontal = 56.dp, vertical = 32.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Clock()
            Spacer(Modifier.weight(1f))
            SmallTile("Settings") { launch(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }
        Spacer(Modifier.size(24.dp))
        LazyVerticalGrid(
            columns = GridCells.Fixed(5),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            contentPadding = PaddingValues(vertical = 12.dp, horizontal = 4.dp),
        ) {
            itemsIndexed(shown, key = { _, a -> a.pkg }) { i, app ->
                AppTile(
                    app = app,
                    modifier = if (i == 0) Modifier.focusRequester(firstTile) else Modifier,
                    onClick = { launch(store.launchIntent(app.pkg)) },
                    onLongClick = { menuFor = app },
                )
            }
            if (hiddenCount > 0) {
                item(key = "hidden-toggle") {
                    Surface(
                        onClick = { showHidden = !showHidden },
                        modifier = Modifier.aspectRatio(16f / 9f),
                        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
                        colors = ClickableSurfaceDefaults.colors(containerColor = Bg, focusedContainerColor = Tile),
                        border = tileBorder(),
                        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.06f),
                    ) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(if (showHidden) "Hide hidden" else "Hidden ($hiddenCount)", color = Color.Gray, fontSize = 16.sp)
                        }
                    }
                }
            }
        }
    }

    LaunchedEffect(shown.isNotEmpty()) {
        if (shown.isNotEmpty() && !focusedOnce) {
            runCatching { firstTile.requestFocus() }
            focusedOnce = true
        }
    }

    menuFor?.let { app ->
        AppMenu(
            app = app,
            onDismiss = { menuFor = null },
            onMove = { delta -> store.move(shown.map { it.pkg }, app.pkg, delta); edits++ },
            onToggleHidden = { store.setHidden(app.pkg, !app.hidden); menuFor = null; edits++ },
            onInfo = {
                menuFor = null
                launch(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${app.pkg}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            },
            onUninstall = {
                menuFor = null
                @Suppress("DEPRECATION")
                launch(Intent(Intent.ACTION_UNINSTALL_PACKAGE, Uri.parse("package:${app.pkg}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            },
        )
    }
}

@Composable
private fun tileBorder() = ClickableSurfaceDefaults.border(
    focusedBorder = Border(BorderStroke(3.dp, Accent), shape = RoundedCornerShape(12.dp)),
)

@Composable
private fun AppTile(app: AppEntry, modifier: Modifier, onClick: () -> Unit, onLongClick: () -> Unit) {
    Surface(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier
            .aspectRatio(16f / 9f)
            .alpha(if (app.hidden) 0.4f else 1f),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
        colors = ClickableSurfaceDefaults.colors(containerColor = Tile, focusedContainerColor = Tile),
        border = tileBorder(),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.08f),
    ) {
        if (app.banner != null) {
            Image(app.banner, app.label, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Column(
                Modifier.fillMaxSize().padding(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                app.icon?.let { Image(it, null, Modifier.size(56.dp)) }
                Spacer(Modifier.size(8.dp))
                Text(app.label, color = Color.White, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun SmallTile(label: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(50)),
        colors = ClickableSurfaceDefaults.colors(containerColor = Tile, focusedContainerColor = Accent, focusedContentColor = Color.Black),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
    ) {
        Text(label, fontSize = 16.sp, modifier = Modifier.padding(horizontal = 22.dp, vertical = 10.dp))
    }
}

@Composable
private fun Clock() {
    val context = androidx.compose.ui.platform.LocalContext.current
    var now by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = Date()
            delay(60_000 - System.currentTimeMillis() % 60_000)
        }
    }
    val time = android.text.format.DateFormat.getTimeFormat(context).format(now)
    val date = android.text.format.DateFormat.format("EEEE, d MMMM", now).toString()
    Column {
        Text(time, color = Color.White, fontSize = 44.sp, fontWeight = FontWeight.Light)
        Text(date, color = Color.Gray, fontSize = 16.sp)
    }
}

@Composable
private fun AppMenu(
    app: AppEntry,
    onDismiss: () -> Unit,
    onMove: (Int) -> Unit,
    onToggleHidden: () -> Unit,
    onInfo: () -> Unit,
    onUninstall: () -> Unit,
) {
    val first = remember { FocusRequester() }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .width(340.dp)
                .background(Tile, RoundedCornerShape(16.dp))
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(app.label, color = Color.White, fontSize = 20.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = { onMove(-1) }, modifier = Modifier.weight(1f).focusRequester(first)) { Text("◀ Move") }
                Button(onClick = { onMove(1) }, modifier = Modifier.weight(1f)) { Text("Move ▶") }
            }
            Button(onClick = onToggleHidden, modifier = Modifier.fillMaxWidth()) { Text(if (app.hidden) "Unhide" else "Hide") }
            Button(onClick = onInfo, modifier = Modifier.fillMaxWidth()) { Text("App info") }
            Button(onClick = onUninstall, modifier = Modifier.fillMaxWidth()) { Text("Uninstall") }
        }
    }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
}
