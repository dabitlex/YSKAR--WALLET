package com.yskar.wallet.signatur

import com.yskar.wallet.kern.Wallet
import com.yskar.wallet.netz.NetzFehler
import com.yskar.wallet.netz.Spiegel
import com.yskar.wallet.signatur.Nachricht.Ablehnung
import com.yskar.wallet.signatur.Nachricht.Echtheit
import com.yskar.wallet.signatur.Nachricht.KOPF
import com.yskar.wallet.signatur.Nachricht.QrPruefung
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * Anmelden per QR: Code lesen, Regeln fuer den Rueckruf, Antwort der
 * Plattform und ein ganzer Durchlauf gegen eine nachgebaute Plattform.
 *
 * Port von `tests/anmeldung-qr.test.ts` der bestehenden Wallet. Die Vorlage
 * prueft gegen einen kleinen Server auf dem eigenen Rechner; hier ist die
 * Plattform eine Attrappe im selben Prozess, die dasselbe tut - kein Test
 * spricht mit dem Netz (REGELN.md, Regel 18).
 */
class AnmeldecodeTest {

    private val kp = Wallet.fromMnemonic(
        "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about",
    )
    private val andere = Wallet.fromMnemonic("zoo zoo zoo zoo zoo zoo zoo zoo zoo zoo zoo wrong")
    private val jetzt = Instant.parse("2026-11-02T11:51:00Z").toEpochMilli()
    private val cb = "https://lab.yskar.app/api/auth/wallet"

    private fun nachricht(
        domain: String = "lab.yskar.app",
        address: String = kp.address,
        nonce: String = "9f2c4e1ab37d4c6e8a05b1d2e3f4a5b6",
        expires: String = "2026-11-02T11:55:00Z",
    ): String = listOf(
        KOPF, "purpose: login", "domain: $domain", "address: $address",
        "nonce: $nonce", "issued: 2026-11-02T11:50:00Z", "expires: $expires",
    ).joinToString("\n")

    private fun code(msg: String, rueckruf: String = cb) =
        "yskar:login?msg=${Nachricht.textBase64url(msg)}&cb=${Nachricht.textBase64url(rueckruf)}"

    private fun grund(c: String, um: Long = jetzt): Any =
        when (val p = Nachricht.anmeldungAusCode(c, kp.address, um)) {
            is QrPruefung.Ok -> "ok"
            is QrPruefung.Abgelehnt -> p.grund
        }

    // ------------------------------------------------------------- Code lesen

    @Test
    fun `base64url hin und zurueck, auch mit Umlauten - Unsinn wird abgelehnt`() {
        for (s in listOf("", "a", "Grüße ✓", nachricht())) {
            assertEquals(s, Nachricht.base64urlText(Nachricht.textBase64url(s)))
        }
        assertNull("kein base64url", Nachricht.base64urlText("ab+/"))
        assertNull("falsche Laenge", Nachricht.base64urlText("a"))
        assertNull("kein UTF-8", Nachricht.base64urlText("_w"))
        assertNull("aufgefuellt", Nachricht.base64urlText("YQ=="))
    }

    @Test
    fun `gueltiger Code - Nachricht, Anmeldung und Rueckruf`() {
        assertTrue(Nachricht.istAnmeldeCode(code(nachricht())))
        assertTrue(Nachricht.istAnmeldeCode("YSKAR://login?msg=a&cb=b"))
        assertTrue(Nachricht.istAnmeldeCode("  yskar:login?msg=a&cb=b\n"))
        assertFalse(Nachricht.istAnmeldeCode("yskar:${kp.address}?amount=1"))
        assertFalse(Nachricht.istAnmeldeCode("https://lab.yskar.app/yskar:login?msg=a"))
        val p = Nachricht.anmeldungAusCode(code(nachricht()), kp.address, jetzt) as QrPruefung.Ok
        assertEquals(nachricht(), p.nachricht)
        assertEquals(cb, p.rueckruf)
        assertEquals("lab.yskar.app", p.anmeldung.domain)
        assertEquals("lab.yskar.app/api/auth/wallet", Nachricht.rueckrufZiel(p.rueckruf))
    }

    // ------------------------------------------------------- Ablehnungsregeln

    @Test
    fun `Ablehnung - Rueckruf ueber http, mit Port oder Benutzername`() {
        assertEquals(Ablehnung.RUECKRUF_HTTPS, grund(code(nachricht(), "http://lab.yskar.app/api/auth/wallet")))
        assertEquals(Ablehnung.RUECKRUF_HTTPS, grund(code(nachricht(), "https://lab.yskar.app:8443/api")))
        assertEquals(Ablehnung.RUECKRUF_HTTPS, grund(code(nachricht(), "https://user:pw@lab.yskar.app/api")))
        assertEquals(Ablehnung.RUECKRUF_HTTPS, grund(code(nachricht(), "kein link")))
    }

    @Test
    fun `Ablehnung - alles, was zwei Leser verschieden deuten koennten`() {
        // Strenger als die Vorlage: Solche Adressen kommen gar nicht erst durch.
        val schief = listOf(
            "https://lab.yskar.app\\@evil.com/api",
            "https://lab.yskar.app#@evil.com/api",
            "https://evil.com#lab.yskar.app/api",
            "https://lab.yskar.app/api#teil",
            "https://LAB.yskar.app/api",
            "https://lab.yskar.app:443/api",
            "https://lab.yskar.app /api",
            "https://lab.yskar.app/a b",
            "https://lab.yskar.app/api\nHost: evil.com",
            "HTTPS://lab.yskar.app/api",
            "https:lab.yskar.app/api",
            "https:///lab.yskar.app/api",
            "https://@lab.yskar.app/api",
            "",
        )
        for (u in schief) {
            val g = Nachricht.rueckrufErlaubt(u, "lab.yskar.app")
            assertTrue("\"$u\" kam durch", g != null)
        }
    }

    @Test
    fun `Ablehnung - Host des Rueckrufs passt nicht genau zur Domain`() {
        assertEquals(Ablehnung.RUECKRUF_HOST, grund(code(nachricht(), "https://evil.com/api")))
        assertEquals(
            "andere Subdomain zaehlt nicht",
            Ablehnung.RUECKRUF_HOST,
            grund(code(nachricht(), "https://yskar.app/api")),
        )
        assertEquals(Ablehnung.RUECKRUF_HOST, grund(code(nachricht(), "https://lab.yskar.app.evil.com/api")))
        val p = Nachricht.anmeldungAusCode(code(nachricht(), "https://evil.com/x"), kp.address, jetzt)
            as QrPruefung.Abgelehnt
        assertEquals("lab.yskar.app", p.domain)
        assertEquals("evil.com", p.rueckrufHost)
        assertNull(Nachricht.rueckrufErlaubt("https://lab.yskar.app/a?b=1", "lab.yskar.app"))
        assertNull(Nachricht.rueckrufErlaubt("https://lab.yskar.app", "lab.yskar.app"))
    }

    @Test
    fun `Ablehnung - falsche Domain in der Nachricht, auch wenn der Rueckruf dazu passt`() {
        assertEquals(Ablehnung.DOMAIN, grund(code(nachricht(domain = "evil.com"), "https://evil.com/api")))
    }

    @Test
    fun `Ablehnung - abgelaufen, laenger als 15 Minuten gueltig, fremde Adresse`() {
        assertEquals(
            Ablehnung.ABGELAUFEN,
            grund(code(nachricht()), Instant.parse("2026-11-02T11:56:00Z").toEpochMilli()),
        )
        assertEquals(Ablehnung.ZU_LANGE, grund(code(nachricht(expires = "2026-11-02T12:30:00Z"))))
        assertEquals(Ablehnung.ADRESSE_FREMD, grund(code(nachricht(address = andere.address))))
    }

    @Test
    fun `Ablehnung - fehlende Kopfzeile, Windows-Zeilenenden, freier Text statt Anmeldung`() {
        assertEquals(Ablehnung.KOPF_FEHLT, grund(code(nachricht().split("\n").drop(1).joinToString("\n"))))
        assertEquals(Ablehnung.ZEILENENDE, grund(code(nachricht().replace("\n", "\r\n"))))
        assertEquals(Ablehnung.KEIN_LOGIN, grund(code("$KOPF\npurpose: text\n\nhallo")))
    }

    @Test
    fun `Ablehnung - kaputter oder doppelter Code`() {
        assertEquals(Ablehnung.QR_FORMAT, grund("yskar:login?msg=%%%&cb=x"))
        assertEquals("cb fehlt", Ablehnung.QR_FORMAT, grund("yskar:login?msg=${Nachricht.textBase64url(nachricht())}"))
        assertEquals(
            "cb doppelt",
            Ablehnung.QR_FORMAT,
            grund(code(nachricht()) + "&cb=${Nachricht.textBase64url("https://evil.com")}"),
        )
        assertEquals("msg doppelt", Ablehnung.QR_FORMAT, grund(code(nachricht()) + "&msg=YQ"))
        assertEquals(Ablehnung.QR_FORMAT, grund("yskar:${kp.address}"))
        assertEquals(Ablehnung.QR_FORMAT, grund(""))
        assertEquals("Anhang hinter dem Code", Ablehnung.QR_FORMAT, grund(code(nachricht()) + "#x"))
    }

    // ---------------------------------------------------------- Antwort lesen

    @Test
    fun `Antwort der Plattform - nur 2xx mit ok true ist Erfolg`() {
        assertEquals(Rueckruf.Ergebnis.Ok, Rueckruf.antwortLesen(200, """{"ok":true}"""))
        assertTrue(Rueckruf.antwortLesen(200, """{"ok":false}""") is Rueckruf.Ergebnis.Abgelehnt)
        assertTrue(Rueckruf.antwortLesen(200, "ok") is Rueckruf.Ergebnis.Abgelehnt)
        assertTrue(Rueckruf.antwortLesen(200, """{"ok":"true"}""") is Rueckruf.Ergebnis.Abgelehnt)
        assertTrue(Rueckruf.antwortLesen(200, "") is Rueckruf.Ergebnis.Abgelehnt)
        assertTrue(Rueckruf.antwortLesen(500, """{"ok":true}""") is Rueckruf.Ergebnis.Abgelehnt)

        val f = Rueckruf.antwortLesen(400, """{"ok":false,"error":"This sign-in code was already used."}""")
            as Rueckruf.Ergebnis.Abgelehnt
        assertEquals("This sign-in code was already used.", f.text)
        assertEquals(400, f.status)

        val lang = Rueckruf.antwortLesen(400, JSONObject().put("error", "x".repeat(500) + "\u0007").toString())
            as Rueckruf.Ergebnis.Abgelehnt
        assertEquals("auf 200 Zeichen gekuerzt", 200, lang.text!!.codePointCount(0, lang.text!!.length))

        val steuer = Rueckruf.antwortLesen(403, JSONObject().put("error", "<b>\u0000böse</b>\n").toString())
            as Rueckruf.Ergebnis.Abgelehnt
        assertEquals("Steuerzeichen raus; angezeigt wird reiner Text", "<b> böse</b>", steuer.text)

        assertTrue(Rueckruf.antwortLesen(302, "") is Rueckruf.Ergebnis.Weiterleitung)
    }

    // -------------------------------------------- die nachgebaute Plattform

    /**
     * Tut, was die Plattform tun wird: prueft Signatur, Adresse zum
     * Schluessel und Domain und nimmt jeden Nonce nur einmal an. Haelt fest,
     * was ankam - und spricht mit niemandem.
     */
    private class Plattform : Spiegel.Transport {
        val erhalten = ArrayList<Pair<String, String>>()
        private val verbraucht = HashSet<String>()

        override fun anfrage(methode: String, url: String, rumpf: String?): Spiegel.Antwort {
            erhalten.add(url to (rumpf ?: ""))
            if (methode != "POST") return Spiegel.Antwort(405, """{"ok":false,"error":"POST only"}""")
            if (url.endsWith("/umleiten")) return Spiegel.Antwort(302, "")
            if (url.endsWith("/tot")) throw NetzFehler(NetzFehler.Ursache.OFFLINE)
            val s = Nachricht.ausJson(rumpf ?: "")
                ?: return Spiegel.Antwort(400, """{"ok":false,"error":"Invalid JSON"}""")
            if (Nachricht.pruefen(s) != Echtheit.GUELTIG) {
                return Spiegel.Antwort(401, """{"ok":false,"error":"Invalid signature"}""")
            }
            val nonce = Regex("\nnonce: ([0-9a-f]{32})\n").find(s.message)?.groupValues?.get(1)
            if (nonce == null || !s.message.contains("\ndomain: lab.yskar.app\n")) {
                return Spiegel.Antwort(400, """{"ok":false,"error":"Wrong message"}""")
            }
            if (!verbraucht.add(nonce)) {
                return Spiegel.Antwort(409, """{"ok":false,"error":"This sign-in code was already used."}""")
            }
            return Spiegel.Antwort(200, """{"ok":true}""")
        }
    }

    @Test
    fun `ganzer Durchlauf - Code lesen, signieren, senden, Nonce nur einmal`() {
        val plattform = Plattform()
        val p = Nachricht.anmeldungAusCode(code(nachricht()), kp.address, jetzt) as QrPruefung.Ok
        val s = Nachricht.signieren(p.nachricht, kp.privateKey, kp.publicKey)

        assertEquals(Rueckruf.Ergebnis.Ok, Rueckruf.senden(p.rueckruf, p.anmeldung.domain, s, plattform))
        val (url, rumpf) = plattform.erhalten[0]
        assertEquals("genau an den Rueckruf", cb, url)
        assertEquals("die Nachricht kommt Byte fuer Byte an", nachricht(), Nachricht.ausJson(rumpf)!!.message)
        assertEquals("nur die vier Felder gehen hinaus", 4, JSONObject(rumpf).length())
        assertFalse("kein Schluessel im Rumpf", rumpf.contains(kp.privateKey.joinToString("") { "%02x".format(it) }))

        val zweimal = Rueckruf.senden(p.rueckruf, p.anmeldung.domain, s, plattform) as Rueckruf.Ergebnis.Abgelehnt
        assertEquals("This sign-in code was already used.", zweimal.text)

        val gefaelscht = s.copy(signature = (if (s.signature[0] == '0') "1" else "0") + s.signature.drop(1))
        val falsch = Rueckruf.senden(p.rueckruf, p.anmeldung.domain, gefaelscht, plattform)
            as Rueckruf.Ergebnis.Abgelehnt
        assertEquals("Invalid signature", falsch.text)

        assertTrue(
            "Weiterleitung wird nicht verfolgt",
            Rueckruf.senden("https://lab.yskar.app/umleiten", "lab.yskar.app", s, plattform)
                is Rueckruf.Ergebnis.Weiterleitung,
        )
    }

    @Test
    fun `nicht erreichbar - Netzfehler statt Absturz`() {
        val s = Nachricht.signieren(nachricht(), kp.privateKey, kp.publicKey)
        assertEquals(
            Rueckruf.Ergebnis.Netz,
            Rueckruf.senden("https://lab.yskar.app/tot", "lab.yskar.app", s, Plattform()),
        )
    }

    @Test
    fun `die Signatur geht nie an http, fremde Hosts oder eine andere Domain als die der Nachricht`() {
        val s = Nachricht.signieren(nachricht(), kp.privateKey, kp.publicKey)
        val plattform = Plattform()
        val faelle = listOf(
            "http://lab.yskar.app/x" to "lab.yskar.app",
            "https://evil.com/x" to "lab.yskar.app",
            "https://evil.com/x" to "evil.com",
            "https://lab.yskar.app:444/x" to "lab.yskar.app",
            "https://yskar.app/x" to "lab.yskar.app",
        )
        for ((url, domain) in faelle) {
            val r = Rueckruf.senden(url, domain, s, plattform)
            assertTrue(url, r is Rueckruf.Ergebnis.Abgelehnt && r.status == 0)
        }
        assertTrue("es wurde nichts gesendet", plattform.erhalten.isEmpty())
    }
}
