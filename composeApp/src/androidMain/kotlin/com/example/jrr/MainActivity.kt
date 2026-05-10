package com.example.jrr

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

import androidx.compose.runtime.remember
import com.example.jrr.data.local.createDataStore

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.core.DataStore

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            val dataStore: DataStore<Preferences> = remember { createDataStore(this) }
            App(dataStore, this)
        }
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    // For preview, we might need a mock, but for now just pass a simple one if possible
    // or just comment out if it's blocking
}