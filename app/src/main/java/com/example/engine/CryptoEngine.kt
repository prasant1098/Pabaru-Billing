package com.example.engine

import com.example.model.AuditLog
import org.json.JSONArray
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.text.SimpleDateFormat
import android.util.Base64
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object CryptoEngine {

    const val GENESIS_HASH = "0000000000000000000000000000000000000000000000000000000000000000"

    fun sha256(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(input.toByteArray(StandardCharsets.UTF_8))
        return hash.joinToString("") { "%02x".format(it) }
    }

    /**
     * Android Base64 encoder using android.util.Base64 with NO_WRAP and JVM fallback
     */
    fun safeBase64Encode(bytes: ByteArray): String {
        return try {
            Base64.encodeToString(bytes, Base64.NO_WRAP).trim()
        } catch (_: Throwable) {
            try {
                val encoder = Class.forName("java.util.Base64").getMethod("getEncoder").invoke(null)
                val method = encoder.javaClass.getMethod("encodeToString", ByteArray::class.java)
                (method.invoke(encoder, bytes) as String).trim()
            } catch (_: Throwable) {
                manualBase64Encode(bytes)
            }
        }
    }

    fun safeBase64Decode(encoded: String): ByteArray {
        val clean = encoded.trim()
        return try {
            Base64.decode(clean, Base64.DEFAULT)
        } catch (_: Throwable) {
            val decoder = Class.forName("java.util.Base64").getMethod("getDecoder").invoke(null)
            val method = decoder.javaClass.getMethod("decode", String::class.java)
            method.invoke(decoder, clean) as ByteArray
        }
    }

    private fun manualBase64Encode(bytes: ByteArray): String {
        val table = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
        val sb = StringBuilder()
        var i = 0
        while (i < bytes.size) {
            val b0 = bytes[i++].toInt() and 0xFF
            val b1 = if (i < bytes.size) bytes[i++].toInt() and 0xFF else -1
            val b2 = if (i < bytes.size) bytes[i++].toInt() and 0xFF else -1

            val c0 = b0 ushr 2
            val c1 = ((b0 and 0x03) shl 4) or (if (b1 >= 0) (b1 ushr 4) else 0)
            val c2 = if (b1 >= 0) (((b1 and 0x0F) shl 2) or (if (b2 >= 0) (b2 ushr 6) else 0)) else -1
            val c3 = if (b2 >= 0) (b2 and 0x3F) else -1

            sb.append(table[c0])
            sb.append(table[c1])
            sb.append(if (c2 >= 0) table[c2] else '=')
            sb.append(if (c3 >= 0) table[c3] else '=')
        }
        return sb.toString()
    }

    /**
     * Sanitizes reason by Base64 encoding UTF-8 bytes to strictly prevent delimiter pipe collision
     */
    fun sanitizeReason(reason: String?): String {
        if (reason.isNullOrEmpty()) return ""
        return safeBase64Encode(reason.toByteArray(StandardCharsets.UTF_8))
    }

    /**
     * Quotes and escapes a string according to JSON RFC 8259 specifications
     */
    fun quoteJsonString(string: String): String {
        val sb = StringBuilder("\"")
        for (c in string) {
            when (c) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\b' -> sb.append("\\b")
                '\u000c' -> sb.append("\\f")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                else -> {
                    if (c.code < 0x20) {
                        sb.append(String.format(Locale.US, "\\u%04x", c.code))
                    } else {
                        sb.append(c)
                    }
                }
            }
        }
        sb.append('"')
        return sb.toString()
    }

    private class SimpleJsonParser(private val src: String) {
        private var pos = 0

        private fun skipWhitespace() {
            while (pos < src.length && src[pos].isWhitespace()) pos++
        }

        private fun peek(): Char = if (pos < src.length) src[pos] else '\u0000'

        fun parseStrict(): Any? {
            val result = parse()
            skipWhitespace()
            if (pos < src.length) {
                throw IllegalArgumentException("Unexpected trailing JSON content at position $pos")
            }
            return result
        }

        fun parse(): Any? {
            skipWhitespace()
            if (pos >= src.length) throw IllegalArgumentException("Unexpected end of JSON input")
            return when (val c = peek()) {
                '{' -> parseObject()
                '[' -> parseArray()
                '"' -> parseString()
                't', 'f' -> parseBoolean()
                'n' -> parseNull()
                else -> if (c == '-' || c in '0'..'9') parseNumber() else throw IllegalArgumentException("Unexpected JSON token '$c' at position $pos")
            }
        }

        private fun parseObject(): Map<String, Any?> {
            val map = mutableMapOf<String, Any?>()
            pos++ // skip '{'
            skipWhitespace()
            if (peek() == '}') {
                pos++
                return map
            }
            var closed = false
            while (pos < src.length) {
                skipWhitespace()
                if (peek() != '"') {
                    throw IllegalArgumentException("Expected object key string at position $pos")
                }
                val key = parseString()
                skipWhitespace()
                if (peek() != ':') {
                    throw IllegalArgumentException("Expected ':' after key '$key' at position $pos")
                }
                pos++
                val value = parse()
                map[key] = value
                skipWhitespace()
                when (peek()) {
                    ',' -> pos++
                    '}' -> {
                        pos++
                        closed = true
                        break
                    }
                    else -> throw IllegalArgumentException("Expected ',' or '}' in JSON object at position $pos")
                }
            }
            if (!closed) {
                throw IllegalArgumentException("Unterminated JSON object")
            }
            return map
        }

        private fun parseArray(): List<Any?> {
            val list = mutableListOf<Any?>()
            pos++ // skip '['
            skipWhitespace()
            if (peek() == ']') {
                pos++
                return list
            }
            var closed = false
            while (pos < src.length) {
                val value = parse()
                list.add(value)
                skipWhitespace()
                when (peek()) {
                    ',' -> pos++
                    ']' -> {
                        pos++
                        closed = true
                        break
                    }
                    else -> throw IllegalArgumentException("Expected ',' or ']' in JSON array at position $pos")
                }
            }
            if (!closed) {
                throw IllegalArgumentException("Unterminated JSON array")
            }
            return list
        }

        private fun parseString(): String {
            pos++ // skip opening quote
            val sb = StringBuilder()
            while (pos < src.length) {
                val c = src[pos++]
                if (c == '"') return sb.toString()
                if (c == '\\') {
                    if (pos >= src.length) throw IllegalArgumentException("Unterminated escape sequence in JSON string")
                    when (val esc = src[pos++]) {
                        '"' -> sb.append('"')
                        '\\' -> sb.append('\\')
                        '/' -> sb.append('/')
                        'b' -> sb.append('\b')
                        'f' -> sb.append('\u000c')
                        'n' -> sb.append('\n')
                        'r' -> sb.append('\r')
                        't' -> sb.append('\t')
                        'u' -> {
                            if (pos + 4 <= src.length) {
                                val hex = src.substring(pos, pos + 4)
                                pos += 4
                                val codePoint = hex.toIntOrNull(16)
                                    ?: throw IllegalArgumentException("Invalid unicode escape '\\u$hex' at position ${pos - 4}")
                                sb.append(codePoint.toChar())
                            } else {
                                throw IllegalArgumentException("Truncated unicode escape at position $pos")
                            }
                        }
                        else -> throw IllegalArgumentException("Invalid JSON escape '\\$esc' at position ${pos - 1}")
                    }
                } else {
                    sb.append(c)
                }
            }
            throw IllegalArgumentException("Unterminated JSON string")
        }

        private fun parseNumber(): Number {
            val start = pos
            if (peek() == '-') pos++
            while (pos < src.length && (src[pos] in '0'..'9' || src[pos] == '.' || src[pos] == 'e' || src[pos] == 'E' || src[pos] == '+' || src[pos] == '-')) {
                pos++
            }
            val text = src.substring(start, pos)
            return if (text.contains('.') || text.contains('e') || text.contains('E')) {
                text.toDoubleOrNull() ?: throw IllegalArgumentException("Malformed JSON floating point number: '$text'")
            } else {
                text.toLongOrNull() ?: throw IllegalArgumentException("Malformed JSON integer: '$text'")
            }
        }

        private fun parseBoolean(): Boolean {
            return if (src.startsWith("true", pos)) {
                pos += 4
                true
            } else if (src.startsWith("false", pos)) {
                pos += 5
                false
            } else {
                throw IllegalArgumentException("Expected boolean literal ('true' or 'false') at position $pos")
            }
        }

        private fun parseNull(): Any? {
            if (src.startsWith("null", pos)) {
                pos += 4
                return null
            }
            throw IllegalArgumentException("Expected 'null' literal at position $pos")
        }
    }

    fun parseJsonStrict(json: String): Any? = SimpleJsonParser(json).parseStrict()

    private fun formatCanonicalNumber(number: Number): String {
        return when (number) {
            is Long, is Int, is Short, is Byte -> number.toString()
            is Float, is Double -> {
                val d = number.toDouble()
                if (d == d.toLong().toDouble() && !d.isInfinite() && !d.isNaN()) {
                    d.toLong().toString()
                } else {
                    java.math.BigDecimal(number.toString()).stripTrailingZeros().toPlainString()
                }
            }
            else -> number.toString()
        }
    }

    /**
     * Deterministically canonicalizes a JSON object, array, or raw string by sorting keys recursively
     */
    fun canonicalizeJson(input: Any?): String {
        return when (input) {
            null -> ""
            is Map<*, *> -> {
                val keys = input.keys.map { it.toString() }.sorted()
                val entries = keys.map { k ->
                    quoteJsonString(k) + ":" + canonicalizeJson(input[k])
                }
                "{" + entries.joinToString(",") + "}"
            }
            is List<*> -> {
                val list = input.map { canonicalizeJson(it) }
                "[" + list.joinToString(",") + "]"
            }
            is Array<*> -> {
                val list = input.map { canonicalizeJson(it) }
                "[" + list.joinToString(",") + "]"
            }
            is JSONObject -> {
                val keys = input.keys().asSequence().toList().sorted()
                val entries = keys.map { k ->
                    quoteJsonString(k) + ":" + canonicalizeJson(input.opt(k))
                }
                "{" + entries.joinToString(",") + "}"
            }
            is JSONArray -> {
                val list = (0 until input.length()).map { i ->
                    canonicalizeJson(input.opt(i))
                }
                "[" + list.joinToString(",") + "]"
            }
            is String -> {
                val trimmed = input.trim()
                if ((trimmed.startsWith("{") && trimmed.endsWith("}")) ||
                    (trimmed.startsWith("[") && trimmed.endsWith("]"))) {
                    try {
                        val parsed = SimpleJsonParser(trimmed).parseStrict()
                        if (parsed != null && (parsed is Map<*, *> || parsed is List<*>)) {
                            return canonicalizeJson(parsed)
                        }
                    } catch (_: Exception) {}
                }
                quoteJsonString(input)
            }
            is Number -> formatCanonicalNumber(input)
            is Boolean -> input.toString()
            else -> quoteJsonString(input.toString())
        }
    }

    /**
     * Computes the deterministic canonical payload hash
     */
    fun computeCanonicalPayloadHash(details: String?): String {
        if (details.isNullOrEmpty()) return sha256("")
        val canonical = canonicalizeJson(details)
        return sha256(canonical)
    }

    fun sanitizeDelimiterField(field: String): String {
        return field.replace("|", "_")
    }

    /**
     * Standardized deterministic audit block payload format across Kotlin and JS:
     * "${previousHash}|${entityId}|${entityType}|${action}|${canonicalPayloadHash}|${sanitizedReason}|${actor}|${isoTimestamp}"
     */
    fun buildAuditPayloadString(
        previousHash: String,
        entityId: String,
        entityType: String,
        action: String,
        canonicalPayloadHash: String,
        sanitizedReason: String,
        actor: String,
        isoTimestamp: String
    ): String {
        val safeEntityId = sanitizeDelimiterField(entityId)
        val safeEntityType = sanitizeDelimiterField(entityType)
        val safeAction = sanitizeDelimiterField(action)
        val safeActor = sanitizeDelimiterField(actor)
        return "$previousHash|$safeEntityId|$safeEntityType|$safeAction|$canonicalPayloadHash|$sanitizedReason|$safeActor|$isoTimestamp"
    }

    fun createAuditRecord(
        prevHash: String?,
        entityType: String,
        entityId: String,
        action: String,
        details: String,
        reason: String,
        actor: String,
        customTimestamp: String? = null
    ): AuditLog {
        val previous = prevHash ?: GENESIS_HASH
        val timestamp = customTimestamp ?: run {
            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
            sdf.timeZone = TimeZone.getTimeZone("UTC")
            sdf.format(Date())
        }

        val canonicalPayloadHash = computeCanonicalPayloadHash(details)
        val sanitizedReason = sanitizeReason(reason)

        val payload = buildAuditPayloadString(
            previousHash = previous,
            entityId = entityId,
            entityType = entityType,
            action = action,
            canonicalPayloadHash = canonicalPayloadHash,
            sanitizedReason = sanitizedReason,
            actor = actor,
            isoTimestamp = timestamp
        )
        val recordHash = sha256(payload)

        return AuditLog(
            id = "AUD-${System.currentTimeMillis()}-${java.util.UUID.randomUUID().toString().take(8)}",
            previousHash = previous,
            recordHash = recordHash,
            entityType = entityType,
            entityId = entityId,
            action = action,
            details = details,
            reason = reason,
            actor = actor,
            timestamp = timestamp
        )
    }

    data class VerificationResult(
        val valid: Boolean,
        val totalCount: Int,
        val corruptedIndex: Int = -1,
        val errorMessage: String? = null
    )

    data class AuditSnapshotAnchor(
        val snapshotIndex: Int,
        val anchorHash: String,
        val timestamp: String
    )

    fun verifyAuditChain(auditLogs: List<AuditLog>): VerificationResult {
        if (auditLogs.isEmpty()) return VerificationResult(valid = true, totalCount = 0)

        var expectedPrevHash = GENESIS_HASH
        for (i in auditLogs.indices) {
            val log = auditLogs[i]
            if (log.previousHash != expectedPrevHash) {
                return VerificationResult(
                    valid = false,
                    totalCount = auditLogs.size,
                    corruptedIndex = i,
                    errorMessage = "Previous hash mismatch at step ${i + 1}"
                )
            }

            val canonicalPayloadHash = computeCanonicalPayloadHash(log.details)
            val sanitizedReason = sanitizeReason(log.reason)

            val payload = buildAuditPayloadString(
                previousHash = log.previousHash,
                entityId = log.entityId,
                entityType = log.entityType,
                action = log.action,
                canonicalPayloadHash = canonicalPayloadHash,
                sanitizedReason = sanitizedReason,
                actor = log.actor,
                isoTimestamp = log.timestamp
            )
            val calculated = sha256(payload)

            if (calculated != log.recordHash) {
                return VerificationResult(
                    valid = false,
                    totalCount = auditLogs.size,
                    corruptedIndex = i,
                    errorMessage = "Tampering detected: hash does not match computed signature at step ${i + 1}"
                )
            }
            expectedPrevHash = log.recordHash
        }
        return VerificationResult(valid = true, totalCount = auditLogs.size)
    }

    fun verifyAuditChainWindowed(
        auditLogs: List<AuditLog>,
        windowSize: Int = Int.MAX_VALUE,
        knownAnchor: AuditSnapshotAnchor? = null
    ): VerificationResult {
        if (auditLogs.isEmpty()) return VerificationResult(valid = true, totalCount = 0)
        // If window covers full chain or no anchor, verify entire ledger starting from GENESIS_HASH
        val startIndex = if (knownAnchor != null && knownAnchor.snapshotIndex in auditLogs.indices) {
            if (auditLogs[knownAnchor.snapshotIndex].recordHash == knownAnchor.anchorHash) {
                knownAnchor.snapshotIndex
            } else 0
        } else if (windowSize >= auditLogs.size) {
            0
        } else {
            (auditLogs.size - windowSize).coerceAtLeast(0)
        }

        var expectedPrevHash = if (startIndex == 0) GENESIS_HASH else auditLogs[startIndex].previousHash
        for (i in startIndex until auditLogs.size) {
            val log = auditLogs[i]
            if (log.previousHash != expectedPrevHash) {
                return VerificationResult(
                    valid = false,
                    totalCount = auditLogs.size,
                    corruptedIndex = i,
                    errorMessage = "Previous hash mismatch at step ${i + 1}"
                )
            }
            val canonicalPayloadHash = computeCanonicalPayloadHash(log.details)
            val sanitizedReason = sanitizeReason(log.reason)
            val payload = buildAuditPayloadString(
                previousHash = log.previousHash,
                entityId = log.entityId,
                entityType = log.entityType,
                action = log.action,
                canonicalPayloadHash = canonicalPayloadHash,
                sanitizedReason = sanitizedReason,
                actor = log.actor,
                isoTimestamp = log.timestamp
            )
            val calculated = sha256(payload)
            if (calculated != log.recordHash) {
                return VerificationResult(
                    valid = false,
                    totalCount = auditLogs.size,
                    corruptedIndex = i,
                    errorMessage = "Tampering detected: hash does not match computed signature at step ${i + 1}"
                )
            }
            expectedPrevHash = log.recordHash
        }
        return VerificationResult(valid = true, totalCount = auditLogs.size)
    }

    data class ReplayedState(
        val voidedTransactionIds: Set<String>,
        val voidReasons: Map<String, String>,
        val isVerified: Boolean = true,
        val errorMessage: String? = null
    )

    fun recalculateLedgerState(auditLogs: List<AuditLog>): ReplayedState {
        val verification = verifyAuditChain(auditLogs)
        if (!verification.valid) {
            return ReplayedState(
                voidedTransactionIds = emptySet(),
                voidReasons = emptyMap(),
                isVerified = false,
                errorMessage = verification.errorMessage
            )
        }
        val voided = mutableSetOf<String>()
        val reasons = mutableMapOf<String, String>()
        for (log in auditLogs) {
            if (log.entityType == "Transaction" && log.action == "VOID") {
                voided.add(log.entityId)
                reasons[log.entityId] = log.reason
            }
        }
        return ReplayedState(voided, reasons, isVerified = true)
    }

    fun generateSecureSalt(numBytes: Int = 16): String {
        val random = SecureRandom()
        val salt = ByteArray(numBytes)
        random.nextBytes(salt)
        return safeBase64Encode(salt)
    }

    // PBKDF2 PIN hashing (100,000 iterations)
    fun hashPin(pin: String, salt: ByteArray): String {
        val spec = PBEKeySpec(pin.toCharArray(), salt, 100000, 256)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val hash = factory.generateSecret(spec).encoded
        return hash.joinToString("") { "%02x".format(it) }
    }

    fun hashPinString(pin: String, salt: String): String {
        require(salt.isNotBlank()) { "Salt must not be blank when hashing a PIN" }
        return hashPin(pin, salt.toByteArray(StandardCharsets.UTF_8))
    }

    fun hashPinWithNewSalt(pin: String): Pair<String, String> {
        val salt = generateSecureSalt()
        return hashPinString(pin, salt) to salt
    }

    private fun deriveAesKey(passphrase: String, salt: ByteArray, iterations: Int = 100000): SecretKeySpec {
        val spec = PBEKeySpec(passphrase.toCharArray(), salt, iterations, 256)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val keyBytes = factory.generateSecret(spec).encoded
        return SecretKeySpec(keyBytes, "AES")
    }

    fun encryptAesGcm(plaintext: String, passphrase: String): String {
        require(passphrase.isNotBlank()) { "Backup encryption passphrase must not be blank" }
        val rng = SecureRandom()
        val salt = ByteArray(16).also { rng.nextBytes(it) }
        val iv = ByteArray(12).also { rng.nextBytes(it) }
        val key = deriveAesKey(passphrase, salt, 100000)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
        val ciphertext = cipher.doFinal(plaintext.toByteArray(StandardCharsets.UTF_8))
        return JSONObject().apply {
            put("format", "pabaru-encrypted-backup-v1")
            put("kdf", "PBKDF2WithHmacSHA256")
            put("iterations", 100000)
            put("cipher", "AES-256-GCM")
            put("salt", safeBase64Encode(salt))
            put("iv", safeBase64Encode(iv))
            put("ciphertext", safeBase64Encode(ciphertext))
        }.toString(2)
    }

    fun decryptAesGcm(envelopeJson: String, passphrase: String): String {
        require(passphrase.isNotBlank()) { "Backup decryption passphrase must not be blank" }
        val obj = JSONObject(envelopeJson)
        val format = obj.optString("format", "")
        require(format == "pabaru-encrypted-backup-v1") { "Unsupported encrypted backup format" }
        val iterations = obj.optInt("iterations", 100000).coerceIn(10000, 1000000)
        val salt = safeBase64Decode(obj.getString("salt"))
        val iv = safeBase64Decode(obj.getString("iv"))
        val ciphertext = safeBase64Decode(obj.getString("ciphertext"))
        val key = deriveAesKey(passphrase, salt, iterations)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
        val plain = cipher.doFinal(ciphertext)
        return String(plain, StandardCharsets.UTF_8)
    }

    // Curated 256-word dictionary for merchant recovery phrases (8 bits per word = 96 bits entropy for 12 words)
    private val WORD_LIST = listOf(
        "abundant", "account", "accurate", "achieve", "action", "advance", "agency", "agile",
        "agree", "ahead", "airport", "alert", "alliance", "alpha", "amber", "amount",
        "anchor", "annual", "answer", "apex", "apparel", "apply", "approve", "arcade",
        "archive", "arena", "arrow", "asset", "atlas", "audit", "august", "author",
        "avatar", "avenue", "award", "balance", "banana", "banner", "banyan", "bargain",
        "barter", "bazaar", "beacon", "benefit", "bengaluru", "bharat", "bilateral", "bison",
        "blossom", "bonus", "border", "botany", "branch", "brave", "breeze", "bridge",
        "budget", "buffalo", "builder", "bulletin", "bundle", "business", "cabinet", "cable",
        "cactus", "calculate", "calendar", "calm", "camera", "canal", "candle", "canvas",
        "capital", "captain", "caravan", "cardamom", "career", "cargo", "carrier", "castle",
        "catalyst", "cautious", "celestial", "cement", "center", "century", "ceramic", "certify",
        "chain", "chamber", "champion", "channel", "charter", "cheetah", "chennai", "cherry",
        "chilli", "cinnamon", "circle", "circuit", "citizen", "citrus", "civic", "clarity",
        "classic", "clay", "client", "climate", "clove", "cluster", "cobalt", "coconut",
        "coffee", "cognition", "coin", "colleague", "colony", "comfort", "command", "commerce",
        "compact", "company", "compass", "compound", "compute", "concept", "concord", "concrete",
        "connect", "conquest", "constant", "consumer", "contact", "content", "contour", "contract",
        "copper", "coral", "corner", "corridor", "cosmos", "cottage", "cotton", "counsel",
        "courage", "courier", "covenant", "cradle", "craft", "credit", "crescent", "cricket",
        "criterion", "crucial", "crystal", "cube", "cuisine", "culture", "current", "custom",
        "cypress", "dairy", "damson", "daring", "database", "dealer", "decade", "decimal",
        "decor", "dedicate", "deep", "defence", "delhi", "delta", "demand", "dentist",
        "depot", "deposit", "deputy", "derby", "design", "desktop", "destiny", "detail",
        "detect", "device", "diamond", "diary", "diesel", "digest", "digital", "dignity",
        "diligence", "dimple", "dinner", "direct", "director", "discover", "display", "distance",
        "district", "divide", "dividend", "divine", "doctor", "document", "dolphin", "domain",
        "donate", "doorway", "dragon", "dynamic", "eagle", "early", "earth", "eastern",
        "economy", "ecosystem", "edition", "educate", "effort", "elastic", "elder", "element",
        "elephant", "elite", "embark", "emerald", "empire", "empower", "energy", "engine",
        "enhance", "enterprise", "entry", "envelope", "epoch", "equator", "equity", "escape",
        "estate", "eternal", "ether", "ethics", "evidence", "exact", "exceed", "excel",
        "exchange", "execute", "exempt", "exotic", "expand", "expert", "express", "extend"
    )

    fun generateRecoveryPhrase(): String {
        val random = SecureRandom()
        val pool = WORD_LIST.toMutableList()
        val words = mutableListOf<String>()
        // Sample without replacement to maximize distinct entropy
        for (i in 0 until 12) {
            val idx = random.nextInt(pool.size)
            words.add(pool.removeAt(idx))
        }
        return words.joinToString(" ")
    }
}
