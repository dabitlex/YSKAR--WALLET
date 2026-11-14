package com.yskar.wallet.ui

import com.yskar.wallet.netz.VerlaufEintrag
import com.yskar.wallet.sprache.Texte
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Zeiten und Zaehler der Oberflaeche in Worten.
 *
 * REINE FUNKTIONEN, ohne Compose und ohne Android: "Heute" und "Gestern"
 * haengen an Mitternacht und an der Zeitzone, und genau dort irrt man sich.
 * Jede Funktion bekommt darum den heutigen Tag und die Zone hereingereicht,
 * statt selbst auf die Uhr zu sehen - so laesst sich in einem Unittest eine
 * Minute vor und eine Minute nach Mitternacht pruefen (`ZeitenTest`).
 *
 * Die Sprache kommt ebenso herein, als letzter Parameter `t`: Sie bestimmt
 * die Woerter und die Schreibweise von Datum und Zahl ("11.11.2026" und
 * "9.472" auf Deutsch, "11 Nov 2026" und "9,472" auf Englisch). Die
 * Uhrzeit steht in beiden Sprachen mit 24 Stunden.
 */

private val UHRZEIT = DateTimeFormatter.ofPattern("HH:mm")

private fun tag(t: Texte) = DateTimeFormatter.ofPattern(t.zeit.musterTag, t.locale)
private fun tagJahr(t: Texte) = DateTimeFormatter.ofPattern(t.zeit.musterTagJahr, t.locale)

/** In welcher Gruppe des Verlaufs eine Bewegung steht. */
enum class Tagesgruppe {
    HEUTE,
    GESTERN,
    FRUEHER;

    fun titel(t: Texte): String = when (this) {
        HEUTE -> t.zeit.heute
        GESTERN -> t.zeit.gestern
        FRUEHER -> t.zeit.frueher
    }
}

/**
 * Heute, gestern oder frueher - nach dem Kalendertag in [zone].
 *
 * Eine Bewegung ohne Zeit (der Spiegel kennt sie nicht) steht unter
 * "Frueher": Dort behauptet sie am wenigsten. Eine Zeit in der Zukunft -
 * die Uhr des Geraets geht nach - zaehlt als heute.
 */
fun tagesgruppe(unixSekunden: Long?, heute: LocalDate, zone: ZoneId): Tagesgruppe {
    if (unixSekunden == null) return Tagesgruppe.FRUEHER
    val tag = Instant.ofEpochSecond(unixSekunden).atZone(zone).toLocalDate()
    return when {
        !tag.isBefore(heute) -> Tagesgruppe.HEUTE
        tag == heute.minusDays(1) -> Tagesgruppe.GESTERN
        else -> Tagesgruppe.FRUEHER
    }
}

/**
 * Die Bewegungen nach Tagesgruppen, in der Reihenfolge Heute, Gestern,
 * Frueher. Innerhalb einer Gruppe bleibt die Reihenfolge, wie sie kam.
 * Gruppen ohne Eintrag fehlen.
 */
fun nachTagen(
    eintraege: List<VerlaufEintrag>,
    heute: LocalDate,
    zone: ZoneId,
): List<Pair<Tagesgruppe, List<VerlaufEintrag>>> {
    val gruppen = eintraege.groupBy { tagesgruppe(it.zeit, heute, zone) }
    return Tagesgruppe.entries.mapNotNull { g -> gruppen[g]?.let { g to it } }
}

/**
 * Die Zeit einer Bewegung INNERHALB ihrer Gruppe: Unter "Heute" und
 * "Gestern" genuegt die Uhrzeit, unter "Frueher" gehoert das Datum dazu.
 */
fun zeitInGruppe(unixSekunden: Long?, gruppe: Tagesgruppe, zone: ZoneId, t: Texte): String {
    if (unixSekunden == null) return t.zeit.zeitUnbekannt
    val z = Instant.ofEpochSecond(unixSekunden).atZone(zone)
    return when (gruppe) {
        Tagesgruppe.HEUTE, Tagesgruppe.GESTERN -> UHRZEIT.format(z)
        Tagesgruppe.FRUEHER -> "${tagJahr(t).format(z)}, ${UHRZEIT.format(z)}"
    }
}

/**
 * "Stand 18:17" - wann das angezeigte Guthaben zuletzt vom Spiegel kam.
 *
 * Ein alter Stand muss alt aussehen: Ist er nicht von heute, steht das
 * Datum dabei, und ist er nicht aus diesem Jahr, auch das Jahr. Wer nur
 * "Stand 18:17" liest, soll sich darauf verlassen koennen, dass heute
 * gemeint ist. Null, solange nichts bekannt ist.
 */
fun standText(unixSekunden: Long?, heute: LocalDate, zone: ZoneId, t: Texte): String? {
    if (unixSekunden == null) return null
    val z = Instant.ofEpochSecond(unixSekunden).atZone(zone)
    val tag = z.toLocalDate()
    return when {
        tag == heute -> t.zeit.stand(UHRZEIT.format(z))
        tag.year == heute.year -> t.zeit.stand("${tag(t).format(z)}, ${UHRZEIT.format(z)}")
        else -> t.zeit.stand("${tagJahr(t).format(z)}, ${UHRZEIT.format(z)}")
    }
}

/**
 * Die Marke oben rechts im Verlauf.
 *
 * Solange noch Seiten fehlen, steht da, wie viele GELADEN sind - "4
 * Eintraege" hiesse, es gaebe nur vier. Erst wenn der Verlauf bis zum
 * Anfang reicht, ist die Zahl die der Eintraege.
 */
fun zaehlerText(anzahl: Int, vollstaendig: Boolean, t: Texte): String =
    if (!vollstaendig) t.zeit.geladen(anzahl) else t.zeit.eintraege(anzahl)

/** Eine ganze Zahl mit Tausendertrenner: 9472 -> "9.472", auf Englisch "9,472". */
fun zahlText(zahl: Long, t: Texte): String =
    NumberFormat.getIntegerInstance(t.locale).format(zahl)

// ------------------------------------------------------------- Sendestatus

private val UHRZEIT_GENAU = DateTimeFormatter.ofPattern("HH:mm:ss")

/**
 * Wann dieses Geraet die ersten drei Schritte einer Ueberweisung erlebt hat.
 *
 * ZEITEN DIESES GERAETS, NICHT DER KETTE - nach seiner Uhr, in Unixsekunden:
 * der Augenblick vor dem Unterschreiben, die Antwort des Knotens, der erste
 * Blick in die Warteliste, bei dem die Ueberweisung darin stand. Sie leben
 * im Zustand der laufenden App und sind weg, wenn sie beendet wird. Dann
 * steht an dem Schritt wieder ein Wort statt einer Uhrzeit.
 */
data class Sendezeiten(
    val unterschrieben: Long? = null,
    val angenommen: Long? = null,
    val warteliste: Long? = null,
)

/** Uhrzeit mit Sekunden: "18:17:02". */
fun uhrzeitGenau(unixSekunden: Long, zone: ZoneId): String =
    UHRZEIT_GENAU.format(Instant.ofEpochSecond(unixSekunden).atZone(zone))

/**
 * "geprueft vor 8 s" - wie lange die letzte Antwort des Spiegels her ist.
 *
 * Steht am Schritt, der gerade wartet: Dort sagt eine Uhrzeit nichts, aber
 * das Alter der letzten Nachfrage sagt, ob die Wallet noch hinsieht.
 */
fun geprueftText(sekunden: Long, t: Texte): String = when {
    sekunden < 1 -> t.zeit.geprueftEben
    sekunden < 60 -> t.zeit.geprueftVorSekunden(sekunden)
    sekunden < 3_600 -> t.zeit.geprueftVorMinuten(sekunden / 60)
    else -> t.zeit.geprueftVorStunden(sekunden / 3_600)
}

/**
 * Was rechts neben einem der fuenf Schritte steht.
 *
 * [nr] zaehlt ab null: unterschrieben, angenommen, Warteliste, Block,
 * Bestaetigungen. Belegt wird nur, was bekannt ist:
 *
 *   erledigt   die Uhrzeit dieses Geraets (Schritte 0 bis 2), die Nummer
 *              des Blocks (3), die Zahl der Bestaetigungen (4). Fehlt die
 *              Angabe - die App wurde zwischendurch beendet, oder die
 *              Ueberweisung stand nie sichtbar in der Warteliste -, bleibt
 *              es beim Wort "erledigt", wie bisher.
 *   wartet     wie lange die letzte Nachfrage her ist; ohne die: "wartet".
 *   offen      "offen".
 *
 * Geraten wird nichts. Ein Schritt bekommt keine Uhrzeit, nur weil der
 * naechste eine hat.
 */
fun schrittAngabe(
    nr: Int,
    teil: com.yskar.wallet.netz.Sendestatus.Teil?,
    zeiten: Sendezeiten,
    block: Long?,
    bestaetigungen: Int,
    geprueft: Long?,
    jetzt: Long,
    zone: ZoneId,
    t: Texte,
): String = when (teil) {
    null -> "—"
    com.yskar.wallet.netz.Sendestatus.Teil.OFFEN -> t.zeit.schrittOffen
    com.yskar.wallet.netz.Sendestatus.Teil.WARTET ->
        if (geprueft != null) geprueftText(jetzt - geprueft, t) else t.zeit.schrittWartet
    com.yskar.wallet.netz.Sendestatus.Teil.ERLEDIGT -> when (nr) {
        0 -> zeiten.unterschrieben?.let { uhrzeitGenau(it, zone) }
        1 -> zeiten.angenommen?.let { uhrzeitGenau(it, zone) }
        2 -> zeiten.warteliste?.let { uhrzeitGenau(it, zone) }
        3 -> block?.let { t.zeit.block(zahlText(it, t)) }
        4 -> bestaetigungen.takeIf { it >= 2 }?.toString()
        else -> null
    } ?: t.zeit.schrittErledigt
}

/**
 * Wie gross der Betrag auf dem Sendebildschirm gesetzt wird, in sp.
 *
 * Bis sieben Zeichen steht er in voller Groesse (54 sp). Wird er laenger,
 * wird die Schrift im selben Mass kleiner, so dass er dieselbe Breite
 * behaelt - gekuerzt wird ein Betrag nie (REGELN.md, Regel 11). Unter 24 sp
 * geht es nicht: Das ist bei 15 Zeichen erreicht, und ein laengerer Betrag
 * wird dann breiter statt kleiner.
 */
fun betragSchriftgroesse(zeichen: Int): Float {
    val voll = 54f
    val bis = 7
    if (zeichen <= bis) return voll
    return (voll * bis / zeichen).coerceAtLeast(24f)
}

/**
 * Was im Betragsfeld steht, nachdem getippt oder eingefuegt wurde.
 *
 * IM FELD STEHT IMMER DAS DEZIMALZEICHEN DER SPRACHE: auf Deutsch das Komma,
 * auf Englisch der Punkt. Das jeweils andere Zeichen wird beim Tippen zu
 * diesem.
 *
 * Warum: `Betrag.ausEingabe` nimmt Komma UND Punkt als Dezimalzeichen und
 * kennt keine Tausendertrenner. Wer auf Deutsch "1.500" tippt und
 * eintausendfuenfhundert meint, bekaeme anderthalb - und saehe es erst im
 * Pruefschritt. So sieht er es sofort: Im Feld steht "1,500". Auf Englisch
 * dasselbe mit "1,500", das zu "1.500" wird.
 *
 * Die Tastatur entscheidet dabei nichts: Manche Zifferntastatur bietet nur
 * den Punkt, manche nur das Komma - je nach Sprache des TELEFONS, nicht der
 * App. Beide Tasten schreiben das Dezimalzeichen.
 *
 * An der Zahl aendert das nichts: `Betrag.ausEingabe` liest "1,5" und "1.5"
 * gleich. Ein eingefuegtes "1.284,57" wird zu "1,284,57" und ist damit
 * unlesbar - der Pruefschritt lehnt es ab, statt zu raten.
 */
fun betragImFeld(getippt: String, t: Texte): String {
    val dezimal = com.yskar.wallet.netz.Betrag.dezimalZeichen(t.locale)
    val anderes = if (dezimal == ',') '.' else ','
    return getippt.replace(anderes, dezimal)
}

/**
 * Ein Betrag in zwei Teilen: die ganze Zahl, und alles hinter dem Komma.
 *
 *     "1.284,57310946"  ->  "1.284"  und  ",57310946"
 *     "+1,00"           ->  "+1"     und  ",00"
 *     "0,001"           ->  "0"      und  ",001"
 *
 * Die Oberflaeche setzt den ersten Teil gross und den zweiten klein
 * (Wunsch des Besitzers, 12.11.2026: ALLE Nachkommastellen klein - davor
 * standen zwei Stunden lang die ersten beiden gross). Zusammen sind beide
 * Teile Zeichen fuer Zeichen der Betrag, der hereinkam: ES WIRD NICHTS
 * GERUNDET, NICHTS ANGEHAENGT UND NICHTS WEGGELASSEN (REGELN.md, Regel 11).
 *
 * [genau] kommt fertig geschrieben herein (`Betrag.genau`), in der Sprache
 * von [locale]: Das Dezimalzeichen ist auf Deutsch das Komma, auf Englisch
 * der Punkt; das jeweils andere trennt Tausender und teilt hier nichts.
 * Was kein Dezimalzeichen hat ("1.284", "—", "••••"), bleibt ungeteilt.
 */
fun betragGeteilt(genau: String, locale: java.util.Locale): Pair<String, String> {
    val komma = genau.lastIndexOf(com.yskar.wallet.netz.Betrag.dezimalZeichen(locale))
    if (komma <= 0) return genau to ""
    return genau.substring(0, komma) to genau.substring(komma)
}
