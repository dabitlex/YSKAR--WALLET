package com.yskar.wallet

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.yskar.wallet.sprache.texteFuer
import com.yskar.wallet.tresor.loeschen
import com.yskar.wallet.ui.BewegungScreen
import com.yskar.wallet.ui.EinfuehrungScreen
import com.yskar.wallet.ui.EinstellungenScreen
import com.yskar.wallet.ui.EinstiegScreen
import com.yskar.wallet.ui.Hintergrundschutz
import com.yskar.wallet.ui.LocalTexte
import com.yskar.wallet.ui.LocalVibration
import com.yskar.wallet.ui.Mitteilungsziel
import com.yskar.wallet.ui.EmpfangenScreen
import com.yskar.wallet.ui.Buehne
import com.yskar.wallet.ui.PinSetzenScreen
import com.yskar.wallet.ui.SelbsttestScreen
import com.yskar.wallet.ui.SendenEingabeScreen
import com.yskar.wallet.ui.SendenFertigScreen
import com.yskar.wallet.ui.SendenPinScreen
import com.yskar.wallet.ui.SendenPruefenScreen
import com.yskar.wallet.kontakte.Kontakte
import com.yskar.wallet.ui.KontaktBearbeitenScreen
import com.yskar.wallet.ui.KontakteScreen
import com.yskar.wallet.ui.SensorFrage
import com.yskar.wallet.ui.SignierenScreen
import com.yskar.wallet.ui.Signierstart
import com.yskar.wallet.ui.Sichtschutz
import com.yskar.wallet.ui.SperreScreen
import com.yskar.wallet.ui.VerlaufScreen
import com.yskar.wallet.ui.WalletModel
import com.yskar.wallet.ui.WalletScreen
import com.yskar.wallet.ui.WiederherstellenScreen
import com.yskar.wallet.ui.WoerterAnsehenScreen
import com.yskar.wallet.ui.WoerterPruefenScreen
import com.yskar.wallet.ui.WoerterZeigenScreen
import com.yskar.wallet.ui.YskarTheme
import com.yskar.wallet.ui.nichtVorn
import kotlinx.coroutines.launch

/**
 * Die Wallet.
 *
 * FragmentActivity, weil BiometricPrompt das verlangt. Die Navigation ist
 * ein schlichtes `when` ueber den Zustand im [WalletModel] - fuer acht
 * Bildschirme ohne tiefe Verschachtelung braucht es keine Navigationsbibliothek,
 * und der Zustand bleibt an einer Stelle statt in Argumenten verstreut.
 */
class MainActivity : FragmentActivity() {

    /**
     * Hier statt im Composable, weil der Startbildschirm den Zustand schon
     * braucht, bevor das erste Bild gezeichnet wird. Es ist derselbe
     * ViewModel - `viewModels()` und `viewModel()` greifen auf denselben
     * Speicher der Activity zu.
     */
    private val model: WalletModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        val start = installSplashScreen()
        super.onCreate(savedInstanceState)

        // Der Startbildschirm bleibt, bis der Tresor gelesen ist - sonst
        // stuende dazwischen kurz eine leere Flaeche (Ansicht.Laedt zeichnet
        // nichts). MINDESTENS laesst den Ablauf des Zeichens zu Ende laufen;
        // HOECHSTENS ist die Notbremse, damit ein haengender Tresor die App
        // nicht fuer immer hinter dem Startbildschirm einsperrt.
        val anfang = SystemClock.uptimeMillis()
        start.setKeepOnScreenCondition {
            val vergangen = SystemClock.uptimeMillis() - anfang
            val laedtNoch = model.stand.value.ansicht == WalletModel.Ansicht.Laedt
            vergangen < MINDESTENS || (laedtNoch && vergangen < HOECHSTENS)
        }

        // Von selbst nachsehen - aber NUR solange die App vorne ist.
        // `repeatOnLifecycle` beendet die Schleife beim Wegschalten und
        // startet sie beim Zurueckkommen neu; das erste Nachsehen passiert
        // also genau dann, wenn der Nutzer wieder hinschaut. Eine Schleife
        // im ViewModel ohne diese Klammer liefe weiter, waehrend das
        // Telefon in der Tasche steckt.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) { model.beobachten() }
        }

        // Im Hintergrund: sperren, und in der App-Uebersicht nichts zeigen
        // (Wunsch des Besitzers, 11.11.2026). Was dazugehoert, steht in
        // ui/Hintergrundschutz.kt.
        Hintergrundschutz.einrichten(
            activity = this,
            onDraussen = model::inDenHintergrund,
            onZurueck = model::zurueckImVordergrund,
        )

        // Ueber einen `yskar:login`-Link geoeffnet? Nur beim ersten Aufbau -
        // nach einem Neuaufbau (Schriftgroesse, dunkles Schema) traegt die
        // Activity denselben Intent noch einmal.
        if (savedInstanceState == null) linkAnnehmen(intent)

        enableEdgeToEdge()
        setContent {
            // `collectAsState`, NICHT `…WithLifecycle`: Die Sperre im
            // Hintergrund muss hier ankommen, WAEHREND die App draussen ist.
            // Sonst zeichnete das erste Bild nach der Rueckkehr noch den
            // alten, offenen Bildschirm.
            val stand by model.stand.collectAsState()
            YskarTheme {
                // Einmal hier gesetzt, ueberall unten sichtbar: Das
                // Ziffernfeld und der Scanner fragen den Wert selbst ab,
                // statt ihn durch fuenf Bildschirme gereicht zu bekommen.
                CompositionLocalProvider(
                    LocalVibration provides stand.vibration,
                    LocalTexte provides texteFuer(stand.sprache),
                ) {
                    // Grund, Raender der Systemleisten und Schriftfarbe
                    // kommen aus `Buehne` - derselben Funktion, durch die
                    // auch der Testrahmen der Bildschirmfotos zeichnet.
                    Buehne {
                        // Der Vorhang liegt, solange die App nicht vorn ist.
                        Sichtschutz(verdeckt = nichtVorn()) {
                            Wallet(activity = this@MainActivity, model = model)
                        }
                    }
                }
            }
        }
    }

    /** Die App lief schon und wird ueber einen Link nach vorn geholt. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        linkAnnehmen(intent)
    }

    /**
     * Einen `yskar:`-Link ans Modell geben. Was kein Anmelde-Code ist,
     * uebergeht das Modell; und ein Anmelde-Code fuehrt nur dazu, dass die
     * Anfrage gezeigt wird - nach dem Entsperren, und unterschrieben erst
     * nach einer weiteren PIN (REGELN.md, Regel 22 und 39).
     */
    private fun linkAnnehmen(intent: Intent?) {
        // Ein Tipp auf eine Mitteilung des Waechters: die Zahlung zeigen,
        // nach dem Entsperren (ui/Mitteilungsziel.kt).
        if (intent?.action == Mitteilungsziel.AKTION) {
            model.zahlungAusMitteilung(intent.getStringExtra(Mitteilungsziel.SCHLUESSEL))
            return
        }
        if (intent?.action != Intent.ACTION_VIEW) return
        val text = intent.dataString ?: return
        model.anmeldecodeEmpfangen(text)
    }

    private companion object {
        /**
         * Der Ablauf in symbol_start.xml ist nach 480 ms fertig. Die 140 ms
         * darueber sind kein Puffer, sondern der Punkt der Sache: Sonst
         * verschwindet der Stamm in derselben Lidschlagdauer, in der er
         * erscheint, und das vollstaendige Zeichen bekaeme niemand zu sehen.
         */
        const val MINDESTENS = 620L
        const val HOECHSTENS = 2_500L
    }
}

@Composable
private fun Wallet(
    activity: FragmentActivity,
    model: WalletModel,
    modifier: Modifier = Modifier,
) {
    val stand by model.stand.collectAsState()
    val scope = rememberCoroutineScope()
    val t = LocalTexte.current

    LaunchedEffect(Unit) {
        model.bioMoeglichkeit(SensorFrage.moeglich(activity))
    }

    val mitteilungsFrage = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { ja ->
        if (ja) model.benachrichtigungen(true) else model.benachrichtigungenVerweigert()
    }

    /**
     * Erst fragen, dann einschalten.
     *
     * Ab Android 13 ist das Mitteilen eine Erlaubnis wie jede andere.
     * Gefragt wird genau hier - wenn der Besitzer den Schalter umlegt -
     * und nicht beim ersten Start. Wer den Waechter nicht will, bekommt
     * die Frage nie zu sehen.
     */
    fun benachrichtigungenUmschalten(an: Boolean) {
        if (!an) {
            model.benachrichtigungen(false)
            return
        }
        val noetig = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                activity,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        if (noetig) {
            mitteilungsFrage.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            model.benachrichtigungen(true)
        }
    }

    /** Entsperren per Fingerabdruck: Cipher holen, Sensor fragen, oeffnen. */
    fun bioEntsperren() {
        val cipher = model.bioCipherZumOeffnen() ?: return
        scope.launch {
            when (val a = SensorFrage.frage(activity, t.modell.bioEntsperren, cipher, t)) {
                is SensorFrage.Antwort.Ok ->
                    a.cipher?.let { model.bioEntsperren(it) }

                is SensorFrage.Antwort.Abbruch -> Unit // der Nutzer wollte nicht
            }
        }
    }

    /**
     * Mit dem Finger unterschreiben.
     *
     * Derselbe Cipher wie beim Entsperren - die Huelle im Keystore
     * enthaelt den Tresorschluessel, und der ist fuer beides derselbe.
     */
    fun bioUnterschreiben() {
        val cipher = model.bioCipherZumOeffnen() ?: return
        scope.launch {
            when (val a = SensorFrage.frage(activity, t.modell.bioUnterschreiben, cipher, t)) {
                is SensorFrage.Antwort.Ok -> a.cipher?.let { model.sendenUnterschreibenBio(it) }
                is SensorFrage.Antwort.Abbruch -> Unit
            }
        }
    }

    /** Die zwoelf Woerter mit dem Finger statt mit der PIN oeffnen. */
    fun bioWoerter() {
        val cipher = model.bioCipherZumOeffnen() ?: return
        scope.launch {
            when (val a = SensorFrage.frage(activity, t.modell.bioWoerter, cipher, t)) {
                is SensorFrage.Antwort.Ok -> a.cipher?.let { model.woerterZeigenBio(it) }
                is SensorFrage.Antwort.Abbruch -> Unit
            }
        }
    }

    /** Biometrie einschalten: PIN kam schon, jetzt den Sensor fragen. */
    fun bioEinschalten(pin: CharArray) {
        val cipher = model.bioCipherZumEinschalten()
        if (cipher == null) {
            pin.loeschen()
            return
        }
        scope.launch {
            when (val a = SensorFrage.frage(activity, t.modell.bioEinrichten, cipher, t)) {
                is SensorFrage.Antwort.Ok ->
                    if (a.cipher != null) model.bioEinschalten(pin, a.cipher) else pin.loeschen()

                is SensorFrage.Antwort.Abbruch -> pin.loeschen()
            }
        }
    }

    // Signieren mit dem Fingerabdruck. Bricht der Nutzer ab oder ist der
    // Schluessel nicht mehr zu gebrauchen, erfaehrt es der Bildschirm ueber
    // [abgebrochen] - sonst wartete er fuer immer.
    fun bioSignieren(
        eingabe: String,
        istCode: Boolean,
        vorDemSenden: () -> Unit,
        fertig: (WalletModel.Signierantwort) -> Unit,
        abgebrochen: () -> Unit,
    ) {
        val cipher = model.bioCipherZumOeffnen()
        if (cipher == null) {
            abgebrochen()
            return
        }
        scope.launch {
            when (val a = SensorFrage.frage(activity, t.signatur.bioGrund, cipher, t)) {
                is SensorFrage.Antwort.Ok ->
                    if (a.cipher != null) {
                        model.signierenBio(eingabe, istCode, a.cipher, vorDemSenden, fertig)
                    } else abgebrochen()

                is SensorFrage.Antwort.Abbruch -> abgebrochen()
            }
        }
    }

    val ansicht = stand.ansicht

    // Zurueck-Taste: aus Unterseiten zurueck zur Wallet, sonst wie gewohnt.
    BackHandler(
        enabled = ansicht is WalletModel.Ansicht.Empfangen ||
            ansicht is WalletModel.Ansicht.Verlauf ||
            ansicht is WalletModel.Ansicht.Einstellungen ||
            ansicht is WalletModel.Ansicht.Selbsttest ||
            ansicht is WalletModel.Ansicht.SendenEingabe ||
            ansicht is WalletModel.Ansicht.SendenFertig
    ) {
        model.zeige(WalletModel.Ansicht.Wallet)
    }

    // Aus einer Bewegung zurueck in den Verlauf - nicht auf den
    // Hauptbildschirm. Wer die dritte Zeile von unten angetippt hat, will
    // nicht wieder von oben anfangen.
    BackHandler(enabled = ansicht is WalletModel.Ansicht.Bewegung) {
        val aus = (ansicht as WalletModel.Ansicht.Bewegung).ausVerlauf
        model.zeige(
            if (aus) WalletModel.Ansicht.Verlauf else WalletModel.Ansicht.Wallet,
        )
    }

    // Die Woerter stehen IN der Ansicht. Zurueck heisst hier: vergessen.
    BackHandler(enabled = ansicht is WalletModel.Ansicht.WoerterAnsehen) {
        model.woerterVergessen()
    }

    // Aus dem Prüfschritt und der PIN geht es zur Eingabe zurück, nicht
    // direkt zur Wallet: Sonst wäre die mühsam getippte Adresse weg.
    BackHandler(
        enabled = ansicht is WalletModel.Ansicht.SendenPruefen ||
            ansicht is WalletModel.Ansicht.SendenPin
    ) {
        model.sendenZurueckZumEntwurf()
    }

    when (ansicht) {
        WalletModel.Ansicht.Laedt -> Unit

        WalletModel.Ansicht.Einstieg -> EinstiegScreen(
            onNeu = model::neueWallet,
            onWiederherstellen = model::wiederherstellenStarten,
            modifier = modifier,
            onSprache = model::spracheWaehlen,
        )

        is WalletModel.Ansicht.WoerterZeigen -> WoerterZeigenScreen(
            woerter = ansicht.woerter,
            onWeiter = model::woerterGeschrieben,
            onAbbrechen = model::zurueckZumEinstieg,
            modifier = modifier,
        )

        is WalletModel.Ansicht.WoerterPruefen -> WoerterPruefenScreen(
            positionen = ansicht.positionen,
            fehler = stand.fehler,
            onPruefen = { model.woerterGeprueft(it) },
            onZurueck = model::woerterNochmalZeigen,
            modifier = modifier,
        )

        WalletModel.Ansicht.Wiederherstellen -> WiederherstellenScreen(
            onUebernehmen = model::woerterUebernehmen,
            onAbbrechen = model::zurueckZumEinstieg,
            modifier = modifier,
        )

        is WalletModel.Ansicht.PinSetzen -> PinSetzenScreen(
            laeuft = stand.arbeitet,
            fehler = stand.fehler,
            onFertig = model::tresorAnlegen,
            onAbbrechen = model::zurueckZumEinstieg,
            modifier = modifier,
        )

        WalletModel.Ansicht.Sperre -> SperreScreen(
            adresse = stand.adresse,
            bioEingeschaltet = stand.bioEingeschaltet,
            laeuft = stand.arbeitet,
            fehler = stand.fehler,
            wartezeit = stand.wartezeit,
            onPin = model::entsperren,
            onBio = { bioEntsperren() },
            modifier = modifier,
        )

        WalletModel.Ansicht.Wallet -> WalletScreen(
            adresse = stand.adresse ?: "",
            konto = stand.konto,
            laedt = stand.laedtKonto,
            netzMeldung = stand.netzMeldung,
            hinweis = stand.hinweis,
            verborgen = stand.verborgen,
            bekanntesGuthaben = stand.bekanntesGuthaben,
            bekanntSeit = stand.bekanntSeit,
            onVerbergen = model::verbergen,
            kontakte = stand.kontakte,
            kurve = stand.kurve,
            zeitraum = stand.zeitraum,
            onZeitraum = model::zeitraumWaehlen,
            onAktualisieren = model::kontoLaden,
            onSenden = model::sendenStarten,
            onEmpfangen = { model.zeige(WalletModel.Ansicht.Empfangen) },
            onVerlauf = { model.zeige(WalletModel.Ansicht.Verlauf) },
            onEinstellungen = { model.zeige(WalletModel.Ansicht.Einstellungen) },
            onSperren = model::sperren,
            onBewegung = { e ->
                model.zeige(WalletModel.Ansicht.Bewegung(e, ausVerlauf = false))
            },
            modifier = modifier,
        )

        WalletModel.Ansicht.Empfangen -> EmpfangenScreen(
            adresse = stand.adresse ?: "",
            onZurueck = { model.zeige(WalletModel.Ansicht.Wallet) },
            modifier = modifier,
        )

        WalletModel.Ansicht.Verlauf -> VerlaufScreen(
            eintraege = stand.verlauf,
            kontakte = stand.kontakte,
            // Ohne frisches Konto keine Linie: Aus dem gemerkten Guthaben
            // allein laesst sich nichts zurueckrechnen.
            kurve = if (stand.konto == null) null else stand.kurve,
            zeitraum = stand.zeitraum,
            onZeitraum = model::zeitraumWaehlen,
            verborgen = stand.verborgen,
            laedt = stand.laedtKonto,
            kannNachladen = stand.letzterZeiger != null,
            onNachladen = model::verlaufNachladen,
            onZurueck = { model.zeige(WalletModel.Ansicht.Wallet) },
            onBewegung = { e ->
                model.zeige(WalletModel.Ansicht.Bewegung(e, ausVerlauf = true))
            },
            modifier = modifier,
        )

        is WalletModel.Ansicht.Bewegung -> BewegungScreen(
            eintrag = ansicht.eintrag,
            spitze = stand.spitze,
            kontakte = stand.kontakte,
            // Aus der Zahlung einen Kontakt machen; danach wieder hierher.
            onKontaktAnlegen = { adr ->
                model.zeige(WalletModel.Ansicht.KontaktBearbeiten(null, adr, ansicht))
            },
            onZurueck = {
                model.zeige(
                    if (ansicht.ausVerlauf) WalletModel.Ansicht.Verlauf
                    else WalletModel.Ansicht.Wallet,
                )
            },
            modifier = modifier,
        )

        WalletModel.Ansicht.Kontaktliste -> KontakteScreen(
            kontakte = stand.kontakte,
            onNeu = { model.zeige(WalletModel.Ansicht.KontaktBearbeiten(null, null, ansicht)) },
            onBearbeiten = { model.zeige(WalletModel.Ansicht.KontaktBearbeiten(it, null, ansicht)) },
            onZurueck = { model.zeige(WalletModel.Ansicht.Einstellungen) },
            modifier = modifier,
        )

        // Der Schluessel `ansicht` setzt die Felder zurueck, wenn ein anderer
        // Kontakt geoeffnet wird.
        is WalletModel.Ansicht.KontaktBearbeiten -> key(ansicht) {
            KontaktBearbeitenScreen(
                vorlage = ansicht.vorlage,
                adresseVorgabe = ansicht.adresseVorgabe,
                onSpeichern = { name, adresse ->
                    model.kontaktSpeichern(name, adresse, ansicht.vorlage?.adresse, ansicht.zurueck)
                },
                onEntfernen = ansicht.vorlage?.let { k ->
                    { model.kontaktEntfernen(k.adresse, ansicht.zurueck) }
                },
                onZurueck = { model.zeige(ansicht.zurueck) },
                modifier = modifier,
            )
        }

        WalletModel.Ansicht.Einstellungen -> EinstellungenScreen(
            adresse = stand.adresse,
            bioMoeglich = stand.bioMoeglich,
            bioEingeschaltet = stand.bioEingeschaltet,
            benachrichtigungen = stand.benachrichtigungen,
            vibration = stand.vibration,
            verborgen = stand.verborgen,
            sprache = stand.sprache,
            hinweis = stand.hinweis,
            fehler = stand.fehler,
            onBioEin = { bioEinschalten(it) },
            onBioAus = model::bioAusschalten,
            onPinAendern = model::pinAendern,
            onBenachrichtigungen = { benachrichtigungenUmschalten(it) },
            onVibration = model::vibration,
            onVerbergen = model::verbergen,
            onSprache = model::spracheWaehlen,
            onWoerterZeigen = model::woerterZeigen,
            onWoerterZeigenBio = { bioWoerter() },
            onSelbsttest = { model.zeige(WalletModel.Ansicht.Selbsttest) },
            kontaktAnzahl = stand.kontakte.size,
            onKontakte = { model.zeige(WalletModel.Ansicht.Kontaktliste) },
            onAnmelden = { model.zeige(WalletModel.Ansicht.Anmelden(code = null)) },
            onSignieren = { model.zeige(WalletModel.Ansicht.Signieren) },
            onEinfuehrung = model::einfuehrungZeigen,
            onLoeschen = model::tresorLoeschen,
            onZurueck = { model.zeige(WalletModel.Ansicht.Wallet) },
            modifier = modifier,
        )

        is WalletModel.Ansicht.WoerterAnsehen -> WoerterAnsehenScreen(
            woerter = ansicht.woerter,
            onZurueck = model::woerterVergessen,
            modifier = modifier,
        )

        is WalletModel.Ansicht.Einfuehrung -> EinfuehrungScreen(
            adresse = stand.adresse,
            onFertig = model::einfuehrungFertig,
            modifier = modifier,
        )

        WalletModel.Ansicht.Selbsttest -> SelbsttestScreen(
            activity = activity,
            onZurueck = { model.zeige(WalletModel.Ansicht.Einstellungen) },
            modifier = modifier,
            adresse = stand.adresse,
        )

        // --- Senden ------------------------------------------------------

        // Der Schluessel `ansicht` setzt den Bildschirm zurueck, wenn eine
        // neue Anfrage kommt, waehrend eine alte noch offen ist.
        WalletModel.Ansicht.Signieren -> key(ansicht) {
            SignierenScreen(
                start = Signierstart.Eingabe,
                adresse = stand.adresse ?: "",
                bioEingeschaltet = stand.bioEingeschaltet,
                onSignieren = model::signieren,
                onSignierenBio = ::bioSignieren,
                onZurueck = { model.zeige(WalletModel.Ansicht.Einstellungen) },
                modifier = modifier,
            )
        }

        is WalletModel.Ansicht.Anmelden -> key(ansicht) {
            SignierenScreen(
                start = ansicht.code?.let { Signierstart.Code(it) } ?: Signierstart.Scannen,
                adresse = stand.adresse ?: "",
                bioEingeschaltet = stand.bioEingeschaltet,
                onSignieren = model::signieren,
                onSignierenBio = ::bioSignieren,
                // Aus einem Link gekommen: zum Hauptbildschirm. Aus den
                // Einstellungen: dorthin zurueck.
                onZurueck = {
                    model.zeige(
                        if (ansicht.code != null) WalletModel.Ansicht.Wallet
                        else WalletModel.Ansicht.Einstellungen,
                    )
                },
                modifier = modifier,
            )
        }

        WalletModel.Ansicht.SendenEingabe -> SendenEingabeScreen(
            guthaben = stand.konto?.guthaben,
            fehler = stand.sendeFehler,
            kontakte = stand.kontakte,
            onPruefen = model::sendenPruefen,
            onZurueck = { model.zeige(WalletModel.Ansicht.Wallet) },
            modifier = modifier,
        )

        is WalletModel.Ansicht.SendenPruefen -> SendenPruefenScreen(
            entwurf = ansicht.entwurf,
            guthaben = stand.konto?.guthaben ?: 0L,
            // Nur der Name NEBEN der Adresse; die Adresse selbst kommt wie
            // immer aus dem Entwurf (REGELN.md, Regel 19).
            kontaktName = Kontakte.nameFuer(stand.kontakte, ansicht.entwurf.empfaenger),
            onWeiter = model::sendenBestaetigen,
            onZurueck = model::sendenZurueckZumEntwurf,
            modifier = modifier,
        )

        is WalletModel.Ansicht.SendenPin -> SendenPinScreen(
            entwurf = ansicht.entwurf,
            laeuft = stand.arbeitet,
            fehler = stand.sendeFehler,
            bioEingeschaltet = stand.bioEingeschaltet,
            onPin = model::sendenUnterschreiben,
            onBio = { bioUnterschreiben() },
            onZurueck = model::sendenZurueckZumEntwurf,
            modifier = modifier,
        )

        is WalletModel.Ansicht.SendenFertig -> SendenFertigScreen(
            ergebnis = ansicht.ergebnis,
            entwurf = ansicht.entwurf,
            schritte = model.sendeSchritte(),
            block = model.sendeNetzbild()?.hoehe,
            bestaetigungen = model.sendeNetzbild()?.bestaetigungen ?: 0,
            zeiten = stand.sendeZeiten,
            // Wann der Spiegel zuletzt geantwortet hat - fuer "geprueft vor n s".
            geprueft = stand.bekanntSeit,
            onAktualisieren = model::kontoLaden,
            onFertig = { model.zeige(WalletModel.Ansicht.Wallet) },
            modifier = modifier,
        )
    }
}
