package com.example.jrr.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.koin.core.annotation.Single

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
    }

    val serverAddress: Flow<String?> = dataStore.data.map { it[Keys.SERVER_ADDRESS] }
    val accessKey: Flow<String?> = dataStore.data.map { it[Keys.ACCESS_KEY] }
    val authToken: Flow<String?> = dataStore.data.map { it[Keys.AUTH_TOKEN] }
    val lastZoneGuid: Flow<String?> = dataStore.data.map { it[Keys.LAST_ZONE_GUID] }
    val username: Flow<String?> = dataStore.data.map { it[Keys.USERNAME] }
    val password: Flow<String?> = dataStore.data.map { it[Keys.PASSWORD] }

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

    suspend fun clear() {
        dataStore.edit { it.clear() }
    }
}
