package com.example.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Rule88ASetOffTest {

    @Test
    fun testRule88AStep1_igstExhaustedFirst() {
        // Output: IGST 50,000, CGST 20,000, SGST 20,000
        // ITC: IGST 80,000, CGST 10,000, SGST 10,000
        // Step 1: 50,000 IGST ITC pays 50,000 IGST output liability.
        // Balance IGST ITC remaining for Step 2 = 30,000.
        val matrix = CmaAndGstEngine.computeRule88ASetOff(
            grossIgst = 5000000L,
            grossCgst = 2000000L,
            grossSgst = 2000000L,
            itcIgst = 8000000L,
            itcCgst = 1000000L,
            itcSgst = 1000000L,
            strategy = CmaAndGstEngine.Rule88AStrategy.OPTIMAL
        )

        assertEquals(5000000L, matrix.igstUsedForIgstPaise)
        assertEquals(0L, matrix.cashIgstPaise)
        assertEquals(8000000L, matrix.totalIgstUtilizedPaise) // 50k for IGST + 30k for CGST/SGST
    }

    @Test
    fun testRule88AOptimalMinimizesCashOutflow_asymmetricDeficit() {
        // Classic textbook case demonstrating why Rule 88A optimization matters:
        // Output: IGST 0, CGST 1,00,000, SGST 1,00,000
        // ITC: IGST 60,000, CGST 90,000, SGST 10,000
        //
        // CGST Deficit = 100k - 90k = 10,000
        // SGST Deficit = 100k - 10k = 90,000
        //
        // With 50:50 equal split:
        // IGST to CGST = 30,000 (which exceeds CGST deficit of 10,000, blocking 20,000 in CGST ITC!)
        // IGST to SGST = 30,000
        // CGST Cash = 0, CGST ITC Carried Forward = 20,000
        // SGST Cash = 100k - 30k - 10k = 60,000
        // Total Cash = 60,000!
        //
        // With OPTIMAL strategy:
        // Allocates all 60,000 to SGST (or covers CGST deficit 10k and rest 50k to SGST):
        // If 60k to SGST:
        // CGST uses 90k CGST ITC -> Cash CGST = 10,000, CF = 0
        // SGST uses 60k IGST ITC + 10k SGST ITC -> Cash SGST = 30,000, CF = 0
        // Total Cash = 40,000!
        // Savings = 20,000!

        val optimal = CmaAndGstEngine.computeRule88ASetOff(
            grossIgst = 0L,
            grossCgst = 10000000L,
            grossSgst = 10000000L,
            itcIgst = 6000000L,
            itcCgst = 9000000L,
            itcSgst = 1000000L,
            strategy = CmaAndGstEngine.Rule88AStrategy.OPTIMAL
        )

        val equalSplit = CmaAndGstEngine.computeRule88ASetOff(
            grossIgst = 0L,
            grossCgst = 10000000L,
            grossSgst = 10000000L,
            itcIgst = 6000000L,
            itcCgst = 9000000L,
            itcSgst = 1000000L,
            strategy = CmaAndGstEngine.Rule88AStrategy.EQUAL_SPLIT
        )

        // Equal split results in 60,000 cash payable
        assertEquals(6000000L, equalSplit.totalCashPayablePaise)
        assertEquals(2000000L, equalSplit.carriedForwardCgstPaise) // 20k blocked

        // Optimal results in strictly less cash (40,000)
        assertEquals(4000000L, optimal.totalCashPayablePaise)
        assertEquals(0L, optimal.carriedForwardCgstPaise) // Zero credit blocked!

        // Exactly 20,000 rupees saved in cash outflow
        assertEquals(2000000L, optimal.cashSavingsPaise)
        assertTrue(optimal.totalCashPayablePaise < equalSplit.totalCashPayablePaise)
    }

    @Test
    fun testRule88AZeroCashWhenItcExceedsGrossLiabilities() {
        val matrix = CmaAndGstEngine.computeRule88ASetOff(
            grossIgst = 3000000L,
            grossCgst = 3000000L,
            grossSgst = 3000000L,
            itcIgst = 5000000L,
            itcCgst = 4000000L,
            itcSgst = 4000000L,
            strategy = CmaAndGstEngine.Rule88AStrategy.OPTIMAL
        )

        assertEquals(0L, matrix.cashIgstPaise)
        assertEquals(0L, matrix.cashCgstPaise)
        assertEquals(0L, matrix.cashSgstPaise)
        assertEquals(0L, matrix.totalCashPayablePaise)
        assertEquals(4000000L, matrix.totalCarriedForwardItcPaise)
    }

    @Test
    fun testCrossUtilizationProhibition_Sec49B() {
        // CGST ITC cannot pay SGST, SGST ITC cannot pay CGST
        val matrix = CmaAndGstEngine.computeRule88ASetOff(
            grossIgst = 0L,
            grossCgst = 0L,
            grossSgst = 5000000L,
            itcIgst = 0L,
            itcCgst = 5000000L, // Plenty of CGST ITC
            itcSgst = 0L
        )

        // CGST ITC cannot pay SGST liability, so cash must be paid
        assertEquals(5000000L, matrix.cashSgstPaise)
        assertEquals(5000000L, matrix.carriedForwardCgstPaise)
        assertEquals(0L, matrix.cgstUsedForCgstPaise)
    }

    @Test
    fun testCompareAllRule88AStrategies() {
        val comparison = CmaAndGstEngine.compareAllRule88AStrategies(
            grossIgst = 0L,
            grossCgst = 10000000L,
            grossSgst = 10000000L,
            itcIgst = 6000000L,
            itcCgst = 9000000L,
            itcSgst = 1000000L
        )

        assertEquals(4000000L, comparison.optimal.totalCashPayablePaise)
        assertEquals(6000000L, comparison.equalSplit.totalCashPayablePaise)
        assertEquals(2000000L, comparison.cashSavedPaise)
    }

    @Test
    fun testCustomStrategyPercentage() {
        val custom75 = CmaAndGstEngine.computeRule88ASetOff(
            grossIgst = 0L,
            grossCgst = 5000000L,
            grossSgst = 5000000L,
            itcIgst = 4000000L,
            itcCgst = 0L,
            itcSgst = 0L,
            strategy = CmaAndGstEngine.Rule88AStrategy.CUSTOM,
            customIgstForCgstPercent = 75.0
        )

        // 75% of 40,000 = 30,000 to CGST, remainder 10,000 to SGST
        assertEquals(3000000L, custom75.igstUsedForCgstPaise)
        assertEquals(1000000L, custom75.igstUsedForSgstPaise)
    }

    @Test
    fun testAuditExplanationStepsGenerated() {
        val matrix = CmaAndGstEngine.computeRule88ASetOff(
            grossIgst = 1000000L,
            grossCgst = 2000000L,
            grossSgst = 2000000L,
            itcIgst = 3000000L,
            itcCgst = 1000000L,
            itcSgst = 1000000L,
            strategy = CmaAndGstEngine.Rule88AStrategy.OPTIMAL
        )

        assertTrue(matrix.explanationSteps.isNotEmpty())
        assertTrue(matrix.explanationSteps.any { it.contains("Step 1") })
        assertTrue(matrix.explanationSteps.any { it.contains("Step 2") })
        assertTrue(matrix.explanationSteps.any { it.contains("Step 3") })
        assertTrue(matrix.explanationSteps.any { it.contains("Step 4") })
        assertTrue(matrix.explanationSteps.any { it.contains("Step 5") })
    }
}
