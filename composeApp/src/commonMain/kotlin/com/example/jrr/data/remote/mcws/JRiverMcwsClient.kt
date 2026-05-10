package com.example.jrr.data.remote.mcws

import com.example.jrr.domain.model.*
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.util.*
import nl.adaptivity.xmlutil.serialization.XML

class JRiverMcwsClient(
    private val httpClient: HttpClient,
    private val api: McwsApi,
    private val xml: XML
) {
    private var baseUrl: String = ""
    private var token: String? = null

    fun updateConfig(baseUrl: String, token: String?) {
        this.baseUrl = baseUrl
        this.token = token
    }

    suspend fun alive(hostAddress: String): Result<ServerInfo> = runCatching {
        val responseXml = api.get(hostAddress, "Alive")
        val response = xml.decodeFromString(McwsResponse.serializer(), responseXml)
        val map = response.toMap()
        
        ServerInfo(
            id = map["RuntimeGUID"] ?: "",
            name = map["FriendlyName"] ?: "",
            version = map["ProgramVersion"] ?: "",
            platform = map["Platform"] ?: "",
            address = hostAddress
        )
    }

    suspend fun authenticate(hostAddress: String, username: String, password: String): Result<String> = runCatching {
        val authHeader = "Basic ${"$username:$password".encodeBase64()}"
        val response = httpClient.get("$hostAddress/MCWS/v1/Authenticate") {
            header(HttpHeaders.Authorization, authHeader)
        }
        
        if (response.status.isSuccess()) {
            val responseXml = response.body<String>()
            val mcwsResponse = xml.decodeFromString(McwsResponse.serializer(), responseXml)
            mcwsResponse.toMap()["Token"] ?: throw Exception("Token not found in response")
        } else {
            throw Exception("Authentication failed: ${response.status}")
        }
    }

    suspend fun getPlaybackInfo(zoneId: String? = null): Result<PlayerStatus> = runCatching {
        val params = mutableMapOf<String, String>()
        if (zoneId != null) {
            params["Zone"] = zoneId
            params["ZoneType"] = "ID"
        }
        
        val responseXml = api.get(baseUrl, "Playback/Info", params, token)
        val response = xml.decodeFromString(McwsResponse.serializer(), responseXml)
        val map = response.toMap()
        
        val state = PlaybackState.fromInt(map["State"]?.toIntOrNull() ?: 0)
        
        val trackInfo = if (map["FileKey"] != null) {
            TrackInfo(
                fileKey = map["FileKey"] ?: "",
                name = map["Name"] ?: "",
                artist = map["Artist"] ?: "",
                album = map["Album"] ?: "",
                imageUrl = map["ImageURL"] ?: "",
                bitrate = map["Bitrate"]?.toIntOrNull() ?: 0,
                bitDepth = map["Bitdepth"]?.toIntOrNull() ?: 0,
                sampleRate = map["SampleRate"]?.toIntOrNull() ?: 0,
                channels = map["Channels"]?.toIntOrNull() ?: 0
            )
        } else null

        PlayerStatus(
            zoneId = map["ZoneID"] ?: "",
            zoneName = map["ZoneName"] ?: "",
            state = state,
            trackInfo = trackInfo,
            positionMs = map["PositionMS"]?.toIntOrNull() ?: 0,
            durationMs = map["DurationMS"]?.toIntOrNull() ?: 0,
            positionDisplay = map["PositionDisplay"] ?: "",
            volume = map["Volume"]?.toFloatOrNull() ?: 0f,
            volumeDisplay = map["VolumeDisplay"] ?: "",
            isMuted = map["Muted"] == "1", // Derived or direct depending on MCWS version, usually derived from Playback/Mute
            shuffleMode = ShuffleMode.fromString(map["Shuffle"] ?: "Off"),
            repeatMode = RepeatMode.fromString(map["Repeat"] ?: "Off"),
            playingNowPosition = map["PlayingNowPosition"]?.toIntOrNull() ?: 0,
            playingNowTracks = map["PlayingNowTracks"]?.toIntOrNull() ?: 0,
            playingNowPositionDisplay = map["PlayingNowPositionDisplay"] ?: "",
            playingNowChangeCounter = map["PlayingNowChangeCounter"]?.toIntOrNull() ?: 0
        )
    }

    // Transport Commands
    suspend fun play(zoneId: String): Result<Unit> = runCatching {
        api.get(baseUrl, "Playback/Play", mapOf("Zone" to zoneId, "ZoneType" to "ID"), token)
    }

    suspend fun pause(zoneId: String): Result<Unit> = runCatching {
        api.get(baseUrl, "Playback/Pause", mapOf("Zone" to zoneId, "ZoneType" to "ID"), token)
    }

    suspend fun playPause(zoneId: String): Result<Unit> = runCatching {
        api.get(baseUrl, "Playback/PlayPause", mapOf("Zone" to zoneId, "ZoneType" to "ID"), token)
    }

    suspend fun stop(zoneId: String): Result<Unit> = runCatching {
        api.get(baseUrl, "Playback/Stop", mapOf("Zone" to zoneId, "ZoneType" to "ID"), token)
    }

    suspend fun next(zoneId: String): Result<Unit> = runCatching {
        api.get(baseUrl, "Playback/Next", mapOf("Zone" to zoneId, "ZoneType" to "ID"), token)
    }

    suspend fun previous(zoneId: String): Result<Unit> = runCatching {
        api.get(baseUrl, "Playback/Previous", mapOf("Zone" to zoneId, "ZoneType" to "ID"), token)
    }

    suspend fun getZones(): Result<List<Zone>> = runCatching {
        val responseXml = api.get(baseUrl, "Playback/Zones", token = token)
        val mcwsResponse = xml.decodeFromString(McwsResponse.serializer(), responseXml)
        val map = mcwsResponse.toMap()
        
        val numZones = map["NumberZones"]?.toIntOrNull() ?: 0
        val zones = mutableListOf<Zone>()
        for (i in 0 until numZones) {
            val id = map["ZoneID$i"] ?: continue
            val name = map["ZoneName$i"] ?: ""
            val guid = map["ZoneGUID$i"] ?: ""
            val isDLNA = map["ZoneDLNA$i"] == "1"
            zones.add(Zone(id, name, guid, isDLNA))
        }
        zones
    }

    suspend fun getPlayingNow(zoneId: String): Result<List<PlayingNowItem>> = runCatching {
        val params = mapOf(
            "Action" to "JSON",
            "Zone" to zoneId,
            "ZoneType" to "ID",
            "Fields" to "Name;Artist;Album",
            "NoLocalFilenames" to "1"
        )
        val responseJson = api.get(baseUrl, "Playback/Playlist", params, token)
        // Parse JSON using kotlinx.serialization
        val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
        val rawItems = json.decodeFromString<List<Map<String, String>>>(responseJson)
        
        rawItems.mapIndexed { index, map ->
            PlayingNowItem(
                index = index,
                fileKey = map["Key"] ?: "",
                name = map["Name"] ?: "",
                artist = map["Artist"] ?: "",
                album = map["Album"] ?: ""
            )
        }
    }
}
