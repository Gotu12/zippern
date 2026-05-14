package com.zipper.datingapp.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Density
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary              = BrandPink,
    onPrimary            = Color.White,
    primaryContainer     = BrandPinkDim,
    onPrimaryContainer   = BrandPink,
    secondary            = BrandPurple,
    onSecondary          = Color.White,
    secondaryContainer   = BrandPurpleDim,
    onSecondaryContainer = Color(0xFFCBB2FF),
    tertiary             = BrandCyan,
    onTertiary           = Color.Black,
    background           = AppBackground,
    onBackground         = TextPrimary,
    surface              = AppSurface,
    onSurface            = TextPrimary,
    surfaceVariant       = AppSurfaceVar,
    onSurfaceVariant     = TextSecondary,
    surfaceContainer     = AppContainer,
    surfaceContainerHigh = AppContainerHi,
    outline              = OutlineDefault,
    outlineVariant       = OutlineSubtle,
    scrim                = Color(0xCC000000),
    inverseSurface       = TextPrimary,
    inverseOnSurface     = AppBackground,
    inversePrimary       = Color(0xFF9B0034),
    error                = Color(0xFFFF6B6B),
    onError              = Color.White,
)

/**
 * Material 3–aligned light palette with stronger text/surface contrast than dynamic defaults,
 * so body copy, hints, and nav icons stay readable on pale surfaces.
 */
private val LightColorScheme = lightColorScheme(
    primary = BrandPink,
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFD9E0),
    onPrimaryContainer = Color(0xFF3E001A),
    secondary = BrandPurple,
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE8DEFF),
    onSecondaryContainer = Color(0xFF1A0062),
    tertiary = Color(0xFF006781),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFD8E4),
    onTertiaryContainer = Color(0xFF31111D),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
    background = Color(0xFFFFFBFE),
    onBackground = Color(0xFF1C1B1F),
    surface = Color(0xFFFFFBFE),
    onSurface = Color(0xFF1C1B1F),
    surfaceVariant = Color(0xFFE7E0EC),
    onSurfaceVariant = Color(0xFF49454F),
    outline = Color(0xFF79747E),
    outlineVariant = Color(0xFFCAC4D0),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF7F2FA),
    surfaceContainer = Color(0xFFF3EDF7),
    surfaceContainerHigh = Color(0xFFECE6F0),
    surfaceContainerHighest = Color(0xFFE6E0E9),
    inverseSurface = Color(0xFF313033),
    inverseOnSurface = Color(0xFFF4EFF4),
    inversePrimary = Color(0xFFD0BCFF),
    scrim = Color(0xFF000000),
)

@Composable
fun DatingAppTheme(
    darkTheme: Boolean = true,
    /** When true on Android 12+, system dynamic palette overrides our high-contrast schemes. */
    dynamicColor: Boolean = false,
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

    // Flip status-bar and navigation-bar icon colours to match the active theme so
    // the clock/battery/home indicators are always readable regardless of day/night mode.
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !darkTheme
            controller.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = {
            val density = LocalDensity.current
            // Clamp OS font scale so large accessibility settings cannot blow up dense Compose layouts (calls, live, chat).
            val capped = density.fontScale.coerceIn(1f, 1.2f)
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, capped),
                content = content
            )
        }
    )
}
