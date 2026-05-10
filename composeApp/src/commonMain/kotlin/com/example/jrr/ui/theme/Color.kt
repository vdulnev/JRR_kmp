package com.example.jrr.ui.theme

import androidx.compose.ui.graphics.Color

val Gold = Color(0xFFF8BC51)
val GoldContainer = Color(0xFFC8922A)
val Obsidian = Color(0xFF131314)
val Surface = Color(0xFF131314)
val SurfaceContainer = Color(0xFF201F21)
val SurfaceContainerHigh = Color(0xFF2A2A2B)
val OnSurface = Color(0xFFE5E2E3)
val OnSurfaceVariant = Color(0xFFD4C4B0)
val Outline = Color(0xFF9C8F7C)
val OutlineVariant = Color(0xFF504536)

val DarkColorScheme = androidx.compose.material3.darkColorScheme(
    primary = Gold,
    onPrimary = Color(0xFF422C00),
    primaryContainer = GoldContainer,
    onPrimaryContainer = Color(0xFF462F00),
    secondary = Color(0xFFC8C6C8),
    onSecondary = Color(0xFF303032),
    secondaryContainer = Color(0xFF474649),
    onSecondaryContainer = Color(0xFFB7B4B7),
    background = Obsidian,
    onBackground = OnSurface,
    surface = Surface,
    onSurface = OnSurface,
    surfaceVariant = Color(0xFF353436),
    onSurfaceVariant = OnSurfaceVariant,
    outline = Outline,
    outlineVariant = OutlineVariant,
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005)
)
