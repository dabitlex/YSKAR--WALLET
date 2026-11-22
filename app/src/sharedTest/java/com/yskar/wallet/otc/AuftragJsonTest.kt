package com.yskar.wallet.otc

import com.yskar.wallet.kern.Wallet
import com.yskar.wallet.otc.Auftrag.Lesung
import com.yskar.wallet.signatur.Nachricht
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * Kaputte Auftraege, deren Fehler nur im JSON steckt.
 *
 * Diese Klasse liegt unter src/sharedTest und laeuft ZWEIMAL: auf der JVM
 * mit org.json aus Maven und auf dem Geraet mit dem org.json von Android.
 * Mit org.json las die App anders als die Unittests (Android nahm doppelte
 * Felder, 0x… und fuehrende Nullen). Seit `FlachJson` liest der Auftrag
 * sein `d` selbst; auf beiden Seiten muss jetzt dasselbe herauskommen.
 * Erwartet wird bei jedem kaputten Auftrag: abgelehnt, mit Fehler.DATEN.
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

    /** Text des Auftrags. Android schreibt `/` als `\/` - beides ist gueltiges JSON. */
    private val roh: String get() = einloesenDaten().toString()

    private fun code(art: String, d: String) = "yskar:otc?v=1&auftrag=$art&d=${Nachricht.textBase64url(d)}"
    private fun einloesen(d: String) = Auftrag.lesen(code("einloesen", d), k.address, jetzt)
    private fun sperren(d: String) = Auftrag.lesen(code("sperren", d), v.address, jetzt)

    /** Haengt ein Feld hinten an - auch eines, das es schon gibt. */
    private fun mehr(d: String, feld: String): String {
        check(d.endsWith("}"))
        return d.dropLast(1) + "," + feld + "}"
    }

    /** Ersetzt den rohen Wert eines Feldes im Text, ohne dass ein JSON-Leser ihn sieht. */
    private fun ersetze(d: String, name: String, neu: String): String {
        val r = Regex("\"$name\":(\"[^\"]*\"|\\d+)")
        check(r.findAll(d).count() == 1) { "$name steht nicht genau einmal in $d" }
        return r.replace(d) { "\"$name\":$neu" }
    }

    private fun abgelehnt(was: String, l: Lesung) {
        assertTrue("$was wurde ANGENOMMEN", l is Lesung.Abgelehnt)
        assertEquals(was, Auftrag.Fehler.DATEN, (l as Lesung.Abgelehnt).fehler)
    }

    private fun angenommen(was: String, l: Lesung) {
        assertTrue("$was wurde abgelehnt: ${(l as? Lesung.Abgelehnt)?.fehler}", l is Lesung.Ok)
    }

    @Test
    fun ohneEingriffWirdAngenommen() {
        // Sonst bewiesen die Faelle unten nichts.
        angenommen("einloesen", einloesen(roh))
        angenommen("sperren", sperren(sperrenDaten().toString()))
    }

    // ---------------------------------------------------------- doppelt

    @Test
    fun ysrDoppeltBeimEinloesen() = abgelehnt("einloesen mit zweitem ysr", einloesen(mehr(roh, "\"ysr\":\"1\"")))

    @Test
    fun ysrDoppeltBeimSperren() =
        abgelehnt("sperren mit zweitem ysr", sperren(mehr(sperrenDaten().toString(), "\"ysr\":\"1\"")))

    @Test
    fun nDoppelt() = abgelehnt("einloesen mit zweitem n", einloesen(mehr(roh, "\"n\":6")))

    // ---------------------------------------- Zahlen, die JSON nicht kennt

    @Test
    fun fristHexadezimal() =
        abgelehnt("frist als 0x…", einloesen(ersetze(roh, "frist", "0x" + fall.getLong("frist").toString(16))))

    @Test
    fun fristMitFuehrenderNull() =
        // Oktal gelesen ergaebe das wieder die echte Frist.
        abgelehnt("frist als 0…", einloesen(ersetze(roh, "frist", "0" + fall.getLong("frist").toString(8))))

    @Test
    fun nHexadezimal() = abgelehnt("n als 0x48", einloesen(ersetze(roh, "n", "0x48")))

    @Test
    fun nMitFuehrenderNull() =
        // 0110 ist oktal 72, dezimal 110 - beides laege in 6..144.
        abgelehnt("n als 0110", einloesen(ersetze(roh, "n", "0110")))

    @Test
    fun nMitKomma() = abgelehnt("n als 72.0", einloesen(ersetze(roh, "n", "72.0")))

    @Test
    fun nMitExponent() = abgelehnt("n als 7.2e1", einloesen(ersetze(roh, "n", "7.2e1")))

    @Test
    fun fristMitExponent() = abgelehnt("frist als 1e3", einloesen(ersetze(roh, "frist", "1e3")))

    @Test
    fun fristNegativ() = abgelehnt("frist als -1", einloesen(ersetze(roh, "frist", "-1")))

    @Test
    fun fristMitPlus() =
        abgelehnt("frist als +…", einloesen(ersetze(roh, "frist", "+" + fall.getLong("frist"))))

    @Test
    fun fristUeberLong() = abgelehnt("frist ueber Long", einloesen(ersetze(roh, "frist", "9223372036854775808")))

    // ------------------------------------------------- andere Werte als Text/Zahl

    @Test
    fun verschachteltesObjekt() = abgelehnt("frist als Objekt", einloesen(ersetze(roh, "frist", "{\"a\":1}")))

    @Test
    fun array() = abgelehnt("frist als Array", einloesen(ersetze(roh, "frist", "[10500]")))

    @Test
    fun wahr() = abgelehnt("n als true", einloesen(ersetze(roh, "n", "true")))

    @Test
    fun nichts() = abgelehnt("n als null", einloesen(ersetze(roh, "n", "null")))

    // ------------------------------------------------------------ Rahmen

    @Test
    fun zusaetzlichesFeld() = abgelehnt("einloesen mit Feld extra", einloesen(mehr(roh, "\"extra\":\"1\"")))

    @Test
    fun muellNachDerKlammer() = abgelehnt("Text nach }", einloesen("$roh x"))

    @Test
    fun zweitesObjekt() = abgelehnt("zwei Objekte", einloesen("$roh{}"))

    @Test
    fun leeresObjekt() = abgelehnt("{}", einloesen("{}"))

    @Test
    fun leerraumAussenIstErlaubt() = angenommen("Leerraum aussen", einloesen(" \n\t$roh\r\n "))

    // ------------------------------------------------------------ Strings

    @Test
    fun roherZeilenumbruchImString() {
        val a = fall.getString("skriptAdresse")
        abgelehnt("Zeilenumbruch in skriptAdresse", einloesen(ersetze(roh, "skriptAdresse", "\"${a.take(10)}\n${a.drop(10)}\"")))
    }

    @Test
    fun unicodeEscapeWirdGelesen() {
        // \u0079 ist "y": dieselbe Adresse, anders geschrieben.
        val a = fall.getString("skriptAdresse")
        check(a.startsWith("y"))
        angenommen("\\u-Escape in skriptAdresse", einloesen(ersetze(roh, "skriptAdresse", "\"\\u0079${a.drop(1)}\"")))
    }
}
