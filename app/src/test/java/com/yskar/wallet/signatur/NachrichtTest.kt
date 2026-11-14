package com.yskar.wallet.signatur

import com.yskar.wallet.kern.Wallet
import com.yskar.wallet.signatur.Nachricht.Ablehnung
import com.yskar.wallet.signatur.Nachricht.Echtheit
import com.yskar.wallet.signatur.Nachricht.KOPF
import com.yskar.wallet.signatur.Nachricht.Pruefung
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.time.Instant

/**
 * Nachrichten signieren: Format, Ablehnungsregeln und feste Testvektoren.
 *
 * Port von `tests/nachricht.test.ts` der bestehenden Wallet - dieselben
 * Faelle, dieselben Erwartungen.
 *
 * DIE GEGENPROBE laeuft mit der Ed25519-Umsetzung des JDK (`java.security`),
 * also unabhaengig von BouncyCastle, mit dem die Wallet signiert. Ed25519
 * ist deterministisch: Dieselben Bytes ergeben mit demselben Schluessel
 * dieselbe Signatur, gleich wer rechnet. Stimmen beide ueberein, signiert
 * dieser Port so wie jede andere korrekte Umsetzung - auch die der Plattform.
 *
 * Die Woerter sind der oeffentliche BIP39-Testvektor und eine zweite
 * oeffentliche Probe (REGELN.md, Regel 3). Dahinter liegt kein Guthaben.
 */
class NachrichtTest {

    private val kp = Wallet.fromMnemonic(
        "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about",
    )
    private val andere = Wallet.fromMnemonic("zoo zoo zoo zoo zoo zoo zoo zoo zoo zoo zoo wrong")
    private val jetzt = Instant.parse("2026-11-02T11:51:00Z").toEpochMilli()

    private fun anmeldung(
        domain: String = "lab.yskar.app",
        address: String = kp.address,
        nonce: String = "9f2c4e1ab37d4c6e8a05b1d2e3f4a5b6",
        issued: String = "2026-11-02T11:50:00Z",
        expires: String = "2026-11-02T11:55:00Z",
    ): String = listOf(
        KOPF, "purpose: login",
        "domain: $domain",
        "address: $address",
        "nonce: $nonce",
        "issued: $issued",
        "expires: $expires",
    ).joinToString("\n")

    private val textNachricht = "$KOPF\npurpose: text\n\nHello YSKAR"

    // ------------------------------------------------------ JDK als Gegenprobe

    private fun hexBytes(hex: String) =
        ByteArray(hex.length / 2) { hex.substring(2 * it, 2 * it + 2).toInt(16).toByte() }

    private fun hex(b: ByteArray) = b.joinToString("") { "%02x".format(it) }

    /** Prueft mit dem JDK, nicht mit BouncyCastle. SPKI-Huelle nach RFC 8410. */
    private fun jdkPrueft(nachricht: String, sigHex: String, pkHex: String): Boolean {
        val spki = hexBytes("302a300506032b6570032100") + hexBytes(pkHex)
        val schluessel = KeyFactory.getInstance("Ed25519").generatePublic(X509EncodedKeySpec(spki))
        return Signature.getInstance("Ed25519").run {
            initVerify(schluessel)
            update(nachricht.toByteArray(Charsets.UTF_8))
            verify(hexBytes(sigHex))
        }
    }

    /** Signiert mit dem JDK. PKCS8-Huelle nach RFC 8410. */
    private fun jdkSigniert(nachricht: String, privateKey: ByteArray): String {
        val pkcs8 = hexBytes("302e020100300506032b657004220420") + privateKey
        val schluessel = KeyFactory.getInstance("Ed25519").generatePrivate(PKCS8EncodedKeySpec(pkcs8))
        return Signature.getInstance("Ed25519").run {
            initSign(schluessel)
            update(nachricht.toByteArray(Charsets.UTF_8))
            hex(sign())
        }
    }

    // ---------------------------------------------------------------- Vektoren

    @Test
    fun `Testschluessel - Adresse und oeffentlicher Schluessel wie in der Vorlage`() {
        assertEquals("ysr1pcy6c8vw2fpp49qh4pg3w348qz6nn6vw2fg7f0", kp.address)
        assertEquals("34792b244cfe851d8b3fc62ccb3033297ea2ca5bc31119b397bc3ec4132a751d", hex(kp.publicKey))
    }

    @Test
    fun `Signatur ueber die rohen UTF-8-Bytes, Gegenprobe mit dem JDK`() {
        val s = Nachricht.signieren(textNachricht, kp.privateKey, kp.publicKey)
        assertEquals(kp.address, s.address)
        assertEquals(hex(kp.publicKey), s.publicKey)
        assertTrue(Regex("^[0-9a-f]{128}$").matches(s.signature))
        assertEquals(textNachricht, s.message)
        assertTrue("das JDK bestaetigt die Signatur", jdkPrueft(s.message, s.signature, s.publicKey))

        // Das JDK signiert dieselben Bytes mit demselben Schluessel -> dieselbe Signatur.
        assertEquals(jdkSigniert(textNachricht, kp.privateKey), s.signature)
    }

    @Test
    fun `Umlaute und Emoji - signiert werden die UTF-8-Bytes, nicht UTF-16`() {
        val n = "$KOPF\npurpose: text\n\nGrüße aus Hessen ✓ 🙂"
        val s = Nachricht.signieren(n, kp.privateKey, kp.publicKey)
        assertTrue(jdkPrueft(n, s.signature, s.publicKey))
        assertEquals(jdkSigniert(n, kp.privateKey), s.signature)
    }

    @Test
    fun `Anmeldenachricht - Signatur vom JDK bestaetigt, eigene Pruefung gueltig`() {
        val n = anmeldung()
        val p = Nachricht.vorbereiten(n, kp.address, jetzt)
        assertTrue(p is Pruefung.Login)
        val s = Nachricht.signieren((p as Pruefung.Login).nachricht, kp.privateKey, kp.publicKey)
        assertEquals("Login wird Byte fuer Byte so signiert, wie er kam", n, s.message)
        assertTrue(jdkPrueft(n, s.signature, s.publicKey))
        assertEquals(jdkSigniert(n, kp.privateKey), s.signature)
        assertEquals(Echtheit.GUELTIG, Nachricht.pruefen(s))
    }

    @Test
    fun `eine Nachricht ist nie eine Ueberweisung`() {
        // Was unterschrieben wird, beginnt immer mit der Kopfzeile in Klartext.
        // Die Bytes einer Ueberweisung beginnen mit ihrer Fassung (1 oder 2).
        val bytes = textNachricht.toByteArray(Charsets.UTF_8)
        assertEquals('Y'.code.toByte(), bytes[0])
        assertTrue(bytes[0].toInt() != 1 && bytes[0].toInt() != 2)
    }

    // -------------------------------------------------------- Format und Pruefen

    @Test
    fun `freier Text - die Wallet setzt die Kopfzeilen davor`() {
        val p = Nachricht.vorbereiten("Hello YSKAR", kp.address, jetzt) as Pruefung.Text
        assertEquals(textNachricht, p.nachricht)
        assertTrue(p.ergaenzt)
    }

    @Test
    fun `freier Text - Wagenruecklauf und Leerzeichen am Zeilenende werden entfernt`() {
        val p = Nachricht.vorbereiten("Zeile eins  \r\nZeile zwei\t\r\n", kp.address, jetzt) as Pruefung.Text
        assertEquals("$KOPF\npurpose: text\n\nZeile eins\nZeile zwei", p.nachricht)
    }

    @Test
    fun `freier Text mit eigener Kopfzeile bleibt unveraendert`() {
        val p = Nachricht.vorbereiten(textNachricht, kp.address, jetzt) as Pruefung.Text
        assertEquals(textNachricht, p.nachricht)
        assertFalse(p.ergaenzt)
    }

    @Test
    fun `Text, der nur mit den Worten der Kopfzeile beginnt, ist freier Text`() {
        val p = Nachricht.vorbereiten("YSKAR Signed Messages are useful", kp.address, jetzt)
        assertTrue(p is Pruefung.Text && p.ergaenzt)
    }

    @Test
    fun `JSON zum Kopieren - feste Reihenfolge, wieder lesbar, gueltig`() {
        val s = Nachricht.signieren(textNachricht, kp.privateKey, kp.publicKey)
        val j = Nachricht.alsJson(s)
        val stellen = listOf("\"address\"", "\"publicKey\"", "\"signature\"", "\"message\"").map { j.indexOf(it) }
        assertTrue("alle vier Felder", stellen.all { it >= 0 })
        assertEquals("in dieser Reihenfolge", stellen.sorted(), stellen)
        assertEquals(4, JSONObject(j).length())
        val zurueck = Nachricht.ausJson(j)
        assertEquals(s, zurueck)
        assertEquals(Echtheit.GUELTIG, Nachricht.pruefen(zurueck!!))
    }

    @Test
    fun `JSON traegt Zeilenumbrueche und Anfuehrungszeichen unbeschadet`() {
        val n = "$KOPF\npurpose: text\n\nEr sagte \"ja\" \\ und ging.\nZweite Zeile ✓"
        val s = Nachricht.signieren(n, kp.privateKey, kp.publicKey)
        val zurueck = Nachricht.ausJson(Nachricht.alsJson(s))!!
        assertEquals(n, zurueck.message)
        assertEquals(Echtheit.GUELTIG, Nachricht.pruefen(zurueck))
    }

    @Test
    fun `die Pruefung erkennt veraenderte Nachricht, fremden Schluessel und Unsinn`() {
        val s = Nachricht.signieren(textNachricht, kp.privateKey, kp.publicKey)
        assertEquals(Echtheit.UNGUELTIG, Nachricht.pruefen(s.copy(message = s.message + "!")))
        assertEquals(Echtheit.UNGUELTIG, Nachricht.pruefen(s.copy(message = s.message.replace("\n", "\r\n"))))
        assertEquals(Echtheit.ADRESSE_PASST_NICHT, Nachricht.pruefen(s.copy(address = andere.address)))
        assertEquals(
            Echtheit.UNGUELTIG,
            Nachricht.pruefen(s.copy(publicKey = hex(andere.publicKey), address = andere.address)),
        )
        assertEquals(Echtheit.FORMAT, Nachricht.pruefen(s.copy(signature = "zz")))
        assertNull(Nachricht.ausJson("{\"address\":1}"))
        assertNull(Nachricht.ausJson("kein json"))
    }

    // -------------------------------------------------------- Ablehnungsregeln

    private fun grund(
        eingabe: String,
        qr: Boolean = false,
        um: Long = jetzt,
        adresse: String = kp.address,
    ): Any = when (val p = Nachricht.vorbereiten(eingabe, adresse, um, qr)) {
        is Pruefung.Abgelehnt -> p.grund
        else -> "ok"
    }

    @Test
    fun `Ablehnung - falsche oder fremde Domain`() {
        assertEquals(Ablehnung.DOMAIN, grund(anmeldung(domain = "evil.com")))
        assertEquals(Ablehnung.DOMAIN, grund(anmeldung(domain = "yskar.app.evil.com")))
        assertEquals(Ablehnung.DOMAIN, grund(anmeldung(domain = "notyskar.app")))
        assertEquals(Ablehnung.DOMAIN, grund(anmeldung(domain = "LAB.YSKAR.APP")))
        assertEquals(Ablehnung.DOMAIN, grund(anmeldung(domain = "lab.yskar.app:443")))
        assertEquals("ok", grund(anmeldung(domain = "yskar.app")))
        assertEquals("ok", grund(anmeldung(domain = "lab.yskar.app")))
    }

    @Test
    fun `erlaubte Hosts - nur yskar_app und Subdomains`() {
        assertTrue(Nachricht.hostErlaubt("yskar.app"))
        assertTrue(Nachricht.hostErlaubt("lab.yskar.app"))
        assertFalse(Nachricht.hostErlaubt("yskar.app.example.com"))
        assertFalse(Nachricht.hostErlaubt("xyskar.app"))
        assertFalse(Nachricht.hostErlaubt("https://lab.yskar.app"))
        assertFalse(Nachricht.hostErlaubt("lab.yskar.app/"))
        assertFalse(Nachricht.hostErlaubt(""))
    }

    @Test
    fun `Ablehnung - abgelaufen und zu lange gueltig`() {
        assertEquals(
            Ablehnung.ABGELAUFEN,
            grund(anmeldung(), um = Instant.parse("2026-11-02T11:55:00Z").toEpochMilli()),
        )
        assertEquals(
            Ablehnung.ABGELAUFEN,
            grund(anmeldung(), um = Instant.parse("2026-11-02T12:30:00Z").toEpochMilli()),
        )
        fun zeit(ms: Long) = Instant.ofEpochMilli(ms).toString().substring(0, 19) + "Z"
        assertEquals(
            Ablehnung.ZU_LANGE,
            grund(anmeldung(expires = zeit(jetzt + Nachricht.GUELTIG_MAX_MS + 1000))),
        )
        assertEquals("ok", grund(anmeldung(expires = zeit(jetzt + Nachricht.GUELTIG_MAX_MS))))
    }

    @Test
    fun `Ablehnung - fremde Adresse`() {
        assertEquals(Ablehnung.ADRESSE_FREMD, grund(anmeldung(address = andere.address)))
        assertEquals(Ablehnung.ADRESSE_FREMD, grund(anmeldung(address = "ysr1kaputt")))
    }

    @Test
    fun `Ablehnung - fehlende Kopfzeile beim QR-Code`() {
        assertEquals(
            Ablehnung.KOPF_FEHLT,
            grund(anmeldung().split("\n").drop(1).joinToString("\n"), qr = true),
        )
        assertEquals(Ablehnung.KOPF_FEHLT, grund("Hello", qr = true))
    }

    @Test
    fun `Ablehnung - Windows-Zeilenenden und Leerzeichen am Zeilenende bei Nachrichten mit Kopfzeile`() {
        assertEquals(Ablehnung.ZEILENENDE, grund(anmeldung().replace("\n", "\r\n")))
        assertEquals(
            Ablehnung.LEERZEICHEN,
            grund(anmeldung().replace("purpose: login", "purpose: login ")),
        )
        assertEquals(Ablehnung.LEERZEICHEN, grund("$textNachricht "))
    }

    @Test
    fun `Ablehnung - unbekannter Zweck`() {
        assertEquals(Ablehnung.ZWECK_UNBEKANNT, grund("$KOPF\npurpose: transfer\n\nx"))
        assertEquals(
            Ablehnung.ZWECK_UNBEKANNT,
            grund(anmeldung().replace("purpose: login", "purpose: Login")),
        )
    }

    @Test
    fun `Ablehnung - falscher Aufbau der Anmeldung`() {
        val z = anmeldung().split("\n")
        assertEquals("Reihenfolge", Ablehnung.AUFBAU, grund(listOf(z[0], z[1], z[3], z[2], z[4], z[5], z[6]).joinToString("\n")))
        assertEquals("Zeile fehlt", Ablehnung.AUFBAU, grund(z.take(6).joinToString("\n")))
        assertEquals("Zeile zu viel", Ablehnung.AUFBAU, grund((z + "extra: 1").joinToString("\n")))
        assertEquals("nonce zu kurz", Ablehnung.AUFBAU, grund(anmeldung(nonce = "abc")))
        assertEquals(
            "nonce gross",
            Ablehnung.AUFBAU,
            grund(anmeldung(nonce = "9F2C4E1AB37D4C6E8A05B1D2E3F4A5B6")),
        )
        assertEquals("Zeitformat", Ablehnung.AUFBAU, grund(anmeldung(expires = "2026-11-02 11:55")))
        assertEquals("kein Kalendertag", Ablehnung.AUFBAU, grund(anmeldung(expires = "2026-02-31T11:55:00Z")))
        assertEquals("Stunde 24", Ablehnung.AUFBAU, grund(anmeldung(expires = "2026-11-02T24:00:00Z")))
        assertEquals("issued nach expires", Ablehnung.AUFBAU, grund(anmeldung(issued = "2026-11-02T11:56:00Z")))
        assertEquals(Ablehnung.AUFBAU, grund("$KOPF\npurpose: text\nText ohne Leerzeile"))
        assertEquals("nur die Kopfzeile", Ablehnung.AUFBAU, grund(KOPF))
    }

    @Test
    fun `Ablehnung - Hex-Daten und Laenge`() {
        assertEquals(Ablehnung.HEX, grund("a".repeat(64)))
        assertEquals(Ablehnung.HEX, grund("0x" + "0123456789abcdef".repeat(4)))
        assertEquals(Ablehnung.HEX, grund("0123 4567 89ab cdef 0123 4567 89ab cdef"))
        assertEquals(Ablehnung.HEX, grund("$KOPF\npurpose: text\n\n${"ab".repeat(40)}"))
        assertEquals("kurzes Wort aus Hex-Buchstaben ist erlaubt", "ok", grund("cafe"))
        assertEquals("ok", grund("x".repeat(Nachricht.TEXT_MAX)))
        assertEquals(Ablehnung.ZU_LANG, grund("x".repeat(Nachricht.TEXT_MAX + 1)))
        assertEquals(Ablehnung.LEER, grund("   \n  "))
        assertEquals(Ablehnung.LEER, grund(""))
    }

    @Test
    fun `die Laenge zaehlt Zeichen, nicht Bausteine - ein Emoji ist eines`() {
        assertEquals("ok", grund("🙂".repeat(Nachricht.TEXT_MAX)))
        assertEquals(Ablehnung.ZU_LANG, grund("🙂".repeat(Nachricht.TEXT_MAX + 1)))
    }

    @Test
    fun `signieren lehnt alles ohne Kopfzeile oder mit Wagenruecklauf ab`() {
        assertThrows(IllegalArgumentException::class.java) {
            Nachricht.signieren("Hello", kp.privateKey, kp.publicKey)
        }
        assertThrows(IllegalArgumentException::class.java) {
            Nachricht.signieren(textNachricht.replace("\n", "\r\n"), kp.privateKey, kp.publicKey)
        }
        // Rohe Bytes einer Ueberweisung als Text haben keine Kopfzeile -> nie signierbar.
        val roh = String(hexBytes("0100" + "00".repeat(40)), Charsets.ISO_8859_1)
        assertThrows(IllegalArgumentException::class.java) {
            Nachricht.signieren(roh, kp.privateKey, kp.publicKey)
        }
        assertThrows(IllegalArgumentException::class.java) {
            Nachricht.signieren(KOPF, kp.privateKey, kp.publicKey)
        }
    }
}
