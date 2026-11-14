package com.yskar.wallet.ui

import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.yskar.wallet.R
import com.yskar.wallet.sprache.Sprache

/**
 * Die Einstellungen der Wallet.
 *
 * EIN EINSTELLUNGSFENSTER, WIE MAN ES KENNT (Wunsch des Besitzers,
 * 11.11.2026): eine Ueberschrift je Gruppe, darunter eine Flaeche mit
 * Zeilen. Jede Zeile hat links ihr Symbol, daneben Name und - wo es etwas zu
 * sagen gibt - einen Satz, und rechts genau eines: einen Schalter, einen
 * Wert oder den Pfeil zur naechsten Seite. Was eine Gruppe als Ganzes
 * erklaert, steht als Fussnote unter ihrer Flaeche.
 *
 * Vorher hiess der Bildschirm "Mehr" und bestand aus fuenf Karten mit
 * Knoepfen darin (Vorlage design-neu/html/11_einstellungen.html). WAS die
 * Zeilen tun, ist unveraendert - neu sind nur die Sprache, "Guthaben
 * verbergen" (bisher allein das Auge auf dem Hauptbildschirm) und die Zeile
 * "Automatisch sperren", die nichts schaltet, sondern sagt, was gilt.
 *
 * Hier steht auch der einzige Weg, die Wallet vom Geraet zu entfernen - in
 * einer eigenen, rot getoenten Flaeche am Ende, mit einer Rueckfrage, die
 * nicht beschoenigt.
 *
 * Der Bildschirm ist hoeher als das Fenster und rollt; das darf er
 * (REGELN.md, Regel 29 gilt den Sendebildschirmen).
 */
@Composable
fun EinstellungenScreen(
    adresse: String?,
    bioMoeglich: Boolean,
    bioEingeschaltet: Boolean,
    benachrichtigungen: Boolean,
    vibration: Boolean,
    hinweis: String?,
    fehler: String?,
    onBioEin: (CharArray) -> Unit,
    onBioAus: () -> Unit,
    onPinAendern: (CharArray, CharArray, (String?) -> Unit) -> Unit,
    onBenachrichtigungen: (Boolean) -> Unit,
    onVibration: (Boolean) -> Unit,
    onWoerterZeigen: (CharArray) -> Unit,
    onWoerterZeigenBio: () -> Unit,
    onSelbsttest: () -> Unit,
    onLoeschen: () -> Unit,
    onEinfuehrung: () -> Unit = {},
    onZurueck: () -> Unit,
    modifier: Modifier = Modifier,
    verborgen: Boolean = false,
    onVerbergen: (Boolean) -> Unit = {},
    sprache: Sprache = LocalTexte.current.sprache,
    onSprache: (Sprache) -> Unit = {},
    onAnmelden: () -> Unit = {},
    onSignieren: () -> Unit = {},
    kontaktAnzahl: Int = 0,
    onKontakte: () -> Unit = {},
) {
    val t = LocalTexte.current.einstellungen
    var pinSchritt by remember { mutableStateOf<PinSchritt?>(null) }
    var loeschFrage by remember { mutableStateOf(false) }
    var sprachwahl by remember { mutableStateOf(false) }
    val ctx = LocalContext.current

    if (pinSchritt != null) {
        PinAbfrage(
            schritt = pinSchritt!!,
            bioEingeschaltet = bioEingeschaltet,
            onAbbrechen = { pinSchritt = null },
            onFertig = { pinSchritt = null },
            onBioEin = onBioEin,
            onPinAendern = onPinAendern,
            onWoerterZeigen = onWoerterZeigen,
            onWoerterZeigenBio = onWoerterZeigenBio,
            modifier = modifier,
        )
        return
    }

    if (sprachwahl) {
        Sprachseite(
            gewaehlt = sprache,
            onWahl = onSprache,
            onZurueck = { sprachwahl = false },
            modifier = modifier,
        )
        return
    }

    // Die Nummer der Fassung, wie sie im Paket steht. Im Testrahmen und auf
    // dem Geraet dieselbe Quelle.
    val fassung = remember(ctx) {
        runCatching {
            ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName
        }.getOrNull().orEmpty()
    }

    Box(modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                // Solange die Rueckfrage darueber liegt, gibt es fuer einen
                // Bildschirmleser nur die Rueckfrage.
                .then(if (loeschFrage) Modifier.clearAndSetSemantics { } else Modifier),
        ) {
            Kopfzeile(t.titel, onZurueck = onZurueck)

            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 16.dp, top = 2.dp, end = 16.dp, bottom = 28.dp),
            ) {
                // Was das Modell zu sagen hat - auch "PIN geaendert." kommt von
                // dort, in der Sprache, die gerade gilt.
                if (fehler != null) {
                    Warnhinweis(fehler, Modifier.padding(bottom = 6.dp))
                } else if (hinweis != null) {
                    Hinweisstreifen(hinweis, Modifier.padding(bottom = 6.dp))
                }

                // ------------------------------------------------ Sicherheit
                Gruppentitel(t.sicherheit)
                Gruppe {
                    Schalterzeile(
                        symbol = R.drawable.ic_fingerprint_bold,
                        titel = t.fingerabdruck,
                        hinweis = if (bioMoeglich) t.fingerabdruckHinweis else t.fingerabdruckFehlt,
                        an = bioEingeschaltet,
                        aktiv = bioMoeglich,
                        onWechsel = { an ->
                            if (an) pinSchritt = PinSchritt.BioEin else onBioAus()
                        },
                    )
                    Trennlinie()
                    Pfeilzeile(
                        symbol = R.drawable.ic_password_fill,
                        titel = t.pinAendern,
                        onKlick = { pinSchritt = PinSchritt.PinAlt },
                    )
                    Trennlinie()
                    Pfeilzeile(
                        symbol = R.drawable.ic_list_numbers_fill,
                        titel = t.woerterAnzeigen,
                        hinweis = t.woerterAnzeigenHinweis,
                        onKlick = { pinSchritt = PinSchritt.WoerterZeigen },
                    )
                    Trennlinie()
                    // Kein Schalter: Das Sperren beim Verlassen laesst sich
                    // nicht abstellen. Die Zeile sagt, was gilt.
                    Einstellzeile(
                        symbol = R.drawable.ic_lock_key_fill,
                        titel = t.autoSperre,
                        wert = t.autoSperreWert,
                    )
                }
                Fussnote(t.autoSperreHinweis)

                // ------------------------------------------------ Signaturen
                // Anmelden auf einer YSKAR-Seite und Nachrichten signieren
                // (Wunsch des Besitzers, 11.11.2026).
                val sig = LocalTexte.current.signatur
                Gruppentitel(sig.rubrik)
                Gruppe {
                    Pfeilzeile(
                        symbol = R.drawable.ic_qr_code_fill,
                        titel = sig.eintragQr,
                        hinweis = sig.eintragQrHinweis,
                        onKlick = onAnmelden,
                        marke = "anmelden",
                    )
                    Trennlinie()
                    Pfeilzeile(
                        symbol = R.drawable.ic_key_fill,
                        titel = sig.eintragSignieren,
                        onKlick = onSignieren,
                        marke = "signieren",
                    )
                }
                Fussnote(sig.einleitung)

                // ------------------------------------------------- Allgemein
                Gruppentitel(t.allgemein)
                Gruppe {
                    // Kontakte: Namen statt Adressen (Wunsch des Besitzers,
                    // 12.11.2026). Rechts steht, wie viele es sind.
                    Pfeilzeile(
                        symbol = R.drawable.ic_user_bold,
                        titel = LocalTexte.current.kontakte.eintrag,
                        hinweis = LocalTexte.current.kontakte.eintragHinweis,
                        wert = if (kontaktAnzahl > 0) kontaktAnzahl.toString() else null,
                        onKlick = onKontakte,
                        marke = "kontakte",
                    )
                    Trennlinie()
                    Pfeilzeile(
                        symbol = R.drawable.ic_globe_bold,
                        titel = t.sprache,
                        wert = sprache.eigenname,
                        onKlick = { sprachwahl = true },
                        marke = "sprache",
                    )
                    Trennlinie()
                    Schalterzeile(
                        symbol = R.drawable.ic_eye_fill,
                        titel = t.guthabenVerbergen,
                        hinweis = t.guthabenVerbergenHinweis,
                        an = verborgen,
                        onWechsel = onVerbergen,
                    )
                    Trennlinie()
                    Schalterzeile(
                        symbol = R.drawable.ic_vibrate_fill,
                        titel = t.vibration,
                        hinweis = t.vibrationHinweis,
                        an = vibration,
                        onWechsel = onVibration,
                    )
                }

                // ---------------------------------------- Benachrichtigungen
                Gruppentitel(t.benachrichtigungen)
                Gruppe {
                    Schalterzeile(
                        symbol = R.drawable.ic_bell_fill,
                        titel = t.imHintergrundNachsehen,
                        hinweis = if (benachrichtigungen) t.nachsehenAn else t.nachsehenAus,
                        an = benachrichtigungen,
                        onWechsel = { onBenachrichtigungen(it) },
                    )
                }
                // Der Satz, der das Versprechen der App einloest: WER fragt
                // WEN. Ohne ihn klingt "Benachrichtigung" nach einem Dienst,
                // der die Adresse kennt - und das waere eine andere Wallet.
                Fussnote(t.keinPush)

                // ---------------------------------------------- Diese Wallet
                Gruppentitel(t.dieseWallet)
                Gruppe {
                    if (adresse != null) {
                        Adresszeile(adresse, onKopieren = { inZwischenablage(ctx, adresse) })
                        Trennlinie()
                    }
                    // Die acht Karten vom Einrichten, zum Nachsehen.
                    Pfeilzeile(
                        symbol = R.drawable.ic_info_bold,
                        titel = t.einfuehrungAnsehen,
                        onKlick = onEinfuehrung,
                    )
                }

                // ------------------------------------------------- Erweitert
                // Was nicht jeder braucht, in einer eigenen Gruppe (Wunsch des
                // Besitzers, 12.11.2026: Die Wallet soll nicht technisch
                // wirken). Nichts davon ist weggefallen.
                Gruppentitel(t.erweitert)
                Gruppe {
                    // Das eigene Konto im Explorer (Wunsch des Besitzers,
                    // 11.11.2026). Die Zeile gibt es nur mit gueltiger Adresse.
                    val imExplorer = adresse?.let { Explorer.konto(it) }
                    if (imExplorer != null) {
                        Pfeilzeile(
                            symbol = R.drawable.ic_arrow_square_out_fill,
                            titel = t.explorer,
                            hinweis = t.explorerHinweis,
                            onKlick = { imBrowserOeffnen(ctx, imExplorer) },
                            marke = "im_explorer",
                        )
                        Trennlinie()
                    }
                    Einstellzeile(
                        symbol = R.drawable.ic_cube_fill,
                        titel = t.netz,
                        wert = "yskar-main-1",
                    )
                    Trennlinie()
                    Pfeilzeile(
                        symbol = R.drawable.ic_pulse_bold,
                        titel = t.selbsttest,
                        hinweis = t.selbsttestHinweis,
                        onKlick = onSelbsttest,
                    )
                }

                // ------------------------------------------------- Entfernen
                Spacer(Modifier.height(22.dp))
                Gruppe(warnung = true) {
                    Pfeilzeile(
                        symbol = R.drawable.ic_trash_fill,
                        titel = t.walletEntfernen,
                        onKlick = { loeschFrage = true },
                        warnung = true,
                    )
                }
                Fussnote(t.walletEntfernenHinweis)

                if (fassung.isNotEmpty()) {
                    Text(
                        t.version(fassung),
                        style = Schrift.Hinweis,
                        color = Farbe.Leise,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(top = 22.dp),
                    )
                }
                // Das Ende des Bildschirms, fuer den Testrahmen zum Hinrollen.
                Spacer(Modifier.height(1.dp).testTag("einstellungen_ende"))
            }
        }

        if (loeschFrage) {
            Rueckfrage(
                onJa = { loeschFrage = false; onLoeschen() },
                onNein = { loeschFrage = false },
            )
        }
    }
}

// ------------------------------------------------- Bausteine dieses Fensters

/** Die Ueberschrift ueber einer Gruppe: klein, gesperrt, leise. */
@Composable
private fun Gruppentitel(text: String) {
    Text(
        text.uppercase(LocalTexte.current.locale),
        style = Schrift.Marke,
        color = Farbe.Leise,
        modifier = Modifier
            .padding(start = 14.dp, top = 22.dp, end = 14.dp, bottom = 8.dp)
            .semantics { heading() },
    )
}

/** Die Flaeche einer Gruppe. Ihre Zeilen stehen ohne Abstand untereinander. */
@Composable
private fun Gruppe(warnung: Boolean = false, inhalt: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .then(
                if (warnung) {
                    Modifier.gefasst(
                        rundung = 22.dp,
                        grund = SolidColor(Farbe.Warnung.copy(alpha = 0.06f)),
                        rand = Farbe.Warnung.copy(alpha = 0.26f),
                        glanz = 0f,
                    )
                } else {
                    Modifier.gefasst(22.dp)
                },
            ),
        content = inhalt,
    )
}

/** Die Linie zwischen zwei Zeilen: beginnt unter dem Text, nicht unter dem Symbol. */
@Composable
private fun Trennlinie() {
    Box(
        Modifier
            .padding(start = 62.dp)
            .fillMaxWidth()
            .height(1.dp)
            .drawBehind { drawRect(Farbe.Linie) },
    )
}

/** Was eine Gruppe als Ganzes erklaert, unter ihrer Flaeche. */
@Composable
private fun Fussnote(text: String) {
    Text(
        text,
        style = Schrift.Hinweis,
        color = Farbe.Leise,
        modifier = Modifier.padding(start = 14.dp, top = 8.dp, end = 14.dp),
    )
}

/** Das Symbol einer Zeile im getoenten Kaestchen. */
@Composable
private fun Zeilensymbol(@DrawableRes symbol: Int, warnung: Boolean) {
    val ton = if (warnung) Farbe.Warnung else YskarFarben.Blau
    Box(
        Modifier
            .size(34.dp)
            .gefasst(
                rundung = 11.dp,
                grund = SolidColor(ton.copy(alpha = 0.12f)),
                rand = ton.copy(alpha = 0.28f),
                glanz = 0f,
            ),
        contentAlignment = Alignment.Center,
    ) { Symbol(symbol, 18.dp, farbe = if (warnung) Farbe.Warnung else Farbe.Schrift) }
}

/**
 * Das Geruest jeder Zeile: Symbol, Name und Satz, und rechts [ende].
 *
 * Mindestens 60 dp hoch - bei grosser Systemschrift waechst sie mit dem
 * Text, statt ihn abzuschneiden.
 */
@Composable
private fun Zeilengeruest(
    @DrawableRes symbol: Int,
    titel: String,
    modifier: Modifier = Modifier,
    hinweis: String? = null,
    warnung: Boolean = false,
    aktiv: Boolean = true,
    ende: @Composable () -> Unit = {},
) {
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .padding(start = 14.dp, top = 10.dp, end = 14.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.alpha(if (aktiv) 1f else 0.45f)) { Zeilensymbol(symbol, warnung) }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                titel,
                style = Schrift.Zeile,
                color = if (warnung) Farbe.WarnSchrift else Farbe.Schrift,
            )
            if (hinweis != null) Text(hinweis, style = Schrift.Hinweis, color = Farbe.Leise)
        }
        ende()
    }
}

/** Eine Zeile, die nur etwas sagt: Name links, Wert rechts. */
@Composable
private fun Einstellzeile(@DrawableRes symbol: Int, titel: String, wert: String) {
    Zeilengeruest(symbol, titel, Modifier.semantics(mergeDescendants = true) { }) {
        Text(wert, style = Schrift.Name, color = Farbe.Leise, textAlign = TextAlign.End)
    }
}

/** Eine Zeile, die weiterfuehrt: die ganze Zeile ist der Knopf, rechts der Pfeil. */
@Composable
private fun Pfeilzeile(
    @DrawableRes symbol: Int,
    titel: String,
    onKlick: () -> Unit,
    hinweis: String? = null,
    wert: String? = null,
    warnung: Boolean = false,
    marke: String? = null,
) {
    Zeilengeruest(
        symbol, titel,
        Modifier
            .clickable(role = Role.Button, onClick = onKlick)
            .then(if (marke != null) Modifier.testTag(marke) else Modifier),
        hinweis = hinweis,
        warnung = warnung,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (wert != null) Text(wert, style = Schrift.Name, color = Farbe.Leise)
            Symbol(
                R.drawable.ic_caret_right_bold, 14.dp,
                farbe = if (warnung) Farbe.Warnung else Farbe.Leise,
            )
        }
    }
}

/**
 * Eine Zeile mit Schalter. DIE GANZE ZEILE schaltet, nicht nur der Schalter
 * am Rand - so, wie man es von einem Einstellungsfenster erwartet. Fuer
 * einen Bildschirmleser ist sie EIN Schalter mit Name und Satz; der
 * gezeichnete Schalter darin meldet sich nicht noch einmal.
 */
@Composable
private fun Schalterzeile(
    @DrawableRes symbol: Int,
    titel: String,
    an: Boolean,
    onWechsel: (Boolean) -> Unit,
    hinweis: String? = null,
    aktiv: Boolean = true,
) {
    Zeilengeruest(
        symbol, titel,
        Modifier.toggleable(value = an, enabled = aktiv, role = Role.Switch, onValueChange = onWechsel),
        hinweis = hinweis,
        aktiv = aktiv,
    ) {
        Umschalter(an, onWechsel, titel, Modifier.clearAndSetSemantics { }, aktiv = aktiv)
    }
}

/** Die eigene Adresse, ganz und in Gruppen, mit dem Knopf zum Kopieren. */
@Composable
private fun Adresszeile(adresse: String, onKopieren: () -> Unit) {
    val t = LocalTexte.current
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 14.dp, top = 12.dp, end = 8.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Zeilensymbol(R.drawable.ic_wallet_fill, warnung = false)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(t.einstellungen.adresse, style = Schrift.Zeile, color = Farbe.Schrift)
            AdresseInGruppen(adresse)
        }
        Symbolknopf(R.drawable.ic_copy_fill, t.allgemein.adresseKopieren, onKopieren)
    }
}

// ---------------------------------------------------------------- Sprachwahl

/**
 * Die Seite "Sprache": eine Zeile je Sprache, die gewaehlte mit Haken.
 *
 * Die Wahl gilt sofort - auch diese Seite steht im naechsten Augenblick in
 * der neuen Sprache. Jede Sprache steht in ihrem EIGENEN Namen da
 * ("Deutsch", "English"): Wer die falsche erwischt hat, findet zurueck,
 * ohne ein Wort der anderen zu verstehen.
 */
@Composable
private fun Sprachseite(
    gewaehlt: Sprache,
    onWahl: (Sprache) -> Unit,
    onZurueck: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = LocalTexte.current.einstellungen
    BackHandler(onBack = onZurueck)
    Column(modifier.fillMaxSize()) {
        Kopfzeile(t.sprache, onZurueck = onZurueck)
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, top = 10.dp, end = 16.dp, bottom = 28.dp),
        ) {
            Gruppe {
                Sprache.entries.forEachIndexed { nr, sprache ->
                    if (nr > 0) Trennlinie()
                    val ist = sprache == gewaehlt
                    Zeilengeruest(
                        R.drawable.ic_globe_bold,
                        sprache.eigenname,
                        Modifier
                            .selectable(
                                selected = ist,
                                role = Role.RadioButton,
                                onClick = { onWahl(sprache) },
                            )
                            .testTag("sprache_${sprache.kennung}")
                            .semantics { if (ist) stateDescription = t.gewaehlt },
                    ) {
                        if (ist) {
                            Symbol(R.drawable.ic_check_bold, 18.dp, farbe = Farbe.BlauSchrift)
                        }
                    }
                }
            }
            Fussnote(t.spracheHinweis)
        }
    }
}

// ----------------------------------------------------------------- Rueckfrage

/**
 * "Wirklich entfernen?" - die letzte Frage vor dem Loeschen des Tresors.
 *
 * Kein Systemdialog, sondern eine Karte ueber dem abgedunkelten Bildschirm,
 * in den Formen der App. Was sie fragt und was die beiden Antworten tun, ist
 * unveraendert: "Ja, entfernen" ruft das Loeschen, alles andere -
 * "Abbrechen", die Zurueck-Taste, ein Tipp daneben - laesst die Wallet, wo
 * sie ist.
 *
 * Der rote Knopf steht OBEN und der ruhige unten, wo der Daumen liegt:
 * Wer ohne hinzusehen tippt, bricht ab.
 */
@Composable
private fun Rueckfrage(onJa: () -> Unit, onNein: () -> Unit) {
    val t = LocalTexte.current
    val titel = t.einstellungen.wirklichEntfernen
    BackHandler(onBack = onNein)
    Box(
        Modifier
            .fillMaxSize()
            // Das Dunkel reicht ueber die Raender fuer Status- und
            // Navigationsleiste hinaus. Sonst bliebe oben und unten ein heller
            // Streifen stehen, und die Karte laege auf einem Bild mit Rahmen.
            .drawBehind {
                val mehr = 400.dp.toPx()
                drawRect(
                    Color.Black.copy(alpha = 0.72f),
                    topLeft = Offset(0f, -mehr),
                    size = Size(size.width, size.height + 2 * mehr),
                )
            }
            .pointerInput(Unit) { detectTapGestures(onTap = { onNein() }) },
        contentAlignment = Alignment.Center,
    ) {
        // Rollt, falls die Karte bei sehr grosser Schrift hoeher wird als der
        // Bildschirm - "Abbrechen" darf nie unerreichbar unter dem Rand liegen.
        Flaeche(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .widthIn(max = 420.dp)
                // Ein Tipp AUF die Karte ist kein Tipp daneben.
                .pointerInput(Unit) { detectTapGestures(onTap = { }) }
                .semantics { paneTitle = titel },
            rundung = 28.dp,
            innen = PaddingValues(start = 20.dp, top = 22.dp, end = 20.dp, bottom = 20.dp),
            abstand = 0.dp,
            grund = SolidColor(Farbe.Tafel),
        ) {
            Box(
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .size(52.dp)
                    .gefasst(
                        rundung = 18.dp,
                        grund = SolidColor(Farbe.Warnung.copy(alpha = 0.10f)),
                        rand = Farbe.Warnung.copy(alpha = 0.30f),
                        glanz = 0f,
                    ),
                contentAlignment = Alignment.Center,
            ) { Symbol(R.drawable.ic_warning_fill, 26.dp, farbe = Farbe.Warnung) }

            Text(
                titel,
                style = Schrift.Mittel,
                color = Farbe.Schrift,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            )
            Text(
                t.einstellungen.wirklichEntfernenText,
                style = Schrift.Text,
                color = Farbe.Weich,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )

            Spacer(Modifier.height(20.dp))
            Knopf(
                t.einstellungen.jaEntfernen,
                onJa,
                Modifier.fillMaxWidth(),
                art = Knopfart.WARNUNG,
                symbol = R.drawable.ic_trash_fill,
            )
            Spacer(Modifier.height(10.dp))
            Knopf(t.allgemein.abbrechen, onNein, Modifier.fillMaxWidth(), art = Knopfart.FLAECHE)
        }
    }
}

// --------------------------------------------------------------- PIN-Abfragen

enum class PinSchritt { BioEin, PinAlt, WoerterZeigen }

/**
 * Die PIN-Abfragen der Einstellungen: Fingerabdruck einschalten, PIN
 * aendern, zwoelf Woerter anzeigen.
 *
 * Gebaut wie "PIN festlegen" (Vorlage 02): Kreuz oben links, das Zeichen,
 * das sich mit den Ziffern fuellt, die Ueberschrift, EIN Satz darunter und
 * das eigene Ziffernfeld (REGELN.md, Regel 15). Beim Aendern der PIN zeigen
 * die Punkte oben rechts, welche der drei Eingaben gerade dran ist.
 *
 * Was eingegeben wird und wohin es geht, ist unveraendert.
 */
@Composable
private fun PinAbfrage(
    schritt: PinSchritt,
    bioEingeschaltet: Boolean,
    onAbbrechen: () -> Unit,
    onFertig: () -> Unit,
    onBioEin: (CharArray) -> Unit,
    onPinAendern: (CharArray, CharArray, (String?) -> Unit) -> Unit,
    onWoerterZeigen: (CharArray) -> Unit,
    onWoerterZeigenBio: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val texte = LocalTexte.current
    val t = texte.einstellungen
    val laenge = 6
    var ziffern by remember { mutableStateOf("") }
    var alte by remember { mutableStateOf<String?>(null) }
    var neue by remember { mutableStateOf<String?>(null) }
    var fehler by remember { mutableStateOf<String?>(null) }
    var laeuft by remember { mutableStateOf(false) }

    // Die Zurueck-Taste bricht die Abfrage ab, wie das Kreuz oben links -
    // statt die Einstellungen ganz zu verlassen. Solange gerechnet wird,
    // tut sie nichts, wie das Kreuz.
    BackHandler { if (!laeuft) onAbbrechen() }

    val titel = when {
        schritt == PinSchritt.BioEin -> t.pinBestaetigen
        schritt == PinSchritt.WoerterZeigen -> t.pinEingeben
        alte == null -> t.bisherigePin
        neue == null -> t.neuePin
        else -> t.neuePinWiederholen
    }
    val erklaerung = when (schritt) {
        PinSchritt.BioEin -> t.bioErklaerung
        PinSchritt.WoerterZeigen -> t.woerterErklaerung
        PinSchritt.PinAlt -> null
    }

    // Reine Anzeige, wie auf der Sperre: je zwei Ziffern ein Teil des
    // Zeichens. Was eingegeben wurde, weiss das Zeichen nicht - nur wie viel.
    val baender = baenderFuerPin(
        ziffern = ziffern.length,
        falsch = fehler != null,
        geprueft = laeuft,
    )

    Box(modifier.fillMaxSize()) {
        Musterhimmel(hoehe = 330.dp, deckkraft = 0.07f)

        Blatt(mittig = true) {
            Kopfzeile(
                titel = "",
                onZurueck = onAbbrechen,
                zurueckSymbol = R.drawable.ic_x_bold,
                zurueckText = texte.allgemein.abbrechen,
                zurueckAktiv = !laeuft,
            ) {
                if (schritt == PinSchritt.PinAlt) {
                    Schrittpunkte(
                        schritt = when {
                            alte == null -> 1
                            neue == null -> 2
                            else -> 3
                        },
                        von = 3,
                    )
                }
            }

            Spacer(Modifier.weight(0.55f))

            Zeichen(
                links = baender[0],
                rechts = baender[1],
                stamm = baender[2],
                groesse = 96.dp,
                beschreibung = zifferStand(ziffern.length, laenge, texte),
            )
            Text(
                titel,
                style = Schrift.Gross,
                color = Farbe.Schrift,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(start = 24.dp, top = 18.dp, end = 24.dp),
            )
            // Die Meldung steht dort, wo sonst der erklaerende Satz steht -
            // an der Stelle, auf die man gerade schaut.
            val satz = fehler ?: if (laeuft) texte.allgemein.einenMoment else erklaerung
            if (satz != null) {
                Text(
                    satz,
                    style = Schrift.Name,
                    color = if (fehler != null) Farbe.Warnung else Farbe.Leise,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(start = 28.dp, top = 6.dp, end = 28.dp),
                )
            }

            Spacer(Modifier.height(30.dp))

            Ziffernfeld(
                laenge = laenge,
                ziffern = ziffern,
                aktiv = !laeuft,
                // Vor den zwoelf Woertern geht es auch mit dem Finger - als
                // Taste des Feldes, wie auf der Sperre.
                onFinger = if (schritt == PinSchritt.WoerterZeigen && bioEingeschaltet) {
                    { laeuft = true; onWoerterZeigenBio(); onFertig() }
                } else null,
                fingerAktiv = !laeuft,
                onZiffern = { neu ->
                    fehler = null
                    ziffern = neu
                    if (neu.length != laenge) return@Ziffernfeld

                    when {
                        schritt == PinSchritt.BioEin -> {
                            laeuft = true
                            onBioEin(neu.toCharArray())
                            onFertig()
                        }

                        schritt == PinSchritt.WoerterZeigen -> {
                            laeuft = true
                            // Das Modell wechselt die Ansicht; hier nur aufraeumen.
                            onWoerterZeigen(neu.toCharArray())
                            onFertig()
                        }

                        alte == null -> {
                            alte = neu
                            ziffern = ""
                        }

                        neue == null -> {
                            neue = neu
                            ziffern = ""
                        }

                        else -> {
                            if (neu != neue) {
                                fehler = t.eingabenUngleich
                                neue = null
                                ziffern = ""
                            } else {
                                laeuft = true
                                onPinAendern(
                                    alte!!.toCharArray(),
                                    neu.toCharArray(),
                                ) { problem ->
                                    laeuft = false
                                    if (problem == null) {
                                        onFertig()
                                    } else {
                                        fehler = problem
                                        alte = null
                                        neue = null
                                        ziffern = ""
                                    }
                                }
                            }
                        }
                    }
                },
            )

            Spacer(Modifier.height(16.dp))
            Spacer(Modifier.weight(1f))

            Spacer(Modifier.height(26.dp))
        }
    }
}
