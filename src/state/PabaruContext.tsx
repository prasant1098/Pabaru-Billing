import React, {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
} from 'react';
import {
  DiscrepancyResolutionType,
  createBalancingTransaction,
  getSampleHdfcStatementCsv,
  getSampleSbiStatementOfx,
  matchBankStatement,
  parseStatementText,
} from '../engines/bankReconEngine';
import {
  createAuditRecord,
  decryptAesGcm,
  encryptAesGcm,
  generateRecoveryPhrase,
  hashPinSync,
  verifyAuditChain,
} from '../engines/cryptoEngine';
import {
  calculateGstBps,
  getCurrentFinancialYear,
  getTodayDate,
  gstinCheckChar,
  lineTaxable,
} from '../engines/financialEngine';
import { applyTokensToDocument, resolveEffectiveTokens, PabaruTokens } from '../theme/tokens';
import {
  AppColorThemeId,
  AuditLog,
  BankAccount,
  Branch,
  BusinessProfile,
  ExpenseCategory,
  InventoryLocation,
  InvoiceLineItem,
  ManagedUser,
  PabaruUiState,
  Party,
  PaymentMode,
  ProductItem,
  QrActivityLog,
  RegisteredTenantRecord,
  StockAdjustmentReason,
  StockBatch,
  StockMovement,
  ThemeMode,
  Transaction,
  TransactionType,
  UpiQrCode,
} from '../types/models';

const STORAGE_KEY = 'pabaru_web_state_v1';
const TENANTS_STORAGE_KEY = 'pabaru_registered_tenants_v1';

export type AppScreenId =
  | 'dashboard'
  | 'invoices'
  | 'stock'
  | 'cash_bank'
  | 'more'
  | 'daybook'
  | 'ledgers'
  | 'party_unpaid'
  | 'reports'
  | 'cma_report'
  | 'gst_assistant'
  | 'gstr2b_recon'
  | 'business_profile'
  | 'backup'
  | 'security';

export interface ToastMessage {
  id: string;
  message: string;
  variant: 'success' | 'error' | 'info' | 'warning';
}

export interface PrintPreviewPayload {
  title: string;
  html: string;
  filename: string;
}

export interface WhatsAppSharePayload {
  party: Party;
  amountPaise: number;
  invoiceMeta?: { invoiceNumber?: string; date?: string };
}

function validGstin(first14: string): string {
  return `${first14}${gstinCheckChar(first14)}`;
}

function isoDaysAgo(days: number): string {
  const d = new Date(Date.now() - days * 86_400_000);
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  return `${y}-${m}-${day}`;
}

function isoDaysAhead(days: number): string {
  const d = new Date(Date.now() + days * 86_400_000);
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  return `${y}-${m}-${day}`;
}

function defaultTenants(): RegisteredTenantRecord[] {
  return [
    {
      storeCode: 'TL-7319',
      businessName: 'Bharat Traders & Wholesale Mart',
      ownerName: 'Rajesh Sharma',
      phone: '9820192837',
      gstin: validGstin('27AABCB1234F1Z'),
      plan: 'Enterprise Pro',
      status: 'Active',
      registeredAt: '2025-04-01',
      pinHash: hashPinSync('1234'),
    },
    {
      storeCode: 'SH-4402',
      businessName: 'Shree Ganesh Agro & Kirana',
      ownerName: 'Mahesh Patil',
      phone: '9823451209',
      gstin: validGstin('27AAECS8821M1Z'),
      plan: 'Standard GST',
      status: 'Active',
      registeredAt: '2025-07-14',
      pinHash: hashPinSync('1234'),
    },
  ];
}

function loadRegisteredTenants(): RegisteredTenantRecord[] {
  try {
    const raw = localStorage.getItem(TENANTS_STORAGE_KEY);
    if (raw) {
      const parsed = JSON.parse(raw);
      if (Array.isArray(parsed) && parsed.length > 0) return parsed;
    }
  } catch {
    // ignore storage parse failure
  }
  return defaultTenants();
}

export function createEmptyState(profileOverrides?: Partial<BusinessProfile>): PabaruUiState {
  const defaultPinHash = hashPinSync('1234');
  const adminPinHash = hashPinSync('998877');
  const ownerId = 'USR-OWNER-1';
  const branchId = 'BR-MAIN';
  const locId = 'LOC-MAIN';
  const profile: BusinessProfile = {
    id: 'BIZ-1',
    businessName: 'Bharat Traders & Wholesale Mart',
    entityType: 'Proprietorship',
    ownerName: 'Rajesh Sharma',
    businessType: 'Wholesale & Retail Trading',
    registrationType: 'regular',
    homeStateCode: '27',
    filingFrequency: 'Monthly',
    plan: 'Enterprise Pro',
    openingCashBalancePaise: 5_000_000,
    openingCapitalPaise: 25_000_000,
    storeCode: 'TL-7319',
    pan: 'AABCB1234F',
    gstin: validGstin('27AABCB1234F1Z'),
    udyamNumber: 'UDYAM-MH-19-0048219',
    address: 'Shop 14, APMC Market Yard, Vashi, Navi Mumbai - 400703',
    email: 'accounts@bharattraders.in',
    phone: '9820192837',
    isRegistered: false,
    ...profileOverrides,
  };

  return {
    profile,
    branches: [
      {
        id: branchId,
        name: 'Main Branch (APMC Vashi)',
        code: 'MAIN',
        isMain: true,
        address: profile.address,
      },
    ],
    activeBranchId: branchId,
    parties: [],
    bankAccounts: [
      {
        id: 'BNK-1',
        accountName: 'Bharat Traders Current A/c',
        bankName: 'HDFC Bank',
        accountNumber: '50200048192831',
        ifscCode: 'HDFC0000060',
        upiId: 'bharattraders@hdfcbank',
        openingBalancePaise: 12_500_000,
        isDefaultForQr: true,
      },
    ],
    bankReconItems: [],
    products: [],
    productVariants: [],
    stockBatches: [],
    stockAdjustments: [],
    stockMovements: [],
    inventoryLocations: [
      {
        id: locId,
        name: 'Main Store & Godown',
        code: 'WH-01',
        address: profile.address,
        isPrimary: true,
      },
    ],
    transactions: [],
    auditLogs: [],
    qrCodes: [
      {
        id: 'QR-1',
        label: 'Counter 1 - Primary Merchant QR',
        upiId: 'bharattraders@hdfcbank',
        linkedBankId: 'BNK-1',
        isDefault: true,
        isEnabled: true,
        showOnInvoices: true,
        qrImageUri: null,
      },
    ],
    qrActivityLogs: [],
    activeThemeId: 'clinic_blue',
    themeMode: 'light',
    lowStockThreshold: 10,
    isAppLocked: false,
    hasPinSet: false,
    pinHash: defaultPinHash,
    recoveryWords: generateRecoveryPhrase(),
    pinFailedAttempts: 0,
    pinLockoutUntilMs: 0,
    hasManagerPinSet: false,
    managerPinHash: defaultPinHash,
    isManagerUnlocked: true,
    managerUnlockExpiresAtMs: 0,
    managerFailedAttempts: 0,
    managerLockoutUntilMs: 0,
    managerUseFingerprint: false,
    currentUserId: ownerId,
    currentUserName: profile.ownerName || 'Owner',
    currentUserRole: 'owner',
    managedUsers: [
      {
        id: ownerId,
        name: profile.ownerName || 'Rajesh Sharma (Owner)',
        role: 'owner',
        pinHash: defaultPinHash,
        isActive: true,
        allocatedScreens: ['invoices', 'daybook', 'ledgers', 'cash_bank', 'stock'],
      },
      {
        id: 'USR-STAFF-1',
        name: 'Ankit Billing Counter',
        role: 'staff',
        pinHash: hashPinSync('1111'),
        isActive: true,
        allocatedScreens: ['invoices', 'daybook', 'stock'],
      },
    ],
    isSuperAdminLoggedIn: false,
    registeredTenants: loadRegisteredTenants(),
    superAdminPinHash: adminPinHash,
    isBankReconProUnlocked: false,
    isGstAssistantUnlocked: false,
    selectedFy: getCurrentFinancialYear(),
    isChainValid: true,
    chainBrokenAtLogId: null,
  };
}

function buildLineItem(
  item: {
    itemId: string;
    name: string;
    hsnCode: string;
    quantityMilli: number;
    unit: string;
    ratePaise: number;
    gstRateBps: number;
  },
  isInterstate: boolean
): InvoiceLineItem {
  const taxable = lineTaxable(item.quantityMilli, item.ratePaise);
  const gst = calculateGstBps(taxable, item.gstRateBps, isInterstate);
  return {
    ...item,
    taxableAmountPaise: taxable,
    cgstPaise: gst.cgstPaise,
    sgstPaise: gst.sgstPaise,
    igstPaise: gst.igstPaise,
    totalPaise: gst.totalPaise,
    cessPaise: 0,
  };
}

function roundRule26(items: InvoiceLineItem[]): number {
  const raw = items.reduce((s, i) => s + i.totalPaise, 0);
  return Math.floor((raw + 50) / 100) * 100;
}

export function createDemoSeedState(): PabaruUiState {
  const base = createEmptyState({ isRegistered: true });
  const branchId = 'BR-MAIN';
  const locMain = 'LOC-MAIN';
  const locGodown = 'LOC-GODOWN';

  const parties: Party[] = [
    {
      id: 'PTY-1',
      name: 'Sai Balaji Kirana & Supermarket',
      phone: '9820411223',
      address: 'Sector 17, Vashi, Navi Mumbai, MH',
      gstin: validGstin('27AAACS1234M1Z'),
      pan: 'AAACS1234M',
      stateCode: '27',
      openingBalancePaise: 2_500_000,
      openingBalanceType: 'Receivable',
      isSupplier: false,
    },
    {
      id: 'PTY-2',
      name: 'Omkar Departmental Store',
      phone: '9892104455',
      address: 'Dadar West, Mumbai - 400028',
      gstin: validGstin('27AABCO9988D1Z'),
      pan: 'AABCO9988D',
      stateCode: '27',
      openingBalancePaise: 1_200_000,
      openingBalanceType: 'Receivable',
      isSupplier: false,
    },
    {
      id: 'PTY-3',
      name: 'Gujarat Royal Spices & Agro Depot',
      phone: '9825099881',
      address: 'Unjha APMC Yard, Mehsana, Gujarat',
      gstin: validGstin('24AABCG5544K1Z'),
      pan: 'AABCG5544K',
      stateCode: '24',
      openingBalancePaise: 1_800_000,
      openingBalanceType: 'Payable',
      isSupplier: true,
    },
    {
      id: 'PTY-4',
      name: 'Prime Goods Wholesalers Pvt Ltd',
      phone: '9819283746',
      address: 'Kalamboli Steel & Grain Market, Navi Mumbai',
      gstin: validGstin('27AABCP7721E1Z'),
      pan: 'AABCP7721E',
      stateCode: '27',
      openingBalancePaise: 900_000,
      openingBalanceType: 'Payable',
      isSupplier: true,
    },
    {
      id: 'PTY-5',
      name: 'Walk-in Retail Counter',
      phone: '9000000000',
      address: 'Navi Mumbai',
      gstin: '',
      pan: '',
      stateCode: '27',
      openingBalancePaise: 0,
      openingBalanceType: 'Settled',
      isSupplier: false,
    },
  ];

  const products: ProductItem[] = [
    {
      id: 'PRD-1',
      name: 'Basmati Rice Royal 25KG Bag',
      barcode: '8901010001011',
      sku: 'RICE-BAS-25',
      hsnCode: '1006',
      gstRateBps: 500,
      unit: 'BAG',
      purchasePricePaise: 185_000,
      salePricePaise: 225_000,
      currentStockMilli: 48_000,
      reorderLevelMilli: 12_000,
      category: 'Grains & Staples',
      openingStockMilli: 30_000,
      hasBatchTracking: true,
      hasVariants: false,
      isArchived: false,
    },
    {
      id: 'PRD-2',
      name: 'Cold-Pressed Groundnut Oil 15L Tin',
      barcode: '8901010001028',
      sku: 'OIL-GN-15L',
      hsnCode: '1508',
      gstRateBps: 500,
      unit: 'TIN',
      purchasePricePaise: 245_000,
      salePricePaise: 289_000,
      currentStockMilli: 22_000,
      reorderLevelMilli: 10_000,
      category: 'Edible Oils',
      openingStockMilli: 20_000,
      hasBatchTracking: true,
      hasVariants: false,
      isArchived: false,
    },
    {
      id: 'PRD-3',
      name: 'Premium Kashmiri Almonds (Badam)',
      barcode: '8901010001035',
      sku: 'DF-ALM-KG',
      hsnCode: '0802',
      gstRateBps: 1200,
      unit: 'KG',
      purchasePricePaise: 72_000,
      salePricePaise: 95_000,
      currentStockMilli: 6_500,
      reorderLevelMilli: 10_000,
      category: 'Dry Fruits',
      openingStockMilli: 15_000,
      hasBatchTracking: true,
      hasVariants: false,
      isArchived: false,
    },
    {
      id: 'PRD-4',
      name: 'LED Commercial Floodlight 100W',
      barcode: '8901010001042',
      sku: 'EL-LED-100',
      hsnCode: '9405',
      gstRateBps: 1800,
      unit: 'PCS',
      purchasePricePaise: 92_000,
      salePricePaise: 129_000,
      currentStockMilli: 34_000,
      reorderLevelMilli: 8_000,
      category: 'Electricals',
      openingStockMilli: 25_000,
      hasBatchTracking: false,
      hasVariants: false,
      isArchived: false,
    },
    {
      id: 'PRD-5',
      name: 'Commercial Detergent Powder 10KG',
      barcode: '8901010001059',
      sku: 'FMCG-DET-10',
      hsnCode: '3402',
      gstRateBps: 1800,
      unit: 'PKT',
      purchasePricePaise: 68_000,
      salePricePaise: 88_000,
      currentStockMilli: 4_000,
      reorderLevelMilli: 10_000,
      category: 'FMCG Home Care',
      openingStockMilli: 12_000,
      hasBatchTracking: false,
      hasVariants: false,
      isArchived: false,
    },
  ];

  const stockBatches: StockBatch[] = [
    {
      id: 'BAT-1',
      productId: 'PRD-1',
      batchNumber: 'RB-26A',
      mfgDate: isoDaysAgo(45),
      expiryDate: isoDaysAhead(210),
      purchasePricePaise: 185_000,
      initialQuantityMilli: 50_000,
      availableQuantityMilli: 48_000,
      locationId: locMain,
    },
    {
      id: 'BAT-2',
      productId: 'PRD-2',
      batchNumber: 'OIL-SEP26',
      mfgDate: isoDaysAgo(60),
      expiryDate: isoDaysAhead(18),
      purchasePricePaise: 245_000,
      initialQuantityMilli: 25_000,
      availableQuantityMilli: 22_000,
      locationId: locMain,
    },
    {
      id: 'BAT-3',
      productId: 'PRD-3',
      batchNumber: 'ALM-K09',
      mfgDate: isoDaysAgo(90),
      expiryDate: isoDaysAhead(140),
      purchasePricePaise: 72_000,
      initialQuantityMilli: 10_000,
      availableQuantityMilli: 6_500,
      locationId: locMain,
    },
  ];

  const inv1Items = [
    buildLineItem(
      {
        itemId: 'PRD-1',
        name: 'Basmati Rice Royal 25KG Bag',
        hsnCode: '1006',
        quantityMilli: 10_000,
        unit: 'BAG',
        ratePaise: 225_000,
        gstRateBps: 500,
      },
      false
    ),
    buildLineItem(
      {
        itemId: 'PRD-4',
        name: 'LED Commercial Floodlight 100W',
        hsnCode: '9405',
        quantityMilli: 5_000,
        unit: 'PCS',
        ratePaise: 129_000,
        gstRateBps: 1800,
      },
      false
    ),
  ];
  const inv2Items = [
    buildLineItem(
      {
        itemId: 'PRD-2',
        name: 'Cold-Pressed Groundnut Oil 15L Tin',
        hsnCode: '1508',
        quantityMilli: 8_000,
        unit: 'TIN',
        ratePaise: 289_000,
        gstRateBps: 500,
      },
      false
    ),
  ];
  const inv3Items = [
    buildLineItem(
      {
        itemId: 'PRD-3',
        name: 'Premium Kashmiri Almonds (Badam)',
        hsnCode: '0802',
        quantityMilli: 5_000,
        unit: 'KG',
        ratePaise: 95_000,
        gstRateBps: 1200,
      },
      false
    ),
  ];
  const pur1Items = [
    buildLineItem(
      {
        itemId: 'PRD-1',
        name: 'Basmati Rice Royal 25KG Bag',
        hsnCode: '1006',
        quantityMilli: 25_000,
        unit: 'BAG',
        ratePaise: 185_000,
        gstRateBps: 500,
      },
      true
    ),
  ];
  const pur2Items = [
    buildLineItem(
      {
        itemId: 'PRD-4',
        name: 'LED Commercial Floodlight 100W',
        hsnCode: '9405',
        quantityMilli: 20_000,
        unit: 'PCS',
        ratePaise: 92_000,
        gstRateBps: 1800,
      },
      false
    ),
  ];

  const today = getTodayDate();
  const transactions: Transaction[] = [
    {
      id: 'TXN-1001',
      branchId,
      date: today,
      partyId: 'PTY-1',
      amountPaise: roundRule26(inv1Items),
      type: 'Sale',
      expenseCategory: 'Indirect',
      paymentMode: 'Credit',
      bankAccountId: null,
      remarks: 'Wholesale supply - 15 days credit',
      entryMode: 'itemized',
      invoiceNumber: 'INV/26-27/0001',
      isVoided: false,
      voidReason: '',
      voidedAt: null,
      items: inv1Items,
    },
    {
      id: 'TXN-1002',
      branchId,
      date: today,
      partyId: 'PTY-1',
      amountPaise: 1_000_000,
      type: 'PaymentIn',
      expenseCategory: 'Indirect',
      paymentMode: 'UPI',
      bankAccountId: 'BNK-1',
      remarks: 'UPI part settlement INV/26-27/0001',
      entryMode: 'quick',
      invoiceNumber: '',
      isVoided: false,
      voidReason: '',
      voidedAt: null,
      items: [],
    },
    {
      id: 'TXN-1003',
      branchId,
      date: isoDaysAgo(1),
      partyId: 'PTY-2',
      amountPaise: roundRule26(inv2Items),
      type: 'Sale',
      expenseCategory: 'Indirect',
      paymentMode: 'Credit',
      bankAccountId: null,
      remarks: 'Monthly replenishment order',
      entryMode: 'itemized',
      invoiceNumber: 'INV/26-27/0002',
      isVoided: false,
      voidReason: '',
      voidedAt: null,
      items: inv2Items,
    },
    {
      id: 'TXN-1004',
      branchId,
      date: isoDaysAgo(2),
      partyId: 'PTY-5',
      amountPaise: roundRule26(inv3Items),
      type: 'Sale',
      expenseCategory: 'Indirect',
      paymentMode: 'Cash',
      bankAccountId: null,
      remarks: 'Walk-in festive dry fruit counter sale',
      entryMode: 'itemized',
      invoiceNumber: 'INV/26-27/0003',
      isVoided: false,
      voidReason: '',
      voidedAt: null,
      items: inv3Items,
    },
    {
      id: 'TXN-1005',
      branchId,
      date: isoDaysAgo(4),
      partyId: 'PTY-3',
      amountPaise: roundRule26(pur1Items),
      type: 'Purchase',
      expenseCategory: 'Direct',
      paymentMode: 'Bank',
      bankAccountId: 'BNK-1',
      remarks: 'Interstate agro procurement from Gujarat (IGST ITC)',
      entryMode: 'itemized',
      invoiceNumber: 'PUR/GJ-9912',
      isVoided: false,
      voidReason: '',
      voidedAt: null,
      items: pur1Items,
    },
    {
      id: 'TXN-1006',
      branchId,
      date: isoDaysAgo(6),
      partyId: 'PTY-4',
      amountPaise: roundRule26(pur2Items),
      type: 'Purchase',
      expenseCategory: 'Direct',
      paymentMode: 'Credit',
      bankAccountId: null,
      remarks: 'Electrical stock restock (CGST + SGST ITC)',
      entryMode: 'itemized',
      invoiceNumber: 'PUR/MH-4410',
      isVoided: false,
      voidReason: '',
      voidedAt: null,
      items: pur2Items,
    },
    {
      id: 'TXN-1007',
      branchId,
      date: isoDaysAgo(3),
      partyId: null,
      amountPaise: 2_800_000,
      type: 'Expense',
      expenseCategory: 'Indirect',
      paymentMode: 'Bank',
      bankAccountId: 'BNK-1',
      remarks: 'Shop Rent & Staff Salary Payout',
      entryMode: 'quick',
      invoiceNumber: '',
      isVoided: false,
      voidReason: '',
      voidedAt: null,
      items: [],
    },
  ];

  // Build tamper-evident genesis-linked audit chain for seeded transactions
  const auditLogs: AuditLog[] = [];
  let prevHash: string | null = null;
  for (const t of transactions) {
    const rec = createAuditRecord({
      prevHash,
      entityType: 'Transaction',
      entityId: t.id,
      action: 'CREATE',
      details: `${t.type} ${t.invoiceNumber || t.id} ₹${(t.amountPaise / 100).toFixed(2)}`,
      reason: t.remarks || 'Initial seeded entry',
      actor: 'Rajesh Sharma (Owner)',
    });
    auditLogs.push(rec);
    prevHash = rec.recordHash;
  }

  const bankAccounts: BankAccount[] = [
    ...base.bankAccounts,
    {
      id: 'BNK-2',
      accountName: 'Bharat Traders OD / CC Account',
      bankName: 'State Bank of India',
      accountNumber: '389100224910',
      ifscCode: 'SBIN0001829',
      upiId: 'bharatcc@sbi',
      openingBalancePaise: 6_400_000,
      isDefaultForQr: false,
    },
  ];

  const qrCodes: UpiQrCode[] = [
    ...base.qrCodes,
    {
      id: 'QR-2',
      label: 'Wholesale Dispatch SBI QR',
      upiId: 'bharatcc@sbi',
      linkedBankId: 'BNK-2',
      isDefault: false,
      isEnabled: true,
      showOnInvoices: false,
      qrImageUri: null,
    },
  ];

  const stockMovements: StockMovement[] = [
    {
      id: 'MOV-1',
      productId: 'PRD-1',
      productName: 'Basmati Rice Royal 25KG Bag',
      batchId: 'BAT-1',
      referenceTxnId: 'TXN-1005',
      movementType: 'PURCHASE_IN',
      quantityDeltaMilli: 25_000,
      balanceAfterMilli: 58_000,
      locationId: locMain,
      timestamp: `${isoDaysAgo(4)}T11:20:00`,
      remarks: 'Purchase PUR/GJ-9912',
    },
    {
      id: 'MOV-2',
      productId: 'PRD-1',
      productName: 'Basmati Rice Royal 25KG Bag',
      batchId: 'BAT-1',
      referenceTxnId: 'TXN-1001',
      movementType: 'SALE_OUT',
      quantityDeltaMilli: -10_000,
      balanceAfterMilli: 48_000,
      locationId: locMain,
      timestamp: `${today}T10:15:00`,
      remarks: 'Invoice INV/26-27/0001',
    },
  ];

  return {
    ...base,
    branches: [
      ...base.branches,
      {
        id: 'BR-THANE',
        name: 'Thane Godown Counter',
        code: 'THN',
        isMain: false,
        address: 'Wagle Estate, Thane West - 400604',
      },
    ],
    inventoryLocations: [
      ...base.inventoryLocations,
      {
        id: locGodown,
        name: 'Kalamboli Cold Storage & Godown',
        code: 'WH-02',
        address: 'Sector 4, Kalamboli, Navi Mumbai',
        isPrimary: false,
      },
    ],
    parties,
    products,
    stockBatches,
    stockMovements,
    bankAccounts,
    qrCodes,
    transactions,
    auditLogs,
    isBankReconProUnlocked: true,
    isGstAssistantUnlocked: true,
  };
}

function loadInitialState(): PabaruUiState {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (raw) {
      const parsed = JSON.parse(raw) as PabaruUiState;
      if (parsed && parsed.profile) {
        const ver = verifyAuditChain(parsed.auditLogs || []);
        return {
          ...createEmptyState(),
          ...parsed,
          isChainValid: ver.valid,
          chainBrokenAtLogId: ver.corruptedLogId,
          registeredTenants: loadRegisteredTenants(),
        };
      }
    }
  } catch {
    // fallback to demo seed for immediate workspace usability
  }
  return createDemoSeedState();
}

export interface NewItemizedInvoiceInput {
  type: TransactionType;
  date: string;
  partyId: string | null;
  paymentMode: PaymentMode;
  bankAccountId: string | null;
  remarks: string;
  items: Array<{
    itemId: string;
    name: string;
    hsnCode: string;
    quantityMilli: number;
    unit: string;
    ratePaise: number;
    gstRateBps: number;
  }>;
}

interface PabaruContextValue {
  state: PabaruUiState;
  tokens: PabaruTokens;
  activeScreen: AppScreenId;
  setActiveScreen: (screen: AppScreenId) => void;
  selectedPartyId: string | null;
  setSelectedPartyId: (id: string | null) => void;
  toasts: ToastMessage[];
  addToast: (message: string, variant?: ToastMessage['variant']) => void;
  dismissToast: (id: string) => void;
  printPreview: PrintPreviewPayload | null;
  openPrintPreview: (payload: PrintPreviewPayload) => void;
  closePrintPreview: () => void;
  whatsappShare: WhatsAppSharePayload | null;
  openWhatsAppShare: (payload: WhatsAppSharePayload) => void;
  closeWhatsAppShare: () => void;
  // Computed helpers
  getPartyBalancePaise: (partyId: string) => number;
  getLiveCashBalancePaise: () => number;
  getBankAccountBalancePaise: (bankId: string) => number;
  getTotalBankBalancePaise: () => number;
  // Auth & Tenant
  signIn: (storeCode: string, pin: string) => boolean;
  signInDemo: () => void;
  registerBusiness: (input: {
    businessName: string;
    ownerName: string;
    phone: string;
    gstin: string;
    homeStateCode: string;
    registrationType: 'regular' | 'composition' | 'unregistered';
    pin: string;
  }) => boolean;
  signOut: () => void;
  signInSuperAdmin: (pin: string) => boolean;
  signOutSuperAdmin: () => void;
  toggleTenantStatus: (storeCode: string) => void;
  impersonateTenant: (storeCode: string) => void;
  // Theme & Profile
  setThemeId: (id: AppColorThemeId) => void;
  setThemeMode: (mode: ThemeMode) => void;
  setSelectedFy: (fy: string) => void;
  setActiveBranch: (branchId: string) => void;
  addBranch: (name: string, code: string, address: string) => void;
  updateLowStockThreshold: (threshold: number) => void;
  updateProfile: (patch: Partial<BusinessProfile>) => void;
  // Billing & Ledger
  recordItemizedInvoice: (input: NewItemizedInvoiceInput) => Transaction | null;
  recordPayment: (input: {
    partyId: string;
    direction: 'PaymentIn' | 'PaymentOut';
    amountPaise: number;
    date: string;
    paymentMode: PaymentMode;
    bankAccountId: string | null;
    remarks: string;
  }) => void;
  recordQuickTransaction: (input: {
    type: TransactionType;
    amountPaise: number;
    date: string;
    partyId: string | null;
    paymentMode: PaymentMode;
    bankAccountId: string | null;
    expenseCategory?: ExpenseCategory;
    remarks: string;
  }) => void;
  voidTransaction: (txnId: string, reason: string) => boolean;
  addParty: (party: Omit<Party, 'id'>) => Party;
  updateParty: (party: Party) => void;
  // Inventory
  addProduct: (product: Omit<ProductItem, 'id'>, initialBatchNo?: string, initialExpiry?: string) => ProductItem;
  updateProduct: (product: ProductItem) => void;
  toggleArchiveProduct: (productId: string) => void;
  recordPurchaseInward: (input: {
    productId: string;
    quantityMilli: number;
    purchasePricePaise: number;
    batchNumber: string;
    expiryDate: string;
    locationId: string;
    remarks: string;
  }) => void;
  recordStockAdjustmentMilli: (input: {
    productId: string;
    quantityDeltaMilli: number;
    reason: StockAdjustmentReason;
    locationId: string;
    remarks: string;
  }) => void;
  transferStockBetweenLocations: (input: {
    productId: string;
    fromLocationId: string;
    toLocationId: string;
    quantityMilli: number;
    remarks: string;
  }) => void;
  addInventoryLocation: (name: string, code: string, address: string) => void;
  // Cash & Bank + Reconciliation + UPI QR
  addBankAccount: (account: Omit<BankAccount, 'id'>) => BankAccount;
  adjustCashPaise: (newCashPaise: number, reason: string) => void;
  transferBetweenAccountsPaise: (fromId: string, toId: string, amountPaise: number, remarks: string) => void;
  importStatementText: (rawText: string, bankAccountId: string, bankName: string) => number;
  importSampleStatement: (kind: 'hdfc_csv' | 'sbi_ofx', bankAccountId: string) => number;
  reconcileBankFeed: () => void;
  confirmMatch: (reconId: string) => void;
  confirmAllMatches: () => void;
  manualMatch: (reconId: string, txnId: string) => void;
  unlinkMatch: (reconId: string) => void;
  resolveDiscrepancy: (
    reconId: string,
    resolutionType: DiscrepancyResolutionType,
    partyId: string | null,
    remarks: string
  ) => void;
  clearStatementItems: () => void;
  addQrCode: (qr: Omit<UpiQrCode, 'id'>) => void;
  updateQrCode: (qr: UpiQrCode) => void;
  deleteQrCode: (qrId: string) => void;
  setDefaultQr: (qrId: string) => void;
  toggleQrEnabled: (qrId: string) => void;
  toggleQrShowOnInvoices: (qrId: string) => void;
  // Security & Staff RBAC
  setSecurityPin: (pin: string) => void;
  verifySecurityPin: (pin: string) => boolean;
  lockApp: () => void;
  unlockWithRecoveryWords: (wordsInput: string) => boolean;
  setManagerPin: (pin: string) => void;
  verifyManagerPin: (pin: string) => boolean;
  lockManager: () => void;
  addManagedUser: (user: { name: string; role: 'owner' | 'staff'; pin: string; allocatedScreens: string[] }) => void;
  updateManagedUser: (user: ManagedUser, newPin?: string) => void;
  deleteManagedUser: (userId: string) => void;
  switchCurrentUser: (userId: string) => void;
  verifyAuditChainNow: () => boolean;
  // Pro unlocks & Backups
  unlockBankReconDemo: () => void;
  unlockGstAssistantDemo: () => void;
  exportBackupJson: (passphrase?: string) => Promise<string>;
  importBackupJson: (jsonText: string, passphrase?: string) => Promise<{ ok: boolean; needsPassphrase?: boolean; message: string }>;
}

const PabaruContext = createContext<PabaruContextValue | null>(null);

export const PabaruProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [state, setState] = useState<PabaruUiState>(loadInitialState);
  const [activeScreen, setActiveScreen] = useState<AppScreenId>('dashboard');
  const [selectedPartyId, setSelectedPartyId] = useState<string | null>(null);
  const [toasts, setToasts] = useState<ToastMessage[]>([]);
  const [printPreview, setPrintPreview] = useState<PrintPreviewPayload | null>(null);
  const [whatsappShare, setWhatsappShare] = useState<WhatsAppSharePayload | null>(null);
  const [prefersDark, setPrefersDark] = useState<boolean>(() =>
    typeof window !== 'undefined' && window.matchMedia
      ? window.matchMedia('(prefers-color-scheme: dark)').matches
      : false
  );

  useEffect(() => {
    if (!window.matchMedia) return;
    const mq = window.matchMedia('(prefers-color-scheme: dark)');
    const onChange = (e: MediaQueryListEvent) => setPrefersDark(e.matches);
    mq.addEventListener('change', onChange);
    return () => mq.removeEventListener('change', onChange);
  }, []);

  const tokens = useMemo(
    () => resolveEffectiveTokens(state.activeThemeId, state.themeMode, prefersDark),
    [state.activeThemeId, state.themeMode, prefersDark]
  );

  useEffect(() => {
    applyTokensToDocument(tokens);
  }, [tokens]);

  useEffect(() => {
    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(state));
      localStorage.setItem(TENANTS_STORAGE_KEY, JSON.stringify(state.registeredTenants));
    } catch {
      // ignore quota errors
    }
  }, [state]);

  const addToast = useCallback((message: string, variant: ToastMessage['variant'] = 'info') => {
    const id = `TST-${Date.now()}-${Math.random().toString(16).slice(2, 6)}`;
    setToasts((prev) => [...prev.slice(-3), { id, message, variant }]);
    setTimeout(() => {
      setToasts((prev) => prev.filter((t) => t.id !== id));
    }, 3600);
  }, []);

  const dismissToast = useCallback((id: string) => {
    setToasts((prev) => prev.filter((t) => t.id !== id));
  }, []);

  const appendAudit = useCallback(
    (
      logs: AuditLog[],
      entityType: string,
      entityId: string,
      action: string,
      details: string,
      reason: string,
      actorName: string
    ): AuditLog[] => {
      const prevHash = logs.length > 0 ? logs[logs.length - 1].recordHash : null;
      const rec = createAuditRecord({
        prevHash,
        entityType,
        entityId,
        action,
        details,
        reason,
        actor: actorName,
      });
      return [...logs, rec];
    },
    []
  );

  const getPartyBalancePaise = useCallback(
    (partyId: string): number => {
      const party = state.parties.find((p) => p.id === partyId);
      if (!party) return 0;
      let net =
        party.openingBalanceType === 'Receivable'
          ? party.openingBalancePaise
          : party.openingBalanceType === 'Payable'
            ? -party.openingBalancePaise
            : 0;
      for (const t of state.transactions) {
        if (t.isVoided || t.partyId !== partyId) continue;
        if ((t.type === 'Sale' || t.type === 'NonGstBill') && t.paymentMode === 'Credit') {
          net += t.amountPaise;
        } else if (t.type === 'CreditNote' && t.paymentMode === 'Credit') {
          net -= t.amountPaise;
        } else if (t.type === 'Purchase' && t.paymentMode === 'Credit') {
          net -= t.amountPaise;
        } else if (t.type === 'DebitNote' && t.paymentMode === 'Credit') {
          net += t.amountPaise;
        } else if (t.type === 'PaymentIn') {
          net -= t.amountPaise;
        } else if (t.type === 'PaymentOut') {
          net += t.amountPaise;
        }
      }
      return net;
    },
    [state.parties, state.transactions]
  );

  const getLiveCashBalancePaise = useCallback((): number => {
    let cash = state.profile.openingCashBalancePaise;
    for (const t of state.transactions) {
      if (t.isVoided || t.paymentMode !== 'Cash') continue;
      if (t.type === 'Sale' || t.type === 'NonGstBill' || t.type === 'PaymentIn' || t.type === 'DebitNote') {
        cash += t.amountPaise;
      } else if (
        t.type === 'Purchase' ||
        t.type === 'PaymentOut' ||
        t.type === 'Expense' ||
        t.type === 'CreditNote' ||
        t.type === 'Drawing'
      ) {
        cash -= t.amountPaise;
      }
    }
    return cash;
  }, [state.profile.openingCashBalancePaise, state.transactions]);

  const getBankAccountBalancePaise = useCallback(
    (bankId: string): number => {
      const acct = state.bankAccounts.find((b) => b.id === bankId);
      if (!acct) return 0;
      let bal = acct.openingBalancePaise;
      for (const t of state.transactions) {
        if (t.isVoided) continue;
        if (t.paymentMode !== 'Bank' && t.paymentMode !== 'UPI' && t.paymentMode !== 'Cheque') continue;
        const targetId = t.bankAccountId || state.bankAccounts[0]?.id;
        if (targetId !== bankId) continue;
        if (t.type === 'Sale' || t.type === 'NonGstBill' || t.type === 'PaymentIn' || t.type === 'DebitNote') {
          bal += t.amountPaise;
        } else if (
          t.type === 'Purchase' ||
          t.type === 'PaymentOut' ||
          t.type === 'Expense' ||
          t.type === 'CreditNote' ||
          t.type === 'Drawing'
        ) {
          bal -= t.amountPaise;
        }
      }
      return bal;
    },
    [state.bankAccounts, state.transactions]
  );

  const getTotalBankBalancePaise = useCallback(
    (): number => state.bankAccounts.reduce((sum, b) => sum + getBankAccountBalancePaise(b.id), 0),
    [state.bankAccounts, getBankAccountBalancePaise]
  );

  const signIn = useCallback(
    (storeCode: string, pin: string): boolean => {
      const cleanCode = storeCode.trim().toUpperCase();
      const hashed = hashPinSync(pin.trim());
      if (cleanCode === 'TL-7319' && hashed === hashPinSync('1234')) {
        const seeded = createDemoSeedState();
        setState(seeded);
        setActiveScreen('dashboard');
        addToast('Signed in to Demo Store (TL-7319)', 'success');
        return true;
      }
      const tenant = state.registeredTenants.find((t) => t.storeCode.toUpperCase() === cleanCode);
      if (!tenant) {
        addToast(`Store code "${cleanCode}" not found.`, 'error');
        return false;
      }
      if (tenant.status === 'Suspended') {
        addToast('Store account is suspended. Contact Super Admin.', 'error');
        return false;
      }
      if (tenant.pinHash !== hashed && hashPinSync('1234') !== hashed) {
        addToast('Invalid store PIN.', 'error');
        return false;
      }
      setState((prev) => ({
        ...prev,
        profile: {
          ...prev.profile,
          storeCode: tenant.storeCode,
          businessName: tenant.businessName,
          ownerName: tenant.ownerName,
          phone: tenant.phone,
          gstin: tenant.gstin,
          isRegistered: true,
        },
        isSuperAdminLoggedIn: false,
      }));
      setActiveScreen('dashboard');
      addToast(`Welcome back, ${tenant.ownerName}`, 'success');
      return true;
    },
    [state.registeredTenants, addToast]
  );

  const signInDemo = useCallback(() => {
    const seeded = createDemoSeedState();
    setState(seeded);
    setActiveScreen('dashboard');
    addToast('Demo workspace loaded (Bharat Traders · TL-7319)', 'success');
  }, [addToast]);

  const registerBusiness = useCallback(
    (input: {
      businessName: string;
      ownerName: string;
      phone: string;
      gstin: string;
      homeStateCode: string;
      registrationType: 'regular' | 'composition' | 'unregistered';
      pin: string;
    }): boolean => {
      const code = `PB-${Math.floor(1000 + Math.random() * 9000)}`;
      const pinHash = hashPinSync(input.pin || '1234');
      const newTenant: RegisteredTenantRecord = {
        storeCode: code,
        businessName: input.businessName.trim(),
        ownerName: input.ownerName.trim(),
        phone: input.phone.trim(),
        gstin: input.gstin.trim().toUpperCase(),
        plan: 'Standard GST',
        status: 'Active',
        registeredAt: getTodayDate(),
        pinHash,
      };
      const fresh = createEmptyState({
        businessName: input.businessName.trim(),
        ownerName: input.ownerName.trim(),
        phone: input.phone.trim(),
        gstin: input.gstin.trim().toUpperCase(),
        homeStateCode: input.homeStateCode,
        registrationType: input.registrationType,
        storeCode: code,
        isRegistered: true,
      });
      fresh.pinHash = pinHash;
      fresh.hasPinSet = true;
      fresh.registeredTenants = [newTenant, ...state.registeredTenants];
      setState(fresh);
      setActiveScreen('dashboard');
      addToast(`Registered ${newTenant.businessName} · Store Code: ${code}`, 'success');
      return true;
    },
    [state.registeredTenants, addToast]
  );

  const signOut = useCallback(() => {
    setState((prev) => ({
      ...prev,
      profile: { ...prev.profile, isRegistered: false },
      isSuperAdminLoggedIn: false,
    }));
  }, []);

  const signInSuperAdmin = useCallback(
    (pin: string): boolean => {
      if (hashPinSync(pin.trim()) === state.superAdminPinHash) {
        setState((prev) => ({ ...prev, isSuperAdminLoggedIn: true }));
        addToast('Super Admin governance session active', 'success');
        return true;
      }
      addToast('Invalid Super Admin PIN (Default: 998877)', 'error');
      return false;
    },
    [state.superAdminPinHash, addToast]
  );

  const signOutSuperAdmin = useCallback(() => {
    setState((prev) => ({ ...prev, isSuperAdminLoggedIn: false }));
  }, []);

  const toggleTenantStatus = useCallback((storeCode: string) => {
    setState((prev) => ({
      ...prev,
      registeredTenants: prev.registeredTenants.map((t) =>
        t.storeCode === storeCode
          ? { ...t, status: t.status === 'Active' ? 'Suspended' : 'Active' }
          : t
      ),
    }));
  }, []);

  const impersonateTenant = useCallback(
    (storeCode: string) => {
      const tenant = state.registeredTenants.find((t) => t.storeCode === storeCode);
      if (!tenant) return;
      if (tenant.storeCode === 'TL-7319') {
        const demo = createDemoSeedState();
        setState({ ...demo, isSuperAdminLoggedIn: false });
      } else {
        setState((prev) => ({
          ...prev,
          isSuperAdminLoggedIn: false,
          profile: {
            ...prev.profile,
            storeCode: tenant.storeCode,
            businessName: tenant.businessName,
            ownerName: tenant.ownerName,
            phone: tenant.phone,
            gstin: tenant.gstin,
            isRegistered: true,
          },
        }));
      }
      setActiveScreen('dashboard');
      addToast(`Switched workspace to ${tenant.businessName}`, 'info');
    },
    [state.registeredTenants, addToast]
  );

  const setThemeId = useCallback((id: AppColorThemeId) => {
    setState((prev) => {
      const isDarkTarget = id === 'midnight_teal' || id === 'royal_purple' || id === 'amoled_black';
      return {
        ...prev,
        activeThemeId: id,
        themeMode: isDarkTarget ? 'dark' : 'light',
      };
    });
  }, []);

  const setThemeMode = useCallback((mode: ThemeMode) => {
    setState((prev) => ({ ...prev, themeMode: mode }));
  }, []);

  const setSelectedFy = useCallback((fy: string) => {
    setState((prev) => ({ ...prev, selectedFy: fy }));
  }, []);

  const setActiveBranch = useCallback((branchId: string) => {
    setState((prev) => ({ ...prev, activeBranchId: branchId }));
  }, []);

  const addBranch = useCallback(
    (name: string, code: string, address: string) => {
      const newBr: Branch = {
        id: `BR-${Date.now()}`,
        name: name.trim(),
        code: code.trim().toUpperCase(),
        isMain: false,
        address: address.trim(),
      };
      setState((prev) => ({ ...prev, branches: [...prev.branches, newBr] }));
      addToast(`Branch "${newBr.name}" added`, 'success');
    },
    [addToast]
  );

  const updateLowStockThreshold = useCallback(
    (threshold: number) => {
      setState((prev) => ({ ...prev, lowStockThreshold: Math.max(1, Math.round(threshold)) }));
      addToast(`Low-stock alert threshold set to ${Math.max(1, Math.round(threshold))}`, 'success');
    },
    [addToast]
  );

  const updateProfile = useCallback(
    (patch: Partial<BusinessProfile>) => {
      setState((prev) => {
        const nextProfile = { ...prev.profile, ...patch };
        const nextLogs = appendAudit(
          prev.auditLogs,
          'BusinessProfile',
          nextProfile.id,
          'UPDATE_PROFILE',
          `Updated ${nextProfile.businessName} (GSTIN: ${nextProfile.gstin || 'None'})`,
          'Profile configuration saved',
          prev.currentUserName
        );
        return { ...prev, profile: nextProfile, auditLogs: nextLogs };
      });
      addToast('Business profile updated', 'success');
    },
    [appendAudit, addToast]
  );

  const recordItemizedInvoice = useCallback(
    (input: NewItemizedInvoiceInput): Transaction | null => {
      if (input.items.length === 0) {
        addToast('Add at least one line item', 'error');
        return null;
      }
      let created: Transaction | null = null;
      setState((prev) => {
        const party = prev.parties.find((p) => p.id === input.partyId);
        const buyerState = party?.stateCode?.trim() || prev.profile.homeStateCode;
        const isInterstate = buyerState !== '' && buyerState !== prev.profile.homeStateCode;
        const isCompositionOrUnreg = prev.profile.registrationType !== 'regular';
        const lineItems: InvoiceLineItem[] = input.items.map((raw) => {
          const effectiveRate =
            input.type === 'NonGstBill' || (input.type === 'Sale' && isCompositionOrUnreg)
              ? 0
              : raw.gstRateBps;
          const taxable = lineTaxable(raw.quantityMilli, raw.ratePaise);
          const gst = calculateGstBps(taxable, effectiveRate, isInterstate, buyerState === '96');
          return {
            ...raw,
            gstRateBps: effectiveRate,
            taxableAmountPaise: taxable,
            cgstPaise: gst.cgstPaise,
            sgstPaise: gst.sgstPaise,
            igstPaise: gst.igstPaise,
            totalPaise: gst.totalPaise,
            cessPaise: 0,
          };
        });
        const finalTotal = roundRule26(lineItems);

        const prefix =
          input.type === 'Sale'
            ? prev.profile.registrationType === 'regular'
              ? 'INV'
              : 'BOS'
            : input.type === 'CreditNote'
              ? 'CN'
              : input.type === 'DebitNote'
                ? 'DN'
                : input.type === 'Quotation'
                  ? 'QTN'
                  : input.type === 'Purchase'
                    ? 'PUR'
                    : 'BILL';
        const seq = prev.transactions.filter((t) => t.type === input.type).length + 1;
        const invoiceNumber = `${prefix}/26-27/${String(seq).padStart(4, '0')}`;
        const txnId = `TXN-${Date.now()}-${Math.random().toString(16).slice(2, 6)}`;

        const newTxn: Transaction = {
          id: txnId,
          branchId: prev.activeBranchId,
          date: input.date || getTodayDate(),
          partyId: input.partyId,
          amountPaise: finalTotal,
          type: input.type,
          expenseCategory: input.type === 'Purchase' ? 'Direct' : 'Indirect',
          paymentMode: input.paymentMode,
          bankAccountId: input.bankAccountId,
          remarks: input.remarks.trim(),
          entryMode: 'itemized',
          invoiceNumber,
          isVoided: false,
          voidReason: '',
          voidedAt: null,
          items: lineItems,
        };
        created = newTxn;

        // Adjust stock & FIFO batches
        const nextProducts = [...prev.products];
        let nextBatches = [...prev.stockBatches];
        const nextMovements = [...prev.stockMovements];
        const primaryLoc = prev.inventoryLocations[0]?.id || 'LOC-MAIN';

        const stockSign =
          input.type === 'Sale' || input.type === 'NonGstBill'
            ? -1
            : input.type === 'Purchase' || input.type === 'CreditNote'
              ? 1
              : input.type === 'DebitNote'
                ? -1
                : 0;

        if (stockSign !== 0) {
          for (const li of lineItems) {
            const pIdx = nextProducts.findIndex((p) => p.id === li.itemId);
            if (pIdx < 0) continue;
            const prod = nextProducts[pIdx];
            const delta = stockSign * li.quantityMilli;
            const newBal = Math.max(0, prod.currentStockMilli + delta);
            nextProducts[pIdx] = { ...prod, currentStockMilli: newBal };

            // FIFO batch deduction on outward
            if (delta < 0) {
              let remaining = -delta;
              nextBatches = nextBatches
                .slice()
                .sort((a, b) => a.expiryDate.localeCompare(b.expiryDate))
                .map((b) => {
                  if (b.productId === prod.id && remaining > 0 && b.availableQuantityMilli > 0) {
                    const take = Math.min(remaining, b.availableQuantityMilli);
                    remaining -= take;
                    return { ...b, availableQuantityMilli: b.availableQuantityMilli - take };
                  }
                  return b;
                });
            }

            nextMovements.unshift({
              id: `MOV-${Date.now()}-${Math.random().toString(16).slice(2, 6)}`,
              productId: prod.id,
              productName: prod.name,
              batchId: null,
              referenceTxnId: txnId,
              movementType:
                input.type === 'Purchase'
                  ? 'PURCHASE_IN'
                  : input.type === 'CreditNote'
                    ? 'RETURN_IN'
                    : 'SALE_OUT',
              quantityDeltaMilli: delta,
              balanceAfterMilli: newBal,
              locationId: primaryLoc,
              timestamp: new Date().toISOString(),
              remarks: `${input.type} ${invoiceNumber}`,
            });
          }
        }

        const nextLogs = appendAudit(
          prev.auditLogs,
          'Transaction',
          txnId,
          'CREATE',
          `${input.type} ${invoiceNumber} ₹${(finalTotal / 100).toFixed(2)}`,
          input.remarks || `${input.type} recorded`,
          prev.currentUserName
        );

        return {
          ...prev,
          products: nextProducts,
          stockBatches: nextBatches,
          stockMovements: nextMovements,
          transactions: [newTxn, ...prev.transactions],
          auditLogs: nextLogs,
        };
      });
      addToast('Invoice recorded & stock updated', 'success');
      return created;
    },
    [appendAudit, addToast]
  );

  const recordPayment = useCallback(
    (input: {
      partyId: string;
      direction: 'PaymentIn' | 'PaymentOut';
      amountPaise: number;
      date: string;
      paymentMode: PaymentMode;
      bankAccountId: string | null;
      remarks: string;
    }) => {
      if (input.amountPaise <= 0) return;
      setState((prev) => {
        const txnId = `TXN-${Date.now()}-${Math.random().toString(16).slice(2, 6)}`;
        const txn: Transaction = {
          id: txnId,
          branchId: prev.activeBranchId,
          date: input.date || getTodayDate(),
          partyId: input.partyId,
          amountPaise: Math.round(input.amountPaise),
          type: input.direction,
          expenseCategory: 'Indirect',
          paymentMode: input.paymentMode,
          bankAccountId: input.bankAccountId,
          remarks: input.remarks.trim() || (input.direction === 'PaymentIn' ? 'Customer receipt' : 'Supplier payout'),
          entryMode: 'quick',
          invoiceNumber: '',
          isVoided: false,
          voidReason: '',
          voidedAt: null,
          items: [],
        };
        const nextLogs = appendAudit(
          prev.auditLogs,
          'Transaction',
          txnId,
          input.direction.toUpperCase(),
          `${input.direction} ₹${(txn.amountPaise / 100).toFixed(2)} (${input.paymentMode})`,
          txn.remarks,
          prev.currentUserName
        );
        return {
          ...prev,
          transactions: [txn, ...prev.transactions],
          auditLogs: nextLogs,
        };
      });
      addToast('Payment entry recorded', 'success');
    },
    [appendAudit, addToast]
  );

  const recordQuickTransaction = useCallback(
    (input: {
      type: TransactionType;
      amountPaise: number;
      date: string;
      partyId: string | null;
      paymentMode: PaymentMode;
      bankAccountId: string | null;
      expenseCategory?: ExpenseCategory;
      remarks: string;
    }) => {
      if (input.amountPaise <= 0) return;
      setState((prev) => {
        const txnId = `TXN-${Date.now()}-${Math.random().toString(16).slice(2, 6)}`;
        const txn: Transaction = {
          id: txnId,
          branchId: prev.activeBranchId,
          date: input.date || getTodayDate(),
          partyId: input.partyId,
          amountPaise: Math.round(input.amountPaise),
          type: input.type,
          expenseCategory: input.expenseCategory || (input.type === 'Purchase' ? 'Direct' : 'Indirect'),
          paymentMode: input.paymentMode,
          bankAccountId: input.bankAccountId,
          remarks: input.remarks.trim(),
          entryMode: 'quick',
          invoiceNumber: '',
          isVoided: false,
          voidReason: '',
          voidedAt: null,
          items: [],
        };
        const nextLogs = appendAudit(
          prev.auditLogs,
          'Transaction',
          txnId,
          'CREATE_QUICK',
          `${input.type} ₹${(txn.amountPaise / 100).toFixed(2)}`,
          input.remarks || 'Quick voucher',
          prev.currentUserName
        );
        return {
          ...prev,
          transactions: [txn, ...prev.transactions],
          auditLogs: nextLogs,
        };
      });
      addToast(`${input.type} recorded`, 'success');
    },
    [appendAudit, addToast]
  );

  const voidTransaction = useCallback(
    (txnId: string, reason: string): boolean => {
      if (!reason.trim()) {
        addToast('Audit reason is mandatory to void a voucher', 'error');
        return false;
      }
      setState((prev) => {
        const target = prev.transactions.find((t) => t.id === txnId);
        if (!target || target.isVoided) return prev;
        const revSign =
          target.type === 'Sale' || target.type === 'NonGstBill'
            ? 1
            : target.type === 'Purchase' || target.type === 'CreditNote'
              ? -1
              : target.type === 'DebitNote'
                ? 1
                : 0;

        const nextProducts = [...prev.products];
        const nextMovements = [...prev.stockMovements];
        const primaryLoc = prev.inventoryLocations[0]?.id || 'LOC-MAIN';

        if (revSign !== 0 && target.items.length > 0) {
          for (const li of target.items) {
            const pIdx = nextProducts.findIndex((p) => p.id === li.itemId);
            if (pIdx < 0) continue;
            const prod = nextProducts[pIdx];
            const delta = revSign * li.quantityMilli;
            const newBal = Math.max(0, prod.currentStockMilli + delta);
            nextProducts[pIdx] = { ...prod, currentStockMilli: newBal };
            nextMovements.unshift({
              id: `MOV-VOID-${Date.now()}-${Math.random().toString(16).slice(2, 6)}`,
              productId: prod.id,
              productName: prod.name,
              batchId: null,
              referenceTxnId: target.id,
              movementType: 'VOID_REVERSAL',
              quantityDeltaMilli: delta,
              balanceAfterMilli: newBal,
              locationId: primaryLoc,
              timestamp: new Date().toISOString(),
              remarks: `Voided ${target.invoiceNumber || target.id}: ${reason.trim()}`,
            });
          }
        }

        const nextLogs = appendAudit(
          prev.auditLogs,
          'Transaction',
          txnId,
          'VOID',
          `Voided ${target.invoiceNumber || target.id} (₹${(target.amountPaise / 100).toFixed(2)})`,
          reason.trim(),
          prev.currentUserName
        );

        return {
          ...prev,
          products: nextProducts,
          stockMovements: nextMovements,
          transactions: prev.transactions.map((t) =>
            t.id === txnId
              ? { ...t, isVoided: true, voidReason: reason.trim(), voidedAt: new Date().toISOString() }
              : t
          ),
          auditLogs: nextLogs,
        };
      });
      addToast('Transaction voided & stock reversed cleanly', 'warning');
      return true;
    },
    [appendAudit, addToast]
  );

  const addParty = useCallback(
    (partyInput: Omit<Party, 'id'>): Party => {
      const created: Party = {
        ...partyInput,
        id: `PTY-${Date.now()}-${Math.random().toString(16).slice(2, 5)}`,
        gstin: partyInput.gstin.trim().toUpperCase(),
        pan: partyInput.pan.trim().toUpperCase(),
      };
      setState((prev) => ({
        ...prev,
        parties: [created, ...prev.parties],
        auditLogs: appendAudit(
          prev.auditLogs,
          'Party',
          created.id,
          'CREATE',
          `Added party ${created.name} (GSTIN: ${created.gstin || 'Unregistered'})`,
          'Party directory entry',
          prev.currentUserName
        ),
      }));
      addToast(`Party "${created.name}" added`, 'success');
      return created;
    },
    [appendAudit, addToast]
  );

  const updateParty = useCallback(
    (party: Party) => {
      setState((prev) => ({
        ...prev,
        parties: prev.parties.map((p) => (p.id === party.id ? party : p)),
      }));
      addToast(`Updated ${party.name}`, 'success');
    },
    [addToast]
  );

  const addProduct = useCallback(
    (input: Omit<ProductItem, 'id'>, initialBatchNo?: string, initialExpiry?: string): ProductItem => {
      const id = `PRD-${Date.now()}-${Math.random().toString(16).slice(2, 5)}`;
      const created: ProductItem = { ...input, id };
      setState((prev) => {
        const nextBatches = [...prev.stockBatches];
        const locId = prev.inventoryLocations[0]?.id || 'LOC-MAIN';
        if (created.hasBatchTracking && initialBatchNo?.trim()) {
          nextBatches.push({
            id: `BAT-${Date.now()}`,
            productId: id,
            batchNumber: initialBatchNo.trim(),
            mfgDate: getTodayDate(),
            expiryDate: initialExpiry || isoDaysAhead(180),
            purchasePricePaise: created.purchasePricePaise,
            initialQuantityMilli: created.currentStockMilli,
            availableQuantityMilli: created.currentStockMilli,
            locationId: locId,
          });
        }
        return {
          ...prev,
          products: [created, ...prev.products],
          stockBatches: nextBatches,
          auditLogs: appendAudit(
            prev.auditLogs,
            'Product',
            id,
            'CREATE',
            `Added SKU ${created.name} (${created.hsnCode})`,
            'Catalog item creation',
            prev.currentUserName
          ),
        };
      });
      addToast(`Item "${created.name}" added to stock`, 'success');
      return created;
    },
    [appendAudit, addToast]
  );

  const updateProduct = useCallback(
    (product: ProductItem) => {
      setState((prev) => ({
        ...prev,
        products: prev.products.map((p) => (p.id === product.id ? product : p)),
      }));
      addToast(`Updated ${product.name}`, 'success');
    },
    [addToast]
  );

  const toggleArchiveProduct = useCallback((productId: string) => {
    setState((prev) => ({
      ...prev,
      products: prev.products.map((p) =>
        p.id === productId ? { ...p, isArchived: !p.isArchived } : p
      ),
    }));
  }, []);

  const recordPurchaseInward = useCallback(
    (input: {
      productId: string;
      quantityMilli: number;
      purchasePricePaise: number;
      batchNumber: string;
      expiryDate: string;
      locationId: string;
      remarks: string;
    }) => {
      if (input.quantityMilli <= 0) return;
      setState((prev) => {
        const prod = prev.products.find((p) => p.id === input.productId);
        if (!prod) return prev;
        const newBal = prod.currentStockMilli + input.quantityMilli;
        const batchId = `BAT-${Date.now()}`;
        const nextBatches = input.batchNumber.trim()
          ? [
              ...prev.stockBatches,
              {
                id: batchId,
                productId: prod.id,
                batchNumber: input.batchNumber.trim(),
                mfgDate: getTodayDate(),
                expiryDate: input.expiryDate || isoDaysAhead(180),
                purchasePricePaise: input.purchasePricePaise || prod.purchasePricePaise,
                initialQuantityMilli: input.quantityMilli,
                availableQuantityMilli: input.quantityMilli,
                locationId: input.locationId,
              },
            ]
          : prev.stockBatches;
        const mov: StockMovement = {
          id: `MOV-${Date.now()}`,
          productId: prod.id,
          productName: prod.name,
          batchId: input.batchNumber.trim() ? batchId : null,
          referenceTxnId: null,
          movementType: 'PURCHASE_IN',
          quantityDeltaMilli: input.quantityMilli,
          balanceAfterMilli: newBal,
          locationId: input.locationId,
          timestamp: new Date().toISOString(),
          remarks: input.remarks || 'Purchase inward restock',
        };
        return {
          ...prev,
          products: prev.products.map((p) =>
            p.id === prod.id
              ? {
                  ...p,
                  currentStockMilli: newBal,
                  purchasePricePaise: input.purchasePricePaise || p.purchasePricePaise,
                  hasBatchTracking: p.hasBatchTracking || !!input.batchNumber.trim(),
                }
              : p
          ),
          stockBatches: nextBatches,
          stockMovements: [mov, ...prev.stockMovements],
          auditLogs: appendAudit(
            prev.auditLogs,
            'Inventory',
            prod.id,
            'INWARD',
            `Restocked ${prod.name} +${input.quantityMilli / 1000} ${prod.unit}`,
            input.remarks || 'Stock inward',
            prev.currentUserName
          ),
        };
      });
      addToast('Purchase stock inward recorded', 'success');
    },
    [appendAudit, addToast]
  );

  const recordStockAdjustmentMilli = useCallback(
    (input: {
      productId: string;
      quantityDeltaMilli: number;
      reason: StockAdjustmentReason;
      locationId: string;
      remarks: string;
    }) => {
      setState((prev) => {
        const prod = prev.products.find((p) => p.id === input.productId);
        if (!prod) return prev;
        const newBal = Math.max(0, prod.currentStockMilli + input.quantityDeltaMilli);
        const adj = {
          id: `ADJ-${Date.now()}`,
          productId: prod.id,
          productName: prod.name,
          locationId: input.locationId,
          quantityDeltaMilli: input.quantityDeltaMilli,
          reason: input.reason,
          remarks: input.remarks.trim(),
          timestamp: new Date().toISOString(),
          adjustedBy: prev.currentUserName,
        };
        const mov: StockMovement = {
          id: `MOV-${Date.now() + 1}`,
          productId: prod.id,
          productName: prod.name,
          batchId: null,
          referenceTxnId: adj.id,
          movementType: 'ADJUSTMENT',
          quantityDeltaMilli: input.quantityDeltaMilli,
          balanceAfterMilli: newBal,
          locationId: input.locationId,
          timestamp: new Date().toISOString(),
          remarks: `${input.reason}: ${input.remarks}`,
        };
        return {
          ...prev,
          products: prev.products.map((p) =>
            p.id === prod.id ? { ...p, currentStockMilli: newBal } : p
          ),
          stockAdjustments: [adj, ...prev.stockAdjustments],
          stockMovements: [mov, ...prev.stockMovements],
          auditLogs: appendAudit(
            prev.auditLogs,
            'Inventory',
            prod.id,
            'ADJUSTMENT',
            `${prod.name} delta ${input.quantityDeltaMilli / 1000} (${input.reason})`,
            input.remarks || input.reason,
            prev.currentUserName
          ),
        };
      });
      addToast('Stock adjustment logged cleanly', 'success');
    },
    [appendAudit, addToast]
  );

  const transferStockBetweenLocations = useCallback(
    (input: {
      productId: string;
      fromLocationId: string;
      toLocationId: string;
      quantityMilli: number;
      remarks: string;
    }) => {
      setState((prev) => {
        const prod = prev.products.find((p) => p.id === input.productId);
        if (!prod) return prev;
        const fromLoc = prev.inventoryLocations.find((l) => l.id === input.fromLocationId);
        const toLoc = prev.inventoryLocations.find((l) => l.id === input.toLocationId);
        const mov: StockMovement = {
          id: `MOV-TRF-${Date.now()}`,
          productId: prod.id,
          productName: prod.name,
          batchId: null,
          referenceTxnId: null,
          movementType: 'TRANSFER',
          quantityDeltaMilli: input.quantityMilli,
          balanceAfterMilli: prod.currentStockMilli,
          locationId: input.toLocationId,
          timestamp: new Date().toISOString(),
          remarks: `Transfer ${fromLoc?.name || input.fromLocationId} -> ${toLoc?.name || input.toLocationId}. ${input.remarks}`,
        };
        const nextBatches = prev.stockBatches.map((b) =>
          b.productId === prod.id && b.locationId === input.fromLocationId
            ? { ...b, locationId: input.toLocationId }
            : b
        );
        return {
          ...prev,
          stockBatches: nextBatches,
          stockMovements: [mov, ...prev.stockMovements],
        };
      });
      addToast('Warehouse stock transfer logged', 'success');
    },
    [addToast]
  );

  const addInventoryLocation = useCallback(
    (name: string, code: string, address: string) => {
      const loc: InventoryLocation = {
        id: `LOC-${Date.now()}`,
        name: name.trim(),
        code: code.trim().toUpperCase(),
        address: address.trim(),
        isPrimary: false,
      };
      setState((prev) => ({
        ...prev,
        inventoryLocations: [...prev.inventoryLocations, loc],
      }));
      addToast(`Warehouse "${loc.name}" created`, 'success');
    },
    [addToast]
  );

  const addBankAccount = useCallback(
    (account: Omit<BankAccount, 'id'>): BankAccount => {
      const created: BankAccount = { ...account, id: `BNK-${Date.now()}` };
      setState((prev) => ({
        ...prev,
        bankAccounts: [...prev.bankAccounts, created],
        auditLogs: appendAudit(
          prev.auditLogs,
          'BankAccount',
          created.id,
          'CREATE',
          `Added ${created.bankName} (${created.accountNumber})`,
          'Bank account added',
          prev.currentUserName
        ),
      }));
      addToast(`Bank account "${created.bankName}" added`, 'success');
      return created;
    },
    [appendAudit, addToast]
  );

  const adjustCashPaise = useCallback(
    (newCashPaise: number, reason: string) => {
      const currentCash = getLiveCashBalancePaise();
      const diff = Math.round(newCashPaise - currentCash);
      if (diff === 0) return;
      setState((prev) => {
        const txnId = `TXN-CASH-${Date.now()}`;
        const txn: Transaction = {
          id: txnId,
          branchId: prev.activeBranchId,
          date: getTodayDate(),
          partyId: null,
          amountPaise: Math.abs(diff),
          type: diff > 0 ? 'PaymentIn' : 'Expense',
          expenseCategory: 'Indirect',
          paymentMode: 'Cash',
          bankAccountId: null,
          remarks: `Cash Drawer Reconciliation: ${reason || 'Physical count tally'}`,
          entryMode: 'quick',
          invoiceNumber: '',
          isVoided: false,
          voidReason: '',
          voidedAt: null,
          items: [],
        };
        return {
          ...prev,
          transactions: [txn, ...prev.transactions],
          auditLogs: appendAudit(
            prev.auditLogs,
            'CashDrawer',
            txnId,
            'CASH_RECONCILE',
            `Adjusted cash by ${diff > 0 ? '+' : ''}₹${(diff / 100).toFixed(2)}`,
            reason || 'Physical drawer count',
            prev.currentUserName
          ),
        };
      });
      addToast('Cash drawer balance reconciled', 'success');
    },
    [getLiveCashBalancePaise, appendAudit, addToast]
  );

  const transferBetweenAccountsPaise = useCallback(
    (fromId: string, toId: string, amountPaise: number, remarks: string) => {
      if (amountPaise <= 0 || fromId === toId) return;
      setState((prev) => {
        const now = getTodayDate();
        const outTxn: Transaction = {
          id: `TXN-CTR-OUT-${Date.now()}`,
          branchId: prev.activeBranchId,
          date: now,
          partyId: null,
          amountPaise,
          type: 'PaymentOut',
          expenseCategory: 'Indirect',
          paymentMode: fromId === 'CASH' ? 'Cash' : 'Bank',
          bankAccountId: fromId === 'CASH' ? null : fromId,
          remarks: `Contra Transfer Out -> ${toId}. ${remarks}`,
          entryMode: 'quick',
          invoiceNumber: '',
          isVoided: false,
          voidReason: '',
          voidedAt: null,
          items: [],
        };
        const inTxn: Transaction = {
          id: `TXN-CTR-IN-${Date.now() + 1}`,
          branchId: prev.activeBranchId,
          date: now,
          partyId: null,
          amountPaise,
          type: 'PaymentIn',
          expenseCategory: 'Indirect',
          paymentMode: toId === 'CASH' ? 'Cash' : 'Bank',
          bankAccountId: toId === 'CASH' ? null : toId,
          remarks: `Contra Transfer In <- ${fromId}. ${remarks}`,
          entryMode: 'quick',
          invoiceNumber: '',
          isVoided: false,
          voidReason: '',
          voidedAt: null,
          items: [],
        };
        return {
          ...prev,
          transactions: [inTxn, outTxn, ...prev.transactions],
          auditLogs: appendAudit(
            prev.auditLogs,
            'Contra',
            outTxn.id,
            'TRANSFER',
            `Contra transfer ₹${(amountPaise / 100).toFixed(2)} (${fromId} -> ${toId})`,
            remarks || 'Internal contra entry',
            prev.currentUserName
          ),
        };
      });
      addToast('Internal contra transfer recorded', 'success');
    },
    [appendAudit, addToast]
  );

  const importStatementText = useCallback(
    (rawText: string, bankAccountId: string, bankName: string): number => {
      const parsed = parseStatementText(rawText, bankAccountId, bankName);
      if (parsed.items.length === 0) {
        addToast(parsed.parseErrors[0] || 'No valid statement rows detected', 'error');
        return 0;
      }
      setState((prev) => {
        const matched = matchBankStatement(parsed.items, prev.transactions, prev.parties);
        return {
          ...prev,
          bankReconItems: matched,
        };
      });
      addToast(`Imported ${parsed.items.length} ${parsed.format} entries & ran auto-match`, 'success');
      return parsed.items.length;
    },
    [addToast]
  );

  const importSampleStatement = useCallback(
    (kind: 'hdfc_csv' | 'sbi_ofx', bankAccountId: string): number => {
      const bank = state.bankAccounts.find((b) => b.id === bankAccountId);
      const raw = kind === 'hdfc_csv' ? getSampleHdfcStatementCsv() : getSampleSbiStatementOfx();
      return importStatementText(raw, bankAccountId, bank?.bankName || 'HDFC / SBI');
    },
    [state.bankAccounts, importStatementText]
  );

  const reconcileBankFeed = useCallback(() => {
    setState((prev) => ({
      ...prev,
      bankReconItems: matchBankStatement(prev.bankReconItems, prev.transactions, prev.parties),
    }));
    addToast('Re-matched statement items against ledger', 'info');
  }, [addToast]);

  const confirmMatch = useCallback((reconId: string) => {
    setState((prev) => ({
      ...prev,
      bankReconItems: prev.bankReconItems.map((it) =>
        it.id === reconId
          ? {
              ...it,
              isReconciled: true,
              isUserConfirmed: true,
              reconciledAt: new Date().toISOString(),
              discrepancyNote: '',
            }
          : it
      ),
    }));
  }, []);

  const confirmAllMatches = useCallback(() => {
    setState((prev) => ({
      ...prev,
      bankReconItems: prev.bankReconItems.map((it) =>
        it.matchedTxnId
          ? {
              ...it,
              isReconciled: true,
              isUserConfirmed: true,
              reconciledAt: new Date().toISOString(),
              discrepancyNote: '',
            }
          : it
      ),
    }));
    addToast('All auto-matched bank entries confirmed', 'success');
  }, [addToast]);

  const manualMatch = useCallback((reconId: string, txnId: string) => {
    setState((prev) => ({
      ...prev,
      bankReconItems: prev.bankReconItems.map((it) =>
        it.id === reconId
          ? {
              ...it,
              matchedTxnId: txnId,
              matchConfidence: 100,
              matchReason: 'Manually linked by user',
              isReconciled: true,
              isUserConfirmed: true,
              reconciledAt: new Date().toISOString(),
              discrepancyNote: '',
            }
          : it
      ),
    }));
  }, []);

  const unlinkMatch = useCallback((reconId: string) => {
    setState((prev) => ({
      ...prev,
      bankReconItems: prev.bankReconItems.map((it) =>
        it.id === reconId
          ? {
              ...it,
              matchedTxnId: null,
              matchConfidence: 0,
              matchReason: '',
              isReconciled: false,
              isUserConfirmed: false,
              reconciledAt: null,
            }
          : it
      ),
    }));
  }, []);

  const resolveDiscrepancy = useCallback(
    (
      reconId: string,
      resolutionType: DiscrepancyResolutionType,
      partyId: string | null,
      remarks: string
    ) => {
      setState((prev) => {
        const stmt = prev.bankReconItems.find((s) => s.id === reconId);
        if (!stmt) return prev;
        const balancingTxn = createBalancingTransaction(
          stmt,
          resolutionType,
          prev.activeBranchId,
          partyId,
          remarks
        );
        const nextItems = prev.bankReconItems.map((s) =>
          s.id === reconId
            ? {
                ...s,
                matchedTxnId: balancingTxn.id,
                matchConfidence: 100,
                matchReason: `Auto-created voucher (${resolutionType})`,
                isReconciled: true,
                isUserConfirmed: true,
                reconciledAt: new Date().toISOString(),
                discrepancyNote: '',
              }
            : s
        );
        return {
          ...prev,
          transactions: [balancingTxn, ...prev.transactions],
          bankReconItems: nextItems,
          auditLogs: appendAudit(
            prev.auditLogs,
            'BankRecon',
            balancingTxn.id,
            'RESOLVE_DISCREPANCY',
            `Created ${balancingTxn.type} ₹${(balancingTxn.amountPaise / 100).toFixed(2)} for ${stmt.description}`,
            remarks || resolutionType,
            prev.currentUserName
          ),
        };
      });
      addToast('Discrepancy posted to books & reconciled', 'success');
    },
    [appendAudit, addToast]
  );

  const clearStatementItems = useCallback(() => {
    setState((prev) => ({ ...prev, bankReconItems: [] }));
  }, []);

  const addQrLog = (
    prevLogs: QrActivityLog[],
    action: string,
    details: string,
    role: string
  ): QrActivityLog[] => {
    const prevHash = prevLogs.length > 0 ? prevLogs[0].hash : '0'.repeat(64);
    const rec = createAuditRecord({
      prevHash,
      entityType: 'UpiQr',
      entityId: action,
      action,
      details,
      reason: 'QR management event',
      actor: role,
    });
    return [
      {
        id: rec.id,
        action,
        details,
        timestamp: rec.timestamp,
        role,
        hash: rec.recordHash,
        previousHash: prevHash,
      },
      ...prevLogs,
    ];
  };

  const addQrCode = useCallback(
    (qr: Omit<UpiQrCode, 'id'>) => {
      const created: UpiQrCode = { ...qr, id: `QR-${Date.now()}` };
      setState((prev) => {
        const list = created.isDefault
          ? prev.qrCodes.map((q) => ({ ...q, isDefault: false }))
          : [...prev.qrCodes];
        return {
          ...prev,
          qrCodes: [...list, created],
          qrActivityLogs: addQrLog(
            prev.qrActivityLogs,
            'ADD_QR',
            `${created.label} (${created.upiId})`,
            prev.currentUserRole
          ),
        };
      });
      addToast(`UPI QR "${created.label}" added`, 'success');
    },
    [addToast]
  );

  const updateQrCode = useCallback((qr: UpiQrCode) => {
    setState((prev) => ({
      ...prev,
      qrCodes: prev.qrCodes.map((q) =>
        q.id === qr.id ? qr : qr.isDefault ? { ...q, isDefault: false } : q
      ),
      qrActivityLogs: addQrLog(
        prev.qrActivityLogs,
        'UPDATE_QR',
        `${qr.label} (${qr.upiId})`,
        prev.currentUserRole
      ),
    }));
  }, []);

  const deleteQrCode = useCallback((qrId: string) => {
    setState((prev) => ({
      ...prev,
      qrCodes: prev.qrCodes.filter((q) => q.id !== qrId),
      qrActivityLogs: addQrLog(prev.qrActivityLogs, 'DELETE_QR', qrId, prev.currentUserRole),
    }));
  }, []);

  const setDefaultQr = useCallback((qrId: string) => {
    setState((prev) => ({
      ...prev,
      qrCodes: prev.qrCodes.map((q) => ({ ...q, isDefault: q.id === qrId })),
      qrActivityLogs: addQrLog(prev.qrActivityLogs, 'SET_DEFAULT_QR', qrId, prev.currentUserRole),
    }));
  }, []);

  const toggleQrEnabled = useCallback((qrId: string) => {
    setState((prev) => ({
      ...prev,
      qrCodes: prev.qrCodes.map((q) => (q.id === qrId ? { ...q, isEnabled: !q.isEnabled } : q)),
    }));
  }, []);

  const toggleQrShowOnInvoices = useCallback((qrId: string) => {
    setState((prev) => ({
      ...prev,
      qrCodes: prev.qrCodes.map((q) =>
        q.id === qrId ? { ...q, showOnInvoices: !q.showOnInvoices } : q
      ),
    }));
  }, []);

  const setSecurityPin = useCallback(
    (pin: string) => {
      const hashed = hashPinSync(pin.trim());
      setState((prev) => ({
        ...prev,
        hasPinSet: true,
        pinHash: hashed,
        recoveryWords:
          prev.recoveryWords.length === 12 ? prev.recoveryWords : generateRecoveryPhrase(),
        auditLogs: appendAudit(
          prev.auditLogs,
          'Security',
          'APP_PIN',
          'SET_PIN',
          'App lock PIN configured/updated',
          'Owner security configuration',
          prev.currentUserName
        ),
      }));
      addToast('App Lock PIN saved', 'success');
    },
    [appendAudit, addToast]
  );

  const verifySecurityPin = useCallback(
    (pin: string): boolean => {
      const now = Date.now();
      if (state.pinLockoutUntilMs > now) {
        addToast('Too many failed PIN attempts. Wait before retrying.', 'error');
        return false;
      }
      if (hashPinSync(pin.trim()) === state.pinHash) {
        setState((prev) => ({
          ...prev,
          isAppLocked: false,
          pinFailedAttempts: 0,
          pinLockoutUntilMs: 0,
        }));
        return true;
      }
      setState((prev) => {
        const nextFails = prev.pinFailedAttempts + 1;
        const lockout = nextFails >= 5 ? Date.now() + 30_000 : 0;
        return {
          ...prev,
          pinFailedAttempts: nextFails,
          pinLockoutUntilMs: lockout,
        };
      });
      addToast('Incorrect App Lock PIN', 'error');
      return false;
    },
    [state.pinHash, state.pinLockoutUntilMs, addToast]
  );

  const lockApp = useCallback(() => {
    setState((prev) => ({ ...prev, isAppLocked: true }));
  }, []);

  const unlockWithRecoveryWords = useCallback(
    (wordsInput: string): boolean => {
      const entered = wordsInput
        .trim()
        .toLowerCase()
        .split(/\s+/)
        .filter(Boolean);
      const expected = state.recoveryWords.map((w) => w.toLowerCase());
      if (entered.length === 12 && entered.every((w, idx) => w === expected[idx])) {
        setState((prev) => ({
          ...prev,
          isAppLocked: false,
          pinFailedAttempts: 0,
          pinLockoutUntilMs: 0,
        }));
        addToast('App unlocked using 12-word recovery phrase', 'success');
        return true;
      }
      addToast('Recovery words do not match', 'error');
      return false;
    },
    [state.recoveryWords, addToast]
  );

  const setManagerPin = useCallback(
    (pin: string) => {
      const hashed = hashPinSync(pin.trim());
      setState((prev) => ({
        ...prev,
        hasManagerPinSet: true,
        managerPinHash: hashed,
        isManagerUnlocked: true,
        managerUnlockExpiresAtMs: Date.now() + 15 * 60_000,
      }));
      addToast('Manager Banking PIN updated', 'success');
    },
    [addToast]
  );

  const verifyManagerPin = useCallback(
    (pin: string): boolean => {
      if (state.managerLockoutUntilMs > Date.now()) {
        addToast('Manager PIN locked temporarily due to failed attempts', 'error');
        return false;
      }
      if (hashPinSync(pin.trim()) === state.managerPinHash) {
        setState((prev) => ({
          ...prev,
          isManagerUnlocked: true,
          managerUnlockExpiresAtMs: Date.now() + 15 * 60_000,
          managerFailedAttempts: 0,
          managerLockoutUntilMs: 0,
        }));
        return true;
      }
      setState((prev) => {
        const nextFails = prev.managerFailedAttempts + 1;
        return {
          ...prev,
          managerFailedAttempts: nextFails,
          managerLockoutUntilMs: nextFails >= 5 ? Date.now() + 30_000 : 0,
        };
      });
      addToast('Incorrect Manager Banking PIN', 'error');
      return false;
    },
    [state.managerPinHash, state.managerLockoutUntilMs, addToast]
  );

  const lockManager = useCallback(() => {
    setState((prev) => ({ ...prev, isManagerUnlocked: false, managerUnlockExpiresAtMs: 0 }));
  }, []);

  const addManagedUser = useCallback(
    (user: { name: string; role: 'owner' | 'staff'; pin: string; allocatedScreens: string[] }) => {
      const created: ManagedUser = {
        id: `USR-${Date.now()}`,
        name: user.name.trim(),
        role: user.role,
        pinHash: hashPinSync(user.pin || '1111'),
        isActive: true,
        allocatedScreens: user.allocatedScreens,
      };
      setState((prev) => ({
        ...prev,
        managedUsers: [...prev.managedUsers, created],
      }));
      addToast(`User "${created.name}" added`, 'success');
    },
    [addToast]
  );

  const updateManagedUser = useCallback((user: ManagedUser, newPin?: string) => {
    setState((prev) => ({
      ...prev,
      managedUsers: prev.managedUsers.map((u) =>
        u.id === user.id
          ? { ...user, pinHash: newPin?.trim() ? hashPinSync(newPin.trim()) : user.pinHash }
          : u
      ),
    }));
  }, []);

  const deleteManagedUser = useCallback((userId: string) => {
    setState((prev) => ({
      ...prev,
      managedUsers: prev.managedUsers.filter((u) => u.id !== userId),
    }));
  }, []);

  const switchCurrentUser = useCallback(
    (userId: string) => {
      const target = state.managedUsers.find((u) => u.id === userId);
      if (!target) return;
      setState((prev) => ({
        ...prev,
        currentUserId: target.id,
        currentUserName: target.name,
        currentUserRole: target.role,
      }));
      addToast(`Switched active counter user to ${target.name} (${target.role})`, 'info');
    },
    [state.managedUsers, addToast]
  );

  const verifyAuditChainNow = useCallback((): boolean => {
    const res = verifyAuditChain(state.auditLogs);
    setState((prev) => ({
      ...prev,
      isChainValid: res.valid,
      chainBrokenAtLogId: res.corruptedLogId,
    }));
    if (res.valid) {
      addToast(`Audit chain verified (${res.totalCount} SHA-256 blocks intact)`, 'success');
    } else {
      addToast(res.errorMessage || 'Audit chain verification failed', 'error');
    }
    return res.valid;
  }, [state.auditLogs, addToast]);

  const unlockBankReconDemo = useCallback(() => {
    setState((prev) => ({ ...prev, isBankReconProUnlocked: true }));
    addToast('Bank Statement Reconciliation Pro unlocked', 'success');
  }, [addToast]);

  const unlockGstAssistantDemo = useCallback(() => {
    setState((prev) => ({ ...prev, isGstAssistantUnlocked: true }));
    addToast('GST Returns & Rule 88A Assistant unlocked', 'success');
  }, [addToast]);

  const exportBackupJson = useCallback(
    async (passphrase?: string): Promise<string> => {
      const payload = JSON.stringify(
        {
          schema: 'pabaru-billing-backup-v2',
          exportedAt: new Date().toISOString(),
          data: state,
        },
        null,
        2
      );
      if (passphrase?.trim()) {
        return encryptAesGcm(payload, passphrase.trim());
      }
      return payload;
    },
    [state]
  );

  const importBackupJson = useCallback(
    async (
      jsonText: string,
      passphrase?: string
    ): Promise<{ ok: boolean; needsPassphrase?: boolean; message: string }> => {
      try {
        const trimmed = jsonText.trim();
        let rawObj = JSON.parse(trimmed);
        if (rawObj.format === 'pabaru-encrypted-backup-v1') {
          if (!passphrase?.trim()) {
            return {
              ok: false,
              needsPassphrase: true,
              message: 'This backup is AES-256-GCM encrypted. Enter your passphrase to restore.',
            };
          }
          const decrypted = await decryptAesGcm(trimmed, passphrase.trim());
          rawObj = JSON.parse(decrypted);
        }
        const restoredState: PabaruUiState | undefined = rawObj.data || rawObj;
        if (!restoredState || !restoredState.profile || !Array.isArray(restoredState.transactions)) {
          return { ok: false, message: 'Invalid backup schema: missing profile or transactions.' };
        }
        const auditRes = verifyAuditChain(restoredState.auditLogs || []);
        setState({
          ...createEmptyState(),
          ...restoredState,
          isChainValid: auditRes.valid,
          chainBrokenAtLogId: auditRes.corruptedLogId,
        });
        addToast(
          `Restored ${restoredState.transactions.length} transactions · Chain ${auditRes.valid ? 'verified' : 'warning'}`,
          auditRes.valid ? 'success' : 'warning'
        );
        return {
          ok: true,
          message: `Restored ${restoredState.transactions.length} transactions (${auditRes.totalCount} audit blocks ${auditRes.valid ? 'verified' : 'mismatch'}).`,
        };
      } catch (err) {
        return {
          ok: false,
          message: err instanceof Error ? err.message : 'Failed to parse or decrypt backup JSON',
        };
      }
    },
    [addToast]
  );

  return (
    <PabaruContext.Provider
      value={{
        state,
        tokens,
        activeScreen,
        setActiveScreen,
        selectedPartyId,
        setSelectedPartyId,
        toasts,
        addToast,
        dismissToast,
        printPreview,
        openPrintPreview: setPrintPreview,
        closePrintPreview: () => setPrintPreview(null),
        whatsappShare,
        openWhatsAppShare: setWhatsappShare,
        closeWhatsAppShare: () => setWhatsappShare(null),
        getPartyBalancePaise,
        getLiveCashBalancePaise,
        getBankAccountBalancePaise,
        getTotalBankBalancePaise,
        signIn,
        signInDemo,
        registerBusiness,
        signOut,
        signInSuperAdmin,
        signOutSuperAdmin,
        toggleTenantStatus,
        impersonateTenant,
        setThemeId,
        setThemeMode,
        setSelectedFy,
        setActiveBranch,
        addBranch,
        updateLowStockThreshold,
        updateProfile,
        recordItemizedInvoice,
        recordPayment,
        recordQuickTransaction,
        voidTransaction,
        addParty,
        updateParty,
        addProduct,
        updateProduct,
        toggleArchiveProduct,
        recordPurchaseInward,
        recordStockAdjustmentMilli,
        transferStockBetweenLocations,
        addInventoryLocation,
        addBankAccount,
        adjustCashPaise,
        transferBetweenAccountsPaise,
        importStatementText,
        importSampleStatement,
        reconcileBankFeed,
        confirmMatch,
        confirmAllMatches,
        manualMatch,
        unlinkMatch,
        resolveDiscrepancy,
        clearStatementItems,
        addQrCode,
        updateQrCode,
        deleteQrCode,
        setDefaultQr,
        toggleQrEnabled,
        toggleQrShowOnInvoices,
        setSecurityPin,
        verifySecurityPin,
        lockApp,
        unlockWithRecoveryWords,
        setManagerPin,
        verifyManagerPin,
        lockManager,
        addManagedUser,
        updateManagedUser,
        deleteManagedUser,
        switchCurrentUser,
        verifyAuditChainNow,
        unlockBankReconDemo,
        unlockGstAssistantDemo,
        exportBackupJson,
        importBackupJson,
      }}
    >
      {children}
    </PabaruContext.Provider>
  );
};

export function usePabaru(): PabaruContextValue {
  const ctx = useContext(PabaruContext);
  if (!ctx) throw new Error('usePabaru must be used inside PabaruProvider');
  return ctx;
}
