package com.example.jrr

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import co.touchlab.kermit.Logger
import com.example.jrr.data.local.createDataStore
import com.example.jrr.di.initKoin
import com.example.jrr.ui.theme.ObsidianTheme
import org.koin.android.ext.koin.androidContext

class MainActivity : ComponentActivity() {
    private val logger = Logger.withTag("MainActivity")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        logger.i { "onCreate started" }
        enableEdgeToEdge()
        
        val dataStore: DataStore<Preferences> = createDataStore(this)
        
        initKoin(dataStore, this) {
            androidContext(this@MainActivity)
        }
        
        setContent {
            ObsidianTheme {
                App()
            }
        }
    }
}
