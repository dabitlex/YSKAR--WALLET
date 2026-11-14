package com.yskar.wallet.kern

import java.io.ByteArrayOutputStream

/**
 * Deterministische Serialisierung. Little-Endian, feste Breiten.
 *
 * Portiert aus src/kern/codec.ts (MIT, The YSKAR developers).
 *
 * Zu u64: Die TypeScript-Fassung rechnet mit bigint und laesst Werte bis
 * 2^64-1 zu. Hier steht Long, also bis 2^63-1. Das ist fuer diese Kette
 * ausreichend und wird geprueft statt verschwiegen: Die Gesamtmenge ist auf
 * 21.000.000 * 10^8 = 2,1e15 begrenzt, Gebuehren und Nonces liegen weit
 * darunter. Ein Wert mit gesetztem obersten Bit kann in einer gueltigen
 * YSKAR-Transaktion nicht vorkommen - taucht er auf, ist die Eingabe kaputt
 * und der Leser wirft, statt eine negative Zahl weiterzugeben.
 */
class Writer {
    private val out = ByteArrayOutputStream(256)

    fun u8(v: Int): Writer {
        out.write(v and 0xff)
        return this
    }

    fun u16(v: Int): Writer {
        out.write(v and 0xff)
        out.write((v ushr 8) and 0xff)
        return this
    }

    fun u32(v: Int): Writer {
        out.write(v and 0xff)
        out.write((v ushr 8) and 0xff)
        out.write((v ushr 16) and 0xff)
        out.write((v ushr 24) and 0xff)
        return this
    }

    /** Auch fuer u32-Felder, die aus einem Long kommen (Hoehen, Zeitstempel). */
    fun u32(v: Long): Writer {
        require(v >= 0 && v <= 0xffff_ffffL) { "u32 ausserhalb des Bereichs: $v" }
        return u32(v.toInt())
    }

    fun u64(v: Long): Writer {
        require(v >= 0) { "u64 ausserhalb des Bereichs: $v" }
        var x = v
        for (i in 0 until 8) {
            out.write((x and 0xff).toInt())
            x = x ushr 8
        }
        return this
    }

    fun bytes(b: ByteArray, expect: Int? = null): Writer {
        if (expect != null && b.size != expect) {
            throw IllegalArgumentException("erwartet $expect Byte, erhalten ${b.size}")
        }
        out.write(b, 0, b.size)
        return this
    }

    val size: Int get() = out.size()

    fun finish(): ByteArray = out.toByteArray()
}

class Reader(private val buf: ByteArray) {
    private var o = 0

    private fun need(n: Int) {
        if (o + n > buf.size) throw IllegalArgumentException("Daten zu kurz")
    }

    fun u8(): Int {
        need(1)
        return buf[o++].toInt() and 0xff
    }

    fun u16(): Int {
        need(2)
        val v = (buf[o].toInt() and 0xff) or ((buf[o + 1].toInt() and 0xff) shl 8)
        o += 2
        return v
    }

    fun u32(): Long {
        need(4)
        var v = 0L
        for (i in 0 until 4) v = v or ((buf[o + i].toLong() and 0xff) shl (8 * i))
        o += 4
        return v
    }

    fun u64(): Long {
        need(8)
        var v = 0L
        for (i in 0 until 8) v = v or ((buf[o + i].toLong() and 0xff) shl (8 * i))
        o += 8
        if (v < 0) {
            throw IllegalArgumentException(
                "u64 mit gesetztem obersten Bit - in dieser Kette unmoeglich"
            )
        }
        return v
    }

    fun bytes(n: Int): ByteArray {
        need(n)
        val v = buf.copyOfRange(o, o + n)
        o += n
        return v
    }

    val rest: Int get() = buf.size - o
    val offset: Int get() = o
}

object Hex {
    private val DIGITS = "0123456789abcdef".toCharArray()

    fun toHex(b: ByteArray): String {
        val s = CharArray(b.size * 2)
        for (i in b.indices) {
            val v = b[i].toInt() and 0xff
            s[i * 2] = DIGITS[v ushr 4]
            s[i * 2 + 1] = DIGITS[v and 0x0f]
        }
        return String(s)
    }

    /** Nimmt auch die Postgres-Schreibweise `\xabcd` und `0x…` entgegen. */
    fun fromHex(s: String): ByteArray {
        val c = when {
            s.startsWith("\\x") -> s.substring(2)
            s.startsWith("0x") || s.startsWith("0X") -> s.substring(2)
            else -> s
        }
        if (c.length % 2 != 0) throw IllegalArgumentException("Hex-Laenge ungerade")
        val out = ByteArray(c.length / 2)
        for (i in out.indices) {
            val hi = Character.digit(c[i * 2], 16)
            val lo = Character.digit(c[i * 2 + 1], 16)
            if (hi < 0 || lo < 0) throw IllegalArgumentException("kein Hex: $s")
            out[i] = ((hi shl 4) or lo).toByte()
        }
        return out
    }
}
