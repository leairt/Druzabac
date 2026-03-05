package com.example.druzabac.ui.theme

import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    // Brand
    primary = FrogGreen,
    onPrimary = OutlineNavy,
    primaryContainer = FrogGreenDark,
    onPrimaryContainer = OffWhite,

    secondary = Turquoise,
    onSecondary = OutlineNavy,
    secondaryContainer = TurquoiseContainer,
    onSecondaryContainer = OffWhite,

    // You can use tertiary for playful highlights (chips, toggles…)
    tertiary = AccentRed,
    onTertiary = OffWhite,
    tertiaryContainer = AccentRedDark,
    onTertiaryContainer = OffWhite,

    // Surfaces (warm dark)
    background = WarmBackground,
    onBackground = OnBackgroundWarm,
    surface = WarmSurface,
    onSurface = OnSurfaceWarm,
    surfaceVariant = WarmSurfaceVariant,
    onSurfaceVariant = OnSurfaceVariantWarm,

    // Outline/dividers
    outline = OnSurfaceVariantWarm,

    // Errors (keep red here too; Material uses this for validation states)
    error = AccentRedDark,
    onError = OffWhite,
    errorContainer = AccentRed,
    onErrorContainer = WarmBackground
)

private val LightColorScheme = lightColorScheme(
    // Light theme - clean and modern
    primary = FrogGreen,
    onPrimary = OffWhite,
    primaryContainer = FrogGreenDark,
    onPrimaryContainer = OffWhite,

    secondary = Turquoise,
    onSecondary = OffWhite,
    secondaryContainer = TurquoiseContainer,
    onSecondaryContainer = OffWhite,

    tertiary = AccentRed,
    onTertiary = OffWhite,
    tertiaryContainer = AccentRedDark,
    onTertiaryContainer = OffWhite,

    background = OffWhite,
    onBackground = OutlineNavy,
    surface = OffWhite,
    onSurface = OutlineNavy,
    surfaceVariant = WarmSurfaceVariant,
    onSurfaceVariant = OutlineNavy,

    outline = WarmSurfaceVariant,

    error = AccentRedDark,
    onError = OffWhite,
    errorContainer = AccentRed,
    onErrorContainer = OffWhite
)

@Composable
fun DruzabacTheme(
    darkTheme: Boolean = false,         // light mode default
    dynamicColor: Boolean = false,      // recommended OFF to keep your brand colors consistent
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
