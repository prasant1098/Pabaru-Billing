import React from 'react';
import { Copy, ExternalLink, MessageCircle, X } from 'lucide-react';
import {
  buildUpiPayUri,
  buildWhatsAppReminderText,
  buildWhatsAppUrl,
} from '../engines/exportEngine';
import { formatPaise } from '../engines/financialEngine';
import { usePabaru } from '../state/PabaruContext';
import { QrCodeSvg } from './QrCodeSvg';

export const WhatsAppShareModal: React.FC = () => {
  const { state, whatsappShare, closeWhatsAppShare, addToast } = usePabaru();
  if (!whatsappShare) return null;

  const { party, amountPaise, invoiceMeta } = whatsappShare;
  const defaultQr = state.qrCodes.find((q) => q.isDefault && q.isEnabled) || state.qrCodes[0] || null;
  const linkedBank =
    state.bankAccounts.find((b) => b.id === defaultQr?.linkedBankId) ||
    state.bankAccounts.find((b) => b.isDefaultForQr) ||
    state.bankAccounts[0] ||
    null;

  const activeBankForText = linkedBank
    ? { ...linkedBank, upiId: defaultQr?.upiId || linkedBank.upiId }
    : null;

  const text = buildWhatsAppReminderText(
    party,
    amountPaise,
    activeBankForText,
    state.profile,
    invoiceMeta
  );
  const waHref = buildWhatsAppUrl(party.phone, text);
  const upiUri = activeBankForText?.upiId
    ? buildUpiPayUri(
        activeBankForText.upiId,
        state.profile.businessName,
        Math.abs(amountPaise),
        invoiceMeta?.invoiceNumber || 'Payment'
      )
    : '';

  const copyMessage = async () => {
    try {
      await navigator.clipboard.writeText(text);
      addToast('Reminder message copied to clipboard', 'success');
    } catch {
      addToast('Copy failed; select text manually', 'warning');
    }
  };

  return (
    <div className="no-print fixed inset-0 z-50 flex items-center justify-center bg-black/60 p-4">
      <div className="w-full max-w-lg rounded-2xl border border-[var(--p-card-border)] bg-[var(--p-card)] shadow-2xl overflow-hidden">
        <div className="flex items-center justify-between px-4 py-3 border-b border-[var(--p-card-border)] bg-[var(--p-secondary-bg)]">
          <div className="flex items-center gap-2">
            <MessageCircle className="w-4 h-4 text-[var(--p-success)]" />
            <h3 className="text-sm font-semibold text-[var(--p-text)]">
              WhatsApp &amp; UPI Payment Share
            </h3>
          </div>
          <button
            type="button"
            onClick={closeWhatsAppShare}
            className="p-1 rounded-lg text-[var(--p-text-muted)] hover:bg-[var(--p-card)]"
          >
            <X className="w-4 h-4" />
          </button>
        </div>

        <div className="p-4 space-y-4">
          <div className="flex items-center justify-between rounded-xl border border-[var(--p-card-border)] bg-[var(--p-bg)] p-3">
            <div>
              <p className="text-xs text-[var(--p-text-muted)]">Party</p>
              <p className="text-sm font-semibold text-[var(--p-text)]">{party.name}</p>
              <p className="text-xs text-[var(--p-text-muted)]">{party.phone || 'No mobile number saved'}</p>
            </div>
            <div className="text-right">
              <p className="text-xs text-[var(--p-text-muted)]">Amount Due</p>
              <p className="text-base font-bold font-mono-num text-[var(--p-primary)]">
                {formatPaise(Math.abs(amountPaise))}
              </p>
            </div>
          </div>

          {upiUri && (
            <div className="flex items-center gap-4 rounded-xl border border-[var(--p-card-border)] bg-[var(--p-bg)] p-3">
              <QrCodeSvg value={upiUri} size={84} />
              <div className="min-w-0">
                <p className="text-xs font-semibold text-[var(--p-text)]">Dynamic UPI Pay QR</p>
                <p className="text-xs text-[var(--p-text-muted)] truncate">
                  UPI ID: {activeBankForText?.upiId}
                </p>
                <p className="text-[11px] text-[var(--p-text-muted)] mt-1">
                  Pre-filled with {formatPaise(Math.abs(amountPaise))}
                </p>
              </div>
            </div>
          )}

          <div>
            <label className="block text-xs font-medium text-[var(--p-text-muted)] mb-1.5">
              Message Preview
            </label>
            <pre className="whitespace-pre-wrap rounded-xl border border-[var(--p-card-border)] bg-[var(--p-bg)] p-3 text-xs text-[var(--p-text)] font-sans max-h-48 overflow-y-auto">
              {text}
            </pre>
          </div>

          <div className="flex items-center justify-end gap-2 pt-1">
            <button
              type="button"
              onClick={copyMessage}
              className="inline-flex items-center gap-1.5 whitespace-nowrap shrink-0 rounded-lg border border-[var(--p-card-border)] bg-[var(--p-card)] px-3 py-2 text-xs font-medium text-[var(--p-text)] hover:bg-[var(--p-secondary-bg)]"
            >
              <Copy className="w-3.5 h-3.5" />
              Copy Text
            </button>
            <a
              href={waHref}
              target="_blank"
              rel="noopener noreferrer"
              className="inline-flex items-center gap-1.5 whitespace-nowrap shrink-0 rounded-lg bg-[#16A34A] px-3.5 py-2 text-xs font-semibold text-white hover:bg-[#15803D]"
            >
              <ExternalLink className="w-3.5 h-3.5" />
              Send on WhatsApp
            </a>
          </div>
        </div>
      </div>
    </div>
  );
};
