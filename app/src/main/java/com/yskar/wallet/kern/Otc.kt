package com.yskar.wallet.kern

/**
 * OTC-Tausch: Geheimnis, Sperrskript und Witness.
 *
 * Gegenstueck zu dabitlex/yskar-otc (src/geheimnis.ts, src/ysr/sperre.ts),
 * dort gegen den YSKAR-Kern gebaut. Geprueft gegen
 * test/resources/otc-vergleichswerte.json, die mit eben diesem Kern erzeugt
 * und vom Konsens (checkScriptSpend) angenommen wurden.
 *
 * Das Sperrskript (Konsensfassung 6, Abschnitt 7):
 *
 *   THRESH(1, [
 *     THRESH(3, [PK kaeufer, HASH h, BEFORE einloeseSchluss]),   -- Einloesen
 *     THRESH(2, [PK verkaeufer, AFTER frist])                    -- Zurueckholen
 *   ])
 *
 * Die Wallet liest keine fremden Skripte. Sie baut dieses eine Muster aus den
 * Teilen, die ein Auftrag nennt, und vergleicht die Adresse -- was nicht
 * Byte fuer Byte passt, wird nicht unterschrieben (Regel 22).
 */
object Otc {

    /** Domaenentrennung fuer das Geheimnis. Aendern hiesse: alle Geheimnisse andere. */
    val DOMAENE: ByteArray = "YSKAR-OTC/1".toByteArray(Charsets.US_ASCII)

    /** Abstand zwischen Einloese-Schluss und Frist, wie auf otc.yskar.app. */
    const val EINLOESE_PUFFER = 12

    private const val OP_PK = 0x01
    private const val OP_THRESH = 0x02
    private const val OP_HASH = 0x03
    private const val OP_AFTER = 0x04
    private const val OP_BEFORE = 0x05

    // ------------------------------------------------------------ Geheimnis

    /**
     * x = HMAC-SHA-256(otcWurzel, "YSKAR-OTC/1" || tauschKennung). 32 Byte.
     * Die Wurzel kommt aus [Wallet.otcWurzel].
     */
    fun geheimnis(wurzel: ByteArray, tauschKennung: ByteArray): ByteArray {
        require(wurzel.size == 32) { "Wurzel muss 32 Byte sein" }
        require(tauschKennung.size == 32) { "Tausch-Kennung muss 32 Byte sein" }
        return Hashes.hmacSha256(wurzel, DOMAENE + tauschKennung)
    }

    /** h = SHA-256(x). */
    fun fingerabdruck(geheimnis: ByteArray): ByteArray {
        require(geheimnis.size == 32) { "Geheimnis muss 32 Byte sein" }
        return Hashes.sha256(geheimnis)
    }

    /** Vergleich in gleichbleibender Zeit. */
    fun passt(geheimnis: ByteArray, h: ByteArray): Boolean {
        if (geheimnis.size != 32 || h.size != 32) return false
        val ist = Hashes.sha256(geheimnis)
        var d = 0
        for (i in 0 until 32) d = d or (ist[i].toInt() xor h[i].toInt())
        return d == 0
    }

    // ----------------------------------------------------------- Sperre

    class Teile(
        val kaeufer: ByteArray,
        val verkaeufer: ByteArray,
        val hash: ByteArray,
        /** T2: ab dieser Hoehe kann der Verkaeufer zurueckholen. */
        val frist: Long,
        /** BEFORE: bis zu dieser Hoehe kann der Kaeufer einloesen. */
        val einloeseSchluss: Long,
    ) {
        init {
            require(kaeufer.size == 32 && verkaeufer.size == 32) { "Schluessel muessen 32 Byte haben" }
            require(hash.size == 32) { "Hash muss 32 Byte haben" }
            require(!kaeufer.contentEquals(verkaeufer)) { "Kaeufer und Verkaeufer duerfen nicht derselbe Schluessel sein" }
            require(einloeseSchluss >= 1 && frist > einloeseSchluss) { "Einloese-Schluss muss vor der Frist liegen" }
            require(frist <= 0xffff_ffffL) { "Frist zu gross" }
        }
    }

    /** Das Sperrskript als Bytes, genau wie der Konsens es kodiert. */
    fun skript(t: Teile): ByteArray {
        val w = Writer()
        w.u8(OP_THRESH).u8(1).u8(2)
        w.u8(OP_THRESH).u8(3).u8(3)
        w.u8(OP_PK).bytes(t.kaeufer, 32)
        w.u8(OP_HASH).bytes(t.hash, 32)
        w.u8(OP_BEFORE).u32(t.einloeseSchluss)
        w.u8(OP_THRESH).u8(2).u8(2)
        w.u8(OP_PK).bytes(t.verkaeufer, 32)
        w.u8(OP_AFTER).u32(t.frist)
        return w.finish()
    }

    /** Adresse des Skriptkontos (ysr1p…). */
    fun adresse(t: Teile, hrp: String = Params.ADDRESS_HRP): String = Address.forScript(skript(t), hrp)

    // ----------------------------------------------------------- Witness
    //
    // Ein Slot je Blatt, Tiefe zuerst: PK kaeufer, HASH, BEFORE, PK verkaeufer, AFTER.
    //   belegt: 0x01 (+ Signatur 64 / Vorbild 32), leer: 0x00

    /** Einloesen durch den Kaeufer: Signatur, Geheimnis, BEFORE belegt. */
    fun witnessEinloesen(signatur: ByteArray, geheimnis: ByteArray): ByteArray {
        require(signatur.size == 64) { "Signatur muss 64 Byte sein" }
        require(geheimnis.size == 32) { "Geheimnis muss 32 Byte sein" }
        return Writer().u8(1).bytes(signatur, 64).u8(1).bytes(geheimnis, 32).u8(1).u8(0).u8(0).finish()
    }

    /** Zurueckholen durch den Verkaeufer: Signatur und AFTER belegt. */
    fun witnessZurueck(signatur: ByteArray): ByteArray {
        require(signatur.size == 64) { "Signatur muss 64 Byte sein" }
        return Writer().u8(0).u8(0).u8(0).u8(1).bytes(signatur, 64).u8(1).finish()
    }
}
