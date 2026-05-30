package com.example.ui.theme

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

private val DarkColorScheme =
  darkColorScheme(
    primary = GoldMedium,
    onPrimary = SlateDark,
    primaryContainer = EmeraldMedium,
    onPrimaryContainer = WarmWhite,
    secondary = EmeraldLight,
    onSecondary = WarmWhite,
    background = SlateDark,
    onBackground = WarmWhite,
    surface = SlateMedium,
    onSurface = WarmWhite,
    surfaceVariant = SlateLight,
    onSurfaceVariant = WarmWhite,
  )

private val LightColorScheme =
  lightColorScheme(
    primary = EmeraldMedium,
    onPrimary = WarmWhite,
    primaryContainer = GoldLight,
    onPrimaryContainer = SlateDark,
    secondary = EmeraldDark,
    onSecondary = WarmWhite,
    background = WarmWhite,
    onBackground = SlateDark,
    surface = Color(0xFFF0F5F2),
    onSurface = SlateDark,
    surfaceVariant = Color(0xFFE2ECE7),
    onSurfaceVariant = SlateDark,
  )

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  // Force off system dynamic colors to preserve our design concept
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
