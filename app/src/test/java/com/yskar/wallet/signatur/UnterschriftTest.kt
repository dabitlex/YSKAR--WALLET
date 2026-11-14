package com.yskar.wallet.signatur

import com.yskar.wallet.kern.Wallet
import com.yskar.wallet.signatur.Nachricht.Ablehnung
import com.yskar.wallet.signatur.Nachricht.KOPF
import com.yskar.wallet.signatur.Unterschrift.Ergebnis
import com.yskar.wallet.signatur.Unterschrift.Stoerung
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * Vom geoeffneten Tresor zur Signatur: Geprueft wird unmittelbar vor dem
 * Unterschreiben, und jede Gegenprobe bricht ab.
 *
 * Die Woerter sind der oeffentliche BIP39-Testvektor (REGELN.md, Regel 3).
 */
class UnterschriftTest {

    private val woerterText =
        "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about"
    private fun woerter() = woerterText.toByteArray(Charsets.UTF_8)
    private val kp = Wallet.fromMnemonic(woerterText)
    private val andere = Wallet.fromMnemonic("zoo zoo zoo zoo zoo zoo zoo zoo zoo zoo zoo wrong")
    private val jetzt = Instant.parse("2026-11-02T11:51:00Z").toEpochMilli()
    private val cb = "https://lab.yskar.app/api/auth/wallet"

    private fun anmeldung(address: String = kp.address, expires: String = "2026-11-02T11:55:00Z") = listOf(
        KOPF, "purpose: login", "domain: lab.yskar.app", "address: $address",
        "nonce: 9f2c4e1ab37d4c6e8a05b1d2e3f4a5b6", "issued: 2026-11-02T11:50:00Z", "expires: $expires",
    ).joinToString("\n")

    private fun code(msg: String, rueckruf: String = cb) =
        "yskar:login?msg=${Nachricht.textBase64url(msg)}&cb=${Nachricht.textBase64url(rueckruf)}"

    // ------------------------------------------------------------------ Text

    @Test
    fun `freier Text wird mit Kopfzeilen signiert und besteht die Pruefung`() {
        val e = Unterschrift.text("Hello YSKAR", woerter(), kp.address, jetzt) as Ergebnis.Fertig
        assertEquals("$KOPF\npurpose: text\n\nHello YSKAR", e.signiert.message)
        assertEquals(kp.address, e.signiert.address)
        assertEquals(Nachricht.Echtheit.GUELTIG, Nachricht.pruefen(e.signiert))
        assertNull(e.anmeldung)
        assertNull("freier Text hat keinen Rueckruf", e.rueckruf)
    }

    @Test
    fun `eine eingefuegte Anmeldung wird signiert, aber nirgendwohin gesendet`() {
        val e = Unterschrift.text(anmeldung(), woerter(), kp.address, jetzt) as Ergebnis.Fertig
        assertEquals(anmeldung(), e.signiert.message)
        assertEquals("lab.yskar.app", e.anmeldung!!.domain)
        assertNull(e.rueckruf)
    }

    @Test
    fun `was die Pruefung ablehnt, wird nicht signiert`() {
        assertEquals(Ergebnis.Abgelehnt(Ablehnung.HEX), Unterschrift.text("ab".repeat(40), woerter(), kp.address, jetzt))
        assertEquals(Ergebnis.Abgelehnt(Ablehnung.LEER), Unterschrift.text("  ", woerter(), kp.address, jetzt))
        assertEquals(
            Ergebnis.Abgelehnt(Ablehnung.ADRESSE_FREMD),
            Unterschrift.text(anmeldung(address = andere.address), woerter(), kp.address, jetzt),
        )
    }

    // ------------------------------------------------------------------ Code

    @Test
    fun `eine Anmeldung aus dem Code traegt ihren Rueckruf mit`() {
        val e = Unterschrift.ausCode(code(anmeldung()), woerter(), kp.address, jetzt) as Ergebnis.Fertig
        assertEquals(anmeldung(), e.signiert.message)
        assertEquals(cb, e.rueckruf)
        assertEquals("lab.yskar.app", e.anmeldung!!.domain)
        assertEquals(Nachricht.Echtheit.GUELTIG, Nachricht.pruefen(e.signiert))
    }

    @Test
    fun `eine Anfrage, die waehrend der PIN-Eingabe ablaeuft, wird nicht mehr signiert`() {
        // Beim Ansehen war sie gueltig ...
        assertTrue(Nachricht.anmeldungAusCode(code(anmeldung()), kp.address, jetzt) is Nachricht.QrPruefung.Ok)
        // ... beim Unterschreiben, fuenf Minuten spaeter, nicht mehr.
        val spaeter = Instant.parse("2026-11-02T11:56:00Z").toEpochMilli()
        assertEquals(
            Ergebnis.Abgelehnt(Ablehnung.ABGELAUFEN),
            Unterschrift.ausCode(code(anmeldung()), woerter(), kp.address, spaeter),
        )
    }

    @Test
    fun `ein Code mit fremdem Rueckruf wird nicht signiert - und sagt, wohin er wollte`() {
        val e = Unterschrift.ausCode(code(anmeldung(), "https://evil.com/x"), woerter(), kp.address, jetzt)
            as Ergebnis.Abgelehnt
        assertEquals(Ablehnung.RUECKRUF_HOST, e.grund)
        assertEquals("lab.yskar.app", e.domain)
        assertEquals("evil.com", e.rueckrufHost)
    }

    @Test
    fun `freier Text in einem Code ist keine Anmeldung`() {
        assertEquals(
            Ergebnis.Abgelehnt(Ablehnung.KEIN_LOGIN),
            Unterschrift.ausCode(code("$KOPF\npurpose: text\n\nhallo"), woerter(), kp.address, jetzt),
        )
    }

    // ----------------------------------------------------------- Gegenproben

    @Test
    fun `passt der Schluessel nicht zur angezeigten Adresse, wird nichts signiert`() {
        // Der Tresor enthielte andere Woerter als die, zu denen die Adresse gehoert.
        assertEquals(
            Ergebnis.Gestoert(Stoerung.SCHLUESSEL_PASST_NICHT),
            Unterschrift.text("Hello", woerter(), andere.address, jetzt),
        )
        assertEquals(
            Ergebnis.Gestoert(Stoerung.SCHLUESSEL_PASST_NICHT),
            Unterschrift.ausCode(code(anmeldung(address = andere.address)), woerter(), andere.address, jetzt),
        )
    }

    @Test
    fun `unbrauchbare Woerter ergeben eine Stoerung, keinen Absturz`() {
        assertEquals(
            Ergebnis.Gestoert(Stoerung.WOERTER_UNBRAUCHBAR),
            Unterschrift.text("Hello", "kein gueltiger satz".toByteArray(), kp.address, jetzt),
        )
        assertEquals(
            Ergebnis.Gestoert(Stoerung.WOERTER_UNBRAUCHBAR),
            Unterschrift.text("Hello", ByteArray(0), kp.address, jetzt),
        )
    }

    @Test
    fun `die Woerter des Aufrufers bleiben unangetastet - er ueberschreibt sie selbst`() {
        val w = woerter()
        Unterschrift.text("Hello", w, kp.address, jetzt)
        assertEquals(woerterText, String(w, Charsets.UTF_8))
    }
}
