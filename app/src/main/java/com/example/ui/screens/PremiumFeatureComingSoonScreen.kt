package com.example.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.PulsingProBadge
import com.example.ui.theme.PabaruTheme
import com.example.ui.theme.success
import com.example.ui.theme.warning

data class PremiumFeatureHighlight(
    val title: String,
    val description: String,
    val icon: ImageVector
)

enum class PremiumFeatureType {
    GST_FILING_ASSISTANT,
    BANK_RECONCILIATION
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PremiumFeatureComingSoonScreen(
    featureType: PremiumFeatureType,
    onBack: () -> Unit,
    businessName: String = "Pabaru Billing Enterprises",
    onUnlockDemo: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val tokens = PabaruTheme.tokens
    var isJoinedWaitlist by remember { mutableStateOf(false) }

    BackHandler {
        onBack()
    }

    val title = when (featureType) {
        PremiumFeatureType.GST_FILING_ASSISTANT -> "GST Filing Assistant"
        PremiumFeatureType.BANK_RECONCILIATION -> "Automated Bank Reconciliation"
    }

    val subtitle = when (featureType) {
        PremiumFeatureType.GST_FILING_ASSISTANT ->
            "Enterprise statutory tax filing automation for regular and composition taxpayers under the CBIC framework."
        PremiumFeatureType.BANK_RECONCILIATION ->
            "Automated bank statement reconciliation and smart matching engine for cash flow, khata and ledgers."
    }

    val heroIcon = when (featureType) {
        PremiumFeatureType.GST_FILING_ASSISTANT -> Icons.Outlined.FactCheck
        PremiumFeatureType.BANK_RECONCILIATION -> Icons.Outlined.AccountBalance
    }

    val highlights = when (featureType) {
        PremiumFeatureType.GST_FILING_ASSISTANT -> listOf(
            PremiumFeatureHighlight(
                title = "1-Click GSTR-1, GSTR-3B & CMP-08 Auto-Computation",
                description = "Zero manual math. Exact paise precision calculations directly aggregated from sales and purchases.",
                icon = Icons.Outlined.Calculate
            ),
            PremiumFeatureHighlight(
                title = "Statutory Rule 88A Tax Set-Off Optimization",
                description = "Optimizes available IGST, CGST, and SGST input tax credit to minimize cash tax payouts legally.",
                icon = Icons.Outlined.AutoAwesome
            ),
            PremiumFeatureHighlight(
                title = "Direct GST Portal JSON Schema Generator",
                description = "One-tap generation of official GSTR-1 offline tool JSON payloads ready for immediate GSTN upload.",
                icon = Icons.Outlined.FileDownload
            ),
            PremiumFeatureHighlight(
                title = "Audit-Ready HSN & Document Summary Tables",
                description = "Automatic compilation of Table 12 (HSN code breakup) and Table 13 (Invoice serial tracking).",
                icon = Icons.Outlined.Shield
            )
        )
        PremiumFeatureType.BANK_RECONCILIATION -> listOf(
            PremiumFeatureHighlight(
                title = "Multi-Format Bank Statement Parser (CSV & OFX)",
                description = "Seamlessly import e-statements exported from HDFC, SBI, ICICI, Axis, Kotak and all major Indian banks.",
                icon = Icons.Outlined.UploadFile
            ),
            PremiumFeatureHighlight(
                title = "AI & Heuristic Auto-Matching Engine",
                description = "Multi-factor matching combining exact amounts, payee narrations, invoice numbers, and payment modes.",
                icon = Icons.Outlined.AutoFixHigh
            ),
            PremiumFeatureHighlight(
                title = "1-Tap Discrepancy Resolution & Balancing Entries",
                description = "Resolve unrecorded bank SMS charges with 18% GST deduction, customer khata deposits, and vendor payouts.",
                icon = Icons.Outlined.CheckCircle
            ),
            PremiumFeatureHighlight(
                title = "Real-Time Book vs Bank Difference Tracker",
                description = "Keep accounting ledgers and live bank feeds completely synchronized with audit trails.",
                icon = Icons.Outlined.TrendingUp
            )
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(tokens.bg)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 600.dp)
                .align(Alignment.TopCenter)
        ) {
            // Top App Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = tokens.text
                    )
                }

                Text(
                    text = businessName,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = tokens.textMuted
                )

                // PRO Badge
                PulsingProBadge(
                    text = "PRO",
                    fontSize = 11.sp,
                    filled = false,
                    horizontalPadding = 8.dp,
                    verticalPadding = 2.dp,
                    accentColor = tokens.warning
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 40.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Hero Header Card
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = tokens.card,
                    border = BorderStroke(1.dp, tokens.cardBorder)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Glowing Icon Container
                        Surface(
                            shape = CircleShape,
                            color = tokens.warning.copy(alpha = 0.15f),
                            border = BorderStroke(1.dp, tokens.warning.copy(alpha = 0.5f)),
                            modifier = Modifier.size(64.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = heroIcon,
                                    contentDescription = null,
                                    tint = tokens.warning,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }

                        // Coming Soon Pill
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = tokens.warning.copy(alpha = 0.15f),
                            border = BorderStroke(0.5.dp, tokens.warning)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .background(tokens.warning, CircleShape)
                                )
                                Text(
                                    text = "PAID SERVICE · COMING SOON",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = tokens.warning,
                                    letterSpacing = 0.5.sp
                                )
                            }
                        }

                        Text(
                            text = title,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = tokens.text,
                            textAlign = TextAlign.Center
                        )

                        Text(
                            text = subtitle,
                            fontSize = 13.sp,
                            color = tokens.textMuted,
                            textAlign = TextAlign.Center,
                            lineHeight = 19.sp
                        )
                    }
                }

                // Deep-Dive Feature Highlights Section Title
                Text(
                    text = "Feature capabilities included in this module:",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = tokens.text,
                    modifier = Modifier.padding(start = 2.dp, top = 4.dp)
                )

                // Highlight Cards
                highlights.forEach { item ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = tokens.card,
                        border = BorderStroke(0.5.dp, tokens.cardBorder)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = tokens.warning.copy(alpha = 0.12f),
                                modifier = Modifier.size(36.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = item.icon,
                                        contentDescription = null,
                                        tint = tokens.warning,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.title,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = tokens.text
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = item.description,
                                    fontSize = 12.sp,
                                    color = tokens.textMuted,
                                    lineHeight = 17.sp
                                )
                            }
                        }
                    }
                }

                // Priority Waitlist Action Button
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = {
                        isJoinedWaitlist = !isJoinedWaitlist
                        val msg = if (isJoinedWaitlist) {
                            "You're on the priority waitlist for $title! We'll notify you upon release."
                        } else {
                            "Removed from waitlist."
                        }
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("waitlist_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isJoinedWaitlist) tokens.success else tokens.warning
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = if (isJoinedWaitlist) Icons.Outlined.Check else Icons.Outlined.NotificationsActive,
                            contentDescription = null,
                            tint = tokens.primaryText,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = if (isJoinedWaitlist) "On Priority Waitlist" else "Notify Me When Available",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = tokens.primaryText
                        )
                    }
                }

                // Secondary Back Action
                OutlinedButton(
                    onClick = onBack,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("back_to_dashboard_button"),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(0.5.dp, tokens.cardBorder),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = tokens.textMuted
                    )
                ) {
                    Text(
                        text = "Return to Dashboard",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = tokens.textMuted
                    )
                }

                // Optional Demo Preview link for testing / admin preview
                if (onUnlockDemo != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .clickable { onUnlockDemo() }
                                .padding(horizontal = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Preview Feature (Internal Demo)",
                                fontSize = 11.sp,
                                color = tokens.textMuted.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            }
        }
    }
}
