package com.example.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.engine.ExportEngine
import com.example.engine.FinancialEngine
import com.example.engine.Gstr2bReconciliationEngine
import com.example.model.BusinessProfile
import com.example.model.Party
import com.example.model.Transaction
import com.example.ui.theme.PabaruTheme
import com.example.ui.theme.PabaruTokens
import com.example.ui.theme.danger
import com.example.ui.theme.dangerContainer
import com.example.ui.theme.info
import com.example.ui.theme.infoContainer
import com.example.ui.theme.onDangerContainer
import com.example.ui.theme.onInfoContainer
import com.example.ui.theme.onSuccessContainer
import com.example.ui.theme.onWarningContainer
import com.example.ui.theme.success
import com.example.ui.theme.successContainer
import com.example.ui.theme.warning
import com.example.ui.theme.warningContainer
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Gstr2bReconciliationScreen(
    profile: BusinessProfile,
    transactions: List<Transaction>,
    parties: List<Party>,
    onBack: () -> Unit
) {
    val tokens = PabaruTheme.tokens
    val context = LocalContext.current

    BackHandler { onBack() }

    // GSTR-2B raw invoices state
    var gstr2bInvoices by remember {
        mutableStateOf<List<Gstr2bReconciliationEngine.Gstr2bInvoice>>(
            Gstr2bReconciliationEngine.getSampleGstr2bInvoices(transactions, parties)
        )
    }

    // Selected period label aligned with dynamic current FY
    var selectedPeriodLabel by remember {
        mutableStateOf("${FinancialEngine.getCurrentFinancialYear()} (All Inward)")
    }

    // Reconcile GSTR-2B with local books purchases
    val (reconciliationItems, summary) = remember(gstr2bInvoices, transactions, parties) {
        Gstr2bReconciliationEngine.reconcile(gstr2bInvoices, transactions, parties)
    }

    // Filter states
    var selectedFilterStatus by remember { mutableStateOf<Gstr2bReconciliationEngine.MatchStatus?>(null) }
    var searchQuery by remember { mutableStateOf("") }

    // Selected item for side-by-side discrepancy review dialog
    var selectedDetailItem by remember { mutableStateOf<Gstr2bReconciliationEngine.ReconciliationItem?>(null) }

    val coroutineScope = rememberCoroutineScope()
    var isImportingFile by remember { mutableStateOf(false) }

    // File picker launcher for importing JSON or CSV with stream validation on Dispatchers.IO
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null && !isImportingFile) {
            isImportingFile = true
            coroutineScope.launch {
                try {
                    val parsed = withContext(Dispatchers.IO) {
                        val maxChars = 10 * 1024 * 1024
                        val content = context.contentResolver.openInputStream(uri)?.use { stream ->
                            BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { reader ->
                                val text = reader.readText()
                                if (text.length > maxChars) {
                                    throw IllegalArgumentException("File exceeds 10 MB limit")
                                }
                                text
                            }
                        } ?: throw IllegalStateException("Unable to open selected file")

                        if (content.trim().startsWith("{")) {
                            Gstr2bReconciliationEngine.parseGstr2bJson(content)
                        } else {
                            Gstr2bReconciliationEngine.parseGstr2bCsvOrTsv(content)
                        }
                    }

                    if (parsed.isNotEmpty()) {
                        gstr2bInvoices = parsed
                        Toast.makeText(context, "Imported ${parsed.size} invoices from GSTR-2B file", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "No valid GSTR-2B invoices found in file", Toast.LENGTH_LONG).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "Error reading file: ${e.message}", Toast.LENGTH_LONG).show()
                } finally {
                    isImportingFile = false
                }
            }
        }
    }

    val filteredItems = remember(reconciliationItems, selectedFilterStatus, searchQuery) {
        reconciliationItems.filter { item ->
            val matchesStatus = selectedFilterStatus == null || item.matchStatus == selectedFilterStatus
            val query = searchQuery.trim().lowercase(Locale.ENGLISH)
            val matchesQuery = query.isEmpty() ||
                item.supplierGstin.lowercase(Locale.ENGLISH).contains(query) ||
                item.supplierName.lowercase(Locale.ENGLISH).contains(query) ||
                item.invoiceNumber.lowercase(Locale.ENGLISH).contains(query)
            matchesStatus && matchesQuery
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(tokens.bg)
    ) {
        // App Bar
        Surface(
            color = tokens.headerBg,
            shadowElevation = 4.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier.size(48.dp).testTag("gstr2b_back_button")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = tokens.headerText)
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Column {
                            Text("GSTR-2B Reconciliation", fontWeight = FontWeight.Black, fontSize = 15.sp, color = tokens.headerText)
                            Text("Rule 36(4) ITC Verification • ₹1 Tolerance", fontSize = 10.5.sp, color = tokens.accent)
                        }
                    }

                    // Action buttons (Import, Sample, PDF, CSV)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { filePickerLauncher.launch("*/*") },
                            modifier = Modifier.size(48.dp).testTag("gstr2b_import_button")
                        ) {
                            Icon(Icons.Default.UploadFile, contentDescription = "Import 2B File", tint = tokens.headerText)
                        }
                        IconButton(
                            onClick = {
                                gstr2bInvoices = Gstr2bReconciliationEngine.getSampleGstr2bInvoices(transactions, parties)
                                Toast.makeText(context, "Loaded Demo GSTR-2B Statement", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.size(48.dp).testTag("gstr2b_sample_button")
                        ) {
                            Icon(Icons.Default.CloudDownload, contentDescription = "Load Sample 2B", tint = tokens.accent)
                        }
                        IconButton(
                            onClick = {
                                ExportEngine.generateAndShareGstr2bReconciliationPdf(
                                    context, profile, selectedPeriodLabel, filteredItems, summary
                                )
                            },
                            modifier = Modifier.size(48.dp).testTag("gstr2b_export_pdf_button")
                        ) {
                            Icon(Icons.Default.PictureAsPdf, contentDescription = "Export PDF", tint = tokens.headerText)
                        }
                        IconButton(
                            onClick = {
                                ExportEngine.generateAndShareGstr2bReconciliationCsv(
                                    context, profile, selectedPeriodLabel, filteredItems, summary
                                )
                            },
                            modifier = Modifier.size(48.dp).testTag("gstr2b_export_csv_button")
                        ) {
                            Icon(Icons.Default.TableChart, contentDescription = "Export CSV", tint = tokens.headerText)
                        }
                    }
                }
            }
        }

        // Main Content List
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp)
                .testTag("gstr2b_lazy_column"),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item { Spacer(modifier = Modifier.height(2.dp)) }

            // KPI Overview Banner
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = tokens.card),
                    border = BorderStroke(1.dp, tokens.cardBorder),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Input Tax Credit (ITC) Comparison", fontWeight = FontWeight.Bold, fontSize = 13.5.sp, color = tokens.text)
                                Text("GSTR-2B Portal vs Purchase Books", fontSize = 10.sp, color = tokens.textMuted)
                            }
                            Surface(color = tokens.secondaryBg, shape = RoundedCornerShape(6.dp)) {
                                Text(
                                    "Rule 36(4) Audit",
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = tokens.accent
                                )
                            }
                        }

                        HorizontalDivider(color = tokens.cardBorder)

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column {
                                Text("2B Available ITC", fontSize = 10.5.sp, color = tokens.textMuted)
                                Text(FinancialEngine.formatPaise(summary.totalGstr2bItcAvailablePaise), fontSize = 13.5.sp, fontWeight = FontWeight.Black, color = tokens.primary)
                                Text("${summary.totalGstr2bCount} invoices", fontSize = 9.sp, color = tokens.textMuted)
                            }
                            Column {
                                Text("Books Claimed ITC", fontSize = 10.5.sp, color = tokens.textMuted)
                                Text(FinancialEngine.formatPaise(summary.totalBooksItcClaimedPaise), fontSize = 13.5.sp, fontWeight = FontWeight.Black, color = tokens.text)
                                Text("${summary.totalBooksPurchaseCount} purchases", fontSize = 9.sp, color = tokens.textMuted)
                            }
                            Column {
                                Text("At-Risk (Not in 2B)", fontSize = 10.5.sp, color = tokens.textMuted)
                                Text(FinancialEngine.formatPaise(summary.missingIn2bAtRiskItcPaise), fontSize = 13.5.sp, fontWeight = FontWeight.Black, color = tokens.danger)
                                Text("${summary.missingIn2bCount} invoices", fontSize = 9.sp, color = tokens.danger)
                            }
                        }
                    }
                }
            }

            // Quick Status Filter Chips
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Filter Discrepancies:", fontWeight = FontWeight.Bold, fontSize = 11.5.sp, color = tokens.text)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        StatusFilterChip(
                            label = "All (${reconciliationItems.size})",
                            isSelected = selectedFilterStatus == null,
                            color = tokens.primary,
                            tokens = tokens,
                            onClick = { selectedFilterStatus = null },
                            modifier = Modifier.weight(1f).testTag("gstr2b_filter_all")
                        )
                        StatusFilterChip(
                            label = "Matched (${summary.matchedCount})",
                            isSelected = selectedFilterStatus == Gstr2bReconciliationEngine.MatchStatus.FullyMatched,
                            color = tokens.success,
                            tokens = tokens,
                            onClick = { selectedFilterStatus = Gstr2bReconciliationEngine.MatchStatus.FullyMatched },
                            modifier = Modifier.weight(1f).testTag("gstr2b_filter_matched")
                        )
                        StatusFilterChip(
                            label = "Mismatch (${summary.valueMismatchCount})",
                            isSelected = selectedFilterStatus == Gstr2bReconciliationEngine.MatchStatus.ValueMismatch,
                            color = tokens.warning,
                            tokens = tokens,
                            onClick = { selectedFilterStatus = Gstr2bReconciliationEngine.MatchStatus.ValueMismatch },
                            modifier = Modifier.weight(1f).testTag("gstr2b_filter_mismatch")
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        StatusFilterChip(
                            label = "Not in Books (${summary.missingInBooksCount})",
                            isSelected = selectedFilterStatus == Gstr2bReconciliationEngine.MatchStatus.MissingInBooks,
                            color = tokens.info,
                            tokens = tokens,
                            onClick = { selectedFilterStatus = Gstr2bReconciliationEngine.MatchStatus.MissingInBooks },
                            modifier = Modifier.weight(1f).testTag("gstr2b_filter_not_in_books")
                        )
                        StatusFilterChip(
                            label = "Not in 2B (${summary.missingIn2bCount})",
                            isSelected = selectedFilterStatus == Gstr2bReconciliationEngine.MatchStatus.MissingIn2B,
                            color = tokens.danger,
                            tokens = tokens,
                            onClick = { selectedFilterStatus = Gstr2bReconciliationEngine.MatchStatus.MissingIn2B },
                            modifier = Modifier.weight(1f).testTag("gstr2b_filter_not_in_2b")
                        )
                        StatusFilterChip(
                            label = "Ineligible (${summary.ineligibleItcCount})",
                            isSelected = selectedFilterStatus == Gstr2bReconciliationEngine.MatchStatus.IneligibleITC,
                            color = tokens.textMuted,
                            tokens = tokens,
                            onClick = { selectedFilterStatus = Gstr2bReconciliationEngine.MatchStatus.IneligibleITC },
                            modifier = Modifier.weight(1f).testTag("gstr2b_filter_ineligible")
                        )
                    }
                }
            }

            // Search Bar
            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search supplier GSTIN, name, or invoice #...", fontSize = 11.5.sp) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = tokens.textMuted) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(
                                onClick = { searchQuery = "" },
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear", tint = tokens.textMuted)
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().testTag("gstr2b_search_input"),
                    shape = RoundedCornerShape(10.dp),
                    singleLine = true
                )
            }

            // Items Count Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Showing ${filteredItems.size} Entries",
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = tokens.textMuted
                    )
                    Text("Tap an item to view side-by-side audit", fontSize = 10.sp, color = tokens.accent)
                }
            }

            // Empty State
            if (filteredItems.isEmpty()) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = tokens.card),
                        border = BorderStroke(1.dp, tokens.cardBorder),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = tokens.success, modifier = Modifier.size(36.dp))
                            Text("No Discrepancies Found", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = tokens.text)
                            Text("All invoices match the selected filter criteria.", fontSize = 11.sp, color = tokens.textMuted)
                        }
                    }
                }
            }

            // Reconciled Item Cards
            items(filteredItems, key = { it.id }) { item ->
                ReconciliationCard(
                    item = item,
                    onClick = { selectedDetailItem = item },
                    tokens = tokens
                )
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }

    // Side-by-Side Detailed Discrepancy Dialog
    val detailItem = selectedDetailItem
    if (detailItem != null) {
        DiscrepancyDetailDialog(
            item = detailItem,
            onDismiss = { selectedDetailItem = null },
            tokens = tokens
        )
    }
}

@Composable
fun StatusFilterChip(
    label: String,
    isSelected: Boolean,
    color: Color,
    tokens: PabaruTokens,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = if (isSelected) color.copy(alpha = 0.16f) else tokens.card,
        border = BorderStroke(1.dp, if (isSelected) color else tokens.cardBorder),
        modifier = modifier.heightIn(min = 48.dp)
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                label,
                fontSize = 10.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) color else tokens.textMuted,
                maxLines = 1
            )
        }
    }
}

@Composable
fun ReconciliationCard(
    item: Gstr2bReconciliationEngine.ReconciliationItem,
    onClick: () -> Unit,
    tokens: PabaruTokens
) {
    val statusLabel: String
    val statusColor: Color
    val statusBg: Color
    when (item.matchStatus) {
        Gstr2bReconciliationEngine.MatchStatus.FullyMatched -> {
            statusLabel = "MATCHED"
            statusColor = tokens.onSuccessContainer
            statusBg = tokens.successContainer
        }
        Gstr2bReconciliationEngine.MatchStatus.ValueMismatch -> {
            statusLabel = "MISMATCH"
            statusColor = tokens.onWarningContainer
            statusBg = tokens.warningContainer
        }
        Gstr2bReconciliationEngine.MatchStatus.MissingInBooks -> {
            statusLabel = "NOT IN BOOKS"
            statusColor = tokens.onInfoContainer
            statusBg = tokens.infoContainer
        }
        Gstr2bReconciliationEngine.MatchStatus.MissingIn2B -> {
            statusLabel = "NOT IN 2B"
            statusColor = tokens.onDangerContainer
            statusBg = tokens.dangerContainer
        }
        Gstr2bReconciliationEngine.MatchStatus.IneligibleITC -> {
            statusLabel = "BLOCKED ITC"
            statusColor = tokens.textMuted
            statusBg = tokens.secondaryBg
        }
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = tokens.card),
        border = BorderStroke(1.dp, tokens.cardBorder),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag("reconciliation_item_${item.id}")
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(item.supplierName, fontWeight = FontWeight.Bold, fontSize = 12.5.sp, color = tokens.text)
                    Text("GSTIN: ${item.supplierGstin}", fontSize = 10.sp, color = tokens.textMuted)
                }

                Surface(
                    color = statusBg,
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        statusLabel,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = statusColor
                    )
                }
            }

            HorizontalDivider(color = tokens.cardBorder.copy(alpha = 0.5f))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("Invoice No & Date", fontSize = 9.5.sp, color = tokens.textMuted)
                    Text("${item.invoiceNumber} • ${item.invoiceDate}", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = tokens.text)
                }

                if (item.matchStatus == Gstr2bReconciliationEngine.MatchStatus.ValueMismatch) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text("Variance", fontSize = 9.5.sp, color = tokens.textMuted)
                        Text("Tax Δ ${FinancialEngine.formatPaise(abs(item.diffTaxPaise))}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = tokens.danger)
                    }
                } else if (item.matchStatus == Gstr2bReconciliationEngine.MatchStatus.MissingIn2B) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text("At-Risk Tax", fontSize = 9.5.sp, color = tokens.danger)
                        Text(FinancialEngine.formatPaise(item.booksTotalTaxPaise), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = tokens.danger)
                    }
                } else {
                    Column(horizontalAlignment = Alignment.End) {
                        Text("2B Tax / ITC", fontSize = 9.5.sp, color = tokens.textMuted)
                        Text(
                            FinancialEngine.formatPaise(item.gstr2bTotalTaxPaise),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (item.matchStatus == Gstr2bReconciliationEngine.MatchStatus.FullyMatched) tokens.success else tokens.info
                        )
                    }
                }
            }

            // Quick Comparison Row
            Surface(
                color = tokens.secondaryBg,
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("2B Taxable: ${FinancialEngine.formatPaise(item.gstr2bTaxablePaise)}", fontSize = 9.5.sp, color = tokens.text)
                    Text("Books Taxable: ${FinancialEngine.formatPaise(item.booksTaxablePaise)}", fontSize = 9.5.sp, color = tokens.text)
                }
            }

            Text(item.remarks, fontSize = 9.5.sp, color = tokens.textMuted)
        }
    }
}

@Composable
fun DiscrepancyDetailDialog(
    item: Gstr2bReconciliationEngine.ReconciliationItem,
    onDismiss: () -> Unit,
    tokens: PabaruTokens
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = tokens.card,
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = tokens.primary),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.heightIn(min = 48.dp).testTag("dialog_dismiss_button")
            ) {
                Text("Close", color = tokens.primaryText, fontWeight = FontWeight.Bold)
            }
        },
        title = {
            Column {
                Text("Side-by-Side Reconciliation", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = tokens.text)
                Text("${item.supplierName} (${item.invoiceNumber})", fontSize = 11.sp, color = tokens.textMuted)
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Info block
                Surface(
                    color = tokens.secondaryBg,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text("Supplier GSTIN: ${item.supplierGstin}", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = tokens.text)
                        Text("Invoice Date: ${item.invoiceDate}", fontSize = 10.sp, color = tokens.textMuted)
                        Text("Audit Status: ${item.matchStatus.name}", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = tokens.accent)
                    }
                }

                // Comparison Table
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(tokens.primary.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                        .padding(vertical = 4.dp, horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Field", fontWeight = FontWeight.Bold, fontSize = 9.5.sp, color = tokens.text, modifier = Modifier.weight(1f))
                    Text("GSTR-2B", fontWeight = FontWeight.Bold, fontSize = 9.5.sp, color = tokens.text, modifier = Modifier.weight(1f))
                    Text("Books", fontWeight = FontWeight.Bold, fontSize = 9.5.sp, color = tokens.text, modifier = Modifier.weight(1f))
                    Text("Diff", fontWeight = FontWeight.Bold, fontSize = 9.5.sp, color = tokens.text, modifier = Modifier.weight(0.8f))
                }

                ComparisonDialogRow("Taxable", FinancialEngine.formatPaise(item.gstr2bTaxablePaise), FinancialEngine.formatPaise(item.booksTaxablePaise), FinancialEngine.formatPaise(item.diffTaxablePaise), item.diffTaxablePaise != 0L, tokens)
                ComparisonDialogRow("CGST", FinancialEngine.formatPaise(item.gstr2bCgstPaise), FinancialEngine.formatPaise(item.booksCgstPaise), FinancialEngine.formatPaise(item.gstr2bCgstPaise - item.booksCgstPaise), item.gstr2bCgstPaise != item.booksCgstPaise, tokens)
                ComparisonDialogRow("SGST", FinancialEngine.formatPaise(item.gstr2bSgstPaise), FinancialEngine.formatPaise(item.booksSgstPaise), FinancialEngine.formatPaise(item.gstr2bSgstPaise - item.booksSgstPaise), item.gstr2bSgstPaise != item.booksSgstPaise, tokens)
                ComparisonDialogRow("IGST", FinancialEngine.formatPaise(item.gstr2bIgstPaise), FinancialEngine.formatPaise(item.booksIgstPaise), FinancialEngine.formatPaise(item.gstr2bIgstPaise - item.booksIgstPaise), item.gstr2bIgstPaise != item.booksIgstPaise, tokens)
                ComparisonDialogRow("Total Tax", FinancialEngine.formatPaise(item.gstr2bTotalTaxPaise), FinancialEngine.formatPaise(item.booksTotalTaxPaise), FinancialEngine.formatPaise(item.diffTaxPaise), item.diffTaxPaise != 0L, tokens)

                HorizontalDivider(color = tokens.cardBorder)

                // Recommendation
                Surface(
                    color = tokens.card,
                    border = BorderStroke(1.dp, tokens.cardBorder),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text("Auditor Guidance:", fontWeight = FontWeight.Bold, fontSize = 10.sp, color = tokens.text)
                        val advice = when (item.matchStatus) {
                            Gstr2bReconciliationEngine.MatchStatus.FullyMatched -> "No action required. Invoice is safely claimed in GSTR-3B."
                            Gstr2bReconciliationEngine.MatchStatus.ValueMismatch -> "Check purchase invoice copy. If supplier billed higher/lower, request a credit/debit note or amend book entry."
                            Gstr2bReconciliationEngine.MatchStatus.MissingInBooks -> "Eligible ITC has not been claimed yet. Create a purchase entry in your ledger to claim this ITC."
                            Gstr2bReconciliationEngine.MatchStatus.MissingIn2B -> "High Risk! Supplier has not filed GSTR-1. Do not claim in GSTR-3B or follow up with supplier to file immediately."
                            Gstr2bReconciliationEngine.MatchStatus.IneligibleITC -> "Blocked under Section 17(5) or POS rules. Do not claim ITC in Table 4(A) of GSTR-3B."
                        }
                        Text(advice, fontSize = 9.5.sp, color = tokens.textMuted)
                    }
                }
            }
        }
    )
}

@Composable
fun ComparisonDialogRow(
    label: String,
    val2b: String,
    valBooks: String,
    diff: String,
    isDifferent: Boolean,
    tokens: PabaruTokens
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp, horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 9.sp, fontWeight = FontWeight.Medium, color = tokens.text, modifier = Modifier.weight(1f))
        Text(val2b, fontSize = 9.sp, color = tokens.text, modifier = Modifier.weight(1f))
        Text(valBooks, fontSize = 9.sp, color = tokens.text, modifier = Modifier.weight(1f))
        Text(
            diff,
            fontSize = 9.sp,
            fontWeight = if (isDifferent) FontWeight.Bold else FontWeight.Normal,
            color = if (isDifferent) tokens.danger else tokens.textMuted,
            modifier = Modifier.weight(0.8f)
        )
    }
}
