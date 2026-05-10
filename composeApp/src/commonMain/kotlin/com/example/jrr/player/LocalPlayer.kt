package com.example.jrr.player

import com.example.jrr.domain.model.PlaybackState
import kotlinx.coroutines.flow.StateFlow

interface LocalPlayer {
    val playbackState: StateFlow<PlaybackState>
    val currentPositionMs: StateFlow<Int>
    val durationMs: StateFlow<Int>
    val volume: StateFlow<Float>

    fun play(url: String)
    fun pause()
    fun resume()
    fun stop()
    fun seekTo(positionMs: Int)
    fun setVolume(level: Float)
}

expect fun createLocalPlayer(context: Any? = null): LocalPlayer
