import React from 'react';
import { AlertCircle, CheckCircle2, Info, AlertTriangle, X } from 'lucide-react';
import { usePabaru } from '../state/PabaruContext';

export const ToastContainer: React.FC = () => {
  const { toasts, dismissToast } = usePabaru();
  if (toasts.length === 0) return null;

  return (
    <div className="no-print fixed bottom-20 md:bottom-5 right-4 z-50 flex flex-col gap-2 max-w-sm w-full pointer-events-none">
      {toasts.map((t) => {
        const icon =
          t.variant === 'success' ? (
            <CheckCircle2 className="w-4 h-4 shrink-0 text-[var(--p-success)]" />
          ) : t.variant === 'error' ? (
            <AlertCircle className="w-4 h-4 shrink-0 text-[var(--p-danger)]" />
          ) : t.variant === 'warning' ? (
            <AlertTriangle className="w-4 h-4 shrink-0 text-[var(--p-warning)]" />
          ) : (
            <Info className="w-4 h-4 shrink-0 text-[var(--p-info)]" />
          );

        return (
          <div
            key={t.id}
            className="pointer-events-auto flex items-center justify-between gap-3 rounded-xl border border-[var(--p-card-border)] bg-[var(--p-card)] px-3.5 py-2.5 shadow-lg text-xs font-medium text-[var(--p-text)]"
          >
            <div className="flex items-center gap-2.5 min-w-0">
              {icon}
              <span className="truncate">{t.message}</span>
            </div>
            <button
              type="button"
              onClick={() => dismissToast(t.id)}
              className="p-1 rounded-md text-[var(--p-text-muted)] hover:bg-[var(--p-secondary-bg)] shrink-0"
              aria-label="Dismiss notification"
            >
              <X className="w-3.5 h-3.5" />
            </button>
          </div>
        );
      })}
    </div>
  );
};
