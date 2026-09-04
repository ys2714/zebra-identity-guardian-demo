package com.zebra.igdemo.ui.theme

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

/** App theme; prefers Material You dynamic color on Android 12+. */
@Composable
fun IdentityGuardianDemoTheme(
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
