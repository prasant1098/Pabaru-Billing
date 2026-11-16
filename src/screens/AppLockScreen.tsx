import React, { useState } from 'react';
import { KeyRound, Lock, ShieldCheck } from 'lucide-react';
import { usePabaru } from '../state/PabaruContext';

export const AppLockScreen: React.FC = () => {
  const { state, verifySecurityPin, unlockWithRecoveryWords } = usePabaru();
  const [pin, setPin] = useState('');
  const [useRecovery, setUseRecovery] = useState(false);
  const [recoveryInput, setRecoveryInput] = useState('');

  const handlePinSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (verifySecurityPin(pin)) {
      setPin('');
    }
  };

  const handleRecoverySubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (unlockWithRecoveryWords(recoveryInput)) {
      setRecoveryInput('');
    }
  };

  return (
    <div className="min-h-screen bg-[var(--p-bg)] text-[var(--p-text)] flex items-center justify-center p-4">
      <div className="w-full max-w-md rounded-2xl border border-[var(--p-card-border)] bg-[var(--p-card)] p-6 shadow-xl space-y-5">
        <div className="flex items-center gap-3">
          <div className="w-11 h-11 rounded-xl bg-[var(--p-primary)] text-[var(--p-primary-text)] flex items-center justify-center shrink-0">
            <Lock className="w-5 h-5" />
          </div>
          <div>
            <h2 className="text-base font-bold text-[var(--p-text)]">Workspace Locked</h2>
            <p className="text-xs text-[var(--p-text-muted)]">
              {state.profile.businessName} · {state.profile.storeCode}
            </p>
          </div>
        </div>

        {!useRecovery ? (
          <form onSubmit={handlePinSubmit} className="space-y-4">
            <div>
              <label className="block text-xs font-medium text-[var(--p-text)] mb-1">
                Enter Owner Security PIN
              </label>
              <input
                type="password"
                required
                autoFocus
                value={pin}
                onChange={(e) => setPin(e.target.value)}
                placeholder="Enter PIN (default: 1234)"
                className="w-full rounded-xl border border-[var(--p-card-border)] bg-[var(--p-bg)] px-3.5 py-2.5 text-sm font-mono-num text-[var(--p-text)]"
              />
              {state.pinFailedAttempts > 0 && (
                <p className="text-[11px] text-[var(--p-danger)] mt-1">
                  Failed attempts: {state.pinFailedAttempts} / 5
                </p>
              )}
            </div>

            <button
              type="submit"
              className="w-full inline-flex items-center justify-center gap-2 rounded-xl bg-[var(--p-primary)] px-4 py-2.5 text-xs font-semibold text-[var(--p-primary-text)] hover:opacity-90"
            >
              <ShieldCheck className="w-4 h-4" />
              Unlock Workspace
            </button>

            <button
              type="button"
              onClick={() => setUseRecovery(true)}
              className="w-full text-center text-xs font-medium text-[var(--p-link)] hover:underline"
            >
              Forgot PIN? Unlock with 12-Word Recovery Phrase
            </button>
          </form>
        ) : (
          <form onSubmit={handleRecoverySubmit} className="space-y-4">
            <div>
              <label className="block text-xs font-medium text-[var(--p-text)] mb-1">
                Enter 12 Recovery Words (space-separated)
              </label>
              <textarea
                rows={3}
                required
                value={recoveryInput}
                onChange={(e) => setRecoveryInput(e.target.value)}
                placeholder="word1 word2 word3 ... word12"
                className="w-full rounded-xl border border-[var(--p-card-border)] bg-[var(--p-bg)] p-3 text-xs font-mono-num text-[var(--p-text)]"
              />
            </div>

            <div className="flex gap-2">
              <button
                type="button"
                onClick={() => setUseRecovery(false)}
                className="flex-1 rounded-xl border border-[var(--p-card-border)] px-3 py-2 text-xs font-medium text-[var(--p-text)]"
              >
                Back to PIN
              </button>
              <button
                type="submit"
                className="flex-1 inline-flex items-center justify-center gap-1.5 rounded-xl bg-[var(--p-primary)] px-3 py-2 text-xs font-semibold text-[var(--p-primary-text)]"
              >
                <KeyRound className="w-3.5 h-3.5" />
                Verify Words
              </button>
            </div>
          </form>
        )}
      </div>
    </div>
  );
};
