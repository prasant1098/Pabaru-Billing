package com.example.engine

import android.content.Context
import android.net.Uri
import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.util.Locale
import java.util.UUID
import java.util.zip.ZipInputStream

object Gstr1ExcelImportEngine {

    // -----------------------------------------------------------------
    // 1. Data Models
    // -----------------------------------------------------------------
    data class SpreadsheetRawData(
        val sheetName: String,
        val headers: List<String>,
        val rows: List<List<String>>,
        val totalRawRows: Int
    )

    enum class TaxSoftwareFormat(val displayName: String, val description: String) {
        AutoDetect("Auto-Detect Format", "Automatically scans headers and matches software pattern"),
        GovtOfflineTool("Govt GST Offline Tool", "Official GST Portal Excel Utility (b2b sheet format)"),
        ClearTax("ClearTax GSTR-1", "ClearTax GSTR-1 outward sales register export"),
        ZohoBooks("Zoho Books Outward", "Zoho Books GST sales report with tax rate split"),
        TallyPrime("TallyPrime / ERP 9", "Tally GSTR-1 B2B outward vouchers export"),
        BusyAccounting("Busy Accounting", "Busy GST sales register with bill particulars"),
        Custom("Custom Column Mapping", "Manually map Excel columns to GSTR-1 statutory fields")
    }

    data class ColumnMapping(
        val gstinCol: Int = -1,
        val nameCol: Int = -1,
        val invoiceNoCol: Int = -1,
        val dateCol: Int = -1,
        val posCol: Int = -1,
        val taxableCol: Int = -1,
        val rateCol: Int = -1,
        val cgstCol: Int = -1,
        val sgstCol: Int = -1,
        val igstCol: Int = -1,
        val totalCol: Int = -1,
        val hsnCol: Int = -1
    )

    data class ImportedGstr1Invoice(
        val id: String = UUID.randomUUID().toString(),
        val gstin: String,
        val partyName: String,
        val invoiceNumber: String,
        val date: String, // YYYY-MM-DD
        val posStateCode: String,
        val taxablePaise: Long,
        val gstRatePercent: Int,
        val cgstPaise: Long,
        val sgstPaise: Long,
        val igstPaise: Long,
        val totalPaise: Long,
        val isB2b: Boolean,
        val isInterstate: Boolean,
        val hsnCode: String = "9999",
        val validationWarnings: List<String> = emptyList(),
        val isSelected: Boolean = true,
        val gstRateBps: Int = gstRatePercent * 100
    )

    data class ImportResult(
        val detectedFormat: TaxSoftwareFormat,
        val mappingUsed: ColumnMapping,
        val invoices: List<ImportedGstr1Invoice>,
        val totalTaxablePaise: Long,
        val totalCgstPaise: Long,
        val totalSgstPaise: Long,
        val totalIgstPaise: Long,
        val totalTaxPaise: Long,
        val b2bCount: Int,
        val b2cCount: Int,
        val invalidCount: Int,
        val rawRowCount: Int
    )

    // -----------------------------------------------------------------
    // 2. Spreadsheet Parser (XLSX via Zip/XmlPullParser + CSV/TSV)
    // -----------------------------------------------------------------
    private const val MAX_IMPORT_SIZE_BYTES = 15 * 1024 * 1024 // 15MB limit to prevent OOM
    private const val MAX_DECOMPRESSED_ZIP_BYTES = 50 * 1024 * 1024 // 50MB decompressed limit to guard zip bombs

    private fun readStreamBounded(inputStream: InputStream, maxBytes: Int = MAX_IMPORT_SIZE_BYTES): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        var total = 0
        while (true) {
            val read = inputStream.read(buffer)
            if (read <= 0) break
            total += read
            if (total > maxBytes) {
                throw IllegalArgumentException("File size exceeds limit of ${maxBytes / (1024 * 1024)}MB")
            }
            out.write(buffer, 0, read)
        }
        return out.toByteArray()
    }

    fun parseSpreadsheetStream(inputStream: InputStream, filename: String = ""): SpreadsheetRawData {
        val bytes = readStreamBounded(inputStream)
        val isZip = isZipArchive(bytes)

        return if (isZip) {
            parseXlsxBytes(bytes)
        } else {
            parseCsvBytes(bytes)
        }
    }

    private fun isZipArchive(bytes: ByteArray): Boolean {
        if (bytes.size < 4) return false
        // ZIP local file header signature: 0x04034b50 (PK\u0003\u0004)
        return bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte() &&
               bytes[2] == 0x03.toByte() && bytes[3] == 0x04.toByte()
    }

    private fun readZipEntryBounded(zis: ZipInputStream, remainingBudget: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        var total = 0
        while (true) {
            val read = zis.read(buffer)
            if (read <= 0) break
            total += read
            if (total > remainingBudget) {
                throw IllegalArgumentException("XLSX decompressed content exceeds safe limit of ${MAX_DECOMPRESSED_ZIP_BYTES / (1024 * 1024)}MB")
            }
            out.write(buffer, 0, read)
        }
        return out.toByteArray()
    }

    private fun parseXlsxBytes(bytes: ByteArray): SpreadsheetRawData {
        if (bytes.size > MAX_IMPORT_SIZE_BYTES) {
            throw IllegalArgumentException("File size exceeds limit of 15MB")
        }
        val sharedStrings = mutableListOf<String>()
        val sheetEntries = mutableMapOf<String, ByteArray>()
        var workbookXmlBytes: ByteArray? = null
        var workbookRelsBytes: ByteArray? = null
        var decompressedRemaining = MAX_DECOMPRESSED_ZIP_BYTES

        // Pass 1: Extract shared strings, workbook XML, relationships, and worksheet entries with zip-bomb guard
        ZipInputStream(ByteArrayInputStream(bytes)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val name = entry.name
                when {
                    name == "xl/sharedStrings.xml" -> {
                        val ssBytes = readZipEntryBounded(zis, decompressedRemaining)
                        decompressedRemaining -= ssBytes.size
                        parseSharedStrings(ByteArrayInputStream(ssBytes), sharedStrings)
                    }
                    name == "xl/workbook.xml" -> {
                        val wbBytes = readZipEntryBounded(zis, decompressedRemaining)
                        decompressedRemaining -= wbBytes.size
                        workbookXmlBytes = wbBytes
                    }
                    name == "xl/_rels/workbook.xml.rels" -> {
                        val relBytes = readZipEntryBounded(zis, decompressedRemaining)
                        decompressedRemaining -= relBytes.size
                        workbookRelsBytes = relBytes
                    }
                    name.startsWith("xl/worksheets/") && name.endsWith(".xml") -> {
                        val shBytes = readZipEntryBounded(zis, decompressedRemaining)
                        decompressedRemaining -= shBytes.size
                        sheetEntries[name] = shBytes
                    }
                }
                entry = zis.nextEntry
            }
        }

        // Resolve relationship IDs -> worksheet ZIP paths
        val relTargetMap = mutableMapOf<String, String>()
        if (workbookRelsBytes != null) {
            try {
                val relParser = Xml.newPullParser()
                relParser.setInput(ByteArrayInputStream(workbookRelsBytes), "UTF-8")
                var ev = relParser.eventType
                while (ev != XmlPullParser.END_DOCUMENT) {
                    if (ev == XmlPullParser.START_TAG && relParser.name == "Relationship") {
                        val id = relParser.getAttributeValue(null, "Id") ?: ""
                        val target = relParser.getAttributeValue(null, "Target") ?: ""
                        if (id.isNotBlank() && target.isNotBlank()) {
                            val normalized = if (target.startsWith("/")) target.drop(1)
                            else if (target.startsWith("xl/")) target
                            else "xl/$target"
                            relTargetMap[id] = normalized
                        }
                    }
                    ev = relParser.next()
                }
            } catch (_: Exception) {}
        }

        // Try to identify B2B / Sales worksheet from workbook.xml + rels
        var targetEntryKey: String? = null
        var foundSheetName = "Sheet1"

        if (workbookXmlBytes != null) {
            try {
                val parser = Xml.newPullParser()
                parser.setInput(ByteArrayInputStream(workbookXmlBytes), "UTF-8")
                var event = parser.eventType
                var sheetIndex = 1
                var firstValidKey: String? = null
                var firstSheetName = "Sheet1"
                while (event != XmlPullParser.END_DOCUMENT) {
                    if (event == XmlPullParser.START_TAG && parser.name == "sheet") {
                        val sName = parser.getAttributeValue(null, "name") ?: ""
                        val rId = parser.getAttributeValue("http://schemas.openxmlformats.org/officeDocument/2006/relationships", "id")
                            ?: parser.getAttributeValue(null, "r:id") ?: "rId$sheetIndex"
                        val candidateKey = relTargetMap[rId] ?: "xl/worksheets/sheet$sheetIndex.xml"
                        if (firstValidKey == null && sheetEntries.containsKey(candidateKey)) {
                            firstValidKey = candidateKey
                            firstSheetName = sName.ifBlank { "Sheet$sheetIndex" }
                        }
                        if (sName.contains("b2b", ignoreCase = true) || sName.contains("sale", ignoreCase = true)) {
                            if (sheetEntries.containsKey(candidateKey)) {
                                targetEntryKey = candidateKey
                                foundSheetName = sName
                                break
                            }
                        }
                        sheetIndex++
                    }
                    event = parser.next()
                }
                if (targetEntryKey == null && firstValidKey != null) {
                    targetEntryKey = firstValidKey
                    foundSheetName = firstSheetName
                }
            } catch (_: Exception) {}
        }

        if (targetEntryKey == null || !sheetEntries.containsKey(targetEntryKey)) {
            targetEntryKey = sheetEntries.keys.firstOrNull { it.contains("b2b", ignoreCase = true) }
                ?: sheetEntries.keys.sorted().firstOrNull()
                ?: throw IllegalArgumentException("No worksheet found in XLSX archive")
            foundSheetName = targetEntryKey.substringAfterLast("/").substringBefore(".xml")
        }

        val sheetBytes = sheetEntries[targetEntryKey] ?: throw IllegalArgumentException("Worksheet data missing")

        // Pass 2: Parse sheet XML rows and cells
        val rows = parseSheetRows(sheetBytes, sharedStrings)
        if (rows.isEmpty()) {
            return SpreadsheetRawData(foundSheetName, emptyList(), emptyList(), 0)
        }

        // Find header row: first row with at least 3 non-empty cells
        var headerRowIndex = 0
        for (i in rows.indices) {
            val nonBlank = rows[i].count { it.isNotBlank() }
            if (nonBlank >= 3) {
                headerRowIndex = i
                break
            }
        }

        val headers = rows[headerRowIndex].map { it.trim() }
        val dataRows = rows.drop(headerRowIndex + 1).filter { row ->
            row.any { it.isNotBlank() }
        }

        return SpreadsheetRawData(
            sheetName = foundSheetName,
            headers = headers,
            rows = dataRows,
            totalRawRows = dataRows.size
        )
    }

    private fun parseSharedStrings(zis: InputStream, outStrings: MutableList<String>) {
        val parser = Xml.newPullParser()
        parser.setInput(zis, "UTF-8")
        var eventType = parser.eventType
        val currentString = StringBuilder()
        var insideSi = false

        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    if (parser.name == "si") {
                        insideSi = true
                        currentString.clear()
                    } else if (insideSi && parser.name == "t") {
                        currentString.append(parser.nextText())
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (parser.name == "si") {
                        outStrings.add(currentString.toString())
                        insideSi = false
                    }
                }
            }
            eventType = parser.next()
        }
    }

    private fun parseSheetRows(sheetBytes: ByteArray, sharedStrings: List<String>): List<List<String>> {
        val parser = Xml.newPullParser()
        parser.setInput(ByteArrayInputStream(sheetBytes), "UTF-8")
        var eventType = parser.eventType

        val resultRows = mutableListOf<List<String>>()
        var currentRowCells = mutableMapOf<Int, String>()
        var currentCellCol = 0
        var currentCellType = ""

        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    when (parser.name) {
                        "row" -> {
                            currentRowCells = mutableMapOf()
                        }
                        "c" -> {
                            val cellRef = parser.getAttributeValue(null, "r") ?: ""
                            currentCellType = parser.getAttributeValue(null, "t") ?: ""
                            currentCellCol = colRefToIndex(cellRef)
                        }
                        "v" -> {
                            val vText = parser.nextText()
                            val cellValue = if (currentCellType == "s") {
                                val sIndex = vText.toIntOrNull()
                                if (sIndex != null && sIndex in sharedStrings.indices) {
                                    sharedStrings[sIndex]
                                } else vText
                            } else {
                                vText
                            }
                            currentRowCells[currentCellCol] = cellValue
                        }
                        "t" -> {
                            val tText = parser.nextText()
                            currentRowCells[currentCellCol] = tText
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (parser.name == "row") {
                        if (currentRowCells.isNotEmpty()) {
                            val maxCol = currentRowCells.keys.maxOrNull() ?: 0
                            val rowList = (0..maxCol).map { col ->
                                currentRowCells[col] ?: ""
                            }
                            resultRows.add(rowList)
                        }
                    }
                }
            }
            eventType = parser.next()
        }

        return resultRows
    }

    private fun colRefToIndex(ref: String): Int {
        val letters = ref.takeWhile { it.isLetter() }.uppercase()
        if (letters.isEmpty()) return 0
        var index = 0
        for (ch in letters) {
            index = index * 26 + (ch - 'A' + 1)
        }
        return (index - 1).coerceAtLeast(0)
    }

    private fun parseCsvBytes(bytes: ByteArray): SpreadsheetRawData {
        val text = String(bytes, Charsets.UTF_8).trimStart('\uFEFF')
        val sampleLines = text.lineSequence().take(5).toList()
        val delimiter = detectDelimiter(sampleLines)
        val parsedRows = parseDelimitedRecords(text, delimiter)

        if (parsedRows.isEmpty()) {
            return SpreadsheetRawData("CSV", emptyList(), emptyList(), 0)
        }

        var headerRowIndex = 0
        for (i in parsedRows.indices) {
            if (parsedRows[i].count { it.isNotBlank() } >= 2) {
                headerRowIndex = i
                break
            }
        }

        val headers = parsedRows[headerRowIndex].map { it.trim() }
        val dataRows = parsedRows.drop(headerRowIndex + 1).filter { r -> r.any { it.isNotBlank() } }

        return SpreadsheetRawData(
            sheetName = "CSV/Spreadsheet",
            headers = headers,
            rows = dataRows,
            totalRawRows = dataRows.size
        )
    }

    fun parseDelimitedRecords(text: String, delimiter: Char): List<List<String>> {
        val records = mutableListOf<List<String>>()
        var currentRow = mutableListOf<String>()
        val sb = StringBuilder()
        var inQuotes = false
        var i = 0
        val len = text.length

        while (i < len) {
            val c = text[i]
            when {
                c == '"' -> {
                    if (inQuotes && i + 1 < len && text[i + 1] == '"') {
                        sb.append('"')
                        i++
                    } else {
                        inQuotes = !inQuotes
                    }
                }
                c == delimiter && !inQuotes -> {
                    currentRow.add(sb.toString().trim())
                    sb.clear()
                }
                (c == '\r' || c == '\n') && !inQuotes -> {
                    if (c == '\r' && i + 1 < len && text[i + 1] == '\n') {
                        i++
                    }
                    currentRow.add(sb.toString().trim())
                    sb.clear()
                    if (currentRow.any { it.isNotBlank() }) {
                        records.add(currentRow)
                    }
                    currentRow = mutableListOf()
                }
                else -> {
                    sb.append(c)
                }
            }
            i++
        }
        if (sb.isNotEmpty() || currentRow.isNotEmpty()) {
            currentRow.add(sb.toString().trim())
            if (currentRow.any { it.isNotBlank() }) {
                records.add(currentRow)
            }
        }
        return records
    }

    private fun detectDelimiter(lines: List<String>): Char {
        val sample = lines.take(5).joinToString("\n")
        val commas = sample.count { it == ',' }
        val tabs = sample.count { it == '\t' }
        val semicolons = sample.count { it == ';' }

        return when {
            tabs > commas && tabs > semicolons -> '\t'
            semicolons > commas && semicolons > tabs -> ';'
            else -> ','
        }
    }

    fun tokenizeDelimitedLine(line: String, delimiter: Char): List<String> {
        val tokens = mutableListOf<String>()
        val sb = StringBuilder()
        var inQuotes = false
        var i = 0

        while (i < line.length) {
            val c = line[i]
            when {
                c == '"' -> {
                    if (inQuotes && i + 1 < line.length && line[i + 1] == '"') {
                        sb.append('"')
                        i++ // skip escaped quote
                    } else {
                        inQuotes = !inQuotes
                    }
                }
                c == delimiter && !inQuotes -> {
                    tokens.add(sb.toString().trim())
                    sb.clear()
                }
                else -> {
                    sb.append(c)
                }
            }
            i++
        }
        tokens.add(sb.toString().trim())
        return tokens
    }

    // -----------------------------------------------------------------
    // 3. Tax Software Column Mapping & Auto-Detection
    // -----------------------------------------------------------------
    fun detectFormat(headers: List<String>): Pair<TaxSoftwareFormat, ColumnMapping> {
        val normalizedHeaders = headers.map { normalizeHeader(it) }

        var gstinCol = -1
        var nameCol = -1
        var invNoCol = -1
        var dateCol = -1
        var posCol = -1
        var taxableCol = -1
        var rateCol = -1
        var cgstCol = -1
        var sgstCol = -1
        var igstCol = -1
        var totalCol = -1
        var hsnCol = -1

        var govtScore = 0
        var clearTaxScore = 0
        var zohoScore = 0
        var tallyScore = 0
        var busyScore = 0

        headers.forEachIndexed { idx, raw ->
            val norm = normalizeHeader(raw)

            // Match GSTIN
            if (gstinCol == -1 && (norm.contains("gstin") || norm.contains("ctin") || norm.contains("uin"))) {
                gstinCol = idx
            }
            // Match Party Name
            if (nameCol == -1 && (norm.contains("receivername") || norm.contains("partyname") ||
                                  norm.contains("customername") || norm.contains("legalname") ||
                                  norm.contains("tradename") || norm.contains("particulars") || norm.contains("accountname"))) {
                nameCol = idx
            }
            // Match Invoice No
            if (invNoCol == -1 && (norm.contains("invoicenumber") || norm.contains("invoiceno") ||
                                  norm.contains("inum") || norm.contains("voucherno") ||
                                  norm.contains("vchno") || norm.contains("billno") || norm == "invoice")) {
                invNoCol = idx
            }
            // Match Date
            if (dateCol == -1 && (norm.contains("invoicedate") || norm.contains("vchdate") ||
                                  norm.contains("voucherdate") || norm.contains("billdate") ||
                                  norm.contains("date") || norm == "idt")) {
                dateCol = idx
            }
            // Match Place of Supply
            if (posCol == -1 && (norm.contains("placeofsupply") || norm == "pos" ||
                                 norm.contains("statename") || norm.contains("statecode") ||
                                 norm.contains("statepos") || norm.contains("destination"))) {
                posCol = idx
            }
            // Match Taxable Value
            if (taxableCol == -1 && (norm.contains("taxablevalue") || norm.contains("taxableamount") ||
                                     norm.contains("taxableamt") || norm.contains("assessablevalue") ||
                                     norm == "taxable" || norm == "txval")) {
                taxableCol = idx
            }
            // Match Tax Rate
            if (rateCol == -1 && (norm.contains("taxrate") || norm.contains("gstrate") ||
                                  norm.contains("itemtax") || norm.contains("rate") ||
                                  norm.contains("applicableoftaxrate"))) {
                rateCol = idx
            }
            // Match CGST
            if (cgstCol == -1 && (norm.contains("cgst") || norm.contains("centraltax") || norm == "camt")) {
                cgstCol = idx
            }
            // Match SGST
            if (sgstCol == -1 && (norm.contains("sgst") || norm.contains("statetax") || norm == "samt" || norm.contains("utgst"))) {
                sgstCol = idx
            }
            // Match IGST
            if (igstCol == -1 && (norm.contains("igst") || norm.contains("integratedtax") || norm == "iamt")) {
                igstCol = idx
            }
            // Match Total Invoice Value
            if (totalCol == -1 && (norm.contains("invoicevalue") || norm.contains("totalvalue") ||
                                   norm.contains("totalamount") || norm.contains("grossvalue") ||
                                   norm.contains("billamount") || norm == "total" || norm == "val")) {
                totalCol = idx
            }
            // Match HSN
            if (hsnCol == -1 && (norm.contains("hsn") || norm.contains("sac"))) {
                hsnCol = idx
            }

            // Software signature scoring
            if (norm.contains("gstinuinofrecipient") || norm.contains("receivername") || norm.contains("applicableoftaxrate")) govtScore += 2
            if (norm == "partyname" || norm == "totalvalue" || norm.contains("cleartax")) clearTaxScore += 1
            if (norm.contains("customername") || norm.contains("customergstin") || norm.contains("itemtax")) zohoScore += 2
            if (norm.contains("voucherno") || norm.contains("vchno") || norm.contains("particulars") || norm.contains("assessablevalue") || norm.contains("centraltaxamount") || norm.contains("integratedtaxamount")) tallyScore += 2
            if (norm.contains("billno") || norm.contains("accountname") || norm.contains("billamount") || norm.contains("taxableamt") || norm.contains("cgstmt")) busyScore += 2
        }

        val detectedFormat = when {
            govtScore >= 2 -> TaxSoftwareFormat.GovtOfflineTool
            zohoScore >= 2 -> TaxSoftwareFormat.ZohoBooks
            tallyScore >= 2 -> TaxSoftwareFormat.TallyPrime
            busyScore >= 2 -> TaxSoftwareFormat.BusyAccounting
            clearTaxScore >= 2 -> TaxSoftwareFormat.ClearTax
            gstinCol != -1 && invNoCol != -1 && taxableCol != -1 -> TaxSoftwareFormat.GovtOfflineTool
            else -> TaxSoftwareFormat.Custom
        }

        val mapping = ColumnMapping(
            gstinCol = gstinCol,
            nameCol = nameCol,
            invoiceNoCol = invNoCol,
            dateCol = dateCol,
            posCol = posCol,
            taxableCol = taxableCol,
            rateCol = rateCol,
            cgstCol = cgstCol,
            sgstCol = sgstCol,
            igstCol = igstCol,
            totalCol = totalCol,
            hsnCol = hsnCol
        )

        return Pair(detectedFormat, mapping)
    }

    private fun normalizeHeader(header: String): String {
        return header.lowercase(Locale.ENGLISH).replace("[^a-z0-9]".toRegex(), "")
    }

    // -----------------------------------------------------------------
    // 4. Invoice Processing & Statutory Tax Calculation
    // -----------------------------------------------------------------
    fun parseInvoices(
        rawData: SpreadsheetRawData,
        format: TaxSoftwareFormat,
        customMapping: ColumnMapping? = null,
        homeStateCode: String = "27"
    ): ImportResult {
        val mapping = customMapping ?: detectFormat(rawData.headers).second

        val invoices = mutableListOf<ImportedGstr1Invoice>()
        var totalTaxable = 0L
        var totalCgst = 0L
        var totalSgst = 0L
        var totalIgst = 0L
        var b2bCount = 0
        var b2cCount = 0
        var invalidCount = 0

        val seenInvoices = mutableSetOf<String>()

        rawData.rows.forEachIndexed { rowIdx, row ->
            fun getCell(col: Int): String = if (col in row.indices) row[col].trim() else ""

            val rawGstin = getCell(mapping.gstinCol).uppercase(Locale.ENGLISH).replace(" ", "")
            val rawName = getCell(mapping.nameCol).ifBlank { "Customer #${rowIdx + 1}" }
            val cellInvNo = getCell(mapping.invoiceNoCol)
            val rawInvNo = cellInvNo.ifBlank { "IMP-INV-${rowIdx + 1}" }
            val rawDateCell = getCell(mapping.dateCol)
            val rawDate = parseDateString(rawDateCell)
            val rawPosCell = getCell(mapping.posCol)
            val rawPos = parseStateCode(rawPosCell, rawGstin, homeStateCode)
            val taxablePaise = parseAmountToPaise(getCell(mapping.taxableCol))
            val rateCell = getCell(mapping.rateCol)
            val rateBps = parseRateBps(rateCell)
            val ratePercent = kotlin.math.round(rateBps / 100.0).toInt()
            val rateLabel = com.example.model.GstSlabs.label(rateBps)

            if (taxablePaise <= 0L) {
                invalidCount++
                return@forEachIndexed
            }

            val warnings = mutableListOf<String>()
            if (rawDate.isBlank()) {
                warnings.add("Invalid or missing invoice date '$rawDateCell'.")
            }
            if (rawPosCell.isBlank() && rawGstin.isBlank()) {
                warnings.add("Place of Supply missing; defaulted to state code $homeStateCode.")
            }
            val parsedRateRaw = rateCell.replace("%", "").trim().toDoubleOrNull()
            if (rateCell.isNotBlank() && parsedRateRaw == null) {
                warnings.add("Unrecognized GST rate '$rateCell'; defaulted to $rateLabel.")
            }
            val dupKey = "${rawGstin.ifBlank { "B2C" }}|${rawInvNo.uppercase(Locale.ENGLISH)}"
            if (!seenInvoices.add(dupKey)) {
                warnings.add("Duplicate invoice number '$rawInvNo' in import.")
            }

            val isGstinValid = isValidGstin(rawGstin)
            val isB2b = rawGstin.isNotBlank() && isGstinValid

            if (rawGstin.isNotBlank() && !isGstinValid) {
                warnings.add("GSTIN '$rawGstin' does not match 15-digit statutory format.")
            }

            val isInterstate = rawPos.isNotBlank() && rawPos != homeStateCode

            // Tax values: extract from row if present and cross-check; otherwise compute via half-up engine
            val extractedCgst = parseAmountToPaise(getCell(mapping.cgstCol))
            val extractedSgst = parseAmountToPaise(getCell(mapping.sgstCol))
            val extractedIgst = parseAmountToPaise(getCell(mapping.igstCol))

            val computedGst = FinancialEngine.calculateGstBps(
                taxablePaise = taxablePaise,
                rateBps = rateBps,
                isInterstate = isInterstate
            )

            val (cgstPaise, sgstPaise, igstPaise) = when {
                extractedCgst > 0L || extractedSgst > 0L || extractedIgst > 0L -> {
                    val extractedTotalTax = extractedCgst + extractedSgst + extractedIgst
                    val expectedTotalTax = computedGst.cgstPaise + computedGst.sgstPaise + computedGst.igstPaise
                    if (kotlin.math.abs(extractedTotalTax - expectedTotalTax) > 100L) {
                        warnings.add("Imported tax diverges from $rateLabel expected calculation by ${FinancialEngine.formatPaise(kotlin.math.abs(extractedTotalTax - expectedTotalTax))}.")
                    }
                    Triple(extractedCgst, extractedSgst, extractedIgst)
                }
                else -> Triple(computedGst.cgstPaise, computedGst.sgstPaise, computedGst.igstPaise)
            }

            val rawTotal = parseAmountToPaise(getCell(mapping.totalCol))
            val totalPaise = if (rawTotal > 0L) rawTotal else (taxablePaise + cgstPaise + sgstPaise + igstPaise)
            val rawHsn = getCell(mapping.hsnCol).ifBlank { "9999" }

            val invoice = ImportedGstr1Invoice(
                gstin = rawGstin,
                partyName = rawName,
                invoiceNumber = rawInvNo,
                date = rawDate,
                posStateCode = rawPos,
                taxablePaise = taxablePaise,
                gstRatePercent = ratePercent,
                cgstPaise = cgstPaise,
                sgstPaise = sgstPaise,
                igstPaise = igstPaise,
                totalPaise = totalPaise,
                isB2b = isB2b,
                isInterstate = isInterstate,
                hsnCode = rawHsn,
                validationWarnings = warnings,
                gstRateBps = rateBps
            )

            invoices.add(invoice)

            totalTaxable += taxablePaise
            totalCgst += cgstPaise
            totalSgst += sgstPaise
            totalIgst += igstPaise

            if (isB2b) b2bCount++ else b2cCount++
        }

        return ImportResult(
            detectedFormat = format,
            mappingUsed = mapping,
            invoices = invoices,
            totalTaxablePaise = totalTaxable,
            totalCgstPaise = totalCgst,
            totalSgstPaise = totalSgst,
            totalIgstPaise = totalIgst,
            totalTaxPaise = totalCgst + totalSgst + totalIgst,
            b2bCount = b2bCount,
            b2cCount = b2cCount,
            invalidCount = invalidCount,
            rawRowCount = rawData.rows.size
        )
    }

    // -----------------------------------------------------------------
    // 5. Utility Parsers (Date, Currency, GSTIN, State Codes)
    // -----------------------------------------------------------------
    fun parseAmountToPaise(str: String): Long {
        if (str.isBlank()) return 0L
        var cleaned = str.replace("₹", "")
            .replace("INR", "", ignoreCase = true)
            .replace("Rs.", "", ignoreCase = true)
            .replace("Rs", "", ignoreCase = true)
            .replace(",", "")
            .replace(Regex("[\\s\\u00A0]+"), "")
            .trim()
        if (cleaned.contains(Regex("[eE]"))) return 0L
        val isNegative = (cleaned.startsWith("(") && cleaned.endsWith(")")) || cleaned.startsWith("-")
        if (cleaned.startsWith("(") && cleaned.endsWith(")")) {
            cleaned = cleaned.substring(1, cleaned.length - 1).trim()
        } else if (cleaned.startsWith("-")) {
            cleaned = cleaned.removePrefix("-").trim()
        }
        val paise = FinancialEngine.parsePaiseFromRupees(cleaned)
        return if (isNegative) -paise else paise
    }

    fun parseRateBps(str: String): Int {
        if (str.isBlank()) return 1800
        val cleaned = str.replace("%", "").replace(" ", "").trim()
        val parsed = cleaned.toDoubleOrNull() ?: return 1800
        if (parsed.isNaN() || parsed.isInfinite() || parsed < 0.0) return 1800
        return kotlin.math.round(parsed * 100.0).toInt().coerceIn(0, 10000)
    }

    fun parseRatePercent(str: String): Int {
        return kotlin.math.round(parseRateBps(str) / 100.0).toInt().coerceIn(0, 100)
    }

    fun parseDateString(str: String): String {
        if (str.isBlank()) return ""
        val trimmed = str.trim()

        // 1. Check Excel serial date (e.g. 45200 = 2023-10-01)
        val serial = trimmed.toDoubleOrNull()
        if (serial != null && serial > 30000 && serial < 60000) {
            return try {
                val epoch = LocalDate.of(1899, 12, 30)
                epoch.plusDays(serial.toLong()).toString()
            } catch (_: Exception) {
                ""
            }
        }

        // 2. Check strict ISO / DD-MM-YYYY / YYYY/MM/DD calendar dates
        FinancialEngine.parseValidLocalDate(trimmed)?.let { parsed ->
            return String.format(Locale.ENGLISH, "%04d-%02d-%02d", parsed.year, parsed.monthValue, parsed.dayOfMonth)
        }

        // 3. Check DD-MMM-YYYY (e.g. 01-Oct-2026, 01-OCT-2026)
        try {
            val df = SimpleDateFormat("dd-MMM-yyyy", Locale.ENGLISH)
            df.isLenient = false
            val parsed = df.parse(trimmed)
            if (parsed != null) {
                val outDf = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH)
                return outDf.format(parsed)
            }
        } catch (_: Exception) {}

        return ""
    }

    fun parseStateCode(posStr: String, gstin: String, fallbackStateCode: String): String {
        val trimmed = posStr.trim()
        // Extract leading 2 digits if available: e.g. "27-Maharashtra" -> "27"
        val leadingDigits = trimmed.take(2)
        if (leadingDigits.length == 2 && leadingDigits.all { it.isDigit() }) {
            return leadingDigits
        }

        // State name mapping
        val lower = trimmed.lowercase(Locale.ENGLISH)
        when {
            lower.contains("maharashtra") -> return "27"
            lower.contains("gujarat") -> return "24"
            lower.contains("delhi") -> return "07"
            lower.contains("karnataka") -> return "29"
            lower.contains("tamil") -> return "33"
            lower.contains("uttar pradesh") || lower.contains("up") -> return "09"
            lower.contains("west bengal") || lower.contains("bengal") -> return "19"
            lower.contains("rajasthan") -> return "08"
            lower.contains("madhya pradesh") || lower.contains("mp") -> return "23"
            lower.contains("punjab") -> return "03"
            lower.contains("haryana") -> return "06"
            lower.contains("telangana") -> return "36"
            lower.contains("andhra") -> return "37"
            lower.contains("kerala") -> return "32"
            lower.contains("bihar") -> return "10"
        }

        // If not found in POS, extract from GSTIN
        if (gstin.length >= 2 && gstin.take(2).all { it.isDigit() }) {
            return gstin.take(2)
        }

        return fallbackStateCode.ifBlank { "27" }
    }

    fun isValidGstin(gstin: String): Boolean {
        return FinancialEngine.isValidGstinFormat(gstin)
    }

    // -----------------------------------------------------------------
    // 6. Built-in Preset Samples for Instant Testing & Offline Demo
    // -----------------------------------------------------------------
    fun getSampleData(format: TaxSoftwareFormat): SpreadsheetRawData {
        return when (format) {
            TaxSoftwareFormat.GovtOfflineTool, TaxSoftwareFormat.AutoDetect -> SpreadsheetRawData(
                sheetName = "b2b",
                headers = listOf(
                    "GSTIN/UIN of Recipient", "Receiver Name", "Invoice Number", "Invoice date",
                    "Invoice Value", "Place Of Supply", "Reverse Charge", "Applicable % of Tax Rate",
                    "Invoice Type", "E-Commerce GSTIN", "Rate", "Taxable Value", "Cess Amount"
                ),
                rows = listOf(
                    listOf("27AAACS1234M1Z2", "Sai Balaji Supermarket & Provisions", "INV/2026/0101", "01-10-2026", "118000.00", "27-Maharashtra", "N", "", "Regular", "", "18", "100000.00", "0.00"),
                    listOf("24AABCG5544K1Z8", "Gujarat Royal Spices & Agro Trd", "INV/2026/0102", "02-10-2026", "56000.00", "24-Gujarat", "N", "", "Regular", "", "12", "50000.00", "0.00"),
                    listOf("07AAACG9921B1Z3", "Delhi Capital Tech Supplies Ltd", "INV/2026/0103", "03-10-2026", "23600.00", "07-Delhi", "N", "", "Regular", "", "18", "20000.00", "0.00"),
                    listOf("27AABCO9988D1Z4", "Omkar Retail Mart Dadar", "INV/2026/0104", "04-10-2026", "84000.00", "27-Maharashtra", "N", "", "Regular", "", "5", "80000.00", "0.00"),
                    listOf("27AAACB8877E1Z1", "Bharat Bulk Commodities Agency", "INV/2026/0105", "05-10-2026", "177000.00", "27-Maharashtra", "N", "", "Regular", "", "18", "150000.00", "0.00")
                ),
                totalRawRows = 5
            )
            TaxSoftwareFormat.ClearTax -> SpreadsheetRawData(
                sheetName = "ClearTax_Sales",
                headers = listOf("GSTIN", "Party Name", "Invoice Number", "Invoice Date", "Total Value", "Place of Supply", "Tax Rate", "Taxable Value", "CGST", "SGST", "IGST", "Cess"),
                rows = listOf(
                    listOf("27AAACS1234M1Z2", "Sai Balaji Kirana Dadar", "CT-26-001", "2026-10-01", "118000", "27", "18", "100000", "9000", "9000", "0", "0"),
                    listOf("24AABCG5544K1Z8", "Gujarat Royal APMC Depot", "CT-26-002", "2026-10-02", "56000", "24", "12", "50000", "0", "0", "6000", "0"),
                    listOf("29AABCR1122Q1Z9", "Bangalore Grain Mart Pvt Ltd", "CT-26-003", "2026-10-03", "29500", "29", "18", "25000", "0", "0", "4500", "0"),
                    listOf("27AABCO9988D1Z4", "Omkar Store Thane West", "CT-26-004", "2026-10-04", "63000", "27", "5", "60000", "1500", "1500", "0", "0")
                ),
                totalRawRows = 4
            )
            TaxSoftwareFormat.ZohoBooks -> SpreadsheetRawData(
                sheetName = "Zoho_Invoices",
                headers = listOf("Customer GSTIN", "Customer Name", "Invoice#", "Invoice Date", "Total", "Place of Supply", "Item Tax %", "Taxable Amount", "IGST Amount", "CGST Amount", "SGST Amount"),
                rows = listOf(
                    listOf("27AAACS1234M1Z2", "Sai Balaji Provisions", "INV-ZOHO-01", "01/10/2026", "118000.00", "Maharashtra", "18%", "100000.00", "0.00", "9000.00", "9000.00"),
                    listOf("24AABCG5544K1Z8", "Gujarat Royal Spices", "INV-ZOHO-02", "02/10/2026", "112000.00", "Gujarat", "12%", "100000.00", "12000.00", "0.00", "0.00"),
                    listOf("07AAACG9921B1Z3", "Delhi Tech Traders", "INV-ZOHO-03", "03/10/2026", "59000.00", "Delhi", "18%", "50000.00", "9000.00", "0.00", "0.00")
                ),
                totalRawRows = 3
            )
            TaxSoftwareFormat.TallyPrime -> SpreadsheetRawData(
                sheetName = "Tally_GSTR1",
                headers = listOf("GSTIN/UIN", "Party Name", "Voucher No.", "Date", "Total Invoice Value", "State Name", "Rate", "Taxable Value", "Central Tax Amount", "State Tax Amount", "Integrated Tax Amount"),
                rows = listOf(
                    listOf("27AAACS1234M1Z2", "Sai Balaji Kirana", "VCH-2026-01", "01-Oct-2026", "118000.00", "Maharashtra", "18%", "100000.00", "9000.00", "9000.00", "0.00"),
                    listOf("24AABCG5544K1Z8", "Gujarat Royal Spices", "VCH-2026-02", "02-Oct-2026", "56000.00", "Gujarat", "12%", "50000.00", "0.00", "0.00", "6000.00"),
                    listOf("27AABCO9988D1Z4", "Omkar Store Dadar", "VCH-2026-03", "03-Oct-2026", "42000.00", "Maharashtra", "5%", "40000.00", "1000.00", "1000.00", "0.00")
                ),
                totalRawRows = 3
            )
            TaxSoftwareFormat.BusyAccounting, TaxSoftwareFormat.Custom -> SpreadsheetRawData(
                sheetName = "Busy_Sales",
                headers = listOf("Party GSTIN", "Party Name", "Bill No.", "Date", "Bill Amount", "State/POS", "Tax Rate", "Taxable Amt", "CGST Amt", "SGST Amt", "IGST Amt"),
                rows = listOf(
                    listOf("27AAACS1234M1Z2", "Sai Balaji Traders", "BILL-B-101", "01-10-2026", "118000", "27-MH", "18%", "100000", "9000", "9000", "0"),
                    listOf("24AABCG5544K1Z8", "Gujarat Royal APMC", "BILL-B-102", "02-10-2026", "67200", "24-GJ", "12%", "60000", "0", "0", "7200"),
                    listOf("27AAACB8877E1Z1", "Bharat Commodity Syndicate", "BILL-B-103", "03-10-2026", "157500", "27-MH", "5%", "150000", "3750", "3750", "0")
                ),
                totalRawRows = 3
            )
        }
    }
}
