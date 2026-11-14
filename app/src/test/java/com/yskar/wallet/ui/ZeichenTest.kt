package com.yskar.wallet.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Die Logik hinter dem Zeichen.
 *
 * Reine Rechnung, kein Compose - darum laeuft sie auf der JVM. Das Zeichnen
 * selbst kann hier nicht geprueft werden; dafuer gibt es das Geraet und das
 * Auge.
 *
 * Anlass: Auf dem Geraet waren bei NULL eingegebenen Ziffern zwei Baender
 * ausgefuellt. Dieser Test haelt fest, was richtig ist.
 */
class ZeichenTest {

    private fun pin(n: Int) = baenderFuerPin(n, falsch = false, geprueft = false)

    @Test
    fun ohneEingabeIstAllesAus() {
        assertEquals(listOf(Teil.AUS, Teil.AUS, Teil.AUS), pin(0))
    }

    @Test
    fun jeZweiZiffernEinBand() {
        assertEquals("1 Ziffer", listOf(Teil.HALB, Teil.AUS, Teil.AUS), pin(1))
        assertEquals("2 Ziffern", listOf(Teil.AN, Teil.AUS, Teil.AUS), pin(2))
        assertEquals("3 Ziffern", listOf(Teil.AN, Teil.HALB, Teil.AUS), pin(3))
        assertEquals("4 Ziffern", listOf(Teil.AN, Teil.AN, Teil.AUS), pin(4))
        assertEquals("5 Ziffern", listOf(Teil.AN, Teil.AN, Teil.HALB), pin(5))
        assertEquals("6 Ziffern", listOf(Teil.AN, Teil.AN, Teil.AN), pin(6))
    }

    @Test
    fun falschePinFaerbtAllesRot() {
        assertEquals(
            listOf(Teil.FEHLER, Teil.FEHLER, Teil.FEHLER),
            baenderFuerPin(3, falsch = true, geprueft = false),
        )
    }

    @Test
    fun waehrendDerPruefungIstAllesAn() {
        assertEquals(
            listOf(Teil.AN, Teil.AN, Teil.AN),
            baenderFuerPin(6, falsch = false, geprueft = true),
        )
    }

    @Test
    fun zahlungFuelltSichSchrittweise() {
        assertEquals(
            "nur angenommen",
            listOf(Teil.AN, Teil.HALB, Teil.AUS),
            baenderFuerZahlung(angenommen = true, imBlock = false, bestaetigungen = 0),
        )
        assertEquals(
            "im Block, erste Bestaetigung",
            listOf(Teil.AN, Teil.AN, Teil.HALB),
            baenderFuerZahlung(angenommen = true, imBlock = true, bestaetigungen = 1),
        )
        assertEquals(
            "zwei Bestaetigungen",
            listOf(Teil.AN, Teil.AN, Teil.AN),
            baenderFuerZahlung(angenommen = true, imBlock = true, bestaetigungen = 2),
        )
    }

    @Test
    fun nochNichtsAngenommenHeisstAllesAus() {
        assertEquals(
            listOf(Teil.AUS, Teil.AUS, Teil.AUS),
            baenderFuerZahlung(angenommen = false, imBlock = false, bestaetigungen = 0),
        )
    }
}
