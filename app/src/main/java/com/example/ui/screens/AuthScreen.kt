package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.engine.FinancialEngine
import com.example.model.BusinessEntityType
import com.example.ui.theme.PabaruTheme
import com.example.ui.theme.danger
import com.example.ui.theme.dangerContainer
import com.example.ui.theme.onDangerContainer
import com.example.ui.theme.warning

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthScreen(
    onRegister: (String, BusinessEntityType, String, String, String, String) -> Unit,
    onSignIn: (String, String) -> Boolean,
    onSuperAdminAccess: (String) -> Boolean = { false },
    onDemoSignIn: () -> Boolean = { onSignIn("TL-7319", "1234") },
    serverAuthErrorMessage: String? = null
) {
    val tokens = PabaruTheme.tokens
    // 0: Sign In (appears first), 1: Register Business (next tab)
    var selectedTab by remember { mutableIntStateOf(0) }

    // Super Admin Master PIN Dialog State
    var showMasterPinDialog by remember { mutableStateOf(false) }
    var masterPinInput by remember { mutableStateOf("") }
    var masterPinError by remember { mutableStateOf<String?>(null) }

    // Sign In Form State (First tab)
    var loginIdentifier by remember { mutableStateOf("") } // Store Code or Phone or Email
    var loginPin by remember { mutableStateOf("") }
    var loginError by remember { mutableStateOf<String?>(null) }
    var showLoginPin by remember { mutableStateOf(false) }

    // Registration Form State (Next tab)
    var businessName by remember { mutableStateOf("") }
    var selectedEntityType by remember { mutableStateOf(BusinessEntityType.Proprietorship) }
    var signatoryName by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }
    var selectedStateCode by remember { mutableStateOf("27") } // Maharashtra default
    var registerError by remember { mutableStateOf<String?>(null) }

    var expandedEntityDropdown by remember { mutableStateOf(false) }
    var expandedStateDropdown by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(tokens.bg),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .widthIn(max = 600.dp)
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.96f),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = tokens.card),
            border = BorderStroke(1.dp, tokens.cardBorder),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header Logo & Branding
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(tokens.accent),
                    contentAlignment = Alignment.Center
                ) {
                    Text("₹", fontSize = 26.sp, fontWeight = FontWeight.Black, color = tokens.primaryText)
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text("Pabaru Billing", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = tokens.text)
                Text("Indian Business Ledger & GST Invoicing", fontSize = 11.sp, color = tokens.textMuted)

                Spacer(modifier = Modifier.height(16.dp))

                // Navigation Tabs: Sign In first, Register Business next
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(tokens.secondaryBg)
                        .padding(4.dp)
                ) {
                    // TAB 1: Sign In (First)
                    Button(
                        onClick = {
                            selectedTab = 0
                            loginError = null
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (selectedTab == 0) tokens.primary else Color.Transparent
                        ),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(vertical = 10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Login,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = if (selectedTab == 0) tokens.primaryText else tokens.text
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "Sign In",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (selectedTab == 0) tokens.primaryText else tokens.text
                            )
                        }
                    }

                    // TAB 2: Register Business (Next Tab)
                    Button(
                        onClick = {
                            selectedTab = 1
                            registerError = null
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (selectedTab == 1) tokens.primary else Color.Transparent
                        ),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(vertical = 10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Business,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = if (selectedTab == 1) tokens.primaryText else tokens.text
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "Register Business",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (selectedTab == 1) tokens.primaryText else tokens.text
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // ==================== TAB 0: SIGN IN (FIRST PAGE) ====================
                if (selectedTab == 0) {
                    Text(
                        "Welcome Back",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = tokens.text,
                        modifier = Modifier.align(Alignment.Start)
                    )
                    Text(
                        "Sign in with your Store Code (e.g. TL-7319) or registered mobile number.",
                        fontSize = 11.sp,
                        color = tokens.textMuted,
                        modifier = Modifier.align(Alignment.Start)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    val activeError = serverAuthErrorMessage ?: loginError
                    if (activeError != null) {
                        Surface(
                            color = tokens.dangerContainer,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                        ) {
                            Text(
                                activeError,
                                color = tokens.onDangerContainer,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }

                    // Store Code or Mobile
                    OutlinedTextField(
                        value = loginIdentifier,
                        onValueChange = {
                            loginIdentifier = it
                            loginError = null
                        },
                        label = { Text("Store Code or Mobile *") },
                        placeholder = { Text("TL-7319 or 9820011223") },
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Default.Store, contentDescription = null, tint = tokens.accent) },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // PIN
                    OutlinedTextField(
                        value = loginPin,
                        onValueChange = {
                            if (it.length <= 6) loginPin = it.filter { c -> c.isDigit() }
                            loginError = null
                        },
                        label = { Text("4–6 Digit Security PIN *") },
                        placeholder = { Text("e.g. 1234") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        visualTransformation = if (showLoginPin) VisualTransformation.None else PasswordVisualTransformation(),
                        leadingIcon = { Icon(Icons.Default.Key, contentDescription = null, tint = tokens.textMuted) },
                        trailingIcon = {
                            IconButton(
                                onClick = { showLoginPin = !showLoginPin },
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(
                                    if (showLoginPin) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = if (showLoginPin) "Hide PIN" else "Show PIN",
                                    tint = tokens.textMuted
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    Button(
                        onClick = {
                            val id = loginIdentifier.trim()
                            val pin = loginPin.trim()
                            if (id.isEmpty()) {
                                loginError = "Please enter your Store Code or Mobile Number."
                                return@Button
                            }
                            if (pin.isEmpty()) {
                                loginError = "Please enter your Security PIN."
                                return@Button
                            }
                            val success = onSignIn(id, pin)
                            if (!success && serverAuthErrorMessage == null) {
                                loginError = "Invalid credentials. Please verify your Store Code / PIN."
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = tokens.primary),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Login, contentDescription = null, tint = tokens.primaryText, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Sign In to Pabaru Ledger", color = tokens.primaryText, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Instant Demo Sign In Button
                    OutlinedButton(
                        onClick = {
                            loginIdentifier = "TL-7319"
                            loginPin = "1234"
                            val ok = onDemoSignIn()
                            if (!ok && serverAuthErrorMessage == null) {
                                loginError = "Demo credentials unavailable for your registered store. Please sign in with your Store Code and PIN."
                            }
                        },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        border = BorderStroke(1.dp, tokens.cardBorder),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.FlashOn, contentDescription = null, tint = tokens.accent, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Instant Demo Sign In (Store: TL-7319, PIN: 1234)", fontSize = 11.sp, color = tokens.accent)
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Super Admin Master PIN Portal Button
                    OutlinedButton(
                        onClick = {
                            masterPinInput = ""
                            masterPinError = null
                            showMasterPinDialog = true
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("super_admin_entry_button"),
                        border = BorderStroke(1.dp, tokens.warning.copy(alpha = 0.8f)),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(containerColor = tokens.warning.copy(alpha = 0.08f))
                    ) {
                        Icon(Icons.Default.AdminPanelSettings, contentDescription = null, tint = tokens.warning, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Super Admin Portal (Master PIN)", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = tokens.warning)
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Link to next tab: Register Business
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("New business? ", fontSize = 12.sp, color = tokens.textMuted)
                        Box(
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .clickable {
                                    selectedTab = 1
                                    registerError = null
                                }
                                .padding(horizontal = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "Register Business here",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = tokens.link
                            )
                        }
                    }
                }

                // ==================== TAB 1: REGISTER BUSINESS (NEXT TAB) ====================
                else {
                    Text(
                        "Register New Business",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = tokens.text,
                        modifier = Modifier.align(Alignment.Start)
                    )
                    Text(
                        "Set up your merchant profile, legal structure, and access credentials.",
                        fontSize = 11.sp,
                        color = tokens.textMuted,
                        modifier = Modifier.align(Alignment.Start)
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    if (registerError != null) {
                        Surface(
                            color = tokens.dangerContainer,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                        ) {
                            Text(
                                registerError ?: "",
                                color = tokens.onDangerContainer,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }

                    // Business Name
                    OutlinedTextField(
                        value = businessName,
                        onValueChange = {
                            businessName = it
                            registerError = null
                        },
                        label = { Text("Name of Business *") },
                        placeholder = { Text("e.g. Pabaru Enterprises") },
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Default.Storefront, contentDescription = null, tint = tokens.accent) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    // Company Type Selector
                    ExposedDropdownMenuBox(
                        expanded = expandedEntityDropdown,
                        onExpandedChange = { expandedEntityDropdown = !expandedEntityDropdown },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = selectedEntityType.displayName,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Company / Business Type *") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedEntityDropdown) },
                            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = expandedEntityDropdown,
                            onDismissRequest = { expandedEntityDropdown = false },
                            modifier = Modifier.background(tokens.card)
                        ) {
                            BusinessEntityType.values().forEach { entity ->
                                DropdownMenuItem(
                                    text = { Text(entity.displayName, fontSize = 12.sp, color = tokens.text) },
                                    onClick = {
                                        selectedEntityType = entity
                                        expandedEntityDropdown = false
                                    }
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))

                    // Dynamic Signatory Title (Proprietor / Partner / Director)
                    OutlinedTextField(
                        value = signatoryName,
                        onValueChange = {
                            signatoryName = it
                            registerError = null
                        },
                        label = { Text("Name of ${selectedEntityType.signatoryTitle} *") },
                        placeholder = { Text("Enter full legal name") },
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Default.Badge, contentDescription = null, tint = tokens.accent) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    // Phone Number
                    OutlinedTextField(
                        value = phone,
                        onValueChange = {
                            phone = it.filter { c -> c.isDigit() }.take(10)
                            registerError = null
                        },
                        label = { Text("Primary Mobile Number *") },
                        placeholder = { Text("10-digit number") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null, tint = tokens.textMuted) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    // State Code Selector
                    ExposedDropdownMenuBox(
                        expanded = expandedStateDropdown,
                        onExpandedChange = { expandedStateDropdown = !expandedStateDropdown },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val stateName = FinancialEngine.INDIAN_STATES.find { it.code == selectedStateCode }?.name ?: "Maharashtra"
                        OutlinedTextField(
                            value = "$selectedStateCode - $stateName",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Home State (for GST) *") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedStateDropdown) },
                            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = expandedStateDropdown,
                            onDismissRequest = { expandedStateDropdown = false },
                            modifier = Modifier.background(tokens.card)
                        ) {
                            FinancialEngine.INDIAN_STATES.forEach { st ->
                                DropdownMenuItem(
                                    text = { Text("${st.code} - ${st.name}", fontSize = 12.sp, color = tokens.text) },
                                    onClick = {
                                        selectedStateCode = st.code
                                        expandedStateDropdown = false
                                    }
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))

                    // 4–6 Digit Access PIN
                    OutlinedTextField(
                        value = pin,
                        onValueChange = {
                            if (it.length <= 6) pin = it.filter { c -> c.isDigit() }
                            registerError = null
                        },
                        label = { Text("Set 4–6 Digit Security PIN *") },
                        placeholder = { Text("e.g. 1234") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        visualTransformation = PasswordVisualTransformation(),
                        leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = tokens.textMuted) },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = {
                            if (businessName.isBlank()) {
                                registerError = "Please enter business name."
                                return@Button
                            }
                            if (signatoryName.isBlank()) {
                                registerError = "Please enter name of ${selectedEntityType.signatoryTitle}."
                                return@Button
                            }
                            if (phone.length < 10) {
                                registerError = "Please enter a valid 10-digit mobile number."
                                return@Button
                            }
                            if (pin.length !in 4..6) {
                                registerError = "Please set a 4 to 6 digit security PIN."
                                return@Button
                            }
                            onRegister(
                                businessName.trim(),
                                selectedEntityType,
                                signatoryName.trim(),
                                phone.trim(),
                                pin.trim(),
                                selectedStateCode
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = tokens.primary),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = tokens.primaryText, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Create Business Ledger", color = tokens.primaryText, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Switch back to Sign In
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Already registered? ", fontSize = 12.sp, color = tokens.textMuted)
                        Box(
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .clickable {
                                    selectedTab = 0
                                    loginError = null
                                }
                                .padding(horizontal = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "Sign In here",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = tokens.link
                            )
                        }
                    }
                }
            }
        }
    }

    if (showMasterPinDialog) {
        AlertDialog(
            onDismissRequest = { showMasterPinDialog = false },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Security, contentDescription = null, tint = tokens.warning)
                    Text("Super Admin Master Access", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = tokens.text)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Enter the 4–6 digit Master Security PIN to manage local business tenants, user activations, and module permissions.",
                        fontSize = 12.sp,
                        color = tokens.textMuted
                    )
                    if (masterPinError != null) {
                        Text(
                            masterPinError ?: "",
                            fontSize = 11.5.sp,
                            color = tokens.danger,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    OutlinedTextField(
                        value = masterPinInput,
                        onValueChange = {
                            if (it.length <= 6) masterPinInput = it.filter { c -> c.isDigit() }
                            masterPinError = null
                        },
                        label = { Text("Master PIN") },
                        placeholder = { Text("••••") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("master_pin_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val pin = masterPinInput.trim()
                        if (onSuperAdminAccess(pin)) {
                            showMasterPinDialog = false
                        } else {
                            masterPinError = "Invalid Master Security PIN. Access denied."
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = tokens.warning),
                    modifier = Modifier.heightIn(min = 48.dp).testTag("master_pin_confirm_button")
                ) {
                    Text("Unlock Super Admin", color = tokens.primaryText, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showMasterPinDialog = false },
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text("Cancel", color = tokens.textMuted)
                }
            },
            containerColor = tokens.card
        )
    }
}

