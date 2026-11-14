package com.yskar.wallet.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/**
 * Der Sperrbildschirm.
 *
 * Hier zeigt sich, warum das Zeichen kein Logo ist: Statt sechs Punkten
 * fuellt jede Ziffer das Zeichen weiter - zwei Ziffern ein Band. Man sieht
 * den Fortschritt an der Marke selbst, und bei falscher PIN wird sie rot.
 * Die Pruefung weiss davon nichts; das ist reine Anzeige.
 *
 * Was hier bewusst FEHLT: das Guthaben. Wer das Telefon in die Hand nimmt,
 * soll nicht sehen, ob sich das Zugreifen lohnt. Die Adresse steht da, weil
 * sie oeffentlich ist und beim Wiedererkennen der eigenen Wallet hilft.
 *
 * Vorlage: design-neu/html/03_sperre.html. EINE ABWEICHUNG, auf Wunsch des
 * Besitzers (11.11.2026): Der Fingerabdruck ist kein breiter Knopf am Fuss
 * mehr, sondern eine Taste im Ziffernfeld - links neben der Null, unter der
 * Sieben, im selben Blau.
 */
@Composable
fun SperreScreen(
    adresse: String?,
    bioEingeschaltet: Boolean,
    laeuft: Boolean,
    fehler: String?,
    wartezeit: Long,
    onPin: (CharArray) -> Unit,
    onBio: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = LocalTexte.current
    val laenge = 6
    var ziffern by remember { mutableStateOf("") }

    // Nach einem Fehlversuch das Feld leeren, damit nicht an eine falsche
    // Eingabe angehaengt wird.
    LaunchedEffect(fehler) {
        if (fehler != null) ziffern = ""
    }

    val gesperrt = wartezeit > 0
    val baender = baenderFuerPin(
        ziffern = ziffern.length,
        falsch = fehler != null,
        geprueft = laeuft,
    )

    Box(modifier.fillMaxSize()) {
        Musterhimmel(hoehe = 330.dp, deckkraft = 0.07f)

        Blatt(mittig = true) {
            // Oben Luft, die bei wenig Platz nachgibt: 54 dp in der Vorlage,
            // hier ein Anteil der freien Hoehe und mindestens 12.
            Spacer(Modifier.height(12.dp))
            Spacer(Modifier.weight(0.6f))

            Zeichen(
                links = baender[0],
                rechts = baender[1],
                stamm = baender[2],
                groesse = 104.dp,
                beschreibung = zifferStand(ziffern.length, laenge, t),
            )
            Text(
                "YSKAR",
                style = Schrift.Schriftzug.copy(fontSize = 17.sp, letterSpacing = 0.14.em),
                color = Farbe.Schrift,
                modifier = Modifier.padding(top = 14.dp),
            )

            if (adresse != null) {
                Adressmarke(adresse, Modifier.padding(top = 10.dp))
            }

            // Eine Zeile, drei Aufgaben: Aufforderung, Fortgang, Fehler. Sie
            // steht immer an derselben Stelle, damit das Ziffernfeld darunter
            // nicht springt, wenn der Text wechselt.
            Text(
                text = when {
                    fehler != null -> fehler
                    laeuft -> t.einrichten.entsperrtGerade
                    else -> t.einrichten.pinEingeben
                },
                style = Schrift.Zeile,
                color = if (fehler != null) Farbe.Warnung else Farbe.Leise,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, top = 30.dp, end = 20.dp),
            )

            Spacer(Modifier.height(20.dp))

            Ziffernfeld(
                laenge = laenge,
                ziffern = ziffern,
                aktiv = !laeuft && !gesperrt,
                onZiffern = { neu ->
                    ziffern = neu
                    if (neu.length == laenge) onPin(neu.toCharArray())
                },
                // Der Fingerabdruck ist eine Taste des Feldes: links neben
                // der Null, unter der Sieben. Waehrend einer Wartezeit
                // fehlt er, wie vorher der Knopf am Fuss.
                onFinger = if (bioEingeschaltet && !gesperrt) onBio else null,
                fingerBeschreibung = t.einrichten.mitFingerabdruckEntsperren,
                fingerAktiv = !laeuft,
            )

            Spacer(Modifier.height(16.dp))
            Spacer(Modifier.weight(1f))

            Spacer(Modifier.height(26.dp))
        }
    }
}
