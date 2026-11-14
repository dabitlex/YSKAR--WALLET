package com.yskar.wallet.netz

import com.yskar.wallet.kern.Params
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

/**
 * Notiz einer Zahlung.
 *
 * Portiert aus src/wallet/notiz.ts (MIT, The YSKAR developers).
 *
 * Die Kette erlaubt 32 BYTE, nicht 32 Zeichen: "ae" als Umlaut belegt zwei,
 * ein Emoji vier. Gekuerzt wird deshalb nach Bytes - und nie mitten in
 * einem Zeichen, sonst stuende am Ende ein kaputtes.
 */
object Notiz {

    const val BYTES = Params.MAX_MEMO_BYTES

    fun bytes(text: String): Int = text.toByteArray(Charsets.UTF_8).size

    /** Auf hoechstens [max] Byte kuerzen, an einer Zeichengrenze. */
    fun kuerzen(text: String, max: Int = BYTES): String {
        val sb = StringBuilder()
        var gebraucht = 0
        // codePoints, nicht Zeichen: Ein Emoji besteht aus zwei char-Werten,
        // und dazwischen zu trennen erzeugt kaputten Text.
        var i = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            val zeichen = String(Character.toChars(cp))
            val n = zeichen.toByteArray(Charsets.UTF_8).size
            if (gebraucht + n > max) break
            sb.append(zeichen)
            gebraucht += n
            i += Character.charCount(cp)
        }
        return sb.toString()
    }

    /**
     * Notiz aus den Bytes der Kette (Hex).
     *
     * Leer, wenn es kein gueltiger Text ist - eine Notiz sind beliebige
     * Bytes, nicht jede ist lesbar. Steuerzeichen haben in einer Zeile
     * nichts verloren und werden zu Leerzeichen.
     */
    fun ausHex(hex: String?): String {
        if (hex.isNullOrEmpty()) return ""
        val roh = try {
            com.yskar.wallet.kern.Hex.fromHex(hex)
        } catch (e: IllegalArgumentException) {
            return ""
        }

        val dekoder = Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)

        val text = try {
            dekoder.decode(ByteBuffer.wrap(roh)).toString()
        } catch (e: Exception) {
            return ""
        }

        return text.map { if (it.code < 0x20 || it.code == 0x7f) ' ' else it }
            .joinToString("")
            .trim()
    }

    /** Die Bytes, wie sie in die Transaktion gehen. */
    fun zuBytes(text: String): ByteArray = kuerzen(text).toByteArray(Charsets.UTF_8)
}
