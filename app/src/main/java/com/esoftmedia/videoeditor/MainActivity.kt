@file:OptIn(androidx.media3.common.util.UnstableApi::class)
package com.esoftmedia.videoeditor

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.PlaybackException
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.effect.*
import androidx.media3.transformer.*
import androidx.media3.transformer.Composition as MediaComposition
import androidx.media3.ui.PlayerView
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val BG = Color(0xFF090A0F)
private val CARD = Color(0xFF151720)
private val AQUA = Color(0xFF00E5D4)
private val VIOLET = Color(0xFF7B2CFF)
private val PAPER = Color(0xFFF7F7FA)

data class VideoClip(val id: String, val uri: Uri, val duration: Long, val start: Long = 0L, val end: Long = duration) {
    val length: Long get() = (end - start).coerceAtLeast(0L)
}

data class ClipStyle(
    val filter: String = "Original",
    val brightness: Float = 0f,
    val contrast: Float = 0f,
    val saturation: Float = 0f,
    val lightness: Float = 0f,
    val aspect: String = "Original",
    val rotation: Float = 0f,
    val flipH: Boolean = false,
    val flipV: Boolean = false,
    val speed: Float = 1f,
    val background: String = "Black",
    val mosaic: Float = 0f
)

data class SavedProject(
    val id: String,
    val name: String,
    val updatedAt: Long,
    val clipsJson: String,
    val musicUri: String?,
    val caption: String,
    val filter: String,
    val aspect: String,
    val brightness: Float,
    val contrast: Float,
    val saturation: Float,
    val lightness: Float,
    val rotation: Float,
    val flipH: Boolean,
    val flipV: Boolean,
    val favorite: Boolean,
    val stylesJson: String = ""
) {
    val clips: List<VideoClip> get() = decode(clipsJson)
    val durationMs: Long get() = clips.sumOf { it.length }
    val previewUri: String? get() = clips.firstOrNull()?.uri?.toString()
}
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); setContent { VideoEditorApp() } }
}

@Composable
fun VideoEditorApp() {
    val ctx = LocalContext.current
    val player = remember { ExoPlayer.Builder(ctx).build() }
    val music = remember { ExoPlayer.Builder(ctx).build() }
    val clips = remember { mutableStateListOf<VideoClip>() }
    val undo = remember { mutableStateListOf<List<VideoClip>>() }
    val redo = remember { mutableStateListOf<List<VideoClip>>() }
    var tab by remember { mutableStateOf("Edit") }
    var selectedIndex by remember { mutableIntStateOf(0) }
    var position by remember { mutableLongStateOf(0L) }
    var playing by remember { mutableStateOf(false) }
    var filter by remember { mutableStateOf("Original") }
    var brightness by remember { mutableFloatStateOf(0f) }
    var contrast by remember { mutableFloatStateOf(0f) }
    var saturation by remember { mutableFloatStateOf(0f) }
    var lightness by remember { mutableFloatStateOf(0f) }
    var aspect by remember { mutableStateOf("Original") }
    var rotation by remember { mutableFloatStateOf(0f) }
    var flipH by remember { mutableStateOf(false) }
    var flipV by remember { mutableStateOf(false) }
    var caption by remember { mutableStateOf("") }
    var draft by remember { mutableStateOf("") }
    var textDialog by remember { mutableStateOf(false) }
    var musicUri by remember { mutableStateOf<Uri?>(null) }
    var musicDuration by remember { mutableLongStateOf(0L) }
    var musicVolume by remember { mutableFloatStateOf(0.8f) }
    var muteOriginal by remember { mutableStateOf(false) }
    var resolution by remember { mutableStateOf("1080p") }
    var fps by remember { mutableIntStateOf(30) }
    var favorite by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Ready • Offline") }
    var exporting by remember { mutableStateOf(false) }
    var trimA by remember { mutableFloatStateOf(0f) }
    var trimB by remember { mutableFloatStateOf(0f) }
    var clipStyles by remember { mutableStateOf<Map<String, ClipStyle>>(emptyMap()) }
    var committedStyle by remember { mutableStateOf(ClipStyle()) }
    var speed by remember { mutableFloatStateOf(1f) }
    var selectedTool by remember { mutableStateOf("Trim") }
    var showHome by remember { mutableStateOf(true) }
    var pendingNewProject by remember { mutableStateOf(false) }
    var projectId by remember { mutableStateOf(UUID.randomUUID().toString()) }
    var projectName by remember { mutableStateOf("Untitled Project") }
    var drafts = remember { mutableStateListOf<SavedProject>() }
    var helpDialog by remember { mutableStateOf(false) }
    val active = clips.getOrNull(selectedIndex.coerceIn(0, (clips.size - 1).coerceAtLeast(0)))

    fun updateActiveStyle(transform: (ClipStyle) -> ClipStyle, applyPreview: Boolean = false) {
        val clip = active ?: return
        val current = clipStyles[clip.id] ?: committedStyle
        val transformed = transform(current)
        val updated = transformed.copy(speed = transformed.speed.coerceIn(0.25f, 4f))
        clipStyles = clipStyles + (clip.id to updated)
        filter = updated.filter
        brightness = updated.brightness
        contrast = updated.contrast
        saturation = updated.saturation
        lightness = updated.lightness
        aspect = updated.aspect
        rotation = updated.rotation
        flipH = updated.flipH
        flipV = updated.flipV
        speed = updated.speed
        if (applyPreview) {
            committedStyle = updated
            player.setPlaybackParameters(PlaybackParameters(updated.speed))
        }
    }

    fun commitActiveStyle() {
        val clip = active ?: return
        val style = clipStyles[clip.id] ?: committedStyle
        committedStyle = style
        player.setPlaybackParameters(PlaybackParameters(style.speed))
    }

    val chooseVideos = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(20)) { uris ->
        val added = uris.mapNotNull { uri ->
            runCatching { ctx.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            val d = durationMs(ctx, uri)
            if (d > 0L) VideoClip(UUID.randomUUID().toString(), uri, d) else null
        }
        if (added.isNotEmpty()) {
            pushUndo(clips, undo, redo)
            val firstAdded = clips.size
            clips.addAll(added)
            selectedIndex = firstAdded
            status = "Imported " + added.size + " clip(s)"
            if (pendingNewProject) {
                showHome = false
                pendingNewProject = false
            }
        } else {
            pendingNewProject = false
            if (uris.isNotEmpty()) Toast.makeText(ctx, "Could not read selected video.", Toast.LENGTH_LONG).show()
        }
    }
    val chooseAudio = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching { ctx.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            runCatching {
                musicUri = uri
                musicDuration = durationMs(ctx, uri)
                music.setMediaItem(MediaItem.fromUri(uri))
                music.prepare()
                music.volume = musicVolume
                status = "Music added"
            }.onFailure {
                musicUri = null
                status = "Couldn't open audio file"
                Toast.makeText(ctx, "This audio file couldn't be opened.", Toast.LENGTH_LONG).show()
            }
        }
    }

    DisposableEffect(Unit) {
        val playerListener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                player.pause()
                music.pause()
                status = "This video could not be decoded"
                Toast.makeText(ctx, "Playback failed for this file. Try another video format.", Toast.LENGTH_LONG).show()
            }
        }
        player.addListener(playerListener)
        onDispose {
            player.removeListener(playerListener)
            player.release()
            music.release()
        }
    }

    LaunchedEffect(clips.map { it.id + ":" + it.start + ":" + it.end }) {
        if (clips.isEmpty()) player.clearMediaItems() else {
            val index = selectedIndex.coerceIn(0, clips.lastIndex)
            val items = clips.map { c ->
                MediaItem.Builder().setUri(c.uri).setClippingConfiguration(
                    MediaItem.ClippingConfiguration.Builder().setStartPositionMs(c.start).setEndPositionMs(c.end).build()
                ).build()
            }
            runCatching {
                player.setMediaItems(items, index, 0L)
                player.prepare()
            }.onFailure {
                status = "Video playback error"
                Toast.makeText(ctx, "This video format may not be supported.", Toast.LENGTH_SHORT).show()
            }
        }
    }
    LaunchedEffect(active?.id) {
        active?.let { clip ->
            trimA = clip.start.toFloat()
            trimB = clip.end.toFloat()
            val style = clipStyles[clip.id] ?: ClipStyle()
            if (clipStyles[clip.id] == null) clipStyles = clipStyles + (clip.id to style)
            filter = style.filter
            brightness = style.brightness
            contrast = style.contrast
            saturation = style.saturation
            lightness = style.lightness
            aspect = style.aspect
            rotation = style.rotation
            flipH = style.flipH
            flipV = style.flipV
            speed = style.speed
            committedStyle = style
            player.setPlaybackParameters(PlaybackParameters(style.speed))
        }
    }
    LaunchedEffect(Unit) {
        val prefs = ctx.getSharedPreferences("esoftmedia_editor", Context.MODE_PRIVATE)
        runCatching {
            drafts.addAll(decodeDrafts(prefs.getString("saved_drafts", null)))
            if (drafts.isEmpty()) {
                val legacyClips = decode(prefs.getString("clips", null))
                if (legacyClips.isNotEmpty()) {
                    drafts.add(SavedProject(
                        UUID.randomUUID().toString(), "Recovered Project", System.currentTimeMillis(),
                        encode(legacyClips), prefs.getString("music", null),
                        prefs.getString("caption", "") ?: "",
                        prefs.getString("filter", "Original") ?: "Original",
                        prefs.getString("aspect", "Original") ?: "Original",
                        prefs.getFloat("brightness", 0f), prefs.getFloat("contrast", 0f),
                        prefs.getFloat("saturation", 0f), prefs.getFloat("lightness", 0f),
                        prefs.getFloat("rotation", 0f), prefs.getBoolean("flipH", false),
                        prefs.getBoolean("flipV", false), prefs.getBoolean("favorite", false)
                    ))
                }
            }
        }.onFailure { status = "Draft list could not be loaded" }
    }
    LaunchedEffect(Unit) {
        while (true) {
            val currentIndex = player.currentMediaItemIndex.coerceIn(0, clips.size)
            position = clips.take(currentIndex).sumOf { it.length } + player.currentPosition.coerceAtLeast(0L)
            selectedIndex = player.currentMediaItemIndex.coerceIn(0, (clips.size - 1).coerceAtLeast(0))
            playing = player.isPlaying
            music.volume = musicVolume
            if (playing && musicUri != null && !music.isPlaying) music.play()
            if (!playing && music.isPlaying) music.pause()
            delay(200)
        }
    }
    // Build the preview pipeline off the UI thread. It changes only when a tool is
    // committed (not on every intermediate slider movement), avoiding renderer churn.
    LaunchedEffect(active?.id, committedStyle.copy(speed = 1f, background = "Black")) {
        val clipId = active?.id ?: return@LaunchedEffect
        delay(120)
        val style = committedStyle
        val previewEffects = withContext(Dispatchers.Default) {
            effects(
                style.filter, style.brightness, style.contrast, style.saturation, style.lightness,
                style.aspect, style.rotation, style.flipH, style.flipV, "", resolutionHeight = 0,
                mosaic = style.mosaic
            )
        }
        if (active?.id == clipId) {
            runCatching { player.setVideoEffects(previewEffects) }
                .onFailure { status = "Preview effect could not be applied" }
        }
    }

    fun saveProject(showToast: Boolean = true) {
        if (clips.isEmpty()) return
        val saved = SavedProject(
            projectId, projectName, System.currentTimeMillis(), encode(clips), musicUri?.toString(),
            caption, filter, aspect, brightness, contrast, saturation, lightness, rotation,
            flipH, flipV, favorite, encodeStyles(clipStyles)
        )
        val existing = drafts.indexOfFirst { it.id == projectId }
        if (existing >= 0) drafts[existing] = saved else drafts.add(0, saved)
        drafts.sortByDescending { it.updatedAt }
        ctx.getSharedPreferences("esoftmedia_editor", Context.MODE_PRIVATE).edit()
            .putString("saved_drafts", encodeDrafts(drafts))
            .putString("clips", encode(clips)).putString("music", musicUri?.toString()).putString("caption", caption)
            .putString("filter", filter).putString("aspect", aspect)
            .putFloat("brightness", brightness).putFloat("contrast", contrast).putFloat("saturation", saturation)
            .putFloat("lightness", lightness).putFloat("rotation", rotation)
            .putBoolean("flipH", flipH).putBoolean("flipV", flipV).putBoolean("favorite", favorite).apply()
        if (showToast) Toast.makeText(ctx, "Project saved to Drafts", Toast.LENGTH_SHORT).show()
    }

    fun resetForNewProject() {
        player.pause(); music.pause()
        clips.clear(); undo.clear(); redo.clear()
        clipStyles = emptyMap()
        committedStyle = ClipStyle()
        musicUri = null; musicDuration = 0L; caption = ""; filter = "Original"
        brightness = 0f; contrast = 0f; saturation = 0f; lightness = 0f
        aspect = "Original"; rotation = 0f; flipH = false; flipV = false; speed = 1f
        favorite = false; selectedIndex = 0; tab = "Edit"
        projectId = UUID.randomUUID().toString()
        projectName = "Project " + (drafts.size + 1)
    }

    fun openDraft(draft: SavedProject) {
        player.pause(); music.pause()
        clips.clear()
        clips.addAll(draft.clips)
        undo.clear(); redo.clear()
        selectedIndex = 0
        clipStyles = decodeStyles(draft.stylesJson)
        if (clipStyles.isEmpty()) {
            val legacyStyle = ClipStyle(
                filter = draft.filter, brightness = draft.brightness, contrast = draft.contrast,
                saturation = draft.saturation, lightness = draft.lightness, aspect = draft.aspect,
                rotation = draft.rotation, flipH = draft.flipH, flipV = draft.flipV
            )
            draft.clips.firstOrNull()?.let { clipStyles = mapOf(it.id to legacyStyle) }
        }
        projectId = draft.id; projectName = draft.name
        caption = draft.caption; filter = draft.filter; aspect = draft.aspect
        brightness = draft.brightness; contrast = draft.contrast
        saturation = draft.saturation; lightness = draft.lightness
        rotation = draft.rotation; flipH = draft.flipH; flipV = draft.flipV
        speed = draft.clips.firstOrNull()?.let { clipStyles[it.id]?.speed } ?: 1f
        committedStyle = draft.clips.firstOrNull()?.let { clipStyles[it.id] } ?: ClipStyle()
        favorite = draft.favorite
        musicUri = draft.musicUri?.let { Uri.parse(it) }
        musicDuration = musicUri?.let { durationMs(ctx, it) } ?: 0L
        musicUri?.let { uri -> runCatching { music.setMediaItem(MediaItem.fromUri(uri)); music.prepare() }.onFailure { musicUri = null } }
        status = "Draft opened"
        tab = "Edit"; showHome = false
    }

    LaunchedEffect(
        clips.map { it.id + ":" + it.start + ":" + it.end },
        clipStyles, caption, musicUri, filter, aspect, brightness, contrast, saturation, lightness, rotation, flipH, flipV, favorite
    ) {
        if (!showHome && clips.isNotEmpty()) {
            delay(650)
            saveProject(false)
        }
    }

    androidx.activity.compose.BackHandler(enabled = !showHome) {
        saveProject(false)
        player.pause()
        music.pause()
        showHome = true
    }

    MaterialTheme(colorScheme = darkColorScheme(background = BG, surface = CARD, primary = AQUA, secondary = VIOLET, onSurface = PAPER)) {
        if (showHome) {
            HomeScreen(
                drafts = drafts,
                onNewProject = {
                    resetForNewProject()
                    pendingNewProject = true
                    chooseVideos.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
                },
                onOpenDraft = { openDraft(it) },
                onDeleteDraft = { draftToDelete ->
                    drafts.removeAll { it.id == draftToDelete.id }
                    ctx.getSharedPreferences("esoftmedia_editor", Context.MODE_PRIVATE).edit()
                        .putString("saved_drafts", encodeDrafts(drafts)).apply()
                },
                onQuickTool = { target ->
                    val latest = drafts.firstOrNull()
                    if (latest == null) {
                        resetForNewProject()
                        pendingNewProject = true
                        chooseVideos.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
                    } else {
                        openDraft(latest)
                        tab = target
                    }
                }
            )
        } else {
            Surface(Modifier.fillMaxSize(), color = BG) {
                Column(Modifier.fillMaxSize()) {
                    EditorTopBar(
                        title = projectName,
                        onClose = {
                            saveProject(false)
                            player.pause()
                            music.pause()
                            showHome = true
                        },
                        onHelp = { helpDialog = true },
                        onExport = {
                            saveProject(false)
                            tab = "Export"
                        }
                    )
                    Preview(player, clips.isNotEmpty(), caption, Modifier.weight(1f).fillMaxWidth(), previewBackground(committedStyle.background)) {
                        chooseVideos.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
                    }
                    PlaybackBar(
                        playing,
                        position,
                        clips.sumOf { it.length },
                        { if (clips.isNotEmpty()) { if (player.isPlaying) { player.pause(); music.pause() } else { player.play(); musicUri?.let { music.play() } } } },
                        { player.pause(); player.seekTo(0L); music.pause(); music.seekTo(0L) },
                        { if (clips.isNotEmpty()) player.seekTo((player.currentMediaItemIndex - 1).coerceAtLeast(0), 0L) },
                        { if (clips.isNotEmpty()) player.seekTo((player.currentMediaItemIndex + 1).coerceAtMost(clips.lastIndex), 0L) },
                        { targetPosition ->
                            var remaining = targetPosition.toLong().coerceAtLeast(0L)
                            val targetIndex = clips.indexOfFirst { clip ->
                                if (remaining <= clip.length) true else { remaining -= clip.length; false }
                            }
                            if (targetIndex >= 0) player.seekTo(targetIndex, remaining)
                        },
                        undo.isNotEmpty(),
                        redo.isNotEmpty(),
                        {
                            if (undo.isNotEmpty()) {
                                redo.add(clips.toList())
                                val previous = undo.removeAt(undo.lastIndex)
                                clips.clear(); clips.addAll(previous)
                                selectedIndex = selectedIndex.coerceIn(0, (clips.size - 1).coerceAtLeast(0))
                            }
                        },
                        {
                            if (redo.isNotEmpty()) {
                                undo.add(clips.toList())
                                val next = redo.removeAt(redo.lastIndex)
                                clips.clear(); clips.addAll(next)
                                selectedIndex = selectedIndex.coerceIn(0, (clips.size - 1).coerceAtLeast(0))
                            }
                        }
                    )
                    TimelinePanel(
                        clips = clips,
                        selectedIndex = selectedIndex,
                        onSelect = { index ->
                            selectedIndex = index
                            tab = "Edit"
                            selectedTool = "Trim"
                            player.seekTo(index, 0L)
                        },
                        onAddClip = { chooseVideos.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)) },
                        onAddAudio = { chooseAudio.launch(arrayOf("audio/*")) },
                        hasAudio = musicUri != null,
                        audioLabel = if (musicUri == null) "Add audio" else "Music track added",
                        muted = muteOriginal,
                        onToggleMute = { muteOriginal = !muteOriginal },
                        onRemoveAudio = { musicUri = null; musicDuration = 0L; music.stop(); status = "Music removed" }
                    )
                    when (tab) {
                        "Edit" -> EditToolPanel(
                            active, trimA, trimB,
                            { trimA = it; trimB = trimB.coerceAtLeast(it + 1f) },
                            { trimB = it; trimA = trimA.coerceAtMost(it - 1f) },
                            {
                                active?.let { clip ->
                                    val start = trimA.toLong().coerceIn(0L, clip.duration - 1L)
                                    val end = trimB.toLong().coerceIn(start + 1L, clip.duration)
                                    changeClip(clips, selectedIndex, clip.copy(start = start, end = end), undo, redo)
                                    status = "Trim applied"
                                }
                            },
                            {
                                active?.let { clip ->
                                    val split = (clip.start + player.currentPosition).coerceIn(clip.start + 1L, clip.end - 1L)
                                    if (split in (clip.start + 1L) until clip.end) {
                                        pushUndo(clips, undo, redo)
                                        clips[selectedIndex] = clip.copy(end = split)
                                        clips.add(selectedIndex + 1, clip.copy(id = UUID.randomUUID().toString(), start = split))
                                        status = "Clip split"
                                    } else Toast.makeText(ctx, "Move playhead inside the selected clip to split.", Toast.LENGTH_SHORT).show()
                                }
                            },
                            {
                                active?.let { pushUndo(clips, undo, redo); clips.add(selectedIndex + 1, it.copy(id = UUID.randomUUID().toString())); status = "Clip duplicated" }
                            },
                            {
                                if (active != null) {
                                    pushUndo(clips, undo, redo)
                                    clips.removeAt(selectedIndex)
                                    selectedIndex = (selectedIndex - 1).coerceAtLeast(0)
                                    status = "Clip deleted"
                                }
                            }
                        )
                        "Filters", "Effects" -> FilterPanel(filter) { chosenFilter ->
                            updateActiveStyle({ current -> current.copy(filter = chosenFilter) }, applyPreview = true)
                            status = chosenFilter + " filter active"
                        }
                        "Adjust" -> AdjustPanel(
                            brightness, contrast, saturation, lightness,
                            { value -> updateActiveStyle { it.copy(brightness = value) } },
                            { value -> updateActiveStyle { it.copy(contrast = value) } },
                            { value -> updateActiveStyle { it.copy(saturation = value) } },
                            { value -> updateActiveStyle { it.copy(lightness = value) } },
                            onCommit = { commitActiveStyle() }
                        )
                        "Crop" -> CropPanel(
                            aspect, flipH, flipV,
                            { value -> updateActiveStyle({ it.copy(aspect = value) }, applyPreview = true) },
                            { updateActiveStyle({ it.copy(rotation = (it.rotation + 90f) % 360f) }, applyPreview = true) },
                            { updateActiveStyle({ it.copy(flipH = !it.flipH) }, applyPreview = true) },
                            { updateActiveStyle({ it.copy(flipV = !it.flipV) }, applyPreview = true) },
                            { updateActiveStyle({ it.copy(aspect = "Original", rotation = 0f, flipH = false, flipV = false) }, applyPreview = true) }
                        )
                        "Speed" -> SpeedPanel(speed,
                            { value -> updateActiveStyle { it.copy(speed = value) } },
                            { commitActiveStyle(); status = "Playback speed set to %.2fx".format(speed) }
                        )
                        "Background" -> BackgroundPanel(committedStyle.background) { value ->
                            updateActiveStyle({ it.copy(background = value) }, applyPreview = true)
                            status = "Preview background set to " + value
                        }
                        "Mosaic" -> MosaicPanel(committedStyle.mosaic,
                            { value -> updateActiveStyle { it.copy(mosaic = value) } },
                            { commitActiveStyle(); status = if (committedStyle.mosaic > 0f) "Full-clip blur applied" else "Blur removed" }
                        )
                        "Audio" -> AudioPanel(
                            musicUri != null, musicVolume, muteOriginal,
                            { chooseAudio.launch(arrayOf("audio/*")) },
                            { musicVolume = it; music.volume = it },
                            { muteOriginal = it },
                            { musicUri = null; musicDuration = 0L; music.stop(); status = "Music removed" }
                        )
                        "Text" -> TextPanel(caption, { draft = caption; textDialog = true }, { caption = ""; status = "Text removed" })
                        "Elements" -> ElementsPanel { caption = (caption + " " + it).trim(); status = "Sticker added to text overlay" }
                        "Export" -> ExportPanel(resolution, fps, status, exporting, { resolution = it }, { fps = it }, {
                            if (clips.isEmpty()) Toast.makeText(ctx, "Import a video first.", Toast.LENGTH_SHORT).show()
                            else if (!exporting) {
                                saveProject(false)
                                val out = File(ctx.cacheDir, "esoftmedia-output-" + System.currentTimeMillis() + ".mp4")
                                val items = clips.map { clip ->
                                    val media = MediaItem.Builder().setUri(clip.uri).setClippingConfiguration(
                                        MediaItem.ClippingConfiguration.Builder().setStartPositionMs(clip.start).setEndPositionMs(clip.end).build()
                                    ).build()
                                                val style = clipStyles[clip.id] ?: ClipStyle()
                                    val clipEffects = effects(
                                        style.filter, style.brightness, style.contrast, style.saturation, style.lightness,
                                        style.aspect, style.rotation, style.flipH, style.flipV, caption,
                                        resolutionHeight(resolution), mosaic = style.mosaic, speed = style.speed, includeSpeed = true
                                    )
                                    EditedMediaItem.Builder(media).setEffects(
                                        Effects(emptyList(), clipEffects)
                                    ).setFrameRate(fps).setRemoveAudio(muteOriginal).build()
                                }
                                val sequences = mutableListOf(EditedMediaItemSequence.Builder(items).build())
                                if (musicUri != null && musicDuration > 0L) {
                                    val cut = minOf(musicDuration, clips.sumOf { it.length })
                                    if (cut > 0L) {
                                        val audioItem = MediaItem.Builder().setUri(musicUri!!).setClippingConfiguration(
                                            MediaItem.ClippingConfiguration.Builder().setStartPositionMs(0L).setEndPositionMs(cut).build()
                                        ).build()
                                        sequences.add(EditedMediaItemSequence.Builder(listOf(EditedMediaItem.Builder(audioItem).setRemoveVideo(true).build())).build())
                                    }
                                }
                                val composition = MediaComposition.Builder(sequences).build()
                                exporting = true
                                status = "Rendering video…"
                                val transformer = Transformer.Builder(ctx).addListener(object : Transformer.Listener {
                                    override fun onCompleted(composition: MediaComposition, exportResult: ExportResult) {
                                        try {
                                            if (Build.VERSION.SDK_INT >= 29) {
                                                val values = ContentValues().apply {
                                                    put(MediaStore.Video.Media.DISPLAY_NAME, "ESOFTMEDIA-" + System.currentTimeMillis() + ".mp4")
                                                    put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                                                    put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/ESOFTMEDIA")
                                                    put(MediaStore.Video.Media.IS_PENDING, 1)
                                                }
                                                val savedUri = ctx.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values) ?: error("Cannot create destination")
                                                ctx.contentResolver.openOutputStream(savedUri)?.use { output -> out.inputStream().use { input -> input.copyTo(output) } } ?: error("Cannot write destination")
                                                values.clear(); values.put(MediaStore.Video.Media.IS_PENDING, 0)
                                                ctx.contentResolver.update(savedUri, values, null, null)
                                                status = "Export complete • Movies/ESOFTMEDIA"
                                                Toast.makeText(ctx, "Video saved to Movies/ESOFTMEDIA", Toast.LENGTH_LONG).show()
                                            } else {
                                                status = "Rendered in app storage: " + out.absolutePath
                                                Toast.makeText(ctx, status, Toast.LENGTH_LONG).show()
                                            }
                                        } catch (error: Exception) {
                                            status = "Save failed: " + error.message
                                            Toast.makeText(ctx, status, Toast.LENGTH_LONG).show()
                                        }
                                        exporting = false
                                    }
                                    override fun onError(composition: MediaComposition, exportResult: ExportResult, exportException: ExportException) {
                                        exporting = false
                                        status = "Export failed: " + exportException.message
                                        Toast.makeText(ctx, status, Toast.LENGTH_LONG).show()
                                    }
                                }).build()
                                runCatching { transformer.start(composition, out.absolutePath) }.onFailure {
                                    exporting = false
                                    status = "Export start failed: " + it.message
                                }
                            }
                        })
                    }
                    if (active != null) {
                        EditorTools(selectedTool) { tool ->
                            selectedTool = tool
                            tab = when (tool) {
                                "Trim" -> "Edit"
                                "Music", "Volume" -> "Audio"
                                "Text", "Captions" -> "Text"
                                "Sticker" -> "Elements"
                                "Effect" -> "Effects"
                                "Filter" -> "Filters"
                                "Enhance" -> "Adjust"
                                "Crop" -> "Crop"
                                "Rotate", "Flip" -> "Crop"
                                "Speed" -> "Speed"
                                "BG" -> "Background"
                                "Mosaic" -> "Mosaic"
                                else -> "Edit"
                            }
                            when (tool) {
                                "Rotate" -> updateActiveStyle({ it.copy(rotation = (it.rotation + 90f) % 360f) }, applyPreview = true)
                                "Flip" -> updateActiveStyle({ it.copy(flipH = !it.flipH) }, applyPreview = true)
                            }
                        }
                    }
                    Text(status, color = Color.Gray, fontSize = 9.sp, modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp))
                }
            }
        }
    }
    if (textDialog) {
        AlertDialog(onDismissRequest = { textDialog = false }, title = { Text("Text on video") },
            text = { OutlinedTextField(draft, { draft = it }, label = { Text("Title or caption") }) },
            confirmButton = { TextButton(onClick = { caption = draft.trim(); textDialog = false; status = "Text overlay updated" }) { Text("Apply") } },
            dismissButton = { TextButton(onClick = { textDialog = false }) { Text("Cancel") } })
    }
    if (helpDialog) {
        AlertDialog(onDismissRequest = { helpDialog = false }, title = { Text("ESOFTMEDIA Editor") },
            text = { Text("Import clips, select a clip on the timeline, trim or split it, add music and text, apply visual adjustments, then export an MP4. Drafts are saved locally on this phone.") },
            confirmButton = { TextButton(onClick = { helpDialog = false }) { Text("Got it") } })
    }
}

private fun encodeDrafts(drafts: List<SavedProject>): String {
    val array = JSONArray()
    drafts.forEach { draft ->
        array.put(JSONObject().apply {
            put("id", draft.id); put("name", draft.name); put("updatedAt", draft.updatedAt)
            put("clips", draft.clipsJson); put("musicUri", draft.musicUri ?: JSONObject.NULL)
            put("caption", draft.caption); put("filter", draft.filter); put("aspect", draft.aspect)
            put("brightness", draft.brightness.toDouble()); put("contrast", draft.contrast.toDouble())
            put("saturation", draft.saturation.toDouble()); put("lightness", draft.lightness.toDouble())
            put("rotation", draft.rotation.toDouble()); put("flipH", draft.flipH); put("flipV", draft.flipV)
            put("favorite", draft.favorite); put("styles", draft.stylesJson)
        })
    }
    return array.toString()
}
private fun decodeDrafts(raw: String?): List<SavedProject> = runCatching {
    if (raw.isNullOrBlank()) emptyList() else {
        val array = JSONArray(raw)
        (0 until array.length()).mapNotNull { index ->
            val item = array.getJSONObject(index)
            val clipJson = item.optString("clips", "[]")
            if (decode(clipJson).isEmpty()) null else SavedProject(
                item.optString("id", UUID.randomUUID().toString()),
                item.optString("name", "Untitled Project"),
                item.optLong("updatedAt", System.currentTimeMillis()),
                clipJson,
                item.optString("musicUri").takeIf { it.isNotBlank() && it != "null" },
                item.optString("caption"),
                item.optString("filter", "Original"),
                item.optString("aspect", "Original"),
                item.optDouble("brightness", 0.0).toFloat(),
                item.optDouble("contrast", 0.0).toFloat(),
                item.optDouble("saturation", 0.0).toFloat(),
                item.optDouble("lightness", 0.0).toFloat(),
                item.optDouble("rotation", 0.0).toFloat(),
                item.optBoolean("flipH", false),
                item.optBoolean("flipV", false),
                item.optBoolean("favorite", false),
                item.optString("styles", "")
            )
        }
    }
}.getOrDefault(emptyList())

@Composable
private fun HomeScreen(
    drafts: List<SavedProject>,
    onNewProject: () -> Unit,
    onOpenDraft: (SavedProject) -> Unit,
    onDeleteDraft: (SavedProject) -> Unit,
    onQuickTool: (String) -> Unit
) {
    var settingsOpen by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxSize().background(Color(0xFF171717))
            .verticalScroll(rememberScrollState()).padding(horizontal = 18.dp)
    ) {
        Spacer(Modifier.height(24.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text("ESOFTMEDIA", color = PAPER, fontSize = 25.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.2.sp)
                Text("VIDEO STUDIO", color = Color(0xFFFF8A72), fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { onQuickTool("Export") }) { Icon(Icons.Default.WorkspacePremium, "Premium tools", tint = Color(0xFFFFB36E), modifier = Modifier.size(27.dp)) }
            IconButton(onClick = { settingsOpen = true }) { Icon(Icons.Default.Settings, "Settings", tint = Color.LightGray, modifier = Modifier.size(26.dp)) }
        }
        Spacer(Modifier.height(24.dp))
        Box(
            Modifier.fillMaxWidth().height(138.dp)
                .clip(RoundedCornerShape(17.dp))
                .background(Brush.horizontalGradient(listOf(Color(0xFFFFC46E), Color(0xFFFF7E65), Color(0xFFF53F68))))
                .clickable(onClick = onNewProject),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.AddCircle, null, tint = Color.White, modifier = Modifier.size(43.dp))
                Spacer(Modifier.height(7.dp))
                Text("New Project", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        Spacer(Modifier.height(18.dp))
        val tools = listOf(
            Triple("Enhance", Icons.Default.HighQuality, "Adjust"),
            Triple("Captions", Icons.Default.Subtitles, "Text"),
            Triple("Filters", Icons.Default.FilterVintage, "Filters"),
            Triple("Trim", Icons.Default.ContentCut, "Edit"),
            Triple("Music", Icons.Default.MusicNote, "Audio"),
            Triple("Crop", Icons.Default.Crop, "Crop")
        )
        tools.chunked(3).forEach { rowTools ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                rowTools.forEach { item ->
                    Column(
                        Modifier.weight(1f).height(82.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF282828))
                            .clickable { onQuickTool(item.third) }
                            .padding(9.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(item.second, null, tint = Color.White, modifier = Modifier.size(30.dp))
                        Spacer(Modifier.height(5.dp))
                        Text(item.first, color = Color.LightGray, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
            Spacer(Modifier.height(9.dp))
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Drafts", color = PAPER, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.width(5.dp))
            Icon(Icons.Default.Info, null, tint = Color.Gray, modifier = Modifier.size(15.dp))
            Spacer(Modifier.weight(1f))
            Icon(Icons.Default.ViewModule, null, tint = Color.LightGray)
        }
        Spacer(Modifier.height(14.dp))
        if (drafts.isEmpty()) {
            Column(
                Modifier.fillMaxWidth().padding(vertical = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(Icons.Default.VideoLibrary, null, tint = Color.Gray, modifier = Modifier.size(42.dp))
                Spacer(Modifier.height(8.dp))
                Text("Your projects will appear here", color = Color.LightGray, fontSize = 13.sp)
                Text("Create a new project to get started", color = Color.Gray, fontSize = 11.sp)
            }
        } else {
            drafts.chunked(3).forEach { rowDrafts ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    rowDrafts.forEach { draft ->
                        DraftCard(
                            draft = draft,
                            modifier = Modifier.weight(1f),
                            onOpen = { onOpenDraft(draft) },
                            onDelete = { onDeleteDraft(draft) }
                        )
                    }
                    repeat((3 - rowDrafts.size).coerceAtLeast(0)) { Spacer(Modifier.weight(1f)) }
                }
                Spacer(Modifier.height(10.dp))
            }
        }
        Spacer(Modifier.height(34.dp))
        Text("Projects stay on your phone", color = Color.Gray, fontSize = 12.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
        Spacer(Modifier.height(18.dp))
    }
    if (settingsOpen) {
        AlertDialog(
            onDismissRequest = { settingsOpen = false },
            title = { Text("Local editor settings") },
            text = { Text("ESOFTMEDIA stores your drafts on this device. Imported videos remain in their original locations; rendered videos are saved to Movies/ESOFTMEDIA. No account or cloud upload is required.") },
            confirmButton = { TextButton(onClick = { settingsOpen = false }) { Text("Close") } }
        )
    }
}

@Composable
private fun DraftCard(draft: SavedProject, modifier: Modifier, onOpen: () -> Unit, onDelete: () -> Unit) {
    var menuOpen by remember { mutableStateOf(false) }
    Box(
        modifier.height(150.dp).clip(RoundedCornerShape(9.dp))
            .background(Color(0xFF2A2A2A)).clickable(onClick = onOpen)
    ) {
        FrameThumbnail(draft.previewUri, Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .78f)))))
        Column(Modifier.align(Alignment.BottomStart).padding(start = 8.dp, bottom = 8.dp, end = 6.dp)) {
            Text(draft.name, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(time(draft.durationMs), color = Color.White, fontSize = 10.sp)
        }
        IconButton(onClick = { menuOpen = true }, modifier = Modifier.align(Alignment.BottomEnd).size(31.dp)) {
            Icon(Icons.Default.MoreHoriz, "Draft options", tint = Color.White, modifier = Modifier.size(21.dp))
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(text = { Text("Open project") }, onClick = { menuOpen = false; onOpen() })
            DropdownMenuItem(text = { Text("Delete draft") }, onClick = { menuOpen = false; onDelete() })
        }
    }
}

@Composable
private fun FrameThumbnail(uriText: String?, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val bitmap by produceState<Bitmap?>(initialValue = null, key1 = uriText) {
        value = withContext(Dispatchers.IO) {
            if (uriText.isNullOrBlank()) null else runCatching {
                val retriever = MediaMetadataRetriever()
                try {
                    retriever.setDataSource(context, Uri.parse(uriText))
                    if (Build.VERSION.SDK_INT >= 27) {
                        retriever.getScaledFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, 160, 90)
                    } else {
                        val frame = retriever.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                        if (frame != null && frame.width > 160) {
                            val scaled = Bitmap.createScaledBitmap(frame, 160, 90, true)
                            if (scaled !== frame) frame.recycle()
                            scaled
                        } else frame
                    }
                } finally {
                    retriever.release()
                }
            }.getOrNull()
        }
    }
    if (bitmap != null) {
        Image(bitmap!!.asImageBitmap(), contentDescription = "Video thumbnail", modifier = modifier, contentScale = ContentScale.Crop)
    } else {
        Box(modifier.background(Brush.linearGradient(listOf(Color(0xFF313846), Color(0xFF20242C)))), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Movie, null, tint = Color(0xFF9BA3B4), modifier = Modifier.size(26.dp))
        }
    }
}

@Composable
private fun EditorTopBar(title: String, onClose: () -> Unit, onHelp: () -> Unit, onExport: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(48.dp).background(Color(0xFF222222)).padding(horizontal = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onClose) { Icon(Icons.Default.Close, "Close editor", tint = PAPER, modifier = Modifier.size(27.dp)) }
        Text(title, color = PAPER, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        IconButton(onClick = onHelp) { Icon(Icons.Default.HelpOutline, "Help", tint = PAPER, modifier = Modifier.size(25.dp)) }
        IconButton(onClick = onExport) { Icon(Icons.Default.FileUpload, "Export video", tint = PAPER, modifier = Modifier.size(25.dp)) }
    }
}

@Composable
private fun EditorTools(active: String, onTool: (String) -> Unit) {
    val tools = listOf(
        "Filter" to Icons.Default.FilterVintage,
        "Enhance" to Icons.Default.HighQuality,
        "Captions" to Icons.Default.Subtitles,
        "Speed" to Icons.Default.Speed,
        "BG" to Icons.Default.Layers,
        "Crop" to Icons.Default.Crop,
        "Mosaic" to Icons.Default.BlurOn,
        "Trim" to Icons.Default.ContentCut,
        "Music" to Icons.Default.MusicNote,
        "Text" to Icons.Default.TextFields,
        "Sticker" to Icons.Default.EmojiEmotions,
        "Effect" to Icons.Default.AutoAwesome,
        "Volume" to Icons.Default.VolumeUp,
        "Rotate" to Icons.Default.RotateRight,
        "Flip" to Icons.Default.Flip
    )
    Row(
        Modifier.fillMaxWidth().height(74.dp).background(Color(0xFF222222))
            .horizontalScroll(rememberScrollState()).padding(horizontal = 5.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        tools.forEach { (name, icon) ->
            val selected = name == active
            Column(
                Modifier.width(58.dp).clip(RoundedCornerShape(8.dp))
                    .background(if (selected) Color(0xFF343434) else Color.Transparent)
                    .clickable { onTool(name) }.padding(vertical = 5.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(icon, null, tint = if (selected) Color.White else Color(0xFFCDCDCD), modifier = Modifier.size(25.dp))
                Spacer(Modifier.height(3.dp))
                Text(name.uppercase(), color = if (selected) Color.White else Color(0xFFBDBDBD), fontSize = 8.sp, maxLines = 1)
            }
        }
    }
}

@Composable
private fun EditToolPanel(
    clip: VideoClip?,
    start: Float,
    end: Float,
    setStart: (Float) -> Unit,
    setEnd: (Float) -> Unit,
    trim: () -> Unit,
    split: () -> Unit,
    duplicate: () -> Unit,
    delete: () -> Unit
) {
    Column(
        Modifier.fillMaxWidth().heightIn(max = 145.dp)
            .background(Color(0xFF292929)).padding(horizontal = 11.dp, vertical = 6.dp)
    ) {
        if (clip == null) {
            Text("Import a clip to start trimming", color = Color.LightGray, fontSize = 11.sp)
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("TRIM", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Text(time(start.toLong()) + " — " + time(end.toLong()), color = Color.LightGray, fontSize = 10.sp)
            }
            RangeSlider(
                value = start.coerceIn(0f, clip.duration.toFloat())..end.coerceIn(0f, clip.duration.toFloat()),
                onValueChange = { setStart(it.start); setEnd(it.endInclusive) },
                onValueChangeFinished = trim,
                valueRange = 0f..clip.duration.toFloat().coerceAtLeast(1f)
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                Mini("Split", Icons.Default.ContentCut, split)
                Mini("Duplicate", Icons.Default.ContentCopy, duplicate)
                Mini("Delete", Icons.Default.Delete, delete)
            }
        }
    }
}

private fun encodeStyles(styles: Map<String, ClipStyle>): String {
    val root = JSONObject()
    styles.forEach { (id, style) ->
        root.put(id, JSONObject().apply {
            put("filter", style.filter)
            put("brightness", style.brightness.toDouble())
            put("contrast", style.contrast.toDouble())
            put("saturation", style.saturation.toDouble())
            put("lightness", style.lightness.toDouble())
            put("aspect", style.aspect)
            put("rotation", style.rotation.toDouble())
            put("flipH", style.flipH)
            put("flipV", style.flipV)
            put("speed", style.speed.toDouble())
            put("background", style.background)
            put("mosaic", style.mosaic.toDouble())
        })
    }
    return root.toString()
}

private fun decodeStyles(raw: String): Map<String, ClipStyle> = runCatching {
    if (raw.isBlank()) emptyMap() else {
        val root = JSONObject(raw)
        buildMap {
            val keys = root.keys()
            while (keys.hasNext()) {
                val id = keys.next()
                val item = root.optJSONObject(id) ?: continue
                put(id, ClipStyle(
                    filter = item.optString("filter", "Original"),
                    brightness = item.optDouble("brightness", 0.0).toFloat().coerceIn(-100f, 100f),
                    contrast = item.optDouble("contrast", 0.0).toFloat().coerceIn(-100f, 100f),
                    saturation = item.optDouble("saturation", 0.0).toFloat().coerceIn(-100f, 100f),
                    lightness = item.optDouble("lightness", 0.0).toFloat().coerceIn(-100f, 100f),
                    aspect = item.optString("aspect", "Original"),
                    rotation = item.optDouble("rotation", 0.0).toFloat(),
                    flipH = item.optBoolean("flipH", false),
                    flipV = item.optBoolean("flipV", false),
                    speed = item.optDouble("speed", 1.0).toFloat().coerceIn(0.25f, 4f),
                    background = item.optString("background", "Black"),
                    mosaic = item.optDouble("mosaic", 0.0).toFloat().coerceIn(0f, 100f)
                ))
            }
        }
    }
}.getOrDefault(emptyMap())

private fun durationMs(ctx: Context, uri: Uri): Long {
    val r = MediaMetadataRetriever()
    return try { r.setDataSource(ctx, uri); r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L }
    catch (_: Exception) { 0L } finally { runCatching { r.release() } }
}
private fun pushUndo(clips: SnapshotStateList<VideoClip>, undo: SnapshotStateList<List<VideoClip>>, redo: SnapshotStateList<List<VideoClip>>) {
    undo.add(clips.toList()); if (undo.size > 35) undo.removeAt(0); redo.clear()
}
private fun changeClip(clips: SnapshotStateList<VideoClip>, i: Int, c: VideoClip, undo: SnapshotStateList<List<VideoClip>>, redo: SnapshotStateList<List<VideoClip>>) {
    if (i in clips.indices) { pushUndo(clips, undo, redo); clips[i] = c }
}
private fun encode(clips: List<VideoClip>): String {
    val a = JSONArray()
    clips.forEach { c -> a.put(JSONObject().apply { put("id", c.id); put("uri", c.uri.toString()); put("duration", c.duration); put("start", c.start); put("end", c.end) }) }
    return a.toString()
}
private fun decode(raw: String?): List<VideoClip> = runCatching {
    if (raw.isNullOrBlank()) emptyList() else {
        val a = JSONArray(raw)
        (0 until a.length()).mapNotNull { n ->
            val x = a.getJSONObject(n); val d = x.optLong("duration"); val s = x.optLong("start"); val e = x.optLong("end", d)
            if (d <= 0L || s < 0L || e <= s || e > d) null else VideoClip(x.optString("id", UUID.randomUUID().toString()), Uri.parse(x.getString("uri")), d, s, e)
        }
    }
}.getOrDefault(emptyList())
private fun previewBackground(name: String): Color = when (name) {
    "White" -> Color(0xFFF2F2F2)
    "Charcoal" -> Color(0xFF282828)
    "Purple" -> Color(0xFF241B35)
    "Teal" -> Color(0xFF123B3A)
    else -> Color.Black
}
private fun resolutionHeight(value: String): Int = when (value) { "480p" -> 480; "720p" -> 720; "1440p" -> 1440; "2160p" -> 2160; else -> 1080 }

private fun effects(
    filter: String,
    b: Float,
    c: Float,
    s: Float,
    l: Float,
    aspect: String,
    rotation: Float,
    fh: Boolean,
    fv: Boolean,
    caption: String,
    resolutionHeight: Int,
    mosaic: Float = 0f,
    speed: Float = 1f,
    includeSpeed: Boolean = false
): MutableList<Effect> {
    val fx = mutableListOf<Effect>()
    when (filter) {
        "Cinema" -> fx.add(HslAdjustment.Builder().adjustSaturation(-8f).adjustLightness(-3f).build())
        "Vintage" -> fx.add(HslAdjustment.Builder().adjustHue(12f).adjustSaturation(-25f).adjustLightness(5f).build())
        "B&W" -> fx.add(HslAdjustment.Builder().adjustSaturation(-100f).build())
        "Warm" -> fx.add(HslAdjustment.Builder().adjustHue(-8f).adjustSaturation(8f).build())
        "Cool" -> fx.add(HslAdjustment.Builder().adjustHue(8f).adjustSaturation(-5f).build())
        "Fade" -> fx.add(HslAdjustment.Builder().adjustLightness(12f).adjustSaturation(-18f).build())
        "Vivid" -> fx.add(HslAdjustment.Builder().adjustSaturation(28f).build())
    }
    if (b != 0f) fx.add(Brightness(b / 100f))
    if (c != 0f) fx.add(Contrast(c / 100f))
    if (s != 0f || l != 0f) fx.add(HslAdjustment.Builder().adjustSaturation(s).adjustLightness(l).build())
    // This is a full-frame privacy blur; region-selective pixel mosaic can be layered on later.
    if (mosaic > 0f) fx.add(GaussianBlur((mosaic / 100f * 7.5f).coerceAtLeast(0.1f)))
    if (includeSpeed && speed != 1f) fx.add(SpeedChangeEffect(speed))
    val ratio = when (aspect) { "1:1" -> 1f; "4:5" -> .8f; "16:9" -> 16f / 9f; "9:16" -> 9f / 16f; "3:2" -> 1.5f; else -> 0f }
    if (ratio > 0f) fx.add(Presentation.createForAspectRatio(ratio, Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP))
    if (resolutionHeight > 0) fx.add(Presentation.createForHeight(resolutionHeight))
    if (rotation != 0f || fh || fv) fx.add(ScaleAndRotateTransformation.Builder().setRotationDegrees(rotation).setScale(if (fh) -1f else 1f, if (fv) -1f else 1f).build())
    if (caption.isNotBlank()) {
        val bmp = Bitmap.createBitmap(1280, 720, Bitmap.Config.ARGB_8888)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE; textSize = 58f; typeface = Typeface.create("sans-serif", Typeface.BOLD)
            textAlign = Paint.Align.CENTER; setShadowLayer(6f, 2f, 3f, android.graphics.Color.BLACK)
        }
        Canvas(bmp).drawText(caption.take(80), 640f, 650f, paint)
        fx.add(OverlayEffect(listOf(BitmapOverlay.createStaticBitmapOverlay(bmp))))
    }
    return fx
}

@Composable private fun Header(save: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(47.dp).background(CARD, CircleShape).border(1.dp, Color.DarkGray, CircleShape), contentAlignment = Alignment.Center) {
            Box(Modifier.size(31.dp).background(Brush.linearGradient(listOf(AQUA, VIOLET)), RoundedCornerShape(9.dp)), contentAlignment = Alignment.Center) { Text("E", color = BG, fontSize = 23.sp, fontWeight = FontWeight.Black) }
        }
        Spacer(Modifier.width(10.dp))
        Column { Text("ESOFTMEDIA", color = PAPER, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.3.sp); Text("VIDEO EDITOR", color = VIOLET, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp) }
        Spacer(Modifier.weight(1f))
        IconButton(onClick = save) { Icon(Icons.Default.Save, "Save project", tint = PAPER) }
    }
}
private fun time(ms: Long): String {
    val seconds = ms.coerceAtLeast(0L) / 1000L
    return "%02d:%02d".format(seconds / 60L, seconds % 60L)
}

@Composable
private fun Preview(
    player: ExoPlayer,
    hasVideo: Boolean,
    caption: String,
    modifier: Modifier = Modifier,
    canvasBackground: Color = Color.Black,
    onImport: () -> Unit
) {
    Box(modifier.fillMaxWidth().background(canvasBackground)) {
        if (hasVideo) {
            AndroidView(
                factory = { context ->
                    PlayerView(context).apply {
                        this.player = player
                        useController = false
                        resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                        setBackgroundColor(canvasBackground.toArgb())
                        setShutterBackgroundColor(canvasBackground.toArgb())
                    }
                },
                update = {
                    it.player = player
                    it.setBackgroundColor(canvasBackground.toArgb())
                    it.setShutterBackgroundColor(canvasBackground.toArgb())
                },
                modifier = Modifier.fillMaxSize()
            )
            if (caption.isNotBlank()) {
                Text(
                    caption, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp)
                        .background(Color.Black.copy(alpha = .45f), RoundedCornerShape(5.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        } else {
            Column(
                Modifier.fillMaxSize().clickable(onClick = onImport),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(Icons.Default.PlayCircleFilled, null, tint = AQUA, modifier = Modifier.size(56.dp))
                Spacer(Modifier.height(8.dp))
                Text("Tap to import videos", color = PAPER, fontSize = 16.sp)
            }
        }
        Text("PREVIEW", color = Color(0xFFBDBDBD), fontSize = 10.sp, modifier = Modifier.align(Alignment.TopStart).padding(10.dp))
        IconButton(onClick = onImport, modifier = Modifier.align(Alignment.TopEnd)) {
            Icon(Icons.Default.Add, "Add video", tint = Color.White, modifier = Modifier.size(27.dp))
        }
    }
}

@Composable
private fun PlaybackBar(
    playing: Boolean,
    position: Long,
    duration: Long,
    toggle: () -> Unit,
    stop: () -> Unit,
    previous: () -> Unit,
    next: () -> Unit,
    seek: (Float) -> Unit,
    canUndo: Boolean,
    canRedo: Boolean,
    undo: () -> Unit,
    redo: () -> Unit
) {
    Column(
        Modifier.fillMaxWidth().background(Color(0xFF242424)).padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().height(43.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            IconButton(onClick = previous, modifier = Modifier.size(38.dp)) { Icon(Icons.Default.SkipPrevious, "Previous clip", tint = Color.White, modifier = Modifier.size(24.dp)) }
            IconButton(onClick = stop, modifier = Modifier.size(38.dp)) { Icon(Icons.Default.Stop, "Stop", tint = Color.White, modifier = Modifier.size(23.dp)) }
            IconButton(onClick = toggle, modifier = Modifier.size(48.dp).background(VIOLET, CircleShape)) {
                Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, "Play or pause", tint = Color.White, modifier = Modifier.size(29.dp))
            }
            Text(time(position) + " / " + time(duration), color = Color.LightGray, fontSize = 11.sp)
            IconButton(onClick = undo, enabled = canUndo, modifier = Modifier.size(36.dp)) { Icon(Icons.Default.Undo, "Undo", tint = if (canUndo) Color.White else Color.DarkGray) }
            IconButton(onClick = redo, enabled = canRedo, modifier = Modifier.size(36.dp)) { Icon(Icons.Default.Redo, "Redo", tint = if (canRedo) Color.White else Color.DarkGray) }
            IconButton(onClick = next, modifier = Modifier.size(38.dp)) { Icon(Icons.Default.SkipNext, "Next clip", tint = Color.White, modifier = Modifier.size(24.dp)) }
        }
        if (duration > 0L) {
            Slider(
                value = position.toFloat().coerceIn(0f, duration.toFloat()),
                onValueChange = seek,
                valueRange = 0f..duration.toFloat(),
                modifier = Modifier.fillMaxWidth().height(22.dp),
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = Color(0xFFDE3D65),
                    inactiveTrackColor = Color(0xFF565656)
                )
            )
        }
    }
}

@Composable
private fun TimelinePanel(
    clips: List<VideoClip>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    onAddClip: () -> Unit,
    onAddAudio: () -> Unit,
    hasAudio: Boolean,
    audioLabel: String,
    muted: Boolean,
    onToggleMute: () -> Unit,
    onRemoveAudio: () -> Unit
) {
    Column(
        Modifier.fillMaxWidth().height(157.dp).background(Color(0xFF202020))
            .padding(horizontal = 8.dp, vertical = 5.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("0:00", color = Color.LightGray, fontSize = 9.sp)
            Spacer(Modifier.width(35.dp))
            Text("0:02", color = Color.Gray, fontSize = 9.sp)
            Spacer(Modifier.width(35.dp))
            Text("0:04", color = Color.Gray, fontSize = 9.sp)
            Spacer(Modifier.width(35.dp))
            Text("0:06", color = Color.Gray, fontSize = 9.sp)
            Spacer(Modifier.weight(1f))
            Text(clips.size.toString() + " clip(s)", color = Color.Gray, fontSize = 9.sp)
        }
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(
                Modifier.width(62.dp).height(65.dp).clickable(onClick = onToggleMute),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(if (muted) Icons.Default.VolumeOff else Icons.Default.VolumeUp, null, tint = if (muted) Color(0xFFFF7890) else Color.LightGray, modifier = Modifier.size(23.dp))
                Text(if (muted) "Unmute clip" else "Clip audio", color = Color.LightGray, fontSize = 8.sp)
            }
            Box(Modifier.weight(1f).height(65.dp)) {
                Row(
                    Modifier.fillMaxSize().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    clips.forEachIndexed { index, clip ->
                        Box(
                            Modifier.width((clip.length / 1000f * 22f).coerceIn(82f, 190f).dp)
                                .height(60.dp).clip(RoundedCornerShape(5.dp))
                                .border(if (index == selectedIndex) 2.dp else 1.dp, if (index == selectedIndex) Color(0xFFFF4569) else Color(0xFF666666), RoundedCornerShape(5.dp))
                                .clickable { onSelect(index) }
                        ) {
                            FrameThumbnail(clip.uri.toString(), Modifier.fillMaxSize())
                            Box(Modifier.fillMaxSize().background(if (index == selectedIndex) Color.Transparent else Color.Black.copy(alpha = .12f)))
                        }
                    }
                    Box(
                        Modifier.width(48.dp).height(60.dp).clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF343434)).clickable(onClick = onAddClip),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Add, "Add clip", tint = Color(0xFFFF4569), modifier = Modifier.size(30.dp))
                    }
                }
                Box(
                    Modifier.align(Alignment.Center).fillMaxHeight().width(2.dp)
                        .background(Color(0xFFFF315F))
                )
            }
        }
        Spacer(Modifier.height(5.dp))
        Row(
            Modifier.fillMaxWidth().height(37.dp).clip(RoundedCornerShape(5.dp))
                .background(Color(0xFF303030)).clickable(onClick = if (hasAudio) onRemoveAudio else onAddAudio)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.MusicNote, null, tint = Color.LightGray, modifier = Modifier.size(17.dp))
            Spacer(Modifier.width(7.dp))
            Text(audioLabel, color = if (hasAudio) Color.White else Color.LightGray, fontSize = 11.sp)
            Spacer(Modifier.weight(1f))
            if (!hasAudio) Icon(Icons.Default.Add, "Add audio", tint = Color.White, modifier = Modifier.size(18.dp))
            else Text("REMOVE", color = Color(0xFFFF879B), fontSize = 8.sp)
        }
    }
}

@Composable private fun FilterPanel(selected: String, choose: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().background(CARD, RoundedCornerShape(15.dp)).padding(12.dp)) { Text("FILTERS & EFFECTS", color = AQUA, fontWeight = FontWeight.Bold); Chips(listOf("Original", "Cinema", "Vintage", "B&W", "Warm", "Cool", "Fade", "Vivid"), selected, choose); Text("Live preview and rendered export.", color = Color.LightGray, fontSize = 10.sp) }
}
@Composable
private fun AdjustPanel(
    b: Float, c: Float, s: Float, l: Float,
    onBrightness: (Float) -> Unit, onContrast: (Float) -> Unit,
    onSaturation: (Float) -> Unit, onLightness: (Float) -> Unit,
    onCommit: () -> Unit
) {
    Column(
        Modifier.fillMaxWidth().background(Color(0xFF171A23), RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text("ADJUST", color = AQUA, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Adjust("Brightness", b, onBrightness, onCommit)
        Adjust("Contrast", c, onContrast, onCommit)
        Adjust("Saturation", s, onSaturation, onCommit)
        Adjust("Lightness", l, onLightness, onCommit)
        Text("Preview updates when you release a slider to keep playback smooth.", color = Color.LightGray, fontSize = 9.sp)
    }
}
@Composable
private fun Adjust(
    label: String,
    value: Float,
    onChange: (Float) -> Unit,
    onFinished: () -> Unit = {}
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = PAPER, fontSize = 11.sp, modifier = Modifier.width(82.dp))
        Slider(
            value = value.coerceIn(-100f, 100f),
            onValueChange = onChange,
            onValueChangeFinished = onFinished,
            valueRange = -100f..100f,
            modifier = Modifier.weight(1f).height(32.dp)
        )
        Text(value.toInt().toString(), color = AQUA, fontSize = 10.sp, modifier = Modifier.width(28.dp))
    }
}
@Composable private fun CropPanel(aspect: String, flipH: Boolean, flipV: Boolean, oa: (String) -> Unit, rotate: () -> Unit, fh: () -> Unit, fv: () -> Unit, reset: () -> Unit) {
    Column(Modifier.fillMaxWidth().background(CARD, RoundedCornerShape(15.dp)).padding(12.dp)) {
        Text("CROP & TRANSFORM", color = AQUA, fontWeight = FontWeight.Bold); Chips(listOf("Original", "1:1", "4:5", "16:9", "9:16", "3:2"), aspect, oa)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) { Mini("Rotate", Icons.Default.RotateRight, rotate); Mini(if (flipH) "Flip H ✓" else "Flip H", Icons.Default.Flip, fh); Mini(if (flipV) "Flip V ✓" else "Flip V", Icons.Default.Flip, fv); Mini("Reset", Icons.Default.RestartAlt, reset) }
    }
}
@Composable
private fun SpeedPanel(speed: Float, onSpeedChange: (Float) -> Unit, onCommit: () -> Unit) {
    Column(Modifier.fillMaxWidth().background(Color(0xFF171A23), RoundedCornerShape(16.dp)).padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("SPEED", color = AQUA, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Spacer(Modifier.weight(1f))
            Text("%.2fx".format(speed), color = Color.White, fontWeight = FontWeight.Bold)
        }
        Slider(
            value = speed.coerceIn(0.25f, 4f),
            onValueChange = onSpeedChange,
            onValueChangeFinished = onCommit,
            valueRange = 0.25f..4f
        )
        Chips(listOf("0.25x", "0.5x", "0.75x", "1x", "1.5x", "2x", "3x", "4x"), "%.2gx".format(speed)) {
            onSpeedChange(it.removeSuffix("x").toFloat())
            onCommit()
        }
        Text("Playback speed is stored on this clip. Export renders the selected speed; original audio timing may need a separate audio-stretch pass.", color = Color.LightGray, fontSize = 9.sp)
    }
}

@Composable
private fun BackgroundPanel(selected: String, onSelect: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().background(Color(0xFF171A23), RoundedCornerShape(16.dp)).padding(12.dp)) {
        Text("BACKGROUND", color = AQUA, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        Text("Choose the preview canvas colour around the fitted video.", color = Color.LightGray, fontSize = 10.sp)
        Chips(listOf("Black", "Charcoal", "White", "Purple", "Teal"), selected, onSelect)
    }
}

@Composable
private fun MosaicPanel(value: Float, onChange: (Float) -> Unit, onCommit: () -> Unit) {
    Column(Modifier.fillMaxWidth().background(Color(0xFF171A23), RoundedCornerShape(16.dp)).padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("MOSAIC / BLUR", color = AQUA, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Spacer(Modifier.weight(1f))
            Text(if (value <= 0f) "Off" else value.toInt().toString() + "%", color = Color.White)
        }
        Slider(value.coerceIn(0f, 100f), onChange, onValueChangeFinished = onCommit, valueRange = 0f..100f)
        Chips(listOf("Off", "Low", "Medium", "Strong"), when {
            value <= 0f -> "Off"
            value < 30f -> "Low"
            value < 65f -> "Medium"
            else -> "Strong"
        }) {
            onChange(when (it) { "Off" -> 0f; "Low" -> 20f; "Medium" -> 50f; else -> 85f })
            onCommit()
        }
        Text("Applies a full-frame blur to the clip. Selecting and masking only one face or object is a later enhancement.", color = Color.LightGray, fontSize = 9.sp)
    }
}

@Composable private fun AudioPanel(has: Boolean, volume: Float, muted: Boolean, add: () -> Unit, setVolume: (Float) -> Unit, setMuted: (Boolean) -> Unit, remove: () -> Unit) {
    Column(Modifier.fillMaxWidth().background(CARD, RoundedCornerShape(15.dp)).padding(12.dp)) {
        Text("AUDIO MIXER", color = AQUA, fontWeight = FontWeight.Bold)
        Row(verticalAlignment = Alignment.CenterVertically) { Text(if (has) "Music track selected" else "No music track", color = PAPER, fontSize = 11.sp, modifier = Modifier.weight(1f)); Action(if (has) "Replace" else "Add music", add) }
        if (has) { Adjust("Music volume", volume * 100f) { setVolume(it / 100f) }; Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(muted, setMuted); Text("Mute original audio", color = PAPER, fontSize = 11.sp); Spacer(Modifier.weight(1f)); TextButton(remove) { Text("Remove", color = Color(0xFFFF7777)) } }; Text("Background audio is added to the video export.", color = Color.LightGray, fontSize = 10.sp) }
    }
}
@Composable private fun TextPanel(caption: String, add: () -> Unit, remove: () -> Unit) {
    Column(Modifier.fillMaxWidth().background(CARD, RoundedCornerShape(15.dp)).padding(12.dp)) {
        Text("TEXT & TITLES", color = AQUA, fontWeight = FontWeight.Bold); Text(if (caption.isBlank()) "No text overlay yet." else caption, color = PAPER, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Row { Action(if (caption.isBlank()) "Add text" else "Edit text", add); if (caption.isNotBlank()) TextButton(remove) { Text("Remove", color = Color(0xFFFF7777)) } }
        Text("Text appears in preview and is burned into the rendered video.", color = Color.LightGray, fontSize = 10.sp)
    }
}
@Composable private fun ElementsPanel(add: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().background(CARD, RoundedCornerShape(15.dp)).padding(12.dp)) { Text("STICKERS & ELEMENTS", color = AQUA, fontWeight = FontWeight.Bold); Chips(listOf("⭐", "❤️", "🔥", "✨", "🎉", "🎵", "👏", "✅"), "", add) }
}
@Composable private fun ExportPanel(res: String, fps: Int, status: String, busy: Boolean, setRes: (String) -> Unit, setFps: (Int) -> Unit, export: () -> Unit) {
    Column(Modifier.fillMaxWidth().background(CARD, RoundedCornerShape(15.dp)).padding(12.dp)) {
        Text("EXPORT VIDEO", color = AQUA, fontWeight = FontWeight.Bold); Text("Resolution", color = PAPER, fontSize = 12.sp)
        Chips(listOf("480p", "720p", "1080p", "1440p", "2160p"), res, setRes)
        Text("Frame rate", color = PAPER, fontSize = 12.sp); Chips(listOf("24 FPS", "30 FPS", "60 FPS"), fps.toString() + " FPS") { setFps(it.substringBefore(" ").toInt()) }
        Text("MP4 • Movies/ESOFTMEDIA", color = Color.LightGray, fontSize = 10.sp)
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth(), color = AQUA)
        Text(status, color = AQUA, fontSize = 10.sp); Action(if (busy) "Rendering…" else "Export video", export, !busy)
    }
}
@Composable private fun Chips(options: List<String>, active: String, click: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEach { Surface(color = if (it == active) VIOLET else Color(0xFF252833), shape = RoundedCornerShape(9.dp), modifier = Modifier.clickable { click(it) }) { Text(it, color = PAPER, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp)) } }
    }
}
@Composable private fun Action(label: String, click: () -> Unit, enabled: Boolean = true) {
    Button(click, enabled = enabled, colors = ButtonDefaults.buttonColors(containerColor = VIOLET), contentPadding = PaddingValues(horizontal = 13.dp, vertical = 7.dp)) { Text(label, fontSize = 11.sp) }
}
@Composable private fun Mini(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, click: () -> Unit, enabled: Boolean = true) {
    TextButton(onClick = click, enabled = enabled, contentPadding = PaddingValues(horizontal = 3.dp, vertical = 1.dp)) { Column(horizontalAlignment = Alignment.CenterHorizontally) { Icon(icon, null, tint = if (enabled) AQUA else Color.DarkGray, modifier = Modifier.size(17.dp)); Text(label, color = if (enabled) PAPER else Color.DarkGray, fontSize = 9.sp) } }
}
@Composable private fun BottomNav(active: String, choose: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp).background(Color(0xFF12141C), RoundedCornerShape(18.dp)).padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
        listOf("Edit" to Icons.Default.ContentCut, "Audio" to Icons.Default.MusicNote, "Text" to Icons.Default.TextFields, "Effects" to Icons.Default.Tune, "Export" to Icons.Default.Save).forEach { (name, icon) ->
            Column(Modifier.clickable { choose(name) }.padding(horizontal = 5.dp), horizontalAlignment = Alignment.CenterHorizontally) { Icon(icon, name, tint = if (active == name) AQUA else Color.Gray); Text(name, color = if (active == name) AQUA else Color.Gray, fontSize = 10.sp) }
        }
    }
}
