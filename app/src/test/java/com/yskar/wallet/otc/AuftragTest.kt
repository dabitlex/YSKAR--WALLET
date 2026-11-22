package com.yskar.wallet.otc

import com.yskar.wallet.kern.Bip39
import com.yskar.wallet.kern.Hex
import com.yskar.wallet.kern.Wallet
import com.yskar.wallet.otc.Auftrag.Bau
import com.yskar.wallet.otc.Auftrag.Fehler
import com.yskar.wallet.otc.Auftrag.Gelesen
import com.yskar.wallet.otc.Auftrag.Lesung
import com.yskar.wallet.signatur.Nachricht
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * Auftraege von otc.yskar.app: Lesen, Pruefen, Bauen.
 *
 * Grundlage ist Fall 0 aus otc-vergleichswerte.json (YSKAR-Kern,
 * checkScriptSpend bestanden). Einloesen und Zurueckholen muessen hier
 * Byte fuer Byte dieselbe Transaktion ergeben wie dort.
 *
 * Schluessel: nur die oeffentlichen BIP39-Testvektoren (Regel 3).
 */
class AuftragTest {

    private val fall: JSONObject by lazy {
        JSONArray(javaClass.getResourceAsStream("/otc-vergleichswerte.json")!!.use { it.readBytes() }
            .toString(Charsets.UTF_8)).getJSONObject(0)
    }
    private val k by lazy { Wallet.fromMnemonic(fall.getString("kaeuferWoerter")) }
    private val v by lazy { Wallet.fromMnemonic(fall.getString("verkaeuferWoerter")) }
    private val kWurzel by lazy { Wallet.otcWurzel(Bip39.toSeed(fall.getString("kaeuferWoerter"), ""), 0) }

    private val jetzt = Instant.parse("2026-11-22T12:00:00Z").toEpochMilli()
    private val cb = "https://otc.yskar.app/api/tausch/hash"

    private fun code(art: String, d: JSONObject, rueckruf: String? = null, v: String = "1"): String {
        var s = "yskar:otc?v=$v&auftrag=$art&d=${Nachricht.textBase64url(d.toString())}"
        if (rueckruf != null) s += "&cb=${Nachricht.textBase64url(rueckruf)}"
        return s
    }

    private fun sperrDaten(n: Int? = 72) = JSONObject().apply {
        put("kennung", fall.getString("tauschKennung"))
        put("kaeufer", fall.getString("kaeuferPub"))
        put("verkaeufer", fall.getString("verkaeuferPub"))
        put("hash", fall.getString("hash"))
        put("frist", fall.getLong("frist"))
        put("schluss", fall.getLong("schluss"))
        if (n != null) put("n", n)
        put("ysr", fall.getJSONObject("einloesen").getString("betrag"))
        put("skriptAdresse", fall.getString("skriptAdresse"))
    }

    private fun sperrenDaten() = sperrDaten().apply {
        put("gegen", "USDC"); put("gegenMenge", "12.5"); put("gegenSperre", "0xabc123")
    }

    private fun hashDaten() = JSONObject().apply {
        put("kennung", fall.getString("tauschKennung"))
        put("adresse", fall.getString("kaeuferAdresse"))
        put("ysr", "15000000000")
        put("gegen", "BTC")
        put("gegenMenge", "0.0004")
        put("ablauf", "2026-11-22T12:10:00Z")
    }

    private fun alsK(text: String) = Auftrag.lesen(text, k.address, k.publicKey, jetzt)
    private fun alsV(text: String) = Auftrag.lesen(text, v.address, v.publicKey, jetzt)

    private fun fehler(l: Lesung) = (l as? Lesung.Abgelehnt)?.fehler
    private inline fun <reified T : Gelesen> ok(l: Lesung): T {
        assertTrue("abgelehnt: ${fehler(l)}", l is Lesung.Ok)
        return (l as Lesung.Ok).auftrag as T
    }

    // ----------------------------------------------------------------- hash

    @Test
    fun hashGibtNurDenFingerabdruck() {
        val a = ok<Gelesen.Hash>(alsK(code("hash", hashDaten(), cb)))
        assertEquals(15_000_000_000L, a.ysr)
        val n = Auftrag.hashNachricht(a, kWurzel)
        assertEquals(
            "YSKAR Signed Message\npurpose: otc-hash\ndomain: otc.yskar.app\n" +
                "address: ${k.address}\nswap: ${fall.getString("tauschKennung")}\n" +
                "hash: ${fall.getString("hash")}\nexpires: 2026-11-22T12:10:00Z",
            n,
        )
        assertTrue(!n.contains(fall.getString("geheimnis")))
        // Laesst sich mit dem vorhandenen Weg unterschreiben und pruefen.
        val s = Nachricht.signieren(n, k.privateKey, k.publicKey)
        assertEquals(Nachricht.Echtheit.GUELTIG, Nachricht.pruefen(s))
    }

    @Test
    fun hashAblehnungen() {
        assertEquals(Fehler.ADRESSE_FREMD, fehler(alsV(code("hash", hashDaten(), cb))))
        assertEquals(Fehler.RUECKRUF, fehler(alsK(code("hash", hashDaten()))))
        assertEquals(Fehler.RUECKRUF, fehler(alsK(code("hash", hashDaten(), "http://otc.yskar.app/x"))))
        assertEquals(Fehler.RUECKRUF_HOST, fehler(alsK(code("hash", hashDaten(), "https://lab.yskar.app/x"))))
        assertEquals(Fehler.RUECKRUF_HOST, fehler(alsK(code("hash", hashDaten(), "https://otc.yskar.app.boese.example/x"))))
        assertEquals(Fehler.ABGELAUFEN, fehler(alsK(code("hash", hashDaten().put("ablauf", "2026-11-22T12:00:00Z"), cb))))
        assertEquals(Fehler.ZU_LANGE, fehler(alsK(code("hash", hashDaten().put("ablauf", "2026-11-22T12:15:01Z"), cb))))
        assertEquals(Fehler.DATEN, fehler(alsK(code("hash", hashDaten().put("ablauf", "2026-02-30T12:05:00Z"), cb))))
        assertEquals(Fehler.DATEN, fehler(alsK(code("hash", hashDaten().put("gegen", "ETH"), cb))))
        assertEquals(Fehler.DATEN, fehler(alsK(code("hash", hashDaten().put("extra", 1), cb))))
        assertEquals(Fehler.BETRAG, fehler(alsK(code("hash", hashDaten().put("ysr", "0"), cb))))
        assertEquals(Fehler.BETRAG, fehler(alsK(code("hash", hashDaten().put("ysr", "1.5"), cb))))
        assertEquals(Fehler.VERSION, fehler(alsK(code("hash", hashDaten(), cb, v = "2"))))
        assertEquals(Fehler.ART, fehler(alsK(code("bezahlen", hashDaten(), cb))))
        assertEquals(Fehler.FORMAT, fehler(alsK("yskar:login?msg=a&cb=b")))
        assertEquals(Fehler.FORMAT, fehler(alsK(code("hash", hashDaten(), cb) + "&v=1")))
    }

    // -------------------------------------------------------------- sperren

    @Test
    fun sperrenNurFuerDenVerkaeufer() {
        val a = ok<Gelesen.Sperren>(alsV(code("sperren", sperrenDaten())))
        assertEquals(fall.getString("skriptAdresse"), a.sperre.skriptAdresse)
        assertEquals(72L, a.n)
        assertEquals(Fehler.NICHT_BETEILIGT, fehler(alsK(code("sperren", sperrenDaten()))))
    }

    @Test
    fun sperrenAblehnungen() {
        val fremdeAdresse = "ysr1p0a26fjuj2s2t8wr240e8x56k753c67x00694a5f59m2tnm2zpuest2dxr5"
        assertEquals(Fehler.SKRIPT, fehler(alsV(code("sperren", sperrenDaten().put("skriptAdresse", fremdeAdresse)))))
        // Eine Frist verschoben: Skript passt nicht mehr zur Adresse.
        assertEquals(
            Fehler.SKRIPT,
            fehler(alsV(code("sperren", sperrenDaten().put("frist", 10_501).put("schluss", 10_489)))),
        )
        assertEquals(Fehler.FRISTEN, fehler(alsV(code("sperren", sperrenDaten().put("schluss", 10_480)))))
        assertEquals(Fehler.FRISTEN, fehler(alsV(code("sperren", sperrenDaten().put("frist", 0x1_0000_0000L)))))
        assertEquals(Fehler.DATEN, fehler(alsV(code("sperren", sperrenDaten().put("n", 5)))))
        assertEquals(Fehler.DATEN, fehler(alsV(code("sperren", sperrenDaten().put("frist", "10500")))))
        assertEquals(Fehler.DATEN, fehler(alsV(code("sperren", sperrenDaten().put("hash", fall.getString("hash").uppercase())))))
        assertEquals(Fehler.RUECKRUF, fehler(alsV(code("sperren", sperrenDaten(), cb))))
    }

    @Test
    fun sperrenZeitfenster() {
        val a = ok<Gelesen.Sperren>(alsV(code("sperren", sperrenDaten())))
        // Schluss 10488: naechster Block + 72 + 6 darf hoechstens 10488 sein.
        assertNull(Auftrag.sperrenPruefen(a, 10_409))
        assertEquals(Fehler.ZU_KNAPP, Auftrag.sperrenPruefen(a, 10_410))
        // Frist 10500: hoechstens 300 Bloecke gebunden.
        assertNull(Auftrag.sperrenPruefen(a, 10_199))
        assertEquals(Fehler.ZU_LANG, Auftrag.sperrenPruefen(a, 10_198))
    }

    // ------------------------------------------------------------ einloesen

    private val ein by lazy { fall.getJSONObject("einloesen") }
    private val zur by lazy { fall.getJSONObject("zurueck") }
    private fun kontoMit(guthaben: Long, eingang: Long = 10_400, wartend: Boolean = false) =
        Auftrag.Skriptkonto(guthaben, naechsteNonce = 0, eingaenge = listOf(eingang), wartend = wartend)
    private val vollesKonto = 15_000_000_400L

    @Test
    fun einloesenWieDerKern() {
        val a = ok<Gelesen.Einloesen>(alsK(code("einloesen", sperrDaten())))
        val bau = Auftrag.einloesenBauen(a, kWurzel, k.address, kontoMit(vollesKonto), 10_481, 400, 15_000_000_000L)
        assertTrue("abgelehnt: $bau", bau is Bau.Ok)
        bau as Bau.Ok
        assertEquals(ein.getLong("validUntil"), bau.entwurf.validUntil)
        val fertig = Auftrag.unterschreiben(a.sperre, bau, k.privateKey, k.publicKey)
        assertEquals(ein.getString("roh"), Hex.toHex(fertig.roh))
        assertEquals(ein.getString("txid"), fertig.txidHex)
        assertEquals(bau.bytes, fertig.roh.size)
    }

    @Test
    fun einloesenAblehnungen() {
        assertEquals(Fehler.NICHT_BETEILIGT, fehler(alsV(code("einloesen", sperrDaten()))))
        assertEquals(Fehler.DATEN, fehler(alsK(code("einloesen", sperrDaten(n = null)))))

        val a = ok<Gelesen.Einloesen>(alsK(code("einloesen", sperrDaten())))
        fun bau(
            wurzel: ByteArray = kWurzel,
            konto: Auftrag.Skriptkonto = kontoMit(vollesKonto),
            spitze: Long = 10_481,
            erwartet: Long? = null,
        ) = (Auftrag.einloesenBauen(a, wurzel, k.address, konto, spitze, 400, erwartet) as? Bau.Abgelehnt)?.fehler

        val andereWurzel = Wallet.otcWurzel(Bip39.toSeed(fall.getString("kaeuferWoerter"), ""), 1)
        assertEquals(Fehler.GEHEIMNIS, bau(wurzel = andereWurzel))
        assertEquals(Fehler.BETRAG, bau(erwartet = 14_999_999_999L))
        assertEquals(Fehler.ZU_SPAET, bau(spitze = 10_482))
        assertEquals(Fehler.WARTEND, bau(konto = kontoMit(vollesKonto, wartend = true)))
        // Eingang bei 10410, Spitze 10481: 72 Bestaetigungen genuegen, 71 nicht.
        assertNull(bau(konto = kontoMit(vollesKonto, eingang = 10_410)))
        assertEquals(Fehler.UNBESTAETIGT, bau(konto = kontoMit(vollesKonto, eingang = 10_411)))
        assertEquals(Fehler.UNBESTAETIGT, bau(konto = Auftrag.Skriptkonto(vollesKonto, 0, emptyList(), false)))
        assertEquals(Fehler.GUTHABEN, bau(konto = kontoMit(14_999_999_999L)))
    }

    // -------------------------------------------------------------- zurueck

    @Test
    fun zurueckWieDerKern() {
        val a = ok<Gelesen.Zurueck>(alsV(code("zurueck", sperrDaten(n = null))))
        assertEquals(
            Fehler.ZU_FRUEH,
            (Auftrag.zurueckBauen(a, v.address, kontoMit(vollesKonto), 10_498, 400) as Bau.Abgelehnt).fehler,
        )
        val bau = Auftrag.zurueckBauen(a, v.address, kontoMit(vollesKonto), 10_499, 400) as Bau.Ok
        assertEquals(zur.getLong("validFrom"), bau.entwurf.validFrom)
        val fertig = Auftrag.unterschreiben(a.sperre, bau, v.privateKey, v.publicKey)
        assertEquals(zur.getString("roh"), Hex.toHex(fertig.roh))
        assertEquals(zur.getString("txid"), fertig.txidHex)
        assertEquals(Fehler.NICHT_BETEILIGT, fehler(alsK(code("zurueck", sperrDaten(n = null)))))
        assertEquals(Fehler.DATEN, fehler(alsV(code("zurueck", sperrDaten()))))
    }

    @Test
    fun staubWirdNichtGebaut() {
        val a = ok<Gelesen.Zurueck>(alsV(code("zurueck", sperrDaten(n = null))))
        assertEquals(
            Fehler.GUTHABEN,
            (Auftrag.zurueckBauen(a, v.address, kontoMit(499), 10_499, 400) as Bau.Abgelehnt).fehler,
        )
    }

    @Test
    fun fremderSchluesselUnterschreibtNicht() {
        val a = ok<Gelesen.Zurueck>(alsV(code("zurueck", sperrDaten(n = null))))
        val bau = Auftrag.zurueckBauen(a, v.address, kontoMit(vollesKonto), 10_499, 400) as Bau.Ok
        assertThrows(IllegalStateException::class.java) {
            Auftrag.unterschreiben(a.sperre, bau, k.privateKey, k.publicKey)
        }
    }

    @Test
    fun bestaetigungenZaehlenDenJuengstenEingang() {
        val konto = Auftrag.Skriptkonto(1, 0, listOf(100, 150, 120), false)
        assertEquals(1L, konto.bestaetigungen(150))
        assertEquals(51L, konto.bestaetigungen(200))
        assertEquals(0L, konto.bestaetigungen(149))
    }
}
