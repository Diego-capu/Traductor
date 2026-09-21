package com.antigravity.translator.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * Strict Hatsune Miku Cyberpunk Color Scheme for Miku_AI.
 * Dynamic Android Material You palettes are explicitly disabled to preserve brand identity.
 */
private val MikuColorScheme = darkColorScheme(
    primary = MikuPrimary,
    onPrimary = MikuOnPrimary,
    primaryContainer = MikuPrimaryContainer,
    onPrimaryContainer = MikuPrimary,
    inversePrimary = MikuPrimaryDark,
    secondary = MikuAccentMagenta,
    onSecondary = MikuOnSecondary,
    secondaryContainer = MikuMagentaContainer,
    onSecondaryContainer = MikuAccentMagenta,
    tertiary = MikuPrimary,
    onTertiary = MikuOnPrimary,
    background = MikuDarkBackground,
    onBackground = MikuTextPrimary,
    surface = MikuSurface,
    onSurface = MikuTextPrimary,
    surfaceVariant = MikuSurfaceContainer,
    onSurfaceVariant = MikuTextSecondary,
    surfaceTint = MikuPrimary,
    inverseSurface = MikuTextPrimary,
    inverseOnSurface = MikuDarkBackground,
    error = MikuError,
    onError = MikuOnSecondary,
    outline = MikuBorder,
    outlineVariant = MikuBorderSubtle
)

@Suppress("UNUSED_PARAMETER")
@Composable
fun ScreenTranslatorTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false, // Strictly false to preserve Miku visual identity
    content: @Composable () -> Unit
) {
    val colorScheme = MikuColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = MikuDarkBackground.toArgb()
            window.navigationBarColor = MikuDarkBackground.toArgb()
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = false
            controller.isAppearanceLightNavigationBars = false
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}

