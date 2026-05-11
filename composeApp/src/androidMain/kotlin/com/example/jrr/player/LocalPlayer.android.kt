package com.example.jrr.player

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import co.touchlab.kermit.Logger
import com.example.jrr.domain.model.PlaybackState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AndroidLocalPlayer(context: Context) : LocalPlayer {
    private val logger = Logger.withTag("AndroidLocalPlayer")
    private val exoPlayer = ExoPlayer.Builder(context).build()
    private val scope = CoroutineScope(Dispatchers.Main + Job())

    private val _playbackState = MutableStateFlow(PlaybackState.STOPPED)
    override val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    private val _currentPositionMs = MutableStateFlow(0)
    override val currentPositionMs: StateFlow<Int> = _currentPositionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0)
    override val durationMs: StateFlow<Int> = _durationMs.asStateFlow()

    private val _volume = MutableStateFlow(1f)
    override val volume: StateFlow<Float> = _volume.asStateFlow()

    init {
        logger.d { "Initializing ExoPlayer" }
        exoPlayer.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                val newState = when (state) {
                    Player.STATE_BUFFERING, Player.STATE_READY -> if (exoPlayer.playWhenReady) PlaybackState.PLAYING else PlaybackState.PAUSED
                    Player.STATE_ENDED, Player.STATE_IDLE -> PlaybackState.STOPPED
                    else -> PlaybackState.STOPPED
                }
                logger.v { "ExoPlayer state changed: $state -> $newState" }
                _playbackState.value = newState
                _durationMs.value = exoPlayer.duration.coerceAtLeast(0).toInt()
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                logger.v { "ExoPlayer isPlaying changed: $isPlaying" }
                _playbackState.value = if (isPlaying) PlaybackState.PLAYING else PlaybackState.PAUSED
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                logger.e(error) { "ExoPlayer error: ${error.message}" }
            }
        })

        // Polling for position
        scope.launch {
            while (true) {
                if (exoPlayer.isPlaying) {
                    _currentPositionMs.value = exoPlayer.currentPosition.toInt()
                }
                delay(500)
            }
        }
    }

    override fun play(url: String) {
        logger.i { "Playing URL: $url" }
        val mediaItem = MediaItem.fromUri(url)
        exoPlayer.setMediaItem(mediaItem)
        exoPlayer.prepare()
        exoPlayer.play()
    }

    override fun pause() {
        logger.d { "Pause" }
        exoPlayer.pause()
    }

    override fun resume() {
        logger.d { "Resume" }
        exoPlayer.play()
    }

    override fun stop() {
        logger.d { "Stop" }
        exoPlayer.stop()
    }

    override fun seekTo(positionMs: Int) {
        logger.d { "Seek to $positionMs" }
        exoPlayer.seekTo(positionMs.toLong())
    }

    override fun setVolume(level: Float) {
        logger.v { "Set volume to $level" }
        exoPlayer.volume = level
        _volume.value = level
    }
}

actual fun createLocalPlayer(context: Any?): LocalPlayer {
    require(context is Context) { "Android context required" }
    return AndroidLocalPlayer(context)
}
