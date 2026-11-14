package com.yskar.wallet.netz

/**
 * Wo steht eine eben gesendete Zahlung?
 *
 * Portiert aus src/wallet/sendestatus.ts (MIT, The YSKAR developers).
 *
 * Fünf Schritte, und jeder gilt erst, wenn eine Antwort ihn belegt:
 *
 *   1 lokal signiert         die App hat sie auf dem Gerät gebaut und signiert
 *   2 vom Knoten angenommen  der Knoten hat "angenommen" geantwortet
 *   3 im Mempool             der Knoten führt sie in seiner Liste
 *   4 in einem Block         der Verlauf nennt sie mit einer Höhe
 *   5 weitere Bestätigungen  ab zwei (der Block selbst zählt als erste)
 *
 * EINE ERFOLGREICHE ANTWORT AUF DAS EINREICHEN IST SCHRITT 2, NICHT MEHR.
 * "Endgültig" gibt es nicht: Das Netz hat keine Checkpoints und keine
 * Grenze für Reorganisationen. Wer in der Oberfläche "erledigt" schreibt,
 * sobald der Knoten genickt hat, lügt den Nutzer an.
 */
object Sendestatus {

    enum class Teil { ERLEDIGT, WARTET, OFFEN }

    data class Netzbild(
        /** true/false nach der Liste des Knotens; null = nicht abrufbar. */
        val imMempool: Boolean?,
        /** Höhe des Blocks; null = in keinem bekannten Block. */
        val hoehe: Long?,
        /** Bestätigungen; 0 ohne Block. */
        val bestaetigungen: Int,
    ) {
        /** Steht sie irgendwo, wo der Knoten oder die Kette sie zeigt? */
        val imNetzGesehen: Boolean get() = imMempool == true || hoehe != null
    }

    fun netzbild(konto: Konto?, spitze: Long?, txid: String): Netzbild {
        val eintrag = konto?.verlauf?.firstOrNull { it.txid == txid }
        val hoehe = eintrag?.height
        val bestaetigungen = when {
            hoehe == null -> 0
            spitze == null || spitze < hoehe -> 1
            else -> (spitze - hoehe + 1).toInt()
        }
        val imMempool =
            if (konto == null || konto.wartendQuelle != "fullnode") null
            else konto.wartend.any { it.txid == txid }
        return Netzbild(imMempool, hoehe, bestaetigungen)
    }

    data class Schritte(
        val signiert: Teil,
        val angenommen: Teil,
        val mempool: Teil,
        val block: Teil,
        val weitere: Teil,
    )

    /**
     * Die fünf Schritte nach dem, was belegt ist.
     *
     * [gesehen]: Die App hat sie in dieser Sitzung schon einmal in der Liste
     * des Knotens gesehen. Ohne das würde der Schritt zurückspringen, sobald
     * sie aus dem Mempool in einen Block wandert.
     */
    fun schritte(n: Netzbild, gesehen: Boolean): Schritte {
        val imBlock = n.hoehe != null
        val mempool = when {
            imBlock || n.imMempool == true -> Teil.ERLEDIGT
            gesehen && n.imMempool == null -> Teil.ERLEDIGT
            else -> Teil.WARTET
        }
        return Schritte(
            signiert = Teil.ERLEDIGT,
            angenommen = Teil.ERLEDIGT,
            mempool = mempool,
            block = when {
                imBlock -> Teil.ERLEDIGT
                mempool == Teil.ERLEDIGT -> Teil.WARTET
                else -> Teil.OFFEN
            },
            weitere = when {
                n.bestaetigungen >= 2 -> Teil.ERLEDIGT
                imBlock -> Teil.WARTET
                else -> Teil.OFFEN
            },
        )
    }

    /** Beschriftungen für die Oberfläche, in der Reihenfolge der Schritte. */
    val namen = listOf(
        "auf dem Gerät unterschrieben",
        "vom Knoten angenommen",
        "in der Warteliste des Knotens",
        "in einem Block",
        "mindestens zwei Bestätigungen",
    )

    fun alsListe(s: Schritte): List<Teil> =
        listOf(s.signiert, s.angenommen, s.mempool, s.block, s.weitere)
}
