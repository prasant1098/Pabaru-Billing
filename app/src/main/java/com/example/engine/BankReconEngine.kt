package com.example.engine

import com.example.model.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

object BankReconEngine {

    enum class DiscrepancyResolutionType {
        DIRECT_EXPENSE,
        BANK_CHARGES,
        CUSTOMER_PAYMENT,
        SUPPLIER_PAYMENT,
        INTEREST_INCOME,
        TRANSFER
    }

    data class StatementParseResult(
        val items: List<BankReconItem>,
        val totalCreditsPaise: Long,
        val totalDebitsPaise: Long,
        val detectedBank: String,
        val format: String,
        val parseErrors: List<String> = emptyList()
    )

    // -------------------------------------------------------------
    // 1. Multi-Format Bank Statement Parsers
    // -------------------------------------------------------------

    /**
     * Parses raw statement text (CSV, TSV, or OFX) into BankReconItem entries.
     */
    fun parseStatementText(
        rawContent: String,
        targetBankAccountId: String,
        targetBankName: String = "Bank Account"
    ): StatementParseResult {
        val trimmed = rawContent.trim()
        return if (trimmed.contains("<OFX>", ignoreCase = true) || trimmed.contains("<STMTTRN>", ignoreCase = true)) {
            parseOfxStatement(trimmed, targetBankAccountId, targetBankName)
        } else {
            parseCsvStatement(trimmed, targetBankAccountId, targetBankName)
        }
    }

    /**
     * Parses CSV / TSV statements from Indian banks (HDFC, SBI, ICICI, Axis, and generic).
     */
    fun parseCsvStatement(
        csvText: String,
        targetBankAccountId: String,
        targetBankName: String
    ): StatementParseResult {
        val cleanText = csvText.trimStart('\uFEFF').trim()
        if (cleanText.isEmpty()) {
            return StatementParseResult(emptyList(), 0L, 0L, targetBankName, "CSV", listOf("File is empty"))
        }
        val firstNonBlank = cleanText.lineSequence().firstOrNull { it.isNotBlank() } ?: ""
        val delimiter = if (firstNonBlank.contains("\t") && !firstNonBlank.contains(",")) '\t' else ','
        val rows = Gstr1ExcelImportEngine.parseDelimitedRecords(cleanText, delimiter)
        if (rows.isEmpty()) {
            return StatementParseResult(emptyList(), 0L, 0L, targetBankName, "CSV", listOf("File is empty"))
        }

        // Find header row
        var headerIndex = -1
        var dateCol = -1
        var descCol = -1
        var refCol = -1
        var debitCol = -1
        var creditCol = -1
        var amountCol = -1
        var typeCol = -1

        for (i in 0 until minOf(rows.size, 15)) {
            val cols = rows[i].map { it.trim().lowercase(Locale.ROOT) }
            val hasDate = cols.any { it.contains("date") }
            val hasNarration = cols.any { it.contains("narration") || it.contains("desc") || it.contains("particular") || it.contains("detail") || it.contains("remark") }
            val hasAmount = cols.any { it.contains("withdrawal") || it.contains("debit") || it.contains("deposit") || it.contains("credit") || it.contains("amount") }

            if (hasDate && (hasNarration || hasAmount)) {
                headerIndex = i
                cols.forEachIndexed { idx, name ->
                    when {
                        dateCol == -1 && name.contains("date") -> dateCol = idx
                        descCol == -1 && (name.contains("narration") || name.contains("desc") || name.contains("particular") || name.contains("detail") || name.contains("remark") || name.contains("payee")) -> descCol = idx
                        refCol == -1 && (name.contains("ref") || name.contains("chq") || name.contains("cheque") || name.contains("utr") || name.contains("tran id")) -> refCol = idx
                        debitCol == -1 && (name.contains("withdrawal") || name.contains("debit") || name.contains("dr") && !name.contains("address")) -> debitCol = idx
                        creditCol == -1 && (name.contains("deposit") || name.contains("credit") || name.contains("cr") && !name.contains("description")) -> creditCol = idx
                        amountCol == -1 && name.contains("amount") && !name.contains("tax") -> amountCol = idx
                        typeCol == -1 && (name == "type" || name.contains("dr/cr") || name.contains("cr/dr")) -> typeCol = idx
                    }
                }
                break
            }
        }

        // Fallback default columns if no standard header matched
        if (headerIndex == -1) {
            headerIndex = 0
            dateCol = 0
            descCol = 1
            debitCol = 2
            creditCol = 3
        }

        val parsedItems = mutableListOf<BankReconItem>()
        val errors = mutableListOf<String>()
        val baseTimestamp = System.currentTimeMillis()

        for (i in (headerIndex + 1) until rows.size) {
            val cols = rows[i]
            if (cols.isEmpty() || cols.all { it.isBlank() }) continue

            try {
                val rawDate = if (dateCol in cols.indices) cols[dateCol].trim() else ""
                val normDate = if (rawDate.isBlank()) "" else normalizeDate(rawDate)

                val desc = if (descCol in cols.indices) cols[descCol].trim() else "Bank Transaction"
                val ref = if (refCol in cols.indices) cols[refCol].trim() else ""

                var isCredit = false
                var amountPaise = 0L

                val debitStr = if (debitCol in cols.indices) cols[debitCol].trim() else ""
                val creditStr = if (creditCol in cols.indices) cols[creditCol].trim() else ""
                val amountStr = if (amountCol in cols.indices) cols[amountCol].trim() else ""
                val typeStr = if (typeCol in cols.indices) cols[typeCol].trim().uppercase(Locale.ROOT) else ""

                val debitVal = parsePaise(debitStr)
                val creditVal = parsePaise(creditStr)

                if (creditVal > 0L) {
                    isCredit = true
                    amountPaise = creditVal
                } else if (debitVal > 0L) {
                    isCredit = false
                    amountPaise = debitVal
                } else if (amountStr.isNotBlank()) {
                    val trimmedAmt = amountStr.trim()
                    val upperAmt = trimmedAmt.uppercase(Locale.ROOT)
                    val amtHasDrSuffix = upperAmt.endsWith("DR")
                    val amtHasCrSuffix = upperAmt.endsWith("CR")
                    val numericAmountStr = when {
                        amtHasDrSuffix -> trimmedAmt.dropLast(2).trim()
                        amtHasCrSuffix -> trimmedAmt.dropLast(2).trim()
                        else -> trimmedAmt
                    }
                    val rawAmt = parsePaise(numericAmountStr)
                    val isNegativeAmt = trimmedAmt.startsWith("-") ||
                        (trimmedAmt.startsWith("(") && trimmedAmt.endsWith(")"))
                    val hasDebitType = amtHasDrSuffix || typeStr == "DR" || typeStr == "D" || typeStr.contains("DEBIT") ||
                        typeStr.contains("WITHDRAW") || typeStr == "WD" || typeStr.endsWith("DR")
                    val hasCreditType = amtHasCrSuffix || typeStr == "CR" || typeStr == "C" || typeStr.contains("CREDIT") ||
                        typeStr.contains("DEPOSIT") || typeStr == "DEP" || typeStr.endsWith("CR")
                    isCredit = when {
                        hasDebitType -> false
                        hasCreditType -> !isNegativeAmt
                        else -> !isNegativeAmt && rawAmt > 0L
                    }
                    amountPaise = rawAmt
                }

                if (amountPaise > 0L) {
                    val itemId = "STMT-CSV-$baseTimestamp-${parsedItems.size + 1}"
                    parsedItems.add(
                        BankReconItem(
                            id = itemId,
                            bankAccountId = targetBankAccountId,
                            date = normDate,
                            description = desc,
                            amountPaise = amountPaise,
                            isCredit = isCredit,
                            isReconciled = false,
                            referenceNo = ref,
                            discrepancyNote = if (isCredit) "Statement deposit waiting for ledger match" else "Statement withdrawal waiting for ledger match"
                        )
                    )
                }
            } catch (e: Exception) {
                errors.add("Line ${i + 1}: ${e.message}")
            }
        }

        val totalCredits = parsedItems.filter { it.isCredit }.sumOf { it.amountPaise }
        val totalDebits = parsedItems.filter { !it.isCredit }.sumOf { it.amountPaise }

        return StatementParseResult(
            items = parsedItems,
            totalCreditsPaise = totalCredits,
            totalDebitsPaise = totalDebits,
            detectedBank = targetBankName,
            format = "CSV",
            parseErrors = errors
        )
    }

    /**
     * Parses standard OFX (Open Financial Exchange) format statements.
     */
    fun parseOfxStatement(
        ofxText: String,
        targetBankAccountId: String,
        targetBankName: String
    ): StatementParseResult {
        val parsedItems = mutableListOf<BankReconItem>()
        val errors = mutableListOf<String>()
        val baseTimestamp = System.currentTimeMillis()

        // Match each <STMTTRN>...</STMTTRN> or block
        val regex = Regex("(?i)<STMTTRN>([\\s\\S]*?)(?:</STMTTRN>|(?=<STMTTRN>)|$)")
        val matches = regex.findAll(ofxText)

        for ((idx, match) in matches.withIndex()) {
            val trnContent = match.groupValues[1]

            val trnType = extractOfxTag(trnContent, "TRNTYPE")
            val dtPosted = extractOfxTag(trnContent, "DTPOSTED")
            val trnAmt = extractOfxTag(trnContent, "TRNAMT")
            val fitId = extractOfxTag(trnContent, "FITID")
            val name = extractOfxTag(trnContent, "NAME")
            val memo = extractOfxTag(trnContent, "MEMO")
            val checkNum = extractOfxTag(trnContent, "CHECKNUM")

            val fullDesc = listOf(name, memo).filter { it.isNotBlank() }.joinToString(" - ")
                .ifEmpty { "OFX Transaction $trnType" }

            val cleanAmtPaise = parsePaise(trnAmt.replace("+", ""))
            val normalizedTrnType = trnType.trim().uppercase(Locale.ROOT)
            val isNegType = normalizedTrnType in setOf("DEBIT", "CHECK", "ATM", "POS", "FEE", "SRVCHG", "DIRECTDEBIT", "PAYMENT")
            val isPosType = normalizedTrnType in setOf("CREDIT", "DEP", "DIRECTDEP", "INT", "DIV")
            val isCredit = when {
                trnAmt.trim().startsWith("-") -> false
                isNegType -> false
                isPosType -> true
                else -> trnAmt.isNotBlank()
            }

            val normDate = parseOfxDate(dtPosted)

            if (cleanAmtPaise > 0L) {
                parsedItems.add(
                    BankReconItem(
                        id = "STMT-OFX-$baseTimestamp-${idx + 1}",
                        bankAccountId = targetBankAccountId,
                        date = normDate,
                        description = fullDesc,
                        amountPaise = cleanAmtPaise,
                        isCredit = isCredit,
                        isReconciled = false,
                        referenceNo = fitId.ifEmpty { checkNum },
                        discrepancyNote = if (isCredit) "OFX Credit entry" else "OFX Debit entry"
                    )
                )
            }
        }

        val totalCredits = parsedItems.filter { it.isCredit }.sumOf { it.amountPaise }
        val totalDebits = parsedItems.filter { !it.isCredit }.sumOf { it.amountPaise }

        return StatementParseResult(
            items = parsedItems,
            totalCreditsPaise = totalCredits,
            totalDebitsPaise = totalDebits,
            detectedBank = targetBankName,
            format = "OFX/QFX",
            parseErrors = errors
        )
    }

    private fun extractOfxTag(content: String, tag: String): String {
        // Handle both <TAG>value</TAG> and <TAG>value\n
        val pattern = Regex("(?i)<$tag>([^<\\r\\n]+)(?:</$tag>)?")
        val match = pattern.find(content)
        return match?.groupValues?.get(1)?.trim() ?: ""
    }

    private fun parseOfxDate(rawDate: String): String {
        val clean = rawDate.trim().take(8)
        if (clean.length == 8 && clean.all { it.isDigit() }) {
            val y = clean.substring(0, 4).toIntOrNull()
            val m = clean.substring(4, 6).toIntOrNull()
            val d = clean.substring(6, 8).toIntOrNull()
            if (y != null && m != null && d != null) {
                return try {
                    java.time.LocalDate.of(y, m, d).toString()
                } catch (_: Exception) {
                    ""
                }
            }
        }
        return normalizeDate(rawDate)
    }

    private fun parseCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        var cur = StringBuilder()
        var inQuotes = false

        val delimiter = if (line.contains("\t") && !line.contains(",")) '\t' else ','

        for (ch in line) {
            when {
                ch == '"' -> inQuotes = !inQuotes
                ch == delimiter && !inQuotes -> {
                    result.add(cur.toString().trim().removeSurrounding("\""))
                    cur = StringBuilder()
                }
                else -> cur.append(ch)
            }
        }
        result.add(cur.toString().trim().removeSurrounding("\""))
        return result
    }

    private fun normalizeDate(dateStr: String): String {
        val trimmed = dateStr.trim().replace("/", "-")
        if (trimmed.isEmpty()) return ""

        // Direct YYYY-MM-DD / DD-MM-YYYY check via calendar validator
        FinancialEngine.parseValidLocalDate(trimmed)?.let { parsed ->
            return String.format(Locale.ENGLISH, "%04d-%02d-%02d", parsed.year, parsed.monthValue, parsed.dayOfMonth)
        }

        // Check word-month and 2-digit year variants (e.g. 02-Oct-2026, 02-10-26)
        val parts = trimmed.split("-")
        if (parts.size == 3) {
            val p0 = parts[0].trim()
            val p1 = parts[1].trim()
            val p2 = parts[2].trim()

            val monthMap = mapOf(
                "jan" to 1, "feb" to 2, "mar" to 3, "apr" to 4, "may" to 5, "jun" to 6,
                "jul" to 7, "aug" to 8, "sep" to 9, "oct" to 10, "nov" to 11, "dec" to 12
            )
            val month = monthMap[p1.lowercase(Locale.ROOT).take(3)] ?: p1.toIntOrNull() ?: return ""

            val (y, d) = when {
                p0.length == 4 -> (p0.toIntOrNull() ?: return "") to (p2.toIntOrNull() ?: return "")
                p2.length == 4 -> (p2.toIntOrNull() ?: return "") to (p0.toIntOrNull() ?: return "")
                p2.length == 2 -> ((p2.toIntOrNull() ?: return "") + 2000) to (p0.toIntOrNull() ?: return "")
                else -> return ""
            }
            return try {
                val parsed = java.time.LocalDate.of(y, month, d)
                String.format(Locale.ENGLISH, "%04d-%02d-%02d", parsed.year, parsed.monthValue, parsed.dayOfMonth)
            } catch (_: Exception) {
                ""
            }
        }

        return ""
    }

    fun parsePaise(amountStr: String): Long {
        if (amountStr.isBlank()) return 0L
        var unsigned = amountStr.trim()
        if (unsigned.startsWith("(") && unsigned.endsWith(")")) {
            unsigned = unsigned.substring(1, unsigned.length - 1).trim()
        }
        unsigned = unsigned.replace("+", "").replace("-", "").trim()
        return FinancialEngine.parsePaiseFromRupees(unsigned)
    }

    // -------------------------------------------------------------
    // 2. Automated Matching Algorithm
    // -------------------------------------------------------------

    data class MatchCandidate(
        val transaction: Transaction,
        val score: Int,
        val reasons: List<String>
    )

    /**
     * Attempts to automatically match statement items with existing transactions
     * based on Amount, Date proximity, Payee/Payer names, invoice numbers, and transaction IDs.
     */
    fun matchBankStatement(
        statementItems: List<BankReconItem>,
        transactions: List<Transaction>,
        parties: List<Party>
    ): List<BankReconItem> {
        val partyMap = parties.associateBy { it.id }

        // Filter valid eligible ledger transactions (not voided)
        val availableLedger = transactions.filter { !it.isVoided }.toMutableList()

        // First preserve user-confirmed matches
        val confirmedTxnIds = statementItems.filter { it.isUserConfirmed && it.matchedTxnId != null }
            .mapNotNull { it.matchedTxnId }.toSet()
        availableLedger.removeAll { confirmedTxnIds.contains(it.id) }

        return statementItems.map { stmt ->
            if (stmt.isUserConfirmed && stmt.isReconciled) {
                stmt
            } else {
                val candidate = findBestMatch(stmt, availableLedger, partyMap)
                if (candidate != null && candidate.score >= 50) {
                    availableLedger.remove(candidate.transaction)
                    val isExact = candidate.score >= 80
                    stmt.copy(
                        matchedTxnId = candidate.transaction.id,
                        matchConfidence = candidate.score,
                        matchReason = candidate.reasons.joinToString(" · "),
                        isReconciled = isExact, // auto-mark exact matches as ready/reconciled
                        discrepancyNote = if (isExact) "" else "Probable match: Review details before confirming"
                    )
                } else {
                    stmt.copy(
                        matchedTxnId = null,
                        matchConfidence = 0,
                        matchReason = "",
                        isReconciled = false,
                        discrepancyNote = if (stmt.isCredit) "Unrecorded deposit / customer receipt" else "Unrecorded payment, expense or bank charge"
                    )
                }
            }
        }
    }

    /**
     * Evaluates candidate transactions against a statement entry and returns best match with confidence score.
     */
    private fun findBestMatch(
        stmt: BankReconItem,
        candidates: List<Transaction>,
        partyMap: Map<String, Party>
    ): MatchCandidate? {
        var bestCandidate: MatchCandidate? = null

        for (txn in candidates) {
            if (txn.type == TransactionType.Quotation) continue
            var score = 0
            val reasons = mutableListOf<String>()

            // 1. Amount Score (Up to 50 pts)
            val amtDiffPaise = abs(stmt.amountPaise - txn.amountPaise)
            if (amtDiffPaise == 0L) {
                score += 50
                reasons.add("Exact amount ₹${stmt.amountPaise / 100.0}")
            } else if (amtDiffPaise <= 10000L) { // within ₹100
                score += 25
                reasons.add("Near amount (₹${amtDiffPaise / 100.0} diff)")
            } else {
                // Amounts don't match, very unlikely
                continue
            }

            // 2. Direction Alignment (Deposit vs Withdrawal)
            val isExpectedCredit = when (txn.type) {
                TransactionType.Sale, TransactionType.NonGstBill, TransactionType.PaymentIn, TransactionType.DebitNote -> true
                TransactionType.Purchase, TransactionType.Expense, TransactionType.PaymentOut, TransactionType.Drawing, TransactionType.CreditNote -> false
                TransactionType.Quotation -> false
            }

            if (stmt.isCredit == isExpectedCredit) {
                score += 15
            } else {
                // Opposite direction, heavily penalize
                score -= 40
            }

            // 3. Date Proximity Score (Up to 20 pts)
            val daysDiff = calculateDayDifference(stmt.date, txn.date)
            when {
                daysDiff == 0L -> {
                    score += 20
                    reasons.add("Same date (${stmt.date})")
                }
                daysDiff in 1..2 -> {
                    score += 15
                    reasons.add("Date within $daysDiff day(s)")
                }
                daysDiff in 3..5 -> {
                    score += 10
                    reasons.add("Date within $daysDiff days")
                }
                daysDiff in 6..10 -> {
                    score += 5
                    reasons.add("Date within 10 days")
                }
            }

            // 4. Payee / Payer / Narration Matching (Up to 25 pts)
            val party = txn.partyId?.let { partyMap[it] }
            val stmtNarration = stmt.description.lowercase(Locale.ROOT)
            val stmtRef = stmt.referenceNo.lowercase(Locale.ROOT)

            if (party != null) {
                val partyName = party.name.lowercase(Locale.ROOT)
                val partyWords = partyName.split(" ").filter { it.length > 2 }
                val matchesWord = partyWords.any { word -> stmtNarration.contains(word) }

                if (stmtNarration.contains(partyName)) {
                    score += 25
                    reasons.add("Payee match: ${party.name}")
                } else if (matchesWord) {
                    score += 15
                    reasons.add("Party keyword match: ${party.name}")
                }
            }

            // Invoice number match
            if (txn.invoiceNumber.isNotBlank()) {
                val cleanInv = txn.invoiceNumber.lowercase(Locale.ROOT)
                val invDigits = cleanInv.filter { it.isDigit() }
                if (cleanInv.isNotBlank() && stmtNarration.contains(cleanInv)) {
                    score += 25
                    reasons.add("Invoice #${txn.invoiceNumber} match")
                } else if (invDigits.length >= 4 && stmtNarration.contains(invDigits)) {
                    score += 15
                    reasons.add("Invoice ref #${invDigits} match")
                }
            }

            // Cheque / UTR / Reference match
            if (stmtRef.isNotBlank() && (txn.remarks.contains(stmtRef, ignoreCase = true) || txn.id.contains(stmtRef, ignoreCase = true))) {
                score += 20
                reasons.add("Reference #$stmtRef match")
            }

            // Payment mode heuristic
            if (txn.paymentMode == PaymentMode.Bank && stmtNarration.contains("neft") ||
                txn.paymentMode == PaymentMode.UPI && (stmtNarration.contains("upi") || stmtNarration.contains("gpay") || stmtNarration.contains("paytm"))) {
                score += 5
                reasons.add("Payment mode match")
            }

            val candidate = MatchCandidate(txn, score.coerceIn(0, 100), reasons)
            if (bestCandidate == null || candidate.score > bestCandidate.score) {
                bestCandidate = candidate
            }
        }

        return bestCandidate
    }

    private fun calculateDayDifference(dateStr1: String, dateStr2: String): Long {
        val d1 = FinancialEngine.parseValidLocalDate(dateStr1) ?: return 99L
        val d2 = FinancialEngine.parseValidLocalDate(dateStr2) ?: return 99L
        return abs(java.time.temporal.ChronoUnit.DAYS.between(d1, d2))
    }

    // -------------------------------------------------------------
    // 3. Discrepancy Resolution Builder
    // -------------------------------------------------------------

    /**
     * Builds a balancing Transaction to resolve unmatched bank statement entries
     * (e.g. Bank charges, interest income, unrecorded payments).
     */
    fun createBalancingTransaction(
        stmt: BankReconItem,
        resolutionType: DiscrepancyResolutionType,
        branchId: String = "BR-MAIN",
        partyId: String? = null,
        customRemarks: String = ""
    ): Transaction {
        val txnId = "TXN-RECON-${System.currentTimeMillis()}-${java.util.UUID.randomUUID().toString().take(6)}"
        val finalRemarks = customRemarks.ifBlank { "Reconciled from statement: ${stmt.description}" }

        return when (resolutionType) {
            DiscrepancyResolutionType.BANK_CHARGES -> {
                // Bank charges expense (18% inclusive, half-up rounded with symmetric CGST == SGST)
                val gstRate = 18
                val totalPaise = stmt.amountPaise
                val taxableHalfUp = (totalPaise * 10000L + 5900L) / 11800L
                val eachGst = (taxableHalfUp * 900L + 5000L) / 10000L
                val taxablePaise = totalPaise - (eachGst * 2L)

                Transaction(
                    id = txnId,
                    branchId = branchId,
                    date = stmt.date,
                    partyId = null,
                    amountPaise = totalPaise,
                    type = TransactionType.Expense,
                    expenseCategory = ExpenseCategory.Indirect,
                    paymentMode = PaymentMode.Bank,
                    bankAccountId = stmt.bankAccountId,
                    remarks = "Bank SMS / Service Charges: ${stmt.description}",
                    entryMode = "itemized",
                    invoiceNumber = "BNK-CHG-${stmt.date.replace("-", "")}",
                    items = listOf(
                        InvoiceLineItem(
                            itemId = "EXP-BANK-CHG",
                            name = "Bank Service & Account Charges",
                            hsnCode = "9971",
                            quantity = 1,
                            unit = "SRV",
                            ratePaise = taxablePaise,
                            gstRate = gstRate,
                            taxableAmountPaise = taxablePaise,
                            cgstPaise = eachGst,
                            sgstPaise = eachGst,
                            igstPaise = 0L,
                            totalPaise = totalPaise
                        )
                    )
                )
            }
            DiscrepancyResolutionType.INTEREST_INCOME -> {
                // Interest credited by bank
                Transaction(
                    id = txnId,
                    branchId = branchId,
                    date = stmt.date,
                    partyId = null,
                    amountPaise = stmt.amountPaise,
                    type = TransactionType.PaymentIn,
                    paymentMode = PaymentMode.Bank,
                    bankAccountId = stmt.bankAccountId,
                    remarks = "Bank Savings/Current Interest Credit: ${stmt.description}",
                    entryMode = "quick",
                    invoiceNumber = "INT-${stmt.date.replace("-", "")}"
                )
            }
            DiscrepancyResolutionType.CUSTOMER_PAYMENT -> {
                // Direct customer NEFT / UPI settlement
                Transaction(
                    id = txnId,
                    branchId = branchId,
                    date = stmt.date,
                    partyId = partyId,
                    amountPaise = stmt.amountPaise,
                    type = TransactionType.PaymentIn,
                    paymentMode = PaymentMode.Bank,
                    bankAccountId = stmt.bankAccountId,
                    remarks = finalRemarks,
                    entryMode = "quick"
                )
            }
            DiscrepancyResolutionType.SUPPLIER_PAYMENT -> {
                // Direct vendor payment from bank
                Transaction(
                    id = txnId,
                    branchId = branchId,
                    date = stmt.date,
                    partyId = partyId,
                    amountPaise = stmt.amountPaise,
                    type = TransactionType.PaymentOut,
                    paymentMode = PaymentMode.Bank,
                    bankAccountId = stmt.bankAccountId,
                    remarks = finalRemarks,
                    entryMode = "quick"
                )
            }
            DiscrepancyResolutionType.DIRECT_EXPENSE -> {
                Transaction(
                    id = txnId,
                    branchId = branchId,
                    date = stmt.date,
                    partyId = null,
                    amountPaise = stmt.amountPaise,
                    type = TransactionType.Expense,
                    expenseCategory = ExpenseCategory.Direct,
                    paymentMode = PaymentMode.Bank,
                    bankAccountId = stmt.bankAccountId,
                    remarks = finalRemarks,
                    entryMode = "quick"
                )
            }
            DiscrepancyResolutionType.TRANSFER -> {
                Transaction(
                    id = txnId,
                    branchId = branchId,
                    date = stmt.date,
                    partyId = null,
                    amountPaise = stmt.amountPaise,
                    type = if (stmt.isCredit) TransactionType.PaymentIn else TransactionType.PaymentOut,
                    paymentMode = PaymentMode.Bank,
                    bankAccountId = stmt.bankAccountId,
                    remarks = finalRemarks,
                    entryMode = "quick"
                )
            }
        }
    }

    // -------------------------------------------------------------
    // 4. Built-in Sample Datasets for Testing & Instant Demo
    // -------------------------------------------------------------

    fun getSampleHdfcStatementCsv(): String {
        return """
Date,Narration,Chq./Ref.No.,Value Dt,Withdrawal Amt.,Deposit Amt.,Closing Balance
01/10/2026,"UPI-GPAY-RECVD FROM SAI BALAJI-9820192837",UPI/6271829102,01/10/2026,,10000.00,195000.00
01/10/2026,"POS SWIPE BATCH SETTLEMENT DADAR STORE",POS/20261001-9,01/10/2026,,4250.00,199250.00
02/10/2026,"NEFT-SENT TO GUJARAT ROYAL SPICES-PUR/GJ-9912",NEFT/HDFC001928,02/10/2026,25000.00,,174250.00
02/10/2026,"MONTHLY BANK SMS CHARGES & GST",CHG/Q3/SMS-29,02/10/2026,29.50,,174220.50
02/10/2026,"CHQ CLR-DIVIDEND / MISC SETTLEMENT",CHQ/918231,02/10/2026,,1250.00,175470.50
03/10/2026,"IMPS-RECVD FROM OMKAR DEPARTMENTAL STORE",IMPS/619283712,03/10/2026,,8500.00,183970.50
03/10/2026,"DIRECT DEBIT-BESCOM ELECTRICITY DISPATCH",DD/PWR-7712,03/10/2026,3500.00,,180470.50
        """.trimIndent()
    }

    fun getSampleSbiStatementOfx(): String {
        return """
OFXHEADER:100
DATA:OFXSGML
VERSION:102
SECURITY:NONE
ENCODING:USASCII
CHARSET:1252
COMPRESSION:NONE
OLDFILEUID:NONE
NEWFILEUID:NONE

<OFX>
<SIGNONMSGSRSV1>
<SONRS>
<STATUS>
<CODE>0
<SEVERITY>INFO
</STATUS>
<DTSERVER>20261003120000
<LANGUAGE>ENG
</SONRS>
</SIGNONMSGSRSV1>
<BANKMSGSRSV1>
<STMTTRNRS>
<TRNUID>1001
<STATUS>
<CODE>0
<SEVERITY>INFO
</STATUS>
<STMTRS>
<CURDEF>INR
<BANKACCTFROM>
<BANKID>SBIN0000300
<ACCTID>38921092831
<ACCTTYPE>CHECKING
</BANKACCTFROM>
<BANKTRANLIST>
<DTSTART>20261001
<DTEND>20261003
<STMTTRN>
<TRNTYPE>CREDIT
<DTPOSTED>20261001
<TRNAMT>10000.00
<FITID>SBI20261001001
<NAME>UPI-RECVD FROM SAI BALAJI
<MEMO>INV/2026-27/0001 PART PAYMENT
</STMTTRN>
<STMTTRN>
<TRNTYPE>DEBIT
<DTPOSTED>20261002
<TRNAMT>-25000.00
<FITID>SBI20261002002
<NAME>NEFT TO GUJARAT ROYAL SPICES
<MEMO>PUR/GJ-9912 INVOICE PAYMENT
</STMTTRN>
<STMTTRN>
<TRNTYPE>DEBIT
<DTPOSTED>20261002
<TRNAMT>-29.50
<FITID>SBI20261002003
<NAME>SBI CONSOLIDATED SMS CHARGES
<MEMO>BANK SERVICE CHARGES
</STMTTRN>
<STMTTRN>
<TRNTYPE>CREDIT
<DTPOSTED>20261003
<TRNAMT>8500.00
<FITID>SBI20261003004
<NAME>IMPS-OMKAR DEPT STORE
<MEMO>KHATA COLLECTION
</STMTTRN>
</BANKTRANLIST>
<LEDGERBAL>
<BALAMT>75000.00
<DTASOF>20261003
</LEDGERBAL>
</STMTRS>
</STMTTRNRS>
</BANKMSGSRSV1>
</OFX>
        """.trimIndent()
    }
}
