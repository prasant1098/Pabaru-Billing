package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.engine.FinancialEngine
import com.example.model.BankAccount
import com.example.ui.theme.PabaruTheme
import com.example.ui.theme.danger
import com.example.ui.theme.dangerContainer
import com.example.ui.theme.info
import com.example.ui.theme.infoContainer
import com.example.ui.theme.success
import com.example.ui.theme.successContainer

/**
 * Adjust Cash Drawer Bottom Sheet
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdjustCashBottomSheet(
    onDismiss: () -> Unit,
    onSave: (Long, Boolean, String) -> Unit = { _, _, _ -> },
    onSavePaise: (Long, Boolean, String) -> Unit = { paise, isAdd, note -> onSave(paise / 100L, isAdd, note) }
) {
    val tokens = PabaruTheme.tokens
    var amountStr by remember { mutableStateOf("") }
    var isAdd by remember { mutableStateOf(true) }
    var noteStr by remember { mutableStateOf("") }

    val amountPaise = FinancialEngine.parsePaiseFromRupees(amountStr)

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
            Text("Adjust cash in drawer", fontSize = 18.sp, fontWeight = FontWeight.Medium, color = tokens.text)

            // Segmented Add / Remove
            Row(
                modifier = Modifier.fillMaxWidth().height(48.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    modifier = Modifier.weight(1f).fillMaxHeight().clickable { isAdd = true },
                    shape = RoundedCornerShape(12.dp),
                    color = if (isAdd) tokens.successContainer else tokens.secondaryBg,
                    border = BorderStroke(0.5.dp, if (isAdd) tokens.success else tokens.cardBorder)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("Add cash", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = if (isAdd) tokens.success else tokens.textMuted)
                    }
                }

                Surface(
                    modifier = Modifier.weight(1f).fillMaxHeight().clickable { isAdd = false },
                    shape = RoundedCornerShape(12.dp),
                    color = if (!isAdd) tokens.dangerContainer else tokens.secondaryBg,
                    border = BorderStroke(0.5.dp, if (!isAdd) tokens.danger else tokens.cardBorder)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("Remove cash", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = if (!isAdd) tokens.danger else tokens.textMuted)
                    }
                }
            }

            // Amount field
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Amount", fontSize = 12.sp, color = tokens.textMuted)
                Surface(
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = tokens.secondaryBg,
                    border = BorderStroke(0.5.dp, tokens.cardBorder)
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("₹", fontSize = 16.sp, fontWeight = FontWeight.Medium, color = tokens.text)
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(modifier = Modifier.weight(1f)) {
                            if (amountStr.isEmpty()) {
                                Text("0.00", fontSize = 14.sp, color = tokens.textMuted)
                            }
                            BasicTextField(
                                value = amountStr,
                                onValueChange = { amountStr = FinancialEngine.sanitizeDecimalInput(it) },
                                singleLine = true,
                                cursorBrush = SolidColor(tokens.primary),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                textStyle = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium, color = tokens.text),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }

            // Note field
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Note", fontSize = 12.sp, color = tokens.textMuted)
                Surface(
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = tokens.secondaryBg,
                    border = BorderStroke(0.5.dp, tokens.cardBorder)
                ) {
                    Box(modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp), contentAlignment = Alignment.CenterStart) {
                        if (noteStr.isEmpty()) {
                            Text("e.g. Petty cash refill, bank withdrawal", fontSize = 13.sp, color = tokens.textMuted)
                        }
                        BasicTextField(
                            value = noteStr,
                            onValueChange = { noteStr = it },
                            singleLine = true,
                            cursorBrush = SolidColor(tokens.primary),
                            textStyle = TextStyle(fontSize = 13.sp, color = tokens.text),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            val canSave = amountPaise > 0L
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clickable(enabled = canSave) {
                        if (amountPaise > 0L) {
                            onSavePaise(amountPaise, isAdd, noteStr.trim().ifEmpty { if (isAdd) "Cash addition" else "Cash withdrawal" })
                        }
                    },
                shape = RoundedCornerShape(12.dp),
                color = if (canSave) tokens.primary else tokens.primary.copy(alpha = 0.4f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("Save cash adjustment", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = tokens.primaryText)
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
        }
    }
}

/**
 * Account Detail Sheet with masked details revealed on tapping "Show details" and copy buttons
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountDetailBottomSheet(
    account: BankAccount,
    onDismiss: () -> Unit,
    liveBalancePaise: Long = account.openingBalancePaise
) {
    val context = LocalContext.current
    val tokens = PabaruTheme.tokens
    var isRevealed by remember { mutableStateOf(false) }

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
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                    Text(account.bankName, fontSize = 18.sp, fontWeight = FontWeight.Medium, color = tokens.text)
                    Text(account.accountName, fontSize = 12.sp, color = tokens.textMuted)
                }
                Text(
                    text = FinancialEngine.formatPaise(liveBalancePaise),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (liveBalancePaise >= 0L) tokens.success else tokens.danger
                )
            }

            // Reveal sensitive details toggle button
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clickable { isRevealed = !isRevealed },
                shape = RoundedCornerShape(12.dp),
                color = tokens.secondaryBg,
                border = BorderStroke(0.5.dp, tokens.cardBorder)
            ) {
                Row(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (isRevealed) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                        contentDescription = null,
                        tint = tokens.textMuted,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isRevealed) "Hide details" else "Show details",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = tokens.text
                    )
                }
            }

            // Account Number row
            val maskedAcc = if (account.accountNumber.length <= 4) account.accountNumber else "••••••••${account.accountNumber.takeLast(4)}"
            DetailRowWithCopy(
                label = "Account number",
                value = if (isRevealed) account.accountNumber else maskedAcc,
                onCopy = { copyToClipboard(context, account.accountNumber, "Account number") }
            )

            // IFSC Code row
            DetailRowWithCopy(
                label = "IFSC code",
                value = account.ifscCode.ifBlank { "Not specified" },
                onCopy = {
                    if (account.ifscCode.isNotBlank()) {
                        copyToClipboard(context, account.ifscCode, "IFSC code")
                    }
                }
            )

            // UPI ID row
            DetailRowWithCopy(
                label = "UPI ID",
                value = account.upiId.ifBlank { "Not configured" },
                onCopy = {
                    if (account.upiId.isNotBlank()) {
                        copyToClipboard(context, account.upiId, "UPI ID")
                    }
                }
            )

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clickable(onClick = onDismiss),
                shape = RoundedCornerShape(12.dp),
                color = tokens.secondaryBg,
                border = BorderStroke(0.5.dp, tokens.cardBorder)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("Close", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = tokens.text)
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
        }
    }
}

@Composable
fun DetailRowWithCopy(
    label: String,
    value: String,
    onCopy: () -> Unit
) {
    val tokens = PabaruTheme.tokens
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = tokens.secondaryBg,
        border = BorderStroke(0.5.dp, tokens.cardBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(label, fontSize = 12.sp, color = tokens.textMuted)
                Spacer(modifier = Modifier.height(2.dp))
                Text(value, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = tokens.text)
            }
            IconButton(onClick = onCopy, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Outlined.ContentCopy, contentDescription = "Copy $label", tint = tokens.textMuted, modifier = Modifier.size(16.dp))
            }
        }
    }
}

/**
 * Add Bank Account Bottom Sheet
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddBankAccountBottomSheet(
    onDismiss: () -> Unit,
    onSave: (BankAccount) -> Unit
) {
    val tokens = PabaruTheme.tokens
    var bankName by remember { mutableStateOf("") }
    var accountType by remember { mutableStateOf("Current Account") }
    var accountNumber by remember { mutableStateOf("") }
    var ifscCode by remember { mutableStateOf("") }
    var upiId by remember { mutableStateOf("") }
    var openingBalRupees by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = tokens.card,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Add bank account", fontSize = 18.sp, fontWeight = FontWeight.Medium, color = tokens.text)

            BankInputField(label = "Bank name", value = bankName, onValueChange = { bankName = it }, placeholder = "e.g. Kotak Mahindra Bank")
            BankInputField(label = "Account number", value = accountNumber, onValueChange = { accountNumber = it.filter { c -> c.isDigit() } }, placeholder = "Full account number", keyboardType = KeyboardType.Number)
            BankInputField(label = "IFSC code", value = ifscCode, onValueChange = { ifscCode = it.uppercase() }, placeholder = "e.g. KKBK0000999")
            BankInputField(label = "UPI ID (optional)", value = upiId, onValueChange = { upiId = it.trim().lowercase() }, placeholder = "name@bank")
            BankInputField(label = "Opening balance (₹)", value = openingBalRupees, onValueChange = { openingBalRupees = FinancialEngine.sanitizeDecimalInput(it) }, placeholder = "0.00", keyboardType = KeyboardType.Decimal)

            val canSave = bankName.isNotBlank() && accountNumber.length >= 6 && ifscCode.length >= 4
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clickable(enabled = canSave) {
                        if (canSave) {
                            val balPaise = FinancialEngine.parsePaiseFromRupees(openingBalRupees)
                            val newAcc = BankAccount(
                                id = "BNK-${System.currentTimeMillis()}-${java.util.UUID.randomUUID().toString().take(4)}",
                                accountName = "${bankName.trim()} $accountType",
                                bankName = bankName.trim(),
                                accountNumber = accountNumber.trim(),
                                ifscCode = ifscCode.trim(),
                                upiId = upiId.trim(),
                                openingBalancePaise = balPaise,
                                isDefaultForQr = false
                            )
                            onSave(newAcc)
                        }
                    },
                shape = RoundedCornerShape(12.dp),
                color = if (canSave) tokens.primary else tokens.primary.copy(alpha = 0.4f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("Save account", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = tokens.primaryText)
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
        }
    }
}

@Composable
fun BankInputField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    keyboardType: KeyboardType = KeyboardType.Text
) {
    val tokens = PabaruTheme.tokens
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(label, fontSize = 12.sp, color = tokens.textMuted)
        Surface(
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(12.dp),
            color = tokens.secondaryBg,
            border = BorderStroke(0.5.dp, tokens.cardBorder)
        ) {
            Box(modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp), contentAlignment = Alignment.CenterStart) {
                if (value.isEmpty()) {
                    Text(placeholder, fontSize = 13.sp, color = tokens.textMuted)
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    singleLine = true,
                    cursorBrush = SolidColor(tokens.primary),
                    keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                    textStyle = TextStyle(fontSize = 13.sp, color = tokens.text),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

/**
 * Transfer between accounts sheet
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransferBottomSheet(
    bankAccounts: List<BankAccount>,
    onDismiss: () -> Unit,
    onTransfer: (String, String, Long, String) -> Unit = { _, _, _, _ -> },
    onTransferPaise: (String, String, Long, String) -> Unit = { from, to, paise, note -> onTransfer(from, to, paise / 100L, note) }
) {
    val tokens = PabaruTheme.tokens
    var isCashToBank by remember { mutableStateOf(true) }
    var selectedBankId by remember { mutableStateOf(bankAccounts.firstOrNull()?.id ?: "") }
    var bankDropdownExpanded by remember { mutableStateOf(false) }
    var amountStr by remember { mutableStateOf("") }
    var noteStr by remember { mutableStateOf("") }

    val fromAccount = if (isCashToBank) "CASH" else selectedBankId
    val toAccount = if (isCashToBank) selectedBankId else "CASH"
    val selectedBank = bankAccounts.find { it.id == selectedBankId } ?: bankAccounts.firstOrNull()
    val amountPaise = FinancialEngine.parsePaiseFromRupees(amountStr)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = tokens.card,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Transfer between accounts", fontSize = 18.sp, fontWeight = FontWeight.Medium, color = tokens.text)

            // Direction Toggle Chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .clickable { isCashToBank = true },
                    shape = RoundedCornerShape(8.dp),
                    color = if (isCashToBank) tokens.accentTint else tokens.secondaryBg,
                    border = BorderStroke(1.dp, if (isCashToBank) tokens.primary else tokens.cardBorder)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("Cash → Bank (Deposit)", fontSize = 11.5.sp, fontWeight = FontWeight.Medium, color = if (isCashToBank) tokens.primary else tokens.textMuted)
                    }
                }

                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .clickable { isCashToBank = false },
                    shape = RoundedCornerShape(8.dp),
                    color = if (!isCashToBank) tokens.infoContainer else tokens.secondaryBg,
                    border = BorderStroke(1.dp, if (!isCashToBank) tokens.info else tokens.cardBorder)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("Bank → Cash (Withdraw)", fontSize = 11.5.sp, fontWeight = FontWeight.Medium, color = if (!isCashToBank) tokens.info else tokens.textMuted)
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("From", fontSize = 12.sp, color = tokens.textMuted)
                Box {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .clickable(enabled = !isCashToBank && bankAccounts.size > 1) {
                                bankDropdownExpanded = true
                            },
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
                                text = if (isCashToBank) "Cash drawer (Physical Cash)" else (selectedBank?.bankName ?: "No bank account available"),
                                fontSize = 13.sp,
                                color = tokens.text
                            )
                            if (!isCashToBank && bankAccounts.size > 1) {
                                Icon(Icons.Outlined.ArrowDropDown, contentDescription = "Select bank", tint = tokens.textMuted)
                            }
                        }
                    }
                    if (!isCashToBank) {
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
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("To", fontSize = 12.sp, color = tokens.textMuted)
                Box {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .clickable(enabled = isCashToBank && bankAccounts.size > 1) {
                                bankDropdownExpanded = true
                            },
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
                                text = if (!isCashToBank) "Cash drawer (Physical Cash)" else (selectedBank?.bankName ?: "No bank account available"),
                                fontSize = 13.sp,
                                color = tokens.text
                            )
                            if (isCashToBank && bankAccounts.size > 1) {
                                Icon(Icons.Outlined.ArrowDropDown, contentDescription = "Select bank", tint = tokens.textMuted)
                            }
                        }
                    }
                    if (isCashToBank) {
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
            }

            BankInputField(label = "Amount (₹)", value = amountStr, onValueChange = { amountStr = FinancialEngine.sanitizeDecimalInput(it) }, placeholder = "0.00", keyboardType = KeyboardType.Decimal)
            BankInputField(label = "Transfer note", value = noteStr, onValueChange = { noteStr = it }, placeholder = if (isCashToBank) "e.g. Bank cash deposit" else "e.g. Counter cash withdrawal")

            val canSave = amountPaise > 0L && selectedBankId.isNotBlank()
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clickable(enabled = canSave) {
                        if (amountPaise > 0L && selectedBankId.isNotBlank()) {
                            val defaultNote = if (isCashToBank) "Cash deposit" else "Cash withdrawal"
                            onTransferPaise(fromAccount, toAccount, amountPaise, noteStr.trim().ifBlank { defaultNote })
                        }
                    },
                shape = RoundedCornerShape(12.dp),
                color = if (canSave) tokens.primary else tokens.primary.copy(alpha = 0.4f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("Complete transfer", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = tokens.primaryText)
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
        }
    }
}
