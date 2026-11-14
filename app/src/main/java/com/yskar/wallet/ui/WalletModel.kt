package com.yskar.wallet.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.yskar.wallet.Kontaktspeicher
import com.yskar.wallet.Vorlieben
import com.yskar.wallet.Waechterdienst
import com.yskar.wallet.kern.Bip39
import com.yskar.wallet.kontakte.Kontakt
import com.yskar.wallet.kontakte.Kontakte
import com.yskar.wallet.netz.Guthabenverlauf
import com.yskar.wallet.netz.Konto
import com.yskar.wallet.netz.Markt
import com.yskar.wallet.netz.NetzFehler
import com.yskar.wallet.netz.Senden
import com.yskar.wallet.netz.Sendestatus
import com.yskar.wallet.netz.Spiegel
import com.yskar.wallet.netz.VerlaufEintrag
import com.yskar.wallet.netz.Verlaufsbestand
import com.yskar.wallet.netz.Verlaufslader
import com.yskar.wallet.netz.Waechter
import com.yskar.wallet.netz.HttpTransport
import com.yskar.wallet.netz.spiegelMainnet
import com.yskar.wallet.signatur.Nachricht
import com.yskar.wallet.signatur.Rueckruf
import com.yskar.wallet.signatur.Unterschrift
import com.yskar.wallet.sprache.Sendemeldungen
import com.yskar.wallet.sprache.Sprache
import com.yskar.wallet.sprache.Texte
import com.yskar.wallet.sprache.texteFuer
import com.yskar.wallet.tresor.BioSchluessel
import com.yskar.wallet.tresor.Tresor
import com.yskar.wallet.tresor.loeschen
import com.yskar.wallet.tresor.tresorFuer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.crypto.Cipher

/**
 * Der Zustand der Wallet.
 *
 * EIN GRUNDSATZ ZIEHT SICH DURCH: Die zwoelf Woerter leben nur so lange,
 * wie sie gebraucht werden. Beim Entsperren prueft die App die PIN,
 * ueberschreibt die Woerter sofort und behaelt nur die Adresse - die ist
 * oeffentlich. "Entsperrt" heisst hier: Guthaben und Verlauf sind sichtbar.
 * Es heisst NICHT, dass ein Schluessel im Speicher wartet.
 *
 * Zum Unterschreiben einer Ueberweisung wird spaeter erneut entsperrt. Das
 * ist eine Unbequemlichkeit mit Gegenwert: Zwischen zwei Zahlungen gibt es
 * im Arbeitsspeicher nichts zu holen.
 */
class WalletModel(app: Application) : AndroidViewModel(app) {

    private val tresor: Tresor = tresorFuer(app)
    private val bioSchluessel = BioSchluessel()
    private val spiegel: Spiegel = spiegelMainnet()
    private val vorlieben = Vorlieben(app)

    /** Namen fuer Adressen - nur auf diesem Geraet (`Kontaktspeicher`). */
    private val kontaktspeicher = Kontaktspeicher(app)

    /**
     * Fuer die Antwort auf eine Anmeldung per QR-Code: derselbe Transport
     * wie zum Spiegel (nur https, folgt keiner Weiterleitung), mit der
     * kuerzeren Frist des Rueckrufs.
     */
    private val rueckrufTransport: Spiegel.Transport =
        HttpTransport(Rueckruf.ZEIT_MS, Rueckruf.ZEIT_MS)

    // Die Sprache steht vom ersten Bild an fest - sonst blitzte bei jedem
    // Start ein deutscher Bildschirm auf, bevor der englische kommt.
    private val _stand = MutableStateFlow(
        Stand(sprache = vorlieben.geltendeSprache, kontakte = kontaktspeicher.liste),
    )
    val stand: StateFlow<Stand> = _stand.asStateFlow()

    init {
        pruefeTresor()
    }

    // ------------------------------------------------------------ Zustand

    data class Stand(
        val ansicht: Ansicht = Ansicht.Laedt,
        /** Oeffentlich; bekannt, sobald ein Tresor da ist. */
        val adresse: String? = null,
        val konto: Konto? = null,
        val weitereEintraege: List<VerlaufEintrag> = emptyList(),
        /** Zeiger auf die naechste Verlaufsseite; null = Ende erreicht. */
        val letzterZeiger: String? = null,
        /** Das Netz wird gefragt. Betrifft nur die Anzeige der Kontodaten. */
        val laedtKonto: Boolean = false,
        /**
         * Der Tresor rechnet gerade (Einrichten, Entsperren, PIN aendern).
         *
         * Bewusst GETRENNT von [laedtKonto]: Beides in ein Feld zu legen
         * hat ein totes Ziffernfeld erzeugt - wer sperrte, waehrend noch
         * Kontodaten geladen wurden, konnte die PIN nicht mehr eingeben,
         * weil die Tasten an derselben Markierung haengen.
         */
        val arbeitet: Boolean = false,
        val netzMeldung: String? = null,
        val bioMoeglich: Boolean = false,
        val bioEingeschaltet: Boolean = false,
        /** Sekunden, die die Bremse noch verlangt. */
        val wartezeit: Long = 0,
        val fehler: String? = null,
        val hinweis: String? = null,
        /** Gebührenlage des nächsten Blocks; nötig, um senden zu dürfen. */
        val marktStand: Markt? = null,
        /** Höhe des letzten Blocks. */
        val spitze: Long? = null,
        /** Fehler im Sendeablauf, getrennt von [fehler] des Entsperrens. */
        val sendeFehler: String? = null,
        /** Kennung der zuletzt eingereichten Überweisung. */
        val gesendeteTxid: String? = null,
        /** Schon einmal in der Warteliste des Knotens gesehen. */
        val mempoolGesehen: Boolean = false,
        val benachrichtigungen: Boolean = false,
        val vibration: Boolean = true,
        /** Guthaben und Betraege auf dem Hauptbildschirm verbergen. */
        val verborgen: Boolean = false,
        /**
         * Die Kontakte: Namen, die der Besitzer Adressen gegeben hat. Eine
         * Lesehilfe, kein Beweis - geprueft wird beim Senden die Adresse
         * (`kontakte/Kontakte.kt`).
         */
        val kontakte: List<Kontakt> = emptyList(),
        /** Der Zeitraum, den das Diagramm auf dem Hauptbildschirm zeigt. */
        val zeitraum: Guthabenverlauf.Zeitraum = Guthabenverlauf.Zeitraum.TAGE_30,
        /**
         * Der zuletzt bekannte Stand: Guthaben in kleinsten Einheiten und der
         * Zeitpunkt in Unixsekunden nach der Uhr dieses Geraets.
         *
         * NUR ZUM ANZEIGEN, und bewusst kein [Konto]: Stuende hier ein Konto,
         * koennte der Sendeweg es fuer frisch halten. `Senden.pruefe` bekommt
         * weiter nur [konto] - das frisch geladene oder keins.
         */
        val bekanntesGuthaben: Long? = null,
        val bekanntSeit: Long? = null,
        /** Wann dieses Geraet die ersten Schritte der letzten Überweisung erlebt hat. */
        val sendeZeiten: Sendezeiten = Sendezeiten(),
        /** Die Sprache der App - siehe `sprache/Sprache.kt`. */
        val sprache: Sprache = Sprache.DEUTSCH,
        /**
         * Wohin es nach dem Entsperren geht, wenn die App sich im
         * Hintergrund gesperrt hat - null heisst: zum Hauptbildschirm.
         * Aufgehoben wird nur der Status einer abgeschickten Ueberweisung
         * ([Hintergrundregel.rueckkehr]).
         */
        val nachEntsperren: Ansicht? = null,
        /**
         * Ein Anmelde-Code aus einem `yskar:login`-Link, der ankam, waehrend
         * die Wallet gesperrt war. Nach dem Entsperren wird er GEZEIGT -
         * unterschrieben wird erst nach einer weiteren PIN-Eingabe.
         */
        val wartenderCode: String? = null,
        /**
         * Die Zahlung aus einer Mitteilung des Waechters, als
         * [Waechter.schluessel] - gezeigt nach dem ersten Laden des Kontos,
         * sobald entsperrt ist ([Mitteilungsziel]).
         */
        val wartendeZahlung: String? = null,
    ) {
        /**
         * Erste Seite plus das Nachgeladene - ohne Doppelte.
         *
         * Das `distinctBy` ist kein Schoenheitsputz: Seit die Wallet sich
         * im Hintergrund aktualisiert, wird die erste Seite neu geholt,
         * waehrend aeltere Seiten liegen bleiben. Kommt dabei eine Bewegung
         * hinzu, verschiebt sich die Seitengrenze um eins - und ein
         * Eintrag stuende zweimal da.
         */
        val verlauf: List<VerlaufEintrag>
            get() = ((konto?.verlauf ?: emptyList()) + weitereEintraege)
                .distinctBy { Waechter.schluessel(it) }

        /** Was vom Verlauf geladen ist; null, solange noch kein Konto da ist. */
        val bestand: Verlaufsbestand?
            get() = if (konto == null) null else Verlaufsbestand(verlauf, letzterZeiger)

        /**
         * Der Guthabenverlauf fuer [zeitraum] - rueckwaerts gerechnet aus dem
         * frisch geladenen Konto und dem, was vom Verlauf da ist.
         *
         * NUR ZUM ANZEIGEN, wie [bekanntesGuthaben]. Nichts davon erreicht
         * `Senden.pruefe`.
         *
         * Das rechte Ende der Linie ist [bekanntSeit]: der Augenblick, in
         * dem der Spiegel zuletzt geantwortet hat. Die Linie reicht also
         * genau so weit wie der "Stand" auf der Guthabenkarte.
         *
         * `lazy`, weil das Sortieren von ein paar tausend Bewegungen nicht
         * bei jedem Lesen neu laufen soll. Jeder neue Zustand ist ein neues
         * Objekt und rechnet einmal - und nur, wenn jemand hinsieht.
         */
        val kurve: Guthabenverlauf.Ergebnis? by lazy {
            val k = konto ?: return@lazy null
            val jetzt = bekanntSeit ?: return@lazy null
            Guthabenverlauf.rechne(
                guthaben = k.guthaben,
                verlauf = verlauf,
                bisZumAnfangGeladen = letzterZeiger == null,
                beginn = zeitraum.beginn(jetzt),
                jetzt = jetzt,
            )
        }
    }

    sealed interface Ansicht {
        data object Laedt : Ansicht
        data object Einstieg : Ansicht

        /** Die zwoelf Woerter stehen auf dem Schirm. */
        data class WoerterZeigen(val woerter: List<String>) : Ansicht

        /** Drei davon muessen abgetippt werden. */
        data class WoerterPruefen(
            val woerter: List<String>,
            val positionen: List<Int>,
        ) : Ansicht

        data object Wiederherstellen : Ansicht

        /** PIN festlegen. [woerter] haengt daran, bis der Tresor steht. */
        data class PinSetzen(val woerter: String, val neueWallet: Boolean) : Ansicht

        data object Sperre : Ansicht
        data object Wallet : Ansicht
        data object Empfangen : Ansicht
        data object Verlauf : Ansicht

        /**
         * Eine einzelne Bewegung, vollstaendig.
         *
         * [ausVerlauf] merkt sich, woher der Nutzer kam. Wer die dritte
         * Zeile von unten im Verlauf angetippt hat, will beim Zurueck
         * nicht wieder oben auf dem Hauptbildschirm stehen.
         */
        data class Bewegung(
            val eintrag: VerlaufEintrag,
            val ausVerlauf: Boolean,
        ) : Ansicht

        data object Einstellungen : Ansicht

        /** Die Liste der Kontakte. */
        data object Kontaktliste : Ansicht

        /**
         * Ein Kontakt wird angelegt ([vorlage] null) oder geaendert.
         * [adresseVorgabe] fuellt das Adressfeld vor ("Als Kontakt speichern"
         * aus einer Zahlung). [zurueck] ist, wohin es danach geht.
         */
        data class KontaktBearbeiten(
            val vorlage: Kontakt?,
            val adresseVorgabe: String?,
            val zurueck: Ansicht,
        ) : Ansicht
        data object Selbsttest : Ansicht

        /** Eine Nachricht eintippen oder einfuegen und signieren. */
        data object Signieren : Ansicht

        /**
         * Anmelden auf einer YSKAR-Seite. [code]: der Inhalt des QR-Codes
         * oder Links; null heisst, er wird erst noch gescannt.
         */
        data class Anmelden(val code: String?) : Ansicht

        /**
         * Die acht Karten der Einfuehrung. [ausMehr] sagt, wohin es danach
         * geht: zurueck zu den Einstellungen, oder - nach dem Einrichten - zur Wallet.
         */
        data class Einfuehrung(val ausMehr: Boolean) : Ansicht

        /**
         * Die zwoelf Woerter noch einmal ansehen - nach PIN oder Finger.
         *
         * Eigene Ansicht und nicht [WoerterZeigen]: Von dort geht es beim
         * Einrichten weiter zur Abfrage, hier zurueck in die Einstellungen.
         */
        data class WoerterAnsehen(val woerter: List<String>) : Ansicht

        // --- Senden, in vier Schritten -----------------------------------
        data object SendenEingabe : Ansicht
        data class SendenPruefen(val entwurf: Senden.Entwurf) : Ansicht
        data class SendenPin(val entwurf: Senden.Entwurf) : Ansicht
        data class SendenFertig(
            val ergebnis: Senden.Ergebnis,
            val entwurf: Senden.Entwurf,
        ) : Ansicht
    }

    /**
     * Ob die App gerade im Hintergrund ist. Gesetzt von `MainActivity`
     * ueber [inDenHintergrund] und [zurueckImVordergrund].
     */
    @Volatile
    private var draussen = false

    /**
     * JEDE Aenderung des Zustands geht hier durch - und solange die App im
     * Hintergrund ist, wird aus jeder offenen Ansicht die Sperre.
     *
     * Das steht an dieser einen Stelle und nicht bei [inDenHintergrund],
     * weil Arbeit weiterlaeuft, waehrend die App draussen ist: Eine
     * Ueberweisung, die gerade unterwegs zum Knoten war, kommt zurueck und
     * will ihren Status zeigen. Ohne diese Stelle stuende die Wallet danach
     * offen im Hintergrund.
     *
     * UND: AUS DER SPERRE FUEHRT NUR DAS ENTSPERREN. Dieselbe Arbeit kann
     * auch erst fertig werden, wenn die App schon wieder vorn ist - PIN
     * eingegeben, kurz weg, zurueck, bevor der Knoten antwortet. Dann ist
     * `draussen` laengst wieder falsch, und der Status legte sich ohne PIN
     * ueber die Sperre. Darum darf eine Aenderung die Sperre nur verlassen,
     * wenn sie mit [entsperrt] kommt - und das tut allein
     * [verarbeiteOeffnen] nach einer richtigen PIN oder dem Finger.
     * (Gefunden bei der Durchsicht am 11.11.2026.)
     *
     * Was die Regel ist, steht in [Hintergrundregel] und hat Unittests.
     */
    private fun setze(entsperrt: Boolean = false, f: (Stand) -> Stand) = _stand.update { alt ->
        val neu = f(alt)
        val sperren = Hintergrundregel.mussSperren(
            alt = alt.ansicht,
            neu = neu.ansicht,
            hatWallet = neu.adresse != null,
            draussen = draussen,
            entsperrt = entsperrt,
        )
        if (sperren) {
            val bleibt = Hintergrundregel.aufgehoben(
                alt = alt.ansicht,
                bisher = alt.nachEntsperren,
                neu = neu.ansicht,
            )
            gesperrt(neu).copy(
                nachEntsperren = bleibt,
                // Kommt die erste Einfuehrung wieder, dann auch der Satz, der
                // zu ihr gehoert ("Wallet angelegt. Bewahre die zwoelf Woerter
                // sicher auf."). Auf der Sperre selbst wird er nicht gezeigt.
                hinweis = if (bleibt is Ansicht.Einfuehrung) neu.hinweis else null,
            )
        } else {
            neu
        }
    }

    /** Die Texte in der Sprache der App. */
    private val t: Texte get() = texteFuer(_stand.value.sprache)

    fun meldungWeg() = setze { it.copy(fehler = null, hinweis = null, netzMeldung = null) }

    // ----------------------------------------------------------- Einstieg

    private fun pruefeTresor() {
        val adresse = tresor.adresse()
        val aktenFehler = tresor.aktenFehler()
        setze {
            it.copy(
                ansicht = if (adresse != null) Ansicht.Sperre else Ansicht.Einstieg,
                adresse = adresse,
                bioEingeschaltet = tresor.bioEingeschaltet(),
                benachrichtigungen = vorlieben.benachrichtigungen,
                vibration = vorlieben.vibration,
                verborgen = vorlieben.guthabenVerborgen,
                zeitraum = Guthabenverlauf.Zeitraum.ausTagen(vorlieben.zeitraumTage),
                // Der zuletzt bekannte Stand gehoert zu DIESER Adresse.
                // Ohne Tresor gibt es keine, und dann auch keinen Stand.
                bekanntesGuthaben = vorlieben.standGuthaben
                    .takeIf { adresse != null && it >= 0 },
                bekanntSeit = vorlieben.standZeit
                    .takeIf { adresse != null && it > 0 },
                wartezeit = 0,
                // Eine unlesbare Tresordatei darf nicht so aussehen, als
                // haette es nie eine Wallet gegeben.
                fehler = aktenFehler?.let { f -> t.modell.tresordateiUnlesbar(f) },
            )
        }
    }

    fun bioMoeglichkeit(moeglich: Boolean) = setze { it.copy(bioMoeglich = moeglich) }

    fun neueWallet() {
        val woerter = Bip39.create(12)
        setze { it.copy(ansicht = Ansicht.WoerterZeigen(woerter.split(" ")), fehler = null) }
    }

    fun woerterGeschrieben() {
        val a = _stand.value.ansicht
        if (a !is Ansicht.WoerterZeigen) return
        // Drei zufaellige Stellen, aufsteigend - sonst wirkt die Abfrage
        // wie ein Gedaechtnistest statt wie eine Kontrolle der Notiz.
        val positionen = (0..11).shuffled().take(3).sorted()
        setze { it.copy(ansicht = Ansicht.WoerterPruefen(a.woerter, positionen)) }
    }

    fun woerterNochmalZeigen() {
        val a = _stand.value.ansicht
        if (a is Ansicht.WoerterPruefen) {
            setze { it.copy(ansicht = Ansicht.WoerterZeigen(a.woerter), fehler = null) }
        }
    }

    /** @return true, wenn alle drei stimmen. */
    fun woerterGeprueft(eingaben: List<String>): Boolean {
        val a = _stand.value.ansicht
        if (a !is Ansicht.WoerterPruefen) return false
        val stimmt = a.positionen.withIndex().all { (i, pos) ->
            eingaben.getOrNull(i)?.trim()?.lowercase() == a.woerter[pos]
        }
        if (stimmt) {
            setze {
                it.copy(
                    ansicht = Ansicht.PinSetzen(a.woerter.joinToString(" "), neueWallet = true),
                    fehler = null,
                )
            }
        } else {
            setze { it.copy(fehler = t.modell.stimmtNochNicht) }
        }
        return stimmt
    }

    fun wiederherstellenStarten() =
        setze { it.copy(ansicht = Ansicht.Wiederherstellen, fehler = null) }

    /** @return Fehlertext oder null. */
    fun woerterUebernehmen(eingabe: String): String? {
        val sauber = Bip39.normalize(eingabe)
        val anzahl = if (sauber.isEmpty()) 0 else sauber.split(" ").size
        // Genau zwoelf (Entscheidung des Besitzers, 11.11.2026). Diese App
        // und die Vorlage legen nur Wallets mit zwoelf Woertern an; laengere
        // Wortfolgen nimmt das Wiederherstellen nicht mehr an. `Bip39`
        // selbst kann sie weiterhin pruefen - es wird hier nur nicht mehr
        // danach gefragt.
        if (anzahl != 12) {
            return t.modell.woerterAnzahl(anzahl)
        }
        if (!Bip39.isValid(sauber)) {
            return t.modell.pruefsummeFalsch
        }
        setze { it.copy(ansicht = Ansicht.PinSetzen(sauber, neueWallet = false), fehler = null) }
        return null
    }

    fun zurueckZumEinstieg() = setze {
        it.copy(ansicht = Ansicht.Einstieg, fehler = null, hinweis = null)
    }

    // ---------------------------------------------------------- Einrichten

    fun tresorAnlegen(pin: CharArray) {
        val a = _stand.value.ansicht
        if (a !is Ansicht.PinSetzen) return

        viewModelScope.launch {
            setze { it.copy(arbeitet = true, fehler = null) }
            val e = withContext(Dispatchers.Default) { tresor.einrichten(a.woerter, pin) }
            pin.loeschen()

            when (e) {
                is Tresor.Ergebnis.Offen -> {
                    e.woerter.fill(0)
                    // Eine andere Wallet, ein anderer Verlauf: Was der
                    // Waechter von der vorigen Adresse weiss, gilt hier nicht.
                    vorlieben.gedaechtnisLeeren()
                    setze {
                        it.copy(
                            // Einmal, gleich nach dem Einrichten: die
                            // Einfuehrung. Ausgeloest vom Einrichten selbst;
                            // gemerkt wird dafuer nichts.
                            ansicht = Ansicht.Einfuehrung(ausMehr = false),
                            adresse = e.adresse,
                            arbeitet = false,
                            // Der Stand der vorigen Adresse ist nicht der
                            // dieser. Lieber ein Strich als eine fremde Zahl.
                            bekanntesGuthaben = null,
                            bekanntSeit = null,
                            hinweis = if (a.neueWallet) t.modell.walletAngelegt
                            else t.modell.walletWiederhergestellt,
                        )
                    }
                    kontoLaden()
                }

                else -> setze {
                    it.copy(arbeitet = false, fehler = beschreibe(e))
                }
            }
        }
    }

    // ----------------------------------------------------------- Entsperren

    fun entsperren(pin: CharArray) {
        viewModelScope.launch {
            setze { it.copy(arbeitet = true, fehler = null) }
            val e = withContext(Dispatchers.Default) { tresor.oeffnen(pin) }
            pin.loeschen()
            verarbeiteOeffnen(e)
        }
    }

    /** Cipher fuer den Fingerabdruck; null, wenn das gerade nicht geht. */
    fun bioCipherZumOeffnen(): Cipher? {
        val iv = tresor.bioIv() ?: return null
        return when (val s = bioSchluessel.zumEntschluesseln(iv)) {
            is BioSchluessel.Stand.Bereit -> s.cipher
            BioSchluessel.Stand.NeuEinrichten -> {
                tresor.bioAusschalten()
                setze {
                    it.copy(
                        bioEingeschaltet = false,
                        hinweis = t.modell.fingerabdrueckeGeaendert,
                    )
                }
                null
            }

            is BioSchluessel.Stand.Fehler -> {
                setze { it.copy(fehler = t.modell.tresorFehler(s.grund)) }
                null
            }
        }
    }

    fun bioEntsperren(cipher: Cipher) {
        viewModelScope.launch {
            setze { it.copy(arbeitet = true, fehler = null) }
            val e = withContext(Dispatchers.Default) { tresor.bioOeffnen(cipher) }
            verarbeiteOeffnen(e)
        }
    }

    private fun verarbeiteOeffnen(e: Tresor.Ergebnis) {
        when (e) {
            is Tresor.Ergebnis.Offen -> {
                // Sofort ueberschreiben: Zum Anzeigen von Guthaben und
                // Verlauf braucht es keinen Schluessel.
                e.woerter.fill(0)
                // Die EINZIGE Stelle, die die Sperre verlassen darf.
                setze(entsperrt = true) {
                    it.copy(
                        // Hat sich die App im Hintergrund gesperrt, waehrend
                        // der Status einer Ueberweisung offen war (oder die
                        // erste Einfuehrung), geht es dorthin zurueck -
                        // sonst zum Hauptbildschirm.
                        // Ein wartender Anmelde-Code geht vor.
                        ansicht = it.wartenderCode?.let { c -> Ansicht.Anmelden(c) }
                            ?: it.nachEntsperren ?: Ansicht.Wallet,
                        nachEntsperren = null,
                        wartenderCode = null,
                        adresse = e.adresse,
                        arbeitet = false,
                        wartezeit = 0,
                        fehler = null,
                    )
                }
                kontoLaden()
            }

            is Tresor.Ergebnis.FalschePin -> setze {
                it.copy(
                    arbeitet = false,
                    wartezeit = e.wartezeit,
                    fehler = if (e.wartezeit > 0) t.modell.pinFalschWarten(e.wartezeit)
                    else t.modell.pinFalschVersuch(e.fehlversuche),
                )
            }

            is Tresor.Ergebnis.Gesperrt -> setze {
                it.copy(
                    arbeitet = false,
                    wartezeit = e.wartezeit,
                    fehler = t.modell.nochGesperrt(e.wartezeit),
                )
            }

            else -> setze { it.copy(arbeitet = false, fehler = beschreibe(e)) }
        }
    }

    /** Von Hand gesperrt, ueber das Schloss auf dem Hauptbildschirm. */
    fun sperren() = setze { gesperrt(it) }

    /**
     * [s] als gesperrter Zustand: die Sperre, und alles weg, was erst nach
     * dem Entsperren da war. Wohin es danach zurueckgeht, steht hier NICHT -
     * von Hand gesperrt geht es immer zum Hauptbildschirm.
     */
    private fun gesperrt(s: Stand): Stand = s.copy(
        ansicht = Ansicht.Sperre,
        nachEntsperren = null,
        laedtKonto = false,
        arbeitet = false,
        konto = null,
        weitereEintraege = emptyList(),
        letzterZeiger = null,
        fehler = null,
        hinweis = null,
        netzMeldung = null,
        sendeFehler = null,
    )

    /**
     * Die App ist in den Hintergrund gegangen: anderes Programm,
     * Startbildschirm, App-Uebersicht, Bildschirm aus. Die Wallet sperrt
     * sich (Wunsch des Besitzers, 11.11.2026) - was das heisst, steht in
     * [Hintergrundregel], durchgesetzt wird es in [setze].
     */
    fun inDenHintergrund() {
        draussen = true
        setze { it }
    }

    /** Die App ist wieder zu sehen. Entsperrt wird dadurch nichts. */
    fun zurueckImVordergrund() {
        draussen = false
    }

    // ---------------------------------------------------------------- Sprache

    /**
     * Die Sprache der App wechseln. Gilt sofort; eine stehende Meldung in
     * der alten Sprache verschwindet dabei.
     */
    fun spracheWaehlen(sprache: Sprache) {
        vorlieben.sprache = sprache.kennung
        setze { it.copy(sprache = sprache, fehler = null, hinweis = null, netzMeldung = null) }
        // Der Kanal der Mitteilungen traegt seinen Namen in den
        // Android-Einstellungen: mit umbenennen, wenn es ihn gibt.
        if (vorlieben.benachrichtigungen) Waechterdienst.kanalAnlegen(getApplication())
    }

    // ----------------------------------------------------------- Biometrie

    fun bioCipherZumEinschalten(): Cipher? =
        when (val s = bioSchluessel.zumVerschluesseln()) {
            is BioSchluessel.Stand.Bereit -> s.cipher
            else -> {
                setze { it.copy(fehler = t.modell.schluesselNichtAngelegt) }
                null
            }
        }

    fun bioEinschalten(pin: CharArray, cipher: Cipher) {
        viewModelScope.launch {
            val e = withContext(Dispatchers.Default) { tresor.bioEinschalten(pin, cipher) }
            pin.loeschen()
            if (e is Tresor.Ergebnis.Offen) {
                setze {
                    it.copy(
                        bioEingeschaltet = true,
                        hinweis = t.modell.fingerabdruckEingerichtet,
                        fehler = null,
                    )
                }
            } else {
                setze { it.copy(fehler = beschreibe(e)) }
            }
        }
    }

    fun bioAusschalten() {
        tresor.bioAusschalten()
        bioSchluessel.loeschen()
        setze { it.copy(bioEingeschaltet = false, hinweis = t.modell.fingerabdruckAbgeschaltet) }
    }

    // --------------------------------------------------------------- Netz

    /**
     * Konto, Markt und Spitze holen.
     *
     * [leise] ist der Unterschied zwischen "der Nutzer hat auf
     * Aktualisieren getippt" und "die Wallet sieht von selbst nach". Leise
     * heisst: Der Knopf zuckt nicht alle dreissig Sekunden auf "laedt …",
     * und ein Funkloch meldet sich nicht.
     *
     * Die schon nachgeladenen Verlaufsseiten bleiben in BEIDEN Faellen
     * stehen (seit dem Guthabenverlauf, UMBAU.md Abschnitt 3). Frueher warf
     * der Tipp auf Aktualisieren sie weg; mit dem Diagramm hiesse das, jedes
     * Mal alles neu zu laden.
     */
    fun kontoLaden(leise: Boolean = false) {
        val adresse = _stand.value.adresse ?: return
        viewModelScope.launch {
            if (!leise) setze { it.copy(laedtKonto = true, netzMeldung = null) }
            try {
                // Konto, Höhe und Gebührenlage zusammen: Ohne Markt und
                // Spitze lässt sich nicht entscheiden, welche Fassung einer
                // Überweisung gerade gilt - und dann wird nicht gesendet.
                val (k, m, kopf) = withContext(Dispatchers.IO) {
                    Triple(
                        spiegel.konto(adresse),
                        runCatching { spiegel.markt() }.getOrNull(),
                        runCatching { spiegel.kopf() }.getOrNull(),
                    )
                }
                // Die Uhr dieses Geraets in dem Augenblick, in dem die Antwort
                // da ist. Das ist der "Stand", den die Guthabenkarte nennt.
                val jetzt = System.currentTimeMillis() / 1000
                setze {
                    // Was schon nachgeladen ist, bleibt - ob die Wallet von
                    // selbst nachsieht oder jemand auf "Stand" tippt. Aber nur,
                    // wenn die neue erste Seite lueckenlos daran anschliesst;
                    // sonst wird neu begonnen (Verlaufslader.erneuert). Ganz
                    // von vorn faengt die App nach dem Sperren an.
                    // Inzwischen gesperrt: Hinter der Sperre wird nichts abgelegt.
                    // Nach dem Entsperren wird ohnehin neu geladen.
                    if (it.ansicht is Ansicht.Sperre) return@setze it.copy(laedtKonto = false)
                    val bestand = Verlaufslader.erneuert(it.bestand, k.verlauf, k.verlaufWeiter)
                    zahlungAusMitteilungZeigen(it.copy(
                        konto = k,
                        marktStand = m ?: it.marktStand,
                        spitze = kopf?.hoehe ?: it.spitze,
                        // Die erste Seite steht in [konto]; hier liegt der ganze
                        // Bestand. `Stand.verlauf` nimmt die Doppelten heraus.
                        weitereEintraege = bestand.eintraege,
                        letzterZeiger = bestand.weiter,
                        laedtKonto = false,
                        netzMeldung = null,
                        bekanntesGuthaben = k.guthaben,
                        bekanntSeit = jetzt,
                    ))
                }
                // Was der Nutzer im Vordergrund ohnehin sieht, braucht keine
                // Mitteilung - aber der Waechter muss es sich merken.
                // Sonst meldet er es spaeter als neu.
                withContext(Dispatchers.IO) {
                    merkeOhneMeldung(k)
                    vorlieben.standMerken(k.guthaben, jetzt)
                }
                // Fuer das Diagramm fehlen vielleicht noch aeltere Seiten.
                kurveNachladen()
            } catch (e: NetzFehler) {
                // Beim leisen Lauf keine Meldung: Ein Funkloch alle dreissig
                // Sekunden darf nicht rot ueber dem Guthaben blinken.
                if (leise) {
                    setze { it.copy(laedtKonto = false) }
                    return@launch
                }
                setze {
                    it.copy(
                        laedtKonto = false,
                        netzMeldung = if (e.offline) t.modell.keineVerbindungAlterStand
                        else t.modell.spiegelAntwortetNicht(e.status),
                        // Ohne Konto keine Zahlung zum Zeigen. Spaeter, mitten
                        // in etwas anderem, soll sie nicht ueberraschend
                        // aufgehen - also verwerfen, ausser hinter der Sperre.
                        wartendeZahlung = if (it.ansicht is Ansicht.Sperre) it.wartendeZahlung else null,
                    )
                }
            }
        }
    }

    private fun merkeOhneMeldung(konto: Konto) {
        val befund = Waechter.pruefe(
            konto,
            Waechter.Gedaechtnis(vorlieben.gesehenesGuthaben, vorlieben.geseheneTxids),
        )
        vorlieben.gesehenesGuthaben = befund.gedaechtnis.guthaben
        vorlieben.geseheneTxids = befund.gedaechtnis.txids
    }

    /**
     * Von selbst nachsehen, solange die App im Vordergrund ist.
     *
     * Das Aufrufen uebernimmt die Activity ueber `repeatOnLifecycle` - die
     * Schleife laeuft also nur, solange der Nutzer wirklich hinsieht, und
     * endet beim Wegschalten. Eine Wallet, die im Hintergrund weiterfragt,
     * waere ein Akkufresser; dafuer gibt es den Waechter.
     *
     * NICHT AUF JEDEM BILDSCHIRM. Auf der Sperre gibt es nichts zu zeigen,
     * und waehrend des Sendens soll nichts unter dem Entwurf wackeln.
     */
    suspend fun beobachten() {
        while (true) {
            val s = _stand.value
            if (s.adresse != null && darfNachsehen(s.ansicht) && !s.laedtKonto) {
                kontoLaden(leise = true)
            }
            delay(taktFuer(_stand.value.ansicht))
        }
    }

    private fun darfNachsehen(a: Ansicht): Boolean = when (a) {
        Ansicht.Wallet, Ansicht.Verlauf, Ansicht.Empfangen -> true
        is Ansicht.Bewegung -> true
        is Ansicht.SendenFertig -> true
        else -> false
    }

    /**
     * Wie oft nachgesehen wird.
     *
     * Nach dem Senden schneller: Da schaut der Nutzer der Ueberweisung
     * zu, wie sie durch die fuenf Schritte geht. Sonst reicht halbe
     * Minute - ein Block dauert rund zehn.
     */
    private fun taktFuer(a: Ansicht): Long =
        if (a is Ansicht.SendenFertig) TAKT_SENDEN else TAKT_NORMAL

    fun verlaufNachladen() {
        val adresse = _stand.value.adresse ?: return
        val weiter = _stand.value.letzterZeiger ?: return

        viewModelScope.launch {
            setze { it.copy(laedtKonto = true) }
            try {
                val seite = withContext(Dispatchers.IO) {
                    spiegel.verlauf(adresse, vor = weiter, limit = 40)
                }
                setze {
                    // Hat inzwischen jemand anders weitergeblaettert (das
                    // Diagramm laedt leise mit) oder neu begonnen, gehoert
                    // diese Seite nicht mehr an diese Stelle.
                    if (it.letzterZeiger != weiter) return@setze it.copy(laedtKonto = false)
                    it.copy(
                        weitereEintraege = it.weitereEintraege + seite.eintraege,
                        laedtKonto = false,
                        letzterZeiger = seite.weiter,
                    )
                }
            } catch (e: NetzFehler) {
                setze {
                    it.copy(
                        laedtKonto = false,
                        netzMeldung = if (e.offline) t.modell.keineVerbindung
                        else t.modell.spiegelAntwortetNicht(e.status),
                    )
                }
            }
        }
    }

    // ---------------------------------------------------------- Einfuehrung

    /** Die Einfuehrung noch einmal ansehen - von den Einstellungen aus. */
    fun einfuehrungZeigen() = setze {
        it.copy(ansicht = Ansicht.Einfuehrung(ausMehr = true), fehler = null, hinweis = null)
    }

    /** Zu Ende gesehen oder uebersprungen: dorthin zurueck, wo sie herkam. */
    fun einfuehrungFertig() {
        val a = _stand.value.ansicht as? Ansicht.Einfuehrung ?: return
        setze { it.copy(ansicht = if (a.ausMehr) Ansicht.Einstellungen else Ansicht.Wallet) }
        // Nach dem Einrichten ist das der erste Blick auf die Wallet.
        if (!a.ausMehr) kontoLaden(leise = true)
    }

    // ----------------------------------------------------- Guthabenverlauf

    /** Den Zeitraum des Diagramms waehlen; er wird gemerkt. */
    fun zeitraumWaehlen(zeitraum: Guthabenverlauf.Zeitraum) {
        vorlieben.zeitraumTage = zeitraum.tage
        setze { it.copy(zeitraum = zeitraum) }
        kurveNachladen()
    }

    /** Laeuft gerade ein Durchgang? Nur vom Hauptfaden gelesen und gesetzt. */
    private var kurveLaedt = false

    /**
     * Aeltere Seiten holen, bis der gewaehlte Zeitraum abgedeckt ist.
     *
     * LEISE (REGELN.md, Regel 26): Der Knopf auf der Guthabenkarte zuckt
     * nicht, ein Funkloch meldet sich nicht. Was fehlt, sagt das Diagramm
     * selbst - es zeigt dann nur den belegten Teil.
     *
     * NUR AUF DEM HAUPTBILDSCHIRM. Dort steht das Diagramm; anderswo
     * braucht niemand die Seiten, und waehrend des Sendens soll nichts
     * nebenher laufen. Gerufen wird nach jedem Laden des Kontos und beim
     * Wechsel des Zeitraums - also hoechstens im Takt von [beobachten],
     * der nur im Vordergrund laeuft.
     */
    private fun kurveNachladen() {
        if (kurveLaedt) return
        val s = _stand.value
        if (s.ansicht != Ansicht.Wallet) return
        val adresse = s.adresse ?: return
        val bestand = s.bestand ?: return
        val jetzt = s.bekanntSeit ?: return
        val beginn = s.zeitraum.beginn(jetzt)
        if (Verlaufslader.abgedeckt(bestand, beginn)) return

        kurveLaedt = true
        viewModelScope.launch {
            try {
                val durchgang = withContext(Dispatchers.IO) {
                    Verlaufslader.lade(spiegel, adresse, bestand, beginn)
                }
                setze {
                    // Nur uebernehmen, wenn der Bestand noch der ist, von dem
                    // aus geladen wurde. Wurde inzwischen gesperrt, die Wallet
                    // gewechselt, neu begonnen oder von Hand geblaettert,
                    // passen die Seiten nicht mehr an diese Stelle.
                    if (it.konto == null || it.adresse != adresse ||
                        it.letzterZeiger != bestand.weiter
                    ) {
                        return@setze it
                    }
                    it.copy(
                        weitereEintraege = it.weitereEintraege +
                            durchgang.bestand.eintraege.drop(bestand.eintraege.size),
                        letzterZeiger = durchgang.bestand.weiter,
                    )
                }
                // durchgang.fehler bleibt ungenannt: Das ist der leise Weg.
            } finally {
                kurveLaedt = false
            }
        }
    }

    // -------------------------------------------------------------- Senden

    fun sendenStarten() = setze {
        it.copy(ansicht = Ansicht.SendenEingabe, sendeFehler = null, fehler = null)
    }

    /**
     * Prüfen, ohne den Schlüssel anzufassen.
     *
     * Erst wenn das durchgeht, sieht der Nutzer den Prüfschritt - und erst
     * danach wird die PIN verlangt.
     */
    fun sendenPruefen(empfaenger: String, betrag: String, notiz: String) {
        val s = _stand.value
        when (
            val p = Senden.pruefe(
                eigeneAdresse = s.adresse ?: "",
                konto = s.konto,
                markt = s.marktStand,
                spitze = s.spitze,
                empfaengerText = empfaenger,
                betragText = betrag,
                notizText = notiz,
            )
        ) {
            is Senden.Pruefung.Bereit -> setze {
                it.copy(ansicht = Ansicht.SendenPruefen(p.entwurf), sendeFehler = null)
            }

            // `Senden.kt` meldet auf Deutsch; uebersetzt wird hier, beim
            // Anzeigen. An der Pruefung selbst aendert das nichts.
            is Senden.Pruefung.Problem -> setze {
                it.copy(sendeFehler = Sendemeldungen.uebersetze(p.grund, it.sprache))
            }
        }
    }

    fun sendenBestaetigen() {
        val a = _stand.value.ansicht
        if (a is Ansicht.SendenPruefen) {
            setze { it.copy(ansicht = Ansicht.SendenPin(a.entwurf), sendeFehler = null) }
        }
    }

    fun sendenZurueckZumEntwurf() {
        val a = _stand.value.ansicht
        val entwurf = when (a) {
            is Ansicht.SendenPruefen -> a.entwurf
            is Ansicht.SendenPin -> a.entwurf
            else -> null
        }
        setze {
            it.copy(
                ansicht = if (entwurf == null) Ansicht.Wallet else Ansicht.SendenEingabe,
                sendeFehler = null,
            )
        }
    }

    /**
     * Der einzige Ort, an dem die Wallet einen Schlüssel anfasst, nachdem
     * sie entsperrt wurde.
     *
     * Die Wörter kommen aus dem Tresor, werden benutzt und sofort
     * überschrieben - auch wenn unterwegs etwas schiefgeht.
     */
    fun sendenUnterschreiben(pin: CharArray) =
        unterschreiben(oeffne = { tresor.oeffnen(pin) }, aufraeumen = { pin.loeschen() })

    /**
     * Dasselbe mit dem Finger.
     *
     * KEIN SCHWAECHERER WEG, SONDERN DERSELBE. `bioOeffnen` holt den
     * Tresorschluessel aus der Huelle, die beim Einschalten mit der PIN
     * gefuellt wurde - der Finger ersetzt die sechs Ziffern, nicht den
     * Schluessel. Wer die Biometrie eingeschaltet hat, hat diese
     * Entscheidung schon fuer das Entsperren getroffen; sie hier noch
     * einmal zu verweigern waere Theater, kein Schutz.
     */
    fun sendenUnterschreibenBio(cipher: Cipher) =
        unterschreiben(oeffne = { tresor.bioOeffnen(cipher) }, aufraeumen = {})

    private fun unterschreiben(
        oeffne: () -> Tresor.Ergebnis,
        aufraeumen: () -> Unit,
    ) {
        val a = _stand.value.ansicht
        if (a !is Ansicht.SendenPin) {
            aufraeumen()
            return
        }
        val adresse = _stand.value.adresse ?: run {
            aufraeumen()
            return
        }

        viewModelScope.launch {
            setze { it.copy(arbeitet = true, sendeFehler = null) }

            // Nur fuer die ANZEIGE der fuenf Schritte: die Uhr dieses Geraets
            // unmittelbar vor dem Unterschreiben. Es wird eine Zeit gelesen,
            // sonst nichts - Entwurf und Aufruf darunter sind unveraendert.
            var unterschriebenUm: Long? = null

            // Hat der Tresor die PIN nicht genommen, steht hier, was er dazu
            // sagt - und es gibt kein Ergebnis: Unterschrieben wurde nichts.
            var pinMeldung: String? = null
            val texte = t

            val ergebnis: Senden.Ergebnis? = withContext(Dispatchers.Default) {
                when (val auf = oeffne()) {
                    is Tresor.Ergebnis.Offen -> try {
                        unterschriebenUm = System.currentTimeMillis() / 1000
                        Senden.unterschreibenUndSenden(a.entwurf, auf.woerter, adresse, spiegel)
                    } finally {
                        auf.woerter.loeschen()
                    }

                    is Tresor.Ergebnis.FalschePin -> {
                        pinMeldung =
                            if (auf.wartezeit > 0) texte.modell.pinFalschWarten(auf.wartezeit)
                            else texte.modell.pinFalschVersuch(auf.fehlversuche)
                        null
                    }

                    is Tresor.Ergebnis.Gesperrt -> {
                        pinMeldung = texte.modell.nochGesperrt(auf.wartezeit)
                        null
                    }

                    else -> Senden.Ergebnis.Fehler(beschreibe(auf))
                }
            }
            // Wann die Antwort des Knotens da war - ebenfalls nur zum Anzeigen.
            val beantwortetUm = System.currentTimeMillis() / 1000
            aufraeumen()

            // Eine falsche PIN fuehrt nicht zum Ergebnisbildschirm - der
            // Nutzer soll es einfach noch einmal eingeben koennen. Dasselbe
            // gilt, solange der Tresor nach Fehlversuchen noch wartet:
            // Abgelehnt hat da niemand etwas, es wurde nur nicht geoeffnet.
            // (Frueher hing das an den ersten Woertern der Meldung; mit zwei
            // Sprachen haengt es am Ergebnis des Tresors.)
            if (ergebnis == null) {
                setze { it.copy(arbeitet = false, sendeFehler = pinMeldung) }
                return@launch
            }

            setze {
                it.copy(
                    ansicht = Ansicht.SendenFertig(ergebnis, a.entwurf),
                    arbeitet = false,
                    gesendeteTxid = (ergebnis as? Senden.Ergebnis.Angenommen)?.txid,
                    mempoolGesehen = false,
                    sendeFehler = null,
                    // "Angenommen" bekommt nur dann eine Zeit, wenn der Knoten
                    // angenommen HAT. Die Warteliste beginnt leer; ihre Zeit
                    // kommt, wenn die Ueberweisung dort gesehen wird.
                    sendeZeiten = Sendezeiten(
                        unterschrieben = unterschriebenUm,
                        angenommen = beantwortetUm
                            .takeIf { ergebnis is Senden.Ergebnis.Angenommen },
                    ),
                )
            }
            if (ergebnis is Senden.Ergebnis.Angenommen) kontoLaden()
        }
    }

    /** Die fünf Schritte zur zuletzt eingereichten Überweisung. */
    fun sendeSchritte(): List<Sendestatus.Teil>? {
        val s = _stand.value
        val txid = s.gesendeteTxid ?: return null
        val bild = Sendestatus.netzbild(s.konto, s.spitze, txid)
        if (bild.imMempool == true && !s.mempoolGesehen) {
            val jetzt = System.currentTimeMillis() / 1000
            setze {
                it.copy(
                    mempoolGesehen = true,
                    sendeZeiten = it.sendeZeiten.copy(warteliste = jetzt),
                )
            }
        }
        return Sendestatus.alsListe(Sendestatus.schritte(bild, s.mempoolGesehen))
    }

    /**
     * Was Knoten und Kette zur zuletzt eingereichten Überweisung zeigen -
     * für die Blocknummer und die Zahl der Bestätigungen neben den Schritten.
     */
    fun sendeNetzbild(): Sendestatus.Netzbild? {
        val s = _stand.value
        val txid = s.gesendeteTxid ?: return null
        return Sendestatus.netzbild(s.konto, s.spitze, txid)
    }

    // ---------------------------------------------------------- Umschalten

    fun zeige(ansicht: Ansicht) = setze { it.copy(ansicht = ansicht, fehler = null) }

    // ------------------------------------------------------------ Kontakte

    /**
     * Einen Kontakt anlegen oder aendern. Geprueft wird in `Kontakte.speichern`
     * - gueltige Adresse, Name nicht leer, keine Adresse zweimal. Erst wenn
     * das besteht, wird gespeichert und nach [danach] gewechselt; sonst
     * bleibt alles, wie es ist, und der Bildschirm zeigt den Grund.
     *
     * [bisher] ist die Adresse des Kontakts, der geaendert wird; null: neu.
     */
    fun kontaktSpeichern(
        name: String,
        adresse: String,
        bisher: String?,
        danach: Ansicht,
    ): Kontakte.Ergebnis {
        val e = Kontakte.speichern(_stand.value.kontakte, name, adresse, bisher)
        if (e is Kontakte.Ergebnis.Ok) {
            kontaktspeicher.liste = e.liste
            setze { it.copy(kontakte = e.liste, ansicht = danach, fehler = null) }
        }
        return e
    }

    fun kontaktEntfernen(adresse: String, danach: Ansicht) {
        val neu = Kontakte.entfernen(_stand.value.kontakte, adresse)
        kontaktspeicher.liste = neu
        setze { it.copy(kontakte = neu, ansicht = danach, fehler = null) }
    }

    // ----------------------------------------------------------- Verwalten

    fun pinAendern(alt: CharArray, neu: CharArray, fertig: (String?) -> Unit) {
        viewModelScope.launch {
            val e = withContext(Dispatchers.Default) { tresor.pinAendern(alt, neu) }
            alt.loeschen()
            neu.loeschen()
            if (e is Tresor.Ergebnis.Offen) {
                e.woerter.fill(0)
                // pinAendern schaltet die Biometrie ab: Die alte Huelle
                // enthielte den alten Schluessel.
                bioSchluessel.loeschen()
                setze {
                    it.copy(
                        bioEingeschaltet = false,
                        // Dass der Fingerabdruck neu eingerichtet werden muss,
                        // steht nur da, wenn er eingeschaltet WAR.
                        hinweis = if (it.bioEingeschaltet) t.modell.pinGeaendertBioNeu
                        else t.einstellungen.pinGeaendert,
                    )
                }
                fertig(null)
            } else {
                fertig(beschreibe(e))
            }
        }
    }

    fun tresorLoeschen() {
        tresor.loeschen()
        bioSchluessel.loeschen()
        // Ohne das gaelte der Verlauf der entfernten Adresse weiter als
        // "schon gesehen" - und die erste Bewegung der naechsten Wallet
        // bliebe stumm.
        vorlieben.gedaechtnisLeeren()
        vorlieben.benachrichtigungen = false
        // Die Kontakte gehen mit der Wallet: Sie sagen, mit wem ihr Besitzer
        // zu tun hatte, und haben auf dem Geraet nichts mehr verloren.
        kontaktspeicher.leeren()
        Waechterdienst.planen(getApplication(), false)
        setze {
            Stand(
                ansicht = Ansicht.Einstieg,
                bioMoeglich = it.bioMoeglich,
                vibration = it.vibration,
                verborgen = it.verborgen,
                zeitraum = it.zeitraum,
                sprache = it.sprache,
                hinweis = t.modell.walletEntfernt,
            )
        }
    }

    // ------------------------------------------------------- Einstellungen

    fun benachrichtigungen(an: Boolean) {
        val app = getApplication<Application>()
        vorlieben.benachrichtigungen = an
        if (an) Waechterdienst.kanalAnlegen(app)
        Waechterdienst.planen(app, an)
        setze {
            it.copy(
                benachrichtigungen = an,
                fehler = null,
                hinweis = if (an) t.modell.waechterAn else t.modell.waechterAus,
            )
        }
    }

    /**
     * Der Nutzer hat die Mitteilungen verweigert.
     *
     * Dann bleibt der Waechter aus. Ihn trotzdem laufen zu lassen hiesse,
     * Akku fuer Meldungen zu verbrauchen, die niemand zu sehen bekommt.
     */
    fun benachrichtigungenVerweigert() {
        vorlieben.benachrichtigungen = false
        Waechterdienst.planen(getApplication(), false)
        setze {
            it.copy(
                benachrichtigungen = false,
                hinweis = null,
                fehler = t.modell.ohneErlaubnis,
            )
        }
    }

    fun vibration(an: Boolean) {
        vorlieben.vibration = an
        setze { it.copy(vibration = an) }
    }

    /** Guthaben und Betraege auf dem Hauptbildschirm verbergen oder zeigen. */
    fun verbergen(an: Boolean) {
        vorlieben.guthabenVerborgen = an
        setze { it.copy(verborgen = an) }
    }

    // ---------------------------------------------- Die zwoelf Woerter

    /**
     * Die Woerter noch einmal zeigen - nach PIN.
     *
     * Der einzige andere Ort neben dem Unterschreiben, an dem die Wallet
     * nach dem Entsperren einen Schluessel anfasst. Hier bleiben die
     * Woerter laenger im Speicher als sonst, naemlich solange sie auf dem
     * Schirm stehen - anders geht es nicht, wenn man sie lesen soll. Der
     * Bildschirm traegt dafuer `FLAG_SECURE`, und [woerterVergessen]
     * raeumt beim Verlassen auf.
     */
    fun woerterZeigen(pin: CharArray) {
        viewModelScope.launch {
            setze { it.copy(arbeitet = true, fehler = null) }
            val e = withContext(Dispatchers.Default) { tresor.oeffnen(pin) }
            pin.loeschen()
            zeigeWoerter(e)
        }
    }

    fun woerterZeigenBio(cipher: Cipher) {
        viewModelScope.launch {
            setze { it.copy(arbeitet = true, fehler = null) }
            val e = withContext(Dispatchers.Default) { tresor.bioOeffnen(cipher) }
            zeigeWoerter(e)
        }
    }

    private fun zeigeWoerter(e: Tresor.Ergebnis) {
        if (e is Tresor.Ergebnis.Offen) {
            val woerter = String(e.woerter).trim().split(" ")
            e.woerter.loeschen()
            setze {
                it.copy(
                    ansicht = Ansicht.WoerterAnsehen(woerter),
                    arbeitet = false,
                    fehler = null,
                )
            }
        } else {
            setze {
                // Inzwischen gesperrt: Die Meldung gehoert zu einer Eingabe
                // unter "Einstellungen", nicht auf den Sperrbildschirm.
                if (it.ansicht is Ansicht.Sperre) it
                else it.copy(arbeitet = false, fehler = beschreibe(e))
            }
        }
    }

    /** Beim Verlassen: Die Ansicht traegt die Woerter, also weg damit. */
    fun woerterVergessen() = setze {
        it.copy(ansicht = Ansicht.Einstellungen, fehler = null)
    }

    // -------------------------------------------------------------- Signieren

    /** Was aus einer Bitte um eine Signatur geworden ist. */
    sealed interface Signierantwort {
        /**
         * Signiert. [gesendet]: Bei einer Anmeldung per Code, was die Plattform
         * auf die Signatur geantwortet hat; sonst null - dann wird die
         * Signatur zum Kopieren gezeigt.
         */
        data class Fertig(
            val ergebnis: Unterschrift.Ergebnis.Fertig,
            val gesendet: Rueckruf.Ergebnis?,
        ) : Signierantwort

        /** Die Pruefung hat abgelehnt. Es wurde nichts signiert. */
        data class Abgelehnt(val ergebnis: Unterschrift.Ergebnis.Abgelehnt) : Signierantwort

        /** PIN falsch oder der Tresor wartet noch: noch einmal eingeben. */
        data class PinFalsch(val meldung: String) : Signierantwort

        /** Es wurde nichts signiert. */
        data class Fehler(val meldung: String) : Signierantwort
    }

    /**
     * Eine Nachricht signieren - mit der PIN.
     *
     * [eingabe] ist die ROHE Eingabe: der Text aus dem Feld, oder ([istCode])
     * der Inhalt eines Anmelde-Codes. Was daraus signiert wird, entscheidet
     * `Unterschrift`, unmittelbar vor dem Unterschreiben - nicht der
     * Bildschirm.
     *
     * [vorDemSenden] wird gerufen, wenn signiert ist und die Antwort an die
     * Plattform hinausgeht; [fertig], wenn alles vorbei ist.
     */
    fun signieren(
        eingabe: String,
        istCode: Boolean,
        pin: CharArray,
        vorDemSenden: () -> Unit,
        fertig: (Signierantwort) -> Unit,
    ) = signierenMit(
        eingabe, istCode,
        oeffne = { tresor.oeffnen(pin) }, aufraeumen = { pin.loeschen() },
        vorDemSenden = vorDemSenden, fertig = fertig,
    )

    /** Dasselbe mit dem Fingerabdruck - durch dieselbe Funktion (REGELN.md, Regel 27). */
    fun signierenBio(
        eingabe: String,
        istCode: Boolean,
        cipher: Cipher,
        vorDemSenden: () -> Unit,
        fertig: (Signierantwort) -> Unit,
    ) = signierenMit(
        eingabe, istCode,
        oeffne = { tresor.bioOeffnen(cipher) }, aufraeumen = {},
        vorDemSenden = vorDemSenden, fertig = fertig,
    )

    private fun signierenMit(
        eingabe: String,
        istCode: Boolean,
        oeffne: () -> Tresor.Ergebnis,
        aufraeumen: () -> Unit,
        vorDemSenden: () -> Unit,
        fertig: (Signierantwort) -> Unit,
    ) {
        val adresse = _stand.value.adresse ?: run {
            aufraeumen()
            return
        }
        val texte = t

        viewModelScope.launch {
            // Entweder schon die Antwort (PIN falsch, Tresor gestoert) oder das
            // Ergebnis des Unterschreibens.
            var frueh: Signierantwort? = null
            val unterschrift: Unterschrift.Ergebnis? = withContext(Dispatchers.Default) {
                when (val auf = oeffne()) {
                    is Tresor.Ergebnis.Offen -> try {
                        val jetzt = System.currentTimeMillis()
                        if (istCode) Unterschrift.ausCode(eingabe, auf.woerter, adresse, jetzt)
                        else Unterschrift.text(eingabe, auf.woerter, adresse, jetzt)
                    } finally {
                        auf.woerter.loeschen()
                    }

                    is Tresor.Ergebnis.FalschePin -> {
                        frueh = Signierantwort.PinFalsch(
                            if (auf.wartezeit > 0) texte.modell.pinFalschWarten(auf.wartezeit)
                            else texte.modell.pinFalschVersuch(auf.fehlversuche),
                        )
                        null
                    }

                    is Tresor.Ergebnis.Gesperrt -> {
                        frueh = Signierantwort.PinFalsch(texte.modell.nochGesperrt(auf.wartezeit))
                        null
                    }

                    else -> {
                        frueh = Signierantwort.Fehler(beschreibe(auf))
                        null
                    }
                }
            }
            aufraeumen()

            val antwort: Signierantwort = when (unterschrift) {
                null -> frueh ?: Signierantwort.Fehler(texte.signatur.nichtsSigniert)

                is Unterschrift.Ergebnis.Abgelehnt -> Signierantwort.Abgelehnt(unterschrift)

                is Unterschrift.Ergebnis.Gestoert -> Signierantwort.Fehler(
                    texte.signatur.stoerung(unterschrift.stoerung) + " " + texte.signatur.nichtsSigniert,
                )

                is Unterschrift.Ergebnis.Fertig -> {
                    val rueckruf = unterschrift.rueckruf
                    val anmeldung = unterschrift.anmeldung
                    if (rueckruf != null && anmeldung != null) {
                        // Genau EIN POST, an die Adresse, die `Unterschrift`
                        // gegen die Domain der Nachricht geprueft hat -
                        // `Rueckruf.senden` prueft sie noch einmal.
                        vorDemSenden()
                        val gesendet = withContext(Dispatchers.IO) {
                            Rueckruf.senden(rueckruf, anmeldung.domain, unterschrift.signiert, rueckrufTransport)
                        }
                        Signierantwort.Fertig(unterschrift, gesendet)
                    } else {
                        Signierantwort.Fertig(unterschrift, null)
                    }
                }
            }
            fertig(antwort)
        }
    }

    /**
     * Ein `yskar:login`-Link hat die App geoeffnet.
     *
     * EIN LINK IST KEIN BEWEIS (REGELN.md, Regel 22): Er fuehrt nur dazu, dass
     * die Anfrage GEZEIGT wird. Ist die Wallet gesperrt, wartet er bis nach
     * dem Entsperren; unterschrieben wird in jedem Fall erst nach einer
     * weiteren PIN-Eingabe oder dem Finger. Ohne Wallet geschieht nichts, und
     * alles, was kein Anmelde-Code ist, wird uebergangen.
     */
    fun anmeldecodeEmpfangen(text: String) {
        if (text.length > CODE_MAX || !Nachricht.istAnmeldeCode(text)) return
        setze { s ->
            when {
                s.adresse == null -> s
                s.ansicht is Ansicht.Sperre || s.ansicht is Ansicht.Laedt -> s.copy(wartenderCode = text)
                else -> s.copy(ansicht = Ansicht.Anmelden(text), fehler = null, hinweis = null)
            }
        }
    }

    /**
     * Ein Tipp auf eine Mitteilung des Waechters hat die App geoeffnet.
     *
     * Wie beim Anmelde-Link (oben): Ist die Wallet gesperrt, wartet der
     * Schluessel, und gezeigt wird erst nach dem Entsperren - wenn das Konto
     * geladen ist und die Zahlung darin steht. Ohne Wallet geschieht
     * nichts, und was nicht die Form eines [Waechter.schluessel] hat, wird
     * uebergangen ([Mitteilungsziel.gueltig]).
     *
     * Ist die Wallet offen, springt sie nur aus einer Ansicht, die nichts
     * Begonnenes verliert ([darfZurZahlung]) - wer gerade sendet, signiert
     * oder die Woerter ansieht, wird nicht herausgerissen.
     */
    fun zahlungAusMitteilung(roh: String?) {
        val schluessel = Mitteilungsziel.gueltig(roh) ?: return
        var nachladen = false
        setze { s ->
            when {
                s.adresse == null -> s
                s.ansicht is Ansicht.Sperre || s.ansicht is Ansicht.Laedt ->
                    s.copy(wartendeZahlung = schluessel)
                !darfZurZahlung(s.ansicht) -> s
                else -> {
                    val e = Mitteilungsziel.finde(s.verlauf, schluessel)
                    nachladen = e == null
                    if (e != null) {
                        s.copy(
                            ansicht = Ansicht.Bewegung(e, ausVerlauf = false),
                            wartendeZahlung = null,
                            fehler = null,
                            hinweis = null,
                        )
                    } else {
                        // Noch nicht geladen: Die Mitteilung kann frischer
                        // sein als der Verlauf auf dem Bildschirm.
                        s.copy(wartendeZahlung = schluessel)
                    }
                }
            }
        }
        if (nachladen) kontoLaden()
    }

    /**
     * Nach dem Laden des Kontos: die wartende Zahlung aus einer Mitteilung
     * zeigen, wenn sie im Verlauf steht und die Ansicht es erlaubt. Danach
     * wartet sie nicht mehr - gefunden oder nicht.
     */
    private fun zahlungAusMitteilungZeigen(s: Stand): Stand {
        val schluessel = s.wartendeZahlung ?: return s
        val e = if (darfZurZahlung(s.ansicht)) Mitteilungsziel.finde(s.verlauf, schluessel) else null
        return s.copy(
            wartendeZahlung = null,
            ansicht = e?.let { Ansicht.Bewegung(it, ausVerlauf = false) } ?: s.ansicht,
        )
    }

    /** Ansichten, aus denen ein Tipp auf eine Mitteilung zur Zahlung springen darf. */
    private fun darfZurZahlung(a: Ansicht): Boolean =
        a is Ansicht.Wallet || a is Ansicht.Verlauf || a is Ansicht.Bewegung || a is Ansicht.Empfangen

    private fun beschreibe(e: Tresor.Ergebnis): String = when (e) {
        is Tresor.Ergebnis.Offen -> "offen"
        is Tresor.Ergebnis.FalschePin -> t.modell.pinFalsch
        is Tresor.Ergebnis.Gesperrt -> t.modell.nochGesperrt(e.wartezeit)
        Tresor.Ergebnis.KeinTresor -> t.modell.keineWalletHier
        Tresor.Ergebnis.BiometrieNeuEinrichten -> t.modell.fingerabdruckNeuEinrichten

        is Tresor.Ergebnis.Fehler -> t.modell.tresorFehler(e.grund)
    }

    private companion object {
        /** Laenger ist kein Anmelde-Code: Nachricht und Rueckruf zusammen bleiben weit darunter. */
        const val CODE_MAX = 4_000

        /** Ein Block dauert rund zehn Minuten; halbe Minute genuegt. */
        const val TAKT_NORMAL = 30_000L

        /** Nach dem Senden sieht der Nutzer zu. */
        const val TAKT_SENDEN = 10_000L
    }
}
