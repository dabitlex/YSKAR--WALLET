package com.yskar.wallet.ui

import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import kotlin.math.ceil
import kotlin.math.floor

/**
 * Der QR-Code der eigenen Adresse - der Teil ohne Android.
 *
 * EIGENE DATEI OHNE ANDROID-IMPORTE, damit auf der JVM geprueft werden
 * kann, was sonst nur ein zweites Telefon beantwortet: Laesst sich der
 * Code noch lesen, wenn in seiner Mitte das Zeichen sitzt? (`QrCodeTest`)
 *
 * DAS ZEICHEN IN DER MITTE VERDECKT MODULE. Der Code traegt das, weil er
 * mit der hoechsten Fehlerkorrektur (H, rund 30 %) geschrieben wird und
 * das Fenster knapp 5 % seiner Flaeche einnimmt. Der Preis: Bei H braucht
 * dieselbe Adresse mehr Module als bei M, jedes Modul wird kleiner.
 *
 * Der Test hier ist ein Beleg, kein Freispruch. Er entziffert ein sauberes,
 * gerades Bild; ein Telefon sieht den Code schraeg, unscharf und mit
 * Spiegelung. Ob er sich in der Hand lesen laesst, zeigt nur ein zweites
 * Geraet - und liest es ihn nicht zuverlaessig, kommt das Zeichen heraus.
 */
object QrCode {

    /**
     * Die Ruhezone um den Code, in Modulen. Vier verlangt die Norm.
     *
     * Sie kommt von der weissen Karte, nicht vom Bild (REGELN.md,
     * Regel 30): `matrix` liefert den Code ohne Rand, und die Karte
     * polstert ihn mit genau dieser Zahl von Modulen.
     */
    const val RUHEZONE = 4

    /** Kantenlaenge des weissen Fensters, als Anteil der Kante des Codes. */
    const val FENSTER = 0.22f

    /** Rundung des Fensters, als Anteil seiner Kante (Vorlage: 1,95 von 8,14). */
    const val FENSTER_RUNDUNG = 0.24f

    /** Groesse des Zeichens, als Anteil der Kante des Fensters (34 von 54). */
    const val ZEICHEN = 0.63f

    /**
     * Die Module des Codes, ein Bit je Modul, OHNE Rand.
     *
     * Null, wenn der Inhalt leer ist oder sich nicht schreiben laesst.
     */
    fun matrix(inhalt: String): BitMatrix? {
        if (inhalt.isEmpty()) return null
        return try {
            QRCodeWriter().encode(
                inhalt,
                BarcodeFormat.QR_CODE,
                // 0 x 0 heisst Modulaufloesung: ein Punkt je Modul, nichts
                // aufgefuellt und nichts zentriert.
                0, 0,
                mapOf(
                    EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.H,
                    EncodeHintType.MARGIN to 0,
                    EncodeHintType.CHARACTER_SET to "UTF-8",
                ),
            )
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Welche Module das Fenster ganz ODER TEILWEISE ueberdeckt - dieselbe
     * Spanne in beiden Richtungen.
     *
     * Das Fenster liegt nicht auf dem Raster der Module; an seinen Kanten
     * bleibt von einem Modul ein Streifen stehen. Fuer die Frage, was der
     * Leser verliert, zaehlt jedes angeschnittene Modul als verloren.
     */
    fun fensterModule(module: Int): IntRange {
        val kante = module * FENSTER
        val von = floor((module - kante) / 2.0).toInt()
        val bis = ceil((module + kante) / 2.0).toInt() - 1
        return von..bis
    }
}
