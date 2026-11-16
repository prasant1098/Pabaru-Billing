package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.engine.CmaProjectEngine
import com.example.engine.ExportEngine
import com.example.engine.FinancialEngine
import com.example.model.BalanceType
import com.example.model.BusinessProfile
import com.example.model.ExpenseCategory
import com.example.model.Party
import com.example.model.PaymentMode
import com.example.model.ProductItem
import com.example.model.Transaction
import com.example.model.TransactionType
import com.example.ui.theme.PabaruTheme
import com.example.ui.theme.PabaruTokens
import com.example.ui.theme.danger
import com.example.ui.theme.info
import com.example.ui.theme.infoContainer
import com.example.ui.theme.success
import com.example.ui.theme.warning
import java.time.LocalDate
import java.util.Locale
import kotlin.math.roundToLong

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CmaProjectReportScreen(
    profile: BusinessProfile,
    transactions: List<Transaction>,
    parties: List<Party>,
    products: List<ProductItem>,
    cashAndBankPaise: Long,
    onBack: () -> Unit
) {
    val tokens = PabaruTheme.tokens
    val context = LocalContext.current

    BackHandler { onBack() }

    // User Selection for Last Audited FY Year (derived dynamically from current FY)
    val defaultBaseYear = remember {
        val now = LocalDate.now()
        val currentFyStart = if (now.monthValue >= 4) now.year else now.year - 1
        val targetAuditedStart = currentFyStart - 1
        val minYear = CmaProjectEngine.AVAILABLE_BASE_YEARS.minOf { it.first }
        val maxYear = CmaProjectEngine.AVAILABLE_BASE_YEARS.maxOf { it.first }
        targetAuditedStart.coerceIn(minYear, maxYear)
    }
    var selectedBaseYear by remember { mutableIntStateOf(defaultBaseYear) }
    var expandedBaseYearDropdown by remember { mutableStateOf(false) }
    var autoPopulateEpoch by remember(selectedBaseYear) { mutableIntStateOf(0) }

    // Initialize the 4-year dossier
    var dossier by remember(selectedBaseYear) {
        mutableStateOf(
            CmaProjectEngine.createInitialDossier(
                baseYear = selectedBaseYear,
                transactions = transactions,
                parties = parties,
                products = products,
                cashAndBankPaise = cashAndBankPaise
            )
        )
    }

    // Editable Project Report Data (Form I)
    var projectReportData by remember(profile.id, selectedBaseYear) {
        mutableStateOf(CmaProjectEngine.createDefaultProjectReport(profile, dossier))
    }

    // Interactive Term Loan Amortization Schedule (Form VII)
    val termLoanSchedule = remember(
        projectReportData.bankTermLoanPaise,
        projectReportData.proposedInterestRatePercent,
        projectReportData.loanTenorMonths,
        projectReportData.moratoriumMonths
    ) {
        CmaProjectEngine.calculateTermLoanSchedule(
            principalPaise = projectReportData.bankTermLoanPaise,
            annualRatePercent = projectReportData.proposedInterestRatePercent,
            tenorMonths = projectReportData.loanTenorMonths,
            moratoriumMonths = projectReportData.moratoriumMonths
        )
    }

    // Funds Flow Schedule (Form VI)
    val fundsFlowSchedule = remember(dossier) {
        CmaProjectEngine.computeFundsFlowSchedule(dossier)
    }

    // Top Tabs:
    // 0: Form I (Overview & Outlay)
    // 1: Form II (Operating Statement / P&L)
    // 2: Form III & IV (Balance Sheet & NWC)
    // 3: Form V (MPBF Tandon & Nayak)
    // 4: Form VI (Funds Flow)
    // 5: Form VII (Loan EMI Amortization)
    // 6: 19 Ratios & Appraisal
    // 7: Fill & Edit Year
    var selectedTab by remember { mutableIntStateOf(0) }

    // Currently selected year index to edit in Tab 7 (0..3)
    var editingYearIndex by remember { mutableIntStateOf(1) } // Default to Current Year Estimate

    // Computed banker feedback for improvements
    val feedbackList = remember(dossier) {
        CmaProjectEngine.evaluateDossier(dossier)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(tokens.bg)
    ) {
        // App Bar / Top Navigation
        Surface(
            color = tokens.headerBg,
            shadowElevation = 4.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier.testTag("cma_back_button")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = tokens.headerText)
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Column {
                            Text("RBI Standard CMA & Project Report", fontWeight = FontWeight.Black, fontSize = 15.sp, color = tokens.headerText)
                            Text("Forms I–VI • MPBF Methods 1 & 2 • Nayak Norms • Amortization", fontSize = 10.sp, color = tokens.accent)
                        }
                    }

                    // Action buttons (PDF & WhatsApp)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                ExportEngine.generateAndShareCmaProjectReportPdf(context, profile, dossier, feedbackList, projectReportData)
                            },
                            modifier = Modifier.testTag("cma_export_pdf_button")
                        ) {
                            Icon(Icons.Default.PictureAsPdf, contentDescription = "Export PDF", tint = tokens.headerText)
                        }
                        IconButton(
                            onClick = {
                                ExportEngine.shareCmaSummaryWhatsApp(context, profile, dossier, feedbackList, projectReportData)
                            },
                            modifier = Modifier.testTag("cma_share_whatsapp_button")
                        ) {
                            Icon(Icons.Default.Share, contentDescription = "Share", tint = tokens.headerText)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Selector for User to Choose Last FY Year
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(tokens.headerText.copy(alpha = 0.1f))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CalendarToday, contentDescription = null, tint = tokens.accent, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Base 4-Year Timeline:", fontSize = 11.sp, color = tokens.headerText, fontWeight = FontWeight.Bold)
                    }

                    Box {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = tokens.primary,
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .clickable { expandedBaseYearDropdown = true }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val currentLabel = CmaProjectEngine.AVAILABLE_BASE_YEARS.find { it.first == selectedBaseYear }?.second
                                    ?: "FY $selectedBaseYear-${(selectedBaseYear + 1).toString().takeLast(2)}"
                                Text(currentLabel, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = tokens.primaryText)
                                Icon(Icons.Default.ArrowDropDown, contentDescription = "Select Base FY", tint = tokens.primaryText, modifier = Modifier.size(16.dp))
                            }
                        }

                        DropdownMenu(
                            expanded = expandedBaseYearDropdown,
                            onDismissRequest = { expandedBaseYearDropdown = false },
                            modifier = Modifier.background(tokens.card)
                        ) {
                            CmaProjectEngine.AVAILABLE_BASE_YEARS.forEach { (year, label) ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            "$label (Last Audited)",
                                            fontSize = 12.sp,
                                            fontWeight = if (year == selectedBaseYear) FontWeight.Bold else FontWeight.Normal,
                                            color = tokens.text
                                        )
                                    },
                                    onClick = {
                                        selectedBaseYear = year
                                        expandedBaseYearDropdown = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        // Sub Navigation Tabs across all RBI Statutory Schedules
        ScrollableTabRow(
            selectedTabIndex = selectedTab,
            containerColor = tokens.secondaryBg,
            contentColor = tokens.primary,
            edgePadding = 12.dp
        ) {
            val tabItems = listOf(
                "Form I: Outlay" to Icons.Default.Description,
                "Form II: P&L" to Icons.Default.ReceiptLong,
                "Form III & IV: Bal Sheet" to Icons.Default.AccountBalance,
                "Form V: MPBF" to Icons.Default.Calculate,
                "Form VI: Funds Flow" to Icons.Default.CompareArrows,
                "Form VII: Loan EMI" to Icons.Default.Payments,
                "19 Ratios" to Icons.Default.Analytics,
                "Edit Year" to Icons.Default.EditNote
            )

            tabItems.forEachIndexed { index, (title, icon) ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    modifier = Modifier.testTag("cma_tab_$index"),
                    text = { Text(title, fontSize = 11.sp, fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Medium) },
                    icon = { Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    selectedContentColor = tokens.primary,
                    unselectedContentColor = tokens.textMuted
                )
            }
        }

        // Body Content
        Box(modifier = Modifier.fillMaxSize().padding(12.dp)) {
            when (selectedTab) {
                0 -> ProjectReportEditorView(
                    projectReport = projectReportData,
                    onUpdateProjectReport = { projectReportData = it },
                    onGeneratePdf = {
                        ExportEngine.generateAndShareCmaProjectReportPdf(context, profile, dossier, feedbackList, projectReportData)
                    },
                    onShareWhatsApp = {
                        ExportEngine.shareCmaSummaryWhatsApp(context, profile, dossier, feedbackList, projectReportData)
                    },
                    tokens = tokens,
                    resetKey = selectedBaseYear
                )
                1 -> FormIiOperatingStatementView(dossier = dossier, tokens = tokens)
                2 -> FormIiiBalanceSheetView(dossier = dossier, tokens = tokens)
                3 -> FormVMpbfView(dossier = dossier, tokens = tokens)
                4 -> FormViFundsFlowView(fundsFlowSchedule = fundsFlowSchedule, tokens = tokens)
                5 -> FormViiAmortizationView(
                    schedule = termLoanSchedule,
                    projectReport = projectReportData,
                    onUpdateTerms = { amt, rate, tenor, mor ->
                        projectReportData = projectReportData.copy(
                            bankTermLoanPaise = amt,
                            proposedInterestRatePercent = rate,
                            loanTenorMonths = tenor,
                            moratoriumMonths = mor
                        )
                    },
                    tokens = tokens,
                    resetKey = selectedBaseYear
                )
                6 -> LoanRatiosScorecardView(dossier = dossier, feedbackList = feedbackList, tokens = tokens)
                7 -> FillableYearEditorView(
                    dossier = dossier,
                    editingYearIndex = editingYearIndex,
                    onSelectYearIndex = { editingYearIndex = it },
                    onUpdateYear = { updatedYear ->
                        val updatedYears = dossier.years.toMutableList()
                        updatedYears[editingYearIndex] = updatedYear
                        dossier = dossier.copy(years = updatedYears)
                    },
                    onAutoPopulateCurrentYear = {
                        val updatedYears = dossier.years.toMutableList()
                        val y2 = updatedYears[1]
                        val fyLabel = y2.fyLabel
                        val fyEnd = FinancialEngine.getFinancialYearEndDate(fyLabel)
                        val partiesMap = parties.associateBy { it.id }

                        var liveSales = 0L
                        var liveReturns = 0L
                        var livePurchases = 0L
                        var liveDirectExp = 0L
                        var liveIndirectExp = 0L
                        var liveDebtors = parties.filter { it.openingBalanceType == BalanceType.Receivable }.sumOf { it.openingBalancePaise }
                        var liveCreditors = parties.filter { it.openingBalanceType == BalanceType.Payable }.sumOf { it.openingBalancePaise }

                        transactions.filter { !it.isVoided }.forEach { t ->
                            val normDate = FinancialEngine.normalizeDate(t.date)
                            val inFy = FinancialEngine.isDateInFinancialYear(t.date, fyLabel)
                            val upToFyEnd = normDate.isNotEmpty() && normDate <= fyEnd
                            val excl = if (t.items.isNotEmpty()) t.items.sumOf { it.taxableAmountPaise } else t.amountPaise
                            val isSupplier = t.partyId?.let { partiesMap[it]?.isSupplier }

                            when (t.type) {
                                TransactionType.Sale, TransactionType.NonGstBill -> {
                                    if (inFy) liveSales += excl
                                    if (upToFyEnd && t.partyId != null && t.paymentMode == PaymentMode.Credit) liveDebtors += t.amountPaise
                                }
                                TransactionType.CreditNote -> {
                                    if (isSupplier == true) {
                                        if (inFy) livePurchases -= excl
                                        if (upToFyEnd && t.partyId != null && t.paymentMode == PaymentMode.Credit) liveCreditors -= t.amountPaise
                                    } else {
                                        if (inFy) liveReturns += excl
                                        if (upToFyEnd && t.partyId != null && t.paymentMode == PaymentMode.Credit) liveDebtors -= t.amountPaise
                                    }
                                }
                                TransactionType.Purchase -> {
                                    if (inFy) livePurchases += excl
                                    if (upToFyEnd && t.partyId != null && t.paymentMode == PaymentMode.Credit) liveCreditors += t.amountPaise
                                }
                                TransactionType.DebitNote -> {
                                    if (isSupplier == false) {
                                        if (inFy) liveSales += excl
                                        if (upToFyEnd && t.partyId != null && t.paymentMode == PaymentMode.Credit) liveDebtors += t.amountPaise
                                    } else {
                                        if (inFy) livePurchases -= excl
                                        if (upToFyEnd && t.partyId != null && t.paymentMode == PaymentMode.Credit) liveCreditors -= t.amountPaise
                                    }
                                }
                                TransactionType.PaymentIn -> if (upToFyEnd && t.partyId != null) liveDebtors -= t.amountPaise
                                TransactionType.PaymentOut -> if (upToFyEnd && t.partyId != null) liveCreditors -= t.amountPaise
                                TransactionType.Expense -> {
                                    if (inFy) {
                                        if (t.expenseCategory == ExpenseCategory.Direct) liveDirectExp += excl
                                        else liveIndirectExp += excl
                                    }
                                }
                                else -> {}
                            }
                        }
                        livePurchases = livePurchases.coerceAtLeast(0L)
                        liveDebtors = liveDebtors.coerceAtLeast(0L)
                        liveCreditors = liveCreditors.coerceAtLeast(0L)
                        val liveStock = products.filter { !it.isArchived }.sumOf {
                            (it.currentStockMilli * it.purchasePricePaise + 500L) / 1000L
                        }

                        updatedYears[1] = y2.copy(
                            grossSalesPaise = if (liveSales > 0L) liveSales else y2.grossSalesPaise,
                            salesReturnsAndDeductionsPaise = if (liveReturns > 0L) liveReturns else y2.salesReturnsAndDeductionsPaise,
                            rawMaterialPurchasesPaise = if (livePurchases > 0L) livePurchases else y2.rawMaterialPurchasesPaise,
                            directPowerAndFreightPaise = if (liveDirectExp > 0L) liveDirectExp else y2.directPowerAndFreightPaise,
                            salariesAndAdminPaise = if (liveIndirectExp > 0L) (liveIndirectExp * 0.52).roundToLong() else y2.salariesAndAdminPaise,
                            sundryDebtorsPaise = if (liveDebtors > 0L) liveDebtors else y2.sundryDebtorsPaise,
                            sundryCreditorsPaise = if (liveCreditors > 0L) liveCreditors else y2.sundryCreditorsPaise,
                            inventoryStockPaise = if (liveStock > 0L) liveStock else y2.inventoryStockPaise,
                            cashAndBankPaise = cashAndBankPaise
                        )
                        dossier = dossier.copy(years = updatedYears)
                        autoPopulateEpoch++
                    },
                    onAutoProjectNext2Years = {
                        val updatedYears = dossier.years.toMutableList()
                        val y2 = updatedYears[1]
                        val y3 = CmaProjectEngine.projectNextYear(y2, updatedYears[2].fyLabel, CmaProjectEngine.CmaYearStage.ProjectionYear1, "Projection Year 1", 0.18)
                        val y4 = CmaProjectEngine.projectNextYear(y3, updatedYears[3].fyLabel, CmaProjectEngine.CmaYearStage.ProjectionYear2, "Projection Year 2", 0.15)
                        updatedYears[2] = y3
                        updatedYears[3] = y4
                        dossier = dossier.copy(years = updatedYears)
                        autoPopulateEpoch++
                    },
                    tokens = tokens,
                    resetKey = Pair(selectedBaseYear, autoPopulateEpoch)
                )
            }
        }
    }
}

fun toLakhStr(paise: Long): String = String.format(Locale.ENGLISH, "₹ %.2f L", (paise / 100.0) / 100000.0)

private fun paiseToRupeeInput(paise: Long): String {
    return if (paise % 100L == 0L) (paise / 100L).toString()
    else String.format(Locale.ENGLISH, "%.2f", paise / 100.0)
}

// ==================== TAB 0: FORM I PROJECT REPORT & FACILITIES ====================
@Composable
fun ProjectReportEditorView(
    projectReport: CmaProjectEngine.ProjectReportData,
    onUpdateProjectReport: (CmaProjectEngine.ProjectReportData) -> Unit,
    onGeneratePdf: () -> Unit,
    onShareWhatsApp: () -> Unit,
    tokens: PabaruTokens,
    resetKey: Any = Unit
) {
    var title by remember(resetKey) { mutableStateOf(projectReport.projectTitle) }
    var sector by remember(resetKey) { mutableStateOf(projectReport.industrySector) }
    var promoterName by remember(resetKey) { mutableStateOf(projectReport.promoterName) }
    var promoterBio by remember(resetKey) { mutableStateOf(projectReport.promoterBackground) }
    var location by remember(resetKey) { mutableStateOf(projectReport.locationDetails) }
    var bankName by remember(resetKey) { mutableStateOf(projectReport.proposedBankName) }
    var facility by remember(resetKey) { mutableStateOf(projectReport.loanFacilityRequested) }
    var tenorText by remember(resetKey) { mutableStateOf(projectReport.loanTenorMonths.toString()) }
    var moratoriumText by remember(resetKey) { mutableStateOf(projectReport.moratoriumMonths.toString()) }
    var interestRateText by remember(resetKey) { mutableStateOf(projectReport.proposedInterestRatePercent.toString()) }

    var primarySecurity by remember(resetKey) { mutableStateOf(projectReport.primarySecurity) }
    var collateralSecurity by remember(resetKey) { mutableStateOf(projectReport.collateralSecurity) }

    // Cost of Project (Rupees)
    var landText by remember(resetKey) { mutableStateOf(paiseToRupeeInput(projectReport.landAndBuildingPaise)) }
    var machineryText by remember(resetKey) { mutableStateOf(paiseToRupeeInput(projectReport.plantAndMachineryPaise)) }
    var furnitureText by remember(resetKey) { mutableStateOf(paiseToRupeeInput(projectReport.furnitureAndFixturesPaise)) }
    var wcMarginText by remember(resetKey) { mutableStateOf(paiseToRupeeInput(projectReport.workingCapitalMarginPaise)) }
    var prelimText by remember(resetKey) { mutableStateOf(paiseToRupeeInput(projectReport.preliminaryExpensesPaise)) }

    // Means of Finance (Rupees)
    var promoterEquityText by remember(resetKey) { mutableStateOf(paiseToRupeeInput(projectReport.promoterContributionPaise)) }
    var bankTermLoanText by remember(resetKey) { mutableStateOf(paiseToRupeeInput(projectReport.bankTermLoanPaise)) }
    var bankCcText by remember(resetKey) { mutableStateOf(paiseToRupeeInput(projectReport.bankWorkingCapitalCcPaise)) }
    var subsidyText by remember(resetKey) { mutableStateOf(paiseToRupeeInput(projectReport.govtSubsidyPaise)) }

    fun commitProjectReport() {
        val updated = projectReport.copy(
            projectTitle = title,
            industrySector = sector,
            promoterName = promoterName,
            promoterBackground = promoterBio,
            locationDetails = location,
            proposedBankName = bankName,
            loanFacilityRequested = facility,
            loanTenorMonths = tenorText.toIntOrNull() ?: 60,
            moratoriumMonths = moratoriumText.toIntOrNull() ?: 6,
            proposedInterestRatePercent = interestRateText.toDoubleOrNull() ?: 9.5,
            primarySecurity = primarySecurity,
            collateralSecurity = collateralSecurity,
            landAndBuildingPaise = FinancialEngine.parsePaiseFromRupees(landText),
            plantAndMachineryPaise = FinancialEngine.parsePaiseFromRupees(machineryText),
            furnitureAndFixturesPaise = FinancialEngine.parsePaiseFromRupees(furnitureText),
            workingCapitalMarginPaise = FinancialEngine.parsePaiseFromRupees(wcMarginText),
            preliminaryExpensesPaise = FinancialEngine.parsePaiseFromRupees(prelimText),
            promoterContributionPaise = FinancialEngine.parsePaiseFromRupees(promoterEquityText),
            bankTermLoanPaise = FinancialEngine.parsePaiseFromRupees(bankTermLoanText),
            bankWorkingCapitalCcPaise = FinancialEngine.parsePaiseFromRupees(bankCcText),
            govtSubsidyPaise = FinancialEngine.parsePaiseFromRupees(subsidyText)
        )
        onUpdateProjectReport(updated)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("cma_form_1_screen"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Hero Action Banner
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = tokens.infoContainer),
                border = BorderStroke(1.dp, tokens.cardBorder),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Form I: Particulars of Proposed Credit Facilities", fontWeight = FontWeight.Black, fontSize = 14.sp, color = tokens.text)
                            Text("Standard RBI Bank Credit Processing & Appraisal Format", fontSize = 10.sp, color = tokens.textMuted)
                        }
                        Surface(color = tokens.accentTint, shape = RoundedCornerShape(6.dp)) {
                            Text("RBI Norms", modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = tokens.accent)
                        }
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                commitProjectReport()
                                onGeneratePdf()
                            },
                            modifier = Modifier.weight(1.2f).heightIn(min = 48.dp).testTag("cma_form1_generate_pdf_btn"),
                            colors = ButtonDefaults.buttonColors(containerColor = tokens.primary),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = tokens.primaryText, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Download CMA Dossier PDF", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = tokens.primaryText)
                        }

                        OutlinedButton(
                            onClick = {
                                commitProjectReport()
                                onShareWhatsApp()
                            },
                            modifier = Modifier.weight(0.8f).heightIn(min = 48.dp),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, tint = tokens.text, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Share Summary", fontSize = 11.sp, color = tokens.text)
                        }
                    }
                }
            }
        }

        // Section 1: Borrower Profile
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = tokens.card),
                border = BorderStroke(1.dp, tokens.cardBorder),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("1. Borrower & Project Profile", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = tokens.text)

                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it; commitProjectReport() },
                        label = { Text("Project Name / Title", fontSize = 11.sp) },
                        modifier = Modifier.fillMaxWidth().testTag("cma_input_project_title")
                    )

                    OutlinedTextField(
                        value = sector,
                        onValueChange = { sector = it; commitProjectReport() },
                        label = { Text("Industry & Economic Activity", fontSize = 11.sp) },
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = promoterName,
                        onValueChange = { promoterName = it; commitProjectReport() },
                        label = { Text("Promoter / Managing Director", fontSize = 11.sp) },
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = bankName,
                        onValueChange = { bankName = it; commitProjectReport() },
                        label = { Text("Proposed Lending Bank & Branch", fontSize = 11.sp) },
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = facility,
                        onValueChange = { facility = it; commitProjectReport() },
                        label = { Text("Credit Facilities Requested", fontSize = 11.sp) },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = tenorText,
                            onValueChange = { tenorText = it.filter { c -> c.isDigit() }; commitProjectReport() },
                            label = { Text("Tenor (Mo)", fontSize = 10.sp) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f).testTag("cma_input_tenor")
                        )
                        OutlinedTextField(
                            value = moratoriumText,
                            onValueChange = { moratoriumText = it.filter { c -> c.isDigit() }; commitProjectReport() },
                            label = { Text("Moratorium (Mo)", fontSize = 10.sp) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = interestRateText,
                            onValueChange = { interestRateText = FinancialEngine.sanitizeDecimalInput(it); commitProjectReport() },
                            label = { Text("ROI (%)", fontSize = 10.sp) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f).testTag("cma_input_interest_rate")
                        )
                    }

                    OutlinedTextField(
                        value = primarySecurity,
                        onValueChange = { primarySecurity = it; commitProjectReport() },
                        label = { Text("Primary Security (Hypothecation)", fontSize = 11.sp) },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = collateralSecurity,
                        onValueChange = { collateralSecurity = it; commitProjectReport() },
                        label = { Text("Collateral Security & Personal Guarantee", fontSize = 11.sp) },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        // Section 2: Cost of Project
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = tokens.card),
                border = BorderStroke(1.dp, tokens.cardBorder),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("2. Cost of Project (Capex Outlay)", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = tokens.text)
                        Surface(color = tokens.secondaryBg, shape = RoundedCornerShape(6.dp)) {
                            Text(
                                "Total: ${toLakhStr(projectReport.totalProjectCostPaise)}",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                fontWeight = FontWeight.Black,
                                fontSize = 12.sp,
                                color = tokens.accent
                            )
                        }
                    }

                    CmaNumericField("Land & Building Site Development (₹)", landText) { landText = it; commitProjectReport() }
                    CmaNumericField("Plant, Machinery & Commercial Equipment (₹)", machineryText) { machineryText = it; commitProjectReport() }
                    CmaNumericField("Furniture, Fixtures & Electrical Works (₹)", furnitureText) { furnitureText = it; commitProjectReport() }
                    CmaNumericField("Margin Money for Working Capital (₹)", wcMarginText) { wcMarginText = it; commitProjectReport() }
                    CmaNumericField("Preliminary & Pre-operative Expenses (₹)", prelimText) { prelimText = it; commitProjectReport() }
                }
            }
        }

        // Section 3: Means of Finance
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = tokens.card),
                border = BorderStroke(1.dp, tokens.cardBorder),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("3. Means of Finance (Funding Structure)", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = tokens.text)
                        Surface(color = tokens.secondaryBg, shape = RoundedCornerShape(6.dp)) {
                            Text(
                                "Total: ${toLakhStr(projectReport.totalMeansOfFinancePaise)}",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                fontWeight = FontWeight.Black,
                                fontSize = 12.sp,
                                color = tokens.accent
                            )
                        }
                    }

                    CmaNumericField("Promoter's Equity / Own Contribution (₹)", promoterEquityText) { promoterEquityText = it; commitProjectReport() }
                    CmaNumericField("Proposed Bank Term Loan (₹)", bankTermLoanText) { bankTermLoanText = it; commitProjectReport() }
                    CmaNumericField("Bank Working Capital Cash Credit (CC) (₹)", bankCcText) { bankCcText = it; commitProjectReport() }
                    CmaNumericField("Government Subsidy / Grant (₹)", subsidyText) { subsidyText = it; commitProjectReport() }
                }
            }
        }
    }
}

// ==================== TAB 1: FORM II OPERATING STATEMENT ====================
@Composable
fun FormIiOperatingStatementView(dossier: CmaProjectEngine.CmaProjectDossier, tokens: PabaruTokens) {
    val years = dossier.years
    val hScroll = rememberScrollState()

    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("cma_form_2_screen"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = tokens.card),
                border = BorderStroke(1.dp, tokens.cardBorder),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Form II: Operating Statement (P&L 4-Year Schedule)", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = tokens.text)
                    Text("Statutory multi-step presentation: Cost of Production, Overheads, EBIT & PAT", fontSize = 10.sp, color = tokens.textMuted)
                    Spacer(modifier = Modifier.height(10.dp))

                    Box(modifier = Modifier.fillMaxWidth().horizontalScroll(hScroll)) {
                        Column {
                            // Header Row
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(tokens.secondaryBg, RoundedCornerShape(6.dp))
                                    .padding(vertical = 6.dp, horizontal = 4.dp)
                            ) {
                                Text("Particulars (₹ in Lakhs)", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = tokens.accent, modifier = Modifier.width(230.dp))
                                years.forEach { y ->
                                    Column(modifier = Modifier.width(115.dp), horizontalAlignment = Alignment.End) {
                                        Text(y.fyLabel, fontWeight = FontWeight.Bold, fontSize = 10.5.sp, color = tokens.text)
                                        Text(y.stageLabel.take(9), fontSize = 8.5.sp, color = tokens.textMuted)
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            MatrixSectionHeader("I. REVENUE & SALES TURNOVER", tokens)
                            MatrixRow("Gross Sales / Turnover", years.map { toLakhStr(it.grossSalesPaise) }, isBold = true, tokens = tokens)
                            MatrixRow("Less: Returns, Allowances & Discounts", years.map { "- ${toLakhStr(it.salesReturnsAndDeductionsPaise)}" }, isBold = false, tokens = tokens)
                            MatrixRow("Net Sales Turnover", years.map { toLakhStr(it.netSalesPaise) }, isBold = true, isAccent = true, tokens = tokens)

                            MatrixSectionHeader("II. COST OF PRODUCTION & COST OF SALES", tokens)
                            MatrixRow("Raw Material Purchases", years.map { toLakhStr(it.rawMaterialPurchasesPaise) }, isBold = false, tokens = tokens)
                            MatrixRow("Direct Wages & Labor", years.map { toLakhStr(it.directWagesPaise) }, isBold = false, tokens = tokens)
                            MatrixRow("Power, Fuel & Freight Inward", years.map { toLakhStr(it.directPowerAndFreightPaise) }, isBold = false, tokens = tokens)
                            MatrixRow("Other Manufacturing Expenses", years.map { toLakhStr(it.otherDirectExpensesPaise) }, isBold = false, tokens = tokens)
                            MatrixRow("Cost of Production", years.map { toLakhStr(it.costOfProductionPaise) }, isBold = true, tokens = tokens)
                            MatrixRow("Add: Opening Stock (WIP & Goods)", years.map { toLakhStr(it.openingStockPaise) }, isBold = false, tokens = tokens)
                            MatrixRow("Less: Closing Stock (WIP & Goods)", years.map { "- ${toLakhStr(it.closingStockPaise)}" }, isBold = false, tokens = tokens)
                            MatrixRow("Cost of Goods Sold (COGS)", years.map { toLakhStr(it.costOfSalesPaise) }, isBold = true, tokens = tokens)
                            MatrixRow("Gross Profit", years.map { toLakhStr(it.grossProfitPaise) }, isBold = true, isAccent = true, tokens = tokens)
                            MatrixRow("Gross Profit Margin (%)", years.map { String.format(Locale.ENGLISH, "%.1f%%", it.gpMarginPercent) }, isBold = false, tokens = tokens)

                            MatrixSectionHeader("III. OPERATING EXPENSES & OVERHEADS", tokens)
                            MatrixRow("Salaries, Administrative & Office", years.map { toLakhStr(it.salariesAndAdminPaise) }, isBold = false, tokens = tokens)
                            MatrixRow("Rent, Rates & Utilities", years.map { toLakhStr(it.rentAndUtilitiesPaise) }, isBold = false, tokens = tokens)
                            MatrixRow("Selling & Marketing Expenses", years.map { toLakhStr(it.sellingAndMarketingPaise) }, isBold = false, tokens = tokens)
                            MatrixRow("Other Overheads & Repairs", years.map { toLakhStr(it.otherOverheadsPaise) }, isBold = false, tokens = tokens)
                            MatrixRow("Operating Profit (EBITDA)", years.map { toLakhStr(it.ebitdaPaise) }, isBold = true, isAccent = true, tokens = tokens)
                            MatrixRow("EBITDA Margin (%)", years.map { String.format(Locale.ENGLISH, "%.1f%%", it.ebitdaMarginPercent) }, isBold = false, tokens = tokens)

                            MatrixSectionHeader("IV. DEPRECIATION, FINANCE CHARGES & TAX", tokens)
                            MatrixRow("Less: Depreciation (WDV Schedule)", years.map { toLakhStr(it.depreciationPaise) }, isBold = false, tokens = tokens)
                            MatrixRow("Operating Profit After Depr. (EBIT)", years.map { toLakhStr(it.ebitPaise) }, isBold = true, tokens = tokens)
                            MatrixRow("Term Loan Interest", years.map { toLakhStr(it.termLoanInterestPaise) }, isBold = false, tokens = tokens)
                            MatrixRow("Cash Credit / OD Interest", years.map { toLakhStr(it.workingCapitalInterestPaise) }, isBold = false, tokens = tokens)
                            MatrixRow("Profit Before Tax (PBT)", years.map { toLakhStr(it.pbtPaise) }, isBold = true, tokens = tokens)
                            MatrixRow("Provision for Tax (${years.first().taxRatePercent}%)", years.map { toLakhStr(it.taxPaise) }, isBold = false, tokens = tokens)
                            MatrixRow("Profit After Tax (PAT)", years.map { toLakhStr(it.patPaise) }, isBold = true, isAccent = true, tokens = tokens)
                            MatrixRow("Net Profit Margin (PAT %)", years.map { String.format(Locale.ENGLISH, "%.1f%%", it.patMarginPercent) }, isBold = true, tokens = tokens)
                            MatrixRow("Cash Accruals (PAT + Depreciation)", years.map { toLakhStr(it.cashAccrualsPaise) }, isBold = true, tokens = tokens)
                        }
                    }
                }
            }
        }
    }
}

// ==================== TAB 2: FORM III & IV BALANCE SHEET ====================
@Composable
fun FormIiiBalanceSheetView(dossier: CmaProjectEngine.CmaProjectDossier, tokens: PabaruTokens) {
    val years = dossier.years
    val hScroll = rememberScrollState()

    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("cma_form_3_screen"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = tokens.card),
                border = BorderStroke(1.dp, tokens.cardBorder),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Form III & IV: Comparative Balance Sheet & Working Capital", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = tokens.text)
                    Text("Liabilities, Assets, and Net Working Capital (NWC = TCA - TCL)", fontSize = 10.sp, color = tokens.textMuted)
                    Spacer(modifier = Modifier.height(10.dp))

                    Box(modifier = Modifier.fillMaxWidth().horizontalScroll(hScroll)) {
                        Column {
                            // Header Row
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(tokens.secondaryBg, RoundedCornerShape(6.dp))
                                    .padding(vertical = 6.dp, horizontal = 4.dp)
                            ) {
                                Text("Particulars (₹ in Lakhs)", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = tokens.accent, modifier = Modifier.width(230.dp))
                                years.forEach { y ->
                                    Column(modifier = Modifier.width(115.dp), horizontalAlignment = Alignment.End) {
                                        Text(y.fyLabel, fontWeight = FontWeight.Bold, fontSize = 10.5.sp, color = tokens.text)
                                        Text(y.stageLabel.take(9), fontSize = 8.5.sp, color = tokens.textMuted)
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            MatrixSectionHeader("I. CURRENT ASSETS", tokens)
                            MatrixRow("Cash & Bank Balances", years.map { toLakhStr(it.cashAndBankPaise) }, isBold = false, tokens = tokens)
                            MatrixRow("Sundry Debtors (Receivables)", years.map { toLakhStr(it.sundryDebtorsPaise) }, isBold = false, tokens = tokens)
                            MatrixRow("Inventories (Raw Materials & Goods)", years.map { toLakhStr(it.inventoryStockPaise) }, isBold = false, tokens = tokens)
                            MatrixRow("Advances to Suppliers & Other CA", years.map { toLakhStr(it.advancesToSuppliersPaise + it.otherCurrentAssetsPaise) }, isBold = false, tokens = tokens)
                            MatrixRow("Total Current Assets (TCA)", years.map { toLakhStr(it.totalCurrentAssetsPaise) }, isBold = true, isAccent = true, tokens = tokens)

                            MatrixSectionHeader("II. FIXED & NON-CURRENT ASSETS", tokens)
                            MatrixRow("Gross Fixed Assets", years.map { toLakhStr(it.grossFixedAssetsPaise) }, isBold = false, tokens = tokens)
                            MatrixRow("Less: Accumulated Depreciation", years.map { "- ${toLakhStr(it.accumulatedDepreciationPaise)}" }, isBold = false, tokens = tokens)
                            MatrixRow("Net Fixed Assets (NFA)", years.map { toLakhStr(it.netFixedAssetsPaise) }, isBold = true, tokens = tokens)
                            MatrixRow("Other Non-Current Assets", years.map { toLakhStr(it.otherNonCurrentAssetsPaise) }, isBold = false, tokens = tokens)
                            MatrixRow("TOTAL ASSETS", years.map { toLakhStr(it.totalAssetsPaise) }, isBold = true, isAccent = true, tokens = tokens)

                            MatrixSectionHeader("III. CURRENT LIABILITIES", tokens)
                            MatrixRow("Sundry Creditors (Trade Payables)", years.map { toLakhStr(it.sundryCreditorsPaise) }, isBold = false, tokens = tokens)
                            MatrixRow("Other Current Liabilities & Provisions", years.map { toLakhStr(it.otherCurrentLiabilitiesPaise + it.provisionsPaise) }, isBold = false, tokens = tokens)
                            MatrixRow("Other Current Liabilities (OCL excl. Bank)", years.map { toLakhStr(it.currentLiabilitiesOtherThanBankPaise) }, isBold = true, tokens = tokens)
                            MatrixRow("Bank Short-Term Borrowing (CC Limit)", years.map { toLakhStr(it.existingBankCcLimitPaise) }, isBold = true, tokens = tokens)
                            MatrixRow("Total Current Liabilities (TCL)", years.map { toLakhStr(it.totalCurrentLiabilitiesPaise) }, isBold = true, isAccent = true, tokens = tokens)

                            MatrixSectionHeader("IV. TERM LIABILITIES & NET WORTH", tokens)
                            MatrixRow("Term Loans (repayable > 1 year)", years.map { toLakhStr(it.termLoanOutstandingPaise) }, isBold = false, tokens = tokens)
                            MatrixRow("Unsecured Loans (Promoter/Family)", years.map { toLakhStr(it.unsecuredLoansPaise) }, isBold = false, tokens = tokens)
                            MatrixRow("Total Outside Liabilities (TOL)", years.map { toLakhStr(it.totalOutsideLiabilitiesPaise) }, isBold = true, tokens = tokens)
                            MatrixRow("Paid-up Share Capital", years.map { toLakhStr(it.shareCapitalPaise) }, isBold = false, tokens = tokens)
                            MatrixRow("Reserves & Surplus", years.map { toLakhStr(it.reservesAndSurplusPaise) }, isBold = false, tokens = tokens)
                            MatrixRow("Tangible Net Worth (TNW)", years.map { toLakhStr(it.tangibleNetWorthPaise) }, isBold = true, isAccent = true, tokens = tokens)
                            MatrixRow("TOTAL LIABILITIES & CAPITAL", years.map { toLakhStr(it.totalLiabilitiesAndCapitalPaise) }, isBold = true, tokens = tokens)

                            MatrixSectionHeader("V. NET WORKING CAPITAL (NWC)", tokens)
                            MatrixRow("Net Working Capital (TCA - TCL)", years.map { toLakhStr(it.netWorkingCapitalPaise) }, isBold = true, isAccent = true, tokens = tokens)
                            MatrixRow("NWC to Net Sales (%)", years.map { String.format(Locale.ENGLISH, "%.1f%%", it.nwcToNetSalesPercent) }, isBold = false, tokens = tokens)
                        }
                    }
                }
            }
        }
    }
}

// ==================== TAB 3: FORM V MPBF COMPUTATION ====================
@Composable
fun FormVMpbfView(dossier: CmaProjectEngine.CmaProjectDossier, tokens: PabaruTokens) {
    val currentYear = dossier.years.firstOrNull { it.stage == CmaProjectEngine.CmaYearStage.CurrentYearEstimate } ?: dossier.years[1]
    val projYear = dossier.years.firstOrNull { it.stage == CmaProjectEngine.CmaYearStage.ProjectionYear1 } ?: dossier.years[2]

    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("cma_form_5_screen"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = tokens.card),
                border = BorderStroke(1.dp, tokens.cardBorder),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Form V: Maximum Permissible Bank Finance (MPBF)", fontWeight = FontWeight.Bold, fontSize = 13.5.sp, color = tokens.text)
                    Text("Comparative calculation across official RBI Lending Committees", fontSize = 10.sp, color = tokens.textMuted)
                }
            }
        }

        // Card 1: Tandon Method 1
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = tokens.card),
                border = BorderStroke(1.dp, tokens.cardBorder),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("1. Tandon Committee Method 1", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = tokens.text)
                        Surface(color = tokens.secondaryBg, shape = RoundedCornerShape(6.dp)) {
                            Text("75% of WCG", modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = tokens.accent)
                        }
                    }
                    Text("Borrower contributes 25% of Working Capital Gap from long-term sources; bank finances 75%.", fontSize = 10.sp, color = tokens.textMuted)

                    HorizontalDivider(color = tokens.cardBorder)

                    MpbfRow("Total Current Assets (TCA)", toLakhStr(currentYear.totalCurrentAssetsPaise), tokens)
                    MpbfRow("Less: Current Liabilities excl. Bank (OCL)", toLakhStr(currentYear.currentLiabilitiesOtherThanBankPaise), tokens)
                    MpbfRow("Working Capital Gap (WCG = TCA - OCL)", toLakhStr(currentYear.workingCapitalGapPaise), tokens, isBold = true)
                    MpbfRow("Min. Promoter Contribution (25% of WCG)", toLakhStr(currentYear.tandonMethod1MinNwcPaise), tokens)
                    MpbfRow("Eligible Bank Finance (MPBF Method 1)", toLakhStr(currentYear.mpbfTandonMethod1Paise), tokens, isBold = true, isAccent = true)
                }
            }
        }

        // Card 2: Tandon Method 2 (RBI Benchmark)
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = tokens.card),
                border = BorderStroke(1.dp, tokens.primary),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("2. Tandon Committee Method 2 (RBI Benchmark)", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = tokens.primary)
                        Surface(color = tokens.primary, shape = RoundedCornerShape(6.dp)) {
                            Text("Standard RBI Mandate", modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = tokens.primaryText)
                        }
                    }
                    Text("Borrower finances 25% of Total Current Assets from long-term funds (CR >= 1.33).", fontSize = 10.sp, color = tokens.textMuted)

                    HorizontalDivider(color = tokens.cardBorder)

                    MpbfRow("Total Current Assets (TCA)", toLakhStr(currentYear.totalCurrentAssetsPaise), tokens)
                    MpbfRow("Min. Promoter Contribution (25% of TCA)", toLakhStr(currentYear.tandonMethod2MinNwcPaise), tokens)
                    MpbfRow("Less: Current Liabilities excl. Bank (OCL)", toLakhStr(currentYear.currentLiabilitiesOtherThanBankPaise), tokens)
                    MpbfRow("Working Capital Gap (WCG)", toLakhStr(currentYear.workingCapitalGapPaise), tokens, isBold = true)
                    MpbfRow("Eligible Bank Finance (MPBF Method 2)", toLakhStr(currentYear.mpbfTandonMethod2Paise), tokens, isBold = true, isAccent = true)
                }
            }
        }

        // Card 3: Nayak Committee Turnover Method
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = tokens.card),
                border = BorderStroke(1.dp, tokens.cardBorder),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("3. Nayak Committee Turnover Method (MSME)", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = tokens.text)
                        Surface(color = tokens.secondaryBg, shape = RoundedCornerShape(6.dp)) {
                            Text("20% Turnover", modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = tokens.accent)
                        }
                    }
                    Text("Simplified method for MSME limits: 25% of turnover total WC, 5% promoter margin, 20% bank finance.", fontSize = 10.sp, color = tokens.textMuted)

                    HorizontalDivider(color = tokens.cardBorder)

                    MpbfRow("Projected Annual Net Turnover", toLakhStr(projYear.netSalesPaise), tokens)
                    MpbfRow("Total Working Capital Requirement (25%)", toLakhStr(projYear.nayakTotalWcRequirementPaise), tokens)
                    MpbfRow("Promoter Contribution / NWC (5%)", toLakhStr(projYear.nayakPromoterMarginPaise), tokens)
                    MpbfRow("Permissible Bank Finance (20% Turnover)", toLakhStr(projYear.nayakCommitteeWorkingCapitalPaise), tokens, isBold = true, isAccent = true)
                }
            }
        }
    }
}

@Composable
fun MpbfRow(label: String, value: String, tokens: PabaruTokens, isBold: Boolean = false, isAccent: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 11.sp, fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal, color = if (isAccent) tokens.accent else tokens.text)
        Text(value, fontSize = 11.5.sp, fontWeight = if (isBold) FontWeight.Bold else FontWeight.SemiBold, color = if (isAccent) tokens.accent else tokens.text)
    }
}

// ==================== TAB 4: FORM VI FUNDS FLOW STATEMENT ====================
@Composable
fun FormViFundsFlowView(fundsFlowSchedule: List<CmaProjectEngine.CmaFundsFlowYear>, tokens: PabaruTokens) {
    val hScroll = rememberScrollState()

    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("cma_form_6_screen"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = tokens.card),
                border = BorderStroke(1.dp, tokens.cardBorder),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Form VI: Funds Flow Statement (4-Year Comparative)", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = tokens.text)
                    Text("Tracking Long-Term Sources, Capital Applications, and Net Working Capital Movement", fontSize = 10.sp, color = tokens.textMuted)
                    Spacer(modifier = Modifier.height(10.dp))

                    Box(modifier = Modifier.fillMaxWidth().horizontalScroll(hScroll)) {
                        Column {
                            // Header Row
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(tokens.secondaryBg, RoundedCornerShape(6.dp))
                                    .padding(vertical = 6.dp, horizontal = 4.dp)
                            ) {
                                Text("Particulars (₹ in Lakhs)", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = tokens.accent, modifier = Modifier.width(230.dp))
                                fundsFlowSchedule.forEach { y ->
                                    Column(modifier = Modifier.width(115.dp), horizontalAlignment = Alignment.End) {
                                        Text(y.fyLabel, fontWeight = FontWeight.Bold, fontSize = 10.5.sp, color = tokens.text)
                                        Text(y.stageLabel.take(9), fontSize = 8.5.sp, color = tokens.textMuted)
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            MatrixSectionHeader("A. SOURCES OF FUNDS", tokens)
                            MatrixRow("Internal Cash Accruals (PAT + Depr)", fundsFlowSchedule.map { toLakhStr(it.cashAccrualsPaise) }, isBold = true, tokens = tokens)
                            MatrixRow("Capital Infusion by Promoters", fundsFlowSchedule.map { toLakhStr(it.capitalInfusionPaise) }, isBold = false, tokens = tokens)
                            MatrixRow("Increase in Bank Term Loans", fundsFlowSchedule.map { toLakhStr(it.increaseInTermLoanPaise) }, isBold = false, tokens = tokens)
                            MatrixRow("Increase in Unsecured Loans", fundsFlowSchedule.map { toLakhStr(it.increaseInUnsecuredLoansPaise) }, isBold = false, tokens = tokens)
                            MatrixRow("Total Sources of Funds", fundsFlowSchedule.map { toLakhStr(it.totalSourcesPaise) }, isBold = true, isAccent = true, tokens = tokens)

                            MatrixSectionHeader("B. APPLICATIONS OF FUNDS", tokens)
                            MatrixRow("Capital Expenditure (Fixed Assets additions)", fundsFlowSchedule.map { toLakhStr(it.capitalExpenditurePaise) }, isBold = false, tokens = tokens)
                            MatrixRow("Repayment of Term Loan Principal", fundsFlowSchedule.map { toLakhStr(it.repaymentOfTermLoanPaise) }, isBold = false, tokens = tokens)
                            MatrixRow("Decrease in Unsecured Borrowings", fundsFlowSchedule.map { toLakhStr(it.decreaseInUnsecuredLoansPaise) }, isBold = false, tokens = tokens)
                            MatrixRow("Promoter Drawings / Dividends", fundsFlowSchedule.map { toLakhStr(it.promoterDrawingsPaise) }, isBold = false, tokens = tokens)
                            MatrixRow("Total Applications of Funds", fundsFlowSchedule.map { toLakhStr(it.totalApplicationsPaise) }, isBold = true, tokens = tokens)

                            MatrixSectionHeader("C. SURPLUS / DEFICIT & NWC MOVEMENT", tokens)
                            MatrixRow("Long-Term Funds Net Surplus / (Deficit)", fundsFlowSchedule.map { toLakhStr(it.netSurplusOrDeficitPaise) }, isBold = true, isAccent = true, tokens = tokens)
                            MatrixRow("Opening Net Working Capital", fundsFlowSchedule.map { toLakhStr(it.openingNwcPaise) }, isBold = false, tokens = tokens)
                            MatrixRow("Closing Net Working Capital", fundsFlowSchedule.map { toLakhStr(it.closingNwcPaise) }, isBold = false, tokens = tokens)
                            MatrixRow("Movement in Net Working Capital (ΔNWC)", fundsFlowSchedule.map { toLakhStr(it.changeInNwcPaise) }, isBold = true, isAccent = true, tokens = tokens)
                        }
                    }
                }
            }
        }
    }
}

// ==================== TAB 5: FORM VII LOAN EMI AMORTIZATION ====================
@Composable
fun FormViiAmortizationView(
    schedule: CmaProjectEngine.TermLoanSchedule,
    projectReport: CmaProjectEngine.ProjectReportData,
    onUpdateTerms: (Long, Double, Int, Int) -> Unit,
    tokens: PabaruTokens,
    resetKey: Any = Unit
) {
    var loanAmountText by remember(resetKey) { mutableStateOf(paiseToRupeeInput(schedule.loanAmountPaise)) }
    var interestRateText by remember(resetKey) { mutableStateOf(schedule.annualRatePercent.toString()) }
    var tenorText by remember(resetKey) { mutableStateOf(schedule.tenorMonths.toString()) }
    var moratoriumText by remember(resetKey) { mutableStateOf(schedule.moratoriumMonths.toString()) }

    var showMonthlyBreakdown by remember { mutableStateOf(false) }

    fun commit() {
        val amt = FinancialEngine.parsePaiseFromRupees(loanAmountText)
        val rate = interestRateText.toDoubleOrNull() ?: 9.5
        val tenor = tenorText.toIntOrNull() ?: 60
        val mor = moratoriumText.toIntOrNull() ?: 6
        onUpdateTerms(amt, rate, tenor, mor)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("cma_form_7_screen"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Hero Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = tokens.card),
                border = BorderStroke(1.dp, tokens.cardBorder),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Form VII: Term Loan Monthly EMI Amortization Schedule", fontWeight = FontWeight.Bold, fontSize = 13.5.sp, color = tokens.text)
                    Text("Standard reducing balance schedule with interest-only moratorium period", fontSize = 10.sp, color = tokens.textMuted)

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = loanAmountText,
                            onValueChange = { loanAmountText = FinancialEngine.sanitizeDecimalInput(it); commit() },
                            label = { Text("Loan Amount (₹)", fontSize = 10.sp) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1.2f).testTag("cma_emi_input_amount")
                        )
                        OutlinedTextField(
                            value = interestRateText,
                            onValueChange = { interestRateText = FinancialEngine.sanitizeDecimalInput(it); commit() },
                            label = { Text("ROI (%)", fontSize = 10.sp) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(0.8f).testTag("cma_emi_input_rate")
                        )
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = tenorText,
                            onValueChange = { tenorText = it.filter { c -> c.isDigit() }; commit() },
                            label = { Text("Tenor (Months)", fontSize = 10.sp) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f).testTag("cma_emi_input_tenor")
                        )
                        OutlinedTextField(
                            value = moratoriumText,
                            onValueChange = { moratoriumText = it.filter { c -> c.isDigit() }; commit() },
                            label = { Text("Moratorium (Months)", fontSize = 10.sp) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f).testTag("cma_emi_input_moratorium")
                        )
                    }

                    HorizontalDivider(color = tokens.cardBorder)

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text("Monthly EMI", fontSize = 11.sp, color = tokens.textMuted)
                            Text(toLakhStr(schedule.monthlyEmiPaise), fontSize = 14.sp, fontWeight = FontWeight.Black, color = tokens.primary)
                        }
                        Column {
                            Text("Total Interest", fontSize = 11.sp, color = tokens.textMuted)
                            Text(toLakhStr(schedule.totalInterestPayablePaise), fontSize = 14.sp, fontWeight = FontWeight.Black, color = tokens.text)
                        }
                        Column {
                            Text("Total Outflow", fontSize = 11.sp, color = tokens.textMuted)
                            Text(toLakhStr(schedule.totalPaymentPaise), fontSize = 14.sp, fontWeight = FontWeight.Black, color = tokens.accent)
                        }
                    }
                }
            }
        }

        // Annual Rollup Summary Table
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = tokens.card),
                border = BorderStroke(1.dp, tokens.cardBorder),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("Annual Repayment Rollup (Principal & Interest Split)", fontWeight = FontWeight.Bold, fontSize = 12.5.sp, color = tokens.text)
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(tokens.secondaryBg, RoundedCornerShape(6.dp))
                            .padding(vertical = 6.dp, horizontal = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Year", fontWeight = FontWeight.Bold, fontSize = 10.sp, color = tokens.textMuted, modifier = Modifier.weight(0.7f))
                        Text("Opening (₹)", fontWeight = FontWeight.Bold, fontSize = 10.sp, color = tokens.textMuted, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
                        Text("Principal (₹)", fontWeight = FontWeight.Bold, fontSize = 10.sp, color = tokens.textMuted, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
                        Text("Interest (₹)", fontWeight = FontWeight.Bold, fontSize = 10.sp, color = tokens.textMuted, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
                        Text("Closing (₹)", fontWeight = FontWeight.Bold, fontSize = 10.sp, color = tokens.textMuted, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
                    }

                    schedule.annualSummaries.forEach { row ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp, horizontal = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Year ${row.yearIndex}", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = tokens.text, modifier = Modifier.weight(0.7f))
                            Text(toLakhStr(row.openingPrincipalPaise), fontSize = 10.sp, color = tokens.text, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
                            Text(toLakhStr(row.totalPrincipalRepaidPaise), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = tokens.primary, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
                            Text(toLakhStr(row.totalInterestPaidPaise), fontSize = 10.sp, color = tokens.accent, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
                            Text(toLakhStr(row.closingPrincipalPaise), fontSize = 10.sp, color = tokens.text, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }

        // Toggle to View Monthly Breakdown
        item {
            OutlinedButton(
                onClick = { showMonthlyBreakdown = !showMonthlyBreakdown },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("cma_toggle_monthly_schedule"),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(if (showMonthlyBreakdown) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text(if (showMonthlyBreakdown) "Hide Full 60-Month Schedule" else "Show Full 60-Month Amortization Schedule")
            }
        }

        if (showMonthlyBreakdown) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = tokens.card),
                    border = BorderStroke(1.dp, tokens.cardBorder),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text("Month-by-Month Amortization Table", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = tokens.text)
                        Spacer(modifier = Modifier.height(6.dp))

                        schedule.monthlySchedule.forEach { m ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Mo ${m.monthNumber}${if (m.isMoratorium) " (Mora)" else ""}", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = if (m.isMoratorium) tokens.accent else tokens.text)
                                Text("EMI: ${toLakhStr(m.emiPaise)}", fontSize = 10.sp, color = tokens.text)
                                Text("Prin: ${toLakhStr(m.principalPaise)}", fontSize = 10.sp, color = tokens.primary)
                                Text("Int: ${toLakhStr(m.interestPaise)}", fontSize = 10.sp, color = tokens.textMuted)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ==================== TAB 6: 19 RATIOS & APPRAISAL ====================
@Composable
fun LoanRatiosScorecardView(
    dossier: CmaProjectEngine.CmaProjectDossier,
    feedbackList: List<CmaProjectEngine.BankerFeedbackItem>,
    tokens: PabaruTokens
) {
    val currentYear = dossier.years.firstOrNull { it.stage == CmaProjectEngine.CmaYearStage.CurrentYearEstimate } ?: dossier.years[1]

    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("cma_ratios_screen"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = tokens.card),
                border = BorderStroke(1.dp, tokens.cardBorder),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("19 Key Banking Ratios vs. RBI Benchmarks", fontWeight = FontWeight.Black, fontSize = 14.sp, color = tokens.text)
                    Text("Audited and projected financial health scorecard for bank loan sanction", fontSize = 10.5.sp, color = tokens.textMuted)
                }
            }
        }

        // Section 1: Liquidity & Solvency Ratios
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = tokens.card),
                border = BorderStroke(1.dp, tokens.cardBorder),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("I. Liquidity & Coverage Ratios", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = tokens.text)

                    RatioScorecardRow("Current Ratio (TCA / TCL)", String.format(Locale.ENGLISH, "%.2f", currentYear.currentRatio), "≥ 1.33 (Tandon)", currentYear.currentRatio >= 1.33, tokens)
                    RatioScorecardRow("Quick / Acid-Test Ratio", String.format(Locale.ENGLISH, "%.2f", currentYear.quickRatio), "≥ 1.00", currentYear.quickRatio >= 1.00, tokens)
                    RatioScorecardRow("Absolute Liquid Ratio", String.format(Locale.ENGLISH, "%.2f", currentYear.absoluteLiquidRatio), "≥ 0.50", currentYear.absoluteLiquidRatio >= 0.50, tokens)
                    RatioScorecardRow("Debt Service Coverage Ratio (DSCR)", String.format(Locale.ENGLISH, "%.2f", currentYear.dscr), "1.50 - 2.00x", currentYear.dscr >= 1.40, tokens)
                    RatioScorecardRow("Interest Coverage Ratio (ICR)", String.format(Locale.ENGLISH, "%.2f", currentYear.interestCoverageRatio), "≥ 2.50x", currentYear.interestCoverageRatio >= 2.00, tokens)
                }
            }
        }

        // Section 2: Capital Structure & Solvency
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = tokens.card),
                border = BorderStroke(1.dp, tokens.cardBorder),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("II. Capital Structure & Solvency Ratios", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = tokens.text)

                    RatioScorecardRow("Debt-Equity Ratio (DER)", String.format(Locale.ENGLISH, "%.2f : 1", currentYear.debtEquityRatio), "≤ 2.00 : 1", currentYear.debtEquityRatio <= 2.50, tokens)
                    RatioScorecardRow("TOL / Tangible Net Worth", String.format(Locale.ENGLISH, "%.2f : 1", currentYear.tolToTnwRatio), "≤ 3.00 : 1", currentYear.tolToTnwRatio <= 3.50, tokens)
                    RatioScorecardRow("Proprietary Ratio", String.format(Locale.ENGLISH, "%.2f", currentYear.proprietaryRatio), "≥ 0.30", currentYear.proprietaryRatio >= 0.30, tokens)
                    RatioScorecardRow("Fixed Assets to Long-Term Funds", String.format(Locale.ENGLISH, "%.2f", currentYear.fixedAssetsToLongTermFundsRatio), "≤ 1.00", currentYear.fixedAssetsToLongTermFundsRatio <= 1.00, tokens)
                }
            }
        }

        // Section 3: Turnover & Activity
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = tokens.card),
                border = BorderStroke(1.dp, tokens.cardBorder),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("III. Activity & Operating Cycle (Days)", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = tokens.text)

                    RatioScorecardRow("Debtor Velocity (Collection Period)", "${currentYear.debtorDays} days", "≤ 60 days", currentYear.debtorDays <= 75, tokens)
                    RatioScorecardRow("Creditor Velocity (Payment Period)", "${currentYear.creditorDays} days", "30 - 60 days", true, tokens)
                    RatioScorecardRow("Inventory Holding Period", "${currentYear.inventoryDays} days", "≤ 60 days", currentYear.inventoryDays <= 75, tokens)
                    RatioScorecardRow("Gross Operating Cycle", "${currentYear.grossOperatingCycleDays} days", "≤ 120 days", true, tokens)
                    RatioScorecardRow("Net Working Capital Cycle", "${currentYear.netWorkingCapitalCycleDays} days", "≤ 90 days", true, tokens)
                }
            }
        }

        // Section 4: Profitability & Returns
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = tokens.card),
                border = BorderStroke(1.dp, tokens.cardBorder),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("IV. Profitability & Returns", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = tokens.text)

                    RatioScorecardRow("Gross Profit Margin (%)", String.format(Locale.ENGLISH, "%.1f%%", currentYear.grossProfitMarginPercent), "≥ 15.0%", currentYear.grossProfitMarginPercent >= 12.0, tokens)
                    RatioScorecardRow("EBITDA Margin (%)", String.format(Locale.ENGLISH, "%.1f%%", currentYear.operatingEbitdaMarginPercent), "≥ 8.0%", currentYear.operatingEbitdaMarginPercent >= 7.0, tokens)
                    RatioScorecardRow("Net Profit Margin (PAT %)", String.format(Locale.ENGLISH, "%.1f%%", currentYear.netProfitMarginPercent), "≥ 4.0%", currentYear.netProfitMarginPercent >= 3.0, tokens)
                    RatioScorecardRow("Return on Capital Employed (ROCE %)", String.format(Locale.ENGLISH, "%.1f%%", currentYear.returnOnCapitalEmployedPercent), "≥ 15.0%", currentYear.returnOnCapitalEmployedPercent >= 12.0, tokens)
                    RatioScorecardRow("NWC to Net Sales (%)", String.format(Locale.ENGLISH, "%.1f%%", currentYear.nwcToNetSalesPercent), "15 - 25%", true, tokens)
                }
            }
        }

        // Section 5: Banker Improvement Insights
        item {
            Text("Credit Officer Feedback & Recommendations", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = tokens.text)
        }

        items(feedbackList) { item ->
            Card(
                colors = CardDefaults.cardColors(containerColor = tokens.card),
                border = BorderStroke(1.dp, tokens.cardBorder),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(item.title, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = tokens.text)
                        val statusColor = when (item.status) {
                            CmaProjectEngine.FeedbackStatus.Healthy -> tokens.success
                            CmaProjectEngine.FeedbackStatus.Acceptable -> tokens.info
                            CmaProjectEngine.FeedbackStatus.Caution -> tokens.warning
                            CmaProjectEngine.FeedbackStatus.Critical -> tokens.danger
                        }
                        Surface(color = statusColor.copy(alpha = 0.15f), shape = RoundedCornerShape(6.dp)) {
                            Text(item.status.name, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = statusColor)
                        }
                    }
                    Text("Benchmark: ${item.benchmark} | Current: ${item.currentValue}", fontSize = 10.sp, color = tokens.textMuted)
                    Text(item.recommendation, fontSize = 10.5.sp, color = tokens.text)
                }
            }
        }
    }
}

@Composable
fun RatioScorecardRow(label: String, value: String, benchmark: String, isHealthy: Boolean, tokens: PabaruTokens) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1.5f)) {
            Text(label, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = tokens.text)
            Text("RBI Norm: $benchmark", fontSize = 9.sp, color = tokens.textMuted)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                value,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = if (isHealthy) tokens.success else tokens.danger
            )
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                if (isHealthy) Icons.Default.CheckCircle else Icons.Default.Warning,
                contentDescription = null,
                tint = if (isHealthy) tokens.success else tokens.danger,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

// ==================== TAB 7: FILLABLE YEAR EDITOR ====================
@Composable
fun FillableYearEditorView(
    dossier: CmaProjectEngine.CmaProjectDossier,
    editingYearIndex: Int,
    onSelectYearIndex: (Int) -> Unit,
    onUpdateYear: (CmaProjectEngine.CmaYearFinancials) -> Unit,
    onAutoPopulateCurrentYear: () -> Unit,
    onAutoProjectNext2Years: () -> Unit,
    tokens: PabaruTokens,
    resetKey: Any = Unit
) {
    val activeYear = dossier.years.getOrNull(editingYearIndex) ?: dossier.years[1]
    val stateKey = Pair(editingYearIndex, resetKey)

    var salesText by remember(stateKey) { mutableStateOf(paiseToRupeeInput(activeYear.grossSalesPaise)) }
    var returnsText by remember(stateKey) { mutableStateOf(paiseToRupeeInput(activeYear.salesReturnsAndDeductionsPaise)) }
    var purchasesText by remember(stateKey) { mutableStateOf(paiseToRupeeInput(activeYear.rawMaterialPurchasesPaise)) }
    var wagesText by remember(stateKey) { mutableStateOf(paiseToRupeeInput(activeYear.directWagesPaise)) }
    var powerText by remember(stateKey) { mutableStateOf(paiseToRupeeInput(activeYear.directPowerAndFreightPaise)) }

    var salariesText by remember(stateKey) { mutableStateOf(paiseToRupeeInput(activeYear.salariesAndAdminPaise)) }
    var rentText by remember(stateKey) { mutableStateOf(paiseToRupeeInput(activeYear.rentAndUtilitiesPaise)) }
    var marketingText by remember(stateKey) { mutableStateOf(paiseToRupeeInput(activeYear.sellingAndMarketingPaise)) }

    var depreciationText by remember(stateKey) { mutableStateOf(paiseToRupeeInput(activeYear.depreciationPaise)) }
    var tlInterestText by remember(stateKey) { mutableStateOf(paiseToRupeeInput(activeYear.termLoanInterestPaise)) }
    var ccInterestText by remember(stateKey) { mutableStateOf(paiseToRupeeInput(activeYear.workingCapitalInterestPaise)) }
    var tlPrincipalText by remember(stateKey) { mutableStateOf(paiseToRupeeInput(activeYear.termLoanPrincipalInstallmentPaise)) }

    var cashBankText by remember(stateKey) { mutableStateOf(paiseToRupeeInput(activeYear.cashAndBankPaise)) }
    var debtorsText by remember(stateKey) { mutableStateOf(paiseToRupeeInput(activeYear.sundryDebtorsPaise)) }
    var stockText by remember(stateKey) { mutableStateOf(paiseToRupeeInput(activeYear.inventoryStockPaise)) }
    var creditorsText by remember(stateKey) { mutableStateOf(paiseToRupeeInput(activeYear.sundryCreditorsPaise)) }
    var otherClText by remember(stateKey) { mutableStateOf(paiseToRupeeInput(activeYear.otherCurrentLiabilitiesPaise)) }
    var netWorthText by remember(stateKey) { mutableStateOf(paiseToRupeeInput(activeYear.tangibleNetWorthPaise)) }
    var termLoanDebtText by remember(stateKey) { mutableStateOf(paiseToRupeeInput(activeYear.termLoanOutstandingPaise)) }

    fun commitChanges() {
        val parsedNetWorth = FinancialEngine.parsePaiseFromRupees(netWorthText)
        val updated = activeYear.copy(
            grossSalesPaise = FinancialEngine.parsePaiseFromRupees(salesText),
            salesReturnsAndDeductionsPaise = FinancialEngine.parsePaiseFromRupees(returnsText),
            rawMaterialPurchasesPaise = FinancialEngine.parsePaiseFromRupees(purchasesText),
            directWagesPaise = FinancialEngine.parsePaiseFromRupees(wagesText),
            directPowerAndFreightPaise = FinancialEngine.parsePaiseFromRupees(powerText),
            salariesAndAdminPaise = FinancialEngine.parsePaiseFromRupees(salariesText),
            rentAndUtilitiesPaise = FinancialEngine.parsePaiseFromRupees(rentText),
            sellingAndMarketingPaise = FinancialEngine.parsePaiseFromRupees(marketingText),
            depreciationPaise = FinancialEngine.parsePaiseFromRupees(depreciationText),
            termLoanInterestPaise = FinancialEngine.parsePaiseFromRupees(tlInterestText),
            workingCapitalInterestPaise = FinancialEngine.parsePaiseFromRupees(ccInterestText),
            termLoanPrincipalInstallmentPaise = FinancialEngine.parsePaiseFromRupees(tlPrincipalText),
            cashAndBankPaise = FinancialEngine.parsePaiseFromRupees(cashBankText),
            sundryDebtorsPaise = FinancialEngine.parsePaiseFromRupees(debtorsText),
            inventoryStockPaise = FinancialEngine.parsePaiseFromRupees(stockText),
            sundryCreditorsPaise = FinancialEngine.parsePaiseFromRupees(creditorsText),
            otherCurrentLiabilitiesPaise = FinancialEngine.parsePaiseFromRupees(otherClText),
            shareCapitalPaise = activeYear.shareCapitalPaise,
            reservesAndSurplusPaise = (parsedNetWorth - activeYear.shareCapitalPaise).coerceAtLeast(0L),
            termLoanOutstandingPaise = FinancialEngine.parsePaiseFromRupees(termLoanDebtText)
        )
        onUpdateYear(updated)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("cma_fillable_year_screen"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            // Select Year Segmented Row
            Text("Select Year to Edit & Customize:", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = tokens.text)
            Spacer(modifier = Modifier.height(6.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                dossier.years.forEachIndexed { idx, y ->
                    Button(
                        onClick = {
                            commitChanges()
                            onSelectYearIndex(idx)
                        },
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("cma_select_year_btn_$idx"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (editingYearIndex == idx) tokens.primary else tokens.secondaryBg
                        ),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(4.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(y.fyLabel, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = if (editingYearIndex == idx) tokens.primaryText else tokens.text)
                            Text(y.stageLabel.take(9), fontSize = 8.sp, color = if (editingYearIndex == idx) tokens.primaryText.copy(alpha = 0.8f) else tokens.textMuted)
                        }
                    }
                }
            }
        }

        item {
            // Quick Automation Bar
            Card(
                colors = CardDefaults.cardColors(containerColor = tokens.card),
                border = BorderStroke(1.dp, tokens.cardBorder),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = onAutoPopulateCurrentYear,
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("cma_auto_seed_btn"),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(6.dp)
                    ) {
                        Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(14.dp), tint = tokens.accent)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Auto-Seed from Ledger", fontSize = 10.sp, color = tokens.accent)
                    }

                    Button(
                        onClick = onAutoProjectNext2Years,
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("cma_auto_project_btn"),
                        colors = ButtonDefaults.buttonColors(containerColor = tokens.primary),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(6.dp)
                    ) {
                        Icon(Icons.Default.TrendingUp, contentDescription = null, modifier = Modifier.size(14.dp), tint = tokens.primaryText)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Auto-Project Next 2 Yrs", fontSize = 10.sp, color = tokens.primaryText)
                    }
                }
            }
        }

        item {
            // Operating Statement Inputs
            Card(
                colors = CardDefaults.cardColors(containerColor = tokens.card),
                border = BorderStroke(1.dp, tokens.cardBorder),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("1. Operating Statement Inputs (${activeYear.fyLabel})", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = tokens.text)

                    CmaNumericField("Gross Sales / Invoiced Turnover (₹)", salesText) { salesText = it; commitChanges() }
                    CmaNumericField("Sales Returns & Deductions (₹)", returnsText) { returnsText = it; commitChanges() }
                    CmaNumericField("Raw Material Purchases (₹)", purchasesText) { purchasesText = it; commitChanges() }
                    CmaNumericField("Direct Wages & Labor (₹)", wagesText) { wagesText = it; commitChanges() }
                    CmaNumericField("Power, Fuel & Freight Inward (₹)", powerText) { powerText = it; commitChanges() }
                    CmaNumericField("Salaries & Administrative Exp. (₹)", salariesText) { salariesText = it; commitChanges() }
                    CmaNumericField("Rent & Utilities (₹)", rentText) { rentText = it; commitChanges() }
                    CmaNumericField("Selling & Marketing (₹)", marketingText) { marketingText = it; commitChanges() }
                    CmaNumericField("Depreciation (₹)", depreciationText) { depreciationText = it; commitChanges() }
                    CmaNumericField("Term Loan Interest (₹)", tlInterestText) { tlInterestText = it; commitChanges() }
                    CmaNumericField("Working Capital / CC Interest (₹)", ccInterestText) { ccInterestText = it; commitChanges() }
                    CmaNumericField("Term Loan Principal Installment (₹)", tlPrincipalText) { tlPrincipalText = it; commitChanges() }
                }
            }
        }

        item {
            // Working Capital & Balance Sheet Inputs
            Card(
                colors = CardDefaults.cardColors(containerColor = tokens.card),
                border = BorderStroke(1.dp, tokens.cardBorder),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("2. Balance Sheet & Working Capital (${activeYear.fyLabel})", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = tokens.text)

                    CmaNumericField("Cash & Bank Balances (₹)", cashBankText) { cashBankText = it; commitChanges() }
                    CmaNumericField("Sundry Debtors / Receivables (₹)", debtorsText) { debtorsText = it; commitChanges() }
                    CmaNumericField("Inventory / Stocks (₹)", stockText) { stockText = it; commitChanges() }
                    CmaNumericField("Sundry Creditors / Payables (₹)", creditorsText) { creditorsText = it; commitChanges() }
                    CmaNumericField("Other Current Liabilities (₹)", otherClText) { otherClText = it; commitChanges() }
                    CmaNumericField("Tangible Net Worth (Capital + Reserves) (₹)", netWorthText) { netWorthText = it; commitChanges() }
                    CmaNumericField("Term Loan Debt Outstanding (₹)", termLoanDebtText) { termLoanDebtText = it; commitChanges() }
                }
            }
        }
    }
}

@Composable
fun CmaNumericField(label: String, value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { onValueChange(FinancialEngine.sanitizeDecimalInput(it)) },
        label = { Text(label, fontSize = 11.sp) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
fun MatrixSectionHeader(title: String, tokens: PabaruTokens) {
    Surface(
        color = tokens.secondaryBg,
        shape = RoundedCornerShape(6.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
    ) {
        Text(title, fontWeight = FontWeight.Bold, fontSize = 10.sp, color = tokens.accent, modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp))
    }
}

@Composable
fun MatrixRow(
    label: String,
    values: List<String>,
    isBold: Boolean,
    isAccent: Boolean = false,
    tokens: PabaruTokens
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            fontSize = 10.5.sp,
            fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal,
            color = if (isAccent) tokens.accent else if (isBold) tokens.text else tokens.textMuted,
            modifier = Modifier.width(230.dp)
        )
        values.forEach { v ->
            Text(
                v,
                fontSize = 10.sp,
                fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal,
                color = if (isAccent) tokens.accent else tokens.text,
                textAlign = TextAlign.End,
                modifier = Modifier.width(115.dp)
            )
        }
    }
}
