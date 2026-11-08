package com.banhammer.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object Pal {
    val Bg = Color(0xFF0B0D12)
    val Card = Color(0xFF151922)
    val CardHi = Color(0xFF1E2430)
    val Text = Color(0xFFF2F3F5)
    val Dim = Color(0xFF8E96A3)
    val Red = Color(0xFFFF453A)
    val Green = Color(0xFF30D158)
    val Amber = Color(0xFFFFB020)
}

private val Scheme = darkColorScheme(
    primary = Pal.Red,
    onPrimary = Color.White,
    background = Pal.Bg,
    onBackground = Pal.Text,
    surface = Pal.Card,
    onSurface = Pal.Text,
    surfaceVariant = Pal.CardHi,
    onSurfaceVariant = Pal.Dim,
    secondaryContainer = Pal.Red.copy(alpha = 0.18f),
    onSecondaryContainer = Pal.Text,
)

@Composable
fun BanHammerTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme, content = content)
}
