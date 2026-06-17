package com.example.babavpn.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = CyberCyan,
    secondary = CyberMagenta,
    tertiary = CyberGreen,
    background = CyberBlack,
    surface = CyberPanel,
    onPrimary = CyberBlack,
    onSecondary = CyberTextPrimary,
    onTertiary = CyberBlack,
    onBackground = CyberTextPrimary,
    onSurface = CyberTextPrimary
)

private val LightColorScheme = lightColorScheme(
    primary = CyberBlue,
    secondary = CyberMagenta,
    tertiary = CyberGreen,
    background = Color(0xFFF6FBFF),
    surface = Color(0xFFEAF1FF),
    onPrimary = CyberTextPrimary,
    onSecondary = CyberTextPrimary,
    onTertiary = CyberBlack,
    onBackground = CyberBlack,
    onSurface = CyberBlack
)

@Composable
fun BabaVPNTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme || dynamicColor) {
        DarkColorScheme
    } else {
        LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
