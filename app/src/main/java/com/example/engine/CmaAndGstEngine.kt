package com.example.engine

import com.example.model.*
import java.util.Locale

object CmaAndGstEngine {

    // -------------------------------------------------------------
    // 1. CMA (Credit Monitoring Arrangement) & Project Report
    // -------------------------------------------------------------
    data class CmaOperatingStatement(
        val grossSalesPaise: Long,
        val cogsPaise: Long,
        val grossProfitPaise: Long,
        val operatingExpensesPaise: Long,
        val operatingProfitPaise: Long, // EBITDA
        val interestAndFinancePaise: Long,
        val netProfitBeforeTaxPaise: Long,
        val taxPaise: Long,
        val patPaise: Long
    )

    data class CmaWorkingCapitalAssessment(
        val totalCurrentAssetsPaise: Long,
        val currentLiabilitiesOtherThanBankPaise: Long, // Creditors + other current liabilities
        val workingCapitalGapPaise: Long,
        val minPromotersContributionPaise: Long, // 25% of Total Current Assets (Tandon Committee Method II)
        val actualNwcPaise: Long,
        val mpbfTandonMethod2Paise: Long, // Maximum Permissible Bank Finance
        val currentRatio: Double,
        val dscr: Double
    )

    fun calculateCmaData(
        transactions: List<Transaction>,
        parties: List<Party>,
        products: List<ProductItem>,
        cashInHandPaise: Long,
        bankBalancesPaise: Long
    ): Pair<CmaOperatingStatement, CmaWorkingCapitalAssessment> {
        var sales = 0L
        var purchases = 0L
        var directExp = 0L
        var indirectExp = 0L

        transactions.filter { !it.isVoided }.forEach { t ->
            val excl = if (t.items.isNotEmpty()) t.items.sumOf { it.taxableAmountPaise } else t.amountPaise
            when (t.type) {
                TransactionType.Sale, TransactionType.NonGstBill -> sales += excl
                TransactionType.CreditNote -> sales -= excl
                TransactionType.Purchase -> purchases += excl
                TransactionType.DebitNote -> purchases -= excl
                TransactionType.Expense -> {
                    if (t.expenseCategory == ExpenseCategory.Direct) directExp += excl
                    else indirectExp += excl
                }
                else -> {}
            }
        }
        sales = maxOf(0L, sales)
        purchases = maxOf(0L, purchases)

        val cogs = purchases + directExp
        val grossProfit = sales - cogs
        val operatingExpenses = indirectExp
        val ebitda = grossProfit - operatingExpenses
        val safeInterest = if (ebitda > 0L) (ebitda * 10L + 50L) / 100L else 0L
        val pbt = ebitda - safeInterest
        val tax = if (pbt > 0L) (pbt * 25L + 50L) / 100L else 0L // 25% corporate / firm tax
        val pat = pbt - tax

        val operatingStmt = CmaOperatingStatement(
            grossSalesPaise = sales,
            cogsPaise = cogs,
            grossProfitPaise = grossProfit,
            operatingExpensesPaise = operatingExpenses,
            operatingProfitPaise = ebitda,
            interestAndFinancePaise = safeInterest,
            netProfitBeforeTaxPaise = pbt,
            taxPaise = tax,
            patPaise = pat
        )

        // Working Capital Assessment: live receivables, payables, and current inventory valuation
        var receivables = parties.filter { it.openingBalanceType == BalanceType.Receivable }.sumOf { it.openingBalancePaise }
        var payables = parties.filter { it.openingBalanceType == BalanceType.Payable }.sumOf { it.openingBalancePaise }
        transactions.filter { !it.isVoided && it.partyId != null }.forEach { t ->
            when (t.type) {
                TransactionType.Sale, TransactionType.NonGstBill -> if (t.paymentMode == PaymentMode.Credit) receivables += t.amountPaise
                TransactionType.CreditNote -> if (t.paymentMode == PaymentMode.Credit) receivables -= t.amountPaise
                TransactionType.Purchase -> if (t.paymentMode == PaymentMode.Credit) payables += t.amountPaise
                TransactionType.DebitNote -> if (t.paymentMode == PaymentMode.Credit) payables -= t.amountPaise
                TransactionType.PaymentIn -> receivables -= t.amountPaise
                TransactionType.PaymentOut -> payables -= t.amountPaise
                else -> {}
            }
        }
        val stockValuation = products.filter { !it.isArchived }.sumOf {
            (it.currentStockMilli * it.purchasePricePaise + 500L) / 1000L
        }
        val totalCurrentAssets = cashInHandPaise + bankBalancesPaise + receivables + stockValuation

        val otherCurrentLiabilities = payables + (operatingExpenses / 12L) // approx 1 month unpaid exp

        val wcGap = totalCurrentAssets - otherCurrentLiabilities
        val minPromotersMargin = (totalCurrentAssets * 25L + 50L) / 100L // 25% margin
        val mpbf = (((totalCurrentAssets * 75L + 50L) / 100L) - otherCurrentLiabilities).coerceAtLeast(0L)

        val cr = if (otherCurrentLiabilities > 0L) totalCurrentAssets.toDouble() / otherCurrentLiabilities.toDouble() else 2.1
        val dscr = if (safeInterest > 0L) (pat + safeInterest).toDouble() / safeInterest.toDouble() else 3.5

        val wcAssessment = CmaWorkingCapitalAssessment(
            totalCurrentAssetsPaise = totalCurrentAssets,
            currentLiabilitiesOtherThanBankPaise = otherCurrentLiabilities,
            workingCapitalGapPaise = wcGap,
            minPromotersContributionPaise = minPromotersMargin,
            actualNwcPaise = minPromotersMargin,
            mpbfTandonMethod2Paise = mpbf,
            currentRatio = cr,
            dscr = dscr
        )

        return Pair(operatingStmt, wcAssessment)
    }

    // -------------------------------------------------------------
    // 2. Statutory GST Filing Assistant (GSTR-1, GSTR-3B & CMP-08)
    // -------------------------------------------------------------
    enum class GstPeriodFilter(val displayName: String) {
        CurrentMonth("Current Month"),
        PreviousMonth("Previous Month"),
        Quarter("Current Quarter"),
        AllTime("All Time / FY")
    }

    fun isDateInPeriod(dateStr: String, filter: GstPeriodFilter): Boolean {
        if (filter == GstPeriodFilter.AllTime) return true
        if (dateStr.isBlank()) return false
        val parsed = FinancialEngine.parseValidLocalDate(dateStr) ?: return false
        val today = java.time.LocalDate.now()
        val curYear = today.year
        val curMonth = today.monthValue
        val y = parsed.year
        val m = parsed.monthValue
        return when (filter) {
            GstPeriodFilter.CurrentMonth -> y == curYear && m == curMonth
            GstPeriodFilter.PreviousMonth -> {
                val prev = today.minusMonths(1)
                y == prev.year && m == prev.monthValue
            }
            GstPeriodFilter.Quarter -> {
                val curQ = (curMonth - 1) / 3
                val q = (m - 1) / 3
                y == curYear && curQ == q
            }
            GstPeriodFilter.AllTime -> true
        }
    }

    data class Gstr1B2bRateItem(
        val gstRate: Int,
        val taxablePaise: Long,
        val cgstPaise: Long,
        val sgstPaise: Long,
        val igstPaise: Long,
        val gstRateBps: Int = gstRate * 100
    )

    data class Gstr1B2bInvoice(
        val partyId: String,
        val gstin: String,
        val partyName: String,
        val invoiceNumber: String,
        val date: String,
        val posStateCode: String,
        val taxablePaise: Long,
        val cgstPaise: Long,
        val sgstPaise: Long,
        val igstPaise: Long,
        val totalPaise: Long,
        val isInterstate: Boolean,
        val itemsSummary: String,
        val isPrefilledFromExcel: Boolean = false,
        val rateBreakdown: List<Gstr1B2bRateItem> = emptyList()
    )

    data class Gstr1CreditDebitNote(
        val partyGstin: String,
        val partyName: String,
        val noteNumber: String,
        val noteDate: String,
        val noteType: String, // "C" = Credit Note, "D" = Debit Note
        val posStateCode: String,
        val taxablePaise: Long,
        val cgstPaise: Long,
        val sgstPaise: Long,
        val igstPaise: Long,
        val totalPaise: Long,
        val rateBreakdown: List<Gstr1B2bRateItem> = emptyList()
    )

    data class Gstr1ExportInvoice(
        val invoiceNumber: String,
        val date: String,
        val exportType: String, // "WPAY" or "WOPAY"
        val taxablePaise: Long,
        val igstPaise: Long,
        val totalPaise: Long,
        val gstRate: Int,
        val gstRateBps: Int = gstRate * 100
    )

    data class Gstr1B2csSupply(
        val posStateCode: String,
        val supplyType: String, // "INTRA" or "INTER"
        val gstRate: Int,
        val taxablePaise: Long,
        val cgstPaise: Long,
        val sgstPaise: Long,
        val igstPaise: Long,
        val gstRateBps: Int = gstRate * 100
    )

    data class Gstr1HsnItem(
        val hsnCode: String,
        val description: String,
        val uqc: String,
        val totalQuantity: Int,
        val taxablePaise: Long,
        val cgstPaise: Long,
        val sgstPaise: Long,
        val igstPaise: Long,
        val gstRate: Int,
        val totalQuantityMilli: Long = totalQuantity.toLong() * 1000L,
        val gstRateBps: Int = gstRate * 100
    )

    data class Gstr1DocItem(
        val minInvoiceNumber: String,
        val maxInvoiceNumber: String,
        val totalIssued: Int,
        val cancelledCount: Int,
        val netIssuedCount: Int
    )

    data class StatutoryGstr1Data(
        val b2bInvoices: List<Gstr1B2bInvoice>,
        val b2csSupplies: List<Gstr1B2csSupply>,
        val hsnSummary: List<Gstr1HsnItem>,
        val docSummary: Gstr1DocItem,
        val b2bCount: Int,
        val b2bTaxablePaise: Long,
        val b2bCgstPaise: Long,
        val b2bSgstPaise: Long,
        val b2bIgstPaise: Long,
        val b2cCount: Int,
        val b2cTaxablePaise: Long,
        val b2cCgstPaise: Long,
        val b2cSgstPaise: Long,
        val b2cIgstPaise: Long,
        val totalTaxablePaise: Long,
        val totalCgstPaise: Long,
        val totalSgstPaise: Long,
        val totalIgstPaise: Long,
        val totalTaxPaise: Long,
        val prefilledExcelCount: Int = 0,
        val cdnrNotes: List<Gstr1CreditDebitNote> = emptyList(),
        val exportInvoices: List<Gstr1ExportInvoice> = emptyList(),
        val nilExemptPaise: Long = 0L,
        val nonGstOutwardPaise: Long = 0L,
        val creditNoteCount: Int = 0,
        val creditNoteNumbers: List<String> = emptyList(),
        val creditNoteCancelledCount: Int = 0,
        val debitNoteCount: Int = 0,
        val debitNoteNumbers: List<String> = emptyList(),
        val debitNoteCancelledCount: Int = 0
    )

    enum class Rule88AStrategy(val displayName: String, val shortDesc: String) {
        OPTIMAL("Optimal (Min Cash)", "Dynamically targets deficits to legally minimize cash outgo"),
        EQUAL_SPLIT("Equal Split (50:50)", "Splits remaining IGST 50% to CGST and 50% to SGST"),
        CGST_FIRST("CGST Priority", "Exhausts IGST towards CGST first, balance to SGST"),
        SGST_FIRST("SGST Priority", "Exhausts IGST towards SGST first, balance to CGST"),
        CUSTOM("Custom Proportion", "User-specified allocation percentage or rupee amount")
    }

    data class Rule88ASetOffMatrix(
        val grossIgstPaise: Long,
        val grossCgstPaise: Long,
        val grossSgstPaise: Long,
        val totalGrossLiabilityPaise: Long,
        val itcIgstPaise: Long,
        val itcCgstPaise: Long,
        val itcSgstPaise: Long,
        val totalItcAvailablePaise: Long,
        val igstUsedForIgstPaise: Long,
        val igstUsedForCgstPaise: Long,
        val igstUsedForSgstPaise: Long,
        val totalIgstUtilizedPaise: Long,
        val cgstUsedForCgstPaise: Long,
        val cgstUsedForIgstPaise: Long,
        val totalCgstUtilizedPaise: Long,
        val sgstUsedForSgstPaise: Long,
        val sgstUsedForIgstPaise: Long,
        val totalSgstUtilizedPaise: Long,
        val cashIgstPaise: Long,
        val cashCgstPaise: Long,
        val cashSgstPaise: Long,
        val totalCashPayablePaise: Long,
        val carriedForwardIgstPaise: Long,
        val carriedForwardCgstPaise: Long,
        val carriedForwardSgstPaise: Long,
        val totalCarriedForwardItcPaise: Long,
        val strategy: Rule88AStrategy = Rule88AStrategy.OPTIMAL,
        val cashSavingsPaise: Long = 0L,
        val explanationSteps: List<String> = emptyList()
    )

    data class Rule88AStrategyComparison(
        val optimal: Rule88ASetOffMatrix,
        val equalSplit: Rule88ASetOffMatrix,
        val cgstFirst: Rule88ASetOffMatrix,
        val sgstFirst: Rule88ASetOffMatrix,
        val cashSavedPaise: Long
    )

    data class StatutoryGstr3bData(
        val outwardTaxablePaise: Long,
        val outwardIgstPaise: Long,
        val outwardCgstPaise: Long,
        val outwardSgstPaise: Long,
        val eligibleItcIgstPaise: Long,
        val eligibleItcCgstPaise: Long,
        val eligibleItcSgstPaise: Long,
        val totalEligibleItcPaise: Long,
        val setOffMatrix: Rule88ASetOffMatrix
    )

    data class StatutoryCmp08Data(
        val turnoverPaise: Long,
        val compositionRatePercent: Double,
        val centralTaxPaise: Long,
        val stateTaxPaise: Long,
        val totalCompositionTaxPaise: Long,
        val rcmTurnoverPaise: Long,
        val rcmCentralTaxPaise: Long,
        val rcmStateTaxPaise: Long,
        val rcmIntegratedTaxPaise: Long,
        val rcmTotalTaxPaise: Long,
        val interestPaise: Long,
        val netCashPayablePaise: Long,
        val transactionsCount: Int
    )

    fun computeRule88ASetOff(
        grossIgst: Long,
        grossCgst: Long,
        grossSgst: Long,
        itcIgst: Long,
        itcCgst: Long,
        itcSgst: Long,
        strategy: Rule88AStrategy = Rule88AStrategy.OPTIMAL,
        customIgstForCgstPercent: Double? = null,
        customIgstForCgstPaise: Long? = null
    ): Rule88ASetOffMatrix {
        val steps = mutableListOf<String>()

        var remGrossIgst = maxOf(0L, grossIgst)
        var remGrossCgst = maxOf(0L, grossCgst)
        var remGrossSgst = maxOf(0L, grossSgst)

        var remItcIgst = maxOf(0L, itcIgst)
        var remItcCgst = maxOf(0L, itcCgst)
        var remItcSgst = maxOf(0L, itcSgst)

        // Step 1: Rule 88A statutory mandate: Exhaust IGST credit completely against IGST liability first
        val igstForIgst = minOf(remGrossIgst, remItcIgst)
        remGrossIgst -= igstForIgst
        remItcIgst -= igstForIgst
        steps.add("Step 1: Utilized IGST credit of ${FinancialEngine.formatPaise(igstForIgst)} against IGST output liability. Remaining IGST liability: ${FinancialEngine.formatPaise(remGrossIgst)}, Balance IGST credit: ${FinancialEngine.formatPaise(remItcIgst)}.")

        // Step 2: Utilize balance IGST credit towards CGST & SGST in any order/proportion
        var igstForCgst = 0L
        var igstForSgst = 0L

        if (remItcIgst > 0) {
            when (strategy) {
                Rule88AStrategy.OPTIMAL -> {
                    // Deficit in each head before applying IGST credit:
                    // What would be payable in cash if only own-head ITC were used
                    val deficitCgst = maxOf(0L, remGrossCgst - remItcCgst)
                    val deficitSgst = maxOf(0L, remGrossSgst - remItcSgst)
                    val totalDeficit = deficitCgst + deficitSgst

                    if (totalDeficit > 0) {
                        if (remItcIgst <= totalDeficit) {
                            // Target the larger deficit first to equalize net cash payable
                            if (deficitCgst > deficitSgst) {
                                val diff = deficitCgst - deficitSgst
                                val firstCgst = minOf(remItcIgst, diff)
                                igstForCgst += firstCgst
                                val leftover = remItcIgst - firstCgst
                                if (leftover > 0) {
                                    val half = leftover / 2
                                    igstForCgst += half
                                    igstForSgst += (leftover - half)
                                }
                            } else if (deficitSgst > deficitCgst) {
                                val diff = deficitSgst - deficitCgst
                                val firstSgst = minOf(remItcIgst, diff)
                                igstForSgst += firstSgst
                                val leftover = remItcIgst - firstSgst
                                if (leftover > 0) {
                                    val half = leftover / 2
                                    igstForSgst += half
                                    igstForCgst += (leftover - half)
                                }
                            } else {
                                val half = remItcIgst / 2
                                igstForCgst += half
                                igstForSgst += (remItcIgst - half)
                            }
                        } else {
                            // remItcIgst > totalDeficit: completely wipe out both deficits (zero cash!)
                            igstForCgst = deficitCgst
                            igstForSgst = deficitSgst
                            var surplus = remItcIgst - totalDeficit

                            // Allocate surplus evenly across remaining room in CGST/SGST liabilities
                            val roomCgst = maxOf(0L, remGrossCgst - igstForCgst)
                            val roomSgst = maxOf(0L, remGrossSgst - igstForSgst)
                            val addCgst = minOf(roomCgst, surplus / 2)
                            igstForCgst += addCgst
                            surplus -= addCgst
                            val addSgst = minOf(roomSgst, surplus)
                            igstForSgst += addSgst
                            surplus -= addSgst

                            if (surplus > 0) {
                                val extraC = minOf(maxOf(0L, remGrossCgst - igstForCgst), surplus)
                                igstForCgst += extraC
                                surplus -= extraC
                                val extraS = minOf(maxOf(0L, remGrossSgst - igstForSgst), surplus)
                                igstForSgst += extraS
                            }
                        }
                    } else {
                        // Both heads already have sufficient own ITC; divide IGST evenly to balance carry forward
                        val half = remItcIgst / 2
                        val cAlloc = minOf(remGrossCgst, half)
                        igstForCgst = cAlloc
                        var left = remItcIgst - cAlloc
                        val sAlloc = minOf(remGrossSgst, left)
                        igstForSgst = sAlloc
                        left -= sAlloc
                        if (left > 0 && remGrossCgst > igstForCgst) {
                            val extraC = minOf(remGrossCgst - igstForCgst, left)
                            igstForCgst += extraC
                        }
                    }
                }
                Rule88AStrategy.EQUAL_SPLIT -> {
                    val half = remItcIgst / 2
                    val allocCgst = minOf(remGrossCgst, half)
                    igstForCgst = allocCgst
                    val rem = remItcIgst - allocCgst
                    val allocSgst = minOf(remGrossSgst, rem)
                    igstForSgst = allocSgst
                    val rem2 = rem - allocSgst
                    if (rem2 > 0 && remGrossCgst > igstForCgst) {
                        val extraC = minOf(remGrossCgst - igstForCgst, rem2)
                        igstForCgst += extraC
                    }
                }
                Rule88AStrategy.CGST_FIRST -> {
                    val allocCgst = minOf(remGrossCgst, remItcIgst)
                    igstForCgst = allocCgst
                    val rem = remItcIgst - allocCgst
                    val allocSgst = minOf(remGrossSgst, rem)
                    igstForSgst = allocSgst
                }
                Rule88AStrategy.SGST_FIRST -> {
                    val allocSgst = minOf(remGrossSgst, remItcIgst)
                    igstForSgst = allocSgst
                    val rem = remItcIgst - allocSgst
                    val allocCgst = minOf(remGrossCgst, rem)
                    igstForCgst = allocCgst
                }
                Rule88AStrategy.CUSTOM -> {
                    val desiredCgst = when {
                        customIgstForCgstPaise != null -> customIgstForCgstPaise
                        customIgstForCgstPercent != null -> ((remItcIgst * customIgstForCgstPercent.coerceIn(0.0, 100.0)) / 100.0).toLong()
                        else -> remItcIgst / 2
                    }
                    val validCgst = minOf(remGrossCgst, minOf(remItcIgst, maxOf(0L, desiredCgst)))
                    igstForCgst = validCgst
                    val rem = remItcIgst - validCgst
                    val validSgst = minOf(remGrossSgst, rem)
                    igstForSgst = validSgst
                    val rem2 = rem - validSgst
                    if (rem2 > 0 && remGrossCgst > igstForCgst) {
                        val extra = minOf(remGrossCgst - igstForCgst, rem2)
                        igstForCgst += extra
                    }
                }
            }

            remGrossCgst = maxOf(0L, remGrossCgst - igstForCgst)
            remGrossSgst = maxOf(0L, remGrossSgst - igstForSgst)
            remItcIgst = maxOf(0L, remItcIgst - (igstForCgst + igstForSgst))

            steps.add("Step 2: Allocated balance IGST credit [${strategy.displayName}]: ${FinancialEngine.formatPaise(igstForCgst)} to CGST liability and ${FinancialEngine.formatPaise(igstForSgst)} to SGST liability.")
        } else {
            steps.add("Step 2: No balance IGST credit remaining after Step 1.")
        }

        // Step 3: Utilize CGST credit against remaining CGST liability, then against remaining IGST liability
        val cgstForCgst = minOf(remGrossCgst, remItcCgst)
        remGrossCgst -= cgstForCgst
        remItcCgst -= cgstForCgst

        val cgstForIgst = minOf(remGrossIgst, remItcCgst)
        remGrossIgst -= cgstForIgst
        remItcCgst -= cgstForIgst
        steps.add("Step 3: Utilized CGST credit: ${FinancialEngine.formatPaise(cgstForCgst)} against CGST and ${FinancialEngine.formatPaise(cgstForIgst)} against IGST. Cross-utilization against SGST is prohibited by Section 49B.")

        // Step 4: Utilize SGST credit against remaining SGST liability, then against remaining IGST liability
        val sgstForSgst = minOf(remGrossSgst, remItcSgst)
        remGrossSgst -= sgstForSgst
        remItcSgst -= sgstForSgst

        val sgstForIgst = minOf(remGrossIgst, remItcSgst)
        remGrossIgst -= sgstForIgst
        remItcSgst -= sgstForIgst
        steps.add("Step 4: Utilized SGST credit: ${FinancialEngine.formatPaise(sgstForSgst)} against SGST and ${FinancialEngine.formatPaise(sgstForIgst)} against IGST. Cross-utilization against CGST is prohibited by Section 49B.")

        // Step 5: Cash liabilities payable through Electronic Cash Ledger
        val cashIgst = maxOf(0L, remGrossIgst)
        val cashCgst = maxOf(0L, remGrossCgst)
        val cashSgst = maxOf(0L, remGrossSgst)
        val totalCash = cashIgst + cashCgst + cashSgst
        val totalCarriedForward = remItcIgst + remItcCgst + remItcSgst

        steps.add("Step 5: Net cash payable via Electronic Cash Ledger: ${FinancialEngine.formatPaise(totalCash)} (IGST: ${FinancialEngine.formatPaise(cashIgst)}, CGST: ${FinancialEngine.formatPaise(cashCgst)}, SGST: ${FinancialEngine.formatPaise(cashSgst)}). Total ITC carried forward: ${FinancialEngine.formatPaise(totalCarriedForward)}.")

        // Compute savings compared to 50:50 equal split if not already equal split
        val savings = if (strategy != Rule88AStrategy.EQUAL_SPLIT) {
            val equalMatrix = computeRule88ASetOff(
                grossIgst, grossCgst, grossSgst, itcIgst, itcCgst, itcSgst,
                strategy = Rule88AStrategy.EQUAL_SPLIT
            )
            maxOf(0L, equalMatrix.totalCashPayablePaise - totalCash)
        } else 0L

        return Rule88ASetOffMatrix(
            grossIgstPaise = grossIgst,
            grossCgstPaise = grossCgst,
            grossSgstPaise = grossSgst,
            totalGrossLiabilityPaise = grossIgst + grossCgst + grossSgst,
            itcIgstPaise = itcIgst,
            itcCgstPaise = itcCgst,
            itcSgstPaise = itcSgst,
            totalItcAvailablePaise = itcIgst + itcCgst + itcSgst,
            igstUsedForIgstPaise = igstForIgst,
            igstUsedForCgstPaise = igstForCgst,
            igstUsedForSgstPaise = igstForSgst,
            totalIgstUtilizedPaise = igstForIgst + igstForCgst + igstForSgst,
            cgstUsedForCgstPaise = cgstForCgst,
            cgstUsedForIgstPaise = cgstForIgst,
            totalCgstUtilizedPaise = cgstForCgst + cgstForIgst,
            sgstUsedForSgstPaise = sgstForSgst,
            sgstUsedForIgstPaise = sgstForIgst,
            totalSgstUtilizedPaise = sgstForSgst + sgstForIgst,
            cashIgstPaise = cashIgst,
            cashCgstPaise = cashCgst,
            cashSgstPaise = cashSgst,
            totalCashPayablePaise = totalCash,
            carriedForwardIgstPaise = remItcIgst,
            carriedForwardCgstPaise = remItcCgst,
            carriedForwardSgstPaise = remItcSgst,
            totalCarriedForwardItcPaise = totalCarriedForward,
            strategy = strategy,
            cashSavingsPaise = savings,
            explanationSteps = steps
        )
    }

    fun compareAllRule88AStrategies(
        grossIgst: Long,
        grossCgst: Long,
        grossSgst: Long,
        itcIgst: Long,
        itcCgst: Long,
        itcSgst: Long
    ): Rule88AStrategyComparison {
        val optimal = computeRule88ASetOff(grossIgst, grossCgst, grossSgst, itcIgst, itcCgst, itcSgst, Rule88AStrategy.OPTIMAL)
        val equal = computeRule88ASetOff(grossIgst, grossCgst, grossSgst, itcIgst, itcCgst, itcSgst, Rule88AStrategy.EQUAL_SPLIT)
        val cgstFirst = computeRule88ASetOff(grossIgst, grossCgst, grossSgst, itcIgst, itcCgst, itcSgst, Rule88AStrategy.CGST_FIRST)
        val sgstFirst = computeRule88ASetOff(grossIgst, grossCgst, grossSgst, itcIgst, itcCgst, itcSgst, Rule88AStrategy.SGST_FIRST)
        val savings = maxOf(0L, equal.totalCashPayablePaise - optimal.totalCashPayablePaise)
        return Rule88AStrategyComparison(
            optimal = optimal,
            equalSplit = equal,
            cgstFirst = cgstFirst,
            sgstFirst = sgstFirst,
            cashSavedPaise = savings
        )
    }

    fun computeStatutoryGstr1(
        transactions: List<Transaction>,
        parties: List<Party>,
        profile: BusinessProfile,
        periodFilter: GstPeriodFilter = GstPeriodFilter.AllTime,
        prefilledInvoices: List<Gstr1ExcelImportEngine.ImportedGstr1Invoice> = emptyList()
    ): StatutoryGstr1Data {
        val b2bList = mutableListOf<Gstr1B2bInvoice>()
        val cdnrList = mutableListOf<Gstr1CreditDebitNote>()
        val exportList = mutableListOf<Gstr1ExportInvoice>()
        val b2csMap = mutableMapOf<Pair<String, Int>, Gstr1B2csSupply>()
        val hsnMap = mutableMapOf<Pair<String, Int>, Gstr1HsnItem>()

        var b2bCount = 0
        var b2bTaxable = 0L
        var b2bCgst = 0L
        var b2bSgst = 0L
        var b2bIgst = 0L

        var b2cCount = 0
        var b2cTaxable = 0L
        var b2cCgst = 0L
        var b2cSgst = 0L
        var b2cIgst = 0L

        var exportTaxable = 0L
        var exportIgst = 0L

        var nilExemptPaise = 0L
        var nonGstOutwardPaise = 0L

        var totalIssued = 0
        var cancelledCount = 0
        val invoiceNumbers = mutableListOf<String>()

        var creditNoteCount = 0
        var creditNoteCancelledCount = 0
        val creditNoteNumbers = mutableListOf<String>()

        var debitNoteCount = 0
        var debitNoteCancelledCount = 0
        val debitNoteNumbers = mutableListOf<String>()

        val filteredTxns = transactions.filter { isDateInPeriod(it.date, periodFilter) }

        fun addToHsn(
            hsnCode: String,
            desc: String,
            uqc: String,
            qtyMilli: Long,
            gstRateBps: Int,
            taxable: Long,
            cgst: Long,
            sgst: Long,
            igst: Long
        ) {
            val cleanHsn = hsnCode.ifBlank { "9999" }
            val hsnKey = Pair(cleanHsn, gstRateBps)
            val prevHsn = hsnMap[hsnKey]
            val wholeRate = kotlin.math.round(gstRateBps / 100.0).toInt()
            if (prevHsn == null) {
                hsnMap[hsnKey] = Gstr1HsnItem(
                    hsnCode = cleanHsn,
                    description = desc,
                    uqc = uqc.ifBlank { "NOS" },
                    totalQuantity = ((qtyMilli + 500L) / 1000L).toInt(),
                    taxablePaise = taxable,
                    cgstPaise = cgst,
                    sgstPaise = sgst,
                    igstPaise = igst,
                    gstRate = wholeRate,
                    totalQuantityMilli = qtyMilli,
                    gstRateBps = gstRateBps
                )
            } else {
                val nextMilli = prevHsn.totalQuantityMilli + qtyMilli
                hsnMap[hsnKey] = prevHsn.copy(
                    totalQuantity = ((nextMilli + 500L) / 1000L).toInt(),
                    totalQuantityMilli = nextMilli,
                    taxablePaise = prevHsn.taxablePaise + taxable,
                    cgstPaise = prevHsn.cgstPaise + cgst,
                    sgstPaise = prevHsn.sgstPaise + sgst,
                    igstPaise = prevHsn.igstPaise + igst
                )
            }
        }

        filteredTxns.forEach { txn ->
            when (txn.type) {
                TransactionType.NonGstBill -> {
                    if (!txn.isVoided) {
                        nonGstOutwardPaise += txn.amountPaise
                    }
                }
                TransactionType.CreditNote, TransactionType.DebitNote -> {
                    val party = parties.find { it.id == txn.partyId }
                    if (txn.type == TransactionType.DebitNote && party?.isSupplier == true) {
                        // Supplier purchase return reduces ITC in GSTR-3B, not outward GSTR-1 CDNR
                        return@forEach
                    }
                    val isDebit = txn.type == TransactionType.DebitNote
                    if (isDebit) {
                        debitNoteCount++
                        if (txn.invoiceNumber.isNotBlank()) debitNoteNumbers.add(txn.invoiceNumber)
                    } else {
                        creditNoteCount++
                        if (txn.invoiceNumber.isNotBlank()) creditNoteNumbers.add(txn.invoiceNumber)
                    }
                    if (txn.isVoided) {
                        if (isDebit) debitNoteCancelledCount++ else creditNoteCancelledCount++
                        return@forEach
                    }
                    val isB2b = party != null && FinancialEngine.isValidGstinFormat(party.gstin)
                    val buyerPos = party?.stateCode?.ifBlank { profile.homeStateCode } ?: profile.homeStateCode
                    val isInterstate = buyerPos.isNotBlank() && buyerPos != profile.homeStateCode

                    var txnTaxable = 0L
                    var txnCgst = 0L
                    var txnSgst = 0L
                    var txnIgst = 0L
                    val rateBuckets = mutableMapOf<Int, Gstr1B2bRateItem>()

                    if (txn.items.isNotEmpty()) {
                        txn.items.forEach { itm ->
                            txnTaxable += itm.taxableAmountPaise
                            txnCgst += itm.cgstPaise
                            txnSgst += itm.sgstPaise
                            txnIgst += itm.igstPaise
                            val prev = rateBuckets[itm.gstRateBps]
                            rateBuckets[itm.gstRateBps] = if (prev == null) {
                                Gstr1B2bRateItem(itm.gstRate, itm.taxableAmountPaise, itm.cgstPaise, itm.sgstPaise, itm.igstPaise, itm.gstRateBps)
                            } else {
                                prev.copy(
                                    taxablePaise = prev.taxablePaise + itm.taxableAmountPaise,
                                    cgstPaise = prev.cgstPaise + itm.cgstPaise,
                                    sgstPaise = prev.sgstPaise + itm.sgstPaise,
                                    igstPaise = prev.igstPaise + itm.igstPaise
                                )
                            }
                        }
                    } else {
                        txnTaxable = txn.amountPaise
                        txnCgst = 0L
                        txnSgst = 0L
                        txnIgst = 0L
                        rateBuckets[0] = Gstr1B2bRateItem(0, txnTaxable, 0L, 0L, 0L, 0)
                    }

                    if (isB2b && party != null) {
                        cdnrList.add(
                            Gstr1CreditDebitNote(
                                partyGstin = party.gstin.trim().uppercase(Locale.ENGLISH),
                                partyName = party.name,
                                noteNumber = txn.invoiceNumber.ifBlank { txn.id.takeLast(6).uppercase(Locale.ENGLISH) },
                                noteDate = txn.date,
                                noteType = if (isDebit) "D" else "C",
                                posStateCode = buyerPos,
                                taxablePaise = txnTaxable,
                                cgstPaise = txnCgst,
                                sgstPaise = txnSgst,
                                igstPaise = txnIgst,
                                totalPaise = txnTaxable + txnCgst + txnSgst + txnIgst,
                                rateBreakdown = rateBuckets.values.toList()
                            )
                        )
                    }
                }
                TransactionType.Sale -> {
                    totalIssued++
                    if (txn.invoiceNumber.isNotBlank()) invoiceNumbers.add(txn.invoiceNumber)

                    if (txn.isVoided) {
                        cancelledCount++
                        return@forEach
                    }

                    val party = parties.find { it.id == txn.partyId }
                    val isB2b = party != null && FinancialEngine.isValidGstinFormat(party.gstin)
                    val buyerPos = party?.stateCode?.ifBlank { profile.homeStateCode } ?: profile.homeStateCode
                    val isExport = buyerPos == "96"
                    val isInterstate = buyerPos.isNotBlank() && buyerPos != profile.homeStateCode

                    var txnTaxable = 0L
                    var txnCgst = 0L
                    var txnSgst = 0L
                    var txnIgst = 0L
                    val rateBuckets = mutableMapOf<Int, Gstr1B2bRateItem>()

                    if (txn.items.isNotEmpty()) {
                        txn.items.forEach { itm ->
                            val taxable = itm.taxableAmountPaise
                            val cgst = itm.cgstPaise
                            val sgst = itm.sgstPaise
                            val igst = itm.igstPaise
                            txnTaxable += taxable
                            txnCgst += cgst
                            txnSgst += sgst
                            txnIgst += igst

                            val prevRate = rateBuckets[itm.gstRateBps]
                            rateBuckets[itm.gstRateBps] = if (prevRate == null) {
                                Gstr1B2bRateItem(itm.gstRate, taxable, cgst, sgst, igst, itm.gstRateBps)
                            } else {
                                prevRate.copy(
                                    taxablePaise = prevRate.taxablePaise + taxable,
                                    cgstPaise = prevRate.cgstPaise + cgst,
                                    sgstPaise = prevRate.sgstPaise + sgst,
                                    igstPaise = prevRate.igstPaise + igst
                                )
                            }

                            if (!isB2b && !isExport && itm.gstRateBps == 0) {
                                nilExemptPaise += taxable
                            }

                            addToHsn(
                                hsnCode = itm.hsnCode,
                                desc = itm.name,
                                uqc = itm.unit,
                                qtyMilli = itm.quantityMilli,
                                gstRateBps = itm.gstRateBps,
                                taxable = taxable,
                                cgst = cgst,
                                sgst = sgst,
                                igst = igst
                            )

                            if (!isB2b && !isExport) {
                                val b2csKey = Pair(buyerPos, itm.gstRateBps)
                                val prevB2cs = b2csMap[b2csKey]
                                val supplyType = if (isInterstate) "INTER" else "INTRA"
                                if (prevB2cs == null) {
                                    b2csMap[b2csKey] = Gstr1B2csSupply(
                                        posStateCode = buyerPos,
                                        supplyType = supplyType,
                                        gstRate = itm.gstRate,
                                        taxablePaise = taxable,
                                        cgstPaise = cgst,
                                        sgstPaise = sgst,
                                        igstPaise = igst,
                                        gstRateBps = itm.gstRateBps
                                    )
                                } else {
                                    b2csMap[b2csKey] = prevB2cs.copy(
                                        taxablePaise = prevB2cs.taxablePaise + taxable,
                                        cgstPaise = prevB2cs.cgstPaise + cgst,
                                        sgstPaise = prevB2cs.sgstPaise + sgst,
                                        igstPaise = prevB2cs.igstPaise + igst
                                    )
                                }
                            }
                        }
                    } else {
                        txnTaxable = txn.amountPaise
                        txnCgst = 0L
                        txnSgst = 0L
                        txnIgst = 0L
                        rateBuckets[0] = Gstr1B2bRateItem(
                            gstRate = 0,
                            taxablePaise = txnTaxable,
                            cgstPaise = 0L,
                            sgstPaise = 0L,
                            igstPaise = 0L,
                            gstRateBps = 0
                        )
                        if (!isB2b && !isExport) {
                            nilExemptPaise += txnTaxable
                            val b2csKey = Pair(buyerPos, 0)
                            val prevB2cs = b2csMap[b2csKey]
                            val supplyType = if (isInterstate) "INTER" else "INTRA"
                            if (prevB2cs == null) {
                                b2csMap[b2csKey] = Gstr1B2csSupply(
                                    posStateCode = buyerPos,
                                    supplyType = supplyType,
                                    gstRate = 0,
                                    taxablePaise = txnTaxable,
                                    cgstPaise = 0L,
                                    sgstPaise = 0L,
                                    igstPaise = 0L,
                                    gstRateBps = 0
                                )
                            } else {
                                b2csMap[b2csKey] = prevB2cs.copy(
                                    taxablePaise = prevB2cs.taxablePaise + txnTaxable
                                )
                            }
                        }
                    }

                    if (isExport) {
                        val invNo = txn.invoiceNumber.ifBlank { txn.id.takeLast(6).uppercase(Locale.ENGLISH) }
                        val rateBps = rateBuckets.keys.firstOrNull() ?: 0
                        exportList.add(
                            Gstr1ExportInvoice(
                                invoiceNumber = invNo,
                                date = txn.date,
                                exportType = if (txnIgst > 0L) "WPAY" else "WOPAY",
                                taxablePaise = txnTaxable,
                                igstPaise = txnIgst,
                                totalPaise = txnTaxable + txnIgst,
                                gstRate = rateBps / 100,
                                gstRateBps = rateBps
                            )
                        )
                        exportTaxable += txnTaxable
                        exportIgst += txnIgst
                    } else if (isB2b) {
                        b2bCount++
                        b2bTaxable += txnTaxable
                        b2bCgst += txnCgst
                        b2bSgst += txnSgst
                        b2bIgst += txnIgst

                        val itemDesc = if (txn.items.isNotEmpty()) {
                            txn.items.joinToString(", ") { "${it.name} (${FinancialEngine.formatQuantityMilli(it.quantityMilli)})" }
                        } else "General Goods/Services"

                        b2bList.add(
                            Gstr1B2bInvoice(
                                partyId = txn.partyId ?: "",
                                gstin = party?.gstin?.trim()?.uppercase(Locale.ENGLISH) ?: "",
                                partyName = party?.name ?: "B2B Customer",
                                invoiceNumber = txn.invoiceNumber.ifBlank { txn.id.takeLast(6).uppercase(Locale.ENGLISH) },
                                date = txn.date,
                                posStateCode = buyerPos,
                                taxablePaise = txnTaxable,
                                cgstPaise = txnCgst,
                                sgstPaise = txnSgst,
                                igstPaise = txnIgst,
                                totalPaise = txnTaxable + txnCgst + txnSgst + txnIgst,
                                isInterstate = isInterstate,
                                itemsSummary = itemDesc,
                                isPrefilledFromExcel = false,
                                rateBreakdown = rateBuckets.values.toList()
                            )
                        )
                    } else {
                        b2cCount++
                        b2cTaxable += txnTaxable
                        b2cCgst += txnCgst
                        b2cSgst += txnSgst
                        b2cIgst += txnIgst
                    }
                }
                else -> {}
            }
        }

        // Merge Excel Pre-filled Invoices
        val filteredPrefilled = prefilledInvoices.filter { isDateInPeriod(it.date, periodFilter) }
        filteredPrefilled.forEach { imp ->
            totalIssued++
            if (imp.invoiceNumber.isNotBlank()) invoiceNumbers.add(imp.invoiceNumber)

            val rateItem = Gstr1B2bRateItem(
                gstRate = imp.gstRatePercent,
                taxablePaise = imp.taxablePaise,
                cgstPaise = imp.cgstPaise,
                sgstPaise = imp.sgstPaise,
                igstPaise = imp.igstPaise,
                gstRateBps = imp.gstRateBps
            )
            if (imp.posStateCode == "96") {
                exportList.add(
                    Gstr1ExportInvoice(
                        invoiceNumber = imp.invoiceNumber,
                        date = imp.date,
                        exportType = if (imp.igstPaise > 0L) "WPAY" else "WOPAY",
                        taxablePaise = imp.taxablePaise,
                        igstPaise = imp.igstPaise,
                        totalPaise = imp.totalPaise,
                        gstRate = imp.gstRatePercent,
                        gstRateBps = imp.gstRateBps
                    )
                )
                exportTaxable += imp.taxablePaise
                exportIgst += imp.igstPaise
            } else if (imp.isB2b) {
                b2bCount++
                b2bTaxable += imp.taxablePaise
                b2bCgst += imp.cgstPaise
                b2bSgst += imp.sgstPaise
                b2bIgst += imp.igstPaise

                b2bList.add(
                    Gstr1B2bInvoice(
                        partyId = "EXCEL-${imp.gstin.takeLast(4)}",
                        gstin = imp.gstin,
                        partyName = imp.partyName,
                        invoiceNumber = imp.invoiceNumber,
                        date = imp.date,
                        posStateCode = imp.posStateCode,
                        taxablePaise = imp.taxablePaise,
                        cgstPaise = imp.cgstPaise,
                        sgstPaise = imp.sgstPaise,
                        igstPaise = imp.igstPaise,
                        totalPaise = imp.totalPaise,
                        isInterstate = imp.isInterstate,
                        itemsSummary = "Pre-filled from Excel (HSN: ${imp.hsnCode})",
                        isPrefilledFromExcel = true,
                        rateBreakdown = listOf(rateItem)
                    )
                )
            } else {
                b2cCount++
                b2cTaxable += imp.taxablePaise
                b2cCgst += imp.cgstPaise
                b2cSgst += imp.sgstPaise
                b2cIgst += imp.igstPaise
                if (imp.gstRateBps == 0) {
                    nilExemptPaise += imp.taxablePaise
                }

                val b2csKey = Pair(imp.posStateCode, imp.gstRateBps)
                val prevB2cs = b2csMap[b2csKey]
                val supplyType = if (imp.isInterstate) "INTER" else "INTRA"
                if (prevB2cs == null) {
                    b2csMap[b2csKey] = Gstr1B2csSupply(
                        posStateCode = imp.posStateCode,
                        supplyType = supplyType,
                        gstRate = imp.gstRatePercent,
                        taxablePaise = imp.taxablePaise,
                        cgstPaise = imp.cgstPaise,
                        sgstPaise = imp.sgstPaise,
                        igstPaise = imp.igstPaise,
                        gstRateBps = imp.gstRateBps
                    )
                } else {
                    b2csMap[b2csKey] = prevB2cs.copy(
                        taxablePaise = prevB2cs.taxablePaise + imp.taxablePaise,
                        cgstPaise = prevB2cs.cgstPaise + imp.cgstPaise,
                        sgstPaise = prevB2cs.sgstPaise + imp.sgstPaise,
                        igstPaise = prevB2cs.igstPaise + imp.igstPaise
                    )
                }
            }

            addToHsn(
                hsnCode = imp.hsnCode,
                desc = "${imp.partyName} (Excel)",
                uqc = "OTH",
                qtyMilli = 1000L,
                gstRateBps = imp.gstRateBps,
                taxable = imp.taxablePaise,
                cgst = imp.cgstPaise,
                sgst = imp.sgstPaise,
                igst = imp.igstPaise
            )
        }

        invoiceNumbers.sort()
        creditNoteNumbers.sort()
        debitNoteNumbers.sort()
        val docSummary = Gstr1DocItem(
            minInvoiceNumber = invoiceNumbers.firstOrNull() ?: "",
            maxInvoiceNumber = invoiceNumbers.lastOrNull() ?: "",
            totalIssued = totalIssued,
            cancelledCount = cancelledCount,
            netIssuedCount = (totalIssued - cancelledCount).coerceAtLeast(0)
        )

        val totalTaxable = b2bTaxable + b2cTaxable + exportTaxable
        val totalCgst = b2bCgst + b2cCgst
        val totalSgst = b2bSgst + b2cSgst
        val totalIgst = b2bIgst + b2cIgst + exportIgst
        val totalTax = totalCgst + totalSgst + totalIgst

        return StatutoryGstr1Data(
            b2bInvoices = b2bList,
            b2csSupplies = b2csMap.values.toList(),
            hsnSummary = hsnMap.values.toList(),
            docSummary = docSummary,
            b2bCount = b2bCount,
            b2bTaxablePaise = b2bTaxable,
            b2bCgstPaise = b2bCgst,
            b2bSgstPaise = b2bSgst,
            b2bIgstPaise = b2bIgst,
            b2cCount = b2cCount,
            b2cTaxablePaise = b2cTaxable,
            b2cCgstPaise = b2cCgst,
            b2cSgstPaise = b2cSgst,
            b2cIgstPaise = b2cIgst,
            totalTaxablePaise = totalTaxable,
            totalCgstPaise = totalCgst,
            totalSgstPaise = totalSgst,
            totalIgstPaise = totalIgst,
            totalTaxPaise = totalTax,
            prefilledExcelCount = filteredPrefilled.size,
            cdnrNotes = cdnrList,
            exportInvoices = exportList,
            nilExemptPaise = nilExemptPaise,
            nonGstOutwardPaise = nonGstOutwardPaise,
            creditNoteCount = creditNoteCount,
            creditNoteNumbers = creditNoteNumbers,
            creditNoteCancelledCount = creditNoteCancelledCount,
            debitNoteCount = debitNoteCount,
            debitNoteNumbers = debitNoteNumbers,
            debitNoteCancelledCount = debitNoteCancelledCount
        )
    }

    fun computeStatutoryGstr3b(
        transactions: List<Transaction>,
        parties: List<Party>,
        profile: BusinessProfile,
        gstr1: StatutoryGstr1Data,
        periodFilter: GstPeriodFilter = GstPeriodFilter.AllTime,
        strategy: Rule88AStrategy = Rule88AStrategy.OPTIMAL,
        customIgstForCgstPercent: Double? = null,
        customIgstForCgstPaise: Long? = null
    ): StatutoryGstr3bData {
        // Net Credit Notes (Sales Returns) and Customer Debit Notes on outward supplies for GSTR-3B Table 3.1(a)
        val filteredTxns = transactions.filter { !it.isVoided && isDateInPeriod(it.date, periodFilter) }
        var cnTaxable = 0L
        var cnIgst = 0L
        var cnCgst = 0L
        var cnSgst = 0L
        filteredTxns.filter { it.type == TransactionType.CreditNote }.forEach { cn ->
            if (cn.items.isNotEmpty()) {
                cn.items.forEach { itm ->
                    cnTaxable += itm.taxableAmountPaise
                    cnIgst += itm.igstPaise
                    cnCgst += itm.cgstPaise
                    cnSgst += itm.sgstPaise
                }
            } else {
                cnTaxable += cn.amountPaise
            }
        }

        var dnOutTaxable = 0L
        var dnOutIgst = 0L
        var dnOutCgst = 0L
        var dnOutSgst = 0L
        filteredTxns.filter { txn ->
            if (txn.type != TransactionType.DebitNote) return@filter false
            val party = parties.find { it.id == txn.partyId }
            party?.isSupplier != true
        }.forEach { dn ->
            if (dn.items.isNotEmpty()) {
                dn.items.forEach { itm ->
                    dnOutTaxable += itm.taxableAmountPaise
                    dnOutIgst += itm.igstPaise
                    dnOutCgst += itm.cgstPaise
                    dnOutSgst += itm.sgstPaise
                }
            } else {
                dnOutTaxable += dn.amountPaise
            }
        }

        val outwardTaxable = (gstr1.totalTaxablePaise + dnOutTaxable - cnTaxable).coerceAtLeast(0L)
        val outwardIgst = (gstr1.totalIgstPaise + dnOutIgst - cnIgst).coerceAtLeast(0L)
        val outwardCgst = (gstr1.totalCgstPaise + dnOutCgst - cnCgst).coerceAtLeast(0L)
        val outwardSgst = (gstr1.totalSgstPaise + dnOutSgst - cnSgst).coerceAtLeast(0L)

        // Calculate net Input Tax Credit (ITC) = Purchase ITC minus Supplier Debit Note (Purchase Return) reversals
        var itcIgst = 0L
        var itcCgst = 0L
        var itcSgst = 0L

        filteredTxns.filter { txn ->
            when (txn.type) {
                TransactionType.Purchase -> true
                TransactionType.DebitNote -> parties.find { it.id == txn.partyId }?.isSupplier == true
                else -> false
            }
        }.forEach { pTxn ->
            val sign = if (pTxn.type == TransactionType.DebitNote) -1L else 1L

            if (pTxn.items.isNotEmpty()) {
                pTxn.items.forEach { itm ->
                    itcIgst += itm.igstPaise * sign
                    itcCgst += itm.cgstPaise * sign
                    itcSgst += itm.sgstPaise * sign
                }
            }
        }
        val netItcIgst = itcIgst.coerceAtLeast(0L)
        val netItcCgst = itcCgst.coerceAtLeast(0L)
        val netItcSgst = itcSgst.coerceAtLeast(0L)

        val totalItc = netItcIgst + netItcCgst + netItcSgst
        val setOff = computeRule88ASetOff(
            grossIgst = outwardIgst,
            grossCgst = outwardCgst,
            grossSgst = outwardSgst,
            itcIgst = netItcIgst,
            itcCgst = netItcCgst,
            itcSgst = netItcSgst,
            strategy = strategy,
            customIgstForCgstPercent = customIgstForCgstPercent,
            customIgstForCgstPaise = customIgstForCgstPaise
        )

        return StatutoryGstr3bData(
            outwardTaxablePaise = outwardTaxable,
            outwardIgstPaise = outwardIgst,
            outwardCgstPaise = outwardCgst,
            outwardSgstPaise = outwardSgst,
            eligibleItcIgstPaise = netItcIgst,
            eligibleItcCgstPaise = netItcCgst,
            eligibleItcSgstPaise = netItcSgst,
            totalEligibleItcPaise = totalItc,
            setOffMatrix = setOff
        )
    }

    fun computeStatutoryCmp08(
        transactions: List<Transaction>,
        profile: BusinessProfile,
        periodFilter: GstPeriodFilter = GstPeriodFilter.AllTime,
        compositionRatePercent: Double = 1.0 // 1% for traders & manufacturers (0.5% CGST + 0.5% SGST)
    ): StatutoryCmp08Data {
        var turnoverPaise = 0L
        var rcmTurnoverPaise = 0L
        var rcmCentralTax = 0L
        var rcmStateTax = 0L
        var rcmIntegratedTax = 0L
        var salesCount = 0

        val filteredTxns = transactions.filter { !it.isVoided && isDateInPeriod(it.date, periodFilter) }

        filteredTxns.forEach { txn ->
            when (txn.type) {
                TransactionType.Sale, TransactionType.NonGstBill -> {
                    turnoverPaise += txn.amountPaise
                    salesCount++
                }
                TransactionType.CreditNote -> {
                    turnoverPaise -= txn.amountPaise
                }
                TransactionType.Expense -> if (txn.expenseCategory == ExpenseCategory.Direct) {
                    val expenseAmount = txn.amountPaise
                    rcmTurnoverPaise += expenseAmount
                    // 2.5% CGST + 2.5% SGST (5% RCM) using integer half-up rounding
                    val rcmEach = (expenseAmount * 250L + 5000L) / 10000L
                    rcmCentralTax += rcmEach
                    rcmStateTax += rcmEach
                }
                else -> {}
            }
        }
        val netTurnover = turnoverPaise.coerceAtLeast(0L)
        val totalBps = kotlin.math.round(compositionRatePercent * 100.0).toLong().coerceIn(0L, 10000L)
        val centralTax = (netTurnover * totalBps + 10000L) / 20000L
        val stateTax = centralTax
        val totalCompositionTax = centralTax + stateTax

        val rcmTotalTax = rcmCentralTax + rcmStateTax + rcmIntegratedTax
        val netCashPayable = totalCompositionTax + rcmTotalTax

        return StatutoryCmp08Data(
            turnoverPaise = netTurnover,
            compositionRatePercent = compositionRatePercent,
            centralTaxPaise = centralTax,
            stateTaxPaise = stateTax,
            totalCompositionTaxPaise = totalCompositionTax,
            rcmTurnoverPaise = rcmTurnoverPaise,
            rcmCentralTaxPaise = rcmCentralTax,
            rcmStateTaxPaise = rcmStateTax,
            rcmIntegratedTaxPaise = rcmIntegratedTax,
            rcmTotalTaxPaise = rcmTotalTax,
            interestPaise = 0L,
            netCashPayablePaise = netCashPayable,
            transactionsCount = salesCount
        )
    }



    // -------------------------------------------------------------
    // 3. Bank Reconciliation Engine
    // -------------------------------------------------------------
    fun reconcileBankFeed(
        statementItems: List<BankReconItem>,
        ledgerTxns: List<Transaction>,
        parties: List<Party> = emptyList()
    ): List<BankReconItem> {
        return BankReconEngine.matchBankStatement(statementItems, ledgerTxns, parties)
    }
}
