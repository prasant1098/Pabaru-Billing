package com.example.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import com.example.model.AppColorTheme

/**
 * Design Tokens matching the Pabaru Theme System:
 * --bg, --header-bg, --header-text, --card, --card-border, --text, --text-muted,
 * --primary, --primary-text, --secondary-bg, --accent, --accent-tint, --link, --shadow.
 */
data class PabaruTokens(
    val bg: Color,
    val headerBg: Color,
    val headerText: Color,
    val card: Color,
    val cardBorder: Color,
    val text: Color,
    val textMuted: Color,
    val primary: Color,
    val primaryText: Color,
    val secondaryBg: Color,
    val accent: Color,
    val accentTint: Color,
    val link: Color,
    val shadow: Color,
    val isDark: Boolean
)

// 1. Midnight Teal (current dark)
val MidnightTealTokens = PabaruTokens(
    bg = Color(0xFF0F172A),
    headerBg = Color(0xFF0F172A),
    headerText = Color(0xFFFFFFFF),
    card = Color(0xFF1E293B),
    cardBorder = Color(0xFF334155),
    text = Color(0xFFF8FAFC),
    textMuted = Color(0xFF94A3B8),
    primary = Color(0xFF0D9488),
    primaryText = Color(0xFFFFFFFF),
    secondaryBg = Color(0xFF132238),
    accent = Color(0xFF14B8A6),
    accentTint = Color(0xFF134E4A),
    link = Color(0xFF38BDF8),
    shadow = Color(0x66000000),
    isDark = true
)

// 2. Clinic Blue (light): header #1E3A8A, app bg #F3F6FB, card #FFFFFF, border #E3E9F3, text #0F1F4D, muted #5B6B8C, primary #1E3A8A, accent teal #0F9D8A
val ClinicBlueTokens = PabaruTokens(
    bg = Color(0xFFF3F6FB),
    headerBg = Color(0xFF1E3A8A),
    headerText = Color(0xFFFFFFFF),
    card = Color(0xFFFFFFFF),
    cardBorder = Color(0xFFE3E9F3),
    text = Color(0xFF0F1F4D),
    textMuted = Color(0xFF5B6B8C),
    primary = Color(0xFF1E3A8A),
    primaryText = Color(0xFFFFFFFF),
    secondaryBg = Color(0xFFE8EEF8),
    accent = Color(0xFF0F9D8A),
    accentTint = Color(0xFFCCFBF1),
    link = Color(0xFF1E3A8A),
    shadow = Color(0x1A1E3A8A),
    isDark = false
)

// 3. Fresh Mint (light): header #0F766E, app bg #F0FAF8, card #FFFFFF, border #D5EEE9, text #0B3B36, muted #55756F, primary #0F766E, accent #14B8A6
val FreshMintTokens = PabaruTokens(
    bg = Color(0xFFF0FAF8),
    headerBg = Color(0xFF0F766E),
    headerText = Color(0xFFFFFFFF),
    card = Color(0xFFFFFFFF),
    cardBorder = Color(0xFFD5EEE9),
    text = Color(0xFF0B3B36),
    textMuted = Color(0xFF55756F),
    primary = Color(0xFF0F766E),
    primaryText = Color(0xFFFFFFFF),
    secondaryBg = Color(0xFFE0F4F0),
    accent = Color(0xFF14B8A6),
    accentTint = Color(0xFFCCFBF1),
    link = Color(0xFF0F766E),
    shadow = Color(0x1A0F766E),
    isDark = false
)

// 4. Warm Sand (light): header #9A3412, app bg #FBF7F2, card #FFFFFF, border #EFE3D3, text #3B2412, muted #7A6652, primary #C2410C, accent #D97706
val WarmSandTokens = PabaruTokens(
    bg = Color(0xFFFBF7F2),
    headerBg = Color(0xFF9A3412),
    headerText = Color(0xFFFFFFFF),
    card = Color(0xFFFFFFFF),
    cardBorder = Color(0xFFEFE3D3),
    text = Color(0xFF3B2412),
    textMuted = Color(0xFF7A6652),
    primary = Color(0xFFC2410C),
    primaryText = Color(0xFFFFFFFF),
    secondaryBg = Color(0xFFF5EBE1),
    accent = Color(0xFFD97706),
    accentTint = Color(0xFFFEF3C7),
    link = Color(0xFFC2410C),
    shadow = Color(0x1A9A3412),
    isDark = false
)

// 5. Royal Purple (dark): bg #14101F, card #1E1830, border #2E2547, text #F1ECFF, muted #A99BC9, primary #8B5CF6, accent #C4B5FD
val RoyalPurpleTokens = PabaruTokens(
    bg = Color(0xFF14101F),
    headerBg = Color(0xFF1E1830),
    headerText = Color(0xFFF1ECFF),
    card = Color(0xFF1E1830),
    cardBorder = Color(0xFF2E2547),
    text = Color(0xFFF1ECFF),
    textMuted = Color(0xFFA99BC9),
    primary = Color(0xFF8B5CF6),
    primaryText = Color(0xFFFFFFFF),
    secondaryBg = Color(0xFF292140),
    accent = Color(0xFFC4B5FD),
    accentTint = Color(0xFF4C1D95),
    link = Color(0xFFA78BFA),
    shadow = Color(0x66000000),
    isDark = true
)

// 6. AMOLED Black (dark): bg #000000, card #0B0B0B, border #1F1F1F, text #FFFFFF, muted #9CA3AF, primary #14B8A6, accent #2DD4BF
val AmoledBlackTokens = PabaruTokens(
    bg = Color(0xFF000000),
    headerBg = Color(0xFF000000),
    headerText = Color(0xFFFFFFFF),
    card = Color(0xFF0B0B0B),
    cardBorder = Color(0xFF1F1F1F),
    text = Color(0xFFFFFFFF),
    textMuted = Color(0xFF9CA3AF),
    primary = Color(0xFF14B8A6),
    primaryText = Color(0xFF000000),
    secondaryBg = Color(0xFF121212),
    accent = Color(0xFF2DD4BF),
    accentTint = Color(0xFF134E4A),
    link = Color(0xFF2DD4BF),
    shadow = Color(0x66000000),
    isDark = true
)

fun getTokensForTheme(theme: AppColorTheme): PabaruTokens {
    return when (theme) {
        AppColorTheme.MidnightTeal -> MidnightTealTokens
        AppColorTheme.ClinicBlue -> ClinicBlueTokens
        AppColorTheme.FreshMint -> FreshMintTokens
        AppColorTheme.WarmSand -> WarmSandTokens
        AppColorTheme.RoyalPurple -> RoyalPurpleTokens
        AppColorTheme.AmoledBlack -> AmoledBlackTokens
    }
}

/**
 * Provides smooth 200ms transitions between colors as requested:
 * "Tapping a card applies the theme instantly with a 200ms color transition"
 */
@Composable
fun rememberAnimatedPabaruTokens(target: PabaruTokens): PabaruTokens {
    val duration = 200
    val bg by animateColorAsState(target.bg, tween(duration), label = "bg")
    val headerBg by animateColorAsState(target.headerBg, tween(duration), label = "headerBg")
    val headerText by animateColorAsState(target.headerText, tween(duration), label = "headerText")
    val card by animateColorAsState(target.card, tween(duration), label = "card")
    val cardBorder by animateColorAsState(target.cardBorder, tween(duration), label = "cardBorder")
    val text by animateColorAsState(target.text, tween(duration), label = "text")
    val textMuted by animateColorAsState(target.textMuted, tween(duration), label = "textMuted")
    val primary by animateColorAsState(target.primary, tween(duration), label = "primary")
    val primaryText by animateColorAsState(target.primaryText, tween(duration), label = "primaryText")
    val secondaryBg by animateColorAsState(target.secondaryBg, tween(duration), label = "secondaryBg")
    val accent by animateColorAsState(target.accent, tween(duration), label = "accent")
    val accentTint by animateColorAsState(target.accentTint, tween(duration), label = "accentTint")
    val link by animateColorAsState(target.link, tween(duration), label = "link")
    val shadow by animateColorAsState(target.shadow, tween(duration), label = "shadow")

    return PabaruTokens(
        bg = bg,
        headerBg = headerBg,
        headerText = headerText,
        card = card,
        cardBorder = cardBorder,
        text = text,
        textMuted = textMuted,
        primary = primary,
        primaryText = primaryText,
        secondaryBg = secondaryBg,
        accent = accent,
        accentTint = accentTint,
        link = link,
        shadow = shadow,
        isDark = target.isDark
    )
}

val LocalPabaruTokens = compositionLocalOf { MidnightTealTokens }

val PabaruTokens.success: Color
    get() = if (isDark) Color(0xFF34D399) else Color(0xFF047857)

val PabaruTokens.successContainer: Color
    get() = if (isDark) Color(0xFF064E3B) else Color(0xFFD1FAE5)

val PabaruTokens.onSuccessContainer: Color
    get() = if (isDark) Color(0xFF6EE7B7) else Color(0xFF065F46)

val PabaruTokens.danger: Color
    get() = if (isDark) Color(0xFFF87171) else Color(0xFFDC2626)

val PabaruTokens.dangerContainer: Color
    get() = if (isDark) Color(0xFF450A0A) else Color(0xFFFEE2E2)

val PabaruTokens.onDangerContainer: Color
    get() = if (isDark) Color(0xFFFCA5A5) else Color(0xFF991B1B)

val PabaruTokens.warning: Color
    get() = if (isDark) Color(0xFFF59E0B) else Color(0xFFB45309)

val PabaruTokens.warningContainer: Color
    get() = if (isDark) Color(0xFF451A03) else Color(0xFFFEF3C7)

val PabaruTokens.onWarningContainer: Color
    get() = if (isDark) Color(0xFFFCD34D) else Color(0xFF92400E)

val PabaruTokens.info: Color
    get() = if (isDark) Color(0xFF38BDF8) else Color(0xFF0284C7)

val PabaruTokens.infoContainer: Color
    get() = if (isDark) Color(0xFF0C4A6E) else Color(0xFFE0F2FE)

val PabaruTokens.onInfoContainer: Color
    get() = if (isDark) Color(0xFF7DD3FC) else Color(0xFF075985)

object PabaruTheme {
    val tokens: PabaruTokens
        @Composable
        get() = LocalPabaruTokens.current
}
