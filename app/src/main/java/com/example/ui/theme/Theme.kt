package com.example.ui.theme

import android.app.Activity
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.example.model.AppColorTheme

@Composable
fun MyApplicationTheme(
    activeTheme: AppColorTheme = AppColorTheme.MidnightTeal,
    content: @Composable () -> Unit
) {
    val targetTokens = getTokensForTheme(activeTheme)
    val animatedTokens = rememberAnimatedPabaruTokens(targetTokens)

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val activity = view.context as? Activity
            val window = activity?.window
            if (window != null) {
                // Eliminate black background flash by setting window decorView background to theme canvas
                window.decorView.setBackgroundColor(animatedTokens.bg.toArgb())
                val insetsController = WindowCompat.getInsetsController(window, view)
                val isDark = animatedTokens.isDark
                insetsController.isAppearanceLightStatusBars = !isDark
                insetsController.isAppearanceLightNavigationBars = !isDark
            }
        }
    }

    val colorScheme = if (animatedTokens.isDark) {
        darkColorScheme(
            primary = animatedTokens.primary,
            onPrimary = animatedTokens.primaryText,
            secondary = animatedTokens.secondaryBg,
            onSecondary = animatedTokens.text,
            tertiary = animatedTokens.accent,
            onTertiary = animatedTokens.primaryText,
            tertiaryContainer = animatedTokens.accentTint,
            onTertiaryContainer = animatedTokens.text,
            background = animatedTokens.bg,
            onBackground = animatedTokens.text,
            surface = animatedTokens.card,
            onSurface = animatedTokens.text,
            surfaceVariant = animatedTokens.secondaryBg,
            onSurfaceVariant = animatedTokens.textMuted,
            outline = animatedTokens.cardBorder,
            outlineVariant = animatedTokens.cardBorder.copy(alpha = 0.5f)
        )
    } else {
        lightColorScheme(
            primary = animatedTokens.primary,
            onPrimary = animatedTokens.primaryText,
            secondary = animatedTokens.secondaryBg,
            onSecondary = animatedTokens.text,
            tertiary = animatedTokens.accent,
            onTertiary = animatedTokens.primaryText,
            tertiaryContainer = animatedTokens.accentTint,
            onTertiaryContainer = animatedTokens.text,
            background = animatedTokens.bg,
            onBackground = animatedTokens.text,
            surface = animatedTokens.card,
            onSurface = animatedTokens.text,
            surfaceVariant = animatedTokens.secondaryBg,
            onSurfaceVariant = animatedTokens.textMuted,
            outline = animatedTokens.cardBorder,
            outlineVariant = animatedTokens.cardBorder.copy(alpha = 0.5f)
        )
    }

    CompositionLocalProvider(LocalPabaruTokens provides animatedTokens) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
