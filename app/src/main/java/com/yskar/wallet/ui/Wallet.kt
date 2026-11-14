package com.yskar.wallet.ui

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.yskar.wallet.R
import com.yskar.wallet.kontakte.Kontakt
import com.yskar.wallet.kontakte.Kontakte
import com.yskar.wallet.netz.Art
import com.yskar.wallet.netz.Betrag
import com.yskar.wallet.netz.Guthabenverlauf
import com.yskar.wallet.netz.Konto
import com.yskar.wallet.netz.VerlaufEintrag
import com.yskar.wallet.sprache.Texte
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val UHR = DateTimeFormatter.ofPattern("HH:mm")

fun zeitText(unixSekunden: Long?, t: Texte): String {
    if (unixSekunden == null) return t.zeit.zeitUnbekannt
    val z = Instant.ofEpochSecond(unixSekunden).atZone(ZoneId.systemDefault())
    val heute = LocalDate.now(ZoneId.systemDefault())
    return when (z.toLocalDate()) {
        heute -> t.wallet.zeitHeute(UHR.format(z))
        heute.minusDays(1) -> t.wallet.zeitGestern(UHR.format(z))
        else -> {
            // Das Datum, wie die Sprache es schreibt: "11.11.2026", "11 Nov 2026".
            val tag = DateTimeFormatter.ofPattern(t.zeit.musterTagJahr, t.locale)
            "${tag.format(z)}, ${UHR.format(z)}"
        }
    }
}

fun artText(art: Art, t: Texte): String = when (art) {
    Art.REWARD -> t.wallet.artBlockertrag
    Art.POOL -> t.wallet.artPoolAnteil
    Art.EIN -> t.wallet.artEmpfangen
    Art.AUS -> t.wallet.artGesendet
    Art.UNBEKANNT -> t.wallet.artUnbekannt
}

/**
 * Was in einer Zeile des Verlaufs vorn steht: "An Anna" statt "Gesendet",
 * wenn der Besitzer der Gegenadresse einen Namen gegeben hat. Blockertrag
 * und Pool-Anteil haben keine Gegenseite und behalten ihr Wort.
 */
fun artMitName(e: VerlaufEintrag, kontakte: List<Kontakt>, t: Texte): String {
    val name = Kontakte.nameFuer(kontakte, e.gegenseite) ?: return artText(e.art, t)
    return when (e.art) {
        Art.EIN -> t.kontakte.von(name)
        Art.AUS -> t.kontakte.an(name)
        else -> artText(e.art, t)
    }
}

/** Vorzeichen und voller Betrag, wie er in einer Zeile des Verlaufs steht. */
private fun betragMitZeichen(e: VerlaufEintrag, t: Texte): String =
    (if (e.art.eingehend) "+" else "−") + Betrag.genau(e.betrag, t.locale)

/**
 * Zeit und, wo es eine gibt, die Notiz - in einer Zeile.
 *
 * Beim Pool-Anteil ist die "Notiz" der Name des Pools, kein Satz von
 * jemandem; er steht in der einzelnen Ueberweisung.
 */
private fun zeitUndNotiz(zeit: String, e: VerlaufEintrag, t: Texte): String =
    if (e.notiz.isEmpty() || e.art == Art.POOL) zeit else "$zeit · ${t.wallet.notizZitat(e.notiz)}"

/**
 * Der Hauptbildschirm.
 *
 * Aufbau von oben nach unten: Zeichen und Name ganz klein, dann das
 * Guthaben, dann die vier runden Aktionen, dann die letzten Zahlungen.
 *
 * Seit dem 12.11.2026 ("Einfacher", UMBAU.md) stehen hier weder die eigene
 * Adresse noch das Diagramm: Die Adresse gehoert zu "Empfangen", das
 * Diagramm in den Verlauf. [kurve], [zeitraum] und [onZeitraum] bleiben
 * als Parameter, damit die Aufrufer bleiben koennen, und werden hier nicht
 * mehr gebraucht.
 *
 * Was hier NICHT steht, ist ebenso Absicht: keine Blockhoehe, keine Nonce,
 * keine Fassung, kein Byte. Das sind Dinge fuer den Pruefschritt und den
 * Selbsttest. Der Hauptbildschirm beantwortet genau zwei Fragen - wie viel
 * habe ich, und was ist zuletzt passiert.
 *
 * Vorlage: design-neu/html/04_wallet.html und 05_wallet_leer.html.
 *
 * [kurve] ist der Guthabenverlauf fuer [zeitraum], fertig gerechnet
 * (`netz/Guthabenverlauf.kt`). Dieser Bildschirm rechnet nichts davon.
 *
 * [bekanntesGuthaben] und [bekanntSeit] sind der zuletzt bekannte Stand.
 * Solange [konto] fehlt - gleich nach dem Entsperren, oder ohne Netz -,
 * steht diese Zahl auf der Karte, beschriftet mit ihrem Alter. Gesendet
 * wird damit nicht: "Senden" bleibt aus, bis ein frisches [konto] da ist.
 */
@Composable
fun WalletScreen(
    adresse: String,
    konto: Konto?,
    laedt: Boolean,
    netzMeldung: String?,
    hinweis: String?,
    onAktualisieren: () -> Unit,
    onSenden: () -> Unit,
    onEmpfangen: () -> Unit,
    onVerlauf: () -> Unit,
    onEinstellungen: () -> Unit,
    onSperren: () -> Unit,
    onBewegung: (VerlaufEintrag) -> Unit,
    modifier: Modifier = Modifier,
    verborgen: Boolean = false,
    bekanntesGuthaben: Long? = null,
    bekanntSeit: Long? = null,
    onVerbergen: (Boolean) -> Unit = {},
    kontakte: List<Kontakt> = emptyList(),
    kurve: Guthabenverlauf.Ergebnis? = null,
    zeitraum: Guthabenverlauf.Zeitraum = Guthabenverlauf.Zeitraum.TAGE_30,
    onZeitraum: (Guthabenverlauf.Zeitraum) -> Unit = {},
) {
    val ctx = LocalContext.current
    val t = LocalTexte.current
    val zone = ZoneId.systemDefault()

    val guthaben = konto?.guthaben ?: bekanntesGuthaben
    val stand = standText(bekanntSeit, LocalDate.now(zone), zone, t)

    Column(modifier.fillMaxSize()) {
        // --- Kopf -------------------------------------------------------
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, top = 16.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ZeichenRuhig(groesse = 28.dp)
            Spacer(Modifier.width(10.dp))
            Text(
                "YSKAR",
                style = Schrift.Schriftzug.copy(fontSize = 15.sp, letterSpacing = 0.10.em),
                color = Farbe.Schrift,
                modifier = Modifier.weight(1f),
            )
            // Das Auge ist ein Schalter. Seine Beschreibung nennt, was der
            // naechste Tipp TUT, nicht, was gerade gilt.
            Symbolknopf(
                R.drawable.ic_eye_fill,
                if (verborgen) t.wallet.guthabenZeigen else t.wallet.guthabenVerbergen,
                { onVerbergen(!verborgen) },
                betont = verborgen,
            )
            Spacer(Modifier.width(8.dp))
            Symbolknopf(R.drawable.ic_lock_key_fill, t.wallet.sperren, onSperren)
        }

        // Alles unter dem Kopf rollt als EIN Stueck. Auf diesem Geraet passt
        // es; mit dem Diagramm ist der feste Teil aber so hoch geworden, dass
        // auf einem kleineren Telefon fuer "Zuletzt" nichts mehr uebrig
        // bliebe, wenn nur die Liste rollen duerfte.
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
        ) {
            if (hinweis != null) {
                Hinweisstreifen(hinweis, Modifier.padding(start = 16.dp, top = 10.dp, end = 16.dp))
            }
            if (netzMeldung != null) {
                Warnhinweis(netzMeldung, Modifier.padding(start = 16.dp, top = 10.dp, end = 16.dp))
            }

            // --- Guthabenkarte ----------------------------------------------
            Kartenkopf(
                Modifier.padding(start = 2.dp, top = 6.dp, end = 2.dp).fillMaxWidth(),
            ) {
                Column(
                    // Oben 7 statt 20 dp und rechts 14: Die Zeile mit dem Stand
                    // ist 44 dp hoch, damit der Finger sie trifft, und hat
                    // selbst 6 dp Rand. So steht die Schrift trotzdem dort, wo
                    // die Vorlage sie zeichnet.
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, top = 7.dp, end = 14.dp, bottom = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            t.wallet.guthaben,
                            style = Schrift.Auftakt,
                            color = Farbe.Lampe,
                            modifier = Modifier.weight(1f),
                        )
                        // Der Stand IST der Knopf zum Aktualisieren. Solange
                        // nichts bekannt ist, steht dort das Wort selbst.
                        Row(
                            Modifier
                                .heightIn(min = 44.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable(
                                    enabled = !laedt,
                                    role = Role.Button,
                                    onClickLabel = t.wallet.aktualisieren,
                                    onClick = onAktualisieren,
                                )
                                .padding(horizontal = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Symbol(R.drawable.ic_arrows_clockwise_bold, 14.dp, farbe = Farbe.Weich)
                            Text(
                                when {
                                    laedt -> t.wallet.laedt
                                    else -> stand ?: t.wallet.aktualisieren
                                },
                                style = Schrift.Hinweis.copy(fontSize = 12.sp),
                                color = Farbe.Weich,
                            )
                        }
                    }

                    if (guthaben == null && laedt) {
                        // Noch nie etwas gehoert und das Netz ist gefragt: ein
                        // Balken in der Groesse der Zahl, die gleich kommt.
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Platzhalter(168.dp, 46.dp, hell = true)
                            Einheit(Modifier.padding(start = 10.dp))
                        }
                    } else {
                        GuthabenZahl(
                            guthaben,
                            Modifier.fillMaxWidth().padding(end = 6.dp),
                            verborgen = verborgen,
                        )
                    }
                }
            }

            // --- Wartendes --------------------------------------------------
            if (konto != null && konto.wartend.isNotEmpty()) {
                Hinweisstreifen(
                    t.wallet.zahlungWartet(konto.wartend.size),
                    Modifier.padding(start = 16.dp, top = 12.dp, end = 16.dp),
                    vorne = { Zeichen(Teil.AN, Teil.HALB, Teil.AUS, groesse = 22.dp) },
                )
            }

            // --- Aktionen ---------------------------------------------------
            Row(
                // Vier gleich breite Spalten: So hat jede Beschriftung ihr
                // Viertel und drueckt keine andere zur Seite - bei grosser
                // Systemschrift standen sie sonst aneinander.
                Modifier.fillMaxWidth().padding(start = 10.dp, top = 16.dp, end = 10.dp),
            ) {
                val spalte = Modifier.weight(1f)
                RunderKnopf(
                    R.drawable.ic_arrow_up_right_bold, t.wallet.knopfSenden, onSenden,
                    modifier = spalte,
                    hervorgehoben = true,
                    // Nur mit einem FRISCHEN Konto. Der zuletzt bekannte Stand
                    // reicht zum Anzeigen, nicht zum Senden.
                    aktiv = konto != null && konto.guthaben > 0,
                )
                RunderKnopf(
                    R.drawable.ic_arrow_down_left_bold, t.wallet.knopfEmpfangen, onEmpfangen,
                    modifier = spalte,
                )
                RunderKnopf(
                    R.drawable.ic_clock_counter_clockwise_fill, t.wallet.knopfVerlauf, onVerlauf,
                    modifier = spalte,
                    aktiv = konto != null && konto.verlauf.isNotEmpty(),
                )
                RunderKnopf(
                    R.drawable.ic_gear_six_fill, t.wallet.knopfEinstellungen, onEinstellungen,
                    modifier = spalte,
                )
            }

            // --- Bewegungen -------------------------------------------------
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 32.dp)
                    .padding(start = 20.dp, top = 26.dp, end = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    t.wallet.zuletzt,
                    style = Schrift.Mittel.copy(fontSize = 19.sp, fontWeight = FontWeight.Medium),
                    color = Farbe.Schrift,
                    modifier = Modifier.weight(1f),
                )
                when {
                    konto == null && laedt -> Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Symbol(R.drawable.ic_arrows_clockwise_bold, 14.dp, farbe = Farbe.Leise)
                        Text(t.wallet.laedt, style = Schrift.Hinweis, color = Farbe.Leise)
                    }

                    konto != null && konto.verlauf.isNotEmpty() -> Row(
                        Modifier
                            .heightIn(min = 32.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable(role = Role.Button, onClick = onVerlauf)
                            .padding(start = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(t.wallet.alleAnzeigen, style = Schrift.Schild, color = Farbe.Verweis)
                        Symbol(R.drawable.ic_caret_right_bold, 15.dp, farbe = Farbe.Verweis)
                    }
                }
            }

            when {
                konto == null && laedt -> Column(Modifier.padding(horizontal = 20.dp)) {
                    for (i in 0 until 4) Platzhalterzeile(linie = i < 3)
                }

                konto == null -> LeerText(
                    t.wallet.keineDaten,
                    t.wallet.keineDatenRat,
                )

                konto.verlauf.isEmpty() -> LeerText(
                    t.wallet.nichtsPassiert,
                    t.wallet.nichtsPassiertRat,
                )

                else -> {
                    val letzte = konto.verlauf.take(7)
                    Column(Modifier.padding(horizontal = 20.dp)) {
                        for ((i, e) in letzte.withIndex()) {
                            Verlaufszeile(
                                art = artMitName(e, kontakte, t),
                                zeit = zeitUndNotiz(zeitText(e.zeit, t), e, t),
                                // Verborgen heisst: keine Zahl. Auch kein
                                // Vorzeichen - "+••••" verriete die Richtung
                                // nicht mehr als der Punkt davor, aber es saehe
                                // aus wie ein halb verdeckter Betrag.
                                betrag = if (verborgen) "••••" else betragMitZeichen(e, t),
                                eingehend = e.art.eingehend,
                                linie = i < letzte.lastIndex,
                                onKlick = { onBewegung(e) },
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun LeerText(oben: String, unten: String) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 22.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(oben, style = Schrift.Text, color = Farbe.Schrift)
        Text(unten, style = Schrift.Hinweis, color = Farbe.Leise)
    }
}

/**
 * Eine Zeile des Verlaufs, die noch laedt: Punkt, zwei Balken links, einer
 * rechts - in den Massen der Zeile, die gleich kommt.
 */
@Composable
private fun Platzhalterzeile(linie: Boolean) {
    Row(
        Modifier
            .fillMaxWidth()
            .drawWithContent {
                drawContent()
                if (linie) {
                    val y = size.height - 0.5.dp.toPx()
                    drawLine(Farbe.Linie, Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
                }
            }
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Platzhalter(40.dp, 40.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Platzhalter(92.dp, 12.dp)
            Platzhalter(140.dp, 10.dp)
        }
        Platzhalter(64.dp, 14.dp)
    }
}

/**
 * Der ganze Verlauf, nach Tagen gruppiert.
 *
 * Oben das Diagramm des Guthabens (seit dem 12.11.2026 hier statt auf dem
 * Hauptbildschirm), darunter die Zahlungen, nach Tagen gruppiert und durch
 * Linien getrennt. Die Liste baut nur auf, was zu sehen ist.
 *
 * Die Marke oben rechts zaehlt, was GELADEN ist, solange [kannNachladen]
 * gilt - erst danach sind es "Eintraege" (`zaehlerText`).
 */
@Composable
fun VerlaufScreen(
    eintraege: List<VerlaufEintrag>,
    laedt: Boolean,
    kannNachladen: Boolean,
    onNachladen: () -> Unit,
    onZurueck: () -> Unit,
    onBewegung: (VerlaufEintrag) -> Unit,
    modifier: Modifier = Modifier,
    kontakte: List<Kontakt> = emptyList(),
    // Das Diagramm. Ohne [kurve] (kein frisches Konto) steht es nicht da.
    kurve: Guthabenverlauf.Ergebnis? = null,
    zeitraum: Guthabenverlauf.Zeitraum = Guthabenverlauf.Zeitraum.TAGE_30,
    onZeitraum: (Guthabenverlauf.Zeitraum) -> Unit = {},
    verborgen: Boolean = false,
) {
    val t = LocalTexte.current
    val zone = ZoneId.systemDefault()
    val heute = LocalDate.now(zone)
    val gruppen = remember(eintraege, heute, zone) { nachTagen(eintraege, heute, zone) }

    Column(modifier.fillMaxSize()) {
        Kopfzeile(t.wallet.verlaufTitel, onZurueck = onZurueck) {
            Marke(zaehlerText(eintraege.size, vollstaendig = !kannNachladen, t = t))
        }

        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 20.dp),
        ) {
            if (kurve != null) {
                item {
                    Kurvenkarte(
                        kurve = kurve,
                        zeitraum = zeitraum,
                        onZeitraum = onZeitraum,
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 6.dp),
                        verborgen = verborgen,
                    )
                }
            }
            for ((gruppe, liste) in gruppen) {
                item {
                    Text(
                        gruppe.titel(t),
                        style = Schrift.Schild,
                        color = Farbe.Leise,
                        modifier = Modifier.padding(start = 4.dp, top = 16.dp, bottom = 4.dp),
                    )
                }
                itemsIndexed(liste) { i, e ->
                    Verlaufszeile(
                        art = artMitName(e, kontakte, t),
                        zeit = zeitUndNotiz(zeitInGruppe(e.zeit, gruppe, zone, t), e, t),
                        betrag = if (verborgen) "••••" else betragMitZeichen(e, t),
                        eingehend = e.art.eingehend,
                        modifier = Modifier.padding(horizontal = 4.dp),
                        linie = i < liste.lastIndex,
                        onKlick = { onBewegung(e) },
                    )
                }
            }
            item {
                if (kannNachladen) {
                    Knopf(
                        if (laedt) t.wallet.laedt else t.wallet.mehrLaden,
                        onNachladen,
                        Modifier.fillMaxWidth().padding(top = 16.dp),
                        art = Knopfart.FLAECHE,
                        symbol = R.drawable.ic_arrows_clockwise_bold,
                        aktiv = !laedt,
                    )
                } else {
                    Text(
                        t.wallet.verlaufAnfang,
                        style = Schrift.Hinweis,
                        color = Farbe.Leise,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 22.dp),
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

/**
 * Empfangen.
 *
 * Der QR-Code ist die Hauptsache und bekommt eine weisse Karte - Kameras
 * lesen dunkel auf hell besser, und die helle Flaeche sagt ausserdem von
 * selbst "halt das hier hin". Darunter die Adresse, vollstaendig und in
 * Vierergruppen, fuer alle, die lieber vergleichen als scannen.
 *
 * Vorlage: design-neu/html/06_empfangen.html.
 */
@Composable
fun EmpfangenScreen(
    adresse: String,
    onZurueck: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ctx = LocalContext.current
    val t = LocalTexte.current

    Blatt(modifier) {
        Kopfzeile(t.wallet.empfangenTitel, onZurueck = onZurueck)

        Kartenkopf(
            Modifier.padding(start = 16.dp, top = 6.dp, end = 16.dp).fillMaxWidth(),
            rundung = 32.dp,
            muster = 0.10f,
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, top = 26.dp, end = 24.dp, bottom = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                QrKarte(adresse, Modifier.widthIn(max = 288.dp).fillMaxWidth())

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(t.wallet.deineAdresse, style = Schrift.Klein, color = Farbe.Weich)
                    Box(
                        Modifier
                            .widthIn(max = 288.dp)
                            .fillMaxWidth()
                            .gefasst(
                                16.dp, SolidColor(Color.Black.copy(alpha = 0.30f)),
                                Farbe.Linie, 0f,
                            )
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                    ) {
                        AdresseInGruppen(
                            adresse,
                            Modifier.fillMaxWidth(),
                            stil = Schrift.AdresseKlein.copy(fontSize = 14.sp),
                        )
                    }
                }
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, top = 12.dp, end = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Knopf(
                t.wallet.kopieren, { inZwischenablage(ctx, adresse) }, Modifier.weight(1f),
                art = Knopfart.FLAECHE, symbol = R.drawable.ic_copy_fill,
            )
            Knopf(
                t.wallet.teilen, { adresseTeilen(ctx, adresse, t.wallet.adresseTeilen) },
                Modifier.weight(1f),
                art = Knopfart.FLAECHE, symbol = R.drawable.ic_export_fill,
            )
        }

        Row(
            Modifier.padding(start = 28.dp, top = 16.dp, end = 28.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Symbol(R.drawable.ic_shield_check_fill, 18.dp, farbe = Farbe.Leise)
            Text(
                t.wallet.adresseOeffentlich,
                style = Schrift.Klein,
                color = Farbe.Leise,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/**
 * Die Adresse ueber das Teilen-Blatt von Android weitergeben.
 *
 * Geteilt wird die nackte Adresse als Text - kein Schema, kein Betrag,
 * nichts sonst. Sie ist oeffentlich; die App, die der Besitzer im Blatt
 * waehlt, bekommt sie zu sehen, und genau das will er in diesem Augenblick.
 * [titel] steht ueber dem Blatt, in der Sprache der App.
 */
private fun adresseTeilen(ctx: Context, adresse: String, titel: String) {
    val absicht = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, adresse)
    }
    // Ohne eine App, die Text annimmt, gaebe startActivity eine Ausnahme.
    runCatching { ctx.startActivity(Intent.createChooser(absicht, titel)) }
}
