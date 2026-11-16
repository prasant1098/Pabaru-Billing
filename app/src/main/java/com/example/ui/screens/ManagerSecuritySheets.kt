package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.PabaruTheme
import com.example.ui.theme.danger
import com.example.ui.theme.dangerContainer
import com.example.ui.theme.onDangerContainer
import com.example.ui.theme.onSuccessContainer
import com.example.ui.theme.success
import com.example.ui.theme.successContainer
import com.example.ui.theme.warning
import com.example.viewmodel.PabaruUiState
import java.util.Locale
import kotlinx.coroutines.delay

/**
 * Staff Restricted QR Dialog
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StaffRestrictedQrSheet(onDismiss: () -> Unit) {
    val tokens = PabaruTheme.tokens
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
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                modifier = Modifier.size(48.dp),
                shape = CircleShape,
                color = tokens.secondaryBg,
                border = BorderStroke(0.5.dp, tokens.cardBorder)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.Lock, contentDescription = null, tint = tokens.warning, modifier = Modifier.size(24.dp))
                }
            }

            Text(
                text = "Manager access needed",
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                color = tokens.text
            )

            Text(
                text = "Ask your manager to update QR settings.",
                fontSize = 13.sp,
                fontWeight = FontWeight.Normal,
                color = tokens.textMuted,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(6.dp))

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clickable(onClick = onDismiss),
                shape = RoundedCornerShape(12.dp),
                color = tokens.primary
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("Got it", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = tokens.primaryText)
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

/**
 * First-time Manager PIN Setup Bottom Sheet (3 steps)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FirstTimePinSetupSheet(
    onDismiss: () -> Unit,
    onPinCreated: (String, Boolean) -> Unit
) {
    val tokens = PabaruTheme.tokens
    var step by remember { mutableStateOf(1) } // 1: Intro, 2: Create, 3: Confirm, 4: Success
    var enteredPin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var validationError by remember { mutableStateOf<String?>(null) }
    var enableFingerprint by remember { mutableStateOf(true) }

    fun isSimplePin(pin: String): Boolean {
        if (pin.length != 4) return false
        val allSame = pin.all { it == pin[0] }
        val seqAsc = pin == "0123" || pin == "1234" || pin == "2345" || pin == "3456" || pin == "4567" || pin == "5678" || pin == "6789"
        val seqDesc = pin == "3210" || pin == "4321" || pin == "5432" || pin == "6543" || pin == "7654" || pin == "8765" || pin == "9876"
        return allSame || seqAsc || seqDesc
    }

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
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Three progress dots
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                for (i in 1..3) {
                    val isActive = step >= i
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(if (isActive) tokens.primary else tokens.cardBorder)
                    )
                }
            }

            when (step) {
                1 -> {
                    // Step 1: Intro
                    Surface(
                        modifier = Modifier.size(52.dp),
                        shape = CircleShape,
                        color = tokens.secondaryBg,
                        border = BorderStroke(0.5.dp, tokens.cardBorder)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Outlined.Shield, contentDescription = null, tint = tokens.success, modifier = Modifier.size(28.dp))
                        }
                    }

                    Text("Protect your QR settings", fontSize = 18.sp, fontWeight = FontWeight.Medium, color = tokens.text)
                    Text(
                        "Create a manager PIN so only you can change payment details.",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Normal,
                        color = tokens.textMuted,
                        textAlign = TextAlign.Center
                    )

                    Text(
                        "Your PIN stays on this device and works without internet.",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Normal,
                        color = tokens.textMuted,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .clickable { step = 2 },
                        shape = RoundedCornerShape(12.dp),
                        color = tokens.primary
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("Create PIN", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = tokens.primaryText)
                        }
                    }

                    Box(
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .clickable(onClick = onDismiss)
                            .padding(horizontal = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Not now",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = tokens.textMuted
                        )
                    }
                }

                2 -> {
                    // Step 2: Create PIN
                    Text("Create a 4-digit PIN", fontSize = 18.sp, fontWeight = FontWeight.Medium, color = tokens.text)
                    Text("Avoid easy PINs like 1234.", fontSize = 12.sp, fontWeight = FontWeight.Normal, color = tokens.textMuted)

                    // Pin dot indicators
                    PinDotDisplay(length = enteredPin.length)

                    if (validationError != null) {
                        Text(validationError ?: "", fontSize = 12.sp, fontWeight = FontWeight.Normal, color = tokens.danger)
                    }

                    NumericKeypad(
                        onDigit = { d ->
                            if (enteredPin.length < 4) {
                                val next = enteredPin + d
                                enteredPin = next
                                validationError = null
                                if (next.length == 4) {
                                    if (isSimplePin(next)) {
                                        validationError = "Choose a PIN that's harder to guess."
                                        enteredPin = ""
                                    } else {
                                        step = 3
                                    }
                                }
                            }
                        },
                        onBackspace = {
                            if (enteredPin.isNotEmpty()) enteredPin = enteredPin.dropLast(1)
                            validationError = null
                        }
                    )
                }

                3 -> {
                    // Step 3: Confirm PIN
                    Text("Confirm your PIN", fontSize = 18.sp, fontWeight = FontWeight.Medium, color = tokens.text)
                    Text("Re-enter the 4 digits to confirm.", fontSize = 12.sp, fontWeight = FontWeight.Normal, color = tokens.textMuted)

                    PinDotDisplay(length = confirmPin.length)

                    if (validationError != null) {
                        Text(validationError ?: "", fontSize = 12.sp, fontWeight = FontWeight.Normal, color = tokens.danger)
                    }

                    NumericKeypad(
                        onDigit = { d ->
                            if (confirmPin.length < 4) {
                                val next = confirmPin + d
                                confirmPin = next
                                if (next.length == 4) {
                                    if (next == enteredPin) {
                                        step = 4
                                    } else {
                                        validationError = "PINs don't match. Try again."
                                        confirmPin = ""
                                        enteredPin = ""
                                        step = 2
                                    }
                                }
                            }
                        },
                        onBackspace = {
                            if (confirmPin.isNotEmpty()) confirmPin = confirmPin.dropLast(1)
                            validationError = null
                        }
                    )
                }

                4 -> {
                    // Step 4: Success
                    Surface(
                        modifier = Modifier.size(52.dp),
                        shape = CircleShape,
                        color = tokens.successContainer,
                        border = BorderStroke(0.5.dp, tokens.success)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Outlined.Check, contentDescription = null, tint = tokens.onSuccessContainer, modifier = Modifier.size(28.dp))
                        }
                    }

                    Text("Manager PIN set", fontSize = 18.sp, fontWeight = FontWeight.Medium, color = tokens.text)
                    Text("Your payment settings are now protected.", fontSize = 13.sp, color = tokens.textMuted)

                    // Optional toggle for fingerprint
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = tokens.secondaryBg,
                        border = BorderStroke(0.5.dp, tokens.cardBorder)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Outlined.Fingerprint, contentDescription = null, tint = tokens.text, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(10.dp))
                                Text("Use fingerprint to unlock", fontSize = 13.sp, fontWeight = FontWeight.Normal, color = tokens.text)
                            }
                            Switch(
                                checked = enableFingerprint,
                                onCheckedChange = { enableFingerprint = it },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = tokens.primaryText,
                                    checkedTrackColor = tokens.success,
                                    uncheckedThumbColor = tokens.textMuted,
                                    uncheckedTrackColor = tokens.card
                                )
                            )
                        }
                    }

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .clickable { onPinCreated(enteredPin, enableFingerprint) },
                        shape = RoundedCornerShape(12.dp),
                        color = tokens.primary
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("Open Manage QR", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = tokens.primaryText)
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
        }
    }
}

/**
 * Manager PIN Check Bottom Sheet with lockout countdown
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManagerPinCheckSheet(
    uiState: PabaruUiState,
    onDismiss: () -> Unit,
    onVerifyPin: (String) -> Boolean,
    onUnlockWithFingerprint: () -> Unit,
    onForgotPin: () -> Unit
) {
    val tokens = PabaruTheme.tokens
    var enteredPin by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var remainingSeconds by remember { mutableStateOf(0L) }

    LaunchedEffect(uiState.managerLockoutUntilTimestamp) {
        while (true) {
            val now = System.currentTimeMillis()
            if (uiState.managerLockoutUntilTimestamp > now) {
                remainingSeconds = (uiState.managerLockoutUntilTimestamp - now + 999L) / 1000L
                delay(1000L)
            } else {
                remainingSeconds = 0L
                break
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = {
            enteredPin = ""
            onDismiss()
        },
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
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("Enter manager PIN", fontSize = 18.sp, fontWeight = FontWeight.Medium, color = tokens.text)

            PinDotDisplay(length = enteredPin.length)

            if (remainingSeconds > 0) {
                val mins = remainingSeconds / 60
                val secs = remainingSeconds % 60
                Text(
                    text = String.format(Locale.ENGLISH, "Too many attempts. Try again in %d:%02d.", mins, secs),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Normal,
                    color = tokens.danger
                )
            } else if (errorMessage != null) {
                Text(errorMessage ?: "", fontSize = 12.sp, fontWeight = FontWeight.Normal, color = tokens.danger)
            }

            NumericKeypad(
                enabled = remainingSeconds <= 0,
                onDigit = { d ->
                    if (enteredPin.length < 4 && remainingSeconds <= 0) {
                        val next = enteredPin + d
                        enteredPin = next
                        errorMessage = null
                        if (next.length == 4) {
                            val verified = onVerifyPin(next)
                            if (!verified) {
                                errorMessage = "That PIN doesn't match. Try again."
                                enteredPin = ""
                            }
                        }
                    }
                },
                onBackspace = {
                    if (enteredPin.isNotEmpty()) enteredPin = enteredPin.dropLast(1)
                    errorMessage = null
                },
                onFingerprint = if (uiState.useFingerprintForManager) {
                    { onUnlockWithFingerprint() }
                } else null
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .clickable(onClick = onForgotPin)
                        .padding(horizontal = 4.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text(
                        text = "Forgot PIN?",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = tokens.link
                    )
                }

                Text(
                    text = "Offline protected",
                    fontSize = 12.sp,
                    color = tokens.textMuted
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
        }
    }
}

/**
 * Forgot PIN Bottom Sheet (device security reset)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ForgotPinSheet(
    onDismiss: () -> Unit,
    onResetConfirm: () -> Unit
) {
    val tokens = PabaruTheme.tokens
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
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                modifier = Modifier.size(48.dp),
                shape = CircleShape,
                color = tokens.secondaryBg,
                border = BorderStroke(0.5.dp, tokens.cardBorder)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.Security, contentDescription = null, tint = tokens.warning, modifier = Modifier.size(24.dp))
                }
            }

            Text("Reset manager PIN", fontSize = 18.sp, fontWeight = FontWeight.Medium, color = tokens.text)
            Text(
                "You can reset your PIN by confirming device screen lock (fingerprint, pattern, or device PIN). This reset will be recorded in the audit log.",
                fontSize = 13.sp,
                color = tokens.textMuted,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(4.dp))

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clickable(onClick = onResetConfirm),
                shape = RoundedCornerShape(12.dp),
                color = tokens.primary
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("Confirm device lock and reset", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = tokens.primaryText)
                }
            }

            Box(
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .clickable(onClick = onDismiss)
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Cancel",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = tokens.textMuted
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
        }
    }
}

/**
 * Confirmation dialog for sensitive QR operations
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfirmSensitiveChangeSheet(
    title: String,
    promptMessage: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val tokens = PabaruTheme.tokens
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
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                modifier = Modifier.size(48.dp),
                shape = CircleShape,
                color = tokens.dangerContainer,
                border = BorderStroke(0.5.dp, tokens.danger)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.WarningAmber, contentDescription = null, tint = tokens.onDangerContainer, modifier = Modifier.size(24.dp))
                }
            }

            Text(text = title, fontSize = 18.sp, fontWeight = FontWeight.Medium, color = tokens.text)
            Text(
                text = promptMessage,
                fontSize = 13.sp,
                color = tokens.textMuted,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(6.dp))

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
                        .clickable(onClick = onConfirm),
                    shape = RoundedCornerShape(12.dp),
                    color = tokens.primary
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("Confirm change", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = tokens.primaryText)
                    }
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
        }
    }
}

// --------------------------------------------------------------------
// Reusable Sub-components (Dots, Keypad)
// --------------------------------------------------------------------

@Composable
fun PinDotDisplay(length: Int) {
    val tokens = PabaruTheme.tokens
    Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.padding(vertical = 12.dp)
    ) {
        for (i in 0 until 4) {
            val filled = i < length
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(if (filled) tokens.success else tokens.secondaryBg)
                    .border(1.dp, if (filled) tokens.success else tokens.cardBorder, CircleShape)
            )
        }
    }
}

@Composable
fun NumericKeypad(
    enabled: Boolean = true,
    onDigit: (String) -> Unit,
    onBackspace: () -> Unit,
    onFingerprint: (() -> Unit)? = null
) {
    val tokens = PabaruTheme.tokens
    val rows = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf("fingerprint", "0", "backspace")
    )

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        rows.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                row.forEach { item ->
                    val isInteractive = enabled && (item != "fingerprint" || onFingerprint != null)
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                            .clickable(enabled = isInteractive) {
                                when (item) {
                                    "backspace" -> onBackspace()
                                    "fingerprint" -> onFingerprint?.invoke()
                                    else -> onDigit(item)
                                }
                            },
                        shape = RoundedCornerShape(12.dp),
                        color = if (item == "fingerprint" && onFingerprint == null) Color.Transparent else tokens.secondaryBg,
                        border = if (item != "fingerprint" || onFingerprint != null) BorderStroke(0.5.dp, tokens.cardBorder) else null
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            when (item) {
                                "backspace" -> Icon(Icons.Outlined.Backspace, contentDescription = "Backspace", tint = tokens.textMuted, modifier = Modifier.size(20.dp))
                                "fingerprint" -> {
                                    if (onFingerprint != null) {
                                        Icon(Icons.Outlined.Fingerprint, contentDescription = "Fingerprint", tint = tokens.success, modifier = Modifier.size(24.dp))
                                    }
                                }
                                else -> Text(item, fontSize = 20.sp, fontWeight = FontWeight.Medium, color = tokens.text)
                            }
                        }
                    }
                }
            }
        }
    }
}
