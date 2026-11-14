package com.yskar.wallet.netz

import com.yskar.wallet.kern.Params
import java.text.NumberFormat
import java.util.Locale

/**
 * Betraege fuer die Anzeige.
 *
 * Portiert aus src/wallet/betrag.ts (MIT, The YSKAR developers).
 *
 * GERECHNET WIRD IN GANZEN EINHEITEN, NIE UEBER GLEITKOMMA. 0,1 + 0,2 ist
 * als Double nicht 0,3, und bei Geld ist das kein Schoenheitsfehler.
 *
 * Exakt bis zur letzten Stelle der Kette (8 Nachkommastellen). Nullen am
 * Ende fallen weg, mindestens zwei Stellen bleiben: "5,00", "0,87512345",
 * "800.000,000045".
 *
 * NIE GERUNDET, immer abgeschnitten: Aufrunden zeigte mehr, als da ist -
 * 0,999 YSR erschienen als "1,00".
 */
object Betrag {

    const val DECIMALS = Params.DECIMALS

    /** Eine ganze Einheit: 10^8. */
    private const val BASIS = 100_000_000L

    /**
     * Betrag exakt, mindestens [min] Nachkommastellen.
     *
     * Fuer den Pruefschritt beim Senden und fuer Gebuehren: Dort darf nichts
     * abgeschnitten sein - "Gesamt" muss die Gebuehr zeigen, auch wenn sie
     * in der sechsten Nachkommastelle liegt.
     */
    fun genau(einheiten: Long, locale: Locale = Locale.GERMAN, min: Int = 2): String {
        val negativ = einheiten < 0
        val abs = if (negativ) -einheiten else einheiten

        var bruch = (abs % BASIS).toString().padStart(DECIMALS, '0').trimEnd('0')
        if (bruch.length < min) bruch = bruch.padEnd(min, '0')

        val ganz = NumberFormat.getIntegerInstance(locale).format(abs / BASIS)
        val text = if (bruch.isEmpty()) ganz else ganz + dezimalZeichen(locale) + bruch
        return if (negativ) "-$text" else text
    }

    /**
     * Genau [stellen] Nachkommastellen, abgeschnitten - fuer Anzeigen, deren
     * Breite nicht wackeln darf.
     */
    fun stellen(einheiten: Long, stellen: Int, locale: Locale = Locale.GERMAN): String {
        val negativ = einheiten < 0
        val abs = if (negativ) -einheiten else einheiten

        val n = stellen.coerceIn(0, DECIMALS)
        val bruch = (abs % BASIS).toString().padStart(DECIMALS, '0').substring(0, n)
        val ganz = NumberFormat.getIntegerInstance(locale).format(abs / BASIS)
        val text = if (bruch.isEmpty()) ganz else ganz + dezimalZeichen(locale) + bruch
        return if (negativ) "-$text" else text
    }

    /**
     * Fuer die Uebersicht, wie der Nutzer es eingestellt hat: 8 = exakt,
     * 4 oder 2 = genau so viele Stellen, abgeschnitten.
     */
    fun anzeige(einheiten: Long, stellen: Int, locale: Locale = Locale.GERMAN): String =
        if (stellen >= DECIMALS) genau(einheiten, locale) else stellen(einheiten, stellen, locale)

    /** Mit Einheit dahinter. */
    fun mitEinheit(einheiten: Long, locale: Locale = Locale.GERMAN, min: Int = 2): String =
        genau(einheiten, locale, min) + " YSR"

    fun dezimalZeichen(locale: Locale = Locale.GERMAN): Char =
        java.text.DecimalFormatSymbols.getInstance(locale).decimalSeparator

    /**
     * Eingabe ("25,5" oder "25.5") in ganze Einheiten.
     *
     * Unsinn und Negatives ergeben 0. Exakt: Ziffern werden gezaehlt, nicht
     * ueber Gleitkomma gerechnet. Mehr Nachkommastellen als die Kette kennt
     * werden abgeschnitten, nicht gerundet.
     */
    fun ausEingabe(text: String): Long {
        val m = Regex("^(\\d{0,12})(?:[.,](\\d{0,18}))?$").find(text.trim()) ?: return 0
        val ganzText = m.groupValues[1]
        val bruchText = m.groupValues[2]
        if (ganzText.isEmpty() && bruchText.isEmpty()) return 0

        val ganz = if (ganzText.isEmpty()) 0L else ganzText.toLong()
        val bruch = bruchText.take(DECIMALS).padEnd(DECIMALS, '0')

        // 12 Vorkommastellen passen mit 10^8 nicht mehr sicher in Long;
        // die Gesamtmenge der Kette liegt bei 21 Millionen.
        if (ganz > Long.MAX_VALUE / BASIS) return 0
        return ganz * BASIS + bruch.toLong()
    }

    /** Einheiten zurueck in einen Eingabetext, ohne Nullen am Ende. */
    fun alsEingabe(einheiten: Long, locale: Locale = Locale.GERMAN, stellen: Int = 4): String {
        if (einheiten <= 0) return ""
        val ganz = einheiten / BASIS
        val bruch = (einheiten % BASIS).toString()
            .padStart(DECIMALS, '0')
            .substring(0, stellen.coerceIn(0, DECIMALS))
            .trimEnd('0')
        return if (bruch.isEmpty()) ganz.toString() else "$ganz${dezimalZeichen(locale)}$bruch"
    }
}
