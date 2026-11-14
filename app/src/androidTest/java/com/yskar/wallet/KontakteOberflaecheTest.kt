package com.yskar.wallet

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.yskar.wallet.kern.Address
import com.yskar.wallet.kontakte.Kontakt
import com.yskar.wallet.kontakte.Kontakte
import com.yskar.wallet.netz.Senden
import com.yskar.wallet.ui.Buehne
import com.yskar.wallet.ui.KontaktBearbeitenScreen
import com.yskar.wallet.ui.SendenEingabeScreen
import com.yskar.wallet.ui.SendenPruefenScreen
import com.yskar.wallet.ui.YskarTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Kontakte auf dem Bildschirm - ohne Tresor, ohne Netz, ohne Speicher.
 *
 * Die Regeln selbst prueft `KontakteTest` auf der JVM. Hier geht es um das
 * Versprechen der Oberflaeche:
 *
 *  - Was nicht gueltig ist, wird nicht gespeichert, und der Grund steht da.
 *  - Die Auswahl beim Senden setzt die ADRESSE ins Feld - dieselbe, die
 *    danach geprueft und gesendet wird.
 *  - Beim Pruefen steht der Name UEBER der Adresse, nie an ihrer Stelle.
 *
 * Der Speicher ist hier eine Liste im Test; auf dem Geraet wird nichts
 * angelegt. Laeuft wie `SendenOberflaecheTest` ueber `adb shell am instrument`.
 */
@RunWith(AndroidJUnit4::class)
class KontakteOberflaecheTest {

    @get:Rule
    val compose = createComposeRule()

    private val anna = "ysr1j8nnl4kzwdck9u6ddltp8dmvauhg6rez967syq"
    private val ben = "ysr17g5ywa80hf2nyjkp59ha6a3ycs0jtsule8a6v4"

    /** Der "Speicher" dieses Tests. */
    private var liste = emptyList<Kontakt>()
    private var entfernt = 0

    private fun zeigeKontakt(vorlage: Kontakt? = null) {
        compose.setContent {
            YskarTheme {
                Buehne {
                    KontaktBearbeitenScreen(
                        vorlage = vorlage,
                        adresseVorgabe = null,
                        onSpeichern = { name, adresse ->
                            val e = Kontakte.speichern(liste, name, adresse, vorlage?.adresse)
                            if (e is Kontakte.Ergebnis.Ok) liste = e.liste
                            e
                        },
                        onEntfernen = if (vorlage == null) null else ({ entfernt++ }),
                        onZurueck = {},
                    )
                }
            }
        }
    }

    // -------------------------------------------------------------- Anlegen

    @Test
    fun eineUngueltigeAdresseWirdNichtGespeichert() {
        zeigeKontakt()
        compose.onNodeWithTag("kontakt_name").performTextInput("Anna")
        compose.onNodeWithTag("kontakt_adresse").performTextInput("ysr1keineadresse")
        compose.onNodeWithTag("kontakt_speichern").performClick()

        assertTrue(compose.gibtEs("keine gültige YSKAR-Adresse"))
        assertTrue("es darf nichts gespeichert sein", liste.isEmpty())
    }

    @Test
    fun einGueltigerKontaktWirdGespeichert() {
        zeigeKontakt()
        compose.onNodeWithTag("kontakt_name").performTextInput("  Anna  ")
        compose.onNodeWithTag("kontakt_adresse").performTextInput(anna)
        compose.onNodeWithTag("kontakt_speichern").performClick()

        // Der Name ohne Raender, die Adresse unveraendert.
        assertEquals(listOf(Kontakt("Anna", anna)), liste)
    }

    @Test
    fun eineAdresseBekommtKeinenZweitenNamen() {
        liste = listOf(Kontakt("Anna", anna))
        zeigeKontakt()
        compose.onNodeWithTag("kontakt_name").performTextInput("Jemand anderes")
        compose.onNodeWithTag("kontakt_adresse").performTextInput(anna)
        compose.onNodeWithTag("kontakt_speichern").performClick()

        // Der Satz nennt, unter welchem Namen sie schon da ist.
        assertTrue(compose.gibtEs("schon gespeichert, als „Anna“"))
        assertEquals(listOf(Kontakt("Anna", anna)), liste)
    }

    @Test
    fun entfernenBrauchtZweiTipps() {
        zeigeKontakt(Kontakt("Anna", anna))
        compose.onNodeWithTag("kontakt_entfernen").performClick()
        assertEquals("ein Tipp entfernt noch nichts", 0, entfernt)
        assertTrue(compose.gibtEs("noch einmal tippen"))
        compose.onNodeWithTag("kontakt_entfernen").performClick()
        assertEquals(1, entfernt)
    }

    // --------------------------------------------------------------- Senden

    @Test
    fun dieAuswahlSetztDieAdresseInsFeld() {
        var geprueft: Triple<String, String, String>? = null
        compose.setContent {
            YskarTheme {
                Buehne {
                    SendenEingabeScreen(
                        guthaben = 500_000_000L,
                        fehler = null,
                        onPruefen = { e, b, n -> geprueft = Triple(e, b, n) },
                        onZurueck = {},
                        kontakte = listOf(Kontakt("Anna", anna), Kontakt("Ben", ben)),
                    )
                }
            }
        }
        compose.onNodeWithTag("betrag").performTextInput("1,5")
        compose.onNodeWithTag("kontakt_waehlen").performClick()
        compose.onNodeWithText("Anna").performClick()

        // Zurueck auf der Eingabe: Der Betrag steht noch da, im Feld steht
        // Annas ADRESSE, und die Beschriftung nennt ihren Namen.
        compose.onNodeWithTag("empfaenger").assertTextContains(anna)
        assertTrue(compose.gibtEs("An Anna"))

        compose.onNodeWithTag("pruefen").performClick()
        assertEquals("zum Pruefen geht die Adresse, nicht der Name", anna, geprueft?.first)
        assertEquals("1,5", geprueft?.second)
    }

    @Test
    fun ohneKontakteGibtEsKeinenVerweis() {
        compose.setContent {
            YskarTheme {
                Buehne {
                    SendenEingabeScreen(
                        guthaben = 500_000_000L,
                        fehler = null,
                        onPruefen = { _, _, _ -> },
                        onZurueck = {},
                    )
                }
            }
        }
        assertEquals(0, compose.onAllNodesWithTag("kontakt_waehlen").fetchSemanticsNodes().size)
    }

    @Test
    fun beimPruefenStehtDerNameUeberDerGanzenAdresse() {
        compose.setContent {
            YskarTheme {
                Buehne {
                    SendenPruefenScreen(
                        entwurf = Senden.Entwurf(
                            empfaenger = anna,
                            empfaengerRoh = Address.decode(anna),
                            betrag = 150_000_000L,
                            gebuehr = 1_730L,
                            notiz = ByteArray(0),
                            nonce = 23,
                            fassung = 1,
                            bytes = 173,
                        ),
                        guthaben = 500_000_000L,
                        onWeiter = {},
                        onZurueck = {},
                        kontaktName = "Anna",
                    )
                }
            }
        }
        compose.onNodeWithTag("pruefen_kontakt").assertTextContains("An Anna")
        // Die Adresse ist trotzdem da - als Ganzes und Block fuer Block.
        compose.onNodeWithContentDescription(anna).assertIsDisplayed()
        for (block in anna.chunked(4)) {
            assertTrue("Block $block der Adresse fehlt", compose.gibtEs(block))
        }
    }
}
