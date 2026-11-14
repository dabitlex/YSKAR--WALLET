package com.yskar.wallet.netz

import com.yskar.wallet.kern.Params
import com.yskar.wallet.kern.Tx
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

/**
 * Netzschicht gegen Antworten, wie der Spiegel sie WIRKLICH schickt.
 *
 * Die Dateien unter test/resources/netz sind Mitschnitte von
 * https://yskar.vercel.app, aufgenommen am 10.11.2026 bei Hoehe 8560.
 * `summary.json`, `fees.json` und `tx-410.json` sind unveraendert. In den
 * Antworten zu einem Konto (`account.json`, `verlauf*.json`,
 * `vollstaendig/`) sind seit dem 13.11.2026 die WERTE erfunden - Adressen,
 * Kennungen, Hoehen, Zeiten, Betraege -, damit kein echtes Konto im
 * Repository steht. Feldnamen, Feldtypen und Reihenfolge sind die echten,
 * und um die geht es hier.
 *
 * Sie liegen hier, damit die Tests ohne Netz laufen - ein Test, der ins
 * Internet greift, ist kein Test, sondern eine Wettervorhersage.
 *
 * Geprueft wird vor allem das, woran ein Parser scheitert, der den
 * TypeScript-Typen glaubt: Zahlen, die als Zeichenkette kommen, und
 * umgekehrt.
 */
private fun lade(name: String): JSONObject {
    val text = NetzTest::class.java.getResourceAsStream("/netz/$name")!!
        .use { it.readBytes() }
        .toString(Charsets.UTF_8)
    return JSONObject(text)
}

/** Transport, der feste Antworten liefert - kein Netz im Test. */
private class FesterTransport(
    private val antworten: Map<String, Spiegel.Antwort>,
) : Spiegel.Transport {
    val gerufen = ArrayList<String>()
    override fun anfrage(methode: String, url: String, rumpf: String?): Spiegel.Antwort {
        gerufen.add("$methode $url")
        return antworten.entries.firstOrNull { url.contains(it.key) }?.value
            ?: Spiegel.Antwort(404, """{"error":"nicht hinterlegt"}""")
    }
}

private class ToterTransport : Spiegel.Transport {
    override fun anfrage(methode: String, url: String, rumpf: String?): Spiegel.Antwort =
        throw NetzFehler(NetzFehler.Ursache.OFFLINE)
}

class SpiegelParserTest {

    private val spiegel = Spiegel(FesterTransport(emptyMap()))

    @Test
    fun kopfAusEchterAntwort() {
        val k = spiegel.kopfAus(lade("summary.json"))
        assertEquals(8560L, k.hoehe)
        assertEquals(0, k.mempool)
    }

    @Test
    fun marktAusEchterAntwort() {
        val m = spiegel.marktAus(lade("fees.json"))
        assertEquals(1680L, m.mindestGebuehr)
        assertEquals(10L, m.mindestJeByte)
        assertEquals(8561L, m.hoehe)
        assertEquals(1, m.fassung)
        assertEquals(168, m.basisBytes)
        assertFalse(m.sendepause)
        assertFalse(m.andrang)
        assertEquals(0, m.wartend)

        assertEquals(3, m.stufen.size)
        val normal = m.stufen[Stufe.NORMAL]!!
        assertEquals(1680L, normal.gebuehr)
        assertEquals(1, normal.block)
        assertEquals(10L, normal.jeByte)
    }

    /**
     * Querprobe zwischen Kette und Port: Der Knoten nennt 168 Byte als
     * Groesse einer Ueberweisung ohne Notiz. Genau diese Zahl steht auch
     * in Tx.TRANSFER_BASE_BYTES - unabhaengig voneinander hergeleitet.
     */
    @Test
    fun basisBytesStimmenMitDemPortUeberein() {
        val m = spiegel.marktAus(lade("fees.json"))
        assertEquals(Tx.TRANSFER_BASE_BYTES, m.basisBytes)
        assertEquals(168, Tx.transferBytesAt(1, 0, Int.MAX_VALUE))
    }

    @Test
    fun kontoAusEchterAntwort() {
        val k = spiegel.kontoAus(lade("account.json"))

        assertEquals("ysr17g5ywa80hf2nyjkp59ha6a3ycs0jtsule8a6v4", k.adresse)
        // balance kommt als ZAHL, nextNonce daneben als ZEICHENKETTE.
        assertEquals(82_345_092_565L, k.guthaben)
        assertEquals(19L, k.nonce)
        assertEquals(19L, k.naechsteNonce)
        assertEquals("fullnode", k.wartendQuelle)
        assertTrue(k.wartend.isEmpty())
        assertEquals(40, k.verlauf.size)
        assertEquals("8394:0", k.verlaufWeiter)
    }

    @Test
    fun verlaufseiteAusEchterAntwort() {
        val s = spiegel.verlaufSeiteAus(lade("verlauf.json"))
        assertEquals(5, s.eintraege.size)
        assertEquals("8530:0", s.weiter)

        val erster = s.eintraege[0]
        assertEquals(
            "30be1cd302e3ce5a0689da1e83272d450031d21021fc6e5f38caec8a10085a2a",
            erster.txid,
        )
        assertEquals(8542L, erster.height)
        assertEquals(Art.POOL, erster.art)
        assertTrue(erster.art.eingehend)
        assertEquals(22, erster.anteile)
        assertEquals(3_568_247_832L, erster.betrag)
        assertEquals(0L, erster.gebuehr)
        assertNull(erster.gegenseite)
        // timestamp kommt als Zeichenkette mit Unixsekunden.
        assertEquals(1_794_261_818L, erster.zeit)
        // memo kommt als HEX der Rohbytes.
        assertEquals("YSKAR MAIN", erster.notiz)
    }

    @Test
    fun eingangMitGegenseite() {
        val s = spiegel.verlaufSeiteAus(lade("verlauf.json"))
        val ein = s.eintraege.first { it.art == Art.EIN }
        assertEquals("ysr1j8nnl4kzwdck9u6ddltp8dmvauhg6rez967syq", ein.gegenseite)
        assertEquals(24_368_710L, ein.betrag)
        assertEquals(1680L, ein.gebuehr)
        assertEquals("", ein.notiz)
        assertNull(ein.anteile)
    }

    @Test
    fun unbekannteArtKipptNicht() {
        val o = JSONObject("""{"txid":"ab","height":1,"idx":0,"kind":"irgendwas","amount":"5"}""")
        val e = spiegel.eintragAus(o)
        assertEquals(Art.UNBEKANNT, e.art)
        assertFalse(e.art.eingehend)
        assertEquals(5L, e.betrag)
        assertNull(e.zeit)
    }

    @Test
    fun fehlendeFelderErgebenKeineAusnahme() {
        val e = spiegel.eintragAus(JSONObject("{}"))
        assertEquals("", e.txid)
        assertEquals(0L, e.betrag)
        assertEquals("", e.notiz)
        val k = spiegel.kontoAus(JSONObject("{}"))
        assertEquals(0L, k.guthaben)
        assertTrue(k.verlauf.isEmpty())
    }
}

class SpiegelAblaufTest {

    private fun antwort(datei: String): Spiegel.Antwort {
        val text = NetzTest::class.java.getResourceAsStream("/netz/$datei")!!
            .use { it.readBytes() }.toString(Charsets.UTF_8)
        return Spiegel.Antwort(200, text)
    }

    @Test
    fun lesenGehtAnDenSpiegel() {
        val t = FesterTransport(
            mapOf(
                "/api/v2/summary" to antwort("summary.json"),
                "/api/v2/fees" to antwort("fees.json"),
            )
        )
        val s = Spiegel(t)
        assertEquals(8560L, s.kopf().hoehe)
        assertEquals(1680L, s.markt().mindestGebuehr)
        assertTrue(t.gerufen.all { it.startsWith("GET https://yskar.vercel.app") })
    }

    /**
     * Die Knoten-Erkennung: Der Spiegel antwortet auf POST /api/v2/tx mit
     * HTTP 410 und nennt die Adresse im Feld `fullnode`. So braucht ein
     * Umzug des Knotens keine neue APK.
     */
    @Test
    fun knotenKommtAusDer410Antwort() {
        val t = FesterTransport(
            mapOf("/api/v2/tx" to Spiegel.Antwort(410, lade("tx-410.json").toString()))
        )
        val s = Spiegel(t)
        assertEquals("https://pool.yskar.app", s.knotenAdresse())
        // Einmal ermittelt, dann gemerkt.
        s.knotenAdresse()
        assertEquals(1, t.gerufen.count { it.startsWith("POST") })
    }

    @Test
    fun ohneAuskunftGiltDieVorgabe() {
        val s = Spiegel(ToterTransport())
        assertEquals(Spiegel.KNOTEN_VORGABE, s.knotenAdresse())
    }

    /** Eine Adresse, die nicht wie eine aussieht, wird nicht uebernommen. */
    @Test
    fun unsinnigeKnotenadresseWirdVerworfen() {
        for (boese in listOf(
            "http://pool.yskar.app",
            "https://pool.yskar.app/pfad",
            "javascript:alert(1)",
            "",
        )) {
            val t = FesterTransport(
                mapOf(
                    "/api/v2/tx" to Spiegel.Antwort(
                        410,
                        JSONObject().put("fullnode", boese).toString(),
                    )
                )
            )
            assertEquals(
                "abgelehnt werden muss: $boese",
                Spiegel.KNOTEN_VORGABE,
                Spiegel(t).knotenAdresse(),
            )
        }
    }

    @Test
    fun festerKnotenUeberspringtDieErkennung() {
        val t = FesterTransport(emptyMap())
        val s = Spiegel(t, knotenFest = "https://knoten.test/")
        assertEquals("https://knoten.test", s.knotenAdresse())
        assertTrue("keine Erkennung noetig", t.gerufen.isEmpty())
    }

    @Test
    fun sendenAngenommen() {
        val t = FesterTransport(
            mapOf(
                "/api/v2/tx" to Spiegel.Antwort(
                    200,
                    """{"accepted":true,"txid":"\\xabcdef"}""",
                )
            )
        )
        val e = Spiegel(t, knotenFest = "https://pool.yskar.app").senden("0100")
        assertTrue(e is SendeErgebnis.Angenommen)
        assertEquals("abcdef", (e as SendeErgebnis.Angenommen).txid)
    }

    @Test
    fun sendenAbgelehnt() {
        val t = FesterTransport(
            mapOf(
                "/api/v2/tx" to Spiegel.Antwort(
                    400,
                    """{"accepted":false,"reason":"bad_nonce","detail":"erwartet 24"}""",
                )
            )
        )
        val e = Spiegel(t, knotenFest = "https://pool.yskar.app").senden("0100")
        assertTrue(e is SendeErgebnis.Abgelehnt)
        assertEquals("bad_nonce", (e as SendeErgebnis.Abgelehnt).grund)
        assertEquals("erwartet 24", e.detail)
    }

    @Test
    fun serverfehlerWirdGemeldet() {
        val t = FesterTransport(mapOf("/api/v2/summary" to Spiegel.Antwort(503, "wartung")))
        var gefangen: NetzFehler? = null
        try {
            Spiegel(t).kopf()
        } catch (e: NetzFehler) {
            gefangen = e
        }
        assertNotNull(gefangen)
        assertEquals(503, gefangen!!.status)
        assertFalse(gefangen.offline)
    }

    @Test
    fun ohneNetzKommtOffline() {
        var gefangen: NetzFehler? = null
        try {
            Spiegel(ToterTransport()).kopf()
        } catch (e: NetzFehler) {
            gefangen = e
        }
        assertNotNull(gefangen)
        assertTrue(gefangen!!.offline)
    }
}

class BetragTest {

    private val de = Locale.GERMAN

    @Test
    fun guthabenWirdRichtigGezeigt() {
        assertEquals("1.284,57210946", Betrag.genau(128_457_210_946L, de))
        assertEquals("1.284,57210946 YSR", Betrag.mitEinheit(128_457_210_946L, de))
    }

    @Test
    fun mindestensZweiStellen() {
        assertEquals("5,00", Betrag.genau(5 * 100_000_000L, de))
        assertEquals("1,00", Betrag.genau(100_000_000L, de))
        assertEquals("0,00", Betrag.genau(0L, de))
    }

    @Test
    fun kleinsteStellenBleibenSichtbar() {
        // Ein kleiner Pool-Anteil.
        assertEquals("0,00172839", Betrag.genau(172_839L, de))
        // Eine einzelne kleinste Einheit darf nicht verschwinden.
        assertEquals("0,00000001", Betrag.genau(1L, de))
        // Gebuehr aus der echten Auskunft.
        assertEquals("0,0000168", Betrag.genau(1680L, de))
    }

    @Test
    fun niemalsAufrunden() {
        // 0,999... darf nie als 1,00 erscheinen.
        assertEquals("0,99999999", Betrag.genau(99_999_999L, de))
        assertEquals("0,99", Betrag.stellen(99_999_999L, 2, de))
        assertEquals("0,9999", Betrag.anzeige(99_999_999L, 4, de))
    }

    @Test
    fun feststellenWackeltNicht() {
        assertEquals("1.284,5721", Betrag.stellen(128_457_210_946L, 4, de))
        assertEquals("1.284,57", Betrag.stellen(128_457_210_946L, 2, de))
        assertEquals("1.284", Betrag.stellen(128_457_210_946L, 0, de))
    }

    @Test
    fun eingabeWirdExaktGerechnet() {
        assertEquals(2_550_000_000L, Betrag.ausEingabe("25,5"))
        assertEquals(2_550_000_000L, Betrag.ausEingabe("25.5"))
        assertEquals(100_000_000L, Betrag.ausEingabe("1"))
        assertEquals(1L, Betrag.ausEingabe("0,00000001"))
        // Mehr Stellen als die Kette kennt: abschneiden, nicht runden.
        assertEquals(1L, Betrag.ausEingabe("0,000000019"))
        assertEquals(30_000_000L, Betrag.ausEingabe("0,3"))
    }

    @Test
    fun unsinnErgibtNull() {
        for (s in listOf("", "abc", "-5", "1,2,3", "1e9", " ", "--1")) {
            assertEquals("'$s'", 0L, Betrag.ausEingabe(s))
        }
    }

    @Test
    fun keinGleitkommaFehler() {
        // 0,1 + 0,2 ist als Double nicht 0,3. In ganzen Einheiten schon.
        val summe = Betrag.ausEingabe("0,1") + Betrag.ausEingabe("0,2")
        assertEquals(Betrag.ausEingabe("0,3"), summe)
        assertEquals("0,30", Betrag.genau(summe, de))
    }

    @Test
    fun rundUmUeberDieEingabe() {
        for (text in listOf("25,5", "0,0001", "1.000", "7")) {
            val e = Betrag.ausEingabe(text.replace(".", ""))
            assertEquals(e, Betrag.ausEingabe(Betrag.alsEingabe(e, de)))
        }
    }
}

class NotizTest {

    @Test
    fun hexAusDerKetteWirdText() {
        assertEquals("YSKAR MAIN", Notiz.ausHex("59534b4152204d41494e"))
        assertEquals("Kaffee", Notiz.ausHex("4b6166666565"))
    }

    @Test
    fun leerUndUnlesbaresErgibtLeer() {
        assertEquals("", Notiz.ausHex(null))
        assertEquals("", Notiz.ausHex(""))
        // Kein gueltiges UTF-8.
        assertEquals("", Notiz.ausHex("fffefd"))
        // Kein Hex.
        assertEquals("", Notiz.ausHex("keinhex!"))
    }

    @Test
    fun steuerzeichenWerdenEntschaerft() {
        // "A\nB" -> Zeilenumbruch wird Leerzeichen
        assertEquals("A B", Notiz.ausHex("410a42"))
    }

    @Test
    fun gekuerztWirdNachBytesUndNieImZeichen() {
        assertEquals(32, Notiz.BYTES)

        val lang = "a".repeat(40)
        assertEquals(32, Notiz.kuerzen(lang).length)

        // Umlaut belegt zwei Byte: 16 Umlaute sind genau 32 Byte.
        val umlaute = "ä".repeat(20)
        val kurz = Notiz.kuerzen(umlaute)
        assertEquals(16, kurz.length)
        assertEquals(32, Notiz.bytes(kurz))

        // Ein Emoji belegt vier Byte und darf nie halbiert werden.
        //
        // Achtung, zwei verschiedene Zaehlungen: 32 Byte sind acht Emojis -
        // aber SECHZEHN Kotlin-Zeichen, weil ein Emoji aus einem
        // Ersatzzeichenpaar besteht. Genau diese Verwechslung erzeugt den
        // Fehler, den kuerzen() vermeiden soll.
        val emoji = "😀".repeat(10)
        val k2 = Notiz.kuerzen(emoji)
        assertEquals("Byte", 32, Notiz.bytes(k2))
        assertEquals("Zeichen", 16, k2.length)
        assertEquals("Emojis", 8, k2.codePointCount(0, k2.length))
        // Entscheidend: Es steht kein halbes Zeichen am Ende - sonst kaeme
        // der Text nicht unveraendert ueber die Kette zurueck.
        assertEquals(k2, Notiz.ausHex(com.yskar.wallet.kern.Hex.toHex(Notiz.zuBytes(k2))))
    }

    @Test
    fun rundUmUeberDieKette() {
        val text = "Miete November"
        val bytes = Notiz.zuBytes(text)
        assertEquals(text, Notiz.ausHex(com.yskar.wallet.kern.Hex.toHex(bytes)))
    }
}

class FassungTest {

    private val spiegel = Spiegel(FesterTransport(emptyMap()))

    @Test
    fun auskunftSchlaegtRechnung() {
        val m = spiegel.marktAus(lade("fees.json"))
        val st = Fassung.sendeStand(m, spitze = 8560L)
        assertEquals(Tx.TRANSFER_V1, st.fassung)
        assertFalse(st.pause)
        assertFalse(st.unbekannt)
        assertTrue(st.darfSenden)
    }

    @Test
    fun ohneAuskunftZaehltDieHoehe() {
        val vor = Fassung.sendeStand(null, spitze = (Params.V6_HEIGHT - 10).toLong())
        assertEquals(Tx.TRANSFER_V1, vor.fassung)
        assertTrue(vor.darfSenden)

        val nach = Fassung.sendeStand(null, spitze = Params.V6_HEIGHT.toLong())
        assertEquals(Tx.TRANSFER_V2, nach.fassung)
    }

    @Test
    fun inDerPauseWirdNichtGesendet() {
        val st = Fassung.sendeStand(null, spitze = (Params.V6_HEIGHT - 2).toLong())
        assertTrue(st.pause)
        assertFalse("in der Pause nichts einreichen", st.darfSenden)
    }

    @Test
    fun ohneJedeKenntnisWirdNichtGeraten() {
        val st = Fassung.sendeStand(null, spitze = null)
        assertTrue(st.unbekannt)
        assertFalse(st.darfSenden)
    }

    @Test
    fun gebuehrAusDerEchtenAuskunft() {
        val m = spiegel.marktAus(lade("fees.json"))
        // 168 Byte mal 10 je Byte = 1680 - genau die genannte Mindestgebuehr.
        assertEquals(1680L, Fassung.gebuehrFuer(m, Stufe.NORMAL, 168))
        assertEquals(m.mindestGebuehr, Fassung.gebuehrFuer(m, Stufe.NORMAL, 168))
        // Mit Notiz wird die Ueberweisung groesser und damit teurer.
        assertEquals(1980L, Fassung.gebuehrFuer(m, Stufe.NORMAL, 198))
    }

    @Test
    fun ohneAuskunftDieAlteMindestgebuehr() {
        assertEquals(Params.MIN_FEE, Fassung.gebuehrFuer(null, Stufe.NORMAL, 168))
    }

    @Test
    fun groesseMitNotizUndSkriptKonto() {
        val m = spiegel.marktAus(lade("fees.json"))
        val st = Fassung.sendeStand(m, 8560L)

        assertEquals(168, Fassung.ueberweisungBytes(st, m, notizBytes = 0))
        assertEquals(174, Fassung.ueberweisungBytes(st, m, notizBytes = 6))
    }

    @Test
    fun empfaengerPruefung() {
        val v0 = "ysr17g5ywa80hf2nyjkp59ha6a3ycs0jtsule8a6v4"
        assertEquals(20, Fassung.empfaengerBytes(v0))
        assertNull(Fassung.empfaengerBytes("kein ysr"))

        assertTrue(Fassung.empfaengerErlaubt(20, Tx.TRANSFER_V1))
        assertFalse("ysr1p… erst ab Fassung 2", Fassung.empfaengerErlaubt(33, Tx.TRANSFER_V1))
        assertTrue(Fassung.empfaengerErlaubt(33, Tx.TRANSFER_V2))
    }
}

/** Nur fuer den Ressourcenzugriff der anderen Klassen. */
class NetzTest
