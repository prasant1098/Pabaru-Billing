package com.example.ui.screens

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.engine.ExportEngine
import com.example.engine.FinancialEngine
import com.example.engine.FinancialEngine.GstBreakdown
import com.example.model.*
import com.example.ui.theme.PabaruTheme
import com.example.ui.theme.danger
import com.example.ui.theme.dangerContainer
import com.example.ui.theme.success
import com.example.ui.theme.successContainer
import com.example.ui.theme.onSuccessContainer
import com.example.ui.theme.warning
import com.example.ui.theme.warningContainer
import com.example.viewmodel.PabaruUiState
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InvoiceMenuScreen(
    uiState: PabaruUiState,
    onSaveInvoice: (String?, PaymentMode, TransactionType, List<InvoiceLineItem>, String) -> Boolean,
    onVoid: (Transaction) -> Unit,
    onAddParty: (Party) -> Unit = {},
    onFormOpenChanged: ((Boolean) -> Unit)? = null
) {
    val context = LocalContext.current
    val tokens = PabaruTheme.tokens

    // Screen mode: false = Invoices List View, true = Full-screen New Tax Invoice Form
    var isCreatingDoc by remember { mutableStateOf(false) }
    var activeDocType by remember { mutableStateOf(TransactionType.Sale) }
    var duplicateTemplate by remember { mutableStateOf<Transaction?>(null) }

    // Notify parent to hide topBar and bottomBar when form is open
    LaunchedEffect(isCreatingDoc) {
        onFormOpenChanged?.invoke(isCreatingDoc)
    }

    // Dialog states for Hub
    var voidingInvoice by remember { mutableStateOf<Transaction?>(null) }
    var viewingInvoice by remember { mutableStateOf<Transaction?>(null) }

    // Search and Filters for Hub
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilterIndex by remember { mutableStateOf(0) } // 0: All, 1: Today, 2: This month, 3: Unpaid, 4: Paid
    var selectedTabItem by remember { mutableStateOf(0) } // 0: Invoices, 1: Quotations, 2: Notes

    val openCreateForm: (TransactionType) -> Unit = { type ->
        duplicateTemplate = null
        activeDocType = type
        isCreatingDoc = true
    }

    val openDuplicate: (Transaction) -> Unit = { txn ->
        duplicateTemplate = txn
        activeDocType = txn.type
        isCreatingDoc = true
    }

    BackHandler(enabled = isCreatingDoc || viewingInvoice != null || voidingInvoice != null) {
        when {
            isCreatingDoc -> {
                isCreatingDoc = false
                duplicateTemplate = null
            }
            viewingInvoice != null -> viewingInvoice = null
            voidingInvoice != null -> voidingInvoice = null
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(tokens.bg)
    ) {
        if (isCreatingDoc) {
            // ==========================================
            // FULL-SCREEN "NEW TAX INVOICE" FORM SCREEN
            // ==========================================
            NewTaxInvoiceFormScreen(
                uiState = uiState,
                initialDocType = activeDocType,
                templateTxn = duplicateTemplate,
                onBack = {
                    isCreatingDoc = false
                    duplicateTemplate = null
                },
                onAddParty = onAddParty,
                onSaveInvoice = { partyId, mode, type, items, remarks ->
                    try {
                        val saved = onSaveInvoice(partyId, mode, type, items, remarks)
                        if (saved) {
                            Toast.makeText(context, "Document issued successfully", Toast.LENGTH_SHORT).show()
                            isCreatingDoc = false
                            duplicateTemplate = null
                        }
                    } catch (e: Exception) {
                        Toast.makeText(context, e.message ?: "Failed to issue invoice", Toast.LENGTH_LONG).show()
                    }
                }
            )
        } else {
            // ==========================================
            // INVOICES HUB DASHBOARD & LIST VIEW
            // ==========================================
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 600.dp)
                    .align(Alignment.TopCenter)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Title
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Invoices",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Medium,
                        color = tokens.text
                    )
                }

                // Primary Buttons: "+ Tax invoice" and "+ Non-GST bill"
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = { openCreateForm(TransactionType.Sale) },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = tokens.warning,
                            contentColor = tokens.primaryText
                        ),
                        elevation = ButtonDefaults.buttonElevation(0.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Add,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "+ Tax invoice",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1
                        )
                    }

                    Button(
                        onClick = { openCreateForm(TransactionType.NonGstBill) },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = tokens.success,
                            contentColor = tokens.primaryText
                        ),
                        elevation = ButtonDefaults.buttonElevation(0.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.ReceiptLong,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "+ Non-GST bill",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1
                        )
                    }
                }

                // Secondary Buttons: "Credit note" and "Quotation"
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .clickable { openCreateForm(TransactionType.CreditNote) },
                        color = tokens.secondaryBg,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(0.5.dp, tokens.cardBorder)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.AssignmentReturn,
                                contentDescription = null,
                                tint = tokens.text,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Credit note",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = tokens.text,
                                maxLines = 1
                            )
                        }
                    }

                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .clickable { openCreateForm(TransactionType.Quotation) },
                        color = tokens.secondaryBg,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(0.5.dp, tokens.cardBorder)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.RequestQuote,
                                contentDescription = null,
                                tint = tokens.text,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Quotation",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = tokens.text,
                                maxLines = 1
                            )
                        }
                    }
                }

                val todayStr = remember { FinancialEngine.getTodayDate() }
                val monthPrefix = remember(todayStr) { todayStr.take(7) }

                // Summary Strip Card (real current-month sales & FIFO-adjusted credit pending)
                val pendingByCreditTxnId = remember(uiState.transactions, uiState.parties) {
                    val activeTxns = uiState.transactions.filter { !it.isVoided }
                    val pendingMap = HashMap<String, Long>()
                    val partiesMap = uiState.parties.associateBy { it.id }

                    val paymentsInByParty = activeTxns
                        .filter { it.type == TransactionType.PaymentIn }
                        .groupBy { it.partyId?.takeIf { id -> id.isNotBlank() } ?: "COUNTER" }
                    val creditNotesByParty = activeTxns
                        .filter { it.type == TransactionType.CreditNote && it.paymentMode == PaymentMode.Credit }
                        .groupBy { it.partyId?.takeIf { id -> id.isNotBlank() } ?: "COUNTER" }
                    val creditInvoicesByParty = activeTxns
                        .filter { (it.type == TransactionType.Sale || it.type == TransactionType.NonGstBill) && it.paymentMode == PaymentMode.Credit }
                        .groupBy { it.partyId?.takeIf { id -> id.isNotBlank() } ?: "COUNTER" }

                    creditInvoicesByParty.forEach { (partyKey, invoices) ->
                        val party = if (partyKey == "COUNTER") null else partiesMap[partyKey]
                        val totalSettlement = (paymentsInByParty[partyKey]?.sumOf { it.amountPaise } ?: 0L) +
                            (creditNotesByParty[partyKey]?.sumOf { it.amountPaise } ?: 0L)
                        var remaining = totalSettlement
                        if (party != null && party.openingBalanceType == BalanceType.Receivable && party.openingBalancePaise > 0L) {
                            val coveredOpening = minOf(remaining, party.openingBalancePaise)
                            remaining -= coveredOpening
                        }
                        invoices.sortedWith(
                            compareBy<Transaction>({ FinancialEngine.normalizeDate(it.date) }, { it.id })
                        ).forEach { inv ->
                            val covered = minOf(remaining, inv.amountPaise)
                            remaining -= covered
                            pendingMap[inv.id] = (inv.amountPaise - covered).coerceAtLeast(0L)
                        }
                    }
                    pendingMap
                }

                val totalBilledPaise = remember(uiState.transactions, monthPrefix) {
                    uiState.transactions
                        .filter {
                            !it.isVoided &&
                                (it.type == TransactionType.Sale || it.type == TransactionType.NonGstBill) &&
                                FinancialEngine.normalizeDate(it.date).startsWith(monthPrefix)
                        }
                        .sumOf { it.amountPaise }
                }
                val totalPendingPaise = remember(uiState.transactions, pendingByCreditTxnId) {
                    uiState.transactions
                        .filter {
                            !it.isVoided &&
                                (it.type == TransactionType.Sale || it.type == TransactionType.NonGstBill) &&
                                it.paymentMode == PaymentMode.Credit
                        }
                        .sumOf { pendingByCreditTxnId[it.id] ?: it.amountPaise }
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = tokens.card),
                    border = BorderStroke(0.5.dp, tokens.cardBorder),
                    elevation = CardDefaults.cardElevation(0.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Billed this month",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Normal,
                                color = tokens.textMuted
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = formatKStat(totalBilledPaise),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Medium,
                                color = tokens.text
                            )
                        }

                        Box(
                            modifier = Modifier
                                .height(36.dp)
                                .width(0.5.dp)
                                .background(tokens.cardBorder)
                        )

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Credit pending",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Normal,
                                color = tokens.textMuted
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = formatKStat(totalPendingPaise),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Medium,
                                color = tokens.warning
                            )
                        }
                    }
                }

                // Search Bar
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
                            contentDescription = "Search",
                            tint = tokens.textMuted,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(modifier = Modifier.weight(1f)) {
                            if (searchQuery.isEmpty()) {
                                Text(
                                    text = "Search customer or invoice number",
                                    fontSize = 12.sp,
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
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }

                // Filter Chips
                val filterChips = listOf("All", "Today", "This month", "Unpaid", "Paid")
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    filterChips.forEachIndexed { index, title ->
                        val isSelected = selectedFilterIndex == index
                        Surface(
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .clickable { selectedFilterIndex = index },
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) tokens.successContainer else tokens.card,
                            border = BorderStroke(
                                0.5.dp,
                                if (isSelected) tokens.success else tokens.cardBorder
                            )
                        ) {
                            Box(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = title,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = if (isSelected) tokens.success else tokens.textMuted
                                )
                            }
                        }
                    }
                }

                // Tabs: Invoices | Quotations | Notes
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
                            .padding(3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        listOf("Invoices", "Quotations", "Notes").forEachIndexed { tabIndex, tabTitle ->
                            val isTabActive = selectedTabItem == tabIndex
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .clickable { selectedTabItem = tabIndex },
                                shape = RoundedCornerShape(9.dp),
                                color = if (isTabActive) tokens.secondaryBg else Color.Transparent,
                                border = if (isTabActive) BorderStroke(0.5.dp, tokens.cardBorder) else null
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = tabTitle,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = if (isTabActive) tokens.text else tokens.textMuted
                                    )
                                }
                            }
                        }
                    }
                }

                // Invoices List
                val tabFilteredInvoices = remember(
                    uiState.transactions,
                    uiState.parties,
                    pendingByCreditTxnId,
                    selectedTabItem,
                    selectedFilterIndex,
                    searchQuery,
                    todayStr,
                    monthPrefix
                ) {
                    uiState.transactions.filter { txn ->
                        val matchesTab = when (selectedTabItem) {
                            0 -> txn.type == TransactionType.Sale || txn.type == TransactionType.NonGstBill
                            1 -> txn.type == TransactionType.Quotation
                            2 -> txn.type == TransactionType.CreditNote || txn.type == TransactionType.DebitNote
                            else -> true
                        }
                        if (!matchesTab) return@filter false

                        val normDate = FinancialEngine.normalizeDate(txn.date)
                        val isCreditInvoice = (txn.type == TransactionType.Sale || txn.type == TransactionType.NonGstBill) &&
                            txn.paymentMode == PaymentMode.Credit
                        val isPendingCredit = if (isCreditInvoice) {
                            (pendingByCreditTxnId[txn.id] ?: txn.amountPaise) > 0L
                        } else {
                            txn.paymentMode == PaymentMode.Credit
                        }
                        val matchesFilter = when (selectedFilterIndex) {
                            1 -> normDate == todayStr
                            2 -> normDate.startsWith(monthPrefix)
                            3 -> !txn.isVoided && isPendingCredit
                            4 -> !txn.isVoided && !isPendingCredit
                            else -> true
                        }
                        if (!matchesFilter) return@filter false

                        if (searchQuery.isNotBlank()) {
                            val q = searchQuery.lowercase()
                            val partyName = uiState.parties.find { it.id == txn.partyId }?.name?.lowercase().orEmpty()
                            txn.invoiceNumber.lowercase().contains(q) ||
                                partyName.contains(q) ||
                                txn.remarks.lowercase().contains(q)
                        } else true
                    }
                }

                if (tabFilteredInvoices.isEmpty()) {
                    InvoiceEmptyState(
                        onCreateClick = { openCreateForm(TransactionType.Sale) }
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(bottom = 84.dp)
                    ) {
                        items(tabFilteredInvoices, key = { it.id }) { inv ->
                            InvoiceItemCard(
                                invoice = inv,
                                uiState = uiState,
                                onShare = {
                                    val party = uiState.parties.find { it.id == inv.partyId }
                                    ExportEngine.generateAndShareInvoicePdf(
                                        context,
                                        inv,
                                        uiState.profile,
                                        party,
                                        uiState.bankAccounts.firstOrNull()
                                    )
                                },
                                onView = { viewingInvoice = inv },
                                onDuplicate = { openDuplicate(inv) },
                                onVoidClick = { voidingInvoice = inv }
                            )
                        }
                    }
                }
            }

            // Floating Action Button
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(end = 20.dp, bottom = 20.dp),
                contentAlignment = Alignment.BottomEnd
            ) {
                FloatingActionButton(
                    onClick = { openCreateForm(TransactionType.Sale) },
                    containerColor = tokens.warning,
                    contentColor = tokens.primaryText,
                    shape = CircleShape,
                    elevation = FloatingActionButtonDefaults.elevation(0.dp),
                    modifier = Modifier.size(56.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Add,
                        contentDescription = "New tax bill",
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
        }

        // Void Confirmation Dialog
        voidingInvoice?.let { targetTxn ->
            AlertDialog(
                onDismissRequest = { voidingInvoice = null },
                containerColor = tokens.card,
                shape = RoundedCornerShape(12.dp),
                tonalElevation = 0.dp,
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Outlined.Warning,
                            contentDescription = null,
                            tint = tokens.danger,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Confirm Void Invoice",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            color = tokens.text
                        )
                    }
                },
                text = {
                    Text(
                        text = "Are you sure you want to void invoice ${targetTxn.invoiceNumber.ifEmpty { targetTxn.id }}? This action reverses accounting entries and is cryptographically recorded in the SHA-256 audit trail.",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Normal,
                        color = tokens.textMuted,
                        lineHeight = 18.sp
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            onVoid(targetTxn)
                            voidingInvoice = null
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = tokens.danger,
                            contentColor = tokens.primaryText
                        ),
                        modifier = Modifier.heightIn(min = 48.dp),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = "Void Invoice",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                },
                dismissButton = {
                    OutlinedButton(
                        onClick = { voidingInvoice = null },
                        modifier = Modifier.heightIn(min = 48.dp),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(0.5.dp, tokens.cardBorder)
                    ) {
                        Text(
                            text = "Cancel",
                            color = tokens.textMuted,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Normal
                        )
                    }
                }
            )
        }

        // View Invoice Details Dialog
        viewingInvoice?.let { viewTxn ->
            val party = uiState.parties.find { it.id == viewTxn.partyId }
            AlertDialog(
                onDismissRequest = { viewingInvoice = null },
                containerColor = tokens.card,
                shape = RoundedCornerShape(12.dp),
                tonalElevation = 0.dp,
                title = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = viewTxn.invoiceNumber.ifEmpty { viewTxn.id },
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            color = tokens.text,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        IconButton(
                            onClick = { viewingInvoice = null },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Close,
                                contentDescription = "Close",
                                tint = tokens.textMuted,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = party?.name ?: "Counter Customer",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            color = tokens.text
                        )
                        Text(
                            text = "Date: ${viewTxn.date} · Mode: ${viewTxn.paymentMode.name}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Normal,
                            color = tokens.textMuted
                        )
                        if (party?.gstin?.isNotBlank() == true) {
                            Text(
                                text = "GSTIN: ${party.gstin}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Normal,
                                color = tokens.textMuted,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        HorizontalDivider(thickness = 0.5.dp, color = tokens.cardBorder)
                        viewTxn.items.forEach { item ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "${item.name} (${FinancialEngine.formatQuantityMilli(item.quantityMilli)} ${item.unit})",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Normal,
                                    color = tokens.text,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = FinancialEngine.formatPaise(item.totalPaise),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = tokens.text
                                )
                            }
                        }
                        HorizontalDivider(thickness = 0.5.dp, color = tokens.cardBorder)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Total Amount",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = tokens.text
                            )
                            Text(
                                text = FinancialEngine.formatPaise(viewTxn.amountPaise),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium,
                                color = tokens.success
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            ExportEngine.generateAndShareInvoicePdf(
                                context,
                                viewTxn,
                                uiState.profile,
                                party,
                                uiState.bankAccounts.firstOrNull()
                            )
                            viewingInvoice = null
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = tokens.secondaryBg,
                            contentColor = tokens.text
                        ),
                        modifier = Modifier.heightIn(min = 48.dp),
                        border = BorderStroke(0.5.dp, tokens.cardBorder),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Share,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Share PDF",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                },
                dismissButton = {
                    OutlinedButton(
                        onClick = { viewingInvoice = null },
                        modifier = Modifier.heightIn(min = 48.dp),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(0.5.dp, tokens.cardBorder)
                    ) {
                        Text(
                            text = "Done",
                            color = tokens.textMuted,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Normal
                        )
                    }
                }
            )
        }
    }
}

// =========================================================================
// REDESIGNED FULL-SCREEN "NEW TAX INVOICE" FORM COMPONENT
// =========================================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewTaxInvoiceFormScreen(
    uiState: PabaruUiState,
    initialDocType: TransactionType,
    templateTxn: Transaction? = null,
    onBack: () -> Unit,
    onAddParty: (Party) -> Unit = {},
    onSaveInvoice: (String?, PaymentMode, TransactionType, List<InvoiceLineItem>, String) -> Unit
) {
    val context = LocalContext.current
    val tokens = PabaruTheme.tokens

    // Document Type Dropdown
    var docType by remember { mutableStateOf(initialDocType) }
    var showDocTypeDropdown by remember { mutableStateOf(false) }

    // Customer Selection State
    val defaultCustomer = uiState.parties.firstOrNull { !it.isSupplier }
    var selectedPartyId by remember { mutableStateOf<String?>(templateTxn?.partyId ?: defaultCustomer?.id) }
    var showCustomerPicker by remember { mutableStateOf(false) }
    var showNewCustomerDialog by remember { mutableStateOf(false) }
    var customerSearchQuery by remember { mutableStateOf("") }

    val initialParty = uiState.parties.find { it.id == selectedPartyId }
    var buyerGstinInput by remember { mutableStateOf(initialParty?.gstin.orEmpty()) }

    val party = uiState.parties.find { it.id == selectedPartyId }
    val gstinValidation = remember(buyerGstinInput) {
        FinancialEngine.validateGstin(buyerGstinInput, allowBlank = true)
    }
    val isGstinValid = buyerGstinInput.isNotBlank() && gstinValidation.isValid
    val effectiveStateCode = party?.stateCode?.ifBlank { null }
        ?: if (isGstinValid && buyerGstinInput.trim().length >= 2) buyerGstinInput.trim().take(2) else null
    val isZeroRatedExport = effectiveStateCode == "96"
    val isInterstate = effectiveStateCode != null && effectiveStateCode != uiState.profile.homeStateCode

    val gstinPan = remember(buyerGstinInput) {
        val trimmed = buyerGstinInput.trim().uppercase()
        if (trimmed.length >= 12) trimmed.substring(2, 12) else ""
    }
    val gstinState = remember(buyerGstinInput) {
        val trimmed = buyerGstinInput.trim().uppercase()
        if (trimmed.length >= 2) trimmed.take(2) else ""
    }

    val isNonGst = docType == TransactionType.NonGstBill

    // Items Section State
    val defaultProduct = uiState.products.firstOrNull { !it.isArchived }
    val originalRateByItemId = remember {
        mutableStateMapOf<String, Int>().apply {
            templateTxn?.items?.forEach { item ->
                if (item.gstRateBps > 0) put(item.itemId, item.gstRateBps)
            }
            if (defaultProduct != null && defaultProduct.gstRateBps > 0) {
                put(defaultProduct.id, defaultProduct.gstRateBps)
            }
        }
    }
    var addedItems by remember {
        val seedItems = if (templateTxn != null && templateTxn.items.isNotEmpty()) {
            templateTxn.items
        } else if (defaultProduct != null) {
            val prod = defaultProduct
            val taxable = prod.salePricePaise
            val effRateBps = if (isNonGst) 0 else prod.gstRateBps
            val gst = if (isNonGst) {
                GstBreakdown(0L, 0L, 0L, taxable)
            } else {
                FinancialEngine.calculateGstBps(taxable, effRateBps, isInterstate, isZeroRatedExport)
            }
            listOf(
                InvoiceLineItem(
                    itemId = prod.id,
                    name = prod.name,
                    hsnCode = prod.hsnCode,
                    quantityMilli = 1000L,
                    unit = prod.unit,
                    ratePaise = prod.salePricePaise,
                    gstRateBps = effRateBps,
                    taxableAmountPaise = taxable,
                    cgstPaise = gst.cgstPaise,
                    sgstPaise = gst.sgstPaise,
                    igstPaise = gst.igstPaise,
                    totalPaise = gst.totalPaise
                )
            )
        } else {
            emptyList()
        }
        mutableStateOf(seedItems)
    }

    // Recalculate GST splits whenever interstate/export/non-GST mode changes
    LaunchedEffect(isInterstate, isZeroRatedExport, isNonGst) {
        addedItems = addedItems.map { item ->
            if (item.gstRateBps > 0) {
                originalRateByItemId[item.itemId] = item.gstRateBps
            }
            val prodRateBps = if (isNonGst) 0 else {
                if (item.gstRateBps > 0) item.gstRateBps
                else originalRateByItemId[item.itemId]
                    ?: uiState.products.find { it.id == item.itemId }?.gstRateBps
                    ?: 0
            }
            val gst = if (isNonGst) {
                GstBreakdown(0L, 0L, 0L, item.taxableAmountPaise)
            } else {
                FinancialEngine.calculateGstBps(item.taxableAmountPaise, prodRateBps, isInterstate, isZeroRatedExport)
            }
            item.copy(
                gstRateBps = prodRateBps,
                cgstPaise = gst.cgstPaise,
                sgstPaise = gst.sgstPaise,
                igstPaise = gst.igstPaise,
                totalPaise = gst.totalPaise
            )
        }
    }

    // Add Item Bottom Sheet
    var showAddItemSheet by remember { mutableStateOf(false) }
    var itemSearchQuery by remember { mutableStateOf("") }

    // Payment Mode State (Cash, UPI, Bank, Credit)
    var paymentMode by remember { mutableStateOf(templateTxn?.paymentMode ?: PaymentMode.Cash) }
    var dueDateInput by remember {
        mutableStateOf(
            SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH).format(
                Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 15) }.time
            )
        )
    }

    // Collapsible Tax Summary State (Collapsed by default)
    var isTaxSummaryExpanded by remember { mutableStateOf(false) }

    // Calculate totals
    val taxableAmountPaise = addedItems.sumOf { it.taxableAmountPaise }.coerceAtLeast(0L)
    val totalCgstPaise = if (isNonGst) 0L else addedItems.sumOf { it.cgstPaise }.coerceAtLeast(0L)
    val totalSgstPaise = if (isNonGst) 0L else addedItems.sumOf { it.sgstPaise }.coerceAtLeast(0L)
    val totalIgstPaise = if (isNonGst) 0L else addedItems.sumOf { it.igstPaise }.coerceAtLeast(0L)
    val totalGstPaise = (if (isInterstate) totalIgstPaise else (totalCgstPaise + totalSgstPaise)).coerceAtLeast(0L)
    val grandTotalPaise = (taxableAmountPaise + totalGstPaise).coerceAtLeast(0L)

    val docTypeLabel = when (docType) {
        TransactionType.NonGstBill -> "Non-GST bill (Estimate / Cash Memo)"
        TransactionType.CreditNote -> "Credit note"
        TransactionType.DebitNote -> "Debit note"
        TransactionType.Quotation -> "Quotation"
        else -> "Standard tax invoice"
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
            // ==========================================
            // HEADER (Back arrow, Title, Document Dropdown)
            // ==========================================
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                color = tokens.headerBg
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = "Back",
                            tint = tokens.headerText,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    Text(
                        text = if (isNonGst) "New Non-GST bill" else "New tax invoice",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium,
                        color = tokens.headerText,
                        modifier = Modifier.weight(1f)
                    )

                    // Right dropdown chip
                    Box {
                        Surface(
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .clickable { showDocTypeDropdown = true },
                            color = tokens.headerText.copy(alpha = 0.14f),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(0.5.dp, tokens.headerText.copy(alpha = 0.35f))
                        ) {
                            Row(
                                modifier = Modifier
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (docType == TransactionType.NonGstBill) "Non-GST bill" else docTypeLabel,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = tokens.headerText
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    imageVector = Icons.Outlined.KeyboardArrowDown,
                                    contentDescription = "Document type",
                                    tint = tokens.headerText,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        DropdownMenu(
                            expanded = showDocTypeDropdown,
                            onDismissRequest = { showDocTypeDropdown = false },
                            modifier = Modifier
                                .background(tokens.card)
                                .border(0.5.dp, tokens.cardBorder, RoundedCornerShape(8.dp))
                        ) {
                            listOf(
                                "Standard tax invoice" to TransactionType.Sale,
                                "Non-GST Bill (Estimate / Cash Memo)" to TransactionType.NonGstBill,
                                "Credit note" to TransactionType.CreditNote,
                                "Debit note" to TransactionType.DebitNote,
                                "Quotation" to TransactionType.Quotation
                            ).forEach { (label, type) ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = label,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = tokens.text
                                        )
                                    },
                                    onClick = {
                                        docType = type
                                        showDocTypeDropdown = false
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // ==========================================
            // SCROLLABLE FORM BODY
            // ==========================================
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(top = 4.dp, bottom = 100.dp)
            ) {
                // 1. CUSTOMER SECTION (card)
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = tokens.card),
                        border = BorderStroke(0.5.dp, tokens.cardBorder),
                        elevation = CardDefaults.cardElevation(0.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Customer",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Normal,
                                color = tokens.textMuted
                            )

                            // Search field row with "+ New" button
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .clickable { showCustomerPicker = true },
                                    shape = RoundedCornerShape(10.dp),
                                    color = tokens.secondaryBg,
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
                                            contentDescription = "Search Customer",
                                            tint = tokens.textMuted,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = party?.name ?: "Walk-in / Counter Customer",
                                            fontSize = 13.sp,
                                            fontWeight = if (party != null) FontWeight.Medium else FontWeight.Normal,
                                            color = if (party != null) tokens.text else tokens.textMuted,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }

                                // "+ New" button
                                OutlinedButton(
                                    onClick = { showNewCustomerDialog = true },
                                    modifier = Modifier.height(48.dp),
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(0.5.dp, tokens.cardBorder),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        containerColor = tokens.secondaryBg,
                                        contentColor = tokens.text
                                    ),
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp)
                                ) {
                                    Text(
                                        text = "+ New",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = tokens.text
                                    )
                                }
                            }

                            // Compact selected customer row
                            if (party != null) {
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    color = tokens.secondaryBg,
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(0.5.dp, tokens.cardBorder)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 10.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = party.name,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = tokens.text
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "State ${party.stateCode.ifEmpty { uiState.profile.homeStateCode }}",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Normal,
                                                color = tokens.textMuted
                                            )
                                        }

                                        Box(
                                            modifier = Modifier
                                                .heightIn(min = 48.dp)
                                                .clickable { showCustomerPicker = true }
                                                .padding(horizontal = 8.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = "Change",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = tokens.primary
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(2.dp))

                            // GSTIN Field
                            Text(
                                text = "Buyer GSTIN",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Normal,
                                color = tokens.textMuted
                            )

                            val hasGstinError = buyerGstinInput.isNotBlank() && !isGstinValid
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp),
                                shape = RoundedCornerShape(10.dp),
                                color = tokens.secondaryBg,
                                border = BorderStroke(
                                    0.5.dp,
                                    if (hasGstinError) tokens.danger else tokens.cardBorder
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(modifier = Modifier.weight(1f)) {
                                        if (buyerGstinInput.isEmpty()) {
                                            Text(
                                                text = "e.g. 27AAACS1234M1Z2",
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Normal,
                                                color = tokens.textMuted
                                            )
                                        }
                                        BasicTextField(
                                            value = buyerGstinInput,
                                            onValueChange = { buyerGstinInput = it.uppercase().take(15) },
                                            singleLine = true,
                                            cursorBrush = SolidColor(tokens.primary),
                                            textStyle = androidx.compose.ui.text.TextStyle(
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Medium,
                                                fontFamily = FontFamily.Monospace,
                                                color = tokens.text
                                            ),
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }

                                    if (isGstinValid) {
                                        Icon(
                                            imageVector = Icons.Outlined.Check,
                                            contentDescription = "Valid GSTIN",
                                            tint = tokens.success,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }

                            if (hasGstinError) {
                                Text(
                                    text = gstinValidation.message,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Normal,
                                    color = tokens.danger
                                )
                            } else if (isGstinValid) {
                                Text(
                                    text = "Valid GSTIN · State $gstinState · PAN $gstinPan",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Normal,
                                    color = tokens.textMuted
                                )
                            }
                        }
                    }
                }

                // 2. ITEMS SECTION (card)
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = tokens.card),
                        border = BorderStroke(0.5.dp, tokens.cardBorder),
                        elevation = CardDefaults.cardElevation(0.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "Items (${addedItems.size})",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = tokens.text
                            )

                            if (addedItems.isEmpty()) {
                                Text(
                                    text = "No items added yet. Tap '+ Add item' below.",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Normal,
                                    color = tokens.textMuted,
                                    modifier = Modifier.padding(vertical = 8.dp)
                                )
                            } else {
                                addedItems.forEachIndexed { index, itm ->
                                    Column(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.Top
                                        ) {
                                            Text(
                                                text = itm.name,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = tokens.text,
                                                modifier = Modifier.weight(1f)
                                            )

                                            Spacer(modifier = Modifier.width(8.dp))

                                            Text(
                                                text = FinancialEngine.formatPaise(itm.totalPaise),
                                                fontSize = 15.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = tokens.text
                                            )
                                        }

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = "${FinancialEngine.formatQuantityMilli(itm.quantityMilli)} × ${FinancialEngine.formatPaise(itm.ratePaise)}",
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Normal,
                                                    color = tokens.textMuted
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Surface(
                                                    color = tokens.secondaryBg,
                                                    shape = RoundedCornerShape(4.dp),
                                                    border = BorderStroke(0.5.dp, tokens.cardBorder)
                                                ) {
                                                    Text(
                                                        text = if (isNonGst) "Non-GST" else "GST ${GstSlabs.label(itm.gstRateBps)}",
                                                        fontSize = 12.sp,
                                                        fontWeight = FontWeight.Normal,
                                                        color = tokens.textMuted,
                                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }

                                            // Stepper (− qty +) and trash icon
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Surface(
                                                    modifier = Modifier
                                                        .size(48.dp)
                                                        .clickable {
                                                            val newQtyMilli = itm.quantityMilli - 1000L
                                                            if (newQtyMilli <= 0L) {
                                                                addedItems = addedItems.filterIndexed { i, _ -> i != index }
                                                            } else {
                                                                val taxable = FinancialEngine.lineTaxable(newQtyMilli, itm.ratePaise)
                                                                val effBps = if (isNonGst) 0 else itm.gstRateBps
                                                                val gst = if (isNonGst) {
                                                                    GstBreakdown(0L, 0L, 0L, taxable)
                                                                } else {
                                                                    FinancialEngine.calculateGstBps(taxable, effBps, isInterstate, isZeroRatedExport)
                                                                }
                                                                addedItems = addedItems.mapIndexed { i, old ->
                                                                    if (i == index) old.copy(
                                                                        quantityMilli = newQtyMilli,
                                                                        gstRateBps = effBps,
                                                                        taxableAmountPaise = taxable,
                                                                        cgstPaise = gst.cgstPaise,
                                                                        sgstPaise = gst.sgstPaise,
                                                                        igstPaise = gst.igstPaise,
                                                                        totalPaise = gst.totalPaise
                                                                    ) else old
                                                                }
                                                            }
                                                        },
                                                    shape = RoundedCornerShape(8.dp),
                                                    color = tokens.secondaryBg,
                                                    border = BorderStroke(0.5.dp, tokens.cardBorder)
                                                ) {
                                                    Box(contentAlignment = Alignment.Center) {
                                                        Text("−", fontSize = 16.sp, color = tokens.text, fontWeight = FontWeight.Medium)
                                                    }
                                                }

                                                Text(
                                                    text = FinancialEngine.formatQuantityMilli(itm.quantityMilli),
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    color = tokens.text,
                                                    modifier = Modifier.padding(horizontal = 8.dp)
                                                )

                                                Surface(
                                                    modifier = Modifier
                                                        .size(48.dp)
                                                        .clickable {
                                                            val newQtyMilli = itm.quantityMilli + 1000L
                                                            val taxable = FinancialEngine.lineTaxable(newQtyMilli, itm.ratePaise)
                                                            val effBps = if (isNonGst) 0 else itm.gstRateBps
                                                            val gst = if (isNonGst) {
                                                                GstBreakdown(0L, 0L, 0L, taxable)
                                                            } else {
                                                                FinancialEngine.calculateGstBps(taxable, effBps, isInterstate, isZeroRatedExport)
                                                            }
                                                            addedItems = addedItems.mapIndexed { i, old ->
                                                                if (i == index) old.copy(
                                                                    quantityMilli = newQtyMilli,
                                                                    gstRateBps = effBps,
                                                                    taxableAmountPaise = taxable,
                                                                    cgstPaise = gst.cgstPaise,
                                                                    sgstPaise = gst.sgstPaise,
                                                                    igstPaise = gst.igstPaise,
                                                                    totalPaise = gst.totalPaise
                                                                ) else old
                                                            }
                                                        },
                                                    shape = RoundedCornerShape(8.dp),
                                                    color = tokens.secondaryBg,
                                                    border = BorderStroke(0.5.dp, tokens.cardBorder)
                                                ) {
                                                    Box(contentAlignment = Alignment.Center) {
                                                        Text("+", fontSize = 16.sp, color = tokens.text, fontWeight = FontWeight.Medium)
                                                    }
                                                }

                                                Spacer(modifier = Modifier.width(6.dp))

                                                IconButton(
                                                    onClick = {
                                                        addedItems = addedItems.filterIndexed { i, _ -> i != index }
                                                    },
                                                    modifier = Modifier.size(48.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Outlined.Delete,
                                                        contentDescription = "Remove item",
                                                        tint = tokens.danger,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                            }
                                        }

                                        if (index < addedItems.lastIndex) {
                                            HorizontalDivider(thickness = 0.5.dp, color = tokens.cardBorder, modifier = Modifier.padding(vertical = 4.dp))
                                        }
                                    }
                                }
                            }

                            OutlinedButton(
                                onClick = {
                                    itemSearchQuery = ""
                                    showAddItemSheet = true
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp),
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(0.5.dp, tokens.cardBorder),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = tokens.secondaryBg,
                                    contentColor = tokens.text
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Add,
                                    contentDescription = null,
                                    tint = tokens.text,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "+ Add item",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = tokens.text
                                )
                            }
                        }
                    }
                }

                // 3. PAYMENT MODE (card)
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = tokens.card),
                        border = BorderStroke(0.5.dp, tokens.cardBorder),
                        elevation = CardDefaults.cardElevation(0.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "Payment mode",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Normal,
                                color = tokens.textMuted
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf(PaymentMode.Cash, PaymentMode.UPI, PaymentMode.Bank, PaymentMode.Credit).forEach { mode ->
                                    val isSelected = paymentMode == mode
                                    Surface(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(48.dp)
                                            .clickable { paymentMode = mode },
                                        color = if (isSelected) tokens.successContainer else tokens.secondaryBg,
                                        border = BorderStroke(
                                            0.5.dp,
                                            if (isSelected) tokens.success else tokens.cardBorder
                                        ),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                text = mode.name,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = if (isSelected) tokens.onSuccessContainer else tokens.textMuted
                                            )
                                        }
                                    }
                                }
                            }

                            if (paymentMode == PaymentMode.Credit) {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(
                                        text = "Due date (YYYY-MM-DD)",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Normal,
                                        color = tokens.textMuted
                                    )

                                    Surface(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(48.dp),
                                        shape = RoundedCornerShape(10.dp),
                                        color = tokens.secondaryBg,
                                        border = BorderStroke(0.5.dp, tokens.cardBorder)
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(horizontal = 12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.CalendarToday,
                                                contentDescription = "Due date",
                                                tint = tokens.textMuted,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            BasicTextField(
                                                value = dueDateInput,
                                                onValueChange = { dueDateInput = it },
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
                                    }
                                }
                            }
                        }
                    }
                }

                // 4. TAX SUMMARY (collapsible card)
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isTaxSummaryExpanded = !isTaxSummaryExpanded },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = tokens.card),
                        border = BorderStroke(0.5.dp, tokens.cardBorder),
                        elevation = CardDefaults.cardElevation(0.dp)
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
                                Text(
                                    text = "Taxable ${FinancialEngine.formatPaise(taxableAmountPaise)} · GST ${FinancialEngine.formatPaise(totalGstPaise)}",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = tokens.text
                                )

                                Icon(
                                    imageVector = if (isTaxSummaryExpanded) Icons.Outlined.KeyboardArrowUp else Icons.Outlined.KeyboardArrowDown,
                                    contentDescription = if (isTaxSummaryExpanded) "Collapse summary" else "Expand summary",
                                    tint = tokens.textMuted,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            if (isTaxSummaryExpanded) {
                                HorizontalDivider(thickness = 0.5.dp, color = tokens.cardBorder)

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Taxable amount", fontSize = 12.sp, color = tokens.textMuted)
                                    Text(FinancialEngine.formatPaise(taxableAmountPaise), fontSize = 12.sp, color = tokens.text, fontWeight = FontWeight.Normal)
                                }

                                if (isZeroRatedExport) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("IGST (Zero-rated Export)", fontSize = 12.sp, color = tokens.textMuted)
                                        Text(FinancialEngine.formatPaise(0L), fontSize = 12.sp, color = tokens.text, fontWeight = FontWeight.Normal)
                                    }
                                } else if (isInterstate) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("IGST (Inter-state)", fontSize = 12.sp, color = tokens.textMuted)
                                        Text(FinancialEngine.formatPaise(totalIgstPaise), fontSize = 12.sp, color = tokens.text, fontWeight = FontWeight.Normal)
                                    }
                                } else {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("CGST", fontSize = 12.sp, color = tokens.textMuted)
                                        Text(FinancialEngine.formatPaise(totalCgstPaise), fontSize = 12.sp, color = tokens.text, fontWeight = FontWeight.Normal)
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("SGST", fontSize = 12.sp, color = tokens.textMuted)
                                        Text(FinancialEngine.formatPaise(totalSgstPaise), fontSize = 12.sp, color = tokens.text, fontWeight = FontWeight.Normal)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // ==========================================
        // STICKY FOOTER
        // ==========================================
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
            color = tokens.card,
            border = BorderStroke(0.5.dp, tokens.cardBorder)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Total",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Normal,
                        color = tokens.textMuted
                    )
                    Text(
                        text = FinancialEngine.formatPaise(grandTotalPaise),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Medium,
                        color = tokens.text
                    )
                }

                Button(
                    onClick = {
                        if (addedItems.isEmpty()) {
                            Toast.makeText(context, "Please add at least one item", Toast.LENGTH_SHORT).show()
                            return@Button
                        }

                        if (!isNonGst && buyerGstinInput.isNotBlank()) {
                            val validation = FinancialEngine.validateGstin(buyerGstinInput, allowBlank = false)
                            if (!validation.isValid) {
                                Toast.makeText(context, validation.message, Toast.LENGTH_LONG).show()
                                return@Button
                            }
                        }

                        var normalizedDueDate = ""
                        if (paymentMode == PaymentMode.Credit && dueDateInput.isNotBlank()) {
                            normalizedDueDate = FinancialEngine.normalizeDate(dueDateInput)
                            if (normalizedDueDate.isEmpty()) {
                                Toast.makeText(context, "Enter a valid due date (YYYY-MM-DD)", Toast.LENGTH_LONG).show()
                                return@Button
                            }
                        }

                        val remarks = buildString {
                            if (!isNonGst && buyerGstinInput.isNotBlank()) {
                                append("GSTIN: ${buyerGstinInput.trim().uppercase()} | ")
                            }
                            if (paymentMode == PaymentMode.Credit && normalizedDueDate.isNotEmpty()) {
                                append("Due: $normalizedDueDate | ")
                            }
                            append(docTypeLabel)
                        }

                        onSaveInvoice(
                            selectedPartyId,
                            paymentMode,
                            docType,
                            addedItems,
                            remarks
                        )
                    },
                    modifier = Modifier.height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = tokens.primary,
                        contentColor = tokens.primaryText
                    ),
                    elevation = ButtonDefaults.buttonElevation(0.dp)
                ) {
                    Text(
                        text = "Issue & Save",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // ==========================================
        // BOTTOM SHEET: "+ ADD ITEM"
        // ==========================================
        if (showAddItemSheet) {
            ModalBottomSheet(
                onDismissRequest = { showAddItemSheet = false },
                containerColor = tokens.card,
                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                dragHandle = {
                    Surface(
                        modifier = Modifier
                            .padding(top = 10.dp)
                            .size(width = 36.dp, height = 4.dp),
                        color = tokens.textMuted.copy(alpha = 0.4f),
                        shape = RoundedCornerShape(2.dp)
                    ) {}
                }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Add item",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = tokens.text
                    )

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(10.dp),
                        color = tokens.secondaryBg,
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
                                contentDescription = "Search item",
                                tint = tokens.textMuted,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Box(modifier = Modifier.weight(1f)) {
                                if (itemSearchQuery.isEmpty()) {
                                    Text(
                                        text = "Search item",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Normal,
                                        color = tokens.textMuted
                                    )
                                }
                                BasicTextField(
                                    value = itemSearchQuery,
                                    onValueChange = { itemSearchQuery = it },
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
                            if (itemSearchQuery.isNotEmpty()) {
                                IconButton(
                                    onClick = { itemSearchQuery = "" },
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Close,
                                        contentDescription = "Clear",
                                        tint = tokens.textMuted,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }

                    val catalogList = remember(uiState.products, itemSearchQuery) {
                        val active = uiState.products.filter { !it.isArchived }
                        val q = itemSearchQuery.trim().lowercase()
                        if (q.isBlank()) active
                        else active.filter { p ->
                            p.name.lowercase().contains(q) || p.hsnCode.lowercase().contains(q)
                        }
                    }

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 320.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(catalogList, key = { it.id }) { prod ->
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        val qtyMilli = 1000L
                                        val taxable = FinancialEngine.lineTaxable(qtyMilli, prod.salePricePaise)
                                        val effRateBps = if (isNonGst) 0 else prod.gstRateBps
                                        val gst = if (isNonGst) {
                                            GstBreakdown(0L, 0L, 0L, taxable)
                                        } else {
                                            FinancialEngine.calculateGstBps(taxable, effRateBps, isInterstate, isZeroRatedExport)
                                        }
                                        if (prod.gstRateBps > 0) {
                                            originalRateByItemId[prod.id] = prod.gstRateBps
                                        }
                                        val newItem = InvoiceLineItem(
                                            itemId = prod.id,
                                            name = prod.name,
                                            hsnCode = prod.hsnCode,
                                            quantityMilli = qtyMilli,
                                            unit = prod.unit,
                                            ratePaise = prod.salePricePaise,
                                            gstRateBps = effRateBps,
                                            taxableAmountPaise = taxable,
                                            cgstPaise = gst.cgstPaise,
                                            sgstPaise = gst.sgstPaise,
                                            igstPaise = gst.igstPaise,
                                            totalPaise = gst.totalPaise
                                        )
                                        addedItems = addedItems + newItem
                                        showAddItemSheet = false
                                        Toast.makeText(context, "Added ${prod.name}", Toast.LENGTH_SHORT).show()
                                    },
                                shape = RoundedCornerShape(8.dp),
                                color = tokens.secondaryBg,
                                border = BorderStroke(0.5.dp, tokens.cardBorder)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = prod.name,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = tokens.text
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = "${FinancialEngine.formatPaise(prod.salePricePaise)} · Stock: ${FinancialEngine.formatQuantityMilli(prod.currentStockMilli)} ${prod.unit}",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Normal,
                                            color = tokens.textMuted
                                        )
                                    }

                                    Icon(
                                        imageVector = Icons.Outlined.Add,
                                        contentDescription = "Add",
                                        tint = tokens.success,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // ==========================================
        // CUSTOMER PICKER MODAL SHEET
        // ==========================================
        if (showCustomerPicker) {
            ModalBottomSheet(
                onDismissRequest = { showCustomerPicker = false },
                containerColor = tokens.card,
                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                dragHandle = {
                    Surface(
                        modifier = Modifier
                            .padding(top = 10.dp)
                            .size(width = 36.dp, height = 4.dp),
                        color = tokens.textMuted.copy(alpha = 0.4f),
                        shape = RoundedCornerShape(2.dp)
                    ) {}
                }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Select customer",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = tokens.text
                    )

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(10.dp),
                        color = tokens.secondaryBg,
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
                                contentDescription = "Search",
                                tint = tokens.textMuted,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Box(modifier = Modifier.weight(1f)) {
                                if (customerSearchQuery.isEmpty()) {
                                    Text(
                                        text = "Search customer by name or state...",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Normal,
                                        color = tokens.textMuted
                                    )
                                }
                                BasicTextField(
                                    value = customerSearchQuery,
                                    onValueChange = { customerSearchQuery = it },
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
                            if (customerSearchQuery.isNotEmpty()) {
                                IconButton(
                                    onClick = { customerSearchQuery = "" },
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Close,
                                        contentDescription = "Clear",
                                        tint = tokens.textMuted,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }

                    val filteredParties = remember(uiState.parties, customerSearchQuery) {
                        val q = customerSearchQuery.trim().lowercase()
                        uiState.parties.filter { !it.isSupplier }.filter { p ->
                            if (q.isBlank()) true
                            else p.name.lowercase().contains(q) || p.stateCode.contains(q) || p.gstin.lowercase().contains(q)
                        }
                    }

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 300.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        item(key = "walk_in_counter") {
                            val isSelected = selectedPartyId == null
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedPartyId = null
                                        buyerGstinInput = ""
                                        showCustomerPicker = false
                                    },
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) tokens.successContainer else tokens.secondaryBg,
                                border = BorderStroke(0.5.dp, if (isSelected) tokens.success else tokens.cardBorder)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Walk-in / Counter Customer",
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = if (isSelected) tokens.onSuccessContainer else tokens.text
                                        )
                                        Text(
                                            text = "Cash / B2C · Home State ${uiState.profile.homeStateCode}",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Normal,
                                            color = tokens.textMuted
                                        )
                                    }
                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Outlined.Check,
                                            contentDescription = null,
                                            tint = tokens.success,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }

                        items(filteredParties, key = { it.id }) { p ->
                            val isSelected = selectedPartyId == p.id
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedPartyId = p.id
                                        buyerGstinInput = p.gstin
                                        showCustomerPicker = false
                                    },
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) tokens.successContainer else tokens.secondaryBg,
                                border = BorderStroke(0.5.dp, if (isSelected) tokens.success else tokens.cardBorder)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = p.name,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = if (isSelected) tokens.onSuccessContainer else tokens.text
                                        )
                                        Text(
                                            text = "State ${p.stateCode.ifEmpty { uiState.profile.homeStateCode }} · GSTIN: ${p.gstin.ifEmpty { "Unregistered" }}",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Normal,
                                            color = tokens.textMuted
                                        )
                                    }

                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Outlined.Check,
                                            contentDescription = null,
                                            tint = tokens.success,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // ==========================================
        // QUICK "+ NEW" CUSTOMER DIALOG
        // ==========================================
        if (showNewCustomerDialog) {
            var newName by remember { mutableStateOf("") }
            var newState by remember { mutableStateOf(uiState.profile.homeStateCode.ifBlank { "27" }) }
            var newGstin by remember { mutableStateOf("") }

            AlertDialog(
                onDismissRequest = { showNewCustomerDialog = false },
                containerColor = tokens.card,
                shape = RoundedCornerShape(12.dp),
                title = {
                    Text("Add new customer", fontSize = 16.sp, fontWeight = FontWeight.Medium, color = tokens.text)
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Customer name", fontSize = 12.sp, color = tokens.textMuted)
                        OutlinedTextField(
                            value = newName,
                            onValueChange = { newName = it },
                            placeholder = { Text("e.g. Mahavir Supermarket", fontSize = 12.sp) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Text("State code (2 digits, 96 for Export)", fontSize = 12.sp, color = tokens.textMuted)
                        OutlinedTextField(
                            value = newState,
                            onValueChange = { s -> newState = s.filter { it.isDigit() }.take(2) },
                            placeholder = { Text("e.g. 27", fontSize = 12.sp) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Text("GSTIN (Optional)", fontSize = 12.sp, color = tokens.textMuted)
                        OutlinedTextField(
                            value = newGstin,
                            onValueChange = { s ->
                                val upper = s.uppercase().take(15)
                                newGstin = upper
                                if (upper.length >= 2 && upper.take(2).all { it.isDigit() }) {
                                    newState = upper.take(2)
                                }
                            },
                            placeholder = { Text("e.g. 27AAACS1234M1Z2", fontSize = 12.sp) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val cleanName = newName.trim()
                            if (cleanName.isBlank()) {
                                Toast.makeText(context, "Customer name is required", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            val cleanGstin = newGstin.trim().uppercase()
                            if (cleanGstin.isNotBlank()) {
                                val gstinCheck = FinancialEngine.validateGstin(cleanGstin, allowBlank = false)
                                if (!gstinCheck.isValid) {
                                    Toast.makeText(context, gstinCheck.message, Toast.LENGTH_LONG).show()
                                    return@Button
                                }
                            }
                            val cleanState = newState.trim().padStart(2, '0')
                            val generatedId = "PTY-${UUID.randomUUID().toString().take(8).uppercase()}"
                            val pan = if (cleanGstin.length >= 12) cleanGstin.substring(2, 12) else ""
                            val newParty = Party(
                                id = generatedId,
                                name = cleanName,
                                phone = "",
                                address = "",
                                gstin = cleanGstin,
                                pan = pan,
                                stateCode = cleanState,
                                openingBalancePaise = 0L,
                                openingBalanceType = BalanceType.Settled,
                                isSupplier = false
                            )
                            onAddParty(newParty)
                            selectedPartyId = generatedId
                            buyerGstinInput = cleanGstin
                            showNewCustomerDialog = false
                            Toast.makeText(context, "Customer added and selected", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.heightIn(min = 48.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = tokens.primary,
                            contentColor = tokens.primaryText
                        )
                    ) {
                        Text("Add customer", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    }
                },
                dismissButton = {
                    OutlinedButton(
                        onClick = { showNewCustomerDialog = false },
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Text("Cancel", color = tokens.textMuted, fontSize = 12.sp)
                    }
                }
            )
        }
    }
}

// ==========================================
// INVOICE CARD (List Item)
// ==========================================
@Composable
fun InvoiceItemCard(
    invoice: Transaction,
    uiState: PabaruUiState,
    onShare: () -> Unit,
    onView: () -> Unit,
    onDuplicate: () -> Unit,
    onVoidClick: () -> Unit
) {
    val tokens = PabaruTheme.tokens
    var showMenu by remember { mutableStateOf(false) }
    val party = uiState.parties.find { it.id == invoice.partyId }
    val customerName = party?.name ?: (if (invoice.remarks.isNotBlank()) invoice.remarks else "Counter Customer")
    val invoiceDate = invoice.date.ifBlank { "—" }
    val invoiceNum = invoice.invoiceNumber.ifBlank { invoice.id }

    val isCredit = invoice.paymentMode == PaymentMode.Credit
    val isVoided = invoice.isVoided

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = tokens.card),
        border = BorderStroke(0.5.dp, tokens.cardBorder),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    modifier = Modifier.size(38.dp),
                    shape = RoundedCornerShape(8.dp),
                    color = tokens.secondaryBg,
                    border = BorderStroke(0.5.dp, tokens.cardBorder)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = when (invoice.type) {
                                TransactionType.Quotation -> Icons.Outlined.RequestQuote
                                TransactionType.CreditNote -> Icons.Outlined.AssignmentReturn
                                else -> Icons.Outlined.Receipt
                            },
                            contentDescription = null,
                            tint = tokens.textMuted,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = customerName,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = tokens.text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "$invoiceNum · $invoiceDate",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Normal,
                        color = tokens.textMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = FinancialEngine.formatPaise(invoice.amountPaise),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = tokens.text
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    when {
                        isVoided -> {
                            StatusPill(
                                label = "Voided",
                                textColor = tokens.danger,
                                bgColor = tokens.dangerContainer,
                                borderColor = tokens.danger
                            )
                        }
                        invoice.type == TransactionType.Quotation -> {
                            StatusPill(
                                label = "Quotation",
                                textColor = tokens.warning,
                                bgColor = tokens.warningContainer,
                                borderColor = tokens.warning
                            )
                        }
                        invoice.type == TransactionType.CreditNote -> {
                            StatusPill(
                                label = "Credit Note",
                                textColor = tokens.warning,
                                bgColor = tokens.warningContainer,
                                borderColor = tokens.warning
                            )
                        }
                        invoice.type == TransactionType.DebitNote -> {
                            StatusPill(
                                label = "Debit Note",
                                textColor = tokens.warning,
                                bgColor = tokens.warningContainer,
                                borderColor = tokens.warning
                            )
                        }
                        isCredit -> {
                            StatusPill(
                                label = "Credit",
                                textColor = tokens.warning,
                                bgColor = tokens.warningContainer,
                                borderColor = tokens.warning
                            )
                        }
                        else -> {
                            StatusPill(
                                label = "Paid",
                                textColor = tokens.onSuccessContainer,
                                bgColor = tokens.successContainer,
                                borderColor = tokens.success
                            )
                        }
                    }
                }
            }

            HorizontalDivider(thickness = 0.5.dp, color = tokens.cardBorder)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .clickable { onShare() },
                    color = Color.Transparent,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Share,
                            contentDescription = "Share",
                            tint = tokens.textMuted,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Share",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = tokens.textMuted
                        )
                    }
                }

                Box {
                    IconButton(
                        onClick = { showMenu = true },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.MoreHoriz,
                            contentDescription = "More actions",
                            tint = tokens.textMuted,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                        modifier = Modifier
                            .background(tokens.card)
                            .border(0.5.dp, tokens.cardBorder, RoundedCornerShape(8.dp))
                    ) {
                        DropdownMenuItem(
                            text = { Text("View", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = tokens.text) },
                            leadingIcon = { Icon(Icons.Outlined.Visibility, contentDescription = null, tint = tokens.textMuted, modifier = Modifier.size(18.dp)) },
                            onClick = { showMenu = false; onView() }
                        )
                        DropdownMenuItem(
                            text = { Text("Duplicate", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = tokens.text) },
                            leadingIcon = { Icon(Icons.Outlined.ContentCopy, contentDescription = null, tint = tokens.textMuted, modifier = Modifier.size(18.dp)) },
                            onClick = { showMenu = false; onDuplicate() }
                        )
                        if (!isVoided) {
                            HorizontalDivider(thickness = 0.5.dp, color = tokens.cardBorder)
                            DropdownMenuItem(
                                text = { Text("Void", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = tokens.danger) },
                                leadingIcon = { Icon(Icons.Outlined.Block, contentDescription = null, tint = tokens.danger, modifier = Modifier.size(18.dp)) },
                                onClick = { showMenu = false; onVoidClick() }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StatusPill(
    label: String,
    textColor: Color,
    bgColor: Color,
    borderColor: Color
) {
    Surface(
        color = bgColor,
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(0.5.dp, borderColor)
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = textColor,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
        )
    }
}

@Composable
fun InvoiceEmptyState(
    onCreateClick: () -> Unit
) {
    val tokens = PabaruTheme.tokens
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = tokens.card),
        border = BorderStroke(0.5.dp, tokens.cardBorder),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 32.dp, horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                modifier = Modifier.size(48.dp),
                shape = RoundedCornerShape(12.dp),
                color = tokens.secondaryBg,
                border = BorderStroke(0.5.dp, tokens.cardBorder)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Outlined.Receipt,
                        contentDescription = null,
                        tint = tokens.textMuted,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Text(
                text = "Create your first invoice",
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = tokens.text,
                textAlign = TextAlign.Center
            )

            Text(
                text = "Bills you create appear here, even offline.",
                fontSize = 12.sp,
                fontWeight = FontWeight.Normal,
                color = tokens.textMuted,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(4.dp))

            Button(
                onClick = onCreateClick,
                modifier = Modifier
                    .fillMaxWidth(0.8f)
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = tokens.primary,
                    contentColor = tokens.primaryText
                ),
                elevation = ButtonDefaults.buttonElevation(0.dp)
            ) {
                Text(
                    text = "New tax bill",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

private fun formatKStat(amountPaise: Long): String {
    val rupees = amountPaise / 100.0
    return if (rupees >= 1000.0) {
        String.format(Locale.ENGLISH, "₹%.2fK", rupees / 1000.0)
    } else {
        String.format(Locale.ENGLISH, "₹%.2f", rupees)
    }
}
