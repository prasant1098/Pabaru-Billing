import React, { useMemo, useState } from 'react';
import {
  AlertTriangle,
  Archive,
  ArrowRightLeft,
  CalendarClock,
  Layers,
  PackagePlus,
  Plus,
  Search,
  Sliders,
  Warehouse,
  X,
} from 'lucide-react';
import {
  formatPaise,
  formatQuantityMilli,
  parsePaiseFromRupees,
} from '../engines/financialEngine';
import { usePabaru } from '../state/PabaruContext';
import {
  GstSlabs,
  ProductItem,
  STOCK_ADJUSTMENT_REASONS,
  StockAdjustmentReason,
  isBatchExpired,
  isBatchExpiringSoon,
} from '../types/models';

export const InventoryScreen: React.FC = () => {
  const {
    state,
    addProduct,
    toggleArchiveProduct,
    recordPurchaseInward,
    recordStockAdjustmentMilli,
    transferStockBetweenLocations,
    addInventoryLocation,
  } = usePabaru();

  const [tab, setTab] = useState<'catalog' | 'batches' | 'movements' | 'warehouses'>('catalog');
  const [search, setSearch] = useState('');
  const [onlyLowStock, setOnlyLowStock] = useState(false);
  const [valuationOpen, setValuationOpen] = useState(false);

  // Add Product modal
  const [addOpen, setAddOpen] = useState(false);
  const [pName, setPName] = useState('');
  const [pSku, setPSku] = useState('');
  const [pHsn, setPHsn] = useState('');
  const [pUnit, setPUnit] = useState('PCS');
  const [pRateBps, setPRateBps] = useState(1800);
  const [pBuyPrice, setPBuyPrice] = useState('');
  const [pSellPrice, setPSellPrice] = useState('');
  const [pStock, setPStock] = useState('0');
  const [pCat, setPCat] = useState('General');
  const [pBatchTrack, setPBatchTrack] = useState(false);
  const [pBatchNo, setPBatchNo] = useState('');
  const [pExpiry, setPExpiry] = useState('');

  // Inward Restock modal
  const [inwardTarget, setInwardTarget] = useState<ProductItem | null>(null);
  const [inQty, setInQty] = useState('10');
  const [inPrice, setInPrice] = useState('');
  const [inBatch, setInBatch] = useState('');
  const [inExpiry, setInExpiry] = useState('');
  const [inLoc, setInLoc] = useState(state.inventoryLocations[0]?.id || 'LOC-MAIN');

  // Stock Adjustment modal
  const [adjTarget, setAdjTarget] = useState<ProductItem | null>(null);
  const [adjDirection, setAdjDirection] = useState<'add' | 'reduce'>('reduce');
  const [adjQty, setAdjQty] = useState('1');
  const [adjReason, setAdjReason] = useState<StockAdjustmentReason>('DAMAGE');
  const [adjRemarks, setAdjRemarks] = useState('');

  // Warehouse Transfer modal
  const [trfTarget, setTrfTarget] = useState<ProductItem | null>(null);
  const [trfFrom, setTrfFrom] = useState(state.inventoryLocations[0]?.id || 'LOC-MAIN');
  const [trfTo, setTrfTo] = useState(
    state.inventoryLocations[1]?.id || state.inventoryLocations[0]?.id || 'LOC-MAIN'
  );
  const [trfQty, setTrfQty] = useState('5');
  const [trfRemarks, setTrfRemarks] = useState('');

  // Add Warehouse state
  const [locName, setLocName] = useState('');
  const [locCode, setLocCode] = useState('');
  const [locAddr, setLocAddr] = useState('');

  const activeProducts = useMemo(
    () => state.products.filter((p) => !p.isArchived),
    [state.products]
  );

  const totalValuation = useMemo(
    () =>
      activeProducts.reduce(
        (s, p) => s + Math.floor((p.currentStockMilli * p.purchasePricePaise + 500) / 1000),
        0
      ),
    [activeProducts]
  );

  const filteredProducts = useMemo(() => {
    const q = search.trim().toLowerCase();
    return state.products.filter((p) => {
      if (onlyLowStock && (p.isArchived || p.currentStockMilli > state.lowStockThreshold * 1000)) {
        return false;
      }
      if (!q) return true;
      return (
        p.name.toLowerCase().includes(q) ||
        p.sku.toLowerCase().includes(q) ||
        p.hsnCode.toLowerCase().includes(q) ||
        p.category.toLowerCase().includes(q)
      );
    });
  }, [state.products, search, onlyLowStock, state.lowStockThreshold]);

  const submitAddProduct = (e: React.FormEvent) => {
    e.preventDefault();
    if (!pName.trim()) return;
    const qtyMilli = Math.max(0, Math.round((Number.parseFloat(pStock) || 0) * 1000));
    addProduct(
      {
        name: pName.trim(),
        barcode: '',
        sku: pSku.trim() || `SKU-${Date.now().toString().slice(-4)}`,
        hsnCode: pHsn.trim() || '9999',
        gstRateBps: pRateBps,
        unit: pUnit.trim() || 'PCS',
        purchasePricePaise: parsePaiseFromRupees(pBuyPrice),
        salePricePaise: parsePaiseFromRupees(pSellPrice),
        currentStockMilli: qtyMilli,
        reorderLevelMilli: state.lowStockThreshold * 1000,
        category: pCat.trim() || 'General',
        openingStockMilli: qtyMilli,
        hasBatchTracking: pBatchTrack,
        hasVariants: false,
        isArchived: false,
      },
      pBatchNo,
      pExpiry
    );
    setPName('');
    setPSku('');
    setPHsn('');
    setPBuyPrice('');
    setPSellPrice('');
    setPStock('0');
    setPBatchNo('');
    setAddOpen(false);
  };

  const submitInward = (e: React.FormEvent) => {
    e.preventDefault();
    if (!inwardTarget) return;
    const qtyMilli = Math.max(0, Math.round((Number.parseFloat(inQty) || 0) * 1000));
    if (qtyMilli <= 0) return;
    recordPurchaseInward({
      productId: inwardTarget.id,
      quantityMilli: qtyMilli,
      purchasePricePaise: parsePaiseFromRupees(inPrice) || inwardTarget.purchasePricePaise,
      batchNumber: inBatch,
      expiryDate: inExpiry,
      locationId: inLoc,
      remarks: 'Purchase inward restock',
    });
    setInwardTarget(null);
  };

  const submitAdj = (e: React.FormEvent) => {
    e.preventDefault();
    if (!adjTarget) return;
    const mag = Math.max(0, Math.round((Number.parseFloat(adjQty) || 0) * 1000));
    if (mag <= 0) return;
    recordStockAdjustmentMilli({
      productId: adjTarget.id,
      quantityDeltaMilli: adjDirection === 'add' ? mag : -mag,
      reason: adjReason,
      locationId: state.inventoryLocations[0]?.id || 'LOC-MAIN',
      remarks: adjRemarks,
    });
    setAdjTarget(null);
    setAdjRemarks('');
  };

  const submitTransfer = (e: React.FormEvent) => {
    e.preventDefault();
    if (!trfTarget || trfFrom === trfTo) return;
    const qtyMilli = Math.max(0, Math.round((Number.parseFloat(trfQty) || 0) * 1000));
    if (qtyMilli <= 0) return;
    transferStockBetweenLocations({
      productId: trfTarget.id,
      fromLocationId: trfFrom,
      toLocationId: trfTo,
      quantityMilli: qtyMilli,
      remarks: trfRemarks,
    });
    setTrfTarget(null);
    setTrfRemarks('');
  };

  return (
    <div className="space-y-5">
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
        <div>
          <h2 className="text-lg md:text-xl font-bold">Stock &amp; Inventory Ledger</h2>
          <p className="text-xs text-[var(--p-text-muted)]">
            {activeProducts.length} active SKUs · Total Cost Valuation{' '}
            <button
              type="button"
              onClick={() => setValuationOpen(true)}
              className="font-mono-num font-bold text-[var(--p-link)] hover:underline"
            >
              {formatPaise(totalValuation)}
            </button>
          </p>
        </div>

        <button
          type="button"
          onClick={() => setAddOpen(true)}
          className="inline-flex items-center gap-1.5 whitespace-nowrap shrink-0 rounded-xl bg-[var(--p-primary)] px-4 py-2 text-xs font-semibold text-[var(--p-primary-text)] hover:opacity-90"
        >
          <Plus className="w-4 h-4" />
          Add New SKU
        </button>
      </div>

      {/* Tabs */}
      <div className="flex flex-wrap gap-1.5">
        {[
          { id: 'catalog' as const, label: 'SKU Catalog', icon: <Layers className="w-3.5 h-3.5" /> },
          {
            id: 'batches' as const,
            label: `Batches & Expiry (${state.stockBatches.length})`,
            icon: <CalendarClock className="w-3.5 h-3.5" />,
          },
          {
            id: 'movements' as const,
            label: `Stock Audit Trail (${state.stockMovements.length})`,
            icon: <ArrowRightLeft className="w-3.5 h-3.5" />,
          },
          {
            id: 'warehouses' as const,
            label: `Godowns (${state.inventoryLocations.length})`,
            icon: <Warehouse className="w-3.5 h-3.5" />,
          },
        ].map((t) => (
          <button
            key={t.id}
            type="button"
            onClick={() => setTab(t.id)}
            className={`inline-flex items-center gap-1.5 whitespace-nowrap rounded-xl border px-3 py-2 text-xs font-semibold transition ${
              tab === t.id
                ? 'border-[var(--p-primary)] bg-[var(--p-primary)] text-[var(--p-primary-text)]'
                : 'border-[var(--p-card-border)] bg-[var(--p-card)] text-[var(--p-text)] hover:bg-[var(--p-secondary-bg)]'
            }`}
          >
            {t.icon}
            <span>{t.label}</span>
          </button>
        ))}
      </div>

      {tab === 'catalog' && (
        <div className="space-y-4">
          <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
            <div className="relative flex-1 max-w-md">
              <Search className="w-4 h-4 text-[var(--p-text-muted)] absolute left-3 top-2.5" />
              <input
                type="text"
                value={search}
                onChange={(e) => setSearch(e.target.value)}
                placeholder="Search item name, SKU, HSN, category..."
                className="w-full rounded-xl border border-[var(--p-card-border)] bg-[var(--p-card)] pl-9 pr-3 py-2 text-xs"
              />
            </div>

            <button
              type="button"
              onClick={() => setOnlyLowStock((v) => !v)}
              className={`inline-flex items-center gap-1.5 whitespace-nowrap rounded-xl border px-3 py-2 text-xs font-medium ${
                onlyLowStock
                  ? 'border-[var(--p-warning)] bg-[var(--p-warning-container)] text-[var(--p-on-warning-container)]'
                  : 'border-[var(--p-card-border)] bg-[var(--p-card)] text-[var(--p-text)]'
              }`}
            >
              <AlertTriangle className="w-3.5 h-3.5" />
              Low Stock Only (&le; {state.lowStockThreshold})
            </button>
          </div>

          <div className="rounded-2xl border border-[var(--p-card-border)] bg-[var(--p-card)] overflow-hidden">
            <div className="overflow-x-auto">
              <table className="w-full text-left text-xs">
                <thead className="bg-[var(--p-secondary-bg)] text-[var(--p-text-muted)] uppercase text-[10px]">
                  <tr>
                    <th className="px-4 py-2.5">Item &amp; HSN</th>
                    <th className="px-4 py-2.5">GST Slab</th>
                    <th className="px-4 py-2.5 text-right">Purchase / Sale</th>
                    <th className="px-4 py-2.5 text-right">On-Hand Stock</th>
                    <th className="px-4 py-2.5 text-right">Actions</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-[var(--p-card-border)]">
                  {filteredProducts.map((p) => {
                    const isLow = p.currentStockMilli <= state.lowStockThreshold * 1000;
                    return (
                      <tr key={p.id} className={p.isArchived ? 'opacity-50' : 'hover:bg-[var(--p-bg)]'}>
                        <td className="px-4 py-3">
                          <p className="font-semibold text-[var(--p-text)]">{p.name}</p>
                          <p className="text-[11px] text-[var(--p-text-muted)]">
                            {p.sku} · HSN {p.hsnCode} · {p.category}
                          </p>
                        </td>
                        <td className="px-4 py-3 font-mono-num">{GstSlabs.label(p.gstRateBps)}</td>
                        <td className="px-4 py-3 text-right font-mono-num whitespace-nowrap">
                          <div>{formatPaise(p.salePricePaise)}</div>
                          <div className="text-[11px] text-[var(--p-text-muted)]">
                            Cost {formatPaise(p.purchasePricePaise)}
                          </div>
                        </td>
                        <td className="px-4 py-3 text-right whitespace-nowrap">
                          <span
                            className={`inline-block rounded-md px-2 py-0.5 font-mono-num font-bold ${
                              isLow
                                ? 'bg-[var(--p-warning-container)] text-[var(--p-on-warning-container)]'
                                : 'bg-[var(--p-success-container)] text-[var(--p-on-success-container)]'
                            }`}
                          >
                            {formatQuantityMilli(p.currentStockMilli)} {p.unit}
                          </span>
                        </td>
                        <td className="px-4 py-3 text-right whitespace-nowrap">
                          <div className="inline-flex items-center gap-1.5">
                            <button
                              type="button"
                              onClick={() => {
                                setInwardTarget(p);
                                setInPrice((p.purchasePricePaise / 100).toFixed(2));
                                setInBatch('');
                              }}
                              className="inline-flex items-center gap-1 rounded-lg bg-[var(--p-primary)] px-2.5 py-1 text-[11px] font-semibold text-[var(--p-primary-text)]"
                              title="Purchase Inward Restock"
                            >
                              <PackagePlus className="w-3 h-3" />
                              Inward
                            </button>
                            <button
                              type="button"
                              onClick={() => setAdjTarget(p)}
                              className="inline-flex items-center gap-1 rounded-lg border border-[var(--p-card-border)] px-2 py-1 text-[11px]"
                              title="Adjust Stock"
                            >
                              <Sliders className="w-3 h-3" />
                              Adjust
                            </button>
                            {state.inventoryLocations.length > 1 && (
                              <button
                                type="button"
                                onClick={() => setTrfTarget(p)}
                                className="inline-flex items-center gap-1 rounded-lg border border-[var(--p-card-border)] px-2 py-1 text-[11px]"
                                title="Transfer Between Warehouses"
                              >
                                <ArrowRightLeft className="w-3 h-3" />
                              </button>
                            )}
                            <button
                              type="button"
                              onClick={() => toggleArchiveProduct(p.id)}
                              className="p-1 rounded-lg border border-[var(--p-card-border)] text-[var(--p-text-muted)]"
                              title={p.isArchived ? 'Unarchive' : 'Archive'}
                            >
                              <Archive className="w-3.5 h-3.5" />
                            </button>
                          </div>
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          </div>
        </div>
      )}

      {tab === 'batches' && (
        <div className="rounded-2xl border border-[var(--p-card-border)] bg-[var(--p-card)] overflow-hidden">
          <div className="overflow-x-auto">
            <table className="w-full text-left text-xs">
              <thead className="bg-[var(--p-secondary-bg)] text-[var(--p-text-muted)] uppercase text-[10px]">
                <tr>
                  <th className="px-4 py-2.5">Batch #</th>
                  <th className="px-4 py-2.5">Product</th>
                  <th className="px-4 py-2.5">Expiry Date</th>
                  <th className="px-4 py-2.5 text-right">Available Qty</th>
                  <th className="px-4 py-2.5">Status</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-[var(--p-card-border)]">
                {state.stockBatches.map((b) => {
                  const prod = state.products.find((p) => p.id === b.productId);
                  const expired = isBatchExpired(b.expiryDate);
                  const soon = isBatchExpiringSoon(b.expiryDate);
                  return (
                    <tr key={b.id}>
                      <td className="px-4 py-3 font-mono-num font-bold">{b.batchNumber}</td>
                      <td className="px-4 py-3 font-semibold">{prod?.name || b.productId}</td>
                      <td className="px-4 py-3 font-mono-num">{b.expiryDate}</td>
                      <td className="px-4 py-3 text-right font-mono-num">
                        {formatQuantityMilli(b.availableQuantityMilli)} {prod?.unit || ''}
                      </td>
                      <td className="px-4 py-3">
                        <span
                          className={`rounded-md px-2 py-0.5 text-[11px] font-semibold ${
                            expired
                              ? 'bg-[var(--p-danger-container)] text-[var(--p-on-danger-container)]'
                              : soon
                                ? 'bg-[var(--p-warning-container)] text-[var(--p-on-warning-container)]'
                                : 'bg-[var(--p-success-container)] text-[var(--p-on-success-container)]'
                          }`}
                        >
                          {expired ? 'Expired' : soon ? 'Expiring <=30d' : 'FIFO Active'}
                        </span>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {tab === 'movements' && (
        <div className="rounded-2xl border border-[var(--p-card-border)] bg-[var(--p-card)] overflow-hidden">
          <div className="overflow-x-auto">
            <table className="w-full text-left text-xs">
              <thead className="bg-[var(--p-secondary-bg)] text-[var(--p-text-muted)] uppercase text-[10px]">
                <tr>
                  <th className="px-4 py-2.5">Timestamp</th>
                  <th className="px-4 py-2.5">Item</th>
                  <th className="px-4 py-2.5">Type</th>
                  <th className="px-4 py-2.5 text-right">Delta</th>
                  <th className="px-4 py-2.5 text-right">Balance</th>
                  <th className="px-4 py-2.5">Remarks</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-[var(--p-card-border)]">
                {state.stockMovements.map((m) => (
                  <tr key={m.id}>
                    <td className="px-4 py-2.5 font-mono-num text-[11px] text-[var(--p-text-muted)]">
                      {m.timestamp.slice(0, 16).replace('T', ' ')}
                    </td>
                    <td className="px-4 py-2.5 font-semibold">{m.productName}</td>
                    <td className="px-4 py-2.5">{m.movementType}</td>
                    <td
                      className={`px-4 py-2.5 text-right font-mono-num font-bold ${
                        m.quantityDeltaMilli >= 0 ? 'text-[var(--p-success)]' : 'text-[var(--p-danger)]'
                      }`}
                    >
                      {m.quantityDeltaMilli >= 0 ? '+' : ''}
                      {formatQuantityMilli(m.quantityDeltaMilli)}
                    </td>
                    <td className="px-4 py-2.5 text-right font-mono-num">
                      {formatQuantityMilli(m.balanceAfterMilli)}
                    </td>
                    <td className="px-4 py-2.5 text-[var(--p-text-muted)]">{m.remarks}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {tab === 'warehouses' && (
        <div className="grid grid-cols-1 md:grid-cols-12 gap-4">
          <div className="md:col-span-7 space-y-3">
            {state.inventoryLocations.map((loc) => (
              <div
                key={loc.id}
                className="rounded-2xl border border-[var(--p-card-border)] bg-[var(--p-card)] p-4 flex items-center justify-between"
              >
                <div>
                  <div className="flex items-center gap-2">
                    <p className="text-sm font-bold">{loc.name}</p>
                    <span className="rounded bg-[var(--p-secondary-bg)] px-2 py-0.5 text-[10px] font-mono-num font-semibold">
                      {loc.code}
                    </span>
                  </div>
                  <p className="text-xs text-[var(--p-text-muted)] mt-1">{loc.address || 'Standard warehouse'}</p>
                </div>
                {loc.isPrimary && (
                  <span className="rounded-md bg-[var(--p-success-container)] px-2 py-0.5 text-[11px] font-semibold text-[var(--p-on-success-container)]">
                    Primary
                  </span>
                )}
              </div>
            ))}
          </div>

          <form
            onSubmit={(e) => {
              e.preventDefault();
              if (!locName.trim()) return;
              addInventoryLocation(locName, locCode || 'WH-NEW', locAddr);
              setLocName('');
              setLocCode('');
              setLocAddr('');
            }}
            className="md:col-span-5 rounded-2xl border border-[var(--p-card-border)] bg-[var(--p-card)] p-4 space-y-3"
          >
            <h3 className="text-sm font-bold">Add Warehouse / Godown</h3>
            <input
              type="text"
              required
              value={locName}
              onChange={(e) => setLocName(e.target.value)}
              placeholder="Location Name *"
              className="w-full rounded-xl border border-[var(--p-card-border)] bg-[var(--p-bg)] px-3 py-2 text-xs"
            />
            <input
              type="text"
              value={locCode}
              onChange={(e) => setLocCode(e.target.value.toUpperCase())}
              placeholder="Code (e.g. WH-03)"
              className="w-full rounded-xl border border-[var(--p-card-border)] bg-[var(--p-bg)] px-3 py-2 text-xs font-mono-num"
            />
            <input
              type="text"
              value={locAddr}
              onChange={(e) => setLocAddr(e.target.value)}
              placeholder="Address"
              className="w-full rounded-xl border border-[var(--p-card-border)] bg-[var(--p-bg)] px-3 py-2 text-xs"
            />
            <button
              type="submit"
              className="w-full rounded-xl bg-[var(--p-primary)] py-2 text-xs font-semibold text-[var(--p-primary-text)]"
            >
              Create Location
            </button>
          </form>
        </div>
      )}

      {/* Valuation Breakdown Modal */}
      {valuationOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 p-4">
          <div className="w-full max-w-lg rounded-2xl border border-[var(--p-card-border)] bg-[var(--p-card)] p-5 space-y-4">
            <div className="flex items-center justify-between">
              <h3 className="text-sm font-bold">Stock Valuation Breakdown (At Cost)</h3>
              <button type="button" onClick={() => setValuationOpen(false)}>
                <X className="w-4 h-4" />
              </button>
            </div>
            <div className="max-h-72 overflow-y-auto divide-y divide-[var(--p-card-border)] text-xs">
              {activeProducts.map((p) => {
                const val = Math.floor((p.currentStockMilli * p.purchasePricePaise + 500) / 1000);
                return (
                  <div key={p.id} className="py-2 flex justify-between">
                    <span>
                      {p.name} ({formatQuantityMilli(p.currentStockMilli)} {p.unit})
                    </span>
                    <span className="font-mono-num font-bold">{formatPaise(val)}</span>
                  </div>
                );
              })}
            </div>
            <div className="flex justify-between pt-2 border-t border-[var(--p-card-border)] text-sm font-bold">
              <span>Total Inventory Asset Value</span>
              <span className="font-mono-num text-[var(--p-primary)]">{formatPaise(totalValuation)}</span>
            </div>
          </div>
        </div>
      )}

      {/* Add SKU Modal */}
      {addOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 p-4">
          <form
            onSubmit={submitAddProduct}
            className="w-full max-w-lg rounded-2xl border border-[var(--p-card-border)] bg-[var(--p-card)] p-5 space-y-3"
          >
            <div className="flex items-center justify-between">
              <h3 className="text-sm font-bold">Add Catalog Product</h3>
              <button type="button" onClick={() => setAddOpen(false)}>
                <X className="w-4 h-4" />
              </button>
            </div>
            <input
              type="text"
              required
              value={pName}
              onChange={(e) => setPName(e.target.value)}
              placeholder="Product / Service Name *"
              className="w-full rounded-xl border border-[var(--p-card-border)] bg-[var(--p-bg)] px-3 py-2 text-xs"
            />
            <div className="grid grid-cols-3 gap-2.5">
              <input
                type="text"
                value={pSku}
                onChange={(e) => setPSku(e.target.value)}
                placeholder="SKU Code"
                className="rounded-xl border border-[var(--p-card-border)] bg-[var(--p-bg)] px-3 py-2 text-xs font-mono-num"
              />
              <input
                type="text"
                value={pHsn}
                onChange={(e) => setPHsn(e.target.value)}
                placeholder="HSN / SAC"
                className="rounded-xl border border-[var(--p-card-border)] bg-[var(--p-bg)] px-3 py-2 text-xs font-mono-num"
              />
              <input
                type="text"
                value={pUnit}
                onChange={(e) => setPUnit(e.target.value.toUpperCase())}
                placeholder="Unit (PCS/KG)"
                className="rounded-xl border border-[var(--p-card-border)] bg-[var(--p-bg)] px-3 py-2 text-xs uppercase"
              />
            </div>
            <div className="grid grid-cols-3 gap-2.5">
              <div>
                <label className="block text-[11px] text-[var(--p-text-muted)] mb-1">Purchase ₹</label>
                <input
                  type="number"
                  step="0.01"
                  required
                  value={pBuyPrice}
                  onChange={(e) => setPBuyPrice(e.target.value)}
                  className="w-full rounded-xl border border-[var(--p-card-border)] bg-[var(--p-bg)] px-3 py-2 text-xs font-mono-num"
                />
              </div>
              <div>
                <label className="block text-[11px] text-[var(--p-text-muted)] mb-1">Sale Price ₹</label>
                <input
                  type="number"
                  step="0.01"
                  required
                  value={pSellPrice}
                  onChange={(e) => setPSellPrice(e.target.value)}
                  className="w-full rounded-xl border border-[var(--p-card-border)] bg-[var(--p-bg)] px-3 py-2 text-xs font-mono-num"
                />
              </div>
              <div>
                <label className="block text-[11px] text-[var(--p-text-muted)] mb-1">GST Rate</label>
                <select
                  value={pRateBps}
                  onChange={(e) => setPRateBps(Number.parseInt(e.target.value, 10))}
                  className="w-full rounded-xl border border-[var(--p-card-border)] bg-[var(--p-bg)] px-2.5 py-2 text-xs"
                >
                  {GstSlabs.ALL_BPS.map((b) => (
                    <option key={b} value={b}>
                      {GstSlabs.label(b)}
                    </option>
                  ))}
                </select>
              </div>
            </div>
            <div className="grid grid-cols-2 gap-2.5">
              <div>
                <label className="block text-[11px] text-[var(--p-text-muted)] mb-1">Opening Stock</label>
                <input
                  type="number"
                  step="0.001"
                  value={pStock}
                  onChange={(e) => setPStock(e.target.value)}
                  className="w-full rounded-xl border border-[var(--p-card-border)] bg-[var(--p-bg)] px-3 py-2 text-xs font-mono-num"
                />
              </div>
              <div>
                <label className="block text-[11px] text-[var(--p-text-muted)] mb-1">Category</label>
                <input
                  type="text"
                  value={pCat}
                  onChange={(e) => setPCat(e.target.value)}
                  className="w-full rounded-xl border border-[var(--p-card-border)] bg-[var(--p-bg)] px-3 py-2 text-xs"
                />
              </div>
            </div>
            <label className="flex items-center gap-2 text-xs pt-1">
              <input
                type="checkbox"
                checked={pBatchTrack}
                onChange={(e) => setPBatchTrack(e.target.checked)}
              />
              Enable Batch &amp; Expiry Tracking (FIFO)
            </label>
            {pBatchTrack && (
              <div className="grid grid-cols-2 gap-2.5">
                <input
                  type="text"
                  value={pBatchNo}
                  onChange={(e) => setPBatchNo(e.target.value)}
                  placeholder="Initial Batch #"
                  className="rounded-xl border border-[var(--p-card-border)] bg-[var(--p-bg)] px-3 py-2 text-xs"
                />
                <input
                  type="date"
                  value={pExpiry}
                  onChange={(e) => setPExpiry(e.target.value)}
                  className="rounded-xl border border-[var(--p-card-border)] bg-[var(--p-bg)] px-3 py-2 text-xs"
                />
              </div>
            )}
            <div className="flex justify-end gap-2 pt-2">
              <button
                type="button"
                onClick={() => setAddOpen(false)}
                className="rounded-lg border border-[var(--p-card-border)] px-3 py-2 text-xs"
              >
                Cancel
              </button>
              <button
                type="submit"
                className="rounded-lg bg-[var(--p-primary)] px-4 py-2 text-xs font-semibold text-[var(--p-primary-text)]"
              >
                Save SKU
              </button>
            </div>
          </form>
        </div>
      )}

      {/* Inward Restock Modal */}
      {inwardTarget && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 p-4">
          <form
            onSubmit={submitInward}
            className="w-full max-w-md rounded-2xl border border-[var(--p-card-border)] bg-[var(--p-card)] p-5 space-y-3"
          >
            <div className="flex items-center justify-between">
              <h3 className="text-sm font-bold">Inward Restock · {inwardTarget.name}</h3>
              <button type="button" onClick={() => setInwardTarget(null)}>
                <X className="w-4 h-4" />
              </button>
            </div>
            <div className="grid grid-cols-2 gap-3">
              <div>
                <label className="block text-xs mb-1">Quantity ({inwardTarget.unit})</label>
                <input
                  type="number"
                  step="0.001"
                  required
                  value={inQty}
                  onChange={(e) => setInQty(e.target.value)}
                  className="w-full rounded-xl border border-[var(--p-card-border)] bg-[var(--p-bg)] px-3 py-2 text-xs font-mono-num"
                />
              </div>
              <div>
                <label className="block text-xs mb-1">Unit Cost ₹</label>
                <input
                  type="number"
                  step="0.01"
                  value={inPrice}
                  onChange={(e) => setInPrice(e.target.value)}
                  className="w-full rounded-xl border border-[var(--p-card-border)] bg-[var(--p-bg)] px-3 py-2 text-xs font-mono-num"
                />
              </div>
            </div>
            <div className="grid grid-cols-2 gap-3">
              <input
                type="text"
                value={inBatch}
                onChange={(e) => setInBatch(e.target.value)}
                placeholder="Batch # (Optional)"
                className="rounded-xl border border-[var(--p-card-border)] bg-[var(--p-bg)] px-3 py-2 text-xs"
              />
              <input
                type="date"
                value={inExpiry}
                onChange={(e) => setInExpiry(e.target.value)}
                className="rounded-xl border border-[var(--p-card-border)] bg-[var(--p-bg)] px-3 py-2 text-xs"
              />
            </div>
            <select
              value={inLoc}
              onChange={(e) => setInLoc(e.target.value)}
              className="w-full rounded-xl border border-[var(--p-card-border)] bg-[var(--p-bg)] px-3 py-2 text-xs"
            >
              {state.inventoryLocations.map((l) => (
                <option key={l.id} value={l.id}>
                  {l.name}
                </option>
              ))}
            </select>
            <div className="flex justify-end gap-2 pt-2">
              <button
                type="button"
                onClick={() => setInwardTarget(null)}
                className="rounded-lg border border-[var(--p-card-border)] px-3 py-1.5 text-xs"
              >
                Cancel
              </button>
              <button
                type="submit"
                className="rounded-lg bg-[var(--p-primary)] px-4 py-1.5 text-xs font-semibold text-[var(--p-primary-text)]"
              >
                Post Inward
              </button>
            </div>
          </form>
        </div>
      )}

      {/* Stock Adjustment Modal */}
      {adjTarget && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 p-4">
          <form
            onSubmit={submitAdj}
            className="w-full max-w-md rounded-2xl border border-[var(--p-card-border)] bg-[var(--p-card)] p-5 space-y-3"
          >
            <div className="flex items-center justify-between">
              <h3 className="text-sm font-bold">Stock Adjustment · {adjTarget.name}</h3>
              <button type="button" onClick={() => setAdjTarget(null)}>
                <X className="w-4 h-4" />
              </button>
            </div>
            <div className="grid grid-cols-2 gap-2">
              <button
                type="button"
                onClick={() => setAdjDirection('reduce')}
                className={`rounded-xl border py-2 text-xs font-semibold ${
                  adjDirection === 'reduce'
                    ? 'border-[var(--p-danger)] bg-[var(--p-danger-container)] text-[var(--p-on-danger-container)]'
                    : 'border-[var(--p-card-border)]'
                }`}
              >
                Reduce Stock (-)
              </button>
              <button
                type="button"
                onClick={() => setAdjDirection('add')}
                className={`rounded-xl border py-2 text-xs font-semibold ${
                  adjDirection === 'add'
                    ? 'border-[var(--p-success)] bg-[var(--p-success-container)] text-[var(--p-on-success-container)]'
                    : 'border-[var(--p-card-border)]'
                }`}
              >
                Add Stock (+)
              </button>
            </div>
            <div className="grid grid-cols-2 gap-3">
              <input
                type="number"
                step="0.001"
                required
                value={adjQty}
                onChange={(e) => setAdjQty(e.target.value)}
                placeholder="Qty"
                className="rounded-xl border border-[var(--p-card-border)] bg-[var(--p-bg)] px-3 py-2 text-xs font-mono-num"
              />
              <select
                value={adjReason}
                onChange={(e) => setAdjReason(e.target.value as StockAdjustmentReason)}
                className="rounded-xl border border-[var(--p-card-border)] bg-[var(--p-bg)] px-2.5 py-2 text-xs"
              >
                {Object.entries(STOCK_ADJUSTMENT_REASONS).map(([k, label]) => (
                  <option key={k} value={k}>
                    {label}
                  </option>
                ))}
              </select>
            </div>
            <input
              type="text"
              required
              value={adjRemarks}
              onChange={(e) => setAdjRemarks(e.target.value)}
              placeholder="Audit remarks *"
              className="w-full rounded-xl border border-[var(--p-card-border)] bg-[var(--p-bg)] px-3 py-2 text-xs"
            />
            <div className="flex justify-end gap-2">
              <button
                type="button"
                onClick={() => setAdjTarget(null)}
                className="rounded-lg border border-[var(--p-card-border)] px-3 py-1.5 text-xs"
              >
                Cancel
              </button>
              <button
                type="submit"
                className="rounded-lg bg-[var(--p-primary)] px-4 py-1.5 text-xs font-semibold text-[var(--p-primary-text)]"
              >
                Record Adjustment
              </button>
            </div>
          </form>
        </div>
      )}

      {/* Warehouse Transfer Modal */}
      {trfTarget && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 p-4">
          <form
            onSubmit={submitTransfer}
            className="w-full max-w-md rounded-2xl border border-[var(--p-card-border)] bg-[var(--p-card)] p-5 space-y-3"
          >
            <div className="flex items-center justify-between">
              <h3 className="text-sm font-bold">Warehouse Transfer · {trfTarget.name}</h3>
              <button type="button" onClick={() => setTrfTarget(null)}>
                <X className="w-4 h-4" />
              </button>
            </div>
            <div className="grid grid-cols-2 gap-3">
              <div>
                <label className="block text-xs mb-1">From</label>
                <select
                  value={trfFrom}
                  onChange={(e) => setTrfFrom(e.target.value)}
                  className="w-full rounded-xl border border-[var(--p-card-border)] bg-[var(--p-bg)] px-2.5 py-2 text-xs"
                >
                  {state.inventoryLocations.map((l) => (
                    <option key={l.id} value={l.id}>
                      {l.name}
                    </option>
                  ))}
                </select>
              </div>
              <div>
                <label className="block text-xs mb-1">To</label>
                <select
                  value={trfTo}
                  onChange={(e) => setTrfTo(e.target.value)}
                  className="w-full rounded-xl border border-[var(--p-card-border)] bg-[var(--p-bg)] px-2.5 py-2 text-xs"
                >
                  {state.inventoryLocations.map((l) => (
                    <option key={l.id} value={l.id}>
                      {l.name}
                    </option>
                  ))}
                </select>
              </div>
            </div>
            <input
              type="number"
              step="0.001"
              required
              value={trfQty}
              onChange={(e) => setTrfQty(e.target.value)}
              placeholder="Quantity"
              className="w-full rounded-xl border border-[var(--p-card-border)] bg-[var(--p-bg)] px-3 py-2 text-xs font-mono-num"
            />
            <input
              type="text"
              value={trfRemarks}
              onChange={(e) => setTrfRemarks(e.target.value)}
              placeholder="Challan / Transfer note"
              className="w-full rounded-xl border border-[var(--p-card-border)] bg-[var(--p-bg)] px-3 py-2 text-xs"
            />
            <div className="flex justify-end gap-2">
              <button
                type="button"
                onClick={() => setTrfTarget(null)}
                className="rounded-lg border border-[var(--p-card-border)] px-3 py-1.5 text-xs"
              >
                Cancel
              </button>
              <button
                type="submit"
                className="rounded-lg bg-[var(--p-primary)] px-4 py-1.5 text-xs font-semibold text-[var(--p-primary-text)]"
              >
                Transfer Stock
              </button>
            </div>
          </form>
        </div>
      )}
    </div>
  );
};
