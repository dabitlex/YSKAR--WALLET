package com.yskar.wallet.kern

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * OTC-Tausch gegen Vergleichswerte aus dem YSKAR-Kern (TypeScript).
 *
 * Erzeugt mit dabitlex/yskar-otc, werkzeug/wallet-vektoren.ts. Jede Ausgabe
 * darin hat checkScriptSpend des Kerns bestanden -- der Knoten wuerde sie
 * annehmen. Stimmen hier Sighash, Signatur (Ed25519 ist deterministisch),
 * Witness, Rohbytes und txid, dann baut die Wallet dieselbe Transaktion.
 *
 * Schluessel: nur die oeffentlichen BIP39-Testvektoren (Regel 3).
 */
class OtcVektorenTest {

    private val faelle: JSONArray by lazy {
        JSONArray(javaClass.getResourceAsStream("/otc-vergleichswerte.json")!!.use { it.readBytes() }.toString(Charsets.UTF_8))
    }

    private fun hex(s: String) = Hex.fromHex(s)

    private fun teile(o: JSONObject) = Otc.Teile(
        kaeufer = hex(o.getString("kaeuferPub")),
        verkaeufer = hex(o.getString("verkaeuferPub")),
        hash = hex(o.getString("hash")),
        frist = o.getLong("frist"),
        einloeseSchluss = o.getLong("schluss"),
    )

    @Test
    fun schluesselPassenZuDenVektoren() {
        for (i in 0 until faelle.length()) {
            val o = faelle.getJSONObject(i)
            val k = Wallet.fromMnemonic(o.getString("kaeuferWoerter"))
            val v = Wallet.fromMnemonic(o.getString("verkaeuferWoerter"))
            assertEquals(o.getString("kaeuferPub"), Hex.toHex(k.publicKey))
            assertEquals(o.getString("verkaeuferPub"), Hex.toHex(v.publicKey))
            assertEquals(o.getString("kaeuferAdresse"), k.address)
        }
    }

    @Test
    fun geheimnisUndFingerabdruck() {
        for (i in 0 until faelle.length()) {
            val o = faelle.getJSONObject(i)
            val wurzel = Wallet.otcWurzel(Bip39.toSeed(o.getString("kaeuferWoerter"), ""), o.getInt("konto"))
            val x = Otc.geheimnis(wurzel, hex(o.getString("tauschKennung")))
            assertEquals("Fall $i", o.getString("geheimnis"), Hex.toHex(x))
            assertEquals("Fall $i", o.getString("hash"), Hex.toHex(Otc.fingerabdruck(x)))
            assertTrue(Otc.passt(x, hex(o.getString("hash"))))
        }
    }

    /** Die freigegebenen Testvektoren aus der OTC-Spezifikation (Entwurf 3, Abschnitt 3). */
    @Test
    fun spezifikationsVektoren() {
        val abandon = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about"
        val legal = "legal winner thank year wave sausage worth useful legal winner thank yellow"
        val null32 = ByteArray(32)
        fun x(w: String, konto: Int, kennung: ByteArray) =
            Hex.toHex(Otc.geheimnis(Wallet.otcWurzel(Bip39.toSeed(w, ""), konto), kennung))
        assertEquals("6d0b0f2d5430abea28afcee604344a356ad5552da3e87abdcead6e1a0437fe56", x(abandon, 0, null32))
        assertEquals("88d229fa1c19ec9cf1232ad20eb1c3ba5b0f4ab714323b14ce4402e2a10c218b", x(abandon, 0, hex("7f3a91c2" + "00".repeat(28))))
        assertEquals("a455778747700014a037366ca1c5a1f0df6f03cefeba89b6a7c765c59fea4731", x(abandon, 0, ByteArray(32) { it.toByte() }))
        assertEquals("8449e3c5f9b0df41086579523f0102301e5f40f4641c6e374523a5b7516bcecf", x(abandon, 1, null32))
        assertEquals("b82e64591cad753cc20209114e55d0dea75b02cb9cc0942f19e1679b20d7d15c", x(legal, 0, null32))
    }

    /** Der OTC-Zweig ist nicht der Signierzweig: andere Schluessel. */
    @Test
    fun otcZweigIstGetrennt() {
        val seed = Bip39.toSeed("abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about", "")
        val signier = Wallet.fromSeed(seed, 0, 0).privateKey
        assertFalse(signier.contentEquals(Wallet.otcWurzel(seed, 0)))
        assertFalse(Wallet.otcWurzel(seed, 0).contentEquals(Wallet.otcWurzel(seed, 1)))
    }

    @Test
    fun skriptUndAdresse() {
        for (i in 0 until faelle.length()) {
            val o = faelle.getJSONObject(i)
            val t = teile(o)
            assertEquals("Fall $i", o.getString("skript"), Hex.toHex(Otc.skript(t)))
            assertEquals("Fall $i", o.getString("skriptRoh"), Hex.toHex(Address.rawFromScript(Otc.skript(t))))
            assertEquals("Fall $i", o.getString("skriptAdresse"), Otc.adresse(t))
        }
    }

    private fun pruefeAusgabe(o: JSONObject, a: JSONObject, priv: ByteArray, einloesen: Boolean, x: ByteArray?) {
        val e = SkriptAusgabe.Entwurf(
            skript = Otc.skript(teile(o)),
            an = hex(a.getString("an")),
            betrag = a.getString("betrag").toLong(),
            gebuehr = a.getString("gebuehr").toLong(),
            nonce = a.getString("nonce").toLong(),
            validFrom = a.getLong("validFrom"),
            validUntil = a.getLong("validUntil"),
            memo = hex(a.getString("memo")),
        )
        val sighash = SkriptAusgabe.sighash(e)
        assertEquals(a.getString("sighash"), Hex.toHex(sighash))
        val sig = Wallet.sign(sighash, priv)
        assertEquals(a.getString("signatur"), Hex.toHex(sig))
        val witness = if (einloesen) Otc.witnessEinloesen(sig, x!!) else Otc.witnessZurueck(sig)
        assertEquals(a.getString("witness"), Hex.toHex(witness))
        val roh = SkriptAusgabe.serialisieren(e, witness)
        assertEquals(a.getString("roh"), Hex.toHex(roh))
        assertEquals(a.getString("txid"), Hex.toHex(SkriptAusgabe.txid(roh)))
    }

    @Test
    fun einloesenWieDerKern() {
        var geprueft = 0
        for (i in 0 until faelle.length()) {
            val o = faelle.getJSONObject(i)
            if (o.isNull("einloesen")) continue
            val k = Wallet.fromMnemonic(o.getString("kaeuferWoerter"))
            pruefeAusgabe(o, o.getJSONObject("einloesen"), k.privateKey, true, hex(o.getString("geheimnis")))
            geprueft++
        }
        assertTrue(geprueft >= 2)
    }

    @Test
    fun zurueckholenWieDerKern() {
        for (i in 0 until faelle.length()) {
            val o = faelle.getJSONObject(i)
            val v = Wallet.fromMnemonic(o.getString("verkaeuferWoerter"))
            pruefeAusgabe(o, o.getJSONObject("zurueck"), v.privateKey, false, null)
        }
    }

    @Test
    fun falscheTeileWerdenAbgelehnt() {
        val a = ByteArray(32) { 1 }
        val b = ByteArray(32) { 2 }
        val h = ByteArray(32) { 3 }
        assertThrows(IllegalArgumentException::class.java) { Otc.Teile(a, a, h, 100, 88) }
        assertThrows(IllegalArgumentException::class.java) { Otc.Teile(a, b, h, 100, 100) }
        assertThrows(IllegalArgumentException::class.java) { Otc.Teile(a, b, h, 100, 0) }
        assertThrows(IllegalArgumentException::class.java) { Otc.Teile(a, b, ByteArray(31), 100, 88) }
        assertThrows(IllegalArgumentException::class.java) { Otc.Teile(a, b, h, 0x1_0000_0000L, 88) }
    }

    @Test
    fun falschesGeheimnisPasstNicht() {
        val o = faelle.getJSONObject(0)
        val x = hex(o.getString("geheimnis"))
        x[0] = (x[0].toInt() xor 1).toByte()
        assertFalse(Otc.passt(x, hex(o.getString("hash"))))
    }

    @Test
    fun witnessFormen() {
        val sig = ByteArray(64) { 9 }
        val x = ByteArray(32) { 7 }
        val e = Otc.witnessEinloesen(sig, x)
        assertEquals(1 + 64 + 1 + 32 + 1 + 1 + 1, e.size)
        val z = Otc.witnessZurueck(sig)
        assertArrayEquals(byteArrayOf(0, 0, 0, 1), z.copyOfRange(0, 4))
        assertEquals(1 + 1 + 1 + 1 + 64 + 1, z.size)
    }
}
