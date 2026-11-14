package com.yskar.wallet.netz

import com.yskar.wallet.netz.Guthabenverlauf.Gegenprobe
import com.yskar.wallet.netz.Guthabenverlauf.Zeitraum
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Das Nachladen des Verlaufs - ohne Netz.
 *
 * Der Transport ist eine Attrappe, die Seiten aus dem Speicher liefert und
 * mitschreibt, was gefragt wurde (REGELN.md, Regel 18). Worum es geht: dass
 * die Schleife aufhoert, wo sie soll, und dass der Bestand dabei nie eine
 * Luecke bekommt - eine Luecke saehe man der Linie nicht an.
 */
class VerlaufsladerTest {

    private val adresse = "ysr17g5ywa80hf2nyjkp59ha6a3ycs0jtsule8a6v4"
    private val tag = 86_400L
    private val jetzt = 1_794_400_000L

    /** Bewegung Nummer [n]: je hoeher, desto juenger. Eine je Stunde. */
    private fun e(n: Int, art: Art = Art.POOL, txid: String = "tx$n") = VerlaufEintrag(
        txid = txid,
        height = 1_000L + n,
        idx = 0,
        zeit = jetzt - (1_000 - n) * 3_600L,
        art = art,
        gegenseite = null,
        anteile = null,
        betrag = 10,
        gebuehr = 0,
        notiz = "",
    )

    /** Die Bewegungen [von] bis [bis], juengste zuerst - wie der Spiegel sie liefert. */
    private fun reihe(von: Int, bis: Int) = (von downTo bis).map { e(it) }

    private fun schluessel(l: List<VerlaufEintrag>) = l.map { Waechter.schluessel(it) }

    private fun seiteJson(eintraege: List<VerlaufEintrag>, weiter: String?): String {
        val zeilen = eintraege.joinToString(",") { x ->
            val art = when (x.art) {
                Art.POOL -> "pool"; Art.EIN -> "in"; Art.AUS -> "out"
                Art.REWARD -> "reward"; Art.UNBEKANNT -> "?"
            }
            // Geformt wie die echte Antwort: Zeit, Betrag und Gebuehr als
            // Zeichenketten.
            """{"txid":"${x.txid}","height":${x.height},"idx":${x.idx},""" +
                """"timestamp":"${x.zeit}","kind":"$art","counterparty":null,""" +
                """"shares":1,"amount":"${x.betrag}","fee":"${x.gebuehr}","memo":""}"""
        }
        val w = if (weiter == null) "null" else "\"$weiter\""
        return """{"eintraege":[$zeilen],"weiter":$w}"""
    }

    /** Liefert je Zeiger eine hinterlegte Seite und schreibt die Anfragen mit. */
    private class Seitenlager(
        private val seiten: Map<String, String>,
        private val totAb: Int = Int.MAX_VALUE,
    ) : Spiegel.Transport {
        val gerufen = ArrayList<String>()
        override fun anfrage(methode: String, url: String, rumpf: String?): Spiegel.Antwort {
            gerufen.add(url)
            if (gerufen.size >= totAb) throw NetzFehler(NetzFehler.Ursache.OFFLINE)
            val zeiger = Regex("[?&]vor=([^&]+)").find(url)?.groupValues?.get(1)
            val text = seiten[zeiger] ?: return Spiegel.Antwort(404, """{"error":"fehlt"}""")
            return Spiegel.Antwort(200, text)
        }
    }

    /**
     * Ein Verlauf von 1.000 Bewegungen in Seiten zu 200, die erste (1000
     * bis 961) ist schon da. Die Zeiger heissen wie die aelteste Bewegung
     * der Seite davor.
     */
    private fun lager(totAb: Int = Int.MAX_VALUE) = Seitenlager(
        mapOf(
            "z960" to seiteJson(reihe(960, 761), "z760"),
            "z760" to seiteJson(reihe(760, 561), "z560"),
            "z560" to seiteJson(reihe(560, 361), "z360"),
            "z360" to seiteJson(reihe(360, 161), "z160"),
            "z160" to seiteJson(reihe(160, 1), null),
        ),
        totAb,
    )

    private val erste = Verlaufsbestand(reihe(1000, 961), "z960")

    // ------------------------------------------------------------- abgedeckt

    @Test
    fun `abgedeckt ist ein Zeitraum, sobald eine geladene Bewegung davor liegt`() {
        // Die aelteste geladene (961) ist 39 Stunden alt.
        assertTrue(Verlaufslader.abgedeckt(erste, jetzt - 1 * tag))
        assertFalse(Verlaufslader.abgedeckt(erste, jetzt - 2 * tag))
    }

    @Test
    fun `ein bis zum Anfang geladener Verlauf deckt jeden Zeitraum ab`() {
        val alles = Verlaufsbestand(reihe(5, 1), null)
        assertTrue(Verlaufslader.abgedeckt(alles, jetzt - 365 * tag))
        assertTrue(Verlaufslader.abgedeckt(Verlaufsbestand(emptyList(), null), 0))
    }

    @Test
    fun `eine Bewegung ohne Zeit belegt nichts`() {
        val ohne = Verlaufsbestand(listOf(e(5).copy(zeit = null)), "z4")
        assertFalse(Verlaufslader.abgedeckt(ohne, jetzt))
    }

    // ------------------------------------------------------------------ lade

    @Test
    fun `ist der Zeitraum schon abgedeckt, wird nichts gefragt`() {
        val l = lager()
        val d = Verlaufslader.lade(Spiegel(l), adresse, erste, jetzt - 1 * tag)
        assertEquals(0, d.seiten)
        assertEquals(emptyList<String>(), l.gerufen)
        assertSame(erste, d.bestand)
        assertNull(d.fehler)
    }

    @Test
    fun `geladen wird, bis eine Bewegung vor dem Beginn liegt, und keine Seite weiter`() {
        // Sieben Tage sind 168 Stunden: Bewegung 831 ist die erste davor.
        // Sie liegt auf der ersten nachgeladenen Seite (960 bis 761).
        val l = lager()
        val d = Verlaufslader.lade(Spiegel(l), adresse, erste, jetzt - 7 * tag)

        assertEquals(1, d.seiten)
        assertEquals(1, l.gerufen.size)
        assertEquals(schluessel(reihe(1000, 761)), schluessel(d.bestand.eintraege))
        assertEquals("z760", d.bestand.weiter)
        assertNull(d.fehler)
    }

    @Test
    fun `gefragt wird mit dem Zeiger und mit 200 je Seite`() {
        val l = lager()
        Verlaufslader.lade(Spiegel(l), adresse, erste, jetzt - 7 * tag)

        val url = l.gerufen.single()
        assertTrue(url, url.contains("/api/v2/account/$adresse/verlauf"))
        assertTrue(url, url.contains("limit=200"))
        assertTrue(url, url.contains("vor=z960"))
    }

    @Test
    fun `am Ende des Verlaufs ist Schluss`() {
        val l = lager()
        val d = Verlaufslader.lade(Spiegel(l), adresse, erste, jetzt - 365 * tag)

        assertEquals(5, d.seiten)
        assertEquals(schluessel(reihe(1000, 1)), schluessel(d.bestand.eintraege))
        assertTrue(d.bestand.bisZumAnfang)
        assertNull(d.fehler)
    }

    @Test
    fun `an der Obergrenze ist Schluss, und der Zeiger steht fuer den naechsten Durchgang`() {
        val l = lager()
        val d = Verlaufslader.lade(Spiegel(l), adresse, erste, jetzt - 365 * tag, hoechstens = 2)

        assertEquals(2, d.seiten)
        assertEquals(2, l.gerufen.size)
        assertEquals(schluessel(reihe(1000, 561)), schluessel(d.bestand.eintraege))
        assertEquals("z560", d.bestand.weiter)

        // Der naechste Durchgang macht dort weiter und kommt an.
        val d2 = Verlaufslader.lade(Spiegel(l), adresse, d.bestand, jetzt - 365 * tag)
        assertEquals(3, d2.seiten)
        assertEquals(schluessel(reihe(1000, 1)), schluessel(d2.bestand.eintraege))
    }

    @Test
    fun `ein Funkloch mittendrin laesst das Geladene stehen`() {
        // Die dritte Anfrage scheitert.
        val l = lager(totAb = 3)
        val d = Verlaufslader.lade(Spiegel(l), adresse, erste, jetzt - 365 * tag)

        assertEquals(2, d.seiten)
        assertEquals(schluessel(reihe(1000, 561)), schluessel(d.bestand.eintraege))
        assertEquals("z560", d.bestand.weiter)
        assertNotNull(d.fehler)
        assertTrue(d.fehler!!.offline)
    }

    @Test
    fun `scheitert schon die erste Anfrage, bleibt der Bestand, wie er war`() {
        val d = Verlaufslader.lade(Spiegel(lager(totAb = 1)), adresse, erste, jetzt - 365 * tag)
        assertEquals(0, d.seiten)
        assertSame(erste, d.bestand)
        assertNotNull(d.fehler)
    }

    @Test
    fun `eine Antwort, die kein Erfolg ist, ist ein Fehler und kein leerer Verlauf`() {
        // Der Zeiger ist im Lager nicht hinterlegt: HTTP 404.
        val bestand = Verlaufsbestand(reihe(1000, 961), "gibtsnicht")
        val d = Verlaufslader.lade(Spiegel(lager()), adresse, bestand, jetzt - 365 * tag)
        assertSame(bestand, d.bestand)
        assertFalse(d.bestand.bisZumAnfang)
        assertEquals(404, d.fehler!!.status)
    }

    @Test
    fun `verschiebt sich die Seitengrenze, steht nichts doppelt da`() {
        // Die nachgeladene Seite beginnt drei Bewegungen zu frueh.
        val l = Seitenlager(mapOf("z960" to seiteJson(reihe(963, 764), null)))
        val d = Verlaufslader.lade(Spiegel(l), adresse, erste, jetzt - 365 * tag)

        val k = schluessel(d.bestand.eintraege)
        assertEquals(k.distinct(), k)
        assertEquals(schluessel(reihe(1000, 764)), k)
    }

    @Test
    fun `kommt derselbe Zeiger zurueck, hoert die Schleife auf`() {
        val l = Seitenlager(mapOf("z960" to seiteJson(reihe(960, 951), "z960")))
        val d = Verlaufslader.lade(Spiegel(l), adresse, erste, jetzt - 365 * tag)

        assertEquals(1, d.seiten)
        assertEquals(1, l.gerufen.size)
        assertFalse(d.bestand.bisZumAnfang)
    }

    // -------------------------------------------------------------- erneuert

    @Test
    fun `ohne Bestand ist die erste Seite der Bestand`() {
        val b = Verlaufslader.erneuert(null, reihe(40, 1), null)
        assertEquals(schluessel(reihe(40, 1)), schluessel(b.eintraege))
        assertTrue(b.bisZumAnfang)

        val c = Verlaufslader.erneuert(Verlaufsbestand(emptyList(), null), reihe(40, 1), "z0")
        assertEquals("z0", c.weiter)
    }

    @Test
    fun `kommt nichts Neues, bleibt alles, wie es war`() {
        val alt = Verlaufsbestand(reihe(1000, 761), "z760")
        val b = Verlaufslader.erneuert(alt, reihe(1000, 961), "z960")

        assertEquals(schluessel(alt.eintraege), schluessel(b.eintraege))
        // Der Zeiger der ersten Seite gilt NICHT: Darunter ist schon geladen.
        assertEquals("z760", b.weiter)
    }

    @Test
    fun `was aus der ersten Seite herausrutscht, bleibt im Bestand`() {
        // Fuenf neue Bewegungen: Die erste Seite ist jetzt 1005 bis 966.
        // 965 bis 961 standen nur auf der alten ersten Seite.
        val alt = Verlaufsbestand(reihe(1000, 961), "z960")
        val b = Verlaufslader.erneuert(alt, reihe(1005, 966), "z965")

        assertEquals(schluessel(reihe(1005, 961)), schluessel(b.eintraege))
        assertEquals("z960", b.weiter)
    }

    @Test
    fun `nachgeladene Seiten ueberstehen die neue erste Seite`() {
        val alt = Verlaufsbestand(reihe(1000, 561), "z560")
        val b = Verlaufslader.erneuert(alt, reihe(1003, 964), "z963")

        assertEquals(schluessel(reihe(1003, 561)), schluessel(b.eintraege))
        assertEquals("z560", b.weiter)
    }

    @Test
    fun `kommt mehr Neues, als auf eine Seite passt, wird neu begonnen`() {
        // 60 neue Bewegungen: Die neue erste Seite (1060 bis 1021) beruehrt
        // den Bestand (bis 1000) nicht. Dazwischen fehlen zwanzig.
        val alt = Verlaufsbestand(reihe(1000, 561), "z560")
        val b = Verlaufslader.erneuert(alt, reihe(1060, 1021), "z1020")

        assertEquals(schluessel(reihe(1060, 1021)), schluessel(b.eintraege))
        assertEquals("z1020", b.weiter)
    }

    @Test
    fun `genau eine Seite Neues schliesst nicht an und beginnt neu`() {
        // Die neue Seite endet bei 1001, der Bestand beginnt bei 1000. Es
        // fehlt nichts - aber das laesst sich nicht belegen.
        val alt = Verlaufsbestand(reihe(1000, 961), "z960")
        val b = Verlaufslader.erneuert(alt, reihe(1040, 1001), "z1000")
        assertEquals(schluessel(reihe(1040, 1001)), schluessel(b.eintraege))
        assertEquals("z1000", b.weiter)
    }

    @Test
    fun `nach einem Umbau der Kette verschwindet, was es nicht mehr gibt`() {
        // 1000 und 999 gibt es nicht mehr; an ihrer Stelle steht eine andere.
        val alt = Verlaufsbestand(reihe(1000, 961), "z960")
        val neu = listOf(e(1000, txid = "anders")) + reihe(998, 962)
        val b = Verlaufslader.erneuert(alt, neu, "z961")

        val k = schluessel(b.eintraege)
        assertFalse(k.contains(Waechter.schluessel(e(1000))))
        assertFalse(k.contains(Waechter.schluessel(e(999))))
        assertTrue(k.contains(Waechter.schluessel(e(1000, txid = "anders"))))
        // 961 stand unter der neuen Seite und bleibt.
        assertEquals(Waechter.schluessel(e(961)), k.last())
        assertEquals("z960", b.weiter)
    }

    @Test
    fun `liegt die Seitengrenze zwischen Aus- und Eingang derselben Ueberweisung, bleibt die zweite Zeile`() {
        // Eine Ueberweisung an sich selbst: zwei Zeilen auf derselben Stelle.
        val aus = e(966, Art.AUS, txid = "selbst")
        val ein = e(966, Art.EIN, txid = "selbst")
        val alt = Verlaufsbestand(reihe(1000, 967) + aus + ein + reihe(965, 961), "z960")

        // Die neue erste Seite endet genau zwischen den beiden.
        val b = Verlaufslader.erneuert(alt, reihe(1004, 967) + aus, "zwischen")

        val k = schluessel(b.eintraege)
        assertTrue(k.contains(Waechter.schluessel(aus)))
        assertTrue(k.contains(Waechter.schluessel(ein)))
        assertEquals(k.distinct(), k)
        // 38 neue und alte von oben, der Ausgang, der Eingang, fuenf darunter.
        assertEquals(45, k.size)
    }

    @Test
    fun `eine leere erste Seite ist ein leerer Bestand`() {
        val alt = Verlaufsbestand(reihe(1000, 961), "z960")
        val b = Verlaufslader.erneuert(alt, emptyList(), null)
        assertEquals(emptyList<VerlaufEintrag>(), b.eintraege)
        assertTrue(b.bisZumAnfang)
    }

    @Test
    fun `der Bestand bleibt nach jedem Erneuern geordnet und ohne Doppelte`() {
        var b = Verlaufsbestand(reihe(1000, 961), "z960")
        // Zwanzigmal kommt eine neue erste Seite, jedes Mal mit 0 bis 9 neuen
        // Bewegungen.
        var oben = 1000
        for (schritt in 0 until 20) {
            oben += schritt % 10
            b = Verlaufslader.erneuert(b, reihe(oben, oben - 39), "z${oben - 40}")
            val hoehen = b.eintraege.map { it.height }
            assertEquals("Schritt $schritt", hoehen.sortedDescending(), hoehen)
            assertEquals("Schritt $schritt", hoehen.distinct(), hoehen)
            // Lueckenlos von oben bis 961.
            assertEquals("Schritt $schritt", (oben - 961 + 1), hoehen.size)
            assertEquals("z960", b.weiter)
        }
    }
}

/**
 * Lader und Rechnung zusammen, an den Seiten unter netz/vollstaendig
 * (geformt wie echte Antworten, Werte erfunden): Die erste Seite ist da,
 * der Rest wird nachgeladen, und am Ende geht die Gegenprobe auf.
 */
class VerlaufsladerAmMitschnittTest {

    private fun text(name: String): String =
        VerlaufsladerAmMitschnittTest::class.java
            .getResourceAsStream("/netz/vollstaendig/$name")!!
            .use { it.readBytes() }
            .toString(Charsets.UTF_8)

    /** Die sechs Seiten, hinterlegt unter dem Zeiger, mit dem die App sie holt. */
    private class Lager(private val seiten: Map<String, String>) : Spiegel.Transport {
        val gerufen = ArrayList<String>()
        override fun anfrage(methode: String, url: String, rumpf: String?): Spiegel.Antwort {
            gerufen.add(url)
            val zeiger = Regex("[?&]vor=([^&]+)").find(url)?.groupValues?.get(1)
            return seiten[zeiger]?.let { Spiegel.Antwort(200, it) }
                ?: Spiegel.Antwort(404, """{"error":"fehlt"}""")
        }
    }

    @Test
    fun `von der ersten Seite bis zum Anfang geladen, kommt die Rechnung bei null an`() {
        val still = Spiegel(Lager(emptyMap()))
        val konto = still.kontoAus(org.json.JSONObject(text("konto.json")))
        val seiten = (1..6).map { still.verlaufSeiteAus(org.json.JSONObject(text("seite_0$it.json"))) }

        // Jede Seite liegt unter dem Zeiger, den die Seite davor genannt hat.
        val lager = Lager((2..6).associate { n -> seiten[n - 2].weiter!! to text("seite_0$n.json") })
        val erste = Verlaufsbestand(seiten[0].eintraege, seiten[0].weiter)
        val jetzt = seiten[0].eintraege.maxOf { it.zeit!! } + 600
        val beginn = Zeitraum.JAHR.beginn(jetzt)

        val d = Verlaufslader.lade(Spiegel(lager), "ysr17g5ywa80hf2nyjkp59ha6a3ycs0jtsule8a6v4", erste, beginn)

        assertNull(d.fehler)
        assertEquals(5, d.seiten)
        assertEquals(1068, d.bestand.eintraege.size)
        assertTrue(d.bestand.bisZumAnfang)

        val e = Guthabenverlauf.rechne(
            konto.guthaben, d.bestand.eintraege, d.bestand.bisZumAnfang, beginn, jetzt,
        )
        assertEquals(Gegenprobe.GehtAuf, e.gegenprobe)
        assertEquals(0L, e.anfangsstand)
    }

    @Test
    fun `sieben Tage brauchen vom Mitschnitt nur so viele Seiten, bis eine Bewegung davor liegt`() {
        val still = Spiegel(Lager(emptyMap()))
        val seiten = (1..6).map { still.verlaufSeiteAus(org.json.JSONObject(text("seite_0$it.json"))) }
        val lager = Lager((2..6).associate { n -> seiten[n - 2].weiter!! to text("seite_0$n.json") })
        val erste = Verlaufsbestand(seiten[0].eintraege, seiten[0].weiter)
        val jetzt = seiten[0].eintraege.maxOf { it.zeit!! } + 600
        val beginn = Zeitraum.TAGE_7.beginn(jetzt)

        val d = Verlaufslader.lade(Spiegel(lager), "ysr17g5ywa80hf2nyjkp59ha6a3ycs0jtsule8a6v4", erste, beginn)

        assertTrue(Verlaufslader.abgedeckt(d.bestand, beginn))
        // Nicht mehr als noetig: Ohne die zuletzt geholte Seite waere der
        // Zeitraum noch nicht abgedeckt gewesen.
        if (d.seiten > 0) {
            val ohneLetzte = seiten.take(d.seiten).flatMap { it.eintraege }
            assertFalse(ohneLetzte.any { it.zeit!! < beginn })
        }
        assertTrue(d.seiten < 5)
    }
}
