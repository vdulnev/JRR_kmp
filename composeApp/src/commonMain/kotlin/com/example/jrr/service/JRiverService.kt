package com.example.jrr.service

import co.touchlab.kermit.Logger
import com.example.jrr.data.local.JRiverSettings
import com.example.jrr.domain.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.koin.core.annotation.Single

@Single
class JRiverService(
    private val mcwsService: McwsService,
    private val localAudioService: LocalAudioService,
    private val settings: JRiverSettings
) {
    private val logger = Logger.withTag("JRiverService")
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private val _activeZoneId = MutableStateFlow<String?>(null)

    val playerStatus: StateFlow<PlayerStatus?> = combine(
        _activeZoneId,
        mcwsService.playerStatus,
        localAudioService.playerStatus
    ) { activeZoneId, remoteStatus, localStatus ->
        if (activeZoneId == LocalAudioService.LOCAL_ZONE_ID) localStatus else remoteStatus
    }.onEach { status ->
        logger.v {
            "Published player status: zone=${status?.zoneId}, state=${status?.state}, " +
                "track=${status?.trackInfo?.fileKey}, positionMs=${status?.positionMs}, queueTracks=${status?.playingNowTracks}"
        }
    }.stateIn(scope, SharingStarted.Eagerly, null)

    val zones: StateFlow<List<Zone>> = combine(mcwsService.zones, flowOf(localAudioService.zone)) { remoteZones, localZone ->
        remoteZones + localZone
    }.onEach { zones ->
        logger.d { "Published zones: ${zones.joinToString { "${it.id}:${it.name}${if (it.isLocal) "(local)" else ""}" }}" }
    }.stateIn(scope, SharingStarted.Eagerly, listOf(localAudioService.zone))

    val currentQueue: StateFlow<List<PlayingNowItem>> = combine(
        _activeZoneId,
        mcwsService.currentQueue
    ) { activeZoneId, remoteQueue ->
        if (activeZoneId == LocalAudioService.LOCAL_ZONE_ID) emptyList() else remoteQueue
    }.onEach { queue ->
        logger.v { "Published queue: activeZone=${_activeZoneId.value}, size=${queue.size}" }
    }.stateIn(scope, SharingStarted.Eagerly, emptyList())

    private var startupJob: Job? = null
    private var zoneRestoreJob: Job? = null

    fun start() {
        if (startupJob?.isActive == true || zoneRestoreJob?.isActive == true) {
            logger.d {
                "Service already running: startupActive=${startupJob?.isActive == true}, " +
                    "restoreActive=${zoneRestoreJob?.isActive == true}, activeZone=${_activeZoneId.value}"
            }
            return
        }

        logger.i { "Starting service coordinator" }
        logger.d { "Starting MCWS backend for zone discovery" }
        mcwsService.start()

        startupJob = scope.launch {
            val savedGuid = settings.lastZoneGuid.first()
            logger.d { "Startup - Saved zone GUID: $savedGuid" }
            if (savedGuid == null || savedGuid == LocalAudioService.LOCAL_ZONE_GUID) {
                logger.i { "Restoring local zone on startup" }
                setActiveZone(LocalAudioService.LOCAL_ZONE_ID)
            }
        }

        zoneRestoreJob = scope.launch {
            logger.d { "Starting remote zone restore collector" }
            mcwsService.zones.drop(1).collect { remoteZones ->
                logger.d {
                    "Restore collector received remote zones: count=${remoteZones.size}, " +
                        "activeZone=${_activeZoneId.value}"
                }
                if (_activeZoneId.value != null) {
                    logger.v { "Skipping zone restore because active zone is already ${_activeZoneId.value}" }
                    return@collect
                }

                val savedGuid = settings.lastZoneGuid.first()
                val restoredZone = remoteZones.find { it.guid == savedGuid }
                when {
                    restoredZone != null -> {
                        logger.i { "Restored last selected zone: ${restoredZone.name} (id: ${restoredZone.id})" }
                        setActiveZone(restoredZone.id)
                    }
                    remoteZones.isNotEmpty() -> {
                        logger.i { "Last selected zone not found, selecting first: ${remoteZones.first().name}" }
                        setActiveZone(remoteZones.first().id)
                    }
                    else -> {
                        logger.i { "No server zones found, selecting local" }
                        setActiveZone(LocalAudioService.LOCAL_ZONE_ID)
                    }
                }
            }
        }
    }

    fun stop() {
        logger.i { "Stopping service coordinator: activeZone=${_activeZoneId.value}" }
        logger.d {
            "Cancelling coordinator jobs: startupActive=${startupJob?.isActive == true}, " +
                "restoreActive=${zoneRestoreJob?.isActive == true}"
        }
        startupJob?.cancel()
        zoneRestoreJob?.cancel()
        startupJob = null
        zoneRestoreJob = null
        _activeZoneId.value = null
        logger.d { "Stopping MCWS and local audio backends" }
        mcwsService.stop()
        localAudioService.stop()
    }

    fun setActiveZone(zoneId: String) {
        val previousZoneId = _activeZoneId.value
        logger.i { "Setting active zone: previous=$previousZoneId, next=$zoneId" }
        _activeZoneId.value = zoneId

        val zone = zones.value.find { it.id == zoneId }
        if (zone != null) {
            scope.launch {
                logger.d { "Saving last zone GUID: ${zone.guid} for zone ${zone.id}:${zone.name}" }
                settings.saveLastZone(zone.guid)
            }
        } else {
            logger.w { "Selected zone $zoneId is not present in published zones: ${zones.value.map { it.id }}" }
        }

        if (zoneId == LocalAudioService.LOCAL_ZONE_ID) {
            logger.d { "Routing active zone to local backend and clearing MCWS active zone" }
            mcwsService.clearActiveZone()
            localAudioService.start()
        } else {
            logger.d { "Routing active zone to MCWS backend and deactivating local observers" }
            localAudioService.deactivate()
            mcwsService.setActiveZone(zoneId)
        }
    }

    fun play() {
        val zoneId = activeZoneOrLog("play") ?: return
        logger.d { "Command play routed to ${backendName(zoneId)} zone=$zoneId" }
        if (zoneId == LocalAudioService.LOCAL_ZONE_ID) localAudioService.play() else mcwsService.play(zoneId)
    }

    fun pause() {
        val zoneId = activeZoneOrLog("pause") ?: return
        logger.d { "Command pause routed to ${backendName(zoneId)} zone=$zoneId" }
        if (zoneId == LocalAudioService.LOCAL_ZONE_ID) localAudioService.pause() else mcwsService.pause(zoneId)
    }

    fun playPause() {
        val zoneId = activeZoneOrLog("playPause") ?: return
        logger.d { "Command playPause routed to ${backendName(zoneId)} zone=$zoneId" }
        if (zoneId == LocalAudioService.LOCAL_ZONE_ID) localAudioService.playPause() else mcwsService.playPause(zoneId)
    }

    fun next() {
        val zoneId = remoteZoneOrLog("next") ?: return
        logger.d { "Command next routed to MCWS zone=$zoneId" }
        mcwsService.next(zoneId)
    }

    fun previous() {
        val zoneId = remoteZoneOrLog("previous") ?: return
        logger.d { "Command previous routed to MCWS zone=$zoneId" }
        mcwsService.previous(zoneId)
    }

    fun setVolume(level: Float) {
        val zoneId = activeZoneOrLog("setVolume") ?: return
        logger.d { "Command setVolume routed to ${backendName(zoneId)} zone=$zoneId level=$level" }
        if (zoneId == LocalAudioService.LOCAL_ZONE_ID) localAudioService.setVolume(level) else mcwsService.setVolume(zoneId, level)
    }

    fun seek(positionMs: Int) {
        val zoneId = activeZoneOrLog("seek") ?: return
        logger.d { "Command seek routed to ${backendName(zoneId)} zone=$zoneId positionMs=$positionMs" }
        if (zoneId == LocalAudioService.LOCAL_ZONE_ID) localAudioService.seek(positionMs) else mcwsService.seek(zoneId, positionMs)
    }

    fun playTrack(track: Track) {
        val zoneId = activeZoneOrLog("playTrack") ?: return
        logger.i {
            "Command playTrack routed to ${backendName(zoneId)} zone=$zoneId, " +
                "trackKey=${track.fileKey}, name=${track.name}, artist=${track.artist}"
        }
        if (zoneId == LocalAudioService.LOCAL_ZONE_ID) {
            localAudioService.playTrack(track)
        } else {
            mcwsService.playTrack(zoneId, track)
        }
    }

    fun setQueuePosition(index: Int) {
        val zoneId = remoteZoneOrLog("setQueuePosition") ?: return
        logger.d { "Command setQueuePosition routed to MCWS zone=$zoneId index=$index" }
        mcwsService.setQueuePosition(zoneId, index)
    }

    fun reorderQueue(from: Int, to: Int) {
        val zoneId = remoteZoneOrLog("reorderQueue") ?: return
        logger.d { "Command reorderQueue routed to MCWS zone=$zoneId from=$from to=$to" }
        mcwsService.reorderQueue(zoneId, from, to)
    }

    fun removeFromQueue(index: Int) {
        val zoneId = remoteZoneOrLog("removeFromQueue") ?: return
        logger.d { "Command removeFromQueue routed to MCWS zone=$zoneId index=$index" }
        mcwsService.removeFromQueue(zoneId, index)
    }

    fun linkZones(targetZoneIds: List<String>) {
        val zoneId = remoteZoneOrLog("linkZones") ?: return
        logger.i { "Command linkZones routed to MCWS zone=$zoneId targets=$targetZoneIds" }
        mcwsService.linkZones(zoneId, targetZoneIds)
    }

    fun unlinkZone(zoneId: String) {
        logger.i { "Command unlinkZone routed to MCWS zone=$zoneId" }
        mcwsService.unlinkZone(zoneId)
    }

    suspend fun browseChildren(id: String = "-1"): List<BrowseItem> {
        logger.d { "Library browseChildren routed to MCWS id=$id" }
        return mcwsService.browseChildren(id)
    }

    suspend fun browseFiles(id: String): List<Track> {
        logger.d { "Library browseFiles routed to MCWS id=$id" }
        return mcwsService.browseFiles(id)
    }

    suspend fun search(query: String, limit: Int = -1): List<Track> {
        logger.d { "Library search routed to MCWS queryLength=${query.length}, limit=$limit" }
        return mcwsService.search(query, limit)
    }

    private fun activeZoneOrLog(command: String): String? {
        val zoneId = _activeZoneId.value
        if (zoneId == null) {
            logger.w { "Ignoring command $command because no active zone is selected" }
        }
        return zoneId
    }

    private fun remoteZoneOrLog(command: String): String? {
        val zoneId = activeZoneOrLog(command) ?: return null
        if (zoneId == LocalAudioService.LOCAL_ZONE_ID) {
            logger.d { "Ignoring MCWS-only command $command while local zone is active" }
            return null
        }
        return zoneId
    }

    private fun backendName(zoneId: String): String {
        return if (zoneId == LocalAudioService.LOCAL_ZONE_ID) "local" else "MCWS"
    }
}
