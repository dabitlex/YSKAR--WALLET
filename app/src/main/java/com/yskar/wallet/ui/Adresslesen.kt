package com.yskar.wallet.ui

import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.yskar.wallet.kern.Address
import com.yskar.wallet.kern.Params

/**
 * Was aus einem gescannten QR-Code herausgelesen wird.
 *
 * EIGENE DATEI OHNE ANDROID-IMPORTE, damit das hier auf der JVM geprueft
 * werden kann. Es ist der einzige Teil des Scanners, bei dem ein Fehler
 * teuer waere: Er entscheidet, welche Zeichenkette als Empfaengeradresse
 * ins Feld geschrieben wird.
 *
 * Die Wallet selbst erzeugt Codes mit der nackten Adresse. Andere
 * Programme schreiben gern ein Schema davor und haengen Parameter an
 * (`yskar:ysr1...?amount=1`). Beides wird abgeschnitten - und was uebrig
 * bleibt, muss `Address.isValid` bestehen. Ein QR-Code ist kein Beweis;
 * jeder kann einen drucken.
 *
 * EIN BETRAG AUS DEM CODE WIRD NICHT UEBERNOMMEN. Wie viel gesendet wird,
 * entscheidet der Besitzer, nicht ein gedrucktes Muster. Darum wird alles
 * ab dem Fragezeichen verworfen, statt es auszuwerten.
 *
 * Gibt null zurueck, wenn nichts Brauchbares darin steht.
 */
fun adresseAus(roh: String): String? {
    var t = roh.trim()

    // Ein Schema davor abschneiden - aber nur, wenn es keines ist. Eine
    // Adresse enthaelt keinen Doppelpunkt, trotzdem wird hier zuerst
    // geprueft, ob der Text schon mit "ysr1" beginnt: Sonst koennte ein
    // kuenftiges Adressformat mit Doppelpunkt stillschweigend zerschnitten
    // werden.
    if (!t.startsWith(Params.ADDRESS_HRP + "1", ignoreCase = true)) {
        val doppel = t.indexOf(':')
        if (doppel in 1..12) t = t.substring(doppel + 1)
    }

    t = t.trimStart('/')
        .substringBefore('?')
        .substringBefore('&')
        .substringBefore('#')
        .trim()

    return if (Address.isValid(t)) t else null
}

// ------------------------------------------------------------ Entziffern

/**
 * Der Leser mit den Einstellungen des Scanners.
 *
 * Nur QR, nichts anderes: Strichcodes und DataMatrix haben in einer
 * Wallet nichts zu suchen, und je weniger Formate gesucht werden, desto
 * schneller ist jedes Bild durch. `TRY_HARDER`, weil das Bild aus der
 * Hand kommt und selten gerade und scharf ist.
 *
 * Steht hier statt im Scanner, damit der Weg vom Bild zum Text ohne
 * Kamera geprueft werden kann - siehe `AdresslesenTest`.
 */
fun qrLeser(): MultiFormatReader = MultiFormatReader().apply {
    setHints(
        mapOf(
            DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
            DecodeHintType.TRY_HARDER to true,
            DecodeHintType.CHARACTER_SET to "UTF-8",
        ),
    )
}

/**
 * Ein Graustufenbild entziffern; null, wenn kein Code darin ist.
 *
 * Gedreht wird nichts: Die Erkennung laeuft ueber die drei Suchmuster in
 * den Ecken und ist damit von der Ausrichtung unabhaengig.
 */
fun qrText(leser: MultiFormatReader, grau: ByteArray, breite: Int, hoehe: Int): String? =
    try {
        leser.decodeWithState(
            BinaryBitmap(
                HybridBinarizer(
                    PlanarYUVLuminanceSource(
                        grau, breite, hoehe,
                        0, 0, breite, hoehe,
                        false,
                    ),
                ),
            ),
        )?.text
    } catch (e: Exception) {
        // `NotFoundException` ist der Normalfall - in den meisten Bildern
        // ist schlicht kein Code.
        null
    } finally {
        leser.reset()
    }
