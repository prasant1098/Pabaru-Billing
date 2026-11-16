package com.example.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import com.example.engine.BankReconEngine
import com.example.engine.CmaAndGstEngine
import com.example.engine.CryptoEngine
import com.example.engine.FinancialEngine
import com.example.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.firstOrNull
import androidx.room.withTransaction
import java.util.Locale
import java.util.UUID

data class PabaruUiState(
    val profile: BusinessProfile = BusinessProfile(isRegistered = false), // Starts at Sign-in/Register
    val branches: List<Branch> = emptyList(),
    val activeBranchId: String = "BR-MAIN",
    val parties: List<Party> = emptyList(),
    val bankAccounts: List<BankAccount> = emptyList(),
    val bankReconItems: List<BankReconItem> = emptyList(),
    val products: List<ProductItem> = emptyList(),
    val transactions: List<Transaction> = emptyList(),
    val auditLogs: List<AuditLog> = emptyList(),
    val auditResult: CryptoEngine.VerificationResult = CryptoEngine.VerificationResult(valid = true, totalCount = 0),
    val currentUserRole: String = "owner", // Business Owner default
    val currentUserName: String = "Pabaru Rameshwar (Owner)",
    val securityPin: String? = null,
    val isAppLocked: Boolean = false,
    val recoveryPhrase: String? = null,
    val lowStockThreshold: Int = 15,
    val themeMode: String = "auto", // "auto" (follow device) or theme id
    val currentTheme: AppColorTheme = AppColorTheme.ClinicBlue,
    val isDatabaseReady: Boolean = false,
    val upiQrs: List<UpiQrCode> = emptyList(),
    val qrActivityLogs: List<QrActivityLog> = emptyList(),
    val managerPinHash: String? = null,
    val managerPinSalt: String = CryptoEngine.generateSecureSalt(),
    val ownerPinHash: String? = null,
    val ownerPinSalt: String = CryptoEngine.generateSecureSalt(),
    val masterPinHash: String? = null,
    val masterPinSalt: String = CryptoEngine.generateSecureSalt(),
    val managerFailedAttempts: Int = 0,
    val managerLockoutUntilTimestamp: Long = 0L,
    val loginFailedAttempts: Int = 0,
    val loginLockoutUntilTimestamp: Long = 0L,
    val useFingerprintForManager: Boolean = false,
    val autoLockMinutes: Int = 5,
    val lastManagerUnlockTimestamp: Long = 0L,
    val isManagerUnlocked: Boolean = false,
    val prefilledGstr1Invoices: List<com.example.engine.Gstr1ExcelImportEngine.ImportedGstr1Invoice> = emptyList(),
    val rule88AStrategy: com.example.engine.CmaAndGstEngine.Rule88AStrategy = com.example.engine.CmaAndGstEngine.Rule88AStrategy.OPTIMAL,
    val customRule88APercent: Double? = null,
    val customRule88APaise: Long? = null,
    val isSuperAdminSession: Boolean = false,
    val isGstUnlocked: Boolean = false,
    val isBankReconUnlocked: Boolean = false,
    val isInvoiceUnlocked: Boolean = true,
    val isInventoryUnlocked: Boolean = true,
    val isCmaUnlocked: Boolean = true,
    val isStaffUnlocked: Boolean = true,
    val isExportUnlocked: Boolean = true,
    val isManagerOverrideUnlocked: Boolean = true,
    val localSyncStatus: String = "All permissions persisted locally",
    val localPendingSyncCount: Int = 0,
    val lastSyncTimestamp: Long = System.currentTimeMillis(),
    val isSyncing: Boolean = false,
    val managedUsers: List<ManagedUserEntity> = emptyList(),
    val inventoryLocations: List<InventoryLocation> = emptyList(),
    val stockBatches: List<StockBatch> = emptyList(),
    val productVariants: List<ProductVariant> = emptyList(),
    val stockAdjustments: List<StockAdjustment> = emptyList(),
    val stockMovements: List<StockMovement> = emptyList(),
    val authErrorMessage: String? = null
) {
    fun isScreenAllocated(screenId: String): Boolean = true
    fun getAllocatedScreensForCurrent(): Set<String> = AllocatableScreen.ALL_SCREEN_IDS
}

class PabaruViewModel : ViewModel() {

    companion object {
    }

    private val _uiState = MutableStateFlow(PabaruUiState())
    val uiState: StateFlow<PabaruUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val events: SharedFlow<String> = _events.asSharedFlow()

    @Volatile
    private var database: PabaruDatabase? = null
    private val isDatabaseInitializing = java.util.concurrent.atomic.AtomicBoolean(false)
    private var appContext: Context? = null
    private val auditLock = Any()
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private val auditIoDispatcher = Dispatchers.IO.limitedParallelism(1)
    private val invoiceSeqLock = Any()
    private val fyPrefixCounters = mutableMapOf<Pair<String, String>, Long>()
    private val tenantCredentials = java.util.concurrent.ConcurrentHashMap<String, Pair<String, String>>()

    fun launchDb(errorMessage: String = "Database operation failed", block: suspend (PabaruDatabase) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val db = database
            if (db == null) {
                _events.tryEmit("Database not ready")
                return@launch
            }
            try {
                block(db)
            } catch (e: Exception) {
                val msg = e.message ?: errorMessage
                android.util.Log.e("PabaruViewModel", "DB Error: $msg", e)
                _events.tryEmit("$errorMessage: $msg")
            }
        }
    }

    private fun securityPrefs(): android.content.SharedPreferences? =
        appContext?.getSharedPreferences("pabaru_security_prefs", Context.MODE_PRIVATE)

    private fun appPrefs(): android.content.SharedPreferences? =
        appContext?.getSharedPreferences("pabaru_prefs", Context.MODE_PRIVATE)

    fun initDatabase(context: Context) {
        val appCtx = context.applicationContext
        appContext = appCtx
        // Restore persistent lockout, credentials, and preferences from disk
        val secPrefs = appCtx.getSharedPreferences("pabaru_security_prefs", Context.MODE_PRIVATE)
        val prefs = appCtx.getSharedPreferences("pabaru_prefs", Context.MODE_PRIVATE)
        val savedAttempts = secPrefs.getInt("login_failed_attempts", 0)
        val savedLockout = secPrefs.getLong("login_lockout_until", 0L)
        val savedMgrAttempts = secPrefs.getInt("manager_failed_attempts", 0)
        val savedMgrLockout = secPrefs.getLong("manager_lockout_until", 0L)
        val savedOwnerHash = secPrefs.getString("owner_pin_hash", null)
        val savedOwnerSalt = secPrefs.getString("owner_pin_salt", null)
        val savedMgrHash = secPrefs.getString("manager_pin_hash", null)
        val savedMgrSalt = secPrefs.getString("manager_pin_salt", null)
        val savedMasterHash = secPrefs.getString("master_pin_hash", null)
        val savedMasterSalt = secPrefs.getString("master_pin_salt", null)
        val savedUseFp = secPrefs.getBoolean("use_fingerprint_for_manager", false)
        val savedAutoLock = secPrefs.getInt("auto_lock_minutes", 5)
        val savedAppLocked = secPrefs.getBoolean("is_app_locked", false)
        val savedThreshold = prefs.getInt("low_stock_threshold", 15).coerceAtLeast(1)
        val savedBranchId = prefs.getString("active_branch_id", "BR-MAIN") ?: "BR-MAIN"
        val savedStrategy = prefs.getString("rule88a_strategy", null)?.let { name ->
            runCatching { com.example.engine.CmaAndGstEngine.Rule88AStrategy.valueOf(name) }.getOrNull()
        } ?: com.example.engine.CmaAndGstEngine.Rule88AStrategy.OPTIMAL
        val savedCustomPct = if (prefs.contains("rule88a_custom_percent")) {
            Double.fromBits(prefs.getLong("rule88a_custom_percent", 0L))
        } else null
        val savedCustomPaise = if (prefs.contains("rule88a_custom_paise")) {
            prefs.getLong("rule88a_custom_paise", 0L)
        } else null

        val isExplicitlySignedOut = secPrefs.contains("is_signed_in") && !secPrefs.getBoolean("is_signed_in", true)
        _uiState.update {
            it.copy(
                loginFailedAttempts = savedAttempts,
                loginLockoutUntilTimestamp = savedLockout,
                managerFailedAttempts = savedMgrAttempts,
                managerLockoutUntilTimestamp = savedMgrLockout,
                ownerPinHash = savedOwnerHash ?: it.ownerPinHash,
                ownerPinSalt = savedOwnerSalt ?: it.ownerPinSalt,
                managerPinHash = savedMgrHash ?: it.managerPinHash,
                managerPinSalt = savedMgrSalt ?: it.managerPinSalt,
                masterPinHash = savedMasterHash ?: it.masterPinHash,
                masterPinSalt = savedMasterSalt ?: it.masterPinSalt,
                useFingerprintForManager = savedUseFp,
                autoLockMinutes = savedAutoLock,
                isAppLocked = savedAppLocked && !(savedOwnerHash ?: it.ownerPinHash).isNullOrEmpty(),
                lowStockThreshold = savedThreshold,
                activeBranchId = savedBranchId,
                rule88AStrategy = savedStrategy,
                customRule88APercent = savedCustomPct,
                customRule88APaise = savedCustomPaise
            )
        }

        if (database != null || !isDatabaseInitializing.compareAndSet(false, true)) return
        val db = PabaruDatabase.getDatabase(context)
        database = db

        // Collect every table into UI state reactively. Room is the single source of truth.
        fun <E, D> collectInto(
            flow: kotlinx.coroutines.flow.Flow<List<E>>,
            map: (E) -> D,
            apply: (PabaruUiState, List<D>) -> PabaruUiState
        ) = viewModelScope.launch(Dispatchers.IO) {
            flow.collect { rows ->
                _uiState.update { apply(it, rows.map(map)) }
            }
        }

        collectInto(db.transactionDao().getAllTransactions(), { it.toDomain() }) { s, l -> s.copy(transactions = l) }
        collectInto(db.partyDao().getAllParties(), { it.toDomain() }) { s, l -> s.copy(parties = l) }
        collectInto(db.productDao().getAllProductsIncludeArchived(), { it.toDomain() }) { s, l -> s.copy(products = l) }
        collectInto(db.productVariantDao().getAllVariants(), { it.toDomain() }) { s, l -> s.copy(productVariants = l) }
        collectInto(db.stockBatchDao().getAllActiveBatches(), { it.toDomain() }) { s, l -> s.copy(stockBatches = l) }
        collectInto(db.stockMovementDao().getAllMovements(), { it.toDomain() }) { s, l -> s.copy(stockMovements = l) }
        collectInto(db.stockAdjustmentDao().getAllAdjustments(), { it.toDomain() }) { s, l -> s.copy(stockAdjustments = l) }
        collectInto(db.inventoryLocationDao().getAllLocations(), { it.toDomain() }) { s, l -> s.copy(inventoryLocations = l) }
        collectInto(db.bankAccountDao().getAllBankAccounts(), { it.toDomain() }) { s, l -> s.copy(bankAccounts = l) }
        collectInto(db.managedUserDao().getAllManagedUsers(), { it }) { s, l -> s.copy(managedUsers = l) }
        collectInto(db.bankReconDao().getAllReconItems(), { it.toDomain() }) { s, l -> s.copy(bankReconItems = l) }
        collectInto(db.upiQrDao().getAllQrCodes(), { it.toDomain() }) { s, l -> s.copy(upiQrs = l) }
        collectInto(db.branchDao().getAllBranches(), { it.toDomain() }) { s, l -> s.copy(branches = l) }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Load persisted audit logs before recording genesis or subscribing to the Flow collector
                val persistedLogs = db.auditLogDao().getAllAuditLogsSync().map { it.toDomain() }
                if (persistedLogs.isNotEmpty()) {
                    synchronized(auditLock) {
                        _uiState.update {
                            it.copy(
                                auditLogs = persistedLogs,
                                auditResult = CryptoEngine.verifyAuditChain(persistedLogs)
                            )
                        }
                    }
                } else {
                    recordAudit("System", "INIT", "GENESIS", "Database initial state loaded", "Initial seed", "System")
                    verifyAuditChain()
                }
            } catch (e: Exception) {
                android.util.Log.e("PabaruViewModel", "Error initializing audit logs: ${e.message}", e)
            }

            collectInto(db.auditLogDao().getAllAuditLogs(), { it.toDomain() }) { s, l ->
                s.copy(auditLogs = l, auditResult = CryptoEngine.verifyAuditChain(l))
            }

            try {
                // Ensure initial profile is read before setting database ready
                val p = db.businessProfileDao().getProfile().firstOrNull()
                if (p != null) {
                    val domainProfile = p.toDomain()
                    val effectiveProfile = if (isExplicitlySignedOut) domainProfile.copy(isRegistered = false) else domainProfile
                    _uiState.update { it.copy(profile = effectiveProfile, isDatabaseReady = true) }
                } else {
                    _uiState.update { it.copy(isDatabaseReady = true) }
                }
            } catch (e: Exception) {
                android.util.Log.e("PabaruViewModel", "Error loading initial profile: ${e.message}", e)
                _uiState.update { it.copy(isDatabaseReady = true) }
            }

            // Continue collecting subsequent profile changes
            try {
                db.businessProfileDao().getProfile().collect { p ->
                    if (p != null) {
                        val domainProfile = p.toDomain()
                        val signedOutNow = securityPrefs()?.let { it.contains("is_signed_in") && !it.getBoolean("is_signed_in", true) } ?: false
                        _uiState.update { it.copy(profile = if (signedOutNow) domainProfile.copy(isRegistered = false) else domainProfile) }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("PabaruViewModel", "Profile collection error: ${e.message}", e)
            }
        }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val existing = db.branchDao().getAllBranches().firstOrNull()
                if (existing.isNullOrEmpty()) {
                    db.branchDao().insertBranch(BranchEntity("BR-MAIN", "Main Branch", "MAIN", true, "Head Office"))
                }
            } catch (e: Exception) {
                android.util.Log.e("PabaruViewModel", "Error initializing main branch: ${e.message}", e)
            }
        }
    }

    fun toggleUserActivation(storeCode: String, isActive: Boolean) {
        val updated = _uiState.value.managedUsers.map {
            if (it.storeCode.equals(storeCode, ignoreCase = true)) it.copy(isActive = isActive) else it
        }
        _uiState.update { it.copy(managedUsers = updated) }
        if (database != null) {
            launchDb("Failed to update user activation") { db ->
                db.managedUserDao().updateUserStatus(storeCode, isActive)
            }
        }
    }

    fun setUserBenefits(
        storeCode: String,
        isGst: Boolean,
        isRecon: Boolean,
        isInvoice: Boolean,
        isInv: Boolean,
        isCma: Boolean,
        isStaff: Boolean,
        isExport: Boolean = true,
        isOverride: Boolean = true
    ) {
        val now = System.currentTimeMillis()
        _uiState.update { s ->
            val updated = s.managedUsers.map {
                if (it.storeCode.equals(storeCode, ignoreCase = true)) {
                    it.copy(
                        isGstUnlocked = isGst,
                        isBankReconUnlocked = isRecon,
                        isInvoiceUnlocked = isInvoice,
                        isInventoryUnlocked = isInv,
                        isCmaUnlocked = isCma,
                        isStaffUnlocked = isStaff,
                        isExportUnlocked = isExport,
                        isManagerOverrideUnlocked = isOverride,
                        lastSyncTimestamp = now
                    )
                } else it
            }
            val matchesCurrent = s.profile.storeCode.equals(storeCode, ignoreCase = true)
            s.copy(
                managedUsers = updated,
                lastSyncTimestamp = now,
                localSyncStatus = "Permissions updated & persisted locally",
                isGstUnlocked = if (matchesCurrent) isGst else s.isGstUnlocked,
                isBankReconUnlocked = if (matchesCurrent) isRecon else s.isBankReconUnlocked,
                isInvoiceUnlocked = if (matchesCurrent) isInvoice else s.isInvoiceUnlocked,
                isInventoryUnlocked = if (matchesCurrent) isInv else s.isInventoryUnlocked,
                isCmaUnlocked = if (matchesCurrent) isCma else s.isCmaUnlocked,
                isStaffUnlocked = if (matchesCurrent) isStaff else s.isStaffUnlocked,
                isExportUnlocked = if (matchesCurrent) isExport else s.isExportUnlocked,
                isManagerOverrideUnlocked = if (matchesCurrent) isOverride else s.isManagerOverrideUnlocked
            )
        }

        if (database != null) {
            launchDb("Failed to update user permissions") { db ->
                db.managedUserDao().updateUserBenefits(
                    storeCode = storeCode,
                    isGst = isGst,
                    isRecon = isRecon,
                    isInvoice = isInvoice,
                    isInv = isInv,
                    isCma = isCma,
                    isStaff = isStaff,
                    isExport = isExport,
                    isOverride = isOverride,
                    syncTime = now
                )
            }
        }
    }

    fun bulkToggleUserStatus(storeCodes: List<String>, isActive: Boolean) {
        if (storeCodes.isEmpty()) return
        val set = storeCodes.map { it.uppercase(Locale.ROOT) }.toSet()
        val now = System.currentTimeMillis()
        _uiState.update { s ->
            val updated = s.managedUsers.map {
                if (set.contains(it.storeCode.uppercase(Locale.ROOT))) it.copy(isActive = isActive, lastSyncTimestamp = now) else it
            }
            s.copy(
                managedUsers = updated,
                lastSyncTimestamp = now,
                localSyncStatus = "Bulk status updated for ${storeCodes.size} tenants"
            )
        }
        if (database != null) {
            launchDb("Failed to update bulk user status") { db ->
                db.managedUserDao().updateUsersStatus(storeCodes, isActive)
            }
        }
    }

    fun bulkUpdateUserBenefits(
        storeCodes: List<String>,
        isGst: Boolean,
        isRecon: Boolean,
        isInvoice: Boolean,
        isInv: Boolean,
        isCma: Boolean,
        isStaff: Boolean,
        isExport: Boolean,
        isOverride: Boolean
    ) {
        if (storeCodes.isEmpty()) return
        val set = storeCodes.map { it.uppercase(Locale.ROOT) }.toSet()
        val now = System.currentTimeMillis()
        _uiState.update { s ->
            val updated = s.managedUsers.map {
                if (set.contains(it.storeCode.uppercase(Locale.ROOT))) {
                    it.copy(
                        isGstUnlocked = isGst,
                        isBankReconUnlocked = isRecon,
                        isInvoiceUnlocked = isInvoice,
                        isInventoryUnlocked = isInv,
                        isCmaUnlocked = isCma,
                        isStaffUnlocked = isStaff,
                        isExportUnlocked = isExport,
                        isManagerOverrideUnlocked = isOverride,
                        lastSyncTimestamp = now
                    )
                } else it
            }
            val matchesCurrent = set.contains(s.profile.storeCode.uppercase(Locale.ROOT))
            s.copy(
                managedUsers = updated,
                lastSyncTimestamp = now,
                localSyncStatus = "Bulk permissions updated for ${storeCodes.size} tenants",
                isGstUnlocked = if (matchesCurrent) isGst else s.isGstUnlocked,
                isBankReconUnlocked = if (matchesCurrent) isRecon else s.isBankReconUnlocked,
                isInvoiceUnlocked = if (matchesCurrent) isInvoice else s.isInvoiceUnlocked,
                isInventoryUnlocked = if (matchesCurrent) isInv else s.isInventoryUnlocked,
                isCmaUnlocked = if (matchesCurrent) isCma else s.isCmaUnlocked,
                isStaffUnlocked = if (matchesCurrent) isStaff else s.isStaffUnlocked,
                isExportUnlocked = if (matchesCurrent) isExport else s.isExportUnlocked,
                isManagerOverrideUnlocked = if (matchesCurrent) isOverride else s.isManagerOverrideUnlocked
            )
        }
        if (database != null) {
            launchDb("Failed to bulk update permissions") { db ->
                db.managedUserDao().bulkUpdateBenefits(
                    storeCodes = storeCodes,
                    isGst = isGst,
                    isRecon = isRecon,
                    isInvoice = isInvoice,
                    isInv = isInv,
                    isCma = isCma,
                    isStaff = isStaff,
                    isExport = isExport,
                    isOverride = isOverride,
                    syncTime = now
                )
            }
        }
    }

    // -------------------------------------------------------------
    // Super Admin Management (Master PIN with PBKDF2 Hashing)
    // -------------------------------------------------------------
    fun verifyMasterPin(pin: String): Boolean {
        val trimmed = pin.trim()
        val s = _uiState.value
        val expectedHash = s.masterPinHash
        val salt = s.masterPinSalt
        if (expectedHash.isNullOrEmpty()) {
            // Default initial master PIN "998877" PBKDF2 check on first setup before custom PIN is configured
            val defaultHash = CryptoEngine.hashPinString("998877", salt)
            return java.security.MessageDigest.isEqual(
                CryptoEngine.hashPinString(trimmed, salt).toByteArray(java.nio.charset.StandardCharsets.UTF_8),
                defaultHash.toByteArray(java.nio.charset.StandardCharsets.UTF_8)
            )
        }
        val computed = CryptoEngine.hashPinString(trimmed, salt)
        return java.security.MessageDigest.isEqual(
            computed.toByteArray(java.nio.charset.StandardCharsets.UTF_8),
            expectedHash.toByteArray(java.nio.charset.StandardCharsets.UTF_8)
        )
    }

    fun setMasterPin(newPin: String) {
        val clean = newPin.trim()
        require(clean.length in 4..6 && clean.all { it.isDigit() }) {
            "Master PIN must be 4 to 6 numeric digits"
        }
        val salt = CryptoEngine.generateSecureSalt()
        val hash = CryptoEngine.hashPinString(clean, salt)
        securityPrefs()?.edit()
            ?.putString("master_pin_hash", hash)
            ?.putString("master_pin_salt", salt)
            ?.apply()
        _uiState.update { it.copy(masterPinHash = hash, masterPinSalt = salt) }
    }

    fun authenticateAndEnterSuperAdmin(pin: String): Boolean {
        if (verifyMasterPin(pin)) {
            _uiState.update {
                it.copy(
                    isSuperAdminSession = true,
                    authErrorMessage = null
                )
            }
            return true
        }
        return false
    }

    fun enterSuperAdminMode() {
        if (com.example.BuildConfig.DEBUG) {
            _uiState.update {
                it.copy(
                    isSuperAdminSession = true,
                    authErrorMessage = null
                )
            }
        }
    }

    fun exitSuperAdminMode() {
        _uiState.update {
            it.copy(isSuperAdminSession = false)
        }
    }

    fun triggerLocalSync() {
        viewModelScope.launch {
            _uiState.update { it.copy(isSyncing = true, localSyncStatus = "Synchronizing local permissions...") }
            kotlinx.coroutines.delay(350)
            val now = System.currentTimeMillis()
            try {
                database?.managedUserDao()?.updateAllSyncTimestamps(now)
            } catch (e: Exception) {
                android.util.Log.e("PabaruViewModel", "Local sync DB error: ${e.message}", e)
            }
            _uiState.update { s ->
                val updated = s.managedUsers.map { it.copy(lastSyncTimestamp = now) }
                s.copy(
                    isSyncing = false,
                    managedUsers = updated,
                    lastSyncTimestamp = now,
                    localPendingSyncCount = 0,
                    localSyncStatus = "All tenant permissions synced locally"
                )
            }
        }
    }

    fun addNewManagedUser(newUser: ManagedUserEntity) {
        _uiState.update { s ->
            val updated = listOf(newUser) + s.managedUsers.filter { it.storeCode != newUser.storeCode }
            s.copy(managedUsers = updated)
        }
        if (database != null) {
            launchDb("Failed to add managed user") { db ->
                db.managedUserDao().insertOrUpdateUser(newUser)
            }
        }
    }

    fun deleteManagedUser(storeCode: String) {
        _uiState.update { s ->
            s.copy(managedUsers = s.managedUsers.filterNot { it.storeCode.equals(storeCode, ignoreCase = true) })
        }
        if (database != null) {
            launchDb("Failed to delete managed user") { db ->
                db.managedUserDao().deleteUser(storeCode)
            }
        }
    }

    fun impersonateUser(storeCode: String) {
        if (!_uiState.value.isSuperAdminSession) return
        _uiState.update { state ->
            val user = state.managedUsers.find { it.storeCode.equals(storeCode, ignoreCase = true) }
                ?: return@update state
            val isCurrentStore = state.profile.storeCode.equals(user.storeCode, ignoreCase = true)
            state.copy(
                isSuperAdminSession = false,
                profile = state.profile.copy(
                    storeCode = user.storeCode,
                    businessName = user.businessName,
                    ownerName = user.ownerName,
                    phone = user.phone,
                    email = user.email,
                    isRegistered = true
                ),
                isGstUnlocked = user.isGstUnlocked,
                isBankReconUnlocked = user.isBankReconUnlocked,
                isInvoiceUnlocked = user.isInvoiceUnlocked,
                isInventoryUnlocked = user.isInventoryUnlocked,
                isCmaUnlocked = user.isCmaUnlocked,
                isStaffUnlocked = user.isStaffUnlocked,
                isExportUnlocked = user.isExportUnlocked,
                isManagerOverrideUnlocked = user.isManagerOverrideUnlocked,
                currentUserRole = "owner",
                currentUserName = "${user.ownerName} (Owner)",
                parties = if (isCurrentStore) state.parties else emptyList(),
                products = if (isCurrentStore) state.products else emptyList(),
                bankAccounts = if (isCurrentStore) state.bankAccounts else emptyList(),
                bankReconItems = if (isCurrentStore) state.bankReconItems else emptyList(),
                upiQrs = if (isCurrentStore) state.upiQrs else emptyList(),
                stockBatches = if (isCurrentStore) state.stockBatches else emptyList(),
                stockMovements = if (isCurrentStore) state.stockMovements else emptyList(),
                stockAdjustments = if (isCurrentStore) state.stockAdjustments else emptyList(),
                productVariants = if (isCurrentStore) state.productVariants else emptyList(),
                prefilledGstr1Invoices = if (isCurrentStore) state.prefilledGstr1Invoices else emptyList(),
                transactions = if (isCurrentStore) state.transactions else emptyList()
            )
        }
    }

    // -------------------------------------------------------------
    // Authentication & Registration (Server Auth Handshake)
    // -------------------------------------------------------------
    fun registerBusiness(
        businessName: String,
        entityType: BusinessEntityType,
        signatoryName: String,
        phone: String,
        pin: String,
        stateCode: String
    ) {
        val randStoreCode = "TL-${(1000..9999).random()}"
        val updatedProfile = _uiState.value.profile.copy(
            businessName = businessName,
            entityType = entityType,
            ownerName = signatoryName,
            phone = phone,
            homeStateCode = stateCode,
            storeCode = randStoreCode,
            isRegistered = true
        )
        val phrase = CryptoEngine.generateRecoveryPhrase()
        val salt = CryptoEngine.generateSecureSalt()
        val pinHash = CryptoEngine.hashPinString(pin.trim(), salt)

        val newManaged = ManagedUserEntity(
            storeCode = randStoreCode,
            businessName = businessName,
            ownerName = signatoryName,
            phone = phone,
            email = "",
            isActive = true,
            isGstUnlocked = false,
            isBankReconUnlocked = false,
            isInvoiceUnlocked = true,
            isInventoryUnlocked = true,
            isCmaUnlocked = true,
            isStaffUnlocked = true,
            createdDate = FinancialEngine.getTodayDate()
        )
        val updatedManagedList = listOf(newManaged) + _uiState.value.managedUsers.filter { it.storeCode != randStoreCode }

        val storeKey = randStoreCode.uppercase(Locale.ROOT)
        tenantCredentials[storeKey] = pinHash to salt
        securityPrefs()?.edit()
            ?.putString("owner_pin_hash", pinHash)
            ?.putString("owner_pin_salt", salt)
            ?.putString("tenant_pin_hash_$storeKey", pinHash)
            ?.putString("tenant_pin_salt_$storeKey", salt)
            ?.putBoolean("is_signed_in", true)
            ?.putBoolean("is_app_locked", false)
            ?.putInt("login_failed_attempts", 0)
            ?.putLong("login_lockout_until", 0L)
            ?.apply()

        _uiState.update {
            it.copy(
                profile = updatedProfile,
                currentUserName = "$signatoryName (${entityType.signatoryTitle})",
                securityPin = null, // Plaintext PIN eliminated from memory
                ownerPinHash = pinHash,
                ownerPinSalt = salt,
                recoveryPhrase = phrase,
                managedUsers = updatedManagedList,
                authErrorMessage = null
            )
        }

        if (database != null) {
            launchDb("Failed to save registered business") { db ->
                db.managedUserDao().insertOrUpdateUser(newManaged)
                db.businessProfileDao().insertOrUpdateProfile(BusinessProfileEntity.fromDomain(updatedProfile))
            }
        }

        recordAudit("BusinessProfile", updatedProfile.id, "REGISTER", "New business registered: $businessName", "Initial sign up", signatoryName)
        verifyAuditChain()
    }

    fun signInDemo(): Boolean {
        val s = _uiState.value
        val hasCustomBusiness = s.ownerPinHash != null && !s.profile.storeCode.equals("TL-7319", ignoreCase = true)
        if (hasCustomBusiness || s.managedUsers.any { !it.storeCode.equals("TL-7319", ignoreCase = true) }) {
            return signIn("TL-7319", "1234")
        }
        if (s.ownerPinHash.isNullOrEmpty()) {
            val demoSalt = CryptoEngine.generateSecureSalt()
            val demoHash = CryptoEngine.hashPinString("1234", demoSalt)
            val demoProfile = s.profile.copy(storeCode = "TL-7319", isRegistered = false)
            val demoUser = ManagedUserEntity(
                storeCode = "TL-7319",
                businessName = demoProfile.businessName.ifBlank { "Pabaru Billing Enterprises" },
                ownerName = demoProfile.ownerName.ifBlank { "Pabaru Rameshwar" },
                phone = demoProfile.phone.ifBlank { "9820011223" },
                email = demoProfile.email,
                isActive = true,
                isGstUnlocked = true,
                isBankReconUnlocked = true,
                isInvoiceUnlocked = true,
                isInventoryUnlocked = true,
                isCmaUnlocked = true,
                isStaffUnlocked = true,
                isExportUnlocked = true,
                isManagerOverrideUnlocked = true,
                createdDate = FinancialEngine.getTodayDate()
            )
            tenantCredentials["TL-7319"] = demoHash to demoSalt
            securityPrefs()?.edit()
                ?.putString("owner_pin_hash", demoHash)
                ?.putString("owner_pin_salt", demoSalt)
                ?.putString("tenant_pin_hash_TL-7319", demoHash)
                ?.putString("tenant_pin_salt_TL-7319", demoSalt)
                ?.apply()
            _uiState.update {
                it.copy(
                    profile = demoProfile,
                    ownerPinHash = demoHash,
                    ownerPinSalt = demoSalt,
                    managedUsers = listOf(demoUser) + it.managedUsers.filter { u -> u.storeCode != "TL-7319" }
                )
            }
            if (database != null) {
                launchDb("Failed to seed demo store") { db ->
                    db.managedUserDao().insertOrUpdateUser(demoUser)
                }
            }
        }
        return signIn("TL-7319", "1234")
    }


    fun signIn(storeCodeOrPhone: String, pin: String): Boolean {
        val trimmedInput = storeCodeOrPhone.trim()
        val cleanPin = pin.trim()
        if (trimmedInput.isBlank() || cleanPin.isBlank()) {
            _uiState.update {
                it.copy(authErrorMessage = "Store Code / Phone and PIN are required.")
            }
            return false
        }
        val s = _uiState.value
        val now = System.currentTimeMillis()

        // Brute force lockout throttle
        if (s.loginLockoutUntilTimestamp > now) {
            val waitSec = (s.loginLockoutUntilTimestamp - now) / 1000
            _uiState.update { it.copy(authErrorMessage = "Too many failed attempts. Try again in ${waitSec}s.") }
            return false
        }

        val p = s.profile
        val matchedManaged = s.managedUsers.find {
            it.storeCode.equals(trimmedInput, ignoreCase = true) ||
            (it.phone.isNotBlank() && it.phone == trimmedInput) ||
            (it.email.isNotBlank() && it.email.equals(trimmedInput, ignoreCase = true))
        }

        // Check account activation
        if (matchedManaged != null && !matchedManaged.isActive) {
            _uiState.update {
                it.copy(authErrorMessage = "Account Suspended: This business account has been deactivated by the Super Admin.")
            }
            return false
        }

        val isIdentifierMatched = trimmedInput.equals(p.storeCode, ignoreCase = true) ||
            (p.phone.isNotBlank() && trimmedInput == p.phone) ||
            (p.email.isNotBlank() && trimmedInput.equals(p.email, ignoreCase = true)) ||
            matchedManaged != null

        val resolvedStore = matchedManaged?.storeCode ?: p.storeCode
        val storeKey = resolvedStore.uppercase(Locale.ROOT)
        val memCred = tenantCredentials[storeKey]
        val secPrefs = securityPrefs()
        val prefHash = secPrefs?.getString("tenant_pin_hash_$storeKey", null)
        val prefSalt = secPrefs?.getString("tenant_pin_salt_$storeKey", null)

        val (storedHash, salt) = when {
            memCred != null -> memCred
            !prefHash.isNullOrEmpty() -> prefHash to prefSalt
            resolvedStore.equals(p.storeCode, ignoreCase = true) || matchedManaged == null -> s.ownerPinHash to s.ownerPinSalt
            else -> null to null
        }

        val isPinCorrect = if (!storedHash.isNullOrEmpty() && !salt.isNullOrEmpty()) {
            val computed = CryptoEngine.hashPinString(cleanPin, salt)
            java.security.MessageDigest.isEqual(
                computed.toByteArray(java.nio.charset.StandardCharsets.UTF_8),
                storedHash.toByteArray(java.nio.charset.StandardCharsets.UTF_8)
            )
        } else {
            false
        }

        if (isIdentifierMatched && isPinCorrect) {
            val resolvedName = matchedManaged?.ownerName ?: p.ownerName
            val resolvedBiz = matchedManaged?.businessName ?: p.businessName
            val isSameStore = resolvedStore.equals(p.storeCode, ignoreCase = true)

            val updatedProfile = p.copy(
                isRegistered = true,
                storeCode = resolvedStore,
                businessName = resolvedBiz,
                ownerName = resolvedName,
                phone = matchedManaged?.phone?.ifBlank { p.phone } ?: p.phone,
                email = matchedManaged?.email?.ifBlank { p.email } ?: p.email
            )

            // Clear persistent lockout counters and active owner hash
            secPrefs
                ?.edit()
                ?.putString("owner_pin_hash", storedHash)
                ?.putString("owner_pin_salt", salt)
                ?.putInt("login_failed_attempts", 0)
                ?.putLong("login_lockout_until", 0L)
                ?.putBoolean("is_signed_in", true)
                ?.apply()

            _uiState.update {
                it.copy(
                    profile = updatedProfile,
                    ownerPinHash = storedHash ?: it.ownerPinHash,
                    ownerPinSalt = salt ?: it.ownerPinSalt,
                    currentUserRole = "owner",
                    currentUserName = "$resolvedName (${p.entityType.signatoryTitle})",
                    authErrorMessage = null,
                    loginFailedAttempts = 0,
                    loginLockoutUntilTimestamp = 0L,
                    isGstUnlocked = matchedManaged?.isGstUnlocked ?: it.isGstUnlocked,
                    isBankReconUnlocked = matchedManaged?.isBankReconUnlocked ?: it.isBankReconUnlocked,
                    isInvoiceUnlocked = matchedManaged?.isInvoiceUnlocked ?: it.isInvoiceUnlocked,
                    isInventoryUnlocked = matchedManaged?.isInventoryUnlocked ?: it.isInventoryUnlocked,
                    isCmaUnlocked = matchedManaged?.isCmaUnlocked ?: it.isCmaUnlocked,
                    isStaffUnlocked = matchedManaged?.isStaffUnlocked ?: it.isStaffUnlocked,
                    isExportUnlocked = matchedManaged?.isExportUnlocked ?: it.isExportUnlocked,
                    isManagerOverrideUnlocked = matchedManaged?.isManagerOverrideUnlocked ?: it.isManagerOverrideUnlocked,
                    parties = if (isSameStore) it.parties else emptyList(),
                    products = if (isSameStore) it.products else emptyList(),
                    bankAccounts = if (isSameStore) it.bankAccounts else emptyList(),
                    bankReconItems = if (isSameStore) it.bankReconItems else emptyList(),
                    upiQrs = if (isSameStore) it.upiQrs else emptyList(),
                    stockBatches = if (isSameStore) it.stockBatches else emptyList(),
                    stockMovements = if (isSameStore) it.stockMovements else emptyList(),
                    stockAdjustments = if (isSameStore) it.stockAdjustments else emptyList(),
                    productVariants = if (isSameStore) it.productVariants else emptyList(),
                    prefilledGstr1Invoices = if (isSameStore) it.prefilledGstr1Invoices else emptyList(),
                    transactions = if (isSameStore) it.transactions else emptyList()
                )
            }

            launchDb { db ->
                db.businessProfileDao().insertOrUpdateProfile(BusinessProfileEntity.fromDomain(updatedProfile))
            }
            return true
        } else {
            val attempts = s.loginFailedAttempts + 1
            val lockout = if (attempts >= 5) now + 60_000L else 0L

            // Persist brute force lockout to disk so restarting the app cannot reset it
            secPrefs
                ?.edit()
                ?.putInt("login_failed_attempts", attempts)
                ?.putLong("login_lockout_until", lockout)
                ?.apply()

            _uiState.update {
                it.copy(
                    loginFailedAttempts = attempts,
                    loginLockoutUntilTimestamp = lockout,
                    authErrorMessage = if (lockout > 0) "Too many failed attempts. Locked for 60s." else "Invalid credentials. Please verify your Store Code / PIN."
                )
            }
            return false
        }
    }

    fun signOut() {
        securityPrefs()?.edit()
            ?.putBoolean("is_signed_in", false)
            ?.putBoolean("is_app_locked", false)
            ?.apply()

        val deauthProfile = _uiState.value.profile.copy(isRegistered = false)
        _uiState.update {
            it.copy(
                profile = deauthProfile,
                currentUserRole = "owner",
                isManagerUnlocked = false,
                isSuperAdminSession = false,
                isGstUnlocked = false,
                isBankReconUnlocked = false,
                isAppLocked = false,
                recoveryPhrase = null,
                prefilledGstr1Invoices = emptyList(),
                authErrorMessage = null
            )
        }
        if (database != null) {
            launchDb("Failed to update sign-out profile state") { db ->
                db.businessProfileDao().insertOrUpdateProfile(BusinessProfileEntity.fromDomain(deauthProfile))
            }
        }
    }

    private var isSystemDarkCached: Boolean = false

    fun toggleTheme(theme: AppColorTheme, context: Context? = null) {
        setThemeMode(theme.id, context)
    }

    fun setThemeMode(mode: String, context: Context? = null) {
        val resolvedTheme = if (mode == "auto") {
            if (isSystemDarkCached) AppColorTheme.MidnightTeal else AppColorTheme.ClinicBlue
        } else {
            AppColorTheme.fromId(mode)
        }
        _uiState.update {
            it.copy(
                themeMode = mode,
                currentTheme = resolvedTheme
            )
        }
        context?.getSharedPreferences("pabaru_prefs", Context.MODE_PRIVATE)
            ?.edit()
            ?.putString("pabaru-theme", mode)
            ?.apply()
    }

    fun updateSystemDarkTheme(isSystemDark: Boolean) {
        isSystemDarkCached = isSystemDark
        _uiState.update { state ->
            if (state.themeMode == "auto") {
                val autoTheme = if (isSystemDark) AppColorTheme.MidnightTeal else AppColorTheme.ClinicBlue
                if (state.currentTheme != autoTheme) state.copy(currentTheme = autoTheme) else state
            } else state
        }
    }

    fun initThemeFromPreferences(context: Context, isSystemDark: Boolean) {
        isSystemDarkCached = isSystemDark
        val saved = context.getSharedPreferences("pabaru_prefs", Context.MODE_PRIVATE)
            .getString("pabaru-theme", "auto") ?: "auto"
        val theme = if (saved == "auto") {
            if (isSystemDark) AppColorTheme.MidnightTeal else AppColorTheme.ClinicBlue
        } else {
            AppColorTheme.fromId(saved)
        }
        _uiState.update {
            it.copy(
                themeMode = saved,
                currentTheme = theme
            )
        }
    }

    fun updateProfile(newProfile: BusinessProfile) {
        _uiState.update { it.copy(profile = newProfile) }
        launchDb("Failed to update profile in database") { db ->
            db.businessProfileDao().insertOrUpdateProfile(BusinessProfileEntity.fromDomain(newProfile))
        }
        recordAudit("BusinessProfile", newProfile.id, "UPDATE_PROFILE", "Statutory profile updated", "Tax compliance", _uiState.value.currentUserName)
        verifyAuditChain()
    }

    // -------------------------------------------------------------
    // Invoicing & Ledger Services
    // -------------------------------------------------------------
    fun validateGstin(gstin: String?, allowBlank: Boolean = true): FinancialEngine.GstinValidationResult {
        return FinancialEngine.validateGstin(gstin, allowBlank)
    }

    fun recordItemizedInvoice(
        partyId: String?,
        paymentMode: PaymentMode,
        type: TransactionType, // Sale, CreditNote, DebitNote, Quotation
        lineItems: List<InvoiceLineItem>,
        remarks: String,
        customGstin: String? = null
    ): Transaction {
        // Enforce GSTIN validation using regex before saving invoice to ensure compliance (optional for Non-GST bills)
        if (type != TransactionType.NonGstBill) {
            val party = _uiState.value.parties.find { it.id == partyId }
            val gstinToValidate = if (!customGstin.isNullOrBlank()) customGstin else party?.gstin
            val validation = FinancialEngine.validateGstin(gstinToValidate, allowBlank = true)
            require(validation.isValid) {
                "GSTIN Compliance Error: ${validation.message}"
            }
        }

        val targetParty = _uiState.value.parties.find { it.id == partyId }
        val isComposition = _uiState.value.profile.registrationType == RegistrationType.composition
        val isNonGst = type == TransactionType.NonGstBill
        val safeLineItems = if (isComposition || isNonGst) {
            lineItems.map { it.copy(gstRateBps = 0, cgstPaise = 0L, sgstPaise = 0L, igstPaise = 0L, totalPaise = it.taxableAmountPaise) }
        } else {
            lineItems
        }
        val totalPaise = safeLineItems.sumOf { it.totalPaise }

        val qtyMapMilli = mutableMapOf<String, Long>()
        safeLineItems.forEach {
            qtyMapMilli[it.itemId] = (qtyMapMilli[it.itemId] ?: 0L) + it.quantityMilli
        }

        // 1. Strict Negative Stock Guard: Check BEFORE allocating invoice counter so failed validations never consume numbers
        if (type == TransactionType.Sale || type == TransactionType.NonGstBill) {
            for ((itemId, requestedQtyMilli) in qtyMapMilli) {
                val prod = _uiState.value.products.find { it.id == itemId }
                if (prod != null && prod.currentStockMilli < requestedQtyMilli) {
                    val availUnits = prod.currentStockMilli / 1000.0
                    val reqUnits = requestedQtyMilli / 1000.0
                    throw IllegalArgumentException(
                        "Insufficient stock for '${prod.name}'! Available: $availUnits ${prod.unit}, Requested: $reqUnits ${prod.unit}."
                    )
                }
            }
        }

        val fy = FinancialEngine.getFinancialYear()
        val prefix = when (type) {
            TransactionType.CreditNote -> "CN"
            TransactionType.DebitNote -> "DN"
            TransactionType.Quotation -> "QTN"
            TransactionType.NonGstBill -> "BILL"
            else -> if (_uiState.value.profile.registrationType == RegistrationType.composition) "BOS" else "INV"
        }

        // Allocate sequence atomically scoped to the active Financial Year (and clamped <= 16 chars per GST rule)
        val compactFy = if (fy.contains("-") && fy.length >= 7) "${fy.substring(2, 4)}-${fy.takeLast(2)}" else fy
        val seriesPrefix = "$prefix/$compactFy/"
        val nextSequence = synchronized(invoiceSeqLock) {
            val counterKey = prefix to fy
            val memMax = fyPrefixCounters[counterKey] ?: 0L
            val txnMax = _uiState.value.transactions
                .filter { it.invoiceNumber.startsWith(seriesPrefix) }
                .mapNotNull { it.invoiceNumber.substringAfterLast('/').toLongOrNull() }
                .maxOrNull() ?: 0L
            val next = maxOf(memMax, txnMax) + 1L
            fyPrefixCounters[counterKey] = next
            next
        }
        val candidateInvoice = "$prefix/$compactFy/${String.format(java.util.Locale.ROOT, "%04d", nextSequence)}"
        val invoiceNumber = if (candidateInvoice.length <= 16) candidateInvoice else "$prefix/$nextSequence".take(16)

        val newTxn = Transaction(
            id = "TXN-${System.currentTimeMillis()}-${java.util.UUID.randomUUID().toString().take(6)}",
            branchId = _uiState.value.activeBranchId,
            date = FinancialEngine.getTodayDate(),
            partyId = partyId,
            amountPaise = totalPaise,
            type = type,
            paymentMode = paymentMode,
            bankAccountId = if (paymentMode == PaymentMode.Bank || paymentMode == PaymentMode.UPI || paymentMode == PaymentMode.Cheque) _uiState.value.bankAccounts.firstOrNull()?.id else null,
            remarks = remarks.ifEmpty { "$prefix $invoiceNumber" },
            entryMode = "itemized",
            invoiceNumber = invoiceNumber,
            items = safeLineItems
        )

        // 2. Deduct inventory in milli-units
        val updatedProducts = _uiState.value.products.map { prod ->
            val totalSoldMilli = qtyMapMilli[prod.id]
            if (totalSoldMilli != null) {
                val stockDeltaMilli = when (type) {
                    TransactionType.Sale, TransactionType.NonGstBill -> -totalSoldMilli
                    TransactionType.CreditNote -> totalSoldMilli // Sales return: inventory restored
                    TransactionType.DebitNote -> if (targetParty?.isSupplier == true) -totalSoldMilli else 0L // Purchase return to vendor
                    TransactionType.Quotation -> 0L
                    else -> 0L
                }
                val newStockMilli = (prod.currentStockMilli + stockDeltaMilli).coerceAtLeast(0L)
                prod.copy(currentStockMilli = newStockMilli)
            } else {
                prod
            }
        }

        // 3. Update batches FIFO if batch tracking is active
        val updatedBatches = _uiState.value.stockBatches.map { it.copy() }.toMutableList()
        val modifiedBatchIds = mutableSetOf<String>()
        val newMovements = mutableListOf<StockMovement>()
        val todayStr = FinancialEngine.getTodayDate()

        if (type == TransactionType.Sale || type == TransactionType.NonGstBill) {
            for ((itemId, requestedQtyMilli) in qtyMapMilli) {
                val prod = _uiState.value.products.find { it.id == itemId }
                val balanceAfterMilli = updatedProducts.find { it.id == itemId }?.currentStockMilli ?: 0L
                var remainingToDeductMilli = requestedQtyMilli
                val prodBatches = updatedBatches
                    .filter { it.productId == itemId && it.availableQuantityMilli > 0L }
                    .sortedBy { it.expiryDate.ifBlank { "9999-12-31" } }
                var anyBatchDeducted = false
                for (batch in prodBatches) {
                    if (remainingToDeductMilli <= 0L) break
                    val deductMilli = minOf(batch.availableQuantityMilli, remainingToDeductMilli)
                    val idx = updatedBatches.indexOfFirst { it.id == batch.id }
                    if (idx >= 0) {
                        val newAvail = updatedBatches[idx].availableQuantityMilli - deductMilli
                        updatedBatches[idx] = updatedBatches[idx].copy(availableQuantityMilli = newAvail)
                        modifiedBatchIds.add(batch.id)
                    }
                    remainingToDeductMilli -= deductMilli
                    anyBatchDeducted = true
                    newMovements.add(
                        StockMovement(
                            id = "MOV-${System.currentTimeMillis()}-${UUID.randomUUID().toString().take(6)}",
                            productId = itemId,
                            productName = prod?.name ?: "Goods",
                            batchId = batch.id,
                            referenceTxnId = newTxn.id,
                            movementType = "SALE_OUT",
                            quantityDeltaMilli = -deductMilli,
                            balanceAfterMilli = balanceAfterMilli,
                            locationId = batch.locationId.ifBlank { "LOC-MAIN" },
                            timestamp = todayStr,
                            remarks = "$prefix $invoiceNumber (Batch ${batch.batchNumber})"
                        )
                    )
                }
                if (!anyBatchDeducted || remainingToDeductMilli > 0L) {
                    newMovements.add(
                        StockMovement(
                            id = "MOV-${System.currentTimeMillis()}-${UUID.randomUUID().toString().take(6)}",
                            productId = itemId,
                            productName = prod?.name ?: "Goods",
                            batchId = null,
                            referenceTxnId = newTxn.id,
                            movementType = "SALE_OUT",
                            quantityDeltaMilli = -remainingToDeductMilli,
                            balanceAfterMilli = balanceAfterMilli,
                            locationId = "LOC-MAIN",
                            timestamp = todayStr,
                            remarks = "$prefix $invoiceNumber"
                        )
                    )
                }
            }
        } else if (type == TransactionType.CreditNote) {
            for ((itemId, returnedQtyMilli) in qtyMapMilli) {
                val prod = _uiState.value.products.find { it.id == itemId }
                val balanceAfterMilli = updatedProducts.find { it.id == itemId }?.currentStockMilli ?: 0L
                newMovements.add(
                    StockMovement(
                        id = "MOV-${System.currentTimeMillis()}-${UUID.randomUUID().toString().take(6)}",
                        productId = itemId,
                        productName = prod?.name ?: "Goods",
                        batchId = null,
                        referenceTxnId = newTxn.id,
                        movementType = "RETURN_IN",
                        quantityDeltaMilli = returnedQtyMilli,
                        balanceAfterMilli = balanceAfterMilli,
                        locationId = "LOC-MAIN",
                        timestamp = todayStr,
                        remarks = "$prefix $invoiceNumber (Sales Return)"
                    )
                )
            }
        }

        _uiState.update {
            it.copy(
                transactions = listOf(newTxn) + it.transactions,
                products = updatedProducts,
                stockBatches = updatedBatches,
                stockMovements = newMovements + it.stockMovements
            )
        }

        val modifiedProducts = updatedProducts.filter { it.id in qtyMapMilli.keys }
        val modifiedBatches = updatedBatches.filter { it.id in modifiedBatchIds }

        if (database != null) {
            launchDb("Failed to record invoice") { db ->
                db.withTransaction {
                    db.transactionDao().insertTransaction(TransactionEntity.fromDomain(newTxn))
                    db.invoiceCounterDao().setCounter(InvoiceCounterEntity(prefix, fy, nextSequence))
                    modifiedProducts.forEach { p ->
                        db.productDao().updateStock(p.id, p.currentStock, p.currentStockMilli)
                    }
                    modifiedBatches.forEach { b ->
                        db.stockBatchDao().insertBatch(StockBatchEntity.fromDomain(b))
                    }
                    newMovements.forEach { m ->
                        db.stockMovementDao().insertMovement(StockMovementEntity.fromDomain(m))
                    }
                }
            }
        }

        val formattedTotal = FinancialEngine.formatPaise(totalPaise)
        recordAudit("Transaction", newTxn.id, type.name, "$prefix $invoiceNumber generated for $formattedTotal", remarks, _uiState.value.currentUserName)
        verifyAuditChain()
        return newTxn
    }

    fun recordPayment(
        partyId: String,
        amountPaise: Long,
        isPaymentIn: Boolean, // True for Customer collection, False for Supplier payment
        paymentMode: PaymentMode,
        remarks: String
    ) {
        if (amountPaise <= 0L || partyId.isBlank()) {
            return
        }
        val type = if (isPaymentIn) TransactionType.PaymentIn else TransactionType.PaymentOut
        val uniqueId = "TXN-PMT-${System.currentTimeMillis()}-${UUID.randomUUID().toString().take(6)}"
        val newTxn = Transaction(
            id = uniqueId,
            branchId = _uiState.value.activeBranchId,
            date = FinancialEngine.getTodayDate(),
            partyId = partyId,
            amountPaise = amountPaise,
            type = type,
            paymentMode = paymentMode,
            bankAccountId = if (paymentMode == PaymentMode.Bank || paymentMode == PaymentMode.UPI || paymentMode == PaymentMode.Cheque) _uiState.value.bankAccounts.firstOrNull()?.id else null,
            remarks = remarks.ifEmpty { if (isPaymentIn) "Customer Payment In" else "Supplier Payment Out" },
            entryMode = "quick"
        )

        _uiState.update {
            it.copy(transactions = listOf(newTxn) + it.transactions)
        }

        if (database != null) {
            launchDb("Failed to record payment transaction") { db ->
                db.transactionDao().insertTransaction(TransactionEntity.fromDomain(newTxn))
            }
        }

        val rupeesStr = String.format(Locale.ENGLISH, "%.2f", amountPaise / 100.0)
        recordAudit("Transaction", newTxn.id, type.name, "Recorded ${type.name} of ₹ $rupeesStr", remarks, _uiState.value.currentUserName)
        verifyAuditChain()
    }



    // -------------------------------------------------------------
    // Bank Reconciliation & Statement Import
    // -------------------------------------------------------------
    fun reconcileBankFeed() {
        val reconciled = BankReconEngine.matchBankStatement(
            _uiState.value.bankReconItems,
            _uiState.value.transactions,
            _uiState.value.parties
        )
        _uiState.update { it.copy(bankReconItems = reconciled) }
        if (database != null) {
            launchDb("Failed to update bank feed reconciliation") { db ->
                db.bankReconDao().insertAll(reconciled.map { BankReconEntity.fromDomain(it) })
            }
        }
        val matchedCount = reconciled.count { it.isReconciled }
        recordAudit("Banking", "RECON-BATCH", "RECONCILE", "Automated bank reconciliation executed ($matchedCount matched)", "Bank feed matching", _uiState.value.currentUserName)
        verifyAuditChain()
    }

    fun importStatementText(
        rawContent: String,
        bankAccountId: String,
        bankName: String = "Bank Account"
    ): BankReconEngine.StatementParseResult {
        val parseResult = BankReconEngine.parseStatementText(rawContent, bankAccountId, bankName)
        if (parseResult.items.isNotEmpty()) {
            val matched = BankReconEngine.matchBankStatement(
                parseResult.items,
                _uiState.value.transactions,
                _uiState.value.parties
            )
            val existingOtherBanks = _uiState.value.bankReconItems.filter { it.bankAccountId != bankAccountId }
            val newItems = existingOtherBanks + matched
            _uiState.update { it.copy(bankReconItems = newItems) }
            if (database != null) {
                launchDb("Failed to save imported statement") { db ->
                    db.withTransaction {
                        db.bankReconDao().clearBankItems(bankAccountId)
                        db.bankReconDao().insertAll(matched.map { BankReconEntity.fromDomain(it) })
                    }
                }
            }
            recordAudit("Banking", "STMT-IMPORT", "IMPORT", "Imported ${matched.size} statement lines (${parseResult.format}) for $bankName", "Bank Statement Import", _uiState.value.currentUserName)
            verifyAuditChain()
        }
        return parseResult
    }

    fun importSampleStatement(format: String, bankAccountId: String) {
        val bank = _uiState.value.bankAccounts.find { it.id == bankAccountId } ?: _uiState.value.bankAccounts.firstOrNull()
        val bankName = bank?.bankName ?: "HDFC Bank"
        val raw = if (format.equals("OFX", ignoreCase = true)) {
            BankReconEngine.getSampleSbiStatementOfx()
        } else {
            BankReconEngine.getSampleHdfcStatementCsv()
        }
        importStatementText(raw, bankAccountId, bankName)
    }

    fun importSimulatedStatement() {
        val defaultBankId = _uiState.value.bankAccounts.firstOrNull()?.id ?: "BNK-01"
        importSampleStatement("CSV", defaultBankId)
    }

    fun confirmMatch(itemId: String) {
        val updated = _uiState.value.bankReconItems.map {
            if (it.id == itemId) {
                it.copy(isUserConfirmed = true, isReconciled = true, reconciledAt = FinancialEngine.getTodayDate())
            } else it
        }
        _uiState.update { it.copy(bankReconItems = updated) }
        val target = updated.find { it.id == itemId }
        if (target != null) {
            launchDb("Failed to update reconciliation item") { db ->
                db.bankReconDao().insertReconItem(BankReconEntity.fromDomain(target))
            }
        }
        recordAudit("Banking", itemId, "CONFIRM_MATCH", "Confirmed reconciliation match for entry $itemId", "Manual Review", _uiState.value.currentUserName)
        verifyAuditChain()
    }

    fun confirmAllMatches(minConfidence: Int = 80) {
        val updated = _uiState.value.bankReconItems.map {
            if (it.matchedTxnId != null && it.matchConfidence >= minConfidence && !it.isUserConfirmed) {
                it.copy(isUserConfirmed = true, isReconciled = true, reconciledAt = FinancialEngine.getTodayDate())
            } else it
        }
        val count = updated.count { it.isUserConfirmed && it.isReconciled }
        _uiState.update { it.copy(bankReconItems = updated) }
        launchDb("Failed to update reconciliation items") { db ->
            db.bankReconDao().insertAll(updated.filter { it.isUserConfirmed && it.isReconciled }.map { BankReconEntity.fromDomain(it) })
        }
        recordAudit("Banking", "BATCH-CONFIRM", "CONFIRM_ALL", "Batch confirmed all auto-matches ($count entries)", "Manual Review", _uiState.value.currentUserName)
        verifyAuditChain()
    }

    fun manualMatch(stmtItemId: String, txnId: String) {
        val txn = _uiState.value.transactions.find { it.id == txnId }
        val updated = _uiState.value.bankReconItems.map {
            if (it.id == stmtItemId) {
                it.copy(
                    matchedTxnId = txnId,
                    isReconciled = true,
                    isUserConfirmed = true,
                    matchConfidence = 100,
                    matchReason = "Manually linked to ${txn?.invoiceNumber?.ifBlank { txnId } ?: txnId}",
                    discrepancyNote = "",
                    reconciledAt = FinancialEngine.getTodayDate()
                )
            } else it
        }
        _uiState.update { it.copy(bankReconItems = updated) }
        val target = updated.find { it.id == stmtItemId }
        if (target != null) {
            launchDb("Failed to link statement item") { db ->
                db.bankReconDao().insertReconItem(BankReconEntity.fromDomain(target))
            }
        }
        recordAudit("Banking", stmtItemId, "MANUAL_MATCH", "Manually matched statement $stmtItemId to transaction $txnId", "Discrepancy Resolution", _uiState.value.currentUserName)
        verifyAuditChain()
    }

    fun unlinkMatch(stmtItemId: String) {
        val updated = _uiState.value.bankReconItems.map {
            if (it.id == stmtItemId) {
                it.copy(
                    matchedTxnId = null,
                    isReconciled = false,
                    isUserConfirmed = false,
                    matchConfidence = 0,
                    matchReason = "",
                    discrepancyNote = "Unlinked by user; awaiting rematch or resolution",
                    reconciledAt = null
                )
            } else it
        }
        _uiState.update { it.copy(bankReconItems = updated) }
        val target = updated.find { it.id == stmtItemId }
        if (target != null) {
            launchDb("Failed to unlink statement item") { db ->
                db.bankReconDao().insertReconItem(BankReconEntity.fromDomain(target))
            }
        }
        recordAudit("Banking", stmtItemId, "UNLINK", "Unlinked match for entry $stmtItemId", "Reconciliation Correction", _uiState.value.currentUserName)
        verifyAuditChain()
    }

    fun resolveDiscrepancy(
        stmtItemId: String,
        resolutionType: BankReconEngine.DiscrepancyResolutionType,
        partyId: String? = null,
        customRemarks: String = ""
    ) {
        val stmt = _uiState.value.bankReconItems.find { it.id == stmtItemId } ?: return
        val newTxn = BankReconEngine.createBalancingTransaction(
            stmt = stmt,
            resolutionType = resolutionType,
            branchId = _uiState.value.activeBranchId,
            partyId = partyId,
            customRemarks = customRemarks
        )

        val updatedTxns = _uiState.value.transactions + newTxn
        val updatedStmt = _uiState.value.bankReconItems.map {
            if (it.id == stmtItemId) {
                it.copy(
                    matchedTxnId = newTxn.id,
                    isReconciled = true,
                    isUserConfirmed = true,
                    matchConfidence = 100,
                    matchReason = "Discrepancy resolved: Created balancing transaction ${newTxn.invoiceNumber.ifBlank { newTxn.id }}",
                    discrepancyNote = "",
                    reconciledAt = FinancialEngine.getTodayDate()
                )
            } else it
        }

        _uiState.update { it.copy(transactions = updatedTxns, bankReconItems = updatedStmt) }
        val target = updatedStmt.find { it.id == stmtItemId }
        launchDb("Failed to resolve discrepancy") { db ->
            db.withTransaction {
                db.transactionDao().insertTransaction(TransactionEntity.fromDomain(newTxn))
                if (target != null) {
                    db.bankReconDao().insertReconItem(BankReconEntity.fromDomain(target))
                }
            }
        }
        recordAudit("Banking", stmtItemId, "RESOLVE_DISCREPANCY", "Created balancing transaction ${newTxn.id} for $resolutionType", "Discrepancy Resolution", _uiState.value.currentUserName)
        verifyAuditChain()
    }

    fun clearStatementItems(bankAccountId: String) {
        val remaining = _uiState.value.bankReconItems.filter { it.bankAccountId != bankAccountId }
        _uiState.update { it.copy(bankReconItems = remaining) }
        launchDb("Failed to clear statement items") { db ->
            db.bankReconDao().clearBankItems(bankAccountId)
        }
        recordAudit("Banking", bankAccountId, "CLEAR_STMT", "Cleared statement items for bank $bankAccountId", "Statement Reset", _uiState.value.currentUserName)
        verifyAuditChain()
    }

    // -------------------------------------------------------------
    // General Ledger & Stock Helpers
    // -------------------------------------------------------------
    fun updateLowStockThreshold(newThreshold: Int) {
        val safe = newThreshold.coerceAtLeast(1)
        appPrefs()?.edit()?.putInt("low_stock_threshold", safe)?.apply()
        _uiState.update { it.copy(lowStockThreshold = safe) }
    }

    fun updateProductStock(productId: String, newStock: Int) {
        val safeStock = newStock.coerceAtLeast(0)
        val safeMilli = safeStock.toLong() * 1000L
        _uiState.update {
            it.copy(
                products = it.products.map { p ->
                    if (p.id == productId) p.copy(currentStockMilli = safeMilli) else p
                }
            )
        }
        launchDb("Failed to update stock") { db ->
            db.productDao().updateStock(productId, safeStock, safeMilli)
        }
    }

    fun addParty(party: Party) {
        _uiState.update { it.copy(parties = it.parties + party) }
        launchDb("Failed to add party") { db ->
            db.partyDao().insertParty(PartyEntity.fromDomain(party))
        }
        recordAudit("Party", party.id, "CREATE", "Added party ${party.name}", "New party onboarding", _uiState.value.currentUserName)
        verifyAuditChain()
    }

    fun updateParty(party: Party) {
        val updated = _uiState.value.parties.map { if (it.id == party.id) party else it }
        _uiState.update { it.copy(parties = updated) }
        launchDb("Failed to update party") { db ->
            db.partyDao().insertParty(PartyEntity.fromDomain(party))
        }
        recordAudit("Party", party.id, "UPDATE", "Updated party ${party.name}", "Party details update", _uiState.value.currentUserName)
        verifyAuditChain()
    }

    fun deleteParty(partyId: String) {
        val hasLinkedTransactions = _uiState.value.transactions.any { it.partyId == partyId && !it.isVoided }
        if (hasLinkedTransactions) {
            _uiState.update { it.copy(authErrorMessage = "Cannot delete party with existing financial transactions.") }
            return
        }
        val party = _uiState.value.parties.find { it.id == partyId }
        val updated = _uiState.value.parties.filter { it.id != partyId }
        _uiState.update { it.copy(parties = updated) }
        launchDb("Failed to delete party") { db ->
            db.partyDao().deleteParty(partyId)
        }
        recordAudit("Party", partyId, "DELETE", "Deleted party ${party?.name ?: partyId}", "Party purge", _uiState.value.currentUserName)
        verifyAuditChain()
    }

    fun addProduct(product: ProductItem) {
        _uiState.update { it.copy(products = it.products + product) }
        launchDb("Failed to add product") { db ->
            db.productDao().insertProduct(ProductEntity.fromDomain(product))
        }
        recordAudit(
            "ProductItem",
            product.id,
            "CREATE",
            "Added product ${product.name} (Qty: ${FinancialEngine.formatQuantityMilli(product.currentStockMilli)} ${product.unit})",
            "Inventory addition",
            _uiState.value.currentUserName
        )
        verifyAuditChain()
    }

    fun updateProduct(product: ProductItem) {
        val existing = _uiState.value.products.find { it.id == product.id }
        val adjustedProduct = if (existing != null &&
            product.openingStockMilli != existing.openingStockMilli &&
            product.currentStockMilli == existing.currentStockMilli
        ) {
            val deltaMilli = product.openingStockMilli - existing.openingStockMilli
            product.copy(currentStockMilli = (existing.currentStockMilli + deltaMilli).coerceAtLeast(0L))
        } else {
            product
        }
        val updated = _uiState.value.products.map { if (it.id == adjustedProduct.id) adjustedProduct else it }
        _uiState.update { it.copy(products = updated) }
        launchDb("Failed to update product") { db ->
            db.productDao().insertProduct(ProductEntity.fromDomain(adjustedProduct))
        }
        recordAudit("ProductItem", adjustedProduct.id, "UPDATE", "Updated product ${adjustedProduct.name}", "Product details update", _uiState.value.currentUserName)
        verifyAuditChain()
    }

    fun deleteProduct(productId: String) {
        val prod = _uiState.value.products.find { it.id == productId }
        val updated = _uiState.value.products.filter { it.id != productId }
        _uiState.update { it.copy(products = updated) }
        launchDb("Failed to delete product") { db ->
            db.productDao().deleteProduct(productId)
        }
        recordAudit("ProductItem", productId, "DELETE", "Deleted product ${prod?.name ?: productId}", "Inventory purge", _uiState.value.currentUserName)
        verifyAuditChain()
    }

    fun toggleProductArchive(productId: String, isArchived: Boolean) {
        val updated = _uiState.value.products.map {
            if (it.id == productId) it.copy(isArchived = isArchived) else it
        }
        _uiState.update { it.copy(products = updated) }
        launchDb("Failed to toggle product archive") { db ->
            db.productDao().setArchived(productId, isArchived)
        }
        val action = if (isArchived) "ARCHIVE" else "UNARCHIVE"
        recordAudit("ProductItem", productId, action, "Product $productId set to archived = $isArchived", "Inventory archiving", _uiState.value.currentUserName)
        verifyAuditChain()
    }

    fun recordStockAdjustment(
        productId: String,
        locationId: String,
        quantityDelta: Int,
        reason: StockAdjustmentReason,
        remarks: String
    ) {
        recordStockAdjustmentMilli(
            productId = productId,
            locationId = locationId,
            quantityDeltaMilli = quantityDelta.toLong() * 1000L,
            reason = reason,
            remarks = remarks
        )
    }

    fun recordStockAdjustmentMilli(
        productId: String,
        locationId: String,
        quantityDeltaMilli: Long,
        reason: StockAdjustmentReason,
        remarks: String
    ) {
        val prod = _uiState.value.products.find { it.id == productId } ?: return
        val newStockMilli = (prod.currentStockMilli + quantityDeltaMilli).coerceAtLeast(0L)
        val newStock = (newStockMilli / 1000L).toInt()
        val updatedProducts = _uiState.value.products.map {
            if (it.id == productId) it.copy(currentStockMilli = newStockMilli) else it
        }

        val todayStr = FinancialEngine.getTodayDate()
        val adjustment = StockAdjustment(
            id = "ADJ-${System.currentTimeMillis()}-${UUID.randomUUID().toString().take(6)}",
            productId = productId,
            productName = prod.name,
            locationId = locationId,
            quantityDeltaMilli = quantityDeltaMilli,
            reason = reason,
            remarks = remarks,
            timestamp = todayStr,
            adjustedBy = _uiState.value.currentUserName
        )

        val movement = StockMovement(
            id = "MOV-${System.currentTimeMillis()}-${UUID.randomUUID().toString().take(6)}",
            productId = productId,
            productName = prod.name,
            batchId = null,
            referenceTxnId = adjustment.id,
            movementType = "ADJUSTMENT",
            quantityDeltaMilli = quantityDeltaMilli,
            balanceAfterMilli = newStockMilli,
            locationId = locationId,
            timestamp = todayStr,
            remarks = "Adjustment: ${reason.displayName} (${remarks.ifEmpty { "Manual count correction" }})"
        )

        _uiState.update {
            it.copy(
                products = updatedProducts,
                stockAdjustments = listOf(adjustment) + it.stockAdjustments,
                stockMovements = listOf(movement) + it.stockMovements
            )
        }

        if (database != null) {
            launchDb("Failed to record stock adjustment") { db ->
                db.withTransaction {
                    db.productDao().updateStock(productId, newStock, newStockMilli)
                    db.stockAdjustmentDao().insertAdjustment(StockAdjustmentEntity.fromDomain(adjustment))
                    db.stockMovementDao().insertMovement(StockMovementEntity.fromDomain(movement))
                }
            }
        }

        val qtyUnits = quantityDeltaMilli / 1000.0
        val sign = if (quantityDeltaMilli >= 0) "+$qtyUnits" else "$qtyUnits"
        recordAudit("StockAdjustment", adjustment.id, "ADJUST", "$sign ${prod.unit} for ${prod.name} due to ${reason.displayName}", remarks, _uiState.value.currentUserName)
        verifyAuditChain()
    }

    fun recordPurchaseBill(
        supplierPartyId: String,
        billNumber: String,
        billDate: String,
        paymentMode: PaymentMode,
        purchasedItems: List<InvoiceLineItem>,
        batchDetails: List<StockBatch> = emptyList(),
        remarks: String = ""
    ) {
        val totalPaise = purchasedItems.sumOf { it.totalPaise }
        val txn = Transaction(
            id = "TXN-PUR-${System.currentTimeMillis()}-${UUID.randomUUID().toString().take(6)}",
            branchId = _uiState.value.activeBranchId,
            date = billDate.ifEmpty { FinancialEngine.getTodayDate() },
            partyId = supplierPartyId,
            amountPaise = totalPaise,
            type = TransactionType.Purchase,
            paymentMode = paymentMode,
            bankAccountId = if (paymentMode == PaymentMode.Bank || paymentMode == PaymentMode.UPI || paymentMode == PaymentMode.Cheque) _uiState.value.bankAccounts.firstOrNull()?.id else null,
            remarks = remarks.ifEmpty { "Purchase Bill $billNumber" },
            entryMode = "itemized",
            invoiceNumber = billNumber,
            items = purchasedItems
        )

        val qtyMapMilli = mutableMapOf<String, Long>()
        purchasedItems.forEach {
            qtyMapMilli[it.itemId] = (qtyMapMilli[it.itemId] ?: 0L) + it.quantityMilli
        }

        val updatedProducts = _uiState.value.products.map { prod ->
            val addQtyMilli = qtyMapMilli[prod.id]
            if (addQtyMilli != null) {
                prod.copy(currentStockMilli = prod.currentStockMilli + addQtyMilli)
            } else prod
        }

        val todayStr = FinancialEngine.getTodayDate()
        val newMovements = purchasedItems.map { itm ->
            val prod = updatedProducts.find { it.id == itm.itemId }
            val matchedBatchId = batchDetails.firstOrNull { it.productId == itm.itemId }?.id
            StockMovement(
                id = "MOV-${System.currentTimeMillis()}-${UUID.randomUUID().toString().take(6)}",
                productId = itm.itemId,
                productName = itm.name,
                batchId = matchedBatchId,
                referenceTxnId = txn.id,
                movementType = "PURCHASE_IN",
                quantityDeltaMilli = itm.quantityMilli,
                balanceAfterMilli = prod?.currentStockMilli ?: itm.quantityMilli,
                timestamp = todayStr,
                remarks = "Purchase Bill $billNumber"
            )
        }

        _uiState.update {
            it.copy(
                transactions = listOf(txn) + it.transactions,
                products = updatedProducts,
                stockBatches = batchDetails + it.stockBatches,
                stockMovements = newMovements + it.stockMovements
            )
        }

        val modifiedProducts = updatedProducts.filter { it.id in qtyMapMilli.keys }
        if (database != null) {
            launchDb("Failed to record purchase bill") { db ->
                db.withTransaction {
                    db.transactionDao().insertTransaction(TransactionEntity.fromDomain(txn))
                    modifiedProducts.forEach { p ->
                        db.productDao().updateStock(p.id, p.currentStock, p.currentStockMilli)
                    }
                    batchDetails.forEach { b ->
                        db.stockBatchDao().insertBatch(StockBatchEntity.fromDomain(b))
                    }
                    newMovements.forEach { m ->
                        db.stockMovementDao().insertMovement(StockMovementEntity.fromDomain(m))
                    }
                }
            }
        }

        recordAudit("Purchase", txn.id, "PURCHASE_ENTRY", "Inward stock for Bill $billNumber total ${FinancialEngine.formatPaise(totalPaise)}", remarks, _uiState.value.currentUserName)
        verifyAuditChain()
    }

    fun transferStockLocation(
        productId: String,
        fromLocationId: String,
        toLocationId: String,
        quantity: Int,
        remarks: String
    ) {
        transferStockLocationMilli(productId, fromLocationId, toLocationId, quantity.toLong() * 1000L, remarks)
    }

    fun transferStockLocationMilli(
        productId: String,
        fromLocationId: String,
        toLocationId: String,
        quantityMilli: Long,
        remarks: String
    ) {
        if (fromLocationId == toLocationId || quantityMilli <= 0L) return
        val prod = _uiState.value.products.find { it.id == productId } ?: return
        val todayStr = FinancialEngine.getTodayDate()
        val fromLoc = _uiState.value.inventoryLocations.find { it.id == fromLocationId }?.name ?: fromLocationId
        val toLoc = _uiState.value.inventoryLocations.find { it.id == toLocationId }?.name ?: toLocationId

        val movementOut = StockMovement(
            id = "MOV-${System.currentTimeMillis()}-${UUID.randomUUID().toString().take(6)}-OUT",
            productId = productId,
            productName = prod.name,
            batchId = null,
            referenceTxnId = null,
            movementType = "TRANSFER_OUT",
            quantityDeltaMilli = -quantityMilli,
            balanceAfterMilli = prod.currentStockMilli,
            locationId = fromLocationId,
            timestamp = todayStr,
            remarks = "Transfer to $toLoc: $remarks"
        )

        val movementIn = StockMovement(
            id = "MOV-${System.currentTimeMillis()}-${UUID.randomUUID().toString().take(6)}-IN",
            productId = productId,
            productName = prod.name,
            batchId = null,
            referenceTxnId = null,
            movementType = "TRANSFER_IN",
            quantityDeltaMilli = quantityMilli,
            balanceAfterMilli = prod.currentStockMilli,
            locationId = toLocationId,
            timestamp = todayStr,
            remarks = "Transfer from $fromLoc: $remarks"
        )

        _uiState.update {
            it.copy(
                stockMovements = listOf(movementOut, movementIn) + it.stockMovements
            )
        }

        if (database != null) {
            launchDb("Failed to record stock transfer") { db ->
                db.stockMovementDao().insertAll(listOf(
                    StockMovementEntity.fromDomain(movementOut),
                    StockMovementEntity.fromDomain(movementIn)
                ))
            }
        }

        recordAudit("StockTransfer", productId, "TRANSFER", "Transferred ${quantityMilli / 1000.0} ${prod.unit} of ${prod.name} from $fromLoc to $toLoc", remarks, _uiState.value.currentUserName)
        verifyAuditChain()
    }

    fun voidTransaction(id: String, reason: String) {
        val txn = _uiState.value.transactions.find { it.id == id } ?: return
        if (txn.isVoided) {
            return // Prevent duplicate voiding
        }
        val voidDate = FinancialEngine.getTodayDate()
        val updatedTxns = _uiState.value.transactions.map {
            if (it.id == id) it.copy(isVoided = true, voidReason = reason, voidedAt = voidDate) else it
        }

        // Aggregate milli-unit quantities
        val qtyMapMilli = mutableMapOf<String, Long>()
        txn.items.forEach {
            qtyMapMilli[it.itemId] = (qtyMapMilli[it.itemId] ?: 0L) + it.quantityMilli
        }
        val targetParty = _uiState.value.parties.find { it.id == txn.partyId }
        val isSupplierDebitNote = txn.type == TransactionType.DebitNote && targetParty?.isSupplier == true
        val updatedProducts = _uiState.value.products.map { prod ->
            val totalSoldMilli = qtyMapMilli[prod.id]
            if (totalSoldMilli != null) {
                val diffMilli = when (txn.type) {
                    TransactionType.Sale, TransactionType.NonGstBill -> totalSoldMilli // Restore stock
                    TransactionType.CreditNote -> -totalSoldMilli // Re-deduct returned stock
                    TransactionType.DebitNote -> if (isSupplierDebitNote) totalSoldMilli else 0L
                    TransactionType.Purchase -> -totalSoldMilli // Remove purchased stock
                    TransactionType.Quotation -> 0L
                    else -> 0L
                }
                val newStockMilli = (prod.currentStockMilli + diffMilli).coerceAtLeast(0L)
                prod.copy(currentStockMilli = newStockMilli)
            } else prod
        }

        val updatedBatches = _uiState.value.stockBatches.map { it.copy() }.toMutableList()
        val modifiedBatchIds = mutableSetOf<String>()
        val reversalMovements = mutableListOf<StockMovement>()
        for ((itemId, qtyMilli) in qtyMapMilli) {
            val prod = _uiState.value.products.find { it.id == itemId }
            val balanceAfterMilli = updatedProducts.find { it.id == itemId }?.currentStockMilli ?: 0L
            val movementDeltaMilli = when (txn.type) {
                TransactionType.Sale, TransactionType.NonGstBill -> qtyMilli
                TransactionType.CreditNote -> -qtyMilli
                TransactionType.Purchase -> -qtyMilli
                TransactionType.DebitNote -> if (isSupplierDebitNote) qtyMilli else 0L
                else -> 0L
            }
            if (movementDeltaMilli != 0L) {
                // Restore depleted batches for Sale/NonGstBill, or deduct purchased batches for Purchase
                if (txn.type == TransactionType.Sale || txn.type == TransactionType.NonGstBill) {
                    var toRestoreMilli = qtyMilli
                    val batches = updatedBatches.filter { it.productId == itemId }.sortedByDescending { it.expiryDate.ifBlank { "9999-12-31" } }
                    for (b in batches) {
                        if (toRestoreMilli <= 0L) break
                        val capMilli = b.initialQuantityMilli - b.availableQuantityMilli
                        if (capMilli > 0L) {
                            val addMilli = minOf(capMilli, toRestoreMilli)
                            val idx = updatedBatches.indexOfFirst { it.id == b.id }
                            if (idx >= 0) {
                                updatedBatches[idx] = updatedBatches[idx].copy(availableQuantityMilli = updatedBatches[idx].availableQuantityMilli + addMilli)
                                modifiedBatchIds.add(b.id)
                            }
                            toRestoreMilli -= addMilli
                        }
                    }
                } else if (txn.type == TransactionType.Purchase) {
                    var toDeductMilli = qtyMilli
                    val batches = updatedBatches.filter { it.productId == itemId && it.availableQuantityMilli > 0L }.sortedByDescending { it.expiryDate.ifBlank { "9999-12-31" } }
                    for (b in batches) {
                        if (toDeductMilli <= 0L) break
                        val subMilli = minOf(b.availableQuantityMilli, toDeductMilli)
                        val idx = updatedBatches.indexOfFirst { it.id == b.id }
                        if (idx >= 0) {
                            updatedBatches[idx] = updatedBatches[idx].copy(availableQuantityMilli = updatedBatches[idx].availableQuantityMilli - subMilli)
                            modifiedBatchIds.add(b.id)
                        }
                        toDeductMilli -= subMilli
                    }
                }

                reversalMovements.add(
                    StockMovement(
                        id = "MOV-${System.currentTimeMillis()}-${UUID.randomUUID().toString().take(6)}",
                        productId = itemId,
                        productName = prod?.name ?: "Goods",
                        batchId = null,
                        referenceTxnId = id,
                        movementType = "VOID_REVERSAL",
                        quantityDeltaMilli = movementDeltaMilli,
                        balanceAfterMilli = balanceAfterMilli,
                        locationId = "LOC-MAIN",
                        timestamp = voidDate,
                        remarks = "Void of ${txn.invoiceNumber.ifEmpty { id }}: $reason"
                    )
                )
            }
        }

        _uiState.update {
            it.copy(
                transactions = updatedTxns,
                products = updatedProducts,
                stockBatches = updatedBatches,
                stockMovements = reversalMovements + it.stockMovements
            )
        }

        val modifiedProducts = updatedProducts.filter { it.id in qtyMapMilli.keys }
        val modifiedBatches = updatedBatches.filter { it.id in modifiedBatchIds }
        if (database != null) {
            launchDb("Failed to void transaction") { db ->
                db.withTransaction {
                    db.transactionDao().voidTransaction(id, reason, voidDate)
                    modifiedProducts.forEach { p ->
                        db.productDao().updateStock(p.id, p.currentStock, p.currentStockMilli)
                    }
                    modifiedBatches.forEach { b ->
                        db.stockBatchDao().insertBatch(StockBatchEntity.fromDomain(b))
                    }
                    reversalMovements.forEach { m ->
                        db.stockMovementDao().insertMovement(StockMovementEntity.fromDomain(m))
                    }
                }
            }
        }

        recordAudit("Transaction", id, "VOID", "Voided transaction ${txn.invoiceNumber.ifEmpty { id }}", reason, _uiState.value.currentUserName)
        verifyAuditChain()
    }

    private fun recordAudit(
        entityType: String,
        entityId: String,
        action: String,
        details: String,
        reason: String,
        actor: String
    ) {
        val newRecord: AuditLog = synchronized(auditLock) {
            val currentLogs = _uiState.value.auditLogs
            val prevHash = if (currentLogs.isNotEmpty()) currentLogs.last().recordHash else CryptoEngine.GENESIS_HASH
            val created = CryptoEngine.createAuditRecord(prevHash, entityType, entityId, action, details, reason, actor)
            _uiState.update { state ->
                state.copy(auditLogs = state.auditLogs + created)
            }
            created
        }

        viewModelScope.launch(auditIoDispatcher) {
            try {
                database?.auditLogDao()?.insertAuditLog(AuditLogEntity.fromDomain(newRecord))
            } catch (_: Exception) {}
        }
    }

    fun verifyAuditChain() {
        _uiState.update { state ->
            state.copy(auditResult = CryptoEngine.verifyAuditChain(state.auditLogs))
        }
    }

    fun setSecurityPin(pin: String) {
        val clean = pin.trim()
        require(clean.length in 4..6 && clean.all { it.isDigit() }) {
            "Security PIN must be 4 to 6 numeric digits"
        }
        val phrase = CryptoEngine.generateRecoveryPhrase()
        val salt = CryptoEngine.generateSecureSalt()
        val hashedPin = CryptoEngine.hashPinString(clean, salt)
        val storeKey = _uiState.value.profile.storeCode.trim().uppercase(Locale.ROOT)
        if (storeKey.isNotEmpty()) {
            tenantCredentials[storeKey] = hashedPin to salt
        }
        securityPrefs()?.edit()?.apply {
            putString("owner_pin_hash", hashedPin)
            putString("owner_pin_salt", salt)
            if (storeKey.isNotEmpty()) {
                putString("tenant_pin_hash_$storeKey", hashedPin)
                putString("tenant_pin_salt_$storeKey", salt)
            }
            putBoolean("is_app_locked", false)
            apply()
        }
        _uiState.update {
            it.copy(
                securityPin = null,
                ownerPinHash = hashedPin,
                ownerPinSalt = salt,
                recoveryPhrase = phrase,
                isAppLocked = false
            )
        }
        recordAudit("Security", "PIN-SETUP", "ENABLE_PIN", "PIN security protection enabled", "Counter protection", _uiState.value.currentUserName)
        verifyAuditChain()
    }

    fun unlockApp(pin: String): Boolean {
        val s = _uiState.value
        val now = System.currentTimeMillis()
        if (s.loginLockoutUntilTimestamp > now) {
            val waitSec = (s.loginLockoutUntilTimestamp - now) / 1000
            _uiState.update { it.copy(authErrorMessage = "Too many failed attempts. Try again in ${waitSec}s.") }
            return false
        }
        val hash = s.ownerPinHash
        val salt = s.ownerPinSalt
        if (!hash.isNullOrEmpty()) {
            val computed = CryptoEngine.hashPinString(pin.trim(), salt)
            val match = java.security.MessageDigest.isEqual(
                computed.toByteArray(java.nio.charset.StandardCharsets.UTF_8),
                hash.toByteArray(java.nio.charset.StandardCharsets.UTF_8)
            )
            if (match) {
                securityPrefs()?.edit()
                    ?.putBoolean("is_app_locked", false)
                    ?.putInt("login_failed_attempts", 0)
                    ?.putLong("login_lockout_until", 0L)
                    ?.apply()
                _uiState.update {
                    it.copy(
                        isAppLocked = false,
                        loginFailedAttempts = 0,
                        loginLockoutUntilTimestamp = 0L,
                        authErrorMessage = null
                    )
                }
                return true
            }
        }
        val attempts = s.loginFailedAttempts + 1
        val lockout = if (attempts >= 5) now + 60_000L else 0L
        securityPrefs()?.edit()
            ?.putInt("login_failed_attempts", attempts)
            ?.putLong("login_lockout_until", lockout)
            ?.apply()
        _uiState.update {
            it.copy(
                loginFailedAttempts = attempts,
                loginLockoutUntilTimestamp = lockout,
                authErrorMessage = if (lockout > 0L) "Too many failed attempts. Locked for 60s." else "Incorrect PIN."
            )
        }
        return false
    }

    fun lockApp() {
        if (!(_uiState.value.ownerPinHash.isNullOrEmpty())) {
            securityPrefs()?.edit()?.putBoolean("is_app_locked", true)?.apply()
            _uiState.update { it.copy(isAppLocked = true) }
        }
    }

    fun switchUserRole(role: String = "owner", name: String = "") {
        val p = _uiState.value.profile
        _uiState.update {
            it.copy(
                currentUserRole = "owner",
                currentUserName = "${p.ownerName} (${p.entityType.signatoryTitle})"
            )
        }
    }

    fun switchToAdmin() {
        val p = _uiState.value.profile
        _uiState.update {
            it.copy(
                currentUserRole = "owner",
                currentUserName = "${p.ownerName} (${p.entityType.signatoryTitle})"
            )
        }
    }

    // -------------------------------------------------------------
    // Manager Security & QR Operations
    // -------------------------------------------------------------

    fun setManagerPin(pin: String, enableFingerprint: Boolean = false) {
        val clean = pin.trim()
        require(clean.length in 4..6 && clean.all { it.isDigit() }) {
            "Manager PIN must be 4 to 6 numeric digits"
        }
        val salt = CryptoEngine.generateSecureSalt()
        val hash = CryptoEngine.hashPinString(clean, salt)
        securityPrefs()?.edit()
            ?.putString("manager_pin_hash", hash)
            ?.putString("manager_pin_salt", salt)
            ?.putBoolean("use_fingerprint_for_manager", enableFingerprint)
            ?.putInt("manager_failed_attempts", 0)
            ?.putLong("manager_lockout_until", 0L)
            ?.apply()
        val currentLogs = _uiState.value.qrActivityLogs
        val prevHash = if (currentLogs.isNotEmpty()) currentLogs.first().hash else "00000000"
        val newHash = CryptoEngine.sha256("$prevHash|Manager PIN set|$salt")
        val newLog = QrActivityLog(
            id = "QRL-${System.currentTimeMillis()}-${UUID.randomUUID().toString().take(4)}",
            action = "Manager PIN set",
            details = "Manager PIN created with salted PBKDF2 hash",
            timestamp = FinancialEngine.getTodayDate(),
            role = if (_uiState.value.currentUserRole == "owner") "Owner" else "Manager",
            hash = newHash.take(8),
            previousHash = prevHash
        )
        _uiState.update {
            it.copy(
                managerPinHash = hash,
                managerPinSalt = salt,
                managerFailedAttempts = 0,
                managerLockoutUntilTimestamp = 0L,
                useFingerprintForManager = enableFingerprint,
                isManagerUnlocked = true,
                lastManagerUnlockTimestamp = System.currentTimeMillis(),
                qrActivityLogs = listOf(newLog) + currentLogs
            )
        }
    }

    fun verifyManagerPin(pin: String): Boolean {
        val state = _uiState.value
        val now = System.currentTimeMillis()
        if (state.managerLockoutUntilTimestamp > now) {
            return false
        }
        val currentHash = state.managerPinHash ?: return false
        val computed = CryptoEngine.hashPinString(pin.trim(), state.managerPinSalt)
        val isMatch = java.security.MessageDigest.isEqual(
            computed.toByteArray(java.nio.charset.StandardCharsets.UTF_8),
            currentHash.toByteArray(java.nio.charset.StandardCharsets.UTF_8)
        )
        if (isMatch) {
            securityPrefs()?.edit()
                ?.putInt("manager_failed_attempts", 0)
                ?.putLong("manager_lockout_until", 0L)
                ?.apply()
            _uiState.update {
                it.copy(
                    managerFailedAttempts = 0,
                    managerLockoutUntilTimestamp = 0L,
                    isManagerUnlocked = true,
                    lastManagerUnlockTimestamp = now
                )
            }
            return true
        } else {
            val baseAttempts = if (state.managerLockoutUntilTimestamp in 1..now) 0 else state.managerFailedAttempts
            val newAttempts = baseAttempts + 1
            val lockoutMs = when {
                newAttempts >= 9 -> 30 * 60 * 1000L // 30 minutes
                newAttempts >= 6 -> 5 * 60 * 1000L  // 5 minutes
                newAttempts >= 3 -> 30 * 1000L       // 30 seconds
                else -> 0L
            }
            val lockoutUntil = if (lockoutMs > 0) now + lockoutMs else 0L
            securityPrefs()?.edit()
                ?.putInt("manager_failed_attempts", newAttempts)
                ?.putLong("manager_lockout_until", lockoutUntil)
                ?.apply()
            _uiState.update {
                it.copy(
                    managerFailedAttempts = newAttempts,
                    managerLockoutUntilTimestamp = lockoutUntil
                )
            }
            return false
        }
    }

    fun unlockManagerWithFingerprint() {
        securityPrefs()?.edit()
            ?.putInt("manager_failed_attempts", 0)
            ?.putLong("manager_lockout_until", 0L)
            ?.apply()
        _uiState.update {
            it.copy(
                managerFailedAttempts = 0,
                managerLockoutUntilTimestamp = 0L,
                isManagerUnlocked = true,
                lastManagerUnlockTimestamp = System.currentTimeMillis()
            )
        }
    }

    fun lockManager() {
        _uiState.update { it.copy(isManagerUnlocked = false) }
    }

    fun resetManagerPinAfterDeviceAuth(newPin: String) {
        val clean = newPin.trim()
        require(clean.length in 4..6 && clean.all { it.isDigit() }) {
            "Manager PIN must be 4 to 6 numeric digits"
        }
        val salt = CryptoEngine.generateSecureSalt()
        val hash = CryptoEngine.hashPinString(clean, salt)
        securityPrefs()?.edit()
            ?.putString("manager_pin_hash", hash)
            ?.putString("manager_pin_salt", salt)
            ?.putInt("manager_failed_attempts", 0)
            ?.putLong("manager_lockout_until", 0L)
            ?.apply()
        val currentLogs = _uiState.value.qrActivityLogs
        val prevHash = if (currentLogs.isNotEmpty()) currentLogs.first().hash else "00000000"
        val newHash = CryptoEngine.sha256("$prevHash|Manager PIN reset|$salt")
        val newLog = QrActivityLog(
            id = "QRL-${System.currentTimeMillis()}-${UUID.randomUUID().toString().take(4)}",
            action = "Manager PIN reset",
            details = "PIN reset after device authentication confirmation",
            timestamp = FinancialEngine.getTodayDate(),
            role = "Owner",
            hash = newHash.take(8),
            previousHash = prevHash
        )
        _uiState.update {
            it.copy(
                managerPinHash = hash,
                managerPinSalt = salt,
                managerFailedAttempts = 0,
                managerLockoutUntilTimestamp = 0L,
                isManagerUnlocked = true,
                lastManagerUnlockTimestamp = System.currentTimeMillis(),
                qrActivityLogs = listOf(newLog) + currentLogs
            )
        }
    }

    fun setAutoLockMinutes(minutes: Int) {
        val safe = minutes.coerceIn(1, 120)
        securityPrefs()?.edit()?.putInt("auto_lock_minutes", safe)?.apply()
        _uiState.update { it.copy(autoLockMinutes = safe) }
    }

    fun setUseFingerprintForManager(enabled: Boolean) {
        securityPrefs()?.edit()?.putBoolean("use_fingerprint_for_manager", enabled)?.apply()
        _uiState.update { it.copy(useFingerprintForManager = enabled) }
    }

    fun addQrCode(qr: UpiQrCode) {
        val current = _uiState.value.upiQrs
        val updated = if (qr.isDefault) {
            current.map { it.copy(isDefault = false) } + qr
        } else {
            current + qr
        }
        val currentLogs = _uiState.value.qrActivityLogs
        val prevHash = if (currentLogs.isNotEmpty()) currentLogs.first().hash else "00000000"
        val newHash = CryptoEngine.sha256("$prevHash|QR added|${qr.label}")
        val newLog = QrActivityLog(
            id = "QRL-${System.currentTimeMillis()}-${UUID.randomUUID().toString().take(4)}",
            action = "QR added",
            details = "${qr.label} (${qr.upiId})",
            timestamp = "Just now",
            role = if (_uiState.value.currentUserRole == "owner") "Owner" else "Manager",
            hash = newHash.take(8),
            previousHash = prevHash
        )
        _uiState.update {
            it.copy(
                upiQrs = updated,
                qrActivityLogs = listOf(newLog) + currentLogs
            )
        }
        launchDb("Failed to add QR code") { db ->
            db.withTransaction {
                if (qr.isDefault) {
                    updated.forEach { db.upiQrDao().insertQrCode(UpiQrCodeEntity.fromDomain(it)) }
                } else {
                    db.upiQrDao().insertQrCode(UpiQrCodeEntity.fromDomain(qr))
                }
            }
        }
    }

    fun updateQrCode(qr: UpiQrCode) {
        val current = _uiState.value.upiQrs
        val updated = current.map {
            if (it.id == qr.id) qr
            else if (qr.isDefault) it.copy(isDefault = false)
            else it
        }
        val currentLogs = _uiState.value.qrActivityLogs
        val prevHash = if (currentLogs.isNotEmpty()) currentLogs.first().hash else "00000000"
        val newHash = CryptoEngine.sha256("$prevHash|UPI ID updated|${qr.upiId}")
        val newLog = QrActivityLog(
            id = "QRL-${System.currentTimeMillis()}-${UUID.randomUUID().toString().take(4)}",
            action = "UPI ID updated",
            details = "${qr.label} · ${qr.upiId}",
            timestamp = "Just now",
            role = if (_uiState.value.currentUserRole == "owner") "Owner" else "Manager",
            hash = newHash.take(8),
            previousHash = prevHash
        )
        _uiState.update {
            it.copy(
                upiQrs = updated,
                qrActivityLogs = listOf(newLog) + currentLogs
            )
        }
        launchDb("Failed to update QR code") { db ->
            db.withTransaction {
                updated.forEach { db.upiQrDao().insertQrCode(UpiQrCodeEntity.fromDomain(it)) }
            }
        }
    }

    fun deleteQrCode(qrId: String) {
        val current = _uiState.value.upiQrs
        val target = current.find { it.id == qrId } ?: return
        var updated = current.filter { it.id != qrId }
        if (target.isDefault && updated.isNotEmpty()) {
            updated = updated.mapIndexed { idx, item -> if (idx == 0) item.copy(isDefault = true) else item }
        }
        val currentLogs = _uiState.value.qrActivityLogs
        val prevHash = if (currentLogs.isNotEmpty()) currentLogs.first().hash else "00000000"
        val newHash = CryptoEngine.sha256("$prevHash|QR deleted|${target.label}")
        val newLog = QrActivityLog(
            id = "QRL-${System.currentTimeMillis()}-${UUID.randomUUID().toString().take(4)}",
            action = "QR deleted",
            details = "${target.label} deleted",
            timestamp = "Just now",
            role = if (_uiState.value.currentUserRole == "owner") "Owner" else "Manager",
            hash = newHash.take(8),
            previousHash = prevHash
        )
        _uiState.update {
            it.copy(
                upiQrs = updated,
                qrActivityLogs = listOf(newLog) + currentLogs
            )
        }
        launchDb("Failed to delete QR code") { db ->
            db.withTransaction {
                db.upiQrDao().deleteQrCode(qrId)
                if (target.isDefault && updated.isNotEmpty()) {
                    updated.forEach { db.upiQrDao().insertQrCode(UpiQrCodeEntity.fromDomain(it)) }
                }
            }
        }
    }

    fun setDefaultQr(qrId: String) {
        val current = _uiState.value.upiQrs
        val target = current.find { it.id == qrId } ?: return
        val updated = current.map { it.copy(isDefault = it.id == qrId) }
        val currentLogs = _uiState.value.qrActivityLogs
        val prevHash = if (currentLogs.isNotEmpty()) currentLogs.first().hash else "00000000"
        val newHash = CryptoEngine.sha256("$prevHash|Default QR changed|${target.label}")
        val newLog = QrActivityLog(
            id = "QRL-${System.currentTimeMillis()}-${UUID.randomUUID().toString().take(4)}",
            action = "Default QR changed",
            details = "${target.label} set as default QR",
            timestamp = "Just now",
            role = if (_uiState.value.currentUserRole == "owner") "Owner" else "Manager",
            hash = newHash.take(8),
            previousHash = prevHash
        )
        _uiState.update {
            it.copy(
                upiQrs = updated,
                qrActivityLogs = listOf(newLog) + currentLogs
            )
        }
        launchDb("Failed to set default QR code") { db ->
            db.withTransaction {
                updated.forEach { db.upiQrDao().insertQrCode(UpiQrCodeEntity.fromDomain(it)) }
            }
        }
    }

    fun toggleQrEnabled(qrId: String) {
        val current = _uiState.value.upiQrs
        val enabledCount = current.count { it.isEnabled }
        val target = current.find { it.id == qrId } ?: return
        if (target.isEnabled && enabledCount <= 1) return // At least one QR must stay enabled
        val updated = current.map {
            if (it.id == qrId) it.copy(isEnabled = !it.isEnabled) else it
        }
        _uiState.update { it.copy(upiQrs = updated) }
        launchDb("Failed to toggle QR code") { db ->
            val itm = updated.find { it.id == qrId }
            if (itm != null) {
                db.upiQrDao().insertQrCode(UpiQrCodeEntity.fromDomain(itm))
            }
        }
    }

    fun addBankAccount(account: BankAccount) {
        val current = _uiState.value.bankAccounts
        val updated = if (account.isDefaultForQr) {
            current.map { it.copy(isDefaultForQr = false) } + account
        } else {
            current + account
        }
        _uiState.update { it.copy(bankAccounts = updated) }
        launchDb("Failed to save bank account") { db ->
            db.withTransaction {
                if (account.isDefaultForQr) {
                    updated.forEach { db.bankAccountDao().insertBankAccount(BankAccountEntity.fromDomain(it)) }
                } else {
                    db.bankAccountDao().insertBankAccount(BankAccountEntity.fromDomain(account))
                }
            }
        }
        recordAudit("BankAccount", account.id, "CREATE", "Added bank account ${account.bankName}", "Banking setup", _uiState.value.currentUserName)
        verifyAuditChain()
    }

    fun deleteBankAccount(id: String) {
        val updated = _uiState.value.bankAccounts.filter { it.id != id }
        _uiState.update { it.copy(bankAccounts = updated) }
        launchDb("Failed to delete bank account") { db ->
            db.bankAccountDao().deleteBankAccount(id)
        }
        recordAudit("BankAccount", id, "DELETE", "Deleted bank account $id", "Banking update", _uiState.value.currentUserName)
        verifyAuditChain()
    }

    fun addBranch(branch: Branch) {
        val current = _uiState.value.branches
        val updated = if (branch.isMain) {
            current.map { it.copy(isMain = false) } + branch
        } else {
            current + branch
        }
        _uiState.update { it.copy(branches = updated) }
        launchDb("Failed to add branch") { db ->
            db.withTransaction {
                if (branch.isMain) {
                    updated.forEach { db.branchDao().insertBranch(BranchEntity.fromDomain(it)) }
                } else {
                    db.branchDao().insertBranch(BranchEntity.fromDomain(branch))
                }
            }
        }
        recordAudit("Branch", branch.id, "CREATE", "Added branch ${branch.name}", "Branch creation", _uiState.value.currentUserName)
        verifyAuditChain()
    }

    fun setActiveBranch(branchId: String) {
        appPrefs()?.edit()?.putString("active_branch_id", branchId)?.apply()
        _uiState.update { it.copy(activeBranchId = branchId) }
    }

    fun adjustCash(amountRupees: Long, isAdd: Boolean, note: String) {
        if (amountRupees <= 0L) return
        adjustCashPaise(amountRupees * 100L, isAdd, note)
    }

    fun adjustCashPaise(paise: Long, isAdd: Boolean, note: String) {
        if (paise <= 0L) return
        val dateStr = FinancialEngine.getTodayDate()
        val txn = Transaction(
            id = "TXN-ADJ-${System.currentTimeMillis()}-${UUID.randomUUID().toString().take(6)}",
            branchId = _uiState.value.activeBranchId,
            date = dateStr,
            partyId = null,
            amountPaise = paise,
            type = if (isAdd) TransactionType.PaymentIn else TransactionType.Expense,
            paymentMode = PaymentMode.Cash,
            remarks = "Cash adjustment (${if (isAdd) "Added" else "Removed"}): $note",
            entryMode = "quick"
        )
        _uiState.update { it.copy(transactions = listOf(txn) + it.transactions) }
        if (database != null) {
            launchDb("Failed to persist cash adjustment") { db ->
                db.transactionDao().insertTransaction(TransactionEntity.fromDomain(txn))
            }
        }
        recordAudit("CashAdjustment", txn.id, "ADJUST", "${if (isAdd) "Added" else "Removed"} ${FinancialEngine.formatPaise(paise)} cash", note, _uiState.value.currentUserName)
        verifyAuditChain()
    }

    fun transferBetweenAccounts(fromId: String, toId: String, amountRupees: Long, note: String) {
        if (amountRupees <= 0L || fromId == toId) return
        transferBetweenAccountsPaise(fromId, toId, amountRupees * 100L, note)
    }

    fun transferBetweenAccountsPaise(fromId: String, toId: String, paise: Long, note: String) {
        if (paise <= 0L || fromId == toId) return
        val dateStr = FinancialEngine.getTodayDate()
        val timestamp = System.currentTimeMillis()
        val tag = UUID.randomUUID().toString().take(6)

        val outId = "TXN-TRF-OUT-$timestamp-$tag"
        val inId = "TXN-TRF-IN-$timestamp-$tag"

        val txnOut = Transaction(
            id = outId,
            branchId = _uiState.value.activeBranchId,
            date = dateStr,
            partyId = null,
            amountPaise = paise,
            type = TransactionType.PaymentOut,
            paymentMode = if (fromId == "CASH") PaymentMode.Cash else PaymentMode.Bank,
            bankAccountId = if (fromId != "CASH") fromId else null,
            remarks = "Transfer to ${if (toId == "CASH") "Cash" else "Bank"}: $note [Ref: $inId]",
            entryMode = "quick"
        )

        val txnIn = Transaction(
            id = inId,
            branchId = _uiState.value.activeBranchId,
            date = dateStr,
            partyId = null,
            amountPaise = paise,
            type = TransactionType.PaymentIn,
            paymentMode = if (toId == "CASH") PaymentMode.Cash else PaymentMode.Bank,
            bankAccountId = if (toId != "CASH") toId else null,
            remarks = "Transfer from ${if (fromId == "CASH") "Cash" else "Bank"}: $note [Ref: $outId]",
            entryMode = "quick"
        )

        _uiState.update {
            it.copy(transactions = listOf(txnOut, txnIn) + it.transactions)
        }
        if (database != null) {
            launchDb("Failed to persist account transfer") { db ->
                db.withTransaction {
                    db.transactionDao().insertAll(listOf(
                        TransactionEntity.fromDomain(txnOut),
                        TransactionEntity.fromDomain(txnIn)
                    ))
                }
            }
        }
        recordAudit("Transfer", txnOut.id, "TRANSFER", "Transferred ${FinancialEngine.formatPaise(paise)} from $fromId to $toId", note, _uiState.value.currentUserName)
        verifyAuditChain()
    }

    fun reconcileSelectedItems(itemIds: Set<String>) {
        val updated = _uiState.value.bankReconItems.map {
            if (itemIds.contains(it.id)) it.copy(isReconciled = true) else it
        }
        _uiState.update { it.copy(bankReconItems = updated) }
        if (database != null) {
            launchDb("Failed to save reconciled items") { db ->
                val toSave = updated.filter { itemIds.contains(it.id) }.map { BankReconEntity.fromDomain(it) }
                db.bankReconDao().insertAll(toSave)
            }
        }
    }

    // -------------------------------------------------------------
    fun setPrefilledGstr1Invoices(invoices: List<com.example.engine.Gstr1ExcelImportEngine.ImportedGstr1Invoice>) {
        _uiState.update { it.copy(prefilledGstr1Invoices = invoices) }
    }

    fun clearPrefilledGstr1Invoices() {
        _uiState.update { it.copy(prefilledGstr1Invoices = emptyList()) }
    }

    fun setRule88AStrategy(
        strategy: com.example.engine.CmaAndGstEngine.Rule88AStrategy,
        customPercent: Double? = null,
        customPaise: Long? = null
    ) {
        val edit = appPrefs()?.edit()?.putString("rule88a_strategy", strategy.name)
        if (customPercent != null) edit?.putLong("rule88a_custom_percent", customPercent.toRawBits()) else edit?.remove("rule88a_custom_percent")
        if (customPaise != null) edit?.putLong("rule88a_custom_paise", customPaise) else edit?.remove("rule88a_custom_paise")
        edit?.apply()
        _uiState.update {
            it.copy(
                rule88AStrategy = strategy,
                customRule88APercent = customPercent,
                customRule88APaise = customPaise
            )
        }
    }

    fun commitPrefilledInvoicesToLedger(invoices: List<com.example.engine.Gstr1ExcelImportEngine.ImportedGstr1Invoice>) {
        val existingParties = _uiState.value.parties.toMutableList()
        val newlyCreatedParties = mutableListOf<Party>()
        val newTxns = mutableListOf<Transaction>()

        invoices.forEach { imp ->
            var partyId: String? = null
            if (imp.isB2b || imp.gstin.isNotBlank()) {
                val found = existingParties.find { it.gstin.equals(imp.gstin, ignoreCase = true) }
                if (found != null) {
                    partyId = found.id
                } else {
                    val newParty = Party(
                        id = "PTY-IMP-${UUID.randomUUID().toString().take(8)}",
                        name = imp.partyName,
                        phone = "",
                        address = "POS State: ${imp.posStateCode}",
                        gstin = imp.gstin,
                        pan = if (imp.gstin.length >= 12) imp.gstin.substring(2, 12) else "",
                        stateCode = imp.posStateCode,
                        openingBalancePaise = 0L,
                        openingBalanceType = BalanceType.Receivable,
                        isSupplier = false
                    )
                    existingParties.add(newParty)
                    newlyCreatedParties.add(newParty)
                    partyId = newParty.id
                }
            }

            val lineItem = InvoiceLineItem(
                itemId = "ITM-GEN-${imp.hsnCode}",
                name = "${imp.partyName} (Sale)",
                hsnCode = imp.hsnCode.ifBlank { "9999" },
                quantityMilli = 1000L,
                unit = "PCS",
                ratePaise = imp.taxablePaise,
                gstRateBps = imp.gstRateBps,
                taxableAmountPaise = imp.taxablePaise,
                cgstPaise = imp.cgstPaise,
                sgstPaise = imp.sgstPaise,
                igstPaise = imp.igstPaise,
                totalPaise = imp.totalPaise
            )

            val txn = Transaction(
                id = "TXN-IMP-${UUID.randomUUID().toString().take(8)}",
                branchId = _uiState.value.activeBranchId,
                date = imp.date,
                partyId = partyId,
                amountPaise = imp.totalPaise,
                type = TransactionType.Sale,
                paymentMode = PaymentMode.Credit,
                remarks = "Imported from Excel spreadsheet (GSTR-1 pre-fill)",
                entryMode = "itemized",
                invoiceNumber = imp.invoiceNumber,
                items = listOf(lineItem)
            )
            newTxns.add(txn)
        }

        _uiState.update {
            it.copy(
                transactions = newTxns + it.transactions,
                parties = existingParties,
                prefilledGstr1Invoices = emptyList()
            )
        }

        if (database != null) {
            launchDb("Failed to persist imported GSTR-1 invoices") { db ->
                db.withTransaction {
                    if (newlyCreatedParties.isNotEmpty()) {
                        db.partyDao().insertAll(newlyCreatedParties.map { PartyEntity.fromDomain(it) })
                    }
                    if (newTxns.isNotEmpty()) {
                        db.transactionDao().insertAll(newTxns.map { TransactionEntity.fromDomain(it) })
                    }
                }
            }
        }
        if (newTxns.isNotEmpty()) {
            recordAudit("GSTR1_IMPORT", "BULK-EXCEL", "IMPORT", "Committed ${newTxns.size} invoices from Excel pre-fill", "GSTR-1 Excel Import", _uiState.value.currentUserName)
            verifyAuditChain()
        }
    }

    fun exportBackupJson(passphrase: String): String {
        val s = _uiState.value
        val converters = PabaruTypeConverters()
        val payload = org.json.JSONObject().apply {
            put("version", 3)
            put("generator", "Pabaru Billing Mobile Native")
            put("exportedAt", System.currentTimeMillis())
            put("profile", org.json.JSONObject().apply {
                put("id", s.profile.id)
                put("businessName", s.profile.businessName)
                put("entityType", s.profile.entityType.name)
                put("ownerName", s.profile.ownerName)
                put("businessType", s.profile.businessType)
                put("registrationType", s.profile.registrationType.name)
                put("homeStateCode", s.profile.homeStateCode)
                put("filingFrequency", s.profile.filingFrequency)
                put("plan", s.profile.plan)
                put("openingCashBalancePaise", s.profile.openingCashBalancePaise)
                put("openingCapitalPaise", s.profile.openingCapitalPaise)
                put("storeCode", s.profile.storeCode)
                put("pan", s.profile.pan)
                put("gstin", s.profile.gstin)
                put("udyamNumber", s.profile.udyamNumber)
                put("address", s.profile.address)
                put("email", s.profile.email)
                put("phone", s.profile.phone)
                put("isRegistered", s.profile.isRegistered)
            })
            put("parties", org.json.JSONArray().apply {
                s.parties.forEach { p ->
                    put(org.json.JSONObject().apply {
                        put("id", p.id); put("name", p.name); put("phone", p.phone); put("address", p.address)
                        put("gstin", p.gstin); put("pan", p.pan); put("stateCode", p.stateCode)
                        put("openingBalancePaise", p.openingBalancePaise)
                        put("openingBalanceType", p.openingBalanceType.name)
                        put("isSupplier", p.isSupplier)
                    })
                }
            })
            put("products", org.json.JSONArray().apply {
                s.products.forEach { p ->
                    put(org.json.JSONObject().apply {
                        put("id", p.id); put("name", p.name); put("barcode", p.barcode); put("sku", p.sku)
                        put("category", p.category); put("hsnCode", p.hsnCode); put("gstRateBps", p.gstRateBps)
                        put("unit", p.unit); put("purchasePricePaise", p.purchasePricePaise); put("salePricePaise", p.salePricePaise)
                        put("currentStockMilli", p.currentStockMilli); put("reorderLevelMilli", p.reorderLevelMilli)
                        put("openingStockMilli", p.openingStockMilli); put("hasBatchTracking", p.hasBatchTracking)
                        put("hasVariants", p.hasVariants); put("isArchived", p.isArchived)
                    })
                }
            })
            put("productVariants", org.json.JSONArray().apply {
                s.productVariants.forEach { v ->
                    put(org.json.JSONObject().apply {
                        put("id", v.id); put("productId", v.productId); put("variantName", v.variantName)
                        put("sku", v.sku); put("barcode", v.barcode); put("additionalPricePaise", v.additionalPricePaise)
                        put("currentStockMilli", v.currentStockMilli)
                    })
                }
            })
            put("stockBatches", org.json.JSONArray().apply {
                s.stockBatches.forEach { b ->
                    put(org.json.JSONObject().apply {
                        put("id", b.id); put("productId", b.productId); put("batchNumber", b.batchNumber)
                        put("mfgDate", b.mfgDate ?: org.json.JSONObject.NULL)
                        put("expiryDate", b.expiryDate); put("purchasePricePaise", b.purchasePricePaise)
                        put("initialQuantityMilli", b.initialQuantityMilli); put("availableQuantityMilli", b.availableQuantityMilli)
                        put("locationId", b.locationId)
                    })
                }
            })
            put("stockAdjustments", org.json.JSONArray().apply {
                s.stockAdjustments.forEach { adj ->
                    put(org.json.JSONObject().apply {
                        put("id", adj.id); put("productId", adj.productId); put("productName", adj.productName)
                        put("locationId", adj.locationId); put("quantityDeltaMilli", adj.quantityDeltaMilli)
                        put("reason", adj.reason.name); put("remarks", adj.remarks)
                        put("timestamp", adj.timestamp); put("adjustedBy", adj.adjustedBy)
                    })
                }
            })
            put("stockMovements", org.json.JSONArray().apply {
                s.stockMovements.forEach { mov ->
                    put(org.json.JSONObject().apply {
                        put("id", mov.id); put("productId", mov.productId); put("productName", mov.productName)
                        put("batchId", mov.batchId ?: org.json.JSONObject.NULL)
                        put("referenceTxnId", mov.referenceTxnId ?: org.json.JSONObject.NULL)
                        put("movementType", mov.movementType); put("quantityDeltaMilli", mov.quantityDeltaMilli)
                        put("balanceAfterMilli", mov.balanceAfterMilli); put("locationId", mov.locationId)
                        put("timestamp", mov.timestamp); put("remarks", mov.remarks)
                    })
                }
            })
            put("inventoryLocations", org.json.JSONArray().apply {
                s.inventoryLocations.forEach { loc ->
                    put(org.json.JSONObject().apply {
                        put("id", loc.id); put("name", loc.name); put("code", loc.code)
                        put("address", loc.address); put("isPrimary", loc.isPrimary)
                    })
                }
            })
            put("branches", org.json.JSONArray().apply {
                s.branches.forEach { br ->
                    put(org.json.JSONObject().apply {
                        put("id", br.id); put("name", br.name); put("code", br.code)
                        put("isMain", br.isMain); put("address", br.address)
                    })
                }
            })
            put("bankAccounts", org.json.JSONArray().apply {
                s.bankAccounts.forEach { b ->
                    put(org.json.JSONObject().apply {
                        put("id", b.id); put("accountName", b.accountName); put("bankName", b.bankName)
                        put("accountNumber", b.accountNumber); put("ifscCode", b.ifscCode); put("upiId", b.upiId)
                        put("openingBalancePaise", b.openingBalancePaise); put("isDefaultForQr", b.isDefaultForQr)
                    })
                }
            })
            put("bankReconItems", org.json.JSONArray().apply {
                s.bankReconItems.forEach { r ->
                    put(org.json.JSONObject().apply {
                        put("id", r.id); put("bankAccountId", r.bankAccountId); put("date", r.date)
                        put("description", r.description); put("amountPaise", r.amountPaise)
                        put("isCredit", r.isCredit); put("isReconciled", r.isReconciled)
                        put("matchedTxnId", r.matchedTxnId ?: org.json.JSONObject.NULL)
                        put("referenceNo", r.referenceNo); put("matchConfidence", r.matchConfidence)
                        put("matchReason", r.matchReason); put("discrepancyNote", r.discrepancyNote)
                        put("isUserConfirmed", r.isUserConfirmed)
                        put("reconciledAt", r.reconciledAt ?: org.json.JSONObject.NULL)
                    })
                }
            })
            put("upiQrs", org.json.JSONArray().apply {
                s.upiQrs.forEach { q ->
                    put(org.json.JSONObject().apply {
                        put("id", q.id); put("label", q.label); put("upiId", q.upiId)
                        put("linkedBankId", q.linkedBankId); put("isDefault", q.isDefault)
                        put("isEnabled", q.isEnabled); put("showOnInvoices", q.showOnInvoices)
                        put("qrImageUri", q.qrImageUri ?: org.json.JSONObject.NULL)
                    })
                }
            })
            put("transactions", org.json.JSONArray().apply {
                s.transactions.forEach { t ->
                    put(org.json.JSONObject().apply {
                        put("id", t.id); put("branchId", t.branchId); put("date", t.date)
                        put("partyId", t.partyId ?: org.json.JSONObject.NULL)
                        put("amountPaise", t.amountPaise); put("type", t.type.name)
                        put("expenseCategory", t.expenseCategory.name); put("paymentMode", t.paymentMode.name)
                        put("bankAccountId", t.bankAccountId ?: org.json.JSONObject.NULL)
                        put("remarks", t.remarks); put("entryMode", t.entryMode); put("invoiceNumber", t.invoiceNumber)
                        put("isVoided", t.isVoided); put("voidReason", t.voidReason)
                        put("voidedAt", t.voidedAt ?: org.json.JSONObject.NULL)
                        put("itemsJson", converters.fromInvoiceLineItemList(t.items))
                    })
                }
            })
            put("auditLogs", org.json.JSONArray().apply {
                s.auditLogs.forEach { a ->
                    put(org.json.JSONObject().apply {
                        put("id", a.id); put("previousHash", a.previousHash); put("recordHash", a.recordHash)
                        put("entityType", a.entityType); put("entityId", a.entityId); put("action", a.action)
                        put("details", a.details); put("reason", a.reason); put("actor", a.actor); put("timestamp", a.timestamp)
                    })
                }
            })
        }.toString()
        return CryptoEngine.encryptAesGcm(payload, passphrase)
    }

    fun restoreEncryptedBackup(envelopeJson: String, passphrase: String): Result<String> {
        return try {
            val plain = CryptoEngine.decryptAesGcm(envelopeJson, passphrase)
            val root = org.json.JSONObject(plain)
            val converters = PabaruTypeConverters()

            val profObj = root.getJSONObject("profile")
            val restoredProfile = BusinessProfile(
                id = profObj.optString("id", "BIZ-001"),
                businessName = profObj.optString("businessName", "Pabaru Billing"),
                entityType = runCatching { BusinessEntityType.valueOf(profObj.optString("entityType")) }.getOrDefault(BusinessEntityType.Proprietorship),
                ownerName = profObj.optString("ownerName", ""),
                businessType = profObj.optString("businessType", "Wholesale & Retail"),
                registrationType = runCatching { RegistrationType.valueOf(profObj.optString("registrationType")) }.getOrDefault(RegistrationType.regular),
                homeStateCode = profObj.optString("homeStateCode", "27"),
                filingFrequency = profObj.optString("filingFrequency", "Monthly"),
                plan = profObj.optString("plan", "standard"),
                openingCashBalancePaise = profObj.optLong("openingCashBalancePaise", 0L),
                openingCapitalPaise = profObj.optLong("openingCapitalPaise", 0L),
                storeCode = profObj.optString("storeCode", _uiState.value.profile.storeCode),
                pan = profObj.optString("pan", ""),
                gstin = profObj.optString("gstin", ""),
                udyamNumber = profObj.optString("udyamNumber", ""),
                address = profObj.optString("address", ""),
                email = profObj.optString("email", ""),
                phone = profObj.optString("phone", ""),
                isRegistered = true
            )

            val partiesArr = root.optJSONArray("parties") ?: org.json.JSONArray()
            val restoredParties = (0 until partiesArr.length()).map { i ->
                val o = partiesArr.getJSONObject(i)
                Party(
                    id = o.getString("id"),
                    name = o.getString("name"),
                    phone = o.optString("phone", ""),
                    address = o.optString("address", ""),
                    gstin = o.optString("gstin", ""),
                    pan = o.optString("pan", ""),
                    stateCode = o.optString("stateCode", "27"),
                    openingBalancePaise = o.optLong("openingBalancePaise", 0L),
                    openingBalanceType = runCatching { BalanceType.valueOf(o.optString("openingBalanceType")) }.getOrDefault(BalanceType.Receivable),
                    isSupplier = o.optBoolean("isSupplier", false)
                )
            }

            val productsArr = root.optJSONArray("products") ?: org.json.JSONArray()
            val restoredProducts = (0 until productsArr.length()).map { i ->
                val o = productsArr.getJSONObject(i)
                ProductItem(
                    id = o.getString("id"),
                    name = o.getString("name"),
                    barcode = o.optString("barcode", ""),
                    sku = o.optString("sku", ""),
                    category = o.optString("category", "General"),
                    hsnCode = o.optString("hsnCode", "1006"),
                    gstRateBps = o.optInt("gstRateBps", 0),
                    unit = o.optString("unit", "PCS"),
                    purchasePricePaise = o.optLong("purchasePricePaise", 0L),
                    salePricePaise = o.optLong("salePricePaise", 0L),
                    currentStockMilli = o.optLong("currentStockMilli", 0L),
                    reorderLevelMilli = o.optLong("reorderLevelMilli", 10000L),
                    openingStockMilli = o.optLong("openingStockMilli", 0L),
                    hasBatchTracking = o.optBoolean("hasBatchTracking", false),
                    hasVariants = o.optBoolean("hasVariants", false),
                    isArchived = o.optBoolean("isArchived", false)
                )
            }

            val variantsArr = root.optJSONArray("productVariants") ?: org.json.JSONArray()
            val restoredVariants = (0 until variantsArr.length()).map { i ->
                val o = variantsArr.getJSONObject(i)
                ProductVariant(
                    id = o.getString("id"),
                    productId = o.getString("productId"),
                    variantName = o.getString("variantName"),
                    sku = o.optString("sku", ""),
                    barcode = o.optString("barcode", ""),
                    additionalPricePaise = o.optLong("additionalPricePaise", 0L),
                    currentStockMilli = o.optLong("currentStockMilli", 0L)
                )
            }

            val batchesArr = root.optJSONArray("stockBatches") ?: org.json.JSONArray()
            val restoredBatches = (0 until batchesArr.length()).map { i ->
                val o = batchesArr.getJSONObject(i)
                StockBatch(
                    id = o.getString("id"),
                    productId = o.getString("productId"),
                    batchNumber = o.optString("batchNumber", ""),
                    mfgDate = if (o.isNull("mfgDate")) null else o.optString("mfgDate"),
                    expiryDate = o.optString("expiryDate", ""),
                    purchasePricePaise = o.optLong("purchasePricePaise", 0L),
                    initialQuantityMilli = o.optLong("initialQuantityMilli", 0L),
                    availableQuantityMilli = o.optLong("availableQuantityMilli", 0L),
                    locationId = o.optString("locationId", "LOC-MAIN")
                )
            }

            val adjustmentsArr = root.optJSONArray("stockAdjustments") ?: org.json.JSONArray()
            val restoredAdjustments = (0 until adjustmentsArr.length()).map { i ->
                val o = adjustmentsArr.getJSONObject(i)
                StockAdjustment(
                    id = o.getString("id"),
                    productId = o.getString("productId"),
                    productName = o.optString("productName", ""),
                    locationId = o.optString("locationId", "LOC-MAIN"),
                    quantityDeltaMilli = o.optLong("quantityDeltaMilli", 0L),
                    reason = runCatching { StockAdjustmentReason.valueOf(o.optString("reason")) }.getOrDefault(StockAdjustmentReason.COUNT_CORRECTION),
                    remarks = o.optString("remarks", ""),
                    timestamp = o.optString("timestamp", ""),
                    adjustedBy = o.optString("adjustedBy", "Owner")
                )
            }

            val movementsArr = root.optJSONArray("stockMovements") ?: org.json.JSONArray()
            val restoredMovements = (0 until movementsArr.length()).map { i ->
                val o = movementsArr.getJSONObject(i)
                StockMovement(
                    id = o.getString("id"),
                    productId = o.getString("productId"),
                    productName = o.optString("productName", ""),
                    batchId = if (o.isNull("batchId")) null else o.optString("batchId"),
                    referenceTxnId = if (o.isNull("referenceTxnId")) null else o.optString("referenceTxnId"),
                    movementType = o.optString("movementType", "ADJUSTMENT"),
                    quantityDeltaMilli = o.optLong("quantityDeltaMilli", 0L),
                    balanceAfterMilli = o.optLong("balanceAfterMilli", 0L),
                    locationId = o.optString("locationId", "LOC-MAIN"),
                    timestamp = o.optString("timestamp", ""),
                    remarks = o.optString("remarks", "")
                )
            }

            val locationsArr = root.optJSONArray("inventoryLocations") ?: org.json.JSONArray()
            val restoredLocations = (0 until locationsArr.length()).map { i ->
                val o = locationsArr.getJSONObject(i)
                InventoryLocation(
                    id = o.getString("id"),
                    name = o.getString("name"),
                    code = o.optString("code", ""),
                    address = o.optString("address", ""),
                    isPrimary = o.optBoolean("isPrimary", false)
                )
            }

            val branchesArr = root.optJSONArray("branches") ?: org.json.JSONArray()
            val restoredBranches = (0 until branchesArr.length()).map { i ->
                val o = branchesArr.getJSONObject(i)
                Branch(
                    id = o.getString("id"),
                    name = o.getString("name"),
                    code = o.optString("code", ""),
                    isMain = o.optBoolean("isMain", false),
                    address = o.optString("address", "")
                )
            }

            val banksArr = root.optJSONArray("bankAccounts") ?: org.json.JSONArray()
            val restoredBanks = (0 until banksArr.length()).map { i ->
                val o = banksArr.getJSONObject(i)
                BankAccount(
                    id = o.getString("id"),
                    accountName = o.getString("accountName"),
                    bankName = o.optString("bankName", ""),
                    accountNumber = o.optString("accountNumber", ""),
                    ifscCode = o.optString("ifscCode", ""),
                    upiId = o.optString("upiId", ""),
                    openingBalancePaise = o.optLong("openingBalancePaise", 0L),
                    isDefaultForQr = o.optBoolean("isDefaultForQr", false)
                )
            }

            val reconArr = root.optJSONArray("bankReconItems") ?: org.json.JSONArray()
            val restoredReconItems = (0 until reconArr.length()).map { i ->
                val o = reconArr.getJSONObject(i)
                BankReconItem(
                    id = o.getString("id"),
                    bankAccountId = o.getString("bankAccountId"),
                    date = o.getString("date"),
                    description = o.optString("description", ""),
                    amountPaise = o.optLong("amountPaise", 0L),
                    isCredit = o.optBoolean("isCredit", false),
                    isReconciled = o.optBoolean("isReconciled", false),
                    matchedTxnId = if (o.isNull("matchedTxnId")) null else o.optString("matchedTxnId"),
                    referenceNo = o.optString("referenceNo", ""),
                    matchConfidence = o.optInt("matchConfidence", 0),
                    matchReason = o.optString("matchReason", ""),
                    discrepancyNote = o.optString("discrepancyNote", ""),
                    isUserConfirmed = o.optBoolean("isUserConfirmed", false),
                    reconciledAt = if (o.isNull("reconciledAt")) null else o.optString("reconciledAt")
                )
            }

            val qrArr = root.optJSONArray("upiQrs") ?: org.json.JSONArray()
            val restoredQrs = (0 until qrArr.length()).map { i ->
                val o = qrArr.getJSONObject(i)
                UpiQrCode(
                    id = o.getString("id"),
                    label = o.getString("label"),
                    upiId = o.getString("upiId"),
                    linkedBankId = o.optString("linkedBankId", ""),
                    isDefault = o.optBoolean("isDefault", false),
                    isEnabled = o.optBoolean("isEnabled", true),
                    showOnInvoices = o.optBoolean("showOnInvoices", true),
                    qrImageUri = if (o.isNull("qrImageUri")) null else o.optString("qrImageUri")
                )
            }

            val txnsArr = root.optJSONArray("transactions") ?: org.json.JSONArray()
            val restoredTxns = (0 until txnsArr.length()).map { i ->
                val o = txnsArr.getJSONObject(i)
                Transaction(
                    id = o.getString("id"),
                    branchId = o.optString("branchId", _uiState.value.activeBranchId),
                    date = o.getString("date"),
                    partyId = if (o.isNull("partyId")) null else o.optString("partyId"),
                    amountPaise = o.optLong("amountPaise", 0L),
                    type = runCatching { TransactionType.valueOf(o.getString("type")) }.getOrDefault(TransactionType.Sale),
                    expenseCategory = runCatching { ExpenseCategory.valueOf(o.optString("expenseCategory")) }.getOrDefault(ExpenseCategory.Direct),
                    paymentMode = runCatching { PaymentMode.valueOf(o.optString("paymentMode")) }.getOrDefault(PaymentMode.Cash),
                    bankAccountId = if (o.isNull("bankAccountId")) null else o.optString("bankAccountId"),
                    remarks = o.optString("remarks", ""),
                    entryMode = o.optString("entryMode", "quick"),
                    invoiceNumber = o.optString("invoiceNumber", ""),
                    isVoided = o.optBoolean("isVoided", false),
                    voidReason = o.optString("voidReason", ""),
                    voidedAt = if (o.isNull("voidedAt")) null else o.optString("voidedAt"),
                    items = converters.toInvoiceLineItemList(o.optString("itemsJson", "[]"))
                )
            }

            val logsArr = root.optJSONArray("auditLogs") ?: org.json.JSONArray()
            val restoredLogs = (0 until logsArr.length()).map { i ->
                val o = logsArr.getJSONObject(i)
                AuditLog(
                    id = o.getString("id"),
                    previousHash = o.getString("previousHash"),
                    recordHash = o.getString("recordHash"),
                    entityType = o.getString("entityType"),
                    entityId = o.getString("entityId"),
                    action = o.getString("action"),
                    details = o.getString("details"),
                    reason = o.getString("reason"),
                    actor = o.getString("actor"),
                    timestamp = o.getString("timestamp")
                )
            }

            val chainResult = CryptoEngine.verifyAuditChain(restoredLogs)
            require(chainResult.valid) { "Backup audit chain verification failed: ${chainResult.errorMessage}" }

            securityPrefs()?.edit()
                ?.putBoolean("is_signed_in", true)
                ?.putBoolean("is_app_locked", false)
                ?.apply()

            synchronized(auditLock) {
                _uiState.update {
                    it.copy(
                        profile = restoredProfile,
                        isAppLocked = false,
                        parties = restoredParties,
                        products = restoredProducts,
                        productVariants = restoredVariants,
                        stockBatches = restoredBatches,
                        stockAdjustments = restoredAdjustments,
                        stockMovements = restoredMovements,
                        inventoryLocations = restoredLocations.ifEmpty { it.inventoryLocations },
                        branches = restoredBranches.ifEmpty { it.branches },
                        bankAccounts = restoredBanks,
                        bankReconItems = restoredReconItems,
                        upiQrs = restoredQrs,
                        transactions = restoredTxns,
                        auditLogs = restoredLogs,
                        auditResult = chainResult,
                        prefilledGstr1Invoices = emptyList()
                    )
                }
            }

            if (database != null) {
                launchDb("Failed to persist restored backup") { db ->
                    db.withTransaction {
                        db.partyDao().deleteAll()
                        db.productDao().deleteAll()
                        db.productVariantDao().deleteAll()
                        db.stockBatchDao().deleteAll()
                        db.stockAdjustmentDao().deleteAll()
                        db.stockMovementDao().deleteAll()
                        db.bankAccountDao().deleteAll()
                        db.bankReconDao().deleteAll()
                        db.upiQrDao().deleteAll()
                        db.transactionDao().deleteAll()
                        db.auditLogDao().deleteAll()
                        if (restoredLocations.isNotEmpty()) {
                            db.inventoryLocationDao().deleteAll()
                            db.inventoryLocationDao().insertAll(restoredLocations.map { InventoryLocationEntity.fromDomain(it) })
                        }
                        if (restoredBranches.isNotEmpty()) {
                            db.branchDao().deleteAll()
                            db.branchDao().insertAll(restoredBranches.map { BranchEntity.fromDomain(it) })
                        }

                        db.businessProfileDao().insertOrUpdateProfile(BusinessProfileEntity.fromDomain(restoredProfile))
                        db.partyDao().insertAll(restoredParties.map { PartyEntity.fromDomain(it) })
                        db.productDao().insertAll(restoredProducts.map { ProductEntity.fromDomain(it) })
                        db.productVariantDao().insertAll(restoredVariants.map { ProductVariantEntity.fromDomain(it) })
                        db.stockBatchDao().insertAll(restoredBatches.map { StockBatchEntity.fromDomain(it) })
                        db.stockAdjustmentDao().insertAll(restoredAdjustments.map { StockAdjustmentEntity.fromDomain(it) })
                        db.stockMovementDao().insertAll(restoredMovements.map { StockMovementEntity.fromDomain(it) })
                        db.bankAccountDao().insertAll(restoredBanks.map { BankAccountEntity.fromDomain(it) })
                        db.bankReconDao().insertAll(restoredReconItems.map { BankReconEntity.fromDomain(it) })
                        db.upiQrDao().insertAll(restoredQrs.map { UpiQrCodeEntity.fromDomain(it) })
                        db.transactionDao().insertAll(restoredTxns.map { TransactionEntity.fromDomain(it) })
                        db.auditLogDao().insertAll(restoredLogs.map { AuditLogEntity.fromDomain(it) })
                    }
                }
            }

            Result.success("Restored ${restoredParties.size} parties, ${restoredProducts.size} items, and ${restoredTxns.size} transactions")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
