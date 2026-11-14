package com.yskar.wallet.netz

/**
 * Was vom Verlauf geladen ist.
 *
 * [eintraege] stehen juengste zuerst und sind LUECKENLOS: von der juengsten
 * Bewegung des Kontos bis zur aeltesten geladenen fehlt keine. Darauf
 * verlaesst sich [Guthabenverlauf] - eine Luecke mittendrin ergaebe eine
 * Linie, die falsch ist, ohne dass man es ihr ansieht.
 *
 * [weiter] zeigt auf die naechste aeltere Seite; `null` heisst, der Verlauf
 * ist bis zum Anfang da.
 */
data class Verlaufsbestand(
    val eintraege: List<VerlaufEintrag>,
    val weiter: String?,
) {
    val bisZumAnfang: Boolean get() = weiter == null
}

/**
 * Den Verlauf so weit laden, wie das Diagramm ihn braucht.
 *
 * Das ist der eine Zusatz an der Netzschicht fuer den Guthabenverlauf. Neu
 * ist kein Aufruf - [Spiegel.verlauf] konnte schon blaettern -, sondern nur
 * die Schleife darum und die Buchfuehrung, was schon da ist.
 *
 * WIE VIEL DER SPIEGEL AUF EINMAL GIBT. Nachgemessen am 10.11.2026: Mit
 * `limit=200` kommen 200 Bewegungen; mit 500, 1000 oder 5000 kommen
 * ebenfalls 200, ohne Fehlermeldung. Mehr als [SEITE] zu verlangen bringt
 * also nichts. Ein Konto mit ueber tausend Bewegungen war in sechs Seiten
 * vollstaendig geladen.
 *
 * Keine Android-API, kein Zustand: Was geladen ist, kommt herein und geht
 * als neuer [Verlaufsbestand] wieder hinaus.
 */
object Verlaufslader {

    /** Mehr gibt der Spiegel je Anfrage nicht her (siehe oben). */
    const val SEITE = 200

    /**
     * Mehr Seiten holt EIN Durchgang nicht. 25 Seiten sind 5.000
     * Bewegungen; reicht das nicht, zeigt das Diagramm den belegten Teil,
     * und der naechste Durchgang macht weiter.
     */
    const val HOECHSTENS_SEITEN = 25

    /**
     * Ist der Zeitraum ab [beginn] abgedeckt?
     *
     * Ja, wenn der Verlauf bis zum Anfang geladen ist - oder wenn schon
     * EINE geladene Bewegung vor dem Beginn liegt: Der Bestand ist
     * lueckenlos, also ist dann auch alles Juengere da.
     */
    fun abgedeckt(bestand: Verlaufsbestand, beginn: Long): Boolean =
        bestand.bisZumAnfang || bestand.eintraege.any { e -> e.zeit != null && e.zeit < beginn }

    /**
     * Eine frisch geholte erste Seite auf den Bestand legen.
     *
     * Die App holt alle dreissig Sekunden die erste Seite neu, waehrend
     * aeltere Seiten liegen bleiben. Drei Faelle:
     *
     *   NICHTS NEUES ODER WENIG NEUES. Die neue Seite schliesst an den
     *   Bestand an - ihre aelteste Bewegung ist im Bestand schon da. Dann
     *   gilt: oben die neue Seite, darunter vom Bestand alles, was nicht
     *   juenger ist als ihre aelteste Bewegung. Was aus der ersten Seite
     *   herausgerutscht ist, bleibt so erhalten, und der Zeiger bleibt, wo
     *   er war.
     *
     *   MEHR NEUES, ALS AUF EINE SEITE PASST. Die neue Seite schliesst
     *   nicht an. Zwischen ihr und dem Bestand fehlen Bewegungen, und
     *   niemand weiss, wie viele. Dann wird der Bestand verworfen und neu
     *   begonnen - lieber noch einmal laden als eine Luecke behalten.
     *
     *   EINE UMGEBAUTE KETTE. Bewegungen, die im Bestand OBERHALB der
     *   aeltesten Bewegung der neuen Seite standen, in der neuen Seite aber
     *   fehlen, gibt es nicht mehr. Sie fallen weg, weil aus diesem Bereich
     *   nur die neue Seite zaehlt.
     */
    fun erneuert(
        alt: Verlaufsbestand?,
        erste: List<VerlaufEintrag>,
        ersteWeiter: String?,
    ): Verlaufsbestand {
        val frisch = Verlaufsbestand(erste, ersteWeiter)
        if (alt == null || alt.eintraege.isEmpty() || erste.isEmpty()) return frisch

        val aelteste = erste.minWith(STELLE)
        val bekannt = alt.eintraege.mapTo(HashSet()) { Waechter.schluessel(it) }
        if (Waechter.schluessel(aelteste) !in bekannt) return frisch

        val oben = erste.mapTo(HashSet()) { Waechter.schluessel(it) }
        // "Nicht juenger" statt "aelter": Aus- und Eingang einer Ueberweisung
        // an sich selbst stehen auf DERSELBEN Stelle. Liegt die Seitengrenze
        // zwischen den beiden, darf die zweite Zeile nicht verloren gehen.
        val darunter = alt.eintraege.filter { e ->
            STELLE.compare(e, aelteste) <= 0 && Waechter.schluessel(e) !in oben
        }
        return Verlaufsbestand(erste + darunter, alt.weiter)
    }

    /** Was ein Durchgang gebracht hat. */
    data class Durchgang(
        val bestand: Verlaufsbestand,
        /** Wie viele Seiten geholt wurden. */
        val seiten: Int,
        /** Woran er gescheitert ist; `null`, wenn er nicht gescheitert ist. */
        val fehler: NetzFehler?,
    )

    /**
     * Holt aeltere Seiten, bis der Zeitraum ab [beginn] abgedeckt ist, der
     * Verlauf zu Ende ist oder [hoechstens] Seiten geholt sind.
     *
     * EIN FEHLER MITTENDRIN WIRFT NICHTS WEG. Was bis dahin geladen war,
     * steht im Ergebnis; der Fehler steht daneben. Der Aufrufer entscheidet,
     * ob er ihn zeigt - der Guthabenverlauf zeigt ihn nicht (REGELN.md,
     * Regel 26).
     *
     * Blockiert, bis die Antworten da sind. Gehoert auf einen Hintergrundfaden.
     */
    fun lade(
        spiegel: Spiegel,
        adresse: String,
        bestand: Verlaufsbestand,
        beginn: Long,
        hoechstens: Int = HOECHSTENS_SEITEN,
    ): Durchgang {
        var b = bestand
        var seiten = 0
        val bekannt = b.eintraege.mapTo(HashSet()) { Waechter.schluessel(it) }

        while (seiten < hoechstens && !abgedeckt(b, beginn)) {
            val zeiger = b.weiter ?: break
            val seite = try {
                spiegel.verlauf(adresse, vor = zeiger, limit = SEITE)
            } catch (e: NetzFehler) {
                return Durchgang(b, seiten, e)
            }
            seiten++

            // Was schon da ist, kommt nicht doppelt hinein - etwa wenn sich
            // die Seitengrenze verschoben hat, waehrend geladen wurde.
            val neu = seite.eintraege.filter { bekannt.add(Waechter.schluessel(it)) }
            b = Verlaufsbestand(b.eintraege + neu, seite.weiter)

            // Derselbe Zeiger noch einmal heisst: Es geht nicht voran. Dann
            // aufhoeren, statt dieselbe Seite bis zur Obergrenze zu holen.
            if (seite.weiter == zeiger) break
        }
        return Durchgang(b, seiten, null)
    }

    /** Aufsteigend: Was weiter unten in der Kette steht, ist "kleiner". */
    private val STELLE: Comparator<VerlaufEintrag> =
        compareBy<VerlaufEintrag> { it.height }.thenBy { it.idx }
}
