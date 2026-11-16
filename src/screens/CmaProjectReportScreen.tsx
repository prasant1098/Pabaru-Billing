import React, { useMemo, useState } from 'react';
import { Printer, RefreshCw, Scale } from 'lucide-react';
import {
  AVAILABLE_BASE_YEARS,
  ProjectReportData,
  calculateTermLoanSchedule,
  computeCmaYearMetrics,
  computeFundsFlowSchedule,
  createDefaultProjectReport,
  createInitialDossier,
  evaluateDossier,
} from '../engines/cmaProjectEngine';
import { buildCmaProjectReportHtml } from '../engines/exportEngine';
import { formatPaise, parsePaiseFromRupees } from '../engines/financialEngine';
import { usePabaru } from '../state/PabaruContext';

export const CmaProjectReportScreen: React.FC = () => {
  const {
    state,
    getLiveCashBalancePaise,
    getTotalBankBalancePaise,
    openPrintPreview,
  } = usePabaru();

  const [baseYear, setBaseYear] = useState(2025);
  const [dossier, setDossier] = useState(() =>
    createInitialDossier(
      2025,
      state.transactions,
      state.parties,
      state.products,
      getLiveCashBalancePaise() + getTotalBankBalancePaise()
    )
  );
  const [project, setProject] = useState<ProjectReportData>(() =>
    createDefaultProjectReport(state.profile, dossier)
  );
  const [tab, setTab] = useState<'forms' | 'ratios' | 'funds_emi' | 'project'>('forms');

  const computed = useMemo(
    () => dossier.years.map((y) => ({ y, m: computeCmaYearMetrics(y) })),
    [dossier]
  );
  const feedback = useMemo(() => evaluateDossier(dossier), [dossier]);
  const fundsFlow = useMemo(() => computeFundsFlowSchedule(dossier), [dossier]);
  const loanSchedule = useMemo(
    () =>
      calculateTermLoanSchedule(
        project.bankTermLoanPaise,
        project.proposedInterestRatePercent,
        project.loanTenorMonths,
        project.moratoriumMonths
      ),
    [project]
  );

  const rebuildFromLedger = (yr: number) => {
    setBaseYear(yr);
    const nextDossier = createInitialDossier(
      yr,
      state.transactions,
      state.parties,
      state.products,
      getLiveCashBalancePaise() + getTotalBankBalancePaise()
    );
    setDossier(nextDossier);
    setProject(createDefaultProjectReport(state.profile, nextDossier));
  };

  const handlePrintDossier = () => {
    openPrintPreview({
      title: `CMA Loan Dossier · ${state.profile.businessName}`,
      html: buildCmaProjectReportHtml(state.profile, dossier, feedback, project, loanSchedule),
      filename: `CMA_Project_Dossier_${baseYear}.html`,
    });
  };

  return (
    <div className="space-y-5">
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
        <div>
          <h2 className="text-lg md:text-xl font-bold">
            RBI CMA Data &amp; Bank Loan Project Report (Forms I–VII)
          </h2>
          <p className="text-xs text-[var(--p-text-muted)]">
            4-Year Operating Projections, Tandon I/II vs Nayak MPBF, Funds Flow &amp; EMI Amortization
          </p>
        </div>

        <div className="flex flex-wrap items-center gap-2">
          <select
            value={baseYear}
            onChange={(e) => rebuildFromLedger(Number.parseInt(e.target.value, 10))}
            className="rounded-xl border border-[var(--p-card-border)] bg-[var(--p-card)] px-3 py-2 text-xs"
          >
            {AVAILABLE_BASE_YEARS.map((b) => (
              <option key={b.year} value={b.year}>
                Base: {b.label}
              </option>
            ))}
          </select>
          <button
            type="button"
            onClick={() => rebuildFromLedger(baseYear)}
            className="inline-flex items-center gap-1.5 rounded-xl border border-[var(--p-card-border)] bg-[var(--p-card)] px-3 py-2 text-xs font-medium"
          >
            <RefreshCw className="w-3.5 h-3.5" />
            Sync Ledger
          </button>
          <button
            type="button"
            onClick={handlePrintDossier}
            className="inline-flex items-center gap-1.5 rounded-xl bg-[var(--p-primary)] px-4 py-2 text-xs font-semibold text-[var(--p-primary-text)]"
          >
            <Printer className="w-4 h-4" />
            Print CMA Dossier
          </button>
        </div>
      </div>

      {/* Tabs */}
      <div className="flex flex-wrap gap-1.5">
        {[
          { id: 'forms' as const, label: 'Forms II–V (P&L, BS, MPBF)' },
          { id: 'ratios' as const, label: '19 Banker Ratios & Appraisal' },
          { id: 'funds_emi' as const, label: 'Forms VI–VII (Funds Flow & EMI)' },
          { id: 'project' as const, label: 'Form I (Loan & Project Inputs)' },
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

      {tab === 'forms' && (
        <div className="rounded-2xl border border-[var(--p-card-border)] bg-[var(--p-card)] overflow-hidden">
          <div className="overflow-x-auto">
            <table className="w-full text-left text-xs">
              <thead className="bg-[var(--p-secondary-bg)] text-[var(--p-text-muted)] uppercase text-[10px]">
                <tr>
                  <th className="px-4 py-2.5">CMA Metric</th>
                  {computed.map((c) => (
                    <th key={c.y.fyLabel} className="px-4 py-2.5 text-right whitespace-nowrap">
                      <div>{c.y.fyLabel}</div>
                      <div className="font-normal text-[9px]">{c.y.stageLabel}</div>
                    </th>
                  ))}
                </tr>
              </thead>
              <tbody className="divide-y divide-[var(--p-card-border)]">
                <CmaRow label="Net Sales" vals={computed.map((c) => formatPaise(c.m.netSalesPaise, true))} bold />
                <CmaRow label="Cost of Sales (COGS)" vals={computed.map((c) => formatPaise(c.m.costOfSalesPaise, true))} />
                <CmaRow label="Gross Profit" vals={computed.map((c) => formatPaise(c.m.grossProfitPaise, true))} />
                <CmaRow label="EBITDA" vals={computed.map((c) => formatPaise(c.m.ebitdaPaise, true))} />
                <CmaRow label="Profit After Tax (PAT)" vals={computed.map((c) => formatPaise(c.m.patPaise, true))} bold />
                <CmaRow label="Total Current Assets" vals={computed.map((c) => formatPaise(c.m.totalCurrentAssetsPaise, true))} />
                <CmaRow label="Other Current Liabilities (excl. Bank)" vals={computed.map((c) => formatPaise(c.m.currentLiabilitiesOtherThanBankPaise, true))} />
                <CmaRow label="Tangible Net Worth (TNW)" vals={computed.map((c) => formatPaise(c.m.tangibleNetWorthPaise, true))} />
                <CmaRow label="MPBF (Tandon Method I - 75% WCG)" vals={computed.map((c) => formatPaise(c.m.mpbfTandonMethod1Paise, true))} />
                <CmaRow label="MPBF (Tandon Method II - 25% CA Margin)" vals={computed.map((c) => formatPaise(c.m.mpbfTandonMethod2Paise, true))} bold />
                <CmaRow label="Nayak Committee WC (20% Projected Turnover)" vals={computed.map((c) => formatPaise(c.m.nayakCommitteeWorkingCapitalPaise, true))} />
              </tbody>
            </table>
          </div>
        </div>
      )}

      {tab === 'ratios' && (
        <div className="space-y-4">
          <div className="grid grid-cols-1 md:grid-cols-2 gap-3.5">
            {feedback.map((fb) => (
              <div
                key={fb.title}
                className="rounded-2xl border border-[var(--p-card-border)] bg-[var(--p-card)] p-4 space-y-2"
              >
                <div className="flex items-start justify-between gap-2">
                  <div>
                    <h4 className="text-xs font-bold">{fb.title}</h4>
                    <p className="text-[11px] text-[var(--p-text-muted)]">
                      Benchmark: {fb.benchmark}
                    </p>
                  </div>
                  <span
                    className={`rounded-md px-2 py-0.5 text-[10px] font-bold shrink-0 ${
                      fb.status === 'Healthy'
                        ? 'bg-[var(--p-success-container)] text-[var(--p-on-success-container)]'
                        : fb.status === 'Acceptable'
                          ? 'bg-[var(--p-info-container)] text-[var(--p-on-info-container)]'
                          : 'bg-[var(--p-warning-container)] text-[var(--p-on-warning-container)]'
                    }`}
                  >
                    {fb.status}
                  </span>
                </div>
                <p className="text-sm font-bold font-mono-num text-[var(--p-primary)]">
                  {fb.currentValue}
                </p>
                <p className="text-xs text-[var(--p-text-muted)]">{fb.recommendation}</p>
              </div>
            ))}
          </div>

          <div className="rounded-2xl border border-[var(--p-card-border)] bg-[var(--p-card)] overflow-hidden">
            <div className="px-4 py-3 border-b border-[var(--p-card-border)] flex items-center gap-2">
              <Scale className="w-4 h-4 text-[var(--p-primary)]" />
              <h3 className="text-sm font-bold">Full Banking Ratio Matrix</h3>
            </div>
            <div className="overflow-x-auto">
              <table className="w-full text-left text-xs">
                <thead className="bg-[var(--p-secondary-bg)] text-[var(--p-text-muted)] uppercase text-[10px]">
                  <tr>
                    <th className="px-4 py-2.5">Ratio</th>
                    {computed.map((c) => (
                      <th key={c.y.fyLabel} className="px-4 py-2.5 text-right">
                        {c.y.fyLabel}
                      </th>
                    ))}
                  </tr>
                </thead>
                <tbody className="divide-y divide-[var(--p-card-border)]">
                  <CmaRow label="Current Ratio" vals={computed.map((c) => c.m.currentRatio.toFixed(2))} />
                  <CmaRow label="Quick Ratio" vals={computed.map((c) => c.m.quickRatio.toFixed(2))} />
                  <CmaRow label="DSCR" vals={computed.map((c) => `${c.m.dscr.toFixed(2)}x`)} />
                  <CmaRow label="Interest Coverage (ICR)" vals={computed.map((c) => `${c.m.interestCoverageRatio.toFixed(2)}x`)} />
                  <CmaRow label="TOL / TNW" vals={computed.map((c) => `${c.m.tolToTnwRatio.toFixed(2)}:1`)} />
                  <CmaRow label="Debt / Equity" vals={computed.map((c) => `${c.m.debtEquityRatio.toFixed(2)}:1`)} />
                  <CmaRow label="Debtor Days" vals={computed.map((c) => `${c.m.debtorDays}d`)} />
                  <CmaRow label="Inventory Days" vals={computed.map((c) => `${c.m.inventoryDays}d`)} />
                  <CmaRow label="ROCE %" vals={computed.map((c) => `${c.m.returnOnCapitalEmployedPercent.toFixed(1)}%`)} />
                </tbody>
              </table>
            </div>
          </div>
        </div>
      )}

      {tab === 'funds_emi' && (
        <div className="space-y-4">
          <div className="rounded-2xl border border-[var(--p-card-border)] bg-[var(--p-card)] overflow-hidden">
            <div className="px-4 py-3 border-b border-[var(--p-card-border)]">
              <h3 className="text-sm font-bold">Form VI · Funds Flow Schedule</h3>
            </div>
            <div className="overflow-x-auto">
              <table className="w-full text-left text-xs">
                <thead className="bg-[var(--p-secondary-bg)] text-[var(--p-text-muted)] uppercase text-[10px]">
                  <tr>
                    <th className="px-4 py-2.5">FY</th>
                    <th className="px-4 py-2.5 text-right">Cash Accruals</th>
                    <th className="px-4 py-2.5 text-right">Total Sources</th>
                    <th className="px-4 py-2.5 text-right">Applications</th>
                    <th className="px-4 py-2.5 text-right">Change in NWC</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-[var(--p-card-border)]">
                  {fundsFlow.map((f) => (
                    <tr key={f.fyLabel}>
                      <td className="px-4 py-2.5 font-semibold">{f.fyLabel}</td>
                      <td className="px-4 py-2.5 text-right font-mono-num">
                        {formatPaise(f.cashAccrualsPaise, true)}
                      </td>
                      <td className="px-4 py-2.5 text-right font-mono-num">
                        {formatPaise(f.totalSourcesPaise, true)}
                      </td>
                      <td className="px-4 py-2.5 text-right font-mono-num">
                        {formatPaise(f.totalApplicationsPaise, true)}
                      </td>
                      <td className="px-4 py-2.5 text-right font-mono-num font-bold">
                        {formatPaise(f.changeInNwcPaise, true)}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>

          <div className="rounded-2xl border border-[var(--p-card-border)] bg-[var(--p-card)] overflow-hidden">
            <div className="px-4 py-3 border-b border-[var(--p-card-border)] flex flex-wrap justify-between gap-2">
              <h3 className="text-sm font-bold">
                Form VII · Term Loan Annual Amortization ({project.loanTenorMonths}m @{' '}
                {project.proposedInterestRatePercent}%)
              </h3>
              <span className="text-xs font-mono-num font-bold text-[var(--p-primary)]">
                Monthly EMI: {formatPaise(loanSchedule.monthlyEmiPaise)}
              </span>
            </div>
            <div className="overflow-x-auto">
              <table className="w-full text-left text-xs">
                <thead className="bg-[var(--p-secondary-bg)] text-[var(--p-text-muted)] uppercase text-[10px]">
                  <tr>
                    <th className="px-4 py-2.5">Year</th>
                    <th className="px-4 py-2.5 text-right">Opening Principal</th>
                    <th className="px-4 py-2.5 text-right">Principal Repaid</th>
                    <th className="px-4 py-2.5 text-right">Interest Paid</th>
                    <th className="px-4 py-2.5 text-right">Closing Balance</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-[var(--p-card-border)]">
                  {loanSchedule.annualSummaries.map((a) => (
                    <tr key={a.yearIndex}>
                      <td className="px-4 py-2.5 font-semibold">Year {a.yearIndex}</td>
                      <td className="px-4 py-2.5 text-right font-mono-num">
                        {formatPaise(a.openingPrincipalPaise)}
                      </td>
                      <td className="px-4 py-2.5 text-right font-mono-num text-[var(--p-success)]">
                        {formatPaise(a.totalPrincipalRepaidPaise)}
                      </td>
                      <td className="px-4 py-2.5 text-right font-mono-num">
                        {formatPaise(a.totalInterestPaidPaise)}
                      </td>
                      <td className="px-4 py-2.5 text-right font-mono-num font-bold">
                        {formatPaise(a.closingPrincipalPaise)}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        </div>
      )}

      {tab === 'project' && (
        <div className="rounded-2xl border border-[var(--p-card-border)] bg-[var(--p-card)] p-5 grid grid-cols-1 md:grid-cols-2 gap-4 text-xs">
          <div>
            <label className="block font-medium mb-1">Project Title</label>
            <input
              type="text"
              value={project.projectTitle}
              onChange={(e) => setProject({ ...project, projectTitle: e.target.value })}
              className="w-full rounded-xl border border-[var(--p-card-border)] bg-[var(--p-bg)] px-3 py-2"
            />
          </div>
          <div>
            <label className="block font-medium mb-1">Proposed Bank</label>
            <input
              type="text"
              value={project.proposedBankName}
              onChange={(e) => setProject({ ...project, proposedBankName: e.target.value })}
              className="w-full rounded-xl border border-[var(--p-card-border)] bg-[var(--p-bg)] px-3 py-2"
            />
          </div>
          <div>
            <label className="block font-medium mb-1">Term Loan Amount (₹)</label>
            <input
              type="number"
              value={(project.bankTermLoanPaise / 100).toFixed(0)}
              onChange={(e) =>
                setProject({
                  ...project,
                  bankTermLoanPaise: parsePaiseFromRupees(e.target.value),
                })
              }
              className="w-full rounded-xl border border-[var(--p-card-border)] bg-[var(--p-bg)] px-3 py-2 font-mono-num"
            />
          </div>
          <div>
            <label className="block font-medium mb-1">Proposed Interest Rate (% p.a.)</label>
            <input
              type="number"
              step="0.1"
              value={project.proposedInterestRatePercent}
              onChange={(e) =>
                setProject({
                  ...project,
                  proposedInterestRatePercent: Number.parseFloat(e.target.value) || 9.5,
                })
              }
              className="w-full rounded-xl border border-[var(--p-card-border)] bg-[var(--p-bg)] px-3 py-2 font-mono-num"
            />
          </div>
          <div>
            <label className="block font-medium mb-1">Loan Tenor (Months)</label>
            <input
              type="number"
              value={project.loanTenorMonths}
              onChange={(e) =>
                setProject({
                  ...project,
                  loanTenorMonths: Math.max(12, Number.parseInt(e.target.value, 10) || 60),
                })
              }
              className="w-full rounded-xl border border-[var(--p-card-border)] bg-[var(--p-bg)] px-3 py-2 font-mono-num"
            />
          </div>
          <div>
            <label className="block font-medium mb-1">Moratorium Period (Months)</label>
            <input
              type="number"
              value={project.moratoriumMonths}
              onChange={(e) =>
                setProject({
                  ...project,
                  moratoriumMonths: Math.max(0, Number.parseInt(e.target.value, 10) || 0),
                })
              }
              className="w-full rounded-xl border border-[var(--p-card-border)] bg-[var(--p-bg)] px-3 py-2 font-mono-num"
            />
          </div>
        </div>
      )}
    </div>
  );
};

const CmaRow: React.FC<{ label: string; vals: string[]; bold?: boolean }> = ({
  label,
  vals,
  bold = false,
}) => (
  <tr className={bold ? 'bg-[var(--p-secondary-bg)] font-bold' : ''}>
    <td className="px-4 py-2.5">{label}</td>
    {vals.map((v, idx) => (
      <td key={idx} className="px-4 py-2.5 text-right font-mono-num whitespace-nowrap">
        {v}
      </td>
    ))}
  </tr>
);
