package com.yskar.wallet.sprache

import java.util.Locale

/**
 * Alle Texte der App, in einer Sprache.
 *
 * WARUM KEINE strings.xml: Die Texte sind Kotlin, je Bereich ein Interface
 * mit einer deutschen und einer englischen Fassung. Fehlt in einer Sprache
 * ein Text, uebersetzt der Compiler die App nicht - eine vergessene
 * Uebersetzung ist ein Baufehler, kein Fund beim Durchklicken. Die Texte
 * mit Zahlen darin sind Funktionen, keine Formatzeichenketten; und weil das
 * Paket kein Android braucht, laufen die Tests auf der JVM (`TexteTest`).
 *
 * WER SIE BEKOMMT:
 *  - Bildschirme lesen `LocalTexte.current` (ui/Sprachzugriff.kt).
 *  - Reine Funktionen (Zeiten, Kurvenform, ...) bekommen `t: Texte` als
 *    letzten Parameter - ohne Vorgabewert, damit kein Aufruf die Sprache
 *    vergessen kann.
 *  - Das Modell und der Waechterdienst fragen [texteFuer].
 *
 * WAS NICHT HIER STEHT: die Meldungen aus `netz/Senden.kt`. Diese Datei
 * prueft und unterschreibt und bleibt, wie sie ist; ihre Meldungen
 * uebersetzt [Sendemeldungen] an der Stelle, an der sie angezeigt werden.
 */
interface Texte {
    val sprache: Sprache

    /** Schreibweise von Zahlen und Daten. */
    val locale: Locale get() = sprache.locale

    val allgemein: AllgemeinTexte
    val zeit: ZeitTexte
    val einrichten: EinrichtenTexte
    val wallet: WalletTexte
    val senden: SendenTexte
    val einfuehrung: EinfuehrungTexte
    val selbsttest: SelbsttestTexte
    val einstellungen: EinstellungenTexte
    val modell: ModellTexte
    val waechter: WaechterTexte
    val signatur: SignaturTexte
    val kontakte: KontakteTexte
}

object Deutsch : Texte {
    override val sprache = Sprache.DEUTSCH
    override val allgemein = AllgemeinDe
    override val zeit = ZeitDe
    override val einrichten = EinrichtenDe
    override val wallet = WalletDe
    override val senden = SendenDe
    override val einfuehrung = EinfuehrungDe
    override val selbsttest = SelbsttestDe
    override val einstellungen = EinstellungenDe
    override val modell = ModellDe
    override val waechter = WaechterDe
    override val signatur = SignaturDe
    override val kontakte = KontakteDe
}

object Englisch : Texte {
    override val sprache = Sprache.ENGLISCH
    override val allgemein = AllgemeinEn
    override val zeit = ZeitEn
    override val einrichten = EinrichtenEn
    override val wallet = WalletEn
    override val senden = SendenEn
    override val einfuehrung = EinfuehrungEn
    override val selbsttest = SelbsttestEn
    override val einstellungen = EinstellungenEn
    override val modell = ModellEn
    override val waechter = WaechterEn
    override val signatur = SignaturEn
    override val kontakte = KontakteEn
}

fun texteFuer(sprache: Sprache): Texte = when (sprache) {
    Sprache.DEUTSCH -> Deutsch
    Sprache.ENGLISCH -> Englisch
}
