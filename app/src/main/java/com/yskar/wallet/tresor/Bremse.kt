package com.yskar.wallet.tresor

/**
 * Bremse gegen Durchprobieren am entsperrten Geraet.
 *
 * Nach fuenf Fehlversuchen eine Wartezeit, die sich mit jedem weiteren
 * verdoppelt, gedeckelt bei 15 Minuten. Uebernommen aus der Vorlage
 * (wallet/tresor.ts).
 *
 * BEWUSST OHNE LOESCHEN. Manche Wallets leeren sich nach zehn Fehlversuchen.
 * Das macht die App zur Waffe gegen ihren eigenen Besitzer: Ein Kind, das
 * herumtippt, oder jemand, der die PIN wirklich vergessen hat, verliert
 * damit alles. Die zwoelf Woerter sind die Wiederherstellung, nicht die
 * Loeschung.
 *
 * Reine Rechnung, keine Android-API: Die Uhr kommt von aussen, damit der
 * Unittest die Zeit vorstellen kann.
 */
class Bremse(
    private val ablage: Ablage,
    private val uhr: () -> Long = { System.currentTimeMillis() },
) {

    /** Wo Fehlzahl und Sperrzeit liegen. Klein genug fuer SharedPreferences. */
    interface Ablage {
        fun lesen(): Stand
        fun schreiben(stand: Stand)
    }

    data class Stand(val fehl: Int = 0, val bis: Long = 0)

    companion object {
        /** So viele Versuche sind frei. */
        const val FREI = 5

        const val BASIS_SEKUNDEN = 30L
        const val HOECHSTENS_SEKUNDEN = 900L

        /** Wartezeit nach `fehl` Fehlversuchen, in Sekunden. */
        fun wartezeitFuer(fehl: Int): Long {
            if (fehl < FREI) return 0
            val stufe = fehl - FREI
            // Ab Stufe 5 ist der Deckel ohnehin erreicht; ohne diese Grenze
            // liefe die Verschiebung in einen Ueberlauf.
            if (stufe >= 5) return HOECHSTENS_SEKUNDEN
            return minOf(HOECHSTENS_SEKUNDEN, BASIS_SEKUNDEN shl stufe)
        }
    }

    /** Sekunden, die noch zu warten sind. 0 = Eingabe erlaubt. */
    fun wartezeit(): Long {
        val bis = ablage.lesen().bis
        val rest = bis - uhr()
        return if (rest <= 0) 0 else (rest + 999) / 1000
    }

    fun darfVersuchen(): Boolean = wartezeit() == 0L

    fun fehlversuch(): Long {
        val fehl = ablage.lesen().fehl + 1
        val sek = wartezeitFuer(fehl)
        ablage.schreiben(Stand(fehl, if (sek > 0) uhr() + sek * 1000 else 0))
        return sek
    }

    fun erfolg() {
        ablage.schreiben(Stand())
    }

    fun fehlversuche(): Int = ablage.lesen().fehl
}
