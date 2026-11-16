export type TransactionType =
  | 'Sale'
  | 'Purchase'
  | 'PaymentIn'
  | 'PaymentOut'
  | 'Expense'
  | 'Drawing'
  | 'CreditNote'
  | 'DebitNote'
  | 'Quotation'
  | 'NonGstBill';

export type PaymentMode = 'Cash' | 'Bank' | 'UPI' | 'Cheque' | 'Credit';

export type ExpenseCategory = 'Direct' | 'Indirect';

export type BalanceType = 'Receivable' | 'Payable' | 'Settled';

export type RegistrationType = 'regular' | 'composition' | 'unregistered';

export type BusinessEntityType =
  | 'Proprietorship'
  | 'Partnership'
  | 'PrivateLimited'
  | 'PublicLimited'
  | 'LLP'
  | 'HUF';

export const BUSINESS_ENTITY_META: Record<
  BusinessEntityType,
  { displayName: string; signatoryTitle: string }
> = {
  Proprietorship: { displayName: 'Sole Proprietorship', signatoryTitle: 'Proprietor' },
  Partnership: { displayName: 'Partnership Firm', signatoryTitle: 'Partner' },
  PrivateLimited: { displayName: 'Private Limited Company', signatoryTitle: 'Director' },
  PublicLimited: { displayName: 'Public Limited Company', signatoryTitle: 'Director' },
  LLP: { displayName: 'Limited Liability Partnership (LLP)', signatoryTitle: 'Designated Partner' },
  HUF: { displayName: 'Hindu Undivided Family (HUF)', signatoryTitle: 'Karta' },
};

export type AppColorThemeId =
  | 'midnight_teal'
  | 'clinic_blue'
  | 'fresh_mint'
  | 'warm_sand'
  | 'royal_purple'
  | 'amoled_black';

export type ThemeMode = 'auto' | 'light' | 'dark';

export interface BusinessProfile {
  id: string;
  businessName: string;
  entityType: BusinessEntityType;
  ownerName: string;
  businessType: string;
  registrationType: RegistrationType;
  homeStateCode: string;
  filingFrequency: 'Monthly' | 'Quarterly';
  plan: string;
  openingCashBalancePaise: number;
  openingCapitalPaise: number;
  storeCode: string;
  pan: string;
  gstin: string;
  udyamNumber: string;
  address: string;
  email: string;
  phone: string;
  isRegistered: boolean;
}

export interface Branch {
  id: string;
  name: string;
  code: string;
  isMain: boolean;
  address: string;
}

export interface Party {
  id: string;
  name: string;
  phone: string;
  address: string;
  gstin: string;
  pan: string;
  stateCode: string;
  openingBalancePaise: number;
  openingBalanceType: BalanceType;
  isSupplier: boolean;
}

export interface BankAccount {
  id: string;
  accountName: string;
  bankName: string;
  accountNumber: string;
  ifscCode: string;
  upiId: string;
  openingBalancePaise: number;
  isDefaultForQr: boolean;
}

export interface BankReconItem {
  id: string;
  bankAccountId: string;
  date: string;
  description: string;
  amountPaise: number;
  isCredit: boolean;
  isReconciled: boolean;
  matchedTxnId: string | null;
  referenceNo: string;
  matchConfidence: number;
  matchReason: string;
  discrepancyNote: string;
  isUserConfirmed: boolean;
  reconciledAt: string | null;
}

export interface UpiQrCode {
  id: string;
  label: string;
  upiId: string;
  linkedBankId: string;
  isDefault: boolean;
  isEnabled: boolean;
  showOnInvoices: boolean;
  qrImageUri: string | null;
}

export interface QrActivityLog {
  id: string;
  action: string;
  details: string;
  timestamp: string;
  role: string;
  hash: string;
  previousHash: string;
}

export const GstSlabs = {
  STANDARD_BPS: [0, 500, 1800, 4000] as const,
  SPECIAL_BPS: [25, 300] as const,
  LEGACY_BPS: [1200, 2800] as const,
  ALL_BPS: [0, 25, 300, 500, 1200, 1800, 2800, 4000] as const,
  label(bps: number): string {
    return bps % 100 === 0 ? `${bps / 100}%` : `${bps / 100}%`;
  },
};

const DECIMAL_UNITS = new Set([
  'KG',
  'KGS',
  'KILOGRAM',
  'LTR',
  'LITRE',
  'LITER',
  'MTR',
  'METER',
  'METRE',
  'GRAM',
  'GM',
]);

export function allowsDecimal(unit: string): boolean {
  return DECIMAL_UNITS.has(unit.trim().toUpperCase());
}

export interface ProductItem {
  id: string;
  name: string;
  barcode: string;
  sku: string;
  hsnCode: string;
  gstRateBps: number;
  unit: string;
  purchasePricePaise: number;
  salePricePaise: number;
  currentStockMilli: number;
  reorderLevelMilli: number;
  category: string;
  openingStockMilli: number;
  hasBatchTracking: boolean;
  hasVariants: boolean;
  isArchived: boolean;
  imageUrl?: string | null;
}

export interface ProductVariant {
  id: string;
  productId: string;
  variantName: string;
  sku: string;
  barcode: string;
  additionalPricePaise: number;
  currentStockMilli: number;
}

export interface StockBatch {
  id: string;
  productId: string;
  batchNumber: string;
  mfgDate: string | null;
  expiryDate: string;
  purchasePricePaise: number;
  initialQuantityMilli: number;
  availableQuantityMilli: number;
  locationId: string;
}

export type StockAdjustmentReason =
  | 'DAMAGE'
  | 'EXPIRED'
  | 'THEFT_OR_LOSS'
  | 'COUNT_CORRECTION'
  | 'INTERNAL_USE'
  | 'INITIAL_SETUP';

export const STOCK_ADJUSTMENT_REASONS: Record<StockAdjustmentReason, string> = {
  DAMAGE: 'Damaged Goods',
  EXPIRED: 'Expired Stock',
  THEFT_OR_LOSS: 'Theft / Shrinkage',
  COUNT_CORRECTION: 'Physical Count Correction',
  INTERNAL_USE: 'Internal Store Usage',
  INITIAL_SETUP: 'Opening Balance Adjustment',
};

export interface StockAdjustment {
  id: string;
  productId: string;
  productName: string;
  locationId: string;
  quantityDeltaMilli: number;
  reason: StockAdjustmentReason;
  remarks: string;
  timestamp: string;
  adjustedBy: string;
}

export interface StockMovement {
  id: string;
  productId: string;
  productName: string;
  batchId: string | null;
  referenceTxnId: string | null;
  movementType:
    | 'SALE_OUT'
    | 'PURCHASE_IN'
    | 'RETURN_IN'
    | 'ADJUSTMENT'
    | 'TRANSFER'
    | 'VOID_REVERSAL';
  quantityDeltaMilli: number;
  balanceAfterMilli: number;
  locationId: string;
  timestamp: string;
  remarks: string;
}

export interface InventoryLocation {
  id: string;
  name: string;
  code: string;
  address: string;
  isPrimary: boolean;
}

export interface InvoiceLineItem {
  itemId: string;
  name: string;
  hsnCode: string;
  quantityMilli: number;
  unit: string;
  ratePaise: number;
  gstRateBps: number;
  taxableAmountPaise: number;
  cgstPaise: number;
  sgstPaise: number;
  igstPaise: number;
  totalPaise: number;
  cessPaise: number;
}

export interface Transaction {
  id: string;
  branchId: string;
  date: string;
  partyId: string | null;
  amountPaise: number;
  type: TransactionType;
  expenseCategory: ExpenseCategory;
  paymentMode: PaymentMode;
  bankAccountId: string | null;
  remarks: string;
  entryMode: 'quick' | 'itemized';
  invoiceNumber: string;
  isVoided: boolean;
  voidReason: string;
  voidedAt: string | null;
  items: InvoiceLineItem[];
}

export interface AuditLog {
  id: string;
  previousHash: string;
  recordHash: string;
  entityType: string;
  entityId: string;
  action: string;
  details: string;
  reason: string;
  actor: string;
  timestamp: string;
}

export interface ManagedUser {
  id: string;
  name: string;
  role: 'owner' | 'staff';
  pinHash: string;
  isActive: boolean;
  allocatedScreens: string[];
}

export type AllocatableScreenId = 'invoices' | 'daybook' | 'ledgers' | 'cash_bank' | 'stock';

export interface AllocatableScreenMeta {
  id: AllocatableScreenId;
  title: string;
  shortTitle: string;
  description: string;
  category: string;
}

export const ALLOCATABLE_SCREENS: AllocatableScreenMeta[] = [
  {
    id: 'invoices',
    title: 'GST Invoices Hub',
    shortTitle: 'Invoices',
    description: 'Tax Invoices, Bills of Supply, Credit Notes & Quotations',
    category: 'Billing',
  },
  {
    id: 'daybook',
    title: 'Daybook Register',
    shortTitle: 'Daybook',
    description: 'Daily sales ledger, cash in/out & transaction records',
    category: 'Cash Flow',
  },
  {
    id: 'ledgers',
    title: 'Parties & Khata',
    shortTitle: 'Ledgers',
    description: 'Customer khata, supplier accounts & collections',
    category: 'Khata',
  },
  {
    id: 'cash_bank',
    title: 'Cash & Bank Balance',
    shortTitle: 'Cash/Bank',
    description: 'Live cash drawer tracking & bank account deposits',
    category: 'Banking',
  },
  {
    id: 'stock',
    title: 'Stock & Inventory',
    shortTitle: 'Stock',
    description: 'Item catalog, current stock & low-stock alerts',
    category: 'Inventory',
  },
];

export interface RegisteredTenantRecord {
  storeCode: string;
  businessName: string;
  ownerName: string;
  phone: string;
  gstin: string;
  plan: string;
  status: 'Active' | 'Suspended';
  registeredAt: string;
  pinHash: string;
}

export interface PabaruUiState {
  profile: BusinessProfile;
  branches: Branch[];
  activeBranchId: string;
  parties: Party[];
  bankAccounts: BankAccount[];
  bankReconItems: BankReconItem[];
  products: ProductItem[];
  productVariants: ProductVariant[];
  stockBatches: StockBatch[];
  stockAdjustments: StockAdjustment[];
  stockMovements: StockMovement[];
  inventoryLocations: InventoryLocation[];
  transactions: Transaction[];
  auditLogs: AuditLog[];
  qrCodes: UpiQrCode[];
  qrActivityLogs: QrActivityLog[];
  activeThemeId: AppColorThemeId;
  themeMode: ThemeMode;
  lowStockThreshold: number;
  isAppLocked: boolean;
  hasPinSet: boolean;
  pinHash: string;
  recoveryWords: string[];
  pinFailedAttempts: number;
  pinLockoutUntilMs: number;
  hasManagerPinSet: boolean;
  managerPinHash: string;
  isManagerUnlocked: boolean;
  managerUnlockExpiresAtMs: number;
  managerFailedAttempts: number;
  managerLockoutUntilMs: number;
  managerUseFingerprint: boolean;
  currentUserId: string;
  currentUserName: string;
  currentUserRole: 'owner' | 'staff';
  managedUsers: ManagedUser[];
  isSuperAdminLoggedIn: boolean;
  registeredTenants: RegisteredTenantRecord[];
  superAdminPinHash: string;
  isBankReconProUnlocked: boolean;
  isGstAssistantUnlocked: boolean;
  selectedFy: string;
  isChainValid: boolean;
  chainBrokenAtLogId: string | null;
}

export function isBatchExpired(expiryDate: string): boolean {
  const clean = expiryDate.trim().slice(0, 10);
  if (!/^\d{4}-\d{2}-\d{2}$/.test(clean)) return false;
  const today = new Date().toISOString().slice(0, 10);
  return clean < today;
}

export function isBatchExpiringSoon(expiryDate: string): boolean {
  const clean = expiryDate.trim().slice(0, 10);
  if (!/^\d{4}-\d{2}-\d{2}$/.test(clean)) return false;
  const now = new Date();
  const today = now.toISOString().slice(0, 10);
  const plus30 = new Date(now.getTime() + 30 * 86_400_000).toISOString().slice(0, 10);
  return clean >= today && clean <= plus30;
}
