package com.yskar.wallet.otc

import com.yskar.wallet.kern.Bip39
import com.yskar.wallet.kern.Wallet
import com.yskar.wallet.otc.Auftrag.Bau
import com.yskar.wallet.otc.Auftrag.Fehler
import com.yskar.wallet.otc.Auftrag.Gelesen
import com.yskar.wallet.signatur.Nachricht

/**
 * Die Schritte eines OTC-Auftrags, die einen Schluessel brauchen -- und der
 * eine, der die Gebuehr einrechnet. Zwischen Modell und [Auftrag]: Das
 * Modell holt die Woerter aus dem Tresor und die Zahlen vom Knoten, alles
 * dazwischen passiert hier, ohne Android und ohne Netz (`AblaufTest`).
 *
 * Die Woerter ueberschreibt der Aufrufer -- er hat sie geholt. Was hier
 * daraus abgeleitet wird (Schluessel, Seed, OTC-Wurzel), wird hier wieder
 * ueberschrieben.
 */
object Ablauf {

    enum class Stoerung {
        WOERTER_UNBRAUCHBAR,     // aus den Woertern laesst sich kein Schluessel ableiten
        SCHLUESSEL_PASST_NICHT,  // abgeleiteter Schluessel gehoert nicht zur Adresse
        GEGENPROBE,              // eine der Gegenproben hat abgebrochen
    }

    // --------------------------------------------------------------- Gebuehr

    /**
     * Den Entwurf mit Gebuehr bauen. Die Groesse haengt nicht von der Gebuehr
     * ab (u64 fest), also: einmal mit 0 messen, Gebuehr fuer diese Groesse
     * holen, endgueltig bauen. [gebuehrFuer]: Bytes -> Gebuehr, aus der
     * Gebuehrenlage des Knotens (`Fassung.gebuehrFuer`).
     */
    fun mitGebuehr(gebuehrFuer: (Int) -> Long, baue: (Long) -> Bau): Bau {
        val probe = baue(0)
        if (probe !is Bau.Ok) return probe
        return baue(gebuehrFuer(probe.bytes))
    }

    // ----------------------------------------------------------------- hash

    sealed interface HashErgebnis {
        class Ok(val auftrag: Gelesen.Hash, val signiert: Nachricht.Signiert) : HashErgebnis
        data class Abgelehnt(val fehler: Fehler) : HashErgebnis
        data class Gestoert(val stoerung: Stoerung) : HashErgebnis
    }

    /**
     * Den hash-Auftrag unmittelbar vor dem Unterschreiben NOCH EINMAL lesen
     * (die Zeit laeuft) und h unterschreiben. Heraus geht nur h.
     */
    fun hashUnterschreiben(code: String, woerter: ByteArray, eigeneAdresse: String, jetztMs: Long): HashErgebnis {
        val a = when (val l = Auftrag.lesen(code, eigeneAdresse, jetztMs)) {
            is Auftrag.Lesung.Abgelehnt -> return HashErgebnis.Abgelehnt(l.fehler)
            is Auftrag.Lesung.Ok -> l.auftrag as? Gelesen.Hash ?: return HashErgebnis.Abgelehnt(Fehler.ART)
        }
        return mitSchluesseln(woerter, eigeneAdresse, { HashErgebnis.Gestoert(it) }) { kp, wurzel ->
            val nachricht = Auftrag.hashNachricht(a, wurzel)
            val s = Nachricht.signieren(nachricht, kp.privateKey, kp.publicKey)
            if (Nachricht.pruefen(s) != Nachricht.Echtheit.GUELTIG) HashErgebnis.Gestoert(Stoerung.GEGENPROBE)
            else HashErgebnis.Ok(a, s)
        }
    }

    // ------------------------------------------------------ einloesen/zurueck

    sealed interface AusgabeErgebnis {
        class Fertig(val fertig: Auftrag.Fertig) : AusgabeErgebnis
        data class Abgelehnt(val fehler: Fehler) : AusgabeErgebnis
        data class Gestoert(val stoerung: Stoerung) : AusgabeErgebnis
    }

    /**
     * Den gezeigten Entwurf unterschreiben -- unveraendert (Regel 19). Beim
     * Einloesen kommt erst hier das Geheimnis dazu, aus Zweig 7'.
     */
    fun ausgabeUnterschreiben(
        auftrag: Gelesen,
        bau: Bau.Ok,
        woerter: ByteArray,
        eigeneAdresse: String,
    ): AusgabeErgebnis {
        val sperre = when (auftrag) {
            is Gelesen.Einloesen -> auftrag.sperre
            is Gelesen.Zurueck -> auftrag.sperre
            else -> return AusgabeErgebnis.Abgelehnt(Fehler.ART)
        }
        if (bau.einloesen != (auftrag is Gelesen.Einloesen)) return AusgabeErgebnis.Abgelehnt(Fehler.ART)
        return mitSchluesseln(woerter, eigeneAdresse, { AusgabeErgebnis.Gestoert(it) }) { kp, wurzel ->
            val x = if (auftrag is Gelesen.Einloesen) {
                Auftrag.geheimnisFuer(auftrag, wurzel) ?: return@mitSchluesseln AusgabeErgebnis.Abgelehnt(Fehler.GEHEIMNIS)
            } else null
            try {
                AusgabeErgebnis.Fertig(Auftrag.unterschreiben(sperre, bau, kp.privateKey, kp.publicKey, x))
            } catch (e: IllegalStateException) {
                AusgabeErgebnis.Gestoert(Stoerung.GEGENPROBE)
            } finally {
                x?.fill(0)
            }
        }
    }

    // ---------------------------------------------------------- Schluessel

    private inline fun <T> mitSchluesseln(
        woerter: ByteArray,
        eigeneAdresse: String,
        gestoert: (Stoerung) -> T,
        tun: (Wallet.Keypair, ByteArray) -> T,
    ): T {
        val text = String(woerter, Charsets.UTF_8)
        val kp = runCatching { Wallet.fromMnemonic(text) }.getOrNull()
            ?: return gestoert(Stoerung.WOERTER_UNBRAUCHBAR)
        var seed: ByteArray? = null
        var wurzel: ByteArray? = null
        try {
            if (kp.address != eigeneAdresse) return gestoert(Stoerung.SCHLUESSEL_PASST_NICHT)
            seed = Bip39.toSeed(text, "")
            wurzel = Wallet.otcWurzel(seed, 0)
            return tun(kp, wurzel)
        } finally {
            kp.wipe()
            seed?.fill(0)
            wurzel?.fill(0)
        }
    }
}
