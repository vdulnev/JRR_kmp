package com.example.jrr.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.koin.core.annotation.Single

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import com.example.jrr.domain.model.SavedServer

@Single
class JRiverSettings(
    private val dataStore: DataStore<Preferences>
) {
    private object Keys {
        val SERVER_ADDRESS = stringPreferencesKey("server_address")
        val ACCESS_KEY = stringPreferencesKey("access_key")
        val AUTH_TOKEN = stringPreferencesKey("auth_token")
        val LAST_ZONE_GUID = stringPreferencesKey("last_zone_guid")
        val USERNAME = stringPreferencesKey("username")
        val PASSWORD = stringPreferencesKey("password")
        val RECENT_SERVERS = stringPreferencesKey("recent_servers")
        val BIT_PERFECT = booleanPreferencesKey("bit_perfect")
        val CROSSFADE_DURATION = floatPreferencesKey("crossfade_duration")
        val SHOW_VU_METER = booleanPreferencesKey("show_vu_meter")
        val SPECTRUM_COMPLEXITY = intPreferencesKey("spectrum_complexity")
    }

    val serverAddress: Flow<String?> = dataStore.data.map { it[Keys.SERVER_ADDRESS] }
    val accessKey: Flow<String?> = dataStore.data.map { it[Keys.ACCESS_KEY] }
    val authToken: Flow<String?> = dataStore.data.map { it[Keys.AUTH_TOKEN] }
    val lastZoneGuid: Flow<String?> = dataStore.data.map { it[Keys.LAST_ZONE_GUID] }
    val username: Flow<String?> = dataStore.data.map { it[Keys.USERNAME] }
    val password: Flow<String?> = dataStore.data.map { it[Keys.PASSWORD] }

    val bitPerfect: Flow<Boolean> = dataStore.data.map { it[Keys.BIT_PERFECT] ?: true }
    val crossfadeDuration: Flow<Float> = dataStore.data.map { it[Keys.CROSSFADE_DURATION] ?: 4.5f }
    val showVuMeter: Flow<Boolean> = dataStore.data.map { it[Keys.SHOW_VU_METER] ?: true }
    val spectrumComplexity: Flow<Int> = dataStore.data.map { it[Keys.SPECTRUM_COMPLEXITY] ?: 64 }

    val recentServers: Flow<List<SavedServer>> = dataStore.data.map { prefs ->
        val json = prefs[Keys.RECENT_SERVERS] ?: return@map emptyList()
        try {
            Json.decodeFromString<List<SavedServer>>(json)
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun saveServerDetails(address: String, accessKey: String?) {
        dataStore.edit { preferences ->
            preferences[Keys.SERVER_ADDRESS] = address
            if (accessKey != null) {
                preferences[Keys.ACCESS_KEY] = accessKey
            } else {
                preferences.remove(Keys.ACCESS_KEY)
            }
        }
    }

    suspend fun saveAuthenticatedServer(address: String, accessKey: String?, token: String) {
        dataStore.edit { preferences ->
            preferences[Keys.SERVER_ADDRESS] = address
            preferences[Keys.AUTH_TOKEN] = token
            if (accessKey != null) {
                preferences[Keys.ACCESS_KEY] = accessKey
            } else {
                preferences.remove(Keys.ACCESS_KEY)
            }
        }
    }

    suspend fun addRecentServer(server: SavedServer) {
        dataStore.edit { prefs ->
            val current = try {
                val json = prefs[Keys.RECENT_SERVERS] ?: "[]"
                Json.decodeFromString<List<SavedServer>>(json)
            } catch (e: Exception) {
                emptyList()
            }
            
            val updated = (listOf(server) + current.filter { it.address != server.address })
                .take(5)
            
            prefs[Keys.RECENT_SERVERS] = Json.encodeToString(updated)
        }
    }

    suspend fun saveAuthToken(token: String?) {
        dataStore.edit { preferences ->
            if (token != null) {
                preferences[Keys.AUTH_TOKEN] = token
            } else {
                preferences.remove(Keys.AUTH_TOKEN)
            }
        }
    }

    suspend fun saveCredentials(username: String, password: String) {
        dataStore.edit { preferences ->
            preferences[Keys.USERNAME] = username
            preferences[Keys.PASSWORD] = password
        }
    }

    suspend fun saveLastZone(guid: String) {
        dataStore.edit { preferences ->
            preferences[Keys.LAST_ZONE_GUID] = guid
        }
    }

    suspend fun setBitPerfect(enabled: Boolean) {
        dataStore.edit { it[Keys.BIT_PERFECT] = enabled }
    }

    suspend fun setCrossfadeDuration(seconds: Float) {
        dataStore.edit { it[Keys.CROSSFADE_DURATION] = seconds }
    }

    suspend fun setShowVuMeter(enabled: Boolean) {
        dataStore.edit { it[Keys.SHOW_VU_METER] = enabled }
    }

    suspend fun setSpectrumComplexity(bands: Int) {
        dataStore.edit { it[Keys.SPECTRUM_COMPLEXITY] = bands }
    }

    suspend fun clear() {
        dataStore.edit { it.clear() }
    }
}
