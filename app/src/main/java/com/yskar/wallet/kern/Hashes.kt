package com.yskar.wallet.kern

import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Hashes.
 *
 * Portiert aus src/kern/hash.ts (MIT, The YSKAR developers).
 *
 * Bewusst nur java.security und javax.crypto, keine Android-API: so laeuft
 * dieser Code unveraendert im JVM-Unittest und auf dem Geraet. Jedes
 * MessageDigest wird frisch geholt - die Instanzen sind nicht threadsicher.
 */
object Hashes {

    fun sha256(data: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(data)

    fun sha256d(data: ByteArray): ByteArray =
        sha256(sha256(data))

    fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(data)
    }

    fun hmacSha512(key: ByteArray, data: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA512")
        mac.init(SecretKeySpec(key, "HmacSHA512"))
        return mac.doFinal(data)
    }

    /**
     * Merkle-Baum mit Bereichstrennung.
     *
     *   Blatt  = sha256d(0x00 || wert)
     *   Knoten = sha256d(0x01 || links || rechts)
     *
     * Das 0x00/0x01-Praefix verhindert, dass ein innerer Knoten als Blatt
     * ausgegeben werden kann.
     *
     * Bei ungerader Anzahl wird der letzte Knoten UNVERAENDERT hochgereicht,
     * statt ihn zu verdoppeln. Eine Verdopplung erlaubte zwei verschiedene
     * Transaktionslisten mit demselben Root (CVE-2012-2459).
     */
    fun merkleRoot(leaves: List<ByteArray>): ByteArray {
        if (leaves.isEmpty()) return ByteArray(32)

        var level = leaves.map { leaf ->
            val b = ByteArray(1 + leaf.size)
            b[0] = 0x00
            leaf.copyInto(b, 1)
            sha256d(b)
        }

        while (level.size > 1) {
            val next = ArrayList<ByteArray>((level.size + 1) / 2)
            var i = 0
            while (i < level.size) {
                if (i + 1 == level.size) {
                    next.add(level[i])
                } else {
                    val b = ByteArray(1 + 64)
                    b[0] = 0x01
                    level[i].copyInto(b, 1)
                    level[i + 1].copyInto(b, 33)
                    next.add(sha256d(b))
                }
                i += 2
            }
            level = next
        }
        return level[0]
    }
}
