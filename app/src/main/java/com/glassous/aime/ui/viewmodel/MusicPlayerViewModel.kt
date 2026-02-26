package com.glassous.aime.ui.viewmodel

import android.media.AudioAttributes
import android.media.MediaPlayer
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.glassous.aime.ui.components.MusicInfo
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class MusicPlayerViewModel : ViewModel() {
    private val _playlist = MutableStateFlow<List<MusicInfo>>(emptyList())
    val playlist: StateFlow<List<MusicInfo>> = _playlist.asStateFlow()

    private val _currentMusic = MutableStateFlow<MusicInfo?>(null)
    val currentMusic: StateFlow<MusicInfo?> = _currentMusic.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _isMinimized = MutableStateFlow(false)
    val isMinimized: StateFlow<Boolean> = _isMinimized.asStateFlow()

    // Controls whether the player dialog is visible (expanded)
    private val _showPlayer = MutableStateFlow(false)
    val showPlayer: StateFlow<Boolean> = _showPlayer.asStateFlow()

    // Controls whether the mini player is visible (minimized)
    private val _showMiniPlayer = MutableStateFlow(false)
    val showMiniPlayer: StateFlow<Boolean> = _showMiniPlayer.asStateFlow()

    private val _progress = MutableStateFlow(0f)
    val progress: StateFlow<Float> = _progress.asStateFlow()

    private val _duration = MutableStateFlow(0)
    val duration: StateFlow<Int> = _duration.asStateFlow()

    private val _currentPosition = MutableStateFlow(0)
    val currentPosition: StateFlow<Int> = _currentPosition.asStateFlow()

    private val _isBuffering = MutableStateFlow(false)
    val isBuffering: StateFlow<Boolean> = _isBuffering.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private var mediaPlayer: MediaPlayer? = null
    private var progressJob: Job? = null

    init {
        mediaPlayer = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .build()
            )
            setOnPreparedListener { mp ->
                _duration.value = mp.duration
                _isBuffering.value = false
                mp.start()
                _isPlaying.value = true
                startProgressUpdate()
            }
            setOnCompletionListener {
                _isPlaying.value = false
                _currentPosition.value = 0
                _progress.value = 0f
                stopProgressUpdate()
                playNext() // Auto play next
            }
            setOnErrorListener { _, _, _ ->
                _isBuffering.value = false
                _isPlaying.value = false
                _error.value = "无法播放"
                true
            }
        }
    }

    fun playMusic(music: MusicInfo, list: List<MusicInfo> = emptyList(), autoExpand: Boolean = true) {
        if (list.isNotEmpty()) {
            _playlist.value = list
        }
        
        // If it's the same music, just ensure player is shown if autoExpand is true
        if (_currentMusic.value?.url == music.url) {
            if (autoExpand) {
                _showPlayer.value = true
                _isMinimized.value = false
                _showMiniPlayer.value = false
            }
            return
        }

        _currentMusic.value = music
        if (autoExpand) {
            _showPlayer.value = true
            _isMinimized.value = false
            _showMiniPlayer.value = false
        } else {
             // Keep current state
             // If already minimized, stay minimized (showMiniPlayer=true, showPlayer=false)
             // If already expanded, stay expanded? No, if autoExpand=false, usually we are in minimized mode.
             // But let's respect _isMinimized state.
             if (_isMinimized.value) {
                _showPlayer.value = false
                _showMiniPlayer.value = true
             } else {
                 // If not minimized (maybe closed?), and we play music without autoExpand,
                 // should we show mini player?
                 // If closed, _isMinimized is false.
                 // So if we play in background, maybe show mini player?
                 _showPlayer.value = false
                 _showMiniPlayer.value = true
                 _isMinimized.value = true
             }
        }

        
        _error.value = null
        _isBuffering.value = true
        
        try {
            mediaPlayer?.reset()
            mediaPlayer?.setDataSource(music.url)
            mediaPlayer?.prepareAsync()
        } catch (e: Exception) {
            _isBuffering.value = false
            _error.value = "加载失败"
        }
    }

    fun togglePlayPause() {
        mediaPlayer?.let { mp ->
            if (mp.isPlaying) {
                mp.pause()
                _isPlaying.value = false
                stopProgressUpdate()
            } else {
                mp.start()
                _isPlaying.value = true
                startProgressUpdate()
            }
        }
    }

    fun seekTo(position: Int) {
        try {
            mediaPlayer?.seekTo(position)
            _currentPosition.value = position
            if (_duration.value > 0) {
                _progress.value = position.toFloat() / _duration.value
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun seekToProgress(progress: Float) {
        val position = (progress * _duration.value).toInt()
        seekTo(position)
    }

    fun playNext() {
        val currentList = _playlist.value
        val current = _currentMusic.value
        if (currentList.isEmpty() || current == null) return

        val index = currentList.indexOfFirst { it.url == current.url }
        if (index != -1) {
            val nextIndex = (index + 1) % currentList.size
            // Don't expand if currently minimized
            playMusic(currentList[nextIndex], autoExpand = !_isMinimized.value)
        }
    }

    fun playPrev() {
        val currentList = _playlist.value
        val current = _currentMusic.value
        if (currentList.isEmpty() || current == null) return

        val index = currentList.indexOfFirst { it.url == current.url }
        if (index != -1) {
            val prevIndex = (index - 1 + currentList.size) % currentList.size
            // Don't expand if currently minimized
            playMusic(currentList[prevIndex], autoExpand = !_isMinimized.value)
        }
    }

    fun minimize() {
        _showPlayer.value = false
        _isMinimized.value = true
        _showMiniPlayer.value = true
    }

    fun expand() {
        _showPlayer.value = true
        _isMinimized.value = false
        _showMiniPlayer.value = false
    }

    fun close() {
        mediaPlayer?.stop()
        _isPlaying.value = false
        _showPlayer.value = false
        _showMiniPlayer.value = false
        _isMinimized.value = false
        stopProgressUpdate()
        _currentMusic.value = null
    }

    private fun startProgressUpdate() {
        stopProgressUpdate()
        progressJob = viewModelScope.launch {
            while (isActive) {
                mediaPlayer?.let { mp ->
                    if (mp.isPlaying) {
                        val pos = mp.currentPosition
                        _currentPosition.value = pos
                        if (_duration.value > 0) {
                            _progress.value = pos.toFloat() / _duration.value
                        }
                    }
                }
                delay(100)
            }
        }
    }

    private fun stopProgressUpdate() {
        progressJob?.cancel()
        progressJob = null
    }

    override fun onCleared() {
        super.onCleared()
        mediaPlayer?.release()
        mediaPlayer = null
    }
}
