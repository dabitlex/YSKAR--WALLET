package com.yskar.wallet.ui

/**
 * Die zwoelf Felder fuer die Merkwoerter beim Wiederherstellen - was in
 * ihnen steht, wenn jemand tippt oder einfuegt.
 *
 * REIN, ohne Compose und ohne Android (`MerkwortfelderTest`). Hier steht
 * kein Wort einer echten Wallet und es wird keines geprueft: Ob die Woerter
 * stimmen, entscheidet wie bisher `WalletModel.woerterUebernehmen` mit
 * `Bip39`. Diese Datei verteilt nur Zeichen auf Felder.
 *
 * GENAU ZWOELF (Entscheidung des Besitzers, 11.11.2026). Diese App legt
 * Wallets mit zwoelf Woertern an, und die Vorlage tut es auch
 * (`createMnemonic(12)` in `src/zustand.tsx`). Laengere Wortfolgen nimmt
 * das Wiederherstellen nicht mehr an; wer mehr als zwoelf Woerter einfuegt,
 * bekommt gesagt, wie viele keinen Platz gefunden haben.
 */
object Merkwortfelder {

    const val ANZAHL = 12

    /** Was nach einer Eingabe gilt. */
    data class Ergebnis(
        /** Die zwoelf Felder danach. */
        val felder: List<String>,
        /** Das Feld, in dem es weitergeht; `null` heisst: wo man gerade ist. */
        val fokus: Int?,
        /** Woerter, fuer die in den zwoelf Feldern kein Platz mehr war. */
        val uebrig: Int,
    )

    /** Zwoelf leere Felder. */
    fun leer(): List<String> = List(ANZAHL) { "" }

    /**
     * In Feld [stelle] steht jetzt [eingabe].
     *
     * EIN WORT bleibt, wo es ist. Getippt wird Zeichen fuer Zeichen; das
     * Feld nimmt, was kommt.
     *
     * EIN LEERZEICHEN AM ENDE heisst "weiter": Das Wort bleibt im Feld, es
     * geht im naechsten weiter. So laesst sich die ganze Reihe tippen, wie
     * man sie abliest - Wort, Leerzeichen, Wort.
     *
     * MEHRERE WOERTER AUF EINMAL - eingefuegt aus der Zwischenablage oder
     * von einer Tastatur, die ganze Saetze schickt - werden verteilt: das
     * erste in dieses Feld, die weiteren in die folgenden. Was dort stand,
     * wird ersetzt; wer einfuegt, meint das Eingefuegte. Was ueber das
     * zwoelfte Feld hinausginge, wird nicht uebernommen und gezaehlt.
     */
    fun eingabe(felder: List<String>, stelle: Int, eingabe: String): Ergebnis {
        require(felder.size == ANZAHL) { "Es sind immer zwoelf Felder" }
        require(stelle in felder.indices) { "Feld $stelle gibt es nicht" }

        val woerter = eingabe.split(Regex("\\s+")).filter { it.isNotEmpty() }
        val trennt = eingabe.any { it.isWhitespace() }

        // Kein Leerraum: schlichtes Tippen.
        if (!trennt) {
            return Ergebnis(felder.mitWort(stelle, eingabe), fokus = null, uebrig = 0)
        }
        // Nur Leerraum: Das Feld ist leer, es geht nirgends hin.
        if (woerter.isEmpty()) {
            return Ergebnis(felder.mitWort(stelle, ""), fokus = null, uebrig = 0)
        }

        val passend = woerter.take(felder.size - stelle)
        val neu = felder.toMutableList().also { l ->
            for ((i, w) in passend.withIndex()) l[stelle + i] = w
        }

        val letztes = stelle + passend.size - 1
        // Endet die Eingabe mit Leerraum, geht es im Feld DANACH weiter -
        // wenn es eines gibt.
        val weiter = eingabe.last().isWhitespace() && letztes < neu.lastIndex
        return Ergebnis(
            felder = neu,
            fokus = if (weiter) letztes + 1 else letztes,
            uebrig = woerter.size - passend.size,
        )
    }

    /** Wie viele Felder ausgefuellt sind. */
    fun anzahl(felder: List<String>): Int = felder.count { it.isNotBlank() }

    /**
     * Die Woerter in ihrer Reihenfolge, durch ein Leerzeichen getrennt - so,
     * wie `WalletModel.woerterUebernehmen` sie erwartet. Leere Felder
     * fallen weg; ob dann die Zahl stimmt, sagt das Modell.
     */
    fun alsText(felder: List<String>): String =
        felder.filter { it.isNotBlank() }.joinToString(" ") { it.trim() }

    private fun List<String>.mitWort(stelle: Int, wort: String): List<String> =
        toMutableList().also { it[stelle] = wort }
}
