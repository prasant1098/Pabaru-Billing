package com.example.engine

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import com.example.model.*
import com.example.viewmodel.PabaruUiState

object FinancialEngine {

    data class IndianState(val code: String, val name: String)

    val INDIAN_STATES = listOf(
        IndianState("01", "Jammu & Kashmir"),
        IndianState("02", "Himachal Pradesh"),
        IndianState("03", "Punjab"),
        IndianState("04", "Chandigarh"),
        IndianState("05", "Uttarakhand"),
        IndianState("06", "Haryana"),
        IndianState("07", "Delhi"),
        IndianState("08", "Rajasthan"),
        IndianState("09", "Uttar Pradesh"),
        IndianState("10", "Bihar"),
        IndianState("11", "Sikkim"),
        IndianState("12", "Arunachal Pradesh"),
        IndianState("13", "Nagaland"),
        IndianState("14", "Manipur"),
        IndianState("15", "Mizoram"),
        IndianState("16", "Tripura"),
        IndianState("17", "Meghalaya"),
        IndianState("18", "Assam"),
        IndianState("19", "West Bengal"),
        IndianState("20", "Jharkhand"),
        IndianState("21", "Odisha"),
        IndianState("22", "Chhattisgarh"),
        IndianState("23", "Madhya Pradesh"),
        IndianState("24", "Gujarat"),
        IndianState("26", "Dadra & Nagar Haveli and Daman & Diu"),
        IndianState("27", "Maharashtra"),
        IndianState("29", "Karnataka"),
        IndianState("30", "Goa"),
        IndianState("31", "Lakshadweep"),
        IndianState("32", "Kerala"),
        IndianState("33", "Tamil Nadu"),
        IndianState("34", "Puducherry"),
        IndianState("35", "Andaman & Nicobar Islands"),
        IndianState("36", "Telangana"),
        IndianState("37", "Andhra Pradesh"),
        IndianState("38", "Ladakh"),
        IndianState("96", "Foreign Country / Exports"),
        IndianState("97", "Other Territory")
    )

    // Formats paise into Indian currency format: ₹ 1,00,000.00
    fun formatPaise(paise: Long, compact: Boolean = false): String {
        val isNegative = paise < 0
        val absPaise = if (paise == Long.MIN_VALUE) Long.MAX_VALUE else abs(paise)

        if (compact) {
            val sign = if (isNegative) "-" else ""
            return when {
                absPaise >= 1000000000L -> String.format(Locale.ENGLISH, "%s₹ %.2f Cr", sign, absPaise / 1000000000.0)
                absPaise >= 10000000L -> String.format(Locale.ENGLISH, "%s₹ %.2f L", sign, absPaise / 10000000.0)
                absPaise >= 100000L -> String.format(Locale.ENGLISH, "%s₹ %.2f K", sign, absPaise / 100000.0)
                else -> String.format(Locale.ENGLISH, "%s₹ %.2f", sign, absPaise / 100.0)
            }
        }

        val wholePart = absPaise / 100
        val fracPart = String.format(Locale.ENGLISH, "%02d", absPaise % 100)
        var wholeStr = wholePart.toString()

        if (wholeStr.length > 3) {
            val last3 = wholeStr.substring(wholeStr.length - 3)
            val rest = wholeStr.substring(0, wholeStr.length - 3)
            val formattedRest = rest.reversed().chunked(2).joinToString(",").reversed()
            wholeStr = "$formattedRest,$last3"
        }

        val sign = if (isNegative) "-" else ""
        return "$sign₹ $wholeStr.$fracPart"
    }

    // GST Breakdown
    data class GstBreakdown(
        val cgstPaise: Long,
        val sgstPaise: Long,
        val igstPaise: Long,
        val totalPaise: Long
    )

    fun lineTaxable(qtyMilli: Long, ratePaise: Long): Long = (qtyMilli * ratePaise + 500L) / 1000L

    fun formatQuantityMilli(qtyMilli: Long): String {
        return if (qtyMilli % 1000L == 0L) {
            (qtyMilli / 1000L).toString()
        } else {
            String.format(Locale.ENGLISH, "%.3f", qtyMilli / 1000.0).trimEnd('0').trimEnd('.')
        }
    }

    fun calculateGstBps(taxablePaise: Long, rateBps: Int, isInterstate: Boolean, isZeroRatedExport: Boolean = false): GstBreakdown {
        val safeTaxable = taxablePaise.coerceAtLeast(0L)
        val safeRate = rateBps.coerceIn(0, 10000)
        if (safeTaxable <= 0L || safeRate <= 0 || isZeroRatedExport) {
            return GstBreakdown(0L, 0L, 0L, safeTaxable)
        }
        return if (isInterstate) {
            val totalTax = (safeTaxable * safeRate + 5000L) / 10000L
            GstBreakdown(0L, 0L, totalTax, safeTaxable + totalTax)
        } else {
            // Intrastate CGST and SGST must be identical (computed equally at half the GST rate)
            val cgst = (safeTaxable * safeRate + 10000L) / 20000L
            val sgst = cgst
            GstBreakdown(cgst, sgst, 0L, safeTaxable + cgst + sgst)
        }
    }

    fun calculateGst(taxablePaise: Long, gstRate: Int, isInterstate: Boolean, isZeroRatedExport: Boolean = false): GstBreakdown {
        return calculateGstBps(taxablePaise, gstRate * 100, isInterstate, isZeroRatedExport)
    }

    private val HSN_REGEX = Regex("^[0-9]{4}([0-9]{2})?([0-9]{2})?$")

    fun validateHsn(hsn: String?, allowBlank: Boolean = true): FieldValidationResult {
        if (hsn.isNullOrBlank()) {
            return if (allowBlank) FieldValidationResult(true, "Optional HSN")
            else FieldValidationResult(false, "HSN code cannot be blank")
        }
        val cleaned = hsn.trim()
        if (!cleaned.all { it.isDigit() }) {
            return FieldValidationResult(false, "HSN code must contain only numeric digits")
        }
        if (!HSN_REGEX.matches(cleaned)) {
            return FieldValidationResult(false, "Statutory GST rules require HSN to be exactly 4, 6, or 8 digits (currently ${cleaned.length} digits)")
        }
        return FieldValidationResult(true, "Valid statutory ${cleaned.length}-digit HSN code")
    }

    /**
     * Sanitizes decimal number input strings, stripping duplicate dots and non-numeric chars.
     */
    fun sanitizeDecimalInput(input: String): String {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return ""
        val sb = StringBuilder()
        var hasDecimal = false
        for (ch in trimmed) {
            if (ch.isDigit()) {
                sb.append(ch)
            } else if (ch == '.' && !hasDecimal) {
                sb.append(ch)
                hasDecimal = true
            }
        }
        return sb.toString()
    }

    /**
     * Converts a rupee string (e.g. "1,250.75" or "1250.75") into exact integer Paise without float errors.
     * Rejects negative values, negative signs, and scientific notation (e, E) by returning 0L.
     * Normalizes Unicode non-breaking spaces (\u00A0, \u2000-\u200a, \u202f, \u3000), strips currency symbols (₹, Rs., INR), and commas.
     */
    fun parsePaiseFromRupees(rupeeString: String): Long {
        val trimmed = rupeeString.trim()
        if (trimmed.isEmpty() || trimmed.contains("-") || trimmed.contains(Regex("[eE]"))) return 0L
        val withoutSymbols = trimmed
            .replace(Regex("[\\s\\u00A0\\u1680\\u2000-\\u200a\\u202f\\u205f\\u3000]+"), "")
            .replace("₹", "")
            .replace("Rs.", "", ignoreCase = true)
            .replace("Rs", "", ignoreCase = true)
            .replace("INR", "", ignoreCase = true)
            .replace(",", "")
            .replace("+", "")

        // Reject if any alphabetic or invalid characters remain or empty
        if (withoutSymbols.isEmpty() || withoutSymbols == "." || withoutSymbols.any { it !in '0'..'9' && it != '.' }) return 0L

        return try {
            val parts = withoutSymbols.split(".")
            if (parts.size > 2) return 0L
            var wholePart = if (parts[0].isEmpty()) 0L else (parts[0].toLongOrNull() ?: return 0L)
            var fracPart = if (parts.size == 2 && parts[1].isNotEmpty()) {
                val fracStr = parts[1]
                if (!fracStr.all { it in '0'..'9' }) return 0L
                if (fracStr.length > 2) {
                    val first2 = fracStr.take(2).toLongOrNull() ?: 0L
                    val thirdDigit = fracStr[2].digitToIntOrNull() ?: 0
                    if (thirdDigit >= 5) first2 + 1L else first2
                } else {
                    fracStr.padEnd(2, '0').toLongOrNull() ?: 0L
                }
            } else 0L
            if (fracPart >= 100L) {
                wholePart += fracPart / 100L
                fracPart %= 100L
            }
            wholePart * 100L + fracPart
        } catch (_: Exception) {
            0L
        }
    }

    // Computes Indian Financial Year: e.g., Oct 2026 -> 2026-27
    fun getFinancialYear(): String {
        val cal = java.util.Calendar.getInstance()
        val year = cal.get(java.util.Calendar.YEAR)
        val month = cal.get(java.util.Calendar.MONTH) + 1 // 1-12
        return if (month >= 4) {
            "$year-${(year + 1).toString().takeLast(2)}"
        } else {
            "${year - 1}-${year.toString().takeLast(2)}"
        }
    }

    // Computes compact 5-char Indian Financial Year: e.g., Oct 2026 -> 26-27 (ensures <= 16-char invoice numbers)
    fun getCompactFinancialYear(): String {
        val cal = java.util.Calendar.getInstance()
        val year = cal.get(java.util.Calendar.YEAR)
        val month = cal.get(java.util.Calendar.MONTH) + 1
        val startYearShort = (if (month >= 4) year else year - 1) % 100
        val endYearShort = (startYearShort + 1) % 100
        return String.format(Locale.ENGLISH, "%02d-%02d", startYearShort, endYearShort)
    }

    fun getTodayDate(): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH).format(Date())
    }

    data class DateValidationResult(
        val isValid: Boolean,
        val normalizedDate: String,
        val errorMessage: String? = null
    )

    fun parseValidLocalDate(rawDate: String): java.time.LocalDate? {
        val trimmed = rawDate.trim()
        if (trimmed.isEmpty()) return null
        val parts = trimmed.split('-', '/', '.')
        if (parts.size != 3) return null
        return try {
            val (y, m, d) = when {
                parts[0].length == 4 -> Triple(parts[0].toInt(), parts[1].toInt(), parts[2].toInt())
                parts[2].length == 4 -> Triple(parts[2].toInt(), parts[1].toInt(), parts[0].toInt())
                else -> return null
            }
            if (y !in 1900..2100) return null
            java.time.LocalDate.of(y, m, d)
        } catch (_: Exception) {
            null
        }
    }

    fun normalizeDate(rawDate: String): String {
        val trimmed = rawDate.trim()
        if (trimmed.isEmpty()) return getTodayDate()
        val parsed = parseValidLocalDate(trimmed) ?: return ""
        return String.format(Locale.ENGLISH, "%04d-%02d-%02d", parsed.year, parsed.monthValue, parsed.dayOfMonth)
    }

    fun isFutureDate(dateStr: String): Boolean {
        val norm = normalizeDate(dateStr)
        if (norm.isEmpty()) return false
        val today = getTodayDate()
        return norm > today
    }

    fun isDateInCurrentFinancialYear(dateStr: String): Boolean {
        val norm = normalizeDate(dateStr)
        if (norm.isEmpty()) return false
        val cal = java.util.Calendar.getInstance()
        val year = cal.get(java.util.Calendar.YEAR)
        val month = cal.get(java.util.Calendar.MONTH) + 1
        val fyStartYear = if (month >= 4) year else year - 1
        val fyStart = String.format(Locale.ENGLISH, "%04d-04-01", fyStartYear)
        val fyEnd = String.format(Locale.ENGLISH, "%04d-03-31", fyStartYear + 1)
        return norm in fyStart..fyEnd
    }

    fun getFinancialYearForDate(dateStr: String): String {
        val norm = normalizeDate(dateStr)
        val parts = norm.split('-')
        val cal = java.util.Calendar.getInstance()
        val currentYear = cal.get(java.util.Calendar.YEAR)
        if (parts.size >= 2) {
            val year = parts[0].toIntOrNull() ?: currentYear
            val month = parts[1].toIntOrNull() ?: 1
            val startYear = if (month >= 4) year else year - 1
            val endYearShort = ((startYear + 1) % 100).toString().padStart(2, '0')
            return "FY $startYear-$endYearShort"
        }
        val month = cal.get(java.util.Calendar.MONTH) + 1
        val startYear = if (month >= 4) currentYear else currentYear - 1
        val endYearShort = ((startYear + 1) % 100).toString().padStart(2, '0')
        return "FY $startYear-$endYearShort"
    }

    fun getCurrentFinancialYear(): String {
        return getFinancialYearForDate(getTodayDate())
    }

    fun getAvailableFinancialYears(transactions: List<Transaction>): List<String> {
        val fySet = mutableSetOf<String>()
        fySet.add(getCurrentFinancialYear())
        transactions.forEach { t ->
            if (t.date.isNotBlank()) {
                fySet.add(getFinancialYearForDate(t.date))
            }
        }
        val sorted = fySet.toList().sortedDescending()
        return sorted + listOf("All Time")
    }

    fun isDateInFinancialYear(dateStr: String, fyLabel: String): Boolean {
        if (fyLabel == "All Time" || fyLabel.isBlank()) return true
        val norm = normalizeDate(dateStr)
        if (norm.isEmpty()) return false
        val regex = Regex("""FY\s*(\d{4})""", RegexOption.IGNORE_CASE)
        val match = regex.find(fyLabel)
        if (match != null) {
            val startYear = match.groupValues[1].toIntOrNull() ?: return true
            val start = String.format(Locale.ENGLISH, "%04d-04-01", startYear)
            val end = String.format(Locale.ENGLISH, "%04d-03-31", startYear + 1)
            return norm in start..end
        }
        return true
    }

    fun getFinancialYearEndDate(fyLabel: String): String {
        if (fyLabel == "All Time" || fyLabel.isBlank()) return getTodayDate()
        val regex = Regex("""FY\s*(\d{4})""", RegexOption.IGNORE_CASE)
        val match = regex.find(fyLabel)
        if (match != null) {
            val today = java.time.LocalDate.now()
            val defaultFyStart = if (today.monthValue >= 4) today.year else today.year - 1
            val startYear = match.groupValues[1].toIntOrNull() ?: defaultFyStart
            return String.format(Locale.ENGLISH, "%04d-03-31", startYear + 1)
        }
        return getTodayDate()
    }

    data class FyFinancialStatement(
        val fyLabel: String,
        // Step 1: Net Sales
        val grossSalesPaise: Long,
        val salesReturnsPaise: Long = 0L,
        val salesDiscountsPaise: Long = 0L,
        val netSalesPaise: Long = grossSalesPaise,

        // Step 2: COGS & Gross Profit
        val purchasesCogsPaise: Long,
        val directExpensesPaise: Long = 0L,
        val totalCogsPaise: Long = purchasesCogsPaise + directExpensesPaise,
        val grossProfitPaise: Long = netSalesPaise - totalCogsPaise,
        val grossProfitMarginPercent: Double = 0.0,

        // Step 3: Operating Expenses & Operating Profit (Operating Income / EBIT)
        val operatingExpensesAdminPaise: Long = 0L,
        val operatingExpensesSalariesPaise: Long = 0L,
        val operatingExpensesMarketingPaise: Long = 0L,
        val operatingExpensesDepreciationPaise: Long = 0L,
        val indirectExpensesPaise: Long = operatingExpensesAdminPaise + operatingExpensesSalariesPaise + operatingExpensesMarketingPaise + operatingExpensesDepreciationPaise,
        val totalOperatingExpensesPaise: Long = indirectExpensesPaise,
        val operatingProfitPaise: Long = grossProfitPaise - totalOperatingExpensesPaise,
        val operatingProfitMarginPercent: Double = 0.0,

        // Step 4: Non-Operating Items & EBT
        val nonOperatingIncomePaise: Long = 0L,
        val nonOperatingInterestIncomePaise: Long = 0L,
        val nonOperatingExpensesPaise: Long = 0L,
        val financeCostsInterestPaise: Long = 0L,
        val earningsBeforeTaxEbtPaise: Long = operatingProfitPaise + nonOperatingIncomePaise - nonOperatingExpensesPaise,

        // Step 5: Income Tax & Net Profit
        val incomeTaxProvisionPaise: Long = 0L,
        val netProfitPaise: Long = earningsBeforeTaxEbtPaise - incomeTaxProvisionPaise,
        val netProfitMarginPercent: Double = 0.0,

        // Drawings (Balance Sheet Equity deduction)
        val drawingsPaise: Long = 0L,

        // Balance Sheet Statement fields
        val cashInHandPaise: Long = 0L,
        val bankTotalPaise: Long = 0L,
        val receivablesPaise: Long = 0L,
        val payablesPaise: Long = 0L,
        val stockValuationPaise: Long = 0L,
        val totalAssetsPaise: Long = 0L,
        val openingCapitalPaise: Long = 0L,
        val closingCapitalPaise: Long = 0L,
        val netGstPayablePaise: Long = 0L,
        val totalLiabilitiesAndEquityPaise: Long = 0L,
        val transactionCount: Int = 0,

        // Backward compatibility accessor
        val salesRevenuePaise: Long = grossSalesPaise
    )

    data class GstSlabBreakdownRow(
        val rateLabel: String,
        val taxablePaise: Long,
        val cgstPaise: Long,
        val sgstPaise: Long,
        val igstPaise: Long,
        val totalTaxPaise: Long
    )

    data class FyGstSummary(
        val fyLabel: String,
        val totalTaxableSalesPaise: Long,
        val b2bTaxableSalesPaise: Long,
        val b2cTaxableSalesPaise: Long,
        val outputCgstPaise: Long,
        val outputSgstPaise: Long,
        val outputIgstPaise: Long,
        val totalOutputTaxPaise: Long,
        val totalTaxablePurchasesPaise: Long,
        val eligibleItcCgstPaise: Long,
        val eligibleItcSgstPaise: Long,
        val eligibleItcIgstPaise: Long,
        val totalEligibleItcPaise: Long,
        val netGstPayablePaise: Long,
        val slabBreakdown: List<GstSlabBreakdownRow>,
        val b2bInvoiceCount: Int,
        val b2cInvoiceCount: Int
    )

    fun calculateFyFinancials(
        uiState: PabaruUiState,
        fyLabel: String,
        taxProvisionPaise: Long = 0L
    ): FyFinancialStatement {
        val fyTxns = uiState.transactions.filter { !it.isVoided && isDateInFinancialYear(it.date, fyLabel) }
        val isAllTime = (fyLabel == "All Time")
        val fyEndDate = getFinancialYearEndDate(fyLabel)

        var grossSales = 0L
        var salesReturns = 0L
        var salesDiscounts = 0L

        var purchases = 0L
        var directExp = 0L

        var opExAdmin = 0L
        var opExSalaries = 0L
        var opExMarketing = 0L
        var opExDepreciation = 0L
        var opExGeneral = 0L

        var nonOpInterestIncome = 0L
        var nonOpOtherIncome = 0L
        var financeLoanInterest = 0L
        var financeBankCharges = 0L

        var drawings = 0L

        val adRegex = Regex("""\b(ad|advertisement|ads|marketing|promo|campaign|branding|delivery)\b""", RegexOption.IGNORE_CASE)
        val emiRegex = Regex("""\b(emi|loan|interest|finance\s*charge)\b""", RegexOption.IGNORE_CASE)
        val salaryRegex = Regex("""\b(salary|payroll|wages|staff|bonus)\b""", RegexOption.IGNORE_CASE)
        val bankFeeRegex = Regex("""\b(bank\s*charge|bank\s*fee|finance\s*fee)\b""", RegexOption.IGNORE_CASE)
        val deprRegex = Regex("""\b(depreciation|amortization|amort)\b""", RegexOption.IGNORE_CASE)
        val adminRegex = Regex("""\b(rent|utility|office|stationery|repair|maintenance)\b""", RegexOption.IGNORE_CASE)

        fyTxns.forEach { t ->
            val rem = t.remarks
            // P&L Tax-Exclusive Base: use taxableAmountPaise when itemized, or net base
            val exclusiveAmount = if (t.items.isNotEmpty()) {
                t.items.sumOf { it.taxableAmountPaise }
            } else {
                t.amountPaise
            }

            when (t.type) {
                TransactionType.Sale, TransactionType.NonGstBill -> {
                    grossSales += exclusiveAmount
                }
                TransactionType.CreditNote -> {
                    salesReturns += exclusiveAmount
                }
                TransactionType.Purchase -> {
                    purchases += exclusiveAmount
                }
                TransactionType.DebitNote -> {
                    purchases -= exclusiveAmount
                }
                TransactionType.Expense -> {
                    if (t.expenseCategory == ExpenseCategory.Direct) {
                        directExp += exclusiveAmount
                    } else {
                        when {
                            emiRegex.containsMatchIn(rem) -> {
                                financeLoanInterest += exclusiveAmount
                            }
                            bankFeeRegex.containsMatchIn(rem) -> {
                                financeBankCharges += exclusiveAmount
                            }
                            salaryRegex.containsMatchIn(rem) -> {
                                opExSalaries += exclusiveAmount
                            }
                            adRegex.containsMatchIn(rem) -> {
                                opExMarketing += exclusiveAmount
                            }
                            deprRegex.containsMatchIn(rem) -> {
                                opExDepreciation += exclusiveAmount
                            }
                            adminRegex.containsMatchIn(rem) -> {
                                opExAdmin += exclusiveAmount
                            }
                            else -> {
                                opExGeneral += exclusiveAmount
                            }
                        }
                    }
                }
                TransactionType.PaymentIn -> {
                    if (rem.contains("interest", ignoreCase = true) || rem.contains("dividend", ignoreCase = true) || rem.contains("fd yield", ignoreCase = true)) {
                        nonOpInterestIncome += t.amountPaise
                    } else if (rem.contains("other income", ignoreCase = true) || rem.contains("scrap", ignoreCase = true) || rem.contains("commission", ignoreCase = true)) {
                        nonOpOtherIncome += t.amountPaise
                    }
                }
                TransactionType.Drawing -> {
                    drawings += t.amountPaise
                }
                else -> {}
            }
        }

        // 1. Net Sales = Gross Sales - Sales Returns - Discounts
        val netSales = grossSales - salesReturns - salesDiscounts

        // 2. Cost of Goods Sold & Gross Profit
        val totalCogs = purchases + directExp
        val grossProfit = netSales - totalCogs
        val gpMargin = if (netSales > 0L) (grossProfit.toDouble() / netSales.toDouble()) * 100.0 else 0.0

        // 3. Operating Expenses & Operating Profit (Operating Income / EBIT)
        val adminTotal = opExAdmin + opExGeneral
        val totalOperatingExp = adminTotal + opExSalaries + opExMarketing + opExDepreciation
        val operatingProfit = grossProfit - totalOperatingExp
        val opMargin = if (netSales > 0L) (operatingProfit.toDouble() / netSales.toDouble()) * 100.0 else 0.0

        // 4. Non-Operating Items & EBT
        val totalNonOpIncome = nonOpInterestIncome + nonOpOtherIncome
        val totalFinanceCosts = financeLoanInterest + financeBankCharges
        val ebt = operatingProfit + totalNonOpIncome - totalFinanceCosts

        // 5. Income Tax & Net Profit
        val tax = maxOf(0L, taxProvisionPaise)
        val netProfit = ebt - tax
        val npMargin = if (netSales > 0L) (netProfit.toDouble() / netSales.toDouble()) * 100.0 else 0.0

        // Cumulative transactions up to the end of selected FY (or all time)
        val cumulativeTxns = uiState.transactions.filter { t ->
            if (t.isVoided) return@filter false
            if (isAllTime) true else {
                val n = normalizeDate(t.date)
                n.isNotEmpty() && n <= fyEndDate
            }
        }

        var cashInHand = uiState.profile.openingCashBalancePaise
        cumulativeTxns.filter { it.paymentMode == PaymentMode.Cash }.forEach { t ->
            when (t.type) {
                TransactionType.PaymentIn, TransactionType.Sale, TransactionType.NonGstBill, TransactionType.DebitNote -> cashInHand += t.amountPaise
                TransactionType.PaymentOut, TransactionType.Purchase, TransactionType.Expense, TransactionType.CreditNote, TransactionType.Drawing -> cashInHand -= t.amountPaise
                TransactionType.Quotation -> {} // Quotations are non-financial estimates; do not hit cash
            }
        }

        var bankTotal = uiState.bankAccounts.sumOf { it.openingBalancePaise }
        cumulativeTxns.filter {
            it.paymentMode == PaymentMode.Bank || it.paymentMode == PaymentMode.UPI || it.paymentMode == PaymentMode.Cheque
        }.forEach { t ->
            when (t.type) {
                TransactionType.PaymentIn, TransactionType.Sale, TransactionType.NonGstBill, TransactionType.DebitNote -> bankTotal += t.amountPaise
                TransactionType.PaymentOut, TransactionType.Purchase, TransactionType.Expense, TransactionType.CreditNote, TransactionType.Drawing -> bankTotal -= t.amountPaise
                TransactionType.Quotation -> {} // Quotations do not hit bank
            }
        }

        var receivables = uiState.parties.filter { it.openingBalanceType == BalanceType.Receivable }.sumOf { it.openingBalancePaise }
        var payables = uiState.parties.filter { it.openingBalanceType == BalanceType.Payable }.sumOf { it.openingBalancePaise }

        cumulativeTxns.forEach { t ->
            if (t.partyId != null) {
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
        }

        val stockValuation = uiState.products.filter { !it.isArchived }.sumOf {
            (it.currentStockMilli * it.purchasePricePaise + 500L) / 1000L
        }

        var cumulativeNet = 0L
        var cumulativeDrawings = 0L
        var netGstCashComponent = 0L
        cumulativeTxns.forEach { t ->
            val rem = t.remarks
            val excl = if (t.items.isNotEmpty()) t.items.sumOf { it.taxableAmountPaise } else t.amountPaise
            val taxComponent = if (t.items.isNotEmpty()) (t.amountPaise - excl) else 0L
            when (t.type) {
                TransactionType.Sale, TransactionType.NonGstBill -> {
                    cumulativeNet += excl
                    netGstCashComponent += taxComponent
                }
                TransactionType.CreditNote -> {
                    cumulativeNet -= excl
                    netGstCashComponent -= taxComponent
                }
                TransactionType.Purchase -> {
                    cumulativeNet -= excl
                    netGstCashComponent -= taxComponent
                }
                TransactionType.DebitNote -> {
                    cumulativeNet += excl
                    netGstCashComponent += taxComponent
                }
                TransactionType.Expense -> {
                    cumulativeNet -= excl
                    netGstCashComponent -= taxComponent
                }
                TransactionType.PaymentIn -> {
                    if (rem.contains("interest", ignoreCase = true) ||
                        rem.contains("dividend", ignoreCase = true) ||
                        rem.contains("fd yield", ignoreCase = true) ||
                        rem.contains("other income", ignoreCase = true) ||
                        rem.contains("scrap", ignoreCase = true) ||
                        rem.contains("commission", ignoreCase = true)
                    ) {
                        cumulativeNet += t.amountPaise
                    }
                }
                TransactionType.Drawing -> cumulativeDrawings += t.amountPaise
                else -> {}
            }
        }

        // If netGstCashComponent < 0, excess ITC is a Current Asset; if >= 0, Net GST Payable is a Current Liability.
        val gstInputCreditAsset = if (netGstCashComponent < 0L) -netGstCashComponent else 0L
        val gstPayableLiability = if (netGstCashComponent > 0L) netGstCashComponent else 0L

        val totalAssets = cashInHand + bankTotal + receivables + stockValuation + gstInputCreditAsset

        // Closing stock asset added to closing equity offset against expensed purchases so inventory isn't double-counted
        val closingCapital = uiState.profile.openingCapitalPaise + cumulativeNet + stockValuation - cumulativeDrawings
        val totalLiabilities = payables + gstPayableLiability + closingCapital

        return FyFinancialStatement(
            fyLabel = fyLabel,
            grossSalesPaise = grossSales,
            salesReturnsPaise = salesReturns,
            salesDiscountsPaise = salesDiscounts,
            netSalesPaise = netSales,
            purchasesCogsPaise = purchases,
            directExpensesPaise = directExp,
            totalCogsPaise = totalCogs,
            grossProfitPaise = grossProfit,
            grossProfitMarginPercent = gpMargin,
            operatingExpensesAdminPaise = adminTotal,
            operatingExpensesSalariesPaise = opExSalaries,
            operatingExpensesMarketingPaise = opExMarketing,
            operatingExpensesDepreciationPaise = opExDepreciation,
            indirectExpensesPaise = totalOperatingExp,
            totalOperatingExpensesPaise = totalOperatingExp,
            operatingProfitPaise = operatingProfit,
            operatingProfitMarginPercent = opMargin,
            nonOperatingIncomePaise = totalNonOpIncome,
            nonOperatingInterestIncomePaise = nonOpInterestIncome,
            nonOperatingExpensesPaise = totalFinanceCosts,
            financeCostsInterestPaise = financeLoanInterest,
            earningsBeforeTaxEbtPaise = ebt,
            incomeTaxProvisionPaise = tax,
            netProfitPaise = netProfit,
            netProfitMarginPercent = npMargin,
            drawingsPaise = drawings,
            cashInHandPaise = cashInHand,
            bankTotalPaise = bankTotal,
            receivablesPaise = receivables,
            payablesPaise = payables,
            stockValuationPaise = stockValuation,
            totalAssetsPaise = totalAssets,
            openingCapitalPaise = uiState.profile.openingCapitalPaise,
            closingCapitalPaise = closingCapital,
            netGstPayablePaise = netGstCashComponent,
            totalLiabilitiesAndEquityPaise = totalLiabilities,
            transactionCount = fyTxns.size,
            salesRevenuePaise = netSales
        )
    }

    fun calculateFyGstSummary(
        transactions: List<Transaction>,
        parties: List<Party>,
        fyLabel: String
    ): FyGstSummary {
        val fyTxns = transactions.filter { !it.isVoided && isDateInFinancialYear(it.date, fyLabel) }
        val partyMap = parties.associateBy { it.id }

        var totalTaxableSales = 0L
        var b2bTaxable = 0L
        var b2cTaxable = 0L
        var outputCgst = 0L
        var outputSgst = 0L
        var outputIgst = 0L
        var b2bCount = 0
        var b2cCount = 0

        val rateTaxableMap = mutableMapOf<Int, Long>()
        val rateCgstMap = mutableMapOf<Int, Long>()
        val rateSgstMap = mutableMapOf<Int, Long>()
        val rateIgstMap = mutableMapOf<Int, Long>()

        val salesTxns = fyTxns.filter { it.type == TransactionType.Sale || it.type == TransactionType.CreditNote }
        salesTxns.forEach { t ->
            val party = t.partyId?.let { partyMap[it] }
            val isB2b = party != null && isValidGstinFormat(party.gstin)
            val isCreditNote = t.type == TransactionType.CreditNote
            val multiplier = if (isCreditNote) -1L else 1L

            if (!isCreditNote) {
                if (isB2b) b2bCount++ else b2cCount++
            }

            if (t.items.isNotEmpty()) {
                var invoiceTaxable = 0L
                t.items.forEach { item ->
                    val lineTaxable = item.taxableAmountPaise * multiplier
                    val lineCgst = item.cgstPaise * multiplier
                    val lineSgst = item.sgstPaise * multiplier
                    val lineIgst = item.igstPaise * multiplier

                    invoiceTaxable += lineTaxable
                    outputCgst += lineCgst
                    outputSgst += lineSgst
                    outputIgst += lineIgst

                    val rateBps = item.gstRateBps
                    rateTaxableMap[rateBps] = (rateTaxableMap[rateBps] ?: 0L) + lineTaxable
                    rateCgstMap[rateBps] = (rateCgstMap[rateBps] ?: 0L) + lineCgst
                    rateSgstMap[rateBps] = (rateSgstMap[rateBps] ?: 0L) + lineSgst
                    rateIgstMap[rateBps] = (rateIgstMap[rateBps] ?: 0L) + lineIgst
                }
                totalTaxableSales += invoiceTaxable
                if (isB2b) b2bTaxable += invoiceTaxable else b2cTaxable += invoiceTaxable
            } else {
                val netAmt = t.amountPaise * multiplier
                totalTaxableSales += netAmt
                if (isB2b) b2bTaxable += netAmt else b2cTaxable += netAmt
                rateTaxableMap[0] = (rateTaxableMap[0] ?: 0L) + netAmt
            }
        }

        var totalTaxablePurchases = 0L
        var itcCgst = 0L
        var itcSgst = 0L
        var itcIgst = 0L

        val purchaseTxns = fyTxns.filter { it.type == TransactionType.Purchase || it.type == TransactionType.DebitNote }
        purchaseTxns.forEach { t ->
            val isDebitNote = t.type == TransactionType.DebitNote
            val multiplier = if (isDebitNote) -1L else 1L
            if (t.items.isNotEmpty()) {
                t.items.forEach { item ->
                    totalTaxablePurchases += item.taxableAmountPaise * multiplier
                    itcCgst += item.cgstPaise * multiplier
                    itcSgst += item.sgstPaise * multiplier
                    itcIgst += item.igstPaise * multiplier
                }
            } else {
                totalTaxablePurchases += t.amountPaise * multiplier
            }
        }

        val totalOutputTax = outputCgst + outputSgst + outputIgst
        val totalEligibleItc = itcCgst + itcSgst + itcIgst
        // Net Payable: preserve negative value to represent ITC carry-forward (positive = cash tax payable)
        val netPayable = totalOutputTax - totalEligibleItc

        val allRateBps = (rateTaxableMap.keys + listOf(0, 25, 300, 500, 1200, 1800, 2800, 4000)).distinct().sorted()
        val slabBreakdown = allRateBps.map { bps ->
            val taxable = rateTaxableMap[bps] ?: 0L
            val cg = rateCgstMap[bps] ?: 0L
            val sg = rateSgstMap[bps] ?: 0L
            val ig = rateIgstMap[bps] ?: 0L
            GstSlabBreakdownRow(
                rateLabel = GstSlabs.label(bps),
                taxablePaise = taxable,
                cgstPaise = cg,
                sgstPaise = sg,
                igstPaise = ig,
                totalTaxPaise = cg + sg + ig
            )
        }.filter { it.taxablePaise != 0L || it.totalTaxPaise != 0L }

        return FyGstSummary(
            fyLabel = fyLabel,
            totalTaxableSalesPaise = totalTaxableSales,
            b2bTaxableSalesPaise = b2bTaxable,
            b2cTaxableSalesPaise = b2cTaxable,
            outputCgstPaise = outputCgst,
            outputSgstPaise = outputSgst,
            outputIgstPaise = outputIgst,
            totalOutputTaxPaise = totalOutputTax,
            totalTaxablePurchasesPaise = totalTaxablePurchases,
            eligibleItcCgstPaise = itcCgst,
            eligibleItcSgstPaise = itcSgst,
            eligibleItcIgstPaise = itcIgst,
            totalEligibleItcPaise = totalEligibleItc,
            netGstPayablePaise = netPayable,
            slabBreakdown = slabBreakdown.ifEmpty {
                listOf(GstSlabBreakdownRow("0%", totalTaxableSales, 0L, 0L, 0L, 0L))
            },
            b2bInvoiceCount = b2bCount,
            b2cInvoiceCount = b2cCount
        )
    }

    data class Gstr9QuarterSummary(
        val quarterKey: String,
        val quarterLabel: String,
        val dateRangeDesc: String,
        val taxableSalesPaise: Long,
        val b2bTaxablePaise: Long,
        val b2cTaxablePaise: Long,
        val outputCgstPaise: Long,
        val outputSgstPaise: Long,
        val outputIgstPaise: Long,
        val totalOutputTaxPaise: Long,
        val taxablePurchasesPaise: Long,
        val itcCgstPaise: Long,
        val itcSgstPaise: Long,
        val itcIgstPaise: Long,
        val totalItcPaise: Long,
        val netTaxPayablePaise: Long,
        val invoiceCount: Int
    )

    data class Gstr9Table4Data(
        val b2cTaxablePaise: Long,
        val b2cTaxPaise: Long,
        val b2bTaxablePaise: Long,
        val b2bTaxPaise: Long,
        val totalTaxablePaise: Long,
        val totalCgstPaise: Long,
        val totalSgstPaise: Long,
        val totalIgstPaise: Long,
        val grandTotalTaxPaise: Long,
        val b2cCgstPaise: Long = 0L,
        val b2cSgstPaise: Long = 0L,
        val b2cIgstPaise: Long = 0L,
        val b2bCgstPaise: Long = 0L,
        val b2bSgstPaise: Long = 0L,
        val b2bIgstPaise: Long = 0L
    )

    data class Gstr9Table6Data(
        val inwardSuppliesTaxablePaise: Long,
        val itcCgstPaise: Long,
        val itcSgstPaise: Long,
        val itcIgstPaise: Long,
        val totalItcPaise: Long
    )

    data class Gstr9Table9Data(
        val taxPayablePaise: Long,
        val taxPaidItcPaise: Long,
        val taxPaidCashPaise: Long,
        val balancePayablePaise: Long
    )

    data class Gstr9AnnualSummary(
        val fyLabel: String,
        val quarters: List<Gstr9QuarterSummary>,
        val annualTaxableSalesPaise: Long,
        val annualB2bTaxablePaise: Long,
        val annualB2cTaxablePaise: Long,
        val annualOutputCgstPaise: Long,
        val annualOutputSgstPaise: Long,
        val annualOutputIgstPaise: Long,
        val annualTotalOutputTaxPaise: Long,
        val annualTaxablePurchasesPaise: Long,
        val annualItcCgstPaise: Long,
        val annualItcSgstPaise: Long,
        val annualItcIgstPaise: Long,
        val annualTotalItcPaise: Long,
        val annualNetTaxPayablePaise: Long,
        val totalInvoicesCount: Int,
        val table4Details: Gstr9Table4Data,
        val table6Details: Gstr9Table6Data,
        val table9Details: Gstr9Table9Data
    )

    fun calculateGstr9AnnualSummary(
        transactions: List<Transaction>,
        parties: List<Party>,
        fyLabel: String
    ): Gstr9AnnualSummary {
        val cleanFy = if (fyLabel == "All Time" || fyLabel.isBlank()) getCurrentFinancialYear() else fyLabel
        val regex = Regex("""FY\s*(\d{4})""", RegexOption.IGNORE_CASE)
        val match = regex.find(cleanFy)
        val startYear = match?.groupValues?.get(1)?.toIntOrNull() ?: 2025
        val nextYear = startYear + 1

        val q1Range = Pair(String.format(Locale.ENGLISH, "%04d-04-01", startYear), String.format(Locale.ENGLISH, "%04d-06-30", startYear))
        val q2Range = Pair(String.format(Locale.ENGLISH, "%04d-07-01", startYear), String.format(Locale.ENGLISH, "%04d-09-30", startYear))
        val q3Range = Pair(String.format(Locale.ENGLISH, "%04d-10-01", startYear), String.format(Locale.ENGLISH, "%04d-12-31", startYear))
        val q4Range = Pair(String.format(Locale.ENGLISH, "%04d-01-01", nextYear), String.format(Locale.ENGLISH, "%04d-03-31", nextYear))

        val quarterDefs = listOf(
            Triple("Q1", "Q1 (Apr - Jun)", q1Range),
            Triple("Q2", "Q2 (Jul - Sep)", q2Range),
            Triple("Q3", "Q3 (Oct - Dec)", q3Range),
            Triple("Q4", "Q4 (Jan - Mar)", q4Range)
        )

        val partyMap = parties.associateBy { it.id }

        var totalB2bCgst = 0L
        var totalB2bSgst = 0L
        var totalB2bIgst = 0L
        var totalB2cCgst = 0L
        var totalB2cSgst = 0L
        var totalB2cIgst = 0L

        val quarterSummaries = quarterDefs.map { (key, label, dateRange) ->
            val qTxns = transactions.filter { t ->
                if (t.isVoided) return@filter false
                val norm = normalizeDate(t.date)
                norm.isNotEmpty() && norm in dateRange.first..dateRange.second
            }

            var qTaxableSales = 0L
            var qB2bTaxable = 0L
            var qB2cTaxable = 0L
            var qOutputCgst = 0L
            var qOutputSgst = 0L
            var qOutputIgst = 0L
            var qInvCount = 0

            val salesTxns = qTxns.filter { it.type == TransactionType.Sale || it.type == TransactionType.CreditNote }
            salesTxns.forEach { t ->
                val isCreditNote = t.type == TransactionType.CreditNote
                val multiplier = if (isCreditNote) -1L else 1L
                if (!isCreditNote) qInvCount++

                val party = t.partyId?.let { partyMap[it] }
                val isB2b = party != null && isValidGstinFormat(party.gstin)

                if (t.items.isNotEmpty()) {
                    var invoiceTaxable = 0L
                    var invCgst = 0L
                    var invSgst = 0L
                    var invIgst = 0L
                    t.items.forEach { item ->
                        invoiceTaxable += item.taxableAmountPaise * multiplier
                        invCgst += item.cgstPaise * multiplier
                        invSgst += item.sgstPaise * multiplier
                        invIgst += item.igstPaise * multiplier
                    }
                    qOutputCgst += invCgst
                    qOutputSgst += invSgst
                    qOutputIgst += invIgst
                    qTaxableSales += invoiceTaxable
                    if (isB2b) {
                        qB2bTaxable += invoiceTaxable
                        totalB2bCgst += invCgst
                        totalB2bSgst += invSgst
                        totalB2bIgst += invIgst
                    } else {
                        qB2cTaxable += invoiceTaxable
                        totalB2cCgst += invCgst
                        totalB2cSgst += invSgst
                        totalB2cIgst += invIgst
                    }
                } else {
                    val netAmt = t.amountPaise * multiplier
                    qTaxableSales += netAmt
                    if (isB2b) qB2bTaxable += netAmt else qB2cTaxable += netAmt
                }
            }

            var qTaxablePurchases = 0L
            var qItcCgst = 0L
            var qItcSgst = 0L
            var qItcIgst = 0L

            val purchaseTxns = qTxns.filter { it.type == TransactionType.Purchase || it.type == TransactionType.DebitNote }
            purchaseTxns.forEach { t ->
                val multiplier = if (t.type == TransactionType.DebitNote) -1L else 1L
                if (t.items.isNotEmpty()) {
                    t.items.forEach { item ->
                        qTaxablePurchases += item.taxableAmountPaise * multiplier
                        qItcCgst += item.cgstPaise * multiplier
                        qItcSgst += item.sgstPaise * multiplier
                        qItcIgst += item.igstPaise * multiplier
                    }
                } else {
                    qTaxablePurchases += t.amountPaise * multiplier
                }
            }

            val qTotalOutput = qOutputCgst + qOutputSgst + qOutputIgst
            val qTotalItc = qItcCgst + qItcSgst + qItcIgst
            val qNetPayable = qTotalOutput - qTotalItc

            Gstr9QuarterSummary(
                quarterKey = key,
                quarterLabel = label,
                dateRangeDesc = "${dateRange.first.takeLast(5)} to ${dateRange.second.takeLast(5)}",
                taxableSalesPaise = qTaxableSales,
                b2bTaxablePaise = qB2bTaxable,
                b2cTaxablePaise = qB2cTaxable,
                outputCgstPaise = qOutputCgst,
                outputSgstPaise = qOutputSgst,
                outputIgstPaise = qOutputIgst,
                totalOutputTaxPaise = qTotalOutput,
                taxablePurchasesPaise = qTaxablePurchases,
                itcCgstPaise = qItcCgst,
                itcSgstPaise = qItcSgst,
                itcIgstPaise = qItcIgst,
                totalItcPaise = qTotalItc,
                netTaxPayablePaise = qNetPayable,
                invoiceCount = qInvCount
            )
        }

        val totalTaxableSales = quarterSummaries.sumOf { it.taxableSalesPaise }
        val totalB2bTaxable = quarterSummaries.sumOf { it.b2bTaxablePaise }
        val totalB2cTaxable = quarterSummaries.sumOf { it.b2cTaxablePaise }
        val totalOutputCgst = quarterSummaries.sumOf { it.outputCgstPaise }
        val totalOutputSgst = quarterSummaries.sumOf { it.outputSgstPaise }
        val totalOutputIgst = quarterSummaries.sumOf { it.outputIgstPaise }
        val totalOutputTax = quarterSummaries.sumOf { it.totalOutputTaxPaise }

        val totalTaxablePurchases = quarterSummaries.sumOf { it.taxablePurchasesPaise }
        val totalItcCgst = quarterSummaries.sumOf { it.itcCgstPaise }
        val totalItcSgst = quarterSummaries.sumOf { it.itcSgstPaise }
        val totalItcIgst = quarterSummaries.sumOf { it.itcIgstPaise }
        val totalItc = quarterSummaries.sumOf { it.totalItcPaise }
        val totalNetPayable = maxOf(0L, totalOutputTax - totalItc)
        val totalInvoices = quarterSummaries.sumOf { it.invoiceCount }

        val b2bTax = totalB2bCgst + totalB2bSgst + totalB2bIgst
        val b2cTax = totalB2cCgst + totalB2cSgst + totalB2cIgst

        val table4 = Gstr9Table4Data(
            b2cTaxablePaise = totalB2cTaxable,
            b2cTaxPaise = b2cTax,
            b2bTaxablePaise = totalB2bTaxable,
            b2bTaxPaise = b2bTax,
            totalTaxablePaise = totalTaxableSales,
            totalCgstPaise = totalOutputCgst,
            totalSgstPaise = totalOutputSgst,
            totalIgstPaise = totalOutputIgst,
            grandTotalTaxPaise = totalOutputTax,
            b2cCgstPaise = totalB2cCgst,
            b2cSgstPaise = totalB2cSgst,
            b2cIgstPaise = totalB2cIgst,
            b2bCgstPaise = totalB2bCgst,
            b2bSgstPaise = totalB2bSgst,
            b2bIgstPaise = totalB2bIgst
        )

        val table6 = Gstr9Table6Data(
            inwardSuppliesTaxablePaise = totalTaxablePurchases,
            itcCgstPaise = totalItcCgst,
            itcSgstPaise = totalItcSgst,
            itcIgstPaise = totalItcIgst,
            totalItcPaise = totalItc
        )

        val itcUtilized = minOf(totalOutputTax, totalItc)
        val cashPaid = maxOf(0L, totalOutputTax - itcUtilized)

        val table9 = Gstr9Table9Data(
            taxPayablePaise = totalOutputTax,
            taxPaidItcPaise = itcUtilized,
            taxPaidCashPaise = cashPaid,
            balancePayablePaise = totalNetPayable
        )

        return Gstr9AnnualSummary(
            fyLabel = cleanFy,
            quarters = quarterSummaries,
            annualTaxableSalesPaise = totalTaxableSales,
            annualB2bTaxablePaise = totalB2bTaxable,
            annualB2cTaxablePaise = totalB2cTaxable,
            annualOutputCgstPaise = totalOutputCgst,
            annualOutputSgstPaise = totalOutputSgst,
            annualOutputIgstPaise = totalOutputIgst,
            annualTotalOutputTaxPaise = totalOutputTax,
            annualTaxablePurchasesPaise = totalTaxablePurchases,
            annualItcCgstPaise = totalItcCgst,
            annualItcSgstPaise = totalItcSgst,
            annualItcIgstPaise = totalItcIgst,
            annualTotalItcPaise = totalItc,
            annualNetTaxPayablePaise = totalNetPayable,
            totalInvoicesCount = totalInvoices,
            table4Details = table4,
            table6Details = table6,
            table9Details = table9
        )
    }

    fun validateTransactionDate(dateStr: String): DateValidationResult {
        if (dateStr.isNotBlank() && parseValidLocalDate(dateStr) == null) {
            return DateValidationResult(
                isValid = false,
                normalizedDate = getTodayDate(),
                errorMessage = "Invalid calendar date '$dateStr'. Expected DD-MM-YYYY or YYYY-MM-DD."
            )
        }
        val norm = normalizeDate(dateStr)
        if (isFutureDate(norm)) {
            return DateValidationResult(
                isValid = false,
                normalizedDate = getTodayDate(),
                errorMessage = "Transaction date cannot be in the future (today is ${getTodayDate()})"
            )
        }
        return DateValidationResult(isValid = true, normalizedDate = norm)
    }

    // Number to words in Indian Rupees
    fun amountInWords(paise: Long): String {
        if (paise == 0L) return "Zero Rupees Only"
        if (paise < 0L) return "Minus " + amountInWords(-paise)
        val rupees = paise / 100
        val remainingPaise = paise % 100

        val ones = arrayOf("", "One", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight", "Nine", "Ten",
            "Eleven", "Twelve", "Thirteen", "Fourteen", "Fifteen", "Sixteen", "Seventeen", "Eighteen", "Nineteen")
        val tens = arrayOf("", "", "Twenty", "Thirty", "Forty", "Fifty", "Sixty", "Seventy", "Eighty", "Ninety")

        fun numToWords(n: Long): String {
            return when {
                n == 0L -> ""
                n < 20 -> ones[n.toInt()] + " "
                n < 100 -> tens[(n / 10).toInt()] + " " + ones[(n % 10).toInt()] + " "
                n < 1000 -> ones[(n / 100).toInt()] + " Hundred " + numToWords(n % 100)
                n < 100000 -> numToWords(n / 1000).trim() + " Thousand " + numToWords(n % 1000)
                n < 10000000 -> numToWords(n / 100000).trim() + " Lakh " + numToWords(n % 100000)
                else -> numToWords(n / 10000000).trim() + " Crore " + numToWords(n % 10000000)
            }
        }

        val rupeeUnit = if (rupees == 1L) "Rupee" else "Rupees"
        var words = if (rupees > 0) "$rupeeUnit " + numToWords(rupees).trim() else ""
        if (remainingPaise > 0) {
            val paisaUnit = if (remainingPaise == 1L) "Paisa" else "Paise"
            words = if (words.isNotEmpty()) {
                words + " and " + numToWords(remainingPaise).trim() + " $paisaUnit"
            } else {
                numToWords(remainingPaise).trim() + " $paisaUnit"
            }
        }
        val cleanWords = words.replace(Regex("\\s+"), " ").trim()
        return "$cleanWords Only"
    }

    // Statutory Indian GSTIN Validation Engine
    // Format: 2-digit State Code + 10-char PAN + 1 Entity Code + 'Z' + 1 Checksum digit (Total 15 characters)
    val GSTIN_REGEX = Regex("^[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z]{1}[1-9A-Z]{1}Z[0-9A-Z]{1}$")

    data class GstinValidationResult(
        val isValid: Boolean,
        val message: String,
        val stateCode: String? = null,
        val pan: String? = null
    )

    const val GSTIN_CHARS = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ"

    /**
     * Calculates the 15th statutory check-character of a 15-character GSTIN
     * using the official Modulo-36 (ISO/IEC 7064) algorithm.
     */
    fun gstinCheckChar(first14: String): Char {
        val s = first14.trim().uppercase()
        if (s.length < 14) return '0'
        var sum = 0
        for (i in 0 until 14) {
            val c = s[i]
            val v = GSTIN_CHARS.indexOf(c)
            if (v < 0) return '0'
            // Multiplier alternates: 1 for even index (0,2,4...), 2 for odd index (1,3,5...)
            val factor = if (i % 2 == 0) 1 else 2
            val p = v * factor
            val q = (p / 36) + (p % 36)
            sum += q
        }
        val remainder = sum % 36
        val checkVal = (36 - remainder) % 36
        return GSTIN_CHARS[checkVal]
    }

    fun today(): java.time.LocalDate = java.time.LocalDate.now()

    fun fyLabel(d: java.time.LocalDate = today()): String {
        val y = d.year
        return if (d.monthValue >= 4) {
            "$y-${(y + 1).toString().takeLast(2)}"
        } else {
            "${y - 1}-${y.toString().takeLast(2)}"
        }
    }

    fun validateGstin(gstin: String?, allowBlank: Boolean = true, partyPan: String? = null): GstinValidationResult {
        if (gstin.isNullOrBlank()) {
            return if (allowBlank) {
                GstinValidationResult(true, "Unregistered / B2C (No GSTIN provided)")
            } else {
                GstinValidationResult(false, "GSTIN cannot be blank for B2B registered invoice")
            }
        }

        val cleaned = gstin.trim().uppercase()
        if (cleaned.length != 15) {
            return GstinValidationResult(
                isValid = false,
                message = "GSTIN must be exactly 15 characters (currently ${cleaned.length})"
            )
        }

        if (!GSTIN_REGEX.matches(cleaned)) {
            return GstinValidationResult(
                isValid = false,
                message = "Invalid GSTIN format. Expected e.g. 27ABCDE1234F1Z5 (2-digit state + 10-char PAN + 1 entity + 'Z' + 1 checksum)"
            )
        }

        val stateCode = cleaned.substring(0, 2)
        val validState = (INDIAN_STATES.any { it.code == stateCode } || ((stateCode.toIntOrNull() ?: 0) in 1..38) || stateCode == "97") && stateCode != "99" && stateCode != "00"
        if (!validState) {
            return GstinValidationResult(
                isValid = false,
                message = "Invalid 2-digit state code '$stateCode' in GSTIN"
            )
        }

        val pan = cleaned.substring(2, 12)
        if (!partyPan.isNullOrBlank()) {
            val cleanPartyPan = partyPan.trim().uppercase()
            if (cleanPartyPan.isNotEmpty() && cleanPartyPan != pan) {
                return GstinValidationResult(
                    isValid = false,
                    message = "GSTIN PAN ($pan) does not match Party PAN ($cleanPartyPan)"
                )
            }
        }

        val expectedCheckChar = gstinCheckChar(cleaned.take(14))
        if (cleaned[14] != expectedCheckChar) {
            return GstinValidationResult(
                isValid = false,
                message = "Invalid GSTIN check-digit '${cleaned[14]}'. Expected statutory check digit is '$expectedCheckChar'"
            )
        }

        return GstinValidationResult(
            isValid = true,
            message = "Valid statutory GSTIN (State: $stateCode, PAN: $pan)",
            stateCode = stateCode,
            pan = pan
        )
    }

    /**
     * Structural GSTIN format + state check (accepts valid state codes 01..38, 96, 97 and 15-char PAN layout).
     */
    fun isValidGstinFormat(gstin: String?): Boolean {
        if (gstin.isNullOrBlank()) return false
        val cleaned = gstin.trim().uppercase()
        if (cleaned.length != 15 || !GSTIN_REGEX.matches(cleaned)) return false
        val stateCode = cleaned.substring(0, 2)
        return (INDIAN_STATES.any { it.code == stateCode } || ((stateCode.toIntOrNull() ?: 0) in 1..38) || stateCode == "96" || stateCode == "97") &&
            stateCode != "99" && stateCode != "00"
    }

    fun isValidGstin(gstin: String?, allowBlank: Boolean = true, partyPan: String? = null): Boolean {
        return validateGstin(gstin, allowBlank, partyPan).isValid
    }

    data class FieldValidationResult(
        val isValid: Boolean,
        val message: String
    )

    private val PAN_REGEX = Regex("^[A-Z]{5}[0-9]{4}[A-Z]$")
    private val IFSC_REGEX = Regex("^[A-Z]{4}0[A-Z0-9]{6}$")
    private val PHONE_REGEX = Regex("^[6-9][0-9]{9}$")

    fun validatePan(pan: String?, allowBlank: Boolean = true): FieldValidationResult {
        if (pan.isNullOrBlank()) {
            return if (allowBlank) FieldValidationResult(true, "Optional PAN")
            else FieldValidationResult(false, "PAN cannot be blank")
        }
        val cleaned = pan.trim().uppercase()
        if (cleaned.length != 10) {
            return FieldValidationResult(false, "PAN must be exactly 10 characters (currently ${cleaned.length})")
        }
        if (!PAN_REGEX.matches(cleaned)) {
            return FieldValidationResult(false, "Invalid PAN format. Expected 5 letters + 4 digits + 1 letter (e.g. ABCDE1234F)")
        }
        val entityChar = cleaned[3]
        val entityDesc = when (entityChar) {
            'P' -> "Individual"
            'C' -> "Company"
            'H' -> "HUF"
            'F' -> "Firm / LLP"
            'A' -> "AOP"
            'T' -> "Trust"
            'B' -> "BOI"
            'G' -> "Government"
            'J' -> "Artificial Juridical Person"
            'L' -> "Local Authority"
            else -> "Entity"
        }
        return FieldValidationResult(true, "Valid PAN ($entityDesc)")
    }

    fun validateIfsc(ifsc: String?, allowBlank: Boolean = true): FieldValidationResult {
        if (ifsc.isNullOrBlank()) {
            return if (allowBlank) FieldValidationResult(true, "Optional IFSC")
            else FieldValidationResult(false, "IFSC cannot be blank")
        }
        val cleaned = ifsc.trim().uppercase()
        if (cleaned.length != 11) {
            return FieldValidationResult(false, "IFSC must be exactly 11 characters (currently ${cleaned.length})")
        }
        if (cleaned[4] != '0') {
            return FieldValidationResult(false, "Invalid IFSC: 5th character must be '0' (currently '${cleaned[4]}')")
        }
        if (!IFSC_REGEX.matches(cleaned)) {
            return FieldValidationResult(false, "Invalid IFSC format. Expected 4 bank letters + 0 + 6 branch alphanumeric (e.g. HDFC0000060)")
        }
        return FieldValidationResult(true, "Valid IFSC code")
    }

    fun validatePhone(phone: String?, allowBlank: Boolean = true): FieldValidationResult {
        if (phone.isNullOrBlank()) {
            return if (allowBlank) FieldValidationResult(true, "Optional Phone")
            else FieldValidationResult(false, "Mobile number cannot be blank")
        }
        val digits = phone.filter { it.isDigit() }
        if (digits.length != 10) {
            return FieldValidationResult(false, "Mobile number must be exactly 10 digits (currently ${digits.length})")
        }
        if (!PHONE_REGEX.matches(digits)) {
            return FieldValidationResult(false, "Invalid Indian mobile number. Must start with 6, 7, 8, or 9")
        }
        return FieldValidationResult(true, "Valid 10-digit mobile number")
    }
}
