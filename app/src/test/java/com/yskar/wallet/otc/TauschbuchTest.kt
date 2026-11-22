package com.yskar.wallet.otc

import com.yskar.wallet.kern.Hex
import com.yskar.wallet.kern.Wallet
import com.yskar.wallet.otc.Tauschbuch.Rolle
import com.yskar.wallet.otc.Tauschbuch.Stand
import com.yskar.wallet.signatur.Nachricht
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/** Offene Tausche: merken, speichern, Auftrag daraus neu bauen. */
class TauschbuchTest {

    private val fall: JSONObject by lazy {
        JSONArray(javaClass.getResourceAsStream("/otc-vergleichswerte.json")!!.use { it.readBytes() }
            .toString(Charsets.UTF_8)).getJSONObject(0)
    }
    private val k by lazy { Wallet.fromMnemonic(fall.getString("kaeuferWoerter")) }
    private val v by lazy { Wallet.fromMnemonic(fall.getString("verkaeuferWoerter")) }
    private val jetztMs = Instant.parse("2026-11-22T12:00:00Z").toEpochMilli()

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

    private fun code(art: String, d: JSONObject, cb: String? = null) =
        "yskar:otc?v=1&auftrag=$art&d=${Nachricht.textBase64url(d.toString())}" +
            (cb?.let { "&cb=${Nachricht.textBase64url(it)}" } ?: "")

    private fun hash(ysr: String = "15000000000") = (Auftrag.lesen(
        code("hash", JSONObject().apply {
            put("kennung", fall.getString("tauschKennung")); put("adresse", k.address); put("ysr", ysr)
            put("gegen", "LTC"); put("gegenMenge", "0.25"); put("ablauf", "2026-11-22T12:10:00Z")
        }, "https://otc.yskar.app/api/x"),
        k.address, jetztMs,
    ) as Auftrag.Lesung.Ok).auftrag as Auftrag.Gelesen.Hash

    private fun einloesen() = (Auftrag.lesen(code("einloesen", sperrD().put("n", 72)), k.address, jetztMs)
        as Auftrag.Lesung.Ok).auftrag as Auftrag.Gelesen.Einloesen

    @Test
    fun kaeuferWegVonHashBisZumNeuGebautenAuftrag() {
        var buch = Tauschbuch.merkeHash(emptyList(), hash(), 100)
        assertEquals(15_000_000_000L, Tauschbuch.erwartetYsr(buch, Hex.fromHex(fall.getString("tauschKennung"))))
        // Kein Auftrag ohne Sperrteile.
        assertNull(Tauschbuch.auftrag(buch.single()))

        // Ein zweiter hash-Auftrag mit anderem Betrag aendert den gemerkten nicht.
        buch = Tauschbuch.merkeHash(buch, hash("1"), 200)
        assertEquals(15_000_000_000L, buch.single().ysr)

        val e = einloesen()
        buch = Tauschbuch.merkeSperre(buch, e.sperre, Rolle.KAEUFER, e.n, 300)
        assertEquals("LTC", buch.single().gegen)
        assertEquals(100L, buch.single().seit)

        // Aus dem Gemerkten gebaut -- und wieder ganz durch Auftrag.lesen.
        val neu = Auftrag.lesen(Tauschbuch.auftrag(buch.single())!!, k.address, jetztMs)
        assertTrue(neu is Auftrag.Lesung.Ok)
        val g = (neu as Auftrag.Lesung.Ok).auftrag as Auftrag.Gelesen.Einloesen
        assertEquals(fall.getString("skriptAdresse"), g.sperre.skriptAdresse)
        assertEquals(72L, g.n)
    }

    @Test
    fun verkaeuferBekommtZurueckAuftrag() {
        val s = (Auftrag.lesen(
            code("sperren", sperrD().put("n", 72).put("gegen", "BTC").put("gegenMenge", "0.001").put("gegenSperre", "ab12")),
            v.address, jetztMs,
        ) as Auftrag.Lesung.Ok).auftrag as Auftrag.Gelesen.Sperren
        var buch = Tauschbuch.merkeSperre(emptyList(), s.sperre, Rolle.VERKAEUFER, s.n, 10, s.gegen, s.gegenMenge)
        buch = Tauschbuch.setzeStand(buch, s.sperre.kennungHex, Rolle.VERKAEUFER, Stand.GESPERRT, "aa")
        val z = Auftrag.lesen(Tauschbuch.auftrag(buch.single())!!, v.address, jetztMs)
        assertTrue((z as Auftrag.Lesung.Ok).auftrag is Auftrag.Gelesen.Zurueck)
        assertEquals(1, Tauschbuch.offen(buch).size)
        buch = Tauschbuch.setzeStand(buch, s.sperre.kennungHex, Rolle.VERKAEUFER, Stand.ZURUECK, "bb")
        assertEquals(0, Tauschbuch.offen(buch).size)
        assertEquals("bb", buch.single().txid)
    }

    @Test
    fun speichernUndLesen() {
        val e = einloesen()
        val buch = Tauschbuch.merkeSperre(Tauschbuch.merkeHash(emptyList(), hash(), 5), e.sperre, Rolle.KAEUFER, 72, 6)
        val zurueck = Tauschbuch.ausText(Tauschbuch.alsText(buch))
        assertEquals(1, zurueck.size)
        assertEquals(Tauschbuch.auftrag(buch.single()), Tauschbuch.auftrag(zurueck.single()))
        assertEquals(Stand.GEMERKT, zurueck.single().stand)
    }

    @Test
    fun kaputteZeilenFallenWeg() {
        val gut = Tauschbuch.alsText(Tauschbuch.merkeHash(emptyList(), hash(), 5))
        val a = JSONArray(gut)
        a.put(JSONObject().put("kennung", "xyz").put("rolle", "kaeufer"))
        a.put("kein Objekt")
        a.put(JSONObject(a.getJSONObject(0).toString()).put("stand", "erfunden"))
        assertEquals(1, Tauschbuch.ausText(a.toString()).size)
        assertEquals(0, Tauschbuch.ausText("kein json").size)
    }

    @Test
    fun hoechstensFuenfzig() {
        var buch = emptyList<Tauschbuch.Eintrag>()
        for (i in 0 until 60) {
            val kennung = "%064x".format(i)
            buch = listOf(
                Tauschbuch.Eintrag(kennung, Rolle.KAEUFER, 1, null, null, null, null, Stand.EINGELOEST, null, i.toLong()),
            ) + buch
            buch = Tauschbuch.setzeStand(buch, kennung, Rolle.KAEUFER, Stand.EINGELOEST, null)
        }
        assertTrue(buch.size <= Tauschbuch.MAX)
        // Die juengsten bleiben.
        assertTrue(buch.any { it.seit == 59L })
    }
}
