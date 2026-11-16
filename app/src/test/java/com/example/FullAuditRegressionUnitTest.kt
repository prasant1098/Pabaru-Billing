package com.example

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.example.data.AuditLogEntity
import com.example.data.MIGRATION_8_9
import com.example.data.PabaruDatabase
import com.example.data.ProductEntity
import com.example.data.ProductVariantEntity
import com.example.engine.BankReconEngine
import com.example.engine.CmaAndGstEngine
import com.example.engine.CmaProjectEngine
import com.example.engine.CryptoEngine
import com.example.engine.FinancialEngine
import com.example.model.*
import com.example.viewmodel.PabaruUiState
import com.example.viewmodel.PabaruViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class FullAuditRegressionUnitTest {

    @Test
    fun testChequeIncludedInBankBalanceAndBalanceSheetParity() {
        val bank = BankAccount("BNK-CHQ", "ICICI Current", "ICICI Bank", "9999", "ICIC0000001", "shop@icici", 100000L, true)
        val cust = Party("PTY-C1", "Buyer A", "9800000001", "Mumbai", "", "", "27", 0L, BalanceType.Settled)
        val supp = Party("PTY-S1", "Vendor A", "9800000002", "Mumbai", "", "", "27", 0L, BalanceType.Settled, isSupplier = true)

        val saleLine = InvoiceLineItem("P1", "Goods", "1006", 1000L, "PCS", 50000L, 1800, 50000L, 4500L, 4500L, 0L, 59000L)
        val chqSale = Transaction(
            id = "TXN-CHQ-IN",
            branchId = "BR-MAIN",
            date = "2026-10-05",
            partyId = cust.id,
            amountPaise = 59000L,
            type = TransactionType.Sale,
            paymentMode = PaymentMode.Cheque,
            bankAccountId = bank.id,
            items = listOf(saleLine)
        )
        val chqPayout = Transaction(
            id = "TXN-CHQ-OUT",
            branchId = "BR-MAIN",
            date = "2026-10-06",
            partyId = supp.id,
            amountPaise = 20000L,
            type = TransactionType.PaymentOut,
            paymentMode = PaymentMode.Cheque,
            bankAccountId = bank.id
        )

        val state = PabaruUiState(
            profile = BusinessProfile(openingCashBalancePaise = 50000L, openingCapitalPaise = 150000L, homeStateCode = "27", isRegistered = true),
            parties = listOf(cust, supp),
            bankAccounts = listOf(bank),
            transactions = listOf(chqSale, chqPayout)
        )

        val fin = FinancialEngine.calculateFyFinancials(state, "All Time")
        // Opening bank 100000 + Cheque sale 59000 - Cheque payout 20000 = 139000
        assertEquals(139000L, fin.bankTotalPaise)
        assertEquals(
            "Balance Sheet assets and liabilities+equity must stay strictly equal with Cheque inflows/outflows",
            fin.totalAssetsPaise,
            fin.totalLiabilitiesAndEquityPaise
        )

        // ViewModel cheque invoice assigns bankAccountId and FY-scoped <=16-char invoice number
        val vm = PabaruViewModel()
        vm.addBankAccount(bank)
        vm.addProduct(ProductItem("P1", "Goods", "111", "SKU1", hsnCode = "1006", gstRateBps = 1800, unit = "PCS", purchasePricePaise = 30000L, salePricePaise = 50000L, currentStockMilli = 10000L, reorderLevelMilli = 1000L))
        val created = vm.recordItemizedInvoice(
            partyId = null,
            paymentMode = PaymentMode.Cheque,
            type = TransactionType.Sale,
            lineItems = listOf(saleLine),
            remarks = "Cheque sale"
        )
        assertEquals(bank.id, created.bankAccountId)
        assertTrue("Invoice number '${created.invoiceNumber}' must be <= 16 chars", created.invoiceNumber.length <= 16)
        assertTrue("Invoice number must be FY-scoped (INV/YY-YY/0001)", created.invoiceNumber.matches(Regex("^INV/\\d{2}-\\d{2}/\\d{4}$")))
    }

    @Test
    fun testGstr1Table8ZeroRatedNoDoubleCountUnitemizedZeroGstAndDebitNoteSplit() {
        val profile = BusinessProfile(businessName = "Test Co", gstin = "27AAACS1234M1Z2", homeStateCode = "27")
        val custB2b = Party("PTY-CUST-B2B", "Registered Customer", "9800000001", "Mumbai", "27AABCU9603R1ZM", "AABCU9603R", "27", 0L, BalanceType.Receivable, isSupplier = false)
        val custB2c = Party("PTY-CUST-B2C", "Walk-in Customer", "9800000002", "Mumbai", "", "", "27", 0L, BalanceType.Receivable, isSupplier = false)
        val suppB2b = Party("PTY-SUPP-B2B", "Registered Vendor", "9800000003", "Mumbai", "27AAACA1234A1Z5", "AAACA1234A", "27", 0L, BalanceType.Payable, isSupplier = true)

        // Zero-rated 0% B2C sale -> must count once in nilExemptPaise (not doubled by both Sale and NonGstBill)
        val zeroSaleLine = InvoiceLineItem("P0", "Exempt Rice", "1006", 1000L, "KG", 10000L, 0, 10000L, 0L, 0L, 0L, 10000L)
        val stdSaleLine = InvoiceLineItem("P1", "Taxable Box", "4819", 1000L, "PCS", 100000L, 1800, 100000L, 9000L, 9000L, 0L, 118000L)
        val custDebitLine = InvoiceLineItem("P1", "Price Adj", "4819", 1000L, "PCS", 20000L, 1800, 20000L, 1800L, 1800L, 0L, 23600L)
        val purchaseLine = InvoiceLineItem("P1", "Stock Buy", "4819", 1000L, "PCS", 50000L, 1800, 50000L, 4500L, 4500L, 0L, 59000L)
        val suppDebitLine = InvoiceLineItem("P1", "Pur Return", "4819", 1000L, "PCS", 10000L, 1800, 10000L, 900L, 900L, 0L, 11800L)

        val txns = listOf(
            // 1. Zero-rated itemized B2C sale (10,000 paise)
            Transaction("S-ZERO", "BR-MAIN", "2026-10-01", custB2c.id, 10000L, TransactionType.Sale, paymentMode = PaymentMode.Cash, invoiceNumber = "INV-01", items = listOf(zeroSaleLine)),
            // 2. Unitemized B2B Sale -> must NOT fabricate 18% GST
            Transaction("S-UNITEM", "BR-MAIN", "2026-10-02", custB2b.id, 40000L, TransactionType.Sale, paymentMode = PaymentMode.Cash, invoiceNumber = "INV-02", items = emptyList()),
            // 3. Regular itemized B2B Sale (100,000 + 18,000 GST)
            Transaction("S-TAX", "BR-MAIN", "2026-10-03", custB2b.id, 118000L, TransactionType.Sale, paymentMode = PaymentMode.Bank, invoiceNumber = "INV-03", items = listOf(stdSaleLine)),
            // 4. Customer DebitNote (+20,000 taxable, +3,600 GST -> CDNR & GSTR-3B outward)
            Transaction("DN-CUST", "BR-MAIN", "2026-10-04", custB2b.id, 23600L, TransactionType.DebitNote, paymentMode = PaymentMode.Credit, invoiceNumber = "DN-C1", items = listOf(custDebitLine)),
            // 5. Purchase (+9,000 ITC)
            Transaction("PUR-1", "BR-MAIN", "2026-10-05", suppB2b.id, 59000L, TransactionType.Purchase, paymentMode = PaymentMode.Credit, invoiceNumber = "PUR-01", items = listOf(purchaseLine)),
            // 6. Supplier DebitNote (Purchase return -1,800 ITC reversal -> must NOT appear in GSTR-1 CDNR)
            Transaction("DN-SUPP", "BR-MAIN", "2026-10-06", suppB2b.id, 11800L, TransactionType.DebitNote, paymentMode = PaymentMode.Credit, invoiceNumber = "DN-S1", items = listOf(suppDebitLine))
        )
        val parties = listOf(custB2b, custB2c, suppB2b)

        val gstr1 = CmaAndGstEngine.computeStatutoryGstr1(txns, parties, profile, CmaAndGstEngine.GstPeriodFilter.AllTime)
        assertEquals("Table 8 nilExemptPaise must count S-ZERO + S-UNITEM once without double-counting", 10000L, gstr1.nilExemptPaise)
        val unitemizedB2b = gstr1.b2bInvoices.first { it.invoiceNumber == "INV-02" }
        assertEquals("Unitemized invoice must not fabricate CGST", 0L, unitemizedB2b.cgstPaise)
        assertEquals("Unitemized invoice must not fabricate SGST", 0L, unitemizedB2b.sgstPaise)
        assertEquals("Unitemized invoice must not fabricate IGST", 0L, unitemizedB2b.igstPaise)
        assertEquals("Only customer DebitNote should appear in GSTR-1 CDNR", 1, gstr1.cdnrNotes.size)
        assertEquals("DN-C1", gstr1.cdnrNotes.first().noteNumber)

        val gstr3b = CmaAndGstEngine.computeStatutoryGstr3b(txns, parties, profile, gstr1, CmaAndGstEngine.GstPeriodFilter.AllTime)
        // Outward taxable = 10000 + 40000 + 100000 + 20000 = 170000; outward CGST = 9000 + 1800 = 10800
        assertEquals(170000L, gstr3b.outwardTaxablePaise)
        assertEquals(10800L, gstr3b.outwardCgstPaise)
        assertEquals(10800L, gstr3b.outwardSgstPaise)
        // Net ITC = Purchase (4500 CGST + 4500 SGST) - Supplier DebitNote reversal (900 CGST + 900 SGST) = 3600 each
        assertEquals(3600L, gstr3b.eligibleItcCgstPaise)
        assertEquals(3600L, gstr3b.eligibleItcSgstPaise)
        assertEquals(7200L, gstr3b.totalEligibleItcPaise)
    }

    @Test
    fun testBankReconCsvSingleColumnDrCrOfxDateValidationAndSymmetricGst() {
        val csv = """
            Date,Description,Amount,Type,Reference
            01-10-2026,NEFT Vendor Payout,1500.00,DR,REF-1
            02-10-2026,UPI Receipt,2500.00,CR,REF-2
            03-10-2026,Bank Fee,118.00 DR,,REF-3
            04-10-2026,Cashback,50.00 CR,,REF-4
        """.trimIndent()
        val csvRes = BankReconEngine.parseStatementText(csv, "BNK-01", "HDFC")
        assertEquals(4, csvRes.items.size)
        assertFalse("DR type must parse as debit", csvRes.items[0].isCredit)
        assertEquals(150000L, csvRes.items[0].amountPaise)
        assertTrue("CR type must parse as credit", csvRes.items[1].isCredit)
        assertEquals(250000L, csvRes.items[1].amountPaise)
        assertFalse("Amount suffix DR must parse as debit", csvRes.items[2].isCredit)
        assertEquals(11800L, csvRes.items[2].amountPaise)
        assertTrue("Amount suffix CR must parse as credit", csvRes.items[3].isCredit)
        assertEquals(5000L, csvRes.items[3].amountPaise)

        // OFX invalid calendar date 20260230 must not survive as non-empty date
        val ofx = """
            <OFX><BANKMSGSRSV1><STMTTRNRS><STMTRS><BANKTRANLIST>
            <STMTTRN><TRNTYPE>DEBIT<DTPOSTED>20260230<TRNAMT>-100.00<FITID>BAD-DT<NAME>BAD DATE FEE</STMTTRN>
            <STMTTRN><TRNTYPE>DEBIT<DTPOSTED>20261002<TRNAMT>-100.00<FITID>OK-DT<NAME>BANK CHARGE</STMTTRN>
            </BANKTRANLIST></STMTRS></STMTTRNRS></BANKMSGSRSV1></OFX>
        """.trimIndent()
        val ofxRes = BankReconEngine.parseStatementText(ofx, "BNK-01", "SBI")
        assertEquals("", ofxRes.items.first { it.referenceNo == "BAD-DT" }.date)
        assertEquals("2026-10-02", ofxRes.items.first { it.referenceNo == "OK-DT" }.date)

        // Bank charges 18% GST split preserves CGST == SGST and exact total parity even on odd paise (e.g. ₹29.51 = 2951 paise)
        val oddStmt = BankReconItem("S-ODD", "BNK-01", "2026-10-02", "Odd Bank Charge", 2951L, isCredit = false)
        val chargeTxn = BankReconEngine.createBalancingTransaction(oddStmt, BankReconEngine.DiscrepancyResolutionType.BANK_CHARGES)
        val line = chargeTxn.items.single()
        assertEquals("CGST must equal SGST", line.cgstPaise, line.sgstPaise)
        assertEquals("Taxable + CGST + SGST must equal exact total", 2951L, line.taxableAmountPaise + line.cgstPaise + line.sgstPaise)
    }

    @Test
    fun testCmaProjectMoratoriumClampAndTwoDecimalLakhs() {
        val schedule = CmaProjectEngine.calculateTermLoanSchedule(
            principalPaise = 10000000L, // ₹1,00,000
            annualRatePercent = 10.0,
            tenorMonths = 12,
            moratoriumMonths = 12 // >= tenorMonths -> must clamp safely so amortization still occurs
        )
        assertTrue(schedule.moratoriumMonths < schedule.tenorMonths)
        assertTrue("Effective repayment EMI must be > 0 when moratorium clamped", schedule.monthlyEmiPaise > 0L)
        assertEquals(12, schedule.monthlySchedule.size)
        assertTrue(schedule.monthlySchedule.any { it.principalPaise > 0L })

        val dossier = CmaProjectEngine.createInitialDossier(2025, emptyList(), emptyList(), emptyList(), 5000000L)
        val feedback = CmaProjectEngine.evaluateDossier(dossier)
        val mpbfItem = feedback.first { it.title.contains("MPBF") }
        // Must contain 2-decimal Lakh format (e.g., "0.00 L" or "12.34 L"), not 1-decimal
        assertTrue(
            "MPBF currentValue '${mpbfItem.currentValue}' must format Lakhs to 2 decimals",
            Regex("""₹\s*-?\d+\.\d{2}\s*L""").containsMatchIn(mpbfItem.currentValue)
        )
    }

    @Test
    fun testRoomMigration8To9PreservesProductsAndAuditDeterminismAndVariants() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbName = "migration_v8_to_v9_${System.nanoTime()}.db"
        val dbFile = context.getDatabasePath(dbName)
        dbFile.parentFile?.mkdirs()

        val callback = object : SupportSQLiteOpenHelper.Callback(8) {
            override fun onCreate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `products` (
                        `id` TEXT NOT NULL PRIMARY KEY,
                        `name` TEXT NOT NULL,
                        `barcode` TEXT NOT NULL,
                        `sku` TEXT NOT NULL,
                        `category` TEXT NOT NULL,
                        `hsnCode` TEXT NOT NULL,
                        `gstRate` INTEGER NOT NULL,
                        `unit` TEXT NOT NULL,
                        `purchasePricePaise` INTEGER NOT NULL,
                        `salePricePaise` INTEGER NOT NULL,
                        `currentStock` INTEGER NOT NULL,
                        `reorderLevel` INTEGER NOT NULL,
                        `hasBatchTracking` INTEGER NOT NULL,
                        `hasVariants` INTEGER NOT NULL,
                        `isArchived` INTEGER NOT NULL,
                        `imageUrl` TEXT,
                        `currentStockMilli` INTEGER NOT NULL,
                        `gstRateBps` INTEGER NOT NULL,
                        `reorderLevelMilli` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }

            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
        }
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(dbFile.absolutePath)
                .callback(callback)
                .build()
        )
        val v8Db = helper.writableDatabase
        v8Db.execSQL(
            """
            INSERT INTO `products` (
                id, name, barcode, sku, category, hsnCode, gstRate, unit,
                purchasePricePaise, salePricePaise, currentStock, reorderLevel,
                hasBatchTracking, hasVariants, isArchived, imageUrl, currentStockMilli,
                gstRateBps, reorderLevelMilli
            ) VALUES (
                'PROD-V8-1', 'Tea Box', '8902', 'SKU-V8', 'Beverages', '0902',
                5, 'PCS', 10000, 15000, 20, 5, 0, 1, 0, NULL, 20000, 500, 5000
            )
            """.trimIndent()
        )
        MIGRATION_8_9.migrate(v8Db)
        v8Db.query("SELECT openingStockMilli FROM `products` WHERE id = 'PROD-V8-1'").use { cursor ->
            assertTrue("Migrated v8 product row should exist", cursor.moveToFirst())
            assertEquals(0L, cursor.getLong(0))
        }
        v8Db.close()
        helper.close()
        dbFile.delete()

        val roomDb = Room.inMemoryDatabaseBuilder(context, PabaruDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val prod = ProductItem(
                id = "PROD-9",
                name = "Spice Pack",
                barcode = "123",
                sku = "SP-1",
                hsnCode = "0910",
                gstRateBps = 500,
                unit = "PCS",
                purchasePricePaise = 5000L,
                salePricePaise = 7500L,
                currentStockMilli = 15000L,
                reorderLevelMilli = 5000L,
                openingStockMilli = 10000L,
                hasVariants = true
            )
            roomDb.productDao().insertProduct(ProductEntity.fromDomain(prod))
            val loadedProd = roomDb.productDao().getAllProductsIncludeArchived().first().single().toDomain()
            assertEquals(10000L, loadedProd.openingStockMilli)

            val variant = ProductVariant("VAR-1", prod.id, "250g Pouch", "SP-250", "123-A", 500L, 4000L)
            roomDb.productVariantDao().insertVariant(ProductVariantEntity.fromDomain(variant))
            assertEquals(1, roomDb.productVariantDao().getAllVariants().first().size)

            // Same-timestamp AuditLog rows must sort deterministically by rowid ASC
            val a1 = CryptoEngine.createAuditRecord(CryptoEngine.GENESIS_HASH, "Product", "P1", "CREATE", "First", "Init", "Owner", customTimestamp = "2026-10-01").copy(id = "AUD-2")
            val a2 = CryptoEngine.createAuditRecord(a1.recordHash, "Product", "P2", "CREATE", "Second", "Init", "Owner", customTimestamp = "2026-10-01").copy(id = "AUD-1")
            roomDb.auditLogDao().insertAuditLog(AuditLogEntity.fromDomain(a1))
            roomDb.auditLogDao().insertAuditLog(AuditLogEntity.fromDomain(a2))
            val orderedLogs = roomDb.auditLogDao().getAllAuditLogsSync().map { it.toDomain() }
            assertEquals(listOf("AUD-2", "AUD-1"), orderedLogs.map { it.id })
            assertTrue("Audit chain verification must pass on deterministic tie-break order", CryptoEngine.verifyAuditChain(orderedLogs).valid)
        } finally {
            roomDb.close()
        }
    }

    @Test
    fun testViewModelProductOpeningStockDeltaTenantPinIsolationBlankSignInAndSignOut() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("pabaru_security_prefs", Context.MODE_PRIVATE).edit().clear().apply()
        context.getSharedPreferences("pabaru_prefs", Context.MODE_PRIVATE).edit().clear().apply()

        val vm = PabaruViewModel()
        vm.initDatabase(context)

        // 1. Blank credentials must be rejected cleanly
        assertFalse("Blank identifier must be rejected", vm.signIn("   ", "1234"))
        assertFalse("Blank PIN must be rejected", vm.signIn("TL-7319", "   "))

        // 2. Per-tenant PIN isolation across two registered stores
        vm.registerBusiness("Store One", BusinessEntityType.Proprietorship, "Owner One", "9810000001", "1111", "27")
        val storeOne = vm.uiState.value.profile.storeCode
        vm.registerBusiness("Store Two", BusinessEntityType.Proprietorship, "Owner Two", "9810000002", "2222", "27")
        val storeTwo = vm.uiState.value.profile.storeCode

        vm.signOut()
        assertFalse(vm.uiState.value.profile.isRegistered)
        assertFalse("signOut must clear isAppLocked", vm.uiState.value.isAppLocked)

        assertTrue("Store One PIN 1111 must still work after registering Store Two", vm.signIn(storeOne, "1111"))
        assertFalse("Store Two PIN must not unlock Store One", vm.signIn(storeOne, "2222"))
        assertTrue("Store Two PIN 2222 must still work", vm.signIn(storeTwo, "2222"))

        // 3. updateProduct adjusts currentStockMilli by openingStockMilli delta when currentStockMilli unchanged
        val initialProd = ProductItem(
            id = "PROD-OPEN-1",
            name = "Sugar",
            barcode = "111",
            sku = "SUG-1",
            hsnCode = "1701",
            gstRateBps = 500,
            unit = "KG",
            purchasePricePaise = 4000L,
            salePricePaise = 4800L,
            currentStockMilli = 10000L,
            reorderLevelMilli = 2000L,
            openingStockMilli = 10000L
        )
        vm.addProduct(initialProd)
        vm.updateProduct(initialProd.copy(openingStockMilli = 15000L)) // +5 KG opening stock change
        val updatedProd = vm.uiState.value.products.first { it.id == "PROD-OPEN-1" }
        assertEquals(15000L, updatedProd.openingStockMilli)
        assertEquals(15000L, updatedProd.currentStockMilli)

        // 4. Backup v3 round-trip preserves openingStockMilli and variants and clears lock
        val variant = ProductVariant("V1", "PROD-OPEN-1", "1KG Pack", "SUG-1K", "111-1", 0L, 5000L)
        val backupPayload = vm.exportBackupJson("AuditPass#99")
        vm.setSecurityPin("4567")
        vm.lockApp()
        assertTrue(vm.uiState.value.isAppLocked)

        vm.deleteProduct("PROD-OPEN-1")
        val restoreRes = vm.restoreEncryptedBackup(backupPayload, "AuditPass#99")
        assertTrue(restoreRes.isSuccess)
        assertFalse("Restoring backup must clear app lock", vm.uiState.value.isAppLocked)
        val restoredProd = vm.uiState.value.products.first { it.id == "PROD-OPEN-1" }
        assertEquals(15000L, restoredProd.openingStockMilli)
        assertEquals(15000L, restoredProd.currentStockMilli)
    }
}
