import React, { useState } from 'react';
import {
  CheckCircle2,
  Copy,
  Eye,
  EyeOff,
  KeyRound,
  Landmark,
  Lock,
  ShieldAlert,
  ShieldCheck,
} from 'lucide-react';
import { usePabaru } from '../state/PabaruContext';

export const SecuritySettingsScreen: React.FC = () => {
  const {
    state,
    setSecurityPin,
    lockApp,
    setManagerPin,
    lockManager,
    verifyAuditChainNow,
    addToast,
  } = usePabaru();

  const [newAppPin, setNewAppPin] = useState('');
  const [confirmAppPin, setConfirmAppPin] = useState('');
  const [newMgrPin, setNewMgrPin] = useState('');
  const [confirmMgrPin, setConfirmMgrPin] = useState('');
  const [showRecovery, setShowRecovery] = useState(false);

  const handleSaveAppPin = (e: React.FormEvent) => {
    e.preventDefault();
    if (!/^\d{4,8}$/.test(newAppPin.trim())) {
      addToast('App Lock PIN must be 4 to 8 digits', 'error');
      return;
    }
    if (newAppPin.trim() !== confirmAppPin.trim()) {
      addToast('App Lock PIN entries do not match', 'error');
      return;
    }
    setSecurityPin(newAppPin.trim());
    setNewAppPin('');
    setConfirmAppPin('');
  };

  const handleSaveManagerPin = (e: React.FormEvent) => {
    e.preventDefault();
    if (!/^\d{4,8}$/.test(newMgrPin.trim())) {
      addToast('Manager Banking PIN must be 4 to 8 digits', 'error');
      return;
    }
    if (newMgrPin.trim() !== confirmMgrPin.trim()) {
      addToast('Manager Banking PIN entries do not match', 'error');
      return;
    }
    setManagerPin(newMgrPin.trim());
    setNewMgrPin('');
    setConfirmMgrPin('');
  };

  const handleCopyWords = async () => {
    try {
      await navigator.clipboard.writeText(state.recoveryWords.join(' '));
      addToast('12-word recovery phrase copied to clipboard', 'success');
    } catch {
      addToast('Copy blocked by browser; note words manually', 'warning');
    }
  };

  return (
    <div className="space-y-5">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h2 className="text-lg font-bold text-[var(--p-text)]">PIN Lock, Recovery &amp; Audit Integrity</h2>
          <p className="text-xs text-[var(--p-text-muted)]">
            Zero-knowledge salted SHA-256 PIN hashing, 12-word recovery key, Manager Banking vault,
            and genesis-linked transaction chain verification.
          </p>
        </div>

        <button
          type="button"
          onClick={lockApp}
          className="inline-flex items-center gap-1.5 rounded-xl bg-[var(--p-primary)] px-4 py-2 text-xs font-semibold text-[var(--p-primary-text)]"
        >
          <Lock className="w-3.5 h-3.5" />
          Lock Workspace Now
        </button>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
        {/* Owner App Lock PIN */}
        <form
          onSubmit={handleSaveAppPin}
          className="rounded-2xl border border-[var(--p-card-border)] bg-[var(--p-card)] p-5 space-y-3"
        >
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-2">
              <KeyRound className="w-4 h-4 text-[var(--p-primary)]" />
              <h3 className="text-sm font-bold text-[var(--p-text)]">Owner App Lock PIN</h3>
            </div>
            <span
              className={`rounded px-2 py-0.5 text-[10px] font-semibold ${
                state.hasPinSet
                  ? 'bg-[var(--p-success-container)] text-[var(--p-on-success-container)]'
                  : 'bg-[var(--p-secondary-bg)] text-[var(--p-text-muted)]'
              }`}
            >
              {state.hasPinSet ? 'Custom PIN Active' : 'Default Demo PIN (1234)'}
            </span>
          </div>

          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="block text-xs font-semibold text-[var(--p-text)] mb-1">
                New 4–8 Digit PIN
              </label>
              <input
                type="password"
                inputMode="numeric"
                value={newAppPin}
                onChange={(e) => setNewAppPin(e.target.value)}
                placeholder="••••"
                className="w-full rounded-xl border border-[var(--p-card-border)] bg-[var(--p-bg)] px-3 py-2 text-xs font-mono-num text-[var(--p-text)]"
              />
            </div>
            <div>
              <label className="block text-xs font-semibold text-[var(--p-text)] mb-1">
                Confirm PIN
              </label>
              <input
                type="password"
                inputMode="numeric"
                value={confirmAppPin}
                onChange={(e) => setConfirmAppPin(e.target.value)}
                placeholder="••••"
                className="w-full rounded-xl border border-[var(--p-card-border)] bg-[var(--p-bg)] px-3 py-2 text-xs font-mono-num text-[var(--p-text)]"
              />
            </div>
          </div>

          <button
            type="submit"
            className="rounded-xl bg-[var(--p-primary)] px-4 py-2 text-xs font-semibold text-[var(--p-primary-text)]"
          >
            Update App Lock PIN
          </button>
        </form>

        {/* Manager Banking PIN */}
        <form
          onSubmit={handleSaveManagerPin}
          className="rounded-2xl border border-[var(--p-card-border)] bg-[var(--p-card)] p-5 space-y-3"
        >
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-2">
              <Landmark className="w-4 h-4 text-[var(--p-primary)]" />
              <h3 className="text-sm font-bold text-[var(--p-text)]">Manager Banking &amp; QR PIN</h3>
            </div>
            <button
              type="button"
              onClick={lockManager}
              className="text-[11px] font-semibold text-[var(--p-primary)] hover:underline"
            >
              Lock Vault Session
            </button>
          </div>
          <p className="text-[11px] text-[var(--p-text-muted)]">
            Protects Cash Adjustments, Inter-Account Transfers, and UPI QR additions/edits. Default
            demo PIN is <span className="font-mono-num font-semibold">1234</span>.
          </p>

          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="block text-xs font-semibold text-[var(--p-text)] mb-1">
                New Manager PIN
              </label>
              <input
                type="password"
                inputMode="numeric"
                value={newMgrPin}
                onChange={(e) => setNewMgrPin(e.target.value)}
                placeholder="••••"
                className="w-full rounded-xl border border-[var(--p-card-border)] bg-[var(--p-bg)] px-3 py-2 text-xs font-mono-num text-[var(--p-text)]"
              />
            </div>
            <div>
              <label className="block text-xs font-semibold text-[var(--p-text)] mb-1">
                Confirm Manager PIN
              </label>
              <input
                type="password"
                inputMode="numeric"
                value={confirmMgrPin}
                onChange={(e) => setConfirmMgrPin(e.target.value)}
                placeholder="••••"
                className="w-full rounded-xl border border-[var(--p-card-border)] bg-[var(--p-bg)] px-3 py-2 text-xs font-mono-num text-[var(--p-text)]"
              />
            </div>
          </div>

          <button
            type="submit"
            className="rounded-xl bg-[var(--p-primary)] px-4 py-2 text-xs font-semibold text-[var(--p-primary-text)]"
          >
            Update Manager Banking PIN
          </button>
        </form>
      </div>

      {/* 12-Word Recovery Phrase */}
      <div className="rounded-2xl border border-[var(--p-card-border)] bg-[var(--p-card)] p-5 space-y-3">
        <div className="flex flex-wrap items-center justify-between gap-2">
          <div>
            <h3 className="text-sm font-bold text-[var(--p-text)]">
              12-Word Offline PIN Recovery Phrase
            </h3>
            <p className="text-[11px] text-[var(--p-text-muted)]">
              Store these words safely offline to unlock your store if you ever forget your PIN.
            </p>
          </div>
          <div className="flex items-center gap-2">
            <button
              type="button"
              onClick={() => setShowRecovery((v) => !v)}
              className="inline-flex items-center gap-1.5 rounded-xl border border-[var(--p-card-border)] px-3 py-1.5 text-xs font-semibold text-[var(--p-text)] hover:bg-[var(--p-secondary-bg)]"
            >
              {showRecovery ? <EyeOff className="w-3.5 h-3.5" /> : <Eye className="w-3.5 h-3.5" />}
              {showRecovery ? 'Hide Words' : 'Reveal Words'}
            </button>
            {showRecovery && (
              <button
                type="button"
                onClick={handleCopyWords}
                className="inline-flex items-center gap-1.5 rounded-xl border border-[var(--p-card-border)] px-3 py-1.5 text-xs font-semibold text-[var(--p-primary)] hover:bg-[var(--p-secondary-bg)]"
              >
                <Copy className="w-3.5 h-3.5" />
                Copy All
              </button>
            )}
          </div>
        </div>

        {showRecovery ? (
          <div className="grid grid-cols-3 sm:grid-cols-4 md:grid-cols-6 gap-2">
            {state.recoveryWords.map((w, idx) => (
              <div
                key={`${idx}-${w}`}
                className="rounded-xl border border-[var(--p-card-border)] bg-[var(--p-bg)] px-2.5 py-2 text-xs font-mono-num"
              >
                <span className="text-[var(--p-text-muted)] mr-1.5">{idx + 1}.</span>
                <span className="font-semibold text-[var(--p-text)]">{w}</span>
              </div>
            ))}
          </div>
        ) : (
          <div className="rounded-xl border border-dashed border-[var(--p-card-border)] bg-[var(--p-bg)] px-4 py-3 text-xs text-[var(--p-text-muted)]">
            •••• •••• •••• •••• •••• •••• •••• •••• •••• •••• •••• •••• (Hidden)
          </div>
        )}
      </div>

      {/* Audit Chain Verifier */}
      <div className="rounded-2xl border border-[var(--p-card-border)] bg-[var(--p-card)] p-5 space-y-3">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <div className="flex items-center gap-2.5">
            {state.isChainValid ? (
              <ShieldCheck className="w-5 h-5 text-[var(--p-success)]" />
            ) : (
              <ShieldAlert className="w-5 h-5 text-[var(--p-danger)]" />
            )}
            <div>
              <h3 className="text-sm font-bold text-[var(--p-text)]">
                Cryptographic SHA-256 Audit Chain
              </h3>
              <p className="text-[11px] text-[var(--p-text-muted)]">
                {state.auditLogs.length} sequential hash-linked events ·{' '}
                {state.isChainValid
                  ? 'All blocks verified against GENESIS_BLOCK'
                  : `Broken at ${state.chainBrokenAtLogId}`}
              </p>
            </div>
          </div>

          <button
            type="button"
            onClick={() => verifyAuditChainNow()}
            className="inline-flex items-center gap-1.5 rounded-xl bg-[var(--p-primary)] px-4 py-2 text-xs font-semibold text-[var(--p-primary-text)]"
          >
            <CheckCircle2 className="w-3.5 h-3.5" />
            Re-Verify Chain Now
          </button>
        </div>

        <div className="max-h-56 overflow-y-auto rounded-xl border border-[var(--p-card-border)] divide-y divide-[var(--p-card-border)]">
          {[...state.auditLogs].reverse().slice(0, 15).map((log) => (
            <div key={log.id} className="px-3 py-2 flex flex-wrap items-center justify-between gap-2 text-xs">
              <div>
                <span className="font-semibold text-[var(--p-text)]">{log.action}</span>{' '}
                <span className="text-[var(--p-text-muted)]">· {log.details}</span>
              </div>
              <span className="font-mono-num text-[10px] text-[var(--p-text-muted)]">
                #{log.recordHash.slice(0, 12)}…
              </span>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
};
