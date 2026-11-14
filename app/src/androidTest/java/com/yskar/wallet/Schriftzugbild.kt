package com.yskar.wallet

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Zeichnet den Schriftzug "YSKAR" fuer den Startbildschirm - als Bild.
 *
 * KEIN TEST, DER ETWAS PRUEFT, sondern ein Werkzeug: Android erlaubt auf
 * dem Startbildschirm unten ein "Markenbild", aber keinen Text. Der
 * Schriftzug muss also einmal als Bild entstehen. Gezeichnet wird er hier
 * mit der Schrift, die auch die App benutzt (Hanken Grotesk, Schnitt 600),
 * wie die Website ihren Namen setzt (`.brand__name`): Laufweite 0,2 em,
 * Farbe #EEF4FF. Die Groesse bleibt 17 sp.
 *
 * Das Bild ist 200 x 80 dp gross - so viel Platz gibt Android dem
 * Markenbild - und liegt in vierfacher Aufloesung vor (800 x 320), fuer
 * `drawable-xxxhdpi`. Der Grund ist durchsichtig.
 *
 * Ablauf, wenn sich Schrift oder Vorlage aendern:
 *
 *     adb shell am instrument -w -e class com.yskar.wallet.Schriftzugbild \
 *       com.yskar.wallet.debug.test/androidx.test.runner.AndroidJUnitRunner
 *     adb pull /sdcard/Android/data/com.yskar.wallet.debug/files/marke/marke_start.png
 *
 * und das Bild nach `app/src/main/res/drawable-xxxhdpi/` legen.
 *
 * Fasst weder Tresor noch Netz an.
 */
@RunWith(AndroidJUnit4::class)
class Schriftzugbild {

    @Test
    fun zeichne() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext

        val fach = 4                      // xxxhdpi
        val breite = 200 * fach
        val hoehe = 80 * fach

        val stift = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            typeface = ctx.resources.getFont(R.font.hanken_grotesk_variable)
            fontVariationSettings = "'wght' 600"
            textSize = 17f * fach
            letterSpacing = 0.2f
            color = 0xFFEEF4FF.toInt()
            textAlign = Paint.Align.LEFT
        }

        val text = "YSKAR"
        // Die Laufweite haengt auch HINTER dem letzten Buchstaben. Fuer die
        // Mitte zaehlt nur, was zu sehen ist.
        val nachlauf = stift.textSize * stift.letterSpacing
        val sichtbar = stift.measureText(text) - nachlauf
        val x = (breite - sichtbar) / 2f
        // Senkrecht mittig nach der Hoehe der Grossbuchstaben.
        val grenzen = android.graphics.Rect().also { stift.getTextBounds("YSKAR", 0, 5, it) }
        val y = (hoehe + grenzen.height()) / 2f

        val bild = Bitmap.createBitmap(breite, hoehe, Bitmap.Config.ARGB_8888)
        Canvas(bild).drawText(text, x, y, stift)

        val ordner = File(ctx.getExternalFilesDir(null), "marke").apply { mkdirs() }
        val datei = File(ordner, "marke_start.png")
        datei.outputStream().use { bild.compress(Bitmap.CompressFormat.PNG, 100, it) }

        assertTrue("Das Bild wurde nicht geschrieben", datei.length() > 0)
        // Der Schriftzug muss ganz im Bild stehen.
        assertTrue("Der Schriftzug ist breiter als das Bild", sichtbar < breite)
    }
}
