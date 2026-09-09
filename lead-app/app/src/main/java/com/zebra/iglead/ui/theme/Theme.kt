package com.zebra.iglead.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// Zebra-flavoured navy/teal palette used when dynamic color isn't available.
private val Navy = Color(0xFF1A365D)
private val NavyLight = Color(0xFF3E5C8A)
private val Teal = Color(0xFF00857D)
private val TealLight = Color(0xFF4FB3AC)

private val LightColors = lightColorScheme(
    primary = Navy,
    secondary = Teal,
)

private val DarkColors = darkColorScheme(
    primary = NavyLight,
    secondary = TealLight,
)

// Red the UI layout uses for values that came out of the Identity Guardian
// session, in the light and dark shades needed to stay readable on either.
private val SessionRed = Color(0xFFD32F2F)
private val SessionRedDark = Color(0xFFFF8A80)

/** The colour session-supplied values are rendered in. */
@Composable
fun sessionValueColor(darkTheme: Boolean = isSystemInDarkTheme()): Color =
    if (darkTheme) SessionRedDark else SessionRed

/** App theme; prefers Material You dynamic color on Android 12+. */
@Composable
fun IdentityGuardianLeadTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content,
    )
}
