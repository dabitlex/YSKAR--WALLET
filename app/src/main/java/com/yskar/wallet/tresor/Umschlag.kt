package com.yskar.wallet.tresor

import com.yskar.wallet.kern.Bip39
import com.yskar.wallet.kern.Hashes
import java.security.GeneralSecurityException
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Der Umschlag - Verschluesseln und Entschluesseln ohne Android.
 *
 * Diese Datei kennt keine Android-API und laeuft darum im JVM-Unittest.
 * Alles Geraetegebundene (Keystore, Biometrie, Dateien) steht in Tresor.kt.
 *
 * ABLEITUNG DES TRESORSCHLUESSELS
 *
 *     basis   = PBKDF2-HMAC-SHA512(pin, salz, runden, 32)
 *     pfeffer = 32 Byte aus dem Android Keystore (siehe Pfeffer.kt)
 *     VK      = HKDF-SHA256(ikm = basis, salt = pfeffer, info = "yskar-tresor-v1")
 *
 *     geheim  = AES-256-GCM(VK, iv, woerter), AAD = "yskar-tresor-v1"
 *
 * WARUM DER PFEFFER. Die Vorlage schreibt selbst, wie weit eine PIN traegt:
 * "Sechs Ziffern sind eine Million Moeglichkeiten. Wer den verschluesselten
 * Tresor in die Hand bekommt, probiert sie in Stunden durch." Das stimmt,
 * solange der Tresor allein aus der PIN aufgeht.
 *
 * Hier geht er das nicht. Der Pfeffer ist ein Schluessel im Android
 * Keystore, der das Geraet nicht verlaesst und auf moderner Hardware im
 * Sicherheitsprozessor liegt. Ohne genau dieses Telefon ist die kopierte
 * Tresordatei kein Raetsel, sondern Rauschen - eine Million Moeglichkeiten
 * durchzuprobieren hilft nicht, weil der zweite Teil des Schluessels fehlt
 * und nicht erraten werden kann.
 *
 * Das verschiebt den Angriff dorthin, wo er hingehoert: an das entsperrte
 * Geraet in fremder Hand. Dagegen hilft die PIN, dagegen hilft die Bremse.
 *
 * WAS ES NICHT LEISTET - ohne Schoenrechnerei: Wer die App auf einem
 * gerooteten, entsperrten Geraet fernsteuert, kann den Keystore benutzen
 * wie die App selbst. Der eigentliche Schutz bleiben die aufgeschriebenen
 * zwoelf Woerter.
 */
object Umschlag {

    const val VERSION = 1

    /** Vorgabe wie in der Vorlage. Bewusst hoch - das Entsperren darf spuerbar dauern. */
    const val RUNDEN = 400_000

    const val SALZ_BYTES = 16
    const val IV_BYTES = 12
    private const val TAG_BITS = 128
    private const val SCHLUESSEL_BYTES = 32

    private val INFO = "yskar-tresor-v$VERSION".toByteArray(Charsets.UTF_8)

    /** Bindet den Geheimtext an seinen Zweck: anderswo entschluesselt er nicht. */
    private val AAD = "yskar-tresor-v$VERSION".toByteArray(Charsets.UTF_8)

    /**
     * HKDF nach RFC 5869 mit SHA-256, auf die hier gebrauchte Laenge
     * beschraenkt (ein Block).
     */
    fun hkdfSha256(ikm: ByteArray, salt: ByteArray, info: ByteArray, len: Int = 32): ByteArray {
        require(len in 1..32) { "diese Umsetzung liefert hoechstens 32 Byte" }
        val prk = Hashes.hmacSha256(salt, ikm)
        val t = Hashes.hmacSha256(prk, info + byteArrayOf(0x01))
        return t.copyOfRange(0, len)
    }

    /**
     * Der Tresorschluessel.
     *
     * Teuer mit Absicht: PBKDF2 mit 400.000 Runden braucht auf einem
     * Telefon rund eine halbe Sekunde. Das ist beim Entsperren ertraeglich
     * und beim Durchprobieren nicht.
     */
    fun tresorSchluessel(
        pin: CharArray,
        salz: ByteArray,
        pfeffer: ByteArray,
        runden: Int = RUNDEN,
    ): ByteArray {
        require(salz.size == SALZ_BYTES) { "Salz muss $SALZ_BYTES Byte sein" }
        require(pfeffer.size == SCHLUESSEL_BYTES) { "Pfeffer muss $SCHLUESSEL_BYTES Byte sein" }
        require(runden >= 100_000) { "zu wenige Runden: $runden" }

        val pinBytes = zuBytes(pin)
        try {
            val basis = Bip39.pbkdf2HmacSha512(pinBytes, salz, runden, SCHLUESSEL_BYTES)
            try {
                return hkdfSha256(basis, pfeffer, INFO, SCHLUESSEL_BYTES)
            } finally {
                basis.fill(0)
            }
        } finally {
            pinBytes.fill(0)
        }
    }

    /** UTF-8, ohne den Umweg ueber einen String - der liesse sich nicht loeschen. */
    private fun zuBytes(pin: CharArray): ByteArray {
        val bb = Charsets.UTF_8.encode(java.nio.CharBuffer.wrap(pin))
        val out = ByteArray(bb.remaining())
        bb.get(out)
        if (bb.hasArray()) bb.array().fill(0)
        return out
    }

    fun versiegeln(schluessel: ByteArray, iv: ByteArray, klartext: ByteArray): ByteArray {
        require(schluessel.size == SCHLUESSEL_BYTES) { "Schluessel muss 32 Byte sein" }
        require(iv.size == IV_BYTES) { "IV muss $IV_BYTES Byte sein" }
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, SecretKeySpec(schluessel, "AES"), GCMParameterSpec(TAG_BITS, iv))
        c.updateAAD(AAD)
        return c.doFinal(klartext)
    }

    /**
     * Oeffnen. Liefert null bei falscher PIN oder verfaelschtem Geheimtext -
     * AES-GCM prueft die Echtheit mit, beides ist hier derselbe Fall.
     *
     * Bewusst ByteArray und nicht String: Ein String laesst sich in Java
     * nicht aus dem Speicher loeschen, ein ByteArray schon.
     */
    fun oeffnen(schluessel: ByteArray, iv: ByteArray, geheim: ByteArray): ByteArray? {
        if (schluessel.size != SCHLUESSEL_BYTES || iv.size != IV_BYTES) return null
        return try {
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(
                Cipher.DECRYPT_MODE,
                SecretKeySpec(schluessel, "AES"),
                GCMParameterSpec(TAG_BITS, iv),
            )
            c.updateAAD(AAD)
            c.doFinal(geheim)
        } catch (e: GeneralSecurityException) {
            null
        }
    }
}
