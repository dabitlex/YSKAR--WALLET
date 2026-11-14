package com.yskar.wallet.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * Was in den zwoelf Feldern steht, wenn jemand tippt oder einfuegt.
 *
 * Die Woerter hier sind der oeffentliche BIP39-Testvektor und erfundene
 * Platzhalter (REGELN.md, Regel 3). Geprueft wird nur das Verteilen.
 */
class MerkwortfelderTest {

    private val leer = Merkwortfelder.leer()
    private val vektor = List(11) { "abandon" } + "about"

    private fun woerter(n: Int) = (1..n).map { "w$it" }

    // ---------------------------------------------------------------- Tippen

    @Test
    fun `am Anfang sind es zwoelf leere Felder`() {
        assertEquals(12, leer.size)
        assertEquals(0, Merkwortfelder.anzahl(leer))
        assertEquals("", Merkwortfelder.alsText(leer))
    }

    @Test
    fun `ein getipptes Wort bleibt in seinem Feld`() {
        val e = Merkwortfelder.eingabe(leer, 3, "aban")
        assertEquals("aban", e.felder[3])
        assertEquals(1, Merkwortfelder.anzahl(e.felder))
        assertNull(e.fokus)
        assertEquals(0, e.uebrig)
    }

    @Test
    fun `ein Feld laesst sich wieder leeren`() {
        val voll = Merkwortfelder.eingabe(leer, 0, "abandon").felder
        val e = Merkwortfelder.eingabe(voll, 0, "")
        assertEquals("", e.felder[0])
        assertNull(e.fokus)
    }

    @Test
    fun `ein Leerzeichen am Ende geht ins naechste Feld`() {
        val e = Merkwortfelder.eingabe(leer, 0, "abandon ")
        assertEquals("abandon", e.felder[0])
        assertEquals(1, e.fokus)
        // Das naechste Feld ist nicht angefasst.
        assertEquals("", e.felder[1])
    }

    @Test
    fun `das Leerzeichen ueberschreibt das naechste Feld nicht`() {
        val vorher = leer.toMutableList().also { it[1] = "schon" }
        val e = Merkwortfelder.eingabe(vorher, 0, "abandon ")
        assertEquals("schon", e.felder[1])
        assertEquals(1, e.fokus)
    }

    @Test
    fun `im letzten Feld bleibt man nach dem Leerzeichen, wo man ist`() {
        val e = Merkwortfelder.eingabe(leer, 11, "about ")
        assertEquals("about", e.felder[11])
        assertEquals(11, e.fokus)
        assertEquals(0, e.uebrig)
    }

    @Test
    fun `nur ein Leerzeichen laesst das Feld leer und geht nirgends hin`() {
        val e = Merkwortfelder.eingabe(leer, 4, " ")
        assertEquals("", e.felder[4])
        assertNull(e.fokus)
    }

    // -------------------------------------------------------------- Einfuegen

    @Test
    fun `zwoelf eingefuegte Woerter fuellen die zwoelf Felder`() {
        val e = Merkwortfelder.eingabe(leer, 0, vektor.joinToString(" "))
        assertEquals(vektor, e.felder)
        assertEquals(11, e.fokus)
        assertEquals(0, e.uebrig)
    }

    @Test
    fun `Zeilenumbrueche, Tabulatoren und doppelte Leerzeichen trennen wie eines`() {
        val e = Merkwortfelder.eingabe(leer, 0, "  w1\tw2\n\nw3   w4 ")
        assertEquals(listOf("w1", "w2", "w3", "w4"), e.felder.take(4))
        assertEquals(4, Merkwortfelder.anzahl(e.felder))
        // Endet mit Leerraum: weiter im fuenften Feld.
        assertEquals(4, e.fokus)
    }

    @Test
    fun `eingefuegt wird ab dem Feld, in dem man steht`() {
        val e = Merkwortfelder.eingabe(leer, 9, "w1 w2 w3")
        assertEquals(listOf("w1", "w2", "w3"), e.felder.drop(9))
        assertEquals(11, e.fokus)
        assertEquals(0, e.uebrig)
    }

    @Test
    fun `Eingefuegtes ersetzt, was in den folgenden Feldern stand`() {
        val vorher = Merkwortfelder.eingabe(leer, 0, woerter(12).joinToString(" ")).felder
        val e = Merkwortfelder.eingabe(vorher, 2, "neu1 neu2")
        assertEquals("w2", e.felder[1])
        assertEquals("neu1", e.felder[2])
        assertEquals("neu2", e.felder[3])
        assertEquals("w5", e.felder[4])
    }

    // ----------------------------------------------------- Mehr als zwoelf

    @Test
    fun `es bleiben immer zwoelf Felder, was auch eingefuegt wird`() {
        for (n in listOf(1, 12, 13, 24, 100)) {
            val e = Merkwortfelder.eingabe(leer, 0, woerter(n).joinToString(" "))
            assertEquals("$n Woerter", 12, e.felder.size)
        }
    }

    @Test
    fun `vierundzwanzig eingefuegte Woerter - zwoelf stehen da, zwoelf sind gezaehlt`() {
        val e = Merkwortfelder.eingabe(leer, 0, woerter(24).joinToString(" "))
        assertEquals(woerter(12), e.felder)
        assertEquals(12, e.uebrig)
        assertEquals(11, e.fokus)
    }

    @Test
    fun `das dreizehnte Wort wird nicht verschwiegen`() {
        val e = Merkwortfelder.eingabe(leer, 0, woerter(13).joinToString(" "))
        assertEquals(woerter(12), e.felder)
        assertEquals(1, e.uebrig)
    }

    @Test
    fun `gezaehlt wird ab dem Feld, in dem man steht`() {
        // Drei Woerter ab Feld 11: Zwei haben Platz, eines nicht.
        val e = Merkwortfelder.eingabe(leer, 10, "a b c")
        assertEquals(listOf("a", "b"), e.felder.drop(10))
        assertEquals(1, e.uebrig)
        assertEquals(11, e.fokus)
    }

    // ------------------------------------------------------------- Uebergabe

    @Test
    fun `der Text fuer das Modell sind die Woerter in ihrer Reihenfolge`() {
        val e = Merkwortfelder.eingabe(leer, 0, vektor.joinToString(" "))
        assertEquals(
            "abandon abandon abandon abandon abandon abandon " +
                "abandon abandon abandon abandon abandon about",
            Merkwortfelder.alsText(e.felder),
        )
    }

    @Test
    fun `leere Felder dazwischen fallen aus dem Text - die Zahl sagt es dem Modell`() {
        var f = leer
        f = Merkwortfelder.eingabe(f, 0, "eins").felder
        f = Merkwortfelder.eingabe(f, 2, "drei").felder
        assertEquals("eins drei", Merkwortfelder.alsText(f))
        assertEquals(2, Merkwortfelder.anzahl(f))
    }

    @Test
    fun `Gross- und Kleinschreibung bleibt, wie sie getippt wurde`() {
        // Angleichen ist Sache von Bip39.normalize, nicht dieser Felder.
        val e = Merkwortfelder.eingabe(leer, 0, "Abandon ABOUT")
        assertEquals("Abandon ABOUT", Merkwortfelder.alsText(e.felder))
    }

    @Test
    fun `ein Feld, das es nicht gibt, ist ein Fehler des Aufrufers`() {
        assertThrows(IllegalArgumentException::class.java) {
            Merkwortfelder.eingabe(leer, 12, "x")
        }
    }

    @Test
    fun `eine Liste mit anderer Laenge ist ein Fehler des Aufrufers`() {
        assertThrows(IllegalArgumentException::class.java) {
            Merkwortfelder.eingabe(List(24) { "" }, 0, "x")
        }
    }
}
