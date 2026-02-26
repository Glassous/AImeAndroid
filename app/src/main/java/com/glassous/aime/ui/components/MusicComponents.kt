package com.glassous.aime.ui.components

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.glassous.aime.ui.viewmodel.MusicPlayerViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.regex.Pattern
import androidx.compose.foundation.interaction.MutableInteractionSource

data class MusicInfo(
    val name: String,
    val artist: String,
    val album: String,
    val url: String,
    val pic: String,
    val lrc: String? = null
)

@Composable
fun MusicList(
    musicList: List<MusicInfo>,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = context.findActivity()
    // Obtain the activity-scoped ViewModel
    val viewModel: MusicPlayerViewModel = if (activity != null) {
        viewModel(activity)
    } else {
        viewModel()
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        musicList.forEachIndexed { index, music ->
            MusicItem(
                music = music,
                onClick = { 
                    viewModel.playMusic(music, musicList)
                }
            )
            if (index < musicList.size - 1) {
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                    thickness = 0.5.dp,
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
            }
        }
    }
}

private fun Context.findActivity(): ComponentActivity? = when (this) {
    is ComponentActivity -> this
    is android.content.ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
fun GlobalMusicPlayer(
    viewModel: MusicPlayerViewModel
) {
    val showPlayer by viewModel.showPlayer.collectAsState()
    val showMiniPlayer by viewModel.showMiniPlayer.collectAsState()
    val currentMusic by viewModel.currentMusic.collectAsState()
    val progress by viewModel.progress.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()

    // Mini Player
    // 使用 Box 作为容器以便定位到右上角
    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = showMiniPlayer && currentMusic != null,
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut(),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding() // 避开状态栏
                .padding(top = 10.dp, end = 16.dp) // 在 TopBar 内部居中 (64-44)/2 = 10dp
        ) {
            currentMusic?.let { music ->
                MiniPlayer(
                    music = music,
                    progress = progress,
                    isPlaying = isPlaying,
                    onClick = { viewModel.expand() },
                    onPrev = { viewModel.playPrev() },
                    onNext = { viewModel.playNext() },
                    onTogglePlay = { viewModel.togglePlayPause() }
                )
            }
        }
    }

    // Full Player Dialog
    if (showPlayer && currentMusic != null) {
        MusicPlayerDialog(
            viewModel = viewModel,
            music = currentMusic!!
        )
    }
}


@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MiniPlayer(
    music: MusicInfo,
    progress: Float,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onTogglePlay: () -> Unit
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    var showControls by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .size(44.dp) // 适应 TopBar 高度
            .combinedClickable(
                onClick = onClick,
                onLongClick = { showControls = true },
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ),
        contentAlignment = Alignment.Center
    ) {
        // 进度条背景
        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize().padding(2.dp)) {
            val strokeWidth = 2.dp.toPx()
            val cornerRadius = 10.dp.toPx()
            
            val path = Path().apply {
                addRoundRect(
                    androidx.compose.ui.geometry.RoundRect(
                        rect = androidx.compose.ui.geometry.Rect(offset = androidx.compose.ui.geometry.Offset.Zero, size = size),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(cornerRadius, cornerRadius)
                    )
                )
            }
            
            // Draw Track
            drawPath(
                path = path,
                color = trackColor,
                style = Stroke(width = strokeWidth)
            )
            
            // Draw Progress
            if (progress > 0) {
                val pathMeasure = PathMeasure()
                pathMeasure.setPath(path, false)
                val length = pathMeasure.length
                val segmentPath = Path()
                
                pathMeasure.getSegment(0f, length * progress, segmentPath, true)
                
                // Reverse direction (Counter-Clockwise) by flipping horizontally
                withTransform({
                    scale(scaleX = -1f, scaleY = 1f, pivot = center)
                }) {
                    drawPath(
                        path = segmentPath,
                        color = primaryColor,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )
                }
            }
        }

        // 专辑图
        Card(
            shape = RoundedCornerShape(10.dp), // Match the path corner radius
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
            modifier = Modifier.size(38.dp) // Leave space for stroke
        ) {
            NetworkImage(
                url = music.pic,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }
        
        if (showControls) {
            MusicControlPopup(
                visible = showControls,
                onDismiss = { showControls = false },
                onPrev = onPrev,
                onNext = onNext,
                onPauseToggle = onTogglePlay,
                isPlaying = isPlaying
            )
        }
    }
}

@Composable
fun MusicItem(
    music: MusicInfo,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Album Art (Left)
        NetworkImage(
            url = music.pic,
            modifier = Modifier
                .size(60.dp)
                .clip(RoundedCornerShape(8.dp))
        )

        Spacer(modifier = Modifier.width(12.dp))

        // Info (Right)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = music.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "${music.artist} - ${music.album}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MusicPlayerDialog(
    viewModel: MusicPlayerViewModel,
    music: MusicInfo
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var downloadState by remember { mutableStateOf(DownloadState.IDLE) }
    
    val isPlaying by viewModel.isPlaying.collectAsState()
    val progress by viewModel.progress.collectAsState()
    val duration by viewModel.duration.collectAsState()
    val currentPosition by viewModel.currentPosition.collectAsState()
    val isBuffering by viewModel.isBuffering.collectAsState()
    val error by viewModel.error.collectAsState()

    var showLyrics by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = { viewModel.minimize() }, 
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        // Animation for entering/exiting from top-right
        AnimatedVisibility(
            visible = true,
            enter = slideInHorizontally { it } + slideInVertically { -it } + fadeIn(), // From Top Right roughly
            exit = slideOutHorizontally { it } + slideOutVertically { -it } + fadeOut()
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .wrapContentHeight()
                    .animateContentSize(), 
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 6.dp
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Header with Minimize and Close buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { viewModel.minimize() }) {
                            Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Minimize")
                        }
                        IconButton(onClick = { viewModel.close() }) {
                            Icon(Icons.Default.Close, contentDescription = "Close")
                        }
                    }
                    
                    // Content Area (Album Art or Lyrics)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(320.dp), 
                        contentAlignment = Alignment.Center
                    ) {
                        androidx.compose.animation.AnimatedVisibility(
                            visible = !showLyrics,
                            enter = fadeIn() + scaleIn(),
                            exit = fadeOut() + scaleOut()
                        ) {
                            NetworkImage(
                                url = music.pic,
                                modifier = Modifier
                                    .size(280.dp)
                                    .clip(RoundedCornerShape(24.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                            )
                        }

                        androidx.compose.animation.AnimatedVisibility(
                            visible = showLyrics,
                            enter = fadeIn() + slideInVertically { it / 2 },
                            exit = fadeOut() + slideOutVertically { it / 2 }
                        ) {
                            if (music.lrc.isNullOrBlank()) {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text("暂无歌词", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            } else {
                                LyricsView(
                                    lrcContent = music.lrc,
                                    currentPosition = currentPosition,
                                    modifier = Modifier.fillMaxSize(),
                                    onLineClick = { time ->
                                        viewModel.seekTo(time.toInt())
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Info
                    Text(
                        text = music.name,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = music.artist,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = music.album,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    // Controls
                    if (error != null) {
                        Text(text = error!!, color = MaterialTheme.colorScheme.error)
                    }

                    Slider(
                        value = progress,
                        onValueChange = { newProgress ->
                            viewModel.seekToProgress(newProgress)
                        },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(formatTime(currentPosition), style = MaterialTheme.typography.labelSmall)
                        Text(formatTime(duration), style = MaterialTheme.typography.labelSmall)
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Lyrics Toggle
                        FilledTonalIconButton(
                            onClick = { showLyrics = !showLyrics },
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = if (showLyrics) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = if (showLyrics) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        ) {
                            Icon(Icons.Default.Lyrics, contentDescription = "Lyrics")
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        // Prev
                        IconButton(onClick = { viewModel.playPrev() }) {
                            Icon(Icons.Default.SkipPrevious, contentDescription = "Previous", modifier = Modifier.size(32.dp))
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        // Play/Pause
                        FilledIconButton(
                            onClick = { viewModel.togglePlayPause() },
                            modifier = Modifier.size(64.dp)
                        ) {
                            if (isBuffering) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            } else {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = if (isPlaying) "Pause" else "Play",
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        // Next
                        IconButton(onClick = { viewModel.playNext() }) {
                            Icon(Icons.Default.SkipNext, contentDescription = "Next", modifier = Modifier.size(32.dp))
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        // Download
                        FilledTonalIconButton(
                            onClick = {
                                if (downloadState == DownloadState.IDLE) {
                                    val id = downloadMusic(context, music.url, "${music.name} - ${music.artist}.mp3")
                                    if (id != null) {
                                        downloadState = DownloadState.DOWNLOADING
                                        scope.launch {
                                            val success = waitForDownload(context, id)
                                            if (success) {
                                                downloadState = DownloadState.COMPLETED
                                            } else {
                                                downloadState = DownloadState.IDLE
                                                Toast.makeText(context, "下载失败", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                }
                            }
                        ) {
                            AnimatedContent(
                                targetState = downloadState,
                                transitionSpec = {
                                    (fadeIn() + scaleIn()).togetherWith(fadeOut() + scaleOut())
                                },
                                label = "DownloadStatus"
                            ) { state ->
                                when (state) {
                                    DownloadState.IDLE -> Icon(Icons.Default.Download, contentDescription = "Download")
                                    DownloadState.DOWNLOADING -> CircularWavyProgressIndicator(
                                        modifier = Modifier.size(24.dp)
                                    )
                                    DownloadState.COMPLETED -> Icon(Icons.Default.Check, contentDescription = "Completed")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LyricsView(
    lrcContent: String,
    currentPosition: Int,
    modifier: Modifier = Modifier,
    onLineClick: (Long) -> Unit
) {
    val lines = remember(lrcContent) { parseLrc(lrcContent) }
    val currentIndex = lines.indexOfLast { it.time <= currentPosition }
    val lazyListState = rememberLazyListState()
    
    LaunchedEffect(currentIndex) {
        if (currentIndex >= 0) {
            lazyListState.animateScrollToItem(if (currentIndex > 3) currentIndex - 3 else 0)
        }
    }

    LazyColumn(
        state = lazyListState,
        modifier = modifier,
        contentPadding = PaddingValues(vertical = 140.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        items(lines.size) { index ->
            val line = lines[index]
            val isCurrent = index == currentIndex
            
            val scale by animateFloatAsState(if (isCurrent) 1.2f else 1.0f, label = "scale")
            val alpha by animateFloatAsState(if (isCurrent) 1f else 0.5f, label = "alpha")
            
            Text(
                text = line.text,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onLineClick(line.time) }
                    .padding(vertical = 8.dp)
                    .scale(scale)
            )
        }
    }
}

data class LrcLine(val time: Long, val text: String)

fun parseLrc(lrc: String): List<LrcLine> {
    val lines = mutableListOf<LrcLine>()
    val regex = Pattern.compile("\\[(\\d{2}):(\\d{2})\\.(\\d{2,3})\\](.*)")
    
    lrc.lines().forEach { line ->
        val matcher = regex.matcher(line)
        if (matcher.find()) {
            val min = matcher.group(1)?.toLongOrNull() ?: 0
            val sec = matcher.group(2)?.toLongOrNull() ?: 0
            val msStr = matcher.group(3) ?: "0"
            val ms = if (msStr.length == 2) msStr.toLong() * 10 else msStr.toLong()
            
            val time = min * 60000 + sec * 1000 + ms
            val text = matcher.group(4)?.trim() ?: ""
            if (text.isNotEmpty()) {
                lines.add(LrcLine(time, text))
            }
        }
    }
    return lines.sortedBy { it.time }
}

enum class DownloadState {
    IDLE, DOWNLOADING, COMPLETED
}

fun downloadMusic(context: Context, url: String, fileName: String): Long? {
    try {
        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle(fileName)
            .setDescription("Downloading music...")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)
        
        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val id = downloadManager.enqueue(request)
        return id
    } catch (e: Exception) {
        Toast.makeText(context, "下载失败: ${e.message}", Toast.LENGTH_SHORT).show()
        return null
    }
}

suspend fun waitForDownload(context: Context, downloadId: Long): Boolean {
    return withContext(Dispatchers.IO) {
        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        var finished = false
        var success = false
        while (!finished) {
            try {
                val cursor = downloadManager.query(DownloadManager.Query().setFilterById(downloadId))
                if (cursor != null && cursor.moveToFirst()) {
                    val statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                    if (statusIndex >= 0) {
                        val status = cursor.getInt(statusIndex)
                        if (status == DownloadManager.STATUS_SUCCESSFUL) {
                            finished = true
                            success = true
                        } else if (status == DownloadManager.STATUS_FAILED) {
                            finished = true
                            success = false
                        }
                    }
                    cursor.close()
                } else {
                    finished = true
                }
            } catch (e: Exception) {
                finished = true
            }
            if (!finished) delay(500)
        }
        success
    }
}

fun parseMusicContent(content: String): MusicInfo? {
    try {
        val lines = content.split("\n")
        var name = ""
        var artist = ""
        var album = ""
        var url = ""
        var pic = ""
        var lrc = ""
        
        var readingLrc = false
        val lrcBuilder = StringBuilder()

        for (line in lines) {
            if (readingLrc) {
                lrcBuilder.append(line).append("\n")
                continue
            }
            
            val parts = line.split(":", limit = 2)
            if (parts.size >= 1) {
                val key = parts[0].trim()
                
                if (key.equals("Lrc", ignoreCase = true)) {
                    readingLrc = true
                    if (parts.size == 2) {
                         val value = parts[1].trim()
                         if (value.isNotEmpty()) {
                             lrcBuilder.append(value).append("\n")
                         }
                    }
                    continue
                }
                
                if (parts.size == 2) {
                    val value = parts[1].trim().removeSurrounding("`")
                    when (key.lowercase()) {
                        "name" -> name = value
                        "artist" -> artist = value
                        "album" -> album = value
                        "url" -> url = value
                        "pic" -> pic = value
                    }
                }
            }
        }

        if (name.isNotEmpty() && url.isNotEmpty()) {
            return MusicInfo(name, artist, album, url, pic, lrcBuilder.toString().trim())
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return null
}

@Composable
fun NetworkImage(
    url: String,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop
) {
    AsyncImage(
        model = ImageRequest.Builder(LocalContext.current)
            .data(url)
            .crossfade(true)
            .build(),
        contentDescription = null,
        modifier = modifier.background(Color.LightGray),
        contentScale = contentScale
    )
}

private fun formatTime(millis: Int): String {
    val seconds = (millis / 1000) % 60
    val minutes = (millis / (1000 * 60)) % 60
    return String.format("%02d:%02d", minutes, seconds)
}
