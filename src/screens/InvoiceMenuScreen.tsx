import React, { useMemo, useState } from 'react';
import {
  FilePlus2,
  MessageCircle,
  Plus,
  Printer,
  Search,
  Trash2,
  X,
} from 'lucide-react';
import { generateQrDataUrl } from '../components/QrCodeSvg';
import { VoidTransactionModal } from '../components/VoidTransactionModal';
import { buildInvoicePrintHtml, buildUpiPayUri } from '../engines/exportEngine';
import {
  INDIAN_STATES,
  calculateGstBps,
  formatPaise,
  formatQuantityMilli,
  getTodayDate,
  lineTaxable,
  parsePaiseFromRupees,
  validateGstin,
} from '../engines/financialEngine';
import { usePabaru } from '../state/PabaruContext';
import {
  GstSlabs,
  PaymentMode,
  Transaction,
  TransactionType,
} from '../types/models';

type InvoiceFilter =
  | 'all'
  | 'tax_invoices'
  | 'non_gst'
  | 'notes'
  | 'quotations'
  | 'purchases';

interface DraftLine {
  itemId: string;
  name: string;
  hsnCode: string;
  qtyStr: string;
  unit: string;
  rateRupees: string;
  gstRateBps: number;
}

export const InvoiceMenuScreen: React.FC = () => {
  const {
    state,
    recordItemizedInvoice,
    addParty,
    openPrintPreview,
    openWhatsAppShare,
  } = usePabaru();

  const [filter, setFilter] = useState<InvoiceFilter>('all');
  const [search, setSearch] = useState('');
  const [voidTarget, setVoidTarget] = useState<Transaction | null>(null);
  const [composerOpen, setComposerOpen] = useState(false);

  // Composer state
  const [docType, setDocType] = useState<TransactionType>('Sale');
  const [docDate, setDocDate] = useState(getTodayDate());
  const [partyId, setPartyId] = useState<string>(state.parties[0]?.id || '');
  const [payMode, setPayMode] = useState<PaymentMode>('Credit');
  const [bankId, setBankId] = useState<string>(state.bankAccounts[0]?.id || '');
  const [remarks, setRemarks] = useState('');
  const [lines, setLines] = useState<DraftLine[]>(() => {
    const first = state.products.find((p) => !p.isArchived);
    return first
      ? [
          {
            itemId: first.id,
            name: first.name,
            hsnCode: first.hsnCode,
            qtyStr: '1',
            unit: first.unit,
            rateRupees: (first.salePricePaise / 100).toFixed(2),
            gstRateBps: first.gstRateBps,
          },
        ]
      : [
          {
            itemId: 'CUSTOM',
            name: '',
            hsnCode: '9999',
            qtyStr: '1',
            unit: 'PCS',
            rateRupees: '0',
            gstRateBps: 1800,
          },
        ];
  });

  // Inline quick add party
  const [quickPartyOpen, setQuickPartyOpen] = useState(false);
  const [qpName, setQpName] = useState('');
  const [qpPhone, setQpPhone] = useState('');
  const [qpGstin, setQpGstin] = useState('');
  const [qpState, setQpState] = useState(state.profile.homeStateCode || '27');

  const invoiceTxns = useMemo(() => {
    const q = search.trim().toLowerCase();
    return state.transactions.filter((t) => {
      if (t.type === 'PaymentIn' || t.type === 'PaymentOut' || t.type === 'Expense' || t.type === 'Drawing') {
        return false;
      }
      if (filter === 'tax_invoices' && t.type !== 'Sale') return false;
      if (filter === 'non_gst' && t.type !== 'NonGstBill') return false;
      if (filter === 'notes' && t.type !== 'CreditNote' && t.type !== 'DebitNote') return false;
      if (filter === 'quotations' && t.type !== 'Quotation') return false;
      if (filter === 'purchases' && t.type !== 'Purchase') return false;
      if (!q) return true;
      const pName = state.parties.find((p) => p.id === t.partyId)?.name.toLowerCase() || '';
      return (
        t.invoiceNumber.toLowerCase().includes(q) ||
        pName.includes(q) ||
        t.remarks.toLowerCase().includes(q)
      );
    });
  }, [state.transactions, state.parties, filter, search]);

  const selectedParty = state.parties.find((p) => p.id === partyId) || null;
  const buyerState = selectedParty?.stateCode || state.profile.homeStateCode;
  const isInterstate = buyerState !== '' && buyerState !== state.profile.homeStateCode;
  const isNonTaxMode =
    docType === 'NonGstBill' ||
    (docType === 'Sale' && state.profile.registrationType !== 'regular');

  const previewTotals = useMemo(() => {
    let taxable = 0;
    let cgst = 0;
    let sgst = 0;
    let igst = 0;
    for (const ln of lines) {
      const qtyMilli = Math.max(0, Math.round((Number.parseFloat(ln.qtyStr) || 0) * 1000));
      const ratePaise = parsePaiseFromRupees(ln.rateRupees);
      const t = lineTaxable(qtyMilli, ratePaise);
      const rateBps = isNonTaxMode ? 0 : ln.gstRateBps;
      const g = calculateGstBps(t, rateBps, isInterstate, buyerState === '96');
      taxable += t;
      cgst += g.cgstPaise;
      sgst += g.sgstPaise;
      igst += g.igstPaise;
    }
    const raw = taxable + cgst + sgst + igst;
    const rounded = Math.floor((raw + 50) / 100) * 100;
    return { taxable, cgst, sgst, igst, raw, rounded, roundOff: rounded - raw };
  }, [lines, isInterstate, isNonTaxMode, buyerState]);

  const handleProductPick = (idx: number, productId: string) => {
    if (productId === 'CUSTOM') {
      setLines((prev) =>
        prev.map((ln, i) => (i === idx ? { ...ln, itemId: 'CUSTOM' } : ln))
      );
      return;
    }
    const prod = state.products.find((p) => p.id === productId);
    if (!prod) return;
    const rate = docType === 'Purchase' ? prod.purchasePricePaise : prod.salePricePaise;
    setLines((prev) =>
      prev.map((ln, i) =>
        i === idx
          ? {
              ...ln,
              itemId: prod.id,
              name: prod.name,
              hsnCode: prod.hsnCode,
              unit: prod.unit,
              rateRupees: (rate / 100).toFixed(2),
              gstRateBps: prod.gstRateBps,
            }
          : ln
      )
    );
  };

  const submitInvoice = (e: React.FormEvent) => {
    e.preventDefault();
    const mapped = lines
      .map((ln) => ({
        itemId: ln.itemId,
        name: ln.name.trim() || 'Item',
        hsnCode: ln.hsnCode.trim() || '9999',
        quantityMilli: Math.max(0, Math.round((Number.parseFloat(ln.qtyStr) || 0) * 1000)),
        unit: ln.unit || 'PCS',
        ratePaise: parsePaiseFromRupees(ln.rateRupees),
        gstRateBps: ln.gstRateBps,
      }))
      .filter((x) => x.quantityMilli > 0 && x.ratePaise > 0);

    if (mapped.length === 0) return;

    const txn = recordItemizedInvoice({
      type: docType,
      date: docDate,
      partyId: partyId || null,
      paymentMode: payMode,
      bankAccountId: payMode === 'Cash' || payMode === 'Credit' ? null : bankId || null,
      remarks,
      items: mapped,
    });

    if (txn) {
      setComposerOpen(false);
      setRemarks('');
    }
  };

  const handlePrint = async (txn: Transaction) => {
    const party = state.parties.find((p) => p.id === txn.partyId) || null;
    const defaultQr =
      state.qrCodes.find((q) => q.isDefault && q.isEnabled && q.showOnInvoices) || null;
    const bank =
      state.bankAccounts.find((b) => b.id === (txn.bankAccountId || defaultQr?.linkedBankId)) ||
      state.bankAccounts[0] ||
      null;
    const upi = defaultQr?.upiId || bank?.upiId;
    const qrDataUrl = upi
      ? await generateQrDataUrl(
          buildUpiPayUri(upi, state.profile.businessName, txn.amountPaise, txn.invoiceNumber || txn.id)
        )
      : null;

    openPrintPreview({
      title: `${txn.type} · ${txn.invoiceNumber || txn.id}`,
      html: buildInvoicePrintHtml(txn, state.profile, party, bank, qrDataUrl),
      filename: `${(txn.invoiceNumber || txn.id).replace(/[^a-zA-Z0-9-_]/g, '_')}.html`,
    });
  };

  const handleQuickPartySave = (e: React.FormEvent) => {
    e.preventDefault();
    if (!qpName.trim()) return;
    const created = addParty({
      name: qpName.trim(),
      phone: qpPhone.trim(),
      address: '',
      gstin: qpGstin.trim().toUpperCase(),
      pan: qpGstin.trim().length >= 12 ? qpGstin.trim().slice(2, 12).toUpperCase() : '',
      stateCode: qpGstin.trim().length >= 2 ? qpGstin.slice(0, 2) : qpState,
      openingBalancePaise: 0,
      openingBalanceType: 'Settled',
      isSupplier: docType === 'Purchase',
    });
    setPartyId(created.id);
    setQpName('');
    setQpPhone('');
    setQpGstin('');
    setQuickPartyOpen(false);
  };

  return (
    <div className="space-y-5">
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
        <div>
          <h2 className="text-lg md:text-xl font-bold">GST Invoices &amp; Billing Hub</h2>
          <p className="text-xs text-[var(--p-text-muted)]">
            Tax Invoices, Bills of Supply, Credit/Debit Notes, Quotations &amp; Purchase Bills
          </p>
        </div>
        <button
          type="button"
          onClick={() => setComposerOpen(true)}
          className="inline-flex items-center gap-1.5 whitespace-nowrap shrink-0 rounded-xl bg-[var(--p-primary)] px-4 py-2 text-xs font-semibold text-[var(--p-primary-text)] hover:opacity-90"
        >
          <FilePlus2 className="w-4 h-4" />
          Create New Voucher
        </button>
      </div>

      {/* Filters & Search */}
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-3">
        <div className="flex flex-wrap gap-1.5">
          {[
            { id: 'all' as InvoiceFilter, label: 'All Docs' },
            { id: 'tax_invoices' as InvoiceFilter, label: 'Sales / Tax Invoices' },
            { id: 'non_gst' as InvoiceFilter, label: 'Non-GST Bills' },
            { id: 'notes' as InvoiceFilter, label: 'Credit / Debit Notes' },
            { id: 'quotations' as InvoiceFilter, label: 'Quotations' },
            { id: 'purchases' as InvoiceFilter, label: 'Purchases' },
          ].map((f) => (
            <button
              key={f.id}
              type="button"
              onClick={() => setFilter(f.id)}
              className={`whitespace-nowrap rounded-lg px-3 py-1.5 text-xs font-medium border transition ${
                filter === f.id
                  ? 'border-[var(--p-primary)] bg-[var(--p-primary)] text-[var(--p-primary-text)]'
                  : 'border-[var(--p-card-border)] bg-[var(--p-card)] text-[var(--p-text)] hover:bg-[var(--p-secondary-bg)]'
              }`}
            >
              {f.label}
            </button>
          ))}
        </div>

        <div className="relative w-full md:w-72">
          <Search className="w-4 h-4 text-[var(--p-text-muted)] absolute left-3 top-2.5" />
          <input
            type="text"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            placeholder="Search invoice #, party, remarks..."
            className="w-full rounded-xl border border-[var(--p-card-border)] bg-[var(--p-card)] pl-9 pr-3 py-2 text-xs"
          />
        </div>
      </div>

      {/* Invoice Table */}
      <div className="rounded-2xl border border-[var(--p-card-border)] bg-[var(--p-card)] overflow-hidden">
        <div className="overflow-x-auto">
          <table className="w-full text-left text-xs">
            <thead className="bg-[var(--p-secondary-bg)] text-[var(--p-text-muted)] uppercase text-[10px]">
              <tr>
                <th className="px-4 py-2.5">Invoice #</th>
                <th className="px-4 py-2.5">Date</th>
                <th className="px-4 py-2.5">Party &amp; Items</th>
                <th className="px-4 py-2.5">Type / Mode</th>
                <th className="px-4 py-2.5 text-right">Amount</th>
                <th className="px-4 py-2.5 text-right">Actions</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-[var(--p-card-border)]">
              {invoiceTxns.map((t) => {
                const party = state.parties.find((p) => p.id === t.partyId);
                return (
                  <tr key={t.id} className={t.isVoided ? 'opacity-55' : 'hover:bg-[var(--p-bg)]'}>
                    <td className="px-4 py-3 font-mono-num font-bold whitespace-nowrap">
                      {t.invoiceNumber || t.id}
                      {t.isVoided && (
                        <span className="ml-1.5 rounded bg-[var(--p-danger-container)] px-1.5 py-0.5 text-[10px] text-[var(--p-on-danger-container)]">
                          VOID
                        </span>
                      )}
                    </td>
                    <td className="px-4 py-3 whitespace-nowrap text-[var(--p-text-muted)]">
                      {t.date}
                    </td>
                    <td className="px-4 py-3">
                      <p className="font-semibold text-[var(--p-text)]">
                        {party?.name || 'Walk-in Customer'}
                      </p>
                      <p className="text-[11px] text-[var(--p-text-muted)] truncate max-w-xs">
                        {t.items.length > 0
                          ? t.items
                              .map((i) => `${i.name} × ${formatQuantityMilli(i.quantityMilli)}`)
                              .join(', ')
                          : t.remarks || t.type}
                      </p>
                    </td>
                    <td className="px-4 py-3 whitespace-nowrap">
                      <span className="rounded-md bg-[var(--p-secondary-bg)] px-2 py-0.5 text-[11px] font-medium">
                        {t.type} · {t.paymentMode}
                      </span>
                    </td>
                    <td className="px-4 py-3 text-right font-mono-num font-bold whitespace-nowrap">
                      {formatPaise(t.amountPaise)}
                    </td>
                    <td className="px-4 py-3 text-right whitespace-nowrap">
                      <div className="inline-flex items-center gap-1.5">
                        <button
                          type="button"
                          onClick={() => handlePrint(t)}
                          className="inline-flex items-center gap-1 rounded-lg border border-[var(--p-card-border)] px-2.5 py-1 text-[11px] font-medium hover:bg-[var(--p-secondary-bg)]"
                        >
                          <Printer className="w-3 h-3" />
                          A4 / PDF
                        </button>
                        {party && !t.isVoided && (
                          <button
                            type="button"
                            onClick={() =>
                              openWhatsAppShare({
                                party,
                                amountPaise: t.amountPaise,
                                invoiceMeta: { invoiceNumber: t.invoiceNumber, date: t.date },
                              })
                            }
                            className="inline-flex items-center gap-1 rounded-lg border border-[var(--p-card-border)] px-2 py-1 text-[11px] text-[var(--p-success)] hover:bg-[var(--p-success-container)]"
                            title="Share via WhatsApp & UPI"
                          >
                            <MessageCircle className="w-3 h-3" />
                          </button>
                        )}
                        {!t.isVoided && (
                          <button
                            type="button"
                            onClick={() => setVoidTarget(t)}
                            className="rounded-lg border border-[var(--p-card-border)] px-2 py-1 text-[11px] text-[var(--p-danger)] hover:bg-[var(--p-danger-container)]"
                          >
                            Void
                          </button>
                        )}
                      </div>
                    </td>
                  </tr>
                );
              })}
              {invoiceTxns.length === 0 && (
                <tr>
                  <td colSpan={6} className="px-4 py-8 text-center text-[var(--p-text-muted)]">
                    No matching billing documents found.
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      </div>

      {/* New Invoice Composer Modal */}
      {composerOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/65 p-3 md:p-6">
          <form
            onSubmit={submitInvoice}
            className="flex flex-col w-full max-w-4xl max-h-[92vh] rounded-2xl border border-[var(--p-card-border)] bg-[var(--p-card)] shadow-2xl overflow-hidden"
          >
            <div className="flex items-center justify-between px-4 py-3 border-b border-[var(--p-card-border)] bg-[var(--p-secondary-bg)]">
              <div>
                <h3 className="text-sm font-bold">New Itemized GST Voucher</h3>
                <p className="text-[11px] text-[var(--p-text-muted)]">
                  Supply: {isInterstate ? 'Inter-State (IGST)' : 'Intra-State (CGST + SGST)'} ·
                  Rule 26 Auto Round-Off
                </p>
              </div>
              <button
                type="button"
                onClick={() => setComposerOpen(false)}
                className="p-1.5 rounded-lg text-[var(--p-text-muted)] hover:bg-[var(--p-card)]"
              >
                <X className="w-4 h-4" />
              </button>
            </div>

            <div className="flex-1 overflow-y-auto p-4 space-y-4">
              {/* Top Meta Grid */}
              <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-3">
                <div>
                  <label className="block text-xs font-medium mb-1">Voucher Type</label>
                  <select
                    value={docType}
                    onChange={(e) => setDocType(e.target.value as TransactionType)}
                    className="w-full rounded-xl border border-[var(--p-card-border)] bg-[var(--p-bg)] px-3 py-2 text-xs font-semibold"
                  >
                    <option value="Sale">Sale (Tax Invoice / BOS)</option>
                    <option value="Purchase">Purchase Bill (Inward ITC)</option>
                    <option value="NonGstBill">Non-GST Bill / Estimate</option>
                    <option value="CreditNote">Credit Note (Sales Return)</option>
                    <option value="DebitNote">Debit Note (Purchase Return)</option>
                    <option value="Quotation">Quotation (No Stock Effect)</option>
                  </select>
                </div>

                <div>
                  <div className="flex items-center justify-between mb-1">
                    <label className="text-xs font-medium">Party / Customer</label>
                    <button
                      type="button"
                      onClick={() => setQuickPartyOpen(true)}
                      className="text-[11px] font-semibold text-[var(--p-link)] hover:underline"
                    >
                      + Quick Add
                    </button>
                  </div>
                  <select
                    value={partyId}
                    onChange={(e) => setPartyId(e.target.value)}
                    className="w-full rounded-xl border border-[var(--p-card-border)] bg-[var(--p-bg)] px-3 py-2 text-xs"
                  >
                    <option value="">Walk-in / Cash Party</option>
                    {state.parties.map((p) => (
                      <option key={p.id} value={p.id}>
                        {p.name} {p.gstin ? `(${p.stateCode})` : ''}
                      </option>
                    ))}
                  </select>
                </div>

                <div>
                  <label className="block text-xs font-medium mb-1">Invoice Date</label>
                  <input
                    type="date"
                    value={docDate}
                    onChange={(e) => setDocDate(e.target.value)}
                    className="w-full rounded-xl border border-[var(--p-card-border)] bg-[var(--p-bg)] px-3 py-2 text-xs"
                  />
                </div>

                <div>
                  <label className="block text-xs font-medium mb-1">Settlement Mode</label>
                  <select
                    value={payMode}
                    onChange={(e) => setPayMode(e.target.value as PaymentMode)}
                    className="w-full rounded-xl border border-[var(--p-card-border)] bg-[var(--p-bg)] px-3 py-2 text-xs"
                  >
                    <option value="Credit">Credit (Khata Ledger)</option>
                    <option value="Cash">Cash</option>
                    <option value="UPI">UPI</option>
                    <option value="Bank">Bank Transfer</option>
                    <option value="Cheque">Cheque</option>
                  </select>
                </div>
              </div>

              {(payMode === 'Bank' || payMode === 'UPI' || payMode === 'Cheque') && (
                <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                  <div>
                    <label className="block text-xs font-medium mb-1">Deposit / Paid Bank Account</label>
                    <select
                      value={bankId}
                      onChange={(e) => setBankId(e.target.value)}
                      className="w-full rounded-xl border border-[var(--p-card-border)] bg-[var(--p-bg)] px-3 py-2 text-xs"
                    >
                      {state.bankAccounts.map((b) => (
                        <option key={b.id} value={b.id}>
                          {b.bankName} — {b.accountName}
                        </option>
                      ))}
                    </select>
                  </div>
                  <div>
                    <label className="block text-xs font-medium mb-1">Notes / Remarks</label>
                    <input
                      type="text"
                      value={remarks}
                      onChange={(e) => setRemarks(e.target.value)}
                      placeholder="Optional PO #, transport or terms"
                      className="w-full rounded-xl border border-[var(--p-card-border)] bg-[var(--p-bg)] px-3 py-2 text-xs"
                    />
                  </div>
                </div>
              )}

              {/* Line Items Builder */}
              <div className="space-y-2">
                <div className="flex items-center justify-between">
                  <h4 className="text-xs font-bold uppercase tracking-wider text-[var(--p-text-muted)]">
                    Line Items ({lines.length})
                  </h4>
                  <button
                    type="button"
                    onClick={() =>
                      setLines((prev) => [
                        ...prev,
                        {
                          itemId: 'CUSTOM',
                          name: '',
                          hsnCode: '9999',
                          qtyStr: '1',
                          unit: 'PCS',
                          rateRupees: '0',
                          gstRateBps: 1800,
                        },
                      ])
                    }
                    className="inline-flex items-center gap-1 rounded-lg border border-[var(--p-card-border)] bg-[var(--p-bg)] px-2.5 py-1 text-xs font-medium hover:bg-[var(--p-secondary-bg)]"
                  >
                    <Plus className="w-3.5 h-3.5" />
                    Add Line Item
                  </button>
                </div>

                {lines.map((ln, idx) => (
                  <div
                    key={idx}
                    className="grid grid-cols-1 md:grid-cols-12 gap-2 items-end rounded-xl border border-[var(--p-card-border)] bg-[var(--p-bg)] p-3"
                  >
                    <div className="md:col-span-4">
                      <label className="block text-[11px] text-[var(--p-text-muted)] mb-1">
                        Catalog Item / Custom
                      </label>
                      <select
                        value={ln.itemId}
                        onChange={(e) => handleProductPick(idx, e.target.value)}
                        className="w-full rounded-lg border border-[var(--p-card-border)] bg-[var(--p-card)] px-2.5 py-1.5 text-xs mb-1.5"
                      >
                        <option value="CUSTOM">-- Custom Item --</option>
                        {state.products
                          .filter((p) => !p.isArchived)
                          .map((p) => (
                            <option key={p.id} value={p.id}>
                              {p.name} (Stock: {formatQuantityMilli(p.currentStockMilli)} {p.unit})
                            </option>
                          ))}
                      </select>
                      {ln.itemId === 'CUSTOM' && (
                        <input
                          type="text"
                          required
                          value={ln.name}
                          onChange={(e) =>
                            setLines((prev) =>
                              prev.map((x, i) => (i === idx ? { ...x, name: e.target.value } : x))
                            )
                          }
                          placeholder="Item description"
                          className="w-full rounded-lg border border-[var(--p-card-border)] bg-[var(--p-card)] px-2.5 py-1.5 text-xs"
                        />
                      )}
                    </div>

                    <div className="md:col-span-2">
                      <label className="block text-[11px] text-[var(--p-text-muted)] mb-1">
                        HSN / SAC
                      </label>
                      <input
                        type="text"
                        value={ln.hsnCode}
                        onChange={(e) =>
                          setLines((prev) =>
                            prev.map((x, i) => (i === idx ? { ...x, hsnCode: e.target.value } : x))
                          )
                        }
                        className="w-full rounded-lg border border-[var(--p-card-border)] bg-[var(--p-card)] px-2.5 py-1.5 text-xs font-mono-num"
                      />
                    </div>

                    <div className="md:col-span-2">
                      <label className="block text-[11px] text-[var(--p-text-muted)] mb-1">
                        Qty ({ln.unit})
                      </label>
                      <input
                        type="number"
                        step="0.001"
                        min="0.001"
                        required
                        value={ln.qtyStr}
                        onChange={(e) =>
                          setLines((prev) =>
                            prev.map((x, i) => (i === idx ? { ...x, qtyStr: e.target.value } : x))
                          )
                        }
                        className="w-full rounded-lg border border-[var(--p-card-border)] bg-[var(--p-card)] px-2.5 py-1.5 text-xs font-mono-num"
                      />
                    </div>

                    <div className="md:col-span-2">
                      <label className="block text-[11px] text-[var(--p-text-muted)] mb-1">
                        Rate (INR)
                      </label>
                      <input
                        type="number"
                        step="0.01"
                        min="0.01"
                        required
                        value={ln.rateRupees}
                        onChange={(e) =>
                          setLines((prev) =>
                            prev.map((x, i) =>
                              i === idx ? { ...x, rateRupees: e.target.value } : x
                            )
                          )
                        }
                        className="w-full rounded-lg border border-[var(--p-card-border)] bg-[var(--p-card)] px-2.5 py-1.5 text-xs font-mono-num"
                      />
                    </div>

                    <div className="md:col-span-2 flex items-end gap-1.5">
                      <div className="flex-1">
                        <label className="block text-[11px] text-[var(--p-text-muted)] mb-1">
                          GST %
                        </label>
                        <select
                          disabled={isNonTaxMode}
                          value={isNonTaxMode ? 0 : ln.gstRateBps}
                          onChange={(e) =>
                            setLines((prev) =>
                              prev.map((x, i) =>
                                i === idx
                                  ? { ...x, gstRateBps: Number.parseInt(e.target.value, 10) }
                                  : x
                              )
                            )
                          }
                          className="w-full rounded-lg border border-[var(--p-card-border)] bg-[var(--p-card)] px-2 py-1.5 text-xs font-mono-num"
                        >
                          {GstSlabs.ALL_BPS.map((b) => (
                            <option key={b} value={b}>
                              {GstSlabs.label(b)}
                            </option>
                          ))}
                        </select>
                      </div>
                      {lines.length > 1 && (
                        <button
                          type="button"
                          onClick={() =>
                            setLines((prev) => prev.filter((_, i) => i !== idx))
                          }
                          className="p-2 rounded-lg text-[var(--p-danger)] hover:bg-[var(--p-danger-container)]"
                          title="Remove row"
                        >
                          <Trash2 className="w-3.5 h-3.5" />
                        </button>
                      )}
                    </div>
                  </div>
                ))}
              </div>

              {/* Live Tax Summary */}
              <div className="rounded-xl border border-[var(--p-card-border)] bg-[var(--p-bg)] p-3.5 grid grid-cols-2 sm:grid-cols-5 gap-3 text-xs">
                <div>
                  <p className="text-[var(--p-text-muted)]">Taxable Value</p>
                  <p className="font-bold font-mono-num">{formatPaise(previewTotals.taxable)}</p>
                </div>
                {isInterstate ? (
                  <div>
                    <p className="text-[var(--p-text-muted)]">IGST</p>
                    <p className="font-bold font-mono-num">{formatPaise(previewTotals.igst)}</p>
                  </div>
                ) : (
                  <>
                    <div>
                      <p className="text-[var(--p-text-muted)]">CGST</p>
                      <p className="font-bold font-mono-num">{formatPaise(previewTotals.cgst)}</p>
                    </div>
                    <div>
                      <p className="text-[var(--p-text-muted)]">SGST</p>
                      <p className="font-bold font-mono-num">{formatPaise(previewTotals.sgst)}</p>
                    </div>
                  </>
                )}
                <div>
                  <p className="text-[var(--p-text-muted)]">Rule 26 Round-off</p>
                  <p className="font-mono-num">
                    {previewTotals.roundOff >= 0 ? '+' : ''}
                    {formatPaise(previewTotals.roundOff)}
                  </p>
                </div>
                <div>
                  <p className="text-[var(--p-text-muted)]">Grand Total</p>
                  <p className="text-base font-bold font-mono-num text-[var(--p-primary)]">
                    {formatPaise(previewTotals.rounded)}
                  </p>
                </div>
              </div>
            </div>

            <div className="px-4 py-3 border-t border-[var(--p-card-border)] flex justify-end gap-2 bg-[var(--p-secondary-bg)]">
              <button
                type="button"
                onClick={() => setComposerOpen(false)}
                className="rounded-xl border border-[var(--p-card-border)] bg-[var(--p-card)] px-4 py-2 text-xs font-medium"
              >
                Cancel
              </button>
              <button
                type="submit"
                className="rounded-xl bg-[var(--p-primary)] px-5 py-2 text-xs font-semibold text-[var(--p-primary-text)] hover:opacity-90"
              >
                Save &amp; Post Voucher
              </button>
            </div>
          </form>
        </div>
      )}

      {/* Quick Add Party Modal */}
      {quickPartyOpen && (
        <div className="fixed inset-0 z-[60] flex items-center justify-center bg-black/60 p-4">
          <form
            onSubmit={handleQuickPartySave}
            className="w-full max-w-sm rounded-2xl border border-[var(--p-card-border)] bg-[var(--p-card)] p-5 space-y-3"
          >
            <div className="flex items-center justify-between">
              <h4 className="text-sm font-bold">Quick Add Party</h4>
              <button type="button" onClick={() => setQuickPartyOpen(false)}>
                <X className="w-4 h-4" />
              </button>
            </div>
            <input
              type="text"
              required
              value={qpName}
              onChange={(e) => setQpName(e.target.value)}
              placeholder="Party / Business Name *"
              className="w-full rounded-xl border border-[var(--p-card-border)] bg-[var(--p-bg)] px-3 py-2 text-xs"
            />
            <input
              type="tel"
              value={qpPhone}
              onChange={(e) => setQpPhone(e.target.value)}
              placeholder="Mobile Number"
              className="w-full rounded-xl border border-[var(--p-card-border)] bg-[var(--p-bg)] px-3 py-2 text-xs"
            />
            <div>
              <input
                type="text"
                maxLength={15}
                value={qpGstin}
                onChange={(e) => setQpGstin(e.target.value.toUpperCase())}
                placeholder="GSTIN (Optional)"
                className="w-full rounded-xl border border-[var(--p-card-border)] bg-[var(--p-bg)] px-3 py-2 text-xs font-mono-num uppercase"
              />
              {qpGstin.trim() && (
                <p
                  className={`text-[11px] mt-1 ${
                    validateGstin(qpGstin, true).isValid
                      ? 'text-[var(--p-success)]'
                      : 'text-[var(--p-danger)]'
                  }`}
                >
                  {validateGstin(qpGstin, true).message}
                </p>
              )}
            </div>
            <select
              value={qpState}
              onChange={(e) => setQpState(e.target.value)}
              className="w-full rounded-xl border border-[var(--p-card-border)] bg-[var(--p-bg)] px-3 py-2 text-xs"
            >
              {INDIAN_STATES.map((s) => (
                <option key={s.code} value={s.code}>
                  {s.code} - {s.name}
                </option>
              ))}
            </select>
            <div className="flex justify-end gap-2 pt-1">
              <button
                type="button"
                onClick={() => setQuickPartyOpen(false)}
                className="rounded-lg border border-[var(--p-card-border)] px-3 py-1.5 text-xs"
              >
                Cancel
              </button>
              <button
                type="submit"
                className="rounded-lg bg-[var(--p-primary)] px-3.5 py-1.5 text-xs font-semibold text-[var(--p-primary-text)]"
              >
                Add Party
              </button>
            </div>
          </form>
        </div>
      )}

      <VoidTransactionModal txn={voidTarget} onClose={() => setVoidTarget(null)} />
    </div>
  );
};
