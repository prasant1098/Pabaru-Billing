package com.example.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.engine.BankReconEngine
import com.example.engine.FinancialEngine
import com.example.model.*
import com.example.ui.theme.PabaruTheme
import com.example.ui.theme.danger
import com.example.ui.theme.info
import com.example.ui.theme.onSuccessContainer
import com.example.ui.theme.success
import com.example.ui.theme.successContainer
import com.example.ui.theme.warning
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Bottom Sheet for Uploading or Importing Bank Statements (CSV, OFX, QFX, Raw Text)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UploadBankStatementSheet(
    targetBank: BankAccount,
    onDismiss: () -> Unit,
    onImportText: (String, String, String) -> Unit,
    onImportSample: (String, String) -> Unit
) {
    val context = LocalContext.current
    val tokens = PabaruTheme.tokens
    var activeTab by remember { mutableStateOf(0) } // 0: File Pick, 1: Sample Statement, 2: Paste Raw Text
    var rawText by remember { mutableStateOf("") }
    var selectedFileName by remember { mutableStateOf<String?>(null) }
    var fileContent by remember { mutableStateOf<String?>(null) }

    val coroutineScope = rememberCoroutineScope()
    var isReadingFile by remember { mutableStateOf(false) }

    // File Picker for CSV, OFX, TXT with null-safe closing & 10 MB size guard on Dispatchers.IO
    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null && !isReadingFile) {
            isReadingFile = true
            coroutineScope.launch {
                try {
                    val content = withContext(Dispatchers.IO) {
                        val maxChars = 10 * 1024 * 1024
                        context.contentResolver.openInputStream(uri)?.use { stream ->
                            BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { reader ->
                                val text = reader.readText()
                                if (text.length > maxChars) {
                                    throw IllegalArgumentException("Statement file exceeds 10 MB limit")
                                }
                                text
                            }
                        } ?: throw IllegalStateException("Unable to open selected file")
                    }

                    fileContent = content
                    selectedFileName = uri.lastPathSegment?.substringAfterLast('/') ?: "bank_statement.csv"
                    Toast.makeText(context, "Loaded statement file: $selectedFileName", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(context, "Failed to read statement: ${e.message}", Toast.LENGTH_SHORT).show()
                } finally {
                    isReadingFile = false
                }
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = tokens.card,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Import Bank Statement", fontSize = 18.sp, fontWeight = FontWeight.Medium, color = tokens.text)
                    Text("Target: ${targetBank.bankName} (••••${targetBank.accountNumber.takeLast(4)})", fontSize = 12.sp, color = tokens.textMuted)
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Outlined.Close, contentDescription = "Close", tint = tokens.textMuted)
                }
            }

            // Tab Switcher (File Upload, Sample, Paste Text)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val tabs = listOf("Upload File", "Try Sample", "Paste Text")
                tabs.forEachIndexed { index, title ->
                    val isSelected = activeTab == index
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clickable { activeTab = index },
                        shape = RoundedCornerShape(10.dp),
                        color = if (isSelected) tokens.primary.copy(alpha = 0.15f) else tokens.secondaryBg,
                        border = BorderStroke(0.5.dp, if (isSelected) tokens.primary else tokens.cardBorder)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = title,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
                                color = if (isSelected) tokens.primary else tokens.textMuted
                            )
                        }
                    }
                }
            }

            when (activeTab) {
                0 -> {
                    // TAB 0: FILE UPLOAD
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "Supports CSV, OFX, and QFX statement formats exported from Indian bank portals (HDFC, SBI, ICICI, Axis, Kotak, etc.).",
                            fontSize = 12.sp,
                            color = tokens.textMuted
                        )

                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp)
                                .clickable { filePicker.launch("*/*") },
                            shape = RoundedCornerShape(12.dp),
                            color = tokens.secondaryBg,
                            border = BorderStroke(1.dp, if (selectedFileName != null) tokens.success else tokens.cardBorder)
                        ) {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = if (selectedFileName != null) Icons.Outlined.CheckCircle else Icons.Outlined.UploadFile,
                                    contentDescription = null,
                                    tint = if (selectedFileName != null) tokens.success else tokens.primary,
                                    modifier = Modifier.size(32.dp)
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = selectedFileName ?: "Tap to choose CSV or OFX file",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = if (selectedFileName != null) tokens.success else tokens.text
                                )
                                Text(
                                    text = if (selectedFileName != null) "Ready for automated matching" else "Direct file upload from device",
                                    fontSize = 11.sp,
                                    color = tokens.textMuted
                                )
                            }
                        }

                        val canImport = fileContent != null
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .clickable(enabled = canImport) {
                                    fileContent?.let {
                                        onImportText(it, targetBank.id, targetBank.bankName)
                                        Toast.makeText(context, "Statement parsed and matched successfully", Toast.LENGTH_SHORT).show()
                                        onDismiss()
                                    }
                                },
                            shape = RoundedCornerShape(12.dp),
                            color = if (canImport) tokens.primary else tokens.primary.copy(alpha = 0.4f)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("Parse & Run Auto-Reconciliation", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = tokens.primaryText)
                            }
                        }
                    }
                }
                1 -> {
                    // TAB 1: SAMPLE STATEMENTS
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = "Test instant reconciliation with realistic pre-configured statements containing customer deposits, vendor NEFTs, bank charges, and electricity bills.",
                            fontSize = 12.sp,
                            color = tokens.textMuted
                        )

                        // Sample 1: HDFC CSV
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onImportSample("CSV", targetBank.id)
                                    Toast.makeText(context, "Loaded HDFC CSV Statement & Auto-Matched", Toast.LENGTH_SHORT).show()
                                    onDismiss()
                                },
                            shape = RoundedCornerShape(12.dp),
                            color = tokens.secondaryBg,
                            border = BorderStroke(0.5.dp, tokens.cardBorder)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("HDFC Bank Statement (CSV Format)", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = tokens.text)
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text("7 lines: UPI receipts, NEFT vendor payment, bank charges, POS swipe", fontSize = 11.sp, color = tokens.textMuted)
                                }
                                Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = tokens.primary)
                            }
                        }

                        // Sample 2: SBI OFX
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onImportSample("OFX", targetBank.id)
                                    Toast.makeText(context, "Loaded SBI OFX Statement & Auto-Matched", Toast.LENGTH_SHORT).show()
                                    onDismiss()
                                },
                            shape = RoundedCornerShape(12.dp),
                            color = tokens.secondaryBg,
                            border = BorderStroke(0.5.dp, tokens.cardBorder)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("State Bank of India (OFX/QFX Format)", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = tokens.text)
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text("Standard Open Financial Exchange SGML format with STMTTRN tags", fontSize = 11.sp, color = tokens.textMuted)
                                }
                                Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = tokens.primary)
                            }
                        }
                    }
                }
                2 -> {
                    // TAB 2: PASTE TEXT
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "Paste comma-separated rows or OFX XML text directly from your online banking statement:",
                            fontSize = 12.sp,
                            color = tokens.textMuted
                        )

                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(130.dp),
                            shape = RoundedCornerShape(12.dp),
                            color = tokens.secondaryBg,
                            border = BorderStroke(0.5.dp, tokens.cardBorder)
                        ) {
                            BasicTextField(
                                value = rawText,
                                onValueChange = { rawText = it },
                                cursorBrush = SolidColor(tokens.primary),
                                textStyle = TextStyle(fontSize = 12.sp, color = tokens.text),
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(10.dp),
                                decorationBox = { innerTextField ->
                                    if (rawText.isEmpty()) {
                                        Text("Date,Narration,RefNo,Withdrawal,Deposit\n01/10/2026,UPI-CUSTOMER PAYMENT,,10000.00", fontSize = 12.sp, color = tokens.textMuted.copy(alpha = 0.6f))
                                    }
                                    innerTextField()
                                }
                            )
                        }

                        val canParse = rawText.trim().isNotEmpty()
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .clickable(enabled = canParse) {
                                    if (canParse) {
                                        onImportText(rawText, targetBank.id, targetBank.bankName)
                                        Toast.makeText(context, "Statement parsed & matched", Toast.LENGTH_SHORT).show()
                                        onDismiss()
                                    }
                                },
                            shape = RoundedCornerShape(12.dp),
                            color = if (canParse) tokens.primary else tokens.primary.copy(alpha = 0.4f)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("Process Pasted Statement", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = tokens.primaryText)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
        }
    }
}

/**
 * Bottom Sheet for Manually Selecting and Linking a Ledger Transaction to a Statement Entry
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManualMatchSheet(
    statementItem: BankReconItem,
    transactions: List<Transaction>,
    parties: List<Party>,
    onDismiss: () -> Unit,
    onConfirmMatch: (String, String) -> Unit
) {
    val tokens = PabaruTheme.tokens
    var searchQuery by remember { mutableStateOf("") }
    var selectedTxnId by remember { mutableStateOf<String?>(null) }
    val partyMap = remember(parties) { parties.associateBy { it.id } }

    // Filter candidate transactions: non-voided, preferring matching direction & exact amount
    val filteredTxns = remember(transactions, searchQuery, statementItem) {
        val q = searchQuery.trim().lowercase(Locale.ENGLISH)
        transactions.filter { txn ->
            if (txn.isVoided || txn.type == TransactionType.Quotation) return@filter false
            if (q.isEmpty()) return@filter true

            val partyName = txn.partyId?.let { partyMap[it]?.name } ?: ""
            val inv = txn.invoiceNumber.lowercase(Locale.ENGLISH)
            val rem = txn.remarks.lowercase(Locale.ENGLISH)
            val amt = String.format(Locale.ENGLISH, "%.2f", txn.amountPaise / 100.0)

            partyName.lowercase(Locale.ENGLISH).contains(q) ||
                inv.contains(q) ||
                rem.contains(q) ||
                amt.contains(q) ||
                txn.id.lowercase(Locale.ENGLISH).contains(q)
        }.sortedByDescending { txn ->
            val isExactAmount = txn.amountPaise == statementItem.amountPaise
            val isTxnInflow = txn.type in listOf(TransactionType.Sale, TransactionType.NonGstBill, TransactionType.PaymentIn, TransactionType.DebitNote)
            val sameDir = (isTxnInflow == statementItem.isCredit)
            (if (isExactAmount) 100 else 0) + (if (sameDir) 20 else 0)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = tokens.card,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
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
                    Text("Match Statement Entry", fontSize = 18.sp, fontWeight = FontWeight.Medium, color = tokens.text)
                    Text(
                        "${statementItem.date} · ${(if (statementItem.isCredit) "+" else "−")}${FinancialEngine.formatPaise(statementItem.amountPaise)}",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (statementItem.isCredit) tokens.success else tokens.danger
                    )
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Outlined.Close, contentDescription = "Close", tint = tokens.textMuted)
                }
            }

            // Statement item summary
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                color = tokens.secondaryBg,
                border = BorderStroke(0.5.dp, tokens.cardBorder)
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Text(statementItem.description, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = tokens.text)
                    if (statementItem.referenceNo.isNotBlank()) {
                        Text("Ref: ${statementItem.referenceNo}", fontSize = 11.sp, color = tokens.textMuted)
                    }
                }
            }

            // Search bar
            Surface(
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(10.dp),
                color = tokens.secondaryBg,
                border = BorderStroke(0.5.dp, tokens.cardBorder)
            ) {
                Row(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Outlined.Search, contentDescription = null, tint = tokens.textMuted, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    BasicTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        singleLine = true,
                        cursorBrush = SolidColor(tokens.primary),
                        textStyle = TextStyle(fontSize = 13.sp, color = tokens.text),
                        modifier = Modifier.fillMaxWidth(),
                        decorationBox = { inner ->
                            if (searchQuery.isEmpty()) {
                                Text("Search ledger by party, invoice #, amount...", fontSize = 12.sp, color = tokens.textMuted)
                            }
                            inner()
                        }
                    )
                }
            }

            Text("Select matching ledger transaction (${filteredTxns.size} available):", fontSize = 12.sp, color = tokens.textMuted)

            // Transactions list
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 280.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (filteredTxns.isEmpty()) {
                    item {
                        Box(modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp), contentAlignment = Alignment.Center) {
                            Text("No matching ledger transactions found", fontSize = 12.sp, color = tokens.textMuted)
                        }
                    }
                } else {
                    items(filteredTxns, key = { it.id }) { txn ->
                        val isSelected = selectedTxnId == txn.id
                        val party = txn.partyId?.let { partyMap[it] }
                        val isExactAmount = txn.amountPaise == statementItem.amountPaise

                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedTxnId = txn.id },
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) tokens.success.copy(alpha = 0.15f) else tokens.secondaryBg,
                            border = BorderStroke(
                                if (isSelected) 1.dp else 0.5.dp,
                                if (isSelected) tokens.success else if (isExactAmount) tokens.success.copy(alpha = 0.5f) else tokens.cardBorder
                            )
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = txn.type.name,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = tokens.info
                                        )
                                        if (txn.invoiceNumber.isNotBlank()) {
                                            Text(
                                                text = " · ${txn.invoiceNumber}",
                                                fontSize = 11.sp,
                                                color = tokens.textMuted
                                            )
                                        }
                                        if (isExactAmount) {
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = tokens.successContainer
                                            ) {
                                                Text(
                                                    "Exact Amount",
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    color = tokens.onSuccessContainer,
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                )
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = party?.name ?: txn.remarks.ifBlank { "Direct Ledger Entry" },
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = tokens.text,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${txn.date} · ${txn.paymentMode.name}",
                                        fontSize = 11.sp,
                                        color = tokens.textMuted
                                    )
                                }

                                Text(
                                    text = FinancialEngine.formatPaise(txn.amountPaise),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = tokens.text
                                )
                            }
                        }
                    }
                }
            }

            // Link & Reconcile Button
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clickable(enabled = selectedTxnId != null) {
                        selectedTxnId?.let { txnId ->
                            onConfirmMatch(statementItem.id, txnId)
                            onDismiss()
                        }
                    },
                shape = RoundedCornerShape(12.dp),
                color = if (selectedTxnId != null) tokens.primary else tokens.primary.copy(alpha = 0.4f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("Confirm Manual Link & Reconcile", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = tokens.primaryText)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

/**
 * Bottom Sheet for Resolving Bank Statement Discrepancies
 * (e.g. Unrecorded bank charges, interest income, customer receipts, vendor payments)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResolveDiscrepancySheet(
    statementItem: BankReconItem,
    parties: List<Party>,
    onDismiss: () -> Unit,
    onResolve: (String, BankReconEngine.DiscrepancyResolutionType, String?, String) -> Unit
) {
    val context = LocalContext.current
    val tokens = PabaruTheme.tokens
    val isCredit = statementItem.isCredit
    var selectedType by remember {
        mutableStateOf(
            if (isCredit) BankReconEngine.DiscrepancyResolutionType.CUSTOMER_PAYMENT
            else BankReconEngine.DiscrepancyResolutionType.BANK_CHARGES
        )
    }
    var selectedPartyId by remember { mutableStateOf<String?>(null) }
    var remarksText by remember { mutableStateOf("") }
    var partyDropdownExpanded by remember { mutableStateOf(false) }

    val eligibleParties = remember(parties, isCredit) {
        if (isCredit) parties.filter { !it.isSupplier } else parties.filter { it.isSupplier }
    }

    val requiresParty = selectedType == BankReconEngine.DiscrepancyResolutionType.CUSTOMER_PAYMENT ||
        selectedType == BankReconEngine.DiscrepancyResolutionType.SUPPLIER_PAYMENT

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = tokens.card,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Resolve Discrepancy", fontSize = 18.sp, fontWeight = FontWeight.Medium, color = tokens.text)
                    Text("Create balancing ledger transaction with 1-tap", fontSize = 12.sp, color = tokens.textMuted)
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Outlined.Close, contentDescription = "Close", tint = tokens.textMuted)
                }
            }

            // Statement Row Info
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                color = tokens.secondaryBg,
                border = BorderStroke(0.5.dp, tokens.cardBorder)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                        Text(statementItem.description, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = tokens.text)
                        Text("${statementItem.date} · Ref: ${statementItem.referenceNo.ifBlank { "N/A" }}", fontSize = 11.sp, color = tokens.textMuted)
                    }
                    Text(
                        text = (if (isCredit) "+" else "−") + FinancialEngine.formatPaise(statementItem.amountPaise),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (isCredit) tokens.success else tokens.danger
                    )
                }
            }

            Text("Select Resolution Type:", fontSize = 12.sp, color = tokens.textMuted)

            if (!isCredit) {
                // Debit Resolution Options (Withdrawals)
                val debitOptions = listOf(
                    BankReconEngine.DiscrepancyResolutionType.BANK_CHARGES to "Bank Service Charges & GST (18%)",
                    BankReconEngine.DiscrepancyResolutionType.SUPPLIER_PAYMENT to "Payment Out to Supplier / Vendor",
                    BankReconEngine.DiscrepancyResolutionType.DIRECT_EXPENSE to "Direct Operating Expense (Electricity/Rent)"
                )

                debitOptions.forEach { (type, label) ->
                    val isSelected = selectedType == type
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedType = type },
                        shape = RoundedCornerShape(10.dp),
                        color = if (isSelected) tokens.warning.copy(alpha = 0.15f) else tokens.secondaryBg,
                        border = BorderStroke(0.5.dp, if (isSelected) tokens.warning else tokens.cardBorder)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = { selectedType = type },
                                colors = RadioButtonDefaults.colors(selectedColor = tokens.warning)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(label, fontSize = 13.sp, color = tokens.text)
                        }
                    }
                }
            } else {
                // Credit Resolution Options (Deposits)
                val creditOptions = listOf(
                    BankReconEngine.DiscrepancyResolutionType.CUSTOMER_PAYMENT to "Payment In from Customer (Khata Clearance)",
                    BankReconEngine.DiscrepancyResolutionType.INTEREST_INCOME to "Bank Interest / Dividend Credit",
                    BankReconEngine.DiscrepancyResolutionType.DIRECT_EXPENSE to "Other Direct Income / Capital Infusion"
                )

                creditOptions.forEach { (type, label) ->
                    val isSelected = selectedType == type
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedType = type },
                        shape = RoundedCornerShape(10.dp),
                        color = if (isSelected) tokens.success.copy(alpha = 0.15f) else tokens.secondaryBg,
                        border = BorderStroke(0.5.dp, if (isSelected) tokens.success else tokens.cardBorder)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = { selectedType = type },
                                colors = RadioButtonDefaults.colors(selectedColor = tokens.success)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(label, fontSize = 13.sp, color = tokens.text)
                        }
                    }
                }
            }

            // Party Selector if Supplier or Customer payment
            if (requiresParty) {
                val party = parties.find { it.id == selectedPartyId }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(if (isCredit) "Select Customer" else "Select Supplier", fontSize = 12.sp, color = tokens.textMuted)
                    Box {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .clickable { partyDropdownExpanded = true },
                            shape = RoundedCornerShape(10.dp),
                            color = tokens.secondaryBg,
                            border = BorderStroke(0.5.dp, tokens.cardBorder)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(party?.name ?: "Tap to choose party...", fontSize = 13.sp, color = if (party != null) tokens.text else tokens.textMuted)
                                Icon(Icons.Outlined.ArrowDropDown, contentDescription = null, tint = tokens.textMuted)
                            }
                        }

                        DropdownMenu(
                            expanded = partyDropdownExpanded,
                            onDismissRequest = { partyDropdownExpanded = false },
                            modifier = Modifier.background(tokens.card).border(0.5.dp, tokens.cardBorder, RoundedCornerShape(10.dp))
                        ) {
                            if (eligibleParties.isEmpty()) {
                                DropdownMenuItem(
                                    text = { Text("No matching parties available", fontSize = 13.sp, color = tokens.textMuted) },
                                    onClick = { partyDropdownExpanded = false }
                                )
                            } else {
                                eligibleParties.forEach { p ->
                                    DropdownMenuItem(
                                        text = { Text(p.name, fontSize = 13.sp, color = tokens.text) },
                                        onClick = {
                                            selectedPartyId = p.id
                                            partyDropdownExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Custom Remarks
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Ledger Remarks (Optional)", fontSize = 12.sp, color = tokens.textMuted)
                Surface(
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(10.dp),
                    color = tokens.secondaryBg,
                    border = BorderStroke(0.5.dp, tokens.cardBorder)
                ) {
                    Box(modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp), contentAlignment = Alignment.CenterStart) {
                        BasicTextField(
                            value = remarksText,
                            onValueChange = { remarksText = it },
                            singleLine = true,
                            cursorBrush = SolidColor(tokens.primary),
                            textStyle = TextStyle(fontSize = 13.sp, color = tokens.text),
                            modifier = Modifier.fillMaxWidth(),
                            decorationBox = { inner ->
                                if (remarksText.isEmpty()) {
                                    Text("e.g. Reconciled via Bank Feed", fontSize = 12.sp, color = tokens.textMuted)
                                }
                                inner()
                            }
                        )
                    }
                }
            }

            // Create Balancing Transaction & Reconcile Button
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clickable {
                        if (requiresParty && selectedPartyId == null) {
                            Toast.makeText(context, "Please select a party first", Toast.LENGTH_SHORT).show()
                        } else {
                            onResolve(statementItem.id, selectedType, selectedPartyId, remarksText.trim())
                            onDismiss()
                        }
                    },
                shape = RoundedCornerShape(12.dp),
                color = tokens.primary
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        "Create Transaction & Reconcile",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = tokens.primaryText
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}
