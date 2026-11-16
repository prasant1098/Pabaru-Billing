package com.example.ui.screens

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.engine.ExportEngine
import com.example.engine.FinancialEngine
import com.example.model.*
import com.example.ui.theme.*
import com.example.viewmodel.PabaruUiState
import java.time.LocalDate
import java.time.temporal.ChronoUnit

// Helper for date formatting e.g. "2026-10-01" -> "1 Oct"
private fun formatDisplayDate(dateStr: String): String {
    val parsed = FinancialEngine.parseValidLocalDate(dateStr) ?: return dateStr
    val monthNames = arrayOf("", "Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
    return "${parsed.dayOfMonth} ${monthNames[parsed.monthValue]}"
}

// Data holder for party ledger calculations
private data class PartyLedgerInfo(
    val party: Party,
    val balancePaise: Long,
    val isOverdue: Boolean,
    val overdueDays: Int,
    val dueStatusText: String?,
    val lastPaymentDateText: String,
    val transactions: List<Transaction>
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LedgerMenuScreen(
    uiState: PabaruUiState,
    onRecordPayment: (String, Long, Boolean, PaymentMode, String) -> Unit,
    onAddParty: (Party) -> Unit
) {
    val context = LocalContext.current
    val tokens = PabaruTheme.tokens

    // Section 0: Customers, Section 1: Suppliers
    var selectedSection by remember { mutableStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }
    var sortOption by remember { mutableStateOf("Highest due") } // "Highest due", "Oldest due", "A to Z"
    var showSortMenu by remember { mutableStateOf(false) }

    // Bottom Sheets state
    var activePaymentParty by remember { mutableStateOf<Pair<Party, Boolean>?>(null) } // Pair(Party, isPaymentIn)
    var selectedLedgerParty by remember { mutableStateOf<PartyLedgerInfo?>(null) }
    var showAddPartySheet by remember { mutableStateOf(false) }

    val isCustomerView = selectedSection == 0

    // Compute ledger information for each party
    val partyLedgerList = remember(uiState.parties, uiState.transactions) {
        val today = LocalDate.now()
        uiState.parties.map { party ->
            val partyTxns = uiState.transactions.filter { it.partyId == party.id && !it.isVoided }

            val netBalPaise = if (!party.isSupplier) {
                var bal = when (party.openingBalanceType) {
                    BalanceType.Receivable -> party.openingBalancePaise
                    BalanceType.Payable -> -party.openingBalancePaise
                    BalanceType.Settled -> 0L
                }
                partyTxns.forEach { t ->
                    when (t.type) {
                        TransactionType.Sale, TransactionType.NonGstBill -> {
                            if (t.paymentMode == PaymentMode.Credit) bal += t.amountPaise
                        }
                        TransactionType.CreditNote -> {
                            if (t.paymentMode == PaymentMode.Credit) bal -= t.amountPaise
                        }
                        TransactionType.PaymentIn -> bal -= t.amountPaise
                        else -> {}
                    }
                }
                bal
            } else {
                var bal = when (party.openingBalanceType) {
                    BalanceType.Payable -> party.openingBalancePaise
                    BalanceType.Receivable -> -party.openingBalancePaise
                    BalanceType.Settled -> 0L
                }
                partyTxns.forEach { t ->
                    when (t.type) {
                        TransactionType.Purchase -> {
                            if (t.paymentMode == PaymentMode.Credit) bal += t.amountPaise
                        }
                        TransactionType.DebitNote -> {
                            if (t.paymentMode == PaymentMode.Credit) bal -= t.amountPaise
                        }
                        TransactionType.PaymentOut -> bal -= t.amountPaise
                        else -> {}
                    }
                }
                bal
            }

            val oldestCreditDate = if (netBalPaise > 0L) {
                val totalSettlements = if (!party.isSupplier) {
                    (if (party.openingBalanceType == BalanceType.Payable) party.openingBalancePaise else 0L) +
                        partyTxns.filter {
                            it.type == TransactionType.PaymentIn ||
                                (it.type == TransactionType.CreditNote && it.paymentMode == PaymentMode.Credit)
                        }.sumOf { it.amountPaise }
                } else {
                    (if (party.openingBalanceType == BalanceType.Receivable) party.openingBalancePaise else 0L) +
                        partyTxns.filter {
                            it.type == TransactionType.PaymentOut ||
                                (it.type == TransactionType.DebitNote && it.paymentMode == PaymentMode.Credit)
                        }.sumOf { it.amountPaise }
                }
                var remainingSettlement = totalSettlements
                if (!party.isSupplier && party.openingBalanceType == BalanceType.Receivable && party.openingBalancePaise > 0L) {
                    remainingSettlement = (remainingSettlement - party.openingBalancePaise).coerceAtLeast(0L)
                } else if (party.isSupplier && party.openingBalanceType == BalanceType.Payable && party.openingBalancePaise > 0L) {
                    remainingSettlement = (remainingSettlement - party.openingBalancePaise).coerceAtLeast(0L)
                }
                val sortedCreditTxns = partyTxns
                    .filter { t ->
                        t.paymentMode == PaymentMode.Credit && (
                            (!party.isSupplier && (t.type == TransactionType.Sale || t.type == TransactionType.NonGstBill)) ||
                                (party.isSupplier && t.type == TransactionType.Purchase)
                            )
                    }
                    .sortedWith(compareBy({ FinancialEngine.parseValidLocalDate(it.date) ?: LocalDate.MAX }, { it.id }))
                var earliestUnsettledDate: LocalDate? = null
                for (txn in sortedCreditTxns) {
                    val covered = minOf(remainingSettlement, txn.amountPaise)
                    remainingSettlement -= covered
                    if (txn.amountPaise - covered > 0L) {
                        earliestUnsettledDate = FinancialEngine.parseValidLocalDate(txn.date)
                        break
                    }
                }
                earliestUnsettledDate
            } else null

            val ageDays = if (oldestCreditDate != null) {
                ChronoUnit.DAYS.between(oldestCreditDate, today).toInt().coerceAtLeast(0)
            } else 0

            val isOverdue = netBalPaise > 0L && ageDays > 30
            val overdueDays = if (isOverdue) ageDays else 0
            val dueStatusText = when {
                netBalPaise <= 0L -> null
                ageDays > 30 -> "$ageDays days overdue"
                ageDays in 23..30 -> "Due this week"
                else -> null
            }

            val lastPayment = partyTxns
                .filter { it.type == TransactionType.PaymentIn || it.type == TransactionType.PaymentOut }
                .maxByOrNull { FinancialEngine.normalizeDate(it.date) }
            val lastPaymentDateText = if (lastPayment != null) {
                "Last payment ${formatDisplayDate(lastPayment.date)}"
            } else {
                "No payments recorded"
            }

            PartyLedgerInfo(
                party = party,
                balancePaise = netBalPaise,
                isOverdue = isOverdue,
                overdueDays = overdueDays,
                dueStatusText = dueStatusText,
                lastPaymentDateText = lastPaymentDateText,
                transactions = partyTxns
            )
        }
    }

    // Filter by customer/supplier
    val currentSectionList = remember(partyLedgerList, isCustomerView) {
        partyLedgerList.filter { if (isCustomerView) !it.party.isSupplier else it.party.isSupplier }
    }

    // Totals for segmented control and summary cards
    val customersTotalPaise = remember(partyLedgerList) {
        partyLedgerList.filter { !it.party.isSupplier }.sumOf { it.balancePaise }
    }
    val suppliersTotalPaise = remember(partyLedgerList) {
        partyLedgerList.filter { it.party.isSupplier }.sumOf { it.balancePaise }
    }

    val customerOverdueCount = remember(partyLedgerList) {
        partyLedgerList.count { !it.party.isSupplier && it.isOverdue }
    }
    val supplierDueThisWeekCount = remember(partyLedgerList) {
        partyLedgerList.count { it.party.isSupplier && it.dueStatusText != null }
    }

    // Apply Search and Sort
    val filteredAndSortedList = remember(currentSectionList, searchQuery, sortOption) {
        val q = searchQuery.trim().lowercase()
        val filtered = if (q.isEmpty()) {
            currentSectionList
        } else {
            currentSectionList.filter {
                it.party.name.lowercase().contains(q) ||
                it.party.phone.contains(q) ||
                it.party.gstin.lowercase().contains(q)
            }
        }

        when (sortOption) {
            "Highest due" -> filtered.sortedByDescending { it.balancePaise }
            "Oldest due" -> filtered.sortedByDescending { it.overdueDays }
            "A to Z" -> filtered.sortedBy { it.party.name.lowercase() }
            else -> filtered
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
                contentPadding = PaddingValues(top = 12.dp, bottom = 88.dp) // space for FAB and bottom nav
            ) {
                // ==========================================
                // TITLE (Ledgers + Khata)
                // ==========================================
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp, bottom = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Ledgers",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Medium,
                            color = tokens.text
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Khata",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Normal,
                            color = tokens.textMuted
                        )
                    }
                }

                // ==========================================
                // SEGMENTED CONTROL (48px tall)
                // ==========================================
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // 1. Customers Segment
                        val isCustSelected = selectedSection == 0
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clickable { selectedSection = 0 },
                            shape = RoundedCornerShape(12.dp),
                            color = if (isCustSelected) tokens.successContainer else tokens.card,
                            border = BorderStroke(
                                width = 0.5.dp,
                                color = if (isCustSelected) tokens.success else tokens.cardBorder
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(vertical = 4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = "Customers",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = if (isCustSelected) tokens.onSuccessContainer else tokens.textMuted
                                )
                                Text(
                                    text = FinancialEngine.formatPaise(customersTotalPaise, compact = true),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = if (isCustSelected) tokens.onSuccessContainer else tokens.success
                                )
                            }
                        }

                        // 2. Suppliers Segment
                        val isSuppSelected = selectedSection == 1
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clickable { selectedSection = 1 },
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSuppSelected) tokens.warningContainer else tokens.card,
                            border = BorderStroke(
                                width = 0.5.dp,
                                color = if (isSuppSelected) tokens.warning else tokens.cardBorder
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(vertical = 4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = "Suppliers",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = if (isSuppSelected) tokens.onWarningContainer else tokens.textMuted
                                )
                                Text(
                                    text = FinancialEngine.formatPaise(suppliersTotalPaise, compact = true),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = if (isSuppSelected) tokens.onWarningContainer else tokens.warning
                                )
                            }
                        }
                    }
                }

                // ==========================================
                // SUMMARY CARD
                // ==========================================
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
                                text = if (isCustomerView) "To collect" else "To pay",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Normal,
                                color = tokens.textMuted
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (isCustomerView) FinancialEngine.formatPaise(customersTotalPaise) else FinancialEngine.formatPaise(suppliersTotalPaise),
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Medium,
                                color = if (isCustomerView) tokens.success else tokens.warning
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (isCustomerView) {
                                    val count = currentSectionList.size
                                    "$count customers · $customerOverdueCount overdue"
                                } else {
                                    val count = currentSectionList.size
                                    "$count suppliers · $supplierDueThisWeekCount due this week"
                                },
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Normal,
                                color = tokens.textMuted
                            )
                        }
                    }
                }

                // ==========================================
                // SEARCH AND SORT
                // ==========================================
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Search Box (48px)
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            shape = RoundedCornerShape(12.dp),
                            color = tokens.card,
                            border = BorderStroke(0.5.dp, tokens.cardBorder)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(start = 12.dp, end = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Search,
                                    contentDescription = "Search",
                                    tint = tokens.textMuted,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Box(modifier = Modifier.weight(1f)) {
                                    if (searchQuery.isEmpty()) {
                                        Text(
                                            text = "Search party",
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
                                        textStyle = TextStyle(
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

                        // Sort Chip
                        Box {
                            Surface(
                                modifier = Modifier
                                    .height(48.dp)
                                    .clickable { showSortMenu = true },
                                shape = RoundedCornerShape(12.dp),
                                color = tokens.card,
                                border = BorderStroke(0.5.dp, tokens.cardBorder)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .padding(horizontal = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = sortOption,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = tokens.text
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Icon(
                                        imageVector = Icons.Outlined.KeyboardArrowDown,
                                        contentDescription = "Sort options",
                                        tint = tokens.textMuted,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }

                            DropdownMenu(
                                expanded = showSortMenu,
                                onDismissRequest = { showSortMenu = false },
                                modifier = Modifier
                                    .background(tokens.card)
                                    .border(0.5.dp, tokens.cardBorder, RoundedCornerShape(12.dp))
                            ) {
                                listOf("Highest due", "Oldest due", "A to Z").forEach { opt ->
                                    DropdownMenuItem(
                                        text = {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = opt,
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    color = if (sortOption == opt) tokens.primary else tokens.text
                                                )
                                                if (sortOption == opt) {
                                                    Icon(
                                                        imageVector = Icons.Outlined.Check,
                                                        contentDescription = null,
                                                        tint = tokens.primary,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            }
                                        },
                                        onClick = {
                                            sortOption = opt
                                            showSortMenu = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                // ==========================================
                // PARTY CARDS / EMPTY STATE
                // ==========================================
                if (filteredAndSortedList.isEmpty()) {
                    item {
                        EmptyLedgerState(
                            tokens = tokens,
                            onAddParty = { showAddPartySheet = true }
                        )
                    }
                } else {
                    items(filteredAndSortedList, key = { it.party.id }) { ledgerInfo ->
                        PartyCardItem(
                            info = ledgerInfo,
                            isCustomer = isCustomerView,
                            tokens = tokens,
                            onCardClick = { selectedLedgerParty = ledgerInfo },
                            onReceiveOrPay = { activePaymentParty = Pair(ledgerInfo.party, isCustomerView) },
                            onRemind = {
                                if (ledgerInfo.balancePaise <= 0L) {
                                    Toast.makeText(
                                        context,
                                        "No pending receivable for ${ledgerInfo.party.name}",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                } else {
                                    ExportEngine.shareWhatsAppReminder(
                                        context = context,
                                        party = ledgerInfo.party,
                                        amountPaise = ledgerInfo.balancePaise,
                                        bankAccount = uiState.bankAccounts.firstOrNull(),
                                        profile = uiState.profile
                                    )
                                }
                            }
                        )
                    }
                }
            }
        }

        // ==========================================
        // FLOATING ACTION BUTTON
        // ==========================================
        FloatingActionButton(
            onClick = { showAddPartySheet = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 16.dp)
                .size(56.dp),
            shape = CircleShape,
            containerColor = tokens.primary,
            contentColor = tokens.primaryText,
            elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 2.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.Add,
                contentDescription = "Add khata",
                modifier = Modifier.size(24.dp)
            )
        }
    }

    // ==========================================
    // PAYMENT BOTTOM SHEET (Receive / Pay)
    // ==========================================
    activePaymentParty?.let { (party, isPaymentIn) ->
        PaymentBottomSheet(
            party = party,
            isPaymentIn = isPaymentIn,
            tokens = tokens,
            onDismiss = { activePaymentParty = null },
            onSave = { amountPaise, mode, remarks ->
                onRecordPayment(party.id, amountPaise, isPaymentIn, mode, remarks)
                val formattedAmt = FinancialEngine.formatPaise(amountPaise)
                Toast.makeText(
                    context,
                    if (isPaymentIn) "Recorded payment of $formattedAmt from ${party.name}"
                    else "Recorded payment of $formattedAmt to ${party.name}",
                    Toast.LENGTH_SHORT
                ).show()
                activePaymentParty = null
            }
        )
    }

    // ==========================================
    // PARTY LEDGER DETAILS SHEET
    // ==========================================
    selectedLedgerParty?.let { info ->
        PartyLedgerDetailSheet(
            info = info,
            tokens = tokens,
            onDismiss = { selectedLedgerParty = null },
            onOpenPayment = {
                val party = info.party
                val isIn = !party.isSupplier
                selectedLedgerParty = null
                activePaymentParty = Pair(party, isIn)
            },
            onRemind = {
                if (info.balancePaise <= 0L) {
                    Toast.makeText(
                        context,
                        "No pending receivable for ${info.party.name}",
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    ExportEngine.shareWhatsAppReminder(
                        context = context,
                        party = info.party,
                        amountPaise = info.balancePaise,
                        bankAccount = uiState.bankAccounts.firstOrNull(),
                        profile = uiState.profile
                    )
                }
            }
        )
    }

    // ==========================================
    // ADD KHATA BOTTOM SHEET
    // ==========================================
    if (showAddPartySheet) {
        AddKhataBottomSheet(
            initialIsSupplier = selectedSection == 1,
            defaultStateCode = uiState.profile.homeStateCode.ifBlank { "27" },
            tokens = tokens,
            onDismiss = { showAddPartySheet = false },
            onSave = { newParty ->
                onAddParty(newParty)
                Toast.makeText(context, "Added ${newParty.name} to ledgers", Toast.LENGTH_SHORT).show()
                showAddPartySheet = false
            }
        )
    }
}

// ==========================================
// PARTY CARD COMPONENT
// ==========================================
@Composable
private fun PartyCardItem(
    info: PartyLedgerInfo,
    isCustomer: Boolean,
    tokens: PabaruTokens,
    onCardClick: () -> Unit,
    onReceiveOrPay: () -> Unit,
    onRemind: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onCardClick),
        shape = RoundedCornerShape(12.dp),
        color = tokens.card,
        border = BorderStroke(0.5.dp, tokens.cardBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            // Row 1: Party Name on Left, Amount on Right
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                // Name (wraps to two lines if needed, never truncated)
                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                    Text(
                        text = info.party.name,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = tokens.text,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = if (info.party.phone.isNotBlank()) "${info.party.phone} · ${info.lastPaymentDateText}" else info.lastPaymentDateText,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Normal,
                        color = tokens.textMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Amount + Overdue Badge on Right
                Column(horizontalAlignment = Alignment.End) {
                    val isSettled = info.balancePaise == 0L
                    Text(
                        text = if (isSettled) "Settled" else FinancialEngine.formatPaise(info.balancePaise),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = when {
                            isSettled -> tokens.textMuted
                            isCustomer -> if (info.balancePaise > 0) tokens.success else tokens.warning
                            else -> if (info.balancePaise > 0) tokens.warning else tokens.success
                        }
                    )

                    if (info.isOverdue) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = tokens.dangerContainer,
                            border = BorderStroke(0.5.dp, tokens.danger)
                        ) {
                            Text(
                                text = "${info.overdueDays} days overdue",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = tokens.onDangerContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            // Hairline separator
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 10.dp),
                thickness = 0.5.dp,
                color = tokens.cardBorder
            )

            // Action row (48dp tall buttons)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (isCustomer) {
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .clickable(onClick = onReceiveOrPay),
                        shape = RoundedCornerShape(12.dp),
                        color = tokens.card,
                        border = BorderStroke(0.5.dp, tokens.success)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.ArrowDownward,
                                contentDescription = "Receive",
                                tint = tokens.success,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Receive",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = tokens.success
                            )
                        }
                    }

                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .clickable(onClick = onRemind),
                        shape = RoundedCornerShape(12.dp),
                        color = tokens.card,
                        border = BorderStroke(0.5.dp, tokens.cardBorder)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Chat,
                                contentDescription = "WhatsApp reminder",
                                tint = tokens.textMuted,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Remind",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = tokens.text
                            )
                        }
                    }
                } else {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .clickable(onClick = onReceiveOrPay),
                        shape = RoundedCornerShape(12.dp),
                        color = tokens.card,
                        border = BorderStroke(0.5.dp, tokens.warning)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.ArrowUpward,
                                contentDescription = "Pay",
                                tint = tokens.warning,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Pay",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = tokens.warning
                            )
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// EMPTY STATE COMPONENT
// ==========================================
@Composable
private fun EmptyLedgerState(
    tokens: PabaruTokens,
    onAddParty: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .drawBehind {
                drawRoundRect(
                    color = tokens.cardBorder,
                    cornerRadius = CornerRadius(12.dp.toPx(), 12.dp.toPx()),
                    style = Stroke(
                        width = 1.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 12f), 0f)
                    )
                )
            }
            .clip(RoundedCornerShape(12.dp))
            .background(tokens.card)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                modifier = Modifier.size(48.dp),
                shape = CircleShape,
                color = tokens.secondaryBg,
                border = BorderStroke(0.5.dp, tokens.cardBorder)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Outlined.People,
                        contentDescription = null,
                        tint = tokens.textMuted,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Text(
                text = "Add your first party",
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = tokens.text
            )

            Text(
                text = "Track what customers owe you and what you owe suppliers.",
                fontSize = 12.sp,
                fontWeight = FontWeight.Normal,
                color = tokens.textMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(4.dp))

            Surface(
                modifier = Modifier
                    .height(48.dp)
                    .clickable(onClick = onAddParty),
                shape = RoundedCornerShape(12.dp),
                color = tokens.primary
            ) {
                Box(
                    modifier = Modifier.padding(horizontal = 20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Add khata",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = tokens.primaryText
                    )
                }
            }
        }
    }
}

// ==========================================
// PAYMENT BOTTOM SHEET
// ==========================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PaymentBottomSheet(
    party: Party,
    isPaymentIn: Boolean,
    tokens: PabaruTokens,
    onDismiss: () -> Unit,
    onSave: (Long, PaymentMode, String) -> Unit
) {
    var amountStr by remember { mutableStateOf("") }
    var selectedMode by remember { mutableStateOf(PaymentMode.Cash) }
    var remarksStr by remember { mutableStateOf("") }

    val activeColor = if (isPaymentIn) tokens.success else tokens.warning

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = tokens.card,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .size(width = 36.dp, height = 4.dp)
                    .clip(CircleShape)
                    .background(tokens.cardBorder)
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header
            Column {
                Text(
                    text = if (isPaymentIn) "Record payment in" else "Record payment out",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    color = tokens.text
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = if (party.phone.isNotBlank()) "${party.name} · ${party.phone}" else party.name,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Normal,
                    color = tokens.textMuted
                )
            }

            // Amount Field
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Amount",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Normal,
                    color = tokens.textMuted
                )
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = tokens.secondaryBg,
                    border = BorderStroke(0.5.dp, tokens.cardBorder)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "₹",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Medium,
                            color = tokens.text
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(modifier = Modifier.weight(1f)) {
                            if (amountStr.isEmpty()) {
                                Text(
                                    text = "0.00",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Normal,
                                    color = tokens.textMuted
                                )
                            }
                            BasicTextField(
                                value = amountStr,
                                onValueChange = { input ->
                                    amountStr = FinancialEngine.sanitizeDecimalInput(input)
                                },
                                singleLine = true,
                                cursorBrush = SolidColor(tokens.primary),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                textStyle = TextStyle(
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = tokens.text
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }

            // Payment Mode Selector (3 equal segments in one row, 48dp tall: Cash, UPI, Bank)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Payment mode",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Normal,
                    color = tokens.textMuted
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(PaymentMode.Cash, PaymentMode.UPI, PaymentMode.Bank).forEach { mode ->
                        val isSelected = selectedMode == mode
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clickable { selectedMode = mode },
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) activeColor.copy(alpha = 0.15f) else tokens.secondaryBg,
                            border = BorderStroke(
                                0.5.dp,
                                if (isSelected) activeColor else tokens.cardBorder
                            )
                        ) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = when (mode) {
                                        PaymentMode.Cash -> "Cash"
                                        PaymentMode.UPI -> "UPI"
                                        PaymentMode.Bank -> "Bank"
                                        else -> mode.name
                                    },
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = if (isSelected) tokens.text else tokens.textMuted
                                )
                            }
                        }
                    }
                }
            }

            // Remarks field (optional)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Remarks (optional)",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Normal,
                    color = tokens.textMuted
                )
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = tokens.secondaryBg,
                    border = BorderStroke(0.5.dp, tokens.cardBorder)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 12.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (remarksStr.isEmpty()) {
                            Text(
                                text = "e.g. Cleared bill / advance",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Normal,
                                color = tokens.textMuted
                            )
                        }
                        BasicTextField(
                            value = remarksStr,
                            onValueChange = { remarksStr = it },
                            singleLine = true,
                            cursorBrush = SolidColor(tokens.primary),
                            textStyle = TextStyle(
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Normal,
                                color = tokens.text
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            val parsedPaise = FinancialEngine.parsePaiseFromRupees(amountStr)
            val canSave = parsedPaise > 0L
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clickable(enabled = canSave) {
                        if (parsedPaise > 0L) {
                            onSave(parsedPaise, selectedMode, remarksStr.trim())
                        }
                    },
                shape = RoundedCornerShape(12.dp),
                color = if (canSave) tokens.primary else tokens.primary.copy(alpha = 0.4f)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Save payment",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = tokens.primaryText
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
        }
    }
}

// ==========================================
// PARTY LEDGER DETAIL BOTTOM SHEET
// ==========================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PartyLedgerDetailSheet(
    info: PartyLedgerInfo,
    tokens: PabaruTokens,
    onDismiss: () -> Unit,
    onOpenPayment: () -> Unit,
    onRemind: () -> Unit
) {
    val party = info.party
    val isCust = !party.isSupplier

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = tokens.card,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .size(width = 36.dp, height = 4.dp)
                    .clip(CircleShape)
                    .background(tokens.cardBorder)
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = party.name,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium,
                        color = tokens.text
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = buildString {
                            if (party.phone.isNotBlank()) {
                                append(party.phone)
                                append(" · ")
                            }
                            append("State ${party.stateCode.ifBlank { "27" }}")
                        },
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Normal,
                        color = tokens.textMuted
                    )
                }

                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = "Close",
                        tint = tokens.textMuted
                    )
                }
            }

            // GSTIN & Address
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = tokens.secondaryBg,
                border = BorderStroke(0.5.dp, tokens.cardBorder)
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Statutory details",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = tokens.textMuted
                    )
                    Text(
                        text = "GSTIN: ${party.gstin.ifEmpty { "Unregistered" }} · PAN: ${party.pan.ifEmpty { "None" }}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Normal,
                        color = tokens.text
                    )
                    Text(
                        text = "Address: ${party.address.ifBlank { "Not specified" }}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Normal,
                        color = tokens.textMuted
                    )
                }
            }

            // Balance Card
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = if (isCust) tokens.success.copy(alpha = 0.1f) else tokens.warning.copy(alpha = 0.1f),
                border = BorderStroke(0.5.dp, if (isCust) tokens.success else tokens.warning)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = if (isCust) "Net to collect" else "Net to pay",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Normal,
                            color = tokens.textMuted
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = FinancialEngine.formatPaise(info.balancePaise),
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (isCust) tokens.success else tokens.warning
                        )
                    }

                    if (info.dueStatusText != null) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (info.isOverdue) tokens.dangerContainer else tokens.warningContainer,
                            border = BorderStroke(0.5.dp, if (info.isOverdue) tokens.danger else tokens.warning)
                        ) {
                            Text(
                                text = info.dueStatusText,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = if (info.isOverdue) tokens.onDangerContainer else tokens.onWarningContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }

            // Action Buttons Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .clickable(onClick = onOpenPayment),
                    shape = RoundedCornerShape(12.dp),
                    color = tokens.primary
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (isCust) "Record receive" else "Record payment",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = tokens.primaryText
                        )
                    }
                }

                if (isCust) {
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .clickable(onClick = onRemind),
                        shape = RoundedCornerShape(12.dp),
                        color = tokens.secondaryBg,
                        border = BorderStroke(0.5.dp, tokens.cardBorder)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Chat,
                                contentDescription = null,
                                tint = tokens.text,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "WhatsApp",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = tokens.text
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
        }
    }
}

// ==========================================
// ADD KHATA BOTTOM SHEET
// ==========================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddKhataBottomSheet(
    initialIsSupplier: Boolean,
    defaultStateCode: String,
    tokens: PabaruTokens,
    onDismiss: () -> Unit,
    onSave: (Party) -> Unit
) {
    var isSupplier by remember { mutableStateOf(initialIsSupplier) }
    var nameInput by remember { mutableStateOf("") }
    var phoneInput by remember { mutableStateOf("") }
    var gstinInput by remember { mutableStateOf("") }
    var addressInput by remember { mutableStateOf("") }
    var openingBalInput by remember { mutableStateOf("") }
    var validationError by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = tokens.card,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .size(width = 36.dp, height = 4.dp)
                    .clip(CircleShape)
                    .background(tokens.cardBorder)
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header
            Text(
                text = "Add khata",
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                color = tokens.text
            )

            // Segmented party type selector (48dp)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable { isSupplier = false },
                    shape = RoundedCornerShape(12.dp),
                    color = if (!isSupplier) tokens.successContainer else tokens.secondaryBg,
                    border = BorderStroke(0.5.dp, if (!isSupplier) tokens.success else tokens.cardBorder)
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "Customer",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (!isSupplier) tokens.onSuccessContainer else tokens.textMuted
                        )
                    }
                }

                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable { isSupplier = true },
                    shape = RoundedCornerShape(12.dp),
                    color = if (isSupplier) tokens.warningContainer else tokens.secondaryBg,
                    border = BorderStroke(0.5.dp, if (isSupplier) tokens.warning else tokens.cardBorder)
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "Supplier",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (isSupplier) tokens.onWarningContainer else tokens.textMuted
                        )
                    }
                }
            }

            // Party Name
            KhataInputField(
                label = "Party name",
                value = nameInput,
                onValueChange = {
                    nameInput = it
                    validationError = null
                },
                placeholder = "e.g. Omkar Traders",
                tokens = tokens
            )

            // Phone Number
            KhataInputField(
                label = "Phone number (optional)",
                value = phoneInput,
                onValueChange = {
                    phoneInput = it.filter { c -> c.isDigit() }.take(10)
                    validationError = null
                },
                placeholder = "10 digit mobile",
                keyboardType = KeyboardType.Phone,
                tokens = tokens
            )

            // GSTIN
            KhataInputField(
                label = "GSTIN (optional)",
                value = gstinInput,
                onValueChange = {
                    gstinInput = it.trim().uppercase().take(15)
                    validationError = null
                },
                placeholder = "15-digit GSTIN",
                tokens = tokens
            )

            // Address / Market
            KhataInputField(
                label = "Address / Market (optional)",
                value = addressInput,
                onValueChange = { addressInput = it },
                placeholder = "e.g. APMC Market, Thane",
                tokens = tokens
            )

            // Opening Balance
            KhataInputField(
                label = "Opening balance (₹)",
                value = openingBalInput,
                onValueChange = { openingBalInput = FinancialEngine.sanitizeDecimalInput(it) },
                placeholder = "0.00",
                keyboardType = KeyboardType.Decimal,
                tokens = tokens
            )

            if (validationError != null) {
                Surface(
                    color = tokens.dangerContainer,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, tokens.danger),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = validationError ?: "",
                        color = tokens.onDangerContainer,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            val canSave = nameInput.isNotBlank()
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clickable(enabled = canSave) {
                        val trimmedPhone = phoneInput.trim()
                        if (trimmedPhone.isNotBlank()) {
                            val phoneRes = FinancialEngine.validatePhone(trimmedPhone, allowBlank = true)
                            if (!phoneRes.isValid) {
                                validationError = phoneRes.message
                                return@clickable
                            }
                        }

                        val trimmedGstin = gstinInput.trim().uppercase()
                        val gstinRes = FinancialEngine.validateGstin(trimmedGstin, allowBlank = true)
                        if (!gstinRes.isValid) {
                            validationError = gstinRes.message
                            return@clickable
                        }

                        val balPaise = FinancialEngine.parsePaiseFromRupees(openingBalInput)
                        val balType = if (balPaise > 0L) {
                            if (isSupplier) BalanceType.Payable else BalanceType.Receivable
                        } else {
                            BalanceType.Settled
                        }
                        val stateCode = if (trimmedGstin.isNotBlank()) gstinRes.stateCode?.takeIf { it.isNotBlank() } ?: defaultStateCode else defaultStateCode
                        val pan = if (trimmedGstin.isNotBlank()) gstinRes.pan.orEmpty() else ""

                        val newParty = Party(
                            id = "PTY-${System.currentTimeMillis()}-${java.util.UUID.randomUUID().toString().take(4)}",
                            name = nameInput.trim(),
                            phone = trimmedPhone,
                            address = addressInput.trim(),
                            gstin = trimmedGstin,
                            pan = pan,
                            stateCode = stateCode,
                            openingBalancePaise = balPaise,
                            openingBalanceType = balType,
                            isSupplier = isSupplier
                        )
                        onSave(newParty)
                    },
                shape = RoundedCornerShape(12.dp),
                color = if (canSave) tokens.primary else tokens.primary.copy(alpha = 0.4f)
            ) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "Save party",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = tokens.primaryText
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
        }
    }
}

// Reusable text field row for forms
@Composable
private fun KhataInputField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    tokens: PabaruTokens,
    keyboardType: KeyboardType = KeyboardType.Text
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.Normal,
            color = tokens.textMuted
        )
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(12.dp),
            color = tokens.secondaryBg,
            border = BorderStroke(0.5.dp, tokens.cardBorder)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                if (value.isEmpty()) {
                    Text(
                        text = placeholder,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Normal,
                        color = tokens.textMuted
                    )
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    singleLine = true,
                    cursorBrush = SolidColor(tokens.primary),
                    keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                    textStyle = TextStyle(
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Normal,
                        color = tokens.text
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
