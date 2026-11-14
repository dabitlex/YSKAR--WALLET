package com.yskar.wallet.kern

/**
 * bech32m nach BIP-350.
 *
 * Ersetzt @scure/base aus der TypeScript-Wallet. bech32m statt bech32, weil
 * es den bekannten Schwachpunkt von bech32 bei Pruefsummen behebt und
 * Tippfehler zuverlaessig erkennt.
 *
 * Diese Umsetzung ist absichtlich streng:
 *   - gemischte Gross- und Kleinschreibung wird abgelehnt (BIP-173),
 *   - beim Zurueckrechnen von 5 auf 8 Bit muss die Auffuellung kuerzer als
 *     5 Bit und komplett Null sein,
 *   - die Laengengrenze ist ein Parameter und standardmaessig 90.
 *
 * Wer hier lockerer liest als der Knoten, zeigt Adressen als gueltig an, die
 * es nicht sind. Darum keine Nachsicht.
 */
object Bech32m {

    private const val CHARSET = "qpzry9x8gf2tvdw0s3jn54khce6mua7l"
    private const val BECH32M_CONST = 0x2bc830a3

    private val GENERATOR = intArrayOf(
        0x3b6a57b2, 0x26508e6d, 0x1ea119fa, 0x3d4233dd, 0x2a1462b3
    )

    data class Decoded(val hrp: String, val words: IntArray) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Decoded) return false
            return hrp == other.hrp && words.contentEquals(other.words)
        }

        override fun hashCode(): Int = 31 * hrp.hashCode() + words.contentHashCode()
    }

    private fun polymod(values: IntArray): Int {
        var chk = 1
        for (v in values) {
            val top = chk ushr 25
            chk = ((chk and 0x1ff_ffff) shl 5) xor v
            for (i in 0 until 5) {
                if (((top ushr i) and 1) != 0) chk = chk xor GENERATOR[i]
            }
        }
        return chk
    }

    private fun hrpExpand(hrp: String): IntArray {
        val out = IntArray(hrp.length * 2 + 1)
        for (i in hrp.indices) out[i] = hrp[i].code ushr 5
        out[hrp.length] = 0
        for (i in hrp.indices) out[hrp.length + 1 + i] = hrp[i].code and 31
        return out
    }

    private fun checksum(hrp: String, words: IntArray): IntArray {
        val values = hrpExpand(hrp) + words + IntArray(6)
        val mod = polymod(values) xor BECH32M_CONST
        return IntArray(6) { (mod ushr (5 * (5 - it))) and 31 }
    }

    /** 5-Bit-Gruppen in eine Textform. */
    fun encode(hrp: String, words: IntArray, limit: Int = 90): String {
        require(hrp.isNotEmpty()) { "hrp ist leer" }
        for (w in words) require(w in 0..31) { "Wort ausserhalb 0..31: $w" }

        val sb = StringBuilder(hrp.length + 1 + words.size + 6)
        sb.append(hrp.lowercase()).append('1')
        for (w in words) sb.append(CHARSET[w])
        for (w in checksum(hrp.lowercase(), words)) sb.append(CHARSET[w])

        val s = sb.toString()
        if (s.length > limit) throw IllegalArgumentException("zu lang: ${s.length} > $limit")
        return s
    }

    /** Textform lesen. Liefert hrp und die 5-Bit-Gruppen ohne Pruefsumme. */
    fun decode(text: String, limit: Int = 90): Decoded {
        if (text.length > limit) throw IllegalArgumentException("zu lang: ${text.length} > $limit")

        var hatKlein = false
        var hatGross = false
        for (ch in text) {
            if (ch.code < 33 || ch.code > 126) throw IllegalArgumentException("Zeichen unzulaessig")
            if (ch in 'a'..'z') hatKlein = true
            if (ch in 'A'..'Z') hatGross = true
        }
        if (hatKlein && hatGross) throw IllegalArgumentException("gemischte Schreibweise")

        val s = text.lowercase()
        val trenn = s.lastIndexOf('1')
        if (trenn < 1) throw IllegalArgumentException("kein Trennzeichen")
        if (trenn + 7 > s.length) throw IllegalArgumentException("Datenteil zu kurz")

        val hrp = s.substring(0, trenn)
        val datenTeil = s.substring(trenn + 1)

        val werte = IntArray(datenTeil.length)
        for (i in datenTeil.indices) {
            val idx = CHARSET.indexOf(datenTeil[i])
            if (idx < 0) throw IllegalArgumentException("Zeichen nicht im Alphabet: ${datenTeil[i]}")
            werte[i] = idx
        }

        if (polymod(hrpExpand(hrp) + werte) != BECH32M_CONST) {
            throw IllegalArgumentException("Pruefsumme falsch")
        }

        return Decoded(hrp, werte.copyOfRange(0, werte.size - 6))
    }

    /**
     * Bitbreiten umrechnen.
     *
     * Beim Aufrunden (8 -> 5) wird mit Nullen aufgefuellt. Beim Abrunden
     * (5 -> 8) darf nur eine Auffuellung von weniger als 8 Bit uebrig
     * bleiben, und die muss Null sein - sonst waere dieselbe Bytefolge
     * mehrfach schreibbar.
     */
    private fun convertBits(data: IntArray, from: Int, to: Int, pad: Boolean): IntArray {
        var acc = 0
        var bits = 0
        val out = ArrayList<Int>(data.size * from / to + 1)
        val maxv = (1 shl to) - 1

        for (v in data) {
            if (v < 0 || (v ushr from) != 0) throw IllegalArgumentException("Wert zu gross: $v")
            acc = (acc shl from) or v
            bits += from
            while (bits >= to) {
                bits -= to
                out.add((acc ushr bits) and maxv)
            }
        }

        if (pad) {
            if (bits > 0) out.add((acc shl (to - bits)) and maxv)
        } else {
            if (bits >= from) throw IllegalArgumentException("Auffuellung zu lang")
            if (((acc shl (to - bits)) and maxv) != 0) {
                throw IllegalArgumentException("Auffuellung nicht Null")
            }
        }

        return out.toIntArray()
    }

    fun toWords(bytes: ByteArray): IntArray =
        convertBits(IntArray(bytes.size) { bytes[it].toInt() and 0xff }, 8, 5, true)

    fun fromWords(words: IntArray): ByteArray {
        val out = convertBits(words, 5, 8, false)
        return ByteArray(out.size) { out[it].toByte() }
    }
}
