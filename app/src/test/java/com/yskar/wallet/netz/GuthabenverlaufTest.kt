package com.yskar.wallet.netz

import com.yskar.wallet.netz.Guthabenverlauf.Gegenprobe
import com.yskar.wallet.netz.Guthabenverlauf.Grenze
import com.yskar.wallet.netz.Guthabenverlauf.Stufe
import com.yskar.wallet.netz.Guthabenverlauf.Zeitraum
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Der Guthabenverlauf - rueckwaerts gerechnet, und wo er aufhoert.
 *
 * Geprueft wird an den Kanten: die Bewegung genau auf dem Beginn, die
 * fehlende Seite, die Bewegung ohne Zeit, der Stand, der unter null fiele.
 * Und einmal am Ganzen: an einem vollstaendigen Verlauf, geformt wie die
 * Antworten des Spiegels (siehe [GuthabenverlaufAmMitschnittTest]).
 */
class GuthabenverlaufTest {

    private val tag = 86_400L
    private val jetzt = 1_794_400_000L
    private val beginn = jetzt - 30 * tag

    private var laufend = 0

    /** Eine Bewegung, [vorTagen] Tage vor jetzt, in einem eigenen Block. */
    private fun bewegung(
        art: Art,
        betrag: Long,
        vorTagen: Double,
        gebuehr: Long = 0,
        height: Long? = null,
        idx: Int = 0,
        txid: String? = null,
        zeit: Long? = jetzt - (vorTagen * tag).toLong(),
        ohneZeit: Boolean = false,
    ): VerlaufEintrag {
        laufend++
        return VerlaufEintrag(
            txid = txid ?: "tx$laufend",
            // Juenger = hoeher. Ein Block je zehn Minuten, grob.
            height = height ?: (100_000 - (vorTagen * 144).toLong()),
            idx = idx,
            zeit = if (ohneZeit) null else zeit,
            art = art,
            gegenseite = null,
            anteile = null,
            betrag = betrag,
            gebuehr = gebuehr,
            notiz = "",
        )
    }

    private fun rechne(
        guthaben: Long,
        verlauf: List<VerlaufEintrag>,
        vollstaendig: Boolean = true,
        ab: Long = beginn,
    ) = Guthabenverlauf.rechne(guthaben, verlauf, vollstaendig, ab, jetzt)

    // ------------------------------------------------------------ Grundfaelle

    @Test
    fun `ein leeres Konto ergibt eine Linie bei null`() {
        val e = rechne(0, emptyList())
        assertEquals(emptyList<Stufe>(), e.stufen)
        assertEquals(0L, e.anfangsstand)
        assertEquals(0L, e.endstand)
        assertEquals(Grenze.KEINE, e.grenze)
        assertEquals(beginn, e.belegtAb)
        assertEquals(Gegenprobe.GehtAuf, e.gegenprobe)
        assertTrue(e.zeichenbar)
    }

    @Test
    fun `Eingaenge ergeben Stufen nach oben, aufsteigend nach Zeit`() {
        val e = rechne(
            guthaben = 600,
            verlauf = listOf(
                bewegung(Art.EIN, 100, vorTagen = 20.0),
                bewegung(Art.EIN, 200, vorTagen = 10.0),
                bewegung(Art.EIN, 300, vorTagen = 1.0),
            ),
        )
        assertEquals(
            listOf(
                Stufe(jetzt - 20 * tag, 100),
                Stufe(jetzt - 10 * tag, 300),
                Stufe(jetzt - 1 * tag, 600),
            ),
            e.stufen,
        )
        assertEquals(0L, e.anfangsstand)
        assertEquals(600L, e.eingang)
        assertEquals(0L, e.ausgang)
        assertEquals(Gegenprobe.GehtAuf, e.gegenprobe)
    }

    @Test
    fun `ein Ausgang kostet Betrag plus Gebuehr`() {
        val e = rechne(
            guthaben = 1_000 - 250 - 17,
            verlauf = listOf(
                bewegung(Art.EIN, 1_000, vorTagen = 20.0),
                bewegung(Art.AUS, 250, vorTagen = 5.0, gebuehr = 17),
            ),
        )
        assertEquals(
            listOf(Stufe(jetzt - 20 * tag, 1_000), Stufe(jetzt - 5 * tag, 733)),
            e.stufen,
        )
        assertEquals(1_000L, e.eingang)
        assertEquals(267L, e.ausgang)
        assertEquals(Gegenprobe.GehtAuf, e.gegenprobe)
    }

    @Test
    fun `die Gebuehr an einem Eingang hat der Absender bezahlt`() {
        // Der Spiegel nennt sie auch dort, an jedem Eingang. Dieses Konto
        // hat sie nicht gezahlt.
        val e = rechne(
            guthaben = 500,
            verlauf = listOf(bewegung(Art.EIN, 500, vorTagen = 3.0, gebuehr = 1_680)),
        )
        assertEquals(500L, e.eingang)
        assertEquals(0L, e.ausgang)
        assertEquals(0L, e.anfangsstand)
        assertEquals(Gegenprobe.GehtAuf, e.gegenprobe)
    }

    @Test
    fun `Pool-Anteil und Blockertrag zaehlen wie ein Eingang`() {
        val e = rechne(
            guthaben = 30,
            verlauf = listOf(
                bewegung(Art.POOL, 10, vorTagen = 3.0),
                bewegung(Art.REWARD, 20, vorTagen = 2.0),
            ),
        )
        assertEquals(30L, e.eingang)
        assertEquals(listOf(10L, 30L), e.stufen.map { it.stand })
        assertEquals(Gegenprobe.GehtAuf, e.gegenprobe)
    }

    @Test
    fun `die Summen stimmen auf die kleinste Einheit`() {
        val e = rechne(
            guthaben = 30_754_160_751 - 2_500_016_800,
            verlauf = listOf(
                bewegung(Art.EIN, 30_754_160_750, vorTagen = 9.0),
                bewegung(Art.EIN, 1, vorTagen = 8.0),
                bewegung(Art.AUS, 2_500_000_000, vorTagen = 7.0, gebuehr = 16_800),
            ),
        )
        assertEquals(30_754_160_751L, e.eingang)
        assertEquals(2_500_016_800L, e.ausgang)
    }

    @Test
    fun `Anfang plus Eingang minus Ausgang ist der heutige Stand`() {
        val verlauf = listOf(
            bewegung(Art.EIN, 5_000, vorTagen = 50.0),
            bewegung(Art.AUS, 1_200, vorTagen = 25.0, gebuehr = 9),
            bewegung(Art.POOL, 77, vorTagen = 12.0),
            bewegung(Art.AUS, 300, vorTagen = 2.0, gebuehr = 9),
        )
        val guthaben = 5_000L - 1_209 + 77 - 309
        for (z in Zeitraum.entries) {
            val e = rechne(guthaben, verlauf, ab = z.beginn(jetzt))
            assertEquals("$z", e.endstand, e.anfangsstand + e.eingang - e.ausgang)
        }
    }

    // ------------------------------------------------ An sich selbst senden

    @Test
    fun `eine Ueberweisung an sich selbst kostet genau die Gebuehr`() {
        val e = rechne(
            guthaben = 1_000 - 9,
            verlauf = listOf(
                bewegung(Art.EIN, 1_000, vorTagen = 20.0),
                // Steht zweimal im Verlauf (REGELN.md, Regel 25): dieselbe
                // Kennung, derselbe Block, dieselbe Stelle.
                bewegung(Art.AUS, 400, vorTagen = 5.0, gebuehr = 9, txid = "selbst", height = 99_000),
                bewegung(Art.EIN, 400, vorTagen = 5.0, gebuehr = 9, txid = "selbst", height = 99_000),
            ),
        )
        assertEquals(1_400L, e.eingang)
        assertEquals(409L, e.ausgang)
        assertEquals(Gegenprobe.GehtAuf, e.gegenprobe)
        // EINE Stufe fuer den Augenblick, nicht zwei - kein Zacken um 400.
        assertEquals(
            listOf(Stufe(jetzt - 20 * tag, 1_000), Stufe(jetzt - 5 * tag, 991)),
            e.stufen,
        )
    }

    @Test
    fun `wer alles an sich selbst sendet, faellt dabei nicht unter null`() {
        // Der ganze Bestand geht hinaus und kommt zurueck. In welcher
        // Reihenfolge die beiden Zeilen auch ankommen: Die Rechnung darf
        // darin keinen Widerspruch sehen.
        val aus = bewegung(Art.AUS, 991, vorTagen = 5.0, gebuehr = 9, txid = "selbst", height = 99_000)
        val ein = bewegung(Art.EIN, 991, vorTagen = 5.0, gebuehr = 9, txid = "selbst", height = 99_000)
        val anfang = bewegung(Art.EIN, 1_000, vorTagen = 20.0)
        for (verlauf in listOf(listOf(anfang, aus, ein), listOf(ein, aus, anfang))) {
            val e = rechne(991, verlauf)
            assertEquals(Grenze.KEINE, e.grenze)
            assertEquals(Gegenprobe.GehtAuf, e.gegenprobe)
        }
    }

    // ------------------------------------------------------------ Der Beginn

    @Test
    fun `eine Bewegung genau auf dem Beginn zaehlt mit, eine Sekunde davor nicht`() {
        val genau = bewegung(Art.EIN, 100, vorTagen = 30.0, zeit = beginn)
        val davor = bewegung(Art.EIN, 40, vorTagen = 30.0, zeit = beginn - 1, height = 1)

        val e = rechne(140, listOf(genau, davor))
        assertEquals(listOf(Stufe(beginn, 140)), e.stufen)
        assertEquals(40L, e.anfangsstand)
        assertEquals(100L, e.eingang)
        assertEquals(beginn, e.belegtAb)
        assertEquals(Grenze.KEINE, e.grenze)
    }

    @Test
    fun `liegt alles vor dem Beginn, ist die Linie waagrecht`() {
        val e = rechne(
            guthaben = 900,
            verlauf = listOf(bewegung(Art.EIN, 900, vorTagen = 200.0)),
        )
        assertEquals(emptyList<Stufe>(), e.stufen)
        assertEquals(900L, e.anfangsstand)
        assertEquals(900L, e.endstand)
        assertEquals(0L, e.eingang)
        assertEquals(0L, e.ausgang)
        assertTrue(e.vollstaendig)
    }

    @Test
    fun `eine aeltere Bewegung reicht als Beleg, auch wenn danach Seiten fehlen`() {
        // Der Zeitraum ist abgedeckt, sobald EINE geladene Bewegung davor
        // liegt. Was noch aelter ist, braucht die Linie nicht.
        val e = rechne(
            guthaben = 900,
            verlauf = listOf(
                bewegung(Art.EIN, 100, vorTagen = 3.0),
                bewegung(Art.EIN, 50, vorTagen = 40.0),
            ),
            vollstaendig = false,
        )
        assertEquals(Grenze.KEINE, e.grenze)
        assertEquals(beginn, e.belegtAb)
        assertEquals(800L, e.anfangsstand)
        // Nachrechnen bis null geht ohne den Rest des Verlaufs nicht.
        assertEquals(Gegenprobe.NichtMoeglich, e.gegenprobe)
        assertTrue(e.zeichenbar)
    }

    @Test
    fun `ein Konto, das juenger ist als der Zeitraum, stand davor bei null`() {
        val e = rechne(
            guthaben = 70,
            verlauf = listOf(bewegung(Art.EIN, 70, vorTagen = 4.0)),
            ab = Zeitraum.JAHR.beginn(jetzt),
        )
        assertEquals(Zeitraum.JAHR.beginn(jetzt), e.belegtAb)
        assertEquals(0L, e.anfangsstand)
        assertTrue(e.vollstaendig)
    }

    // ---------------------------------------------- Wo die Rechnung aufhoert

    @Test
    fun `fehlen Seiten, gilt die Linie erst ab der aeltesten geladenen Bewegung`() {
        val e = rechne(
            guthaben = 1_000,
            verlauf = listOf(
                bewegung(Art.EIN, 100, vorTagen = 2.0),
                bewegung(Art.EIN, 200, vorTagen = 6.0),
            ),
            vollstaendig = false,
        )
        assertEquals(Grenze.SEITEN_FEHLEN, e.grenze)
        assertFalse(e.vollstaendig)
        assertEquals(jetzt - 6 * tag, e.belegtAb)
        assertEquals(700L, e.anfangsstand)
        assertEquals(300L, e.eingang)
        assertEquals(Gegenprobe.NichtMoeglich, e.gegenprobe)
        // Der belegte Teil darf gezeichnet werden.
        assertTrue(e.zeichenbar)
    }

    @Test
    fun `ohne eine einzige geladene Bewegung ist nichts belegt`() {
        val e = rechne(1_000, emptyList(), vollstaendig = false)
        assertEquals(Grenze.SEITEN_FEHLEN, e.grenze)
        assertEquals(jetzt, e.belegtAb)
        assertEquals(emptyList<Stufe>(), e.stufen)
    }

    @Test
    fun `an einer Bewegung unbekannter Art hoert die Rechnung auf`() {
        val e = rechne(
            guthaben = 1_000,
            verlauf = listOf(
                bewegung(Art.EIN, 100, vorTagen = 2.0),
                bewegung(Art.UNBEKANNT, 555, vorTagen = 6.0),
                bewegung(Art.EIN, 345, vorTagen = 9.0),
            ),
        )
        assertEquals(Grenze.UNBEKANNTE_ART, e.grenze)
        assertEquals(jetzt - 2 * tag, e.belegtAb)
        assertEquals(listOf(Stufe(jetzt - 2 * tag, 1_000)), e.stufen)
        assertEquals(100L, e.eingang)
        // Die 555 stehen in keiner Summe - ihre Richtung kennt niemand.
        assertEquals(0L, e.ausgang)
        assertEquals(Gegenprobe.NichtMoeglich, e.gegenprobe)
    }

    @Test
    fun `an einer Bewegung ohne Zeit hoert die Linie auf`() {
        val e = rechne(
            guthaben = 300,
            verlauf = listOf(
                bewegung(Art.EIN, 100, vorTagen = 2.0),
                bewegung(Art.EIN, 200, vorTagen = 6.0, ohneZeit = true),
            ),
        )
        assertEquals(Grenze.OHNE_ZEIT, e.grenze)
        assertEquals(jetzt - 2 * tag, e.belegtAb)
        assertEquals(100L, e.eingang)
        // Nachrechnen laesst sich trotzdem: Dafuer braucht es keine Zeit.
        assertEquals(Gegenprobe.GehtAuf, e.gegenprobe)
    }

    @Test
    fun `eine Zeit in der Zukunft steht am rechten Rand, nicht dahinter`() {
        val e = rechne(
            guthaben = 100,
            verlauf = listOf(bewegung(Art.EIN, 100, vorTagen = 0.0, zeit = jetzt + 3_600)),
        )
        assertEquals(listOf(Stufe(jetzt, 100)), e.stufen)
    }

    @Test
    fun `laeuft die Blockzeit rueckwaerts, gilt die Reihenfolge der Kette`() {
        // Block 500 traegt eine SPAETERE Zeit als Block 501. Gebucht wurde
        // trotzdem erst 500, dann 501.
        val frueher = bewegung(Art.EIN, 100, vorTagen = 0.0, height = 500, zeit = jetzt - 1_000)
        val spaeter = bewegung(Art.AUS, 60, vorTagen = 0.0, height = 501, zeit = jetzt - 1_300, gebuehr = 5)

        val e = rechne(35, listOf(frueher, spaeter))
        assertEquals(Grenze.KEINE, e.grenze)
        // Beide Stufen fallen auf denselben Augenblick: eine Stufe, der
        // Stand nach beiden.
        assertEquals(listOf(Stufe(jetzt - 1_300, 35)), e.stufen)
        assertEquals(Gegenprobe.GehtAuf, e.gegenprobe)
    }

    @Test
    fun `die Stufen stehen nie in falscher Zeitfolge`() {
        val verlauf = listOf(
            bewegung(Art.EIN, 10, vorTagen = 0.0, height = 10, zeit = jetzt - 500),
            bewegung(Art.EIN, 10, vorTagen = 0.0, height = 11, zeit = jetzt - 900),
            bewegung(Art.EIN, 10, vorTagen = 0.0, height = 12, zeit = jetzt - 700),
            bewegung(Art.EIN, 10, vorTagen = 0.0, height = 13, zeit = jetzt - 100),
        )
        val zeiten = rechne(40, verlauf).stufen.map { it.zeit }
        assertEquals(zeiten.sorted(), zeiten)
        assertEquals(zeiten.distinct(), zeiten)
    }

    // ------------------------------------------- Wenn die Zahlen nicht passen

    @Test
    fun `faellt ein Stand unter null, wird nichts gezeichnet`() {
        // Heute 50, davor ein Eingang von 80: Dann waeren es vorher -30
        // gewesen. Mittendrin fehlt etwas.
        val e = rechne(
            guthaben = 50,
            verlauf = listOf(
                bewegung(Art.EIN, 20, vorTagen = 1.0),
                bewegung(Art.EIN, 80, vorTagen = 3.0),
            ),
            vollstaendig = false,
        )
        assertEquals(Grenze.UNSTIMMIG, e.grenze)
        assertFalse(e.zeichenbar)
    }

    @Test
    fun `bleibt am Anfang ein Rest, geht die Gegenprobe nicht auf`() {
        val e = rechne(
            guthaben = 1_000,
            verlauf = listOf(bewegung(Art.EIN, 400, vorTagen = 3.0)),
        )
        assertEquals(Gegenprobe.GehtNichtAuf(600), e.gegenprobe)
        assertFalse(e.zeichenbar)
    }

    @Test
    fun `die Gegenprobe rechnet auch das nach, was vor dem Zeitraum liegt`() {
        // Im Zeitraum stimmt alles. Der Fehler liegt 100 Tage zurueck.
        val e = rechne(
            guthaben = 500,
            verlauf = listOf(
                bewegung(Art.EIN, 100, vorTagen = 3.0),
                bewegung(Art.EIN, 300, vorTagen = 100.0),
            ),
        )
        assertEquals(Grenze.KEINE, e.grenze)
        assertEquals(Gegenprobe.GehtNichtAuf(100), e.gegenprobe)
        assertFalse(e.zeichenbar)
    }

    @Test
    fun `ein Betrag, der nicht mehr in ein Long passt, ist ein Widerspruch und kein Absturz`() {
        val e = rechne(
            guthaben = Long.MAX_VALUE - 5,
            verlauf = listOf(bewegung(Art.AUS, 100, vorTagen = 1.0, gebuehr = 9)),
            vollstaendig = false,
        )
        assertEquals(Grenze.UNSTIMMIG, e.grenze)
        assertFalse(e.zeichenbar)
    }

    @Test
    fun `ein negativer Betrag vom Spiegel ist ein Widerspruch`() {
        val e = rechne(
            guthaben = 100,
            verlauf = listOf(bewegung(Art.EIN, -100, vorTagen = 1.0)),
            vollstaendig = false,
        )
        assertEquals(Grenze.UNSTIMMIG, e.grenze)
    }

    // --------------------------------------------------------- Unempfindlich

    @Test
    fun `Reihenfolge und Doppelte aendern nichts am Ergebnis`() {
        val a = bewegung(Art.EIN, 1_000, vorTagen = 20.0)
        val b = bewegung(Art.AUS, 250, vorTagen = 5.0, gebuehr = 17)
        val c = bewegung(Art.POOL, 3, vorTagen = 1.0)
        val guthaben = 1_000L - 267 + 3

        val geordnet = rechne(guthaben, listOf(c, b, a))
        assertEquals(geordnet, rechne(guthaben, listOf(a, b, c)))
        assertEquals(geordnet, rechne(guthaben, listOf(b, c, a, b, c, a)))
    }

    @Test
    fun `der Zeitraum rechnet in Sekunden, ohne Zeitzone`() {
        assertEquals(jetzt - 7 * tag, Zeitraum.TAGE_7.beginn(jetzt))
        assertEquals(jetzt - 30 * tag, Zeitraum.TAGE_30.beginn(jetzt))
        assertEquals(jetzt - 365 * tag, Zeitraum.JAHR.beginn(jetzt))
    }

    @Test
    fun `ein unbekannter gemerkter Zeitraum wird zu 30 Tagen`() {
        assertEquals(Zeitraum.TAGE_7, Zeitraum.ausTagen(7))
        assertEquals(Zeitraum.JAHR, Zeitraum.ausTagen(365))
        assertEquals(Zeitraum.TAGE_30, Zeitraum.ausTagen(0))
        assertEquals(Zeitraum.TAGE_30, Zeitraum.ausTagen(90))
    }

    @Test
    fun `ein Zeitraum, der nach seinem Ende beginnt, ist ein Fehler des Aufrufers`() {
        assertThrows(IllegalArgumentException::class.java) {
            Guthabenverlauf.rechne(0, emptyList(), true, beginn = jetzt + 1, jetzt = jetzt)
        }
    }
}

/**
 * Die Rechnung gegen einen VOLLSTAENDIGEN Verlauf.
 *
 * Die Dateien unter netz/vollstaendig sind geformt wie die Antworten von
 * https://yskar.vercel.app: das Konto und sechs Seiten zu je hoechstens
 * 200 Bewegungen, bis `weiter` null ist. Feldnamen, Feldtypen und
 * Reihenfolge stammen aus echten Mitschnitten vom 10.11.2026. Die WERTE
 * sind seit dem 13.11.2026 erfunden - Adressen, Kennungen, Hoehen, Zeiten,
 * Betraege und Umfang -, damit kein echtes Konto im Repository steht.
 * 1068 Bewegungen aus 33 Tagen: 1034 Pool-Anteile, 14 Eingaenge,
 * 20 Ausgaenge.
 *
 * DIE REGEL "Ausgang = Betrag plus Gebuehr, Eingang = Betrag ohne Gebuehr"
 * ist am echten Verlauf gemessen worden: Nur mit ihr kam die Rechnung bei
 * genau null an. Die erfundenen Werte sind nach derselben Regel gebaut.
 * Dieser Test BEWEIST die Regel also nicht mehr - er haelt sie fest und
 * schlaegt an, sobald jemand anders rechnet.
 */
class GuthabenverlaufAmMitschnittTest {

    private fun lies(name: String): JSONObject =
        JSONObject(
            GuthabenverlaufAmMitschnittTest::class.java
                .getResourceAsStream("/netz/vollstaendig/$name")!!
                .use { it.readBytes() }
                .toString(Charsets.UTF_8),
        )

    private val spiegel = Spiegel(
        object : Spiegel.Transport {
            override fun anfrage(methode: String, url: String, rumpf: String?) =
                throw NetzFehler(NetzFehler.Ursache.OFFLINE)
        },
    )

    private val konto = spiegel.kontoAus(lies("konto.json"))
    private val seiten = (1..6).map { spiegel.verlaufSeiteAus(lies("seite_0$it.json")) }
    private val alles = seiten.flatMap { it.eintraege }

    /** Der Augenblick der Aufnahme: kurz nach der juengsten Bewegung. */
    private val jetzt = alles.maxOf { it.zeit!! } + 600

    @Test
    fun `die Testdaten sind vollstaendig und so gross wie erzeugt`() {
        assertEquals(1068, alles.size)
        assertEquals(1068, alles.distinctBy { Waechter.schluessel(it) }.size)
        assertEquals(null, seiten.last().weiter)
        assertEquals(43_140_686_902L, konto.guthaben)
        assertEquals(1034, alles.count { it.art == Art.POOL })
        assertEquals(14, alles.count { it.art == Art.EIN })
        assertEquals(20, alles.count { it.art == Art.AUS })
        assertTrue(alles.none { it.zeit == null })
    }

    @Test
    fun `der vollstaendige Verlauf kommt bei genau null an`() {
        for (z in Zeitraum.entries) {
            val e = Guthabenverlauf.rechne(konto.guthaben, alles, true, z.beginn(jetzt), jetzt)
            assertEquals("$z", Gegenprobe.GehtAuf, e.gegenprobe)
            assertEquals("$z", Grenze.KEINE, e.grenze)
            assertTrue("$z", e.zeichenbar)
        }
    }

    @Test
    fun `ueber ein Jahr gerechnet beginnt das Konto bei null und die Summen stimmen`() {
        val e = Guthabenverlauf.rechne(
            konto.guthaben, alles, true, Zeitraum.JAHR.beginn(jetzt), jetzt,
        )
        // Das Konto ist 34 Tage alt, der Zeitraum ein Jahr.
        assertEquals(0L, e.anfangsstand)
        assertEquals(Zeitraum.JAHR.beginn(jetzt), e.belegtAb)
        // Beim Erzeugen der Testdaten mitgezaehlt, unabhaengig von dieser Klasse.
        assertEquals(1_097_822_552_736L, e.eingang)
        assertEquals(1_054_681_865_834L, e.ausgang)
        assertEquals(konto.guthaben, e.eingang - e.ausgang)
    }

    @Test
    fun `kein Stand der Linie liegt unter null`() {
        val e = Guthabenverlauf.rechne(
            konto.guthaben, alles, true, Zeitraum.JAHR.beginn(jetzt), jetzt,
        )
        assertTrue(e.stufen.all { it.stand >= 0 })
        assertEquals(konto.guthaben, e.stufen.last().stand)
        val zeiten = e.stufen.map { it.zeit }
        assertEquals(zeiten.sorted(), zeiten)
    }

    @Test
    fun `sieben Tage sind schon mit der ersten Seite belegt oder sagen, dass etwas fehlt`() {
        // Nur die erste Seite, wie die App sie nach dem Start hat.
        val erste = seiten.first()
        val e = Guthabenverlauf.rechne(
            konto.guthaben, erste.eintraege, false, Zeitraum.TAGE_7.beginn(jetzt), jetzt,
        )
        assertEquals(Gegenprobe.NichtMoeglich, e.gegenprobe)
        assertEquals(e.endstand, e.anfangsstand + e.eingang - e.ausgang)
        val aelteste = erste.eintraege.minOf { it.zeit!! }
        if (aelteste < Zeitraum.TAGE_7.beginn(jetzt)) {
            assertEquals(Grenze.KEINE, e.grenze)
        } else {
            assertEquals(Grenze.SEITEN_FEHLEN, e.grenze)
            assertEquals(aelteste, e.belegtAb)
        }
    }

    @Test
    fun `mit jeder weiteren Seite reicht die Linie weiter zurueck, und der belegte Teil bleibt gleich`() {
        val beginn = Zeitraum.JAHR.beginn(jetzt)
        var vorher: Guthabenverlauf.Ergebnis? = null
        for (n in 1..6) {
            val geladen = seiten.take(n).flatMap { it.eintraege }
            val e = Guthabenverlauf.rechne(konto.guthaben, geladen, n == 6, beginn, jetzt)
            vorher?.let { alt ->
                assertTrue("Seite $n", e.belegtAb <= alt.belegtAb)
                // Was schon belegt war, aendert sich durch aeltere Seiten nicht.
                val ueberlappung = e.stufen.filter { it.zeit > alt.belegtAb }
                assertEquals("Seite $n", alt.stufen.filter { it.zeit > alt.belegtAb }, ueberlappung)
            }
            vorher = e
        }
        assertEquals(Grenze.KEINE, vorher!!.grenze)
    }
}
