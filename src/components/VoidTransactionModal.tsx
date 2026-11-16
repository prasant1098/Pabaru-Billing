import React, { useState } from 'react';
import { AlertTriangle, X } from 'lucide-react';
import { formatPaise } from '../engines/financialEngine';
import { usePabaru } from '../state/PabaruContext';
import { Transaction } from '../types/models';

interface VoidTransactionModalProps {
  txn: Transaction | null;
  onClose: () => void;
}

export const VoidTransactionModal: React.FC<VoidTransactionModalProps> = ({ txn, onClose }) => {
  const { voidTransaction } = usePabaru();
  const [reason, setReason] = useState('');

  if (!txn) return null;

  const handleConfirm = (e: React.FormEvent) => {
    e.preventDefault();
    if (voidTransaction(txn.id, reason)) {
      setReason('');
      onClose();
    }
  };

  return (
    <div className="no-print fixed inset-0 z-50 flex items-center justify-center bg-black/60 p-4">
      <form
        onSubmit={handleConfirm}
        className="w-full max-w-md rounded-2xl border border-[var(--p-card-border)] bg-[var(--p-card)] shadow-2xl overflow-hidden"
      >
        <div className="flex items-center justify-between px-4 py-3 border-b border-[var(--p-card-border)] bg-[var(--p-danger-container)]">
          <div className="flex items-center gap-2 text-[var(--p-on-danger-container)]">
            <AlertTriangle className="w-4 h-4" />
            <h3 className="text-sm font-semibold">Void Voucher (Audit Protected)</h3>
          </div>
          <button
            type="button"
            onClick={onClose}
            className="p-1 rounded-lg text-[var(--p-on-danger-container)] hover:bg-black/10"
          >
            <X className="w-4 h-4" />
          </button>
        </div>

        <div className="p-4 space-y-4">
          <div className="rounded-xl border border-[var(--p-card-border)] bg-[var(--p-bg)] p-3 text-xs">
            <div className="flex justify-between">
              <span className="text-[var(--p-text-muted)]">Document</span>
              <span className="font-semibold text-[var(--p-text)]">
                {txn.invoiceNumber || txn.id} ({txn.type})
              </span>
            </div>
            <div className="flex justify-between mt-1">
              <span className="text-[var(--p-text-muted)]">Amount</span>
              <span className="font-bold font-mono-num text-[var(--p-text)]">
                {formatPaise(txn.amountPaise)}
              </span>
            </div>
            <p className="mt-2 text-[11px] text-[var(--p-text-muted)]">
              Voiding marks this voucher cancelled without deleting ledger history and reverses any
              itemized stock movement automatically.
            </p>
          </div>

          <div>
            <label className="block text-xs font-medium text-[var(--p-text)] mb-1">
              Mandatory Audit Reason *
            </label>
            <input
              type="text"
              required
              value={reason}
              onChange={(e) => setReason(e.target.value)}
              placeholder="e.g. Wrong GSTIN entered / Customer cancelled order"
              className="w-full rounded-xl border border-[var(--p-card-border)] bg-[var(--p-bg)] px-3 py-2 text-xs text-[var(--p-text)] focus:outline-none focus:ring-1 focus:ring-[var(--p-danger)]"
            />
          </div>

          <div className="flex justify-end gap-2">
            <button
              type="button"
              onClick={onClose}
              className="whitespace-nowrap rounded-lg border border-[var(--p-card-border)] px-3 py-2 text-xs font-medium text-[var(--p-text)] hover:bg-[var(--p-secondary-bg)]"
            >
              Cancel
            </button>
            <button
              type="submit"
              disabled={!reason.trim()}
              className="whitespace-nowrap rounded-lg bg-[var(--p-danger)] px-3.5 py-2 text-xs font-semibold text-white hover:opacity-90 disabled:opacity-50"
            >
              Void Voucher
            </button>
          </div>
        </div>
      </form>
    </div>
  );
};
