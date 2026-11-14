package com.yskar.wallet.ui

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.yskar.wallet.R

/**
 * Das Aussehen der YSKAR-Wallet - Design "Erstes Licht".
 *
 * Vorlage: die Website (Vorschau 2, "Erstes Licht", 12.11.2026). Die Werte
 * hier sind die ihres Stylesheets (`:root`), beim Namen der Website genannt;
 * erfunden ist keiner. Davor galt "Praegung" (Graphit, Geist, blauer
 * Hauptknopf) - Marke `vor-erstes-licht`, UMBAU.md.
 *
 * DIE ENTSCHEIDUNGEN, UND WARUM:
 *
 * Nur dunkel. Die Website ist Nacht; ihr heller Fuss ("Tag") ist dort das
 * Ende der Seite, kein zweites Schema. Das alte helle Schema liegt weiter
 * unten in dieser Datei, wird aber nicht benutzt.
 *
 * Zwei Lichter, zwei Bedeutungen - wie auf der Website. KALTES BLAU ist die
 * Kette: das Zeichen, die Kurve, Adressen, Fortschritt. WARMES LAMPENLICHT
 * ist der Mensch: der eine Knopf, mit dem du handelst (senden,
 * unterschreiben, anmelden), und die Beschriftung ueber dem, worum es
 * geht. Gruen heisst weiter nur "kommt herein", Rot nur "da stimmt etwas
 * nicht" - das Lampenlicht ist KEINE Warnfarbe und steht nie fuer Gefahr.
 *
 * Hanken Grotesk fuer alles, was man liest: gross und leicht fuer Zahlen
 * und Aussagen, klein und kraeftig fuer Bedienung. Geist Mono fuer alles,
 * was man Zeichen fuer Zeichen vergleicht. Zahlen stehen ueberall mit
 * festen Ziffernbreiten (`tnum`): Ein Guthaben, das beim Aktualisieren
 * seitlich zuckt, sieht aus, als haette sich mehr geaendert als eine Ziffer.
 */
object Farbe {
    // Die Namen in Klammern sind die der Website (`:root`).

    /** Der Grund der App (night-900) - und in res/values/colors.xml der des Fensters. */
    val Grund = Color(0xFF040A1A)

    /** Oben steht der Himmel heller (night-700) und laeuft bis 38 % aus. */
    val GrundOben = Color(0xFF0B1C40)

    /** Ganz unten wird es noch eine Spur dunkler (night-950). */
    val GrundUnten = Color(0xFF02050F)

    /**
     * Der Hauch, aus dem Linien und Flaechen gemacht sind: ein helles,
     * kaltes Blau (170, 198, 255), immer durchscheinend. Auf Nachtblau sieht
     * reines Weiss mit wenig Deckkraft grau aus - dieses nicht.
     */
    fun hauch(deckkraft: Float): Color = Color(0xFFAAC6FF).copy(alpha = deckkraft)

    /** Die Schriftfarbe, durchscheinend - fuer Umrisse von Knoepfen. */
    fun eis(deckkraft: Float): Color = Schrift.copy(alpha = deckkraft)

    /** Flaechen: kaum mehr als der Grund, nach unten auslaufend. */
    val FlaecheOben = hauch(0.075f)
    val FlaecheUnten = hauch(0.035f)

    /** Haarlinie und Rand (line): 15 %. */
    val Linie = hauch(0.15f)

    /** Schrift auf der Nacht (ice). */
    val Schrift = Color(0xFFEEF4FF)

    /** Beschriftungen, Hinweise (ice-3, auf dem Grund ausgerechnet). */
    val Leise = Color(0xFF8792AB)

    /** Absaetze, Zweitrangiges (ice-2, auf dem Grund ausgerechnet). */
    val Weich = Color(0xFFADB6C8)

    /** Verweise und alles, was zur Kette fuehrt (ribbon-1). */
    val Verweis = Color(0xFF63D8FF)

    /** Nur fuer "kommt herein" (ok). */
    val Eingang = Color(0xFF5BE3AB)

    /** Der Punkt vor einem Ausgang - leise, nicht Rot: Senden ist kein Fehler. */
    val Ausgang = Color(0xFF8792AB)

    /** Fehler und Unwiderrufliches (bad). */
    val Warnung = Color(0xFFFF8E8E)
    val WarnSchrift = Color(0xFFFFC9C9)

    /** Die blau getoenten Nebenknoepfe ("QR-Code scannen", "Einfuegen"). */
    val BlauSchrift = Color(0xFF9CC4FF)

    /**
     * Das Lampenlicht (lamp): der eine Knopf, der nach vorn fuehrt, und die
     * Beschriftung ueber dem, worum es auf dem Bildschirm geht.
     */
    val Lampe = Color(0xFFFFC47A)

    /** Die Schrift auf dem Lampenlicht (lamp-ink): fast schwarz, warm. */
    val LampenTinte = Color(0xFF1B1205)

    /** Der Schein unter dem Hauptknopf (lamp-2, durchscheinend). */
    val LampenSchein = Color(0x52FFAE52)

    /**
     * Fuer das, was UEBER anderem Inhalt liegt: eine Rueckfrage, ein Blatt.
     * Deckend - die durchscheinende [Flaeche] liesse dort alles durch, was
     * darunter steht (am Geraet gesehen, 12.11.2026).
     */
    val Tafel = Color(0xFF0A1630)

    /** Karten und Felder: von oben nach unten. */
    val Flaeche: Brush get() = Brush.verticalGradient(listOf(FlaecheOben, FlaecheUnten))

    /** Der eine gefuellte Knopf: Lampenlicht, ohne Verlauf - wie `.btn`. */
    val Hauptknopf: Brush get() = SolidColor(Lampe)
}

private val DunklesSchema
 = darkColorScheme(
    primary = Farbe.Verweis,
    onPrimary = Farbe.Grund,
    // Blau 12 % auf dem Grund, als feste Farbe ausgerechnet.
    primaryContainer = Color(0xFF091835),
    onPrimaryContainer = Farbe.BlauSchrift,
    secondary = YskarFarben.BlauHell,
    onSecondary = Farbe.Grund,
    background = Farbe.Grund,
    onBackground = Farbe.Schrift,
    surface = Color(0xFF071229),
    onSurface = Farbe.Schrift,
    surfaceVariant = Color(0xFF0B1C40),
    onSurfaceVariant = Farbe.Leise,
    outline = Farbe.hauch(0.30f),
    outlineVariant = Farbe.Linie,
    error = Farbe.Warnung,
    onError = Farbe.Grund,
    // Rot 8 % auf dem Grund, als feste Farbe ausgerechnet.
    errorContainer = Color(0xFF181523),
    onErrorContainer = Farbe.WarnSchrift,
    tertiary = Farbe.Eingang,
    onTertiary = Farbe.Grund,
)

/**
 * Das helle Schema von vor dem Umbau. UNGENUTZT: Die App zeigt sich immer
 * dunkel, bis ein helles Design gezeichnet ist. Es bleibt hier liegen, weil
 * ueber sein Ende nicht entschieden wurde (DESIGN-NEU.md, Abschnitt 4).
 */
private val HellesSchema = lightColorScheme(
    primary = Color(0xFF1F5FDB),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD7E5FF),
    onPrimaryContainer = Color(0xFF032253),
    secondary = YskarFarben.BlauTief,
    onSecondary = Color.White,
    background = Color(0xFFF6F8FC),
    onBackground = Color(0xFF0A1220),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF0A1220),
    surfaceVariant = Color(0xFFEDF2F9),
    onSurfaceVariant = Color(0xFF5A6B85),
    outline = Color(0xFFD7E0EC),
    outlineVariant = Color(0xFFE4EAF2),
    error = Color(0xFFC0272C),
    onError = Color.White,
    errorContainer = Color(0xFFFFDBDC),
    onErrorContainer = Color(0xFF410006),
    tertiary = Color(0xFF13885A),
    onTertiary = Color.White,
)

// --- Schrift ----------------------------------------------------------------

/**
 * Ein Schnitt aus der variablen Datei.
 *
 * `weight` sagt Compose, WANN dieser Eintrag gewaehlt wird; erst
 * `variationSettings` stellt die Gewichtsachse der Datei wirklich ein. Ohne
 * die zweite Angabe kaemen alle drei Schnitte gleich mager heraus - die
 * Datei steht von sich aus auf 400.
 */
private fun schnitt(datei: Int, gewicht: Int) = Font(
    resId = datei,
    weight = FontWeight(gewicht),
    variationSettings = FontVariation.Settings(FontVariation.weight(gewicht)),
)

/**
 * Hanken Grotesk, die Schrift der Website. 300 fuer das Grosse (Guthaben,
 * Betraege, Aussagen), 400 fuer Text, 500 und 600 fuer Bedienung.
 */
val Hanken = FontFamily(
    schnitt(R.font.hanken_grotesk_variable, 300),
    schnitt(R.font.hanken_grotesk_variable, 400),
    schnitt(R.font.hanken_grotesk_variable, 500),
    schnitt(R.font.hanken_grotesk_variable, 600),
)

val GeistMono = FontFamily(
    schnitt(R.font.geist_mono_variable, 400),
    schnitt(R.font.geist_mono_variable, 500),
)

/** Feste Ziffernbreiten. Geist Mono braucht das nicht, schadet aber nicht. */
private const val ZIFFERN = "tnum"

private fun hanken(
    groesse: Float,
    gewicht: Int = 400,
    laufweite: Float = 0f,
    zeile: Float = 1.35f,
) = TextStyle(
    fontFamily = Hanken,
    fontSize = groesse.sp,
    fontWeight = FontWeight(gewicht),
    letterSpacing = laufweite.em,
    lineHeight = (groesse * zeile).sp,
    fontFeatureSettings = ZIFFERN,
)

private fun mono(groesse: Float, gewicht: Int = 400) = TextStyle(
    fontFamily = GeistMono,
    fontSize = groesse.sp,
    fontWeight = FontWeight(gewicht),
    letterSpacing = 0.02.em,
    lineHeight = (groesse * 1.4f).sp,
)

/**
 * Die Schriftstile der Vorlage, beim Namen genannt.
 *
 * Die neuen Bausteine greifen hierher, nicht in `MaterialTheme.typography`:
 * Dessen Faecher heissen nach Material ("labelMedium") und sagen nichts
 * darueber, wofuer die Vorlage die Groesse benutzt.
 */
object Schrift {
    /** Guthaben: 58 sp, leicht (300), eng. Die Zeile so hoch wie die Schrift. */
    val Guthaben = hanken(58f, 300, -0.045f, zeile = 1f)

    /**
     * Die Nachkommastellen des Guthabens: alle klein, weich gefaerbt. Die
     * Zahl bleibt vollstaendig (`betragGeteilt`).
     */
    val GuthabenBruch = hanken(21f, 400, -0.01f, zeile = 1f)

    /** Betrag im Kopf eines Belegs: 54 sp. */
    val BelegBetrag = hanken(54f, 300, -0.045f, zeile = 1f)

    /** Eingabe des Betrags: 38 sp. */
    val BetragEingabe = hanken(38f, 300, -0.035f, zeile = 1.2f)

    /** Der Satz auf dem Einstieg: 36 sp, zwei Zeilen. */
    val Riesig = hanken(36f, 300, -0.035f, zeile = 1.08f)

    /** "YSKAR" unter dem Zeichen: gesperrt. Kleiner fuer Sperre und Kopf. */
    val Schriftzug = hanken(18f, 600, 0.2f)

    /** Ueberschrift des Status ("Angenommen"). */
    val Gross = hanken(26f, 300, -0.03f, zeile = 1.12f)

    /** Betrag in schmalen Kartenkoepfen ("Verfuegbar"). */
    val Mittel = hanken(20f, 400, -0.025f, zeile = 1.2f)

    /** Bildschirmtitel, mittig; auch "Zuletzt" und der Hauptknopf. */
    val Titel = hanken(16f, 600, -0.01f)

    /** Betrag in einer Zeile des Verlaufs. */
    val Betrag = hanken(16f, 500, -0.01f)

    /** Ueberschrift einer Karte, Name einer Zeile. */
    val Kopf = hanken(15f, 600, -0.01f)
    val Zeile = hanken(15f, 500, -0.01f)

    /** Fliesstext. */
    val Text = hanken(15f)

    /** Beschriftung links in einer Wertzeile. */
    val Name = hanken(14f)

    /** Knoepfe unterhalb des Hauptknopfs: "Fertig" und die blauen Pillen. */
    val KnopfStill = hanken(14f, 600)
    val KnopfBlau = hanken(13.5f, 600)

    /** Beschriftung ueber einem Feld oder Wert. */
    val Schild = hanken(13f, 500)

    /**
     * Die Beschriftung ueber dem, worum es geht ("Guthaben", "Du sendest") -
     * auf der Website `.ph__k`: klein, 500, im Lampenlicht.
     */
    val Auftakt = hanken(14f, 500)

    /** Warnstreifen und der Hinweis unter dem QR-Code. */
    val Klein = hanken(13f)

    /** Hinweise, Zeitangaben, Beschriftung der runden Knoepfe. */
    val Hinweis = hanken(12.5f)
    val HinweisFest = hanken(12.5f, 500)

    /** "YSR" als kleines Schild: 12 sp, 600, gesperrt. */
    val Einheit = hanken(12f, 600, 0.1f)

    /** Beschriftung der drei Teile des Zeichens: 11 sp, 600, gesperrt. */
    val Marke = hanken(11f, 600, 0.06f)

    /** Ziffern des Ziffernfelds. */
    val Ziffer = hanken(28f, 300, -0.02f, zeile = 1f)

    /** Adresse im Beleg. */
    val Adresse = mono(15f)

    /** Adresse in Feldern und Karten. */
    val AdresseKlein = mono(13.5f)

    /** Kurzadresse in Marken. */
    val Kurz = mono(12.5f)

    /** Gegenseite in einer Zeile des Verlaufs, Fusszeile des Belegs. */
    val Winzig = mono(11f)
}

private val MaterialSchrift = Typography(
    displayMedium = Schrift.Guthaben,
    headlineSmall = Schrift.Gross,
    titleMedium = Schrift.Titel,
    titleSmall = Schrift.Kopf,
    bodyLarge = hanken(16f),
    bodyMedium = Schrift.Text,
    bodySmall = Schrift.Hinweis,
    labelLarge = Schrift.Titel,
    labelMedium = Schrift.HinweisFest,
    labelSmall = hanken(11f, 500),
)

/**
 * [dunkel] steht fest auf `true` und folgt NICHT mehr dem System. Der
 * Parameter bleibt, damit das helle Schema erreichbar ist, falls es
 * gezeichnet wird - und damit der Testrahmen nichts aendern muss.
 */
@Composable
fun YskarTheme(
    dunkel: Boolean = true,
    inhalt: @Composable () -> Unit,
) {
    val schema = if (dunkel) DunklesSchema else HellesSchema
    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            val fenster = (view.context as Activity).window
            WindowCompat.getInsetsController(fenster, view).apply {
                isAppearanceLightStatusBars = !dunkel
                isAppearanceLightNavigationBars = !dunkel
            }
        }
    }

    MaterialTheme(colorScheme = schema, typography = MaterialSchrift, content = inhalt)
}
