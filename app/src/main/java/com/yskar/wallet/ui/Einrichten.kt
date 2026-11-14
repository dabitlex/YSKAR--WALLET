package com.yskar.wallet.ui

import android.app.Activity
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import com.yskar.wallet.sprache.Sprache
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yskar.wallet.R

/**
 * Die Wallet anlegen oder wiederherstellen.
 */

/**
 * Bildschirmschutz, solange Geheimes sichtbar ist.
 *
 * FLAG_SECURE verhindert Bildschirmfotos, Bildschirmaufnahmen und das
 * Vorschaubild in der App-Uebersicht. Wer die zwoelf Woerter zeigt und das
 * nicht setzt, legt sie in jeden Screenshot-Ordner und in jedes
 * Cloud-Backup der Galerie.
 *
 * Nur fuer die Bildschirme, auf denen es noetig ist - nicht fuer die ganze
 * App: Eine Adresse oder ein Guthaben darf man fotografieren duerfen.
 */
@Composable
fun BildschirmSchutz() {
    val ctx = LocalContext.current
    DisposableEffect(Unit) {
        val fenster = (ctx as? Activity)?.window
        // Ueber `Fensterschutz`, nicht direkt am Fenster: Der Sichtschutz im
        // Hintergrund benutzt denselben Schalter, und keiner darf dem anderen
        // den Schutz wegnehmen.
        fenster?.let { Fensterschutz.halte(it, Fensterschutz.WOERTER) }
        onDispose { fenster?.let { Fensterschutz.gibFrei(it, Fensterschutz.WOERTER) } }
    }
}

/**
 * Der Rahmen der Seiten rund um die zwoelf Woerter: Kopf fest, Fuss fest,
 * die Mitte rollt - wie auf den Sendebildschirmen.
 *
 * Die Ueberschrift steht gross IN der Seite, nicht im Kopf: "Schreib diese
 * zwoelf Woerter auf" passt nicht zwischen zwei Knoepfe, und bei
 * vergroesserter Systemschrift erst recht nicht.
 *
 * Der Hauptknopf steht im [fuss] und damit immer unten am Daumen. Die Mitte
 * darf rollen (kleines Geraet, grosse Schrift); der Knopf bleibt erreichbar.
 */
@Composable
private fun Seite(
    ueberschrift: String,
    onZurueck: () -> Unit,
    modifier: Modifier = Modifier,
    zurueckSymbol: Int = R.drawable.ic_caret_left_bold,
    // null: das Wort fuer "zurueck" in der Sprache der App.
    zurueckText: String? = null,
    fuss: @Composable ColumnScope.() -> Unit,
    inhalt: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.fillMaxSize()) {
        Kopfzeile(
            titel = "",
            onZurueck = onZurueck,
            zurueckSymbol = zurueckSymbol,
            zurueckText = zurueckText,
        )
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, top = 2.dp, end = 16.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                ueberschrift,
                // Ausgeglichen umbrechen: nicht "…zwoelf Woerter" und darunter
                // allein "auf".
                style = Schrift.Gross.copy(lineBreak = LineBreak.Heading),
                color = Farbe.Schrift,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
            inhalt()
        }
        Column(
            Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 22.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = fuss,
        )
    }
}

/** Der erklaerende Absatz unter der Ueberschrift. */
@Composable
private fun Absatz(text: String) {
    Text(
        text,
        style = Schrift.Text,
        color = Farbe.Weich,
        modifier = Modifier.padding(horizontal = 4.dp),
    )
}

/** Ein Eingabefeld: Flaeche mit Beschriftung, darunter der Inhalt. */
@Composable
private fun Feld(name: String, inhalt: @Composable ColumnScope.() -> Unit) {
    Flaeche(
        rundung = 22.dp,
        innen = PaddingValues(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 14.dp),
        abstand = 6.dp,
    ) {
        Text(name, style = Schrift.Klein, color = Farbe.Leise)
        inhalt()
    }
}

/** "Bildschirmfotos sind gesperrt" - auf beiden Seiten mit den Woertern. */
@Composable
private fun Fotosperre() {
    Hinweisstreifen(
        LocalTexte.current.einrichten.fotosperre,
        vorne = { Symbol(R.drawable.ic_shield_check_fill, 20.dp, farbe = Farbe.BlauSchrift) },
    )
}

@Composable
fun EinstiegScreen(
    onNeu: () -> Unit,
    onWiederherstellen: () -> Unit,
    modifier: Modifier = Modifier,
    onSprache: ((Sprache) -> Unit)? = null,
) {
    val t = LocalTexte.current

    // Der erste Eindruck: das Zeichen gross, ein Satz, zwei Wege. Kein
    // Fliesstext ueber Blockchain, keine Aufzaehlung von Vorteilen - wer
    // hier landet, will die Wallet, nicht die Broschuere.
    //
    // Vorlage: design-neu/html/01_einstieg.html. Zeichen, Schriftzug und
    // Satz stehen in der Luft zwischen zwei Abstandhaltern; die Karte mit
    // den zwei Saetzen ueber die Woerter und die beiden Wege stehen unten,
    // wo der Daumen ist.
    Box(modifier.fillMaxSize()) {
        Musterhimmel(hoehe = 460.dp, deckkraft = 0.08f)

        Blatt(mittig = true) {
            Spacer(Modifier.height(24.dp))
            Spacer(Modifier.weight(1f))

            ZeichenRuhig(groesse = 136.dp)
            Text(
                "YSKAR",
                style = Schrift.Schriftzug,
                color = Farbe.Schrift,
                modifier = Modifier.padding(top = 24.dp),
            )
            Text(
                t.einrichten.einstiegLeitsatz,
                style = Schrift.Riesig,
                color = Farbe.Schrift,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(start = 24.dp, top = 20.dp, end = 24.dp),
            )

            Spacer(Modifier.height(24.dp))
            Spacer(Modifier.weight(1f))

            Flaeche(
                Modifier.padding(horizontal = 16.dp),
                innen = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                abstand = 0.dp,
            ) {
                Merksatz(R.drawable.ic_key_fill, t.einrichten.einstiegWoerterSindGuthaben)
                Merksatz(
                    R.drawable.ic_shield_check_fill,
                    t.einrichten.einstiegNiemandSetztZurueck,
                )
            }

            Column(
                Modifier.padding(start = 16.dp, top = 14.dp, end = 16.dp, bottom = 26.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Knopf(t.einrichten.neueWalletAnlegen, onNeu, Modifier.fillMaxWidth())
                // Gleiche Hoehe wie der Hauptknopf darueber, nur ohne Farbe.
                // Zwei Knoepfe untereinander, die sich um vier Punkte in der
                // Hoehe unterscheiden, sieht man nicht bewusst, aber man
                // sieht es.
                Knopf(
                    t.einrichten.ausWoerternWiederherstellen,
                    onWiederherstellen,
                    Modifier.fillMaxWidth().heightIn(min = 58.dp),
                    art = Knopfart.FLAECHE,
                )
            }
        }

        // Die Sprache, schon hier: Die Einstellungen gibt es erst, wenn eine
        // Wallet da ist - und bis dahin soll niemand durch zwoelf Woerter
        // und eine PIN in einer Sprache muessen, die er nicht liest. Der
        // Knopf nennt die ANDERE Sprache in deren eigenem Namen; bei zwei
        // Sprachen ist das die ganze Auswahl.
        if (onSprache != null) {
            val andere = Sprache.entries.first { it != t.sprache }
            Row(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 14.dp, end = 16.dp)
                    .gefasst(
                        rundung = 20.dp,
                        grund = SolidColor(Farbe.hauch(0.05f)),
                        rand = Farbe.Linie,
                        glanz = 0f,
                    )
                    .clip(RoundedCornerShape(20.dp))
                    .clickable(role = Role.Button) { onSprache(andere) }
                    .heightIn(min = 40.dp)
                    .padding(horizontal = 14.dp)
                    .testTag("sprache_wechseln"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Symbol(R.drawable.ic_globe_bold, 16.dp, farbe = Farbe.Weich)
                Text(andere.eigenname, style = Schrift.KnopfBlau, color = Farbe.Weich)
            }
        }
    }
}

/** Ein Satz mit Symbol im blauen Kaestchen - die zwei auf dem Einstieg. */
@Composable
private fun Merksatz(symbol: Int, text: String) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 44.dp).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier.size(34.dp).gefasst(
                rundung = 12.dp,
                grund = SolidColor(YskarFarben.Blau.copy(alpha = 0.12f)),
                rand = YskarFarben.Blau.copy(alpha = 0.28f),
                glanz = 0f,
            ),
            contentAlignment = Alignment.Center,
        ) { Symbol(symbol, 20.dp) }
        Text(text, style = Schrift.Name, color = Farbe.Weich, modifier = Modifier.weight(1f))
    }
}

// ---------------------------------------------------------------------------
// Die vier Seiten mit den zwoelf Woertern.
//
// Jede gibt es zweimal: als `…Screen` und als `…Bild`.
//
//   …Screen  ist, was die App aufruft. Er setzt ZUERST den Bildschirmschutz
//            (FLAG_SECURE, REGELN.md Regeln 14 und 28) und zeichnet dann
//            das Bild. Daran hat der Umbau nichts geaendert.
//   …Bild    ist nur das Aussehen, OHNE Schutz. Es ist `internal` und hat
//            genau zwei Aufrufer: den Screen darueber und den Testrahmen
//            `Bildschirmbilder`, der es mit dem oeffentlichen
//            BIP39-Testvektor zeichnet. Ohne diese Trennung gaebe es von
//            diesen Seiten kein Bild - ein Foto der geschuetzten Seite ist
//            schwarz -, und ihr Aussehen waere nie nachzupruefen.
//
// WER HIER EINE SEITE DAZUBAUT ODER EIN `…Bild` VON ANDERSWO AUFRUFT, zeigt
// die Woerter ohne Schutz. `SchutzTest` prueft auf dem Geraet, dass jeder
// der vier Screens das Fenster wirklich sperrt.
// ---------------------------------------------------------------------------

@Composable
fun WoerterZeigenScreen(
    woerter: List<String>,
    onWeiter: () -> Unit,
    onAbbrechen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BildschirmSchutz()
    WoerterZeigenBild(woerter, onWeiter, onAbbrechen, modifier)
}

@Composable
internal fun WoerterZeigenBild(
    woerter: List<String>,
    onWeiter: () -> Unit,
    onAbbrechen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = LocalTexte.current

    Seite(
        t.einrichten.woerterAufschreibenTitel,
        onAbbrechen,
        modifier,
        zurueckSymbol = R.drawable.ic_x_bold,
        zurueckText = t.allgemein.abbrechen,
        fuss = {
            Knopf(t.einrichten.woerterAufgeschrieben, onWeiter, Modifier.fillMaxWidth())
        },
    ) {
        Absatz(t.einrichten.woerterAufschreibenAbsatz)
        Wortgitter(woerter)
        Fotosperre()
    }
}

/**
 * Die zwoelf Woerter in zwei Spalten, zeilenweise nummeriert: 1 und 2 in
 * der ersten Zeile, 3 und 4 in der zweiten. So wie man sie aufschreibt.
 */
@Composable
private fun Wortgitter(woerter: List<String>) {
    Flaeche(innen = PaddingValues(12.dp), abstand = 8.dp) {
        for (reihe in woerter.chunked(2).withIndex()) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                for ((spalte, wort) in reihe.value.withIndex()) {
                    Wortfeld(
                        nummer = reihe.index * 2 + spalte + 1,
                        wort = wort,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

/** Ein Wort der Merkwortliste, mit seiner Nummer. */
@Composable
private fun Wortfeld(nummer: Int, wort: String, modifier: Modifier = Modifier) {
    Row(
        modifier
            .heightIn(min = 44.dp)
            .gefasst(
                rundung = 14.dp,
                grund = SolidColor(Color.Black.copy(alpha = 0.30f)),
                rand = Farbe.Linie,
                glanz = 0f,
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            "$nummer",
            style = Schrift.Winzig,
            color = YskarFarben.BlauHell,
            textAlign = TextAlign.End,
            // Breit genug fuer zwei Ziffern, auch bei grosser Schrift - sonst
            // stehen die Woerter ab Nummer 10 weiter rechts als die darueber.
            modifier = Modifier.widthIn(min = 20.dp),
        )
        // Testmarke: Der instrumentierte Test liest die Woerter hier ab,
        // um die Gegenprobe ausfuellen zu koennen.
        Text(
            wort,
            style = Schrift.Adresse,
            color = Farbe.Schrift,
            modifier = Modifier.testTag("wort$nummer"),
        )
    }
}

/**
 * Die zwoelf Woerter noch einmal ansehen.
 *
 * NICHT DERSELBE BILDSCHIRM WIE BEIM EINRICHTEN, obwohl er gleich
 * aussieht: Dort geht es weiter zur Abfrage, hier zurueck. Und der Text
 * ist ein anderer, weil die Lage eine andere ist - wer hierherkommt, hat
 * die Woerter schon einmal gehabt und will sie pruefen oder ersetzen.
 *
 * `FLAG_SECURE` steht hier wie auf den anderen Woerter-Seiten.
 * Dass ein Bildschirmfoto schwarz bleibt, ist der Beweis, dass es wirkt.
 */
@Composable
fun WoerterAnsehenScreen(
    woerter: List<String>,
    onZurueck: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BildschirmSchutz()
    WoerterAnsehenBild(woerter, onZurueck, modifier)
}

@Composable
internal fun WoerterAnsehenBild(
    woerter: List<String>,
    onZurueck: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = LocalTexte.current

    Seite(
        t.einrichten.woerterAnsehenTitel,
        onZurueck,
        modifier,
        fuss = { Knopf(t.einrichten.fertig, onZurueck, Modifier.fillMaxWidth()) },
    ) {
        Absatz(t.einrichten.woerterAnsehenAbsatz)
        Wortgitter(woerter)
        Fotosperre()
    }
}

@Composable
fun WoerterPruefenScreen(
    positionen: List<Int>,
    fehler: String?,
    onPruefen: (List<String>) -> Unit,
    onZurueck: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BildschirmSchutz()
    WoerterPruefenBild(positionen, fehler, onPruefen, onZurueck, modifier)
}

/**
 * Die Gegenprobe: drei Woerter aus der Liste eintragen.
 *
 * Der Pfeil oben links fuehrt zurueck zu den Woertern - das stand vorher
 * als Textknopf "Wörter noch einmal zeigen" unter "Weiter" und ist jetzt
 * die Beschreibung des Pfeils.
 */
@Composable
internal fun WoerterPruefenBild(
    positionen: List<Int>,
    fehler: String?,
    onPruefen: (List<String>) -> Unit,
    onZurueck: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = LocalTexte.current
    var eingaben by remember { mutableStateOf(List(positionen.size) { "" }) }

    Seite(
        t.einrichten.gegenprobeTitel,
        onZurueck,
        modifier,
        zurueckText = t.einrichten.woerterNochEinmalZeigen,
        fuss = {
            Knopf(
                t.einrichten.weiter,
                { onPruefen(eingaben) },
                Modifier.fillMaxWidth(),
                aktiv = eingaben.all { it.isNotBlank() },
            )
        },
    ) {
        Absatz(t.einrichten.gegenprobeAbsatz)

        for ((i, pos) in positionen.withIndex()) {
            Feld(t.einrichten.wortNummer(pos + 1)) {
                Eingabe(
                    wert = eingaben[i],
                    onWert = { neu ->
                        eingaben = eingaben.toMutableList().also { l -> l[i] = neu.trim() }
                    },
                    platzhalter = "",
                    // Testmarke: der instrumentierte Test fuellt hier aus.
                    modifier = Modifier.fillMaxWidth().testTag("pruefwort${pos + 1}"),
                    stil = Schrift.Adresse.copy(fontSize = 16.sp, lineHeight = 22.sp),
                    tastatur = KeyboardOptions(
                        // Passworttastatur: keine Vorschlaege, kein Lernen.
                        // Die Anzeige bleibt trotzdem offen (keine Maskierung).
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Next,
                    ),
                )
            }
        }

        if (fehler != null) Warnhinweis(fehler)
    }
}

@Composable
fun WiederherstellenScreen(
    onUebernehmen: (String) -> String?,
    onAbbrechen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BildschirmSchutz()
    WiederherstellenBild(onUebernehmen, onAbbrechen, modifier)
}

/**
 * Wiederherstellen: zwoelf Felder, eines je Wort - wie die zwoelf Kacheln
 * beim Anlegen (Wunsch des Besitzers, 11.11.2026). Vorher war es ein
 * einziges grosses Feld.
 *
 * Was in den Feldern steht, wenn getippt oder eingefuegt wird, rechnet
 * `Merkwortfelder` (rein, mit Unittests): Ein Leerzeichen geht ins naechste
 * Feld; mehrere eingefuegte Woerter verteilen sich.
 *
 * GENAU ZWOELF (Entscheidung des Besitzers, 11.11.2026). Wer mehr als
 * zwoelf Woerter einfuegt, bekommt gesagt, wie viele keinen Platz gefunden
 * haben. Ob die zwoelf stimmen, sagt weiter das Modell.
 *
 * Passworttastatur in jedem Feld: keine Vorschlaege, kein Lernen. Die
 * Woerter stehen offen da (keine Maskierung); der Bildschirmschutz kommt
 * vom Screen darueber.
 */
@Composable
internal fun WiederherstellenBild(
    onUebernehmen: (String) -> String?,
    onAbbrechen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = LocalTexte.current
    var felder by remember { mutableStateOf(Merkwortfelder.leer()) }
    var fehler by remember { mutableStateOf<String?>(null) }
    var zuViele by remember { mutableStateOf(0) }

    // Ein Ziel fuer den Fokus je Feld.
    val fokus = remember { List(Merkwortfelder.ANZAHL) { FocusRequester() } }
    var ziel by remember { mutableStateOf<Int?>(null) }
    // Erst NACH dem Zeichnen: Ein gerade erst dazugekommenes Feld gibt es
    // vorher noch nicht.
    LaunchedEffect(ziel, felder.size) {
        val z = ziel
        if (z != null && z < felder.size) {
            fokus[z].requestFocus()
            ziel = null
        }
    }

    val anzahl = Merkwortfelder.anzahl(felder)

    Seite(
        t.einrichten.wiederherstellenTitel,
        onAbbrechen,
        modifier,
        zurueckSymbol = R.drawable.ic_x_bold,
        zurueckText = t.allgemein.abbrechen,
        fuss = {
            Knopf(
                t.einrichten.weiter,
                { fehler = onUebernehmen(Merkwortfelder.alsText(felder)) },
                Modifier.fillMaxWidth(),
                aktiv = anzahl == Merkwortfelder.ANZAHL,
            )
        },
    ) {
        Absatz(t.einrichten.wiederherstellenAbsatz)

        Flaeche(innen = PaddingValues(12.dp), abstand = 8.dp) {
            for (reihe in felder.indices.chunked(2)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    for (i in reihe) {
                        Merkwortfeld(
                            nummer = i + 1,
                            wort = felder[i],
                            letztes = i == felder.lastIndex,
                            fokus = fokus[i],
                            onWort = { neu ->
                                fehler = null
                                val e = Merkwortfelder.eingabe(felder, i, neu)
                                felder = e.felder
                                zuViele = e.uebrig
                                if (e.fokus != null && e.fokus != i) ziel = e.fokus
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }

        Text(
            t.einrichten.woerterVon(anzahl, Merkwortfelder.ANZAHL),
            style = Schrift.Hinweis,
            color = if (fehler != null) Farbe.Warnung else Farbe.Leise,
            modifier = Modifier.align(Alignment.End).padding(horizontal = 4.dp),
        )

        if (zuViele > 0) {
            Warnhinweis(t.einrichten.zuVieleWoerter(zuViele))
        }
        if (fehler != null) Warnhinweis(fehler!!)
    }
}

/**
 * Ein Feld fuer ein Merkwort: die Nummer, daneben die Eingabe - in der Form
 * der Kachel, in der das Wort beim Anlegen stand.
 */
@Composable
private fun Merkwortfeld(
    nummer: Int,
    wort: String,
    letztes: Boolean,
    fokus: FocusRequester,
    onWort: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .heightIn(min = 44.dp)
            .gefasst(
                rundung = 14.dp,
                grund = SolidColor(Color.Black.copy(alpha = 0.30f)),
                rand = Farbe.Linie,
                glanz = 0f,
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            "$nummer",
            style = Schrift.Winzig,
            color = YskarFarben.BlauHell,
            textAlign = TextAlign.End,
            modifier = Modifier.widthIn(min = 20.dp),
        )
        // Das Feld fuehrt Text UND Schreibmarke selbst. Kommt das Wort von
        // aussen - eingefuegte Woerter verteilen sich, ein Leerzeichen wird
        // abgeschnitten -, steht die Marke danach am ENDE des Wortes. Mit
        // einem blossen Text staende sie am Anfang.
        var wert by remember { mutableStateOf(TextFieldValue(wort, TextRange(wort.length))) }
        if (wert.text != wort) wert = TextFieldValue(wort, TextRange(wort.length))

        BasicTextField(
            value = wert,
            onValueChange = { neu ->
                wert = neu
                if (neu.text != wort) onWort(neu.text)
            },
            // Testmarke: der instrumentierte Test tippt hier.
            modifier = Modifier
                .weight(1f)
                .focusRequester(fokus)
                .testTag("merkwort$nummer"),
            textStyle = Schrift.Adresse.copy(color = Farbe.Schrift),
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                // Passworttastatur: keine Vorschlaege, kein Lernen.
                keyboardType = KeyboardType.Password,
                imeAction = if (letztes) ImeAction.Done else ImeAction.Next,
            ),
            cursorBrush = SolidColor(YskarFarben.BlauHell),
        )
    }
}

@Composable
fun PinSetzenScreen(
    laeuft: Boolean,
    fehler: String?,
    onFertig: (CharArray) -> Unit,
    onAbbrechen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = LocalTexte.current
    val laenge = 6
    var erste by remember { mutableStateOf("") }
    var zweite by remember { mutableStateOf("") }
    var wiederholung by remember { mutableStateOf(false) }
    var eigenerFehler by remember { mutableStateOf<String?>(null) }

    val meldung = eigenerFehler ?: fehler
    val getippt = (if (wiederholung) zweite else erste).length

    // Reine Anzeige, wie auf der Sperre: je zwei Ziffern ein Teil des
    // Zeichens. Was eingegeben wurde, weiss das Zeichen nicht - nur wie viel.
    val baender = baenderFuerPin(ziffern = getippt, falsch = meldung != null, geprueft = laeuft)

    // Vorlage: design-neu/html/02_pin_setzen.html. Die zwei Punkte oben
    // rechts sind die zwei Eingaben - festlegen und wiederholen.
    Box(modifier.fillMaxSize()) {
        Musterhimmel(hoehe = 330.dp, deckkraft = 0.07f)

        Blatt(mittig = true) {
            Kopfzeile(
                titel = "",
                onZurueck = onAbbrechen,
                zurueckSymbol = R.drawable.ic_x_bold,
                zurueckText = t.allgemein.abbrechen,
                zurueckAktiv = !laeuft,
            ) { Schrittpunkte(schritt = if (wiederholung) 2 else 1, von = 2) }

            Spacer(Modifier.weight(0.55f))

            Zeichen(
                links = baender[0],
                rechts = baender[1],
                stamm = baender[2],
                groesse = 96.dp,
                beschreibung = zifferStand(getippt, laenge, t),
            )
            Text(
                if (wiederholung) t.einrichten.pinWiederholen else t.einrichten.pinFestlegen,
                style = Schrift.Gross,
                color = Farbe.Schrift,
                modifier = Modifier.padding(top = 18.dp),
            )
            // Die Meldung steht dort, wo sonst der erklaerende Satz steht -
            // an der Stelle, auf die man gerade schaut. Der Satz kommt
            // zurueck, sobald wieder getippt wird.
            Text(
                meldung
                    ?: if (wiederholung) t.einrichten.pinWiederholenAbsatz
                    else t.einrichten.pinFestlegenAbsatz,
                style = Schrift.Name,
                color = if (meldung != null) Farbe.Warnung else Farbe.Leise,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(start = 28.dp, top = 6.dp, end = 28.dp),
            )

            Spacer(Modifier.height(30.dp))

            Ziffernfeld(
                laenge = laenge,
                ziffern = if (wiederholung) zweite else erste,
                aktiv = !laeuft,
                onZiffern = { neu ->
                    eigenerFehler = null
                    if (wiederholung) {
                        zweite = neu
                        if (neu.length == laenge) {
                            if (neu == erste) {
                                onFertig(neu.toCharArray())
                            } else {
                                eigenerFehler = t.einrichten.pinNichtGleich
                                erste = ""
                                zweite = ""
                                wiederholung = false
                            }
                        }
                    } else {
                        erste = neu
                        if (neu.length == laenge) {
                            wiederholung = true
                            zweite = ""
                        }
                    }
                },
            )

            Spacer(Modifier.height(16.dp))
            Spacer(Modifier.weight(1f))

            Row(
                Modifier.padding(start = 28.dp, end = 28.dp, bottom = 28.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Symbol(R.drawable.ic_shield_check_fill, 16.dp, farbe = Farbe.Leise)
                Text(
                    if (laeuft) t.einrichten.tresorWirdVerschluesselt
                    else t.einrichten.zeichenFuelltSich,
                    style = Schrift.Hinweis,
                    color = Farbe.Leise,
                )
            }
        }
    }
}
