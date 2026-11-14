package com.yskar.wallet

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.yskar.wallet.signatur.Nachricht
import com.yskar.wallet.signatur.Rueckruf
import com.yskar.wallet.signatur.Unterschrift
import com.yskar.wallet.ui.Buehne
import com.yskar.wallet.ui.SignierenScreen
import com.yskar.wallet.ui.Signierschritt
import com.yskar.wallet.ui.Signierstart
import com.yskar.wallet.ui.WalletModel.Signierantwort
import com.yskar.wallet.ui.YskarTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

/**
 * Der Bildschirm zum Anmelden und Signieren - ohne Tresor, ohne Netz.
 *
 * Das Modell ist hier eine Attrappe, die festhaelt, WAS der Bildschirm ihm
 * zum Unterschreiben reicht. Geprueft wird das Versprechen des Bildschirms:
 *
 *  - Er zeigt die Nachricht genau so, wie `Nachricht` sie baut.
 *  - Er reicht die ROHE Eingabe weiter, nicht etwas Eigenes - und erst nach
 *    sechs Ziffern.
 *  - Was abgelehnt ist, bekommt keinen Knopf zum Bestaetigen.
 *
 * Unterschrieben oder gesendet wird nichts; die Adresse ist die oeffentliche
 * Pruefadresse der Tests. Laeuft wie `SendenOberflaecheTest` ueber
 * `adb shell am instrument`.
 */
@RunWith(AndroidJUnit4::class)
class SignierenOberflaecheTest {

    @get:Rule
    val compose = createComposeRule()

    private val adresse = "ysr17g5ywa80hf2nyjkp59ha6a3ycs0jtsule8a6v4"
    private val uhr = Instant.parse("2026-11-09T12:31:00Z").toEpochMilli()

    private fun anmeldung(domain: String = "lab.yskar.app") = listOf(
        "YSKAR Signed Message", "purpose: login", "domain: $domain", "address: $adresse",
        "nonce: 9f2c4e1ab37d4c6e8a05b1d2e3f4a5b6", "issued: 2026-11-09T12:30:00Z",
        "expires: 2026-11-09T12:35:00Z",
    ).joinToString("\n")

    private fun code(msg: String = anmeldung(), cb: String = "https://lab.yskar.app/api/auth/wallet") =
        "yskar:login?msg=${Nachricht.textBase64url(msg)}&cb=${Nachricht.textBase64url(cb)}"

    /** Was der Bildschirm dem Modell gereicht hat. */
    private class Gereicht(val eingabe: String, val istCode: Boolean, val pin: String)

    private val gereicht = ArrayList<Gereicht>()
    private var zurueck = 0

    /** Zeigt den Bildschirm; [antwort] ist, was das Modell auf eine PIN sagt. */
    private fun zeige(
        start: Signierstart,
        anfang: Signierschritt? = null,
        antwort: (eingabe: String) -> Signierantwort = { Signierantwort.Fehler("nicht vorgesehen") },
    ) {
        compose.setContent {
            YskarTheme {
                Buehne {
                    SignierenScreen(
                        start = start,
                        adresse = adresse,
                        bioEingeschaltet = false,
                        onSignieren = { eingabe, istCode, pin, vorDemSenden, fertig ->
                            gereicht += Gereicht(eingabe, istCode, String(pin))
                            if (istCode) vorDemSenden()
                            fertig(antwort(eingabe))
                        },
                        onSignierenBio = { _, _, _, _, abgebrochen -> abgebrochen() },
                        onZurueck = { zurueck++ },
                        anfang = anfang,
                        uhrFest = uhr,
                    )
                }
            }
        }
    }

    private fun tippePin(ziffern: String = "123456") {
        for (z in ziffern) compose.onNodeWithText(z.toString()).performClick()
        compose.waitForIdle()
    }

    private fun signiert(nachricht: String) = Nachricht.Signiert(
        address = adresse,
        publicKey = "34792b244cfe851d8b3fc62ccb3033297ea2ca5bc31119b397bc3ec4132a751d",
        signature = "51".repeat(64),
        message = nachricht,
    )

    // ------------------------------------------------------------ freier Text

    @Test
    fun freierTextWirdGezeigtWieErSigniertWirdUndRohWeitergereicht() {
        zeige(Signierstart.Eingabe, antwort = { eingabe ->
            val p = Nachricht.vorbereiten(eingabe, adresse, uhr) as Nachricht.Pruefung.Text
            Signierantwort.Fertig(Unterschrift.Ergebnis.Fertig(signiert(p.nachricht)), gesendet = null)
        })

        compose.onNodeWithTag("signieren_weiter").assertIsNotEnabled()
        compose.onNodeWithTag("signieren_text").performTextInput("Hallo YSKAR")
        compose.onNodeWithTag("signieren_weiter").performClick()

        // Angezeigt wird die Nachricht MIT den Kopfzeilen, die die Wallet davorsetzt.
        compose.onNodeWithTag("genaue_nachricht")
            .assertTextContains("YSKAR Signed Message\npurpose: text\n\nHallo YSKAR")
        assertTrue("vor der PIN geht nichts ans Modell", gereicht.isEmpty())

        compose.onNodeWithTag("signieren_bestaetigen").performClick()
        tippePin("12345")
        assertTrue("fuenf Ziffern sind keine PIN", gereicht.isEmpty())
        tippePin("6")

        assertEquals(1, gereicht.size)
        assertEquals("die ROHE Eingabe", "Hallo YSKAR", gereicht[0].eingabe)
        assertEquals(false, gereicht[0].istCode)
        assertEquals("123456", gereicht[0].pin)

        // Danach die Signatur zum Kopieren.
        compose.onNodeWithTag("signatur_kopieren").assertIsDisplayed()
    }

    @Test
    fun rohdatenBekommenKeinenKnopfZumBestaetigen() {
        zeige(Signierstart.Eingabe)
        compose.onNodeWithTag("signieren_text").performTextInput("ab".repeat(40))
        compose.onNodeWithTag("signieren_weiter").performClick()

        compose.onNodeWithText("Rohdaten", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Es wurde nichts signiert.", substring = true).assertIsDisplayed()
        assertEquals(0, compose.onAllNodesWithTag("signieren_bestaetigen").fetchSemanticsNodes().size)
        assertTrue(gereicht.isEmpty())
    }

    // -------------------------------------------------------------- Anmeldung

    @Test
    fun anmeldungZeigtDieSeiteUndReichtDenCodeWeiter() {
        val c = code()
        zeige(Signierstart.Code(c), antwort = {
            Signierantwort.Fertig(
                Unterschrift.Ergebnis.Fertig(
                    signiert(anmeldung()),
                    (Nachricht.anmeldungAusCode(c, adresse, uhr) as Nachricht.QrPruefung.Ok).anmeldung,
                    "https://lab.yskar.app/api/auth/wallet",
                ),
                gesendet = Rueckruf.Ergebnis.Ok,
            )
        })

        compose.onNodeWithTag("anmelde_domain").assertTextContains("lab.yskar.app")
        compose.onNodeWithTag("genaue_nachricht").assertTextContains(anmeldung())
        compose.onNodeWithText("lab.yskar.app/api/auth/wallet").assertIsDisplayed()

        compose.onNodeWithTag("signieren_bestaetigen").performClick()
        // Ueber dem Ziffernfeld steht, wo man sich anmeldet.
        compose.onNodeWithTag("signieren_worum").assertTextContains("lab.yskar.app", substring = true)
        tippePin()

        assertEquals(1, gereicht.size)
        assertEquals("der Code, unveraendert", c, gereicht[0].eingabe)
        assertEquals(true, gereicht[0].istCode)
        compose.onNodeWithText("Angemeldet").assertIsDisplayed()
    }

    @Test
    fun codeMitFremdemRueckrufWirdAbgelehntUndNenntBeideNamen() {
        zeige(Signierstart.Code(code(cb = "https://evil.example/x")))

        compose.onNodeWithText("Dieser Code kann nicht verwendet werden").assertIsDisplayed()
        compose.onNodeWithText("lab.yskar.app").assertIsDisplayed()
        compose.onNodeWithText("evil.example").assertIsDisplayed()
        assertEquals(0, compose.onAllNodesWithTag("signieren_bestaetigen").fetchSemanticsNodes().size)
        assertTrue(gereicht.isEmpty())
    }

    @Test
    fun fremdeSeiteWirdAbgelehnt() {
        zeige(Signierstart.Code(code(msg = anmeldung(domain = "evil.example"), cb = "https://evil.example/x")))
        compose.onNodeWithText("gehört nicht zu YSKAR", substring = true).assertIsDisplayed()
        assertEquals(0, compose.onAllNodesWithTag("signieren_bestaetigen").fetchSemanticsNodes().size)
    }

    @Test
    fun abgelaufeneAnfrageBekommtKeinenKnopf() {
        // Dieselbe Anfrage, zehn Minuten spaeter angesehen.
        compose.setContent {
            YskarTheme {
                Buehne {
                    SignierenScreen(
                        start = Signierstart.Code(code()),
                        adresse = adresse,
                        bioEingeschaltet = false,
                        onSignieren = { e, c, p, _, _ -> gereicht += Gereicht(e, c, String(p)) },
                        onSignierenBio = { _, _, _, _, _ -> },
                        onZurueck = { zurueck++ },
                        uhrFest = uhr + 10 * 60_000,
                    )
                }
            }
        }
        compose.onNodeWithText("abgelaufen", substring = true).assertIsDisplayed()
        assertEquals(0, compose.onAllNodesWithTag("signieren_bestaetigen").fetchSemanticsNodes().size)
    }

    // ------------------------------------------------------------------- PIN

    @Test
    fun falschePinBleibtAufDemZiffernfeldUndZeigtDieMeldung() {
        var versuche = 0
        zeige(Signierstart.Code(code()), antwort = {
            versuche++
            Signierantwort.PinFalsch("PIN falsch. $versuche. Versuch.")
        })
        compose.onNodeWithTag("signieren_bestaetigen").performClick()
        tippePin("111111")

        compose.onNodeWithTag("signieren_worum").assertTextContains("PIN falsch. 1. Versuch.")
        assertEquals(1, gereicht.size)
        // Noch einmal: Das Feld ist leer und nimmt sechs neue Ziffern.
        tippePin("222222")
        assertEquals(2, gereicht.size)
        assertEquals("222222", gereicht[1].pin)
    }

    @Test
    fun wasDieSeiteSagtStehtAlsZitatDa() {
        zeige(
            Signierstart.Eingabe,
            anfang = Signierschritt.NichtAngenommen(
                "lab.yskar.app",
                Rueckruf.Ergebnis.Abgelehnt("This sign-in code was already used.", 409),
            ),
        )
        compose.onNodeWithText("lab.yskar.app hat die Anmeldung nicht angenommen").assertIsDisplayed()
        compose.onNodeWithText("„This sign-in code was already used.“", substring = true).assertIsDisplayed()
        compose.onNodeWithTag("signieren_schliessen").performClick()
        assertEquals(1, zurueck)
        assertNull(gereicht.firstOrNull())
    }
}
