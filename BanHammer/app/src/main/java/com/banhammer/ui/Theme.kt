package com.banhammer.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val Light = lightColorScheme(
    primary = Color(0xFF4056B0), onPrimary = Color.White,
    primaryContainer = Color(0xFFDDE1FF), onPrimaryContainer = Color(0xFF001452),
    secondaryContainer = Color(0xFFDDE1F9), onSecondaryContainer = Color(0xFF161B2C),
    background = Color(0xFFFAF8FF), onBackground = Color(0xFF1A1B21),
    surface = Color(0xFFFAF8FF), onSurface = Color(0xFF1A1B21),
    surfaceVariant = Color(0xFFE2E1EC), onSurfaceVariant = Color(0xFF45464F),
    outline = Color(0xFF767680), outlineVariant = Color(0xFFC6C5D0),
    error = Color(0xFFBA1A1A), onError = Color.White,
    errorContainer = Color(0xFFFFDAD6), onErrorContainer = Color(0xFF410002),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFF4F2FA),
    surfaceContainer = Color(0xFFEEEDF4), surfaceContainerHigh = Color(0xFFE8E7EF),
    surfaceContainerHighest = Color(0xFFE3E1E9),
)

private val Dark = darkColorScheme(
    primary = Color(0xFFB6C4FF), onPrimary = Color(0xFF0F2578),
    primaryContainer = Color(0xFF283E91), onPrimaryContainer = Color(0xFFDDE1FF),
    secondaryContainer = Color(0xFF414659), onSecondaryContainer = Color(0xFFDDE1F9),
    background = Color(0xFF121318), onBackground = Color(0xFFE3E1E9),
    surface = Color(0xFF121318), onSurface = Color(0xFFE3E1E9),
    surfaceVariant = Color(0xFF45464F), onSurfaceVariant = Color(0xFFC6C5D0),
    outline = Color(0xFF90909A), outlineVariant = Color(0xFF45464F),
    error = Color(0xFFFFB4AB), onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A), onErrorContainer = Color(0xFFFFDAD6),
    surfaceContainerLowest = Color(0xFF0C0E13), surfaceContainerLow = Color(0xFF1A1B21),
    surfaceContainer = Color(0xFF1E1F25), surfaceContainerHigh = Color(0xFF292A2F),
    surfaceContainerHighest = Color(0xFF34343A),
)

/** Follows the system light/dark setting and, on Android 12+, the user's wallpaper colours. */
@Composable
fun BanHammerTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val ctx = LocalContext.current
    val scheme = when {
        Build.VERSION.SDK_INT >= 31 -> if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dark -> Dark
        else -> Light
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
