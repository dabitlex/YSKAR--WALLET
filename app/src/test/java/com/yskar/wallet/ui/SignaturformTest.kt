package com.yskar.wallet.ui

import com.yskar.wallet.signatur.Nachricht
import com.yskar.wallet.sprache.Deutsch
import com.yskar.wallet.sprache.Englisch
import com.yskar.wallet.ui.WalletModel.Ansicht
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

/** Was der Bildschirm zum Signieren in Worten sagt. */
class SignaturformTest {

    private val berlin = ZoneId.of("Europe/Berlin")
    private val ablauf = Instant.parse("2026-11-09T12:35:00Z").toEpochMilli()   // 13:35 in Berlin

    private fun vor(sekunden: Long) = ablauf - sekunden * 1000

    @Test
    fun `die Ablaufzeit steht in Minuten und als Uhrzeit des Geraets`() {
        assertEquals("in 4 Min. · 13:35", ablaufText(ablauf, vor(240), berlin, Deutsch))
        assertEquals("in 4 min · 13:35", ablaufText(ablauf, vor(240), berlin, Englisch))
        assertEquals("in 4 Min. · 12:35", ablaufText(ablauf, vor(240), ZoneId.of("UTC"), Deutsch))
    }

    @Test
    fun `aufgerundet - nie eine Minute zu wenig`() {
        assertEquals("in 5 Min. · 13:35", ablaufText(ablauf, vor(241), berlin, Deutsch))
        assertEquals("in 2 Min. · 13:35", ablaufText(ablauf, vor(61), berlin, Deutsch))
        assertEquals("in unter 1 Min. · 13:35", ablaufText(ablauf, vor(60), berlin, Deutsch))
        assertEquals("in unter 1 Min. · 13:35", ablaufText(ablauf, vor(1), berlin, Deutsch))
    }

    @Test
    fun `vorbei ist vorbei`() {
        assertEquals("abgelaufen", ablaufText(ablauf, ablauf, berlin, Deutsch))
        assertEquals("abgelaufen", ablaufText(ablauf, ablauf + 60_000, berlin, Deutsch))
        assertEquals("expired", ablaufText(ablauf, ablauf, berlin, Englisch))
    }

    @Test
    fun `der Scanner nimmt nur, was ein Anmelde-Code sein koennte`() {
        val code = "yskar:login?msg=YQ&cb=Yg"
        assertEquals(code, anmeldecodeAus(code))
        assertEquals("mit Leerraum drumherum", code, anmeldecodeAus("  $code\n"))
        assertNull("eine Adresse", anmeldecodeAus("ysr17g5ywa80hf2nyjkp59ha6a3ycs0jtsule8a6v4"))
        assertNull("eine Zahlungsanforderung", anmeldecodeAus("yskar:ysr17g5ywa80hf2nyjkp59ha6a3ycs0jtsule8a6v4?amount=1"))
        assertNull("ein Weblink", anmeldecodeAus("https://lab.yskar.app/login"))
        assertNull("leer", anmeldecodeAus(""))
        assertNull("viel zu lang", anmeldecodeAus("yskar:login?msg=" + "a".repeat(5_000)))
        // Dass er einer sein KOENNTE, heisst nicht, dass er gilt.
        assertTrue(
            Nachricht.anmeldungAusCode(code, "ysr17g5ywa80hf2nyjkp59ha6a3ycs0jtsule8a6v4", 0)
                is Nachricht.QrPruefung.Abgelehnt,
        )
    }

    @Test
    fun `Anmelden und Signieren sperren im Hintergrund und kommen nicht wieder`() {
        // Eine Anfrage laeuft in Minuten ab; nach der Sperre beginnt man neu.
        for (a in listOf(Ansicht.Signieren, Ansicht.Anmelden(null), Ansicht.Anmelden("yskar:login?msg=YQ&cb=Yg"))) {
            assertTrue("$a", Hintergrundregel.sperrt(a, hatWallet = true))
            assertNull("$a", Hintergrundregel.rueckkehr(a))
        }
    }
}
