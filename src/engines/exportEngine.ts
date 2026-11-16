import {
  BUSINESS_ENTITY_META,
  BankAccount,
  BusinessProfile,
  Party,
  Transaction,
} from '../types/models';
import {
  BankerFeedbackItem,
  CmaProjectDossier,
  ProjectReportData,
  TermLoanSchedule,
  computeCmaYearMetrics,
  computeFundsFlowSchedule,
} from './cmaProjectEngine';
import {
  FyFinancialStatement,
  Gstr9AnnualSummary,
  INDIAN_STATES,
  amountInWords,
  formatPaise,
  formatQuantityMilli,
} from './financialEngine';
import { Gstr2bReconciliationItem, Gstr2bReconciliationSummary } from './gstr2bEngine';

export function escapeHtml(str: string): string {
  return str
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;');
}

export function downloadFile(
  filename: string,
  content: string,
  mimeType = 'application/octet-stream'
): void {
  const blob = new Blob([content], { type: mimeType });
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement('a');
  anchor.href = url;
  anchor.download = filename;
  anchor.style.display = 'none';
  document.body.appendChild(anchor);
  anchor.click();
  document.body.removeChild(anchor);
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}

export function buildUpiPayUri(
  upiId: string,
  businessName: string,
  amountPaise: number,
  note = 'Invoice Payment'
): string {
  const pa = encodeURIComponent(upiId.trim());
  const pn = encodeURIComponent(businessName.trim() || 'Merchant');
  const tn = encodeURIComponent(note);
  const am = (Math.max(0, amountPaise) / 100).toFixed(2);
  return `upi://pay?pa=${pa}&pn=${pn}&am=${am}&cu=INR&tn=${tn}`;
}

export function buildWhatsAppReminderText(
  party: Party,
  amountPaise: number,
  bankAccount: BankAccount | null,
  profile: BusinessProfile,
  invoiceMeta?: { invoiceNumber?: string; date?: string }
): string {
  const amt = formatPaise(Math.abs(amountPaise));
  const lines: string[] = [
    `Namaste *${party.name}*,`,
    '',
    invoiceMeta?.invoiceNumber
      ? `Greetings from *${profile.businessName}*. Here are the details for Invoice *#${invoiceMeta.invoiceNumber}*${invoiceMeta.date ? ` dated ${invoiceMeta.date}` : ''}:`
      : `Gentle payment reminder from *${profile.businessName}* regarding your pending account balance:`,
    `Amount Due: *${amt}*`,
  ];
  if (bankAccount) {
    lines.push('', '*Payment Details:*');
    if (bankAccount.upiId) {
      lines.push(`UPI ID: ${bankAccount.upiId}`);
      lines.push(`Pay Link: ${buildUpiPayUri(bankAccount.upiId, profile.businessName, Math.abs(amountPaise), invoiceMeta?.invoiceNumber || 'Khata Settlement')}`);
    }
    if (bankAccount.accountNumber) {
      lines.push(`Bank: ${bankAccount.bankName} (${bankAccount.accountName})`);
      lines.push(`A/c No: ${bankAccount.accountNumber} · IFSC: ${bankAccount.ifscCode}`);
    }
  }
  lines.push('', 'Thank you for your business!', `— ${profile.businessName}`);
  return lines.join('\n');
}

export function buildWhatsAppUrl(phone: string, text: string): string {
  const digits = phone.replace(/\D/g, '');
  const withCountry = digits.length === 10 ? `91${digits}` : digits;
  const encoded = encodeURIComponent(text);
  return withCountry ? `https://wa.me/${withCountry}?text=${encoded}` : `https://wa.me/?text=${encoded}`;
}

function getStateName(code: string): string {
  const st = INDIAN_STATES.find((s) => s.code === code);
  return st ? `${st.code} - ${st.name}` : code || '27 - Maharashtra';
}

const PRINT_BASE_CSS = `
  * { box-sizing: border-box; }
  body { font-family: 'Segoe UI', Arial, sans-serif; color: #0f172a; margin: 0; padding: 24px; background: #fff; font-size: 12px; line-height: 1.45; }
  .sheet { max-width: 820px; margin: 0 auto; border: 1px solid #cbd5e1; padding: 24px; }
  .header { display: flex; justify-content: space-between; align-items: flex-start; border-bottom: 2px solid #0f172a; padding-bottom: 14px; margin-bottom: 16px; }
  .title { font-size: 20px; font-weight: 700; letter-spacing: -0.01em; color: #0f172a; }
  .sub { color: #475569; font-size: 11px; margin-top: 2px; }
  .badge { display: inline-block; padding: 3px 8px; font-size: 10px; font-weight: 700; text-transform: uppercase; border: 1px solid #0f172a; letter-spacing: 0.04em; }
  .grid-2 { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; margin-bottom: 16px; }
  .box { border: 1px solid #cbd5e1; padding: 10px 12px; background: #f8fafc; }
  .box-title { font-size: 10px; font-weight: 700; text-transform: uppercase; color: #475569; margin-bottom: 4px; letter-spacing: 0.04em; }
  table { width: 100%; border-collapse: collapse; margin-top: 10px; margin-bottom: 14px; }
  th { background: #f1f5f9; color: #0f172a; font-size: 10px; text-transform: uppercase; letter-spacing: 0.03em; text-align: left; padding: 7px 8px; border: 1px solid #cbd5e1; }
  td { padding: 7px 8px; border: 1px solid #e2e8f0; vertical-align: top; }
  .num { text-align: right; font-variant-numeric: tabular-nums; font-family: 'Consolas', monospace; }
  .total-row td { font-weight: 700; background: #f8fafc; border-top: 2px solid #0f172a; }
  .footer { display: flex; justify-content: space-between; align-items: flex-end; margin-top: 24px; padding-top: 12px; border-top: 1px solid #cbd5e1; }
  .void-stamp { color: #dc2626; border: 2px solid #dc2626; padding: 4px 10px; font-weight: 800; font-size: 13px; text-transform: uppercase; display: inline-block; margin-top: 6px; }
  @media print { body { padding: 0; } .sheet { border: none; padding: 10px; max-width: none; } }
`;

export function buildInvoicePrintHtml(
  txn: Transaction,
  profile: BusinessProfile,
  party: Party | null,
  bankAccount: BankAccount | null,
  qrDataUrl?: string | null
): string {
  const isReg = profile.registrationType === 'regular';
  const docTitle =
    txn.type === 'Quotation'
      ? 'QUOTATION / ESTIMATE'
      : txn.type === 'CreditNote'
        ? 'CREDIT NOTE'
        : txn.type === 'DebitNote'
          ? 'DEBIT NOTE'
          : txn.type === 'Purchase'
            ? 'PURCHASE VOUCHER'
            : txn.type === 'NonGstBill'
              ? 'BILL / CASH MEMO'
              : profile.registrationType === 'composition'
                ? 'BILL OF SUPPLY (COMPOSITION)'
                : isReg
                  ? 'TAX INVOICE'
                  : 'BILL OF SUPPLY';

  const taxableSum =
    txn.items.length > 0 ? txn.items.reduce((s, it) => s + it.taxableAmountPaise, 0) : txn.amountPaise;
  const cgstSum = txn.items.reduce((s, it) => s + it.cgstPaise, 0);
  const sgstSum = txn.items.reduce((s, it) => s + it.sgstPaise, 0);
  const igstSum = txn.items.reduce((s, it) => s + it.igstPaise, 0);
  const preRoundTotal = taxableSum + cgstSum + sgstSum + igstSum;
  const roundOff = txn.items.length > 0 ? txn.amountPaise - preRoundTotal : 0;
  const isInterstate = igstSum > 0 || (party?.stateCode && party.stateCode !== profile.homeStateCode);
  const signatoryTitle = BUSINESS_ENTITY_META[profile.entityType]?.signatoryTitle || 'Authorised Signatory';

  const itemRows =
    txn.items.length > 0
      ? txn.items
          .map(
            (it, idx) => `
        <tr>
          <td>${idx + 1}</td>
          <td><strong>${escapeHtml(it.name)}</strong></td>
          <td>${escapeHtml(it.hsnCode || '—')}</td>
          <td class="num">${formatQuantityMilli(it.quantityMilli)} ${escapeHtml(it.unit)}</td>
          <td class="num">${formatPaise(it.ratePaise)}</td>
          <td class="num">${formatPaise(it.taxableAmountPaise)}</td>
          <td class="num">${(it.gstRateBps / 100).toFixed(it.gstRateBps % 100 === 0 ? 0 : 2)}%</td>
          ${
            isInterstate
              ? `<td class="num">${formatPaise(it.igstPaise)}</td>`
              : `<td class="num">${formatPaise(it.cgstPaise)}</td><td class="num">${formatPaise(it.sgstPaise)}</td>`
          }
          <td class="num"><strong>${formatPaise(it.totalPaise)}</strong></td>
        </tr>`
          )
          .join('')
      : `<tr>
          <td>1</td>
          <td><strong>${escapeHtml(txn.remarks || txn.type)}</strong></td>
          <td>—</td>
          <td class="num">1</td>
          <td class="num">${formatPaise(txn.amountPaise)}</td>
          <td class="num">${formatPaise(txn.amountPaise)}</td>
          <td class="num">0%</td>
          ${isInterstate ? `<td class="num">₹ 0.00</td>` : `<td class="num">₹ 0.00</td><td class="num">₹ 0.00</td>`}
          <td class="num"><strong>${formatPaise(txn.amountPaise)}</strong></td>
        </tr>`;

  return `<!DOCTYPE html>
<html lang="en"><head><meta charset="utf-8"><title>${escapeHtml(docTitle)} ${escapeHtml(txn.invoiceNumber || txn.id)}</title>
<style>${PRINT_BASE_CSS}</style></head><body>
<div class="sheet">
  <div class="header">
    <div>
      <div class="title">${escapeHtml(profile.businessName)}</div>
      <div class="sub">${escapeHtml(profile.address || 'Registered Business Premises')}</div>
      <div class="sub">State: ${escapeHtml(getStateName(profile.homeStateCode))} ${profile.gstin ? `· GSTIN: <strong>${escapeHtml(profile.gstin)}</strong>` : ''}</div>
      ${profile.phone || profile.email ? `<div class="sub">${escapeHtml([profile.phone, profile.email].filter(Boolean).join(' · '))}</div>` : ''}
    </div>
    <div style="text-align:right">
      <div class="badge">${escapeHtml(docTitle)}</div>
      <div style="margin-top:6px;font-weight:700;font-size:14px">${escapeHtml(txn.invoiceNumber || txn.id)}</div>
      <div class="sub">Date: ${escapeHtml(txn.date)} · Mode: ${escapeHtml(txn.paymentMode)}</div>
      ${txn.isVoided ? `<div class="void-stamp">VOIDED</div>` : ''}
    </div>
  </div>
  <div class="grid-2">
    <div class="box">
      <div class="box-title">Bill To / Party Details</div>
      <div><strong>${escapeHtml(party?.name || 'Walk-in / Cash Customer')}</strong></div>
      ${party?.address ? `<div class="sub">${escapeHtml(party.address)}</div>` : ''}
      ${party?.gstin ? `<div class="sub">GSTIN: <strong>${escapeHtml(party.gstin)}</strong></div>` : ''}
      ${party?.phone ? `<div class="sub">Phone: ${escapeHtml(party.phone)}</div>` : ''}
    </div>
    <div class="box">
      <div class="box-title">Place of Supply & Compliance</div>
      <div>Supply Type: <strong>${isInterstate ? 'Inter-State (IGST)' : 'Intra-State (CGST + SGST)'}</strong></div>
      <div class="sub">Place of Supply: ${escapeHtml(getStateName(party?.stateCode || profile.homeStateCode))}</div>
      <div class="sub">Entity: ${escapeHtml(BUSINESS_ENTITY_META[profile.entityType]?.displayName || profile.entityType)}</div>
    </div>
  </div>
  <table>
    <thead>
      <tr>
        <th>#</th><th>Item & Description</th><th>HSN/SAC</th><th class="num">Qty</th>
        <th class="num">Rate</th><th class="num">Taxable</th><th class="num">GST</th>
        ${isInterstate ? '<th class="num">IGST</th>' : '<th class="num">CGST</th><th class="num">SGST</th>'}
        <th class="num">Total</th>
      </tr>
    </thead>
    <tbody>
      ${itemRows}
      <tr class="total-row">
        <td colspan="5">Sub-Total</td>
        <td class="num">${formatPaise(taxableSum)}</td>
        <td></td>
        ${isInterstate ? `<td class="num">${formatPaise(igstSum)}</td>` : `<td class="num">${formatPaise(cgstSum)}</td><td class="num">${formatPaise(sgstSum)}</td>`}
        <td class="num">${formatPaise(preRoundTotal)}</td>
      </tr>
    </tbody>
  </table>
  <div class="grid-2">
    <div class="box">
      <div class="box-title">Amount in Words</div>
      <div><strong>${escapeHtml(amountInWords(txn.amountPaise))}</strong></div>
      ${txn.remarks ? `<div class="sub" style="margin-top:6px">Remarks: ${escapeHtml(txn.remarks)}</div>` : ''}
    </div>
    <div class="box">
      <div class="box-title">Tax & Total Summary</div>
      <div style="display:flex;justify-content:space-between"><span>Taxable Value</span><span class="num">${formatPaise(taxableSum)}</span></div>
      ${cgstSum > 0 ? `<div style="display:flex;justify-content:space-between"><span>CGST</span><span class="num">${formatPaise(cgstSum)}</span></div>` : ''}
      ${sgstSum > 0 ? `<div style="display:flex;justify-content:space-between"><span>SGST</span><span class="num">${formatPaise(sgstSum)}</span></div>` : ''}
      ${igstSum > 0 ? `<div style="display:flex;justify-content:space-between"><span>IGST</span><span class="num">${formatPaise(igstSum)}</span></div>` : ''}
      ${roundOff !== 0 ? `<div style="display:flex;justify-content:space-between"><span>Round-Off (Rule 26)</span><span class="num">${roundOff > 0 ? '+' : ''}${formatPaise(roundOff)}</span></div>` : ''}
      <div style="display:flex;justify-content:space-between;border-top:1px solid #0f172a;margin-top:6px;padding-top:6px;font-weight:700;font-size:14px">
        <span>Grand Total</span><span class="num">${formatPaise(txn.amountPaise)}</span>
      </div>
    </div>
  </div>
  <div class="footer">
    <div style="display:flex;gap:12px;align-items:center">
      ${qrDataUrl ? `<img src="${qrDataUrl}" alt="UPI QR" style="width:78px;height:78px;border:1px solid #cbd5e1;padding:3px" />` : ''}
      <div>
        <div class="box-title">Bank & UPI Payment Info</div>
        ${bankAccount ? `<div class="sub"><strong>${escapeHtml(bankAccount.bankName)}</strong> · A/c: ${escapeHtml(bankAccount.accountNumber)} · IFSC: ${escapeHtml(bankAccount.ifscCode)}</div>` : '<div class="sub">Cash / Cheque / Online Transfer accepted.</div>'}
        ${bankAccount?.upiId ? `<div class="sub">UPI ID: <strong>${escapeHtml(bankAccount.upiId)}</strong></div>` : ''}
      </div>
    </div>
    <div style="text-align:right">
      <div class="sub">For <strong>${escapeHtml(profile.businessName)}</strong></div>
      <div style="height:36px"></div>
      <div style="border-top:1px solid #475569;padding-top:4px;font-weight:600">${escapeHtml(signatoryTitle)}</div>
    </div>
  </div>
</div></body></html>`;
}

export function buildFinancialStatementPrintHtml(
  stmt: FyFinancialStatement,
  profile: BusinessProfile
): string {
  const row = (label: string, val: number, bold = false) =>
    `<tr ${bold ? 'class="total-row"' : ''}><td>${escapeHtml(label)}</td><td class="num">${formatPaise(val)}</td></tr>`;
  return `<!DOCTYPE html>
<html lang="en"><head><meta charset="utf-8"><title>Financial Statements - ${escapeHtml(stmt.fyLabel)}</title>
<style>${PRINT_BASE_CSS}</style></head><body>
<div class="sheet">
  <div class="header">
    <div>
      <div class="title">${escapeHtml(profile.businessName)}</div>
      <div class="sub">GSTIN: ${escapeHtml(profile.gstin || 'Unregistered')} · State: ${escapeHtml(getStateName(profile.homeStateCode))}</div>
    </div>
    <div style="text-align:right">
      <div class="badge">P&amp;L &amp; Balance Sheet</div>
      <div style="font-weight:700;margin-top:4px">${escapeHtml(stmt.fyLabel)}</div>
    </div>
  </div>
  <h3 style="margin:8px 0 4px">Statement of Profit &amp; Loss (5-Step Accounting)</h3>
  <table>
    <thead><tr><th>Particulars</th><th class="num">Amount (INR)</th></tr></thead>
    <tbody>
      ${row('Gross Sales / Revenue (Excl. GST)', stmt.grossSalesPaise)}
      ${row('Less: Sales Returns (Credit Notes)', stmt.salesReturnsPaise)}
      ${row('Net Sales Turnover', stmt.netSalesPaise, true)}
      ${row('Purchases (Net of Debit Notes)', stmt.purchasesCogsPaise)}
      ${row('Direct Expenses (Freight, Labour, Power)', stmt.directExpensesPaise)}
      ${row(`Gross Profit (${stmt.grossProfitMarginPercent.toFixed(1)}%)`, stmt.grossProfitPaise, true)}
      ${row('Salaries & Staff Payroll', stmt.operatingExpensesSalariesPaise)}
      ${row('Admin, Rent & Office Overheads', stmt.operatingExpensesAdminPaise)}
      ${row('Marketing & Distribution', stmt.operatingExpensesMarketingPaise)}
      ${row('Depreciation', stmt.operatingExpensesDepreciationPaise)}
      ${row(`Operating Profit / EBIT (${stmt.operatingProfitMarginPercent.toFixed(1)}%)`, stmt.operatingProfitPaise, true)}
      ${row('Add: Non-Operating Income', stmt.nonOperatingIncomePaise)}
      ${row('Less: Finance & Interest Costs', stmt.nonOperatingExpensesPaise)}
      ${row(`Net Profit (${stmt.netProfitMarginPercent.toFixed(1)}%)`, stmt.netProfitPaise, true)}
    </tbody>
  </table>
  <h3 style="margin:14px 0 4px">Balance Sheet Position</h3>
  <table>
    <thead><tr><th>Assets &amp; Liabilities</th><th class="num">Balance (INR)</th></tr></thead>
    <tbody>
      ${row('Cash in Hand', stmt.cashInHandPaise)}
      ${row('Bank Balances', stmt.bankTotalPaise)}
      ${row('Trade Receivables (Debtors)', stmt.receivablesPaise)}
      ${row('Closing Stock Valuation (FIFO / Cost)', stmt.stockValuationPaise)}
      ${row('GST Input Credit Asset', stmt.gstInputCreditAssetPaise)}
      ${row('Total Assets', stmt.totalAssetsPaise, true)}
      ${row('Trade Payables (Creditors)', stmt.payablesPaise)}
      ${row('GST Output Liability', stmt.gstPayableLiabilityPaise)}
      ${row('Closing Capital & Reserves', stmt.closingCapitalPaise)}
      ${row('Total Liabilities & Equity', stmt.totalLiabilitiesAndEquityPaise, true)}
    </tbody>
  </table>
</div></body></html>`;
}

export function buildGstr9PrintHtml(
  summary: Gstr9AnnualSummary,
  profile: BusinessProfile
): string {
  return `<!DOCTYPE html>
<html lang="en"><head><meta charset="utf-8"><title>GSTR-9 Annual Summary - ${escapeHtml(summary.fyLabel)}</title>
<style>${PRINT_BASE_CSS}</style></head><body>
<div class="sheet">
  <div class="header">
    <div>
      <div class="title">${escapeHtml(profile.businessName)}</div>
      <div class="sub">GSTIN: ${escapeHtml(profile.gstin || 'N/A')}</div>
    </div>
    <div style="text-align:right">
      <div class="badge">Form GSTR-9 Summary</div>
      <div style="font-weight:700;margin-top:4px">${escapeHtml(summary.fyLabel)}</div>
    </div>
  </div>
  <table>
    <thead><tr><th>Quarter</th><th class="num">Invoices</th><th class="num">Taxable Sales</th><th class="num">Output Tax</th><th class="num">Eligible ITC</th><th class="num">Net Payable</th></tr></thead>
    <tbody>
      ${summary.quarters
        .map(
          (q) => `<tr>
        <td><strong>${escapeHtml(q.quarterLabel)}</strong><div class="sub">${escapeHtml(q.dateRangeDesc)}</div></td>
        <td class="num">${q.invoiceCount}</td>
        <td class="num">${formatPaise(q.taxableSalesPaise)}</td>
        <td class="num">${formatPaise(q.totalOutputTaxPaise)}</td>
        <td class="num">${formatPaise(q.totalItcPaise)}</td>
        <td class="num">${formatPaise(q.netTaxPayablePaise)}</td>
      </tr>`
        )
        .join('')}
      <tr class="total-row">
        <td>Annual Total</td>
        <td class="num">${summary.totalInvoicesCount}</td>
        <td class="num">${formatPaise(summary.annualTaxableSalesPaise)}</td>
        <td class="num">${formatPaise(summary.annualTotalOutputTaxPaise)}</td>
        <td class="num">${formatPaise(summary.annualTotalItcPaise)}</td>
        <td class="num">${formatPaise(summary.annualNetTaxPayablePaise)}</td>
      </tr>
    </tbody>
  </table>
</div></body></html>`;
}

export function buildGstr2bReconPrintHtml(
  items: Gstr2bReconciliationItem[],
  summary: Gstr2bReconciliationSummary,
  profile: BusinessProfile
): string {
  return `<!DOCTYPE html>
<html lang="en"><head><meta charset="utf-8"><title>GSTR-2B ITC Reconciliation Report</title>
<style>${PRINT_BASE_CSS}</style></head><body>
<div class="sheet">
  <div class="header">
    <div>
      <div class="title">${escapeHtml(profile.businessName)}</div>
      <div class="sub">GSTIN: ${escapeHtml(profile.gstin || 'N/A')}</div>
    </div>
    <div style="text-align:right">
      <div class="badge">GSTR-2B vs Books Audit</div>
      <div class="sub">Rule 36(4) &amp; Sec 17(5) Verification</div>
    </div>
  </div>
  <div class="grid-2">
    <div class="box"><div class="box-title">Portal 2B Eligible ITC</div><strong>${formatPaise(summary.totalGstr2bItcAvailablePaise)}</strong> (${summary.totalGstr2bCount} docs)</div>
    <div class="box"><div class="box-title">Matched ITC / At-Risk u/r 36(4)</div><strong>${formatPaise(summary.matchedItcPaise)}</strong> matched · <span style="color:#dc2626">${formatPaise(summary.missingIn2bAtRiskItcPaise)} at risk</span></div>
  </div>
  <table>
    <thead><tr><th>Status</th><th>Supplier / GSTIN</th><th>Invoice</th><th class="num">2B Tax</th><th class="num">Books Tax</th><th class="num">Diff</th></tr></thead>
    <tbody>
      ${items
        .map(
          (it) => `<tr>
        <td><strong>${escapeHtml(it.matchStatus)}</strong></td>
        <td>${escapeHtml(it.supplierName)}<div class="sub">${escapeHtml(it.supplierGstin)}</div></td>
        <td>${escapeHtml(it.invoiceNumber)}<div class="sub">${escapeHtml(it.invoiceDate)}</div></td>
        <td class="num">${formatPaise(it.gstr2bTotalTaxPaise)}</td>
        <td class="num">${formatPaise(it.booksTotalTaxPaise)}</td>
        <td class="num">${formatPaise(it.diffTaxPaise)}</td>
      </tr>`
        )
        .join('')}
    </tbody>
  </table>
</div></body></html>`;
}

export function buildCmaProjectReportHtml(
  profile: BusinessProfile,
  dossier: CmaProjectDossier,
  feedback: BankerFeedbackItem[],
  project: ProjectReportData,
  loanSchedule: TermLoanSchedule
): string {
  const computedYears = dossier.years.map((y) => ({ y, m: computeCmaYearMetrics(y) }));
  const fundsFlow = computeFundsFlowSchedule(dossier);
  const yearHeaders = computedYears.map((x) => `<th class="num">${escapeHtml(x.y.fyLabel)}<div class="sub">${escapeHtml(x.y.stageLabel)}</div></th>`).join('');
  const metricRow = (label: string, fn: (x: (typeof computedYears)[0]) => string, bold = false) =>
    `<tr ${bold ? 'class="total-row"' : ''}><td>${escapeHtml(label)}</td>${computedYears.map((x) => `<td class="num">${fn(x)}</td>`).join('')}</tr>`;

  return `<!DOCTYPE html>
<html lang="en"><head><meta charset="utf-8"><title>CMA Project Dossier - ${escapeHtml(profile.businessName)}</title>
<style>${PRINT_BASE_CSS}</style></head><body>
<div class="sheet">
  <div class="header">
    <div>
      <div class="title">${escapeHtml(project.projectTitle)}</div>
      <div class="sub">${escapeHtml(profile.businessName)} · GSTIN: ${escapeHtml(profile.gstin || 'N/A')} · Proposed Bank: ${escapeHtml(project.proposedBankName)}</div>
    </div>
    <div style="text-align:right">
      <div class="badge">RBI CMA Forms I - VII</div>
      <div class="sub">MPBF &amp; Term Loan Viability Dossier</div>
    </div>
  </div>
  <div class="grid-2">
    <div class="box">
      <div class="box-title">Form I · Facility &amp; Project Outlay</div>
      <div>Facility: <strong>${escapeHtml(project.loanFacilityRequested)}</strong></div>
      <div class="sub">Term Loan: ${formatPaise(project.bankTermLoanPaise)} · CC Limit: ${formatPaise(project.bankWorkingCapitalCcPaise)}</div>
      <div class="sub">Promoter Margin: ${formatPaise(project.promoterContributionPaise)} · Rate: ${project.proposedInterestRatePercent}% · ${project.loanTenorMonths}m</div>
    </div>
    <div class="box">
      <div class="box-title">Term Loan EMI Summary (Form VII)</div>
      <div>Monthly EMI (post moratorium): <strong>${formatPaise(loanSchedule.monthlyEmiPaise)}</strong></div>
      <div class="sub">Total Interest: ${formatPaise(loanSchedule.totalInterestPayablePaise)} · Total Repayment: ${formatPaise(loanSchedule.totalPaymentPaise)}</div>
      <div class="sub">Security: ${escapeHtml(project.primarySecurity)}</div>
    </div>
  </div>
  <h3>Form II · Operating Statement (P&amp;L Projections)</h3>
  <table>
    <thead><tr><th>Particulars</th>${yearHeaders}</tr></thead>
    <tbody>
      ${metricRow('Net Sales', (x) => formatPaise(x.m.netSalesPaise), true)}
      ${metricRow('Cost of Goods Sold', (x) => formatPaise(x.m.costOfSalesPaise))}
      ${metricRow('Gross Profit', (x) => formatPaise(x.m.grossProfitPaise))}
      ${metricRow('EBITDA', (x) => formatPaise(x.m.ebitdaPaise))}
      ${metricRow('Finance Charges', (x) => formatPaise(x.m.totalFinanceChargesPaise))}
      ${metricRow('Profit After Tax (PAT)', (x) => formatPaise(x.m.patPaise), true)}
    </tbody>
  </table>
  <h3>Form III · Balance Sheet &amp; Form IV/V · Tandon MPBF vs Nayak Norms</h3>
  <table>
    <thead><tr><th>Metrics</th>${yearHeaders}</tr></thead>
    <tbody>
      ${metricRow('Total Current Assets', (x) => formatPaise(x.m.totalCurrentAssetsPaise))}
      ${metricRow('Other Current Liabilities (excl. Bank)', (x) => formatPaise(x.m.currentLiabilitiesOtherThanBankPaise))}
      ${metricRow('Net Working Capital (NWC)', (x) => formatPaise(x.m.netWorkingCapitalPaise))}
      ${metricRow('Tangible Net Worth (TNW)', (x) => formatPaise(x.m.tangibleNetWorthPaise))}
      ${metricRow('MPBF (Tandon Method II)', (x) => formatPaise(x.m.mpbfTandonMethod2Paise), true)}
      ${metricRow('Nayak Committee WC (20% Sales)', (x) => formatPaise(x.m.nayakCommitteeWorkingCapitalPaise))}
      ${metricRow('Current Ratio', (x) => x.m.currentRatio.toFixed(2))}
      ${metricRow('DSCR', (x) => `${x.m.dscr.toFixed(2)}x`)}
      ${metricRow('TOL / TNW', (x) => `${x.m.tolToTnwRatio.toFixed(2)}:1`)}
    </tbody>
  </table>
  <h3>Form VI · Funds Flow Schedule</h3>
  <table>
    <thead><tr><th>FY</th><th class="num">Cash Accruals</th><th class="num">Total Sources</th><th class="num">Total Applications</th><th class="num">Change in NWC</th></tr></thead>
    <tbody>
      ${fundsFlow
        .map(
          (f) => `<tr><td>${escapeHtml(f.fyLabel)}</td><td class="num">${formatPaise(f.cashAccrualsPaise)}</td><td class="num">${formatPaise(f.totalSourcesPaise)}</td><td class="num">${formatPaise(f.totalApplicationsPaise)}</td><td class="num">${formatPaise(f.changeInNwcPaise)}</td></tr>`
        )
        .join('')}
    </tbody>
  </table>
  <h3>Banker Credit Appraisal Remarks</h3>
  <table>
    <thead><tr><th>Parameter</th><th>Benchmark</th><th>Current</th><th>Status</th><th>Recommendation</th></tr></thead>
    <tbody>
      ${feedback
        .map(
          (fb) => `<tr><td><strong>${escapeHtml(fb.title)}</strong></td><td>${escapeHtml(fb.benchmark)}</td><td class="num">${escapeHtml(fb.currentValue)}</td><td><strong>${escapeHtml(fb.status)}</strong></td><td>${escapeHtml(fb.recommendation)}</td></tr>`
        )
        .join('')}
    </tbody>
  </table>
</div></body></html>`;
}
