package com.example.engine

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import com.example.model.BusinessProfile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

object GstPortalJsonEngine {

    private fun paiseToRupeesDouble(paise: Long): Double {
        return Math.round(paise.toDouble()) / 100.0
    }

    private fun bpsToRateNumber(bps: Int): Double {
        return bps / 100.0
    }

    fun formatDateForGst(dateStr: String): String {
        val parsed = FinancialEngine.parseValidLocalDate(dateStr) ?: return ""
        return String.format(Locale.ENGLISH, "%02d-%02d-%04d", parsed.dayOfMonth, parsed.monthValue, parsed.year)
    }

    fun sanitizeGstInvoiceNumber(inum: String): String {
        val filtered = inum.trim().filter { it.isLetterOrDigit() || it == '-' || it == '/' }
        if (filtered.isBlank()) return "INV-1"
        if (filtered.length <= 16) return filtered
        // Retain prefix, a short deterministic hash of the full string (prevents middle collisions), and unique serial suffix
        val suffixLen = 6.coerceAtMost(filtered.length / 2)
        val rawHash = (filtered.hashCode().toLong() and 0x7FFFFFFFL).toString(36).uppercase(Locale.ENGLISH)
        val hashTag = rawHash.takeLast(3).padStart(3, '0')
        val prefixLen = (16 - suffixLen - 4).coerceAtLeast(1)
        val prefix = filtered.take(prefixLen)
        val suffix = filtered.takeLast(suffixLen)
        return "${prefix}-${hashTag}${suffix}".take(16)
    }

    fun sanitizeGstStateCode(pos: String, fallback: String = "27"): String {
        val digits = pos.trim().filter { it.isDigit() }
        val num = digits.toIntOrNull()
        return if (num != null && num in 1..99) {
            String.format(Locale.ENGLISH, "%02d", num)
        } else {
            val fallbackDigits = fallback.trim().filter { it.isDigit() }
            val fallbackNum = fallbackDigits.toIntOrNull() ?: 27
            String.format(Locale.ENGLISH, "%02d", fallbackNum)
        }
    }

    fun getFilingPeriodCode(
        filter: CmaAndGstEngine.GstPeriodFilter,
        referenceDates: List<String> = emptyList()
    ): String {
        val today = java.time.LocalDate.now()
        val curYear = today.year
        val curMonth = today.monthValue
        return when (filter) {
            CmaAndGstEngine.GstPeriodFilter.CurrentMonth -> String.format(Locale.ENGLISH, "%02d%d", curMonth, curYear)
            CmaAndGstEngine.GstPeriodFilter.PreviousMonth -> {
                val prevM = if (curMonth == 1) 12 else curMonth - 1
                val prevY = if (curMonth == 1) curYear - 1 else curYear
                String.format(Locale.ENGLISH, "%02d%d", prevM, prevY)
            }
            CmaAndGstEngine.GstPeriodFilter.Quarter -> {
                val quarterEndMonth = (((curMonth - 1) / 3) + 1) * 3
                String.format(Locale.ENGLISH, "%02d%d", quarterEndMonth, curYear)
            }
            CmaAndGstEngine.GstPeriodFilter.AllTime -> {
                val latestDate = referenceDates
                    .mapNotNull { FinancialEngine.parseValidLocalDate(it) }
                    .maxOrNull() ?: today
                String.format(Locale.ENGLISH, "%02d%d", latestDate.monthValue, latestDate.year)
            }
        }
    }

    // -------------------------------------------------------------
    // 1. Official GSTR-1 Offline Tool JSON Generator
    // -------------------------------------------------------------
    fun generateGstr1Json(
        profile: BusinessProfile,
        gstr1Data: CmaAndGstEngine.StatutoryGstr1Data,
        filter: CmaAndGstEngine.GstPeriodFilter,
        annualTurnoverPaise: Long? = null
    ): String {
        val gstin = profile.gstin.trim()
        val refDates = buildList {
            gstr1Data.b2bInvoices.forEach { add(it.date) }
            gstr1Data.cdnrNotes.forEach { add(it.noteDate) }
            gstr1Data.exportInvoices.forEach { add(it.date) }
        }
        val fp = getFilingPeriodCode(filter, refDates)
        val curGtPaise = gstr1Data.totalTaxablePaise + gstr1Data.totalTaxPaise
        val totalGtPaise = annualTurnoverPaise ?: curGtPaise

        val root = JSONObject()
        root.put("gstin", gstin)
        root.put("fp", fp)
        root.put("gt", paiseToRupeesDouble(totalGtPaise))
        root.put("cur_gt", paiseToRupeesDouble(curGtPaise))
        root.put("version", "GST3.1.6")

        // Table 4: B2B Invoices (Grouped by Buyer GSTIN)
        val b2bArray = JSONArray()
        val groupedByGstin = gstr1Data.b2bInvoices.groupBy { it.gstin }
        groupedByGstin.forEach { (buyerGstin, invoices) ->
            val b2bEntry = JSONObject()
            b2bEntry.put("ctin", buyerGstin)
            b2bEntry.put("cfs", "Y")

            val invArray = JSONArray()
            invoices.forEach { inv ->
                val invObj = JSONObject()
                invObj.put("inum", sanitizeGstInvoiceNumber(inv.invoiceNumber))
                invObj.put("idt", formatDateForGst(inv.date))
                invObj.put("val", paiseToRupeesDouble(inv.totalPaise))
                invObj.put("pos", sanitizeGstStateCode(inv.posStateCode.ifBlank { profile.homeStateCode }, profile.homeStateCode))
                invObj.put("rchrg", "N")
                invObj.put("inv_typ", "R")

                val itmArray = JSONArray()
                if (inv.rateBreakdown.isNotEmpty()) {
                    inv.rateBreakdown.forEachIndexed { idx, rb ->
                        val itmObj = JSONObject()
                        itmObj.put("num", (idx + 1) * 100 + 1)
                        val itmDet = JSONObject()
                        itmDet.put("rt", bpsToRateNumber(rb.gstRateBps))
                        itmDet.put("txval", paiseToRupeesDouble(rb.taxablePaise))
                        itmDet.put("iamt", paiseToRupeesDouble(rb.igstPaise))
                        itmDet.put("camt", paiseToRupeesDouble(rb.cgstPaise))
                        itmDet.put("samt", paiseToRupeesDouble(rb.sgstPaise))
                        itmDet.put("csamt", 0.0)
                        itmObj.put("itm_det", itmDet)
                        itmArray.put(itmObj)
                    }
                } else {
                    val itmObj = JSONObject()
                    itmObj.put("num", 1)
                    val itmDet = JSONObject()
                    val rawRate = if (inv.taxablePaise > 0) {
                        val totalTax = inv.cgstPaise + inv.sgstPaise + inv.igstPaise
                        (totalTax.toDouble() / inv.taxablePaise.toDouble()) * 100.0
                    } else 0.0
                    val statutorySlabs = listOf(0.0, 0.25, 3.0, 5.0, 12.0, 18.0, 28.0, 40.0)
                    val matchedRate = statutorySlabs.minByOrNull { abs(it - rawRate) } ?: 0.0

                    itmDet.put("rt", matchedRate)
                    itmDet.put("txval", paiseToRupeesDouble(inv.taxablePaise))
                    itmDet.put("iamt", paiseToRupeesDouble(inv.igstPaise))
                    itmDet.put("camt", paiseToRupeesDouble(inv.cgstPaise))
                    itmDet.put("samt", paiseToRupeesDouble(inv.sgstPaise))
                    itmDet.put("csamt", 0.0)
                    itmObj.put("itm_det", itmDet)
                    itmArray.put(itmObj)
                }

                invObj.put("itms", itmArray)
                invArray.put(invObj)
            }
            b2bEntry.put("inv", invArray)
            b2bArray.put(b2bEntry)
        }
        root.put("b2b", b2bArray)

        // Table 6A: Exports (if any)
        if (gstr1Data.exportInvoices.isNotEmpty()) {
            val expArray = JSONArray()
            gstr1Data.exportInvoices.groupBy { it.exportType }.forEach { (expType, expInvoices) ->
                val expGroup = JSONObject()
                expGroup.put("exp_typ", expType)
                val invArr = JSONArray()
                expInvoices.forEach { expInv ->
                    val invObj = JSONObject()
                    invObj.put("inum", sanitizeGstInvoiceNumber(expInv.invoiceNumber))
                    invObj.put("idt", formatDateForGst(expInv.date))
                    invObj.put("val", paiseToRupeesDouble(expInv.totalPaise))
                    val itmsArr = JSONArray()
                    val itmObj = JSONObject()
                    itmObj.put("txval", paiseToRupeesDouble(expInv.taxablePaise))
                    itmObj.put("rt", bpsToRateNumber(expInv.gstRateBps))
                    itmObj.put("iamt", paiseToRupeesDouble(expInv.igstPaise))
                    itmObj.put("csamt", 0.0)
                    itmsArr.put(itmObj)
                    invObj.put("itms", itmsArr)
                    invArr.put(invObj)
                }
                expGroup.put("inv", invArr)
                expArray.put(expGroup)
            }
            root.put("exp", expArray)
        }

        // Table 7: B2C Small Supplies (taxable rate > 0; nil-rated/exempt goes to Table 8 nil)
        val b2csArray = JSONArray()
        gstr1Data.b2csSupplies.filter { it.gstRateBps > 0 }.forEach { b2cs ->
            val b2csObj = JSONObject()
            b2csObj.put("sply_ty", b2cs.supplyType)
            b2csObj.put("pos", sanitizeGstStateCode(b2cs.posStateCode.ifBlank { profile.homeStateCode }, profile.homeStateCode))
            b2csObj.put("typ", "OE")
            b2csObj.put("rt", bpsToRateNumber(b2cs.gstRateBps))
            b2csObj.put("txval", paiseToRupeesDouble(b2cs.taxablePaise))
            b2csObj.put("iamt", paiseToRupeesDouble(b2cs.igstPaise))
            b2csObj.put("camt", paiseToRupeesDouble(b2cs.cgstPaise))
            b2csObj.put("samt", paiseToRupeesDouble(b2cs.sgstPaise))
            b2csObj.put("csamt", 0.0)
            b2csArray.put(b2csObj)
        }
        root.put("b2cs", b2csArray)

        // Table 8: Nil-Rated, Exempted & Non-GST Outward Supplies
        if (gstr1Data.nilExemptPaise > 0L || gstr1Data.nonGstOutwardPaise > 0L) {
            val nilObj = JSONObject()
            val nilInvArray = JSONArray()
            val nilEntry = JSONObject()
            nilEntry.put("sply_ty", "INTRB2C")
            nilEntry.put("nil_amt", paiseToRupeesDouble(gstr1Data.nilExemptPaise))
            nilEntry.put("expt_amt", 0.0)
            nilEntry.put("ngsup_amt", paiseToRupeesDouble(gstr1Data.nonGstOutwardPaise))
            nilInvArray.put(nilEntry)
            nilObj.put("inv", nilInvArray)
            root.put("nil", nilObj)
        }

        // Table 9B: Credit / Debit Notes (Registered - CDNR)
        if (gstr1Data.cdnrNotes.isNotEmpty()) {
            val cdnrArray = JSONArray()
            gstr1Data.cdnrNotes.groupBy { it.partyGstin }.forEach { (partyGstin, notes) ->
                val groupObj = JSONObject()
                groupObj.put("ctin", partyGstin)
                groupObj.put("cfs", "Y")
                val ntArray = JSONArray()
                notes.forEach { note ->
                    val ntObj = JSONObject()
                    ntObj.put("ntty", note.noteType)
                    ntObj.put("nt_num", sanitizeGstInvoiceNumber(note.noteNumber))
                    ntObj.put("nt_dt", formatDateForGst(note.noteDate))
                    ntObj.put("val", paiseToRupeesDouble(note.totalPaise))
                    ntObj.put("pos", sanitizeGstStateCode(note.posStateCode.ifBlank { profile.homeStateCode }, profile.homeStateCode))
                    ntObj.put("rchrg", "N")
                    ntObj.put("inv_typ", "R")
                    val itmArray = JSONArray()
                    val breakdown = note.rateBreakdown.ifEmpty {
                        listOf(
                            CmaAndGstEngine.Gstr1B2bRateItem(
                                gstRate = 0,
                                taxablePaise = note.taxablePaise,
                                cgstPaise = note.cgstPaise,
                                sgstPaise = note.sgstPaise,
                                igstPaise = note.igstPaise,
                                gstRateBps = 0
                            )
                        )
                    }
                    breakdown.forEachIndexed { idx, rb ->
                        val itmObj = JSONObject()
                        itmObj.put("num", (idx + 1) * 100 + 1)
                        val itmDet = JSONObject()
                        itmDet.put("rt", bpsToRateNumber(rb.gstRateBps))
                        itmDet.put("txval", paiseToRupeesDouble(rb.taxablePaise))
                        itmDet.put("iamt", paiseToRupeesDouble(rb.igstPaise))
                        itmDet.put("camt", paiseToRupeesDouble(rb.cgstPaise))
                        itmDet.put("samt", paiseToRupeesDouble(rb.sgstPaise))
                        itmDet.put("csamt", 0.0)
                        itmObj.put("itm_det", itmDet)
                        itmArray.put(itmObj)
                    }
                    ntObj.put("itms", itmArray)
                    ntArray.put(ntObj)
                }
                groupObj.put("nt", ntArray)
                cdnrArray.put(groupObj)
            }
            root.put("cdnr", cdnrArray)
        }

        // Table 12: HSN Summary
        val hsnRoot = JSONObject()
        val hsnDataArray = JSONArray()
        gstr1Data.hsnSummary.forEachIndexed { idx, hsn ->
            val hsnObj = JSONObject()
            hsnObj.put("num", idx + 1)
            hsnObj.put("hsn_sc", hsn.hsnCode)
            hsnObj.put("desc", hsn.description)
            hsnObj.put("uqc", hsn.uqc)
            hsnObj.put("qty", (hsn.totalQuantityMilli.toDouble() / 1000.0).let { (it * 1000.0).roundToLong() / 1000.0 })
            hsnObj.put("rt", bpsToRateNumber(hsn.gstRateBps))
            hsnObj.put("txval", paiseToRupeesDouble(hsn.taxablePaise))
            hsnObj.put("iamt", paiseToRupeesDouble(hsn.igstPaise))
            hsnObj.put("camt", paiseToRupeesDouble(hsn.cgstPaise))
            hsnObj.put("samt", paiseToRupeesDouble(hsn.sgstPaise))
            hsnObj.put("csamt", 0.0)
            hsnDataArray.put(hsnObj)
        }
        hsnRoot.put("data", hsnDataArray)
        root.put("hsn", hsnRoot)

        // Table 13: Document Summary (Invoices + Debit Notes + Credit Notes)
        val docRoot = JSONObject()
        val docDetArray = JSONArray()

        val invDocDet = JSONObject()
        invDocDet.put("doc_num", 1)
        invDocDet.put("doc_typ", "Invoices for outward supply")
        val invDocsArray = JSONArray()
        val invDocItem = JSONObject()
        invDocItem.put("num", 1)
        invDocItem.put("from", if (gstr1Data.docSummary.minInvoiceNumber.isBlank()) "NIL" else sanitizeGstInvoiceNumber(gstr1Data.docSummary.minInvoiceNumber))
        invDocItem.put("to", if (gstr1Data.docSummary.maxInvoiceNumber.isBlank()) "NIL" else sanitizeGstInvoiceNumber(gstr1Data.docSummary.maxInvoiceNumber))
        invDocItem.put("totnum", gstr1Data.docSummary.totalIssued)
        invDocItem.put("canc", gstr1Data.docSummary.cancelledCount)
        invDocItem.put("net_issue", gstr1Data.docSummary.netIssuedCount)
        invDocsArray.put(invDocItem)
        invDocDet.put("docs", invDocsArray)
        docDetArray.put(invDocDet)

        if (gstr1Data.debitNoteCount > 0 || gstr1Data.debitNoteCancelledCount > 0) {
            val dnNums = gstr1Data.debitNoteNumbers.sorted()
            val dnDocDet = JSONObject()
            dnDocDet.put("doc_num", 4)
            dnDocDet.put("doc_typ", "Debit Note")
            val dnDocsArray = JSONArray()
            val dnDocItem = JSONObject()
            dnDocItem.put("num", 1)
            dnDocItem.put("from", sanitizeGstInvoiceNumber(dnNums.firstOrNull() ?: "DN-001"))
            dnDocItem.put("to", sanitizeGstInvoiceNumber(dnNums.lastOrNull() ?: "DN-001"))
            dnDocItem.put("totnum", gstr1Data.debitNoteCount)
            dnDocItem.put("canc", gstr1Data.debitNoteCancelledCount)
            dnDocItem.put("net_issue", (gstr1Data.debitNoteCount - gstr1Data.debitNoteCancelledCount).coerceAtLeast(0))
            dnDocsArray.put(dnDocItem)
            dnDocDet.put("docs", dnDocsArray)
            docDetArray.put(dnDocDet)
        }

        if (gstr1Data.creditNoteCount > 0 || gstr1Data.creditNoteCancelledCount > 0) {
            val cnNums = gstr1Data.creditNoteNumbers.sorted()
            val cnDocDet = JSONObject()
            cnDocDet.put("doc_num", 5)
            cnDocDet.put("doc_typ", "Credit Note")
            val cnDocsArray = JSONArray()
            val cnDocItem = JSONObject()
            cnDocItem.put("num", 1)
            cnDocItem.put("from", sanitizeGstInvoiceNumber(cnNums.firstOrNull() ?: "CN-001"))
            cnDocItem.put("to", sanitizeGstInvoiceNumber(cnNums.lastOrNull() ?: "CN-001"))
            cnDocItem.put("totnum", gstr1Data.creditNoteCount)
            cnDocItem.put("canc", gstr1Data.creditNoteCancelledCount)
            cnDocItem.put("net_issue", (gstr1Data.creditNoteCount - gstr1Data.creditNoteCancelledCount).coerceAtLeast(0))
            cnDocsArray.put(cnDocItem)
            cnDocDet.put("docs", cnDocsArray)
            docDetArray.put(cnDocDet)
        }

        docRoot.put("doc_det", docDetArray)
        root.put("doc_issue", docRoot)

        return root.toString(2)
    }

    // -------------------------------------------------------------
    // 2. Official GSTR-3B Offline Tool JSON Generator (Rule 88A)
    // -------------------------------------------------------------
    fun generateGstr3bJson(
        profile: BusinessProfile,
        gstr3bData: CmaAndGstEngine.StatutoryGstr3bData,
        filter: CmaAndGstEngine.GstPeriodFilter,
        referenceDates: List<String> = emptyList()
    ): String {
        val gstin = profile.gstin.trim()
        val fp = getFilingPeriodCode(filter, referenceDates)
        val m = gstr3bData.setOffMatrix

        val root = JSONObject()
        root.put("gstin", gstin)
        root.put("ret_period", fp)

        // 3.1 Details of Outward Supplies (official GSTR-3B sup_details.osup_det schema)
        val supDetails = JSONObject()
        val osupDet = JSONObject()
        osupDet.put("txval", paiseToRupeesDouble(gstr3bData.outwardTaxablePaise))
        osupDet.put("iamt", paiseToRupeesDouble(gstr3bData.outwardIgstPaise))
        osupDet.put("camt", paiseToRupeesDouble(gstr3bData.outwardCgstPaise))
        osupDet.put("samt", paiseToRupeesDouble(gstr3bData.outwardSgstPaise))
        osupDet.put("csamt", 0.0)
        supDetails.put("osup_det", osupDet)
        root.put("sup_details", supDetails)

        // 4. Eligible ITC
        val itcElg = JSONObject()
        val itcAvlArray = JSONArray()
        val itcAvl = JSONObject()
        itcAvl.put("ty", "OTH")
        itcAvl.put("iamt", paiseToRupeesDouble(gstr3bData.eligibleItcIgstPaise))
        itcAvl.put("camt", paiseToRupeesDouble(gstr3bData.eligibleItcCgstPaise))
        itcAvl.put("samt", paiseToRupeesDouble(gstr3bData.eligibleItcSgstPaise))
        itcAvl.put("csamt", 0.0)
        itcAvlArray.put(itcAvl)
        itcElg.put("itc_avl", itcAvlArray)
        root.put("itc_elg", itcElg)

        // 6.1 Payment of Tax (Rule 88A Set-off Matrix)
        val txPmt = JSONObject()

        // Paid via ITC Breakdown
        val pdItcArray = JSONArray()

        val igstPdItc = JSONObject()
        igstPdItc.put("liab_ldg_id", "IGST")
        igstPdItc.put("iamt", paiseToRupeesDouble(m.igstUsedForIgstPaise))
        igstPdItc.put("camt", paiseToRupeesDouble(m.cgstUsedForIgstPaise))
        igstPdItc.put("samt", paiseToRupeesDouble(m.sgstUsedForIgstPaise))
        igstPdItc.put("csamt", 0.0)
        pdItcArray.put(igstPdItc)

        val cgstPdItc = JSONObject()
        cgstPdItc.put("liab_ldg_id", "CGST")
        cgstPdItc.put("iamt", paiseToRupeesDouble(m.igstUsedForCgstPaise))
        cgstPdItc.put("camt", paiseToRupeesDouble(m.cgstUsedForCgstPaise))
        cgstPdItc.put("samt", 0.0)
        cgstPdItc.put("csamt", 0.0)
        pdItcArray.put(cgstPdItc)

        val sgstPdItc = JSONObject()
        sgstPdItc.put("liab_ldg_id", "SGST")
        sgstPdItc.put("iamt", paiseToRupeesDouble(m.igstUsedForSgstPaise))
        sgstPdItc.put("camt", 0.0)
        sgstPdItc.put("samt", paiseToRupeesDouble(m.sgstUsedForSgstPaise))
        sgstPdItc.put("csamt", 0.0)
        pdItcArray.put(sgstPdItc)

        txPmt.put("pd_itc", pdItcArray)

        // Paid via Cash (Electronic Cash Ledger)
        val pdCashArray = JSONArray()

        val igstCash = JSONObject()
        igstCash.put("liab_ldg_id", "IGST")
        igstCash.put("iamt", paiseToRupeesDouble(m.cashIgstPaise))
        igstCash.put("camt", 0.0)
        igstCash.put("samt", 0.0)
        igstCash.put("csamt", 0.0)
        pdCashArray.put(igstCash)

        val cgstCash = JSONObject()
        cgstCash.put("liab_ldg_id", "CGST")
        cgstCash.put("iamt", 0.0)
        cgstCash.put("camt", paiseToRupeesDouble(m.cashCgstPaise))
        cgstCash.put("samt", 0.0)
        cgstCash.put("csamt", 0.0)
        pdCashArray.put(cgstCash)

        val sgstCash = JSONObject()
        sgstCash.put("liab_ldg_id", "SGST")
        sgstCash.put("iamt", 0.0)
        sgstCash.put("camt", 0.0)
        sgstCash.put("samt", paiseToRupeesDouble(m.cashSgstPaise))
        sgstCash.put("csamt", 0.0)
        pdCashArray.put(sgstCash)

        txPmt.put("pd_cash", pdCashArray)
        root.put("tx_pmt", txPmt)

        return root.toString(2)
    }

    // -------------------------------------------------------------
    // 3. Official CMP-08 Quarterly Composition Statement JSON
    // -------------------------------------------------------------
    fun generateCmp08Json(
        profile: BusinessProfile,
        cmp08Data: CmaAndGstEngine.StatutoryCmp08Data,
        filter: CmaAndGstEngine.GstPeriodFilter,
        referenceDates: List<String> = emptyList()
    ): String {
        val gstin = profile.gstin.trim()
        val fp = getFilingPeriodCode(filter, referenceDates)

        val root = JSONObject()
        root.put("gstin", gstin)
        root.put("rtn_prd", fp)
        root.put("arn", "")

        val cmpDetails = JSONArray()

        // Row 1: Outward supplies (including exempt supplies)
        val row1 = JSONObject()
        row1.put("sec_cd", "1")
        row1.put("txval", paiseToRupeesDouble(cmp08Data.turnoverPaise))
        row1.put("iamt", 0.0)
        row1.put("camt", paiseToRupeesDouble(cmp08Data.centralTaxPaise))
        row1.put("samt", paiseToRupeesDouble(cmp08Data.stateTaxPaise))
        row1.put("csamt", 0.0)
        cmpDetails.put(row1)

        // Row 2: Inward supplies attracting reverse charge (RCM)
        val row2 = JSONObject()
        row2.put("sec_cd", "2")
        row2.put("txval", paiseToRupeesDouble(cmp08Data.rcmTurnoverPaise))
        row2.put("iamt", paiseToRupeesDouble(cmp08Data.rcmIntegratedTaxPaise))
        row2.put("camt", paiseToRupeesDouble(cmp08Data.rcmCentralTaxPaise))
        row2.put("samt", paiseToRupeesDouble(cmp08Data.rcmStateTaxPaise))
        row2.put("csamt", 0.0)
        cmpDetails.put(row2)

        // Row 3: Tax Payable (1 + 2)
        val row3 = JSONObject()
        row3.put("sec_cd", "3")
        row3.put("txval", paiseToRupeesDouble(cmp08Data.turnoverPaise + cmp08Data.rcmTurnoverPaise))
        row3.put("iamt", paiseToRupeesDouble(cmp08Data.rcmIntegratedTaxPaise))
        row3.put("camt", paiseToRupeesDouble(cmp08Data.centralTaxPaise + cmp08Data.rcmCentralTaxPaise))
        row3.put("samt", paiseToRupeesDouble(cmp08Data.stateTaxPaise + cmp08Data.rcmStateTaxPaise))
        row3.put("csamt", 0.0)
        cmpDetails.put(row3)

        // Row 4: Interest Payable (preserve total paise without dropping odd remainder)
        val halfInterest = cmp08Data.interestPaise / 2L
        val row4 = JSONObject()
        row4.put("sec_cd", "4")
        row4.put("txval", 0.0)
        row4.put("iamt", 0.0)
        row4.put("camt", paiseToRupeesDouble(halfInterest))
        row4.put("samt", paiseToRupeesDouble(cmp08Data.interestPaise - halfInterest))
        row4.put("csamt", 0.0)
        cmpDetails.put(row4)

        root.put("cmp_details", cmpDetails)

        return root.toString(2)
    }

    // -------------------------------------------------------------
    // 4. File Saving & FileProvider Share Action
    // -------------------------------------------------------------
    fun shareOrSaveJson(
        context: Context,
        jsonContent: String,
        filename: String,
        returnTitle: String
    ) {
        try {
            val exportDir = File(context.cacheDir, "exports").apply {
                if (!exists()) mkdirs()
                val cutoff = System.currentTimeMillis() - 86_400_000L
                listFiles()?.forEach { old -> if (old.lastModified() < cutoff) old.delete() }
            }
            val safeFilename = ExportEngine.sanitizeFileName(filename)
                .let { if (it.endsWith(".json", ignoreCase = true)) it else "$it.json" }
            val file = File(exportDir, safeFilename)
            FileOutputStream(file).use { out ->
                out.write(jsonContent.toByteArray(Charsets.UTF_8))
                out.flush()
            }

            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                file
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/json"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "$returnTitle Offline GST JSON Draft")
                putExtra(Intent.EXTRA_TEXT, "Attached is the $returnTitle JSON export generated by Pabaru Billing for review.")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            ExportEngine.launchChooserSafely(
                context = context,
                intent = shareIntent,
                chooserTitle = "Save or Share $returnTitle JSON"
            )
            val kb = ((file.length() + 1023L) / 1024L).coerceAtLeast(1L)
            Toast.makeText(
                context,
                "Saved $safeFilename ($kb KB) — review before uploading",
                Toast.LENGTH_LONG
            ).show()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Error saving JSON: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}
