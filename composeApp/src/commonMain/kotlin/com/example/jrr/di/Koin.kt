package com.example.jrr.di

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.KoinAppDeclaration

fun initKoin(
    dataStore: DataStore<Preferences>,
    platformContext: Any? = null,
    appDeclaration: KoinAppDeclaration = {}
) {
    // Ensure Koin is stopped before starting (common KMP pattern to avoid double-start)
    stopKoin()
    startKoin {
        appDeclaration()
        modules(appModule(dataStore, platformContext))
    }
}
