package com.example.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import com.example.ui.theme.PabaruTheme
import com.example.ui.theme.warning

/**
 * A gentle pulsing PRO badge that draws user attention to premium features
 * through smooth scale and border-glow oscillation.
 */
@Composable
fun PulsingProBadge(
    modifier: Modifier = Modifier,
    text: String = "PRO",
    fontSize: TextUnit = 9.sp,
    filled: Boolean = false,
    horizontalPadding: Dp = 4.dp,
    verticalPadding: Dp = 1.dp,
    accentColor: Color = PabaruTheme.tokens.warning,
    darkColor: Color = PabaruTheme.tokens.primaryText
) {
    val infiniteTransition = rememberInfiniteTransition(label = "ProBadgePulse")

    // Gentle breathing scale (1.0x to 1.12x)
    val scale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "BadgeScale"
    )

    // Pulsing border glow alpha
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.45f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "BadgeGlowAlpha"
    )

    val containerColor = if (filled) accentColor else accentColor.copy(alpha = 0.18f)
    val contentColor = if (filled) darkColor else accentColor
    val borderColor = if (filled) darkColor else accentColor.copy(alpha = glowAlpha)

    Surface(
        modifier = modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
        },
        shape = RoundedCornerShape(4.dp),
        color = containerColor,
        border = BorderStroke(0.6.dp, borderColor)
    ) {
        Text(
            text = text,
            fontSize = fontSize,
            fontWeight = FontWeight.Bold,
            color = contentColor,
            modifier = Modifier.padding(horizontal = horizontalPadding, vertical = verticalPadding)
        )
    }
}
