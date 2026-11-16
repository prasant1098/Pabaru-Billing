import React, { useMemo, useState } from 'react';
import {
  BarChart3,
  BookOpen,
  Building2,
  DatabaseBackup,
  FileSpreadsheet,
  FileText,
  Home,
  Landmark,
  LayoutGrid,
  Lock,
  LogOut,
  Package,
  Palette,
  ReceiptIndianRupee,
  Scale,
  ShieldAlert,
  ShieldCheck,
  UserCheck,
  Users,
} from 'lucide-react';
import { getAvailableFinancialYears } from '../engines/financialEngine';
import { AppScreenId, usePabaru } from '../state/PabaruContext';
import { AuditShieldModal } from './AuditShieldModal';
import { ThemePickerModal } from './ThemePickerModal';

const NAV_ITEMS: Array<{
  id: AppScreenId;
  label: string;
  mobileLabel?: string;
  icon: React.ReactNode;
  section: 'primary' | 'accounting' | 'gst' | 'settings';
  rbacKey?: string;
}> = [
  { id: 'dashboard', label: 'Dashboard', mobileLabel: 'Home', icon: <Home className="w-4 h-4" />, section: 'primary' },
  { id: 'invoices', label: 'GST Invoices Hub', mobileLabel: 'Invoices', icon: <FileText className="w-4 h-4" />, section: 'primary', rbacKey: 'invoices' },
  { id: 'stock', label: 'Stock & Inventory', mobileLabel: 'Stock', icon: <Package className="w-4 h-4" />, section: 'primary', rbacKey: 'stock' },
  { id: 'cash_bank', label: 'Cash & Bank Recon', mobileLabel: 'Bank', icon: <Landmark className="w-4 h-4" />, section: 'primary', rbacKey: 'cash_bank' },
  { id: 'ledgers', label: 'Parties & Khata', icon: <Users className="w-4 h-4" />, section: 'accounting', rbacKey: 'ledgers' },
  { id: 'daybook', label: 'Daybook Register', icon: <BookOpen className="w-4 h-4" />, section: 'accounting', rbacKey: 'daybook' },
  { id: 'reports', label: 'P&L, BS & GSTR-9', icon: <BarChart3 className="w-4 h-4" />, section: 'accounting' },
  { id: 'cma_report', label: 'CMA Loan Dossier', icon: <Scale className="w-4 h-4" />, section: 'accounting' },
  { id: 'gst_assistant', label: 'GSTR-1 / 3B / Rule 88A', icon: <ReceiptIndianRupee className="w-4 h-4" />, section: 'gst' },
  { id: 'gstr2b_recon', label: 'GSTR-2B ITC Match', icon: <FileSpreadsheet className="w-4 h-4" />, section: 'gst' },
  { id: 'business_profile', label: 'Business & RBAC', icon: <Building2 className="w-4 h-4" />, section: 'settings' },
  { id: 'backup', label: 'Encrypted Backup', icon: <DatabaseBackup className="w-4 h-4" />, section: 'settings' },
  { id: 'security', label: 'PIN & Security', icon: <Lock className="w-4 h-4" />, section: 'settings' },
];

export const AppShell: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const {
    state,
    activeScreen,
    setActiveScreen,
    setSelectedFy,
    setActiveBranch,
    switchCurrentUser,
    lockApp,
    signOut,
  } = usePabaru();
  const [themeOpen, setThemeOpen] = useState(false);
  const [auditOpen, setAuditOpen] = useState(false);

  const fyOptions = useMemo(
    () => getAvailableFinancialYears(state.transactions),
    [state.transactions]
  );

  const lowStockCount = useMemo(
    () =>
      state.products.filter(
        (p) => !p.isArchived && p.currentStockMilli <= state.lowStockThreshold * 1000
      ).length,
    [state.products, state.lowStockThreshold]
  );

  const currentUserObj = state.managedUsers.find((u) => u.id === state.currentUserId);
  const isScreenAllowed = (screen: AppScreenId): boolean => {
    if (state.currentUserRole === 'owner') return true;
    if (screen === 'dashboard' || screen === 'more') return true;
    const allowed = new Set(currentUserObj?.allocatedScreens || []);
    if (screen === 'party_unpaid') return allowed.has('ledgers');
    return allowed.has(screen);
  };

  const renderSection = (title: string, sectionKey: string) => {
    const items = NAV_ITEMS.filter((i) => i.section === sectionKey);
    return (
      <div className="mb-4">
        <p className="px-3 mb-1.5 text-[10px] font-semibold uppercase tracking-wider text-[var(--p-text-muted)]">
          {title}
        </p>
        <div className="space-y-0.5">
          {items.map((item) => {
            const active =
              activeScreen === item.id ||
              (item.id === 'ledgers' && activeScreen === 'party_unpaid');
            const allowed = isScreenAllowed(item.id);
            return (
              <button
                key={item.id}
                type="button"
                onClick={() => setActiveScreen(item.id)}
                className={`w-full flex items-center justify-between gap-2 rounded-xl px-3 py-2 text-xs font-medium transition ${
                  active
                    ? 'bg-[var(--p-primary)] text-[var(--p-primary-text)] shadow-sm'
                    : 'text-[var(--p-text)] hover:bg-[var(--p-secondary-bg)]'
                } ${!allowed ? 'opacity-50' : ''}`}
              >
                <span className="flex items-center gap-2.5 min-w-0">
                  <span className="shrink-0">{item.icon}</span>
                  <span className="truncate">{item.label}</span>
                </span>
                {item.id === 'stock' && lowStockCount > 0 && (
                  <span
                    className={`rounded-md px-1.5 py-0.5 text-[10px] font-bold font-mono-num shrink-0 ${
                      active
                        ? 'bg-white/20 text-white'
                        : 'bg-[var(--p-danger-container)] text-[var(--p-on-danger-container)]'
                    }`}
                  >
                    {lowStockCount}
                  </span>
                )}
              </button>
            );
          })}
        </div>
      </div>
    );
  };

  const screenAllowed = isScreenAllowed(activeScreen);

  return (
    <div className="min-h-screen flex bg-[var(--p-bg)] text-[var(--p-text)]">
      {/* Desktop Sidebar */}
      <aside className="no-print hidden md:flex md:w-[248px] md:flex-col md:fixed md:inset-y-0 border-r border-[var(--p-nav-border)] bg-[var(--p-nav-bg)] z-30">
        <div className="px-4 py-3.5 border-b border-[var(--p-nav-border)] flex items-center gap-2.5">
          <div className="w-8 h-8 rounded-lg bg-[var(--p-primary)] text-[var(--p-primary-text)] flex items-center justify-center font-bold text-sm shrink-0">
            P
          </div>
          <div className="min-w-0">
            <p className="text-sm font-bold text-[var(--p-text)] truncate">Pabaru Billing</p>
            <p className="text-[11px] text-[var(--p-text-muted)] truncate">
              {state.profile.storeCode} · {state.profile.registrationType.toUpperCase()}
            </p>
          </div>
        </div>

        <nav className="flex-1 overflow-y-auto px-2.5 py-3">
          {renderSection('Operations', 'primary')}
          {renderSection('Accounting & CMA', 'accounting')}
          {renderSection('GST Filings', 'gst')}
          {renderSection('Administration', 'settings')}
        </nav>

        <div className="p-3 border-t border-[var(--p-nav-border)] flex items-center justify-between gap-2">
          <button
            type="button"
            onClick={lockApp}
            className="inline-flex items-center gap-1.5 whitespace-nowrap rounded-lg border border-[var(--p-card-border)] px-2.5 py-1.5 text-xs font-medium text-[var(--p-text)] hover:bg-[var(--p-secondary-bg)]"
          >
            <Lock className="w-3.5 h-3.5" />
            Lock
          </button>
          <button
            type="button"
            onClick={signOut}
            className="inline-flex items-center gap-1.5 whitespace-nowrap rounded-lg border border-[var(--p-card-border)] px-2.5 py-1.5 text-xs font-medium text-[var(--p-danger)] hover:bg-[var(--p-danger-container)]"
          >
            <LogOut className="w-3.5 h-3.5" />
            Sign Out
          </button>
        </div>
      </aside>

      {/* Main Column */}
      <div className="flex-1 flex flex-col md:pl-[248px] min-w-0">
        {/* Top Bar */}
        <header className="no-print sticky top-0 z-20 border-b border-[var(--p-card-border)] bg-[var(--p-header-bg)] text-[var(--p-header-text)] px-3 md:px-6 py-2.5">
          <div className="max-w-7xl mx-auto flex flex-wrap items-center justify-between gap-2">
            <div className="flex items-center gap-2.5 min-w-0">
              <div className="min-w-0">
                <h1 className="text-sm md:text-base font-bold truncate">
                  {state.profile.businessName}
                </h1>
                <p className="text-[11px] opacity-80 truncate">
                  {state.profile.gstin ? `GSTIN ${state.profile.gstin}` : 'Unregistered Store'} ·{' '}
                  {state.currentUserName} ({state.currentUserRole})
                </p>
              </div>
            </div>

            <div className="flex items-center gap-2 flex-wrap">
              {state.branches.length > 1 && (
                <select
                  value={state.activeBranchId}
                  onChange={(e) => setActiveBranch(e.target.value)}
                  aria-label="Active Branch"
                  className="rounded-lg bg-white/15 border border-white/25 px-2.5 py-1.5 text-xs font-medium text-white focus:outline-none"
                >
                  {state.branches.map((b) => (
                    <option key={b.id} value={b.id} className="text-slate-900">
                      {b.name}
                    </option>
                  ))}
                </select>
              )}

              <select
                value={state.selectedFy}
                onChange={(e) => setSelectedFy(e.target.value)}
                aria-label="Financial Year"
                className="rounded-lg bg-white/15 border border-white/25 px-2.5 py-1.5 text-xs font-medium text-white focus:outline-none"
              >
                {fyOptions.map((fy) => (
                  <option key={fy} value={fy} className="text-slate-900">
                    {fy}
                  </option>
                ))}
              </select>

              <select
                value={state.currentUserId}
                onChange={(e) => switchCurrentUser(e.target.value)}
                aria-label="Switch Active Counter User"
                className="hidden sm:block rounded-lg bg-white/15 border border-white/25 px-2.5 py-1.5 text-xs font-medium text-white focus:outline-none"
              >
                {state.managedUsers
                  .filter((u) => u.isActive)
                  .map((u) => (
                    <option key={u.id} value={u.id} className="text-slate-900">
                      {u.name} ({u.role})
                    </option>
                  ))}
              </select>

              <button
                type="button"
                onClick={() => setAuditOpen(true)}
                className={`inline-flex items-center gap-1.5 whitespace-nowrap shrink-0 rounded-lg px-2.5 py-1.5 text-xs font-semibold border ${
                  state.isChainValid
                    ? 'bg-emerald-500/20 border-emerald-300/40 text-white'
                    : 'bg-red-500/30 border-red-300/60 text-white'
                }`}
                title="Inspect SHA-256 Tamper-Evident Audit Chain"
              >
                {state.isChainValid ? (
                  <ShieldCheck className="w-3.5 h-3.5" />
                ) : (
                  <ShieldAlert className="w-3.5 h-3.5" />
                )}
                <span className="hidden sm:inline">Audit Shield</span>
              </button>

              <button
                type="button"
                onClick={() => setThemeOpen(true)}
                className="inline-flex items-center gap-1.5 whitespace-nowrap shrink-0 rounded-lg bg-white/15 border border-white/25 px-2.5 py-1.5 text-xs font-medium text-white hover:bg-white/25"
                title="Change Theme"
              >
                <Palette className="w-3.5 h-3.5" />
                <span className="hidden lg:inline">Theme</span>
              </button>
            </div>
          </div>
        </header>

        {/* Workspace Content */}
        <main className="flex-1 p-3 md:p-6 pb-24 md:pb-8 max-w-7xl w-full mx-auto">
          {screenAllowed ? (
            children
          ) : (
            <div className="max-w-lg mx-auto mt-10 rounded-2xl border border-[var(--p-card-border)] bg-[var(--p-card)] p-6 text-center space-y-3">
              <div className="w-12 h-12 rounded-xl bg-[var(--p-warning-container)] text-[var(--p-on-warning-container)] mx-auto flex items-center justify-center">
                <UserCheck className="w-6 h-6" />
              </div>
              <h2 className="text-base font-bold text-[var(--p-text)]">
                Counter Access Restricted
              </h2>
              <p className="text-xs text-[var(--p-text-muted)]">
                Active staff profile <strong>{state.currentUserName}</strong> does not have RBAC
                permission for this screen. Switch to an Owner profile or ask the business owner to
                allocate this module in Business &amp; RBAC settings.
              </p>
              <button
                type="button"
                onClick={() => setActiveScreen('dashboard')}
                className="inline-flex items-center gap-1.5 rounded-xl bg-[var(--p-primary)] px-4 py-2 text-xs font-semibold text-[var(--p-primary-text)]"
              >
                Return to Dashboard
              </button>
            </div>
          )}
        </main>

        {/* Mobile Bottom Navigation */}
        <nav className="no-print md:hidden fixed bottom-0 inset-x-0 z-30 border-t border-[var(--p-nav-border)] bg-[var(--p-nav-bg)] px-2 py-1.5 grid grid-cols-5 gap-1">
          {[
            { id: 'dashboard' as AppScreenId, label: 'Home', icon: <Home className="w-4 h-4" /> },
            { id: 'invoices' as AppScreenId, label: 'Invoices', icon: <FileText className="w-4 h-4" /> },
            { id: 'stock' as AppScreenId, label: 'Inventory', icon: <Package className="w-4 h-4" />, badge: lowStockCount },
            { id: 'cash_bank' as AppScreenId, label: 'Bank', icon: <Landmark className="w-4 h-4" /> },
            { id: 'more' as AppScreenId, label: 'Menu', icon: <LayoutGrid className="w-4 h-4" /> },
          ].map((tab) => {
            const active = activeScreen === tab.id;
            return (
              <button
                key={tab.id}
                type="button"
                onClick={() => setActiveScreen(tab.id)}
                className={`relative flex flex-col items-center justify-center rounded-xl py-1.5 text-[11px] font-medium ${
                  active ? 'text-[var(--p-nav-active)] bg-[var(--p-secondary-bg)]' : 'text-[var(--p-nav-inactive)]'
                }`}
              >
                {tab.icon}
                <span className="mt-0.5 truncate">{tab.label}</span>
                {!!tab.badge && tab.badge > 0 && (
                  <span className="absolute top-1 right-3 min-w-[16px] h-4 px-1 rounded-full bg-[var(--p-danger)] text-white text-[9px] font-bold flex items-center justify-center">
                    {tab.badge}
                  </span>
                )}
              </button>
            );
          })}
        </nav>
      </div>

      <ThemePickerModal open={themeOpen} onClose={() => setThemeOpen(false)} />
      <AuditShieldModal open={auditOpen} onClose={() => setAuditOpen(false)} />
    </div>
  );
};
