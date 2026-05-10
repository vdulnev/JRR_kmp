package com.example.jrr.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class ServerInfo(
    val id: String,
    val name: String,
    val version: String,
    val platform: String,
    val address: String
)

@Serializable
data class Zone(
    val id: String,
    val name: String,
    val guid: String,
    val isDLNA: Boolean,
    val isLocal: Boolean = false
)

@Serializable
enum class PlaybackState(val value: Int) {
    STOPPED(0),
    PAUSED(1),
    PLAYING(2);

    companion object {
        fun fromInt(value: Int) = entries.find { it.value == value } ?: STOPPED
    }
}

@Serializable
enum class ShuffleMode(val value: String) {
    OFF("Off"),
    ON("On"),
    AUTOMATIC("Automatic");

    companion object {
        fun fromString(value: String) = entries.find { it.value == value } ?: OFF
    }
}

@Serializable
enum class RepeatMode(val value: String) {
    OFF("Off"),
    PLAYLIST("Playlist"),
    TRACK("Track");

    companion object {
        fun fromString(value: String) = entries.find { it.value == value } ?: OFF
    }
}

@Serializable
data class PlayerStatus(
    val zoneId: String,
    val zoneName: String,
    val state: PlaybackState,
    val trackInfo: TrackInfo? = null,
    val positionMs: Int,
    val durationMs: Int,
    val positionDisplay: String,
    val volume: Float,
    val volumeDisplay: String,
    val isMuted: Boolean,
    val shuffleMode: ShuffleMode,
    val repeatMode: RepeatMode,
    val playingNowPosition: Int,
    val playingNowTracks: Int,
    val playingNowPositionDisplay: String,
    val playingNowChangeCounter: Int
)

@Serializable
data class TrackInfo(
    val fileKey: String,
    val name: String,
    val artist: String,
    val album: String,
    val imageUrl: String,
    val bitrate: Int,
    val bitDepth: Int,
    val sampleRate: Int,
    val channels: Int
)

@Serializable
data class PlayingNowItem(
    val index: Int,
    val fileKey: String,
    val name: String,
    val artist: String,
    val album: String
)

@Serializable
data class Track(
    val fileKey: Int,
    val name: String,
    val artist: String,
    val album: String,
    val albumArtist: String,
    val albumArtistAuto: String,
    val genre: String,
    val duration: Double,
    val trackNumber: Int,
    val discNumber: Int,
    val totalDiscs: Int,
    val totalTracks: Int,
    val imageUrl: String,
    val bitrate: Int,
    val bitDepth: Int,
    val sampleRate: Int,
    val channels: Int,
    val fileType: String,
    val filePath: String,
    val dateReadable: String
)

@Serializable
data class Album(
    val name: String,
    val artist: String,
    val folderPath: String,
    val date: String
)

@Serializable
data class BrowseItem(
    val id: String,
    val name: String
)
