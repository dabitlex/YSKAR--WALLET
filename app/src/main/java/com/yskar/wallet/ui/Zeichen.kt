package com.yskar.wallet.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Das YSKAR-Zeichen.
 *
 * Es ist kein Logo, sondern ein Messgeraet. Drei Teile - zwei Baender und
 * ein Stamm - leuchten nacheinander auf und sagen dabei etwas aus:
 *
 *   Sperre   je zwei eingegebene Ziffern ein Band, die ungerade Ziffer halb
 *   Senden   Band 1 angenommen, Band 2 im Block, Stamm ab zwei Bestaetigungen
 *   Wallet   alles an, wenn offen und der Stand frisch ist
 *
 * Die Pfaddaten stehen Zeichen fuer Zeichen so in der bestehenden Wallet
 * (src/bilder/zeichen.svg, src/ui/zeichenform.ts). Sie sind das einzige
 * gestalterische Erbe, das uebernommen wurde - und das wichtigste: Andere
 * Wallets haben ein Logo, dieses sagt etwas.
 */

private const val STAMM =
    "M688.0 969.2 C688.0 850.6 687.5 845.0 673.8 817.5 C659.4 788.7 647.4 777.8 562.0 " +
        "716.5 C479.0 656.9 470.2 650.8 468.8 651.6 C467.2 652.6 467.8 790.6 469.5 803.3 " +
        "C475.2 846.9 493.4 880.1 528.5 910.7 C533.5 915.0 545.6 925.8 555.5 934.6 C565.4 " +
        "943.4 584.5 960.2 598.0 972.0 C641.5 1010.0 659.5 1027.9 677.2 1050.8 C681.6 " +
        "1056.4 685.8 1061.0 686.6 1061.0 C687.8 1061.0 688.0 1048.0 688.0 969.2 Z"

private const val BAND_LINKS =
    "M727.9 847.0 C797.3 765.8 810.3 667.4 760.3 600.9 C746.5 582.5 735.3 573.0 680.5 " +
        "532.7 C669.5 524.7 644.3 506.1 624.5 491.4 C604.7 476.8 584.7 462.0 580.0 458.6 " +
        "C482.6 387.3 367.5 300.7 338.0 276.6 C309.8 253.6 277.6 223.1 254.6 197.5 C249.1 " +
        "191.4 244.2 186.5 243.6 186.5 C242.0 186.5 241.5 301.3 243.0 317.9 C247.5 367.3 " +
        "262.8 415.1 285.7 451.2 C311.5 491.7 341.0 518.7 416.5 571.0 C447.3 592.3 445.7 " +
        "591.2 495.5 626.5 C615.1 711.4 644.9 733.8 665.5 754.4 C695.2 784.2 712.4 815.2 " +
        "716.1 845.5 C717.9 859.9 717.0 859.8 727.9 847.0 Z"

private const val BAND_RECHTS =
    "M816.3 594.8 C829.6 582.7 835.3 577.9 854.0 562.9 C934.5 498.6 960.5 472.6 982.2 " +
        "435.1 C997.2 409.2 1007.7 377.3 1010.9 347.8 C1012.3 335.3 1012.4 254.3 1011.0 " +
        "253.5 C1010.5 253.2 1003.6 258.2 995.8 264.6 C966.5 288.7 916.7 324.5 833.0 381.5 " +
        "C786.7 413.1 688.1 481.9 684.2 485.3 C680.9 488.4 681.3 489.1 690.8 496.0 C772.8 " +
        "556.1 786.1 568.4 800.6 597.2 C804.9 605.7 804.1 605.8 816.3 594.8 Z"

/** Der Ausschnitt aus der Vorlage: viewBox="187.5 184 879 879". */
private const val VB_X = 187.5f
private const val VB_Y = 184f
private const val VB_KANTE = 879f

enum class Teil { AUS, HALB, AN, FEHLER }

/**
 * Das Zeichen zeichnen.
 *
 * [baender] ist links, rechts, Stamm - in der Reihenfolge, in der sie
 * aufleuchten.
 */
@Composable
fun Zeichen(
    links: Teil = Teil.AN,
    rechts: Teil = Teil.AN,
    stamm: Teil = Teil.AN,
    groesse: Dp = 64.dp,
    modifier: Modifier = Modifier,
    beschreibung: String? = null,
) {
    val formen = remember {
        listOf(BAND_LINKS, BAND_RECHTS, STAMM).map {
            PathParser().parsePathString(it).toPath()
        }
    }

    // Weiches Aufleuchten statt Umschalten. Die drei Werte werden
    // EINZELN angelegt, nicht in einer Schleife: Composables in `map`
    // aufzurufen ist zwar uebersetzbar, aber die Zustandsplaetze sind dann
    // nicht mehr offensichtlich - und genau dort lagen die Baender beim
    // ersten Versuch falsch.
    val wertLinks by animateFloatAsState(fuellung(links), tween(260), label = "links")
    val wertRechts by animateFloatAsState(fuellung(rechts), tween(260), label = "rechts")
    val wertStamm by animateFloatAsState(fuellung(stamm), tween(260), label = "stamm")

    val fehler = links == Teil.FEHLER || rechts == Teil.FEHLER || stamm == Teil.FEHLER

    // Wer das Zeichen als Anzeige benutzt, sagt hier in Worten, was es
    // zeigt ("Zwei von sechs Ziffern eingegeben"). Ohne das ist die Anzeige
    // fuer jemanden, der den Bildschirm vorgelesen bekommt, nicht da.
    val gesagt =
        if (beschreibung != null) Modifier.semantics { contentDescription = beschreibung }
        else Modifier

    Canvas(modifier.size(groesse).then(gesagt)) {
        val kante = minOf(size.width, size.height)
        val faktor = kante / VB_KANTE

        // Der Verlauf muss im KOORDINATENRAUM DES ZEICHENS liegen, nicht in
        // dem der Leinwand. Beim ersten Versuch lief er von 0 bis zur
        // Leinwandbreite, waehrend die Pfade bei 187..1066 liegen - alles
        // jenseits davon bekam die geklammerte Endfarbe, und das Zeichen
        // sah aus, als waere ein Band dunkel eingefaerbt.
        val pinsel = if (fehler) {
            Brush.linearGradient(
                colors = listOf(YskarFarben.FehlerHell, YskarFarben.Fehler),
                start = Offset(VB_X, VB_Y),
                end = Offset(VB_X + VB_KANTE, VB_Y + VB_KANTE),
            )
        } else {
            // Derselbe Verlauf wie im Zeichen der bestehenden Wallet.
            Brush.linearGradient(
                colorStops = arrayOf(
                    0f to YskarFarben.BlauHell,
                    0.55f to YskarFarben.Blau,
                    1f to YskarFarben.BlauTief,
                ),
                start = Offset(VB_X + VB_KANTE * 0.1f, VB_Y),
                end = Offset(VB_X + VB_KANTE * 0.75f, VB_Y + VB_KANTE),
            )
        }

        // Die Vorlage zeichnet den Umriss 9 Einheiten breit im 879er Feld -
        // er waechst also mit dem Zeichen: gut 1 dp bei 104 dp Groesse.
        // Darunter eine Untergrenze von 1,5 Bildpunkten, damit er bei
        // kleinen Zeichen nicht verschwindet.
        val strich = maxOf(9f, 1.5f / faktor)

        scale(faktor, faktor, pivot = Offset.Zero) {
            translate(-VB_X, -VB_Y) {
                zeichneTeil(formen[0], wertLinks, pinsel, strich)
                zeichneTeil(formen[1], wertRechts, pinsel, strich)
                zeichneTeil(formen[2], wertStamm, pinsel, strich)
            }
        }
    }
}

private fun fuellung(teil: Teil): Float = when (teil) {
    Teil.AUS -> 0f
    Teil.HALB -> 0.45f
    Teil.AN -> 1f
    Teil.FEHLER -> 1f
}

private fun DrawScope.zeichneTeil(
    form: Path,
    staerke: Float,
    pinsel: Brush,
    strich: Float,
) {
    // Ausgeschaltet bleibt die Form sichtbar: ein Hauch Fuellung und eine
    // helle Kontur. Das Zeichen soll immer ganz zu erkennen sein, auch wenn
    // noch nichts erfuellt ist.
    //
    // Die Kontur weicht in dem Mass, in dem die Fuellung kommt. Bliebe sie
    // stehen, haette das leuchtende Band einen hellen Rand, den die Vorlage
    // nicht hat - dort ist ein Teil entweder Umriss oder Farbe.
    val leer = 1f - staerke.coerceIn(0f, 1f)
    if (leer > 0.01f) {
        drawPath(path = form, color = YskarFarben.ZeichenLeer, alpha = leer)
        drawPath(
            path = form,
            color = YskarFarben.ZeichenAus,
            alpha = leer,
            style = Stroke(width = strich),
        )
    }
    if (staerke <= 0.01f) return
    drawPath(path = form, brush = pinsel, alpha = staerke)
}

/**
 * Wie weit die PIN-Eingabe das Zeichen erleuchtet.
 *
 * Je zwei Ziffern ein Band, die erste Ziffer eines Paares fuellt es halb.
 * Reine Anzeige - die Pruefung der PIN weiss davon nichts. Uebernommen aus
 * `baenderFuerPin` der Vorlage.
 */
fun baenderFuerPin(ziffern: Int, falsch: Boolean, geprueft: Boolean): List<Teil> = when {
    falsch -> listOf(Teil.FEHLER, Teil.FEHLER, Teil.FEHLER)
    geprueft -> listOf(Teil.AN, Teil.AN, Teil.AN)
    else -> (0..2).map { b ->
        when {
            ziffern >= b * 2 + 2 -> Teil.AN
            ziffern == b * 2 + 1 -> Teil.HALB
            else -> Teil.AUS
        }
    }
}

/**
 * Das Zeichen als Fortschritt einer Zahlung.
 *
 * Band 1 angenommen, Band 2 im Block, Stamm ab zwei Bestaetigungen - genau
 * die Lesart aus `yStand` der Vorlage.
 */
fun baenderFuerZahlung(
    angenommen: Boolean,
    imBlock: Boolean,
    bestaetigungen: Int,
): List<Teil> = listOf(
    if (angenommen) Teil.AN else Teil.AUS,
    if (imBlock) Teil.AN else if (angenommen) Teil.HALB else Teil.AUS,
    if (bestaetigungen >= 2) Teil.AN else if (imBlock) Teil.HALB else Teil.AUS,
)

/**
 * Das Zeichen als Praegung - unten rechts auf der Guthabenkarte.
 *
 * Dreimal dieselbe Form uebereinander, im Koordinatenraum der Pfade
 * (879er Feld): eine helle Kopie um (+8, +10) versetzt, eine dunkle um
 * (-6, -8), darueber der Koerper in der Farbe der Karte. Das Auge liest
 * den hellen Rand unten rechts als Licht auf einer Kante und den dunklen
 * oben links als Schatten - die Form scheint in die Karte gedrueckt.
 *
 * Reiner Schmuck: Sie zeigt nichts an und aendert sich nie. Wer am Zeichen
 * etwas ablesen soll, bekommt [Zeichen].
 */
@Composable
fun Praegung(groesse: Dp = 74.dp, modifier: Modifier = Modifier) {
    val formen = remember {
        listOf(BAND_LINKS, BAND_RECHTS, STAMM).map {
            PathParser().parsePathString(it).toPath()
        }
    }
    Canvas(modifier.size(groesse)) {
        val faktor = minOf(size.width, size.height) / VB_KANTE
        scale(faktor, faktor, pivot = Offset.Zero) {
            translate(-VB_X, -VB_Y) {
                translate(8f, 10f) {
                    for (f in formen) drawPath(f, Color.White.copy(alpha = 0.22f))
                }
                translate(-6f, -8f) {
                    for (f in formen) drawPath(f, Color.Black.copy(alpha = 0.55f))
                }
                for (f in formen) drawPath(f, Color(0xFF0E1116))
            }
        }
    }
}

/** Ruhiges, vollstaendiges Zeichen - fuer Kopfzeilen. */
@Composable
fun ZeichenRuhig(groesse: Dp = 28.dp, modifier: Modifier = Modifier) =
    Zeichen(Teil.AN, Teil.AN, Teil.AN, groesse, modifier)

/** Nur Umriss - wenn nichts zu melden ist. */
@Composable
fun ZeichenLeer(groesse: Dp = 64.dp, modifier: Modifier = Modifier) =
    Zeichen(Teil.AUS, Teil.AUS, Teil.AUS, groesse, modifier)

/** Die Farben des Zeichens, auch anderswo gebraucht. */
object YskarFarben {
    val BlauHell = Color(0xFF63D8FF)
    val Blau = Color(0xFF2F7BFF)
    val BlauTief = Color(0xFF1B4FD1)
    /**
     * Der Umriss im Zustand "aus": Weiss 32 %, wie in der Vorlage.
     *
     * Weiss, nicht Blau: Auf dem Graphitgrund liest sich ein blauer Umriss
     * als "halb an". Ein weisser ist eindeutig leer - und das Blau gehoert
     * damit allein dem, was erfuellt ist.
     */
    val ZeichenAus = Color.White.copy(alpha = 0.32f)

    /** Die Fuellung im Zustand "aus": kaum mehr als nichts. */
    val ZeichenLeer = Color.White.copy(alpha = 0.03f)

    /** Falsche PIN: das Rot der Palette, nach oben heller. */
    val Fehler = Color(0xFFFF7A7F)
    val FehlerHell = Color(0xFFFFB3B6)
}

/**
 * Was das Zeichen bei der PIN-Eingabe zeigt, in Worten - fuer jemanden,
 * der den Bildschirm vorgelesen bekommt. Genannt wird nur die ANZAHL der
 * Ziffern, nie eine Ziffer.
 */
fun zifferStand(getippt: Int, von: Int, t: com.yskar.wallet.sprache.Texte): String =
    if (getippt <= 0) t.allgemein.nochKeineZiffer
    else t.allgemein.ziffernEingegeben(getippt, von)
