import React, { useState } from 'react';
import {
  ArrowRight,
  Building2,
  CheckCircle2,
  KeyRound,
  Shield,
  Sparkles,
  Store,
} from 'lucide-react';
import { INDIAN_STATES, validateGstin } from '../engines/financialEngine';
import { usePabaru } from '../state/PabaruContext';
import { RegistrationType } from '../types/models';

export const AuthScreen: React.FC = () => {
  const { signIn, signInDemo, registerBusiness, signInSuperAdmin } = usePabaru();
  const [tab, setTab] = useState<'signin' | 'register' | 'superadmin'>('signin');

  // Sign-In state
  const [storeCode, setStoreCode] = useState('TL-7319');
  const [pin, setPin] = useState('1234');

  // Register state
  const [regName, setRegName] = useState('');
  const [regOwner, setRegOwner] = useState('');
  const [regPhone, setRegPhone] = useState('');
  const [regGstin, setRegGstin] = useState('');
  const [regState, setRegState] = useState('27');
  const [regType, setRegType] = useState<RegistrationType>('regular');
  const [regPin, setRegPin] = useState('1234');

  // SuperAdmin state
  const [adminPin, setAdminPin] = useState('');

  const gstinCheck = validateGstin(regGstin, true);

  const handleSignIn = (e: React.FormEvent) => {
    e.preventDefault();
    signIn(storeCode, pin);
  };

  const handleRegister = (e: React.FormEvent) => {
    e.preventDefault();
    if (!regName.trim() || !regOwner.trim()) return;
    if (regGstin.trim() && !gstinCheck.isValid) return;
    registerBusiness({
      businessName: regName,
      ownerName: regOwner,
      phone: regPhone,
      gstin: regGstin,
      homeStateCode: gstStateOrFromGstin(regGstin, regState),
      registrationType: regType,
      pin: regPin,
    });
  };

  const handleSuperAdmin = (e: React.FormEvent) => {
    e.preventDefault();
    signInSuperAdmin(adminPin);
  };

  return (
    <div className="min-h-screen bg-[var(--p-bg)] text-[var(--p-text)] flex flex-col justify-center p-4 md:p-8">
      <div className="max-w-4xl w-full mx-auto grid grid-cols-1 md:grid-cols-12 gap-6 items-stretch">
        {/* Brand & Value Proposition */}
        <div className="md:col-span-5 rounded-2xl bg-[var(--p-header-bg)] text-[var(--p-header-text)] p-6 flex flex-col justify-between">
          <div className="space-y-4">
            <div className="flex items-center gap-2.5">
              <div className="w-10 h-10 rounded-xl bg-white/15 flex items-center justify-center font-bold text-lg">
                P
              </div>
              <div>
                <h1 className="text-lg font-bold tracking-tight">Pabaru Billing</h1>
                <p className="text-xs opacity-80">GST Accounting · Khata · CMA · Recon</p>
              </div>
            </div>

            <h2 className="text-xl font-bold leading-snug pt-2">
              Offline-first GST billing, inventory &amp; banking ledger for Indian businesses.
            </h2>

            <ul className="space-y-2.5 text-xs opacity-90">
              <li className="flex items-start gap-2">
                <CheckCircle2 className="w-4 h-4 shrink-0 mt-0.5 text-emerald-300" />
                <span>Tax Invoices, Bills of Supply, Rule 26 round-off &amp; dynamic UPI QR</span>
              </li>
              <li className="flex items-start gap-2">
                <CheckCircle2 className="w-4 h-4 shrink-0 mt-0.5 text-emerald-300" />
                <span>GSTR-1, GSTR-3B Rule 88A optimizer, CMP-08 &amp; GSTR-2B ITC matching</span>
              </li>
              <li className="flex items-start gap-2">
                <CheckCircle2 className="w-4 h-4 shrink-0 mt-0.5 text-emerald-300" />
                <span>RBI CMA Loan Project Report (Forms I–VII) &amp; SHA-256 audit chain</span>
              </li>
            </ul>
          </div>

          <div className="mt-6 pt-4 border-t border-white/15">
            <button
              type="button"
              onClick={signInDemo}
              className="w-full inline-flex items-center justify-center gap-2 rounded-xl bg-white text-slate-900 px-4 py-2.5 text-xs font-bold hover:bg-slate-100 transition"
            >
              <Sparkles className="w-4 h-4 text-teal-600" />
              Launch Instant Demo Store (TL-7319)
            </button>
          </div>
        </div>

        {/* Auth Panel */}
        <div className="md:col-span-7 rounded-2xl border border-[var(--p-card-border)] bg-[var(--p-card)] p-6 shadow-sm">
          <div className="flex gap-1.5 p-1 rounded-xl bg-[var(--p-bg)] border border-[var(--p-card-border)] mb-5">
            {[
              { id: 'signin' as const, label: 'Store Sign In', icon: <Store className="w-3.5 h-3.5" /> },
              { id: 'register' as const, label: 'Register Business', icon: <Building2 className="w-3.5 h-3.5" /> },
              { id: 'superadmin' as const, label: 'Super Admin', icon: <Shield className="w-3.5 h-3.5" /> },
            ].map((item) => (
              <button
                key={item.id}
                type="button"
                onClick={() => setTab(item.id)}
                className={`flex-1 inline-flex items-center justify-center gap-1.5 whitespace-nowrap rounded-lg px-2.5 py-2 text-xs font-semibold transition ${
                  tab === item.id
                    ? 'bg-[var(--p-primary)] text-[var(--p-primary-text)]'
                    : 'text-[var(--p-text-muted)] hover:text-[var(--p-text)]'
                }`}
              >
                {item.icon}
                <span>{item.label}</span>
              </button>
            ))}
          </div>

          {tab === 'signin' && (
            <form onSubmit={handleSignIn} className="space-y-4">
              <div>
                <label className="block text-xs font-medium text-[var(--p-text)] mb-1">
                  Store Code
                </label>
                <input
                  type="text"
                  required
                  value={storeCode}
                  onChange={(e) => setStoreCode(e.target.value.toUpperCase())}
                  placeholder="TL-7319"
                  className="w-full rounded-xl border border-[var(--p-card-border)] bg-[var(--p-bg)] px-3.5 py-2.5 text-sm font-mono-num uppercase text-[var(--p-text)] focus:outline-none focus:ring-1 focus:ring-[var(--p-primary)]"
                />
              </div>

              <div>
                <label className="block text-xs font-medium text-[var(--p-text)] mb-1">
                  Store Security PIN
                </label>
                <input
                  type="password"
                  required
                  value={pin}
                  onChange={(e) => setPin(e.target.value)}
                  placeholder="Enter 4-6 digit PIN (Demo: 1234)"
                  className="w-full rounded-xl border border-[var(--p-card-border)] bg-[var(--p-bg)] px-3.5 py-2.5 text-sm font-mono-num text-[var(--p-text)] focus:outline-none focus:ring-1 focus:ring-[var(--p-primary)]"
                />
              </div>

              <div className="rounded-xl border border-[var(--p-card-border)] bg-[var(--p-bg)] px-3.5 py-2.5 text-xs text-[var(--p-text-muted)]">
                Demo credentials: Store Code <strong className="text-[var(--p-text)]">TL-7319</strong> · PIN{' '}
                <strong className="text-[var(--p-text)]">1234</strong>
              </div>

              <button
                type="submit"
                className="w-full inline-flex items-center justify-center gap-2 rounded-xl bg-[var(--p-primary)] px-4 py-2.5 text-xs font-semibold text-[var(--p-primary-text)] hover:opacity-90"
              >
                <span>Sign In to Workspace</span>
                <ArrowRight className="w-4 h-4" />
              </button>
            </form>
          )}

          {tab === 'register' && (
            <form onSubmit={handleRegister} className="space-y-3">
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                <div>
                  <label className="block text-xs font-medium text-[var(--p-text)] mb-1">
                    Business Legal / Trade Name *
                  </label>
                  <input
                    type="text"
                    required
                    value={regName}
                    onChange={(e) => setRegName(e.target.value)}
                    placeholder="e.g. Laxmi Agro & Provisions"
                    className="w-full rounded-xl border border-[var(--p-card-border)] bg-[var(--p-bg)] px-3 py-2 text-xs text-[var(--p-text)]"
                  />
                </div>
                <div>
                  <label className="block text-xs font-medium text-[var(--p-text)] mb-1">
                    Owner / Signatory Name *
                  </label>
                  <input
                    type="text"
                    required
                    value={regOwner}
                    onChange={(e) => setRegOwner(e.target.value)}
                    placeholder="e.g. Suresh Kulkarni"
                    className="w-full rounded-xl border border-[var(--p-card-border)] bg-[var(--p-bg)] px-3 py-2 text-xs text-[var(--p-text)]"
                  />
                </div>
              </div>

              <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                <div>
                  <label className="block text-xs font-medium text-[var(--p-text)] mb-1">
                    Mobile Number
                  </label>
                  <input
                    type="tel"
                    value={regPhone}
                    onChange={(e) => setRegPhone(e.target.value)}
                    placeholder="10-digit mobile"
                    className="w-full rounded-xl border border-[var(--p-card-border)] bg-[var(--p-bg)] px-3 py-2 text-xs text-[var(--p-text)]"
                  />
                </div>
                <div>
                  <label className="block text-xs font-medium text-[var(--p-text)] mb-1">
                    GST Registration Type
                  </label>
                  <select
                    value={regType}
                    onChange={(e) => setRegType(e.target.value as RegistrationType)}
                    className="w-full rounded-xl border border-[var(--p-card-border)] bg-[var(--p-bg)] px-3 py-2 text-xs text-[var(--p-text)]"
                  >
                    <option value="regular">Regular GST Taxpayer</option>
                    <option value="composition">Composition Dealer (CMP-08)</option>
                    <option value="unregistered">Unregistered / Non-GST</option>
                  </select>
                </div>
              </div>

              <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                <div>
                  <label className="block text-xs font-medium text-[var(--p-text)] mb-1">
                    GSTIN (Optional)
                  </label>
                  <input
                    type="text"
                    value={regGstin}
                    onChange={(e) => setRegGstin(e.target.value.toUpperCase())}
                    placeholder="15-char GSTIN"
                    maxLength={15}
                    className="w-full rounded-xl border border-[var(--p-card-border)] bg-[var(--p-bg)] px-3 py-2 text-xs font-mono-num uppercase text-[var(--p-text)]"
                  />
                  {regGstin.trim() && (
                    <p
                      className={`text-[11px] mt-1 ${
                        gstinCheck.isValid ? 'text-[var(--p-success)]' : 'text-[var(--p-danger)]'
                      }`}
                    >
                      {gstinCheck.message}
                    </p>
                  )}
                </div>
                <div>
                  <label className="block text-xs font-medium text-[var(--p-text)] mb-1">
                    Home State (POS)
                  </label>
                  <select
                    value={regState}
                    onChange={(e) => setRegState(e.target.value)}
                    className="w-full rounded-xl border border-[var(--p-card-border)] bg-[var(--p-bg)] px-3 py-2 text-xs text-[var(--p-text)]"
                  >
                    {INDIAN_STATES.map((s) => (
                      <option key={s.code} value={s.code}>
                        {s.code} - {s.name}
                      </option>
                    ))}
                  </select>
                </div>
              </div>

              <div>
                <label className="block text-xs font-medium text-[var(--p-text)] mb-1">
                  Set Store PIN (4–6 digits)
                </label>
                <input
                  type="password"
                  required
                  value={regPin}
                  onChange={(e) => setRegPin(e.target.value)}
                  className="w-full rounded-xl border border-[var(--p-card-border)] bg-[var(--p-bg)] px-3 py-2 text-xs font-mono-num text-[var(--p-text)]"
                />
              </div>

              <button
                type="submit"
                disabled={Boolean(regGstin.trim() && !gstinCheck.isValid)}
                className="w-full inline-flex items-center justify-center gap-2 rounded-xl bg-[var(--p-primary)] px-4 py-2.5 text-xs font-semibold text-[var(--p-primary-text)] hover:opacity-90 disabled:opacity-50"
              >
                <span>Create Business Workspace</span>
                <ArrowRight className="w-4 h-4" />
              </button>
            </form>
          )}

          {tab === 'superadmin' && (
            <form onSubmit={handleSuperAdmin} className="space-y-4">
              <div className="rounded-xl border border-[var(--p-card-border)] bg-[var(--p-bg)] p-3.5 text-xs text-[var(--p-text-muted)]">
                Platform Oversight Portal manages multi-store tenant provisioning, status controls,
                and impersonation diagnostics. Default governance PIN:{' '}
                <strong className="text-[var(--p-text)] font-mono-num">998877</strong>.
              </div>
              <div>
                <label className="block text-xs font-medium text-[var(--p-text)] mb-1">
                  Master Governance PIN
                </label>
                <div className="relative">
                  <KeyRound className="w-4 h-4 text-[var(--p-text-muted)] absolute left-3 top-2.5" />
                  <input
                    type="password"
                    required
                    value={adminPin}
                    onChange={(e) => setAdminPin(e.target.value)}
                    placeholder="Enter Super Admin PIN"
                    className="w-full rounded-xl border border-[var(--p-card-border)] bg-[var(--p-bg)] pl-9 pr-3 py-2.5 text-sm font-mono-num text-[var(--p-text)]"
                  />
                </div>
              </div>
              <button
                type="submit"
                className="w-full inline-flex items-center justify-center gap-2 rounded-xl bg-[var(--p-primary)] px-4 py-2.5 text-xs font-semibold text-[var(--p-primary-text)] hover:opacity-90"
              >
                <Shield className="w-4 h-4" />
                <span>Enter Platform Oversight</span>
              </button>
            </form>
          )}
        </div>
      </div>
    </div>
  );
};

function gstStateOrFromGstin(gstin: string, fallback: string): string {
  const clean = gstin.trim();
  if (clean.length >= 2 && /^\d{2}/.test(clean)) return clean.slice(0, 2);
  return fallback;
}
