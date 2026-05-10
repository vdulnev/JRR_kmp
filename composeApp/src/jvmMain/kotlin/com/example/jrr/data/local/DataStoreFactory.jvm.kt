package com.example.jrr.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import okio.Path.Companion.toPath
import java.io.File

actual fun createDataStore(context: Any?): DataStore<Preferences> {
    return PreferenceDataStoreFactory.create(
        produceFile = {
            File(System.getProperty("user.home"), DATASTORE_FILE_NAME)
        }
    )
}
