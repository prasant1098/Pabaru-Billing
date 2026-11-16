import React, { useState } from 'react';
import { CheckCircle2, Eye, LogOut, Search, Shield, ShieldAlert, Store } from 'lucide-react';
import { usePabaru } from '../state/PabaruContext';

export const SuperAdminScreen: React.FC = () => {
  const { state, signOutSuperAdmin, toggleTenantStatus, impersonateTenant } = usePabaru();
  const [query, setQuery] = useState('');

  const filtered = state.registeredTenants.filter((t) => {
    const q = query.trim().toLowerCase();
    if (!q) return true;
    return (
      t.businessName.toLowerCase().includes(q) ||
      t.storeCode.toLowerCase().includes(q) ||
      t.ownerName.toLowerCase().includes(q) ||
      t.gstin.toLowerCase().includes(q)
    );
  });

  const activeCount = state.registeredTenants.filter((t) => t.status === 'Active').length;
  const suspendedCount = state.registeredTenants.length - activeCount;

  return (
    <div className="min-h-screen bg-[var(--p-bg)] text-[var(--p-text)]">
      <header className="border-b border-[var(--p-card-border)] bg-[var(--p-header-bg)] text-[var(--p-header-text)] px-4 md:px-6 py-3">
        <div className="max-w-6xl mx-auto flex items-center justify-between gap-3">
          <div className="flex items-center gap-2.5">
            <Shield className="w-5 h-5" />
            <div>
              <h1 className="text-sm md:text-base font-bold">
                Pabaru Platform Oversight (Super Admin)
              </h1>
              <p className="text-[11px] opacity-80">
                Multi-Tenant Store Governance &amp; Provisioning Registry
              </p>
            </div>
          </div>
          <button
            type="button"
            onClick={signOutSuperAdmin}
            className="inline-flex items-center gap-1.5 whitespace-nowrap rounded-lg bg-white/15 border border-white/25 px-3 py-1.5 text-xs font-medium text-white hover:bg-white/25"
          >
            <LogOut className="w-3.5 h-3.5" />
            Exit Admin
          </button>
        </div>
      </header>

      <main className="max-w-6xl mx-auto p-4 md:p-6 space-y-5">
        <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
          <div className="rounded-2xl border border-[var(--p-card-border)] bg-[var(--p-card)] p-4">
            <p className="text-xs text-[var(--p-text-muted)]">Registered Stores</p>
            <p className="text-2xl font-bold font-mono-num mt-1">
              {state.registeredTenants.length}
            </p>
          </div>
          <div className="rounded-2xl border border-[var(--p-card-border)] bg-[var(--p-card)] p-4">
            <p className="text-xs text-[var(--p-text-muted)]">Active Tenants</p>
            <p className="text-2xl font-bold font-mono-num text-[var(--p-success)] mt-1">
              {activeCount}
            </p>
          </div>
          <div className="rounded-2xl border border-[var(--p-card-border)] bg-[var(--p-card)] p-4">
            <p className="text-xs text-[var(--p-text-muted)]">Suspended Tenants</p>
            <p className="text-2xl font-bold font-mono-num text-[var(--p-danger)] mt-1">
              {suspendedCount}
            </p>
          </div>
        </div>

        <div className="rounded-2xl border border-[var(--p-card-border)] bg-[var(--p-card)] p-4 space-y-4">
          <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
            <div className="flex items-center gap-2">
              <Store className="w-4 h-4 text-[var(--p-primary)]" />
              <h2 className="text-sm font-bold">Tenant Directory</h2>
            </div>
            <div className="relative w-full sm:w-72">
              <Search className="w-4 h-4 text-[var(--p-text-muted)] absolute left-3 top-2.5" />
              <input
                type="text"
                value={query}
                onChange={(e) => setQuery(e.target.value)}
                placeholder="Search store code, name, or GSTIN..."
                className="w-full rounded-xl border border-[var(--p-card-border)] bg-[var(--p-bg)] pl-9 pr-3 py-2 text-xs"
              />
            </div>
          </div>

          <div className="overflow-x-auto rounded-xl border border-[var(--p-card-border)]">
            <table className="w-full text-left text-xs">
              <thead className="bg-[var(--p-secondary-bg)] text-[var(--p-text-muted)] uppercase text-[10px]">
                <tr>
                  <th className="px-3 py-2.5">Store Code</th>
                  <th className="px-3 py-2.5">Business &amp; Owner</th>
                  <th className="px-3 py-2.5">GSTIN</th>
                  <th className="px-3 py-2.5">Plan</th>
                  <th className="px-3 py-2.5">Status</th>
                  <th className="px-3 py-2.5 text-right">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-[var(--p-card-border)]">
                {filtered.map((t) => (
                  <tr key={t.storeCode} className="hover:bg-[var(--p-bg)]">
                    <td className="px-3 py-2.5 font-mono-num font-bold">{t.storeCode}</td>
                    <td className="px-3 py-2.5">
                      <p className="font-semibold">{t.businessName}</p>
                      <p className="text-[11px] text-[var(--p-text-muted)]">
                        {t.ownerName} · {t.phone || 'N/A'}
                      </p>
                    </td>
                    <td className="px-3 py-2.5 font-mono-num">{t.gstin || 'Unregistered'}</td>
                    <td className="px-3 py-2.5">{t.plan}</td>
                    <td className="px-3 py-2.5">
                      <span
                        className={`inline-flex items-center gap-1 rounded-md px-2 py-0.5 text-[11px] font-semibold ${
                          t.status === 'Active'
                            ? 'bg-[var(--p-success-container)] text-[var(--p-on-success-container)]'
                            : 'bg-[var(--p-danger-container)] text-[var(--p-on-danger-container)]'
                        }`}
                      >
                        {t.status === 'Active' ? (
                          <CheckCircle2 className="w-3 h-3" />
                        ) : (
                          <ShieldAlert className="w-3 h-3" />
                        )}
                        {t.status}
                      </span>
                    </td>
                    <td className="px-3 py-2.5 text-right">
                      <div className="inline-flex items-center gap-2">
                        <button
                          type="button"
                          onClick={() => toggleTenantStatus(t.storeCode)}
                          className="whitespace-nowrap rounded-lg border border-[var(--p-card-border)] px-2.5 py-1 text-[11px] font-medium hover:bg-[var(--p-secondary-bg)]"
                        >
                          {t.status === 'Active' ? 'Suspend' : 'Activate'}
                        </button>
                        <button
                          type="button"
                          onClick={() => impersonateTenant(t.storeCode)}
                          className="inline-flex items-center gap-1 whitespace-nowrap rounded-lg bg-[var(--p-primary)] px-2.5 py-1 text-[11px] font-semibold text-[var(--p-primary-text)] hover:opacity-90"
                        >
                          <Eye className="w-3 h-3" />
                          Open Store
                        </button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      </main>
    </div>
  );
};
