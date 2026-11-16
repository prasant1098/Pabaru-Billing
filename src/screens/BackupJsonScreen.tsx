import React, { useRef, useState } from 'react';
import {
  CheckCircle2,
  DatabaseBackup,
  Download,
  FileJson,
  KeyRound,
  Lock,
  ShieldAlert,
  Upload,
} from 'lucide-react';
import { downloadFile } from '../engines/exportEngine';
import { usePabaru } from '../state/PabaruContext';

export const BackupJsonScreen: React.FC = () => {
  const { state, exportBackupJson, importBackupJson, addToast } = usePabaru();
  const [exportPassphrase, setExportPassphrase] = useState('');
  const [restorePassphrase, setRestorePassphrase] = useState('');
  const [pendingBackupText, setPendingBackupText] = useState('');
  const [needsPassphrase, setNeedsPassphrase] = useState(false);
  const [statusMessage, setStatusMessage] = useState<string | null>(null);
  const [statusOk, setStatusOk] = useState<boolean>(true);
  const [busy, setBusy] = useState(false);
  const fileRef = useRef<HTMLInputElement>(null);

  const handleExportPlain = async () => {
    setBusy(true);
    try {
      const json = await exportBackupJson();
      const stamp = new Date().toISOString().slice(0, 10);
      downloadFile(
        `pabaru-backup-${state.profile.storeCode.toLowerCase()}-${stamp}.json`,
        json,
        'application/json'
      );
      addToast('Plain JSON backup downloaded', 'success');
    } finally {
      setBusy(false);
    }
  };

  const handleExportEncrypted = async (e: React.FormEvent) => {
    e.preventDefault();
    if (exportPassphrase.trim().length < 6) {
      addToast('Enter at least a 6-character encryption passphrase', 'error');
      return;
    }
    setBusy(true);
    try {
      const encrypted = await exportBackupJson(exportPassphrase.trim());
      const stamp = new Date().toISOString().slice(0, 10);
      downloadFile(
        `pabaru-encrypted-${state.profile.storeCode.toLowerCase()}-${stamp}.json`,
        encrypted,
        'application/json'
      );
      addToast('AES-256-GCM encrypted backup downloaded', 'success');
      setExportPassphrase('');
    } finally {
      setBusy(false);
    }
  };

  const attemptRestore = async (rawText: string, passphrase?: string) => {
    setBusy(true);
    try {
      const res = await importBackupJson(rawText, passphrase);
      if (res.needsPassphrase) {
        setPendingBackupText(rawText);
        setNeedsPassphrase(true);
        setStatusOk(false);
        setStatusMessage(res.message);
        return;
      }
      setStatusOk(res.ok);
      setStatusMessage(res.message);
      if (res.ok) {
        setNeedsPassphrase(false);
        setPendingBackupText('');
        setRestorePassphrase('');
      }
    } finally {
      setBusy(false);
    }
  };

  const handleFilePick = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;
    const text = await file.text();
    await attemptRestore(text, restorePassphrase);
    e.target.value = '';
  };

  return (
    <div className="space-y-5">
      <div>
        <h2 className="text-lg font-bold text-[var(--p-text)]">
          Offline JSON Backup &amp; AES-256-GCM Vault
        </h2>
        <p className="text-xs text-[var(--p-text-muted)]">
          Export full store state with PBKDF2-HMAC-SHA256 (100,000 iterations) + AES-256-GCM
          encryption, or restore from an archived backup file.
        </p>
      </div>

      {/* Snapshot Summary */}
      <div className="grid grid-cols-2 sm:grid-cols-4 gap-3">
        {[
          { label: 'Transactions', val: state.transactions.length },
          { label: 'Parties / Khata', val: state.parties.length },
          { label: 'Catalog SKUs', val: state.products.length },
          { label: 'Audit Chain Blocks', val: state.auditLogs.length },
        ].map((stat) => (
          <div
            key={stat.label}
            className="rounded-2xl border border-[var(--p-card-border)] bg-[var(--p-card)] p-4"
          >
            <p className="text-[11px] font-medium text-[var(--p-text-muted)]">{stat.label}</p>
            <p className="mt-1 text-xl font-bold font-mono-num text-[var(--p-text)]">{stat.val}</p>
          </div>
        ))}
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
        {/* Export Panel */}
        <div className="rounded-2xl border border-[var(--p-card-border)] bg-[var(--p-card)] p-5 space-y-4">
          <div className="flex items-center gap-2.5">
            <div className="w-9 h-9 rounded-xl bg-[var(--p-secondary-bg)] text-[var(--p-primary)] flex items-center justify-center">
              <DatabaseBackup className="w-4 h-4" />
            </div>
            <div>
              <h3 className="text-sm font-bold text-[var(--p-text)]">Export Workspace Backup</h3>
              <p className="text-[11px] text-[var(--p-text-muted)]">
                Includes vouchers, stock batches, bank recon, QR logs, and audit hash chain
              </p>
            </div>
          </div>

          <form onSubmit={handleExportEncrypted} className="space-y-3 rounded-xl border border-[var(--p-card-border)] bg-[var(--p-bg)] p-4">
            <div className="flex items-center gap-2 text-xs font-semibold text-[var(--p-text)]">
              <Lock className="w-3.5 h-3.5 text-[var(--p-primary)]" />
              <span>Recommended: AES-256-GCM Encrypted Export</span>
            </div>
            <input
              type="password"
              value={exportPassphrase}
              onChange={(e) => setExportPassphrase(e.target.value)}
              placeholder="Enter strong passphrase (min 6 characters)"
              className="w-full rounded-xl border border-[var(--p-card-border)] bg-[var(--p-card)] px-3 py-2 text-xs text-[var(--p-text)]"
            />
            <button
              type="submit"
              disabled={busy}
              className="w-full inline-flex items-center justify-center gap-1.5 rounded-xl bg-[var(--p-primary)] px-4 py-2 text-xs font-semibold text-[var(--p-primary-text)] hover:opacity-90 disabled:opacity-50"
            >
              <Download className="w-3.5 h-3.5" />
              Download Encrypted Vault (.json)
            </button>
          </form>

          <button
            type="button"
            disabled={busy}
            onClick={handleExportPlain}
            className="w-full inline-flex items-center justify-center gap-1.5 rounded-xl border border-[var(--p-card-border)] bg-[var(--p-bg)] px-4 py-2.5 text-xs font-semibold text-[var(--p-text)] hover:bg-[var(--p-secondary-bg)] disabled:opacity-50"
          >
            <FileJson className="w-3.5 h-3.5" />
            Download Unencrypted Plain JSON
          </button>
        </div>

        {/* Restore Panel */}
        <div className="rounded-2xl border border-[var(--p-card-border)] bg-[var(--p-card)] p-5 space-y-4">
          <div className="flex items-center gap-2.5">
            <div className="w-9 h-9 rounded-xl bg-[var(--p-secondary-bg)] text-[var(--p-primary)] flex items-center justify-center">
              <Upload className="w-4 h-4" />
            </div>
            <div>
              <h3 className="text-sm font-bold text-[var(--p-text)]">Restore From Backup File</h3>
              <p className="text-[11px] text-[var(--p-text-muted)]">
                Automatically detects plain vs AES-256-GCM vault and verifies SHA-256 audit chain
              </p>
            </div>
          </div>

          <input
            ref={fileRef}
            type="file"
            accept=".json,application/json"
            onChange={handleFilePick}
            className="hidden"
          />

          <button
            type="button"
            disabled={busy}
            onClick={() => fileRef.current?.click()}
            className="w-full rounded-xl border-2 border-dashed border-[var(--p-card-border)] bg-[var(--p-bg)] p-6 text-center hover:border-[var(--p-primary)] transition"
          >
            <Upload className="w-6 h-6 mx-auto text-[var(--p-primary)] mb-2" />
            <p className="text-xs font-semibold text-[var(--p-text)]">
              Click to choose a Pabaru `.json` backup file
            </p>
            <p className="text-[11px] text-[var(--p-text-muted)] mt-0.5">
              Schema: pabaru-billing-backup-v2 or pabaru-encrypted-backup-v1
            </p>
          </button>

          {needsPassphrase && (
            <div className="rounded-xl border border-[var(--p-warning)] bg-[var(--p-warning-container)] p-4 space-y-2.5">
              <div className="flex items-center gap-2 text-xs font-semibold text-[var(--p-on-warning-container)]">
                <KeyRound className="w-4 h-4" />
                <span>Passphrase Required to Decrypt Vault</span>
              </div>
              <input
                type="password"
                value={restorePassphrase}
                onChange={(e) => setRestorePassphrase(e.target.value)}
                placeholder="Enter backup decryption passphrase"
                className="w-full rounded-xl border border-[var(--p-card-border)] bg-[var(--p-card)] px-3 py-2 text-xs text-[var(--p-text)]"
              />
              <button
                type="button"
                disabled={busy}
                onClick={() => attemptRestore(pendingBackupText, restorePassphrase)}
                className="w-full rounded-xl bg-[var(--p-primary)] px-3 py-2 text-xs font-semibold text-[var(--p-primary-text)]"
              >
                Decrypt &amp; Restore Workspace
              </button>
            </div>
          )}

          {statusMessage && (
            <div
              className={`rounded-xl border p-3 flex items-start gap-2 text-xs ${
                statusOk
                  ? 'border-[var(--p-success)] bg-[var(--p-success-container)] text-[var(--p-on-success-container)]'
                  : 'border-[var(--p-danger)] bg-[var(--p-danger-container)] text-[var(--p-on-danger-container)]'
              }`}
            >
              {statusOk ? (
                <CheckCircle2 className="w-4 h-4 shrink-0 mt-0.5" />
              ) : (
                <ShieldAlert className="w-4 h-4 shrink-0 mt-0.5" />
              )}
              <span>{statusMessage}</span>
            </div>
          )}
        </div>
      </div>
    </div>
  );
};
