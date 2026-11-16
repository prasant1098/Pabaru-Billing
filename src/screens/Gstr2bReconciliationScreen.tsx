import React, { useMemo, useState } from 'react';
import { FileSpreadsheet, Printer, Sparkles, Upload } from 'lucide-react';
import { buildGstr2bReconPrintHtml } from '../engines/exportEngine';
import { formatPaise } from '../engines/financialEngine';
import {
  Gstr2bInvoice,
  Gstr2bMatchStatus,
  getSampleGstr2bInvoices,
  parseGstr2bFileText,
  reconcileGstr2b,
} from '../engines/gstr2bEngine';
import { usePabaru } from '../state/PabaruContext';

export const Gstr2bReconciliationScreen: React.FC = () => {
  const { state, openPrintPreview, addToast } = usePabaru();
  const [gstr2bDocs, setGstr2bDocs] = useState<Gstr2bInvoice[]>(() =>
    getSampleGstr2bInvoices(state.transactions, state.parties)
  );
  const [statusFilter, setStatusFilter] = useState<'ALL' | Gstr2bMatchStatus>('ALL');

  const recon = useMemo(
    () =>
      reconcileGstr2b(
        gstr2bDocs,
        state.transactions,
        state.parties,
        state.profile.homeStateCode
      ),
    [gstr2bDocs, state.transactions, state.parties, state.profile.homeStateCode]
  );

  const visibleItems = useMemo(
    () =>
      statusFilter === 'ALL'
        ? recon.items
        : recon.items.filter((i) => i.matchStatus === statusFilter),
    [recon.items, statusFilter]
  );

  const handleUpload = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;
    const text = await file.text();
    const parsed = parseGstr2bFileText(text);
    if (parsed.length === 0) {
      addToast('No GSTR-2B B2B invoices found in file', 'error');
    } else {
      setGstr2bDocs(parsed);
      addToast(`Loaded ${parsed.length} GSTR-2B portal invoices`, 'success');
    }
    e.target.value = '';
  };

  return (
    <div className="space-y-5">
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
        <div>
          <h2 className="text-lg md:text-xl font-bold">
            GSTR-2B Input Tax Credit (ITC) Reconciliation
          </h2>
          <p className="text-xs text-[var(--p-text-muted)]">
            Matches GST Portal Form 2B against local Purchase Book (Rule 36(4) &amp; Sec 17(5))
          </p>
        </div>

        <div className="flex flex-wrap items-center gap-2">
          <label className="inline-flex items-center gap-1.5 cursor-pointer rounded-xl bg-[var(--p-primary)] px-3.5 py-2 text-xs font-semibold text-[var(--p-primary-text)]">
            <Upload className="w-3.5 h-3.5" />
            <span>Upload 2B JSON / CSV</span>
            <input
              type="file"
              accept=".json,.csv,.tsv,.txt"
              onChange={handleUpload}
              className="hidden"
            />
          </label>
          <button
            type="button"
            onClick={() => {
              setGstr2bDocs(getSampleGstr2bInvoices(state.transactions, state.parties));
              addToast('Reloaded sample GSTR-2B portal dataset', 'info');
            }}
            className="inline-flex items-center gap-1 rounded-xl border border-[var(--p-card-border)] bg-[var(--p-card)] px-3 py-2 text-xs font-medium"
          >
            <Sparkles className="w-3.5 h-3.5" />
            Load Sample 2B
          </button>
          <button
            type="button"
            onClick={() =>
              openPrintPreview({
                title: 'GSTR-2B Reconciliation Report',
                html: buildGstr2bReconPrintHtml(recon.items, recon.summary, state.profile),
                filename: 'GSTR2B_Reconciliation.html',
              })
            }
            className="inline-flex items-center gap-1 rounded-xl border border-[var(--p-card-border)] bg-[var(--p-card)] px-3 py-2 text-xs font-medium"
          >
            <Printer className="w-3.5 h-3.5" />
            Print Audit Report
          </button>
        </div>
      </div>

      {/* Summary Cards */}
      <div className="grid grid-cols-2 md:grid-cols-5 gap-3">
        <div className="rounded-xl border border-[var(--p-card-border)] bg-[var(--p-card)] p-3">
          <p className="text-[11px] text-[var(--p-text-muted)]">Matched ITC</p>
          <p className="text-base font-bold font-mono-num text-[var(--p-success)] mt-1">
            {formatPaise(recon.summary.matchedItcPaise)}
          </p>
          <p className="text-[10px] text-[var(--p-text-muted)]">
            {recon.summary.matchedCount} invoices
          </p>
        </div>
        <div className="rounded-xl border border-[var(--p-card-border)] bg-[var(--p-card)] p-3">
          <p className="text-[11px] text-[var(--p-text-muted)]">Value Mismatch</p>
          <p className="text-base font-bold font-mono-num text-[var(--p-warning)] mt-1">
            {formatPaise(recon.summary.valueMismatchTaxVariancePaise)}
          </p>
          <p className="text-[10px] text-[var(--p-text-muted)]">
            {recon.summary.valueMismatchCount} invoices
          </p>
        </div>
        <div className="rounded-xl border border-[var(--p-card-border)] bg-[var(--p-card)] p-3">
          <p className="text-[11px] text-[var(--p-text-muted)]">Missing in Books</p>
          <p className="text-base font-bold font-mono-num text-[var(--p-info)] mt-1">
            {formatPaise(recon.summary.missingInBooksUnclaimedItcPaise)}
          </p>
          <p className="text-[10px] text-[var(--p-text-muted)]">
            {recon.summary.missingInBooksCount} unclaimed
          </p>
        </div>
        <div className="rounded-xl border border-[var(--p-card-border)] bg-[var(--p-card)] p-3">
          <p className="text-[11px] text-[var(--p-text-muted)]">Missing in 2B (Risk)</p>
          <p className="text-base font-bold font-mono-num text-[var(--p-danger)] mt-1">
            {formatPaise(recon.summary.missingIn2bAtRiskItcPaise)}
          </p>
          <p className="text-[10px] text-[var(--p-text-muted)]">
            {recon.summary.missingIn2bCount} Rule 36(4) risk
          </p>
        </div>
        <div className="rounded-xl border border-[var(--p-card-border)] bg-[var(--p-card)] p-3">
          <p className="text-[11px] text-[var(--p-text-muted)]">Blocked Sec 17(5)</p>
          <p className="text-base font-bold font-mono-num text-[var(--p-text-muted)] mt-1">
            {formatPaise(recon.summary.ineligibleItcPaise)}
          </p>
          <p className="text-[10px] text-[var(--p-text-muted)]">
            {recon.summary.ineligibleItcCount} ineligible
          </p>
        </div>
      </div>

      {/* Filter Pills */}
      <div className="flex flex-wrap gap-1.5">
        {(
          [
            ['ALL', 'All Items'],
            ['FullyMatched', 'Matched'],
            ['ValueMismatch', 'Mismatch'],
            ['MissingInBooks', 'Missing in Books'],
            ['MissingIn2B', 'Missing in 2B'],
            ['IneligibleITC', 'Blocked Sec 17(5)'],
          ] as Array<['ALL' | Gstr2bMatchStatus, string]>
        ).map(([k, label]) => (
          <button
            key={k}
            type="button"
            onClick={() => setStatusFilter(k)}
            className={`whitespace-nowrap rounded-xl border px-3 py-1.5 text-xs font-semibold ${
              statusFilter === k
                ? 'border-[var(--p-primary)] bg-[var(--p-primary)] text-[var(--p-primary-text)]'
                : 'border-[var(--p-card-border)] bg-[var(--p-card)]'
            }`}
          >
            {label}
          </button>
        ))}
      </div>

      {/* Recon Table */}
      <div className="rounded-2xl border border-[var(--p-card-border)] bg-[var(--p-card)] overflow-hidden">
        <div className="px-4 py-3 border-b border-[var(--p-card-border)] flex items-center gap-2">
          <FileSpreadsheet className="w-4 h-4 text-[var(--p-primary)]" />
          <h3 className="text-sm font-bold">Invoice-Level Verification ({visibleItems.length})</h3>
        </div>
        <div className="overflow-x-auto">
          <table className="w-full text-left text-xs">
            <thead className="bg-[var(--p-secondary-bg)] text-[var(--p-text-muted)] uppercase text-[10px]">
              <tr>
                <th className="px-4 py-2.5">Status</th>
                <th className="px-4 py-2.5">Supplier &amp; GSTIN</th>
                <th className="px-4 py-2.5">Invoice</th>
                <th className="px-4 py-2.5 text-right">2B Tax</th>
                <th className="px-4 py-2.5 text-right">Books Tax</th>
                <th className="px-4 py-2.5 text-right">Variance</th>
                <th className="px-4 py-2.5">Compliance Remarks</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-[var(--p-card-border)]">
              {visibleItems.map((it) => (
                <tr key={it.id} className="hover:bg-[var(--p-bg)]">
                  <td className="px-4 py-2.5 whitespace-nowrap">
                    <span
                      className={`rounded-md px-2 py-0.5 text-[10px] font-bold ${
                        it.matchStatus === 'FullyMatched'
                          ? 'bg-[var(--p-success-container)] text-[var(--p-on-success-container)]'
                          : it.matchStatus === 'MissingIn2B'
                            ? 'bg-[var(--p-danger-container)] text-[var(--p-on-danger-container)]'
                            : 'bg-[var(--p-warning-container)] text-[var(--p-on-warning-container)]'
                      }`}
                    >
                      {it.matchStatus}
                    </span>
                  </td>
                  <td className="px-4 py-2.5">
                    <p className="font-semibold">{it.supplierName}</p>
                    <p className="text-[11px] font-mono-num text-[var(--p-text-muted)]">
                      {it.supplierGstin}
                    </p>
                  </td>
                  <td className="px-4 py-2.5 font-mono-num">
                    {it.invoiceNumber}
                    <div className="text-[10px] text-[var(--p-text-muted)]">{it.invoiceDate}</div>
                  </td>
                  <td className="px-4 py-2.5 text-right font-mono-num">
                    {formatPaise(it.gstr2bTotalTaxPaise)}
                  </td>
                  <td className="px-4 py-2.5 text-right font-mono-num">
                    {formatPaise(it.booksTotalTaxPaise)}
                  </td>
                  <td className="px-4 py-2.5 text-right font-mono-num font-bold">
                    {formatPaise(it.diffTaxPaise)}
                  </td>
                  <td className="px-4 py-2.5 text-[11px] text-[var(--p-text-muted)]">
                    {it.itcBlockedReason || it.remarks}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
};
