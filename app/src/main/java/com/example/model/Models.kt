package com.example.model

enum class TransactionType {
    Sale, Purchase, PaymentIn, PaymentOut, Expense, Drawing, CreditNote, DebitNote, Quotation, NonGstBill
}

enum class PaymentMode {
    Cash, Bank, UPI, Cheque, Credit
}

enum class ExpenseCategory {
    Direct, Indirect
}

enum class BalanceType {
    Receivable, Payable, Settled
}

enum class RegistrationType {
    regular, composition, unregistered
}

enum class BusinessEntityType(val displayName: String, val signatoryTitle: String) {
    Proprietorship("Sole Proprietorship", "Proprietor"),
    Partnership("Partnership Firm", "Partner"),
    PrivateLimited("Private Limited Company", "Director"),
    PublicLimited("Public Limited Company", "Director"),
    LLP("Limited Liability Partnership (LLP)", "Designated Partner"),
    HUF("Hindu Undivided Family (HUF)", "Karta")
}

enum class AppColorTheme(
    val id: String,
    val displayName: String,
    val isDark: Boolean,
    val headerHex: String,
    val cardHex: String,
    val buttonHex: String
) {
    MidnightTeal("midnight_teal", "Midnight Teal", true, "#0F172A", "#1E293B", "#0D9488"),
    ClinicBlue("clinic_blue", "Clinic Blue", false, "#1E3A8A", "#FFFFFF", "#1E3A8A"),
    FreshMint("fresh_mint", "Fresh Mint", false, "#0F766E", "#FFFFFF", "#0F766E"),
    WarmSand("warm_sand", "Warm Sand", false, "#9A3412", "#FFFFFF", "#C2410C"),
    RoyalPurple("royal_purple", "Royal Purple", true, "#1E1830", "#1E1830", "#8B5CF6"),
    AmoledBlack("amoled_black", "AMOLED Black", true, "#000000", "#0B0B0B", "#14B8A6");

    companion object {
        fun fromId(id: String): AppColorTheme =
            values().firstOrNull { it.id == id } ?: MidnightTeal

        // Backward compatibility accessors
        val NavyExecutive: AppColorTheme get() = ClinicBlue
        val LightClean: AppColorTheme get() = FreshMint
        val SlateDark: AppColorTheme get() = MidnightTeal
    }
}

data class BusinessProfile(
    val id: String = "BIZ-001",
    val businessName: String = "Pabaru Billing Enterprises",
    val entityType: BusinessEntityType = BusinessEntityType.Proprietorship,
    val ownerName: String = "Pabaru Rameshwar", // Name of Proprietor / Partner / Director
    val businessType: String = "Wholesale & Retail",
    val registrationType: RegistrationType = RegistrationType.regular,
    val homeStateCode: String = "27", // Maharashtra
    val filingFrequency: String = "Monthly",
    val plan: String = "standard",
    val openingCashBalancePaise: Long = 5000000L, // ₹ 50,000.00
    val openingCapitalPaise: Long = 25000000L,    // ₹ 2,50,000.00
    val storeCode: String = "TL-7319",
    val pan: String = "ABCDE1234F",
    val gstin: String = "27ABCDE1234F1Z5",
    val udyamNumber: String = "UDYAM-MH-01-0019283",
    val address: String = "Shop 12, Swadeshi Market, Dadar West, Mumbai",
    val email: String = "contact@pabarubilling.com",
    val phone: String = "9820011223",
    val isRegistered: Boolean = true // Set to true after registration/sign-in
)

data class Branch(
    val id: String,
    val name: String,
    val code: String,
    val isMain: Boolean,
    val address: String
)

data class Party(
    val id: String,
    val name: String,
    val phone: String,
    val address: String,
    val gstin: String,
    val pan: String,
    val stateCode: String,
    val openingBalancePaise: Long,
    val openingBalanceType: BalanceType,
    val isSupplier: Boolean = false // Customer vs Supplier designation
)

data class BankAccount(
    val id: String,
    val accountName: String,
    val bankName: String,
    val accountNumber: String,
    val ifscCode: String,
    val upiId: String,
    val openingBalancePaise: Long,
    val isDefaultForQr: Boolean
)

data class BankReconItem(
    val id: String,
    val bankAccountId: String,
    val date: String,
    val description: String,
    val amountPaise: Long,
    val isCredit: Boolean, // True for deposit, False for withdrawal
    val isReconciled: Boolean = false,
    val matchedTxnId: String? = null,
    val referenceNo: String = "",
    val matchConfidence: Int = 0,
    val matchReason: String = "",
    val discrepancyNote: String = "",
    val isUserConfirmed: Boolean = false,
    val reconciledAt: String? = null
)

data class UpiQrCode(
    val id: String,
    val label: String,
    val upiId: String,
    val linkedBankId: String,
    val isDefault: Boolean = false,
    val isEnabled: Boolean = true,
    val showOnInvoices: Boolean = true,
    val qrImageUri: String? = null
)

data class QrActivityLog(
    val id: String,
    val action: String,
    val details: String,
    val timestamp: String,
    val role: String,
    val hash: String = "",
    val previousHash: String = ""
)

object GstSlabs {
    val STANDARD_BPS = listOf(0, 500, 1800, 4000)
    val SPECIAL_BPS = listOf(25, 300) // rough stones, gold/silver
    fun label(bps: Int): String = if (bps % 100 == 0) "${bps / 100}%" else "${bps / 100.0}%"
}

fun allowsDecimal(unit: String): Boolean {
    val u = unit.trim().uppercase()
    return u in listOf("KG", "KGS", "KILOGRAM", "LTR", "LITRE", "LITER", "MTR", "METER", "METRE", "GRAM", "GM")
}

data class ProductItem(
    val id: String,
    val name: String,
    val barcode: String = "",
    val sku: String = "",
    val hsnCode: String = "1006",
    val gstRateBps: Int = 0,
    val unit: String = "PCS",
    val purchasePricePaise: Long = 0L,
    val salePricePaise: Long = 0L,
    val currentStockMilli: Long = 0L,
    val reorderLevelMilli: Long = 10000L,
    val category: String = "General",
    val openingStockMilli: Long = 0L,
    val hasBatchTracking: Boolean = false,
    val hasVariants: Boolean = false,
    val isArchived: Boolean = false,
    val imageUrl: String? = null
) {
    val currentStock: Int get() = (currentStockMilli / 1000L).toInt()
    val currentStockDouble: Double get() = currentStockMilli / 1000.0
    val reorderLevel: Int get() = (reorderLevelMilli / 1000L).toInt()
    val openingStock: Int get() = (openingStockMilli / 1000L).toInt()
    val gstRate: Int get() = gstRateBps / 100

    constructor(
        id: String,
        name: String,
        barcode: String = "",
        sku: String = "",
        hsnCode: String = "1006",
        gstRate: Int = 0,
        unit: String = "PCS",
        purchasePricePaise: Long = 0L,
        salePricePaise: Long = 0L,
        currentStock: Int = 0,
        reorderLevel: Int = 10,
        category: String = "General",
        openingStock: Int = 0,
        hasBatchTracking: Boolean = false,
        hasVariants: Boolean = false,
        isArchived: Boolean = false,
        imageUrl: String? = null
    ) : this(
        id = id,
        name = name,
        barcode = barcode,
        sku = sku,
        hsnCode = hsnCode,
        gstRateBps = gstRate * 100,
        unit = unit,
        purchasePricePaise = purchasePricePaise,
        salePricePaise = salePricePaise,
        currentStockMilli = currentStock.toLong() * 1000L,
        reorderLevelMilli = reorderLevel.toLong() * 1000L,
        category = category,
        openingStockMilli = openingStock.toLong() * 1000L,
        hasBatchTracking = hasBatchTracking,
        hasVariants = hasVariants,
        isArchived = isArchived,
        imageUrl = imageUrl
    )
}

data class ProductVariant(
    val id: String,
    val productId: String,
    val variantName: String, // e.g. "Size: L / Red"
    val sku: String = "",
    val barcode: String = "",
    val additionalPricePaise: Long = 0L,
    val currentStockMilli: Long = 0L
) {
    val currentStock: Int get() = (currentStockMilli / 1000L).toInt()
    constructor(
        id: String,
        productId: String,
        variantName: String,
        sku: String = "",
        barcode: String = "",
        additionalPricePaise: Long = 0L,
        currentStock: Int = 0
    ) : this(id, productId, variantName, sku, barcode, additionalPricePaise, currentStock.toLong() * 1000L)
}

data class StockBatch(
    val id: String,
    val productId: String,
    val batchNumber: String,
    val mfgDate: String? = null,
    val expiryDate: String, // YYYY-MM-DD
    val purchasePricePaise: Long = 0L,
    val initialQuantityMilli: Long = 0L,
    val availableQuantityMilli: Long = 0L,
    val locationId: String = "LOC-MAIN"
) {
    val initialQuantity: Int get() = (initialQuantityMilli / 1000L).toInt()
    val availableQuantity: Int get() = (availableQuantityMilli / 1000L).toInt()
    val quantityRemaining: Int get() = availableQuantity
    val isExpired: Boolean get() {
        val clean = expiryDate.trim()
        if (clean.isBlank()) return false
        return try {
            val exp = java.time.LocalDate.parse(clean.take(10))
            exp.isBefore(java.time.LocalDate.now())
        } catch (_: Exception) {
            false
        }
    }
    val isExpiringSoon: Boolean get() {
        val clean = expiryDate.trim()
        if (clean.isBlank()) return false
        return try {
            val exp = java.time.LocalDate.parse(clean.take(10))
            val today = java.time.LocalDate.now()
            !exp.isBefore(today) && !exp.isAfter(today.plusDays(30))
        } catch (_: Exception) {
            false
        }
    }

    constructor(
        id: String,
        productId: String,
        batchNumber: String,
        mfgDate: String? = null,
        expiryDate: String,
        purchasePricePaise: Long = 0L,
        initialQuantity: Int,
        availableQuantity: Int,
        locationId: String = "LOC-MAIN"
    ) : this(
        id = id,
        productId = productId,
        batchNumber = batchNumber,
        mfgDate = mfgDate,
        expiryDate = expiryDate,
        purchasePricePaise = purchasePricePaise,
        initialQuantityMilli = initialQuantity.toLong() * 1000L,
        availableQuantityMilli = availableQuantity.toLong() * 1000L,
        locationId = locationId
    )

    constructor(
        id: String,
        productId: String,
        batchNumber: String,
        expiryDate: String,
        mfgDate: String? = null,
        quantityReceived: Int,
        quantityRemaining: Int,
        purchasePricePaise: Long = 0L,
        locationId: String = "LOC-MAIN"
    ) : this(
        id = id,
        productId = productId,
        batchNumber = batchNumber,
        mfgDate = mfgDate,
        expiryDate = expiryDate,
        purchasePricePaise = purchasePricePaise,
        initialQuantityMilli = quantityReceived.toLong() * 1000L,
        availableQuantityMilli = quantityRemaining.toLong() * 1000L,
        locationId = locationId
    )
}

enum class StockAdjustmentReason(val displayName: String) {
    DAMAGE("Damaged Goods"),
    EXPIRED("Expired Stock"),
    THEFT_OR_LOSS("Theft / Shrinkage"),
    COUNT_CORRECTION("Physical Count Correction"),
    INTERNAL_USE("Internal Store Usage"),
    INITIAL_SETUP("Opening Balance Adjustment")
}

data class StockAdjustment(
    val id: String,
    val productId: String,
    val productName: String,
    val locationId: String = "LOC-MAIN",
    val quantityDeltaMilli: Long,
    val reason: StockAdjustmentReason,
    val remarks: String = "",
    val timestamp: String,
    val adjustedBy: String = "Owner"
) {
    val quantityDelta: Int get() = (quantityDeltaMilli / 1000L).toInt()
    constructor(
        id: String,
        productId: String,
        productName: String,
        locationId: String = "LOC-MAIN",
        quantityDelta: Int,
        reason: StockAdjustmentReason,
        remarks: String = "",
        timestamp: String,
        adjustedBy: String = "Owner"
    ) : this(id, productId, productName, locationId, quantityDelta.toLong() * 1000L, reason, remarks, timestamp, adjustedBy)
}

data class StockMovement(
    val id: String,
    val productId: String,
    val productName: String,
    val batchId: String? = null,
    val referenceTxnId: String? = null,
    val movementType: String, // SALE_OUT, PURCHASE_IN, RETURN_IN, ADJUSTMENT, TRANSFER, VOID_REVERSAL
    val quantityDeltaMilli: Long,
    val balanceAfterMilli: Long,
    val locationId: String = "LOC-MAIN",
    val timestamp: String,
    val remarks: String = ""
) {
    val quantityDelta: Int get() = (quantityDeltaMilli / 1000L).toInt()
    val balanceAfter: Int get() = (balanceAfterMilli / 1000L).toInt()
    constructor(
        id: String,
        productId: String,
        productName: String,
        batchId: String? = null,
        referenceTxnId: String? = null,
        movementType: String,
        quantityDelta: Int,
        balanceAfter: Int,
        locationId: String = "LOC-MAIN",
        timestamp: String,
        remarks: String = ""
    ) : this(id, productId, productName, batchId, referenceTxnId, movementType, quantityDelta.toLong() * 1000L, balanceAfter.toLong() * 1000L, locationId, timestamp, remarks)
}

data class InventoryLocation(
    val id: String,
    val name: String,
    val code: String,
    val address: String = "",
    val isPrimary: Boolean = false
)

data class InvoiceLineItem(
    val itemId: String,
    val name: String,
    val hsnCode: String = "",
    val quantityMilli: Long = 1000L,
    val unit: String = "pcs",
    val ratePaise: Long = 0L,
    val gstRateBps: Int = 0,
    val taxableAmountPaise: Long = (quantityMilli * ratePaise + 500L) / 1000L,
    val cgstPaise: Long = 0L,
    val sgstPaise: Long = 0L,
    val igstPaise: Long = 0L,
    val totalPaise: Long = taxableAmountPaise + cgstPaise + sgstPaise + igstPaise,
    val cessPaise: Long = 0L
) {
    val quantity: Int get() = (quantityMilli / 1000L).toInt()
    val quantityDouble: Double get() = quantityMilli / 1000.0
    val gstRate: Int get() = gstRateBps / 100

    constructor(
        itemId: String,
        name: String,
        hsnCode: String = "",
        quantity: Int,
        unit: String = "pcs",
        ratePaise: Long = 0L,
        gstRate: Int = 0,
        taxableAmountPaise: Long = 0L,
        cgstPaise: Long = 0L,
        sgstPaise: Long = 0L,
        igstPaise: Long = 0L,
        totalPaise: Long = 0L
    ) : this(
        itemId = itemId,
        name = name,
        hsnCode = hsnCode,
        quantityMilli = quantity.toLong() * 1000L,
        unit = unit,
        ratePaise = ratePaise,
        gstRateBps = gstRate * 100,
        taxableAmountPaise = if (taxableAmountPaise > 0L) taxableAmountPaise else ((quantity.toLong() * 1000L * ratePaise + 500L) / 1000L),
        cgstPaise = cgstPaise,
        sgstPaise = sgstPaise,
        igstPaise = igstPaise,
        totalPaise = if (totalPaise > 0L) totalPaise else (taxableAmountPaise + cgstPaise + sgstPaise + igstPaise),
        cessPaise = 0L
    )
}

data class Transaction(
    val id: String,
    val branchId: String,
    val date: String,
    val partyId: String?,
    val amountPaise: Long,
    val type: TransactionType,
    val expenseCategory: ExpenseCategory = ExpenseCategory.Direct,
    val paymentMode: PaymentMode,
    val bankAccountId: String? = null,
    val remarks: String = "",
    val entryMode: String = "quick", // "quick" or "itemized"
    val invoiceNumber: String = "",
    val isVoided: Boolean = false,
    val voidReason: String = "",
    val voidedAt: String? = null,
    val items: List<InvoiceLineItem> = emptyList()
)

data class AuditLog(
    val id: String,
    val previousHash: String,
    val recordHash: String,
    val entityType: String,
    val entityId: String,
    val action: String,
    val details: String,
    val reason: String,
    val actor: String,
    val timestamp: String
)

enum class AllocatableScreen(
    val id: String,
    val title: String,
    val shortTitle: String,
    val description: String,
    val category: String
) {
    INVOICES(
        id = "invoices",
        title = "GST Invoices Hub",
        shortTitle = "Invoices",
        description = "Tax Invoices, Bills of Supply, Credit Notes & Quotations",
        category = "Billing"
    ),
    DAYBOOK(
        id = "daybook",
        title = "Daybook Register",
        shortTitle = "Daybook",
        description = "Daily sales ledger, cash in/out & transaction records",
        category = "Cash Flow"
    ),
    LEDGERS(
        id = "ledgers",
        title = "Parties & Khata",
        shortTitle = "Ledgers",
        description = "Customer khata, supplier accounts & collections",
        category = "Khata"
    ),
    CASH_BANK(
        id = "cash_bank",
        title = "Cash & Bank Balance",
        shortTitle = "Cash/Bank",
        description = "Live cash drawer tracking & bank account deposits",
        category = "Banking"
    ),
    STOCK(
        id = "stock",
        title = "Stock & Inventory",
        shortTitle = "Stock",
        description = "Item catalog, current stock & low-stock alerts",
        category = "Inventory"
    );

    companion object {
        val ALL_SCREEN_IDS = entries.map { it.id }.toSet()
        fun findById(id: String): AllocatableScreen? = entries.find { it.id == id }
    }
}
