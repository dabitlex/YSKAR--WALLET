package com.yskar.wallet.kern

/**
 * Konsensparameter von YSKAR.
 *
 * Portiert aus src/kern/params.ts der bestehenden Wallet (MIT, The YSKAR
 * developers). Hier stehen nur die Werte, die die Wallet braucht - die
 * Difficulty- und Blockregeln gehoeren in den Knoten, nicht ins Telefon.
 *
 * ALLES IST GANZZAHLIG. Sobald zwei Knoten sich einig sein muessen, ist
 * Fliesskomma ein Konsensfehler mit Ansage: zwei Umgebungen koennen bei
 * derselben Eingabe unterschiedlich runden, und dann spaltet sich die Kette.
 * Darum in Kotlin durchgaengig Long/BigInteger und nie Double.
 */
object Params {

    const val NETWORK = "yskar-main-1"

    /**
     * Verhindert, dass eine Signatur auf einer anderen Kette gilt.
     * Erstes Feld jedes Sighash.
     */
    val CHAIN_ID: ByteArray by lazy { Hashes.sha256(NETWORK.toByteArray(Charsets.UTF_8)) }

    const val DECIMALS = 8

    /** 1 YSR in kleinsten Einheiten. */
    const val UNIT = 100_000_000L

    // ---------------------------------------------------------------- Adressen

    const val ADDRESS_BYTES = 20
    const val ADDRESS_HRP = "ysr"

    /** Praefixe je Netz. Nur Kodierungsregel, kein Konsens. */
    const val HRP_MAINNET = ADDRESS_HRP
    const val HRP_TESTNET = "tysr"
    const val HRP_REGTEST = "rysr"

    // ------------------------------------------------------------- Ableitung

    /**
     * Coin-Type 9077 ist NICHT bei SLIP-0044 registriert. Fuer eine eigene
     * Kette unkritisch, muss aber bekannt sein, falls YSKAR spaeter in fremde
     * Wallets soll.
     */
    const val COIN_TYPE = 9077
    const val DERIVATION_PURPOSE = 44

    // ------------------------------------------------------------ Fassung 6

    /**
     * Fork-Kennung, zweites Feld jedes Sighash ab Konsensfassung 6: eine
     * Signatur der einen Fassung ist in der anderen nie gueltig.
     */
    const val FORK_ID_V6 = 6

    /** Blockversion ab Fassung 6; darunter 1. */
    const val BLOCK_VERSION_V6 = 6

    /** Aktivierungshoehe der Fassung 6 im Mainnet. */
    const val V6_HEIGHT = 10_000

    /** Der Wert "keine Hoehe", den kein Block erreicht. */
    const val V6_HEIGHT_UNSET = 0xffff_ffffL

    // ---------------------------------------------------------------- Grenzen

    const val MAX_MEMO_BYTES = 32
    const val MAX_BATCH_OUTPUTS = 1_000
    const val MAX_SCRIPT_BYTES = 1_024

    /** Betraege darunter sind ab FEE_V3_HEIGHT unzulaessig. */
    const val DUST_LIMIT = 100L

    const val FEE_V3_HEIGHT = 4_000
    const val MIN_FEE = 100_000L
    const val MIN_FEE_RATE = 1L

    /** Konsens-Untergrenze der Gebuehr fuer eine Transaktion dieser Groesse. */
    fun minFeeAt(height: Int, bytes: Int, feeV3Height: Int = FEE_V3_HEIGHT): Long =
        if (height >= feeV3Height) bytes.toLong() * MIN_FEE_RATE else MIN_FEE

    /** Die Blockversion, die an dieser Hoehe verlangt wird. */
    fun blockVersionAt(height: Int, v6Height: Int = V6_HEIGHT): Int =
        if (height >= v6Height) BLOCK_VERSION_V6 else 1
}
