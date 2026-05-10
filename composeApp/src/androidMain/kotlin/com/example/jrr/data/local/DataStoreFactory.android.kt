package com.example.jrr.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile

actual fun createDataStore(context: Any?): DataStore<Preferences> {
    require(context is Context) { "Android context is required to create DataStore" }
    return androidx.datastore.preferences.core.PreferenceDataStoreFactory.create(
        produceFile = { context.preferencesDataStoreFile(DATASTORE_FILE_NAME) }
    )
}
