package com.yskar.wallet.ui

import android.view.Window
import android.view.WindowManager
import java.util.WeakHashMap

/**
 * Wer das Fenster gerade gegen Bildschirmfotos sperrt - und warum.
 *
 * `FLAG_SECURE` ist EIN Schalter am Fenster, aber es gibt zwei Gruende, ihn
 * zu setzen: die Bildschirme mit den zwoelf Woertern (`BildschirmSchutz`,
 * REGELN.md Regel 14) und, auf Android vor 13, der Sichtschutz im
 * Hintergrund (`Hintergrundschutz`). Setzte und loeschte jeder fuer sich,
 * naehme der eine dem anderen den Schutz weg: Die App kaeme aus dem
 * Hintergrund zurueck, der Sichtschutz gaebe das Fenster frei - und die
 * zwoelf Woerter staenden ungeschuetzt da.
 *
 * Darum zaehlt diese Stelle mit. Gesperrt ist das Fenster, solange
 * irgendein Grund besteht; freigegeben wird erst, wenn keiner mehr da ist.
 *
 * Nur vom Hauptfaden aus benutzen - wie alles, was ein Fenster anfasst.
 */
object Fensterschutz {

    const val WOERTER = "woerter"
    const val HINTERGRUND = "hintergrund"

    private val gruende = WeakHashMap<Window, MutableMap<String, Int>>()

    fun halte(fenster: Window, grund: String) {
        val eigene = gruende.getOrPut(fenster) { mutableMapOf() }
        eigene[grund] = (eigene[grund] ?: 0) + 1
        fenster.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }

    fun gibFrei(fenster: Window, grund: String) {
        val eigene = gruende[fenster] ?: return
        val uebrig = (eigene[grund] ?: 0) - 1
        if (uebrig > 0) eigene[grund] = uebrig else eigene.remove(grund)
        if (eigene.isEmpty()) {
            gruende.remove(fenster)
            fenster.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    /** Ob [grund] das Fenster gerade haelt. */
    fun haelt(fenster: Window, grund: String): Boolean = (gruende[fenster]?.get(grund) ?: 0) > 0
}
