package com.yskar.wallet

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import com.yskar.wallet.kontakte.Kontakt
import com.yskar.wallet.netz.Art
import com.yskar.wallet.netz.Guthabenverlauf
import com.yskar.wallet.netz.Guthabenverlauf.Zeitraum
import com.yskar.wallet.netz.Konto
import com.yskar.wallet.netz.Senden
import com.yskar.wallet.netz.Sendestatus
import com.yskar.wallet.netz.VerlaufEintrag
import com.yskar.wallet.signatur.Nachricht
import com.yskar.wallet.signatur.Rueckruf
import com.yskar.wallet.sprache.Sprache
import com.yskar.wallet.sprache.texteFuer
import com.yskar.wallet.ui.BewegungScreen
import com.yskar.wallet.ui.EinstellungenScreen
import com.yskar.wallet.ui.EinstiegScreen
import com.yskar.wallet.ui.EmpfangenScreen
import com.yskar.wallet.ui.PinSetzenScreen
import com.yskar.wallet.ui.ScannerScreen
import com.yskar.wallet.ui.SendenEingabeScreen
import com.yskar.wallet.ui.SendenFertigScreen
import com.yskar.wallet.ui.SendenPinScreen
import com.yskar.wallet.ui.SendenPruefenScreen
import com.yskar.wallet.ui.SperreScreen
import com.yskar.wallet.ui.VerlaufScreen
import com.yskar.wallet.ui.WalletScreen
import com.yskar.wallet.ui.YskarTheme
import com.yskar.wallet.ui.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Bildschirmfotos zum Ansehen - kein Test, der etwas behauptet.
 *
 * Auf dem Entwicklungsgeraet laesst HyperOS keine Eingaben ueber adb zu.
 * Die Bildschirme sind damit von aussen nicht zu erreichen, und wer sie
 * beurteilen will, muss sie anders zu Gesicht bekommen. Diese Klasse
 * zeichnet jeden Bildschirm einmal mit Beispieldaten und legt ein PNG ab.
 *
 * WICHTIG, UND DER GRUND FUER EINE EIGENE KLASSE: Hier wird NICHTS am
 * Tresor angefasst. Kein `tresorFuer`, kein Loeschen, kein ViewModel. Die
 * Bildschirme bekommen ihre Daten als Werte uebergeben. `OberflaecheTest`
 * darf das echte Geraet nicht sehen, solange eine echte Wallet darauf
 * liegt - diese Klasse darf es.
 *
 * Die vier Seiten mit den zwoelf Woertern tragen in der App `FLAG_SECURE`;
 * ein Bildschirmfoto davon waere schwarz. Gezeichnet wird hier darum ihr
 * `…Bild` ohne den Schutz, mit dem oeffentlichen BIP39-Testvektor - siehe
 * unten, "Die Seiten mit den Woertern". Dass die Screens der App das
 * Fenster sperren, prueft `SchutzTest`.
 *
 *     adb shell am instrument -w -e class com.yskar.wallet.Bildschirmbilder \
 *       com.yskar.wallet.debug.test/androidx.test.runner.AndroidJUnitRunner
 *     adb pull /sdcard/Android/data/com.yskar.wallet.debug/files/bilder
 */
@RunWith(AndroidJUnit4::class)
class Bildschirmbilder {

    @get:Rule
    val rolle = createComposeRule()

    private val adresse = "ysr17g5ywa80hf2nyjkp59ha6a3ycs0jtsule8a6v4"
    private val gegen = "ysr1j8nnl4kzwdck9u6ddltp8dmvauhg6rez967syq"

    /**
     * Die Beispieldaten sind geformt wie echte Antworten des Spiegels
     * (app/src/test/resources/netz/account.json): Ein Blockertrag und ein
     * Pool-Anteil haben KEINE Gegenseite, und beim Pool-Anteil steht der
     * Name des Pools als Notiz da.
     */
    private fun eintrag(
        art: Art,
        betrag: Long,
        zeitVersatz: Long,
        notiz: String = "",
        gegenseite: String? = gegen,
        anteile: Int? = null,
    ) = VerlaufEintrag(
        txid = "76f239b4b9b8644e068334e7bf07d01744b8a894b66892b950f3058d9ceab797",
        height = 9472,
        idx = 0,
        zeit = System.currentTimeMillis() / 1000 - zeitVersatz,
        art = art,
        gegenseite = gegenseite,
        anteile = anteile,
        betrag = betrag,
        gebuehr = if (art == Art.EIN || art == Art.AUS) 16_800 else 0,
        notiz = notiz,
    )

    private val verlauf = listOf(
        eintrag(Art.EIN, 100_000_000, 3_600, "Danke!"),
        eintrag(Art.AUS, 2_500_000_000, 90_000),
        eintrag(Art.REWARD, 50_000_000_000, 180_000, gegenseite = null),
        eintrag(Art.POOL, 1_234_567, 400_000, "YSKAR MAIN", gegenseite = null, anteile = 18),
    )

    /** Heute um 18:17 nach der Uhr des Geraets - fuer "Aktualisiert 18:17". */
    private val heuteAbend: Long =
        java.time.LocalDate.now().atTime(18, 17)
            .atZone(java.time.ZoneId.systemDefault()).toEpochSecond()

    private val konto = Konto(
        adresse = adresse,
        guthaben = 128_457_310_946,
        nonce = 23,
        naechsteNonce = 24,
        wartend = emptyList(),
        wartendQuelle = "fullnode",
        verlauf = verlauf,
        verlaufWeiter = "zeiger",
    )

    // ------------------------------------------- Beispiele fuer das Diagramm
    //
    // Erfundene Bewegungen, gerechnet mit der echten Rechnung
    // (`Guthabenverlauf.rechne`). Das rechte Ende der Linie ist "Stand
    // 18:17", wie in der App.

    private var kurvenNr = 0

    private fun kb(art: Art, betrag: Long, vorStunden: Int, gebuehr: Long = 0): VerlaufEintrag {
        kurvenNr++
        return VerlaufEintrag(
            txid = "kurve$kurvenNr",
            height = 100_000L - vorStunden * 6,
            idx = 0,
            zeit = heuteAbend - vorStunden * 3_600L,
            art = art,
            gegenseite = null,
            anteile = null,
            betrag = betrag,
            gebuehr = gebuehr,
            notiz = "",
        )
    }

    /** Dreissig Tage: kleine Pool-Anteile, vier groessere Eingaenge, ein Ausgang. */
    private val kurvenBewegungen: List<VerlaufEintrag> = buildList {
        // Vor dem Zeitraum - belegt, dass davor nichts fehlt.
        add(kb(Art.EIN, 50_000_000_000, vorStunden = 45 * 24))
        for (tag in 29 downTo 1) {
            when {
                tag in setOf(25, 19, 12, 5) -> add(kb(Art.EIN, 6_875_472_219, tag * 24))
                tag % 3 == 0 -> add(kb(Art.POOL, 438_271_593, tag * 24))
            }
        }
        add(kb(Art.AUS, 2_500_000_000, vorStunden = 30, gebuehr = 16_800))
        add(kb(Art.EIN, 100_000_000, vorStunden = 5))
    }

    private fun kurve(
        bewegungen: List<VerlaufEintrag> = kurvenBewegungen,
        zeitraum: Zeitraum = Zeitraum.TAGE_30,
        bisZumAnfang: Boolean = false,
    ) = Guthabenverlauf.rechne(
        konto.guthaben, bewegungen, bisZumAnfang, zeitraum.beginn(heuteAbend), heuteAbend,
    )

    private val entwurf = Senden.Entwurf(
        empfaenger = gegen,
        empfaengerRoh = ByteArray(20),
        betrag = 100_000,
        gebuehr = 16_800,
        notiz = "Miete November".toByteArray(),
        nonce = 24,
        fassung = 2,
        bytes = 182,
    )

    // ------------------------------------------------------------- Ablage

    /** Die Aufnahmen der Sendebildschirme, am Anfang ihres Namens erkannt. */
    private val SENDEBILDER = listOf("08_", "09_", "10_", "13_", "16_")

    private fun halte(name: String, wartenMs: Long = 0) {
        rolle.waitForIdle()
        if (wartenMs > 0) Thread.sleep(wartenMs)
        val bild = rolle.onRoot().captureToImage().asAndroidBitmap()
        val ordner = File(
            InstrumentationRegistry.getInstrumentation().targetContext
                .getExternalFilesDir(null),
            "bilder",
        ).apply { mkdirs() }
        File(ordner, "$name.png").outputStream().use {
            bild.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        File(ordner, "rollen.txt").appendText(rollweg(name) + "\n")
    }

    /**
     * Muss dieser Bildschirm rollen? Gefragt wird der Bildschirm selbst.
     *
     * Am Bild laesst sich das nicht entscheiden: Ein Bildschirm mit fest
     * stehendem Fuss reicht immer bis unten, ob seine Mitte nun passt oder
     * ueberlaeuft. Jeder rollbare Bereich meldet aber zweierlei:
     *
     *   WEG       wie weit er sich rollen LAESST. Null heisst: Alles steht
     *             da. Mehr als null heisst noch nicht, dass etwas fehlt -
     *             am Ende eines Bildschirms steht oft nur Luft.
     *   UEBERHANG wie weit das tiefste Element mit Text oder Knopf unter
     *             den sichtbaren Bereich ragt. Das ist die eigentliche
     *             Antwort: Mehr als null heisst, jemand MUSS rollen, um
     *             es zu sehen.
     *
     * Gemessen wird in dem Rahmen, den auch die App benutzt (`Buehne`).
     *
     * Eine Zeile je Aufnahme: Name, Zahl der rollbaren Bereiche, Weg,
     * Ueberhang - beides in Bildpunkten. `werkzeug\bilder-holen.ps1` holt
     * die Datei mit und zeigt sie an.
     */
    private fun rollweg(name: String): String {
        val bereiche = rolle
            .onAllNodes(
                SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange),
                useUnmergedTree = true,
            )
            .fetchSemanticsNodes()
        var weg = 0f
        var ueberhang = 0f

        fun pruefe(knoten: SemanticsNode, unten: Float) {
            val zaehlt = SemanticsProperties.Text in knoten.config ||
                SemanticsProperties.ContentDescription in knoten.config ||
                SemanticsActions.OnClick in knoten.config
            if (zaehlt) {
                // positionInRoot, nicht boundsInRoot: Die Grenzen sind auf
                // das Sichtbare beschnitten und laegen damit nie darunter.
                val fuss = knoten.positionInRoot.y + knoten.size.height
                ueberhang = maxOf(ueberhang, fuss - unten)
            }
            for (kind in knoten.children) pruefe(kind, unten)
        }

        for (b in bereiche) {
            weg = maxOf(weg, b.config[SemanticsProperties.VerticalScrollAxisRange].maxValue())
            for (kind in b.children) pruefe(kind, b.boundsInRoot.bottom)
        }
        return "%-34s %d %d %d".format(name, bereiche.size, weg.toInt(), ueberhang.toInt())
    }

    /**
     * Der Rahmen um jeden Bildschirm - DERSELBE wie in der App.
     *
     * Frueher stand hier ein `Surface(fillMaxSize)`. Das gab dem Bildschirm
     * die volle Hoehe des Fensters, waehrend die App die Raender fuer
     * Status- und Navigationsleiste abzieht. Ein Bildschirm, der in der App
     * rollen muss, sah im Bild darum genauso aus wie einer, der passt
     * (bilder/LIESMICH.md, "Was diese Messung NICHT beweist").
     *
     * Jetzt zeichnen App und Testrahmen durch `Buehne`. Was im Bild Platz
     * hat, hat in der App Platz - auf diesem Geraet, mit dieser Schrift.
     *
     * [schrift] ist der Faktor der Systemschrift. 1,3 entspricht der
     * Einstellung "gross" und ist die Probe, ob ein Bildschirm auch dann
     * noch vollstaendig ist.
     */
    @Composable
    private fun Rahmen(schrift: Float, sprache: Sprache, inhalt: @Composable () -> Unit) {
        val dichte = LocalDensity.current
        CompositionLocalProvider(
            LocalDensity provides Density(dichte.density, schrift),
            LocalTexte provides texteFuer(sprache),
        ) {
            YskarTheme { Buehne { inhalt() } }
        }
    }

    /**
     * Zeichnet [inhalt] und legt ihn als `name.png` ab; mit [auchGross]
     * danach noch einmal mit Systemschrift 1,3 als `name_gross.png`.
     *
     * Beides in EINEM `setContent`: Der Testrahmen erlaubt den Aufruf nur
     * einmal je Test. Die Schriftgroesse ist darum ein Zustand, der
     * zwischen den beiden Aufnahmen umgestellt wird.
     */
    private fun zeige(
        name: String,
        auchGross: Boolean = false,
        wartenMs: Long = 0,
        davor: (() -> Unit)? = null,
        inhalt: @Composable () -> Unit,
    ) {
        val schrift = mutableFloatStateOf(1f)
        val sprache = mutableStateOf(Sprache.DEUTSCH)
        rolle.setContent { Rahmen(schrift.floatValue, sprache.value, inhalt) }
        // [davor] bringt den Bildschirm in den Zustand, der aufgenommen
        // werden soll - etwa zwei getippte Ziffern auf der Sperre.
        davor?.invoke()
        halte(name, wartenMs)
        if (auchGross) {
            schrift.floatValue = 1.3f
            halte(name + "_gross", wartenMs)
        }

        // Und auf Englisch, als `name_en.png`. Der Zustand des Bildschirms
        // bleibt dabei stehen - was [davor] getippt und aufgeklappt hat, ist
        // noch da. Texte, die der TEST hereinreicht (eine Fehlermeldung als
        // Parameter), bleiben deutsch: Sie kommen sonst aus dem Modell.
        // Der Musterbogen (00…) zeigt Bausteine mit festen Beispielwoertern
        // und hat keine englische Fassung.
        if (name.startsWith("00")) return
        schrift.floatValue = 1f
        sprache.value = Sprache.ENGLISCH
        halte(name + "_en", wartenMs)
        // Die Sendebildschirme duerfen auch auf Englisch nicht rollen
        // muessen (REGELN.md, Regel 29) - darum dort auch mit grosser Schrift.
        if (auchGross && SENDEBILDER.any { name.startsWith(it) }) {
            schrift.floatValue = 1.3f
            halte(name + "_en_gross", wartenMs)
        }
    }

    // ------------------------------------------------------------ Musterbogen
    //
    // Vier Blaetter mit den Bausteinen des Designs "Praegung", jedes einmal
    // in normaler und einmal in grosser Systemschrift. Sie zeigen keinen
    // Bildschirm der App, sondern das, woraus die Bildschirme gebaut werden
    // - damit sich die Grundlage beurteilen laesst, bevor der erste
    // Bildschirm umgebaut ist. Verglichen wird gegen design-neu/html.

    /** Blatt a: Guthabenkarte, runde Knoepfe, schmaler Kartenkopf, Verlauf. */
    @Test
    fun bausteineKarte() = zeige("00a_bausteine_karte", auchGross = true) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Row(
                Modifier.fillMaxWidth().padding(start = 20.dp, top = 16.dp, end = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Zeichen(groesse = 28.dp)
                Spacer(Modifier.width(10.dp))
                Text(
                    "YSKAR",
                    style = Schrift.Kopf.copy(letterSpacing = 0.10.em),
                    color = Farbe.Schrift,
                    modifier = Modifier.weight(1f),
                )
                Symbolknopf(R.drawable.ic_eye_fill, "Guthaben verbergen", {})
                Spacer(Modifier.width(8.dp))
                Symbolknopf(R.drawable.ic_lock_key_fill, "Sperren", {})
            }

            Kartenkopf(
                Modifier.padding(start = 16.dp, top = 12.dp, end = 16.dp).fillMaxWidth(),
                praegung = true,
            ) {
                Column(
                    Modifier.fillMaxWidth().heightIn(min = 204.dp).padding(20.dp),
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Guthaben",
                            style = Schrift.Schild,
                            color = Farbe.Weich,
                            modifier = Modifier.weight(1f),
                        )
                        Symbol(R.drawable.ic_arrows_clockwise_bold, 14.dp, farbe = Farbe.Weich)
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "Aktualisiert 18:17",
                            style = Schrift.Hinweis.copy(fontSize = 12.sp),
                            color = Farbe.Weich,
                        )
                    }
                    GuthabenZahl(konto.guthaben, Modifier.fillMaxWidth())
                    Adressmarke(adresse, aufKarte = true, onKopieren = {})
                }
            }

            Row(
                Modifier.fillMaxWidth().padding(start = 22.dp, top = 16.dp, end = 22.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                RunderKnopf(R.drawable.ic_arrow_up_right_bold, "Senden", {}, hervorgehoben = true)
                RunderKnopf(R.drawable.ic_arrow_down_left_bold, "Empfangen", {})
                RunderKnopf(R.drawable.ic_clock_counter_clockwise_fill, "Verlauf", {})
                RunderKnopf(R.drawable.ic_gear_six_fill, "Einstellungen", {})
            }

            Kartenkopf(
                Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp).fillMaxWidth(),
                rundung = 22.dp,
            ) {
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 66.dp).padding(horizontal = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Verfügbar",
                        style = Schrift.Klein,
                        color = Farbe.Weich,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        betragText("1.284,57310946", Farbe.Schrift, java.util.Locale.GERMANY),
                        style = Schrift.Mittel,
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "YSR",
                        style = Schrift.Einheit.copy(letterSpacing = 0.06.em),
                        color = Farbe.Weich,
                    )
                }
            }

            Flaeche(
                Modifier.padding(start = 16.dp, top = 12.dp, end = 16.dp),
                rundung = 22.dp,
                innen = PaddingValues(horizontal = 14.dp),
                abstand = 0.dp,
            ) {
                Verlaufszeile(
                    "Empfangen", "Heute, 10:52 · „Danke!“", "+1,00",
                    eingehend = true, linie = true,
                )
                Verlaufszeile(
                    "Gesendet", "Gestern, 10:52", "−25,00",
                    eingehend = false, linie = true,
                )
                Verlaufszeile(
                    "Mining-Anteil (Pool)", "05.11.2026, 20:45", "+0,01234567",
                    eingehend = true,
                )
            }

            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, top = 12.dp, end = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Knopf(
                    "Kopieren", {}, Modifier.weight(1f),
                    art = Knopfart.FLAECHE, symbol = R.drawable.ic_copy_fill,
                )
                Knopf(
                    "Teilen", {}, Modifier.weight(1f),
                    art = Knopfart.FLAECHE, symbol = R.drawable.ic_export_fill,
                )
            }
        }
    }

    /** Blatt b: der Beleg mit Abrisskanten, Warnhinweis, Hauptknopf. */
    @Test
    fun bausteineBeleg() = zeige("00b_bausteine_beleg", auchGross = true) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Kopfzeile("Prüfen", onZurueck = {}) { Schrittpunkte(2) }

            Beleg(Modifier.padding(horizontal = 16.dp)) {
                Kartenkopf(Modifier.fillMaxWidth(), gefasst = false) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 132.dp)
                            .padding(horizontal = 20.dp, vertical = 18.dp),
                        verticalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "Du sendest",
                                style = Schrift.Schild,
                                color = Farbe.Weich,
                                modifier = Modifier.weight(1f),
                            )
                            Zeichen(groesse = 26.dp)
                        }
                        GuthabenZahl(
                            entwurf.betrag,
                            Modifier.fillMaxWidth(),
                            gross = Schrift.BelegBetrag,
                            klein = null,
                        )
                    }
                }
                Abrisskante()
                Column(
                    Modifier.padding(start = 20.dp, top = 16.dp, end = 20.dp, bottom = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row {
                        Text(
                            "An diese Adresse",
                            style = Schrift.Klein,
                            color = Farbe.Leise,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            "Zeichen für Zeichen vergleichen",
                            style = Schrift.Hinweis.copy(fontSize = 12.sp),
                            color = YskarFarben.BlauHell,
                        )
                    }
                    AdresseInGruppen(
                        gegen, Modifier.fillMaxWidth(),
                        stil = Schrift.Adresse, luecke = 10.dp,
                    )
                }
                Abrisskante()
                Column(Modifier.padding(start = 20.dp, top = 4.dp, end = 20.dp, bottom = 2.dp)) {
                    Wertzeile("Betrag", "0,001", einheit = "YSR")
                    Wertzeile("Netzgebühr", "0,000168", einheit = "YSR")
                    Wertzeile("Gesamt", "0,001168", einheit = "YSR", betont = true)
                    Wertzeile("Danach verfügbar", "1.284,57194146", einheit = "YSR")
                    Wertzeile("Notiz · für immer in der Kette", "„Miete November“", linie = false)
                }
                BelegFuss("Nr. 24", "Fassung 2", "182 Byte")
            }

            Column(
                Modifier.padding(start = 16.dp, top = 22.dp, end = 16.dp, bottom = 22.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Warnhinweis(
                    "Nicht rückgängig zu machen: Eine Überweisung an eine falsche " +
                        "Adresse ist weg.",
                )
                Knopf(
                    "Jetzt senden", {}, Modifier.fillMaxWidth(),
                    symbol = R.drawable.ic_lock_key_fill,
                )
                Knopf("Weiter", {}, Modifier.fillMaxWidth(), aktiv = false)
            }
        }
    }

    /** Blatt c: Flaechen, Schalter, Zeilen, Marken, die kleinen Knoepfe. */
    @Test
    fun bausteineZeilen() = zeige("00c_bausteine_zeilen", auchGross = true) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Kopfzeile("Einstellungen", onZurueck = {}) { Marke("4 geladen") }

            Column(
                Modifier.padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Flaeche {
                    Abschnitt(R.drawable.ic_shield_check_fill, "Sicherheit")
                    Schaltzeile(
                        "Fingerabdruck", an = true, onWechsel = {},
                        hinweis = "Zum Entsperren und Bestätigen. Die PIN gilt " +
                            "weiter.",
                    )
                    Schaltzeile(
                        "Vibration", an = false, onWechsel = {},
                        hinweis = "Kurzes Rückmelden am Ziffernfeld und beim Scannen.",
                    )
                    Zeilenknopf("PIN ändern", {})
                    Zeilenknopf(
                        "Wallet von diesem Gerät entfernen", {},
                        symbol = R.drawable.ic_trash_fill, warnung = true,
                    )
                }

                Flaeche {
                    Abschnitt(R.drawable.ic_wallet_fill, "Diese Wallet")
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .gefasst(
                                16.dp, SolidColor(Color.Black.copy(alpha = 0.30f)),
                                Farbe.Linie, 0f,
                            )
                            .padding(start = 14.dp, top = 12.dp, end = 10.dp, bottom = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        AdresseInGruppen(adresse, Modifier.weight(1f))
                        Symbolknopf(R.drawable.ic_copy_fill, "Adresse kopieren", {})
                    }
                    Column {
                        Wertzeile("Netz", "yskar-main-1", hoehe = 40.dp)
                        Wertzeile("Explorer", "www.yskar.app", hoehe = 40.dp, linie = false)
                    }
                }

                // FlowRow, nicht Row: Bei grosser Schrift passen die drei
                // nicht mehr nebeneinander. In einer Row wird der letzte
                // dann auf einen Strich zusammengedrueckt.
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Knopf(
                        "QR-Code scannen", {},
                        art = Knopfart.BLAU, symbol = R.drawable.ic_qr_code_fill,
                    )
                    Knopf(
                        "Einfügen", {},
                        art = Knopfart.BLAU, symbol = R.drawable.ic_clipboard_text_fill,
                    )
                    Knopf("Fertig", {}, art = Knopfart.STILL)
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Adressmarke(adresse)
                    Einheit()
                    Schrittpunkte(1)
                }
            }
        }
    }

    /** Blatt d: Ziffernfeld und Schriftprobe. */
    @Test
    fun bausteineSchrift() = zeige("00d_bausteine_schrift", auchGross = true) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Ziffernfeld(laenge = 6, ziffern = "12", aktiv = true, onZiffern = {})

            Flaeche(abstand = 6.dp) {
                // Vier Schnitte derselben Datei. Sehen sie gleich aus, ist
                // die Gewichtsachse nicht eingestellt worden.
                for (gewicht in listOf(300, 400, 500, 600)) {
                    Text(
                        "Hanken $gewicht  Erstes Licht äöüß ÄÖÜ",
                        style = Schrift.Text.copy(fontWeight = FontWeight(gewicht), fontSize = 17.sp),
                        color = Farbe.Schrift,
                    )
                }
                Text("„ “ – … − × ·  0123456789", style = Schrift.Text, color = Farbe.Schrift)
                // Feste Ziffernbreiten: Die beiden Zeilen muessen gleich
                // breit sein, obwohl eine 1 schmaler ist als eine 8.
                Text("1.111.111,11111111", style = Schrift.Betrag, color = Farbe.Schrift)
                Text("8.888.888,88888888", style = Schrift.Betrag, color = Farbe.Schrift)
                for (gewicht in listOf(400, 500)) {
                    Text(
                        "Mono $gewicht  ysr1 7g5y äöüß „ “ – … 0O1l",
                        style = Schrift.AdresseKlein.copy(fontWeight = FontWeight(gewicht)),
                        color = Farbe.Schrift,
                    )
                }
            }
        }
    }

    // ----------------------------------------------------------- Bildschirme

    /** Tippt 1 und 2 - das Zeichen fuellt dabei sein erstes Band. */
    private fun zweiZiffern() {
        rolle.onNodeWithText("1").performClick()
        rolle.onNodeWithText("2").performClick()
    }

    @Test
    fun einstieg() = zeige("01_einstieg", auchGross = true) {
        EinstiegScreen(onNeu = {}, onWiederherstellen = {}, onSprache = {})
    }

    @Test
    fun pinSetzen() = zeige("02_pin_setzen", auchGross = true) {
        PinSetzenScreen(laeuft = false, fehler = null, onFertig = {}, onAbbrechen = {})
    }

    @Test
    fun sperre() = zeige("03_sperre", auchGross = true, wartenMs = 700, davor = ::zweiZiffern) {
        SperreScreen(
            adresse = adresse,
            bioEingeschaltet = true,
            laeuft = false,
            fehler = null,
            wartezeit = 0,
            onPin = {},
            onBio = {},
        )
    }

    /** Der Wallet-Bildschirm mit allem, was sich von Aufnahme zu Aufnahme aendert. */
    @Composable
    private fun Wallet(
        mitKonto: Konto?,
        laedt: Boolean,
        verborgen: Boolean = false,
        bekanntesGuthaben: Long? = null,
        bekanntSeit: Long? = null,
        netzMeldung: String? = null,
        kontakte: List<Kontakt> = emptyList(),
    ) {
        WalletScreen(
            adresse = adresse,
            konto = mitKonto,
            laedt = laedt,
            netzMeldung = netzMeldung,
            hinweis = null,
            onAktualisieren = {},
            onSenden = {},
            onEmpfangen = {},
            onVerlauf = {},
            onEinstellungen = {},
            onSperren = {},
            onBewegung = {},
            verborgen = verborgen,
            bekanntesGuthaben = bekanntesGuthaben,
            bekanntSeit = bekanntSeit,
            kontakte = kontakte,
        )
    }

    /**
     * Der Verlauf mit dem Diagramm obenauf. Seit dem 12.11.2026 steht es
     * hier und nicht mehr auf dem Hauptbildschirm.
     */
    @Composable
    private fun VerlaufMit(
        kurve: Guthabenverlauf.Ergebnis?,
        zeitraum: Zeitraum = Zeitraum.TAGE_30,
        verborgen: Boolean = false,
    ) {
        VerlaufScreen(
            eintraege = verlauf,
            laedt = false,
            kannNachladen = true,
            onNachladen = {},
            onZurueck = {},
            onBewegung = {},
            kurve = kurve,
            zeitraum = zeitraum,
            verborgen = verborgen,
        )
    }

    @Test
    fun wallet() = zeige("04_wallet", auchGross = true) {
        Wallet(
            konto, laedt = false,
            bekanntesGuthaben = konto.guthaben, bekanntSeit = heuteAbend,
        )
    }

    /** Sieben Tage gewaehlt: derselbe Verlauf, enger gefasst. */
    @Test
    fun walletSiebenTage() = zeige("07_verlauf_7_tage") {
        VerlaufMit(
            kurve = kurve(zeitraum = Zeitraum.TAGE_7), zeitraum = Zeitraum.TAGE_7,
        )
    }

    /**
     * Aeltere Seiten fehlen noch: Die Linie beginnt, wo sie belegt ist, und
     * der Titel sagt, ab wann.
     */
    @Test
    fun walletTeilweiseBelegt() = zeige("07_verlauf_teilweise", auchGross = true) {
        VerlaufMit(
            // Nur was juenger ist als elf Tage, und dahinter kommt noch etwas.
            kurve = kurve(kurvenBewegungen.filter { it.zeit!! > heuteAbend - 11 * 86_400L }),
        )
    }

    /** Im Zeitraum hat sich nichts bewegt: eine waagrechte Linie, Summen null. */
    @Test
    fun walletOhneBewegung() = zeige("07_verlauf_ohne_bewegung") {
        VerlaufMit(
            kurve = kurve(kurvenBewegungen.take(1)),
        )
    }

    /**
     * Der Verlauf ist ganz geladen und erklaert das Guthaben nicht: keine
     * Linie, ein Satz.
     */
    @Test
    fun walletUnstimmig() = zeige("07_verlauf_unstimmig") {
        VerlaufMit(
            kurve = kurve(bisZumAnfang = true),
        )
    }

    /**
     * Sehr grosse Summen (ueber eine Million YSR in jede Richtung): Sie
     * stehen vollstaendig da, notfalls untereinander.
     */
    @Test
    fun walletGrosseSummen() = zeige("07_verlauf_grosse_summen", auchGross = true) {
        VerlaufMit(
            kurve = kurve(
                listOf(
                    kb(Art.EIN, 50_000_000_000, vorStunden = 45 * 24),
                    kb(Art.EIN, 127_645_903_218_574, vorStunden = 20 * 24),
                    kb(Art.AUS, 127_645_842_310_078, vorStunden = 10 * 24, gebuehr = 16_800),
                ),
                zeitraum = Zeitraum.JAHR,
            ),
            zeitraum = Zeitraum.JAHR,
        )
    }

    /** Das Auge ist gedrueckt: keine Zahl auf der Karte, keine in der Liste. */
    @Test
    fun walletVerborgen() = zeige("04_wallet_verborgen") {
        Wallet(
            konto, laedt = false, verborgen = true,
            bekanntesGuthaben = konto.guthaben, bekanntSeit = heuteAbend,
        )
    }

    /** Noch nie etwas vom Netz gehoert, und es wird gefragt: nur Platzhalter. */
    @Test
    fun walletLeer() = zeige("05_wallet_leer", auchGross = true) {
        Wallet(null, laedt = true)
    }

    /**
     * Kein Netz, aber ein Stand von vorgestern: Die Zahl steht da, und sie
     * sieht alt aus - mit Datum, nicht nur mit Uhrzeit. "Senden" ist aus.
     */
    @Test
    fun walletAlterStand() = zeige("05_wallet_alter_stand") {
        Wallet(
            null, laedt = false,
            bekanntesGuthaben = konto.guthaben,
            bekanntSeit = heuteAbend - 2 * 86_400,
            netzMeldung = "Keine Verbindung. Angezeigt wird der letzte bekannte Stand.",
        )
    }

    // ---------------------------------------------------------- Kontakte (25)
    //
    // Namen statt Adressen. Die Namen hier sind erfunden; die Adressen sind
    // die beiden oeffentlichen Beispieladressen dieses Testrahmens.

    private val kontakte = listOf(
        Kontakt("Anna", gegen),
        Kontakt("Ben · Werkstatt", adresse),
    )

    /** Die Gegenadresse heisst "Anna": Im Verlauf steht der Name, nicht das Wort. */
    @Test
    fun walletMitKontakten() = zeige("04_wallet_kontakte") {
        Wallet(
            konto, laedt = false,
            bekanntesGuthaben = konto.guthaben, bekanntSeit = heuteAbend,
            kontakte = kontakte,
        )
    }

    @Test
    fun kontakteListe() = zeige("25_kontakte", auchGross = true) {
        KontakteScreen(kontakte = kontakte, onNeu = {}, onBearbeiten = {}, onZurueck = {})
    }

    @Test
    fun kontakteLeer() = zeige("25_kontakte_leer") {
        KontakteScreen(kontakte = emptyList(), onNeu = {}, onBearbeiten = {}, onZurueck = {})
    }

    @Test
    fun kontaktNeu() = zeige("25_kontakt_neu") {
        KontaktBearbeitenScreen(
            vorlage = null,
            adresseVorgabe = null,
            onSpeichern = { _, _ -> com.yskar.wallet.kontakte.Kontakte.Ergebnis.Ok(emptyList()) },
            onEntfernen = null,
            onZurueck = {},
        )
    }

    /** Ein vorhandener Kontakt: Name, ganze Adresse, unten "Kontakt entfernen". */
    @Test
    fun kontaktBearbeiten() = zeige("25_kontakt_bearbeiten", auchGross = true) {
        KontaktBearbeitenScreen(
            vorlage = kontakte[0],
            adresseVorgabe = null,
            onSpeichern = { _, _ -> com.yskar.wallet.kontakte.Kontakte.Ergebnis.Ok(emptyList()) },
            onEntfernen = {},
            onZurueck = {},
        )
    }

    /** Eine ungueltige Adresse: Der Grund steht da, gespeichert wird nichts. */
    @Test
    fun kontaktAbgelehnt() = zeige(
        "25_kontakt_abgelehnt",
        wartenMs = 300,
        davor = {
            rolle.onNodeWithTag("kontakt_name").performTextInput("Anna")
            rolle.onNodeWithTag("kontakt_adresse").performTextInput("ysr1keineadresse")
            rolle.onNodeWithTag("kontakt_speichern").performClick()
        },
    ) {
        KontaktBearbeitenScreen(
            vorlage = null,
            adresseVorgabe = null,
            onSpeichern = { name, adr ->
                com.yskar.wallet.kontakte.Kontakte.speichern(emptyList(), name, adr)
            },
            onEntfernen = null,
            onZurueck = {},
        )
    }

    /** Die Auswahl beim Senden, ueber der Eingabe. */
    @Test
    fun kontaktWaehlen() = zeige("25_kontakt_waehlen") {
        KontaktAuswahl(kontakte = kontakte, onGewaehlt = {}, onAbbrechen = {})
    }

    /**
     * Senden mit Kontakten: am Adressfeld der Verweis "Kontakte", und weil
     * die Adresse im Feld einen Namen hat, steht er als Beschriftung da.
     * Ein Sendebildschirm - er darf nicht rollen muessen (Regel 29).
     */
    @Test
    fun sendenEingabeKontakt() = zeige(
        "08_senden_eingabe_kontakt",
        auchGross = true,
        wartenMs = 600,
        davor = {
            rolle.onNodeWithTag("betrag").performTextInput("25")
            rolle.onNodeWithTag("empfaenger").performTextInput(gegen)
        },
    ) {
        SendenEingabeScreen(
            guthaben = konto.guthaben,
            fehler = null,
            onPruefen = { _, _, _ -> },
            onZurueck = {},
            kontakte = kontakte,
        )
    }

    /** Pruefen: der Name UEBER der Adresse, die Adresse weiter vollstaendig. */
    @Test
    fun sendenPruefenKontakt() = zeige("09_senden_pruefen_kontakt", auchGross = true) {
        SendenPruefenScreen(
            entwurf = entwurf,
            guthaben = konto.guthaben,
            onWeiter = {},
            onZurueck = {},
            kontaktName = "Anna",
        )
    }

    /** Die Zahlung an einen Kontakt: "An Anna", darunter die ganze Adresse. */
    @Test
    fun bewegungKontakt() = zeige("14_bewegung_kontakt") {
        BewegungScreen(
            eintrag = verlauf[1],
            spitze = 9_501,
            onZurueck = {},
            kontakte = kontakte,
            onKontaktAnlegen = {},
        )
    }

    @Test
    fun empfangen() = zeige("06_empfangen", auchGross = true) {

        EmpfangenScreen(adresse = adresse, onZurueck = {})
    }

    @Test
    fun verlauf() = zeige("07_verlauf", auchGross = true) {
        VerlaufMit(kurve())
    }

    /** Das Auge ist gedrueckt: auch im Verlauf keine Zahl, kein Diagramm. */
    @Test
    fun verlaufVerborgen() = zeige("07_verlauf_verborgen") {
        VerlaufMit(kurve(), verborgen = true)
    }

    @Test
    fun bewegung() = zeige("14_bewegung", auchGross = true) {
        BewegungScreen(
            eintrag = verlauf[1],
            spitze = 9_501,
            onZurueck = {},
            onKontaktAnlegen = {},
        )
    }

    /** Dieselbe Zahlung, "Einzelheiten" aufgeklappt: nichts fehlt gegenueber frueher. */
    @Test
    fun bewegungEinzelheiten() = zeige("14_bewegung_einzelheiten") {
        BewegungScreen(
            eintrag = verlauf[1],
            spitze = 9_501,
            onZurueck = {},
            onKontaktAnlegen = {},
            einzelheitenOffen = true,
        )
    }

    @Test
    fun bewegungEingang() = zeige("15_bewegung_eingang", auchGross = true) {
        BewegungScreen(
            eintrag = verlauf[0],
            spitze = 9_472,
            onZurueck = {},
            onKontaktAnlegen = {},
        )
    }

    @Test
    fun sendenEingabe() = zeige("08_senden_eingabe", auchGross = true) {
        SendenEingabeScreen(
            guthaben = konto.guthaben,
            fehler = null,
            onPruefen = { _, _, _ -> },
            onZurueck = {},
        )
    }

    /**
     * Derselbe Bildschirm ausgefuellt: ein langer Betrag (er wird kleiner
     * gesetzt, nicht abgeschnitten), die Adresse, die Notiz aufgeklappt.
     */
    @Test
    fun sendenEingabeGefuellt() = zeige(
        "08_senden_eingabe_gefuellt",
        auchGross = true,
        wartenMs = 600,
        davor = {
            rolle.onNodeWithTag("betrag").performTextInput("1284,57310946")
            rolle.onNodeWithTag("empfaenger").performTextInput(gegen)
            rolle.onNodeWithText("Notiz hinzufügen").performClick()
            rolle.onNodeWithTag("notiz").performTextInput("Danke!")
        },
    ) {
        SendenEingabeScreen(
            guthaben = konto.guthaben,
            fehler = null,
            onPruefen = { _, _, _ -> },
            onZurueck = {},
        )
    }

    @Test
    fun sendenPruefen() = zeige("09_senden_pruefen", auchGross = true) {
        SendenPruefenScreen(
            entwurf = entwurf,
            guthaben = konto.guthaben,
            onWeiter = {},
            onZurueck = {},
        )
    }

    /** Der Pruefschritt mit aufgeklappten Einzelheiten. Dieser Zustand DARF rollen. */
    @Test
    fun sendenPruefenEinzelheiten() = zeige("19e_pruefen_einzelheiten") {
        SendenPruefenScreen(
            entwurf = entwurf,
            guthaben = konto.guthaben,
            onWeiter = {},
            onZurueck = {},
            einzelheitenOffen = true,
        )
    }

    private val kennung = "76f239b4b9b8644e068334e7bf07d01744b8a894b66892b950f3058d9ceab797"

    private val angenommenWartet = listOf(
        Sendestatus.Teil.ERLEDIGT,
        Sendestatus.Teil.ERLEDIGT,
        Sendestatus.Teil.WARTET,
        Sendestatus.Teil.OFFEN,
        Sendestatus.Teil.OFFEN,
    )

    /** Die Zeiten dieses Geraets, wie sie nach einem Senden um 18:17 stuenden. */
    private val zeiten = Sendezeiten(
        unterschrieben = heuteAbend + 2,
        angenommen = heuteAbend + 3,
        warteliste = heuteAbend + 3,
    )

    @Test
    fun sendenFertig() = zeige("10_senden_fertig", auchGross = true) {
        SendenFertigScreen(
            ergebnis = Senden.Ergebnis.Angenommen(kennung),
            entwurf = entwurf,
            schritte = angenommenWartet,
            onAktualisieren = {},
            onFertig = {},
            zeiten = zeiten.copy(warteliste = null),
            geprueft = heuteAbend + 5,
            // Feste Uhr, damit das Bild bei jedem Lauf dasselbe zeigt.
            jetzt = heuteAbend + 13,
        )
    }

    /** Nach dem Senden, "Einzelheiten" aufgeklappt. Dieser Zustand DARF rollen. */
    @Test
    fun sendenFertigEinzelheiten() = zeige("19e_fertig_einzelheiten") {
        SendenFertigScreen(
            ergebnis = Senden.Ergebnis.Angenommen(kennung),
            entwurf = entwurf,
            schritte = angenommenWartet,
            onAktualisieren = {},
            onFertig = {},
            zeiten = zeiten.copy(warteliste = null),
            geprueft = heuteAbend + 5,
            jetzt = heuteAbend + 13,
            einzelheitenOffen = true,
        )
    }

    /**
     * Derselbe Zustand, aber die App weiss keine Zeiten mehr - so stuende
     * es da, wenn sie zwischendurch beendet worden waere. Dann bleiben die
     * Woerter: "erledigt", "wartet", "offen".
     */
    @Test
    fun sendenFertigOhneZeiten() = zeige("10_senden_fertig_ohne_zeiten") {
        SendenFertigScreen(
            ergebnis = Senden.Ergebnis.Angenommen(kennung),
            entwurf = entwurf,
            schritte = angenommenWartet,
            onAktualisieren = {},
            onFertig = {},
        )
    }

    /** Der Knoten hat abgelehnt: nichts abgebucht, und das steht da. */
    @Test
    fun sendenAbgelehnt() = zeige("10_senden_abgelehnt") {
        SendenFertigScreen(
            ergebnis = Senden.Ergebnis.Abgelehnt(
                "bad_nonce",
                Senden.klartext("bad_nonce"),
                "erwartet 24",
            ),
            entwurf = entwurf,
            schritte = null,
            onAktualisieren = {},
            onFertig = {},
        )
    }

    /**
     * Derselbe Bildschirm am anderen Ende: alle fuenf Schritte erledigt.
     *
     * Eigenes Bild, weil hier das Zeichen ganz gefuellt ist UND weil in
     * diesem Zustand am ehesten etwas steht, das nach "endgueltig"
     * klingt. Regel 20 sagt, dass es das nicht gibt - das muss man sehen
     * koennen, nicht nur im Quelltext nachlesen.
     */
    @Test
    fun sendenFertigBestaetigt() = zeige("13_senden_fertig_bestaetigt", auchGross = true) {
        SendenFertigScreen(
            ergebnis = Senden.Ergebnis.Angenommen(kennung),
            entwurf = entwurf,
            schritte = List(5) { Sendestatus.Teil.ERLEDIGT },
            onAktualisieren = {},
            onFertig = {},
            block = 8_602,
            bestaetigungen = 2,
            zeiten = zeiten,
            geprueft = heuteAbend + 5,
            jetzt = heuteAbend + 13,
        )
    }

    /**
     * Der QR-Scanner, mit laufender Kamera.
     *
     * Braucht die Erlaubnis vorher, sonst steht der Systemdialog im Bild:
     *
     *     adb shell pm grant com.yskar.wallet.debug android.permission.CAMERA
     *
     * Die anderthalb Sekunden sind kein Schoenheitsfehler: CameraX bindet
     * sich asynchron, und `waitForIdle` weiss von der Kamera nichts.
     */
    @Test
    fun scanner() = zeige("12_scanner", wartenMs = 1_800) {
        ScannerScreen(onGefunden = {}, onAbbrechen = {})
    }

    /**
     * Der Scanner, wenn die Kamera verweigert wurde.
     *
     * Auf einem Geraet, das die Erlaubnis schon erteilt hat, ist dieser
     * Zustand sonst nie zu sehen. `ScannerBild` ist der Scanner ohne
     * Kamera und ohne Erlaubnisfrage - es zeichnet, was man ihm sagt.
     */
    @Test
    fun scannerOhneKamera() = zeige("12_scanner_ohne_kamera", auchGross = true) {
        ScannerBild(
            erlaubt = false,
            abgelehnt = true,
            fehler = null,
            licht = false,
            onLicht = {},
            onEinstellungen = {},
            onAbbrechen = {},
        ) {}
    }

    /** Der Scanner hat einen Code gelesen, der keine YSKAR-Adresse ist. */
    @Test
    fun scannerFalscherCode() = zeige("12_scanner_falscher_code", wartenMs = 1_800) {
        ScannerBild(
            erlaubt = true,
            abgelehnt = false,
            fehler = "Das ist keine YSKAR-Adresse.",
            licht = true,
            onLicht = {},
            onEinstellungen = {},
            onAbbrechen = {},
        ) {}
    }

    /**
     * Der Bildschirm, auf dem unterschrieben wird.
     *
     * Bisher fehlte er hier - ausgerechnet der, auf dem es ernst wird.
     * Dass er "nicht sauber" aussah, konnte darum niemand sehen.
     */
    @Test
    fun sendenPin() = zeige("16_senden_pin", auchGross = true, wartenMs = 700, davor = ::zweiZiffern) {
        SendenPinScreen(
            entwurf = entwurf,
            laeuft = false,
            fehler = null,
            bioEingeschaltet = true,
            onPin = {},
            onBio = {},
            onZurueck = {},
        )
    }

    // ---------------------------------------------- Einstellungen (Teil 2e)

    /** Der Bildschirm "Einstellungen" mit Beispieldaten; nichts davon tut etwas. */
    @Composable
    private fun Mehr() {
        EinstellungenScreen(
            adresse = adresse,
            bioMoeglich = true,
            bioEingeschaltet = true,
            benachrichtigungen = true,
            vibration = true,
            hinweis = null,
            fehler = null,
            onBioEin = {},
            onBioAus = {},
            onPinAendern = { _, _, _ -> },
            onBenachrichtigungen = {},
            onVibration = {},
            onWoerterZeigen = {},
            onWoerterZeigenBio = {},
            onSelbsttest = {},
            onLoeschen = {},
            onEinfuehrung = {},
            onZurueck = {},
        )
    }

    private fun tippeZeile(text: String) {
        rolle.onNodeWithText(text).performScrollTo().performClick()
    }

    @Test
    fun einstellungen() = zeige("11_einstellungen", auchGross = true) { Mehr() }

    /** Derselbe Bildschirm, ans Ende gerollt: "Diese Wallet" und das Entfernen. */
    @Test
    fun einstellungenUnten() = zeige(
        "11_einstellungen_unten",
        wartenMs = 300,
        davor = { rolle.onNodeWithTag("einstellungen_ende").performScrollTo() },
    ) { Mehr() }

    /** Die Seite "Sprache": eine Zeile je Sprache, die gewaehlte mit Haken. */
    @Test
    fun sprachwahl() = zeige(
        "11_sprache",
        auchGross = true,
        wartenMs = 300,
        davor = { rolle.onNodeWithTag("sprache").performScrollTo().performClick() },
    ) { Mehr() }

    /** Die Rueckfrage vor dem Entfernen - die Karte ueber dem dunklen Bildschirm. */
    @Test
    fun rueckfrageEntfernen() = zeige(
        "11_rueckfrage_entfernen",
        auchGross = true,
        wartenMs = 700,
        davor = { tippeZeile("Wallet von diesem Gerät entfernen") },
    ) { Mehr() }

    /** "PIN ändern", erste von drei Eingaben, zwei Ziffern getippt. */
    @Test
    fun pinAendern() = zeige(
        "11_pin_aendern",
        auchGross = true,
        wartenMs = 700,
        davor = {
            tippeZeile("PIN ändern")
            zweiZiffern()
        },
    ) { Mehr() }

    /** Die PIN-Abfrage vor den zwoelf Woertern, mit dem Satz und dem Finger. */
    @Test
    fun woerterPin() = zeige(
        "11_woerter_pin",
        auchGross = true,
        wartenMs = 700,
        davor = { tippeZeile("Zwölf Wörter anzeigen") },
    ) { Mehr() }

    // ------------------------------------------- Die Seiten mit den Woertern
    //
    // In der App liegt auf diesen vier Seiten FLAG_SECURE; ein Foto davon ist
    // schwarz. Gezeichnet wird hier darum das `…Bild` OHNE den Schutz, mit
    // dem OEFFENTLICHEN BIP39-Testvektor "abandon … about" (REGELN.md,
    // Regel 3) - dahinter liegt kein Guthaben. Dass die Screens der App das
    // Fenster wirklich sperren, prueft `SchutzTest`.

    private val testwoerter = List(11) { "abandon" } + "about"

    @Test
    fun woerterZeigen() = zeige("17_woerter_zeigen", auchGross = true) {
        WoerterZeigenBild(woerter = testwoerter, onWeiter = {}, onAbbrechen = {})
    }

    @Test
    fun gegenprobe() = zeige("18_gegenprobe", auchGross = true) {
        WoerterPruefenBild(
            positionen = listOf(2, 6, 10),
            fehler = null,
            onPruefen = {},
            onZurueck = {},
        )
    }

    @Test
    fun gegenprobeFalsch() = zeige("18_gegenprobe_falsch") {
        WoerterPruefenBild(
            positionen = listOf(2, 6, 10),
            fehler = "Das stimmt noch nicht. Sieh noch einmal nach.",
            onPruefen = {},
            onZurueck = {},
        )
    }

    @Test
    fun wiederherstellen() = zeige("19_wiederherstellen", auchGross = true) {
        WiederherstellenBild(onUebernehmen = { null }, onAbbrechen = {})
    }

    /**
     * Alle zwoelf Woerter in das erste Feld eingefuegt: Sie verteilen sich
     * auf die zwoelf Felder. Der oeffentliche Testvektor, kein Guthaben.
     */
    @Test
    fun wiederherstellenGefuellt() = zeige(
        "19_wiederherstellen_gefuellt",
        auchGross = true,
        wartenMs = 500,
        davor = {
            rolle.onNodeWithTag("merkwort1").performTextInput(testwoerter.joinToString(" "))
        },
    ) {
        WiederherstellenBild(
            onUebernehmen = { "Die Wörter passen nicht zusammen. Eines ist falsch oder vertauscht." },
            onAbbrechen = {},
        )
    }

    /**
     * Vierzehn Woerter eingefuegt: Zwoelf stehen da, und die Seite sagt, dass
     * zwei keinen Platz gefunden haben.
     */
    @Test
    fun wiederherstellenZuViele() = zeige(
        "19_wiederherstellen_zu_viele",
        wartenMs = 500,
        davor = {
            rolle.onNodeWithTag("merkwort1")
                .performTextInput((testwoerter + "abandon" + "about").joinToString(" "))
        },
    ) {
        WiederherstellenBild(onUebernehmen = { null }, onAbbrechen = {})
    }

    @Test
    fun woerterAnsehen() = zeige("20_woerter_ansehen", auchGross = true) {
        WoerterAnsehenBild(woerter = testwoerter, onZurueck = {})
    }

    // ----------------------------------------------------------- Selbsttest
    //
    // Gezeichnet mit Beispielberichten. Der echte Selbsttest spricht mit dem
    // Spiegel und legt einen Testtresor an - beides gehoert nicht in ein Bild
    // (REGELN.md, Regel 18).

    private val berichtKern = Selbsttest.Bericht(
        zeilen = listOf(
            Selbsttest.Zeile(true, "Adresse aus den Merkwörtern", adresse),
            Selbsttest.Zeile(true, "Kennung der Testüberweisung", "76f239b4b9b8644e…9ceab797"),
            Selbsttest.Zeile(true, "Eigene Signatur gültig", "ja"),
        ),
        dauerMs = 412,
    )

    private val berichtTresor = Selbsttest.Bericht(
        zeilen = listOf(
            Selbsttest.Zeile(true, "Richtige PIN öffnet", "ja"),
            Selbsttest.Zeile(true, "Falsche PIN öffnet nicht", "ja"),
        ),
        dauerMs = 4_180,
    )

    @Test
    fun selbsttest() = zeige("21_selbsttest", auchGross = true) {
        SelbsttestBild(
            kern = berichtKern,
            tresor = berichtTresor,
            netz = null,
            bio = null,
            bioLaeuft = false,
            onBio = {},
            onZurueck = {},
        )
    }

    @Test
    fun selbsttestFehler() = zeige("21_selbsttest_fehler") {
        SelbsttestBild(
            kern = berichtKern,
            tresor = berichtTresor,
            netz = Selbsttest.Bericht(
                zeilen = listOf(
                    Selbsttest.Zeile(true, "Spiegel antwortet", "200"),
                    Selbsttest.Zeile(false, "Knoten erreichbar", "Zeitüberschreitung nach 10 s"),
                ),
                dauerMs = 10_240,
            ),
            bio = null,
            bioLaeuft = true,
            onBio = {},
            onZurueck = {},
        )
    }

    // ----------------------------------------------------------- Einfuehrung
    //
    // Jede der acht Karten einzeln, normal und mit Systemschrift 1,3. Die
    // Adresse auf Karte 2 ist die Beispieladresse dieses Testrahmens - in
    // der App steht dort die eigene.

    private fun einfuehrung(karte: Int) = zeige(
        "22_einfuehrung_$karte",
        auchGross = true,
        // Das Foto wird beim ersten Zeichnen geladen.
        wartenMs = 400,
    ) {
        EinfuehrungScreen(adresse = adresse, onFertig = {}, start = karte - 1)
    }

    @Test fun einfuehrung1() = einfuehrung(1)
    @Test fun einfuehrung2() = einfuehrung(2)
    @Test fun einfuehrung3() = einfuehrung(3)
    @Test fun einfuehrung4() = einfuehrung(4)
    @Test fun einfuehrung5() = einfuehrung(5)
    @Test fun einfuehrung6() = einfuehrung(6)
    @Test fun einfuehrung7() = einfuehrung(7)
    @Test fun einfuehrung8() = einfuehrung(8)

    // ------------------------------------------- Anmelden und Signieren (23/24)
    //
    // Jeder Schritt einzeln, mit fester Uhr und Beispielwerten. Nichts davon
    // oeffnet einen Tresor, unterschreibt oder sendet: Die Rueckrufe sind leer.

    private val sigUhr = java.time.Instant.parse("2026-11-09T12:31:00Z").toEpochMilli()

    /** Eine Anmeldung fuer die Beispieladresse, gueltig noch vier Minuten nach [sigUhr]. */
    private val anmeldeNachricht = listOf(
        "YSKAR Signed Message", "purpose: login", "domain: lab.yskar.app", "address: $adresse",
        "nonce: 9f2c4e1ab37d4c6e8a05b1d2e3f4a5b6", "issued: 2026-11-09T12:30:00Z",
        "expires: 2026-11-09T12:35:00Z",
    ).joinToString("\n")

    private fun anmeldeCode(rueckruf: String = "https://lab.yskar.app/api/auth/wallet") =
        "yskar:login?msg=${Nachricht.textBase64url(anmeldeNachricht)}&cb=${Nachricht.textBase64url(rueckruf)}"

    @Composable
    private fun Signaturbild(anfang: Signierschritt, start: Signierstart = Signierstart.Eingabe) {
        SignierenScreen(
            start = start,
            adresse = adresse,
            bioEingeschaltet = true,
            onSignieren = { _, _, _, _, _ -> },
            onSignierenBio = { _, _, _, _, _ -> },
            onZurueck = {},
            anfang = anfang,
            uhrFest = sigUhr,
        )
    }

    /** Die Anfrage einer YSKAR-Seite: wo, mit welcher Adresse, bis wann, wohin die Antwort geht. */
    @Test
    fun anmeldenAnsehen() = zeige("23_anmelden_ansehen", auchGross = true) {
        Signaturbild(Signierschritt.Ansehen(anmeldeCode(), istCode = true))
    }

    /** Bestaetigen: Ueber dem Ziffernfeld steht, wo man sich anmeldet. */
    @Test
    fun anmeldenPin() = zeige("23_anmelden_pin", wartenMs = 300, davor = { zweiZiffern() }) {
        Signaturbild(Signierschritt.Bestaetigen(anmeldeCode(), istCode = true))
    }

    @Test
    fun anmeldenSendet() = zeige("23_anmelden_sendet") {
        Signaturbild(Signierschritt.Sendet("lab.yskar.app"))
    }

    @Test
    fun angemeldet() = zeige("23_angemeldet") {
        Signaturbild(Signierschritt.Angemeldet("lab.yskar.app"))
    }

    /** Die Seite hat abgelehnt und sagt, warum - ihr Satz steht als Zitat da. */
    @Test
    fun anmeldungNichtAngenommen() = zeige("23_nicht_angenommen") {
        Signaturbild(
            Signierschritt.NichtAngenommen(
                "lab.yskar.app",
                Rueckruf.Ergebnis.Abgelehnt("This sign-in code was already used.", 409),
            ),
        )
    }

    /** Ein Code, dessen Antwort woandershin ginge: abgelehnt, mit beiden Namen. */
    @Test
    fun anmeldecodeAbgelehnt() = zeige("23_code_abgelehnt") {
        Signaturbild(Signierschritt.Ansehen(anmeldeCode("https://evil.example/x"), istCode = true))
    }

    @Test
    fun signierenEingabe() = zeige("24_signieren_eingabe") {
        Signaturbild(Signierschritt.Eingabe())
    }

    /** Freier Text: Die Wallet zeigt, was sie davorgesetzt hat. */
    @Test
    fun signierenAnsehen() = zeige("24_signieren_ansehen", auchGross = true) {
        Signaturbild(Signierschritt.Ansehen("Ich bin es wirklich.\nGruß, Anna", istCode = false))
    }

    /** Die fertige Signatur. Schluessel und Signatur sind erfundene Hex-Werte. */
    @Test
    fun signiert() = zeige("24_signiert") {
        Signaturbild(
            Signierschritt.Signiert(
                Nachricht.Signiert(
                    address = adresse,
                    publicKey = "34792b244cfe851d8b3fc62ccb3033297ea2ca5bc31119b397bc3ec4132a751d",
                    signature = "76f239b4b9b8644e068334e7bf07d01744b8a894b66892b950f3058d9ceab797" +
                        "aaa2fded1b8ca1d782360bdc924566db8ce69b9dd7a9eb26e3fcd702c6334368",
                    message = "YSKAR Signed Message\npurpose: text\n\nIch bin es wirklich.\nGruß, Anna",
                ),
            ),
        )
    }
}
