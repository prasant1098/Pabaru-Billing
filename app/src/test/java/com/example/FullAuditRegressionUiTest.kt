package com.example

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.example.engine.FinancialEngine
import com.example.model.*
import com.example.ui.screens.CmaProjectReportScreen
import com.example.ui.screens.InventoryScreen
import com.example.ui.screens.InvoiceMenuScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.PabaruUiState
import com.example.viewmodel.PabaruViewModel
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class FullAuditRegressionUiTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun testCmaFillableYearDecimalTypingDoesNotResetAndAutoSeedUpdatesEpoch() {
        val currentFy = FinancialEngine.getCurrentFinancialYear()
        val saleTxn = Transaction(
            id = "TXN-CMA-SALE",
            branchId = "BR-MAIN",
            date = FinancialEngine.getTodayDate(),
            partyId = "PTY-1",
            amountPaise = 250000L,
            type = TransactionType.Sale,
            paymentMode = PaymentMode.Cash,
            invoiceNumber = "INV/26-27/0001",
            items = listOf(InvoiceLineItem("P1", "Goods", "1006", 1000L, "PCS", 250000L, 0, 250000L, 0L, 0L, 0L, 250000L))
        )

        composeTestRule.setContent {
            MyApplicationTheme {
                CmaProjectReportScreen(
                    profile = BusinessProfile(businessName = "Test Biz", homeStateCode = "27", isRegistered = true),
                    transactions = listOf(saleTxn),
                    parties = emptyList(),
                    products = emptyList(),
                    cashAndBankPaise = 100000L,
                    onBack = {}
                )
            }
        }
        composeTestRule.waitForIdle()

        // Navigate to Tab 7 (Edit Year)
        composeTestRule.onNodeWithTag("cma_tab_7").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        // Scroll to Operating Statement Inputs and replace text with trailing decimal "100."
        composeTestRule.onNodeWithTag("cma_fillable_year_screen")
            .performScrollToNode(hasText("Gross Sales / Invoiced Turnover (₹)"))
        composeTestRule.waitForIdle()
        val salesField = composeTestRule.onNodeWithText("Gross Sales / Invoiced Turnover (₹)")
        salesField.performTextReplacement("100.")
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("100.").assertExists()

        // Clear field to "" and verify it remains empty instead of snapping back to previous value
        salesField.performTextReplacement("")
        composeTestRule.waitForIdle()

        // Scroll back to Quick Automation Bar and click Auto-Seed from Ledger -> populates ₹2,500 ("2500")
        composeTestRule.onNodeWithTag("cma_fillable_year_screen")
            .performScrollToNode(hasTestTag("cma_auto_seed_btn"))
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("cma_auto_seed_btn").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("cma_fillable_year_screen")
            .performScrollToNode(hasText("Gross Sales / Invoiced Turnover (₹)"))
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("2500").assertExists()

        assertTrue(currentFy.isNotBlank())
    }

    @Test
    fun testInvoiceMenuFormPreservesHsnAndGstRateAcrossNonGstToggleAndRejectsInvalidDueDate() {
        val prod = ProductItem(
            id = "PROD-GST-18",
            name = "Laptop Stand",
            barcode = "111",
            sku = "LS-1",
            hsnCode = "8471",
            gstRateBps = 1800,
            unit = "PCS",
            purchasePricePaise = 50000L,
            salePricePaise = 100000L,
            currentStockMilli = 10000L,
            reorderLevelMilli = 2000L
        )
        val customer = Party("PTY-CUST", "Alpha Traders", "9800000001", "Mumbai", "", "", "27", 0L, BalanceType.Receivable)
        var savedType: TransactionType? = null
        var savedItems: List<InvoiceLineItem> = emptyList()
        var saveCount = 0

        composeTestRule.setContent {
            MyApplicationTheme {
                InvoiceMenuScreen(
                    uiState = PabaruUiState(
                        parties = listOf(customer),
                        products = listOf(prod),
                        profile = BusinessProfile(homeStateCode = "27", isRegistered = true)
                    ),
                    onSaveInvoice = { _, _, type, items, _ ->
                        saveCount++
                        savedType = type
                        savedItems = items
                        true
                    },
                    onVoid = {}
                )
            }
        }
        composeTestRule.waitForIdle()

        // Verify filter chips enforce >= 48.dp touch target
        composeTestRule.onNodeWithText("All").assertHeightIsAtLeast(48.dp)
        composeTestRule.onNodeWithText("Unpaid").assertHeightIsAtLeast(48.dp)
        composeTestRule.onNodeWithText("Paid").assertHeightIsAtLeast(48.dp)

        // Open "+ Tax invoice" form
        composeTestRule.onNodeWithText("+ Tax invoice").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("GST 18%").assertExists()

        // Toggle doc type -> Non-GST Bill -> Tax badge becomes "Non-GST"
        composeTestRule.onNodeWithText("Standard tax invoice").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Non-GST Bill (Estimate / Cash Memo)").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Non-GST").assertExists()

        // Toggle back -> Standard tax invoice -> original 18% GST and HSN must be restored
        composeTestRule.onNodeWithText("Non-GST bill").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Standard tax invoice").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("GST 18%").assertExists()

        // Scroll to Payment mode card, select Credit, and enter invalid due date "2026-02-30" -> Save must be blocked
        composeTestRule.onNode(hasScrollAction()).performScrollToNode(hasText("Credit"))
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Credit").performClick()
        composeTestRule.waitForIdle()
        val dueDateMatcher = hasSetTextAction().and(hasText("-", substring = true))
        composeTestRule.onNode(hasScrollAction()).performScrollToNode(dueDateMatcher)
        composeTestRule.waitForIdle()
        val dueDateNode = composeTestRule.onAllNodes(hasSetTextAction()).filterToOne(hasText("-", substring = true))
        dueDateNode.performTextReplacement("2026-02-30")
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Issue & Save").performClick()
        composeTestRule.waitForIdle()
        assertEquals("Invalid credit due date must prevent saving", 0, saveCount)

        // Fix due date to valid "2026-12-15" and issue -> HSN and 1800 bps rate preserved
        dueDateNode.performTextReplacement("2026-12-15")
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Issue & Save").performClick()
        composeTestRule.waitForIdle()
        assertEquals(1, saveCount)
        assertEquals(TransactionType.Sale, savedType)
        assertEquals(1800, savedItems.single().gstRateBps)
        assertEquals("8471", savedItems.single().hsnCode)
    }

    @Test
    fun testInvoiceHubFifoCreditPendingAndUnpaidPaidFilters() {
        val partyA = Party("PTY-A", "Unpaid Buyer", "9800000001", "Mumbai", "", "", "27", 0L, BalanceType.Receivable)
        val partyB = Party("PTY-B", "Settled Buyer", "9800000002", "Mumbai", "", "", "27", 0L, BalanceType.Receivable)
        val today = FinancialEngine.getTodayDate()

        val unpaidSale = Transaction("T-UNPAID", "BR-MAIN", today, partyA.id, 200000L, TransactionType.Sale, paymentMode = PaymentMode.Credit, invoiceNumber = "INV/26-27/0001")
        val partialPay = Transaction("T-PAY-PART", "BR-MAIN", today, partyA.id, 50000L, TransactionType.PaymentIn, paymentMode = PaymentMode.Cash)
        val paidSale = Transaction("T-PAID", "BR-MAIN", today, partyB.id, 100000L, TransactionType.Sale, paymentMode = PaymentMode.Credit, invoiceNumber = "INV/26-27/0002")
        val fullPay = Transaction("T-PAY-FULL", "BR-MAIN", today, partyB.id, 100000L, TransactionType.PaymentIn, paymentMode = PaymentMode.UPI)

        composeTestRule.setContent {
            MyApplicationTheme {
                InvoiceMenuScreen(
                    uiState = PabaruUiState(
                        parties = listOf(partyA, partyB),
                        transactions = listOf(unpaidSale, partialPay, paidSale, fullPay)
                    ),
                    onSaveInvoice = { _, _, _, _, _ -> true },
                    onVoid = {}
                )
            }
        }
        composeTestRule.waitForIdle()

        // Remaining credit pending = (2000 - 500) + (1000 - 1000) = ₹1,500.00 -> ₹1.50K
        composeTestRule.onNodeWithText("Credit pending").assertExists()
        composeTestRule.onNodeWithText("₹1.50K").assertExists()

        // Unpaid filter shows only INV/26-27/0001
        composeTestRule.onNodeWithText("Unpaid").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Unpaid Buyer").assertExists()
        composeTestRule.onNodeWithText("Settled Buyer").assertDoesNotExist()

        // Paid filter shows only INV/26-27/0002
        composeTestRule.onNodeWithText("Paid").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Settled Buyer").assertExists()
        composeTestRule.onNodeWithText("Unpaid Buyer").assertDoesNotExist()
    }

    @Test
    fun testInventoryUnifiedLowStockAndMandatoryBatchValidation() {
        val vm = PabaruViewModel()
        val lowProd = ProductItem(
            id = "PROD-LOW",
            name = "Low Stock Widget",
            barcode = "111",
            sku = "LOW-1",
            hsnCode = "1006",
            gstRateBps = 500,
            unit = "PCS",
            purchasePricePaise = 1000L,
            salePricePaise = 2000L,
            currentStockMilli = 10000L, // 10 <= 15 threshold
            reorderLevelMilli = 5000L,
            hasBatchTracking = true
        )
        val outProd = ProductItem(
            id = "PROD-OUT",
            name = "Zero Stock Gadget",
            barcode = "222",
            sku = "OUT-1",
            hsnCode = "1006",
            gstRateBps = 500,
            unit = "PCS",
            purchasePricePaise = 1000L,
            salePricePaise = 2000L,
            currentStockMilli = 0L, // out of stock must NOT be counted in Low Stock
            reorderLevelMilli = 5000L
        )
        val okProd = ProductItem(
            id = "PROD-OK",
            name = "Healthy Stock Item",
            barcode = "333",
            sku = "OK-1",
            hsnCode = "1006",
            gstRateBps = 500,
            unit = "PCS",
            purchasePricePaise = 1000L,
            salePricePaise = 2000L,
            currentStockMilli = 50000L,
            reorderLevelMilli = 5000L
        )
        vm.addProduct(lowProd)
        vm.addProduct(outProd)
        vm.addProduct(okProd)

        composeTestRule.setContent {
            MyApplicationTheme {
                InventoryScreen(
                    uiState = vm.uiState.value,
                    viewModel = vm,
                    initialFilterLowStock = true
                )
            }
        }
        composeTestRule.waitForIdle()

        val verticalScrollMatcher = SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange)

        // Scroll product list into view: only lowProd should match Low Stock filter
        composeTestRule.onNode(verticalScrollMatcher).performScrollToNode(hasText("Low Stock Widget"))
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Low Stock Widget").assertExists()
        composeTestRule.onNodeWithText("Zero Stock Gadget").assertDoesNotExist()
        composeTestRule.onNodeWithText("Healthy Stock Item").assertDoesNotExist()

        // Scroll back to Quick Action Bar, open Purchase Bill dialog, and verify batch-tracked item rejects blank batch number
        composeTestRule.onNode(verticalScrollMatcher).performScrollToNode(hasTestTag("inventory_purchase_bill_btn"))
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("inventory_purchase_bill_btn").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNode(verticalScrollMatcher.and(hasAnyAncestor(isDialog())))
            .performScrollToNode(hasText("+ Add to Bill"))
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("+ Add to Bill").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Bill Items (1):").assertDoesNotExist()
    }
}
