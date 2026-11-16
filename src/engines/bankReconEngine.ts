import { BankReconItem, Party, Transaction } from '../types/models';
import { normalizeDate, parsePaiseFromRupees } from './financialEngine';
import { parseDelimitedRecords } from './gstr1ImportEngine';

export type DiscrepancyResolutionType =
  | 'DIRECT_EXPENSE'
  | 'BANK_CHARGES'
  | 'CUSTOMER_PAYMENT'
  | 'SUPPLIER_PAYMENT'
  | 'INTEREST_INCOME'
  | 'TRANSFER';

export interface StatementParseResult {
  items: BankReconItem[];
  totalCreditsPaise: number;
  totalDebitsPaise: number;
  detectedBank: string;
  format: string;
  parseErrors: string[];
}

function normalizeStmtDate(raw: string): string {
  const direct = normalizeDate(raw);
  if (direct) return direct;
  const trimmed = raw.trim().replace(/\//g, '-');
  const m = trimmed.match(/^(\d{1,2})-([A-Za-z]{3})-(\d{2,4})$/);
  if (m) {
    const months: Record<string, string> = {
      jan: '01', feb: '02', mar: '03', apr: '04', may: '05', jun: '06',
      jul: '07', aug: '08', sep: '09', oct: '10', nov: '11', dec: '12',
    };
    const mon = months[m[2].toLowerCase()];
    if (mon) {
      const y = m[3].length === 2 ? `20${m[3]}` : m[3];
      return `${y}-${mon}-${m[1].padStart(2, '0')}`;
    }
  }
  return '';
}

function parseUnsignedPaise(str: string): number {
  if (!str || !str.trim()) return 0;
  let u = str.trim();
  if (u.startsWith('(') && u.endsWith(')')) u = u.slice(1, -1).trim();
  u = u.replace(/[+-]/g, '').trim();
  return parsePaiseFromRupees(u);
}

export function parseStatementText(
  rawContent: string,
  targetBankAccountId: string,
  targetBankName = 'Bank Account'
): StatementParseResult {
  const trimmed = rawContent.trim();
  if (/<OFX>/i.test(trimmed) || /<STMTTRN>/i.test(trimmed)) {
    return parseOfxStatement(trimmed, targetBankAccountId, targetBankName);
  }
  return parseCsvStatement(trimmed, targetBankAccountId, targetBankName);
}

export function parseCsvStatement(
  csvText: string,
  targetBankAccountId: string,
  targetBankName: string
): StatementParseResult {
  const cleanText = csvText.replace(/^\uFEFF/, '').trim();
  if (!cleanText) {
    return {
      items: [],
      totalCreditsPaise: 0,
      totalDebitsPaise: 0,
      detectedBank: targetBankName,
      format: 'CSV',
      parseErrors: ['File is empty'],
    };
  }
  const firstNonBlank = cleanText.split(/\r?\n/).find((l) => l.trim() !== '') || '';
  const delimiter = firstNonBlank.includes('\t') && !firstNonBlank.includes(',') ? '\t' : ',';
  const rows = parseDelimitedRecords(cleanText, delimiter);
  if (rows.length === 0) {
    return {
      items: [],
      totalCreditsPaise: 0,
      totalDebitsPaise: 0,
      detectedBank: targetBankName,
      format: 'CSV',
      parseErrors: ['No rows found'],
    };
  }

  let headerIndex = -1;
  let dateCol = -1;
  let descCol = -1;
  let refCol = -1;
  let debitCol = -1;
  let creditCol = -1;
  let amountCol = -1;
  let typeCol = -1;

  for (let i = 0; i < Math.min(rows.length, 15); i++) {
    const cols = rows[i].map((c) => c.trim().toLowerCase());
    const hasDate = cols.some((c) => c.includes('date'));
    const hasNarr = cols.some((c) => /narration|desc|particular|detail|remark/.test(c));
    const hasAmt = cols.some((c) => /withdrawal|debit|deposit|credit|amount/.test(c));
    if (hasDate && (hasNarr || hasAmt)) {
      headerIndex = i;
      cols.forEach((n, idx) => {
        if (dateCol === -1 && n.includes('date')) dateCol = idx;
        else if (descCol === -1 && /narration|desc|particular|detail|remark|payee/.test(n)) descCol = idx;
        else if (refCol === -1 && /ref|chq|cheque|utr|tran id/.test(n)) refCol = idx;
        else if (debitCol === -1 && (n.includes('withdrawal') || n.includes('debit') || (n.includes('dr') && !n.includes('address')))) debitCol = idx;
        else if (creditCol === -1 && (n.includes('deposit') || n.includes('credit') || (n.includes('cr') && !n.includes('description')))) creditCol = idx;
        else if (amountCol === -1 && n.includes('amount') && !n.includes('tax')) amountCol = idx;
        else if (typeCol === -1 && (n === 'type' || n.includes('dr/cr') || n.includes('cr/dr'))) typeCol = idx;
      });
      break;
    }
  }

  if (headerIndex === -1) {
    headerIndex = 0;
    dateCol = 0;
    descCol = 1;
    debitCol = 2;
    creditCol = 3;
  }

  const parsedItems: BankReconItem[] = [];
  const errors: string[] = [];
  const baseTs = Date.now();

  for (let i = headerIndex + 1; i < rows.length; i++) {
    const cols = rows[i];
    if (!cols.length || cols.every((c) => !c.trim())) continue;
    const rawDate = dateCol >= 0 && dateCol < cols.length ? cols[dateCol].trim() : '';
    const normDate = rawDate ? normalizeStmtDate(rawDate) : '';
    const desc = descCol >= 0 && descCol < cols.length ? cols[descCol].trim() : 'Bank Transaction';
    const ref = refCol >= 0 && refCol < cols.length ? cols[refCol].trim() : '';

    const debitStr = debitCol >= 0 && debitCol < cols.length ? cols[debitCol].trim() : '';
    const creditStr = creditCol >= 0 && creditCol < cols.length ? cols[creditCol].trim() : '';
    const amountStr = amountCol >= 0 && amountCol < cols.length ? cols[amountCol].trim() : '';
    const typeStr = typeCol >= 0 && typeCol < cols.length ? cols[typeCol].trim().toUpperCase() : '';

    const debitVal = parseUnsignedPaise(debitStr);
    const creditVal = parseUnsignedPaise(creditStr);
    let isCredit = false;
    let amountPaise = 0;

    if (creditVal > 0) {
      isCredit = true;
      amountPaise = creditVal;
    } else if (debitVal > 0) {
      isCredit = false;
      amountPaise = debitVal;
    } else if (amountStr) {
      const u = amountStr.toUpperCase();
      const hasDr = u.endsWith('DR') || typeStr === 'DR' || typeStr === 'D' || typeStr.includes('DEBIT');
      const hasCr = u.endsWith('CR') || typeStr === 'CR' || typeStr === 'C' || typeStr.includes('CREDIT');
      const rawAmt = parseUnsignedPaise(amountStr.replace(/(DR|CR)$/i, ''));
      const isNeg = amountStr.startsWith('-') || (amountStr.startsWith('(') && amountStr.endsWith(')'));
      isCredit = hasDr ? false : hasCr ? !isNeg : !isNeg && rawAmt > 0;
      amountPaise = rawAmt;
    }

    if (amountPaise > 0) {
      parsedItems.push({
        id: `STMT-CSV-${baseTs}-${parsedItems.length + 1}`,
        bankAccountId: targetBankAccountId,
        date: normDate,
        description: desc,
        amountPaise,
        isCredit,
        isReconciled: false,
        matchedTxnId: null,
        referenceNo: ref,
        matchConfidence: 0,
        matchReason: '',
        discrepancyNote: isCredit
          ? 'Statement deposit waiting for ledger match'
          : 'Statement withdrawal waiting for ledger match',
        isUserConfirmed: false,
        reconciledAt: null,
      });
    }
  }

  return {
    items: parsedItems,
    totalCreditsPaise: parsedItems.filter((x) => x.isCredit).reduce((s, x) => s + x.amountPaise, 0),
    totalDebitsPaise: parsedItems.filter((x) => !x.isCredit).reduce((s, x) => s + x.amountPaise, 0),
    detectedBank: targetBankName,
    format: 'CSV',
    parseErrors: errors,
  };
}

function extractOfxTag(content: string, tag: string): string {
  const m = content.match(new RegExp(`<${tag}>([^<\\r\\n]+)`, 'i'));
  return m ? m[1].trim() : '';
}

export function parseOfxStatement(
  ofxText: string,
  targetBankAccountId: string,
  targetBankName: string
): StatementParseResult {
  const parsedItems: BankReconItem[] = [];
  const baseTs = Date.now();
  const blocks = ofxText.match(/<STMTTRN>[\s\S]*?(?=<\/STMTTRN>|<STMTTRN>|$)/gi) || [];

  blocks.forEach((trn, idx) => {
    const trnType = extractOfxTag(trn, 'TRNTYPE').toUpperCase();
    const dtPosted = extractOfxTag(trn, 'DTPOSTED');
    const trnAmt = extractOfxTag(trn, 'TRNAMT');
    const fitId = extractOfxTag(trn, 'FITID');
    const name = extractOfxTag(trn, 'NAME');
    const memo = extractOfxTag(trn, 'MEMO');
    const checkNum = extractOfxTag(trn, 'CHECKNUM');
    const fullDesc = [name, memo].filter(Boolean).join(' - ') || `OFX Transaction ${trnType}`;
    const amountPaise = parseUnsignedPaise(trnAmt);
    const isNegType = ['DEBIT', 'CHECK', 'ATM', 'POS', 'FEE', 'SRVCHG', 'DIRECTDEBIT', 'PAYMENT'].includes(trnType);
    const isPosType = ['CREDIT', 'DEP', 'DIRECTDEP', 'INT', 'DIV'].includes(trnType);
    const isCredit = trnAmt.startsWith('-') ? false : isNegType ? false : isPosType ? true : trnAmt !== '';
    const d8 = dtPosted.slice(0, 8);
    const normDate = /^\d{8}$/.test(d8) ? `${d8.slice(0, 4)}-${d8.slice(4, 6)}-${d8.slice(6, 8)}` : normalizeStmtDate(dtPosted);

    if (amountPaise > 0) {
      parsedItems.push({
        id: `STMT-OFX-${baseTs}-${idx + 1}`,
        bankAccountId: targetBankAccountId,
        date: normDate,
        description: fullDesc,
        amountPaise,
        isCredit,
        isReconciled: false,
        matchedTxnId: null,
        referenceNo: fitId || checkNum,
        matchConfidence: 0,
        matchReason: '',
        discrepancyNote: isCredit ? 'OFX Credit entry' : 'OFX Debit entry',
        isUserConfirmed: false,
        reconciledAt: null,
      });
    }
  });

  return {
    items: parsedItems,
    totalCreditsPaise: parsedItems.filter((x) => x.isCredit).reduce((s, x) => s + x.amountPaise, 0),
    totalDebitsPaise: parsedItems.filter((x) => !x.isCredit).reduce((s, x) => s + x.amountPaise, 0),
    detectedBank: targetBankName,
    format: 'OFX/QFX',
    parseErrors: [],
  };
}

function dayDiff(d1: string, d2: string): number {
  const n1 = normalizeDate(d1);
  const n2 = normalizeDate(d2);
  if (!n1 || !n2) return 99;
  return Math.abs(Math.round((Date.parse(n1) - Date.parse(n2)) / 86_400_000));
}

export function matchBankStatement(
  statementItems: BankReconItem[],
  transactions: Transaction[],
  parties: Party[]
): BankReconItem[] {
  const partyMap = new Map(parties.map((p) => [p.id, p]));
  const available = transactions.filter((t) => !t.isVoided && t.type !== 'Quotation');
  const confirmedIds = new Set(
    statementItems.filter((s) => s.isUserConfirmed && s.matchedTxnId).map((s) => s.matchedTxnId!)
  );
  const usedIds = new Set(confirmedIds);

  return statementItems.map((stmt) => {
    if (stmt.isUserConfirmed && stmt.isReconciled) return stmt;

    let bestTxn: Transaction | null = null;
    let bestScore = 0;
    let bestReasons: string[] = [];

    for (const txn of available) {
      if (usedIds.has(txn.id)) continue;
      let score = 0;
      const reasons: string[] = [];
      const diff = Math.abs(stmt.amountPaise - txn.amountPaise);
      if (diff === 0) {
        score += 50;
        reasons.push(`Exact amount ₹${(stmt.amountPaise / 100).toFixed(2)}`);
      } else if (diff <= 10000) {
        score += 25;
        reasons.push(`Near amount (₹${(diff / 100).toFixed(2)} diff)`);
      } else continue;

      const expectedCredit = ['Sale', 'NonGstBill', 'PaymentIn', 'DebitNote'].includes(txn.type);
      score += stmt.isCredit === expectedCredit ? 15 : -40;

      const dd = dayDiff(stmt.date, txn.date);
      if (dd === 0) {
        score += 20;
        reasons.push(`Same date (${stmt.date})`);
      } else if (dd <= 2) {
        score += 15;
        reasons.push(`Date within ${dd}d`);
      } else if (dd <= 5) {
        score += 10;
        reasons.push(`Date within ${dd}d`);
      } else if (dd <= 10) {
        score += 5;
        reasons.push('Date within 10d');
      }

      const narr = stmt.description.toLowerCase();
      const party = txn.partyId ? partyMap.get(txn.partyId) : undefined;
      if (party) {
        const pn = party.name.toLowerCase();
        if (narr.includes(pn)) {
          score += 25;
          reasons.push(`Payee: ${party.name}`);
        } else if (pn.split(' ').some((w) => w.length > 2 && narr.includes(w))) {
          score += 15;
          reasons.push(`Party keyword: ${party.name}`);
        }
      }

      if (txn.invoiceNumber) {
        const invL = txn.invoiceNumber.toLowerCase();
        const digits = invL.replace(/\D/g, '');
        if (narr.includes(invL)) {
          score += 25;
          reasons.push(`Invoice #${txn.invoiceNumber}`);
        } else if (digits.length >= 4 && narr.includes(digits)) {
          score += 15;
          reasons.push(`Invoice ref #${digits}`);
        }
      }

      if (stmt.referenceNo && txn.remarks.toLowerCase().includes(stmt.referenceNo.toLowerCase())) {
        score += 20;
        reasons.push(`Ref #${stmt.referenceNo}`);
      }

      const clamped = Math.min(100, Math.max(0, score));
      if (clamped > bestScore) {
        bestScore = clamped;
        bestTxn = txn;
        bestReasons = reasons;
      }
    }

    if (bestTxn && bestScore >= 50) {
      usedIds.add(bestTxn.id);
      const isExact = bestScore >= 80;
      return {
        ...stmt,
        matchedTxnId: bestTxn.id,
        matchConfidence: bestScore,
        matchReason: bestReasons.join(' · '),
        isReconciled: isExact,
        discrepancyNote: isExact ? '' : 'Probable match: Review details before confirming',
      };
    }

    return {
      ...stmt,
      matchedTxnId: null,
      matchConfidence: 0,
      matchReason: '',
      isReconciled: false,
      discrepancyNote: stmt.isCredit
        ? 'Unrecorded deposit / customer receipt'
        : 'Unrecorded payment, expense or bank charge',
    };
  });
}

export function createBalancingTransaction(
  stmt: BankReconItem,
  resolutionType: DiscrepancyResolutionType,
  branchId = 'BR-MAIN',
  partyId: string | null = null,
  customRemarks = ''
): Transaction {
  const txnId = `TXN-RECON-${Date.now()}-${Math.random().toString(16).slice(2, 8)}`;
  const remarks = customRemarks.trim() || `Reconciled from statement: ${stmt.description}`;

  if (resolutionType === 'BANK_CHARGES') {
    const totalPaise = stmt.amountPaise;
    const taxableHalfUp = Math.floor((totalPaise * 10000 + 5900) / 11800);
    const eachGst = Math.floor((taxableHalfUp * 900 + 5000) / 10000);
    const taxablePaise = totalPaise - eachGst * 2;
    return {
      id: txnId,
      branchId,
      date: stmt.date || new Date().toISOString().slice(0, 10),
      partyId: null,
      amountPaise: totalPaise,
      type: 'Expense',
      expenseCategory: 'Indirect',
      paymentMode: 'Bank',
      bankAccountId: stmt.bankAccountId,
      remarks: `Bank SMS / Service Charges: ${stmt.description}`,
      entryMode: 'itemized',
      invoiceNumber: `BNK-CHG-${(stmt.date || '').replace(/-/g, '')}`,
      isVoided: false,
      voidReason: '',
      voidedAt: null,
      items: [
        {
          itemId: 'EXP-BANK-CHG',
          name: 'Bank Service & Account Charges',
          hsnCode: '9971',
          quantityMilli: 1000,
          unit: 'SRV',
          ratePaise: taxablePaise,
          gstRateBps: 1800,
          taxableAmountPaise: taxablePaise,
          cgstPaise: eachGst,
          sgstPaise: eachGst,
          igstPaise: 0,
          totalPaise,
          cessPaise: 0,
        },
      ],
    };
  }

  if (resolutionType === 'INTEREST_INCOME') {
    return {
      id: txnId,
      branchId,
      date: stmt.date || new Date().toISOString().slice(0, 10),
      partyId: null,
      amountPaise: stmt.amountPaise,
      type: 'PaymentIn',
      expenseCategory: 'Indirect',
      paymentMode: 'Bank',
      bankAccountId: stmt.bankAccountId,
      remarks: `Bank Interest Credit: ${stmt.description}`,
      entryMode: 'quick',
      invoiceNumber: `INT-${(stmt.date || '').replace(/-/g, '')}`,
      isVoided: false,
      voidReason: '',
      voidedAt: null,
      items: [],
    };
  }

  return {
    id: txnId,
    branchId,
    date: stmt.date || new Date().toISOString().slice(0, 10),
    partyId:
      resolutionType === 'CUSTOMER_PAYMENT' || resolutionType === 'SUPPLIER_PAYMENT'
        ? partyId
        : null,
    amountPaise: stmt.amountPaise,
    type:
      resolutionType === 'CUSTOMER_PAYMENT'
        ? 'PaymentIn'
        : resolutionType === 'SUPPLIER_PAYMENT'
          ? 'PaymentOut'
          : resolutionType === 'DIRECT_EXPENSE'
            ? 'Expense'
            : stmt.isCredit
              ? 'PaymentIn'
              : 'PaymentOut',
    expenseCategory: resolutionType === 'DIRECT_EXPENSE' ? 'Direct' : 'Indirect',
    paymentMode: 'Bank',
    bankAccountId: stmt.bankAccountId,
    remarks,
    entryMode: 'quick',
    invoiceNumber: '',
    isVoided: false,
    voidReason: '',
    voidedAt: null,
    items: [],
  };
}

export function getSampleHdfcStatementCsv(): string {
  return `Date,Narration,Chq./Ref.No.,Value Dt,Withdrawal Amt.,Deposit Amt.,Closing Balance
01/10/2026,"UPI-GPAY-RECVD FROM SAI BALAJI-9820192837",UPI/6271829102,01/10/2026,,10000.00,195000.00
01/10/2026,"POS SWIPE BATCH SETTLEMENT DADAR STORE",POS/20261001-9,01/10/2026,,4250.00,199250.00
02/10/2026,"NEFT-SENT TO GUJARAT ROYAL SPICES-PUR/GJ-9912",NEFT/HDFC001928,02/10/2026,25000.00,,174250.00
02/10/2026,"MONTHLY BANK SMS CHARGES & GST",CHG/Q3/SMS-29,02/10/2026,29.50,,174220.50
02/10/2026,"CHQ CLR-DIVIDEND / MISC SETTLEMENT",CHQ/918231,02/10/2026,,1250.00,175470.50
03/10/2026,"IMPS-RECVD FROM OMKAR DEPARTMENTAL STORE",IMPS/619283712,03/10/2026,,8500.00,183970.50
03/10/2026,"DIRECT DEBIT-BESCOM ELECTRICITY DISPATCH",DD/PWR-7712,03/10/2026,3500.00,,180470.50`;
}

export function getSampleSbiStatementOfx(): string {
  return `<OFX>
<BANKMSGSRSV1><STMTTRNRS><STMTRS><BANKTRANLIST>
<STMTTRN><TRNTYPE>CREDIT<DTPOSTED>20261001<TRNAMT>10000.00<FITID>SBI20261001001<NAME>UPI-RECVD FROM SAI BALAJI<MEMO>INV/26-27/0001 PART PAYMENT</STMTTRN>
<STMTTRN><TRNTYPE>DEBIT<DTPOSTED>20261002<TRNAMT>-25000.00<FITID>SBI20261002002<NAME>NEFT TO GUJARAT ROYAL SPICES<MEMO>PUR/GJ-9912 INVOICE PAYMENT</STMTTRN>
<STMTTRN><TRNTYPE>DEBIT<DTPOSTED>20261002<TRNAMT>-29.50<FITID>SBI20261002003<NAME>SBI CONSOLIDATED SMS CHARGES<MEMO>BANK SERVICE CHARGES</STMTTRN>
<STMTTRN><TRNTYPE>CREDIT<DTPOSTED>20261003<TRNAMT>8500.00<FITID>SBI20261003004<NAME>IMPS-OMKAR DEPT STORE<MEMO>KHATA COLLECTION</STMTTRN>
</BANKTRANLIST></STMTRS></STMTTRNRS></BANKMSGSRSV1>
</OFX>`;
}
