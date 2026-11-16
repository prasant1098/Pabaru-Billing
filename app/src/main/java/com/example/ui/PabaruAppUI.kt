package com.example.ui

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.engine.ExportEngine
import com.example.engine.FinancialEngine
import com.example.model.*
import com.example.ui.components.PulsingProBadge
import com.example.ui.components.ThemePickerBottomSheet
import com.example.ui.screens.*
import com.example.ui.theme.PabaruTheme
import com.example.ui.theme.danger
import com.example.ui.theme.dangerContainer
import com.example.ui.theme.info
import com.example.ui.theme.infoContainer
import com.example.ui.theme.onDangerContainer
import com.example.ui.theme.onInfoContainer
import com.example.ui.theme.onSuccessContainer
import com.example.ui.theme.success
import com.example.ui.theme.successContainer
import com.example.ui.theme.warning
import com.example.ui.theme.warningContainer
import com.example.viewmodel.PabaruUiState
import com.example.viewmodel.PabaruViewModel

import androidx.compose.runtime.saveable.rememberSaveable

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PabaruAppUI(viewModel: PabaruViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    var selectedTab by rememberSaveable { mutableIntStateOf(0) } // 0: Dash, 1: Invoice, 2: Quick, 3: Daybook, 4: More
    var moreTab by rememberSaveable { mutableStateOf<String?>(null) } // "parties", "stock", "banking", "reports", "security", "desktop"
    var isGstPaidUnlocked by rememberSaveable { mutableStateOf(false) }
    var inventoryInitialFilterLowStock by rememberSaveable { mutableStateOf(false) }

    val activeLowStockCount = remember(uiState.products, uiState.lowStockThreshold) {
        val thresholdMilli = uiState.lowStockThreshold.toLong() * 1000L
        uiState.products.count {
            !it.isArchived && (it.currentStockMilli <= it.reorderLevelMilli || it.currentStockMilli <= thresholdMilli)
        }
    }

    var voidingTxnId by rememberSaveable { mutableStateOf<String?>(null) }
    val showVoidDialog = uiState.transactions.find { it.id == voidingTxnId }
    var voidReasonInput by rememberSaveable { mutableStateOf("") }
    var showSetPinDialog by rememberSaveable { mutableStateOf(false) }
    var pinInput by rememberSaveable { mutableStateOf("") }
    var unlockPinInput by rememberSaveable { mutableStateOf("") }
    var showThemeBottomSheet by rememberSaveable { mutableStateOf(false) }
    var showProfileDropdown by rememberSaveable { mutableStateOf(false) }

    val tokens = PabaruTheme.tokens
    val containerBg = tokens.bg

    // Cold-start splash screen while offline Room database loads
    if (!uiState.isDatabaseReady) {
        com.example.ui.components.PabaruSplashScreen(tokens = tokens)
        return
    }

    // Super Admin Management Session
    if (uiState.isSuperAdminSession) {
        SuperAdminScreen(
            managedUsers = uiState.managedUsers,
            onToggleUserStatus = { storeCode, isActive -> viewModel.toggleUserActivation(storeCode, isActive) },
            onUpdateUserBenefits = { code, gst, recon, invc, inv, cma, staff, export, ovr ->
                viewModel.setUserBenefits(code, gst, recon, invc, inv, cma, staff, export, ovr)
            },
            onAddNewUser = { newUser -> viewModel.addNewManagedUser(newUser) },
            onDeleteUser = { code -> viewModel.deleteManagedUser(code) },
            onImpersonateUser = { code -> viewModel.impersonateUser(code) },
            onExitSuperAdmin = { viewModel.exitSuperAdminMode() },
            syncStatus = uiState.localSyncStatus,
            lastSyncTime = uiState.lastSyncTimestamp,
            isSyncing = uiState.isSyncing,
            onTriggerSync = { viewModel.triggerLocalSync() },
            onBulkToggleUserStatus = { storeCodes, isActive -> viewModel.bulkToggleUserStatus(storeCodes, isActive) },
            onBulkUpdateUserBenefits = { codes, gst, recon, invc, inv, cma, staff, export, ovr ->
                viewModel.bulkUpdateUserBenefits(codes, gst, recon, invc, inv, cma, staff, export, ovr)
            }
        )
        return
    }

    // First Turn / Sign-in & Registration Screen
    if (!uiState.profile.isRegistered) {
        AuthScreen(
            onRegister = { bName, entityType, sigName, phone, pin, stateCode ->
                viewModel.registerBusiness(bName, entityType, sigName, phone, pin, stateCode)
            },
            onSignIn = { id, pin ->
                viewModel.signIn(id, pin)
            },
            onSuperAdminAccess = { pin ->
                viewModel.authenticateAndEnterSuperAdmin(pin)
            },
            onDemoSignIn = { viewModel.signInDemo() },
            serverAuthErrorMessage = uiState.authErrorMessage
        )
        return
    }

    // App Lock Screen
    if (uiState.isAppLocked) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(tokens.bg),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = tokens.card),
                border = BorderStroke(1.dp, tokens.cardBorder)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(tokens.accentTint),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Lock, contentDescription = "Locked", tint = tokens.accent, modifier = Modifier.size(32.dp))
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Pabaru Billing Secure Lock", color = tokens.text, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text("PBKDF2 100k-iteration protected", color = tokens.textMuted, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(20.dp))

                    OutlinedTextField(
                        value = unlockPinInput,
                        onValueChange = { raw ->
                            val digits = raw.filter { it.isDigit() }
                            if (digits.length <= 6) unlockPinInput = digits
                        },
                        placeholder = { Text("Enter Master PIN", color = tokens.textMuted) },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = {
                            if (!viewModel.unlockApp(unlockPinInput)) {
                                Toast.makeText(context, "Incorrect PIN!", Toast.LENGTH_SHORT).show()
                            }
                            unlockPinInput = ""
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = tokens.primary),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                    ) {
                        Text("Unlock Ledger", color = tokens.primaryText, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        return
    }

    BackHandler(enabled = moreTab != null || showThemeBottomSheet) {
        if (showThemeBottomSheet) {
            showThemeBottomSheet = false
        } else {
            moreTab = null
        }
    }

    if (showThemeBottomSheet) {
        ThemePickerBottomSheet(
            currentThemeMode = uiState.themeMode,
            activeTheme = uiState.currentTheme,
            onSelectThemeMode = { mode ->
                viewModel.setThemeMode(mode, context)
            },
            onDismissRequest = { showThemeBottomSheet = false }
        )
    }

    val navigateToAllocated: (String) -> Unit = { screenId ->
        when (screenId) {
            "invoices" -> { selectedTab = 1; moreTab = null }
            "quick" -> { moreTab = "quick" }
            "daybook" -> { moreTab = "daybook" }
            "ledgers" -> { moreTab = "ledgers" }
            "cash_bank" -> { selectedTab = 3; moreTab = null }
            "stock" -> { selectedTab = 2; moreTab = null }
            else -> { selectedTab = 0; moreTab = null }
        }
    }
    val backToHome: () -> Unit = { selectedTab = 0; moreTab = null }
    val switchToAdmin: () -> Unit = {
        viewModel.switchToAdmin()
        Toast.makeText(context, "Switched back to Admin (Full Access)", Toast.LENGTH_SHORT).show()
    }

    var isInvoiceFormOpen by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            if (!isInvoiceFormOpen && !(selectedTab == 4 && moreTab == null) && moreTab != "unpaid_bills_collect" && moreTab != "unpaid_bills_pay") {
                TopAppBar(
                title = {
                    Column {
                        Text(
                            text = uiState.profile.businessName.ifEmpty { "Pabaru Billing Enterprises" },
                            color = tokens.headerText,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        val subtitleText = if (uiState.currentUserRole == "staff") {
                            "${uiState.profile.storeCode.ifEmpty { "TL-7319" }} · Offline active (${uiState.currentUserName.split(" ").firstOrNull() ?: "Staff"})"
                        } else {
                            "${uiState.profile.storeCode.ifEmpty { "TL-7319" }} · Offline active"
                        }
                        Text(
                            text = subtitleText,
                            color = tokens.headerText.copy(alpha = 0.78f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Normal
                        )
                    }
                },
                actions = {
                    Surface(
                        modifier = Modifier
                            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                            .clickable { viewModel.verifyAuditChain() }
                            .padding(end = 8.dp),
                        color = if (uiState.auditResult.valid) tokens.successContainer else tokens.dangerContainer,
                        border = BorderStroke(0.5.dp, if (uiState.auditResult.valid) tokens.success else tokens.danger),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.heightIn(min = 48.dp).padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = if (uiState.auditResult.valid) Icons.Default.Shield else Icons.Default.Warning,
                                contentDescription = "Audit Shield",
                                tint = if (uiState.auditResult.valid) tokens.onSuccessContainer else tokens.onDangerContainer,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (uiState.auditResult.valid) "OK" else "Alert",
                                color = if (uiState.auditResult.valid) tokens.onSuccessContainer else tokens.onDangerContainer,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    Box(modifier = Modifier.padding(end = 8.dp)) {
                        Surface(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .clickable { showProfileDropdown = true },
                            color = tokens.headerText.copy(alpha = 0.14f),
                            border = BorderStroke(0.5.dp, tokens.headerText.copy(alpha = 0.3f)),
                            shape = CircleShape
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = "Profile and Settings",
                                    tint = tokens.headerText,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        DropdownMenu(
                            expanded = showProfileDropdown,
                            onDismissRequest = { showProfileDropdown = false },
                            modifier = Modifier
                                .background(tokens.card)
                                .border(BorderStroke(0.5.dp, tokens.cardBorder), RoundedCornerShape(12.dp))
                        ) {
                            if (uiState.currentUserRole == "staff") {
                                DropdownMenuItem(
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.AdminPanelSettings, contentDescription = null, tint = tokens.warning, modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Text("Switch to Admin (Owner)", color = tokens.warning, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                        }
                                    },
                                    onClick = {
                                        showProfileDropdown = false
                                        viewModel.switchToAdmin()
                                        Toast.makeText(context, "Switched back to Admin (Full Access)", Toast.LENGTH_SHORT).show()
                                    }
                                )
                            }
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Palette, contentDescription = null, tint = tokens.primary, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Text("Appearance & Themes", color = tokens.text, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                    }
                                },
                                onClick = {
                                    showProfileDropdown = false
                                    showThemeBottomSheet = true
                                }
                            )
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Storefront, contentDescription = null, tint = tokens.textMuted, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Text("Business Profile", color = tokens.text, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                    }
                                },
                                onClick = {
                                    showProfileDropdown = false
                                    moreTab = "business_profile"
                                }
                            )
                            if (!uiState.ownerPinHash.isNullOrEmpty()) {
                                DropdownMenuItem(
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.Lock, contentDescription = null, tint = tokens.danger, modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Text("Lock App", color = tokens.text, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                        }
                                    },
                                    onClick = {
                                        showProfileDropdown = false
                                        viewModel.lockApp()
                                    }
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = tokens.headerBg
                )
            )
            }
        },
        bottomBar = {
            if (!isInvoiceFormOpen && moreTab != "unpaid_bills_collect" && moreTab != "unpaid_bills_pay") {
                NavigationBar(
                    containerColor = tokens.card,
                    contentColor = tokens.text,
                    windowInsets = NavigationBarDefaults.windowInsets,
                    modifier = Modifier.border(width = 0.5.dp, color = tokens.cardBorder)
                ) {
                    val navItemColors = NavigationBarItemDefaults.colors(
                        selectedIconColor = tokens.primaryText,
                        indicatorColor = tokens.primary,
                        unselectedIconColor = tokens.textMuted,
                        unselectedTextColor = tokens.textMuted,
                        selectedTextColor = tokens.primary
                    )

                    // 1. Home
                    NavigationBarItem(
                        selected = selectedTab == 0 && moreTab == null,
                        onClick = { selectedTab = 0; moreTab = null },
                        icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
                        label = { Text("Home", fontSize = 12.sp, fontWeight = FontWeight.Medium) },
                        colors = navItemColors
                    )
                    // 2. Invoices
                    val invoicesAllocated = uiState.isScreenAllocated("invoices")
                    NavigationBarItem(
                        selected = (selectedTab == 1 && moreTab == null) || moreTab == "invoices",
                        onClick = { selectedTab = 1; moreTab = null },
                        icon = {
                            Icon(
                                imageVector = if (uiState.currentUserRole == "staff" && !invoicesAllocated) Icons.Default.Lock else Icons.Default.ReceiptLong,
                                contentDescription = "Invoices"
                            )
                        },
                        label = { Text(if (uiState.currentUserRole == "staff" && !invoicesAllocated) "Invoices 🔒" else "Invoices", fontSize = 12.sp, fontWeight = FontWeight.Medium) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = tokens.primaryText,
                            indicatorColor = if (uiState.currentUserRole == "staff" && !invoicesAllocated) tokens.warning else tokens.primary,
                            unselectedIconColor = tokens.textMuted,
                            unselectedTextColor = tokens.textMuted,
                            selectedTextColor = if (uiState.currentUserRole == "staff" && !invoicesAllocated) tokens.warning else tokens.primary
                        )
                    )
                    // 3. Inventory
                    val isInventoryActive = (selectedTab == 2 && moreTab == null) || moreTab == "stock"
                    NavigationBarItem(
                        selected = isInventoryActive,
                        onClick = {
                            selectedTab = 2
                            moreTab = null
                            inventoryInitialFilterLowStock = false
                        },
                        icon = {
                            if (activeLowStockCount > 0) {
                                BadgedBox(
                                    badge = {
                                        Badge(
                                            containerColor = tokens.danger,
                                            contentColor = tokens.primaryText
                                        ) {
                                            Text(
                                                text = if (activeLowStockCount > 99) "99+" else activeLowStockCount.toString(),
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Inventory2,
                                        contentDescription = "Inventory ($activeLowStockCount low stock)"
                                    )
                                }
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Inventory2,
                                    contentDescription = "Inventory"
                                )
                            }
                        },
                        label = {
                            Text("Inventory", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        },
                        colors = navItemColors
                    )
                    // 4. Bank
                    val bankAllocated = uiState.isScreenAllocated("cash_bank")
                    NavigationBarItem(
                        selected = (selectedTab == 3 && moreTab == null) || moreTab == "cash_bank" || moreTab == "banking",
                        onClick = { selectedTab = 3; moreTab = null },
                        icon = {
                            Icon(
                                imageVector = if (uiState.currentUserRole == "staff" && !bankAllocated) Icons.Default.Lock else Icons.Default.AccountBalance,
                                contentDescription = "Bank"
                            )
                        },
                        label = { Text(if (uiState.currentUserRole == "staff" && !bankAllocated) "Bank 🔒" else "Bank", fontSize = 12.sp, fontWeight = FontWeight.Medium) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = tokens.primaryText,
                            indicatorColor = if (uiState.currentUserRole == "staff" && !bankAllocated) tokens.warning else tokens.primary,
                            unselectedIconColor = tokens.textMuted,
                            unselectedTextColor = tokens.textMuted,
                            selectedTextColor = if (uiState.currentUserRole == "staff" && !bankAllocated) tokens.warning else tokens.primary
                        )
                    )
                    // 5. Menu
                    NavigationBarItem(
                        selected = selectedTab == 4 && moreTab == null,
                        onClick = { selectedTab = 4; moreTab = null },
                        icon = { Icon(Icons.Default.Menu, contentDescription = "Menu") },
                        label = { Text("Menu", fontSize = 12.sp, fontWeight = FontWeight.Medium) },
                        colors = navItemColors
                    )
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(containerBg)
        ) {
            when {
                moreTab == "invoices" -> {
                    if (!uiState.isScreenAllocated("invoices")) {
                        ScreenAccessRestrictedView(
                            screenTitle = "GST Invoices Hub",
                            screenCategory = "Tax & Sales Billing",
                            uiState = uiState,
                            onNavigateToScreen = navigateToAllocated,
                            onBackToHome = backToHome,
                            onSwitchToAdmin = switchToAdmin
                        )
                    } else {
                        InvoiceMenuScreen(
                            uiState = uiState,
                            onSaveInvoice = { partyId, mode, type, items, remarks ->
                                try {
                                    val newTxn = viewModel.recordItemizedInvoice(partyId, mode, type, items, remarks)
                                    val party = uiState.parties.find { it.id == partyId }
                                    ExportEngine.generateAndShareInvoicePdf(context, newTxn, uiState.profile, party, uiState.bankAccounts.firstOrNull())
                                    true
                                } catch (e: IllegalArgumentException) {
                                    Toast.makeText(context, e.message ?: "Insufficient stock to create invoice", Toast.LENGTH_LONG).show()
                                    false
                                }
                            },
                            onVoid = { txn -> voidingTxnId = txn.id },
                            onAddParty = { newParty -> viewModel.addParty(newParty) },
                            onFormOpenChanged = { isInvoiceFormOpen = it }
                        )
                    }
                }
                moreTab == "ledgers" || moreTab == "parties" -> {
                    if (!uiState.isScreenAllocated("ledgers")) {
                        ScreenAccessRestrictedView(
                            screenTitle = "Parties & Khata Ledgers",
                            screenCategory = "Customer & Supplier Ledgers",
                            uiState = uiState,
                            onNavigateToScreen = navigateToAllocated,
                            onBackToHome = backToHome,
                            onSwitchToAdmin = switchToAdmin
                        )
                    } else {
                        LedgerMenuScreen(
                            uiState = uiState,
                            onRecordPayment = { pId, amt, isPmtIn, mode, rem ->
                                viewModel.recordPayment(pId, amt, isPmtIn, mode, rem)
                            },
                            onAddParty = { newParty -> viewModel.addParty(newParty) }
                        )
                    }
                }
                moreTab == "cash_bank" || moreTab == "banking" -> {
                    if (!uiState.isScreenAllocated("cash_bank")) {
                        ScreenAccessRestrictedView(
                            screenTitle = "Cash & Bank Balance",
                            screenCategory = "Live Drawer & Bank Accounts",
                            uiState = uiState,
                            onNavigateToScreen = navigateToAllocated,
                            onBackToHome = backToHome,
                            onSwitchToAdmin = switchToAdmin
                        )
                    } else {
                        CashBankScreen(
                            uiState = uiState,
                            onRunReconciliation = { viewModel.reconcileBankFeed() },
                            onImportStatement = { viewModel.importSimulatedStatement() },
                            onSetManagerPin = { pin, enableFp -> viewModel.setManagerPin(pin, enableFp) },
                            onVerifyManagerPin = { pin -> viewModel.verifyManagerPin(pin) },
                            onUnlockWithFingerprint = { viewModel.unlockManagerWithFingerprint() },
                            onLockManager = { viewModel.lockManager() },
                            onResetManagerPin = { pin -> viewModel.resetManagerPinAfterDeviceAuth(pin) },
                            onAddQr = { qr -> viewModel.addQrCode(qr) },
                            onUpdateQr = { qr -> viewModel.updateQrCode(qr) },
                            onDeleteQr = { id -> viewModel.deleteQrCode(id) },
                            onSetDefaultQr = { id -> viewModel.setDefaultQr(id) },
                            onToggleQrEnabled = { id -> viewModel.toggleQrEnabled(id) },
                            onAddBankAccount = { acc -> viewModel.addBankAccount(acc) },
                            onAdjustCash = { amt, isAdd, note -> viewModel.adjustCash(amt, isAdd, note) },
                            onAdjustCashPaise = { paise, isAdd, note -> viewModel.adjustCashPaise(paise, isAdd, note) },
                            onTransfer = { from, to, amt, note -> viewModel.transferBetweenAccounts(from, to, amt, note) },
                            onTransferPaise = { from, to, paise, note -> viewModel.transferBetweenAccountsPaise(from, to, paise, note) },
                            onReconcileSelected = { ids -> viewModel.reconcileSelectedItems(ids) },
                            onConfirmMatch = { id -> viewModel.confirmMatch(id) },
                            onConfirmAllMatches = { conf -> viewModel.confirmAllMatches(conf) },
                            onManualMatch = { stmtId, txnId -> viewModel.manualMatch(stmtId, txnId) },
                            onUnlinkMatch = { id -> viewModel.unlinkMatch(id) },
                            onResolveDiscrepancy = { stmtId, resType, partyId, remarks -> viewModel.resolveDiscrepancy(stmtId, resType, partyId, remarks) },
                            onImportStatementText = { text, bankId, name -> viewModel.importStatementText(text, bankId, name) },
                            onImportSampleStatement = { format, bankId -> viewModel.importSampleStatement(format, bankId) },
                            onClearStatement = { bankId -> viewModel.clearStatementItems(bankId) }
                        )
                    }
                }
                moreTab == "statement_imports" -> {
                    if (!uiState.isScreenAllocated("cash_bank")) {
                        ScreenAccessRestrictedView(
                            screenTitle = "Bank Statement Imports",
                            screenCategory = "Banking & Reconciliation",
                            uiState = uiState,
                            onNavigateToScreen = navigateToAllocated,
                            onBackToHome = backToHome,
                            onSwitchToAdmin = switchToAdmin
                        )
                    } else {
                        CashBankScreen(
                            uiState = uiState,
                            onRunReconciliation = { viewModel.reconcileBankFeed() },
                            onImportStatement = { viewModel.importSimulatedStatement() },
                            initialSubTab = 1,
                            onSetManagerPin = { pin, enableFp -> viewModel.setManagerPin(pin, enableFp) },
                            onVerifyManagerPin = { pin -> viewModel.verifyManagerPin(pin) },
                            onUnlockWithFingerprint = { viewModel.unlockManagerWithFingerprint() },
                            onLockManager = { viewModel.lockManager() },
                            onResetManagerPin = { pin -> viewModel.resetManagerPinAfterDeviceAuth(pin) },
                            onAddQr = { qr -> viewModel.addQrCode(qr) },
                            onUpdateQr = { qr -> viewModel.updateQrCode(qr) },
                            onDeleteQr = { id -> viewModel.deleteQrCode(id) },
                            onSetDefaultQr = { id -> viewModel.setDefaultQr(id) },
                            onToggleQrEnabled = { id -> viewModel.toggleQrEnabled(id) },
                            onAddBankAccount = { acc -> viewModel.addBankAccount(acc) },
                            onAdjustCash = { amt, isAdd, note -> viewModel.adjustCash(amt, isAdd, note) },
                            onAdjustCashPaise = { paise, isAdd, note -> viewModel.adjustCashPaise(paise, isAdd, note) },
                            onTransfer = { from, to, amt, note -> viewModel.transferBetweenAccounts(from, to, amt, note) },
                            onTransferPaise = { from, to, paise, note -> viewModel.transferBetweenAccountsPaise(from, to, paise, note) },
                            onReconcileSelected = { ids -> viewModel.reconcileSelectedItems(ids) },
                            onConfirmMatch = { id -> viewModel.confirmMatch(id) },
                            onConfirmAllMatches = { conf -> viewModel.confirmAllMatches(conf) },
                            onManualMatch = { stmtId, txnId -> viewModel.manualMatch(stmtId, txnId) },
                            onUnlinkMatch = { id -> viewModel.unlinkMatch(id) },
                            onResolveDiscrepancy = { stmtId, resType, partyId, remarks -> viewModel.resolveDiscrepancy(stmtId, resType, partyId, remarks) },
                            onImportStatementText = { text, bankId, name -> viewModel.importStatementText(text, bankId, name) },
                            onImportSampleStatement = { format, bankId -> viewModel.importSampleStatement(format, bankId) },
                            onClearStatement = { bankId -> viewModel.clearStatementItems(bankId) }
                        )
                    }
                }
                moreTab == "reports_analytics" -> {
                    if (uiState.currentUserRole == "staff") {
                        ScreenAccessRestrictedView(
                            screenTitle = "Reports & Analytics",
                            screenCategory = "Audited Financials",
                            uiState = uiState,
                            onNavigateToScreen = navigateToAllocated,
                            onBackToHome = backToHome,
                            onSwitchToAdmin = switchToAdmin
                        )
                    } else {
                        ReportsAndFinancialsScreen(uiState = uiState, initialTab = 0, onBack = { moreTab = null })
                    }
                }
                moreTab == "pl" || moreTab == "reports" -> {
                    if (uiState.currentUserRole == "staff") {
                        ScreenAccessRestrictedView(
                            screenTitle = "Profit & Loss (P&L)",
                            screenCategory = "Audited Financials",
                            uiState = uiState,
                            onNavigateToScreen = navigateToAllocated,
                            onBackToHome = backToHome,
                            onSwitchToAdmin = switchToAdmin
                        )
                    } else {
                        ReportsAndFinancialsScreen(uiState = uiState, initialTab = 0, onBack = { moreTab = null })
                    }
                }
                moreTab == "balance_sheet" -> {
                    if (uiState.currentUserRole == "staff") {
                        ScreenAccessRestrictedView(
                            screenTitle = "Balance Sheet",
                            screenCategory = "Audited Financials",
                            uiState = uiState,
                            onNavigateToScreen = navigateToAllocated,
                            onBackToHome = backToHome,
                            onSwitchToAdmin = switchToAdmin
                        )
                    } else {
                        ReportsAndFinancialsScreen(uiState = uiState, initialTab = 1, onBack = { moreTab = null })
                    }
                }
                moreTab == "gst_reports" -> {
                    if (uiState.currentUserRole == "staff") {
                        ScreenAccessRestrictedView(
                            screenTitle = "GST Filing Reports",
                            screenCategory = "Audited Financials",
                            uiState = uiState,
                            onNavigateToScreen = navigateToAllocated,
                            onBackToHome = backToHome,
                            onSwitchToAdmin = switchToAdmin
                        )
                    } else {
                        ReportsAndFinancialsScreen(uiState = uiState, initialTab = 2, onBack = { moreTab = null })
                    }
                }
                moreTab == "cma_project" -> {
                    if (uiState.currentUserRole == "staff") {
                        ScreenAccessRestrictedView(
                            screenTitle = "CMA & Bank Loan Report",
                            screenCategory = "Admin Financial Tools",
                            uiState = uiState,
                            onNavigateToScreen = navigateToAllocated,
                            onBackToHome = backToHome,
                            onSwitchToAdmin = switchToAdmin
                        )
                    } else {
                        val finAll = FinancialEngine.calculateFyFinancials(uiState, "All Time")
                        CmaProjectReportScreen(
                            profile = uiState.profile,
                            transactions = uiState.transactions,
                            parties = uiState.parties,
                            products = uiState.products,
                            cashAndBankPaise = finAll.cashInHandPaise + finAll.bankTotalPaise,
                            onBack = { moreTab = null }
                        )
                    }
                }

                moreTab == "gstr2b_recon" -> {
                    Gstr2bReconciliationScreen(
                        profile = uiState.profile,
                        transactions = uiState.transactions,
                        parties = uiState.parties,
                        onBack = { moreTab = null }
                    )
                }

                moreTab == "business_profile" -> {
                    if (uiState.currentUserRole == "staff") {
                        ScreenAccessRestrictedView(
                            screenTitle = "Business Statutory Profile",
                            screenCategory = "Admin Privileges Required",
                            uiState = uiState,
                            onNavigateToScreen = navigateToAllocated,
                            onBackToHome = backToHome,
                            onSwitchToAdmin = switchToAdmin
                        )
                    } else {
                        BusinessProfileScreen(
                            uiState = uiState,
                            onSaveProfile = { updated -> viewModel.updateProfile(updated) },
                            onBack = { moreTab = null },
                            onOpenThemePicker = { showThemeBottomSheet = true }
                        )
                    }
                }
                moreTab == "backup_json" -> {
                    if (uiState.currentUserRole == "staff") {
                        ScreenAccessRestrictedView(
                            screenTitle = "Backup JSON & Security Keys",
                            screenCategory = "Admin Privileges Required",
                            uiState = uiState,
                            onNavigateToScreen = navigateToAllocated,
                            onBackToHome = backToHome,
                            onSwitchToAdmin = switchToAdmin
                        )
                    } else {
                        BackupJsonScreen(
                            uiState = uiState,
                            viewModel = viewModel,
                            onBack = { moreTab = null }
                        )
                    }
                }
                moreTab == "gst_assistant" -> {
                    if (!isGstPaidUnlocked && !uiState.isGstUnlocked) {
                        PremiumFeatureComingSoonScreen(
                            featureType = PremiumFeatureType.GST_FILING_ASSISTANT,
                            onBack = { moreTab = null },
                            businessName = uiState.profile.businessName,
                            onUnlockDemo = { isGstPaidUnlocked = true }
                        )
                    } else {
                        GstAssistantScreen(uiState = uiState, viewModel = viewModel)
                    }
                }

                moreTab == "daybook" -> {
                    if (!uiState.isScreenAllocated("daybook")) {
                        ScreenAccessRestrictedView(
                            screenTitle = "Daybook Register",
                            screenCategory = "Daily Ledger & Cash Flow",
                            uiState = uiState,
                            onNavigateToScreen = navigateToAllocated,
                            onBackToHome = backToHome,
                            onSwitchToAdmin = switchToAdmin
                        )
                    } else {
                        DaybookScreen(
                            uiState = uiState,
                            onVoid = { txn -> voidingTxnId = txn.id },
                            onSharePdf = { txn ->
                                val party = uiState.parties.find { it.id == txn.partyId }
                                ExportEngine.generateAndShareInvoicePdf(context, txn, uiState.profile, party, uiState.bankAccounts.firstOrNull())
                            }
                        )
                    }
                }
                moreTab == "stock" -> {
                    if (!uiState.isScreenAllocated("stock")) {
                        ScreenAccessRestrictedView(
                            screenTitle = "Stock & Inventory Tracking",
                            screenCategory = "Products & Reorder Alerts",
                            uiState = uiState,
                            onNavigateToScreen = navigateToAllocated,
                            onBackToHome = backToHome,
                            onSwitchToAdmin = switchToAdmin
                        )
                    } else {
                        InventoryScreen(uiState = uiState, viewModel = viewModel, onBack = { moreTab = null })
                    }
                }
                moreTab == "security" -> SecurityScreen(uiState, viewModel, onSetPin = { showSetPinDialog = true }, onBack = { moreTab = null })
                moreTab == "desktop" -> CrossPlatformInfoScreen(onBack = { moreTab = null })
                moreTab == "unpaid_bills_collect" -> UnpaidBillsDetailScreen(
                    isCollect = true,
                    uiState = uiState,
                    onBack = { moreTab = null },
                    onRecordPayment = { pId, amt, isPmtIn, mode, rem ->
                        viewModel.recordPayment(pId, amt, isPmtIn, mode, rem)
                    }
                )
                moreTab == "unpaid_bills_pay" -> UnpaidBillsDetailScreen(
                    isCollect = false,
                    uiState = uiState,
                    onBack = { moreTab = null },
                    onRecordPayment = { pId, amt, isPmtIn, mode, rem ->
                        viewModel.recordPayment(pId, amt, isPmtIn, mode, rem)
                    }
                )
                selectedTab == 0 -> DashboardScreen(
                    uiState = uiState,
                    onNavigateToInvoice = { selectedTab = 1; moreTab = null },
                    onNavigateToDaybook = { moreTab = "daybook" },
                    onNavigateToUnpaidCollect = { moreTab = "unpaid_bills_collect" },
                    onNavigateToUnpaidPay = { moreTab = "unpaid_bills_pay" },
                    onNavigateToInventory = { filterLowStock ->
                        inventoryInitialFilterLowStock = filterLowStock
                        selectedTab = 2
                        moreTab = null
                    },
                    onVoid = { txn -> voidingTxnId = txn.id },
                    onUpdateThreshold = { newTh -> viewModel.updateLowStockThreshold(newTh) },
                    onRestockProduct = { pId, addQty ->
                        val locId = uiState.inventoryLocations.firstOrNull()?.id ?: "LOC-MAIN"
                        viewModel.recordStockAdjustmentMilli(
                            productId = pId,
                            locationId = locId,
                            quantityDeltaMilli = addQty.toLong() * 1000L,
                            reason = StockAdjustmentReason.COUNT_CORRECTION,
                            remarks = "Quick restock +$addQty from Dashboard"
                        )
                    }
                )
                selectedTab == 1 -> {
                    if (!uiState.isScreenAllocated("invoices")) {
                        ScreenAccessRestrictedView(
                            screenTitle = "GST Invoices Hub",
                            screenCategory = "Tax & Sales Billing",
                            uiState = uiState,
                            onNavigateToScreen = navigateToAllocated,
                            onBackToHome = backToHome,
                            onSwitchToAdmin = switchToAdmin
                        )
                    } else {
                        InvoiceMenuScreen(
                            uiState = uiState,
                            onSaveInvoice = { partyId, mode, type, items, remarks ->
                                try {
                                    val newTxn = viewModel.recordItemizedInvoice(partyId, mode, type, items, remarks)
                                    val party = uiState.parties.find { it.id == partyId }
                                    ExportEngine.generateAndShareInvoicePdf(context, newTxn, uiState.profile, party, uiState.bankAccounts.firstOrNull())
                                    true
                                } catch (e: IllegalArgumentException) {
                                    Toast.makeText(context, e.message ?: "Insufficient stock to create invoice", Toast.LENGTH_LONG).show()
                                    false
                                }
                            },
                            onVoid = { txn -> voidingTxnId = txn.id },
                            onAddParty = { newParty -> viewModel.addParty(newParty) },
                            onFormOpenChanged = { isInvoiceFormOpen = it }
                        )
                    }
                }
                selectedTab == 2 -> {
                    if (!uiState.isScreenAllocated("stock")) {
                        ScreenAccessRestrictedView(
                            screenTitle = "Stock & Inventory Tracking",
                            screenCategory = "Products & Reorder Alerts",
                            uiState = uiState,
                            onNavigateToScreen = navigateToAllocated,
                            onBackToHome = backToHome,
                            onSwitchToAdmin = switchToAdmin
                        )
                    } else {
                        InventoryScreen(
                            uiState = uiState,
                            viewModel = viewModel,
                            initialFilterLowStock = inventoryInitialFilterLowStock
                        )
                    }
                }
                selectedTab == 3 -> {
                    if (!uiState.isScreenAllocated("cash_bank")) {
                        ScreenAccessRestrictedView(
                            screenTitle = "Cash & Bank Balance",
                            screenCategory = "Live Drawer & Bank Accounts",
                            uiState = uiState,
                            onNavigateToScreen = navigateToAllocated,
                            onBackToHome = backToHome,
                            onSwitchToAdmin = switchToAdmin
                        )
                    } else {
                        CashBankScreen(
                            uiState = uiState,
                            onRunReconciliation = { viewModel.reconcileBankFeed() },
                            onImportStatement = { viewModel.importSimulatedStatement() },
                            onSetManagerPin = { pin, enableFp -> viewModel.setManagerPin(pin, enableFp) },
                            onVerifyManagerPin = { pin -> viewModel.verifyManagerPin(pin) },
                            onUnlockWithFingerprint = { viewModel.unlockManagerWithFingerprint() },
                            onLockManager = { viewModel.lockManager() },
                            onResetManagerPin = { pin -> viewModel.resetManagerPinAfterDeviceAuth(pin) },
                            onAddQr = { qr -> viewModel.addQrCode(qr) },
                            onUpdateQr = { qr -> viewModel.updateQrCode(qr) },
                            onDeleteQr = { id -> viewModel.deleteQrCode(id) },
                            onSetDefaultQr = { id -> viewModel.setDefaultQr(id) },
                            onToggleQrEnabled = { id -> viewModel.toggleQrEnabled(id) },
                            onAddBankAccount = { acc -> viewModel.addBankAccount(acc) },
                            onAdjustCash = { amt, isAdd, note -> viewModel.adjustCash(amt, isAdd, note) },
                            onAdjustCashPaise = { paise, isAdd, note -> viewModel.adjustCashPaise(paise, isAdd, note) },
                            onTransfer = { from, to, amt, note -> viewModel.transferBetweenAccounts(from, to, amt, note) },
                            onTransferPaise = { from, to, paise, note -> viewModel.transferBetweenAccountsPaise(from, to, paise, note) },
                            onReconcileSelected = { ids -> viewModel.reconcileSelectedItems(ids) },
                            onConfirmMatch = { id -> viewModel.confirmMatch(id) },
                            onConfirmAllMatches = { conf -> viewModel.confirmAllMatches(conf) },
                            onManualMatch = { stmtId, txnId -> viewModel.manualMatch(stmtId, txnId) },
                            onUnlinkMatch = { id -> viewModel.unlinkMatch(id) },
                            onResolveDiscrepancy = { stmtId, resType, partyId, remarks -> viewModel.resolveDiscrepancy(stmtId, resType, partyId, remarks) },
                            onImportStatementText = { text, bankId, name -> viewModel.importStatementText(text, bankId, name) },
                            onImportSampleStatement = { format, bankId -> viewModel.importSampleStatement(format, bankId) },
                            onClearStatement = { bankId -> viewModel.clearStatementItems(bankId) }
                        )
                    }
                }
                selectedTab == 4 -> MoreMenuScreen(
                    uiState = uiState,
                    onSelect = { section ->
                        if (section == "color_theme") {
                            showThemeBottomSheet = true
                        } else if (section == "sign_out") {
                            viewModel.signOut()
                        } else {
                            moreTab = section
                        }
                    }
                )
            }
        }
    }

    // Void Dialog with Mandatory Reason
    showVoidDialog?.let { txn ->
        AlertDialog(
            onDismissRequest = { voidingTxnId = null; voidReasonInput = "" },
            containerColor = tokens.card,
            title = {
                Text(
                    "Void Transaction #${txn.invoiceNumber.ifEmpty { txn.id }}",
                    color = tokens.danger,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    Text(
                        "Mandatory reason required for audited ledger. Any itemized stock deducted will be automatically restored.",
                        fontSize = 12.sp,
                        color = tokens.textMuted
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = voidReasonInput,
                        onValueChange = { voidReasonInput = it },
                        placeholder = { Text("Reason (e.g. Order cancelled, typo)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (voidReasonInput.isNotBlank()) {
                            viewModel.voidTransaction(txn.id, voidReasonInput.trim())
                            voidingTxnId = null
                            voidReasonInput = ""
                        } else {
                            Toast.makeText(context, "Reason is required!", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = tokens.danger,
                        contentColor = tokens.primaryText
                    )
                ) {
                    Text("Confirm Void")
                }
            },
            dismissButton = {
                TextButton(onClick = { voidingTxnId = null; voidReasonInput = "" }) {
                    Text("Cancel", color = tokens.textMuted)
                }
            }
        )
    }

    // Set PIN Dialog
    if (showSetPinDialog) {
        AlertDialog(
            onDismissRequest = { showSetPinDialog = false },
            containerColor = tokens.card,
            title = { Text("Set PBKDF2 Master PIN", fontWeight = FontWeight.Bold, color = tokens.text) },
            text = {
                Column {
                    Text("Protects counter when left unattended.", fontSize = 12.sp, color = tokens.textMuted)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = pinInput,
                        onValueChange = { raw ->
                            val digits = raw.filter { it.isDigit() }
                            if (digits.length <= 6) pinInput = digits
                        },
                        placeholder = { Text("4-6 digit numeric PIN") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (pinInput.length < 4) {
                            Toast.makeText(context, "PIN must be 4 to 6 numeric digits", Toast.LENGTH_SHORT).show()
                        } else {
                            viewModel.setSecurityPin(pinInput)
                            showSetPinDialog = false
                            pinInput = ""
                            Toast.makeText(context, "PIN Set! 12-Word Recovery generated.", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = tokens.primary,
                        contentColor = tokens.primaryText
                    )
                ) {
                    Text("Save PIN")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSetPinDialog = false; pinInput = "" }) {
                    Text("Cancel", color = tokens.textMuted)
                }
            }
        )
    }
}

// -------------------------------------------------------------
// Sub-screen Composables
// -------------------------------------------------------------

private fun formatDashboardAmount(paise: Long, compact: Boolean = false): String {
    val isNegative = paise < 0
    val absPaise = kotlin.math.abs(paise)
    val rupees = absPaise / 100.0
    val sign = if (isNegative) "-" else ""
    return if (compact) {
        when {
            rupees >= 10000000.0 -> String.format(java.util.Locale.ENGLISH, "%s₹%.2fCr", sign, rupees / 10000000.0)
            rupees >= 100000.0 -> String.format(java.util.Locale.ENGLISH, "%s₹%.2fL", sign, rupees / 100000.0)
            rupees >= 1000.0 -> String.format(java.util.Locale.ENGLISH, "%s₹%.2fK", sign, rupees / 1000.0)
            else -> String.format(java.util.Locale.ENGLISH, "%s₹%.2f", sign, rupees)
        }
    } else {
        String.format(java.util.Locale.ENGLISH, "%s₹%.2f", sign, rupees)
    }
}

private fun Modifier.dashedBorder(
    width: androidx.compose.ui.unit.Dp,
    color: Color,
    cornerRadius: androidx.compose.ui.unit.Dp = 12.dp,
    dashLength: androidx.compose.ui.unit.Dp = 6.dp,
    gapLength: androidx.compose.ui.unit.Dp = 4.dp
): Modifier = this.drawBehind {
    val strokeWidth = width.toPx()
    val halfStroke = strokeWidth / 2f
    val radius = cornerRadius.toPx()
    val path = Path().apply {
        addRoundRect(
            RoundRect(
                rect = Rect(
                    left = halfStroke,
                    top = halfStroke,
                    right = size.width - halfStroke,
                    bottom = size.height - halfStroke
                ),
                cornerRadius = CornerRadius(radius, radius)
            )
        )
    }
    drawPath(
        path = path,
        color = color,
        style = Stroke(
            width = strokeWidth,
            pathEffect = PathEffect.dashPathEffect(
                floatArrayOf(dashLength.toPx(), gapLength.toPx()),
                0f
            )
        )
    )
}

@Composable
fun DashboardTransactionCard(
    txn: Transaction,
    uiState: PabaruUiState,
    onVoid: (Transaction) -> Unit
) {
    val tokens = PabaruTheme.tokens
    val party = uiState.parties.find { it.id == txn.partyId }
    val isQuotation = txn.type == TransactionType.Quotation
    val isIn = txn.type == TransactionType.PaymentIn ||
        ((txn.type == TransactionType.Sale || txn.type == TransactionType.NonGstBill || txn.type == TransactionType.DebitNote) && txn.paymentMode != PaymentMode.Credit)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = tokens.card),
        border = BorderStroke(0.5.dp, tokens.cardBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                Text(
                    text = party?.name ?: txn.remarks.ifEmpty { "Counter Customer" },
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = tokens.text,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    textDecoration = if (txn.isVoided) TextDecoration.LineThrough else TextDecoration.None
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${txn.invoiceNumber.ifEmpty { txn.date }} · ${txn.paymentMode.name}",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Normal,
                    color = tokens.textMuted
                )
                if (txn.isVoided) {
                    Text(
                        text = "[VOID: ${txn.voidReason}]",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = tokens.danger
                    )
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = if (isQuotation) formatDashboardAmount(txn.amountPaise) else (if (isIn) "+" else "-") + formatDashboardAmount(txn.amountPaise),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = when {
                        isQuotation -> tokens.textMuted
                        isIn -> tokens.success
                        else -> tokens.danger
                    }
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = txn.type.name,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Normal,
                    color = tokens.textMuted
                )
            }
        }
    }
}

@Composable
fun ScreenAccessRestrictedView(
    screenTitle: String,
    screenCategory: String,
    uiState: PabaruUiState,
    onNavigateToScreen: (String) -> Unit,
    onBackToHome: () -> Unit,
    onSwitchToAdmin: () -> Unit
) {
    val tokens = PabaruTheme.tokens
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(tokens.bg)
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .widthIn(max = 440.dp)
                .fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = tokens.card),
            border = BorderStroke(0.5.dp, tokens.warning),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(tokens.warningContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Screen Not Allocated",
                        tint = tokens.warning,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Text(
                    text = "Screen Not Allocated",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    color = tokens.text
                )

                Text(
                    text = "Access to '$screenTitle' ($screenCategory) has not been allocated to your counter profile by the store administrator.",
                    fontSize = 12.sp,
                    color = tokens.textMuted,
                    textAlign = TextAlign.Center,
                    lineHeight = 16.sp
                )

                val allocated = uiState.getAllocatedScreensForCurrent()
                if (allocated.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(tokens.secondaryBg, RoundedCornerShape(8.dp))
                            .border(0.5.dp, tokens.cardBorder, RoundedCornerShape(8.dp))
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "Screens allocated to you (${allocated.size} of ${AllocatableScreen.entries.size}):",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = tokens.success
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            allocated.take(3).forEach { screenId ->
                                val screenDef = AllocatableScreen.findById(screenId)
                                if (screenDef != null) {
                                    Button(
                                        onClick = { onNavigateToScreen(screenId) },
                                        colors = ButtonDefaults.buttonColors(containerColor = tokens.card),
                                        border = BorderStroke(0.5.dp, tokens.cardBorder),
                                        shape = RoundedCornerShape(6.dp),
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                                        modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                                    ) {
                                        Text(
                                            text = screenDef.shortTitle,
                                            fontSize = 11.sp,
                                            color = tokens.success,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                        }
                        if (allocated.size > 3) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                allocated.drop(3).forEach { screenId ->
                                    val screenDef = AllocatableScreen.findById(screenId)
                                    if (screenDef != null) {
                                        Button(
                                            onClick = { onNavigateToScreen(screenId) },
                                            colors = ButtonDefaults.buttonColors(containerColor = tokens.card),
                                            border = BorderStroke(0.5.dp, tokens.cardBorder),
                                            shape = RoundedCornerShape(6.dp),
                                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                                            modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                                        ) {
                                            Text(
                                                text = screenDef.shortTitle,
                                                fontSize = 11.sp,
                                                color = tokens.success,
                                                maxLines = 1
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onBackToHome,
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(0.5.dp, tokens.cardBorder),
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                    ) {
                        Text("Go to Home", color = tokens.textMuted, fontSize = 12.sp)
                    }

                    Button(
                        onClick = onSwitchToAdmin,
                        colors = ButtonDefaults.buttonColors(containerColor = tokens.primary, contentColor = tokens.primaryText),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                    ) {
                        Text("Switch to Admin", color = tokens.primaryText, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }
}

@Composable
fun DashboardScreen(
    uiState: PabaruUiState,
    onNavigateToInvoice: () -> Unit,
    onNavigateToDaybook: () -> Unit,
    onNavigateToUnpaidCollect: () -> Unit = {},
    onNavigateToUnpaidPay: () -> Unit = {},
    onNavigateToInventory: (filterLowStock: Boolean) -> Unit = {},
    onVoid: (Transaction) -> Unit,
    onUpdateThreshold: (Int) -> Unit = {},
    onRestockProduct: (String, Int) -> Unit = { _, _ -> }
) {
    val tokens = PabaruTheme.tokens
    val branchTxns = remember(uiState.transactions, uiState.activeBranchId) {
        uiState.transactions.filter { it.branchId == uiState.activeBranchId }
    }

    var isLowStockExpanded by rememberSaveable { mutableStateOf(false) }
    var showThresholdDialog by remember { mutableStateOf(false) }
    var thresholdInput by remember { mutableStateOf(uiState.lowStockThreshold.toString()) }

    if (showThresholdDialog) {
        AlertDialog(
            onDismissRequest = { showThresholdDialog = false },
            containerColor = tokens.card,
            shape = RoundedCornerShape(12.dp),
            title = {
                Text(
                    text = "Inventory Alert Threshold",
                    fontWeight = FontWeight.Medium,
                    fontSize = 16.sp,
                    color = tokens.text
                )
            },
            text = {
                Column {
                    Text(
                        text = "Items with stock quantity at or below this threshold trigger low stock alerts.",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Normal,
                        color = tokens.textMuted
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = thresholdInput,
                        onValueChange = { thresholdInput = it.filter { char -> char.isDigit() } },
                        label = { Text("Stock Alert Level (Units)", fontSize = 12.sp, color = tokens.textMuted) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = tokens.text,
                            unfocusedTextColor = tokens.text,
                            focusedBorderColor = tokens.primary,
                            unfocusedBorderColor = tokens.cardBorder
                        )
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(5, 10, 15, 20, 30).forEach { preset ->
                            SuggestionChip(
                                onClick = { thresholdInput = preset.toString() },
                                label = { Text("$preset units", fontSize = 12.sp, fontWeight = FontWeight.Normal) },
                                colors = SuggestionChipDefaults.suggestionChipColors(
                                    containerColor = if (thresholdInput == preset.toString()) tokens.warningContainer else tokens.secondaryBg,
                                    labelColor = if (thresholdInput == preset.toString()) tokens.warning else tokens.text
                                ),
                                border = BorderStroke(0.5.dp, if (thresholdInput == preset.toString()) tokens.warning else tokens.cardBorder)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val newTh = (thresholdInput.toIntOrNull() ?: uiState.lowStockThreshold).coerceAtLeast(1)
                        onUpdateThreshold(newTh)
                        showThresholdDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = tokens.primary, contentColor = tokens.primaryText),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Save Threshold", color = tokens.primaryText, fontWeight = FontWeight.Medium, fontSize = 12.sp)
                }
            },
            dismissButton = {
                TextButton(onClick = { showThresholdDialog = false }) {
                    Text("Cancel", color = tokens.textMuted, fontSize = 12.sp, fontWeight = FontWeight.Normal)
                }
            }
        )
    }

    val todayStr = FinancialEngine.getTodayDate()
    var todayInPaise = 0L
    var todayOutPaise = 0L

    branchTxns.forEach { t ->
        if (!t.isVoided && FinancialEngine.normalizeDate(t.date) == todayStr && t.paymentMode != PaymentMode.Credit) {
            when (t.type) {
                TransactionType.PaymentIn,
                TransactionType.Sale,
                TransactionType.NonGstBill,
                TransactionType.DebitNote -> todayInPaise += t.amountPaise
                TransactionType.Purchase,
                TransactionType.PaymentOut,
                TransactionType.Expense,
                TransactionType.CreditNote,
                TransactionType.Drawing -> todayOutPaise += t.amountPaise
                TransactionType.Quotation -> {}
            }
        }
    }

    var totalReceivablePaise = 0L
    var totalPayablePaise = 0L
    uiState.parties.forEach { p ->
        var bal = when (p.openingBalanceType) {
            BalanceType.Receivable -> p.openingBalancePaise
            BalanceType.Payable -> -p.openingBalancePaise
            BalanceType.Settled -> 0L
        }
        branchTxns.forEach { t ->
            if (t.partyId == p.id && !t.isVoided) {
                when (t.type) {
                    TransactionType.Sale, TransactionType.NonGstBill -> if (t.paymentMode == PaymentMode.Credit) bal += t.amountPaise
                    TransactionType.CreditNote -> if (t.paymentMode == PaymentMode.Credit) bal -= t.amountPaise
                    TransactionType.Purchase -> if (t.paymentMode == PaymentMode.Credit) bal -= t.amountPaise
                    TransactionType.DebitNote -> if (t.paymentMode == PaymentMode.Credit) bal += t.amountPaise
                    TransactionType.PaymentIn -> bal -= t.amountPaise
                    TransactionType.PaymentOut -> bal += t.amountPaise
                    else -> {}
                }
            }
        }
        if (bal > 0) totalReceivablePaise += bal
        if (bal < 0) totalPayablePaise += kotlin.math.abs(bal)
    }

    val lowStockItems = remember(uiState.products, uiState.lowStockThreshold) {
        val thresholdMilli = uiState.lowStockThreshold.toLong() * 1000L
        uiState.products.filter {
            !it.isArchived && (it.currentStockMilli <= thresholdMilli || (it.reorderLevelMilli > 0L && it.currentStockMilli <= it.reorderLevelMilli))
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(tokens.bg),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxHeight()
                .widthIn(max = 600.dp)
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // SUMMARY CARDS (2x2 grid)
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    // Row 1: Today's In and Today's Out
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // 1. Today's in
                        Card(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = tokens.card),
                            border = BorderStroke(0.5.dp, tokens.cardBorder),
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Today's in",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Normal,
                                        color = tokens.textMuted
                                    )
                                    Icon(
                                        imageVector = Icons.Default.SouthWest,
                                        contentDescription = "Inbound",
                                        tint = tokens.success,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = formatDashboardAmount(todayInPaise),
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = tokens.success
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Cash and UPI",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Normal,
                                    color = tokens.textMuted
                                )
                            }
                        }

                        // 2. Today's out
                        Card(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = tokens.card),
                            border = BorderStroke(0.5.dp, tokens.cardBorder),
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Today's out",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Normal,
                                        color = tokens.textMuted
                                    )
                                    Icon(
                                        imageVector = Icons.Default.NorthEast,
                                        contentDescription = "Outbound",
                                        tint = tokens.danger,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = formatDashboardAmount(todayOutPaise),
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = tokens.danger
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Expense and purchase",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Normal,
                                    color = tokens.textMuted
                                )
                            }
                        }
                    }

                    // Row 2: To collect and To pay
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // 3. To collect
                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { onNavigateToUnpaidCollect() }
                                .testTag("dashboard_to_collect_card"),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = tokens.card),
                            border = BorderStroke(0.5.dp, tokens.cardBorder),
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "To collect",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Normal,
                                        color = tokens.textMuted
                                    )
                                    Icon(
                                        imageVector = Icons.Default.SouthWest,
                                        contentDescription = "Collect",
                                        tint = tokens.success,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = formatDashboardAmount(totalReceivablePaise, compact = true),
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = tokens.text
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(4.dp)
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(tokens.cardBorder)
                                ) {
                                    val progress = if (totalReceivablePaise + totalPayablePaise > 0) {
                                        (totalReceivablePaise.toFloat() / (totalReceivablePaise + totalPayablePaise)).coerceIn(0.15f, 1f)
                                    } else 0.5f
                                    Box(
                                        modifier = Modifier
                                            .fillMaxHeight()
                                            .fillMaxWidth(progress)
                                            .background(tokens.success)
                                    )
                                }
                            }
                        }

                        // 4. To pay
                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { onNavigateToUnpaidPay() }
                                .testTag("dashboard_to_pay_card"),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = tokens.card),
                            border = BorderStroke(0.5.dp, tokens.cardBorder),
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "To pay",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Normal,
                                        color = tokens.textMuted
                                    )
                                    Icon(
                                        imageVector = Icons.Default.NorthEast,
                                        contentDescription = "Payable",
                                        tint = tokens.warning,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = formatDashboardAmount(totalPayablePaise, compact = true),
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = tokens.text
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(4.dp)
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(tokens.cardBorder)
                                ) {
                                    val progress = if (totalReceivablePaise + totalPayablePaise > 0) {
                                        (totalPayablePaise.toFloat() / (totalReceivablePaise + totalPayablePaise)).coerceIn(0.15f, 1f)
                                    } else 0.7f
                                    Box(
                                        modifier = Modifier
                                            .fillMaxHeight()
                                            .fillMaxWidth(progress)
                                            .background(tokens.warning)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // LOW STOCK ALERT (Real inventory data; hidden when 0 depleted items; collapsible for multiple items)
            if (lowStockItems.isNotEmpty()) {
                item {
                    val lowStockCount = lowStockItems.size
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("dashboard_low_stock_card"),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = tokens.card),
                        border = BorderStroke(0.5.dp, tokens.danger),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            // Header Banner
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        if (lowStockCount > 1) {
                                            isLowStockExpanded = !isLowStockExpanded
                                        } else {
                                            onNavigateToInventory(true)
                                        }
                                    },
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Warning,
                                        contentDescription = "Low stock warning",
                                        tint = tokens.danger,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = if (lowStockCount == 1) "Low stock alert (1 item)" else "Low stock alert ($lowStockCount items critical)",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = tokens.text
                                    )
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    TextButton(
                                        onClick = { onNavigateToInventory(true) },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                        modifier = Modifier
                                            .heightIn(min = 48.dp)
                                            .testTag("btn_view_inventory_low_stock")
                                    ) {
                                        Text(
                                            text = "View in Inventory",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = tokens.success
                                        )
                                        Spacer(modifier = Modifier.width(2.dp))
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                            contentDescription = "Go to inventory",
                                            tint = tokens.success,
                                            modifier = Modifier.size(12.dp)
                                        )
                                    }

                                    if (lowStockCount > 1) {
                                        IconButton(
                                            onClick = { isLowStockExpanded = !isLowStockExpanded },
                                            modifier = Modifier
                                                .size(48.dp)
                                                .testTag("btn_toggle_low_stock_expand")
                                        ) {
                                            Icon(
                                                imageVector = if (isLowStockExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                                contentDescription = if (isLowStockExpanded) "Collapse low stock items" else "Expand low stock items",
                                                tint = tokens.textMuted,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            // If single item or expanded multiple items, show product details
                            val shouldShowItems = lowStockCount == 1 || isLowStockExpanded
                            androidx.compose.animation.AnimatedVisibility(
                                visible = shouldShowItems,
                                enter = androidx.compose.animation.expandVertically() + androidx.compose.animation.fadeIn(),
                                exit = androidx.compose.animation.shrinkVertically() + androidx.compose.animation.fadeOut()
                            ) {
                                Column(modifier = Modifier.padding(top = 10.dp)) {
                                    lowStockItems.forEachIndexed { index, prod ->
                                        if (index > 0) {
                                            Spacer(modifier = Modifier.height(8.dp))
                                            HorizontalDivider(thickness = 0.5.dp, color = tokens.cardBorder)
                                            Spacer(modifier = Modifier.height(8.dp))
                                        }
                                        val isOutOfStock = prod.currentStockMilli <= 0L
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(8.dp))
                                                .clickable { onNavigateToInventory(true) }
                                                .padding(vertical = 4.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                                Text(
                                                    text = prod.name,
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    color = tokens.text,
                                                    maxLines = 1,
                                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                                )
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Text(
                                                    text = "HSN ${prod.hsnCode} · ${formatDashboardAmount(prod.salePricePaise)}",
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Normal,
                                                    color = tokens.textMuted
                                                )
                                            }

                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Surface(
                                                    color = if (isOutOfStock) tokens.dangerContainer else tokens.warningContainer,
                                                    shape = RoundedCornerShape(6.dp),
                                                    border = BorderStroke(0.5.dp, if (isOutOfStock) tokens.danger else tokens.warning)
                                                ) {
                                                    Text(
                                                        text = if (isOutOfStock) "Out of stock" else "${FinancialEngine.formatQuantityMilli(prod.currentStockMilli)} ${prod.unit} left",
                                                        color = if (isOutOfStock) tokens.danger else tokens.warning,
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.SemiBold,
                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                                    )
                                                }

                                                Spacer(modifier = Modifier.width(8.dp))

                                                OutlinedButton(
                                                    onClick = { onRestockProduct(prod.id, 10) },
                                                    modifier = Modifier.heightIn(min = 48.dp),
                                                    shape = RoundedCornerShape(8.dp),
                                                    border = BorderStroke(0.5.dp, tokens.cardBorder),
                                                    colors = ButtonDefaults.outlinedButtonColors(
                                                        containerColor = Color.Transparent,
                                                        contentColor = tokens.text
                                                    ),
                                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                                                ) {
                                                    Text(
                                                        text = "Restock",
                                                        fontSize = 12.sp,
                                                        fontWeight = FontWeight.Medium,
                                                        color = tokens.text
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))
                                    HorizontalDivider(thickness = 0.5.dp, color = tokens.cardBorder)
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Alert at ${uiState.lowStockThreshold} or less",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Normal,
                                            color = tokens.textMuted,
                                            modifier = Modifier
                                                .clickable {
                                                    thresholdInput = uiState.lowStockThreshold.toString()
                                                    showThresholdDialog = true
                                                }
                                                .padding(vertical = 8.dp)
                                        )
                                        TextButton(
                                            onClick = { onNavigateToInventory(true) },
                                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                                            modifier = Modifier.heightIn(min = 48.dp)
                                        ) {
                                            Text(
                                                text = "Open Inventory ($lowStockCount items) →",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = tokens.success
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // FAST COUNTER
            item {
                Column {
                    Text(
                        text = "Fast counter",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = tokens.text
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = onNavigateToInvoice,
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 52.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = tokens.primary,
                                contentColor = tokens.primaryText
                            ),
                            elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ReceiptLong,
                                    contentDescription = null,
                                    tint = tokens.primaryText,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "GST invoice",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = tokens.primaryText,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }

                        OutlinedButton(
                            onClick = onNavigateToDaybook,
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 52.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = tokens.card,
                                contentColor = tokens.text
                            ),
                            border = BorderStroke(0.5.dp, tokens.cardBorder),
                            elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CalendarToday,
                                    contentDescription = null,
                                    tint = tokens.textMuted,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Daybook",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = tokens.text,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    }
                }
            }

            // RECENT TRANSACTIONS
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Recent transactions",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = tokens.text
                    )
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                            .clickable { onNavigateToDaybook() }
                            .padding(horizontal = 8.dp)
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

            val todayTxns = branchTxns.filter { !it.isVoided && FinancialEngine.normalizeDate(it.date) == todayStr }

            if (todayTxns.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .dashedBorder(
                                width = 1.dp,
                                color = tokens.cardBorder,
                                cornerRadius = 12.dp,
                                dashLength = 6.dp,
                                gapLength = 4.dp
                            ),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = tokens.card),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp, horizontal = 16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.ReceiptLong,
                                contentDescription = null,
                                tint = tokens.textMuted,
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "No transactions today. Create your first bill.",
                                color = tokens.textMuted,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Normal,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                items(todayTxns.take(5), key = { it.id }) { txn ->
                    DashboardTransactionCard(
                        txn = txn,
                        uiState = uiState,
                        onVoid = onVoid
                    )
                }
            }
        }
    }
}

@Composable
fun TransactionRow(
    txn: Transaction,
    uiState: PabaruUiState,
    onVoid: (Transaction) -> Unit,
    onSharePdf: ((Transaction) -> Unit)? = null
) {
    val party = uiState.parties.find { it.id == txn.partyId }
    val isQuotation = txn.type == TransactionType.Quotation
    val isIn = txn.type == TransactionType.PaymentIn ||
        ((txn.type == TransactionType.Sale || txn.type == TransactionType.NonGstBill || txn.type == TransactionType.DebitNote) && txn.paymentMode != PaymentMode.Credit)
    val tokens = PabaruTheme.tokens

    val badgeContainer = when (txn.type) {
        TransactionType.Sale, TransactionType.NonGstBill, TransactionType.PaymentIn -> tokens.successContainer
        TransactionType.Quotation -> tokens.infoContainer
        else -> tokens.dangerContainer
    }
    val badgeText = when (txn.type) {
        TransactionType.Sale, TransactionType.NonGstBill, TransactionType.PaymentIn -> tokens.onSuccessContainer
        TransactionType.Quotation -> tokens.onInfoContainer
        else -> tokens.onDangerContainer
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = tokens.card),
        border = BorderStroke(1.dp, tokens.cardBorder),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = badgeContainer,
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            txn.type.name,
                            color = badgeText,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        txn.invoiceNumber.ifEmpty { txn.date },
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = tokens.textMuted
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    party?.name ?: (txn.remarks.ifEmpty { "Counter Customer" }),
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = tokens.text,
                    textDecoration = if (txn.isVoided) TextDecoration.LineThrough else TextDecoration.None
                )
                if (txn.isVoided) {
                    Text("[VOID: ${txn.voidReason}]", fontSize = 10.sp, color = tokens.danger, fontWeight = FontWeight.Bold)
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    if (isQuotation) FinancialEngine.formatPaise(txn.amountPaise)
                    else (if (isIn) "+" else "-") + FinancialEngine.formatPaise(txn.amountPaise),
                    fontWeight = FontWeight.Black,
                    fontSize = 14.sp,
                    color = when {
                        isQuotation -> tokens.textMuted
                        isIn -> tokens.success
                        else -> tokens.danger
                    }
                )
                Text(txn.paymentMode.name, fontSize = 10.sp, color = tokens.textMuted)

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (onSharePdf != null && txn.invoiceNumber.isNotEmpty()) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                                .clickable { onSharePdf(txn) }
                                .padding(horizontal = 8.dp)
                        ) {
                            Text(
                                "PDF",
                                color = tokens.link,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    if (!txn.isVoided) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                                .clickable { onVoid(txn) }
                                .padding(horizontal = 8.dp)
                        ) {
                            Text(
                                "Void",
                                color = tokens.danger,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DaybookScreen(
    uiState: PabaruUiState,
    onVoid: (Transaction) -> Unit,
    onSharePdf: (Transaction) -> Unit
) {
    val tokens = PabaruTheme.tokens
    var searchQuery by remember { mutableStateOf("") }

    val filtered = remember(uiState.transactions, uiState.activeBranchId, searchQuery) {
        uiState.transactions.filter { t ->
            t.branchId == uiState.activeBranchId &&
            if (searchQuery.isBlank()) true
            else {
                val q = searchQuery.lowercase()
                t.invoiceNumber.lowercase().contains(q) ||
                t.remarks.lowercase().contains(q) ||
                (uiState.parties.find { it.id == t.partyId }?.name?.lowercase()?.contains(q) == true)
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(tokens.bg).padding(16.dp)) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search invoice, party, remarks...", color = tokens.textMuted) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = tokens.text,
                unfocusedTextColor = tokens.text,
                focusedBorderColor = tokens.primary,
                unfocusedBorderColor = tokens.cardBorder
            )
        )
        Spacer(modifier = Modifier.height(12.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(filtered, key = { it.id }) { txn ->
                TransactionRow(txn = txn, uiState = uiState, onVoid = onVoid, onSharePdf = onSharePdf)
            }
        }
    }
}

@Composable
fun SecurityScreen(uiState: PabaruUiState, viewModel: PabaruViewModel, onSetPin: () -> Unit, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val tokens = PabaruTheme.tokens
    Column(modifier = Modifier.fillMaxSize().background(tokens.bg).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = tokens.text)
            }
            Text("Cryptographic Security & Audit", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = tokens.text)
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = tokens.card),
            border = BorderStroke(1.dp, tokens.cardBorder),
            shape = RoundedCornerShape(14.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Audit Chain Status", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = tokens.text)
                Text("Traces forward from GENESIS_HASH (0000...0000)", fontSize = 11.sp, color = tokens.textMuted)
                Text(
                    if (uiState.auditResult.valid) "Chain Fully Verified: ${uiState.auditLogs.size} blocks" else "Tampering Detected!",
                    fontWeight = FontWeight.Bold,
                    color = if (uiState.auditResult.valid) tokens.success else tokens.danger
                )
                Button(
                    onClick = { viewModel.verifyAuditChain() },
                    modifier = Modifier.heightIn(min = 48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = tokens.primary, contentColor = tokens.primaryText),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Verify Hash Chain", color = tokens.primaryText, fontWeight = FontWeight.Bold)
                }
                Button(
                    onClick = onSetPin,
                    modifier = Modifier.heightIn(min = 48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = tokens.secondaryBg),
                    border = BorderStroke(1.dp, tokens.cardBorder),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Set Master Security PIN", color = tokens.text)
                }
            }
        }
    }
}

@Composable
fun CrossPlatformInfoScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val tokens = PabaruTheme.tokens
    Column(modifier = Modifier.fillMaxSize().background(tokens.bg).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = tokens.text)
            }
            Text("Run on Android, iOS & Desktop", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = tokens.text)
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = tokens.card),
            border = BorderStroke(1.dp, tokens.cardBorder),
            shape = RoundedCornerShape(14.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Android: Running natively as APK.", fontSize = 12.sp, color = tokens.text)
                Text("iOS / Mobile Web: Open web dashboard, tap Share -> 'Add to Home Screen'.", fontSize = 12.sp, color = tokens.text)
                Text("Desktop: Install via Chrome/Edge URL bar icon.", fontSize = 12.sp, color = tokens.text)
            }
        }
    }
}
