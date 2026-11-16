import React, { useMemo, useState } from 'react';
import {
  ArrowLeft,
  Clock,
  IndianRupee,
  MessageCircle,
  Plus,
  Search,
  UserPlus,
  X,
} from 'lucide-react';
import {
  INDIAN_STATES,
  formatPaise,
  getTodayDate,
  parsePaiseFromRupees,
  validateGstin,
} from '../engines/financialEngine';
import { usePabaru } from '../state/PabaruContext';
import { BalanceType, Party, PaymentMode } from '../types/models';

export const LedgerMenuScreen: React.FC = () => {
  const {
    state,
    activeScreen,
    setActiveScreen,
    selectedPartyId,
    setSelectedPartyId,
    getPartyBalancePaise,
    addParty,
    recordPayment,
    openWhatsAppShare,
  } = usePabaru();

  const [filter, setFilter] = useState<'all' | 'receivable' | 'payable'>('all');
  const [search, setSearch] = useState('');
  const [addOpen, setAddOpen] = useState(false);

  // Add party form
  const [pName, setPName] = useState('');
  const [pPhone, setPPhone] = useState('');
  const [pGstin, setPGstin] = useState('');
  const [pState, setPState] = useState(state.profile.homeStateCode || '27');
  const [pAddr, setPAddr] = useState('');
  const [pOpenBal, setPOpenBal] = useState('0');
  const [pOpenType, setPOpenType] = useState<BalanceType>('Settled');
  const [pSupplier, setPSupplier] = useState(false);

  // Record payment modal
  const [payTarget, setPayTarget] = useState<Party | null>(null);
  const [payDir, setPayDir] = useState<'PaymentIn' | 'PaymentOut'>('PaymentIn');
  const [payAmount, setPayAmount] = useState('');
  const [payMode, setPayMode] = useState<PaymentMode>('UPI');
  const [payBank, setPayBank] = useState(state.bankAccounts[0]?.id || '');
  const [payRemarks, setPayRemarks] = useState('');

  const partyRows = useMemo(() => {
    const q = search.trim().toLowerCase();
    return state.parties
      .map((p) => ({ party: p, balance: getPartyBalancePaise(p.id) }))
      .filter(({ party, balance }) => {
        if (filter === 'receivable' && balance <= 0) return false;
        if (filter === 'payable' && balance >= 0) return false;
        if (!q) return true;
        return (
          party.name.toLowerCase().includes(q) ||
          party.gstin.toLowerCase().includes(q) ||
          party.phone.includes(q)
        );
      });
  }, [state.parties, getPartyBalancePaise, filter, search]);

  const totalReceivable = partyRows
    .filter((r) => r.balance > 0)
    .reduce((s, r) => s + r.balance, 0);
  const totalPayable = partyRows
    .filter((r) => r.balance < 0)
    .reduce((s, r) => s + Math.abs(r.balance), 0);

  const gstinCheck = validateGstin(pGstin, true);

  const handleAddParty = (e: React.FormEvent) => {
    e.preventDefault();
    if (!pName.trim() || (pGstin.trim() && !gstinCheck.isValid)) return;
    addParty({
      name: pName.trim(),
      phone: pPhone.trim(),
      address: pAddr.trim(),
      gstin: pGstin.trim().toUpperCase(),
      pan: pGstin.trim().length >= 12 ? pGstin.trim().slice(2, 12).toUpperCase() : '',
      stateCode: pGstin.trim().length >= 2 ? pGstin.slice(0, 2) : pState,
      openingBalancePaise: parsePaiseFromRupees(pOpenBal),
      openingBalanceType: pOpenType,
      isSupplier: pSupplier,
    });
    setPName('');
    setPPhone('');
    setPGstin('');
    setPAddr('');
    setPOpenBal('0');
    setAddOpen(false);
  };

  const submitPayment = (e: React.FormEvent) => {
    e.preventDefault();
    if (!payTarget) return;
    const paise = parsePaiseFromRupees(payAmount);
    if (paise <= 0) return;
    recordPayment({
      partyId: payTarget.id,
      direction: payDir,
      amountPaise: paise,
      date: getTodayDate(),
      paymentMode: payMode,
      bankAccountId: payMode === 'Cash' ? null : payBank || null,
      remarks: payRemarks,
    });
    setPayTarget(null);
    setPayAmount('');
    setPayRemarks('');
  };

  // Unpaid Bills FIFO Aging Detail View
  if (activeScreen === 'party_unpaid' && selectedPartyId) {
    const party = state.parties.find((p) => p.id === selectedPartyId);
    const bal = party ? getPartyBalancePaise(party.id) : 0;
    const partyTxns = state.transactions.filter((t) => !t.isVoided && t.partyId === selectedPartyId);
    const bills = partyTxns.filter(
      (t) =>
        (t.type === 'Sale' || t.type === 'NonGstBill' || t.type === 'Purchase') &&
        t.paymentMode === 'Credit'
    );
    const totalPayments = partyTxns
      .filter((t) => t.type === 'PaymentIn' || t.type === 'PaymentOut')
      .reduce((s, t) => s + t.amountPaise, 0);

    // FIFO allocation oldest first
    let remainingPool = totalPayments;
    const fifoRows = bills
      .slice()
      .sort((a, b) => a.date.localeCompare(b.date))
      .map((b) => {
        const settled = Math.min(b.amountPaise, remainingPool);
        remainingPool = Math.max(0, remainingPool - settled);
        const unpaid = b.amountPaise - settled;
        const ageDays = Math.max(
          0,
          Math.floor((Date.now() - Date.parse(b.date || getTodayDate())) / 86_400_000)
        );
        return { txn: b, settled, unpaid, ageDays };
      })
      .reverse();

    return (
      <div className="space-y-5">
        <div className="flex items-center justify-between gap-3">
          <button
            type="button"
            onClick={() => setActiveScreen('ledgers')}
            className="inline-flex items-center gap-1.5 text-xs font-semibold text-[var(--p-link)] hover:underline"
          >
            <ArrowLeft className="w-4 h-4" />
            Back to Parties &amp; Khata
          </button>
          {party && (
            <div className="flex items-center gap-2">
              <button
                type="button"
                onClick={() => openWhatsAppShare({ party, amountPaise: bal })}
                className="inline-flex items-center gap-1.5 rounded-xl border border-[var(--p-card-border)] bg-[var(--p-card)] px-3 py-1.5 text-xs font-semibold text-[var(--p-success)]"
              >
                <MessageCircle className="w-3.5 h-3.5" />
                Send Reminder
              </button>
              <button
                type="button"
                onClick={() => {
                  setPayTarget(party);
                  setPayDir(bal >= 0 ? 'PaymentIn' : 'PaymentOut');
                  setPayAmount((Math.abs(bal) / 100).toFixed(2));
                }}
                className="inline-flex items-center gap-1.5 rounded-xl bg-[var(--p-primary)] px-3 py-1.5 text-xs font-semibold text-[var(--p-primary-text)]"
              >
                <IndianRupee className="w-3.5 h-3.5" />
                Settle Balance
              </button>
            </div>
          )}
        </div>

        <div className="rounded-2xl border border-[var(--p-card-border)] bg-[var(--p-card)] p-4 flex flex-wrap items-center justify-between gap-4">
          <div>
            <h2 className="text-lg font-bold">{party?.name || 'Party'}</h2>
            <p className="text-xs text-[var(--p-text-muted)]">
              {party?.gstin ? `GSTIN ${party.gstin}` : 'Unregistered'} · {party?.phone || 'No phone'}
            </p>
          </div>
          <div className="text-right">
            <p className="text-xs text-[var(--p-text-muted)]">Net Khata Balance</p>
            <p
              className={`text-xl font-bold font-mono-num ${
                bal > 0
                  ? 'text-[var(--p-success)]'
                  : bal < 0
                    ? 'text-[var(--p-danger)]'
                    : 'text-[var(--p-text)]'
              }`}
            >
              {formatPaise(Math.abs(bal))} {bal > 0 ? 'Dr (Receivable)' : bal < 0 ? 'Cr (Payable)' : '(Settled)'}
            </p>
          </div>
        </div>

        <div className="rounded-2xl border border-[var(--p-card-border)] bg-[var(--p-card)] overflow-hidden">
          <div className="px-4 py-3 border-b border-[var(--p-card-border)] flex items-center gap-2">
            <Clock className="w-4 h-4 text-[var(--p-primary)]" />
            <h3 className="text-sm font-bold">FIFO Unpaid Bills &amp; Overdue Aging</h3>
          </div>
          <div className="overflow-x-auto">
            <table className="w-full text-left text-xs">
              <thead className="bg-[var(--p-secondary-bg)] text-[var(--p-text-muted)] uppercase text-[10px]">
                <tr>
                  <th className="px-4 py-2.5">Bill #</th>
                  <th className="px-4 py-2.5">Date</th>
                  <th className="px-4 py-2.5 text-right">Bill Amount</th>
                  <th className="px-4 py-2.5 text-right">Settled (FIFO)</th>
                  <th className="px-4 py-2.5 text-right">Pending</th>
                  <th className="px-4 py-2.5">Aging</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-[var(--p-card-border)]">
                {fifoRows.map(({ txn, settled, unpaid, ageDays }) => (
                  <tr key={txn.id}>
                    <td className="px-4 py-2.5 font-mono-num font-bold">{txn.invoiceNumber || txn.id}</td>
                    <td className="px-4 py-2.5">{txn.date}</td>
                    <td className="px-4 py-2.5 text-right font-mono-num">{formatPaise(txn.amountPaise)}</td>
                    <td className="px-4 py-2.5 text-right font-mono-num text-[var(--p-success)]">
                      {formatPaise(settled)}
                    </td>
                    <td className="px-4 py-2.5 text-right font-mono-num font-bold text-[var(--p-danger)]">
                      {formatPaise(unpaid)}
                    </td>
                    <td className="px-4 py-2.5">
                      {unpaid === 0 ? (
                        <span className="rounded bg-[var(--p-success-container)] px-2 py-0.5 text-[10px] font-semibold text-[var(--p-on-success-container)]">
                          Paid
                        </span>
                      ) : (
                        <span className="rounded bg-[var(--p-warning-container)] px-2 py-0.5 text-[10px] font-semibold text-[var(--p-on-warning-container)]">
                          {ageDays}d overdue
                        </span>
                      )}
                    </td>
                  </tr>
                ))}
                {fifoRows.length === 0 && (
                  <tr>
                    <td colSpan={6} className="px-4 py-6 text-center text-[var(--p-text-muted)]">
                      No credit bills recorded for this party.
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="space-y-5">
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
        <div>
          <h2 className="text-lg md:text-xl font-bold">Parties &amp; Khata Ledgers</h2>
          <p className="text-xs text-[var(--p-text-muted)]">
            Receivable <strong className="font-mono-num text-[var(--p-success)]">{formatPaise(totalReceivable)}</strong> ·
            Payable <strong className="font-mono-num text-[var(--p-danger)]">{formatPaise(totalPayable)}</strong>
          </p>
        </div>

        <button
          type="button"
          onClick={() => setAddOpen(true)}
          className="inline-flex items-center gap-1.5 whitespace-nowrap shrink-0 rounded-xl bg-[var(--p-primary)] px-4 py-2 text-xs font-semibold text-[var(--p-primary-text)] hover:opacity-90"
        >
          <UserPlus className="w-4 h-4" />
          Add Party
        </button>
      </div>

      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
        <div className="flex gap-1.5">
          {[
            { id: 'all' as const, label: 'All Parties' },
            { id: 'receivable' as const, label: "You'll Get (Dr)" },
            { id: 'payable' as const, label: "You'll Pay (Cr)" },
          ].map((f) => (
            <button
              key={f.id}
              type="button"
              onClick={() => setFilter(f.id)}
              className={`whitespace-nowrap rounded-xl border px-3 py-1.5 text-xs font-semibold ${
                filter === f.id
                  ? 'border-[var(--p-primary)] bg-[var(--p-primary)] text-[var(--p-primary-text)]'
                  : 'border-[var(--p-card-border)] bg-[var(--p-card)]'
              }`}
            >
              {f.label}
            </button>
          ))}
        </div>

        <div className="relative w-full sm:w-72">
          <Search className="w-4 h-4 text-[var(--p-text-muted)] absolute left-3 top-2.5" />
          <input
            type="text"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            placeholder="Search name, GSTIN, mobile..."
            className="w-full rounded-xl border border-[var(--p-card-border)] bg-[var(--p-card)] pl-9 pr-3 py-2 text-xs"
          />
        </div>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-2 gap-3.5">
        {partyRows.map(({ party, balance }) => (
          <div
            key={party.id}
            className="rounded-2xl border border-[var(--p-card-border)] bg-[var(--p-card)] p-4 flex flex-col justify-between gap-3"
          >
            <div className="flex items-start justify-between gap-3">
              <div className="min-w-0">
                <div className="flex items-center gap-2">
                  <p className="text-sm font-bold truncate">{party.name}</p>
                  {party.isSupplier && (
                    <span className="rounded bg-[var(--p-secondary-bg)] px-1.5 py-0.5 text-[10px] font-semibold">
                      Supplier
                    </span>
                  )}
                </div>
                <p className="text-[11px] text-[var(--p-text-muted)] mt-0.5 truncate">
                  {party.gstin ? `GSTIN ${party.gstin}` : `Unregistered · State ${party.stateCode}`} ·{' '}
                  {party.phone || 'No mobile'}
                </p>
              </div>
              <div className="text-right shrink-0">
                <p
                  className={`text-base font-bold font-mono-num ${
                    balance > 0
                      ? 'text-[var(--p-success)]'
                      : balance < 0
                        ? 'text-[var(--p-danger)]'
                        : 'text-[var(--p-text-muted)]'
                  }`}
                >
                  {formatPaise(Math.abs(balance))}
                </p>
                <p className="text-[10px] text-[var(--p-text-muted)]">
                  {balance > 0 ? "You'll Get" : balance < 0 ? "You'll Pay" : 'Settled'}
                </p>
              </div>
            </div>

            <div className="flex items-center justify-between gap-2 pt-2 border-t border-[var(--p-card-border)]">
              <button
                type="button"
                onClick={() => {
                  setSelectedPartyId(party.id);
                  setActiveScreen('party_unpaid');
                }}
                className="text-xs font-semibold text-[var(--p-link)] hover:underline"
              >
                FIFO Bills &amp; Aging
              </button>
              <div className="flex items-center gap-1.5">
                {balance > 0 && (
                  <button
                    type="button"
                    onClick={() => openWhatsAppShare({ party, amountPaise: balance })}
                    className="inline-flex items-center gap-1 rounded-lg border border-[var(--p-card-border)] px-2.5 py-1 text-[11px] font-medium text-[var(--p-success)] hover:bg-[var(--p-success-container)]"
                  >
                    <MessageCircle className="w-3 h-3" />
                    Remind
                  </button>
                )}
                <button
                  type="button"
                  onClick={() => {
                    setPayTarget(party);
                    setPayDir(balance < 0 ? 'PaymentOut' : 'PaymentIn');
                    setPayAmount(balance !== 0 ? (Math.abs(balance) / 100).toFixed(2) : '');
                  }}
                  className="inline-flex items-center gap-1 rounded-lg bg-[var(--p-primary)] px-2.5 py-1 text-[11px] font-semibold text-[var(--p-primary-text)]"
                >
                  <Plus className="w-3 h-3" />
                  Payment
                </button>
              </div>
            </div>
          </div>
        ))}
      </div>

      {/* Add Party Modal */}
      {addOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 p-4">
          <form
            onSubmit={handleAddParty}
            className="w-full max-w-md rounded-2xl border border-[var(--p-card-border)] bg-[var(--p-card)] p-5 space-y-3"
          >
            <div className="flex items-center justify-between">
              <h3 className="text-sm font-bold">Add Customer / Supplier</h3>
              <button type="button" onClick={() => setAddOpen(false)}>
                <X className="w-4 h-4" />
              </button>
            </div>
            <input
              type="text"
              required
              value={pName}
              onChange={(e) => setPName(e.target.value)}
              placeholder="Party Business / Full Name *"
              className="w-full rounded-xl border border-[var(--p-card-border)] bg-[var(--p-bg)] px-3 py-2 text-xs"
            />
            <div className="grid grid-cols-2 gap-2.5">
              <input
                type="tel"
                value={pPhone}
                onChange={(e) => setPPhone(e.target.value)}
                placeholder="Mobile Number"
                className="rounded-xl border border-[var(--p-card-border)] bg-[var(--p-bg)] px-3 py-2 text-xs"
              />
              <select
                value={pState}
                onChange={(e) => setPState(e.target.value)}
                className="rounded-xl border border-[var(--p-card-border)] bg-[var(--p-bg)] px-2.5 py-2 text-xs"
              >
                {INDIAN_STATES.map((s) => (
                  <option key={s.code} value={s.code}>
                    {s.code} - {s.name}
                  </option>
                ))}
              </select>
            </div>
            <div>
              <input
                type="text"
                maxLength={15}
                value={pGstin}
                onChange={(e) => setPGstin(e.target.value.toUpperCase())}
                placeholder="15-Char GSTIN (Auto-validates Modulo-36)"
                className="w-full rounded-xl border border-[var(--p-card-border)] bg-[var(--p-bg)] px-3 py-2 text-xs font-mono-num uppercase"
              />
              {pGstin.trim() && (
                <p
                  className={`text-[11px] mt-1 ${
                    gstinCheck.isValid ? 'text-[var(--p-success)]' : 'text-[var(--p-danger)]'
                  }`}
                >
                  {gstinCheck.message}
                </p>
              )}
            </div>
            <input
              type="text"
              value={pAddr}
              onChange={(e) => setPAddr(e.target.value)}
              placeholder="Billing Address"
              className="w-full rounded-xl border border-[var(--p-card-border)] bg-[var(--p-bg)] px-3 py-2 text-xs"
            />
            <div className="grid grid-cols-2 gap-2.5">
              <input
                type="number"
                step="0.01"
                value={pOpenBal}
                onChange={(e) => setPOpenBal(e.target.value)}
                placeholder="Opening Balance ₹"
                className="rounded-xl border border-[var(--p-card-border)] bg-[var(--p-bg)] px-3 py-2 text-xs font-mono-num"
              />
              <select
                value={pOpenType}
                onChange={(e) => setPOpenType(e.target.value as BalanceType)}
                className="rounded-xl border border-[var(--p-card-border)] bg-[var(--p-bg)] px-2.5 py-2 text-xs"
              >
                <option value="Settled">Settled (₹0)</option>
                <option value="Receivable">Receivable (Dr)</option>
                <option value="Payable">Payable (Cr)</option>
              </select>
            </div>
            <label className="flex items-center gap-2 text-xs">
              <input
                type="checkbox"
                checked={pSupplier}
                onChange={(e) => setPSupplier(e.target.checked)}
              />
              Mark as Supplier / Vendor
            </label>
            <div className="flex justify-end gap-2 pt-1">
              <button
                type="button"
                onClick={() => setAddOpen(false)}
                className="rounded-lg border border-[var(--p-card-border)] px-3 py-1.5 text-xs"
              >
                Cancel
              </button>
              <button
                type="submit"
                disabled={Boolean(pGstin.trim() && !gstinCheck.isValid)}
                className="rounded-lg bg-[var(--p-primary)] px-4 py-1.5 text-xs font-semibold text-[var(--p-primary-text)] disabled:opacity-50"
              >
                Save Party
              </button>
            </div>
          </form>
        </div>
      )}

      {/* Record Payment Modal */}
      {payTarget && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 p-4">
          <form
            onSubmit={submitPayment}
            className="w-full max-w-md rounded-2xl border border-[var(--p-card-border)] bg-[var(--p-card)] p-5 space-y-3"
          >
            <div className="flex items-center justify-between">
              <h3 className="text-sm font-bold">Record Settlement · {payTarget.name}</h3>
              <button type="button" onClick={() => setPayTarget(null)}>
                <X className="w-4 h-4" />
              </button>
            </div>
            <div className="grid grid-cols-2 gap-2">
              <button
                type="button"
                onClick={() => setPayDir('PaymentIn')}
                className={`rounded-xl border py-2 text-xs font-semibold ${
                  payDir === 'PaymentIn'
                    ? 'border-[var(--p-success)] bg-[var(--p-success-container)] text-[var(--p-on-success-container)]'
                    : 'border-[var(--p-card-border)]'
                }`}
              >
                Received (Payment In)
              </button>
              <button
                type="button"
                onClick={() => setPayDir('PaymentOut')}
                className={`rounded-xl border py-2 text-xs font-semibold ${
                  payDir === 'PaymentOut'
                    ? 'border-[var(--p-danger)] bg-[var(--p-danger-container)] text-[var(--p-on-danger-container)]'
                    : 'border-[var(--p-card-border)]'
                }`}
              >
                Paid (Payment Out)
              </button>
            </div>
            <input
              type="number"
              step="0.01"
              required
              value={payAmount}
              onChange={(e) => setPayAmount(e.target.value)}
              placeholder="Amount ₹"
              className="w-full rounded-xl border border-[var(--p-card-border)] bg-[var(--p-bg)] px-3 py-2 text-sm font-mono-num"
            />
            <div className="grid grid-cols-2 gap-2.5">
              <select
                value={payMode}
                onChange={(e) => setPayMode(e.target.value as PaymentMode)}
                className="rounded-xl border border-[var(--p-card-border)] bg-[var(--p-bg)] px-3 py-2 text-xs"
              >
                <option value="UPI">UPI</option>
                <option value="Bank">Bank Transfer</option>
                <option value="Cash">Cash</option>
                <option value="Cheque">Cheque</option>
              </select>
              {payMode !== 'Cash' && (
                <select
                  value={payBank}
                  onChange={(e) => setPayBank(e.target.value)}
                  className="rounded-xl border border-[var(--p-card-border)] bg-[var(--p-bg)] px-3 py-2 text-xs"
                >
                  {state.bankAccounts.map((b) => (
                    <option key={b.id} value={b.id}>
                      {b.bankName}
                    </option>
                  ))}
                </select>
              )}
            </div>
            <input
              type="text"
              value={payRemarks}
              onChange={(e) => setPayRemarks(e.target.value)}
              placeholder="UTR / Reference / Remarks"
              className="w-full rounded-xl border border-[var(--p-card-border)] bg-[var(--p-bg)] px-3 py-2 text-xs"
            />
            <div className="flex justify-end gap-2 pt-1">
              <button
                type="button"
                onClick={() => setPayTarget(null)}
                className="rounded-lg border border-[var(--p-card-border)] px-3 py-1.5 text-xs"
              >
                Cancel
              </button>
              <button
                type="submit"
                className="rounded-lg bg-[var(--p-primary)] px-4 py-1.5 text-xs font-semibold text-[var(--p-primary-text)]"
              >
                Post Settlement
              </button>
            </div>
          </form>
        </div>
      )}
    </div>
  );
};
