package com.yskar.wallet

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.junit.runners.model.Statement
import java.io.File

/**
 * Die Oberflaeche, einmal durchgespielt - auf dem echten Geraet.
 *
 * WARUM INSTRUMENTIERT UND NICHT PER adb: HyperOS verbietet
 * "adb shell input" (INJECT_EVENTS), die App laesst sich also nicht von
 * aussen bedienen. Ein instrumentierter Test laeuft IM Prozess der App und
 * darf tippen. Nebenbei ist er wiederholbar, waehrend Tippen von Hand es
 * nicht ist.
 *
 * ACHTUNG: Der Test LOESCHT den Tresor dieses Geraets, bevor er startet.
 * Auf einem Telefon mit echtem Guthaben darf er nicht laufen. Die
 * Merkwoerter der Testwallet entstehen zufaellig und werden nie notiert -
 * was darauf liegt, waere verloren, und es liegt nichts darauf.
 *
 * Was hier NICHT geprueft werden kann: die Biometrie. Der Systemdialog
 * gehoert nicht zur App, und einen Fingerabdruck kann kein Test auflegen.
 * Dafuer gibt es den Selbsttest mit Knopf.
 */
@RunWith(AndroidJUnit4::class)
class OberflaecheTest {

    private val compose = createAndroidComposeRule<MainActivity>()

    /**
     * Raeumt VOR dem Start der Activity auf.
     *
     * Noetig, weil die Compose-Regel die Activity startet, bevor `@Before`
     * laeuft: Ein in `@Before` geloeschter Tresor kaeme zu spaet, die App
     * haette ihn dann schon gelesen und stuende auf dem Sperrbildschirm.
     */
    private val sauber = TestRule { basis, _: Description ->
        object : Statement() {
            override fun evaluate() {
                val ctx = InstrumentationRegistry.getInstrumentation().targetContext
                File(ctx.filesDir, "tresor.json").delete()
                File(ctx.filesDir, "tresor.json.neu").delete()
                File(ctx.filesDir, "selbsttest.tresor.json").delete()
                ctx.getSharedPreferences("yskar.bremse", 0).edit().clear().commit()
                // Dieser Test sucht deutsche Woerter. Die App richtet sich nach
                // der gewaehlten Sprache, sonst nach der des Geraets - also hier
                // festlegen, bevor die Activity startet.
                ctx.getSharedPreferences("yskar.vorlieben", 0).edit()
                    .putString("sprache", "de").commit()
                basis.evaluate()
            }
        }
    }

    @get:Rule
    val kette: RuleChain = RuleChain.outerRule(sauber).around(compose)

    private val pin = "246813"

    // ------------------------------------------------------------ Helfer

    private fun gibtEs(text: String): Boolean =
        compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()

    private fun warteAuf(text: String, dauerMs: Long = 20_000) {
        compose.waitUntil(dauerMs) { gibtEs(text) }
    }

    private fun pinTippen(ziffern: String) {
        for (z in ziffern) compose.onNodeWithText(z.toString()).performClick()
    }

    private fun woerterAblesen(): List<String> = (1..12).map { nr ->
        compose.onNodeWithTag("wort$nr")
            .fetchSemanticsNode()
            .config[SemanticsProperties.Text]
            .joinToString("") { it.text }
    }

    /** Vom Einstieg bis zur fertigen Wallet. */
    private fun walletAnlegen() {
        compose.onNodeWithText("Neue Wallet anlegen").performClick()

        warteAuf("Ich habe sie aufgeschrieben")
        val woerter = woerterAblesen()
        assertTrue("zwoelf Woerter erwartet", woerter.all { it.isNotBlank() })
        compose.onNodeWithText("Ich habe sie aufgeschrieben").performClick()

        warteAuf("Kurze Kontrolle")
        // Die App fragt drei zufaellige Stellen ab; welche, steht an den
        // Testmarken.
        var gefuellt = 0
        for (nr in 1..12) {
            val feld = compose.onAllNodesWithTag("pruefwort$nr").fetchSemanticsNodes()
            if (feld.isEmpty()) continue
            compose.onNodeWithTag("pruefwort$nr").performTextInput(woerter[nr - 1])
            gefuellt++
        }
        assertTrue("drei Felder erwartet, waren $gefuellt", gefuellt == 3)
        compose.onNodeWithText("Weiter").performClick()

        warteAuf("PIN festlegen")
        pinTippen(pin)
        warteAuf("PIN wiederholen")
        pinTippen(pin)

        // Der Tresor wird mit 400.000 Runden verschluesselt - das dauert
        // auf dem Geraet rund zwei Sekunden. Danach kommt seit Abschnitt 4
        // einmal die Einfuehrung; "Überspringen" steht auf jeder Karte.
        warteAuf("Überspringen", dauerMs = 60_000)
        compose.onNodeWithText("Überspringen").performClick()
        warteAuf("Guthaben", dauerMs = 60_000)
    }

    // ------------------------------------------------------------- Tests

    @Test
    fun walletAnlegenEntsperrenUndEmpfangen() {
        compose.onNodeWithText("Neue Wallet anlegen").assertIsDisplayed()
        walletAnlegen()

        compose.onNodeWithText("Empfangen").assertIsDisplayed()

        // Die Adresse muss zu sehen sein - und zwar als ysr1…
        assertTrue("Adresse fehlt auf der Wallet-Seite", gibtEs("ysr1"))

        // Eine frische Wallet hat kein Guthaben - dann darf "Senden" nicht
        // anklickbar sein. Ohne Kontostand ist ausserdem die laufende
        // Nummer unbekannt.
        compose.onNodeWithText("Senden").assertIsNotEnabled()

        compose.onNodeWithText("Empfangen").performClick()
        // Seit dem Umbau heisst der Knopf "Kopieren"; Anker ist die
        // Zeile ueber der Adresse, die es nur auf diesem Bildschirm gibt.
        warteAuf("Deine YSKAR-Adresse")
        assertTrue("QR-Seite zeigt die Adresse nicht", gibtEs("ysr1"))
        // "Zurück" ist seit dem Umbau ein Pfeil mit dieser Beschreibung.
        compose.onNodeWithContentDescription("Zurück").performClick()

        warteAuf("Guthaben")
        // "Sperren" ist seit dem Umbau ein Schloss-Symbol; das Wort steht
        // als Beschreibung daran, nicht mehr als Text.
        compose.onNodeWithContentDescription("Sperren").performClick()

        // Auf dem Sperrbildschirm darf kein Guthaben stehen.
        // Anker ist die Aufforderung, nicht der Name: Der steht seit der
        // Neugestaltung nur noch als "YSKAR" da.
        warteAuf("PIN eingeben")
        assertTrue(
            "gesperrt darf kein Guthaben sichtbar sein",
            !gibtEs("Guthaben"),
        )

        pinTippen(pin)
        warteAuf("Guthaben", dauerMs = 60_000)
        compose.onNodeWithText("Empfangen").assertIsDisplayed()
    }

    @Test
    fun falschePinLaesstNichtRein() {
        walletAnlegen()
        // "Sperren" ist seit dem Umbau ein Schloss-Symbol; das Wort steht
        // als Beschreibung daran, nicht mehr als Text.
        compose.onNodeWithContentDescription("Sperren").performClick()
        warteAuf("PIN eingeben")

        pinTippen("111111")

        warteAuf("PIN falsch", dauerMs = 60_000)
        assertTrue(
            "nach falscher PIN darf kein Guthaben zu sehen sein",
            !gibtEs("Guthaben"),
        )

        // Mit der richtigen PIN geht es danach weiter.
        pinTippen(pin)
        warteAuf("Guthaben", dauerMs = 60_000)
    }

    @Test
    fun wiederherstellenMitFalschenWoertern() {
        compose.onNodeWithText("Aus zwölf Wörtern wiederherstellen").performClick()
        warteAuf("Wallet wiederherstellen")

        // Zwoelf gueltige Woerter, aber mit falscher Pruefsumme. Seit dem
        // 11.11.2026 sind es zwoelf Felder; alle zwoelf Woerter gehen in das
        // erste und verteilen sich von dort (Merkwortfelder).
        compose.onNodeWithTag("merkwort1").performTextInput(
            "abandon abandon abandon abandon abandon abandon " +
                "abandon abandon abandon abandon abandon abandon"
        )
        compose.onNodeWithText("Weiter").performClick()

        warteAuf("passen nicht zusammen")
        assertTrue(
            "ohne gueltige Woerter darf es nicht zur PIN gehen",
            !gibtEs("PIN festlegen"),
        )
    }
}
