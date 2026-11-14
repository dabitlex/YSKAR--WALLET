package com.yskar.wallet.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.yskar.wallet.BiometriePruefung
import com.yskar.wallet.NetzSelbsttest
import com.yskar.wallet.R
import com.yskar.wallet.Selbsttest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Selbsttest - erreichbar ueber "Einstellungen".
 *
 * Kein Bildschirm fuer den Alltag, sondern der Nachweis, dass Kryptokern,
 * Tresor und Verbindung auf DIESEM Geraet tun, was die Unittests auf der
 * JVM behaupten. Jeder Bericht geht zusaetzlich ins Logcat
 * (``adb logcat -s YskarSelbsttest:I``), weil HyperOS "adb shell input"
 * verbietet und sich eine lange Liste sonst nicht von aussen durchblaettern
 * laesst.
 *
 * Alles hier arbeitet mit dem oeffentlichen BIP39-Testvektor und einem
 * eigenen Testtresor, der danach geloescht wird. Die echte Wallet wird
 * nicht angefasst.
 *
 * Was geprueft wird und in welcher Reihenfolge, steht HIER und ist seit dem
 * Umbau unveraendert. Wie es aussieht, steht in [SelbsttestBild].
 */
@Composable
fun SelbsttestScreen(
    activity: FragmentActivity,
    onZurueck: () -> Unit,
    modifier: Modifier = Modifier,
    // Die Adresse der Wallet auf diesem Geraet: Ihr Konto liest Abschnitt 3.
    adresse: String? = null,
) {
    // Die Berichte stehen in der Sprache der App. Die Namen der Abschnitte
    // im Logcat ("Kryptokern", "Tresor", ...) bleiben, wie sie sind: Das Log
    // liest der Entwickler.
    val t = LocalTexte.current

    val kern by produceState<Selbsttest.Bericht?>(initialValue = null) {
        value = withContext(Dispatchers.Default) {
            Selbsttest.kern(t).also { Selbsttest.protokolliere("Kryptokern", it) }
        }
    }
    val tresor by produceState<Selbsttest.Bericht?>(initialValue = null) {
        value = withContext(Dispatchers.Default) {
            Selbsttest.tresor(activity, t).also { Selbsttest.protokolliere("Tresor", it) }
        }
    }
    val netz by produceState<Selbsttest.Bericht?>(initialValue = null) {
        value = withContext(Dispatchers.IO) {
            NetzSelbsttest.pruefen(adresse, t).also { Selbsttest.protokolliere("Netz", it) }
        }
    }

    var bio by remember { mutableStateOf<Selbsttest.Bericht?>(null) }
    var bioLaeuft by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    SelbsttestBild(
        kern = kern,
        tresor = tresor,
        netz = netz,
        bio = bio,
        bioLaeuft = bioLaeuft,
        onBio = {
            bioLaeuft = true
            scope.launch {
                bio = BiometriePruefung.pruefen(activity, t)
                    .also { Selbsttest.protokolliere("Biometrie", it) }
                bioLaeuft = false
            }
        },
        onZurueck = onZurueck,
        modifier = modifier,
    )
}

/**
 * Das Aussehen des Selbsttests, ohne die Pruefungen selbst.
 *
 * Getrennt vom Screen, damit der Testrahmen den Bildschirm mit
 * Beispielberichten zeichnen kann: Der echte Selbsttest spricht mit dem
 * Spiegel und legt einen Testtresor an - beides hat in einem Bild nichts
 * zu suchen (REGELN.md, Regel 18).
 *
 * Vier Karten, eine je Pruefung. Ein Bericht, der noch `null` ist, rechnet
 * noch.
 */
@Composable
internal fun SelbsttestBild(
    kern: Selbsttest.Bericht?,
    tresor: Selbsttest.Bericht?,
    netz: Selbsttest.Bericht?,
    bio: Selbsttest.Bericht?,
    bioLaeuft: Boolean,
    onBio: () -> Unit,
    onZurueck: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val w = LocalTexte.current.selbsttest

    Column(modifier.fillMaxSize()) {
        Kopfzeile(w.titel, onZurueck = onZurueck)

        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, top = 2.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Pruefkarte(w.karteKern, kern)
            Pruefkarte(w.karteTresor, tresor)
            Pruefkarte(w.karteNetz, netz)

            Flaeche {
                Text(w.karteBiometrie, style = Schrift.Kopf, color = Farbe.Schrift)
                Text(
                    w.biometrieHinweis,
                    style = Schrift.Hinweis,
                    color = Farbe.Leise,
                )
                Knopf(
                    if (bioLaeuft) w.wartetAufSensor else w.biometriePruefen,
                    onBio,
                    Modifier.fillMaxWidth(),
                    art = Knopfart.FLAECHE,
                    symbol = R.drawable.ic_fingerprint_bold,
                    aktiv = !bioLaeuft,
                )
                bio?.let { Ergebnis(it) }
            }

            Text(
                w.fussnote,
                style = Schrift.Hinweis,
                color = Farbe.Leise,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
    }
}

/** Eine Pruefung: Titel, dann "rechnet …" oder das Ergebnis. */
@Composable
private fun Pruefkarte(titel: String, bericht: Selbsttest.Bericht?) {
    Flaeche {
        Text(titel, style = Schrift.Kopf, color = Farbe.Schrift)
        if (bericht == null) {
            Text(
                LocalTexte.current.selbsttest.rechnet,
                style = Schrift.Name,
                color = Farbe.Leise,
            )
        } else {
            Ergebnis(bericht)
        }
    }
}

/**
 * Das Urteil und darunter jede einzelne Zeile mit ihrem Wert.
 *
 * Ein Haken ersetzt das Wort "OK"; fuer einen Bildschirmleser steht es
 * weiter da. Ein Fehler behaelt sein Wort - er soll nicht nur an einer
 * Farbe zu erkennen sein.
 */
@Composable
private fun Ergebnis(bericht: Selbsttest.Bericht) {
    val w = LocalTexte.current.selbsttest

    Text(
        if (bericht.allesGruen) w.bestanden(bericht.dauerMs)
        else w.fehlgeschlagen(bericht.dauerMs),
        style = Schrift.HinweisFest,
        color = if (bericht.allesGruen) Farbe.Eingang else Farbe.Warnung,
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (z in bericht.zeilen) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Symbol(
                    if (z.ok) R.drawable.ic_check_bold else R.drawable.ic_x_bold,
                    14.dp,
                    Modifier
                        .padding(top = 3.dp)
                        .then(
                            if (z.ok) Modifier.semantics { contentDescription = w.ok }
                            else Modifier,
                        ),
                    farbe = if (z.ok) Farbe.Eingang else Farbe.Warnung,
                )
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        if (z.ok) z.name else w.fehlerZeile(z.name),
                        style = Schrift.Name,
                        color = if (z.ok) Farbe.Schrift else Farbe.WarnSchrift,
                    )
                    Text(z.wert, style = Schrift.Winzig, color = Farbe.Leise)
                }
            }
        }
    }
}
