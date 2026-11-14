package com.yskar.wallet.netz

import com.yskar.wallet.kern.Address
import com.yskar.wallet.kern.Params
import com.yskar.wallet.kern.Tx

/**
 * Welche Ueberweisung die Wallet jetzt bauen darf.
 *
 * Portiert aus src/wallet/fassung.ts (MIT, The YSKAR developers).
 *
 * Ab V6_HEIGHT gilt nur noch Fassung 2, davor nur Fassung 1 - keine
 * Uebergangsfrist. In den drei Bloecken davor sendet die Wallet nichts:
 * Eine Fassung 1, die an der Grenze noch wartet, faellt aus dem Mempool;
 * eine Fassung 2 nimmt vor der Hoehe niemand an.
 *
 * Quelle ist ZUERST die Gebuehrenauskunft: Der Knoten nennt dort die
 * Fassung und die Sendepause fuer den naechsten Block. Fehlt sie, rechnet
 * die Wallet aus der bekannten Hoehe. Ist auch die unbekannt, wird NICHT
 * GERATEN und nicht gesendet - eine Ueberweisung in der falschen Fassung
 * ist verlorene Zeit, im Zweifel mit gesperrter Nonce.
 */
object Fassung {

    data class SendeStand(
        /** Fassung der Ueberweisung, die jetzt zu bauen ist. */
        val fassung: Int,
        /** Sendepause vor der Aktivierungshoehe. */
        val pause: Boolean,
        /** Weder Auskunft noch Hoehe: nicht raten, nicht senden. */
        val unbekannt: Boolean,
    ) {
        val darfSenden: Boolean get() = !pause && !unbekannt
    }

    fun sendeStand(
        markt: Markt?,
        spitze: Long?,
        v6Height: Int = Params.V6_HEIGHT,
    ): SendeStand {
        val f = markt?.fassung
        if (f == 1 || f == 2) {
            return SendeStand(
                fassung = if (f == 2) Tx.TRANSFER_V2 else Tx.TRANSFER_V1,
                pause = markt.sendepause,
                unbekannt = false,
            )
        }
        if (spitze != null) {
            val naechste = (spitze + 1).toInt()
            return SendeStand(
                fassung = Tx.transferVersionAt(naechste, v6Height),
                pause = Tx.sendepause(naechste, v6Height),
                unbekannt = false,
            )
        }
        return SendeStand(
            fassung = Tx.TRANSFER_V1,
            pause = false,
            unbekannt = v6Height.toLong() != Params.V6_HEIGHT_UNSET,
        )
    }

    /** Laenge der Rohadresse: 20 fuer ysr1…, 33 fuer ysr1p…; null bei Unsinn. */
    fun empfaengerBytes(adresse: String): Int? =
        runCatching { Address.decode(adresse).size }.getOrNull()

    /** ysr1p… erst ab Fassung 2. */
    fun empfaengerErlaubt(bytes: Int, fassung: Int): Boolean =
        bytes == Params.ADDRESS_BYTES || fassung == Tx.TRANSFER_V2

    /** Groesse der Ueberweisung in Byte, so wie der naechste Block sie misst. */
    fun ueberweisungBytes(
        stand: SendeStand,
        markt: Markt?,
        notizBytes: Int,
        empfaenger: Int = Params.ADDRESS_BYTES,
    ): Int {
        val v6 = if (stand.fassung == Tx.TRANSFER_V2) 0 else Int.MAX_VALUE
        val basis = markt?.basisBytes
        if (basis != null) {
            // Die Auskunft nennt die Groesse ohne Notiz fuer einen
            // Empfaenger der Version 0; der Aufschlag fuer ysr1p… kommt dazu.
            val mehr = Tx.transferBytesAt(1, 0, v6, empfaenger) - Tx.transferBytesAt(1, 0, v6)
            return basis + mehr + notizBytes
        }
        return Tx.transferBytesAt(1, notizBytes, v6, empfaenger)
    }

    /**
     * Gebuehr fuer eine Stufe.
     *
     * Ohne Auskunft die alte Mindestgebuehr - sie deckt jede Regel ab. Mit
     * Auskunft die Stufe des Marktes, aber NIE unter dem Satz je Byte fuer
     * genau diese Groesse: Wer zu wenig zahlt, bleibt liegen.
     */
    fun gebuehrFuer(markt: Markt?, stufe: Stufe, bytes: Int): Long {
        if (markt == null) return Params.MIN_FEE
        val s = markt.stufen[stufe] ?: return Params.MIN_FEE
        val ausStufe = s.jeByte?.let { it * bytes } ?: s.gebuehr
        val boden = markt.mindestJeByte?.let { it * bytes } ?: 0L
        return maxOf(ausStufe, boden)
    }
}
