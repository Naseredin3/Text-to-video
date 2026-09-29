package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection

private val CinemaDarkColorScheme = darkColorScheme(
    primary = TorchAmber,
    onPrimary = OnTorchAmber,
    primaryContainer = TorchAmberContainer,
    onPrimaryContainer = TorchAmberBright,
    secondary = AnamorphicCyan,
    onSecondary = Color(0xFF001E2C),
    secondaryContainer = AnamorphicCyanContainer,
    onSecondaryContainer = Color(0xFFB8E8FF),
    tertiary = DialogueCoral,
    onTertiary = Color(0xFF2D050D),
    tertiaryContainer = DialogueCoralContainer,
    onTertiaryContainer = Color(0xFFFFD9DF),
    background = ObsidianBackground,
    onBackground = TextPrimaryDark,
    surface = ObsidianSurface,
    onSurface = TextPrimaryDark,
    surfaceVariant = ObsidianSurfaceVariant,
    onSurfaceVariant = TextSecondaryDark,
    surfaceContainerHigh = ObsidianSurfaceElevated,
    outline = OutlineDark,
    outlineVariant = Color(0xFF222B3A)
)

private val CinemaLightColorScheme = lightColorScheme(
    primary = TorchAmberLight,
    onPrimary = Color.White,
    primaryContainer = TorchAmberLightContainer,
    onPrimaryContainer = Color(0xFF3A2000),
    secondary = Color(0xFF0284C7),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE0F2FE),
    onSecondaryContainer = Color(0xFF075985),
    tertiary = Color(0xFFE11D48),
    onTertiary = Color.White,
    background = ParchmentBackground,
    onBackground = Color(0xFF171B24),
    surface = ParchmentSurface,
    onSurface = Color(0xFF171B24),
    surfaceVariant = ParchmentSurfaceVariant,
    onSurfaceVariant = Color(0xFF4A5568),
    outline = Color(0xFFD2C9B8)
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true,
    language: AppLanguage = AppLanguage.FA,
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) CinemaDarkColorScheme else CinemaLightColorScheme
    val strings = if (language == AppLanguage.FA) PersianStrings else EnglishStrings
    val layoutDirection = if (language.isRtl) LayoutDirection.Rtl else LayoutDirection.Ltr
    val typography = buildAppTypography(isPersian = language == AppLanguage.FA)

    CompositionLocalProvider(
        LocalAppStrings provides strings,
        LocalLayoutDirection provides layoutDirection
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = typography,
            content = content
        )
    }
}
