package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

val LocalIsDarkTheme = compositionLocalOf { true }

private val ScrollDownDarkColorScheme = darkColorScheme(
    primary = GoldPrimary,
    onPrimary = ObsidianBlack,
    primaryContainer = GoldTintSurface,
    onPrimaryContainer = GoldBright,
    secondary = GoldBright,
    onSecondary = ObsidianBlack,
    secondaryContainer = ObsidianElevated,
    onSecondaryContainer = IvoryText,
    tertiary = GoldDeep,
    onTertiary = IvoryText,
    background = ObsidianBlack,
    onBackground = IvoryText,
    surface = ObsidianSurface,
    onSurface = IvoryText,
    surfaceVariant = ObsidianCard,
    onSurfaceVariant = WarmGrayText,
    surfaceContainerLowest = ObsidianBlack,
    surfaceContainerLow = ObsidianSurface,
    surfaceContainer = ObsidianCard,
    surfaceContainerHigh = ObsidianElevated,
    outline = ObsidianOutline,
    outlineVariant = ObsidianSubtleBorder,
    error = CrimsonHeart,
    onError = IvoryText
)

private val ScrollDownLightColorScheme = lightColorScheme(
    primary = Color(0xFFB38424),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFF6E9C8),
    onPrimaryContainer = Color(0xFF4A3304),
    secondary = GoldDeep,
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFEEE8DA),
    onSecondaryContainer = Color(0xFF1A1712),
    tertiary = GoldPrimary,
    onTertiary = Color(0xFF1A1712),
    background = Color(0xFFFAF7F0),
    onBackground = Color(0xFF14120F),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF14120F),
    surfaceVariant = Color(0xFFF2EDE2),
    onSurfaceVariant = Color(0xFF665F52),
    surfaceContainerLowest = Color(0xFFFAF7F0),
    surfaceContainerLow = Color(0xFFFFFFFF),
    surfaceContainer = Color(0xFFF5F0E6),
    surfaceContainerHigh = Color(0xFFEBE4D6),
    outline = Color(0xFFD5C9AE),
    outlineVariant = Color(0xFFE6DEC9),
    error = CrimsonHeart,
    onError = Color(0xFFFFFFFF)
)

val ScrollDownShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun ScrollDownTheme(
    isDarkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(LocalIsDarkTheme provides isDarkTheme) {
        MaterialTheme(
            colorScheme = if (isDarkTheme) ScrollDownDarkColorScheme else ScrollDownLightColorScheme,
            typography = ScrollDownTypography,
            shapes = ScrollDownShapes,
            content = content
        )
    }
}
