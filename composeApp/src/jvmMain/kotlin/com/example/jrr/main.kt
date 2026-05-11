package com.example.jrr

import androidx.compose.runtime.remember
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.example.jrr.data.local.createDataStore
import com.example.jrr.di.initKoin
import com.example.jrr.ui.theme.ObsidianTheme

fun main() = application {
    val dataStore = remember { createDataStore() }
    
    remember {
        initKoin(dataStore)
    }

    Window(
        onCloseRequest = ::exitApplication,
        title = "jrr_kmp",
    ) {
        ObsidianTheme {
            App()
        }
    }
}
