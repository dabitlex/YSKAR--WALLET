package com.yskar.wallet.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yskar.wallet.R
import com.yskar.wallet.kontakte.Kontakt
import com.yskar.wallet.kontakte.Kontakte

// ============================================================================
//  KONTAKTE - Namen statt Adressen (Wunsch des Besitzers, 12.11.2026)
//
//  Drei Bildschirme: die Liste, ein Kontakt zum Anlegen oder Aendern, und
//  die Auswahl beim Senden. Was ein Kontakt ist und was er nicht ist, steht
//  in `kontakte/Kontakte.kt`: eine Lesehilfe, kein Beweis. Darum zeigt jeder
//  dieser Bildschirme die Adresse mit - nie nur den Namen.
// ============================================================================

/** Der erste Buchstabe des Namens im Kreis - damit man in der Liste schnell findet. */
@Composable
private fun Namenskreis(name: String) {
    val erster = name.firstOrNull { it.isLetterOrDigit() }?.uppercase() ?: "·"
    Box(
        Modifier.size(40.dp).background(Farbe.hauch(0.10f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(erster, style = Schrift.Zeile.copy(fontSize = 16.sp), color = Farbe.Schrift)
    }
}

/** Ein Kontakt in einer Liste: Kreis, Name, darunter die Adresse in Kurzform. */
@Composable
private fun Kontaktzeile(kontakt: Kontakt, linie: Boolean, onKlick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onKlick)
            .drawWithContent {
                drawContent()
                if (linie) {
                    val y = size.height - 0.5.dp.toPx()
                    drawLine(
                        Farbe.Linie, Offset(52.dp.toPx(), y), Offset(size.width, y), 1.dp.toPx(),
                    )
                }
            }
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Namenskreis(kontakt.name)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                kontakt.name,
                style = Schrift.Zeile.copy(fontSize = 16.sp),
                color = Farbe.Schrift,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            // Kurz, aber da: Zwei Kontakte duerfen gleich heissen, und dann
            // unterscheidet sie nur das hier.
            Text(adresseKurz(kontakt.adresse), style = Schrift.Winzig, color = Farbe.Leise, maxLines = 1)
        }
        Symbol(R.drawable.ic_caret_right_bold, 16.dp, farbe = Farbe.Leise)
    }
}

/** Kopf fest, Mitte rollt, Fuss fest. */
@Composable
private fun Kontaktseite(
    titel: String,
    onZurueck: () -> Unit,
    modifier: Modifier = Modifier,
    fuss: @Composable ColumnScope.() -> Unit = {},
    inhalt: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.fillMaxSize()) {
        Kopfzeile(titel, onZurueck = onZurueck)
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
            content = inhalt,
        )
        Column(
            Modifier.fillMaxWidth().padding(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 22.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = fuss,
        )
    }
}

// ------------------------------------------------------------------- Liste

/**
 * Alle Kontakte. Ein Tipp oeffnet einen zum Aendern; unten der eine Knopf
 * zum Hinzufuegen.
 */
@Composable
fun KontakteScreen(
    kontakte: List<Kontakt>,
    onNeu: () -> Unit,
    onBearbeiten: (Kontakt) -> Unit,
    onZurueck: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = LocalTexte.current
    BackHandler(onBack = onZurueck)
    Column(modifier.fillMaxSize()) {
        Kopfzeile(t.kontakte.titel, onZurueck = onZurueck)
        if (kontakte.isEmpty()) {
            Column(
                Modifier.weight(1f).fillMaxWidth().padding(horizontal = 28.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier.size(64.dp).background(Farbe.hauch(0.08f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) { Symbol(R.drawable.ic_user_bold, 28.dp, farbe = Farbe.Weich) }
                Text(
                    t.kontakte.leerTitel,
                    style = Schrift.Mittel,
                    color = Farbe.Schrift,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 18.dp),
                )
                Text(
                    t.kontakte.leerText,
                    style = Schrift.Text,
                    color = Farbe.Weich,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        } else {
            LazyColumn(
                Modifier.weight(1f).testTag("kontaktliste"),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 12.dp),
            ) {
                itemsIndexed(kontakte, key = { _, k -> k.adresse }) { i, k ->
                    Kontaktzeile(k, linie = i < kontakte.lastIndex) { onBearbeiten(k) }
                }
            }
        }
        Knopf(
            t.kontakte.hinzufuegen,
            onNeu,
            Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 22.dp)
                .testTag("kontakt_neu"),
        )
    }
}

// --------------------------------------------------------- Anlegen, Aendern

/**
 * Ein Kontakt: Name und Adresse.
 *
 * [vorlage] ist der Kontakt, der geaendert wird; null heisst: neu.
 * [adresseVorgabe] fuellt das Adressfeld vor - fuer "Als Kontakt speichern"
 * aus einer Zahlung. Den NAMEN fuellt nie etwas vor: Er entsteht nur durch
 * Eintippen (`kontakte/Kontakte.kt`).
 *
 * [onSpeichern] prueft und speichert; bei `Ok` fuehrt das Modell weiter,
 * bei einer Ablehnung steht ihr Satz hier.
 *
 * Der Scanner legt sich ueber DIESEN Bildschirm wie beim Senden, damit der
 * getippte Name stehen bleibt. Er liest nur Adressen (Regel 22).
 */
@Composable
fun KontaktBearbeitenScreen(
    vorlage: Kontakt?,
    adresseVorgabe: String?,
    onSpeichern: (name: String, adresse: String) -> Kontakte.Ergebnis,
    onEntfernen: (() -> Unit)?,
    onZurueck: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ctx = LocalContext.current
    val t = LocalTexte.current
    var name by remember { mutableStateOf(vorlage?.name ?: "") }
    var adresse by remember { mutableStateOf(vorlage?.adresse ?: adresseVorgabe ?: "") }
    var fehler by remember { mutableStateOf<String?>(null) }
    var scannen by remember { mutableStateOf(false) }
    var entfernenGefragt by remember { mutableStateOf(false) }

    if (scannen) {
        ScannerScreen(
            onGefunden = { gefunden ->
                adresse = gefunden
                fehler = null
                scannen = false
            },
            onAbbrechen = { scannen = false },
            modifier = modifier,
        )
        return
    }

    BackHandler(onBack = onZurueck)
    Kontaktseite(
        if (vorlage == null) t.kontakte.neuTitel else t.kontakte.bearbeitenTitel,
        onZurueck,
        modifier,
        fuss = {
            Knopf(
                t.kontakte.speichern,
                {
                    when (val e = onSpeichern(name, adresse)) {
                        is Kontakte.Ergebnis.Ok -> fehler = null
                        is Kontakte.Ergebnis.Abgelehnt ->
                            fehler = e.vorhanden?.let { t.kontakte.schonGespeichertAls(it.name) }
                                ?: t.kontakte.grund(e.grund)
                    }
                },
                Modifier.fillMaxWidth().testTag("kontakt_speichern"),
                aktiv = name.isNotBlank() && adresse.isNotBlank(),
            )
            if (onEntfernen != null) {
                // Zwei Tipps statt einer Rueckfrage: Ein Kontakt ist schnell
                // wieder angelegt, aber ein Versehen soll es nicht sein.
                Knopf(
                    if (entfernenGefragt) t.kontakte.wirklichEntfernen else t.kontakte.entfernen,
                    { if (entfernenGefragt) onEntfernen() else entfernenGefragt = true },
                    Modifier.fillMaxWidth().testTag("kontakt_entfernen"),
                    art = if (entfernenGefragt) Knopfart.WARNUNG else Knopfart.FLAECHE,
                    symbol = R.drawable.ic_trash_fill,
                )
            }
        },
    ) {
        Column(
            Modifier.padding(start = 16.dp, top = 4.dp, end = 16.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (fehler != null) Warnhinweis(fehler!!)

            Flaeche(
                rundung = 22.dp,
                innen = PaddingValues(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 14.dp),
                abstand = 6.dp,
            ) {
                Text(t.kontakte.name, style = Schrift.Klein, color = Farbe.Leise)
                Eingabe(
                    wert = name,
                    onWert = {
                        name = it
                        fehler = null
                    },
                    platzhalter = t.kontakte.namePlatzhalter,
                    modifier = Modifier.fillMaxWidth().testTag("kontakt_name"),
                    tastatur = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Words,
                        imeAction = ImeAction.Next,
                    ),
                )
            }

            Flaeche(
                rundung = 22.dp,
                innen = PaddingValues(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 14.dp),
                abstand = 6.dp,
            ) {
                Text(t.kontakte.adresse, style = Schrift.Klein, color = Farbe.Leise)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Eingabe(
                        wert = adresse,
                        onWert = {
                            adresse = it.trim()
                            fehler = null
                        },
                        platzhalter = "ysr1…",
                        modifier = Modifier.weight(1f).testTag("kontakt_adresse"),
                        stil = Schrift.Adresse.copy(fontSize = 16.sp),
                        einzeilig = false,
                        tastatur = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done,
                        ),
                    )
                    Symbolknopf(
                        R.drawable.ic_qr_code_fill, t.senden.qrCodeScannen,
                        { scannen = true },
                        betont = true,
                    )
                    Symbolknopf(
                        R.drawable.ic_clipboard_text_fill, t.senden.einfuegen,
                        {
                            adresse = ausZwischenablage(ctx).trim()
                            fehler = null
                        },
                        betont = true,
                    )
                }
            }

            Text(
                t.kontakte.nurAufDiesemGeraet,
                style = Schrift.Hinweis,
                color = Farbe.Leise,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
            )
        }
    }
}

// ----------------------------------------------------------------- Auswahl

/**
 * Einen Kontakt waehlen - beim Senden. Liegt wie der Scanner UEBER der
 * Eingabe, damit Betrag und Notiz stehen bleiben.
 *
 * Gewaehlt wird die ADRESSE des Kontakts; sie landet im Adressfeld und
 * steht im Pruefschritt vollstaendig da, wie jede andere.
 */
@Composable
fun KontaktAuswahl(
    kontakte: List<Kontakt>,
    onGewaehlt: (Kontakt) -> Unit,
    onAbbrechen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = LocalTexte.current
    BackHandler(onBack = onAbbrechen)
    Column(modifier.fillMaxSize()) {
        Kopfzeile(
            t.kontakte.waehlenTitel,
            onZurueck = onAbbrechen,
            zurueckSymbol = R.drawable.ic_x_bold,
            zurueckText = t.allgemein.abbrechen,
        )
        LazyColumn(
            Modifier.weight(1f).testTag("kontaktauswahl"),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 20.dp),
        ) {
            itemsIndexed(kontakte, key = { _, k -> k.adresse }) { i, k ->
                Kontaktzeile(k, linie = i < kontakte.lastIndex) { onGewaehlt(k) }
            }
        }
    }
}
