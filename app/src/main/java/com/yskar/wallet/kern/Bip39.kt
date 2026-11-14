package com.yskar.wallet.kern

import java.security.SecureRandom
import java.text.Normalizer

/**
 * BIP39 - Merkwoerter.
 *
 * Ersetzt @scure/bip39 aus der TypeScript-Wallet. Die englische Wortliste
 * liegt als Ressource bei und wird gegen ihren SHA-256 geprueft, damit eine
 * verfaelschte Liste nicht unbemerkt andere Adressen erzeugt.
 *
 * PBKDF2 ist hier selbst ausgeschrieben statt ueber SecretKeyFactory geholt.
 * Grund: Die Verfuegbarkeit von "PBKDF2WithHmacSHA512" haengt bei Android
 * vom Provider und der API-Stufe ab, und ein stiller Rueckfall auf SHA-1
 * waere ein Desaster - er erzeugte eine voellig andere Wallet. HMAC-SHA512
 * selbst ist ueberall vorhanden. Die Richtigkeit beweisen die Testvektoren.
 */
object Bip39 {

    private const val RESOURCE = "/kern/bip39-english.txt"

    /** SHA-256 der kanonischen Liste aus bitcoin/bips, bip-0039/english.txt. */
    private const val WORDLIST_SHA256 =
        "2f5eed53a4727b4bf8880d8f3f199efc90e58503646d9ff8eff3a2ed3b24dbda"

    private const val ITERATIONS = 2048
    private const val SEED_BYTES = 64

    val wordlist: List<String> by lazy { ladeWortliste() }

    private val index: Map<String, Int> by lazy {
        wordlist.withIndex().associate { (i, w) -> w to i }
    }

    private fun ladeWortliste(): List<String> {
        val bytes = Bip39::class.java.getResourceAsStream(RESOURCE)?.use { it.readBytes() }
            ?: throw IllegalStateException("Wortliste $RESOURCE fehlt im Paket")

        val ist = Hex.toHex(Hashes.sha256(bytes))
        if (ist != WORDLIST_SHA256) {
            throw IllegalStateException(
                "Wortliste verfaelscht: SHA-256 ist $ist, erwartet $WORDLIST_SHA256"
            )
        }

        val woerter = bytes.toString(Charsets.UTF_8)
            .split('\n')
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        if (woerter.size != 2048) {
            throw IllegalStateException("Wortliste hat ${woerter.size} Woerter, erwartet 2048")
        }
        return woerter
    }

    /**
     * Eingabe entschaerfen: doppelte Leerzeichen, Grossschreibung und
     * Zeilenumbrueche aus dem Einfuegen sind der haeufigste Grund, warum eine
     * korrekt notierte Wiederherstellung scheitert.
     */
    fun normalize(mnemonic: String): String =
        Normalizer.normalize(mnemonic, Normalizer.Form.NFKD)
            .trim()
            .lowercase()
            .split(Regex("\\s+"))
            .filter { it.isNotEmpty() }
            .joinToString(" ")

    fun isValid(mnemonic: String): Boolean =
        runCatching { pruefe(normalize(mnemonic)) }.isSuccess

    /** Wirft mit Begruendung, wenn die Woerter nicht stimmen. */
    private fun pruefe(clean: String) {
        val woerter = if (clean.isEmpty()) emptyList() else clean.split(' ')
        if (woerter.size !in intArrayOf(12, 15, 18, 21, 24).toList()) {
            throw IllegalArgumentException(
                "${woerter.size} Woerter - erlaubt sind 12, 15, 18, 21 oder 24"
            )
        }

        val bits = StringBuilder(woerter.size * 11)
        for (w in woerter) {
            val i = index[w] ?: throw IllegalArgumentException("Wort nicht in der Liste: $w")
            bits.append(Integer.toBinaryString(i).padStart(11, '0'))
        }

        val pruefBits = bits.length / 33
        val entropieBits = bits.length - pruefBits
        val entropie = ByteArray(entropieBits / 8)
        for (i in entropie.indices) {
            entropie[i] = bits.substring(i * 8, i * 8 + 8).toInt(2).toByte()
        }

        val hash = Hashes.sha256(entropie)
        val soll = StringBuilder(pruefBits)
        for (i in 0 until pruefBits) {
            val bit = (hash[i / 8].toInt() shr (7 - i % 8)) and 1
            soll.append(if (bit == 1) '1' else '0')
        }

        if (bits.substring(entropieBits) != soll.toString()) {
            throw IllegalArgumentException("Merkwoerter ungueltig - Pruefsumme stimmt nicht")
        }
    }

    /** Neue Merkwoerter. 12 Woerter = 128 Bit, 24 Woerter = 256 Bit. */
    fun create(words: Int = 12, rnd: SecureRandom = SecureRandom()): String {
        require(words == 12 || words == 24) { "nur 12 oder 24 Woerter" }
        val entropie = ByteArray(if (words == 24) 32 else 16)
        rnd.nextBytes(entropie)
        return fromEntropy(entropie)
    }

    fun fromEntropy(entropie: ByteArray): String {
        require(entropie.size in intArrayOf(16, 20, 24, 28, 32).toList()) {
            "Entropie muss 16, 20, 24, 28 oder 32 Byte sein"
        }
        val pruefBits = entropie.size * 8 / 32
        val hash = Hashes.sha256(entropie)

        val bits = StringBuilder(entropie.size * 8 + pruefBits)
        for (b in entropie) {
            bits.append(Integer.toBinaryString(b.toInt() and 0xff).padStart(8, '0'))
        }
        for (i in 0 until pruefBits) {
            val bit = (hash[i / 8].toInt() shr (7 - i % 8)) and 1
            bits.append(if (bit == 1) '1' else '0')
        }

        val out = ArrayList<String>(bits.length / 11)
        for (i in 0 until bits.length / 11) {
            out.add(wordlist[bits.substring(i * 11, i * 11 + 11).toInt(2)])
        }
        return out.joinToString(" ")
    }

    /**
     * Merkwoerter zu Seed.
     *
     * Die optionale Passphrase ("25. Wort") erzeugt aus denselben Woertern
     * eine voellig andere Wallet und ist genauso wenig wiederherstellbar.
     */
    fun toSeed(mnemonic: String, passphrase: String = ""): ByteArray {
        val clean = normalize(mnemonic)
        pruefe(clean)
        val pass = clean.toByteArray(Charsets.UTF_8)
        val salt = ("mnemonic" + Normalizer.normalize(passphrase, Normalizer.Form.NFKD))
            .toByteArray(Charsets.UTF_8)
        return pbkdf2HmacSha512(pass, salt, ITERATIONS, SEED_BYTES)
    }

    /**
     * PBKDF2 mit HMAC-SHA512, RFC 2898.
     *
     * Bei dkLen = 64 ist das genau ein Block, weil HMAC-SHA512 64 Byte
     * liefert. Die Schleife ist trotzdem allgemein geschrieben.
     */
    fun pbkdf2HmacSha512(pass: ByteArray, salt: ByteArray, iter: Int, dkLen: Int): ByteArray {
        require(iter > 0) { "Iterationen muessen > 0 sein" }
        require(dkLen > 0) { "Laenge muss > 0 sein" }

        val hLen = 64
        val bloecke = (dkLen + hLen - 1) / hLen
        val out = ByteArray(bloecke * hLen)

        for (b in 1..bloecke) {
            val erst = ByteArray(salt.size + 4)
            salt.copyInto(erst)
            erst[salt.size] = ((b ushr 24) and 0xff).toByte()
            erst[salt.size + 1] = ((b ushr 16) and 0xff).toByte()
            erst[salt.size + 2] = ((b ushr 8) and 0xff).toByte()
            erst[salt.size + 3] = (b and 0xff).toByte()

            var u = Hashes.hmacSha512(pass, erst)
            val t = u.copyOf()
            for (i in 2..iter) {
                u = Hashes.hmacSha512(pass, u)
                for (j in t.indices) t[j] = (t[j].toInt() xor u[j].toInt()).toByte()
            }
            t.copyInto(out, (b - 1) * hLen)
        }

        return out.copyOfRange(0, dkLen)
    }
}
