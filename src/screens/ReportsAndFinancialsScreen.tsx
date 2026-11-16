import React, { useMemo, useState } from 'react';
import { BarChart3, CheckCircle2, Printer, ReceiptIndianRupee, Scale } from 'lucide-react';
import {
  buildFinancialStatementPrintHtml,
  buildGstr9PrintHtml,
} from '../engines/exportEngine';
import {
  calculateFyFinancials,
  calculateFyGstSummary,
  calculateGstr9AnnualSummary,
  formatPaise,
} from '../engines/financialEngine';
import { usePabaru } from '../state/PabaruContext';

export const ReportsAndFinancialsScreen: React.FC = () => {
  const { state, openPrintPreview } = usePabaru();
  const [tab, setTab] = useState<'pnl' | 'bs' | 'gstr9'>('pnl');

  const stmt = useMemo(
    () => calculateFyFinancials(state, state.selectedFy),
    [state]
  );
  const gstSummary = useMemo(
    () => calculateFyGstSummary(state.transactions, state.parties, state.selectedFy),
    [state.transactions, state.parties, state.selectedFy]
  );
  const gstr9 = useMemo(
    () => calculateGstr9AnnualSummary(state.transactions, state.parties, state.selectedFy),
    [state.transactions, state.parties, state.selectedFy]
  );

  const bsDiff = stmt.totalAssetsPaise - stmt.totalLiabilitiesAndEquityPaise;

  const printPnlBs = () => {
    openPrintPreview({
      title: `Financial Statements · ${stmt.fyLabel}`,
      html: buildFinancialStatementPrintHtml(stmt, state.profile),
      filename: `Financials_${stmt.fyLabel.replace(/\s+/g, '_')}.html`,
    });
  };

  const printGstr9 = () => {
    openPrintPreview({
      title: `Form GSTR-9 Annual Summary · ${gstr9.fyLabel}`,
      html: buildGstr9PrintHtml(gstr9, state.profile),
      filename: `GSTR9_${gstr9.fyLabel.replace(/\s+/g, '_')}.html`,
    });
  };

  return (
    <div className="space-y-5">
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
        <div>
          <h2 className="text-lg md:text-xl font-bold">
            Financial Statements &amp; GSTR-9 ({state.selectedFy})
          </h2>
          <p className="text-xs text-[var(--p-text-muted)]">
            5-Step Profit &amp; Loss, Live Balance Sheet &amp; Annual GST Consolidation
          </p>
        </div>

        <button
          type="button"
          onClick={tab === 'gstr9' ? printGstr9 : printPnlBs}
          className="inline-flex items-center gap-1.5 whitespace-nowrap shrink-0 rounded-xl bg-[var(--p-primary)] px-4 py-2 text-xs font-semibold text-[var(--p-primary-text)] hover:opacity-90"
        >
          <Printer className="w-4 h-4" />
          Print A4 Report
        </button>
      </div>

      {/* Tabs */}
      <div className="flex flex-wrap gap-1.5">
        {[
          { id: 'pnl' as const, label: 'Profit & Loss (5-Step)', icon: <BarChart3 className="w-3.5 h-3.5" /> },
          { id: 'bs' as const, label: 'Balance Sheet', icon: <Scale className="w-3.5 h-3.5" /> },
          { id: 'gstr9' as const, label: 'GST & GSTR-9 Annual', icon: <ReceiptIndianRupee className="w-3.5 h-3.5" /> },
        ].map((t) => (
          <button
            key={t.id}
            type="button"
            onClick={() => setTab(t.id)}
            className={`inline-flex items-center gap-1.5 whitespace-nowrap rounded-xl border px-3 py-2 text-xs font-semibold ${
              tab === t.id
                ? 'border-[var(--p-primary)] bg-[var(--p-primary)] text-[var(--p-primary-text)]'
                : 'border-[var(--p-card-border)] bg-[var(--p-card)] hover:bg-[var(--p-secondary-bg)]'
            }`}
          >
            {t.icon}
            <span>{t.label}</span>
          </button>
        ))}
      </div>

      {tab === 'pnl' && (
        <div className="rounded-2xl border border-[var(--p-card-border)] bg-[var(--p-card)] overflow-hidden">
          <table className="w-full text-left text-xs">
            <thead className="bg-[var(--p-secondary-bg)] text-[var(--p-text-muted)] uppercase text-[10px]">
              <tr>
                <th className="px-4 py-2.5">Income Statement Line Item</th>
                <th className="px-4 py-2.5 text-right">Amount ({stmt.fyLabel})</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-[var(--p-card-border)]">
              <PnlRow label="1. Gross Sales / Revenue (Excl. GST)" value={stmt.grossSalesPaise} />
              <PnlRow label="Less: Sales Returns (Credit Notes)" value={-stmt.salesReturnsPaise} />
              <PnlRow label="Net Sales Turnover" value={stmt.netSalesPaise} highlight />
              <PnlRow label="2. Cost of Goods Purchased (Net of Debit Notes)" value={stmt.purchasesCogsPaise} />
              <PnlRow label="Direct Expenses (Freight, Power, Wages)" value={stmt.directExpensesPaise} />
              <PnlRow
                label={`Gross Profit (${stmt.grossProfitMarginPercent.toFixed(1)}% GP Margin)`}
                value={stmt.grossProfitPaise}
                highlight
              />
              <PnlRow label="3. Salaries & Staff Payroll" value={stmt.operatingExpensesSalariesPaise} />
              <PnlRow label="Admin, Rent & Office Overheads" value={stmt.operatingExpensesAdminPaise} />
              <PnlRow label="Marketing & Distribution" value={stmt.operatingExpensesMarketingPaise} />
              <PnlRow label="Depreciation" value={stmt.operatingExpensesDepreciationPaise} />
              <PnlRow
                label={`Operating Profit / EBIT (${stmt.operatingProfitMarginPercent.toFixed(1)}%)`}
                value={stmt.operatingProfitPaise}
                highlight
              />
              <PnlRow label="4. Non-Operating Income" value={stmt.nonOperatingIncomePaise} />
              <PnlRow label="Finance & Loan Interest Costs" value={-stmt.nonOperatingExpensesPaise} />
              <PnlRow
                label={`5. Net Profit (${stmt.netProfitMarginPercent.toFixed(1)}% NP Margin)`}
                value={stmt.netProfitPaise}
                highlight
                accent
              />
            </tbody>
          </table>
        </div>
      )}

      {tab === 'bs' && (
        <div className="space-y-4">
          <div className="rounded-xl border border-[var(--p-card-border)] bg-[var(--p-card)] px-4 py-3 flex items-center justify-between text-xs">
            <span className="flex items-center gap-2 font-semibold">
              <CheckCircle2 className="w-4 h-4 text-[var(--p-success)]" />
              Accounting Equation Status (Assets = Liabilities + Capital)
            </span>
            <span className="font-mono-num font-bold">
              Variance: {formatPaise(bsDiff)}
            </span>
          </div>

          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            <div className="rounded-2xl border border-[var(--p-card-border)] bg-[var(--p-card)] overflow-hidden">
              <div className="px-4 py-3 border-b border-[var(--p-card-border)] bg-[var(--p-secondary-bg)] font-bold text-xs uppercase">
                Assets
              </div>
              <table className="w-full text-xs">
                <tbody className="divide-y divide-[var(--p-card-border)]">
                  <PnlRow label="Cash in Hand" value={stmt.cashInHandPaise} />
                  <PnlRow label="Bank Balances" value={stmt.bankTotalPaise} />
                  <PnlRow label="Trade Receivables (Debtors)" value={stmt.receivablesPaise} />
                  <PnlRow label="Closing Stock Valuation" value={stmt.stockValuationPaise} />
                  <PnlRow label="GST Input Credit Asset" value={stmt.gstInputCreditAssetPaise} />
                  <PnlRow label="Total Assets" value={stmt.totalAssetsPaise} highlight accent />
                </tbody>
              </table>
            </div>

            <div className="rounded-2xl border border-[var(--p-card-border)] bg-[var(--p-card)] overflow-hidden">
              <div className="px-4 py-3 border-b border-[var(--p-card-border)] bg-[var(--p-secondary-bg)] font-bold text-xs uppercase">
                Liabilities &amp; Owner Equity
              </div>
              <table className="w-full text-xs">
                <tbody className="divide-y divide-[var(--p-card-border)]">
                  <PnlRow label="Trade Payables (Creditors)" value={stmt.payablesPaise} />
                  <PnlRow label="GST Output Liability" value={stmt.gstPayableLiabilityPaise} />
                  <PnlRow label="Opening Capital" value={stmt.openingCapitalPaise} />
                  <PnlRow label="Drawings (FY)" value={-stmt.drawingsPaise} />
                  <PnlRow label="Closing Capital & Reserves" value={stmt.closingCapitalPaise} />
                  <PnlRow
                    label="Total Liabilities & Equity"
                    value={stmt.totalLiabilitiesAndEquityPaise}
                    highlight
                    accent
                  />
                </tbody>
              </table>
            </div>
          </div>
        </div>
      )}

      {tab === 'gstr9' && (
        <div className="space-y-4">
          <div className="grid grid-cols-1 sm:grid-cols-3 gap-3.5">
            <div className="rounded-2xl border border-[var(--p-card-border)] bg-[var(--p-card)] p-4">
              <p className="text-xs text-[var(--p-text-muted)]">Annual Taxable Turnover</p>
              <p className="text-lg font-bold font-mono-num mt-1">
                {formatPaise(gstSummary.totalTaxableSalesPaise)}
              </p>
              <p className="text-[11px] text-[var(--p-text-muted)] mt-1">
                B2B: {gstSummary.b2bInvoiceCount} · B2C: {gstSummary.b2cInvoiceCount}
              </p>
            </div>
            <div className="rounded-2xl border border-[var(--p-card-border)] bg-[var(--p-card)] p-4">
              <p className="text-xs text-[var(--p-text-muted)]">Output GST vs Eligible ITC</p>
              <p className="text-lg font-bold font-mono-num mt-1">
                {formatPaise(gstSummary.totalOutputTaxPaise)}
              </p>
              <p className="text-[11px] text-[var(--p-success)] mt-1">
                ITC Claimed: {formatPaise(gstSummary.totalEligibleItcPaise)}
              </p>
            </div>
            <div className="rounded-2xl border border-[var(--p-card-border)] bg-[var(--p-card)] p-4">
              <p className="text-xs text-[var(--p-text-muted)]">Net Annual GST Liability</p>
              <p className="text-lg font-bold font-mono-num text-[var(--p-primary)] mt-1">
                {formatPaise(gstSummary.netGstPayablePaise)}
              </p>
            </div>
          </div>

          <div className="rounded-2xl border border-[var(--p-card-border)] bg-[var(--p-card)] overflow-hidden">
            <div className="px-4 py-3 border-b border-[var(--p-card-border)]">
              <h3 className="text-sm font-bold">GSTR-9 Quarterly Consolidation ({gstr9.fyLabel})</h3>
            </div>
            <div className="overflow-x-auto">
              <table className="w-full text-left text-xs">
                <thead className="bg-[var(--p-secondary-bg)] text-[var(--p-text-muted)] uppercase text-[10px]">
                  <tr>
                    <th className="px-4 py-2.5">Quarter</th>
                    <th className="px-4 py-2.5 text-right">Invoices</th>
                    <th className="px-4 py-2.5 text-right">Taxable Sales</th>
                    <th className="px-4 py-2.5 text-right">Output Tax</th>
                    <th className="px-4 py-2.5 text-right">Input ITC</th>
                    <th className="px-4 py-2.5 text-right">Net Payable</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-[var(--p-card-border)]">
                  {gstr9.quarters.map((q) => (
                    <tr key={q.quarterKey}>
                      <td className="px-4 py-2.5 font-semibold">{q.quarterLabel}</td>
                      <td className="px-4 py-2.5 text-right font-mono-num">{q.invoiceCount}</td>
                      <td className="px-4 py-2.5 text-right font-mono-num">
                        {formatPaise(q.taxableSalesPaise)}
                      </td>
                      <td className="px-4 py-2.5 text-right font-mono-num">
                        {formatPaise(q.totalOutputTaxPaise)}
                      </td>
                      <td className="px-4 py-2.5 text-right font-mono-num text-[var(--p-success)]">
                        {formatPaise(q.totalItcPaise)}
                      </td>
                      <td className="px-4 py-2.5 text-right font-mono-num font-bold">
                        {formatPaise(q.netTaxPayablePaise)}
                      </td>
                    </tr>
                  ))}
                  <tr className="bg-[var(--p-secondary-bg)] font-bold">
                    <td className="px-4 py-2.5">Annual Total</td>
                    <td className="px-4 py-2.5 text-right font-mono-num">
                      {gstr9.totalInvoicesCount}
                    </td>
                    <td className="px-4 py-2.5 text-right font-mono-num">
                      {formatPaise(gstr9.annualTaxableSalesPaise)}
                    </td>
                    <td className="px-4 py-2.5 text-right font-mono-num">
                      {formatPaise(gstr9.annualTotalOutputTaxPaise)}
                    </td>
                    <td className="px-4 py-2.5 text-right font-mono-num text-[var(--p-success)]">
                      {formatPaise(gstr9.annualTotalItcPaise)}
                    </td>
                    <td className="px-4 py-2.5 text-right font-mono-num text-[var(--p-primary)]">
                      {formatPaise(gstr9.annualNetTaxPayablePaise)}
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

const PnlRow: React.FC<{
  label: string;
  value: number;
  highlight?: boolean;
  accent?: boolean;
}> = ({ label, value, highlight = false, accent = false }) => (
  <tr className={highlight ? 'bg-[var(--p-secondary-bg)] font-bold' : ''}>
    <td className="px-4 py-2.5">{label}</td>
    <td
      className={`px-4 py-2.5 text-right font-mono-num whitespace-nowrap ${
        accent ? 'text-[var(--p-primary)] text-sm' : ''
      }`}
    >
      {formatPaise(value)}
    </td>
  </tr>
);
