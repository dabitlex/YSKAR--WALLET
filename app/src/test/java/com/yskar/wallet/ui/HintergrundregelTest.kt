package com.yskar.wallet.ui

import com.yskar.wallet.netz.Senden
import com.yskar.wallet.ui.WalletModel.Ansicht
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Was die App im Hintergrund sperrt - und was sie danach wieder zeigt.
 *
 * Die Woerter hier sind der oeffentliche BIP39-Testvektor (REGELN.md,
 * Regel 3).
 */
class HintergrundregelTest {

    private val vektor = List(11) { "abandon" } + "about"
    private val fertig = Ansicht.SendenFertig(
        ergebnis = Senden.Ergebnis.Fehler("nur ein Platzhalter"),
        entwurf = entwurf(),
    )

    /** Irgendein Entwurf - hier zaehlt nur, dass es einer ist. Gesendet wird nichts. */
    private fun entwurf() = Senden.Entwurf(
        empfaenger = "ysr1platzhalter",
        empfaengerRoh = ByteArray(0),
        betrag = 1,
        gebuehr = 1,
        notiz = ByteArray(0),
        nonce = 0,
        fassung = 1,
        bytes = 100,
    )

    // ------------------------------------------------- mit Wallet: gesperrt

    @Test
    fun `mit einer Wallet sperrt jede offene Ansicht`() {
        val offene = listOf(
            Ansicht.Wallet,
            Ansicht.Empfangen,
            Ansicht.Verlauf,
            Ansicht.Einstellungen,
            Ansicht.Selbsttest,
            Ansicht.SendenEingabe,
            Ansicht.Einfuehrung(ausMehr = true),
            Ansicht.Einfuehrung(ausMehr = false),
            Ansicht.WoerterAnsehen(vektor),
            fertig,
        )
        for (a in offene) assertTrue("$a", Hintergrundregel.sperrt(a, hatWallet = true))
    }

    @Test
    fun `die zwoelf Woerter unter Einstellungen bleiben nicht stehen`() {
        assertTrue(Hintergrundregel.sperrt(Ansicht.WoerterAnsehen(vektor), hatWallet = true))
        assertNull(Hintergrundregel.rueckkehr(Ansicht.WoerterAnsehen(vektor)))
    }

    @Test
    fun `was schon gesperrt ist, wird nicht noch einmal gesperrt`() {
        assertFalse(Hintergrundregel.sperrt(Ansicht.Sperre, hatWallet = true))
        assertFalse(Hintergrundregel.sperrt(Ansicht.Laedt, hatWallet = true))
    }

    // ---------------------------------------------- ohne Wallet: bleibt stehen

    @Test
    fun `ohne Wallet gibt es nichts, wohin gesperrt werden koennte`() {
        val einrichten = listOf(
            Ansicht.Laedt,
            Ansicht.Einstieg,
            Ansicht.WoerterZeigen(vektor),
            Ansicht.WoerterPruefen(vektor, listOf(1, 5, 9)),
            Ansicht.Wiederherstellen,
            Ansicht.PinSetzen(vektor.joinToString(" "), neueWallet = true),
        )
        for (a in einrichten) assertFalse("$a", Hintergrundregel.sperrt(a, hatWallet = false))
    }

    // ------------------------------------------------------------ Rueckkehr

    @Test
    fun `nur der Status einer Ueberweisung wird aufgehoben`() {
        assertSame(fertig, Hintergrundregel.rueckkehr(fertig))
    }

    @Test
    fun `alles andere geht nach dem Entsperren zum Hauptbildschirm`() {
        val andere = listOf(
            Ansicht.Wallet,
            Ansicht.Empfangen,
            Ansicht.Verlauf,
            Ansicht.Einstellungen,
            Ansicht.Selbsttest,
            Ansicht.SendenEingabe,
            Ansicht.Einfuehrung(ausMehr = true),
            Ansicht.Sperre,
        )
        for (a in andere) assertEquals("$a", null, Hintergrundregel.rueckkehr(a))
    }

    @Test
    fun `der Entwurf einer Ueberweisung wird verworfen, nicht aufgehoben`() {
        val pruefen = Ansicht.SendenPruefen(entwurf())
        val pin = Ansicht.SendenPin(entwurf())
        assertTrue(Hintergrundregel.sperrt(pruefen, hatWallet = true))
        assertTrue(Hintergrundregel.sperrt(pin, hatWallet = true))
        assertNull(Hintergrundregel.rueckkehr(pruefen))
        assertNull(Hintergrundregel.rueckkehr(pin))
    }

    @Test
    fun `die Einfuehrung gleich nach dem Einrichten kommt wieder`() {
        // Der Tresor braucht Sekunden zum Verschluesseln. Wer in der Zeit
        // wegschaut, soll die Einfuehrung nach dem Entsperren noch sehen.
        val erste = Ansicht.Einfuehrung(ausMehr = false)
        assertSame(erste, Hintergrundregel.rueckkehr(erste))
        assertNull(Hintergrundregel.rueckkehr(Ansicht.Einfuehrung(ausMehr = true)))
    }

    // ------------------------------------ aus der Sperre fuehrt nur das Entsperren

    private fun muss(
        alt: Ansicht,
        neu: Ansicht,
        draussen: Boolean = false,
        entsperrt: Boolean = false,
        hatWallet: Boolean = true,
    ) = Hintergrundregel.mussSperren(alt, neu, hatWallet, draussen, entsperrt)

    @Test
    fun `vorn und offen bleibt offen`() {
        assertFalse(muss(Ansicht.Wallet, Ansicht.Empfangen))
        assertFalse(muss(Ansicht.SendenPin(entwurf()), fertig))
        assertFalse(muss(Ansicht.Einstellungen, Ansicht.WoerterAnsehen(vektor)))
    }

    @Test
    fun `im Hintergrund wird aus jeder offenen Ansicht die Sperre`() {
        assertTrue(muss(Ansicht.Wallet, Ansicht.Wallet, draussen = true))
        assertTrue(muss(Ansicht.SendenPin(entwurf()), fertig, draussen = true))
        // Schon gesperrt: nichts zu tun.
        assertFalse(muss(Ansicht.Sperre, Ansicht.Sperre, draussen = true))
    }

    @Test
    fun `Arbeit, die erst nach der Rueckkehr fertig wird, oeffnet die Sperre nicht`() {
        // PIN zum Senden eingegeben, kurz weg, zurueck - die App ist wieder
        // vorn und gesperrt, und jetzt antwortet der Knoten.
        assertTrue(muss(Ansicht.Sperre, fertig, draussen = false))
        // Dasselbe mit den zwoelf Woertern unter "Einstellungen".
        assertTrue(muss(Ansicht.Sperre, Ansicht.WoerterAnsehen(vektor), draussen = false))
        // Und mit jedem anderen Wechsel, der nicht vom Entsperren kommt.
        assertTrue(muss(Ansicht.Sperre, Ansicht.Wallet, draussen = false))
        assertTrue(muss(Ansicht.Sperre, Ansicht.Einstellungen, draussen = false))
    }

    @Test
    fun `das Entsperren oeffnet - aber nicht, solange die App draussen ist`() {
        assertFalse(muss(Ansicht.Sperre, Ansicht.Wallet, entsperrt = true))
        assertFalse(muss(Ansicht.Sperre, fertig, entsperrt = true))
        assertTrue(muss(Ansicht.Sperre, Ansicht.Wallet, draussen = true, entsperrt = true))
    }

    @Test
    fun `eine Meldung auf dem Sperrbildschirm ist kein Verlassen der Sperre`() {
        // "PIN falsch" aendert den Zustand, aber nicht die Ansicht.
        assertFalse(muss(Ansicht.Sperre, Ansicht.Sperre))
    }

    @Test
    fun `beim Einrichten wird auch im Hintergrund nicht gesperrt`() {
        val pin = Ansicht.PinSetzen(vektor.joinToString(" "), neueWallet = true)
        assertFalse(muss(pin, pin, draussen = true, hatWallet = false))
        assertFalse(
            muss(Ansicht.WoerterZeigen(vektor), Ansicht.WoerterZeigen(vektor), draussen = true, hatWallet = false),
        )
    }

    // -------------------------------------------------------- was aufgehoben ist

    @Test
    fun `der Status, der auf die Sperre trifft, wird aufgehoben`() {
        assertSame(fertig, Hintergrundregel.aufgehoben(Ansicht.Sperre, bisher = null, neu = fertig))
        assertSame(
            fertig,
            Hintergrundregel.aufgehoben(Ansicht.SendenPin(entwurf()), bisher = null, neu = fertig),
        )
    }

    @Test
    fun `die Woerter, die auf die Sperre treffen, sind weg`() {
        assertNull(
            Hintergrundregel.aufgehoben(Ansicht.Sperre, bisher = null, neu = Ansicht.WoerterAnsehen(vektor)),
        )
    }

    @Test
    fun `ein schon aufgehobener Status ueberlebt eine spaetere Aenderung an der Sperre`() {
        // Gesperrt, der Status ist aufgehoben - dann wird ein Laden fertig
        // und wollte zum Hauptbildschirm. Der Status bleibt aufgehoben.
        assertSame(fertig, Hintergrundregel.aufgehoben(Ansicht.Sperre, bisher = fertig, neu = Ansicht.Wallet))
    }

    @Test
    fun `wer aus einer offenen Ansicht sperrt, hebt nichts Altes auf`() {
        assertNull(Hintergrundregel.aufgehoben(Ansicht.Wallet, bisher = fertig, neu = Ansicht.Wallet))
    }
}
