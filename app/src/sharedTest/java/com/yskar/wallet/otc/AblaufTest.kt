package com.yskar.wallet.otc

import com.yskar.wallet.kern.Hex
import com.yskar.wallet.kern.Wallet
import com.yskar.wallet.otc.Ablauf.AusgabeErgebnis
import com.yskar.wallet.otc.Ablauf.HashErgebnis
import com.yskar.wallet.otc.Auftrag.Bau
import com.yskar.wallet.signatur.Nachricht
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/** Vom Auftrag mit den zwoelf Woertern bis zur fertigen Transaktion. */
class AblaufTest {

    private val fall: JSONObject by lazy {
        JSONArray(javaClass.getResourceAsStream("/otc-vergleichswerte.json")!!.use { it.readBytes() }
            .toString(Charsets.UTF_8)).getJSONObject(0)
    }
    private val kWoerter by lazy { fall.getString("kaeuferWoerter").toByteArray() }
    private val vWoerter by lazy { fall.getString("verkaeuferWoerter").toByteArray() }
    private val k by lazy { Wallet.fromMnemonic(fall.getString("kaeuferWoerter")) }
    private val v by lazy { Wallet.fromMnemonic(fall.getString("verkaeuferWoerter")) }
    private val jetzt = Instant.parse("2026-11-22T12:00:00Z").toEpochMilli()

    private fun code(art: String, d: JSONObject, cb: String? = null) =
        "yskar:otc?v=1&auftrag=$art&d=${Nachricht.textBase64url(d.toString())}" +
            (cb?.let { "&cb=${Nachricht.textBase64url(it)}" } ?: "")

    private fun sperrD() = JSONObject().apply {
        put("kennung", fall.getString("tauschKennung"))
        put("kaeufer", fall.getString("kaeuferPub"))
        put("verkaeufer", fall.getString("verkaeuferPub"))
        put("hash", fall.getString("hash"))
        put("frist", fall.getLong("frist"))
        put("schluss", fall.getLong("schluss"))
        put("ysr", "15000000000")
        put("skriptAdresse", fall.getString("skriptAdresse"))
    }

    private val hashCode by lazy {
        code("hash", JSONObject().apply {
            put("kennung", fall.getString("tauschKennung")); put("adresse", k.address); put("ysr", "15000000000")
            put("gegen", "USDC"); put("gegenMenge", "10"); put("ablauf", "2026-11-22T12:10:00Z")
        }, "https://otc.yskar.app/api/tausch/hash")
    }

    @Test
    fun hashMitDenWoertern() {
        val e = Ablauf.hashUnterschreiben(hashCode, kWoerter, k.address, jetzt)
        assertTrue("$e", e is HashErgebnis.Ok)
        e as HashErgebnis.Ok
        assertTrue(e.signiert.message.contains("hash: ${fall.getString("hash")}"))
        assertEquals(Hex.toHex(k.publicKey), e.signiert.publicKey)
        assertEquals(Nachricht.Echtheit.GUELTIG, Nachricht.pruefen(e.signiert))
    }

    @Test
    fun hashMitFremdenWoertern() {
        assertEquals(
            HashErgebnis.Gestoert(Ablauf.Stoerung.SCHLUESSEL_PASST_NICHT),
            Ablauf.hashUnterschreiben(hashCode, vWoerter, k.address, jetzt),
        )
        assertEquals(
            HashErgebnis.Gestoert(Ablauf.Stoerung.WOERTER_UNBRAUCHBAR),
            Ablauf.hashUnterschreiben(hashCode, "kein wort".toByteArray(), k.address, jetzt),
        )
        // Abgelaufen, waehrend die PIN getippt wurde.
        assertEquals(
            HashErgebnis.Abgelehnt(Auftrag.Fehler.ABGELAUFEN),
            Ablauf.hashUnterschreiben(hashCode, kWoerter, k.address, jetzt + 11 * 60_000),
        )
    }

    private fun konto() = Auftrag.Skriptkonto(15_000_000_400L, 0, listOf(10_400L), false)

    @Test
    fun einloesenMitDenWoerternWieDerKern() {
        val a = (Auftrag.lesen(code("einloesen", sperrD().put("n", 72)), k.address, jetzt) as Auftrag.Lesung.Ok)
            .auftrag as Auftrag.Gelesen.Einloesen
        var gemessen = 0
        val bau = Ablauf.mitGebuehr({ gemessen = it; 400L }) { g ->
            Auftrag.einloesenBauen(a, k.address, konto(), 10_481, g, 15_000_000_000L)
        } as Bau.Ok
        assertEquals(bau.bytes, gemessen)
        val e = Ablauf.ausgabeUnterschreiben(a, bau, kWoerter, k.address)
        assertTrue("$e", e is AusgabeErgebnis.Fertig)
        val ein = fall.getJSONObject("einloesen")
        assertEquals(ein.getString("roh"), Hex.toHex((e as AusgabeErgebnis.Fertig).fertig.roh))
        assertEquals(ein.getString("txid"), e.fertig.txidHex)
    }

    @Test
    fun zurueckMitDenWoerternWieDerKern() {
        val a = (Auftrag.lesen(code("zurueck", sperrD()), v.address, jetzt) as Auftrag.Lesung.Ok)
            .auftrag as Auftrag.Gelesen.Zurueck
        val bau = Ablauf.mitGebuehr({ 400L }) { g -> Auftrag.zurueckBauen(a, v.address, konto(), 10_499, g) } as Bau.Ok
        val e = Ablauf.ausgabeUnterschreiben(a, bau, vWoerter, v.address) as AusgabeErgebnis.Fertig
        assertEquals(fall.getJSONObject("zurueck").getString("roh"), Hex.toHex(e.fertig.roh))
        // Ein Einloese-Entwurf darf nicht als Zurueckholen durchgehen.
        assertEquals(
            AusgabeErgebnis.Gestoert(Ablauf.Stoerung.SCHLUESSEL_PASST_NICHT),
            Ablauf.ausgabeUnterschreiben(a, bau, kWoerter, v.address),
        )
    }

    @Test
    fun gebuehrDieDasGuthabenAufzehrtWirdAbgelehnt() {
        val a = (Auftrag.lesen(code("zurueck", sperrD()), v.address, jetzt) as Auftrag.Lesung.Ok)
            .auftrag as Auftrag.Gelesen.Zurueck
        val klein = Auftrag.Skriptkonto(1_000, 0, listOf(10_400L), false)
        val b = Ablauf.mitGebuehr({ 950L }) { g -> Auftrag.zurueckBauen(a, v.address, klein, 10_499, g) }
        assertEquals(Bau.Abgelehnt(Auftrag.Fehler.GUTHABEN), b)
    }
}
