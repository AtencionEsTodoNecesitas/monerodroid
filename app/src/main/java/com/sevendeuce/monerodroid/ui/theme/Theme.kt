package com.sevendeuce.monerodroid.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val MoneroDarkColorScheme = darkColorScheme(
    primary = MoneroOrange,
    onPrimary = TextWhite,
    primaryContainer = MoneroOrangeDark,
    onPrimaryContainer = TextWhite,
    secondary = MoneroOrangeLight,
    onSecondary = TextWhite,
    background = DarkBackground,
    onBackground = TextWhite,
    surface = CardBackground,
    onSurface = TextWhite,
    surfaceVariant = CardBackgroundLight,
    onSurfaceVariant = TextGray,
    error = ErrorRed,
    onError = TextWhite
)

@Composable
fun MonerodroidTheme(
    content: @Composable () -> Unit
) {
    val colorScheme = MoneroDarkColorScheme
    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = false
            @Suppress("DEPRECATION")
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) {
                window.statusBarColor = DarkBackground.toArgb()
                window.navigationBarColor = DarkBackground.toArgb()
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
