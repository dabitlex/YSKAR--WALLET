package com.yskar.wallet

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.yskar.wallet.kern.Address
import com.yskar.wallet.netz.Senden
import com.yskar.wallet.netz.Sendestatus
import com.yskar.wallet.ui.SendenEingabeScreen
import com.yskar.wallet.ui.SendenFertigScreen
import com.yskar.wallet.ui.SendenPruefenScreen
import com.yskar.wallet.ui.YskarTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Die Sendebildschirme einzeln - ohne Netz, ohne Tresor, ohne Schlüssel.
 *
 * HIER WIRD NICHTS GESENDET UND NICHTS UNTERSCHRIEBEN. Die Bildschirme
 * bekommen feste Daten und geben ihre Eingaben an eine Attrappe zurück.
 *
 * Worum es geht: Der Prüfschritt ist das Versprechen der App an den Nutzer.
 * Was dort steht, wird unterschrieben. Dieser Test belegt, dass dort das
 * Richtige steht - auf die letzte Nachkommastelle.
 */
@RunWith(AndroidJUnit4::class)
class SendenOberflaecheTest {

    @get:Rule
    val compose = createComposeRule()

    private val empfaenger = "ysr135sh9v4zxsp9rc8hx3st4alkskrh7mf6f73h8q"

    private fun entwurf(
        betrag: Long = 150_000_000L,
        gebuehr: Long = 1_730L,
        notiz: String = "Miete",
    ) = Senden.Entwurf(
        empfaenger = empfaenger,
        empfaengerRoh = Address.decode(empfaenger),
        betrag = betrag,
        gebuehr = gebuehr,
        notiz = notiz.toByteArray(Charsets.UTF_8),
        nonce = 23,
        fassung = 1,
        bytes = 173,
    )

    // ------------------------------------------------------------ Eingabe

    @Test
    fun eingabeReichtGenauDasWeiterWasGetipptWurde() {
        var gesehen: Triple<String, String, String>? = null
        compose.setContent {
            YskarTheme {
                SendenEingabeScreen(
                    guthaben = 500_000_000L,
                    fehler = null,
                    onPruefen = { e, b, n -> gesehen = Triple(e, b, n) },
                    onZurueck = {},
                )
            }
        }

        compose.onNodeWithTag("empfaenger").performTextInput(empfaenger)
        compose.onNodeWithTag("betrag").performTextInput("1,5")
        // Die Notiz ist seit dem 11.11.2026 eingeklappt, bis jemand sie will.
        compose.onNodeWithText("Notiz hinzufügen").performClick()
        compose.onNodeWithTag("notiz").performTextInput("Miete")
        compose.onNodeWithTag("pruefen").performClick()

        assertEquals(Triple(empfaenger, "1,5", "Miete"), gesehen)
    }

    @Test
    fun ohneAufgeklappteNotizGehtKeineMit() {
        var gesehen: Triple<String, String, String>? = null
        compose.setContent {
            YskarTheme {
                SendenEingabeScreen(
                    guthaben = 500_000_000L,
                    fehler = null,
                    onPruefen = { e, b, n -> gesehen = Triple(e, b, n) },
                    onZurueck = {},
                )
            }
        }

        compose.onNodeWithTag("empfaenger").performTextInput(empfaenger)
        compose.onNodeWithTag("betrag").performTextInput("1,5")
        // Das Notizfeld gibt es nicht, solange es eingeklappt ist.
        assertTrue(compose.onAllNodesWithTag("notiz").fetchSemanticsNodes().isEmpty())
        compose.onNodeWithTag("pruefen").performClick()

        assertEquals(Triple(empfaenger, "1,5", ""), gesehen)
    }

    /**
     * Was zu sehen ist, wird gesendet: Das Kreuz an der Notiz klappt nicht nur
     * zu, es LOESCHT sie. Eine Notiz, die eingeklappt und unsichtbar mitginge,
     * stuende fuer immer in der Kette, ohne dass der Besitzer sie noch saehe.
     */
    @Test
    fun eineEntfernteNotizGehtNichtMit() {
        var gesehen: Triple<String, String, String>? = null
        compose.setContent {
            YskarTheme {
                SendenEingabeScreen(
                    guthaben = 500_000_000L,
                    fehler = null,
                    onPruefen = { e, b, n -> gesehen = Triple(e, b, n) },
                    onZurueck = {},
                )
            }
        }

        compose.onNodeWithTag("empfaenger").performTextInput(empfaenger)
        compose.onNodeWithTag("betrag").performTextInput("1,5")
        compose.onNodeWithText("Notiz hinzufügen").performClick()
        compose.onNodeWithTag("notiz").performTextInput("doch nicht")
        compose.onNodeWithContentDescription("Notiz entfernen").performClick()

        // Wieder eingeklappt, und der Knopf zum Aufklappen ist zurueck.
        assertTrue(compose.onAllNodesWithTag("notiz").fetchSemanticsNodes().isEmpty())
        compose.onNodeWithText("Notiz hinzufügen").assertIsDisplayed()

        compose.onNodeWithTag("pruefen").performClick()
        assertEquals(Triple(empfaenger, "1,5", ""), gesehen)

        // Und wer sie wieder aufklappt, findet ein leeres Feld.
        compose.onNodeWithText("Notiz hinzufügen").performClick()
        assertTrue(!compose.gibtEs("doch nicht"))
    }

    @Test
    fun ohneAdresseOderBetragGehtEsNichtWeiter() {
        compose.setContent {
            YskarTheme {
                SendenEingabeScreen(
                    guthaben = 500_000_000L,
                    fehler = null,
                    onPruefen = { _, _, _ -> },
                    onZurueck = {},
                )
            }
        }
        compose.onNodeWithTag("pruefen").assertIsNotEnabled()

        compose.onNodeWithTag("empfaenger").performTextInput(empfaenger)
        compose.onNodeWithTag("pruefen").assertIsNotEnabled()

        compose.onNodeWithTag("betrag").performTextInput("1")
        compose.onNodeWithTag("pruefen").assertIsEnabled()
    }

    @Test
    fun dasGuthabenStehtUeberDemFeld() {
        compose.setContent {
            YskarTheme {
                SendenEingabeScreen(
                    guthaben = 128_457_210_946L,
                    fehler = null,
                    onPruefen = { _, _, _ -> },
                    onZurueck = {},
                )
            }
        }
        // Seit dem Umbau stehen "Verfügbar", die Zahl und "YSR" als drei
        // Texte nebeneinander. Geprueft wird die Zahl - vollstaendig.
        compose.onNodeWithText("Verfügbar").assertIsDisplayed()
        compose.onNodeWithText("1.284,57210946").assertIsDisplayed()
    }

    // ---------------------------------------------------------- Prüfschritt

    /**
     * Der wichtigste Test dieser Datei: Was im Prüfschritt steht, muss dem
     * Entwurf entsprechen - vollständig und ungerundet.
     */
    @Test
    fun derPruefschrittZeigtAllesUngerundet() {
        compose.setContent {
            YskarTheme {
                SendenPruefenScreen(
                    entwurf = entwurf(),
                    guthaben = 500_000_000L,
                    onWeiter = {},
                    onZurueck = {},
                )
            }
        }

        // Betrag, Gebühr, Gesamt - exakt. Der Betrag steht seit dem
        // 12.11.2026 nur noch gross im Kopf ("1,50", daneben "YSR").
        compose.onNodeWithText("1,50").assertIsDisplayed()
        compose.onNodeWithText("0,0000173 YSR").assertIsDisplayed()
        compose.onNodeWithText("1,5000173 YSR").assertIsDisplayed()

        // Die Adresse in Blöcken zu vier Zeichen. Seit dem Umbau ist jeder
        // Block ein eigener Text; das Ganze traegt die vollstaendige Adresse
        // als Beschreibung. Geprueft wird beides: die Adresse als Ganzes,
        // und dass JEDER ihrer Bloecke als Text dasteht.
        compose.onNodeWithContentDescription(empfaenger).assertIsDisplayed()
        for (block in empfaenger.chunked(4)) {
            assertTrue("Block $block der Adresse fehlt", compose.gibtEs(block))
        }

        // Die Notiz steht offen da, mit ihrer Beschriftung.
        compose.onNodeWithText("„Miete“").assertIsDisplayed()
        assertTrue(compose.gibtEs("öffentlich, für immer"))
        compose.onNodeWithText("Jetzt senden").assertIsEnabled()

        // Was man zum Entscheiden nicht braucht, liegt unter "Einzelheiten":
        // eingeklappt nicht da, aufgeklappt vollstaendig und ungerundet.
        assertTrue("eingeklappt darf die laufende Nummer nicht dastehen", !compose.gibtEs("Nr. 23"))
        compose.onNodeWithTag("einzelheiten").performClick()
        compose.waitForIdle()
        // Was danach übrig bleibt.
        assertTrue("danach verfuegbar fehlt", compose.gibtEs("3,4999827 YSR"))
        // Laufende Nummer, Fassung, Größe.
        assertTrue(compose.gibtEs("Nr. 23"))
        assertTrue(compose.gibtEs("Fassung 1"))
        assertTrue(compose.gibtEs("173 Byte"))
    }

    @Test
    fun kleinsteEinheitenVerschwindenNicht() {
        compose.setContent {
            YskarTheme {
                SendenPruefenScreen(
                    entwurf = entwurf(betrag = 1L, gebuehr = 1_680L, notiz = ""),
                    guthaben = 100_000_000L,
                    onWeiter = {},
                    onZurueck = {},
                )
            }
        }
        // Eine einzelne kleinste Einheit muss sichtbar bleiben.
        compose.onNodeWithText("0,00000001").assertIsDisplayed()
        compose.onNodeWithText("0,0000168 YSR").assertIsDisplayed()
        compose.onNodeWithText("0,00001681 YSR").assertIsDisplayed()
    }

    @Test
    fun ohneNotizKeineNotizkarte() {
        compose.setContent {
            YskarTheme {
                SendenPruefenScreen(
                    entwurf = entwurf(notiz = ""),
                    guthaben = 500_000_000L,
                    onWeiter = {},
                    onZurueck = {},
                )
            }
        }
        assertTrue(
            "ohne Notiz darf die Notizzeile nicht erscheinen",
            // Die Zeile heisst "Notiz · öffentlich, für immer". Mit einem
            // veralteten Suchtext waere diese Pruefung immer bestanden -
            // auch wenn die Zeile faelschlich dastuende. Der Test darueber
            // haelt den Suchtext ehrlich: Dort steht die Zeile da.
            !compose.gibtEs("öffentlich, für immer"),
        )
    }

    // -------------------------------------------------------------- Fertig

    @Test
    fun angenommenIstNichtErledigt() {
        compose.setContent {
            YskarTheme {
                SendenFertigScreen(
                    ergebnis = Senden.Ergebnis.Angenommen("ab".repeat(32)),
                    entwurf = entwurf(),
                    schritte = listOf(
                        Sendestatus.Teil.ERLEDIGT,
                        Sendestatus.Teil.ERLEDIGT,
                        Sendestatus.Teil.WARTET,
                        Sendestatus.Teil.OFFEN,
                        Sendestatus.Teil.OFFEN,
                    ),
                    onAktualisieren = {},
                    onFertig = {},
                )
            }
        }
        // Die Ueberschrift nennt den erreichten Schritt, nicht mehr: Der
        // Knoten hat angenommen - also "Abgeschickt", nicht "Bestätigt".
        compose.onNodeWithTag("stand_wort").assertTextContains("Abgeschickt")
        // Der Satz darunter sagt, was noch aussteht.
        assertTrue(compose.gibtEs("sind unterwegs"))
        assertTrue(compose.gibtEs("Das Netz bestätigt sie meist"))

        // Unter "Einzelheiten" stehen die fuenf Schritte des Netzes und der
        // Satz, dass es "endgueltig" nicht gibt.
        assertTrue("eingeklappt stehen die fuenf Schritte nicht da", !compose.gibtEs("Vom Knoten angenommen"))
        compose.onNodeWithTag("einzelheiten").performClick()
        compose.waitForIdle()
        assertTrue(compose.gibtEs("Vom Knoten angenommen"))
        assertTrue(compose.gibtEs("In einem Block"))
        assertTrue(compose.gibtEs("Endgültig"))
    }

    @Test
    fun ablehnungNenntGrundUndCode() {
        compose.setContent {
            YskarTheme {
                SendenFertigScreen(
                    ergebnis = Senden.Ergebnis.Abgelehnt(
                        "bad_nonce",
                        Senden.klartext("bad_nonce"),
                        "erwartet 24",
                    ),
                    entwurf = entwurf(),
                    schritte = null,
                    onAktualisieren = {},
                    onFertig = {},
                )
            }
        }
        compose.onNodeWithText("Abgelehnt").assertIsDisplayed()
        assertTrue(compose.gibtEs("laufende Nummer"))
        assertTrue(compose.gibtEs("bad_nonce"))
        assertTrue(compose.gibtEs("erwartet 24"))
        assertTrue(compose.gibtEs("nichts abgebucht"))
    }

    @Test
    fun fehlerWarntVorDemZweitenVersuch() {
        compose.setContent {
            YskarTheme {
                SendenFertigScreen(
                    ergebnis = Senden.Ergebnis.Fehler("Keine Verbindung zum Knoten."),
                    entwurf = entwurf(),
                    schritte = null,
                    onAktualisieren = {},
                    onFertig = {},
                )
            }
        }
        compose.onNodeWithText("Nicht gesendet").assertIsDisplayed()
        assertTrue(compose.gibtEs("Prüfe dein Guthaben"))
    }
}
