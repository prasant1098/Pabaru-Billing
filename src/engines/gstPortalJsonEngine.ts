import { BusinessProfile } from '../types/models';
import {
  GstPeriodFilter,
  StatutoryCmp08Data,
  StatutoryGstr1Data,
  StatutoryGstr3bData,
} from './cmaAndGstEngine';
import { normalizeDate } from './financialEngine';

function p2r(paise: number): number {
  return Math.round(paise) / 100;
}

function formatDateForGst(dateStr: string): string {
  const n = normalizeDate(dateStr);
  if (!n) return '';
  const [y, m, d] = n.split('-');
  return `${d}-${m}-${y}`;
}

export function sanitizeGstInvoiceNumber(inum: string): string {
  const filtered = inum.trim().replace(/[^a-zA-Z0-9-/]/g, '');
  if (!filtered) return 'INV-1';
  if (filtered.length <= 16) return filtered;
  const suffix = filtered.slice(-6);
  const prefix = filtered.slice(0, 6);
  return `${prefix}-G1-${suffix}`.slice(0, 16);
}

function sanitizeStateCode(pos: string, fallback = '27'): string {
  const digits = pos.replace(/\D/g, '');
  const num = Number.parseInt(digits, 10);
  if (Number.isFinite(num) && num >= 1 && num <= 99) return String(num).padStart(2, '0');
  const fb = Number.parseInt(fallback.replace(/\D/g, ''), 10) || 27;
  return String(fb).padStart(2, '0');
}

export function getFilingPeriodCode(filter: GstPeriodFilter): string {
  const now = new Date();
  const y = now.getFullYear();
  const m = now.getMonth() + 1;
  if (filter === 'CurrentMonth') return `${String(m).padStart(2, '0')}${y}`;
  if (filter === 'PreviousMonth') {
    const pm = m === 1 ? 12 : m - 1;
    const py = m === 1 ? y - 1 : y;
    return `${String(pm).padStart(2, '0')}${py}`;
  }
  if (filter === 'Quarter') {
    const qe = (Math.floor((m - 1) / 3) + 1) * 3;
    return `${String(qe).padStart(2, '0')}${y}`;
  }
  return `${String(m).padStart(2, '0')}${y}`;
}

export function generateGstr1Json(
  profile: BusinessProfile,
  gstr1: StatutoryGstr1Data,
  filter: GstPeriodFilter,
  annualTurnoverPaise?: number
): string {
  const fp = getFilingPeriodCode(filter);
  const curGt = gstr1.totalTaxablePaise + gstr1.totalTaxPaise;
  const gt = annualTurnoverPaise ?? curGt;

  const b2bGrouped = new Map<string, typeof gstr1.b2bInvoices>();
  for (const inv of gstr1.b2bInvoices) {
    const arr = b2bGrouped.get(inv.gstin) || [];
    arr.push(inv);
    b2bGrouped.set(inv.gstin, arr);
  }

  const b2b = Array.from(b2bGrouped.entries()).map(([ctin, invoices]) => ({
    ctin,
    cfs: 'Y',
    inv: invoices.map((inv) => ({
      inum: sanitizeGstInvoiceNumber(inv.invoiceNumber),
      idt: formatDateForGst(inv.date),
      val: p2r(inv.totalPaise),
      pos: sanitizeStateCode(inv.posStateCode || profile.homeStateCode, profile.homeStateCode),
      rchrg: 'N',
      inv_typ: 'R',
      itms: (inv.rateBreakdown.length
        ? inv.rateBreakdown
        : [
            {
              gstRateBps: 0,
              taxablePaise: inv.taxablePaise,
              cgstPaise: inv.cgstPaise,
              sgstPaise: inv.sgstPaise,
              igstPaise: inv.igstPaise,
            },
          ]
      ).map((rb, idx) => ({
        num: (idx + 1) * 100 + 1,
        itm_det: {
          rt: rb.gstRateBps / 100,
          txval: p2r(rb.taxablePaise),
          iamt: p2r(rb.igstPaise),
          camt: p2r(rb.cgstPaise),
          samt: p2r(rb.sgstPaise),
          csamt: 0,
        },
      })),
    })),
  }));

  const b2cs = gstr1.b2csSupplies
    .filter((s) => s.gstRateBps > 0)
    .map((s) => ({
      sply_ty: s.supplyType,
      pos: sanitizeStateCode(s.posStateCode || profile.homeStateCode, profile.homeStateCode),
      typ: 'OE',
      rt: s.gstRateBps / 100,
      txval: p2r(s.taxablePaise),
      iamt: p2r(s.igstPaise),
      camt: p2r(s.cgstPaise),
      samt: p2r(s.sgstPaise),
      csamt: 0,
    }));

  const root: Record<string, unknown> = {
    gstin: profile.gstin.trim(),
    fp,
    gt: p2r(gt),
    cur_gt: p2r(curGt),
    version: 'GST3.1.6',
    b2b,
    b2cs,
    hsn: {
      data: gstr1.hsnSummary.map((h, idx) => ({
        num: idx + 1,
        hsn_sc: h.hsnCode,
        desc: h.description,
        uqc: h.uqc,
        qty: Math.round(h.totalQuantityMilli) / 1000,
        rt: h.gstRateBps / 100,
        txval: p2r(h.taxablePaise),
        iamt: p2r(h.igstPaise),
        camt: p2r(h.cgstPaise),
        samt: p2r(h.sgstPaise),
        csamt: 0,
      })),
    },
    doc_issue: {
      doc_det: [
        {
          doc_num: 1,
          doc_typ: 'Invoices for outward supply',
          docs: [
            {
              num: 1,
              from: gstr1.docSummary.minInvoiceNumber || 'NIL',
              to: gstr1.docSummary.maxInvoiceNumber || 'NIL',
              totnum: gstr1.docSummary.totalIssued,
              canc: gstr1.docSummary.cancelledCount,
              net_issue: gstr1.docSummary.netIssuedCount,
            },
          ],
        },
      ],
    },
  };

  if (gstr1.nilExemptPaise > 0 || gstr1.nonGstOutwardPaise > 0) {
    root.nil = {
      inv: [
        {
          sply_ty: 'INTRB2C',
          nil_amt: p2r(gstr1.nilExemptPaise),
          expt_amt: 0,
          ngsup_amt: p2r(gstr1.nonGstOutwardPaise),
        },
      ],
    };
  }

  return JSON.stringify(root, null, 2);
}

export function generateGstr3bJson(
  profile: BusinessProfile,
  gstr3b: StatutoryGstr3bData,
  filter: GstPeriodFilter
): string {
  const m = gstr3b.setOffMatrix;
  return JSON.stringify(
    {
      gstin: profile.gstin.trim(),
      ret_period: getFilingPeriodCode(filter),
      sup_details: {
        osup_det: {
          txval: p2r(gstr3b.outwardTaxablePaise),
          iamt: p2r(gstr3b.outwardIgstPaise),
          camt: p2r(gstr3b.outwardCgstPaise),
          samt: p2r(gstr3b.outwardSgstPaise),
          csamt: 0,
        },
      },
      itc_elg: {
        itc_avl: [
          {
            ty: 'OTH',
            iamt: p2r(gstr3b.eligibleItcIgstPaise),
            camt: p2r(gstr3b.eligibleItcCgstPaise),
            samt: p2r(gstr3b.eligibleItcSgstPaise),
            csamt: 0,
          },
        ],
      },
      tx_pmt: {
        pd_itc: [
          {
            liab_ldg_id: 'IGST',
            iamt: p2r(m.igstUsedForIgstPaise),
            camt: p2r(m.cgstUsedForIgstPaise),
            samt: p2r(m.sgstUsedForIgstPaise),
            csamt: 0,
          },
          {
            liab_ldg_id: 'CGST',
            iamt: p2r(m.igstUsedForCgstPaise),
            camt: p2r(m.cgstUsedForCgstPaise),
            samt: 0,
            csamt: 0,
          },
          {
            liab_ldg_id: 'SGST',
            iamt: p2r(m.igstUsedForSgstPaise),
            camt: 0,
            samt: p2r(m.sgstUsedForSgstPaise),
            csamt: 0,
          },
        ],
        pd_cash: [
          { liab_ldg_id: 'IGST', iamt: p2r(m.cashIgstPaise), camt: 0, samt: 0, csamt: 0 },
          { liab_ldg_id: 'CGST', iamt: 0, camt: p2r(m.cashCgstPaise), samt: 0, csamt: 0 },
          { liab_ldg_id: 'SGST', iamt: 0, camt: 0, samt: p2r(m.cashSgstPaise), csamt: 0 },
        ],
      },
    },
    null,
    2
  );
}

export function generateCmp08Json(
  profile: BusinessProfile,
  cmp08: StatutoryCmp08Data,
  filter: GstPeriodFilter
): string {
  const halfInt = Math.floor(cmp08.interestPaise / 2);
  return JSON.stringify(
    {
      gstin: profile.gstin.trim(),
      rtn_prd: getFilingPeriodCode(filter),
      arn: '',
      cmp_details: [
        {
          sec_cd: '1',
          txval: p2r(cmp08.turnoverPaise),
          iamt: 0,
          camt: p2r(cmp08.centralTaxPaise),
          samt: p2r(cmp08.stateTaxPaise),
          csamt: 0,
        },
        {
          sec_cd: '2',
          txval: p2r(cmp08.rcmTurnoverPaise),
          iamt: p2r(cmp08.rcmIntegratedTaxPaise),
          camt: p2r(cmp08.rcmCentralTaxPaise),
          samt: p2r(cmp08.rcmStateTaxPaise),
          csamt: 0,
        },
        {
          sec_cd: '3',
          txval: p2r(cmp08.turnoverPaise + cmp08.rcmTurnoverPaise),
          iamt: p2r(cmp08.rcmIntegratedTaxPaise),
          camt: p2r(cmp08.centralTaxPaise + cmp08.rcmCentralTaxPaise),
          samt: p2r(cmp08.stateTaxPaise + cmp08.rcmStateTaxPaise),
          csamt: 0,
        },
        {
          sec_cd: '4',
          txval: 0,
          iamt: 0,
          camt: p2r(halfInt),
          samt: p2r(cmp08.interestPaise - halfInt),
          csamt: 0,
        },
      ],
    },
    null,
    2
  );
}
