package com.yskar.wallet.netz

/**
 * Was der Spiegel liefert.
 *
 * Nachgebaut aus src/netz/api.ts (MIT, The YSKAR developers) - aber nach
 * den ECHTEN Antworten der laufenden API, nicht nach den TypeScript-Typen.
 * Die beiden weichen an mehreren Stellen voneinander ab, siehe Spiegel.kt.
 *
 * Betraege sind hier ueberall `Long` in kleinsten Einheiten. Die API
 * schickt sie teils als Zahl, teils als Zeichenkette; das glaettet der
 * Parser, damit es in der App nur noch eine Form gibt.
 */

enum class Stufe { LANGSAM, NORMAL, SCHNELL;

    val schluessel: String
        get() = when (this) {
            LANGSAM -> "langsam"
            NORMAL -> "normal"
            SCHNELL -> "schnell"
        }
}

/** Art eines Verlaufseintrags. */
enum class Art { REWARD, POOL, EIN, AUS, UNBEKANNT;

    companion object {
        fun aus(text: String?): Art = when (text) {
            "reward" -> REWARD
            "pool" -> POOL
            "in" -> EIN
            "out" -> AUS
            else -> UNBEKANNT
        }
    }

    /** Fliesst Geld auf das Konto? */
    val eingehend: Boolean get() = this == REWARD || this == POOL || this == EIN
}

data class VerlaufEintrag(
    val txid: String,
    val height: Long,
    val idx: Int,
    /** Unixsekunden; null, wenn der Spiegel keine Zeit kennt. */
    val zeit: Long?,
    val art: Art,
    val gegenseite: String?,
    /** Nur bei Pool-Anteilen. */
    val anteile: Int?,
    val betrag: Long,
    val gebuehr: Long,
    /** Schon aus Hex gelesen; leer, wenn keine oder unlesbar. */
    val notiz: String,
)

/** Eine Zahlung, die noch in keinem Block steht. */
data class Wartend(
    val txid: String,
    val art: Art,
    val von: String,
    /** Fehlt bei einer Sammelzahlung (Typ 3). */
    val an: String?,
    val betrag: Long,
    val gebuehr: Long,
    val nonce: Long,
    val notiz: String,
)

data class Konto(
    val adresse: String,
    val guthaben: Long,
    val nonce: Long,
    val naechsteNonce: Long,
    val wartend: List<Wartend>,
    /** "fullnode" oder "unavailable" - sagt, ob Wartendes verlaesslich ist. */
    val wartendQuelle: String,
    val verlauf: List<VerlaufEintrag>,
    /** Zeiger auf die naechste Seite, null = Ende. */
    val verlaufWeiter: String?,
)

data class VerlaufSeite(
    val eintraege: List<VerlaufEintrag>,
    val weiter: String?,
)

data class Kopf(
    /** Hoehe des letzten Blocks; null, wenn der Spiegel sie nicht nennt. */
    val hoehe: Long?,
    val mempool: Int,
)

data class StufenPreis(
    val gebuehr: Long,
    val block: Int,
    /** Satz je Byte, wenn der Knoten einen nennt. */
    val jeByte: Long?,
)

/** Die Gebuehrenlage fuer den naechsten Block. */
data class Markt(
    val mindestGebuehr: Long,
    val mindestJeByte: Long?,
    val wartend: Int,
    val andrang: Boolean,
    val stufen: Map<Stufe, StufenPreis>,
    /** Hoehe des naechsten Blocks. */
    val hoehe: Long?,
    /** Fassung der Ueberweisung fuer den naechsten Block: 1 oder 2. */
    val fassung: Int?,
    /** Groesse einer Ueberweisung ohne Notiz an diesem Block. */
    val basisBytes: Int?,
    /** Die Bloecke vor der Aktivierung von Fassung 6: nichts einreichen. */
    val sendepause: Boolean,
)

/** Antwort des Full Node auf eine eingereichte Ueberweisung. */
sealed interface SendeErgebnis {
    data class Angenommen(val txid: String?) : SendeErgebnis
    data class Abgelehnt(val grund: String, val detail: String?) : SendeErgebnis
}

/**
 * Fehler beim Reden mit dem Netz.
 *
 * Zwei Arten, weil die Oberflaeche sie verschieden behandeln muss:
 * OFFLINE heisst "spaeter nochmal", ANTWORT heisst "der Server hat etwas
 * gesagt, das wir nicht verstehen" - das ist ein Fehler, den man sehen soll.
 */
class NetzFehler(
    val ursache: Ursache,
    val status: Int = 0,
    meldung: String = "",
) : Exception(
    meldung.ifEmpty {
        if (ursache == Ursache.OFFLINE) "keine Verbindung" else "HTTP $status"
    }
) {
    /** Nicht "Art" - der Name gehoert oben schon zur Art eines Eintrags. */
    enum class Ursache { OFFLINE, ANTWORT }

    val offline: Boolean get() = ursache == Ursache.OFFLINE
}
