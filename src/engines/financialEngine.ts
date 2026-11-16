import {
  GstSlabs,
  PabaruUiState,
  Party,
  Transaction,
} from '../types/models';

export interface IndianState {
  code: string;
  name: string;
}

export const INDIAN_STATES: IndianState[] = [
  { code: '01', name: 'Jammu & Kashmir' },
  { code: '02', name: 'Himachal Pradesh' },
  { code: '03', name: 'Punjab' },
  { code: '04', name: 'Chandigarh' },
  { code: '05', name: 'Uttarakhand' },
  { code: '06', name: 'Haryana' },
  { code: '07', name: 'Delhi' },
  { code: '08', name: 'Rajasthan' },
  { code: '09', name: 'Uttar Pradesh' },
  { code: '10', name: 'Bihar' },
  { code: '11', name: 'Sikkim' },
  { code: '12', name: 'Arunachal Pradesh' },
  { code: '13', name: 'Nagaland' },
  { code: '14', name: 'Manipur' },
  { code: '15', name: 'Mizoram' },
  { code: '16', name: 'Tripura' },
  { code: '17', name: 'Meghalaya' },
  { code: '18', name: 'Assam' },
  { code: '19', name: 'West Bengal' },
  { code: '20', name: 'Jharkhand' },
  { code: '21', name: 'Odisha' },
  { code: '22', name: 'Chhattisgarh' },
  { code: '23', name: 'Madhya Pradesh' },
  { code: '24', name: 'Gujarat' },
  { code: '26', name: 'Dadra & Nagar Haveli and Daman & Diu' },
  { code: '27', name: 'Maharashtra' },
  { code: '29', name: 'Karnataka' },
  { code: '30', name: 'Goa' },
  { code: '31', name: 'Lakshadweep' },
  { code: '32', name: 'Kerala' },
  { code: '33', name: 'Tamil Nadu' },
  { code: '34', name: 'Puducherry' },
  { code: '35', name: 'Andaman & Nicobar Islands' },
  { code: '36', name: 'Telangana' },
  { code: '37', name: 'Andhra Pradesh' },
  { code: '38', name: 'Ladakh' },
  { code: '96', name: 'Foreign Country / Exports' },
  { code: '97', name: 'Other Territory' },
];

export function formatPaise(paise: number, compact = false): string {
  const safe = Number.isFinite(paise) ? Math.round(paise) : 0;
  const isNegative = safe < 0;
  const absPaise = Math.abs(safe);
  const sign = isNegative ? '-' : '';

  if (compact) {
    if (absPaise >= 1_000_000_000) return `${sign}₹ ${(absPaise / 1_000_000_000).toFixed(2)} Cr`;
    if (absPaise >= 10_000_000) return `${sign}₹ ${(absPaise / 10_000_000).toFixed(2)} L`;
    if (absPaise >= 100_000) return `${sign}₹ ${(absPaise / 100_000).toFixed(2)} K`;
    return `${sign}₹ ${(absPaise / 100).toFixed(2)}`;
  }

  const wholePart = Math.floor(absPaise / 100);
  const fracPart = String(absPaise % 100).padStart(2, '0');
  let wholeStr = String(wholePart);

  if (wholeStr.length > 3) {
    const last3 = wholeStr.slice(-3);
    const rest = wholeStr.slice(0, -3);
    const chunks: string[] = [];
    for (let i = rest.length; i > 0; i -= 2) {
      chunks.unshift(rest.slice(Math.max(0, i - 2), i));
    }
    wholeStr = `${chunks.join(',')},${last3}`;
  }

  return `${sign}₹ ${wholeStr}.${fracPart}`;
}

export function lineTaxable(qtyMilli: number, ratePaise: number): number {
  return Math.floor((qtyMilli * ratePaise + 500) / 1000);
}

export function formatQuantityMilli(qtyMilli: number): string {
  if (qtyMilli % 1000 === 0) return String(Math.floor(qtyMilli / 1000));
  return (qtyMilli / 1000).toFixed(3).replace(/0+$/, '').replace(/\.$/, '');
}

export interface GstBreakdown {
  cgstPaise: number;
  sgstPaise: number;
  igstPaise: number;
  totalPaise: number;
}

export function calculateGstBps(
  taxablePaise: number,
  rateBps: number,
  isInterstate: boolean,
  isZeroRatedExport = false
): GstBreakdown {
  const safeTaxable = Math.max(0, Math.round(taxablePaise));
  const safeRate = Math.min(10000, Math.max(0, Math.round(rateBps)));
  if (safeTaxable <= 0 || safeRate <= 0 || isZeroRatedExport) {
    return { cgstPaise: 0, sgstPaise: 0, igstPaise: 0, totalPaise: safeTaxable };
  }
  if (isInterstate) {
    const totalTax = Math.floor((safeTaxable * safeRate + 5000) / 10000);
    return { cgstPaise: 0, sgstPaise: 0, igstPaise: totalTax, totalPaise: safeTaxable + totalTax };
  }
  const cgst = Math.floor((safeTaxable * safeRate + 10000) / 20000);
  const sgst = cgst;
  return { cgstPaise: cgst, sgstPaise: sgst, igstPaise: 0, totalPaise: safeTaxable + cgst + sgst };
}

export function parsePaiseFromRupees(rupeeString: string): number {
  const trimmed = rupeeString.trim();
  if (!trimmed || trimmed.includes('-') || /[eE]/.test(trimmed)) return 0;
  const clean = trimmed
    .replace(/[\s\u00A0\u2000-\u200a\u202f\u205f\u3000]+/g, '')
    .replace(/₹/g, '')
    .replace(/Rs\.?/gi, '')
    .replace(/INR/gi, '')
    .replace(/,/g, '')
    .replace(/\+/g, '');

  if (!clean || clean === '.' || /[^0-9.]/.test(clean)) return 0;
  const parts = clean.split('.');
  if (parts.length > 2) return 0;

  let whole = parts[0] ? Number.parseInt(parts[0], 10) : 0;
  if (!Number.isFinite(whole)) return 0;

  let frac = 0;
  if (parts.length === 2 && parts[1]) {
    const f = parts[1];
    if (f.length > 2) {
      const first2 = Number.parseInt(f.slice(0, 2), 10) || 0;
      const third = Number.parseInt(f[2], 10) || 0;
      frac = third >= 5 ? first2 + 1 : first2;
    } else {
      frac = Number.parseInt(f.padEnd(2, '0'), 10) || 0;
    }
  }

  if (frac >= 100) {
    whole += Math.floor(frac / 100);
    frac %= 100;
  }
  return whole * 100 + frac;
}

export function getTodayDate(): string {
  const d = new Date();
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  return `${y}-${m}-${day}`;
}

export function getCompactFinancialYear(): string {
  const d = new Date();
  const y = d.getFullYear();
  const m = d.getMonth() + 1;
  const startShort = (m >= 4 ? y : y - 1) % 100;
  const endShort = (startShort + 1) % 100;
  return `${String(startShort).padStart(2, '0')}-${String(endShort).padStart(2, '0')}`;
}

export function getFinancialYearForDate(dateStr: string): string {
  const norm = normalizeDate(dateStr);
  const d = new Date();
  const curY = d.getFullYear();
  if (norm) {
    const [yStr, mStr] = norm.split('-');
    const y = Number.parseInt(yStr, 10) || curY;
    const m = Number.parseInt(mStr, 10) || 1;
    const startYear = m >= 4 ? y : y - 1;
    return `FY ${startYear}-${String((startYear + 1) % 100).padStart(2, '0')}`;
  }
  const curM = d.getMonth() + 1;
  const startYear = curM >= 4 ? curY : curY - 1;
  return `FY ${startYear}-${String((startYear + 1) % 100).padStart(2, '0')}`;
}

export function getCurrentFinancialYear(): string {
  return getFinancialYearForDate(getTodayDate());
}

export function normalizeDate(rawDate: string): string {
  const trimmed = rawDate.trim();
  if (!trimmed) return getTodayDate();
  const parts = trimmed.split(/[-/.]/);
  if (parts.length !== 3) return '';
  let y = 0;
  let m = 0;
  let day = 0;
  if (parts[0].length === 4) {
    y = Number.parseInt(parts[0], 10);
    m = Number.parseInt(parts[1], 10);
    day = Number.parseInt(parts[2], 10);
  } else if (parts[2].length === 4) {
    day = Number.parseInt(parts[0], 10);
    m = Number.parseInt(parts[1], 10);
    y = Number.parseInt(parts[2], 10);
  } else {
    return '';
  }
  if (y < 1900 || y > 2100 || m < 1 || m > 12 || day < 1 || day > 31) return '';
  const dt = new Date(Date.UTC(y, m - 1, day));
  if (dt.getUTCFullYear() !== y || dt.getUTCMonth() + 1 !== m || dt.getUTCDate() !== day) {
    return '';
  }
  return `${String(y).padStart(4, '0')}-${String(m).padStart(2, '0')}-${String(day).padStart(2, '0')}`;
}

export function getAvailableFinancialYears(transactions: Transaction[]): string[] {
  const set = new Set<string>([getCurrentFinancialYear()]);
  for (const t of transactions) {
    if (t.date.trim()) set.add(getFinancialYearForDate(t.date));
  }
  return [...Array.from(set).sort().reverse(), 'All Time'];
}

export function isDateInFinancialYear(dateStr: string, fyLabel: string): boolean {
  if (!fyLabel || fyLabel === 'All Time') return true;
  const norm = normalizeDate(dateStr);
  if (!norm) return false;
  const m = fyLabel.match(/FY\s*(\d{4})/i);
  if (!m) return true;
  const startYear = Number.parseInt(m[1], 10);
  const start = `${String(startYear).padStart(4, '0')}-04-01`;
  const end = `${String(startYear + 1).padStart(4, '0')}-03-31`;
  return norm >= start && norm <= end;
}

export function getFinancialYearEndDate(fyLabel: string): string {
  if (!fyLabel || fyLabel === 'All Time') return getTodayDate();
  const m = fyLabel.match(/FY\s*(\d{4})/i);
  if (!m) return getTodayDate();
  const startYear = Number.parseInt(m[1], 10);
  return `${String(startYear + 1).padStart(4, '0')}-03-31`;
}

export interface FyFinancialStatement {
  fyLabel: string;
  grossSalesPaise: number;
  salesReturnsPaise: number;
  salesDiscountsPaise: number;
  netSalesPaise: number;
  purchasesCogsPaise: number;
  directExpensesPaise: number;
  totalCogsPaise: number;
  grossProfitPaise: number;
  grossProfitMarginPercent: number;
  operatingExpensesAdminPaise: number;
  operatingExpensesSalariesPaise: number;
  operatingExpensesMarketingPaise: number;
  operatingExpensesDepreciationPaise: number;
  indirectExpensesPaise: number;
  totalOperatingExpensesPaise: number;
  operatingProfitPaise: number;
  operatingProfitMarginPercent: number;
  nonOperatingIncomePaise: number;
  nonOperatingInterestIncomePaise: number;
  nonOperatingExpensesPaise: number;
  financeCostsInterestPaise: number;
  earningsBeforeTaxEbtPaise: number;
  incomeTaxProvisionPaise: number;
  netProfitPaise: number;
  netProfitMarginPercent: number;
  drawingsPaise: number;
  cashInHandPaise: number;
  bankTotalPaise: number;
  receivablesPaise: number;
  payablesPaise: number;
  stockValuationPaise: number;
  gstInputCreditAssetPaise: number;
  gstPayableLiabilityPaise: number;
  totalAssetsPaise: number;
  openingCapitalPaise: number;
  closingCapitalPaise: number;
  netGstPayablePaise: number;
  totalLiabilitiesAndEquityPaise: number;
  transactionCount: number;
}

export function calculateFyFinancials(
  uiState: PabaruUiState,
  fyLabel: string,
  taxProvisionPaise = 0
): FyFinancialStatement {
  const fyTxns = uiState.transactions.filter(
    (t) => !t.isVoided && isDateInFinancialYear(t.date, fyLabel)
  );
  const isAllTime = fyLabel === 'All Time';
  const fyEndDate = getFinancialYearEndDate(fyLabel);

  let grossSales = 0;
  let salesReturns = 0;
  const salesDiscounts = 0;
  let purchases = 0;
  let directExp = 0;

  let opExAdmin = 0;
  let opExSalaries = 0;
  let opExMarketing = 0;
  let opExDepreciation = 0;
  let opExGeneral = 0;

  let nonOpInterestIncome = 0;
  let nonOpOtherIncome = 0;
  let financeLoanInterest = 0;
  let financeBankCharges = 0;
  let drawings = 0;

  const adRegex = /\b(ad|advertisement|ads|marketing|promo|campaign|branding|delivery)\b/i;
  const emiRegex = /\b(emi|loan|interest|finance\s*charge)\b/i;
  const salaryRegex = /\b(salary|payroll|wages|staff|bonus)\b/i;
  const bankFeeRegex = /\b(bank\s*charge|bank\s*fee|finance\s*fee)\b/i;
  const deprRegex = /\b(depreciation|amortization|amort)\b/i;
  const adminRegex = /\b(rent|utility|office|stationery|repair|maintenance)\b/i;

  for (const t of fyTxns) {
    const rem = t.remarks;
    const excl =
      t.items.length > 0
        ? t.items.reduce((acc, it) => acc + it.taxableAmountPaise, 0)
        : t.amountPaise;

    switch (t.type) {
      case 'Sale':
      case 'NonGstBill':
        grossSales += excl;
        break;
      case 'CreditNote':
        salesReturns += excl;
        break;
      case 'Purchase':
        purchases += excl;
        break;
      case 'DebitNote':
        purchases -= excl;
        break;
      case 'Expense':
        if (t.expenseCategory === 'Direct') {
          directExp += excl;
        } else if (emiRegex.test(rem)) {
          financeLoanInterest += excl;
        } else if (bankFeeRegex.test(rem)) {
          financeBankCharges += excl;
        } else if (salaryRegex.test(rem)) {
          opExSalaries += excl;
        } else if (adRegex.test(rem)) {
          opExMarketing += excl;
        } else if (deprRegex.test(rem)) {
          opExDepreciation += excl;
        } else if (adminRegex.test(rem)) {
          opExAdmin += excl;
        } else {
          opExGeneral += excl;
        }
        break;
      case 'PaymentIn':
        if (/interest|dividend|fd yield/i.test(rem)) {
          nonOpInterestIncome += t.amountPaise;
        } else if (/other income|scrap|commission/i.test(rem)) {
          nonOpOtherIncome += t.amountPaise;
        }
        break;
      case 'Drawing':
        drawings += t.amountPaise;
        break;
      default:
        break;
    }
  }

  const netSales = grossSales - salesReturns - salesDiscounts;
  const totalCogs = purchases + directExp;
  const grossProfit = netSales - totalCogs;
  const gpMargin = netSales > 0 ? (grossProfit / netSales) * 100 : 0;

  const adminTotal = opExAdmin + opExGeneral;
  const totalOperatingExp = adminTotal + opExSalaries + opExMarketing + opExDepreciation;
  const operatingProfit = grossProfit - totalOperatingExp;
  const opMargin = netSales > 0 ? (operatingProfit / netSales) * 100 : 0;

  const totalNonOpIncome = nonOpInterestIncome + nonOpOtherIncome;
  const totalFinanceCosts = financeLoanInterest + financeBankCharges;
  const ebt = operatingProfit + totalNonOpIncome - totalFinanceCosts;
  const tax = Math.max(0, taxProvisionPaise);
  const netProfit = ebt - tax;
  const npMargin = netSales > 0 ? (netProfit / netSales) * 100 : 0;

  const cumulativeTxns = uiState.transactions.filter((t) => {
    if (t.isVoided) return false;
    if (isAllTime) return true;
    const n = normalizeDate(t.date);
    return n !== '' && n <= fyEndDate;
  });

  let cashInHand = uiState.profile.openingCashBalancePaise;
  let bankTotal = uiState.bankAccounts.reduce((s, b) => s + b.openingBalancePaise, 0);

  for (const t of cumulativeTxns) {
    const delta = (() => {
      switch (t.type) {
        case 'PaymentIn':
        case 'Sale':
        case 'NonGstBill':
        case 'DebitNote':
          return t.amountPaise;
        case 'PaymentOut':
        case 'Purchase':
        case 'Expense':
        case 'CreditNote':
        case 'Drawing':
          return -t.amountPaise;
        case 'Quotation':
        default:
          return 0;
      }
    })();
    if (t.paymentMode === 'Cash') cashInHand += delta;
    else if (t.paymentMode === 'Bank' || t.paymentMode === 'UPI' || t.paymentMode === 'Cheque') {
      bankTotal += delta;
    }
  }

  let receivables = uiState.parties
    .filter((p) => p.openingBalanceType === 'Receivable')
    .reduce((s, p) => s + p.openingBalancePaise, 0);
  let payables = uiState.parties
    .filter((p) => p.openingBalanceType === 'Payable')
    .reduce((s, p) => s + p.openingBalancePaise, 0);

  for (const t of cumulativeTxns) {
    if (!t.partyId) continue;
    if ((t.type === 'Sale' || t.type === 'NonGstBill') && t.paymentMode === 'Credit') {
      receivables += t.amountPaise;
    } else if (t.type === 'CreditNote' && t.paymentMode === 'Credit') {
      receivables -= t.amountPaise;
    } else if (t.type === 'Purchase' && t.paymentMode === 'Credit') {
      payables += t.amountPaise;
    } else if (t.type === 'DebitNote' && t.paymentMode === 'Credit') {
      payables -= t.amountPaise;
    } else if (t.type === 'PaymentIn') {
      receivables -= t.amountPaise;
    } else if (t.type === 'PaymentOut') {
      payables -= t.amountPaise;
    }
  }

  const stockValuation = uiState.products
    .filter((p) => !p.isArchived)
    .reduce((s, p) => s + Math.floor((p.currentStockMilli * p.purchasePricePaise + 500) / 1000), 0);

  let cumulativeNet = 0;
  let cumulativeDrawings = 0;
  let netGstCashComponent = 0;

  for (const t of cumulativeTxns) {
    const excl =
      t.items.length > 0
        ? t.items.reduce((s, it) => s + it.taxableAmountPaise, 0)
        : t.amountPaise;
    const taxComponent = t.items.length > 0 ? t.amountPaise - excl : 0;

    if (t.type === 'Sale' || t.type === 'NonGstBill') {
      cumulativeNet += excl;
      netGstCashComponent += taxComponent;
    } else if (t.type === 'CreditNote') {
      cumulativeNet -= excl;
      netGstCashComponent -= taxComponent;
    } else if (t.type === 'Purchase' || t.type === 'Expense') {
      cumulativeNet -= excl;
      netGstCashComponent -= taxComponent;
    } else if (t.type === 'DebitNote') {
      cumulativeNet += excl;
      netGstCashComponent += taxComponent;
    } else if (t.type === 'PaymentIn') {
      if (/interest|dividend|fd yield|other income|scrap|commission/i.test(t.remarks)) {
        cumulativeNet += t.amountPaise;
      }
    } else if (t.type === 'Drawing') {
      cumulativeDrawings += t.amountPaise;
    }
  }

  const gstInputCreditAsset = netGstCashComponent < 0 ? -netGstCashComponent : 0;
  const gstPayableLiability = netGstCashComponent > 0 ? netGstCashComponent : 0;
  const totalAssets = cashInHand + bankTotal + receivables + stockValuation + gstInputCreditAsset;
  const closingCapital =
    uiState.profile.openingCapitalPaise + cumulativeNet + stockValuation - cumulativeDrawings;
  const totalLiabilities = payables + gstPayableLiability + closingCapital;

  return {
    fyLabel,
    grossSalesPaise: grossSales,
    salesReturnsPaise: salesReturns,
    salesDiscountsPaise: salesDiscounts,
    netSalesPaise: netSales,
    purchasesCogsPaise: purchases,
    directExpensesPaise: directExp,
    totalCogsPaise: totalCogs,
    grossProfitPaise: grossProfit,
    grossProfitMarginPercent: gpMargin,
    operatingExpensesAdminPaise: adminTotal,
    operatingExpensesSalariesPaise: opExSalaries,
    operatingExpensesMarketingPaise: opExMarketing,
    operatingExpensesDepreciationPaise: opExDepreciation,
    indirectExpensesPaise: totalOperatingExp,
    totalOperatingExpensesPaise: totalOperatingExp,
    operatingProfitPaise: operatingProfit,
    operatingProfitMarginPercent: opMargin,
    nonOperatingIncomePaise: totalNonOpIncome,
    nonOperatingInterestIncomePaise: nonOpInterestIncome,
    nonOperatingExpensesPaise: totalFinanceCosts,
    financeCostsInterestPaise: financeLoanInterest,
    earningsBeforeTaxEbtPaise: ebt,
    incomeTaxProvisionPaise: tax,
    netProfitPaise: netProfit,
    netProfitMarginPercent: npMargin,
    drawingsPaise: drawings,
    cashInHandPaise: cashInHand,
    bankTotalPaise: bankTotal,
    receivablesPaise: receivables,
    payablesPaise: payables,
    stockValuationPaise: stockValuation,
    gstInputCreditAssetPaise: gstInputCreditAsset,
    gstPayableLiabilityPaise: gstPayableLiability,
    totalAssetsPaise: totalAssets,
    openingCapitalPaise: uiState.profile.openingCapitalPaise,
    closingCapitalPaise: closingCapital,
    netGstPayablePaise: netGstCashComponent,
    totalLiabilitiesAndEquityPaise: totalLiabilities,
    transactionCount: fyTxns.length,
  };
}

export interface GstSlabBreakdownRow {
  rateLabel: string;
  taxablePaise: number;
  cgstPaise: number;
  sgstPaise: number;
  igstPaise: number;
  totalTaxPaise: number;
}

export interface FyGstSummary {
  fyLabel: string;
  totalTaxableSalesPaise: number;
  b2bTaxableSalesPaise: number;
  b2cTaxableSalesPaise: number;
  outputCgstPaise: number;
  outputSgstPaise: number;
  outputIgstPaise: number;
  totalOutputTaxPaise: number;
  totalTaxablePurchasesPaise: number;
  eligibleItcCgstPaise: number;
  eligibleItcSgstPaise: number;
  eligibleItcIgstPaise: number;
  totalEligibleItcPaise: number;
  netGstPayablePaise: number;
  slabBreakdown: GstSlabBreakdownRow[];
  b2bInvoiceCount: number;
  b2cInvoiceCount: number;
}

export const GSTIN_REGEX = /^[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z]{1}[1-9A-Z]{1}Z[0-9A-Z]{1}$/;
const GSTIN_CHARS = '0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ';

export function gstinCheckChar(first14: string): string {
  const s = first14.trim().toUpperCase();
  if (s.length < 14) return '0';
  let sum = 0;
  for (let i = 0; i < 14; i++) {
    const v = GSTIN_CHARS.indexOf(s[i]);
    if (v < 0) return '0';
    const factor = i % 2 === 0 ? 1 : 2;
    const p = v * factor;
    sum += Math.floor(p / 36) + (p % 36);
  }
  const checkVal = (36 - (sum % 36)) % 36;
  return GSTIN_CHARS[checkVal];
}

export function isValidGstinFormat(gstin?: string | null): boolean {
  if (!gstin || !gstin.trim()) return false;
  const cleaned = gstin.trim().toUpperCase();
  if (cleaned.length !== 15 || !GSTIN_REGEX.test(cleaned)) return false;
  const stateCode = cleaned.slice(0, 2);
  const num = Number.parseInt(stateCode, 10);
  return (
    (INDIAN_STATES.some((s) => s.code === stateCode) || (num >= 1 && num <= 38) || stateCode === '96' || stateCode === '97') &&
    stateCode !== '99' &&
    stateCode !== '00'
  );
}

export interface GstinValidationResult {
  isValid: boolean;
  message: string;
  stateCode?: string;
  pan?: string;
}

export function validateGstin(
  gstin?: string | null,
  allowBlank = true,
  partyPan?: string | null
): GstinValidationResult {
  if (!gstin || !gstin.trim()) {
    return allowBlank
      ? { isValid: true, message: 'Unregistered / B2C (No GSTIN provided)' }
      : { isValid: false, message: 'GSTIN cannot be blank for B2B registered invoice' };
  }
  const cleaned = gstin.trim().toUpperCase();
  if (cleaned.length !== 15) {
    return { isValid: false, message: `GSTIN must be 15 characters (currently ${cleaned.length})` };
  }
  if (!GSTIN_REGEX.test(cleaned)) {
    return { isValid: false, message: 'Invalid GSTIN layout (expected e.g. 27ABCDE1234F1Z5)' };
  }
  const stateCode = cleaned.slice(0, 2);
  const num = Number.parseInt(stateCode, 10);
  const validState =
    (INDIAN_STATES.some((s) => s.code === stateCode) || (num >= 1 && num <= 38) || stateCode === '97') &&
    stateCode !== '99' &&
    stateCode !== '00';
  if (!validState) {
    return { isValid: false, message: `Invalid 2-digit state code '${stateCode}' in GSTIN` };
  }
  const pan = cleaned.slice(2, 12);
  if (partyPan && partyPan.trim()) {
    const cp = partyPan.trim().toUpperCase();
    if (cp !== pan) {
      return { isValid: false, message: `GSTIN PAN (${pan}) does not match Party PAN (${cp})` };
    }
  }
  const expectedCheck = gstinCheckChar(cleaned.slice(0, 14));
  if (cleaned[14] !== expectedCheck) {
    return {
      isValid: false,
      message: `Invalid check-digit '${cleaned[14]}'. Expected '${expectedCheck}'`,
    };
  }
  return {
    isValid: true,
    message: `Valid statutory GSTIN (State: ${stateCode}, PAN: ${pan})`,
    stateCode,
    pan,
  };
}

export function validatePan(pan?: string | null, allowBlank = true): { isValid: boolean; message: string } {
  if (!pan || !pan.trim()) {
    return allowBlank ? { isValid: true, message: 'Optional PAN' } : { isValid: false, message: 'PAN required' };
  }
  const c = pan.trim().toUpperCase();
  if (!/^[A-Z]{5}[0-9]{4}[A-Z]$/.test(c)) {
    return { isValid: false, message: 'Expected 10-char PAN (e.g. ABCDE1234F)' };
  }
  return { isValid: true, message: 'Valid PAN' };
}

export function validateIfsc(ifsc?: string | null, allowBlank = true): { isValid: boolean; message: string } {
  if (!ifsc || !ifsc.trim()) {
    return allowBlank ? { isValid: true, message: 'Optional IFSC' } : { isValid: false, message: 'IFSC required' };
  }
  const c = ifsc.trim().toUpperCase();
  if (!/^[A-Z]{4}0[A-Z0-9]{6}$/.test(c)) {
    return { isValid: false, message: 'Expected 11-char IFSC (e.g. HDFC0000060)' };
  }
  return { isValid: true, message: 'Valid IFSC' };
}

export function validatePhone(phone?: string | null, allowBlank = true): { isValid: boolean; message: string } {
  if (!phone || !phone.trim()) {
    return allowBlank ? { isValid: true, message: 'Optional Phone' } : { isValid: false, message: 'Phone required' };
  }
  const digits = phone.replace(/\D/g, '');
  if (!/^[6-9][0-9]{9}$/.test(digits)) {
    return { isValid: false, message: 'Enter valid 10-digit Indian mobile number' };
  }
  return { isValid: true, message: 'Valid mobile number' };
}

export function validateHsn(hsn?: string | null, allowBlank = true): { isValid: boolean; message: string } {
  if (!hsn || !hsn.trim()) {
    return allowBlank ? { isValid: true, message: 'Optional HSN' } : { isValid: false, message: 'HSN required' };
  }
  const c = hsn.trim();
  if (!/^[0-9]{4}([0-9]{2})?([0-9]{2})?$/.test(c)) {
    return { isValid: false, message: 'HSN must be 4, 6, or 8 numeric digits' };
  }
  return { isValid: true, message: `Valid ${c.length}-digit HSN` };
}

export function calculateFyGstSummary(
  transactions: Transaction[],
  parties: Party[],
  fyLabel: string
): FyGstSummary {
  const fyTxns = transactions.filter((t) => !t.isVoided && isDateInFinancialYear(t.date, fyLabel));
  const partyMap = new Map(parties.map((p) => [p.id, p]));

  let totalTaxableSales = 0;
  let b2bTaxable = 0;
  let b2cTaxable = 0;
  let outputCgst = 0;
  let outputSgst = 0;
  let outputIgst = 0;
  let b2bCount = 0;
  let b2cCount = 0;

  const rateTaxableMap = new Map<number, number>();
  const rateCgstMap = new Map<number, number>();
  const rateSgstMap = new Map<number, number>();
  const rateIgstMap = new Map<number, number>();

  for (const t of fyTxns) {
    if (t.type !== 'Sale' && t.type !== 'CreditNote') continue;
    const party = t.partyId ? partyMap.get(t.partyId) : undefined;
    const isB2b = !!party && isValidGstinFormat(party.gstin);
    const isCreditNote = t.type === 'CreditNote';
    const mult = isCreditNote ? -1 : 1;
    if (!isCreditNote) {
      if (isB2b) b2bCount++;
      else b2cCount++;
    }

    if (t.items.length > 0) {
      let invTaxable = 0;
      for (const item of t.items) {
        const lt = item.taxableAmountPaise * mult;
        const lc = item.cgstPaise * mult;
        const ls = item.sgstPaise * mult;
        const li = item.igstPaise * mult;
        invTaxable += lt;
        outputCgst += lc;
        outputSgst += ls;
        outputIgst += li;
        rateTaxableMap.set(item.gstRateBps, (rateTaxableMap.get(item.gstRateBps) || 0) + lt);
        rateCgstMap.set(item.gstRateBps, (rateCgstMap.get(item.gstRateBps) || 0) + lc);
        rateSgstMap.set(item.gstRateBps, (rateSgstMap.get(item.gstRateBps) || 0) + ls);
        rateIgstMap.set(item.gstRateBps, (rateIgstMap.get(item.gstRateBps) || 0) + li);
      }
      totalTaxableSales += invTaxable;
      if (isB2b) b2bTaxable += invTaxable;
      else b2cTaxable += invTaxable;
    } else {
      const net = t.amountPaise * mult;
      totalTaxableSales += net;
      if (isB2b) b2bTaxable += net;
      else b2cTaxable += net;
      rateTaxableMap.set(0, (rateTaxableMap.get(0) || 0) + net);
    }
  }

  let totalTaxablePurchases = 0;
  let itcCgst = 0;
  let itcSgst = 0;
  let itcIgst = 0;

  for (const t of fyTxns) {
    if (t.type !== 'Purchase' && t.type !== 'DebitNote') continue;
    const mult = t.type === 'DebitNote' ? -1 : 1;
    if (t.items.length > 0) {
      for (const item of t.items) {
        totalTaxablePurchases += item.taxableAmountPaise * mult;
        itcCgst += item.cgstPaise * mult;
        itcSgst += item.sgstPaise * mult;
        itcIgst += item.igstPaise * mult;
      }
    } else {
      totalTaxablePurchases += t.amountPaise * mult;
    }
  }

  const totalOutputTax = outputCgst + outputSgst + outputIgst;
  const totalEligibleItc = itcCgst + itcSgst + itcIgst;
  const netPayable = totalOutputTax - totalEligibleItc;

  const allBps = Array.from(
    new Set([...rateTaxableMap.keys(), 0, 25, 300, 500, 1200, 1800, 2800, 4000])
  ).sort((a, b) => a - b);

  const slabBreakdown = allBps
    .map((bps) => {
      const taxable = rateTaxableMap.get(bps) || 0;
      const cg = rateCgstMap.get(bps) || 0;
      const sg = rateSgstMap.get(bps) || 0;
      const ig = rateIgstMap.get(bps) || 0;
      return {
        rateLabel: GstSlabs.label(bps),
        taxablePaise: taxable,
        cgstPaise: cg,
        sgstPaise: sg,
        igstPaise: ig,
        totalTaxPaise: cg + sg + ig,
      };
    })
    .filter((r) => r.taxablePaise !== 0 || r.totalTaxPaise !== 0);

  return {
    fyLabel,
    totalTaxableSalesPaise: totalTaxableSales,
    b2bTaxableSalesPaise: b2bTaxable,
    b2cTaxableSalesPaise: b2cTaxable,
    outputCgstPaise: outputCgst,
    outputSgstPaise: outputSgst,
    outputIgstPaise: outputIgst,
    totalOutputTaxPaise: totalOutputTax,
    totalTaxablePurchasesPaise: totalTaxablePurchases,
    eligibleItcCgstPaise: itcCgst,
    eligibleItcSgstPaise: itcSgst,
    eligibleItcIgstPaise: itcIgst,
    totalEligibleItcPaise: totalEligibleItc,
    netGstPayablePaise: netPayable,
    slabBreakdown:
      slabBreakdown.length > 0
        ? slabBreakdown
        : [
            {
              rateLabel: '0%',
              taxablePaise: totalTaxableSales,
              cgstPaise: 0,
              sgstPaise: 0,
              igstPaise: 0,
              totalTaxPaise: 0,
            },
          ],
    b2bInvoiceCount: b2bCount,
    b2cInvoiceCount: b2cCount,
  };
}

export interface Gstr9QuarterSummary {
  quarterKey: string;
  quarterLabel: string;
  dateRangeDesc: string;
  taxableSalesPaise: number;
  b2bTaxablePaise: number;
  b2cTaxablePaise: number;
  outputCgstPaise: number;
  outputSgstPaise: number;
  outputIgstPaise: number;
  totalOutputTaxPaise: number;
  taxablePurchasesPaise: number;
  itcCgstPaise: number;
  itcSgstPaise: number;
  itcIgstPaise: number;
  totalItcPaise: number;
  netTaxPayablePaise: number;
  invoiceCount: number;
}

export interface Gstr9AnnualSummary {
  fyLabel: string;
  quarters: Gstr9QuarterSummary[];
  annualTaxableSalesPaise: number;
  annualB2bTaxablePaise: number;
  annualB2cTaxablePaise: number;
  annualOutputCgstPaise: number;
  annualOutputSgstPaise: number;
  annualOutputIgstPaise: number;
  annualTotalOutputTaxPaise: number;
  annualTaxablePurchasesPaise: number;
  annualItcCgstPaise: number;
  annualItcSgstPaise: number;
  annualItcIgstPaise: number;
  annualTotalItcPaise: number;
  annualNetTaxPayablePaise: number;
  totalInvoicesCount: number;
  table4Details: {
    b2cTaxablePaise: number;
    b2cTaxPaise: number;
    b2bTaxablePaise: number;
    b2bTaxPaise: number;
    totalTaxablePaise: number;
    totalCgstPaise: number;
    totalSgstPaise: number;
    totalIgstPaise: number;
    grandTotalTaxPaise: number;
  };
  table6Details: {
    inwardSuppliesTaxablePaise: number;
    itcCgstPaise: number;
    itcSgstPaise: number;
    itcIgstPaise: number;
    totalItcPaise: number;
  };
  table9Details: {
    taxPayablePaise: number;
    taxPaidItcPaise: number;
    taxPaidCashPaise: number;
    balancePayablePaise: number;
  };
}

export function calculateGstr9AnnualSummary(
  transactions: Transaction[],
  parties: Party[],
  fyLabel: string
): Gstr9AnnualSummary {
  const cleanFy = !fyLabel || fyLabel === 'All Time' ? getCurrentFinancialYear() : fyLabel;
  const match = cleanFy.match(/FY\s*(\d{4})/i);
  const startYear = match ? Number.parseInt(match[1], 10) : 2025;
  const nextYear = startYear + 1;

  const quarterDefs: Array<{ key: string; label: string; start: string; end: string }> = [
    { key: 'Q1', label: 'Q1 (Apr - Jun)', start: `${startYear}-04-01`, end: `${startYear}-06-30` },
    { key: 'Q2', label: 'Q2 (Jul - Sep)', start: `${startYear}-07-01`, end: `${startYear}-09-30` },
    { key: 'Q3', label: 'Q3 (Oct - Dec)', start: `${startYear}-10-01`, end: `${startYear}-12-31` },
    { key: 'Q4', label: 'Q4 (Jan - Mar)', start: `${nextYear}-01-01`, end: `${nextYear}-03-31` },
  ];

  const partyMap = new Map(parties.map((p) => [p.id, p]));
  let totalB2bTax = 0;
  let totalB2cTax = 0;

  const quarters: Gstr9QuarterSummary[] = quarterDefs.map((q) => {
    const qTxns = transactions.filter((t) => {
      if (t.isVoided) return false;
      const n = normalizeDate(t.date);
      return n !== '' && n >= q.start && n <= q.end;
    });

    let qTaxableSales = 0;
    let qB2bTaxable = 0;
    let qB2cTaxable = 0;
    let qOutputCgst = 0;
    let qOutputSgst = 0;
    let qOutputIgst = 0;
    let qInvCount = 0;

    for (const t of qTxns) {
      if (t.type !== 'Sale' && t.type !== 'CreditNote') continue;
      const isCreditNote = t.type === 'CreditNote';
      const mult = isCreditNote ? -1 : 1;
      if (!isCreditNote) qInvCount++;
      const party = t.partyId ? partyMap.get(t.partyId) : undefined;
      const isB2b = !!party && isValidGstinFormat(party.gstin);

      if (t.items.length > 0) {
        let invTaxable = 0;
        let invCg = 0;
        let invSg = 0;
        let invIg = 0;
        for (const it of t.items) {
          invTaxable += it.taxableAmountPaise * mult;
          invCg += it.cgstPaise * mult;
          invSg += it.sgstPaise * mult;
          invIg += it.igstPaise * mult;
        }
        qOutputCgst += invCg;
        qOutputSgst += invSg;
        qOutputIgst += invIg;
        qTaxableSales += invTaxable;
        if (isB2b) {
          qB2bTaxable += invTaxable;
          totalB2bTax += invCg + invSg + invIg;
        } else {
          qB2cTaxable += invTaxable;
          totalB2cTax += invCg + invSg + invIg;
        }
      } else {
        const net = t.amountPaise * mult;
        qTaxableSales += net;
        if (isB2b) qB2bTaxable += net;
        else qB2cTaxable += net;
      }
    }

    let qTaxablePurchases = 0;
    let qItcCgst = 0;
    let qItcSgst = 0;
    let qItcIgst = 0;

    for (const t of qTxns) {
      if (t.type !== 'Purchase' && t.type !== 'DebitNote') continue;
      const mult = t.type === 'DebitNote' ? -1 : 1;
      if (t.items.length > 0) {
        for (const it of t.items) {
          qTaxablePurchases += it.taxableAmountPaise * mult;
          qItcCgst += it.cgstPaise * mult;
          qItcSgst += it.sgstPaise * mult;
          qItcIgst += it.igstPaise * mult;
        }
      } else {
        qTaxablePurchases += t.amountPaise * mult;
      }
    }

    const totalOutputTax = qOutputCgst + qOutputSgst + qOutputIgst;
    const totalItc = qItcCgst + qItcSgst + qItcIgst;
    return {
      quarterKey: q.key,
      quarterLabel: q.label,
      dateRangeDesc: `${q.start} to ${q.end}`,
      taxableSalesPaise: qTaxableSales,
      b2bTaxablePaise: qB2bTaxable,
      b2cTaxablePaise: qB2cTaxable,
      outputCgstPaise: qOutputCgst,
      outputSgstPaise: qOutputSgst,
      outputIgstPaise: qOutputIgst,
      totalOutputTaxPaise: totalOutputTax,
      taxablePurchasesPaise: qTaxablePurchases,
      itcCgstPaise: qItcCgst,
      itcSgstPaise: qItcSgst,
      itcIgstPaise: qItcIgst,
      totalItcPaise: totalItc,
      netTaxPayablePaise: totalOutputTax - totalItc,
      invoiceCount: qInvCount,
    };
  });

  const annualTaxableSales = quarters.reduce((s, q) => s + q.taxableSalesPaise, 0);
  const annualB2bTaxable = quarters.reduce((s, q) => s + q.b2bTaxablePaise, 0);
  const annualB2cTaxable = quarters.reduce((s, q) => s + q.b2cTaxablePaise, 0);
  const annualOutputCgst = quarters.reduce((s, q) => s + q.outputCgstPaise, 0);
  const annualOutputSgst = quarters.reduce((s, q) => s + q.outputSgstPaise, 0);
  const annualOutputIgst = quarters.reduce((s, q) => s + q.outputIgstPaise, 0);
  const annualTotalOutputTax = annualOutputCgst + annualOutputSgst + annualOutputIgst;
  const annualTaxablePurchases = quarters.reduce((s, q) => s + q.taxablePurchasesPaise, 0);
  const annualItcCgst = quarters.reduce((s, q) => s + q.itcCgstPaise, 0);
  const annualItcSgst = quarters.reduce((s, q) => s + q.itcSgstPaise, 0);
  const annualItcIgst = quarters.reduce((s, q) => s + q.itcIgstPaise, 0);
  const annualTotalItc = annualItcCgst + annualItcSgst + annualItcIgst;
  const annualNet = annualTotalOutputTax - annualTotalItc;
  const itcUsed = Math.min(annualTotalOutputTax, annualTotalItc);
  const cashPaid = Math.max(0, annualTotalOutputTax - itcUsed);

  return {
    fyLabel: cleanFy,
    quarters,
    annualTaxableSalesPaise: annualTaxableSales,
    annualB2bTaxablePaise: annualB2bTaxable,
    annualB2cTaxablePaise: annualB2cTaxable,
    annualOutputCgstPaise: annualOutputCgst,
    annualOutputSgstPaise: annualOutputSgst,
    annualOutputIgstPaise: annualOutputIgst,
    annualTotalOutputTaxPaise: annualTotalOutputTax,
    annualTaxablePurchasesPaise: annualTaxablePurchases,
    annualItcCgstPaise: annualItcCgst,
    annualItcSgstPaise: annualItcSgst,
    annualItcIgstPaise: annualItcIgst,
    annualTotalItcPaise: annualTotalItc,
    annualNetTaxPayablePaise: annualNet,
    totalInvoicesCount: quarters.reduce((s, q) => s + q.invoiceCount, 0),
    table4Details: {
      b2cTaxablePaise: annualB2cTaxable,
      b2cTaxPaise: totalB2cTax,
      b2bTaxablePaise: annualB2bTaxable,
      b2bTaxPaise: totalB2bTax,
      totalTaxablePaise: annualTaxableSales,
      totalCgstPaise: annualOutputCgst,
      totalSgstPaise: annualOutputSgst,
      totalIgstPaise: annualOutputIgst,
      grandTotalTaxPaise: annualTotalOutputTax,
    },
    table6Details: {
      inwardSuppliesTaxablePaise: annualTaxablePurchases,
      itcCgstPaise: annualItcCgst,
      itcSgstPaise: annualItcSgst,
      itcIgstPaise: annualItcIgst,
      totalItcPaise: annualTotalItc,
    },
    table9Details: {
      taxPayablePaise: annualTotalOutputTax,
      taxPaidItcPaise: itcUsed,
      taxPaidCashPaise: cashPaid,
      balancePayablePaise: annualNet,
    },
  };
}

export function amountInWords(paise: number): string {
  const safe = Math.round(paise);
  if (safe === 0) return 'Zero Rupees Only';
  if (safe < 0) return `Minus ${amountInWords(-safe)}`;
  const rupees = Math.floor(safe / 100);
  const remPaise = safe % 100;

  const ones = [
    '', 'One', 'Two', 'Three', 'Four', 'Five', 'Six', 'Seven', 'Eight', 'Nine', 'Ten',
    'Eleven', 'Twelve', 'Thirteen', 'Fourteen', 'Fifteen', 'Sixteen', 'Seventeen', 'Eighteen', 'Nineteen',
  ];
  const tens = ['', '', 'Twenty', 'Thirty', 'Forty', 'Fifty', 'Sixty', 'Seventy', 'Eighty', 'Ninety'];

  function numToWords(n: number): string {
    if (n === 0) return '';
    if (n < 20) return `${ones[n]} `;
    if (n < 100) return `${tens[Math.floor(n / 10)]} ${ones[n % 10]} `;
    if (n < 1000) return `${ones[Math.floor(n / 100)]} Hundred ${numToWords(n % 100)}`;
    if (n < 100000) return `${numToWords(Math.floor(n / 1000)).trim()} Thousand ${numToWords(n % 1000)}`;
    if (n < 10000000) return `${numToWords(Math.floor(n / 100000)).trim()} Lakh ${numToWords(n % 100000)}`;
    return `${numToWords(Math.floor(n / 10000000)).trim()} Crore ${numToWords(n % 10000000)}`;
  }

  let words = '';
  if (rupees > 0) {
    words = `${rupees === 1 ? 'Rupee' : 'Rupees'} ${numToWords(rupees).trim()}`;
  }
  if (remPaise > 0) {
    const pUnit = remPaise === 1 ? 'Paisa' : 'Paise';
    words = words
      ? `${words} and ${numToWords(remPaise).trim()} ${pUnit}`
      : `${numToWords(remPaise).trim()} ${pUnit}`;
  }
  return `${words.replace(/\s+/g, ' ').trim()} Only`;
}
