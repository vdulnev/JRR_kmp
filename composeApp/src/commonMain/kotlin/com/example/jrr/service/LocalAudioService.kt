package com.example.jrr.service

import co.touchlab.kermit.Logger
import com.example.jrr.data.remote.mcws.JRiverMcwsClient
import com.example.jrr.domain.model.PlaybackState
import com.example.jrr.domain.model.PlayerStatus
import com.example.jrr.domain.model.RepeatMode
import com.example.jrr.domain.model.ShuffleMode
import com.example.jrr.domain.model.Track
import com.example.jrr.domain.model.TrackInfo
import com.example.jrr.domain.model.Zone
import com.example.jrr.player.LocalPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.koin.core.annotation.Single

@Single
class LocalAudioService(
    private val localPlayer: LocalPlayer,
    private val mcwsClient: JRiverMcwsClient
) {
    private val logger = Logger.withTag("LocalAudioService")
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    val zone = Zone(
        id = LOCAL_ZONE_ID,
        name = "This Device",
        guid = LOCAL_ZONE_GUID,
        isDLNA = false,
        isLocal = true
    )

    private val _playerStatus = MutableStateFlow(createStatus(null))
    val playerStatus: StateFlow<PlayerStatus> = _playerStatus.asStateFlow()

    private var playbackStateJob: Job? = null
    private var positionJob: Job? = null
    private var currentTrack: Track? = null

    fun start() {
        if (playbackStateJob?.isActive == true || positionJob?.isActive == true) {
            logger.d { "Local audio service already running" }
            return
        }

        playbackStateJob = scope.launch {
            localPlayer.playbackState.collect {
                logger.v { "Local player state changed: $it" }
                updateStatus()
            }
        }
        positionJob = scope.launch {
            localPlayer.currentPositionMs.collect { updateStatus() }
        }
        updateStatus()
    }

    fun stop() {
        deactivate()
        localPlayer.stop()
    }

    fun deactivate() {
        playbackStateJob?.cancel()
        positionJob?.cancel()
        playbackStateJob = null
        positionJob = null
    }

    fun play() {
        localPlayer.resume()
    }

    fun pause() {
        localPlayer.pause()
    }

    fun playPause() {
        if (localPlayer.playbackState.value == PlaybackState.PLAYING) {
            localPlayer.pause()
        } else {
            localPlayer.resume()
        }
    }

    fun setVolume(level: Float) {
        localPlayer.setVolume(level)
    }

    fun seek(positionMs: Int) {
        localPlayer.seekTo(positionMs)
    }

    fun playTrack(track: Track) {
        currentTrack = track
        localPlayer.play(mcwsClient.buildStreamUrl(track.fileKey.toString()))
        updateStatus()
    }

    private fun updateStatus() {
        _playerStatus.value = createStatus(currentTrack)
    }

    private fun createStatus(track: Track?): PlayerStatus {
        val positionMs = localPlayer.currentPositionMs.value
        return PlayerStatus(
            zoneId = LOCAL_ZONE_ID,
            zoneName = zone.name,
            state = localPlayer.playbackState.value,
            trackInfo = track?.let {
                TrackInfo(
                    fileKey = it.fileKey.toString(),
                    name = it.name,
                    artist = it.artist,
                    album = it.album,
                    imageUrl = it.imageUrl,
                    bitrate = it.bitrate,
                    bitDepth = it.bitDepth,
                    sampleRate = it.sampleRate,
                    channels = it.channels
                )
            },
            positionMs = positionMs,
            durationMs = localPlayer.durationMs.value,
            positionDisplay = formatTime(positionMs),
            volume = localPlayer.volume.value,
            volumeDisplay = "${(localPlayer.volume.value * 100).toInt()}%",
            isMuted = false,
            shuffleMode = ShuffleMode.OFF,
            repeatMode = RepeatMode.OFF,
            playingNowPosition = 0,
            playingNowTracks = if (track == null) 0 else 1,
            playingNowPositionDisplay = "",
            playingNowChangeCounter = 0
        )
    }

    private fun formatTime(ms: Int): String {
        val totalSeconds = ms / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return "${minutes}:${seconds.toString().padStart(2, '0')}"
    }

    companion object {
        const val LOCAL_ZONE_ID = "local"
        const val LOCAL_ZONE_GUID = "local_guid"
    }
}
