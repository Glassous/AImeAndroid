package com.glassous.aime.ui.components

import android.app.DownloadManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.util.regex.Pattern

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
    var selectedMusic by remember { mutableStateOf<MusicInfo?>(null) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        musicList.forEachIndexed { index, music ->
            MusicItem(
                music = music,
                onClick = { selectedMusic = music }
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

    if (selectedMusic != null) {
        MusicPlayerDialog(
            music = selectedMusic!!,
            onDismiss = { selectedMusic = null }
        )
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
    music: MusicInfo,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var downloadState by remember { mutableStateOf(DownloadState.IDLE) }
    
    // Media Player State
    var isPlaying by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0f) }
    var duration by remember { mutableStateOf(0) }
    var currentPosition by remember { mutableStateOf(0) }
    var isBuffering by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    
    val mediaPlayer = remember { MediaPlayer() }

    DisposableEffect(music.url) {
        try {
            mediaPlayer.reset()
            mediaPlayer.setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .build()
            )
            mediaPlayer.setDataSource(music.url)
            mediaPlayer.setOnPreparedListener { mp ->
                duration = mp.duration
                isBuffering = false
                mp.start()
                isPlaying = true
            }
            mediaPlayer.setOnCompletionListener {
                isPlaying = false
                currentPosition = 0
                progress = 0f
            }
            mediaPlayer.setOnErrorListener { _, _, _ ->
                isBuffering = false
                error = "无法播放"
                true
            }
            mediaPlayer.prepareAsync()
        } catch (e: Exception) {
            isBuffering = false
            error = "加载失败"
        }

        onDispose {
            try {
                if (mediaPlayer.isPlaying) {
                    mediaPlayer.stop()
                }
                mediaPlayer.release()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            try {
                if (mediaPlayer.isPlaying) {
                    currentPosition = mediaPlayer.currentPosition
                    if (duration > 0) {
                        progress = currentPosition.toFloat() / duration.toFloat()
                    }
                }
            } catch (e: Exception) { }
            delay(100) // Smooth lyrics
        }
    }

    var showLyrics by remember { mutableStateOf(false) }

    // 1. Prevent dismissal on click outside
    Dialog(
        onDismissRequest = {}, // Empty lambda prevents dismissal on back press (handled manually) or outside click
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false // Explicitly disable outside click dismissal
        )
    ) {
        // MD3 Expressive: Larger corner radius
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .wrapContentHeight()
                .animateContentSize(), // Smooth transition when size changes
            shape = RoundedCornerShape(28.dp), // Extra Large shape
            color = MaterialTheme.colorScheme.surfaceContainerHigh, // Expressive surface color
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header with Close button
                Box(modifier = Modifier.fillMaxWidth()) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.align(Alignment.TopEnd)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }
                
                // Content Area (Album Art or Lyrics)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(320.dp), // Fixed height to prevent jumping
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
                                .clip(RoundedCornerShape(24.dp)) // Expressive shape
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
                                    try {
                                        mediaPlayer.seekTo(time.toInt())
                                        // Update local state immediately for responsiveness
                                        currentPosition = time.toInt()
                                        if (duration > 0) {
                                            progress = currentPosition.toFloat() / duration.toFloat()
                                        }
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                    }
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
                        progress = newProgress
                        val newPosition = (newProgress * duration).toInt()
                        currentPosition = newPosition
                        try {
                            mediaPlayer.seekTo(newPosition)
                        } catch (e: Exception) { }
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
                    horizontalArrangement = Arrangement.Center, // Centered
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Lyrics Toggle (Left)
                    FilledTonalIconButton(
                        onClick = { showLyrics = !showLyrics },
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = if (showLyrics) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = if (showLyrics) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    ) {
                        Icon(Icons.Default.Lyrics, contentDescription = "Lyrics")
                    }

                    Spacer(modifier = Modifier.width(24.dp))

                    // Play/Pause (Center)
                    FilledIconButton(
                        onClick = {
                            try {
                                if (isPlaying) {
                                    mediaPlayer.pause()
                                    isPlaying = false
                                } else {
                                    mediaPlayer.start()
                                    isPlaying = true
                                }
                            } catch (e: Exception) { }
                        },
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

                    Spacer(modifier = Modifier.width(24.dp))

                    // Download (Right)
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

@Composable
fun LyricsView(
    lrcContent: String,
    currentPosition: Int,
    modifier: Modifier = Modifier,
    onLineClick: (Long) -> Unit
) {
    val lines = remember(lrcContent) { parseLrc(lrcContent) }
    
    // Better: Find current line index
    val currentIndex = lines.indexOfLast { it.time <= currentPosition }
    
    // Let's switch to LazyColumn for lyrics
    val lazyListState = androidx.compose.foundation.lazy.rememberLazyListState()
    
    LaunchedEffect(currentIndex) {
        if (currentIndex >= 0) {
            lazyListState.animateScrollToItem(if (currentIndex > 3) currentIndex - 3 else 0)
        }
    }

    androidx.compose.foundation.lazy.LazyColumn(
        state = lazyListState,
        modifier = modifier,
        contentPadding = PaddingValues(vertical = 140.dp), // Padding to allow scrolling to ends
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        items(lines.size) { index ->
            val line = lines[index]
            val isCurrent = index == currentIndex
            
            val scale by animateFloatAsState(if (isCurrent) 1.2f else 1.0f)
            val alpha by animateFloatAsState(if (isCurrent) 1f else 0.5f)
            
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
    // Regex for [mm:ss.xx] or [mm:ss.xxx]
    val regex = Pattern.compile("\\[(\\d{2}):(\\d{2})\\.(\\d{2,3})\\](.*)")
    
    lrc.lines().forEach { line ->
        val matcher = regex.matcher(line)
        if (matcher.find()) {
            val min = matcher.group(1)?.toLongOrNull() ?: 0
            val sec = matcher.group(2)?.toLongOrNull() ?: 0
            val msStr = matcher.group(3) ?: "0"
            // If 2 digits, it's 10ms units (e.g. .12 = 120ms). If 3, it's ms.
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

// Updated Helper to parse music tag content including Lrc
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
                // If we hit a line that looks like a key but is actually part of lyrics?
                // Standard keys: Name:, Artist:, etc.
                // But lyrics can contain anything.
                // However, our generator puts Lrc last.
                // So we can safely assume everything after Lrc: is lyrics.
                lrcBuilder.append(line).append("\n")
                continue
            }
            
            val parts = line.split(":", limit = 2)
            if (parts.size >= 1) {
                val key = parts[0].trim()
                
                if (key.equals("Lrc", ignoreCase = true)) {
                    readingLrc = true
                    if (parts.size == 2) {
                         val value = parts[1].trim() // removeSurrounding("`")?
                         // If value is not empty, append it.
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
    modifier: Modifier = Modifier
) {
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(url) {
        if (url.isEmpty()) return@LaunchedEffect
        withContext(Dispatchers.IO) {
            try {
                val connection = URL(url).openConnection() as HttpURLConnection
                connection.doInput = true
                connection.connect()
                val input = connection.inputStream
                val decoded = BitmapFactory.decodeStream(input)
                bitmap = decoded
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isLoading = false
            }
        }
    }

    Box(
        modifier = modifier.background(Color.LightGray),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap!!.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize()
            )
        } else if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                strokeWidth = 2.dp,
                color = Color.White
            )
        } else {
            // Error or placeholder
            Icon(
                imageVector = Icons.Default.Close, // Or a music note icon if available
                contentDescription = "Error",
                tint = Color.Gray
            )
        }
    }
}

private fun formatTime(millis: Int): String {
    val seconds = (millis / 1000) % 60
    val minutes = (millis / (1000 * 60)) % 60
    return String.format("%02d:%02d", minutes, seconds)
}
