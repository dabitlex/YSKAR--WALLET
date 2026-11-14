package com.yskar.wallet.kern

import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Vergleich mit der bestehenden TypeScript-Wallet.
 *
 * tests/vergleichswerte.json stammt unveraendert aus dem Repo
 * dabitlex/YSKAR--WALLET. Jeder Wert darin wurde von der laufenden Wallet
 * erzeugt. Stimmt hier etwas nicht Byte fuer Byte, ist der Port falsch -
 * nicht der Vektor.
 *
 * Diese Tests laufen auf der JVM, nicht auf dem Geraet. Darum darf im Paket
 * kern keine Android-API vorkommen.
 */
class VektorenTest {

    private data class Vektor(
        val woerter: String,
        val adresse: String,
        val oeffentlich: String,
        val pfad: String,
        val adresse1: String,
    )

    private val vektoren: List<Vektor> by lazy {
        val text = javaClass.getResourceAsStream("/vergleichswerte.json")!!
            .use { it.readBytes() }
            .toString(Charsets.UTF_8)
        val arr = JSONArray(text)
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            Vektor(
                woerter = o.getString("woerter"),
                adresse = o.getString("adresse"),
                oeffentlich = o.getString("oeffentlich"),
                pfad = o.getString("pfad"),
                adresse1 = o.getString("adresse1"),
            )
        }
    }

    /**
     * Schutz vor einem leeren Lauf: Wenn das Einlesen scheitert, sollen die
     * folgenden Tests nicht stillschweigend ueber eine leere Liste gruen
     * werden.
     */
    @Test
    fun vektorenSindVollstaendigGeladen() {
        assertEquals("Anzahl der Vektoren", 5, vektoren.size)
        for (v in vektoren) {
            assertTrue(v.woerter.split(' ').size >= 12)
            assertEquals(64, v.oeffentlich.length)
            assertTrue(v.adresse.startsWith("ysr1"))
            assertTrue(v.adresse1.startsWith("ysr1"))
        }
    }

    @Test
    fun wortlisteIstEcht() {
        assertEquals(2048, Bip39.wordlist.size)
        assertEquals("abandon", Bip39.wordlist.first())
        assertEquals("zoo", Bip39.wordlist.last())
    }

    @Test
    fun merkwoerterErgebenDenOeffentlichenSchluessel() {
        for (v in vektoren) {
            val kp = Wallet.fromMnemonic(v.woerter)
            assertEquals(
                "pubkey fuer \"${v.woerter.take(24)}…\"",
                v.oeffentlich,
                Hex.toHex(kp.publicKey),
            )
        }
    }

    @Test
    fun merkwoerterErgebenDieAdresse() {
        for (v in vektoren) {
            val kp = Wallet.fromMnemonic(v.woerter)
            assertEquals("Adresse Index 0", v.adresse, kp.address)
            assertEquals("Pfad", v.pfad, kp.path)
            assertEquals(20, kp.addressRaw.size)
        }
    }

    @Test
    fun zweiterSchluesselDerselbenWoerter() {
        for (v in vektoren) {
            val kp = Wallet.fromMnemonic(v.woerter, index = 1)
            assertEquals("Adresse Index 1", v.adresse1, kp.address)
            assertEquals("m/44'/9077'/0'/0'/1'", kp.path)
        }
    }

    @Test
    fun adresseLaesstSichWiederEinlesen() {
        for (v in vektoren) {
            val roh = Address.decode(v.adresse)
            assertEquals(20, roh.size)
            assertEquals(v.adresse, Address.encode(roh))
            assertEquals(Address.V0, Address.rawVersion(roh))
            assertTrue(Address.isValid(v.adresse))
        }
    }

    @Test
    fun adresseHatVierzigZweiZeichen() {
        for (v in vektoren) {
            assertEquals("Laenge von ${v.adresse}", 42, v.adresse.length)
        }
    }

    @Test
    fun passphraseErgibtEineAndereWallet() {
        val v = vektoren.first()
        val ohne = Wallet.fromMnemonic(v.woerter)
        val mit = Wallet.fromMnemonic(v.woerter, passphrase = "25. Wort")
        assertEquals(v.adresse, ohne.address)
        assertFalse("Passphrase muss die Wallet aendern", ohne.address == mit.address)
    }

    @Test
    fun schreibweiseUndLeerzeichenSindEgal() {
        val v = vektoren.first()
        val entstellt = "  " + v.woerter.uppercase().replace(" ", "   ") + "\n"
        assertEquals(v.adresse, Wallet.fromMnemonic(entstellt).address)
    }
}

/**
 * Pruefsummen und Grenzfaelle - unabhaengig von den Vektoren.
 */
class KernRegelnTest {

    @Test
    fun falschesLetztesWortWirdAbgelehnt() {
        val gut = "abandon abandon abandon abandon abandon abandon " +
            "abandon abandon abandon abandon abandon about"
        val schlecht = "abandon abandon abandon abandon abandon abandon " +
            "abandon abandon abandon abandon abandon abandon"
        assertTrue(Bip39.isValid(gut))
        assertFalse("Pruefsumme muss greifen", Bip39.isValid(schlecht))
    }

    @Test
    fun unbekanntesWortWirdAbgelehnt() {
        val s = "kartoffel abandon abandon abandon abandon abandon " +
            "abandon abandon abandon abandon abandon about"
        assertFalse(Bip39.isValid(s))
    }

    @Test
    fun falscheWortzahlWirdAbgelehnt() {
        assertFalse(Bip39.isValid("abandon abandon about"))
        assertFalse(Bip39.isValid(""))
    }

    @Test
    fun entropieUndWoerterSindUmkehrbar() {
        val e = ByteArray(16)
        assertEquals(
            "abandon abandon abandon abandon abandon abandon " +
                "abandon abandon abandon abandon abandon about",
            Bip39.fromEntropy(e),
        )
    }

    @Test
    fun bech32mLehntGemischteSchreibweiseAb() {
        val gut = "ysr1pcy6c8vw2fpp49qh4pg3w348qz6nn6vw2fg7f0"
        assertTrue(Address.isValid(gut))
        assertFalse(Address.isValid(gut.replaceFirst("ysr", "YSr")))
    }

    @Test
    fun bech32mLehntVerfaelschteAdresseAb() {
        val gut = "ysr1pcy6c8vw2fpp49qh4pg3w348qz6nn6vw2fg7f0"
        // Ein Zeichen im Datenteil kippen: die Pruefsumme muss das merken.
        val kaputt = gut.dropLast(1) + if (gut.last() == 'q') 'p' else 'q'
        assertFalse(Address.isValid(kaputt))
    }

    @Test
    fun falschesPraefixWirdAbgelehnt() {
        val roh = Address.decode("ysr1pcy6c8vw2fpp49qh4pg3w348qz6nn6vw2fg7f0")
        val test = Address.encode(roh, Params.HRP_TESTNET)
        assertTrue(test.startsWith("tysr1"))
        assertFalse("Testnetzadresse darf im Mainnet nicht gelten", Address.isValid(test))
        assertTrue(Address.isValid(test, Params.HRP_TESTNET))
    }

    @Test
    fun versionEinsAdresseHat63Zeichen() {
        val script = ByteArray(40) { it.toByte() }
        val text = Address.forScript(script)
        assertEquals(63, text.length)
        assertTrue(text.startsWith("ysr1p"))
        val roh = Address.decode(text)
        assertEquals(33, roh.size)
        assertEquals(Address.V1, Address.rawVersion(roh))
        assertEquals(text, Address.encode(roh))
    }

    @Test
    fun rohformenKoennenNichtKollidieren() {
        assertEquals(20, Address.ZERO.size)
        assertTrue(Address.istAuszahlungsAdresse(Address.ZERO))
        val v1 = Address.rawFromScript(ByteArray(8))
        assertFalse(Address.istAuszahlungsAdresse(v1))
    }

    @Test
    fun codecSchreibtLittleEndian() {
        val b = Writer().u8(0x01).u16(0x0302).u32(0x07060504).finish()
        assertEquals("01020304050607", Hex.toHex(b))
        val r = Reader(b)
        assertEquals(0x01, r.u8())
        assertEquals(0x0302, r.u16())
        assertEquals(0x07060504L, r.u32())
        assertEquals(0, r.rest)
    }

    @Test
    fun codecU64RundUm() {
        val v = 2_100_000_000_000_000L
        val b = Writer().u64(v).finish()
        assertEquals(8, b.size)
        assertEquals(v, Reader(b).u64())
    }

    @Test
    fun hexNimmtPostgresSchreibweise() {
        assertEquals("ab12", Hex.toHex(Hex.fromHex("\\xab12")))
        assertEquals("ab12", Hex.toHex(Hex.fromHex("0xAB12")))
    }

    @Test
    fun merkleReichtEinzelnenKnotenUnveraendertHoch() {
        val a = ByteArray(32) { 1 }
        val b = ByteArray(32) { 2 }
        val c = ByteArray(32) { 3 }
        // Bei drei Blaettern wird das dritte unveraendert hochgereicht,
        // nicht verdoppelt (CVE-2012-2459).
        val blatt = { x: ByteArray -> Hashes.sha256d(byteArrayOf(0x00) + x) }
        val knoten = { l: ByteArray, r: ByteArray ->
            Hashes.sha256d(byteArrayOf(0x01) + l + r)
        }
        val erwartet = knoten(knoten(blatt(a), blatt(b)), blatt(c))
        assertEquals(Hex.toHex(erwartet), Hex.toHex(Hashes.merkleRoot(listOf(a, b, c))))
        assertEquals(32, Hashes.merkleRoot(emptyList()).size)
    }
}
