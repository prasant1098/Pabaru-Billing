import React, { useMemo, useState } from 'react';
import { RefreshCw, Search, ShieldAlert, ShieldCheck, X } from 'lucide-react';
import { GENESIS_HASH } from '../engines/cryptoEngine';
import { usePabaru } from '../state/PabaruContext';

interface AuditShieldModalProps {
  open: boolean;
  onClose: () => void;
}

export const AuditShieldModal: React.FC<AuditShieldModalProps> = ({ open, onClose }) => {
  const { state, verifyAuditChainNow } = usePabaru();
  const [query, setQuery] = useState('');

  const filteredLogs = useMemo(() => {
    const q = query.trim().toLowerCase();
    const list = state.auditLogs.slice().reverse();
    if (!q) return list;
    return list.filter(
      (l) =>
        l.action.toLowerCase().includes(q) ||
        l.entityType.toLowerCase().includes(q) ||
        l.details.toLowerCase().includes(q) ||
        l.actor.toLowerCase().includes(q)
    );
  }, [state.auditLogs, query]);

  if (!open) return null;

  const latestHash =
    state.auditLogs.length > 0
      ? state.auditLogs[state.auditLogs.length - 1].recordHash
      : GENESIS_HASH;

  return (
    <div className="no-print fixed inset-0 z-50 flex items-center justify-center bg-black/60 p-4">
      <div className="flex flex-col w-full max-w-3xl max-h-[86vh] rounded-2xl border border-[var(--p-card-border)] bg-[var(--p-card)] shadow-2xl overflow-hidden">
        <div className="flex items-center justify-between px-4 py-3 border-b border-[var(--p-card-border)] bg-[var(--p-secondary-bg)]">
          <div className="flex items-center gap-2">
            {state.isChainValid ? (
              <ShieldCheck className="w-4 h-4 text-[var(--p-success)]" />
            ) : (
              <ShieldAlert className="w-4 h-4 text-[var(--p-danger)]" />
            )}
            <h3 className="text-sm font-semibold text-[var(--p-text)]">
              Tamper-Evident SHA-256 Audit Ledger
            </h3>
          </div>
          <div className="flex items-center gap-2">
            <button
              type="button"
              onClick={verifyAuditChainNow}
              className="inline-flex items-center gap-1.5 whitespace-nowrap rounded-lg bg-[var(--p-primary)] px-3 py-1.5 text-xs font-semibold text-[var(--p-primary-text)] hover:opacity-90"
            >
              <RefreshCw className="w-3.5 h-3.5" />
              Verify Chain Now
            </button>
            <button
              type="button"
              onClick={onClose}
              className="p-1.5 rounded-lg text-[var(--p-text-muted)] hover:bg-[var(--p-card)]"
            >
              <X className="w-4 h-4" />
            </button>
          </div>
        </div>

        <div className="p-4 space-y-3 overflow-y-auto">
          <div className="grid grid-cols-1 md:grid-cols-3 gap-3">
            <div className="rounded-xl border border-[var(--p-card-border)] bg-[var(--p-bg)] p-3">
              <p className="text-[11px] uppercase tracking-wider text-[var(--p-text-muted)]">
                Chain Status
              </p>
              <p
                className={`text-sm font-bold mt-0.5 ${
                  state.isChainValid ? 'text-[var(--p-success)]' : 'text-[var(--p-danger)]'
                }`}
              >
                {state.isChainValid ? 'Intact & Verified' : 'Integrity Warning'}
              </p>
            </div>
            <div className="rounded-xl border border-[var(--p-card-border)] bg-[var(--p-bg)] p-3">
              <p className="text-[11px] uppercase tracking-wider text-[var(--p-text-muted)]">
                Sealed Blocks
              </p>
              <p className="text-sm font-bold font-mono-num text-[var(--p-text)] mt-0.5">
                {state.auditLogs.length} Entries
              </p>
            </div>
            <div className="rounded-xl border border-[var(--p-card-border)] bg-[var(--p-bg)] p-3">
              <p className="text-[11px] uppercase tracking-wider text-[var(--p-text-muted)]">
                Latest SHA-256 Digest
              </p>
              <p className="text-xs font-mono-num text-[var(--p-text)] truncate mt-1">
                {latestHash}
              </p>
            </div>
          </div>

          <div className="relative">
            <Search className="w-4 h-4 text-[var(--p-text-muted)] absolute left-3 top-2.5" />
            <input
              type="text"
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              placeholder="Search audit events by action, actor, or details..."
              className="w-full rounded-xl border border-[var(--p-card-border)] bg-[var(--p-bg)] pl-9 pr-3 py-2 text-xs text-[var(--p-text)] focus:outline-none focus:ring-1 focus:ring-[var(--p-primary)]"
            />
          </div>

          <div className="overflow-x-auto rounded-xl border border-[var(--p-card-border)]">
            <table className="w-full text-left text-xs">
              <thead className="bg-[var(--p-secondary-bg)] text-[var(--p-text-muted)] uppercase text-[10px]">
                <tr>
                  <th className="px-3 py-2">Timestamp</th>
                  <th className="px-3 py-2">Action</th>
                  <th className="px-3 py-2">Details &amp; Reason</th>
                  <th className="px-3 py-2">Actor</th>
                  <th className="px-3 py-2">Record Hash</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-[var(--p-card-border)]">
                {filteredLogs.map((log) => (
                  <tr key={log.id} className="hover:bg-[var(--p-bg)]">
                    <td className="px-3 py-2 whitespace-nowrap font-mono-num text-[11px] text-[var(--p-text-muted)]">
                      {log.timestamp.slice(0, 19).replace('T', ' ')}
                    </td>
                    <td className="px-3 py-2 font-semibold text-[var(--p-text)]">
                      {log.entityType} · {log.action}
                    </td>
                    <td className="px-3 py-2 text-[var(--p-text)]">
                      <div>{log.details}</div>
                      {log.reason && (
                        <div className="text-[11px] text-[var(--p-text-muted)]">{log.reason}</div>
                      )}
                    </td>
                    <td className="px-3 py-2 whitespace-nowrap text-[var(--p-text-muted)]">
                      {log.actor}
                    </td>
                    <td className="px-3 py-2 font-mono-num text-[11px] text-[var(--p-text-muted)]">
                      {log.recordHash.slice(0, 12)}...
                    </td>
                  </tr>
                ))}
                {filteredLogs.length === 0 && (
                  <tr>
                    <td colSpan={5} className="px-3 py-6 text-center text-[var(--p-text-muted)]">
                      No matching audit records.
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>
        </div>
      </div>
    </div>
  );
};
