package com.example.driverlicensescanner.ui.theme

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

// Define Light and Dark Color Schemes
private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF6200EE), // Example color
    secondary = Color(0xFF03DAC6),
)

private val LightColorScheme = lightColorScheme(
    primary = Color.Blue,
    secondary = Color.Green,
    // Add other custom colors
)

// DriverLicenseScannerTheme with dynamic theming and light/dark support
@Composable
fun DriverLicenseScannerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
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
        typography = Typography, // Ensure you define Typography or import it
        content = content
    )
}
