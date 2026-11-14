package com.yskar.wallet.signatur

import com.yskar.wallet.kern.Wallet
import com.yskar.wallet.signatur.Nachricht.Ablehnung

/**
 * Vom geoeffneten Tresor zur fertigen Signatur - in EINEM Schritt, an EINER
 * Stelle.
 *
 * Was hier hereinkommt, ist die ROHE Eingabe (der Text aus dem Feld, der
 * Code aus dem QR), nicht eine schon gepruefte Nachricht. Geprueft wird
 * hier, unmittelbar vor dem Unterschreiben, mit der Uhr von jetzt - so kann
 * kein Bildschirm etwas anderes unterschreiben lassen, als diese Pruefung
 * durchlaesst, und eine Anfrage, die waehrend der PIN-Eingabe abgelaufen
 * ist, wird nicht mehr signiert.
 *
 * Drei Gegenproben, wie beim Senden (REGELN.md, Regel 21, sinngemaess):
 *  - die Adresse des abgeleiteten Schluessels ist die angezeigte,
 *  - die Nachricht besteht die Pruefung (`Nachricht.vorbereiten`),
 *  - die eigene Signatur ist gueltig.
 * Jede davon bricht ab, statt zu signieren.
 *
 * Der Schluessel wird hier abgeleitet und hier wieder ueberschrieben. Die
 * Woerter ueberschreibt der Aufrufer - er hat sie aus dem Tresor geholt.
 *
 * Reine Funktionen, ohne Android und ohne Netz (`UnterschriftTest`).
 */
object Unterschrift {

    /** Was schiefgehen kann, ohne dass die Nachricht schuld ist. */
    enum class Stoerung {
        /** Aus den Woertern im Tresor laesst sich kein Schluessel ableiten. */
        WOERTER_UNBRAUCHBAR,

        /** Der abgeleitete Schluessel gehoert nicht zur angezeigten Adresse. */
        SCHLUESSEL_PASST_NICHT,

        /** Die eigene Signatur liess sich nicht bestaetigen. */
        GEGENPROBE,
    }

    sealed interface Ergebnis {
        /**
         * Signiert. [anmeldung] bei einer Anmeldung; [rueckruf] nur, wenn sie
         * aus einem Code kam und die Antwort dorthin gehen soll.
         */
        data class Fertig(
            val signiert: Nachricht.Signiert,
            val anmeldung: Nachricht.Anmeldung? = null,
            val rueckruf: String? = null,
        ) : Ergebnis

        /** Die Pruefung hat abgelehnt. Es wurde nichts signiert. */
        data class Abgelehnt(
            val grund: Ablehnung,
            val domain: String? = null,
            val rueckrufHost: String? = null,
        ) : Ergebnis

        /** Es wurde nichts signiert. */
        data class Gestoert(val stoerung: Stoerung) : Ergebnis
    }

    /**
     * Freien Text oder eine eingefuegte Nachricht signieren. Eine
     * eingefuegte Anmeldung wird wie eine Anmeldung geprueft; ihre Antwort
     * geht dann nirgendwohin, sie wird zum Kopieren angezeigt.
     */
    fun text(eingabe: String, woerter: ByteArray, eigeneAdresse: String, jetztMs: Long): Ergebnis =
        mitSchluessel(woerter, eigeneAdresse) { kp ->
            when (val p = Nachricht.vorbereiten(eingabe, eigeneAdresse, jetztMs)) {
                is Nachricht.Pruefung.Abgelehnt -> Ergebnis.Abgelehnt(p.grund)
                is Nachricht.Pruefung.Text -> besiegelt(p.nachricht, kp, null, null)
                is Nachricht.Pruefung.Login -> besiegelt(p.nachricht, kp, p.anmeldung, null)
            }
        }

    /** Eine Anmeldung aus einem QR-Code oder `yskar:`-Link signieren. */
    fun ausCode(code: String, woerter: ByteArray, eigeneAdresse: String, jetztMs: Long): Ergebnis =
        mitSchluessel(woerter, eigeneAdresse) { kp ->
            when (val p = Nachricht.anmeldungAusCode(code, eigeneAdresse, jetztMs)) {
                is Nachricht.QrPruefung.Abgelehnt -> Ergebnis.Abgelehnt(p.grund, p.domain, p.rueckrufHost)
                is Nachricht.QrPruefung.Ok -> besiegelt(p.nachricht, kp, p.anmeldung, p.rueckruf)
            }
        }

    private fun besiegelt(
        nachricht: String,
        kp: Wallet.Keypair,
        anmeldung: Nachricht.Anmeldung?,
        rueckruf: String?,
    ): Ergebnis {
        val s = Nachricht.signieren(nachricht, kp.privateKey, kp.publicKey)
        if (Nachricht.pruefen(s) != Nachricht.Echtheit.GUELTIG) return Ergebnis.Gestoert(Stoerung.GEGENPROBE)
        return Ergebnis.Fertig(s, anmeldung, rueckruf)
    }

    private fun mitSchluessel(
        woerter: ByteArray,
        eigeneAdresse: String,
        tun: (Wallet.Keypair) -> Ergebnis,
    ): Ergebnis {
        val kp = runCatching { Wallet.fromMnemonic(String(woerter, Charsets.UTF_8)) }
            .getOrElse { return Ergebnis.Gestoert(Stoerung.WOERTER_UNBRAUCHBAR) }
        try {
            if (kp.address != eigeneAdresse) return Ergebnis.Gestoert(Stoerung.SCHLUESSEL_PASST_NICHT)
            return tun(kp)
        } finally {
            kp.wipe()
        }
    }
}
