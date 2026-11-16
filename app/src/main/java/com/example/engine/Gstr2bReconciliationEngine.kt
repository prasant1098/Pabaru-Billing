package com.example.engine

import com.example.model.Party
import com.example.model.Transaction
import com.example.model.TransactionType
import org.json.JSONObject
import java.util.Locale
import kotlin.math.abs

/**
 * Automated GSTR-2B Reconciliation Engine for Indian GST Compliance.
 *
 * Automatically cross-references government GSTR-2B inward ITC files (JSON or CSV/Excel)
 * with internal purchase ledgers with smart ₹1.00 rounding tolerance to identify,
 * categorize, and audit Input Tax Credit (ITC) discrepancies:
 * 1. Fully Matched (ITC Safe to Claim)
 * 2. Value Mismatch (Taxable or Tax Differ by > ₹1)
 * 3. Missing in Books (In 2B from supplier, but not booked by user - Unclaimed ITC)
 * 4. Missing in 2B (Booked by user, but supplier failed to file - Rule 36(4) Risk)
 * 5. Ineligible ITC (Marked ineligible under Sec 17(5) or POS rules)
 */
object Gstr2bReconciliationEngine {

    enum class MatchStatus {
        FullyMatched,
        ValueMismatch,
        MissingInBooks,
        MissingIn2B,
        IneligibleITC
    }

    enum class InvoiceType {
        RegularB2B,
        AmendedB2BA,
        CreditDebitNote,
        ReverseCharge
    }

    data class Gstr2bInvoice(
        val supplierGstin: String,
        val supplierTradeName: String,
        val invoiceNumber: String,
        val invoiceDate: String, // dd/MM/yyyy
        val invoiceType: InvoiceType = InvoiceType.RegularB2B,
        val invoiceValuePaise: Long,
        val taxableValuePaise: Long,
        val cgstPaise: Long,
        val sgstPaise: Long,
        val igstPaise: Long,
        val cessPaise: Long = 0L,
        val posStateCode: String = "",
        val isReverseCharge: Boolean = false,
        val itcAvailable: Boolean = true,
        val itcBlockedReason: String = "",
        val filingPeriod: String = "" // e.g. "082025"
    ) {
        val totalTaxPaise: Long get() = cgstPaise + sgstPaise + igstPaise + cessPaise
    }

    data class BookPurchaseEntry(
        val transactionId: String,
        val invoiceNumber: String,
        val invoiceDate: String,
        val partyId: String,
        val partyName: String,
        val partyGstin: String,
        val taxableValuePaise: Long,
        val cgstPaise: Long,
        val sgstPaise: Long,
        val igstPaise: Long,
        val totalAmountPaise: Long
    ) {
        val totalTaxPaise: Long get() = cgstPaise + sgstPaise + igstPaise
    }

    data class ReconciliationItem(
        val id: String,
        val matchStatus: MatchStatus,
        val supplierGstin: String,
        val supplierName: String,
        val invoiceNumber: String,
        val invoiceDate: String,

        // GSTR-2B Values (0 if missing in 2B)
        val gstr2bTaxablePaise: Long = 0L,
        val gstr2bCgstPaise: Long = 0L,
        val gstr2bSgstPaise: Long = 0L,
        val gstr2bIgstPaise: Long = 0L,
        val gstr2bTotalTaxPaise: Long = 0L,
        val gstr2bTotalValuePaise: Long = 0L,
        val itcAvailable: Boolean = true,
        val itcBlockedReason: String = "",

        // Book Values (0 if missing in books)
        val booksTransactionId: String? = null,
        val booksTaxablePaise: Long = 0L,
        val booksCgstPaise: Long = 0L,
        val booksSgstPaise: Long = 0L,
        val booksIgstPaise: Long = 0L,
        val booksTotalTaxPaise: Long = 0L,
        val booksTotalValuePaise: Long = 0L,

        // Variances (2B minus Books)
        val diffTaxablePaise: Long = 0L,
        val diffTaxPaise: Long = 0L,
        val remarks: String = ""
    )

    data class ReconciliationSummary(
        val totalGstr2bCount: Int,
        val totalGstr2bTaxablePaise: Long,
        val totalGstr2bItcAvailablePaise: Long,
        val totalGstr2bItcBlockedPaise: Long,

        val totalBooksPurchaseCount: Int,
        val totalBooksTaxablePaise: Long,
        val totalBooksItcClaimedPaise: Long,

        // Breakdown by status
        val matchedCount: Int,
        val matchedItcPaise: Long,

        val valueMismatchCount: Int,
        val valueMismatchTaxVariancePaise: Long,

        val missingInBooksCount: Int,
        val missingInBooksUnclaimedItcPaise: Long,

        val missingIn2bCount: Int,
        val missingIn2bAtRiskItcPaise: Long,

        val ineligibleItcCount: Int,
        val ineligibleItcPaise: Long
    )

    /**
     * Normalizes an invoice number by removing whitespace, slashes, dashes,
     * leading zeros, and case differences for smart matching.
     * e.g. "INV/2025/001" -> "INV20251"
     */
    fun normalizeInvoiceNum(raw: String): String {
        val segments = raw.trim().uppercase(Locale.ENGLISH).split(Regex("[^A-Z0-9]+")).filter { it.isNotEmpty() }
        return segments.joinToString("") { seg ->
            if (seg.all { it.isDigit() }) seg.trimStart('0').ifEmpty { "0" } else seg
        }
    }

    /**
     * Normalizes a GSTIN (stripping spaces and uppercase).
     */
    fun normalizeGstin(raw: String): String {
        return raw.trim().uppercase(Locale.ENGLISH)
    }

    /**
     * Smart tolerance threshold in paise (100 paise = ₹ 1.00).
     */
    const val SMART_TOLERANCE_PAISE = 100L

    private fun jsonMoneyToPaise(obj: JSONObject, key: String): Long {
        val raw = obj.opt(key) ?: return 0L
        return when (raw) {
            is Number -> kotlin.math.round(raw.toDouble() * 100.0).toLong()
            is String -> parseSignedPaise(raw)
            else -> 0L
        }
    }

    private fun parseSignedPaise(raw: String): Long {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return 0L
        val isNegative = trimmed.startsWith("-") || (trimmed.startsWith("(") && trimmed.endsWith(")"))
        val unsigned = trimmed.removePrefix("-").removeSurrounding("(", ")").trim()
        val mag = FinancialEngine.parsePaiseFromRupees(unsigned)
        return if (isNegative) -mag else mag
    }

    private fun separatorPreservingNorm(raw: String): String {
        return raw.trim().uppercase(Locale.ENGLISH)
            .split(Regex("[^A-Z0-9]+"))
            .filter { it.isNotEmpty() }
            .joinToString("/") { seg ->
                if (seg.all { it.isDigit() }) seg.trimStart('0').ifEmpty { "0" } else seg
            }
    }

    /**
     * Reconciles a list of GSTR-2B invoices against local purchase transactions.
     */
    fun reconcile(
        gstr2bList: List<Gstr2bInvoice>,
        transactions: List<Transaction>,
        parties: List<Party>,
        homeStateCode: String = "27"
    ): Pair<List<ReconciliationItem>, ReconciliationSummary> {
        val partyMap = parties.associateBy { it.id }

        // Extract purchases from local transactions
        val bookPurchases = transactions.filter { !it.isVoided && it.type == TransactionType.Purchase }.map { t ->
            val party = partyMap[t.partyId]
            val gstin = party?.gstin?.trim() ?: ""
            val partyName = party?.name?.ifEmpty { "Cash / Local Supplier" } ?: "Supplier"

            // Calculate taxable and tax breakdown from transaction items or default 18% standard
            val (taxable, cgst, sgst, igst) = if (t.items.isNotEmpty()) {
                val tx = t.items.sumOf { it.taxableAmountPaise }
                val c = t.items.sumOf { it.cgstPaise }
                val s = t.items.sumOf { it.sgstPaise }
                val i = t.items.sumOf { it.igstPaise }
                Triple(tx, c, s) to i
            } else {
                val tx = (t.amountPaise * 10000L + 5900L) / 11800L
                val taxTotal = t.amountPaise - tx
                val isInter = FinancialEngine.isValidGstinFormat(gstin) &&
                    !gstin.startsWith(homeStateCode.ifBlank { "27" })
                if (isInter) {
                    Triple(tx, 0L, 0L) to taxTotal
                } else {
                    val half = taxTotal / 2L
                    Triple(tx, half, taxTotal - half) to 0L
                }
            }.let { (trio, i) ->
                listOf(trio.first, trio.second, trio.third, i)
            }

            BookPurchaseEntry(
                transactionId = t.id,
                invoiceNumber = t.invoiceNumber.ifEmpty { t.id },
                invoiceDate = t.date,
                partyId = t.partyId ?: "",
                partyName = partyName,
                partyGstin = gstin,
                taxableValuePaise = taxable,
                cgstPaise = cgst,
                sgstPaise = sgst,
                igstPaise = igst,
                totalAmountPaise = t.amountPaise
            )
        }

        val reconciledItems = mutableListOf<ReconciliationItem>()
        val matchedBookEntryIds = mutableSetOf<String>()

        // 1. Process GSTR-2B Entries
        for ((idx, inv2b) in gstr2bList.withIndex()) {
            val norm2bGstin = normalizeGstin(inv2b.supplierGstin)
            val norm2bInv = normalizeInvoiceNum(inv2b.invoiceNumber)
            val raw2bInv = inv2b.invoiceNumber.trim().uppercase(Locale.ENGLISH)
            val sep2bInv = separatorPreservingNorm(inv2b.invoiceNumber)

            // Ineligible in 2B
            if (!inv2b.itcAvailable) {
                reconciledItems.add(
                    ReconciliationItem(
                        id = "2B-INEL-$idx",
                        matchStatus = MatchStatus.IneligibleITC,
                        supplierGstin = inv2b.supplierGstin,
                        supplierName = inv2b.supplierTradeName,
                        invoiceNumber = inv2b.invoiceNumber,
                        invoiceDate = inv2b.invoiceDate,
                        gstr2bTaxablePaise = inv2b.taxableValuePaise,
                        gstr2bCgstPaise = inv2b.cgstPaise,
                        gstr2bSgstPaise = inv2b.sgstPaise,
                        gstr2bIgstPaise = inv2b.igstPaise,
                        gstr2bTotalTaxPaise = inv2b.totalTaxPaise,
                        gstr2bTotalValuePaise = inv2b.invoiceValuePaise,
                        itcAvailable = false,
                        itcBlockedReason = inv2b.itcBlockedReason.ifEmpty { "Ineligible under Section 17(5) / POS Rules" },
                        remarks = "Ineligible ITC marked by GST Portal"
                    )
                )
                continue
            }

            // Require non-blank valid supplier GSTIN on both sides; rank best candidate instead of firstOrNull
            val candidate = if (norm2bGstin.isNotEmpty()) {
                val eligibleSameSupplier = bookPurchases.filter { b ->
                    !matchedBookEntryIds.contains(b.transactionId) &&
                        normalizeGstin(b.partyGstin) == norm2bGstin
                }
                eligibleSameSupplier
                    .mapNotNull { b ->
                        val rawBookInv = b.invoiceNumber.trim().uppercase(Locale.ENGLISH)
                        val sepBookInv = separatorPreservingNorm(b.invoiceNumber)
                        val collapsedBookInv = normalizeInvoiceNum(b.invoiceNumber)
                        val invScore = when {
                            rawBookInv == raw2bInv -> 30
                            sepBookInv == sep2bInv -> 20
                            collapsedBookInv == norm2bInv -> 10
                            else -> 0
                        }
                        val amtDiff = abs(b.totalAmountPaise - inv2b.invoiceValuePaise)
                        if (invScore > 0 || amtDiff <= SMART_TOLERANCE_PAISE) {
                            val totalRank = (invScore * 1_000_000L) - amtDiff.coerceAtMost(999_999L)
                            b to totalRank
                        } else null
                    }
                    .maxByOrNull { it.second }
                    ?.first
            } else null

            if (candidate != null) {
                matchedBookEntryIds.add(candidate.transactionId)

                val diffTaxable = inv2b.taxableValuePaise - candidate.taxableValuePaise
                val diffTax = inv2b.totalTaxPaise - candidate.totalTaxPaise

                val isWithinTolerance = abs(diffTaxable) <= SMART_TOLERANCE_PAISE && abs(diffTax) <= SMART_TOLERANCE_PAISE

                if (isWithinTolerance) {
                    reconciledItems.add(
                        ReconciliationItem(
                            id = "MATCH-$idx",
                            matchStatus = MatchStatus.FullyMatched,
                            supplierGstin = inv2b.supplierGstin,
                            supplierName = inv2b.supplierTradeName.ifEmpty { candidate.partyName },
                            invoiceNumber = inv2b.invoiceNumber,
                            invoiceDate = inv2b.invoiceDate,
                            gstr2bTaxablePaise = inv2b.taxableValuePaise,
                            gstr2bCgstPaise = inv2b.cgstPaise,
                            gstr2bSgstPaise = inv2b.sgstPaise,
                            gstr2bIgstPaise = inv2b.igstPaise,
                            gstr2bTotalTaxPaise = inv2b.totalTaxPaise,
                            gstr2bTotalValuePaise = inv2b.invoiceValuePaise,
                            booksTransactionId = candidate.transactionId,
                            booksTaxablePaise = candidate.taxableValuePaise,
                            booksCgstPaise = candidate.cgstPaise,
                            booksSgstPaise = candidate.sgstPaise,
                            booksIgstPaise = candidate.igstPaise,
                            booksTotalTaxPaise = candidate.totalTaxPaise,
                            booksTotalValuePaise = candidate.totalAmountPaise,
                            diffTaxablePaise = diffTaxable,
                            diffTaxPaise = diffTax,
                            remarks = "Fully matched within ₹1.00 tolerance. ITC 100% compliant."
                        )
                    )
                } else {
                    reconciledItems.add(
                        ReconciliationItem(
                            id = "MISMATCH-$idx",
                            matchStatus = MatchStatus.ValueMismatch,
                            supplierGstin = inv2b.supplierGstin,
                            supplierName = inv2b.supplierTradeName.ifEmpty { candidate.partyName },
                            invoiceNumber = inv2b.invoiceNumber,
                            invoiceDate = inv2b.invoiceDate,
                            gstr2bTaxablePaise = inv2b.taxableValuePaise,
                            gstr2bCgstPaise = inv2b.cgstPaise,
                            gstr2bSgstPaise = inv2b.sgstPaise,
                            gstr2bIgstPaise = inv2b.igstPaise,
                            gstr2bTotalTaxPaise = inv2b.totalTaxPaise,
                            gstr2bTotalValuePaise = inv2b.invoiceValuePaise,
                            booksTransactionId = candidate.transactionId,
                            booksTaxablePaise = candidate.taxableValuePaise,
                            booksCgstPaise = candidate.cgstPaise,
                            booksSgstPaise = candidate.sgstPaise,
                            booksIgstPaise = candidate.igstPaise,
                            booksTotalTaxPaise = candidate.totalTaxPaise,
                            booksTotalValuePaise = candidate.totalAmountPaise,
                            diffTaxablePaise = diffTaxable,
                            diffTaxPaise = diffTax,
                            remarks = "Value variance: Taxable Δ ₹${diffTaxable / 100.0}, Tax Δ ₹${diffTax / 100.0}"
                        )
                    )
                }
            } else {
                // In 2B but Missing in Books
                reconciledItems.add(
                    ReconciliationItem(
                        id = "MISS-BOOKS-$idx",
                        matchStatus = MatchStatus.MissingInBooks,
                        supplierGstin = inv2b.supplierGstin,
                        supplierName = inv2b.supplierTradeName,
                        invoiceNumber = inv2b.invoiceNumber,
                        invoiceDate = inv2b.invoiceDate,
                        gstr2bTaxablePaise = inv2b.taxableValuePaise,
                        gstr2bCgstPaise = inv2b.cgstPaise,
                        gstr2bSgstPaise = inv2b.sgstPaise,
                        gstr2bIgstPaise = inv2b.igstPaise,
                        gstr2bTotalTaxPaise = inv2b.totalTaxPaise,
                        gstr2bTotalValuePaise = inv2b.invoiceValuePaise,
                        diffTaxablePaise = inv2b.taxableValuePaise,
                        diffTaxPaise = inv2b.totalTaxPaise,
                        remarks = "Invoice filed by supplier in GSTR-1, but not recorded in purchase book."
                    )
                )
            }
        }

        // 2. Any book purchases NOT matched with 2B -> Missing in 2B
        for (b in bookPurchases) {
            if (!matchedBookEntryIds.contains(b.transactionId)) {
                reconciledItems.add(
                    ReconciliationItem(
                        id = "MISS-2B-${b.transactionId}",
                        matchStatus = MatchStatus.MissingIn2B,
                        supplierGstin = b.partyGstin.ifEmpty { "Unregistered / Unknown" },
                        supplierName = b.partyName,
                        invoiceNumber = b.invoiceNumber,
                        invoiceDate = b.invoiceDate,
                        booksTransactionId = b.transactionId,
                        booksTaxablePaise = b.taxableValuePaise,
                        booksCgstPaise = b.cgstPaise,
                        booksSgstPaise = b.sgstPaise,
                        booksIgstPaise = b.igstPaise,
                        booksTotalTaxPaise = b.totalTaxPaise,
                        booksTotalValuePaise = b.totalAmountPaise,
                        diffTaxablePaise = -b.taxableValuePaise,
                        diffTaxPaise = -b.totalTaxPaise,
                        remarks = "Purchase booked locally, but supplier has NOT filed in GSTR-1 (Rule 36(4) Risk)."
                    )
                )
            }
        }

        // 3. Compute Summary Statistics
        val matchedItems = reconciledItems.filter { it.matchStatus == MatchStatus.FullyMatched }
        val mismatchItems = reconciledItems.filter { it.matchStatus == MatchStatus.ValueMismatch }
        val missingInBooksItems = reconciledItems.filter { it.matchStatus == MatchStatus.MissingInBooks }
        val missingIn2bItems = reconciledItems.filter { it.matchStatus == MatchStatus.MissingIn2B }
        val ineligibleItems = reconciledItems.filter { it.matchStatus == MatchStatus.IneligibleITC }

        val summary = ReconciliationSummary(
            totalGstr2bCount = gstr2bList.size,
            totalGstr2bTaxablePaise = gstr2bList.sumOf { it.taxableValuePaise },
            totalGstr2bItcAvailablePaise = gstr2bList.filter { it.itcAvailable }.sumOf { it.totalTaxPaise },
            totalGstr2bItcBlockedPaise = gstr2bList.filter { !it.itcAvailable }.sumOf { it.totalTaxPaise },
            totalBooksPurchaseCount = bookPurchases.size,
            totalBooksTaxablePaise = bookPurchases.sumOf { it.taxableValuePaise },
            totalBooksItcClaimedPaise = bookPurchases.sumOf { it.totalTaxPaise },
            matchedCount = matchedItems.size,
            matchedItcPaise = matchedItems.sumOf { it.gstr2bTotalTaxPaise },
            valueMismatchCount = mismatchItems.size,
            valueMismatchTaxVariancePaise = mismatchItems.sumOf { abs(it.diffTaxPaise) },
            missingInBooksCount = missingInBooksItems.size,
            missingInBooksUnclaimedItcPaise = missingInBooksItems.sumOf { it.gstr2bTotalTaxPaise },
            missingIn2bCount = missingIn2bItems.size,
            missingIn2bAtRiskItcPaise = missingIn2bItems.sumOf { it.booksTotalTaxPaise },
            ineligibleItcCount = ineligibleItems.size,
            ineligibleItcPaise = ineligibleItems.sumOf { it.gstr2bTotalTaxPaise }
        )

        return Pair(reconciledItems, summary)
    }

    // -------------------------------------------------------------
    // GSTR-2B Official JSON & CSV/TSV Parsers
    // -------------------------------------------------------------

    /**
     * Parses official GST Portal GSTR-2B JSON file.
     * JSON Schema standard:
     * { "data": { "docdata": { "b2b": [ { "ctin": "...", "trdnm": "...", "inv": [ ... ] } ] } } }
     */
    fun parseGstr2bJson(jsonContent: String): List<Gstr2bInvoice> {
        val result = mutableListOf<Gstr2bInvoice>()
        try {
            val root = JSONObject(jsonContent)
            val dataObj = if (root.has("data")) root.getJSONObject("data") else root
            val docData = if (dataObj.has("docdata")) dataObj.getJSONObject("docdata") else dataObj

            // 1. Process B2B Regular Inward Supplies
            if (docData.has("b2b")) {
                val b2bArr = docData.getJSONArray("b2b")
                for (i in 0 until b2bArr.length()) {
                    val supplierObj = b2bArr.getJSONObject(i)
                    val ctin = supplierObj.optString("ctin", "")
                    val tradeName = when {
                        supplierObj.optString("trdnm").isNotBlank() -> supplierObj.optString("trdnm")
                        supplierObj.optString("lgnm").isNotBlank() -> supplierObj.optString("lgnm")
                        supplierObj.optString("cname").isNotBlank() -> supplierObj.optString("cname")
                        supplierObj.optString("name").isNotBlank() -> supplierObj.optString("name")
                        else -> "Supplier"
                    }

                    val invArr = supplierObj.optJSONArray("inv") ?: continue
                    for (j in 0 until invArr.length()) {
                        val inv = invArr.getJSONObject(j)
                        val inum = inv.optString("inum", "")
                        val idt = inv.optString("idt", "")
                        val ival = jsonMoneyToPaise(inv, "val")
                        val pos = inv.optString("pos", "")
                        val rchrg = inv.optString("rchrg", "N").equals("Y", ignoreCase = true)
                        val itcavl = inv.optString("itcavl", "Y").equals("Y", ignoreCase = true)
                        val rsn = inv.optString("rsn", "")

                        var taxable = 0L
                        var igst = 0L
                        var cgst = 0L
                        var sgst = 0L
                        var cess = 0L

                        val itemsArr = inv.optJSONArray("items")
                        if (itemsArr != null) {
                            for (k in 0 until itemsArr.length()) {
                                val item = itemsArr.getJSONObject(k)
                                taxable += jsonMoneyToPaise(item, "txval")
                                igst += jsonMoneyToPaise(item, "iamt")
                                cgst += jsonMoneyToPaise(item, "camt")
                                sgst += jsonMoneyToPaise(item, "samt")
                                cess += jsonMoneyToPaise(item, "csamt")
                            }
                        } else {
                            taxable = ival
                        }

                        result.add(
                            Gstr2bInvoice(
                                supplierGstin = ctin,
                                supplierTradeName = tradeName,
                                invoiceNumber = inum,
                                invoiceDate = idt,
                                invoiceType = InvoiceType.RegularB2B,
                                invoiceValuePaise = ival,
                                taxableValuePaise = taxable,
                                cgstPaise = cgst,
                                sgstPaise = sgst,
                                igstPaise = igst,
                                cessPaise = cess,
                                posStateCode = pos,
                                isReverseCharge = rchrg,
                                itcAvailable = itcavl,
                                itcBlockedReason = rsn
                            )
                        )
                    }
                }
            }

            // 2. Process Credit / Debit Notes (CDNR)
            if (docData.has("cdnr")) {
                val cdnrArr = docData.getJSONArray("cdnr")
                for (i in 0 until cdnrArr.length()) {
                    val supplierObj = cdnrArr.getJSONObject(i)
                    val ctin = supplierObj.optString("ctin", "")
                    val tradeName = when {
                        supplierObj.optString("trdnm").isNotBlank() -> supplierObj.optString("trdnm")
                        supplierObj.optString("lgnm").isNotBlank() -> supplierObj.optString("lgnm")
                        supplierObj.optString("cname").isNotBlank() -> supplierObj.optString("cname")
                        supplierObj.optString("name").isNotBlank() -> supplierObj.optString("name")
                        else -> "Supplier"
                    }

                    val ntArr = supplierObj.optJSONArray("nt") ?: continue
                    for (j in 0 until ntArr.length()) {
                        val nt = ntArr.getJSONObject(j)
                        val ntNum = nt.optString("nt_num", "")
                        val ntDt = nt.optString("nt_dt", "")
                        val ival = jsonMoneyToPaise(nt, "val")
                        val itcavl = nt.optString("itcavl", "Y").equals("Y", ignoreCase = true)

                        var taxable = 0L
                        var igst = 0L
                        var cgst = 0L
                        var sgst = 0L

                        val itemsArr = nt.optJSONArray("items")
                        if (itemsArr != null) {
                            for (k in 0 until itemsArr.length()) {
                                val item = itemsArr.getJSONObject(k)
                                taxable += jsonMoneyToPaise(item, "txval")
                                igst += jsonMoneyToPaise(item, "iamt")
                                cgst += jsonMoneyToPaise(item, "camt")
                                sgst += jsonMoneyToPaise(item, "samt")
                            }
                        }

                        result.add(
                            Gstr2bInvoice(
                                supplierGstin = ctin,
                                supplierTradeName = tradeName,
                                invoiceNumber = ntNum,
                                invoiceDate = ntDt,
                                invoiceType = InvoiceType.CreditDebitNote,
                                invoiceValuePaise = ival,
                                taxableValuePaise = taxable,
                                cgstPaise = cgst,
                                sgstPaise = sgst,
                                igstPaise = igst,
                                itcAvailable = itcavl
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return result
    }

    /**
     * Parses standard GSTR-2B CSV / TSV text (exported from government excel utility or portal).
     */
    fun parseGstr2bCsvOrTsv(text: String): List<Gstr2bInvoice> {
        val list = mutableListOf<Gstr2bInvoice>()
        val cleanText = text.trimStart('\uFEFF')
        if (cleanText.isBlank()) return list

        val firstLine = cleanText.lineSequence().firstOrNull { it.isNotBlank() } ?: ""
        val delimiter = if (firstLine.contains("\t") && !firstLine.contains(",")) '\t' else ','
        val records = Gstr1ExcelImportEngine.parseDelimitedRecords(cleanText, delimiter)
        if (records.size <= 1) return list

        val header = records[0].map { it.trim().lowercase(Locale.ENGLISH) }

        val gstinIdx = header.indexOfFirst { it.contains("gstin") || it.contains("ctin") }
        val nameIdx = header.indexOfFirst { it.contains("trade") || it.contains("legal") || it.contains("name") }
        val invIdx = header.indexOfFirst { it.contains("invoice") || it.contains("number") || it.contains("inum") }
        val dateIdx = header.indexOfFirst { it.contains("date") || it.contains("idt") }
        val valIdx = header.indexOfFirst { it.contains("invoice value") || it == "val" }
        val taxableIdx = header.indexOfFirst { it.contains("taxable") || it == "txval" }
        val igstIdx = header.indexOfFirst { it.contains("integrated") || it == "iamt" || it == "igst" }
        val cgstIdx = header.indexOfFirst { it.contains("central") || it == "camt" || it == "cgst" }
        val sgstIdx = header.indexOfFirst { it.contains("state") || it == "samt" || it == "sgst" }
        val itcIdx = header.indexOfFirst { it.contains("itc") || it == "itcavl" }

        for (i in 1 until records.size) {
            val cols = records[i]
            if (cols.size <= 3) continue

            val gstin = if (gstinIdx in cols.indices) cols[gstinIdx].uppercase(Locale.ENGLISH) else ""
            if (!FinancialEngine.isValidGstinFormat(gstin)) continue

            val tradeName = if (nameIdx in cols.indices) cols[nameIdx] else "Supplier"
            val inum = if (invIdx in cols.indices && cols[invIdx].isNotBlank()) cols[invIdx] else continue
            val rawIdt = if (dateIdx in cols.indices) cols[dateIdx].trim() else ""
            val idt = if (rawIdt.isBlank()) "" else FinancialEngine.normalizeDate(rawIdt)

            fun parsePaise(idx: Int): Long {
                if (idx !in cols.indices) return 0L
                return parseSignedPaise(cols[idx])
            }

            val ival = parsePaise(valIdx)
            val taxable = parsePaise(taxableIdx).takeIf { it > 0L } ?: ival
            val igst = parsePaise(igstIdx)
            val cgst = parsePaise(cgstIdx)
            val sgst = parsePaise(sgstIdx)

            val itcAvail = if (itcIdx in cols.indices) {
                !cols[itcIdx].startsWith("N", ignoreCase = true)
            } else true

            list.add(
                Gstr2bInvoice(
                    supplierGstin = gstin,
                    supplierTradeName = tradeName,
                    invoiceNumber = inum,
                    invoiceDate = idt,
                    invoiceValuePaise = ival,
                    taxableValuePaise = taxable,
                    cgstPaise = cgst,
                    sgstPaise = sgst,
                    igstPaise = igst,
                    itcAvailable = itcAvail
                )
            )
        }
        return list
    }

    /**
     * Generates a realistic set of demo GSTR-2B invoices to allow the user to test
     * reconciliation immediately with a mix of Matched, Mismatched, and Missing records.
     */
    fun getSampleGstr2bInvoices(
        purchases: List<Transaction>,
        parties: List<Party>
    ): List<Gstr2bInvoice> {
        val result = mutableListOf<Gstr2bInvoice>()
        val partyMap = parties.associateBy { it.id }

        val actualPurchases = purchases.filter { !it.isVoided && it.type == TransactionType.Purchase }

        // 1. Take up to 3 actual purchases and create matching 2B entries (Fully Matched)
        actualPurchases.take(3).forEachIndexed { idx, p ->
            val party = partyMap[p.partyId]
            val gstin = party?.gstin?.ifEmpty { "27AABCS1429B1Z${idx + 1}" } ?: "27AABCS1429B1Z${idx + 1}"
            val invNum = p.invoiceNumber.ifEmpty { "PUR-2025-${idx + 101}" }

            val taxable = (p.amountPaise * 10000L + 5900L) / 11800L
            val totalTax = p.amountPaise - taxable
            val cgst = totalTax / 2L
            val sgst = totalTax - cgst

            result.add(
                Gstr2bInvoice(
                    supplierGstin = gstin,
                    supplierTradeName = party?.name ?: "Prime Goods Wholesalers",
                    invoiceNumber = invNum,
                    invoiceDate = p.date,
                    invoiceValuePaise = p.amountPaise,
                    taxableValuePaise = taxable,
                    cgstPaise = cgst,
                    sgstPaise = sgst,
                    igstPaise = 0L,
                    itcAvailable = true
                )
            )
        }

        // 2. Take 1 actual purchase and create a Value Mismatch (e.g. ₹1,500 taxable difference)
        if (actualPurchases.size > 3) {
            val p = actualPurchases[3]
            val party = partyMap[p.partyId]
            val gstin = party?.gstin?.ifEmpty { "27BBBCS4321A1Z9" } ?: "27BBBCS4321A1Z9"
            val invNum = p.invoiceNumber.ifEmpty { "PUR-MISMATCH-104" }

            val taxable = ((p.amountPaise * 10000L + 5900L) / 11800L) + 150000L // ₹ 1,500 higher in 2B
            val gst = FinancialEngine.calculateGstBps(taxable, 1800, isInterstate = false)

            result.add(
                Gstr2bInvoice(
                    supplierGstin = gstin,
                    supplierTradeName = party?.name ?: "Apex Logistics & Supplies",
                    invoiceNumber = invNum,
                    invoiceDate = p.date,
                    invoiceValuePaise = gst.totalPaise,
                    taxableValuePaise = taxable,
                    cgstPaise = gst.cgstPaise,
                    sgstPaise = gst.sgstPaise,
                    igstPaise = 0L,
                    itcAvailable = true
                )
            )
        }

        // 3. Add an entry present in 2B but Missing in Books (Unclaimed ITC opportunity)
        result.add(
            Gstr2bInvoice(
                supplierGstin = "27AAACR7654C1Z3",
                supplierTradeName = "Reliance Jio Infocomm Ltd (Telecom)",
                invoiceNumber = "MH/MUM/2026/98214",
                invoiceDate = "05/01/2026",
                invoiceValuePaise = 117900L, // ₹ 1,179.00
                taxableValuePaise = 99900L,  // ₹ 999.00
                cgstPaise = 9000L,
                sgstPaise = 9000L,
                igstPaise = 0L,
                itcAvailable = true
            )
        )

        // 4. Add an entry marked as Ineligible in GSTR-2B (Section 17(5))
        result.add(
            Gstr2bInvoice(
                supplierGstin = "27AABCT2345K1Z8",
                supplierTradeName = "Hotel Taj Residency (Food & Beverage)",
                invoiceNumber = "FB/2025/1104",
                invoiceDate = "12/01/2026",
                invoiceValuePaise = 525000L, // ₹ 5,250.00
                taxableValuePaise = 500000L, // ₹ 5,000.00
                cgstPaise = 12500L,
                sgstPaise = 12500L,
                igstPaise = 0L,
                itcAvailable = false,
                itcBlockedReason = "Blocked Credit: Food, Beverages & Catering under Sec 17(5)(b)"
            )
        )

        return result
    }
}
