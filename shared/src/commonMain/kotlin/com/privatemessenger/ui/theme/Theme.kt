package com.privatemessenger.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// ── Premium Monochromatic (Black & White) Palette ────────────────────────────
val PrimaryAccent      = Color(0xFFFFFFFF) // Pure White
val PrimaryAccentDark  = Color(0xFFE0E0E0)
val SecondaryAccent    = Color(0xFF888888) // Mid Gray
val BackgroundDark     = Color(0xFF000000) // Pure Black
val SurfaceDark        = Color(0xFF0A0A0A)
val SurfaceVariantDark = Color(0xFF141414)
val TextPrimaryDark    = Color(0xFFFFFFFF)
val TextSecondaryDark  = Color(0xFFAAAAAA)

private val DarkColorScheme = darkColorScheme(
    primary         = PrimaryAccent,
    secondary       = SecondaryAccent,
    tertiary        = PrimaryAccent,
    background      = BackgroundDark,
    surface         = SurfaceDark,
    surfaceVariant  = SurfaceVariantDark,
    onPrimary       = Color.Black,
    onSecondary     = Color.Black,
    onTertiary      = Color.Black,
    onBackground    = TextPrimaryDark,
    onSurface       = TextPrimaryDark,
    onSurfaceVariant = TextSecondaryDark,
)

private val LightColorScheme = lightColorScheme(
    primary         = PrimaryAccentDark,
    secondary       = Color(0xFF555555),
    background      = Color(0xFFF7F7F9),
    surface         = Color.White,
    surfaceVariant  = Color(0xFFEFEFF4),
    onPrimary       = Color.White,
    onSecondary     = Color.White,
    onBackground    = Color(0xFF121212),
    onSurface       = Color(0xFF121212),
    onSurfaceVariant = Color(0xFF666666),
)

/**
 * App-wide Material 3 theme — shared across Android and iOS.
 * Android status-bar tinting is handled in the androidApp layer.
 */
@Composable
fun PrivateMessengerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content,
    )
}
