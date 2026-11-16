package com.example.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.example.model.*
import kotlinx.coroutines.flow.Flow
import org.json.JSONArray
import org.json.JSONObject

// ==================== TYPE CONVERTERS ====================
class PabaruTypeConverters {

    @TypeConverter
    fun fromInvoiceLineItemList(items: List<InvoiceLineItem>?): String {
        if (items.isNullOrEmpty()) return "[]"
        val arr = JSONArray()
        for (it in items) {
            val obj = JSONObject()
            obj.put("itemId", it.itemId)
            obj.put("name", it.name)
            obj.put("hsnCode", it.hsnCode)
            obj.put("quantity", it.quantity)
            obj.put("quantityMilli", it.quantityMilli)
            obj.put("unit", it.unit)
            obj.put("ratePaise", it.ratePaise)
            obj.put("gstRate", it.gstRate)
            obj.put("gstRateBps", it.gstRateBps)
            obj.put("taxableAmountPaise", it.taxableAmountPaise)
            obj.put("cgstPaise", it.cgstPaise)
            obj.put("sgstPaise", it.sgstPaise)
            obj.put("igstPaise", it.igstPaise)
            obj.put("totalPaise", it.totalPaise)
            obj.put("cessPaise", it.cessPaise)
            arr.put(obj)
        }
        return arr.toString()
    }

    @TypeConverter
    fun toInvoiceLineItemList(json: String?): List<InvoiceLineItem> {
        if (json.isNullOrEmpty() || json == "[]") return emptyList()
        val list = mutableListOf<InvoiceLineItem>()
        try {
            val arr = JSONArray(json)
            for (i in 0 until arr.length()) {
                try {
                    val obj = arr.optJSONObject(i) ?: continue
                    val qtyMilli = if (obj.has("quantityMilli")) {
                        obj.optLong("quantityMilli", 1000L).coerceAtLeast(0L)
                    } else {
                        obj.optInt("quantity", 1).coerceAtLeast(0).toLong() * 1000L
                    }
                    val rateBps = if (obj.has("gstRateBps")) {
                        obj.optInt("gstRateBps", 0).coerceIn(0, 10000)
                    } else {
                        obj.optInt("gstRate", 5).coerceIn(0, 100) * 100
                    }
                    val ratePaise = obj.optLong("ratePaise", 0L).coerceAtLeast(0L)
                    val taxable = obj.optLong("taxableAmountPaise", 0L).coerceAtLeast(0L)
                    val cgst = obj.optLong("cgstPaise", 0L).coerceAtLeast(0L)
                    val sgst = obj.optLong("sgstPaise", 0L).coerceAtLeast(0L)
                    val igst = obj.optLong("igstPaise", 0L).coerceAtLeast(0L)
                    val cess = obj.optLong("cessPaise", 0L).coerceAtLeast(0L)
                    val total = obj.optLong("totalPaise", 0L).coerceAtLeast(0L)
                    list.add(
                        InvoiceLineItem(
                            itemId = obj.optString("itemId", "ITM-01"),
                            name = obj.optString("name", "Goods"),
                            hsnCode = obj.optString("hsnCode", "1006"),
                            quantityMilli = qtyMilli,
                            unit = obj.optString("unit", "PCS"),
                            ratePaise = ratePaise,
                            gstRateBps = rateBps,
                            taxableAmountPaise = taxable,
                            cgstPaise = cgst,
                            sgstPaise = sgst,
                            igstPaise = igst,
                            totalPaise = total,
                            cessPaise = cess
                        )
                    )
                } catch (e: Exception) {
                    android.util.Log.w("PabaruDatabase", "Skipping corrupted invoice line item at index $i: ${e.message}")
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("PabaruDatabase", "Failed to deserialize invoice line items: ${e.message}")
        }
        return list
    }
}

// ==================== ROOM ENTITIES ====================

@Entity(
    tableName = "transactions",
    indices = [
        Index("partyId"),
        Index("branchId"),
        Index("date")
    ]
)
data class TransactionEntity(
    @PrimaryKey val id: String,
    val branchId: String,
    val date: String,
    val partyId: String?,
    val amountPaise: Long,
    val transactionType: String,
    val expenseCategory: String,
    val paymentMode: String,
    val bankAccountId: String?,
    val remarks: String,
    val entryMode: String,
    val invoiceNumber: String,
    val isVoided: Boolean,
    val voidReason: String,
    val voidedAt: String?,
    val items: List<InvoiceLineItem>
) {
    fun toDomain(): Transaction = Transaction(
        id = id,
        branchId = branchId,
        date = date,
        partyId = partyId,
        amountPaise = amountPaise,
        type = when (transactionType.trim().replace(" ", "").replace("_", "").lowercase(java.util.Locale.ROOT)) {
            "paymentin" -> TransactionType.PaymentIn
            "paymentout" -> TransactionType.PaymentOut
            "creditnote" -> TransactionType.CreditNote
            "debitnote" -> TransactionType.DebitNote
            "sale" -> TransactionType.Sale
            "purchase" -> TransactionType.Purchase
            "expense" -> TransactionType.Expense
            "drawing" -> TransactionType.Drawing
            "quotation" -> TransactionType.Quotation
            "nongstbill", "estimate", "cashmemo" -> TransactionType.NonGstBill
            else -> try { TransactionType.valueOf(transactionType.trim()) } catch (_: Exception) { TransactionType.Sale }
        },
        expenseCategory = when (expenseCategory.trim().lowercase(java.util.Locale.ROOT)) {
            "direct" -> ExpenseCategory.Direct
            "indirect" -> ExpenseCategory.Indirect
            else -> try { ExpenseCategory.valueOf(expenseCategory.trim()) } catch (_: Exception) { ExpenseCategory.Direct }
        },
        paymentMode = when (paymentMode.trim().lowercase(java.util.Locale.ROOT)) {
            "cash" -> PaymentMode.Cash
            "bank", "pos", "card" -> PaymentMode.Bank
            "upi" -> PaymentMode.UPI
            "cheque" -> PaymentMode.Cheque
            "credit", "udhar" -> PaymentMode.Credit
            else -> try { PaymentMode.valueOf(paymentMode.trim()) } catch (_: Exception) { PaymentMode.Cash }
        },
        bankAccountId = bankAccountId,
        remarks = remarks,
        entryMode = entryMode,
        invoiceNumber = invoiceNumber,
        isVoided = isVoided,
        voidReason = voidReason,
        voidedAt = voidedAt,
        items = items
    )

    companion object {
        fun fromDomain(t: Transaction): TransactionEntity = TransactionEntity(
            id = t.id,
            branchId = t.branchId,
            date = t.date,
            partyId = t.partyId,
            amountPaise = t.amountPaise,
            transactionType = t.type.name,
            expenseCategory = t.expenseCategory.name,
            paymentMode = t.paymentMode.name,
            bankAccountId = t.bankAccountId,
            remarks = t.remarks,
            entryMode = t.entryMode,
            invoiceNumber = t.invoiceNumber,
            isVoided = t.isVoided,
            voidReason = t.voidReason,
            voidedAt = t.voidedAt,
            items = t.items
        )
    }
}

@Entity(
    tableName = "parties",
    indices = [
        Index("phone"),
        Index("gstin")
    ]
)
data class PartyEntity(
    @PrimaryKey val id: String,
    val name: String,
    val phone: String,
    val address: String,
    val gstin: String,
    val pan: String,
    val stateCode: String,
    val openingBalancePaise: Long,
    val openingBalanceType: String,
    val isSupplier: Boolean
) {
    fun toDomain(): Party = Party(
        id = id,
        name = name,
        phone = phone,
        address = address,
        gstin = gstin,
        pan = pan,
        stateCode = stateCode,
        openingBalancePaise = openingBalancePaise,
        openingBalanceType = when (openingBalanceType.trim()) {
            "Receivable", "Dr", "DR" -> BalanceType.Receivable
            "Payable", "Cr", "CR" -> BalanceType.Payable
            else -> try { BalanceType.valueOf(openingBalanceType) } catch (_: Exception) { BalanceType.Settled }
        },
        isSupplier = isSupplier
    )

    companion object {
        fun fromDomain(p: Party): PartyEntity = PartyEntity(
            id = p.id,
            name = p.name,
            phone = p.phone,
            address = p.address,
            gstin = p.gstin,
            pan = p.pan,
            stateCode = p.stateCode,
            openingBalancePaise = p.openingBalancePaise,
            openingBalanceType = p.openingBalanceType.name,
            isSupplier = p.isSupplier
        )
    }
}

@Entity(
    tableName = "products",
    indices = [
        Index("barcode")
    ]
)
data class ProductEntity(
    @PrimaryKey val id: String,
    val name: String,
    val barcode: String,
    val sku: String,
    val category: String = "General",
    val hsnCode: String,
    val gstRate: Int,
    val unit: String,
    val purchasePricePaise: Long,
    val salePricePaise: Long,
    val currentStock: Int,
    val reorderLevel: Int,
    val hasBatchTracking: Boolean = false,
    val hasVariants: Boolean = false,
    val isArchived: Boolean = false,
    val imageUrl: String? = null,
    val currentStockMilli: Long = 0L,
    val gstRateBps: Int = 0,
    val reorderLevelMilli: Long = 0L,
    val openingStockMilli: Long = 0L
) {
    fun toDomain(): ProductItem = ProductItem(
        id = id,
        name = name,
        barcode = barcode,
        sku = sku,
        category = category,
        hsnCode = hsnCode,
        gstRateBps = if (gstRateBps > 0) gstRateBps else gstRate * 100,
        unit = unit,
        purchasePricePaise = purchasePricePaise,
        salePricePaise = salePricePaise,
        reorderLevelMilli = if (reorderLevelMilli != 0L || reorderLevel == 0) reorderLevelMilli else reorderLevel.toLong() * 1000L,
        hasBatchTracking = hasBatchTracking,
        hasVariants = hasVariants,
        isArchived = isArchived,
        imageUrl = imageUrl,
        currentStockMilli = if (currentStockMilli != 0L) currentStockMilli else (currentStock.toLong() * 1000L),
        openingStockMilli = openingStockMilli
    )

    companion object {
        fun fromDomain(p: ProductItem): ProductEntity = ProductEntity(
            id = p.id,
            name = p.name,
            barcode = p.barcode,
            sku = p.sku,
            category = p.category,
            hsnCode = p.hsnCode,
            gstRate = p.gstRate,
            unit = p.unit,
            purchasePricePaise = p.purchasePricePaise,
            salePricePaise = p.salePricePaise,
            currentStock = p.currentStock,
            reorderLevel = p.reorderLevel,
            hasBatchTracking = p.hasBatchTracking,
            hasVariants = p.hasVariants,
            isArchived = p.isArchived,
            imageUrl = p.imageUrl,
            currentStockMilli = p.currentStockMilli,
            gstRateBps = p.gstRateBps,
            reorderLevelMilli = p.reorderLevelMilli,
            openingStockMilli = p.openingStockMilli
        )
    }
}

@Entity(
    tableName = "product_variants",
    indices = [Index("productId"), Index("sku")]
)
data class ProductVariantEntity(
    @PrimaryKey val id: String,
    val productId: String,
    val variantName: String,
    val sku: String,
    val barcode: String,
    val additionalPricePaise: Long,
    val currentStock: Int,
    val currentStockMilli: Long = 0L
) {
    fun toDomain(): ProductVariant = ProductVariant(
        id = id,
        productId = productId,
        variantName = variantName,
        sku = sku,
        barcode = barcode,
        additionalPricePaise = additionalPricePaise,
        currentStockMilli = if (currentStockMilli != 0L) currentStockMilli else currentStock.toLong() * 1000L
    )

    companion object {
        fun fromDomain(v: ProductVariant): ProductVariantEntity = ProductVariantEntity(
            id = v.id,
            productId = v.productId,
            variantName = v.variantName,
            sku = v.sku,
            barcode = v.barcode,
            additionalPricePaise = v.additionalPricePaise,
            currentStock = v.currentStock,
            currentStockMilli = v.currentStockMilli
        )
    }
}

@Entity(
    tableName = "stock_batches",
    indices = [Index("productId"), Index("expiryDate")]
)
data class StockBatchEntity(
    @PrimaryKey val id: String,
    val productId: String,
    val batchNumber: String,
    val mfgDate: String?,
    val expiryDate: String,
    val purchasePricePaise: Long,
    val initialQuantity: Int,
    val availableQuantity: Int,
    val locationId: String,
    val initialQuantityMilli: Long = 0L,
    val availableQuantityMilli: Long = 0L
) {
    fun toDomain(): StockBatch = StockBatch(
        id = id,
        productId = productId,
        batchNumber = batchNumber,
        mfgDate = mfgDate,
        expiryDate = expiryDate,
        purchasePricePaise = purchasePricePaise,
        initialQuantityMilli = if (initialQuantityMilli != 0L || availableQuantityMilli != 0L) initialQuantityMilli else initialQuantity.toLong() * 1000L,
        availableQuantityMilli = if (initialQuantityMilli != 0L || availableQuantityMilli != 0L) availableQuantityMilli else availableQuantity.toLong() * 1000L,
        locationId = locationId
    )

    companion object {
        fun fromDomain(b: StockBatch): StockBatchEntity = StockBatchEntity(
            id = b.id,
            productId = b.productId,
            batchNumber = b.batchNumber,
            mfgDate = b.mfgDate,
            expiryDate = b.expiryDate,
            purchasePricePaise = b.purchasePricePaise,
            initialQuantity = b.initialQuantity,
            availableQuantity = b.availableQuantity,
            locationId = b.locationId,
            initialQuantityMilli = b.initialQuantityMilli,
            availableQuantityMilli = b.availableQuantityMilli
        )
    }
}

@Entity(
    tableName = "stock_adjustments",
    indices = [Index("productId"), Index("timestamp")]
)
data class StockAdjustmentEntity(
    @PrimaryKey val id: String,
    val productId: String,
    val productName: String,
    val locationId: String,
    val quantityDelta: Int,
    val reason: String,
    val remarks: String,
    val timestamp: String,
    val adjustedBy: String,
    val quantityDeltaMilli: Long = 0L
) {
    fun toDomain(): StockAdjustment = StockAdjustment(
        id = id,
        productId = productId,
        productName = productName,
        locationId = locationId,
        quantityDeltaMilli = if (quantityDeltaMilli != 0L) quantityDeltaMilli else quantityDelta.toLong() * 1000L,
        reason = when (reason) {
            "DAMAGED" -> StockAdjustmentReason.DAMAGE
            else -> try { StockAdjustmentReason.valueOf(reason) } catch (_: Exception) { StockAdjustmentReason.COUNT_CORRECTION }
        },
        remarks = remarks,
        timestamp = timestamp,
        adjustedBy = adjustedBy
    )

    companion object {
        fun fromDomain(a: StockAdjustment): StockAdjustmentEntity = StockAdjustmentEntity(
            id = a.id,
            productId = a.productId,
            productName = a.productName,
            locationId = a.locationId,
            quantityDelta = a.quantityDelta,
            reason = a.reason.name,
            remarks = a.remarks,
            timestamp = a.timestamp,
            adjustedBy = a.adjustedBy,
            quantityDeltaMilli = a.quantityDeltaMilli
        )
    }
}

@Entity(
    tableName = "stock_movements",
    indices = [Index("productId"), Index("timestamp")]
)
data class StockMovementEntity(
    @PrimaryKey val id: String,
    val productId: String,
    val productName: String,
    val batchId: String?,
    val referenceTxnId: String?,
    val movementType: String,
    val quantityDelta: Int,
    val balanceAfter: Int,
    val locationId: String,
    val timestamp: String,
    val remarks: String,
    val quantityDeltaMilli: Long = 0L,
    val balanceAfterMilli: Long = 0L
) {
    fun toDomain(): StockMovement = StockMovement(
        id = id,
        productId = productId,
        productName = productName,
        batchId = batchId,
        referenceTxnId = referenceTxnId,
        movementType = movementType,
        quantityDeltaMilli = if (quantityDeltaMilli != 0L || balanceAfterMilli != 0L) quantityDeltaMilli else quantityDelta.toLong() * 1000L,
        balanceAfterMilli = if (quantityDeltaMilli != 0L || balanceAfterMilli != 0L) balanceAfterMilli else balanceAfter.toLong() * 1000L,
        locationId = locationId,
        timestamp = timestamp,
        remarks = remarks
    )

    companion object {
        fun fromDomain(m: StockMovement): StockMovementEntity = StockMovementEntity(
            id = m.id,
            productId = m.productId,
            productName = m.productName,
            batchId = m.batchId,
            referenceTxnId = m.referenceTxnId,
            movementType = m.movementType,
            quantityDelta = m.quantityDelta,
            balanceAfter = m.balanceAfter,
            locationId = m.locationId,
            timestamp = m.timestamp,
            remarks = m.remarks,
            quantityDeltaMilli = m.quantityDeltaMilli,
            balanceAfterMilli = m.balanceAfterMilli
        )
    }
}

@Entity(
    tableName = "inventory_locations"
)
data class InventoryLocationEntity(
    @PrimaryKey val id: String,
    val name: String,
    val code: String,
    val address: String,
    val isPrimary: Boolean
) {
    fun toDomain(): InventoryLocation = InventoryLocation(
        id = id,
        name = name,
        code = code,
        address = address,
        isPrimary = isPrimary
    )

    companion object {
        fun fromDomain(l: InventoryLocation): InventoryLocationEntity = InventoryLocationEntity(
            id = l.id,
            name = l.name,
            code = l.code,
            address = l.address,
            isPrimary = l.isPrimary
        )
    }
}

@Entity(tableName = "bank_accounts")
data class BankAccountEntity(
    @PrimaryKey val id: String,
    val accountName: String,
    val bankName: String,
    val accountNumber: String,
    val ifscCode: String,
    val upiId: String,
    val openingBalancePaise: Long,
    val isDefaultForQr: Boolean
) {
    fun toDomain(): BankAccount = BankAccount(
        id = id,
        accountName = accountName,
        bankName = bankName,
        accountNumber = accountNumber,
        ifscCode = ifscCode,
        upiId = upiId,
        openingBalancePaise = openingBalancePaise,
        isDefaultForQr = isDefaultForQr
    )

    companion object {
        fun fromDomain(b: BankAccount): BankAccountEntity = BankAccountEntity(
            id = b.id,
            accountName = b.accountName,
            bankName = b.bankName,
            accountNumber = b.accountNumber,
            ifscCode = b.ifscCode,
            upiId = b.upiId,
            openingBalancePaise = b.openingBalancePaise,
            isDefaultForQr = b.isDefaultForQr
        )
    }
}

@Entity(tableName = "business_profile")
data class BusinessProfileEntity(
    @PrimaryKey val id: String = "BIZ-001",
    val businessName: String,
    val entityType: String,
    val ownerName: String,
    val businessType: String,
    val registrationType: String,
    val homeStateCode: String,
    val filingFrequency: String,
    val plan: String,
    val openingCashBalancePaise: Long,
    val openingCapitalPaise: Long,
    val storeCode: String,
    val pan: String,
    val gstin: String,
    val udyamNumber: String,
    val address: String,
    val email: String,
    val phone: String,
    val isRegistered: Boolean
) {
    fun toDomain(): BusinessProfile = BusinessProfile(
        id = id,
        businessName = businessName,
        entityType = try { BusinessEntityType.valueOf(entityType) } catch (_: Exception) { BusinessEntityType.Proprietorship },
        ownerName = ownerName,
        businessType = businessType,
        registrationType = try {
            RegistrationType.valueOf(registrationType.trim().lowercase(java.util.Locale.ROOT))
        } catch (_: Exception) {
            try {
                RegistrationType.valueOf(registrationType.trim())
            } catch (_: Exception) {
                RegistrationType.regular
            }
        },
        homeStateCode = homeStateCode,
        filingFrequency = filingFrequency,
        plan = plan,
        openingCashBalancePaise = openingCashBalancePaise,
        openingCapitalPaise = openingCapitalPaise,
        storeCode = storeCode,
        pan = pan,
        gstin = gstin,
        udyamNumber = udyamNumber,
        address = address,
        email = email,
        phone = phone,
        isRegistered = isRegistered
    )

    companion object {
        fun fromDomain(p: BusinessProfile): BusinessProfileEntity = BusinessProfileEntity(
            id = p.id,
            businessName = p.businessName,
            entityType = p.entityType.name,
            ownerName = p.ownerName,
            businessType = p.businessType,
            registrationType = p.registrationType.name,
            homeStateCode = p.homeStateCode,
            filingFrequency = p.filingFrequency,
            plan = p.plan,
            openingCashBalancePaise = p.openingCashBalancePaise,
            openingCapitalPaise = p.openingCapitalPaise,
            storeCode = p.storeCode,
            pan = p.pan,
            gstin = p.gstin,
            udyamNumber = p.udyamNumber,
            address = p.address,
            email = p.email,
            phone = p.phone,
            isRegistered = p.isRegistered
        )
    }
}

@Entity(
    tableName = "audit_logs",
    indices = [
        Index("recordHash"),
        Index("timestamp"),
        Index("entityId")
    ]
)
data class AuditLogEntity(
    @PrimaryKey val id: String,
    val previousHash: String,
    val recordHash: String,
    val entityType: String,
    val entityId: String,
    val action: String,
    val details: String,
    val reason: String,
    val actor: String,
    val timestamp: String
) {
    fun toDomain(): AuditLog = AuditLog(
        id = id,
        previousHash = previousHash,
        recordHash = recordHash,
        entityType = entityType,
        entityId = entityId,
        action = action,
        details = details,
        reason = reason,
        actor = actor,
        timestamp = timestamp
    )

    companion object {
        fun fromDomain(a: AuditLog): AuditLogEntity = AuditLogEntity(
            id = a.id,
            previousHash = a.previousHash,
            recordHash = a.recordHash,
            entityType = a.entityType,
            entityId = a.entityId,
            action = a.action,
            details = a.details,
            reason = a.reason,
            actor = a.actor,
            timestamp = a.timestamp
        )
    }
}

// ==================== DAOs ====================

@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions ORDER BY date DESC, id DESC")
    fun getAllTransactions(): Flow<List<TransactionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(txn: TransactionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(txns: List<TransactionEntity>)

    @Query("UPDATE transactions SET isVoided = 1, voidReason = :reason, voidedAt = :voidedAt WHERE id = :id")
    suspend fun voidTransaction(id: String, reason: String, voidedAt: String)

    @Query("SELECT COUNT(*) FROM transactions")
    suspend fun getTransactionCount(): Int

    @Query("DELETE FROM transactions")
    suspend fun deleteAll()
}

@Dao
interface PartyDao {
    @Query("SELECT * FROM parties ORDER BY name ASC")
    fun getAllParties(): Flow<List<PartyEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertParty(party: PartyEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(parties: List<PartyEntity>)

    @Query("DELETE FROM parties WHERE id = :id")
    suspend fun deleteParty(id: String)

    @Query("SELECT COUNT(*) FROM parties")
    suspend fun getPartyCount(): Int

    @Query("DELETE FROM parties")
    suspend fun deleteAll()
}

@Dao
interface ProductDao {
    @Query("SELECT * FROM products WHERE isArchived = 0 ORDER BY name ASC")
    fun getAllProducts(): Flow<List<ProductEntity>>

    @Query("SELECT * FROM products ORDER BY name ASC")
    fun getAllProductsIncludeArchived(): Flow<List<ProductEntity>>

    @Query("SELECT * FROM products WHERE id = :id LIMIT 1")
    suspend fun getProductById(id: String): ProductEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProduct(prod: ProductEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(products: List<ProductEntity>)

    @Query("UPDATE products SET currentStock = :newStock, currentStockMilli = :newStockMilli WHERE id = :id")
    suspend fun updateStock(id: String, newStock: Int, newStockMilli: Long = newStock.toLong() * 1000L)

    @Query("UPDATE products SET isArchived = :isArchived WHERE id = :id")
    suspend fun setArchived(id: String, isArchived: Boolean)

    @Query("DELETE FROM products WHERE id = :id")
    suspend fun deleteProduct(id: String)

    @Query("SELECT COUNT(*) FROM products")
    suspend fun getProductCount(): Int

    @Query("DELETE FROM products")
    suspend fun deleteAll()
}

@Dao
interface ProductVariantDao {
    @Query("SELECT * FROM product_variants ORDER BY variantName ASC")
    fun getAllVariants(): Flow<List<ProductVariantEntity>>

    @Query("SELECT * FROM product_variants WHERE productId = :productId ORDER BY variantName ASC")
    fun getVariantsForProduct(productId: String): Flow<List<ProductVariantEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVariant(variant: ProductVariantEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(variants: List<ProductVariantEntity>)

    @Query("UPDATE product_variants SET currentStock = :newStock, currentStockMilli = :newStockMilli WHERE id = :id")
    suspend fun updateVariantStock(id: String, newStock: Int, newStockMilli: Long = newStock.toLong() * 1000L)

    @Query("DELETE FROM product_variants WHERE id = :id")
    suspend fun deleteVariant(id: String)

    @Query("DELETE FROM product_variants")
    suspend fun deleteAll()
}

@Dao
interface StockBatchDao {
    @Query("SELECT * FROM stock_batches WHERE productId = :productId AND (availableQuantityMilli > 0 OR availableQuantity > 0) ORDER BY CASE WHEN expiryDate = '' THEN '9999-12-31' ELSE expiryDate END ASC")
    fun getAvailableBatches(productId: String): Flow<List<StockBatchEntity>>

    @Query("SELECT * FROM stock_batches WHERE productId = :productId AND (availableQuantityMilli > 0 OR availableQuantity > 0) ORDER BY CASE WHEN expiryDate = '' THEN '9999-12-31' ELSE expiryDate END ASC")
    suspend fun getAvailableBatchesSync(productId: String): List<StockBatchEntity>

    @Query("SELECT * FROM stock_batches WHERE availableQuantityMilli > 0 OR availableQuantity > 0 ORDER BY CASE WHEN expiryDate = '' THEN '9999-12-31' ELSE expiryDate END ASC")
    fun getAllActiveBatches(): Flow<List<StockBatchEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBatch(batch: StockBatchEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(batches: List<StockBatchEntity>)

    @Query("UPDATE stock_batches SET availableQuantity = :qty, availableQuantityMilli = :qtyMilli WHERE id = :id")
    suspend fun updateAvailableQuantity(id: String, qty: Int, qtyMilli: Long = qty.toLong() * 1000L)

    @Query("DELETE FROM stock_batches")
    suspend fun deleteAll()
}

@Dao
interface StockAdjustmentDao {
    @Query("SELECT * FROM stock_adjustments ORDER BY timestamp DESC")
    fun getAllAdjustments(): Flow<List<StockAdjustmentEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAdjustment(adj: StockAdjustmentEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(adjustments: List<StockAdjustmentEntity>)

    @Query("DELETE FROM stock_adjustments")
    suspend fun deleteAll()
}

@Dao
interface StockMovementDao {
    @Query("SELECT * FROM stock_movements WHERE productId = :productId ORDER BY timestamp DESC")
    fun getMovementsForProduct(productId: String): Flow<List<StockMovementEntity>>

    @Query("SELECT * FROM stock_movements ORDER BY timestamp DESC LIMIT 500")
    fun getAllMovements(): Flow<List<StockMovementEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMovement(mov: StockMovementEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(movements: List<StockMovementEntity>)

    @Query("DELETE FROM stock_movements")
    suspend fun deleteAll()
}

@Dao
interface InventoryLocationDao {
    @Query("SELECT * FROM inventory_locations ORDER BY name ASC")
    fun getAllLocations(): Flow<List<InventoryLocationEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLocation(loc: InventoryLocationEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(locs: List<InventoryLocationEntity>)

    @Query("DELETE FROM inventory_locations")
    suspend fun deleteAll()
}

@Dao
interface BankAccountDao {
    @Query("SELECT * FROM bank_accounts ORDER BY id ASC")
    fun getAllBankAccounts(): Flow<List<BankAccountEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBankAccount(bank: BankAccountEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(banks: List<BankAccountEntity>)

    @Query("DELETE FROM bank_accounts WHERE id = :id")
    suspend fun deleteBankAccount(id: String)

    @Query("SELECT COUNT(*) FROM bank_accounts")
    suspend fun getBankAccountCount(): Int

    @Query("DELETE FROM bank_accounts")
    suspend fun deleteAll()
}

@Dao
interface BusinessProfileDao {
    @Query("SELECT * FROM business_profile WHERE id = 'BIZ-001' LIMIT 1")
    fun getProfile(): Flow<BusinessProfileEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateProfile(profile: BusinessProfileEntity)

    @Query("SELECT COUNT(*) FROM business_profile")
    suspend fun getProfileCount(): Int
}

@Dao
interface AuditLogDao {
    @Query("SELECT * FROM audit_logs ORDER BY timestamp ASC, rowid ASC")
    fun getAllAuditLogs(): Flow<List<AuditLogEntity>>

    @Query("SELECT * FROM audit_logs ORDER BY timestamp ASC, rowid ASC")
    suspend fun getAllAuditLogsSync(): List<AuditLogEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAuditLog(log: AuditLogEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(logs: List<AuditLogEntity>)

    @Query("SELECT COUNT(*) FROM audit_logs")
    suspend fun getAuditLogCount(): Int

    @Query("DELETE FROM audit_logs")
    suspend fun deleteAll()
}

// ==================== SUPER ADMIN MANAGED USERS ====================

@Entity(tableName = "managed_users")
data class ManagedUserEntity(
    @PrimaryKey val storeCode: String,
    val businessName: String,
    val ownerName: String,
    val phone: String,
    val email: String,
    val isActive: Boolean = true,
    val isGstUnlocked: Boolean = false,
    val isBankReconUnlocked: Boolean = false,
    val isInvoiceUnlocked: Boolean = true,
    val isInventoryUnlocked: Boolean = true,
    val isCmaUnlocked: Boolean = true,
    val isStaffUnlocked: Boolean = true,
    val isExportUnlocked: Boolean = true,
    val isManagerOverrideUnlocked: Boolean = true,
    val createdDate: String = "2026-10-01",
    val lastLoginTimestamp: Long = System.currentTimeMillis(),
    val lastSyncTimestamp: Long = System.currentTimeMillis()
)

@Dao
interface ManagedUserDao {
    @Query("SELECT * FROM managed_users ORDER BY lastLoginTimestamp DESC, storeCode ASC")
    fun getAllManagedUsers(): Flow<List<ManagedUserEntity>>

    @Query("SELECT * FROM managed_users WHERE storeCode = :storeCode LIMIT 1")
    suspend fun getManagedUser(storeCode: String): ManagedUserEntity?

    @Query("SELECT * FROM managed_users WHERE phone = :phone OR email = :email LIMIT 1")
    suspend fun findByPhoneOrEmail(phone: String, email: String): ManagedUserEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateUser(user: ManagedUserEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(users: List<ManagedUserEntity>)

    @Query("UPDATE managed_users SET isActive = :isActive WHERE storeCode = :storeCode")
    suspend fun updateUserStatus(storeCode: String, isActive: Boolean)

    @androidx.room.Transaction
    @Query("UPDATE managed_users SET isActive = :isActive WHERE storeCode IN (:storeCodes)")
    suspend fun updateUsersStatus(storeCodes: List<String>, isActive: Boolean)

    @Query("""
        UPDATE managed_users 
        SET isGstUnlocked = :isGst, 
            isBankReconUnlocked = :isRecon,
            isInvoiceUnlocked = :isInvoice,
            isInventoryUnlocked = :isInv,
            isCmaUnlocked = :isCma, 
            isStaffUnlocked = :isStaff,
            isExportUnlocked = :isExport,
            isManagerOverrideUnlocked = :isOverride,
            lastSyncTimestamp = :syncTime
        WHERE storeCode = :storeCode
    """)
    suspend fun updateUserBenefits(
        storeCode: String,
        isGst: Boolean,
        isRecon: Boolean,
        isInvoice: Boolean,
        isInv: Boolean,
        isCma: Boolean,
        isStaff: Boolean,
        isExport: Boolean = true,
        isOverride: Boolean = true,
        syncTime: Long = System.currentTimeMillis()
    )

    @Query("""
        UPDATE managed_users 
        SET isGstUnlocked = :isGst, 
            isBankReconUnlocked = :isRecon,
            isInvoiceUnlocked = :isInvoice,
            isInventoryUnlocked = :isInv,
            isCmaUnlocked = :isCma, 
            isStaffUnlocked = :isStaff,
            isExportUnlocked = :isExport,
            isManagerOverrideUnlocked = :isOverride,
            lastSyncTimestamp = :syncTime
        WHERE storeCode IN (:storeCodes)
    """)
    suspend fun bulkUpdateBenefits(
        storeCodes: List<String>,
        isGst: Boolean,
        isRecon: Boolean,
        isInvoice: Boolean,
        isInv: Boolean,
        isCma: Boolean,
        isStaff: Boolean,
        isExport: Boolean = true,
        isOverride: Boolean = true,
        syncTime: Long = System.currentTimeMillis()
    )

    @Query("UPDATE managed_users SET lastSyncTimestamp = :syncTime")
    suspend fun updateAllSyncTimestamps(syncTime: Long)

    @Query("DELETE FROM managed_users WHERE storeCode = :storeCode")
    suspend fun deleteUser(storeCode: String)

    @Query("SELECT COUNT(*) FROM managed_users")
    suspend fun getUserCount(): Int
}

// ==================== INVOICE COUNTER ENTITY & DAO ====================

@Entity(
    tableName = "invoice_counters",
    primaryKeys = ["series", "fy"]
)
data class InvoiceCounterEntity(
    val series: String,
    val fy: String,
    val lastNumber: Long
)

@Dao
interface InvoiceCounterDao {
    @Query("SELECT * FROM invoice_counters WHERE series = :series AND fy = :fy LIMIT 1")
    suspend fun getCounter(series: String, fy: String): InvoiceCounterEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setCounter(counter: InvoiceCounterEntity)

    @androidx.room.Transaction
    suspend fun next(series: String, fy: String): String {
        val existing = getCounter(series, fy)
        val nextNum = (existing?.lastNumber ?: 0L) + 1L
        setCounter(InvoiceCounterEntity(series = series, fy = fy, lastNumber = nextNum))
        // Compact FY format (e.g. 2026-27 -> 26-27) ensures BILL/26-27/0001 (15 chars) never truncates
        val compactFy = if (fy.contains("-") && fy.length >= 7) {
            "${fy.substring(2, 4)}-${fy.takeLast(2)}"
        } else {
            fy
        }
        val numStr = String.format(java.util.Locale.ROOT, "%04d", nextNum)
        val candidate = "$series/$compactFy/$numStr"
        return if (candidate.length <= 16) candidate else "$series/$nextNum".take(16)
    }
}

// ==================== BANK RECONCILIATION ENTITY & DAO ====================

@Entity(
    tableName = "bank_reconciliations",
    indices = [Index("bankAccountId"), Index("date")]
)
data class BankReconEntity(
    @PrimaryKey val id: String,
    val bankAccountId: String,
    val date: String,
    val description: String,
    val amountPaise: Long,
    val isCredit: Boolean,
    val isReconciled: Boolean = false,
    val matchedTxnId: String? = null,
    val referenceNo: String = "",
    val matchConfidence: Int = 0,
    val matchReason: String = "",
    val discrepancyNote: String = "",
    val isUserConfirmed: Boolean = false,
    val reconciledAt: String? = null
) {
    fun toDomain(): BankReconItem = BankReconItem(
        id = id,
        bankAccountId = bankAccountId,
        date = date,
        description = description,
        amountPaise = amountPaise,
        isCredit = isCredit,
        isReconciled = isReconciled,
        matchedTxnId = matchedTxnId,
        referenceNo = referenceNo,
        matchConfidence = matchConfidence,
        matchReason = matchReason,
        discrepancyNote = discrepancyNote,
        isUserConfirmed = isUserConfirmed,
        reconciledAt = reconciledAt
    )

    companion object {
        fun fromDomain(r: BankReconItem): BankReconEntity = BankReconEntity(
            id = r.id,
            bankAccountId = r.bankAccountId,
            date = r.date,
            description = r.description,
            amountPaise = r.amountPaise,
            isCredit = r.isCredit,
            isReconciled = r.isReconciled,
            matchedTxnId = r.matchedTxnId,
            referenceNo = r.referenceNo,
            matchConfidence = r.matchConfidence,
            matchReason = r.matchReason,
            discrepancyNote = r.discrepancyNote,
            isUserConfirmed = r.isUserConfirmed,
            reconciledAt = r.reconciledAt
        )
    }
}

@Dao
interface BankReconDao {
    @Query("SELECT * FROM bank_reconciliations ORDER BY date DESC, id DESC")
    fun getAllReconItems(): Flow<List<BankReconEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReconItem(item: BankReconEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<BankReconEntity>)

    @Query("DELETE FROM bank_reconciliations WHERE id = :id")
    suspend fun deleteReconItem(id: String)

    @Query("DELETE FROM bank_reconciliations WHERE bankAccountId = :bankId")
    suspend fun clearBankItems(bankId: String)

    @Query("DELETE FROM bank_reconciliations")
    suspend fun deleteAll()
}

// ==================== UPI QR CODE ENTITY & DAO ====================

@Entity(tableName = "upi_qr_codes")
data class UpiQrCodeEntity(
    @PrimaryKey val id: String,
    val label: String,
    val upiId: String,
    val linkedBankId: String,
    val isDefault: Boolean = false,
    val isEnabled: Boolean = true,
    val showOnInvoices: Boolean = true,
    val qrImageUri: String? = null
) {
    fun toDomain(): UpiQrCode = UpiQrCode(
        id = id,
        label = label,
        upiId = upiId,
        linkedBankId = linkedBankId,
        isDefault = isDefault,
        isEnabled = isEnabled,
        showOnInvoices = showOnInvoices,
        qrImageUri = qrImageUri
    )

    companion object {
        fun fromDomain(q: UpiQrCode): UpiQrCodeEntity = UpiQrCodeEntity(
            id = q.id,
            label = q.label,
            upiId = q.upiId,
            linkedBankId = q.linkedBankId,
            isDefault = q.isDefault,
            isEnabled = q.isEnabled,
            showOnInvoices = q.showOnInvoices,
            qrImageUri = q.qrImageUri
        )
    }
}

@Dao
interface UpiQrDao {
    @Query("SELECT * FROM upi_qr_codes ORDER BY isDefault DESC, label ASC")
    fun getAllQrCodes(): Flow<List<UpiQrCodeEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQrCode(qr: UpiQrCodeEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(qrs: List<UpiQrCodeEntity>)

    @Query("DELETE FROM upi_qr_codes WHERE id = :id")
    suspend fun deleteQrCode(id: String)

    @Query("DELETE FROM upi_qr_codes")
    suspend fun deleteAll()
}

// ==================== BRANCH ENTITY & DAO ====================

@Entity(tableName = "branches")
data class BranchEntity(
    @PrimaryKey val id: String,
    val name: String,
    val code: String,
    val isMain: Boolean,
    val address: String
) {
    fun toDomain(): Branch = Branch(
        id = id,
        name = name,
        code = code,
        isMain = isMain,
        address = address
    )

    companion object {
        fun fromDomain(b: Branch): BranchEntity = BranchEntity(
            id = b.id,
            name = b.name,
            code = b.code,
            isMain = b.isMain,
            address = b.address
        )
    }
}

@Dao
interface BranchDao {
    @Query("SELECT * FROM branches ORDER BY isMain DESC, name ASC")
    fun getAllBranches(): Flow<List<BranchEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBranch(b: BranchEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(branches: List<BranchEntity>)

    @Query("DELETE FROM branches WHERE id = :id")
    suspend fun deleteBranch(id: String)

    @Query("DELETE FROM branches")
    suspend fun deleteAll()
}

// ==================== DATABASE ====================

val MIGRATION_1_6 = object : androidx.room.migration.Migration(1, 6) {
    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        MIGRATION_5_6.migrate(db)
    }
}

val MIGRATION_2_6 = object : androidx.room.migration.Migration(2, 6) {
    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        MIGRATION_5_6.migrate(db)
    }
}

val MIGRATION_3_6 = object : androidx.room.migration.Migration(3, 6) {
    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        MIGRATION_5_6.migrate(db)
    }
}

val MIGRATION_4_6 = object : androidx.room.migration.Migration(4, 6) {
    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        MIGRATION_5_6.migrate(db)
    }
}

val MIGRATION_5_6 = object : androidx.room.migration.Migration(5, 6) {
    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `invoice_counters` (
                `series` TEXT NOT NULL,
                `fy` TEXT NOT NULL,
                `lastNumber` INTEGER NOT NULL,
                PRIMARY KEY(`series`, `fy`)
            )
        """.trimIndent())
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `bank_reconciliations` (
                `id` TEXT NOT NULL,
                `bankAccountId` TEXT NOT NULL,
                `date` TEXT NOT NULL,
                `description` TEXT NOT NULL,
                `amountPaise` INTEGER NOT NULL,
                `isCredit` INTEGER NOT NULL,
                `isReconciled` INTEGER NOT NULL,
                `matchedTxnId` TEXT,
                `referenceNo` TEXT NOT NULL,
                `matchConfidence` INTEGER NOT NULL,
                `matchReason` TEXT NOT NULL,
                `discrepancyNote` TEXT NOT NULL,
                `isUserConfirmed` INTEGER NOT NULL,
                `reconciledAt` TEXT,
                PRIMARY KEY(`id`)
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_bank_reconciliations_bankAccountId` ON `bank_reconciliations` (`bankAccountId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_bank_reconciliations_date` ON `bank_reconciliations` (`date`)")
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `upi_qr_codes` (
                `id` TEXT NOT NULL,
                `label` TEXT NOT NULL,
                `upiId` TEXT NOT NULL,
                `linkedBankId` TEXT NOT NULL,
                `isDefault` INTEGER NOT NULL,
                `isEnabled` INTEGER NOT NULL,
                `showOnInvoices` INTEGER NOT NULL,
                `qrImageUri` TEXT,
                PRIMARY KEY(`id`)
            )
        """.trimIndent())
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `branches` (
                `id` TEXT NOT NULL,
                `name` TEXT NOT NULL,
                `code` TEXT NOT NULL,
                `isMain` INTEGER NOT NULL,
                `address` TEXT NOT NULL,
                PRIMARY KEY(`id`)
            )
        """.trimIndent())
        // Add currentStockMilli column to products table if not exists
        try {
            db.execSQL("ALTER TABLE `products` ADD COLUMN `currentStockMilli` INTEGER NOT NULL DEFAULT 0")
        } catch (_: Exception) {
            // Ignored if column already exists
        }
    }
}

val MIGRATION_6_7 = object : androidx.room.migration.Migration(6, 7) {
    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        fun addColIfMissing(sql: String) {
            try { db.execSQL(sql) } catch (_: Exception) {}
        }
        addColIfMissing("ALTER TABLE `product_variants` ADD COLUMN `currentStockMilli` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("UPDATE `product_variants` SET `currentStockMilli` = `currentStock` * 1000 WHERE `currentStockMilli` = 0 AND `currentStock` != 0")

        addColIfMissing("ALTER TABLE `stock_batches` ADD COLUMN `initialQuantityMilli` INTEGER NOT NULL DEFAULT 0")
        addColIfMissing("ALTER TABLE `stock_batches` ADD COLUMN `availableQuantityMilli` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("UPDATE `stock_batches` SET `initialQuantityMilli` = `initialQuantity` * 1000, `availableQuantityMilli` = `availableQuantity` * 1000 WHERE `initialQuantityMilli` = 0 AND `availableQuantityMilli` = 0")

        addColIfMissing("ALTER TABLE `stock_adjustments` ADD COLUMN `quantityDeltaMilli` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("UPDATE `stock_adjustments` SET `quantityDeltaMilli` = `quantityDelta` * 1000 WHERE `quantityDeltaMilli` = 0 AND `quantityDelta` != 0")

        addColIfMissing("ALTER TABLE `stock_movements` ADD COLUMN `quantityDeltaMilli` INTEGER NOT NULL DEFAULT 0")
        addColIfMissing("ALTER TABLE `stock_movements` ADD COLUMN `balanceAfterMilli` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("UPDATE `stock_movements` SET `quantityDeltaMilli` = `quantityDelta` * 1000, `balanceAfterMilli` = `balanceAfter` * 1000 WHERE `quantityDeltaMilli` = 0 AND `balanceAfterMilli` = 0")

        db.execSQL("UPDATE `products` SET `currentStockMilli` = `currentStock` * 1000 WHERE `currentStockMilli` = 0 AND `currentStock` != 0")
    }
}

val MIGRATION_1_7 = object : androidx.room.migration.Migration(1, 7) {
    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        MIGRATION_5_6.migrate(db)
        MIGRATION_6_7.migrate(db)
    }
}
val MIGRATION_2_7 = object : androidx.room.migration.Migration(2, 7) {
    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        MIGRATION_5_6.migrate(db)
        MIGRATION_6_7.migrate(db)
    }
}
val MIGRATION_3_7 = object : androidx.room.migration.Migration(3, 7) {
    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        MIGRATION_5_6.migrate(db)
        MIGRATION_6_7.migrate(db)
    }
}
val MIGRATION_4_7 = object : androidx.room.migration.Migration(4, 7) {
    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        MIGRATION_5_6.migrate(db)
        MIGRATION_6_7.migrate(db)
    }
}
val MIGRATION_5_7 = object : androidx.room.migration.Migration(5, 7) {
    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        MIGRATION_5_6.migrate(db)
        MIGRATION_6_7.migrate(db)
    }
}

val MIGRATION_7_8 = object : androidx.room.migration.Migration(7, 8) {
    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        fun addColIfMissing(sql: String) {
            try { db.execSQL(sql) } catch (_: Exception) {}
        }
        addColIfMissing("ALTER TABLE `products` ADD COLUMN `gstRateBps` INTEGER NOT NULL DEFAULT 0")
        addColIfMissing("ALTER TABLE `products` ADD COLUMN `reorderLevelMilli` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("UPDATE `products` SET `gstRateBps` = `gstRate` * 100 WHERE `gstRateBps` = 0 AND `gstRate` != 0")
        db.execSQL("UPDATE `products` SET `reorderLevelMilli` = `reorderLevel` * 1000 WHERE `reorderLevelMilli` = 0 AND `reorderLevel` != 0")
    }
}

val MIGRATION_1_8 = object : androidx.room.migration.Migration(1, 8) {
    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        MIGRATION_5_6.migrate(db)
        MIGRATION_6_7.migrate(db)
        MIGRATION_7_8.migrate(db)
    }
}
val MIGRATION_2_8 = object : androidx.room.migration.Migration(2, 8) {
    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        MIGRATION_5_6.migrate(db)
        MIGRATION_6_7.migrate(db)
        MIGRATION_7_8.migrate(db)
    }
}
val MIGRATION_3_8 = object : androidx.room.migration.Migration(3, 8) {
    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        MIGRATION_5_6.migrate(db)
        MIGRATION_6_7.migrate(db)
        MIGRATION_7_8.migrate(db)
    }
}
val MIGRATION_4_8 = object : androidx.room.migration.Migration(4, 8) {
    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        MIGRATION_5_6.migrate(db)
        MIGRATION_6_7.migrate(db)
        MIGRATION_7_8.migrate(db)
    }
}
val MIGRATION_5_8 = object : androidx.room.migration.Migration(5, 8) {
    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        MIGRATION_5_6.migrate(db)
        MIGRATION_6_7.migrate(db)
        MIGRATION_7_8.migrate(db)
    }
}
val MIGRATION_6_8 = object : androidx.room.migration.Migration(6, 8) {
    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        MIGRATION_6_7.migrate(db)
        MIGRATION_7_8.migrate(db)
    }
}

val MIGRATION_8_9 = object : androidx.room.migration.Migration(8, 9) {
    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        try {
            db.execSQL("ALTER TABLE `products` ADD COLUMN `openingStockMilli` INTEGER NOT NULL DEFAULT 0")
        } catch (_: Exception) {
        }
    }
}

val MIGRATION_1_9 = object : androidx.room.migration.Migration(1, 9) {
    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        MIGRATION_5_6.migrate(db)
        MIGRATION_6_7.migrate(db)
        MIGRATION_7_8.migrate(db)
        MIGRATION_8_9.migrate(db)
    }
}
val MIGRATION_2_9 = object : androidx.room.migration.Migration(2, 9) {
    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        MIGRATION_5_6.migrate(db)
        MIGRATION_6_7.migrate(db)
        MIGRATION_7_8.migrate(db)
        MIGRATION_8_9.migrate(db)
    }
}
val MIGRATION_3_9 = object : androidx.room.migration.Migration(3, 9) {
    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        MIGRATION_5_6.migrate(db)
        MIGRATION_6_7.migrate(db)
        MIGRATION_7_8.migrate(db)
        MIGRATION_8_9.migrate(db)
    }
}
val MIGRATION_4_9 = object : androidx.room.migration.Migration(4, 9) {
    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        MIGRATION_5_6.migrate(db)
        MIGRATION_6_7.migrate(db)
        MIGRATION_7_8.migrate(db)
        MIGRATION_8_9.migrate(db)
    }
}
val MIGRATION_5_9 = object : androidx.room.migration.Migration(5, 9) {
    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        MIGRATION_5_6.migrate(db)
        MIGRATION_6_7.migrate(db)
        MIGRATION_7_8.migrate(db)
        MIGRATION_8_9.migrate(db)
    }
}
val MIGRATION_6_9 = object : androidx.room.migration.Migration(6, 9) {
    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        MIGRATION_6_7.migrate(db)
        MIGRATION_7_8.migrate(db)
        MIGRATION_8_9.migrate(db)
    }
}
val MIGRATION_7_9 = object : androidx.room.migration.Migration(7, 9) {
    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        MIGRATION_7_8.migrate(db)
        MIGRATION_8_9.migrate(db)
    }
}

@Database(
    entities = [
        TransactionEntity::class,
        PartyEntity::class,
        ProductEntity::class,
        ProductVariantEntity::class,
        StockBatchEntity::class,
        StockAdjustmentEntity::class,
        StockMovementEntity::class,
        InventoryLocationEntity::class,
        BankAccountEntity::class,
        BusinessProfileEntity::class,
        AuditLogEntity::class,
        ManagedUserEntity::class,
        InvoiceCounterEntity::class,
        BankReconEntity::class,
        UpiQrCodeEntity::class,
        BranchEntity::class
    ],
    version = 9,
    exportSchema = true
)
@TypeConverters(PabaruTypeConverters::class)
abstract class PabaruDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao
    abstract fun partyDao(): PartyDao
    abstract fun productDao(): ProductDao
    abstract fun productVariantDao(): ProductVariantDao
    abstract fun stockBatchDao(): StockBatchDao
    abstract fun stockAdjustmentDao(): StockAdjustmentDao
    abstract fun stockMovementDao(): StockMovementDao
    abstract fun inventoryLocationDao(): InventoryLocationDao
    abstract fun bankAccountDao(): BankAccountDao
    abstract fun businessProfileDao(): BusinessProfileDao
    abstract fun auditLogDao(): AuditLogDao
    abstract fun managedUserDao(): ManagedUserDao
    abstract fun invoiceCounterDao(): InvoiceCounterDao
    abstract fun bankReconDao(): BankReconDao
    abstract fun upiQrDao(): UpiQrDao
    abstract fun branchDao(): BranchDao

    companion object {
        @Volatile
        private var INSTANCE: PabaruDatabase? = null

        fun getDatabase(context: Context): PabaruDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    PabaruDatabase::class.java,
                    "pabaru_billing.db"
                )
                    .addMigrations(
                        MIGRATION_1_6, MIGRATION_2_6, MIGRATION_3_6, MIGRATION_4_6, MIGRATION_5_6,
                        MIGRATION_6_7, MIGRATION_1_7, MIGRATION_2_7, MIGRATION_3_7, MIGRATION_4_7, MIGRATION_5_7,
                        MIGRATION_7_8, MIGRATION_1_8, MIGRATION_2_8, MIGRATION_3_8, MIGRATION_4_8, MIGRATION_5_8, MIGRATION_6_8,
                        MIGRATION_8_9, MIGRATION_1_9, MIGRATION_2_9, MIGRATION_3_9, MIGRATION_4_9, MIGRATION_5_9, MIGRATION_6_9, MIGRATION_7_9
                    )
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
