import React, { useRef } from 'react';
import { Download, Printer, X } from 'lucide-react';
import { downloadFile } from '../engines/exportEngine';
import { usePabaru } from '../state/PabaruContext';

export const PrintPreviewModal: React.FC = () => {
  const { printPreview, closePrintPreview, addToast } = usePabaru();
  const iframeRef = useRef<HTMLIFrameElement | null>(null);

  if (!printPreview) return null;

  const handlePrint = () => {
    const win = iframeRef.current?.contentWindow;
    if (win) {
      win.focus();
      win.print();
    } else {
      addToast('Use Download HTML if browser blocks iframe print', 'info');
    }
  };

  return (
    <div className="no-print fixed inset-0 z-50 flex items-center justify-center bg-black/60 p-3 md:p-6">
      <div className="flex flex-col w-full max-w-4xl h-[90vh] rounded-2xl border border-[var(--p-card-border)] bg-[var(--p-card)] shadow-2xl overflow-hidden">
        <div className="flex items-center justify-between gap-3 px-4 py-3 border-b border-[var(--p-card-border)] bg-[var(--p-secondary-bg)]">
          <div className="min-w-0">
            <h3 className="text-sm font-semibold text-[var(--p-text)] truncate">
              {printPreview.title}
            </h3>
            <p className="text-xs text-[var(--p-text-muted)] truncate">{printPreview.filename}</p>
          </div>
          <div className="flex items-center gap-2 shrink-0">
            <button
              type="button"
              onClick={() => {
                downloadFile(printPreview.filename, printPreview.html, 'text/html;charset=utf-8');
                addToast(`Downloaded ${printPreview.filename}`, 'success');
              }}
              className="inline-flex items-center gap-1.5 whitespace-nowrap shrink-0 rounded-lg border border-[var(--p-card-border)] bg-[var(--p-card)] px-3 py-1.5 text-xs font-medium text-[var(--p-text)] hover:bg-[var(--p-bg)]"
            >
              <Download className="w-3.5 h-3.5" />
              Download HTML
            </button>
            <button
              type="button"
              onClick={handlePrint}
              className="inline-flex items-center gap-1.5 whitespace-nowrap shrink-0 rounded-lg bg-[var(--p-primary)] px-3 py-1.5 text-xs font-semibold text-[var(--p-primary-text)] hover:opacity-90"
            >
              <Printer className="w-3.5 h-3.5" />
              Print / Save PDF
            </button>
            <button
              type="button"
              onClick={closePrintPreview}
              className="p-1.5 rounded-lg text-[var(--p-text-muted)] hover:bg-[var(--p-card)]"
              aria-label="Close preview"
            >
              <X className="w-4 h-4" />
            </button>
          </div>
        </div>
        <div className="flex-1 bg-slate-200 p-3 md:p-5 overflow-hidden">
          <iframe
            ref={iframeRef}
            title={printPreview.title}
            srcDoc={printPreview.html}
            className="w-full h-full rounded-lg bg-white shadow border border-slate-300"
          />
        </div>
      </div>
    </div>
  );
};
