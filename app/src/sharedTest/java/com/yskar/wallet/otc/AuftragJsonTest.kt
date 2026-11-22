package com.yskar.wallet.otc

import com.yskar.wallet.kern.Wallet
import com.yskar.wallet.otc.Auftrag.Lesung
import com.yskar.wallet.signatur.Nachricht
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * Kaputte Auftraege, deren Fehler nur im JSON steckt.
 *
 * Diese Klasse liegt unter src/sharedTest und laeuft ZWEIMAL: auf der JVM
 * mit org.json aus Maven und auf dem Geraet mit dem org.json von Android,
 * das die App wirklich benutzt. Die beiden Leser sind verschieden. Erwartet
 * wird in jedem Fall: abgelehnt - mit welchem Grund, ist gleich.
 *
 * Faellt ein Fall nur auf einer Seite, liest die App anders, als die
 * Unittests glauben. Das ist der Zweck dieser Klasse; umgangen wird es
 * hier nicht.
 *
 * Grundlage ist Fall 0 aus otc-vergleichswerte.json, Schluessel nur aus den
 * oeffentlichen BIP39-Testvektoren (Regel 3).
 */
class AuftragJsonTest {

    private val fall: JSONObject by lazy {
        JSONArray(javaClass.getResourceAsStream("/otc-vergleichswerte.json")!!.use { it.readBytes() }
            .toString(Charsets.UTF_8)).getJSONObject(0)
    }
    private val k by lazy { Wallet.fromMnemonic(fall.getString("kaeuferWoerter")) }
    private val v by lazy { Wallet.fromMnemonic(fall.getString("verkaeuferWoerter")) }
    private val jetzt = Instant.parse("2026-11-22T12:00:00Z").toEpochMilli()

    private fun einloesenDaten() = JSONObject().apply {
        put("kennung", fall.getString("tauschKennung"))
        put("kaeufer", fall.getString("kaeuferPub"))
        put("verkaeufer", fall.getString("verkaeuferPub"))
        put("hash", fall.getString("hash"))
        put("frist", fall.getLong("frist"))
        put("schluss", fall.getLong("schluss"))
        put("n", 72)
        put("ysr", fall.getJSONObject("einloesen").getString("betrag"))
        put("skriptAdresse", fall.getString("skriptAdresse"))
    }

    private fun sperrenDaten() = einloesenDaten().apply {
        put("gegen", "USDC"); put("gegenMenge", "12.5"); put("gegenSperre", "0xabc123")
    }

    private fun code(art: String, d: String) = "yskar:otc?v=1&auftrag=$art&d=${Nachricht.textBase64url(d)}"
    private fun einloesen(d: String) = Auftrag.lesen(code("einloesen", d), k.address, jetzt)
    private fun sperren(d: String) = Auftrag.lesen(code("sperren", d), v.address, jetzt)

    /** Haengt ein Feld hinten an - auch eines, das es schon gibt. */
    private fun mehr(d: String, feld: String): String {
        check(d.endsWith("}"))
        return d.dropLast(1) + "," + feld + "}"
    }

    /** Ersetzt den Wert einer Zahl im Text, ohne dass ein JSON-Leser ihn sieht. */
    private fun zahl(d: String, name: String, neu: String): String {
        val r = Regex("\"$name\":\\d+")
        check(r.findAll(d).count() == 1) { "$name steht nicht genau einmal in $d" }
        return r.replace(d, "\"$name\":$neu")
    }

    private fun abgelehnt(was: String, l: Lesung) {
        assertTrue("$was wurde ANGENOMMEN", l is Lesung.Abgelehnt)
    }

    @Test
    fun ohneEingriffWirdAngenommen() {
        // Sonst bewiesen die Faelle unten nichts.
        assertTrue("einloesen: ${(einloesen(einloesenDaten().toString()) as? Lesung.Abgelehnt)?.fehler}",
            einloesen(einloesenDaten().toString()) is Lesung.Ok)
        assertTrue("sperren: ${(sperren(sperrenDaten().toString()) as? Lesung.Abgelehnt)?.fehler}",
            sperren(sperrenDaten().toString()) is Lesung.Ok)
    }

    // ---------------------------------------------------------- doppelt

    @Test
    fun ysrDoppeltBeimEinloesen() =
        abgelehnt("einloesen mit zweitem ysr", einloesen(mehr(einloesenDaten().toString(), "\"ysr\":\"1\"")))

    @Test
    fun ysrDoppeltBeimSperren() =
        abgelehnt("sperren mit zweitem ysr", sperren(mehr(sperrenDaten().toString(), "\"ysr\":\"1\"")))

    @Test
    fun nDoppelt() =
        abgelehnt("einloesen mit zweitem n", einloesen(mehr(einloesenDaten().toString(), "\"n\":6")))

    // ---------------------------------------- Zahlen, die JSON nicht kennt

    @Test
    fun fristHexadezimal() {
        val frist = fall.getLong("frist")
        abgelehnt("frist als 0x…", einloesen(zahl(einloesenDaten().toString(), "frist", "0x" + frist.toString(16))))
    }

    @Test
    fun fristMitFuehrenderNull() {
        // Oktal gelesen ergibt das wieder die echte Frist.
        val frist = fall.getLong("frist")
        abgelehnt("frist als 0…", einloesen(zahl(einloesenDaten().toString(), "frist", "0" + frist.toString(8))))
    }

    @Test
    fun nHexadezimal() =
        abgelehnt("n als 0x48", einloesen(zahl(einloesenDaten().toString(), "n", "0x48")))

    @Test
    fun nMitFuehrenderNull() =
        // 0110 ist oktal 72, dezimal 110 - beides laege in 6..144.
        abgelehnt("n als 0110", einloesen(zahl(einloesenDaten().toString(), "n", "0110")))

    // ---------------------------------------------------- Feld zu viel

    @Test
    fun zusaetzlichesFeld() =
        abgelehnt("einloesen mit Feld extra", einloesen(mehr(einloesenDaten().toString(), "\"extra\":\"1\"")))
}
