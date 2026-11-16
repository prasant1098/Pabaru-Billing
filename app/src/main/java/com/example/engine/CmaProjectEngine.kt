package com.example.engine

import com.example.model.BalanceType
import com.example.model.ExpenseCategory
import com.example.model.Party
import com.example.model.ProductItem
import com.example.model.Transaction
import com.example.model.TransactionType
import java.util.Locale
import kotlin.math.pow
import kotlin.math.roundToLong

/**
 * Credit Monitoring Arrangement (CMA) & Project Report Engine for Bank Loan Applications.
 * Implements official RBI & Indian Commercial Banking Standards:
 * - Form I: Existing & Proposed Credit Facilities, Project Cost & Means of Finance
 * - Form II: Operating Statement (P&L with Cost of Production, WIP, Finished Goods, GA&S overheads, EBIT, EBT, PAT)
 * - Form III: Balance Sheet Analysis (Current Liabilities, Term Debt, Tangible Net Worth, Current Assets, Net Fixed Assets)
 * - Form IV: Comparative Statement of Current Assets & Current Liabilities
 * - Form V: Maximum Permissible Bank Finance (MPBF) under:
 *     1. Tandon Committee Method 1 (75% of Working Capital Gap)
 *     2. Tandon Committee Method 2 (75% TCA - OCL)
 *     3. Nayak Committee MSME Turnover Method (20% of Projected Turnover)
 * - Form VI: Funds Flow Statement (Sources vs Applications & Movement in NWC)
 * - Form VII: Monthly Term Loan EMI Amortization Schedule with Moratorium & Annual Rollup
 * - 19 Key Banking Ratios evaluated against RBI Benchmarks
 */
object CmaProjectEngine {

    enum class CmaYearStage {
        AuditedLastYear,
        CurrentYearEstimate,
        ProjectionYear1,
        ProjectionYear2
    }

    data class CmaYearFinancials(
        val stage: CmaYearStage,
        val fyLabel: String, // e.g. "FY 2024-25"
        val stageLabel: String, // e.g. "Audited", "Estimate", "Proj Y1", "Proj Y2"

        // --- Form II: Operating Performance (Paise) ---
        var grossSalesPaise: Long = 0L,
        var salesReturnsAndDeductionsPaise: Long = 0L,
        var nonOperatingIncomePaise: Long = 0L,

        // Cost of Production
        var rawMaterialPurchasesPaise: Long = 0L,
        var directWagesPaise: Long = 0L,
        var directPowerAndFreightPaise: Long = 0L,
        var otherDirectExpensesPaise: Long = 0L,

        // Stock Movements
        var openingStockPaise: Long = 0L,
        var closingStockPaise: Long = 0L,

        // GA&S Expenses
        var salariesAndAdminPaise: Long = 0L,
        var rentAndUtilitiesPaise: Long = 0L,
        var sellingAndMarketingPaise: Long = 0L,
        var otherOverheadsPaise: Long = 0L,

        // Depreciation & Finance Charges
        var depreciationPaise: Long = 0L,
        var termLoanInterestPaise: Long = 0L,
        var workingCapitalInterestPaise: Long = 0L,
        var nonOperatingExpensesPaise: Long = 0L,
        var taxRatePercent: Double = 25.0,
        var termLoanPrincipalInstallmentPaise: Long = 0L,

        // --- Form III: Current Assets (Paise) ---
        var cashAndBankPaise: Long = 0L,
        var sundryDebtorsPaise: Long = 0L,
        var inventoryStockPaise: Long = 0L,
        var advancesToSuppliersPaise: Long = 0L,
        var otherCurrentAssetsPaise: Long = 0L,

        // Fixed Assets & Non-Current Assets
        var grossFixedAssetsPaise: Long = 0L,
        var accumulatedDepreciationPaise: Long = 0L,
        var otherNonCurrentAssetsPaise: Long = 0L,

        // --- Form III: Liabilities & Capital (Paise) ---
        // Current Liabilities
        var sundryCreditorsPaise: Long = 0L,
        var otherCurrentLiabilitiesPaise: Long = 0L,
        var provisionsPaise: Long = 0L,
        var existingBankCcLimitPaise: Long = 0L,

        // Long Term Debt & Net Worth
        var termLoanOutstandingPaise: Long = 0L,
        var unsecuredLoansPaise: Long = 0L,
        var shareCapitalPaise: Long = 0L,
        var reservesAndSurplusPaise: Long = 0L,
        var promoterDrawingsPaise: Long = 0L
    ) {
        // --- Form II: Computed Operating Metrics ---
        val netSalesPaise: Long get() = (grossSalesPaise - salesReturnsAndDeductionsPaise).coerceAtLeast(0L)

        val costOfProductionPaise: Long
            get() = rawMaterialPurchasesPaise + directWagesPaise + directPowerAndFreightPaise + otherDirectExpensesPaise

        val costOfSalesPaise: Long
            get() = costOfProductionPaise + openingStockPaise - closingStockPaise

        val cogsPaise: Long get() = costOfSalesPaise

        val grossProfitPaise: Long get() = netSalesPaise - costOfSalesPaise
        val gpMarginPercent: Double
            get() = if (netSalesPaise > 0) (grossProfitPaise.toDouble() / netSalesPaise) * 100.0 else 0.0

        val totalGeneralAdminSellingExpPaise: Long
            get() = salariesAndAdminPaise + rentAndUtilitiesPaise + sellingAndMarketingPaise + otherOverheadsPaise

        // Operating Profit Before Interest & Depreciation (EBITDA)
        val ebitdaPaise: Long get() = grossProfitPaise - totalGeneralAdminSellingExpPaise
        val ebitdaMarginPercent: Double
            get() = if (netSalesPaise > 0) (ebitdaPaise.toDouble() / netSalesPaise) * 100.0 else 0.0

        // Operating Profit After Depreciation (EBIT)
        val ebitPaise: Long get() = ebitdaPaise - depreciationPaise

        val totalFinanceChargesPaise: Long get() = termLoanInterestPaise + workingCapitalInterestPaise

        // Profit Before Non-Operating & Tax
        val operatingProfitAfterInterestPaise: Long get() = ebitPaise - totalFinanceChargesPaise

        // Profit Before Tax (PBT)
        val pbtPaise: Long
            get() = operatingProfitAfterInterestPaise + nonOperatingIncomePaise - nonOperatingExpensesPaise

        val taxPaise: Long get() = if (pbtPaise > 0) ((pbtPaise * (taxRatePercent / 100.0)).roundToLong()) else 0L

        // Profit After Tax (PAT)
        val patPaise: Long get() = pbtPaise - taxPaise
        val patMarginPercent: Double
            get() = if (netSalesPaise > 0) (patPaise.toDouble() / netSalesPaise) * 100.0 else 0.0

        val retainedProfitPaise: Long get() = (patPaise - promoterDrawingsPaise).coerceAtLeast(0L)

        // Cash Accruals = PAT + Depreciation
        val cashAccrualsPaise: Long get() = patPaise + depreciationPaise

        // --- Form III & IV: Assets & Liabilities Computations ---
        val totalCurrentAssetsPaise: Long
            get() = cashAndBankPaise + sundryDebtorsPaise + inventoryStockPaise + advancesToSuppliersPaise + otherCurrentAssetsPaise

        val netFixedAssetsPaise: Long
            get() = (grossFixedAssetsPaise - accumulatedDepreciationPaise).coerceAtLeast(0L)

        val totalAssetsPaise: Long
            get() = totalCurrentAssetsPaise + netFixedAssetsPaise + otherNonCurrentAssetsPaise

        // Current Liabilities Other Than Bank Borrowings (OCL)
        val currentLiabilitiesOtherThanBankPaise: Long
            get() = sundryCreditorsPaise + otherCurrentLiabilitiesPaise + provisionsPaise

        // Total Current Liabilities (TCL = OCL + Bank Short-Term Borrowing)
        val totalCurrentLiabilitiesPaise: Long
            get() = currentLiabilitiesOtherThanBankPaise + existingBankCcLimitPaise

        // Total Long-Term Debt
        val totalTermLiabilitiesPaise: Long
            get() = termLoanOutstandingPaise + unsecuredLoansPaise

        // Total Outside Liabilities (TOL = TCL + Term Liabilities)
        val totalOutsideLiabilitiesPaise: Long
            get() = totalCurrentLiabilitiesPaise + totalTermLiabilitiesPaise

        // Tangible Net Worth (TNW)
        val tangibleNetWorthPaise: Long
            get() = shareCapitalPaise + reservesAndSurplusPaise

        val totalLiabilitiesAndCapitalPaise: Long
            get() = totalOutsideLiabilitiesPaise + tangibleNetWorthPaise

        // Net Working Capital (NWC = TCA - TCL)
        val netWorkingCapitalPaise: Long
            get() = totalCurrentAssetsPaise - totalCurrentLiabilitiesPaise

        // --- Form V: Maximum Permissible Bank Finance (MPBF) ---
        // Working Capital Gap (WCG = TCA - OCL)
        val workingCapitalGapPaise: Long
            get() = (totalCurrentAssetsPaise - currentLiabilitiesOtherThanBankPaise).coerceAtLeast(0L)

        // 1. Tandon Committee Method 1:
        // Min NWC = 25% of WCG. MPBF = 75% of WCG.
        val tandonMethod1MinNwcPaise: Long
            get() = (workingCapitalGapPaise * 0.25).roundToLong()

        val mpbfTandonMethod1Paise: Long
            get() = (workingCapitalGapPaise * 0.75).roundToLong().coerceAtLeast(0L)

        // 2. Tandon Committee Method 2 (Official RBI Benchmark for Medium/Large WC):
        // Min NWC = 25% of Total Current Assets. MPBF = 75% TCA - OCL.
        val tandonMethod2MinNwcPaise: Long
            get() = (totalCurrentAssetsPaise * 0.25).roundToLong()

        val mpbfTandonMethod2Paise: Long
            get() = ((totalCurrentAssetsPaise * 0.75) - currentLiabilitiesOtherThanBankPaise).roundToLong().coerceAtLeast(0L)

        // 3. Nayak Committee Turnover Method (for MSME limits up to ₹5-10 Cr):
        // Total WC Requirement = 25% of Turnover; Margin by Promoter = 5%; Bank Finance = 20% of Turnover.
        val nayakTotalWcRequirementPaise: Long
            get() = (netSalesPaise * 0.25).roundToLong()

        val nayakPromoterMarginPaise: Long
            get() = (netSalesPaise * 0.05).roundToLong()

        val nayakCommitteeWorkingCapitalPaise: Long
            get() = (netSalesPaise * 0.20).roundToLong()

        // Alias for backwards compatibility
        val minPromoterContributionPaise: Long get() = tandonMethod2MinNwcPaise

        // --- 19 Key Banking Ratios Evaluated Against RBI Benchmarks ---

        // 1. Current Ratio (CR) = TCA / TCL (RBI Benchmark: >= 1.33)
        val currentRatio: Double
            get() = if (totalCurrentLiabilitiesPaise > 0) totalCurrentAssetsPaise.toDouble() / totalCurrentLiabilitiesPaise else 2.5

        // 2. Quick Ratio (QR) = (TCA - Inventory) / TCL (RBI Benchmark: >= 1.00)
        val quickRatio: Double
            get() = if (totalCurrentLiabilitiesPaise > 0) {
                (totalCurrentAssetsPaise - inventoryStockPaise).toDouble() / totalCurrentLiabilitiesPaise
            } else 2.0

        // 3. Absolute Liquid Ratio = Cash & Bank / TCL (RBI Benchmark: >= 0.50)
        val absoluteLiquidRatio: Double
            get() = if (totalCurrentLiabilitiesPaise > 0) cashAndBankPaise.toDouble() / totalCurrentLiabilitiesPaise else 0.60

        // 4. Debt to Equity Ratio (DER) = Total Term Debt / TNW (RBI Benchmark: <= 2.00 : 1)
        val debtEquityRatio: Double
            get() = if (tangibleNetWorthPaise > 0) termLoanOutstandingPaise.toDouble() / tangibleNetWorthPaise else 0.8

        // 5. Total Outside Liabilities to TNW (TOL/TNW) (RBI Benchmark: <= 3.00 : 1)
        val tolToTnwRatio: Double
            get() = if (tangibleNetWorthPaise > 0) totalOutsideLiabilitiesPaise.toDouble() / tangibleNetWorthPaise else 1.8

        // 6. Proprietary Ratio = TNW / Total Assets (RBI Benchmark: >= 0.30)
        val proprietaryRatio: Double
            get() = if (totalAssetsPaise > 0) tangibleNetWorthPaise.toDouble() / totalAssetsPaise else 0.45

        // 7. Fixed Assets to Long-Term Funds = Net Fixed Assets / (TNW + Term Loans) (RBI Benchmark: <= 1.00)
        val fixedAssetsToLongTermFundsRatio: Double
            get() {
                val longTermFunds = tangibleNetWorthPaise + termLoanOutstandingPaise
                return if (longTermFunds > 0) netFixedAssetsPaise.toDouble() / longTermFunds else 0.65
            }

        // 8. Interest Coverage Ratio (ICR) = EBITDA / Total Finance Charges (RBI Benchmark: >= 2.50)
        val interestCoverageRatio: Double
            get() = if (totalFinanceChargesPaise > 0) ebitdaPaise.toDouble() / totalFinanceChargesPaise else 4.0

        // 9. Debt Service Coverage Ratio (DSCR) = (PAT + Depr + TL Interest) / (Principal + TL Interest) (RBI Benchmark: 1.50 - 2.00)
        val dscr: Double
            get() {
                val debtObligations = termLoanPrincipalInstallmentPaise + termLoanInterestPaise
                return if (debtObligations > 0) {
                    (patPaise + depreciationPaise + termLoanInterestPaise).toDouble() / debtObligations
                } else 3.5
            }

        // 10. Debtor Velocity (Days) = (Debtors / Net Sales) * 365
        val debtorDays: Int
            get() = if (netSalesPaise > 0) ((sundryDebtorsPaise.toDouble() / netSalesPaise) * 365).toInt() else 30

        // 11. Creditor Velocity (Days) = (Creditors / Purchases) * 365
        val creditorDays: Int
            get() = if (rawMaterialPurchasesPaise > 0) ((sundryCreditorsPaise.toDouble() / rawMaterialPurchasesPaise) * 365).toInt() else 30

        // 12. Inventory Holding (Days) = (Inventory / COGS) * 365
        val inventoryDays: Int
            get() = if (cogsPaise > 0) ((inventoryStockPaise.toDouble() / cogsPaise) * 365).toInt() else 45

        // 13. Gross Operating Cycle (Days) = Debtor Days + Inventory Days
        val grossOperatingCycleDays: Int get() = debtorDays + inventoryDays

        // 14. Net Operating Cycle / Working Capital Cycle (Days) = Gross Cycle - Creditor Days
        val netWorkingCapitalCycleDays: Int get() = grossOperatingCycleDays - creditorDays

        // 15. NWC to Net Sales %
        val nwcToNetSalesPercent: Double
            get() = if (netSalesPaise > 0) (netWorkingCapitalPaise.toDouble() / netSalesPaise) * 100.0 else 0.0

        // 16. Gross Profit Margin %
        val grossProfitMarginPercent: Double get() = gpMarginPercent

        // 17. EBITDA Margin %
        val operatingEbitdaMarginPercent: Double get() = ebitdaMarginPercent

        // 18. Net Profit Margin (PAT %)
        val netProfitMarginPercent: Double get() = patMarginPercent

        // 19. Return on Capital Employed (ROCE %) = EBIT / (TNW + Total Debt) * 100
        val returnOnCapitalEmployedPercent: Double
            get() {
                val capitalEmployed = tangibleNetWorthPaise + totalTermLiabilitiesPaise + existingBankCcLimitPaise
                return if (capitalEmployed > 0) (ebitPaise.toDouble() / capitalEmployed) * 100.0 else 18.0
            }
    }

    // -------------------------------------------------------------
    // Form VI: Funds Flow Statement Data Model
    // -------------------------------------------------------------
    data class CmaFundsFlowYear(
        val fyLabel: String,
        val stageLabel: String,

        // Sources of Funds
        val patPaise: Long,
        val depreciationPaise: Long,
        val cashAccrualsPaise: Long,
        val capitalInfusionPaise: Long,
        val increaseInTermLoanPaise: Long,
        val increaseInUnsecuredLoansPaise: Long,
        val decreaseInFixedAssetsPaise: Long,
        val totalSourcesPaise: Long,

        // Applications of Funds
        val capitalExpenditurePaise: Long, // Additions to Fixed Assets
        val repaymentOfTermLoanPaise: Long,
        val decreaseInUnsecuredLoansPaise: Long,
        val promoterDrawingsPaise: Long,
        val totalApplicationsPaise: Long,

        // Balance & NWC Movement
        val netSurplusOrDeficitPaise: Long, // Sources - Applications
        val openingNwcPaise: Long,
        val closingNwcPaise: Long,
        val changeInNwcPaise: Long // Closing NWC - Opening NWC
    )

    fun computeFundsFlowSchedule(dossier: CmaProjectDossier): List<CmaFundsFlowYear> {
        val result = mutableListOf<CmaFundsFlowYear>()
        val years = dossier.years

        for (i in years.indices) {
            val curr = years[i]
            val prev = if (i > 0) years[i - 1] else null

            val pat = curr.patPaise
            val depr = curr.depreciationPaise
            val accruals = pat + depr

            val capDiff = if (prev != null) curr.shareCapitalPaise - prev.shareCapitalPaise else 0L
            val capInfusion = if (capDiff > 0) capDiff else 0L

            val tlDiff = if (prev != null) curr.termLoanOutstandingPaise - prev.termLoanOutstandingPaise else 0L
            val tlIncrease = if (tlDiff > 0) tlDiff else 0L
            val tlRepayment = if (tlDiff < 0) -tlDiff else curr.termLoanPrincipalInstallmentPaise

            val unsecDiff = if (prev != null) curr.unsecuredLoansPaise - prev.unsecuredLoansPaise else 0L
            val unsecIncrease = if (unsecDiff > 0) unsecDiff else 0L
            val unsecDecrease = if (unsecDiff < 0) -unsecDiff else 0L

            val gfaDiff = if (prev != null) curr.grossFixedAssetsPaise - prev.grossFixedAssetsPaise else 0L
            val capex = if (gfaDiff > 0) gfaDiff else 0L
            val faSale = if (gfaDiff < 0) -gfaDiff else 0L

            val totalSources = accruals + capInfusion + tlIncrease + unsecIncrease + faSale
            val totalApps = capex + tlRepayment + unsecDecrease + curr.promoterDrawingsPaise

            val netSurplus = totalSources - totalApps
            val opNwc = prev?.netWorkingCapitalPaise ?: (curr.netWorkingCapitalPaise - netSurplus)
            val clNwc = curr.netWorkingCapitalPaise
            val deltaNwc = clNwc - opNwc

            result.add(
                CmaFundsFlowYear(
                    fyLabel = curr.fyLabel,
                    stageLabel = curr.stageLabel,
                    patPaise = pat,
                    depreciationPaise = depr,
                    cashAccrualsPaise = accruals,
                    capitalInfusionPaise = capInfusion,
                    increaseInTermLoanPaise = tlIncrease,
                    increaseInUnsecuredLoansPaise = unsecIncrease,
                    decreaseInFixedAssetsPaise = faSale,
                    totalSourcesPaise = totalSources,
                    capitalExpenditurePaise = capex,
                    repaymentOfTermLoanPaise = tlRepayment,
                    decreaseInUnsecuredLoansPaise = unsecDecrease,
                    promoterDrawingsPaise = curr.promoterDrawingsPaise,
                    totalApplicationsPaise = totalApps,
                    netSurplusOrDeficitPaise = netSurplus,
                    openingNwcPaise = opNwc,
                    closingNwcPaise = clNwc,
                    changeInNwcPaise = deltaNwc
                )
            )
        }
        return result
    }

    // -------------------------------------------------------------
    // Form VII: Term Loan Monthly EMI Amortization Schedule
    // -------------------------------------------------------------
    data class TermLoanAmortizationMonth(
        val monthNumber: Int,
        val openingPrincipalPaise: Long,
        val emiPaise: Long,
        val interestPaise: Long,
        val principalPaise: Long,
        val closingPrincipalPaise: Long,
        val isMoratorium: Boolean
    )

    data class TermLoanAnnualSummary(
        val yearIndex: Int, // 1, 2, 3...
        val openingPrincipalPaise: Long,
        val totalEmiPaidPaise: Long,
        val totalInterestPaidPaise: Long,
        val totalPrincipalRepaidPaise: Long,
        val closingPrincipalPaise: Long
    )

    data class TermLoanSchedule(
        val loanAmountPaise: Long,
        val annualRatePercent: Double,
        val tenorMonths: Int,
        val moratoriumMonths: Int,
        val monthlyEmiPaise: Long,
        val totalInterestPayablePaise: Long,
        val totalPaymentPaise: Long,
        val monthlySchedule: List<TermLoanAmortizationMonth>,
        val annualSummaries: List<TermLoanAnnualSummary>
    )

    fun calculateTermLoanSchedule(
        principalPaise: Long,
        annualRatePercent: Double,
        tenorMonths: Int,
        moratoriumMonths: Int = 0
    ): TermLoanSchedule {
        if (principalPaise <= 0L || tenorMonths <= 0) {
            return TermLoanSchedule(0L, annualRatePercent, tenorMonths, moratoriumMonths, 0L, 0L, 0L, emptyList(), emptyList())
        }

        val safeMoratorium = moratoriumMonths.coerceIn(0, (tenorMonths - 1).coerceAtLeast(0))
        val r = (annualRatePercent / 12.0) / 100.0
        val effectiveTenor = (tenorMonths - safeMoratorium).coerceAtLeast(1)

        // Standard Reducing Balance EMI Formula: E = P * r * (1+r)^n / ((1+r)^n - 1)
        val emiPaise = if (r > 0.0) {
            val factor = (1.0 + r).pow(effectiveTenor.toDouble())
            val numerator = principalPaise.toDouble() * r * factor
            val denominator = factor - 1.0
            (numerator / denominator).roundToLong()
        } else {
            principalPaise / effectiveTenor
        }

        val monthlyList = mutableListOf<TermLoanAmortizationMonth>()
        var currentBalance = principalPaise
        var cumulativeInterest = 0L
        var cumulativePayment = 0L

        for (m in 1..tenorMonths) {
            val isMora = m <= safeMoratorium
            val interest = (currentBalance.toDouble() * r).roundToLong()

            val (mEmi, mPrincipal) = if (isMora) {
                // Moratorium: Pay only interest
                interest to 0L
            } else {
                val principalPart = (emiPaise - interest).coerceAtMost(currentBalance).coerceAtLeast(0L)
                val actualEmi = if (m == tenorMonths) (currentBalance + interest) else (interest + principalPart)
                actualEmi to (actualEmi - interest)
            }

            val closingBalance = (currentBalance - mPrincipal).coerceAtLeast(0L)

            monthlyList.add(
                TermLoanAmortizationMonth(
                    monthNumber = m,
                    openingPrincipalPaise = currentBalance,
                    emiPaise = mEmi,
                    interestPaise = interest,
                    principalPaise = mPrincipal,
                    closingPrincipalPaise = closingBalance,
                    isMoratorium = isMora
                )
            )

            cumulativeInterest += interest
            cumulativePayment += mEmi
            currentBalance = closingBalance
        }

        // Annual rollup summaries
        val annualSummaries = mutableListOf<TermLoanAnnualSummary>()
        var yearIdx = 1
        var mIdx = 0
        while (mIdx < monthlyList.size) {
            val chunk = monthlyList.subList(mIdx, (mIdx + 12).coerceAtMost(monthlyList.size))
            val yrOpening = chunk.first().openingPrincipalPaise
            val yrClosing = chunk.last().closingPrincipalPaise
            val yrEmi = chunk.sumOf { it.emiPaise }
            val yrInt = chunk.sumOf { it.interestPaise }
            val yrPrin = chunk.sumOf { it.principalPaise }

            annualSummaries.add(
                TermLoanAnnualSummary(
                    yearIndex = yearIdx,
                    openingPrincipalPaise = yrOpening,
                    totalEmiPaidPaise = yrEmi,
                    totalInterestPaidPaise = yrInt,
                    totalPrincipalRepaidPaise = yrPrin,
                    closingPrincipalPaise = yrClosing
                )
            )
            yearIdx++
            mIdx += 12
        }

        return TermLoanSchedule(
            loanAmountPaise = principalPaise,
            annualRatePercent = annualRatePercent,
            tenorMonths = tenorMonths,
            moratoriumMonths = safeMoratorium,
            monthlyEmiPaise = emiPaise,
            totalInterestPayablePaise = cumulativeInterest,
            totalPaymentPaise = cumulativePayment,
            monthlySchedule = monthlyList,
            annualSummaries = annualSummaries
        )
    }

    // -------------------------------------------------------------
    // Form I & Overall Dossier Data Models
    // -------------------------------------------------------------
    data class CmaProjectDossier(
        val baseStartYear: Int, // e.g. 2024 for FY 2024-25
        val years: List<CmaYearFinancials>
    )

    data class BankerFeedbackItem(
        val title: String,
        val benchmark: String,
        val currentValue: String,
        val status: FeedbackStatus,
        val analysis: String,
        val recommendation: String
    )

    enum class FeedbackStatus {
        Healthy,
        Acceptable,
        Caution,
        Critical
    }

    val AVAILABLE_BASE_YEARS = listOf(
        2023 to "FY 2023-24",
        2024 to "FY 2024-25",
        2025 to "FY 2025-26",
        2026 to "FY 2026-27"
    )

    fun createInitialDossier(
        baseYear: Int,
        transactions: List<Transaction> = emptyList(),
        parties: List<Party> = emptyList(),
        products: List<ProductItem> = emptyList(),
        cashAndBankPaise: Long = 26000000L
    ): CmaProjectDossier {
        val y1Label = "FY $baseYear-${(baseYear + 1).toString().takeLast(2)}"
        val y2Label = "FY ${baseYear + 1}-${(baseYear + 2).toString().takeLast(2)}"
        val y3Label = "FY ${baseYear + 2}-${(baseYear + 3).toString().takeLast(2)}"
        val y4Label = "FY ${baseYear + 3}-${(baseYear + 4).toString().takeLast(2)}"

        // Compute actual numbers from live transactions for Current Year (Year 2)
        var liveSales = 0L
        var liveReturns = 0L
        var livePurchases = 0L
        var liveDirectExp = 0L
        var liveIndirectExp = 0L

        transactions.filter { !it.isVoided }.forEach { t ->
            val excl = if (t.items.isNotEmpty()) t.items.sumOf { it.taxableAmountPaise } else t.amountPaise
            when (t.type) {
                TransactionType.Sale, TransactionType.NonGstBill -> liveSales += excl
                TransactionType.CreditNote -> liveReturns += excl
                TransactionType.Purchase -> livePurchases += excl
                TransactionType.DebitNote -> livePurchases -= excl
                TransactionType.Expense -> {
                    if (t.expenseCategory == ExpenseCategory.Direct) liveDirectExp += excl
                    else liveIndirectExp += excl
                }
                else -> {}
            }
        }
        livePurchases = livePurchases.coerceAtLeast(0L)

        val seedSalesY2 = if (liveSales > 5000000L) liveSales else 125000000L // 1.25 Cr
        val seedReturnsY2 = if (liveReturns > 0L) liveReturns else 2500000L // 25 L
        val seedPurchasesY2 = if (livePurchases > 3000000L) livePurchases else 88000000L
        val seedDirectExpY2 = if (liveDirectExp > 0L) liveDirectExp else 3500000L
        val seedIndirectExpY2 = if (liveIndirectExp > 0L) liveIndirectExp else 9500000L

        val liveDebtors = parties.filter { it.openingBalanceType == BalanceType.Receivable }.sumOf { it.openingBalancePaise }
        val seedDebtorsY2 = if (liveDebtors > 0L) liveDebtors else 16500000L // 16.5 L
        val liveCreditors = parties.filter { it.openingBalanceType == BalanceType.Payable }.sumOf { it.openingBalancePaise }
        val seedCreditorsY2 = if (liveCreditors > 0L) liveCreditors else 12000000L
        val liveStock = products.filter { !it.isArchived }.sumOf { (it.currentStockMilli * it.purchasePricePaise + 500L) / 1000L }
        val seedStockY2 = if (liveStock > 0L) liveStock else 21000000L

        // Year 1: Audited Last Year (~85% of Current Year)
        val y1 = CmaYearFinancials(
            stage = CmaYearStage.AuditedLastYear,
            fyLabel = y1Label,
            stageLabel = "Audited Last Year",
            grossSalesPaise = (seedSalesY2 * 0.85).roundToLong(),
            salesReturnsAndDeductionsPaise = (seedReturnsY2 * 0.85).roundToLong(),
            nonOperatingIncomePaise = 150000L,
            rawMaterialPurchasesPaise = (seedPurchasesY2 * 0.85).roundToLong(),
            directWagesPaise = 2000000L,
            directPowerAndFreightPaise = (seedDirectExpY2 * 0.85).roundToLong(),
            otherDirectExpensesPaise = 500000L,
            openingStockPaise = (seedStockY2 * 0.80).roundToLong(),
            closingStockPaise = (seedStockY2 * 0.88).roundToLong(),
            salariesAndAdminPaise = (seedIndirectExpY2 * 0.50).roundToLong(),
            rentAndUtilitiesPaise = (seedIndirectExpY2 * 0.25).roundToLong(),
            sellingAndMarketingPaise = (seedIndirectExpY2 * 0.15).roundToLong(),
            otherOverheadsPaise = (seedIndirectExpY2 * 0.10).roundToLong(),
            depreciationPaise = 250000L,
            termLoanInterestPaise = 420000L,
            workingCapitalInterestPaise = 950000L,
            nonOperatingExpensesPaise = 80000L,
            taxRatePercent = 25.0,
            termLoanPrincipalInstallmentPaise = 600000L,
            cashAndBankPaise = 15000000L,
            sundryDebtorsPaise = (seedDebtorsY2 * 0.85).roundToLong(),
            inventoryStockPaise = (seedStockY2 * 0.88).roundToLong(),
            advancesToSuppliersPaise = 1000000L,
            otherCurrentAssetsPaise = 1000000L,
            grossFixedAssetsPaise = 32000000L,
            accumulatedDepreciationPaise = 4500000L,
            otherNonCurrentAssetsPaise = 1500000L,
            sundryCreditorsPaise = (seedCreditorsY2 * 0.85).roundToLong(),
            otherCurrentLiabilitiesPaise = 2000000L,
            provisionsPaise = 500000L,
            existingBankCcLimitPaise = 10000000L,
            termLoanOutstandingPaise = 30000000L,
            unsecuredLoansPaise = 5000000L,
            shareCapitalPaise = 25000000L,
            reservesAndSurplusPaise = 20000000L,
            promoterDrawingsPaise = 1200000L
        )

        // Year 2: Current Year Estimate / Provisional
        val y2 = CmaYearFinancials(
            stage = CmaYearStage.CurrentYearEstimate,
            fyLabel = y2Label,
            stageLabel = "Current Year (Est.)",
            grossSalesPaise = seedSalesY2,
            salesReturnsAndDeductionsPaise = seedReturnsY2,
            nonOperatingIncomePaise = 200000L,
            rawMaterialPurchasesPaise = seedPurchasesY2,
            directWagesPaise = 2400000L,
            directPowerAndFreightPaise = seedDirectExpY2,
            otherDirectExpensesPaise = 600000L,
            openingStockPaise = (seedStockY2 * 0.88).roundToLong(),
            closingStockPaise = seedStockY2,
            salariesAndAdminPaise = (seedIndirectExpY2 * 0.52).roundToLong(),
            rentAndUtilitiesPaise = (seedIndirectExpY2 * 0.24).roundToLong(),
            sellingAndMarketingPaise = (seedIndirectExpY2 * 0.16).roundToLong(),
            otherOverheadsPaise = (seedIndirectExpY2 * 0.08).roundToLong(),
            depreciationPaise = 300000L,
            termLoanInterestPaise = 350000L,
            workingCapitalInterestPaise = 1150000L,
            nonOperatingExpensesPaise = 50000L,
            taxRatePercent = 25.0,
            termLoanPrincipalInstallmentPaise = 600000L,
            cashAndBankPaise = cashAndBankPaise,
            sundryDebtorsPaise = seedDebtorsY2,
            inventoryStockPaise = seedStockY2,
            advancesToSuppliersPaise = 1200000L,
            otherCurrentAssetsPaise = 1300000L,
            grossFixedAssetsPaise = 38000000L,
            accumulatedDepreciationPaise = 4800000L,
            otherNonCurrentAssetsPaise = 1800000L,
            sundryCreditorsPaise = seedCreditorsY2,
            otherCurrentLiabilitiesPaise = 2500000L,
            provisionsPaise = 500000L,
            existingBankCcLimitPaise = 12500000L,
            termLoanOutstandingPaise = 24000000L,
            unsecuredLoansPaise = 6000000L,
            shareCapitalPaise = 25000000L,
            reservesAndSurplusPaise = 33000000L,
            promoterDrawingsPaise = 1500000L
        )

        // Year 3: Projection Year 1 (+18% growth)
        val y3 = projectNextYear(y2, y3Label, CmaYearStage.ProjectionYear1, "Projection Year 1", growthRate = 0.18)

        // Year 4: Projection Year 2 (+15% growth over Year 3)
        val y4 = projectNextYear(y3, y4Label, CmaYearStage.ProjectionYear2, "Projection Year 2", growthRate = 0.15)

        return CmaProjectDossier(baseStartYear = baseYear, years = listOf(y1, y2, y3, y4))
    }

    fun projectNextYear(
        prev: CmaYearFinancials,
        fyLabel: String,
        stage: CmaYearStage,
        stageLabel: String,
        growthRate: Double = 0.15
    ): CmaYearFinancials {
        val multiplier = 1.0 + growthRate
        val projectedGrossSales = (prev.grossSalesPaise * multiplier).roundToLong()
        val projectedReturns = (prev.salesReturnsAndDeductionsPaise * multiplier).roundToLong()
        val projectedPurchases = (prev.rawMaterialPurchasesPaise * multiplier).roundToLong()
        val projectedWages = (prev.directWagesPaise * (1.0 + growthRate * 0.8)).roundToLong()
        val projectedPower = (prev.directPowerAndFreightPaise * multiplier).roundToLong()
        val projectedOtherDirect = (prev.otherDirectExpensesPaise * multiplier).roundToLong()

        val projectedSalaries = (prev.salariesAndAdminPaise * (1.0 + growthRate * 0.75)).roundToLong()
        val projectedRent = (prev.rentAndUtilitiesPaise * 1.05).roundToLong() // +5% annual rent escalation
        val projectedMarketing = (prev.sellingAndMarketingPaise * (1.0 + growthRate * 0.85)).roundToLong()
        val projectedOverheads = (prev.otherOverheadsPaise * 1.05).roundToLong()

        val projectedDebtors = (prev.sundryDebtorsPaise * (1.0 + (growthRate * 0.90))).roundToLong()
        val projectedStock = (prev.inventoryStockPaise * (1.0 + (growthRate * 0.85))).roundToLong()
        val projectedCreditors = (prev.sundryCreditorsPaise * (1.0 + (growthRate * 0.90))).roundToLong()

        val proposedCc = ((projectedStock + projectedDebtors - projectedCreditors) * 0.75).roundToLong().coerceAtLeast(prev.existingBankCcLimitPaise)
        val tlRepayment = prev.termLoanPrincipalInstallmentPaise
        val tlOutstanding = (prev.termLoanOutstandingPaise - tlRepayment).coerceAtLeast(0L)
        val tlInterest = (tlOutstanding * 0.095).roundToLong() // ~9.5% p.a.

        val newDepr = (prev.depreciationPaise * 0.90).roundToLong() // WDV depreciation
        val newGfa = prev.grossFixedAssetsPaise + 2500000L // routine capex
        val newAccDepr = prev.accumulatedDepreciationPaise + newDepr

        val newReserves = prev.reservesAndSurplusPaise + prev.retainedProfitPaise

        return CmaYearFinancials(
            stage = stage,
            fyLabel = fyLabel,
            stageLabel = stageLabel,
            grossSalesPaise = projectedGrossSales,
            salesReturnsAndDeductionsPaise = projectedReturns,
            nonOperatingIncomePaise = (prev.nonOperatingIncomePaise * 1.05).roundToLong(),
            rawMaterialPurchasesPaise = projectedPurchases,
            directWagesPaise = projectedWages,
            directPowerAndFreightPaise = projectedPower,
            otherDirectExpensesPaise = projectedOtherDirect,
            openingStockPaise = prev.closingStockPaise,
            closingStockPaise = projectedStock,
            salariesAndAdminPaise = projectedSalaries,
            rentAndUtilitiesPaise = projectedRent,
            sellingAndMarketingPaise = projectedMarketing,
            otherOverheadsPaise = projectedOverheads,
            depreciationPaise = newDepr,
            termLoanInterestPaise = tlInterest,
            workingCapitalInterestPaise = (proposedCc * 0.095).roundToLong(), // ~9.5% CC interest
            nonOperatingExpensesPaise = prev.nonOperatingExpensesPaise,
            taxRatePercent = prev.taxRatePercent,
            termLoanPrincipalInstallmentPaise = tlRepayment,
            cashAndBankPaise = (prev.cashAndBankPaise * 1.10).roundToLong(),
            sundryDebtorsPaise = projectedDebtors,
            inventoryStockPaise = projectedStock,
            advancesToSuppliersPaise = (prev.advancesToSuppliersPaise * 1.08).roundToLong(),
            otherCurrentAssetsPaise = (prev.otherCurrentAssetsPaise * 1.05).roundToLong(),
            grossFixedAssetsPaise = newGfa,
            accumulatedDepreciationPaise = newAccDepr,
            otherNonCurrentAssetsPaise = prev.otherNonCurrentAssetsPaise,
            sundryCreditorsPaise = projectedCreditors,
            otherCurrentLiabilitiesPaise = (prev.otherCurrentLiabilitiesPaise * 1.05).roundToLong(),
            provisionsPaise = (prev.provisionsPaise * 1.05).roundToLong(),
            existingBankCcLimitPaise = proposedCc,
            termLoanOutstandingPaise = tlOutstanding,
            unsecuredLoansPaise = prev.unsecuredLoansPaise,
            shareCapitalPaise = prev.shareCapitalPaise,
            reservesAndSurplusPaise = newReserves,
            promoterDrawingsPaise = (prev.promoterDrawingsPaise * 1.10).roundToLong()
        )
    }

    /**
     * Credit Officer Assessment & Improvement Feedback against RBI Benchmarks
     */
    fun evaluateDossier(dossier: CmaProjectDossier): List<BankerFeedbackItem> {
        val currentYear = dossier.years.firstOrNull { it.stage == CmaYearStage.CurrentYearEstimate }
            ?: dossier.years.getOrNull(1)
            ?: dossier.years.firstOrNull()
            ?: return emptyList()
        val projYear = dossier.years.firstOrNull { it.stage == CmaYearStage.ProjectionYear1 }
            ?: dossier.years.getOrNull(2)
            ?: currentYear

        val feedback = mutableListOf<BankerFeedbackItem>()

        // 1. Current Ratio
        val cr = currentYear.currentRatio
        val (crStatus, crRec) = when {
            cr >= 1.33 -> FeedbackStatus.Healthy to "Current Ratio satisfies RBI Tandon guidelines (≥ 1.33). Maintain this liquidity buffer."
            cr >= 1.15 -> FeedbackStatus.Acceptable to "Current Ratio is acceptable for trading, but nationalized banks mandate ≥ 1.33. Retain more profits as working capital."
            else -> FeedbackStatus.Critical to "Current Ratio is below 1.15. Inject additional equity/promoter funds or convert short-term payables."
        }
        feedback.add(
            BankerFeedbackItem(
                title = "Current Ratio (Liquidity Buffer)",
                benchmark = "≥ 1.33 (RBI Tandon Comm.)",
                currentValue = String.format(Locale.ENGLISH, "%.2f", cr),
                status = crStatus,
                analysis = "Measures short-term debt repaying capability from current assets.",
                recommendation = crRec
            )
        )

        // 2. Quick Ratio
        val qr = currentYear.quickRatio
        val (qrStatus, qrRec) = when {
            qr >= 1.00 -> FeedbackStatus.Healthy to "Quick assets comfortably cover short-term liabilities without needing to liquidate inventory."
            qr >= 0.80 -> FeedbackStatus.Acceptable to "Slight inventory reliance. Keep inventory turn days under 60 days."
            else -> FeedbackStatus.Caution to "Low quick liquidity. Accelerate debtor collection and build cash reserves."
        }
        feedback.add(
            BankerFeedbackItem(
                title = "Quick / Acid-Test Ratio",
                benchmark = "≥ 1.00",
                currentValue = String.format(Locale.ENGLISH, "%.2f", qr),
                status = qrStatus,
                analysis = "Immediate cash and debtors coverage excluding slow-moving inventory.",
                recommendation = qrRec
            )
        )

        // 3. Debt Service Coverage Ratio (DSCR)
        val dscr = currentYear.dscr
        val (dscrStatus, dscrRec) = when {
            dscr >= 1.75 -> FeedbackStatus.Healthy to "Strong debt servicing cushion. Loan installments and term interest are safely covered."
            dscr >= 1.40 -> FeedbackStatus.Acceptable to "Adequate coverage for commercial banks. Ensure stable operating margins."
            else -> FeedbackStatus.Caution to "Tight debt servicing cushion (< 1.40). Consider requesting a longer term loan repayment tenor."
        }
        feedback.add(
            BankerFeedbackItem(
                title = "Debt Service Coverage Ratio (DSCR)",
                benchmark = "1.50 - 2.00x",
                currentValue = String.format(Locale.ENGLISH, "%.2f", dscr),
                status = dscrStatus,
                analysis = "Indicates capacity to pay loan principal installments and interest from cash profits.",
                recommendation = dscrRec
            )
        )

        // 4. Interest Coverage Ratio (ICR)
        val icr = currentYear.interestCoverageRatio
        val (icrStatus, icrRec) = when {
            icr >= 3.0 -> FeedbackStatus.Healthy to "EBITDA comfortably handles all CC and term loan interest charges."
            icr >= 2.0 -> FeedbackStatus.Acceptable to "Satisfactory coverage. Keep finance charges under strict check."
            else -> FeedbackStatus.Caution to "Interest burden is elevated. Negotiate interest rate concessions with bank or trim expensive debt."
        }
        feedback.add(
            BankerFeedbackItem(
                title = "Interest Coverage Ratio (ICR)",
                benchmark = "≥ 2.50x",
                currentValue = String.format(Locale.ENGLISH, "%.2f", icr),
                status = icrStatus,
                analysis = "Evaluates whether operating profits easily absorb total bank interest obligations.",
                recommendation = icrRec
            )
        )

        // 5. Total Outside Liabilities to Tangible Net Worth (TOL/TNW)
        val tolTnw = currentYear.tolToTnwRatio
        val (tolStatus, tolRec) = when {
            tolTnw <= 2.5 -> FeedbackStatus.Healthy to "Total outside liabilities are well proportioned against owner equity."
            tolTnw <= 3.5 -> FeedbackStatus.Acceptable to "Within banking tolerance (<= 3.5:1), but watch further debt buildup."
            else -> FeedbackStatus.Caution to "Gearing exceeds bank threshold. Infuse promoter capital or restrict outside liabilities."
        }
        feedback.add(
            BankerFeedbackItem(
                title = "TOL / Tangible Net Worth (Solvency)",
                benchmark = "≤ 3.00 : 1",
                currentValue = String.format(Locale.ENGLISH, "%.2f : 1", tolTnw),
                status = tolStatus,
                analysis = "Total claims of all outside creditors relative to tangible net worth.",
                recommendation = tolRec
            )
        )

        // 6. Working Capital Cycle (Debtors vs Creditors)
        val debtorDays = currentYear.debtorDays
        val inventoryDays = currentYear.inventoryDays
        val (cycleStatus, cycleRec) = when {
            debtorDays <= 45 && inventoryDays <= 60 -> FeedbackStatus.Healthy to "Lean cash conversion cycle. Fast receivables collection and healthy stock turnover."
            debtorDays <= 75 -> FeedbackStatus.Acceptable to "Debtor collection period is reasonable. Set up automated WhatsApp invoice reminders."
            else -> FeedbackStatus.Caution to "Debtor collection exceeds 75 days. Overdue receivables tie up your working capital limits."
        }
        feedback.add(
            BankerFeedbackItem(
                title = "Receivables & Stock Turnover Days",
                benchmark = "Debtors ≤ 60d, Stock ≤ 60d",
                currentValue = "$debtorDays days (Debtors) | $inventoryDays days (Stock)",
                status = cycleStatus,
                analysis = "Length of time cash is tied up in outstanding customer bills and unsold goods.",
                recommendation = cycleRec
            )
        )

        // 7. MPBF Eligibility (Tandon 1 vs Tandon 2 vs Nayak)
        fun formatLakhs(paise: Long): String = String.format(Locale.ENGLISH, "%.2f", paise / 10000000.0)
        val m1Lakh = formatLakhs(currentYear.mpbfTandonMethod1Paise)
        val m2Lakh = formatLakhs(currentYear.mpbfTandonMethod2Paise)
        val nayakLakh = formatLakhs(currentYear.nayakCommitteeWorkingCapitalPaise)
        val projSalesLakh = formatLakhs(projYear.netSalesPaise)
        feedback.add(
            BankerFeedbackItem(
                title = "Eligible Working Capital Limit (MPBF)",
                benchmark = "Tandon Method I & II vs Nayak Method",
                currentValue = "₹ $m1Lakh L (M1) / ₹ $m2Lakh L (M2) / ₹ $nayakLakh L (Nayak)",
                status = FeedbackStatus.Healthy,
                analysis = "Maximum Permissible Bank Finance under RBI Tandon Method 1 (75% WCG), Method 2 (75% TCA - OCL), and Nayak MSME Turnover Method (20% Turnover).",
                recommendation = "You can formally request an enhanced CC Limit of ₹ $m2Lakh Lakhs (Tandon II) or ₹ $nayakLakh Lakhs (Nayak MSME) based on your projected turnover of ₹ $projSalesLakh Lakhs."
            )
        )

        return feedback
    }

    // -------------------------------------------------------------
    // Form I: Project Report Model for Bank Loan Application
    // -------------------------------------------------------------
    data class ProjectReportData(
        var projectTitle: String = "Expansion of Commercial Trading & Distribution Facility",
        var industrySector: String = "Retail & Wholesale Trade / Commercial Services",
        var promoterName: String = "",
        var promoterBackground: String = "Experienced promoter with established track record in commercial operations, strong local goodwill, and statutory tax compliance.",
        var locationDetails: String = "",
        var proposedBankName: String = "State Bank of India / Commercial Bank",
        var loanFacilityRequested: String = "Term Loan & Working Capital Cash Credit (CC/OD)",
        var loanTenorMonths: Int = 60,
        var moratoriumMonths: Int = 6,
        var proposedInterestRatePercent: Double = 9.5,

        // Primary & Collateral Security Details
        var primarySecurity: String = "Hypothecation of entire current assets including stocks of raw materials, finished goods, and book debts/receivables.",
        var collateralSecurity: String = "Equitable mortgage of commercial shop/godown property and personal guarantee of directors/promoters.",

        // Cost of Project (Paise)
        var landAndBuildingPaise: Long = 0L,
        var plantAndMachineryPaise: Long = 25000000L, // 25 L
        var furnitureAndFixturesPaise: Long = 5000000L, // 5 L
        var workingCapitalMarginPaise: Long = 15000000L, // 15 L
        var preliminaryExpensesPaise: Long = 2000000L, // 2 L

        // Means of Finance (Paise)
        var promoterContributionPaise: Long = 11750000L, // ~25%
        var bankTermLoanPaise: Long = 35250000L, // ~75%
        var bankWorkingCapitalCcPaise: Long = 12500000L, // CC Limit
        var govtSubsidyPaise: Long = 0L,

        // Market Viability & Employment
        var marketPotential: String = "Established trading relationship with repeat retail & wholesale buyers. Projected growth supported by increased working capital limit.",
        var directEmployment: Int = 8,
        var indirectEmployment: Int = 15,
        var bankerRemarks: String = "The project demonstrates robust economic viability with healthy debt-servicing cushion and compliant promoter margin."
    ) {
        val totalProjectCostPaise: Long
            get() = landAndBuildingPaise + plantAndMachineryPaise + furnitureAndFixturesPaise + workingCapitalMarginPaise + preliminaryExpensesPaise

        val totalMeansOfFinancePaise: Long
            get() = promoterContributionPaise + bankTermLoanPaise + bankWorkingCapitalCcPaise + govtSubsidyPaise

        val promoterContributionPercent: Double
            get() = if (totalProjectCostPaise > 0) (promoterContributionPaise.toDouble() / totalProjectCostPaise) * 100.0 else 25.0

        val bankFinancePercent: Double
            get() = if (totalProjectCostPaise > 0) ((bankTermLoanPaise + bankWorkingCapitalCcPaise).toDouble() / totalProjectCostPaise) * 100.0 else 75.0
    }

    fun createDefaultProjectReport(
        profile: com.example.model.BusinessProfile,
        dossier: CmaProjectDossier
    ): ProjectReportData {
        val currentYear = dossier.years.firstOrNull { it.stage == CmaYearStage.CurrentYearEstimate }
            ?: dossier.years.getOrNull(1)
            ?: dossier.years.first()
        val wcMargin = currentYear.minPromoterContributionPaise.coerceAtLeast(10000000L)
        val ccRequired = currentYear.mpbfTandonMethod2Paise.coerceAtLeast(12500000L)
        val plant = 25000000L
        val furniture = 5000000L
        val prelim = 2000000L
        val totalCost = plant + furniture + prelim + wcMargin
        val promoterMargin = (totalCost * 0.25).roundToLong()
        val bankTL = totalCost - promoterMargin

        return ProjectReportData(
            projectTitle = "${profile.businessName} - Business Expansion & Modernization Project",
            industrySector = "Trading, Distribution & Commercial Operations",
            promoterName = profile.ownerName.ifEmpty { "Proprietor / Managing Partner" },
            promoterBackground = "Promoter has established market standing with valid GSTIN (${profile.gstin.ifEmpty { "Registered" }}) and consistent revenue growth.",
            locationDetails = profile.address.ifEmpty { "Commercial Trading Zone, State Code: ${profile.homeStateCode}" },
            proposedBankName = "State Bank of India / Commercial Bank",
            loanFacilityRequested = "Term Loan (Machinery/Setup) & Working Capital Cash Credit (CC)",
            loanTenorMonths = 60,
            moratoriumMonths = 6,
            proposedInterestRatePercent = 9.5,
            primarySecurity = "First hypothecation charge on all current assets including inventory and receivables.",
            collateralSecurity = "Mortgage of commercial property and personal guarantee of promoter.",
            landAndBuildingPaise = 0L,
            plantAndMachineryPaise = plant,
            furnitureAndFixturesPaise = furniture,
            workingCapitalMarginPaise = wcMargin,
            preliminaryExpensesPaise = prelim,
            promoterContributionPaise = promoterMargin,
            bankTermLoanPaise = bankTL,
            bankWorkingCapitalCcPaise = ccRequired,
            govtSubsidyPaise = 0L,
            marketPotential = "Established trading relationship with repeat retail & wholesale buyers. Projected growth of 18% p.a. supported by enhanced credit facilities.",
            directEmployment = 8,
            indirectEmployment = 15,
            bankerRemarks = "The project shows viable commercial feasibility, adequate DSCR (${String.format(Locale.ENGLISH, "%.2f", currentYear.dscr)}), and strong liquidity (Current Ratio ${String.format(Locale.ENGLISH, "%.2f", currentYear.currentRatio)})."
        )
    }
}
