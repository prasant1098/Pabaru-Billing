package com.example

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.example.data.MIGRATION_7_8
import com.example.data.ManagedUserEntity
import com.example.data.PabaruDatabase
import com.example.data.ProductEntity
import com.example.engine.BankReconEngine
import com.example.engine.CmaAndGstEngine
import com.example.engine.FinancialEngine
import com.example.engine.GstPortalJsonEngine
import com.example.engine.Gstr1ExcelImportEngine
import com.example.model.*
import com.example.viewmodel.PabaruUiState
import com.example.viewmodel.PabaruViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ResidualBugsUnitTest {

    @Test
    fun testRoomMigration7To8AndFractionalPersistenceAndLegacyDamagedMapping() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbName = "migration_v7_to_v8_${System.nanoTime()}.db"
        val dbFile = context.getDatabasePath(dbName)
        dbFile.parentFile?.mkdirs()

        // 1. Create a v7 products table directly and verify MIGRATION_7_8 backfills gstRateBps & reorderLevelMilli
        val callback = object : SupportSQLiteOpenHelper.Callback(7) {
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
                        `currentStockMilli` INTEGER NOT NULL
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
        val v7Db = helper.writableDatabase
        v7Db.execSQL(
            """
            INSERT INTO `products` (
                id, name, barcode, sku, category, hsnCode, gstRate, unit,
                purchasePricePaise, salePricePaise, currentStock, reorderLevel,
                hasBatchTracking, hasVariants, isArchived, imageUrl, currentStockMilli
            ) VALUES (
                'PROD-V7-1', 'Gold Jewellery Box', '8901', 'SKU-V7', 'Jewellery', '7113',
                3, 'PCS', 50000, 75000, 12, 5, 0, 0, 0, NULL, 12000
            )
            """.trimIndent()
        )
        MIGRATION_7_8.migrate(v7Db)
        v7Db.query("SELECT gstRateBps, reorderLevelMilli FROM `products` WHERE id = 'PROD-V7-1'").use { cursor ->
            assertTrue("Migrated v7 product row should exist", cursor.moveToFirst())
            assertEquals(300, cursor.getInt(0))
            assertEquals(5000L, cursor.getLong(1))
        }
        v7Db.close()
        helper.close()
        dbFile.delete()

        // 2. Verify v8 Room in-memory persistence of fractional gstRateBps (0.25%), fractional reorderLevelMilli (2.5),
        //    and legacy "DAMAGED" -> StockAdjustmentReason.DAMAGE mapping on read.
        val roomDb = Room.inMemoryDatabaseBuilder(context, PabaruDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val diamondProduct = ProductItem(
                id = "PROD-DIA-025",
                name = "Rough Diamond Parcel",
                barcode = "7102",
                sku = "DIA-025",
                hsnCode = "7102",
                gstRateBps = 25,
                unit = "CTS",
                purchasePricePaise = 1000000L,
                salePricePaise = 1250000L,
                currentStockMilli = 15500L,
                reorderLevelMilli = 2500L
            )
            roomDb.productDao().insertProduct(ProductEntity.fromDomain(diamondProduct))
            val loadedProduct = roomDb.productDao().getAllProductsIncludeArchived().first().single().toDomain()
            assertEquals(25, loadedProduct.gstRateBps)
            assertEquals(2500L, loadedProduct.reorderLevelMilli)
            assertEquals(15500L, loadedProduct.currentStockMilli)

            // Insert legacy "DAMAGED" string directly into SQLite
            roomDb.openHelper.writableDatabase.execSQL(
                """
                INSERT INTO `stock_adjustments` (
                    id, productId, productName, locationId, quantityDelta, reason, remarks, adjustedBy, timestamp, quantityDeltaMilli
                ) VALUES (
                    'ADJ-LEG-1', 'PROD-DIA-025', 'Rough Diamond Parcel', 'LOC-MAIN', -1, 'DAMAGED', 'Legacy row', 'Owner', '2026-10-01', -1000
                )
                """.trimIndent()
            )
            val loadedAdj = roomDb.stockAdjustmentDao().getAllAdjustments().first().single().toDomain()
            assertEquals(StockAdjustmentReason.DAMAGE, loadedAdj.reason)
            assertEquals(-1000L, loadedAdj.quantityDeltaMilli)
        } finally {
            roomDb.close()
        }
    }

    @Test
    fun testUnclampedNetSalesWcGapAndBalanceSheetParityAcrossItcAndPayableRegimes() {
        val partyCust = Party("PTY-C1", "Customer 1", "9800000001", "Mumbai", "27AAACS1234M1Z2", "AAACS1234M", "27", 0L, BalanceType.Receivable)
        val partySupp = Party("PTY-S1", "Supplier 1", "9800000002", "Mumbai", "27AABCS1234M1Z9", "AABCS1234M", "27", 0L, BalanceType.Payable, isSupplier = true)
        val bank = BankAccount("BNK-01", "HDFC Current", "HDFC Bank", "1234", "HDFC0000001", "shop@hdfc", 5000000L, true)

        // Regime A: Excess ITC (purchases > sales), returns > sales so Net Sales is negative, Liabilities > Assets so WC Gap is negative
        val saleLine = InvoiceLineItem("P1", "Item A", "1006", 1000L, "PCS", 10000L, 1800, 10000L, 900L, 900L, 0L, 11800L)
        val returnLine = InvoiceLineItem("P1", "Item A", "1006", 3000L, "PCS", 10000L, 1800, 30000L, 2700L, 2700L, 0L, 35400L)
        val purchaseLine = InvoiceLineItem("P1", "Item A", "1006", 10000L, "PCS", 10000L, 1800, 100000L, 9000L, 9000L, 0L, 118000L)

        val itcTxns = listOf(
            Transaction("T1", "BR-MAIN", "2026-10-01", partyCust.id, 11800L, TransactionType.Sale, paymentMode = PaymentMode.Bank, bankAccountId = "BNK-01", items = listOf(saleLine)),
            Transaction("T2", "BR-MAIN", "2026-10-02", partyCust.id, 35400L, TransactionType.CreditNote, paymentMode = PaymentMode.Credit, items = listOf(returnLine)),
            Transaction("T3", "BR-MAIN", "2026-10-03", partySupp.id, 118000L, TransactionType.Purchase, paymentMode = PaymentMode.Credit, items = listOf(purchaseLine))
        )

        val stateItc = PabaruUiState(
            profile = BusinessProfile(openingCashBalancePaise = 100000L, openingCapitalPaise = 5100000L, homeStateCode = "27", isRegistered = true),
            parties = listOf(partyCust, partySupp),
            bankAccounts = listOf(bank),
            transactions = itcTxns
        )

        val finItc = FinancialEngine.calculateFyFinancials(stateItc, "All Time")
        assertEquals(-20000L, finItc.netSalesPaise)
        assertTrue("Expected negative net GST (excess ITC asset)", finItc.netGstPayablePaise < 0L)
        assertEquals(
            "Balance sheet must balance strictly when Net GST is ITC Asset",
            finItc.totalAssetsPaise,
            finItc.totalLiabilitiesAndEquityPaise
        )

        // Regime B: Net GST Payable (sales > purchases)
        val bigSaleLine = InvoiceLineItem("P1", "Item A", "1006", 20000L, "PCS", 10000L, 1800, 200000L, 18000L, 18000L, 0L, 236000L)
        val payableTxns = listOf(
            Transaction("T10", "BR-MAIN", "2026-10-01", partyCust.id, 236000L, TransactionType.Sale, paymentMode = PaymentMode.Bank, bankAccountId = "BNK-01", items = listOf(bigSaleLine)),
            Transaction("T11", "BR-MAIN", "2026-10-02", partySupp.id, 59000L, TransactionType.Purchase, paymentMode = PaymentMode.Credit, items = listOf(
                InvoiceLineItem("P1", "Item A", "1006", 5000L, "PCS", 10000L, 1800, 50000L, 4500L, 4500L, 0L, 59000L)
            ))
        )
        val statePayable = stateItc.copy(transactions = payableTxns)
        val finPayable = FinancialEngine.calculateFyFinancials(statePayable, "All Time")
        assertTrue("Expected positive net GST payable", finPayable.netGstPayablePaise > 0L)
        assertEquals(
            "Balance sheet must balance strictly when Net GST is Payable Liability",
            finPayable.totalAssetsPaise,
            finPayable.totalLiabilitiesAndEquityPaise
        )

        // CMA negative Working Capital Gap must not be clamped to 0
        val highPayableParty = partySupp.copy(openingBalancePaise = 90000000L)
        val (_, wc) = CmaAndGstEngine.calculateCmaData(
            transactions = emptyList(),
            parties = listOf(highPayableParty),
            products = emptyList(),
            cashInHandPaise = 100000L,
            bankBalancesPaise = 200000L
        )
        assertTrue("Working capital gap should be negative without clamping", wc.workingCapitalGapPaise < 0L)
    }

    @Test
    fun testFractionalGstSlabsExportNoDoubleCountAndDebitNotePortalJson() {
        val profile = BusinessProfile(businessName = "Jewel & Diamond Co", gstin = "27AAACS1234M1Z2", homeStateCode = "27")
        val regCustomer = Party("PTY-B2B", "Registered Jeweller", "9820011223", "Mumbai", "27AABCU9603R1ZM", "AABCU9603R", "27", 0L, BalanceType.Receivable)
        val walkInCustomer = Party("PTY-B2C", "Walk-in Customer", "9820011224", "Pune", "", "", "27", 0L, BalanceType.Receivable)
        val exportCustomer = Party("PTY-EXP", "Overseas Buyer", "", "Dubai", "", "", "96", 0L, BalanceType.Receivable)

        val diamondLine = InvoiceLineItem("D1", "Rough Diamond", "7102", 1000L, "CTS", 100000L, 25, 100000L, 125L, 125L, 0L, 100250L)
        val exportLine = InvoiceLineItem("E1", "Export Cut Stone", "7102", 1000L, "CTS", 500000L, 1800, 500000L, 0L, 0L, 90000L, 590000L)
        val debitLine = InvoiceLineItem("D2", "Price Escalation", "7102", 1000L, "CTS", 20000L, 300, 20000L, 300L, 300L, 0L, 20600L)

        val txns = listOf(
            // Walk-in B2C at 0.25%
            Transaction("S1", "BR-MAIN", "2026-10-05", walkInCustomer.id, 100250L, TransactionType.Sale, paymentMode = PaymentMode.Cash, invoiceNumber = "INV-001", items = listOf(diamondLine)),
            // Export (POS 96) -> must NOT double-count into B2CS
            Transaction("S2", "BR-MAIN", "2026-10-06", exportCustomer.id, 590000L, TransactionType.Sale, paymentMode = PaymentMode.Bank, invoiceNumber = "EXP-001", remarks = "Export POS: 96 LUT", items = listOf(exportLine)),
            // Customer Debit Note (Registered B2B) at 3%
            Transaction("DN1", "BR-MAIN", "2026-10-07", regCustomer.id, 20600L, TransactionType.DebitNote, paymentMode = PaymentMode.Credit, invoiceNumber = "DN-001", items = listOf(debitLine))
        )
        val parties = listOf(regCustomer, walkInCustomer, exportCustomer)

        // 1. FinancialEngine slab breakdown preserves "0.25%"
        val fySummary = FinancialEngine.calculateFyGstSummary(txns, parties, "All Time")
        assertTrue(fySummary.slabBreakdown.any { it.rateLabel == "0.25%" && it.taxablePaise == 100000L })

        // 2. Statutory GSTR-1 keeps export isolated from B2CS and records customer DebitNote in CDNR
        val gstr1 = CmaAndGstEngine.computeStatutoryGstr1(txns, parties, profile, CmaAndGstEngine.GstPeriodFilter.AllTime)
        assertEquals("Export must not inflate b2cCount", 1, gstr1.b2cCount)
        assertEquals("Export must not inflate b2cTaxablePaise", 100000L, gstr1.b2cTaxablePaise)
        assertEquals("Export invoice must be recorded in exportInvoices", 1, gstr1.exportInvoices.size)
        assertEquals("Customer DebitNote must be recorded in cdnrNotes", 1, gstr1.cdnrNotes.size)
        assertEquals("D", gstr1.cdnrNotes.first().noteType)

        // 3. Portal JSON emits fractional rt=0.25, exp section, CDNR ntty='D', and Table 13 doc_num=4
        val json = JSONObject(GstPortalJsonEngine.generateGstr1Json(profile, gstr1, CmaAndGstEngine.GstPeriodFilter.AllTime))
        val b2cs = json.getJSONArray("b2cs")
        assertEquals(1, b2cs.length())
        assertEquals(0.25, b2cs.getJSONObject(0).getDouble("rt"), 0.001)

        val exp = json.getJSONArray("exp")
        assertEquals(1, exp.length())

        val cdnr = json.getJSONArray("cdnr")
        assertEquals("D", cdnr.getJSONObject(0).getJSONArray("nt").getJSONObject(0).getString("ntty"))

        val docDet = json.getJSONObject("doc_issue").getJSONArray("doc_det")
        val docNums = (0 until docDet.length()).map { docDet.getJSONObject(it).getInt("doc_num") }
        assertTrue("Table 13 should emit doc_num 4 for Debit Notes", docNums.contains(4))
    }

    @Test
    fun testMultilineCsvZipBombGuardAndOfxPrecedence() {
        // 1. RFC-4180 multiline quoted field & escaped quote in CSV
        val multilineCsv = """
            "GSTIN/UIN of Recipient","Receiver Name","Invoice Number","Invoice date","Invoice Value","Place Of Supply","Rate","Taxable Value"
            "27AAACS1234M1Z2","Alpha ""Premier""
            Enterprises","INV-ML-1","01-10-2026","1002.50","27-Maharashtra","0.25","1000.00"
        """.trimIndent()
        val raw = Gstr1ExcelImportEngine.parseSpreadsheetStream(ByteArrayInputStream(multilineCsv.toByteArray()), "multiline.csv")
        assertEquals(1, raw.rows.size)
        assertTrue(raw.rows[0][1].contains("Alpha \"Premier\""))
        assertTrue(raw.rows[0][1].contains("Enterprises"))

        val (_, mapping) = Gstr1ExcelImportEngine.detectFormat(raw.headers)
        val parsed = Gstr1ExcelImportEngine.parseInvoices(raw, Gstr1ExcelImportEngine.TaxSoftwareFormat.GovtOfflineTool, mapping, "27")
        assertEquals(1, parsed.invoices.size)
        assertEquals(25, parsed.invoices[0].gstRateBps)

        // 2. Zip-bomb guard on XLSX (> 50 MB decompressed)
        val zipOut = ByteArrayOutputStream()
        ZipOutputStream(zipOut).use { zos ->
            zos.putNextEntry(ZipEntry("xl/worksheets/sheet1.xml"))
            val chunk = ByteArray(1024 * 1024) // 1 MB of zeros compresses tiny
            repeat(52) { zos.write(chunk) } // 52 MB uncompressed (> 50 MB cap)
            zos.closeEntry()
        }
        val exception = assertThrows(IllegalArgumentException::class.java) {
            Gstr1ExcelImportEngine.parseSpreadsheetStream(ByteArrayInputStream(zipOut.toByteArray()), "bomb.xlsx")
        }
        assertTrue(exception.message?.contains("exceeds safe limit", ignoreCase = true) == true)

        // 3. OFX TRNTYPE precedence vs signed TRNAMT
        val ofx = """
            <OFX><BANKMSGSRSV1><STMTTRNRS><STMTRS><BANKTRANLIST>
            <STMTTRN><TRNTYPE>DEBIT<DTPOSTED>20261001<TRNAMT>+1500.00<FITID>ID1<NAME>ATM FEE</STMTTRN>
            <STMTTRN><TRNTYPE>CREDIT<DTPOSTED>20261002<TRNAMT>2500.00<FITID>ID2<NAME>CUSTOMER NEFT</STMTTRN>
            <STMTTRN><TRNTYPE>OTHER<DTPOSTED>20261003<TRNAMT>-750.00<FITID>ID3<NAME>MISC DEBIT</STMTTRN>
            </BANKTRANLIST></STMTRS></STMTTRNRS></BANKMSGSRSV1></OFX>
        """.trimIndent()
        val ofxResult = BankReconEngine.parseStatementText(ofx, "BNK-01", "SBI")
        assertEquals(3, ofxResult.items.size)
        assertFalse("TRNTYPE DEBIT must remain debit even if positive TRNAMT", ofxResult.items.first { it.referenceNo == "ID1" }.isCredit)
        assertTrue("TRNTYPE CREDIT with positive TRNAMT is credit", ofxResult.items.first { it.referenceNo == "ID2" }.isCredit)
        assertFalse("TRNTYPE OTHER with negative TRNAMT is debit", ofxResult.items.first { it.referenceNo == "ID3" }.isCredit)
    }

    @Test
    fun testViewModelSignInPermissionsImpersonationClearAndBackupRestore() {
        val vm = PabaruViewModel()
        vm.registerBusiness("Store Alpha", BusinessEntityType.Proprietorship, "Owner A", "9820011223", "4321", "27")
        val alphaCode = vm.uiState.value.profile.storeCode

        // Restrict Export & Manager Override for Store Alpha
        vm.setUserBenefits(
            storeCode = alphaCode,
            isGst = true,
            isRecon = true,
            isInvoice = true,
            isInv = true,
            isCma = true,
            isStaff = true,
            isExport = false,
            isOverride = false
        )
        // Reset flags to opposite before signing in
        vm.signIn(alphaCode, "4321")
        assertFalse("signIn must hydrate isExportUnlocked from ManagedUserEntity", vm.uiState.value.isExportUnlocked)
        assertFalse("signIn must hydrate isManagerOverrideUnlocked from ManagedUserEntity", vm.uiState.value.isManagerOverrideUnlocked)

        // Add store data + backup
        vm.addParty(Party("PTY-A1", "Alpha Party", "9811111111", "Mumbai", "", "", "27", 0L, BalanceType.Receivable))
        val backupJson = vm.exportBackupJson("StrongPass#2026")

        // Impersonate another store -> store-scoped lists must be cleared
        val betaUser = ManagedUserEntity(
            storeCode = "TL-BETA",
            businessName = "Store Beta",
            ownerName = "Owner B",
            phone = "9822222222",
            email = "b@beta.in",
            isActive = true
        )
        vm.addNewManagedUser(betaUser)
        vm.enterSuperAdminMode()
        vm.impersonateUser("TL-BETA")
        assertEquals("TL-BETA", vm.uiState.value.profile.storeCode)
        assertTrue("Impersonating different store must clear parties", vm.uiState.value.parties.isEmpty())

        // Add dirty extra party/bank on Beta, then restore Alpha backup -> dirty records must be replaced cleanly
        vm.addParty(Party("PTY-DIRTY", "Dirty Beta Party", "9833333333", "Delhi", "", "", "07", 0L, BalanceType.Receivable))
        val restoreResult = vm.restoreEncryptedBackup(backupJson, "StrongPass#2026")
        assertTrue("Encrypted backup restore should succeed", restoreResult.isSuccess)
        assertEquals(alphaCode, vm.uiState.value.profile.storeCode)
        assertTrue("Restored parties should contain PTY-A1", vm.uiState.value.parties.any { it.id == "PTY-A1" })
        assertFalse("Dirty pre-restore parties must be purged", vm.uiState.value.parties.any { it.id == "PTY-DIRTY" })
    }
}
