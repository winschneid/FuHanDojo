package com.winschneid.fuhandojo.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = Felt80,
    onPrimary = OnFelt20,
    primaryContainer = FeltContainer30,
    onPrimaryContainer = FeltContainer90,
    secondary = Sage80,
    onSecondary = OnSage20,
    secondaryContainer = SageContainer30,
    onSecondaryContainer = SageContainer90,
    tertiary = Vermilion80,
    onTertiary = OnVermilion20,
    tertiaryContainer = VermilionContainer30,
    onTertiaryContainer = VermilionContainer90,
    background = Surface6,
    onBackground = OnSurface90,
    surface = Surface6,
    onSurface = OnSurface90,
    surfaceVariant = SurfaceVariant30,
    onSurfaceVariant = OnSurfaceVariant80,
    surfaceContainerLowest = SurfaceContainerLowest4,
    surfaceContainerLow = SurfaceContainerLow10,
    surfaceContainer = SurfaceContainer12,
    surfaceContainerHigh = SurfaceContainerHigh17,
    surfaceContainerHighest = SurfaceContainerHighest22,
    outline = Outline60,
    outlineVariant = SurfaceVariant30,
)

private val LightColorScheme = lightColorScheme(
    primary = Felt40,
    primaryContainer = FeltContainer90,
    onPrimaryContainer = OnFeltContainer10,
    secondary = Sage40,
    secondaryContainer = SageContainer90,
    onSecondaryContainer = OnSageContainer10,
    tertiary = Vermilion40,
    tertiaryContainer = VermilionContainer90,
    onTertiaryContainer = OnVermilionContainer10,
    background = Surface98,
    onBackground = OnSurface10,
    surface = Surface98,
    onSurface = OnSurface10,
    surfaceVariant = SurfaceVariant90,
    onSurfaceVariant = OnSurfaceVariant30,
    surfaceContainerLowest = SurfaceContainerLowest100,
    surfaceContainerLow = SurfaceContainerLow96,
    surfaceContainer = SurfaceContainer94,
    surfaceContainerHigh = SurfaceContainerHigh92,
    surfaceContainerHighest = SurfaceContainerHighest90,
    outline = Outline50,
    outlineVariant = SurfaceVariant90,
)

@Composable
fun FuHanDojoTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = Typography,
        content = content,
    )
}
