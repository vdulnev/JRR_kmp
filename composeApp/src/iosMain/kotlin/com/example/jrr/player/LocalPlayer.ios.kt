package com.example.jrr.player

import com.example.jrr.domain.model.PlaybackState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import platform.AVFoundation.*
import platform.Foundation.*
import platform.darwin.NSObject
import kotlinx.cinterop.*

class IosLocalPlayer : LocalPlayer {
    private var player: AVPlayer? = null
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
        scope.launch {
            while (true) {
                player?.let { p ->
                    val time = p.currentTime()
                    if (CMTIME_IS_VALID(time)) {
                        _currentPositionMs.value = (CMTimeGetSeconds(time) * 1000).toInt()
                    }
                    
                    val duration = p.currentItem?.duration()
                    if (duration != null && CMTIME_IS_VALID(duration)) {
                        _durationMs.value = (CMTimeGetSeconds(duration) * 1000).toInt()
                    }

                    _playbackState.value = when (p.timeControlStatus) {
                        AVPlayerTimeControlStatusPlaying -> PlaybackState.PLAYING
                        AVPlayerTimeControlStatusWaitingToPlayAtSpecifiedRate -> PlaybackState.PLAYING // Buffering
                        else -> if (p.rate > 0) PlaybackState.PLAYING else PlaybackState.PAUSED
                    }
                }
                delay(500)
            }
        }
    }

    override fun play(url: String) {
        val nsUrl = NSURL.URLWithString(url) ?: return
        val playerItem = AVPlayerItem.playerItemWithURL(nsUrl)
        if (player == null) {
            player = AVPlayer.playerWithPlayerItem(playerItem)
        } else {
            player?.replaceCurrentItemWithPlayerItem(playerItem)
        }
        player?.play()
    }

    override fun pause() {
        player?.pause()
    }

    override fun resume() {
        player?.play()
    }

    override fun stop() {
        player?.pause()
        player?.replaceCurrentItemWithPlayerItem(null)
        _playbackState.value = PlaybackState.STOPPED
    }

    override fun seekTo(positionMs: Int) {
        val time = CMTimeMake(positionMs.toLong(), 1000)
        player?.seekToTime(time)
    }

    override fun setVolume(level: Float) {
        player?.volume = level
        _volume.value = level
    }
}

actual fun createLocalPlayer(context: Any?): LocalPlayer {
    return IosLocalPlayer()
}

// Helper functions for CMTime (simplified for KMP/Native)
@OptIn(ExperimentalForeignApi::class)
private fun CMTIME_IS_VALID(time: CValue<CMTime>): Boolean = true // Mock for now, requires deeper cinterop for exact check

@OptIn(ExperimentalForeignApi::class)
private fun CMTimeGetSeconds(time: CValue<CMTime>): Double = 0.0 // Mock for now
