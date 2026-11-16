package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.engine.CmaAndGstEngine
import com.example.engine.FinancialEngine
import com.example.engine.GstPortalJsonEngine
import com.example.engine.Gstr1ExcelImportEngine
import com.example.model.GstSlabs
import com.example.model.RegistrationType
import com.example.ui.theme.*
import com.example.viewmodel.PabaruUiState
import com.example.viewmodel.PabaruViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun GstAssistantScreen(
    uiState: PabaruUiState,
    viewModel: PabaruViewModel? = null,
    initialStep: Int = 1
) {
    val context = LocalContext.current
    val tokens = PabaruTheme.tokens

    // Initial mode matches profile, but user can freely toggle anytime
    var isCompositionMode by remember(uiState.profile.registrationType) {
        mutableStateOf(uiState.profile.registrationType == RegistrationType.composition)
    }

    var selectedRegularTab by remember { mutableStateOf(0) } // 0: GSTR-1, 1: GSTR-3B, 2: Rule 88A Calculator
    var selectedPeriodFilter by remember { mutableStateOf(CmaAndGstEngine.GstPeriodFilter.AllTime) }

    // State for Excel prefilled invoices
    var localPrefilledInvoices by remember { mutableStateOf<List<Gstr1ExcelImportEngine.ImportedGstr1Invoice>>(emptyList()) }
    val effectivePrefilledInvoices = if (viewModel != null) uiState.prefilledGstr1Invoices else localPrefilledInvoices
    var lastImportedFileName by remember { mutableStateOf<String?>(null) }
    var showExcelImportDialog by remember { mutableStateOf(false) }

    // Computations
    val gstr1Data = remember(uiState.transactions, uiState.parties, uiState.profile, selectedPeriodFilter, effectivePrefilledInvoices) {
        CmaAndGstEngine.computeStatutoryGstr1(
            transactions = uiState.transactions,
            parties = uiState.parties,
            profile = uiState.profile,
            periodFilter = selectedPeriodFilter,
            prefilledInvoices = effectivePrefilledInvoices
        )
    }

    val gstr3bData = remember(
        uiState.transactions, uiState.parties, uiState.profile, gstr1Data, selectedPeriodFilter,
        uiState.rule88AStrategy, uiState.customRule88APercent, uiState.customRule88APaise
    ) {
        CmaAndGstEngine.computeStatutoryGstr3b(
            transactions = uiState.transactions,
            parties = uiState.parties,
            profile = uiState.profile,
            gstr1 = gstr1Data,
            periodFilter = selectedPeriodFilter,
            strategy = uiState.rule88AStrategy,
            customIgstForCgstPercent = uiState.customRule88APercent,
            customIgstForCgstPaise = uiState.customRule88APaise
        )
    }

    val cmp08Data = remember(uiState.transactions, uiState.profile, selectedPeriodFilter) {
        CmaAndGstEngine.computeStatutoryCmp08(
            transactions = uiState.transactions,
            profile = uiState.profile,
            periodFilter = selectedPeriodFilter
        )
    }

    val periodCode = remember(selectedPeriodFilter, gstr1Data, uiState.transactions, effectivePrefilledInvoices) {
        val refDates = buildList {
            gstr1Data.b2bInvoices.forEach { add(it.date) }
            gstr1Data.cdnrNotes.forEach { add(it.noteDate) }
            gstr1Data.exportInvoices.forEach { add(it.date) }
            if (isEmpty()) {
                uiState.transactions.filter { !it.isVoided }.forEach { add(it.date) }
                effectivePrefilledInvoices.forEach { add(it.date) }
            }
        }
        GstPortalJsonEngine.getFilingPeriodCode(selectedPeriodFilter, refDates)
    }

    var currentStep by remember(initialStep) { mutableStateOf(initialStep) } // 1: Choose period, 2: Add sales data, 3: Review summary, 4: File
    var isGstinExpanded by remember { mutableStateOf(false) }
    var showFilingConfirmDialog by remember { mutableStateOf(false) }
    var excelErrorMessage by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var showDetailedStatutoryTables by remember { mutableStateOf(false) }

    val rawGstin = uiState.profile.gstin.trim()
    val displayGstin = when {
        rawGstin.isBlank() -> "GSTIN not set"
        isGstinExpanded -> rawGstin
        else -> "GSTIN ${rawGstin.take(6)}…"
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(tokens.bg)
    ) {
        // 1. Scrollable Page Content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header (Business name once, TL-7319, Offline ready status pill, profile icon)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = uiState.profile.businessName.ifBlank { "My Business" },
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = tokens.text
                    )
                    Text(
                        text = uiState.profile.storeCode.ifBlank { "State ${uiState.profile.homeStateCode.ifBlank { "27" }}" },
                        fontSize = 12.sp,
                        color = tokens.textMuted
                    )
                }

                // Offline ready status pill
                Surface(
                    color = tokens.secondaryBg,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, tokens.cardBorder)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .background(tokens.success, CircleShape)
                        )
                        Text(
                            text = "Offline ready",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = tokens.text
                        )
                    }
                }
            }

            // 2. Title Card (Title, Subtitle, GSTIN short chip with expand & copy, Filing mode toggle)
            Card(
                colors = CardDefaults.cardColors(containerColor = tokens.card),
                border = BorderStroke(1.dp, tokens.cardBorder),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "GST filing assistant",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = tokens.text
                        )

                        // GSTIN as short chip that expands on tap to show full number with copy icon
                        Surface(
                            color = tokens.secondaryBg,
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(1.dp, tokens.cardBorder),
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .clickable {
                                    if (rawGstin.isBlank()) {
                                        Toast.makeText(context, "Set your GSTIN in Business Profile first", Toast.LENGTH_SHORT).show()
                                    } else {
                                        isGstinExpanded = !isGstinExpanded
                                    }
                                }
                                .testTag("gstin_chip_button")
                        ) {
                            Row(
                                modifier = Modifier.padding(start = 10.dp, end = if (isGstinExpanded && rawGstin.isNotBlank()) 0.dp else 10.dp, top = 4.dp, bottom = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = displayGstin,
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Medium,
                                    color = if (rawGstin.isBlank()) tokens.textMuted else tokens.text
                                )
                                if (isGstinExpanded && rawGstin.isNotBlank()) {
                                    IconButton(
                                        onClick = {
                                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            clipboard.setPrimaryClip(ClipData.newPlainText("GSTIN", rawGstin))
                                            Toast.makeText(context, "GSTIN copied to clipboard", Toast.LENGTH_SHORT).show()
                                        },
                                        modifier = Modifier.size(48.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ContentCopy,
                                            contentDescription = "Copy GSTIN",
                                            tint = tokens.accent,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Text(
                        text = "GSTR-1, GSTR-3B and CMP-08",
                        fontSize = 12.sp,
                        color = tokens.textMuted
                    )

                    HorizontalDivider(color = tokens.cardBorder.copy(alpha = 0.5f))

                    // Filing mode toggle with clear text label beside it
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (isCompositionMode) "Filing mode: Composition" else "Filing mode: Regular",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = tokens.text
                            )
                            Text(
                                text = if (isCompositionMode) {
                                    "State ${uiState.profile.homeStateCode.ifBlank { "27" }} · Period $periodCode · 1% Flat tax"
                                } else {
                                    "State ${uiState.profile.homeStateCode.ifBlank { "27" }} · Period $periodCode · Rule 88A set-off"
                                },
                                fontSize = 12.sp,
                                color = tokens.textMuted
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                color = if (isCompositionMode) tokens.accent.copy(alpha = 0.2f) else tokens.primary.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(6.dp),
                                border = BorderStroke(1.dp, if (isCompositionMode) tokens.accent else tokens.primary)
                            ) {
                                Text(
                                    text = if (isCompositionMode) "CMP-08" else "GSTR",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isCompositionMode) tokens.accent else tokens.primary,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                )
                            }

                            Switch(
                                checked = isCompositionMode,
                                onCheckedChange = { isCompositionMode = it },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = tokens.accent,
                                    checkedTrackColor = tokens.accent.copy(alpha = 0.4f),
                                    uncheckedThumbColor = tokens.primary,
                                    uncheckedTrackColor = tokens.secondaryBg
                                ),
                                modifier = Modifier.testTag("gst_scheme_mode_switch")
                            )
                        }
                    }
                }
            }

            if (isLoading) {
                LoadingSkeletonCard(tokens = tokens)
            }

            // -------------------------------------------------------------
            // 4. Step Flow: 4 Numbered Steps as a Vertical Card List
            // -------------------------------------------------------------

            // Step 1: Choose period
            StepContainerCard(
                stepNumber = 1,
                title = "Step 1: Choose period",
                subtitle = "Selected: ${selectedPeriodFilter.displayName} ($periodCode)",
                isExpanded = currentStep == 1,
                isCompleted = currentStep > 1,
                tokens = tokens,
                onHeaderClick = { currentStep = 1 }
            ) {
                // 2x2 grid of equal 44px buttons
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentHeight(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        PeriodGridButton(
                            label = "Current month",
                            isSelected = selectedPeriodFilter == CmaAndGstEngine.GstPeriodFilter.CurrentMonth,
                            tokens = tokens,
                            modifier = Modifier.weight(1f).height(48.dp),
                            onClick = { selectedPeriodFilter = CmaAndGstEngine.GstPeriodFilter.CurrentMonth }
                        )
                        PeriodGridButton(
                            label = "Previous month",
                            isSelected = selectedPeriodFilter == CmaAndGstEngine.GstPeriodFilter.PreviousMonth,
                            tokens = tokens,
                            modifier = Modifier.weight(1f).height(48.dp),
                            onClick = { selectedPeriodFilter = CmaAndGstEngine.GstPeriodFilter.PreviousMonth }
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        PeriodGridButton(
                            label = "Current quarter",
                            isSelected = selectedPeriodFilter == CmaAndGstEngine.GstPeriodFilter.Quarter,
                            tokens = tokens,
                            modifier = Modifier.weight(1f).height(48.dp),
                            onClick = { selectedPeriodFilter = CmaAndGstEngine.GstPeriodFilter.Quarter }
                        )
                        PeriodGridButton(
                            label = "All time",
                            isSelected = selectedPeriodFilter == CmaAndGstEngine.GstPeriodFilter.AllTime,
                            tokens = tokens,
                            modifier = Modifier.weight(1f).height(48.dp),
                            onClick = { selectedPeriodFilter = CmaAndGstEngine.GstPeriodFilter.AllTime }
                        )
                    }
                }
            }

            // Step 2: Add sales data
            StepContainerCard(
                stepNumber = 2,
                title = "Step 2: Add sales data",
                subtitle = if (effectivePrefilledInvoices.isNotEmpty()) {
                    "${effectivePrefilledInvoices.size} Excel invoices loaded"
                } else {
                    "${gstr1Data.b2bCount + gstr1Data.b2cCount} ledger invoices loaded"
                },
                isExpanded = currentStep == 2,
                isCompleted = currentStep > 2,
                tokens = tokens,
                onHeaderClick = { currentStep = 2 }
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = { showExcelImportDialog = true },
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, tokens.accent.copy(alpha = 0.7f)),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = tokens.secondaryBg,
                            contentColor = tokens.text
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("step2_upload_excel_button")
                    ) {
                        Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(16.dp), tint = tokens.accent)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Upload Excel", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = tokens.text)
                    }

                    OutlinedButton(
                        onClick = {
                            if (viewModel != null) viewModel.clearPrefilledGstr1Invoices() else localPrefilledInvoices = emptyList()
                            lastImportedFileName = null
                            Toast.makeText(context, "Using local app sales invoices", Toast.LENGTH_SHORT).show()
                        },
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, tokens.cardBorder),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = tokens.secondaryBg,
                            contentColor = tokens.text
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("step2_use_my_invoices_button")
                    ) {
                        Icon(Icons.Default.Receipt, contentDescription = null, modifier = Modifier.size(16.dp), tint = tokens.text)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Use my invoices", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = tokens.text)
                    }
                }

                // Friendly status or empty state
                if (excelErrorMessage != null) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = tokens.warningContainer),
                        border = BorderStroke(1.dp, tokens.warning),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = excelErrorMessage ?: "Couldn't read the file. Check the columns and try again.",
                                fontSize = 12.sp,
                                color = tokens.onWarningContainer,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedButton(
                                onClick = {
                                    excelErrorMessage = null
                                    showExcelImportDialog = true
                                },
                                border = BorderStroke(1.dp, tokens.warning),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = tokens.warning),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.heightIn(min = 48.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text("Retry", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                } else if (effectivePrefilledInvoices.isNotEmpty()) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = tokens.successContainer),
                        border = BorderStroke(1.dp, tokens.success),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.CloudDone, contentDescription = null, tint = tokens.success, modifier = Modifier.size(16.dp))
                                    Text(
                                        text = "Imported: ${lastImportedFileName ?: "Spreadsheet"} · ${effectivePrefilledInvoices.size} rows loaded",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = tokens.onSuccessContainer
                                    )
                                }
                                TextButton(
                                    onClick = {
                                        if (viewModel != null) viewModel.clearPrefilledGstr1Invoices() else localPrefilledInvoices = emptyList()
                                        lastImportedFileName = null
                                    },
                                    modifier = Modifier.heightIn(min = 48.dp),
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text("Clear", fontSize = 12.sp, color = tokens.danger, fontWeight = FontWeight.Bold)
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = {
                                        if (viewModel != null) {
                                            viewModel.commitPrefilledInvoicesToLedger(effectivePrefilledInvoices)
                                            lastImportedFileName = null
                                            Toast.makeText(context, "${effectivePrefilledInvoices.size} invoices saved to ledger!", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    border = BorderStroke(1.dp, tokens.success),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = tokens.onSuccessContainer),
                                    shape = RoundedCornerShape(6.dp),
                                    modifier = Modifier.heightIn(min = 48.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Save to ledger", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                } else if (gstr1Data.b2bCount + gstr1Data.b2cCount == 0) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = tokens.secondaryBg),
                        border = BorderStroke(1.dp, tokens.cardBorder),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "No sales found for this period. Upload an Excel file to get started",
                                fontSize = 12.sp,
                                color = tokens.textMuted,
                                textAlign = TextAlign.Center
                            )
                            OutlinedButton(
                                onClick = { showExcelImportDialog = true },
                                shape = RoundedCornerShape(6.dp),
                                border = BorderStroke(1.dp, tokens.accent),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = tokens.accent),
                                modifier = Modifier.heightIn(min = 48.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Upload Excel", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                } else {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = tokens.secondaryBg),
                        border = BorderStroke(1.dp, tokens.cardBorder),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = tokens.success, modifier = Modifier.size(16.dp))
                            Text(
                                text = "App sales ledger: ${gstr1Data.b2bCount + gstr1Data.b2cCount} invoices active (${gstr1Data.b2bCount} B2B, ${gstr1Data.b2cCount} B2C)",
                                fontSize = 12.sp,
                                color = tokens.text
                            )
                        }
                    }
                }
            }

            // Step 3: Review summary
            StepContainerCard(
                stepNumber = 3,
                title = "Step 3: Review summary",
                subtitle = "Turnover: ${FinancialEngine.formatPaise(gstr1Data.totalTaxablePaise)} · Tax: ${FinancialEngine.formatPaise(gstr1Data.totalTaxPaise)}",
                isExpanded = currentStep == 3,
                isCompleted = currentStep > 3,
                tokens = tokens,
                onHeaderClick = { currentStep = 3 }
            ) {
                // Friendly status banner above cards
                if (gstr1Data.totalTaxablePaise > 0 || (gstr1Data.b2bCount + gstr1Data.b2cCount > 0)) {
                    Surface(
                        color = tokens.successContainer,
                        border = BorderStroke(1.dp, tokens.success.copy(alpha = 0.7f)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = tokens.success, modifier = Modifier.size(16.dp))
                            Text(
                                text = "Outward supplies summarized for $periodCode",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = tokens.onSuccessContainer
                            )
                        }
                    }
                } else {
                    Surface(
                        color = tokens.warningContainer,
                        border = BorderStroke(1.dp, tokens.warning.copy(alpha = 0.7f)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = tokens.warning, modifier = Modifier.size(16.dp))
                            Text(
                                text = "Check these items — no transactions recorded for this period",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = tokens.onWarningContainer
                            )
                        }
                    }
                }

                // Four metric cards in 2x2 grid
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        MetricCard(
                            label = "Taxable turnover",
                            value = FinancialEngine.formatPaise(gstr1Data.totalTaxablePaise),
                            tokens = tokens,
                            modifier = Modifier.weight(1f)
                        )
                        MetricCard(
                            label = "Total tax",
                            value = FinancialEngine.formatPaise(gstr1Data.totalTaxPaise),
                            tokens = tokens,
                            modifier = Modifier.weight(1f),
                            valueColor = tokens.accent
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        MetricCard(
                            label = "B2B invoices",
                            value = "${gstr1Data.b2bCount}",
                            tokens = tokens,
                            modifier = Modifier.weight(1f)
                        )
                        MetricCard(
                            label = "B2C sales",
                            value = "${gstr1Data.b2cCount}",
                            tokens = tokens,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                HorizontalDivider(color = tokens.cardBorder.copy(alpha = 0.5f))

                // CGST, SGST, IGST breakdown rows
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    TaxBreakdownRow(label = "CGST", amountPaise = gstr1Data.totalCgstPaise, tokens = tokens)
                    TaxBreakdownRow(label = "SGST", amountPaise = gstr1Data.totalSgstPaise, tokens = tokens)
                    TaxBreakdownRow(label = "IGST", amountPaise = gstr1Data.totalIgstPaise, tokens = tokens)
                }

                // Expandable detailed statutory tables
                TextButton(
                    onClick = { showDetailedStatutoryTables = !showDetailedStatutoryTables },
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text(
                        text = if (showDetailedStatutoryTables) "Hide statutory tables" else "View statutory tables & invoices",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = tokens.accent
                    )
                    Icon(
                        imageVector = if (showDetailedStatutoryTables) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = tokens.accent
                    )
                }

                if (showDetailedStatutoryTables) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Table 4A & 4B B2B invoices preview
                        Card(
                            colors = CardDefaults.cardColors(containerColor = tokens.secondaryBg),
                            border = BorderStroke(1.dp, tokens.cardBorder),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("Table 4A & 4B: B2B Invoices (${gstr1Data.b2bInvoices.size})", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = tokens.accent)
                                if (gstr1Data.b2bInvoices.isEmpty()) {
                                    Text("No B2B sales registered in this period.", fontSize = 12.sp, color = tokens.textMuted)
                                } else {
                                    gstr1Data.b2bInvoices.take(5).forEach { inv ->
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text("${inv.invoiceNumber} • ${inv.partyName}", fontSize = 12.sp, color = tokens.text)
                                            Text(FinancialEngine.formatPaise(inv.totalPaise), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = tokens.text)
                                        }
                                    }
                                }
                            }
                        }

                        // Table 12 HSN Summary preview
                        Card(
                            colors = CardDefaults.cardColors(containerColor = tokens.secondaryBg),
                            border = BorderStroke(1.dp, tokens.cardBorder),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("Table 12: HSN Summary (${gstr1Data.hsnSummary.size} items)", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = tokens.accent)
                                gstr1Data.hsnSummary.take(4).forEach { hsn ->
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("HSN ${hsn.hsnCode} • ${hsn.description}", fontSize = 12.sp, color = tokens.text)
                                        Text(FinancialEngine.formatPaise(hsn.taxablePaise), fontSize = 12.sp, color = tokens.text)
                                    }
                                }
                            }
                        }

                        // Table 13 Document Summary preview
                        Card(
                            colors = CardDefaults.cardColors(containerColor = tokens.secondaryBg),
                            border = BorderStroke(1.dp, tokens.cardBorder),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("Table 13: Document Summary", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = tokens.accent)
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Serial range", fontSize = 12.sp, color = tokens.textMuted)
                                    Text("${gstr1Data.docSummary.minInvoiceNumber} to ${gstr1Data.docSummary.maxInvoiceNumber}", fontSize = 12.sp, color = tokens.text)
                                }
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Net issued invoices", fontSize = 12.sp, color = tokens.textMuted)
                                    Text("${gstr1Data.docSummary.netIssuedCount}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = tokens.text)
                                }
                            }
                        }
                    }
                }
            }

            // Step 4: File
            StepContainerCard(
                stepNumber = 4,
                title = "Step 4: File",
                subtitle = "Form: ${if (selectedRegularTab == 1) "GSTR-3B" else (if (selectedRegularTab == 2) "Rule 88A Calc" else "GSTR-1 outward")}",
                isExpanded = currentStep == 4,
                isCompleted = false,
                tokens = tokens,
                onHeaderClick = { currentStep = 4 }
            ) {
                // Segmented control: GSTR-1 outward & GSTR-3B + Rule 88A quick action
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = tokens.secondaryBg,
                        border = BorderStroke(1.dp, tokens.cardBorder),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            SegmentButton(
                                text = "GSTR-1 outward",
                                isSelected = selectedRegularTab == 0,
                                tokens = tokens,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("tab_gstr1_button"),
                                onClick = { selectedRegularTab = 0 }
                            )

                            SegmentButton(
                                text = "GSTR-3B",
                                isSelected = selectedRegularTab == 1,
                                tokens = tokens,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("tab_gstr3b_button"),
                                onClick = { selectedRegularTab = 1 }
                            )
                        }
                    }

                    // Quick action icon for Rule 88A Calculator in return switcher header
                    OutlinedIconButton(
                        onClick = { selectedRegularTab = if (selectedRegularTab == 2) 1 else 2 },
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, if (selectedRegularTab == 2) tokens.primary else tokens.cardBorder),
                        colors = IconButtonDefaults.outlinedIconButtonColors(
                            containerColor = if (selectedRegularTab == 2) tokens.primary.copy(alpha = 0.2f) else tokens.secondaryBg
                        ),
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("tab_rule88a_calculator_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Calculate,
                            contentDescription = "Rule 88A Calculator",
                            tint = if (selectedRegularTab == 2) tokens.primary else tokens.text,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // Summary line below return switch
                Text(
                    text = when (selectedRegularTab) {
                        1 -> "GSTR-3B summary for period $periodCode • Monthly tax liability & statutory ITC set-off"
                        2 -> "Rule 88A ITC calculator • Optimizing IGST, CGST, SGST cross-utilization"
                        else -> "GSTR-1 outward supplies for period $periodCode • Offline JSON export"
                    },
                    fontSize = 12.sp,
                    color = tokens.textMuted
                )

                HorizontalDivider(color = tokens.cardBorder.copy(alpha = 0.5f))

                when (selectedRegularTab) {
                    0 -> {
                        // GSTR-1 File Overview
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("GSTR-1 JSON Schema export", fontSize = 12.sp, color = tokens.text, fontWeight = FontWeight.Medium)
                                val hasGstin = rawGstin.isNotBlank()
                                Surface(
                                    color = if (hasGstin) tokens.successContainer else tokens.warningContainer,
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = if (hasGstin) "GSTIN Ready" else "Set GSTIN",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (hasGstin) tokens.onSuccessContainer else tokens.onWarningContainer,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Total document count", fontSize = 12.sp, color = tokens.textMuted)
                                Text("${gstr1Data.b2bCount + gstr1Data.b2cCount} invoices", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = tokens.text)
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Total liability amount", fontSize = 12.sp, color = tokens.textMuted)
                                Text(FinancialEngine.formatPaise(gstr1Data.totalTaxPaise), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = tokens.accent)
                            }

                            OutlinedButton(
                                onClick = {
                                    val jsonStr = GstPortalJsonEngine.generateGstr1Json(uiState.profile, gstr1Data, selectedPeriodFilter)
                                    val filename = "GSTR1_${rawGstin.ifBlank { "UNREG" }}_$periodCode.json"
                                    GstPortalJsonEngine.shareOrSaveJson(context, jsonStr, filename, "GSTR-1")
                                },
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, tokens.cardBorder),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = tokens.secondaryBg,
                                    contentColor = tokens.text
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .testTag("export_gstr1_json_step4_button")
                            ) {
                                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp), tint = tokens.text)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Export GSTR-1 JSON directly", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = tokens.text)
                            }
                        }
                    }
                    1 -> {
                        // GSTR-3B Return Details
                        Gstr3bReturnView(
                            uiState = uiState,
                            gstr3bData = gstr3bData,
                            periodFilter = selectedPeriodFilter,
                            periodCode = periodCode,
                            tokens = tokens,
                            onOpenCalculator = { selectedRegularTab = 2 }
                        )
                    }
                    2 -> {
                        // Rule 88A Calculator View
                        Rule88ACalculatorView(
                            initialGrossIgst = gstr3bData.outwardIgstPaise,
                            initialGrossCgst = gstr3bData.outwardCgstPaise,
                            initialGrossSgst = gstr3bData.outwardSgstPaise,
                            initialItcIgst = gstr3bData.eligibleItcIgstPaise,
                            initialItcCgst = gstr3bData.eligibleItcCgstPaise,
                            initialItcSgst = gstr3bData.eligibleItcSgstPaise,
                            onApplyToGstr3b = { strategy, customPct ->
                                if (viewModel != null) {
                                    viewModel.setRule88AStrategy(strategy, customPct)
                                }
                                selectedRegularTab = 1
                            }
                        )
                    }
                }
            }

            // Composition return view if in composition mode
            if (isCompositionMode) {
                Cmp08ReturnView(
                    uiState = uiState,
                    cmp08Data = cmp08Data,
                    periodFilter = selectedPeriodFilter,
                    periodCode = periodCode,
                    tokens = tokens
                )
            }
        }

        // 5. Sticky Bottom Action Bar Above Navigation
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding(),
            color = tokens.headerBg,
            shadowElevation = 8.dp,
            border = BorderStroke(1.dp, tokens.cardBorder)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // One full-width primary button (minimum height 48px, the only filled button)
                Button(
                    onClick = {
                        if (currentStep < 4) {
                            currentStep += 1
                        } else {
                            showFilingConfirmDialog = true
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("step_primary_action_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = tokens.primary,
                        contentColor = tokens.primaryText
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(
                        imageVector = if (currentStep < 4) Icons.AutoMirrored.Filled.ArrowForward else Icons.Default.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = tokens.primaryText
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (currentStep < 4) {
                            "Continue"
                        } else if (isCompositionMode) {
                            "Review and file CMP-08"
                        } else if (selectedRegularTab == 1) {
                            "Review and file GSTR-3B"
                        } else {
                            "Review and file GSTR-1"
                        },
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = tokens.primaryText
                    )
                }

                // Small "Download draft" link or secondary button
                TextButton(
                    onClick = {
                        val gstinPrefix = rawGstin.ifBlank { "UNREG" }
                        val (formTitle, jsonStr, filePrefix) = when {
                            isCompositionMode -> Triple(
                                "CMP-08 Draft",
                                GstPortalJsonEngine.generateCmp08Json(uiState.profile, cmp08Data, selectedPeriodFilter),
                                "CMP08_DRAFT"
                            )
                            selectedRegularTab == 1 -> Triple(
                                "GSTR-3B Draft",
                                GstPortalJsonEngine.generateGstr3bJson(uiState.profile, gstr3bData, selectedPeriodFilter),
                                "GSTR3B_DRAFT"
                            )
                            else -> Triple(
                                "GSTR-1 Draft",
                                GstPortalJsonEngine.generateGstr1Json(uiState.profile, gstr1Data, selectedPeriodFilter),
                                "GSTR1_DRAFT"
                            )
                        }
                        val filename = "${filePrefix}_${gstinPrefix}_$periodCode.json"
                        GstPortalJsonEngine.shareOrSaveJson(context, jsonStr, filename, formTitle)
                        Toast.makeText(context, "$formTitle JSON saved for $periodCode", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("download_draft_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = tokens.accent
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Download draft",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = tokens.accent
                    )
                }
            }
        }
    }

    // Confirmation Dialog for Filing
    if (showFilingConfirmDialog) {
        val formLabel = if (isCompositionMode) "CMP-08" else if (selectedRegularTab == 1) "GSTR-3B" else "GSTR-1"
        AlertDialog(
            onDismissRequest = { showFilingConfirmDialog = false },
            title = {
                Text(
                    text = "Export $formLabel for $periodCode?",
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = tokens.text
                )
            },
            text = {
                Text(
                    text = "Generate $formLabel JSON for $periodCode for ${uiState.profile.businessName.ifBlank { "My Business" }}. Verify the values in the GST offline tool before uploading to the portal.",
                    fontSize = 13.sp,
                    color = tokens.textMuted
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showFilingConfirmDialog = false
                        val gstinPrefix = rawGstin.ifBlank { "UNREG" }
                        val (jsonStr, filePrefix) = when {
                            isCompositionMode -> GstPortalJsonEngine.generateCmp08Json(uiState.profile, cmp08Data, selectedPeriodFilter) to "CMP08"
                            selectedRegularTab == 1 -> GstPortalJsonEngine.generateGstr3bJson(uiState.profile, gstr3bData, selectedPeriodFilter) to "GSTR3B"
                            else -> GstPortalJsonEngine.generateGstr1Json(uiState.profile, gstr1Data, selectedPeriodFilter) to "GSTR1"
                        }
                        val filename = "${filePrefix}_${gstinPrefix}_$periodCode.json"
                        GstPortalJsonEngine.shareOrSaveJson(context, jsonStr, filename, formLabel)
                        Toast.makeText(context, "$formLabel JSON saved for $periodCode", Toast.LENGTH_LONG).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = tokens.primary),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.testTag("confirm_filing_button")
                ) {
                    Text("Confirm", fontWeight = FontWeight.Bold, color = tokens.primaryText, fontSize = 13.sp)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showFilingConfirmDialog = false },
                    border = BorderStroke(1.dp, tokens.cardBorder),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.testTag("cancel_filing_button")
                ) {
                    Text("Cancel", color = tokens.text, fontSize = 13.sp)
                }
            },
            containerColor = tokens.card,
            shape = RoundedCornerShape(14.dp)
        )
    }

    if (showExcelImportDialog) {
        Gstr1ExcelImportDialog(
            uiState = uiState,
            tokens = tokens,
            onDismiss = { showExcelImportDialog = false },
            onApplyPrefill = { invoices ->
                if (viewModel != null) {
                    viewModel.setPrefilledGstr1Invoices(invoices)
                } else {
                    localPrefilledInvoices = invoices
                }
                showExcelImportDialog = false
                Toast.makeText(context, "${invoices.size} invoices pre-filled into GSTR-1!", Toast.LENGTH_SHORT).show()
            },
            onCommitDirectToLedger = { invoices ->
                if (viewModel != null) {
                    viewModel.commitPrefilledInvoicesToLedger(invoices)
                } else {
                    localPrefilledInvoices = emptyList()
                }
                lastImportedFileName = null
                showExcelImportDialog = false
                Toast.makeText(context, "${invoices.size} invoices saved directly to sales ledger!", Toast.LENGTH_SHORT).show()
            },
            onFileLoaded = { loadedName -> lastImportedFileName = loadedName }
        )
    }
}

// =====================================================================
// 1. GSTR-1 View: Outward Supplies & Statutory Tables
// =====================================================================
@Composable
private fun Gstr1ReturnView(
    uiState: PabaruUiState,
    gstr1Data: CmaAndGstEngine.StatutoryGstr1Data,
    periodFilter: CmaAndGstEngine.GstPeriodFilter,
    periodCode: String,
    tokens: com.example.ui.theme.PabaruTokens,
    activePrefilledInvoices: List<Gstr1ExcelImportEngine.ImportedGstr1Invoice> = emptyList(),
    onOpenExcelImport: () -> Unit = {},
    onClearPrefill: () -> Unit = {},
    onCommitToLedger: (List<Gstr1ExcelImportEngine.ImportedGstr1Invoice>) -> Unit = {}
) {
    val context = LocalContext.current

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        // Active Excel Pre-fill Banner
        if (activePrefilledInvoices.isNotEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = tokens.successContainer),
                    border = BorderStroke(1.dp, tokens.success),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.CloudDone, contentDescription = null, tint = tokens.success, modifier = Modifier.size(18.dp))
                                Column {
                                    Text(
                                        text = "Excel Pre-filled Data Active (${activePrefilledInvoices.size} Invoices)",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = tokens.onSuccessContainer
                                    )
                                    Text(
                                        text = "Populating Table 4A/B, Table 7, Table 12 & GSTR-1 Portal JSON",
                                        fontSize = 12.sp,
                                        color = tokens.onSuccessContainer.copy(alpha = 0.85f)
                                    )
                                }
                            }
                            Surface(
                                color = tokens.success.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = "Pre-fill Active",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = tokens.onSuccessContainer,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        HorizontalDivider(color = tokens.success.copy(alpha = 0.3f))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = { onCommitToLedger(activePrefilledInvoices) },
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        containerColor = tokens.success.copy(alpha = 0.15f),
                                        contentColor = tokens.onSuccessContainer
                                    ),
                                    border = BorderStroke(1.dp, tokens.success),
                                    shape = RoundedCornerShape(6.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                    modifier = Modifier
                                        .heightIn(min = 48.dp)
                                        .testTag("save_prefill_to_ledger_button")
                                ) {
                                    Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(14.dp), tint = tokens.onSuccessContainer)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Save to Ledger", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = tokens.onSuccessContainer)
                                }

                                OutlinedButton(
                                    onClick = onOpenExcelImport,
                                    shape = RoundedCornerShape(6.dp),
                                    border = BorderStroke(1.dp, tokens.cardBorder),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        containerColor = tokens.secondaryBg,
                                        contentColor = tokens.text
                                    ),
                                    modifier = Modifier.heightIn(min = 48.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Text("Re-map / Upload", fontSize = 12.sp, color = tokens.text)
                                }
                            }

                            TextButton(
                                onClick = onClearPrefill,
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .testTag("clear_prefill_button")
                            ) {
                                Text("Clear", fontSize = 12.sp, color = tokens.danger, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // 5. Summary Section: Outward supplies summary
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = tokens.card),
                border = BorderStroke(1.dp, tokens.cardBorder),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Header row: Title + Subtitle on left, Upload Excel on right
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Outward supplies summary",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = tokens.text
                            )
                            Text(
                                text = "GSTR-1 · period $periodCode",
                                fontSize = 12.sp,
                                color = tokens.textMuted
                            )
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(
                                onClick = onOpenExcelImport,
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, tokens.accent.copy(alpha = 0.6f)),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = tokens.secondaryBg,
                                    contentColor = tokens.text
                                ),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .testTag("upload_excel_gstr1_button")
                            ) {
                                Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(16.dp), tint = tokens.accent)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Upload Excel", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = tokens.text)
                            }

                            OutlinedButton(
                                onClick = {
                                    val jsonStr = GstPortalJsonEngine.generateGstr1Json(uiState.profile, gstr1Data, periodFilter)
                                    val filename = "GSTR1_${uiState.profile.gstin.trim().ifBlank { "UNREG" }}_$periodCode.json"
                                    GstPortalJsonEngine.shareOrSaveJson(context, jsonStr, filename, "GSTR-1")
                                },
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, tokens.cardBorder),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = tokens.secondaryBg,
                                    contentColor = tokens.text
                                ),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .testTag("export_gstr1_json_button")
                            ) {
                                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(14.dp), tint = tokens.text)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("JSON", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = tokens.text)
                            }
                        }
                    }

                    HorizontalDivider(color = tokens.cardBorder.copy(alpha = 0.5f))

                    // 4 metric cards in 2x2 grid:
                    // Taxable turnover, Total tax, B2B invoices, B2C sales
                    // Label 12px muted, value 20px medium weight
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            MetricCard(
                                label = "Taxable turnover",
                                value = FinancialEngine.formatPaise(gstr1Data.totalTaxablePaise),
                                tokens = tokens,
                                modifier = Modifier.weight(1f)
                            )
                            MetricCard(
                                label = "Total tax",
                                value = FinancialEngine.formatPaise(gstr1Data.totalTaxPaise),
                                tokens = tokens,
                                modifier = Modifier.weight(1f),
                                valueColor = tokens.accent
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            MetricCard(
                                label = "B2B invoices",
                                value = "${gstr1Data.b2bCount}",
                                tokens = tokens,
                                modifier = Modifier.weight(1f)
                            )
                            MetricCard(
                                label = "B2C sales",
                                value = "${gstr1Data.b2cCount}",
                                tokens = tokens,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    HorizontalDivider(color = tokens.cardBorder.copy(alpha = 0.5f))

                    // Under them, list CGST, SGST and IGST as rows with their amounts right-aligned (₹0.00 when none).
                    // Never show a label without a value.
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TaxBreakdownRow(
                            label = "CGST",
                            amountPaise = gstr1Data.totalCgstPaise,
                            tokens = tokens
                        )
                        TaxBreakdownRow(
                            label = "SGST",
                            amountPaise = gstr1Data.totalSgstPaise,
                            tokens = tokens
                        )
                        TaxBreakdownRow(
                            label = "IGST",
                            amountPaise = gstr1Data.totalIgstPaise,
                            tokens = tokens
                        )
                    }
                }
            }
        }

        // Table 4A & 4B: B2B Invoices
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = tokens.card),
                border = BorderStroke(1.dp, tokens.cardBorder),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Table 4A & 4B: B2B Invoices", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = tokens.accent)
                            val excelNote = if (gstr1Data.prefilledExcelCount > 0) " (incl. ${gstr1Data.prefilledExcelCount} from Excel)" else ""
                            Text("Supplies made to GST registered buyers (${gstr1Data.b2bInvoices.size}$excelNote)", fontSize = 12.sp, color = tokens.textMuted)
                        }
                        Text(
                            text = FinancialEngine.formatPaise(gstr1Data.b2bTaxablePaise),
                            fontWeight = FontWeight.Black,
                            fontSize = 13.sp,
                            color = tokens.text
                        )
                    }

                    HorizontalDivider(color = tokens.cardBorder)

                    if (gstr1Data.b2bInvoices.isEmpty()) {
                        Text(
                            text = "No B2B sales registered in this period. Invoices created or uploaded from Excel will automatically appear here.",
                            fontSize = 12.sp,
                            color = tokens.textMuted,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    } else {
                        gstr1Data.b2bInvoices.take(12).forEach { inv ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = "${inv.invoiceNumber} • ${inv.partyName}",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = tokens.text
                                        )
                                        if (inv.isPrefilledFromExcel) {
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Surface(
                                                color = tokens.successContainer,
                                                shape = RoundedCornerShape(4.dp),
                                                border = BorderStroke(1.dp, tokens.success.copy(alpha = 0.5f))
                                            ) {
                                                Text(
                                                    text = "Excel Pre-fill",
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = tokens.onSuccessContainer,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }
                                    Text(
                                        text = "GSTIN: ${inv.gstin} | POS: ${inv.posStateCode} | Date: ${inv.date}",
                                        fontSize = 12.sp,
                                        color = tokens.textMuted,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = FinancialEngine.formatPaise(inv.totalPaise),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = tokens.text
                                    )
                                    Text(
                                        text = "Tax: ${FinancialEngine.formatPaise(inv.cgstPaise + inv.sgstPaise + inv.igstPaise)}",
                                        fontSize = 12.sp,
                                        color = tokens.accent
                                    )
                                }
                            }
                            HorizontalDivider(color = tokens.cardBorder.copy(alpha = 0.4f))
                        }
                    }
                }
            }
        }

        // Table 7: B2C Small Supplies
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = tokens.card),
                border = BorderStroke(1.dp, tokens.cardBorder),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Table 7: B2C Small (Retail Consumers)", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = tokens.accent)
                    Text("Intra-state and inter-state sales to unregistered persons rate-wise", fontSize = 12.sp, color = tokens.textMuted)

                    HorizontalDivider(color = tokens.cardBorder)

                    if (gstr1Data.b2csSupplies.isEmpty()) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("B2C Turnover", fontSize = 12.sp, color = tokens.text)
                            Text(FinancialEngine.formatPaise(gstr1Data.b2cTaxablePaise), fontWeight = FontWeight.Bold, fontSize = 12.sp, color = tokens.text)
                        }
                    } else {
                        gstr1Data.b2csSupplies.forEach { b2cs ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Rate ${GstSlabs.label(b2cs.gstRateBps)} (${b2cs.supplyType} - POS: ${b2cs.posStateCode})",
                                    fontSize = 12.sp,
                                    color = tokens.text
                                )
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = FinancialEngine.formatPaise(b2cs.taxablePaise),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = tokens.text
                                    )
                                    Text(
                                        text = "Tax: ${FinancialEngine.formatPaise(b2cs.cgstPaise + b2cs.sgstPaise + b2cs.igstPaise)}",
                                        fontSize = 12.sp,
                                        color = tokens.textMuted
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Table 12: HSN Summary of Outward Supplies
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = tokens.card),
                border = BorderStroke(1.dp, tokens.cardBorder),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Table 12: HSN/SAC Summary", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = tokens.accent)
                    Text("Tariff code-wise outward quantity & tax classification", fontSize = 12.sp, color = tokens.textMuted)

                    HorizontalDivider(color = tokens.cardBorder)

                    if (gstr1Data.hsnSummary.isEmpty()) {
                        Text("No itemized HSN entries recorded yet.", fontSize = 12.sp, color = tokens.textMuted)
                    } else {
                        gstr1Data.hsnSummary.forEach { hsn ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "HSN ${hsn.hsnCode} • ${hsn.description}",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = tokens.text
                                    )
                                    Text(
                                        text = "Qty: ${FinancialEngine.formatQuantityMilli(hsn.totalQuantityMilli)} ${hsn.uqc} • Rate: ${GstSlabs.label(hsn.gstRateBps)}",
                                        fontSize = 12.sp,
                                        color = tokens.textMuted
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = FinancialEngine.formatPaise(hsn.taxablePaise),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = tokens.text
                                    )
                                    Text(
                                        text = "Tax: ${FinancialEngine.formatPaise(hsn.cgstPaise + hsn.sgstPaise + hsn.igstPaise)}",
                                        fontSize = 12.sp,
                                        color = tokens.accent
                                    )
                                }
                            }
                            HorizontalDivider(color = tokens.cardBorder.copy(alpha = 0.3f))
                        }
                    }
                }
            }
        }

        // Table 13: Document Summary
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = tokens.card),
                border = BorderStroke(1.dp, tokens.cardBorder),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Table 13: Documents Issued", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = tokens.accent)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Serial Range", fontSize = 12.sp, color = tokens.text)
                        Text("${gstr1Data.docSummary.minInvoiceNumber} to ${gstr1Data.docSummary.maxInvoiceNumber}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = tokens.text)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Total Issued / Cancelled", fontSize = 12.sp, color = tokens.text)
                        Text("${gstr1Data.docSummary.totalIssued} issued • ${gstr1Data.docSummary.cancelledCount} voided", fontSize = 12.sp, color = tokens.text)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Net Valid Invoices", fontSize = 12.sp, color = tokens.text)
                        Text("${gstr1Data.docSummary.netIssuedCount}", fontSize = 12.sp, fontWeight = FontWeight.Black, color = tokens.text)
                    }
                }
            }
        }

        // 6. Main Action: Full-width primary button "Review and file GSTR-1"
        item {
            Button(
                onClick = {
                    val jsonStr = GstPortalJsonEngine.generateGstr1Json(uiState.profile, gstr1Data, periodFilter)
                    val filename = "GSTR1_${uiState.profile.gstin.trim().ifBlank { "UNREG" }}_$periodCode.json"
                    GstPortalJsonEngine.shareOrSaveJson(context, jsonStr, filename, "GSTR-1")
                    Toast.makeText(context, "GSTR-1 JSON saved for $periodCode", Toast.LENGTH_LONG).show()
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = tokens.primary,
                    contentColor = tokens.primaryText
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag("review_and_file_gstr1_button")
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = tokens.primaryText
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Review and file GSTR-1",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = tokens.primaryText
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

// =====================================================================
// 2. GSTR-3B View: Statutory Table 3.1, Table 4 ITC & Rule 88A Matrix
// =====================================================================
@Composable
private fun Gstr3bReturnView(
    uiState: PabaruUiState,
    gstr3bData: CmaAndGstEngine.StatutoryGstr3bData,
    periodFilter: CmaAndGstEngine.GstPeriodFilter,
    periodCode: String,
    tokens: com.example.ui.theme.PabaruTokens,
    onOpenCalculator: () -> Unit = {}
) {
    val context = LocalContext.current
    val matrix = gstr3bData.setOffMatrix

    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        // Top Header & JSON Export Action
        Card(
                colors = CardDefaults.cardColors(containerColor = tokens.card),
                border = BorderStroke(1.dp, tokens.cardBorder),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("GSTR-3B Statutory Return Summary", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = tokens.text)
                            Text("Rule 88A ITC Matrix • Return Period: $periodCode", fontSize = 11.sp, color = tokens.textMuted)
                        }

                        Button(
                            onClick = {
                                val jsonStr = GstPortalJsonEngine.generateGstr3bJson(uiState.profile, gstr3bData, periodFilter)
                                val filename = "GSTR3B_${uiState.profile.gstin.trim().ifBlank { "UNREG" }}_$periodCode.json"
                                GstPortalJsonEngine.shareOrSaveJson(context, jsonStr, filename, "GSTR-3B")
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = tokens.primary),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("export_gstr3b_json_button")
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(14.dp), tint = tokens.primaryText)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Export Portal JSON", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = tokens.primaryText)
                        }
                    }

                    HorizontalDivider(color = tokens.cardBorder)

                    // 3 Major Pillars of GSTR-3B
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Gross Tax Liability", fontSize = 10.sp, color = tokens.textMuted)
                            Text(FinancialEngine.formatPaise(matrix.totalGrossLiabilityPaise), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = tokens.text)
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Eligible ITC (Credit)", fontSize = 10.sp, color = tokens.textMuted)
                            Text(FinancialEngine.formatPaise(matrix.totalItcAvailablePaise), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = tokens.success)
                        }
                        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                            Text("Net Cash Liability", fontSize = 10.sp, color = tokens.textMuted)
                            Text(
                                FinancialEngine.formatPaise(matrix.totalCashPayablePaise),
                                fontWeight = FontWeight.Black,
                                fontSize = 13.sp,
                                color = if (matrix.totalCashPayablePaise > 0) tokens.danger else tokens.success
                            )
                        }
                    }
                }
            }
        }

        // Table 3.1 & Table 4 ITC Summary Cards
        Card(
            colors = CardDefaults.cardColors(containerColor = tokens.card),
            border = BorderStroke(1.dp, tokens.cardBorder),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("3.1 Outward Taxable Supplies", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = tokens.accent)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Total Outward Taxable Value", fontSize = 11.sp, color = tokens.text)
                    Text(FinancialEngine.formatPaise(gstr3bData.outwardTaxablePaise), fontWeight = FontWeight.Bold, color = tokens.text)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Integrated Tax (IGST)", fontSize = 11.sp, color = tokens.textMuted)
                    Text(FinancialEngine.formatPaise(gstr3bData.outwardIgstPaise), color = tokens.text)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Central Tax (CGST) + State Tax (SGST)", fontSize = 11.sp, color = tokens.textMuted)
                    Text("${FinancialEngine.formatPaise(gstr3bData.outwardCgstPaise)} + ${FinancialEngine.formatPaise(gstr3bData.outwardSgstPaise)}", color = tokens.text)
                }

                HorizontalDivider(color = tokens.cardBorder)

                Text("4. Eligible Input Tax Credit (ITC)", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = tokens.success)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("All other ITC from Purchases", fontSize = 11.sp, color = tokens.text)
                    Text(FinancialEngine.formatPaise(gstr3bData.totalEligibleItcPaise), fontWeight = FontWeight.Bold, color = tokens.success)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("IGST Credit: ${FinancialEngine.formatPaise(gstr3bData.eligibleItcIgstPaise)}", fontSize = 10.sp, color = tokens.textMuted)
                    Text("CGST: ${FinancialEngine.formatPaise(gstr3bData.eligibleItcCgstPaise)} | SGST: ${FinancialEngine.formatPaise(gstr3bData.eligibleItcSgstPaise)}", fontSize = 10.sp, color = tokens.textMuted)
                }
            }
        }

        // =============================================================
        // AUTOMATIC RULE 88A ITC SET-OFF MATRIX TABLE
        // =============================================================
        Card(
                colors = CardDefaults.cardColors(containerColor = tokens.card),
                border = BorderStroke(1.dp, tokens.cardBorder),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text("Rule 88A ITC Set-off Matrix", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = tokens.text)
                                Surface(
                                    color = tokens.primary.copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = matrix.strategy.displayName,
                                        fontSize = 9.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = tokens.primary,
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Text("Statutory Order of Utilization (Circular 98/17/2019)", fontSize = 10.sp, color = tokens.textMuted)
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (matrix.cashSavingsPaise > 0) {
                                Surface(
                                    color = tokens.successContainer,
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = "Saves ${FinancialEngine.formatPaise(matrix.cashSavingsPaise)}",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = tokens.onSuccessContainer,
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            FilledTonalButton(
                                onClick = onOpenCalculator,
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .testTag("launch_rule_88a_calculator_btn")
                            ) {
                                Icon(Icons.Default.Calculate, contentDescription = null, modifier = Modifier.size(13.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Calculator", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    HorizontalDivider(color = tokens.cardBorder)

                    // Scrollable Interactive Matrix Table
                    Box(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                        Column(modifier = Modifier.widthIn(min = 520.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            // Table Header Row
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(tokens.secondaryBg, RoundedCornerShape(6.dp))
                                    .padding(vertical = 6.dp, horizontal = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Tax Head", modifier = Modifier.width(90.dp), fontWeight = FontWeight.Bold, fontSize = 10.sp, color = tokens.text)
                                Text("Gross Tax", modifier = Modifier.width(85.dp), fontWeight = FontWeight.Bold, fontSize = 10.sp, textAlign = TextAlign.End, color = tokens.text)
                                Text("Paid via IGST", modifier = Modifier.width(85.dp), fontWeight = FontWeight.Bold, fontSize = 10.sp, textAlign = TextAlign.End, color = tokens.success)
                                Text("Paid via CGST", modifier = Modifier.width(85.dp), fontWeight = FontWeight.Bold, fontSize = 10.sp, textAlign = TextAlign.End, color = tokens.success)
                                Text("Paid via SGST", modifier = Modifier.width(85.dp), fontWeight = FontWeight.Bold, fontSize = 10.sp, textAlign = TextAlign.End, color = tokens.success)
                                Text("Cash Payable", modifier = Modifier.width(90.dp), fontWeight = FontWeight.Bold, fontSize = 10.sp, textAlign = TextAlign.End, color = tokens.danger)
                            }

                            // Row 1: IGST
                            MatrixTableRow(
                                head = "Integrated (IGST)",
                                gross = matrix.grossIgstPaise,
                                fromIgst = matrix.igstUsedForIgstPaise,
                                fromCgst = matrix.cgstUsedForIgstPaise,
                                fromSgst = matrix.sgstUsedForIgstPaise,
                                cash = matrix.cashIgstPaise,
                                tokens = tokens
                            )

                            // Row 2: CGST
                            MatrixTableRow(
                                head = "Central (CGST)",
                                gross = matrix.grossCgstPaise,
                                fromIgst = matrix.igstUsedForCgstPaise,
                                fromCgst = matrix.cgstUsedForCgstPaise,
                                fromSgst = null, // Blocked by Rule 88A
                                cash = matrix.cashCgstPaise,
                                tokens = tokens
                            )

                            // Row 3: SGST
                            MatrixTableRow(
                                head = "State (SGST)",
                                gross = matrix.grossSgstPaise,
                                fromIgst = matrix.igstUsedForSgstPaise,
                                fromCgst = null, // Blocked by Rule 88A
                                fromSgst = matrix.sgstUsedForSgstPaise,
                                cash = matrix.cashSgstPaise,
                                tokens = tokens
                            )

                            HorizontalDivider(color = tokens.cardBorder)

                            // Total Summary Row
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(tokens.secondaryBg.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                                    .padding(vertical = 6.dp, horizontal = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Total", modifier = Modifier.width(90.dp), fontWeight = FontWeight.Black, fontSize = 10.sp, color = tokens.text)
                                Text(FinancialEngine.formatPaise(matrix.totalGrossLiabilityPaise), modifier = Modifier.width(85.dp), fontWeight = FontWeight.Black, fontSize = 10.sp, textAlign = TextAlign.End, color = tokens.text)
                                Text(FinancialEngine.formatPaise(matrix.totalIgstUtilizedPaise), modifier = Modifier.width(85.dp), fontWeight = FontWeight.Black, fontSize = 10.sp, textAlign = TextAlign.End, color = tokens.success)
                                Text(FinancialEngine.formatPaise(matrix.totalCgstUtilizedPaise), modifier = Modifier.width(85.dp), fontWeight = FontWeight.Black, fontSize = 10.sp, textAlign = TextAlign.End, color = tokens.success)
                                Text(FinancialEngine.formatPaise(matrix.totalSgstUtilizedPaise), modifier = Modifier.width(85.dp), fontWeight = FontWeight.Black, fontSize = 10.sp, textAlign = TextAlign.End, color = tokens.success)
                                Text(FinancialEngine.formatPaise(matrix.totalCashPayablePaise), modifier = Modifier.width(90.dp), fontWeight = FontWeight.Black, fontSize = 10.sp, textAlign = TextAlign.End, color = tokens.danger)
                            }
                        }
                    }

                    // Rule 88A Statutory Principles Note
                    Surface(
                        color = tokens.secondaryBg,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(13.dp), tint = tokens.accent)
                                Text("Statutory Rule 88A Principles Applied:", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = tokens.accent)
                            }
                            Text("1. IGST ITC is completely exhausted against IGST liability before CGST or SGST set-off.", fontSize = 10.sp, color = tokens.textMuted)
                            Text("2. Remaining IGST credit is balanced between CGST and SGST to legally minimize cash outgo.", fontSize = 10.sp, color = tokens.textMuted)
                            Text("3. Section 49B: CGST credit cannot be cross-utilized against SGST and vice versa.", fontSize = 10.sp, color = tokens.textMuted)
                        }
                    }

                    // Carry Forward ITC Card
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("ITC Balance Carried Forward", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = tokens.text)
                            Text(
                                "CGST: ${FinancialEngine.formatPaise(matrix.carriedForwardCgstPaise)} | SGST: ${FinancialEngine.formatPaise(matrix.carriedForwardSgstPaise)}",
                                fontSize = 10.sp,
                                color = tokens.textMuted
                            )
                        }
                        Text(
                            text = FinancialEngine.formatPaise(matrix.totalCarriedForwardItcPaise),
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = tokens.success
                        )
                    }

                    HorizontalDivider(color = tokens.cardBorder)

                    // Final Net Cash Obligation
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Net Cash Payable (Challan / ECL)", fontWeight = FontWeight.Black, fontSize = 13.sp, color = tokens.text)
                            Text("Payable through Electronic Cash Ledger", fontSize = 10.sp, color = tokens.textMuted)
                        }
                        Text(
                            text = FinancialEngine.formatPaise(matrix.totalCashPayablePaise),
                            fontWeight = FontWeight.Black,
                            fontSize = 15.sp,
                            color = if (matrix.totalCashPayablePaise > 0) tokens.danger else tokens.success
                        )
                    }
                }
            }
        }

@Composable
private fun MatrixTableRow(
    head: String,
    gross: Long,
    fromIgst: Long,
    fromCgst: Long?,
    fromSgst: Long?,
    cash: Long,
    tokens: com.example.ui.theme.PabaruTokens
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp, horizontal = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(head, modifier = Modifier.width(90.dp), fontSize = 10.sp, color = tokens.text, fontWeight = FontWeight.Medium)
        Text(FinancialEngine.formatPaise(gross), modifier = Modifier.width(85.dp), fontSize = 10.sp, textAlign = TextAlign.End, color = tokens.text)
        Text(FinancialEngine.formatPaise(fromIgst), modifier = Modifier.width(85.dp), fontSize = 10.sp, textAlign = TextAlign.End, color = tokens.success)

        if (fromCgst != null) {
            Text(FinancialEngine.formatPaise(fromCgst), modifier = Modifier.width(85.dp), fontSize = 10.sp, textAlign = TextAlign.End, color = tokens.success)
        } else {
            Text("N/A", modifier = Modifier.width(85.dp), fontSize = 10.sp, textAlign = TextAlign.End, color = tokens.textMuted)
        }

        if (fromSgst != null) {
            Text(FinancialEngine.formatPaise(fromSgst), modifier = Modifier.width(85.dp), fontSize = 10.sp, textAlign = TextAlign.End, color = tokens.success)
        } else {
            Text("N/A", modifier = Modifier.width(85.dp), fontSize = 10.sp, textAlign = TextAlign.End, color = tokens.textMuted)
        }

        Text(
            FinancialEngine.formatPaise(cash),
            modifier = Modifier.width(90.dp),
            fontSize = 10.sp,
            textAlign = TextAlign.End,
            fontWeight = FontWeight.Bold,
            color = if (cash > 0) tokens.danger else tokens.textMuted
        )
    }
}

// =====================================================================
// 3. Composition Mode: Form GST CMP-08 Quarterly Statement
// =====================================================================
@Composable
private fun Cmp08ReturnView(
    uiState: PabaruUiState,
    cmp08Data: CmaAndGstEngine.StatutoryCmp08Data,
    periodFilter: CmaAndGstEngine.GstPeriodFilter,
    periodCode: String,
    tokens: com.example.ui.theme.PabaruTokens
) {
    val context = LocalContext.current
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        // Top Header & JSON Export
        Card(
                colors = CardDefaults.cardColors(containerColor = tokens.card),
                border = BorderStroke(1.dp, tokens.cardBorder),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Form GST CMP-08", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = tokens.text)
                            Text("Special Quarterly Statement for Composition Dealers", fontSize = 10.sp, color = tokens.textMuted)
                        }

                        Button(
                            onClick = {
                                val jsonStr = GstPortalJsonEngine.generateCmp08Json(uiState.profile, cmp08Data, periodFilter)
                                val filename = "CMP08_${uiState.profile.gstin.trim().ifBlank { "UNREG" }}_$periodCode.json"
                                GstPortalJsonEngine.shareOrSaveJson(context, jsonStr, filename, "CMP-08")
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = tokens.primary),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("export_cmp08_json_button")
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(14.dp), tint = tokens.primaryText)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Export CMP-08 JSON", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = tokens.primaryText)
                        }
                    }

                    HorizontalDivider(color = tokens.cardBorder)

                    // Scheme Overview
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        StatSmallItem("Quarter Turnover", FinancialEngine.formatPaise(cmp08Data.turnoverPaise), tokens.text, tokens)
                        StatSmallItem("Composition Rate", "${cmp08Data.compositionRatePercent}%", tokens.accent, tokens)
                        StatSmallItem("Net Cash Payable", FinancialEngine.formatPaise(cmp08Data.netCashPayablePaise), tokens.danger, tokens)
                    }
                }
            }
        }

        // Table 1: Outward Supplies Turnover
        Card(
            colors = CardDefaults.cardColors(containerColor = tokens.card),
            border = BorderStroke(1.dp, tokens.cardBorder),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("1. Outward Supplies (including exempt)", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = tokens.accent)

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Gross Turnover (${cmp08Data.transactionsCount} sales)", fontSize = 11.sp, color = tokens.text)
                    Text(FinancialEngine.formatPaise(cmp08Data.turnoverPaise), fontWeight = FontWeight.Bold, color = tokens.text)
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Central Tax (${cmp08Data.compositionRatePercent / 2}%)", fontSize = 11.sp, color = tokens.textMuted)
                    Text(FinancialEngine.formatPaise(cmp08Data.centralTaxPaise), color = tokens.text)
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("State/UT Tax (${cmp08Data.compositionRatePercent / 2}%)", fontSize = 11.sp, color = tokens.textMuted)
                    Text(FinancialEngine.formatPaise(cmp08Data.stateTaxPaise), color = tokens.text)
                }

                HorizontalDivider(color = tokens.cardBorder)

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Sub-total Composition Tax", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = tokens.text)
                    Text(FinancialEngine.formatPaise(cmp08Data.totalCompositionTaxPaise), fontWeight = FontWeight.Bold, color = tokens.text)
                }
            }
        }

        // Table 2: Inward Supplies liable to Reverse Charge (RCM)
        Card(
            colors = CardDefaults.cardColors(containerColor = tokens.card),
            border = BorderStroke(1.dp, tokens.cardBorder),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("2. Inward Supplies Attracting Reverse Charge (RCM)", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = tokens.accent)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Inward RCM Supplies", fontSize = 11.sp, color = tokens.text)
                    Text(FinancialEngine.formatPaise(cmp08Data.rcmTurnoverPaise), color = tokens.text)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("RCM Tax Payable", fontSize = 11.sp, color = tokens.text)
                    Text(FinancialEngine.formatPaise(cmp08Data.rcmTotalTaxPaise), color = tokens.text)
                }
            }
        }

        // Table 3 & 4: Total Tax & Statutory Notice
        Card(
            colors = CardDefaults.cardColors(containerColor = tokens.card),
            border = BorderStroke(1.dp, tokens.cardBorder),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("3. Total Tax Payable in Cash", fontWeight = FontWeight.Black, fontSize = 13.sp, color = tokens.text)
                        Text("Quarterly obligation via Electronic Cash Ledger", fontSize = 10.sp, color = tokens.textMuted)
                    }
                    Text(
                        text = FinancialEngine.formatPaise(cmp08Data.netCashPayablePaise),
                        fontWeight = FontWeight.Black,
                        fontSize = 15.sp,
                        color = if (cmp08Data.netCashPayablePaise > 0) tokens.danger else tokens.success
                    )
                }

                HorizontalDivider(color = tokens.cardBorder)

                Surface(
                    color = tokens.secondaryBg,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(14.dp), tint = tokens.accent)
                        Text(
                            text = "Under Section 10 of the CGST Act, composition dealers cannot issue tax invoices, cannot collect GST from customers, and cannot claim Input Tax Credit (ITC). All taxes must be settled directly in cash.",
                            fontSize = 10.sp,
                            color = tokens.textMuted
                        )
                    }
                }
            }
        }
    }

@Composable
private fun StatSmallItem(title: String, value: String, color: Color, tokens: com.example.ui.theme.PabaruTokens) {
    Column {
        Text(title, fontSize = 10.sp, color = tokens.textMuted)
        Text(value, fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = color)
    }
}

// =====================================================================
// 4. Excel & Tax Software Pre-fill Dialog
// =====================================================================
@Composable
fun Gstr1ExcelImportDialog(
    uiState: PabaruUiState,
    tokens: com.example.ui.theme.PabaruTokens,
    onDismiss: () -> Unit,
    onApplyPrefill: (List<Gstr1ExcelImportEngine.ImportedGstr1Invoice>) -> Unit,
    onCommitDirectToLedger: (List<Gstr1ExcelImportEngine.ImportedGstr1Invoice>) -> Unit,
    onFileLoaded: (String) -> Unit = {}
) {
    val context = LocalContext.current
    var selectedFormat by remember { mutableStateOf(Gstr1ExcelImportEngine.TaxSoftwareFormat.AutoDetect) }
    var rawData by remember { mutableStateOf<Gstr1ExcelImportEngine.SpreadsheetRawData?>(null) }
    var customMapping by remember { mutableStateOf<Gstr1ExcelImportEngine.ColumnMapping?>(null) }
    var fileName by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showMappingEditor by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()
    var isParsingSpreadsheet by remember { mutableStateOf(false) }

    val filePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null && !isParsingSpreadsheet) {
            isParsingSpreadsheet = true
            coroutineScope.launch {
                try {
                    val result = withContext(Dispatchers.IO) {
                        val stream = context.contentResolver.openInputStream(uri)
                            ?: throw IllegalStateException("Unable to open selected file.")
                        stream.use { inputStream ->
                            val name = uri.lastPathSegment ?: "Spreadsheet.xlsx"
                            val parsed = Gstr1ExcelImportEngine.parseSpreadsheetStream(inputStream, name)
                            val (detected, mapping) = Gstr1ExcelImportEngine.detectFormat(parsed.headers)
                            Triple(name, parsed, Pair(detected, mapping))
                        }
                    }
                    val (name, parsed, formatInfo) = result
                    rawData = parsed
                    fileName = name
                    if (selectedFormat == Gstr1ExcelImportEngine.TaxSoftwareFormat.AutoDetect) {
                        selectedFormat = formatInfo.first
                    }
                    customMapping = formatInfo.second
                    errorMessage = null
                } catch (e: Exception) {
                    errorMessage = "Failed to parse spreadsheet: ${e.message}"
                } finally {
                    isParsingSpreadsheet = false
                }
            }
        }
    }

    fun loadPreset(format: Gstr1ExcelImportEngine.TaxSoftwareFormat) {
        val sample = Gstr1ExcelImportEngine.getSampleData(format)
        rawData = sample
        fileName = "${format.displayName} Sample"
        selectedFormat = format
        val (_, mapping) = Gstr1ExcelImportEngine.detectFormat(sample.headers)
        customMapping = mapping
        errorMessage = null
    }

    val importResult = remember(rawData, selectedFormat, customMapping) {
        val data = rawData
        if (data != null) {
            Gstr1ExcelImportEngine.parseInvoices(
                rawData = data,
                format = selectedFormat,
                customMapping = customMapping,
                homeStateCode = uiState.profile.homeStateCode.ifBlank { "27" }
            )
        } else null
    }

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 700.dp),
            shape = RoundedCornerShape(16.dp),
            color = tokens.card,
            border = BorderStroke(1.dp, tokens.cardBorder)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Dialog Title Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(
                            imageVector = Icons.Default.TableChart,
                            contentDescription = null,
                            tint = tokens.primary,
                            modifier = Modifier.size(22.dp)
                        )
                        Column {
                            Text(
                                text = "Pre-fill GSTR-1 from Excel",
                                fontWeight = FontWeight.Black,
                                fontSize = 15.sp,
                                color = tokens.text
                            )
                            Text(
                                text = "Govt Utility, ClearTax, Zoho Books, Tally, Busy",
                                fontSize = 10.sp,
                                color = tokens.textMuted
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(48.dp).testTag("close_import_dialog_button")
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = tokens.textMuted)
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = tokens.cardBorder)

                // Body content
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // 1. File Upload & Quick Presets Section
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = tokens.secondaryBg),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, tokens.cardBorder)
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "1. Select Spreadsheet File",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = tokens.text
                                    )

                                    Button(
                                        onClick = { filePickerLauncher.launch("*/*") },
                                        colors = ButtonDefaults.buttonColors(containerColor = tokens.primary),
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                        modifier = Modifier.testTag("select_file_button")
                                    ) {
                                        Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(14.dp), tint = tokens.primaryText)
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Browse File (.xlsx / .csv)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = tokens.primaryText)
                                    }
                                }

                                if (fileName != null) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = tokens.success, modifier = Modifier.size(14.dp))
                                        Text(
                                            text = "Loaded: $fileName (${rawData?.rows?.size ?: 0} rows parsed)",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = tokens.success
                                        )
                                    }
                                }

                                Text(
                                    text = "Or load pre-configured software formats:",
                                    fontSize = 10.sp,
                                    color = tokens.textMuted
                                )

                                // Preset software chips
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    listOf(
                                        Gstr1ExcelImportEngine.TaxSoftwareFormat.GovtOfflineTool to "Govt Utility",
                                        Gstr1ExcelImportEngine.TaxSoftwareFormat.ClearTax to "ClearTax",
                                        Gstr1ExcelImportEngine.TaxSoftwareFormat.ZohoBooks to "Zoho Books",
                                        Gstr1ExcelImportEngine.TaxSoftwareFormat.TallyPrime to "TallyPrime",
                                        Gstr1ExcelImportEngine.TaxSoftwareFormat.BusyAccounting to "Busy"
                                    ).forEach { (fmt, label) ->
                                        val isSel = selectedFormat == fmt
                                        FilterChip(
                                            selected = isSel,
                                            onClick = { loadPreset(fmt) },
                                            label = { Text(label, fontSize = 10.sp) },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = tokens.accent,
                                                selectedLabelColor = tokens.primaryText,
                                                containerColor = tokens.card,
                                                labelColor = tokens.text
                                            ),
                                            shape = RoundedCornerShape(6.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Error display if any
                    if (errorMessage != null) {
                        item {
                            Surface(
                                color = tokens.dangerContainer,
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, tokens.danger)
                            ) {
                                Row(modifier = Modifier.padding(10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Icon(Icons.Default.Error, contentDescription = null, tint = tokens.danger, modifier = Modifier.size(16.dp))
                                    Text(errorMessage ?: "", fontSize = 11.sp, color = tokens.onDangerContainer)
                                }
                            }
                        }
                    }

                    // 2. Format & Mapping Card
                    if (rawData != null) {
                        item {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = tokens.secondaryBg),
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, tokens.cardBorder)
                            ) {
                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text("2. Tax Software Column Mapping", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = tokens.text)
                                            Text("Format: ${selectedFormat.displayName}", fontSize = 10.sp, color = tokens.accent)
                                        }

                                        TextButton(
                                            onClick = { showMappingEditor = !showMappingEditor },
                                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = if (showMappingEditor) "Hide Columns ▲" else "Customize Mapping ▼",
                                                fontSize = 11.sp,
                                                color = tokens.primary
                                            )
                                        }
                                    }

                                    // Mapping editor dropdowns if expanded
                                    AnimatedVisibility(visible = showMappingEditor) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(top = 4.dp),
                                            verticalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            val headers = rawData?.headers ?: emptyList()
                                            val curMapping = customMapping ?: Gstr1ExcelImportEngine.ColumnMapping()

                                            ColumnSelectorRow("Recipient GSTIN", curMapping.gstinCol, headers, tokens) { newIdx ->
                                                customMapping = curMapping.copy(gstinCol = newIdx)
                                            }
                                            ColumnSelectorRow("Party / Buyer Name", curMapping.nameCol, headers, tokens) { newIdx ->
                                                customMapping = curMapping.copy(nameCol = newIdx)
                                            }
                                            ColumnSelectorRow("Invoice Number", curMapping.invoiceNoCol, headers, tokens) { newIdx ->
                                                customMapping = curMapping.copy(invoiceNoCol = newIdx)
                                            }
                                            ColumnSelectorRow("Invoice Date", curMapping.dateCol, headers, tokens) { newIdx ->
                                                customMapping = curMapping.copy(dateCol = newIdx)
                                            }
                                            ColumnSelectorRow("Place of Supply (POS)", curMapping.posCol, headers, tokens) { newIdx ->
                                                customMapping = curMapping.copy(posCol = newIdx)
                                            }
                                            ColumnSelectorRow("Taxable Amount", curMapping.taxableCol, headers, tokens) { newIdx ->
                                                customMapping = curMapping.copy(taxableCol = newIdx)
                                            }
                                            ColumnSelectorRow("GST Rate %", curMapping.rateCol, headers, tokens) { newIdx ->
                                                customMapping = curMapping.copy(rateCol = newIdx)
                                            }
                                            ColumnSelectorRow("CGST Amount", curMapping.cgstCol, headers, tokens) { newIdx ->
                                                customMapping = curMapping.copy(cgstCol = newIdx)
                                            }
                                            ColumnSelectorRow("SGST Amount", curMapping.sgstCol, headers, tokens) { newIdx ->
                                                customMapping = curMapping.copy(sgstCol = newIdx)
                                            }
                                            ColumnSelectorRow("IGST Amount", curMapping.igstCol, headers, tokens) { newIdx ->
                                                customMapping = curMapping.copy(igstCol = newIdx)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 3. Validation & Metrics Summary
                    if (importResult != null) {
                        item {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = tokens.secondaryBg),
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, tokens.cardBorder)
                            ) {
                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("3. Summary of Extracted Invoices", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = tokens.text)

                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        StatSmallItem("Invoices", "${importResult.invoices.size} bills", tokens.text, tokens)
                                        StatSmallItem("B2B Valid", "${importResult.b2bCount} bills", tokens.success, tokens)
                                        StatSmallItem("B2C Sales", "${importResult.b2cCount} bills", tokens.text, tokens)
                                        StatSmallItem("Turnover", FinancialEngine.formatPaise(importResult.totalTaxablePaise), tokens.text, tokens)
                                    }

                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        StatSmallItem("CGST", FinancialEngine.formatPaise(importResult.totalCgstPaise), tokens.textMuted, tokens)
                                        StatSmallItem("SGST", FinancialEngine.formatPaise(importResult.totalSgstPaise), tokens.textMuted, tokens)
                                        StatSmallItem("IGST", FinancialEngine.formatPaise(importResult.totalIgstPaise), tokens.textMuted, tokens)
                                        StatSmallItem("Total Tax", FinancialEngine.formatPaise(importResult.totalTaxPaise), tokens.accent, tokens)
                                    }
                                }
                            }
                        }

                        // 4. Parsed Invoices Preview List
                        item {
                            Text(
                                text = "Preview Extracted Invoices (${importResult.invoices.size})",
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                color = tokens.text
                            )
                        }

                        items(importResult.invoices.take(6)) { inv ->
                            Surface(
                                color = tokens.card,
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, tokens.cardBorder),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Text(
                                                text = inv.invoiceNumber,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 11.sp,
                                                color = tokens.text
                                            )
                                            Surface(
                                                color = if (inv.isB2b) tokens.successContainer else tokens.secondaryBg,
                                                shape = RoundedCornerShape(4.dp)
                                            ) {
                                                Text(
                                                    text = if (inv.isB2b) "B2B" else "B2C",
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (inv.isB2b) tokens.onSuccessContainer else tokens.textMuted,
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                )
                                            }
                                        }

                                        Text(
                                            text = FinancialEngine.formatPaise(inv.totalPaise),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp,
                                            color = tokens.text
                                        )
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = inv.partyName,
                                            fontSize = 10.sp,
                                            color = tokens.textMuted
                                        )
                                        Text(
                                            text = "Taxable: ${FinancialEngine.formatPaise(inv.taxablePaise)} (${GstSlabs.label(inv.gstRateBps)})",
                                            fontSize = 10.sp,
                                            color = tokens.textMuted
                                        )
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "GSTIN: ${inv.gstin.ifBlank { "Unregistered" }} • POS: ${inv.posStateCode}",
                                            fontSize = 9.sp,
                                            fontFamily = FontFamily.Monospace,
                                            color = tokens.textMuted
                                        )
                                        Text(
                                            text = if (inv.isInterstate) "IGST: ${FinancialEngine.formatPaise(inv.igstPaise)}" else "CGST+SGST: ${FinancialEngine.formatPaise(inv.cgstPaise + inv.sgstPaise)}",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = tokens.accent
                                        )
                                    }

                                    if (inv.validationWarnings.isNotEmpty()) {
                                        Text(
                                            text = inv.validationWarnings.first(),
                                            fontSize = 9.sp,
                                            color = tokens.warning
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = tokens.cardBorder)

                // Dialog Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .weight(0.7f)
                            .heightIn(min = 48.dp)
                            .testTag("cancel_import_button")
                    ) {
                        Text("Cancel", fontSize = 11.sp, color = tokens.text)
                    }

                    Button(
                        onClick = {
                            if (importResult != null) {
                                onFileLoaded(fileName ?: "Spreadsheet")
                                onApplyPrefill(importResult.invoices)
                            }
                        },
                        enabled = importResult != null && importResult.invoices.isNotEmpty(),
                        colors = ButtonDefaults.buttonColors(containerColor = tokens.primary),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .weight(1.2f)
                            .heightIn(min = 48.dp)
                            .testTag("apply_prefill_button")
                    ) {
                        Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(14.dp), tint = tokens.primaryText)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Pre-fill GSTR-1", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = tokens.primaryText)
                    }

                    Button(
                        onClick = {
                            if (importResult != null) {
                                onCommitDirectToLedger(importResult.invoices)
                            }
                        },
                        enabled = importResult != null && importResult.invoices.isNotEmpty(),
                        colors = ButtonDefaults.buttonColors(containerColor = tokens.success),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .weight(1.3f)
                            .heightIn(min = 48.dp)
                            .testTag("commit_ledger_button")
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(14.dp), tint = tokens.primaryText)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Save to Ledger", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = tokens.primaryText)
                    }
                }
            }
        }
    }
}

@Composable
private fun ColumnSelectorRow(
    label: String,
    selectedIndex: Int,
    headers: List<String>,
    tokens: com.example.ui.theme.PabaruTokens,
    onSelectIndex: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val displayValue = if (selectedIndex in headers.indices) {
        "Col ${selectedIndex + 1}: ${headers[selectedIndex]}"
    } else "(Unmapped)"

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = tokens.text)

        Box {
            Surface(
                color = tokens.card,
                shape = RoundedCornerShape(6.dp),
                border = BorderStroke(1.dp, tokens.cardBorder),
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .clickable { expanded = true }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = displayValue.take(22),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = tokens.text
                    )
                    Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(16.dp), tint = tokens.textMuted)
                }
            }

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.background(tokens.card)
            ) {
                DropdownMenuItem(
                    text = { Text("(Unmapped)", fontSize = 11.sp, color = tokens.text) },
                    onClick = {
                        onSelectIndex(-1)
                        expanded = false
                    }
                )
                headers.forEachIndexed { idx, h ->
                    DropdownMenuItem(
                        text = { Text("Col ${idx + 1}: $h", fontSize = 11.sp, color = tokens.text) },
                        onClick = {
                            onSelectIndex(idx)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun PeriodGridButton(
    label: String,
    isSelected: Boolean,
    tokens: com.example.ui.theme.PabaruTokens,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = if (isSelected) tokens.accentTint else tokens.secondaryBg,
        border = BorderStroke(
            1.dp,
            if (isSelected) tokens.accent else tokens.cardBorder
        ),
        modifier = modifier
            .heightIn(min = 48.dp)
            .testTag("period_btn_${label.lowercase().replace(" ", "_")}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Selected",
                    tint = tokens.accent,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
            }
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) tokens.text else tokens.textMuted,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SegmentButton(
    text: String,
    isSelected: Boolean,
    tokens: com.example.ui.theme.PabaruTokens,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = if (isSelected) tokens.card else Color.Transparent,
        shadowElevation = if (isSelected) 3.dp else 0.dp,
        border = if (isSelected) BorderStroke(1.dp, tokens.cardBorder) else null,
        modifier = modifier
            .fillMaxHeight()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                fontSize = 12.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) tokens.accent else tokens.textMuted,
                textAlign = TextAlign.Center,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun MetricCard(
    label: String,
    value: String,
    tokens: com.example.ui.theme.PabaruTokens,
    modifier: Modifier = Modifier,
    valueColor: Color = tokens.text
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = tokens.secondaryBg,
        border = BorderStroke(1.dp, tokens.cardBorder),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = label,
                fontSize = 12.sp,
                color = tokens.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = value,
                fontSize = 20.sp,
                fontWeight = FontWeight.Medium,
                color = valueColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun TaxBreakdownRow(
    label: String,
    amountPaise: Long,
    tokens: com.example.ui.theme.PabaruTokens
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            color = tokens.textMuted,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = if (amountPaise > 0) FinancialEngine.formatPaise(amountPaise) else "₹0.00",
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = tokens.text,
            textAlign = TextAlign.End
        )
    }
}

@Composable
private fun StepContainerCard(
    stepNumber: Int,
    title: String,
    subtitle: String,
    isExpanded: Boolean,
    isCompleted: Boolean,
    tokens: com.example.ui.theme.PabaruTokens,
    onHeaderClick: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = tokens.card),
        border = BorderStroke(
            1.dp,
            if (isExpanded) tokens.primary.copy(alpha = 0.6f) else tokens.cardBorder
        ),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .testTag("step_container_card_$stepNumber")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clickable { onHeaderClick() }
                    .testTag("step_header_$stepNumber")
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = when {
                            isCompleted -> tokens.success
                            isExpanded -> tokens.primary
                            else -> tokens.secondaryBg
                        },
                        border = if (!isCompleted && !isExpanded) BorderStroke(1.dp, tokens.cardBorder) else null,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            if (isCompleted) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Completed",
                                    tint = tokens.primaryText,
                                    modifier = Modifier.size(16.dp)
                                )
                            } else {
                                Text(
                                    text = "$stepNumber",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isExpanded) tokens.primaryText else tokens.textMuted
                                )
                            }
                        }
                    }

                    Column {
                        Text(
                            text = title,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = tokens.text
                        )
                        if (!isExpanded && subtitle.isNotBlank()) {
                            Text(
                                text = subtitle,
                                fontSize = 12.sp,
                                color = tokens.textMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                IconButton(
                    onClick = onHeaderClick,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (isExpanded) "Collapse" else "Expand",
                        tint = tokens.textMuted
                    )
                }
            }

            if (isExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    HorizontalDivider(color = tokens.cardBorder.copy(alpha = 0.5f))
                    content()
                }
            }
        }
    }
}

@Composable
private fun LoadingSkeletonCard(
    tokens: com.example.ui.theme.PabaruTokens
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = tokens.card),
        border = BorderStroke(1.dp, tokens.cardBorder),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(110.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.5f)
                    .height(16.dp)
                    .background(tokens.secondaryBg, RoundedCornerShape(4.dp))
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.8f)
                    .height(14.dp)
                    .background(tokens.secondaryBg.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.35f)
                    .height(20.dp)
                    .background(tokens.accent.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
            )
        }
    }
}

