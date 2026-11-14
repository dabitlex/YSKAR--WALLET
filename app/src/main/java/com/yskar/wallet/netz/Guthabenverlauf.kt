package com.yskar.wallet.netz

/**
 * Wie hoch das Guthaben in den letzten Tagen stand.
 *
 * REINE RECHNUNG, KEINE ANDROID-API - dasselbe Muster wie [Waechter] und
 * [Sendestatus]. Darum laeuft das im JVM-Unittest, und darum gibt es
 * Beweise dafuer, bevor eine Linie gezeichnet wird.
 *
 * DER SPIEGEL NENNT NUR DAS HEUTIGE GUTHABEN. Frueheres steht nirgends. Es
 * laesst sich aber ausrechnen: rueckwaerts vom heutigen Stand durch die
 * Bewegungen. Ein Eingang, ein Pool-Anteil und ein Blockertrag ziehen
 * ihren Betrag ab; ein Ausgang legt Betrag PLUS Gebuehr wieder drauf.
 *
 * Die Gebuehr zaehlt NUR beim Ausgang. Der Spiegel nennt sie auch an
 * Eingaengen - dort hat sie der Absender bezahlt, nicht dieses Konto.
 *
 * Nachgemessen am 10.11.2026 am vollstaendigen Verlauf eines echten Kontos
 * (ueber tausend Bewegungen: Pool-Anteile, Eingaenge, Ausgaenge):
 * Eingaenge minus Ausgaenge samt Gebuehren ergaben das Guthaben des
 * Spiegels auf die kleinste Einheit genau. Die Testdaten unter
 * test/resources/netz/vollstaendig sind nach dieser Regel gebaut, mit
 * erfundenen Werten; der Test dazu steht in `GuthabenverlaufTest`.
 *
 * ES WIRD NICHTS GESCHAETZT. Wo die Rechnung nicht weiterkommt, hoert sie
 * auf und sagt, warum ([Grenze]). Die Linie und die Summen gelten dann nur
 * fuer den Teil, der belegt ist.
 *
 * NICHTS HIERVON FLIESST IN EINE UEBERWEISUNG. Das ist Anzeige. Was
 * gesendet werden darf, entscheidet [Senden.pruefe] am frisch geladenen
 * Konto.
 */
object Guthabenverlauf {

    /** Die drei Zeitraeume der Vorlage. Rollend: jetzt minus so viele Tage. */
    enum class Zeitraum(val tage: Int) {
        TAGE_7(7),
        TAGE_30(30),
        JAHR(365);

        /** Beginn in Unixsekunden. Keine Zeitzone, keine Tagesgrenze. */
        fun beginn(jetzt: Long): Long = jetzt - tage * SEKUNDEN_JE_TAG

        companion object {
            fun ausTagen(tage: Int): Zeitraum = entries.firstOrNull { it.tage == tage } ?: TAGE_30
        }
    }

    /** Warum die Linie nicht bis zum Beginn des Zeitraums zurueckreicht. */
    enum class Grenze {
        /** Sie reicht zurueck. Nichts fehlt. */
        KEINE,

        /** Aeltere Seiten des Verlaufs sind noch nicht geladen. */
        SEITEN_FEHLEN,

        /** Eine Bewegung, deren Art der Spiegel nicht nennt - Richtung unbekannt. */
        UNBEKANNTE_ART,

        /** Eine Bewegung ohne Zeit - sie laesst sich nicht auf die Achse setzen. */
        OHNE_ZEIT,

        /** Die Rechnung ergaebe einen Stand unter null. Dann stimmt etwas nicht. */
        UNSTIMMIG,
    }

    /**
     * Kommt die Rechnung am Anfang des Verlaufs bei null an?
     *
     * Nur pruefbar, wenn der Verlauf bis zum Anfang geladen ist. Dann MUSS
     * sie aufgehen: Ein Konto beginnt mit nichts.
     */
    sealed interface Gegenprobe {
        /** Der Verlauf ist nicht bis zum Anfang geladen oder nicht durchzurechnen. */
        data object NichtMoeglich : Gegenprobe

        data object GehtAuf : Gegenprobe

        /**
         * [rest] ist, was am Anfang uebrig bleibt. `null`, wenn die Rechnung
         * unterwegs unter null fiele - dann gibt es keinen Rest zu nennen.
         */
        data class GehtNichtAuf(val rest: Long?) : Gegenprobe
    }

    /** Eine Stufe der Linie: Ab [zeit] stand das Guthaben bei [stand]. */
    data class Stufe(val zeit: Long, val stand: Long)

    data class Ergebnis(
        /** Der gewuenschte Beginn des Zeitraums. */
        val beginn: Long,
        /** Das rechte Ende der Linie. */
        val jetzt: Long,
        /**
         * Ab hier ist die Linie belegt. Gleich [beginn], wenn nichts fehlt;
         * sonst der Zeitpunkt der aeltesten Bewegung, die sich einrechnen
         * liess - oder [jetzt], wenn keine einzige.
         */
        val belegtAb: Long,
        val grenze: Grenze,
        /** Der Stand unmittelbar vor der ersten Stufe, also bei [belegtAb]. */
        val anfangsstand: Long,
        /** Aufsteigend nach Zeit. Leer, wenn sich im belegten Teil nichts bewegt hat. */
        val stufen: List<Stufe>,
        /** Das heutige Guthaben - der Punkt am rechten Ende. */
        val endstand: Long,
        /** Summe der Eingaenge im belegten Teil. */
        val eingang: Long,
        /** Summe der Ausgaenge im belegten Teil, Betrag PLUS Gebuehr. */
        val ausgang: Long,
        val gegenprobe: Gegenprobe,
    ) {
        /** Reicht die Linie ueber den ganzen Zeitraum? */
        val vollstaendig: Boolean get() = grenze == Grenze.KEINE

        /**
         * Darf ueberhaupt eine Linie gezeichnet werden?
         *
         * Nicht, wenn die Gegenprobe nicht aufgeht oder ein Stand unter null
         * fiele: Dann erklaert der Verlauf das Guthaben nicht - etwa weil
         * mittendrin Bewegungen fehlen -, und jede Stufe waere eine
         * Behauptung. Wo der Fehler liegt, laesst sich nicht sagen; also gilt
         * auch der Teil davor nicht.
         */
        val zeichenbar: Boolean
            get() = grenze != Grenze.UNSTIMMIG && gegenprobe !is Gegenprobe.GehtNichtAuf
    }

    /**
     * Rechnet rueckwaerts.
     *
     * [guthaben] ist der heutige Stand, [verlauf] sind die geladenen
     * Bewegungen in beliebiger Reihenfolge und duerfen Doppelte enthalten.
     * [bisZumAnfangGeladen] sagt, ob hinter der aeltesten keine Seite mehr
     * kommt. [beginn] und [jetzt] sind Unixsekunden.
     *
     * Geordnet wird nach Block und Stelle im Block, NICHT nach der Zeit:
     * Die Reihenfolge der Kette ist die, in der gebucht wurde. Die Zeit
     * eines Blocks darf ein paar Minuten hinter der seines Vorgaengers
     * liegen; fuer die Achse wird sie darum nie spaeter gesetzt als die der
     * juengeren Bewegung davor, und nie spaeter als [jetzt].
     */
    fun rechne(
        guthaben: Long,
        verlauf: List<VerlaufEintrag>,
        bisZumAnfangGeladen: Boolean,
        beginn: Long,
        jetzt: Long,
    ): Ergebnis {
        require(beginn <= jetzt) { "Der Zeitraum beginnt nach seinem Ende" }

        // Juengste zuerst. Die Art gehoert zum Schluessel: Eine Ueberweisung
        // an sich selbst steht zweimal da, als Aus- und als Eingang.
        val geordnet = verlauf
            .distinctBy { Waechter.schluessel(it) }
            .sortedWith(
                compareByDescending<VerlaufEintrag> { it.height }
                    .thenByDescending { it.idx }
                    // Auf derselben Stelle stehen Aus- und Eingang einer
                    // Ueberweisung an sich selbst. Rueckwaerts kommt zuerst der
                    // Ausgang wieder drauf, dann geht der Eingang ab: So faellt
                    // der Zwischenstand nie unter null, wenn die Rechnung im
                    // Ganzen stimmt. Zu sehen ist er nicht - siehe unten,
                    // "eine Stufe je Augenblick".
                    .thenBy { if (it.art.eingehend) 1 else 0 },
            )

        val stufenRueckwaerts = ArrayList<Stufe>()
        var stand = guthaben
        var eingang = 0L
        var ausgang = 0L
        var grenze = Grenze.KEINE
        // Zeit der zuletzt eingerechneten (juengeren) Bewegung.
        var deckel = jetzt
        var belegtAb = jetzt
        // Liegt eine geladene Bewegung VOR dem Beginn? Dann ist der Zeitraum
        // ganz abgedeckt.
        var davorErreicht = false

        for (e in geordnet) {
            if (e.art == Art.UNBEKANNT) { grenze = Grenze.UNBEKANNTE_ART; break }
            val roh = e.zeit
            if (roh == null) { grenze = Grenze.OHNE_ZEIT; break }
            val zeit = minOf(roh, deckel)

            if (zeit < beginn) { davorErreicht = true; break }

            val davor = standDavor(stand, e)
            val summe = if (e.art.eingehend) plus(eingang, e.betrag)
            else plus(ausgang, plus(e.betrag, e.gebuehr))
            if (davor == null || summe == null) { grenze = Grenze.UNSTIMMIG; break }

            // EINE STUFE JE AUGENBLICK. Mehrere Bewegungen zur selben Zeit
            // ergeben eine Stufe mit dem Stand NACH der letzten von ihnen.
            // Sonst zeichnete die Linie Zwischenstaende, die es keine
            // Sekunde lang gab - bei einer Ueberweisung an sich selbst einen
            // Zacken um den ganzen Betrag.
            if (stufenRueckwaerts.isEmpty() || stufenRueckwaerts.last().zeit != zeit) {
                stufenRueckwaerts.add(Stufe(zeit, stand))
            }
            if (e.art.eingehend) eingang = summe else ausgang = summe
            stand = davor
            deckel = zeit
            belegtAb = zeit
        }

        val durchgelaufen = grenze == Grenze.KEINE && !davorErreicht
        when {
            // Eine aeltere Bewegung liegt vor dem Beginn: Der Zeitraum ist
            // ganz abgedeckt.
            davorErreicht -> belegtAb = beginn

            // Alle Bewegungen liegen im Zeitraum, und davor gab es keine:
            // Das Konto ist juenger als der Zeitraum. Davor stand es bei dem,
            // was uebrig ist - und das muss null sein (siehe Gegenprobe).
            durchgelaufen && bisZumAnfangGeladen -> belegtAb = beginn

            // Alle geladenen Bewegungen liegen im Zeitraum, aber es fehlen
            // Seiten: Was davor war, weiss niemand.
            durchgelaufen -> grenze = Grenze.SEITEN_FEHLEN

            // Sonst hat eine der Grenzen oben schon gegriffen.
        }

        return Ergebnis(
            beginn = beginn,
            jetzt = jetzt,
            belegtAb = belegtAb,
            grenze = grenze,
            anfangsstand = stand,
            stufen = stufenRueckwaerts.asReversed().toList(),
            endstand = guthaben,
            eingang = eingang,
            ausgang = ausgang,
            gegenprobe = gegenprobe(guthaben, geordnet, bisZumAnfangGeladen),
        )
    }

    /**
     * Rechnet ALLE Bewegungen zurueck, ohne Ruecksicht auf den Zeitraum und
     * ohne etwas davon zu zeichnen. Am Ende muss null stehen.
     *
     * Die Zeit spielt dafuer keine Rolle, die Art schon: Mit einer Bewegung
     * unbekannter Richtung laesst sich nichts nachrechnen.
     */
    private fun gegenprobe(
        guthaben: Long,
        geordnet: List<VerlaufEintrag>,
        bisZumAnfangGeladen: Boolean,
    ): Gegenprobe {
        if (!bisZumAnfangGeladen) return Gegenprobe.NichtMoeglich
        if (geordnet.any { it.art == Art.UNBEKANNT }) return Gegenprobe.NichtMoeglich

        var stand = guthaben
        for (e in geordnet) {
            stand = standDavor(stand, e) ?: return Gegenprobe.GehtNichtAuf(null)
        }
        return if (stand == 0L) Gegenprobe.GehtAuf else Gegenprobe.GehtNichtAuf(stand)
    }

    /**
     * Der Stand unmittelbar VOR der Bewegung [e], wenn er danach [danach]
     * war. `null`, wenn das unter null fiele oder nicht mehr in ein `Long`
     * passte - beides heisst: Die Zahlen passen nicht zusammen.
     */
    private fun standDavor(danach: Long, e: VerlaufEintrag): Long? {
        if (e.betrag < 0 || e.gebuehr < 0) return null
        return try {
            val davor = if (e.art.eingehend) {
                Math.subtractExact(danach, e.betrag)
            } else {
                Math.addExact(danach, Math.addExact(e.betrag, e.gebuehr))
            }
            if (davor < 0) null else davor
        } catch (ueberlauf: ArithmeticException) {
            null
        }
    }

    /** Addiert, ohne ueberzulaufen; `null`, wenn es nicht mehr in ein `Long` passt. */
    private fun plus(a: Long, b: Long?): Long? {
        if (b == null) return null
        return try {
            Math.addExact(a, b)
        } catch (ueberlauf: ArithmeticException) {
            null
        }
    }

    private const val SEKUNDEN_JE_TAG = 86_400L
}
