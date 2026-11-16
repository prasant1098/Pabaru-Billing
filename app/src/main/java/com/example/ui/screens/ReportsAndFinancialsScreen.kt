package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import java.util.Locale
import com.example.engine.CmaAndGstEngine
import com.example.engine.ExportEngine
import com.example.engine.FinancialEngine
import com.example.model.*
import com.example.ui.theme.PabaruTheme
import com.example.ui.theme.danger
import com.example.ui.theme.dangerContainer
import com.example.ui.theme.infoContainer
import com.example.ui.theme.onDangerContainer
import com.example.ui.theme.onSuccessContainer
import com.example.ui.theme.success
import com.example.ui.theme.successContainer
import com.example.viewmodel.PabaruUiState

@Composable
fun ReportsAndFinancialsScreen(
    uiState: PabaruUiState,
    initialTab: Int = 0, // 0: PL, 1: Balance Sheet, 2: GST Filing, 3: Annual GSTR-9, 4: CMA Dossier
    onBack: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val tokens = PabaruTheme.tokens
    var selectedTab by remember { mutableIntStateOf(initialTab) }
    var showFullCmaReport by remember { mutableStateOf(false) }
    var showGstr2bRecon by remember { mutableStateOf(false) }

    // Available Financial Years
    val availableFys = remember(uiState.transactions) {
        FinancialEngine.getAvailableFinancialYears(uiState.transactions)
    }
    var selectedFy by remember {
        mutableStateOf(availableFys.firstOrNull() ?: FinancialEngine.getCurrentFinancialYear())
    }
    LaunchedEffect(availableFys) {
        if (selectedFy !in availableFys) {
            selectedFy = availableFys.firstOrNull() ?: FinancialEngine.getCurrentFinancialYear()
        }
    }

    // Manual Income Tax Provision input state (in ₹)
    var taxInputText by remember(selectedFy) { mutableStateOf("") }
    val customTaxPaise = remember(taxInputText) {
        FinancialEngine.parsePaiseFromRupees(taxInputText)
    }

    // Computed FY Statements
    val finStatement = remember(uiState, selectedFy, customTaxPaise) {
        FinancialEngine.calculateFyFinancials(uiState, selectedFy, customTaxPaise)
    }
    val gstSummary = remember(uiState.transactions, uiState.parties, selectedFy) {
        FinancialEngine.calculateFyGstSummary(uiState.transactions, uiState.parties, selectedFy)
    }
    val gstr9Summary = remember(uiState.transactions, uiState.parties, selectedFy) {
        FinancialEngine.calculateGstr9AnnualSummary(uiState.transactions, uiState.parties, selectedFy)
    }

    BackHandler {
        when {
            showFullCmaReport -> showFullCmaReport = false
            showGstr2bRecon -> showGstr2bRecon = false
            else -> onBack?.invoke()
        }
    }

    if (showFullCmaReport) {
        CmaProjectReportScreen(
            profile = uiState.profile,
            transactions = uiState.transactions,
            parties = uiState.parties,
            products = uiState.products,
            cashAndBankPaise = finStatement.cashInHandPaise + finStatement.bankTotalPaise,
            onBack = { showFullCmaReport = false }
        )
        return
    }

    if (showGstr2bRecon) {
        Gstr2bReconciliationScreen(
            profile = uiState.profile,
            transactions = uiState.transactions,
            parties = uiState.parties,
            onBack = { showGstr2bRecon = false }
        )
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(tokens.bg)
    ) {
        // TOP APP BAR
        Surface(
            color = tokens.headerBg,
            border = BorderStroke(0.5.dp, tokens.cardBorder)
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        if (onBack != null) {
                            IconButton(
                                onClick = onBack,
                                modifier = Modifier
                                    .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                                    .testTag("reports_back_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back",
                                    tint = tokens.headerText
                                )
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                        }
                        Column {
                            Text(
                                text = "Financial Reports & GST",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = tokens.headerText
                            )
                            Text(
                                text = "${uiState.profile.businessName.ifEmpty { "My Business" }} · GSTIN ${uiState.profile.gstin.ifEmpty { "N/A" }}",
                                fontSize = 11.sp,
                                color = tokens.headerText.copy(alpha = 0.8f)
                            )
                        }
                    }

                    // Action buttons (Export PDF / CSV)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilledTonalButton(
                            onClick = {
                                if (selectedTab == 3) {
                                    ExportEngine.generateAndShareGstr9ReportCsv(
                                        context = context,
                                        profile = uiState.profile,
                                        fyLabel = selectedFy,
                                        gstr9 = gstr9Summary
                                    )
                                } else {
                                    ExportEngine.generateAndShareFinancialReportCsv(
                                        context = context,
                                        profile = uiState.profile,
                                        fyLabel = selectedFy,
                                        statement = finStatement,
                                        gstSummary = gstSummary
                                    )
                                }
                            },
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("btn_export_csv_report"),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.TableChart, contentDescription = "CSV", modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("CSV", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = {
                                if (selectedTab == 3) {
                                    ExportEngine.generateAndShareGstr9ReportPdf(
                                        context = context,
                                        profile = uiState.profile,
                                        fyLabel = selectedFy,
                                        gstr9 = gstr9Summary
                                    )
                                } else {
                                    ExportEngine.generateAndShareFinancialReportPdf(
                                        context = context,
                                        profile = uiState.profile,
                                        fyLabel = selectedFy,
                                        statement = finStatement,
                                        gstSummary = gstSummary
                                    )
                                }
                            },
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("btn_export_pdf_report"),
                            colors = ButtonDefaults.buttonColors(containerColor = tokens.primary),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.PictureAsPdf, contentDescription = "PDF", tint = tokens.primaryText, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("PDF", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = tokens.primaryText)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // FINANCIAL YEAR SELECTOR PILLS
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.DateRange,
                        contentDescription = "FY",
                        tint = tokens.headerText,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "Period:",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = tokens.headerText.copy(alpha = 0.85f)
                    )
                    availableFys.forEach { fy ->
                        val isSelected = (fy == selectedFy)
                        Surface(
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { selectedFy = fy }
                                .testTag("fy_pill_${fy.replace(" ", "_")}"),
                            color = if (isSelected) tokens.primary else tokens.card,
                            border = BorderStroke(1.dp, if (isSelected) tokens.primary else tokens.cardBorder),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    text = fy,
                                    fontSize = 11.5.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) tokens.primaryText else tokens.text
                                )
                            }
                        }
                    }
                }
            }
        }

        // TABS: P&L | Balance Sheet | GST Filing | Annual GSTR-9 | CMA Dossier | GSTR-2B Recon
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf("Profit & Loss", "Balance Sheet", "GST Filing", "Annual GSTR-9", "CMA Dossier", "GSTR-2B Recon").forEachIndexed { idx, title ->
                val isSelected = (selectedTab == idx)
                Surface(
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { selectedTab = idx }
                        .testTag("report_tab_$idx"),
                    color = if (isSelected) tokens.primary else tokens.secondaryBg,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(0.5.dp, if (isSelected) tokens.primary else tokens.cardBorder)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        Text(
                            text = title,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) tokens.primaryText else tokens.text,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            maxLines = 1
                        )
                    }
                }
            }
        }

        // CONTENT BODY
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 4.dp)
                .testTag("reports_lazy_column"),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            when (selectedTab) {
                0 -> {
                    // TAB 0: MULTI-STEP PROFIT & LOSS STATEMENT
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = tokens.card),
                            border = BorderStroke(1.dp, tokens.cardBorder),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = "Multi-Step Profit & Loss Statement",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp,
                                            color = tokens.text
                                        )
                                        Text(
                                            text = "Classified operational, non-operating & tax statement",
                                            fontSize = 11.sp,
                                            color = tokens.textMuted
                                        )
                                    }
                                    Surface(
                                        color = tokens.accentTint,
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = selectedFy,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = tokens.accent,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                        )
                                    }
                                }

                                HorizontalDivider(color = tokens.cardBorder)

                                // Quick 3-KPI bar
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Surface(
                                        modifier = Modifier.weight(1f),
                                        color = tokens.secondaryBg,
                                        shape = RoundedCornerShape(8.dp),
                                        border = BorderStroke(0.5.dp, tokens.cardBorder)
                                    ) {
                                        Column(modifier = Modifier.padding(8.dp)) {
                                            Text("Net Sales", fontSize = 10.sp, color = tokens.textMuted)
                                            Text(
                                                FinancialEngine.formatPaise(finStatement.netSalesPaise),
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = tokens.text
                                            )
                                        }
                                    }
                                    Surface(
                                        modifier = Modifier.weight(1f),
                                        color = tokens.secondaryBg,
                                        shape = RoundedCornerShape(8.dp),
                                        border = BorderStroke(0.5.dp, tokens.cardBorder)
                                    ) {
                                        Column(modifier = Modifier.padding(8.dp)) {
                                            Text("Operating Profit", fontSize = 10.sp, color = tokens.textMuted)
                                            Text(
                                                FinancialEngine.formatPaise(finStatement.operatingProfitPaise),
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (finStatement.operatingProfitPaise >= 0L) tokens.accent else tokens.danger
                                            )
                                        }
                                    }
                                    Surface(
                                        modifier = Modifier.weight(1f),
                                        color = tokens.secondaryBg,
                                        shape = RoundedCornerShape(8.dp),
                                        border = BorderStroke(0.5.dp, tokens.cardBorder)
                                    ) {
                                        Column(modifier = Modifier.padding(8.dp)) {
                                            Text("Net Profit (PAT)", fontSize = 10.sp, color = tokens.textMuted)
                                            Text(
                                                FinancialEngine.formatPaise(finStatement.netProfitPaise),
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (finStatement.netProfitPaise >= 0L) tokens.success else tokens.danger
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // STEP 1: REVENUE & NET SALES CARD
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = tokens.card),
                            border = BorderStroke(1.dp, tokens.cardBorder),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.testTag("pl_net_sales_card")
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "1. Revenue & Net Sales",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.5.sp,
                                        color = tokens.text
                                    )
                                    Text(
                                        text = "Gross Sales less Deductions",
                                        fontSize = 10.5.sp,
                                        color = tokens.textMuted
                                    )
                                }

                                HorizontalDivider(color = tokens.cardBorder)

                                ReportRow("Gross Sales / Invoiced Turnover", FinancialEngine.formatPaise(finStatement.grossSalesPaise), tokens.text)
                                ReportRow("Less: Sales Returns & Credit Notes", "- " + FinancialEngine.formatPaise(finStatement.salesReturnsPaise), tokens.danger)
                                ReportRow("Less: Trade Discounts & Allowances", "- " + FinancialEngine.formatPaise(finStatement.salesDiscountsPaise), tokens.danger)

                                HorizontalDivider(color = tokens.cardBorder, thickness = 1.dp)

                                ReportRow(
                                    label = "NET SALES REVENUE",
                                    amount = FinancialEngine.formatPaise(finStatement.netSalesPaise),
                                    amountColor = tokens.text,
                                    isBold = true,
                                    isHighlight = true
                                )
                            }
                        }
                    }

                    // STEP 2: COST OF GOODS SOLD (COGS) & GROSS PROFIT CARD
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = tokens.card),
                            border = BorderStroke(1.dp, tokens.cardBorder),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.testTag("pl_gross_profit_card")
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "2. Cost of Goods Sold & Gross Profit",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.5.sp,
                                        color = tokens.text
                                    )
                                    Surface(
                                        color = tokens.accentTint,
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            text = "Margin ${String.format(Locale.ENGLISH, "%.1f%%", finStatement.grossProfitMarginPercent)}",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = tokens.accent,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }

                                HorizontalDivider(color = tokens.cardBorder)

                                ReportRow("Purchases & Raw Materials", FinancialEngine.formatPaise(finStatement.purchasesCogsPaise), tokens.text)
                                ReportRow("Direct Manufacturing & Freight Inward", FinancialEngine.formatPaise(finStatement.directExpensesPaise), tokens.text)
                                ReportRow("Cost of Goods Sold (COGS)", "- " + FinancialEngine.formatPaise(finStatement.totalCogsPaise), tokens.danger)

                                HorizontalDivider(color = tokens.cardBorder, thickness = 1.dp)

                                ReportRow(
                                    label = "GROSS PROFIT / (GROSS LOSS)",
                                    amount = FinancialEngine.formatPaise(finStatement.grossProfitPaise),
                                    amountColor = if (finStatement.grossProfitPaise >= 0L) tokens.accent else tokens.danger,
                                    isBold = true,
                                    isHighlight = true
                                )
                            }
                        }
                    }

                    // STEP 3: OPERATING EXPENSES & OPERATING INCOME CARD
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = tokens.card),
                            border = BorderStroke(1.dp, tokens.cardBorder),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.testTag("pl_operating_income_card")
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "3. Operating Expenses & Operating Income",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.5.sp,
                                        color = tokens.text
                                    )
                                    Surface(
                                        color = tokens.accentTint,
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            text = "Op Margin ${String.format(Locale.ENGLISH, "%.1f%%", finStatement.operatingProfitMarginPercent)}",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = tokens.accent,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }

                                HorizontalDivider(color = tokens.cardBorder)

                                ReportRow("Administrative, Office & Utilities", FinancialEngine.formatPaise(finStatement.operatingExpensesAdminPaise), tokens.textMuted)
                                ReportRow("Employee Salaries & Wages", FinancialEngine.formatPaise(finStatement.operatingExpensesSalariesPaise), tokens.textMuted)
                                ReportRow("Marketing, Sales & Delivery Costs", FinancialEngine.formatPaise(finStatement.operatingExpensesMarketingPaise), tokens.textMuted)
                                ReportRow("Depreciation & Amortization", FinancialEngine.formatPaise(finStatement.operatingExpensesDepreciationPaise), tokens.textMuted)
                                ReportRow("Total Operating Expenses", "- " + FinancialEngine.formatPaise(finStatement.totalOperatingExpensesPaise), tokens.danger)

                                HorizontalDivider(color = tokens.cardBorder, thickness = 1.dp)

                                ReportRow(
                                    label = "OPERATING INCOME / PROFIT (EBIT)",
                                    amount = FinancialEngine.formatPaise(finStatement.operatingProfitPaise),
                                    amountColor = if (finStatement.operatingProfitPaise >= 0L) tokens.accent else tokens.danger,
                                    isBold = true,
                                    isHighlight = true
                                )
                            }
                        }
                    }

                    // STEP 4: NON-OPERATING ITEMS & EARNINGS BEFORE TAX (EBT) CARD
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = tokens.card),
                            border = BorderStroke(1.dp, tokens.cardBorder),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.testTag("pl_ebt_card")
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "4. Non-Operating Items & Earnings Before Tax (EBT)",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.5.sp,
                                    color = tokens.text
                                )
                                HorizontalDivider(color = tokens.cardBorder)

                                ReportRow("Add: Non-Operating / Interest Income", "+ " + FinancialEngine.formatPaise(finStatement.nonOperatingIncomePaise), tokens.success)
                                ReportRow("Less: Finance Charges & Loan Interest", "- " + FinancialEngine.formatPaise(finStatement.nonOperatingExpensesPaise), tokens.danger)

                                HorizontalDivider(color = tokens.cardBorder, thickness = 1.dp)

                                ReportRow(
                                    label = "EARNINGS BEFORE TAX (EBT)",
                                    amount = FinancialEngine.formatPaise(finStatement.earningsBeforeTaxEbtPaise),
                                    amountColor = if (finStatement.earningsBeforeTaxEbtPaise >= 0L) tokens.text else tokens.danger,
                                    isBold = true,
                                    isHighlight = true
                                )
                            }
                        }
                    }

                    // STEP 5: INCOME TAX PROVISION & FINAL NET PROFIT CARD
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = tokens.card),
                            border = BorderStroke(1.dp, tokens.cardBorder),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.testTag("pl_net_profit_card")
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "5. Income Tax & Final Net Profit",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.5.sp,
                                        color = tokens.text
                                    )
                                    Surface(
                                        color = if (finStatement.netProfitPaise >= 0L) tokens.successContainer else tokens.dangerContainer,
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            text = "PAT Margin ${String.format(Locale.ENGLISH, "%.1f%%", finStatement.netProfitMarginPercent)}",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (finStatement.netProfitPaise >= 0L) tokens.onSuccessContainer else tokens.onDangerContainer,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }

                                Text(
                                    text = "Estimated corporate / income tax liability on Earnings Before Tax (EBT):",
                                    fontSize = 11.sp,
                                    color = tokens.textMuted
                                )

                                OutlinedTextField(
                                    value = taxInputText,
                                    onValueChange = { taxInputText = FinancialEngine.sanitizeDecimalInput(it) },
                                    label = { Text("Income Tax Provision (₹)") },
                                    placeholder = { Text("Enter exact tax e.g. 5000") },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("input_income_tax")
                                )

                                ReportRow("Less: Income Tax Expense", "- " + FinancialEngine.formatPaise(finStatement.incomeTaxProvisionPaise), tokens.danger)

                                HorizontalDivider(color = tokens.cardBorder, thickness = 1.5.dp)

                                ReportRow(
                                    label = "NET PROFIT / (NET LOSS) FOR PERIOD",
                                    amount = FinancialEngine.formatPaise(finStatement.netProfitPaise),
                                    amountColor = if (finStatement.netProfitPaise >= 0L) tokens.success else tokens.danger,
                                    isBold = true,
                                    isHighlight = true
                                )

                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "* Drawings of ${FinancialEngine.formatPaise(finStatement.drawingsPaise)} are isolated from P&L and deducted directly from Proprietor Capital in the Balance Sheet.",
                                    fontSize = 10.sp,
                                    color = tokens.textMuted
                                )
                            }
                        }
                    }
                }

                1 -> {
                    // TAB 1: BALANCE SHEET STATEMENT
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = tokens.card),
                            border = BorderStroke(1.dp, tokens.cardBorder),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Balance Sheet Statement",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = tokens.text
                                    )
                                    Surface(
                                        color = tokens.successContainer,
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = "Cut-off: $selectedFy",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = tokens.onSuccessContainer,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                        )
                                    }
                                }

                                HorizontalDivider(color = tokens.cardBorder)

                                val gstInputCreditAssetPaise = if (finStatement.netGstPayablePaise < 0L) -finStatement.netGstPayablePaise else 0L
                                val gstPayableLiabilityPaise = if (finStatement.netGstPayablePaise > 0L) finStatement.netGstPayablePaise else 0L

                                // ASSETS SECTION
                                Text(
                                    text = "CURRENT & FIXED ASSETS",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.5.sp,
                                    color = tokens.accent
                                )
                                ReportRow("Cash in Hand / Live Drawer", FinancialEngine.formatPaise(finStatement.cashInHandPaise), tokens.text)
                                ReportRow("Bank Account Balances", FinancialEngine.formatPaise(finStatement.bankTotalPaise), tokens.text)
                                ReportRow("Customer Receivables (Debtors)", FinancialEngine.formatPaise(finStatement.receivablesPaise), tokens.text)
                                ReportRow("Stock Inventory Valuation", FinancialEngine.formatPaise(finStatement.stockValuationPaise), tokens.text)
                                if (gstInputCreditAssetPaise > 0L) {
                                    ReportRow("Net GST Input Credit (ITC Asset)", FinancialEngine.formatPaise(gstInputCreditAssetPaise), tokens.success)
                                }

                                HorizontalDivider(color = tokens.cardBorder)

                                ReportRow(
                                    label = "TOTAL ASSETS",
                                    amount = FinancialEngine.formatPaise(finStatement.totalAssetsPaise),
                                    amountColor = tokens.text,
                                    isBold = true
                                )

                                Spacer(modifier = Modifier.height(10.dp))

                                // LIABILITIES & EQUITY SECTION
                                Text(
                                    text = "LIABILITIES & PROPRIETOR EQUITY",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.5.sp,
                                    color = tokens.accent
                                )
                                ReportRow("Supplier Payables (Creditors)", FinancialEngine.formatPaise(finStatement.payablesPaise), tokens.text)
                                if (gstPayableLiabilityPaise > 0L) {
                                    ReportRow("Net GST Payable Liability", FinancialEngine.formatPaise(gstPayableLiabilityPaise), tokens.danger)
                                }
                                ReportRow("Opening Capital", FinancialEngine.formatPaise(finStatement.openingCapitalPaise), tokens.text)
                                ReportRow("Period Net Profit / (Loss)", FinancialEngine.formatPaise(finStatement.netProfitPaise), if (finStatement.netProfitPaise >= 0L) tokens.success else tokens.danger)
                                ReportRow("Less: Period Drawings", "- " + FinancialEngine.formatPaise(finStatement.drawingsPaise), tokens.danger)
                                ReportRow("Closing Capital & Reserves", FinancialEngine.formatPaise(finStatement.closingCapitalPaise), tokens.text, isBold = true)

                                HorizontalDivider(color = tokens.cardBorder, thickness = 1.5.dp)

                                ReportRow(
                                    label = "TOTAL LIABILITIES & CAPITAL",
                                    amount = FinancialEngine.formatPaise(finStatement.totalLiabilitiesAndEquityPaise),
                                    amountColor = tokens.text,
                                    isBold = true,
                                    isHighlight = true
                                )
                            }
                        }
                    }
                }

                2 -> {
                    // TAB 2: GST FILING RELATED DATA (GSTR-1 & GSTR-3B)
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = tokens.card),
                            border = BorderStroke(1.dp, tokens.cardBorder),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "GSTR-1 Outward Supplies Summary",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = tokens.text
                                    )
                                    Surface(
                                        color = tokens.accentTint,
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = selectedFy,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = tokens.accent,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                        )
                                    }
                                }

                                HorizontalDivider(color = tokens.cardBorder)

                                ReportRow("Total Taxable Sales Turnover", FinancialEngine.formatPaise(gstSummary.totalTaxableSalesPaise), tokens.text, isBold = true)
                                ReportRow("• B2B Registered Sales (${gstSummary.b2bInvoiceCount} Invoices)", FinancialEngine.formatPaise(gstSummary.b2bTaxableSalesPaise), tokens.text)
                                ReportRow("• B2C Consumer Sales (${gstSummary.b2cInvoiceCount} Invoices)", FinancialEngine.formatPaise(gstSummary.b2cTaxableSalesPaise), tokens.text)

                                Spacer(modifier = Modifier.height(4.dp))
                                HorizontalDivider(color = tokens.cardBorder)

                                Text(
                                    text = "GSTR-3B TAX LIABILITY & INPUT TAX CREDIT (ITC)",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.5.sp,
                                    color = tokens.accent
                                )

                                ReportRow("Output Tax Liability (CGST + SGST + IGST)", FinancialEngine.formatPaise(gstSummary.totalOutputTaxPaise), tokens.text)
                                ReportRow("Less: Eligible ITC (Purchases & Expenses)", "- " + FinancialEngine.formatPaise(gstSummary.totalEligibleItcPaise), tokens.success)

                                HorizontalDivider(color = tokens.cardBorder, thickness = 1.5.dp)

                                val isPayable = gstSummary.netGstPayablePaise >= 0L
                                ReportRow(
                                    label = if (isPayable) "NET GST PAYABLE IN CASH" else "EXCESS ITC CARRIED FORWARD",
                                    amount = FinancialEngine.formatPaise(kotlin.math.abs(gstSummary.netGstPayablePaise)),
                                    amountColor = if (gstSummary.netGstPayablePaise > 0L) tokens.danger else tokens.success,
                                    isBold = true,
                                    isHighlight = true
                                )
                            }
                        }
                    }

                    // RATE-WISE GST BREAKDOWN CARD
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = tokens.card),
                            border = BorderStroke(1.dp, tokens.cardBorder),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "Rate-Wise GST Slabs ($selectedFy)",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.5.sp,
                                    color = tokens.text
                                )
                                HorizontalDivider(color = tokens.cardBorder)

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Rate", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = tokens.textMuted, modifier = Modifier.weight(1f))
                                    Text("Taxable", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = tokens.textMuted, modifier = Modifier.weight(1.3f))
                                    Text("CGST+SGST", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = tokens.textMuted, modifier = Modifier.weight(1.3f))
                                    Text("Total Tax", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = tokens.textMuted, modifier = Modifier.weight(1.3f))
                                }

                                HorizontalDivider(color = tokens.cardBorder)

                                gstSummary.slabBreakdown.forEach { row ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(row.rateLabel, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = tokens.text, modifier = Modifier.weight(1f))
                                        Text(FinancialEngine.formatPaise(row.taxablePaise), fontSize = 11.sp, color = tokens.text, modifier = Modifier.weight(1.3f))
                                        Text(FinancialEngine.formatPaise(row.cgstPaise + row.sgstPaise), fontSize = 11.sp, color = tokens.textMuted, modifier = Modifier.weight(1.3f))
                                        Text(FinancialEngine.formatPaise(row.totalTaxPaise), fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = tokens.accent, modifier = Modifier.weight(1.3f))
                                    }
                                }
                            }
                        }
                    }
                }

                3 -> {
                    // TAB 3: ANNUAL GSTR-9 SUMMARY CALCULATOR
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = tokens.card),
                            border = BorderStroke(1.dp, tokens.cardBorder),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.testTag("gstr9_overview_card")
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.Calculate,
                                            contentDescription = null,
                                            tint = tokens.accent,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "Annual GSTR-9 Summary",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.5.sp,
                                            color = tokens.text
                                        )
                                    }
                                    Surface(
                                        color = tokens.accentTint,
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = "$selectedFy Annual",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = tokens.accent,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                        )
                                    }
                                }

                                Text(
                                    text = "Consolidated 4-Quarter Statutory GST Return Calculator under Section 44 of CGST Act. Aggregates quarterly sales, output liabilities, and ITC.",
                                    fontSize = 11.5.sp,
                                    color = tokens.textMuted
                                )

                                HorizontalDivider(color = tokens.cardBorder)

                                // 4 KPI metric chips
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Surface(
                                        modifier = Modifier.weight(1f),
                                        color = tokens.secondaryBg,
                                        shape = RoundedCornerShape(8.dp),
                                        border = BorderStroke(0.5.dp, tokens.cardBorder)
                                    ) {
                                        Column(modifier = Modifier.padding(10.dp)) {
                                            Text("Annual Turnover", fontSize = 10.sp, color = tokens.textMuted)
                                            Text(
                                                FinancialEngine.formatPaise(gstr9Summary.annualTaxableSalesPaise),
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = tokens.text
                                            )
                                        }
                                    }
                                    Surface(
                                        modifier = Modifier.weight(1f),
                                        color = tokens.secondaryBg,
                                        shape = RoundedCornerShape(8.dp),
                                        border = BorderStroke(0.5.dp, tokens.cardBorder)
                                    ) {
                                        Column(modifier = Modifier.padding(10.dp)) {
                                            Text("Total Output Tax", fontSize = 10.sp, color = tokens.textMuted)
                                            Text(
                                                FinancialEngine.formatPaise(gstr9Summary.annualTotalOutputTaxPaise),
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = tokens.danger
                                            )
                                        }
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Surface(
                                        modifier = Modifier.weight(1f),
                                        color = tokens.secondaryBg,
                                        shape = RoundedCornerShape(8.dp),
                                        border = BorderStroke(0.5.dp, tokens.cardBorder)
                                    ) {
                                        Column(modifier = Modifier.padding(10.dp)) {
                                            Text("Eligible ITC Availed", fontSize = 10.sp, color = tokens.textMuted)
                                            Text(
                                                FinancialEngine.formatPaise(gstr9Summary.annualTotalItcPaise),
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = tokens.success
                                            )
                                        }
                                    }
                                    Surface(
                                        modifier = Modifier.weight(1f),
                                        color = tokens.secondaryBg,
                                        shape = RoundedCornerShape(8.dp),
                                        border = BorderStroke(0.5.dp, tokens.cardBorder)
                                    ) {
                                        Column(modifier = Modifier.padding(10.dp)) {
                                            Text("Net Tax Payable", fontSize = 10.sp, color = tokens.textMuted)
                                            Text(
                                                FinancialEngine.formatPaise(gstr9Summary.annualNetTaxPayablePaise),
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = tokens.accent
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(4.dp))

                                // Action Buttons
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(
                                        onClick = {
                                            ExportEngine.generateAndShareGstr9ReportPdf(
                                                context = context,
                                                profile = uiState.profile,
                                                fyLabel = selectedFy,
                                                gstr9 = gstr9Summary
                                            )
                                        },
                                        modifier = Modifier
                                            .weight(1f)
                                            .heightIn(min = 48.dp)
                                            .testTag("btn_gstr9_pdf"),
                                        colors = ButtonDefaults.buttonColors(containerColor = tokens.primary),
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp)
                                    ) {
                                        Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = tokens.primaryText, modifier = Modifier.size(15.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Download PDF", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = tokens.primaryText)
                                    }

                                    OutlinedButton(
                                        onClick = {
                                            ExportEngine.generateAndShareGstr9ReportCsv(
                                                context = context,
                                                profile = uiState.profile,
                                                fyLabel = selectedFy,
                                                gstr9 = gstr9Summary
                                            )
                                        },
                                        modifier = Modifier
                                            .weight(1f)
                                            .heightIn(min = 48.dp)
                                            .testTag("btn_gstr9_csv"),
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp)
                                    ) {
                                        Icon(Icons.Default.TableChart, contentDescription = null, modifier = Modifier.size(15.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Export CSV", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }

                    // 4-QUARTER COMPARISON MATRIX CARD
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = tokens.card),
                            border = BorderStroke(1.dp, tokens.cardBorder),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.testTag("gstr9_quarterly_matrix_card")
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Quarterly Aggregation Matrix (Q1 to Q4)",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.5.sp,
                                        color = tokens.text
                                    )
                                    Text(
                                        text = "${gstr9Summary.totalInvoicesCount} Invoices",
                                        fontSize = 11.sp,
                                        color = tokens.textMuted
                                    )
                                }

                                Text(
                                    text = "Breakdown of taxable sales turnover, output taxes, and eligible ITC across each fiscal quarter:",
                                    fontSize = 11.sp,
                                    color = tokens.textMuted
                                )

                                HorizontalDivider(color = tokens.cardBorder)

                                // Render each of 4 quarters
                                gstr9Summary.quarters.forEach { q ->
                                    Surface(
                                        modifier = Modifier.fillMaxWidth(),
                                        color = tokens.secondaryBg,
                                        shape = RoundedCornerShape(10.dp),
                                        border = BorderStroke(0.5.dp, tokens.cardBorder)
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(10.dp),
                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Surface(
                                                        color = tokens.primary.copy(alpha = 0.15f),
                                                        shape = RoundedCornerShape(4.dp)
                                                    ) {
                                                        Text(
                                                            text = q.quarterKey,
                                                            fontSize = 11.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = tokens.primary,
                                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                        )
                                                    }
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text(
                                                        text = q.dateRangeDesc,
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Medium,
                                                        color = tokens.textMuted
                                                    )
                                                }
                                                Text(
                                                    text = "${q.invoiceCount} inv",
                                                    fontSize = 10.5.sp,
                                                    color = tokens.textMuted
                                                )
                                            }

                                            HorizontalDivider(color = tokens.cardBorder, thickness = 0.5.dp)

                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text("Taxable Sales", fontSize = 9.5.sp, color = tokens.textMuted)
                                                    Text(FinancialEngine.formatPaise(q.taxableSalesPaise), fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = tokens.text)
                                                }
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text("Output Tax", fontSize = 9.5.sp, color = tokens.textMuted)
                                                    Text(FinancialEngine.formatPaise(q.totalOutputTaxPaise), fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = tokens.danger)
                                                }
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text("Eligible ITC", fontSize = 9.5.sp, color = tokens.textMuted)
                                                    Text(FinancialEngine.formatPaise(q.totalItcPaise), fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = tokens.success)
                                                }
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text("Net Tax", fontSize = 9.5.sp, color = tokens.textMuted)
                                                    Text(FinancialEngine.formatPaise(q.netTaxPayablePaise), fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = tokens.accent)
                                                }
                                            }
                                        }
                                    }
                                }

                                // Full Annual Totals Banner
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    color = tokens.accentTint,
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(1.dp, tokens.accent.copy(alpha = 0.3f))
                                ) {
                                    Column(
                                        modifier = Modifier.padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = "ANNUAL CONSOLIDATED TOTAL ($selectedFy)",
                                                fontSize = 11.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = tokens.accent
                                            )
                                            Text(
                                                text = "${gstr9Summary.totalInvoicesCount} Invoices",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = tokens.accent
                                            )
                                        }
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text("Sales Turnover", fontSize = 9.5.sp, color = tokens.textMuted)
                                                Text(FinancialEngine.formatPaise(gstr9Summary.annualTaxableSalesPaise), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = tokens.text)
                                            }
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text("Output GST", fontSize = 9.5.sp, color = tokens.textMuted)
                                                Text(FinancialEngine.formatPaise(gstr9Summary.annualTotalOutputTaxPaise), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = tokens.danger)
                                            }
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text("ITC Availed", fontSize = 9.5.sp, color = tokens.textMuted)
                                                Text(FinancialEngine.formatPaise(gstr9Summary.annualTotalItcPaise), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = tokens.success)
                                            }
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text("Net Tax Due", fontSize = 9.5.sp, color = tokens.textMuted)
                                                Text(FinancialEngine.formatPaise(gstr9Summary.annualNetTaxPayablePaise), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = tokens.accent)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // STATUTORY TABLE 4: OUTWARD SUPPLIES CARD
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = tokens.card),
                            border = BorderStroke(1.dp, tokens.cardBorder),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.testTag("gstr9_table4_card")
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "Table 4: Outward Supplies on which Tax is Payable",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.5.sp,
                                    color = tokens.text
                                )
                                Text(
                                    text = "Statutory GSTR-9 breakdown of supplies made to unregistered (B2C) and registered (B2B) entities:",
                                    fontSize = 11.sp,
                                    color = tokens.textMuted
                                )

                                HorizontalDivider(color = tokens.cardBorder)

                                ReportRow(
                                    label = "4A. Supplies to Unregistered Persons (B2C)",
                                    amount = FinancialEngine.formatPaise(gstr9Summary.table4Details.b2cTaxablePaise),
                                    amountColor = tokens.text
                                )
                                Text(
                                    text = "  Tax liability: ${FinancialEngine.formatPaise(gstr9Summary.table4Details.b2cTaxPaise)}",
                                    fontSize = 10.5.sp,
                                    color = tokens.textMuted
                                )

                                HorizontalDivider(color = tokens.cardBorder, thickness = 0.5.dp)

                                ReportRow(
                                    label = "4B. Supplies to Registered Persons (B2B)",
                                    amount = FinancialEngine.formatPaise(gstr9Summary.table4Details.b2bTaxablePaise),
                                    amountColor = tokens.text
                                )
                                Text(
                                    text = "  Tax liability: ${FinancialEngine.formatPaise(gstr9Summary.table4Details.b2bTaxPaise)}",
                                    fontSize = 10.5.sp,
                                    color = tokens.textMuted
                                )

                                HorizontalDivider(color = tokens.cardBorder, thickness = 1.dp)

                                ReportRow(
                                    label = "4N. Total Outward Supplies (Taxable Turnover)",
                                    amount = FinancialEngine.formatPaise(gstr9Summary.table4Details.totalTaxablePaise),
                                    amountColor = tokens.text,
                                    isBold = true,
                                    isHighlight = true
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("CGST: ${FinancialEngine.formatPaise(gstr9Summary.table4Details.totalCgstPaise)} | SGST: ${FinancialEngine.formatPaise(gstr9Summary.table4Details.totalSgstPaise)} | IGST: ${FinancialEngine.formatPaise(gstr9Summary.table4Details.totalIgstPaise)}", fontSize = 10.5.sp, color = tokens.textMuted)
                                    Text("Total Tax: ${FinancialEngine.formatPaise(gstr9Summary.table4Details.grandTotalTaxPaise)}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = tokens.accent)
                                }
                            }
                        }
                    }

                    // STATUTORY TABLE 6: ITC AVAILED CARD
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = tokens.card),
                            border = BorderStroke(1.dp, tokens.cardBorder),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.testTag("gstr9_table6_card")
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "Table 6: Details of ITC Availed during Financial Year",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.5.sp,
                                    color = tokens.text
                                )
                                Text(
                                    text = "Input Tax Credit claims from inward supplies (business purchases) during $selectedFy:",
                                    fontSize = 11.sp,
                                    color = tokens.textMuted
                                )

                                HorizontalDivider(color = tokens.cardBorder)

                                ReportRow(
                                    label = "6B. Inward Supplies (other than imports & RCM)",
                                    amount = FinancialEngine.formatPaise(gstr9Summary.table6Details.inwardSuppliesTaxablePaise),
                                    amountColor = tokens.text
                                )
                                ReportRow(
                                    label = "• Central Tax (CGST ITC)",
                                    amount = FinancialEngine.formatPaise(gstr9Summary.table6Details.itcCgstPaise),
                                    amountColor = tokens.textMuted
                                )
                                ReportRow(
                                    label = "• State / UT Tax (SGST ITC)",
                                    amount = FinancialEngine.formatPaise(gstr9Summary.table6Details.itcSgstPaise),
                                    amountColor = tokens.textMuted
                                )
                                ReportRow(
                                    label = "• Integrated Tax (IGST ITC)",
                                    amount = FinancialEngine.formatPaise(gstr9Summary.table6Details.itcIgstPaise),
                                    amountColor = tokens.textMuted
                                )

                                HorizontalDivider(color = tokens.cardBorder, thickness = 1.dp)

                                ReportRow(
                                    label = "Total Input Tax Credit (ITC) Availed",
                                    amount = FinancialEngine.formatPaise(gstr9Summary.table6Details.totalItcPaise),
                                    amountColor = tokens.success,
                                    isBold = true,
                                    isHighlight = true
                                )
                            }
                        }
                    }

                    // STATUTORY TABLE 9: TAX PAID AS DECLARED IN RETURNS
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = tokens.card),
                            border = BorderStroke(1.dp, tokens.cardBorder),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.testTag("gstr9_table9_card")
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "Table 9: Tax Paid as Declared in Returns Filed",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.5.sp,
                                    color = tokens.text
                                )
                                Text(
                                    text = "Annual tax settlement reconciliation between ITC ledger and cash payments:",
                                    fontSize = 11.sp,
                                    color = tokens.textMuted
                                )

                                HorizontalDivider(color = tokens.cardBorder)

                                ReportRow(
                                    label = "Total Tax Payable (Table 4 Output)",
                                    amount = FinancialEngine.formatPaise(gstr9Summary.table9Details.taxPayablePaise),
                                    amountColor = tokens.danger
                                )
                                ReportRow(
                                    label = "Paid through ITC (Electronic Credit Ledger)",
                                    amount = "- " + FinancialEngine.formatPaise(gstr9Summary.table9Details.taxPaidItcPaise),
                                    amountColor = tokens.success
                                )
                                ReportRow(
                                    label = "Paid in Cash (Electronic Cash Ledger)",
                                    amount = "- " + FinancialEngine.formatPaise(gstr9Summary.table9Details.taxPaidCashPaise),
                                    amountColor = tokens.text
                                )

                                HorizontalDivider(color = tokens.cardBorder, thickness = 1.dp)

                                ReportRow(
                                    label = "Balance Tax Payable for Financial Year",
                                    amount = FinancialEngine.formatPaise(gstr9Summary.table9Details.balancePayablePaise),
                                    amountColor = if (gstr9Summary.table9Details.balancePayablePaise > 0L) tokens.danger else tokens.success,
                                    isBold = true,
                                    isHighlight = true
                                )

                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "ℹ️ Note: Section 44 requires GSTR-9 for businesses exceeding prescribed annual aggregate turnover. Reconciled with GSTR-1, GSTR-3B and Audited Books of Accounts.",
                                    fontSize = 10.sp,
                                    color = tokens.textMuted
                                )
                            }
                        }
                    }
                }

                4 -> {
                    // TAB 4: CMA & BANK PROJECT DOSSIER
                    item {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = tokens.infoContainer
                            ),
                            border = BorderStroke(1.dp, tokens.cardBorder),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.AccountBalance, contentDescription = null, tint = tokens.accent, modifier = Modifier.size(24.dp))
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text("4-Year CMA & Bank Project Report", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = tokens.text)
                                }
                                Text(
                                    "Configure Last Year Audited FY, Current Year Estimate & 2-Year Projections. Includes Tandon Committee MPBF, Nayak Method, DSCR, ICR & PDF Export for credit managers.",
                                    fontSize = 11.5.sp,
                                    color = tokens.textMuted
                                )
                                Button(
                                    onClick = { showFullCmaReport = true },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 48.dp)
                                        .testTag("btn_open_cma_dossier"),
                                    colors = ButtonDefaults.buttonColors(containerColor = tokens.primary),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(Icons.Default.Launch, contentDescription = null, tint = tokens.primaryText, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Open 4-Year Fillable CMA Dossier", fontWeight = FontWeight.Bold, fontSize = 12.5.sp, color = tokens.primaryText)
                                }
                            }
                        }
                    }
                }
                5 -> {
                    // TAB 5: GSTR-2B AUTOMATED RECONCILIATION
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = tokens.card),
                            border = BorderStroke(1.dp, tokens.cardBorder),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("GSTR-2B Automated ITC Reconciliation", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = tokens.text)
                                    Surface(
                                        color = tokens.secondaryBg,
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            "Rule 36(4)",
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = tokens.accent
                                        )
                                    }
                                }
                                Text(
                                    "Import official GST Portal GSTR-2B JSON or Excel/CSV to cross-reference with local purchase ledgers. Detects value discrepancies, unbooked supplier invoices, and at-risk claims with smart ₹1.00 tolerance.",
                                    fontSize = 11.5.sp,
                                    color = tokens.textMuted
                                )
                                Button(
                                    onClick = { showGstr2bRecon = true },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 48.dp)
                                        .testTag("btn_open_gstr2b_recon"),
                                    colors = ButtonDefaults.buttonColors(containerColor = tokens.primary),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(Icons.Default.Launch, contentDescription = null, tint = tokens.primaryText, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Launch GSTR-2B Reconciliation Tool", fontWeight = FontWeight.Bold, fontSize = 12.5.sp, color = tokens.primaryText)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReportRow(
    label: String,
    amount: String,
    amountColor: Color,
    isBold: Boolean = false,
    isHighlight: Boolean = false
) {
    val tokens = PabaruTheme.tokens
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = if (isHighlight) 4.dp else 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = if (isHighlight) 13.sp else 12.sp,
            fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal,
            color = if (isBold) tokens.text else tokens.textMuted
        )
        Text(
            text = amount,
            fontSize = if (isHighlight) 13.5.sp else 12.sp,
            fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal,
            color = amountColor
        )
    }
}
