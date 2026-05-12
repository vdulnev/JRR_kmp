package com.example.jrr

import androidx.compose.runtime.remember
import androidx.compose.ui.window.ComposeUIViewController
import com.example.jrr.data.local.createDataStore
import com.example.jrr.di.initKoin
import com.example.jrr.ui.theme.ObsidianTheme

fun MainViewController() = ComposeUIViewController {
    val dataStore = remember { createDataStore() }

    remember {
        initKoin(dataStore)
    }

    ObsidianTheme {
        App()
    }
}
