package com.example.jrr.data.remote.mcws

import co.touchlab.kermit.Logger
import com.example.jrr.domain.model.*
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.util.*
import io.ktor.utils.io.readBuffer
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.io.decodeFromSource
import nl.adaptivity.xmlutil.serialization.XML
import org.koin.core.annotation.Single
import kotlin.random.Random

@Single
class JRiverMcwsClient(
    private val httpClient: HttpClient,
    private val api: McwsApi,
    private val xml: XML
) {
    private val logger = Logger.withTag("JRiverMcwsClient")
    val instanceId = Random.nextInt(1000, 9999)
    private var baseUrl: String = ""
    private var token: String? = null

    init {
        logger.i { "Initialized (instanceId: $instanceId)" }
    }

    fun updateConfig(baseUrl: String, token: String?) {
        logger.i { "Updating config - instanceId: $instanceId, baseUrl: $baseUrl, token: ${token?.take(5)}..." }
        this.baseUrl = baseUrl
        this.token = token
    }

    suspend fun alive(hostAddress: String): Result<ServerInfo> = runCatching {
        logger.d { "Calling Alive on $hostAddress (instanceId: $instanceId)" }
        val responseXml = api.get(hostAddress, "Alive")
        val response = xml.decodeFromString(McwsResponse.serializer(), responseXml)
        val map = response.toMap()
        
        ServerInfo(
            id = map["runtimeguid"] ?: "",
            name = map["friendlyname"] ?: "",
            version = map["programversion"] ?: "",
            platform = map["platform"] ?: "",
            address = hostAddress
        )
    }

    suspend fun authenticate(hostAddress: String, username: String, password: String): Result<String> = runCatching {
        logger.d { "Calling Authenticate on $hostAddress (instanceId: $instanceId)" }
        val authHeader = "Basic ${"$username:$password".encodeBase64()}"
        val response = httpClient.get("$hostAddress/MCWS/v1/Authenticate") {
            header(HttpHeaders.Authorization, authHeader)
        }
        
        if (response.status.isSuccess()) {
            val responseXml = response.body<String>()
            val mcwsResponse = xml.decodeFromString(McwsResponse.serializer(), responseXml)
            mcwsResponse.toMap()["token"] ?: throw Exception("Token not found in response")
        } else {
            logger.e { "Authentication failed with status ${response.status} (instanceId: $instanceId)" }
            throw Exception("Authentication failed: ${response.status}")
        }
    }

    suspend fun getPlaybackInfo(zoneId: String? = null): Result<PlayerStatus> = runCatching {
        if (baseUrl.isBlank()) {
            logger.e { "getPlaybackInfo called with BLANK baseUrl (instanceId: $instanceId)" }
            throw Exception("baseUrl is not configured")
        }
        logger.v { "getPlaybackInfo using baseUrl: $baseUrl (instanceId: $instanceId)" }
        val params = mutableMapOf<String, String>()
        if (zoneId != null) {
            params["Zone"] = zoneId
            params["ZoneType"] = "ID"
        }
        
        val responseXml = api.get(baseUrl, "Playback/Info", params, token)
        val response = xml.decodeFromString(McwsResponse.serializer(), responseXml)
        val map = response.toMap()
        
        val state = PlaybackState.fromInt(map["state"]?.toIntOrNull() ?: 0)
        
        val trackInfo = if (map["filekey"] != null) {
            TrackInfo(
                fileKey = map["filekey"] ?: "",
                name = map["name"] ?: "",
                artist = map["artist"] ?: "",
                album = map["album"] ?: "",
                imageUrl = map["imageurl"] ?: "",
                bitrate = map["bitrate"]?.toIntOrNull() ?: 0,
                bitDepth = map["bitdepth"]?.toIntOrNull() ?: 0,
                sampleRate = map["samplerate"]?.toIntOrNull() ?: 0,
                channels = map["channels"]?.toIntOrNull() ?: 0
            )
        } else null

        PlayerStatus(
            zoneId = map["zoneid"] ?: "",
            zoneName = map["zonename"] ?: "",
            state = state,
            trackInfo = trackInfo,
            positionMs = map["positionms"]?.toIntOrNull() ?: 0,
            durationMs = map["durationms"]?.toIntOrNull() ?: 0,
            positionDisplay = map["positiondisplay"] ?: "",
            volume = map["volume"]?.toFloatOrNull() ?: 0f,
            volumeDisplay = map["volumedisplay"] ?: "",
            isMuted = map["muted"] == "1",
            shuffleMode = ShuffleMode.fromString(map["shuffle"] ?: "Off"),
            repeatMode = RepeatMode.fromString(map["repeat"] ?: "Off"),
            playingNowPosition = map["playingnowposition"]?.toIntOrNull() ?: 0,
            playingNowTracks = map["playingnowtracks"]?.toIntOrNull() ?: 0,
            playingNowPositionDisplay = map["playingnowpositiondisplay"] ?: "",
            playingNowChangeCounter = map["playingnowchangecounter"]?.toIntOrNull() ?: 0
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

    suspend fun playByKey(zoneId: String, key: String): Result<Unit> = runCatching {
        api.get(baseUrl, "Playback/PlayByKey", mapOf("Zone" to zoneId, "ZoneType" to "ID", "Key" to key), token)
    }

    suspend fun setVolume(zoneId: String, level: Float): Result<Unit> = runCatching {
        val mcwsLevel = (level * 100).toInt().coerceIn(0, 100)
        api.get(baseUrl, "Playback/Volume", mapOf("Zone" to zoneId, "ZoneType" to "ID", "Level" to mcwsLevel.toString()), token)
    }

    suspend fun seek(zoneId: String, positionMs: Int): Result<Unit> = runCatching {
        api.get(baseUrl, "Playback/Position", mapOf("Zone" to zoneId, "ZoneType" to "ID", "Position" to positionMs.toString()), token)
    }

    suspend fun getZones(): Result<List<Zone>> = runCatching {
        if (baseUrl.isBlank()) {
            logger.e { "getZones called with BLANK baseUrl (instanceId: $instanceId)" }
            throw Exception("baseUrl is not configured")
        }
        logger.d { "getZones using baseUrl: $baseUrl (instanceId: $instanceId)" }
        val responseXml = api.get(baseUrl, "Playback/Zones", token = token)
        val mcwsResponse = xml.decodeFromString(McwsResponse.serializer(), responseXml)
        val map = mcwsResponse.toMap()
        
        val numZones = map["numberzones"]?.toIntOrNull() ?: 0
        logger.d { "Found $numZones zones in XML (instanceId: $instanceId)" }
        val zones = mutableListOf<Zone>()
        for (i in 0 until numZones) {
            val id = map["zoneid$i"] ?: continue
            val name = map["zonename$i"] ?: ""
            val guid = map["zoneguid$i"] ?: ""
            val isDLNA = map["zonedlna$i"] == "1"
            zones.add(Zone(id, name, guid, isDLNA))
        }
        zones
    }

    suspend fun getPlayingNow(zoneId: String): Result<List<PlayingNowItem>> = runCatching {
        if (baseUrl.isBlank()) {
            logger.e { "getPlayingNow called with BLANK baseUrl (instanceId: $instanceId)" }
            throw Exception("baseUrl is not configured")
        }
        logger.v { "getPlayingNow using baseUrl: $baseUrl (instanceId: $instanceId)" }
        val params = mapOf(
            "Action" to "JSON",
            "Zone" to zoneId,
            "ZoneType" to "ID",
            "Fields" to "Key;Name;Artist;Album",
            "NoLocalFilenames" to "1"
        )
        val responseJson = api.get(baseUrl, "Playback/Playlist", params, token)
        
        val json = kotlinx.serialization.json.Json { 
            ignoreUnknownKeys = true
            isLenient = true
        }
        
        val jsonElement = json.parseToJsonElement(responseJson)
        
        val itemsArray = when {
            jsonElement is kotlinx.serialization.json.JsonArray -> jsonElement
            jsonElement is kotlinx.serialization.json.JsonObject -> {
                val response = jsonElement["Response"] as? kotlinx.serialization.json.JsonObject
                val target = response ?: jsonElement
                target["Item"] as? kotlinx.serialization.json.JsonArray
            }
            else -> null
        } ?: throw Exception("Could not find Playlist items in JSON response")

        val items = itemsArray.mapIndexed { index, element ->
            val obj = element as? kotlinx.serialization.json.JsonObject
                ?: throw Exception("Expected JsonObject in Playlist array")
            
            fun getString(key: String): String {
                val foundKey = obj.keys.find { it.equals(key, ignoreCase = true) }
                return foundKey?.let { k ->
                    val el = obj[k]
                    if (el is kotlinx.serialization.json.JsonPrimitive) el.content else el.toString()
                } ?: ""
            }

            PlayingNowItem(
                index = index,
                fileKey = getString("Key"),
                name = getString("Name"),
                artist = getString("Artist"),
                album = getString("Album")
            )
        }

        // Fallback: If metadata is missing (only Key is present), fetch it via Files/Search
        if (items.isNotEmpty() && items.all { it.name.isBlank() && it.fileKey.isNotBlank() }) {
            logger.i { "Metadata missing in Playlist response. Fetching via fallback search. (instanceId: $instanceId)" }
            val keys = items.joinToString(",") { it.fileKey }
            val metadataResult = getTracksByKeys(keys)
            metadataResult.fold(
                onSuccess = { metadataList ->
                    val metadataMap = metadataList.associateBy { it.fileKey.toString() }
                    items.map { item ->
                        val meta = metadataMap[item.fileKey]
                        if (meta != null) {
                            item.copy(name = meta.name, artist = meta.artist, album = meta.album)
                        } else item
                    }
                },
                onFailure = { 
                    logger.w { "Fallback metadata fetch failed: ${it.message} (instanceId: $instanceId)" }
                    items 
                }
            )
        } else {
            items
        }
    }

    suspend fun getTracksByKeys(keys: String): Result<List<Track>> = runCatching {
        val params = mapOf(
            "Action" to "JSON",
            "Query" to "[Key]=$keys",
            "Fields" to "Calculated"
        )
        val responseJson = api.get(baseUrl, "Files/Search", params, token)
        val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true; isLenient = true }
        
        val jsonElement = json.parseToJsonElement(responseJson)
        val itemsArray = if (jsonElement is kotlinx.serialization.json.JsonArray) {
            jsonElement
        } else {
            jsonElement.let { it as? kotlinx.serialization.json.JsonObject }?.get("Item") as? kotlinx.serialization.json.JsonArray
                ?: emptyList<kotlinx.serialization.json.JsonElement>()
        }

        itemsArray.map { element ->
            val obj = element as kotlinx.serialization.json.JsonObject
            fun getString(key: String): String {
                val foundKey = obj.keys.find { it.equals(key, ignoreCase = true) }
                return foundKey?.let { k ->
                    val el = obj[k]
                    if (el is kotlinx.serialization.json.JsonPrimitive) el.content else el.toString()
                } ?: ""
            }
            fun getInt(key: String): Int = getString(key).toIntOrNull() ?: 0

            Track(
                fileKey = getInt("Key"),
                name = getString("Name"),
                artist = getString("Artist"),
                album = getString("Album"),
                albumArtist = getString("Album Artist"),
                albumArtistAuto = getString("Album Artist (auto)"),
                genre = getString("Genre"),
                duration = getString("Duration").toDoubleOrNull() ?: 0.0,
                trackNumber = getInt("Track #"),
                discNumber = getInt("Disc #"),
                totalDiscs = getInt("Total Discs"),
                totalTracks = getInt("Total Tracks"),
                imageUrl = getString("Image File"),
                bitrate = getInt("Bitrate"),
                bitDepth = getInt("Bit Depth"),
                sampleRate = getInt("Sample Rate"),
                channels = getInt("Channels"),
                fileType = getString("File Type"),
                filePath = getString("Filename"),
                dateReadable = getString("Date (readable)")
            )
        }
    }

    suspend fun setQueuePosition(zoneId: String, index: Int): Result<Unit> = runCatching {
        api.get(baseUrl, "Playback/SetPosition", mapOf("Zone" to zoneId, "ZoneType" to "ID", "Index" to index.toString()), token)
    }

    suspend fun reorderQueue(zoneId: String, fromIndex: Int, toIndex: Int): Result<Unit> = runCatching {
        api.get(baseUrl, "Playback/EditPlaylist", mapOf(
            "Zone" to zoneId, 
            "ZoneType" to "ID", 
            "Action" to "Reorder", 
            "Index" to fromIndex.toString(), 
            "To" to toIndex.toString()
        ), token)
    }

    suspend fun removeFromQueue(zoneId: String, index: Int): Result<Unit> = runCatching {
        api.get(baseUrl, "Playback/EditPlaylist", mapOf(
            "Zone" to zoneId, 
            "ZoneType" to "ID", 
            "Action" to "Remove", 
            "Index" to index.toString()
        ), token)
    }

    suspend fun linkZones(zoneId: String, targetZoneIds: List<String>): Result<Unit> = runCatching {
        api.get(baseUrl, "Playback/LinkZones", mapOf(
            "Zone" to zoneId,
            "ZoneType" to "ID",
            "Zones" to targetZoneIds.joinToString(",")
        ), token)
    }

    suspend fun unlinkZone(zoneId: String): Result<Unit> = runCatching {
        api.get(baseUrl, "Playback/UnlinkZone", mapOf("Zone" to zoneId, "ZoneType" to "ID"), token)
    }

    fun buildStreamUrl(fileKey: String, conversion: String = "wav", quality: String = "high"): String {
        return URLBuilder("$baseUrl/MCWS/v1/File/GetFile").apply {
            parameters.append("File", fileKey)
            parameters.append("FileType", "Key")
            parameters.append("Playback", "1")
            parameters.append("Conversion", conversion)
            parameters.append("Quality", quality)
            token?.takeIf { it.isNotBlank() }?.let { parameters.append("Token", it) }
        }.buildString()
    }

    suspend fun browseChildren(id: String = "-1"): Result<List<BrowseItem>> = runCatching {
        val params = mapOf(
            "ID" to id,
            "Version" to "1"
        )
        val responseXml = api.get(baseUrl, "Browse/Children", params, token)
        val response = xml.decodeFromString(McwsResponse.serializer(), responseXml)
        response.items.map { BrowseItem(it.value, it.name) }
    }

    suspend fun browseFiles(id: String): Result<List<Track>> = runCatching {
        streamTracks("Browse/Files", mapOf("ID" to id, "Action" to "JSON"))
    }

    suspend fun searchFiles(query: String, fields: String = "Calculated", limit: Int = -1): Result<List<Track>> = runCatching {
        val params = mutableMapOf(
            "Action" to "JSON",
            "Query" to query,
            "Fields" to fields
        )
        if (limit > 0) params["Limit"] = limit.toString()
        streamTracks("Files/Search", params)
    }

    @OptIn(ExperimentalSerializationApi::class)
    private suspend fun streamTracks(endpoint: String, params: Map<String, String>): List<Track> {
        val url = "$baseUrl/MCWS/v1/$endpoint"
        val response = httpClient.get(url) {
            params.forEach { (k, v) -> parameter(k, v) }
            token?.let { parameter("Token", it) }
        }
        if (!response.status.isSuccess()) {
            throw Exception("MCWS request failed: ${response.status}")
        }
        val source = response.bodyAsChannel().readBuffer()
        val dtos = trackJson.decodeFromSource<List<TrackDto>>(source)
        return dtos.map { it.toTrack() }
    }
}

private val trackJson = Json { ignoreUnknownKeys = true; isLenient = true }

@Serializable
private data class TrackDto(
    @SerialName("Key") val key: String = "",
    @SerialName("Name") val name: String = "",
    @SerialName("Artist") val artist: String = "",
    @SerialName("Album") val album: String = "",
    @SerialName("Album Artist") val albumArtist: String = "",
    @SerialName("Album Artist (auto)") val albumArtistAuto: String = "",
    @SerialName("Genre") val genre: String = "",
    @SerialName("Duration") val duration: String = "",
    @SerialName("Track #") val trackNumber: String = "",
    @SerialName("Disc #") val discNumber: String = "",
    @SerialName("Total Discs") val totalDiscs: String = "",
    @SerialName("Total Tracks") val totalTracks: String = "",
    @SerialName("Image File") val imageUrl: String = "",
    @SerialName("Bitrate") val bitrate: String = "",
    @SerialName("Bit Depth") val bitDepth: String = "",
    @SerialName("Sample Rate") val sampleRate: String = "",
    @SerialName("Channels") val channels: String = "",
    @SerialName("File Type") val fileType: String = "",
    @SerialName("Filename") val filePath: String = "",
    @SerialName("Date (readable)") val dateReadable: String = "",
)

private fun TrackDto.toTrack(): Track = Track(
    fileKey = key.toIntOrNull() ?: 0,
    name = name,
    artist = artist,
    album = album,
    albumArtist = albumArtist,
    albumArtistAuto = albumArtistAuto,
    genre = genre,
    duration = duration.toDoubleOrNull() ?: 0.0,
    trackNumber = trackNumber.toIntOrNull() ?: 0,
    discNumber = discNumber.toIntOrNull() ?: 0,
    totalDiscs = totalDiscs.toIntOrNull() ?: 0,
    totalTracks = totalTracks.toIntOrNull() ?: 0,
    imageUrl = imageUrl,
    bitrate = bitrate.toIntOrNull() ?: 0,
    bitDepth = bitDepth.toIntOrNull() ?: 0,
    sampleRate = sampleRate.toIntOrNull() ?: 0,
    channels = channels.toIntOrNull() ?: 0,
    fileType = fileType,
    filePath = filePath,
    dateReadable = dateReadable,
)
