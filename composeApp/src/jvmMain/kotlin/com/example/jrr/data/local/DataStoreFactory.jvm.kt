package com.example.jrr.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import java.io.File

@Suppress("UNUSED_PARAMETER")
actual fun createDataStore(context: Any?): DataStore<Preferences> {
    Logger.withTag("DataStoreFactory").i { "Creating JVM DataStore" }
    return PreferenceDataStoreFactory.create(
        produceFile = {
            File(System.getProperty("user.home"), DATASTORE_FILE_NAME)
        }
    )
}
