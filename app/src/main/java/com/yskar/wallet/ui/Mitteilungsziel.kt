package com.yskar.wallet.ui

import com.yskar.wallet.netz.VerlaufEintrag
import com.yskar.wallet.netz.Waechter

/**
 * Wohin ein Tipp auf eine Mitteilung des Waechters fuehrt.
 *
 * Die Mitteilung traegt den [Waechter.schluessel] EINES Verlaufseintrags
 * (`txid:idx:art`). Die App nimmt ihn an, wartet - wenn sie gesperrt ist -
 * bis nach dem Entsperren (Regel 35: aus der Sperre fuehrt nur das
 * Entsperren) und oeffnet dann genau diese Zahlung.
 *
 * EINE ABSICHT IST KEIN BEWEIS. MainActivity ist exportiert; jede App
 * koennte sie mit dieser Aktion starten. Darum wird der Schluessel streng
 * geprueft, bevor er gilt, und er bewirkt nie mehr, als eine Zahlung der
 * EIGENEN Wallet zu zeigen - und nur, wenn sie im geladenen Verlauf steht.
 * Kein Netzabruf nach dieser Kennung, kein Verweis, nichts wird gespeichert.
 *
 * REIN, OHNE ANDROID-API: siehe `MitteilungszielTest`.
 */
object Mitteilungsziel {

    /** Aktion der Absicht hinter der Mitteilung. */
    const val AKTION = "com.yskar.wallet.ZAHLUNG_ZEIGEN"

    /** Name des Zusatzes, der den Schluessel traegt. */
    const val SCHLUESSEL = "schluessel"

    /** 64 kleine Hex-Zeichen, Platz im Block, Art - genau wie [Waechter.schluessel]. */
    private val FORM = Regex("^[0-9a-f]{64}:[0-9]{1,9}:(REWARD|POOL|EIN|AUS|UNBEKANNT)$")

    /** Der Schluessel, wenn er die Form hat - sonst null. */
    fun gueltig(roh: String?): String? =
        roh?.takeIf { it.length <= 100 && FORM.matches(it) }

    /** Der Eintrag zu [schluessel] im geladenen Verlauf, oder null. */
    fun finde(verlauf: List<VerlaufEintrag>, schluessel: String): VerlaufEintrag? =
        verlauf.firstOrNull { Waechter.schluessel(it) == schluessel }
}
