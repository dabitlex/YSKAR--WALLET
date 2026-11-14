package com.yskar.wallet.ui

import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.yskar.wallet.R
import kotlinx.coroutines.launch

/**
 * Die Einfuehrung: acht Karten, einmal nach dem Einrichten und danach unter
 * "Einstellungen".
 *
 * Vorlage: design-neu/html/einfuehrung_1…8.html und DESIGN-NEU.md,
 * Abschnitt 9. Jede Karte hat oben den Fortschritt, "N von 8" und
 * "Überspringen", darunter ein Bild, eine zweizeilige Ueberschrift, einen
 * Absatz, und unten den Zurueck-Kreis und den Hauptknopf.
 *
 * KOPF UND FUSS STEHEN FEST, die Karte dazwischen laesst sich wischen. In
 * der Vorlage ist jede Karte ein eigenes Blatt mit eigenem Kopf; hier
 * wechselt nur, was sich aendert. Wischen steht nicht in der Vorlage - aber
 * wer acht Karten sieht, wischt. NICHTS LAEUFT VON ALLEIN.
 *
 * Was die Karten sagen, steht in `Einfuehrungstexte.kt` und, je Sprache, in
 * `sprache/EinfuehrungTexte.kt`. Hier steht nur, wie sie aussehen.
 *
 * [adresse] ist die eigene Adresse. Auf Karte 2 steht sie in der Marke
 * unter dem Bild - nicht die Beispieladresse der Vorlage: Eine fremde
 * Adresse neben dem Satz "schick deine Adresse" waere eine Einladung zu
 * einer Verwechslung. Fehlt sie, fehlt die Marke.
 *
 * [start] ist die Karte, mit der es beginnt (0 bis 7) - fuer den
 * Testrahmen, der jede Karte einzeln aufnimmt.
 */
@Composable
fun EinfuehrungScreen(
    adresse: String?,
    onFertig: () -> Unit,
    modifier: Modifier = Modifier,
    start: Int = 0,
) {
    val t = LocalTexte.current
    val karten = remember(t) { einfuehrungskarten(t) }
    val seiten = rememberPagerState(initialPage = start.coerceIn(0, karten.lastIndex)) { karten.size }
    val scope = rememberCoroutineScope()
    val karte = seiten.currentPage

    fun gehe(zu: Int) {
        scope.launch { seiten.animateScrollToPage(zu) }
    }

    // Zurueck heisst: eine Karte zurueck. Auf der ersten ist die Einfuehrung
    // damit zu Ende - wie "Überspringen".
    BackHandler { if (karte > 0) gehe(karte - 1) else onFertig() }

    Box(
        modifier
            .fillMaxSize()
            // Unter den Fotos ein ruhiger, dunkler Grund - bis hinter Status-
            // und Navigationsleiste. Der helle Himmel des App-Grunds stuende
            // sonst als blauer Streifen ueber jedem Bild.
            .drawBehind {
                val mehr = 400.dp.toPx()
                drawRect(
                    Farbe.Grund,
                    topLeft = Offset(0f, -mehr),
                    size = Size(size.width, size.height + 2 * mehr),
                )
            },
    ) {
        HorizontalPager(seiten, Modifier.fillMaxSize()) { n ->
            Karte(n, karten[n], adresse)
        }

        // --- Kopf: Fortschritt, Zaehler, Überspringen --------------------
        Column(
            Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, top = 18.dp, end = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clearAndSetSemantics {
                        contentDescription = kartenansage(karte, karten.size, t)
                    },
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                for (i in karten.indices) {
                    Box(
                        Modifier
                            .weight(1f)
                            .height(3.dp)
                            .background(
                                when {
                                    i < karte -> Farbe.Schrift
                                    i == karte -> YskarFarben.BlauHell
                                    else -> Farbe.hauch(0.16f)
                                },
                                RoundedCornerShape(2.dp),
                            ),
                    )
                }
            }
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    kartenzaehler(karte, karten.size, t),
                    style = Schrift.Klein,
                    color = Farbe.Weich,
                )
                Box(
                    Modifier
                        .heightIn(min = 44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(role = Role.Button, onClick = onFertig)
                        .padding(start = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        t.einfuehrung.ueberspringen,
                        style = Schrift.Name.copy(fontWeight = FontWeight.Medium),
                        color = Farbe.Schrift,
                    )
                }
            }
        }

        // --- Fuss: Zurueck-Kreis und Hauptknopf --------------------------
        Row(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, bottom = FUSS_UNTEN),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (karte > 0) {
                Box(
                    Modifier
                        .size(FUSS_KNOPF)
                        .clip(CircleShape)
                        .gefasst(
                            FUSS_KNOPF / 2, SolidColor(Farbe.hauch(0.04f)),
                            Farbe.Linie, 0f,
                        )
                        .clickable(role = Role.Button) { gehe(karte - 1) }
                        .semantics { contentDescription = t.einfuehrung.vorigeKarte },
                    contentAlignment = Alignment.Center,
                ) { Symbol(R.drawable.ic_caret_left_bold, 19.dp) }
            } else {
                // Der Platz bleibt, damit der Hauptknopf auf jeder Karte an
                // derselben Stelle sitzt.
                Spacer(Modifier.size(FUSS_KNOPF))
            }

            val letzte = karte == karten.lastIndex
            Weiterknopf(
                text = karten[karte].knopf,
                pfeil = !letzte,
                onKlick = { if (letzte) onFertig() else gehe(karte + 1) },
            )
        }
    }
}

private val FUSS_KNOPF = 56.dp
private val FUSS_UNTEN = 24.dp

/** Wie hoch der feste Kopf ist: 18 Rand, 3 Fortschritt, 4 Luft, 44 Zeile. */
private val KOPF_HOEHE = 69.dp

/** So hoch ist das Feld mit dem Bild in der Vorlage. */
private val BILD_HOEHE = 372.dp

/** Der Hauptknopf der Einfuehrung: 56 hoch, ganz rund, der Pfeil HINTER dem Wort. */
@Composable
private fun RowScope.Weiterknopf(text: String, pfeil: Boolean, onKlick: () -> Unit) {
    val rundung = FUSS_KNOPF / 2
    Row(
        Modifier
            .weight(1f)
            .heightIn(min = FUSS_KNOPF)
            .schein(Farbe.LampenSchein, rundung, 10.dp, 30.dp, 12.dp)
            .gefasst(rundung, Farbe.Hauptknopf, Color.Transparent)
            .clickable(role = Role.Button, onClick = onKlick)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = Schrift.Titel, color = Farbe.LampenTinte)
        if (pfeil) Symbol(R.drawable.ic_caret_right_bold, 18.dp, farbe = Farbe.LampenTinte)
    }
}

// ---------------------------------------------------------------- Die Karte

/**
 * Eine Karte ohne Kopf und Fuss: das Bild mit dem, was darueber schwebt,
 * die Ueberschrift, der Absatz.
 *
 * DAS BILD IST EINE ZEICHNUNG, KEIN TEXT. Alles, was auf ihm schwebt -
 * Marken, die kleine Guthabenkarte, das kleine Diagramm -, behaelt seine
 * Groesse, auch wenn die Systemschrift vergroessert ist: Es sitzt an festen
 * Stellen ueber einem Foto und wuerde sonst uebereinanderlaufen. Was
 * mitwaechst, ist das, was gelesen werden soll: Ueberschrift und Absatz. Der
 * Absatz rollt, wenn er dann nicht mehr passt.
 */
@Composable
private fun Karte(n: Int, karte: Kartentext, adresse: String?) {
    val dichte = LocalDensity.current
    val t = LocalTexte.current
    val e = t.einfuehrung

    BoxWithConstraints(Modifier.fillMaxSize()) {
        // Das Foto ist quadratisch und 480/390 der Breite breit - es ragt
        // links und rechts ueber den Bildschirm.
        val foto = maxWidth * (480f / 390f)

        CompositionLocalProvider(LocalDensity provides Density(dichte.density, 1f)) {
            if (n == 3) {
                // Karte 4 hat kein Foto, sondern das Linienmuster des
                // Einstiegs und darin das Y in drei Stufen.
                Musterhimmel(hoehe = 470.dp, deckkraft = 0.08f)
            } else {
                Box(
                    Modifier
                        .align(Alignment.TopCenter)
                        .offset(y = 22.dp)
                        .requiredSize(foto),
                ) {
                    Foto(FOTOS[n]!!, karte.bild)
                    when (n) {
                        1 -> Muenzen(foto, MUENZEN_EMPFANGEN, BOGEN_EMPFANGEN)
                        2 -> Muenzen(foto, MUENZEN_SENDEN, BOGEN_SENDEN)
                    }
                }
            }

            Box(
                Modifier
                    .padding(top = KOPF_HOEHE)
                    .fillMaxWidth()
                    .height(BILD_HOEHE),
            ) {
                when (n) {
                    0 -> Guthabenkaertchen(Modifier.offset(16.dp, 269.dp))
                    1 -> {
                        Schwebemarke(Modifier.offset(16.dp, 25.dp)) {
                            Punkt(Farbe.Eingang, 8.dp)
                            Text(e.markeEmpfangen, style = MARKE, color = Farbe.Schrift)
                            Text(
                                betragText(e.beispielEmpfangen, Farbe.Eingang, t.locale, 0.6f),
                                style = MARKE,
                            )
                        }
                        if (adresse != null) {
                            Schwebemarke(Modifier.align(Alignment.TopCenter).offset(y = 323.dp)) {
                                Punkt(YskarFarben.BlauHell, 6.dp)
                                Text(
                                    adresse.take(8),
                                    style = Schrift.Kurz.copy(fontWeight = FontWeight.Medium),
                                    color = Farbe.Schrift,
                                )
                                Text("····", style = Schrift.Kurz, color = Farbe.Leise)
                                Text(
                                    adresse.takeLast(6),
                                    style = Schrift.Kurz.copy(fontWeight = FontWeight.Medium),
                                    color = Farbe.Schrift,
                                )
                            }
                        }
                    }

                    2 -> Schwebemarke(Modifier.align(Alignment.TopEnd).offset((-16).dp, 25.dp)) {
                        Zeichen(Teil.AN, Teil.AUS, Teil.AUS, groesse = 20.dp)
                        Text(e.markeAngenommen, style = MARKE, color = Farbe.Schrift)
                    }

                    3 -> DreiStufen(Modifier.align(Alignment.Center))

                    4 -> {
                        Schwebemarke(Modifier.offset(16.dp, 23.dp)) {
                            Punkt(Farbe.Eingang, 8.dp)
                            Text(e.markeEmpfangen, style = MARKE, color = Farbe.Schrift)
                            Text(
                                betragText(e.beispielEingang, Farbe.Eingang, t.locale, 0.6f),
                                style = MARKE,
                            )
                        }
                        Schwebemarke(Modifier.offset(16.dp, 67.dp)) {
                            Punkt(Farbe.Ausgang, 8.dp)
                            Text(e.markeGesendet, style = MARKE, color = Farbe.Schrift)
                            Text(
                                betragText(e.beispielAusgang, Farbe.Schrift, t.locale, 0.6f),
                                style = MARKE,
                            )
                        }
                        Diagrammkaertchen(Modifier.offset(16.dp, 253.dp))
                    }

                    5 -> {
                        Schwebemarke(Modifier.offset(16.dp, 25.dp)) {
                            Symbol(R.drawable.ic_list_numbers_fill, 16.dp)
                            Text(e.markeWoerterPapier, style = MARKE, color = Farbe.Schrift)
                        }
                        Schwebemarke(
                            Modifier.align(Alignment.TopCenter).offset(y = 323.dp),
                            warnung = true,
                        ) {
                            Symbol(R.drawable.ic_warning_fill, 16.dp, farbe = Farbe.Warnung)
                            Text(
                                e.markeNiemalsWeitergeben,
                                style = MARKE,
                                color = Farbe.WarnSchrift,
                            )
                        }
                    }

                    6 -> {
                        Schwebemarke(Modifier.offset(16.dp, 319.dp)) {
                            Symbol(R.drawable.ic_fingerprint_bold, 17.dp)
                            Text(e.markeFingerabdruck, style = MARKE, color = Farbe.Schrift)
                            Symbol(R.drawable.ic_check_bold, 15.dp, farbe = Farbe.Eingang)
                        }
                        Schwebemarke(Modifier.align(Alignment.TopEnd).offset((-16).dp, 319.dp)) {
                            Symbol(R.drawable.ic_password_fill, 17.dp)
                            Text(e.markePin, style = MARKE, color = Farbe.Schrift)
                            Symbol(R.drawable.ic_check_bold, 15.dp, farbe = Farbe.Eingang)
                        }
                    }

                    7 -> Schwebemarke(Modifier.align(Alignment.TopCenter).offset(y = 323.dp)) {
                        Symbol(R.drawable.ic_key_fill, 16.dp)
                        Text(e.markeSchluesselAufGeraet, style = MARKE, color = Farbe.Schrift)
                    }
                }
            }
        }

        // --- Ueberschrift und Absatz --------------------------------------
        Column(
            Modifier
                .fillMaxSize()
                .padding(
                    top = KOPF_HOEHE + BILD_HOEHE,
                    // Unter dem Text steht der feste Fuss.
                    bottom = FUSS_KNOPF + FUSS_UNTEN + 8.dp,
                )
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, top = 4.dp, end = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // EIN Text mit zwei Zeilen, nicht zwei Texte: Nur so gilt zwischen
            // den Zeilen die enge Zeilenhoehe der Vorlage. Zwei einzeilige
            // Texte stuenden je in der vollen Hoehe der Schrift - sieben
            // Punkte weiter auseinander.
            Text(
                buildAnnotatedString {
                    append(karte.titel)
                    append("\n")
                    withStyle(SpanStyle(color = YskarFarben.BlauHell)) { append(karte.titelBlau) }
                },
                style = UEBERSCHRIFT,
                color = Farbe.Schrift,
            )
            Text(
                karte.text,
                style = Schrift.Text.copy(fontSize = 15.5.sp, lineHeight = 23.25.sp),
                color = Farbe.Weich,
            )
        }
    }
}

/** Ueberschrift der Karten: 34 sp, 600, eng, Zeile 1,1. */
private val UEBERSCHRIFT: TextStyle =
    Schrift.Riesig.copy(fontSize = 34.sp, lineHeight = 37.4.sp, letterSpacing = (-0.03).em)

/** Schrift in den Marken: 13 sp, 600. */
private val MARKE: TextStyle = Schrift.Klein.copy(fontWeight = FontWeight.SemiBold)

/** Karte (0 bis 7) -> Foto. Karte 4 (Stelle 3) hat keines. */
private val FOTOS: Map<Int, Int> = mapOf(
    0 to R.drawable.einfuehrung_1,
    1 to R.drawable.einfuehrung_2,
    2 to R.drawable.einfuehrung_3,
    4 to R.drawable.einfuehrung_5,
    5 to R.drawable.einfuehrung_6,
    6 to R.drawable.einfuehrung_7,
    7 to R.drawable.einfuehrung_8,
)

/**
 * Das Foto, oben bis 14 % und unten ab 78 % in den Grund ausgeblendet.
 *
 * Eigene Zeichenebene, damit die Maske nur das Foto trifft und nicht den
 * Grund dahinter (wie beim `Musterhimmel`).
 */
@Composable
private fun BoxScope.Foto(@DrawableRes bild: Int, beschreibung: String) {
    Image(
        painterResource(bild),
        contentDescription = beschreibung,
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .matchParentSize()
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                drawRect(
                    brush = Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.14f to Color.Black,
                        0.78f to Color.Black,
                        1f to Color.Transparent,
                    ),
                    blendMode = BlendMode.DstIn,
                )
            },
    )
}

// ------------------------------------------------- Was ueber dem Bild schwebt

/**
 * Eine schwebende Marke: 38 hoch, ganz rund, dunkel und halb durchsichtig,
 * mit feinem Rand. [warnung] toent sie rot - fuer den einen Satz auf Karte 6.
 */
@Composable
private fun Schwebemarke(
    modifier: Modifier = Modifier,
    warnung: Boolean = false,
    inhalt: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier
            .height(38.dp)
            .gefasst(
                rundung = 19.dp,
                grund = SolidColor(if (warnung) Color(0xCC301012) else Color(0xBD040A1A)),
                rand = if (warnung) Farbe.Warnung.copy(alpha = 0.34f) else Farbe.hauch(0.13f),
                glanz = if (warnung) 0f else 0.07f,
            )
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        content = inhalt,
    )
}

@Composable
private fun Punkt(farbe: Color, groesse: Dp) {
    Box(Modifier.size(groesse).background(farbe, CircleShape))
}

/** Eine Muenze im Raum des Fotos (1254 x 1254): Mitte, Halbmesser, Deckkraft. */
private class Muenze(val x: Float, val y: Float, val r: Float, val deckkraft: Float)

private val MUENZEN_EMPFANGEN = listOf(
    Muenze(693f, 503f, 27f, 1f),
    Muenze(639f, 487f, 23f, 0.9f),
    Muenze(588f, 502f, 19f, 0.7f),
)

/** Anfang, zwei Stuetzpunkte, Ende des Bogens - ebenfalls im Raum des Fotos. */
private val BOGEN_EMPFANGEN = floatArrayOf(716f, 546f, 700f, 468f, 578f, 468f, 562f, 546f)

private val MUENZEN_SENDEN = listOf(
    Muenze(575f, 408f, 27f, 1f),
    Muenze(523f, 361f, 22f, 0.9f),
    Muenze(477f, 326f, 17f, 0.7f),
)

private val BOGEN_SENDEN = floatArrayOf(596f, 446f, 572f, 392f, 520f, 350f, 456f, 312f)

private val MUENZRAND = Brush.linearGradient(
    0f to YskarFarben.BlauHell,
    0.55f to YskarFarben.Blau,
    1f to YskarFarben.BlauTief,
)

/**
 * Die drei YSR-Muenzen mit dem gestrichelten Bogen (Karten 2 und 3).
 *
 * Als Vektor ueber dem Foto, nicht ins Bild gebrannt (DESIGN-NEU.md,
 * Abschnitt 9). Die Zahlen stehen in der Vorlage im Raum des Fotos; [foto]
 * ist dessen Kante auf dem Bildschirm.
 */
@Composable
private fun BoxScope.Muenzen(foto: Dp, muenzen: List<Muenze>, bogen: FloatArray) {
    val je = foto / 1254f

    Canvas(Modifier.matchParentSize()) {
        val f = size.width / 1254f
        val pfad = Path().apply {
            moveTo(bogen[0] * f, bogen[1] * f)
            cubicTo(
                bogen[2] * f, bogen[3] * f,
                bogen[4] * f, bogen[5] * f,
                bogen[6] * f, bogen[7] * f,
            )
        }
        drawPath(
            pfad,
            brush = Brush.linearGradient(
                0f to YskarFarben.BlauHell,
                0.55f to YskarFarben.Blau,
                1f to YskarFarben.BlauTief,
                start = Offset(0f, 0f),
                end = Offset(size.width, size.height),
            ),
            style = Stroke(
                width = 5f * f,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f * f, 18f * f)),
            ),
        )
    }

    for (m in muenzen) {
        Box(
            Modifier
                .offset(x = je * (m.x - m.r), y = je * (m.y - m.r))
                .size(je * (2 * m.r))
                .alpha(m.deckkraft)
                .background(Farbe.GrundUnten, CircleShape)
                .border(je * 5f, MUENZRAND, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            // In der Vorlage fuellt das Y rund 62 % der Muenze.
            ZeichenRuhig(groesse = je * (2 * m.r) * 0.62f)
        }
    }
}

/** Karte 1: die Guthabenkarte im Kleinen, mit der Beispielzahl der Vorlage. */
@Composable
private fun Guthabenkaertchen(modifier: Modifier = Modifier) {
    val e = LocalTexte.current.einfuehrung
    Kartenkopf(
        // Ueber einem Foto braucht das Kaertchen einen eigenen Grund.
        modifier.size(212.dp, 88.dp).gefasst(20.dp, SolidColor(Color(0xE0071229)), Farbe.hauch(0.22f)),
    ) {
        Column(
            Modifier.padding(horizontal = 14.dp, vertical = 13.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                e.kaertchenGuthaben,
                style = Schrift.Hinweis.copy(fontSize = 11.5.sp),
                color = Farbe.Weich,
            )
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    e.beispielGuthabenGanz,
                    style = Schrift.Guthaben.copy(fontSize = 30.sp, letterSpacing = (-0.04).em),
                    color = Farbe.Schrift,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.alignByBaseline(),
                )
                Text(
                    e.beispielGuthabenBruch,
                    style = Schrift.Klein,
                    color = Farbe.Weich,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.alignByBaseline().padding(start = 1.dp),
                )
                Text(
                    "YSR",
                    style = Schrift.Einheit.copy(fontSize = 10.sp),
                    color = Farbe.Schrift,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier
                        .alignByBaseline()
                        .padding(start = 7.dp)
                        .background(Farbe.hauch(0.10f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }
    }
}

/**
 * Karte 5: das Diagramm im Kleinen.
 *
 * Eine ZEICHNUNG mit den Zahlen der Vorlage, nicht der eigene Verlauf: Die
 * Einfuehrung erscheint, bevor es einen gibt.
 */
@Composable
private fun Diagrammkaertchen(modifier: Modifier = Modifier) {
    val t = LocalTexte.current
    val e = t.einfuehrung
    Column(
        modifier
            .width(196.dp)
            .gefasst(
                rundung = 20.dp,
                grund = SolidColor(Color(0xBD040A1A)),
                rand = Farbe.hauch(0.13f),
                glanz = 0.07f,
            )
            .padding(start = 14.dp, top = 11.dp, end = 14.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                e.diagrammTitel,
                style = Schrift.Hinweis.copy(fontSize = 11.5.sp),
                color = Farbe.Weich,
            )
            Text(
                e.diagrammSpanne,
                style = Schrift.Marke.copy(letterSpacing = 0.em),
                color = Farbe.Schrift,
                modifier = Modifier
                    .background(Farbe.hauch(0.10f), RoundedCornerShape(9.dp))
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }

        Canvas(Modifier.size(168.dp, 32.dp)) {
            // Die Linie der Vorlage (326 x 62), auf 168 x 32 verkleinert.
            val fx = size.width / 326f
            val fy = size.height / 62f
            val pfad = Path()
            for (i in STUFEN.indices step 2) {
                val x = STUFEN[i] * fx
                val y = STUFEN[i + 1] * fy
                if (i == 0) pfad.moveTo(x, y) else pfad.lineTo(x, y)
            }
            val ende = Offset(STUFEN[STUFEN.size - 2] * fx, STUFEN[STUFEN.size - 1] * fy)

            val flaeche = Path().apply {
                addPath(pfad)
                lineTo(ende.x, size.height)
                lineTo(0f, size.height)
                close()
            }
            drawPath(
                flaeche,
                brush = Brush.verticalGradient(
                    0f to YskarFarben.Blau.copy(alpha = 0.34f),
                    1f to YskarFarben.Blau.copy(alpha = 0f),
                ),
            )
            drawPath(
                pfad,
                brush = Brush.horizontalGradient(
                    0f to YskarFarben.BlauTief,
                    0.6f to YskarFarben.Blau,
                    1f to YskarFarben.BlauHell,
                ),
                style = Stroke(width = 1.2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
            drawCircle(Farbe.Grund, radius = 3.4.dp.toPx(), center = ende)
            drawCircle(YskarFarben.BlauHell, radius = 1.8.dp.toPx(), center = ende)
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                betragText(e.beispielSummeEingang, Farbe.Eingang, t.locale, 0.6f),
                style = Schrift.Marke.copy(letterSpacing = 0.em),
            )
            Text(
                betragText(e.beispielSummeAusgang, Farbe.Schrift, t.locale, 0.6f),
                style = Schrift.Marke.copy(letterSpacing = 0.em),
            )
        }
    }
}

/** Die Eckpunkte der Beispiellinie aus der Vorlage, als x und y im Feld 326 x 62. */
private val STUFEN = floatArrayOf(
    0f, 54.3f, 54.3f, 53.2f, 54.3f, 45.6f, 130.4f, 43.5f, 130.4f, 35.8f,
    206.5f, 33.7f, 206.5f, 26.0f, 282.5f, 23.9f, 282.5f, 15.1f, 304.3f, 15.1f,
    304.3f, 7.4f, 315.1f, 7.4f, 315.1f, 11.3f, 326f, 11.1f,
)

/**
 * Karte 4: das Y in drei Stufen - angenommen, in einem Block, zweimal
 * bestaetigt. Dasselbe [Zeichen], das nach dem Senden den Stand zeigt.
 */
@Composable
private fun DreiStufen(modifier: Modifier = Modifier) {
    val e = LocalTexte.current.einfuehrung
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Stufe(e.stufeAngenommen, Teil.AN, Teil.AUS, Teil.AUS)
        Symbol(
            R.drawable.ic_caret_right_bold, 18.dp,
            Modifier.padding(top = 28.dp), farbe = Farbe.Leise,
        )
        Stufe(e.stufeImBlock, Teil.AN, Teil.AN, Teil.AUS)
        Symbol(
            R.drawable.ic_caret_right_bold, 18.dp,
            Modifier.padding(top = 28.dp), farbe = Farbe.Leise,
        )
        Stufe(e.stufeZweimalBestaetigt, Teil.AN, Teil.AN, Teil.AN)
    }
}

@Composable
private fun Stufe(name: String, links: Teil, rechts: Teil, stamm: Teil) {
    Column(
        Modifier.width(96.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Zeichen(links, rechts, stamm, groesse = 78.dp, beschreibung = name)
        Text(
            name,
            style = Schrift.Klein,
            color = Farbe.Weich,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}
