package com.yskar.wallet.kontakte

import com.yskar.wallet.kern.Address

/** Ein Name fuer eine Adresse. Mehr ist ein Kontakt nicht. */
data class Kontakt(val name: String, val adresse: String)

/**
 * Kontakte: Namen statt Adressen (Wunsch des Besitzers, 12.11.2026).
 *
 * WAS EIN KONTAKT IST - UND WAS NICHT. Ein Kontakt ist ein Name, den der
 * Besitzer SELBST einer Adresse gegeben hat. Er ist eine Lesehilfe: "An
 * Anna" liest sich leichter als "An ysr1j8nn…". Er ist KEIN Beweis. Wohin
 * eine Ueberweisung geht, sagt allein die Adresse, und die steht beim
 * Pruefen weiter vollstaendig da (REGELN.md, Regeln 19, 21 und 40). Der
 * Name ersetzt sie nirgends, er steht nur daneben.
 *
 * Darum gilt:
 *
 *  - Ein Name entsteht nur durch Eintippen. Kein QR-Code, kein Link und
 *    keine Antwort des Netzes legt einen Kontakt an oder benennt ihn um
 *    (Regel 22: Ein gescannter Code ist kein Beweis).
 *  - Gespeichert wird nur eine Adresse, die `Address.isValid` besteht.
 *  - Eine Adresse hat hoechstens EINEN Namen. Zwei Namen fuer dieselbe
 *    Adresse waeren harmlos; aber welcher stuende dann im Verlauf?
 *  - Ein Name wird bereinigt: keine Steuerzeichen, keine Zeichen, die die
 *    Schreibrichtung umkehren. Sonst liesse sich ein Name bauen, der auf
 *    dem Bildschirm anders aussieht, als er gespeichert ist.
 *
 * Kontakte liegen nur auf diesem Geraet (`Kontaktspeicher`), verlassen es
 * nie und stehen in keinem Log. Sie sind nicht geheim wie die zwoelf
 * Woerter - aber sie sagen, mit wem jemand zu tun hat, und gehen mit der
 * Wallet, wenn sie vom Geraet entfernt wird.
 *
 * Ohne Android, ohne Netz: Diese Datei ist reine Logik und in
 * `KontakteTest` auf der JVM getestet.
 */
object Kontakte {

    /** So lang darf ein Name sein, in Zeichen. */
    const val NAME_MAX = 40

    /** So viele Kontakte fasst die Liste. */
    const val ANZAHL_MAX = 500

    enum class Ablehnung {
        NAME_LEER,
        NAME_ZU_LANG,
        ADRESSE_UNGUELTIG,

        /** Die Adresse hat schon einen Namen - welchen, steht im Ergebnis. */
        ADRESSE_SCHON_DA,
        ZU_VIELE,
    }

    sealed interface Ergebnis {
        data class Ok(val liste: List<Kontakt>) : Ergebnis
        data class Abgelehnt(val grund: Ablehnung, val vorhanden: Kontakt? = null) : Ergebnis
    }

    /**
     * Ein Name, wie er gespeichert und gezeigt wird: ohne Steuerzeichen,
     * ohne Zeichen, die die Schreibrichtung aendern, ohne Raender, und
     * jede Folge von Leerraum ist EIN Leerzeichen.
     */
    fun nameBereinigt(roh: String): String {
        val sauber = StringBuilder()
        var i = 0
        while (i < roh.length) {
            val cp = roh.codePointAt(i)
            i += Character.charCount(cp)
            when {
                Character.isWhitespace(cp) || cp == 0x00A0 -> sauber.append(' ')
                Character.isISOControl(cp) -> Unit
                // Schreibrichtung: LRM/RLM, Einbettungen, Ueberschreibungen, Isolate.
                cp == 0x200E || cp == 0x200F -> Unit
                cp in 0x202A..0x202E -> Unit
                cp in 0x2066..0x2069 -> Unit
                // Unsichtbares: Breite null, Wortverbinder, BOM.
                cp in 0x200B..0x200D || cp == 0x2060 || cp == 0xFEFF -> Unit
                else -> sauber.appendCodePoint(cp)
            }
        }
        return sauber.toString().trim().replace(Regex(" +"), " ")
    }

    /**
     * Einen Kontakt anlegen oder aendern.
     *
     * [bisher] ist die Adresse des Kontakts, der gerade bearbeitet wird;
     * null heisst: neu. Beim Aendern darf die Adresse dieselbe bleiben -
     * sie darf nur nicht die eines ANDEREN Kontakts werden.
     */
    fun speichern(
        liste: List<Kontakt>,
        name: String,
        adresse: String,
        bisher: String? = null,
    ): Ergebnis {
        val n = nameBereinigt(name)
        val a = adresse.trim()
        if (n.isEmpty()) return Ergebnis.Abgelehnt(Ablehnung.NAME_LEER)
        if (n.codePointCount(0, n.length) > NAME_MAX) return Ergebnis.Abgelehnt(Ablehnung.NAME_ZU_LANG)
        if (!Address.isValid(a)) return Ergebnis.Abgelehnt(Ablehnung.ADRESSE_UNGUELTIG)

        val andere = liste.filter { it.adresse != bisher }
        andere.firstOrNull { it.adresse == a }?.let {
            return Ergebnis.Abgelehnt(Ablehnung.ADRESSE_SCHON_DA, it)
        }
        if (andere.size >= ANZAHL_MAX) return Ergebnis.Abgelehnt(Ablehnung.ZU_VIELE)
        return Ergebnis.Ok(sortiert(andere + Kontakt(n, a)))
    }

    fun entfernen(liste: List<Kontakt>, adresse: String): List<Kontakt> =
        liste.filter { it.adresse != adresse }

    /** Der Name zu einer Adresse - oder null, wenn sie keinen hat. */
    fun nameFuer(liste: List<Kontakt>, adresse: String?): String? =
        if (adresse == null) null else liste.firstOrNull { it.adresse == adresse }?.name

    /** Nach Namen, Gross- und Kleinschreibung gleich; bei gleichem Namen nach Adresse. */
    fun sortiert(liste: List<Kontakt>): List<Kontakt> =
        liste.sortedWith(compareBy({ it.name.lowercase() }, { it.adresse }))

    // --- Ablage ----------------------------------------------------------
    //
    // Eine Zeile je Kontakt: Adresse, Tabulator, Name. Ein Name enthaelt
    // weder Tabulator noch Zeilenende (`nameBereinigt`), eine Adresse
    // ohnehin nicht - es gibt also nichts zu maskieren.

    fun alsText(liste: List<Kontakt>): String =
        liste.joinToString("\n") { "${it.adresse}\t${it.name}" }

    /**
     * Liest, was [alsText] schrieb - und traut ihm nicht: Jede Zeile wird
     * geprueft wie eine neue Eingabe. Was nicht besteht, faellt heraus,
     * statt als Kontakt mit ungueltiger Adresse oder unsichtbarem Namen
     * wieder aufzutauchen.
     */
    fun ausText(text: String): List<Kontakt> {
        var liste = emptyList<Kontakt>()
        for (zeile in text.split('\n')) {
            val tab = zeile.indexOf('\t')
            if (tab <= 0) continue
            val ergebnis = speichern(liste, zeile.substring(tab + 1), zeile.substring(0, tab))
            if (ergebnis is Ergebnis.Ok) liste = ergebnis.liste
        }
        return liste
    }
}
