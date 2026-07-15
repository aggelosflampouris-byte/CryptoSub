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

// Premium Monochromatic (Black & White) Aesthetic
val PrimaryAccent = Color(0xFFFFFFFF) // Pure White
val PrimaryAccentDark = Color(0xFFE0E0E0)
val SecondaryAccent = Color(0xFF888888) // Mid Gray
val SecondaryAccentDark = Color(0xFF555555)
val BackgroundDark = Color(0xFF000000) // Pure Black
val SurfaceDark = Color(0xFF0A0A0A) // Very Dark Gray for Glass
val SurfaceVariantDark = Color(0xFF141414)
val TextPrimaryDark = Color(0xFFFFFFFF)
val TextSecondaryDark = Color(0xFFAAAAAA)
val SuccessGreen = Color(0xFFFFFFFF) // Use white instead of green
val ErrorRed = Color(0xFF555555) // Use gray instead of red for errors in mono

private val DarkColorScheme = darkColorScheme(
    primary = PrimaryAccent,
    secondary = SecondaryAccent,
    tertiary = SuccessGreen,
    error = ErrorRed,
    background = BackgroundDark,
    surface = SurfaceDark,
    surfaceVariant = SurfaceVariantDark,
    onPrimary = Color.Black,
    onSecondary = Color.Black,
    onTertiary = Color.Black,
    onBackground = TextPrimaryDark,
    onSurface = TextPrimaryDark,
    onSurfaceVariant = TextSecondaryDark,
)

// For light mode, a sleek Frosted White aesthetic
private val LightColorScheme = lightColorScheme(
    primary = PrimaryAccentDark,
    secondary = SecondaryAccentDark,
    tertiary = SuccessGreen,
    error = ErrorRed,
    background = Color(0xFFF7F7F9),
    surface = Color.White,
    surfaceVariant = Color(0xFFEFEFF4),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color(0xFF121212),
    onSurface = Color(0xFF121212),
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
