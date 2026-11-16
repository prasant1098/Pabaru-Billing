package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.BasicTextField
import androidx.activity.compose.BackHandler
import com.example.engine.BankReconEngine
import com.example.engine.FinancialEngine
import com.example.model.BankAccount
import com.example.model.BankReconItem
import com.example.model.PaymentMode
import com.example.model.TransactionType
import com.example.model.UpiQrCode
import com.example.ui.theme.PabaruTheme
import com.example.ui.theme.danger
import com.example.ui.theme.dangerContainer
import com.example.ui.theme.info
import com.example.ui.theme.infoContainer
import com.example.ui.theme.success
import com.example.ui.theme.successContainer
import com.example.ui.theme.warning
import com.example.ui.theme.warningContainer
import com.example.viewmodel.PabaruUiState
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import kotlin.math.abs

private fun formatInr(paise: Long): String {
    val rupees = paise / 100.0
    val symbols = DecimalFormatSymbols(Locale.forLanguageTag("en-IN")).apply {
        currencySymbol = "₹"
    }
    val df = DecimalFormat("₹#,##,##0.00", symbols)
    return df.format(rupees)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CashBankScreen(
    uiState: PabaruUiState,
    onRunReconciliation: () -> Unit,
    onImportStatement: () -> Unit,
    initialSubTab: Int = 0,
    // Callback hooks for manager PIN, QR & bank operations
    onSetManagerPin: (String, Boolean) -> Unit = { _, _ -> },
    onVerifyManagerPin: (String) -> Boolean = { false },
    onUnlockWithFingerprint: () -> Unit = {},
    onLockManager: () -> Unit = {},
    onResetManagerPin: (String) -> Unit = {},
    onAddQr: (UpiQrCode) -> Unit = {},
    onUpdateQr: (UpiQrCode) -> Unit = {},
    onDeleteQr: (String) -> Unit = {},
    onSetDefaultQr: (String) -> Unit = {},
    onToggleQrEnabled: (String) -> Unit = {},
    onAddBankAccount: (BankAccount) -> Unit = {},
    onAdjustCash: (Long, Boolean, String) -> Unit = { _, _, _ -> },
    onAdjustCashPaise: (Long, Boolean, String) -> Unit = { paise, isAdd, note -> onAdjustCash(paise / 100L, isAdd, note) },
    onTransfer: (String, String, Long, String) -> Unit = { _, _, _, _ -> },
    onTransferPaise: (String, String, Long, String) -> Unit = { from, to, paise, note -> onTransfer(from, to, paise / 100L, note) },
    onReconcileSelected: (Set<String>) -> Unit = {},
    onConfirmMatch: (String) -> Unit = {},
    onConfirmAllMatches: (Int) -> Unit = {},
    onManualMatch: (String, String) -> Unit = { _, _ -> },
    onUnlinkMatch: (String) -> Unit = {},
    onResolveDiscrepancy: (String, BankReconEngine.DiscrepancyResolutionType, String?, String) -> Unit = { _, _, _, _ -> },
    onImportStatementText: (String, String, String) -> Unit = { _, _, _ -> },
    onImportSampleStatement: (String, String) -> Unit = { _, _ -> },
    onClearStatement: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val tokens = PabaruTheme.tokens
    var activeTab by remember(initialSubTab) { mutableStateOf(initialSubTab) } // 0: Accounts, 1: Reconcile
    var showAllActivity by remember { mutableStateOf(false) }

    // Sheet states
    var showShowQrSheet by remember { mutableStateOf(false) }
    var showStaffRestrictedSheet by remember { mutableStateOf(false) }
    var showFirstTimePinSetup by remember { mutableStateOf(false) }
    var showPinCheckSheet by remember { mutableStateOf(false) }
    var showForgotPinSheet by remember { mutableStateOf(false) }
    var isManageQrScreenOpen by remember { mutableStateOf(false) }
    var isReconUnlocked by remember { mutableStateOf(false) }

    var showAdjustCashSheet by remember { mutableStateOf(false) }
    var showAddBankSheet by remember { mutableStateOf(false) }
    var showTransferSheet by remember { mutableStateOf(false) }
    var selectedAccountForDetail by remember { mutableStateOf<BankAccount?>(null) }
    var showFabMenu by remember { mutableStateOf(false) }

    // Reconcile Tab states
    var selectedReconBankId by remember { mutableStateOf(uiState.bankAccounts.firstOrNull()?.id ?: "") }
    LaunchedEffect(uiState.bankAccounts) {
        if (selectedReconBankId.isBlank() || uiState.bankAccounts.none { it.id == selectedReconBankId }) {
            selectedReconBankId = uiState.bankAccounts.firstOrNull()?.id ?: ""
        }
    }
    var bankDropdownExpanded by remember { mutableStateOf(false) }
    var bankActionsMenuExpanded by remember { mutableStateOf(false) }

    var showUploadSheet by remember { mutableStateOf(false) }
    var manualMatchTargetItem by remember { mutableStateOf<BankReconItem?>(null) }
    var resolveDiscrepancyTargetItem by remember { mutableStateOf<BankReconItem?>(null) }
    var reconFilterStatus by remember { mutableStateOf("ALL") } // ALL, NEEDS_REVIEW, AUTO_MATCHED, UNMATCHED, RECONCILED
    var reconSearchQuery by remember { mutableStateOf("") }

    // Resolve Default QR and Linked Bank
    val defaultQr = uiState.upiQrs.find { it.isDefault } ?: uiState.upiQrs.firstOrNull()
    val defaultBank = defaultQr?.let { qr -> uiState.bankAccounts.find { it.id == qr.linkedBankId } }
        ?: uiState.bankAccounts.find { it.isDefaultForQr }
        ?: uiState.bankAccounts.firstOrNull()

    // Compute real-time cash and bank balances (aggregate + per-account)
    val computedFin = remember(uiState.transactions, uiState.bankAccounts, uiState.profile, uiState.parties) {
        FinancialEngine.calculateFyFinancials(uiState, "All Time")
    }
    val liveBankBalances = remember(uiState.bankAccounts, uiState.transactions) {
        val balances = uiState.bankAccounts.associate { it.id to it.openingBalancePaise }.toMutableMap()
        val fallbackBankId = uiState.bankAccounts.firstOrNull()?.id
        uiState.transactions.forEach { t ->
            if (!t.isVoided && (t.paymentMode == PaymentMode.Bank || t.paymentMode == PaymentMode.UPI || t.paymentMode == PaymentMode.Cheque)) {
                val targetId = t.bankAccountId?.takeIf { balances.containsKey(it) } ?: fallbackBankId
                if (targetId != null) {
                    val delta = when (t.type) {
                        TransactionType.PaymentIn,
                        TransactionType.Sale,
                        TransactionType.NonGstBill,
                        TransactionType.DebitNote -> t.amountPaise
                        TransactionType.PaymentOut,
                        TransactionType.Purchase,
                        TransactionType.Expense,
                        TransactionType.CreditNote,
                        TransactionType.Drawing -> -t.amountPaise
                        TransactionType.Quotation -> 0L
                    }
                    balances[targetId] = (balances[targetId] ?: 0L) + delta
                }
            }
        }
        balances
    }
    val cashPaise = computedFin.cashInHandPaise
    val bankPaise = computedFin.bankTotalPaise
    val totalAvailablePaise = cashPaise + bankPaise

    val todayStr = FinancialEngine.getTodayDate()
    val todayCashNetPaise = remember(uiState.transactions, todayStr) {
        var net = 0L
        uiState.transactions.forEach { t ->
            if (!t.isVoided && t.paymentMode == PaymentMode.Cash && FinancialEngine.normalizeDate(t.date) == todayStr) {
                when (t.type) {
                    TransactionType.PaymentIn,
                    TransactionType.Sale,
                    TransactionType.NonGstBill,
                    TransactionType.DebitNote -> net += t.amountPaise
                    TransactionType.PaymentOut,
                    TransactionType.Purchase,
                    TransactionType.Expense,
                    TransactionType.CreditNote,
                    TransactionType.Drawing -> net -= t.amountPaise
                    TransactionType.Quotation -> {}
                }
            }
        }
        net
    }

    val partiesMap = remember(uiState.parties) { uiState.parties.associateBy { it.id } }

    val cashBankActivity = remember(uiState.transactions, partiesMap) {
        uiState.transactions.filter { t ->
            !t.isVoided &&
                t.type != TransactionType.Quotation &&
                t.paymentMode != PaymentMode.Credit
        }
    }

    BackHandler(enabled = isManageQrScreenOpen || activeTab != 0 || showAdjustCashSheet || showAddBankSheet || showTransferSheet || selectedAccountForDetail != null) {
        when {
            isManageQrScreenOpen -> isManageQrScreenOpen = false
            showAdjustCashSheet -> showAdjustCashSheet = false
            showAddBankSheet -> showAddBankSheet = false
            showTransferSheet -> showTransferSheet = false
            selectedAccountForDetail != null -> selectedAccountForDetail = null
            activeTab != 0 -> activeTab = 0
        }
    }

    // Handle Manage QR tap based on role & PIN status
    fun handleManageQrTap() {
        if (uiState.currentUserRole == "staff") {
            showStaffRestrictedSheet = true
        } else {
            if (uiState.isManagerUnlocked) {
                isManageQrScreenOpen = true
            } else if (uiState.managerPinHash == null) {
                showFirstTimePinSetup = true
            } else {
                showPinCheckSheet = true
            }
        }
    }

    if (isManageQrScreenOpen) {
        ManageQrScreen(
            uiState = uiState,
            onBack = { isManageQrScreenOpen = false },
            onLockNow = {
                onLockManager()
                isManageQrScreenOpen = false
                Toast.makeText(context, "Manager mode locked", Toast.LENGTH_SHORT).show()
            },
            onAddQr = onAddQr,
            onUpdateQr = onUpdateQr,
            onDeleteQr = onDeleteQr,
            onSetDefaultQr = onSetDefaultQr,
            onToggleQrEnabled = onToggleQrEnabled
        )
        return
    }

    if (activeTab == 1 && !isReconUnlocked) {
        PremiumFeatureComingSoonScreen(
            featureType = PremiumFeatureType.BANK_RECONCILIATION,
            onBack = { activeTab = 0 },
            businessName = uiState.profile.businessName,
            onUnlockDemo = { isReconUnlocked = true }
        )
        return
    }

    val allBankItems = remember(uiState.bankReconItems, selectedReconBankId) {
        uiState.bankReconItems.filter { selectedReconBankId.isBlank() || it.bankAccountId == selectedReconBankId }
    }
    val filteredBankItems = remember(allBankItems, reconFilterStatus, reconSearchQuery) {
        val q = reconSearchQuery.trim().lowercase(Locale.ROOT)
        allBankItems.filter { item ->
            val statusMatches = when (reconFilterStatus) {
                "NEEDS_REVIEW" -> item.matchedTxnId != null && !item.isUserConfirmed && item.matchConfidence in 50..79
                "AUTO_MATCHED" -> item.matchedTxnId != null && !item.isUserConfirmed && item.matchConfidence >= 80
                "UNMATCHED" -> item.matchedTxnId == null && !item.isReconciled
                "RECONCILED" -> item.isReconciled
                else -> true
            }
            if (!statusMatches) return@filter false
            if (q.isEmpty()) return@filter true

            item.description.lowercase(Locale.ROOT).contains(q) ||
                    item.referenceNo.lowercase(Locale.ROOT).contains(q) ||
                    (item.amountPaise / 100.0).toString().contains(q) ||
                    item.matchReason.lowercase(Locale.ROOT).contains(q)
        }
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
                contentPadding = PaddingValues(top = 12.dp, bottom = 88.dp)
            ) {
                // ==========================================
                // PAGE TITLE (20px, medium)
                // ==========================================
                item {
                    Text(
                        text = "Cash and bank",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Medium,
                        color = tokens.text,
                        modifier = Modifier.padding(top = 2.dp, bottom = 2.dp)
                    )
                }

                // ==========================================
                // ACCOUNTS HEADER
                // ==========================================
                item {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = tokens.successContainer,
                        border = BorderStroke(
                            width = 0.5.dp,
                            color = tokens.success
                        )
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "Cash & Bank Accounts",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = tokens.text
                            )
                        }
                    }
                }

                if (activeTab == 0) {
                    // ==========================================
                    // ACCOUNTS TAB CONTENT
                    // ==========================================

                    // 1. Total Balance Card
                    item {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            color = tokens.card,
                            border = BorderStroke(0.5.dp, tokens.cardBorder)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp)
                            ) {
                                Text(
                                    text = "Total available",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Normal,
                                    color = tokens.textMuted
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = formatInr(totalAvailablePaise),
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = if (totalAvailablePaise >= 0L) tokens.success else tokens.danger
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Cash ${formatInr(cashPaise)} · Bank ${formatInr(bankPaise)}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Normal,
                                    color = tokens.textMuted
                                )
                            }
                        }
                    }

                    // 2. Cash In Hand Card
                    item {
                        val todayNetSign = if (todayCashNetPaise >= 0L) "+" else "−"
                        val todayNetText = "$todayNetSign${formatInr(abs(todayCashNetPaise))}"
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            color = tokens.card,
                            border = BorderStroke(0.5.dp, tokens.cardBorder)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = "Cash in hand",
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = tokens.text
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Box(
                                            modifier = Modifier
                                                .size(6.dp)
                                                .clip(CircleShape)
                                                .background(tokens.success)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "Live",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = tokens.success
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = formatInr(cashPaise),
                                        fontSize = 22.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = if (cashPaise >= 0L) tokens.success else tokens.danger
                                    )
                                    Spacer(modifier = Modifier.height(3.dp))
                                    Text(
                                        text = "Opening ${formatInr(uiState.profile.openingCashBalancePaise)} · Today $todayNetText",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Normal,
                                        color = tokens.textMuted
                                    )
                                }

                                Surface(
                                    modifier = Modifier
                                        .height(48.dp)
                                        .clickable { showAdjustCashSheet = true },
                                    shape = RoundedCornerShape(12.dp),
                                    color = tokens.secondaryBg,
                                    border = BorderStroke(0.5.dp, tokens.cardBorder)
                                ) {
                                    Box(
                                        modifier = Modifier.padding(horizontal = 14.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "Adjust cash",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = tokens.text
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 3. UPI QR Card
                    item {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            color = tokens.card,
                            border = BorderStroke(0.5.dp, tokens.cardBorder)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = "Counter UPI QR",
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = tokens.text
                                        )
                                        Text(
                                            text = defaultQr?.label ?: "No counter UPI QR configured",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Normal,
                                            color = tokens.textMuted
                                        )
                                    }
                                    if (defaultQr != null) {
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = tokens.successContainer,
                                            border = BorderStroke(0.5.dp, tokens.success)
                                        ) {
                                            Text(
                                                text = "Default",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = tokens.success,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }

                                if (defaultQr != null) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .heightIn(min = 48.dp)
                                            .clickable { copyToClipboard(context, defaultQr.upiId, "UPI ID") }
                                            .padding(vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = defaultQr.upiId,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = tokens.textMuted
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Icon(
                                            imageVector = Icons.Outlined.ContentCopy,
                                            contentDescription = "Copy UPI ID",
                                            tint = tokens.textMuted,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }

                                    Text(
                                        text = if (defaultBank != null) "Linked to ${defaultBank.bankName}" else "Not linked to a bank account",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Normal,
                                        color = tokens.textMuted
                                    )
                                }

                                HorizontalDivider(thickness = 0.5.dp, color = tokens.cardBorder)

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    if (defaultQr != null) {
                                        Surface(
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(48.dp)
                                                .clickable { showShowQrSheet = true },
                                            shape = RoundedCornerShape(12.dp),
                                            color = tokens.secondaryBg,
                                            border = BorderStroke(0.5.dp, tokens.cardBorder)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxSize(),
                                                horizontalArrangement = Arrangement.Center,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(Icons.Outlined.QrCode2, contentDescription = null, tint = tokens.text, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("Show QR", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = tokens.text)
                                            }
                                        }
                                    }

                                    Surface(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(48.dp)
                                            .clickable { handleManageQrTap() },
                                        shape = RoundedCornerShape(12.dp),
                                        color = tokens.secondaryBg,
                                        border = BorderStroke(0.5.dp, tokens.cardBorder)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxSize(),
                                            horizontalArrangement = Arrangement.Center,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            if (uiState.currentUserRole == "staff") {
                                                Icon(Icons.Outlined.Lock, contentDescription = "Lock", tint = tokens.textMuted, modifier = Modifier.size(16.dp))
                                            } else {
                                                Icon(Icons.Outlined.Settings, contentDescription = "Settings", tint = tokens.textMuted, modifier = Modifier.size(16.dp))
                                            }
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                if (defaultQr == null) "Set up QR" else "Manage QR",
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = tokens.text
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 4. Bank Accounts Section
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Bank accounts (${uiState.bankAccounts.size})",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = tokens.textMuted
                            )
                            Box(
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .clickable { showAddBankSheet = true }
                                    .padding(horizontal = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "+ Add",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = tokens.primary
                                )
                            }
                        }
                    }

                    // Bank Account Cards
                    items(uiState.bankAccounts, key = { it.id }) { acc ->
                        val isDefaultAccount = acc.isDefaultForQr || acc.id == defaultBank?.id
                        val initials = when {
                            acc.bankName.contains("HDFC", true) -> "HDFC"
                            acc.bankName.contains("State Bank", true) || acc.bankName.contains("SBI", true) -> "SBI"
                            acc.bankName.contains("ICICI", true) -> "ICICI"
                            acc.bankName.contains("Axis", true) -> "AXIS"
                            else -> acc.bankName.trim().take(4).uppercase()
                        }

                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedAccountForDetail = acc },
                            shape = RoundedCornerShape(12.dp),
                            color = tokens.card,
                            border = BorderStroke(0.5.dp, tokens.cardBorder)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f).padding(end = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        modifier = Modifier.size(40.dp),
                                        shape = RoundedCornerShape(10.dp),
                                        color = tokens.secondaryBg,
                                        border = BorderStroke(0.5.dp, tokens.cardBorder)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                text = initials,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = tokens.text
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(10.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = acc.bankName,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = tokens.text,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            if (isDefaultAccount) {
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Surface(
                                                    shape = RoundedCornerShape(4.dp),
                                                    color = tokens.successContainer,
                                                    border = BorderStroke(0.5.dp, tokens.success)
                                                ) {
                                                    Text(
                                                        text = "Default",
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Medium,
                                                        color = tokens.success,
                                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                                    )
                                                }
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = "Account ••••${acc.accountNumber.takeLast(4)}",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Normal,
                                            color = tokens.textMuted
                                        )
                                    }
                                }

                                val liveBankBal = liveBankBalances[acc.id] ?: acc.openingBalancePaise
                                Text(
                                    text = formatInr(liveBankBal),
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = if (liveBankBal >= 0L) tokens.text else tokens.danger,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    }

                    // 5. Recent Activity Section (real Cash/Bank/UPI ledger entries)
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Recent activity (${cashBankActivity.size})",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = tokens.textMuted
                            )
                            if (cashBankActivity.size > 5) {
                                Box(
                                    modifier = Modifier
                                        .heightIn(min = 48.dp)
                                        .clickable { showAllActivity = !showAllActivity }
                                        .padding(horizontal = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = if (showAllActivity) "Show less" else "View all",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = tokens.link
                                    )
                                }
                            }
                        }
                    }

                    if (cashBankActivity.isEmpty()) {
                        item {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                color = tokens.card,
                                border = BorderStroke(0.5.dp, tokens.cardBorder)
                            ) {
                                Box(
                                    modifier = Modifier.fillMaxWidth().padding(20.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "No cash or bank activity recorded yet",
                                        fontSize = 12.sp,
                                        color = tokens.textMuted
                                    )
                                }
                            }
                        }
                    } else {
                        val visibleActivity = if (showAllActivity) cashBankActivity else cashBankActivity.take(5)
                        items(visibleActivity, key = { it.id }) { txn ->
                            val isCredit = txn.type == TransactionType.PaymentIn ||
                                txn.type == TransactionType.Sale ||
                                txn.type == TransactionType.NonGstBill ||
                                txn.type == TransactionType.DebitNote
                            val partyName = txn.partyId?.let { partiesMap[it]?.name }
                            val desc = txn.remarks.ifBlank {
                                "${txn.type.name}${if (partyName != null) " · $partyName" else ""}"
                            }
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                color = tokens.card,
                                border = BorderStroke(0.5.dp, tokens.cardBorder)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                        Text(
                                            text = desc,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = tokens.text,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = "${txn.date} · ${txn.paymentMode.name}",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Normal,
                                            color = tokens.textMuted
                                        )
                                    }

                                    Text(
                                        text = (if (isCredit) "+" else "−") + formatInr(txn.amountPaise),
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = if (isCredit) tokens.success else tokens.danger,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                } else {
                    // ==========================================
                    // RECONCILE TAB CONTENT (Automated Bank Reconciliation)
                    // ==========================================
                    val currentBank = uiState.bankAccounts.find { it.id == selectedReconBankId } ?: uiState.bankAccounts.firstOrNull()

                    val autoMatchCount = allBankItems.count { it.matchedTxnId != null && !it.isUserConfirmed && it.matchConfidence >= 80 }
                    val needsReviewCount = allBankItems.count { it.matchedTxnId != null && !it.isUserConfirmed && it.matchConfidence in 50..79 }
                    val unmatchedCount = allBankItems.count { it.matchedTxnId == null && !it.isReconciled }
                    val reconciledCount = allBankItems.count { it.isReconciled }

                    val bookBalancePaise = currentBank?.let { liveBankBalances[it.id] ?: it.openingBalancePaise } ?: 0L
                    val unreconciledItems = allBankItems.filter { !it.isReconciled }
                    val diffPaise = unreconciledItems.sumOf { if (it.isCredit) it.amountPaise else -it.amountPaise }
                    val bankBalancePaise = bookBalancePaise + diffPaise

                    // 1. Bank Account Selector & Primary Reconciliation Actions
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Reconcile Bank Feed", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = tokens.text)
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Surface(
                                        modifier = Modifier
                                            .height(48.dp)
                                            .clickable { showUploadSheet = true },
                                        shape = RoundedCornerShape(8.dp),
                                        color = tokens.warningContainer,
                                        border = BorderStroke(0.5.dp, tokens.warning)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Icon(Icons.Outlined.UploadFile, contentDescription = null, tint = tokens.warning, modifier = Modifier.size(14.dp))
                                            Text("Import Statement", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = tokens.warning)
                                        }
                                    }

                                    Box {
                                        IconButton(
                                            onClick = { bankActionsMenuExpanded = true },
                                            modifier = Modifier.size(48.dp)
                                        ) {
                                            Icon(Icons.Outlined.MoreVert, contentDescription = "More actions", tint = tokens.textMuted, modifier = Modifier.size(18.dp))
                                        }

                                        DropdownMenu(
                                            expanded = bankActionsMenuExpanded,
                                            onDismissRequest = { bankActionsMenuExpanded = false },
                                            modifier = Modifier.background(tokens.card).border(0.5.dp, tokens.cardBorder, RoundedCornerShape(12.dp))
                                        ) {
                                            DropdownMenuItem(
                                                leadingIcon = { Icon(Icons.Outlined.AutoFixHigh, contentDescription = null, tint = tokens.success, modifier = Modifier.size(16.dp)) },
                                                text = { Text("Re-run Auto-Match", fontSize = 13.sp, color = tokens.text) },
                                                onClick = {
                                                    bankActionsMenuExpanded = false
                                                    onRunReconciliation()
                                                    Toast.makeText(context, "Running automated bank matching engine...", Toast.LENGTH_SHORT).show()
                                                }
                                            )
                                            DropdownMenuItem(
                                                leadingIcon = { Icon(Icons.Outlined.Description, contentDescription = null, tint = tokens.warning, modifier = Modifier.size(16.dp)) },
                                                text = { Text("Load Sample CSV", fontSize = 13.sp, color = tokens.text) },
                                                onClick = {
                                                    bankActionsMenuExpanded = false
                                                    onImportSampleStatement("CSV", currentBank?.id ?: "")
                                                    Toast.makeText(context, "Loaded sample CSV statement", Toast.LENGTH_SHORT).show()
                                                }
                                            )
                                            DropdownMenuItem(
                                                leadingIcon = { Icon(Icons.Outlined.Description, contentDescription = null, tint = tokens.info, modifier = Modifier.size(16.dp)) },
                                                text = { Text("Load Sample OFX", fontSize = 13.sp, color = tokens.text) },
                                                onClick = {
                                                    bankActionsMenuExpanded = false
                                                    onImportSampleStatement("OFX", currentBank?.id ?: "")
                                                    Toast.makeText(context, "Loaded sample OFX statement", Toast.LENGTH_SHORT).show()
                                                }
                                            )
                                            HorizontalDivider(thickness = 0.5.dp, color = tokens.cardBorder)
                                            DropdownMenuItem(
                                                leadingIcon = { Icon(Icons.Outlined.DeleteOutline, contentDescription = null, tint = tokens.danger, modifier = Modifier.size(16.dp)) },
                                                text = { Text("Clear Statement Feed", fontSize = 13.sp, color = tokens.danger) },
                                                onClick = {
                                                    bankActionsMenuExpanded = false
                                                    onClearStatement(currentBank?.id ?: "")
                                                    Toast.makeText(context, "Cleared statement entries", Toast.LENGTH_SHORT).show()
                                                }
                                            )
                                        }
                                    }
                                }
                            }

                            Box {
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .clickable { bankDropdownExpanded = true },
                                    shape = RoundedCornerShape(10.dp),
                                    color = tokens.card,
                                    border = BorderStroke(0.5.dp, tokens.cardBorder)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Icon(Icons.Outlined.AccountBalance, contentDescription = null, tint = tokens.success, modifier = Modifier.size(16.dp))
                                            Text(
                                                text = currentBank?.let { "${it.bankName} (••••${it.accountNumber.takeLast(4)})" } ?: "Select bank",
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = tokens.text
                                            )
                                        }
                                        Icon(Icons.Outlined.ArrowDropDown, contentDescription = null, tint = tokens.textMuted)
                                    }
                                }

                                DropdownMenu(
                                    expanded = bankDropdownExpanded,
                                    onDismissRequest = { bankDropdownExpanded = false },
                                    modifier = Modifier.background(tokens.card).border(0.5.dp, tokens.cardBorder, RoundedCornerShape(12.dp))
                                ) {
                                    uiState.bankAccounts.forEach { acc ->
                                        DropdownMenuItem(
                                            text = { Text("${acc.bankName} (••••${acc.accountNumber.takeLast(4)})", fontSize = 13.sp, color = tokens.text) },
                                            onClick = {
                                                selectedReconBankId = acc.id
                                                bankDropdownExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 2. Summary Card: Book balance, Bank balance, Difference & Match Progress
                    item {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            color = tokens.card,
                            border = BorderStroke(0.5.dp, tokens.cardBorder)
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Book balance", fontSize = 11.sp, color = tokens.textMuted)
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(formatInr(bookBalancePaise), fontSize = 14.sp, fontWeight = FontWeight.Medium, color = tokens.text)
                                    }

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Bank statement", fontSize = 11.sp, color = tokens.textMuted)
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(formatInr(bankBalancePaise), fontSize = 14.sp, fontWeight = FontWeight.Medium, color = tokens.text)
                                    }

                                    Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                                        Text("Difference", fontSize = 11.sp, color = tokens.textMuted)
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = formatInr(abs(diffPaise)),
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = if (diffPaise == 0L) tokens.success else tokens.danger
                                        )
                                    }
                                }

                                HorizontalDivider(thickness = 0.5.dp, color = tokens.cardBorder)

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Icon(
                                            imageVector = if (diffPaise == 0L) Icons.Outlined.CheckCircle else Icons.Outlined.Info,
                                            contentDescription = null,
                                            tint = if (diffPaise == 0L) tokens.success else tokens.warning,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = if (diffPaise == 0L) "Statements and ledger in sync" else "$unmatchedCount unreconciled item(s) pending review",
                                            fontSize = 11.sp,
                                            color = if (diffPaise == 0L) tokens.success else tokens.textMuted
                                        )
                                    }

                                    Text(
                                        text = "$reconciledCount / ${allBankItems.size} Reconciled",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = tokens.text
                                    )
                                }
                            }
                        }
                    }

                    // 3. Batch Auto-Match Confirmation Banner
                    if (autoMatchCount > 0) {
                        item {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                color = tokens.successContainer,
                                border = BorderStroke(0.5.dp, tokens.success)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        modifier = Modifier.weight(1f).padding(end = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(Icons.Outlined.AutoFixHigh, contentDescription = null, tint = tokens.success, modifier = Modifier.size(20.dp))
                                        Column {
                                            Text(
                                                text = "$autoMatchCount Exact Matches Detected",
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = tokens.success
                                            )
                                            Text(
                                                text = "Matched by exact amount, date & payee",
                                                fontSize = 11.sp,
                                                color = tokens.textMuted
                                            )
                                        }
                                    }

                                    Surface(
                                        modifier = Modifier
                                            .height(48.dp)
                                            .clickable {
                                                onConfirmAllMatches(80)
                                                Toast.makeText(context, "Confirmed all auto-matches", Toast.LENGTH_SHORT).show()
                                            },
                                        shape = RoundedCornerShape(8.dp),
                                        color = tokens.success
                                    ) {
                                        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 12.dp)) {
                                            Text("Confirm All", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = tokens.bg)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 4. Filter Chips
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            val filterOptions = listOf(
                                "ALL" to "All (${allBankItems.size})",
                                "NEEDS_REVIEW" to "Review ($needsReviewCount)",
                                "AUTO_MATCHED" to "Matched ($autoMatchCount)",
                                "UNMATCHED" to "Unmatched ($unmatchedCount)",
                                "RECONCILED" to "Done ($reconciledCount)"
                            )

                            filterOptions.forEach { (statusKey, label) ->
                                val isSelected = reconFilterStatus == statusKey
                                Surface(
                                    modifier = Modifier
                                        .weight(1f)
                                        .heightIn(min = 48.dp)
                                        .clickable { reconFilterStatus = statusKey },
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isSelected) tokens.warningContainer else tokens.card,
                                    border = BorderStroke(0.5.dp, if (isSelected) tokens.warning else tokens.cardBorder)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = label,
                                            fontSize = 10.sp,
                                            fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
                                            color = if (isSelected) tokens.warning else tokens.textMuted,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 5. Search Bar for Entries
                    if (allBankItems.isNotEmpty()) {
                        item {
                            Surface(
                                modifier = Modifier.fillMaxWidth().height(48.dp),
                                shape = RoundedCornerShape(10.dp),
                                color = tokens.card,
                                border = BorderStroke(0.5.dp, tokens.cardBorder)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Outlined.Search, contentDescription = null, tint = tokens.textMuted, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    BasicTextField(
                                        value = reconSearchQuery,
                                        onValueChange = { reconSearchQuery = it },
                                        cursorBrush = SolidColor(tokens.primary),
                                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp, color = tokens.text),
                                        modifier = Modifier.fillMaxWidth(),
                                        decorationBox = { inner ->
                                            if (reconSearchQuery.isEmpty()) {
                                                Text("Search description, reference, amount...", fontSize = 12.sp, color = tokens.textMuted)
                                            }
                                            inner()
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // 6. Statement Entries List or Empty State
                    if (allBankItems.isEmpty()) {
                        item {
                            Surface(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                                shape = RoundedCornerShape(12.dp),
                                color = tokens.card,
                                border = BorderStroke(0.5.dp, tokens.cardBorder)
                            ) {
                                Column(
                                    modifier = Modifier.padding(20.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(Icons.Outlined.AccountBalanceWallet, contentDescription = null, tint = tokens.warning, modifier = Modifier.size(40.dp))
                                    Text(
                                        text = "No Bank Statement Loaded",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = tokens.text
                                    )
                                    Text(
                                        text = "Upload bank statements in CSV or OFX formats to automatically match payments, deposits, and vendor payouts.",
                                        fontSize = 12.sp,
                                        color = tokens.textMuted,
                                        textAlign = TextAlign.Center
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Surface(
                                            modifier = Modifier
                                                .height(48.dp)
                                                .clickable { showUploadSheet = true },
                                            shape = RoundedCornerShape(10.dp),
                                            color = tokens.primary
                                        ) {
                                            Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 14.dp)) {
                                                Text("Upload Statement", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = tokens.primaryText)
                                            }
                                        }

                                        Surface(
                                            modifier = Modifier
                                                .height(48.dp)
                                                .clickable {
                                                    onImportSampleStatement("CSV", currentBank?.id ?: "")
                                                    Toast.makeText(context, "Loaded sample CSV statement", Toast.LENGTH_SHORT).show()
                                                },
                                            shape = RoundedCornerShape(10.dp),
                                            color = tokens.secondaryBg,
                                            border = BorderStroke(0.5.dp, tokens.cardBorder)
                                        ) {
                                            Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 14.dp)) {
                                                Text("Try Sample", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = tokens.text)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } else if (filteredBankItems.isEmpty()) {
                        item {
                            Surface(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp),
                                shape = RoundedCornerShape(12.dp),
                                color = tokens.card,
                                border = BorderStroke(0.5.dp, tokens.cardBorder)
                            ) {
                                Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                                    Text("No entries match the selected filter", fontSize = 13.sp, color = tokens.textMuted)
                                }
                            }
                        }
                    } else {
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Statement Entries (${filteredBankItems.size})",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = tokens.textMuted
                                )
                                Text(
                                    text = "Tap to review or resolve",
                                    fontSize = 11.sp,
                                    color = tokens.textMuted
                                )
                            }
                        }

                        items(filteredBankItems, key = { it.id }) { item ->
                            val isCredit = item.isCredit
                            val isReconciled = item.isReconciled
                            val matchedTxn = item.matchedTxnId?.let { id -> uiState.transactions.find { it.id == id } }
                            val matchedParty = matchedTxn?.partyId?.let { partiesMap[it] }

                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                color = tokens.card,
                                border = BorderStroke(
                                    width = 0.5.dp,
                                    color = if (isReconciled) tokens.success.copy(alpha = 0.4f)
                                    else if (item.matchedTxnId != null) tokens.warning.copy(alpha = 0.4f)
                                    else tokens.cardBorder
                                )
                            ) {
                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        when {
                                            isReconciled -> {
                                                Surface(shape = RoundedCornerShape(4.dp), color = tokens.successContainer, border = BorderStroke(0.5.dp, tokens.success)) {
                                                    Row(modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                                        Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = tokens.success, modifier = Modifier.size(11.dp))
                                                        Text("Reconciled", fontSize = 10.sp, fontWeight = FontWeight.Medium, color = tokens.success)
                                                    }
                                                }
                                            }
                                            item.matchedTxnId != null && item.matchConfidence >= 80 -> {
                                                Surface(shape = RoundedCornerShape(4.dp), color = tokens.successContainer, border = BorderStroke(0.5.dp, tokens.success)) {
                                                    Text("Exact Match (${item.matchConfidence}%)", fontSize = 10.sp, fontWeight = FontWeight.Medium, color = tokens.success, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                                }
                                            }
                                            item.matchedTxnId != null -> {
                                                Surface(shape = RoundedCornerShape(4.dp), color = tokens.warningContainer, border = BorderStroke(0.5.dp, tokens.warning)) {
                                                    Text("Probable (${item.matchConfidence}%)", fontSize = 10.sp, fontWeight = FontWeight.Medium, color = tokens.warning, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                                }
                                            }
                                            else -> {
                                                Surface(shape = RoundedCornerShape(4.dp), color = tokens.dangerContainer, border = BorderStroke(0.5.dp, tokens.danger)) {
                                                    Text("Unmatched", fontSize = 10.sp, fontWeight = FontWeight.Medium, color = tokens.danger, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                                }
                                            }
                                        }

                                        Text(
                                            text = item.date,
                                            fontSize = 11.sp,
                                            color = tokens.textMuted
                                        )

                                        Text(
                                            text = (if (isCredit) "+" else "−") + formatInr(item.amountPaise),
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = if (isCredit) tokens.success else tokens.danger
                                        )
                                    }

                                    Column {
                                        Text(
                                            text = item.description,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = tokens.text
                                        )
                                        if (item.referenceNo.isNotBlank()) {
                                            Text(
                                                text = "Ref: ${item.referenceNo}",
                                                fontSize = 11.sp,
                                                color = tokens.textMuted
                                            )
                                        }
                                    }

                                    if (item.matchedTxnId != null) {
                                        Surface(
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(8.dp),
                                            color = tokens.secondaryBg,
                                            border = BorderStroke(0.5.dp, tokens.cardBorder)
                                        ) {
                                            Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(
                                                        text = "Ledger: ${matchedTxn?.type?.name ?: "Entry"} · ${matchedTxn?.invoiceNumber?.ifBlank { matchedTxn.id } ?: item.matchedTxnId}",
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Medium,
                                                        color = tokens.info
                                                    )
                                                    Text(
                                                        text = matchedTxn?.let { formatInr(it.amountPaise) } ?: "",
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Medium,
                                                        color = tokens.text
                                                    )
                                                }

                                                Text(
                                                    text = matchedParty?.name ?: matchedTxn?.remarks?.ifBlank { "Ledger transaction" } ?: "Transaction ${item.matchedTxnId}",
                                                    fontSize = 12.sp,
                                                    color = tokens.text,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )

                                                if (item.matchReason.isNotBlank()) {
                                                    Text(
                                                        text = item.matchReason,
                                                        fontSize = 10.sp,
                                                        color = tokens.textMuted
                                                    )
                                                }
                                            }
                                        }
                                    } else {
                                        Surface(
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(8.dp),
                                            color = tokens.dangerContainer,
                                            border = BorderStroke(0.5.dp, tokens.danger.copy(alpha = 0.4f))
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(8.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Icon(Icons.Outlined.ErrorOutline, contentDescription = null, tint = tokens.danger, modifier = Modifier.size(14.dp))
                                                Text(
                                                    text = item.discrepancyNote.ifBlank { "No matching ledger transaction found in application" },
                                                    fontSize = 11.sp,
                                                    color = tokens.textMuted
                                                )
                                            }
                                        }
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.End,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        if (item.matchedTxnId != null) {
                                            if (!item.isUserConfirmed) {
                                                TextButton(
                                                    onClick = { manualMatchTargetItem = item },
                                                    contentPadding = PaddingValues(horizontal = 8.dp),
                                                    modifier = Modifier.heightIn(min = 48.dp)
                                                ) {
                                                    Text("Change", fontSize = 12.sp, color = tokens.textMuted)
                                                }
                                                IconButton(
                                                    onClick = {
                                                        onUnlinkMatch(item.id)
                                                        Toast.makeText(context, "Unlinked match", Toast.LENGTH_SHORT).show()
                                                    },
                                                    modifier = Modifier.size(48.dp)
                                                ) {
                                                    Icon(Icons.Outlined.LinkOff, contentDescription = "Unlink", tint = tokens.textMuted, modifier = Modifier.size(16.dp))
                                                }
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Surface(
                                                    modifier = Modifier
                                                        .height(48.dp)
                                                        .clickable {
                                                            onConfirmMatch(item.id)
                                                            Toast.makeText(context, "Match confirmed", Toast.LENGTH_SHORT).show()
                                                        },
                                                    shape = RoundedCornerShape(8.dp),
                                                    color = tokens.success
                                                ) {
                                                    Row(
                                                        modifier = Modifier.padding(horizontal = 10.dp),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                    ) {
                                                        Icon(Icons.Outlined.Check, contentDescription = null, tint = tokens.bg, modifier = Modifier.size(14.dp))
                                                        Text("Confirm Match", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = tokens.bg)
                                                    }
                                                }
                                            } else {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Text("Confirmed", fontSize = 11.sp, color = tokens.success)
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    IconButton(
                                                        onClick = {
                                                            onUnlinkMatch(item.id)
                                                            Toast.makeText(context, "Unlinked match", Toast.LENGTH_SHORT).show()
                                                        },
                                                        modifier = Modifier.size(48.dp)
                                                    ) {
                                                        Icon(Icons.Outlined.LinkOff, contentDescription = "Unlink", tint = tokens.textMuted, modifier = Modifier.size(16.dp))
                                                    }
                                                }
                                            }
                                        } else {
                                            Surface(
                                                modifier = Modifier
                                                    .height(48.dp)
                                                    .clickable { manualMatchTargetItem = item },
                                                shape = RoundedCornerShape(8.dp),
                                                color = tokens.secondaryBg,
                                                border = BorderStroke(0.5.dp, tokens.cardBorder)
                                            ) {
                                                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 10.dp)) {
                                                    Text("Match Manually", fontSize = 12.sp, color = tokens.text)
                                                }
                                            }

                                            Spacer(modifier = Modifier.width(8.dp))

                                            Surface(
                                                modifier = Modifier
                                                    .height(48.dp)
                                                    .clickable { resolveDiscrepancyTargetItem = item },
                                                shape = RoundedCornerShape(8.dp),
                                                color = tokens.primary
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 10.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                ) {
                                                    Icon(Icons.Outlined.Build, contentDescription = null, tint = tokens.primaryText, modifier = Modifier.size(13.dp))
                                                    Text("Resolve", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = tokens.primaryText)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // ==========================================
        // FLOATING ACTION BUTTON (Accounts tab only)
        // ==========================================
        if (activeTab == 0) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = 16.dp)
            ) {
                FloatingActionButton(
                    onClick = { showFabMenu = !showFabMenu },
                    shape = CircleShape,
                    containerColor = tokens.primary,
                    contentColor = tokens.primaryText,
                    elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 0.dp),
                    modifier = Modifier.size(56.dp)
                ) {
                    Icon(
                        imageVector = if (showFabMenu) Icons.Outlined.Close else Icons.Outlined.Add,
                        contentDescription = "Quick actions",
                        modifier = Modifier.size(24.dp)
                    )
                }

                DropdownMenu(
                    expanded = showFabMenu,
                    onDismissRequest = { showFabMenu = false },
                    modifier = Modifier
                        .background(tokens.card)
                        .border(0.5.dp, tokens.cardBorder, RoundedCornerShape(12.dp))
                ) {
                    DropdownMenuItem(
                        text = { Text("Add bank account", fontSize = 13.sp, color = tokens.text) },
                        onClick = {
                            showFabMenu = false
                            showAddBankSheet = true
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Adjust cash", fontSize = 13.sp, color = tokens.text) },
                        onClick = {
                            showFabMenu = false
                            showAdjustCashSheet = true
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Transfer between accounts", fontSize = 13.sp, color = tokens.text) },
                        onClick = {
                            showFabMenu = false
                            showTransferSheet = true
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Add QR", fontSize = 13.sp, color = tokens.primary) },
                        onClick = {
                            showFabMenu = false
                            handleManageQrTap()
                        }
                    )
                }
            }
        }
    }

    // ==========================================
    // BOTTOM SHEETS & DIALOGS
    // ==========================================

    // 1. Show QR Sheet
    if (showShowQrSheet && defaultQr != null) {
        ShowQrBottomSheet(
            qr = defaultQr,
            bankName = defaultBank?.bankName ?: "Unlinked bank account",
            onDismiss = { showShowQrSheet = false }
        )
    }

    // 2. Staff Restricted Sheet
    if (showStaffRestrictedSheet) {
        StaffRestrictedQrSheet(onDismiss = { showStaffRestrictedSheet = false })
    }

    // 3. First-time PIN setup
    if (showFirstTimePinSetup) {
        FirstTimePinSetupSheet(
            onDismiss = { showFirstTimePinSetup = false },
            onPinCreated = { pin, enableFingerprint ->
                onSetManagerPin(pin, enableFingerprint)
                showFirstTimePinSetup = false
                isManageQrScreenOpen = true
            }
        )
    }

    // 4. PIN check sheet
    if (showPinCheckSheet) {
        ManagerPinCheckSheet(
            uiState = uiState,
            onDismiss = { showPinCheckSheet = false },
            onVerifyPin = { pin ->
                val ok = onVerifyManagerPin(pin)
                if (ok) {
                    showPinCheckSheet = false
                    isManageQrScreenOpen = true
                }
                ok
            },
            onUnlockWithFingerprint = {
                onUnlockWithFingerprint()
                showPinCheckSheet = false
                isManageQrScreenOpen = true
            },
            onForgotPin = {
                showPinCheckSheet = false
                showForgotPinSheet = true
            }
        )
    }

    // 5. Forgot PIN Sheet
    if (showForgotPinSheet) {
        ForgotPinSheet(
            onDismiss = { showForgotPinSheet = false },
            onResetConfirm = {
                showForgotPinSheet = false
                showFirstTimePinSetup = true
            }
        )
    }

    // 6. Adjust Cash Sheet
    if (showAdjustCashSheet) {
        AdjustCashBottomSheet(
            onDismiss = { showAdjustCashSheet = false },
            onSavePaise = { paise, isAdd, note ->
                onAdjustCashPaise(paise, isAdd, note)
                Toast.makeText(context, "Cash drawer updated", Toast.LENGTH_SHORT).show()
                showAdjustCashSheet = false
            }
        )
    }

    // 7. Add Bank Account Sheet
    if (showAddBankSheet) {
        AddBankAccountBottomSheet(
            onDismiss = { showAddBankSheet = false },
            onSave = { acc ->
                onAddBankAccount(acc)
                Toast.makeText(context, "Added ${acc.bankName}", Toast.LENGTH_SHORT).show()
                showAddBankSheet = false
            }
        )
    }

    // 8. Transfer Sheet
    if (showTransferSheet) {
        TransferBottomSheet(
            bankAccounts = uiState.bankAccounts,
            onDismiss = { showTransferSheet = false },
            onTransferPaise = { from, to, paise, note ->
                onTransferPaise(from, to, paise, note)
                Toast.makeText(context, "Transfer completed", Toast.LENGTH_SHORT).show()
                showTransferSheet = false
            }
        )
    }

    // 9. Account Detail Sheet
    selectedAccountForDetail?.let { acc ->
        AccountDetailBottomSheet(
            account = acc,
            liveBalancePaise = liveBankBalances[acc.id] ?: acc.openingBalancePaise,
            onDismiss = { selectedAccountForDetail = null }
        )
    }

    // 10. Bank Statement Upload / Import Sheet
    if (showUploadSheet) {
        val targetBank = uiState.bankAccounts.find { it.id == selectedReconBankId }
            ?: uiState.bankAccounts.firstOrNull()
            ?: BankAccount("BNK-PRIMARY", "Primary Bank Account", "Primary Bank", "", "", "", 0L, false)
        UploadBankStatementSheet(
            targetBank = targetBank,
            onDismiss = { showUploadSheet = false },
            onImportText = { text, bankId, bankName ->
                onImportStatementText(text, bankId, bankName)
                showUploadSheet = false
            },
            onImportSample = { format, bankId ->
                onImportSampleStatement(format, bankId)
                showUploadSheet = false
            }
        )
    }

    // 11. Manual Match Sheet
    manualMatchTargetItem?.let { item ->
        ManualMatchSheet(
            statementItem = item,
            transactions = uiState.transactions,
            parties = uiState.parties,
            onDismiss = { manualMatchTargetItem = null },
            onConfirmMatch = { stmtId, txnId ->
                onManualMatch(stmtId, txnId)
                Toast.makeText(context, "Entry manually matched and reconciled", Toast.LENGTH_SHORT).show()
                manualMatchTargetItem = null
            }
        )
    }

    // 12. Resolve Discrepancy Sheet
    resolveDiscrepancyTargetItem?.let { item ->
        ResolveDiscrepancySheet(
            statementItem = item,
            parties = uiState.parties,
            onDismiss = { resolveDiscrepancyTargetItem = null },
            onResolve = { stmtId, resType, partyId, remarks ->
                onResolveDiscrepancy(stmtId, resType, partyId, remarks)
                Toast.makeText(context, "Created balancing transaction & reconciled", Toast.LENGTH_SHORT).show()
                resolveDiscrepancyTargetItem = null
            }
        )
    }
}
