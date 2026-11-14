package com.yskar.wallet.netz

/**
 * Was sich seit dem letzten Hinsehen geaendert hat.
 *
 * REINE RECHNUNG, KEINE ANDROID-API. Darum laeuft das im JVM-Unittest, und
 * darum gibt es ueberhaupt Beweise dafuer - eine Benachrichtigung, die zur
 * falschen Zeit kommt oder zweimal, faellt sonst erst dem Besitzer auf.
 * Dasselbe Muster wie [Sendestatus].
 *
 * WER MELDET, MUSS SICH ERINNERN. Ohne [Gedaechtnis] wuerde bei jedem
 * Aufwachen der ganze Verlauf als neu gelten. Das Gedaechtnis wird nicht
 * hier gespeichert, sondern zurueckgegeben - diese Datei kennt keine Ablage.
 */
object Waechter {

    /** Eine Bewegung, die der Besitzer noch nicht gesehen hat. */
    data class Meldung(
        val txid: String,
        val eingehend: Boolean,
        val betrag: Long,
        val gegenseite: String?,
        /**
         * Welcher Eintrag im Verlauf das ist ([schluessel]). Ein Tipp auf
         * die Mitteilung oeffnet genau diesen - die txid allein waere bei
         * einer Ueberweisung an sich selbst zweideutig.
         */
        val schluessel: String,
    )

    data class Gedaechtnis(
        /** -1 heisst: noch nie hingesehen. */
        val guthaben: Long = UNBEKANNT,
        val txids: Set<String> = emptySet(),
    )

    data class Befund(
        val meldungen: List<Meldung>,
        val gedaechtnis: Gedaechtnis,
    )

    const val UNBEKANNT = -1L

    /**
     * Was ist neu?
     *
     * DER ERSTE DURCHGANG MELDET NICHTS. Wer die Benachrichtigungen
     * einschaltet, soll nicht als Erstes seinen gesamten Verlauf als
     * Mitteilungen bekommen - er merkt sich nur, was da ist, und meldet
     * ab dem naechsten Mal.
     *
     * Gemeldet wird, was IN EINEM BLOCK steht. Der Verlauf enthaelt genau
     * das; Wartendes steht woanders. Fuer einen Ausgang heisst das, was
     * der Besitzer erwartet: "bestaetigt", nicht "abgeschickt".
     */
    fun pruefe(konto: Konto, alt: Gedaechtnis): Befund {
        val kennungen = konto.verlauf.map { schluessel(it) }
        // Das Gedaechtnis traegt den frischen Verlauf UND die Kennungen,
        // die wir schon kannten. Sonst faellt ein Eintrag, der aus der
        // ersten Seite herausrutscht, spaeter wieder als neu an.
        val neuesGedaechtnis = Gedaechtnis(
            guthaben = konto.guthaben,
            txids = (kennungen + alt.txids).toSet(),
        )

        if (alt.guthaben == UNBEKANNT) {
            return Befund(emptyList(), neuesGedaechtnis)
        }

        val meldungen = konto.verlauf
            .filter { schluessel(it) !in alt.txids }
            .map {
                Meldung(
                    txid = it.txid,
                    eingehend = it.art.eingehend,
                    betrag = it.betrag,
                    gegenseite = it.gegenseite,
                    schluessel = schluessel(it),
                )
            }
        return Befund(meldungen, neuesGedaechtnis)
    }

    /**
     * Kennung EINES Eintrags, nicht einer Ueberweisung.
     *
     * Eine Ueberweisung an sich selbst steht zweimal im Verlauf - einmal
     * als Ein-, einmal als Ausgang. Mit der txid allein verschwaende die
     * zweite Zeile, und beim Nachladen taeuchte sie als neu wieder auf.
     */
    fun schluessel(e: VerlaufEintrag): String = "${e.txid}:${e.idx}:${e.art}"
}
