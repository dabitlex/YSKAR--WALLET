package com.yskar.wallet.ui

import com.yskar.wallet.netz.Betrag
import com.yskar.wallet.sprache.Deutsch
import com.yskar.wallet.sprache.EinfuehrungTexte
import com.yskar.wallet.sprache.Englisch
import com.yskar.wallet.sprache.Texte
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Was die Einfuehrung verspricht.
 *
 * Kein Test der Rechtschreibung, sondern der Zusagen: Die Karten duerfen
 * nichts behaupten, was die Regeln der App verbieten (REGELN.md, Regeln 20
 * und 24), und die eine freigegebene Aenderung muss drinstehen.
 *
 * Das gilt in JEDER Sprache. Die englische Fassung verspricht dasselbe wie
 * die deutsche - darum steht hier zu jeder deutschen Pruefung die englische.
 */
class EinfuehrungstexteTest {

    private val deutsch = einfuehrungskarten(Deutsch)
    private val englisch = einfuehrungskarten(Englisch)

    private fun zusammen(karten: List<Kartentext>) =
        karten.joinToString(" ") { "${it.titel} ${it.titelBlau} ${it.text}" }

    private val alles = zusammen(deutsch)
    private val allesEnglisch = zusammen(englisch)

    /**
     * Jedes feste Wort eines Bereichs, auch die Marken und Beispielzahlen,
     * die auf keiner `Kartentext` stehen: alles, was das Objekt als
     * Zeichenkette ohne Parameter hergibt.
     */
    private fun alleWorte(texte: EinfuehrungTexte): List<String> =
        texte.javaClass.methods
            .filter {
                it.name.startsWith("get") && it.parameterCount == 0 &&
                    it.returnType == String::class.java
            }
            .map { it.invoke(texte) as String }

    // ------------------------------------------------------------- Deutsch

    @Test
    fun `es sind acht Karten, und nur die letzte oeffnet die Wallet`() {
        assertEquals(8, deutsch.size)
        assertEquals(List(7) { "Weiter" } + "Wallet öffnen", deutsch.map { it.knopf })
    }

    @Test
    fun `die Ueberschriften stehen wie in der Vorlage`() {
        assertEquals(
            listOf(
                "Das ist deine Wallet.",
                "YSR empfangen.",
                "YSR senden.",
                "Das Y zeigt den Fortschritt.",
                "Alles im Blick.",
                "Deine 12 Wörter.",
                "Sicher bleiben.",
                "Deine Wallet. Deine Kontrolle.",
            ),
            deutsch.map { "${it.titel} ${it.titelBlau}" },
        )
    }

    @Test
    fun `keine Karte behauptet, niemand beobachte die Adresse`() {
        // Der Waechter fragt den Spiegel regelmaessig nach genau dieser
        // Adresse (Regel 24). Der Satz der Vorlage ist darum ersetzt.
        assertFalse(alles.contains("beobachtet"))
        assertFalse(alles.contains("kein Server"))
    }

    @Test
    fun `Karte 7 sagt, dass man die Benachrichtigungen selbst einschaltet`() {
        // Der Bildschirm hiess frueher "Mehr"; die Karte nennt ihn, wie er
        // jetzt heisst.
        val t = deutsch[6].text
        assertTrue(t, t.contains("schaltest du unter „Einstellungen“ ein"))
        assertFalse(t, t.contains("„Mehr“"))
        assertTrue(t, t.contains("ohne Push-Dienst"))
        assertTrue(t, t.contains("Dein Gerät fragt selbst nach"))
    }

    @Test
    fun `keine Karte nennt eine Zahlung erledigt, und endgueltig nur verneint`() {
        assertFalse(alles.contains("erledigt"))
        // Das Wort kommt genau einmal vor - in dem Satz, der es ausschliesst.
        assertEquals(1, Regex("[Ee]ndgültig").findAll(alles).count())
        assertTrue(alles.contains("„Endgültig“ nennt die Wallet eine Zahlung nie."))
    }

    @Test
    fun `die drei Stufen heissen wie auf dem Statusbildschirm`() {
        val t = deutsch[3].text
        assertTrue(t.contains("vom Knoten angenommen, in einem Block, zweimal bestätigt"))
    }

    @Test
    fun `die Woerter sollen auf Papier und niemals weitergegeben werden`() {
        val t = deutsch[5].text
        assertTrue(t.contains("Schreib sie auf Papier"))
        assertTrue(t.contains("gib sie niemals weiter"))
    }

    @Test
    fun `jedes Bild ist fuer den Bildschirmleser beschrieben`() {
        assertTrue(deutsch.all { it.bild.length > 20 })
    }

    @Test
    fun `der Zaehler zaehlt ab eins`() {
        assertEquals("1 von 8", kartenzaehler(0, 8, Deutsch))
        assertEquals("8 von 8", kartenzaehler(7, 8, Deutsch))
        assertEquals("Karte 3 von 8", kartenansage(2, 8, Deutsch))
    }

    // ------------------------------------------------------------ Englisch

    @Test
    fun `auf Englisch sind es dieselben acht Karten`() {
        assertEquals(8, englisch.size)
        assertEquals(deutsch.size, englisch.size)
        assertEquals(List(7) { "Continue" } + "Open wallet", englisch.map { it.knopf })
    }

    @Test
    fun `die englischen Ueberschriften`() {
        assertEquals(
            listOf(
                "This is your wallet.",
                "Receive YSR.",
                "Send YSR.",
                "The Y shows the progress.",
                "Everything at a glance.",
                "Your 12 words.",
                "Stay safe.",
                "Your wallet. Your control.",
            ),
            englisch.map { "${it.titel} ${it.titelBlau}" },
        )
    }

    @Test
    fun `auch auf Englisch behauptet keine Karte, niemand beobachte die Adresse`() {
        assertFalse(allesEnglisch.contains("observ", ignoreCase = true))
        assertFalse(allesEnglisch.contains("watch", ignoreCase = true))
        assertFalse(allesEnglisch.contains("no server", ignoreCase = true))
    }

    @Test
    fun `Karte 7 sagt auch auf Englisch, dass man die Benachrichtigungen selbst einschaltet`() {
        val t = englisch[6].text
        assertTrue(t, t.contains("You switch on notifications under “Settings”"))
        assertTrue(t, t.contains("without a push service"))
        assertTrue(t, t.contains("your device asks by itself"))
    }

    @Test
    fun `auch auf Englisch ist keine Zahlung erledigt, und endgueltig nur verneint`() {
        assertFalse(allesEnglisch.contains("done", ignoreCase = true))
        assertFalse(allesEnglisch.contains("complete", ignoreCase = true))
        assertFalse(allesEnglisch.contains("settled", ignoreCase = true))
        // Das Wort kommt genau einmal vor - in dem Satz, der es ausschliesst.
        assertEquals(1, Regex("[Ff]inal").findAll(allesEnglisch).count())
        assertTrue(allesEnglisch.contains("The wallet never calls a payment “final”."))
    }

    @Test
    fun `die drei Stufen heissen auch auf Englisch wie auf dem Statusbildschirm`() {
        val t = englisch[3].text
        assertTrue(t.contains("accepted by the node, in a block, confirmed twice"))
    }

    @Test
    fun `auch auf Englisch sollen die Woerter auf Papier und niemals weitergegeben werden`() {
        val t = englisch[5].text
        assertTrue(t.contains("Write them down on paper"))
        assertTrue(t.contains("never share them"))
    }

    @Test
    fun `auch auf Englisch ist jedes Bild beschrieben`() {
        assertTrue(englisch.all { it.bild.length > 20 })
    }

    @Test
    fun `der englische Zaehler zaehlt ab eins`() {
        assertEquals("1 of 8", kartenzaehler(0, 8, Englisch))
        assertEquals("8 of 8", kartenzaehler(7, 8, Englisch))
        assertEquals("Card 3 of 8", kartenansage(2, 8, Englisch))
    }

    @Test
    fun `im Englischen steht kein deutscher Buchstabe`() {
        val worte = alleWorte(Englisch.einfuehrung) +
            englisch.flatMap { listOf(it.titel, it.titelBlau, it.text, it.bild, it.knopf) } +
            kartenzaehler(0, 8, Englisch) + kartenansage(0, 8, Englisch)
        for (wort in worte) {
            assertFalse(wort, wort.any { it in "äöüÄÖÜß" })
            // Deutsche Anfuehrungszeichen unten gibt es im Englischen nicht.
            assertFalse(wort, wort.contains('„'))
        }
    }

    // ------------------------------------------------------- Beide Sprachen

    @Test
    fun `kein Text ist leer, in keiner Sprache`() {
        for (t in listOf<Texte>(Deutsch, Englisch)) {
            val worte = alleWorte(t.einfuehrung)
            // 57 feste Worte je Sprache; die zwei mit Zahlen stehen unten.
            assertEquals(t.sprache.name, 57, worte.size)
            assertTrue(t.sprache.name, worte.all { it.isNotBlank() })
            assertTrue(einfuehrungskarten(t).all { it.titel.isNotBlank() && it.text.isNotBlank() })
            assertTrue(kartenzaehler(0, 8, t).isNotBlank())
            assertTrue(kartenansage(0, 8, t).isNotBlank())
        }
    }

    @Test
    fun `die Stufen unter dem Y heissen wie im Satz daneben`() {
        for (t in listOf<Texte>(Deutsch, Englisch)) {
            val e = t.einfuehrung
            for (stufe in listOf(e.stufeAngenommen, e.stufeImBlock, e.stufeZweimalBestaetigt)) {
                assertTrue(stufe, e.fortschrittText.contains(stufe))
                assertTrue(stufe, e.fortschrittBild.contains(stufe))
            }
        }
    }

    @Test
    fun `die Beispielzahlen sind in beiden Sprachen dieselben Betraege`() {
        // Geschrieben, wie `Betrag` sie in der jeweiligen Sprache schreibt:
        // "1.284,57310946" auf Deutsch, "1,284.57310946" auf Englisch. Das
        // Minus ist das echte Minuszeichen der Vorlage, nicht der Bindestrich.
        for (t in listOf<Texte>(Deutsch, Englisch)) {
            val e = t.einfuehrung
            val name = t.sprache.name
            assertEquals(name, "+" + Betrag.genau(1_250_000_000L, t.locale), e.beispielEmpfangen)
            assertEquals(name, "+" + Betrag.genau(100_000_000L, t.locale), e.beispielEingang)
            assertEquals(name, "−" + Betrag.genau(2_500_000_000L, t.locale), e.beispielAusgang)
            assertEquals(
                name,
                Betrag.genau(128_457_310_946L, t.locale),
                e.beispielGuthabenGanz + e.beispielGuthabenBruch,
            )
            assertEquals(name, Betrag.dezimalZeichen(t.locale), e.beispielGuthabenBruch.first())
            assertEquals(
                name,
                "+" + Betrag.genau(30_754_160_751L, t.locale),
                e.beispielSummeEingang,
            )
            assertEquals(
                name,
                "−" + Betrag.genau(2_500_016_800L, t.locale),
                e.beispielSummeAusgang,
            )
        }
    }
}
