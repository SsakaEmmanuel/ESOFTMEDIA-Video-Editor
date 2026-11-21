package com.esoftmedia.videoeditor

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

private val Deep = Color(0xFF090A0F)
private val Panel = Color(0xFF151720)
private val Cyan = Color(0xFF00E5D4)
private val Purple = Color(0xFF7B2CFF)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            EditorApp()
        }
    }
}

@Composable
fun EditorApp() {
    val context = LocalContext.current
    var selectedVideo by remember { mutableStateOf<Uri?>(null) }

    val pickVideo = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        selectedVideo = uri
    }

    val player = remember(selectedVideo) {
        selectedVideo?.let { uri ->
            ExoPlayer.Builder(context).build().apply {
                setMediaItem(MediaItem.fromUri(uri))
                prepare()
                playWhenReady = false
            }
        }
    }

    DisposableEffect(player) {
        onDispose {
            player?.release()
        }
    }

    val importVideo = {
        pickVideo.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
        )
    }

    MaterialTheme(
        colorScheme = darkColorScheme(
            background = Deep,
            surface = Panel,
            primary = Cyan,
            secondary = Purple
        )
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Deep
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 18.dp)
            ) {
                Spacer(Modifier.height(18.dp))
                Header()
                Spacer(Modifier.height(24.dp))

                Text(
                    "Your creative studio",
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Create. Edit. Anywhere.",
                    color = Color(0xFFB7B7C6),
                    fontSize = 14.sp
                )

                Spacer(Modifier.height(22.dp))
                PreviewCard(
                    videoUri = selectedVideo,
                    player = player,
                    onImport = importVideo
                )

                Spacer(Modifier.height(18.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    QuickTool("Trim", Icons.Default.ContentCut, Modifier.weight(1f))
                    QuickTool("Music", Icons.Default.MusicNote, Modifier.weight(1f))
                    QuickTool("Text", Icons.Default.TextFields, Modifier.weight(1f))
                    QuickTool("Effects", Icons.Default.Settings, Modifier.weight(1f))
                }

                Spacer(Modifier.height(22.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Timeline",
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 18.sp
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        if (selectedVideo == null) "00:00 / 00:00" else "VIDEO READY",
                        color = Color(0xFF8E91A0),
                        fontSize = 12.sp
                    )
                }

                Spacer(Modifier.height(12.dp))
                TimelinePlaceholder(selectedVideo != null)
                Spacer(Modifier.height(22.dp))

                Text(
                    "Start creating",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(12.dp))

                Button(
                    onClick = importVideo,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Purple),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Import video", fontWeight = FontWeight.Bold)
                }

                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "Projects stay on your phone",
                        color = Color(0xFF888A98),
                        fontSize = 12.sp
                    )
                    Text(
                        "OFFLINE",
                        color = Cyan,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(Modifier.weight(1f))
                BottomBar()
                Spacer(Modifier.height(14.dp))
            }
        }
    }
}

@Composable
private fun Header() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .background(Panel, CircleShape)
                .border(1.dp, Color(0xFF292B37), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            BrandMark()
        }

        Spacer(Modifier.width(12.dp))
        Column {
            Text(
                "ESOFTMEDIA",
                color = Color.White,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.4.sp
            )
            Text(
                "VIDEO EDITOR",
                color = Purple,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp
            )
        }

        Spacer(Modifier.weight(1f))
        IconButton(onClick = {}) {
            Icon(
                Icons.Default.Save,
                contentDescription = "Save project",
                tint = Color.White
            )
        }
    }
}

@Composable
private fun BrandMark() {
    Box(
        modifier = Modifier
            .size(31.dp)
            .background(
                Brush.linearGradient(listOf(Cyan, Purple)),
                RoundedCornerShape(10.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            "E",
            color = Deep,
            fontSize = 22.sp,
            fontWeight = FontWeight.Black
        )
    }
}

@Composable
private fun PreviewCard(
    videoUri: Uri?,
    player: ExoPlayer?,
    onImport: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp)
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFF181B28), Color(0xFF101116))
                ),
                RoundedCornerShape(24.dp)
            )
            .border(1.dp, Color(0xFF252735), RoundedCornerShape(24.dp)),
        contentAlignment = Alignment.Center
    ) {
        if (videoUri != null && player != null) {
            AndroidView(
                factory = { viewContext ->
                    PlayerView(viewContext).apply {
                        this.player = player
                        useController = true
                    }
                },
                update = { view ->
                    view.player = player
                },
                modifier = Modifier.fillMaxSize()
            )

            IconButton(
                onClick = onImport,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .background(Color(0x99090A0F), CircleShape)
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = "Replace video",
                    tint = Color.White
                )
            }
        } else {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(onClick = onImport)
                    .padding(16.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    Modifier
                        .size(66.dp)
                        .background(
                            Brush.linearGradient(listOf(Cyan, Purple)),
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(38.dp)
                    )
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    "Your preview appears here",
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "Tap to import from your gallery",
                    color = Color(0xFF9294A3),
                    fontSize = 12.sp
                )
            }
        }

        Text(
            "PREVIEW",
            color = Color(0xFFB9BAC7),
            fontSize = 10.sp,
            letterSpacing = 2.sp,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp)
        )
    }
}

@Composable
private fun QuickTool(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier
) {
    Column(
        modifier = modifier
            .background(Panel, RoundedCornerShape(16.dp))
            .clickable { }
            .padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = Cyan,
            modifier = Modifier.size(22.dp)
        )
        Spacer(Modifier.height(6.dp))
        Text(label, color = Color.White, fontSize = 11.sp)
    }
}

@Composable
private fun TimelinePlaceholder(hasVideo: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(84.dp)
            .background(Panel, RoundedCornerShape(16.dp))
            .border(1.dp, Color(0xFF292B37), RoundedCornerShape(16.dp))
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (hasVideo) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                Color(0xFF183A3B),
                                Color(0xFF3B205C),
                                Color(0xFF183A3B)
                            )
                        ),
                        RoundedCornerShape(10.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "VIDEO CLIP",
                    color = Cyan,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            }
        } else {
            Icon(
                Icons.Default.Movie,
                contentDescription = null,
                tint = Color(0xFF696D7D),
                modifier = Modifier.size(25.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "Import media to begin your timeline",
                color = Color(0xFF777A89),
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun BottomBar() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF12141C), RoundedCornerShape(18.dp))
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        BottomItem("Edit", Icons.Default.ContentCut, true)
        BottomItem("Audio", Icons.Default.MusicNote, false)
        BottomItem("Text", Icons.Default.TextFields, false)
        BottomItem("Export", Icons.Default.Save, false)
    }
}

@Composable
private fun BottomItem(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    active: Boolean
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            icon,
            contentDescription = label,
            tint = if (active) Cyan else Color(0xFF858898),
            modifier = Modifier.size(20.dp)
        )
        Text(
            label,
            color = if (active) Cyan else Color(0xFF858898),
            fontSize = 10.sp
        )
    }
}
