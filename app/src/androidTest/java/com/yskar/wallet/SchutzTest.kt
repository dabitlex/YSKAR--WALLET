package com.yskar.wallet

import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.yskar.wallet.ui.Buehne
import com.yskar.wallet.ui.EinstiegScreen
import com.yskar.wallet.ui.WiederherstellenScreen
import com.yskar.wallet.ui.WoerterAnsehenScreen
import com.yskar.wallet.ui.WoerterPruefenScreen
import com.yskar.wallet.ui.WoerterZeigenScreen
import com.yskar.wallet.ui.YskarTheme
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Sperren die vier Seiten mit den zwoelf Woertern das Fenster?
 *
 * REGELN.md, Regeln 14 und 28: `FLAG_SECURE` auf jedem Bildschirm, der die
 * Woerter zeigt - und nur dort. Bisher war der einzige Beleg dafuer, dass
 * ein Bildschirmfoto schwarz waere. Hier wird das Fenster selbst gefragt:
 * Solange einer der vier Screens gezeichnet ist, muss die Sperre gesetzt
 * sein; ist er weg, muss sie wieder fort sein.
 *
 * Seit Teil 2e des Umbaus hat jede dieser Seiten ein `…Bild` ohne Schutz,
 * das der Testrahmen `Bildschirmbilder` zeichnet. Umso wichtiger, dass der
 * `…Screen`, den die App aufruft, die Sperre nachweislich setzt.
 *
 * HIER WIRD KEIN TRESOR ANGEFASST und nichts gesendet. Die Woerter sind
 * der oeffentliche BIP39-Testvektor; dahinter liegt kein Guthaben.
 *
 *     adb shell am instrument -w -e class com.yskar.wallet.SchutzTest \
 *       com.yskar.wallet.debug.test/androidx.test.runner.AndroidJUnitRunner
 */
@RunWith(AndroidJUnit4::class)
class SchutzTest {

    @get:Rule
    val rolle = createAndroidComposeRule<ComponentActivity>()

    private val testwoerter = List(11) { "abandon" } + "about"

    private fun gesperrt(): Boolean {
        var flags = 0
        rolle.runOnUiThread { flags = rolle.activity.window.attributes.flags }
        return flags and WindowManager.LayoutParams.FLAG_SECURE != 0
    }

    /** Zeichnet [inhalt], prueft die Sperre, nimmt ihn weg, prueft wieder. */
    private fun sperrtUndGibtFrei(name: String, inhalt: @Composable () -> Unit) {
        val da = mutableStateOf(true)
        rolle.setContent { YskarTheme { Buehne { if (da.value) inhalt() } } }
        rolle.waitForIdle()
        assertTrue("$name: Das Fenster ist NICHT gesperrt", gesperrt())

        da.value = false
        rolle.waitForIdle()
        assertFalse("$name: Die Sperre bleibt nach dem Verlassen stehen", gesperrt())
    }

    @Test
    fun woerterZeigenSperrt() = sperrtUndGibtFrei("Wörter zeigen") {
        WoerterZeigenScreen(woerter = testwoerter, onWeiter = {}, onAbbrechen = {})
    }

    @Test
    fun woerterAnsehenSperrt() = sperrtUndGibtFrei("Wörter ansehen unter Mehr") {
        WoerterAnsehenScreen(woerter = testwoerter, onZurueck = {})
    }

    @Test
    fun gegenprobeSperrt() = sperrtUndGibtFrei("Gegenprobe") {
        WoerterPruefenScreen(
            positionen = listOf(2, 6, 10),
            fehler = null,
            onPruefen = {},
            onZurueck = {},
        )
    }

    @Test
    fun wiederherstellenSperrt() = sperrtUndGibtFrei("Wiederherstellen") {
        WiederherstellenScreen(onUebernehmen = { null }, onAbbrechen = {})
    }

    /** "Und nur dort": Ein Bildschirm ohne Woerter sperrt nichts. */
    @Test
    fun einstiegSperrtNicht() {
        rolle.setContent {
            YskarTheme { Buehne { EinstiegScreen(onNeu = {}, onWiederherstellen = {}) } }
        }
        rolle.waitForIdle()
        assertFalse("Der Einstieg darf das Fenster nicht sperren", gesperrt())
    }
}
