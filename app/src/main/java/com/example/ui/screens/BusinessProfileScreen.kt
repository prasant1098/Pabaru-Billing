package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import com.example.engine.FinancialEngine
import com.example.model.BusinessEntityType
import com.example.model.BusinessProfile
import com.example.model.RegistrationType
import com.example.ui.theme.PabaruTheme
import com.example.ui.theme.danger
import com.example.ui.theme.success
import com.example.viewmodel.PabaruUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BusinessProfileScreen(
    uiState: PabaruUiState,
    onSaveProfile: (BusinessProfile) -> Unit,
    onBack: () -> Unit,
    onOpenThemePicker: () -> Unit = {}
) {
    val tokens = PabaruTheme.tokens

    var businessName by remember { mutableStateOf(uiState.profile.businessName) }
    var entityType by remember { mutableStateOf(uiState.profile.entityType) }
    var ownerName by remember { mutableStateOf(uiState.profile.ownerName) }
    var phone by remember { mutableStateOf(uiState.profile.phone) }
    var email by remember { mutableStateOf(uiState.profile.email) }
    var address by remember { mutableStateOf(uiState.profile.address) }
    var homeStateCode by remember { mutableStateOf(uiState.profile.homeStateCode) }

    // Statutory and Tax Compliance Fields
    var gstin by remember { mutableStateOf(uiState.profile.gstin) }
    var pan by remember { mutableStateOf(uiState.profile.pan) }
    var udyamNumber by remember { mutableStateOf(uiState.profile.udyamNumber) }
    var regType by remember { mutableStateOf(uiState.profile.registrationType) }
    var filingFreq by remember { mutableStateOf(uiState.profile.filingFrequency) }

    var expandedEntityDropdown by remember { mutableStateOf(false) }
    var expandedStateDropdown by remember { mutableStateOf(false) }
    var expandedRegDropdown by remember { mutableStateOf(false) }
    var expandedFilingDropdown by remember { mutableStateOf(false) }

    var isSavedSnackbar by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val gstinValidation = remember(gstin) {
        FinancialEngine.validateGstin(gstin, allowBlank = true)
    }
    val panValidation = remember(pan) {
        FinancialEngine.validatePan(pan, allowBlank = true)
    }

    BackHandler(onBack = onBack)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = tokens.text)
            }
            Spacer(modifier = Modifier.width(4.dp))
            Column {
                Text(
                    "Business Profile & Statutory Details",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = tokens.text
                )
                Text(
                    "Configure legal identity, GSTIN, Udyam & Tax Registration",
                    fontSize = 11.sp,
                    color = tokens.textMuted
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 1. Basic Profile Card (Entered at Sign-in/Registration)
        Card(
            colors = CardDefaults.cardColors(containerColor = tokens.card),
            border = BorderStroke(1.dp, tokens.cardBorder),
            shape = RoundedCornerShape(14.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
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
                        "1. Basic Business Identity",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = tokens.text
                    )
                    Surface(
                        color = tokens.accentTint,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            uiState.profile.storeCode,
                            color = tokens.accent,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                HorizontalDivider(color = tokens.cardBorder)

                OutlinedTextField(
                    value = businessName,
                    onValueChange = { businessName = it },
                    label = { Text("Trade Name / Firm Name") },
                    leadingIcon = { Icon(Icons.Default.Storefront, contentDescription = null, tint = tokens.accent) },
                    modifier = Modifier.fillMaxWidth()
                )

                // Entity Type Dropdown
                ExposedDropdownMenuBox(
                    expanded = expandedEntityDropdown,
                    onExpandedChange = { expandedEntityDropdown = !expandedEntityDropdown },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = entityType.displayName,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Legal Entity Constitution") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedEntityDropdown) },
                        modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = expandedEntityDropdown,
                        onDismissRequest = { expandedEntityDropdown = false },
                        modifier = Modifier.background(tokens.card)
                    ) {
                        BusinessEntityType.values().forEach { type ->
                            DropdownMenuItem(
                                text = { Text(type.displayName, fontSize = 12.sp, color = tokens.text) },
                                onClick = {
                                    entityType = type
                                    expandedEntityDropdown = false
                                }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = ownerName,
                    onValueChange = { ownerName = it },
                    label = { Text("Authorized Signatory (${entityType.signatoryTitle})") },
                    leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = tokens.accent) },
                    modifier = Modifier.fillMaxWidth()
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = phone,
                        onValueChange = { phone = it },
                        label = { Text("Primary Phone") },
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text("Email") },
                        modifier = Modifier.weight(1f)
                    )
                }

                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("Principal Place of Business") },
                    leadingIcon = { Icon(Icons.Default.LocationOn, contentDescription = null, tint = tokens.accent) },
                    modifier = Modifier.fillMaxWidth()
                )

                // Home State Dropdown
                val currentState = FinancialEngine.INDIAN_STATES.find { it.code == homeStateCode }
                ExposedDropdownMenuBox(
                    expanded = expandedStateDropdown,
                    onExpandedChange = { expandedStateDropdown = !expandedStateDropdown },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = currentState?.let { "${it.code} - ${it.name}" } ?: homeStateCode,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Home State (Determines CGST/SGST vs IGST)") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedStateDropdown) },
                        modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = expandedStateDropdown,
                        onDismissRequest = { expandedStateDropdown = false },
                        modifier = Modifier.background(tokens.card)
                    ) {
                        FinancialEngine.INDIAN_STATES.forEach { state ->
                            DropdownMenuItem(
                                text = { Text("${state.code} - ${state.name}", fontSize = 12.sp, color = tokens.text) },
                                onClick = {
                                    homeStateCode = state.code
                                    expandedStateDropdown = false
                                }
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 2. Statutory & Tax Registration Card
        Card(
            colors = CardDefaults.cardColors(containerColor = tokens.card),
            border = BorderStroke(1.dp, tokens.cardBorder),
            shape = RoundedCornerShape(14.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    "2. Statutory Compliance & Tax Numbers",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = tokens.text
                )
                HorizontalDivider(color = tokens.cardBorder)

                // GSTIN Field with Live Validation Indicator
                OutlinedTextField(
                    value = gstin,
                    onValueChange = { gstin = it.uppercase() },
                    label = { Text("GSTIN (Goods and Services Tax ID)") },
                    placeholder = { Text("e.g. 27ABCDE1234F1Z5") },
                    leadingIcon = { Icon(Icons.Default.ReceiptLong, contentDescription = null, tint = tokens.accent) },
                    trailingIcon = {
                        if (gstin.isNotBlank()) {
                            Icon(
                                imageVector = if (gstinValidation.isValid) Icons.Default.CheckCircle else Icons.Default.Warning,
                                contentDescription = null,
                                tint = if (gstinValidation.isValid) tokens.success else tokens.danger
                            )
                        }
                    },
                    isError = gstin.isNotBlank() && !gstinValidation.isValid,
                    modifier = Modifier.fillMaxWidth()
                )

                if (gstin.isNotBlank()) {
                    Text(
                        gstinValidation.message,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (gstinValidation.isValid) tokens.success else tokens.danger
                    )
                }

                OutlinedTextField(
                    value = pan,
                    onValueChange = { pan = it.uppercase() },
                    label = { Text("PAN (Permanent Account Number)") },
                    placeholder = { Text("e.g. ABCDE1234F") },
                    leadingIcon = { Icon(Icons.Default.Badge, contentDescription = null, tint = tokens.accent) },
                    trailingIcon = {
                        if (pan.isNotBlank()) {
                            Icon(
                                imageVector = if (panValidation.isValid) Icons.Default.CheckCircle else Icons.Default.Warning,
                                contentDescription = null,
                                tint = if (panValidation.isValid) tokens.success else tokens.danger
                            )
                        }
                    },
                    isError = pan.isNotBlank() && !panValidation.isValid,
                    modifier = Modifier.fillMaxWidth()
                )

                if (pan.isNotBlank()) {
                    Text(
                        panValidation.message,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (panValidation.isValid) tokens.success else tokens.danger
                    )
                }

                OutlinedTextField(
                    value = udyamNumber,
                    onValueChange = { udyamNumber = it.uppercase() },
                    label = { Text("MSME / Udyam Registration Number") },
                    placeholder = { Text("e.g. UDYAM-MH-01-0019283") },
                    leadingIcon = { Icon(Icons.Default.WorkspacePremium, contentDescription = null, tint = tokens.accent) },
                    modifier = Modifier.fillMaxWidth()
                )

                // Registration Type Dropdown
                ExposedDropdownMenuBox(
                    expanded = expandedRegDropdown,
                    onExpandedChange = { expandedRegDropdown = !expandedRegDropdown },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = when (regType) {
                            RegistrationType.regular -> "Regular GST Registered (Tax Invoice eligible)"
                            RegistrationType.composition -> "Composition Dealer (Bill of Supply only)"
                            RegistrationType.unregistered -> "Unregistered / Small Business"
                        },
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("GST Registration Type") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedRegDropdown) },
                        modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = expandedRegDropdown,
                        onDismissRequest = { expandedRegDropdown = false },
                        modifier = Modifier.background(tokens.card)
                    ) {
                        RegistrationType.values().forEach { type ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        when (type) {
                                            RegistrationType.regular -> "Regular GST Registered"
                                            RegistrationType.composition -> "Composition Dealer"
                                            RegistrationType.unregistered -> "Unregistered"
                                        },
                                        fontSize = 12.sp,
                                        color = tokens.text
                                    )
                                },
                                onClick = {
                                    regType = type
                                    expandedRegDropdown = false
                                }
                            )
                        }
                    }
                }

                // Filing Frequency Dropdown
                ExposedDropdownMenuBox(
                    expanded = expandedFilingDropdown,
                    onExpandedChange = { expandedFilingDropdown = !expandedFilingDropdown },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = filingFreq,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("GSTR Filing Frequency") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedFilingDropdown) },
                        modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = expandedFilingDropdown,
                        onDismissRequest = { expandedFilingDropdown = false },
                        modifier = Modifier.background(tokens.card)
                    ) {
                        listOf("Monthly (Standard)", "Quarterly (QRMP Scheme)").forEach { freq ->
                            DropdownMenuItem(
                                text = { Text(freq, fontSize = 12.sp, color = tokens.text) },
                                onClick = {
                                    filingFreq = freq
                                    expandedFilingDropdown = false
                                }
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 3. Theme & Visual Appearance Card (As requested: "Also add the same 'Theme' option in Profile settings.")
        Card(
            colors = CardDefaults.cardColors(containerColor = tokens.card),
            border = BorderStroke(1.dp, tokens.cardBorder),
            shape = RoundedCornerShape(14.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
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
                        "3. Theme & Visual Appearance",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = tokens.text
                    )
                    Surface(
                        color = tokens.accentTint,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            if (uiState.themeMode == "auto") "Auto (follow device)" else uiState.currentTheme.displayName,
                            color = tokens.accent,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                HorizontalDivider(color = tokens.cardBorder)

                Text(
                    "6 custom WCAG AA color palettes (Dark / Light) or Auto device tracking with 200ms transition.",
                    fontSize = 11.sp,
                    color = tokens.textMuted
                )

                OutlinedButton(
                    onClick = onOpenThemePicker,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = tokens.accent),
                    border = BorderStroke(1.dp, tokens.accent)
                ) {
                    Icon(Icons.Default.Palette, contentDescription = null, tint = tokens.accent)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "Change Theme (${if (uiState.themeMode == "auto") "Auto" else uiState.currentTheme.displayName})",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Button(
            onClick = {
                if (businessName.trim().isBlank()) {
                    Toast.makeText(context, "Business name cannot be empty", Toast.LENGTH_SHORT).show()
                    return@Button
                }
                if (gstin.isNotBlank() && !gstinValidation.isValid) {
                    Toast.makeText(context, gstinValidation.message, Toast.LENGTH_LONG).show()
                    return@Button
                }
                if (pan.isNotBlank() && !panValidation.isValid) {
                    Toast.makeText(context, panValidation.message, Toast.LENGTH_LONG).show()
                    return@Button
                }
                val updated = uiState.profile.copy(
                    businessName = businessName.trim(),
                    entityType = entityType,
                    ownerName = ownerName.trim(),
                    phone = phone.trim(),
                    email = email.trim(),
                    address = address.trim(),
                    homeStateCode = homeStateCode,
                    gstin = gstin.trim(),
                    pan = pan.trim(),
                    udyamNumber = udyamNumber.trim(),
                    registrationType = regType,
                    filingFrequency = filingFreq
                )
                onSaveProfile(updated)
                isSavedSnackbar = true
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            colors = ButtonDefaults.buttonColors(containerColor = tokens.primary),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Default.Save, contentDescription = null, tint = tokens.primaryText)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Save Statutory Profile & Update Ledger", color = tokens.primaryText, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }

        if (isSavedSnackbar) {
            Spacer(modifier = Modifier.height(10.dp))
            Card(
                colors = CardDefaults.cardColors(containerColor = tokens.accentTint),
                border = BorderStroke(1.dp, tokens.accent),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = tokens.accent)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Statutory details saved and hashed into tamper-evident audit log!", color = tokens.accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
