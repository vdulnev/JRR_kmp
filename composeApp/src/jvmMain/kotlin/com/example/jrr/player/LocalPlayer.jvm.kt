package com.example.jrr.player

import co.touchlab.kermit.Logger
import com.example.jrr.domain.model.PlaybackState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class JvmLocalPlayer : LocalPlayer {
    private val logger = Logger.withTag("JvmLocalPlayer")
    private val _playbackState = MutableStateFlow(PlaybackState.STOPPED)
    override val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    private val _currentPositionMs = MutableStateFlow(0)
    override val currentPositionMs: StateFlow<Int> = _currentPositionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0)
    override val durationMs: StateFlow<Int> = _durationMs.asStateFlow()

    private val _volume = MutableStateFlow(1f)
    override val volume: StateFlow<Float> = _volume.asStateFlow()

    override fun play(url: String) {
        logger.i { "Play URL $url (Placeholder)" }
        _playbackState.value = PlaybackState.PLAYING
    }

    override fun pause() {
        logger.d { "Pause" }
        _playbackState.value = PlaybackState.PAUSED
    }

    override fun resume() {
        logger.d { "Resume" }
        _playbackState.value = PlaybackState.PLAYING
    }

    override fun stop() {
        logger.d { "Stop" }
        _playbackState.value = PlaybackState.STOPPED
    }

    override fun seekTo(positionMs: Int) {
        logger.d { "Seek to $positionMs" }
        _currentPositionMs.value = positionMs
    }

    override fun setVolume(level: Float) {
        logger.v { "Set volume to $level" }
        _volume.value = level
    }
}

actual fun createLocalPlayer(context: Any?): LocalPlayer {
    return JvmLocalPlayer()
}
