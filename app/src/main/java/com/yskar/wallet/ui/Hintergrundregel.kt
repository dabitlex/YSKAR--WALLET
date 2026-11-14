package com.yskar.wallet.ui

import com.yskar.wallet.ui.WalletModel.Ansicht

/**
 * Was geschieht, wenn die App in den Hintergrund geht.
 *
 * DIE WALLET SPERRT SICH, SOBALD MAN SIE VERLAESST (Wunsch des Besitzers,
 * 11.11.2026): anderes Programm, Startbildschirm, App-Uebersicht, Bildschirm
 * aus. Wer das Telefon danach in die Hand bekommt, steht vor der Sperre.
 *
 * Reine Funktionen, ohne Android - damit es Unittests gibt
 * (`HintergrundregelTest`).
 */
object Hintergrundregel {

    /**
     * Ob [ansicht] im Hintergrund zur Sperre werden muss.
     *
     * NICHT gesperrt wird, solange es noch keine Wallet gibt ([hatWallet]
     * falsch): Beim Einrichten steht nichts hinter einer PIN, zu der man
     * zurueckkehren koennte - wer die zwoelf Woerter gerade abschreibt und
     * kurz weg muss, soll sie danach noch vor sich haben. Diese Bildschirme
     * sind gegen Bildschirmfotos gesperrt und in der App-Uebersicht verdeckt.
     */
    fun sperrt(ansicht: Ansicht, hatWallet: Boolean): Boolean = when {
        !hatWallet -> false
        ansicht is Ansicht.Sperre -> false
        ansicht is Ansicht.Laedt -> false
        else -> true
    }

    /**
     * Wohin es nach dem Entsperren zurueckgeht - null heisst: zum
     * Hauptbildschirm.
     *
     * Aufgehoben wird zweierlei, und in beidem steht nichts Geheimes:
     *
     *  - der Status einer abgeschickten Ueberweisung. Wer gesendet hat und
     *    kurz in ein anderes Programm wechselt, will danach wissen, wie es
     *    um die Ueberweisung steht.
     *  - die Einfuehrung gleich nach dem Einrichten. Der Tresor braucht ein
     *    paar Sekunden zum Verschluesseln; wer in der Zeit wegschaut, kaeme
     *    sonst an der Sperre an und saehe die Einfuehrung nie.
     *
     * ALLES ANDERE WIRD VERWORFEN - der Entwurf einer Ueberweisung ebenso
     * wie die zwoelf Woerter unter "Einstellungen".
     */
    fun rueckkehr(ansicht: Ansicht): Ansicht? = when {
        ansicht is Ansicht.SendenFertig -> ansicht
        ansicht is Ansicht.Einfuehrung && !ansicht.ausMehr -> ansicht
        else -> null
    }

    /**
     * Ob eine Aenderung des Zustands zur Sperre werden muss. Zwei Gruende:
     *
     *  1. Die App ist [draussen], und [neu] waere eine offene Ansicht.
     *  2. AUS DER SPERRE FUEHRT NUR DAS ENTSPERREN: [alt] ist die Sperre,
     *     [neu] waere etwas anderes, und die Aenderung kommt nicht vom
     *     Entsperren ([entsperrt]). So eine Aenderung ist Arbeit, die vor der
     *     Sperre begonnen hat und erst danach fertig wurde - der Status einer
     *     Ueberweisung, die zwoelf Woerter nach der PIN.
     *
     * Auch ein Entsperren oeffnet nichts, solange die App draussen ist.
     */
    fun mussSperren(
        alt: Ansicht,
        neu: Ansicht,
        hatWallet: Boolean,
        draussen: Boolean,
        entsperrt: Boolean,
    ): Boolean {
        val imHintergrund = draussen && sperrt(neu, hatWallet)
        val ausDerSperre = alt is Ansicht.Sperre && neu !is Ansicht.Sperre && !entsperrt
        return imHintergrund || ausDerSperre
    }

    /**
     * Was nach dem Sperren aufgehoben ist: [neu], wenn es aufgehoben werden
     * darf ([rueckkehr]). Sonst bleibt, was schon aufgehoben war ([bisher]) -
     * aber nur, wenn vorher schon gesperrt war. Wer aus einer offenen Ansicht
     * sperrt, hebt nichts Altes auf.
     */
    fun aufgehoben(alt: Ansicht, bisher: Ansicht?, neu: Ansicht): Ansicht? =
        rueckkehr(neu) ?: bisher.takeIf { alt is Ansicht.Sperre }
}
