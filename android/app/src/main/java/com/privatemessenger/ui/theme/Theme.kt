package com.privatemessenger.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// Rich, vibrant color palette for a premium feel
val PrimaryAccent = Color(0xFF6C63FF)
val PrimaryAccentDark = Color(0xFF5A52D5)
val BackgroundDark = Color(0xFF0F0F1A)
val SurfaceDark = Color(0xFF1A1A2E)
val SurfaceVariantDark = Color(0xFF232336)
val TextPrimaryDark = Color(0xFFE0E0E0)
val TextSecondaryDark = Color(0xFFA0A0B5)
val SuccessGreen = Color(0xFF00E676)

private val DarkColorScheme = darkColorScheme(
    primary = PrimaryAccent,
    secondary = PrimaryAccentDark,
    tertiary = SuccessGreen,
    background = BackgroundDark,
    surface = SurfaceDark,
    surfaceVariant = SurfaceVariantDark,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = TextPrimaryDark,
    onSurface = TextPrimaryDark,
    onSurfaceVariant = TextSecondaryDark,
)

private val LightColorScheme = lightColorScheme(
    primary = PrimaryAccent,
    secondary = PrimaryAccentDark,
    tertiary = SuccessGreen,
    background = Color(0xFFF4F4F9),
    surface = Color.White,
    surfaceVariant = Color(0xFFEBEBF0),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color(0xFF1E1E1E),
    onSurface = Color(0xFF1E1E1E),
    onSurfaceVariant = Color(0xFF666666),
)

@Composable
fun PrivateMessengerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+, but we disable it to
    // enforce our custom, highly-curated aesthetic instead.
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
    
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = Color.Transparent.toArgb()
            WindowCompat.setDecorFitsSystemWindows(window, false)
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
