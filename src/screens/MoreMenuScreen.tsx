import React, { useState } from 'react';
import {
  BarChart3,
  BookOpen,
  Building2,
  ChevronRight,
  DatabaseBackup,
  FileSpreadsheet,
  FileText,
  Landmark,
  Lock,
  LogOut,
  Package,
  Palette,
  ReceiptIndianRupee,
  Scale,
  ShieldCheck,
  Users,
} from 'lucide-react';
import { ThemePickerModal } from '../components/ThemePickerModal';
import { AppScreenId, usePabaru } from '../state/PabaruContext';

interface MenuCardItem {
  id: AppScreenId;
  title: string;
  subtitle: string;
  icon: React.ReactNode;
  group: 'Operations' | 'Accounting & CMA' | 'GST & Compliance' | 'Settings';
}

const MENU_ITEMS: MenuCardItem[] = [
  {
    id: 'invoices',
    title: 'GST Invoices Hub',
    subtitle: 'Create Tax Invoices, Bills of Supply, Credit Notes & Quotations',
    icon: <FileText className="w-4 h-4" />,
    group: 'Operations',
  },
  {
    id: 'daybook',
    title: 'Daybook Register',
    subtitle: 'Daily chronological voucher book with voiding & quick expenses',
    icon: <BookOpen className="w-4 h-4" />,
    group: 'Operations',
  },
  {
    id: 'ledgers',
    title: 'Parties & Khata',
    subtitle: 'Customer receivables, supplier payables, FIFO bills & WhatsApp reminders',
    icon: <Users className="w-4 h-4" />,
    group: 'Operations',
  },
  {
    id: 'stock',
    title: 'Stock & Inventory',
    subtitle: 'Catalog, batch expiry tracking, adjustments & multi-godown transfers',
    icon: <Package className="w-4 h-4" />,
    group: 'Operations',
  },
  {
    id: 'cash_bank',
    title: 'Cash & Bank Recon',
    subtitle: 'Live drawer, bank accounts, UPI QRs & CSV/OFX auto-matching',
    icon: <Landmark className="w-4 h-4" />,
    group: 'Operations',
  },
  {
    id: 'reports',
    title: 'P&L, BS & GSTR-9',
    subtitle: 'FY Profit & Loss, Balance Sheet, Quarterly GSTR-9 & A4 print sheets',
    icon: <BarChart3 className="w-4 h-4" />,
    group: 'Accounting & CMA',
  },
  {
    id: 'cma_report',
    title: 'CMA Loan Dossier',
    subtitle: 'RBI CMA Forms I–VII, MPBF Nayak/Tandon, DSCR & Term Loan schedule',
    icon: <Scale className="w-4 h-4" />,
    group: 'Accounting & CMA',
  },
  {
    id: 'gst_assistant',
    title: 'GSTR-1 / 3B / Rule 88A',
    subtitle: 'Statutory GSTR-1/3B/CMP-08 JSON, Rule 88A optimizer & Excel import',
    icon: <ReceiptIndianRupee className="w-4 h-4" />,
    group: 'GST & Compliance',
  },
  {
    id: 'gstr2b_recon',
    title: 'GSTR-2B ITC Match',
    subtitle: 'Match GSTR-2B portal JSON/CSV against purchase register for Rule 36(4)',
    icon: <FileSpreadsheet className="w-4 h-4" />,
    group: 'GST & Compliance',
  },
  {
    id: 'business_profile',
    title: 'Business & RBAC',
    subtitle: 'GSTIN, entity profile, multi-branch setup & counter staff permissions',
    icon: <Building2 className="w-4 h-4" />,
    group: 'Settings',
  },
  {
    id: 'backup',
    title: 'Encrypted Backup',
    subtitle: 'AES-256-GCM PBKDF2 encrypted or plain JSON backup export & restore',
    icon: <DatabaseBackup className="w-4 h-4" />,
    group: 'Settings',
  },
  {
    id: 'security',
    title: 'PIN & Security',
    subtitle: 'Owner App Lock PIN, 12-word recovery, Manager PIN & SHA-256 audit check',
    icon: <Lock className="w-4 h-4" />,
    group: 'Settings',
  },
];

export const MoreMenuScreen: React.FC = () => {
  const { state, setActiveScreen, switchCurrentUser, lockApp, signOut } = usePabaru();
  const [themeOpen, setThemeOpen] = useState(false);

  const groups: Array<MenuCardItem['group']> = [
    'Operations',
    'Accounting & CMA',
    'GST & Compliance',
    'Settings',
  ];

  const currentUserObj = state.managedUsers.find((u) => u.id === state.currentUserId);
  const allowedSet = new Set(currentUserObj?.allocatedScreens || []);

  return (
    <div className="space-y-6">
      {/* Header Banner */}
      <div className="rounded-2xl border border-[var(--p-card-border)] bg-[var(--p-card)] p-4 flex flex-wrap items-center justify-between gap-3">
        <div>
          <h2 className="text-base font-bold text-[var(--p-text)]">All Modules &amp; Controls</h2>
          <p className="text-xs text-[var(--p-text-muted)]">
            {state.profile.businessName} · Store Code {state.profile.storeCode} ·{' '}
            {state.currentUserName} ({state.currentUserRole})
          </p>
        </div>

        <div className="flex flex-wrap items-center gap-2">
          <select
            value={state.currentUserId}
            onChange={(e) => switchCurrentUser(e.target.value)}
            aria-label="Switch Active Counter User"
            className="rounded-xl border border-[var(--p-card-border)] bg-[var(--p-bg)] px-3 py-2 text-xs font-medium text-[var(--p-text)]"
          >
            {state.managedUsers
              .filter((u) => u.isActive)
              .map((u) => (
                <option key={u.id} value={u.id}>
                  {u.name} ({u.role})
                </option>
              ))}
          </select>

          <button
            type="button"
            onClick={() => setThemeOpen(true)}
            className="inline-flex items-center gap-1.5 whitespace-nowrap rounded-xl border border-[var(--p-card-border)] bg-[var(--p-secondary-bg)] px-3 py-2 text-xs font-semibold text-[var(--p-text)] hover:opacity-90"
          >
            <Palette className="w-3.5 h-3.5 text-[var(--p-primary)]" />
            Theme
          </button>

          <button
            type="button"
            onClick={lockApp}
            className="inline-flex items-center gap-1.5 whitespace-nowrap rounded-xl border border-[var(--p-card-border)] bg-[var(--p-bg)] px-3 py-2 text-xs font-semibold text-[var(--p-text)] hover:bg-[var(--p-secondary-bg)]"
          >
            <Lock className="w-3.5 h-3.5" />
            Lock App
          </button>

          <button
            type="button"
            onClick={signOut}
            className="inline-flex items-center gap-1.5 whitespace-nowrap rounded-xl border border-[var(--p-card-border)] bg-[var(--p-danger-container)] px-3 py-2 text-xs font-semibold text-[var(--p-on-danger-container)]"
          >
            <LogOut className="w-3.5 h-3.5" />
            Sign Out
          </button>
        </div>
      </div>

      {/* Grouped Launcher Grid */}
      {groups.map((grp) => {
        const items = MENU_ITEMS.filter((m) => m.group === grp);
        return (
          <section key={grp} className="space-y-2.5">
            <h3 className="text-xs font-semibold uppercase tracking-wider text-[var(--p-text-muted)]">
              {grp}
            </h3>
            <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-3">
              {items.map((item) => {
                const allowed =
                  state.currentUserRole === 'owner' || allowedSet.has(item.id);
                return (
                  <button
                    key={item.id}
                    type="button"
                    onClick={() => setActiveScreen(item.id)}
                    className={`group text-left rounded-2xl border border-[var(--p-card-border)] bg-[var(--p-card)] p-4 flex items-start justify-between gap-3 transition hover:border-[var(--p-primary)] ${
                      !allowed ? 'opacity-60' : ''
                    }`}
                  >
                    <div className="flex items-start gap-3 min-w-0">
                      <div className="w-9 h-9 rounded-xl bg-[var(--p-secondary-bg)] text-[var(--p-primary)] flex items-center justify-center shrink-0">
                        {item.icon}
                      </div>
                      <div className="min-w-0">
                        <div className="flex items-center gap-1.5">
                          <p className="text-sm font-semibold text-[var(--p-text)] truncate">
                            {item.title}
                          </p>
                          {!allowed && (
                            <span className="rounded px-1.5 py-0.5 text-[10px] font-semibold bg-[var(--p-warning-container)] text-[var(--p-on-warning-container)]">
                              RBAC
                            </span>
                          )}
                        </div>
                        <p className="mt-0.5 text-xs text-[var(--p-text-muted)] line-clamp-2">
                          {item.subtitle}
                        </p>
                      </div>
                    </div>
                    <ChevronRight className="w-4 h-4 text-[var(--p-text-muted)] shrink-0 mt-1 group-hover:text-[var(--p-primary)]" />
                  </button>
                );
              })}
            </div>
          </section>
        );
      })}

      {/* Audit Status Card */}
      <div className="rounded-2xl border border-[var(--p-card-border)] bg-[var(--p-card)] p-4 flex flex-wrap items-center justify-between gap-3">
        <div className="flex items-center gap-3">
          <div
            className={`w-9 h-9 rounded-xl flex items-center justify-center ${
              state.isChainValid
                ? 'bg-[var(--p-success-container)] text-[var(--p-on-success-container)]'
                : 'bg-[var(--p-danger-container)] text-[var(--p-on-danger-container)]'
            }`}
          >
            <ShieldCheck className="w-4 h-4" />
          </div>
          <div>
            <p className="text-xs font-semibold text-[var(--p-text)]">
              Tamper-Evident Audit Chain: {state.isChainValid ? 'Verified Intact' : 'Integrity Warning'}
            </p>
            <p className="text-[11px] text-[var(--p-text-muted)]">
              {state.auditLogs.length} SHA-256 linked records stored locally in this browser
            </p>
          </div>
        </div>
        <button
          type="button"
          onClick={() => setActiveScreen('security')}
          className="rounded-xl border border-[var(--p-card-border)] px-3 py-1.5 text-xs font-semibold text-[var(--p-primary)] hover:bg-[var(--p-secondary-bg)]"
        >
          Inspect Security &amp; Chain
        </button>
      </div>

      <ThemePickerModal open={themeOpen} onClose={() => setThemeOpen(false)} />
    </div>
  );
};
