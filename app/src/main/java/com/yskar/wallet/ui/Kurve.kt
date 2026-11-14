package com.yskar.wallet.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yskar.wallet.netz.Betrag
import com.yskar.wallet.netz.Guthabenverlauf
import com.yskar.wallet.netz.Guthabenverlauf.Zeitraum
import java.time.LocalDate
import java.time.ZoneId

/**
 * Die Karte "Guthaben · 30 Tage" auf dem Hauptbildschirm.
 *
 * Vorlage: design-neu/html/04_wallet.html (mit Linie) und
 * 05_wallet_leer.html (Platzhalter). Titel, rechts die Wahl des Zeitraums,
 * darunter die Stufenlinie, darunter die Summen.
 *
 * DIE KARTE ZEICHNET NUR AB. Was die Linie zeigt, hat
 * [Guthabenverlauf.rechne] gerechnet; wo ihre Punkte liegen und was
 * daruebersteht, bestimmt `Kurvenform.kt`. Beides ist ohne Geraet geprueft.
 *
 * Fuenf Zustaende:
 *
 *   [kurve] ist null      Es gibt noch nichts zu zeigen (das Netz ist
 *                         gefragt, oder es hat nie geantwortet). Ein
 *                         stiller Balken an Stelle der Linie.
 *   [verborgen]           Das Auge ist gedrueckt. Die Karte bleibt stehen,
 *                         damit der Bildschirm nicht springt; Linie und
 *                         Summen sind weg.
 *   nicht zeichenbar      Der geladene Verlauf erklaert das Guthaben nicht.
 *                         Dann steht ein Satz da und KEINE Linie.
 *   teilweise belegt      Die Linie beginnt dort, wo sie belegt ist. Der
 *                         Titel sagt, ab wann.
 *   belegt                wie in der Vorlage.
 *
 * Die Wahl des Zeitraums ist 36 dp hoch statt der 28 der Vorlage: Ein Ziel
 * von 28 dp trifft der Daumen nicht verlaesslich. Die Pille darin hat die
 * 28 dp der Zeichnung.
 */
@Composable
fun Kurvenkarte(
    kurve: Guthabenverlauf.Ergebnis?,
    zeitraum: Zeitraum,
    onZeitraum: (Zeitraum) -> Unit,
    modifier: Modifier = Modifier,
    verborgen: Boolean = false,
) {
    val zone = ZoneId.systemDefault()
    val heute = LocalDate.now(zone)
    val t = LocalTexte.current

    Flaeche(
        modifier,
        innen = PaddingValues(start = 16.dp, top = 8.dp, end = 8.dp, bottom = 12.dp),
        abstand = 0.dp,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                kurventitel(kurve, zeitraum, heute, zone, t),
                style = Schrift.Schild,
                color = Farbe.Leise,
                modifier = Modifier.weight(1f),
            )
            Row(
                Modifier.selectableGroup(),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                for (z in Zeitraum.entries) {
                    Zeitwahl(z, gewaehlt = z == zeitraum, onKlick = { onZeitraum(z) })
                }
            }
        }

        Spacer(Modifier.height(2.dp))

        val feld = Modifier.fillMaxWidth().padding(end = 8.dp).height(62.dp)
        when {
            kurve == null -> Balken(feld)

            verborgen -> Balken(
                feld.semantics { contentDescription = t.wallet.kurveVerborgen },
            )

            !kurve.zeichenbar -> Box(feld, contentAlignment = Alignment.CenterStart) {
                Text(
                    t.wallet.kurveUnstimmig,
                    style = Schrift.Hinweis,
                    color = Farbe.Weich,
                )
            }

            else -> Linie(
                kurve,
                feld.semantics {
                    contentDescription = kurvenbeschreibung(kurve, zeitraum, heute, zone, t)
                },
            )
        }

        Spacer(Modifier.height(8.dp))

        // Zwei Summen nebeneinander, wenn sie nebeneinander passen. Bei
        // grossen Betraegen oder grosser Schrift rueckt die zweite in die
        // naechste Zeile - gekuerzt wird keine (REGELN.md, Regel 11).
        FlowRow(
            Modifier.fillMaxWidth().padding(end = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            when {
                kurve == null -> {
                    Platzhalter(120.dp, 10.dp, Modifier.padding(vertical = 4.dp))
                    Platzhalter(104.dp, 10.dp, Modifier.padding(vertical = 4.dp))
                }

                // Unstimmig: Dann gelten auch die Summen nicht.
                !kurve.zeichenbar -> Unit

                else -> {
                    Summe(
                        t.wallet.summeEingang,
                        if (verborgen) null else "+" + Betrag.genau(kurve.eingang, t.locale),
                        Farbe.Eingang,
                    )
                    Summe(
                        t.wallet.summeAusgang,
                        if (verborgen) null else "−" + Betrag.genau(kurve.ausgang, t.locale),
                        Farbe.Schrift,
                    )
                }
            }
        }
    }
}

/** Eine der drei Wahlen "7 T", "30 T", "1 J". */
@Composable
private fun Zeitwahl(z: Zeitraum, gewaehlt: Boolean, onKlick: () -> Unit) {
    val t = LocalTexte.current
    Box(
        Modifier
            .heightIn(min = 36.dp)
            .widthIn(min = 44.dp)
            .clip(RoundedCornerShape(18.dp))
            .selectable(selected = gewaehlt, role = Role.RadioButton, onClick = onKlick)
            // Vorgelesen wird "30 Tage", nicht "30 T".
            .clearAndSetSemantics { contentDescription = zeitraumWort(z, t) },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .heightIn(min = 28.dp)
                .background(
                    if (gewaehlt) Farbe.hauch(0.10f) else Color.Transparent,
                    RoundedCornerShape(14.dp),
                )
                .padding(horizontal = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                zeitraumKurz(z, t),
                style = Schrift.Hinweis.copy(
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    fontWeight = if (gewaehlt) FontWeight.SemiBold else FontWeight.Medium,
                ),
                color = if (gewaehlt) Farbe.Schrift else Farbe.Leise,
                maxLines = 1,
            )
        }
    }
}

/** Der stille Balken an Stelle der Linie - wie die Platzhalter anderswo. */
@Composable
private fun Balken(modifier: Modifier) {
    Box(
        modifier.background(
            Brush.horizontalGradient(
                listOf(
                    Farbe.hauch(0.05f),
                    Farbe.hauch(0.10f),
                    Farbe.hauch(0.05f),
                ),
            ),
            RoundedCornerShape(14.dp),
        ),
    )
}

/**
 * Eine Summe: das Wort leise, die Zahl in [farbe], Nachkommastellen
 * zurueckgenommen. Ohne [wert] - verborgen - stehen vier Punkte da.
 */
@Composable
private fun Summe(name: String, wert: String?, farbe: Color) {
    val locale = LocalTexte.current.locale
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(name, style = Schrift.Hinweis, color = Farbe.Leise)
        Spacer(Modifier.width(5.dp))
        if (wert == null) {
            Text("••••", style = Schrift.Betrag.copy(fontSize = 13.sp), color = Farbe.Leise)
        } else {
            // Einzeilig und vollstaendig: Wird der Platz knapp, wird die
            // Schrift kleiner, nicht die Zahl kuerzer.
            Text(
                betragText(wert, farbe, locale),
                style = Schrift.Betrag.copy(fontSize = 13.sp, lineHeight = 18.sp),
                maxLines = 1,
                softWrap = false,
                autoSize = TextAutoSize.StepBased(
                    minFontSize = 8.sp,
                    maxFontSize = 13.sp,
                    stepSize = 0.5.sp,
                ),
            )
        }
    }
}

/**
 * Die Stufenlinie mit den drei Hilfslinien und dem Punkt fuer heute.
 *
 * Masse aus der Vorlage (326 x 62): Hilfslinien bei 10, 31 und 52; die
 * Linie laeuft zwischen 8 und 54; darunter eine Flaeche im selben Blau,
 * nach unten ausgeblendet; Strich 2, Punkt 3,5 mit einem Hof von 6,5 in der
 * Farbe des Grunds. Der Punkt sitzt genau auf dem rechten Rand und
 * ragt zur Haelfte hinaus - dafuer laesst die Karte rechts Platz.
 */
@Composable
private fun Linie(kurve: Guthabenverlauf.Ergebnis, modifier: Modifier) {
    val punkte = remember(kurve) { kurvenpunkte(kurve) }

    Canvas(modifier) {
        val w = size.width
        val oben = 8.dp.toPx()
        val unten = 54.dp.toPx()

        for (y in listOf(10.dp, 31.dp, 52.dp)) {
            drawLine(
                color = Farbe.hauch(0.05f),
                start = Offset(0f, y.toPx()),
                end = Offset(w, y.toPx()),
                strokeWidth = 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 5.dp.toPx())),
            )
        }

        if (punkte.isEmpty()) return@Canvas
        fun ort(p: Kurvenpunkt) = Offset(p.x * w, unten - p.y * (unten - oben))

        if (punkte.size > 1) {
            val pfad = Path()
            val erster = ort(punkte.first())
            var hoechster = erster.y
            pfad.moveTo(erster.x, erster.y)
            for (p in punkte.drop(1)) {
                val o = ort(p)
                pfad.lineTo(o.x, o.y)
                hoechster = minOf(hoechster, o.y)
            }

            // Die Flaeche unter der Linie: dasselbe Blau, von 34 % am hoechsten
            // Punkt der Linie bis nichts am unteren Rand. Sie beginnt und
            // endet, wo die Linie beginnt und endet.
            val flaeche = Path().apply {
                addPath(pfad)
                lineTo(ort(punkte.last()).x, size.height)
                lineTo(erster.x, size.height)
                close()
            }
            drawPath(
                flaeche,
                brush = Brush.verticalGradient(
                    0f to YskarFarben.Blau.copy(alpha = 0.34f),
                    1f to YskarFarben.Blau.copy(alpha = 0f),
                    startY = hoechster,
                    endY = size.height,
                ),
            )
            drawPath(
                pfad,
                brush = Brush.horizontalGradient(
                    0f to YskarFarben.BlauTief,
                    0.6f to YskarFarben.Blau,
                    1f to YskarFarben.BlauHell,
                    startX = 0f,
                    endX = w,
                ),
                style = Stroke(
                    width = 2.dp.toPx(),
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round,
                ),
            )
        }

        val heute = ort(punkte.last())
        drawCircle(Farbe.Grund, radius = 6.5.dp.toPx(), center = heute)
        drawCircle(YskarFarben.BlauHell, radius = 3.5.dp.toPx(), center = heute)
    }
}
