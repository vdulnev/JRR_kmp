package com.example.jrr.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

@Composable
fun ObsidianTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = ObsidianTypography,
        shapes = ObsidianShapes,
        content = content
    )
}
