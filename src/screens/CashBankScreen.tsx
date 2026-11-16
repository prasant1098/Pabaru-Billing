import React, { useMemo, useState } from 'react';
import {
  ArrowLeftRight,
  Check,
  CheckCircle2,
  FileSpreadsheet,
  Landmark,
  Link2,
  Lock,
  Plus,
  QrCode,
  RefreshCw,
  Trash2,
  Unlink2,
  Upload,
  Wallet,
  Wrench,
  X,
} from 'lucide-react';
import { QrCodeSvg } from '../components/QrCodeSvg';
import { DiscrepancyResolutionType } from '../engines/bankReconEngine';
import { buildUpiPayUri } from '../engines/exportEngine';
import { formatPaise, parsePaiseFromRupees, validateIfsc } from '../engines/financialEngine';
import { usePabaru } from '../state/PabaruContext';
import { BankReconItem } from '../types/models';

export const CashBankScreen: React.FC = () => {
  const {
    state,
    getLiveCashBalancePaise,
    getBankAccountBalancePaise,
    getTotalBankBalancePaise,
    addBankAccount,
    adjustCashPaise,
    transferBetweenAccountsPaise,
    importStatementText,
    importSampleStatement,
    reconcileBankFeed,
    confirmMatch,
    confirmAllMatches,
    manualMatch,
    unlinkMatch,
    resolveDiscrepancy,
    clearStatementItems,
    addQrCode,
    deleteQrCode,
    setDefaultQr,
    toggleQrEnabled,
    toggleQrShowOnInvoices,
    verifyManagerPin,
    lockManager,
    unlockBankReconDemo,
  } = usePabaru();

  const [tab, setTab] = useState<'accounts' | 'qrs' | 'recon'>('accounts');

  // Cash adjust modal
  const [cashModalOpen, setCashModalOpen] = useState(false);
  const [newCashStr, setNewCashStr] = useState('');
  const [cashReason, setCashReason] = useState('');

  // Contra modal
  const [contraOpen, setContraOpen] = useState(false);
  const [cFrom, setCFrom] = useState('CASH');
  const [cTo, setCTo] = useState(state.bankAccounts[0]?.id || 'CASH');
  const [cAmount, setCAmount] = useState('');
  const [cRemarks, setCRemarks] = useState('');

  // Add Bank modal
  const [bankModalOpen, setBankModalOpen] = useState(false);
  const [bAcctName, setBAcctName] = useState('');
  const [bBankName, setBBankName] = useState('');
  const [bAcctNo, setBAcctNo] = useState('');
  const [bIfsc, setBIfsc] = useState('');
  const [bUpi, setBUpi] = useState('');
  const [bOpenBal, setBOpenBal] = useState('0');

  // Manager PIN Gate for UPI QR management
  const [mgrPinInput, setMgrPinInput] = useState('');
  const [qrAddOpen, setQrAddOpen] = useState(false);
  const [qrLabel, setQrLabel] = useState('');
  const [qrUpiId, setQrUpiId] = useState('');
  const [qrBankId, setQrBankId] = useState(state.bankAccounts[0]?.id || '');
  const [qrDefault, setQrDefault] = useState(true);

  // Recon controls
  const [reconBankId, setReconBankId] = useState(state.bankAccounts[0]?.id || 'BNK-1');
  const [manualModalItem, setManualModalItem] = useState<BankReconItem | null>(null);
  const [resolveModalItem, setResolveModalItem] = useState<BankReconItem | null>(null);
  const [resType, setResType] = useState<DiscrepancyResolutionType>('BANK_CHARGES');
  const [resPartyId, setResPartyId] = useState('');
  const [resRemarks, setResRemarks] = useState('');

  const cashBal = getLiveCashBalancePaise();
  const bankTotal = getTotalBankBalancePaise();

  const cashBankTxns = useMemo(
    () => state.transactions.filter((t) => !t.isVoided && t.paymentMode !== 'Credit').slice(0, 20),
    [state.transactions]
  );

  const reconSummary = useMemo(() => {
    const items = state.bankReconItems;
    const matched = items.filter((i) => i.isReconciled);
    const review = items.filter((i) => !i.isReconciled && i.matchedTxnId);
    const unmatched = items.filter((i) => !i.matchedTxnId);
    return {
      total: items.length,
      matchedCount: matched.length,
      reviewCount: review.length,
      unmatchedCount: unmatched.length,
    };
  }, [state.bankReconItems]);

  const isMgrAllowed =
    !state.hasManagerPinSet ||
    (state.isManagerUnlocked &&
      (state.managerUnlockExpiresAtMs === 0 || state.managerUnlockExpiresAtMs > Date.now()));

  const handleFileUpload = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;
    const text = await file.text();
    const bank = state.bankAccounts.find((b) => b.id === reconBankId);
    importStatementText(text, reconBankId, bank?.bankName || 'Uploaded Bank');
    e.target.value = '';
  };

  return (
    <div className="space-y-5">
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
        <div>
          <h2 className="text-lg md:text-xl font-bold">Cash, Bank &amp; UPI Reconciliation</h2>
          <p className="text-xs text-[var(--p-text-muted)]">
            Cash in Hand <strong className="font-mono-num">{formatPaise(cashBal)}</strong> · Bank
            Total <strong className="font-mono-num">{formatPaise(bankTotal)}</strong>
          </p>
        </div>

        <div className="flex flex-wrap gap-2">
          <button
            type="button"
            onClick={() => {
              setNewCashStr((cashBal / 100).toFixed(2));
              setCashModalOpen(true);
            }}
            className="inline-flex items-center gap-1.5 whitespace-nowrap rounded-xl border border-[var(--p-card-border)] bg-[var(--p-card)] px-3 py-2 text-xs font-medium hover:bg-[var(--p-secondary-bg)]"
          >
            <Wallet className="w-3.5 h-3.5" />
            Tally Cash Drawer
          </button>
          <button
            type="button"
            onClick={() => setContraOpen(true)}
            className="inline-flex items-center gap-1.5 whitespace-nowrap rounded-xl border border-[var(--p-card-border)] bg-[var(--p-card)] px-3 py-2 text-xs font-medium hover:bg-[var(--p-secondary-bg)]"
          >
            <ArrowLeftRight className="w-3.5 h-3.5" />
            Contra Transfer
          </button>
          <button
            type="button"
            onClick={() => setBankModalOpen(true)}
            className="inline-flex items-center gap-1.5 whitespace-nowrap rounded-xl bg-[var(--p-primary)] px-3.5 py-2 text-xs font-semibold text-[var(--p-primary-text)]"
          >
            <Plus className="w-3.5 h-3.5" />
            Add Bank Account
          </button>
        </div>
      </div>

      {/* Tabs */}
      <div className="flex flex-wrap gap-1.5">
        {[
          { id: 'accounts' as const, label: 'Cash & Bank Accounts', icon: <Landmark className="w-3.5 h-3.5" /> },
          { id: 'qrs' as const, label: `Manage UPI QRs (${state.qrCodes.length})`, icon: <QrCode className="w-3.5 h-3.5" /> },
          { id: 'recon' as const, label: `Statement Recon (${state.bankReconItems.length})`, icon: <FileSpreadsheet className="w-3.5 h-3.5" /> },
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

      {tab === 'accounts' && (
        <div className="space-y-5">
          <div className="grid grid-cols-1 md:grid-cols-3 gap-3.5">
            <div className="rounded-2xl border border-[var(--p-card-border)] bg-[var(--p-card)] p-4">
              <div className="flex items-center justify-between text-xs text-[var(--p-text-muted)]">
                <span>Physical Cash Drawer</span>
                <Wallet className="w-4 h-4 text-[var(--p-success)]" />
              </div>
              <p className="text-xl font-bold font-mono-num mt-1.5">{formatPaise(cashBal)}</p>
              <p className="text-[11px] text-[var(--p-text-muted)] mt-1">
                Opening: {formatPaise(state.profile.openingCashBalancePaise)}
              </p>
            </div>

            {state.bankAccounts.map((b) => {
              const bal = getBankAccountBalancePaise(b.id);
              return (
                <div
                  key={b.id}
                  className="rounded-2xl border border-[var(--p-card-border)] bg-[var(--p-card)] p-4"
                >
                  <div className="flex items-center justify-between text-xs">
                    <span className="font-bold text-[var(--p-text)] truncate">{b.bankName}</span>
                    {b.isDefaultForQr && (
                      <span className="rounded bg-[var(--p-success-container)] px-1.5 py-0.5 text-[10px] font-semibold text-[var(--p-on-success-container)]">
                        Primary QR
                      </span>
                    )}
                  </div>
                  <p className="text-xl font-bold font-mono-num mt-1.5">{formatPaise(bal)}</p>
                  <p className="text-[11px] text-[var(--p-text-muted)] mt-1 truncate">
                    A/c {b.accountNumber} · IFSC {b.ifscCode}
                  </p>
                  {b.upiId && (
                    <p className="text-[11px] text-[var(--p-link)] truncate">UPI: {b.upiId}</p>
                  )}
                </div>
              );
            })}
          </div>

          <div className="rounded-2xl border border-[var(--p-card-border)] bg-[var(--p-card)] overflow-hidden">
            <div className="px-4 py-3 border-b border-[var(--p-card-border)]">
              <h3 className="text-sm font-bold">Recent Cash &amp; Bank Movements</h3>
            </div>
            <div className="overflow-x-auto">
              <table className="w-full text-left text-xs">
                <thead className="bg-[var(--p-secondary-bg)] text-[var(--p-text-muted)] uppercase text-[10px]">
                  <tr>
                    <th className="px-4 py-2.5">Date</th>
                    <th className="px-4 py-2.5">Mode</th>
                    <th className="px-4 py-2.5">Particulars</th>
                    <th className="px-4 py-2.5 text-right">Amount</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-[var(--p-card-border)]">
                  {cashBankTxns.map((t) => {
                    const isIn =
                      t.type === 'Sale' ||
                      t.type === 'NonGstBill' ||
                      t.type === 'PaymentIn' ||
                      t.type === 'DebitNote';
                    return (
                      <tr key={t.id}>
                        <td className="px-4 py-2.5 whitespace-nowrap">{t.date}</td>
                        <td className="px-4 py-2.5">{t.paymentMode}</td>
                        <td className="px-4 py-2.5">
                          <span className="font-semibold">{t.type}</span>{' '}
                          <span className="text-[var(--p-text-muted)]">
                            {t.invoiceNumber || t.remarks}
                          </span>
                        </td>
                        <td
                          className={`px-4 py-2.5 text-right font-mono-num font-bold ${
                            isIn ? 'text-[var(--p-success)]' : 'text-[var(--p-danger)]'
                          }`}
                        >
                          {isIn ? '+' : '-'}
                          {formatPaise(t.amountPaise)}
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          </div>
        </div>
      )}

      {tab === 'qrs' && (
        <div className="space-y-4">
          {!isMgrAllowed ? (
            <div className="max-w-md mx-auto rounded-2xl border border-[var(--p-card-border)] bg-[var(--p-card)] p-6 space-y-4 text-center">
              <Lock className="w-8 h-8 text-[var(--p-primary)] mx-auto" />
              <h3 className="text-sm font-bold">Manager Banking PIN Required</h3>
              <p className="text-xs text-[var(--p-text-muted)]">
                UPI QR code edits are protected by your Manager Banking PIN (15-min unlock session).
              </p>
              <form
                onSubmit={(e) => {
                  e.preventDefault();
                  if (verifyManagerPin(mgrPinInput)) setMgrPinInput('');
                }}
                className="space-y-3"
              >
                <input
                  type="password"
                  required
                  value={mgrPinInput}
                  onChange={(e) => setMgrPinInput(e.target.value)}
                  placeholder="Enter Manager Banking PIN"
                  className="w-full rounded-xl border border-[var(--p-card-border)] bg-[var(--p-bg)] px-3 py-2 text-sm font-mono-num text-center"
                />
                <button
                  type="submit"
                  className="w-full rounded-xl bg-[var(--p-primary)] py-2 text-xs font-semibold text-[var(--p-primary-text)]"
                >
                  Unlock QR Management
                </button>
              </form>
            </div>
          ) : (
            <>
              <div className="flex items-center justify-between">
                <p className="text-xs text-[var(--p-text-muted)]">
                  Configure counter &amp; invoice merchant UPI QR codes.
                </p>
                <div className="flex items-center gap-2">
                  {state.hasManagerPinSet && (
                    <button
                      type="button"
                      onClick={lockManager}
                      className="rounded-xl border border-[var(--p-card-border)] px-3 py-1.5 text-xs"
                    >
                      Lock Manager
                    </button>
                  )}
                  <button
                    type="button"
                    onClick={() => setQrAddOpen(true)}
                    className="inline-flex items-center gap-1.5 rounded-xl bg-[var(--p-primary)] px-3.5 py-2 text-xs font-semibold text-[var(--p-primary-text)]"
                  >
                    <Plus className="w-3.5 h-3.5" />
                    Add UPI QR
                  </button>
                </div>
              </div>

              <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                {state.qrCodes.map((qr) => {
                  const bank = state.bankAccounts.find((b) => b.id === qr.linkedBankId);
                  return (
                    <div
                      key={qr.id}
                      className="rounded-2xl border border-[var(--p-card-border)] bg-[var(--p-card)] p-4 flex gap-4 items-center"
                    >
                      <QrCodeSvg
                        value={buildUpiPayUri(qr.upiId, state.profile.businessName, 0, qr.label)}
                        size={104}
                        className="shrink-0"
                      />
                      <div className="flex-1 min-w-0 space-y-2">
                        <div className="flex items-center justify-between gap-2">
                          <p className="text-sm font-bold truncate">{qr.label}</p>
                          {qr.isDefault && (
                            <span className="rounded bg-[var(--p-success-container)] px-1.5 py-0.5 text-[10px] font-semibold text-[var(--p-on-success-container)]">
                              Default
                            </span>
                          )}
                        </div>
                        <p className="text-xs font-mono-num text-[var(--p-link)] truncate">
                          {qr.upiId}
                        </p>
                        <p className="text-[11px] text-[var(--p-text-muted)] truncate">
                          Linked: {bank?.bankName || 'Direct'}
                        </p>
                        <div className="flex flex-wrap gap-1.5 pt-1">
                          {!qr.isDefault && (
                            <button
                              type="button"
                              onClick={() => setDefaultQr(qr.id)}
                              className="rounded-lg border border-[var(--p-card-border)] px-2 py-1 text-[11px]"
                            >
                              Set Default
                            </button>
                          )}
                          <button
                            type="button"
                            onClick={() => toggleQrEnabled(qr.id)}
                            className="rounded-lg border border-[var(--p-card-border)] px-2 py-1 text-[11px]"
                          >
                            {qr.isEnabled ? 'Enabled' : 'Disabled'}
                          </button>
                          <button
                            type="button"
                            onClick={() => toggleQrShowOnInvoices(qr.id)}
                            className="rounded-lg border border-[var(--p-card-border)] px-2 py-1 text-[11px]"
                          >
                            {qr.showOnInvoices ? 'On Invoices' : 'Hidden on PDF'}
                          </button>
                          <button
                            type="button"
                            onClick={() => deleteQrCode(qr.id)}
                            className="p-1 rounded-lg text-[var(--p-danger)] hover:bg-[var(--p-danger-container)]"
                          >
                            <Trash2 className="w-3.5 h-3.5" />
                          </button>
                        </div>
                      </div>
                    </div>
                  );
                })}
              </div>
            </>
          )}
        </div>
      )}

      {tab === 'recon' && (
        <div className="space-y-4">
          {!state.isBankReconProUnlocked ? (
            <div className="rounded-2xl border border-[var(--p-card-border)] bg-[var(--p-card)] p-6 text-center space-y-3">
              <FileSpreadsheet className="w-10 h-10 text-[var(--p-primary)] mx-auto" />
              <h3 className="text-base font-bold">Bank Statement Reconciliation Engine</h3>
              <p className="text-xs text-[var(--p-text-muted)] max-w-md mx-auto">
                Auto-match HDFC, SBI, ICICI CSV &amp; OFX bank feeds against ledger vouchers and
                post bank charges / unrecorded UPI receipts in one click.
              </p>
              <button
                type="button"
                onClick={unlockBankReconDemo}
                className="rounded-xl bg-[var(--p-primary)] px-5 py-2.5 text-xs font-semibold text-[var(--p-primary-text)]"
              >
                Unlock Bank Reconciliation Pro (Demo)
              </button>
            </div>
          ) : (
            <>
              {/* Toolbar */}
              <div className="rounded-2xl border border-[var(--p-card-border)] bg-[var(--p-card)] p-4 flex flex-wrap items-center justify-between gap-3">
                <div className="flex flex-wrap items-center gap-2">
                  <select
                    value={reconBankId}
                    onChange={(e) => setReconBankId(e.target.value)}
                    className="rounded-xl border border-[var(--p-card-border)] bg-[var(--p-bg)] px-3 py-2 text-xs"
                  >
                    {state.bankAccounts.map((b) => (
                      <option key={b.id} value={b.id}>
                        {b.bankName} ({b.accountNumber.slice(-4)})
                      </option>
                    ))}
                  </select>

                  <label className="inline-flex items-center gap-1.5 cursor-pointer rounded-xl bg-[var(--p-primary)] px-3.5 py-2 text-xs font-semibold text-[var(--p-primary-text)]">
                    <Upload className="w-3.5 h-3.5" />
                    <span>Upload CSV / OFX</span>
                    <input
                      type="file"
                      accept=".csv,.ofx,.qfx,.txt"
                      onChange={handleFileUpload}
                      className="hidden"
                    />
                  </label>

                  <button
                    type="button"
                    onClick={() => importSampleStatement('hdfc_csv', reconBankId)}
                    className="rounded-xl border border-[var(--p-card-border)] px-3 py-2 text-xs font-medium hover:bg-[var(--p-secondary-bg)]"
                  >
                    Load HDFC CSV Sample
                  </button>
                  <button
                    type="button"
                    onClick={() => importSampleStatement('sbi_ofx', reconBankId)}
                    className="rounded-xl border border-[var(--p-card-border)] px-3 py-2 text-xs font-medium hover:bg-[var(--p-secondary-bg)]"
                  >
                    Load SBI OFX Sample
                  </button>
                </div>

                {state.bankReconItems.length > 0 && (
                  <div className="flex items-center gap-2">
                    <button
                      type="button"
                      onClick={reconcileBankFeed}
                      className="inline-flex items-center gap-1 rounded-xl border border-[var(--p-card-border)] px-3 py-2 text-xs font-medium"
                    >
                      <RefreshCw className="w-3.5 h-3.5" />
                      Re-Match
                    </button>
                    <button
                      type="button"
                      onClick={confirmAllMatches}
                      className="inline-flex items-center gap-1 rounded-xl bg-[var(--p-success)] px-3 py-2 text-xs font-semibold text-white"
                    >
                      <CheckCircle2 className="w-3.5 h-3.5" />
                      Confirm All Matches
                    </button>
                    <button
                      type="button"
                      onClick={clearStatementItems}
                      className="rounded-xl border border-[var(--p-card-border)] px-2.5 py-2 text-xs text-[var(--p-danger)]"
                    >
                      Clear
                    </button>
                  </div>
                )}
              </div>

              {state.bankReconItems.length > 0 && (
                <>
                  <div className="grid grid-cols-2 sm:grid-cols-4 gap-3">
                    <div className="rounded-xl border border-[var(--p-card-border)] bg-[var(--p-card)] p-3">
                      <p className="text-[11px] text-[var(--p-text-muted)]">Statement Lines</p>
                      <p className="text-lg font-bold font-mono-num">{reconSummary.total}</p>
                    </div>
                    <div className="rounded-xl border border-[var(--p-card-border)] bg-[var(--p-card)] p-3">
                      <p className="text-[11px] text-[var(--p-text-muted)]">Matched</p>
                      <p className="text-lg font-bold font-mono-num text-[var(--p-success)]">
                        {reconSummary.matchedCount}
                      </p>
                    </div>
                    <div className="rounded-xl border border-[var(--p-card-border)] bg-[var(--p-card)] p-3">
                      <p className="text-[11px] text-[var(--p-text-muted)]">Probable Match</p>
                      <p className="text-lg font-bold font-mono-num text-[var(--p-warning)]">
                        {reconSummary.reviewCount}
                      </p>
                    </div>
                    <div className="rounded-xl border border-[var(--p-card-border)] bg-[var(--p-card)] p-3">
                      <p className="text-[11px] text-[var(--p-text-muted)]">Unmatched / Discrepancy</p>
                      <p className="text-lg font-bold font-mono-num text-[var(--p-danger)]">
                        {reconSummary.unmatchedCount}
                      </p>
                    </div>
                  </div>

                  <div className="space-y-2.5">
                    {state.bankReconItems.map((item) => {
                      const matchedTxn = item.matchedTxnId
                        ? state.transactions.find((t) => t.id === item.matchedTxnId)
                        : null;
                      return (
                        <div
                          key={item.id}
                          className="rounded-2xl border border-[var(--p-card-border)] bg-[var(--p-card)] p-4 flex flex-col md:flex-row md:items-center justify-between gap-3"
                        >
                          <div className="min-w-0 space-y-1">
                            <div className="flex flex-wrap items-center gap-2">
                              <span className="text-xs font-mono-num text-[var(--p-text-muted)]">
                                {item.date || '—'}
                              </span>
                              <span
                                className={`rounded px-1.5 py-0.5 text-[10px] font-bold ${
                                  item.isCredit
                                    ? 'bg-[var(--p-success-container)] text-[var(--p-on-success-container)]'
                                    : 'bg-[var(--p-danger-container)] text-[var(--p-on-danger-container)]'
                                }`}
                              >
                                {item.isCredit ? 'CREDIT' : 'DEBIT'}
                              </span>
                              <span className="text-sm font-bold font-mono-num">
                                {formatPaise(item.amountPaise)}
                              </span>
                              {item.matchConfidence > 0 && (
                                <span className="rounded bg-[var(--p-info-container)] px-1.5 py-0.5 text-[10px] font-semibold text-[var(--p-on-info-container)]">
                                  {item.matchConfidence}% confidence
                                </span>
                              )}
                            </div>
                            <p className="text-xs font-medium text-[var(--p-text)]">
                              {item.description}
                            </p>
                            {matchedTxn ? (
                              <p className="text-[11px] text-[var(--p-success)]">
                                Linked: {matchedTxn.type} {matchedTxn.invoiceNumber || matchedTxn.id} ·{' '}
                                {item.matchReason}
                              </p>
                            ) : (
                              <p className="text-[11px] text-[var(--p-warning)]">
                                {item.discrepancyNote}
                              </p>
                            )}
                          </div>

                          <div className="flex flex-wrap items-center gap-1.5 shrink-0">
                            {matchedTxn && !item.isUserConfirmed && (
                              <button
                                type="button"
                                onClick={() => confirmMatch(item.id)}
                                className="inline-flex items-center gap-1 rounded-lg bg-[var(--p-success)] px-2.5 py-1.5 text-[11px] font-semibold text-white"
                              >
                                <Check className="w-3 h-3" />
                                Confirm
                              </button>
                            )}
                            {matchedTxn ? (
                              <button
                                type="button"
                                onClick={() => unlinkMatch(item.id)}
                                className="inline-flex items-center gap-1 rounded-lg border border-[var(--p-card-border)] px-2.5 py-1.5 text-[11px]"
                              >
                                <Unlink2 className="w-3 h-3" />
                                Unlink
                              </button>
                            ) : (
                              <>
                                <button
                                  type="button"
                                  onClick={() => setManualModalItem(item)}
                                  className="inline-flex items-center gap-1 rounded-lg border border-[var(--p-card-border)] px-2.5 py-1.5 text-[11px]"
                                >
                                  <Link2 className="w-3 h-3" />
                                  Link Voucher
                                </button>
                                <button
                                  type="button"
                                  onClick={() => {
                                    setResolveModalItem(item);
                                    setResType(item.isCredit ? 'CUSTOMER_PAYMENT' : 'BANK_CHARGES');
                                    setResRemarks(item.description);
                                  }}
                                  className="inline-flex items-center gap-1 rounded-lg bg-[var(--p-primary)] px-2.5 py-1.5 text-[11px] font-semibold text-[var(--p-primary-text)]"
                                >
                                  <Wrench className="w-3 h-3" />
                                  Post to Books
                                </button>
                              </>
                            )}
                          </div>
                        </div>
                      );
                    })}
                  </div>
                </>
              )}
            </>
          )}
        </div>
      )}

      {/* Cash Drawer Adjust Modal */}
      {cashModalOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 p-4">
          <form
            onSubmit={(e) => {
              e.preventDefault();
              adjustCashPaise(parsePaiseFromRupees(newCashStr), cashReason);
              setCashModalOpen(false);
              setCashReason('');
            }}
            className="w-full max-w-sm rounded-2xl border border-[var(--p-card-border)] bg-[var(--p-card)] p-5 space-y-3"
          >
            <div className="flex items-center justify-between">
              <h3 className="text-sm font-bold">Reconcile Cash Drawer</h3>
              <button type="button" onClick={() => setCashModalOpen(false)}>
                <X className="w-4 h-4" />
              </button>
            </div>
            <p className="text-xs text-[var(--p-text-muted)]">
              Current book cash: {formatPaise(cashBal)}. Enter actual counted cash:
            </p>
            <input
              type="number"
              step="0.01"
              required
              value={newCashStr}
              onChange={(e) => setNewCashStr(e.target.value)}
              className="w-full rounded-xl border border-[var(--p-card-border)] bg-[var(--p-bg)] px-3 py-2 text-sm font-mono-num"
            />
            <input
              type="text"
              required
              value={cashReason}
              onChange={(e) => setCashReason(e.target.value)}
              placeholder="Audit reason (e.g. Evening cash count)"
              className="w-full rounded-xl border border-[var(--p-card-border)] bg-[var(--p-bg)] px-3 py-2 text-xs"
            />
            <div className="flex justify-end gap-2">
              <button
                type="button"
                onClick={() => setCashModalOpen(false)}
                className="rounded-lg border border-[var(--p-card-border)] px-3 py-1.5 text-xs"
              >
                Cancel
              </button>
              <button
                type="submit"
                className="rounded-lg bg-[var(--p-primary)] px-4 py-1.5 text-xs font-semibold text-[var(--p-primary-text)]"
              >
                Save Tally
              </button>
            </div>
          </form>
        </div>
      )}

      {/* Contra Modal */}
      {contraOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 p-4">
          <form
            onSubmit={(e) => {
              e.preventDefault();
              transferBetweenAccountsPaise(cFrom, cTo, parsePaiseFromRupees(cAmount), cRemarks);
              setContraOpen(false);
              setCAmount('');
              setCRemarks('');
            }}
            className="w-full max-w-md rounded-2xl border border-[var(--p-card-border)] bg-[var(--p-card)] p-5 space-y-3"
          >
            <div className="flex items-center justify-between">
              <h3 className="text-sm font-bold">Internal Contra Transfer</h3>
              <button type="button" onClick={() => setContraOpen(false)}>
                <X className="w-4 h-4" />
              </button>
            </div>
            <div className="grid grid-cols-2 gap-3">
              <div>
                <label className="block text-xs mb-1">From Account</label>
                <select
                  value={cFrom}
                  onChange={(e) => setCFrom(e.target.value)}
                  className="w-full rounded-xl border border-[var(--p-card-border)] bg-[var(--p-bg)] px-2.5 py-2 text-xs"
                >
                  <option value="CASH">Cash Drawer</option>
                  {state.bankAccounts.map((b) => (
                    <option key={b.id} value={b.id}>
                      {b.bankName}
                    </option>
                  ))}
                </select>
              </div>
              <div>
                <label className="block text-xs mb-1">To Account</label>
                <select
                  value={cTo}
                  onChange={(e) => setCTo(e.target.value)}
                  className="w-full rounded-xl border border-[var(--p-card-border)] bg-[var(--p-bg)] px-2.5 py-2 text-xs"
                >
                  <option value="CASH">Cash Drawer</option>
                  {state.bankAccounts.map((b) => (
                    <option key={b.id} value={b.id}>
                      {b.bankName}
                    </option>
                  ))}
                </select>
              </div>
            </div>
            <input
              type="number"
              step="0.01"
              required
              value={cAmount}
              onChange={(e) => setCAmount(e.target.value)}
              placeholder="Amount ₹"
              className="w-full rounded-xl border border-[var(--p-card-border)] bg-[var(--p-bg)] px-3 py-2 text-sm font-mono-num"
            />
            <input
              type="text"
              value={cRemarks}
              onChange={(e) => setCRemarks(e.target.value)}
              placeholder="e.g. Cash deposited to HDFC A/c"
              className="w-full rounded-xl border border-[var(--p-card-border)] bg-[var(--p-bg)] px-3 py-2 text-xs"
            />
            <div className="flex justify-end gap-2">
              <button
                type="button"
                onClick={() => setContraOpen(false)}
                className="rounded-lg border border-[var(--p-card-border)] px-3 py-1.5 text-xs"
              >
                Cancel
              </button>
              <button
                type="submit"
                disabled={cFrom === cTo}
                className="rounded-lg bg-[var(--p-primary)] px-4 py-1.5 text-xs font-semibold text-[var(--p-primary-text)] disabled:opacity-50"
              >
                Transfer
              </button>
            </div>
          </form>
        </div>
      )}

      {/* Add Bank Modal */}
      {bankModalOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 p-4">
          <form
            onSubmit={(e) => {
              e.preventDefault();
              if (!bBankName.trim() || !bAcctNo.trim()) return;
              addBankAccount({
                accountName: bAcctName.trim() || `${bBankName} Current A/c`,
                bankName: bBankName.trim(),
                accountNumber: bAcctNo.trim(),
                ifscCode: bIfsc.trim().toUpperCase(),
                upiId: bUpi.trim(),
                openingBalancePaise: parsePaiseFromRupees(bOpenBal),
                isDefaultForQr: false,
              });
              setBankModalOpen(false);
              setBBankName('');
              setBAcctNo('');
              setBIfsc('');
              setBUpi('');
            }}
            className="w-full max-w-md rounded-2xl border border-[var(--p-card-border)] bg-[var(--p-card)] p-5 space-y-3"
          >
            <div className="flex items-center justify-between">
              <h3 className="text-sm font-bold">Add Bank Account</h3>
              <button type="button" onClick={() => setBankModalOpen(false)}>
                <X className="w-4 h-4" />
              </button>
            </div>
            <input
              type="text"
              required
              value={bBankName}
              onChange={(e) => setBBankName(e.target.value)}
              placeholder="Bank Name (e.g. ICICI Bank) *"
              className="w-full rounded-xl border border-[var(--p-card-border)] bg-[var(--p-bg)] px-3 py-2 text-xs"
            />
            <input
              type="text"
              value={bAcctName}
              onChange={(e) => setBAcctName(e.target.value)}
              placeholder="Account Holder / Label"
              className="w-full rounded-xl border border-[var(--p-card-border)] bg-[var(--p-bg)] px-3 py-2 text-xs"
            />
            <div className="grid grid-cols-2 gap-2.5">
              <input
                type="text"
                required
                value={bAcctNo}
                onChange={(e) => setBAcctNo(e.target.value)}
                placeholder="Account Number *"
                className="rounded-xl border border-[var(--p-card-border)] bg-[var(--p-bg)] px-3 py-2 text-xs font-mono-num"
              />
              <div>
                <input
                  type="text"
                  maxLength={11}
                  value={bIfsc}
                  onChange={(e) => setBIfsc(e.target.value.toUpperCase())}
                  placeholder="IFSC Code"
                  className="w-full rounded-xl border border-[var(--p-card-border)] bg-[var(--p-bg)] px-3 py-2 text-xs font-mono-num uppercase"
                />
                {bIfsc.trim() && (
                  <p
                    className={`text-[10px] mt-0.5 ${
                      validateIfsc(bIfsc, true).isValid
                        ? 'text-[var(--p-success)]'
                        : 'text-[var(--p-danger)]'
                    }`}
                  >
                    {validateIfsc(bIfsc, true).message}
                  </p>
                )}
              </div>
            </div>
            <div className="grid grid-cols-2 gap-2.5">
              <input
                type="text"
                value={bUpi}
                onChange={(e) => setBUpi(e.target.value)}
                placeholder="UPI VPA (Optional)"
                className="rounded-xl border border-[var(--p-card-border)] bg-[var(--p-bg)] px-3 py-2 text-xs"
              />
              <input
                type="number"
                step="0.01"
                value={bOpenBal}
                onChange={(e) => setBOpenBal(e.target.value)}
                placeholder="Opening Balance ₹"
                className="rounded-xl border border-[var(--p-card-border)] bg-[var(--p-bg)] px-3 py-2 text-xs font-mono-num"
              />
            </div>
            <div className="flex justify-end gap-2">
              <button
                type="button"
                onClick={() => setBankModalOpen(false)}
                className="rounded-lg border border-[var(--p-card-border)] px-3 py-1.5 text-xs"
              >
                Cancel
              </button>
              <button
                type="submit"
                className="rounded-lg bg-[var(--p-primary)] px-4 py-1.5 text-xs font-semibold text-[var(--p-primary-text)]"
              >
                Save Bank
              </button>
            </div>
          </form>
        </div>
      )}

      {/* Add QR Modal */}
      {qrAddOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 p-4">
          <form
            onSubmit={(e) => {
              e.preventDefault();
              if (!qrLabel.trim() || !qrUpiId.trim()) return;
              addQrCode({
                label: qrLabel.trim(),
                upiId: qrUpiId.trim(),
                linkedBankId: qrBankId,
                isDefault: qrDefault,
                isEnabled: true,
                showOnInvoices: true,
                qrImageUri: null,
              });
              setQrAddOpen(false);
              setQrLabel('');
              setQrUpiId('');
            }}
            className="w-full max-w-md rounded-2xl border border-[var(--p-card-border)] bg-[var(--p-card)] p-5 space-y-3"
          >
            <div className="flex items-center justify-between">
              <h3 className="text-sm font-bold">Add Merchant UPI QR</h3>
              <button type="button" onClick={() => setQrAddOpen(false)}>
                <X className="w-4 h-4" />
              </button>
            </div>
            <input
              type="text"
              required
              value={qrLabel}
              onChange={(e) => setQrLabel(e.target.value)}
              placeholder="Counter Label (e.g. Counter 2 PayTM QR)"
              className="w-full rounded-xl border border-[var(--p-card-border)] bg-[var(--p-bg)] px-3 py-2 text-xs"
            />
            <input
              type="text"
              required
              value={qrUpiId}
              onChange={(e) => setQrUpiId(e.target.value)}
              placeholder="Merchant UPI ID (e.g. store@hdfcbank)"
              className="w-full rounded-xl border border-[var(--p-card-border)] bg-[var(--p-bg)] px-3 py-2 text-xs font-mono-num"
            />
            <select
              value={qrBankId}
              onChange={(e) => setQrBankId(e.target.value)}
              className="w-full rounded-xl border border-[var(--p-card-border)] bg-[var(--p-bg)] px-3 py-2 text-xs"
            >
              {state.bankAccounts.map((b) => (
                <option key={b.id} value={b.id}>
                  {b.bankName} ({b.accountNumber.slice(-4)})
                </option>
              ))}
            </select>
            <label className="flex items-center gap-2 text-xs">
              <input
                type="checkbox"
                checked={qrDefault}
                onChange={(e) => setQrDefault(e.target.checked)}
              />
              Set as default QR on printed tax invoices
            </label>
            <div className="flex justify-end gap-2">
              <button
                type="button"
                onClick={() => setQrAddOpen(false)}
                className="rounded-lg border border-[var(--p-card-border)] px-3 py-1.5 text-xs"
              >
                Cancel
              </button>
              <button
                type="submit"
                className="rounded-lg bg-[var(--p-primary)] px-4 py-1.5 text-xs font-semibold text-[var(--p-primary-text)]"
              >
                Save QR
              </button>
            </div>
          </form>
        </div>
      )}

      {/* Manual Link Modal */}
      {manualModalItem && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 p-4">
          <div className="w-full max-w-lg rounded-2xl border border-[var(--p-card-border)] bg-[var(--p-card)] p-5 space-y-3">
            <div className="flex items-center justify-between">
              <h3 className="text-sm font-bold">
                Link Statement Line ({formatPaise(manualModalItem.amountPaise)})
              </h3>
              <button type="button" onClick={() => setManualModalItem(null)}>
                <X className="w-4 h-4" />
              </button>
            </div>
            <div className="max-h-72 overflow-y-auto divide-y divide-[var(--p-card-border)]">
              {state.transactions
                .filter((t) => !t.isVoided && t.type !== 'Quotation')
                .map((t) => (
                  <button
                    key={t.id}
                    type="button"
                    onClick={() => {
                      manualMatch(manualModalItem.id, t.id);
                      setManualModalItem(null);
                    }}
                    className="w-full py-2.5 text-left flex items-center justify-between text-xs hover:bg-[var(--p-bg)] px-2 rounded"
                  >
                    <div>
                      <p className="font-semibold">
                        {t.type} · {t.invoiceNumber || t.id}
                      </p>
                      <p className="text-[11px] text-[var(--p-text-muted)]">
                        {t.date} · {t.remarks}
                      </p>
                    </div>
                    <span className="font-mono-num font-bold">{formatPaise(t.amountPaise)}</span>
                  </button>
                ))}
            </div>
          </div>
        </div>
      )}

      {/* Resolve Discrepancy Modal */}
      {resolveModalItem && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 p-4">
          <form
            onSubmit={(e) => {
              e.preventDefault();
              resolveDiscrepancy(resolveModalItem.id, resType, resPartyId || null, resRemarks);
              setResolveModalItem(null);
            }}
            className="w-full max-w-md rounded-2xl border border-[var(--p-card-border)] bg-[var(--p-card)] p-5 space-y-3"
          >
            <div className="flex items-center justify-between">
              <h3 className="text-sm font-bold">
                Post Balancing Entry ({formatPaise(resolveModalItem.amountPaise)})
              </h3>
              <button type="button" onClick={() => setResolveModalItem(null)}>
                <X className="w-4 h-4" />
              </button>
            </div>
            <p className="text-xs text-[var(--p-text-muted)]">{resolveModalItem.description}</p>
            <select
              value={resType}
              onChange={(e) => setResType(e.target.value as DiscrepancyResolutionType)}
              className="w-full rounded-xl border border-[var(--p-card-border)] bg-[var(--p-bg)] px-3 py-2 text-xs"
            >
              <option value="BANK_CHARGES">Bank SMS / Service Charges (18% GST Split)</option>
              <option value="DIRECT_EXPENSE">Direct / Operating Expense</option>
              <option value="CUSTOMER_PAYMENT">Customer Payment Receipt (PaymentIn)</option>
              <option value="SUPPLIER_PAYMENT">Supplier Payment Payout (PaymentOut)</option>
              <option value="INTEREST_INCOME">Bank Interest Income</option>
            </select>
            {(resType === 'CUSTOMER_PAYMENT' || resType === 'SUPPLIER_PAYMENT') && (
              <select
                value={resPartyId}
                onChange={(e) => setResPartyId(e.target.value)}
                className="w-full rounded-xl border border-[var(--p-card-border)] bg-[var(--p-bg)] px-3 py-2 text-xs"
              >
                <option value="">-- Select Party --</option>
                {state.parties.map((p) => (
                  <option key={p.id} value={p.id}>
                    {p.name}
                  </option>
                ))}
              </select>
            )}
            <input
              type="text"
              value={resRemarks}
              onChange={(e) => setResRemarks(e.target.value)}
              placeholder="Voucher remarks"
              className="w-full rounded-xl border border-[var(--p-card-border)] bg-[var(--p-bg)] px-3 py-2 text-xs"
            />
            <div className="flex justify-end gap-2">
              <button
                type="button"
                onClick={() => setResolveModalItem(null)}
                className="rounded-lg border border-[var(--p-card-border)] px-3 py-1.5 text-xs"
              >
                Cancel
              </button>
              <button
                type="submit"
                className="rounded-lg bg-[var(--p-primary)] px-4 py-1.5 text-xs font-semibold text-[var(--p-primary-text)]"
              >
                Create &amp; Reconcile
              </button>
            </div>
          </form>
        </div>
      )}
    </div>
  );
};
