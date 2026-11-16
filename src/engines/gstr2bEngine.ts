import { Party, Transaction } from '../types/models';
import {
  calculateGstBps,
  isValidGstinFormat,
  normalizeDate,
  parsePaiseFromRupees,
} from './financialEngine';
import { parseDelimitedRecords } from './gstr1ImportEngine';

export type Gstr2bMatchStatus =
  | 'FullyMatched'
  | 'ValueMismatch'
  | 'MissingInBooks'
  | 'MissingIn2B'
  | 'IneligibleITC';

export interface Gstr2bInvoice {
  supplierGstin: string;
  supplierTradeName: string;
  invoiceNumber: string;
  invoiceDate: string;
  invoiceType: 'RegularB2B' | 'AmendedB2BA' | 'CreditDebitNote' | 'ReverseCharge';
  invoiceValuePaise: number;
  taxableValuePaise: number;
  cgstPaise: number;
  sgstPaise: number;
  igstPaise: number;
  cessPaise: number;
  posStateCode: string;
  isReverseCharge: boolean;
  itcAvailable: boolean;
  itcBlockedReason: string;
  totalTaxPaise: number;
}

export interface Gstr2bReconciliationItem {
  id: string;
  matchStatus: Gstr2bMatchStatus;
  supplierGstin: string;
  supplierName: string;
  invoiceNumber: string;
  invoiceDate: string;
  gstr2bTaxablePaise: number;
  gstr2bCgstPaise: number;
  gstr2bSgstPaise: number;
  gstr2bIgstPaise: number;
  gstr2bTotalTaxPaise: number;
  gstr2bTotalValuePaise: number;
  itcAvailable: boolean;
  itcBlockedReason: string;
  booksTransactionId: string | null;
  booksTaxablePaise: number;
  booksCgstPaise: number;
  booksSgstPaise: number;
  booksIgstPaise: number;
  booksTotalTaxPaise: number;
  booksTotalValuePaise: number;
  diffTaxablePaise: number;
  diffTaxPaise: number;
  remarks: string;
}

export interface Gstr2bReconciliationSummary {
  totalGstr2bCount: number;
  totalGstr2bTaxablePaise: number;
  totalGstr2bItcAvailablePaise: number;
  totalGstr2bItcBlockedPaise: number;
  totalBooksPurchaseCount: number;
  totalBooksTaxablePaise: number;
  totalBooksItcClaimedPaise: number;
  matchedCount: number;
  matchedItcPaise: number;
  valueMismatchCount: number;
  valueMismatchTaxVariancePaise: number;
  missingInBooksCount: number;
  missingInBooksUnclaimedItcPaise: number;
  missingIn2bCount: number;
  missingIn2bAtRiskItcPaise: number;
  ineligibleItcCount: number;
  ineligibleItcPaise: number;
}

export const SMART_TOLERANCE_PAISE = 100;

export function normalizeInvoiceNum(raw: string): string {
  return raw
    .trim()
    .toUpperCase()
    .split(/[^A-Z0-9]+/)
    .filter(Boolean)
    .map((seg) => (/^\d+$/.test(seg) ? seg.replace(/^0+/, '') || '0' : seg))
    .join('');
}

function separatorPreservingNorm(raw: string): string {
  return raw
    .trim()
    .toUpperCase()
    .split(/[^A-Z0-9]+/)
    .filter(Boolean)
    .map((seg) => (/^\d+$/.test(seg) ? seg.replace(/^0+/, '') || '0' : seg))
    .join('/');
}

export function reconcileGstr2b(
  gstr2bList: Gstr2bInvoice[],
  transactions: Transaction[],
  parties: Party[],
  homeStateCode = '27'
): { items: Gstr2bReconciliationItem[]; summary: Gstr2bReconciliationSummary } {
  const partyMap = new Map(parties.map((p) => [p.id, p]));
  const bookPurchases = transactions
    .filter((t) => !t.isVoided && t.type === 'Purchase')
    .map((t) => {
      const party = t.partyId ? partyMap.get(t.partyId) : undefined;
      const gstin = party?.gstin.trim() || '';
      const partyName = party?.name || 'Supplier';
      let taxable = 0;
      let cgst = 0;
      let sgst = 0;
      let igst = 0;
      if (t.items.length > 0) {
        taxable = t.items.reduce((s, i) => s + i.taxableAmountPaise, 0);
        cgst = t.items.reduce((s, i) => s + i.cgstPaise, 0);
        sgst = t.items.reduce((s, i) => s + i.sgstPaise, 0);
        igst = t.items.reduce((s, i) => s + i.igstPaise, 0);
      } else {
        taxable = Math.floor((t.amountPaise * 10000 + 5900) / 11800);
        const taxTotal = t.amountPaise - taxable;
        const isInter = isValidGstinFormat(gstin) && !gstin.startsWith(homeStateCode || '27');
        if (isInter) igst = taxTotal;
        else {
          cgst = Math.floor(taxTotal / 2);
          sgst = taxTotal - cgst;
        }
      }
      return {
        transactionId: t.id,
        invoiceNumber: t.invoiceNumber || t.id,
        invoiceDate: t.date,
        partyName,
        partyGstin: gstin,
        taxableValuePaise: taxable,
        cgstPaise: cgst,
        sgstPaise: sgst,
        igstPaise: igst,
        totalTaxPaise: cgst + sgst + igst,
        totalAmountPaise: t.amountPaise,
      };
    });

  const items: Gstr2bReconciliationItem[] = [];
  const matchedBookIds = new Set<string>();

  gstr2bList.forEach((inv2b, idx) => {
    const normGstin = inv2b.supplierGstin.trim().toUpperCase();
    const raw2bInv = inv2b.invoiceNumber.trim().toUpperCase();
    const sep2bInv = separatorPreservingNorm(inv2b.invoiceNumber);
    const norm2bInv = normalizeInvoiceNum(inv2b.invoiceNumber);

    if (!inv2b.itcAvailable) {
      items.push({
        id: `2B-INEL-${idx}`,
        matchStatus: 'IneligibleITC',
        supplierGstin: inv2b.supplierGstin,
        supplierName: inv2b.supplierTradeName,
        invoiceNumber: inv2b.invoiceNumber,
        invoiceDate: inv2b.invoiceDate,
        gstr2bTaxablePaise: inv2b.taxableValuePaise,
        gstr2bCgstPaise: inv2b.cgstPaise,
        gstr2bSgstPaise: inv2b.sgstPaise,
        gstr2bIgstPaise: inv2b.igstPaise,
        gstr2bTotalTaxPaise: inv2b.totalTaxPaise,
        gstr2bTotalValuePaise: inv2b.invoiceValuePaise,
        itcAvailable: false,
        itcBlockedReason: inv2b.itcBlockedReason || 'Ineligible under Sec 17(5)',
        booksTransactionId: null,
        booksTaxablePaise: 0,
        booksCgstPaise: 0,
        booksSgstPaise: 0,
        booksIgstPaise: 0,
        booksTotalTaxPaise: 0,
        booksTotalValuePaise: 0,
        diffTaxablePaise: 0,
        diffTaxPaise: 0,
        remarks: 'Ineligible ITC marked by GST Portal',
      });
      return;
    }

    let bestCandidate: (typeof bookPurchases)[0] | null = null;
    let bestRank = -Infinity;
    if (normGstin) {
      for (const b of bookPurchases) {
        if (matchedBookIds.has(b.transactionId)) continue;
        if (b.partyGstin.trim().toUpperCase() !== normGstin) continue;
        const rawB = b.invoiceNumber.trim().toUpperCase();
        const sepB = separatorPreservingNorm(b.invoiceNumber);
        const colB = normalizeInvoiceNum(b.invoiceNumber);
        const invScore = rawB === raw2bInv ? 30 : sepB === sep2bInv ? 20 : colB === norm2bInv ? 10 : 0;
        const amtDiff = Math.abs(b.totalAmountPaise - inv2b.invoiceValuePaise);
        if (invScore > 0 || amtDiff <= SMART_TOLERANCE_PAISE) {
          const rank = invScore * 1_000_000 - Math.min(999_999, amtDiff);
          if (rank > bestRank) {
            bestRank = rank;
            bestCandidate = b;
          }
        }
      }
    }

    if (bestCandidate) {
      matchedBookIds.add(bestCandidate.transactionId);
      const diffTaxable = inv2b.taxableValuePaise - bestCandidate.taxableValuePaise;
      const diffTax = inv2b.totalTaxPaise - bestCandidate.totalTaxPaise;
      const withinTol =
        Math.abs(diffTaxable) <= SMART_TOLERANCE_PAISE && Math.abs(diffTax) <= SMART_TOLERANCE_PAISE;
      items.push({
        id: `${withinTol ? 'MATCH' : 'MISMATCH'}-${idx}`,
        matchStatus: withinTol ? 'FullyMatched' : 'ValueMismatch',
        supplierGstin: inv2b.supplierGstin,
        supplierName: inv2b.supplierTradeName || bestCandidate.partyName,
        invoiceNumber: inv2b.invoiceNumber,
        invoiceDate: inv2b.invoiceDate,
        gstr2bTaxablePaise: inv2b.taxableValuePaise,
        gstr2bCgstPaise: inv2b.cgstPaise,
        gstr2bSgstPaise: inv2b.sgstPaise,
        gstr2bIgstPaise: inv2b.igstPaise,
        gstr2bTotalTaxPaise: inv2b.totalTaxPaise,
        gstr2bTotalValuePaise: inv2b.invoiceValuePaise,
        itcAvailable: true,
        itcBlockedReason: '',
        booksTransactionId: bestCandidate.transactionId,
        booksTaxablePaise: bestCandidate.taxableValuePaise,
        booksCgstPaise: bestCandidate.cgstPaise,
        booksSgstPaise: bestCandidate.sgstPaise,
        booksIgstPaise: bestCandidate.igstPaise,
        booksTotalTaxPaise: bestCandidate.totalTaxPaise,
        booksTotalValuePaise: bestCandidate.totalAmountPaise,
        diffTaxablePaise: diffTaxable,
        diffTaxPaise: diffTax,
        remarks: withinTol
          ? 'Fully matched within ₹1.00 tolerance. ITC compliant.'
          : `Value variance: Taxable Δ ₹${(diffTaxable / 100).toFixed(2)}, Tax Δ ₹${(diffTax / 100).toFixed(2)}`,
      });
    } else {
      items.push({
        id: `MISS-BOOKS-${idx}`,
        matchStatus: 'MissingInBooks',
        supplierGstin: inv2b.supplierGstin,
        supplierName: inv2b.supplierTradeName,
        invoiceNumber: inv2b.invoiceNumber,
        invoiceDate: inv2b.invoiceDate,
        gstr2bTaxablePaise: inv2b.taxableValuePaise,
        gstr2bCgstPaise: inv2b.cgstPaise,
        gstr2bSgstPaise: inv2b.sgstPaise,
        gstr2bIgstPaise: inv2b.igstPaise,
        gstr2bTotalTaxPaise: inv2b.totalTaxPaise,
        gstr2bTotalValuePaise: inv2b.invoiceValuePaise,
        itcAvailable: true,
        itcBlockedReason: '',
        booksTransactionId: null,
        booksTaxablePaise: 0,
        booksCgstPaise: 0,
        booksSgstPaise: 0,
        booksIgstPaise: 0,
        booksTotalTaxPaise: 0,
        booksTotalValuePaise: 0,
        diffTaxablePaise: inv2b.taxableValuePaise,
        diffTaxPaise: inv2b.totalTaxPaise,
        remarks: 'Invoice filed by supplier in GSTR-1, missing from purchase book.',
      });
    }
  });

  for (const b of bookPurchases) {
    if (!matchedBookIds.has(b.transactionId)) {
      items.push({
        id: `MISS-2B-${b.transactionId}`,
        matchStatus: 'MissingIn2B',
        supplierGstin: b.partyGstin || 'Unregistered / Unknown',
        supplierName: b.partyName,
        invoiceNumber: b.invoiceNumber,
        invoiceDate: b.invoiceDate,
        gstr2bTaxablePaise: 0,
        gstr2bCgstPaise: 0,
        gstr2bSgstPaise: 0,
        gstr2bIgstPaise: 0,
        gstr2bTotalTaxPaise: 0,
        gstr2bTotalValuePaise: 0,
        itcAvailable: false,
        itcBlockedReason: '',
        booksTransactionId: b.transactionId,
        booksTaxablePaise: b.taxableValuePaise,
        booksCgstPaise: b.cgstPaise,
        booksSgstPaise: b.sgstPaise,
        booksIgstPaise: b.igstPaise,
        booksTotalTaxPaise: b.totalTaxPaise,
        booksTotalValuePaise: b.totalAmountPaise,
        diffTaxablePaise: -b.taxableValuePaise,
        diffTaxPaise: -b.totalTaxPaise,
        remarks: 'Booked locally, supplier not filed in GSTR-2B (Rule 36(4) risk).',
      });
    }
  }

  const matched = items.filter((i) => i.matchStatus === 'FullyMatched');
  const mismatch = items.filter((i) => i.matchStatus === 'ValueMismatch');
  const missBooks = items.filter((i) => i.matchStatus === 'MissingInBooks');
  const miss2b = items.filter((i) => i.matchStatus === 'MissingIn2B');
  const inel = items.filter((i) => i.matchStatus === 'IneligibleITC');

  return {
    items,
    summary: {
      totalGstr2bCount: gstr2bList.length,
      totalGstr2bTaxablePaise: gstr2bList.reduce((s, i) => s + i.taxableValuePaise, 0),
      totalGstr2bItcAvailablePaise: gstr2bList.filter((i) => i.itcAvailable).reduce((s, i) => s + i.totalTaxPaise, 0),
      totalGstr2bItcBlockedPaise: gstr2bList.filter((i) => !i.itcAvailable).reduce((s, i) => s + i.totalTaxPaise, 0),
      totalBooksPurchaseCount: bookPurchases.length,
      totalBooksTaxablePaise: bookPurchases.reduce((s, b) => s + b.taxableValuePaise, 0),
      totalBooksItcClaimedPaise: bookPurchases.reduce((s, b) => s + b.totalTaxPaise, 0),
      matchedCount: matched.length,
      matchedItcPaise: matched.reduce((s, i) => s + i.gstr2bTotalTaxPaise, 0),
      valueMismatchCount: mismatch.length,
      valueMismatchTaxVariancePaise: mismatch.reduce((s, i) => s + Math.abs(i.diffTaxPaise), 0),
      missingInBooksCount: missBooks.length,
      missingInBooksUnclaimedItcPaise: missBooks.reduce((s, i) => s + i.gstr2bTotalTaxPaise, 0),
      missingIn2bCount: miss2b.length,
      missingIn2bAtRiskItcPaise: miss2b.reduce((s, i) => s + i.booksTotalTaxPaise, 0),
      ineligibleItcCount: inel.length,
      ineligibleItcPaise: inel.reduce((s, i) => s + i.gstr2bTotalTaxPaise, 0),
    },
  };
}

function jsonMoneyToPaise(val: unknown): number {
  if (typeof val === 'number') return Math.round(val * 100);
  if (typeof val === 'string') return parsePaiseFromRupees(val);
  return 0;
}

export function parseGstr2bFileText(content: string): Gstr2bInvoice[] {
  const trimmed = content.trim();
  if (!trimmed) return [];
  if (trimmed.startsWith('{') || trimmed.startsWith('[')) {
    try {
      const root = JSON.parse(trimmed);
      const data = root.data || root;
      const docdata = data.docdata || data;
      const result: Gstr2bInvoice[] = [];
      for (const sup of docdata.b2b || []) {
        const ctin = sup.ctin || '';
        const tradeName = sup.trdnm || sup.lgnm || sup.cname || sup.name || 'Supplier';
        for (const inv of sup.inv || []) {
          const ival = jsonMoneyToPaise(inv.val);
          let taxable = 0;
          let igst = 0;
          let cgst = 0;
          let sgst = 0;
          let cess = 0;
          if (Array.isArray(inv.items)) {
            for (const item of inv.items) {
              taxable += jsonMoneyToPaise(item.txval);
              igst += jsonMoneyToPaise(item.iamt);
              cgst += jsonMoneyToPaise(item.camt);
              sgst += jsonMoneyToPaise(item.samt);
              cess += jsonMoneyToPaise(item.csamt);
            }
          } else taxable = ival;
          result.push({
            supplierGstin: ctin,
            supplierTradeName: tradeName,
            invoiceNumber: inv.inum || '',
            invoiceDate: inv.idt || '',
            invoiceType: 'RegularB2B',
            invoiceValuePaise: ival,
            taxableValuePaise: taxable,
            cgstPaise: cgst,
            sgstPaise: sgst,
            igstPaise: igst,
            cessPaise: cess,
            posStateCode: inv.pos || '',
            isReverseCharge: String(inv.rchrg || 'N').toUpperCase() === 'Y',
            itcAvailable: String(inv.itcavl || 'Y').toUpperCase() === 'Y',
            itcBlockedReason: inv.rsn || '',
            totalTaxPaise: cgst + sgst + igst + cess,
          });
        }
      }
      return result;
    } catch {
      return [];
    }
  }

  const firstLine = trimmed.split(/\r?\n/)[0] || '';
  const delimiter = firstLine.includes('\t') && !firstLine.includes(',') ? '\t' : ',';
  const records = parseDelimitedRecords(trimmed, delimiter);
  if (records.length <= 1) return [];
  const header = records[0].map((h) => h.trim().toLowerCase());
  const gstinIdx = header.findIndex((h) => h.includes('gstin') || h.includes('ctin'));
  const nameIdx = header.findIndex((h) => h.includes('trade') || h.includes('legal') || h.includes('name'));
  const invIdx = header.findIndex((h) => h.includes('invoice') || h.includes('number') || h.includes('inum'));
  const dateIdx = header.findIndex((h) => h.includes('date') || h.includes('idt'));
  const valIdx = header.findIndex((h) => h.includes('invoice value') || h === 'val');
  const taxIdx = header.findIndex((h) => h.includes('taxable') || h === 'txval');
  const igstIdx = header.findIndex((h) => h.includes('integrated') || h === 'iamt' || h === 'igst');
  const cgstIdx = header.findIndex((h) => h.includes('central') || h === 'camt' || h === 'cgst');
  const sgstIdx = header.findIndex((h) => h.includes('state') || h === 'samt' || h === 'sgst');
  const itcIdx = header.findIndex((h) => h.includes('itc') || h === 'itcavl');

  const out: Gstr2bInvoice[] = [];
  for (let i = 1; i < records.length; i++) {
    const cols = records[i];
    if (cols.length <= 3) continue;
    const gstin = (cols[gstinIdx] || '').toUpperCase();
    if (!isValidGstinFormat(gstin)) continue;
    const inum = cols[invIdx] || '';
    if (!inum) continue;
    const ival = valIdx >= 0 ? parsePaiseFromRupees(cols[valIdx] || '') : 0;
    const taxable = (taxIdx >= 0 ? parsePaiseFromRupees(cols[taxIdx] || '') : 0) || ival;
    const igst = igstIdx >= 0 ? parsePaiseFromRupees(cols[igstIdx] || '') : 0;
    const cgst = cgstIdx >= 0 ? parsePaiseFromRupees(cols[cgstIdx] || '') : 0;
    const sgst = sgstIdx >= 0 ? parsePaiseFromRupees(cols[sgstIdx] || '') : 0;
    const itcAvail = itcIdx >= 0 ? !/^N/i.test(cols[itcIdx] || '') : true;

    out.push({
      supplierGstin: gstin,
      supplierTradeName: (cols[nameIdx] || 'Supplier').trim(),
      invoiceNumber: inum,
      invoiceDate: normalizeDate(cols[dateIdx] || '') || cols[dateIdx] || '',
      invoiceType: 'RegularB2B',
      invoiceValuePaise: ival || taxable + cgst + sgst + igst,
      taxableValuePaise: taxable,
      cgstPaise: cgst,
      sgstPaise: sgst,
      igstPaise: igst,
      cessPaise: 0,
      posStateCode: gstin.slice(0, 2),
      isReverseCharge: false,
      itcAvailable: itcAvail,
      itcBlockedReason: '',
      totalTaxPaise: cgst + sgst + igst,
    });
  }
  return out;
}

export function getSampleGstr2bInvoices(
  purchases: Transaction[],
  parties: Party[]
): Gstr2bInvoice[] {
  const result: Gstr2bInvoice[] = [];
  const partyMap = new Map(parties.map((p) => [p.id, p]));
  const actual = purchases.filter((p) => !p.isVoided && p.type === 'Purchase');

  actual.slice(0, 3).forEach((p, idx) => {
    const party = p.partyId ? partyMap.get(p.partyId) : undefined;
    const gstin = party?.gstin || `27AABCS1429B1Z${idx + 1}`;
    const taxable =
      p.items.length > 0
        ? p.items.reduce((s, it) => s + it.taxableAmountPaise, 0)
        : Math.floor((p.amountPaise * 10000 + 5900) / 11800);
    const cgst =
      p.items.length > 0
        ? p.items.reduce((s, it) => s + it.cgstPaise, 0)
        : Math.floor((p.amountPaise - taxable) / 2);
    const sgst =
      p.items.length > 0
        ? p.items.reduce((s, it) => s + it.sgstPaise, 0)
        : p.amountPaise - taxable - cgst;
    const igst = p.items.length > 0 ? p.items.reduce((s, it) => s + it.igstPaise, 0) : 0;

    result.push({
      supplierGstin: gstin,
      supplierTradeName: party?.name || 'Prime Goods Wholesalers',
      invoiceNumber: p.invoiceNumber || `PUR-2026-${idx + 101}`,
      invoiceDate: p.date,
      invoiceType: 'RegularB2B',
      invoiceValuePaise: p.amountPaise,
      taxableValuePaise: taxable,
      cgstPaise: cgst,
      sgstPaise: sgst,
      igstPaise: igst,
      cessPaise: 0,
      posStateCode: gstin.slice(0, 2),
      isReverseCharge: false,
      itcAvailable: true,
      itcBlockedReason: '',
      totalTaxPaise: cgst + sgst + igst,
    });
  });

  if (actual.length > 3) {
    const p = actual[3];
    const party = p.partyId ? partyMap.get(p.partyId) : undefined;
    const taxable = Math.floor((p.amountPaise * 10000 + 5900) / 11800) + 150000;
    const gst = calculateGstBps(taxable, 1800, false);
    result.push({
      supplierGstin: party?.gstin || '27BBBCS4321A1Z9',
      supplierTradeName: party?.name || 'Apex Logistics & Supplies',
      invoiceNumber: p.invoiceNumber || 'PUR-MISMATCH-104',
      invoiceDate: p.date,
      invoiceType: 'RegularB2B',
      invoiceValuePaise: gst.totalPaise,
      taxableValuePaise: taxable,
      cgstPaise: gst.cgstPaise,
      sgstPaise: gst.sgstPaise,
      igstPaise: 0,
      cessPaise: 0,
      posStateCode: '27',
      isReverseCharge: false,
      itcAvailable: true,
      itcBlockedReason: '',
      totalTaxPaise: gst.cgstPaise + gst.sgstPaise,
    });
  }

  result.push(
    {
      supplierGstin: '27AAACR7654C1Z3',
      supplierTradeName: 'Reliance Jio Infocomm Ltd (Telecom)',
      invoiceNumber: 'MH/MUM/2026/98214',
      invoiceDate: '2026-10-05',
      invoiceType: 'RegularB2B',
      invoiceValuePaise: 117900,
      taxableValuePaise: 99900,
      cgstPaise: 9000,
      sgstPaise: 9000,
      igstPaise: 0,
      cessPaise: 0,
      posStateCode: '27',
      isReverseCharge: false,
      itcAvailable: true,
      itcBlockedReason: '',
      totalTaxPaise: 18000,
    },
    {
      supplierGstin: '27AABCT2345K1Z8',
      supplierTradeName: 'Hotel Taj Residency (Food & Beverage)',
      invoiceNumber: 'FB/2026/1104',
      invoiceDate: '2026-10-12',
      invoiceType: 'RegularB2B',
      invoiceValuePaise: 525000,
      taxableValuePaise: 500000,
      cgstPaise: 12500,
      sgstPaise: 12500,
      igstPaise: 0,
      cessPaise: 0,
      posStateCode: '27',
      isReverseCharge: false,
      itcAvailable: false,
      itcBlockedReason: 'Blocked Credit: Food & Beverages under Sec 17(5)(b)',
      totalTaxPaise: 25000,
    }
  );

  return result;
}
