package com.example

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.data.ManagedUserEntity
import com.example.ui.screens.GstAssistantScreen
import com.example.ui.screens.SuperAdminScreen
import com.example.viewmodel.PabaruUiState
import com.example.viewmodel.PabaruViewModel
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun testClickGstr3bTab() {
        composeTestRule.setContent {
            GstAssistantScreen(
                uiState = PabaruUiState(),
                viewModel = null,
                initialStep = 4
            )
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("tab_gstr3b_button", useUnmergedTree = true).performScrollTo().performClick()
        composeTestRule.waitForIdle()
    }

    @Test
    fun testPremiumFeatureComingSoonScreenGst() {
        var backClicked = false
        composeTestRule.setContent {
            com.example.ui.screens.PremiumFeatureComingSoonScreen(
                featureType = com.example.ui.screens.PremiumFeatureType.GST_FILING_ASSISTANT,
                onBack = { backClicked = true }
            )
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("GST Filing Assistant").assertExists()
        composeTestRule.onNodeWithText("PAID SERVICE · COMING SOON").assertExists()

        // Verify Upgrade Benefits Checklist has been completely removed as requested
        composeTestRule.onNodeWithTag("upgrade_benefits_checklist", useUnmergedTree = true).assertDoesNotExist()

        // Click Notify Me / Waitlist button
        composeTestRule.onNodeWithTag("waitlist_button", useUnmergedTree = true).performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("On Priority Waitlist").assertExists()

        // Click Back
        composeTestRule.onNodeWithTag("back_to_dashboard_button", useUnmergedTree = true).performScrollTo().performClick()
        composeTestRule.waitForIdle()
        assertTrue("Back button should trigger onBack callback", backClicked)
    }

    @Test
    fun testPremiumFeatureComingSoonScreenReconciliation() {
        composeTestRule.setContent {
            com.example.ui.screens.PremiumFeatureComingSoonScreen(
                featureType = com.example.ui.screens.PremiumFeatureType.BANK_RECONCILIATION,
                onBack = {}
            )
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Automated Bank Reconciliation").assertExists()
        composeTestRule.onNodeWithText("PAID SERVICE · COMING SOON").assertExists()

        // Verify Upgrade Benefits Checklist has been removed
        composeTestRule.onNodeWithTag("upgrade_benefits_checklist", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun testSuperAdminMasterPinAndActivationLogic() {
        val viewModel = PabaruViewModel()

        // 1. Set Master PIN and verify PBKDF2 hash comparison
        viewModel.setMasterPin("9999")
        assertTrue("PIN 9999 must unlock Super Admin", viewModel.verifyMasterPin("9999"))
        assertFalse("PIN 1234 is not master PIN", viewModel.verifyMasterPin("1234"))

        // 2. Test Super Admin enter / exit mode
        viewModel.enterSuperAdminMode()
        assertTrue("Super Admin session should be active", viewModel.uiState.value.isSuperAdminSession)
        viewModel.exitSuperAdminMode()
        assertFalse("Super Admin session should be closed", viewModel.uiState.value.isSuperAdminSession)

        // Seed a managed user for test
        viewModel.addNewManagedUser(
            ManagedUserEntity(
                storeCode = "TL-7319",
                businessName = "Test Store",
                ownerName = "Test Owner",
                phone = "9820011223",
                email = "test@store.in",
                isActive = true
            )
        )

        // 3. Test Suspension of user
        viewModel.toggleUserActivation("TL-7319", false)
        val suspendedUser = viewModel.uiState.value.managedUsers.find { it.storeCode == "TL-7319" }
        assertFalse("TL-7319 should now be deactivated", suspendedUser?.isActive ?: true)

        // Attempt sign in with suspended store should fail
        val signInResult = viewModel.signIn("TL-7319", "1234")
        assertFalse("Sign in for suspended user must fail", signInResult)
        assertTrue("Error message must indicate suspension", viewModel.uiState.value.authErrorMessage?.contains("Suspended") == true)

        // Reactivate store
        viewModel.toggleUserActivation("TL-7319", true)
        val reactivatedUser = viewModel.uiState.value.managedUsers.find { it.storeCode == "TL-7319" }
        assertTrue("TL-7319 should now be active", reactivatedUser?.isActive ?: false)

        // 4. Test Benefits allocation
        viewModel.setUserBenefits(
            storeCode = "TL-7319",
            isGst = true,
            isRecon = true,
            isInvoice = true,
            isInv = true,
            isCma = true,
            isStaff = true,
            isExport = true,
            isOverride = true
        )
        val userWithBenefits = viewModel.uiState.value.managedUsers.find { it.storeCode == "TL-7319" }
        assertTrue("GST should be unlocked", userWithBenefits?.isGstUnlocked == true)
        assertTrue("Bank Recon should be unlocked", userWithBenefits?.isBankReconUnlocked == true)
        assertTrue("Export should be unlocked", userWithBenefits?.isExportUnlocked == true)
        assertTrue("Manager Override should be unlocked", userWithBenefits?.isManagerOverrideUnlocked == true)

        // 5. Test Trigger Local Offline-First Sync
        viewModel.triggerLocalSync()
        assertTrue("Sync status should indicate local synchronization", viewModel.uiState.value.localSyncStatus.contains("local", ignoreCase = true))
    }

    @Test
    fun testGstinModulo36CheckChar() {
        val first14 = "27ABCDE1234F1Z"
        val checkChar = com.example.engine.FinancialEngine.gstinCheckChar(first14)
        assertTrue("Check char should be valid char", checkChar in com.example.engine.FinancialEngine.GSTIN_CHARS)

        // Validate standard sample GSTIN
        val fullGstin = "$first14$checkChar"
        val validation = com.example.engine.FinancialEngine.validateGstin(fullGstin, allowBlank = false)
        assertTrue("GSTIN with correct check char must be valid: ${validation.message}", validation.isValid)

        // Incorrect check char must fail validation
        val badChar = if (checkChar == 'A') 'B' else 'A'
        val invalidGstin = "$first14$badChar"
        val badValidation = com.example.engine.FinancialEngine.validateGstin(invalidGstin, allowBlank = false)
        assertFalse("GSTIN with incorrect check char must fail validation", badValidation.isValid)
    }

    @Test
    fun testGstSlabsAndBasisPointMath() {
        val taxable = 100000L // ₹ 1,000.00
        // Standard rates: 0%, 5% (500 bps), 18% (1800 bps), 40% (4000 bps)
        val intra5 = com.example.engine.FinancialEngine.calculateGstBps(taxable, 500, isInterstate = false)
        org.junit.Assert.assertEquals(2500L, intra5.cgstPaise) // ₹ 25.00
        org.junit.Assert.assertEquals(2500L, intra5.sgstPaise) // ₹ 25.00
        org.junit.Assert.assertEquals(0L, intra5.igstPaise)
        org.junit.Assert.assertEquals(105000L, intra5.totalPaise)

        // Interstate 18% (1800 bps)
        val inter18 = com.example.engine.FinancialEngine.calculateGstBps(taxable, 1800, isInterstate = true)
        org.junit.Assert.assertEquals(0L, inter18.cgstPaise)
        org.junit.Assert.assertEquals(0L, inter18.sgstPaise)
        org.junit.Assert.assertEquals(18000L, inter18.igstPaise)
        org.junit.Assert.assertEquals(118000L, inter18.totalPaise)
    }

    @Test
    fun testSuperAdminScreenComposables() {
        var backClicked = false
        var statusToggled = false
        var benefitsUpdated = false
        var syncTriggered = false
        val testUsers = listOf(
            ManagedUserEntity(
                storeCode = "TL-TEST-1",
                businessName = "Test Kirana Store",
                ownerName = "Rakesh Gupta",
                phone = "9820011223",
                email = "rakesh@kirana.in",
                isActive = true,
                isGstUnlocked = false,
                isBankReconUnlocked = false,
                isInvoiceUnlocked = true,
                isInventoryUnlocked = true,
                isCmaUnlocked = true,
                isStaffUnlocked = true,
                isExportUnlocked = true,
                isManagerOverrideUnlocked = true
            )
        )

        composeTestRule.setContent {
            SuperAdminScreen(
                managedUsers = testUsers,
                onToggleUserStatus = { _, _ -> statusToggled = true },
                onUpdateUserBenefits = { _, _, _, _, _, _, _, _, _ -> benefitsUpdated = true },
                onAddNewUser = {},
                onDeleteUser = {},
                onImpersonateUser = {},
                onExitSuperAdmin = { backClicked = true },
                syncStatus = "All permissions persisted locally",
                lastSyncTime = System.currentTimeMillis(),
                isSyncing = false,
                onTriggerSync = { syncTriggered = true }
            )
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Super Admin Console").assertExists()
        composeTestRule.onNodeWithText("MASTER CONSOLE").assertExists()

        // Verify Live Offline-First Sync Card & Sync Action
        composeTestRule.onNodeWithTag("super_admin_live_sync_card", useUnmergedTree = true).assertExists()
        composeTestRule.onNodeWithTag("super_admin_sync_now_button", useUnmergedTree = true).performClick()
        composeTestRule.waitForIdle()
        assertTrue("Sync Now callback should be invoked", syncTriggered)

        // Scroll to tenant card in LazyColumn
        composeTestRule.onNodeWithTag("super_admin_lazy_column")
            .performScrollToNode(hasTestTag("managed_user_card_TL-TEST-1"))
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("TL-TEST-1").assertExists()
        composeTestRule.onNodeWithText("Test Kirana Store").assertExists()
        composeTestRule.onNodeWithText("ACTIVE").assertExists()

        // Toggle user status
        composeTestRule.onNodeWithTag("toggle_status_TL-TEST-1", useUnmergedTree = true).performClick()
        composeTestRule.waitForIdle()
        assertTrue("Status toggle callback should be invoked", statusToggled)

        // Test One-Tap Interactive Module Chip (GST)
        composeTestRule.onNodeWithTag("chip_permission_gst_TL-TEST-1", useUnmergedTree = true).performClick()
        composeTestRule.waitForIdle()
        assertTrue("Interactive permission chip tap should trigger onUpdateUserBenefits", benefitsUpdated)

        // Click Back / Exit
        composeTestRule.onNodeWithTag("super_admin_back_button", useUnmergedTree = true).performClick()
        composeTestRule.waitForIdle()
        assertTrue("Exit Super Admin should be called", backClicked)
    }

    @Test
    fun testBulkSelectionAndBatchOperations() {
        val viewModel = PabaruViewModel()

        // 1. ViewModel bulk toggle activation test
        val testStoreCodes = listOf("TL-7319", "TL-2041")
        viewModel.bulkToggleUserStatus(testStoreCodes, false)
        val suspendedUsers = viewModel.uiState.value.managedUsers.filter { it.storeCode in testStoreCodes }
        assertTrue("All selected users must be deactivated", suspendedUsers.all { !it.isActive })

        viewModel.bulkToggleUserStatus(testStoreCodes, true)
        val reactivatedUsers = viewModel.uiState.value.managedUsers.filter { it.storeCode in testStoreCodes }
        assertTrue("All selected users must be reactivated", reactivatedUsers.all { it.isActive })

        // 2. ViewModel bulk update benefits test
        viewModel.bulkUpdateUserBenefits(
            storeCodes = testStoreCodes,
            isGst = true,
            isRecon = true,
            isInvoice = true,
            isInv = true,
            isCma = true,
            isStaff = true,
            isExport = true,
            isOverride = true
        )
        val updatedUsers = viewModel.uiState.value.managedUsers.filter { it.storeCode in testStoreCodes }
        assertTrue("GST must be unlocked for all bulk users", updatedUsers.all { it.isGstUnlocked })
        assertTrue("Bank Recon must be unlocked for all bulk users", updatedUsers.all { it.isBankReconUnlocked })
        assertTrue("Export must be unlocked for all bulk users", updatedUsers.all { it.isExportUnlocked })

        // 3. Compose UI Bulk Mode & Floating Bottom Action Bar test
        var bulkStatusToggled = false
        var bulkBenefitsApplied = false
        val testList = listOf(
            ManagedUserEntity(
                storeCode = "TL-BULK-1",
                businessName = "Store One",
                ownerName = "Owner One",
                phone = "9800000001",
                email = "one@bulk.in",
                isActive = true
            ),
            ManagedUserEntity(
                storeCode = "TL-BULK-2",
                businessName = "Store Two",
                ownerName = "Owner Two",
                phone = "9800000002",
                email = "two@bulk.in",
                isActive = false
            )
        )

        composeTestRule.setContent {
            SuperAdminScreen(
                managedUsers = testList,
                onToggleUserStatus = { _, _ -> },
                onUpdateUserBenefits = { _, _, _, _, _, _, _, _, _ -> },
                onAddNewUser = {},
                onDeleteUser = {},
                onImpersonateUser = {},
                onExitSuperAdmin = {},
                onBulkToggleUserStatus = { codes, _ ->
                    if (codes.isNotEmpty()) bulkStatusToggled = true
                },
                onBulkUpdateUserBenefits = { codes, _, _, _, _, _, _, _, _ ->
                    if (codes.isNotEmpty()) bulkBenefitsApplied = true
                }
            )
        }

        composeTestRule.waitForIdle()

        // Verify bulk mode is initially off and floating action bar does not exist
        composeTestRule.onNodeWithTag("bulk_action_bar", useUnmergedTree = true).assertDoesNotExist()

        // Scroll to and toggle bulk mode
        composeTestRule.onNodeWithTag("super_admin_lazy_column")
            .performScrollToNode(hasTestTag("toggle_bulk_mode_button"))
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("toggle_bulk_mode_button", useUnmergedTree = true).performClick()
        composeTestRule.waitForIdle()

        // Select All button should exist
        composeTestRule.onNodeWithTag("bulk_select_all_button", useUnmergedTree = true).performClick()
        composeTestRule.waitForIdle()

        // Floating Action Bar should now be displayed
        composeTestRule.onNodeWithTag("bulk_action_bar", useUnmergedTree = true).assertExists()
        composeTestRule.onNodeWithTag("bulk_activate_button", useUnmergedTree = true).assertExists()
        composeTestRule.onNodeWithTag("bulk_suspend_button", useUnmergedTree = true).assertExists()
        composeTestRule.onNodeWithTag("bulk_permissions_button", useUnmergedTree = true).assertExists()

        // Click Bulk Activate Button
        composeTestRule.onNodeWithTag("bulk_activate_button", useUnmergedTree = true).performClick()
        composeTestRule.waitForIdle()
        assertTrue("Bulk activate callback should be called", bulkStatusToggled)

        // Select All again and open Bulk Permissions Dialog
        composeTestRule.onNodeWithTag("bulk_select_all_button", useUnmergedTree = true).performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("bulk_permissions_button", useUnmergedTree = true).performClick()
        composeTestRule.waitForIdle()

        // Verify Dialog and Apply Permissions
        composeTestRule.onNodeWithTag("apply_bulk_permissions_button", useUnmergedTree = true).performClick()
        composeTestRule.waitForIdle()
        assertTrue("Bulk update benefits callback should be called", bulkBenefitsApplied)
    }

    @Test
    fun testAppWideCalculationAndSanitizationEdgeCases() {
        // 1. FinancialEngine: GST Calculation Edge Cases
        val zeroGst = com.example.engine.FinancialEngine.calculateGst(10000L, 0, false)
        org.junit.Assert.assertEquals(0L, zeroGst.cgstPaise)
        org.junit.Assert.assertEquals(0L, zeroGst.sgstPaise)
        org.junit.Assert.assertEquals(10000L, zeroGst.totalPaise)

        val negativeTaxable = com.example.engine.FinancialEngine.calculateGst(-5000L, 18, false)
        org.junit.Assert.assertEquals(0L, negativeTaxable.totalPaise)

        val interstate18 = com.example.engine.FinancialEngine.calculateGst(100000L, 18, true)
        org.junit.Assert.assertEquals(18000L, interstate18.igstPaise)
        org.junit.Assert.assertEquals(0L, interstate18.cgstPaise)
        org.junit.Assert.assertEquals(118000L, interstate18.totalPaise)

        val intrastate18 = com.example.engine.FinancialEngine.calculateGst(100000L, 18, false)
        org.junit.Assert.assertEquals(9000L, intrastate18.cgstPaise)
        org.junit.Assert.assertEquals(9000L, intrastate18.sgstPaise)
        org.junit.Assert.assertEquals(118000L, intrastate18.totalPaise)

        // 2. FinancialEngine: Numeric Input Sanitization Edge Cases
        org.junit.Assert.assertEquals("1250.75", com.example.engine.FinancialEngine.sanitizeDecimalInput("1250.75"))
        org.junit.Assert.assertEquals("1250.7599", com.example.engine.FinancialEngine.sanitizeDecimalInput("1250.75.99"))
        org.junit.Assert.assertEquals("100", com.example.engine.FinancialEngine.sanitizeDecimalInput("abc100xyz"))
        org.junit.Assert.assertEquals("", com.example.engine.FinancialEngine.sanitizeDecimalInput(""))

        org.junit.Assert.assertEquals(125075L, com.example.engine.FinancialEngine.parsePaiseFromRupees("₹ 1,250.75"))
        org.junit.Assert.assertEquals(0L, com.example.engine.FinancialEngine.parsePaiseFromRupees("1,250.75.50"))
        org.junit.Assert.assertEquals(0L, com.example.engine.FinancialEngine.parsePaiseFromRupees("1.5.3"))
        org.junit.Assert.assertEquals(0L, com.example.engine.FinancialEngine.parsePaiseFromRupees("12abc34"))
        org.junit.Assert.assertEquals(1013L, com.example.engine.FinancialEngine.parsePaiseFromRupees("10.125"))
        org.junit.Assert.assertEquals(0L, com.example.engine.FinancialEngine.parsePaiseFromRupees(""))
        org.junit.Assert.assertEquals(0L, com.example.engine.FinancialEngine.parsePaiseFromRupees("-500"))

        // 3. GSTIN Validation Edge Cases
        assertTrue(com.example.engine.FinancialEngine.validateGstin(null, allowBlank = true).isValid)
        assertFalse(com.example.engine.FinancialEngine.validateGstin(null, allowBlank = false).isValid)
        val validChk = com.example.engine.FinancialEngine.gstinCheckChar("27ABCDE1234F1Z")
        assertTrue(com.example.engine.FinancialEngine.validateGstin("27ABCDE1234F1Z$validChk", allowBlank = true).isValid) // valid MH

        // 4. BankReconEngine: parsePaise Edge Cases
        org.junit.Assert.assertEquals(50000L, com.example.engine.BankReconEngine.parsePaise("₹ 500.00"))
        org.junit.Assert.assertEquals(0L, com.example.engine.BankReconEngine.parsePaise(""))
        org.junit.Assert.assertEquals(0L, com.example.engine.BankReconEngine.parsePaise("invalid-text"))

        // 5. Rule 88A Set-off Math Edge Cases
        val setOff = com.example.engine.CmaAndGstEngine.computeRule88ASetOff(
            grossIgst = 10000L,
            grossCgst = 5000L,
            grossSgst = 5000L,
            itcIgst = 15000L,
            itcCgst = 2000L,
            itcSgst = 2000L,
            strategy = com.example.engine.CmaAndGstEngine.Rule88AStrategy.OPTIMAL
        )
        assertTrue(setOff.totalCashPayablePaise >= 0L)
        assertTrue(setOff.totalCarriedForwardItcPaise >= 0L)
    }

    @Test
    fun testLedgerCashBankAndDateBoundaryHardening() {
        val viewModel = PabaruViewModel()
        val initialTxnCount = viewModel.uiState.value.transactions.size

        // 1. Future Date Blocking and Normalization
        val today = com.example.engine.FinancialEngine.getTodayDate()
        assertFalse(com.example.engine.FinancialEngine.isFutureDate(today))
        assertFalse(com.example.engine.FinancialEngine.isFutureDate("2020-01-01"))
        assertTrue(com.example.engine.FinancialEngine.isFutureDate("2099-12-31"))

        val futureValidation = com.example.engine.FinancialEngine.validateTransactionDate("2099-12-31")
        assertFalse("Future date must be blocked", futureValidation.isValid)
        assertTrue(futureValidation.errorMessage?.contains("future") == true)

        val validValidation = com.example.engine.FinancialEngine.validateTransactionDate("01-10-2026")
        assertTrue("Past/today date must be valid", validValidation.isValid)
        org.junit.Assert.assertEquals("2026-10-01", validValidation.normalizedDate)

        // 2. Dual-Entry Contra Transfer (Cash to Bank)
        viewModel.transferBetweenAccounts(
            fromId = "CASH",
            toId = "BNK-01",
            amountRupees = 5000L,
            note = "Cash counter deposit"
        )
        val afterTransferTxns = viewModel.uiState.value.transactions
        org.junit.Assert.assertEquals(initialTxnCount + 2, afterTransferTxns.size)

        val txnOut = afterTransferTxns.find { it.remarks.contains("Cash counter deposit") && it.type == com.example.model.TransactionType.PaymentOut }
        val txnIn = afterTransferTxns.find { it.remarks.contains("Cash counter deposit") && it.type == com.example.model.TransactionType.PaymentIn }
        org.junit.Assert.assertNotNull("Debit side of transfer must exist", txnOut)
        org.junit.Assert.assertNotNull("Credit side of transfer must exist", txnIn)
        org.junit.Assert.assertEquals(500000L, txnOut?.amountPaise)
        org.junit.Assert.assertEquals(500000L, txnIn?.amountPaise)
        org.junit.Assert.assertEquals(com.example.model.PaymentMode.Cash, txnOut?.paymentMode)
        org.junit.Assert.assertEquals(com.example.model.PaymentMode.Bank, txnIn?.paymentMode)

        // 3. Same account and non-positive transfer rejection
        viewModel.transferBetweenAccounts("CASH", "CASH", 5000L, "Invalid same-account")
        org.junit.Assert.assertEquals("Same-account transfer must be rejected", initialTxnCount + 2, viewModel.uiState.value.transactions.size)

        viewModel.transferBetweenAccounts("CASH", "BNK-01", 0L, "Zero transfer")
        org.junit.Assert.assertEquals("Zero amount transfer must be rejected", initialTxnCount + 2, viewModel.uiState.value.transactions.size)

        // 4. Cash Adjustment Guard
        viewModel.adjustCash(0L, true, "Zero adjustment")
        org.junit.Assert.assertEquals("Zero adjustment must be rejected", initialTxnCount + 2, viewModel.uiState.value.transactions.size)

        viewModel.adjustCash(2000L, true, "Cash float addition")
        org.junit.Assert.assertEquals(initialTxnCount + 3, viewModel.uiState.value.transactions.size)
        val adjTxn = viewModel.uiState.value.transactions.first()
        org.junit.Assert.assertEquals(200000L, adjTxn.amountPaise)
        org.junit.Assert.assertEquals(com.example.model.TransactionType.PaymentIn, adjTxn.type)
    }

    @Test
    fun testGstr1PortalFormattingPanIfscAndPhoneValidation() {
        // 1. GSTR-1 Date Formatting (Must not invert DD-MM-YYYY)
        org.junit.Assert.assertEquals("01-10-2026", com.example.engine.GstPortalJsonEngine.formatDateForGst("2026-10-01"))
        org.junit.Assert.assertEquals("01-10-2026", com.example.engine.GstPortalJsonEngine.formatDateForGst("01-10-2026"))
        org.junit.Assert.assertEquals("15-08-2026", com.example.engine.GstPortalJsonEngine.formatDateForGst("15/08/2026"))

        // 2. GSTR-1 Max 16-char Invoice Number Rule
        val longInum = "INV/2026-2027/MAH/000001" // 24 chars
        val sanitized = com.example.engine.GstPortalJsonEngine.sanitizeGstInvoiceNumber(longInum)
        assertTrue("Invoice number must not exceed 16 chars", sanitized.length <= 16)
        assertTrue("Must retain serial suffix 000001", sanitized.endsWith("000001"))

        val specialCharsInum = "INV#2026@001!"
        val cleanInum = com.example.engine.GstPortalJsonEngine.sanitizeGstInvoiceNumber(specialCharsInum)
        org.junit.Assert.assertEquals("INV2026001", cleanInum)

        // 3. State Code Sanitization (2-digit 01-99)
        org.junit.Assert.assertEquals("27", com.example.engine.GstPortalJsonEngine.sanitizeGstStateCode("27"))
        org.junit.Assert.assertEquals("07", com.example.engine.GstPortalJsonEngine.sanitizeGstStateCode("7"))
        org.junit.Assert.assertEquals("27", com.example.engine.GstPortalJsonEngine.sanitizeGstStateCode("Maharashtra", fallback = "27"))

        // 4. PAN Validation
        assertTrue(com.example.engine.FinancialEngine.validatePan("ABCDE1234F").isValid)
        assertTrue(com.example.engine.FinancialEngine.validatePan("AAACS1234M").isValid)
        assertFalse(com.example.engine.FinancialEngine.validatePan("ABC12").isValid) // too short
        assertFalse(com.example.engine.FinancialEngine.validatePan("12345ABCDE").isValid) // wrong format
        assertTrue(com.example.engine.FinancialEngine.validatePan(null, allowBlank = true).isValid)

        // 5. IFSC Validation
        assertTrue(com.example.engine.FinancialEngine.validateIfsc("HDFC0000060").isValid)
        assertTrue(com.example.engine.FinancialEngine.validateIfsc("SBIN0000300").isValid)
        assertFalse(com.example.engine.FinancialEngine.validateIfsc("HDFC1000060").isValid) // 5th char must be '0'
        assertFalse(com.example.engine.FinancialEngine.validateIfsc("HDFC006").isValid) // too short

        // 6. Phone Validation
        assertTrue(com.example.engine.FinancialEngine.validatePhone("9820192837").isValid)
        assertTrue(com.example.engine.FinancialEngine.validatePhone("7890123456").isValid)
        assertFalse(com.example.engine.FinancialEngine.validatePhone("1234567890").isValid) // starts with 1
        assertFalse(com.example.engine.FinancialEngine.validatePhone("98201").isValid) // too short
    }

    @Test
    fun testGstRoundOffParityHsnValidationAndSecurity() {
        // 1. GST Rule 26 Round-off Parity for odd rates
        val oddTaxable = 10555L // ₹105.55
        val interstateGst = com.example.engine.FinancialEngine.calculateGst(oddTaxable, 5, isInterstate = true)
        val intrastateGst = com.example.engine.FinancialEngine.calculateGst(oddTaxable, 5, isInterstate = false)
        org.junit.Assert.assertEquals(
            "Intrastate CGST + SGST must exactly equal interstate total tax",
            interstateGst.igstPaise,
            intrastateGst.cgstPaise + intrastateGst.sgstPaise
        )
        org.junit.Assert.assertEquals(interstateGst.totalPaise, intrastateGst.totalPaise)

        // 2. Statutory HSN Validation (4, 6, or 8 digits only)
        assertTrue(com.example.engine.FinancialEngine.validateHsn("1006").isValid) // 4 digits
        assertTrue(com.example.engine.FinancialEngine.validateHsn("100610").isValid) // 6 digits
        assertTrue(com.example.engine.FinancialEngine.validateHsn("10061010").isValid) // 8 digits
        assertFalse(com.example.engine.FinancialEngine.validateHsn("10").isValid) // 2 digits invalid
        assertFalse(com.example.engine.FinancialEngine.validateHsn("10061").isValid) // 5 digits invalid
        assertFalse(com.example.engine.FinancialEngine.validateHsn("ABCD").isValid) // non-numeric invalid

        // 3. FileProvider Filename Path Traversal Sanitization
        val dangerousFileName = "../../etc/passwd..//invoice.pdf"
        val safeFileName = com.example.engine.ExportEngine.sanitizeFileName(dangerousFileName)
        assertFalse("Filename must not contain path traversal", safeFileName.contains(".."))
        assertFalse("Filename must not contain slashes", safeFileName.contains("/"))

        // 4. Audit Chain: Tampered Replay Rejection
        val legitimateLog = com.example.engine.CryptoEngine.createAuditRecord(
            prevHash = null,
            entityType = "Transaction",
            entityId = "TXN-001",
            action = "VOID",
            details = "Voided legitimate invoice",
            reason = "Customer cancelled",
            actor = "Owner"
        )
        val tamperedLog = legitimateLog.copy(details = "Injected unauthorized ledger change")
        val replayedState = com.example.engine.CryptoEngine.recalculateLedgerState(listOf(tamperedLog))
        assertFalse("Replay from tampered audit chain must be rejected", replayedState.isVerified)
        assertTrue("Tampered transactions must not be accepted into state", replayedState.voidedTransactionIds.isEmpty())

        // 5. Windowed Audit Verification
        val validLogs = mutableListOf<com.example.model.AuditLog>()
        var prevHash: String? = null
        for (i in 1..10) {
            val log = com.example.engine.CryptoEngine.createAuditRecord(
                prevHash = prevHash,
                entityType = "Product",
                entityId = "PROD-$i",
                action = "CREATE",
                details = "Added product item $i",
                reason = "Inventory setup",
                actor = "Owner"
            )
            validLogs.add(log)
            prevHash = log.recordHash
        }
        val windowedResult = com.example.engine.CryptoEngine.verifyAuditChainWindowed(validLogs, windowSize = 5)
        assertTrue("Windowed verification must succeed on valid chain", windowedResult.valid)
    }

    @Test
    fun testToCollectAndToPayUnpaidBillsFeature() {
        val viewModel = PabaruViewModel()
        val initialTxnCount = viewModel.uiState.value.transactions.size

        // Record a credit sale bill
        val creditSale = com.example.model.Transaction(
            id = "TXN-SALE-CREDIT-TEST",
            branchId = viewModel.uiState.value.activeBranchId,
            date = com.example.engine.FinancialEngine.getTodayDate(),
            partyId = "PTY-101",
            amountPaise = 150000L, // ₹ 1,500.00
            type = com.example.model.TransactionType.Sale,
            paymentMode = com.example.model.PaymentMode.Credit,
            invoiceNumber = "INV-TEST-001",
            remarks = "Credit sale of test goods"
        )
        val lineItem = com.example.model.InvoiceLineItem(
            itemId = "PROD-101",
            name = "Organic Cotton Yarn",
            hsnCode = "5205",
            quantity = 1,
            unit = "KG",
            ratePaise = 150000L,
            gstRate = 0,
            taxableAmountPaise = 150000L,
            cgstPaise = 0L,
            sgstPaise = 0L,
            igstPaise = 0L,
            totalPaise = 150000L
        )
        viewModel.recordItemizedInvoice(
            partyId = "PTY-101",
            paymentMode = com.example.model.PaymentMode.Credit,
            type = com.example.model.TransactionType.Sale,
            lineItems = listOf(lineItem),
            remarks = "Credit sale of test goods"
        )
        val postSaleTxnCount = viewModel.uiState.value.transactions.size
        assertTrue(postSaleTxnCount > initialTxnCount)

        // Record partial payment collection of ₹ 500.00 (50000 paise)
        viewModel.recordPayment(
            partyId = "PTY-101",
            amountPaise = 50000L,
            isPaymentIn = true,
            paymentMode = com.example.model.PaymentMode.Cash,
            remarks = "Partial collection for INV-TEST-001"
        )

        val latestTxn = viewModel.uiState.value.transactions.first()
        org.junit.Assert.assertEquals(com.example.model.TransactionType.PaymentIn, latestTxn.type)
        org.junit.Assert.assertEquals(50000L, latestTxn.amountPaise)
        org.junit.Assert.assertEquals("PTY-101", latestTxn.partyId)

        // Record supplier payment out of ₹ 200.00
        viewModel.recordPayment(
            partyId = "PTY-103",
            amountPaise = 20000L,
            isPaymentIn = false,
            paymentMode = com.example.model.PaymentMode.UPI,
            remarks = "Supplier payment for materials"
        )

        val supplierPaymentTxn = viewModel.uiState.value.transactions.first()
        org.junit.Assert.assertEquals(com.example.model.TransactionType.PaymentOut, supplierPaymentTxn.type)
        org.junit.Assert.assertEquals(20000L, supplierPaymentTxn.amountPaise)
        org.junit.Assert.assertEquals("PTY-103", supplierPaymentTxn.partyId)
    }

    @Test
    fun testParsePaiseFromRupeesStrictSanitization() {
        // Unicode non-breaking spaces (\u00A0, \u202f, \u3000) and currency symbols
        org.junit.Assert.assertEquals(125075L, com.example.engine.FinancialEngine.parsePaiseFromRupees("₹\u00A01,250.75"))
        org.junit.Assert.assertEquals(125075L, com.example.engine.FinancialEngine.parsePaiseFromRupees("Rs.\u202F1,250.75"))
        org.junit.Assert.assertEquals(125075L, com.example.engine.FinancialEngine.parsePaiseFromRupees("INR\u30001,250.75"))

        // Scientific notation rejection: must return 0L
        org.junit.Assert.assertEquals(0L, com.example.engine.FinancialEngine.parsePaiseFromRupees("1e3"))
        org.junit.Assert.assertEquals(0L, com.example.engine.FinancialEngine.parsePaiseFromRupees("2.5E4"))

        // Negative values and negative sign prefixes: must return 0L
        org.junit.Assert.assertEquals(0L, com.example.engine.FinancialEngine.parsePaiseFromRupees("-150.00"))
        org.junit.Assert.assertEquals(0L, com.example.engine.FinancialEngine.parsePaiseFromRupees("-₹50"))
    }

    @Test
    fun testRule26StatutoryGstRoundingParity() {
        // Interstate: (taxablePaise * rateBps + 5000L) / 10000L
        val igst1 = com.example.engine.FinancialEngine.calculateGst(100L, 5, isInterstate = true)
        org.junit.Assert.assertEquals(5L, igst1.igstPaise)

        // Intrastate: CGST and SGST must be strictly equal at half the GST rate (2.5% each rounded half-up)
        // For taxable = 100 paise, 2.5% = 2.5 -> rounds half-up to 3 paise CGST + 3 paise SGST = 6 paise
        val gst1 = com.example.engine.FinancialEngine.calculateGst(100L, 5, isInterstate = false)
        org.junit.Assert.assertEquals(gst1.cgstPaise, gst1.sgstPaise)
        org.junit.Assert.assertEquals(6L, gst1.cgstPaise + gst1.sgstPaise)

        // Example: taxable = 35 paise, rate = 5% -> 2.5% of 35 = 0.875 -> rounds half-up to 1 paisa each
        val gst2 = com.example.engine.FinancialEngine.calculateGst(35L, 5, isInterstate = false)
        org.junit.Assert.assertEquals(1L, gst2.cgstPaise)
        org.junit.Assert.assertEquals(1L, gst2.sgstPaise)
        org.junit.Assert.assertEquals(2L, gst2.cgstPaise + gst2.sgstPaise)
    }

    @Test
    fun testNonGstBillCreationAndInventoryDeduction() {
        val viewModel = PabaruViewModel()
        viewModel.addProduct(
            com.example.model.ProductItem(
                id = "PROD-TEST-1",
                name = "Test Item",
                barcode = "123456789",
                sku = "SKU-1",
                hsnCode = "1006",
                gstRate = 0,
                unit = "PCS",
                purchasePricePaise = 5000L,
                salePricePaise = 10000L,
                currentStock = 20,
                reorderLevel = 5
            )
        )
        val prod = viewModel.uiState.value.products.first()
        val initialStock = prod.currentStock

        val lineItem = com.example.model.InvoiceLineItem(
            itemId = prod.id,
            name = prod.name,
            hsnCode = "", // HSN is optional for non-GST
            quantity = 2,
            unit = prod.unit,
            ratePaise = prod.salePricePaise,
            gstRate = 0, // 0% tax forced
            taxableAmountPaise = 2 * prod.salePricePaise,
            cgstPaise = 0L,
            sgstPaise = 0L,
            igstPaise = 0L,
            totalPaise = 2 * prod.salePricePaise
        )

        viewModel.recordItemizedInvoice(
            partyId = "PTY-101",
            paymentMode = com.example.model.PaymentMode.Cash,
            type = com.example.model.TransactionType.NonGstBill,
            lineItems = listOf(lineItem),
            remarks = "Non-GST Commercial Estimate"
        )

        val latestTxn = viewModel.uiState.value.transactions.first()
        org.junit.Assert.assertEquals(com.example.model.TransactionType.NonGstBill, latestTxn.type)
        assertTrue(latestTxn.invoiceNumber.startsWith("BILL/"))

        // Inventory should be properly deducted
        val updatedProd = viewModel.uiState.value.products.first { it.id == prod.id }
        org.junit.Assert.assertEquals(initialStock - 2, updatedProd.currentStock)
    }

    @Test
    fun testStrictNegativeStockInvoiceBlock() {
        val viewModel = PabaruViewModel()
        viewModel.addProduct(
            com.example.model.ProductItem(
                id = "PROD-TEST-1",
                name = "Test Item",
                barcode = "123456789",
                sku = "SKU-1",
                hsnCode = "1006",
                gstRate = 0,
                unit = "PCS",
                purchasePricePaise = 5000L,
                salePricePaise = 10000L,
                currentStock = 20,
                reorderLevel = 5
            )
        )
        val prod = viewModel.uiState.value.products.first()
        val initialStock = prod.currentStock

        // Try to request more than available stock
        val excessiveQty = initialStock + 50
        val lineItem = com.example.model.InvoiceLineItem(
            itemId = prod.id,
            name = prod.name,
            hsnCode = prod.hsnCode,
            quantity = excessiveQty,
            unit = prod.unit,
            ratePaise = prod.salePricePaise,
            gstRate = prod.gstRate,
            taxableAmountPaise = excessiveQty * prod.salePricePaise,
            cgstPaise = 0L,
            sgstPaise = 0L,
            igstPaise = 0L,
            totalPaise = excessiveQty * prod.salePricePaise
        )

        var exceptionThrown = false
        try {
            viewModel.recordItemizedInvoice(
                partyId = "PTY-101",
                paymentMode = com.example.model.PaymentMode.Cash,
                type = com.example.model.TransactionType.Sale,
                lineItems = listOf(lineItem),
                remarks = "Should fail due to insufficient stock"
            )
        } catch (e: IllegalArgumentException) {
            exceptionThrown = true
            assertTrue(e.message?.contains("Insufficient stock") == true)
        }

        assertTrue("Expected IllegalArgumentException for negative stock", exceptionThrown)

        // Verify product stock remains completely untouched
        val productAfter = viewModel.uiState.value.products.first { it.id == prod.id }
        org.junit.Assert.assertEquals(initialStock, productAfter.currentStock)
    }

    @Test
    fun testStockAdjustmentAndMovementAudit() {
        val viewModel = PabaruViewModel()
        viewModel.addProduct(
            com.example.model.ProductItem(
                id = "PROD-TEST-1",
                name = "Test Item",
                barcode = "123456789",
                sku = "SKU-1",
                hsnCode = "1006",
                gstRate = 0,
                unit = "PCS",
                purchasePricePaise = 5000L,
                salePricePaise = 10000L,
                currentStock = 20,
                reorderLevel = 5
            )
        )
        val prod = viewModel.uiState.value.products.first()
        val initialStock = prod.currentStock

        // Adjust -3 units due to DAMAGE
        viewModel.recordStockAdjustment(
            productId = prod.id,
            locationId = "LOC-MAIN",
            quantityDelta = -3,
            reason = com.example.model.StockAdjustmentReason.DAMAGE,
            remarks = "Water damage in warehouse shelf"
        )

        val updatedProd = viewModel.uiState.value.products.first { it.id == prod.id }
        org.junit.Assert.assertEquals(initialStock - 3, updatedProd.currentStock)

        // Verify adjustment log recorded
        val latestAdj = viewModel.uiState.value.stockAdjustments.first()
        org.junit.Assert.assertEquals(prod.id, latestAdj.productId)
        org.junit.Assert.assertEquals(-3, latestAdj.quantityDelta)
        org.junit.Assert.assertEquals(com.example.model.StockAdjustmentReason.DAMAGE, latestAdj.reason)

        // Verify movement log recorded
        val latestMov = viewModel.uiState.value.stockMovements.first()
        org.junit.Assert.assertEquals(prod.id, latestMov.productId)
        org.junit.Assert.assertEquals("ADJUSTMENT", latestMov.movementType)
        org.junit.Assert.assertEquals(-3, latestMov.quantityDelta)
        org.junit.Assert.assertEquals(initialStock - 3, latestMov.balanceAfter)
    }

    @Test
    fun testPurchaseBillInwardIncrementsStock() {
        val viewModel = PabaruViewModel()
        viewModel.addProduct(
            com.example.model.ProductItem(
                id = "PROD-TEST-1",
                name = "Test Item",
                barcode = "123456789",
                sku = "SKU-1",
                hsnCode = "1006",
                gstRate = 0,
                unit = "PCS",
                purchasePricePaise = 5000L,
                salePricePaise = 10000L,
                currentStock = 20,
                reorderLevel = 5
            )
        )
        val prod = viewModel.uiState.value.products.first()
        val initialStock = prod.currentStock

        val purchaseItem = com.example.model.InvoiceLineItem(
            itemId = prod.id,
            name = prod.name,
            hsnCode = prod.hsnCode,
            quantity = 25,
            ratePaise = prod.purchasePricePaise,
            gstRate = prod.gstRate,
            totalPaise = 25 * prod.purchasePricePaise
        )

        viewModel.recordPurchaseBill(
            supplierPartyId = "PTY-103",
            billNumber = "BILL-TEST-999",
            billDate = "2026-11-06",
            paymentMode = com.example.model.PaymentMode.Credit,
            purchasedItems = listOf(purchaseItem),
            remarks = "Stock replenishment"
        )

        val updatedProd = viewModel.uiState.value.products.first { it.id == prod.id }
        org.junit.Assert.assertEquals(initialStock + 25, updatedProd.currentStock)

        // Verify movement log
        val purchaseMov = viewModel.uiState.value.stockMovements.first { it.referenceTxnId?.startsWith("TXN-PUR-") == true }
        org.junit.Assert.assertEquals(prod.id, purchaseMov.productId)
        org.junit.Assert.assertEquals("PURCHASE_IN", purchaseMov.movementType)
        org.junit.Assert.assertEquals(25, purchaseMov.quantityDelta)
    }

    @Test
    fun testInventoryScreenUiButtons() {
        val viewModel = PabaruViewModel()
        composeTestRule.setContent {
            com.example.ui.screens.InventoryScreen(
                uiState = viewModel.uiState.value,
                viewModel = viewModel
            )
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("inventory_add_item_btn").assertExists()
        composeTestRule.onNodeWithTag("inventory_purchase_bill_btn").assertExists()
        composeTestRule.onNodeWithTag("inventory_adjust_btn").assertExists()
        composeTestRule.onNodeWithTag("inventory_search_input").assertExists()
    }

    @Test
    fun testDashboardScreenLowStockBadgeCollapsibleAndLinks() {
        val lowProd1 = com.example.model.ProductItem(
            id = "PRD-1",
            name = "Low Item 1",
            currentStock = 2,
            reorderLevel = 10,
            salePricePaise = 10000L
        )
        val lowProd2 = com.example.model.ProductItem(
            id = "PRD-2",
            name = "Low Item 2",
            currentStock = 0,
            reorderLevel = 10,
            salePricePaise = 15000L
        )
        val uiState = PabaruUiState(
            products = listOf(lowProd1, lowProd2),
            lowStockThreshold = 5
        )

        var navigatedWithFilter = false
        composeTestRule.setContent {
            com.example.ui.DashboardScreen(
                uiState = uiState,
                onNavigateToInvoice = {},
                onNavigateToDaybook = {},
                onNavigateToInventory = { filterLow -> navigatedWithFilter = filterLow },
                onVoid = {}
            )
        }

        composeTestRule.waitForIdle()
        // Low stock card must exist
        composeTestRule.onNodeWithTag("dashboard_low_stock_card").assertExists()
        // Toggle expand button must exist because count > 1
        composeTestRule.onNodeWithTag("btn_toggle_low_stock_expand").assertExists()
        // Click View in Inventory button
        composeTestRule.onNodeWithTag("btn_view_inventory_low_stock").performClick()
        composeTestRule.waitForIdle()
        assertTrue(navigatedWithFilter)
    }

    @Test
    fun testDashboardScreenHidesWhenNoLowStock() {
        val normalProd = com.example.model.ProductItem(
            id = "PRD-OK",
            name = "Healthy Stock Item",
            currentStock = 50,
            reorderLevel = 10,
            salePricePaise = 10000L
        )
        val uiState = PabaruUiState(
            products = listOf(normalProd),
            lowStockThreshold = 5
        )

        composeTestRule.setContent {
            com.example.ui.DashboardScreen(
                uiState = uiState,
                onNavigateToInvoice = {},
                onNavigateToDaybook = {},
                onNavigateToInventory = {},
                onVoid = {}
            )
        }

        composeTestRule.waitForIdle()
        // Low stock card must NOT exist when all stock is healthy
        composeTestRule.onNodeWithTag("dashboard_low_stock_card").assertDoesNotExist()
    }

    @Test
    fun testFinancialYearDateParsing() {
        // Apr-Dec should belong to FY Year-(Year+1)
        val fyApr = com.example.engine.FinancialEngine.getFinancialYearForDate("2025-05-15")
        org.junit.Assert.assertEquals("FY 2025-26", fyApr)

        // Jan-Mar should belong to FY (Year-1)-Year
        val fyJan = com.example.engine.FinancialEngine.getFinancialYearForDate("2026-02-10")
        org.junit.Assert.assertEquals("FY 2025-26", fyJan)

        // Boundary tests
        assertTrue(com.example.engine.FinancialEngine.isDateInFinancialYear("2025-04-01", "FY 2025-26"))
        assertTrue(com.example.engine.FinancialEngine.isDateInFinancialYear("2026-03-31", "FY 2025-26"))
        assertFalse(com.example.engine.FinancialEngine.isDateInFinancialYear("2025-03-31", "FY 2025-26"))
        assertFalse(com.example.engine.FinancialEngine.isDateInFinancialYear("2026-04-01", "FY 2025-26"))
    }

    @Test
    fun testCalculateFyFinancialsAndGstSummary() {
        val txnSale = com.example.model.Transaction(
            id = "TXN-S1",
            branchId = "BR-MAIN",
            date = "2025-06-10",
            partyId = "PTY-1",
            amountPaise = 100000L,
            type = com.example.model.TransactionType.Sale,
            paymentMode = com.example.model.PaymentMode.Cash,
            items = listOf(
                com.example.model.InvoiceLineItem(
                    itemId = "ITM-1",
                    name = "Widget A",
                    quantityMilli = 10000L,
                    ratePaise = 10000L,
                    gstRateBps = 1800,
                    taxableAmountPaise = 100000L,
                    cgstPaise = 9000L,
                    sgstPaise = 9000L,
                    igstPaise = 0L,
                    totalPaise = 118000L
                )
            )
        )
        val txnPur = com.example.model.Transaction(
            id = "TXN-P1",
            branchId = "BR-MAIN",
            date = "2025-07-15",
            partyId = null,
            amountPaise = 40000L,
            type = com.example.model.TransactionType.Purchase,
            paymentMode = com.example.model.PaymentMode.Cash,
            items = listOf(
                com.example.model.InvoiceLineItem(
                    itemId = "ITM-1",
                    name = "Widget A Raw",
                    quantityMilli = 10000L,
                    ratePaise = 4000L,
                    gstRateBps = 1800,
                    taxableAmountPaise = 40000L,
                    cgstPaise = 3600L,
                    sgstPaise = 3600L,
                    igstPaise = 0L,
                    totalPaise = 47200L
                )
            )
        )

        val partyB2b = com.example.model.Party(
            id = "PTY-1",
            name = "Alpha Corp",
            phone = "9876543210",
            address = "Mumbai",
            gstin = "27ABCDE1234F1Z5",
            pan = "ABCDE1234F",
            stateCode = "27",
            openingBalancePaise = 0L,
            openingBalanceType = com.example.model.BalanceType.Receivable
        )

        val uiState = PabaruUiState(
            transactions = listOf(txnSale, txnPur),
            parties = listOf(partyB2b)
        )

        val fin = com.example.engine.FinancialEngine.calculateFyFinancials(uiState, "FY 2025-26")
        org.junit.Assert.assertEquals(100000L, fin.salesRevenuePaise)
        org.junit.Assert.assertEquals(40000L, fin.purchasesCogsPaise)
        org.junit.Assert.assertEquals(60000L, fin.grossProfitPaise)
        org.junit.Assert.assertEquals(60000L, fin.netProfitPaise)

        val gst = com.example.engine.FinancialEngine.calculateFyGstSummary(listOf(txnSale, txnPur), listOf(partyB2b), "FY 2025-26")
        org.junit.Assert.assertEquals(100000L, gst.totalTaxableSalesPaise)
        org.junit.Assert.assertEquals(100000L, gst.b2bTaxableSalesPaise)
        org.junit.Assert.assertEquals(1, gst.b2bInvoiceCount)
        org.junit.Assert.assertEquals(18000L, gst.totalOutputTaxPaise)
        org.junit.Assert.assertEquals(7200L, gst.totalEligibleItcPaise)
        org.junit.Assert.assertEquals(10800L, gst.netGstPayablePaise)
    }

    @Test
    fun testReportsAndFinancialsScreenUi() {
        val uiState = PabaruUiState()
        var backClicked = false

        composeTestRule.setContent {
            com.example.ui.screens.ReportsAndFinancialsScreen(
                uiState = uiState,
                initialTab = 0,
                onBack = { backClicked = true }
            )
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("btn_export_pdf_report").assertExists()
        composeTestRule.onNodeWithTag("btn_export_csv_report").assertExists()
        composeTestRule.onNodeWithTag("report_tab_0").assertExists()
        composeTestRule.onNodeWithTag("report_tab_1").assertExists()
        composeTestRule.onNodeWithTag("report_tab_2").assertExists()

        // Switch to GST tab
        composeTestRule.onNodeWithTag("report_tab_2").performClick()
        composeTestRule.waitForIdle()

        // Click back
        composeTestRule.onNodeWithTag("reports_back_btn").performClick()
        composeTestRule.waitForIdle()
        assertTrue(backClicked)
    }

    @Test
    fun testGstr9QuarterlyAggregation() {
        val partyB2b = com.example.model.Party(
            id = "PTY-B2B-1",
            name = "Beta Enterprise",
            phone = "9876543210",
            address = "Pune",
            gstin = "27ABCDE1234F1Z5",
            pan = "ABCDE1234F",
            stateCode = "27",
            openingBalancePaise = 0L,
            openingBalanceType = com.example.model.BalanceType.Receivable
        )

        // Q1 Sale: 2025-05-15 (Apr-Jun) B2B
        val q1Sale = com.example.model.Transaction(
            id = "TXN-Q1",
            branchId = "BR-MAIN",
            date = "2025-05-15",
            partyId = partyB2b.id,
            amountPaise = 100000L,
            type = com.example.model.TransactionType.Sale,
            paymentMode = com.example.model.PaymentMode.Cash,
            items = listOf(
                com.example.model.InvoiceLineItem(
                    itemId = "ITM-1",
                    name = "Goods 1",
                    quantityMilli = 10000L,
                    ratePaise = 10000L,
                    gstRateBps = 1800,
                    taxableAmountPaise = 100000L,
                    cgstPaise = 9000L,
                    sgstPaise = 9000L,
                    igstPaise = 0L,
                    totalPaise = 118000L
                )
            )
        )

        // Q2 Sale: 2025-08-20 (Jul-Sep) B2C (No party)
        val q2Sale = com.example.model.Transaction(
            id = "TXN-Q2",
            branchId = "BR-MAIN",
            date = "2025-08-20",
            partyId = null,
            amountPaise = 50000L,
            type = com.example.model.TransactionType.Sale,
            paymentMode = com.example.model.PaymentMode.Cash,
            items = listOf(
                com.example.model.InvoiceLineItem(
                    itemId = "ITM-2",
                    name = "Goods 2",
                    quantityMilli = 5000L,
                    ratePaise = 10000L,
                    gstRateBps = 1800,
                    taxableAmountPaise = 50000L,
                    cgstPaise = 4500L,
                    sgstPaise = 4500L,
                    igstPaise = 0L,
                    totalPaise = 59000L
                )
            )
        )

        // Q3 Purchase: 2025-11-10 (Oct-Dec) Inward for ITC
        val q3Pur = com.example.model.Transaction(
            id = "TXN-Q3",
            branchId = "BR-MAIN",
            date = "2025-11-10",
            partyId = null,
            amountPaise = 40000L,
            type = com.example.model.TransactionType.Purchase,
            paymentMode = com.example.model.PaymentMode.Cash,
            items = listOf(
                com.example.model.InvoiceLineItem(
                    itemId = "ITM-RAW",
                    name = "Raw Material",
                    quantityMilli = 4000L,
                    ratePaise = 10000L,
                    gstRateBps = 1800,
                    taxableAmountPaise = 40000L,
                    cgstPaise = 3600L,
                    sgstPaise = 3600L,
                    igstPaise = 0L,
                    totalPaise = 47200L
                )
            )
        )

        // Q4 Sale: 2026-02-14 (Jan-Mar) B2B
        val q4Sale = com.example.model.Transaction(
            id = "TXN-Q4",
            branchId = "BR-MAIN",
            date = "2026-02-14",
            partyId = partyB2b.id,
            amountPaise = 80000L,
            type = com.example.model.TransactionType.Sale,
            paymentMode = com.example.model.PaymentMode.Cash,
            items = listOf(
                com.example.model.InvoiceLineItem(
                    itemId = "ITM-3",
                    name = "Goods 3",
                    quantityMilli = 8000L,
                    ratePaise = 10000L,
                    gstRateBps = 1800,
                    taxableAmountPaise = 80000L,
                    cgstPaise = 7200L,
                    sgstPaise = 7200L,
                    igstPaise = 0L,
                    totalPaise = 94400L
                )
            )
        )

        val txns = listOf(q1Sale, q2Sale, q3Pur, q4Sale)
        val parties = listOf(partyB2b)

        val gstr9 = com.example.engine.FinancialEngine.calculateGstr9AnnualSummary(txns, parties, "FY 2025-26")

        // 4 quarters aggregated
        org.junit.Assert.assertEquals(4, gstr9.quarters.size)
        val q1 = gstr9.quarters.first { it.quarterKey == "Q1" }
        val q2 = gstr9.quarters.first { it.quarterKey == "Q2" }
        val q3 = gstr9.quarters.first { it.quarterKey == "Q3" }
        val q4 = gstr9.quarters.first { it.quarterKey == "Q4" }

        org.junit.Assert.assertEquals(100000L, q1.taxableSalesPaise)
        org.junit.Assert.assertEquals(100000L, q1.b2bTaxablePaise)
        org.junit.Assert.assertEquals(18000L, q1.totalOutputTaxPaise)

        org.junit.Assert.assertEquals(50000L, q2.taxableSalesPaise)
        org.junit.Assert.assertEquals(50000L, q2.b2cTaxablePaise)
        org.junit.Assert.assertEquals(9000L, q2.totalOutputTaxPaise)

        org.junit.Assert.assertEquals(40000L, q3.taxablePurchasesPaise)
        org.junit.Assert.assertEquals(7200L, q3.totalItcPaise)

        org.junit.Assert.assertEquals(80000L, q4.taxableSalesPaise)
        org.junit.Assert.assertEquals(80000L, q4.b2bTaxablePaise)
        org.junit.Assert.assertEquals(14400L, q4.totalOutputTaxPaise)

        // Annual Totals: 100000 + 50000 + 80000 = 230000
        org.junit.Assert.assertEquals(230000L, gstr9.annualTaxableSalesPaise)
        // Output Tax: 18000 + 9000 + 14400 = 41400
        org.junit.Assert.assertEquals(41400L, gstr9.annualTotalOutputTaxPaise)
        // Total ITC: 7200
        org.junit.Assert.assertEquals(7200L, gstr9.annualTotalItcPaise)
        // Net Tax: 41400 - 7200 = 34200
        org.junit.Assert.assertEquals(34200L, gstr9.annualNetTaxPayablePaise)

        // Statutory Table 4
        org.junit.Assert.assertEquals(180000L, gstr9.table4Details.b2bTaxablePaise)
        org.junit.Assert.assertEquals(50000L, gstr9.table4Details.b2cTaxablePaise)
        org.junit.Assert.assertEquals(230000L, gstr9.table4Details.totalTaxablePaise)
        org.junit.Assert.assertEquals(41400L, gstr9.table4Details.grandTotalTaxPaise)

        // Statutory Table 6
        org.junit.Assert.assertEquals(40000L, gstr9.table6Details.inwardSuppliesTaxablePaise)
        org.junit.Assert.assertEquals(7200L, gstr9.table6Details.totalItcPaise)

        // Statutory Table 9
        org.junit.Assert.assertEquals(41400L, gstr9.table9Details.taxPayablePaise)
        org.junit.Assert.assertEquals(7200L, gstr9.table9Details.taxPaidItcPaise)
        org.junit.Assert.assertEquals(34200L, gstr9.table9Details.balancePayablePaise)
    }

    @Test
    fun testReportsAndFinancialsScreenGstr9TabUi() {
        val uiState = PabaruUiState()

        composeTestRule.setContent {
            com.example.ui.screens.ReportsAndFinancialsScreen(
                uiState = uiState,
                initialTab = 3
            )
        }

        composeTestRule.waitForIdle()

        // Tab 3 elements should exist
        composeTestRule.onNodeWithTag("report_tab_3").assertExists()
        composeTestRule.onNodeWithTag("gstr9_overview_card").assertExists()
        composeTestRule.onNodeWithTag("btn_gstr9_pdf").assertExists()
        composeTestRule.onNodeWithTag("btn_gstr9_csv").assertExists()

        // Scroll to subsequent cards in the LazyColumn
        composeTestRule.onNodeWithTag("reports_lazy_column").performScrollToIndex(1)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("gstr9_quarterly_matrix_card").assertExists()

        composeTestRule.onNodeWithTag("reports_lazy_column").performScrollToIndex(2)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("gstr9_table4_card").assertExists()

        composeTestRule.onNodeWithTag("reports_lazy_column").performScrollToIndex(3)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("gstr9_table6_card").assertExists()

        composeTestRule.onNodeWithTag("reports_lazy_column").performScrollToIndex(4)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("gstr9_table9_card").assertExists()
    }

    @Test
    fun testMultiStepProfitAndLossStatement() {
        // 1. Gross Sale: 2000.00
        val txnSale = com.example.model.Transaction(
            id = "TXN-MS-S1",
            branchId = "BR-MAIN",
            date = "2025-06-15",
            partyId = "PTY-1",
            amountPaise = 200000L,
            type = com.example.model.TransactionType.Sale,
            paymentMode = com.example.model.PaymentMode.Cash
        )

        // 2. Sales Return (Credit Note): 200.00
        val txnReturn = com.example.model.Transaction(
            id = "TXN-MS-CN1",
            branchId = "BR-MAIN",
            date = "2025-06-20",
            partyId = "PTY-1",
            amountPaise = 20000L,
            type = com.example.model.TransactionType.CreditNote,
            paymentMode = com.example.model.PaymentMode.Cash,
            remarks = "Customer return damaged goods"
        )

        // 3. Purchase (COGS): 600.00
        val txnPur = com.example.model.Transaction(
            id = "TXN-MS-P1",
            branchId = "BR-MAIN",
            date = "2025-07-01",
            partyId = "PTY-SUPP",
            amountPaise = 60000L,
            type = com.example.model.TransactionType.Purchase,
            paymentMode = com.example.model.PaymentMode.Cash
        )

        // 4. Direct Expense: 100.00
        val txnDirectExp = com.example.model.Transaction(
            id = "TXN-MS-DE1",
            branchId = "BR-MAIN",
            date = "2025-07-05",
            partyId = null,
            amountPaise = 10000L,
            type = com.example.model.TransactionType.Expense,
            expenseCategory = com.example.model.ExpenseCategory.Direct,
            paymentMode = com.example.model.PaymentMode.Cash,
            remarks = "Factory freight inward"
        )

        // 5. Operating Expense (Admin/Rent): 150.00
        val txnRent = com.example.model.Transaction(
            id = "TXN-MS-OE1",
            branchId = "BR-MAIN",
            date = "2025-07-10",
            partyId = null,
            amountPaise = 15000L,
            type = com.example.model.TransactionType.Expense,
            expenseCategory = com.example.model.ExpenseCategory.Indirect,
            paymentMode = com.example.model.PaymentMode.Bank,
            remarks = "Office shop rent"
        )

        // 6. Operating Expense (Salaries): 250.00
        val txnSalaries = com.example.model.Transaction(
            id = "TXN-MS-OE2",
            branchId = "BR-MAIN",
            date = "2025-07-12",
            partyId = null,
            amountPaise = 25000L,
            type = com.example.model.TransactionType.Expense,
            expenseCategory = com.example.model.ExpenseCategory.Indirect,
            paymentMode = com.example.model.PaymentMode.Bank,
            remarks = "Staff salary payroll"
        )

        // 7. Non-Operating Income (Interest): 50.00
        val txnInterest = com.example.model.Transaction(
            id = "TXN-MS-NOI1",
            branchId = "BR-MAIN",
            date = "2025-07-20",
            partyId = null,
            amountPaise = 5000L,
            type = com.example.model.TransactionType.PaymentIn,
            paymentMode = com.example.model.PaymentMode.Bank,
            remarks = "Bank FD Interest yield"
        )

        // 8. Non-Operating Expense (Loan interest): 30.00
        val txnLoanInterest = com.example.model.Transaction(
            id = "TXN-MS-NOE1",
            branchId = "BR-MAIN",
            date = "2025-07-25",
            partyId = null,
            amountPaise = 3000L,
            type = com.example.model.TransactionType.Expense,
            expenseCategory = com.example.model.ExpenseCategory.Indirect,
            paymentMode = com.example.model.PaymentMode.Bank,
            remarks = "Bank CC Loan interest"
        )

        val uiState = PabaruUiState(
            transactions = listOf(txnSale, txnReturn, txnPur, txnDirectExp, txnRent, txnSalaries, txnInterest, txnLoanInterest)
        )

        // Multi-Step P&L calculation with tax provision = 120.00 (12000 paise)
        val statement = com.example.engine.FinancialEngine.calculateFyFinancials(uiState, "FY 2025-26", taxProvisionPaise = 12000L)

        // 1. Net Sales = 200,000 - 20,000 = 180,000
        org.junit.Assert.assertEquals(200000L, statement.grossSalesPaise)
        org.junit.Assert.assertEquals(20000L, statement.salesReturnsPaise)
        org.junit.Assert.assertEquals(180000L, statement.netSalesPaise)

        // 2. COGS = 60,000 + 10,000 = 70,000; Gross Profit = 180,000 - 70,000 = 110,000
        org.junit.Assert.assertEquals(70000L, statement.totalCogsPaise)
        org.junit.Assert.assertEquals(110000L, statement.grossProfitPaise)
        org.junit.Assert.assertEquals(61.11, statement.grossProfitMarginPercent, 0.1)

        // 3. Operating Expenses = 15,000 + 25,000 = 40,000; Operating Profit = 110,000 - 40,000 = 70,000
        org.junit.Assert.assertEquals(15000L, statement.operatingExpensesAdminPaise)
        org.junit.Assert.assertEquals(25000L, statement.operatingExpensesSalariesPaise)
        org.junit.Assert.assertEquals(40000L, statement.totalOperatingExpensesPaise)
        org.junit.Assert.assertEquals(70000L, statement.operatingProfitPaise)
        org.junit.Assert.assertEquals(38.89, statement.operatingProfitMarginPercent, 0.1)

        // 4. Non-Operating items; EBT = 70,000 + 5,000 - 3,000 = 72,000
        org.junit.Assert.assertEquals(5000L, statement.nonOperatingIncomePaise)
        org.junit.Assert.assertEquals(3000L, statement.nonOperatingExpensesPaise)
        org.junit.Assert.assertEquals(72000L, statement.earningsBeforeTaxEbtPaise)

        // 5. Taxes & Net Profit; Net Profit = 72,000 - 12,000 = 60,000
        org.junit.Assert.assertEquals(12000L, statement.incomeTaxProvisionPaise)
        org.junit.Assert.assertEquals(60000L, statement.netProfitPaise)
        org.junit.Assert.assertEquals(33.33, statement.netProfitMarginPercent, 0.1)
    }

    @Test
    fun testMultiStepProfitAndLossScreenUi() {
        val uiState = PabaruUiState()

        composeTestRule.setContent {
            com.example.ui.screens.ReportsAndFinancialsScreen(
                uiState = uiState,
                initialTab = 0
            )
        }

        composeTestRule.waitForIdle()

        // Verify Tab 0 multi-step cards exist by scrolling through LazyColumn
        composeTestRule.onNodeWithTag("reports_lazy_column").performScrollToIndex(1)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("pl_net_sales_card").assertExists()

        composeTestRule.onNodeWithTag("reports_lazy_column").performScrollToIndex(2)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("pl_gross_profit_card").assertExists()

        // Scroll and check operating income & EBT cards
        composeTestRule.onNodeWithTag("reports_lazy_column").performScrollToIndex(3)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("pl_operating_income_card").assertExists()

        composeTestRule.onNodeWithTag("reports_lazy_column").performScrollToIndex(4)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("pl_ebt_card").assertExists()

        composeTestRule.onNodeWithTag("reports_lazy_column").performScrollToIndex(5)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("pl_net_profit_card").assertExists()
        composeTestRule.onNodeWithTag("input_income_tax").assertExists()

        // Test manual tax input
        composeTestRule.onNodeWithTag("input_income_tax").performTextInput("500")
        composeTestRule.waitForIdle()
    }

    @Test
    fun testCmaRbiMpbfCalculations() {
        // Setup financial parameters
        val year = com.example.engine.CmaProjectEngine.CmaYearFinancials(
            stage = com.example.engine.CmaProjectEngine.CmaYearStage.CurrentYearEstimate,
            fyLabel = "FY 2025-26",
            stageLabel = "Estimate",
            grossSalesPaise = 1000000000L, // ₹ 1 Crore
            salesReturnsAndDeductionsPaise = 0L,
            cashAndBankPaise = 50000000L, // ₹ 5 L
            sundryDebtorsPaise = 150000000L, // ₹ 15 L
            inventoryStockPaise = 200000000L, // ₹ 20 L
            advancesToSuppliersPaise = 0L,
            otherCurrentAssetsPaise = 0L,
            sundryCreditorsPaise = 100000000L, // ₹ 10 L
            otherCurrentLiabilitiesPaise = 20000000L, // ₹ 2 L
            provisionsPaise = 0L,
            existingBankCcLimitPaise = 150000000L // ₹ 15 L
        )

        // Total Current Assets (TCA) = 5L + 15L + 20L = ₹ 40 Lakhs (400,000,000 paise)
        org.junit.Assert.assertEquals(400000000L, year.totalCurrentAssetsPaise)

        // Other Current Liabilities (OCL) = 10L + 2L = ₹ 12 Lakhs (120,000,000 paise)
        org.junit.Assert.assertEquals(120000000L, year.currentLiabilitiesOtherThanBankPaise)

        // Working Capital Gap (WCG = TCA - OCL) = 40L - 12L = ₹ 28 Lakhs (280,000,000 paise)
        org.junit.Assert.assertEquals(280000000L, year.workingCapitalGapPaise)

        // 1. Tandon Committee Method 1:
        // Min NWC = 25% of WCG = 28L * 0.25 = ₹ 7 Lakhs (70,000,000 paise)
        // MPBF 1 = 75% of WCG = 28L * 0.75 = ₹ 21 Lakhs (210,000,000 paise)
        org.junit.Assert.assertEquals(70000000L, year.tandonMethod1MinNwcPaise)
        org.junit.Assert.assertEquals(210000000L, year.mpbfTandonMethod1Paise)

        // 2. Tandon Committee Method 2 (RBI Mandate):
        // Min NWC = 25% of TCA = 40L * 0.25 = ₹ 10 Lakhs (100,000,000 paise)
        // MPBF 2 = 75% TCA - OCL = (40L * 0.75) - 12L = 30L - 12L = ₹ 18 Lakhs (180,000,000 paise)
        org.junit.Assert.assertEquals(100000000L, year.tandonMethod2MinNwcPaise)
        org.junit.Assert.assertEquals(180000000L, year.mpbfTandonMethod2Paise)

        // 3. Nayak Committee MSME Turnover Method:
        // Total WC Requirement = 25% of Turnover = ₹ 25 Lakhs (250,000,000 paise)
        // Promoter Margin = 5% of Turnover = ₹ 5 Lakhs (50,000,000 paise)
        // Permissible Bank Finance = 20% of Turnover = ₹ 20 Lakhs (200,000,000 paise)
        org.junit.Assert.assertEquals(250000000L, year.nayakTotalWcRequirementPaise)
        org.junit.Assert.assertEquals(50000000L, year.nayakPromoterMarginPaise)
        org.junit.Assert.assertEquals(200000000L, year.nayakCommitteeWorkingCapitalPaise)
    }

    @Test
    fun testCmaFundsFlowSchedule() {
        val dossier = com.example.engine.CmaProjectEngine.createInitialDossier(baseYear = 2024)
        val fundsFlow = com.example.engine.CmaProjectEngine.computeFundsFlowSchedule(dossier)

        org.junit.Assert.assertEquals(4, fundsFlow.size)
        fundsFlow.forEach { yr ->
            org.junit.Assert.assertEquals(
                "Total Sources - Total Applications must equal Net Surplus/Deficit",
                yr.totalSourcesPaise - yr.totalApplicationsPaise,
                yr.netSurplusOrDeficitPaise
            )
            org.junit.Assert.assertEquals(
                "Closing NWC - Opening NWC must equal ΔNWC",
                yr.closingNwcPaise - yr.openingNwcPaise,
                yr.changeInNwcPaise
            )
        }
    }

    @Test
    fun testCmaTermLoanAmortizationSchedule() {
        // Loan ₹ 30 Lakhs, 9.5% p.a., 60 months tenor, 6 months moratorium
        val schedule = com.example.engine.CmaProjectEngine.calculateTermLoanSchedule(
            principalPaise = 300000000L,
            annualRatePercent = 9.5,
            tenorMonths = 60,
            moratoriumMonths = 6
        )

        org.junit.Assert.assertEquals(60, schedule.monthlySchedule.size)
        org.junit.Assert.assertTrue("Monthly EMI must be positive", schedule.monthlyEmiPaise > 0L)
        org.junit.Assert.assertTrue("Total interest must be positive", schedule.totalInterestPayablePaise > 0L)

        // Month 1 to 6 should be Moratorium (principal repaid is 0)
        for (m in 0 until 6) {
            val month = schedule.monthlySchedule[m]
            org.junit.Assert.assertTrue("Month ${m + 1} must be moratorium", month.isMoratorium)
            org.junit.Assert.assertEquals(0L, month.principalPaise)
            org.junit.Assert.assertTrue("Interest must be paid in moratorium", month.interestPaise > 0L)
        }

        // Final month closing balance must be 0
        val lastMonth = schedule.monthlySchedule.last()
        org.junit.Assert.assertEquals(0L, lastMonth.closingPrincipalPaise)

        // Annual summaries should cover 5 years
        org.junit.Assert.assertEquals(5, schedule.annualSummaries.size)
        org.junit.Assert.assertEquals(0L, schedule.annualSummaries.last().closingPrincipalPaise)
    }

    @Test
    fun testCmaBankingRatiosRbiBenchmarks() {
        val year = com.example.engine.CmaProjectEngine.CmaYearFinancials(
            stage = com.example.engine.CmaProjectEngine.CmaYearStage.CurrentYearEstimate,
            fyLabel = "FY 2025-26",
            stageLabel = "Estimate",
            grossSalesPaise = 1000000000L,
            salesReturnsAndDeductionsPaise = 0L,
            cashAndBankPaise = 50000000L,
            sundryDebtorsPaise = 150000000L,
            inventoryStockPaise = 100000000L,
            sundryCreditorsPaise = 80000000L,
            otherCurrentLiabilitiesPaise = 20000000L,
            existingBankCcLimitPaise = 100000000L,
            shareCapitalPaise = 200000000L,
            reservesAndSurplusPaise = 100000000L,
            termLoanOutstandingPaise = 150000000L,
            grossFixedAssetsPaise = 250000000L,
            accumulatedDepreciationPaise = 50000000L
        )

        // TCA = 50L + 150L + 100L = 300L paise (₹ 30 L)
        // TCL = 80L + 20L + 100L = 200L paise (₹ 20 L)
        // Current Ratio = 300 / 200 = 1.50 (Benchmark >= 1.33)
        org.junit.Assert.assertEquals(1.50, year.currentRatio, 0.01)

        // Quick Ratio = (300 - 100) / 200 = 1.00 (Benchmark >= 1.00)
        org.junit.Assert.assertEquals(1.00, year.quickRatio, 0.01)

        // Absolute Liquid Ratio = 50 / 200 = 0.25
        org.junit.Assert.assertEquals(0.25, year.absoluteLiquidRatio, 0.01)

        // Debt-Equity Ratio = Term Loan / TNW = 150L / 300L = 0.50 : 1
        org.junit.Assert.assertEquals(0.50, year.debtEquityRatio, 0.01)
    }

    @Test
    fun testCmaProjectReportScreenUi() {
        val profile = com.example.model.BusinessProfile(
            businessName = "Pabaru Enterprises",
            ownerName = "Nami Jais",
            gstin = "27ABCDE1234F1Z5"
        )

        composeTestRule.setContent {
            com.example.ui.screens.CmaProjectReportScreen(
                profile = profile,
                transactions = emptyList(),
                parties = emptyList(),
                products = emptyList(),
                cashAndBankPaise = 25000000L,
                onBack = {}
            )
        }

        composeTestRule.waitForIdle()

        // Form 1 should be visible by default
        composeTestRule.onNodeWithTag("cma_form_1_screen").assertExists()
        composeTestRule.onNodeWithTag("cma_input_project_title").assertExists()
        composeTestRule.onNodeWithTag("cma_export_pdf_button").assertExists()

        // Switch to Tab 3 (Form V MPBF)
        composeTestRule.onNodeWithTag("cma_tab_3").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("cma_form_5_screen").assertExists()

        // Switch to Tab 5 (Form VII Loan EMI)
        composeTestRule.onNodeWithTag("cma_tab_5").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("cma_form_7_screen").assertExists()

        // Switch to Tab 6 (19 Ratios)
        composeTestRule.onNodeWithTag("cma_tab_6").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("cma_ratios_screen").assertExists()
    }

    @Test
    fun testGstr2bJsonAndCsvParsing() {
        // 1. Test official GST portal JSON parsing
        val sampleJson = """
            {
              "data": {
                "docdata": {
                  "b2b": [
                    {
                      "ctin": "27AAACR1234F1Z1",
                      "cname": "Apex Engineering Ltd",
                      "inv": [
                        {
                          "inum": "INV/2025/088",
                          "idt": "14-07-2025",
                          "val": 118000.0,
                          "pos": "27",
                          "rchrg": "N",
                          "inv_typ": "R",
                          "itcavl": "Y",
                          "items": [
                            {
                              "num": 1,
                              "txval": 100000.0,
                              "iamt": 0.0,
                              "camt": 9000.0,
                              "samt": 9000.0,
                              "csamt": 0.0
                            }
                          ]
                        }
                      ]
                    }
                  ],
                  "cdnr": [
                    {
                      "ctin": "27AAACR1234F1Z1",
                      "nt": [
                        {
                          "nt_num": "CRN-099",
                          "nt_dt": "22-07-2025",
                          "val": 11800.0,
                          "itcavl": "N",
                          "items": [
                            {
                              "txval": 10000.0,
                              "camt": 900.0,
                              "samt": 900.0
                            }
                          ]
                        }
                      ]
                    }
                  ]
                }
              }
            }
        """.trimIndent()

        val parsedInvoices = com.example.engine.Gstr2bReconciliationEngine.parseGstr2bJson(sampleJson)
        org.junit.Assert.assertEquals(2, parsedInvoices.size)

        val inv1 = parsedInvoices[0]
        org.junit.Assert.assertEquals("27AAACR1234F1Z1", inv1.supplierGstin)
        org.junit.Assert.assertEquals("Apex Engineering Ltd", inv1.supplierTradeName)
        org.junit.Assert.assertEquals("INV/2025/088", inv1.invoiceNumber)
        org.junit.Assert.assertEquals(10000000L, inv1.taxableValuePaise) // ₹100,000
        org.junit.Assert.assertEquals(1800000L, inv1.totalTaxPaise)      // ₹18,000 (9k CGST + 9k SGST)
        org.junit.Assert.assertEquals(11800000L, inv1.invoiceValuePaise)
        org.junit.Assert.assertTrue(inv1.itcAvailable)

        val inv2 = parsedInvoices[1]
        org.junit.Assert.assertEquals("CRN-099", inv2.invoiceNumber)
        org.junit.Assert.assertEquals(1000000L, inv2.taxableValuePaise)
        org.junit.Assert.assertEquals(180000L, inv2.totalTaxPaise)
        org.junit.Assert.assertFalse(inv2.itcAvailable)

        // 2. Test CSV parsing
        val sampleCsv = """
            GSTIN,Supplier Name,Invoice Number,Invoice Date,Invoice Value,Taxable Value,Integrated Tax,Central Tax,State Tax,Cess,ITC Available
            29AABCS1429B1Z4,Techno Kraft Solutions,TK-992,10-07-2025,59000.00,50000.00,0.00,4500.00,4500.00,0.00,Y
        """.trimIndent()

        val parsedCsv = com.example.engine.Gstr2bReconciliationEngine.parseGstr2bCsvOrTsv(sampleCsv)
        org.junit.Assert.assertEquals(1, parsedCsv.size)
        org.junit.Assert.assertEquals("29AABCS1429B1Z4", parsedCsv[0].supplierGstin)
        org.junit.Assert.assertEquals("TK-992", parsedCsv[0].invoiceNumber)
        org.junit.Assert.assertEquals(5000000L, parsedCsv[0].taxableValuePaise)
        org.junit.Assert.assertEquals(900000L, parsedCsv[0].totalTaxPaise)
        org.junit.Assert.assertEquals(5900000L, parsedCsv[0].invoiceValuePaise)
        org.junit.Assert.assertTrue(parsedCsv[0].itcAvailable)
    }

    @Test
    fun testGstr2bSmartMatchingWithTolerance() {
        // Verify invoice normalization
        org.junit.Assert.assertEquals(
            "INV202588",
            com.example.engine.Gstr2bReconciliationEngine.normalizeInvoiceNum("INV/2025/088")
        )
        org.junit.Assert.assertEquals(
            "INV202588",
            com.example.engine.Gstr2bReconciliationEngine.normalizeInvoiceNum("INV-2025-88")
        )

        // 1. Setup GSTR-2B invoices
        val gstr2bList = listOf(
            // Perfect match with 50 paise rounding diff (within ₹1 tolerance)
            com.example.engine.Gstr2bReconciliationEngine.Gstr2bInvoice(
                supplierGstin = "27AAACR1234F1Z1",
                supplierTradeName = "Apex Supplies",
                invoiceNumber = "INV/2025/101",
                invoiceDate = "10/07/2025",
                invoiceValuePaise = 11800050L,
                taxableValuePaise = 10000000L,
                cgstPaise = 900000L,
                sgstPaise = 900000L,
                igstPaise = 0L,
                itcAvailable = true
            ),
            // Value Mismatch (Taxable diff > ₹1)
            com.example.engine.Gstr2bReconciliationEngine.Gstr2bInvoice(
                supplierGstin = "27BBBCS5678G2Z2",
                supplierTradeName = "Beta Components",
                invoiceNumber = "BC-404",
                invoiceDate = "12/07/2025",
                invoiceValuePaise = 23600000L,
                taxableValuePaise = 20000000L,
                cgstPaise = 1800000L,
                sgstPaise = 1800000L,
                igstPaise = 0L,
                itcAvailable = true
            ),
            // Missing in Books (In 2B but buyer forgot to enter in books)
            com.example.engine.Gstr2bReconciliationEngine.Gstr2bInvoice(
                supplierGstin = "27CCCCS9012H3Z3",
                supplierTradeName = "Gamma Logistics",
                invoiceNumber = "GL-777",
                invoiceDate = "15/07/2025",
                invoiceValuePaise = 5900000L,
                taxableValuePaise = 5000000L,
                cgstPaise = 450000L,
                sgstPaise = 450000L,
                igstPaise = 0L,
                itcAvailable = true
            ),
            // Ineligible ITC (Section 17(5))
            com.example.engine.Gstr2bReconciliationEngine.Gstr2bInvoice(
                supplierGstin = "27DDDDM3456J4Z4",
                supplierTradeName = "Delta Motors",
                invoiceNumber = "DM-001",
                invoiceDate = "18/07/2025",
                invoiceValuePaise = 128000000L,
                taxableValuePaise = 100000000L,
                cgstPaise = 14000000L,
                sgstPaise = 14000000L,
                igstPaise = 0L,
                itcAvailable = false,
                itcBlockedReason = "Blocked Credit u/s 17(5)"
            )
        )

        // 2. Setup Parties and Transactions
        val parties = listOf(
            com.example.model.Party(id = "p1", name = "Apex Supplies", phone = "", address = "", gstin = "27AAACR1234F1Z1", pan = "", stateCode = "27", openingBalancePaise = 0L, openingBalanceType = com.example.model.BalanceType.Settled, isSupplier = true),
            com.example.model.Party(id = "p2", name = "Beta Components", phone = "", address = "", gstin = "27BBBCS5678G2Z2", pan = "", stateCode = "27", openingBalancePaise = 0L, openingBalanceType = com.example.model.BalanceType.Settled, isSupplier = true),
            com.example.model.Party(id = "p3", name = "Echo Traders", phone = "", address = "", gstin = "27EEEEZ7890K5Z5", pan = "", stateCode = "27", openingBalancePaise = 0L, openingBalanceType = com.example.model.BalanceType.Settled, isSupplier = true)
        )

        val transactions = listOf(
            // Matches 2b_1 with ₹0.50 difference
            com.example.model.Transaction(
                id = "tx_1",
                branchId = "main",
                date = "10/07/2025",
                partyId = "p1",
                amountPaise = 11800000L,
                type = com.example.model.TransactionType.Purchase,
                paymentMode = com.example.model.PaymentMode.Bank,
                invoiceNumber = "INV-2025-101",
                items = listOf(
                    com.example.model.InvoiceLineItem(
                        itemId = "prod_1",
                        name = "Raw Material A",
                        hsnCode = "8471",
                        quantity = 1,
                        unit = "kg",
                        ratePaise = 10000000L,
                        gstRate = 18,
                        taxableAmountPaise = 10000000L,
                        cgstPaise = 900000L,
                        sgstPaise = 900000L,
                        igstPaise = 0L,
                        totalPaise = 11800000L
                    )
                )
            ),
            // Value mismatch with 2b_2 (Books has ₹220,000 taxable vs ₹200,000 in 2B)
            com.example.model.Transaction(
                id = "tx_2",
                branchId = "main",
                date = "12/07/2025",
                partyId = "p2",
                amountPaise = 25960000L,
                type = com.example.model.TransactionType.Purchase,
                paymentMode = com.example.model.PaymentMode.Bank,
                invoiceNumber = "BC-404",
                items = listOf(
                    com.example.model.InvoiceLineItem(
                        itemId = "prod_2",
                        name = "Hardware Pack B",
                        hsnCode = "8471",
                        quantity = 1,
                        unit = "set",
                        ratePaise = 22000000L,
                        gstRate = 18,
                        taxableAmountPaise = 22000000L,
                        cgstPaise = 1980000L,
                        sgstPaise = 1980000L,
                        igstPaise = 0L,
                        totalPaise = 25960000L
                    )
                )
            ),
            // Missing in 2B (Rule 36(4) Risk: Supplier has not filed GSTR-1)
            com.example.model.Transaction(
                id = "tx_missing",
                branchId = "main",
                date = "25/07/2025",
                partyId = "p3",
                amountPaise = 9440000L,
                type = com.example.model.TransactionType.Purchase,
                paymentMode = com.example.model.PaymentMode.Bank,
                invoiceNumber = "ET-555",
                items = listOf(
                    com.example.model.InvoiceLineItem(
                        itemId = "prod_3",
                        name = "Consumables C",
                        hsnCode = "8471",
                        quantity = 1,
                        unit = "pcs",
                        ratePaise = 8000000L,
                        gstRate = 18,
                        taxableAmountPaise = 8000000L,
                        cgstPaise = 720000L,
                        sgstPaise = 720000L,
                        igstPaise = 0L,
                        totalPaise = 9440000L
                    )
                )
            )
        )

        val (items, summary) = com.example.engine.Gstr2bReconciliationEngine.reconcile(
            gstr2bList = gstr2bList,
            transactions = transactions,
            parties = parties
        )

        org.junit.Assert.assertEquals(1, summary.matchedCount)
        org.junit.Assert.assertEquals(1, summary.valueMismatchCount)
        org.junit.Assert.assertEquals(1, summary.missingInBooksCount)
        org.junit.Assert.assertEquals(1, summary.missingIn2bCount)
        org.junit.Assert.assertEquals(1, summary.ineligibleItcCount)

        // Verify individual statuses
        val matchedItem = items.find { it.matchStatus == com.example.engine.Gstr2bReconciliationEngine.MatchStatus.FullyMatched }
        org.junit.Assert.assertNotNull(matchedItem)
        org.junit.Assert.assertEquals("INV/2025/101", matchedItem!!.invoiceNumber)

        val mismatchItem = items.find { it.matchStatus == com.example.engine.Gstr2bReconciliationEngine.MatchStatus.ValueMismatch }
        org.junit.Assert.assertNotNull(mismatchItem)
        org.junit.Assert.assertEquals("BC-404", mismatchItem!!.invoiceNumber)

        val missingInBooks = items.find { it.matchStatus == com.example.engine.Gstr2bReconciliationEngine.MatchStatus.MissingInBooks }
        org.junit.Assert.assertNotNull(missingInBooks)
        org.junit.Assert.assertEquals("GL-777", missingInBooks!!.invoiceNumber)

        val missingIn2b = items.find { it.matchStatus == com.example.engine.Gstr2bReconciliationEngine.MatchStatus.MissingIn2B }
        org.junit.Assert.assertNotNull(missingIn2b)
        org.junit.Assert.assertEquals("ET-555", missingIn2b!!.invoiceNumber)

        val ineligible = items.find { it.matchStatus == com.example.engine.Gstr2bReconciliationEngine.MatchStatus.IneligibleITC }
        org.junit.Assert.assertNotNull(ineligible)
        org.junit.Assert.assertEquals("DM-001", ineligible!!.invoiceNumber)
    }

    @Test
    fun testGstr2bReconciliationScreenUi() {
        val profile = com.example.model.BusinessProfile(
            businessName = "Pabaru Enterprises",
            ownerName = "Nami Jais",
            gstin = "27ABCDE1234F1Z5"
        )

        composeTestRule.setContent {
            com.example.ui.screens.Gstr2bReconciliationScreen(
                profile = profile,
                transactions = emptyList(),
                parties = emptyList(),
                onBack = {}
            )
        }

        composeTestRule.waitForIdle()

        // 1. Verify TopAppBar and Primary Action Buttons
        composeTestRule.onNodeWithTag("gstr2b_back_button").assertExists()
        composeTestRule.onNodeWithTag("gstr2b_import_button").assertExists()
        composeTestRule.onNodeWithTag("gstr2b_sample_button").assertExists()
        composeTestRule.onNodeWithTag("gstr2b_export_pdf_button").assertExists()
        composeTestRule.onNodeWithTag("gstr2b_export_csv_button").assertExists()

        // 2. Click "Load Sample 2B" to populate demo reconciliation data
        composeTestRule.onNodeWithTag("gstr2b_sample_button").performClick()
        composeTestRule.waitForIdle()

        // 3. Verify LazyColumn and Filter Chips exist
        composeTestRule.onNodeWithTag("gstr2b_lazy_column").assertExists()
        composeTestRule.onNodeWithTag("gstr2b_filter_all").assertExists()
        composeTestRule.onNodeWithTag("gstr2b_filter_matched").assertExists()
        composeTestRule.onNodeWithTag("gstr2b_filter_mismatch").assertExists()
        composeTestRule.onNodeWithTag("gstr2b_filter_not_in_books").assertExists()
        composeTestRule.onNodeWithTag("gstr2b_filter_not_in_2b").assertExists()

        // Click Value Mismatch filter
        composeTestRule.onNodeWithTag("gstr2b_filter_mismatch").performClick()
        composeTestRule.waitForIdle()

        // Switch back to All filter
        composeTestRule.onNodeWithTag("gstr2b_filter_all").performClick()
        composeTestRule.waitForIdle()

        // 4. Scroll to Search Bar and verify
        composeTestRule.onNodeWithTag("gstr2b_lazy_column").performScrollToIndex(3)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("gstr2b_search_input").assertExists()
        composeTestRule.onNodeWithTag("gstr2b_search_input").performTextInput("Apex")
        composeTestRule.waitForIdle()
    }

    @Test
    fun testStartupSplashScreenAndAutoTheme() {
        val lightTokens = com.example.ui.theme.getTokensForTheme(com.example.model.AppColorTheme.ClinicBlue)
        org.junit.Assert.assertFalse("ClinicBlue must be light theme", lightTokens.isDark)
        org.junit.Assert.assertEquals(
            "ClinicBlue card must be crisp pure white",
            androidx.compose.ui.graphics.Color(0xFFFFFFFF),
            lightTokens.card
        )

        val darkTokens = com.example.ui.theme.getTokensForTheme(com.example.model.AppColorTheme.MidnightTeal)
        org.junit.Assert.assertTrue("MidnightTeal must be dark theme", darkTokens.isDark)

        // Verify PabaruSplashScreen renders correctly with theme tokens
        composeTestRule.setContent {
            com.example.ui.components.PabaruSplashScreen(tokens = lightTokens)
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("pabaru_splash_screen").assertExists()
        composeTestRule.onNodeWithText("Pabaru Billing").assertExists()
        composeTestRule.onNodeWithText("Offline-First GST Billing & Accounting").assertExists()
    }

    @Test
    fun testUnpaidBillsNullPartySafety() {
        // Setup state containing a transaction with null partyId (counter sales)
        val counterTxn = com.example.model.Transaction(
            id = "TX-COUNTER-1",
            branchId = "BR-MAIN",
            date = "08/11/2026",
            partyId = null, // Null party
            amountPaise = 50000L,
            type = com.example.model.TransactionType.Sale,
            paymentMode = com.example.model.PaymentMode.Cash
        )

        val customer = com.example.model.Party(
            id = "CUST-1",
            name = "Ramesh Kumar",
            phone = "9876543210",
            address = "Shop 4",
            gstin = "",
            pan = "",
            stateCode = "27",
            openingBalancePaise = 100000L,
            openingBalanceType = com.example.model.BalanceType.Receivable,
            isSupplier = false
        )

        val uiState = com.example.viewmodel.PabaruUiState(
            parties = listOf(customer),
            transactions = listOf(counterTxn)
        )

        // Rendering UnpaidBillsDetailScreen must NOT crash with NullPointerException on null partyId
        composeTestRule.setContent {
            com.example.ui.screens.UnpaidBillsDetailScreen(
                isCollect = true,
                uiState = uiState,
                onBack = {},
                onRecordPayment = { _, _, _, _, _ -> }
            )
        }
        composeTestRule.waitForIdle()
    }
}



