package com.yskar.wallet

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Path
import android.graphics.RectF
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.Locale
import kotlin.math.hypot

/**
 * Zeichnet das App-Symbol in Dateien - und misst es nach.
 *
 * Das Symbol zeigt der Starter des Telefons, nicht die App; ein
 * Bildschirmbild davon gibt es nicht. Hier wird es so gezeichnet, wie
 * Android es zusammensetzt (Hintergrund, darauf das Zeichen, zugeschnitten),
 * einmal zum Kreis und einmal zum abgerundeten Quadrat:
 *
 *     /sdcard/Android/data/com.yskar.wallet.debug/files/marke/symbol.png
 *     /sdcard/Android/data/com.yskar.wallet.debug/files/marke/symbol_quadrat.png
 *     /sdcard/Android/data/com.yskar.wallet.debug/files/marke/symbol.txt
 *
 * Geprueft wird dreierlei:
 *  - Der Hintergrund ist schwarz (Wunsch des Besitzers, 11.11.2026).
 *  - Das Zeichen bleibt in der sicheren Zone. Die Flaeche eines Symbols hat
 *    108 Einheiten; garantiert sichtbar ist nur ein Kreis mit Durchmesser 66
 *    um die Mitte. Bis zum 11.11.2026 ragte die Spitze links oben darueber
 *    hinaus, und eine runde Maske schnitt sie ab.
 *  - Unter dem Symbol steht "Yskar".
 *
 * Fasst weder Tresor noch Netz an und startet die App nicht.
 */
@RunWith(AndroidJUnit4::class)
class Symbolbild {

    private val ctx get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun symbol(): AdaptiveIconDrawable {
        val symbol = ctx.getDrawable(R.mipmap.ic_launcher)
        assertTrue("Das Symbol ist kein adaptives Symbol", symbol is AdaptiveIconDrawable)
        return symbol as AdaptiveIconDrawable
    }

    private fun ordner() = File(ctx.getExternalFilesDir(null), "marke").apply { mkdirs() }

    @Test
    fun zeichneUndPruefeDenGrund() {
        val symbol = symbol()
        val kante = 432

        // Die Hintergrundebene allein: an ihren Ecken muss Schwarz stehen.
        val grund = Bitmap.createBitmap(kante, kante, Bitmap.Config.ARGB_8888)
        symbol.background.setBounds(0, 0, kante, kante)
        symbol.background.draw(Canvas(grund))
        for ((x, y) in listOf(2 to 2, kante - 3 to 2, 2 to kante - 3, kante - 3 to kante - 3, kante / 2 to kante / 2)) {
            assertEquals("Grund bei $x/$y", 0xFF000000.toInt(), grund.getPixel(x, y))
        }

        // Das ganze Symbol, zugeschnitten: zum Kreis wie auf den meisten
        // Startern, und zum abgerundeten Quadrat, wie HyperOS es ungefaehr tut.
        val k = kante.toFloat()
        val kreis = Path().apply { addCircle(k / 2, k / 2, k / 2, Path.Direction.CW) }
        val quadrat = Path().apply {
            addRoundRect(RectF(0f, 0f, k, k), k * 0.23f, k * 0.23f, Path.Direction.CW)
        }
        for ((name, maske) in listOf("symbol.png" to kreis, "symbol_quadrat.png" to quadrat)) {
            val bild = Bitmap.createBitmap(kante, kante, Bitmap.Config.ARGB_8888)
            val leinwand = Canvas(bild)
            leinwand.clipPath(maske)
            // Android zeichnet die Ebenen um die Haelfte groesser als die Maske
            // und zeigt nur die Mitte.
            val ueber = kante / 4
            symbol.background.setBounds(-ueber, -ueber, kante + ueber, kante + ueber)
            symbol.background.draw(leinwand)
            symbol.foreground.setBounds(-ueber, -ueber, kante + ueber, kante + ueber)
            symbol.foreground.draw(leinwand)

            val datei = File(ordner(), name)
            datei.outputStream().use { bild.compress(Bitmap.CompressFormat.PNG, 100, it) }
            assertTrue("$name wurde nicht geschrieben", datei.length() > 0)
        }
    }

    /**
     * Wie weit der fernste Bildpunkt einer Ebene von der Mitte liegt, in
     * Einheiten der Flaeche von 108. Gezeichnet wird achtfach vergroessert;
     * gezaehlt wird, was mehr als halb deckt - der weiche Rand der Kante
     * zaehlt nicht mit.
     */
    private fun fernsterPunkt(ebene: Drawable): Float {
        val mal = 8
        val kante = FLAECHE * mal
        val bild = Bitmap.createBitmap(kante, kante, Bitmap.Config.ARGB_8888)
        ebene.setBounds(0, 0, kante, kante)
        ebene.draw(Canvas(bild))
        val punkte = IntArray(kante * kante)
        bild.getPixels(punkte, 0, kante, 0, 0, kante, kante)

        val mitte = kante / 2f
        var weit = 0f
        var gefunden = false
        for (y in 0 until kante) {
            for (x in 0 until kante) {
                if ((punkte[y * kante + x] ushr 24) > 127) {
                    gefunden = true
                    val d = hypot(x + 0.5f - mitte, y + 0.5f - mitte)
                    if (d > weit) weit = d
                }
            }
        }
        assertTrue("Die Ebene ist leer", gefunden)
        return weit / mal
    }

    @Test
    fun dasZeichenBleibtInDerSicherenZone() {
        val symbol = symbol()
        val zeilen = mutableListOf<String>()

        val ebenen = buildList {
            add("Vordergrund" to symbol.foreground)
            // Die einfarbige Ebene gibt es ab Android 13.
            if (Build.VERSION.SDK_INT >= 33) symbol.monochrome?.let { add("Einfarbig" to it) }
        }
        assertTrue("Keine Ebene gemessen", ebenen.isNotEmpty())

        val ergebnisse = ebenen.map { (name, ebene) -> name to fernsterPunkt(ebene) }
        for ((name, weit) in ergebnisse) {
            zeilen += String.format(
                Locale.GERMANY,
                "%s: fernster Punkt %.2f von der Mitte (sichere Zone %d, runde Maske %d) - " +
                    "Luft zur sicheren Zone %.2f, zur runden Maske %.2f",
                name, weit, SICHER, MASKE, SICHER - weit, MASKE - weit,
            )
        }
        File(ordner(), "symbol.txt").writeText(zeilen.joinToString("\n") + "\n")

        for ((name, weit) in ergebnisse) {
            assertTrue(
                "$name ragt aus der sicheren Zone: $weit von der Mitte, erlaubt sind $SICHER",
                weit <= SICHER,
            )
        }
    }

    @Test
    fun unterDemSymbolStehtYskar() {
        assertEquals("Yskar", ctx.applicationInfo.loadLabel(ctx.packageManager).toString())
    }

    private companion object {
        /** Kante der Flaeche, in der Android ein Symbol erwartet. */
        const val FLAECHE = 108

        /** Halbmesser der sicheren Zone: Was hier liegt, schneidet keine Maske ab. */
        const val SICHER = 33

        /** Halbmesser einer runden Maske - der sichtbare Teil hat die Kante 72. */
        const val MASKE = 36
    }
}
