package com.example.jrr.di

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import org.koin.core.context.startKoin
import org.koin.dsl.KoinAppDeclaration

fun initKoin(
    dataStore: DataStore<Preferences>,
    platformContext: Any? = null,
    appDeclaration: KoinAppDeclaration = {}
) {
    // Prevent multiple startKoin calls
    if (org.koin.core.context.GlobalContext.getOrNull() == null) {
        startKoin {
            appDeclaration()
            modules(appModule(dataStore, platformContext))
        }
    }
}
