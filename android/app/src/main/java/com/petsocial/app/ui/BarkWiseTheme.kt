package com.petsocial.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val BarkWiseGreen = Color(0xFF6EA887)
private val BarkWiseGreenSoft = Color(0xFF8FBFA3)
private val BarkWiseGreenMist = Color(0xFFB8D8C5)
private val BarkWiseLilac = Color(0xFFA38FBF)
private val BarkWiseSand = Color(0xFFBFA38F)

private val BarkWiseLightColorScheme = lightColorScheme(
    primary = BarkWiseGreen,
    onPrimary = Color(0xFFF7FCF8),
    primaryContainer = BarkWiseGreenSoft,
    onPrimaryContainer = Color(0xFF102219),
    secondary = BarkWiseGreenSoft,
    onSecondary = Color(0xFF102219),
    secondaryContainer = Color(0xFFD9EBDD),
    onSecondaryContainer = Color(0xFF1D3024),
    tertiary = BarkWiseLilac,
    onTertiary = Color(0xFF161021),
    tertiaryContainer = Color(0xFFF0ECF7),
    onTertiaryContainer = Color(0xFF2A1F3A),
    background = Color(0xFFF2F8F4),
    onBackground = Color(0xFF111111),
    surface = Color.White,
    onSurface = Color(0xFF111111),
    surfaceVariant = BarkWiseGreenMist,
    onSurfaceVariant = Color(0xFF344238),
    outline = BarkWiseSand,
    outlineVariant = Color(0xFFE8DCCF),
)

private val BarkWiseDarkColorScheme = darkColorScheme(
    primary = BarkWiseGreenSoft,
    onPrimary = Color(0xFF0C1D15),
    primaryContainer = Color(0xFF3A6A53),
    onPrimaryContainer = Color(0xFFD8F2E0),
    secondary = Color(0xFFA6C6B2),
    onSecondary = Color(0xFF0C1D15),
    secondaryContainer = Color(0xFF325244),
    onSecondaryContainer = Color(0xFFD8F2E0),
    tertiary = Color(0xFFBEAFD3),
    onTertiary = Color(0xFF261A36),
    tertiaryContainer = Color(0xFF4A395D),
    onTertiaryContainer = Color(0xFFEADDFA),
    background = Color(0xFF0D110F),
    onBackground = Color(0xFFE7EFE9),
    surface = Color(0xFF171D1A),
    onSurface = Color(0xFFE7EFE9),
    surfaceVariant = Color(0xFF2E3933),
    onSurfaceVariant = Color(0xFFC7D6CC),
    outline = Color(0xFF8A7D72),
    outlineVariant = Color(0xFF72807A),
    surfaceDim = Color(0xFF121714),
    surfaceBright = Color(0xFF343B37),
    surfaceContainerLowest = Color(0xFF0A0E0C),
    surfaceContainerLow = Color(0xFF151B18),
    surfaceContainer = Color(0xFF1B231F),
    surfaceContainerHigh = Color(0xFF25302B),
    surfaceContainerHighest = Color(0xFF303B35),
)

@Composable
fun BarkWiseTheme(
    themeMode: String = "system",
    content: @Composable () -> Unit,
) {
    val useDarkTheme = when (themeMode.trim().lowercase()) {
        "dark" -> true
        "light" -> false
        else -> isSystemInDarkTheme()
    }
    val colorScheme = if (useDarkTheme) {
        BarkWiseDarkColorScheme
    } else {
        BarkWiseLightColorScheme
    }
    MaterialTheme(
        colorScheme = colorScheme,
        content = content,
    )
}
