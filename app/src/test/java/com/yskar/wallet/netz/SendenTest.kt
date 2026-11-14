package com.yskar.wallet.netz

import com.yskar.wallet.kern.Address
import com.yskar.wallet.kern.Hex
import com.yskar.wallet.kern.Params
import com.yskar.wallet.kern.Tx
import com.yskar.wallet.kern.Wallet
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Senden - geprüft, ohne irgendetwas zu senden.
 *
 * KEIN TEST HIER SPRICHT MIT DEM ECHTEN NETZ. Der Transport ist eine
 * Attrappe, die die eingereichten Bytes festhält, statt sie wegzuschicken;
 * genau diese Bytes werden danach auseinandergenommen und geprüft.
 *
 * Das ist der Punkt: Die Oberfläche zeigt dem Nutzer einen Betrag und einen
 * Empfänger. Dieser Test belegt, dass GENAU DAS unterschrieben wird und
 * nichts anderes.
 */
private const val WOERTER =
    "abandon abandon abandon abandon abandon abandon " +
        "abandon abandon abandon abandon abandon about"

private const val EIGENE = "ysr1pcy6c8vw2fpp49qh4pg3w348qz6nn6vw2fg7f0"
private const val EMPFAENGER = "ysr135sh9v4zxsp9rc8hx3st4alkskrh7mf6f73h8q"

/** Hält fest, was eingereicht wurde - und schickt nichts. */
private class Briefkasten(
    private val antwort: String = """{"accepted":true,"txid":"abcdef"}""",
    private val status: Int = 200,
) : Spiegel.Transport {
    var eingereicht: String? = null
    var aufrufe = 0

    override fun anfrage(methode: String, url: String, rumpf: String?): Spiegel.Antwort {
        aufrufe++
        if (rumpf != null && rumpf.contains("\"raw\"")) {
            eingereicht = JSONObject(rumpf).optString("raw")
        }
        return Spiegel.Antwort(status, antwort)
    }
}

private fun spiegelMit(t: Spiegel.Transport) =
    Spiegel(t, knotenFest = "https://pool.yskar.app")

/**
 * Die Grundgröße gehört zur Fassung, und der Knoten nennt sie passend:
 * 168 Byte in Fassung 1, 174 in Fassung 2 (flags, validFrom, Versionsbyte).
 * Ein Markt mit Fassung 2 und 168 Byte wäre in sich widersprüchlich und
 * ergäbe eine zu niedrige Gebühr - darum hier abhängig von der Fassung.
 */
private fun markt(
    hoehe: Long = 8561,
    fassung: Int = 1,
    sendepause: Boolean = false,
    basisBytes: Int = if (fassung >= 2) Tx.TRANSFER_V2_BASE_BYTES else Tx.TRANSFER_BASE_BYTES,
) = Markt(
    mindestGebuehr = 1680,
    mindestJeByte = 10,
    wartend = 0,
    andrang = false,
    stufen = mapOf(
        Stufe.LANGSAM to StufenPreis(1680, 1, 10),
        Stufe.NORMAL to StufenPreis(1680, 1, 10),
        Stufe.SCHNELL to StufenPreis(1680, 1, 10),
    ),
    hoehe = hoehe,
    fassung = fassung,
    basisBytes = basisBytes,
    sendepause = sendepause,
)

private fun konto(
    guthaben: Long = 500_000_000L,
    nonce: Long = 23,
    wartend: List<Wartend> = emptyList(),
) = Konto(
    adresse = EIGENE,
    guthaben = guthaben,
    nonce = nonce,
    naechsteNonce = nonce,
    wartend = wartend,
    wartendQuelle = "fullnode",
    verlauf = emptyList(),
    verlaufWeiter = null,
)

private fun pruefe(
    empfaenger: String = EMPFAENGER,
    betrag: String = "1",
    notiz: String = "",
    k: Konto? = konto(),
    m: Markt? = markt(),
    spitze: Long? = 8560,
) = Senden.pruefe(EIGENE, k, m, spitze, empfaenger, betrag, notiz)

class SendenPruefungTest {

    @Test
    fun gueltigeAngabenErgebenEinenEntwurf() {
        val p = pruefe(betrag = "1,5", notiz = "Miete")
        assertTrue("sollte bereit sein: $p", p is Senden.Pruefung.Bereit)
        val e = (p as Senden.Pruefung.Bereit).entwurf

        assertEquals(EMPFAENGER, e.empfaenger)
        assertEquals(150_000_000L, e.betrag)
        assertEquals(23L, e.nonce)
        assertEquals(Tx.TRANSFER_V1, e.fassung)
        // 168 Byte Grundgröße plus fünf Byte Notiz, mal 10 je Byte.
        assertEquals(173, e.bytes)
        assertEquals(1730L, e.gebuehr)
        assertEquals(150_001_730L, e.gesamt)
        assertEquals("Miete", e.notizText)
    }

    @Test
    fun ohneNotizGiltDieGrundgroesse() {
        val e = (pruefe() as Senden.Pruefung.Bereit).entwurf
        assertEquals(168, e.bytes)
        assertEquals(1680L, e.gebuehr)
        assertEquals(Tx.TRANSFER_BASE_BYTES, e.bytes)
    }

    private fun grund(p: Senden.Pruefung): String {
        assertTrue("sollte abgelehnt werden, war: $p", p is Senden.Pruefung.Problem)
        return (p as Senden.Pruefung.Problem).grund
    }

    @Test
    fun ungueltigeAdresseWirdAbgelehnt() {
        assertTrue(grund(pruefe(empfaenger = "")).contains("fehlt"))
        assertTrue(grund(pruefe(empfaenger = "kein ysr")).contains("gültig"))
        // Ein gekipptes Zeichen muss die Prüfsumme bemerken.
        val kaputt = EMPFAENGER.dropLast(1) + if (EMPFAENGER.last() == 'q') 'p' else 'q'
        assertTrue(grund(pruefe(empfaenger = kaputt)).contains("gültig"))
    }

    @Test
    fun eigeneAdresseWirdAbgelehnt() {
        assertTrue(grund(pruefe(empfaenger = EIGENE)).contains("eigene Adresse"))
    }

    @Test
    fun betragMussLesbarUndPositivSein() {
        for (b in listOf("", "0", "abc", "-5", "0,0")) {
            assertTrue("Betrag '$b'", grund(pruefe(betrag = b)).contains("Betrag"))
        }
    }

    @Test
    fun staubgrenzeGiltAbDerHoehe() {
        // 99 Einheiten liegen unter DUST_LIMIT (100).
        val p = pruefe(betrag = "0,00000099")
        assertTrue(grund(p).contains("Staubgrenze"))
        assertEquals(100L, Params.DUST_LIMIT)
    }

    @Test
    fun zuLangeNotizWirdAbgelehnt() {
        val lang = "a".repeat(33)
        assertTrue(grund(pruefe(notiz = lang)).contains("Byte"))
        // 32 Byte sind noch erlaubt.
        assertTrue(pruefe(notiz = "a".repeat(32)) is Senden.Pruefung.Bereit)
        // Umlaute zählen zwei: 17 davon sind 34 Byte.
        assertTrue(grund(pruefe(notiz = "ä".repeat(17))).contains("Byte"))
    }

    @Test
    fun fehlendeDeckungWirdAbgelehnt() {
        val p = pruefe(betrag = "6", k = konto(guthaben = 500_000_000L))
        val g = grund(p)
        assertTrue(g.contains("Guthaben reicht nicht"))
        assertTrue("der Fehlbetrag muss dastehen", g.contains("fehlen"))
    }

    @Test
    fun knappDarunterGehtNoch() {
        // Guthaben genau Betrag plus Gebühr.
        val k = konto(guthaben = 100_000_000L + 1680L)
        assertTrue(pruefe(betrag = "1", k = k) is Senden.Pruefung.Bereit)
        // Eine Einheit weniger reicht nicht mehr.
        val knapp = konto(guthaben = 100_000_000L + 1679L)
        assertTrue(grund(pruefe(betrag = "1", k = knapp)).contains("Guthaben"))
    }

    @Test
    fun wartendeZahlungBlockiert() {
        val w = Wartend("ab", Art.AUS, EIGENE, EMPFAENGER, 1, 1, 23, "")
        assertTrue(grund(pruefe(k = konto(wartend = listOf(w)))).contains("Nonce"))
    }

    @Test
    fun ohneKontostandWirdNichtGesendet() {
        assertTrue(grund(pruefe(k = null)).contains("Kontostand"))
    }

    @Test
    fun inDerSendepauseWirdNichtGesendet() {
        val p = pruefe(m = markt(sendepause = true))
        assertTrue(grund(p).contains("Konsensfassung"))
    }

    @Test
    fun ohneJedeKenntnisWirdNichtGesendet() {
        assertTrue(grund(pruefe(m = null, spitze = null)).contains("nicht raten"))
    }

    @Test
    fun skriptKontoErstAbFassungZwei() {
        val skript = Address.encode(Address.rawFromScript(ByteArray(8)))
        assertTrue(grund(pruefe(empfaenger = skript)).contains("Konsensfassung 6"))

        // Ab Fassung 2 geht es.
        val p = pruefe(empfaenger = skript, m = markt(fassung = 2), spitze = 10_000)
        assertTrue("ab Fassung 2 erlaubt: $p", p is Senden.Pruefung.Bereit)
        val e = (p as Senden.Pruefung.Bereit).entwurf
        assertEquals(Tx.TRANSFER_V2, e.fassung)
        // 12 Byte mehr als an eine gewöhnliche Adresse.
        assertEquals(Tx.TRANSFER_V2_BASE_BYTES + 12, e.bytes)
    }
}

class SendenUnterschriftTest {

    private fun entwurf(betrag: String = "1,5", notiz: String = "Miete"): Senden.Entwurf =
        (pruefe(betrag = betrag, notiz = notiz) as Senden.Pruefung.Bereit).entwurf

    private val woerter = WOERTER.toByteArray(Charsets.UTF_8)

    @Test
    fun dieEingereichtenBytesSindGenauDasVersprochene() {
        val kasten = Briefkasten()
        val e = entwurf()

        val r = Senden.unterschreibenUndSenden(e, woerter, EIGENE, spiegelMit(kasten))
        assertTrue("angenommen erwartet, war: $r", r is Senden.Ergebnis.Angenommen)
        assertEquals("abcdef", (r as Senden.Ergebnis.Angenommen).txid)

        val roh = kasten.eingereicht
        assertNotNull("es muss etwas eingereicht worden sein", roh)

        // Jetzt die Gegenprobe: Was steht wirklich drin?
        val tx = Tx.deserializeTransfer(Hex.fromHex(roh!!))
        val kp = Wallet.fromMnemonic(WOERTER)

        assertArrayEqualsHex("Absender", kp.addressRaw, tx.from)
        assertArrayEqualsHex("Empfänger", Address.decode(EMPFAENGER), tx.to)
        assertEquals("Betrag", 150_000_000L, tx.amount)
        assertEquals("Gebühr", e.gebuehr, tx.fee)
        assertEquals("Nonce", 23L, tx.nonce)
        assertEquals("Fassung", Tx.TRANSFER_V1, tx.version)
        assertEquals("Notiz", "Miete", String(tx.memo, Charsets.UTF_8))
        assertArrayEqualsHex("Pubkey", kp.publicKey, tx.publicKey)
        assertTrue("Signatur muss gelten", Tx.verifyOwnSignature(tx))
    }

    private fun assertArrayEqualsHex(was: String, soll: ByteArray, ist: ByteArray) {
        assertEquals(was, Hex.toHex(soll), Hex.toHex(ist))
    }

    @Test
    fun eineAndereAdresseStopptAlles() {
        val kasten = Briefkasten()
        val r = Senden.unterschreibenUndSenden(
            entwurf(), woerter, EMPFAENGER, spiegelMit(kasten),
        )
        assertTrue("muss scheitern: $r", r is Senden.Ergebnis.Fehler)
        assertTrue((r as Senden.Ergebnis.Fehler).grund.contains("passt nicht"))
        assertNull("es darf NICHTS eingereicht worden sein", kasten.eingereicht)
    }

    @Test
    fun unbrauchbareWoerterStoppenAlles() {
        val kasten = Briefkasten()
        val r = Senden.unterschreibenUndSenden(
            entwurf(), "kein gueltiger satz".toByteArray(), EIGENE, spiegelMit(kasten),
        )
        assertTrue(r is Senden.Ergebnis.Fehler)
        assertNull(kasten.eingereicht)
    }

    @Test
    fun ablehnungWirdUebersetzt() {
        val kasten = Briefkasten(
            antwort = """{"accepted":false,"reason":"bad_nonce","detail":"erwartet 24"}""",
            status = 400,
        )
        val r = Senden.unterschreibenUndSenden(entwurf(), woerter, EIGENE, spiegelMit(kasten))
        assertTrue(r is Senden.Ergebnis.Abgelehnt)
        val a = r as Senden.Ergebnis.Abgelehnt
        assertEquals("bad_nonce", a.code)
        assertTrue(a.klartext.contains("laufende Nummer"))
        assertEquals("erwartet 24", a.detail)
    }

    @Test
    fun ohneVerbindungWirdNichtsBehauptet() {
        val tot = object : Spiegel.Transport {
            override fun anfrage(methode: String, url: String, rumpf: String?) =
                throw NetzFehler(NetzFehler.Ursache.OFFLINE)
        }
        val r = Senden.unterschreibenUndSenden(entwurf(), woerter, EIGENE, spiegelMit(tot))
        assertTrue(r is Senden.Ergebnis.Fehler)
        assertTrue((r as Senden.Ergebnis.Fehler).grund.contains("NICHT eingereicht"))
    }

    @Test
    fun zweiGleicheEntwuerfeErgebenDieselbenBytes() {
        // Ed25519 ist deterministisch - dieselbe Zahlung zweimal gebaut
        // ergibt Byte für Byte dasselbe. Das ist kein Zufall, sondern die
        // Grundlage dafür, dass der Prüfschritt etwas wert ist.
        val a = Briefkasten()
        val b = Briefkasten()
        Senden.unterschreibenUndSenden(entwurf(), woerter, EIGENE, spiegelMit(a))
        Senden.unterschreibenUndSenden(entwurf(), woerter, EIGENE, spiegelMit(b))
        assertEquals(a.eingereicht, b.eingereicht)
    }

    @Test
    fun jedeAenderungAendertDieBytes() {
        val a = Briefkasten()
        val b = Briefkasten()
        Senden.unterschreibenUndSenden(entwurf(betrag = "1,5"), woerter, EIGENE, spiegelMit(a))
        Senden.unterschreibenUndSenden(entwurf(betrag = "1,6"), woerter, EIGENE, spiegelMit(b))
        assertFalse(a.eingereicht == b.eingereicht)
    }

    @Test
    fun klartextKenntDieWichtigstenGruende() {
        for (code in listOf(
            "bad_nonce", "insufficient_funds", "fee_too_low",
            "bad_signature", "dust", "mempool_full", "expired",
        )) {
            val t = Senden.klartext(code)
            assertFalse("'$code' braucht einen Satz", t.isEmpty())
            assertFalse("'$code' darf nicht der Code selbst sein", t == code)
        }
        assertTrue(Senden.klartext("voellig_neu").contains("abgelehnt"))
    }
}

class SendestatusTest {

    private val txid = "aa".repeat(32)

    @Test
    fun ohneBlockUndOhneMempoolWartetSie() {
        val n = Sendestatus.netzbild(
            konto(), spitze = 8560, txid = txid,
        )
        assertNull(n.hoehe)
        assertEquals(false, n.imMempool)
        assertEquals(0, n.bestaetigungen)
        assertFalse(n.imNetzGesehen)

        val s = Sendestatus.schritte(n, gesehen = false)
        assertEquals(Sendestatus.Teil.ERLEDIGT, s.signiert)
        assertEquals(Sendestatus.Teil.ERLEDIGT, s.angenommen)
        assertEquals(Sendestatus.Teil.WARTET, s.mempool)
        assertEquals(Sendestatus.Teil.OFFEN, s.block)
    }

    @Test
    fun imMempoolGesehen() {
        val w = Wartend(txid, Art.AUS, EIGENE, EMPFAENGER, 1, 1, 23, "")
        val n = Sendestatus.netzbild(konto(wartend = listOf(w)), 8560, txid)
        assertEquals(true, n.imMempool)
        assertTrue(n.imNetzGesehen)
        assertEquals(Sendestatus.Teil.ERLEDIGT, Sendestatus.schritte(n, false).mempool)
    }

    @Test
    fun imBlockZaehltDerBlockSelbstAlsErsteBestaetigung() {
        val e = VerlaufEintrag(
            txid = txid, height = 8560, idx = 0, zeit = null, art = Art.AUS,
            gegenseite = EMPFAENGER, anteile = null, betrag = 1, gebuehr = 1, notiz = "",
        )
        val k = konto().copy(verlauf = listOf(e))

        val eine = Sendestatus.netzbild(k, spitze = 8560, txid = txid)
        assertEquals(1, eine.bestaetigungen)
        assertEquals(Sendestatus.Teil.WARTET, Sendestatus.schritte(eine, true).weitere)

        val zwei = Sendestatus.netzbild(k, spitze = 8561, txid = txid)
        assertEquals(2, zwei.bestaetigungen)
        assertEquals(Sendestatus.Teil.ERLEDIGT, Sendestatus.schritte(zwei, true).weitere)
    }

    @Test
    fun ohneAbrufbareListeIstDerMempoolUnbekannt() {
        val k = konto().copy(wartendQuelle = "unavailable")
        assertNull(Sendestatus.netzbild(k, 8560, txid).imMempool)
    }

    @Test
    fun fuenfSchritteHabenFuenfNamen() {
        assertEquals(5, Sendestatus.namen.size)
        val n = Sendestatus.netzbild(konto(), 8560, txid)
        assertEquals(5, Sendestatus.alsListe(Sendestatus.schritte(n, false)).size)
    }
}
