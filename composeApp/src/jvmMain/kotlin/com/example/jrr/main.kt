package com.example.jrr

import androidx.compose.runtime.remember
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.example.jrr.data.local.createDataStore

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "jrr_kmp",
    ) {
        val dataStore = remember { createDataStore() }
        App(dataStore)
    }
}