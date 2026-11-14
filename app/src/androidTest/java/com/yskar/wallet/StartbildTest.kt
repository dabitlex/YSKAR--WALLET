package com.yskar.wallet

import android.os.Build
import android.util.TypedValue
import android.view.ContextThemeWrapper
import androidx.compose.ui.graphics.toArgb
import com.yskar.wallet.ui.Farbe
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Laesst sich das Thema des Startbildschirms auf diesem Geraet aufloesen?
 *
 * Den Startbildschirm zeichnet Android, bevor die App laeuft. Kein Test
 * kommt dorthin, und ein Bild davon gibt es nicht. Was sich pruefen laesst,
 * ohne die App zu starten: dass `Theme.Start` die Angaben traegt, die es
 * tragen soll, und dass das Markenbild - der Schriftzug "YSKAR" - auf
 * diesem Geraet geladen wird und die Groesse hat, die Android dafuer
 * vorsieht (200 x 80 dp).
 *
 * WAS DAS NICHT BEWEIST: dass der Hersteller das Markenbild auch zeigt.
 * Das sieht nur, wer die App oeffnet.
 *
 * Fasst weder Tresor noch Netz an und startet die App nicht.
 */
@RunWith(AndroidJUnit4::class)
class StartbildTest {

    private val ctx = ContextThemeWrapper(
        InstrumentationRegistry.getInstrumentation().targetContext,
        R.style.Theme_Start,
    )

    private fun wert(attribut: Int): TypedValue? {
        val v = TypedValue()
        return if (ctx.theme.resolveAttribute(attribut, v, true)) v else null
    }

    @Test
    fun derGrundIstDerDerApp() {
        val v = wert(androidx.core.splashscreen.R.attr.windowSplashScreenBackground)
        assertNotNull("windowSplashScreenBackground fehlt", v)
        // Derselbe Wert wie `Farbe.Grund` - zwei Orte fuer eine Farbe
        // (res/values/colors.xml), und dieser Test haelt sie zusammen.
        assertEquals(Farbe.Grund.toArgb(), v!!.data)
    }

    @Test
    fun dasZeichenIstDa() {
        val v = wert(androidx.core.splashscreen.R.attr.windowSplashScreenAnimatedIcon)
        assertNotNull("windowSplashScreenAnimatedIcon fehlt", v)
        assertEquals(R.drawable.symbol_start, v!!.resourceId)
    }

    @Test
    fun derSchriftzugWirdGeladenUndHatDieRichtigeGroesse() {
        assumeTrue("Das Markenbild gibt es erst ab Android 12", Build.VERSION.SDK_INT >= 31)

        val v = wert(android.R.attr.windowSplashScreenBrandingImage)
        assertNotNull("windowSplashScreenBrandingImage fehlt", v)
        assertEquals(R.drawable.marke_start, v!!.resourceId)

        val bild = ctx.getDrawable(v.resourceId)
        assertNotNull("Das Markenbild laesst sich nicht laden", bild)

        val dichte = ctx.resources.displayMetrics.density
        // 200 x 80 dp, auf einen Bildpunkt genau gerundet.
        assertTrue(
            "Breite ${bild!!.intrinsicWidth} statt ${200 * dichte}",
            kotlin.math.abs(bild.intrinsicWidth - 200 * dichte) <= 1f,
        )
        assertTrue(
            "Hoehe ${bild.intrinsicHeight} statt ${80 * dichte}",
            kotlin.math.abs(bild.intrinsicHeight - 80 * dichte) <= 1f,
        )
    }
}
