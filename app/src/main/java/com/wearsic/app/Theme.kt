package com.wearsic.app

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.wear.compose.material3.ColorScheme
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Typography

private val DarkColorScheme = ColorScheme(
    primary = Color(0xFFABC7FF),
    primaryDim = Color(0xFF7EA7F0),
    primaryContainer = Color(0xFF00458E),
    onPrimary = Color(0xFF002F65),
    onPrimaryContainer = Color(0xFFD7E2FF),
    secondary = Color(0xFFBEC6DC),
    secondaryDim = Color(0xFF96A5C5),
    secondaryContainer = Color(0xFF3E4759),
    onSecondary = Color(0xFF283141),
    onSecondaryContainer = Color(0xFFDAE2F9),
    tertiary = Color(0xFFDDBDE1),
    tertiaryDim = Color(0xFFC09DC5),
    tertiaryContainer = Color(0xFF573E5C),
    onTertiary = Color(0xFF3F2845),
    onTertiaryContainer = Color(0xFFFAD8FD),
    surfaceContainerLow = Color(0xFF1B1B1F),
    surfaceContainer = Color(0xFF252529),
    surfaceContainerHigh = Color(0xFF2F2F33),
    onSurface = Color(0xFFE4E1E6),
    onSurfaceVariant = Color(0xFFC8C5CA),
    outline = Color(0xFF918F94),
    outlineVariant = Color(0xFF45464B),
    background = Color(0xFF1B1B1F),
    onBackground = Color(0xFFE4E1E6),
    error = Color(0xFFFFB4AB),
    errorDim = Color(0xFFE8948F),
    errorContainer = Color(0xFF93000A),
    onError = Color(0xFF690005),
    onErrorContainer = Color(0xFFFFDAD6),
)

private val WearsicTypography = Typography()

@Composable
fun WearsicTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = WearsicTypography,
        content = content
    )
}
