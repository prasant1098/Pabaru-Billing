import JSZip from 'jszip';
import { GstSlabs } from '../types/models';
import {
  calculateGstBps,
  isValidGstinFormat,
  normalizeDate,
  parsePaiseFromRupees,
} from './financialEngine';

export interface SpreadsheetRawData {
  sheetName: string;
  headers: string[];
  rows: string[][];
  totalRawRows: number;
}

export type TaxSoftwareFormat =
  | 'AutoDetect'
  | 'GovtOfflineTool'
  | 'ClearTax'
  | 'ZohoBooks'
  | 'TallyPrime'
  | 'BusyAccounting'
  | 'Custom';

export const TAX_SOFTWARE_META: Record<
  TaxSoftwareFormat,
  { displayName: string; description: string }
> = {
  AutoDetect: {
    displayName: 'Auto-Detect Format',
    description: 'Automatically scans headers and matches software pattern',
  },
  GovtOfflineTool: {
    displayName: 'Govt GST Offline Tool',
    description: 'Official GST Portal Excel Utility (b2b sheet format)',
  },
  ClearTax: {
    displayName: 'ClearTax GSTR-1',
    description: 'ClearTax GSTR-1 outward sales register export',
  },
  ZohoBooks: {
    displayName: 'Zoho Books Outward',
    description: 'Zoho Books GST sales report with tax rate split',
  },
  TallyPrime: {
    displayName: 'TallyPrime / ERP 9',
    description: 'Tally GSTR-1 B2B outward vouchers export',
  },
  BusyAccounting: {
    displayName: 'Busy Accounting',
    description: 'Busy GST sales register with bill particulars',
  },
  Custom: {
    displayName: 'Custom Column Mapping',
    description: 'Manually map Excel columns to GSTR-1 statutory fields',
  },
};

export interface ColumnMapping {
  gstinCol: number;
  nameCol: number;
  invoiceNoCol: number;
  dateCol: number;
  posCol: number;
  taxableCol: number;
  rateCol: number;
  cgstCol: number;
  sgstCol: number;
  igstCol: number;
  totalCol: number;
  hsnCol: number;
}

export interface ImportedGstr1Invoice {
  id: string;
  gstin: string;
  partyName: string;
  invoiceNumber: string;
  date: string;
  posStateCode: string;
  taxablePaise: number;
  gstRatePercent: number;
  cgstPaise: number;
  sgstPaise: number;
  igstPaise: number;
  totalPaise: number;
  isB2b: boolean;
  isInterstate: boolean;
  hsnCode: string;
  validationWarnings: string[];
  isSelected: boolean;
  gstRateBps: number;
}

export interface Gstr1ImportResult {
  detectedFormat: TaxSoftwareFormat;
  mappingUsed: ColumnMapping;
  invoices: ImportedGstr1Invoice[];
  totalTaxablePaise: number;
  totalCgstPaise: number;
  totalSgstPaise: number;
  totalIgstPaise: number;
  totalTaxPaise: number;
  b2bCount: number;
  b2cCount: number;
  invalidCount: number;
  rawRowCount: number;
}

export function parseDelimitedRecords(text: string, delimiter: string): string[][] {
  const records: string[][] = [];
  let currentRow: string[] = [];
  let cur = '';
  let inQuotes = false;
  for (let i = 0; i < text.length; i++) {
    const c = text[i];
    if (c === '"') {
      if (inQuotes && i + 1 < text.length && text[i + 1] === '"') {
        cur += '"';
        i++;
      } else {
        inQuotes = !inQuotes;
      }
    } else if (c === delimiter && !inQuotes) {
      currentRow.push(cur.trim());
      cur = '';
    } else if ((c === '\r' || c === '\n') && !inQuotes) {
      if (c === '\r' && i + 1 < text.length && text[i + 1] === '\n') i++;
      currentRow.push(cur.trim());
      cur = '';
      if (currentRow.some((cell) => cell.trim() !== '')) records.push(currentRow);
      currentRow = [];
    } else {
      cur += c;
    }
  }
  if (cur !== '' || currentRow.length > 0) {
    currentRow.push(cur.trim());
    if (currentRow.some((cell) => cell.trim() !== '')) records.push(currentRow);
  }
  return records;
}

function colRefToIndex(ref: string): number {
  const letters = ref.match(/^[A-Za-z]+/)?.[0]?.toUpperCase() || '';
  if (!letters) return 0;
  let index = 0;
  for (let i = 0; i < letters.length; i++) {
    index = index * 26 + (letters.charCodeAt(i) - 64);
  }
  return Math.max(0, index - 1);
}

export async function parseSpreadsheetFile(file: File): Promise<SpreadsheetRawData> {
  const buf = await file.arrayBuffer();
  const bytes = new Uint8Array(buf);
  const isZip =
    bytes.length >= 4 &&
    bytes[0] === 0x50 &&
    bytes[1] === 0x4b &&
    bytes[2] === 0x03 &&
    bytes[3] === 0x04;

  if (!isZip) {
    const text = new TextDecoder().decode(bytes).replace(/^\uFEFF/, '');
    const sample = text.split(/\r?\n/).slice(0, 5).join('\n');
    const commas = (sample.match(/,/g) || []).length;
    const tabs = (sample.match(/\t/g) || []).length;
    const semis = (sample.match(/;/g) || []).length;
    const delimiter = tabs > commas && tabs > semis ? '\t' : semis > commas && semis > tabs ? ';' : ',';
    const parsedRows = parseDelimitedRecords(text, delimiter);
    if (parsedRows.length === 0) return { sheetName: 'CSV', headers: [], rows: [], totalRawRows: 0 };
    let headerIdx = 0;
    for (let i = 0; i < parsedRows.length; i++) {
      if (parsedRows[i].filter((c) => c.trim() !== '').length >= 2) {
        headerIdx = i;
        break;
      }
    }
    const headers = parsedRows[headerIdx].map((h) => h.trim());
    const dataRows = parsedRows.slice(headerIdx + 1).filter((r) => r.some((c) => c.trim() !== ''));
    return {
      sheetName: 'CSV/Spreadsheet',
      headers,
      rows: dataRows,
      totalRawRows: dataRows.length,
    };
  }

  const zip = await JSZip.loadAsync(bytes);
  const parser = new DOMParser();

  const sharedStrings: string[] = [];
  const ssFile = zip.file('xl/sharedStrings.xml');
  if (ssFile) {
    const ssXml = await ssFile.async('string');
    const doc = parser.parseFromString(ssXml, 'application/xml');
    const siNodes = Array.from(doc.getElementsByTagName('si'));
    for (const si of siNodes) {
      const tNodes = Array.from(si.getElementsByTagName('t'));
      sharedStrings.push(tNodes.map((t) => t.textContent || '').join(''));
    }
  }

  const relMap = new Map<string, string>();
  const relFile = zip.file('xl/_rels/workbook.xml.rels');
  if (relFile) {
    const relXml = await relFile.async('string');
    const doc = parser.parseFromString(relXml, 'application/xml');
    for (const rel of Array.from(doc.getElementsByTagName('Relationship'))) {
      const id = rel.getAttribute('Id') || '';
      const target = rel.getAttribute('Target') || '';
      if (id && target) {
        const norm = target.startsWith('/') ? target.slice(1) : target.startsWith('xl/') ? target : `xl/${target}`;
        relMap.set(id, norm);
      }
    }
  }

  let targetPath: string | null = null;
  let sheetName = 'Sheet1';
  const wbFile = zip.file('xl/workbook.xml');
  if (wbFile) {
    const wbXml = await wbFile.async('string');
    const doc = parser.parseFromString(wbXml, 'application/xml');
    const sheets = Array.from(doc.getElementsByTagName('sheet'));
    for (let i = 0; i < sheets.length; i++) {
      const sh = sheets[i];
      const name = sh.getAttribute('name') || `Sheet${i + 1}`;
      const rId = sh.getAttribute('r:id') || sh.getAttribute('id') || `rId${i + 1}`;
      const path = relMap.get(rId) || `xl/worksheets/sheet${i + 1}.xml`;
      if (!targetPath && zip.file(path)) {
        targetPath = path;
        sheetName = name;
      }
      if (/b2b|sale/i.test(name) && zip.file(path)) {
        targetPath = path;
        sheetName = name;
        break;
      }
    }
  }

  if (!targetPath) {
    targetPath =
      Object.keys(zip.files).find((k) => k.startsWith('xl/worksheets/') && k.endsWith('.xml')) || null;
  }
  if (!targetPath) throw new Error('No worksheet found in XLSX archive');

  const sheetXml = await zip.file(targetPath)!.async('string');
  const sDoc = parser.parseFromString(sheetXml, 'application/xml');
  const rowNodes = Array.from(sDoc.getElementsByTagName('row'));
  const rows: string[][] = [];
  for (const rNode of rowNodes) {
    const cellMap = new Map<number, string>();
    let maxCol = 0;
    for (const cNode of Array.from(rNode.getElementsByTagName('c'))) {
      const ref = cNode.getAttribute('r') || '';
      const type = cNode.getAttribute('t') || '';
      const col = colRefToIndex(ref);
      maxCol = Math.max(maxCol, col);
      const vNode = cNode.getElementsByTagName('v')[0];
      const tNode = cNode.getElementsByTagName('t')[0];
      let val = '';
      if (vNode) {
        const rawV = vNode.textContent || '';
        if (type === 's') {
          const idx = Number.parseInt(rawV, 10);
          val = Number.isFinite(idx) && sharedStrings[idx] !== undefined ? sharedStrings[idx] : rawV;
        } else val = rawV;
      } else if (tNode) {
        val = tNode.textContent || '';
      }
      cellMap.set(col, val);
    }
    if (cellMap.size > 0) {
      const rowArr: string[] = [];
      for (let c = 0; c <= maxCol; c++) rowArr.push(cellMap.get(c) || '');
      rows.push(rowArr);
    }
  }

  if (rows.length === 0) return { sheetName, headers: [], rows: [], totalRawRows: 0 };
  let headerIdx = 0;
  for (let i = 0; i < rows.length; i++) {
    if (rows[i].filter((c) => c.trim() !== '').length >= 3) {
      headerIdx = i;
      break;
    }
  }
  const headers = rows[headerIdx].map((h) => h.trim());
  const dataRows = rows.slice(headerIdx + 1).filter((r) => r.some((c) => c.trim() !== ''));
  return { sheetName, headers, rows: dataRows, totalRawRows: dataRows.length };
}

function normalizeHeader(h: string): string {
  return h.toLowerCase().replace(/[^a-z0-9]/g, '');
}

export function detectGstr1Format(headers: string[]): {
  format: TaxSoftwareFormat;
  mapping: ColumnMapping;
} {
  let gstinCol = -1;
  let nameCol = -1;
  let invoiceNoCol = -1;
  let dateCol = -1;
  let posCol = -1;
  let taxableCol = -1;
  let rateCol = -1;
  let cgstCol = -1;
  let sgstCol = -1;
  let igstCol = -1;
  let totalCol = -1;
  let hsnCol = -1;

  let govtScore = 0;
  let clearTaxScore = 0;
  let zohoScore = 0;
  let tallyScore = 0;
  let busyScore = 0;

  headers.forEach((raw, idx) => {
    const n = normalizeHeader(raw);
    if (gstinCol === -1 && (n.includes('gstin') || n.includes('ctin') || n.includes('uin'))) gstinCol = idx;
    if (
      nameCol === -1 &&
      (n.includes('receivername') ||
        n.includes('partyname') ||
        n.includes('customername') ||
        n.includes('legalname') ||
        n.includes('tradename') ||
        n.includes('particulars') ||
        n.includes('accountname'))
    ) {
      nameCol = idx;
    }
    if (
      invoiceNoCol === -1 &&
      (n.includes('invoicenumber') ||
        n.includes('invoiceno') ||
        n.includes('inum') ||
        n.includes('voucherno') ||
        n.includes('vchno') ||
        n.includes('billno') ||
        n === 'invoice')
    ) {
      invoiceNoCol = idx;
    }
    if (
      dateCol === -1 &&
      (n.includes('invoicedate') ||
        n.includes('vchdate') ||
        n.includes('voucherdate') ||
        n.includes('billdate') ||
        n.includes('date') ||
        n === 'idt')
    ) {
      dateCol = idx;
    }
    if (
      posCol === -1 &&
      (n.includes('placeofsupply') ||
        n === 'pos' ||
        n.includes('statename') ||
        n.includes('statecode') ||
        n.includes('statepos') ||
        n.includes('destination'))
    ) {
      posCol = idx;
    }
    if (
      taxableCol === -1 &&
      (n.includes('taxablevalue') ||
        n.includes('taxableamount') ||
        n.includes('taxableamt') ||
        n.includes('assessablevalue') ||
        n === 'taxable' ||
        n === 'txval')
    ) {
      taxableCol = idx;
    }
    if (
      rateCol === -1 &&
      (n.includes('taxrate') ||
        n.includes('gstrate') ||
        n.includes('itemtax') ||
        n.includes('rate') ||
        n.includes('applicableoftaxrate'))
    ) {
      rateCol = idx;
    }
    if (cgstCol === -1 && (n.includes('cgst') || n.includes('centraltax') || n === 'camt')) cgstCol = idx;
    if (
      sgstCol === -1 &&
      (n.includes('sgst') || n.includes('statetax') || n === 'samt' || n.includes('utgst'))
    ) {
      sgstCol = idx;
    }
    if (igstCol === -1 && (n.includes('igst') || n.includes('integratedtax') || n === 'iamt')) igstCol = idx;
    if (
      totalCol === -1 &&
      (n.includes('invoicevalue') ||
        n.includes('totalvalue') ||
        n.includes('totalamount') ||
        n.includes('grossvalue') ||
        n.includes('billamount') ||
        n === 'total' ||
        n === 'val')
    ) {
      totalCol = idx;
    }
    if (hsnCol === -1 && (n.includes('hsn') || n.includes('sac'))) hsnCol = idx;

    if (n.includes('gstinuinofrecipient') || n.includes('receivername') || n.includes('applicableoftaxrate')) govtScore += 2;
    if (n === 'partyname' || n === 'totalvalue' || n.includes('cleartax')) clearTaxScore += 1;
    if (n.includes('customername') || n.includes('customergstin') || n.includes('itemtax')) zohoScore += 2;
    if (n.includes('voucherno') || n.includes('vchno') || n.includes('particulars') || n.includes('assessablevalue')) tallyScore += 2;
    if (n.includes('billno') || n.includes('accountname') || n.includes('billamount') || n.includes('taxableamt')) busyScore += 2;
  });

  let format: TaxSoftwareFormat = 'Custom';
  if (govtScore >= 2) format = 'GovtOfflineTool';
  else if (zohoScore >= 2) format = 'ZohoBooks';
  else if (tallyScore >= 2) format = 'TallyPrime';
  else if (busyScore >= 2) format = 'BusyAccounting';
  else if (clearTaxScore >= 2) format = 'ClearTax';
  else if (gstinCol !== -1 && invoiceNoCol !== -1 && taxableCol !== -1) format = 'GovtOfflineTool';

  return {
    format,
    mapping: {
      gstinCol,
      nameCol,
      invoiceNoCol,
      dateCol,
      posCol,
      taxableCol,
      rateCol,
      cgstCol,
      sgstCol,
      igstCol,
      totalCol,
      hsnCol,
    },
  };
}

function parseExcelDateString(str: string): string {
  const trimmed = str.trim();
  if (!trimmed) return '';
  if (/^\d+(\.\d+)?$/.test(trimmed)) {
    const serial = Number.parseFloat(trimmed);
    if (serial > 30000 && serial < 60000) {
      const utcDays = Math.floor(serial) - 25569;
      return new Date(utcDays * 86_400_000).toISOString().slice(0, 10);
    }
  }
  const direct = normalizeDate(trimmed);
  if (direct) return direct;
  const mmmMatch = trimmed.match(/^(\d{1,2})[-/]([A-Za-z]{3})[-/](\d{4})$/);
  if (mmmMatch) {
    const months: Record<string, string> = {
      jan: '01', feb: '02', mar: '03', apr: '04', may: '05', jun: '06',
      jul: '07', aug: '08', sep: '09', oct: '10', nov: '11', dec: '12',
    };
    const m = months[mmmMatch[2].toLowerCase()];
    if (m) return `${mmmMatch[3]}-${m}-${mmmMatch[1].padStart(2, '0')}`;
  }
  return '';
}

function parseStateCode(posStr: string, gstin: string, fallback: string): string {
  const t = posStr.trim();
  const lead = t.slice(0, 2);
  if (/^\d{2}$/.test(lead)) return lead;
  const l = t.toLowerCase();
  if (l.includes('maharashtra')) return '27';
  if (l.includes('gujarat')) return '24';
  if (l.includes('delhi')) return '07';
  if (l.includes('karnataka')) return '29';
  if (l.includes('tamil')) return '33';
  if (l.includes('uttar pradesh') || l === 'up') return '09';
  if (l.includes('west bengal') || l.includes('bengal')) return '19';
  if (l.includes('rajasthan')) return '08';
  if (l.includes('madhya pradesh') || l === 'mp') return '23';
  if (l.includes('punjab')) return '03';
  if (l.includes('haryana')) return '06';
  if (l.includes('telangana')) return '36';
  if (l.includes('andhra')) return '37';
  if (l.includes('kerala')) return '32';
  if (l.includes('bihar')) return '10';
  if (/^\d{2}/.test(gstin)) return gstin.slice(0, 2);
  return fallback || '27';
}

export function parseGstr1Invoices(
  rawData: SpreadsheetRawData,
  format: TaxSoftwareFormat,
  customMapping?: ColumnMapping,
  homeStateCode = '27'
): Gstr1ImportResult {
  const mapping = customMapping || detectGstr1Format(rawData.headers).mapping;
  const invoices: ImportedGstr1Invoice[] = [];
  let totalTaxable = 0;
  let totalCgst = 0;
  let totalSgst = 0;
  let totalIgst = 0;
  let b2bCount = 0;
  let b2cCount = 0;
  let invalidCount = 0;
  const seen = new Set<string>();

  rawData.rows.forEach((row, rowIdx) => {
    const getCell = (col: number) => (col >= 0 && col < row.length ? row[col].trim() : '');
    const rawGstin = getCell(mapping.gstinCol).toUpperCase().replace(/\s+/g, '');
    const rawName = getCell(mapping.nameCol) || `Customer #${rowIdx + 1}`;
    const rawInvNo = getCell(mapping.invoiceNoCol) || `IMP-INV-${rowIdx + 1}`;
    const rawDateCell = getCell(mapping.dateCol);
    const rawDate = parseExcelDateString(rawDateCell);
    const rawPosCell = getCell(mapping.posCol);
    const rawPos = parseStateCode(rawPosCell, rawGstin, homeStateCode);
    const taxablePaise = parsePaiseFromRupees(getCell(mapping.taxableCol));
    const rateCell = getCell(mapping.rateCol);
    const parsedRate = Number.parseFloat(rateCell.replace(/%/g, '').trim());
    const rateBps =
      Number.isFinite(parsedRate) && parsedRate >= 0
        ? Math.min(10000, Math.round(parsedRate * 100))
        : 1800;
    const ratePercent = Math.round(rateBps / 100);

    if (taxablePaise <= 0) {
      invalidCount++;
      return;
    }

    const warnings: string[] = [];
    if (!rawDate) warnings.push(`Invalid or missing date '${rawDateCell}'.`);
    if (!rawPosCell && !rawGstin) warnings.push(`POS missing; defaulted to ${homeStateCode}.`);
    if (rateCell && !Number.isFinite(parsedRate)) {
      warnings.push(`Unrecognized GST rate '${rateCell}'; defaulted to ${GstSlabs.label(rateBps)}.`);
    }
    const dupKey = `${rawGstin || 'B2C'}|${rawInvNo.toUpperCase()}`;
    if (seen.has(dupKey)) warnings.push(`Duplicate invoice number '${rawInvNo}'.`);
    seen.add(dupKey);

    const gstinOk = isValidGstinFormat(rawGstin);
    const isB2b = rawGstin !== '' && gstinOk;
    if (rawGstin && !gstinOk) warnings.push(`GSTIN '${rawGstin}' does not match statutory format.`);
    const isInterstate = rawPos !== '' && rawPos !== homeStateCode;

    const extC = parsePaiseFromRupees(getCell(mapping.cgstCol));
    const extS = parsePaiseFromRupees(getCell(mapping.sgstCol));
    const extI = parsePaiseFromRupees(getCell(mapping.igstCol));
    const computed = calculateGstBps(taxablePaise, rateBps, isInterstate);

    let cgstPaise = computed.cgstPaise;
    let sgstPaise = computed.sgstPaise;
    let igstPaise = computed.igstPaise;
    if (extC > 0 || extS > 0 || extI > 0) {
      cgstPaise = extC;
      sgstPaise = extS;
      igstPaise = extI;
    }

    const rawTotal = parsePaiseFromRupees(getCell(mapping.totalCol));
    const totalPaise = rawTotal > 0 ? rawTotal : taxablePaise + cgstPaise + sgstPaise + igstPaise;

    invoices.push({
      id: `IMP-${Date.now()}-${rowIdx}`,
      gstin: rawGstin,
      partyName: rawName,
      invoiceNumber: rawInvNo,
      date: rawDate,
      posStateCode: rawPos,
      taxablePaise,
      gstRatePercent: ratePercent,
      cgstPaise,
      sgstPaise,
      igstPaise,
      totalPaise,
      isB2b,
      isInterstate,
      hsnCode: getCell(mapping.hsnCol) || '9999',
      validationWarnings: warnings,
      isSelected: true,
      gstRateBps: rateBps,
    });

    totalTaxable += taxablePaise;
    totalCgst += cgstPaise;
    totalSgst += sgstPaise;
    totalIgst += igstPaise;
    if (isB2b) b2bCount++;
    else b2cCount++;
  });

  return {
    detectedFormat: format,
    mappingUsed: mapping,
    invoices,
    totalTaxablePaise: totalTaxable,
    totalCgstPaise: totalCgst,
    totalSgstPaise: totalSgst,
    totalIgstPaise: totalIgst,
    totalTaxPaise: totalCgst + totalSgst + totalIgst,
    b2bCount,
    b2cCount,
    invalidCount,
    rawRowCount: rawData.rows.length,
  };
}

export function getSampleGstr1Data(format: TaxSoftwareFormat): SpreadsheetRawData {
  if (format === 'ClearTax') {
    return {
      sheetName: 'ClearTax_Sales',
      headers: ['GSTIN', 'Party Name', 'Invoice Number', 'Invoice Date', 'Total Value', 'Place of Supply', 'Tax Rate', 'Taxable Value', 'CGST', 'SGST', 'IGST', 'Cess'],
      rows: [
        ['27AAACS1234M1Z2', 'Sai Balaji Kirana Dadar', 'CT-26-001', '2026-10-01', '118000', '27', '18', '100000', '9000', '9000', '0', '0'],
        ['24AABCG5544K1Z8', 'Gujarat Royal APMC Depot', 'CT-26-002', '2026-10-02', '56000', '24', '12', '50000', '0', '0', '6000', '0'],
        ['29AABCR1122Q1Z9', 'Bangalore Grain Mart Pvt Ltd', 'CT-26-003', '2026-10-03', '29500', '29', '18', '25000', '0', '0', '4500', '0'],
        ['27AABCO9988D1Z4', 'Omkar Store Thane West', 'CT-26-004', '2026-10-04', '63000', '27', '5', '60000', '1500', '1500', '0', '0'],
      ],
      totalRawRows: 4,
    };
  }
  if (format === 'ZohoBooks') {
    return {
      sheetName: 'Zoho_Invoices',
      headers: ['Customer GSTIN', 'Customer Name', 'Invoice#', 'Invoice Date', 'Total', 'Place of Supply', 'Item Tax %', 'Taxable Amount', 'IGST Amount', 'CGST Amount', 'SGST Amount'],
      rows: [
        ['27AAACS1234M1Z2', 'Sai Balaji Provisions', 'INV-ZOHO-01', '01/10/2026', '118000.00', 'Maharashtra', '18%', '100000.00', '0.00', '9000.00', '9000.00'],
        ['24AABCG5544K1Z8', 'Gujarat Royal Spices', 'INV-ZOHO-02', '02/10/2026', '112000.00', 'Gujarat', '12%', '100000.00', '12000.00', '0.00', '0.00'],
      ],
      totalRawRows: 2,
    };
  }
  if (format === 'TallyPrime') {
    return {
      sheetName: 'Tally_GSTR1',
      headers: ['GSTIN/UIN', 'Party Name', 'Voucher No.', 'Date', 'Total Invoice Value', 'State Name', 'Rate', 'Taxable Value', 'Central Tax Amount', 'State Tax Amount', 'Integrated Tax Amount'],
      rows: [
        ['27AAACS1234M1Z2', 'Sai Balaji Kirana', 'VCH-2026-01', '01-Oct-2026', '118000.00', 'Maharashtra', '18%', '100000.00', '9000.00', '9000.00', '0.00'],
        ['24AABCG5544K1Z8', 'Gujarat Royal Spices', 'VCH-2026-02', '02-Oct-2026', '56000.00', 'Gujarat', '12%', '50000.00', '0.00', '0.00', '6000.00'],
      ],
      totalRawRows: 2,
    };
  }
  return {
    sheetName: 'b2b',
    headers: ['GSTIN/UIN of Recipient', 'Receiver Name', 'Invoice Number', 'Invoice date', 'Invoice Value', 'Place Of Supply', 'Reverse Charge', 'Applicable % of Tax Rate', 'Invoice Type', 'E-Commerce GSTIN', 'Rate', 'Taxable Value', 'Cess Amount'],
    rows: [
      ['27AAACS1234M1Z2', 'Sai Balaji Supermarket & Provisions', 'INV/2026/0101', '01-10-2026', '118000.00', '27-Maharashtra', 'N', '', 'Regular', '', '18', '100000.00', '0.00'],
      ['24AABCG5544K1Z8', 'Gujarat Royal Spices & Agro Trd', 'INV/2026/0102', '02-10-2026', '56000.00', '24-Gujarat', 'N', '', 'Regular', '', '12', '50000.00', '0.00'],
      ['07AAACG9921B1Z3', 'Delhi Capital Tech Supplies Ltd', 'INV/2026/0103', '03-10-2026', '23600.00', '07-Delhi', 'N', '', 'Regular', '', '18', '20000.00', '0.00'],
      ['27AABCO9988D1Z4', 'Omkar Retail Mart Dadar', 'INV/2026/0104', '04-10-2026', '84000.00', '27-Maharashtra', 'N', '', 'Regular', '', '5', '80000.00', '0.00'],
    ],
    totalRawRows: 4,
  };
}
