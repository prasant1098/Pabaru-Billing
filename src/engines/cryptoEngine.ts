import { AuditLog } from '../types/models';

export const GENESIS_HASH = '0'.repeat(64);

// Pure TypeScript synchronous SHA-256 (FIPS 180-4) so audit chain verification & record hashing work synchronously
const K256 = new Uint32Array([
  0x428a2f98, 0x71374491, 0xb5c0fbcf, 0xe9b5dba5, 0x3956c25b, 0x59f111f1, 0x923f82a4, 0xab1c5ed5,
  0xd807aa98, 0x12835b01, 0x243185be, 0x550c7dc3, 0x72be5d74, 0x80deb1fe, 0x9bdc06a7, 0xc19bf174,
  0xe49b69c1, 0xefbe4786, 0x0fc19dc6, 0x240ca1cc, 0x2de92c6f, 0x4a7484aa, 0x5cb0a9dc, 0x76f988da,
  0x983e5152, 0xa831c66d, 0xb00327c8, 0xbf597fc7, 0xc6e00bf3, 0xd5a79147, 0x06ca6351, 0x14292967,
  0x27b70a85, 0x2e1b2138, 0x4d2c6dfc, 0x53380d13, 0x650a7354, 0x766a0abb, 0x81c2c92e, 0x92722c85,
  0xa2bfe8a1, 0xa81a664b, 0xc24b8b70, 0xc76c51a3, 0xd192e819, 0xd6990624, 0xf40e3585, 0x106aa070,
  0x19a4c116, 0x1e376c08, 0x2748774c, 0x34b0bcb5, 0x391c0cb3, 0x4ed8aa4a, 0x5b9cca4f, 0x682e6ff3,
  0x748f82ee, 0x78a5636f, 0x84c87814, 0x8cc70208, 0x90befffa, 0xa4506ceb, 0xbef9a3f7, 0xc67178f2,
]);

function rotr(x: number, n: number): number {
  return (x >>> n) | (x << (32 - n));
}

export function sha256(input: string): string {
  const bytes = new TextEncoder().encode(input);
  const bitLen = bytes.length * 8;
  const paddedLen = Math.ceil((bytes.length + 9) / 64) * 64;
  const padded = new Uint8Array(paddedLen);
  padded.set(bytes);
  padded[bytes.length] = 0x80;
  const view = new DataView(padded.buffer);
  view.setUint32(paddedLen - 8, Math.floor(bitLen / 0x100000000), false);
  view.setUint32(paddedLen - 4, bitLen >>> 0, false);

  let h0 = 0x6a09e667;
  let h1 = 0xbb67ae85;
  let h2 = 0x3c6ef372;
  let h3 = 0xa54ff53a;
  let h4 = 0x510e527f;
  let h5 = 0x9b05688c;
  let h6 = 0x1f83d9ab;
  let h7 = 0x5be0cd19;

  const w = new Uint32Array(64);
  for (let offset = 0; offset < paddedLen; offset += 64) {
    for (let i = 0; i < 16; i++) {
      w[i] = view.getUint32(offset + i * 4, false);
    }
    for (let i = 16; i < 64; i++) {
      const s0 = rotr(w[i - 15], 7) ^ rotr(w[i - 15], 18) ^ (w[i - 15] >>> 3);
      const s1 = rotr(w[i - 2], 17) ^ rotr(w[i - 2], 19) ^ (w[i - 2] >>> 10);
      w[i] = (w[i - 16] + s0 + w[i - 7] + s1) >>> 0;
    }

    let a = h0;
    let b = h1;
    let c = h2;
    let d = h3;
    let e = h4;
    let f = h5;
    let g = h6;
    let h = h7;

    for (let i = 0; i < 64; i++) {
      const S1 = rotr(e, 6) ^ rotr(e, 11) ^ rotr(e, 25);
      const ch = (e & f) ^ (~e & g);
      const temp1 = (h + S1 + ch + K256[i] + w[i]) >>> 0;
      const S0 = rotr(a, 2) ^ rotr(a, 13) ^ rotr(a, 22);
      const maj = (a & b) ^ (a & c) ^ (b & c);
      const temp2 = (S0 + maj) >>> 0;

      h = g;
      g = f;
      f = e;
      e = (d + temp1) >>> 0;
      d = c;
      c = b;
      b = a;
      a = (temp1 + temp2) >>> 0;
    }

    h0 = (h0 + a) >>> 0;
    h1 = (h1 + b) >>> 0;
    h2 = (h2 + c) >>> 0;
    h3 = (h3 + d) >>> 0;
    h4 = (h4 + e) >>> 0;
    h5 = (h5 + f) >>> 0;
    h6 = (h6 + g) >>> 0;
    h7 = (h7 + h) >>> 0;
  }

  return [h0, h1, h2, h3, h4, h5, h6, h7]
    .map((v) => v.toString(16).padStart(8, '0'))
    .join('');
}

export function safeBase64Encode(bytes: Uint8Array): string {
  let bin = '';
  for (let i = 0; i < bytes.length; i++) {
    bin += String.fromCharCode(bytes[i]);
  }
  return btoa(bin);
}

export function safeBase64Decode(encoded: string): Uint8Array {
  const bin = atob(encoded.trim());
  const out = new Uint8Array(bin.length);
  for (let i = 0; i < bin.length; i++) {
    out[i] = bin.charCodeAt(i);
  }
  return out;
}

export function sanitizeReason(reason?: string | null): string {
  if (!reason) return '';
  return safeBase64Encode(new TextEncoder().encode(reason));
}

export function canonicalizeJson(input: unknown): string {
  if (input === null || input === undefined) return '';
  if (Array.isArray(input)) {
    return '[' + input.map((v) => canonicalizeJson(v)).join(',') + ']';
  }
  if (typeof input === 'object') {
    const obj = input as Record<string, unknown>;
    const keys = Object.keys(obj).sort();
    return '{' + keys.map((k) => JSON.stringify(k) + ':' + canonicalizeJson(obj[k])).join(',') + '}';
  }
  if (typeof input === 'string') {
    const trimmed = input.trim();
    if (
      (trimmed.startsWith('{') && trimmed.endsWith('}')) ||
      (trimmed.startsWith('[') && trimmed.endsWith(']'))
    ) {
      try {
        const parsed = JSON.parse(trimmed);
        if (parsed && typeof parsed === 'object') {
          return canonicalizeJson(parsed);
        }
      } catch {
        // treat as plain string
      }
    }
    return JSON.stringify(input);
  }
  if (typeof input === 'number' || typeof input === 'boolean') {
    return String(input);
  }
  return JSON.stringify(String(input));
}

export function computeCanonicalPayloadHash(details?: string | null): string {
  if (!details) return sha256('');
  return sha256(canonicalizeJson(details));
}

function sanitizeDelimiterField(field: string): string {
  return field.replace(/\|/g, '_');
}

export function buildAuditPayloadString(
  previousHash: string,
  entityId: string,
  entityType: string,
  action: string,
  canonicalPayloadHash: string,
  sanitizedReason: string,
  actor: string,
  isoTimestamp: string
): string {
  return [
    previousHash,
    sanitizeDelimiterField(entityId),
    sanitizeDelimiterField(entityType),
    sanitizeDelimiterField(action),
    canonicalPayloadHash,
    sanitizedReason,
    sanitizeDelimiterField(actor),
    isoTimestamp,
  ].join('|');
}

export function createAuditRecord(params: {
  prevHash: string | null;
  entityType: string;
  entityId: string;
  action: string;
  details: string;
  reason: string;
  actor: string;
  customTimestamp?: string;
}): AuditLog {
  const previousHash = params.prevHash || GENESIS_HASH;
  const timestamp = params.customTimestamp || new Date().toISOString();
  const canonicalPayloadHash = computeCanonicalPayloadHash(params.details);
  const sanitizedReason = sanitizeReason(params.reason);
  const payload = buildAuditPayloadString(
    previousHash,
    params.entityId,
    params.entityType,
    params.action,
    canonicalPayloadHash,
    sanitizedReason,
    params.actor,
    timestamp
  );
  const recordHash = sha256(payload);
  const rand = Math.random().toString(16).slice(2, 10);
  return {
    id: `AUD-${Date.now()}-${rand}`,
    previousHash,
    recordHash,
    entityType: params.entityType,
    entityId: params.entityId,
    action: params.action,
    details: params.details,
    reason: params.reason,
    actor: params.actor,
    timestamp,
  };
}

export interface VerificationResult {
  valid: boolean;
  totalCount: number;
  corruptedIndex: number;
  corruptedLogId: string | null;
  errorMessage?: string;
}

export function verifyAuditChain(auditLogs: AuditLog[]): VerificationResult {
  if (auditLogs.length === 0) {
    return { valid: true, totalCount: 0, corruptedIndex: -1, corruptedLogId: null };
  }
  let expectedPrevHash = GENESIS_HASH;
  for (let i = 0; i < auditLogs.length; i++) {
    const log = auditLogs[i];
    if (log.previousHash !== expectedPrevHash) {
      return {
        valid: false,
        totalCount: auditLogs.length,
        corruptedIndex: i,
        corruptedLogId: log.id,
        errorMessage: `Previous hash mismatch at step ${i + 1}`,
      };
    }
    const canonicalPayloadHash = computeCanonicalPayloadHash(log.details);
    const sanitizedReason = sanitizeReason(log.reason);
    const payload = buildAuditPayloadString(
      log.previousHash,
      log.entityId,
      log.entityType,
      log.action,
      canonicalPayloadHash,
      sanitizedReason,
      log.actor,
      log.timestamp
    );
    const calculated = sha256(payload);
    if (calculated !== log.recordHash) {
      return {
        valid: false,
        totalCount: auditLogs.length,
        corruptedIndex: i,
        corruptedLogId: log.id,
        errorMessage: `Tampering detected: hash mismatch at step ${i + 1}`,
      };
    }
    expectedPrevHash = log.recordHash;
  }
  return { valid: true, totalCount: auditLogs.length, corruptedIndex: -1, corruptedLogId: null };
}

export function hashPinSync(pin: string, salt = 'pabaru-salt-v1'): string {
  // Multi-round salted SHA-256 for fast deterministic PIN checks + Web Crypto PBKDF2 support
  let h = sha256(`${salt}:${pin}`);
  for (let i = 0; i < 64; i++) {
    h = sha256(`${h}:${salt}:${pin}`);
  }
  return h;
}

export function generateSecureSalt(numBytes = 16): string {
  const bytes = new Uint8Array(numBytes);
  crypto.getRandomValues(bytes);
  return safeBase64Encode(bytes);
}

async function deriveAesKey(passphrase: string, salt: Uint8Array, iterations = 100000): Promise<CryptoKey> {
  const keyMaterial = await crypto.subtle.importKey(
    'raw',
    new TextEncoder().encode(passphrase),
    'PBKDF2',
    false,
    ['deriveKey']
  );
  return crypto.subtle.deriveKey(
    {
      name: 'PBKDF2',
      salt: salt.buffer as ArrayBuffer,
      iterations,
      hash: 'SHA-256',
    },
    keyMaterial,
    { name: 'AES-GCM', length: 256 },
    false,
    ['encrypt', 'decrypt']
  );
}

export async function encryptAesGcm(plaintext: string, passphrase: string): Promise<string> {
  if (!passphrase.trim()) throw new Error('Backup encryption passphrase must not be blank');
  const salt = new Uint8Array(16);
  const iv = new Uint8Array(12);
  crypto.getRandomValues(salt);
  crypto.getRandomValues(iv);
  const key = await deriveAesKey(passphrase, salt, 100000);
  const cipherBuf = await crypto.subtle.encrypt(
    { name: 'AES-GCM', iv },
    key,
    new TextEncoder().encode(plaintext)
  );
  return JSON.stringify(
    {
      format: 'pabaru-encrypted-backup-v1',
      kdf: 'PBKDF2WithHmacSHA256',
      iterations: 100000,
      cipher: 'AES-256-GCM',
      salt: safeBase64Encode(salt),
      iv: safeBase64Encode(iv),
      ciphertext: safeBase64Encode(new Uint8Array(cipherBuf)),
    },
    null,
    2
  );
}

export async function decryptAesGcm(envelopeJson: string, passphrase: string): Promise<string> {
  if (!passphrase.trim()) throw new Error('Backup decryption passphrase must not be blank');
  const obj = JSON.parse(envelopeJson);
  if (obj.format !== 'pabaru-encrypted-backup-v1') {
    throw new Error('Unsupported encrypted backup format');
  }
  const iterations = Math.min(1000000, Math.max(10000, Number(obj.iterations || 100000)));
  const salt = safeBase64Decode(obj.salt);
  const iv = safeBase64Decode(obj.iv);
  const ciphertext = safeBase64Decode(obj.ciphertext);
  const key = await deriveAesKey(passphrase, salt, iterations);
  const plainBuf = await crypto.subtle.decrypt(
    { name: 'AES-GCM', iv: iv.buffer as ArrayBuffer },
    key,
    ciphertext.buffer as ArrayBuffer
  );
  return new TextDecoder().decode(plainBuf);
}

const WORD_LIST = [
  'abundant', 'account', 'accurate', 'achieve', 'action', 'advance', 'agency', 'agile',
  'agree', 'ahead', 'airport', 'alert', 'alliance', 'alpha', 'amber', 'amount',
  'anchor', 'annual', 'answer', 'apex', 'apparel', 'apply', 'approve', 'arcade',
  'archive', 'arena', 'arrow', 'asset', 'atlas', 'audit', 'august', 'author',
  'avatar', 'avenue', 'award', 'balance', 'banana', 'banner', 'banyan', 'bargain',
  'barter', 'bazaar', 'beacon', 'benefit', 'bengaluru', 'bharat', 'bilateral', 'bison',
  'blossom', 'bonus', 'border', 'botany', 'branch', 'brave', 'breeze', 'bridge',
  'budget', 'buffalo', 'builder', 'bulletin', 'bundle', 'business', 'cabinet', 'cable',
  'cactus', 'calculate', 'calendar', 'calm', 'camera', 'canal', 'candle', 'canvas',
  'capital', 'captain', 'caravan', 'cardamom', 'career', 'cargo', 'carrier', 'castle',
  'catalyst', 'cautious', 'celestial', 'cement', 'center', 'century', 'ceramic', 'certify',
  'chain', 'chamber', 'champion', 'channel', 'charter', 'cheetah', 'chennai', 'cherry',
  'chilli', 'cinnamon', 'circle', 'circuit', 'citizen', 'citrus', 'civic', 'clarity',
  'classic', 'clay', 'client', 'climate', 'clove', 'cluster', 'cobalt', 'coconut',
  'coffee', 'cognition', 'coin', 'colleague', 'colony', 'comfort', 'command', 'commerce',
  'compact', 'company', 'compass', 'compound', 'compute', 'concept', 'concord', 'concrete',
  'connect', 'conquest', 'constant', 'consumer', 'contact', 'content', 'contour', 'contract',
  'copper', 'coral', 'corner', 'corridor', 'cosmos', 'cottage', 'cotton', 'counsel',
  'courage', 'courier', 'covenant', 'cradle', 'craft', 'credit', 'crescent', 'cricket',
  'criterion', 'crucial', 'crystal', 'cube', 'cuisine', 'culture', 'current', 'custom',
  'cypress', 'dairy', 'damson', 'daring', 'database', 'dealer', 'decade', 'decimal',
  'decor', 'dedicate', 'deep', 'defence', 'delhi', 'delta', 'demand', 'dentist',
  'depot', 'deposit', 'deputy', 'derby', 'design', 'desktop', 'destiny', 'detail',
  'detect', 'device', 'diamond', 'diary', 'diesel', 'digest', 'digital', 'dignity',
  'diligence', 'dimple', 'dinner', 'direct', 'director', 'discover', 'display', 'distance',
  'district', 'divide', 'dividend', 'divine', 'doctor', 'document', 'dolphin', 'domain',
  'donate', 'doorway', 'dragon', 'dynamic', 'eagle', 'early', 'earth', 'eastern',
  'economy', 'ecosystem', 'edition', 'educate', 'effort', 'elastic', 'elder', 'element',
  'elephant', 'elite', 'embark', 'emerald', 'empire', 'empower', 'energy', 'engine',
  'enhance', 'enterprise', 'entry', 'envelope', 'epoch', 'equator', 'equity', 'escape',
  'estate', 'eternal', 'ether', 'ethics', 'evidence', 'exact', 'exceed', 'excel',
  'exchange', 'execute', 'exempt', 'exotic', 'expand', 'expert', 'express', 'extend',
];

export function generateRecoveryPhrase(): string[] {
  const pool = [...WORD_LIST];
  const words: string[] = [];
  const rand = new Uint32Array(12);
  crypto.getRandomValues(rand);
  for (let i = 0; i < 12; i++) {
    const idx = rand[i] % pool.length;
    words.push(pool.splice(idx, 1)[0]);
  }
  return words;
}
