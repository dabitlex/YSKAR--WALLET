package com.yskar.wallet

import android.os.Build
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.yskar.wallet.ui.Buehne
import com.yskar.wallet.ui.Fensterschutz
import com.yskar.wallet.ui.Hintergrundschutz
import com.yskar.wallet.ui.Sichtschutz
import com.yskar.wallet.ui.YskarTheme
import com.yskar.wallet.ui.nichtVorn
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Die App im Hintergrund: gesperrt und nicht zu erkennen.
 *
 * Geprueft wird am Geraet, was sich ohne die echte App pruefen laesst - in
 * einer leeren Activity, mit denselben Funktionen, die `MainActivity`
 * benutzt (`Hintergrundschutz.einrichten`, `Sichtschutz`, `nichtVorn`):
 *
 *  - Der Vorhang verdeckt den Inhalt, fuer das Auge und fuer einen
 *    Bildschirmleser.
 *  - Er folgt dem Lebenslauf: liegt, sobald die Activity nicht mehr vorn
 *    ist, und geht weg, wenn sie zurueckkommt.
 *  - Beim Verlassen erfaehrt es das Modell (hier: ein Zaehler), bei der
 *    Rueckkehr auch.
 *  - Das Fenster bleibt fuer Bildschirmfotos offen (REGELN.md, Regel 14) -
 *    und der Schutz der zwoelf Woerter ueberlebt den Hintergrund.
 *
 * WAS DIESER TEST NICHT SIEHT: die App-Uebersicht selbst. Ob HyperOS dort
 * wirklich nichts zeigt, sieht nur, wer die App oeffnet und in die Uebersicht
 * wischt. `dumpsys activity activities` sagt auf diesem Geraet nichts dazu
 * (versucht am 11.11.2026: keine Zeile zum Vorschaubild).
 *
 * Fasst weder Tresor noch Netz an und startet die App nicht.
 */
@RunWith(AndroidJUnit4::class)
class HintergrundTest {

    @get:Rule
    val rolle = createAndroidComposeRule<ComponentActivity>()

    private fun warteBis(was: String, ms: Long = 3_000, probe: () -> Boolean) {
        val ende = System.currentTimeMillis() + ms
        while (System.currentTimeMillis() < ende) {
            // Im Testrahmen laeuft die Uhr von Compose nur, solange der Test
            // auf Ruhe wartet - ohne das kaeme keine Neuzeichnung zustande.
            rolle.waitForIdle()
            if (probe()) return
            Thread.sleep(40)
        }
        assertTrue("Nicht eingetreten: $was", probe())
    }

    private fun gesperrt(): Boolean {
        var flags = 0
        rolle.runOnUiThread { flags = rolle.activity.window.attributes.flags }
        return flags and WindowManager.LayoutParams.FLAG_SECURE != 0
    }

    /** Wie viele von neun Punkten quer ueber das Bild rot sind. */
    private fun rotePunkte(): Int {
        val bild = rolle.onRoot().captureToImage().asAndroidBitmap()
        var rot = 0
        for (i in 1..9) {
            val punkt = bild.getPixel(bild.width * i / 10, bild.height * i / 10)
            val r = (punkt shr 16) and 0xFF
            val g = (punkt shr 8) and 0xFF
            val b = punkt and 0xFF
            if (r > 200 && g < 60 && b < 60) rot++
        }
        return rot
    }

    @Test
    fun derVorhangVerdecktDenInhalt() {
        val verdeckt = mutableStateOf(false)
        rolle.setContent {
            YskarTheme {
                Buehne {
                    Sichtschutz(verdeckt = verdeckt.value) {
                        // Knallrot, damit es im Bild nicht zu uebersehen ist.
                        Box(Modifier.fillMaxSize().background(Color.Red)) {
                            Text("geheimer Inhalt")
                        }
                    }
                }
            }
        }
        rolle.onNodeWithText("geheimer Inhalt").assertIsDisplayed()
        rolle.onAllNodesWithTag("sichtschutz").assertCountEquals(0)
        assertTrue("Der Inhalt ist nicht rot - die Probe taugt nichts", rotePunkte() >= 7)

        verdeckt.value = true
        rolle.waitForIdle()
        rolle.onNodeWithTag("sichtschutz").assertIsDisplayed()
        // Fuer einen Bildschirmleser gibt es den Inhalt nicht mehr ...
        rolle.onAllNodesWithText("geheimer Inhalt").assertCountEquals(0)
        // ... und im Bild ist nichts mehr davon zu sehen.
        assertEquals("Unter dem Vorhang scheint der Inhalt durch", 0, rotePunkte())

        verdeckt.value = false
        rolle.waitForIdle()
        rolle.onNodeWithText("geheimer Inhalt").assertIsDisplayed()
        rolle.onAllNodesWithTag("sichtschutz").assertCountEquals(0)
    }

    @Test
    fun derVorhangFolgtDemLebenslauf() {
        val verlauf = CopyOnWriteArrayList<Boolean>()
        rolle.setContent {
            val verdeckt = nichtVorn()
            SideEffect { verlauf += verdeckt }
        }
        rolle.waitForIdle()
        assertFalse("Vorn, und doch verdeckt", verlauf.last())

        // Angehalten, aber noch sichtbar: so steht die App waehrend der
        // Wischgeste in die Uebersicht.
        rolle.activityRule.scenario.moveToState(Lifecycle.State.STARTED)
        warteBis("der Vorhang liegt, sobald die App nicht mehr vorn ist") { verlauf.last() }

        rolle.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        warteBis("der Vorhang geht weg, wenn die App zurueck ist") { !verlauf.last() }
    }

    @Test
    fun beimVerlassenUndBeiDerRueckkehrErfaehrtEsDasModell() {
        var draussen = 0
        var zurueck = 0
        rolle.runOnUiThread {
            Hintergrundschutz.einrichten(rolle.activity, { draussen++ }, { zurueck++ })
        }
        rolle.waitForIdle()
        // Ein Beobachter, der spaet dazukommt, bekommt die bisherigen
        // Ereignisse nachgereicht - darum zaehlt hier nur der Unterschied.
        val zurueckVorher = zurueck
        assertEquals("Ohne Verlassen gemeldet", 0, draussen)

        // Nur angehalten (Frage nach dem Finger, nach einer Erlaubnis):
        // KEINE Sperre.
        rolle.activityRule.scenario.moveToState(Lifecycle.State.STARTED)
        assertEquals("Schon beim Anhalten gesperrt", 0, draussen)

        // Wirklich weg.
        rolle.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        assertEquals("Das Verlassen kam nicht an", 1, draussen)

        rolle.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        assertEquals("Die Rueckkehr kam nicht an", zurueckVorher + 1, zurueck)
        assertEquals("Zweimal gesperrt", 1, draussen)
    }

    @Test
    fun dasFensterBleibtFuerBildschirmfotosOffen() {
        rolle.runOnUiThread { Hintergrundschutz.einrichten(rolle.activity, {}, {}) }
        rolle.waitForIdle()
        assertFalse("Vorn darf das Fenster nicht gesperrt sein", gesperrt())

        rolle.activityRule.scenario.moveToState(Lifecycle.State.STARTED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Ab Android 13 verzichtet das System selbst auf das Vorschaubild;
            // FLAG_SECURE bleibt den zwoelf Woertern vorbehalten.
            assertFalse("Der Hintergrund sperrt Bildschirmfotos", gesperrt())
        } else {
            assertTrue("Vor Android 13 muss das Fenster im Hintergrund gesperrt sein", gesperrt())
        }

        rolle.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        assertFalse("Nach der Rueckkehr bleibt das Fenster gesperrt", gesperrt())
    }

    @Test
    fun derSchutzDerWoerterUeberlebtDenHintergrund() {
        rolle.runOnUiThread {
            val fenster = rolle.activity.window
            // Ein Bildschirm mit den zwoelf Woertern ist offen ...
            Fensterschutz.halte(fenster, Fensterschutz.WOERTER)
            // ... die App geht in den Hintergrund und kommt zurueck.
            Fensterschutz.halte(fenster, Fensterschutz.HINTERGRUND)
            Fensterschutz.gibFrei(fenster, Fensterschutz.HINTERGRUND)
        }
        assertTrue("Der Hintergrund hat den Woertern den Schutz genommen", gesperrt())

        rolle.runOnUiThread {
            Fensterschutz.gibFrei(rolle.activity.window, Fensterschutz.WOERTER)
        }
        assertFalse("Die Sperre bleibt stehen, obwohl kein Grund mehr da ist", gesperrt())

        // Mehr freigeben als gehalten: bleibt frei und wirft nicht.
        rolle.runOnUiThread {
            Fensterschutz.gibFrei(rolle.activity.window, Fensterschutz.WOERTER)
            Fensterschutz.gibFrei(rolle.activity.window, Fensterschutz.HINTERGRUND)
        }
        assertFalse(gesperrt())
    }

}
