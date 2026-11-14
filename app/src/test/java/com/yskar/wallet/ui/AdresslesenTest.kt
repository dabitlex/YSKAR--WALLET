package com.yskar.wallet.ui

import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Was der Scanner aus einem Code macht.
 *
 * Die Adressen hier sind gueltig gebildet, aber erfunden (Regel 42):
 * Niemand hat den Schluessel dazu - nichts dorthin senden.
 */
class AdresslesenTest {

    private val adresse = "ysr17g5ywa80hf2nyjkp59ha6a3ycs0jtsule8a6v4"

    @Test
    fun `nackte Adresse wird uebernommen`() {
        assertEquals(adresse, adresseAus(adresse))
    }

    @Test
    fun `Leerzeichen und Zeilenumbrueche stoeren nicht`() {
        assertEquals(adresse, adresseAus("  $adresse\n"))
    }

    @Test
    fun `Schema davor wird abgeschnitten`() {
        assertEquals(adresse, adresseAus("yskar:$adresse"))
        assertEquals(adresse, adresseAus("YSKAR://$adresse"))
    }

    @Test
    fun `ein Betrag im Code wird verworfen, nicht uebernommen`() {
        // Wie viel gesendet wird, entscheidet der Besitzer. Der Code darf
        // die Adresse liefern und sonst nichts.
        assertEquals(adresse, adresseAus("yskar:$adresse?amount=999999"))
        assertEquals(adresse, adresseAus("$adresse?amount=1&memo=hallo"))
        assertEquals(adresse, adresseAus("$adresse#notiz"))
    }

    @Test
    fun `fremde Codes werden abgelehnt`() {
        assertNull(adresseAus("https://example.com"))
        assertNull(adresseAus("bc1qar0srrr7xfkvy5l643lydnw9re59gtzzwf5mdq"))
        assertNull(adresseAus(""))
        assertNull(adresseAus("Hallo Welt"))
    }

    @Test
    fun `eine verfaelschte Adresse faellt durch die Pruefsumme`() {
        // Ein einziges Zeichen geaendert - bech32m merkt das. Genau dafuer
        // wird jeder gescannte Code geprueft, statt ihm zu glauben.
        val falsch = adresse.dropLast(1) + if (adresse.last() == 'c') 'd' else 'c'
        assertNull(adresseAus(falsch))
    }

    // ------------------------------------------------------- Entziffern

    /**
     * Ein QR-Code als Graustufenbild, so wie ihn die Kamera liefern wuerde:
     * schwarze Punkte auf hellem Grund, mit Rand. [lupe] ist die Kantenlaenge
     * eines Codepunkts in Bildpunkten.
     */
    private fun alsGraubild(inhalt: String, lupe: Int = 6, rand: Int = 24): Triple<ByteArray, Int, Int> {
        val matrix = QRCodeWriter().encode(
            inhalt, BarcodeFormat.QR_CODE, 0, 0,
            mapOf(
                EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
                EncodeHintType.MARGIN to 1,
                EncodeHintType.CHARACTER_SET to "UTF-8",
            ),
        )
        val breite = matrix.width * lupe + rand * 2
        val hoehe = matrix.height * lupe + rand * 2
        val grau = ByteArray(breite * hoehe) { 0xFF.toByte() }
        for (y in 0 until matrix.height) {
            for (x in 0 until matrix.width) {
                if (!matrix.get(x, y)) continue
                for (dy in 0 until lupe) {
                    val zeile = (rand + y * lupe + dy) * breite + rand + x * lupe
                    for (dx in 0 until lupe) grau[zeile + dx] = 0
                }
            }
        }
        return Triple(grau, breite, hoehe)
    }

    @Test
    fun `ein gezeichneter Code wird wieder gelesen`() {
        // Der ganze Weg vom Bild zum Text, ohne Kamera: Die App zeichnet
        // auf dem Empfangen-Bildschirm genau so einen Code - er muss sich
        // mit den Einstellungen des Scanners wieder entziffern lassen.
        val (grau, b, h) = alsGraubild(adresse)
        assertEquals(adresse, qrText(qrLeser(), grau, b, h))
    }

    @Test
    fun `ein gelesener Code geht durch die Adresspruefung`() {
        val (grau, b, h) = alsGraubild("yskar:$adresse?amount=5")
        val text = qrText(qrLeser(), grau, b, h)
        assertEquals("yskar:$adresse?amount=5", text)
        assertEquals(adresse, adresseAus(text!!))
    }

    @Test
    fun `ein leeres Bild ergibt nichts`() {
        val leer = ByteArray(200 * 200) { 0xFF.toByte() }
        assertNull(qrText(qrLeser(), leer, 200, 200))
    }

    @Test
    fun `derselbe Leser entziffert mehrere Bilder nacheinander`() {
        // `decodeWithState` behaelt Zustand; ohne `reset()` zwischendurch
        // liest der zweite Durchgang Unsinn. Der Scanner benutzt genau
        // einen Leser fuer alle Bilder.
        val leser = qrLeser()
        val (a, ab, ah) = alsGraubild(adresse)
        val zweite = "ysr1j8nnl4kzwdck9u6ddltp8dmvauhg6rez967syq"
        val (z, zb, zh) = alsGraubild(zweite)
        assertEquals(adresse, qrText(leser, a, ab, ah))
        assertEquals(zweite, qrText(leser, z, zb, zh))
        assertEquals(adresse, qrText(leser, a, ab, ah))
    }

    @Test
    fun `Grossschreibung allein macht noch keine Adresse`() {
        // bech32m erlaubt rein grosse ODER rein kleine Schreibung. Was
        // davon `Address.isValid` durchlaesst, entscheidet der Kern - hier
        // wird nur festgehalten, dass Gemischtes nicht durchgeht.
        assertNull(adresseAus("Ysr17G5ywa80hf2nyjkp59ha6a3ycs0jtsule8a6v4"))
    }
}
