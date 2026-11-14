package com.yskar.wallet.ui

import com.yskar.wallet.netz.Art
import com.yskar.wallet.netz.Guthabenverlauf
import com.yskar.wallet.netz.Guthabenverlauf.Zeitraum
import com.yskar.wallet.netz.VerlaufEintrag
import com.yskar.wallet.sprache.Deutsch
import com.yskar.wallet.sprache.Englisch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Wo die Linie beginnt, wie sie laeuft und was daruebersteht.
 */
class KurvenformTest {

    private val berlin = ZoneId.of("Europe/Berlin")
    private val heute = LocalDate.of(2026, 11, 10)

    /** 10.11.2026, 23:00 in Berlin. */
    private val jetzt = LocalDateTime.of(2026, 11, 10, 23, 0).atZone(berlin).toEpochSecond()
    private val tag = 86_400L
    private val beginn = jetzt - 30 * tag

    private var laufend = 0

    private fun bewegung(art: Art, betrag: Long, vorTagen: Double, gebuehr: Long = 0): VerlaufEintrag {
        laufend++
        return VerlaufEintrag(
            txid = "tx$laufend",
            height = 100_000 - (vorTagen * 144).toLong(),
            idx = 0,
            zeit = jetzt - (vorTagen * tag).toLong(),
            art = art,
            gegenseite = null,
            anteile = null,
            betrag = betrag,
            gebuehr = gebuehr,
            notiz = "",
        )
    }

    private fun rechne(guthaben: Long, verlauf: List<VerlaufEintrag>, vollstaendig: Boolean = true) =
        Guthabenverlauf.rechne(guthaben, verlauf, vollstaendig, beginn, jetzt)

    private fun p(x: Float, y: Float) = Kurvenpunkt(x, y)

    // ------------------------------------------------------------- Die Linie

    @Test
    fun `ohne Bewegung liegt die Linie waagrecht in der Mitte`() {
        val e = rechne(900, listOf(bewegung(Art.EIN, 900, vorTagen = 200.0)))
        assertEquals(listOf(p(0f, 0.5f), p(1f, 0.5f)), kurvenpunkte(e))
    }

    @Test
    fun `ein Eingang ist eine Stufe nach oben an seinem Zeitpunkt`() {
        // Vor 15 von 30 Tagen: genau in der Mitte.
        val e = rechne(1_000, listOf(bewegung(Art.EIN, 1_000, vorTagen = 15.0)))
        assertEquals(
            listOf(p(0f, 0f), p(0.5f, 0f), p(0.5f, 1f), p(1f, 1f)),
            kurvenpunkte(e),
        )
    }

    @Test
    fun `ein Ausgang ist eine Stufe nach unten`() {
        val e = rechne(
            guthaben = 600,
            verlauf = listOf(
                bewegung(Art.EIN, 1_000, vorTagen = 100.0),
                bewegung(Art.AUS, 391, vorTagen = 7.5, gebuehr = 9),
            ),
        )
        assertEquals(
            listOf(p(0f, 1f), p(0.75f, 1f), p(0.75f, 0f), p(1f, 0f)),
            kurvenpunkte(e),
        )
    }

    @Test
    fun `die Hoehe reicht vom kleinsten bis zum groessten Stand`() {
        // 0 -> 400 -> 1000 -> 700: unten 0, oben 1000.
        val e = rechne(
            guthaben = 700,
            verlauf = listOf(
                bewegung(Art.EIN, 400, vorTagen = 24.0),
                bewegung(Art.EIN, 600, vorTagen = 12.0),
                bewegung(Art.AUS, 291, vorTagen = 6.0, gebuehr = 9),
            ),
        )
        val y = kurvenpunkte(e).map { it.y }
        assertEquals(listOf(0f, 0f, 0.4f, 0.4f, 1f, 1f, 0.7f, 0.7f), y)
    }

    @Test
    fun `ist nur ein Teil belegt, beginnt die Linie dort und nicht am Rand`() {
        // Seiten fehlen; die aelteste geladene Bewegung ist 6 Tage alt.
        val e = rechne(
            guthaben = 1_000,
            verlauf = listOf(
                bewegung(Art.EIN, 100, vorTagen = 3.0),
                bewegung(Art.EIN, 200, vorTagen = 6.0),
            ),
            vollstaendig = false,
        )
        val punkte = kurvenpunkte(e)
        assertEquals(0.8f, punkte.first().x, 1e-6f)
        // Unten ist der Stand VOR der aeltesten Bewegung (700), oben 1000.
        assertEquals(0f, punkte.first().y, 1e-6f)
        assertEquals(p(1f, 1f), punkte.last())
    }

    @Test
    fun `ist nichts belegt, bleibt nur der Punkt fuer heute`() {
        val e = rechne(1_000, emptyList(), vollstaendig = false)
        assertEquals(listOf(p(1f, 0.5f)), kurvenpunkte(e))
    }

    @Test
    fun `geht die Rechnung nicht auf, gibt es keine Linie`() {
        val e = rechne(1_000, listOf(bewegung(Art.EIN, 400, vorTagen = 3.0)))
        assertEquals(emptyList<Kurvenpunkt>(), kurvenpunkte(e))
    }

    @Test
    fun `die Linie laeuft nie rueckwaerts und bleibt im Feld`() {
        val verlauf = (1..200).map { n ->
            if (n % 7 == 0) bewegung(Art.AUS, 30, vorTagen = n * 0.2, gebuehr = 1)
            else bewegung(Art.POOL, 11, vorTagen = n * 0.2)
        }
        val ein = verlauf.filter { it.art.eingehend }.sumOf { it.betrag }
        val aus = verlauf.filter { !it.art.eingehend }.sumOf { it.betrag + it.gebuehr }
        // Ein Startguthaben, damit zwischendurch nichts unter null faellt.
        val start = bewegung(Art.EIN, 5_000, vorTagen = 100.0)

        val punkte = kurvenpunkte(rechne(5_000 + ein - aus, verlauf + start))
        assertTrue(punkte.size > 100)
        assertEquals(punkte.map { it.x }.sorted(), punkte.map { it.x })
        assertTrue(punkte.all { it.x in 0f..1f && it.y in 0f..1f })
        assertEquals(1f, punkte.last().x, 0f)
        // Stufenlinie: Von Punkt zu Punkt aendert sich nur x oder nur y.
        for ((a, b) in punkte.zipWithNext()) {
            assertTrue("$a -> $b", a.x == b.x || a.y == b.y)
        }
    }

    // ---------------------------------------------------------------- Worte

    @Test
    fun `der Titel nennt den Zeitraum, solange nichts fehlt`() {
        val e = rechne(900, listOf(bewegung(Art.EIN, 900, vorTagen = 200.0)))
        assertEquals("Guthaben · 30 Tage", kurventitel(e, Zeitraum.TAGE_30, heute, berlin, Deutsch))
        assertEquals("Guthaben · 7 Tage", kurventitel(null, Zeitraum.TAGE_7, heute, berlin, Deutsch))
        assertEquals("Guthaben · 1 Jahr", kurventitel(null, Zeitraum.JAHR, heute, berlin, Deutsch))
    }

    @Test
    fun `fehlt etwas, nennt der Titel, ab wann die Linie belegt ist`() {
        val e = rechne(
            guthaben = 1_000,
            verlauf = listOf(bewegung(Art.EIN, 200, vorTagen = 6.0)),
            vollstaendig = false,
        )
        // Sechs Tage vor dem 10.11., 23:00.
        assertEquals("Guthaben · seit 04.11.", kurventitel(e, Zeitraum.TAGE_30, heute, berlin, Deutsch))
    }

    @Test
    fun `ist heute erst belegt, steht die Uhrzeit da, nicht das Datum`() {
        val e = rechne(
            guthaben = 1_000,
            verlauf = listOf(bewegung(Art.EIN, 200, vorTagen = 0.125)),
            vollstaendig = false,
        )
        // Drei Stunden vor 23:00.
        assertEquals("Guthaben · seit 20:00", kurventitel(e, Zeitraum.TAGE_30, heute, berlin, Deutsch))
    }

    @Test
    fun `ein Datum aus dem Vorjahr traegt das Jahr`() {
        val silvester = LocalDateTime.of(2025, 12, 31, 12, 0).atZone(berlin).toEpochSecond()
        assertEquals("31.12.2025", belegtAbText(silvester, heute, berlin, Deutsch))
        assertEquals("04.11.", belegtAbText(jetzt - 6 * tag, heute, berlin, Deutsch))
        assertEquals("23:00", belegtAbText(jetzt, heute, berlin, Deutsch))
    }

    @Test
    fun `ist gar nichts belegt, sagt der Titel das`() {
        val e = rechne(1_000, emptyList(), vollstaendig = false)
        assertEquals("Guthaben · noch keine Daten", kurventitel(e, Zeitraum.TAGE_30, heute, berlin, Deutsch))
    }

    @Test
    fun `geht die Rechnung nicht auf, behauptet der Titel kein Datum`() {
        val e = rechne(1_000, listOf(bewegung(Art.EIN, 400, vorTagen = 3.0)))
        assertEquals("Guthaben · 30 Tage", kurventitel(e, Zeitraum.TAGE_30, heute, berlin, Deutsch))
    }

    @Test
    fun `die Beschreibung nennt beide Staende vollstaendig`() {
        val e = rechne(
            guthaben = 128_457_210_946,
            verlauf = listOf(
                bewegung(Art.EIN, 100_000_000_000, vorTagen = 100.0),
                bewegung(Art.EIN, 28_457_210_946, vorTagen = 3.0),
            ),
        )
        assertEquals(
            "Guthaben über 30 Tage: von 1.000,00 auf 1.284,57210946 YSR",
            kurvenbeschreibung(e, Zeitraum.TAGE_30, heute, berlin, Deutsch),
        )
    }

    @Test
    fun `die Beschreibung eines Teils sagt, seit wann`() {
        val e = rechne(
            guthaben = 1_000,
            verlauf = listOf(bewegung(Art.EIN, 200, vorTagen = 6.0)),
            vollstaendig = false,
        )
        assertEquals(
            "Guthaben seit 04.11.: von 0,000008 auf 0,00001 YSR",
            kurvenbeschreibung(e, Zeitraum.TAGE_30, heute, berlin, Deutsch),
        )
    }

    @Test
    fun `die Kurzform der Zeitraeume steht wie in der Vorlage`() {
        assertEquals(listOf("7 T", "30 T", "1 J"), Zeitraum.entries.map { zeitraumKurz(it, Deutsch) })
        assertEquals(listOf("7 Tage", "30 Tage", "1 Jahr"), Zeitraum.entries.map { zeitraumWort(it, Deutsch) })
    }
}

/**
 * Dieselben Worte auf Englisch: das Datum anders geschrieben ("4 Nov"), die
 * Zahl anders getrennt ("1,284.57"), die Aussage dieselbe. Die Linie selbst
 * haengt nicht an der Sprache und steht darum nur oben.
 */
class KurvenformEnglischTest {

    private val berlin = ZoneId.of("Europe/Berlin")
    private val heute = LocalDate.of(2026, 11, 10)

    /** 10.11.2026, 23:00 in Berlin. */
    private val jetzt = LocalDateTime.of(2026, 11, 10, 23, 0).atZone(berlin).toEpochSecond()
    private val tag = 86_400L
    private val beginn = jetzt - 30 * tag

    private var laufend = 0

    private fun eingang(betrag: Long, vorTagen: Double): VerlaufEintrag {
        laufend++
        return VerlaufEintrag(
            txid = "tx$laufend",
            height = 100_000 - (vorTagen * 144).toLong(),
            idx = 0,
            zeit = jetzt - (vorTagen * tag).toLong(),
            art = Art.EIN,
            gegenseite = null,
            anteile = null,
            betrag = betrag,
            gebuehr = 0,
            notiz = "",
        )
    }

    private fun rechne(guthaben: Long, verlauf: List<VerlaufEintrag>, vollstaendig: Boolean = true) =
        Guthabenverlauf.rechne(guthaben, verlauf, vollstaendig, beginn, jetzt)

    /** Seiten fehlen; die aelteste geladene Bewegung ist [vorTagen] alt. */
    private fun teilweise(vorTagen: Double) =
        rechne(1_000, listOf(eingang(200, vorTagen)), vollstaendig = false)

    @Test
    fun `der Titel nennt den Zeitraum, solange nichts fehlt`() {
        val e = rechne(900, listOf(eingang(900, vorTagen = 200.0)))
        assertEquals("Balance · 30 days", kurventitel(e, Zeitraum.TAGE_30, heute, berlin, Englisch))
        assertEquals("Balance · 7 days", kurventitel(null, Zeitraum.TAGE_7, heute, berlin, Englisch))
        assertEquals("Balance · 1 year", kurventitel(null, Zeitraum.JAHR, heute, berlin, Englisch))
    }

    @Test
    fun `fehlt etwas, nennt der Titel, ab wann die Linie belegt ist`() {
        assertEquals(
            "Balance · since 4 Nov",
            kurventitel(teilweise(6.0), Zeitraum.TAGE_30, heute, berlin, Englisch),
        )
        // Drei Stunden vor 23:00 - die Uhrzeit bleibt bei 24 Stunden.
        assertEquals(
            "Balance · since 20:00",
            kurventitel(teilweise(0.125), Zeitraum.TAGE_30, heute, berlin, Englisch),
        )
    }

    @Test
    fun `das Datum steht, wie Englisch es schreibt`() {
        val silvester = LocalDateTime.of(2025, 12, 31, 12, 0).atZone(berlin).toEpochSecond()
        assertEquals("31 Dec 2025", belegtAbText(silvester, heute, berlin, Englisch))
        assertEquals("4 Nov", belegtAbText(jetzt - 6 * tag, heute, berlin, Englisch))
        assertEquals("23:00", belegtAbText(jetzt, heute, berlin, Englisch))
    }

    @Test
    fun `ist nichts belegt oder geht die Rechnung nicht auf, sagt der Titel nicht mehr als auf Deutsch`() {
        val leer = rechne(1_000, emptyList(), vollstaendig = false)
        assertEquals(
            "Balance · no data yet",
            kurventitel(leer, Zeitraum.TAGE_30, heute, berlin, Englisch),
        )
        val unstimmig = rechne(1_000, listOf(eingang(400, vorTagen = 3.0)))
        assertEquals(
            "Balance · 30 days",
            kurventitel(unstimmig, Zeitraum.TAGE_30, heute, berlin, Englisch),
        )
    }

    @Test
    fun `die Beschreibung nennt beide Staende vollstaendig`() {
        val e = rechne(
            guthaben = 128_457_210_946,
            verlauf = listOf(
                eingang(100_000_000_000, vorTagen = 100.0),
                eingang(28_457_210_946, vorTagen = 3.0),
            ),
        )
        assertEquals(
            "Balance over 30 days: from 1,000.00 to 1,284.57210946 YSR",
            kurvenbeschreibung(e, Zeitraum.TAGE_30, heute, berlin, Englisch),
        )
    }

    @Test
    fun `die Beschreibung eines Teils sagt, seit wann`() {
        assertEquals(
            "Balance since 4 Nov: from 0.000008 to 0.00001 YSR",
            kurvenbeschreibung(teilweise(6.0), Zeitraum.TAGE_30, heute, berlin, Englisch),
        )
    }

    @Test
    fun `die Kurzform und das Wort der Zeitraeume`() {
        assertEquals(listOf("7D", "30D", "1Y"), Zeitraum.entries.map { zeitraumKurz(it, Englisch) })
        assertEquals(
            listOf("7 days", "30 days", "1 year"),
            Zeitraum.entries.map { zeitraumWort(it, Englisch) },
        )
    }
}
