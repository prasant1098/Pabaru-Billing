package com.example

import com.example.engine.CmaAndGstEngine
import com.example.engine.GstPortalJsonEngine
import com.example.engine.Gstr1ExcelImportEngine
import com.example.model.BusinessProfile
import com.example.model.Party
import com.example.model.Transaction
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Gstr1ExcelImportTest {

    @Test
    fun testGovtOfflineToolPresetParsingAndMapping() {
        val sample = Gstr1ExcelImportEngine.getSampleData(Gstr1ExcelImportEngine.TaxSoftwareFormat.GovtOfflineTool)
        assertEquals(5, sample.rows.size)
        assertTrue(sample.headers.contains("GSTIN/UIN of Recipient"))

        val (detectedFormat, mapping) = Gstr1ExcelImportEngine.detectFormat(sample.headers)
        assertEquals(Gstr1ExcelImportEngine.TaxSoftwareFormat.GovtOfflineTool, detectedFormat)
        assertNotEquals(-1, mapping.gstinCol)
        assertNotEquals(-1, mapping.taxableCol)

        val result = Gstr1ExcelImportEngine.parseInvoices(
            rawData = sample,
            format = detectedFormat,
            customMapping = mapping,
            homeStateCode = "27"
        )

        assertEquals(5, result.invoices.size)
        assertEquals(5, result.b2bCount)
        assertEquals(0, result.b2cCount)
        assertTrue(result.totalTaxablePaise > 0L)

        // Verify Interstate detection: Gujarat (24) vs Maharashtra (27)
        val gjInv = result.invoices.find { it.posStateCode == "24" }
        assertNotNull(gjInv)
        assertTrue(gjInv!!.isInterstate)
        assertTrue(gjInv.igstPaise > 0L)
        assertEquals(0L, gjInv.cgstPaise)

        // Verify Intrastate detection: Maharashtra (27) vs Maharashtra (27)
        val mhInv = result.invoices.find { it.posStateCode == "27" }
        assertNotNull(mhInv)
        assertFalse(mhInv!!.isInterstate)
        assertEquals(0L, mhInv.igstPaise)
        assertEquals(mhInv.cgstPaise, mhInv.sgstPaise)
    }

    @Test
    fun testClearTaxFormatDetectionAndTaxes() {
        val sample = Gstr1ExcelImportEngine.getSampleData(Gstr1ExcelImportEngine.TaxSoftwareFormat.ClearTax)
        val (detectedFormat, mapping) = Gstr1ExcelImportEngine.detectFormat(sample.headers)

        assertEquals(Gstr1ExcelImportEngine.TaxSoftwareFormat.ClearTax, detectedFormat)
        val result = Gstr1ExcelImportEngine.parseInvoices(sample, detectedFormat, mapping, "27")

        assertEquals(4, result.invoices.size)
        val first = result.invoices.first()
        assertEquals("27AAACS1234M1Z2", first.gstin)
        assertEquals(10000000L, first.taxablePaise)
        assertEquals(900000L, first.cgstPaise)
        assertEquals(900000L, first.sgstPaise)
    }

    @Test
    fun testZohoBooksFormatDetection() {
        val sample = Gstr1ExcelImportEngine.getSampleData(Gstr1ExcelImportEngine.TaxSoftwareFormat.ZohoBooks)
        val (detectedFormat, mapping) = Gstr1ExcelImportEngine.detectFormat(sample.headers)

        assertEquals(Gstr1ExcelImportEngine.TaxSoftwareFormat.ZohoBooks, detectedFormat)
        val result = Gstr1ExcelImportEngine.parseInvoices(sample, detectedFormat, mapping, "27")

        assertEquals(3, result.invoices.size)
        assertEquals(3, result.b2bCount)
    }

    @Test
    fun testTallyPrimeFormatDetection() {
        val sample = Gstr1ExcelImportEngine.getSampleData(Gstr1ExcelImportEngine.TaxSoftwareFormat.TallyPrime)
        val (detectedFormat, mapping) = Gstr1ExcelImportEngine.detectFormat(sample.headers)

        assertEquals(Gstr1ExcelImportEngine.TaxSoftwareFormat.TallyPrime, detectedFormat)
        val result = Gstr1ExcelImportEngine.parseInvoices(sample, detectedFormat, mapping, "27")

        assertEquals(3, result.invoices.size)
        val inv = result.invoices.find { it.invoiceNumber == "VCH-2026-01" }
        assertNotNull(inv)
        assertEquals("27AAACS1234M1Z2", inv!!.gstin)
        assertEquals("27", inv.posStateCode)
    }

    @Test
    fun testBusyAccountingFormatDetection() {
        val sample = Gstr1ExcelImportEngine.getSampleData(Gstr1ExcelImportEngine.TaxSoftwareFormat.BusyAccounting)
        val (detectedFormat, mapping) = Gstr1ExcelImportEngine.detectFormat(sample.headers)

        assertEquals(Gstr1ExcelImportEngine.TaxSoftwareFormat.BusyAccounting, detectedFormat)
        val result = Gstr1ExcelImportEngine.parseInvoices(sample, detectedFormat, mapping, "27")

        assertEquals(3, result.invoices.size)
    }

    @Test
    fun testCsvStreamParsingWithQuotesAndCommas() {
        val csvContent = """
            "GSTIN/UIN of Recipient","Receiver Name","Invoice Number","Invoice date","Invoice Value","Place Of Supply","Rate","Taxable Value"
            "27AAACS1234M1Z2","Sai Balaji, Kirana Mart","INV-9901","01-10-2026","1,18,000.00","27-Maharashtra","18","1,00,000.00"
            "24AABCG5544K1Z8","Gujarat Royal Traders","INV-9902","02-10-2026","56,000.00","24-Gujarat","12","50,000.00"
        """.trimIndent()

        val parsed = Gstr1ExcelImportEngine.parseSpreadsheetStream(
            ByteArrayInputStream(csvContent.toByteArray(Charsets.UTF_8)),
            "invoices.csv"
        )

        assertEquals(8, parsed.headers.size)
        assertEquals(2, parsed.rows.size)
        assertEquals("Sai Balaji, Kirana Mart", parsed.rows[0][1]) // Commas preserved inside quotes
        assertEquals("INV-9901", parsed.rows[0][2])
    }

    @Test
    fun testComputeStatutoryGstr1WithPrefilledExcelInvoices() {
        val profile = BusinessProfile(
            businessName = "Pabaru Enterprises",
            gstin = "27AAACP0011A1Z0",
            homeStateCode = "27"
        )

        val sample = Gstr1ExcelImportEngine.getSampleData(Gstr1ExcelImportEngine.TaxSoftwareFormat.GovtOfflineTool)
        val (_, mapping) = Gstr1ExcelImportEngine.detectFormat(sample.headers)
        val importResult = Gstr1ExcelImportEngine.parseInvoices(sample, Gstr1ExcelImportEngine.TaxSoftwareFormat.GovtOfflineTool, mapping, "27")

        val gstr1 = CmaAndGstEngine.computeStatutoryGstr1(
            transactions = emptyList(),
            parties = emptyList(),
            profile = profile,
            periodFilter = CmaAndGstEngine.GstPeriodFilter.AllTime,
            prefilledInvoices = importResult.invoices
        )

        assertEquals(5, gstr1.b2bCount)
        assertEquals(5, gstr1.b2bInvoices.size)
        assertEquals(5, gstr1.prefilledExcelCount)
        assertTrue(gstr1.b2bInvoices.all { it.isPrefilledFromExcel })
        assertEquals(importResult.totalTaxablePaise, gstr1.totalTaxablePaise)
        assertEquals(importResult.totalTaxPaise, gstr1.totalTaxPaise)

        // Verify Portal JSON export contains the prefilled invoices
        val jsonStr = GstPortalJsonEngine.generateGstr1Json(profile, gstr1, CmaAndGstEngine.GstPeriodFilter.AllTime)
        assertNotNull(jsonStr)
        val rootJson = JSONObject(jsonStr)
        assertEquals("27AAACP0011A1Z0", rootJson.getString("gstin"))
        val b2bArray = rootJson.getJSONArray("b2b")
        assertTrue(b2bArray.length() > 0)
    }
}
