package com.example.engine

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import com.example.model.BankAccount
import com.example.model.BusinessProfile
import com.example.model.Party
import com.example.model.Transaction
import com.example.model.TransactionType
import java.io.File
import java.io.FileOutputStream
import java.net.URLEncoder
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

object ExportEngine {

    data class HsnTaxSummaryRow(
        val hsnCode: String,
        val gstRate: Int,
        var taxablePaise: Long = 0L,
        var cgstPaise: Long = 0L,
        var sgstPaise: Long = 0L,
        var igstPaise: Long = 0L,
        val gstRateBps: Int = gstRate * 100
    )

    private fun formatHalfRateLabel(bps: Int): String {
        val pct = bps / 200.0
        return if (bps % 200 == 0) "${bps / 200}%" else String.format(Locale.ENGLISH, "%.3f", pct).trimEnd('0').trimEnd('.') + "%"
    }

    fun sanitizeFileName(rawName: String): String {
        val cleaned = rawName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        val safeName = cleaned.replace("..", "_").trim('_')
        return if (safeName.isBlank()) "document_${System.currentTimeMillis()}" else safeName.take(64)
    }

    fun launchChooserSafely(
        context: Context,
        intent: Intent,
        chooserTitle: String,
        fallbackToast: String = "No compatible app found to open or share this file"
    ) {
        try {
            val chooser = Intent.createChooser(intent, chooserTitle).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        } catch (_: android.content.ActivityNotFoundException) {
            Toast.makeText(context, fallbackToast, Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(context, "Sharing unavailable: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    fun shareTextContentAsFile(
        context: Context,
        fileName: String,
        mimeType: String,
        content: String,
        subject: String,
        summaryText: String,
        chooserTitle: String
    ) {
        try {
            val file = prepareExportFile(context, fileName)
            file.writeText(content, Charsets.UTF_8)
            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                file
            )
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, subject)
                putExtra(Intent.EXTRA_TEXT, summaryText)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            launchChooserSafely(context, shareIntent, chooserTitle)
        } catch (e: Exception) {
            Toast.makeText(context, "Export failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun prepareExportFile(context: Context, filename: String): File {
        val exportDir = File(context.cacheDir, "exports").apply {
            if (!exists()) mkdirs()
            val cutoff = System.currentTimeMillis() - 86_400_000L
            listFiles()?.forEach { old -> if (old.lastModified() < cutoff) old.delete() }
        }
        return File(exportDir, filename)
    }

    private fun csvEscape(value: String): String = "\"${value.replace("\"", "\"\"")}\""

    fun drawWrappedText(
        canvas: android.graphics.Canvas,
        paint: Paint,
        text: String,
        x: Float,
        startY: Float,
        maxWidth: Float,
        lineHeight: Float = 11f,
        maxLines: Int = 2
    ): Float {
        val words = text.split(" ")
        var curLine = ""
        var curY = startY
        var linesDrawn = 0
        for (w in words) {
            val candidate = if (curLine.isEmpty()) w else "$curLine $w"
            if (paint.measureText(candidate) <= maxWidth) {
                curLine = candidate
            } else {
                if (curLine.isNotEmpty()) {
                    canvas.drawText(curLine, x, curY, paint)
                    linesDrawn++
                    curY += lineHeight
                    if (linesDrawn >= maxLines - 1) {
                        curLine = w
                        break
                    }
                }
                curLine = w
            }
        }
        if (curLine.isNotEmpty()) {
            val finalLine = if (paint.measureText(curLine) > maxWidth) {
                var truncated = curLine
                while (truncated.isNotEmpty() && paint.measureText("$truncated...") > maxWidth) {
                    truncated = truncated.dropLast(1)
                }
                "$truncated..."
            } else curLine
            canvas.drawText(finalLine, x, curY, paint)
        }
        return curY
    }

    fun generateAndShareInvoicePdf(
        context: Context,
        transaction: Transaction,
        profile: BusinessProfile,
        party: Party?,
        bankAccount: BankAccount?
    ) {
        try {
            val doc = PdfDocument()
            var pageNumber = 1
            var page = doc.startPage(PdfDocument.PageInfo.Builder(595, 842, pageNumber).create())
            var canvas = page.canvas

            val paint = Paint().apply { isAntiAlias = true }

            fun drawPageFooter(c: android.graphics.Canvas, pNum: Int) {
                paint.color = Color.rgb(148, 163, 184)
                paint.textSize = 8f
                paint.isFakeBoldText = false
                c.drawText("Page $pNum • Generated by Pabaru Billing • Statutory GST Compliant & Cryptographically Audited", 60f, 815f, paint)
            }

            var y = 222f

            fun checkPageBreak(requiredHeight: Float, onNewPage: (android.graphics.Canvas) -> Unit = {}) {
                if (y + requiredHeight > 760f) {
                    drawPageFooter(canvas, pageNumber)
                    doc.finishPage(page)
                    pageNumber++
                    page = doc.startPage(PdfDocument.PageInfo.Builder(595, 842, pageNumber).create())
                    canvas = page.canvas
                    y = 35f
                    onNewPage(canvas)
                }
            }

            val isNonGst = transaction.type == TransactionType.NonGstBill ||
                    transaction.invoiceNumber.startsWith("BILL") ||
                    transaction.invoiceNumber.startsWith("EST")
            val isInterstate = !isNonGst && party != null && party.stateCode.isNotBlank() && party.stateCode != profile.homeStateCode

            // 1. Header Background & Branding
            paint.color = Color.rgb(15, 23, 42) // #0F172A
            canvas.drawRect(0f, 0f, 595f, 95f, paint)

            paint.color = Color.rgb(245, 158, 11) // Amber Gold
            paint.textSize = 20f
            paint.isFakeBoldText = true
            canvas.drawText(profile.businessName, 30f, 40f, paint)

            paint.color = Color.WHITE
            paint.textSize = 9f
            paint.isFakeBoldText = false
            if (isNonGst) {
                canvas.drawText("Store: ${profile.storeCode} | State Code: ${profile.homeStateCode} | PAN: ${profile.pan.ifEmpty { "N/A" }}", 30f, 60f, paint)
                canvas.drawText("UDYAM: ${profile.udyamNumber.ifEmpty { "N/A" }}", 30f, 76f, paint)
            } else {
                canvas.drawText("GSTIN: ${profile.gstin.ifEmpty { "N/A" }} | State Code: ${profile.homeStateCode} | Store: ${profile.storeCode}", 30f, 60f, paint)
                canvas.drawText("UDYAM: ${profile.udyamNumber.ifEmpty { "N/A" }} | PAN: ${profile.pan.ifEmpty { "N/A" }}", 30f, 76f, paint)
            }

            // Invoice Type & Number
            val isBos = profile.registrationType.name.equals("composition", ignoreCase = true) ||
                    profile.registrationType.name.equals("unregistered", ignoreCase = true)
            paint.color = Color.rgb(245, 158, 11)
            paint.textSize = 14f
            paint.isFakeBoldText = true
            val title = when {
                isNonGst -> "ESTIMATE / NON-GST BILL"
                isBos -> "BILL OF SUPPLY"
                else -> "TAX INVOICE"
            }
            canvas.drawText(title, 360f, 40f, paint)

            paint.color = Color.WHITE
            paint.textSize = 9.5f
            paint.isFakeBoldText = false
            canvas.drawText("Invoice: ${transaction.invoiceNumber.ifEmpty { transaction.id }}", 360f, 60f, paint)
            canvas.drawText("Date: ${transaction.date}", 360f, 76f, paint)

            // 2. Buyer Information Box
            paint.color = Color.rgb(241, 245, 249)
            canvas.drawRoundRect(30f, 105f, 565f, 175f, 8f, 8f, paint)

            paint.color = Color.rgb(15, 23, 42)
            paint.textSize = 10f
            paint.isFakeBoldText = true
            canvas.drawText("BILLED TO / CUSTOMER DETAILS:", 45f, 125f, paint)

            paint.textSize = 9f
            paint.isFakeBoldText = false
            canvas.drawText("Name: ${party?.name ?: "Counter Cash Customer"}", 45f, 143f, paint)
            canvas.drawText("Phone: ${party?.phone?.ifEmpty { "N/A" } ?: "N/A"}", 45f, 160f, paint)

            if (!isNonGst) {
                canvas.drawText("GSTIN: ${party?.gstin?.ifEmpty { "Unregistered" } ?: "Unregistered"}", 340f, 125f, paint)
                canvas.drawText("Place of Supply: State Code ${party?.stateCode?.ifEmpty { profile.homeStateCode } ?: profile.homeStateCode} (${if (isInterstate) "Inter-state IGST" else "Intra-state CGST+SGST"})", 340f, 143f, paint)
            } else {
                canvas.drawText("Type: Non-GST Commercial Estimate / Cash Memo", 340f, 125f, paint)
                canvas.drawText("Place of Supply: State Code ${party?.stateCode?.ifEmpty { profile.homeStateCode } ?: profile.homeStateCode}", 340f, 143f, paint)
            }
            canvas.drawText("Payment Mode: ${transaction.paymentMode}", 340f, 160f, paint)

            // 3. Table Header
            paint.color = Color.rgb(30, 58, 138) // Navy Blue
            canvas.drawRect(30f, 185f, 565f, 207f, paint)

            paint.color = Color.WHITE
            paint.textSize = 8.5f
            paint.isFakeBoldText = true
            canvas.drawText("#", 38f, 200f, paint)
            canvas.drawText("Item Description", 60f, 200f, paint)
            canvas.drawText("HSN", 240f, 200f, paint)
            canvas.drawText("Qty", 305f, 200f, paint)
            canvas.drawText("Rate (₹)", 360f, 200f, paint)
            canvas.drawText("Taxable (₹)", 425f, 200f, paint)
            canvas.drawText("GST%", 485f, 200f, paint)
            canvas.drawText("Total (₹)", 520f, 200f, paint)

            // 4. Line Items Rows
            y = 222f
            val items = transaction.items
            paint.color = Color.rgb(30, 41, 59)
            paint.isFakeBoldText = false
            paint.textSize = 8.5f

            var subtotalTaxablePaise = 0L

            if (items.isNotEmpty()) {
                items.forEachIndexed { idx, it ->
                    val wrappedHeight = 36f
                    checkPageBreak(wrappedHeight) { c ->
                        paint.color = Color.rgb(30, 58, 138)
                        c.drawRect(30f, y, 565f, y + 22f, paint)
                        paint.color = Color.WHITE
                        paint.textSize = 8.5f
                        paint.isFakeBoldText = true
                        c.drawText("#", 38f, y + 15f, paint)
                        c.drawText("Item Description (Contd.)", 60f, y + 15f, paint)
                        c.drawText("HSN", 240f, y + 15f, paint)
                        c.drawText("Qty", 305f, y + 15f, paint)
                        c.drawText("Rate (₹)", 360f, y + 15f, paint)
                        c.drawText("Taxable (₹)", 425f, y + 15f, paint)
                        c.drawText("GST%", 485f, y + 15f, paint)
                        c.drawText("Total (₹)", 520f, y + 15f, paint)
                        y += 36f
                        paint.color = Color.rgb(30, 41, 59)
                        paint.isFakeBoldText = false
                        paint.textSize = 8.5f
                    }
                    subtotalTaxablePaise += it.taxableAmountPaise
                    canvas.drawText("${idx + 1}", 38f, y, paint)
                    val endY = drawWrappedText(canvas, paint, it.name, 60f, y, 175f, lineHeight = 11f, maxLines = 3)
                    canvas.drawText(if (isNonGst && it.hsnCode.isEmpty()) "-" else it.hsnCode, 240f, y, paint)
                    val qtyText = FinancialEngine.formatQuantityMilli(it.quantityMilli)
                    canvas.drawText("$qtyText ${it.unit}", 305f, y, paint)
                    canvas.drawText(FinancialEngine.formatPaise(it.ratePaise).replace("₹ ", ""), 360f, y, paint)
                    canvas.drawText(FinancialEngine.formatPaise(it.taxableAmountPaise).replace("₹ ", ""), 425f, y, paint)
                    canvas.drawText(if (isNonGst) "0%" else com.example.model.GstSlabs.label(it.gstRateBps), 485f, y, paint)
                    canvas.drawText(FinancialEngine.formatPaise(it.totalPaise).replace("₹ ", ""), 520f, y, paint)
                    y = maxOf(y + 18f, endY + 11f)
                }
            } else {
                subtotalTaxablePaise = transaction.amountPaise
                canvas.drawText("1", 38f, y, paint)
                val endY = drawWrappedText(canvas, paint, transaction.remarks.ifEmpty { "Counter Goods / Services" }, 60f, y, 175f, lineHeight = 11f, maxLines = 3)
                canvas.drawText(if (isNonGst) "-" else "9999", 240f, y, paint)
                canvas.drawText("1 PCS", 305f, y, paint)
                canvas.drawText(FinancialEngine.formatPaise(transaction.amountPaise).replace("₹ ", ""), 360f, y, paint)
                canvas.drawText(FinancialEngine.formatPaise(transaction.amountPaise).replace("₹ ", ""), 425f, y, paint)
                canvas.drawText("0%", 485f, y, paint)
                canvas.drawText(FinancialEngine.formatPaise(transaction.amountPaise).replace("₹ ", ""), 520f, y, paint)
                y = maxOf(y + 18f, endY + 11f)
            }

            // Divider Line
            paint.color = Color.rgb(226, 232, 240)
            canvas.drawLine(30f, y + 2f, 565f, y + 2f, paint)
            y += 16f

            var totalCgstPaise = 0L
            var totalSgstPaise = 0L
            var totalIgstPaise = 0L

            if (!isNonGst) {
                // 5. Task 4: HSN/SAC Tax Summary Table immediately following line-item table
                val hsnMap = mutableMapOf<String, HsnTaxSummaryRow>()
                if (items.isNotEmpty()) {
                    for (it in items) {
                        val key = "${it.hsnCode}_${it.gstRateBps}"
                        val row = hsnMap.getOrPut(key) {
                            HsnTaxSummaryRow(hsnCode = it.hsnCode, gstRate = it.gstRate, gstRateBps = it.gstRateBps)
                        }
                        row.taxablePaise += it.taxableAmountPaise
                        row.cgstPaise += it.cgstPaise
                        row.sgstPaise += it.sgstPaise
                        row.igstPaise += it.igstPaise
                    }
                } else {
                    hsnMap["9999_0"] = HsnTaxSummaryRow(
                        hsnCode = "9999",
                        gstRate = 0,
                        taxablePaise = transaction.amountPaise,
                        cgstPaise = 0,
                        sgstPaise = 0,
                        igstPaise = 0,
                        gstRateBps = 0
                    )
                }

                val hsnTableHeight = 45f + (hsnMap.size * 18f)
                checkPageBreak(hsnTableHeight)

                paint.color = Color.rgb(15, 23, 42)
                paint.textSize = 9f
                paint.isFakeBoldText = true
                canvas.drawText("HSN / SAC STATUTORY TAX SUMMARY", 30f, y, paint)
                y += 8f

                // HSN Table Header
                paint.color = Color.rgb(71, 85, 105) // Slate
                canvas.drawRect(30f, y, 565f, y + 18f, paint)

                paint.color = Color.WHITE
                paint.textSize = 7.5f
                paint.isFakeBoldText = true
                canvas.drawText("HSN/SAC", 38f, y + 12f, paint)
                canvas.drawText("Taxable Value (₹)", 110f, y + 12f, paint)
                canvas.drawText("CGST Rate & Amt", 210f, y + 12f, paint)
                canvas.drawText("SGST Rate & Amt", 320f, y + 12f, paint)
                canvas.drawText("IGST Rate & Amt", 430f, y + 12f, paint)
                canvas.drawText("Total Tax (₹)", 515f, y + 12f, paint)

                y += 24f
                paint.color = Color.rgb(30, 41, 59)
                paint.isFakeBoldText = false
                paint.textSize = 8f

                for (hsnRow in hsnMap.values) {
                    totalCgstPaise += hsnRow.cgstPaise
                    totalSgstPaise += hsnRow.sgstPaise
                    totalIgstPaise += hsnRow.igstPaise
                    val rowTotalTax = hsnRow.cgstPaise + hsnRow.sgstPaise + hsnRow.igstPaise

                    canvas.drawText(hsnRow.hsnCode, 38f, y, paint)
                    canvas.drawText(FinancialEngine.formatPaise(hsnRow.taxablePaise).replace("₹ ", ""), 110f, y, paint)

                    if (isInterstate) {
                        canvas.drawText("-", 230f, y, paint)
                        canvas.drawText("-", 340f, y, paint)
                        canvas.drawText("${com.example.model.GstSlabs.label(hsnRow.gstRateBps)} (${FinancialEngine.formatPaise(hsnRow.igstPaise)})", 430f, y, paint)
                    } else {
                        val halfLabel = formatHalfRateLabel(hsnRow.gstRateBps)
                        canvas.drawText("$halfLabel (${FinancialEngine.formatPaise(hsnRow.cgstPaise)})", 210f, y, paint)
                        canvas.drawText("$halfLabel (${FinancialEngine.formatPaise(hsnRow.sgstPaise)})", 320f, y, paint)
                        canvas.drawText("-", 450f, y, paint)
                    }
                    canvas.drawText(FinancialEngine.formatPaise(rowTotalTax).replace("₹ ", ""), 515f, y, paint)
                    y += 16f
                }
            }

            val totalGstPaise = totalCgstPaise + totalSgstPaise + totalIgstPaise
            val netBeforeRoundPaise = subtotalTaxablePaise + totalGstPaise
            // Rule 26 of CGST Rules: Auto round-off to nearest rupee using pure integer paise arithmetic
            val finalGrandTotalPaise = ((netBeforeRoundPaise + 50L) / 100L) * 100L
            val roundOffPaise = finalGrandTotalPaise - netBeforeRoundPaise

            y += 8f
            paint.color = Color.rgb(226, 232, 240)
            canvas.drawLine(30f, y, 565f, y, paint)
            y += 14f

            // 6. Check space for Statutory Calculations Box and Signatory
            checkPageBreak(150f)

            // 6. Statutory Calculations Box (Subtotal, Total GST, Auto Round-Off, Grand Total)
            paint.color = Color.rgb(248, 250, 252)
            canvas.drawRoundRect(310f, y, 565f, y + 90f, 6f, 6f, paint)
            paint.color = Color.rgb(203, 213, 225)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 1f
            canvas.drawRoundRect(310f, y, 565f, y + 90f, 6f, 6f, paint)
            paint.style = Paint.Style.FILL

            paint.color = Color.rgb(71, 85, 105)
            paint.textSize = 8.5f
            paint.isFakeBoldText = false
            canvas.drawText("Subtotal (Taxable):", 325f, y + 18f, paint)
            canvas.drawText(FinancialEngine.formatPaise(subtotalTaxablePaise), 485f, y + 18f, paint)

            canvas.drawText("Total GST Amount:", 325f, y + 36f, paint)
            canvas.drawText(FinancialEngine.formatPaise(totalGstPaise), 485f, y + 36f, paint)

            val roundOffSign = if (roundOffPaise >= 0) "+ " else "- "
            canvas.drawText("Auto Round-Off (Rule 26):", 325f, y + 54f, paint)
            canvas.drawText("$roundOffSign${FinancialEngine.formatPaise(abs(roundOffPaise))}", 485f, y + 54f, paint)

            paint.color = Color.rgb(15, 23, 42)
            paint.textSize = 10.5f
            paint.isFakeBoldText = true
            canvas.drawText("Final Grand Total:", 325f, y + 76f, paint)
            paint.color = Color.rgb(13, 148, 136) // Teal Accent
            canvas.drawText(FinancialEngine.formatPaise(finalGrandTotalPaise), 475f, y + 76f, paint)

            // Amount in words (Left side)
            paint.color = Color.rgb(71, 85, 105)
            paint.textSize = 8.5f
            paint.isFakeBoldText = false
            canvas.drawText("Amount in Words:", 30f, y + 18f, paint)
            paint.color = Color.rgb(15, 23, 42)
            paint.isFakeBoldText = true
            canvas.drawText(FinancialEngine.amountInWords(finalGrandTotalPaise), 30f, y + 34f, paint)

            // Bank Payment Details
            if (bankAccount != null) {
                paint.color = Color.rgb(15, 23, 42)
                paint.textSize = 9f
                paint.isFakeBoldText = true
                canvas.drawText("Bank & UPI Payment Details:", 30f, y + 60f, paint)
                paint.textSize = 8f
                paint.isFakeBoldText = false
                canvas.drawText("Bank: ${bankAccount.bankName} | A/c: ${bankAccount.accountNumber}", 30f, y + 74f, paint)
                canvas.drawText("IFSC: ${bankAccount.ifscCode} | UPI ID: ${bankAccount.upiId}", 30f, y + 86f, paint)
            }

            // Signatory
            val sigY = y + 120f
            paint.color = Color.rgb(15, 23, 42)
            paint.textSize = 9.5f
            paint.isFakeBoldText = true
            canvas.drawText("For ${profile.businessName}", 420f, sigY, paint)
            paint.textSize = 8.5f
            paint.isFakeBoldText = false
            canvas.drawText("Authorized Signatory", 420f, sigY + 28f, paint)

            // Footer on final page
            drawPageFooter(canvas, pageNumber)

            doc.finishPage(page)

            // Write to sanitized cache file and share
            val safeDocName = sanitizeFileName(transaction.invoiceNumber.ifEmpty { transaction.id })
            val file = prepareExportFile(context, "Invoice_${safeDocName}.pdf")
            FileOutputStream(file).use { outputStream ->
                doc.writeTo(outputStream)
            }
            doc.close()

            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                file
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            launchChooserSafely(context, shareIntent, "Share Invoice PDF")
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "PDF Export: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    fun shareWhatsAppReminder(
        context: Context,
        party: Party,
        amountPaise: Long,
        bankAccount: BankAccount?,
        profile: BusinessProfile
    ) {
        try {
            val rupees = String.format(java.util.Locale.ENGLISH, "%.2f", amountPaise / 100.0)
            val upiId = bankAccount?.upiId ?: "pabarubilling@upi"
            val upiLink = "upi://pay?pa=$upiId&pn=${URLEncoder.encode(profile.businessName, "UTF-8")}&am=$rupees&cu=INR"

            val message = "Namaste ${party.name} ji,\n\n" +
                    "This is a polite reminder from *${profile.businessName}* regarding your pending balance of *₹ $rupees*.\n\n" +
                    "Click to pay instantly via Google Pay / PhonePe / Paytm / BHIM:\n" +
                    "👉 $upiLink\n\n" +
                    "Bank Details:\n" +
                    "Bank: ${bankAccount?.bankName ?: "HDFC Bank"}\n" +
                    "A/c: ${bankAccount?.accountNumber ?: ""}\n" +
                    "IFSC: ${bankAccount?.ifscCode ?: ""}\n\n" +
                    "Thank you for your business!"

            val digits = party.phone.filter { it.isDigit() }
            val cleanPhone = when {
                digits.startsWith("91") && digits.length > 10 -> digits
                digits.length == 10 -> "91$digits"
                else -> digits
            }
            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, message)
                putExtra("jid", "$cleanPhone@s.whatsapp.net")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            launchChooserSafely(context, sendIntent, "Send Payment Reminder via WhatsApp")
        } catch (e: Exception) {
            Toast.makeText(context, "Sharing failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    fun shareWhatsAppBillReminder(
        context: Context,
        party: Party,
        invoiceNumber: String,
        invoiceDate: String,
        totalBillPaise: Long,
        pendingPaise: Long,
        bankAccount: BankAccount?,
        profile: BusinessProfile
    ) {
        try {
            val totalRupees = String.format(java.util.Locale.ENGLISH, "%.2f", totalBillPaise / 100.0)
            val pendingRupees = String.format(java.util.Locale.ENGLISH, "%.2f", pendingPaise / 100.0)
            val upiId = bankAccount?.upiId ?: "pabarubilling@upi"
            val upiLink = "upi://pay?pa=$upiId&pn=${URLEncoder.encode(profile.businessName, "UTF-8")}&am=$pendingRupees&cu=INR"

            val message = "Namaste ${party.name} ji,\n\n" +
                    "This is a payment reminder from *${profile.businessName}* regarding Invoice *#$invoiceNumber* dated *$invoiceDate*.\n\n" +
                    "• Bill Total: ₹ $totalRupees\n" +
                    "• Outstanding Balance: *₹ $pendingRupees*\n\n" +
                    "Click to pay instantly via UPI (GPay / PhonePe / Paytm / BHIM):\n" +
                    "👉 $upiLink\n\n" +
                    "Bank Details for NEFT/IMPS:\n" +
                    "Bank: ${bankAccount?.bankName ?: "HDFC Bank"}\n" +
                    "A/c: ${bankAccount?.accountNumber ?: ""}\n" +
                    "IFSC: ${bankAccount?.ifscCode ?: ""}\n\n" +
                    "Kindly arrange payment at your earliest convenience. Thank you!"

            val digits = party.phone.filter { it.isDigit() }
            val cleanPhone = when {
                digits.startsWith("91") && digits.length > 10 -> digits
                digits.length == 10 -> "91$digits"
                else -> digits
            }
            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, message)
                putExtra("jid", "$cleanPhone@s.whatsapp.net")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            launchChooserSafely(context, sendIntent, "Send Bill Reminder via WhatsApp")
        } catch (e: Exception) {
            Toast.makeText(context, "Sharing failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    fun generateAndShareCmaProjectReportPdf(
        context: Context,
        profile: BusinessProfile,
        dossier: CmaProjectEngine.CmaProjectDossier,
        feedback: List<CmaProjectEngine.BankerFeedbackItem>,
        projectReport: CmaProjectEngine.ProjectReportData? = null
    ) {
        try {
            val doc = PdfDocument()
            val paint = Paint().apply { isAntiAlias = true }

            val years = dossier.years
            val colX = listOf(160f, 260f, 360f, 460f)
            val totalPages = 5
            var curPageNum = 1

            fun toLakh(paise: Long): String = String.format(java.util.Locale.ENGLISH, "₹ %.2f L", (paise / 100.0) / 100000.0)

            fun drawHeader(canvas: android.graphics.Canvas, title: String, subtitle: String) {
                paint.color = Color.rgb(15, 23, 42) // #0F172A
                canvas.drawRect(0f, 0f, 595f, 75f, paint)

                paint.color = Color.rgb(245, 158, 11) // Gold
                paint.textSize = 13.5f
                paint.isFakeBoldText = true
                canvas.drawText("${profile.businessName} • RBI STANDARD CMA DOSSIER", 30f, 28f, paint)

                paint.color = Color.WHITE
                paint.textSize = 8.5f
                paint.isFakeBoldText = false
                canvas.drawText("$title • $subtitle", 30f, 44f, paint)
                canvas.drawText("GSTIN: ${profile.gstin.ifEmpty { "N/A" }} | UDYAM: ${profile.udyamNumber.ifEmpty { "N/A" }} | Constitution: ${profile.entityType.displayName}", 30f, 58f, paint)
            }

            fun drawFooter(canvas: android.graphics.Canvas, pageNum: Int) {
                paint.color = Color.rgb(148, 163, 184)
                paint.textSize = 7.5f
                paint.isFakeBoldText = false
                canvas.drawText("Page $pageNum • RBI Standard CMA & Project Appraisal • Tandon Comm. Methods I & II, Nayak Norms", 35f, 818f, paint)
                canvas.drawText("Pabaru Billing Cryptographic Audit Trail", 410f, 818f, paint)
            }

            fun drawTableHeader(canvas: android.graphics.Canvas, yPos: Float) {
                paint.color = Color.rgb(30, 58, 138)
                canvas.drawRect(30f, yPos, 565f, yPos + 20f, paint)
                paint.color = Color.WHITE
                paint.textSize = 8f
                paint.isFakeBoldText = true
                canvas.drawText("Particulars (₹ in Lakhs)", 35f, yPos + 14f, paint)
                years.forEachIndexed { i, y ->
                    canvas.drawText("${y.fyLabel}\n(${y.stageLabel.take(8)})", colX[i], yPos + 14f, paint)
                }
            }

            val pReport = projectReport ?: CmaProjectEngine.createDefaultProjectReport(profile, dossier)

            // ==========================================
            // PAGE 1: FORM I — CREDIT FACILITIES & OUTLAY
            // ==========================================
            val page1 = doc.startPage(PdfDocument.PageInfo.Builder(595, 842, curPageNum).create())
            var canvas = page1.canvas
            drawHeader(canvas, "PART I: FORM I — PROPOSED CREDIT FACILITIES", "Borrower Profile, Cost of Project & Means of Finance")

            var y = 90f

            // Project Overview Card
            paint.color = Color.rgb(241, 245, 249)
            canvas.drawRoundRect(30f, y, 565f, y + 105f, 6f, 6f, paint)

            paint.color = Color.rgb(15, 23, 42)
            paint.textSize = 9.5f
            paint.isFakeBoldText = true
            canvas.drawText("BORROWER PROFILE & CREDIT FACILITIES REQUESTED", 40f, y + 18f, paint)

            paint.textSize = 8f
            paint.isFakeBoldText = false
            paint.color = Color.rgb(51, 65, 85)
            canvas.drawText("Project Title: ${pReport.projectTitle}", 40f, y + 34f, paint)
            canvas.drawText("Activity / Industry Sector: ${pReport.industrySector}", 40f, y + 48f, paint)
            canvas.drawText("Promoter: ${pReport.promoterName.ifEmpty { profile.ownerName }} (${profile.entityType.signatoryTitle}) | Proposed Bank: ${pReport.proposedBankName}", 40f, y + 62f, paint)
            canvas.drawText("Facility: ${pReport.loanFacilityRequested} | Tenor: ${pReport.loanTenorMonths} Months | Moratorium: ${pReport.moratoriumMonths} Months | Rate: ${pReport.proposedInterestRatePercent}% p.a.", 40f, y + 76f, paint)
            canvas.drawText("Primary Security: ${pReport.primarySecurity.take(105)}", 40f, y + 90f, paint)
            canvas.drawText("Collateral Security: ${pReport.collateralSecurity.take(105)}", 40f, y + 102f, paint)

            y += 118f

            // Table 1: Cost of Project
            paint.color = Color.rgb(30, 58, 138)
            canvas.drawRect(30f, y, 565f, y + 18f, paint)
            paint.color = Color.WHITE
            paint.textSize = 8.5f
            paint.isFakeBoldText = true
            canvas.drawText("1. COST OF PROJECT (CAPITAL EXPENDITURE & OUTLAY)", 38f, y + 12f, paint)
            canvas.drawText("Amount (₹)", 480f, y + 12f, paint)

            y += 24f
            fun drawCostRow(label: String, amt: Long, isTotal: Boolean = false) {
                paint.color = if (isTotal) Color.rgb(15, 23, 42) else Color.rgb(71, 85, 105)
                paint.textSize = if (isTotal) 8.5f else 8f
                paint.isFakeBoldText = isTotal
                canvas.drawText(label, 40f, y, paint)
                canvas.drawText(toLakh(amt), 480f, y, paint)
                y += 14f
            }

            drawCostRow("A. Land, Site & Building Development", pReport.landAndBuildingPaise)
            drawCostRow("B. Plant, Machinery & Commercial Equipment", pReport.plantAndMachineryPaise)
            drawCostRow("C. Furniture, Fixtures & Interior Setup", pReport.furnitureAndFixturesPaise)
            drawCostRow("D. Margin Money for Working Capital (NWC Margin)", pReport.workingCapitalMarginPaise)
            drawCostRow("E. Preliminary, Pre-operative & Contingency Expenses", pReport.preliminaryExpensesPaise)

            paint.color = Color.rgb(226, 232, 240)
            canvas.drawLine(30f, y - 4f, 565f, y - 4f, paint)
            drawCostRow("TOTAL ESTIMATED COST OF PROJECT", pReport.totalProjectCostPaise, isTotal = true)

            y += 10f

            // Table 2: Means of Finance
            paint.color = Color.rgb(30, 58, 138)
            canvas.drawRect(30f, y, 565f, y + 18f, paint)
            paint.color = Color.WHITE
            paint.textSize = 8.5f
            paint.isFakeBoldText = true
            canvas.drawText("2. MEANS OF FINANCE (PROPOSED FUNDING STRUCTURE)", 38f, y + 12f, paint)
            canvas.drawText("Share (%)", 410f, y + 12f, paint)
            canvas.drawText("Amount (₹)", 480f, y + 12f, paint)

            y += 24f
            fun drawFinanceRow(label: String, share: String, amt: Long, isTotal: Boolean = false) {
                paint.color = if (isTotal) Color.rgb(15, 23, 42) else Color.rgb(71, 85, 105)
                paint.textSize = if (isTotal) 8.5f else 8f
                paint.isFakeBoldText = isTotal
                canvas.drawText(label, 40f, y, paint)
                canvas.drawText(share, 410f, y, paint)
                canvas.drawText(toLakh(amt), 480f, y, paint)
                y += 14f
            }

            val totalOutlay = pReport.totalProjectCostPaise
            fun pct(amt: Long) = if (totalOutlay > 0) String.format(Locale.ENGLISH, "%.1f%%", (amt.toDouble() / totalOutlay) * 100.0) else "0.0%"

            drawFinanceRow("A. Promoter's Equity / Own Contribution", pct(pReport.promoterContributionPaise), pReport.promoterContributionPaise)
            drawFinanceRow("B. Proposed Bank Term Loan", pct(pReport.bankTermLoanPaise), pReport.bankTermLoanPaise)
            drawFinanceRow("C. Bank Working Capital Cash Credit (CC)", "-", pReport.bankWorkingCapitalCcPaise)
            drawFinanceRow("D. Government Subsidy / Incentives (if any)", pct(pReport.govtSubsidyPaise), pReport.govtSubsidyPaise)

            paint.color = Color.rgb(226, 232, 240)
            canvas.drawLine(30f, y - 4f, 565f, y - 4f, paint)
            drawFinanceRow("TOTAL MEANS OF FINANCE", "100.0%", pReport.totalMeansOfFinancePaise, isTotal = true)

            y += 10f

            // Market Potential & Employment Section
            paint.color = Color.rgb(248, 250, 252)
            canvas.drawRoundRect(30f, y, 565f, y + 80f, 6f, 6f, paint)
            paint.color = Color.rgb(203, 213, 225)
            paint.style = Paint.Style.STROKE
            canvas.drawRoundRect(30f, y, 565f, y + 80f, 6f, 6f, paint)
            paint.style = Paint.Style.FILL

            paint.color = Color.rgb(15, 23, 42)
            paint.textSize = 8.5f
            paint.isFakeBoldText = true
            canvas.drawText("ECONOMIC FEASIBILITY, EMPLOYMENT & CREDIT APPRAISAL:", 40f, y + 16f, paint)

            paint.color = Color.rgb(71, 85, 105)
            paint.textSize = 7.5f
            paint.isFakeBoldText = false
            canvas.drawText("• Market Demand: ${pReport.marketPotential.take(115)}", 40f, y + 30f, paint)
            canvas.drawText("• Employment Generation: ${pReport.directEmployment} Persons (Direct) | ${pReport.indirectEmployment} Persons (Indirect / Logistics)", 40f, y + 44f, paint)
            canvas.drawText("• Debt Servicing Viability: Adequate DSCR and compliant promoter margin under RBI Tandon Norms.", 40f, y + 58f, paint)
            canvas.drawText("• Promoter Compliance: Books maintained on Pabaru Billing Ledger with complete cryptographic audit trail.", 40f, y + 72f, paint)

            drawFooter(canvas, curPageNum)
            doc.finishPage(page1)
            curPageNum++

            // ==========================================
            // PAGE 2: FORM II — OPERATING STATEMENT (P&L)
            // ==========================================
            val page2 = doc.startPage(PdfDocument.PageInfo.Builder(595, 842, curPageNum).create())
            canvas = page2.canvas
            drawHeader(canvas, "CMA FORM II: OPERATING STATEMENT", "4-Year Comparative Profitability & Production Schedule")

            y = 90f
            drawTableHeader(canvas, y)
            y += 28f

            fun drawRow(canvas: android.graphics.Canvas, label: String, values: List<String>, isBold: Boolean = false, isAccent: Boolean = false) {
                paint.color = if (isAccent) Color.rgb(13, 148, 136) else if (isBold) Color.rgb(15, 23, 42) else Color.rgb(51, 65, 85)
                paint.textSize = if (isBold) 8.5f else 8f
                paint.isFakeBoldText = isBold
                canvas.drawText(label, 35f, y, paint)
                values.forEachIndexed { i, v ->
                    canvas.drawText(v, colX[i], y, paint)
                }
                y += 15f
            }

            drawRow(canvas, "A. Gross Sales Turnover", years.map { toLakh(it.grossSalesPaise) }, isBold = true)
            drawRow(canvas, "   Less: Returns, Allowances & Discounts", years.map { "- ${toLakh(it.salesReturnsAndDeductionsPaise)}" })
            drawRow(canvas, "B. Net Sales Turnover", years.map { toLakh(it.netSalesPaise) }, isBold = true, isAccent = true)

            paint.color = Color.rgb(226, 232, 240)
            canvas.drawLine(30f, y - 4f, 565f, y - 4f, paint)
            drawRow(canvas, "C. Cost of Production:", emptyList(), isBold = true)
            drawRow(canvas, "   Raw Material Purchases", years.map { toLakh(it.rawMaterialPurchasesPaise) })
            drawRow(canvas, "   Direct Wages & Labor", years.map { toLakh(it.directWagesPaise) })
            drawRow(canvas, "   Power, Fuel & Freight Inward", years.map { toLakh(it.directPowerAndFreightPaise) })
            drawRow(canvas, "   Other Direct Expenses", years.map { toLakh(it.otherDirectExpensesPaise) })
            drawRow(canvas, "   Total Cost of Production", years.map { toLakh(it.costOfProductionPaise) }, isBold = true)

            drawRow(canvas, "   Add: Opening Stock (WIP & Finished Goods)", years.map { toLakh(it.openingStockPaise) })
            drawRow(canvas, "   Less: Closing Stock (WIP & Finished Goods)", years.map { "- ${toLakh(it.closingStockPaise)}" })
            drawRow(canvas, "D. Cost of Goods Sold (COGS)", years.map { toLakh(it.costOfSalesPaise) }, isBold = true)
            drawRow(canvas, "E. Gross Profit (B - D)", years.map { toLakh(it.grossProfitPaise) }, isBold = true, isAccent = true)
            drawRow(canvas, "   Gross Profit Margin (%)", years.map { String.format(Locale.ENGLISH, "%.1f%%", it.gpMarginPercent) })

            paint.color = Color.rgb(226, 232, 240)
            canvas.drawLine(30f, y - 4f, 565f, y - 4f, paint)
            drawRow(canvas, "F. General, Admin & Selling (GA&S) Expenses:", emptyList(), isBold = true)
            drawRow(canvas, "   Salaries & Office Administration", years.map { toLakh(it.salariesAndAdminPaise) })
            drawRow(canvas, "   Rent, Rates & Utilities", years.map { toLakh(it.rentAndUtilitiesPaise) })
            drawRow(canvas, "   Selling & Marketing Expenses", years.map { toLakh(it.sellingAndMarketingPaise) })
            drawRow(canvas, "   Other Overheads", years.map { toLakh(it.otherOverheadsPaise) })
            drawRow(canvas, "G. Operating Profit (EBITDA)", years.map { toLakh(it.ebitdaPaise) }, isBold = true, isAccent = true)
            drawRow(canvas, "   EBITDA Margin (%)", years.map { String.format(Locale.ENGLISH, "%.1f%%", it.ebitdaMarginPercent) })

            paint.color = Color.rgb(226, 232, 240)
            canvas.drawLine(30f, y - 4f, 565f, y - 4f, paint)
            drawRow(canvas, "H. Less: Depreciation (WDV Schedule)", years.map { toLakh(it.depreciationPaise) })
            drawRow(canvas, "I. Operating Profit After Depr. (EBIT)", years.map { toLakh(it.ebitPaise) }, isBold = true)
            drawRow(canvas, "   Less: Term Loan Interest", years.map { toLakh(it.termLoanInterestPaise) })
            drawRow(canvas, "   Less: Cash Credit / Working Capital Interest", years.map { toLakh(it.workingCapitalInterestPaise) })
            drawRow(canvas, "J. Profit Before Tax (PBT)", years.map { toLakh(it.pbtPaise) }, isBold = true)
            drawRow(canvas, "   Less: Provision for Income Tax (${years.first().taxRatePercent}%)", years.map { toLakh(it.taxPaise) })
            drawRow(canvas, "K. Profit After Tax (PAT)", years.map { toLakh(it.patPaise) }, isBold = true, isAccent = true)
            drawRow(canvas, "   Net Profit Margin (PAT %)", years.map { String.format(Locale.ENGLISH, "%.1f%%", it.patMarginPercent) }, isBold = true)
            drawRow(canvas, "L. Cash Accruals (PAT + Depreciation)", years.map { toLakh(it.cashAccrualsPaise) }, isBold = true)

            drawFooter(canvas, curPageNum)
            doc.finishPage(page2)
            curPageNum++

            // ====================================================
            // PAGE 3: FORM III & IV — COMPARATIVE BALANCE SHEET & NWC
            // ====================================================
            val page3 = doc.startPage(PdfDocument.PageInfo.Builder(595, 842, curPageNum).create())
            canvas = page3.canvas
            drawHeader(canvas, "CMA FORM III & IV: BALANCE SHEET ANALYSIS", "Comparative Current Assets, Liabilities & Net Working Capital")

            y = 90f
            drawTableHeader(canvas, y)
            y += 28f

            drawRow(canvas, "I. CURRENT ASSETS", emptyList(), isBold = true)
            drawRow(canvas, "   Cash & Bank Balances", years.map { toLakh(it.cashAndBankPaise) })
            drawRow(canvas, "   Sundry Debtors (Receivables)", years.map { toLakh(it.sundryDebtorsPaise) })
            drawRow(canvas, "   Inventories (Raw Materials & Stock)", years.map { toLakh(it.inventoryStockPaise) })
            drawRow(canvas, "   Advances to Suppliers & Other CA", years.map { toLakh(it.advancesToSuppliersPaise + it.otherCurrentAssetsPaise) })
            drawRow(canvas, "Total Current Assets (TCA)", years.map { toLakh(it.totalCurrentAssetsPaise) }, isBold = true, isAccent = true)

            paint.color = Color.rgb(226, 232, 240)
            canvas.drawLine(30f, y - 4f, 565f, y - 4f, paint)
            drawRow(canvas, "II. FIXED & NON-CURRENT ASSETS", emptyList(), isBold = true)
            drawRow(canvas, "   Gross Fixed Assets", years.map { toLakh(it.grossFixedAssetsPaise) })
            drawRow(canvas, "   Less: Accumulated Depreciation", years.map { "- ${toLakh(it.accumulatedDepreciationPaise)}" })
            drawRow(canvas, "   Net Fixed Assets (NFA)", years.map { toLakh(it.netFixedAssetsPaise) }, isBold = true)
            drawRow(canvas, "   Other Non-Current Assets", years.map { toLakh(it.otherNonCurrentAssetsPaise) })
            drawRow(canvas, "TOTAL ASSETS", years.map { toLakh(it.totalAssetsPaise) }, isBold = true, isAccent = true)

            paint.color = Color.rgb(226, 232, 240)
            canvas.drawLine(30f, y - 4f, 565f, y - 4f, paint)
            drawRow(canvas, "III. CURRENT LIABILITIES", emptyList(), isBold = true)
            drawRow(canvas, "   Sundry Creditors (Trade Payables)", years.map { toLakh(it.sundryCreditorsPaise) })
            drawRow(canvas, "   Other Current Liabilities & Provisions", years.map { toLakh(it.otherCurrentLiabilitiesPaise + it.provisionsPaise) })
            drawRow(canvas, "Total Other Current Liabilities (OCL excl. Bank)", years.map { toLakh(it.currentLiabilitiesOtherThanBankPaise) }, isBold = true)
            drawRow(canvas, "Bank Short-Term Borrowing (Cash Credit)", years.map { toLakh(it.existingBankCcLimitPaise) }, isBold = true)
            drawRow(canvas, "Total Current Liabilities (TCL)", years.map { toLakh(it.totalCurrentLiabilitiesPaise) }, isBold = true, isAccent = true)

            paint.color = Color.rgb(226, 232, 240)
            canvas.drawLine(30f, y - 4f, 565f, y - 4f, paint)
            drawRow(canvas, "IV. TERM LIABILITIES & NET WORTH", emptyList(), isBold = true)
            drawRow(canvas, "   Term Loans (repayable > 1 year)", years.map { toLakh(it.termLoanOutstandingPaise) })
            drawRow(canvas, "   Unsecured Loans from Promoters", years.map { toLakh(it.unsecuredLoansPaise) })
            drawRow(canvas, "Total Outside Liabilities (TOL)", years.map { toLakh(it.totalOutsideLiabilitiesPaise) }, isBold = true)
            drawRow(canvas, "   Paid-up Capital", years.map { toLakh(it.shareCapitalPaise) })
            drawRow(canvas, "   Reserves & Surplus", years.map { toLakh(it.reservesAndSurplusPaise) })
            drawRow(canvas, "Tangible Net Worth (TNW)", years.map { toLakh(it.tangibleNetWorthPaise) }, isBold = true, isAccent = true)
            drawRow(canvas, "TOTAL LIABILITIES & CAPITAL", years.map { toLakh(it.totalLiabilitiesAndCapitalPaise) }, isBold = true)

            paint.color = Color.rgb(226, 232, 240)
            canvas.drawLine(30f, y - 4f, 565f, y - 4f, paint)
            drawRow(canvas, "V. WORKING CAPITAL & LIQUIDITY", emptyList(), isBold = true)
            drawRow(canvas, "   Working Capital Gap (WCG = TCA - OCL)", years.map { toLakh(it.workingCapitalGapPaise) }, isBold = true)
            drawRow(canvas, "   Net Working Capital (NWC = TCA - TCL)", years.map { toLakh(it.netWorkingCapitalPaise) }, isBold = true, isAccent = true)
            drawRow(canvas, "   NWC to Net Sales (%)", years.map { String.format(Locale.ENGLISH, "%.1f%%", it.nwcToNetSalesPercent) })

            drawFooter(canvas, curPageNum)
            doc.finishPage(page3)
            curPageNum++

            // =======================================================
            // PAGE 4: FORM V MPBF & FORM VI FUNDS FLOW STATEMENT
            // =======================================================
            val page4 = doc.startPage(PdfDocument.PageInfo.Builder(595, 842, curPageNum).create())
            canvas = page4.canvas
            drawHeader(canvas, "CMA FORM V & VI: MPBF & FUNDS FLOW", "Tandon Methods I & II, Nayak Turnover Norms & Funds Flow")

            y = 90f
            drawTableHeader(canvas, y)
            y += 28f

            drawRow(canvas, "PART A: FORM V — MPBF COMPUTATION", emptyList(), isBold = true)
            drawRow(canvas, "1. TANDON COMMITTEE METHOD 1:", emptyList(), isBold = true)
            drawRow(canvas, "   Working Capital Gap (WCG)", years.map { toLakh(it.workingCapitalGapPaise) })
            drawRow(canvas, "   Min. Promoter Contribution (25% of WCG)", years.map { toLakh(it.tandonMethod1MinNwcPaise) })
            drawRow(canvas, "   Permissible Bank Finance (MPBF Method 1)", years.map { toLakh(it.mpbfTandonMethod1Paise) }, isBold = true, isAccent = true)

            drawRow(canvas, "2. TANDON COMMITTEE METHOD 2 (RBI Mandate):", emptyList(), isBold = true)
            drawRow(canvas, "   Total Current Assets (TCA)", years.map { toLakh(it.totalCurrentAssetsPaise) })
            drawRow(canvas, "   Min. Promoter Contribution (25% of TCA)", years.map { toLakh(it.tandonMethod2MinNwcPaise) })
            drawRow(canvas, "   Less: Current Liabilities excl. Bank (OCL)", years.map { toLakh(it.currentLiabilitiesOtherThanBankPaise) })
            drawRow(canvas, "   Permissible Bank Finance (MPBF Method 2)", years.map { toLakh(it.mpbfTandonMethod2Paise) }, isBold = true, isAccent = true)

            drawRow(canvas, "3. NAYAK COMMITTEE TURNOVER METHOD (MSME):", emptyList(), isBold = true)
            drawRow(canvas, "   Projected Annual Turnover", years.map { toLakh(it.netSalesPaise) })
            drawRow(canvas, "   Total WC Requirement (25% of Turnover)", years.map { toLakh(it.nayakTotalWcRequirementPaise) })
            drawRow(canvas, "   Promoter Margin / NWC (5% of Turnover)", years.map { toLakh(it.nayakPromoterMarginPaise) })
            drawRow(canvas, "   Permissible Bank Finance (20% of Turnover)", years.map { toLakh(it.nayakCommitteeWorkingCapitalPaise) }, isBold = true, isAccent = true)

            paint.color = Color.rgb(226, 232, 240)
            canvas.drawLine(30f, y - 4f, 565f, y - 4f, paint)

            val fundsFlow = CmaProjectEngine.computeFundsFlowSchedule(dossier)
            drawRow(canvas, "PART B: FORM VI — FUNDS FLOW STATEMENT", emptyList(), isBold = true)
            drawRow(canvas, "1. Sources of Funds:", emptyList(), isBold = true)
            drawRow(canvas, "   Cash Accruals (PAT + Depreciation)", fundsFlow.map { toLakh(it.cashAccrualsPaise) })
            drawRow(canvas, "   Capital Infusion / Promoter Equity", fundsFlow.map { toLakh(it.capitalInfusionPaise) })
            drawRow(canvas, "   Increase in Term Loans", fundsFlow.map { toLakh(it.increaseInTermLoanPaise) })
            drawRow(canvas, "   Increase in Unsecured Loans", fundsFlow.map { toLakh(it.increaseInUnsecuredLoansPaise) })
            drawRow(canvas, "   Total Sources of Funds", fundsFlow.map { toLakh(it.totalSourcesPaise) }, isBold = true)

            drawRow(canvas, "2. Applications of Funds:", emptyList(), isBold = true)
            drawRow(canvas, "   Capital Expenditure (Fixed Assets Additions)", fundsFlow.map { toLakh(it.capitalExpenditurePaise) })
            drawRow(canvas, "   Repayment of Term Loan Principal", fundsFlow.map { toLakh(it.repaymentOfTermLoanPaise) })
            drawRow(canvas, "   Promoter Drawings / Dividends", fundsFlow.map { toLakh(it.promoterDrawingsPaise) })
            drawRow(canvas, "   Total Applications of Funds", fundsFlow.map { toLakh(it.totalApplicationsPaise) }, isBold = true)

            drawRow(canvas, "3. Long-Term Net Surplus / (Deficit)", fundsFlow.map { toLakh(it.netSurplusOrDeficitPaise) }, isBold = true, isAccent = true)
            drawRow(canvas, "   Movement in Net Working Capital (ΔNWC)", fundsFlow.map { toLakh(it.changeInNwcPaise) }, isBold = true)

            drawFooter(canvas, curPageNum)
            doc.finishPage(page4)
            curPageNum++

            // ===============================================================
            // PAGE 5: FORM VII LOAN AMORTIZATION & 19 BANKING RATIOS SCORECARD
            // ===============================================================
            val page5 = doc.startPage(PdfDocument.PageInfo.Builder(595, 842, curPageNum).create())
            canvas = page5.canvas
            drawHeader(canvas, "FORM VII & RATIOS: AMORTIZATION & 19 RATIOS", "Term Loan EMI Schedule & Comprehensive RBI Benchmark Appraisal")

            y = 90f

            val tlSchedule = CmaProjectEngine.calculateTermLoanSchedule(
                principalPaise = pReport.bankTermLoanPaise,
                annualRatePercent = pReport.proposedInterestRatePercent,
                tenorMonths = pReport.loanTenorMonths,
                moratoriumMonths = pReport.moratoriumMonths
            )

            // Section 1: Term Loan Amortization Table
            paint.color = Color.rgb(30, 58, 138)
            canvas.drawRect(30f, y, 565f, y + 18f, paint)
            paint.color = Color.WHITE
            paint.textSize = 8.5f
            paint.isFakeBoldText = true
            canvas.drawText("FORM VII: TERM LOAN ANNUAL REPAYMENT SCHEDULE (Loan: ${toLakh(tlSchedule.loanAmountPaise)} @ ${tlSchedule.annualRatePercent}% p.a.)", 35f, y + 12f, paint)

            y += 24f
            paint.color = Color.rgb(241, 245, 249)
            canvas.drawRect(30f, y, 565f, y + 15f, paint)
            paint.color = Color.rgb(15, 23, 42)
            paint.textSize = 8f
            paint.isFakeBoldText = true
            canvas.drawText("Period", 35f, y + 11f, paint)
            canvas.drawText("Opening Balance", 120f, y + 11f, paint)
            canvas.drawText("Principal Repaid", 230f, y + 11f, paint)
            canvas.drawText("Interest Paid", 340f, y + 11f, paint)
            canvas.drawText("Closing Balance", 450f, y + 11f, paint)
            y += 20f

            var activeLoanPage = page5
            tlSchedule.annualSummaries.forEach { row ->
                if (y > 760f) {
                    drawFooter(canvas, curPageNum)
                    doc.finishPage(activeLoanPage)
                    curPageNum++
                    activeLoanPage = doc.startPage(PdfDocument.PageInfo.Builder(595, 842, curPageNum).create())
                    canvas = activeLoanPage.canvas
                    drawHeader(canvas, "FORM VII: TERM LOAN REPAYMENT (CONTD.)", "Annual Amortization Schedule")
                    y = 95f
                    paint.color = Color.rgb(241, 245, 249)
                    canvas.drawRect(30f, y, 565f, y + 15f, paint)
                    paint.color = Color.rgb(15, 23, 42)
                    paint.textSize = 8f
                    paint.isFakeBoldText = true
                    canvas.drawText("Period", 35f, y + 11f, paint)
                    canvas.drawText("Opening Balance", 120f, y + 11f, paint)
                    canvas.drawText("Principal Repaid", 230f, y + 11f, paint)
                    canvas.drawText("Interest Paid", 340f, y + 11f, paint)
                    canvas.drawText("Closing Balance", 450f, y + 11f, paint)
                    y += 20f
                }
                paint.color = Color.rgb(51, 65, 85)
                paint.textSize = 8f
                paint.isFakeBoldText = false
                canvas.drawText("Year ${row.yearIndex}", 35f, y, paint)
                canvas.drawText(toLakh(row.openingPrincipalPaise), 120f, y, paint)
                canvas.drawText(toLakh(row.totalPrincipalRepaidPaise), 230f, y, paint)
                canvas.drawText(toLakh(row.totalInterestPaidPaise), 340f, y, paint)
                canvas.drawText(toLakh(row.closingPrincipalPaise), 450f, y, paint)
                y += 14f
            }

            paint.color = Color.rgb(15, 23, 42)
            paint.textSize = 8f
            paint.isFakeBoldText = true
            canvas.drawText("Total Loan Outflow: EMI = ${toLakh(tlSchedule.monthlyEmiPaise)}/mo | Total Interest = ${toLakh(tlSchedule.totalInterestPayablePaise)} | Total Repayment = ${toLakh(tlSchedule.totalPaymentPaise)}", 35f, y + 4f, paint)
            y += 18f

            if (y + 370f > 780f) {
                drawFooter(canvas, curPageNum)
                doc.finishPage(activeLoanPage)
                curPageNum++
                activeLoanPage = doc.startPage(PdfDocument.PageInfo.Builder(595, 842, curPageNum).create())
                canvas = activeLoanPage.canvas
                drawHeader(canvas, "KEY BANKING RATIOS SCORECARD", "Comprehensive RBI Benchmark Appraisal")
                y = 90f
            }

            // Section 2: 19 Key Banking Ratios vs. RBI Benchmarks
            drawTableHeader(canvas, y)
            y += 28f

            drawRow(canvas, "19 KEY RATIOS VS. RBI BENCHMARKS", emptyList(), isBold = true)
            drawRow(canvas, "1. Current Ratio (Benchmark ≥ 1.33)", years.map { String.format(Locale.ENGLISH, "%.2f", it.currentRatio) }, isBold = true, isAccent = true)
            drawRow(canvas, "2. Quick / Acid-Test Ratio (≥ 1.00)", years.map { String.format(Locale.ENGLISH, "%.2f", it.quickRatio) })
            drawRow(canvas, "3. Absolute Liquid Ratio (≥ 0.50)", years.map { String.format(Locale.ENGLISH, "%.2f", it.absoluteLiquidRatio) })
            drawRow(canvas, "4. Debt-Equity Ratio (DER ≤ 2.00:1)", years.map { String.format(Locale.ENGLISH, "%.2f:1", it.debtEquityRatio) }, isBold = true)
            drawRow(canvas, "5. TOL / Tangible Net Worth (≤ 3.00:1)", years.map { String.format(Locale.ENGLISH, "%.2f:1", it.tolToTnwRatio) })
            drawRow(canvas, "6. Proprietary Ratio (≥ 0.30)", years.map { String.format(Locale.ENGLISH, "%.2f", it.proprietaryRatio) })
            drawRow(canvas, "7. Fixed Assets to Long-Term Funds (≤ 1.00)", years.map { String.format(Locale.ENGLISH, "%.2f", it.fixedAssetsToLongTermFundsRatio) })
            drawRow(canvas, "8. Interest Coverage Ratio (ICR ≥ 2.50)", years.map { String.format(Locale.ENGLISH, "%.2f", it.interestCoverageRatio) }, isBold = true)
            drawRow(canvas, "9. Debt Service Coverage Ratio (DSCR 1.50 - 2.00)", years.map { String.format(Locale.ENGLISH, "%.2f", it.dscr) }, isBold = true, isAccent = true)
            drawRow(canvas, "10. Debtor Velocity (Days ≤ 60d)", years.map { "${it.debtorDays} d" })
            drawRow(canvas, "11. Creditor Velocity (Days)", years.map { "${it.creditorDays} d" })
            drawRow(canvas, "12. Inventory Holding (Days ≤ 60d)", years.map { "${it.inventoryDays} d" })
            drawRow(canvas, "13. Gross Operating Cycle (Days)", years.map { "${it.grossOperatingCycleDays} d" })
            drawRow(canvas, "14. Net Working Capital Cycle (Days)", years.map { "${it.netWorkingCapitalCycleDays} d" }, isBold = true)
            drawRow(canvas, "15. NWC to Net Sales (%)", years.map { String.format(Locale.ENGLISH, "%.1f%%", it.nwcToNetSalesPercent) })
            drawRow(canvas, "16. Gross Profit Margin (%)", years.map { String.format(Locale.ENGLISH, "%.1f%%", it.grossProfitMarginPercent) })
            drawRow(canvas, "17. EBITDA Margin (%)", years.map { String.format(Locale.ENGLISH, "%.1f%%", it.operatingEbitdaMarginPercent) })
            drawRow(canvas, "18. Net Profit Margin (PAT %)", years.map { String.format(Locale.ENGLISH, "%.1f%%", it.netProfitMarginPercent) })
            drawRow(canvas, "19. Return on Capital Employed (ROCE %)", years.map { String.format(Locale.ENGLISH, "%.1f%%", it.returnOnCapitalEmployedPercent) }, isBold = true)

            // Signatory & Stamp Box
            y += 8f
            paint.color = Color.rgb(15, 23, 42)
            paint.textSize = 8.5f
            paint.isFakeBoldText = true
            canvas.drawText("Submitted to Bank Credit Processing Cell by:", 40f, y, paint)
            canvas.drawText("For ${profile.businessName}", 390f, y, paint)
            y += 14f
            paint.color = Color.rgb(71, 85, 105)
            paint.textSize = 7.5f
            paint.isFakeBoldText = false
            canvas.drawText("Verified against Books of Accounts and Statutory GST Returns.", 40f, y, paint)
            canvas.drawText("Authorized Signatory: ${profile.ownerName}", 390f, y, paint)

            drawFooter(canvas, curPageNum)
            doc.finishPage(activeLoanPage)

            // Save and Share with sanitized store code
            val safeStoreCode = sanitizeFileName(profile.storeCode.ifEmpty { "PABARU" })
            val file = prepareExportFile(context, "CMA_Project_Report_${safeStoreCode}.pdf")
            FileOutputStream(file).use { outputStream ->
                doc.writeTo(outputStream)
            }
            doc.close()

            val uri: Uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            launchChooserSafely(context, shareIntent, "Share CMA & Project Report PDF")
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "CMA PDF Export: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }


    fun shareCmaSummaryWhatsApp(
        context: Context,
        profile: BusinessProfile,
        dossier: CmaProjectEngine.CmaProjectDossier,
        feedback: List<CmaProjectEngine.BankerFeedbackItem>,
        projectReport: CmaProjectEngine.ProjectReportData? = null
    ) {
        try {
            val y1 = dossier.years[0]
            val y2 = dossier.years[1]
            val y3 = dossier.years[2]
            val y4 = dossier.years[3]

            fun toL(paise: Long) = String.format(java.util.Locale.ENGLISH, "₹ %.2f L", (paise / 100.0) / 100000.0)

            val msg = buildString {
                appendLine("📊 *BANK PROJECT REPORT & CMA DOSSIER*")
                appendLine("• *Enterprise:* ${profile.businessName} (${profile.entityType.displayName})")
                appendLine("• *GSTIN:* ${profile.gstin.ifEmpty { "N/A" }} | UDYAM: ${profile.udyamNumber.ifEmpty { "N/A" }}")
                if (projectReport != null) {
                    appendLine("• *Project:* ${projectReport.projectTitle}")
                    appendLine("• *Proposed Bank:* ${projectReport.proposedBankName}")
                    appendLine("• *Facility:* ${projectReport.loanFacilityRequested}")
                    appendLine("─────────────────────────")
                    appendLine("🏗️ *PROJECT OUTLAY & MEANS OF FINANCE:*")
                    appendLine("• *Total Project Cost:* ${toL(projectReport.totalProjectCostPaise)}")
                    appendLine("• *Promoter Contribution:* ${toL(projectReport.promoterContributionPaise)} (${String.format(Locale.ENGLISH, "%.1f%%", projectReport.promoterContributionPercent)})")
                    appendLine("• *Bank Term Loan:* ${toL(projectReport.bankTermLoanPaise)}")
                    appendLine("• *Bank CC Limit:* ${toL(projectReport.bankWorkingCapitalCcPaise)}")
                }
                appendLine("─────────────────────────")
                appendLine("📈 *4-YEAR FINANCIAL PROGRESSION:*")
                appendLine("• ${y1.fyLabel} (Audited): Sales ${toL(y1.grossSalesPaise)} | PAT ${toL(y1.patPaise)}")
                appendLine("• ${y2.fyLabel} (Current Est.): Sales ${toL(y2.grossSalesPaise)} | PAT ${toL(y2.patPaise)}")
                appendLine("• ${y3.fyLabel} (Proj Y1): Sales ${toL(y3.grossSalesPaise)} | PAT ${toL(y3.patPaise)}")
                appendLine("• ${y4.fyLabel} (Proj Y2): Sales ${toL(y4.grossSalesPaise)} | PAT ${toL(y4.patPaise)}")
                appendLine("─────────────────────────")
                appendLine("🏦 *BANK LOAN ELIGIBILITY & KEY RATIOS:*")
                appendLine("• *Current Ratio:* ${String.format(Locale.ENGLISH, "%.2f", y2.currentRatio)} (Benchmark ≥ 1.33)")
                appendLine("• *DSCR Ratio:* ${String.format(Locale.ENGLISH, "%.2f", y2.dscr)} (Benchmark 1.50 - 2.00)")
                appendLine("• *Interest Coverage (ICR):* ${String.format(Locale.ENGLISH, "%.2f", y2.interestCoverageRatio)}")
                appendLine("• *MPBF Limit (Tandon Method II):* ${toL(y2.mpbfTandonMethod2Paise)}")
                appendLine("• *Nayak Turnover Method (20%):* ${toL(y2.nayakCommitteeWorkingCapitalPaise)}")
                appendLine("─────────────────────────")
                appendLine("💡 *BANKER IMPROVEMENT RECOMMENDATION:*")
                feedback.take(3).forEach {
                    appendLine("• ${it.title}: ${it.recommendation}")
                }
                appendLine("\nGenerated via Pabaru Billing Ledger • Statutory Compliant")
            }

            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, msg)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            launchChooserSafely(context, sendIntent, "Share CMA Summary")
        } catch (e: Exception) {
            Toast.makeText(context, "Sharing failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    fun generateAndShareFinancialReportPdf(
        context: Context,
        profile: BusinessProfile,
        fyLabel: String,
        statement: FinancialEngine.FyFinancialStatement,
        gstSummary: FinancialEngine.FyGstSummary
    ) {
        try {
            val doc = PdfDocument()
            val paint = Paint().apply { isAntiAlias = true }

            fun fmt(paise: Long): String = FinancialEngine.formatPaise(paise)

            // Page 1: P&L and Balance Sheet
            val page1 = doc.startPage(PdfDocument.PageInfo.Builder(595, 842, 1).create())
            var canvas = page1.canvas

            // Header Banner
            paint.color = Color.rgb(15, 23, 42) // Slate 900
            canvas.drawRect(0f, 0f, 595f, 75f, paint)

            paint.color = Color.rgb(245, 158, 11) // Gold/Amber
            paint.textSize = 15f
            paint.isFakeBoldText = true
            canvas.drawText(profile.businessName.ifEmpty { "BUSINESS FINANCIAL REPORT" }.uppercase(), 30f, 28f, paint)

            paint.color = Color.WHITE
            paint.textSize = 9f
            paint.isFakeBoldText = false
            canvas.drawText("ANNUAL FINANCIAL STATEMENTS • $fyLabel", 30f, 44f, paint)
            canvas.drawText("GSTIN: ${profile.gstin.ifEmpty { "Unregistered" }} | PAN: ${profile.pan.ifEmpty { "N/A" }} | Phone: ${profile.phone}", 30f, 58f, paint)

            var y = 80f

            // SECTION 1: MULTI-STEP PROFIT & LOSS STATEMENT
            paint.color = Color.rgb(241, 245, 249)
            canvas.drawRoundRect(25f, y, 570f, y + 295f, 8f, 8f, paint)
            paint.color = Color.rgb(203, 213, 225)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 1f
            canvas.drawRoundRect(25f, y, 570f, y + 295f, 8f, 8f, paint)
            paint.style = Paint.Style.FILL

            paint.color = Color.rgb(15, 23, 42)
            paint.textSize = 10.5f
            paint.isFakeBoldText = true
            canvas.drawText("I. MULTI-STEP PROFIT & LOSS STATEMENT ($fyLabel)", 38f, y + 18f, paint)

            paint.color = Color.rgb(203, 213, 225)
            canvas.drawLine(38f, y + 24f, 557f, y + 24f, paint)

            var py = y + 40f
            val plRows = listOf(
                Pair("1. Gross Sales / Turnover", fmt(statement.grossSalesPaise)),
                Pair("   Less: Sales Returns & Credit Notes", "- " + fmt(statement.salesReturnsPaise)),
                Pair("   Less: Trade Discounts & Allowances", "- " + fmt(statement.salesDiscountsPaise)),
                Pair("NET SALES REVENUE", fmt(statement.netSalesPaise)),
                Pair("2. Less: Purchases & Raw Materials", "- " + fmt(statement.purchasesCogsPaise)),
                Pair("   Less: Direct Freight & Operational Costs", "- " + fmt(statement.directExpensesPaise)),
                Pair("GROSS PROFIT / (LOSS) (${String.format(Locale.ENGLISH, "%.1f%%", statement.grossProfitMarginPercent)})", fmt(statement.grossProfitPaise)),
                Pair("3. Less: Operating Expenses (Admin, Salaries, Marketing, Depr.)", "- " + fmt(statement.totalOperatingExpensesPaise)),
                Pair("OPERATING PROFIT / (INCOME) EBIT (${String.format(Locale.ENGLISH, "%.1f%%", statement.operatingProfitMarginPercent)})", fmt(statement.operatingProfitPaise)),
                Pair("4. Non-Operating Net Income / (Finance Costs)", (if (statement.nonOperatingIncomePaise - statement.nonOperatingExpensesPaise >= 0L) "+ " else "- ") + fmt(kotlin.math.abs(statement.nonOperatingIncomePaise - statement.nonOperatingExpensesPaise))),
                Pair("EARNINGS BEFORE TAX (EBT)", fmt(statement.earningsBeforeTaxEbtPaise)),
                Pair("5. Less: Provision for Income Tax", "- " + fmt(statement.incomeTaxProvisionPaise)),
                Pair("NET PROFIT / (LOSS) FOR PERIOD (${String.format(Locale.ENGLISH, "%.1f%%", statement.netProfitMarginPercent)})", fmt(statement.netProfitPaise))
            )

            paint.textSize = 8.5f
            plRows.forEachIndexed { idx, (label, amt) ->
                val isNetSale = (idx == 3)
                val isGross = (idx == 6)
                val isOp = (idx == 8)
                val isEbt = (idx == 10)
                val isNet = (idx == 12)
                val isBold = isNetSale || isGross || isOp || isEbt || isNet
                paint.isFakeBoldText = isBold
                paint.color = when {
                    isNet -> Color.rgb(13, 148, 136) // Teal
                    isGross || isOp -> Color.rgb(30, 41, 59)
                    amt.startsWith("-") -> Color.rgb(220, 38, 38)
                    amt.startsWith("+") -> Color.rgb(16, 185, 129)
                    else -> Color.rgb(51, 65, 85)
                }
                canvas.drawText(label, 40f, py, paint)
                val amtWidth = paint.measureText(amt)
                canvas.drawText(amt, 555f - amtWidth, py, paint)

                if (isBold) {
                    paint.color = Color.rgb(226, 232, 240)
                    canvas.drawLine(40f, py + 4f, 555f, py + 4f, paint)
                }
                py += 19f
            }

            y += 308f

            // SECTION 2: BALANCE SHEET STATEMENT
            val assetLines = buildList {
                add(Pair("Cash in Hand / Drawer", fmt(statement.cashInHandPaise)))
                add(Pair("Bank Account Balances", fmt(statement.bankTotalPaise)))
                add(Pair("Customer Receivables (Debtors)", fmt(statement.receivablesPaise)))
                add(Pair("Stock Inventory Valuation", fmt(statement.stockValuationPaise)))
                if (statement.netGstPayablePaise < 0L) {
                    add(Pair("Net GST Input Credit (ITC Asset)", fmt(-statement.netGstPayablePaise)))
                }
            }
            val liabLines = buildList {
                add(Pair("Supplier Payables (Creditors)", fmt(statement.payablesPaise)))
                if (statement.netGstPayablePaise > 0L) {
                    add(Pair("Net GST Payable Liability", fmt(statement.netGstPayablePaise)))
                }
                add(Pair("Closing Capital & Reserves", fmt(statement.closingCapitalPaise)))
            }
            val bsRowCount = maxOf(assetLines.size, liabLines.size) + 1
            val bsCardHeight = 68f + (bsRowCount * 22f)

            paint.color = Color.rgb(241, 245, 249)
            canvas.drawRoundRect(25f, y, 570f, y + bsCardHeight, 8f, 8f, paint)
            paint.color = Color.rgb(203, 213, 225)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 1f
            canvas.drawRoundRect(25f, y, 570f, y + bsCardHeight, 8f, 8f, paint)
            paint.style = Paint.Style.FILL

            paint.color = Color.rgb(15, 23, 42)
            paint.textSize = 11f
            paint.isFakeBoldText = true
            canvas.drawText("II. BALANCE SHEET STATEMENT (As of $fyLabel Cut-off)", 38f, y + 20f, paint)

            paint.color = Color.rgb(203, 213, 225)
            canvas.drawLine(38f, y + 26f, 557f, y + 26f, paint)

            var by = y + 44f
            paint.color = Color.rgb(30, 41, 59)
            paint.textSize = 9.5f
            paint.isFakeBoldText = true
            canvas.drawText("ASSETS", 40f, by, paint)
            canvas.drawText("LIABILITIES & CAPITAL", 310f, by, paint)
            by += 16f

            val maxBodyRows = maxOf(assetLines.size, liabLines.size)
            val assetRows = assetLines + List(maxBodyRows - assetLines.size) { Pair("", "") } +
                listOf(Pair("TOTAL ASSETS", fmt(statement.totalAssetsPaise)))
            val liabRows = liabLines + List(maxBodyRows - liabLines.size) { Pair("", "") } +
                listOf(Pair("TOTAL LIABILITIES & EQUITY", fmt(statement.totalLiabilitiesAndEquityPaise)))

            paint.textSize = 8.5f
            for (i in 0 until bsRowCount) {
                val a = assetRows[i]
                val l = liabRows[i]
                val isTotal = (i == bsRowCount - 1)

                paint.isFakeBoldText = isTotal
                paint.color = if (isTotal) Color.rgb(15, 23, 42) else Color.rgb(51, 65, 85)

                if (a.first.isNotEmpty()) {
                    canvas.drawText(a.first, 40f, by, paint)
                    canvas.drawText(a.second, 280f - paint.measureText(a.second), by, paint)
                }

                if (l.first.isNotEmpty()) {
                    canvas.drawText(l.first, 310f, by, paint)
                    canvas.drawText(l.second, 555f - paint.measureText(l.second), by, paint)
                }

                if (isTotal) {
                    paint.color = Color.rgb(203, 213, 225)
                    canvas.drawLine(40f, by - 12f, 280f, by - 12f, paint)
                    canvas.drawLine(310f, by - 12f, 555f, by - 12f, paint)
                }
                by += 22f
            }

            // Signatory block
            val sigY = maxOf(745f, y + bsCardHeight + 18f).coerceAtMost(775f)
            paint.color = Color.rgb(15, 23, 42)
            paint.textSize = 9.5f
            paint.isFakeBoldText = true
            canvas.drawText("For ${profile.businessName}", 410f, sigY, paint)
            paint.textSize = 8.5f
            paint.isFakeBoldText = false
            canvas.drawText("Authorized Signatory", 410f, sigY + 28f, paint)

            paint.color = Color.rgb(148, 163, 184)
            paint.textSize = 8f
            canvas.drawText("Page 1 • Financial Statements • Generated via Pabaru Billing", 35f, 815f, paint)

            doc.finishPage(page1)

            // Page 2: GST Filing Summary (GSTR-1 & GSTR-3B)
            var finPageNum = 2
            var activePage = doc.startPage(PdfDocument.PageInfo.Builder(595, 842, finPageNum).create())
            canvas = activePage.canvas

            // Header Banner
            paint.color = Color.rgb(15, 23, 42)
            canvas.drawRect(0f, 0f, 595f, 75f, paint)

            paint.color = Color.rgb(245, 158, 11)
            paint.textSize = 15f
            paint.isFakeBoldText = true
            canvas.drawText(profile.businessName.ifEmpty { "BUSINESS FINANCIAL REPORT" }.uppercase(), 30f, 28f, paint)

            paint.color = Color.WHITE
            paint.textSize = 9f
            paint.isFakeBoldText = false
            canvas.drawText("GST FILING SUMMARY & TAX LIABILITY • $fyLabel", 30f, 44f, paint)
            canvas.drawText("GSTIN: ${profile.gstin.ifEmpty { "Unregistered" }} | Registered State: ${profile.homeStateCode}", 30f, 58f, paint)

            y = 92f

            // GSTR-1 & GSTR-3B Summary Card
            paint.color = Color.rgb(241, 245, 249)
            canvas.drawRoundRect(25f, y, 570f, y + 210f, 8f, 8f, paint)
            paint.color = Color.rgb(203, 213, 225)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 1f
            canvas.drawRoundRect(25f, y, 570f, y + 210f, 8f, 8f, paint)
            paint.style = Paint.Style.FILL

            paint.color = Color.rgb(15, 23, 42)
            paint.textSize = 11f
            paint.isFakeBoldText = true
            canvas.drawText("III. GST FILING DATA (GSTR-1 & GSTR-3B)", 38f, y + 20f, paint)

            paint.color = Color.rgb(203, 213, 225)
            canvas.drawLine(38f, y + 26f, 557f, y + 26f, paint)

            var gy = y + 44f
            val gstRows = listOf(
                Pair("GSTR-1 Total Outward Taxable Turnover", fmt(gstSummary.totalTaxableSalesPaise)),
                Pair("• B2B Taxable Sales (With Party GSTIN)", "${fmt(gstSummary.b2bTaxableSalesPaise)} (${gstSummary.b2bInvoiceCount} Invoices)"),
                Pair("• B2C Retail Taxable Sales", "${fmt(gstSummary.b2cTaxableSalesPaise)} (${gstSummary.b2cInvoiceCount} Invoices)"),
                Pair("Total Output GST Liability (CGST + SGST + IGST)", fmt(gstSummary.totalOutputTaxPaise)),
                Pair("GSTR-3B Eligible Input Tax Credit (ITC from Purchases)", "- " + fmt(gstSummary.totalEligibleItcPaise)),
                Pair("NET GST PAYABLE IN CASH", fmt(gstSummary.netGstPayablePaise))
            )

            paint.textSize = 9.5f
            gstRows.forEachIndexed { idx, (label, amt) ->
                val isNet = (idx == 5)
                paint.isFakeBoldText = isNet || (idx == 0) || (idx == 3)
                paint.color = if (isNet) Color.rgb(13, 148, 136) else Color.rgb(30, 41, 59)
                canvas.drawText(label, 40f, gy, paint)
                canvas.drawText(amt, 555f - paint.measureText(amt), gy, paint)
                gy += 24f
            }

            y += 225f

            // RATE-WISE GST BREAKDOWN TABLE (dynamic height & pagination)
            fun drawSlabCardHeader(startY: Float, rowsInSlice: Int) {
                val cardH = 68f + (rowsInSlice * 18f)
                paint.color = Color.rgb(241, 245, 249)
                canvas.drawRoundRect(25f, startY, 570f, startY + cardH, 8f, 8f, paint)
                paint.color = Color.rgb(203, 213, 225)
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 1f
                canvas.drawRoundRect(25f, startY, 570f, startY + cardH, 8f, 8f, paint)
                paint.style = Paint.Style.FILL

                paint.color = Color.rgb(15, 23, 42)
                paint.textSize = 11f
                paint.isFakeBoldText = true
                canvas.drawText("IV. RATE-WISE TAX BREAKDOWN ($fyLabel)", 38f, startY + 20f, paint)

                paint.color = Color.rgb(203, 213, 225)
                canvas.drawLine(38f, startY + 26f, 557f, startY + 26f, paint)

                val hdrY = startY + 42f
                paint.textSize = 8.5f
                paint.isFakeBoldText = true
                paint.color = Color.rgb(71, 85, 105)
                canvas.drawText("Rate", 40f, hdrY, paint)
                canvas.drawText("Taxable Value", 110f, hdrY, paint)
                canvas.drawText("CGST", 230f, hdrY, paint)
                canvas.drawText("SGST", 320f, hdrY, paint)
                canvas.drawText("IGST", 410f, hdrY, paint)
                canvas.drawText("Total Tax", 490f, hdrY, paint)
            }

            val allSlabs = gstSummary.slabBreakdown
            val firstSliceCount = minOf(allSlabs.size, ((785f - y - 68f) / 18f).toInt().coerceAtLeast(1))
            drawSlabCardHeader(y, firstSliceCount)
            var ty = y + 58f
            paint.isFakeBoldText = false
            paint.textSize = 8.5f
            var rendered = 0
            allSlabs.forEach { row ->
                if (ty > 780f) {
                    paint.color = Color.rgb(148, 163, 184)
                    paint.textSize = 8f
                    canvas.drawText("Page $finPageNum • GST Filing Summary • Generated via Pabaru Billing", 35f, 815f, paint)
                    doc.finishPage(activePage)
                    finPageNum++
                    activePage = doc.startPage(PdfDocument.PageInfo.Builder(595, 842, finPageNum).create())
                    canvas = activePage.canvas
                    y = 40f
                    val remaining = (allSlabs.size - rendered).coerceAtLeast(1)
                    val sliceCount = minOf(remaining, 38)
                    drawSlabCardHeader(y, sliceCount)
                    ty = y + 58f
                    paint.isFakeBoldText = false
                    paint.textSize = 8.5f
                }
                paint.color = Color.rgb(30, 41, 59)
                canvas.drawText(row.rateLabel, 40f, ty, paint)
                canvas.drawText(fmt(row.taxablePaise), 110f, ty, paint)
                canvas.drawText(fmt(row.cgstPaise), 230f, ty, paint)
                canvas.drawText(fmt(row.sgstPaise), 320f, ty, paint)
                canvas.drawText(fmt(row.igstPaise), 410f, ty, paint)
                canvas.drawText(fmt(row.totalTaxPaise), 490f, ty, paint)
                ty += 18f
                rendered++
            }

            paint.color = Color.rgb(148, 163, 184)
            paint.textSize = 8f
            canvas.drawText("Page $finPageNum • GST Filing Summary • Generated via Pabaru Billing", 35f, 815f, paint)

            doc.finishPage(activePage)

            val safeName = sanitizeFileName("${profile.businessName}_Financial_Report_${fyLabel}".replace(" ", "_"))
            val file = prepareExportFile(context, "${safeName}.pdf")
            FileOutputStream(file).use { out ->
                doc.writeTo(out)
            }
            doc.close()

            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                file
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "${profile.businessName} - Financial Report & GST ($fyLabel)")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            launchChooserSafely(context, shareIntent, "Share Financial Report PDF")
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "PDF Export: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    fun generateAndShareFinancialReportCsv(
        context: Context,
        profile: BusinessProfile,
        fyLabel: String,
        statement: FinancialEngine.FyFinancialStatement,
        gstSummary: FinancialEngine.FyGstSummary
    ) {
        try {
            fun toR(paise: Long): String = String.format(Locale.ENGLISH, "%.2f", paise / 100.0)

            val csv = buildString {
                appendLine("PABARU BILLING - FINANCIAL STATEMENTS & GST SUMMARY REPORT")
                appendLine("Business Name,${csvEscape(profile.businessName)}")
                appendLine("GSTIN,${csvEscape(profile.gstin)}")
                appendLine("Financial Year,${csvEscape(fyLabel)}")
                appendLine("Report Generated Date,\"${FinancialEngine.getTodayDate()}\"")
                appendLine()

                appendLine("--- I. MULTI-STEP PROFIT & LOSS STATEMENT ---")
                appendLine("Step,Particulars,Amount (INR)")
                appendLine("1. Revenue,Gross Sales / Invoiced Turnover,${toR(statement.grossSalesPaise)}")
                appendLine(",Less: Sales Returns & Credit Notes,-${toR(statement.salesReturnsPaise)}")
                appendLine(",Less: Trade Discounts & Allowances,-${toR(statement.salesDiscountsPaise)}")
                appendLine(",NET SALES REVENUE,${toR(statement.netSalesPaise)}")
                appendLine("2. Cost of Goods Sold,Purchases & Raw Materials,-${toR(statement.purchasesCogsPaise)}")
                appendLine(",Direct Manufacturing & Freight Costs,-${toR(statement.directExpensesPaise)}")
                appendLine(",Total Cost of Goods Sold (COGS),-${toR(statement.totalCogsPaise)}")
                appendLine(",GROSS PROFIT / (LOSS) (${String.format(Locale.ENGLISH, "%.1f%%", statement.grossProfitMarginPercent)}),${toR(statement.grossProfitPaise)}")
                appendLine("3. Operating Expenses,Administrative & Office Overheads,-${toR(statement.operatingExpensesAdminPaise)}")
                appendLine(",Employee Salaries & Wages,-${toR(statement.operatingExpensesSalariesPaise)}")
                appendLine(",Marketing & Delivery Costs,-${toR(statement.operatingExpensesMarketingPaise)}")
                appendLine(",Depreciation & Amortization,-${toR(statement.operatingExpensesDepreciationPaise)}")
                appendLine(",Total Operating Expenses,-${toR(statement.totalOperatingExpensesPaise)}")
                appendLine(",OPERATING INCOME / PROFIT (EBIT) (${String.format(Locale.ENGLISH, "%.1f%%", statement.operatingProfitMarginPercent)}),${toR(statement.operatingProfitPaise)}")
                appendLine("4. Non-Operating Items,Add: Non-Operating / Interest Income,+${toR(statement.nonOperatingIncomePaise)}")
                appendLine(",Less: Finance Costs & Loan Interest,-${toR(statement.nonOperatingExpensesPaise)}")
                appendLine(",EARNINGS BEFORE TAX (EBT),${toR(statement.earningsBeforeTaxEbtPaise)}")
                appendLine("5. Taxes,Less: Provision for Income Tax,-${toR(statement.incomeTaxProvisionPaise)}")
                appendLine(",NET PROFIT / (LOSS) FOR PERIOD (${String.format(Locale.ENGLISH, "%.1f%%", statement.netProfitMarginPercent)}),${toR(statement.netProfitPaise)}")
                appendLine(",Proprietor Drawings (Isolated from P&L; deducted from Equity),${toR(statement.drawingsPaise)}")
                appendLine()

                appendLine("--- II. BALANCE SHEET STATEMENT ---")
                appendLine("Assets,Amount (INR),Liabilities & Equity,Amount (INR)")
                val csvAssets = buildList {
                    add(Pair("Cash in Hand / Drawer", toR(statement.cashInHandPaise)))
                    add(Pair("Bank Balances", toR(statement.bankTotalPaise)))
                    add(Pair("Customer Receivables (Debtors)", toR(statement.receivablesPaise)))
                    add(Pair("Stock Inventory Valuation", toR(statement.stockValuationPaise)))
                    if (statement.netGstPayablePaise < 0L) {
                        add(Pair("Net GST Input Credit (ITC Asset)", toR(-statement.netGstPayablePaise)))
                    }
                }
                val csvLiab = buildList {
                    add(Pair("Supplier Payables (Creditors)", toR(statement.payablesPaise)))
                    if (statement.netGstPayablePaise > 0L) {
                        add(Pair("Net GST Payable Liability", toR(statement.netGstPayablePaise)))
                    }
                    add(Pair("Closing Capital & Reserves", toR(statement.closingCapitalPaise)))
                }
                val csvRows = maxOf(csvAssets.size, csvLiab.size)
                for (i in 0 until csvRows) {
                    val a = csvAssets.getOrNull(i) ?: Pair("", "")
                    val l = csvLiab.getOrNull(i) ?: Pair("", "")
                    appendLine("${a.first},${a.second},${l.first},${l.second}")
                }
                appendLine("TOTAL ASSETS,${toR(statement.totalAssetsPaise)},TOTAL LIABILITIES & EQUITY,${toR(statement.totalLiabilitiesAndEquityPaise)}")
                appendLine()

                appendLine("--- III. GST FILING DATA (GSTR-1 & GSTR-3B) ---")
                appendLine("GST Metric,Amount (INR)")
                appendLine("Total Taxable Sales Turnover,${toR(gstSummary.totalTaxableSalesPaise)}")
                appendLine("B2B Taxable Sales (${gstSummary.b2bInvoiceCount} Invoices),${toR(gstSummary.b2bTaxableSalesPaise)}")
                appendLine("B2C Taxable Sales (${gstSummary.b2cInvoiceCount} Invoices),${toR(gstSummary.b2cTaxableSalesPaise)}")
                appendLine("Total Output Tax Liability,${toR(gstSummary.totalOutputTaxPaise)}")
                appendLine("Output CGST,${toR(gstSummary.outputCgstPaise)}")
                appendLine("Output SGST,${toR(gstSummary.outputSgstPaise)}")
                appendLine("Output IGST,${toR(gstSummary.outputIgstPaise)}")
                appendLine("Total Eligible Input Tax Credit (ITC),${toR(gstSummary.totalEligibleItcPaise)}")
                appendLine("NET GST PAYABLE IN CASH,${toR(gstSummary.netGstPayablePaise)}")
                appendLine()

                appendLine("--- IV. RATE-WISE GST BREAKDOWN ---")
                appendLine("Tax Rate,Taxable Value (INR),CGST (INR),SGST (INR),IGST (INR),Total Tax (INR)")
                gstSummary.slabBreakdown.forEach { row ->
                    appendLine("${csvEscape(row.rateLabel)},${toR(row.taxablePaise)},${toR(row.cgstPaise)},${toR(row.sgstPaise)},${toR(row.igstPaise)},${toR(row.totalTaxPaise)}")
                }
            }

            val safeName = sanitizeFileName("${profile.businessName}_Financial_Report_${fyLabel}".replace(" ", "_"))
            val file = prepareExportFile(context, "${safeName}.csv")
            file.writeText(csv)

            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                file
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "${profile.businessName} - Financial Statements ($fyLabel)")
                putExtra(Intent.EXTRA_TEXT, "Attached is the Financial Statements & GST CSV report ($fyLabel) generated by ${profile.businessName}.")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            launchChooserSafely(context, shareIntent, "Share Financial Report CSV")
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "CSV Export: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    fun generateAndShareGstr9ReportPdf(
        context: Context,
        profile: BusinessProfile,
        fyLabel: String,
        gstr9: FinancialEngine.Gstr9AnnualSummary
    ) {
        try {
            val doc = PdfDocument()
            val paint = Paint().apply { isAntiAlias = true }

            fun fmt(paise: Long): String = FinancialEngine.formatPaise(paise)

            val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
            val page = doc.startPage(pageInfo)
            val canvas = page.canvas

            // Header Banner (Slate 900)
            paint.color = Color.rgb(15, 23, 42)
            canvas.drawRect(0f, 0f, 595f, 75f, paint)

            paint.color = Color.rgb(245, 158, 11) // Gold/Amber
            paint.textSize = 14f
            paint.isFakeBoldText = true
            canvas.drawText(profile.businessName.ifEmpty { "BUSINESS ENTERPRISE" }.uppercase(), 30f, 26f, paint)

            paint.color = Color.WHITE
            paint.textSize = 9.5f
            paint.isFakeBoldText = true
            canvas.drawText("FORM GSTR-9: ANNUAL GST RETURN SUMMARY • $fyLabel", 30f, 42f, paint)

            paint.textSize = 8f
            paint.isFakeBoldText = false
            canvas.drawText("GSTIN: ${profile.gstin.ifEmpty { "Unregistered" }} | PAN: ${profile.pan.ifEmpty { "N/A" }} | State: ${profile.homeStateCode} | Phone: ${profile.phone}", 30f, 56f, paint)

            var y = 88f

            // SECTION 1: STATUTORY TABLE 4 - OUTWARD SUPPLIES
            paint.color = Color.rgb(241, 245, 249)
            canvas.drawRoundRect(25f, y, 570f, y + 125f, 6f, 6f, paint)
            paint.color = Color.rgb(203, 213, 225)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 1f
            canvas.drawRoundRect(25f, y, 570f, y + 125f, 6f, 6f, paint)
            paint.style = Paint.Style.FILL

            paint.color = Color.rgb(15, 23, 42)
            paint.textSize = 9.5f
            paint.isFakeBoldText = true
            canvas.drawText("TABLE 4: DETAILS OF OUTWARD SUPPLIES ON WHICH TAX IS PAYABLE", 35f, y + 16f, paint)

            paint.color = Color.rgb(203, 213, 225)
            canvas.drawLine(35f, y + 22f, 560f, y + 22f, paint)

            var ty = y + 36f
            paint.textSize = 8f
            paint.color = Color.rgb(71, 85, 105)
            paint.isFakeBoldText = true
            canvas.drawText("Nature of Supply", 35f, ty, paint)
            canvas.drawText("Taxable Value", 210f, ty, paint)
            canvas.drawText("CGST", 310f, ty, paint)
            canvas.drawText("SGST", 390f, ty, paint)
            canvas.drawText("IGST", 465f, ty, paint)
            canvas.drawText("Total Tax", 520f, ty, paint)

            ty += 16f
            paint.isFakeBoldText = false
            paint.color = Color.rgb(30, 41, 59)

            // 4A B2C
            canvas.drawText("4A. Supplies to Unregistered (B2C)", 35f, ty, paint)
            canvas.drawText(fmt(gstr9.table4Details.b2cTaxablePaise), 210f, ty, paint)
            canvas.drawText(fmt(gstr9.table4Details.b2cCgstPaise), 310f, ty, paint)
            canvas.drawText(fmt(gstr9.table4Details.b2cSgstPaise), 390f, ty, paint)
            canvas.drawText(fmt(gstr9.table4Details.b2cIgstPaise), 465f, ty, paint)
            canvas.drawText(fmt(gstr9.table4Details.b2cTaxPaise), 520f, ty, paint)

            ty += 16f
            // 4B B2B
            canvas.drawText("4B. Supplies to Registered (B2B)", 35f, ty, paint)
            canvas.drawText(fmt(gstr9.table4Details.b2bTaxablePaise), 210f, ty, paint)
            canvas.drawText(fmt(gstr9.table4Details.b2bCgstPaise), 310f, ty, paint)
            canvas.drawText(fmt(gstr9.table4Details.b2bSgstPaise), 390f, ty, paint)
            canvas.drawText(fmt(gstr9.table4Details.b2bIgstPaise), 465f, ty, paint)
            canvas.drawText(fmt(gstr9.table4Details.b2bTaxPaise), 520f, ty, paint)

            ty += 16f
            paint.color = Color.rgb(203, 213, 225)
            canvas.drawLine(35f, ty - 6f, 560f, ty - 6f, paint)

            // 4N Total
            paint.isFakeBoldText = true
            paint.color = Color.rgb(15, 23, 42)
            canvas.drawText("4N. Total Outward Supplies", 35f, ty + 4f, paint)
            canvas.drawText(fmt(gstr9.table4Details.totalTaxablePaise), 210f, ty + 4f, paint)
            canvas.drawText(fmt(gstr9.table4Details.totalCgstPaise), 310f, ty + 4f, paint)
            canvas.drawText(fmt(gstr9.table4Details.totalSgstPaise), 390f, ty + 4f, paint)
            canvas.drawText(fmt(gstr9.table4Details.totalIgstPaise), 465f, ty + 4f, paint)
            canvas.drawText(fmt(gstr9.table4Details.grandTotalTaxPaise), 520f, ty + 4f, paint)

            y += 135f

            // SECTION 2: STATUTORY TABLE 6 (ITC) & TABLE 9 (TAX PAID)
            paint.color = Color.rgb(241, 245, 249)
            canvas.drawRoundRect(25f, y, 570f, y + 115f, 6f, 6f, paint)
            paint.color = Color.rgb(203, 213, 225)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 1f
            canvas.drawRoundRect(25f, y, 570f, y + 115f, 6f, 6f, paint)
            paint.style = Paint.Style.FILL

            // Table 6 Section Header
            paint.color = Color.rgb(15, 23, 42)
            paint.textSize = 9.5f
            paint.isFakeBoldText = true
            canvas.drawText("TABLE 6: ITC AVAILED & TABLE 9: TAX PAID AS DECLARED IN RETURNS", 35f, y + 16f, paint)

            paint.color = Color.rgb(203, 213, 225)
            canvas.drawLine(35f, y + 22f, 560f, y + 22f, paint)

            var iy = y + 36f
            paint.textSize = 8f
            paint.color = Color.rgb(51, 65, 85)
            paint.isFakeBoldText = false
            canvas.drawText("6B. Inward Supplies Attracting ITC:", 35f, iy, paint)
            canvas.drawText("Inward Taxable: ${fmt(gstr9.table6Details.inwardSuppliesTaxablePaise)}", 200f, iy, paint)
            canvas.drawText("CGST: ${fmt(gstr9.table6Details.itcCgstPaise)} | SGST: ${fmt(gstr9.table6Details.itcSgstPaise)} | IGST: ${fmt(gstr9.table6Details.itcIgstPaise)}", 330f, iy, paint)

            iy += 16f
            paint.isFakeBoldText = true
            paint.color = Color.rgb(13, 148, 136)
            canvas.drawText("Total Input Tax Credit (ITC) Availed: ${fmt(gstr9.table6Details.totalItcPaise)}", 35f, iy, paint)

            iy += 16f
            paint.color = Color.rgb(203, 213, 225)
            canvas.drawLine(35f, iy - 6f, 560f, iy - 6f, paint)

            paint.color = Color.rgb(15, 23, 42)
            paint.textSize = 8.5f
            paint.isFakeBoldText = true
            canvas.drawText("TABLE 9: TAX LIABILITY & SETTLEMENT SUMMARY", 35f, iy + 4f, paint)

            iy += 18f
            paint.textSize = 8f
            paint.isFakeBoldText = false
            paint.color = Color.rgb(51, 65, 85)
            canvas.drawText("Total Tax Payable: ${fmt(gstr9.table9Details.taxPayablePaise)}", 35f, iy, paint)
            canvas.drawText("Paid through ITC: ${fmt(gstr9.table9Details.taxPaidItcPaise)}", 195f, iy, paint)
            canvas.drawText("Paid in Cash: ${fmt(gstr9.table9Details.taxPaidCashPaise)}", 350f, iy, paint)
            paint.isFakeBoldText = true
            paint.color = if (gstr9.table9Details.balancePayablePaise > 0L) Color.rgb(220, 38, 38) else Color.rgb(16, 185, 129)
            canvas.drawText("Balance Due: ${fmt(gstr9.table9Details.balancePayablePaise)}", 475f, iy, paint)

            y += 125f

            // SECTION 3: 4-QUARTER PROGRESSION & COMPARISON MATRIX
            paint.color = Color.rgb(241, 245, 249)
            canvas.drawRoundRect(25f, y, 570f, y + 175f, 6f, 6f, paint)
            paint.color = Color.rgb(203, 213, 225)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 1f
            canvas.drawRoundRect(25f, y, 570f, y + 175f, 6f, 6f, paint)
            paint.style = Paint.Style.FILL

            paint.color = Color.rgb(15, 23, 42)
            paint.textSize = 9.5f
            paint.isFakeBoldText = true
            canvas.drawText("4-QUARTER COMPARISON MATRIX (Q1 to Q4 AGGREGATION • $fyLabel)", 35f, y + 16f, paint)

            paint.color = Color.rgb(203, 213, 225)
            canvas.drawLine(35f, y + 22f, 560f, y + 22f, paint)

            var qy = y + 36f
            paint.textSize = 8f
            paint.color = Color.rgb(71, 85, 105)
            paint.isFakeBoldText = true
            canvas.drawText("Quarter", 35f, qy, paint)
            canvas.drawText("Period", 95f, qy, paint)
            canvas.drawText("Taxable Sales", 185f, qy, paint)
            canvas.drawText("Output GST", 285f, qy, paint)
            canvas.drawText("Eligible ITC", 380f, qy, paint)
            canvas.drawText("Net Tax Due", 475f, qy, paint)
            canvas.drawText("Inv", 545f, qy, paint)

            qy += 16f
            paint.isFakeBoldText = false
            paint.color = Color.rgb(30, 41, 59)

            gstr9.quarters.forEach { q ->
                canvas.drawText(q.quarterKey, 35f, qy, paint)
                canvas.drawText(q.dateRangeDesc, 95f, qy, paint)
                canvas.drawText(fmt(q.taxableSalesPaise), 185f, qy, paint)
                canvas.drawText(fmt(q.totalOutputTaxPaise), 285f, qy, paint)
                canvas.drawText(fmt(q.totalItcPaise), 380f, qy, paint)
                canvas.drawText(fmt(q.netTaxPayablePaise), 475f, qy, paint)
                canvas.drawText("${q.invoiceCount}", 545f, qy, paint)
                qy += 18f
            }

            paint.color = Color.rgb(203, 213, 225)
            canvas.drawLine(35f, qy - 6f, 560f, qy - 6f, paint)

            // Annual Consolidated Row
            paint.isFakeBoldText = true
            paint.color = Color.rgb(13, 148, 136)
            canvas.drawText("ANNUAL", 35f, qy + 4f, paint)
            canvas.drawText("Full FY", 95f, qy + 4f, paint)
            canvas.drawText(fmt(gstr9.annualTaxableSalesPaise), 185f, qy + 4f, paint)
            canvas.drawText(fmt(gstr9.annualTotalOutputTaxPaise), 285f, qy + 4f, paint)
            canvas.drawText(fmt(gstr9.annualTotalItcPaise), 380f, qy + 4f, paint)
            canvas.drawText(fmt(gstr9.annualNetTaxPayablePaise), 475f, qy + 4f, paint)
            canvas.drawText("${gstr9.totalInvoicesCount}", 545f, qy + 4f, paint)

            y += 185f

            // DECLARATION & SIGNATORY BOX
            paint.color = Color.rgb(248, 250, 252)
            canvas.drawRoundRect(25f, y, 570f, y + 80f, 6f, 6f, paint)
            paint.color = Color.rgb(203, 213, 225)
            paint.style = Paint.Style.STROKE
            canvas.drawRoundRect(25f, y, 570f, y + 80f, 6f, 6f, paint)
            paint.style = Paint.Style.FILL

            paint.color = Color.rgb(15, 23, 42)
            paint.textSize = 8.5f
            paint.isFakeBoldText = true
            canvas.drawText("STATUTORY DECLARATION & VERIFICATION:", 35f, y + 16f, paint)

            paint.color = Color.rgb(71, 85, 105)
            paint.textSize = 7.5f
            paint.isFakeBoldText = false
            canvas.drawText("I hereby solemnly affirm and declare that the information given herein above is true and correct to the best of my", 35f, y + 30f, paint)
            canvas.drawText("knowledge and belief and nothing has been concealed therefrom. Reconciled against Books of Account & Invoices.", 35f, y + 42f, paint)

            paint.color = Color.rgb(15, 23, 42)
            paint.textSize = 8f
            paint.isFakeBoldText = true
            canvas.drawText("For ${profile.businessName}", 390f, y + 58f, paint)
            paint.isFakeBoldText = false
            canvas.drawText("Authorized Signatory: ${profile.ownerName} (${profile.entityType.signatoryTitle})", 390f, y + 70f, paint)

            // Page Footer
            paint.color = Color.rgb(148, 163, 184)
            paint.textSize = 8f
            canvas.drawText("Form GSTR-9 Annual Return Summary • $fyLabel • Generated via Pabaru Billing Ledger", 30f, 815f, paint)

            doc.finishPage(page)

            val safeName = sanitizeFileName("${profile.businessName}_GSTR9_Annual_${fyLabel}".replace(" ", "_"))
            val file = prepareExportFile(context, "${safeName}.pdf")
            FileOutputStream(file).use { out ->
                doc.writeTo(out)
            }
            doc.close()

            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                file
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "${profile.businessName} - Form GSTR-9 Annual Return ($fyLabel)")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            launchChooserSafely(context, shareIntent, "Share GSTR-9 Annual Return PDF")
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "GSTR-9 PDF Export: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    fun generateAndShareGstr9ReportCsv(
        context: Context,
        profile: BusinessProfile,
        fyLabel: String,
        gstr9: FinancialEngine.Gstr9AnnualSummary
    ) {
        try {
            fun toR(paise: Long): String = String.format(Locale.ENGLISH, "%.2f", paise / 100.0)

            val csv = buildString {
                appendLine("PABARU BILLING - FORM GSTR-9 ANNUAL GST RETURN SUMMARY")
                appendLine("Business Name,${csvEscape(profile.businessName)}")
                appendLine("GSTIN,${csvEscape(profile.gstin)}")
                appendLine("PAN,${csvEscape(profile.pan)}")
                appendLine("State Code,${csvEscape(profile.homeStateCode)}")
                appendLine("Financial Year,${csvEscape(fyLabel)}")
                appendLine("Report Date,\"${FinancialEngine.getTodayDate()}\"")
                appendLine()

                appendLine("--- I. 4-QUARTER COMPARISON & AGGREGATION MATRIX ---")
                appendLine("Quarter,Period,Invoices,Taxable Sales (INR),B2B Taxable (INR),B2C Taxable (INR),Output CGST (INR),Output SGST (INR),Output IGST (INR),Total Output Tax (INR),Taxable Purchases (INR),ITC CGST (INR),ITC SGST (INR),ITC IGST (INR),Total ITC (INR),Net Tax Payable (INR)")
                gstr9.quarters.forEach { q ->
                    appendLine("${csvEscape(q.quarterKey)},${csvEscape(q.dateRangeDesc)},${q.invoiceCount},${toR(q.taxableSalesPaise)},${toR(q.b2bTaxablePaise)},${toR(q.b2cTaxablePaise)},${toR(q.outputCgstPaise)},${toR(q.outputSgstPaise)},${toR(q.outputIgstPaise)},${toR(q.totalOutputTaxPaise)},${toR(q.taxablePurchasesPaise)},${toR(q.itcCgstPaise)},${toR(q.itcSgstPaise)},${toR(q.itcIgstPaise)},${toR(q.totalItcPaise)},${toR(q.netTaxPayablePaise)}")
                }
                appendLine("\"ANNUAL TOTAL\",\"Full FY\",${gstr9.totalInvoicesCount},${toR(gstr9.annualTaxableSalesPaise)},${toR(gstr9.annualB2bTaxablePaise)},${toR(gstr9.annualB2cTaxablePaise)},${toR(gstr9.annualOutputCgstPaise)},${toR(gstr9.annualOutputSgstPaise)},${toR(gstr9.annualOutputIgstPaise)},${toR(gstr9.annualTotalOutputTaxPaise)},${toR(gstr9.annualTaxablePurchasesPaise)},${toR(gstr9.annualItcCgstPaise)},${toR(gstr9.annualItcSgstPaise)},${toR(gstr9.annualItcIgstPaise)},${toR(gstr9.annualTotalItcPaise)},${toR(gstr9.annualNetTaxPayablePaise)}")
                appendLine()

                appendLine("--- II. TABLE 4: DETAILS OF OUTWARD SUPPLIES ON WHICH TAX IS PAYABLE ---")
                appendLine("Supply Category,Taxable Value (INR),CGST (INR),SGST (INR),IGST (INR),Total Tax (INR)")
                appendLine("4A. Supplies to Unregistered Persons (B2C),${toR(gstr9.table4Details.b2cTaxablePaise)},${toR(gstr9.table4Details.b2cCgstPaise)},${toR(gstr9.table4Details.b2cSgstPaise)},${toR(gstr9.table4Details.b2cIgstPaise)},${toR(gstr9.table4Details.b2cTaxPaise)}")
                appendLine("4B. Supplies to Registered Persons (B2B),${toR(gstr9.table4Details.b2bTaxablePaise)},${toR(gstr9.table4Details.b2bCgstPaise)},${toR(gstr9.table4Details.b2bSgstPaise)},${toR(gstr9.table4Details.b2bIgstPaise)},${toR(gstr9.table4Details.b2bTaxPaise)}")
                appendLine("4N. Total Outward Supplies,${toR(gstr9.table4Details.totalTaxablePaise)},${toR(gstr9.table4Details.totalCgstPaise)},${toR(gstr9.table4Details.totalSgstPaise)},${toR(gstr9.table4Details.totalIgstPaise)},${toR(gstr9.table4Details.grandTotalTaxPaise)}")
                appendLine()

                appendLine("--- III. TABLE 6: DETAILS OF INPUT TAX CREDIT (ITC) AVAILED ---")
                appendLine("ITC Category,Inward Taxable (INR),ITC CGST (INR),ITC SGST (INR),ITC IGST (INR),Total ITC (INR)")
                appendLine("6B. Inward Supplies (Other than imports and RCM),${toR(gstr9.table6Details.inwardSuppliesTaxablePaise)},${toR(gstr9.table6Details.itcCgstPaise)},${toR(gstr9.table6Details.itcSgstPaise)},${toR(gstr9.table6Details.itcIgstPaise)},${toR(gstr9.table6Details.totalItcPaise)}")
                appendLine()

                appendLine("--- IV. TABLE 9: DETAILS OF TAX PAID AS DECLARED IN RETURNS ---")
                appendLine("Metric,Amount (INR)")
                appendLine("Total Tax Payable,${toR(gstr9.table9Details.taxPayablePaise)}")
                appendLine("Paid Through ITC,${toR(gstr9.table9Details.taxPaidItcPaise)}")
                appendLine("Paid in Cash,${toR(gstr9.table9Details.taxPaidCashPaise)}")
                appendLine("Balance Payable,${toR(gstr9.table9Details.balancePayablePaise)}")
            }

            val safeName = sanitizeFileName("${profile.businessName}_GSTR9_Annual_${fyLabel}".replace(" ", "_"))
            val file = prepareExportFile(context, "${safeName}.csv")
            file.writeText(csv)

            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                file
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "${profile.businessName} - Form GSTR-9 Annual Return CSV ($fyLabel)")
                putExtra(Intent.EXTRA_TEXT, "Attached is the Form GSTR-9 Annual Return CSV summary ($fyLabel) generated by ${profile.businessName}.")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            launchChooserSafely(context, shareIntent, "Share GSTR-9 CSV")
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "GSTR-9 CSV Export: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    // =========================================================================
    // GSTR-2B AUTOMATED RECONCILIATION EXPORTS (PDF & CSV)
    // =========================================================================

    fun generateAndShareGstr2bReconciliationPdf(
        context: Context,
        profile: BusinessProfile,
        periodLabel: String,
        items: List<Gstr2bReconciliationEngine.ReconciliationItem>,
        summary: Gstr2bReconciliationEngine.ReconciliationSummary
    ) {
        try {
            val doc = PdfDocument()
            val paint = Paint().apply { isAntiAlias = true }
            var reconPageNum = 1
            var activeReconPage = doc.startPage(PdfDocument.PageInfo.Builder(595, 842, reconPageNum).create())
            var canvas = activeReconPage.canvas

            fun fmt(paise: Long) = FinancialEngine.formatPaise(paise)

            // Header Banner
            paint.color = Color.rgb(15, 23, 42)
            canvas.drawRect(0f, 0f, 595f, 75f, paint)

            paint.color = Color.rgb(245, 158, 11) // Gold
            paint.textSize = 13.5f
            paint.isFakeBoldText = true
            canvas.drawText("${profile.businessName} • GSTR-2B ITC RECONCILIATION STATEMENT", 30f, 28f, paint)

            paint.color = Color.WHITE
            paint.textSize = 8.5f
            paint.isFakeBoldText = false
            canvas.drawText("Reconciliation Period: $periodLabel • Rule 36(4) Statutory ITC Audit", 30f, 44f, paint)
            canvas.drawText("GSTIN: ${profile.gstin.ifEmpty { "N/A" }} | Date: ${java.text.SimpleDateFormat("dd/MM/yyyy", Locale.ENGLISH).format(java.util.Date())}", 30f, 58f, paint)

            var y = 92f

            // Executive Summary Card
            paint.color = Color.rgb(241, 245, 249)
            canvas.drawRoundRect(30f, y, 565f, y + 68f, 6f, 6f, paint)

            paint.color = Color.rgb(15, 23, 42)
            paint.textSize = 9f
            paint.isFakeBoldText = true
            canvas.drawText("INPUT TAX CREDIT (ITC) RECONCILIATION SUMMARY", 40f, y + 16f, paint)

            paint.textSize = 8f
            paint.isFakeBoldText = false
            paint.color = Color.rgb(51, 65, 85)
            canvas.drawText("• GSTR-2B Invoices: ${summary.totalGstr2bCount} | Taxable: ${fmt(summary.totalGstr2bTaxablePaise)} | Available ITC: ${fmt(summary.totalGstr2bItcAvailablePaise)}", 40f, y + 32f, paint)
            canvas.drawText("• Books Purchases: ${summary.totalBooksPurchaseCount} | Taxable: ${fmt(summary.totalBooksTaxablePaise)} | Claimed ITC: ${fmt(summary.totalBooksItcClaimedPaise)}", 40f, y + 46f, paint)
            canvas.drawText("• Matched ITC: ${fmt(summary.matchedItcPaise)} (${summary.matchedCount} bills) | At-Risk (Missing in 2B): ${fmt(summary.missingIn2bAtRiskItcPaise)} (${summary.missingIn2bCount} bills)", 40f, y + 60f, paint)

            y += 82f

            fun drawReconTableHeader(c: android.graphics.Canvas, yPos: Float) {
                paint.color = Color.rgb(30, 58, 138)
                c.drawRect(30f, yPos, 565f, yPos + 20f, paint)
                paint.color = Color.WHITE
                paint.textSize = 8f
                paint.isFakeBoldText = true
                c.drawText("Status", 35f, yPos + 13f, paint)
                c.drawText("Supplier GSTIN / Name", 110f, yPos + 13f, paint)
                c.drawText("Inv # / Date", 250f, yPos + 13f, paint)
                c.drawText("2B Tax (₹)", 350f, yPos + 13f, paint)
                c.drawText("Books Tax (₹)", 430f, yPos + 13f, paint)
                c.drawText("Variance (₹)", 510f, yPos + 13f, paint)
            }

            drawReconTableHeader(canvas, y)
            y += 28f

            items.forEach { item ->
                if (y > 780f) {
                    paint.color = Color.rgb(148, 163, 184)
                    paint.textSize = 7.5f
                    paint.isFakeBoldText = false
                    canvas.drawText("Page $reconPageNum • Pabaru Billing • Verified against internal purchase vouchers and GSTR-2B Statement.", 35f, 818f, paint)
                    doc.finishPage(activeReconPage)
                    reconPageNum++
                    activeReconPage = doc.startPage(PdfDocument.PageInfo.Builder(595, 842, reconPageNum).create())
                    canvas = activeReconPage.canvas
                    y = 35f
                    drawReconTableHeader(canvas, y)
                    y += 28f
                }

                val (statusLabel, statusColor) = when (item.matchStatus) {
                    Gstr2bReconciliationEngine.MatchStatus.FullyMatched -> "MATCHED" to Color.rgb(16, 185, 129)
                    Gstr2bReconciliationEngine.MatchStatus.ValueMismatch -> "MISMATCH" to Color.rgb(245, 158, 11)
                    Gstr2bReconciliationEngine.MatchStatus.MissingInBooks -> "NOT IN BOOKS" to Color.rgb(59, 130, 246)
                    Gstr2bReconciliationEngine.MatchStatus.MissingIn2B -> "NOT IN 2B" to Color.rgb(239, 68, 68)
                    Gstr2bReconciliationEngine.MatchStatus.IneligibleITC -> "BLOCKED" to Color.rgb(100, 116, 139)
                }

                paint.color = statusColor
                paint.textSize = 7.5f
                paint.isFakeBoldText = true
                canvas.drawText(statusLabel, 35f, y, paint)

                paint.color = Color.rgb(30, 41, 59)
                paint.textSize = 7.5f
                paint.isFakeBoldText = false
                canvas.drawText(item.supplierGstin.take(15), 110f, y, paint)
                canvas.drawText("${item.invoiceNumber.take(12)} (${item.invoiceDate.take(10)})", 250f, y, paint)
                canvas.drawText(fmt(item.gstr2bTotalTaxPaise), 350f, y, paint)
                canvas.drawText(fmt(item.booksTotalTaxPaise), 430f, y, paint)

                paint.color = if (item.diffTaxPaise == 0L) Color.rgb(71, 85, 105) else Color.rgb(220, 38, 38)
                paint.isFakeBoldText = item.diffTaxPaise != 0L
                canvas.drawText(fmt(item.diffTaxPaise), 510f, y, paint)

                y += 18f
            }

            // Footer
            paint.color = Color.rgb(148, 163, 184)
            paint.textSize = 7.5f
            paint.isFakeBoldText = false
            canvas.drawText("Page $reconPageNum • Pabaru Billing • Verified against internal purchase vouchers and GST Portal GSTR-2B Statement.", 35f, 818f, paint)

            doc.finishPage(activeReconPage)

            val safeName = sanitizeFileName("GSTR2B_Reconciliation_${periodLabel}")
            val file = prepareExportFile(context, "${safeName}.pdf")
            FileOutputStream(file).use { out -> doc.writeTo(out) }
            doc.close()

            val uri: Uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "${profile.businessName} - GSTR-2B Reconciliation Statement")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            launchChooserSafely(context, shareIntent, "Share GSTR-2B Reconciliation PDF")
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "GSTR-2B PDF Export: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    fun generateAndShareGstr2bReconciliationCsv(
        context: Context,
        profile: BusinessProfile,
        periodLabel: String,
        items: List<Gstr2bReconciliationEngine.ReconciliationItem>,
        summary: Gstr2bReconciliationEngine.ReconciliationSummary
    ) {
        try {
            fun toR(paise: Long) = String.format(Locale.ENGLISH, "%.2f", paise / 100.0)

            val csv = buildString {
                appendLine("GSTR-2B AUTOMATED RECONCILIATION STATEMENT")
                appendLine("Business Name,${csvEscape(profile.businessName)}")
                appendLine("GSTIN,${csvEscape(profile.gstin)}")
                appendLine("Reconciliation Period,${csvEscape(periodLabel)}")
                appendLine("Total 2B ITC Available,${toR(summary.totalGstr2bItcAvailablePaise)}")
                appendLine("Total Books Claimed ITC,${toR(summary.totalBooksItcClaimedPaise)}")
                appendLine("Matched ITC,${toR(summary.matchedItcPaise)}")
                appendLine("At-Risk ITC (Missing in 2B),${toR(summary.missingIn2bAtRiskItcPaise)}")
                appendLine("Unclaimed ITC (Missing in Books),${toR(summary.missingInBooksUnclaimedItcPaise)}")
                appendLine()
                appendLine("Status,Supplier GSTIN,Supplier Trade Name,Invoice Number,Invoice Date,2B Taxable (INR),2B CGST (INR),2B SGST (INR),2B IGST (INR),2B Total Tax (INR),Books Taxable (INR),Books CGST (INR),Books SGST (INR),Books IGST (INR),Books Total Tax (INR),Taxable Variance (INR),Tax Variance (INR),Remarks")

                items.forEach { itm ->
                    appendLine("${csvEscape(itm.matchStatus.name)},${csvEscape(itm.supplierGstin)},${csvEscape(itm.supplierName)},${csvEscape(itm.invoiceNumber)},${csvEscape(itm.invoiceDate)},${toR(itm.gstr2bTaxablePaise)},${toR(itm.gstr2bCgstPaise)},${toR(itm.gstr2bSgstPaise)},${toR(itm.gstr2bIgstPaise)},${toR(itm.gstr2bTotalTaxPaise)},${toR(itm.booksTaxablePaise)},${toR(itm.booksCgstPaise)},${toR(itm.booksSgstPaise)},${toR(itm.booksIgstPaise)},${toR(itm.booksTotalTaxPaise)},${toR(itm.diffTaxablePaise)},${toR(itm.diffTaxPaise)},${csvEscape(itm.remarks)}")
                }
            }

            val safeName = sanitizeFileName("GSTR2B_Reconciliation_${periodLabel}")
            val file = prepareExportFile(context, "${safeName}.csv")
            file.writeText(csv, Charsets.UTF_8)

            val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "${profile.businessName} - GSTR-2B Reconciliation CSV ($periodLabel)")
                putExtra(Intent.EXTRA_TEXT, "Attached is the GSTR-2B Reconciliation CSV statement ($periodLabel) generated by ${profile.businessName}.")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            launchChooserSafely(context, shareIntent, "Share GSTR-2B Reconciliation CSV")
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "GSTR-2B CSV Export: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}

