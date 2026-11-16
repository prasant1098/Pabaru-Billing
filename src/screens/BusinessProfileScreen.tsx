import React, { useMemo, useState } from 'react';
import {
  Building2,
  CheckCircle2,
  GitBranch,
  Palette,
  Plus,
  Save,
  Shield,
  Trash2,
  UserCheck,
  UserPlus,
  XCircle,
} from 'lucide-react';
import { ThemePickerModal } from '../components/ThemePickerModal';
import {
  INDIAN_STATES,
  formatPaise,
  parsePaiseFromRupees,
  validateGstin,
  validatePan,
  validatePhone,
} from '../engines/financialEngine';
import { usePabaru } from '../state/PabaruContext';
import {
  ALLOCATABLE_SCREENS,
  BUSINESS_ENTITY_META,
  BusinessEntityType,
  ManagedUser,
  RegistrationType,
} from '../types/models';

export const BusinessProfileScreen: React.FC = () => {
  const {
    state,
    updateProfile,
    addBranch,
    setActiveBranch,
    updateLowStockThreshold,
    addManagedUser,
    updateManagedUser,
    deleteManagedUser,
    switchCurrentUser,
    addToast,
  } = usePabaru();

  const [tab, setTab] = useState<'profile' | 'branches' | 'staff'>('profile');
  const [themeOpen, setThemeOpen] = useState(false);

  // Profile form state
  const [businessName, setBusinessName] = useState(state.profile.businessName);
  const [entityType, setEntityType] = useState<BusinessEntityType>(state.profile.entityType);
  const [ownerName, setOwnerName] = useState(state.profile.ownerName);
  const [businessType, setBusinessType] = useState(state.profile.businessType);
  const [registrationType, setRegistrationType] = useState<RegistrationType>(
    state.profile.registrationType
  );
  const [homeStateCode, setHomeStateCode] = useState(state.profile.homeStateCode);
  const [filingFrequency, setFilingFrequency] = useState<'Monthly' | 'Quarterly'>(
    state.profile.filingFrequency
  );
  const [gstin, setGstin] = useState(state.profile.gstin);
  const [pan, setPan] = useState(state.profile.pan);
  const [udyamNumber, setUdyamNumber] = useState(state.profile.udyamNumber);
  const [phone, setPhone] = useState(state.profile.phone);
  const [email, setEmail] = useState(state.profile.email);
  const [address, setAddress] = useState(state.profile.address);
  const [openingCashRupees, setOpeningCashRupees] = useState(
    (state.profile.openingCashBalancePaise / 100).toFixed(2)
  );
  const [openingCapitalRupees, setOpeningCapitalRupees] = useState(
    (state.profile.openingCapitalPaise / 100).toFixed(2)
  );
  const [lowStockInput, setLowStockInput] = useState(String(state.lowStockThreshold));

  // Branch form
  const [branchName, setBranchName] = useState('');
  const [branchCode, setBranchCode] = useState('');
  const [branchAddress, setBranchAddress] = useState('');

  // Staff RBAC form
  const [staffName, setStaffName] = useState('');
  const [staffRole, setStaffRole] = useState<'owner' | 'staff'>('staff');
  const [staffPin, setStaffPin] = useState('1111');
  const [staffScreens, setStaffScreens] = useState<string[]>(['invoices', 'daybook', 'stock']);

  const gstinCheck = useMemo(() => validateGstin(gstin, true, pan), [gstin, pan]);
  const panCheck = useMemo(() => validatePan(pan, true), [pan]);
  const phoneCheck = useMemo(() => validatePhone(phone, true), [phone]);

  const handleSaveProfile = (e: React.FormEvent) => {
    e.preventDefault();
    if (!businessName.trim()) {
      addToast('Business name is required', 'error');
      return;
    }
    if (registrationType === 'regular' && gstin.trim() && !gstinCheck.isValid) {
      addToast(gstinCheck.message, 'error');
      return;
    }
    if (pan.trim() && !panCheck.isValid) {
      addToast(panCheck.message, 'error');
      return;
    }
    if (phone.trim() && !phoneCheck.isValid) {
      addToast(phoneCheck.message, 'error');
      return;
    }
    updateProfile({
      businessName: businessName.trim(),
      entityType,
      ownerName: ownerName.trim(),
      businessType: businessType.trim(),
      registrationType,
      homeStateCode,
      filingFrequency,
      gstin: gstin.trim().toUpperCase(),
      pan: pan.trim().toUpperCase(),
      udyamNumber: udyamNumber.trim().toUpperCase(),
      phone: phone.trim(),
      email: email.trim(),
      address: address.trim(),
      openingCashBalancePaise: parsePaiseFromRupees(openingCashRupees),
      openingCapitalPaise: parsePaiseFromRupees(openingCapitalRupees),
    });
    const parsedThresh = Number.parseInt(lowStockInput, 10);
    if (Number.isFinite(parsedThresh) && parsedThresh > 0 && parsedThresh !== state.lowStockThreshold) {
      updateLowStockThreshold(parsedThresh);
    }
  };

  const handleAddBranch = (e: React.FormEvent) => {
    e.preventDefault();
    if (!branchName.trim() || !branchCode.trim()) {
      addToast('Enter branch name and short code', 'error');
      return;
    }
    addBranch(branchName, branchCode, branchAddress || state.profile.address);
    setBranchName('');
    setBranchCode('');
    setBranchAddress('');
  };

  const toggleNewStaffScreen = (id: string) => {
    setStaffScreens((prev) =>
      prev.includes(id) ? prev.filter((s) => s !== id) : [...prev, id]
    );
  };

  const handleAddUser = (e: React.FormEvent) => {
    e.preventDefault();
    if (!staffName.trim()) {
      addToast('Enter staff or co-owner display name', 'error');
      return;
    }
    if (staffPin.trim().length < 4) {
      addToast('User PIN must be at least 4 digits', 'error');
      return;
    }
    addManagedUser({
      name: staffName.trim(),
      role: staffRole,
      pin: staffPin.trim(),
      allocatedScreens:
        staffRole === 'owner' ? ALLOCATABLE_SCREENS.map((s) => s.id) : staffScreens,
    });
    setStaffName('');
    setStaffPin('1111');
  };

  const toggleExistingUserScreen = (user: ManagedUser, screenId: string) => {
    const has = user.allocatedScreens.includes(screenId);
    const updated: ManagedUser = {
      ...user,
      allocatedScreens: has
        ? user.allocatedScreens.filter((s) => s !== screenId)
        : [...user.allocatedScreens, screenId],
    };
    updateManagedUser(updated);
  };

  return (
    <div className="space-y-5">
      {/* Header & Tabs */}
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h2 className="text-lg font-bold text-[var(--p-text)]">
            Business Profile, Branches &amp; Counter RBAC
          </h2>
          <p className="text-xs text-[var(--p-text-muted)]">
            Statutory GSTIN &amp; entity details · Store Code{' '}
            <span className="font-mono-num font-semibold text-[var(--p-text)]">
              {state.profile.storeCode}
            </span>
          </p>
        </div>

        <div className="flex items-center gap-2 flex-wrap">
          <button
            type="button"
            onClick={() => setThemeOpen(true)}
            className="inline-flex items-center gap-1.5 whitespace-nowrap rounded-xl border border-[var(--p-card-border)] bg-[var(--p-card)] px-3 py-2 text-xs font-semibold text-[var(--p-text)] hover:bg-[var(--p-secondary-bg)]"
          >
            <Palette className="w-3.5 h-3.5 text-[var(--p-primary)]" />
            Theme Palette
          </button>

          <div className="inline-flex rounded-xl border border-[var(--p-card-border)] bg-[var(--p-card)] p-1">
            {[
              { id: 'profile' as const, label: 'Business Profile', icon: <Building2 className="w-3.5 h-3.5" /> },
              { id: 'branches' as const, label: 'Branches', icon: <GitBranch className="w-3.5 h-3.5" /> },
              { id: 'staff' as const, label: 'Staff & RBAC', icon: <Shield className="w-3.5 h-3.5" /> },
            ].map((t) => (
              <button
                key={t.id}
                type="button"
                onClick={() => setTab(t.id)}
                className={`inline-flex items-center gap-1.5 whitespace-nowrap rounded-lg px-3 py-1.5 text-xs font-semibold transition ${
                  tab === t.id
                    ? 'bg-[var(--p-primary)] text-[var(--p-primary-text)]'
                    : 'text-[var(--p-text-muted)] hover:text-[var(--p-text)]'
                }`}
              >
                {t.icon}
                <span>{t.label}</span>
              </button>
            ))}
          </div>
        </div>
      </div>

      {/* TAB 1: BUSINESS PROFILE */}
      {tab === 'profile' && (
        <form
          onSubmit={handleSaveProfile}
          className="rounded-2xl border border-[var(--p-card-border)] bg-[var(--p-card)] p-5 space-y-5"
        >
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
            <div>
              <label className="block text-xs font-semibold text-[var(--p-text)] mb-1">
                Legal / Trade Business Name *
              </label>
              <input
                type="text"
                value={businessName}
                onChange={(e) => setBusinessName(e.target.value)}
                required
                className="w-full rounded-xl border border-[var(--p-card-border)] bg-[var(--p-bg)] px-3 py-2 text-xs text-[var(--p-text)]"
              />
            </div>

            <div>
              <label className="block text-xs font-semibold text-[var(--p-text)] mb-1">
                Constitution / Entity Type
              </label>
              <select
                value={entityType}
                onChange={(e) => setEntityType(e.target.value as BusinessEntityType)}
                className="w-full rounded-xl border border-[var(--p-card-border)] bg-[var(--p-bg)] px-3 py-2 text-xs text-[var(--p-text)]"
              >
                {(Object.keys(BUSINESS_ENTITY_META) as BusinessEntityType[]).map((k) => (
                  <option key={k} value={k}>
                    {BUSINESS_ENTITY_META[k].displayName}
                  </option>
                ))}
              </select>
            </div>

            <div>
              <label className="block text-xs font-semibold text-[var(--p-text)] mb-1">
                Signatory ({BUSINESS_ENTITY_META[entityType].signatoryTitle})
              </label>
              <input
                type="text"
                value={ownerName}
                onChange={(e) => setOwnerName(e.target.value)}
                className="w-full rounded-xl border border-[var(--p-card-border)] bg-[var(--p-bg)] px-3 py-2 text-xs text-[var(--p-text)]"
              />
            </div>

            <div>
              <label className="block text-xs font-semibold text-[var(--p-text)] mb-1">
                GST Registration Type
              </label>
              <select
                value={registrationType}
                onChange={(e) => setRegistrationType(e.target.value as RegistrationType)}
                className="w-full rounded-xl border border-[var(--p-card-border)] bg-[var(--p-bg)] px-3 py-2 text-xs text-[var(--p-text)]"
              >
                <option value="regular">Regular Taxpayer (GSTR-1 / 3B)</option>
                <option value="composition">Composition Scheme (CMP-08)</option>
                <option value="unregistered">Unregistered / Non-GST Store</option>
              </select>
            </div>

            <div>
              <label className="block text-xs font-semibold text-[var(--p-text)] mb-1">
                Home State (Place of Supply Origin)
              </label>
              <select
                value={homeStateCode}
                onChange={(e) => setHomeStateCode(e.target.value)}
                className="w-full rounded-xl border border-[var(--p-card-border)] bg-[var(--p-bg)] px-3 py-2 text-xs text-[var(--p-text)]"
              >
                {INDIAN_STATES.map((s) => (
                  <option key={s.code} value={s.code}>
                    {s.code} - {s.name}
                  </option>
                ))}
              </select>
            </div>

            <div>
              <label className="block text-xs font-semibold text-[var(--p-text)] mb-1">
                Return Filing Frequency
              </label>
              <select
                value={filingFrequency}
                onChange={(e) => setFilingFrequency(e.target.value as 'Monthly' | 'Quarterly')}
                className="w-full rounded-xl border border-[var(--p-card-border)] bg-[var(--p-bg)] px-3 py-2 text-xs text-[var(--p-text)]"
              >
                <option value="Monthly">Monthly (Regular)</option>
                <option value="Quarterly">Quarterly (QRMP / CMP-08)</option>
              </select>
            </div>

            <div>
              <label className="block text-xs font-semibold text-[var(--p-text)] mb-1">
                15-Digit GSTIN (ISO 7064 Mod-36 Validated)
              </label>
              <input
                type="text"
                maxLength={15}
                value={gstin}
                onChange={(e) => setGstin(e.target.value.toUpperCase())}
                placeholder="27AABCB1234F1Z5"
                className="w-full rounded-xl border border-[var(--p-card-border)] bg-[var(--p-bg)] px-3 py-2 text-xs font-mono-num uppercase text-[var(--p-text)]"
              />
              {gstin.trim() && (
                <p
                  className={`mt-1 flex items-center gap-1 text-[11px] ${
                    gstinCheck.isValid ? 'text-[var(--p-success)]' : 'text-[var(--p-danger)]'
                  }`}
                >
                  {gstinCheck.isValid ? (
                    <CheckCircle2 className="w-3 h-3 shrink-0" />
                  ) : (
                    <XCircle className="w-3 h-3 shrink-0" />
                  )}
                  <span>{gstinCheck.message}</span>
                </p>
              )}
            </div>

            <div>
              <label className="block text-xs font-semibold text-[var(--p-text)] mb-1">
                Business PAN
              </label>
              <input
                type="text"
                maxLength={10}
                value={pan}
                onChange={(e) => setPan(e.target.value.toUpperCase())}
                placeholder="AABCB1234F"
                className="w-full rounded-xl border border-[var(--p-card-border)] bg-[var(--p-bg)] px-3 py-2 text-xs font-mono-num uppercase text-[var(--p-text)]"
              />
              {pan.trim() && !panCheck.isValid && (
                <p className="mt-1 text-[11px] text-[var(--p-danger)]">{panCheck.message}</p>
              )}
            </div>

            <div>
              <label className="block text-xs font-semibold text-[var(--p-text)] mb-1">
                UDYAM MSME Registration No.
              </label>
              <input
                type="text"
                value={udyamNumber}
                onChange={(e) => setUdyamNumber(e.target.value.toUpperCase())}
                placeholder="UDYAM-MH-19-0048219"
                className="w-full rounded-xl border border-[var(--p-card-border)] bg-[var(--p-bg)] px-3 py-2 text-xs font-mono-num text-[var(--p-text)]"
              />
            </div>

            <div>
              <label className="block text-xs font-semibold text-[var(--p-text)] mb-1">
                Mobile Number
              </label>
              <input
                type="tel"
                value={phone}
                onChange={(e) => setPhone(e.target.value)}
                className="w-full rounded-xl border border-[var(--p-card-border)] bg-[var(--p-bg)] px-3 py-2 text-xs font-mono-num text-[var(--p-text)]"
              />
              {phone.trim() && !phoneCheck.isValid && (
                <p className="mt-1 text-[11px] text-[var(--p-danger)]">{phoneCheck.message}</p>
              )}
            </div>

            <div>
              <label className="block text-xs font-semibold text-[var(--p-text)] mb-1">
                Business Email
              </label>
              <input
                type="email"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                className="w-full rounded-xl border border-[var(--p-card-border)] bg-[var(--p-bg)] px-3 py-2 text-xs text-[var(--p-text)]"
              />
            </div>

            <div>
              <label className="block text-xs font-semibold text-[var(--p-text)] mb-1">
                Trade Nature / Category
              </label>
              <input
                type="text"
                value={businessType}
                onChange={(e) => setBusinessType(e.target.value)}
                className="w-full rounded-xl border border-[var(--p-card-border)] bg-[var(--p-bg)] px-3 py-2 text-xs text-[var(--p-text)]"
              />
            </div>

            <div>
              <label className="block text-xs font-semibold text-[var(--p-text)] mb-1">
                Opening Cash Balance (₹)
              </label>
              <input
                type="text"
                inputMode="decimal"
                value={openingCashRupees}
                onChange={(e) => setOpeningCashRupees(e.target.value)}
                className="w-full rounded-xl border border-[var(--p-card-border)] bg-[var(--p-bg)] px-3 py-2 text-xs font-mono-num text-[var(--p-text)]"
              />
              <p className="mt-0.5 text-[11px] text-[var(--p-text-muted)] font-mono-num">
                Parsed: {formatPaise(parsePaiseFromRupees(openingCashRupees))}
              </p>
            </div>

            <div>
              <label className="block text-xs font-semibold text-[var(--p-text)] mb-1">
                Opening Owner Capital (₹)
              </label>
              <input
                type="text"
                inputMode="decimal"
                value={openingCapitalRupees}
                onChange={(e) => setOpeningCapitalRupees(e.target.value)}
                className="w-full rounded-xl border border-[var(--p-card-border)] bg-[var(--p-bg)] px-3 py-2 text-xs font-mono-num text-[var(--p-text)]"
              />
              <p className="mt-0.5 text-[11px] text-[var(--p-text-muted)] font-mono-num">
                Parsed: {formatPaise(parsePaiseFromRupees(openingCapitalRupees))}
              </p>
            </div>

            <div>
              <label className="block text-xs font-semibold text-[var(--p-text)] mb-1">
                Global Low-Stock Alert Threshold (Units)
              </label>
              <input
                type="number"
                min={1}
                value={lowStockInput}
                onChange={(e) => setLowStockInput(e.target.value)}
                className="w-full rounded-xl border border-[var(--p-card-border)] bg-[var(--p-bg)] px-3 py-2 text-xs font-mono-num text-[var(--p-text)]"
              />
            </div>
          </div>

          <div>
            <label className="block text-xs font-semibold text-[var(--p-text)] mb-1">
              Registered Principal Place of Business Address
            </label>
            <textarea
              rows={2}
              value={address}
              onChange={(e) => setAddress(e.target.value)}
              className="w-full rounded-xl border border-[var(--p-card-border)] bg-[var(--p-bg)] px-3 py-2 text-xs text-[var(--p-text)]"
            />
          </div>

          <div className="flex justify-end">
            <button
              type="submit"
              className="inline-flex items-center gap-1.5 rounded-xl bg-[var(--p-primary)] px-4 py-2 text-xs font-semibold text-[var(--p-primary-text)] hover:opacity-90"
            >
              <Save className="w-3.5 h-3.5" />
              Save Business Configuration
            </button>
          </div>
        </form>
      )}

      {/* TAB 2: BRANCHES */}
      {tab === 'branches' && (
        <div className="grid grid-cols-1 lg:grid-cols-3 gap-4">
          <div className="lg:col-span-2 rounded-2xl border border-[var(--p-card-border)] bg-[var(--p-card)] p-4 space-y-3">
            <h3 className="text-sm font-bold text-[var(--p-text)]">
              Registered Store Branches ({state.branches.length})
            </h3>
            <div className="space-y-2.5">
              {state.branches.map((br) => {
                const isActive = state.activeBranchId === br.id;
                return (
                  <div
                    key={br.id}
                    className={`rounded-xl border p-3.5 flex flex-wrap items-center justify-between gap-3 ${
                      isActive
                        ? 'border-[var(--p-primary)] bg-[var(--p-secondary-bg)]'
                        : 'border-[var(--p-card-border)] bg-[var(--p-bg)]'
                    }`}
                  >
                    <div>
                      <div className="flex items-center gap-2">
                        <p className="text-xs font-bold text-[var(--p-text)]">{br.name}</p>
                        <span className="rounded px-1.5 py-0.5 text-[10px] font-mono-num font-semibold bg-[var(--p-card)] border border-[var(--p-card-border)]">
                          {br.code}
                        </span>
                        {br.isMain && (
                          <span className="rounded px-1.5 py-0.5 text-[10px] font-semibold bg-[var(--p-success-container)] text-[var(--p-on-success-container)]">
                            Head Office
                          </span>
                        )}
                      </div>
                      <p className="text-[11px] text-[var(--p-text-muted)] mt-0.5">
                        {br.address || 'No address set'}
                      </p>
                    </div>

                    <button
                      type="button"
                      onClick={() => setActiveBranch(br.id)}
                      className={`rounded-xl px-3 py-1.5 text-xs font-semibold ${
                        isActive
                          ? 'bg-[var(--p-primary)] text-[var(--p-primary-text)]'
                          : 'border border-[var(--p-card-border)] text-[var(--p-text)] hover:bg-[var(--p-card)]'
                      }`}
                    >
                      {isActive ? 'Active Branch' : 'Switch To Branch'}
                    </button>
                  </div>
                );
              })}
            </div>
          </div>

          <form
            onSubmit={handleAddBranch}
            className="rounded-2xl border border-[var(--p-card-border)] bg-[var(--p-card)] p-4 space-y-3 h-fit"
          >
            <h3 className="text-sm font-bold text-[var(--p-text)]">Add New Branch</h3>
            <div>
              <label className="block text-xs font-semibold text-[var(--p-text)] mb-1">
                Branch Name *
              </label>
              <input
                type="text"
                value={branchName}
                onChange={(e) => setBranchName(e.target.value)}
                placeholder="e.g. Pune Wholesale Hub"
                className="w-full rounded-xl border border-[var(--p-card-border)] bg-[var(--p-bg)] px-3 py-2 text-xs text-[var(--p-text)]"
              />
            </div>
            <div>
              <label className="block text-xs font-semibold text-[var(--p-text)] mb-1">
                Branch Code *
              </label>
              <input
                type="text"
                maxLength={8}
                value={branchCode}
                onChange={(e) => setBranchCode(e.target.value.toUpperCase())}
                placeholder="e.g. PUN"
                className="w-full rounded-xl border border-[var(--p-card-border)] bg-[var(--p-bg)] px-3 py-2 text-xs font-mono-num uppercase text-[var(--p-text)]"
              />
            </div>
            <div>
              <label className="block text-xs font-semibold text-[var(--p-text)] mb-1">
                Address
              </label>
              <input
                type="text"
                value={branchAddress}
                onChange={(e) => setBranchAddress(e.target.value)}
                placeholder="Market Yard, Gultekdi, Pune"
                className="w-full rounded-xl border border-[var(--p-card-border)] bg-[var(--p-bg)] px-3 py-2 text-xs text-[var(--p-text)]"
              />
            </div>
            <button
              type="submit"
              className="w-full inline-flex items-center justify-center gap-1.5 rounded-xl bg-[var(--p-primary)] px-3 py-2 text-xs font-semibold text-[var(--p-primary-text)]"
            >
              <Plus className="w-3.5 h-3.5" />
              Create Branch
            </button>
          </form>
        </div>
      )}

      {/* TAB 3: STAFF & RBAC */}
      {tab === 'staff' && (
        <div className="grid grid-cols-1 lg:grid-cols-3 gap-4">
          <div className="lg:col-span-2 rounded-2xl border border-[var(--p-card-border)] bg-[var(--p-card)] p-4 space-y-3">
            <div className="flex items-center justify-between gap-2">
              <h3 className="text-sm font-bold text-[var(--p-text)]">
                Counter Users &amp; Screen Permissions
              </h3>
              <span className="text-[11px] text-[var(--p-text-muted)]">
                Active: <strong>{state.currentUserName}</strong>
              </span>
            </div>

            <div className="space-y-3">
              {state.managedUsers.map((u) => {
                const isCurrent = state.currentUserId === u.id;
                return (
                  <div
                    key={u.id}
                    className={`rounded-xl border p-3.5 space-y-2.5 ${
                      isCurrent
                        ? 'border-[var(--p-primary)] bg-[var(--p-secondary-bg)]'
                        : 'border-[var(--p-card-border)] bg-[var(--p-bg)]'
                    }`}
                  >
                    <div className="flex flex-wrap items-center justify-between gap-2">
                      <div className="flex items-center gap-2">
                        <UserCheck className="w-4 h-4 text-[var(--p-primary)]" />
                        <span className="text-xs font-bold text-[var(--p-text)]">{u.name}</span>
                        <span
                          className={`rounded px-1.5 py-0.5 text-[10px] font-semibold uppercase ${
                            u.role === 'owner'
                              ? 'bg-[var(--p-primary)] text-[var(--p-primary-text)]'
                              : 'bg-[var(--p-warning-container)] text-[var(--p-on-warning-container)]'
                          }`}
                        >
                          {u.role}
                        </span>
                      </div>

                      <div className="flex items-center gap-2">
                        <button
                          type="button"
                          onClick={() => switchCurrentUser(u.id)}
                          className={`rounded-lg px-2.5 py-1 text-[11px] font-semibold ${
                            isCurrent
                              ? 'bg-[var(--p-primary)] text-[var(--p-primary-text)]'
                              : 'border border-[var(--p-card-border)] text-[var(--p-text)] hover:bg-[var(--p-card)]'
                          }`}
                        >
                          {isCurrent ? 'Active Counter' : 'Switch To User'}
                        </button>
                        {state.managedUsers.length > 1 && !isCurrent && (
                          <button
                            type="button"
                            onClick={() => deleteManagedUser(u.id)}
                            className="p-1.5 rounded-lg text-[var(--p-danger)] hover:bg-[var(--p-danger-container)]"
                            title="Delete user"
                          >
                            <Trash2 className="w-3.5 h-3.5" />
                          </button>
                        )}
                      </div>
                    </div>

                    {u.role === 'owner' ? (
                      <p className="text-[11px] text-[var(--p-text-muted)]">
                        Owner role has unrestricted access to all billing, accounting, GST, CMA,
                        backup, and security modules.
                      </p>
                    ) : (
                      <div className="flex flex-wrap gap-1.5">
                        {ALLOCATABLE_SCREENS.map((scr) => {
                          const enabled = u.allocatedScreens.includes(scr.id);
                          return (
                            <button
                              key={scr.id}
                              type="button"
                              onClick={() => toggleExistingUserScreen(u, scr.id)}
                              className={`rounded-lg border px-2.5 py-1 text-[11px] font-medium transition ${
                                enabled
                                  ? 'border-[var(--p-primary)] bg-[var(--p-primary)] text-[var(--p-primary-text)]'
                                  : 'border-[var(--p-card-border)] bg-[var(--p-card)] text-[var(--p-text-muted)]'
                              }`}
                            >
                              {scr.shortTitle}
                            </button>
                          );
                        })}
                      </div>
                    )}
                  </div>
                );
              })}
            </div>
          </div>

          <form
            onSubmit={handleAddUser}
            className="rounded-2xl border border-[var(--p-card-border)] bg-[var(--p-card)] p-4 space-y-3 h-fit"
          >
            <div className="flex items-center gap-2">
              <UserPlus className="w-4 h-4 text-[var(--p-primary)]" />
              <h3 className="text-sm font-bold text-[var(--p-text)]">Add Staff / Counter</h3>
            </div>
            <div>
              <label className="block text-xs font-semibold text-[var(--p-text)] mb-1">
                Display Name *
              </label>
              <input
                type="text"
                value={staffName}
                onChange={(e) => setStaffName(e.target.value)}
                placeholder="e.g. Priya Cashier Desk 2"
                className="w-full rounded-xl border border-[var(--p-card-border)] bg-[var(--p-bg)] px-3 py-2 text-xs text-[var(--p-text)]"
              />
            </div>

            <div className="grid grid-cols-2 gap-2">
              <div>
                <label className="block text-xs font-semibold text-[var(--p-text)] mb-1">
                  Role
                </label>
                <select
                  value={staffRole}
                  onChange={(e) => setStaffRole(e.target.value as 'owner' | 'staff')}
                  className="w-full rounded-xl border border-[var(--p-card-border)] bg-[var(--p-bg)] px-3 py-2 text-xs text-[var(--p-text)]"
                >
                  <option value="staff">Staff (RBAC)</option>
                  <option value="owner">Co-Owner</option>
                </select>
              </div>
              <div>
                <label className="block text-xs font-semibold text-[var(--p-text)] mb-1">
                  Counter PIN
                </label>
                <input
                  type="password"
                  value={staffPin}
                  onChange={(e) => setStaffPin(e.target.value)}
                  className="w-full rounded-xl border border-[var(--p-card-border)] bg-[var(--p-bg)] px-3 py-2 text-xs font-mono-num text-[var(--p-text)]"
                />
              </div>
            </div>

            {staffRole === 'staff' && (
              <div>
                <p className="text-xs font-semibold text-[var(--p-text)] mb-1.5">
                  Allocated Modules
                </p>
                <div className="flex flex-wrap gap-1.5">
                  {ALLOCATABLE_SCREENS.map((scr) => {
                    const active = staffScreens.includes(scr.id);
                    return (
                      <button
                        key={scr.id}
                        type="button"
                        onClick={() => toggleNewStaffScreen(scr.id)}
                        className={`rounded-lg border px-2.5 py-1 text-[11px] font-medium ${
                          active
                            ? 'border-[var(--p-primary)] bg-[var(--p-primary)] text-[var(--p-primary-text)]'
                            : 'border-[var(--p-card-border)] bg-[var(--p-bg)] text-[var(--p-text-muted)]'
                        }`}
                      >
                        {scr.shortTitle}
                      </button>
                    );
                  })}
                </div>
              </div>
            )}

            <button
              type="submit"
              className="w-full inline-flex items-center justify-center gap-1.5 rounded-xl bg-[var(--p-primary)] px-3 py-2 text-xs font-semibold text-[var(--p-primary-text)]"
            >
              <Plus className="w-3.5 h-3.5" />
              Create Counter User
            </button>
          </form>
        </div>
      )}

      <ThemePickerModal open={themeOpen} onClose={() => setThemeOpen(false)} />
    </div>
  );
};
