import { BusinessProfile, Party, ProductItem, Transaction } from '../types/models';
import { formatPaise, isValidGstinFormat, normalizeDate } from './financialEngine';
import type { ImportedGstr1Invoice } from './gstr1ImportEngine';

export interface CmaOperatingStatement {
  grossSalesPaise: number;
  cogsPaise: number;
  grossProfitPaise: number;
  operatingExpensesPaise: number;
  operatingProfitPaise: number;
  interestAndFinancePaise: number;
  netProfitBeforeTaxPaise: number;
  taxPaise: number;
  patPaise: number;
}

export interface CmaWorkingCapitalAssessment {
  totalCurrentAssetsPaise: number;
  currentLiabilitiesOtherThanBankPaise: number;
  workingCapitalGapPaise: number;
  minPromotersContributionPaise: number;
  actualNwcPaise: number;
  mpbfTandonMethod2Paise: number;
  currentRatio: number;
  dscr: number;
}

export function calculateCmaData(
  transactions: Transaction[],
  parties: Party[],
  products: ProductItem[],
  cashInHandPaise: number,
  bankBalancesPaise: number
): { operating: CmaOperatingStatement; wc: CmaWorkingCapitalAssessment } {
  let sales = 0;
  let purchases = 0;
  let directExp = 0;
  let indirectExp = 0;

  for (const t of transactions) {
    if (t.isVoided) continue;
    const excl =
      t.items.length > 0 ? t.items.reduce((s, it) => s + it.taxableAmountPaise, 0) : t.amountPaise;
    if (t.type === 'Sale' || t.type === 'NonGstBill') sales += excl;
    else if (t.type === 'CreditNote') sales -= excl;
    else if (t.type === 'Purchase') purchases += excl;
    else if (t.type === 'DebitNote') purchases -= excl;
    else if (t.type === 'Expense') {
      if (t.expenseCategory === 'Direct') directExp += excl;
      else indirectExp += excl;
    }
  }
  sales = Math.max(0, sales);
  purchases = Math.max(0, purchases);

  const cogs = purchases + directExp;
  const grossProfit = sales - cogs;
  const operatingExpenses = indirectExp;
  const ebitda = grossProfit - operatingExpenses;
  const safeInterest = ebitda > 0 ? Math.floor((ebitda * 10 + 50) / 100) : 0;
  const pbt = ebitda - safeInterest;
  const tax = pbt > 0 ? Math.floor((pbt * 25 + 50) / 100) : 0;
  const pat = pbt - tax;

  let receivables = parties
    .filter((p) => p.openingBalanceType === 'Receivable')
    .reduce((s, p) => s + p.openingBalancePaise, 0);
  let payables = parties
    .filter((p) => p.openingBalanceType === 'Payable')
    .reduce((s, p) => s + p.openingBalancePaise, 0);

  for (const t of transactions) {
    if (t.isVoided || !t.partyId) continue;
    if ((t.type === 'Sale' || t.type === 'NonGstBill') && t.paymentMode === 'Credit') {
      receivables += t.amountPaise;
    } else if (t.type === 'CreditNote' && t.paymentMode === 'Credit') {
      receivables -= t.amountPaise;
    } else if (t.type === 'Purchase' && t.paymentMode === 'Credit') {
      payables += t.amountPaise;
    } else if (t.type === 'DebitNote' && t.paymentMode === 'Credit') {
      payables -= t.amountPaise;
    } else if (t.type === 'PaymentIn') receivables -= t.amountPaise;
    else if (t.type === 'PaymentOut') payables -= t.amountPaise;
  }

  const stockValuation = products
    .filter((p) => !p.isArchived)
    .reduce((s, p) => s + Math.floor((p.currentStockMilli * p.purchasePricePaise + 500) / 1000), 0);

  const totalCurrentAssets = cashInHandPaise + bankBalancesPaise + receivables + stockValuation;
  const otherCurrentLiabilities = payables + Math.floor(operatingExpenses / 12);
  const wcGap = totalCurrentAssets - otherCurrentLiabilities;
  const minMargin = Math.floor((totalCurrentAssets * 25 + 50) / 100);
  const mpbf = Math.max(0, Math.floor((totalCurrentAssets * 75 + 50) / 100) - otherCurrentLiabilities);
  const currentRatio = otherCurrentLiabilities > 0 ? totalCurrentAssets / otherCurrentLiabilities : 2.1;
  const dscr = safeInterest > 0 ? (pat + safeInterest) / safeInterest : 3.5;

  return {
    operating: {
      grossSalesPaise: sales,
      cogsPaise: cogs,
      grossProfitPaise: grossProfit,
      operatingExpensesPaise: operatingExpenses,
      operatingProfitPaise: ebitda,
      interestAndFinancePaise: safeInterest,
      netProfitBeforeTaxPaise: pbt,
      taxPaise: tax,
      patPaise: pat,
    },
    wc: {
      totalCurrentAssetsPaise: totalCurrentAssets,
      currentLiabilitiesOtherThanBankPaise: otherCurrentLiabilities,
      workingCapitalGapPaise: wcGap,
      minPromotersContributionPaise: minMargin,
      actualNwcPaise: minMargin,
      mpbfTandonMethod2Paise: mpbf,
      currentRatio,
      dscr,
    },
  };
}

export type GstPeriodFilter = 'CurrentMonth' | 'PreviousMonth' | 'Quarter' | 'AllTime';

export const GST_PERIOD_LABELS: Record<GstPeriodFilter, string> = {
  CurrentMonth: 'Current Month',
  PreviousMonth: 'Previous Month',
  Quarter: 'Current Quarter',
  AllTime: 'All Time / FY',
};

export function isDateInPeriod(dateStr: string, filter: GstPeriodFilter): boolean {
  if (filter === 'AllTime') return true;
  const norm = normalizeDate(dateStr);
  if (!norm) return false;
  const [yStr, mStr] = norm.split('-');
  const y = Number.parseInt(yStr, 10);
  const m = Number.parseInt(mStr, 10);
  const now = new Date();
  const curY = now.getFullYear();
  const curM = now.getMonth() + 1;
  if (filter === 'CurrentMonth') return y === curY && m === curM;
  if (filter === 'PreviousMonth') {
    const prevM = curM === 1 ? 12 : curM - 1;
    const prevY = curM === 1 ? curY - 1 : curY;
    return y === prevY && m === prevM;
  }
  const curQ = Math.floor((curM - 1) / 3);
  const q = Math.floor((m - 1) / 3);
  return y === curY && q === curQ;
}

export interface Gstr1B2bRateItem {
  gstRate: number;
  taxablePaise: number;
  cgstPaise: number;
  sgstPaise: number;
  igstPaise: number;
  gstRateBps: number;
}

export interface Gstr1B2bInvoice {
  partyId: string;
  gstin: string;
  partyName: string;
  invoiceNumber: string;
  date: string;
  posStateCode: string;
  taxablePaise: number;
  cgstPaise: number;
  sgstPaise: number;
  igstPaise: number;
  totalPaise: number;
  isInterstate: boolean;
  itemsSummary: string;
  isPrefilledFromExcel: boolean;
  rateBreakdown: Gstr1B2bRateItem[];
}

export interface Gstr1CreditDebitNote {
  partyGstin: string;
  partyName: string;
  noteNumber: string;
  noteDate: string;
  noteType: 'C' | 'D';
  posStateCode: string;
  taxablePaise: number;
  cgstPaise: number;
  sgstPaise: number;
  igstPaise: number;
  totalPaise: number;
  rateBreakdown: Gstr1B2bRateItem[];
}

export interface Gstr1ExportInvoice {
  invoiceNumber: string;
  date: string;
  exportType: 'WPAY' | 'WOPAY';
  taxablePaise: number;
  igstPaise: number;
  totalPaise: number;
  gstRate: number;
  gstRateBps: number;
}

export interface Gstr1B2csSupply {
  posStateCode: string;
  supplyType: 'INTRA' | 'INTER';
  gstRate: number;
  taxablePaise: number;
  cgstPaise: number;
  sgstPaise: number;
  igstPaise: number;
  gstRateBps: number;
}

export interface Gstr1HsnItem {
  hsnCode: string;
  description: string;
  uqc: string;
  totalQuantity: number;
  taxablePaise: number;
  cgstPaise: number;
  sgstPaise: number;
  igstPaise: number;
  gstRate: number;
  totalQuantityMilli: number;
  gstRateBps: number;
}

export interface Gstr1DocItem {
  minInvoiceNumber: string;
  maxInvoiceNumber: string;
  totalIssued: number;
  cancelledCount: number;
  netIssuedCount: number;
}

export interface StatutoryGstr1Data {
  b2bInvoices: Gstr1B2bInvoice[];
  b2csSupplies: Gstr1B2csSupply[];
  hsnSummary: Gstr1HsnItem[];
  docSummary: Gstr1DocItem;
  b2bCount: number;
  b2bTaxablePaise: number;
  b2bCgstPaise: number;
  b2bSgstPaise: number;
  b2bIgstPaise: number;
  b2cCount: number;
  b2cTaxablePaise: number;
  b2cCgstPaise: number;
  b2cSgstPaise: number;
  b2cIgstPaise: number;
  totalTaxablePaise: number;
  totalCgstPaise: number;
  totalSgstPaise: number;
  totalIgstPaise: number;
  totalTaxPaise: number;
  prefilledExcelCount: number;
  cdnrNotes: Gstr1CreditDebitNote[];
  exportInvoices: Gstr1ExportInvoice[];
  nilExemptPaise: number;
  nonGstOutwardPaise: number;
  creditNoteCount: number;
  creditNoteNumbers: string[];
  creditNoteCancelledCount: number;
  debitNoteCount: number;
  debitNoteNumbers: string[];
  debitNoteCancelledCount: number;
}

export type Rule88AStrategy =
  | 'OPTIMAL'
  | 'EQUAL_SPLIT'
  | 'CGST_FIRST'
  | 'SGST_FIRST'
  | 'CUSTOM';

export const RULE88A_STRATEGY_META: Record<
  Rule88AStrategy,
  { displayName: string; shortDesc: string }
> = {
  OPTIMAL: {
    displayName: 'Optimal (Min Cash)',
    shortDesc: 'Dynamically targets deficits to legally minimize cash outgo',
  },
  EQUAL_SPLIT: {
    displayName: 'Equal Split (50:50)',
    shortDesc: 'Splits remaining IGST 50% to CGST and 50% to SGST',
  },
  CGST_FIRST: {
    displayName: 'CGST Priority',
    shortDesc: 'Exhausts IGST towards CGST first, balance to SGST',
  },
  SGST_FIRST: {
    displayName: 'SGST Priority',
    shortDesc: 'Exhausts IGST towards SGST first, balance to CGST',
  },
  CUSTOM: {
    displayName: 'Custom Proportion',
    shortDesc: 'User-specified allocation percentage or rupee amount',
  },
};

export interface Rule88ASetOffMatrix {
  grossIgstPaise: number;
  grossCgstPaise: number;
  grossSgstPaise: number;
  totalGrossLiabilityPaise: number;
  itcIgstPaise: number;
  itcCgstPaise: number;
  itcSgstPaise: number;
  totalItcAvailablePaise: number;
  igstUsedForIgstPaise: number;
  igstUsedForCgstPaise: number;
  igstUsedForSgstPaise: number;
  totalIgstUtilizedPaise: number;
  cgstUsedForCgstPaise: number;
  cgstUsedForIgstPaise: number;
  totalCgstUtilizedPaise: number;
  sgstUsedForSgstPaise: number;
  sgstUsedForIgstPaise: number;
  totalSgstUtilizedPaise: number;
  cashIgstPaise: number;
  cashCgstPaise: number;
  cashSgstPaise: number;
  totalCashPayablePaise: number;
  carriedForwardIgstPaise: number;
  carriedForwardCgstPaise: number;
  carriedForwardSgstPaise: number;
  totalCarriedForwardItcPaise: number;
  strategy: Rule88AStrategy;
  cashSavingsPaise: number;
  explanationSteps: string[];
}

export interface Rule88AStrategyComparison {
  optimal: Rule88ASetOffMatrix;
  equalSplit: Rule88ASetOffMatrix;
  cgstFirst: Rule88ASetOffMatrix;
  sgstFirst: Rule88ASetOffMatrix;
  cashSavedPaise: number;
}

export interface StatutoryGstr3bData {
  outwardTaxablePaise: number;
  outwardIgstPaise: number;
  outwardCgstPaise: number;
  outwardSgstPaise: number;
  eligibleItcIgstPaise: number;
  eligibleItcCgstPaise: number;
  eligibleItcSgstPaise: number;
  totalEligibleItcPaise: number;
  setOffMatrix: Rule88ASetOffMatrix;
}

export interface StatutoryCmp08Data {
  turnoverPaise: number;
  compositionRatePercent: number;
  centralTaxPaise: number;
  stateTaxPaise: number;
  totalCompositionTaxPaise: number;
  rcmTurnoverPaise: number;
  rcmCentralTaxPaise: number;
  rcmStateTaxPaise: number;
  rcmIntegratedTaxPaise: number;
  rcmTotalTaxPaise: number;
  interestPaise: number;
  netCashPayablePaise: number;
  transactionsCount: number;
}

export function computeRule88ASetOff(
  grossIgst: number,
  grossCgst: number,
  grossSgst: number,
  itcIgst: number,
  itcCgst: number,
  itcSgst: number,
  strategy: Rule88AStrategy = 'OPTIMAL',
  customIgstForCgstPercent?: number,
  customIgstForCgstPaise?: number
): Rule88ASetOffMatrix {
  const steps: string[] = [];
  let remGrossIgst = Math.max(0, grossIgst);
  let remGrossCgst = Math.max(0, grossCgst);
  let remGrossSgst = Math.max(0, grossSgst);

  let remItcIgst = Math.max(0, itcIgst);
  let remItcCgst = Math.max(0, itcCgst);
  let remItcSgst = Math.max(0, itcSgst);

  // Step 1: Exhaust IGST credit against IGST liability first
  const igstForIgst = Math.min(remGrossIgst, remItcIgst);
  remGrossIgst -= igstForIgst;
  remItcIgst -= igstForIgst;
  steps.push(
    `Step 1: Utilized IGST credit of ${formatPaise(igstForIgst)} against IGST liability. Remaining IGST liability: ${formatPaise(remGrossIgst)}, Balance IGST credit: ${formatPaise(remItcIgst)}.`
  );

  let igstForCgst = 0;
  let igstForSgst = 0;

  if (remItcIgst > 0) {
    if (strategy === 'OPTIMAL') {
      const deficitCgst = Math.max(0, remGrossCgst - remItcCgst);
      const deficitSgst = Math.max(0, remGrossSgst - remItcSgst);
      const totalDeficit = deficitCgst + deficitSgst;
      if (totalDeficit > 0) {
        if (remItcIgst <= totalDeficit) {
          if (deficitCgst > deficitSgst) {
            const firstCgst = Math.min(remItcIgst, deficitCgst - deficitSgst);
            igstForCgst += firstCgst;
            const leftover = remItcIgst - firstCgst;
            const half = Math.floor(leftover / 2);
            igstForCgst += half;
            igstForSgst += leftover - half;
          } else if (deficitSgst > deficitCgst) {
            const firstSgst = Math.min(remItcIgst, deficitSgst - deficitCgst);
            igstForSgst += firstSgst;
            const leftover = remItcIgst - firstSgst;
            const half = Math.floor(leftover / 2);
            igstForSgst += half;
            igstForCgst += leftover - half;
          } else {
            const half = Math.floor(remItcIgst / 2);
            igstForCgst += half;
            igstForSgst += remItcIgst - half;
          }
        } else {
          igstForCgst = deficitCgst;
          igstForSgst = deficitSgst;
          let surplus = remItcIgst - totalDeficit;
          const addCgst = Math.min(Math.max(0, remGrossCgst - igstForCgst), Math.floor(surplus / 2));
          igstForCgst += addCgst;
          surplus -= addCgst;
          const addSgst = Math.min(Math.max(0, remGrossSgst - igstForSgst), surplus);
          igstForSgst += addSgst;
          surplus -= addSgst;
          if (surplus > 0) {
            const extraC = Math.min(Math.max(0, remGrossCgst - igstForCgst), surplus);
            igstForCgst += extraC;
          }
        }
      } else {
        const half = Math.floor(remItcIgst / 2);
        const cAlloc = Math.min(remGrossCgst, half);
        igstForCgst = cAlloc;
        let left = remItcIgst - cAlloc;
        const sAlloc = Math.min(remGrossSgst, left);
        igstForSgst = sAlloc;
        left -= sAlloc;
        if (left > 0 && remGrossCgst > igstForCgst) {
          igstForCgst += Math.min(remGrossCgst - igstForCgst, left);
        }
      }
    } else if (strategy === 'EQUAL_SPLIT') {
      const half = Math.floor(remItcIgst / 2);
      igstForCgst = Math.min(remGrossCgst, half);
      const rem = remItcIgst - igstForCgst;
      igstForSgst = Math.min(remGrossSgst, rem);
      const rem2 = rem - igstForSgst;
      if (rem2 > 0 && remGrossCgst > igstForCgst) {
        igstForCgst += Math.min(remGrossCgst - igstForCgst, rem2);
      }
    } else if (strategy === 'CGST_FIRST') {
      igstForCgst = Math.min(remGrossCgst, remItcIgst);
      igstForSgst = Math.min(remGrossSgst, remItcIgst - igstForCgst);
    } else if (strategy === 'SGST_FIRST') {
      igstForSgst = Math.min(remGrossSgst, remItcIgst);
      igstForCgst = Math.min(remGrossCgst, remItcIgst - igstForSgst);
    } else {
      const desiredCgst =
        customIgstForCgstPaise !== undefined
          ? customIgstForCgstPaise
          : customIgstForCgstPercent !== undefined
            ? Math.floor((remItcIgst * Math.min(100, Math.max(0, customIgstForCgstPercent))) / 100)
            : Math.floor(remItcIgst / 2);
      igstForCgst = Math.min(remGrossCgst, Math.min(remItcIgst, Math.max(0, desiredCgst)));
      const rem = remItcIgst - igstForCgst;
      igstForSgst = Math.min(remGrossSgst, rem);
      const rem2 = rem - igstForSgst;
      if (rem2 > 0 && remGrossCgst > igstForCgst) {
        igstForCgst += Math.min(remGrossCgst - igstForCgst, rem2);
      }
    }

    remGrossCgst = Math.max(0, remGrossCgst - igstForCgst);
    remGrossSgst = Math.max(0, remGrossSgst - igstForSgst);
    remItcIgst = Math.max(0, remItcIgst - (igstForCgst + igstForSgst));
    steps.push(
      `Step 2: Allocated balance IGST credit [${RULE88A_STRATEGY_META[strategy].displayName}]: ${formatPaise(igstForCgst)} to CGST and ${formatPaise(igstForSgst)} to SGST.`
    );
  } else {
    steps.push('Step 2: No balance IGST credit remaining after Step 1.');
  }

  const cgstForCgst = Math.min(remGrossCgst, remItcCgst);
  remGrossCgst -= cgstForCgst;
  remItcCgst -= cgstForCgst;
  const cgstForIgst = Math.min(remGrossIgst, remItcCgst);
  remGrossIgst -= cgstForIgst;
  remItcCgst -= cgstForIgst;
  steps.push(
    `Step 3: Utilized CGST credit: ${formatPaise(cgstForCgst)} against CGST and ${formatPaise(cgstForIgst)} against IGST (cross-utilization with SGST prohibited u/s 49B).`
  );

  const sgstForSgst = Math.min(remGrossSgst, remItcSgst);
  remGrossSgst -= sgstForSgst;
  remItcSgst -= sgstForSgst;
  const sgstForIgst = Math.min(remGrossIgst, remItcSgst);
  remGrossIgst -= sgstForIgst;
  remItcSgst -= sgstForIgst;
  steps.push(
    `Step 4: Utilized SGST credit: ${formatPaise(sgstForSgst)} against SGST and ${formatPaise(sgstForIgst)} against IGST (cross-utilization with CGST prohibited u/s 49B).`
  );

  const cashIgst = Math.max(0, remGrossIgst);
  const cashCgst = Math.max(0, remGrossCgst);
  const cashSgst = Math.max(0, remGrossSgst);
  const totalCash = cashIgst + cashCgst + cashSgst;
  const totalCarried = remItcIgst + remItcCgst + remItcSgst;
  steps.push(
    `Step 5: Net cash payable: ${formatPaise(totalCash)} | Carry-forward ITC: ${formatPaise(totalCarried)}.`
  );

  let cashSavingsPaise = 0;
  if (strategy !== 'EQUAL_SPLIT') {
    const eq = computeRule88ASetOff(
      grossIgst,
      grossCgst,
      grossSgst,
      itcIgst,
      itcCgst,
      itcSgst,
      'EQUAL_SPLIT'
    );
    cashSavingsPaise = Math.max(0, eq.totalCashPayablePaise - totalCash);
  }

  return {
    grossIgstPaise: grossIgst,
    grossCgstPaise: grossCgst,
    grossSgstPaise: grossSgst,
    totalGrossLiabilityPaise: grossIgst + grossCgst + grossSgst,
    itcIgstPaise: itcIgst,
    itcCgstPaise: itcCgst,
    itcSgstPaise: itcSgst,
    totalItcAvailablePaise: itcIgst + itcCgst + itcSgst,
    igstUsedForIgstPaise: igstForIgst,
    igstUsedForCgstPaise: igstForCgst,
    igstUsedForSgstPaise: igstForSgst,
    totalIgstUtilizedPaise: igstForIgst + igstForCgst + igstForSgst,
    cgstUsedForCgstPaise: cgstForCgst,
    cgstUsedForIgstPaise: cgstForIgst,
    totalCgstUtilizedPaise: cgstForCgst + cgstForIgst,
    sgstUsedForSgstPaise: sgstForSgst,
    sgstUsedForIgstPaise: sgstForIgst,
    totalSgstUtilizedPaise: sgstForSgst + sgstForIgst,
    cashIgstPaise: cashIgst,
    cashCgstPaise: cashCgst,
    cashSgstPaise: cashSgst,
    totalCashPayablePaise: totalCash,
    carriedForwardIgstPaise: remItcIgst,
    carriedForwardCgstPaise: remItcCgst,
    carriedForwardSgstPaise: remItcSgst,
    totalCarriedForwardItcPaise: totalCarried,
    strategy,
    cashSavingsPaise,
    explanationSteps: steps,
  };
}

export function compareAllRule88AStrategies(
  grossIgst: number,
  grossCgst: number,
  grossSgst: number,
  itcIgst: number,
  itcCgst: number,
  itcSgst: number
): Rule88AStrategyComparison {
  const optimal = computeRule88ASetOff(grossIgst, grossCgst, grossSgst, itcIgst, itcCgst, itcSgst, 'OPTIMAL');
  const equalSplit = computeRule88ASetOff(grossIgst, grossCgst, grossSgst, itcIgst, itcCgst, itcSgst, 'EQUAL_SPLIT');
  const cgstFirst = computeRule88ASetOff(grossIgst, grossCgst, grossSgst, itcIgst, itcCgst, itcSgst, 'CGST_FIRST');
  const sgstFirst = computeRule88ASetOff(grossIgst, grossCgst, grossSgst, itcIgst, itcCgst, itcSgst, 'SGST_FIRST');
  return {
    optimal,
    equalSplit,
    cgstFirst,
    sgstFirst,
    cashSavedPaise: Math.max(0, equalSplit.totalCashPayablePaise - optimal.totalCashPayablePaise),
  };
}

export function computeStatutoryGstr1(
  transactions: Transaction[],
  parties: Party[],
  profile: BusinessProfile,
  periodFilter: GstPeriodFilter = 'AllTime',
  prefilledInvoices: ImportedGstr1Invoice[] = []
): StatutoryGstr1Data {
  const b2bList: Gstr1B2bInvoice[] = [];
  const cdnrList: Gstr1CreditDebitNote[] = [];
  const exportList: Gstr1ExportInvoice[] = [];
  const b2csMap = new Map<string, Gstr1B2csSupply>();
  const hsnMap = new Map<string, Gstr1HsnItem>();

  let b2bCount = 0;
  let b2bTaxable = 0;
  let b2bCgst = 0;
  let b2bSgst = 0;
  let b2bIgst = 0;
  let b2cCount = 0;
  let b2cTaxable = 0;
  let b2cCgst = 0;
  let b2cSgst = 0;
  let b2cIgst = 0;
  let exportTaxable = 0;
  let exportIgst = 0;
  let nilExemptPaise = 0;
  let nonGstOutwardPaise = 0;
  let totalIssued = 0;
  let cancelledCount = 0;
  const invoiceNumbers: string[] = [];
  let creditNoteCount = 0;
  let creditNoteCancelledCount = 0;
  const creditNoteNumbers: string[] = [];
  let debitNoteCount = 0;
  let debitNoteCancelledCount = 0;
  const debitNoteNumbers: string[] = [];

  const filteredTxns = transactions.filter((t) => isDateInPeriod(t.date, periodFilter));

  const addToHsn = (
    hsnCode: string,
    desc: string,
    uqc: string,
    qtyMilli: number,
    gstRateBps: number,
    taxable: number,
    cgst: number,
    sgst: number,
    igst: number
  ) => {
    const cleanHsn = hsnCode.trim() || '9999';
    const key = `${cleanHsn}|${gstRateBps}`;
    const prev = hsnMap.get(key);
    const wholeRate = Math.round(gstRateBps / 100);
    if (!prev) {
      hsnMap.set(key, {
        hsnCode: cleanHsn,
        description: desc,
        uqc: uqc.trim() || 'NOS',
        totalQuantity: Math.floor((qtyMilli + 500) / 1000),
        taxablePaise: taxable,
        cgstPaise: cgst,
        sgstPaise: sgst,
        igstPaise: igst,
        gstRate: wholeRate,
        totalQuantityMilli: qtyMilli,
        gstRateBps,
      });
    } else {
      const nextMilli = prev.totalQuantityMilli + qtyMilli;
      hsnMap.set(key, {
        ...prev,
        totalQuantity: Math.floor((nextMilli + 500) / 1000),
        totalQuantityMilli: nextMilli,
        taxablePaise: prev.taxablePaise + taxable,
        cgstPaise: prev.cgstPaise + cgst,
        sgstPaise: prev.sgstPaise + sgst,
        igstPaise: prev.igstPaise + igst,
      });
    }
  };

  for (const txn of filteredTxns) {
    if (txn.type === 'NonGstBill') {
      if (!txn.isVoided) nonGstOutwardPaise += txn.amountPaise;
      continue;
    }

    if (txn.type === 'CreditNote' || txn.type === 'DebitNote') {
      const party = parties.find((p) => p.id === txn.partyId);
      if (txn.type === 'DebitNote' && party?.isSupplier) continue;
      const isDebit = txn.type === 'DebitNote';
      if (isDebit) {
        debitNoteCount++;
        if (txn.invoiceNumber) debitNoteNumbers.push(txn.invoiceNumber);
      } else {
        creditNoteCount++;
        if (txn.invoiceNumber) creditNoteNumbers.push(txn.invoiceNumber);
      }
      if (txn.isVoided) {
        if (isDebit) debitNoteCancelledCount++;
        else creditNoteCancelledCount++;
        continue;
      }

      const isB2b = !!party && isValidGstinFormat(party.gstin);
      const buyerPos = party?.stateCode?.trim() || profile.homeStateCode;
      let txnTaxable = 0;
      let txnCgst = 0;
      let txnSgst = 0;
      let txnIgst = 0;
      const rateBuckets = new Map<number, Gstr1B2bRateItem>();

      if (txn.items.length > 0) {
        for (const itm of txn.items) {
          txnTaxable += itm.taxableAmountPaise;
          txnCgst += itm.cgstPaise;
          txnSgst += itm.sgstPaise;
          txnIgst += itm.igstPaise;
          const prev = rateBuckets.get(itm.gstRateBps);
          rateBuckets.set(
            itm.gstRateBps,
            prev
              ? {
                  ...prev,
                  taxablePaise: prev.taxablePaise + itm.taxableAmountPaise,
                  cgstPaise: prev.cgstPaise + itm.cgstPaise,
                  sgstPaise: prev.sgstPaise + itm.sgstPaise,
                  igstPaise: prev.igstPaise + itm.igstPaise,
                }
              : {
                  gstRate: Math.round(itm.gstRateBps / 100),
                  taxablePaise: itm.taxableAmountPaise,
                  cgstPaise: itm.cgstPaise,
                  sgstPaise: itm.sgstPaise,
                  igstPaise: itm.igstPaise,
                  gstRateBps: itm.gstRateBps,
                }
          );
        }
      } else {
        txnTaxable = txn.amountPaise;
        rateBuckets.set(0, {
          gstRate: 0,
          taxablePaise: txnTaxable,
          cgstPaise: 0,
          sgstPaise: 0,
          igstPaise: 0,
          gstRateBps: 0,
        });
      }

      if (isB2b && party) {
        cdnrList.push({
          partyGstin: party.gstin.trim().toUpperCase(),
          partyName: party.name,
          noteNumber: txn.invoiceNumber || txn.id.slice(-6).toUpperCase(),
          noteDate: txn.date,
          noteType: isDebit ? 'D' : 'C',
          posStateCode: buyerPos,
          taxablePaise: txnTaxable,
          cgstPaise: txnCgst,
          sgstPaise: txnSgst,
          igstPaise: txnIgst,
          totalPaise: txnTaxable + txnCgst + txnSgst + txnIgst,
          rateBreakdown: Array.from(rateBuckets.values()),
        });
      }
      continue;
    }

    if (txn.type !== 'Sale') continue;
    totalIssued++;
    if (txn.invoiceNumber) invoiceNumbers.push(txn.invoiceNumber);
    if (txn.isVoided) {
      cancelledCount++;
      continue;
    }

    const party = parties.find((p) => p.id === txn.partyId);
    const isB2b = !!party && isValidGstinFormat(party.gstin);
    const buyerPos = party?.stateCode?.trim() || profile.homeStateCode;
    const isExport = buyerPos === '96';
    const isInterstate = buyerPos !== '' && buyerPos !== profile.homeStateCode;

    let txnTaxable = 0;
    let txnCgst = 0;
    let txnSgst = 0;
    let txnIgst = 0;
    const rateBuckets = new Map<number, Gstr1B2bRateItem>();

    if (txn.items.length > 0) {
      for (const itm of txn.items) {
        txnTaxable += itm.taxableAmountPaise;
        txnCgst += itm.cgstPaise;
        txnSgst += itm.sgstPaise;
        txnIgst += itm.igstPaise;
        const prev = rateBuckets.get(itm.gstRateBps);
        rateBuckets.set(
          itm.gstRateBps,
          prev
            ? {
                ...prev,
                taxablePaise: prev.taxablePaise + itm.taxableAmountPaise,
                cgstPaise: prev.cgstPaise + itm.cgstPaise,
                sgstPaise: prev.sgstPaise + itm.sgstPaise,
                igstPaise: prev.igstPaise + itm.igstPaise,
              }
            : {
                gstRate: Math.round(itm.gstRateBps / 100),
                taxablePaise: itm.taxableAmountPaise,
                cgstPaise: itm.cgstPaise,
                sgstPaise: itm.sgstPaise,
                igstPaise: itm.igstPaise,
                gstRateBps: itm.gstRateBps,
              }
        );

        if (!isB2b && !isExport && itm.gstRateBps === 0) nilExemptPaise += itm.taxableAmountPaise;
        addToHsn(
          itm.hsnCode,
          itm.name,
          itm.unit,
          itm.quantityMilli,
          itm.gstRateBps,
          itm.taxableAmountPaise,
          itm.cgstPaise,
          itm.sgstPaise,
          itm.igstPaise
        );

        if (!isB2b && !isExport) {
          const bKey = `${buyerPos}|${itm.gstRateBps}`;
          const prevB = b2csMap.get(bKey);
          b2csMap.set(
            bKey,
            prevB
              ? {
                  ...prevB,
                  taxablePaise: prevB.taxablePaise + itm.taxableAmountPaise,
                  cgstPaise: prevB.cgstPaise + itm.cgstPaise,
                  sgstPaise: prevB.sgstPaise + itm.sgstPaise,
                  igstPaise: prevB.igstPaise + itm.igstPaise,
                }
              : {
                  posStateCode: buyerPos,
                  supplyType: isInterstate ? 'INTER' : 'INTRA',
                  gstRate: Math.round(itm.gstRateBps / 100),
                  taxablePaise: itm.taxableAmountPaise,
                  cgstPaise: itm.cgstPaise,
                  sgstPaise: itm.sgstPaise,
                  igstPaise: itm.igstPaise,
                  gstRateBps: itm.gstRateBps,
                }
          );
        }
      }
    } else {
      txnTaxable = txn.amountPaise;
      rateBuckets.set(0, {
        gstRate: 0,
        taxablePaise: txnTaxable,
        cgstPaise: 0,
        sgstPaise: 0,
        igstPaise: 0,
        gstRateBps: 0,
      });
      if (!isB2b && !isExport) {
        nilExemptPaise += txnTaxable;
        const bKey = `${buyerPos}|0`;
        const prevB = b2csMap.get(bKey);
        b2csMap.set(
          bKey,
          prevB
            ? { ...prevB, taxablePaise: prevB.taxablePaise + txnTaxable }
            : {
                posStateCode: buyerPos,
                supplyType: isInterstate ? 'INTER' : 'INTRA',
                gstRate: 0,
                taxablePaise: txnTaxable,
                cgstPaise: 0,
                sgstPaise: 0,
                igstPaise: 0,
                gstRateBps: 0,
              }
        );
      }
    }

    if (isExport) {
      const rateBps = Array.from(rateBuckets.keys())[0] || 0;
      exportList.push({
        invoiceNumber: txn.invoiceNumber || txn.id.slice(-6).toUpperCase(),
        date: txn.date,
        exportType: txnIgst > 0 ? 'WPAY' : 'WOPAY',
        taxablePaise: txnTaxable,
        igstPaise: txnIgst,
        totalPaise: txnTaxable + txnIgst,
        gstRate: Math.floor(rateBps / 100),
        gstRateBps: rateBps,
      });
      exportTaxable += txnTaxable;
      exportIgst += txnIgst;
    } else if (isB2b && party) {
      b2bCount++;
      b2bTaxable += txnTaxable;
      b2bCgst += txnCgst;
      b2bSgst += txnSgst;
      b2bIgst += txnIgst;
      b2bList.push({
        partyId: txn.partyId || '',
        gstin: party.gstin.trim().toUpperCase(),
        partyName: party.name || 'B2B Customer',
        invoiceNumber: txn.invoiceNumber || txn.id.slice(-6).toUpperCase(),
        date: txn.date,
        posStateCode: buyerPos,
        taxablePaise: txnTaxable,
        cgstPaise: txnCgst,
        sgstPaise: txnSgst,
        igstPaise: txnIgst,
        totalPaise: txnTaxable + txnCgst + txnSgst + txnIgst,
        isInterstate,
        itemsSummary:
          txn.items.length > 0
            ? txn.items.map((it) => `${it.name} (${it.quantityMilli / 1000})`).join(', ')
            : 'General Goods/Services',
        isPrefilledFromExcel: false,
        rateBreakdown: Array.from(rateBuckets.values()),
      });
    } else {
      b2cCount++;
      b2cTaxable += txnTaxable;
      b2cCgst += txnCgst;
      b2cSgst += txnSgst;
      b2cIgst += txnIgst;
    }
  }

  const filteredPrefilled = prefilledInvoices.filter((imp) => isDateInPeriod(imp.date, periodFilter));
  for (const imp of filteredPrefilled) {
    totalIssued++;
    if (imp.invoiceNumber) invoiceNumbers.push(imp.invoiceNumber);
    if (imp.isB2b) {
      b2bCount++;
      b2bTaxable += imp.taxablePaise;
      b2bCgst += imp.cgstPaise;
      b2bSgst += imp.sgstPaise;
      b2bIgst += imp.igstPaise;
      b2bList.push({
        partyId: imp.id,
        gstin: imp.gstin,
        partyName: imp.partyName,
        invoiceNumber: imp.invoiceNumber,
        date: imp.date,
        posStateCode: imp.posStateCode,
        taxablePaise: imp.taxablePaise,
        cgstPaise: imp.cgstPaise,
        sgstPaise: imp.sgstPaise,
        igstPaise: imp.igstPaise,
        totalPaise: imp.totalPaise,
        isInterstate: imp.isInterstate,
        itemsSummary: 'Imported via Excel GSTR-1 Pre-fill',
        isPrefilledFromExcel: true,
        rateBreakdown: [
          {
            gstRate: imp.gstRatePercent,
            taxablePaise: imp.taxablePaise,
            cgstPaise: imp.cgstPaise,
            sgstPaise: imp.sgstPaise,
            igstPaise: imp.igstPaise,
            gstRateBps: imp.gstRateBps,
          },
        ],
      });
    } else {
      b2cCount++;
      b2cTaxable += imp.taxablePaise;
      b2cCgst += imp.cgstPaise;
      b2cSgst += imp.sgstPaise;
      b2cIgst += imp.igstPaise;
      if (imp.gstRateBps === 0) nilExemptPaise += imp.taxablePaise;
      const bKey = `${imp.posStateCode}|${imp.gstRateBps}`;
      const prevB = b2csMap.get(bKey);
      b2csMap.set(
        bKey,
        prevB
          ? {
              ...prevB,
              taxablePaise: prevB.taxablePaise + imp.taxablePaise,
              cgstPaise: prevB.cgstPaise + imp.cgstPaise,
              sgstPaise: prevB.sgstPaise + imp.sgstPaise,
              igstPaise: prevB.igstPaise + imp.igstPaise,
            }
          : {
              posStateCode: imp.posStateCode,
              supplyType: imp.isInterstate ? 'INTER' : 'INTRA',
              gstRate: imp.gstRatePercent,
              taxablePaise: imp.taxablePaise,
              cgstPaise: imp.cgstPaise,
              sgstPaise: imp.sgstPaise,
              igstPaise: imp.igstPaise,
              gstRateBps: imp.gstRateBps,
            }
      );
    }

    addToHsn(
      imp.hsnCode,
      `${imp.partyName} (Excel)`,
      'OTH',
      1000,
      imp.gstRateBps,
      imp.taxablePaise,
      imp.cgstPaise,
      imp.sgstPaise,
      imp.igstPaise
    );
  }

  invoiceNumbers.sort();
  creditNoteNumbers.sort();
  debitNoteNumbers.sort();

  const totalTaxable = b2bTaxable + b2cTaxable + exportTaxable;
  const totalCgst = b2bCgst + b2cCgst;
  const totalSgst = b2bSgst + b2cSgst;
  const totalIgst = b2bIgst + b2cIgst + exportIgst;

  return {
    b2bInvoices: b2bList,
    b2csSupplies: Array.from(b2csMap.values()),
    hsnSummary: Array.from(hsnMap.values()),
    docSummary: {
      minInvoiceNumber: invoiceNumbers[0] || '',
      maxInvoiceNumber: invoiceNumbers[invoiceNumbers.length - 1] || '',
      totalIssued,
      cancelledCount,
      netIssuedCount: Math.max(0, totalIssued - cancelledCount),
    },
    b2bCount,
    b2bTaxablePaise: b2bTaxable,
    b2bCgstPaise: b2bCgst,
    b2bSgstPaise: b2bSgst,
    b2bIgstPaise: b2bIgst,
    b2cCount,
    b2cTaxablePaise: b2cTaxable,
    b2cCgstPaise: b2cCgst,
    b2cSgstPaise: b2cSgst,
    b2cIgstPaise: b2cIgst,
    totalTaxablePaise: totalTaxable,
    totalCgstPaise: totalCgst,
    totalSgstPaise: totalSgst,
    totalIgstPaise: totalIgst,
    totalTaxPaise: totalCgst + totalSgst + totalIgst,
    prefilledExcelCount: filteredPrefilled.length,
    cdnrNotes: cdnrList,
    exportInvoices: exportList,
    nilExemptPaise,
    nonGstOutwardPaise,
    creditNoteCount,
    creditNoteNumbers,
    creditNoteCancelledCount,
    debitNoteCount,
    debitNoteNumbers,
    debitNoteCancelledCount,
  };
}

export function computeStatutoryGstr3b(
  transactions: Transaction[],
  parties: Party[],
  _profile: BusinessProfile,
  gstr1: StatutoryGstr1Data,
  periodFilter: GstPeriodFilter = 'AllTime',
  strategy: Rule88AStrategy = 'OPTIMAL',
  customIgstForCgstPercent?: number,
  customIgstForCgstPaise?: number
): StatutoryGstr3bData {
  const filteredTxns = transactions.filter((t) => !t.isVoided && isDateInPeriod(t.date, periodFilter));
  let cnTaxable = 0;
  let cnIgst = 0;
  let cnCgst = 0;
  let cnSgst = 0;
  for (const cn of filteredTxns) {
    if (cn.type !== 'CreditNote') continue;
    if (cn.items.length > 0) {
      for (const itm of cn.items) {
        cnTaxable += itm.taxableAmountPaise;
        cnIgst += itm.igstPaise;
        cnCgst += itm.cgstPaise;
        cnSgst += itm.sgstPaise;
      }
    } else cnTaxable += cn.amountPaise;
  }

  let dnOutTaxable = 0;
  let dnOutIgst = 0;
  let dnOutCgst = 0;
  let dnOutSgst = 0;
  for (const dn of filteredTxns) {
    if (dn.type !== 'DebitNote') continue;
    const p = parties.find((x) => x.id === dn.partyId);
    if (p?.isSupplier) continue;
    if (dn.items.length > 0) {
      for (const itm of dn.items) {
        dnOutTaxable += itm.taxableAmountPaise;
        dnOutIgst += itm.igstPaise;
        dnOutCgst += itm.cgstPaise;
        dnOutSgst += itm.sgstPaise;
      }
    } else dnOutTaxable += dn.amountPaise;
  }

  const outwardTaxable = Math.max(0, gstr1.totalTaxablePaise + dnOutTaxable - cnTaxable);
  const outwardIgst = Math.max(0, gstr1.totalIgstPaise + dnOutIgst - cnIgst);
  const outwardCgst = Math.max(0, gstr1.totalCgstPaise + dnOutCgst - cnCgst);
  const outwardSgst = Math.max(0, gstr1.totalSgstPaise + dnOutSgst - cnSgst);

  let itcIgst = 0;
  let itcCgst = 0;
  let itcSgst = 0;
  for (const pTxn of filteredTxns) {
    const isPurchase = pTxn.type === 'Purchase';
    const isSupplierDn =
      pTxn.type === 'DebitNote' && !!parties.find((p) => p.id === pTxn.partyId)?.isSupplier;
    if (!isPurchase && !isSupplierDn) continue;
    const sign = isSupplierDn ? -1 : 1;
    for (const itm of pTxn.items) {
      itcIgst += itm.igstPaise * sign;
      itcCgst += itm.cgstPaise * sign;
      itcSgst += itm.sgstPaise * sign;
    }
  }

  const netItcIgst = Math.max(0, itcIgst);
  const netItcCgst = Math.max(0, itcCgst);
  const netItcSgst = Math.max(0, itcSgst);
  const totalItc = netItcIgst + netItcCgst + netItcSgst;

  const setOffMatrix = computeRule88ASetOff(
    outwardIgst,
    outwardCgst,
    outwardSgst,
    netItcIgst,
    netItcCgst,
    netItcSgst,
    strategy,
    customIgstForCgstPercent,
    customIgstForCgstPaise
  );

  return {
    outwardTaxablePaise: outwardTaxable,
    outwardIgstPaise: outwardIgst,
    outwardCgstPaise: outwardCgst,
    outwardSgstPaise: outwardSgst,
    eligibleItcIgstPaise: netItcIgst,
    eligibleItcCgstPaise: netItcCgst,
    eligibleItcSgstPaise: netItcSgst,
    totalEligibleItcPaise: totalItc,
    setOffMatrix,
  };
}

export function computeStatutoryCmp08(
  transactions: Transaction[],
  _profile: BusinessProfile,
  periodFilter: GstPeriodFilter = 'AllTime',
  compositionRatePercent = 1.0
): StatutoryCmp08Data {
  let turnoverPaise = 0;
  let rcmTurnoverPaise = 0;
  let rcmCentralTax = 0;
  let rcmStateTax = 0;
  let salesCount = 0;

  for (const txn of transactions) {
    if (txn.isVoided || !isDateInPeriod(txn.date, periodFilter)) continue;
    if (txn.type === 'Sale' || txn.type === 'NonGstBill') {
      turnoverPaise += txn.amountPaise;
      salesCount++;
    } else if (txn.type === 'CreditNote') {
      turnoverPaise -= txn.amountPaise;
    } else if (txn.type === 'Expense' && txn.expenseCategory === 'Direct') {
      rcmTurnoverPaise += txn.amountPaise;
      const rcmEach = Math.floor((txn.amountPaise * 250 + 5000) / 10000);
      rcmCentralTax += rcmEach;
      rcmStateTax += rcmEach;
    }
  }

  const netTurnover = Math.max(0, turnoverPaise);
  const totalBps = Math.min(10000, Math.max(0, Math.round(compositionRatePercent * 100)));
  const centralTax = Math.floor((netTurnover * totalBps + 10000) / 20000);
  const stateTax = centralTax;
  const totalCompositionTax = centralTax + stateTax;
  const rcmTotalTax = rcmCentralTax + rcmStateTax;

  return {
    turnoverPaise: netTurnover,
    compositionRatePercent,
    centralTaxPaise: centralTax,
    stateTaxPaise: stateTax,
    totalCompositionTaxPaise: totalCompositionTax,
    rcmTurnoverPaise,
    rcmCentralTaxPaise: rcmCentralTax,
    rcmStateTaxPaise: rcmStateTax,
    rcmIntegratedTaxPaise: 0,
    rcmTotalTaxPaise: rcmTotalTax,
    interestPaise: 0,
    netCashPayablePaise: totalCompositionTax + rcmTotalTax,
    transactionsCount: salesCount,
  };
}
