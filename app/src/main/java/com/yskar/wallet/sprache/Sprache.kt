package com.yskar.wallet.sprache

import java.util.Locale

/**
 * Die Sprachen der App.
 *
 * Die Sprache ist eine Einstellung DER APP, nicht des Telefons: Sie steht
 * unter "Einstellungen" und gilt sofort, ohne Neustart. Solange niemand
 * gewaehlt hat, richtet sie sich nach der Sprache des Geraets
 * ([fuerSystem]).
 *
 * [locale] bestimmt, wie Zahlen und Daten geschrieben werden:
 * "1.284,57" und "11.11.2026" auf Deutsch, "1,284.57" und "11 Nov 2026"
 * auf Englisch. Gerechnet wird davon unberuehrt in ganzen Einheiten
 * (REGELN.md, Regel 5 und 11).
 *
 * Reines Kotlin, ohne Android - wie alles in diesem Paket. So laufen die
 * Tests der Texte auf der JVM.
 */
enum class Sprache(val kennung: String, val eigenname: String, val locale: Locale) {
    DEUTSCH("de", "Deutsch", Locale.GERMANY),
    ENGLISCH("en", "English", Locale.UK);

    companion object {
        /** Die gespeicherte Wahl; null, wenn nichts oder Unbekanntes gespeichert ist. */
        fun ausKennung(kennung: String?): Sprache? = entries.firstOrNull { it.kennung == kennung }

        /**
         * Die Sprache fuer ein Geraet, auf dem noch niemand gewaehlt hat:
         * Deutsch, wenn das Geraet deutsch spricht, sonst Englisch.
         */
        fun fuerSystem(geraet: Locale): Sprache =
            if (geraet.language == Locale.GERMAN.language) DEUTSCH else ENGLISCH
    }
}
