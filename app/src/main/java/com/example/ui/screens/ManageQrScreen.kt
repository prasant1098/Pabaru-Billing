package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.engine.ExportEngine
import com.example.model.BankAccount
import com.example.model.UpiQrCode
import com.example.ui.theme.PabaruTheme
import com.example.ui.theme.danger
import com.example.ui.theme.onSuccessContainer
import com.example.ui.theme.success
import com.example.ui.theme.successContainer
import com.example.ui.theme.warning
import com.example.viewmodel.PabaruUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageQrScreen(
    uiState: PabaruUiState,
    onBack: () -> Unit,
    onLockNow: () -> Unit,
    onAddQr: (UpiQrCode) -> Unit,
    onUpdateQr: (UpiQrCode) -> Unit,
    onDeleteQr: (String) -> Unit,
    onSetDefaultQr: (String) -> Unit,
    onToggleQrEnabled: (String) -> Unit
) {
    val context = LocalContext.current
    val tokens = PabaruTheme.tokens
    var qrToEdit by remember { mutableStateOf<UpiQrCode?>(null) }
    var isAddingNewQr by remember { mutableStateOf(false) }
    var qrToShowLarge by remember { mutableStateOf<UpiQrCode?>(null) }
    var sensitiveConfirmAction by remember { mutableStateOf<Triple<String, String, () -> Unit>?>(null) }
    var showAllLogsSheet by remember { mutableStateOf(false) }

    BackHandler(onBack = onBack)

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
            // Header: Back Arrow, Title, Manager mode pill, Lock now
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = "Back",
                        tint = tokens.text
                    )
                }

                Spacer(modifier = Modifier.width(4.dp))

                Text(
                    text = "Manage QR",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Medium,
                    color = tokens.text
                )

                Spacer(modifier = Modifier.width(8.dp))

                // Green "Manager mode" pill
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = tokens.successContainer,
                    border = BorderStroke(0.5.dp, tokens.success)
                ) {
                    Text(
                        text = "Manager mode",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = tokens.onSuccessContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                // Lock now text button
                Box(
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .clickable(onClick = onLockNow)
                        .padding(horizontal = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Lock now",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = tokens.warning
                    )
                }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(top = 4.dp, bottom = 88.dp)
            ) {
                item {
                    Text(
                        text = "The default QR appears on new invoices and the POS screen.",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Normal,
                        color = tokens.textMuted
                    )
                }

                // List of saved QR codes
                items(uiState.upiQrs, key = { it.id }) { qr ->
                    val linkedBank = uiState.bankAccounts.find { it.id == qr.linkedBankId }
                    val bankLabel = linkedBank?.let { "${it.bankName} (••••${it.accountNumber.takeLast(4)})" } ?: "Unlinked bank account"
                    var showDropdownMenu by remember { mutableStateOf(false) }

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = tokens.card,
                        border = BorderStroke(0.5.dp, if (qr.isDefault) tokens.success.copy(alpha = 0.5f) else tokens.cardBorder)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // QR thumbnail (high-contrast black on white for scannability)
                                Surface(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clickable { qrToShowLarge = qr },
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color.White,
                                    border = BorderStroke(0.5.dp, tokens.cardBorder)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(Icons.Outlined.QrCode2, contentDescription = "Show QR code", tint = Color.Black, modifier = Modifier.size(32.dp))
                                    }
                                }

                                Spacer(modifier = Modifier.width(10.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = qr.label,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = tokens.text
                                        )
                                        if (qr.isDefault) {
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
                                                    color = tokens.onSuccessContainer,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                                                )
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(text = qr.upiId, fontSize = 12.sp, color = tokens.textMuted)
                                    Text(text = bankLabel, fontSize = 11.sp, color = tokens.textMuted)
                                }

                                // Toggle enabled
                                Switch(
                                    checked = qr.isEnabled,
                                    onCheckedChange = {
                                        val enabledCount = uiState.upiQrs.count { it.isEnabled }
                                        if (qr.isEnabled && enabledCount <= 1) {
                                            Toast.makeText(context, "At least one QR must stay enabled", Toast.LENGTH_SHORT).show()
                                        } else {
                                            onToggleQrEnabled(qr.id)
                                        }
                                    },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = tokens.primaryText,
                                        checkedTrackColor = tokens.success,
                                        uncheckedThumbColor = tokens.textMuted,
                                        uncheckedTrackColor = tokens.secondaryBg
                                    )
                                )

                                // Three dots menu
                                Box {
                                    IconButton(
                                        onClick = { showDropdownMenu = true },
                                        modifier = Modifier.size(48.dp)
                                    ) {
                                        Icon(Icons.Outlined.MoreVert, contentDescription = "More options for ${qr.label}", tint = tokens.textMuted)
                                    }

                                    DropdownMenu(
                                        expanded = showDropdownMenu,
                                        onDismissRequest = { showDropdownMenu = false },
                                        modifier = Modifier
                                            .background(tokens.card)
                                            .border(0.5.dp, tokens.cardBorder, RoundedCornerShape(12.dp))
                                    ) {
                                        if (!qr.isDefault) {
                                            DropdownMenuItem(
                                                text = { Text("Set as default", fontSize = 13.sp, color = tokens.text) },
                                                onClick = {
                                                    showDropdownMenu = false
                                                    sensitiveConfirmAction = Triple(
                                                        "Change default QR",
                                                        "Set ${qr.label} (${qr.upiId}) as the default QR for bills and POS?"
                                                    ) {
                                                        onSetDefaultQr(qr.id)
                                                        Toast.makeText(context, "QR updated", Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                            )
                                        }
                                        DropdownMenuItem(
                                            text = { Text("Edit", fontSize = 13.sp, color = tokens.text) },
                                            onClick = {
                                                showDropdownMenu = false
                                                qrToEdit = qr
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Show QR", fontSize = 13.sp, color = tokens.text) },
                                            onClick = {
                                                showDropdownMenu = false
                                                qrToShowLarge = qr
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Share", fontSize = 13.sp, color = tokens.text) },
                                            onClick = {
                                                showDropdownMenu = false
                                                val upiUri = "upi://pay?pa=${Uri.encode(qr.upiId)}&pn=${Uri.encode(qr.label)}&cu=INR"
                                                val shareText = "Pay ${qr.label}\nUPI ID: ${qr.upiId}\nLink: $upiUri"
                                                val intent = Intent(Intent.ACTION_SEND).apply {
                                                    type = "text/plain"
                                                    putExtra(Intent.EXTRA_SUBJECT, "UPI Payment Details - ${qr.label}")
                                                    putExtra(Intent.EXTRA_TEXT, shareText)
                                                }
                                                ExportEngine.launchChooserSafely(context, intent, "Share UPI Payment Details")
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Delete", fontSize = 13.sp, color = tokens.danger) },
                                            onClick = {
                                                showDropdownMenu = false
                                                if (uiState.upiQrs.size <= 1) {
                                                    Toast.makeText(context, "Cannot delete the only QR code", Toast.LENGTH_SHORT).show()
                                                } else {
                                                    sensitiveConfirmAction = Triple(
                                                        "Delete QR code",
                                                        "Are you sure you want to delete ${qr.label}? If this was default, a new default will be assigned."
                                                    ) {
                                                        onDeleteQr(qr.id)
                                                        Toast.makeText(context, "QR deleted", Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Recent changes (Activity Log with SHA-256 verifiable chain)
                item {
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Recent changes",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = tokens.textMuted
                        )
                        Box(
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .clickable { showAllLogsSheet = true }
                                .padding(horizontal = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "View all",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = tokens.link
                            )
                        }
                    }
                }

                items(uiState.qrActivityLogs.take(3), key = { it.id }) { log ->
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
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Outlined.Schedule, contentDescription = null, tint = tokens.textMuted, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${log.action} · ${log.timestamp} · ${log.role}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = tokens.text
                                )
                                Text(text = log.details, fontSize = 11.sp, color = tokens.textMuted)
                            }
                            Text(text = "SHA: ${log.hash}", fontSize = 10.sp, color = tokens.success)
                        }
                    }
                }
            }

            // Bottom Sticky Bar with "Add QR" (48dp tall)
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = tokens.card,
                border = BorderStroke(0.5.dp, tokens.cardBorder)
            ) {
                Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .clickable { isAddingNewQr = true },
                        shape = RoundedCornerShape(12.dp),
                        color = tokens.primary
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("Add QR", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = tokens.primaryText)
                        }
                    }
                }
            }
        }
    }

    // Add / Edit Bottom Sheet
    if (isAddingNewQr || qrToEdit != null) {
        val editing = qrToEdit
        AddEditQrSheet(
            existingQr = editing,
            bankAccounts = uiState.bankAccounts,
            onDismiss = {
                isAddingNewQr = false
                qrToEdit = null
            },
            onSave = { updatedQr ->
                if (editing != null) {
                    val changedUpi = editing.upiId != updatedQr.upiId
                    val changedBank = editing.linkedBankId != updatedQr.linkedBankId
                    val changedDefault = !editing.isDefault && updatedQr.isDefault
                    if (changedUpi || changedBank || changedDefault) {
                        sensitiveConfirmAction = Triple(
                            "Confirm QR changes",
                            "Save changes for ${updatedQr.label}? UPI ID: ${updatedQr.upiId}"
                        ) {
                            onUpdateQr(updatedQr)
                            Toast.makeText(context, "QR updated", Toast.LENGTH_SHORT).show()
                            qrToEdit = null
                            isAddingNewQr = false
                        }
                    } else {
                        onUpdateQr(updatedQr)
                        Toast.makeText(context, "QR updated", Toast.LENGTH_SHORT).show()
                        qrToEdit = null
                        isAddingNewQr = false
                    }
                } else {
                    onAddQr(updatedQr)
                    Toast.makeText(context, "QR code added", Toast.LENGTH_SHORT).show()
                    isAddingNewQr = false
                }
            }
        )
    }

    // Large Scannable QR sheet
    qrToShowLarge?.let { qr ->
        val linkedBank = uiState.bankAccounts.find { it.id == qr.linkedBankId }?.bankName ?: "Unlinked bank account"
        ShowQrBottomSheet(
            qr = qr,
            bankName = linkedBank,
            onDismiss = { qrToShowLarge = null }
        )
    }

    // Confirmation Sheet for Sensitive Changes
    sensitiveConfirmAction?.let { (title, prompt, onConfirm) ->
        ConfirmSensitiveChangeSheet(
            title = title,
            promptMessage = prompt,
            onConfirm = {
                onConfirm()
                sensitiveConfirmAction = null
            },
            onDismiss = { sensitiveConfirmAction = null }
        )
    }

    // View All Activity Logs Sheet
    if (showAllLogsSheet) {
        ModalBottomSheet(
            onDismissRequest = { showAllLogsSheet = false },
            containerColor = tokens.card,
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
                    .navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("All QR security logs", fontSize = 18.sp, fontWeight = FontWeight.Medium, color = tokens.text)
                Text("Every modification is chained with SHA-256 signatures.", fontSize = 12.sp, color = tokens.textMuted)
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 350.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(uiState.qrActivityLogs, key = { it.id }) { log ->
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            color = tokens.secondaryBg,
                            border = BorderStroke(0.5.dp, tokens.cardBorder)
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text("${log.action} · ${log.timestamp}", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = tokens.text)
                                Text(log.details, fontSize = 11.sp, color = tokens.textMuted)
                                Text("Hash: ${log.hash} | Prev: ${log.previousHash}", fontSize = 10.sp, color = tokens.success)
                            }
                        }
                    }
                }
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clickable { showAllLogsSheet = false },
                    shape = RoundedCornerShape(12.dp),
                    color = tokens.secondaryBg,
                    border = BorderStroke(0.5.dp, tokens.cardBorder)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("Close", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = tokens.text)
                    }
                }
            }
        }
    }
}

/**
 * Add or Edit QR Bottom Sheet
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditQrSheet(
    existingQr: UpiQrCode?,
    bankAccounts: List<BankAccount>,
    onDismiss: () -> Unit,
    onSave: (UpiQrCode) -> Unit
) {
    val context = LocalContext.current
    val tokens = PabaruTheme.tokens
    var label by remember { mutableStateOf(existingQr?.label ?: "") }
    var upiId by remember { mutableStateOf(existingQr?.upiId ?: "") }
    var selectedBankId by remember { mutableStateOf(existingQr?.linkedBankId ?: bankAccounts.firstOrNull()?.id ?: "") }
    var showOnInvoices by remember { mutableStateOf(existingQr?.showOnInvoices ?: true) }
    var setAsDefault by remember { mutableStateOf(existingQr?.isDefault ?: false) }
    var qrImageUri by remember { mutableStateOf(existingQr?.qrImageUri) }
    var bankDropdownExpanded by remember { mutableStateOf(false) }

    val pickQrImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: SecurityException) {
                // Transient URI read grant still works for current session
            }
            qrImageUri = uri.toString()
            Toast.makeText(context, "QR image attached", Toast.LENGTH_SHORT).show()
        }
    }

    val isUpiValid = upiId.contains("@") && upiId.length >= 5 && !upiId.startsWith("@") && !upiId.endsWith("@")

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
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = if (existingQr != null) "Edit QR code" else "Add QR code",
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                color = tokens.text
            )

            // Label Field (48dp tall)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Label", fontSize = 12.sp, color = tokens.textMuted)
                Surface(
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = tokens.secondaryBg,
                    border = BorderStroke(0.5.dp, tokens.cardBorder)
                ) {
                    Box(modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp), contentAlignment = Alignment.CenterStart) {
                        if (label.isEmpty()) {
                            Text("Main counter", fontSize = 13.sp, color = tokens.textMuted)
                        }
                        BasicTextField(
                            value = label,
                            onValueChange = { label = it },
                            singleLine = true,
                            cursorBrush = SolidColor(tokens.primary),
                            textStyle = TextStyle(fontSize = 13.sp, color = tokens.text),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            // UPI ID Field (48dp tall with inline validation)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("UPI ID", fontSize = 12.sp, color = tokens.textMuted)
                Surface(
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = tokens.secondaryBg,
                    border = BorderStroke(0.5.dp, if (upiId.isNotEmpty() && !isUpiValid) tokens.danger else tokens.cardBorder)
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.weight(1f)) {
                            if (upiId.isEmpty()) {
                                Text("name@bank", fontSize = 13.sp, color = tokens.textMuted)
                            }
                            BasicTextField(
                                value = upiId,
                                onValueChange = { upiId = it.trim().lowercase() },
                                singleLine = true,
                                cursorBrush = SolidColor(tokens.primary),
                                textStyle = TextStyle(fontSize = 13.sp, color = tokens.text),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        if (upiId.isNotEmpty()) {
                            if (isUpiValid) {
                                Icon(Icons.Outlined.CheckCircle, contentDescription = "Valid", tint = tokens.success, modifier = Modifier.size(18.dp))
                            } else {
                                Icon(Icons.Outlined.ErrorOutline, contentDescription = "Invalid", tint = tokens.danger, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
                if (upiId.isNotEmpty() && !isUpiValid) {
                    Text(
                        text = "Enter a valid UPI ID like name@bank.",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Normal,
                        color = tokens.danger
                    )
                }
            }

            // Linked Bank Account Dropdown (48dp tall)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Linked bank account", fontSize = 12.sp, color = tokens.textMuted)
                val currentBank = bankAccounts.find { it.id == selectedBankId }
                Box {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .clickable { bankDropdownExpanded = true },
                        shape = RoundedCornerShape(12.dp),
                        color = tokens.secondaryBg,
                        border = BorderStroke(0.5.dp, tokens.cardBorder)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = currentBank?.let { "${it.bankName} (••••${it.accountNumber.takeLast(4)})" } ?: "Select bank",
                                fontSize = 13.sp,
                                color = tokens.text
                            )
                            Icon(Icons.Outlined.ArrowDropDown, contentDescription = null, tint = tokens.textMuted)
                        }
                    }

                    DropdownMenu(
                        expanded = bankDropdownExpanded,
                        onDismissRequest = { bankDropdownExpanded = false },
                        modifier = Modifier.background(tokens.card).border(0.5.dp, tokens.cardBorder, RoundedCornerShape(12.dp))
                    ) {
                        bankAccounts.forEach { acc ->
                            DropdownMenuItem(
                                text = { Text("${acc.bankName} (••••${acc.accountNumber.takeLast(4)})", fontSize = 13.sp, color = tokens.text) },
                                onClick = {
                                    selectedBankId = acc.id
                                    bankDropdownExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            // Zero-permission Android Photo Picker for QR image attachment
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .clickable {
                            pickQrImageLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        }
                        .padding(horizontal = 4.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text(
                        text = if (qrImageUri != null) "Change QR image (Attached)" else "Attach QR image",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = tokens.link
                    )
                }
                if (qrImageUri != null) {
                    Box(
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .clickable { qrImageUri = null }
                            .padding(horizontal = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Remove", fontSize = 12.sp, color = tokens.danger)
                    }
                }
            }

            // Toggles
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Show on invoices and POS receipts", fontSize = 13.sp, color = tokens.text)
                Switch(
                    checked = showOnInvoices,
                    onCheckedChange = { showOnInvoices = it },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = tokens.primaryText,
                        checkedTrackColor = tokens.success,
                        uncheckedThumbColor = tokens.textMuted,
                        uncheckedTrackColor = tokens.secondaryBg
                    )
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Set as default QR", fontSize = 13.sp, color = tokens.text)
                Switch(
                    checked = setAsDefault,
                    onCheckedChange = { setAsDefault = it },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = tokens.primaryText,
                        checkedTrackColor = tokens.success,
                        uncheckedThumbColor = tokens.textMuted,
                        uncheckedTrackColor = tokens.secondaryBg
                    )
                )
            }

            // Buttons: Save QR (primary) and Cancel
            val canSave = label.isNotBlank() && isUpiValid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .clickable(onClick = onDismiss),
                    shape = RoundedCornerShape(12.dp),
                    color = tokens.secondaryBg,
                    border = BorderStroke(0.5.dp, tokens.cardBorder)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("Cancel", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = tokens.text)
                    }
                }

                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .clickable(enabled = canSave) {
                            if (canSave) {
                                val newQr = UpiQrCode(
                                    id = existingQr?.id ?: "QR-${System.currentTimeMillis()}-${java.util.UUID.randomUUID().toString().take(4)}",
                                    label = label.trim(),
                                    upiId = upiId.trim(),
                                    linkedBankId = selectedBankId,
                                    isDefault = setAsDefault,
                                    isEnabled = existingQr?.isEnabled ?: true,
                                    showOnInvoices = showOnInvoices,
                                    qrImageUri = qrImageUri
                                )
                                onSave(newQr)
                            }
                        },
                    shape = RoundedCornerShape(12.dp),
                    color = if (canSave) tokens.primary else tokens.primary.copy(alpha = 0.4f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("Save QR", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = tokens.primaryText)
                    }
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
        }
    }
}
