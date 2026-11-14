package com.yskar.wallet.kern

/**
 * Adressen.
 *
 * Portiert aus src/kern/address.ts (MIT, The YSKAR developers).
 *
 * Version 0 (seit Genesis):
 *   programm = sha256(pubkey)[0..20]
 *   text     = bech32m(hrp='ysr', programm)       42 Zeichen, KEIN Versionszeichen
 *   roh      = programm                            20 Byte
 *
 * Version 1 (Konsensfassung 6):
 *   programm = sha256(script)                      32 Byte
 *   text     = bech32m(hrp, [1] | programm)        63 Zeichen, beginnt mit "<hrp>1p"
 *   roh      = 0x01 | programm                     33 Byte
 *
 * ACHTUNG, haeufige Fehlerquelle: Alt und neu unterscheiden sich NUR UEBER
 * DIE LAENGE, nie ueber das Praefix. Eine Adresse der Version 0 beginnt
 * ebenfalls mit "ysr1p", wenn die ersten fuenf Bits ihres Hashes 00001 sind.
 * Wer das erste Zeichen auswertet, baut einen Fehler.
 *
 * Beide Rohformen koennen nicht kollidieren: 20 gegen 33 Byte.
 */
object Address {

    const val V0 = 0
    const val V1 = 1
    const val SCRIPT_PROGRAM_BYTES = 32
    const val MAX_VERSION = 15

    data class Versioned(val version: Int, val program: ByteArray) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Versioned) return false
            return version == other.version && program.contentEquals(other.program)
        }

        override fun hashCode(): Int = 31 * version + program.contentHashCode()
    }

    /** Nullkonto - Empfaenger fuer verbrannte Betraege, hat keinen Schluessel. */
    val ZERO: ByteArray get() = ByteArray(Params.ADDRESS_BYTES)

    /** Programmlaenge je Version; null fuer Versionen, die es (noch) nicht gibt. */
    fun programLength(version: Int): Int? = when (version) {
        V0 -> Params.ADDRESS_BYTES
        V1 -> SCRIPT_PROGRAM_BYTES
        else -> null
    }

    fun fromPublicKey(pubkey: ByteArray): ByteArray {
        require(pubkey.size == 32) { "pubkey muss 32 Byte sein, ist ${pubkey.size}" }
        return Hashes.sha256(pubkey).copyOfRange(0, Params.ADDRESS_BYTES)
    }

    /** Programm einer Version-1-Adresse: der Hash des Skripts. */
    fun programFromScript(script: ByteArray): ByteArray = Hashes.sha256(script)

    // ------------------------------------------------------------- Rohform

    fun raw(version: Int, program: ByteArray): ByteArray {
        val len = programLength(version)
            ?: throw IllegalArgumentException("unbekannte Adressversion $version")
        require(program.size == len) {
            "Programm der Version $version muss $len Byte sein, ist ${program.size}"
        }
        if (version == V0) return program
        val out = ByteArray(1 + len)
        out[0] = version.toByte()
        program.copyInto(out, 1)
        return out
    }

    /** Version und Programm aus der Rohform. Wirft bei unbekannter Form. */
    fun splitRaw(raw: ByteArray): Versioned {
        if (raw.size == Params.ADDRESS_BYTES) return Versioned(V0, raw)
        if (raw.size == 1 + SCRIPT_PROGRAM_BYTES && (raw[0].toInt() and 0xff) == V1) {
            return Versioned(V1, raw.copyOfRange(1, raw.size))
        }
        throw IllegalArgumentException("keine gueltige Rohadresse (${raw.size} Byte)")
    }

    fun isRaw(raw: ByteArray): Boolean =
        runCatching { splitRaw(raw) }.isSuccess

    fun rawVersion(raw: ByteArray): Int = splitRaw(raw).version

    fun rawFromScript(script: ByteArray): ByteArray =
        raw(V1, programFromScript(script))

    // ------------------------------------------------------------- Leitung

    /**
     * Versioniertes Adressfeld auf der Leitung: `u8 version | programm`.
     * Ohne Laengenbyte - der Leser kennt die Laengen der Versionen 0 und 1,
     * jede andere Version ist nicht lesbar.
     */
    fun write(w: Writer, raw: ByteArray): Writer {
        val (version, program) = splitRaw(raw)
        return w.u8(version).bytes(program)
    }

    fun read(r: Reader): ByteArray {
        val version = r.u8()
        val len = programLength(version)
            ?: throw IllegalArgumentException("unknown_address_version:$version")
        return raw(version, r.bytes(len))
    }

    /** Groesse des Feldes auf der Leitung. */
    fun wireBytes(raw: ByteArray): Int = 1 + splitRaw(raw).program.size

    /** Das Feld, wie es auf der Leitung steht: `u8 version | programm`. */
    fun wire(raw: ByteArray): ByteArray {
        val (version, program) = splitRaw(raw)
        val out = ByteArray(1 + program.size)
        out[0] = version.toByte()
        program.copyInto(out, 1)
        return out
    }

    /**
     * Vergleich zweier Rohadressen in der Reihenfolge ihrer LEITUNGSFORM:
     * So sortiert eine Sammelzahlung ihre Empfaenger. Die Rohform taugt
     * dafuer nicht, weil Version 0 kein Versionsbyte traegt.
     */
    fun compareWire(a: ByteArray, b: ByteArray): Int {
        val x = wire(a)
        val y = wire(b)
        val n = minOf(x.size, y.size)
        for (i in 0 until n) {
            val d = (x[i].toInt() and 0xff) - (y[i].toInt() and 0xff)
            if (d != 0) return d
        }
        return x.size - y.size
    }

    // ------------------------------------------------------------- Textform

    fun encode(raw: ByteArray, hrp: String = Params.ADDRESS_HRP): String {
        val (version, program) = splitRaw(raw)
        val words = Bech32m.toWords(program)
        return if (version == V0) {
            Bech32m.encode(hrp, words)
        } else {
            Bech32m.encode(hrp, intArrayOf(version) + words)
        }
    }

    /**
     * Textform lesen. Liefert die Rohform; wirft bei falschem Praefix,
     * falscher Laenge oder einer Version, die es nicht gibt.
     */
    fun decode(text: String, hrp: String = Params.ADDRESS_HRP): ByteArray {
        val (version, program) = decodeAny(text, hrp)
        val len = programLength(version)
            ?: throw IllegalArgumentException("unbekannte Adressversion $version")
        require(program.size == len) {
            "Adresse der Version $version hat ${program.size} Byte, erwartet $len"
        }
        return raw(version, program)
    }

    /**
     * Textform lesen, ohne die Version zu bewerten - fuer das reine ANZEIGEN
     * einer Adresse aus einer spaeteren Fassung. Version 0 erkennt sich an
     * genau 32 Datenzeichen; alles andere ist `[v] | programm` mit v = 1..15.
     */
    fun decodeAny(text: String, hrp: String = Params.ADDRESS_HRP): Versioned {
        val d = Bech32m.decode(text, 90)
        if (d.hrp != hrp) {
            throw IllegalArgumentException("falsches Praefix: ${d.hrp}, erwartet $hrp")
        }
        if (d.words.size == 32) {
            return Versioned(V0, Bech32m.fromWords(d.words))
        }
        if (d.words.isEmpty()) throw IllegalArgumentException("Datenteil leer")
        val version = d.words[0]
        if (version < 1 || version > MAX_VERSION) {
            throw IllegalArgumentException("unzulaessige Adressversion $version")
        }
        // fromWords prueft die Auffuellung und wirft sonst.
        val program = Bech32m.fromWords(d.words.copyOfRange(1, d.words.size))
        return Versioned(version, program)
    }

    fun isValid(text: String, hrp: String = Params.ADDRESS_HRP): Boolean =
        runCatching { decode(text, hrp) }.isSuccess

    fun forPublicKey(pubkey: ByteArray, hrp: String = Params.ADDRESS_HRP): String =
        encode(fromPublicKey(pubkey), hrp)

    fun forScript(script: ByteArray, hrp: String = Params.ADDRESS_HRP): String =
        encode(rawFromScript(script), hrp)

    /**
     * Kann die Coinbase an diese Rohadresse auszahlen? Nur an Version 0:
     * Jeder Coinbase-Empfaenger hat auf der Leitung feste 20 Byte.
     */
    fun istAuszahlungsAdresse(raw: ByteArray): Boolean =
        raw.size == Params.ADDRESS_BYTES

    const val REGELKONTO_KEIN_MINING =
        "Mining zahlt nur an normale Wallet-Adressen aus (ysr1…, 42 Zeichen). " +
            "Gemeinsame Konten (ysr1p…) koennen noch keinen Blockertrag empfangen."
}
