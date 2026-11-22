package com.yskar.wallet.kern

/**
 * Ausgabe aus einem Skriptkonto -- Transaktionstyp 2 (Konsensfassung 6,
 * Abschnitt 6.5). Neu in 3.1.0 fuer den OTC-Tausch; Typ 1 in [Tx] bleibt
 * unveraendert.
 *
 * Portiert aus dabitlex/YSKAR src/lib/core/tx.ts (scriptSpendSigningBytes,
 * serializeTx, txid). Geprueft gegen test/resources/otc-vergleichswerte.json.
 *
 * Signiert wird der Sighash OHNE Witness; die Signatur steht danach im
 * Witness. AFTER und BEFORE im Skript pruefen gegen validFrom und
 * validUntil DIESER Transaktion, nicht gegen die Blockhoehe.
 */
object SkriptAusgabe {

    const val VERSION = 1

    class Entwurf(
        /** Das Skript; `from` ist sein Hash. */
        val skript: ByteArray,
        /** Empfaenger in Rohform (20 Byte Version 0 oder 33 Byte Version 1). */
        val an: ByteArray,
        val betrag: Long,
        val gebuehr: Long,
        val nonce: Long,
        val validFrom: Long = 0,
        val validUntil: Long = 0,
        val memo: ByteArray = ByteArray(0),
    ) {
        val von: ByteArray get() = Address.programFromScript(skript)

        init {
            require(skript.isNotEmpty() && skript.size <= 0xffff) { "Skript leer oder zu gross" }
            require(memo.size <= Params.MAX_MEMO_BYTES) { "memo zu lang" }
            require(betrag > 0 && gebuehr >= 0 && nonce >= 0) { "Betrag, Gebuehr oder Nonce ungueltig" }
            require(validFrom in 0..0xffff_ffffL && validUntil in 0..0xffff_ffffL) { "Gueltigkeit ausserhalb u32" }
        }
    }

    /** Kopf wie bei jedem Sighash der Fassung 6: u8 typ | u32 FORK_ID_V6 | chainId | u16 version | u8 flags. */
    private fun kopf(chainId: ByteArray): Writer =
        Writer().u8(Tx.TYPE_SCRIPT_SPEND).u32(Params.FORK_ID_V6).bytes(chainId, 32).u16(VERSION).u8(0)

    fun signierBytes(e: Entwurf, chainId: ByteArray = Params.CHAIN_ID): ByteArray {
        val w = kopf(chainId).bytes(e.von, Address.SCRIPT_PROGRAM_BYTES)
        Address.write(w, e.an)
        return w.u64(e.betrag).u64(e.gebuehr).u64(e.nonce)
            .u32(e.validFrom).u32(e.validUntil)
            .u8(e.memo.size).bytes(e.memo)
            .u16(e.skript.size).bytes(e.skript)
            .finish()
    }

    fun sighash(e: Entwurf, chainId: ByteArray = Params.CHAIN_ID): ByteArray =
        Hashes.sha256d(signierBytes(e, chainId))

    /** Leitungsform. CHAIN_ID steckt nur im Sighash. */
    fun serialisieren(e: Entwurf, witness: ByteArray): ByteArray {
        val w = Writer().u16(VERSION).u8(Tx.TYPE_SCRIPT_SPEND).u8(0).bytes(e.von, Address.SCRIPT_PROGRAM_BYTES)
        Address.write(w, e.an)
        return w.u64(e.betrag).u64(e.gebuehr).u64(e.nonce)
            .u32(e.validFrom).u32(e.validUntil)
            .u8(e.memo.size).bytes(e.memo)
            .u16(e.skript.size).bytes(e.skript)
            .bytes(witness)
            .finish()
    }

    fun txid(roh: ByteArray): ByteArray = Hashes.sha256d(roh)
}
