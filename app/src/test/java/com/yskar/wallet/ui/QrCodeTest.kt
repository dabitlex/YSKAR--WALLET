package com.yskar.wallet.ui

import com.google.zxing.common.BitMatrix
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Der QR-Code mit dem Zeichen in der Mitte: Laesst er sich noch lesen?
 *
 * Geschrieben wird der Code so, wie die App ihn schreibt (`QrCode.matrix`),
 * und gelesen mit dem Leser, den der Scanner der App benutzt (`qrLeser`,
 * `qrText`). Dazwischen wird das Fenster in der Mitte zerstoert - so, als
 * laege das Zeichen darauf.
 *
 * WAS DAS BEWEIST: Die Fehlerkorrektur traegt den Verlust, und zwar mit
 * Abstand. WAS ES NICHT BEWEIST: dass ein Telefon den Code in der Hand
 * liest. Das Bild hier ist gerade, scharf und ohne Spiegelung.
 */
class QrCodeTest {

    private val adresse = "ysr17g5ywa80hf2nyjkp59ha6a3ycs0jtsule8a6v4"

    /** Punkte je Modul im Probebild. */
    private val punkt = 8

    /**
     * Den Code als Graustufenbild zeichnen, mit vier Modulen Ruhezone -
     * so, wie er auf der weissen Karte steht. [stoerung] entscheidet fuer
     * jedes Modul im Fenster, was dort zu sehen ist: true = dunkel.
     */
    private fun bild(
        matrix: BitMatrix,
        fenster: IntRange?,
        stoerung: (x: Int, y: Int) -> Boolean = { _, _ -> false },
    ): Triple<ByteArray, Int, Int> {
        val n = matrix.width
        val kante = (n + 2 * QrCode.RUHEZONE) * punkt
        val grau = ByteArray(kante * kante) { 0xFF.toByte() }
        for (y in 0 until n) {
            for (x in 0 until n) {
                val imFenster = fenster != null && x in fenster && y in fenster
                val dunkel = if (imFenster) stoerung(x, y) else matrix.get(x, y)
                if (!dunkel) continue
                for (py in 0 until punkt) {
                    val zeile = ((y + QrCode.RUHEZONE) * punkt + py) * kante
                    for (px in 0 until punkt) {
                        grau[zeile + (x + QrCode.RUHEZONE) * punkt + px] = 0
                    }
                }
            }
        }
        return Triple(grau, kante, kante)
    }

    private fun lies(b: Triple<ByteArray, Int, Int>): String? =
        qrText(qrLeser(), b.first, b.second, b.third)

    @Test
    fun `der Code der Adresse hat 37 Module`() {
        // Hoechste Fehlerkorrektur, 42 Zeichen: Fassung 5. Aendert sich das,
        // aendern sich Modulgroesse und Ruhezone auf dem Bildschirm - dann
        // soll dieser Test es sagen, nicht ein Foto.
        val m = QrCode.matrix(adresse)
        assertNotNull(m)
        assertEquals(37, m!!.width)
        assertEquals(37, m.height)
    }

    @Test
    fun `ohne Inhalt gibt es keinen Code`() {
        assertNull(QrCode.matrix(""))
    }

    @Test
    fun `der unversehrte Code liest sich`() {
        val m = QrCode.matrix(adresse)!!
        assertEquals(adresse, lies(bild(m, fenster = null)))
    }

    @Test
    fun `das Fenster deckt neun Module in der Mitte`() {
        // 22 % von 37 sind 8,14 Module. Angeschnitten werden dabei neun:
        // sieben ganz, links und rechts je eines zum Teil.
        assertEquals(14..22, QrCode.fensterModule(37))
    }

    @Test
    fun `mit weissem Fenster liest sich der Code`() {
        val m = QrCode.matrix(adresse)!!
        val f = QrCode.fensterModule(m.width)
        assertEquals(adresse, lies(bild(m, f) { _, _ -> false }))
    }

    @Test
    fun `mit schwarzem Fenster liest sich der Code`() {
        // Der schlimmere Fall: Jedes Modul im Fenster ist falsch herum
        // oder dunkel. Das Zeichen selbst ist blau auf Weiss und liest
        // sich fuer die Kamera irgendwo dazwischen.
        val m = QrCode.matrix(adresse)!!
        val f = QrCode.fensterModule(m.width)
        assertEquals(adresse, lies(bild(m, f) { _, _ -> true }))
    }

    @Test
    fun `mit umgekehrtem Fenster liest sich der Code`() {
        // Jedes Modul im Fenster zeigt das Gegenteil dessen, was es soll -
        // mehr Fehler kann ein Fenster dieser Groesse nicht machen.
        val m = QrCode.matrix(adresse)!!
        val f = QrCode.fensterModule(m.width)
        assertEquals(adresse, lies(bild(m, f) { x, y -> !m.get(x, y) }))
    }

    @Test
    fun `das Fenster darf noch deutlich wachsen, bevor der Code bricht`() {
        // Wie viel Luft ist da? Das Fenster wird von der Mitte aus Modul um
        // Modul vergroessert und jedes Mal ganz umgekehrt, bis der Leser
        // aufgibt. Verlangt wird, dass er mindestens ein Fenster traegt,
        // das auf jeder Seite ZWEI Module breiter ist als das der App.
        val m = QrCode.matrix(adresse)!!
        val mitte = m.width / 2
        val app = QrCode.fensterModule(m.width)
        val halbApp = (app.last - app.first) / 2

        var groesstes = -1
        for (halb in 0..mitte) {
            val f = (mitte - halb)..(mitte + halb)
            if (lies(bild(m, f) { x, y -> !m.get(x, y) }) == adresse) groesstes = halb
            else break
        }
        println(
            "QR-Fenster: App ${2 * halbApp + 1} Module breit, " +
                "lesbar bis ${2 * groesstes + 1} Module breit (von ${m.width}).",
        )
        assertTrue(
            "Der Code traegt nur ein Fenster von ${2 * groesstes + 1} Modulen",
            groesstes >= halbApp + 2,
        )
    }

    /**
     * Der Code, wie er wirklich auf dem Telefon steht.
     *
     * Die Tests darueber zeichnen den Code selbst. Dieser liest das
     * Bildschirmfoto, das `werkzeug\bilder-holen.ps1` vom Geraet holt -
     * mit dem blauen Zeichen, dem weissen Fenster, der Karte und allem,
     * was sonst auf dem Bildschirm ist. Liegt kein Foto da, wird er
     * uebergangen; er soll niemanden aufhalten, der ohne Geraet baut.
     *
     * Auch das ist noch kein Telefon in der Hand: Das Foto ist gerade und
     * scharf. Aber es sind die echten Bildpunkte.
     *
     * ZWEI STUFEN. Erst: Der Code liest sich, und heraus kommt eine
     * gueltige Adresse - das ist die Aussage ueber das Zeichen in der
     * Mitte, und eine Adresse mit Pruefsumme entsteht nicht aus Versehen.
     * Dann: Es ist die Beispieladresse von heute. Ist sie es nicht, ist
     * das Foto aelter als die Beispieldaten; dann wird der Test
     * uebergangen und sagt, was zu tun ist, statt eine veraltete Adresse
     * im Quelltext festzuhalten.
     */
    @Test
    fun `das Bildschirmfoto vom Geraet liest sich`() {
        val datei = java.io.File("../bilder/06_empfangen.png")
        org.junit.Assume.assumeTrue("kein Bildschirmfoto unter bilder/", datei.exists())

        val foto = javax.imageio.ImageIO.read(datei)
        val grau = ByteArray(foto.width * foto.height)
        for (y in 0 until foto.height) {
            for (x in 0 until foto.width) {
                val rgb = foto.getRGB(x, y)
                val hell = (((rgb shr 16) and 0xFF) * 299 +
                    ((rgb shr 8) and 0xFF) * 587 +
                    (rgb and 0xFF) * 114) / 1000
                grau[y * foto.width + x] = hell.toByte()
            }
        }
        val gelesen = qrText(qrLeser(), grau, foto.width, foto.height)
        assertNotNull("der Code auf dem Bildschirmfoto liest sich nicht", gelesen)
        assertTrue(
            "gelesen, aber keine gueltige Adresse",
            com.yskar.wallet.kern.Address.isValid(gelesen!!),
        )
        org.junit.Assume.assumeTrue(
            "bilder/06_empfangen.png zeigt eine aeltere Beispieladresse - " +
                "werkzeug\\bilder-holen.ps1 laufen lassen",
            gelesen == adresse,
        )
    }
}
