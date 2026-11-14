package com.yskar.wallet.kern

import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer

/**
 * Wallet - aus zwoelf oder vierundzwanzig Woertern wiederherstellbar.
 *
 * Portiert aus src/kern/wallet.ts (MIT, The YSKAR developers).
 *
 * Merkwoerter (BIP39) -> Seed -> Schluessel (SLIP-0010 fuer ed25519).
 *
 * Wer die zwoelf Woerter hat, hat das Guthaben - auf jedem Geraet, in jeder
 * App, auch ohne Server. Die Kehrseite gehoert dazu und muss in der
 * Oberflaeche stehen: Wer die Woerter verliert, verliert das Guthaben
 * endgueltig. Es gibt niemanden, der sie zuruecksetzen kann.
 *
 * Ableitung nach SLIP-0010, weil BIP32 fuer ed25519 nicht funktioniert:
 * Ed25519-Schluessel lassen sich nicht linear addieren, deshalb sind
 * ausschliesslich gehaertete Pfade moeglich.
 *
 *   m/44'/9077'/konto'/0'/index'
 *
 * Ed25519 kommt aus BouncyCastles leichter API, nicht ueber einen
 * JCE-Provider: Androids eigenes Ed25519 gibt es erst ab API 33, minSdk ist
 * 26. Die leichte API registriert nichts global und kann darum auch keinen
 * anderen Provider stoeren.
 */
object Wallet {

    private val ED25519_DOMAIN = "ed25519 seed".toByteArray(Charsets.UTF_8)
    private const val HARDENED = 0x8000_0000L

    data class Keypair(
        /** 32 Byte, Seed des Ed25519-Schluessels. Geheim. */
        val privateKey: ByteArray,
        /** 32 Byte. */
        val publicKey: ByteArray,
        /** bech32m. */
        val address: String,
        /** 20 Byte. */
        val addressRaw: ByteArray,
        val path: String,
    ) {
        /**
         * Schluesselbytes ueberschreiben. Java-Strings und ByteArrays lassen
         * sich nicht sicher aus dem Speicher entfernen, aber ein
         * ueberschriebenes Array verkuerzt das Zeitfenster deutlich.
         */
        fun wipe() {
            privateKey.fill(0)
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Keypair) return false
            return privateKey.contentEquals(other.privateKey) &&
                publicKey.contentEquals(other.publicKey) &&
                address == other.address &&
                addressRaw.contentEquals(other.addressRaw) &&
                path == other.path
        }

        override fun hashCode(): Int {
            var r = privateKey.contentHashCode()
            r = 31 * r + publicKey.contentHashCode()
            r = 31 * r + address.hashCode()
            r = 31 * r + addressRaw.contentHashCode()
            r = 31 * r + path.hashCode()
            return r
        }

        /** Nie den Schluessel in Logs oder Fehlermeldungen. */
        override fun toString(): String = "Keypair($address, $path)"
    }

    private class Node(val key: ByteArray, val chainCode: ByteArray)

    private fun masterNode(seed: ByteArray): Node {
        val i = Hashes.hmacSha512(ED25519_DOMAIN, seed)
        return Node(i.copyOfRange(0, 32), i.copyOfRange(32, 64))
    }

    /** Nur gehaertete Ableitung - SLIP-0010 laesst fuer ed25519 nichts anderes zu. */
    private fun derive(parent: Node, index: Int): Node {
        val data = ByteArray(1 + 32 + 4)
        data[0] = 0
        parent.key.copyInto(data, 1)

        // Big-Endian, und der gehaertete Offset wird in Long gerechnet:
        // 0x80000000 + index laeuft als Int ins Negative.
        val idx = (index.toLong() and 0xffff_ffffL) + HARDENED
        data[33] = ((idx ushr 24) and 0xff).toByte()
        data[34] = ((idx ushr 16) and 0xff).toByte()
        data[35] = ((idx ushr 8) and 0xff).toByte()
        data[36] = (idx and 0xff).toByte()

        val i = Hashes.hmacSha512(parent.chainCode, data)
        return Node(i.copyOfRange(0, 32), i.copyOfRange(32, 64))
    }

    fun derivationPath(account: Int = 0, index: Int = 0): String =
        "m/${Params.DERIVATION_PURPOSE}'/${Params.COIN_TYPE}'/$account'/0'/$index'"

    fun publicKeyFromPrivate(privateKey: ByteArray): ByteArray {
        require(privateKey.size == 32) { "Schluessel muss 32 Byte sein" }
        return Ed25519PrivateKeyParameters(privateKey, 0).generatePublicKey().encoded
    }

    fun fromSeed(seed: ByteArray, account: Int = 0, index: Int = 0): Keypair {
        var node = masterNode(seed)
        for (level in intArrayOf(Params.DERIVATION_PURPOSE, Params.COIN_TYPE, account, 0, index)) {
            node = derive(node, level)
        }
        val privateKey = node.key
        val publicKey = publicKeyFromPrivate(privateKey)
        return Keypair(
            privateKey = privateKey,
            publicKey = publicKey,
            address = Address.forPublicKey(publicKey),
            addressRaw = Address.fromPublicKey(publicKey),
            path = derivationPath(account, index),
        )
    }

    fun fromMnemonic(
        mnemonic: String,
        passphrase: String = "",
        account: Int = 0,
        index: Int = 0,
    ): Keypair = fromSeed(Bip39.toSeed(mnemonic, passphrase), account, index)

    fun sign(message: ByteArray, privateKey: ByteArray): ByteArray {
        require(privateKey.size == 32) { "Schluessel muss 32 Byte sein" }
        val signer = Ed25519Signer()
        signer.init(true, Ed25519PrivateKeyParameters(privateKey, 0))
        signer.update(message, 0, message.size)
        return signer.generateSignature()
    }

    /**
     * Reine Ed25519-Pruefung nach RFC 8032.
     *
     * ACHTUNG: Das ist NICHT die Konsensregel. Der Knoten prueft nach
     * sigcheck.ts zusaetzliche Bedingungen (siehe CONSENSUS_V6.md 7.8). Diese
     * Funktion taugt fuer eigene Signaturen, nicht zum Bewerten fremder.
     */
    fun verifyRaw(signature: ByteArray, message: ByteArray, publicKey: ByteArray): Boolean {
        if (signature.size != 64 || publicKey.size != 32) return false
        return runCatching {
            val verifier = Ed25519Signer()
            verifier.init(false, Ed25519PublicKeyParameters(publicKey, 0))
            verifier.update(message, 0, message.size)
            verifier.verifySignature(signature)
        }.getOrDefault(false)
    }
}
