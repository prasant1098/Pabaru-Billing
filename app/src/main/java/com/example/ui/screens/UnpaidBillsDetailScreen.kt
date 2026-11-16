package com.example.ui.screens

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.engine.ExportEngine
import com.example.engine.FinancialEngine
import com.example.model.*
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
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.*

enum class UnpaidFilterOption {
    ALL,
    OVERDUE,
    PARTLY_PAID,
    UNPAID
}

data class UnpaidBillDetail(
    val transaction: Transaction,
    val party: Party?,
    val totalBillPaise: Long,
    val paidPaise: Long,
    val pendingPaise: Long,
    val isPartlyPaid: Boolean,
    val daysPending: Int,
    val isOverdue: Boolean
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnpaidBillsDetailScreen(
    isCollect: Boolean, // true = To Collect (Receivables / Sales), false = To Pay (Payables / Purchases)
    uiState: PabaruUiState,
    onBack: () -> Unit,
    onRecordPayment: (partyId: String, amountPaise: Long, isPaymentIn: Boolean, mode: PaymentMode, remarks: String) -> Unit
) {
    val context = LocalContext.current
    val tokens = PabaruTheme.tokens

    BackHandler(onBack = onBack)

    val activeBranchId = uiState.activeBranchId
    val branchTxns = remember(uiState.transactions, activeBranchId) {
        uiState.transactions.filter { it.branchId == activeBranchId && !it.isVoided }
    }

    // Color Palette derived from theme tokens
    val primaryAccent = if (isCollect) tokens.success else tokens.danger
    val badgeBg = if (isCollect) tokens.successContainer else tokens.dangerContainer
    val cardBg = tokens.card
    val cardBorder = tokens.cardBorder
    val textPrimary = tokens.text
    val textMuted = tokens.textMuted

    // Compute Unpaid Bills List with Deterministic FIFO Allocation
    val allUnpaidBills = remember(branchTxns, uiState.parties, isCollect) {
        val list = mutableListOf<UnpaidBillDetail>()
        val todayStr = FinancialEngine.getTodayDate()
        val todayLocalDate = FinancialEngine.parseValidLocalDate(todayStr) ?: LocalDate.now()

        fun daysSince(dateStr: String): Int {
            val billDate = FinancialEngine.parseValidLocalDate(dateStr) ?: return 0
            return ChronoUnit.DAYS.between(billDate, todayLocalDate).toInt().coerceAtLeast(0)
        }

        if (isCollect) {
            // Customer sales invoices
            val customerParties = uiState.parties.filter { !it.isSupplier }

            // Group transactions by party safely without force unwrap
            val partySales = branchTxns.filter { it.type == TransactionType.Sale && !it.partyId.isNullOrBlank() }
                .groupBy { it.partyId ?: "" }
            val partyPaymentsIn = branchTxns.filter { it.type == TransactionType.PaymentIn && !it.partyId.isNullOrBlank() }
                .groupBy { it.partyId ?: "" }
            val partyCreditNotes = branchTxns.filter {
                it.type == TransactionType.CreditNote && it.paymentMode == PaymentMode.Credit && !it.partyId.isNullOrBlank()
            }.groupBy { it.partyId ?: "" }

            // Process each customer party
            customerParties.forEach { party ->
                val sales = (partySales[party.id] ?: emptyList()).sortedBy { it.date }
                val totalPayments = (partyPaymentsIn[party.id] ?: emptyList()).sumOf { it.amountPaise } +
                    (partyCreditNotes[party.id] ?: emptyList()).sumOf { it.amountPaise }
                var remainingPayments = totalPayments

                // Settle opening balance if receivable
                if (party.openingBalanceType == BalanceType.Receivable && party.openingBalancePaise > 0L) {
                    val covered = minOf(remainingPayments, party.openingBalancePaise)
                    val pending = party.openingBalancePaise - covered
                    remainingPayments -= covered
                    if (pending > 0L) {
                        list.add(
                            UnpaidBillDetail(
                                transaction = Transaction(
                                    id = "OPENING-REC-${party.id}",
                                    branchId = activeBranchId,
                                    date = todayStr,
                                    partyId = party.id,
                                    amountPaise = party.openingBalancePaise,
                                    type = TransactionType.Sale,
                                    paymentMode = PaymentMode.Credit,
                                    invoiceNumber = "OPENING-BAL",
                                    remarks = "Opening Khata Balance"
                                ),
                                party = party,
                                totalBillPaise = party.openingBalancePaise,
                                paidPaise = covered,
                                pendingPaise = pending,
                                isPartlyPaid = covered > 0L,
                                daysPending = 35,
                                isOverdue = true
                            )
                        )
                    }
                }

                sales.forEach { sale ->
                    if (sale.paymentMode == PaymentMode.Credit) {
                        val covered = minOf(remainingPayments, sale.amountPaise)
                        val pending = sale.amountPaise - covered
                        remainingPayments -= covered

                        if (pending > 0L) {
                            val diffDays = daysSince(sale.date)
                            list.add(
                                UnpaidBillDetail(
                                    transaction = sale,
                                    party = party,
                                    totalBillPaise = sale.amountPaise,
                                    paidPaise = covered,
                                    pendingPaise = pending,
                                    isPartlyPaid = covered > 0L,
                                    daysPending = diffDays,
                                    isOverdue = diffDays > 30
                                )
                            )
                        }
                    }
                }
            }

            // Also check counter credit sales (without party), FIFO-settled by COUNTER payments + credit notes
            var remCounterPayments = (partyPaymentsIn["COUNTER"] ?: emptyList()).sumOf { it.amountPaise } +
                branchTxns.filter {
                    it.type == TransactionType.CreditNote && it.paymentMode == PaymentMode.Credit &&
                        (it.partyId == null || it.partyId == "COUNTER")
                }.sumOf { it.amountPaise }
            branchTxns.filter { it.type == TransactionType.Sale && it.partyId == null && it.paymentMode == PaymentMode.Credit }
                .sortedBy { it.date }
                .forEach { sale ->
                    val covered = minOf(remCounterPayments, sale.amountPaise)
                    val pending = sale.amountPaise - covered
                    remCounterPayments -= covered
                    if (pending > 0L) {
                        val diffDays = daysSince(sale.date)
                        list.add(
                            UnpaidBillDetail(
                                transaction = sale,
                                party = null,
                                totalBillPaise = sale.amountPaise,
                                paidPaise = covered,
                                pendingPaise = pending,
                                isPartlyPaid = covered > 0L,
                                daysPending = diffDays,
                                isOverdue = diffDays > 30
                            )
                        )
                    }
                }
        } else {
            // Supplier purchase bills
            val supplierParties = uiState.parties.filter { it.isSupplier }
            val partyPurchases = branchTxns.filter { it.type == TransactionType.Purchase && !it.partyId.isNullOrBlank() }
                .groupBy { it.partyId ?: "" }
            val partyPaymentsOut = branchTxns.filter { it.type == TransactionType.PaymentOut && !it.partyId.isNullOrBlank() }
                .groupBy { it.partyId ?: "" }
            val partyDebitNotes = branchTxns.filter {
                it.type == TransactionType.DebitNote && it.paymentMode == PaymentMode.Credit && !it.partyId.isNullOrBlank()
            }.groupBy { it.partyId ?: "" }

            supplierParties.forEach { party ->
                val purchases = (partyPurchases[party.id] ?: emptyList()).sortedBy { it.date }
                val totalPayments = (partyPaymentsOut[party.id] ?: emptyList()).sumOf { it.amountPaise } +
                    (partyDebitNotes[party.id] ?: emptyList()).sumOf { it.amountPaise }
                var remainingPayments = totalPayments

                // Settle opening balance if payable
                if (party.openingBalanceType == BalanceType.Payable && party.openingBalancePaise > 0L) {
                    val covered = minOf(remainingPayments, party.openingBalancePaise)
                    val pending = party.openingBalancePaise - covered
                    remainingPayments -= covered
                    if (pending > 0L) {
                        list.add(
                            UnpaidBillDetail(
                                transaction = Transaction(
                                    id = "OPENING-PAY-${party.id}",
                                    branchId = activeBranchId,
                                    date = todayStr,
                                    partyId = party.id,
                                    amountPaise = party.openingBalancePaise,
                                    type = TransactionType.Purchase,
                                    paymentMode = PaymentMode.Credit,
                                    invoiceNumber = "OPENING-BAL",
                                    remarks = "Opening Supplier Balance"
                                ),
                                party = party,
                                totalBillPaise = party.openingBalancePaise,
                                paidPaise = covered,
                                pendingPaise = pending,
                                isPartlyPaid = covered > 0L,
                                daysPending = 35,
                                isOverdue = true
                            )
                        )
                    }
                }

                purchases.forEach { purchase ->
                    if (purchase.paymentMode == PaymentMode.Credit) {
                        val covered = minOf(remainingPayments, purchase.amountPaise)
                        val pending = purchase.amountPaise - covered
                        remainingPayments -= covered

                        if (pending > 0L) {
                            val diffDays = daysSince(purchase.date)
                            list.add(
                                UnpaidBillDetail(
                                    transaction = purchase,
                                    party = party,
                                    totalBillPaise = purchase.amountPaise,
                                    paidPaise = covered,
                                    pendingPaise = pending,
                                    isPartlyPaid = covered > 0L,
                                    daysPending = diffDays,
                                    isOverdue = diffDays > 30
                                )
                            )
                        }
                    }
                }
            }

            // Counter credit purchases, FIFO-settled by COUNTER payments + debit notes
            var remCounterOut = (partyPaymentsOut["COUNTER"] ?: emptyList()).sumOf { it.amountPaise } +
                branchTxns.filter {
                    it.type == TransactionType.DebitNote && it.paymentMode == PaymentMode.Credit &&
                        (it.partyId == null || it.partyId == "COUNTER")
                }.sumOf { it.amountPaise }
            branchTxns.filter { it.type == TransactionType.Purchase && it.partyId == null && it.paymentMode == PaymentMode.Credit }
                .sortedBy { it.date }
                .forEach { purchase ->
                    val covered = minOf(remCounterOut, purchase.amountPaise)
                    val pending = purchase.amountPaise - covered
                    remCounterOut -= covered
                    if (pending > 0L) {
                        val diffDays = daysSince(purchase.date)
                        list.add(
                            UnpaidBillDetail(
                                transaction = purchase,
                                party = null,
                                totalBillPaise = purchase.amountPaise,
                                paidPaise = covered,
                                pendingPaise = pending,
                                isPartlyPaid = covered > 0L,
                                daysPending = diffDays,
                                isOverdue = diffDays > 30
                            )
                        )
                    }
                }
        }

        list.sortedByDescending { it.transaction.date }
    }

    // State for Search, Filters & Sorting
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var selectedFilter by rememberSaveable { mutableStateOf(UnpaidFilterOption.ALL) }
    var sortByNewest by rememberSaveable { mutableStateOf(true) }

    // Active Payment Sheet State
    var recordingBillItem by remember { mutableStateOf<UnpaidBillDetail?>(null) }

    // Aggregate Metrics
    val totalPendingPaise = remember(allUnpaidBills) { allUnpaidBills.sumOf { it.pendingPaise } }
    val totalBilledPaise = remember(allUnpaidBills) { allUnpaidBills.sumOf { it.totalBillPaise } }
    val totalPaidPaise = remember(allUnpaidBills) { allUnpaidBills.sumOf { it.paidPaise } }

    val overdueCount = remember(allUnpaidBills) { allUnpaidBills.count { it.isOverdue } }
    val partlyPaidCount = remember(allUnpaidBills) { allUnpaidBills.count { it.isPartlyPaid } }
    val unpaidCount = remember(allUnpaidBills) { allUnpaidBills.count { !it.isPartlyPaid } }

    // Filtered & Sorted List
    val filteredBills = remember(allUnpaidBills, searchQuery, selectedFilter, sortByNewest) {
        val q = searchQuery.trim().lowercase()
        var stream = allUnpaidBills.asSequence()

        if (q.isNotEmpty()) {
            stream = stream.filter {
                val inv = (it.transaction.invoiceNumber.ifEmpty { it.transaction.id }).lowercase()
                val partyName = it.party?.name?.lowercase() ?: "counter"
                val partyPhone = it.party?.phone ?: ""
                inv.contains(q) || partyName.contains(q) || partyPhone.contains(q)
            }
        }

        when (selectedFilter) {
            UnpaidFilterOption.ALL -> {}
            UnpaidFilterOption.OVERDUE -> stream = stream.filter { it.isOverdue }
            UnpaidFilterOption.PARTLY_PAID -> stream = stream.filter { it.isPartlyPaid }
            UnpaidFilterOption.UNPAID -> stream = stream.filter { !it.isPartlyPaid }
        }

        val sorted = if (sortByNewest) {
            stream.sortedByDescending { it.transaction.date }
        } else {
            stream.sortedByDescending { it.pendingPaise }
        }
        sorted.toList()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(tokens.bg)
            .testTag("unpaid_bills_screen")
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // ==========================================
            // TOP BAR
            // ==========================================
            Surface(
                color = tokens.card,
                border = BorderStroke(0.5.dp, cardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("unpaid_bills_back_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = textPrimary
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = if (isCollect) "Bills to Collect" else "Bills to Pay",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = textPrimary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Surface(
                                    color = badgeBg,
                                    shape = RoundedCornerShape(6.dp),
                                    border = BorderStroke(0.5.dp, primaryAccent)
                                ) {
                                    Text(
                                        text = if (isCollect) "Lena Hai" else "Dena Hai",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = primaryAccent,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Text(
                                text = "${allUnpaidBills.size} pending bills • ${FinancialEngine.formatPaise(totalPendingPaise)} outstanding",
                                fontSize = 12.sp,
                                color = textMuted
                            )
                        }

                        // Toggle Sort Button
                        IconButton(
                            onClick = { sortByNewest = !sortByNewest },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = if (sortByNewest) Icons.Outlined.CalendarToday else Icons.Outlined.TrendingDown,
                                contentDescription = if (sortByNewest) "Sort by date" else "Sort by amount",
                                tint = primaryAccent,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Search Field
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("unpaid_bills_search_field"),
                        placeholder = {
                            Text(
                                "Search bill #, party or phone...",
                                fontSize = 13.sp,
                                color = textMuted
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null,
                                tint = textMuted,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(
                                    onClick = { searchQuery = "" },
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Clear,
                                        contentDescription = "Clear search",
                                        tint = textMuted,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = tokens.bg,
                            unfocusedContainerColor = tokens.bg,
                            focusedBorderColor = primaryAccent,
                            unfocusedBorderColor = cardBorder,
                            focusedTextColor = textPrimary,
                            unfocusedTextColor = textPrimary
                        )
                    )
                }
            }

            // ==========================================
            // FILTER CHIPS ROW
            // ==========================================
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChipItem(
                    label = "All (${allUnpaidBills.size})",
                    selected = selectedFilter == UnpaidFilterOption.ALL,
                    onClick = { selectedFilter = UnpaidFilterOption.ALL },
                    activeColor = primaryAccent
                )
                FilterChipItem(
                    label = "Overdue ($overdueCount)",
                    selected = selectedFilter == UnpaidFilterOption.OVERDUE,
                    onClick = { selectedFilter = UnpaidFilterOption.OVERDUE },
                    activeColor = tokens.danger
                )
                FilterChipItem(
                    label = "Partly Paid ($partlyPaidCount)",
                    selected = selectedFilter == UnpaidFilterOption.PARTLY_PAID,
                    onClick = { selectedFilter = UnpaidFilterOption.PARTLY_PAID },
                    activeColor = tokens.info
                )
                FilterChipItem(
                    label = "Unpaid ($unpaidCount)",
                    selected = selectedFilter == UnpaidFilterOption.UNPAID,
                    onClick = { selectedFilter = UnpaidFilterOption.UNPAID },
                    activeColor = tokens.warning
                )
            }

            // ==========================================
            // HERO FINANCIAL SUMMARY BANNER
            // ==========================================
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = cardBg),
                border = BorderStroke(0.5.dp, cardBorder)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Total Billed", fontSize = 11.sp, color = textMuted)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = FinancialEngine.formatPaise(totalBilledPaise, compact = true),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = textPrimary
                        )
                    }

                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(28.dp)
                            .background(cardBorder)
                    )

                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(if (isCollect) "Collected" else "Paid", fontSize = 11.sp, color = textMuted)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = FinancialEngine.formatPaise(totalPaidPaise, compact = true),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = tokens.info
                        )
                    }

                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(28.dp)
                            .background(cardBorder)
                    )

                    Column(
                        modifier = Modifier.weight(1.2f),
                        horizontalAlignment = Alignment.End
                    ) {
                        Text(
                            text = if (isCollect) "Net to Collect" else "Net to Pay",
                            fontSize = 11.sp,
                            color = primaryAccent,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = FinancialEngine.formatPaise(totalPendingPaise, compact = true),
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = primaryAccent
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // ==========================================
            // BILL ITEMS LIST / EMPTY STATE
            // ==========================================
            if (filteredBills.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(tokens.cardBorder),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (allUnpaidBills.isEmpty()) Icons.Default.CheckCircle else Icons.Default.SearchOff,
                                contentDescription = null,
                                tint = if (allUnpaidBills.isEmpty()) tokens.success else textMuted,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                        Text(
                            text = if (allUnpaidBills.isEmpty()) {
                                if (isCollect) "All customer bills collected!" else "All supplier bills settled!"
                            } else "No matching bills found",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = textPrimary
                        )
                        Text(
                            text = if (allUnpaidBills.isEmpty()) {
                                if (isCollect) "Zero pending customer receivables in this branch."
                                else "Zero pending supplier liabilities in this branch."
                            } else "Try adjusting your search query or filter chip selection.",
                            fontSize = 12.sp,
                            color = textMuted,
                            modifier = Modifier.padding(horizontal = 24.dp)
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredBills, key = { it.transaction.id }) { item ->
                        UnpaidBillCard(
                            item = item,
                            isCollect = isCollect,
                            primaryAccent = primaryAccent,
                            onRecordPayment = { recordingBillItem = item },
                            onViewInvoice = {
                                ExportEngine.generateAndShareInvoicePdf(
                                    context = context,
                                    transaction = item.transaction,
                                    profile = uiState.profile,
                                    party = item.party,
                                    bankAccount = uiState.bankAccounts.firstOrNull()
                                )
                            },
                            onSendReminder = {
                                if (isCollect && item.party != null) {
                                    val invNum = item.transaction.invoiceNumber.ifEmpty { item.transaction.id }
                                    ExportEngine.shareWhatsAppBillReminder(
                                        context = context,
                                        party = item.party,
                                        invoiceNumber = invNum,
                                        invoiceDate = item.transaction.date,
                                        totalBillPaise = item.totalBillPaise,
                                        pendingPaise = item.pendingPaise,
                                        bankAccount = uiState.bankAccounts.firstOrNull(),
                                        profile = uiState.profile
                                    )
                                } else if (item.party != null) {
                                    ExportEngine.shareWhatsAppReminder(
                                        context = context,
                                        party = item.party,
                                        amountPaise = item.pendingPaise,
                                        bankAccount = uiState.bankAccounts.firstOrNull(),
                                        profile = uiState.profile
                                    )
                                } else {
                                    Toast.makeText(context, "No contact details available for counter bill", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                    }
                }
            }
        }

        // ==========================================
        // RECORD PAYMENT BOTTOM SHEET
        // ==========================================
        recordingBillItem?.let { bill ->
            RecordBillPaymentSheet(
                bill = bill,
                isCollect = isCollect,
                onDismiss = { recordingBillItem = null },
                onConfirmPayment = { amtPaise, mode, remarks ->
                    val partyId = bill.party?.id ?: bill.transaction.partyId ?: "COUNTER"
                    onRecordPayment(partyId, amtPaise, isCollect, mode, remarks)
                    Toast.makeText(
                        context,
                        if (isCollect) "Payment of ${FinancialEngine.formatPaise(amtPaise)} received!"
                        else "Payment of ${FinancialEngine.formatPaise(amtPaise)} recorded!",
                        Toast.LENGTH_SHORT
                    ).show()
                    recordingBillItem = null
                }
            )
        }
    }
}

@Composable
private fun FilterChipItem(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    activeColor: Color
) {
    val tokens = PabaruTheme.tokens
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = if (selected) activeColor.copy(alpha = 0.15f) else tokens.card,
        border = BorderStroke(0.5.dp, if (selected) activeColor else tokens.cardBorder),
        modifier = Modifier.heightIn(min = 48.dp)
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                color = if (selected) activeColor else tokens.textMuted
            )
        }
    }
}

@Composable
private fun UnpaidBillCard(
    item: UnpaidBillDetail,
    isCollect: Boolean,
    primaryAccent: Color,
    onRecordPayment: () -> Unit,
    onViewInvoice: () -> Unit,
    onSendReminder: () -> Unit
) {
    val tokens = PabaruTheme.tokens
    val invNum = item.transaction.invoiceNumber.ifEmpty { item.transaction.id }
    val whatsAppAccent = tokens.success

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("unpaid_bill_card_$invNum"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = tokens.card),
        border = BorderStroke(
            0.5.dp,
            if (item.isOverdue) tokens.danger.copy(alpha = 0.5f) else tokens.cardBorder
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header Row: Invoice # + Date + Status Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.ReceiptLong,
                        contentDescription = null,
                        tint = primaryAccent,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = invNum,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = tokens.text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "• ${item.transaction.date}",
                        fontSize = 12.sp,
                        color = tokens.textMuted
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Status Badge
                val (badgeText, badgeColor, badgeBgColor) = when {
                    item.isOverdue -> Triple("${item.daysPending}d Overdue", tokens.danger, tokens.dangerContainer)
                    item.isPartlyPaid -> Triple("Partly Paid", tokens.info, tokens.infoContainer)
                    else -> Triple("Unpaid", tokens.warning, tokens.warningContainer)
                }

                Surface(
                    color = badgeBgColor,
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(0.5.dp, badgeColor.copy(alpha = 0.5f))
                ) {
                    Text(
                        text = badgeText,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = badgeColor,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Party Details Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    tint = tokens.textMuted,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = item.party?.name ?: "Counter Cash Customer",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = tokens.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (item.party?.phone?.isNotEmpty() == true) {
                    Text(
                        text = item.party.phone,
                        fontSize = 11.sp,
                        color = tokens.textMuted
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 3-Column Financial Breakdown Box
            Surface(
                color = tokens.bg,
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(0.5.dp, tokens.cardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Bill Total", fontSize = 10.sp, color = tokens.textMuted)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = FinancialEngine.formatPaise(item.totalBillPaise),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = tokens.text
                        )
                    }

                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(22.dp)
                            .background(tokens.cardBorder)
                    )

                    Column {
                        Text("Paid", fontSize = 10.sp, color = tokens.textMuted)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = FinancialEngine.formatPaise(item.paidPaise),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = tokens.info
                        )
                    }

                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(22.dp)
                            .background(tokens.cardBorder)
                    )

                    Column(horizontalAlignment = Alignment.End) {
                        Text("Pending Balance", fontSize = 10.sp, color = primaryAccent, fontWeight = FontWeight.Medium)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = FinancialEngine.formatPaise(item.pendingPaise),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = primaryAccent
                        )
                    }
                }
            }

            // Optional items note
            if (item.transaction.items.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                val itemsSummary = item.transaction.items.joinToString(", ") {
                    "${it.name} (${FinancialEngine.formatQuantityMilli(it.quantityMilli)} ${it.unit})"
                }
                Text(
                    text = "Items: $itemsSummary",
                    fontSize = 11.sp,
                    color = tokens.textMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // ==========================================
            // ACTION BUTTONS ROW (Record, View, Remind)
            // ==========================================
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 1. Record Payment Button (Primary)
                Button(
                    onClick = onRecordPayment,
                    modifier = Modifier
                        .weight(1.3f)
                        .heightIn(min = 48.dp)
                        .testTag("record_payment_button_$invNum"),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = primaryAccent,
                        contentColor = tokens.primaryText
                    ),
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isCollect) "Receive" else "Pay Bill",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }

                // 2. View Invoice Button (PDF)
                OutlinedButton(
                    onClick = onViewInvoice,
                    modifier = Modifier
                        .weight(1.1f)
                        .heightIn(min = 48.dp)
                        .testTag("view_invoice_button_$invNum"),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(0.5.dp, tokens.cardBorder),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = tokens.text),
                    contentPadding = PaddingValues(horizontal = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PictureAsPdf,
                        contentDescription = null,
                        tint = tokens.textMuted,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Invoice",
                        fontSize = 12.sp,
                        color = tokens.text,
                        maxLines = 1
                    )
                }

                // 3. WhatsApp Reminder Button
                OutlinedButton(
                    onClick = onSendReminder,
                    modifier = Modifier
                        .weight(1.2f)
                        .heightIn(min = 48.dp)
                        .testTag("remind_button_$invNum"),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(0.5.dp, whatsAppAccent.copy(alpha = 0.5f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = whatsAppAccent),
                    contentPadding = PaddingValues(horizontal = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = null,
                        tint = whatsAppAccent,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isCollect) "Remind" else "Share",
                        fontSize = 12.sp,
                        color = whatsAppAccent,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecordBillPaymentSheet(
    bill: UnpaidBillDetail,
    isCollect: Boolean,
    onDismiss: () -> Unit,
    onConfirmPayment: (amountPaise: Long, mode: PaymentMode, remarks: String) -> Unit
) {
    val tokens = PabaruTheme.tokens
    val context = LocalContext.current
    val pendingRupees = remember(bill.pendingPaise) {
        String.format(Locale.ENGLISH, "%.2f", bill.pendingPaise / 100.0)
    }
    var amountInput by remember(bill.transaction.id) { mutableStateOf(pendingRupees) }
    var selectedMode by remember { mutableStateOf(PaymentMode.Cash) }
    var remarksInput by remember(bill.transaction.id) {
        mutableStateOf(
            if (isCollect) "Payment for ${bill.transaction.invoiceNumber.ifEmpty { bill.transaction.id }}"
            else "Payment to ${bill.party?.name ?: "Supplier"} for bill"
        )
    }

    val activeColor = if (isCollect) tokens.success else tokens.danger

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
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            // Title
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isCollect) "Receive Payment" else "Pay Supplier Bill",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = tokens.text
                    )
                    Text(
                        text = "${bill.party?.name ?: "Counter Customer"} • Bill #${bill.transaction.invoiceNumber.ifEmpty { bill.transaction.id }}",
                        fontSize = 12.sp,
                        color = tokens.textMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Surface(
                    color = activeColor.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(0.5.dp, activeColor)
                ) {
                    Text(
                        text = "Pending: ${FinancialEngine.formatPaise(bill.pendingPaise)}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = activeColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Amount Field
            Text("Payment Amount (₹)", fontSize = 12.sp, color = tokens.textMuted)
            Spacer(modifier = Modifier.height(4.dp))
            OutlinedTextField(
                value = amountInput,
                onValueChange = { amountInput = FinancialEngine.sanitizeDecimalInput(it) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                shape = RoundedCornerShape(10.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = tokens.bg,
                    unfocusedContainerColor = tokens.bg,
                    focusedBorderColor = activeColor,
                    unfocusedBorderColor = tokens.cardBorder,
                    focusedTextColor = tokens.text,
                    unfocusedTextColor = tokens.text
                )
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Payment Mode Selector
            Text("Payment Mode", fontSize = 12.sp, color = tokens.textMuted)
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(PaymentMode.Cash, PaymentMode.UPI, PaymentMode.Bank, PaymentMode.Cheque).forEach { mode ->
                    val isSelected = selectedMode == mode
                    Surface(
                        onClick = { selectedMode = mode },
                        shape = RoundedCornerShape(8.dp),
                        color = if (isSelected) activeColor.copy(alpha = 0.15f) else tokens.bg,
                        border = BorderStroke(0.5.dp, if (isSelected) activeColor else tokens.cardBorder),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                    ) {
                        Box(
                            modifier = Modifier.padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = mode.name,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) activeColor else tokens.textMuted
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Remarks Field
            Text("Notes / Remarks", fontSize = 12.sp, color = tokens.textMuted)
            Spacer(modifier = Modifier.height(4.dp))
            OutlinedTextField(
                value = remarksInput,
                onValueChange = { remarksInput = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(10.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = tokens.bg,
                    unfocusedContainerColor = tokens.bg,
                    focusedBorderColor = activeColor,
                    unfocusedBorderColor = tokens.cardBorder,
                    focusedTextColor = tokens.text,
                    unfocusedTextColor = tokens.text
                )
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Confirm Button
            Button(
                onClick = {
                    val parsedPaise = FinancialEngine.parsePaiseFromRupees(amountInput)
                    if (parsedPaise > 0L) {
                        onConfirmPayment(parsedPaise, selectedMode, remarksInput)
                    } else {
                        Toast.makeText(context, "Enter a valid payment amount greater than ₹0", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = activeColor,
                    contentColor = tokens.primaryText
                )
            ) {
                Text(
                    text = if (isCollect) "Confirm Collection" else "Confirm Payment",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
