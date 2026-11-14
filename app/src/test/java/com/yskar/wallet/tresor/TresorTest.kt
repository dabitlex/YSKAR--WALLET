package com.yskar.wallet.tresor

import com.yskar.wallet.kern.Hex
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.SecureRandom

/** Tresorakte im Arbeitsspeicher - ersetzt die Datei im Test. */
private class SpeicherAblage(var inhalt: String? = null) : Tresor.AktenAblage {
    var schreibzugriffe = 0
    override fun lesen(): String? = inhalt
    override fun schreiben(text: String) {
        inhalt = text
        schreibzugriffe++
    }

    override fun loeschen() {
        inhalt = null
    }
}

private class SpeicherBremse : Bremse.Ablage {
    var stand = Bremse.Stand()
    override fun lesen(): Bremse.Stand = stand
    override fun schreiben(stand: Bremse.Stand) {
        this.stand = stand
    }
}

private const val WOERTER =
    "abandon abandon abandon abandon abandon abandon " +
        "abandon abandon abandon abandon abandon about"

private const val ADRESSE = "ysr1pcy6c8vw2fpp49qh4pg3w348qz6nn6vw2fg7f0"

/**
 * Im Test die kleinste Zahl, die [Umschlag] zulaesst.
 *
 * Nicht weniger: Die Untergrenze von 100.000 Runden ist eine echte
 * Schutzregel, keine Formalie - sie verhindert, dass irgendwo im Code eine
 * schwache Ableitung durchrutscht. Ein Test, der sie umgeht, pruefte etwas
 * anderes als den Betrieb. Die Faelle laufen damit in gut einer Sekunde.
 */
private const val TEST_RUNDEN = 100_000

private fun pin(s: String) = s.toCharArray()

private fun tresorBauen(
    ablage: SpeicherAblage = SpeicherAblage(),
    pfefferGeheimnis: String = "geraet-A",
    bremseAblage: SpeicherBremse = SpeicherBremse(),
    uhr: () -> Long = { 1_000_000L },
): Tresor = Tresor(
    ablage = ablage,
    pfeffer = FesterPfeffer(pfefferGeheimnis.toByteArray()),
    bremse = Bremse(bremseAblage, uhr),
    zufall = SecureRandom(),
    runden = TEST_RUNDEN,
)

class UmschlagTest {

    private val salz = ByteArray(16) { it.toByte() }
    private val iv = ByteArray(12) { (it + 100).toByte() }
    private val pfeffer = ByteArray(32) { (it * 7).toByte() }

    @Test
    fun hkdfIstBestimmt() {
        val a = Umschlag.hkdfSha256(ByteArray(32), salz, "info".toByteArray())
        val b = Umschlag.hkdfSha256(ByteArray(32), salz, "info".toByteArray())
        assertEquals(Hex.toHex(a), Hex.toHex(b))
        assertEquals(32, a.size)

        val c = Umschlag.hkdfSha256(ByteArray(32), salz, "andere".toByteArray())
        assertFalse("info muss den Schluessel aendern", a.contentEquals(c))
    }

    @Test
    fun versiegelnUndOeffnen() {
        val k = Umschlag.tresorSchluessel(pin("123456"), salz, pfeffer, TEST_RUNDEN)
        val klar = "zwoelf woerter".toByteArray()
        val geheim = Umschlag.versiegeln(k, iv, klar)

        assertFalse("Geheimtext darf nicht der Klartext sein", geheim.contentEquals(klar))
        assertEquals("GCM haengt 16 Byte Pruefwert an", klar.size + 16, geheim.size)
        assertArrayEquals(klar, Umschlag.oeffnen(k, iv, geheim))
    }

    @Test
    fun falschePinOeffnetNicht() {
        val k = Umschlag.tresorSchluessel(pin("123456"), salz, pfeffer, TEST_RUNDEN)
        val falsch = Umschlag.tresorSchluessel(pin("123457"), salz, pfeffer, TEST_RUNDEN)
        val geheim = Umschlag.versiegeln(k, iv, "geheim".toByteArray())
        assertNull(Umschlag.oeffnen(falsch, iv, geheim))
    }

    /**
     * Die zentrale Eigenschaft: Ohne den Pfeffer dieses Geraets nuetzt die
     * richtige PIN nichts.
     */
    @Test
    fun andererPfefferOeffnetNicht() {
        val hier = Umschlag.tresorSchluessel(pin("123456"), salz, pfeffer, TEST_RUNDEN)
        val fremd = Umschlag.tresorSchluessel(
            pin("123456"), salz, ByteArray(32) { 42 }, TEST_RUNDEN,
        )
        assertFalse(hier.contentEquals(fremd))

        val geheim = Umschlag.versiegeln(hier, iv, "geheim".toByteArray())
        assertNull("gleiche PIN, anderes Geraet - darf nicht aufgehen",
            Umschlag.oeffnen(fremd, iv, geheim))
    }

    @Test
    fun verfaelschterGeheimtextOeffnetNicht() {
        val k = Umschlag.tresorSchluessel(pin("123456"), salz, pfeffer, TEST_RUNDEN)
        val geheim = Umschlag.versiegeln(k, iv, "geheim".toByteArray())

        val kaputt = geheim.copyOf()
        kaputt[0] = (kaputt[0].toInt() xor 1).toByte()
        assertNull("AES-GCM muss die Verfaelschung bemerken", Umschlag.oeffnen(k, iv, kaputt))

        val andererIv = iv.copyOf().also { it[0] = (it[0].toInt() xor 1).toByte() }
        assertNull(Umschlag.oeffnen(k, andererIv, geheim))
    }

    @Test
    fun salzAendertDenSchluessel() {
        val a = Umschlag.tresorSchluessel(pin("123456"), salz, pfeffer, TEST_RUNDEN)
        val anderesSalz = salz.copyOf().also { it[0] = 99 }
        val b = Umschlag.tresorSchluessel(pin("123456"), anderesSalz, pfeffer, TEST_RUNDEN)
        assertFalse(a.contentEquals(b))
    }

    @Test
    fun zuWenigRundenWirdAbgelehnt() {
        var geworfen = false
        try {
            Umschlag.tresorSchluessel(pin("123456"), salz, pfeffer, 1)
        } catch (e: IllegalArgumentException) {
            geworfen = true
        }
        assertTrue("eine zu schwache Ableitung darf nicht durchrutschen", geworfen)
    }
}

class BremseTest {

    @Test
    fun ersteVierVersucheSindFrei() {
        for (i in 0 until Bremse.FREI) {
            assertEquals("Versuch $i", 0L, Bremse.wartezeitFuer(i))
        }
    }

    @Test
    fun wartezeitVerdoppeltSichUndHatEinenDeckel() {
        assertEquals(30L, Bremse.wartezeitFuer(5))
        assertEquals(60L, Bremse.wartezeitFuer(6))
        assertEquals(120L, Bremse.wartezeitFuer(7))
        assertEquals(240L, Bremse.wartezeitFuer(8))
        assertEquals(480L, Bremse.wartezeitFuer(9))
        assertEquals(900L, Bremse.wartezeitFuer(10))
        assertEquals("auch nach sehr vielen Versuchen kein Ueberlauf",
            900L, Bremse.wartezeitFuer(1000))
    }

    @Test
    fun sperreLaeuftAb() {
        var jetzt = 1_000_000L
        val b = Bremse(SpeicherBremse(), { jetzt })

        repeat(5) { b.fehlversuch() }
        assertEquals(30L, b.wartezeit())
        assertFalse(b.darfVersuchen())

        jetzt += 29_000
        assertEquals(1L, b.wartezeit())

        jetzt += 1_000
        assertEquals(0L, b.wartezeit())
        assertTrue(b.darfVersuchen())
    }

    @Test
    fun erfolgSetztAllesZurueck() {
        val b = Bremse(SpeicherBremse(), { 1_000_000L })
        repeat(7) { b.fehlversuch() }
        assertEquals(7, b.fehlversuche())
        b.erfolg()
        assertEquals(0, b.fehlversuche())
        assertEquals(0L, b.wartezeit())
    }
}

class TresorAkteTest {

    private fun beispiel(bio: Boolean) = TresorAkte(
        version = 1,
        salz = ByteArray(16) { it.toByte() },
        iv = ByteArray(12) { it.toByte() },
        geheim = ByteArray(40) { (it * 3).toByte() },
        runden = 400_000,
        adresse = ADRESSE,
        erstellt = "2026-11-09T22:00:00Z",
        bio = if (bio) TresorAkte.BioHuelle(ByteArray(12) { 7 }, ByteArray(48) { 9 }) else null,
    )

    @Test
    fun jsonRundUmOhneBiometrie() {
        val a = beispiel(false)
        assertEquals(a, TresorAkte.ausJson(a.alsJson()))
    }

    @Test
    fun jsonRundUmMitBiometrie() {
        val a = beispiel(true)
        val zurueck = TresorAkte.ausJson(a.alsJson())
        assertEquals(a, zurueck)
        assertNotNull(zurueck.bio)
    }

    @Test
    fun fremdeFassungWirdAbgelehnt() {
        val text = beispiel(false).alsJson().replace("\"version\": 1", "\"version\": 2")
        var meldung: String? = null
        try {
            TresorAkte.ausJson(text)
        } catch (e: IllegalArgumentException) {
            meldung = e.message
        }
        assertNotNull("eine unbekannte Fassung muss auffallen", meldung)
        assertTrue(meldung!!.contains("Fassung 2"))
    }

    @Test
    fun falscheSalzlaengeWirdAbgelehnt() {
        val a = beispiel(false)
        val kaputt = a.alsJson().replace(
            "\"salz\": \"" + java.util.Base64.getEncoder().encodeToString(a.salz) + "\"",
            "\"salz\": \"AAAA\"",
        )
        var geworfen = false
        try {
            TresorAkte.ausJson(kaputt)
        } catch (e: IllegalArgumentException) {
            geworfen = true
        }
        assertTrue(geworfen)
    }

    @Test
    fun toStringZeigtKeinSchluesselmaterial() {
        val s = beispiel(true).toString()
        assertTrue(s.contains(ADRESSE))
        assertFalse(s.contains("geheim"))
        assertFalse(s.contains("salz"))
    }
}

class TresorAblaufTest {

    @Test
    fun einrichtenUndOeffnen() {
        val t = tresorBauen()
        assertFalse(t.istEingerichtet())
        assertNull(t.adresse())

        val ein = t.einrichten(WOERTER, pin("123456"))
        assertTrue("Einrichten muss gelingen: $ein", ein is Tresor.Ergebnis.Offen)
        assertEquals(ADRESSE, (ein as Tresor.Ergebnis.Offen).adresse)

        assertTrue(t.istEingerichtet())
        assertEquals("Adresse muss ohne PIN sichtbar sein", ADRESSE, t.adresse())

        val auf = t.oeffnen(pin("123456"))
        assertTrue("Oeffnen muss gelingen: $auf", auf is Tresor.Ergebnis.Offen)
        assertEquals(WOERTER, String((auf as Tresor.Ergebnis.Offen).woerter, Charsets.UTF_8))
    }

    @Test
    fun dieWoerterStehenNichtInDerDatei() {
        val ablage = SpeicherAblage()
        tresorBauen(ablage).einrichten(WOERTER, pin("123456"))

        val datei = ablage.inhalt!!
        assertFalse("Merkwoerter duerfen nirgends im Klartext stehen", datei.contains("abandon"))
        assertFalse(datei.contains("about"))
        assertFalse("die PIN schon gar nicht", datei.contains("123456"))
        assertTrue("die Adresse ist oeffentlich und darf drinstehen", datei.contains(ADRESSE))
    }

    @Test
    fun falschePinOeffnetNichtUndZaehlt() {
        val t = tresorBauen()
        t.einrichten(WOERTER, pin("123456"))

        val falsch = t.oeffnen(pin("654321"))
        assertTrue(falsch is Tresor.Ergebnis.FalschePin)
        assertEquals(1, (falsch as Tresor.Ergebnis.FalschePin).fehlversuche)
        assertEquals("die ersten Versuche ohne Wartezeit", 0L, falsch.wartezeit)

        // Nach dem richtigen Versuch ist der Zaehler wieder bei null.
        assertTrue(t.oeffnen(pin("123456")) is Tresor.Ergebnis.Offen)
        val wieder = t.oeffnen(pin("000000"))
        assertEquals(1, (wieder as Tresor.Ergebnis.FalschePin).fehlversuche)
    }

    @Test
    fun nachFuenfFehlversuchenGreiftDieBremse() {
        var jetzt = 1_000_000L
        val t = tresorBauen(uhr = { jetzt })
        t.einrichten(WOERTER, pin("123456"))

        repeat(5) { t.oeffnen(pin("000000")) }

        val gesperrt = t.oeffnen(pin("123456"))
        assertTrue("auch die richtige PIN muss warten: $gesperrt",
            gesperrt is Tresor.Ergebnis.Gesperrt)
        assertEquals(30L, (gesperrt as Tresor.Ergebnis.Gesperrt).wartezeit)

        jetzt += 30_000
        assertTrue(t.oeffnen(pin("123456")) is Tresor.Ergebnis.Offen)
    }

    /**
     * Der Angriff, gegen den der Pfeffer gebaut ist: Tresordatei kopiert,
     * PIN bekannt, anderes Geraet.
     */
    @Test
    fun kopierteDateiOeffnetAufAnderemGeraetNicht() {
        val ablage = SpeicherAblage()
        tresorBauen(ablage, pfefferGeheimnis = "geraet-A").einrichten(WOERTER, pin("123456"))

        val kopie = SpeicherAblage(ablage.inhalt)
        val fremd = tresorBauen(kopie, pfefferGeheimnis = "geraet-B")

        val versuch = fremd.oeffnen(pin("123456"))
        assertTrue(
            "mit richtiger PIN, aber fremdem Geraet darf der Tresor nicht aufgehen: $versuch",
            versuch is Tresor.Ergebnis.FalschePin,
        )
    }

    @Test
    fun einZweiterTresorUeberschreibtDenErstenNicht() {
        val t = tresorBauen()
        t.einrichten(WOERTER, pin("123456"))

        val andere = "legal winner thank year wave sausage worth useful legal winner thank yellow"
        val zweiter = t.einrichten(andere, pin("999999"))
        assertTrue(zweiter is Tresor.Ergebnis.Fehler)

        // Der erste ist unversehrt.
        assertEquals(ADRESSE, t.adresse())
        assertTrue(t.oeffnen(pin("123456")) is Tresor.Ergebnis.Offen)
    }

    @Test
    fun ungueltigeWoerterWerdenAbgelehnt() {
        val t = tresorBauen()
        val schlecht = "abandon abandon abandon abandon abandon abandon " +
            "abandon abandon abandon abandon abandon abandon"
        val e = t.einrichten(schlecht, pin("123456"))
        assertTrue(e is Tresor.Ergebnis.Fehler)
        assertTrue((e as Tresor.Ergebnis.Fehler).grund.contains("Pruefsumme"))
        assertFalse(t.istEingerichtet())
    }

    @Test
    fun zuKurzePinWirdAbgelehnt() {
        val t = tresorBauen()
        assertTrue(t.einrichten(WOERTER, pin("12")) is Tresor.Ergebnis.Fehler)
        assertFalse(t.istEingerichtet())
    }

    @Test
    fun pinAendern() {
        val t = tresorBauen()
        t.einrichten(WOERTER, pin("123456"))

        assertTrue(t.pinAendern(pin("111111"), pin("222222")) is Tresor.Ergebnis.FalschePin)
        assertTrue(t.pinAendern(pin("123456"), pin("654321")) is Tresor.Ergebnis.Offen)

        assertTrue("die alte PIN darf nicht mehr gelten",
            t.oeffnen(pin("123456")) is Tresor.Ergebnis.FalschePin)

        val auf = t.oeffnen(pin("654321"))
        assertTrue(auf is Tresor.Ergebnis.Offen)
        assertEquals(WOERTER, String((auf as Tresor.Ergebnis.Offen).woerter, Charsets.UTF_8))
        assertEquals("die Adresse bleibt dieselbe", ADRESSE, t.adresse())
    }

    @Test
    fun pinAendernErneuertSalzUndIv() {
        val ablage = SpeicherAblage()
        val t = tresorBauen(ablage)
        t.einrichten(WOERTER, pin("123456"))
        val vorher = TresorAkte.ausJson(ablage.inhalt!!)

        t.pinAendern(pin("123456"), pin("654321"))
        val nachher = TresorAkte.ausJson(ablage.inhalt!!)

        assertFalse("frisches Salz", vorher.salz.contentEquals(nachher.salz))
        assertFalse("frischer IV", vorher.iv.contentEquals(nachher.iv))
        assertFalse(vorher.geheim.contentEquals(nachher.geheim))
    }

    @Test
    fun loeschenEntferntAlles() {
        val ablage = SpeicherAblage()
        val t = tresorBauen(ablage)
        t.einrichten(WOERTER, pin("123456"))

        t.loeschen()
        assertFalse(t.istEingerichtet())
        assertNull(t.adresse())
        assertNull(ablage.inhalt)
        assertTrue(t.oeffnen(pin("123456")) is Tresor.Ergebnis.KeinTresor)
    }

    @Test
    fun kaputteDateiMeldetSichStattZuSchweigen() {
        val ablage = SpeicherAblage("{ das ist kein JSON")
        val t = tresorBauen(ablage)

        assertFalse(t.istEingerichtet())
        assertNotNull("der Fehler muss sichtbar sein", t.aktenFehler())

        val e = t.oeffnen(pin("123456"))
        assertTrue("nicht KeinTresor - das waere eine Luege: $e", e is Tresor.Ergebnis.Fehler)
    }

    @Test
    fun jederTresorBekommtEigenesSalz() {
        val a = SpeicherAblage()
        val b = SpeicherAblage()
        tresorBauen(a).einrichten(WOERTER, pin("123456"))
        tresorBauen(b).einrichten(WOERTER, pin("123456"))

        val sa = TresorAkte.ausJson(a.inhalt!!)
        val sb = TresorAkte.ausJson(b.inhalt!!)
        assertFalse("zwei Tresore duerfen nie dasselbe Salz haben",
            sa.salz.contentEquals(sb.salz))
        assertFalse(sa.geheim.contentEquals(sb.geheim))
        assertEquals("gleiche Woerter, gleiche Adresse", sa.adresse, sb.adresse)
    }

    @Test
    fun echteRundenzahlIstHinterlegt() {
        // Der Test laeuft mit wenigen Runden; der Betrieb nicht.
        assertEquals(400_000, Umschlag.RUNDEN)
        val ablage = SpeicherAblage()
        Tresor(
            ablage = ablage,
            pfeffer = FesterPfeffer("geraet-A".toByteArray()),
            bremse = Bremse(SpeicherBremse()),
        ).einrichten(WOERTER, pin("123456"))
        assertEquals(400_000, TresorAkte.ausJson(ablage.inhalt!!).runden)
    }
}
