package com.glassous.aime.ui.utils

import android.media.MediaPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

object AudioPlayerManager {
    private var mediaPlayer: MediaPlayer? = null
    private var progressJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main)

    private val _currentPath = MutableStateFlow<String?>(null)
    val currentPath: StateFlow<String?> = _currentPath.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _progress = MutableStateFlow(0f)
    val progress: StateFlow<Float> = _progress.asStateFlow()

    private val _currentPosition = MutableStateFlow(0L)
    val currentPosition: StateFlow<Long> = _currentPosition.asStateFlow()
    
    private val _duration = MutableStateFlow(0L)
    val duration: StateFlow<Long> = _duration.asStateFlow()

    fun play(path: String) {
        if (_currentPath.value == path) {
            // Resume or Pause
            if (_isPlaying.value) {
                pause()
            } else {
                resume()
            }
            return
        }

        // Stop previous
        stop()

        try {
            val file = File(path)
            if (!file.exists()) return

            mediaPlayer = MediaPlayer().apply {
                setDataSource(path)
                setOnPreparedListener { mp ->
                    _duration.value = mp.duration.toLong()
                    mp.start()
                    _isPlaying.value = true
                    startProgressTracker()
                }
                setOnCompletionListener {
                    stop()
                }
                prepareAsync()
            }
            
            _currentPath.value = path
        } catch (e: Exception) {
            e.printStackTrace()
            stop()
        }
    }

    private fun resume() {
        mediaPlayer?.let {
            if (!it.isPlaying) {
                it.start()
                _isPlaying.value = true
                startProgressTracker()
            }
        }
    }

    fun pause() {
        mediaPlayer?.let {
            if (it.isPlaying) {
                it.pause()
                _isPlaying.value = false
                stopProgressTracker()
            }
        }
    }

    fun stop() {
        stopProgressTracker()
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        mediaPlayer = null
        _currentPath.value = null
        _isPlaying.value = false
        _progress.value = 0f
        _currentPosition.value = 0L
        _duration.value = 0L
    }

    fun seekTo(position: Long) {
        mediaPlayer?.let {
            if (_duration.value > 0) {
                val newPos = position.coerceIn(0, _duration.value)
                it.seekTo(newPos.toInt())
                _currentPosition.value = newPos
                _progress.value = newPos.toFloat() / _duration.value.toFloat()
            }
        }
    }

    private fun startProgressTracker() {
        stopProgressTracker()
        progressJob = scope.launch {
            while (isActive) {
                mediaPlayer?.let { mp ->
                    if (mp.isPlaying) {
                        val current = mp.currentPosition.toLong()
                        val total = mp.duration.toLong()
                        _currentPosition.value = current
                        if (total > 0) {
                            _progress.value = current.toFloat() / total.toFloat()
                        }
                    }
                }
                delay(100) // Update every 100ms
            }
        }
    }

    private fun stopProgressTracker() {
        progressJob?.cancel()
        progressJob = null
    }
}
