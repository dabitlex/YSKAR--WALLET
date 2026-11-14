package com.yskar.wallet.ui

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.yskar.wallet.R
import com.yskar.wallet.kontakte.Kontakt
import com.yskar.wallet.kontakte.Kontakte
import com.yskar.wallet.netz.Betrag
import com.yskar.wallet.netz.Notiz
import com.yskar.wallet.netz.Senden
import com.yskar.wallet.netz.Sendestatus
import com.yskar.wallet.sprache.Sendemeldungen
import com.yskar.wallet.sprache.Sprache
import com.yskar.wallet.sprache.Texte
import kotlinx.coroutines.delay
import java.time.ZoneId

/**
 * Senden - in vier Schritten.
 *
 *   1 Eingabe        Empfänger, Betrag, Notiz
 *   2 Prüfschritt    alles im Klartext, nichts abgekürzt
 *   3 PIN            erst hier wird der Schlüssel angefasst
 *   4 Ergebnis       und zwar ehrlich: "angenommen" ist nicht "erledigt"
 *
 * Der Prüfschritt ist nicht zu überspringen, und was dort steht, ist
 * wörtlich das, was unterschrieben wird - der Entwurf wandert unverändert
 * weiter.
 *
 * Vorlagen: design-neu/html/08_senden_eingabe, 09_senden_pruefen,
 * 16_senden_pin, 10_senden_fertig und 13_senden_fertig_bestaetigt.
 * GEAENDERT IST DAS AUSSEHEN. Was eingegeben, geprueft, angezeigt und
 * weitergereicht wird, ist dasselbe wie vorher.
 */

/**
 * Der Rahmen der Sendebildschirme: Kopf fest, Fuss fest, die Mitte rollt.
 *
 * DER ROLLBALKEN BLEIBT, obwohl die Seiten hineinpassen. Sie passen auf
 * DIESES Geraet, bei DIESER Schriftgroesse. Wer die Systemschrift
 * vergroessert oder ein kleineres Telefon hat, braucht ihn - und ohne ihn
 * waere der Knopf "Unterschreiben und senden" unerreichbar. In einer
 * Wallet ist das keine Schoenheitsfrage, sondern eine Falle. Ziel ist,
 * dass niemand rollen MUSS; nicht, dass niemand rollen KANN.
 *
 * Nur die MITTE rollt. Damit sitzt der Hauptknopf unten am Daumen statt in
 * der Bildschirmmitte, auch wenn der Inhalt darueber kurz ist - und er
 * bleibt erreichbar, wenn der Inhalt einmal lang wird.
 */
@Composable
private fun Seite(
    titel: String,
    onZurueck: () -> Unit,
    modifier: Modifier = Modifier,
    schritt: Int? = null,
    zurueckAktiv: Boolean = true,
    fuss: @Composable ColumnScope.() -> Unit = {},
    inhalt: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.fillMaxSize()) {
        Kopfzeile(titel, onZurueck = onZurueck, zurueckAktiv = zurueckAktiv) {
            if (schritt != null) Schrittpunkte(schritt)
        }
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            content = inhalt,
        )
        Column(Modifier.fillMaxWidth(), content = fuss)
    }
}

@Composable
fun SendenEingabeScreen(
    guthaben: Long?,
    fehler: String?,
    onPruefen: (String, String, String) -> Unit,
    onZurueck: () -> Unit,
    modifier: Modifier = Modifier,
    // Die Kontakte des Besitzers. Gibt es welche, steht am Adressfeld der
    // Weg zur Auswahl; und hat die Adresse im Feld einen Namen, steht er da.
    kontakte: List<Kontakt> = emptyList(),
) {
    val ctx = LocalContext.current
    val t = LocalTexte.current
    var empfaenger by remember { mutableStateOf("") }
    var betrag by remember { mutableStateOf("") }
    var notiz by remember { mutableStateOf("") }

    // Die Notiz ist eingeklappt, bis jemand eine will.
    var notizOffen by remember { mutableStateOf(false) }

    val notizBytes = remember(notiz) { Notiz.bytes(notiz) }
    var scannen by remember { mutableStateOf(false) }
    var waehlen by remember { mutableStateOf(false) }

    // Der Scanner legt sich ueber DIESEN Bildschirm, statt ein eigener
    // Schritt der Navigation zu sein. Damit bleiben Betrag und Notiz
    // stehen, waehrend die Adresse geholt wird - sonst waere das Scannen
    // eine Falle fuer alle, die den Betrag zuerst eintippen.
    if (scannen) {
        ScannerScreen(
            onGefunden = { gefunden ->
                empfaenger = gefunden
                scannen = false
            },
            onAbbrechen = { scannen = false },
            modifier = modifier,
        )
        return
    }

    // Die Auswahl eines Kontakts ebenso: ueber diesem Bildschirm, damit
    // Betrag und Notiz stehen bleiben. Gewaehlt wird die ADRESSE; sie steht
    // danach im Feld wie eine getippte und im Pruefschritt vollstaendig da.
    if (waehlen) {
        KontaktAuswahl(
            kontakte,
            onGewaehlt = {
                empfaenger = it.adresse
                waehlen = false
            },
            onAbbrechen = { waehlen = false },
            modifier = modifier,
        )
        return
    }

    Seite(
        t.senden.eingabeTitel,
        onZurueck,
        modifier,
        schritt = 1,
        fuss = {
            Knopf(
                t.senden.weiterZumPruefen,
                { onPruefen(empfaenger, betrag, notiz) },
                Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 22.dp)
                    .testTag("pruefen"),
                aktiv = empfaenger.isNotBlank() && betrag.isNotBlank(),
            )
        },
    ) {
        if (fehler != null) {
            Warnhinweis(fehler, Modifier.padding(start = 16.dp, top = 4.dp, end = 16.dp))
        }

        // --- Betrag -----------------------------------------------------
        // Der Betrag ist die Hauptfigur dieses Bildschirms: FREI und GROSS,
        // ohne Karte, in der Mitte - wie das Guthaben auf dem
        // Hauptbildschirm (Wunsch des Besitzers, 11.11.2026). Die Einheit
        // steht daneben, damit beim Tippen sichtbar bleibt, worin gerechnet
        // wird; darunter, wie viel da ist.
        //
        // Ein Tipp irgendwo in diese Flaeche setzt die Schreibmarke in den
        // Betrag - das Feld selbst ist nur so breit wie die Zahl.
        val betragFokus = remember { FocusRequester() }
        Column(
            Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClickLabel = t.senden.betragEingeben,
                ) { betragFokus.requestFocus() }
                .padding(start = 16.dp, top = 22.dp, end = 16.dp, bottom = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(t.senden.betrag, style = Schrift.Auftakt, color = Farbe.Lampe)
            Row(
                Modifier.padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // Lange Betraege werden kleiner gesetzt, nicht abgeschnitten
                // (REGELN.md, Regel 11).
                val groesse = betragSchriftgroesse(betrag.length)
                Eingabe(
                    wert = betrag,
                    // Im Feld steht immer das Dezimalzeichen der Sprache -
                    // siehe `betragImFeld` in Zeiten.kt.
                    onWert = { betrag = betragImFeld(it, t) },
                    platzhalter = t.senden.betragPlatzhalter,
                    modifier = Modifier
                        .alignByBaseline()
                        .width(IntrinsicSize.Min)
                        .focusRequester(betragFokus)
                        .testTag("betrag"),
                    stil = Schrift.BelegBetrag.copy(
                        fontSize = groesse.sp,
                        lineHeight = (groesse * 1.15f).sp,
                    ),
                    tastatur = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                        imeAction = ImeAction.Next,
                    ),
                )
                Einheit(Modifier.alignByBaseline())
            }
            if (guthaben != null) {
                Row(
                    Modifier.padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(t.senden.verfuegbar, style = Schrift.Klein, color = Farbe.Leise)
                    Text(
                        betragText(Betrag.genau(guthaben, t.locale), Farbe.Weich, t.locale),
                        style = Schrift.Klein.copy(fontWeight = FontWeight.Medium),
                    )
                    Text(
                        "YSR",
                        style = Schrift.Marke,
                        color = Farbe.Leise,
                    )
                }
            }
        }

        Column(
            Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // --- Empfaenger -------------------------------------------------
            // Das Feld und, rechts daneben, die zwei anderen Wege zur
            // Adresse als Symbole: scannen und einfuegen. Abtippen ist der
            // dritte, und der ist das Feld selbst.
            // Hat die Adresse im Feld einen Namen, steht er als Beschriftung
            // da ("An Anna") - eine Lesehilfe; die Adresse bleibt sichtbar.
            val bekannt = Kontakte.nameFuer(kontakte, empfaenger)
            Feld(
                name = bekannt?.let { t.kontakte.an(it) } ?: t.senden.adresseDesEmpfaengers,
                rechts = if (kontakte.isEmpty()) null else {
                    {
                        Row(
                            Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .clickable(role = Role.Button) { waehlen = true }
                                .testTag("kontakt_waehlen")
                                .padding(horizontal = 6.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                        ) {
                            Symbol(R.drawable.ic_user_bold, 14.dp, farbe = Farbe.Verweis)
                            Text(
                                t.kontakte.kontaktWaehlen,
                                style = Schrift.Schild,
                                color = Farbe.Verweis,
                            )
                        }
                    }
                },
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Eingabe(
                        wert = empfaenger,
                        onWert = { empfaenger = it.trim() },
                        platzhalter = "ysr1…",
                        modifier = Modifier.weight(1f).testTag("empfaenger"),
                        stil = Schrift.Adresse.copy(fontSize = 16.sp),
                        // Eine Adresse passt nicht in eine Zeile. Sie bricht um,
                        // statt nach rechts aus dem Feld zu laufen.
                        einzeilig = false,
                        tastatur = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Next,
                        ),
                    )
                    Symbolknopf(
                        R.drawable.ic_qr_code_fill, t.senden.qrCodeScannen,
                        { scannen = true },
                        betont = true,
                    )
                    Symbolknopf(
                        R.drawable.ic_clipboard_text_fill, t.senden.einfuegen,
                        { empfaenger = ausZwischenablage(ctx).trim() },
                        betont = true,
                    )
                }
            }

            // --- Notiz ------------------------------------------------------
            // Eingeklappt, bis jemand eine will. Die meisten Ueberweisungen
            // haben keine - und eine Notiz steht fuer immer oeffentlich in der
            // Kette; das Feld soll nicht dazu einladen.
            //
            // WAS ZU SEHEN IST, WIRD GESENDET: Das Kreuz klappt nicht nur zu,
            // es LOESCHT die Notiz. Eine Notiz, die eingeklappt und unsichtbar
            // mitginge, waere eine Falle.
            if (!notizOffen) {
                Zeilenknopf(t.senden.notizHinzufuegen, { notizOffen = true })
            } else {
                val notizFokus = remember { FocusRequester() }
                LaunchedEffect(Unit) { notizFokus.requestFocus() }

                Flaeche(
                    rundung = 22.dp,
                    innen = PaddingValues(start = 16.dp, top = 4.dp, end = 6.dp, bottom = 14.dp),
                    abstand = 2.dp,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            t.senden.notiz,
                            style = Schrift.Klein,
                            color = Farbe.Leise,
                            modifier = Modifier.weight(1f),
                        )
                        Symbolknopf(
                            R.drawable.ic_x_bold, t.senden.notizEntfernen,
                            { notiz = ""; notizOffen = false },
                        )
                    }
                    Column(
                        Modifier.padding(end = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Eingabe(
                            wert = notiz,
                            onWert = { notiz = it },
                            platzhalter = t.senden.notizPlatzhalter,
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(notizFokus)
                                .testTag("notiz"),
                            tastatur = KeyboardOptions(imeAction = ImeAction.Done),
                        )
                        // Der Zaehler zaehlt, was der Kern prueft: Bytes in
                        // UTF-8, nicht Zeichen. Ein Umlaut kostet zwei. Ist
                        // die Notiz zu lang, wird die Zahl rot - abgelehnt
                        // wird sie dann ohnehin im Pruefschritt, hier sieht
                        // man es vorher.
                        FlowRow(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                t.senden.notizFuerImmer,
                                style = Schrift.Hinweis.copy(fontSize = 12.sp),
                                color = Farbe.Leise,
                            )
                            Text(
                                t.senden.notizBytes(notizBytes, Notiz.BYTES),
                                style = Schrift.Hinweis.copy(fontSize = 12.sp),
                                color = if (notizBytes > Notiz.BYTES) Farbe.Warnung else Farbe.Leise,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Ein Feld der Eingabe: Flaeche mit Beschriftung, darunter der Inhalt. */
@Composable
private fun Feld(
    name: String,
    rechts: (@Composable () -> Unit)? = null,
    inhalt: @Composable ColumnScope.() -> Unit,
) {
    Flaeche(
        rundung = 22.dp,
        innen = PaddingValues(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 14.dp),
        abstand = 6.dp,
    ) {
        if (rechts == null) {
            Text(name, style = Schrift.Klein, color = Farbe.Leise)
        } else {
            // Beschriftung links, ein Verweis rechts - auf einer Zeile, damit
            // das Feld nicht hoeher wird (REGELN.md, Regel 29).
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    name,
                    style = Schrift.Klein,
                    color = Farbe.Leise,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                rechts()
            }
        }
        inhalt()
    }
}

internal fun ausZwischenablage(ctx: Context): String {
    val cb = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    return cb.primaryClip?.getItemAt(0)?.coerceToText(ctx)?.toString() ?: ""
}

/**
 * Der Prüfschritt - ein Beleg.
 *
 * Oben der Betrag, dann die Adresse des Empfaengers vollstaendig in
 * Vierergruppen, dann Gebuehr und Summe. Was man zum Entscheiden nicht
 * braucht - "danach verfuegbar", laufende Nummer, Fassung, Groesse -, liegt
 * seit dem 12.11.2026 unter "Einzelheiten". JEDER Wert kommt unveraendert
 * aus dem [entwurf]; hier wird nichts gerechnet ausser "danach verfuegbar"
 * (REGELN.md, Regel 19).
 */
@Composable
fun SendenPruefenScreen(
    entwurf: Senden.Entwurf,
    guthaben: Long,
    onWeiter: () -> Unit,
    onZurueck: () -> Unit,
    modifier: Modifier = Modifier,
    einzelheitenOffen: Boolean = false,
    // Der Name, den der Besitzer dieser Adresse gegeben hat - oder null.
    // Er steht UEBER der Adresse, nie an ihrer Stelle: Geprueft wird die
    // Adresse (`kontakte/Kontakte.kt`).
    kontaktName: String? = null,
) {
    val t = LocalTexte.current
    // GROSSE SYSTEMSCHRIFT (REGELN.md, Regel 29): Ab hier wird der Beleg
    // knapper gesetzt, damit er ohne Rollen dasteht - die Adresse bleibt in
    // zwei Zeilen, kein Wert rutscht unter seine Beschriftung, die Zeilen
    // stehen dichter, und der grosse Betrag waechst nicht mit (er ist
    // ohnehin die groesste Schrift auf dem Bildschirm). Gekuerzt wird
    // nichts; jede Schrift bleibt groesser als bei normaler Einstellung.
    val schriftfaktor = LocalDensity.current.fontScale
    val knapp = schriftfaktor > 1.15f
    val zeilenhoehe = if (knapp) 34.dp else 42.dp
    Seite(
        t.senden.pruefenTitel,
        onZurueck,
        modifier,
        schritt = 2,
        fuss = {
            Column(
                Modifier.padding(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 22.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Ein Satz statt zwei. "Nicht rueckgaengig" und "ist weg"
                // sagten dasselbe zweimal.
                Warnhinweis(t.senden.nichtRueckgaengig)
                Knopf(
                    t.senden.unterschreibenUndSenden,
                    onWeiter,
                    Modifier.fillMaxWidth().testTag("unterschreiben"),
                )
                Text(
                    t.senden.imNaechstenSchritt,
                    style = Schrift.Hinweis.copy(fontSize = 12.sp),
                    color = Farbe.Leise,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
    ) {
        Beleg(Modifier.padding(start = 2.dp, top = 2.dp, end = 2.dp)) {
            Kartenkopf(Modifier.fillMaxWidth(), gefasst = false) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = if (knapp) 112.dp else 132.dp)
                        .padding(horizontal = 20.dp, vertical = if (knapp) 12.dp else 18.dp),
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            t.senden.duSendest,
                            style = Schrift.Auftakt,
                            color = Farbe.Lampe,
                            modifier = Modifier.weight(1f),
                        )
                        ZeichenRuhig(groesse = 26.dp)
                    }
                    GuthabenZahl(
                        entwurf.betrag,
                        Modifier.fillMaxWidth().padding(top = if (knapp) 6.dp else 10.dp),
                        gross = if (knapp) {
                            Schrift.BelegBetrag.copy(fontSize = Schrift.BelegBetrag.fontSize / schriftfaktor)
                        } else Schrift.BelegBetrag,
                        klein = null,
                    )
                }
            }

            Abrisskante()
            Column(
                Modifier.padding(
                    start = 20.dp, top = if (knapp) 10.dp else 16.dp,
                    end = 20.dp, bottom = if (knapp) 12.dp else 18.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(if (knapp) 6.dp else 10.dp),
            ) {
                FlowRow(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    if (kontaktName != null) {
                        Text(
                            t.kontakte.an(kontaktName),
                            style = Schrift.Zeile,
                            color = Farbe.Schrift,
                            modifier = Modifier.testTag("pruefen_kontakt"),
                        )
                    } else {
                        Text(t.senden.empfaenger, style = Schrift.Klein, color = Farbe.Leise)
                    }
                    // Die Anweisung steht an der Adresse, nicht irgendwo
                    // darueber: Hier wird verglichen.
                    Text(
                        t.senden.zeichenVergleichen,
                        style = Schrift.Hinweis.copy(fontSize = 12.sp),
                        color = YskarFarben.BlauHell,
                    )
                }
                AdresseInGruppen(
                    entwurf.empfaenger,
                    Modifier.fillMaxWidth(),
                    stil = Schrift.Adresse,
                    luecke = if (knapp) 8.dp else 10.dp,
                    einpassen = knapp,
                )
            }

            Abrisskante()
            Column(Modifier.padding(start = 20.dp, top = 4.dp, end = 20.dp, bottom = 2.dp)) {
                // Der Betrag steht gross im Kopf; hier folgen Gebuehr und
                // Summe. Betrag.genau() zeigt jede Stelle bis zur letzten der
                // Kette und haengt keine ueberfluessige Null an.
                Wertzeile(
                    t.senden.gebuehr, Betrag.genau(entwurf.gebuehr, t.locale),
                    einheit = "YSR", hoehe = zeilenhoehe, klein = knapp, einpassen = knapp,
                )
                Wertzeile(
                    t.senden.gesamt, Betrag.genau(entwurf.gesamt, t.locale),
                    einheit = "YSR", betont = true, hoehe = zeilenhoehe, einpassen = knapp,
                    linie = entwurf.notizText.isNotEmpty(),
                )
                if (entwurf.notizText.isNotEmpty()) {
                    Wertzeile(
                        t.senden.notizInDerKette,
                        t.senden.notizWoertlich(entwurf.notizText),
                        linie = false,
                    )
                }
            }

            Abrisskante()
            Einzelheiten(offen = einzelheitenOffen) {
                Column(Modifier.padding(horizontal = 20.dp)) {
                    Wertzeile(
                        t.senden.danachVerfuegbar,
                        Betrag.genau(guthaben - entwurf.gesamt, t.locale),
                        einheit = "YSR",
                        linie = false,
                        hoehe = zeilenhoehe, klein = knapp, einpassen = knapp,
                    )
                }
                // ABGEKUERZT IST NUR DAS WORT, nicht der Wert: "Nr." ist die
                // laufende Nummer, und alle drei Angaben stehen vollstaendig da.
                BelegFuss(
                    t.senden.laufendeNummer(entwurf.nonce),
                    t.senden.fassung(entwurf.fassung),
                    t.senden.bytes(entwurf.bytes),
                )
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}

/**
 * Der Augenblick, in dem der Schluessel angefasst wird.
 *
 * WAS HIER STEHT UND WARUM: Betrag, Gebuehr, Gesamt und der Empfaenger -
 * noch einmal, obwohl der Pruefschritt sie gerade gezeigt hat. Das ist
 * keine Dopplung aus Versehen. Wer die PIN eintippt, unterschreibt; der
 * Bildschirm, auf dem unterschrieben wird, muss sagen, WAS unterschrieben
 * wird. Ein Ziffernfeld ohne Zahlen daneben ist eine Blankounterschrift
 * (REGELN.md, Regel 27).
 *
 * Die Marke fuellt sich mit den Ziffern, genau wie auf der Sperre. Dort
 * ist es dieselbe Geste, hier derselbe Tresor - zwei verschiedene
 * Anzeigen fuer dieselbe Sache waeren Unfug.
 */
@Composable
fun SendenPinScreen(
    entwurf: Senden.Entwurf,
    laeuft: Boolean,
    fehler: String?,
    bioEingeschaltet: Boolean,
    onPin: (CharArray) -> Unit,
    onBio: () -> Unit,
    onZurueck: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = LocalTexte.current
    val laenge = 6
    var ziffern by remember { mutableStateOf("") }

    // Nach einem Fehlversuch das Feld leeren - sonst wird an eine falsche
    // Eingabe angehaengt. Dasselbe Verhalten wie auf der Sperre.
    LaunchedEffect(fehler) {
        if (fehler != null) ziffern = ""
    }

    val baender = baenderFuerPin(
        ziffern = ziffern.length,
        falsch = fehler != null,
        geprueft = laeuft,
    )

    Blatt(modifier, mittig = true) {
        Kopfzeile(
            t.senden.unterschreibenTitel, onZurueck = onZurueck, zurueckAktiv = !laeuft,
        ) {
            Schrittpunkte(3)
        }

        Beleg(Modifier.padding(start = 2.dp, top = 2.dp, end = 2.dp), rundung = 26.dp) {
            Kartenkopf(Modifier.fillMaxWidth(), gefasst = false) {
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 76.dp).padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(t.senden.gesamt, style = Schrift.Auftakt.copy(fontSize = 12.5.sp, lineHeight = 17.sp), color = Farbe.Lampe)
                        // Passt die Zahl nicht in die Breite, wird sie
                        // kleiner. Abgeschnitten wird sie nicht.
                        GuthabenZahl(
                            entwurf.gesamt,
                            Modifier.fillMaxWidth(),
                            gross = Schrift.BelegBetrag.copy(
                                fontSize = 30.sp,
                                lineHeight = 34.5.sp,
                                letterSpacing = (-0.035).em,
                            ),
                            klein = null,
                        )
                    }
                    Zeichen(
                        baender[0], baender[1], baender[2],
                        groesse = 46.dp,
                        beschreibung = zifferStand(ziffern.length, laenge, t),
                    )
                }
            }
            Column(Modifier.padding(start = 20.dp, top = 6.dp, end = 20.dp, bottom = 8.dp)) {
                Wertzeile(
                    t.senden.betrag, Betrag.genau(entwurf.betrag, t.locale), einheit = "YSR",
                    klein = true, hoehe = 30.dp, linie = false,
                )
                Wertzeile(
                    t.senden.gebuehr, Betrag.genau(entwurf.gebuehr, t.locale), einheit = "YSR",
                    klein = true, hoehe = 30.dp, linie = false,
                )
                Wertzeile(
                    t.senden.an, adresseKurz(entwurf.empfaenger),
                    klein = true, festbreite = true, hoehe = 30.dp, linie = false,
                )
            }
        }

        // Eine Zeile, drei Aufgaben - wie auf der Sperre: Aufforderung,
        // Fortgang, Fehler. Immer an derselben Stelle.
        Text(
            when {
                fehler != null -> fehler
                laeuft -> t.senden.wirdUnterschrieben
                else -> t.senden.pinEingeben
            },
            style = Schrift.Zeile,
            color = if (fehler != null) Farbe.Warnung else Farbe.Leise,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, top = 16.dp, end = 20.dp, bottom = 12.dp),
        )

        Ziffernfeld(
            laenge = laenge,
            ziffern = ziffern,
            aktiv = !laeuft,
            knapp = true,
            // Der Fingerabdruck ist eine Taste des Feldes, links neben der
            // Null - wie auf der Sperre. Er ruft dieselbe Funktion wie
            // vorher der breite Knopf am Fuss.
            onFinger = if (bioEingeschaltet) onBio else null,
            fingerBeschreibung = t.senden.mitFingerabdruckUnterschreiben,
            fingerAktiv = !laeuft,
            onZiffern = { neu ->
                ziffern = neu
                if (neu.length == laenge) {
                    onPin(neu.toCharArray())
                    ziffern = ""
                }
            },
        )

        Spacer(Modifier.height(12.dp))
        Spacer(Modifier.weight(1f))

        Text(
            t.senden.schluesselNurJetzt,
            style = Schrift.Hinweis.copy(fontSize = 12.sp),
            color = Farbe.Leise,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(start = 16.dp, top = 10.dp, end = 16.dp, bottom = 20.dp),
        )
    }
}

// ------------------------------------------------------------------- Status

/**
 * Die fuenf Schritte, wie die Vorlage sie nennt. Reihenfolge wie `Sendestatus`.
 *
 * Aus den Texten der Sprache, nicht aus `Sendestatus.namen`: Die Liste
 * dort ist deutsch und bleibt es.
 */
private fun schrittNamen(t: Texte): List<String> = listOf(
    t.senden.schrittUnterschrieben,
    t.senden.schrittAngenommen,
    t.senden.schrittWarteliste,
    t.senden.schrittBlock,
    t.senden.schrittBestaetigungen,
)

/**
 * Was oben steht: ein Wort und ein Satz.
 *
 * REGEL 20 GILT AUCH HIER, und hier besonders: Keines dieser Woerter heisst
 * "erledigt" oder "endgueltig". "Bestaetigt" steht erst nach zwei
 * Bestaetigungen da, und der Satz dazu sagt genau das - nicht mehr. Dass
 * auch zwei Bestaetigungen kein Checkpoint sind, steht unter
 * "Einzelheiten".
 *
 * Der letzte Fall ist nicht "nichts passiert": Dieser Bildschirm erscheint
 * erst, wenn der Knoten angenommen hat. Fehlt die Liste der Schritte, ist
 * sie nur noch nicht geholt.
 */
private fun standInWorten(
    imBlock: Boolean,
    bestaetigt: Boolean,
    betrag: String,
    t: Texte,
): Pair<String, String> = when {
    bestaetigt -> t.senden.wegBestaetigt to t.senden.satzBestaetigt
    imBlock -> t.senden.wegImNetz to t.senden.satzImNetz
    else -> t.senden.wegAbgeschickt to t.senden.satzUnterwegs(betrag)
}

/**
 * Das Ergebnis.
 *
 * [block], [bestaetigungen], [zeiten] und [geprueft] sind nur fuer die
 * Angaben neben den Schritten da (`schrittAngabe`). Fehlen sie, stehen dort
 * die Woerter "erledigt", "wartet", "offen" wie bisher.
 *
 * [jetzt] ist die Uhr fuer "geprueft vor n s". Ohne Angabe laeuft sie von
 * selbst, einmal je Sekunde und nur, solange ein Schritt wartet.
 *
 * SEIT DEM 12.11.2026 EINFACH: ein Wort, ein Satz, drei Schritte, unten
 * "Fertig". Die fuenf Schritte mit Uhrzeiten, die drei Schilder, die
 * Kennung und "Stand jetzt abfragen" liegen unter "Einzelheiten".
 */
@Composable
fun SendenFertigScreen(
    ergebnis: Senden.Ergebnis,
    entwurf: Senden.Entwurf,
    schritte: List<Sendestatus.Teil>?,
    onAktualisieren: () -> Unit,
    onFertig: () -> Unit,
    modifier: Modifier = Modifier,
    block: Long? = null,
    bestaetigungen: Int = 0,
    zeiten: Sendezeiten = Sendezeiten(),
    geprueft: Long? = null,
    jetzt: Long? = null,
    einzelheitenOffen: Boolean = false,
) {
    val t = LocalTexte.current
    Column(modifier.fillMaxSize()) {
        // Angenommen: kein Kopf - das Wort steht unter dem Zeichen, "Fertig"
        // am Fuss. Abgelehnt und Fehler behalten Titel und Knopf oben.
        if (ergebnis is Senden.Ergebnis.Angenommen) {
            Spacer(Modifier.height(28.dp))
        } else Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, top = 16.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                when (ergebnis) {
                    is Senden.Ergebnis.Angenommen -> t.senden.eingereicht
                    is Senden.Ergebnis.Abgelehnt -> t.senden.abgelehnt
                    is Senden.Ergebnis.Fehler -> t.senden.nichtGesendet
                },
                style = Schrift.Titel,
                color = Farbe.Schrift,
                modifier = Modifier.weight(1f),
            )
            Knopf(t.senden.fertig, onFertig, art = Knopfart.STILL)
        }

        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
        ) {
            when (ergebnis) {
                is Senden.Ergebnis.Angenommen -> Angenommen(
                    ergebnis, entwurf, schritte, block, bestaetigungen, zeiten, geprueft, jetzt,
                    einzelheitenOffen, onAktualisieren,
                )

                is Senden.Ergebnis.Abgelehnt -> Column(
                    Modifier.padding(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    // `klartext` und, weiter unten, `grund` kommen deutsch aus
                    // netz/Senden.kt. Uebersetzt wird erst hier, wo sie
                    // angezeigt werden. Code und Einzelheit sind die Worte
                    // des Knotens und bleiben, wie er sie geschickt hat.
                    Warnhinweis(Sendemeldungen.uebersetze(ergebnis.klartext, t.sprache))
                    Flaeche(abstand = 0.dp) {
                        Wertzeile(
                            t.senden.grundDesKnotens, ergebnis.code,
                            festbreite = true, linie = ergebnis.detail != null,
                        )
                        if (ergebnis.detail != null) {
                            Wertzeile(t.senden.einzelheit, ergebnis.detail, linie = false)
                        }
                    }
                    Text(
                        t.senden.nichtsAbgebucht,
                        style = Schrift.Text,
                        color = Farbe.Schrift,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                }

                is Senden.Ergebnis.Fehler -> Column(
                    Modifier.padding(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Warnhinweis(Sendemeldungen.uebersetze(ergebnis.grund, t.sprache))
                    Text(
                        t.senden.kontostandPruefen,
                        style = Schrift.Text,
                        color = Farbe.Schrift,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                }
            }
        }

        if (ergebnis is Senden.Ergebnis.Angenommen) {
            Column(
                Modifier.padding(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 22.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Solange noch etwas aussteht: Man muss nicht zusehen. Ist die
                // Ueberweisung bestaetigt, laeuft nichts mehr "weiter".
                if (schritte?.getOrNull(4) != Sendestatus.Teil.ERLEDIGT) {
                    Text(
                        t.senden.appSchliessen,
                        style = Schrift.Hinweis.copy(fontSize = 12.5.sp),
                        color = Farbe.Leise,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Knopf(
                    t.senden.fertig,
                    onFertig,
                    Modifier.fillMaxWidth().testTag("fertig"),
                )
            }
        }
    }
}

@Composable
private fun Angenommen(
    ergebnis: Senden.Ergebnis.Angenommen,
    entwurf: Senden.Entwurf,
    schritte: List<Sendestatus.Teil>?,
    block: Long?,
    bestaetigungen: Int,
    zeiten: Sendezeiten,
    geprueft: Long?,
    jetzt: Long?,
    einzelheitenOffen: Boolean,
    onAktualisieren: () -> Unit,
) {
    val ctx = LocalContext.current
    val t = LocalTexte.current
    val erledigt = { i: Int -> schritte?.getOrNull(i) == Sendestatus.Teil.ERLEDIGT }

    // HIER gehoert das Zeichen hin, und nirgends dringender: Band 1
    // angenommen, Band 2 im Block, Stamm ab zwei Bestaetigungen. Die Liste
    // darunter sagt dasselbe in Worten; das Zeichen sagt es auf einen Blick.
    val baender = baenderFuerZahlung(
        angenommen = erledigt(1),
        imBlock = erledigt(3),
        bestaetigungen = if (erledigt(4)) 2 else 0,
    )
    val (ueberschrift, unterzeile) = standInWorten(
        imBlock = erledigt(3),
        bestaetigt = erledigt(4),
        betrag = Betrag.genau(entwurf.betrag, t.locale),
        t = t,
    )

    // Die Uhr fuer "geprueft vor n s". Sie tickt nur, solange ein Schritt
    // wartet und bekannt ist, wann zuletzt nachgefragt wurde - sonst gibt
    // es nichts, was sich sekundenweise aendert.
    val tickt = jetzt == null && geprueft != null &&
        schritte?.any { it == Sendestatus.Teil.WARTET } == true
    val uhr by produceState(System.currentTimeMillis() / 1000, tickt) {
        while (tickt) {
            delay(1_000)
            value = System.currentTimeMillis() / 1000
        }
    }
    val zeitpunkt = jetzt ?: uhr
    val zone = ZoneId.systemDefault()

    Column(
        Modifier.fillMaxWidth().padding(start = 16.dp, top = 6.dp, end = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Bei grosser Systemschrift nimmt das Zeichen weniger Platz -
        // den brauchen dann die fuenf Schritte (REGELN.md, Regel 29).
        Zeichen(
            baender[0], baender[1], baender[2],
            groesse = if (LocalDensity.current.fontScale > 1.15f) 56.dp else 100.dp,
            beschreibung = t.senden.standDerZahlung(ueberschrift, unterzeile),
        )
        Text(
            ueberschrift,
            style = Schrift.Gross.copy(fontSize = 30.sp, lineHeight = 34.sp),
            color = Farbe.Schrift,
            modifier = Modifier.padding(top = 12.dp).testTag("stand_wort"),
        )
        Text(
            unterzeile,
            style = Schrift.Text,
            color = Farbe.Weich,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(start = 20.dp, top = 6.dp, end = 20.dp),
        )
    }

    // --- Drei Schritte, die jeder versteht ------------------------------
    // Sie fassen die fuenf Schritte des Netzes zusammen und behaupten
    // nichts, was die fuenf nicht hergeben: "Abgeschickt" ist "vom Knoten
    // angenommen", "Im Netz angekommen" ist "in einem Block", "Bestaetigt"
    // sind zwei Bestaetigungen.
    val wartet = { i: Int -> schritte?.getOrNull(i) == Sendestatus.Teil.WARTET }
    fun angabe(i: Int) = schrittAngabe(
        i, schritte?.getOrNull(i), zeiten, block, bestaetigungen, geprueft, zeitpunkt, zone, t,
    )
    Column(Modifier.fillMaxWidth().padding(start = 24.dp, top = 26.dp, end = 24.dp)) {
        Schritt(
            name = t.senden.wegAbgeschickt,
            teil = if (erledigt(1)) Sendestatus.Teil.ERLEDIGT else Sendestatus.Teil.WARTET,
            angabe = if (erledigt(1)) angabe(1) else "",
            letzter = false,
        )
        Schritt(
            name = t.senden.wegImNetz,
            teil = when {
                erledigt(3) -> Sendestatus.Teil.ERLEDIGT
                erledigt(1) -> Sendestatus.Teil.WARTET
                else -> Sendestatus.Teil.OFFEN
            },
            // Solange gewartet wird: wann zuletzt nachgesehen wurde.
            angabe = when {
                erledigt(3) -> ""
                wartet(3) -> angabe(3)
                wartet(2) -> angabe(2)
                else -> ""
            },
            letzter = false,
        )
        Schritt(
            name = t.senden.wegBestaetigt,
            teil = when {
                erledigt(4) -> Sendestatus.Teil.ERLEDIGT
                erledigt(3) -> Sendestatus.Teil.WARTET
                else -> Sendestatus.Teil.OFFEN
            },
            angabe = if (!erledigt(4) && wartet(4)) angabe(4) else "",
            letzter = true,
        )
    }

    Box(Modifier.padding(top = 10.dp)) { Abrisskante() }
    Einzelheiten(offen = einzelheitenOffen) {
        // Das Zeichen, erklaert: Jedes der drei Teile sagt, wofuer es steht,
        // und leuchtet mit, wenn es erfuellt ist.
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, top = 4.dp, end = 16.dp)
                .height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Teilschild(
                t.senden.teilLinkesBand, t.senden.teilAngenommen,
                erledigt(1), Modifier.weight(1f),
            )
            Teilschild(
                t.senden.teilRechtesBand, t.senden.teilImBlock,
                erledigt(3), Modifier.weight(1f),
            )
            Teilschild(
                t.senden.teilStamm, t.senden.teilZweimalBestaetigt,
                erledigt(4), Modifier.weight(1f),
            )
        }

        Flaeche(
            Modifier.padding(start = 16.dp, top = 12.dp, end = 16.dp),
            innen = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 4.dp),
            abstand = 0.dp,
        ) {
            val namen = schrittNamen(t)
            for ((i, name) in namen.withIndex()) {
                val teil = schritte?.getOrNull(i)
                Schritt(
                    name = name,
                    teil = teil,
                    angabe = schrittAngabe(
                        i, teil, zeiten, block, bestaetigungen, geprueft, zeitpunkt, zone, t,
                    ),
                    letzter = i == namen.lastIndex,
                )
            }
        }

        Kennungskarte(ergebnis.txid)

        Text(
            t.senden.endgueltigGibtEsNicht,
            style = Schrift.Hinweis.copy(fontSize = 12.sp),
            color = Farbe.Leise,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(start = 20.dp, top = 14.dp, end = 20.dp),
        )
        // Die Wallet sieht selbst alle zehn Sekunden nach. Ganz weg darf der
        // Knopf trotzdem nicht: Im Funkloch laeuft der leise Takt ins Leere,
        // und dann will man es laut versuchen koennen.
        Knopf(
            t.senden.standJetztAbfragen,
            onAktualisieren,
            Modifier.fillMaxWidth().padding(start = 16.dp, top = 12.dp, end = 16.dp),
            art = Knopfart.FLAECHE,
            symbol = R.drawable.ic_arrows_clockwise_bold,
        )
    }
    Spacer(Modifier.height(8.dp))
}

/** Die Kennung der Ueberweisung mit Kopieren und dem Weg in den Explorer. */
@Composable
private fun Kennungskarte(txid: String?) {
    val ctx = LocalContext.current
    val t = LocalTexte.current
    if (txid != null) {
        Flaeche(
            Modifier.padding(start = 16.dp, top = 12.dp, end = 16.dp),
            innen = PaddingValues(start = 16.dp, top = 12.dp, end = 10.dp, bottom = 12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        t.senden.kennungDerUeberweisung,
                        style = Schrift.Klein,
                        color = Farbe.Leise,
                    )
                    KennungInGruppen(txid, Modifier.fillMaxWidth())
                }
                // Kopieren, und darunter: im Explorer nachsehen (Wunsch des
                // Besitzers, 11.11.2026). UNTEREINANDER, nicht nebeneinander:
                // Ein zweiter Knopf daneben nimmt der Kennung die Breite, und
                // bei grosser Schrift bricht sie dann in eine Zeile mehr um -
                // der Status wuerde hoeher (REGELN.md, Regel 29; gemessen am
                // 11.11.2026: 76 Bildpunkte mehr).
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Symbolknopf(R.drawable.ic_copy_fill, t.senden.kennungKopieren, {
                        inZwischenablage(ctx, txid)
                    })
                    val imExplorer = Explorer.ueberweisung(txid)
                    if (imExplorer != null) {
                        Symbolknopf(
                            R.drawable.ic_arrow_square_out_fill,
                            t.allgemein.imExplorer,
                            { imBrowserOeffnen(ctx, imExplorer) },
                            Modifier.testTag("im_explorer"),
                        )
                    }
                }
            }
        }
    }
}

/** Eines der drei Schilder unter dem Zeichen: welcher Teil, wofuer er steht. */
@Composable
private fun Teilschild(teil: String, steht: String, an: Boolean, modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxHeight()
            .gefasst(
                rundung = 14.dp,
                grund = SolidColor(Farbe.hauch(0.03f)),
                rand = if (an) YskarFarben.BlauHell.copy(alpha = 0.35f) else Farbe.Linie,
                glanz = 0f,
            )
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        // Jedes Schild hat ein Drittel der Breite. Bei vergroesserter
        // Systemschrift passt "angenommen" dort nicht mehr in eine Zeile -
        // und brach mitten im Wort um ("angenomme / n"). Darum bleibt jede
        // der beiden Zeilen EINE Zeile und wird nur so weit kleiner, wie sie
        // muss. Die Schilder sind die Legende zum Zeichen; dieselben Woerter
        // stehen in voller Groesse in der Liste darunter.
        Text(
            teil,
            style = Schrift.Marke,
            color = if (an) YskarFarben.BlauHell else Farbe.Leise,
            maxLines = 1,
            softWrap = false,
            autoSize = TextAutoSize.StepBased(
                minFontSize = 7.sp, maxFontSize = Schrift.Marke.fontSize, stepSize = 0.5.sp,
            ),
        )
        Text(
            steht,
            style = Schrift.Hinweis,
            color = if (an) Farbe.Schrift else Farbe.Leise,
            maxLines = 1,
            softWrap = false,
            autoSize = TextAutoSize.StepBased(
                minFontSize = 8.sp, maxFontSize = Schrift.Hinweis.fontSize, stepSize = 0.5.sp,
            ),
        )
    }
}

/**
 * Ein Schritt: links der Kreis mit der Linie zum naechsten, in der Mitte
 * der Name, rechts die Angabe.
 *
 *   erledigt   gefuellter Kreis mit Haken, blaue Linie
 *   wartet     heller Ring mit Punkt
 *   offen      grauer Ring, Name leise
 *
 * Der Zustand steht NIE nur in der Farbe: Der Kreis hat je Zustand eine
 * andere Form, und rechts steht er als Wort oder als Angabe.
 */
@Composable
private fun Schritt(name: String, teil: Sendestatus.Teil?, angabe: String, letzter: Boolean) {
    val erledigt = teil == Sendestatus.Teil.ERLEDIGT
    val wartet = teil == Sendestatus.Teil.WARTET

    Row(
        // Erst die Mindesthoehe, dann "so hoch wie der Inhalt": 22 dp Kreis,
        // 3 dp Luft, mindestens 12 dp Linie, 3 dp Luft. In der anderen
        // Reihenfolge wuerde die Hoehe des Inhalts die Mindesthoehe schlucken.
        Modifier
            .fillMaxWidth()
            .heightIn(min = if (letzter) 0.dp else 40.dp)
            .height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(
            Modifier.fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier
                    .size(22.dp)
                    .then(
                        if (erledigt) {
                            Modifier.background(
                                Brush.verticalGradient(
                                    listOf(YskarFarben.Blau, YskarFarben.BlauTief),
                                ),
                                CircleShape,
                            )
                        } else {
                            Modifier.drawBehind {
                                val strich = 2.dp.toPx()
                                drawCircle(
                                    color = if (wartet) YskarFarben.BlauHell
                                    else Farbe.hauch(0.16f),
                                    radius = (size.minDimension - strich) / 2f,
                                    style = Stroke(strich),
                                )
                            }
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    erledigt -> Symbol(R.drawable.ic_check_bold, 13.dp, farbe = Farbe.Schrift)
                    wartet -> Box(Modifier.size(8.dp).background(YskarFarben.BlauHell, CircleShape))
                }
            }
            if (!letzter) {
                Box(
                    Modifier
                        .padding(vertical = 3.dp)
                        .width(2.dp)
                        .weight(1f)
                        .background(
                            if (erledigt) YskarFarben.Blau else Farbe.hauch(0.12f),
                            RoundedCornerShape(1.dp),
                        ),
                )
            }
        }
        Row(
            Modifier.weight(1f).padding(top = 1.dp, bottom = 13.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Der Name eines Schritts bleibt in EINER Zeile und wird dafuer
            // notfalls kleiner gesetzt. Bei grosser Systemschrift brachen drei
            // der fuenf Namen um, und der Status ragte ueber den Bildschirm
            // (REGELN.md, Regel 29). Der Wortlaut bleibt vollstaendig.
            val namenStil = if (teil == Sendestatus.Teil.OFFEN || teil == null) {
                Schrift.Text
            } else Schrift.Zeile
            Text(
                name,
                style = namenStil,
                color = if (teil == Sendestatus.Teil.OFFEN || teil == null) Farbe.Leise
                else Farbe.Schrift,
                maxLines = 1,
                softWrap = false,
                autoSize = TextAutoSize.StepBased(
                    minFontSize = 10.sp, maxFontSize = namenStil.fontSize, stepSize = 0.5.sp,
                ),
                modifier = Modifier.weight(1f).alignByBaseline(),
            )
            Text(
                angabe,
                style = Schrift.Hinweis.copy(fontSize = 12.sp),
                color = Farbe.Leise,
                fontWeight = FontWeight.Normal,
                modifier = Modifier.alignByBaseline(),
            )
        }
    }
}
