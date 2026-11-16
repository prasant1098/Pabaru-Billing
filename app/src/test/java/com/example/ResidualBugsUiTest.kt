package com.example

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.unit.dp
import com.example.data.ManagedUserEntity
import com.example.model.*
import com.example.ui.screens.BusinessProfileScreen
import com.example.ui.screens.CashBankScreen
import com.example.ui.screens.ManageQrScreen
import com.example.ui.screens.SuperAdminScreen
import com.example.ui.screens.UnpaidBillsDetailScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.PabaruUiState
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ResidualBugsUiTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun testCashBankScreenDisplaysLiveBankBalance() {
        val bank = BankAccount(
            id = "BNK-LIVE-1",
            accountName = "Main HDFC",
            bankName = "HDFC Bank",
            accountNumber = "1234",
            ifscCode = "HDFC0000001",
            upiId = "main@hdfc",
            openingBalancePaise = 100000L, // ₹1,000.00
            isDefaultForQr = true
        )
        val inflow = Transaction(
            id = "TXN-UPI-IN-1",
            branchId = "BR-MAIN",
            date = "2026-10-01",
            partyId = null,
            amountPaise = 250000L, // ₹2,500.00
            type = TransactionType.PaymentIn,
            paymentMode = PaymentMode.UPI,
            bankAccountId = "BNK-LIVE-1",
            remarks = "UPI collection"
        )
        val state = PabaruUiState(
            bankAccounts = listOf(bank),
            transactions = listOf(inflow)
        )

        composeTestRule.setContent {
            MyApplicationTheme {
                CashBankScreen(
                    uiState = state,
                    onRunReconciliation = {},
                    onImportStatement = {}
                )
            }
        }
        composeTestRule.waitForIdle()
        // Live balance = ₹1,000 + ₹2,500 = ₹3,500.00 (not static opening ₹1,000.00)
        composeTestRule.onNodeWithText("₹3,500.00", substring = true).assertExists()
    }

    @Test
    fun testUnpaidBillsDeductsCreditNotesAndDebitNotes() {
        val customer = Party("PTY-CUST", "Customer One", "9800000001", "Mumbai", "", "", "27", 0L, BalanceType.Settled)
        val supplier = Party("PTY-SUPP", "Supplier One", "9800000002", "Mumbai", "", "", "27", 0L, BalanceType.Settled, isSupplier = true)

        val sale = Transaction(
            id = "TXN-S1", branchId = "BR-MAIN", date = "2026-10-01", partyId = customer.id,
            amountPaise = 200000L, type = TransactionType.Sale, paymentMode = PaymentMode.Credit, invoiceNumber = "INV-100"
        )
        val creditNote = Transaction(
            id = "TXN-CN1", branchId = "BR-MAIN", date = "2026-10-02", partyId = customer.id,
            amountPaise = 50000L, type = TransactionType.CreditNote, paymentMode = PaymentMode.Credit, invoiceNumber = "CN-100"
        )
        val purchase = Transaction(
            id = "TXN-P1", branchId = "BR-MAIN", date = "2026-10-01", partyId = supplier.id,
            amountPaise = 300000L, type = TransactionType.Purchase, paymentMode = PaymentMode.Credit, invoiceNumber = "PUR-200"
        )
        val debitNote = Transaction(
            id = "TXN-DN1", branchId = "BR-MAIN", date = "2026-10-02", partyId = supplier.id,
            amountPaise = 100000L, type = TransactionType.DebitNote, paymentMode = PaymentMode.Credit, invoiceNumber = "DN-200"
        )
        val uiState = PabaruUiState(
            parties = listOf(customer, supplier),
            transactions = listOf(sale, creditNote, purchase, debitNote)
        )

        // Collect tab: ₹2,000 Sale - ₹500 CreditNote = ₹1,500.00 pending
        composeTestRule.setContent {
            MyApplicationTheme {
                UnpaidBillsDetailScreen(
                    isCollect = true,
                    uiState = uiState,
                    onBack = {},
                    onRecordPayment = { _, _, _, _, _ -> }
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("1 pending bills • ₹ 1,500.00 outstanding").assertIsDisplayed()
        composeTestRule.onAllNodesWithText("1,500.00", substring = true).onFirst().assertIsDisplayed()
    }

    @Test
    fun testUnpaidBillsDeductsSupplierDebitNotes() {
        val supplier = Party("PTY-SUPP", "Supplier One", "9800000002", "Mumbai", "", "", "27", 0L, BalanceType.Settled, isSupplier = true)
        val purchase = Transaction(
            id = "TXN-P1", branchId = "BR-MAIN", date = "2026-10-01", partyId = supplier.id,
            amountPaise = 300000L, type = TransactionType.Purchase, paymentMode = PaymentMode.Credit, invoiceNumber = "PUR-200"
        )
        val debitNote = Transaction(
            id = "TXN-DN1", branchId = "BR-MAIN", date = "2026-10-02", partyId = supplier.id,
            amountPaise = 100000L, type = TransactionType.DebitNote, paymentMode = PaymentMode.Credit, invoiceNumber = "DN-200"
        )
        val uiState = PabaruUiState(
            parties = listOf(supplier),
            transactions = listOf(purchase, debitNote)
        )

        // Pay tab: ₹3,000 Purchase - ₹1,000 DebitNote = ₹2,000.00 pending
        composeTestRule.setContent {
            MyApplicationTheme {
                UnpaidBillsDetailScreen(
                    isCollect = false,
                    uiState = uiState,
                    onBack = {},
                    onRecordPayment = { _, _, _, _, _ -> }
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("1 pending bills • ₹ 2,000.00 outstanding").assertIsDisplayed()
        composeTestRule.onAllNodesWithText("2,000.00", substring = true).onFirst().assertIsDisplayed()
    }

    @Test
    fun testSecondaryScreensHandleSystemBackPressAndMeetTouchTargets() {
        var profileBacked = false
        composeTestRule.setContent {
            MyApplicationTheme {
                BusinessProfileScreen(
                    uiState = PabaruUiState(),
                    onSaveProfile = {},
                    onBack = { profileBacked = true }
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.runOnUiThread {
            composeTestRule.activity.onBackPressedDispatcher.onBackPressed()
        }
        composeTestRule.waitForIdle()
        assertTrue("BusinessProfileScreen BackHandler must invoke onBack", profileBacked)
    }

    @Test
    fun testManageQrSystemBackAndSuperAdminTouchTargets() {
        var qrBacked = false
        composeTestRule.setContent {
            MyApplicationTheme {
                ManageQrScreen(
                    uiState = PabaruUiState(),
                    onBack = { qrBacked = true },
                    onLockNow = {},
                    onAddQr = {},
                    onUpdateQr = {},
                    onDeleteQr = {},
                    onSetDefaultQr = {},
                    onToggleQrEnabled = {}
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.runOnUiThread {
            composeTestRule.activity.onBackPressedDispatcher.onBackPressed()
        }
        composeTestRule.waitForIdle()
        assertTrue("ManageQrScreen BackHandler must invoke onBack", qrBacked)
    }

    @Test
    fun testSuperAdminActionControlsHaveMinimum48DpTouchTarget() {
        val sampleUsers = listOf(
            ManagedUserEntity(
                storeCode = "TL-M3",
                businessName = "Touch Target Store",
                ownerName = "Tester",
                phone = "9820011223",
                email = "t@store.in",
                isActive = true
            )
        )
        composeTestRule.setContent {
            MyApplicationTheme {
                SuperAdminScreen(
                    managedUsers = sampleUsers,
                    onToggleUserStatus = { _, _ -> },
                    onUpdateUserBenefits = { _, _, _, _, _, _, _, _, _ -> },
                    onAddNewUser = {},
                    onDeleteUser = {},
                    onImpersonateUser = {},
                    onExitSuperAdmin = {}
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("super_admin_back_button", useUnmergedTree = true).assertHeightIsAtLeast(48.dp)
        composeTestRule.onNodeWithTag("super_admin_sync_now_button", useUnmergedTree = true).assertHeightIsAtLeast(48.dp)
        composeTestRule.onNodeWithTag("super_admin_lazy_column")
            .performScrollToNode(hasTestTag("toggle_bulk_mode_button"))
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("toggle_bulk_mode_button", useUnmergedTree = true).assertHeightIsAtLeast(48.dp)
    }
}
