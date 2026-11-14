package com.yskar.wallet.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yskar.wallet.R
import com.yskar.wallet.kontakte.Kontakt
import com.yskar.wallet.kontakte.Kontakte
import com.yskar.wallet.netz.Art
import com.yskar.wallet.netz.Betrag
import com.yskar.wallet.netz.VerlaufEintrag
import com.yskar.wallet.sprache.Texte

/**
 * Eine Bewegung, vollstaendig.
 *
 * SEIT DEM 12.11.2026 IN ZWEI STUFEN: Oben steht, was jeder wissen will -
 * wie viel, wann, wie weit, an wen. Block, Platz im Block, Zahl der
 * Bestaetigungen und die Kennung stehen unter "Einzelheiten", vollstaendig
 * wie vorher.
 *
 * Die Liste muss kuerzen - hier wird nichts gekuerzt. Das ist der
 * Unterschied: In der Liste steht, DASS etwas passiert ist, hier steht,
 * was genau, in welchem Block und wie oft bestaetigt.
 *
 * DREI DINGE STEHEN HIER, DIE DIE LISTE NICHT ZEIGEN KANN:
 *
 * 1. Was wirklich vom Konto abging. Bei einem Ausgang ist das Betrag PLUS
 *    Gebuehr - die Liste zeigt nur den Betrag, und wer nachrechnet, kaeme
 *    sonst auf eine Differenz, die er sich nicht erklaeren kann.
 * 2. Wie viele Bestaetigungen. Eine Bewegung in einem Block ist nicht
 *    dasselbe wie eine in einem Block mit zwanzig darueber.
 * 3. Die ganze Gegenadresse, in Vierergruppen, zum Vergleichen.
 *
 * Vorlage: design-neu/html/14_bewegung.html und 15_bewegung_eingang.html.
 * Die Bewegung ist ein Beleg: Betrag im Kartenkopf, darunter Abschnitte
 * zwischen Abrisskanten. Dieser Bildschirm DARF rollen - er ist kein
 * Sendebildschirm, und eine lange Notiz oder grosse Schrift soll Platz
 * haben.
 */
@Composable
fun BewegungScreen(
    eintrag: VerlaufEintrag,
    spitze: Long?,
    onZurueck: () -> Unit,
    modifier: Modifier = Modifier,
    einzelheitenOffen: Boolean = false,
    kontakte: List<Kontakt> = emptyList(),
    // "Als Kontakt speichern" - nur, wenn die Gegenadresse noch keinen
    // Namen hat. null: der Knopf fehlt.
    onKontaktAnlegen: ((String) -> Unit)? = null,
) {
    val ctx = LocalContext.current
    val t = LocalTexte.current
    val eingehend = eintrag.art.eingehend

    // Bestaetigt heisst wie nach dem Senden: mindestens zwei, der Block
    // selbst mitgezaehlt. Ist die Spitze unbekannt, steht kein Stand da.
    val kontaktName = Kontakte.nameFuer(kontakte, eintrag.gegenseite)
    val bestaetigungen = if (spitze == null || spitze < eintrag.height) null else spitze - eintrag.height + 1

    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Kopfzeile(t.wallet.bewegungTitel, onZurueck = onZurueck)

        Beleg(Modifier.padding(horizontal = 2.dp)) {
            // --- Der Betrag, gross --------------------------------------
            Kartenkopf(Modifier.fillMaxWidth(), gefasst = false) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, top = 14.dp, end = 20.dp, bottom = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(artText(eintrag.art, t), style = Schrift.Auftakt, color = Farbe.Lampe)
                    GuthabenZahl(
                        eintrag.betrag,
                        Modifier.fillMaxWidth(),
                        gross = Schrift.BelegBetrag.copy(fontSize = 52.sp, lineHeight = 52.sp),
                        klein = null,
                        vorzeichen = if (eingehend) "+" else "−",
                        farbe = if (eingehend) Farbe.Eingang else Farbe.Schrift,
                    )
                }
            }

            // --- Was jeder wissen will: wann, wie weit, was es gekostet hat
            Abrisskante()
            Column(Modifier.padding(horizontal = 20.dp, vertical = 2.dp)) {
                Wertzeile(t.wallet.zeileWann, zeitText(eintrag.zeit, t), hoehe = 44.dp)
                if (bestaetigungen != null) {
                    Wertzeile(
                        t.wallet.zeileStand,
                        if (bestaetigungen >= 2) t.wallet.standBestaetigt else t.wallet.standWirdBestaetigt,
                        hoehe = 44.dp,
                        linie = !eingehend,
                    )
                }
                if (!eingehend) {
                    Wertzeile(
                        t.wallet.zeileGebuehr, Betrag.genau(eintrag.gebuehr, t.locale),
                        einheit = "YSR", hoehe = 44.dp, linie = false,
                    )
                }
            }

            // --- Die Gegenseite -----------------------------------------
            if (eintrag.gegenseite != null) {
                Abrisskante()
                Row(
                    Modifier.padding(start = 20.dp, top = 12.dp, end = 12.dp, bottom = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (kontaktName != null) {
                            // Der Name, den der Besitzer vergeben hat - und
                            // darunter weiter die ganze Adresse.
                            Text(
                                if (eingehend) t.kontakte.von(kontaktName) else t.kontakte.an(kontaktName),
                                style = Schrift.Zeile.copy(fontSize = 16.sp),
                                color = Farbe.Schrift,
                            )
                        } else {
                            Text(
                                if (eingehend) t.wallet.vonDieserAdresse else t.wallet.anDieseAdresse,
                                style = Schrift.Klein,
                                color = Farbe.Leise,
                            )
                        }
                        AdresseInGruppen(
                            eintrag.gegenseite,
                            Modifier.fillMaxWidth(),
                            stil = Schrift.AdresseKlein.copy(fontSize = 14.sp),
                        )
                    }
                    Symbolknopf(R.drawable.ic_copy_fill, t.allgemein.adresseKopieren, {
                        inZwischenablage(ctx, eintrag.gegenseite)
                    })
                }
                if (kontaktName == null && onKontaktAnlegen != null) {
                    Knopf(
                        t.kontakte.alsKontaktSpeichern,
                        { onKontaktAnlegen(eintrag.gegenseite) },
                        Modifier
                            .padding(start = 20.dp, bottom = 14.dp)
                            .testTag("als_kontakt"),
                        art = Knopfart.STILL,
                        symbol = R.drawable.ic_user_bold,
                    )
                }
            }

            // --- Die Notiz ----------------------------------------------
            // Beim Pool-Anteil ist das der Name des Pools.
            if (eintrag.notiz.isNotEmpty()) {
                Abrisskante()
                Column(
                    Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        t.wallet.notizInDerKette,
                        style = Schrift.Klein,
                        color = Farbe.Leise,
                    )
                    Text(
                        t.wallet.notizZitat(eintrag.notiz),
                        style = Schrift.Zeile.copy(fontSize = 16.sp),
                        color = Farbe.Schrift,
                    )
                }
            }

            // --- Einzelheiten: wo sie in der Kette steht ------------------
            // Hier wird nichts gekuerzt. In der Liste steht, DASS etwas
            // passiert ist; hier steht, was genau, in welchem Block und wie
            // oft bestaetigt.
            Abrisskante()
            Einzelheiten(offen = einzelheitenOffen) {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 2.dp)) {
                    if (eingehend) {
                        // Die Gebuehr hat der Absender getragen. Ohne diesen
                        // Namen sieht sie aus, als waere sie vom Eingang
                        // abgezogen worden.
                        Wertzeile(
                            t.wallet.zeileGebuehrDesAbsenders,
                            Betrag.genau(eintrag.gebuehr, t.locale),
                            einheit = "YSR", hoehe = 40.dp,
                        )
                    } else {
                        // Was wirklich vom Konto abging: Betrag PLUS Gebuehr.
                        Wertzeile(
                            t.wallet.zeileVomKontoAbgegangen,
                            Betrag.genau(eintrag.betrag + eintrag.gebuehr, t.locale),
                            einheit = "YSR", hoehe = 40.dp,
                        )
                    }
                    if (eintrag.art == Art.POOL && eintrag.anteile != null) {
                        Wertzeile(t.wallet.zeileAnteile, eintrag.anteile.toString(), hoehe = 40.dp)
                    }
                    Wertzeile(t.wallet.zeileBlock, zahlText(eintrag.height, t), hoehe = 40.dp)
                    Wertzeile(t.wallet.zeilePlatzImBlock, eintrag.idx.toString(), hoehe = 40.dp)
                    Wertzeile(
                        t.wallet.zeileBestaetigungen, bestaetigungText(eintrag.height, spitze, t),
                        hoehe = 40.dp, linie = false,
                    )
                }

                // --- Die Kennung ------------------------------------------
                Flaeche(
                    Modifier.padding(start = 16.dp, top = 8.dp, end = 16.dp),
                    innen = PaddingValues(start = 16.dp, top = 12.dp, end = 10.dp, bottom = 12.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                t.wallet.kennungDerUeberweisung,
                                style = Schrift.Klein,
                                color = Farbe.Leise,
                            )
                            KennungInGruppen(eintrag.txid, Modifier.fillMaxWidth())
                        }
                        Symbolknopf(R.drawable.ic_copy_fill, t.wallet.kennungKopieren, {
                            inZwischenablage(ctx, eintrag.txid)
                        })
                    }
                }

                // Im Explorer nachsehen (Wunsch des Besitzers, 11.11.2026).
                // Den Knopf gibt es nur, wenn die Kennung eine ist - siehe
                // `Explorer`.
                val imExplorer = Explorer.ueberweisung(eintrag.txid)
                if (imExplorer != null) {
                    Knopf(
                        t.allgemein.imExplorer,
                        { imBrowserOeffnen(ctx, imExplorer) },
                        Modifier
                            .padding(start = 16.dp, top = 12.dp, end = 16.dp)
                            .fillMaxWidth()
                            .testTag("im_explorer"),
                        art = Knopfart.FLAECHE,
                        symbol = R.drawable.ic_arrow_square_out_fill,
                    )
                    Text(
                        t.wallet.kennungNachschlagen,
                        style = Schrift.Hinweis,
                        color = Farbe.Leise,
                        modifier = Modifier.padding(start = 20.dp, top = 12.dp, end = 20.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

/**
 * Wie oft bestaetigt.
 *
 * Der Block selbst zaehlt als erste Bestaetigung - dieselbe Lesart wie im
 * Sendestatus, sonst zeigten zwei Bildschirme fuer dieselbe Ueberweisung
 * verschiedene Zahlen. Ist die Spitze unbekannt, wird geschwiegen statt
 * geraten.
 */
private fun bestaetigungText(hoehe: Long, spitze: Long?, t: Texte): String {
    if (spitze == null || spitze < hoehe) return t.wallet.hoeheUnbekannt
    return when (val n = spitze - hoehe + 1) {
        1L -> t.wallet.bestaetigungBlockSelbst
        else -> n.toString()
    }
}
