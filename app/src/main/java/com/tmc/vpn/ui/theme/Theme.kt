package com.tmc.vpn.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable

private val TmcDarkColorScheme = darkColorScheme(
    primary = TmcColors.Accent,
    onPrimary = TmcColors.Background,
    secondary = TmcColors.AccentBlue,
    onSecondary = TmcColors.Background,
    background = TmcColors.Background,
    onBackground = TmcColors.TextPrimary,
    surface = TmcColors.Surface,
    onSurface = TmcColors.TextPrimary,
    surfaceVariant = TmcColors.SurfaceElevated,
    onSurfaceVariant = TmcColors.TextSecondary,
    error = TmcColors.Disconnected
)

/**
 * Opt-in design system for the V4.6 screen migration.
 * P1 does not wrap or replace the existing UltimateApp MaterialTheme.
 */
@Composable
fun TmcVpnTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = TmcDarkColorScheme,
        typography = Typography(),
        content = content
    )
}
