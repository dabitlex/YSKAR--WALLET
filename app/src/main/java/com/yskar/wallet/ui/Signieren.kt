package com.yskar.wallet.ui

import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yskar.wallet.R
import com.yskar.wallet.signatur.Nachricht
import com.yskar.wallet.signatur.Rueckruf
import com.yskar.wallet.ui.WalletModel.Signierantwort
import kotlinx.coroutines.delay
import java.time.ZoneId

/**
 * Anmelden auf einer YSKAR-Seite und Nachrichten signieren.
 *
 * Zwei Eingaenge, ein Weg:
 *
 *   Anmelden   scannen (oder Link) -> ansehen -> PIN oder Finger -> senden
 *              -> angemeldet / nicht angenommen
 *   Signieren  tippen oder einfuegen -> ansehen -> PIN oder Finger
 *              -> Signatur zum Kopieren
 *
 * WAS DIESER BILDSCHIRM NICHT ENTSCHEIDET: was signiert wird. Er zeigt, was
 * `Nachricht` zu einer Eingabe sagt, und reicht beim Bestaetigen die ROHE
 * Eingabe an das Modell. Dort wird unmittelbar vor dem Unterschreiben noch
 * einmal geprueft (`Unterschrift`). Zeigte der Bildschirm etwas anderes, als
 * signiert wird, waere das ein Fehler in `Nachricht`, nicht hier.
 *
 * VOR DER BESTAETIGUNG GEHT NICHTS INS NETZ. Danach, bei einer Anmeldung per
 * Code, genau ein POST an den Rueckruf.
 *
 * Wer bestaetigt, sieht, was er bestaetigt (REGELN.md, Regel 27,
 * sinngemaess): Ueber dem Ziffernfeld steht, wo man sich anmeldet oder dass
 * eine Nachricht signiert wird.
 */

/** Womit der Bildschirm beginnt. */
sealed interface Signierstart {
    /** Eine Nachricht tippen oder einfuegen. */
    data object Eingabe : Signierstart

    /** Einen Anmelde-Code scannen. */
    data object Scannen : Signierstart

    /** Ein Anmelde-Code ist schon da - aus einem `yskar:login`-Link. */
    data class Code(val code: String) : Signierstart
}

/** Wo der Bildschirm steht. Oeffentlich nur, damit der Testrahmen jeden Schritt zeichnen kann. */
sealed interface Signierschritt {
    data class Eingabe(val text: String = "") : Signierschritt
    data object Scannen : Signierschritt

    /** Die Anfrage ansehen. [istCode]: [eingabe] ist ein Anmelde-Code, kein Text. */
    data class Ansehen(val eingabe: String, val istCode: Boolean) : Signierschritt
    data class Bestaetigen(val eingabe: String, val istCode: Boolean) : Signierschritt
    data class Sendet(val domain: String) : Signierschritt
    data class Signiert(val signiert: Nachricht.Signiert) : Signierschritt
    data class Angemeldet(val domain: String) : Signierschritt
    data class NichtAngenommen(val domain: String, val ergebnis: Rueckruf.Ergebnis) : Signierschritt
    data class Abgelehnt(
        val grund: Nachricht.Ablehnung,
        val istCode: Boolean,
        val domain: String? = null,
        val rueckrufHost: String? = null,
    ) : Signierschritt
    data class Fehler(val meldung: String) : Signierschritt
}

@Composable
fun SignierenScreen(
    start: Signierstart,
    adresse: String,
    bioEingeschaltet: Boolean,
    onSignieren: (
        eingabe: String,
        istCode: Boolean,
        pin: CharArray,
        vorDemSenden: () -> Unit,
        fertig: (Signierantwort) -> Unit,
    ) -> Unit,
    onSignierenBio: (
        eingabe: String,
        istCode: Boolean,
        vorDemSenden: () -> Unit,
        fertig: (Signierantwort) -> Unit,
        abgebrochen: () -> Unit,
    ) -> Unit,
    onZurueck: () -> Unit,
    modifier: Modifier = Modifier,
    // Nur fuer den Testrahmen: direkt in einem Schritt beginnen, mit fester Uhr.
    anfang: Signierschritt? = null,
    uhrFest: Long? = null,
) {
    val t = LocalTexte.current
    var schritt by remember {
        mutableStateOf(
            anfang ?: when (start) {
                Signierstart.Eingabe -> Signierschritt.Eingabe()
                Signierstart.Scannen -> Signierschritt.Scannen
                is Signierstart.Code -> Signierschritt.Ansehen(start.code, istCode = true)
            },
        )
    }
    var pinFehler by remember { mutableStateOf<String?>(null) }
    var laeuft by remember { mutableStateOf(false) }

    // Die Uhr laeuft nur, solange eine Anfrage angesehen wird: Dort zaehlt die
    // Ablaufzeit herunter.
    val tickt = uhrFest == null && schritt is Signierschritt.Ansehen
    val uhr by produceState(uhrFest ?: System.currentTimeMillis(), tickt) {
        while (tickt) {
            value = System.currentTimeMillis()
            delay(1_000)
        }
    }

    /** Was das Modell geantwortet hat, als Schritt. */
    fun zeige(antwort: Signierantwort, eingabe: String, istCode: Boolean) {
        laeuft = false
        when (antwort) {
            is Signierantwort.PinFalsch -> {
                pinFehler = antwort.meldung
                schritt = Signierschritt.Bestaetigen(eingabe, istCode)
            }

            is Signierantwort.Abgelehnt -> schritt = Signierschritt.Abgelehnt(
                antwort.ergebnis.grund, istCode, antwort.ergebnis.domain, antwort.ergebnis.rueckrufHost,
            )

            is Signierantwort.Fehler -> schritt = Signierschritt.Fehler(antwort.meldung)

            is Signierantwort.Fertig -> {
                val gesendet = antwort.gesendet
                val domain = antwort.ergebnis.anmeldung?.domain.orEmpty()
                schritt = when (gesendet) {
                    null -> Signierschritt.Signiert(antwort.ergebnis.signiert)
                    Rueckruf.Ergebnis.Ok -> Signierschritt.Angemeldet(domain)
                    else -> Signierschritt.NichtAngenommen(domain, gesendet)
                }
            }
        }
    }

    val zurueck: () -> Unit = {
        val s = schritt
        when {
            laeuft || s is Signierschritt.Sendet -> Unit
            s is Signierschritt.Bestaetigen -> {
                pinFehler = null
                schritt = Signierschritt.Ansehen(s.eingabe, s.istCode)
            }

            s is Signierschritt.Ansehen && !s.istCode -> {
                // Zurueck zum Feld, mit dem Text, der dort stand - es sei denn,
                // die Wallet hat ihn gar nicht selbst entgegengenommen.
                if (start == Signierstart.Eingabe) schritt = Signierschritt.Eingabe(s.eingabe) else onZurueck()
            }

            else -> onZurueck()
        }
    }
    // Der Scanner hat seine eigene Zurueck-Taste.
    BackHandler(enabled = schritt !is Signierschritt.Scannen, onBack = zurueck)

    when (val s = schritt) {
        is Signierschritt.Eingabe -> Eingabeseite(
            anfangstext = s.text,
            onWeiter = { schritt = Signierschritt.Ansehen(it, istCode = false) },
            onZurueck = zurueck,
            modifier = modifier,
        )

        Signierschritt.Scannen -> ScannerScreen(
            onGefunden = { schritt = Signierschritt.Ansehen(it, istCode = true) },
            onAbbrechen = onZurueck,
            modifier = modifier,
            lesen = ::anmeldecodeAus,
            titel = t.signatur.scannerTitel,
            anleitung = t.signatur.eintragQrHinweis,
            keinTreffer = t.signatur.scannerKeinCode,
        )

        is Signierschritt.Ansehen -> Ansehseite(
            eingabe = s.eingabe,
            istCode = s.istCode,
            adresse = adresse,
            jetzt = uhr,
            bioEingeschaltet = bioEingeschaltet,
            onBestaetigen = {
                pinFehler = null
                schritt = Signierschritt.Bestaetigen(s.eingabe, s.istCode)
            },
            onZurueck = zurueck,
            modifier = modifier,
        )

        is Signierschritt.Bestaetigen -> Bestaetigseite(
            eingabe = s.eingabe,
            istCode = s.istCode,
            adresse = adresse,
            jetzt = uhr,
            fehler = pinFehler,
            laeuft = laeuft,
            bioEingeschaltet = bioEingeschaltet,
            onPin = { pin ->
                laeuft = true
                pinFehler = null
                onSignieren(
                    s.eingabe, s.istCode, pin,
                    { schritt = Signierschritt.Sendet(domainAus(s.eingabe, s.istCode, adresse, uhr)) },
                    { zeige(it, s.eingabe, s.istCode) },
                )
            },
            onBio = {
                laeuft = true
                pinFehler = null
                onSignierenBio(
                    s.eingabe, s.istCode,
                    { schritt = Signierschritt.Sendet(domainAus(s.eingabe, s.istCode, adresse, uhr)) },
                    { zeige(it, s.eingabe, s.istCode) },
                    { laeuft = false },
                )
            },
            onZurueck = zurueck,
            modifier = modifier,
        )

        is Signierschritt.Sendet -> Meldeseite(
            symbol = R.drawable.ic_globe_bold,
            titel = t.signatur.sendet(s.domain),
            modifier = modifier,
        ) {
            Hinweisstreifen(t.signatur.sendetHinweis)
        }

        is Signierschritt.Angemeldet -> Meldeseite(
            symbol = R.drawable.ic_check_bold,
            titel = t.signatur.angemeldet,
            text = t.signatur.angemeldetText(s.domain),
            ton = Farbe.Eingang,
            knopf = t.signatur.fertig,
            onKnopf = onZurueck,
            hauptknopf = true,
            modifier = modifier,
        )

        is Signierschritt.NichtAngenommen -> Meldeseite(
            symbol = R.drawable.ic_warning_fill,
            titel = t.signatur.nichtAngenommen(s.domain),
            ton = Farbe.Warnung,
            knopf = t.signatur.schliessen,
            onKnopf = onZurueck,
            modifier = modifier,
        ) {
            Warnhinweis(
                when (val e = s.ergebnis) {
                    Rueckruf.Ergebnis.Netz -> t.signatur.netz
                    is Rueckruf.Ergebnis.Weiterleitung -> t.signatur.weiterleitung
                    is Rueckruf.Ergebnis.Abgelehnt ->
                        // Der Text kommt von der Seite, also aus dem Netz: als
                        // Zitat gekennzeichnet, gekuerzt und ohne Steuerzeichen
                        // (`Rueckruf.antwortLesen`).
                        if (e.text != null) "${t.signatur.vonSeite} ${t.signatur.zitat(e.text)}"
                        else t.signatur.keinText

                    Rueckruf.Ergebnis.Ok -> t.signatur.angemeldet
                },
            )
            Hinweisstreifen(t.signatur.neuerCode)
        }

        is Signierschritt.Abgelehnt -> Meldeseite(
            symbol = R.drawable.ic_warning_fill,
            titel = if (s.istCode) t.signatur.qrAbgelehnt else t.signatur.abgelehntTitel,
            ton = Farbe.Warnung,
            knopf = t.signatur.schliessen,
            onKnopf = onZurueck,
            modifier = modifier,
        ) {
            Warnhinweis("${t.signatur.grund(s.grund)} ${t.signatur.nichtsSigniert}")
            if (s.domain != null && s.rueckrufHost != null) {
                Flaeche(abstand = 0.dp) {
                    Wertzeile(t.signatur.seite, s.domain, festbreite = true)
                    Wertzeile(t.signatur.antwortAn, s.rueckrufHost, festbreite = true, linie = false)
                }
            }
        }

        is Signierschritt.Fehler -> Meldeseite(
            symbol = R.drawable.ic_warning_fill,
            titel = t.signatur.abgelehntTitel,
            ton = Farbe.Warnung,
            knopf = t.signatur.schliessen,
            onKnopf = onZurueck,
            modifier = modifier,
        ) {
            Warnhinweis(s.meldung)
        }

        is Signierschritt.Signiert -> Ergebnisseite(s.signiert, onFertig = onZurueck, modifier = modifier)
    }
}

/** Die Domain einer Anmeldung, zum Anzeigen waehrend des Sendens. Leer, wenn keine. */
private fun domainAus(eingabe: String, istCode: Boolean, adresse: String, jetzt: Long): String =
    if (istCode) {
        (Nachricht.anmeldungAusCode(eingabe, adresse, jetzt) as? Nachricht.QrPruefung.Ok)
            ?.anmeldung?.domain.orEmpty()
    } else {
        (Nachricht.vorbereiten(eingabe, adresse, jetzt) as? Nachricht.Pruefung.Login)
            ?.anmeldung?.domain.orEmpty()
    }

// ------------------------------------------------------------------- Rahmen

/** Kopf fest, Mitte rollt, Fuss fest - wie auf den Sendebildschirmen. */
@Composable
private fun Signierseite(
    titel: String,
    onZurueck: () -> Unit,
    modifier: Modifier = Modifier,
    fuss: @Composable ColumnScope.() -> Unit = {},
    inhalt: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.fillMaxSize()) {
        Kopfzeile(titel, onZurueck = onZurueck)
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, top = 2.dp, end = 16.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = inhalt,
        )
        Column(
            Modifier.padding(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 22.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = fuss,
        )
    }
}

/** Das Symbol im getoenten Kaestchen ueber einer Meldung. */
@Composable
private fun Kachel(@DrawableRes symbol: Int, ton: Color, kante: Dp = 64.dp) {
    Box(
        Modifier
            .size(kante)
            .gefasst(
                rundung = kante * 0.3125f,
                grund = SolidColor(ton.copy(alpha = 0.12f)),
                rand = ton.copy(alpha = 0.30f),
                glanz = 0f,
            ),
        contentAlignment = Alignment.Center,
    ) { Symbol(symbol, kante * 0.4375f, farbe = if (ton == YskarFarben.Blau) Farbe.Schrift else ton) }
}

/** Eine Seite, die EINES sagt: wird gesendet, angemeldet, abgelehnt. */
@Composable
private fun Meldeseite(
    @DrawableRes symbol: Int,
    titel: String,
    modifier: Modifier = Modifier,
    text: String? = null,
    ton: Color = YskarFarben.Blau,
    knopf: String? = null,
    onKnopf: () -> Unit = {},
    hauptknopf: Boolean = false,
    mehr: @Composable ColumnScope.() -> Unit = {},
) {
    Column(modifier.fillMaxSize()) {
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, top = 56.dp, end = 16.dp, bottom = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Kachel(symbol, ton)
            Text(
                titel,
                style = Schrift.Mittel,
                color = Farbe.Schrift,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(start = 12.dp, top = 6.dp, end = 12.dp),
            )
            if (text != null) {
                Text(
                    text,
                    style = Schrift.Text,
                    color = Farbe.Weich,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
            }
            Spacer(Modifier.height(4.dp))
            mehr()
        }
        if (knopf != null) {
            Knopf(
                knopf,
                onKnopf,
                Modifier
                    .padding(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 22.dp)
                    .fillMaxWidth()
                    .testTag("signieren_schliessen"),
                art = if (hauptknopf) Knopfart.HAUPT else Knopfart.FLAECHE,
            )
        }
    }
}

/** Die Nachricht, Zeichen fuer Zeichen so, wie sie signiert wird. */
@Composable
private fun GenaueNachricht(text: String) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            LocalTexte.current.signatur.genau,
            style = Schrift.Klein,
            color = Farbe.Leise,
            modifier = Modifier.padding(start = 4.dp),
        )
        Box(
            Modifier
                .fillMaxWidth()
                .gefasst(16.dp, SolidColor(Color.Black.copy(alpha = 0.30f)), Farbe.Linie, 0f)
                .padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            // Festbreite, damit Zeilen und Leerzeichen zu sehen sind.
            Text(
                text,
                style = Schrift.Winzig.copy(fontSize = 12.sp, lineHeight = 17.sp),
                color = Farbe.Weich,
                modifier = Modifier.testTag("genaue_nachricht"),
            )
        }
    }
}

// ------------------------------------------------------------------ Eingabe

@Composable
private fun Eingabeseite(
    anfangstext: String,
    onWeiter: (String) -> Unit,
    onZurueck: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = LocalTexte.current
    val ctx = LocalContext.current
    var text by remember { mutableStateOf(anfangstext) }

    Signierseite(
        t.signatur.eintragSignieren,
        onZurueck,
        modifier,
        fuss = {
            Knopf(
                t.signatur.weiter,
                { onWeiter(text) },
                Modifier.fillMaxWidth().testTag("signieren_weiter"),
                aktiv = text.isNotBlank(),
            )
        },
    ) {
        Text(
            t.signatur.einleitung,
            style = Schrift.Text,
            color = Farbe.Weich,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        Flaeche(
            rundung = 22.dp,
            innen = PaddingValues(start = 16.dp, top = 6.dp, end = 6.dp, bottom = 14.dp),
            abstand = 2.dp,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    t.signatur.nachricht,
                    style = Schrift.Klein,
                    color = Farbe.Leise,
                    modifier = Modifier.weight(1f),
                )
                Symbolknopf(
                    R.drawable.ic_clipboard_text_fill, t.signatur.einfuegen,
                    { text = ausZwischenablage(ctx) },
                    betont = true,
                )
            }
            Eingabe(
                wert = text,
                onWert = { text = it },
                platzhalter = t.signatur.platzhalter,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 120.dp)
                    .padding(end = 10.dp)
                    .testTag("signieren_text"),
                einzeilig = false,
                tastatur = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            )
        }
        Text(
            t.signatur.kopfHinweis,
            style = Schrift.Hinweis,
            color = Farbe.Leise,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
    }
}

// ------------------------------------------------------------------ Ansehen

@Composable
private fun Ansehseite(
    eingabe: String,
    istCode: Boolean,
    adresse: String,
    jetzt: Long,
    bioEingeschaltet: Boolean,
    onBestaetigen: () -> Unit,
    onZurueck: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = LocalTexte.current

    // Was `Nachricht` zu dieser Eingabe sagt - jede Sekunde neu, weil eine
    // Anmeldung ablaeuft.
    var nachricht: String? = null
    var anmeldung: Nachricht.Anmeldung? = null
    var rueckruf: String? = null
    var ergaenzt = false
    var abgelehnt: Signierschritt.Abgelehnt? = null

    if (istCode) {
        when (val p = Nachricht.anmeldungAusCode(eingabe, adresse, jetzt)) {
            is Nachricht.QrPruefung.Ok -> {
                nachricht = p.nachricht; anmeldung = p.anmeldung; rueckruf = p.rueckruf
            }

            is Nachricht.QrPruefung.Abgelehnt ->
                abgelehnt = Signierschritt.Abgelehnt(p.grund, true, p.domain, p.rueckrufHost)
        }
    } else {
        when (val p = Nachricht.vorbereiten(eingabe, adresse, jetzt)) {
            is Nachricht.Pruefung.Login -> {
                nachricht = p.nachricht; anmeldung = p.anmeldung
            }

            is Nachricht.Pruefung.Text -> {
                nachricht = p.nachricht; ergaenzt = p.ergaenzt
            }

            is Nachricht.Pruefung.Abgelehnt -> abgelehnt = Signierschritt.Abgelehnt(p.grund, false)
        }
    }

    if (abgelehnt != null || nachricht == null) {
        val a = abgelehnt ?: Signierschritt.Abgelehnt(Nachricht.Ablehnung.AUFBAU, istCode)
        Meldeseite(
            symbol = R.drawable.ic_warning_fill,
            titel = if (a.istCode) t.signatur.qrAbgelehnt else t.signatur.abgelehntTitel,
            ton = Farbe.Warnung,
            knopf = t.signatur.schliessen,
            onKnopf = onZurueck,
            modifier = modifier,
        ) {
            Warnhinweis("${t.signatur.grund(a.grund)} ${t.signatur.nichtsSigniert}")
            if (a.domain != null && a.rueckrufHost != null) {
                Flaeche(abstand = 0.dp) {
                    Wertzeile(t.signatur.seite, a.domain, festbreite = true)
                    Wertzeile(t.signatur.antwortAn, a.rueckrufHost, festbreite = true, linie = false)
                }
            }
        }
        return
    }

    // Ab hier steht fest, was gezeigt wird.
    val wortlaut: String = nachricht
    val login: Nachricht.Anmeldung? = anmeldung
    val ziel: String? = rueckruf
    val wurdeErgaenzt = ergaenzt
    val danach = if (bioEingeschaltet) t.signatur.danach else t.signatur.danachPin

    if (login != null) {
        Signierseite(
            if (istCode) t.signatur.qrTitel else t.signatur.anmeldungTitel,
            onZurueck,
            modifier,
            fuss = {
                Knopf(
                    t.signatur.anmelden,
                    onBestaetigen,
                    Modifier.fillMaxWidth().testTag("signieren_bestaetigen"),
                    symbol = R.drawable.ic_lock_key_fill,
                )
                Text(
                    danach,
                    style = Schrift.Hinweis.copy(fontSize = 12.sp),
                    color = Farbe.Leise,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
        ) {
            // Kleiner als auf den Meldeseiten: Hier steht darunter viel, und
            // nichts davon soll hinter dem Knopf verschwinden (am Geraet
            // gemessen, 11.11.2026: mit 64 dp schnitt der Fuss den Hinweis an).
            Column(
                Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Kachel(R.drawable.ic_globe_bold, YskarFarben.Blau, kante = 48.dp)
                Text(
                    t.signatur.anmeldenBei,
                    style = Schrift.Klein,
                    color = Farbe.Leise,
                    modifier = Modifier.padding(top = 2.dp),
                )
                // Die Domain ist die Hauptsache dieses Bildschirms: Wo meldest du
                // dich an? Gross, und in Festbreite, damit l und I nicht gleich
                // aussehen.
                Text(
                    login.domain,
                    style = Schrift.Adresse.copy(fontSize = 22.sp, lineHeight = 28.sp),
                    color = Farbe.Schrift,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.testTag("anmelde_domain"),
                )
            }
            Flaeche(abstand = 0.dp) {
                Column(
                    Modifier.padding(top = 4.dp, bottom = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            t.signatur.adresse,
                            style = Schrift.Name,
                            color = Farbe.Leise,
                            modifier = Modifier.weight(1f),
                        )
                        Symbol(R.drawable.ic_check_bold, 12.dp, farbe = Farbe.Eingang)
                        Text(
                            t.signatur.dieseWallet,
                            style = Schrift.Hinweis,
                            color = Farbe.Eingang,
                            modifier = Modifier.padding(start = 6.dp),
                        )
                    }
                    AdresseInGruppen(login.address, Modifier.fillMaxWidth(), einpassen = true)
                }
                Wertzeile(
                    t.signatur.laeuftAb,
                    ablaufText(login.expires.toEpochMilli(), jetzt, ZoneId.systemDefault(), t),
                    linie = ziel != null,
                )
                if (ziel != null) {
                    Wertzeile(
                        t.signatur.antwortAn,
                        Nachricht.rueckrufZiel(ziel) ?: ziel,
                        festbreite = true,
                        linie = false,
                    )
                }
            }
            GenaueNachricht(wortlaut)
            Hinweisstreifen(
                t.signatur.anmeldeHinweis,
                vorne = { Symbol(R.drawable.ic_shield_check_fill, 20.dp, farbe = Farbe.BlauSchrift) },
            )
        }
    } else {
        Signierseite(
            t.signatur.textTitel,
            onZurueck,
            modifier,
            fuss = {
                Knopf(
                    t.signatur.signierenKnopf,
                    onBestaetigen,
                    Modifier.fillMaxWidth().testTag("signieren_bestaetigen"),
                    symbol = R.drawable.ic_lock_key_fill,
                )
                Text(
                    danach,
                    style = Schrift.Hinweis.copy(fontSize = 12.sp),
                    color = Farbe.Leise,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
        ) {
            Text(
                t.signatur.duSignierst,
                style = Schrift.Titel,
                color = Farbe.Schrift,
                modifier = Modifier.padding(start = 4.dp, top = 4.dp),
            )
            GenaueNachricht(wortlaut)
            if (wurdeErgaenzt) {
                Text(
                    t.signatur.ergaenztHinweis,
                    style = Schrift.Hinweis,
                    color = Farbe.Leise,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
            Flaeche {
                Text(t.signatur.signiertMit, style = Schrift.Klein, color = Farbe.Leise)
                AdresseInGruppen(adresse, Modifier.fillMaxWidth(), einpassen = true)
            }
        }
    }
}

// -------------------------------------------------------------- Bestaetigen

/**
 * PIN oder Finger. Gebaut wie "PIN festlegen": das Zeichen, das sich mit den
 * Ziffern fuellt, darunter WAS bestaetigt wird, dann das eigene Ziffernfeld
 * (REGELN.md, Regel 15).
 */
@Composable
private fun Bestaetigseite(
    eingabe: String,
    istCode: Boolean,
    adresse: String,
    jetzt: Long,
    fehler: String?,
    laeuft: Boolean,
    bioEingeschaltet: Boolean,
    onPin: (CharArray) -> Unit,
    onBio: () -> Unit,
    onZurueck: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val texte = LocalTexte.current
    val t = texte.signatur
    val laenge = 6
    var ziffern by remember { mutableStateOf("") }
    // Die Meldung nach einer falschen PIN steht da, bis wieder getippt wird.
    val meldung = fehler.takeIf { ziffern.isEmpty() && !laeuft }

    val domain = remember(eingabe, istCode) { domainAus(eingabe, istCode, adresse, jetzt) }
    val worum = if (domain.isNotEmpty()) "${t.anmeldenBei} $domain" else t.pinText

    val baender = baenderFuerPin(
        ziffern = ziffern.length,
        falsch = meldung != null,
        geprueft = laeuft,
    )

    Box(modifier.fillMaxSize()) {
        Musterhimmel(hoehe = 330.dp, deckkraft = 0.07f)

        Blatt(mittig = true) {
            Kopfzeile(
                titel = "",
                onZurueck = onZurueck,
                zurueckSymbol = R.drawable.ic_x_bold,
                zurueckText = texte.allgemein.abbrechen,
                zurueckAktiv = !laeuft,
            )

            Spacer(Modifier.weight(0.55f))

            Zeichen(
                links = baender[0],
                rechts = baender[1],
                stamm = baender[2],
                groesse = 96.dp,
                beschreibung = zifferStand(ziffern.length, laenge, texte),
            )
            Text(
                t.pinTitel,
                style = Schrift.Gross,
                color = Farbe.Schrift,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(start = 24.dp, top = 18.dp, end = 24.dp),
            )
            Text(
                meldung ?: if (laeuft) texte.allgemein.einenMoment else worum,
                style = Schrift.Name,
                color = if (meldung != null) Farbe.Warnung else Farbe.Leise,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .padding(start = 28.dp, top = 6.dp, end = 28.dp)
                    .testTag("signieren_worum"),
            )

            Spacer(Modifier.height(30.dp))

            Ziffernfeld(
                laenge = laenge,
                ziffern = ziffern,
                aktiv = !laeuft,
                onFinger = if (bioEingeschaltet) onBio else null,
                fingerAktiv = !laeuft,
                onZiffern = { neu ->
                    ziffern = neu
                    if (neu.length == laenge) {
                        ziffern = ""
                        onPin(neu.toCharArray())
                    }
                },
            )

            Spacer(Modifier.height(16.dp))
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.height(26.dp))
        }
    }
}

// ----------------------------------------------------------------- Ergebnis

/** Die fertige Signatur, zum Kopieren. */
@Composable
private fun Ergebnisseite(
    s: Nachricht.Signiert,
    onFertig: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = LocalTexte.current
    val ctx = LocalContext.current
    var kopiert by remember { mutableStateOf(false) }

    Signierseite(
        t.signatur.ergebnisTitel,
        onFertig,
        modifier,
        fuss = {
            Knopf(
                if (kopiert) t.signatur.kopiert else t.signatur.allesKopieren,
                {
                    inZwischenablage(ctx, Nachricht.alsJson(s))
                    kopiert = true
                },
                Modifier.fillMaxWidth().testTag("signatur_kopieren"),
                symbol = if (kopiert) R.drawable.ic_check_bold else R.drawable.ic_copy_fill,
            )
            Knopf(t.signatur.fertig, onFertig, Modifier.fillMaxWidth(), art = Knopfart.FLAECHE)
        },
    ) {
        Row(
            Modifier.padding(start = 4.dp, top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Symbol(R.drawable.ic_check_bold, 16.dp, farbe = Farbe.Eingang)
            Text(t.signatur.signiert, style = Schrift.Titel, color = Farbe.Schrift)
        }
        // Was jetzt zu tun ist, steht oben - nicht am Ende, wo es als Erstes
        // aus dem Bild rutscht (am Geraet gesehen, 11.11.2026).
        Text(
            t.signatur.signaturHinweis,
            style = Schrift.Hinweis,
            color = Farbe.Leise,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        // Adresse und Schluessel gehoeren zusammen: wer signiert hat.
        Flaeche {
            Text(t.signatur.signiertMit, style = Schrift.Klein, color = Farbe.Leise)
            AdresseInGruppen(s.address, Modifier.fillMaxWidth(), einpassen = true)
            Box(Modifier.fillMaxWidth().height(1.dp).background(Farbe.Linie))
            Text(t.signatur.schluessel, style = Schrift.Klein, color = Farbe.Leise)
            KennungInGruppen(s.publicKey, Modifier.fillMaxWidth())
        }
        Flaeche {
            Text(t.signatur.signatur, style = Schrift.Klein, color = Farbe.Leise)
            // 128 Hex-Zeichen: zwei Kennungen lang.
            KennungInGruppen(s.signature.take(64), Modifier.fillMaxWidth())
            KennungInGruppen(s.signature.drop(64), Modifier.fillMaxWidth())
        }
        GenaueNachricht(s.message)
    }
}
