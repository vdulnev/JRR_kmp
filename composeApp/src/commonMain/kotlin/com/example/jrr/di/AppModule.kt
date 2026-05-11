package com.example.jrr.di

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.example.jrr.data.local.JRiverSettings
import com.example.jrr.data.remote.lookup.JRiverLookupService
import com.example.jrr.data.remote.mcws.JRiverMcwsClient
import com.example.jrr.data.remote.mcws.McwsApi
import com.example.jrr.player.LocalPlayer
import com.example.jrr.player.createLocalPlayer
import com.example.jrr.service.JRiverService
import com.example.jrr.ui.library.LibraryViewModel
import com.example.jrr.ui.player.PlayerViewModel
import com.example.jrr.ui.setup.SetupViewModel
import io.ktor.client.*
import nl.adaptivity.xmlutil.serialization.XML
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

fun appModule(dataStore: DataStore<Preferences>, platformContext: Any? = null) = module {
    single { dataStore }
    single { platformContext }
    
    single { HttpClient() }
    single { XML { autoPolymorphic = true } }
    
    singleOf(::JRiverSettings)
    singleOf(::JRiverLookupService)
    singleOf(::McwsApi)
    singleOf(::JRiverMcwsClient)
    
    single<LocalPlayer> { createLocalPlayer(get()) }
    
    // Explicitly define JRiverService to avoid injecting the default CoroutineScope
    single { JRiverService(get(), get()) }
    
    viewModelOf(::SetupViewModel)
    viewModelOf(::PlayerViewModel)
    viewModelOf(::LibraryViewModel)
}
