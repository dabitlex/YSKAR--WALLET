package com.yskar.wallet.ui

import com.yskar.wallet.netz.Betrag
import com.yskar.wallet.netz.Guthabenverlauf
import com.yskar.wallet.netz.Guthabenverlauf.Zeitraum
import com.yskar.wallet.sprache.Texte
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Vom Ergebnis der Rechnung zur Linie und zu ihren Worten.
 *
 * REINE FUNKTIONEN, ohne Compose und ohne Android - wie `Zeiten.kt`. Was
 * hier herauskommt, zeichnet `Kurve.kt` nur noch ab. So laesst sich im
 * Unittest pruefen, wo die Linie beginnt, dass sie nie rueckwaerts laeuft
 * und was daruebersteht, ohne ein Geraet (`KurvenformTest`).
 */

/**
 * Ein Eckpunkt der Linie. Beides zwischen 0 und 1: [x] von links nach
 * rechts ueber den Zeitraum, [y] von UNTEN nach oben zwischen dem kleinsten
 * und dem groessten Stand.
 */
data class Kurvenpunkt(val x: Float, val y: Float)

/**
 * Die Eckpunkte der Stufenlinie, von links nach rechts. Der letzte ist der
 * heutige Stand - dort sitzt der Punkt.
 *
 * Leer, wenn nichts gezeichnet werden darf ([Guthabenverlauf.Ergebnis.zeichenbar]).
 *
 * DIE HOEHE ZEIGT DIE FORM, NICHT DIE GROESSE. Unten ist der kleinste Stand
 * des belegten Teils, oben der groesste - wie in der Vorlage. Ein
 * Zehntelprozent Unterschied fuellt die Hoehe genauso wie eine Verdopplung.
 * Hat sich nichts bewegt, liegt die Linie in der Mitte.
 *
 * Ist nur ein Teil belegt, beginnt die Linie nicht am linken Rand, sondern
 * dort, wo sie belegt ist. Links davon bleibt es leer.
 */
fun kurvenpunkte(e: Guthabenverlauf.Ergebnis): List<Kurvenpunkt> {
    if (!e.zeichenbar) return emptyList()

    val spanne = e.jetzt - e.beginn
    fun x(zeit: Long): Float =
        if (spanne <= 0) 1f
        else ((zeit - e.beginn).toDouble() / spanne).coerceIn(0.0, 1.0).toFloat()

    val staende = e.stufen.map { it.stand } + e.anfangsstand + e.endstand
    val unten = staende.min()
    val oben = staende.max()
    fun y(stand: Long): Float =
        if (oben == unten) 0.5f
        else ((stand - unten).toDouble() / (oben - unten)).coerceIn(0.0, 1.0).toFloat()

    val punkte = ArrayList<Kurvenpunkt>()
    fun dazu(p: Kurvenpunkt) {
        if (punkte.lastOrNull() != p) punkte.add(p)
    }

    var stand = e.anfangsstand
    dazu(Kurvenpunkt(x(e.belegtAb), y(stand)))
    for (s in e.stufen) {
        val xs = x(s.zeit)
        dazu(Kurvenpunkt(xs, y(stand)))   // waagrecht bis zur Bewegung
        dazu(Kurvenpunkt(xs, y(s.stand))) // senkrecht auf den neuen Stand
        stand = s.stand
    }
    dazu(Kurvenpunkt(1f, y(e.endstand)))
    return punkte
}

/** "7 Tage", "30 Tage", "1 Jahr" - ausgeschrieben, fuer Titel und Bildschirmleser. */
fun zeitraumWort(z: Zeitraum, t: Texte): String = when (z) {
    Zeitraum.TAGE_7 -> t.wallet.zeitraum7Tage
    Zeitraum.TAGE_30 -> t.wallet.zeitraum30Tage
    Zeitraum.JAHR -> t.wallet.zeitraum1Jahr
}

/** "7 T", "30 T", "1 J" - die Beschriftung der Wahl, wie in der Vorlage. */
fun zeitraumKurz(z: Zeitraum, t: Texte): String = when (z) {
    Zeitraum.TAGE_7 -> t.wallet.zeitraumKurz7Tage
    Zeitraum.TAGE_30 -> t.wallet.zeitraumKurz30Tage
    Zeitraum.JAHR -> t.wallet.zeitraumKurz1Jahr
}

private val KURVE_UHR = DateTimeFormatter.ofPattern("HH:mm")

// Tag und Monat schreibt jede Sprache anders ("04.11." und "4 Nov"); die
// Uhrzeit steht in beiden mit 24 Stunden - wie in `Zeiten.kt`.
private fun kurveTag(t: Texte) = DateTimeFormatter.ofPattern(t.zeit.musterTag, t.locale)
private fun kurveTagJahr(t: Texte) = DateTimeFormatter.ofPattern(t.zeit.musterTagJahr, t.locale)

/**
 * Ab wann die Linie belegt ist, in Worten: heute die Uhrzeit, sonst das
 * Datum, in einem anderen Jahr mit Jahr. Dieselbe Regel wie beim "Stand"
 * auf der Guthabenkarte - ein Datum von gestern darf nicht aussehen wie
 * eine Uhrzeit von heute.
 */
fun belegtAbText(unixSekunden: Long, heute: LocalDate, zone: ZoneId, t: Texte): String {
    val z = Instant.ofEpochSecond(unixSekunden).atZone(zone)
    return when {
        z.toLocalDate() == heute -> KURVE_UHR.format(z)
        z.year == heute.year -> kurveTag(t).format(z)
        else -> kurveTagJahr(t).format(z)
    }
}

/**
 * Was ueber dem Diagramm steht.
 *
 * "Guthaben · 30 Tage", solange nichts fehlt oder noch nichts da ist.
 * Reicht die Linie nicht ueber den ganzen Zeitraum, steht stattdessen da,
 * ab wann sie belegt ist - der Titel verspricht nie mehr, als die Linie
 * zeigt.
 */
fun kurventitel(
    e: Guthabenverlauf.Ergebnis?,
    zeitraum: Zeitraum,
    heute: LocalDate,
    zone: ZoneId,
    t: Texte,
): String = when {
    e == null || !e.zeichenbar || e.vollstaendig ->
        t.wallet.kurventitelZeitraum(zeitraumWort(zeitraum, t))
    e.belegtAb >= e.jetzt -> t.wallet.kurventitelNichtBelegt
    else -> t.wallet.kurventitelBelegtAb(belegtAbText(e.belegtAb, heute, zone, t))
}

/**
 * Die Linie in einem Satz, fuer den Bildschirmleser: von welchem Stand auf
 * welchen. Das ist die eine Stelle, an der die Groesse steht, die die
 * Linie selbst nicht zeigt.
 */
fun kurvenbeschreibung(
    e: Guthabenverlauf.Ergebnis,
    zeitraum: Zeitraum,
    heute: LocalDate,
    zone: ZoneId,
    t: Texte,
): String {
    val von = Betrag.genau(e.anfangsstand, t.locale)
    val auf = Betrag.genau(e.endstand, t.locale)
    return when {
        !e.zeichenbar -> t.wallet.kurveVorgelesenUnstimmig
        e.vollstaendig ->
            t.wallet.kurveVorgelesenZeitraum(zeitraumWort(zeitraum, t), von, auf)
        e.belegtAb >= e.jetzt -> t.wallet.kurveVorgelesenNichtBelegt
        else ->
            t.wallet.kurveVorgelesenSeit(belegtAbText(e.belegtAb, heute, zone, t), von, auf)
    }
}
