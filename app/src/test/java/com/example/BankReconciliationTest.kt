package com.example

import com.example.engine.BankReconEngine
import com.example.model.*
import com.example.viewmodel.PabaruViewModel
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class BankReconciliationTest {

    @Test
    fun testCsvStatementParsing() {
        val sampleCsv = BankReconEngine.getSampleHdfcStatementCsv()
        val result = BankReconEngine.parseStatementText(
            rawContent = sampleCsv,
            targetBankAccountId = "BNK-01",
            targetBankName = "HDFC Bank"
        )

        assertEquals("CSV", result.format)
        assertTrue("Should parse at least 5 statement items", result.items.size >= 5)

        val saiBalajiItem = result.items.find { it.description.contains("SAI BALAJI", ignoreCase = true) }
        assertNotNull("Should find Sai Balaji deposit item", saiBalajiItem)
        assertEquals(1000000L, saiBalajiItem?.amountPaise) // ₹10,000.00
        assertTrue("Should be a credit deposit", saiBalajiItem?.isCredit == true)
        assertEquals("2026-10-01", saiBalajiItem?.date)

        val gujaratSpicesItem = result.items.find { it.description.contains("GUJARAT ROYAL SPICES", ignoreCase = true) }
        assertNotNull("Should find Gujarat Spices withdrawal item", gujaratSpicesItem)
        assertEquals(2500000L, gujaratSpicesItem?.amountPaise) // ₹25,000.00
        assertFalse("Should be a debit withdrawal", gujaratSpicesItem?.isCredit == true)

        val bankChargesItem = result.items.find { it.description.contains("SMS CHARGES", ignoreCase = true) }
        assertNotNull("Should find SMS charges", bankChargesItem)
        assertEquals(2950L, bankChargesItem?.amountPaise) // ₹29.50
    }

    @Test
    fun testOfxStatementParsing() {
        val sampleOfx = BankReconEngine.getSampleSbiStatementOfx()
        val result = BankReconEngine.parseStatementText(
            rawContent = sampleOfx,
            targetBankAccountId = "BNK-02",
            targetBankName = "State Bank of India"
        )

        assertEquals("OFX/QFX", result.format)
        assertTrue("Should parse 4 OFX items", result.items.size == 4)

        val creditItem = result.items.find { it.referenceNo == "SBI20261001001" }
        assertNotNull(creditItem)
        assertEquals(1000000L, creditItem?.amountPaise)
        assertTrue(creditItem?.isCredit == true)
        assertEquals("2026-10-01", creditItem?.date)

        val debitItem = result.items.find { it.referenceNo == "SBI20261002002" }
        assertNotNull(debitItem)
        assertEquals(2500000L, debitItem?.amountPaise)
        assertFalse(debitItem?.isCredit == true)
    }

    @Test
    fun testAutomatedMatchingAlgorithm() {
        val transactions = listOf(
            Transaction(
                id = "TXN-001",
                branchId = "BR-MAIN",
                date = "2026-10-01",
                partyId = "PTY-101",
                amountPaise = 1000000L, // ₹10,000
                type = TransactionType.PaymentIn,
                paymentMode = PaymentMode.UPI,
                bankAccountId = "BNK-01",
                remarks = "Received via GPay QR",
                invoiceNumber = "INV/2026-27/0001"
            ),
            Transaction(
                id = "TXN-002",
                branchId = "BR-MAIN",
                date = "2026-10-02",
                partyId = "PTY-102",
                amountPaise = 2500000L, // ₹25,000
                type = TransactionType.Purchase,
                paymentMode = PaymentMode.Bank,
                bankAccountId = "BNK-01",
                remarks = "Dispatch payment",
                invoiceNumber = "PUR/GJ-9912"
            )
        )

        val parties = listOf(
            Party("PTY-101", "Sai Balaji Kirana and Traders", "9820192837", "Mumbai", "27AAACS1234M1Z2", "AAACS1234M", "27", 0L, BalanceType.Receivable),
            Party("PTY-102", "Gujarat Royal Spices and Grains", "9426781290", "Gujarat", "24AABCG5544K1Z8", "AABCG5544K", "24", 0L, BalanceType.Payable, isSupplier = true)
        )

        val statementItems = listOf(
            BankReconItem("STMT-01", "BNK-01", "2026-10-01", "UPI-GPAY-RECVD FROM SAI BALAJI-INV/2026-27/0001", 1000000L, isCredit = true),
            BankReconItem("STMT-02", "BNK-01", "2026-10-02", "NEFT-SENT TO GUJARAT ROYAL SPICES", 2500000L, isCredit = false),
            BankReconItem("STMT-03", "BNK-01", "2026-10-02", "BANK CHARGES & SMS ALERT", 2950L, isCredit = false)
        )

        val matched = BankReconEngine.matchBankStatement(statementItems, transactions, parties)

        val match1 = matched.find { it.id == "STMT-01" }
        assertNotNull(match1)
        assertEquals("TXN-001", match1?.matchedTxnId)
        assertTrue("Match score should be high (>=80)", (match1?.matchConfidence ?: 0) >= 80)
        assertTrue("Should be auto-reconciled", match1?.isReconciled == true)

        val match2 = matched.find { it.id == "STMT-02" }
        assertNotNull(match2)
        assertEquals("TXN-002", match2?.matchedTxnId)
        assertTrue("Match score should be high", (match2?.matchConfidence ?: 0) >= 80)

        val match3 = matched.find { it.id == "STMT-03" }
        assertNotNull(match3)
        assertNull("Bank charges has no matching transaction, should be unmatched", match3?.matchedTxnId)
        assertEquals(0, match3?.matchConfidence)
        assertFalse(match3?.isReconciled == true)
    }

    @Test
    fun testDiscrepancyResolutionCreatesBalancingTransaction() {
        val stmtItem = BankReconItem(
            id = "STMT-CHG",
            bankAccountId = "BNK-01",
            date = "2026-10-02",
            description = "MONTHLY BANK SMS CHARGES & GST",
            amountPaise = 2950L, // ₹29.50
            isCredit = false
        )

        val txn = BankReconEngine.createBalancingTransaction(
            stmt = stmtItem,
            resolutionType = BankReconEngine.DiscrepancyResolutionType.BANK_CHARGES,
            branchId = "BR-MAIN"
        )

        assertEquals(2950L, txn.amountPaise)
        assertEquals(TransactionType.Expense, txn.type)
        assertEquals("BNK-01", txn.bankAccountId)
        assertEquals("2026-10-02", txn.date)

        // Verify GST item calculation: 18% inclusive
        assertEquals(1, txn.items.size)
        val lineItem = txn.items[0]
        assertEquals("9971", lineItem.hsnCode)
        assertEquals(18, lineItem.gstRate)
        assertEquals(2950L, lineItem.totalPaise)
        assertEquals(2500L, lineItem.taxableAmountPaise) // ₹25.00
        assertEquals(225L, lineItem.cgstPaise)           // ₹2.25
        assertEquals(225L, lineItem.sgstPaise)           // ₹2.25
    }

    @Test
    fun testViewModelFullReconciliationWorkflow() {
        val vm = PabaruViewModel()
        vm.addParty(
            com.example.model.Party(
                id = "PTY-101",
                name = "Sai Balaji Kirana and Traders",
                phone = "9820192837",
                address = "Mumbai",
                gstin = "27AAACS1234M1Z2",
                pan = "AAACS1234M",
                stateCode = "27",
                openingBalancePaise = 0L,
                openingBalanceType = com.example.model.BalanceType.Settled
            )
        )
        vm.recordPayment(
            partyId = "PTY-101",
            amountPaise = 1000000L,
            isPaymentIn = true,
            paymentMode = com.example.model.PaymentMode.UPI,
            remarks = "UPI-GPAY-RECVD FROM SAI BALAJI"
        )

        // 1. Import sample statement into ViewModel
        vm.importSampleStatement("CSV", "BNK-01")
        val state = vm.uiState.value

        val bnk01Items = state.bankReconItems.filter { it.bankAccountId == "BNK-01" }
        assertTrue("Should have imported statement items for BNK-01", bnk01Items.isNotEmpty())

        // 2. Re-run reconciliation
        vm.reconcileBankFeed()
        val afterRecon = vm.uiState.value.bankReconItems.filter { it.bankAccountId == "BNK-01" }
        val matchedCount = afterRecon.count { it.matchedTxnId != null }
        assertTrue("Should have matched transactions", matchedCount > 0)

        // 3. Confirm all matches
        vm.confirmAllMatches(80)
        val confirmedCount = vm.uiState.value.bankReconItems.count { it.isUserConfirmed }
        assertTrue("Should have confirmed matches", confirmedCount > 0)

        // 4. Resolve an unmatched discrepancy
        val unmatched = vm.uiState.value.bankReconItems.firstOrNull { it.matchedTxnId == null }
        if (unmatched != null) {
            vm.resolveDiscrepancy(
                stmtItemId = unmatched.id,
                resolutionType = if (unmatched.isCredit) BankReconEngine.DiscrepancyResolutionType.INTEREST_INCOME else BankReconEngine.DiscrepancyResolutionType.BANK_CHARGES
            )
            val resolvedItem = vm.uiState.value.bankReconItems.find { it.id == unmatched.id }
            assertNotNull(resolvedItem?.matchedTxnId)
            assertTrue(resolvedItem?.isReconciled == true)
            assertTrue(resolvedItem?.isUserConfirmed == true)
        }
    }
}
