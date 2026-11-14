package com.yskar.wallet.ui

import com.yskar.wallet.sprache.Texte

/**
 * Die Worte der acht Karten der Einfuehrung.
 *
 * REIN, ohne Compose und ohne Android - damit ein Unittest sie lesen kann
 * (`EinfuehrungstexteTest`). Eine Einfuehrung verspricht etwas; was sie
 * verspricht, soll an EINER Stelle stehen und dort pruefbar sein.
 *
 * Die Worte selbst stehen in `sprache/EinfuehrungTexte.kt`, je Sprache
 * einmal. Hier steht, welche davon zu welcher Karte gehoeren - die
 * Reihenfolge und die Zahl der Karten haengen von keiner Sprache ab.
 *
 * Vorlage: design-neu/html/einfuehrung_1…8.html. Die Texte stehen dort und
 * nennen nur, was die App kann. EINE Stelle weicht ab, mit Freigabe
 * (UMBAU.md, Punkt B und Abschnitt 4): Karte 7 sagte "kein Server
 * beobachtet deine Adresse". Der Waechter fragt den Spiegel aber
 * regelmaessig nach genau dieser Adresse (REGELN.md, Regel 24) - und er
 * ist aus, bis der Besitzer ihn einschaltet.
 */
data class Kartentext(
    /** Erste Zeile der Ueberschrift, weiss. */
    val titel: String,
    /** Zweite Zeile, im hellen Blau. */
    val titelBlau: String,
    val text: String,
    /** Was auf dem Bild zu sehen ist - fuer den Bildschirmleser. */
    val bild: String,
    /** Der Hauptknopf: "Weiter", auf der letzten Karte "Wallet öffnen". */
    val knopf: String,
)

/** Die acht Karten in der Sprache [t] - dieselben Karten, dieselbe Reihenfolge. */
fun einfuehrungskarten(t: Texte): List<Kartentext> {
    val e = t.einfuehrung
    return listOf(
        Kartentext(
            titel = e.walletTitel,
            titelBlau = e.walletTitelBlau,
            text = e.walletText,
            bild = e.walletBild,
            knopf = e.weiter,
        ),
        Kartentext(
            titel = e.empfangenTitel,
            titelBlau = e.empfangenTitelBlau,
            text = e.empfangenText,
            bild = e.empfangenBild,
            knopf = e.weiter,
        ),
        Kartentext(
            titel = e.sendenTitel,
            titelBlau = e.sendenTitelBlau,
            text = e.sendenText,
            bild = e.sendenBild,
            knopf = e.weiter,
        ),
        Kartentext(
            titel = e.fortschrittTitel,
            titelBlau = e.fortschrittTitelBlau,
            text = e.fortschrittText,
            bild = e.fortschrittBild,
            knopf = e.weiter,
        ),
        Kartentext(
            titel = e.ueberblickTitel,
            titelBlau = e.ueberblickTitelBlau,
            text = e.ueberblickText,
            bild = e.ueberblickBild,
            knopf = e.weiter,
        ),
        Kartentext(
            titel = e.woerterTitel,
            titelBlau = e.woerterTitelBlau,
            text = e.woerterText,
            bild = e.woerterBild,
            knopf = e.weiter,
        ),
        Kartentext(
            titel = e.sicherTitel,
            titelBlau = e.sicherTitelBlau,
            text = e.sicherText,
            bild = e.sicherBild,
            knopf = e.weiter,
        ),
        Kartentext(
            titel = e.kontrolleTitel,
            titelBlau = e.kontrolleTitelBlau,
            text = e.kontrolleText,
            bild = e.kontrolleBild,
            knopf = e.walletOeffnen,
        ),
    )
}

/** "3 von 8" - die Zeile unter dem Fortschritt. [karte] zaehlt ab null. */
fun kartenzaehler(karte: Int, von: Int, t: Texte): String = t.einfuehrung.zaehler(karte + 1, von)

/** "Karte 3 von 8" - der Fortschritt in Worten, fuer den Bildschirmleser. */
fun kartenansage(karte: Int, von: Int, t: Texte): String = t.einfuehrung.ansage(karte + 1, von)
