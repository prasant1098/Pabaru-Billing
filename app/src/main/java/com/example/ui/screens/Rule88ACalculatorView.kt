package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.engine.CmaAndGstEngine
import com.example.engine.FinancialEngine
import com.example.ui.theme.PabaruTheme
import com.example.ui.theme.danger
import com.example.ui.theme.onSuccessContainer
import com.example.ui.theme.success
import com.example.ui.theme.successContainer
import java.util.Locale

private fun formatInitialRupees(paise: Long): String {
    if (paise <= 0L) return "0"
    return if (paise % 100L == 0L) (paise / 100L).toString()
    else String.format(Locale.ENGLISH, "%.2f", paise / 100.0)
}

@Composable
fun Rule88ACalculatorView(
    initialGrossIgst: Long = 0L,
    initialGrossCgst: Long = 0L,
    initialGrossSgst: Long = 0L,
    initialItcIgst: Long = 0L,
    initialItcCgst: Long = 0L,
    initialItcSgst: Long = 0L,
    onApplyToGstr3b: ((CmaAndGstEngine.Rule88AStrategy, Double?) -> Unit)? = null
) {
    val context = LocalContext.current
    val tokens = PabaruTheme.tokens

    // State for inputs (in rupees for easy user editing, preserving exact paise)
    var grossIgstRupees by remember { mutableStateOf(formatInitialRupees(initialGrossIgst)) }
    var grossCgstRupees by remember { mutableStateOf(formatInitialRupees(initialGrossCgst)) }
    var grossSgstRupees by remember { mutableStateOf(formatInitialRupees(initialGrossSgst)) }

    var itcIgstRupees by remember { mutableStateOf(formatInitialRupees(initialItcIgst)) }
    var itcCgstRupees by remember { mutableStateOf(formatInitialRupees(initialItcCgst)) }
    var itcSgstRupees by remember { mutableStateOf(formatInitialRupees(initialItcSgst)) }

    var selectedStrategy by remember { mutableStateOf(CmaAndGstEngine.Rule88AStrategy.OPTIMAL) }
    var customCgstPercent by remember { mutableStateOf(50f) } // 0 to 100% of unutilized IGST to CGST
    var showStepsExpanded by remember { mutableStateOf(true) }

    // Helper to safely parse rupees to paise without float precision loss
    fun toPaise(rupeesStr: String): Long = FinancialEngine.parsePaiseFromRupees(rupeesStr)

    val grossIgstPaise = remember(grossIgstRupees) { toPaise(grossIgstRupees) }
    val grossCgstPaise = remember(grossCgstRupees) { toPaise(grossCgstRupees) }
    val grossSgstPaise = remember(grossSgstRupees) { toPaise(grossSgstRupees) }

    val itcIgstPaise = remember(itcIgstRupees) { toPaise(itcIgstRupees) }
    val itcCgstPaise = remember(itcCgstRupees) { toPaise(itcCgstRupees) }
    val itcSgstPaise = remember(itcSgstRupees) { toPaise(itcSgstRupees) }

    // Strategy comparison and calculation
    val comparison = remember(
        grossIgstPaise, grossCgstPaise, grossSgstPaise,
        itcIgstPaise, itcCgstPaise, itcSgstPaise
    ) {
        CmaAndGstEngine.compareAllRule88AStrategies(
            grossIgst = grossIgstPaise,
            grossCgst = grossCgstPaise,
            grossSgst = grossSgstPaise,
            itcIgst = itcIgstPaise,
            itcCgst = itcCgstPaise,
            itcSgst = itcSgstPaise
        )
    }

    val activeMatrix = remember(
        grossIgstPaise, grossCgstPaise, grossSgstPaise,
        itcIgstPaise, itcCgstPaise, itcSgstPaise,
        selectedStrategy, customCgstPercent
    ) {
        if (selectedStrategy == CmaAndGstEngine.Rule88AStrategy.CUSTOM) {
            CmaAndGstEngine.computeRule88ASetOff(
                grossIgst = grossIgstPaise,
                grossCgst = grossCgstPaise,
                grossSgst = grossSgstPaise,
                itcIgst = itcIgstPaise,
                itcCgst = itcCgstPaise,
                itcSgst = itcSgstPaise,
                strategy = CmaAndGstEngine.Rule88AStrategy.CUSTOM,
                customIgstForCgstPercent = customCgstPercent.toDouble()
            )
        } else {
            when (selectedStrategy) {
                CmaAndGstEngine.Rule88AStrategy.OPTIMAL -> comparison.optimal
                CmaAndGstEngine.Rule88AStrategy.EQUAL_SPLIT -> comparison.equalSplit
                CmaAndGstEngine.Rule88AStrategy.CGST_FIRST -> comparison.cgstFirst
                CmaAndGstEngine.Rule88AStrategy.SGST_FIRST -> comparison.sgstFirst
                else -> comparison.optimal
            }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("rule_88a_calculator_scroll_view"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        // -------------------------------------------------------------
        // Header & Statutory Reference Card
        // -------------------------------------------------------------
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = tokens.card),
                border = BorderStroke(1.dp, tokens.cardBorder),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Calculate,
                                contentDescription = null,
                                tint = tokens.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "Rule 88A ITC Set-off Calculator",
                                fontWeight = FontWeight.Black,
                                fontSize = 15.sp,
                                color = tokens.text
                            )
                        }

                        Surface(
                            color = tokens.primary.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "Circular 98/17/2019",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = tokens.primary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Text(
                        text = "Statutory optimization engine that helps taxpayers minimize electronic cash ledger liability and prevent credit blockage by dynamically utilizing input tax credit across Central and State tax heads.",
                        fontSize = 11.sp,
                        color = tokens.textMuted
                    )
                }
            }
        }

        // -------------------------------------------------------------
        // Preset Scenarios
        // -------------------------------------------------------------
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Quick Presets & Testing Scenarios",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = tokens.textMuted
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = false,
                        onClick = {
                            grossIgstRupees = formatInitialRupees(initialGrossIgst)
                            grossCgstRupees = formatInitialRupees(initialGrossCgst)
                            grossSgstRupees = formatInitialRupees(initialGrossSgst)
                            itcIgstRupees = formatInitialRupees(initialItcIgst)
                            itcCgstRupees = formatInitialRupees(initialItcCgst)
                            itcSgstRupees = formatInitialRupees(initialItcSgst)
                        },
                        label = { Text("Active GSTR-3B Data", fontSize = 10.5.sp) },
                        leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(12.dp)) },
                        modifier = Modifier.heightIn(min = 48.dp)
                    )

                    FilterChip(
                        selected = false,
                        onClick = {
                            // Asymmetric case: Output CGST 100k, SGST 100k; ITC IGST 60k, CGST 90k, SGST 10k
                            grossIgstRupees = "0"
                            grossCgstRupees = "100000"
                            grossSgstRupees = "100000"
                            itcIgstRupees = "60000"
                            itcCgstRupees = "90000"
                            itcSgstRupees = "10000"
                            selectedStrategy = CmaAndGstEngine.Rule88AStrategy.OPTIMAL
                        },
                        label = { Text("Asymmetric ITC Deficit (Saves ₹20k)", fontSize = 10.5.sp) },
                        modifier = Modifier.heightIn(min = 48.dp)
                    )

                    FilterChip(
                        selected = false,
                        onClick = {
                            // High Interstate ITC surplus
                            grossIgstRupees = "0"
                            grossCgstRupees = "40000"
                            grossSgstRupees = "40000"
                            itcIgstRupees = "120000"
                            itcCgstRupees = "15000"
                            itcSgstRupees = "15000"
                            selectedStrategy = CmaAndGstEngine.Rule88AStrategy.OPTIMAL
                        },
                        label = { Text("Interstate Surplus (Zero Cash)", fontSize = 10.5.sp) },
                        modifier = Modifier.heightIn(min = 48.dp)
                    )

                    FilterChip(
                        selected = false,
                        onClick = {
                            grossIgstRupees = "0"
                            grossCgstRupees = "0"
                            grossSgstRupees = "0"
                            itcIgstRupees = "0"
                            itcCgstRupees = "0"
                            itcSgstRupees = "0"
                        },
                        label = { Text("Reset to Zero", fontSize = 10.5.sp) },
                        modifier = Modifier.heightIn(min = 48.dp)
                    )
                }
            }
        }

        // -------------------------------------------------------------
        // Tax Liabilities & Available ITC Inputs
        // -------------------------------------------------------------
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = tokens.card),
                border = BorderStroke(1.dp, tokens.cardBorder),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // SECTION 1: GROSS OUTPUT TAX LIABILITY
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "1. Gross Output Tax Liability",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = tokens.text
                        )
                        Text(
                            text = "Total: ${FinancialEngine.formatPaise(grossIgstPaise + grossCgstPaise + grossSgstPaise)}",
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = tokens.primary
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = grossIgstRupees,
                            onValueChange = { grossIgstRupees = FinancialEngine.sanitizeDecimalInput(it) },
                            label = { Text("IGST (₹)", fontSize = 10.sp) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("input_gross_igst"),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = grossCgstRupees,
                            onValueChange = { grossCgstRupees = FinancialEngine.sanitizeDecimalInput(it) },
                            label = { Text("CGST (₹)", fontSize = 10.sp) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("input_gross_cgst"),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = grossSgstRupees,
                            onValueChange = { grossSgstRupees = FinancialEngine.sanitizeDecimalInput(it) },
                            label = { Text("SGST (₹)", fontSize = 10.sp) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("input_gross_sgst"),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true
                        )
                    }

                    HorizontalDivider(color = tokens.cardBorder.copy(alpha = 0.5f))

                    // SECTION 2: AVAILABLE INPUT TAX CREDIT (ITC)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "2. Available Input Tax Credit (ITC)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = tokens.text
                        )
                        Text(
                            text = "Total: ${FinancialEngine.formatPaise(itcIgstPaise + itcCgstPaise + itcSgstPaise)}",
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = tokens.success
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = itcIgstRupees,
                            onValueChange = { itcIgstRupees = FinancialEngine.sanitizeDecimalInput(it) },
                            label = { Text("IGST ITC (₹)", fontSize = 10.sp) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("input_itc_igst"),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = itcCgstRupees,
                            onValueChange = { itcCgstRupees = FinancialEngine.sanitizeDecimalInput(it) },
                            label = { Text("CGST ITC (₹)", fontSize = 10.sp) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("input_itc_cgst"),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = itcSgstRupees,
                            onValueChange = { itcSgstRupees = FinancialEngine.sanitizeDecimalInput(it) },
                            label = { Text("SGST ITC (₹)", fontSize = 10.sp) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("input_itc_sgst"),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true
                        )
                    }
                }
            }
        }

        // -------------------------------------------------------------
        // Strategy Selection Chips & Custom Proportion Slider
        // -------------------------------------------------------------
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = tokens.card),
                border = BorderStroke(1.dp, tokens.cardBorder),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "3. Rule 88A Utilization Strategy",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = tokens.text
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        CmaAndGstEngine.Rule88AStrategy.values().forEach { strat ->
                            val isSelected = selectedStrategy == strat
                            FilterChip(
                                selected = isSelected,
                                onClick = { selectedStrategy = strat },
                                label = {
                                    Text(
                                        text = strat.displayName,
                                        fontSize = 10.5.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                leadingIcon = if (strat == CmaAndGstEngine.Rule88AStrategy.OPTIMAL) {
                                    {
                                        Icon(
                                            Icons.Default.Star,
                                            contentDescription = "Recommended",
                                            modifier = Modifier.size(13.dp),
                                            tint = if (isSelected) tokens.primary else tokens.success
                                        )
                                    }
                                } else null,
                                modifier = Modifier.heightIn(min = 48.dp)
                            )
                        }
                    }

                    // Interactive Slider for Custom Strategy
                    AnimatedVisibility(visible = selectedStrategy == CmaAndGstEngine.Rule88AStrategy.CUSTOM) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(tokens.secondaryBg, RoundedCornerShape(8.dp))
                                .padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Allocate to CGST: ${customCgstPercent.toInt()}%",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = tokens.primary
                                )
                                Text(
                                    text = "Allocate to SGST: ${100 - customCgstPercent.toInt()}%",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = tokens.accent
                                )
                            }

                            Slider(
                                value = customCgstPercent,
                                onValueChange = { customCgstPercent = it },
                                valueRange = 0f..100f,
                                steps = 19,
                                modifier = Modifier.testTag("custom_rule_88a_slider")
                            )

                            Text(
                                text = "Rule 88A gives you complete statutory freedom to utilize unexhausted IGST against CGST and SGST in any proportion.",
                                fontSize = 9.5.sp,
                                color = tokens.textMuted
                            )
                        }
                    }
                }
            }
        }

        // -------------------------------------------------------------
        // Optimization Insight & Cash Savings Banner
        // -------------------------------------------------------------
        item {
            val savings = activeMatrix.cashSavingsPaise
            val isZeroCash = activeMatrix.totalCashPayablePaise == 0L

            Surface(
                color = when {
                    isZeroCash -> tokens.successContainer.copy(alpha = 0.45f)
                    savings > 0 -> tokens.primary.copy(alpha = 0.12f)
                    else -> tokens.secondaryBg
                },
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(
                    1.dp,
                    when {
                        isZeroCash -> tokens.success.copy(alpha = 0.4f)
                        savings > 0 -> tokens.primary.copy(alpha = 0.4f)
                        else -> tokens.cardBorder
                    }
                )
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (isZeroCash) Icons.Default.CheckCircle else Icons.Default.Lightbulb,
                                contentDescription = null,
                                tint = if (isZeroCash) tokens.success else tokens.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = when {
                                    isZeroCash -> "Zero Cash Payment Required"
                                    savings > 0 -> "Optimal Strategy Saves Cash"
                                    else -> "Utilization Assessment"
                                },
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = tokens.text
                            )
                        }

                        if (savings > 0) {
                            Surface(
                                color = tokens.successContainer,
                                shape = RoundedCornerShape(6.dp),
                                border = BorderStroke(0.5.dp, tokens.success)
                            ) {
                                Text(
                                    text = "Saves ${FinancialEngine.formatPaise(savings)}",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = tokens.onSuccessContainer,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    Text(
                        text = when {
                            isZeroCash -> "Your available Input Tax Credit fully covers all Central, State, and Integrated tax liabilities. No cash ledger deposit required for this period."
                            savings > 0 -> "By strategically routing unutilized IGST credit toward the tax head facing an ITC deficit, you save ${FinancialEngine.formatPaise(savings)} in direct cash outflow compared to an arbitrary 50:50 equal split!"
                            else -> "ITC set-off strictly conforms to statutory Rule 88A order of utilization (Circular No. 98/17/2019-GST)."
                        },
                        fontSize = 11.sp,
                        color = tokens.text
                    )

                    // Summary Pills
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            modifier = Modifier.weight(1f),
                            color = tokens.card,
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, tokens.cardBorder)
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Text("Net Cash Payable", fontSize = 9.5.sp, color = tokens.textMuted)
                                Text(
                                    text = FinancialEngine.formatPaise(activeMatrix.totalCashPayablePaise),
                                    fontWeight = FontWeight.Black,
                                    fontSize = 13.sp,
                                    color = if (activeMatrix.totalCashPayablePaise > 0) tokens.danger else tokens.success
                                )
                            }
                        }

                        Surface(
                            modifier = Modifier.weight(1f),
                            color = tokens.card,
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, tokens.cardBorder)
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Text("Carried Forward ITC", fontSize = 9.5.sp, color = tokens.textMuted)
                                Text(
                                    text = FinancialEngine.formatPaise(activeMatrix.totalCarriedForwardItcPaise),
                                    fontWeight = FontWeight.Black,
                                    fontSize = 13.sp,
                                    color = tokens.accent
                                )
                            }
                        }
                    }
                }
            }
        }

        // -------------------------------------------------------------
        // Strategy Multi-Comparison Grid
        // -------------------------------------------------------------
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = tokens.card),
                border = BorderStroke(1.dp, tokens.cardBorder),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Strategy Impact Comparison",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = tokens.text
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        StrategyCard(
                            title = "Optimal (Recommended)",
                            cash = comparison.optimal.totalCashPayablePaise,
                            cf = comparison.optimal.totalCarriedForwardItcPaise,
                            isSelected = selectedStrategy == CmaAndGstEngine.Rule88AStrategy.OPTIMAL,
                            isBest = true,
                            tokens = tokens,
                            onClick = { selectedStrategy = CmaAndGstEngine.Rule88AStrategy.OPTIMAL }
                        )

                        StrategyCard(
                            title = "50:50 Equal Split",
                            cash = comparison.equalSplit.totalCashPayablePaise,
                            cf = comparison.equalSplit.totalCarriedForwardItcPaise,
                            isSelected = selectedStrategy == CmaAndGstEngine.Rule88AStrategy.EQUAL_SPLIT,
                            isBest = false,
                            tokens = tokens,
                            onClick = { selectedStrategy = CmaAndGstEngine.Rule88AStrategy.EQUAL_SPLIT }
                        )

                        StrategyCard(
                            title = "CGST Priority",
                            cash = comparison.cgstFirst.totalCashPayablePaise,
                            cf = comparison.cgstFirst.totalCarriedForwardItcPaise,
                            isSelected = selectedStrategy == CmaAndGstEngine.Rule88AStrategy.CGST_FIRST,
                            isBest = false,
                            tokens = tokens,
                            onClick = { selectedStrategy = CmaAndGstEngine.Rule88AStrategy.CGST_FIRST }
                        )

                        StrategyCard(
                            title = "SGST Priority",
                            cash = comparison.sgstFirst.totalCashPayablePaise,
                            cf = comparison.sgstFirst.totalCarriedForwardItcPaise,
                            isSelected = selectedStrategy == CmaAndGstEngine.Rule88AStrategy.SGST_FIRST,
                            isBest = false,
                            tokens = tokens,
                            onClick = { selectedStrategy = CmaAndGstEngine.Rule88AStrategy.SGST_FIRST }
                        )
                    }
                }
            }
        }

        // -------------------------------------------------------------
        // Interactive Rule 88A Set-off Matrix Table
        // -------------------------------------------------------------
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = tokens.card),
                border = BorderStroke(1.dp, tokens.cardBorder),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Resulting Set-off Matrix (${selectedStrategy.displayName})",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = tokens.text
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                    ) {
                        Column(modifier = Modifier.widthIn(min = 520.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            // Header
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(tokens.secondaryBg, RoundedCornerShape(6.dp))
                                    .padding(vertical = 6.dp, horizontal = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Tax Head", modifier = Modifier.width(90.dp), fontWeight = FontWeight.Bold, fontSize = 10.sp, color = tokens.text)
                                Text("Gross Tax", modifier = Modifier.width(85.dp), fontWeight = FontWeight.Bold, fontSize = 10.sp, textAlign = TextAlign.End, color = tokens.text)
                                Text("From IGST", modifier = Modifier.width(85.dp), fontWeight = FontWeight.Bold, fontSize = 10.sp, textAlign = TextAlign.End, color = tokens.success)
                                Text("From CGST", modifier = Modifier.width(85.dp), fontWeight = FontWeight.Bold, fontSize = 10.sp, textAlign = TextAlign.End, color = tokens.success)
                                Text("From SGST", modifier = Modifier.width(85.dp), fontWeight = FontWeight.Bold, fontSize = 10.sp, textAlign = TextAlign.End, color = tokens.success)
                                Text("Cash Payable", modifier = Modifier.width(90.dp), fontWeight = FontWeight.Bold, fontSize = 10.sp, textAlign = TextAlign.End, color = tokens.danger)
                            }

                            // IGST Row
                            MatrixRow(
                                head = "Integrated (IGST)",
                                gross = activeMatrix.grossIgstPaise,
                                fromIgst = activeMatrix.igstUsedForIgstPaise,
                                fromCgst = activeMatrix.cgstUsedForIgstPaise,
                                fromSgst = activeMatrix.sgstUsedForIgstPaise,
                                cash = activeMatrix.cashIgstPaise,
                                tokens = tokens
                            )

                            // CGST Row
                            MatrixRow(
                                head = "Central (CGST)",
                                gross = activeMatrix.grossCgstPaise,
                                fromIgst = activeMatrix.igstUsedForCgstPaise,
                                fromCgst = activeMatrix.cgstUsedForCgstPaise,
                                fromSgst = null,
                                cash = activeMatrix.cashCgstPaise,
                                tokens = tokens
                            )

                            // SGST Row
                            MatrixRow(
                                head = "State (SGST)",
                                gross = activeMatrix.grossSgstPaise,
                                fromIgst = activeMatrix.igstUsedForSgstPaise,
                                fromCgst = null,
                                fromSgst = activeMatrix.sgstUsedForSgstPaise,
                                cash = activeMatrix.cashSgstPaise,
                                tokens = tokens
                            )

                            HorizontalDivider(color = tokens.cardBorder)

                            // Totals
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(tokens.secondaryBg.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                                    .padding(vertical = 6.dp, horizontal = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Total", modifier = Modifier.width(90.dp), fontWeight = FontWeight.Black, fontSize = 10.sp, color = tokens.text)
                                Text(FinancialEngine.formatPaise(activeMatrix.totalGrossLiabilityPaise), modifier = Modifier.width(85.dp), fontWeight = FontWeight.Black, fontSize = 10.sp, textAlign = TextAlign.End, color = tokens.text)
                                Text(FinancialEngine.formatPaise(activeMatrix.totalIgstUtilizedPaise), modifier = Modifier.width(85.dp), fontWeight = FontWeight.Black, fontSize = 10.sp, textAlign = TextAlign.End, color = tokens.success)
                                Text(FinancialEngine.formatPaise(activeMatrix.totalCgstUtilizedPaise), modifier = Modifier.width(85.dp), fontWeight = FontWeight.Black, fontSize = 10.sp, textAlign = TextAlign.End, color = tokens.success)
                                Text(FinancialEngine.formatPaise(activeMatrix.totalSgstUtilizedPaise), modifier = Modifier.width(85.dp), fontWeight = FontWeight.Black, fontSize = 10.sp, textAlign = TextAlign.End, color = tokens.success)
                                Text(FinancialEngine.formatPaise(activeMatrix.totalCashPayablePaise), modifier = Modifier.width(90.dp), fontWeight = FontWeight.Black, fontSize = 10.sp, textAlign = TextAlign.End, color = tokens.danger)
                            }
                        }
                    }

                    // Carried forward details
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Balance ITC Carried Forward", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = tokens.text)
                            Text(
                                "IGST: ${FinancialEngine.formatPaise(activeMatrix.carriedForwardIgstPaise)} | CGST: ${FinancialEngine.formatPaise(activeMatrix.carriedForwardCgstPaise)} | SGST: ${FinancialEngine.formatPaise(activeMatrix.carriedForwardSgstPaise)}",
                                fontSize = 9.5.sp,
                                color = tokens.textMuted
                            )
                        }
                        Text(
                            text = FinancialEngine.formatPaise(activeMatrix.totalCarriedForwardItcPaise),
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = tokens.accent
                        )
                    }
                }
            }
        }

        // -------------------------------------------------------------
        // Step-by-Step Statutory Audit Trail
        // -------------------------------------------------------------
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = tokens.card),
                border = BorderStroke(1.dp, tokens.cardBorder),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .clickable { showStepsExpanded = !showStepsExpanded },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.FormatListNumbered, contentDescription = null, tint = tokens.accent, modifier = Modifier.size(16.dp))
                            Text(
                                text = "Rule 88A Step-by-Step Statutory Audit Trail",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = tokens.text
                            )
                        }
                        Icon(
                            imageVector = if (showStepsExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null,
                            tint = tokens.textMuted
                        )
                    }

                    AnimatedVisibility(visible = showStepsExpanded) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            activeMatrix.explanationSteps.forEach { stepText ->
                                Surface(
                                    color = tokens.secondaryBg,
                                    shape = RoundedCornerShape(6.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = stepText,
                                        fontSize = 10.sp,
                                        color = tokens.text,
                                        modifier = Modifier.padding(8.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // -------------------------------------------------------------
        // Action Buttons: Apply to GSTR-3B & Copy Advice
        // -------------------------------------------------------------
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        val advice = buildString {
                            appendLine("=== RULE 88A ITC SET-OFF REPORT ===")
                            appendLine("Strategy: ${selectedStrategy.displayName}")
                            appendLine("Gross Liabilities: IGST: ${FinancialEngine.formatPaise(grossIgstPaise)}, CGST: ${FinancialEngine.formatPaise(grossCgstPaise)}, SGST: ${FinancialEngine.formatPaise(grossSgstPaise)}")
                            appendLine("Available ITC: IGST: ${FinancialEngine.formatPaise(itcIgstPaise)}, CGST: ${FinancialEngine.formatPaise(itcCgstPaise)}, SGST: ${FinancialEngine.formatPaise(itcSgstPaise)}")
                            appendLine("Net Cash Payable: ${FinancialEngine.formatPaise(activeMatrix.totalCashPayablePaise)}")
                            appendLine("Carried Forward ITC: ${FinancialEngine.formatPaise(activeMatrix.totalCarriedForwardItcPaise)}")
                            if (activeMatrix.cashSavingsPaise > 0) {
                                appendLine("Cash Saved vs 50:50: ${FinancialEngine.formatPaise(activeMatrix.cashSavingsPaise)}")
                            }
                            appendLine("\nStep Breakdown:")
                            activeMatrix.explanationSteps.forEach { appendLine(it) }
                        }
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("Rule 88A Set-off Advice", advice))
                        Toast.makeText(context, "Rule 88A advice copied to clipboard!", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("btn_copy_setoff_advice"),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                        Text("Copy Advice", fontSize = 11.sp)
                    }
                }

                if (onApplyToGstr3b != null) {
                    Button(
                        onClick = {
                            val percent = if (selectedStrategy == CmaAndGstEngine.Rule88AStrategy.CUSTOM) customCgstPercent.toDouble() else null
                            onApplyToGstr3b(selectedStrategy, percent)
                            Toast.makeText(context, "${selectedStrategy.displayName} applied to active GSTR-3B!", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("btn_apply_strategy_to_gstr3b"),
                        colors = ButtonDefaults.buttonColors(containerColor = tokens.primary),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp), tint = tokens.primaryText)
                            Text("Apply to GSTR-3B", fontSize = 11.sp, color = tokens.primaryText, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StrategyCard(
    title: String,
    cash: Long,
    cf: Long,
    isSelected: Boolean,
    isBest: Boolean,
    tokens: com.example.ui.theme.PabaruTokens,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = if (isSelected) tokens.primary.copy(alpha = 0.15f) else tokens.secondaryBg,
        border = BorderStroke(
            if (isSelected) 1.5.dp else 1.dp,
            if (isSelected) tokens.primary else tokens.cardBorder
        ),
        modifier = Modifier.width(135.dp).heightIn(min = 48.dp)
    ) {
        Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    fontSize = 10.sp,
                    fontWeight = if (isSelected) FontWeight.Black else FontWeight.Bold,
                    color = tokens.text
                )
                if (isBest) {
                    Box(modifier = Modifier.size(6.dp).background(tokens.success, CircleShape))
                }
            }
            Text(
                text = "Cash: ${FinancialEngine.formatPaise(cash)}",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = if (cash > 0) tokens.danger else tokens.success
            )
            Text(
                text = "CF: ${FinancialEngine.formatPaise(cf)}",
                fontSize = 9.sp,
                color = tokens.textMuted
            )
        }
    }
}

@Composable
private fun MatrixRow(
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
        Text(head, modifier = Modifier.width(90.dp), fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = tokens.text)
        Text(FinancialEngine.formatPaise(gross), modifier = Modifier.width(85.dp), fontSize = 10.sp, textAlign = TextAlign.End, color = tokens.text)
        Text(FinancialEngine.formatPaise(fromIgst), modifier = Modifier.width(85.dp), fontSize = 10.sp, textAlign = TextAlign.End, color = tokens.success)

        if (fromCgst != null) {
            Text(FinancialEngine.formatPaise(fromCgst), modifier = Modifier.width(85.dp), fontSize = 10.sp, textAlign = TextAlign.End, color = tokens.success)
        } else {
            Text("Blocked", modifier = Modifier.width(85.dp), fontSize = 9.sp, textAlign = TextAlign.End, color = tokens.textMuted.copy(alpha = 0.5f))
        }

        if (fromSgst != null) {
            Text(FinancialEngine.formatPaise(fromSgst), modifier = Modifier.width(85.dp), fontSize = 10.sp, textAlign = TextAlign.End, color = tokens.success)
        } else {
            Text("Blocked", modifier = Modifier.width(85.dp), fontSize = 9.sp, textAlign = TextAlign.End, color = tokens.textMuted.copy(alpha = 0.5f))
        }

        Text(
            FinancialEngine.formatPaise(cash),
            modifier = Modifier.width(90.dp),
            fontSize = 10.sp,
            textAlign = TextAlign.End,
            fontWeight = FontWeight.Bold,
            color = if (cash > 0) tokens.danger else tokens.success
        )
    }
}
