package com.example.jrr

import androidx.compose.runtime.remember
import androidx.compose.ui.window.ComposeUIViewController
import com.example.jrr.data.local.createDataStore

fun MainViewController() = ComposeUIViewController {
    val dataStore = remember { createDataStore() }
    App(dataStore)
}