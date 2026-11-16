import React, { useMemo, useState } from 'react';
import { Printer, Search } from 'lucide-react';
import { generateQrDataUrl } from '../components/QrCodeSvg';
import { VoidTransactionModal } from '../components/VoidTransactionModal';
import { buildInvoicePrintHtml, buildUpiPayUri } from '../engines/exportEngine';
import { formatPaise, isDateInFinancialYear } from '../engines/financialEngine';
import { usePabaru } from '../state/PabaruContext';
import { Transaction } from '../types/models';

export const DaybookScreen: React.FC = () => {
  const { state, openPrintPreview } = usePabaru();
  const [search, setSearch] = useState('');
  const [typeFilter, setTypeFilter] = useState('ALL');
  const [voidTarget, setVoidTarget] = useState<Transaction | null>(null);

  const txns = useMemo(() => {
    const q = search.trim().toLowerCase();
    return state.transactions.filter((t) => {
      if (!isDateInFinancialYear(t.date, state.selectedFy)) return false;
      if (typeFilter !== 'ALL' && t.type !== typeFilter) return false;
      if (!q) return true;
      const party = state.parties.find((p) => p.id === t.partyId)?.name.toLowerCase() || '';
      return (
        t.invoiceNumber.toLowerCase().includes(q) ||
        t.remarks.toLowerCase().includes(q) ||
        t.type.toLowerCase().includes(q) ||
        party.includes(q)
      );
    });
  }, [state.transactions, state.parties, state.selectedFy, search, typeFilter]);

  const handlePrint = async (txn: Transaction) => {
    const party = state.parties.find((p) => p.id === txn.partyId) || null;
    const bank = state.bankAccounts[0] || null;
    const qrDataUrl = bank?.upiId
      ? await generateQrDataUrl(buildUpiPayUri(bank.upiId, state.profile.businessName, txn.amountPaise))
      : null;
    openPrintPreview({
      title: `${txn.type} · ${txn.invoiceNumber || txn.id}`,
      html: buildInvoicePrintHtml(txn, state.profile, party, bank, qrDataUrl),
      filename: `${(txn.invoiceNumber || txn.id).replace(/[^a-zA-Z0-9-_]/g, '_')}.html`,
    });
  };

  return (
    <div className="space-y-5">
      <div>
        <h2 className="text-lg md:text-xl font-bold">Daybook Register</h2>
        <p className="text-xs text-[var(--p-text-muted)]">
          Chronological journal for {state.selectedFy} ({txns.length} vouchers)
        </p>
      </div>

      <div className="flex flex-col sm:flex-row gap-3">
        <div className="relative flex-1">
          <Search className="w-4 h-4 text-[var(--p-text-muted)] absolute left-3 top-2.5" />
          <input
            type="text"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            placeholder="Search voucher #, party, narration..."
            className="w-full rounded-xl border border-[var(--p-card-border)] bg-[var(--p-card)] pl-9 pr-3 py-2 text-xs"
          />
        </div>
        <select
          value={typeFilter}
          onChange={(e) => setTypeFilter(e.target.value)}
          className="rounded-xl border border-[var(--p-card-border)] bg-[var(--p-card)] px-3 py-2 text-xs"
        >
          <option value="ALL">All Voucher Types</option>
          <option value="Sale">Sales</option>
          <option value="Purchase">Purchases</option>
          <option value="PaymentIn">Payments In</option>
          <option value="PaymentOut">Payments Out</option>
          <option value="Expense">Expenses</option>
          <option value="CreditNote">Credit Notes</option>
          <option value="DebitNote">Debit Notes</option>
        </select>
      </div>

      <div className="rounded-2xl border border-[var(--p-card-border)] bg-[var(--p-card)] overflow-hidden">
        <div className="overflow-x-auto">
          <table className="w-full text-left text-xs">
            <thead className="bg-[var(--p-secondary-bg)] text-[var(--p-text-muted)] uppercase text-[10px]">
              <tr>
                <th className="px-4 py-2.5">Date</th>
                <th className="px-4 py-2.5">Voucher / Party</th>
                <th className="px-4 py-2.5">Type &amp; Mode</th>
                <th className="px-4 py-2.5 text-right">Amount</th>
                <th className="px-4 py-2.5 text-right">Actions</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-[var(--p-card-border)]">
              {txns.map((t) => {
                const party = state.parties.find((p) => p.id === t.partyId);
                return (
                  <tr key={t.id} className={t.isVoided ? 'opacity-50' : 'hover:bg-[var(--p-bg)]'}>
                    <td className="px-4 py-2.5 whitespace-nowrap font-mono-num">{t.date}</td>
                    <td className="px-4 py-2.5">
                      <p className="font-semibold">
                        {party?.name || t.remarks || t.type}
                      </p>
                      <p className="text-[11px] text-[var(--p-text-muted)]">
                        {t.invoiceNumber || t.id} {t.remarks ? `· ${t.remarks}` : ''}
                      </p>
                    </td>
                    <td className="px-4 py-2.5 whitespace-nowrap">
                      {t.type} · {t.paymentMode}
                    </td>
                    <td className="px-4 py-2.5 text-right font-mono-num font-bold whitespace-nowrap">
                      {formatPaise(t.amountPaise)}
                    </td>
                    <td className="px-4 py-2.5 text-right whitespace-nowrap">
                      <div className="inline-flex items-center gap-1.5">
                        <button
                          type="button"
                          onClick={() => handlePrint(t)}
                          className="p-1.5 rounded-lg border border-[var(--p-card-border)] hover:bg-[var(--p-secondary-bg)]"
                        >
                          <Printer className="w-3.5 h-3.5" />
                        </button>
                        {!t.isVoided && (
                          <button
                            type="button"
                            onClick={() => setVoidTarget(t)}
                            className="rounded-lg border border-[var(--p-card-border)] px-2 py-1 text-[11px] text-[var(--p-danger)]"
                          >
                            Void
                          </button>
                        )}
                      </div>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      </div>

      <VoidTransactionModal txn={voidTarget} onClose={() => setVoidTarget(null)} />
    </div>
  );
};
