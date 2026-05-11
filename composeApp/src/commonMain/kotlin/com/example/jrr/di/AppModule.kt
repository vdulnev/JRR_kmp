package com.example.jrr.di

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.example.jrr.player.LocalPlayer
import com.example.jrr.player.createLocalPlayer
import io.ktor.client.*
import nl.adaptivity.xmlutil.serialization.XML
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single
import org.koin.dsl.module

@Module
@ComponentScan("com.example.jrr")
class CoreModule(
    private val dataStore: DataStore<Preferences>,
    private val platformContext: Any? = null
) {
    @Single
    fun dataStore() = dataStore

    @Single
    fun platformContext() = platformContext

    @Single
    fun httpClient() = HttpClient()

    @Single
    fun xml() = XML { autoPolymorphic = true }

    @Single
    fun localPlayer() = createLocalPlayer(platformContext)
}

fun appModule(dataStore: DataStore<Preferences>, platformContext: Any? = null) = module {
    // The Koin Compiler Plugin provides a synthetic 'module()' function on annotated classes
    includes(CoreModule(dataStore, platformContext).module())
}
