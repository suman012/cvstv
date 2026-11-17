package com.example.iptv

import android.content.SharedPreferences
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme(colorScheme = darkColorScheme()) { Surface(Modifier.fillMaxSize()) { App() } } }
    }
}

@Composable
fun App() {
    val prefs = LocalContext.current.getSharedPreferences("iptv", 0)
    var logged by remember { mutableStateOf(false) }
    if (!logged) Login(prefs) { logged = true } else Home()
}

@Composable
fun Login(prefs: SharedPreferences, onOk: () -> Unit) {
    var url by remember { mutableStateOf(prefs.getString("url", "http://10.254.254.3")!!) }
    var user by remember { mutableStateOf(prefs.getString("user", "abcd")!!) }
    var pass by remember { mutableStateOf(prefs.getString("pass", "1234")!!) }
    var msg by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize().statusBarsPadding().padding(24.dp), verticalArrangement = Arrangement.Center) {
        Text("Xtream TV", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(url, { url = it }, label = { Text("Server URL") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(user, { user = it }, label = { Text("Username") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(pass, { pass = it }, label = { Text("Password") }, singleLine = true,
            visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(16.dp))
        Button(enabled = !busy, modifier = Modifier.fillMaxWidth(), onClick = {
            busy = true; msg = ""
            Api.base = url.trim().trimEnd('/').let { if (it.startsWith("http")) it else "http://$it" }
            Api.user = user.trim(); Api.pass = pass.trim()
            scope.launch {
                try {
                    if (Api.login()) {
                        prefs.edit().putString("url", Api.base).putString("user", Api.user).putString("pass", Api.pass).apply()
                        onOk()
                    } else msg = "Login failed: wrong credentials or expired account"
                } catch (e: Exception) { msg = "Error: ${e.message}" }
                busy = false
            }
        }) { Text(if (busy) "Connecting…" else "Login") }
        if (msg.isNotEmpty()) Text(msg, Modifier.padding(top = 12.dp), color = MaterialTheme.colorScheme.error)
    }
}

@Composable
fun Home() {
    val kinds = listOf("live" to "Live TV", "vod" to "Movies", "series" to "Series")
    var kind by remember { mutableStateOf("live") }
    var cats by remember(kind) { mutableStateOf(listOf<Pair<String, String>>()) }
    var cat by remember(kind) { mutableStateOf<String?>(null) }
    var list by remember(kind) { mutableStateOf(listOf<Item>()) }
    var status by remember(kind) { mutableStateOf("Loading…") }
    var query by remember { mutableStateOf("") }
    var playing by remember { mutableStateOf<Pair<String, String>?>(null) }
    var series by remember { mutableStateOf<Item?>(null) }
    var eps by remember(series) { mutableStateOf(listOf<Item>()) }

    LaunchedEffect(kind) {
        try { cats = Api.cats(kind); cat = cats.firstOrNull()?.first; status = if (cats.isEmpty()) "Nothing here" else "" }
        catch (e: Exception) { status = "Error: ${e.message}" }
    }
    LaunchedEffect(cat) {
        val c = cat ?: return@LaunchedEffect
        try { status = "Loading…"; list = Api.items(kind, c); status = if (list.isEmpty()) "Empty" else "" }
        catch (e: Exception) { status = "Error: ${e.message}" }
    }
    LaunchedEffect(series) {
        val s = series ?: return@LaunchedEffect
        try { eps = Api.episodes(s.id) } catch (e: Exception) { status = "Error: ${e.message}" }
    }

    playing?.let { (t, u) -> Player(t, u) { playing = null }; return }
    BackHandler(series != null) { series = null }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        if (series != null) {
            Text("← ${series!!.title}", Modifier.fillMaxWidth().clickable { series = null }.padding(16.dp),
                style = MaterialTheme.typography.titleMedium)
            LazyColumn {
                items(eps) { e ->
                    Text(e.title, Modifier.fillMaxWidth().clickable { playing = e.title to Api.url("series", e) }.padding(16.dp))
                    HorizontalDivider()
                }
            }
            return@Column
        }
        Row(Modifier.fillMaxWidth()) {
            kinds.forEach { (k, label) ->
                Text(label, Modifier.weight(1f).background(if (k == kind) MaterialTheme.colorScheme.primary else Color.Transparent)
                    .clickable { kind = k; query = "" }.padding(14.dp))
            }
        }
        OutlinedTextField(query, { query = it }, placeholder = { Text("Search") }, singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp))
        LazyRow(Modifier.padding(vertical = 8.dp)) {
            items(cats) { (id, name) ->
                Text(name, Modifier.padding(horizontal = 4.dp)
                    .background(if (id == cat) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { cat = id }.padding(horizontal = 12.dp, vertical = 8.dp))
            }
        }
        if (status.isNotEmpty()) Text(status, Modifier.padding(16.dp))
        LazyColumn(Modifier.fillMaxSize()) {
            items(list.filter { it.title.contains(query, true) }) { it ->
                Text(it.title, Modifier.fillMaxWidth().clickable {
                    if (kind == "series") series = it else playing = it.title to Api.url(kind, it)
                }.padding(16.dp))
                HorizontalDivider()
            }
        }
    }
}

@Composable
fun Player(title: String, url: String, onBack: () -> Unit) {
    val ctx = LocalContext.current
    var err by remember { mutableStateOf("") }
    val player = remember {
        ExoPlayer.Builder(ctx).build().apply {
            setMediaItem(MediaItem.fromUri(url)); prepare(); playWhenReady = true
            var retried = false
            addListener(object : Player.Listener {
                override fun onPlayerError(e: PlaybackException) {
                    if (url.endsWith(".m3u8") && !retried) { // live fallback to raw TS
                        retried = true
                        setMediaItem(MediaItem.fromUri(url.removeSuffix(".m3u8") + ".ts")); prepare(); play()
                    } else err = "Playback error: ${e.errorCodeName}"
                }
            })
        }
    }
    DisposableEffect(Unit) { onDispose { player.release() } }
    BackHandler(onBack = onBack)
    Column(Modifier.fillMaxSize().background(Color.Black).statusBarsPadding()) {
        Text("← $title", Modifier.fillMaxWidth().clickable { onBack() }.padding(12.dp), color = Color.White)
        if (err.isNotEmpty()) Text(err, Modifier.padding(12.dp), color = Color.Red)
        AndroidView(factory = { PlayerView(it).apply { this.player = player } }, modifier = Modifier.fillMaxWidth().weight(1f))
    }
}
