package com.yskar.wallet.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.yskar.wallet.kern.Address
import com.yskar.wallet.netz.Spiegel

/**
 * Die Adressen des Explorers - `https://www.yskar.app/explorer`.
 *
 * Der Explorer oeffnet, was hinter dem `#` steht (nachgesehen an der Seite
 * selbst am 11.11.2026; die bestehende Wallet verlinkt ebenso):
 *
 *     #tx-<Kennung>     eine Ueberweisung
 *     #adr-<Adresse>    ein Konto mit Guthaben und Bewegungen
 *     #block-<Hoehe>    ein Block
 *
 * NICHTS UNGEPRUEFTES KOMMT IN EINE ADRESSE. Kennung und Hoehe kommen vom
 * Spiegel, also aus dem Netz. Eine Kennung ist genau 64 Hex-Zeichen, eine
 * Adresse besteht `Address.isValid` - alles andere ergibt null, und dann
 * gibt es keinen Knopf. So kann eine Antwort des Netzes keinen Verweis
 * formen, der woandershin fuehrt.
 *
 * Reine Funktionen (`ExplorerTest`).
 */
object Explorer {

    /** Dieselbe Adresse, die die Netzschicht schon kennt. */
    const val URL = Spiegel.EXPLORER

    private val KENNUNG = Regex("^[0-9a-f]{64}$")

    fun ueberweisung(txid: String): String? =
        if (KENNUNG.matches(txid)) "$URL#tx-$txid" else null

    fun konto(adresse: String): String? =
        if (Address.isValid(adresse)) "$URL#adr-$adresse" else null

    fun block(hoehe: Long): String? =
        if (hoehe >= 0) "$URL#block-$hoehe" else null
}

/**
 * Oeffnet [url] im Browser des Telefons.
 *
 * Die App geht dabei in den Hintergrund und SPERRT SICH (REGELN.md, Regel
 * 35) - wer zurueckkommt, gibt die PIN ein. Das ist gewollt: Der Explorer
 * ist ein fremdes Programm.
 *
 * Der Browser erfaehrt die Kennung oder die Adresse, die er zeigen soll -
 * und damit der Betreiber des Explorers (www.yskar.app), dass von diesem
 * Anschluss aus danach gesehen wurde. Die App selbst fragt einen anderen
 * Rechnernamen (den Spiegel); darum geschieht das nur auf einen Tipp hin,
 * nie von selbst.
 *
 * Gibt es keinen Browser, passiert nichts.
 */
fun imBrowserOeffnen(ctx: Context, url: String) {
    val absicht = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { ctx.startActivity(absicht) }
}
