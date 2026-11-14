package com.yskar.wallet.netz

import com.yskar.wallet.kern.Address
import com.yskar.wallet.kern.Hex
import com.yskar.wallet.kern.Params
import com.yskar.wallet.kern.Tx
import com.yskar.wallet.kern.Wallet

/**
 * Eine Überweisung prüfen, unterschreiben und einreichen.
 *
 * DIE REIHENFOLGE IST ABSICHT: erst alles prüfen, dann dem Nutzer zeigen,
 * was er unterschreibt, und erst danach den Schlüssel anfassen. Zwischen
 * Prüfung und Unterschrift liegt der Bestätigungsschritt der Oberfläche -
 * was dort steht, ist genau das, was signiert wird.
 *
 * Die Wallet baut und signiert auf dem Gerät. Was hinausgeht, sind fertige
 * Bytes: Kein Server kann Betrag oder Empfänger ändern, ohne die Signatur
 * zu brechen.
 */
object Senden {

    // ----------------------------------------------------------- Entwurf

    /** Alles, was feststeht, bevor unterschrieben wird. */
    data class Entwurf(
        val empfaenger: String,
        val empfaengerRoh: ByteArray,
        val betrag: Long,
        val gebuehr: Long,
        val notiz: ByteArray,
        val nonce: Long,
        val fassung: Int,
        val bytes: Int,
    ) {
        val gesamt: Long get() = betrag + gebuehr
        val notizText: String get() = String(notiz, Charsets.UTF_8)

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Entwurf) return false
            return empfaenger == other.empfaenger &&
                empfaengerRoh.contentEquals(other.empfaengerRoh) &&
                betrag == other.betrag && gebuehr == other.gebuehr &&
                notiz.contentEquals(other.notiz) && nonce == other.nonce &&
                fassung == other.fassung && bytes == other.bytes
        }

        override fun hashCode(): Int {
            var r = empfaenger.hashCode()
            r = 31 * r + betrag.hashCode()
            r = 31 * r + gebuehr.hashCode()
            r = 31 * r + nonce.hashCode()
            return r
        }
    }

    sealed interface Pruefung {
        data class Bereit(val entwurf: Entwurf) : Pruefung
        data class Problem(val grund: String) : Pruefung
    }

    /**
     * Alles prüfen, was sich ohne Schlüssel prüfen lässt.
     *
     * Gibt im Zweifel NICHT frei. Eine Überweisung, die der Knoten ablehnt,
     * kostet nur Zeit; eine, die er annimmt und die falsch ist, kostet Geld.
     */
    fun pruefe(
        eigeneAdresse: String,
        konto: Konto?,
        markt: Markt?,
        spitze: Long?,
        empfaengerText: String,
        betragText: String,
        notizText: String,
        stufe: Stufe = Stufe.NORMAL,
    ): Pruefung {
        if (konto == null) {
            return Pruefung.Problem(
                "Der Kontostand ist unbekannt. Lade ihn zuerst, sonst stimmt " +
                    "die Nonce nicht."
            )
        }

        val stand = Fassung.sendeStand(markt, spitze)
        if (stand.unbekannt) {
            return Pruefung.Problem(
                "Weder Gebührenauskunft noch Blockhöhe sind bekannt. " +
                    "Welche Fassung gilt, lässt sich nicht raten - es wird nicht gesendet."
            )
        }
        if (stand.pause) {
            return Pruefung.Problem(
                "Das Netz wechselt gerade die Konsensfassung. In diesem kurzen " +
                    "Fenster nimmt kein Knoten eine Überweisung an. Gleich wieder versuchen."
            )
        }

        // --- Empfänger ---------------------------------------------------
        val ziel = empfaengerText.trim()
        if (ziel.isEmpty()) return Pruefung.Problem("Es fehlt die Adresse des Empfängers.")

        val roh = runCatching { Address.decode(ziel) }.getOrNull()
            ?: return Pruefung.Problem(
                "Diese Adresse ist nicht gültig. Prüfsumme oder Präfix stimmen nicht."
            )

        if (!Fassung.empfaengerErlaubt(roh.size, stand.fassung)) {
            return Pruefung.Problem(
                "An ein gemeinsames Konto (ysr1p…) kann erst ab Konsensfassung 6 " +
                    "gezahlt werden. Das Netz steht noch davor."
            )
        }

        if (ziel == eigeneAdresse) {
            return Pruefung.Problem(
                "Das ist deine eigene Adresse. Die Überweisung würde nur die Gebühr kosten."
            )
        }

        // --- Betrag ------------------------------------------------------
        val betrag = Betrag.ausEingabe(betragText)
        if (betrag <= 0) return Pruefung.Problem("Der Betrag fehlt oder ist nicht lesbar.")

        val hoehe = (markt?.hoehe ?: spitze?.plus(1) ?: 0L).toInt()
        if (hoehe >= Params.FEE_V3_HEIGHT && betrag < Params.DUST_LIMIT) {
            return Pruefung.Problem(
                "Der Betrag liegt unter der Staubgrenze von " +
                    "${Betrag.genau(Params.DUST_LIMIT, min = 8)} YSR."
            )
        }

        // --- Notiz -------------------------------------------------------
        val notiz = Notiz.zuBytes(notizText)
        if (Notiz.bytes(notizText) > Notiz.BYTES) {
            return Pruefung.Problem(
                "Die Notiz ist länger als ${Notiz.BYTES} Byte. Umlaute zählen zwei, " +
                    "ein Emoji vier."
            )
        }

        // --- Gebühr und Deckung ------------------------------------------
        val bytes = Fassung.ueberweisungBytes(stand, markt, notiz.size, roh.size)
        val gebuehr = Fassung.gebuehrFuer(markt, stufe, bytes)
        val gesamt = betrag + gebuehr

        if (gesamt > konto.guthaben) {
            val fehlt = gesamt - konto.guthaben
            return Pruefung.Problem(
                "Das Guthaben reicht nicht. Betrag und Gebühr ergeben " +
                    "${Betrag.genau(gesamt)} YSR, es fehlen ${Betrag.genau(fehlt)} YSR."
            )
        }

        if (konto.wartend.isNotEmpty()) {
            return Pruefung.Problem(
                "Es wartet noch eine Zahlung auf einen Block. Warte, bis sie " +
                    "drin ist - sonst kollidieren die Nonces."
            )
        }

        return Pruefung.Bereit(
            Entwurf(
                empfaenger = ziel,
                empfaengerRoh = roh,
                betrag = betrag,
                gebuehr = gebuehr,
                notiz = notiz,
                nonce = konto.naechsteNonce,
                fassung = stand.fassung,
                bytes = bytes,
            )
        )
    }

    // -------------------------------------------------------- Unterschrift

    sealed interface Ergebnis {
        /**
         * Der Knoten hat sie angenommen. Das ist Schritt 2 von fünf - NICHT
         * "erledigt". Sie steht damit in keinem Block.
         */
        data class Angenommen(val txid: String?) : Ergebnis

        data class Abgelehnt(val code: String, val klartext: String, val detail: String?) : Ergebnis
        data class Fehler(val grund: String) : Ergebnis
    }

    /**
     * Unterschreiben und einreichen.
     *
     * [woerter] sind die zwölf Wörter aus dem Tresor. Sie werden hier NICHT
     * überschrieben - das gehört dem Aufrufer, der sie geholt hat. Der
     * abgeleitete Schlüssel wird überschrieben.
     *
     * Die Gegenprobe auf die eigene Adresse ist kein Schmuck: Passten Tresor
     * und abgeleiteter Schlüssel nicht zusammen, ginge die Überweisung von
     * einem Konto aus, das der Nutzer nicht gesehen hat.
     */
    fun unterschreibenUndSenden(
        entwurf: Entwurf,
        woerter: ByteArray,
        eigeneAdresse: String,
        spiegel: Spiegel,
    ): Ergebnis {
        val kp = runCatching {
            Wallet.fromMnemonic(String(woerter, Charsets.UTF_8))
        }.getOrElse {
            return Ergebnis.Fehler("Die Merkwörter aus dem Tresor sind unbrauchbar.")
        }

        try {
            if (kp.address != eigeneAdresse) {
                return Ergebnis.Fehler(
                    "Der Schlüssel passt nicht zur angezeigten Adresse. " +
                        "Es wird nichts gesendet."
                )
            }

            val tx = Tx.buildTransfer(
                from = kp.addressRaw,
                to = entwurf.empfaengerRoh,
                amount = entwurf.betrag,
                fee = entwurf.gebuehr,
                nonce = entwurf.nonce,
                publicKey = kp.publicKey,
                privateKey = kp.privateKey,
                memo = entwurf.notiz,
                version = entwurf.fassung,
            )

            // Die eigene Unterschrift nachrechnen, bevor sie hinausgeht.
            if (!Tx.verifyOwnSignature(tx)) {
                return Ergebnis.Fehler("Die eigene Signatur ließ sich nicht bestätigen.")
            }

            val roh = Tx.serialize(tx)
            if (roh.size > entwurf.bytes) {
                // Die Gebühr wurde auf die geschätzte Größe berechnet. Ist
                // die echte Überweisung größer, ist die Gebühr je Byte zu
                // niedrig - sie bliebe liegen. Lieber hier abbrechen als
                // eine Zahlung in den Mempool legen, die nie ankommt.
                return Ergebnis.Fehler(
                    "Die Überweisung ist größer als berechnet " +
                        "(${roh.size} statt ${entwurf.bytes} Byte). " +
                        "Die Gebühr wäre zu niedrig - es wird nichts gesendet."
                )
            }

            return when (val a = spiegel.senden(Hex.toHex(roh))) {
                is SendeErgebnis.Angenommen -> Ergebnis.Angenommen(a.txid)
                is SendeErgebnis.Abgelehnt ->
                    Ergebnis.Abgelehnt(a.grund, klartext(a.grund), a.detail)
            }
        } catch (e: NetzFehler) {
            return Ergebnis.Fehler(
                if (e.offline)
                    "Keine Verbindung zum Knoten. Die Überweisung wurde NICHT eingereicht."
                else "Der Knoten antwortet nicht wie erwartet (${e.status})."
            )
        } catch (e: Exception) {
            return Ergebnis.Fehler(e.message ?: e::class.java.simpleName)
        } finally {
            kp.wipe()
        }
    }

    /**
     * Ablehnungsgründe des Knotens in verständliche Sätze.
     *
     * Der Code bleibt daneben stehen: Wer nachfragen will, braucht ihn.
     */
    fun klartext(code: String): String = when (code) {
        "bad_nonce", "nonce_too_low", "nonce_gap" ->
            "Die laufende Nummer passt nicht. Lade den Kontostand neu und versuche es erneut."

        "insufficient_funds", "insufficient_balance" ->
            "Das Guthaben reicht nicht - vielleicht ist in der Zwischenzeit etwas abgegangen."

        "fee_too_low", "min_fee", "below_min_fee" ->
            "Die Gebühr ist zu niedrig für den nächsten Block."

        "bad_signature", "invalid_signature" ->
            "Die Signatur wurde nicht anerkannt. Das darf nicht passieren - bitte melden."

        "dust", "below_dust" ->
            "Der Betrag liegt unter der Staubgrenze."

        "mempool_full" ->
            "Die Warteliste des Knotens ist voll. Später noch einmal."

        "tx_too_large", "too_large" ->
            "Die Überweisung ist zu groß. Kürze die Notiz."

        "expired", "valid_until" ->
            "Die Überweisung ist abgelaufen, bevor sie in einen Block kam."

        "duplicate", "already_known" ->
            "Diese Überweisung kennt der Knoten schon."

        "bad_version", "wrong_version" ->
            "Die Fassung passt nicht zur aktuellen Höhe. Lade die Gebührenauskunft neu."

        else -> "Der Knoten hat sie abgelehnt."
    }
}
