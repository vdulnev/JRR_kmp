package com.example.jrr.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences

@Suppress("UNUSED_PARAMETER")
expect fun createDataStore(context: Any? = null): DataStore<Preferences>

internal const val DATASTORE_FILE_NAME = "jrr_settings.preferences_pb"
