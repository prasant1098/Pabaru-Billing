import {
  BusinessProfile,
  Party,
  ProductItem,
  Transaction,
} from '../types/models';

export type CmaYearStage =
  | 'AuditedLastYear'
  | 'CurrentYearEstimate'
  | 'ProjectionYear1'
  | 'ProjectionYear2';

export interface CmaYearFinancials {
  stage: CmaYearStage;
  fyLabel: string;
  stageLabel: string;
  grossSalesPaise: number;
  salesReturnsAndDeductionsPaise: number;
  nonOperatingIncomePaise: number;
  rawMaterialPurchasesPaise: number;
  directWagesPaise: number;
  directPowerAndFreightPaise: number;
  otherDirectExpensesPaise: number;
  openingStockPaise: number;
  closingStockPaise: number;
  salariesAndAdminPaise: number;
  rentAndUtilitiesPaise: number;
  sellingAndMarketingPaise: number;
  otherOverheadsPaise: number;
  depreciationPaise: number;
  termLoanInterestPaise: number;
  workingCapitalInterestPaise: number;
  nonOperatingExpensesPaise: number;
  taxRatePercent: number;
  termLoanPrincipalInstallmentPaise: number;
  cashAndBankPaise: number;
  sundryDebtorsPaise: number;
  inventoryStockPaise: number;
  advancesToSuppliersPaise: number;
  otherCurrentAssetsPaise: number;
  grossFixedAssetsPaise: number;
  accumulatedDepreciationPaise: number;
  otherNonCurrentAssetsPaise: number;
  sundryCreditorsPaise: number;
  otherCurrentLiabilitiesPaise: number;
  provisionsPaise: number;
  existingBankCcLimitPaise: number;
  termLoanOutstandingPaise: number;
  unsecuredLoansPaise: number;
  shareCapitalPaise: number;
  reservesAndSurplusPaise: number;
  promoterDrawingsPaise: number;
}

export interface CmaYearComputed {
  netSalesPaise: number;
  costOfProductionPaise: number;
  costOfSalesPaise: number;
  cogsPaise: number;
  grossProfitPaise: number;
  gpMarginPercent: number;
  totalGeneralAdminSellingExpPaise: number;
  ebitdaPaise: number;
  ebitdaMarginPercent: number;
  ebitPaise: number;
  totalFinanceChargesPaise: number;
  operatingProfitAfterInterestPaise: number;
  pbtPaise: number;
  taxPaise: number;
  patPaise: number;
  patMarginPercent: number;
  retainedProfitPaise: number;
  cashAccrualsPaise: number;
  totalCurrentAssetsPaise: number;
  netFixedAssetsPaise: number;
  totalAssetsPaise: number;
  currentLiabilitiesOtherThanBankPaise: number;
  totalCurrentLiabilitiesPaise: number;
  totalTermLiabilitiesPaise: number;
  totalOutsideLiabilitiesPaise: number;
  tangibleNetWorthPaise: number;
  totalLiabilitiesAndCapitalPaise: number;
  netWorkingCapitalPaise: number;
  workingCapitalGapPaise: number;
  tandonMethod1MinNwcPaise: number;
  mpbfTandonMethod1Paise: number;
  tandonMethod2MinNwcPaise: number;
  mpbfTandonMethod2Paise: number;
  nayakTotalWcRequirementPaise: number;
  nayakPromoterMarginPaise: number;
  nayakCommitteeWorkingCapitalPaise: number;
  currentRatio: number;
  quickRatio: number;
  absoluteLiquidRatio: number;
  debtEquityRatio: number;
  tolToTnwRatio: number;
  proprietaryRatio: number;
  fixedAssetsToLongTermFundsRatio: number;
  interestCoverageRatio: number;
  dscr: number;
  debtorDays: number;
  creditorDays: number;
  inventoryDays: number;
  grossOperatingCycleDays: number;
  netWorkingCapitalCycleDays: number;
  nwcToNetSalesPercent: number;
  returnOnCapitalEmployedPercent: number;
}

export function computeCmaYearMetrics(y: CmaYearFinancials): CmaYearComputed {
  const netSalesPaise = Math.max(0, y.grossSalesPaise - y.salesReturnsAndDeductionsPaise);
  const costOfProductionPaise =
    y.rawMaterialPurchasesPaise +
    y.directWagesPaise +
    y.directPowerAndFreightPaise +
    y.otherDirectExpensesPaise;
  const costOfSalesPaise = costOfProductionPaise + y.openingStockPaise - y.closingStockPaise;
  const grossProfitPaise = netSalesPaise - costOfSalesPaise;
  const gpMarginPercent = netSalesPaise > 0 ? (grossProfitPaise / netSalesPaise) * 100 : 0;
  const totalGeneralAdminSellingExpPaise =
    y.salariesAndAdminPaise + y.rentAndUtilitiesPaise + y.sellingAndMarketingPaise + y.otherOverheadsPaise;
  const ebitdaPaise = grossProfitPaise - totalGeneralAdminSellingExpPaise;
  const ebitdaMarginPercent = netSalesPaise > 0 ? (ebitdaPaise / netSalesPaise) * 100 : 0;
  const ebitPaise = ebitdaPaise - y.depreciationPaise;
  const totalFinanceChargesPaise = y.termLoanInterestPaise + y.workingCapitalInterestPaise;
  const operatingProfitAfterInterestPaise = ebitPaise - totalFinanceChargesPaise;
  const pbtPaise =
    operatingProfitAfterInterestPaise + y.nonOperatingIncomePaise - y.nonOperatingExpensesPaise;
  const taxPaise = pbtPaise > 0 ? Math.round(pbtPaise * (y.taxRatePercent / 100)) : 0;
  const patPaise = pbtPaise - taxPaise;
  const patMarginPercent = netSalesPaise > 0 ? (patPaise / netSalesPaise) * 100 : 0;
  const retainedProfitPaise = Math.max(0, patPaise - y.promoterDrawingsPaise);
  const cashAccrualsPaise = patPaise + y.depreciationPaise;

  const totalCurrentAssetsPaise =
    y.cashAndBankPaise +
    y.sundryDebtorsPaise +
    y.inventoryStockPaise +
    y.advancesToSuppliersPaise +
    y.otherCurrentAssetsPaise;
  const netFixedAssetsPaise = Math.max(0, y.grossFixedAssetsPaise - y.accumulatedDepreciationPaise);
  const totalAssetsPaise = totalCurrentAssetsPaise + netFixedAssetsPaise + y.otherNonCurrentAssetsPaise;
  const currentLiabilitiesOtherThanBankPaise =
    y.sundryCreditorsPaise + y.otherCurrentLiabilitiesPaise + y.provisionsPaise;
  const totalCurrentLiabilitiesPaise = currentLiabilitiesOtherThanBankPaise + y.existingBankCcLimitPaise;
  const totalTermLiabilitiesPaise = y.termLoanOutstandingPaise + y.unsecuredLoansPaise;
  const totalOutsideLiabilitiesPaise = totalCurrentLiabilitiesPaise + totalTermLiabilitiesPaise;
  const tangibleNetWorthPaise = y.shareCapitalPaise + y.reservesAndSurplusPaise;
  const totalLiabilitiesAndCapitalPaise = totalOutsideLiabilitiesPaise + tangibleNetWorthPaise;
  const netWorkingCapitalPaise = totalCurrentAssetsPaise - totalCurrentLiabilitiesPaise;

  const workingCapitalGapPaise = Math.max(0, totalCurrentAssetsPaise - currentLiabilitiesOtherThanBankPaise);
  const tandonMethod1MinNwcPaise = Math.round(workingCapitalGapPaise * 0.25);
  const mpbfTandonMethod1Paise = Math.max(0, Math.round(workingCapitalGapPaise * 0.75));
  const tandonMethod2MinNwcPaise = Math.round(totalCurrentAssetsPaise * 0.25);
  const mpbfTandonMethod2Paise = Math.max(
    0,
    Math.round(totalCurrentAssetsPaise * 0.75 - currentLiabilitiesOtherThanBankPaise)
  );
  const nayakTotalWcRequirementPaise = Math.round(netSalesPaise * 0.25);
  const nayakPromoterMarginPaise = Math.round(netSalesPaise * 0.05);
  const nayakCommitteeWorkingCapitalPaise = Math.round(netSalesPaise * 0.2);

  const currentRatio =
    totalCurrentLiabilitiesPaise > 0 ? totalCurrentAssetsPaise / totalCurrentLiabilitiesPaise : 2.5;
  const quickRatio =
    totalCurrentLiabilitiesPaise > 0
      ? (totalCurrentAssetsPaise - y.inventoryStockPaise) / totalCurrentLiabilitiesPaise
      : 2.0;
  const absoluteLiquidRatio =
    totalCurrentLiabilitiesPaise > 0 ? y.cashAndBankPaise / totalCurrentLiabilitiesPaise : 0.6;
  const debtEquityRatio =
    tangibleNetWorthPaise > 0 ? y.termLoanOutstandingPaise / tangibleNetWorthPaise : 0.8;
  const tolToTnwRatio =
    tangibleNetWorthPaise > 0 ? totalOutsideLiabilitiesPaise / tangibleNetWorthPaise : 1.8;
  const proprietaryRatio = totalAssetsPaise > 0 ? tangibleNetWorthPaise / totalAssetsPaise : 0.45;
  const ltFunds = tangibleNetWorthPaise + y.termLoanOutstandingPaise;
  const fixedAssetsToLongTermFundsRatio = ltFunds > 0 ? netFixedAssetsPaise / ltFunds : 0.65;
  const interestCoverageRatio =
    totalFinanceChargesPaise > 0 ? ebitdaPaise / totalFinanceChargesPaise : 4.0;
  const debtObligations = y.termLoanPrincipalInstallmentPaise + y.termLoanInterestPaise;
  const dscr =
    debtObligations > 0
      ? (patPaise + y.depreciationPaise + y.termLoanInterestPaise) / debtObligations
      : 3.5;

  const debtorDays =
    netSalesPaise > 0 ? Math.floor((y.sundryDebtorsPaise / netSalesPaise) * 365) : 30;
  const creditorDays =
    y.rawMaterialPurchasesPaise > 0
      ? Math.floor((y.sundryCreditorsPaise / y.rawMaterialPurchasesPaise) * 365)
      : 30;
  const inventoryDays =
    costOfSalesPaise > 0 ? Math.floor((y.inventoryStockPaise / costOfSalesPaise) * 365) : 45;
  const grossOperatingCycleDays = debtorDays + inventoryDays;
  const netWorkingCapitalCycleDays = grossOperatingCycleDays - creditorDays;
  const nwcToNetSalesPercent =
    netSalesPaise > 0 ? (netWorkingCapitalPaise / netSalesPaise) * 100 : 0;
  const capEmployed =
    tangibleNetWorthPaise + totalTermLiabilitiesPaise + y.existingBankCcLimitPaise;
  const returnOnCapitalEmployedPercent =
    capEmployed > 0 ? (ebitPaise / capEmployed) * 100 : 18.0;

  return {
    netSalesPaise,
    costOfProductionPaise,
    costOfSalesPaise,
    cogsPaise: costOfSalesPaise,
    grossProfitPaise,
    gpMarginPercent,
    totalGeneralAdminSellingExpPaise,
    ebitdaPaise,
    ebitdaMarginPercent,
    ebitPaise,
    totalFinanceChargesPaise,
    operatingProfitAfterInterestPaise,
    pbtPaise,
    taxPaise,
    patPaise,
    patMarginPercent,
    retainedProfitPaise,
    cashAccrualsPaise,
    totalCurrentAssetsPaise,
    netFixedAssetsPaise,
    totalAssetsPaise,
    currentLiabilitiesOtherThanBankPaise,
    totalCurrentLiabilitiesPaise,
    totalTermLiabilitiesPaise,
    totalOutsideLiabilitiesPaise,
    tangibleNetWorthPaise,
    totalLiabilitiesAndCapitalPaise,
    netWorkingCapitalPaise,
    workingCapitalGapPaise,
    tandonMethod1MinNwcPaise,
    mpbfTandonMethod1Paise,
    tandonMethod2MinNwcPaise,
    mpbfTandonMethod2Paise,
    nayakTotalWcRequirementPaise,
    nayakPromoterMarginPaise,
    nayakCommitteeWorkingCapitalPaise,
    currentRatio,
    quickRatio,
    absoluteLiquidRatio,
    debtEquityRatio,
    tolToTnwRatio,
    proprietaryRatio,
    fixedAssetsToLongTermFundsRatio,
    interestCoverageRatio,
    dscr,
    debtorDays,
    creditorDays,
    inventoryDays,
    grossOperatingCycleDays,
    netWorkingCapitalCycleDays,
    nwcToNetSalesPercent,
    returnOnCapitalEmployedPercent,
  };
}

export interface CmaProjectDossier {
  baseStartYear: number;
  years: CmaYearFinancials[];
}

export interface CmaFundsFlowYear {
  fyLabel: string;
  stageLabel: string;
  patPaise: number;
  depreciationPaise: number;
  cashAccrualsPaise: number;
  capitalInfusionPaise: number;
  increaseInTermLoanPaise: number;
  increaseInUnsecuredLoansPaise: number;
  decreaseInFixedAssetsPaise: number;
  totalSourcesPaise: number;
  capitalExpenditurePaise: number;
  repaymentOfTermLoanPaise: number;
  decreaseInUnsecuredLoansPaise: number;
  promoterDrawingsPaise: number;
  totalApplicationsPaise: number;
  netSurplusOrDeficitPaise: number;
  openingNwcPaise: number;
  closingNwcPaise: number;
  changeInNwcPaise: number;
}

export function computeFundsFlowSchedule(dossier: CmaProjectDossier): CmaFundsFlowYear[] {
  const result: CmaFundsFlowYear[] = [];
  for (let i = 0; i < dossier.years.length; i++) {
    const curr = dossier.years[i];
    const currM = computeCmaYearMetrics(curr);
    const prev = i > 0 ? dossier.years[i - 1] : null;
    const prevM = prev ? computeCmaYearMetrics(prev) : null;

    const accruals = currM.patPaise + curr.depreciationPaise;
    const capDiff = prev ? curr.shareCapitalPaise - prev.shareCapitalPaise : 0;
    const capInfusion = capDiff > 0 ? capDiff : 0;
    const tlDiff = prev ? curr.termLoanOutstandingPaise - prev.termLoanOutstandingPaise : 0;
    const tlIncrease = tlDiff > 0 ? tlDiff : 0;
    const tlRepayment = tlDiff < 0 ? -tlDiff : curr.termLoanPrincipalInstallmentPaise;
    const unsecDiff = prev ? curr.unsecuredLoansPaise - prev.unsecuredLoansPaise : 0;
    const unsecIncrease = unsecDiff > 0 ? unsecDiff : 0;
    const unsecDecrease = unsecDiff < 0 ? -unsecDiff : 0;
    const gfaDiff = prev ? curr.grossFixedAssetsPaise - prev.grossFixedAssetsPaise : 0;
    const capex = gfaDiff > 0 ? gfaDiff : 0;
    const faSale = gfaDiff < 0 ? -gfaDiff : 0;

    const totalSources = accruals + capInfusion + tlIncrease + unsecIncrease + faSale;
    const totalApps = capex + tlRepayment + unsecDecrease + curr.promoterDrawingsPaise;
    const netSurplus = totalSources - totalApps;
    const opNwc = prevM ? prevM.netWorkingCapitalPaise : currM.netWorkingCapitalPaise - netSurplus;
    const clNwc = currM.netWorkingCapitalPaise;

    result.push({
      fyLabel: curr.fyLabel,
      stageLabel: curr.stageLabel,
      patPaise: currM.patPaise,
      depreciationPaise: curr.depreciationPaise,
      cashAccrualsPaise: accruals,
      capitalInfusionPaise: capInfusion,
      increaseInTermLoanPaise: tlIncrease,
      increaseInUnsecuredLoansPaise: unsecIncrease,
      decreaseInFixedAssetsPaise: faSale,
      totalSourcesPaise: totalSources,
      capitalExpenditurePaise: capex,
      repaymentOfTermLoanPaise: tlRepayment,
      decreaseInUnsecuredLoansPaise: unsecDecrease,
      promoterDrawingsPaise: curr.promoterDrawingsPaise,
      totalApplicationsPaise: totalApps,
      netSurplusOrDeficitPaise: netSurplus,
      openingNwcPaise: opNwc,
      closingNwcPaise: clNwc,
      changeInNwcPaise: clNwc - opNwc,
    });
  }
  return result;
}

export interface TermLoanAmortizationMonth {
  monthNumber: number;
  openingPrincipalPaise: number;
  emiPaise: number;
  interestPaise: number;
  principalPaise: number;
  closingPrincipalPaise: number;
  isMoratorium: boolean;
}

export interface TermLoanAnnualSummary {
  yearIndex: number;
  openingPrincipalPaise: number;
  totalEmiPaidPaise: number;
  totalInterestPaidPaise: number;
  totalPrincipalRepaidPaise: number;
  closingPrincipalPaise: number;
}

export interface TermLoanSchedule {
  loanAmountPaise: number;
  annualRatePercent: number;
  tenorMonths: number;
  moratoriumMonths: number;
  monthlyEmiPaise: number;
  totalInterestPayablePaise: number;
  totalPaymentPaise: number;
  monthlySchedule: TermLoanAmortizationMonth[];
  annualSummaries: TermLoanAnnualSummary[];
}

export function calculateTermLoanSchedule(
  principalPaise: number,
  annualRatePercent: number,
  tenorMonths: number,
  moratoriumMonths = 0
): TermLoanSchedule {
  if (principalPaise <= 0 || tenorMonths <= 0) {
    return {
      loanAmountPaise: 0,
      annualRatePercent,
      tenorMonths,
      moratoriumMonths,
      monthlyEmiPaise: 0,
      totalInterestPayablePaise: 0,
      totalPaymentPaise: 0,
      monthlySchedule: [],
      annualSummaries: [],
    };
  }

  const safeMoratorium = Math.min(Math.max(0, moratoriumMonths), Math.max(0, tenorMonths - 1));
  const r = annualRatePercent / 12 / 100;
  const effectiveTenor = Math.max(1, tenorMonths - safeMoratorium);

  const emiPaise =
    r > 0
      ? Math.round((principalPaise * r * Math.pow(1 + r, effectiveTenor)) / (Math.pow(1 + r, effectiveTenor) - 1))
      : Math.floor(principalPaise / effectiveTenor);

  const monthlyList: TermLoanAmortizationMonth[] = [];
  let currentBalance = principalPaise;
  let cumulativeInterest = 0;
  let cumulativePayment = 0;

  for (let m = 1; m <= tenorMonths; m++) {
    const isMora = m <= safeMoratorium;
    const interest = Math.round(currentBalance * r);
    let mEmi: number;
    let mPrincipal: number;
    if (isMora) {
      mEmi = interest;
      mPrincipal = 0;
    } else {
      const pPart = Math.max(0, Math.min(currentBalance, emiPaise - interest));
      mEmi = m === tenorMonths ? currentBalance + interest : interest + pPart;
      mPrincipal = mEmi - interest;
    }
    const closingBalance = Math.max(0, currentBalance - mPrincipal);
    monthlyList.push({
      monthNumber: m,
      openingPrincipalPaise: currentBalance,
      emiPaise: mEmi,
      interestPaise: interest,
      principalPaise: mPrincipal,
      closingPrincipalPaise: closingBalance,
      isMoratorium: isMora,
    });
    cumulativeInterest += interest;
    cumulativePayment += mEmi;
    currentBalance = closingBalance;
  }

  const annualSummaries: TermLoanAnnualSummary[] = [];
  for (let idx = 0, yr = 1; idx < monthlyList.length; idx += 12, yr++) {
    const chunk = monthlyList.slice(idx, idx + 12);
    annualSummaries.push({
      yearIndex: yr,
      openingPrincipalPaise: chunk[0].openingPrincipalPaise,
      totalEmiPaidPaise: chunk.reduce((s, c) => s + c.emiPaise, 0),
      totalInterestPaidPaise: chunk.reduce((s, c) => s + c.interestPaise, 0),
      totalPrincipalRepaidPaise: chunk.reduce((s, c) => s + c.principalPaise, 0),
      closingPrincipalPaise: chunk[chunk.length - 1].closingPrincipalPaise,
    });
  }

  return {
    loanAmountPaise: principalPaise,
    annualRatePercent,
    tenorMonths,
    moratoriumMonths: safeMoratorium,
    monthlyEmiPaise: emiPaise,
    totalInterestPayablePaise: cumulativeInterest,
    totalPaymentPaise: cumulativePayment,
    monthlySchedule: monthlyList,
    annualSummaries,
  };
}

export const AVAILABLE_BASE_YEARS: Array<{ year: number; label: string }> = [
  { year: 2023, label: 'FY 2023-24' },
  { year: 2024, label: 'FY 2024-25' },
  { year: 2025, label: 'FY 2025-26' },
  { year: 2026, label: 'FY 2026-27' },
];

export function projectNextYear(
  prev: CmaYearFinancials,
  fyLabel: string,
  stage: CmaYearStage,
  stageLabel: string,
  growthRate = 0.15
): CmaYearFinancials {
  const prevM = computeCmaYearMetrics(prev);
  const mult = 1 + growthRate;
  const projectedDebtors = Math.round(prev.sundryDebtorsPaise * (1 + growthRate * 0.9));
  const projectedStock = Math.round(prev.inventoryStockPaise * (1 + growthRate * 0.85));
  const projectedCreditors = Math.round(prev.sundryCreditorsPaise * (1 + growthRate * 0.9));
  const proposedCc = Math.max(
    prev.existingBankCcLimitPaise,
    Math.round((projectedStock + projectedDebtors - projectedCreditors) * 0.75)
  );
  const tlRepayment = prev.termLoanPrincipalInstallmentPaise;
  const tlOutstanding = Math.max(0, prev.termLoanOutstandingPaise - tlRepayment);
  const newDepr = Math.round(prev.depreciationPaise * 0.9);

  return {
    stage,
    fyLabel,
    stageLabel,
    grossSalesPaise: Math.round(prev.grossSalesPaise * mult),
    salesReturnsAndDeductionsPaise: Math.round(prev.salesReturnsAndDeductionsPaise * mult),
    nonOperatingIncomePaise: Math.round(prev.nonOperatingIncomePaise * 1.05),
    rawMaterialPurchasesPaise: Math.round(prev.rawMaterialPurchasesPaise * mult),
    directWagesPaise: Math.round(prev.directWagesPaise * (1 + growthRate * 0.8)),
    directPowerAndFreightPaise: Math.round(prev.directPowerAndFreightPaise * mult),
    otherDirectExpensesPaise: Math.round(prev.otherDirectExpensesPaise * mult),
    openingStockPaise: prev.closingStockPaise,
    closingStockPaise: projectedStock,
    salariesAndAdminPaise: Math.round(prev.salariesAndAdminPaise * (1 + growthRate * 0.75)),
    rentAndUtilitiesPaise: Math.round(prev.rentAndUtilitiesPaise * 1.05),
    sellingAndMarketingPaise: Math.round(prev.sellingAndMarketingPaise * (1 + growthRate * 0.85)),
    otherOverheadsPaise: Math.round(prev.otherOverheadsPaise * 1.05),
    depreciationPaise: newDepr,
    termLoanInterestPaise: Math.round(tlOutstanding * 0.095),
    workingCapitalInterestPaise: Math.round(proposedCc * 0.095),
    nonOperatingExpensesPaise: prev.nonOperatingExpensesPaise,
    taxRatePercent: prev.taxRatePercent,
    termLoanPrincipalInstallmentPaise: tlRepayment,
    cashAndBankPaise: Math.round(prev.cashAndBankPaise * 1.1),
    sundryDebtorsPaise: projectedDebtors,
    inventoryStockPaise: projectedStock,
    advancesToSuppliersPaise: Math.round(prev.advancesToSuppliersPaise * 1.08),
    otherCurrentAssetsPaise: Math.round(prev.otherCurrentAssetsPaise * 1.05),
    grossFixedAssetsPaise: prev.grossFixedAssetsPaise + 2_500_000,
    accumulatedDepreciationPaise: prev.accumulatedDepreciationPaise + newDepr,
    otherNonCurrentAssetsPaise: prev.otherNonCurrentAssetsPaise,
    sundryCreditorsPaise: projectedCreditors,
    otherCurrentLiabilitiesPaise: Math.round(prev.otherCurrentLiabilitiesPaise * 1.05),
    provisionsPaise: Math.round(prev.provisionsPaise * 1.05),
    existingBankCcLimitPaise: proposedCc,
    termLoanOutstandingPaise: tlOutstanding,
    unsecuredLoansPaise: prev.unsecuredLoansPaise,
    shareCapitalPaise: prev.shareCapitalPaise,
    reservesAndSurplusPaise: prev.reservesAndSurplusPaise + prevM.retainedProfitPaise,
    promoterDrawingsPaise: Math.round(prev.promoterDrawingsPaise * 1.1),
  };
}

export function createInitialDossier(
  baseYear: number,
  transactions: Transaction[] = [],
  parties: Party[] = [],
  products: ProductItem[] = [],
  cashAndBankPaise = 26_000_000
): CmaProjectDossier {
  const fy = (s: number) => `FY ${s}-${String((s + 1) % 100).padStart(2, '0')}`;
  let liveSales = 0;
  let liveReturns = 0;
  let livePurchases = 0;
  let liveDirect = 0;
  let liveIndirect = 0;

  for (const t of transactions) {
    if (t.isVoided) continue;
    const excl =
      t.items.length > 0 ? t.items.reduce((s, it) => s + it.taxableAmountPaise, 0) : t.amountPaise;
    if (t.type === 'Sale' || t.type === 'NonGstBill') liveSales += excl;
    else if (t.type === 'CreditNote') liveReturns += excl;
    else if (t.type === 'Purchase') livePurchases += excl;
    else if (t.type === 'DebitNote') livePurchases -= excl;
    else if (t.type === 'Expense') {
      if (t.expenseCategory === 'Direct') liveDirect += excl;
      else liveIndirect += excl;
    }
  }

  const seedSalesY2 = liveSales > 5_000_000 ? liveSales : 125_000_000;
  const seedReturnsY2 = liveReturns > 0 ? liveReturns : 2_500_000;
  const seedPurchasesY2 = Math.max(0, livePurchases) > 3_000_000 ? livePurchases : 88_000_000;
  const seedDirectY2 = liveDirect > 0 ? liveDirect : 3_500_000;
  const seedIndirectY2 = liveIndirect > 0 ? liveIndirect : 9_500_000;

  const liveDebtors = parties
    .filter((p) => p.openingBalanceType === 'Receivable')
    .reduce((s, p) => s + p.openingBalancePaise, 0);
  const seedDebtorsY2 = liveDebtors > 0 ? liveDebtors : 16_500_000;
  const liveCreditors = parties
    .filter((p) => p.openingBalanceType === 'Payable')
    .reduce((s, p) => s + p.openingBalancePaise, 0);
  const seedCreditorsY2 = liveCreditors > 0 ? liveCreditors : 12_000_000;
  const liveStock = products
    .filter((p) => !p.isArchived)
    .reduce((s, p) => s + Math.floor((p.currentStockMilli * p.purchasePricePaise + 500) / 1000), 0);
  const seedStockY2 = liveStock > 0 ? liveStock : 21_000_000;

  const y1: CmaYearFinancials = {
    stage: 'AuditedLastYear',
    fyLabel: fy(baseYear),
    stageLabel: 'Audited Last Year',
    grossSalesPaise: Math.round(seedSalesY2 * 0.85),
    salesReturnsAndDeductionsPaise: Math.round(seedReturnsY2 * 0.85),
    nonOperatingIncomePaise: 150_000,
    rawMaterialPurchasesPaise: Math.round(seedPurchasesY2 * 0.85),
    directWagesPaise: 2_000_000,
    directPowerAndFreightPaise: Math.round(seedDirectY2 * 0.85),
    otherDirectExpensesPaise: 500_000,
    openingStockPaise: Math.round(seedStockY2 * 0.8),
    closingStockPaise: Math.round(seedStockY2 * 0.88),
    salariesAndAdminPaise: Math.round(seedIndirectY2 * 0.5),
    rentAndUtilitiesPaise: Math.round(seedIndirectY2 * 0.25),
    sellingAndMarketingPaise: Math.round(seedIndirectY2 * 0.15),
    otherOverheadsPaise: Math.round(seedIndirectY2 * 0.1),
    depreciationPaise: 250_000,
    termLoanInterestPaise: 420_000,
    workingCapitalInterestPaise: 950_000,
    nonOperatingExpensesPaise: 80_000,
    taxRatePercent: 25.0,
    termLoanPrincipalInstallmentPaise: 600_000,
    cashAndBankPaise: 15_000_000,
    sundryDebtorsPaise: Math.round(seedDebtorsY2 * 0.85),
    inventoryStockPaise: Math.round(seedStockY2 * 0.88),
    advancesToSuppliersPaise: 1_000_000,
    otherCurrentAssetsPaise: 1_000_000,
    grossFixedAssetsPaise: 32_000_000,
    accumulatedDepreciationPaise: 4_500_000,
    otherNonCurrentAssetsPaise: 1_500_000,
    sundryCreditorsPaise: Math.round(seedCreditorsY2 * 0.85),
    otherCurrentLiabilitiesPaise: 2_000_000,
    provisionsPaise: 500_000,
    existingBankCcLimitPaise: 10_000_000,
    termLoanOutstandingPaise: 30_000_000,
    unsecuredLoansPaise: 5_000_000,
    shareCapitalPaise: 25_000_000,
    reservesAndSurplusPaise: 20_000_000,
    promoterDrawingsPaise: 1_200_000,
  };

  const y2: CmaYearFinancials = {
    stage: 'CurrentYearEstimate',
    fyLabel: fy(baseYear + 1),
    stageLabel: 'Current Year (Est.)',
    grossSalesPaise: seedSalesY2,
    salesReturnsAndDeductionsPaise: seedReturnsY2,
    nonOperatingIncomePaise: 200_000,
    rawMaterialPurchasesPaise: seedPurchasesY2,
    directWagesPaise: 2_400_000,
    directPowerAndFreightPaise: seedDirectY2,
    otherDirectExpensesPaise: 600_000,
    openingStockPaise: Math.round(seedStockY2 * 0.88),
    closingStockPaise: seedStockY2,
    salariesAndAdminPaise: Math.round(seedIndirectY2 * 0.52),
    rentAndUtilitiesPaise: Math.round(seedIndirectY2 * 0.24),
    sellingAndMarketingPaise: Math.round(seedIndirectY2 * 0.16),
    otherOverheadsPaise: Math.round(seedIndirectY2 * 0.08),
    depreciationPaise: 300_000,
    termLoanInterestPaise: 350_000,
    workingCapitalInterestPaise: 1_150_000,
    nonOperatingExpensesPaise: 50_000,
    taxRatePercent: 25.0,
    termLoanPrincipalInstallmentPaise: 600_000,
    cashAndBankPaise,
    sundryDebtorsPaise: seedDebtorsY2,
    inventoryStockPaise: seedStockY2,
    advancesToSuppliersPaise: 1_200_000,
    otherCurrentAssetsPaise: 1_300_000,
    grossFixedAssetsPaise: 38_000_000,
    accumulatedDepreciationPaise: 4_800_000,
    otherNonCurrentAssetsPaise: 1_800_000,
    sundryCreditorsPaise: seedCreditorsY2,
    otherCurrentLiabilitiesPaise: 2_500_000,
    provisionsPaise: 500_000,
    existingBankCcLimitPaise: 12_500_000,
    termLoanOutstandingPaise: 24_000_000,
    unsecuredLoansPaise: 6_000_000,
    shareCapitalPaise: 25_000_000,
    reservesAndSurplusPaise: 33_000_000,
    promoterDrawingsPaise: 1_500_000,
  };

  const y3 = projectNextYear(y2, fy(baseYear + 2), 'ProjectionYear1', 'Projection Year 1', 0.18);
  const y4 = projectNextYear(y3, fy(baseYear + 3), 'ProjectionYear2', 'Projection Year 2', 0.15);

  return { baseStartYear: baseYear, years: [y1, y2, y3, y4] };
}

export type FeedbackStatus = 'Healthy' | 'Acceptable' | 'Caution' | 'Critical';

export interface BankerFeedbackItem {
  title: string;
  benchmark: string;
  currentValue: string;
  status: FeedbackStatus;
  analysis: string;
  recommendation: string;
}

export function evaluateDossier(dossier: CmaProjectDossier): BankerFeedbackItem[] {
  const cyRaw = dossier.years[1] || dossier.years[0];
  const pyRaw = dossier.years[2] || cyRaw;
  if (!cyRaw) return [];
  const cy = computeCmaYearMetrics(cyRaw);
  const py = computeCmaYearMetrics(pyRaw);
  const toL = (p: number) => (p / 10_000_000).toFixed(2);

  return [
    {
      title: 'Current Ratio (Liquidity Buffer)',
      benchmark: '>= 1.33 (RBI Tandon Comm.)',
      currentValue: cy.currentRatio.toFixed(2),
      status: cy.currentRatio >= 1.33 ? 'Healthy' : cy.currentRatio >= 1.15 ? 'Acceptable' : 'Critical',
      analysis: 'Measures short-term debt repaying capability from current assets.',
      recommendation:
        cy.currentRatio >= 1.33
          ? 'Current Ratio satisfies RBI Tandon guidelines (>= 1.33). Maintain this liquidity buffer.'
          : 'Retain earnings or infuse promoter working capital margin to lift ratio above 1.33.',
    },
    {
      title: 'Quick / Acid-Test Ratio',
      benchmark: '>= 1.00',
      currentValue: cy.quickRatio.toFixed(2),
      status: cy.quickRatio >= 1.0 ? 'Healthy' : cy.quickRatio >= 0.8 ? 'Acceptable' : 'Caution',
      analysis: 'Immediate cash and debtors coverage excluding slow-moving inventory.',
      recommendation:
        cy.quickRatio >= 1.0
          ? 'Quick assets comfortably cover short-term liabilities.'
          : 'Accelerate debtor collections and optimize stock holding.',
    },
    {
      title: 'Debt Service Coverage Ratio (DSCR)',
      benchmark: '1.50 - 2.00x',
      currentValue: cy.dscr.toFixed(2),
      status: cy.dscr >= 1.75 ? 'Healthy' : cy.dscr >= 1.4 ? 'Acceptable' : 'Caution',
      analysis: 'Indicates capacity to pay loan principal installments and interest from cash profits.',
      recommendation:
        cy.dscr >= 1.75
          ? 'Strong debt servicing cushion covering principal and term interest.'
          : 'Request longer repayment tenor or step-up schedule to improve DSCR.',
    },
    {
      title: 'Interest Coverage Ratio (ICR)',
      benchmark: '>= 2.50x',
      currentValue: cy.interestCoverageRatio.toFixed(2),
      status: cy.interestCoverageRatio >= 3.0 ? 'Healthy' : cy.interestCoverageRatio >= 2.0 ? 'Acceptable' : 'Caution',
      analysis: 'Evaluates whether EBITDA easily absorbs total bank interest obligations.',
      recommendation:
        cy.interestCoverageRatio >= 3.0
          ? 'EBITDA comfortably handles all CC and term loan interest charges.'
          : 'Monitor finance costs and negotiate lower spread on CC limit.',
    },
    {
      title: 'TOL / Tangible Net Worth (Solvency)',
      benchmark: '<= 3.00 : 1',
      currentValue: `${cy.tolToTnwRatio.toFixed(2)} : 1`,
      status: cy.tolToTnwRatio <= 2.5 ? 'Healthy' : cy.tolToTnwRatio <= 3.5 ? 'Acceptable' : 'Caution',
      analysis: 'Total claims of all outside creditors relative to tangible net worth.',
      recommendation:
        cy.tolToTnwRatio <= 2.5
          ? 'Outside liabilities are well proportioned against owner equity.'
          : 'Limit additional short-term debt and retain PAT inside reserves.',
    },
    {
      title: 'Receivables & Stock Turnover Days',
      benchmark: 'Debtors <= 60d, Stock <= 60d',
      currentValue: `${cy.debtorDays}d Debtors · ${cy.inventoryDays}d Stock`,
      status: cy.debtorDays <= 45 && cy.inventoryDays <= 60 ? 'Healthy' : cy.debtorDays <= 75 ? 'Acceptable' : 'Caution',
      analysis: 'Length of time cash is tied up in customer bills and unsold inventory.',
      recommendation:
        cy.debtorDays <= 60
          ? 'Healthy cash conversion cycle supporting working capital turnover.'
          : 'Follow up on overdue invoices to shorten receivable cycle.',
    },
    {
      title: 'Eligible Working Capital Limit (MPBF)',
      benchmark: 'Tandon I & II vs Nayak Method',
      currentValue: `₹ ${toL(cy.mpbfTandonMethod1Paise)}L (M1) / ₹ ${toL(cy.mpbfTandonMethod2Paise)}L (M2) / ₹ ${toL(py.nayakCommitteeWorkingCapitalPaise)}L (Nayak)`,
      status: 'Healthy',
      analysis: 'Maximum Permissible Bank Finance across RBI Tandon Methods I & II and Nayak Turnover Norms.',
      recommendation: `Eligible for CC limit of ₹ ${toL(cy.mpbfTandonMethod2Paise)} L (Tandon II) or ₹ ${toL(py.nayakCommitteeWorkingCapitalPaise)} L (Nayak 20% Projected Turnover).`,
    },
  ];
}

export interface ProjectReportData {
  projectTitle: string;
  industrySector: string;
  promoterName: string;
  promoterBackground: string;
  locationDetails: string;
  proposedBankName: string;
  loanFacilityRequested: string;
  loanTenorMonths: number;
  moratoriumMonths: number;
  proposedInterestRatePercent: number;
  primarySecurity: string;
  collateralSecurity: string;
  landAndBuildingPaise: number;
  plantAndMachineryPaise: number;
  furnitureAndFixturesPaise: number;
  workingCapitalMarginPaise: number;
  preliminaryExpensesPaise: number;
  promoterContributionPaise: number;
  bankTermLoanPaise: number;
  bankWorkingCapitalCcPaise: number;
  govtSubsidyPaise: number;
  marketPotential: string;
  directEmployment: number;
  indirectEmployment: number;
  bankerRemarks: string;
}

export function createDefaultProjectReport(
  profile: BusinessProfile,
  dossier: CmaProjectDossier
): ProjectReportData {
  const cyRaw = dossier.years[1] || dossier.years[0];
  const cy = computeCmaYearMetrics(cyRaw);
  const wcMargin = Math.max(10_000_000, cy.tandonMethod2MinNwcPaise);
  const ccRequired = Math.max(12_500_000, cy.mpbfTandonMethod2Paise);
  const plant = 25_000_000;
  const furniture = 5_000_000;
  const prelim = 2_000_000;
  const totalCost = plant + furniture + prelim + wcMargin;
  const promoterMargin = Math.round(totalCost * 0.25);
  const bankTL = totalCost - promoterMargin;

  return {
    projectTitle: `${profile.businessName} - Business Expansion & Modernization Project`,
    industrySector: 'Trading, Distribution & Commercial Operations',
    promoterName: profile.ownerName || 'Proprietor / Managing Partner',
    promoterBackground: `Promoter has established market standing with GSTIN (${profile.gstin || 'Registered'}) and steady revenue trajectory.`,
    locationDetails: profile.address || `Commercial Trading Zone, State Code ${profile.homeStateCode}`,
    proposedBankName: 'State Bank of India / Commercial Bank',
    loanFacilityRequested: 'Term Loan (Setup) & Working Capital Cash Credit (CC)',
    loanTenorMonths: 60,
    moratoriumMonths: 6,
    proposedInterestRatePercent: 9.5,
    primarySecurity: 'First hypothecation charge on all current assets including inventory and receivables.',
    collateralSecurity: 'Mortgage of commercial property and personal guarantee of promoter.',
    landAndBuildingPaise: 0,
    plantAndMachineryPaise: plant,
    furnitureAndFixturesPaise: furniture,
    workingCapitalMarginPaise: wcMargin,
    preliminaryExpensesPaise: prelim,
    promoterContributionPaise: promoterMargin,
    bankTermLoanPaise: bankTL,
    bankWorkingCapitalCcPaise: ccRequired,
    govtSubsidyPaise: 0,
    marketPotential:
      'Established trading relationship with repeat retail & wholesale buyers. Projected growth of 18% p.a. supported by enhanced working capital limit.',
    directEmployment: 8,
    indirectEmployment: 15,
    bankerRemarks: `Viable commercial feasibility with DSCR ${cy.dscr.toFixed(2)}x and Current Ratio ${cy.currentRatio.toFixed(2)}.`,
  };
}
