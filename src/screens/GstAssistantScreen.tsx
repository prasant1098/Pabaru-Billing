import React, { useMemo, useState } from 'react';
import {
  CheckCircle2,
  Download,
  FileSpreadsheet,
  ReceiptIndianRupee,
  SlidersHorizontal,
  Sparkles,
  Upload,
} from 'lucide-react';
import {
  GST_PERIOD_LABELS,
  GstPeriodFilter,
  RULE88A_STRATEGY_META,
  Rule88AStrategy,
  compareAllRule88AStrategies,
  computeStatutoryCmp08,
  computeStatutoryGstr1,
  computeStatutoryGstr3b,
} from '../engines/cmaAndGstEngine';
import { downloadFile } from '../engines/exportEngine';
import { formatPaise } from '../engines/financialEngine';
import {
  generateCmp08Json,
  generateGstr1Json,
  generateGstr3bJson,
  getFilingPeriodCode,
} from '../engines/gstPortalJsonEngine';
import {
  ImportedGstr1Invoice,
  TaxSoftwareFormat,
  detectGstr1Format,
  getSampleGstr1Data,
  parseGstr1Invoices,
  parseSpreadsheetFile,
} from '../engines/gstr1ImportEngine';
import { usePabaru } from '../state/PabaruContext';

export const GstAssistantScreen: React.FC = () => {
  const { state, unlockGstAssistantDemo, addToast } = usePabaru();

  const [period, setPeriod] = useState<GstPeriodFilter>('AllTime');
  const [tab, setTab] = useState<'gstr1' | 'gstr3b' | 'cmp08' | 'import'>('gstr1');
  const [strategy, setStrategy] = useState<Rule88AStrategy>('OPTIMAL');
  const [customPct, setCustomPct] = useState(50);
  const [cmpRate, setCmpRate] = useState(1.0);

  // Excel GSTR-1 Prefill state
  const [prefilled, setPrefilled] = useState<ImportedGstr1Invoice[]>([]);
  const [importSoftware, setImportSoftware] = useState<TaxSoftwareFormat>('AutoDetect');

  const selectedPrefilled = useMemo(
    () => prefilled.filter((p) => p.isSelected),
    [prefilled]
  );

  const gstr1 = useMemo(
    () =>
      computeStatutoryGstr1(
        state.transactions,
        state.parties,
        state.profile,
        period,
        selectedPrefilled
      ),
    [state.transactions, state.parties, state.profile, period, selectedPrefilled]
  );

  const gstr3b = useMemo(
    () =>
      computeStatutoryGstr3b(
        state.transactions,
        state.parties,
        state.profile,
        gstr1,
        period,
        strategy,
        strategy === 'CUSTOM' ? customPct : undefined
      ),
    [state.transactions, state.parties, state.profile, gstr1, period, strategy, customPct]
  );

  const rule88aComparison = useMemo(
    () =>
      compareAllRule88AStrategies(
        gstr3b.outwardIgstPaise,
        gstr3b.outwardCgstPaise,
        gstr3b.outwardSgstPaise,
        gstr3b.eligibleItcIgstPaise,
        gstr3b.eligibleItcCgstPaise,
        gstr3b.eligibleItcSgstPaise
      ),
    [gstr3b]
  );

  const cmp08 = useMemo(
    () => computeStatutoryCmp08(state.transactions, state.profile, period, cmpRate),
    [state.transactions, state.profile, period, cmpRate]
  );

  const handleExcelUpload = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;
    try {
      const raw = await parseSpreadsheetFile(file);
      const det = detectGstr1Format(raw.headers);
      const fmt = importSoftware === 'AutoDetect' ? det.format : importSoftware;
      const res = parseGstr1Invoices(raw, fmt, det.mapping, state.profile.homeStateCode);
      setPrefilled(res.invoices);
      addToast(`Parsed ${res.invoices.length} invoices from ${file.name} (${res.detectedFormat})`, 'success');
    } catch (err) {
      addToast(err instanceof Error ? err.message : 'Failed to parse Excel/CSV file', 'error');
    }
    e.target.value = '';
  };

  const loadSamplePrefill = (fmt: TaxSoftwareFormat) => {
    const raw = getSampleGstr1Data(fmt);
    const det = detectGstr1Format(raw.headers);
    const res = parseGstr1Invoices(raw, fmt, det.mapping, state.profile.homeStateCode);
    setPrefilled(res.invoices);
    addToast(`Loaded ${res.invoices.length} sample ${fmt} GSTR-1 invoices`, 'success');
  };

  if (!state.isGstAssistantUnlocked) {
    return (
      <div className="max-w-lg mx-auto mt-8 rounded-2xl border border-[var(--p-card-border)] bg-[var(--p-card)] p-6 text-center space-y-4">
        <ReceiptIndianRupee className="w-10 h-10 text-[var(--p-primary)] mx-auto" />
        <h2 className="text-lg font-bold">GST Returns &amp; Rule 88A Assistant</h2>
        <p className="text-xs text-[var(--p-text-muted)]">
          Generate official GSTR-1, GSTR-3B, and CMP-08 JSON payloads for GST Portal upload, import
          Tally/Zoho/ClearTax Excel registers, and legally minimize cash tax via Rule 88A.
        </p>
        <button
          type="button"
          onClick={unlockGstAssistantDemo}
          className="rounded-xl bg-[var(--p-primary)] px-5 py-2.5 text-xs font-semibold text-[var(--p-primary-text)]"
        >
          Unlock GST Assistant Pro (Demo)
        </button>
      </div>
    );
  }

  return (
    <div className="space-y-5">
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
        <div>
          <h2 className="text-lg md:text-xl font-bold">
            GST Filing Hub (GSTR-1 · GSTR-3B · Rule 88A · CMP-08)
          </h2>
          <p className="text-xs text-[var(--p-text-muted)]">
            Filing Period Code: <strong className="font-mono-num">{getFilingPeriodCode(period)}</strong> ·{' '}
            {selectedPrefilled.length} Excel pre-filled docs merged
          </p>
        </div>

        <select
          value={period}
          onChange={(e) => setPeriod(e.target.value as GstPeriodFilter)}
          className="rounded-xl border border-[var(--p-card-border)] bg-[var(--p-card)] px-3 py-2 text-xs font-medium"
        >
          {(Object.keys(GST_PERIOD_LABELS) as GstPeriodFilter[]).map((k) => (
            <option key={k} value={k}>
              {GST_PERIOD_LABELS[k]}
            </option>
          ))}
        </select>
      </div>

      {/* Tabs */}
      <div className="flex flex-wrap gap-1.5">
        {[
          { id: 'gstr1' as const, label: 'GSTR-1 (Outward Supplies)' },
          { id: 'gstr3b' as const, label: 'GSTR-3B & Rule 88A Optimizer' },
          { id: 'cmp08' as const, label: 'CMP-08 (Composition)' },
          { id: 'import' as const, label: `Excel Pre-Fill (${prefilled.length})` },
        ].map((t) => (
          <button
            key={t.id}
            type="button"
            onClick={() => setTab(t.id)}
            className={`whitespace-nowrap rounded-xl border px-3 py-2 text-xs font-semibold ${
              tab === t.id
                ? 'border-[var(--p-primary)] bg-[var(--p-primary)] text-[var(--p-primary-text)]'
                : 'border-[var(--p-card-border)] bg-[var(--p-card)] hover:bg-[var(--p-secondary-bg)]'
            }`}
          >
            {t.label}
          </button>
        ))}
      </div>

      {tab === 'gstr1' && (
        <div className="space-y-4">
          <div className="grid grid-cols-1 sm:grid-cols-4 gap-3">
            <div className="rounded-2xl border border-[var(--p-card-border)] bg-[var(--p-card)] p-4">
              <p className="text-xs text-[var(--p-text-muted)]">B2B Taxable ({gstr1.b2bCount})</p>
              <p className="text-lg font-bold font-mono-num mt-1">
                {formatPaise(gstr1.b2bTaxablePaise)}
              </p>
            </div>
            <div className="rounded-2xl border border-[var(--p-card-border)] bg-[var(--p-card)] p-4">
              <p className="text-xs text-[var(--p-text-muted)]">B2C Taxable ({gstr1.b2cCount})</p>
              <p className="text-lg font-bold font-mono-num mt-1">
                {formatPaise(gstr1.b2cTaxablePaise)}
              </p>
            </div>
            <div className="rounded-2xl border border-[var(--p-card-border)] bg-[var(--p-card)] p-4">
              <p className="text-xs text-[var(--p-text-muted)]">Total Outward Tax</p>
              <p className="text-lg font-bold font-mono-num text-[var(--p-primary)] mt-1">
                {formatPaise(gstr1.totalTaxPaise)}
              </p>
            </div>
            <div className="rounded-2xl border border-[var(--p-card-border)] bg-[var(--p-card)] p-4 flex items-center justify-between">
              <div>
                <p className="text-xs text-[var(--p-text-muted)]">Portal Utility</p>
                <p className="text-xs font-semibold mt-0.5">v3.1.6 JSON</p>
              </div>
              <button
                type="button"
                onClick={() => {
                  const json = generateGstr1Json(state.profile, gstr1, period);
                  downloadFile(
                    `GSTR1_${state.profile.gstin || 'UNREG'}_${getFilingPeriodCode(period)}.json`,
                    json,
                    'application/json'
                  );
                  addToast('Downloaded GSTR-1 Portal JSON', 'success');
                }}
                className="inline-flex items-center gap-1.5 rounded-xl bg-[var(--p-primary)] px-3 py-2 text-xs font-semibold text-[var(--p-primary-text)]"
              >
                <Download className="w-3.5 h-3.5" />
                Export JSON
              </button>
            </div>
          </div>

          <div className="rounded-2xl border border-[var(--p-card-border)] bg-[var(--p-card)] overflow-hidden">
            <div className="px-4 py-3 border-b border-[var(--p-card-border)]">
              <h3 className="text-sm font-bold">Table 4A · B2B Registered Invoices ({gstr1.b2bInvoices.length})</h3>
            </div>
            <div className="overflow-x-auto">
              <table className="w-full text-left text-xs">
                <thead className="bg-[var(--p-secondary-bg)] text-[var(--p-text-muted)] uppercase text-[10px]">
                  <tr>
                    <th className="px-4 py-2.5">Invoice #</th>
                    <th className="px-4 py-2.5">Buyer &amp; GSTIN</th>
                    <th className="px-4 py-2.5">POS</th>
                    <th className="px-4 py-2.5 text-right">Taxable</th>
                    <th className="px-4 py-2.5 text-right">CGST+SGST</th>
                    <th className="px-4 py-2.5 text-right">IGST</th>
                    <th className="px-4 py-2.5 text-right">Invoice Val</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-[var(--p-card-border)]">
                  {gstr1.b2bInvoices.map((inv, idx) => (
                    <tr key={`${inv.invoiceNumber}-${idx}`}>
                      <td className="px-4 py-2.5 font-mono-num font-bold">
                        {inv.invoiceNumber}
                        {inv.isPrefilledFromExcel && (
                          <span className="ml-1.5 rounded bg-[var(--p-info-container)] px-1.5 py-0.5 text-[9px] text-[var(--p-on-info-container)]">
                            EXCEL
                          </span>
                        )}
                      </td>
                      <td className="px-4 py-2.5">
                        <p className="font-semibold">{inv.partyName}</p>
                        <p className="text-[11px] font-mono-num text-[var(--p-text-muted)]">
                          {inv.gstin}
                        </p>
                      </td>
                      <td className="px-4 py-2.5 font-mono-num">{inv.posStateCode}</td>
                      <td className="px-4 py-2.5 text-right font-mono-num">
                        {formatPaise(inv.taxablePaise)}
                      </td>
                      <td className="px-4 py-2.5 text-right font-mono-num">
                        {formatPaise(inv.cgstPaise + inv.sgstPaise)}
                      </td>
                      <td className="px-4 py-2.5 text-right font-mono-num">
                        {formatPaise(inv.igstPaise)}
                      </td>
                      <td className="px-4 py-2.5 text-right font-mono-num font-bold">
                        {formatPaise(inv.totalPaise)}
                      </td>
                    </tr>
                  ))}
                  {gstr1.b2bInvoices.length === 0 && (
                    <tr>
                      <td colSpan={7} className="px-4 py-6 text-center text-[var(--p-text-muted)]">
                        No B2B invoices in selected period.
                      </td>
                    </tr>
                  )}
                </tbody>
              </table>
            </div>
          </div>

          <div className="rounded-2xl border border-[var(--p-card-border)] bg-[var(--p-card)] overflow-hidden">
            <div className="px-4 py-3 border-b border-[var(--p-card-border)]">
              <h3 className="text-sm font-bold">Table 12 · HSN Summary ({gstr1.hsnSummary.length})</h3>
            </div>
            <div className="overflow-x-auto">
              <table className="w-full text-left text-xs">
                <thead className="bg-[var(--p-secondary-bg)] text-[var(--p-text-muted)] uppercase text-[10px]">
                  <tr>
                    <th className="px-4 py-2.5">HSN</th>
                    <th className="px-4 py-2.5">Description</th>
                    <th className="px-4 py-2.5 text-right">Qty</th>
                    <th className="px-4 py-2.5 text-right">Taxable</th>
                    <th className="px-4 py-2.5 text-right">Tax</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-[var(--p-card-border)]">
                  {gstr1.hsnSummary.map((h) => (
                    <tr key={`${h.hsnCode}-${h.gstRateBps}`}>
                      <td className="px-4 py-2 font-mono-num font-bold">{h.hsnCode}</td>
                      <td className="px-4 py-2">{h.description}</td>
                      <td className="px-4 py-2 text-right font-mono-num">
                        {h.totalQuantity} {h.uqc}
                      </td>
                      <td className="px-4 py-2 text-right font-mono-num">
                        {formatPaise(h.taxablePaise)}
                      </td>
                      <td className="px-4 py-2 text-right font-mono-num font-bold">
                        {formatPaise(h.cgstPaise + h.sgstPaise + h.igstPaise)}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        </div>
      )}

      {tab === 'gstr3b' && (
        <div className="space-y-4">
          <div className="rounded-2xl border border-[var(--p-card-border)] bg-[var(--p-card)] p-4 space-y-4">
            <div className="flex flex-wrap items-center justify-between gap-3">
              <div className="flex items-center gap-2">
                <SlidersHorizontal className="w-4 h-4 text-[var(--p-primary)]" />
                <div>
                  <h3 className="text-sm font-bold">Rule 88A / Sec 49B Set-Off Optimizer</h3>
                  <p className="text-[11px] text-[var(--p-text-muted)]">
                    Cash saved vs 50:50 split:{' '}
                    <strong className="text-[var(--p-success)] font-mono-num">
                      {formatPaise(rule88aComparison.cashSavedPaise)}
                    </strong>
                  </p>
                </div>
              </div>

              <button
                type="button"
                onClick={() => {
                  const json = generateGstr3bJson(state.profile, gstr3b, period);
                  downloadFile(
                    `GSTR3B_${state.profile.gstin || 'UNREG'}_${getFilingPeriodCode(period)}.json`,
                    json,
                    'application/json'
                  );
                  addToast('Downloaded GSTR-3B Portal JSON', 'success');
                }}
                className="inline-flex items-center gap-1.5 rounded-xl bg-[var(--p-primary)] px-3.5 py-2 text-xs font-semibold text-[var(--p-primary-text)]"
              >
                <Download className="w-3.5 h-3.5" />
                Export GSTR-3B JSON
              </button>
            </div>

            <div className="grid grid-cols-2 sm:grid-cols-5 gap-2">
              {(Object.keys(RULE88A_STRATEGY_META) as Rule88AStrategy[]).map((st) => (
                <button
                  key={st}
                  type="button"
                  onClick={() => setStrategy(st)}
                  className={`rounded-xl border p-2.5 text-left text-xs transition ${
                    strategy === st
                      ? 'border-[var(--p-primary)] bg-[var(--p-primary)] text-[var(--p-primary-text)]'
                      : 'border-[var(--p-card-border)] bg-[var(--p-bg)] hover:bg-[var(--p-secondary-bg)]'
                  }`}
                >
                  <p className="font-bold truncate">{RULE88A_STRATEGY_META[st].displayName}</p>
                </button>
              ))}
            </div>

            {strategy === 'CUSTOM' && (
              <div className="rounded-xl border border-[var(--p-card-border)] bg-[var(--p-bg)] p-3">
                <label className="block text-xs font-medium mb-1">
                  Remaining IGST Credit Allocated to CGST: <strong>{customPct}%</strong> (SGST:{' '}
                  {100 - customPct}%)
                </label>
                <input
                  type="range"
                  min={0}
                  max={100}
                  value={customPct}
                  onChange={(e) => setCustomPct(Number.parseInt(e.target.value, 10))}
                  className="w-full"
                />
              </div>
            )}

            <div className="grid grid-cols-1 md:grid-cols-3 gap-3 text-xs">
              <div className="rounded-xl border border-[var(--p-card-border)] bg-[var(--p-bg)] p-3">
                <p className="text-[var(--p-text-muted)]">Gross Liability (3.1)</p>
                <p className="text-base font-bold font-mono-num mt-0.5">
                  {formatPaise(gstr3b.setOffMatrix.totalGrossLiabilityPaise)}
                </p>
                <p className="text-[11px] text-[var(--p-text-muted)] mt-1">
                  IGST {formatPaise(gstr3b.outwardIgstPaise)} · CGST{' '}
                  {formatPaise(gstr3b.outwardCgstPaise)} · SGST {formatPaise(gstr3b.outwardSgstPaise)}
                </p>
              </div>
              <div className="rounded-xl border border-[var(--p-card-border)] bg-[var(--p-bg)] p-3">
                <p className="text-[var(--p-text-muted)]">Eligible ITC Claimed (4A)</p>
                <p className="text-base font-bold font-mono-num text-[var(--p-success)] mt-0.5">
                  {formatPaise(gstr3b.totalEligibleItcPaise)}
                </p>
                <p className="text-[11px] text-[var(--p-text-muted)] mt-1">
                  Carry Forward: {formatPaise(gstr3b.setOffMatrix.totalCarriedForwardItcPaise)}
                </p>
              </div>
              <div className="rounded-xl border border-[var(--p-card-border)] bg-[var(--p-bg)] p-3">
                <p className="text-[var(--p-text-muted)]">Net Cash Payable (6.1)</p>
                <p className="text-base font-bold font-mono-num text-[var(--p-primary)] mt-0.5">
                  {formatPaise(gstr3b.setOffMatrix.totalCashPayablePaise)}
                </p>
                <p className="text-[11px] text-[var(--p-text-muted)] mt-1">
                  Cash IGST {formatPaise(gstr3b.setOffMatrix.cashIgstPaise)} · CGST{' '}
                  {formatPaise(gstr3b.setOffMatrix.cashCgstPaise)} · SGST{' '}
                  {formatPaise(gstr3b.setOffMatrix.cashSgstPaise)}
                </p>
              </div>
            </div>

            <div className="rounded-xl border border-[var(--p-card-border)] bg-[var(--p-bg)] p-3 space-y-1.5 text-xs">
              <p className="font-bold">Statutory Set-Off Audit Trail</p>
              {gstr3b.setOffMatrix.explanationSteps.map((s, idx) => (
                <p key={idx} className="text-[var(--p-text-muted)]">
                  {s}
                </p>
              ))}
            </div>
          </div>
        </div>
      )}

      {tab === 'cmp08' && (
        <div className="rounded-2xl border border-[var(--p-card-border)] bg-[var(--p-card)] p-5 space-y-4">
          <div className="flex flex-wrap items-center justify-between gap-3">
            <div>
              <h3 className="text-sm font-bold">Form CMP-08 Composition Tax Return</h3>
              <p className="text-xs text-[var(--p-text-muted)]">
                For Composition Dealers paying flat turnover tax without ITC
              </p>
            </div>
            <div className="flex items-center gap-2">
              <select
                value={cmpRate}
                onChange={(e) => setCmpRate(Number.parseFloat(e.target.value))}
                className="rounded-xl border border-[var(--p-card-border)] bg-[var(--p-bg)] px-3 py-2 text-xs"
              >
                <option value={1.0}>1% Traders / Manufacturers</option>
                <option value={5.0}>5% Restaurants</option>
                <option value={6.0}>6% Service Providers</option>
              </select>
              <button
                type="button"
                onClick={() => {
                  const json = generateCmp08Json(state.profile, cmp08, period);
                  downloadFile(
                    `CMP08_${state.profile.gstin || 'COMP'}_${getFilingPeriodCode(period)}.json`,
                    json,
                    'application/json'
                  );
                  addToast('Downloaded CMP-08 Portal JSON', 'success');
                }}
                className="inline-flex items-center gap-1.5 rounded-xl bg-[var(--p-primary)] px-3.5 py-2 text-xs font-semibold text-[var(--p-primary-text)]"
              >
                <Download className="w-3.5 h-3.5" />
                Export CMP-08 JSON
              </button>
            </div>
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-3 gap-3 text-xs">
            <div className="rounded-xl border border-[var(--p-card-border)] bg-[var(--p-bg)] p-3.5">
              <p className="text-[var(--p-text-muted)]">Outward Turnover ({cmp08.transactionsCount} bills)</p>
              <p className="text-lg font-bold font-mono-num mt-1">{formatPaise(cmp08.turnoverPaise)}</p>
            </div>
            <div className="rounded-xl border border-[var(--p-card-border)] bg-[var(--p-bg)] p-3.5">
              <p className="text-[var(--p-text-muted)]">Composition Tax ({cmp08.compositionRatePercent}%)</p>
              <p className="text-lg font-bold font-mono-num mt-1">
                {formatPaise(cmp08.totalCompositionTaxPaise)}
              </p>
              <p className="text-[11px] text-[var(--p-text-muted)]">
                CGST {formatPaise(cmp08.centralTaxPaise)} · SGST {formatPaise(cmp08.stateTaxPaise)}
              </p>
            </div>
            <div className="rounded-xl border border-[var(--p-card-border)] bg-[var(--p-bg)] p-3.5">
              <p className="text-[var(--p-text-muted)]">Net Cash Payable (incl. RCM)</p>
              <p className="text-lg font-bold font-mono-num text-[var(--p-primary)] mt-1">
                {formatPaise(cmp08.netCashPayablePaise)}
              </p>
            </div>
          </div>
        </div>
      )}

      {tab === 'import' && (
        <div className="space-y-4">
          <div className="rounded-2xl border border-[var(--p-card-border)] bg-[var(--p-card)] p-4 flex flex-wrap items-center justify-between gap-3">
            <div className="flex flex-wrap items-center gap-2">
              <select
                value={importSoftware}
                onChange={(e) => setImportSoftware(e.target.value as TaxSoftwareFormat)}
                className="rounded-xl border border-[var(--p-card-border)] bg-[var(--p-bg)] px-3 py-2 text-xs"
              >
                <option value="AutoDetect">Auto-Detect Excel Format</option>
                <option value="GovtOfflineTool">Govt GST Offline Tool</option>
                <option value="ClearTax">ClearTax GSTR-1</option>
                <option value="ZohoBooks">Zoho Books Outward</option>
                <option value="TallyPrime">TallyPrime / ERP 9</option>
                <option value="BusyAccounting">Busy Accounting</option>
              </select>

              <label className="inline-flex items-center gap-1.5 cursor-pointer rounded-xl bg-[var(--p-primary)] px-3.5 py-2 text-xs font-semibold text-[var(--p-primary-text)]">
                <Upload className="w-3.5 h-3.5" />
                <span>Upload .XLSX / .CSV</span>
                <input
                  type="file"
                  accept=".xlsx,.csv,.tsv,.txt"
                  onChange={handleExcelUpload}
                  className="hidden"
                />
              </label>

              <button
                type="button"
                onClick={() => loadSamplePrefill('GovtOfflineTool')}
                className="inline-flex items-center gap-1 rounded-xl border border-[var(--p-card-border)] px-3 py-2 text-xs"
              >
                <Sparkles className="w-3.5 h-3.5" />
                Load Govt Offline Sample
              </button>
              <button
                type="button"
                onClick={() => loadSamplePrefill('TallyPrime')}
                className="rounded-xl border border-[var(--p-card-border)] px-3 py-2 text-xs"
              >
                Load Tally Sample
              </button>
            </div>

            {prefilled.length > 0 && (
              <button
                type="button"
                onClick={() => setPrefilled([])}
                className="text-xs text-[var(--p-danger)] font-medium"
              >
                Clear Pre-filled Rows
              </button>
            )}
          </div>

          {prefilled.length > 0 && (
            <div className="rounded-2xl border border-[var(--p-card-border)] bg-[var(--p-card)] overflow-hidden">
              <div className="px-4 py-3 border-b border-[var(--p-card-border)] flex items-center gap-2">
                <FileSpreadsheet className="w-4 h-4 text-[var(--p-primary)]" />
                <h3 className="text-sm font-bold">
                  Pre-filled Invoices Merged into GSTR-1 ({selectedPrefilled.length}/{prefilled.length} active)
                </h3>
              </div>
              <div className="overflow-x-auto">
                <table className="w-full text-left text-xs">
                  <thead className="bg-[var(--p-secondary-bg)] text-[var(--p-text-muted)] uppercase text-[10px]">
                    <tr>
                      <th className="px-3 py-2">Include</th>
                      <th className="px-3 py-2">Invoice</th>
                      <th className="px-3 py-2">Party / GSTIN</th>
                      <th className="px-3 py-2 text-right">Taxable</th>
                      <th className="px-3 py-2 text-right">Total</th>
                      <th className="px-3 py-2">Status</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-[var(--p-card-border)]">
                    {prefilled.map((inv) => (
                      <tr key={inv.id}>
                        <td className="px-3 py-2">
                          <input
                            type="checkbox"
                            checked={inv.isSelected}
                            onChange={() =>
                              setPrefilled((prev) =>
                                prev.map((x) =>
                                  x.id === inv.id ? { ...x, isSelected: !x.isSelected } : x
                                )
                              )
                            }
                          />
                        </td>
                        <td className="px-3 py-2 font-mono-num font-bold">
                          {inv.invoiceNumber}
                          <div className="text-[10px] font-normal text-[var(--p-text-muted)]">
                            {inv.date}
                          </div>
                        </td>
                        <td className="px-3 py-2">
                          <p className="font-semibold">{inv.partyName}</p>
                          <p className="text-[11px] font-mono-num text-[var(--p-text-muted)]">
                            {inv.gstin || 'B2C'}
                          </p>
                        </td>
                        <td className="px-3 py-2 text-right font-mono-num">
                          {formatPaise(inv.taxablePaise)}
                        </td>
                        <td className="px-3 py-2 text-right font-mono-num font-bold">
                          {formatPaise(inv.totalPaise)}
                        </td>
                        <td className="px-3 py-2">
                          {inv.validationWarnings.length === 0 ? (
                            <span className="inline-flex items-center gap-1 text-[var(--p-success)]">
                              <CheckCircle2 className="w-3.5 h-3.5" /> Clean
                            </span>
                          ) : (
                            <span className="text-[11px] text-[var(--p-warning)]">
                              {inv.validationWarnings[0]}
                            </span>
                          )}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>
          )}
        </div>
      )}
    </div>
  );
};
