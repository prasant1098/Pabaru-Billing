package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.PulsingProBadge
import com.example.ui.theme.PabaruTheme
import com.example.ui.theme.danger
import com.example.ui.theme.info
import com.example.ui.theme.success
import com.example.ui.theme.warning
import com.example.viewmodel.PabaruUiState

@Composable
fun MoreMenuScreen(
    uiState: PabaruUiState,
    onSelect: (String) -> Unit
) {
    val tokens = PabaruTheme.tokens
    var searchQuery by remember { mutableStateOf("") }

    // Live low-stock count (milli-units + non-archived + user threshold)
    val lowStockCount = remember(uiState.products, uiState.lowStockThreshold) {
        uiState.products.count {
            !it.isArchived && (
                it.currentStockMilli <= uiState.lowStockThreshold * 1000L ||
                    (it.reorderLevelMilli > 0L && it.currentStockMilli <= it.reorderLevelMilli)
                )
        }
    }

    val businessName = uiState.profile.businessName.ifEmpty { "Pabaru Billing Enterprises" }
    val storeCode = uiState.profile.storeCode.ifEmpty { "TL-7319" }
    val entityType = uiState.profile.entityType.displayName.ifEmpty { "Sole proprietorship" }
    val initials = remember(businessName) {
        businessName.split(" ")
            .filter { it.isNotBlank() }
            .take(2)
            .joinToString("") { it.first().uppercaseChar().toString() }
            .ifBlank { "PB" }
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
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(top = 12.dp, bottom = 88.dp) // enough space for bottom nav
            ) {
                // ==========================================
                // HEADER (Title "Menu", 20px, Medium)
                // ==========================================
                item {
                    Text(
                        text = "Menu",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Medium,
                        color = tokens.text,
                        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
                    )
                }

                // ==========================================
                // SLIM PROFILE ROW (64px)
                // ==========================================
                item {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(64.dp)
                            .clickable { onSelect("business_profile") },
                        shape = RoundedCornerShape(12.dp),
                        color = tokens.card,
                        border = BorderStroke(0.5.dp, tokens.cardBorder)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Circular avatar with business initials
                            Surface(
                                modifier = Modifier.size(40.dp),
                                shape = CircleShape,
                                color = tokens.secondaryBg,
                                border = BorderStroke(0.5.dp, tokens.cardBorder)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = initials,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = tokens.text
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = businessName,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = tokens.text,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "$entityType · $storeCode",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Normal,
                                    color = tokens.textMuted,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            // Outlined edit icon button on right
                            IconButton(
                                onClick = { onSelect("business_profile") },
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Edit,
                                    contentDescription = "Edit profile",
                                    tint = tokens.textMuted,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }

                // ==========================================
                // SEARCH FIELD (48px)
                // ==========================================
                item {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = tokens.card,
                        border = BorderStroke(0.5.dp, tokens.cardBorder)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Search,
                                contentDescription = "Search menu",
                                tint = tokens.textMuted,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Box(modifier = Modifier.weight(1f)) {
                                if (searchQuery.isEmpty()) {
                                    Text(
                                        text = "Search menu",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Normal,
                                        color = tokens.textMuted
                                    )
                                }
                                BasicTextField(
                                    value = searchQuery,
                                    onValueChange = { searchQuery = it },
                                    singleLine = true,
                                    cursorBrush = SolidColor(tokens.primary),
                                    textStyle = androidx.compose.ui.text.TextStyle(
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Normal,
                                        color = tokens.text
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            if (searchQuery.isNotEmpty()) {
                                IconButton(
                                    onClick = { searchQuery = "" },
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Close,
                                        contentDescription = "Clear",
                                        tint = tokens.textMuted,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // ==========================================
                // SECTION 1 (FIRST): INVENTORY AND STOCK
                // ==========================================
                val q = searchQuery.trim().lowercase()
                val showSection1 = q.isEmpty() ||
                    "inventory".contains(q) || "stock".contains(q) || "items".contains(q) ||
                    "adjustment".contains(q) || "purchases".contains(q) || "products".contains(q)

                if (showSection1) {
                    item {
                        Text(
                            text = "Inventory and stock",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = tokens.textMuted,
                            modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
                        )
                    }

                    // Featured Card (72px tall): Amber icon, "Inventory and stock", "1 low" badge
                    item {
                        FeaturedMenuCard(
                            title = "Inventory and stock",
                            description = "Items, quantities, and reorder alerts",
                            icon = Icons.Outlined.Inventory2,
                            iconTint = tokens.warning,
                            badgeText = if (lowStockCount > 0) "$lowStockCount low" else null,
                            onClick = { onSelect("stock") }
                        )
                    }

                    // 2-Column Grid (Row 1): Items, Stock adjustment
                    item {
                        TilePairRow(
                            leftTile = {
                                MenuTile(
                                    title = "Items",
                                    description = "Add and edit products",
                                    icon = Icons.Outlined.Category,
                                    iconTint = tokens.warning,
                                    onClick = { onSelect("stock") }
                                )
                            },
                            rightTile = {
                                MenuTile(
                                    title = "Stock adjustment",
                                    description = "Correct quantities",
                                    icon = Icons.Outlined.Tune,
                                    iconTint = tokens.warning,
                                    onClick = { onSelect("stock") }
                                )
                            }
                        )
                    }

                    // 2-Column Grid (Row 2): Low stock (red dot), Purchases
                    item {
                        TilePairRow(
                            leftTile = {
                                MenuTile(
                                    title = "Low stock",
                                    description = "Below your alert level",
                                    icon = Icons.Outlined.WarningAmber,
                                    iconTint = tokens.warning,
                                    hasRedDot = lowStockCount > 0,
                                    onClick = { onSelect("stock") }
                                )
                            },
                            rightTile = {
                                MenuTile(
                                    title = "Purchases",
                                    description = "Stock in from suppliers",
                                    icon = Icons.Outlined.ShoppingBag,
                                    iconTint = tokens.warning,
                                    onClick = { onSelect("ledgers") }
                                )
                            }
                        )
                    }
                }

                // ==========================================
                // SECTION 2: BILLING AND CASH FLOW
                // ==========================================
                val showSection2 = q.isEmpty() ||
                    "billing".contains(q) || "cash".contains(q) || "pos".contains(q) ||
                    "invoices".contains(q) || "ledgers".contains(q) || "bank".contains(q) || "daybook".contains(q)

                if (showSection2) {
                    item {
                        Text(
                            text = "Billing and cash flow",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = tokens.textMuted,
                            modifier = Modifier.padding(top = 6.dp, bottom = 2.dp)
                        )
                    }

                    // Featured Card (72px tall): Green receipt icon, "Billing and invoices"
                    item {
                        FeaturedMenuCard(
                            title = "Billing and invoices",
                            description = "Tax invoices, non-GST bills and estimates",
                            icon = Icons.Outlined.Receipt,
                            iconTint = tokens.success,
                            onClick = { onSelect("invoices") }
                        )
                    }

                    // 2-Column Grid (Row 1): Invoices, Ledgers
                    item {
                        TilePairRow(
                            leftTile = {
                                MenuTile(
                                    title = "Invoices",
                                    description = "Bills, notes, quotations",
                                    icon = Icons.Outlined.Receipt,
                                    iconTint = tokens.success,
                                    onClick = { onSelect("invoices") }
                                )
                            },
                            rightTile = {
                                MenuTile(
                                    title = "Ledgers",
                                    description = "Customers and suppliers",
                                    icon = Icons.Outlined.People,
                                    iconTint = tokens.success,
                                    onClick = { onSelect("ledgers") }
                                )
                            }
                        )
                    }

                    // 2-Column Grid (Row 2): Cash and bank, Daybook
                    item {
                        TilePairRow(
                            leftTile = {
                                MenuTile(
                                    title = "Cash and bank",
                                    description = "Balances and reconciliation",
                                    icon = Icons.Outlined.AccountBalance,
                                    iconTint = tokens.success,
                                    onClick = { onSelect("cash_bank") }
                                )
                            },
                            rightTile = {
                                MenuTile(
                                    title = "Daybook",
                                    description = "Daily audited entries",
                                    icon = Icons.Outlined.CalendarToday,
                                    iconTint = tokens.success,
                                    onClick = { onSelect("daybook") }
                                )
                            }
                        )
                    }
                }

                // ==========================================
                // SECTION 3: REPORTS
                // ==========================================
                val showSection3 = q.isEmpty() ||
                    "reports".contains(q) || "profit".contains(q) || "loss".contains(q) ||
                    "balance".contains(q) || "gst".contains(q) || "cma".contains(q) || "statement".contains(q)

                if (showSection3) {
                    item {
                        Text(
                            text = "Reports",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = tokens.textMuted,
                            modifier = Modifier.padding(top = 6.dp, bottom = 2.dp)
                        )
                    }

                    // 2-Column Grid in Blue: Profit and loss, Balance sheet
                    item {
                        TilePairRow(
                            leftTile = {
                                MenuTile(
                                    title = "Profit and loss",
                                    description = "Gross and net profit",
                                    icon = Icons.Outlined.TrendingUp,
                                    iconTint = tokens.info,
                                    onClick = { onSelect("pl") }
                                )
                            },
                            rightTile = {
                                MenuTile(
                                    title = "Balance sheet",
                                    description = "Assets and liabilities",
                                    icon = Icons.Outlined.AccountBalanceWallet,
                                    iconTint = tokens.info,
                                    onClick = { onSelect("balance_sheet") }
                                )
                            }
                        )
                    }

                    // 2-Column Grid in Blue: Inventory valuation & Reports summary
                    item {
                        TilePairRow(
                            leftTile = {
                                MenuTile(
                                    title = "Stock valuation",
                                    description = "Current stock value & assets",
                                    icon = Icons.Outlined.Inventory2,
                                    iconTint = tokens.warning,
                                    onClick = { onSelect("stock") }
                                )
                            },
                            rightTile = {
                                MenuTile(
                                    title = "FY Reports & GST",
                                    description = "P&L, Balance Sheet, GSTR & PDF/CSV",
                                    icon = Icons.Outlined.BarChart,
                                    iconTint = tokens.info,
                                    onClick = { onSelect("reports_analytics") }
                                )
                            }
                        )
                    }

                    // 2-Column Grid in Blue: CMA report, Bank statements
                    item {
                        TilePairRow(
                            leftTile = {
                                MenuTile(
                                    title = "CMA report",
                                    description = "Working capital assessment",
                                    icon = Icons.Outlined.Assessment,
                                    iconTint = tokens.info,
                                    onClick = { onSelect("cma_project") }
                                )
                            },
                            rightTile = {
                                MenuTile(
                                    title = "Bank statements",
                                    description = "Import CSV and recon",
                                    icon = Icons.Outlined.FileDownload,
                                    iconTint = tokens.info,
                                    onClick = { onSelect("statement_imports") }
                                )
                            }
                        )
                    }

                    // GSTR-2B Automated ITC Reconciliation
                    item {
                        FeaturedMenuCard(
                            title = "GSTR-2B Reconciliation",
                            description = "Automated ITC verification & Rule 36(4) discrepancy audit",
                            icon = Icons.Outlined.FactCheck,
                            iconTint = tokens.info,
                            onClick = { onSelect("gstr2b_recon") }
                        )
                    }
                }

                // ==========================================
                // SECTION 4: SETTINGS AND MORE
                // ==========================================
                val showSection4 = q.isEmpty() ||
                    "settings".contains(q) || "backup".contains(q) || "sync".contains(q) ||
                    "staff".contains(q) || "audit".contains(q) || "help".contains(q) || "log out".contains(q)

                if (showSection4) {
                    item {
                        Text(
                            text = "Settings and more",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = tokens.textMuted,
                            modifier = Modifier.padding(top = 6.dp, bottom = 2.dp)
                        )
                    }

                    // 2-Column Grid in Muted: Backup and sync, Settings
                    item {
                        TilePairRow(
                            leftTile = {
                                MenuTile(
                                    title = "Backup and sync",
                                    description = "Encrypted JSON export & restore",
                                    icon = Icons.Outlined.CloudSync,
                                    iconTint = tokens.textMuted,
                                    hasGreenDot = true,
                                    onClick = { onSelect("backup_json") }
                                )
                            },
                            rightTile = {
                                MenuTile(
                                    title = "Settings",
                                    description = "Themes and counter security",
                                    icon = Icons.Outlined.Settings,
                                    iconTint = tokens.textMuted,
                                    onClick = { onSelect("color_theme") }
                                )
                            }
                        )
                    }

                    // 2-Column Grid in Muted: Audit trail, Help
                    item {
                        TilePairRow(
                            leftTile = {
                                MenuTile(
                                    title = "Audit trail",
                                    description = "SHA-256 tamper-evident log",
                                    icon = Icons.Outlined.Shield,
                                    iconTint = tokens.textMuted,
                                    onClick = { onSelect("security") }
                                )
                            },
                            rightTile = {
                                MenuTile(
                                    title = "Help",
                                    description = "Guides and documentation",
                                    icon = Icons.Outlined.HelpOutline,
                                    iconTint = tokens.textMuted,
                                    onClick = { onSelect("desktop") }
                                )
                            }
                        )
                    }

                    // Log out
                    item {
                        MenuTile(
                            title = "Log out",
                            description = "Sign out of counter session",
                            icon = Icons.Outlined.Logout,
                            iconTint = tokens.danger,
                            isDestructive = true,
                            onClick = { onSelect("sign_out") }
                        )
                    }
                }
            }
        }
    }
}

// ==========================================
// MENU TILE (2-Column Grid Item)
// ==========================================
@Composable
fun MenuTile(
    title: String,
    description: String,
    icon: ImageVector,
    iconTint: Color,
    onClick: () -> Unit,
    hasRedDot: Boolean = false,
    hasGreenDot: Boolean = false,
    isDestructive: Boolean = false,
    badgeText: String? = null,
    badgeColor: Color = PabaruTheme.tokens.warning,
    modifier: Modifier = Modifier
) {
    val tokens = PabaruTheme.tokens
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(72.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = tokens.card,
        border = BorderStroke(0.5.dp, tokens.cardBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isDestructive) tokens.danger else iconTint,
                    modifier = Modifier.size(20.dp)
                )

                if (badgeText != null) {
                    if (badgeText == "PRO") {
                        PulsingProBadge(
                            text = "PRO",
                            fontSize = 9.sp,
                            filled = false,
                            accentColor = badgeColor
                        )
                    } else {
                        Surface(
                            color = badgeColor.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(4.dp),
                            border = BorderStroke(0.5.dp, badgeColor)
                        ) {
                            Text(
                                text = badgeText,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = badgeColor,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                } else if (hasRedDot) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(tokens.danger)
                    )
                } else if (hasGreenDot) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(tokens.success)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Column {
                Text(
                    text = title,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (isDestructive) tokens.danger else tokens.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = description,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Normal,
                    color = if (isDestructive) tokens.danger.copy(alpha = 0.8f) else tokens.textMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

// 2-Column Pair Row helper
@Composable
fun TilePairRow(
    leftTile: @Composable () -> Unit,
    rightTile: @Composable () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(modifier = Modifier.weight(1f)) { leftTile() }
        Box(modifier = Modifier.weight(1f)) { rightTile() }
    }
}

// ==========================================
// FEATURED MENU CARD (Full-Width Item)
// ==========================================
@Composable
fun FeaturedMenuCard(
    title: String,
    description: String,
    icon: ImageVector,
    iconTint: Color,
    onClick: () -> Unit,
    badgeText: String? = null,
    badgeColor: Color = PabaruTheme.tokens.danger,
    modifier: Modifier = Modifier
) {
    val tokens = PabaruTheme.tokens
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(72.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = tokens.card,
        border = BorderStroke(0.5.dp, tokens.cardBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(40.dp),
                shape = RoundedCornerShape(10.dp),
                color = iconTint.copy(alpha = 0.15f),
                border = BorderStroke(0.5.dp, iconTint.copy(alpha = 0.4f))
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = tokens.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = description,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Normal,
                    color = tokens.textMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (badgeText != null) {
                Surface(
                    color = badgeColor.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(0.5.dp, badgeColor)
                ) {
                    Text(
                        text = badgeText,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = badgeColor,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
            }

            Icon(
                imageVector = Icons.Outlined.ChevronRight,
                contentDescription = null,
                tint = tokens.textMuted,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
