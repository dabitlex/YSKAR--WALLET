package com.yskar.wallet.kern

/**
 * Transaktionen - Kontomodell, nicht UTXO.
 *
 * Portiert aus src/kern/tx.ts (MIT, The YSKAR developers).
 *
 * Fuer eine Wallet auf dem Handy ist das Kontomodell deutlich einfacher: kein
 * Auswaehlen von Eingaengen, kleinere Transaktionen, verstaendliche Salden.
 * Wiederholungsschutz ueber eine fortlaufende Nonce je Absender.
 *
 * Die Signatur deckt CHAIN_ID mit ab. Eine auf dem Testnet gueltige
 * Transaktion ist damit auf dem Mainnet wertlos.
 *
 * UMFANG DIESES PORTS. Hier steht ausschliesslich Typ 1, die Ueberweisung,
 * in den Fassungen 1 und 2 - das, was eine Wallet selbst unterschreibt und
 * einreicht. NICHT portiert sind:
 *
 *   Typ 0  Coinbase          baut der Miner, nie die Wallet
 *   Typ 2  Skript-Ausgabe    braucht den Skript-Interpreter (script.ts)
 *   Typ 3  Sammelzahlung     mehrere Empfaenger in einer Transaktion
 *
 * Sie fehlen absichtlich und nicht aus Versehen: Was die Wallet nicht
 * unterschreibt, muss sie nicht Byte fuer Byte nachbauen koennen. Den
 * Verlauf liest sie als JSON vom Spiegel, nicht als Rohbytes.
 *
 * Auf der Leitung ist die Reihenfolge `u16 version | u8 typ`. Im Sighash der
 * Fassung 2 steht der Typ ZUERST, dann die Fork-Kennung, dann das Netz.
 * Diese Umstellung ist Absicht und keine Unachtsamkeit der Vorlage.
 */
object Tx {

    const val TYPE_COINBASE = 0
    const val TYPE_TRANSFER = 1
    const val TYPE_SCRIPT_SPEND = 2
    const val TYPE_BATCH = 3

    /** Fassung einer Ueberweisung bis Konsensfassung 6. */
    const val TRANSFER_V1 = 1

    /** Fassung einer Ueberweisung ab Konsensfassung 6. */
    const val TRANSFER_V2 = 2

    /** Groesse einer Ueberweisung der Fassung 1 auf der Leitung: 168 Byte plus Notiz. */
    const val TRANSFER_BASE_BYTES =
        2 + 1 + Params.ADDRESS_BYTES * 2 + 8 + 8 + 8 + 4 + 1 + 32 + 64

    /** Fassung 2 mit Empfaenger der Version 0: 174 Byte plus Notiz. */
    const val TRANSFER_V2_BASE_BYTES = TRANSFER_BASE_BYTES + 1 + 4 + 1

    /**
     * So viele Bloecke vor der Aktivierungshoehe sendet eine Wallet nichts
     * mehr: Eine Ueberweisung der Fassung 1 faellt an der Grenze aus dem
     * Mempool, eine der Fassung 2 nimmt davor kein Knoten an.
     */
    const val WECHSEL_PAUSE_BLOECKE = 3

    data class Transfer(
        val version: Int,
        /** Ab Fassung 2; muss 0 sein. */
        val flags: Int = 0,
        /** 20 Byte, Version 0. */
        val from: ByteArray,
        /** Rohform: 20 Byte (Version 0) oder 33 Byte (Version 1). Fassung 1: nur 20. */
        val to: ByteArray,
        val amount: Long,
        val fee: Long,
        val nonce: Long,
        /** Ab Fassung 2: erste Hoehe, 0 = keine Untergrenze. */
        val validFrom: Long = 0,
        /** Blockhoehe, 0 = unbegrenzt. */
        val validUntil: Long = 0,
        /** 0..32 Byte. */
        val memo: ByteArray = ByteArray(0),
        /** 32 Byte. */
        val publicKey: ByteArray,
        /** 64 Byte. */
        val signature: ByteArray,
    ) {
        val type: Int get() = TYPE_TRANSFER

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Transfer) return false
            return version == other.version &&
                flags == other.flags &&
                from.contentEquals(other.from) &&
                to.contentEquals(other.to) &&
                amount == other.amount &&
                fee == other.fee &&
                nonce == other.nonce &&
                validFrom == other.validFrom &&
                validUntil == other.validUntil &&
                memo.contentEquals(other.memo) &&
                publicKey.contentEquals(other.publicKey) &&
                signature.contentEquals(other.signature)
        }

        override fun hashCode(): Int {
            var r = version
            r = 31 * r + flags
            r = 31 * r + from.contentHashCode()
            r = 31 * r + to.contentHashCode()
            r = 31 * r + amount.hashCode()
            r = 31 * r + fee.hashCode()
            r = 31 * r + nonce.hashCode()
            r = 31 * r + validFrom.hashCode()
            r = 31 * r + validUntil.hashCode()
            r = 31 * r + memo.contentHashCode()
            r = 31 * r + publicKey.contentHashCode()
            r = 31 * r + signature.contentHashCode()
            return r
        }
    }

    // ------------------------------------------------------------- Sighash

    /**
     * Gemeinsamer Kopf jedes Sighash der Fassung 6:
     *   u8 typ | u32 FORK_ID_V6 | chainId(32) | u16 version | u8 flags
     *
     * Typ zuerst (er legt den Aufbau des Rests fest), dann die Fork-Kennung
     * (trennt die Fassungen), dann das Netz.
     */
    private fun v6Prefix(type: Int, version: Int, flags: Int, chainId: ByteArray): Writer =
        Writer().u8(type).u32(Params.FORK_ID_V6).bytes(chainId, 32).u16(version).u8(flags)

    /**
     * Die signierten Felder einer Ueberweisung. Enthaelt CHAIN_ID, aber
     * weder den oeffentlichen Schluessel noch die Signatur.
     */
    fun signingBytes(
        version: Int,
        flags: Int,
        from: ByteArray,
        to: ByteArray,
        amount: Long,
        fee: Long,
        nonce: Long,
        validFrom: Long,
        validUntil: Long,
        memo: ByteArray,
        chainId: ByteArray = Params.CHAIN_ID,
    ): ByteArray {
        if (memo.size > Params.MAX_MEMO_BYTES) throw IllegalArgumentException("memo zu lang")

        if (version == TRANSFER_V1) {
            return Writer()
                .bytes(chainId, 32)
                .u16(version)
                .u8(TYPE_TRANSFER)
                .bytes(from, Params.ADDRESS_BYTES)
                .bytes(to, Params.ADDRESS_BYTES)
                .u64(amount)
                .u64(fee)
                .u64(nonce)
                .u32(validUntil)
                .u8(memo.size)
                .bytes(memo)
                .finish()
        }

        val w = v6Prefix(TYPE_TRANSFER, version, flags, chainId)
            .bytes(from, Params.ADDRESS_BYTES)
        Address.write(w, to)
        return w.u64(amount).u64(fee).u64(nonce)
            .u32(validFrom).u32(validUntil)
            .u8(memo.size).bytes(memo)
            .finish()
    }

    fun signingBytes(t: Transfer, chainId: ByteArray = Params.CHAIN_ID): ByteArray =
        signingBytes(
            t.version, t.flags, t.from, t.to, t.amount, t.fee, t.nonce,
            t.validFrom, t.validUntil, t.memo, chainId,
        )

    fun sighash(t: Transfer, chainId: ByteArray = Params.CHAIN_ID): ByteArray =
        Hashes.sha256d(signingBytes(t, chainId))

    // ------------------------------------------------------------- Leitung

    /** Uebertragungsformat. CHAIN_ID steckt nur im Sighash, nicht auf der Leitung. */
    fun serialize(t: Transfer): ByteArray {
        val w = Writer().u16(t.version).u8(TYPE_TRANSFER)

        if (t.version == TRANSFER_V1) {
            return w
                .bytes(t.from, Params.ADDRESS_BYTES)
                .bytes(t.to, Params.ADDRESS_BYTES)
                .u64(t.amount).u64(t.fee).u64(t.nonce).u32(t.validUntil)
                .u8(t.memo.size).bytes(t.memo)
                .bytes(t.publicKey, 32).bytes(t.signature, 64)
                .finish()
        }

        w.u8(t.flags).bytes(t.from, Params.ADDRESS_BYTES)
        Address.write(w, t.to)
        return w.u64(t.amount).u64(t.fee).u64(t.nonce)
            .u32(t.validFrom).u32(t.validUntil)
            .u8(t.memo.size).bytes(t.memo)
            .bytes(t.publicKey, 32).bytes(t.signature, 64)
            .finish()
    }

    /**
     * Eine Ueberweisung aus Rohbytes lesen.
     *
     * Wirft bei jedem anderen Typ. Der Leser der Vorlage kennt alle vier
     * Typen; dieser Port bewusst nur Typ 1 - lieber eine klare Ausnahme als
     * ein halb gelesenes Feld.
     */
    fun deserializeTransfer(bytes: ByteArray): Transfer {
        val r = Reader(bytes)
        val version = r.u16()
        val type = r.u8()
        if (type != TYPE_TRANSFER) {
            throw IllegalArgumentException(
                "Typ $type ist in diesem Port nicht gelesen - nur Ueberweisungen (Typ 1)"
            )
        }

        if (version == TRANSFER_V1) {
            val from = r.bytes(Params.ADDRESS_BYTES)
            val to = r.bytes(Params.ADDRESS_BYTES)
            val amount = r.u64()
            val fee = r.u64()
            val nonce = r.u64()
            val validUntil = r.u32()
            val memo = r.bytes(r.u8())
            val publicKey = r.bytes(32)
            val signature = r.bytes(64)
            return Transfer(
                version = version, flags = 0, from = from, to = to,
                amount = amount, fee = fee, nonce = nonce,
                validFrom = 0, validUntil = validUntil, memo = memo,
                publicKey = publicKey, signature = signature,
            )
        }

        val flags = r.u8()
        val from = r.bytes(Params.ADDRESS_BYTES)
        val to = Address.read(r)
        val amount = r.u64()
        val fee = r.u64()
        val nonce = r.u64()
        val validFrom = r.u32()
        val validUntil = r.u32()
        val memo = r.bytes(r.u8())
        val publicKey = r.bytes(32)
        val signature = r.bytes(64)
        return Transfer(
            version = version, flags = flags, from = from, to = to,
            amount = amount, fee = fee, nonce = nonce,
            validFrom = validFrom, validUntil = validUntil, memo = memo,
            publicKey = publicKey, signature = signature,
        )
    }

    fun txid(t: Transfer): ByteArray = Hashes.sha256d(serialize(t))

    fun txidHex(t: Transfer): String = Hex.toHex(txid(t))

    /** Groesse auf der Leitung - Grundlage der Mindestgebuehr. */
    fun txBytes(t: Transfer): Int = serialize(t).size

    // ---------------------------------------------------------------- Groessen

    /**
     * Groesse einer gewoehnlichen Ueberweisung an dieser Hoehe - fuer die
     * Gebuehrenrechnung.
     *
     * Ab Fassung 6 steht der Empfaenger als `u8 version | programm`: 21 Byte
     * fuer Version 0, 33 Byte fuer Version 1 (`ysr1p…`). Eine Ueberweisung
     * an ein Skript-Konto ist also 12 Byte groesser - wer stur mit 174 Byte
     * rechnet, zahlt zu wenig Gebuehr und bleibt liegen.
     */
    fun transferBytesAt(
        height: Int,
        memoLength: Int,
        v6Height: Int = Params.V6_HEIGHT,
        empfaengerBytes: Int = Params.ADDRESS_BYTES,
    ): Int {
        if (height < v6Height) return TRANSFER_BASE_BYTES + memoLength
        val leitung =
            if (empfaengerBytes == Params.ADDRESS_BYTES) Params.ADDRESS_BYTES + 1
            else empfaengerBytes
        return TRANSFER_V2_BASE_BYTES + (leitung - (Params.ADDRESS_BYTES + 1)) + memoLength
    }

    /**
     * Welche Fassung eine Wallet fuer eine Ueberweisung an `height` (dem
     * naechsten Block) waehlt. Keine Uebergangsfrist: Fassung 1 ist ab der
     * Hoehe ungueltig, Fassung 2 davor.
     */
    fun transferVersionAt(height: Int, v6Height: Int = Params.V6_HEIGHT): Int =
        if (height >= v6Height) TRANSFER_V2 else TRANSFER_V1

    /** Liegt `height` (der naechste Block) in der Pause vor der Aktivierung? */
    fun sendepause(height: Int, v6Height: Int = Params.V6_HEIGHT): Boolean =
        height < v6Height && height + WECHSEL_PAUSE_BLOECKE >= v6Height

    // ------------------------------------------------------------------ Bauen

    /**
     * Eine Ueberweisung bauen und unterschreiben.
     *
     * Die Signatur entsteht ueber den Sighash, nicht ueber die Leitungsform:
     * Was hier unterschrieben wird, enthaelt die Chain-ID und keine
     * Signatur. Ed25519 ist deterministisch (RFC 8032) - dieselben Felder
     * und derselbe Schluessel ergeben immer dieselben 64 Byte.
     */
    fun buildTransfer(
        from: ByteArray,
        to: ByteArray,
        amount: Long,
        fee: Long,
        nonce: Long,
        publicKey: ByteArray,
        privateKey: ByteArray,
        validFrom: Long = 0,
        validUntil: Long = 0,
        memo: ByteArray = ByteArray(0),
        version: Int = TRANSFER_V1,
        chainId: ByteArray = Params.CHAIN_ID,
    ): Transfer {
        require(amount > 0) { "Betrag muss groesser als 0 sein" }
        require(fee >= 0) { "Gebuehr darf nicht negativ sein" }
        require(nonce >= 0) { "Nonce darf nicht negativ sein" }
        require(memo.size <= Params.MAX_MEMO_BYTES) { "memo zu lang" }
        require(Address.isRaw(to)) { "Empfaenger ist keine gueltige Rohadresse" }
        if (version == TRANSFER_V1) {
            require(to.size == Params.ADDRESS_BYTES) {
                "Fassung 1 kann nur an eine Adresse der Version 0 zahlen"
            }
        }

        val flags = 0
        val vf = if (version == TRANSFER_V1) 0L else validFrom

        val signature = Wallet.sign(
            Hashes.sha256d(
                signingBytes(
                    version, flags, from, to, amount, fee, nonce,
                    vf, validUntil, memo, chainId,
                )
            ),
            privateKey,
        )

        return Transfer(
            version = version, flags = flags, from = from, to = to,
            amount = amount, fee = fee, nonce = nonce,
            validFrom = vf, validUntil = validUntil, memo = memo,
            publicKey = publicKey, signature = signature,
        )
    }

    /**
     * Die eigene Signatur nachrechnen.
     *
     * ACHTUNG - DAS IST NICHT DIE KONSENSREGEL. Der Knoten prueft nach
     * ZIP-215 (CONSENSUS_V6.md 7.8): nicht-kanonische Punkte erlaubt,
     * S < Gruppenordnung, Gleichung MIT Kofaktor. BouncyCastle prueft nach
     * RFC 8032, also ohne Kofaktor und mit strenger Kodierung, und
     * entscheidet in Grenzfaellen ANDERS.
     *
     * Fuer eigene, frisch erzeugte Signaturen stimmen beide Regeln ueberein -
     * dafuer ist diese Funktion gedacht. Fremde Signaturen darf sie NICHT
     * bewerten: Dafuer braeuchte es einen ZIP-215-Pruefer, und ohne ihn
     * gehoert diese Entscheidung zum Knoten, nicht ins Telefon.
     */
    fun verifyOwnSignature(t: Transfer, chainId: ByteArray = Params.CHAIN_ID): Boolean =
        Wallet.verifyRaw(t.signature, sighash(t, chainId), t.publicKey)
}
