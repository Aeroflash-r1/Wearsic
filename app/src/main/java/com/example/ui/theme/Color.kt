package com.example.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.wear.compose.material3.ColorScheme

// Wearsic Vibrant Palette Theme
val WearsicBlack = Color(0xFF000000)
val WearsicCanvasDark = Color(0xFF0A0A0A)
val WearsicSurface = Color(0xFF1C1B1F)
val WearsicSurfaceActive = Color(0xFF2C2B2F)
val WearsicSurfaceBorder = Color(0x1AFFFFFF) // white/10
val WearsicSurfaceBorderSubtle = Color(0x0DFFFFFF) // white/5

// Vibrant Accents
val WearsicVibrantLavender = Color(0xFFD0BCFF)
val WearsicLavenderSecondary = Color(0xFFCCC2DC)
val WearsicLavenderTertiary = Color(0xFFB8A1FF)
val WearsicLavenderSubtle = Color(0x33D0BCFF)
val WearsicLavenderContainer = Color(0xFF382959)

// Text Tokens
val WearsicTextPrimary = Color(0xFFFFFFFF)
val WearsicTextPrimaryDark = Color(0xFF000000)
val WearsicTextSecondary = Color(0xFFD0BCFF)
val WearsicTextWhite80 = Color(0xCCFFFFFF)
val WearsicTextWhite60 = Color(0x99FFFFFF)
val WearsicTextWhite40 = Color(0x66FFFFFF)
val WearsicTextMuted = Color(0xFF8E8A98)

// Status
val WearsicError = Color(0xFFFFB4AB)
val WearsicSuccess = Color(0xFF81C784)

// Wear Material 3 ColorScheme
val WearsicColorScheme = ColorScheme(
    primary = WearsicVibrantLavender,
    primaryDim = WearsicLavenderTertiary,
    primaryContainer = WearsicLavenderContainer,
    onPrimary = WearsicBlack,
    onPrimaryContainer = WearsicVibrantLavender,
    secondary = WearsicLavenderSecondary,
    secondaryDim = WearsicLavenderSecondary,
    secondaryContainer = WearsicSurface,
    onSecondary = WearsicBlack,
    onSecondaryContainer = WearsicTextPrimary,
    tertiary = WearsicLavenderTertiary,
    onTertiary = WearsicBlack,
    surfaceContainer = WearsicSurface,
    surfaceContainerLow = WearsicCanvasDark,
    surfaceContainerHigh = WearsicSurfaceActive,
    onSurface = WearsicTextPrimary,
    onSurfaceVariant = WearsicTextSecondary,
    outline = WearsicSurfaceBorder,
    outlineVariant = WearsicSurfaceBorderSubtle,
    background = WearsicBlack,
    onBackground = WearsicTextPrimary,
    error = WearsicError,
    onError = WearsicBlack
)
