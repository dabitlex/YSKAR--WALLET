package com.yskar.wallet.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import androidx.annotation.DrawableRes
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.yskar.wallet.R
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import com.yskar.wallet.netz.Betrag

/**
 * Bausteine, die mehrere Bildschirme teilen.
 */

// ------------------------------------------------------------ Kleines Werkzeug

/** Anfang und Ende - fuer Listen, wo kein Platz ist. */
fun adresseKurz(adresse: String): String =
    if (adresse.length <= 20) adresse else adresse.take(10) + "…" + adresse.takeLast(6)

fun inZwischenablage(ctx: Context, text: String) {
    val cb = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    // Als "sensibel" melden: Android 13 und neuer zeigt den Inhalt dann
    // nicht in der Vorschau der Zwischenablage an.
    val clip = ClipData.newPlainText("YSKAR", text).apply {
        description.extras = android.os.PersistableBundle().apply {
            putBoolean("android.content.extra.IS_SENSITIVE", true)
        }
    }
    cb.setPrimaryClip(clip)
}

/**
 * Ob die App zurueckruetteln darf.
 *
 * ALS COMPOSITION-LOCAL UND NICHT ALS PARAMETER: Das Ziffernfeld steht auf
 * vier Bildschirmen, der Scanner auf einem fuenften. Die Einstellung durch
 * jeden davon durchzureichen hiesse, fuenf Signaturen zu aendern, damit
 * eine einzige Kachel ganz unten davon erfaehrt.
 *
 * `compositionLocalOf`, nicht `staticCompositionLocalOf`: Der Wert aendert
 * sich zur Laufzeit, wenn der Schalter umgelegt wird. Die statische
 * Fassung wuerde dabei den ganzen Baum neu zeichnen.
 */
val LocalVibration = compositionLocalOf { true }

/**
 * Ein kurzes Ruetteln - oder nichts, wenn der Besitzer es abgestellt hat.
 *
 * Gibt eine Funktion zurueck statt selbst zu ruetteln, weil der Aufrufer
 * ein Klickhandler ist und kein Composable.
 */
@Composable
fun kurzesRuetteln(): () -> Unit {
    val an = LocalVibration.current
    val tasten = LocalHapticFeedback.current
    return { if (an) tasten.performHapticFeedback(HapticFeedbackType.LongPress) }
}

// ============================================================================
//  DIE BAUSTEINE - seit dem 12.11.2026 im Stil der Website ("Erstes Licht")
//
//  Die Masse stammen aus dem Design "Praegung" (design-neu/DESIGN-NEU.md,
//  Abschnitt 6) und sind geblieben: Sie sind am Geraet vermessen (Regel 29).
//  Geaendert hat sich, WORAUS die Formen gemacht sind - Nachtblau statt
//  Graphit, Umriss statt Kasten, Lampenlicht fuer den einen Knopf, der nach
//  vorn fuehrt. Die Website zeichnet keine Glanzkanten und keine Kacheln mit
//  Rahmen; darum gibt es hier keine mehr.
//
//  Die alten Bausteine (Hauptknopf, Karte, Zeile, PinEingabe und ihre
//  Nachbarn) sind mit Teil 2e des Umbaus entfernt. Daher manche Namen:
//  `Knopf` und `Wertzeile` heissen so, weil es `Hauptknopf` und `Zeile`
//  daneben noch gab, solange umgebaut wurde.
// ============================================================================

// ------------------------------------------------------------------ Symbole

/** Ein Symbol aus res/drawable/ic_*.xml, in der Farbe der Schrift. */
@Composable
fun Symbol(
    @DrawableRes datei: Int,
    groesse: Dp,
    modifier: Modifier = Modifier,
    farbe: Color = Farbe.Schrift,
) {
    Icon(
        painter = painterResource(datei),
        contentDescription = null,
        modifier = modifier.size(groesse),
        tint = farbe,
    )
}

// ------------------------------------------------------------------ Fassung

/**
 * Fuellung und Haarlinie - auf der Website `background` und
 * `box-shadow: inset 0 0 0 1px …`.
 *
 * [glanz] war die helle Linie unter dem oberen Rand ("Praegung"). Die
 * Website kennt sie nicht; der Parameter bleibt, damit die Aufrufer bleiben
 * koennen, und wird nicht mehr gezeichnet.
 */
@Suppress("UNUSED_PARAMETER")
fun Modifier.gefasst(
    rundung: Dp,
    grund: Brush = Farbe.Flaeche,
    rand: Color = Farbe.Linie,
    glanz: Float = 0f,
): Modifier = this
    .clip(RoundedCornerShape(rundung))
    .background(grund)
    .drawWithContent {
        drawContent()
        fassung(rundung.toPx(), rand)
    }

private fun DrawScope.fassung(rundung: Float, rand: Color) {
    val px = 1.dp.toPx()
    val r = minOf(rundung, size.minDimension / 2f)
    if (rand.alpha > 0f) {
        drawRoundRect(
            color = rand,
            topLeft = Offset(px / 2f, px / 2f),
            size = Size(size.width - px, size.height - px),
            cornerRadius = CornerRadius(maxOf(r - px / 2f, 0f)),
            style = Stroke(px),
        )
    }
}

/**
 * Der warme Schein unter dem Hauptknopf und dem Senden-Kreis - auf der
 * Website `box-shadow: 0 10px 40px -8px rgba(255,174,82,.55)`.
 *
 * Gezeichnet wie dort gerechnet: die Form um [einzug] verkleinert, um
 * [versatz] nach unten geschoben, mit [weich] weichgezeichnet. CSS nennt
 * dabei die doppelte Standardabweichung, Android einen "Radius" mit
 * sigma = 0,57735 * radius + 0,5 - daher die Umrechnung.
 *
 * Auf Android 8 (API 26/27) zeichnet die Grafikschicht den Weichzeichner
 * nicht. Die Form liegt dann vollstaendig hinter dem Knopf, der Schein
 * fehlt einfach, und nichts sieht kaputt aus.
 */
internal fun Modifier.schein(
    farbe: Color,
    rundung: Dp,
    versatz: Dp,
    weich: Dp,
    einzug: Dp,
): Modifier = drawWithCache {
    val sigma = weich.toPx() / 2f
    // Der Pinsel von Android selbst, nicht der von Compose: Nur er kennt
    // den Weichzeichner.
    val pinsel = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = farbe.toArgb()
        maskFilter = BlurMaskFilter(
            ((sigma - 0.5f) / 0.57735f).coerceAtLeast(1f),
            BlurMaskFilter.Blur.NORMAL,
        )
    }
    val e = einzug.toPx()
    val v = versatz.toPx()
    val r = (minOf(rundung.toPx(), size.minDimension / 2f) - e).coerceAtLeast(0f)
    onDrawBehind {
        drawIntoCanvas {
            it.nativeCanvas.drawRoundRect(
                e, e + v, size.width - e, size.height - e + v, r, r, pinsel,
            )
        }
    }
}

// -------------------------------------------------------- Grund und Flaeche

/**
 * Der Grund jedes Bildschirms: Nacht. Oben steht der Himmel heller und
 * traegt zwei kalte Lichter - links das Blau des Zeichens, rechts ein Hauch
 * seines hellen Endes -, nach unten wird es dunkler.
 *
 * Die Lichter sind Ellipsen um die oberen Ecken, gezeichnet als Kreis mit
 * Radius 1 und auf die Ellipse gestreckt. Sie liegen HINTER allem und
 * tragen keine Bedeutung: Wer sie nicht sieht, verliert nichts.
 */
@Composable
fun Grund(modifier: Modifier = Modifier, inhalt: @Composable BoxScope.() -> Unit) {
    Box(
        modifier
            .fillMaxSize()
            .drawWithCache {
                val nacht = Brush.verticalGradient(
                    0f to Farbe.GrundOben,
                    0.38f to Farbe.Grund,
                    0.72f to Farbe.Grund,
                    1f to Farbe.GrundUnten,
                )
                val links = Brush.radialGradient(
                    0f to YskarFarben.Blau.copy(alpha = 0.30f),
                    0.62f to YskarFarben.Blau.copy(alpha = 0f),
                    center = Offset.Zero,
                    radius = 1f,
                )
                val rechts = Brush.radialGradient(
                    0f to YskarFarben.BlauHell.copy(alpha = 0.14f),
                    0.60f to YskarFarben.BlauHell.copy(alpha = 0f),
                    center = Offset.Zero,
                    radius = 1f,
                )
                // Die Hoehe der Lichter haengt an der BREITE: Auf einem
                // langen Bildschirm reichen sie sonst bis zur Mitte.
                val w = size.width
                onDrawBehind {
                    drawRect(nacht)
                    val einheit = Size(2f, 2f)
                    val ecke = Offset(-1f, -1f)
                    // Links oben: Mittelpunkt bei 20 % der Breite, knapp
                    // ueber dem Rand; Halbachsen 120 % und 75 % der Breite.
                    withTransform({
                        translate(0.20f * w, -0.06f * w)
                        scale(1.2f * w, 0.75f * w, pivot = Offset.Zero)
                    }) { drawRect(links, topLeft = ecke, size = einheit) }
                    // Rechts oben: Halbachsen 90 % und 62 % der Breite.
                    withTransform({
                        translate(0.96f * w, -0.03f * w)
                        scale(0.9f * w, 0.62f * w, pivot = Offset.Zero)
                    }) { drawRect(rechts, topLeft = ecke, size = einheit) }
                }
            },
        content = inhalt,
    )
}

/** Karte oder Feld: ein Hauch heller als der Grund, mit Haarlinie. */
@Composable
fun Flaeche(
    modifier: Modifier = Modifier,
    rundung: Dp = 24.dp,
    innen: PaddingValues = PaddingValues(start = 16.dp, top = 14.dp, end = 16.dp, bottom = 16.dp),
    abstand: Dp = 12.dp,
    // Was UEBER anderem Inhalt liegt (eine Rueckfrage), braucht einen Grund,
    // der nichts durchscheinen laesst: [Farbe.Tafel].
    grund: Brush = Farbe.Flaeche,
    inhalt: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier.fillMaxWidth().gefasst(rundung, grund).padding(innen),
        verticalArrangement = Arrangement.spacedBy(abstand),
        content = inhalt,
    )
}

/** Ueberschrift einer Karte: Symbol im blauen Kaestchen, Titel. */
@Composable
fun Abschnitt(@DrawableRes symbol: Int, titel: String, modifier: Modifier = Modifier) {
    Row(
        modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier.size(32.dp).gefasst(
                rundung = 11.dp,
                grund = SolidColor(YskarFarben.Blau.copy(alpha = 0.12f)),
                rand = YskarFarben.Blau.copy(alpha = 0.28f),
                glanz = 0f,
            ),
            contentAlignment = Alignment.Center,
        ) { Symbol(symbol, 19.dp) }
        Text(titel, style = Schrift.Kopf, color = Farbe.Schrift)
    }
}

// --------------------------------------------- Guthabenkarte und Kartenkopf

/**
 * Das Linienmuster: zwei Scharen kubischer Kurven ueber die ganze Flaeche.
 *
 * Die Formel aus DESIGN-NEU.md, Abschnitt 6, fuer eine Karte der Breite w
 * und y von -60 bis Hoehe + 70 in Schritten von 9:
 *
 *     Schar 1:  M -10, y      C 0,25w, y-52   0,56w, y+64   w+10, y-22
 *     Schar 2:  M -10, y+34   C 0,30w, y+84   0,62w, y-58   w+10, y+30
 *
 * Die waagrechten Stuetzpunkte haengen an w, die senkrechten Abstaende sind
 * feste dp. Auf einem breiteren Geraet streckt sich das Muster also, statt
 * abzureissen.
 */
private fun linienpfad(flaeche: Size, dp: Float): Path {
    val w = flaeche.width
    val pfad = Path()
    var y = -60f * dp
    val ende = flaeche.height + 70f * dp
    while (y < ende) {
        pfad.moveTo(-10f * dp, y)
        pfad.cubicTo(0.25f * w, y - 52f * dp, 0.56f * w, y + 64f * dp, w + 10f * dp, y - 22f * dp)
        pfad.moveTo(-10f * dp, y + 34f * dp)
        pfad.cubicTo(0.30f * w, y + 84f * dp, 0.62f * w, y - 58f * dp, w + 10f * dp, y + 30f * dp)
        y += 9f * dp
    }
    return pfad
}

/**
 * Der Pinsel dazu: Weiss mit [deckkraft], nach links auf 5 % davon
 * ausgeblendet. In der Vorlage ist das eine Maske mit einem Verlauf von
 * (0; 0,2) nach (1; 0,8) der Flaeche - ein Verlauf im Strich selbst ergibt
 * dasselbe Bild ohne eine zweite Zeichenebene.
 */
private fun linienpinsel(flaeche: Size, deckkraft: Float): Brush = Brush.linearGradient(
    colors = listOf(
        Farbe.hauch(deckkraft * 0.05f),
        Farbe.hauch(deckkraft),
    ),
    start = Offset(0f, flaeche.height * 0.2f),
    end = Offset(flaeche.width, flaeche.height * 0.8f),
)

/** Das Muster allein - fuer Flaechen, die keine Karte sind (Sperre). */
fun DrawScope.linienmuster(deckkraft: Float = 0.11f) {
    drawPath(
        path = linienpfad(size, 1.dp.toPx()),
        brush = linienpinsel(size, deckkraft),
        style = Stroke(0.8.dp.toPx()),
    )
}

/**
 * Der Kopf eines Bildschirms oder Belegs: das Guthaben, "Verfuegbar", der
 * Betrag eines Belegs.
 *
 * Im Design "Praegung" war das die Graphit-Karte mit Linienmuster,
 * Lichtkante und eingepraegtem Zeichen. Die Website setzt ihre Hauptsache
 * OHNE Kasten auf die Nacht - also zeichnet dieser Baustein seit dem
 * 12.11.2026 nichts mehr ausser seinem Inhalt. Er bleibt, weil er sagt, was
 * der Inhalt IST, und weil die Bildschirme ihre vermessenen Raender behalten.
 *
 * [rundung], [gefasst], [muster] und [praegung] haben keine Wirkung mehr.
 */
@Suppress("UNUSED_PARAMETER")
@Composable
fun Kartenkopf(
    modifier: Modifier = Modifier,
    rundung: Dp = 28.dp,
    gefasst: Boolean = true,
    muster: Float = 0.11f,
    praegung: Boolean = false,
    inhalt: @Composable BoxScope.() -> Unit,
) {
    Box(modifier, content = inhalt)
}

/**
 * Text eines Betrags fuer Uebersichten: die ganze Zahl voll, alles hinter
 * dem Komma kleiner und leiser.
 *
 * Geteilt wird mit `betragGeteilt`. Der Text selbst bleibt, wie er
 * hereinkommt - gekuerzt und gerundet wird hier nichts.
 */
fun betragText(
    text: String,
    farbe: Color,
    locale: java.util.Locale,
    bruch: Float = 0.72f,
): AnnotatedString {
    val (haupt, rest) = betragGeteilt(text, locale)
    if (rest.isEmpty()) return AnnotatedString(haupt, SpanStyle(color = farbe))
    return buildAnnotatedString {
        withStyle(SpanStyle(color = farbe)) { append(haupt) }
        withStyle(
            SpanStyle(color = farbe.copy(alpha = farbe.alpha * bruch), fontSize = 0.78.em),
        ) { append(rest) }
    }
}

/**
 * Das Guthaben als Hauptfigur: die ganze Zahl gross, ALLE Nachkommastellen
 * klein dahinter (Wunsch des Besitzers, 12.11.2026), dann "YSR".
 *
 * Abgeschnitten wird nie. Passt die Zahl nicht in die Breite - grosses
 * Guthaben, vergroesserte Systemschrift -, wird die Schrift fuer beide
 * Teile im selben Verhaeltnis kleiner, bis sie passt.
 *
 * [klein] = null: nicht teilen, die ganze Zahl in einer Groesse und einer
 * Farbe - so steht der Betrag im Kopf eines Belegs. [vorzeichen] und [farbe]
 * sind fuer die Bewegung: "+1,00" in Gruen, "-25,00" in Weiss.
 */
@Composable
fun GuthabenZahl(
    einheiten: Long?,
    modifier: Modifier = Modifier,
    verborgen: Boolean = false,
    gross: TextStyle = Schrift.Guthaben,
    klein: TextStyle? = Schrift.GuthabenBruch,
    vorzeichen: String = "",
    farbe: Color = Farbe.Schrift,
) {
    val locale = LocalTexte.current.locale
    val text = if (einheiten == null) "—" else vorzeichen + Betrag.genau(einheiten, locale)
    val teile = betragGeteilt(text, locale)
    val ganz = when {
        verborgen -> "••••"
        klein != null -> teile.first
        else -> text
    }
    val bruch = if (verborgen || klein == null) "" else teile.second

    val messer = rememberTextMeasurer()
    val dichte = LocalDensity.current

    BoxWithConstraints(modifier) {
        val faktor = remember(ganz, bruch, gross, klein, dichte, constraints.maxWidth) {
            if (!constraints.hasBoundedWidth) 1f
            else {
                // Das Schild wird NICHT mit verkleinert. Es muss darum vom
                // Platz abgezogen werden, bevor geteilt wird - sonst ragt es
                // um genau den Betrag hinaus, um den die Zahl kleiner wurde.
                // (So stand bei grosser Systemschrift "YS" statt "YSR" da.)
                val schild = messer.measure("YSR", Schrift.Einheit).size.width +
                    with(dichte) { (16.dp + 8.dp + 2.dp).toPx() }
                val zahl = messer.measure(ganz, gross).size.width +
                    (if (klein == null || bruch.isEmpty()) 0 else messer.measure(bruch, klein).size.width)
                ((constraints.maxWidth - schild) / zahl).coerceIn(0.35f, 1f)
            }
        }
        Row {
            Text(
                ganz,
                style = gross.copy(
                    fontSize = gross.fontSize * faktor,
                    lineHeight = gross.lineHeight * faktor,
                ),
                color = farbe,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.alignByBaseline(),
            )
            if (klein != null && bruch.isNotEmpty()) {
                Text(
                    bruch,
                    style = klein.copy(
                        fontSize = klein.fontSize * faktor,
                        lineHeight = klein.lineHeight * faktor,
                    ),
                    color = Farbe.Weich,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.alignByBaseline(),
                )
            }
            // Auf der Grundlinie der Zahl, nicht in ihrer Mitte: Ohne Kaestchen
            // haengt das Wort sonst in der Luft.
            Einheit(Modifier.padding(start = 8.dp).alignByBaseline())
        }
    }
}

// -------------------------------------------------------------------- Beleg

/**
 * Der Beleg: Pruefschritt und Bewegung.
 *
 * Hinein gehoeren ein [Kartenkopf], darunter Abschnitte, getrennt durch
 * [Abrisskante], am Ende ein [BelegFuss]. Einen Rahmen hat er nicht mehr -
 * die Website trennt mit Linien, nicht mit Kaesten.
 */
@Suppress("UNUSED_PARAMETER")
@Composable
fun Beleg(
    modifier: Modifier = Modifier,
    rundung: Dp = 28.dp,
    inhalt: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.fillMaxWidth(), content = inhalt)
}

/**
 * Die Trennlinie zwischen zwei Abschnitten des Belegs. Sie hiess
 * Abrisskante, als sie gestrichelt war und links und rechts ein Stueck
 * ausgestanzt schien; geblieben ist ihre Hoehe.
 */
@Composable
fun Abrisskante(modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxWidth().padding(horizontal = 20.dp).height(1.5.dp)) {
        drawLine(
            color = Farbe.Linie,
            start = Offset(0f, size.height / 2f),
            end = Offset(size.width, size.height / 2f),
            strokeWidth = 1.dp.toPx(),
        )
    }
}

/** Fusszeile des Belegs: Festbreite, die Angaben gleichmaessig verteilt. */
@Composable
fun BelegFuss(vararg angaben: String, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .drawWithContent {
                drawContent()
                drawLine(
                    Farbe.Linie,
                    Offset(20.dp.toPx(), 0.5.dp.toPx()),
                    Offset(size.width - 20.dp.toPx(), 0.5.dp.toPx()),
                    1.dp.toPx(),
                )
            }
            .padding(horizontal = 20.dp, vertical = 9.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        for (a in angaben) Text(a, style = Schrift.Winzig, color = Farbe.Leise)
    }
}

// ------------------------------------------------------------------ Knoepfe

/**
 * HAUPT    der eine gefuellte Knopf, der nach vorn fuehrt (58 dp):
 *          Lampenlicht mit dunkler Schrift, wie `.btn` der Website
 * FLAECHE  alles andere von Gewicht: Kopieren, Teilen, Mehr laden (54 dp):
 *          nur ein heller Umriss, wie `.btn--ghost`
 * BLAU     kleine Helfer am Feld: QR-Code scannen, Einfuegen (44 dp)
 * BLAU_GROSS  derselbe Ton, aber allein am Fuss: Fingerabdruck (50 dp)
 * STILL    der leiseste: "Fertig" oben rechts (44 dp)
 * WARNUNG  rot getoent, fuer das eine, das nicht rueckgaengig geht:
 *          "Ja, entfernen" in der Rueckfrage (54 dp)
 */
enum class Knopfart { HAUPT, FLAECHE, BLAU, BLAU_GROSS, STILL, WARNUNG }

/**
 * Ein Knopf mit Beschriftung und, wenn gewuenscht, einem Symbol davor.
 *
 * Die Hoehen sind MINDESThoehen. Bei vergroesserter Systemschrift waechst
 * der Knopf mit, statt den Text abzuschneiden. Die Breite bestimmt der
 * Aufrufer.
 *
 * Ein HAUPT-Knopf, der nicht [aktiv] ist, verliert Licht und Schein und
 * ist nur noch ein leiser Umriss mit leiser Schrift - so steht er unter
 * "Senden", solange noch nichts eingegeben ist.
 *
 * Alle Knoepfe sind ganz rund (Website: `border-radius: 99px`). Waechst
 * einer bei grosser Schrift ueber zwei Zeilen, bleibt die Rundung die
 * seiner Mindesthoehe.
 */
@Composable
fun Knopf(
    text: String,
    onKlick: () -> Unit,
    modifier: Modifier = Modifier,
    art: Knopfart = Knopfart.HAUPT,
    @DrawableRes symbol: Int? = null,
    aktiv: Boolean = true,
) {
    val gross = art == Knopfart.HAUPT || art == Knopfart.FLAECHE || art == Knopfart.WARNUNG
    val rundung = when {
        art == Knopfart.HAUPT -> 29.dp
        gross -> 27.dp
        art == Knopfart.BLAU_GROSS -> 25.dp
        else -> 22.dp
    }

    val vorne = when (art) {
        Knopfart.HAUPT -> if (aktiv) Farbe.LampenTinte else Farbe.Leise
        Knopfart.BLAU, Knopfart.BLAU_GROSS ->
            Farbe.BlauSchrift.copy(alpha = if (aktiv) 1f else 0.45f)
        Knopfart.WARNUNG -> Farbe.WarnSchrift.copy(alpha = if (aktiv) 1f else 0.45f)
        else -> Farbe.Schrift.copy(alpha = if (aktiv) 1f else 0.45f)
    }
    val fassung = when (art) {
        Knopfart.HAUPT ->
            if (aktiv) {
                Modifier
                    .schein(Farbe.LampenSchein, rundung, 10.dp, 30.dp, 12.dp)
                    .gefasst(rundung, Farbe.Hauptknopf, Color.Transparent)
            } else Modifier.gefasst(rundung, SolidColor(Color.Transparent), Farbe.Linie)

        Knopfart.FLAECHE -> Modifier.gefasst(
            rundung,
            SolidColor(Color.Transparent),
            Farbe.eis(if (aktiv) 0.42f else 0.20f),
        )

        Knopfart.BLAU, Knopfart.BLAU_GROSS -> Modifier.gefasst(
            rundung,
            SolidColor(YskarFarben.Blau.copy(alpha = 0.12f)),
            YskarFarben.Blau.copy(alpha = if (art == Knopfart.BLAU_GROSS) 0.35f else 0.30f),
            0f,
        )

        Knopfart.STILL -> Modifier.gefasst(
            rundung,
            SolidColor(Color.Transparent),
            Farbe.eis(0.24f),
            0f,
        )

        Knopfart.WARNUNG -> Modifier.gefasst(
            rundung,
            SolidColor(Farbe.Warnung.copy(alpha = 0.10f)),
            Farbe.Warnung.copy(alpha = 0.32f),
            0f,
        )
    }

    Row(
        modifier
            .heightIn(
                min = when (art) {
                    Knopfart.HAUPT -> 58.dp
                    Knopfart.FLAECHE, Knopfart.WARNUNG -> 54.dp
                    Knopfart.BLAU_GROSS -> 50.dp
                    else -> 44.dp
                },
            )
            .then(fassung)
            .clickable(enabled = aktiv, role = Role.Button, onClick = onKlick)
            .padding(
                horizontal = when (art) {
                    Knopfart.BLAU -> 14.dp
                    Knopfart.BLAU_GROSS -> 20.dp
                    else -> 16.dp
                },
                vertical = 6.dp,
            ),
        horizontalArrangement = Arrangement.spacedBy(
            when (art) {
                Knopfart.HAUPT, Knopfart.BLAU_GROSS -> 10.dp
                Knopfart.FLAECHE, Knopfart.WARNUNG -> 9.dp
                else -> 8.dp
            },
            Alignment.CenterHorizontally,
        ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (symbol != null) {
            Symbol(
                symbol,
                when (art) {
                    Knopfart.HAUPT -> 19.dp
                    Knopfart.FLAECHE, Knopfart.WARNUNG -> 18.dp
                    Knopfart.BLAU_GROSS -> 20.dp
                    else -> 17.dp
                },
                farbe = vorne,
            )
        }
        Text(
            text,
            style = when (art) {
                Knopfart.HAUPT -> Schrift.Titel
                Knopfart.FLAECHE, Knopfart.BLAU_GROSS, Knopfart.WARNUNG -> Schrift.Kopf
                Knopfart.BLAU -> Schrift.KnopfBlau
                Knopfart.STILL -> Schrift.KnopfStill
            },
            color = vorne,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * Runder Knopf mit Beschriftung darunter - die vier Aktionen unter dem
 * Guthaben. Nur einer davon ist [hervorgehoben]: Senden.
 */
@Composable
fun RunderKnopf(
    @DrawableRes symbol: Int,
    beschriftung: String,
    onKlick: () -> Unit,
    modifier: Modifier = Modifier,
    hervorgehoben: Boolean = false,
    aktiv: Boolean = true,
) {
    val kreis = if (hervorgehoben) {
        Modifier
            // Der Schein nur am aktiven Knopf: Ausgegraut liegt der ganze
            // Baustein in einer eigenen Ebene, und die schneidet alles ab,
            // was ueber seine Kante ragt.
            .then(
                if (aktiv) Modifier.schein(Farbe.LampenSchein, 31.dp, 10.dp, 24.dp, 12.dp)
                else Modifier,
            )
            .gefasst(rundung = 31.dp, grund = Farbe.Hauptknopf, rand = Color.Transparent)
    } else Modifier.gefasst(31.dp, SolidColor(Color.Transparent), Farbe.eis(0.30f))

    // Kreis UND Beschriftung sind EIN Knopf, wie in der Vorlage: Wer auf das
    // Wort tippt, trifft auch. Fuer einen Bildschirmleser ist es damit ein
    // Element ("Senden, Schaltflaeche, nicht verfuegbar") statt eines
    // namenlosen Kreises und eines Wortes daneben - und die
    // Oberflaechentests finden den Knopf an seinem Namen.
    //
    // Das Aufleuchten bei Beruehrung bleibt trotzdem im Kreis: Die Spalte
    // nimmt den Tipp entgegen, zeigt ihn aber nicht selbst an.
    val beruehrung = remember { MutableInteractionSource() }

    Column(
        modifier
            .widthIn(min = 78.dp)
            .alpha(if (aktiv) 1f else 0.4f)
            .clickable(
                interactionSource = beruehrung,
                indication = null,
                enabled = aktiv,
                role = Role.Button,
                onClick = onKlick,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            Modifier
                .size(62.dp)
                .then(kreis)
                .indication(beruehrung, LocalIndication.current),
            contentAlignment = Alignment.Center,
        ) {
            Symbol(symbol, 27.dp, farbe = if (hervorgehoben) Farbe.LampenTinte else Farbe.Schrift)
        }
        // Eine Zeile, und notfalls kleiner: Vier Knoepfe teilen sich die
        // Breite, und "Einstellungen" ist bei grosser Systemschrift breiter
        // als sein Viertel. Abgeschnitten wird die Beschriftung nie.
        Text(
            beschriftung,
            style = Schrift.HinweisFest,
            color = Farbe.Schrift,
            maxLines = 1,
            softWrap = false,
            autoSize = TextAutoSize.StepBased(
                minFontSize = 8.sp, maxFontSize = Schrift.HinweisFest.fontSize, stepSize = 0.5.sp,
            ),
        )
    }
}

/**
 * Runder Knopf nur mit Symbol: Zurueck, Sperren, Kopieren.
 *
 * Sichtbar 42 dp, beruehrbar 44 - die Vorlage zeichnet den Kreis eine Spur
 * kleiner als die Flaeche, auf die der Finger trifft.
 *
 * [betont]: der Knopf ist ein Schalter und gerade AN (das Auge, solange
 * das Guthaben verborgen ist). Dann traegt er das Blau der Helferknoepfe.
 * Die Vorlage zeichnet diesen Zustand nicht; ohne ihn saehe man dem Knopf
 * nicht an, ob er gedrueckt ist.
 */
@Composable
fun Symbolknopf(
    @DrawableRes symbol: Int,
    beschreibung: String,
    onKlick: () -> Unit,
    modifier: Modifier = Modifier,
    aktiv: Boolean = true,
    betont: Boolean = false,
) {
    Box(
        modifier
            .size(44.dp)
            .clip(CircleShape)
            .alpha(if (aktiv) 1f else 0.4f)
            .clickable(enabled = aktiv, role = Role.Button, onClick = onKlick)
            .semantics { contentDescription = beschreibung },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.size(42.dp).gefasst(
                21.dp,
                SolidColor(
                    if (betont) YskarFarben.Blau.copy(alpha = 0.16f)
                    else Color.Transparent,
                ),
                if (betont) YskarFarben.Blau.copy(alpha = 0.40f) else Farbe.eis(0.30f),
                0f,
            ),
            contentAlignment = Alignment.Center,
        ) { Symbol(symbol, 19.dp, farbe = if (betont) Farbe.BlauSchrift else Farbe.Schrift) }
    }
}

/**
 * Zeile, die irgendwohin fuehrt: Beschriftung links, Winkel rechts.
 * Mit [warnung] rot getoent - fuer das eine, das nicht rueckgaengig geht.
 */
@Composable
fun Zeilenknopf(
    text: String,
    onKlick: () -> Unit,
    modifier: Modifier = Modifier,
    @DrawableRes symbol: Int? = null,
    warnung: Boolean = false,
) {
    val ton = if (warnung) Farbe.Warnung else Farbe.hauch(1f)
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .gefasst(
                rundung = 16.dp,
                grund = SolidColor(ton.copy(alpha = if (warnung) 0.07f else 0.04f)),
                rand = if (warnung) ton.copy(alpha = 0.26f) else Farbe.Linie,
                glanz = 0f,
            )
            .clickable(role = Role.Button, onClick = onKlick)
            .padding(start = 14.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (symbol != null) Symbol(symbol, 19.dp, farbe = if (warnung) Farbe.Warnung else Farbe.Schrift)
        Text(
            text,
            style = Schrift.Zeile,
            color = if (warnung) Farbe.WarnSchrift else Farbe.Schrift,
            modifier = Modifier.weight(1f),
        )
        Symbol(
            R.drawable.ic_caret_right_bold, 16.dp,
            farbe = if (warnung) Farbe.Warnung else Farbe.Leise,
        )
    }
}

// --------------------------------------------------------------- Kopfzeile

/**
 * Kopf einer Unterseite: Zurueck links, Titel in der Mitte, rechts Platz
 * fuer eine Marke oder die Schrittpunkte.
 *
 * Der Titel steht in der Mitte des BILDSCHIRMS, nicht in der Mitte des
 * Platzes zwischen den beiden Seiten - sonst wandert er, sobald rechts
 * etwas Breiteres steht als links.
 */
@Composable
fun Kopfzeile(
    titel: String,
    modifier: Modifier = Modifier,
    onZurueck: (() -> Unit)? = null,
    @DrawableRes zurueckSymbol: Int = R.drawable.ic_caret_left_bold,
    // null: "Zurück" in der Sprache der App.
    zurueckText: String? = null,
    zurueckAktiv: Boolean = true,
    rechts: @Composable () -> Unit = {},
) {
    Box(
        modifier
            .fillMaxWidth()
            .padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 10.dp)
            .heightIn(min = 44.dp),
    ) {
        if (onZurueck != null) {
            Symbolknopf(
                zurueckSymbol, zurueckText ?: LocalTexte.current.allgemein.zurueck, onZurueck,
                Modifier.align(Alignment.CenterStart),
                aktiv = zurueckAktiv,
            )
        }
        Text(
            titel,
            style = Schrift.Titel,
            color = Farbe.Schrift,
            modifier = Modifier.align(Alignment.Center),
        )
        Box(Modifier.align(Alignment.CenterEnd)) { rechts() }
    }
}

/** Wo man im Senden steht: ein Strich fuer den Schritt, Punkte fuer die anderen. */
@Composable
fun Schrittpunkte(schritt: Int, von: Int = 3, modifier: Modifier = Modifier) {
    val ansage = LocalTexte.current.allgemein.schrittVon(schritt, von)
    Row(
        modifier.semantics { contentDescription = ansage },
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        for (i in 1..von) {
            Box(
                Modifier
                    .size(width = if (i == schritt) 18.dp else 6.dp, height = 6.dp)
                    .background(
                        if (i == schritt) YskarFarben.Blau else Farbe.hauch(0.26f),
                        RoundedCornerShape(3.dp),
                    ),
            )
        }
    }
}

// ------------------------------------------------------------------ Marken

/** Kleine Marke fuer eine Zahl oder ein Wort ("4 geladen"). */
@Composable
fun Marke(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier
            .heightIn(min = 30.dp)
            .widthIn(min = 44.dp)
            .background(Farbe.hauch(0.09f), RoundedCornerShape(15.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = Schrift.Hinweis, color = Farbe.Weich, maxLines = 1)
    }
}

/**
 * "YSR" hinter einem Betrag: klein, gesperrt, leise - ohne Kaestchen. Der
 * Rand bleibt, damit die Zahl davor ihren vermessenen Platz behaelt.
 */
@Composable
fun Einheit(modifier: Modifier = Modifier, text: String = "YSR") {
    Text(
        text,
        style = Schrift.Einheit,
        color = Farbe.Leise,
        maxLines = 1,
        modifier = modifier.padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

/**
 * Die eigene Adresse als Marke: Punkt, Anfang, vier Punkte, Ende.
 *
 * Gezeigt werden acht Zeichen vom Anfang und sechs vom Ende. Das ist eine
 * Erkennungsmarke, kein Vergleichswert - wer vergleichen muss, bekommt die
 * ganze Adresse in [AdresseInGruppen].
 *
 * [aufKarte]: die groessere Fassung auf der Guthabenkarte, antippbar zum
 * Kopieren. Sonst die kleine von der Sperre.
 */
@Composable
fun Adressmarke(
    adresse: String,
    modifier: Modifier = Modifier,
    aufKarte: Boolean = false,
    onKopieren: (() -> Unit)? = null,
) {
    val kopierenText = LocalTexte.current.allgemein.adresseKopieren
    val kurz = adresse.length > 16
    val stil = if (aufKarte) Schrift.Kurz.copy(fontSize = 13.sp) else Schrift.Kurz
    val vorne = if (aufKarte) Farbe.Schrift else Farbe.Weich
    Row(
        modifier
            .then(if (aufKarte) Modifier.heightIn(min = 44.dp) else Modifier)
            .gefasst(
                rundung = if (aufKarte) 14.dp else 12.dp,
                grund = SolidColor(
                    if (aufKarte) Color.Transparent
                    else Farbe.hauch(0.05f),
                ),
                rand = Farbe.Linie,
                glanz = 0f,
            )
            .then(
                if (onKopieren != null) {
                    Modifier.clickable(
                        role = Role.Button,
                        onClickLabel = kopierenText,
                        onClick = onKopieren,
                    )
                } else Modifier,
            )
            .padding(
                start = if (aufKarte) 10.dp else 12.dp,
                end = 12.dp,
                top = 7.dp,
                bottom = 7.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (aufKarte) 9.dp else 8.dp),
    ) {
        Box(Modifier.size(6.dp).background(YskarFarben.BlauHell, CircleShape))
        // Drei Texte, nicht einer: In der Vorlage stehen Anfang, Punkte und
        // Ende mit demselben Abstand nebeneinander wie Punkt und Symbol.
        if (kurz) {
            Text(adresse.take(8), style = stil, color = vorne, maxLines = 1)
            Text("····", style = stil, color = Farbe.Leise, maxLines = 1)
            Text(adresse.takeLast(6), style = stil, color = vorne, maxLines = 1)
        } else {
            Text(adresse, style = stil, color = vorne, maxLines = 1)
        }
        if (onKopieren != null) Symbol(R.drawable.ic_copy_fill, 15.dp, farbe = Farbe.Weich)
    }
}

/** Roter Streifen mit Warnzeichen - fuer das, was nicht rueckgaengig geht. */
@Composable
fun Warnhinweis(text: String, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .gefasst(
                rundung = 16.dp,
                grund = SolidColor(Farbe.Warnung.copy(alpha = 0.08f)),
                rand = Farbe.Warnung.copy(alpha = 0.22f),
                glanz = 0f,
            )
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Symbol(R.drawable.ic_warning_fill, 20.dp, farbe = Farbe.Warnung)
        Text(text, style = Schrift.Klein, color = Farbe.WarnSchrift, modifier = Modifier.weight(1f))
    }
}

// ------------------------------------------------------------------- Zeilen

/**
 * Beschriftung links, Wert rechts, darunter eine Haarlinie.
 *
 * Passen beide nebeneinander, stehen sie nebeneinander. Passen sie nicht -
 * lange Notiz, grosser Betrag, vergroesserte Systemschrift -, rueckt der
 * Wert in eine eigene Zeile darunter und bekommt die volle Breite. Vorher
 * teilten sich beide die Zeile, und der Wert brach mitten im Wort um
 * ("Novem-ber"). Abgeschnitten wird in keinem Fall.
 */
@Composable
fun Wertzeile(
    name: String,
    wert: String,
    modifier: Modifier = Modifier,
    einheit: String? = null,
    betont: Boolean = false,
    hoehe: Dp = 42.dp,
    linie: Boolean = true,
    klein: Boolean = false,
    festbreite: Boolean = false,
    // Der Wert bleibt in EINER Zeile neben der Beschriftung und wird dafuer
    // notfalls kleiner gesetzt, statt darunter zu rutschen. Fuer die
    // Sendebildschirme bei grosser Systemschrift (REGELN.md, Regel 29):
    // Eine Zeile, die umbricht, kostet dort den Platz der naechsten.
    // Gekuerzt wird nichts - die Zahl steht vollstaendig da.
    einpassen: Boolean = false,
) {
    val wertStil = when {
        festbreite -> Schrift.Kurz.copy(fontSize = 13.sp)
        betont -> Schrift.Betrag
        klein -> Schrift.Zeile.copy(fontSize = 14.sp)
        else -> Schrift.Zeile
    }
    Layout(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = hoehe)
            .drawWithContent {
                drawContent()
                if (linie) {
                    val y = size.height - 0.5.dp.toPx()
                    drawLine(Farbe.Linie, Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
                }
            }
            .padding(vertical = 6.dp),
        content = {
            Text(name, style = if (klein) Schrift.Klein else Schrift.Name, color = Farbe.Leise)
            Text(
                buildAnnotatedString {
                    append(wert)
                    if (einheit != null) {
                        append(" ")
                        withStyle(
                            SpanStyle(
                                color = Farbe.Leise,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = 0.72.sp,
                            ),
                        ) { append(einheit) }
                    }
                },
                style = wertStil,
                color = Farbe.Schrift,
                textAlign = TextAlign.End,
                maxLines = if (einpassen) 1 else Int.MAX_VALUE,
                softWrap = !einpassen,
                autoSize = if (einpassen) {
                    TextAutoSize.StepBased(
                        minFontSize = 9.sp,
                        maxFontSize = wertStil.fontSize,
                        stepSize = 0.5.sp,
                    )
                } else null,
            )
        },
    ) { teile, grenzen ->
        val breite = grenzen.maxWidth
        val luecke = 12.dp.roundToPx()
        val frei = Constraints(maxWidth = breite)

        val links = teile[0].measure(frei)
        if (einpassen) {
            // Immer nebeneinander: Der Wert bekommt, was neben der
            // Beschriftung uebrig ist, und passt seine Schrift daran an.
            val rest = (breite - links.width - luecke).coerceAtLeast(0)
            val wert = teile[1].measure(Constraints(maxWidth = rest))
            val h = maxOf(links.height, wert.height, grenzen.minHeight)
            return@Layout layout(breite, h) {
                links.placeRelative(0, (h - links.height) / 2)
                wert.placeRelative(breite - wert.width, (h - wert.height) / 2)
            }
        }
        // Wie breit waere der Wert in EINER Zeile? Danach entscheidet sich,
        // ob er neben die Beschriftung passt.
        val einzeilig = teile[1].maxIntrinsicWidth(Constraints.Infinity)
        val nebeneinander = links.width + luecke + einzeilig <= breite
        val rechts = teile[1].measure(
            if (nebeneinander) Constraints(maxWidth = breite - links.width - luecke) else frei,
        )

        if (nebeneinander) {
            val h = maxOf(links.height, rechts.height, grenzen.minHeight)
            layout(breite, h) {
                links.placeRelative(0, (h - links.height) / 2)
                rechts.placeRelative(breite - rechts.width, (h - rechts.height) / 2)
            }
        } else {
            val zwischen = 2.dp.roundToPx()
            val inhalt = links.height + zwischen + rechts.height
            val h = maxOf(inhalt, grenzen.minHeight)
            val oben = (h - inhalt) / 2
            layout(breite, h) {
                links.placeRelative(0, oben)
                rechts.placeRelative(breite - rechts.width, oben + links.height + zwischen)
            }
        }
    }
}

/**
 * Eine Zeile im Verlauf.
 *
 * Vorn ein Pfeil im Kreis fuer die Richtung - gruen herein, leise hinaus -,
 * dann was es war und wann, rechts der Betrag. Mehr nicht: Adressen und
 * Blocknummern stehen in der einzelnen Ueberweisung, nicht in der Liste.
 * Der Betrag ist vollstaendig; was hinter der zweiten Nachkommastelle
 * steht, ist kleiner gesetzt.
 */
@Composable
fun Verlaufszeile(
    art: String,
    zeit: String,
    betrag: String,
    eingehend: Boolean,
    modifier: Modifier = Modifier,
    linie: Boolean = false,
    onKlick: (() -> Unit)? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .then(if (onKlick != null) Modifier.clickable(onClick = onKlick) else Modifier)
            .drawWithContent {
                drawContent()
                if (linie) {
                    val y = size.height - 0.5.dp.toPx()
                    // Die Linie beginnt unter dem Text, nicht unter dem Kreis.
                    drawLine(
                        Farbe.Linie, Offset(52.dp.toPx(), y), Offset(size.width, y), 1.dp.toPx(),
                    )
                }
            }
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(40.dp)
                .background(
                    if (eingehend) Farbe.Eingang.copy(alpha = 0.12f) else Farbe.hauch(0.08f),
                    CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Symbol(
                if (eingehend) R.drawable.ic_arrow_down_left_bold
                else R.drawable.ic_arrow_up_right_bold,
                17.dp,
                farbe = if (eingehend) Farbe.Eingang else Farbe.Weich,
            )
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                art,
                style = Schrift.Zeile.copy(fontSize = 16.sp),
                color = Farbe.Schrift,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                zeit,
                style = Schrift.Hinweis,
                color = Farbe.Leise,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            // [betrag] kommt fertig geschrieben herein, in der Sprache der App.
            betragText(
                betrag,
                if (eingehend) Farbe.Eingang else Farbe.Schrift,
                LocalTexte.current.locale,
            ),
            style = Schrift.Betrag.copy(fontSize = 16.5.sp, fontWeight = FontWeight.Normal),
            maxLines = 1,
            softWrap = false,
        )
    }
}

/**
 * Die ganze Adresse in Vierergruppen, Anfang und Ende hervorgehoben.
 *
 * Hervorgehoben sind die ersten vier und die letzten sechs Zeichen: Das
 * sind die Stellen, an denen das Auge beim Vergleichen ansetzt. Gezeigt
 * wird trotzdem JEDES Zeichen - die Farbe hilft beim Ansetzen, sie ersetzt
 * das Vergleichen nicht.
 *
 * Sechs Gruppen je Zeile, wie in der Vorlage. Passen sechs nicht in die
 * Breite (schmales Geraet, grosse Schrift), werden es weniger; eine Gruppe
 * wird nie umbrochen und nie abgeschnitten.
 */
@Composable
fun AdresseInGruppen(
    adresse: String,
    modifier: Modifier = Modifier,
    stil: TextStyle = Schrift.AdresseKlein,
    luecke: Dp = 8.dp,
    // Sechs Gruppen je Zeile halten und dafuer die Schrift so weit
    // verkleinern wie noetig (hoechstens um ein Fuenftel), statt in eine
    // Zeile mehr umzubrechen. Fuer den Pruefschritt bei grosser
    // Systemschrift (REGELN.md, Regel 29).
    einpassen: Boolean = false,
) {
    Gruppenraster(
        text = adresse,
        gruppe = 4,
        spalten = 6,
        modifier = modifier,
        stil = stil,
        luecke = luecke,
        zeilenabstand = 6.dp,
        einpassen = einpassen,
        betont = { stelle -> stelle < 4 || stelle >= adresse.length - 6 },
    )
}

/**
 * Die Kennung einer Ueberweisung: 64 Hex-Zeichen in Achtergruppen, vier je
 * Zeile. Nichts ist hervorgehoben - eine Kennung vergleicht man nicht mit
 * dem Auge, man kopiert sie.
 */
@Composable
fun KennungInGruppen(
    kennung: String,
    modifier: Modifier = Modifier,
    stil: TextStyle = Schrift.Kurz,
) {
    Gruppenraster(
        text = kennung,
        gruppe = 8,
        spalten = 4,
        modifier = modifier,
        stil = stil,
        luecke = 8.dp,
        zeilenabstand = 4.dp,
        betont = { false },
    )
}

/**
 * Ein langer Wert in gleich breiten Gruppen, [spalten] je Zeile.
 *
 * Passen so viele nicht in die Breite (schmales Geraet, grosse Schrift),
 * werden es weniger. Eine Gruppe wird nie umbrochen und nie abgeschnitten,
 * und kein Zeichen faellt weg.
 */
@Composable
private fun Gruppenraster(
    text: String,
    gruppe: Int,
    spalten: Int,
    modifier: Modifier,
    stil: TextStyle,
    luecke: Dp,
    zeilenabstand: Dp,
    betont: (Int) -> Boolean,
    einpassen: Boolean = false,
) {
    val messer = rememberTextMeasurer()
    val dichte = LocalDensity.current
    val gruppenbreite = remember(stil, dichte, gruppe) {
        messer.measure("0".repeat(gruppe), stil).size.width
    }
    val gruppen = remember(text, gruppe) { text.chunked(gruppe) }

    // Fuer einen Bildschirmleser ist der Wert EIN Ding: die ganze Adresse,
    // nicht elf Bruchstuecke. Die Gruppen bleiben als Text darunter stehen.
    BoxWithConstraints(
        modifier.semantics(mergeDescendants = true) { contentDescription = text },
    ) {
        val lueckePx = with(dichte) { luecke.roundToPx() }
        val passen =
            if (!constraints.hasBoundedWidth) spalten
            else ((constraints.maxWidth + lueckePx) / (gruppenbreite + lueckePx))
                .coerceIn(1, spalten)
        // Wie stark die Schrift schrumpfen muesste, damit alle [spalten]
        // Gruppen in eine Zeile passen. Bis zu einem Fuenftel wird das
        // gemacht; darunter wird doch umbrochen - lesbar geht vor.
        val faktor =
            if (!einpassen || passen == spalten || gruppenbreite == 0) 1f
            else (constraints.maxWidth - (spalten - 1) * lueckePx).toFloat() /
                (spalten * gruppenbreite)
        val schrumpft = faktor < 1f && faktor >= 0.8f
        val jeZeile = if (schrumpft) spalten else passen
        // Eine Spur unter dem Faktor, damit Rundung keine Gruppe abschneidet.
        val zeilenstil = if (schrumpft) stil.copy(fontSize = stil.fontSize * (faktor * 0.985f)) else stil

        Column(verticalArrangement = Arrangement.spacedBy(zeilenabstand)) {
            gruppen.chunked(jeZeile).forEachIndexed { z, zeile ->
                Row(horizontalArrangement = Arrangement.spacedBy(luecke)) {
                    zeile.forEachIndexed { s, stueck ->
                        val anfang = (z * jeZeile + s) * gruppe
                        Text(
                            buildAnnotatedString {
                                stueck.forEachIndexed { i, zeichen ->
                                    withStyle(
                                        SpanStyle(
                                            color = if (betont(anfang + i)) YskarFarben.BlauHell
                                            else Farbe.Schrift,
                                        ),
                                    ) { append(zeichen) }
                                }
                            },
                            style = zeilenstil,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    repeat(jeZeile - zeile.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

// ----------------------------------------------------------------- Schalter

/**
 * Der Schalter: 52 x 32 dp sichtbar, 52 x 44 beruehrbar.
 *
 * Die Vorlage zeichnet nur den Zustand "an". "Aus" ist hier ergaenzt:
 * dieselbe Form ohne Blau, der Knopf links und eine Spur dunkler - damit
 * man an Form UND Farbe sieht, was gilt.
 */
@Composable
fun Umschalter(
    an: Boolean,
    onWechsel: (Boolean) -> Unit,
    beschreibung: String,
    modifier: Modifier = Modifier,
    aktiv: Boolean = true,
) {
    val versatz by animateDpAsState(if (an) 23.dp else 3.dp, label = "schalter")
    Box(
        modifier
            .size(width = 52.dp, height = 44.dp)
            .alpha(if (aktiv) 1f else 0.45f)
            .toggleable(value = an, enabled = aktiv, role = Role.Switch, onValueChange = onWechsel)
            .semantics { contentDescription = beschreibung },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            Modifier.size(width = 52.dp, height = 32.dp).then(
                if (an) {
                    Modifier.gefasst(
                        16.dp,
                        Brush.verticalGradient(listOf(YskarFarben.Blau, YskarFarben.BlauTief)),
                        Color.Transparent,
                    )
                } else {
                    Modifier.gefasst(
                        16.dp, SolidColor(Farbe.hauch(0.10f)), Farbe.Linie, 0f,
                    )
                },
            ),
        )
        Box(
            Modifier
                .offset(x = versatz)
                .size(26.dp)
                .shadow(3.dp, CircleShape)
                .background(if (an) Farbe.Schrift else Farbe.Weich, CircleShape),
        )
    }
}

/** Einstellung mit Schalter: Name, darunter ein Satz, rechts der Schalter. */
@Composable
fun Schaltzeile(
    titel: String,
    an: Boolean,
    onWechsel: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    hinweis: String? = null,
    aktiv: Boolean = true,
) {
    Row(
        modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(titel, style = Schrift.Zeile, color = Farbe.Schrift)
            if (hinweis != null) Text(hinweis, style = Schrift.Hinweis, color = Farbe.Leise)
        }
        Umschalter(an, onWechsel, titel, aktiv = aktiv)
    }
}

// -------------------------------------------------------------- Ziffernfeld

/**
 * Das Ziffernfeld der Vorlage: Kreise von 76 dp, 28 dp nebeneinander und
 * 16 dp untereinander.
 *
 * Es bleibt ein eigenes Feld: Eine fremde Tastatur sieht jede Eingabe
 * (REGELN.md, Regel 15).
 *
 * [onFinger]: Mit einer Funktion steht links neben der Null - unter der
 * Sieben - der Fingerabdruck: nur das Symbol, ohne Kreis, wie rechts der
 * Rueckschritt, aber im Blau der Helferknoepfe. Ohne bleibt die Stelle
 * leer. [fingerBeschreibung] ist, was ein Bildschirmleser dazu sagt; die
 * Taste selbst traegt kein Wort.
 */
@Composable
fun Ziffernfeld(
    laenge: Int,
    ziffern: String,
    aktiv: Boolean,
    onZiffern: (String) -> Unit,
    modifier: Modifier = Modifier,
    knapp: Boolean = false,
    onFinger: (() -> Unit)? = null,
    // null: "Mit Fingerabdruck" in der Sprache der App.
    fingerBeschreibung: String? = null,
    fingerAktiv: Boolean = true,
) {
    // Auf dem Bildschirm zum Unterschreiben stehen ueber dem Feld noch die
    // Zahlen der Ueberweisung. Dort ist es eine Spur kleiner (68 dp), wie in
    // der Vorlage - damit alles ohne Rollen dasteht.
    val kante = if (knapp) 68.dp else 76.dp
    val quer = if (knapp) 30.dp else 28.dp
    val hoch = if (knapp) 12.dp else 16.dp

    Column(
        modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(hoch),
    ) {
        for (reihe in listOf("123", "456", "789")) {
            Row(horizontalArrangement = Arrangement.spacedBy(quer)) {
                for (z in reihe) {
                    Zifferntaste(z.toString(), aktiv, kante) {
                        if (ziffern.length < laenge) onZiffern(ziffern + z)
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(quer)) {
            // Links unten: der Fingerabdruck, wo es einen gibt. Ein Tipp
            // aus Versehen oeffnet nur die Frage des Systems nach dem
            // Finger - die laesst sich abbrechen, und ohne Finger geht
            // nichts auf.
            if (onFinger != null) {
                Fingertaste(
                    fingerBeschreibung ?: LocalTexte.current.allgemein.mitFingerabdruck,
                    fingerAktiv, kante, onFinger,
                )
            } else {
                Box(Modifier.size(kante))
            }
            Zifferntaste("0", aktiv, kante) {
                if (ziffern.length < laenge) onZiffern(ziffern + "0")
            }
            Zifferntaste(null, aktiv, kante) {
                if (ziffern.isNotEmpty()) onZiffern(ziffern.dropLast(1))
            }
        }
    }
}

/** Eine Taste; [ziffer] null ist der Rueckschritt, ohne Kreis. */
@Composable
private fun Zifferntaste(ziffer: String?, aktiv: Boolean, kante: Dp, onKlick: () -> Unit) {
    val ruetteln = kurzesRuetteln()
    val loeschenText = LocalTexte.current.allgemein.letzteZifferLoeschen
    Box(
        Modifier
            .size(kante)
            .alpha(if (aktiv) 1f else 0.4f)
            // Nur ein Umriss, wie die Nebenknoepfe der Website: Die Ziffer
            // ist die Hauptsache, nicht der Kreis.
            .then(
                if (ziffer != null) {
                    Modifier.gefasst(kante / 2, SolidColor(Color.Transparent), Farbe.eis(0.24f))
                } else Modifier.clip(CircleShape),
            )
            .then(
                if (aktiv) Modifier.clickable(role = Role.Button) { ruetteln(); onKlick() }
                else Modifier,
            )
            .then(
                if (ziffer == null) Modifier.semantics { contentDescription = loeschenText }
                else Modifier,
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (ziffer != null) {
            Text(
                ziffer,
                style = if (kante < 76.dp) Schrift.Ziffer.copy(fontSize = 26.sp, lineHeight = 26.sp)
                else Schrift.Ziffer,
                color = Farbe.Schrift,
            )
        }
        else Symbol(R.drawable.ic_backspace_fill, 26.dp)
    }
}

/**
 * Der Fingerabdruck im Ziffernfeld: NUR DAS SYMBOL, ohne Kreis - wie der
 * Rueckschritt auf der anderen Seite der Null. Die Kreise gehoeren den
 * Ziffern; was keine Ziffer ist, hat keinen.
 *
 * Blau statt weiss, damit er sich vom Rueckschritt unterscheidet: Der eine
 * loescht eine Ziffer, der andere fragt nach dem Finger. Die Flaeche zum
 * Treffen ist trotzdem so gross wie eine Zifferntaste.
 */
@Composable
private fun Fingertaste(beschreibung: String, aktiv: Boolean, kante: Dp, onKlick: () -> Unit) {
    val ruetteln = kurzesRuetteln()
    Box(
        Modifier
            .size(kante)
            .alpha(if (aktiv) 1f else 0.4f)
            .clip(CircleShape)
            .then(
                if (aktiv) Modifier.clickable(role = Role.Button) { ruetteln(); onKlick() }
                else Modifier,
            )
            .semantics { contentDescription = beschreibung },
        contentAlignment = Alignment.Center,
    ) {
        Symbol(
            R.drawable.ic_fingerprint_bold,
            if (kante < 76.dp) 28.dp else 32.dp,
            farbe = Farbe.BlauSchrift,
        )
    }
}

// ------------------------------------------------------------------- Buehne

/**
 * Der Rahmen, in dem jeder Bildschirm steht: der Grund bis hinter Status-
 * und Navigationsleiste, darin der Inhalt mit den Raendern, die diese
 * Leisten verlangen.
 *
 * EIN BAUSTEIN FUER APP UND TESTRAHMEN, und das ist der Punkt: Die
 * Bildschirmfotos entstanden bisher in einem Rahmen OHNE diese Raender und
 * zeigten jeden Bildschirm darum hoeher, als er in der App ist. Ob ein
 * Sendebildschirm rollen muss (REGELN.md, Regel 29), liess sich am Bild
 * nicht entscheiden. Zeichnen beide durch dieselbe Funktion, kann das Bild
 * nicht mehr von der App abweichen.
 *
 * Das Scaffold ist durchsichtig, sonst laege seine einfarbige Flaeche ueber
 * dem Verlauf des Grunds. Die Schriftfarbe steht deshalb ausdruecklich da:
 * Zu "durchsichtig" kennt Material keine passende Schriftfarbe und nimmt
 * Schwarz.
 */
@Composable
fun Buehne(inhalt: @Composable BoxScope.() -> Unit) {
    Grund {
        androidx.compose.material3.Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = Color.Transparent,
            contentColor = Farbe.Schrift,
        ) { raender ->
            Box(Modifier.fillMaxSize().padding(raender), content = inhalt)
        }
    }
}

// -------------------------------------------------------------------- Blatt

/**
 * Ein Bildschirm, der die Hoehe fuellt und nur rollt, wenn er muss.
 *
 * Innen duerfen Abstandhalter mit `weight` stehen - sie verteilen die Luft,
 * die uebrig ist. Passt der Inhalt nicht (kleines Geraet, grosse Schrift),
 * schrumpfen sie auf null, und der Bildschirm rollt.
 *
 * Der Kniff: Eine rollende Spalte bietet ihren Kindern unendlich viel
 * Hoehe an, und in unendlicher Hoehe gilt kein Gewicht. Mit einer
 * MINDESThoehe in der Groesse des Fensters rechnet die Spalte aber mit
 * genau dieser Hoehe - und waechst nur, wenn der Inhalt mehr verlangt.
 */
@Composable
fun Blatt(
    modifier: Modifier = Modifier,
    mittig: Boolean = false,
    inhalt: @Composable ColumnScope.() -> Unit,
) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .heightIn(min = maxHeight),
            horizontalAlignment = if (mittig) Alignment.CenterHorizontally else Alignment.Start,
            content = inhalt,
        )
    }
}

/**
 * Das Linienmuster hinter dem oberen Teil eines Bildschirms (Einstieg,
 * PIN, Sperre), nach unten ausgeblendet.
 *
 * In der Vorlage liegt ueber dem Muster eine zweite Maske von oben nach
 * unten. Dafuer braucht es eine eigene Zeichenebene: Erst das Muster, dann
 * ein Verlauf, der nur dessen Deckkraft stehen laesst (`DstIn`). Ohne die
 * Ebene wuerde der Verlauf auch den Grund darunter ausblenden.
 *
 * Gehoert als ERSTES in eine `Box`, damit der Inhalt darueber liegt.
 */
@Suppress("UNUSED_PARAMETER")
@Composable
fun Musterhimmel(hoehe: Dp, deckkraft: Float, modifier: Modifier = Modifier) {
    // Seit dem 12.11.2026 leer: Das Linienmuster gehoerte zum Design
    // "Praegung". Den Himmel zeichnet jetzt der Grund selbst ([Grund]), auf
    // jedem Bildschirm. Der Baustein bleibt als Platzhalter derselben Hoehe,
    // damit kein Bildschirm seine Ordnung aendert.
    Box(modifier.fillMaxWidth().height(hoehe))
}

// ------------------------------------------------------- Nachtraege zu 2b

/** Kurzadresse fuer Listen: acht Zeichen vom Anfang, vier vom Ende. */
fun adresseKnapp(adresse: String): String =
    if (adresse.length <= 14) adresse else adresse.take(8) + "…" + adresse.takeLast(4)

/**
 * Blauer Streifen fuer einen Hinweis, der kein Fehler ist ("Wallet
 * angelegt …", "1 Zahlung wartet auf einen Block"). Das Gegenstueck zu
 * [Warnhinweis]; [vorne] ist Platz fuer ein Symbol oder das Zeichen.
 */
@Composable
fun Hinweisstreifen(
    text: String,
    modifier: Modifier = Modifier,
    vorne: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .gefasst(
                rundung = 16.dp,
                grund = SolidColor(YskarFarben.Blau.copy(alpha = 0.12f)),
                rand = YskarFarben.Blau.copy(alpha = 0.28f),
                glanz = 0f,
            )
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        vorne?.invoke()
        Text(text, style = Schrift.Klein, color = Farbe.BlauSchrift, modifier = Modifier.weight(1f))
    }
}

/**
 * Platzhalter in der Form dessen, was noch laedt.
 *
 * Ein ruhiger Balken statt eines drehenden Kreises: Er zeigt, WO gleich
 * etwas steht, und der Bildschirm springt nicht, wenn es kommt. Er bewegt
 * sich nicht - die Vorlage zeichnet ihn still, und ein Schimmern, das
 * minutenlang ueber ein Funkloch laeuft, behauptet Fortschritt, den es
 * nicht gibt.
 */
@Composable
fun Platzhalter(breite: Dp, hoehe: Dp, modifier: Modifier = Modifier, hell: Boolean = false) {
    val a = if (hell) 0.06f else 0.05f
    val b = if (hell) 0.12f else 0.10f
    Box(
        modifier
            .size(breite, hoehe)
            .background(
                Brush.horizontalGradient(
                    listOf(
                        Farbe.hauch(a),
                        Farbe.hauch(b),
                        Farbe.hauch(a),
                    ),
                ),
                RoundedCornerShape(minOf(hoehe / 2, 14.dp)),
            ),
    )
}

/**
 * Ein Stueck einer Karte, die sich ueber mehrere Zeilen einer Liste zieht.
 *
 * Im Verlauf ist jede Tagesgruppe eine Karte. Als EIN Listenelement
 * gezeichnet, muesste eine Gruppe mit dreihundert Eintraegen auf einmal
 * aufgebaut werden - die Liste waere nicht mehr faul. Darum zeichnet jede
 * Zeile ihr Stueck: die erste die runden Ecken oben, die letzte die unten,
 * alle die Seitenkanten.
 *
 * [von] und [bis] sagen, welchen Abschnitt des Verlaufs der Flaeche
 * (0 = oben, 1 = unten) dieses Stueck traegt, damit die Karte ueber alle
 * Zeilen EINEN Verlauf hat und nicht in jeder Zeile neu anfaengt.
 *
 * Der Rand ist ein Rahmen um die gedachte ganze Karte, so weit nach oben
 * und unten hinausgeschoben, dass in dieser Zeile nur der Teil zu sehen
 * ist, der hierher gehoert. Den Rest schneidet die Zeile selbst ab.
 */
fun Modifier.kartenstueck(
    erste: Boolean,
    letzte: Boolean,
    von: Float,
    bis: Float,
    rundung: Dp = 22.dp,
): Modifier {
    val oben = if (erste) rundung else 0.dp
    val unten = if (letzte) rundung else 0.dp
    fun ton(t: Float) = androidx.compose.ui.graphics.lerp(Farbe.FlaecheOben, Farbe.FlaecheUnten, t)
    return this
        .clip(RoundedCornerShape(topStart = oben, topEnd = oben, bottomStart = unten, bottomEnd = unten))
        .background(Brush.verticalGradient(listOf(ton(von), ton(bis))))
        .drawWithContent {
            drawContent()
            val px = 1.dp.toPx()
            val r = rundung.toPx()
            val ueber = r * 2f
            val y0 = if (erste) 0f else -ueber
            val y1 = if (letzte) size.height else size.height + ueber
            if (erste) {
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        0f to Color.Transparent,
                        1f to Color.Transparent,
                        startY = px,
                        endY = px + r,
                    ),
                    topLeft = Offset(px * 1.5f, px * 1.5f),
                    size = Size(size.width - px * 3f, y1 - px * 3f),
                    cornerRadius = CornerRadius(maxOf(r - px * 1.5f, 0f)),
                    style = Stroke(px),
                )
            }
            drawRoundRect(
                color = Farbe.Linie,
                topLeft = Offset(px / 2f, y0 + px / 2f),
                size = Size(size.width - px, y1 - y0 - px),
                cornerRadius = CornerRadius(maxOf(r - px / 2f, 0f)),
                style = Stroke(px),
            )
        }
}

// ------------------------------------------------------------------ QR-Karte

/**
 * Der QR-Code auf seiner weissen Karte, mit dem Zeichen in der Mitte.
 *
 * Weiss auch auf dunklem Grund: Kameras lesen dunkel auf hell deutlich
 * zuverlaessiger, und ein weisses Feld sagt von selbst "halt das hier hin".
 *
 * DIE RUHEZONE KOMMT VON DER KARTE (REGELN.md, Regel 30). `QrCode.matrix`
 * liefert den Code ohne Rand; die Karte laesst ringsum VIER MODULE frei -
 * so viel verlangt die Norm, und so viel waren die frueheren 22 dp auch
 * gemeint. Als feste Zahl stimmten sie nur fuer eine Modulgroesse: Seit der
 * Code fuer das Zeichen mit der hoechsten Fehlerkorrektur geschrieben wird,
 * hat er 37 Module statt weniger groessere, und 22 dp waeren nur noch gut
 * drei. Darum wird jetzt in Modulen gerechnet, nicht in dp.
 *
 * Jedes Modul ist gleich viele Bildpunkte breit. Die Kante der Karte wird
 * dazu durch (Module + 8) geteilt und ABGERUNDET; was uebrig bleibt, kommt
 * der Ruhezone zugute. Vergroessert wird mit `FilterQuality.None`, damit
 * die Kanten hart bleiben.
 *
 * Das Zeichen sitzt auf einem weissen Fenster von 22 % der Kante. Dass der
 * Code das traegt, prueft `QrCodeTest` - ob ein Telefon ihn in der Hand
 * liest, prueft nur ein zweites Telefon.
 */
@Composable
fun QrKarte(inhalt: String, modifier: Modifier = Modifier) {
    val matrix = remember(inhalt) { QrCode.matrix(inhalt) }
    val bild = remember(matrix) {
        matrix?.let { m ->
            val vorne = Farbe.Grund.toArgb()
            val hinten = Color.White.toArgb()
            Bitmap.createBitmap(m.width, m.height, Bitmap.Config.ARGB_8888).also { b ->
                for (y in 0 until m.height) {
                    for (x in 0 until m.width) b.setPixel(x, y, if (m.get(x, y)) vorne else hinten)
                }
            }.asImageBitmap()
        }
    }

    BoxWithConstraints(
        modifier.aspectRatio(1f).background(Color.White, RoundedCornerShape(24.dp)),
        contentAlignment = Alignment.Center,
    ) {
        if (matrix == null || bild == null) {
            Text(
                LocalTexte.current.allgemein.qrNichtDarstellbar,
                style = Schrift.Hinweis,
                color = Farbe.Grund,
            )
        } else {
            val modulPx = constraints.maxWidth / (matrix.width + 2 * QrCode.RUHEZONE)
            val kante = with(LocalDensity.current) { (modulPx * matrix.width).toDp() }
            val fenster = kante * QrCode.FENSTER

            Box(Modifier.size(kante), contentAlignment = Alignment.Center) {
                Image(
                    bitmap = bild,
                    contentDescription = LocalTexte.current.allgemein.qrDerAdresse,
                    // fillMaxSIZE und FillBounds, nicht fillMaxWidth und Fit:
                    // Das Bild ist nur modulgross. Mit Fit rechnet Compose
                    // auf die kleinere Seite, und der Code stuende als
                    // Briefmarke mitten auf der Karte.
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.FillBounds,
                    filterQuality = androidx.compose.ui.graphics.FilterQuality.None,
                )
                Box(
                    Modifier
                        .size(fenster)
                        .background(
                            Color.White,
                            RoundedCornerShape(fenster * QrCode.FENSTER_RUNDUNG),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    ZeichenRuhig(groesse = fenster * QrCode.ZEICHEN)
                }
            }
        }
    }
}

// ------------------------------------------------------------------ Eingabe

/**
 * Ein Eingabefeld ohne eigenen Rahmen - der Rahmen ist die Flaeche, in der
 * es steht.
 *
 * Mehr als die Schrift ist nicht zu sehen: der Text, solange er leer ist
 * der [platzhalter] in leiser Farbe, und die Schreibmarke im Blau des
 * Zeichens. Welche Tastatur kommt und was sie vorschlagen darf, bestimmt
 * der Aufrufer ueber [tastatur] - fuer eine Adresse etwa eine, die nichts
 * lernt und nichts vorschlaegt.
 */
@Composable
fun Eingabe(
    wert: String,
    onWert: (String) -> Unit,
    platzhalter: String,
    modifier: Modifier = Modifier,
    stil: TextStyle = Schrift.Text.copy(fontSize = 16.sp, lineHeight = 22.sp),
    einzeilig: Boolean = true,
    tastatur: KeyboardOptions = KeyboardOptions.Default,
) {
    BasicTextField(
        value = wert,
        onValueChange = onWert,
        modifier = modifier,
        textStyle = stil.copy(color = Farbe.Schrift),
        singleLine = einzeilig,
        keyboardOptions = tastatur,
        cursorBrush = SolidColor(YskarFarben.BlauHell),
        decorationBox = { innen ->
            Box {
                if (wert.isEmpty()) {
                    Text(platzhalter, style = stil, color = Farbe.Leise.copy(alpha = 0.7f))
                }
                innen()
            }
        },
    )
}

// ------------------------------------------------------------- Einzelheiten

/**
 * "Einzelheiten": was nicht jeder braucht, eingeklappt.
 *
 * DER GEDANKE (Wunsch des Besitzers, 12.11.2026): Die Wallet soll niemanden
 * mit Blocknummern, Kennungen und Fassungen empfangen, der nur wissen will,
 * ob sein Geld angekommen ist. Diese Angaben sind darum NICHT WEG - sie
 * stehen einen Tipp entfernt, vollstaendig wie vorher. Was man zum
 * Entscheiden braucht (Betrag, Adresse, Gebuehr, Summe), steht nie hier
 * drin.
 *
 * [offen] ist nur der Anfangszustand - fuer den Testrahmen, der beide
 * Zustaende aufnimmt.
 */
@Composable
fun Einzelheiten(
    modifier: Modifier = Modifier,
    offen: Boolean = false,
    inhalt: @Composable ColumnScope.() -> Unit,
) {
    val t = LocalTexte.current
    var auf by rememberSaveable { mutableStateOf(offen) }
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clickable(
                    role = Role.Button,
                    onClickLabel = if (auf) t.allgemein.einzelheitenZu else t.allgemein.einzelheitenAuf,
                ) { auf = !auf }
                .testTag("einzelheiten")
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                t.allgemein.einzelheiten,
                style = Schrift.Zeile,
                color = Farbe.Weich,
                modifier = Modifier.weight(1f),
            )
            // Der Winkel zeigt nach unten und, aufgeklappt, nach oben.
            Symbol(
                R.drawable.ic_caret_right_bold, 16.dp,
                Modifier.rotate(if (auf) -90f else 90f),
                farbe = Farbe.Leise,
            )
        }
        if (auf) inhalt()
    }
}
