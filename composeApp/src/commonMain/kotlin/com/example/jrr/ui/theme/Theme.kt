package com.example.jrr.ui.theme

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

@Composable
fun ObsidianTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = ObsidianTypography,
        shapes = ObsidianShapes
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color(0xFF131314), // Force Obsidian background
            contentColor = Color(0xFFE5E2E3) // Force OnSurface color
        ) {
            content()
        }
    }
}
