package com.cbt.musicplayer

import android.Manifest
import android.content.ComponentName
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.cbt.musicplayer.audio.AudioEffects
import com.cbt.musicplayer.data.MusicRepository
import com.cbt.musicplayer.data.Song
import com.cbt.musicplayer.data.formatTime
import com.cbt.musicplayer.player.CBTPlaybackService
import com.cbt.musicplayer.studio.StemApi
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Pixel-inspired Material You Rich Dark Palette
private val BgDark = Color(0xFF0C100E)
private val SurfaceDark = Color(0xFF141C16)
private val SurfaceVariant = Color(0xFF1D2820)
private val PrimaryGreen = Color(0xFF85D399)
private val OnPrimaryGreen = Color(0xFF00391A)
private val MintAccent = Color(0xFFA1CFA9)
private val MutedText = Color(0xFF9EAFA3)
private val WhiteText = Color(0xFFF2F5F3)
private val CardBg = Color(0xFF18221B)
private val CardHoverBg = Color(0xFF223027)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { CBTApp() }
    }
}

@Composable
private fun CBTApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("cbt", 0) }
    val effects = remember { AudioEffects() }

    var tab by remember { mutableIntStateOf(0) }
    var songs by remember { mutableStateOf(emptyList<Song>()) }
    var scanning by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var controller by remember { mutableStateOf<MediaController?>(null) }
    var fullPlayer by remember { mutableStateOf(false) }
    var title by remember { mutableStateOf("Nothing Playing") }
    var artist by remember { mutableStateOf("Choose a song") }
    var playing by remember { mutableStateOf(false) }
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var currentId by remember { mutableStateOf("") }
    var shuffle by remember { mutableStateOf(false) }
    var repeat by remember { mutableIntStateOf(Player.REPEAT_MODE_OFF) }
    var favorites by remember { mutableStateOf(prefs.getStringSet("favorites", emptySet())?.toSet() ?: emptySet()) }
    var endpoint by remember { mutableStateOf(prefs.getString("endpoint", "") ?: "") }
    var apiKey by remember { mutableStateOf(prefs.getString("api_key", "") ?: "") }
    var stemStatus by remember { mutableStateOf("Choose an audio file to start online separation.") }
    var stemBusy by remember { mutableStateOf(false) }
    var selectedAudio by remember { mutableStateOf<android.net.Uri?>(null) }
    var bandValues by remember { mutableStateOf(List(10) { 0f }) }
    var bass by remember { mutableFloatStateOf(0f) }
    var width by remember { mutableFloatStateOf(0f) }
    var effectsAvailable by remember { mutableStateOf(false) }

    DisposableEffect(context) {
        val token = SessionToken(context, ComponentName(context, CBTPlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener({ try { controller = future.get() } catch (_: Exception) {} }, MoreExecutors.directExecutor())
        onDispose { MediaController.releaseFuture(future) }
    }

    DisposableEffect(controller) {
        val p = controller
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) { playing = isPlaying }
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                title = mediaItem?.mediaMetadata?.title?.toString() ?: "Unknown title"
                artist = mediaItem?.mediaMetadata?.artist?.toString() ?: "Unknown artist"
                currentId = mediaItem?.mediaId ?: ""
            }
            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                effects.attach(audioSessionId)
                effectsAvailable = effects.bandCount() > 0
            }
        }
        p?.addListener(listener)
        onDispose { p?.removeListener(listener); effects.release() }
    }

    LaunchedEffect(controller) {
        while (true) {
            controller?.let {
                position = it.currentPosition.coerceAtLeast(0L)
                duration = it.duration.coerceAtLeast(0L)
                playing = it.isPlaying
                shuffle = it.shuffleModeEnabled
                repeat = it.repeatMode
            }
            delay(400)
        }
    }

    fun scan() {
        scanning = true
        scope.launch {
            songs = withContext(Dispatchers.IO) { MusicRepository.scan(context) }
            scanning = false
        }
    }

    val permission = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { if (it) scan() }
    fun requestScan() {
        if (context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED) scan()
        else permissionLauncher.launch(permission)
    }

    fun playSong(song: Song) {
        val p = controller ?: return
        val index = songs.indexOfFirst { it.id == song.id }
        if (index < 0) return
        val queue = songs.map { s ->
            MediaItem.Builder().setMediaId(s.id.toString()).setUri(s.uri)
                .setMediaMetadata(MediaMetadata.Builder().setTitle(s.title).setArtist(s.artist).setAlbumTitle(s.album).build())
                .build()
        }
        p.setMediaItems(queue, index, 0L)
        p.prepare(); p.play()
    }

    BackHandler(enabled = fullPlayer) { fullPlayer = false }

    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = PrimaryGreen,
            onPrimary = OnPrimaryGreen,
            primaryContainer = Color(0xFF1E3824),
            onPrimaryContainer = Color(0xFFAEEFC2),
            background = BgDark,
            surface = SurfaceDark,
            surfaceVariant = SurfaceVariant,
            onSurface = WhiteText,
            onSurfaceVariant = MutedText
        )
    ) {
        Scaffold(
            containerColor = BgDark,
            bottomBar = {
                if (!fullPlayer) {
                    Column(
                        modifier = Modifier
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color.Transparent, BgDark.copy(alpha = 0.95f), BgDark)
                                )
                            )
                    ) {
                        if (title != "Nothing Playing") {
                            MiniPlayer(
                                title = title,
                                artist = artist,
                                playing = playing,
                                open = { fullPlayer = true },
                                toggle = { if (playing) controller?.pause() else controller?.play() },
                                next = { controller?.seekToNextMediaItem() }
                            )
                        }
                        NavigationBar(
                            containerColor = SurfaceDark,
                            tonalElevation = 8.dp,
                            modifier = Modifier
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                                .clip(RoundedCornerShape(28.dp))
                        ) {
                            val labels = listOf("Home", "Library", "Search", "Mixer", "Studio")
                            val icons = listOf(Icons.Default.Home, Icons.Default.LibraryMusic, Icons.Default.Search, Icons.Default.Tune, Icons.Default.GraphicEq)
                            labels.forEachIndexed { i, label ->
                                NavigationBarItem(
                                    selected = tab == i,
                                    onClick = { tab = i },
                                    icon = { Icon(icons[i], null, modifier = Modifier.size(22.dp)) },
                                    label = { Text(label, fontSize = 11.sp, fontWeight = FontWeight.Medium) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = OnPrimaryGreen,
                                        selectedTextColor = PrimaryGreen,
                                        indicatorColor = PrimaryGreen,
                                        unselectedIconColor = MutedText,
                                        unselectedTextColor = MutedText
                                    )
                                )
                            }
                        }
                    }
                }
            }
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(if (fullPlayer) PaddingValues(0.dp) else padding)) {
                AnimatedContent(targetState = fullPlayer, transitionSpec = { fadeIn() + scaleIn(initialScale = 0.96f) togetherWith fadeOut() + scaleOut(targetScale = 0.96f) }, label = "PlayerTransition") { isFull ->
                    if (isFull) {
                        NowPlaying(
                            title = title, artist = artist, playing = playing,
                            position = position, duration = duration, shuffle = shuffle, repeat = repeat,
                            onBack = { fullPlayer = false },
                            onPlay = { if (playing) controller?.pause() else controller?.play() },
                            onNext = { controller?.seekToNextMediaItem() },
                            onPrevious = { controller?.seekToPreviousMediaItem() },
                            onSeek = { controller?.seekTo(it) },
                            onShuffle = { controller?.let { p -> p.shuffleModeEnabled = !p.shuffleModeEnabled } },
                            onRepeat = {
                                controller?.let { p ->
                                    p.repeatMode = when (p.repeatMode) {
                                        Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                                        Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                                        else -> Player.REPEAT_MODE_OFF
                                    }
                                }
                            }
                        )
                    } else {
                        when (tab) {
                            0 -> Home { tab = it }
                            1 -> Library(songs, scanning, title, ::requestScan, ::playSong)
                            2 -> SearchScreen(query, { query = it }, songs.filter { it.title.contains(query, true) || it.artist.contains(query, true) || it.album.contains(query, true) }, ::playSong)
                            3 -> Mixer(effectsAvailable, bandValues, { i, v -> bandValues = bandValues.toMutableList().also { it[i] = v }; effects.setBand(i, (v * 1500).toInt()) }, bass, { bass = it; effects.setBass((it * 10).toInt()) }, width, { width = it; effects.setWidth((it * 10).toInt()) })
                            4 -> Studio(endpoint, { endpoint = it }, apiKey, { apiKey = it }, selectedAudio != null, stemStatus, stemBusy,
                                onSave = { prefs.edit().putString("endpoint", endpoint).putString("api_key", apiKey).apply(); stemStatus = "API settings saved securely." },
                                onPick = { selectedAudio = it },
                                onSeparate = {
                                    val uri = selectedAudio
                                    if (uri != null) {
                                        stemBusy = true; stemStatus = "Uploading audio to your configured server…"
                                        scope.launch {
                                            try { stemStatus = withContext(Dispatchers.IO) { StemApi.upload(context, uri, endpoint, apiKey) } }
                                            catch (e: Exception) { stemStatus = "Separation request failed: ${e.message}" }
                                            stemBusy = false
                                        }
                                    }
                                })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ScreenTitle(title: String, subtitle: String? = null) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(title, color = WhiteText, fontSize = 28.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp)
        if (subtitle != null) {
            Spacer(Modifier.height(2.dp))
            Text(subtitle, color = MutedText, fontSize = 13.sp, fontWeight = FontWeight.Normal)
        }
    }
}

@Composable
private fun Home(onNavigate: (Int) -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(24.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(40.dp)
                    .background(PrimaryGreen, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.MusicNote, null, tint = OnPrimaryGreen, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text("CBT MUSIC", color = WhiteText, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.5.sp)
                Text("Pixel Material You", color = MintAccent, fontSize = 11.sp, fontWeight = FontWeight.Medium)
            }
        }
        Spacer(Modifier.height(28.dp))
        Card(
            colors = CardDefaults.cardColors(containerColor = CardBg),
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(24.dp)) {
                BadgeContainer("NOW DISCOVERING")
                Spacer(Modifier.height(12.dp))
                Text("Feel every note in pristine clarity.", color = WhiteText, fontSize = 24.sp, fontWeight = FontWeight.Bold, lineHeight = 30.sp)
                Spacer(Modifier.height(8.dp))
                Text("Your local library, advanced 10-band equalizer, and AI stem separation all in one classy workspace.", color = MutedText, fontSize = 13.sp, lineHeight = 18.sp)
            }
        }
        Spacer(Modifier.height(20.dp))
        Text("QUICK ACTIONS", color = MintAccent, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
        Spacer(Modifier.height(12.dp))
        Feature("Your Library", "Browse local device music collection", Icons.Default.LibraryMusic) { onNavigate(1) }
        Spacer(Modifier.height(10.dp))
        Feature("Audio Mixer", "Fine-tune 10-band EQ, bass & stereo width", Icons.Default.Tune) { onNavigate(3) }
        Spacer(Modifier.height(10.dp))
        Feature("AI Stem Studio", "Separate vocals, drums & instruments", Icons.Default.GraphicEq) { onNavigate(4) }
        Spacer(Modifier.weight(1f))
        Text("MADE FOR AUDIOPHILES", color = MutedText.copy(alpha = 0.7f), modifier = Modifier.align(Alignment.CenterHorizontally), fontSize = 10.sp, letterSpacing = 2.sp)
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun BadgeContainer(text: String) {
    Surface(
        color = SurfaceVariant,
        shape = RoundedCornerShape(12.dp)
    ) {
        Text(
            text = text,
            color = PrimaryGreen,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
        )
    }
}

@Composable
private fun Feature(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = CardBg,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(48.dp)
                    .background(SurfaceVariant, RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = PrimaryGreen, modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = WhiteText, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Spacer(Modifier.height(2.dp))
                Text(subtitle, color = MutedText, fontSize = 12.sp)
            }
            Icon(Icons.Default.ChevronRight, null, tint = MutedText)
        }
    }
}

@Composable
private fun Library(songs: List<Song>, scanning: Boolean, current: String, scan: () -> Unit, play: (Song) -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(20.dp))
        ScreenTitle("Your Library", "${songs.size} audio tracks found")
        Spacer(Modifier.height(14.dp))
        Button(
            onClick = scan,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen, contentColor = OnPrimaryGreen)
        ) {
            Icon(Icons.Default.Refresh, null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(if (scanning) "Scanning Library…" else "SCAN DEVICE MUSIC", fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
        if (scanning) {
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(Modifier.fillMaxWidth().clip(CircleShape), color = PrimaryGreen, trackColor = SurfaceVariant)
        }
        Spacer(Modifier.height(12.dp))
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 90.dp, top = 4.dp)
        ) {
            items(songs, key = { it.id }) { song ->
                SongRow(song, current == song.title, onClick = { play(song) })
            }
        }
    }
}

@Composable
private fun SongRow(song: Song, active: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = if (active) CardHoverBg else CardBg,
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(46.dp)
                    .background(if (active) PrimaryGreen else SurfaceVariant, RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.MusicNote,
                    null,
                    tint = if (active) OnPrimaryGreen else PrimaryGreen,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    song.title,
                    color = if (active) PrimaryGreen else WhiteText,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(song.artist, color = MutedText, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (active) {
                    Spacer(Modifier.height(2.dp))
                    Text("NOW PLAYING", color = MintAccent, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                }
            }
            Spacer(Modifier.width(8.dp))
            Text(formatTime(song.durationMs), color = MutedText, fontSize = 12.sp)
        }
    }
}

@Composable
private fun SearchScreen(query: String, onQuery: (String) -> Unit, results: List<Song>, play: (Song) -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(20.dp))
        ScreenTitle("Search", "Discover tracks instantly")
        Spacer(Modifier.height(14.dp))
        OutlinedTextField(
            value = query,
            onValueChange = onQuery,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Search title or artist", color = MutedText) },
            leadingIcon = { Icon(Icons.Default.Search, null, tint = PrimaryGreen) },
            singleLine = true,
            shape = RoundedCornerShape(20.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = PrimaryGreen,
                unfocusedBorderColor = SurfaceVariant,
                focusedContainerColor = CardBg,
                unfocusedContainerColor = CardBg,
                focusedTextColor = WhiteText,
                unfocusedTextColor = WhiteText
            )
        )
        Spacer(Modifier.height(16.dp))
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 90.dp)
        ) {
            items(results, key = { it.id }) { song ->
                SongRow(song, false) { play(song) }
            }
        }
    }
}

@Composable
private fun MiniPlayer(title: String, artist: String, playing: Boolean, open: () -> Unit, toggle: () -> Unit, next: () -> Unit) {
    Surface(
        onClick = open,
        color = CardHoverBg,
        shape = RoundedCornerShape(24.dp),
        tonalElevation = 6.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(40.dp)
                    .background(PrimaryGreen, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.MusicNote, null, tint = OnPrimaryGreen, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = WhiteText, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(artist, color = MutedText, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = toggle) {
                Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, null, tint = PrimaryGreen)
            }
            IconButton(onClick = next) {
                Icon(Icons.Default.SkipNext, null, tint = WhiteText)
            }
        }
    }
}

@Composable
private fun NowPlaying(
    title: String, artist: String, playing: Boolean, position: Long, duration: Long,
    shuffle: Boolean, repeat: Int, onBack: () -> Unit, onPlay: () -> Unit,
    onNext: () -> Unit, onPrevious: () -> Unit, onSeek: (Long) -> Unit,
    onShuffle: () -> Unit, onRepeat: () -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(SurfaceVariant, BgDark)
                )
            )
            .padding(horizontal = 24.dp)
    ) {
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            IconButton(onClick = onBack, modifier = Modifier.background(CardBg, CircleShape)) {
                Icon(Icons.Default.KeyboardArrowDown, null, tint = WhiteText)
            }
            Spacer(Modifier.weight(1f))
            Text("NOW PLAYING", color = PrimaryGreen, letterSpacing = 2.sp, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            IconButton(onClick = {}, modifier = Modifier.background(CardBg, CircleShape)) {
                Icon(Icons.Default.MoreVert, null, tint = MutedText)
            }
        }
        Spacer(Modifier.weight(0.5f))
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .padding(20.dp)
                .background(
                    Brush.radialGradient(listOf(CardHoverBg, CardBg)),
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.Album, null, tint = PrimaryGreen, modifier = Modifier.size(110.dp))
                Spacer(Modifier.height(12.dp))
                Text("CBT", color = WhiteText, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 2.sp)
                Text("CHORDS BY THOUGHT", color = MintAccent, fontSize = 9.sp, letterSpacing = 3.sp)
            }
        }
        Spacer(Modifier.weight(0.5f))
        Text(title, color = WhiteText, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(4.dp))
        Text(artist, color = MutedText, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(28.dp))
        val maxDur = duration.coerceAtLeast(1L).toFloat()
        val posFloat = position.toFloat().coerceIn(0f, maxDur)
        Slider(
            value = posFloat,
            onValueChange = { onSeek(it.toLong()) },
            valueRange = 0f..maxDur,
            colors = SliderDefaults.colors(
                thumbColor = PrimaryGreen,
                activeTrackColor = PrimaryGreen,
                inactiveTrackColor = SurfaceVariant
            )
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatTime(position), color = MutedText, fontSize = 11.sp)
            Text(formatTime(duration), color = MutedText, fontSize = 11.sp)
        }
        Spacer(Modifier.height(20.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onShuffle) {
                Icon(Icons.Default.Shuffle, null, tint = if (shuffle) PrimaryGreen else MutedText)
            }
            IconButton(onClick = onPrevious) {
                Icon(Icons.Default.SkipPrevious, null, tint = WhiteText, modifier = Modifier.size(36.dp))
            }
            FilledIconButton(
                onClick = onPlay,
                modifier = Modifier.size(72.dp),
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = PrimaryGreen, contentColor = OnPrimaryGreen)
            ) {
                Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, null, modifier = Modifier.size(38.dp))
            }
            IconButton(onClick = onNext) {
                Icon(Icons.Default.SkipNext, null, tint = WhiteText, modifier = Modifier.size(36.dp))
            }
            IconButton(onClick = onRepeat) {
                Icon(
                    Icons.Default.Repeat,
                    null,
                    tint = if (repeat != Player.REPEAT_MODE_OFF) PrimaryGreen else MutedText
                )
            }
        }
        Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun Mixer(
    available: Boolean, bands: List<Float>, onBand: (Int, Float) -> Unit,
    bass: Float, onBass: (Float) -> Unit, width: Float, onWidth: (Float) -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(20.dp))
        ScreenTitle("Audio Mixer", if (available) "Device audio effects connected" else "Hardware EQ ready")
        Spacer(Modifier.height(14.dp))
        Card(
            colors = CardDefaults.cardColors(containerColor = CardBg),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.fillMaxWidth().weight(1f)
        ) {
            Column(Modifier.padding(16.dp).fillMaxHeight()) {
                Text("10-BAND EQUALIZER", color = PrimaryGreen, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 1.sp)
                Spacer(Modifier.height(8.dp))
                val labels = listOf("31", "62", "125", "250", "500", "1k", "2k", "4k", "8k", "16k")
                Row(
                    Modifier.fillMaxWidth().weight(1f),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    labels.forEachIndexed { i, label ->
                        Column(
                            Modifier.fillMaxHeight().weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            // Vertical slider emulation or compact slider
                            Box(Modifier.weight(1f).width(24.dp), contentAlignment = Alignment.Center) {
                                Slider(
                                    value = bands[i],
                                    onValueChange = { onBand(i, it) },
                                    valueRange = -1f..1f,
                                    colors = SliderDefaults.colors(thumbColor = PrimaryGreen, activeTrackColor = PrimaryGreen),
                                    modifier = Modifier
                                        .graphicsLayer(rotationZ = -90f)
                                        .width(120.dp)
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(label, color = MutedText, fontSize = 9.sp)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Card(
            colors = CardDefaults.cardColors(containerColor = CardBg),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(18.dp)) {
                Text("BASS BOOST: ${(bass * 100).toInt()}%", color = WhiteText, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Slider(bass, onBass, valueRange = 0f..1f, colors = SliderDefaults.colors(thumbColor = PrimaryGreen, activeTrackColor = PrimaryGreen))
                Spacer(Modifier.height(8.dp))
                Text("STEREO WIDTH: ${(width * 100).toInt()}%", color = WhiteText, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Slider(width, onWidth, valueRange = 0f..1f, colors = SliderDefaults.colors(thumbColor = PrimaryGreen, activeTrackColor = PrimaryGreen))
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun Studio(
    endpoint: String, onEndpoint: (String) -> Unit, apiKey: String, onApiKey: (String) -> Unit,
    fileSelected: Boolean, status: String, busy: Boolean, onSave: () -> Unit,
    onPick: (android.net.Uri) -> Unit, onSeparate: () -> Unit
) {
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> if (uri != null) onPick(uri) }
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(20.dp))
        ScreenTitle("AI Stem Studio", "Online source separation")
        Spacer(Modifier.height(14.dp))
        Card(
            colors = CardDefaults.cardColors(containerColor = CardBg),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(20.dp)) {
                Text("SERVER CONFIGURATION", color = PrimaryGreen, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = endpoint,
                    onValueChange = onEndpoint,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("https://your-server.example/api", color = MutedText) },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PrimaryGreen,
                        unfocusedBorderColor = SurfaceVariant,
                        focusedContainerColor = SurfaceDark,
                        unfocusedContainerColor = SurfaceDark,
                        focusedTextColor = WhiteText,
                        unfocusedTextColor = WhiteText
                    )
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = onApiKey,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Bearer token (optional)", color = MutedText) },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PrimaryGreen,
                        unfocusedBorderColor = SurfaceVariant,
                        focusedContainerColor = SurfaceDark,
                        unfocusedContainerColor = SurfaceDark,
                        focusedTextColor = WhiteText,
                        unfocusedTextColor = WhiteText
                    )
                )
                Spacer(Modifier.height(14.dp))
                Button(
                    onClick = onSave,
                    modifier = Modifier.fillMaxWidth().height(46.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = SurfaceVariant, contentColor = PrimaryGreen)
                ) {
                    Text("SAVE API SETTINGS", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(
                onClick = { picker.launch("audio/*") },
                modifier = Modifier.weight(1f).height(50.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = WhiteText),
                border = BorderStroke(1.dp, SurfaceVariant)
            ) {
                Icon(Icons.Default.AudioFile, null, tint = PrimaryGreen)
                Spacer(Modifier.width(8.dp))
                Text(if (fileSelected) "File Selected" else "Select Audio", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            Button(
                onClick = onSeparate,
                enabled = fileSelected && endpoint.startsWith("https://") && !busy,
                modifier = Modifier.weight(1f).height(50.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen, contentColor = OnPrimaryGreen)
            ) {
                if (busy) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), color = OnPrimaryGreen)
                } else {
                    Icon(Icons.Default.AutoFixHigh, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Separate", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Card(
            colors = CardDefaults.cardColors(containerColor = CardBg),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(16.dp)) {
                Text("STATUS", color = PrimaryGreen, fontWeight = FontWeight.Bold, fontSize = 10.sp, letterSpacing = 1.sp)
                Spacer(Modifier.height(6.dp))
                Text(status, color = MutedText, fontSize = 12.sp, lineHeight = 16.sp)
            }
        }
    }
}
