package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AppColorTheme
import com.example.ui.theme.PabaruTheme
import com.example.ui.theme.getTokensForTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemePickerBottomSheet(
    currentThemeMode: String,
    activeTheme: AppColorTheme,
    onSelectThemeMode: (String) -> Unit,
    onDismissRequest: () -> Unit
) {
    val tokens = PabaruTheme.tokens

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        containerColor = tokens.card,
        contentColor = tokens.text,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        dragHandle = {
            BottomSheetDefaults.DragHandle(
                color = tokens.cardBorder
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .navigationBarsPadding()
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = tokens.accentTint,
                    shape = CircleShape,
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.Palette,
                            contentDescription = null,
                            tint = tokens.accent,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        "Choose Theme",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = tokens.text
                    )
                    Text(
                        "WCAG AA compliant palettes with 200ms transitions",
                        fontSize = 11.sp,
                        color = tokens.textMuted
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 1. Auto (follow device) option
            val isAutoActive = currentThemeMode == "auto"
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        onSelectThemeMode("auto")
                        onDismissRequest()
                    },
                shape = RoundedCornerShape(12.dp),
                color = if (isAutoActive) tokens.accentTint.copy(alpha = 0.5f) else tokens.secondaryBg,
                border = BorderStroke(
                    width = if (isAutoActive) 2.dp else 1.dp,
                    color = if (isAutoActive) tokens.accent else tokens.cardBorder
                )
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            Icons.Default.BrightnessAuto,
                            contentDescription = null,
                            tint = if (isAutoActive) tokens.accent else tokens.textMuted,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "Auto (follow device)",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = tokens.text
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    color = tokens.accent.copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        "DEFAULT",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Black,
                                        color = tokens.accent,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                    )
                                }
                            }
                            Text(
                                "Switches between Clinic Blue (light) and Midnight Teal (dark)",
                                fontSize = 11.sp,
                                color = tokens.textMuted
                            )
                        }
                    }

                    if (isAutoActive) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = "Active",
                            tint = tokens.accent,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                "PRESET THEMES",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = tokens.textMuted
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 2-column grid of preview cards (~100px high)
            val themes = AppColorTheme.values()
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                for (row in 0 until (themes.size + 1) / 2) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        val firstIndex = row * 2
                        val secondIndex = firstIndex + 1

                        ThemePreviewCard(
                            theme = themes[firstIndex],
                            isActive = (currentThemeMode == themes[firstIndex].id) || (isAutoActive && activeTheme == themes[firstIndex]),
                            modifier = Modifier.weight(1f),
                            onClick = {
                                onSelectThemeMode(themes[firstIndex].id)
                                onDismissRequest()
                            }
                        )

                        if (secondIndex < themes.size) {
                            ThemePreviewCard(
                                theme = themes[secondIndex],
                                isActive = (currentThemeMode == themes[secondIndex].id) || (isAutoActive && activeTheme == themes[secondIndex]),
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    onSelectThemeMode(themes[secondIndex].id)
                                    onDismissRequest()
                                }
                            )
                        } else {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))
        }
    }
}

/**
 * Preview card (~100px high):
 * A mini mockup showing the header color, card color, button color, the theme name below,
 * and a check mark on the active one.
 */
@Composable
fun ThemePreviewCard(
    theme: AppColorTheme,
    isActive: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val themeTokens = getTokensForTheme(theme)
    val appTokens = PabaruTheme.tokens

    Surface(
        modifier = modifier
            .height(102.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        color = if (isActive) appTokens.accentTint.copy(alpha = 0.25f) else appTokens.card,
        border = BorderStroke(
            width = if (isActive) 2.dp else 1.dp,
            color = if (isActive) appTokens.accent else appTokens.cardBorder
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(6.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Mini Mockup (showing header color, card color and button color)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(themeTokens.bg)
                    .border(0.5.dp, themeTokens.cardBorder, RoundedCornerShape(6.dp))
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Mini Header
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(18.dp)
                            .background(themeTokens.headerBg)
                            .padding(horizontal = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Box(
                            modifier = Modifier
                                .width(28.dp)
                                .height(4.dp)
                                .background(themeTokens.headerText.copy(alpha = 0.8f), RoundedCornerShape(2.dp))
                        )
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(themeTokens.accent, CircleShape)
                        )
                    }

                    // Mini Body with Card & Button
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(4.dp)
                            .background(themeTokens.card, RoundedCornerShape(4.dp))
                            .border(0.5.dp, themeTokens.cardBorder, RoundedCornerShape(4.dp))
                            .padding(4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(24.dp)
                                    .height(4.dp)
                                    .background(themeTokens.text.copy(alpha = 0.7f), RoundedCornerShape(2.dp))
                            )
                            // Mini Button
                            Box(
                                modifier = Modifier
                                    .width(22.dp)
                                    .height(12.dp)
                                    .background(themeTokens.primary, RoundedCornerShape(3.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .width(10.dp)
                                        .height(2.dp)
                                        .background(themeTokens.primaryText, RoundedCornerShape(1.dp))
                                )
                            }
                        }
                    }
                }
            }

            // Theme Name & Active Checkmark
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 2.dp, vertical = 1.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = theme.displayName,
                    fontSize = 11.sp,
                    fontWeight = if (isActive) FontWeight.ExtraBold else FontWeight.Medium,
                    color = if (isActive) appTokens.accent else appTokens.text,
                    maxLines = 1
                )

                if (isActive) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Active",
                        tint = appTokens.accent,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}
