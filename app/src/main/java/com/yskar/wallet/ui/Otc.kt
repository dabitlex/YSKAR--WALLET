package com.yskar.wallet.ui

import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yskar.wallet.R
import com.yskar.wallet.kern.Hex
import com.yskar.wallet.netz.Betrag
import com.yskar.wallet.otc.Auftrag
import com.yskar.wallet.otc.Tauschbuch
import com.yskar.wallet.signatur.Nachricht
import com.yskar.wallet.signatur.Rueckruf
import com.yskar.wallet.ui.WalletModel.OtcAntwort
import com.yskar.wallet.ui.WalletModel.OtcVorbereitung
import java.time.ZoneId

/**
 * OTC-Tausch mit otc.yskar.app (Wallet 3.1, REGELN.md, Regel 43).
 *
 *   scannen (oder Link) -> ansehen (Knoten wird gefragt) -> PIN oder Finger
 *   -> senden -> Ergebnis
 *
 * Bei `sperren` fuehrt "Weiter" in den gewohnten Pruefschritt einer
 * Ueberweisung an die Sperradresse; alles Weitere ist der Sendeweg.
 *
 * WAS DIESER BILDSCHIRM NICHT ENTSCHEIDET: was unterschrieben wird. Er zeigt,
 * was `Auftrag` und der Knoten sagen; der Entwurf, den er zeigt, geht
 * unveraendert ins Unterschreiben (Regel 19), und der Bildschirm zum
 * Unterschreiben zeigt seine Zahlen (Regel 27).
 */

/** Wo der Bildschirm steht. */
sealed interface OtcSchritt {
    data object Scannen : OtcSchritt
    data class Ansehen(val code: String) : OtcSchritt

    /** [bau] null: hash (nichts wird ausgegeben, nur h unterschrieben). */
    class Bestaetigen(val code: String, val auftrag: Auftrag.Gelesen, val bau: Auftrag.Bau.Ok?) : OtcSchritt
    data object Sendet : OtcSchritt
    data class Ergebnis(val antwort: OtcAntwort) : OtcSchritt
}

@Composable
fun OtcScreen(
    code: String?,
    adresse: String,
    bioEingeschaltet: Boolean,
    onLesen: (String) -> Auftrag.Lesung,
    onVorbereiten: (Auftrag.Gelesen, (OtcVorbereitung) -> Unit) -> Unit,
    onSperrenWeiter: (Auftrag.Gelesen.Sperren) -> String?,
    onHash: (String, CharArray, () -> Unit, (OtcAntwort) -> Unit) -> Unit,
    onHashBio: (String, () -> Unit, (OtcAntwort) -> Unit, () -> Unit) -> Unit,
    onAusgeben: (Auftrag.Gelesen, Auftrag.Bau.Ok, CharArray, () -> Unit, (OtcAntwort) -> Unit) -> Unit,
    onAusgebenBio: (Auftrag.Gelesen, Auftrag.Bau.Ok, () -> Unit, (OtcAntwort) -> Unit, () -> Unit) -> Unit,
    onOffeneTausche: () -> Unit,
    onZurueck: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = LocalTexte.current
    var schritt by remember {
        mutableStateOf<OtcSchritt>(if (code != null) OtcSchritt.Ansehen(code) else OtcSchritt.Scannen)
    }
    var pinFehler by remember { mutableStateOf<String?>(null) }
    var laeuft by remember { mutableStateOf(false) }

    fun zeige(antwort: OtcAntwort) {
        laeuft = false
        // Eine falsche PIN kommt, bevor etwas gesendet wird: Das Ziffernfeld
        // bleibt stehen, mit der Meldung darueber.
        if (antwort is OtcAntwort.PinFalsch) {
            pinFehler = antwort.meldung
            return
        }
        schritt = OtcSchritt.Ergebnis(antwort)
    }

    val zurueck: () -> Unit = {
        val s = schritt
        when {
            laeuft || s is OtcSchritt.Sendet -> Unit
            s is OtcSchritt.Bestaetigen -> {
                pinFehler = null
                schritt = OtcSchritt.Ansehen(s.code)
            }
            else -> onZurueck()
        }
    }
    // Der Scanner hat seine eigene Zurueck-Taste.
    BackHandler(enabled = schritt !is OtcSchritt.Scannen, onBack = zurueck)

    when (val s = schritt) {
        OtcSchritt.Scannen -> ScannerScreen(
            onGefunden = { schritt = OtcSchritt.Ansehen(it) },
            onAbbrechen = onZurueck,
            modifier = modifier,
            lesen = { roh -> roh.trim().takeIf { it.length <= 4_000 && Auftrag.istOtcCode(it) } },
            titel = t.otc.scannerTitel,
            anleitung = t.otc.scannerAnleitung,
            keinTreffer = t.otc.scannerKeinCode,
        )

        is OtcSchritt.Ansehen -> Ansehen(
            code = s.code,
            adresse = adresse,
            bioEingeschaltet = bioEingeschaltet,
            onLesen = onLesen,
            onVorbereiten = onVorbereiten,
            onSperrenWeiter = onSperrenWeiter,
            onBestaetigen = { auftrag, bau ->
                pinFehler = null
                schritt = OtcSchritt.Bestaetigen(s.code, auftrag, bau)
            },
            onZurueck = zurueck,
            modifier = modifier,
        )

        is OtcSchritt.Bestaetigen -> {
            val bau = s.bau
            val worum = when (s.auftrag) {
                is Auftrag.Gelesen.Einloesen -> t.otc.worumEinloesen
                is Auftrag.Gelesen.Zurueck -> t.otc.worumZurueck
                else -> t.otc.worumHash
            }
            // Regel 27: Was unterschrieben wird, steht neben dem Ziffernfeld.
            val zahlen = if (bau == null) {
                val h = s.auftrag as? Auftrag.Gelesen.Hash
                listOfNotNull(h?.let { t.otc.tausch to Hex.toHex(it.kennung).take(16) + "…" })
            } else {
                listOf(
                    t.otc.anDich to (Betrag.genau(bau.entwurf.betrag, t.locale) + " YSR"),
                    t.otc.gebuehr to (Betrag.genau(bau.entwurf.gebuehr, t.locale) + " YSR"),
                    t.otc.vonSkript to adresseKnapp(sperreVon(s.auftrag)?.skriptAdresse.orEmpty()),
                )
            }
            Pinseite(
                worum = worum,
                zahlen = zahlen,
                fehler = pinFehler,
                laeuft = laeuft,
                bioEingeschaltet = bioEingeschaltet,
                onPin = { pin ->
                    laeuft = true
                    pinFehler = null
                    if (bau == null) onHash(s.code, pin, { schritt = OtcSchritt.Sendet }, ::zeige)
                    else onAusgeben(s.auftrag, bau, pin, { schritt = OtcSchritt.Sendet }, ::zeige)
                },
                onBio = {
                    laeuft = true
                    pinFehler = null
                    if (bau == null) {
                        onHashBio(s.code, { schritt = OtcSchritt.Sendet }, ::zeige) { laeuft = false }
                    } else {
                        onAusgebenBio(s.auftrag, bau, { schritt = OtcSchritt.Sendet }, ::zeige) { laeuft = false }
                    }
                },
                onZurueck = zurueck,
                modifier = modifier,
            )
        }

        OtcSchritt.Sendet -> Meldung(R.drawable.ic_globe_bold, t.otc.sendet, modifier = modifier)

        is OtcSchritt.Ergebnis -> Ergebnisseite(s.antwort, onOffeneTausche, onZurueck, modifier)
    }
}

private fun sperreVon(a: Auftrag.Gelesen): Auftrag.Sperre? = when (a) {
    is Auftrag.Gelesen.Sperren -> a.sperre
    is Auftrag.Gelesen.Einloesen -> a.sperre
    is Auftrag.Gelesen.Zurueck -> a.sperre
    is Auftrag.Gelesen.Hash -> null
}

// ------------------------------------------------------------------ Ansehen

@Composable
private fun Ansehen(
    code: String,
    adresse: String,
    bioEingeschaltet: Boolean,
    onLesen: (String) -> Auftrag.Lesung,
    onVorbereiten: (Auftrag.Gelesen, (OtcVorbereitung) -> Unit) -> Unit,
    onSperrenWeiter: (Auftrag.Gelesen.Sperren) -> String?,
    onBestaetigen: (Auftrag.Gelesen, Auftrag.Bau.Ok?) -> Unit,
    onZurueck: () -> Unit,
    modifier: Modifier,
) {
    val t = LocalTexte.current
    val lesung = remember(code) { onLesen(code) }
    val danach = if (bioEingeschaltet) t.signatur.danach else t.signatur.danachPin

    if (lesung is Auftrag.Lesung.Abgelehnt) {
        Meldung(
            R.drawable.ic_warning_fill, t.otc.abgelehntTitel,
            text = "${t.otc.fehler(lesung.fehler)} ${t.otc.nichtsUnterschrieben}",
            ton = Farbe.Warnung, knopf = t.otc.schliessen, onKnopf = onZurueck, modifier = modifier,
        )
        return
    }
    val auftrag = (lesung as Auftrag.Lesung.Ok).auftrag

    if (auftrag is Auftrag.Gelesen.Hash) {
        HashAnsicht(auftrag, danach, { onBestaetigen(auftrag, null) }, onZurueck, modifier)
        return
    }

    var runde by remember(code) { mutableStateOf(0) }
    var vorbereitung by remember(code) { mutableStateOf<OtcVorbereitung?>(null) }
    LaunchedEffect(code, runde) {
        vorbereitung = null
        onVorbereiten(auftrag) { vorbereitung = it }
    }

    val titel = when (auftrag) {
        is Auftrag.Gelesen.Sperren -> t.otc.titelSperren
        is Auftrag.Gelesen.Einloesen -> t.otc.titelEinloesen
        else -> t.otc.titelZurueck
    }

    when (val v = vorbereitung) {
        null -> Seite(titel, onZurueck, modifier) {
            Text(t.otc.fragtKnoten, style = Schrift.Text, color = Farbe.Leise, modifier = Modifier.padding(4.dp))
            Platzhalter(breite = 220.dp, hoehe = 18.dp)
            Platzhalter(breite = 160.dp, hoehe = 18.dp)
        }

        is OtcVorbereitung.Problem -> Meldung(
            R.drawable.ic_warning_fill, titel, text = v.text, ton = Farbe.Warnung,
            knopf = t.otc.nochmal, onKnopf = { runde++ }, zweiterKnopf = t.otc.schliessen,
            onZweiterKnopf = onZurueck, modifier = modifier,
        )

        is OtcVorbereitung.Abgelehnt -> Meldung(
            R.drawable.ic_warning_fill, titel,
            text = "${t.otc.fehler(v.fehler)} ${t.otc.nichtsUnterschrieben}",
            ton = Farbe.Warnung, knopf = t.otc.nochmal, onKnopf = { runde++ },
            zweiterKnopf = t.otc.schliessen, onZweiterKnopf = onZurueck, modifier = modifier,
        ) {
            val sp = sperreVon(auftrag)
            if (sp != null && v.spitze != null) {
                Flaeche(abstand = 0.dp) {
                    if (auftrag is Auftrag.Gelesen.Einloesen && v.bestaetigungen != null) {
                        Wertzeile(t.otc.bestaetigungen, t.otc.bestaetigungenVon(v.bestaetigungen, auftrag.n))
                    }
                    Wertzeile(
                        t.otc.einloesenBis,
                        t.otc.blockIn(sp.teile.einloeseSchluss, sp.teile.einloeseSchluss - v.spitze),
                    )
                    Wertzeile(t.otc.zurueckAb, t.otc.blockIn(sp.teile.frist, sp.teile.frist - v.spitze), linie = false)
                }
            }
        }

        is OtcVorbereitung.Sperre -> SperrenAnsicht(
            auftrag as Auftrag.Gelesen.Sperren, v.spitze, onSperrenWeiter, onZurueck, modifier,
        )

        is OtcVorbereitung.Ausgabe -> AusgabeAnsicht(
            auftrag, v.bau, v.spitze, v.bestaetigungen, adresse, danach,
            { onBestaetigen(auftrag, v.bau) }, onZurueck, modifier,
        )
    }
}

@Composable
private fun Hauptbetrag(name: String, einheiten: Long) {
    val t = LocalTexte.current
    Column(Modifier.padding(start = 4.dp, top = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(name, style = Schrift.Klein, color = Farbe.Leise)
        Text(
            Betrag.genau(einheiten, t.locale) + " YSR",
            style = Schrift.Gross,
            color = Farbe.Schrift,
            modifier = Modifier.testTag("otc_betrag"),
        )
    }
}

@Composable
private fun AdressFeld(name: String, adresse: String) {
    Flaeche {
        Text(name, style = Schrift.Klein, color = Farbe.Leise)
        AdresseInGruppen(adresse, Modifier.fillMaxWidth(), einpassen = true)
    }
}

@Composable
private fun HashAnsicht(
    a: Auftrag.Gelesen.Hash,
    danach: String,
    onBestaetigen: () -> Unit,
    onZurueck: () -> Unit,
    modifier: Modifier,
) {
    val t = LocalTexte.current
    Seite(
        t.otc.titelHash, onZurueck, modifier,
        fuss = { Hauptknopf(t.otc.knopfHash, danach, onBestaetigen) },
    ) {
        Hauptbetrag(t.otc.duBekommst, a.ysr)
        Flaeche(abstand = 0.dp) {
            Wertzeile(t.otc.gegenwert, "${a.gegenMenge} ${a.gegen}") // bis 34 Zeichen: nie abschneiden
            Wertzeile(
                t.otc.laeuftAb,
                ablaufText(a.ablauf.toEpochMilli(), System.currentTimeMillis(), ZoneId.systemDefault(), t),
                einpassen = true,
            )
            Wertzeile(t.otc.antwortAn, Nachricht.rueckrufZiel(a.rueckruf) ?: a.rueckruf, festbreite = true, linie = false)
        }
        Flaeche {
            Text(t.otc.tausch, style = Schrift.Klein, color = Farbe.Leise)
            KennungInGruppen(Hex.toHex(a.kennung), Modifier.fillMaxWidth())
        }
        Hinweisstreifen(
            t.otc.hashErklaerung,
            vorne = { Symbol(R.drawable.ic_shield_check_fill, 20.dp, farbe = Farbe.BlauSchrift) },
        )
    }
}

@Composable
private fun SperrenAnsicht(
    a: Auftrag.Gelesen.Sperren,
    spitze: Long,
    onSperrenWeiter: (Auftrag.Gelesen.Sperren) -> String?,
    onZurueck: () -> Unit,
    modifier: Modifier,
) {
    val t = LocalTexte.current
    var fehler by remember(a) { mutableStateOf<String?>(null) }
    val teile = a.sperre.teile
    Seite(
        t.otc.titelSperren, onZurueck, modifier,
        fuss = {
            Knopf(
                t.otc.knopfSperren,
                { fehler = onSperrenWeiter(a) },
                Modifier.fillMaxWidth().testTag("otc_weiter"),
                symbol = R.drawable.ic_lock_key_fill,
            )
        },
    ) {
        // Regel 29: Betrag, Warnungen, Zahlen, Adresse, zuletzt der blaue Hinweis.
        Hauptbetrag(t.otc.duGibst, a.sperre.ysr)
        Warnhinweis(t.otc.sperrenLautSeite)
        fehler?.let { Warnhinweis(it) }
        Flaeche(abstand = 0.dp) {
            Wertzeile(t.otc.gegenwert, "${a.gegenMenge} ${a.gegen}") // bis 34 Zeichen: nie abschneiden
            Wertzeile(
                t.otc.einloesenBis,
                t.otc.blockIn(teile.einloeseSchluss, teile.einloeseSchluss - spitze),
                einpassen = true,
            )
            Wertzeile(t.otc.zurueckAb, t.otc.blockIn(teile.frist, teile.frist - spitze), einpassen = true)
            Wertzeile(t.otc.bestaetigungen, a.n.toString(), einpassen = true)
            // Bis zu 100 Zeichen: OHNE einpassen, damit nichts abgeschnitten
            // wird - passt sie nicht neben die Beschriftung, rueckt sie darunter.
            Wertzeile(t.otc.gegenSperre, a.gegenSperre, festbreite = true, linie = false)
        }
        AdressFeld(t.otc.sperradresse, a.sperre.skriptAdresse)
        // Der blaue Hinweis zugeklappt (Regel 29 und 40): Was man prueft, steht darueber.
        Einzelheiten {
            Hinweisstreifen(
                t.otc.sperrenGeprueft,
                vorne = { Symbol(R.drawable.ic_shield_check_fill, 20.dp, farbe = Farbe.BlauSchrift) },
            )
        }
    }
}

@Composable
private fun AusgabeAnsicht(
    auftrag: Auftrag.Gelesen,
    bau: Auftrag.Bau.Ok,
    spitze: Long,
    bestaetigungen: Long,
    adresse: String,
    danach: String,
    onBestaetigen: () -> Unit,
    onZurueck: () -> Unit,
    modifier: Modifier,
) {
    val t = LocalTexte.current
    val sperre = sperreVon(auftrag) ?: return
    val einloesen = auftrag is Auftrag.Gelesen.Einloesen
    val e = bau.entwurf
    Seite(
        if (einloesen) t.otc.titelEinloesen else t.otc.titelZurueck, onZurueck, modifier,
        fuss = { Hauptknopf(if (einloesen) t.otc.knopfEinloesen else t.otc.knopfZurueck, danach, onBestaetigen) },
    ) {
        // Regel 29: Betrag, Warnung, Zahlen, Adressen, zuletzt der blaue Hinweis.
        Hauptbetrag(t.otc.duBekommst, e.betrag)
        if (einloesen) Warnhinweis(t.otc.einloesenOeffentlich)
        Flaeche(abstand = 0.dp) {
            Wertzeile(t.otc.gebuehr, Betrag.genau(e.gebuehr, t.locale), einheit = "YSR", einpassen = true)
            Wertzeile(t.otc.gesamt, Betrag.genau(e.betrag + e.gebuehr, t.locale), einheit = "YSR", einpassen = true)
            if (auftrag is Auftrag.Gelesen.Einloesen) {
                Wertzeile(t.otc.bestaetigungen, t.otc.bestaetigungenVon(bestaetigungen, auftrag.n), einpassen = true)
                Wertzeile(
                    t.otc.einloesenBis,
                    t.otc.blockIn(sperre.teile.einloeseSchluss, sperre.teile.einloeseSchluss - spitze),
                    linie = false,
                    einpassen = true,
                )
            } else {
                Wertzeile(t.otc.zurueckAb, t.otc.block(sperre.teile.frist), linie = false, einpassen = true)
            }
        }
        AdressFeld(t.otc.vonSkript, sperre.skriptAdresse)
        AdressFeld(t.otc.anDich, adresse)
        if (einloesen) {
            // Der blaue Hinweis zugeklappt (Regel 29 und 40): Was man prueft, steht darueber.
            Einzelheiten {
                Hinweisstreifen(
                    t.otc.einloesenGeprueft,
                    vorne = { Symbol(R.drawable.ic_shield_check_fill, 20.dp, farbe = Farbe.BlauSchrift) },
                )
            }
        } else {
            Hinweisstreifen(
                t.otc.zurueckGeprueft,
                vorne = { Symbol(R.drawable.ic_shield_check_fill, 20.dp, farbe = Farbe.BlauSchrift) },
            )
        }
    }
}

// ----------------------------------------------------------------- Rahmen

/** Kopf fest, Mitte rollt, Fuss fest - wie auf den Sendebildschirmen. */
@Composable
private fun Seite(
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

@Composable
private fun Hauptknopf(text: String, danach: String, onKlick: () -> Unit) {
    Knopf(text, onKlick, Modifier.fillMaxWidth().testTag("otc_bestaetigen"), symbol = R.drawable.ic_lock_key_fill)
    Text(
        danach,
        style = Schrift.Hinweis.copy(fontSize = 12.sp),
        color = Farbe.Leise,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Eine Seite, die EINES sagt: wird gesendet, angenommen, abgelehnt. */
@Composable
private fun Meldung(
    @DrawableRes symbol: Int,
    titel: String,
    modifier: Modifier = Modifier,
    text: String? = null,
    ton: Color = YskarFarben.Blau,
    knopf: String? = null,
    onKnopf: () -> Unit = {},
    zweiterKnopf: String? = null,
    onZweiterKnopf: () -> Unit = {},
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
            Box(
                Modifier
                    .size(64.dp)
                    .gefasst(
                        rundung = 20.dp,
                        grund = SolidColor(ton.copy(alpha = 0.12f)),
                        rand = ton.copy(alpha = 0.30f),
                        glanz = 0f,
                    ),
                contentAlignment = Alignment.Center,
            ) { Symbol(symbol, 28.dp, farbe = if (ton == YskarFarben.Blau) Farbe.Schrift else ton) }
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
        Column(
            Modifier.padding(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 22.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (knopf != null) {
                Knopf(knopf, onKnopf, Modifier.fillMaxWidth().testTag("otc_knopf"), art = Knopfart.FLAECHE)
            }
            if (zweiterKnopf != null) {
                Knopf(zweiterKnopf, onZweiterKnopf, Modifier.fillMaxWidth(), art = Knopfart.STILL)
            }
        }
    }
}

// --------------------------------------------------------------- Bestaetigen

/**
 * PIN oder Finger - mit den Zahlen dessen, was unterschrieben wird
 * (Regel 27), ueber dem eigenen Ziffernfeld (Regel 15).
 */
@Composable
private fun Pinseite(
    worum: String,
    zahlen: List<Pair<String, String>>,
    fehler: String?,
    laeuft: Boolean,
    bioEingeschaltet: Boolean,
    onPin: (CharArray) -> Unit,
    onBio: () -> Unit,
    onZurueck: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val texte = LocalTexte.current
    val laenge = 6
    var ziffern by remember { mutableStateOf("") }
    val meldung = fehler.takeIf { ziffern.isEmpty() && !laeuft }
    val baender = baenderFuerPin(ziffern = ziffern.length, falsch = meldung != null, geprueft = laeuft)

    Box(modifier.fillMaxSize()) {
        Blatt(mittig = true) {
            Kopfzeile(
                titel = "",
                onZurueck = onZurueck,
                zurueckSymbol = R.drawable.ic_x_bold,
                zurueckText = texte.allgemein.abbrechen,
                zurueckAktiv = !laeuft,
            )
            Spacer(Modifier.weight(0.4f))
            Zeichen(
                links = baender[0],
                rechts = baender[1],
                stamm = baender[2],
                groesse = 72.dp,
                beschreibung = zifferStand(ziffern.length, laenge, texte),
            )
            Text(
                texte.otc.pinTitel,
                style = Schrift.Gross,
                color = Farbe.Schrift,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(start = 24.dp, top = 14.dp, end = 24.dp),
            )
            Text(
                meldung ?: if (laeuft) texte.allgemein.einenMoment else worum,
                style = Schrift.Name,
                color = if (meldung != null) Farbe.Warnung else Farbe.Leise,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(start = 28.dp, top = 6.dp, end = 28.dp).testTag("otc_worum"),
            )
            if (zahlen.isNotEmpty()) {
                Flaeche(Modifier.padding(start = 16.dp, top = 14.dp, end = 16.dp), abstand = 0.dp) {
                    zahlen.forEachIndexed { i, (name, wert) ->
                        Wertzeile(name, wert, hoehe = 36.dp, linie = i < zahlen.lastIndex, klein = true, einpassen = true)
                    }
                }
            }
            Spacer(Modifier.height(18.dp))
            Ziffernfeld(
                laenge = laenge,
                ziffern = ziffern,
                aktiv = !laeuft,
                knapp = true,
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
        }
    }
}

// ----------------------------------------------------------------- Ergebnis

@Composable
private fun Ergebnisseite(
    antwort: OtcAntwort,
    onOffeneTausche: () -> Unit,
    onFertig: () -> Unit,
    modifier: Modifier,
) {
    val t = LocalTexte.current.otc
    when (antwort) {
        is OtcAntwort.HashGesendet -> when (val r = antwort.ergebnis) {
            Rueckruf.Ergebnis.Ok -> Meldung(
                R.drawable.ic_check_bold, t.hashGesendet, text = t.hashGesendetText, ton = Farbe.Eingang,
                knopf = t.fertig, onKnopf = onFertig, modifier = modifier,
            )
            else -> Meldung(
                R.drawable.ic_warning_fill, t.seiteLehntAb, ton = Farbe.Warnung,
                knopf = t.schliessen, onKnopf = onFertig, modifier = modifier,
            ) {
                Warnhinweis(
                    when (r) {
                        Rueckruf.Ergebnis.Netz -> t.seiteNichtErreichbar
                        is Rueckruf.Ergebnis.Weiterleitung -> t.seiteWeiterleitung
                        is Rueckruf.Ergebnis.Abgelehnt ->
                            // Der Text kommt aus dem Netz: als Zitat, gekuerzt, ohne Steuerzeichen.
                            if (r.text != null) "${t.seiteSagt} ${t.zitat(r.text)}" else t.seiteLehntAb
                        Rueckruf.Ergebnis.Ok -> t.hashGesendet
                    },
                )
            }
        }

        is OtcAntwort.Angenommen -> Meldung(
            R.drawable.ic_check_bold, t.angenommen, text = t.angenommenText, ton = Farbe.Eingang,
            knopf = t.eintragOffen, onKnopf = onOffeneTausche,
            zweiterKnopf = t.fertig, onZweiterKnopf = onFertig, modifier = modifier,
        ) {
            Flaeche {
                Text(t.kennung, style = Schrift.Klein, color = Farbe.Leise)
                KennungInGruppen(antwort.txid, Modifier.fillMaxWidth())
            }
        }

        is OtcAntwort.KnotenLehntAb -> Meldung(
            R.drawable.ic_warning_fill, t.abgelehntTitel, text = t.knotenLehntAb(antwort.grund),
            ton = Farbe.Warnung, knopf = t.schliessen, onKnopf = onFertig, modifier = modifier,
        )

        is OtcAntwort.Abgelehnt -> Meldung(
            R.drawable.ic_warning_fill, t.abgelehntTitel,
            text = "${t.fehler(antwort.fehler)} ${t.nichtsUnterschrieben}",
            ton = Farbe.Warnung, knopf = t.schliessen, onKnopf = onFertig, modifier = modifier,
        )

        is OtcAntwort.Gestoert -> Meldung(
            R.drawable.ic_warning_fill, t.abgelehntTitel,
            text = "${t.stoerung(antwort.stoerung)} ${t.nichtsUnterschrieben}",
            ton = Farbe.Warnung, knopf = t.schliessen, onKnopf = onFertig, modifier = modifier,
        )

        is OtcAntwort.Fehler -> Meldung(
            R.drawable.ic_warning_fill, t.abgelehntTitel, text = antwort.meldung,
            ton = Farbe.Warnung, knopf = t.schliessen, onKnopf = onFertig, modifier = modifier,
        )

        // Kommt hier nicht an - eine falsche PIN bleibt auf dem Ziffernfeld.
        is OtcAntwort.PinFalsch -> Meldung(
            R.drawable.ic_warning_fill, t.abgelehntTitel, text = antwort.meldung,
            ton = Farbe.Warnung, knopf = t.schliessen, onKnopf = onFertig, modifier = modifier,
        )
    }
}

// ---------------------------------------------------------- Offene Tausche

/**
 * Die Tausche auf diesem Geraet. Ein Tipp baut aus dem Gemerkten den
 * Auftrag neu - der dann wieder ganz geprueft wird (Regel 43).
 */
@Composable
fun OffeneTauscheScreen(
    tausche: List<Tauschbuch.Eintrag>,
    onOeffnen: (Tauschbuch.Eintrag) -> Unit,
    onZurueck: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val texte = LocalTexte.current
    val t = texte.otc
    val offen = Tauschbuch.offen(tausche)
    BackHandler(onBack = onZurueck)
    Seite(t.offenTitel, onZurueck, modifier) {
        if (offen.isEmpty()) {
            Text(t.offenLeer, style = Schrift.Text, color = Farbe.Leise, modifier = Modifier.padding(4.dp))
        }
        for (e in offen) {
            Flaeche(abstand = 0.dp) {
                Wertzeile(t.rolle(e.rolle), t.stand(e.stand))
                Wertzeile(
                    "YSR",
                    Betrag.genau(e.ysr, texte.locale),
                    linie = e.gegen != null || e.skriptAdresse != null,
                )
                if (e.gegen != null) {
                    Wertzeile(t.gegenwert, "${e.gegenMenge.orEmpty()} ${e.gegen}", linie = e.skriptAdresse != null)
                }
                e.skriptAdresse?.let { Wertzeile(t.sperradresse, adresseKnapp(it), festbreite = true, linie = false) }
                Spacer(Modifier.height(10.dp))
                if (Tauschbuch.auftrag(e) != null) {
                    Knopf(t.oeffnen, { onOeffnen(e) }, Modifier.fillMaxWidth(), art = Knopfart.FLAECHE)
                } else {
                    Text(t.wartetAufSperre, style = Schrift.Hinweis, color = Farbe.Leise)
                }
            }
        }
        Hinweisstreifen(t.offenHinweis)
    }
}
