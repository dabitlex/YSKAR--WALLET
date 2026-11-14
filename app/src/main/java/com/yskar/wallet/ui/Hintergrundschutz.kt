package com.yskar.wallet.ui

import android.os.Build
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/**
 * Die App im Hintergrund: gesperrt und nicht zu erkennen.
 *
 * Drei Dinge, die zusammen gehoeren (Wunsch des Besitzers, 11.11.2026):
 *
 *  1. SPERREN. Geht die App in den Hintergrund (`ON_STOP`), erfaehrt es das
 *     Modell und sperrt - siehe [Hintergrundregel]. Kommt sie zurueck
 *     (`ON_START`), erfaehrt es das auch.
 *
 *  2. DIE APP-UEBERSICHT ZEIGT NICHTS. Ab Android 13 nimmt das System kein
 *     Vorschaubild mehr auf (`setRecentsScreenshotEnabled(false)`).
 *     Bildschirmfotos bleiben dabei erlaubt - eine Adresse oder einen
 *     Status darf man fotografieren duerfen (REGELN.md, Regel 14).
 *     Davor gibt es diesen Schalter nicht; dort wird das Fenster gesperrt,
 *     solange die App nicht vorn ist (`FLAG_SECURE` ueber [Fensterschutz],
 *     damit die Bildschirme mit den zwoelf Woertern ihren Schutz behalten).
 *
 *  3. DER VORHANG ([Sichtschutz]). Solange die App nicht vorn ist, liegt
 *     ueber dem Inhalt eine Flaeche mit dem Zeichen. Das deckt, was Schalter
 *     2 nicht deckt: das lebende Bild waehrend der Wischgeste in die
 *     Uebersicht, und Hersteller, die es mit Schalter 2 nicht genau nehmen.
 *
 * `ON_PAUSE` fuer Vorhang und Fenster, `ON_STOP` fuer die Sperre: Die Frage
 * des Systems nach dem Finger oder nach einer Erlaubnis haelt die App nur
 * an - dabei darf sie sich nicht sperren, sonst kaeme niemand mehr hinein.
 */
object Hintergrundschutz {

    /** Ab hier kann das System selbst auf das Vorschaubild verzichten. */
    private val systemKannEs = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    /**
     * Einmal in `onCreate`. [onDraussen] und [onZurueck] gehen an das Modell.
     *
     * Ein Neuaufbau der Activity (Schriftgroesse, dunkles Schema) ist kein
     * Verlassen: Dabei wird nicht gesperrt.
     */
    fun einrichten(
        activity: ComponentActivity,
        onDraussen: () -> Unit,
        onZurueck: () -> Unit,
    ) {
        if (systemKannEs) activity.setRecentsScreenshotEnabled(false)

        activity.lifecycle.addObserver(
            LifecycleEventObserver { _, ereignis ->
                when (ereignis) {
                    Lifecycle.Event.ON_PAUSE ->
                        if (!systemKannEs) {
                            Fensterschutz.halte(activity.window, Fensterschutz.HINTERGRUND)
                        }

                    Lifecycle.Event.ON_RESUME ->
                        if (Fensterschutz.haelt(activity.window, Fensterschutz.HINTERGRUND)) {
                            Fensterschutz.gibFrei(activity.window, Fensterschutz.HINTERGRUND)
                        }

                    Lifecycle.Event.ON_STOP ->
                        if (!activity.isChangingConfigurations) onDraussen()

                    Lifecycle.Event.ON_START -> onZurueck()

                    else -> Unit
                }
            },
        )
    }

    /** Ob bei diesem Zustand der Vorhang liegt: immer, wenn die App nicht vorn ist. */
    fun verdeckt(zustand: Lifecycle.State): Boolean = !zustand.isAtLeast(Lifecycle.State.RESUMED)
}

/** Ob die App gerade nicht vorn ist - folgt dem Lebenslauf der Activity. */
@Composable
fun nichtVorn(): Boolean {
    val lebenslauf = LocalLifecycleOwner.current.lifecycle
    var verdeckt by remember(lebenslauf) {
        mutableStateOf(Hintergrundschutz.verdeckt(lebenslauf.currentState))
    }
    DisposableEffect(lebenslauf) {
        val beobachter = LifecycleEventObserver { quelle, _ ->
            verdeckt = Hintergrundschutz.verdeckt(quelle.lifecycle.currentState)
        }
        lebenslauf.addObserver(beobachter)
        onDispose { lebenslauf.removeObserver(beobachter) }
    }
    return verdeckt
}

/**
 * Der Vorhang: [inhalt], und darueber - solange [verdeckt] - der Grund der
 * App mit dem Zeichen in der Mitte.
 *
 * Der Inhalt bleibt darunter stehen (Eingaben gehen nicht verloren), ist
 * aber weder zu sehen noch fuer einen Bildschirmleser da.
 */
@Composable
fun Sichtschutz(
    verdeckt: Boolean,
    modifier: Modifier = Modifier,
    inhalt: @Composable () -> Unit,
) {
    Box(modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .then(if (verdeckt) Modifier.clearAndSetSemantics { } else Modifier),
        ) { inhalt() }

        if (verdeckt) {
            val ansage = LocalTexte.current.allgemein.sichtschutz
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Farbe.Grund)
                    .testTag("sichtschutz")
                    .semantics { contentDescription = ansage },
                contentAlignment = Alignment.Center,
            ) {
                ZeichenRuhig(groesse = 72.dp)
            }
        }
    }
}
