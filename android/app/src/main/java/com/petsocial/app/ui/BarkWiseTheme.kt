package com.petsocial.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val BarkWiseGreen = Color(0xFF8FBFA3)
private val BarkWiseLilac = Color(0xFFA38FBF)
private val BarkWiseSand = Color(0xFFBFA38F)

private val BarkWiseLightColorScheme = lightColorScheme(
    primary = BarkWiseGreen,
    onPrimary = Color(0xFF111111),
    primaryContainer = Color(0xFFDCEDE3),
    onPrimaryContainer = Color(0xFF1D3024),
    secondary = BarkWiseGreen,
    onSecondary = Color(0xFF111111),
    secondaryContainer = Color(0xFFE9F4EE),
    onSecondaryContainer = Color(0xFF1D3024),
    tertiary = BarkWiseLilac,
    onTertiary = Color(0xFF161021),
    tertiaryContainer = Color(0xFFF0ECF7),
    onTertiaryContainer = Color(0xFF2A1F3A),
    background = Color(0xFFF6FBF7),
    onBackground = Color(0xFF111111),
    surface = Color.White,
    onSurface = Color(0xFF111111),
    surfaceVariant = Color(0xFFE2ECE4),
    onSurfaceVariant = Color(0xFF344238),
    outline = BarkWiseSand,
    outlineVariant = Color(0xFFE8DCCF),
)

private val BarkWiseDarkColorScheme = darkColorScheme(
    primary = Color(0xFF7BCB96),
    onPrimary = Color(0xFF052010),
    primaryContainer = Color(0xFF1F5D37),
    onPrimaryContainer = Color(0xFFD8F2E0),
    secondary = Color(0xFF7BCB96),
    onSecondary = Color(0xFF052010),
    secondaryContainer = Color(0xFF1F5D37),
    onSecondaryContainer = Color(0xFFD8F2E0),
    tertiary = Color(0xFFCABDE0),
    onTertiary = Color(0xFF261A36),
    tertiaryContainer = Color(0xFF413054),
    onTertiaryContainer = Color(0xFFEADDFA),
    background = Color(0xFF0F1511),
    onBackground = Color(0xFFE7EFE9),
    surface = Color(0xFF162019),
    onSurface = Color(0xFFE7EFE9),
    surfaceVariant = Color(0xFF25352B),
    onSurfaceVariant = Color(0xFFB9CABF),
    outline = Color(0xFF8F7A67),
    outlineVariant = Color(0xFF4B4038),
)

@Composable
fun BarkWiseTheme(content: @Composable () -> Unit) {
    val colorScheme = if (isSystemInDarkTheme()) {
        BarkWiseDarkColorScheme
    } else {
        BarkWiseLightColorScheme
    }
    MaterialTheme(
        colorScheme = colorScheme,
        content = content,
    )
}
