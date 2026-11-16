import React, { useMemo, useState } from 'react';
import {
  AlertTriangle,
  ArrowDownLeft,
  ArrowUpRight,
  FilePlus2,
  Landmark,
  Package,
  Plus,
  Printer,
  SlidersHorizontal,
  TrendingUp,
  Users,
  Wallet,
  X,
} from 'lucide-react';
import { generateQrDataUrl } from '../components/QrCodeSvg';
import { VoidTransactionModal } from '../components/VoidTransactionModal';
import { buildInvoicePrintHtml, buildUpiPayUri } from '../engines/exportEngine';
import {
  calculateFyFinancials,
  formatPaise,
  formatQuantityMilli,
  getTodayDate,
  parsePaiseFromRupees,
} from '../engines/financialEngine';
import { usePabaru } from '../state/PabaruContext';
import { PaymentMode, Transaction, TransactionType } from '../types/models';

export const DashboardScreen: React.FC = () => {
  const {
    state,
    setActiveScreen,
    getLiveCashBalancePaise,
    getTotalBankBalancePaise,
    openPrintPreview,
    updateLowStockThreshold,
    recordQuickTransaction,
  } = usePabaru();

  const [voidTarget, setVoidTarget] = useState<Transaction | null>(null);
  const [thresholdModalOpen, setThresholdModalOpen] = useState(false);
  const [thresholdInput, setThresholdInput] = useState(String(state.lowStockThreshold));
  const [quickVoucherOpen, setQuickVoucherOpen] = useState<'Expense' | 'Drawing' | null>(null);
  const [qAmount, setQAmount] = useState('');
  const [qMode, setQMode] = useState<PaymentMode>('Cash');
  const [qRemarks, setQRemarks] = useState('');
  const [qCategory, setQCategory] = useState<'Direct' | 'Indirect'>('Indirect');

  const fyStmt = useMemo(
    () => calculateFyFinancials(state, state.selectedFy),
    [state]
  );

  const today = getTodayDate();
  const todayStats = useMemo(() => {
    let cashIn = 0;
    let cashOut = 0;
    let invoiceCount = 0;
    for (const t of state.transactions) {
      if (t.isVoided || t.date !== today) continue;
      if (t.type === 'Sale' || t.type === 'NonGstBill' || t.type === 'PaymentIn') {
        cashIn += t.amountPaise;
        if (t.type === 'Sale' || t.type === 'NonGstBill') invoiceCount++;
      } else if (
        t.type === 'Purchase' ||
        t.type === 'PaymentOut' ||
        t.type === 'Expense' ||
        t.type === 'Drawing'
      ) {
        cashOut += t.amountPaise;
      }
    }
    return { cashIn, cashOut, invoiceCount };
  }, [state.transactions, today]);

  const cashBalance = getLiveCashBalancePaise();
  const bankBalance = getTotalBankBalancePaise();

  const lowStockItems = useMemo(
    () =>
      state.products.filter(
        (p) => !p.isArchived && p.currentStockMilli <= state.lowStockThreshold * 1000
      ),
    [state.products, state.lowStockThreshold]
  );

  const recentTxns = useMemo(() => state.transactions.slice(0, 8), [state.transactions]);

  const handlePrintTxn = async (txn: Transaction) => {
    const party = state.parties.find((p) => p.id === txn.partyId) || null;
    const defaultQr =
      state.qrCodes.find((q) => q.isDefault && q.isEnabled && q.showOnInvoices) || null;
    const bank =
      state.bankAccounts.find((b) => b.id === (txn.bankAccountId || defaultQr?.linkedBankId)) ||
      state.bankAccounts[0] ||
      null;
    const upiId = defaultQr?.upiId || bank?.upiId;
    const qrDataUrl = upiId
      ? await generateQrDataUrl(
          buildUpiPayUri(upiId, state.profile.businessName, txn.amountPaise, txn.invoiceNumber || txn.id)
        )
      : null;

    openPrintPreview({
      title: `${txn.type} · ${txn.invoiceNumber || txn.id}`,
      html: buildInvoicePrintHtml(txn, state.profile, party, bank, qrDataUrl),
      filename: `${(txn.invoiceNumber || txn.id).replace(/[^a-zA-Z0-9-_]/g, '_')}.html`,
    });
  };

  const submitQuickVoucher = (e: React.FormEvent) => {
    e.preventDefault();
    if (!quickVoucherOpen) return;
    const paise = parsePaiseFromRupees(qAmount);
    if (paise <= 0) return;
    recordQuickTransaction({
      type: quickVoucherOpen as TransactionType,
      amountPaise: paise,
      date: today,
      partyId: null,
      paymentMode: qMode,
      bankAccountId: qMode === 'Cash' ? null : state.bankAccounts[0]?.id || null,
      expenseCategory: qCategory,
      remarks: qRemarks || `${quickVoucherOpen} voucher`,
    });
    setQAmount('');
    setQRemarks('');
    setQuickVoucherOpen(null);
  };

  return (
    <div className="space-y-5">
      {/* Header Actions */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
        <div>
          <h2 className="text-lg md:text-xl font-bold text-[var(--p-text)]">
            Executive Overview
          </h2>
          <p className="text-xs text-[var(--p-text-muted)]">
            Live position for {state.selectedFy} · Today {today}
          </p>
        </div>

        <div className="flex items-center gap-2 flex-wrap">
          <button
            type="button"
            onClick={() => setActiveScreen('invoices')}
            className="inline-flex items-center gap-1.5 whitespace-nowrap shrink-0 rounded-xl bg-[var(--p-primary)] px-3.5 py-2 text-xs font-semibold text-[var(--p-primary-text)] hover:opacity-90"
          >
            <FilePlus2 className="w-4 h-4" />
            New GST Invoice
          </button>
          <button
            type="button"
            onClick={() => setQuickVoucherOpen('Expense')}
            className="inline-flex items-center gap-1.5 whitespace-nowrap shrink-0 rounded-xl border border-[var(--p-card-border)] bg-[var(--p-card)] px-3 py-2 text-xs font-medium text-[var(--p-text)] hover:bg-[var(--p-secondary-bg)]"
          >
            <Plus className="w-3.5 h-3.5" />
            Record Expense
          </button>
          <button
            type="button"
            onClick={() => setActiveScreen('ledgers')}
            className="inline-flex items-center gap-1.5 whitespace-nowrap shrink-0 rounded-xl border border-[var(--p-card-border)] bg-[var(--p-card)] px-3 py-2 text-xs font-medium text-[var(--p-text)] hover:bg-[var(--p-secondary-bg)]"
          >
            <Users className="w-3.5 h-3.5" />
            Party Khata
          </button>
        </div>
      </div>

      {/* Primary KPI Grid */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-3.5">
        <div className="rounded-2xl border border-[var(--p-card-border)] bg-[var(--p-card)] p-4">
          <div className="flex items-center justify-between text-xs text-[var(--p-text-muted)]">
            <span>Today Inflow / Sales</span>
            <ArrowDownLeft className="w-4 h-4 text-[var(--p-success)]" />
          </div>
          <p className="text-xl font-bold font-mono-num text-[var(--p-success)] mt-1.5">
            {formatPaise(todayStats.cashIn)}
          </p>
          <p className="text-[11px] text-[var(--p-text-muted)] mt-1">
            {todayStats.invoiceCount} bills today · Outflow {formatPaise(todayStats.cashOut)}
          </p>
        </div>

        <button
          type="button"
          onClick={() => setActiveScreen('ledgers')}
          className="text-left rounded-2xl border border-[var(--p-card-border)] bg-[var(--p-card)] p-4 hover:bg-[var(--p-secondary-bg)] transition"
        >
          <div className="flex items-center justify-between text-xs text-[var(--p-text-muted)]">
            <span>You&apos;ll Get (Receivables)</span>
            <ArrowUpRight className="w-4 h-4 text-[var(--p-success)]" />
          </div>
          <p className="text-xl font-bold font-mono-num text-[var(--p-text)] mt-1.5">
            {formatPaise(fyStmt.receivablesPaise)}
          </p>
          <p className="text-[11px] text-[var(--p-text-muted)] mt-1">
            Payables: <span className="font-mono-num">{formatPaise(fyStmt.payablesPaise)}</span>
          </p>
        </button>

        <button
          type="button"
          onClick={() => setActiveScreen('cash_bank')}
          className="text-left rounded-2xl border border-[var(--p-card-border)] bg-[var(--p-card)] p-4 hover:bg-[var(--p-secondary-bg)] transition"
        >
          <div className="flex items-center justify-between text-xs text-[var(--p-text-muted)]">
            <span>Cash &amp; Bank Liquidity</span>
            <Landmark className="w-4 h-4 text-[var(--p-primary)]" />
          </div>
          <p className="text-xl font-bold font-mono-num text-[var(--p-text)] mt-1.5">
            {formatPaise(cashBalance + bankBalance)}
          </p>
          <p className="text-[11px] text-[var(--p-text-muted)] mt-1">
            Cash <span className="font-mono-num">{formatPaise(cashBalance, true)}</span> · Bank{' '}
            <span className="font-mono-num">{formatPaise(bankBalance, true)}</span>
          </p>
        </button>

        <button
          type="button"
          onClick={() => setActiveScreen('reports')}
          className="text-left rounded-2xl border border-[var(--p-card-border)] bg-[var(--p-card)] p-4 hover:bg-[var(--p-secondary-bg)] transition"
        >
          <div className="flex items-center justify-between text-xs text-[var(--p-text-muted)]">
            <span>{state.selectedFy} Net Profit</span>
            <TrendingUp className="w-4 h-4 text-[var(--p-primary)]" />
          </div>
          <p
            className={`text-xl font-bold font-mono-num mt-1.5 ${
              fyStmt.netProfitPaise >= 0 ? 'text-[var(--p-success)]' : 'text-[var(--p-danger)]'
            }`}
          >
            {formatPaise(fyStmt.netProfitPaise)}
          </p>
          <p className="text-[11px] text-[var(--p-text-muted)] mt-1">
            Sales {formatPaise(fyStmt.netSalesPaise, true)} · Stock{' '}
            {formatPaise(fyStmt.stockValuationPaise, true)}
          </p>
        </button>
      </div>

      {/* Main Content Split */}
      <div className="grid grid-cols-1 lg:grid-cols-12 gap-5">
        {/* Recent Ledger Vouchers */}
        <div className="lg:col-span-8 rounded-2xl border border-[var(--p-card-border)] bg-[var(--p-card)] overflow-hidden">
          <div className="flex items-center justify-between px-4 py-3 border-b border-[var(--p-card-border)]">
            <div>
              <h3 className="text-sm font-bold text-[var(--p-text)]">Recent Vouchers</h3>
              <p className="text-[11px] text-[var(--p-text-muted)]">
                Latest billing, purchases, and cash/bank receipts
              </p>
            </div>
            <button
              type="button"
              onClick={() => setActiveScreen('daybook')}
              className="text-xs font-semibold text-[var(--p-link)] hover:underline whitespace-nowrap"
            >
              Open Daybook
            </button>
          </div>

          <div className="divide-y divide-[var(--p-card-border)]">
            {recentTxns.map((t) => {
              const party = state.parties.find((p) => p.id === t.partyId);
              const isInflow =
                t.type === 'Sale' ||
                t.type === 'NonGstBill' ||
                t.type === 'PaymentIn' ||
                t.type === 'DebitNote';
              return (
                <div
                  key={t.id}
                  className={`flex items-center justify-between gap-3 px-4 py-3 text-xs ${
                    t.isVoided ? 'opacity-55' : ''
                  }`}
                >
                  <div className="min-w-0">
                    <div className="flex items-center gap-2">
                      <span className="font-semibold text-[var(--p-text)] truncate">
                        {party?.name || t.remarks || t.type}
                      </span>
                      <span className="rounded-md bg-[var(--p-secondary-bg)] px-1.5 py-0.5 text-[10px] font-semibold text-[var(--p-text-muted)] shrink-0">
                        {t.type}
                      </span>
                      {t.isVoided && (
                        <span className="rounded-md bg-[var(--p-danger-container)] px-1.5 py-0.5 text-[10px] font-bold text-[var(--p-on-danger-container)] shrink-0">
                          VOIDED
                        </span>
                      )}
                    </div>
                    <p className="text-[11px] text-[var(--p-text-muted)] mt-0.5 truncate">
                      {t.invoiceNumber ? `${t.invoiceNumber} · ` : ''}
                      {t.date} · {t.paymentMode}
                    </p>
                  </div>

                  <div className="flex items-center gap-2 shrink-0">
                    <span
                      className={`font-bold font-mono-num text-sm ${
                        t.isVoided
                          ? 'line-through text-[var(--p-text-muted)]'
                          : isInflow
                            ? 'text-[var(--p-success)]'
                            : 'text-[var(--p-danger)]'
                      }`}
                    >
                      {isInflow ? '+' : '-'}
                      {formatPaise(t.amountPaise)}
                    </span>
                    <button
                      type="button"
                      onClick={() => handlePrintTxn(t)}
                      className="p-1.5 rounded-lg border border-[var(--p-card-border)] text-[var(--p-text-muted)] hover:text-[var(--p-text)] hover:bg-[var(--p-secondary-bg)]"
                      title="Print / Preview"
                    >
                      <Printer className="w-3.5 h-3.5" />
                    </button>
                    {!t.isVoided && (
                      <button
                        type="button"
                        onClick={() => setVoidTarget(t)}
                        className="px-2 py-1 rounded-lg border border-[var(--p-card-border)] text-[11px] text-[var(--p-danger)] hover:bg-[var(--p-danger-container)]"
                      >
                        Void
                      </button>
                    )}
                  </div>
                </div>
              );
            })}
            {recentTxns.length === 0 && (
              <div className="p-8 text-center text-xs text-[var(--p-text-muted)]">
                No vouchers yet. Create your first GST invoice or payment entry.
              </div>
            )}
          </div>
        </div>

        {/* Low Stock & Compliance Shortcuts */}
        <div className="lg:col-span-4 space-y-4">
          <div className="rounded-2xl border border-[var(--p-card-border)] bg-[var(--p-card)] overflow-hidden">
            <div className="flex items-center justify-between px-4 py-3 border-b border-[var(--p-card-border)]">
              <div className="flex items-center gap-2">
                <AlertTriangle className="w-4 h-4 text-[var(--p-warning)]" />
                <h3 className="text-sm font-bold text-[var(--p-text)]">
                  Low Stock Alerts ({lowStockItems.length})
                </h3>
              </div>
              <button
                type="button"
                onClick={() => {
                  setThresholdInput(String(state.lowStockThreshold));
                  setThresholdModalOpen(true);
                }}
                className="inline-flex items-center gap-1 text-[11px] font-medium text-[var(--p-link)] hover:underline"
              >
                <SlidersHorizontal className="w-3 h-3" />
                &le; {state.lowStockThreshold}
              </button>
            </div>

            <div className="divide-y divide-[var(--p-card-border)]">
              {lowStockItems.slice(0, 5).map((item) => (
                <div key={item.id} className="flex items-center justify-between px-4 py-2.5 text-xs">
                  <div className="min-w-0 pr-2">
                    <p className="font-semibold text-[var(--p-text)] truncate">{item.name}</p>
                    <p className="text-[11px] text-[var(--p-text-muted)]">
                      HSN {item.hsnCode} · Rate {formatPaise(item.salePricePaise)}
                    </p>
                  </div>
                  <span className="rounded-md bg-[var(--p-warning-container)] px-2 py-0.5 text-[11px] font-bold font-mono-num text-[var(--p-on-warning-container)] shrink-0">
                    {formatQuantityMilli(item.currentStockMilli)} {item.unit}
                  </span>
                </div>
              ))}
              {lowStockItems.length === 0 && (
                <div className="p-4 text-xs text-[var(--p-text-muted)] flex items-center gap-2">
                  <Package className="w-4 h-4 text-[var(--p-success)]" />
                  <span>All active SKUs are above threshold ({state.lowStockThreshold} units).</span>
                </div>
              )}
            </div>

            <div className="p-3 bg-[var(--p-bg)] border-t border-[var(--p-card-border)]">
              <button
                type="button"
                onClick={() => setActiveScreen('stock')}
                className="w-full rounded-xl border border-[var(--p-card-border)] bg-[var(--p-card)] py-2 text-xs font-semibold text-[var(--p-text)] hover:bg-[var(--p-secondary-bg)]"
              >
                Manage Inventory &amp; Restock
              </button>
            </div>
          </div>

          {/* Compliance Shortcuts */}
          <div className="rounded-2xl border border-[var(--p-card-border)] bg-[var(--p-card)] p-4 space-y-2.5">
            <div className="flex items-center gap-2 text-xs font-bold text-[var(--p-text)]">
              <Wallet className="w-4 h-4 text-[var(--p-primary)]" />
              <span>GST &amp; Banking Intelligence</span>
            </div>
            <div className="grid grid-cols-2 gap-2">
              <button
                type="button"
                onClick={() => setActiveScreen('gst_assistant')}
                className="rounded-xl border border-[var(--p-card-border)] bg-[var(--p-bg)] p-2.5 text-left hover:bg-[var(--p-secondary-bg)]"
              >
                <p className="text-xs font-semibold">GSTR-1 &amp; 3B</p>
                <p className="text-[11px] text-[var(--p-text-muted)]">Rule 88A + JSON</p>
              </button>
              <button
                type="button"
                onClick={() => setActiveScreen('gstr2b_recon')}
                className="rounded-xl border border-[var(--p-card-border)] bg-[var(--p-bg)] p-2.5 text-left hover:bg-[var(--p-secondary-bg)]"
              >
                <p className="text-xs font-semibold">GSTR-2B Match</p>
                <p className="text-[11px] text-[var(--p-text-muted)]">Rule 36(4) ITC audit</p>
              </button>
              <button
                type="button"
                onClick={() => setActiveScreen('cma_report')}
                className="rounded-xl border border-[var(--p-card-border)] bg-[var(--p-bg)] p-2.5 text-left hover:bg-[var(--p-secondary-bg)]"
              >
                <p className="text-xs font-semibold">CMA Loan Report</p>
                <p className="text-[11px] text-[var(--p-text-muted)]">Forms I–VII MPBF</p>
              </button>
              <button
                type="button"
                onClick={() => setActiveScreen('cash_bank')}
                className="rounded-xl border border-[var(--p-card-border)] bg-[var(--p-bg)] p-2.5 text-left hover:bg-[var(--p-secondary-bg)]"
              >
                <p className="text-xs font-semibold">Bank Recon</p>
                <p className="text-[11px] text-[var(--p-text-muted)]">CSV / OFX auto-match</p>
              </button>
            </div>
          </div>
        </div>
      </div>

      {/* Low Stock Threshold Modal */}
      {thresholdModalOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 p-4">
          <div className="w-full max-w-sm rounded-2xl border border-[var(--p-card-border)] bg-[var(--p-card)] p-5 space-y-4">
            <div className="flex items-center justify-between">
              <h3 className="text-sm font-bold">Low Stock Alert Threshold</h3>
              <button
                type="button"
                onClick={() => setThresholdModalOpen(false)}
                className="p-1 rounded-lg text-[var(--p-text-muted)]"
              >
                <X className="w-4 h-4" />
              </button>
            </div>
            <p className="text-xs text-[var(--p-text-muted)]">
              Items at or below this quantity will be flagged on your dashboard and inventory tab.
            </p>
            <input
              type="number"
              min={1}
              value={thresholdInput}
              onChange={(e) => setThresholdInput(e.target.value)}
              className="w-full rounded-xl border border-[var(--p-card-border)] bg-[var(--p-bg)] px-3 py-2 text-sm font-mono-num"
            />
            <div className="flex justify-end gap-2">
              <button
                type="button"
                onClick={() => setThresholdModalOpen(false)}
                className="rounded-lg border border-[var(--p-card-border)] px-3 py-1.5 text-xs"
              >
                Cancel
              </button>
              <button
                type="button"
                onClick={() => {
                  const val = Number.parseInt(thresholdInput, 10);
                  if (Number.isFinite(val) && val > 0) updateLowStockThreshold(val);
                  setThresholdModalOpen(false);
                }}
                className="rounded-lg bg-[var(--p-primary)] px-3.5 py-1.5 text-xs font-semibold text-[var(--p-primary-text)]"
              >
                Save Threshold
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Quick Expense Modal */}
      {quickVoucherOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 p-4">
          <form
            onSubmit={submitQuickVoucher}
            className="w-full max-w-md rounded-2xl border border-[var(--p-card-border)] bg-[var(--p-card)] p-5 space-y-4"
          >
            <div className="flex items-center justify-between">
              <h3 className="text-sm font-bold">Record {quickVoucherOpen}</h3>
              <button
                type="button"
                onClick={() => setQuickVoucherOpen(null)}
                className="p-1 rounded-lg text-[var(--p-text-muted)]"
              >
                <X className="w-4 h-4" />
              </button>
            </div>
            <div>
              <label className="block text-xs font-medium mb-1">Amount (INR) *</label>
              <input
                type="number"
                step="0.01"
                required
                value={qAmount}
                onChange={(e) => setQAmount(e.target.value)}
                placeholder="0.00"
                className="w-full rounded-xl border border-[var(--p-card-border)] bg-[var(--p-bg)] px-3 py-2 text-sm font-mono-num"
              />
            </div>
            <div className="grid grid-cols-2 gap-3">
              <div>
                <label className="block text-xs font-medium mb-1">Expense Type</label>
                <select
                  value={qCategory}
                  onChange={(e) => setQCategory(e.target.value as 'Direct' | 'Indirect')}
                  className="w-full rounded-xl border border-[var(--p-card-border)] bg-[var(--p-bg)] px-3 py-2 text-xs"
                >
                  <option value="Indirect">Indirect (Rent/Salary/Admin)</option>
                  <option value="Direct">Direct (Freight/Labour)</option>
                </select>
              </div>
              <div>
                <label className="block text-xs font-medium mb-1">Payment Mode</label>
                <select
                  value={qMode}
                  onChange={(e) => setQMode(e.target.value as PaymentMode)}
                  className="w-full rounded-xl border border-[var(--p-card-border)] bg-[var(--p-bg)] px-3 py-2 text-xs"
                >
                  <option value="Cash">Cash</option>
                  <option value="Bank">Bank Transfer</option>
                  <option value="UPI">UPI</option>
                </select>
              </div>
            </div>
            <div>
              <label className="block text-xs font-medium mb-1">Particulars / Remarks</label>
              <input
                type="text"
                required
                value={qRemarks}
                onChange={(e) => setQRemarks(e.target.value)}
                placeholder="e.g. Shop Electricity Bill / Staff Salary"
                className="w-full rounded-xl border border-[var(--p-card-border)] bg-[var(--p-bg)] px-3 py-2 text-xs"
              />
            </div>
            <div className="flex justify-end gap-2">
              <button
                type="button"
                onClick={() => setQuickVoucherOpen(null)}
                className="rounded-lg border border-[var(--p-card-border)] px-3 py-2 text-xs"
              >
                Cancel
              </button>
              <button
                type="submit"
                className="rounded-lg bg-[var(--p-primary)] px-4 py-2 text-xs font-semibold text-[var(--p-primary-text)]"
              >
                Save Expense
              </button>
            </div>
          </form>
        </div>
      )}

      <VoidTransactionModal txn={voidTarget} onClose={() => setVoidTarget(null)} />
    </div>
  );
};
