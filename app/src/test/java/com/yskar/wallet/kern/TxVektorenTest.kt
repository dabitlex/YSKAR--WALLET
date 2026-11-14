package com.yskar.wallet.kern

import org.json.JSONArray
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Transaktionen gegen die Vektoren der laufenden Wallet.
 *
 * Der eigentliche Beweis steckt in neuUnterschriebenErgibtDieselbenBytes():
 * Ed25519 ist deterministisch (RFC 8032). Wenn aus demselben Schluessel und
 * denselben Feldern dieselben 64 Byte Signatur entstehen, dann stimmt der
 * Sighash Bit fuer Bit - denn eine um ein Bit abweichende Nachricht ergibt
 * eine voellig andere Signatur. Ein Vergleich der Rohbytes allein wuerde das
 * nicht zeigen: Der haette die Signatur ja nur durchgereicht.
 */
class TxVektorenTest {

    private data class Fall(
        val woerter: String,
        val rohHex: String,
        val txidHex: String,
        val bezeichnung: String,
    )

    private val faelle: List<Fall> by lazy {
        val text = javaClass.getResourceAsStream("/vergleichswerte.json")!!
            .use { it.readBytes() }
            .toString(Charsets.UTF_8)
        val arr = JSONArray(text)
        val out = ArrayList<Fall>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val w = o.getString("woerter")
            out.add(Fall(w, o.getString("roh"), o.getString("txid"), "roh #$i"))
            out.add(Fall(w, o.getString("roh0"), o.getString("txid0"), "roh0 #$i"))
        }
        out
    }

    @Test
    fun faelleSindVollstaendigGeladen() {
        assertEquals("zwei Transaktionen je Vektor", 10, faelle.size)
        for (f in faelle) {
            assertTrue(f.bezeichnung, f.rohHex.length > 300)
            assertEquals(f.bezeichnung, 64, f.txidHex.length)
        }
    }

    @Test
    fun rohbytesLassenSichUnveraendertZurueckschreiben() {
        for (f in faelle) {
            val bytes = Hex.fromHex(f.rohHex)
            val t = Tx.deserializeTransfer(bytes)
            assertArrayEquals(
                "Leitungsform von ${f.bezeichnung}",
                bytes,
                Tx.serialize(t),
            )
            assertEquals(f.bezeichnung, bytes.size, Tx.txBytes(t))
        }
    }

    @Test
    fun txidStimmt() {
        for (f in faelle) {
            val t = Tx.deserializeTransfer(Hex.fromHex(f.rohHex))
            assertEquals("txid von ${f.bezeichnung}", f.txidHex, Tx.txidHex(t))
        }
    }

    @Test
    fun absenderIstDieAbgeleiteteAdresse() {
        for (f in faelle) {
            val kp = Wallet.fromMnemonic(f.woerter)
            val t = Tx.deserializeTransfer(Hex.fromHex(f.rohHex))
            assertArrayEquals(
                "Absender von ${f.bezeichnung}",
                kp.addressRaw,
                t.from,
            )
            assertArrayEquals(
                "oeffentlicher Schluessel in ${f.bezeichnung}",
                kp.publicKey,
                t.publicKey,
            )
        }
    }

    @Test
    fun signaturDerVorlageIstGueltig() {
        for (f in faelle) {
            val t = Tx.deserializeTransfer(Hex.fromHex(f.rohHex))
            assertTrue(
                "Signatur von ${f.bezeichnung} muss ueber unserem Sighash gelten",
                Tx.verifyOwnSignature(t),
            )
        }
    }

    /**
     * Der Kern des Beweises: dieselben Felder, derselbe Schluessel, dieselben
     * Bytes - Signatur eingeschlossen.
     */
    @Test
    fun neuUnterschriebenErgibtDieselbenBytes() {
        for (f in faelle) {
            val kp = Wallet.fromMnemonic(f.woerter)
            val bytes = Hex.fromHex(f.rohHex)
            val vorlage = Tx.deserializeTransfer(bytes)

            val neu = Tx.buildTransfer(
                from = vorlage.from,
                to = vorlage.to,
                amount = vorlage.amount,
                fee = vorlage.fee,
                nonce = vorlage.nonce,
                publicKey = kp.publicKey,
                privateKey = kp.privateKey,
                validFrom = vorlage.validFrom,
                validUntil = vorlage.validUntil,
                memo = vorlage.memo,
                version = vorlage.version,
            )

            assertArrayEquals(
                "Signatur von ${f.bezeichnung}",
                vorlage.signature,
                neu.signature,
            )
            assertEquals("Transaktion ${f.bezeichnung}", vorlage, neu)
            assertArrayEquals("Leitungsform ${f.bezeichnung}", bytes, Tx.serialize(neu))
            assertEquals(f.txidHex, Tx.txidHex(neu))
        }
    }

    @Test
    fun einGeaendertesFeldAendertDieSignatur() {
        val f = faelle.first()
        val kp = Wallet.fromMnemonic(f.woerter)
        val vorlage = Tx.deserializeTransfer(Hex.fromHex(f.rohHex))

        // Ein einziges Byte mehr im Betrag - die Signatur muss sich voellig
        // aendern, sonst deckt der Sighash das Feld nicht ab.
        val anders = Tx.buildTransfer(
            from = vorlage.from,
            to = vorlage.to,
            amount = vorlage.amount + 1,
            fee = vorlage.fee,
            nonce = vorlage.nonce,
            publicKey = kp.publicKey,
            privateKey = kp.privateKey,
            validFrom = vorlage.validFrom,
            validUntil = vorlage.validUntil,
            memo = vorlage.memo,
            version = vorlage.version,
        )
        assertFalse(vorlage.signature.contentEquals(anders.signature))
        assertFalse(Tx.txidHex(vorlage) == Tx.txidHex(anders))
    }

    @Test
    fun andereChainIdMachtDieSignaturUngueltig() {
        val f = faelle.first()
        val t = Tx.deserializeTransfer(Hex.fromHex(f.rohHex))
        val fremd = Hashes.sha256("yskar-test-1".toByteArray())
        assertTrue(Tx.verifyOwnSignature(t, Params.CHAIN_ID))
        assertFalse(
            "eine Signatur darf auf einer anderen Kette nicht gelten",
            Tx.verifyOwnSignature(t, fremd),
        )
    }

    @Test
    fun groessenangabeStimmtMitDerWirklichkeit() {
        for (f in faelle) {
            val t = Tx.deserializeTransfer(Hex.fromHex(f.rohHex))
            if (t.version == Tx.TRANSFER_V1) {
                assertEquals(
                    "berechnete Groesse ${f.bezeichnung}",
                    Tx.transferBytesAt(0, t.memo.size),
                    Tx.txBytes(t),
                )
            }
        }
        // Die Vorlage nennt 168 Byte ohne Notiz als Grundgroesse.
        assertEquals(168, Tx.TRANSFER_BASE_BYTES)
        assertEquals(174, Tx.TRANSFER_V2_BASE_BYTES)
    }

    @Test
    fun fassungZweiRundUm() {
        val kp = Wallet.fromMnemonic(faelle.first().woerter)
        val empfaenger = Address.decode("ysr135sh9v4zxsp9rc8hx3st4alkskrh7mf6f73h8q")

        val t = Tx.buildTransfer(
            from = kp.addressRaw,
            to = empfaenger,
            amount = 123_456_789L,
            fee = 200L,
            nonce = 42L,
            publicKey = kp.publicKey,
            privateKey = kp.privateKey,
            validFrom = 10_000L,
            validUntil = 10_100L,
            memo = "Miete".toByteArray(),
            version = Tx.TRANSFER_V2,
        )

        val bytes = Tx.serialize(t)
        assertEquals(Tx.TRANSFER_V2_BASE_BYTES + 5, bytes.size)
        assertEquals(t, Tx.deserializeTransfer(bytes))
        assertTrue(Tx.verifyOwnSignature(t))

        // Fassung 2 schreibt den Empfaenger mit Versionsbyte - die Fassungen
        // duerfen darum nie dieselben Bytes ergeben.
        val alt = Tx.buildTransfer(
            from = kp.addressRaw, to = empfaenger, amount = 123_456_789L, fee = 200L,
            nonce = 42L, publicKey = kp.publicKey, privateKey = kp.privateKey,
            validUntil = 10_100L, memo = "Miete".toByteArray(), version = Tx.TRANSFER_V1,
        )
        assertFalse(t.signature.contentEquals(alt.signature))
    }

    @Test
    fun fassungZweiZahltAnEinSkriptKonto() {
        val kp = Wallet.fromMnemonic(faelle.first().woerter)
        val skriptKonto = Address.rawFromScript(ByteArray(16) { it.toByte() })
        assertEquals(33, skriptKonto.size)

        val t = Tx.buildTransfer(
            from = kp.addressRaw, to = skriptKonto, amount = 1_000L, fee = 300L,
            nonce = 1L, publicKey = kp.publicKey, privateKey = kp.privateKey,
            version = Tx.TRANSFER_V2,
        )
        val bytes = Tx.serialize(t)

        // 12 Byte groesser als an eine Adresse der Version 0 - wer mit 174
        // rechnet, zahlt zu wenig Gebuehr.
        assertEquals(Tx.TRANSFER_V2_BASE_BYTES + 12, bytes.size)
        assertEquals(
            bytes.size,
            Tx.transferBytesAt(Params.V6_HEIGHT, 0, empfaengerBytes = 33),
        )
        assertEquals(t, Tx.deserializeTransfer(bytes))
    }

    @Test
    fun fassungEinsLehntSkriptKontoAb() {
        val kp = Wallet.fromMnemonic(faelle.first().woerter)
        val skriptKonto = Address.rawFromScript(ByteArray(16))
        var geworfen = false
        try {
            Tx.buildTransfer(
                from = kp.addressRaw, to = skriptKonto, amount = 1L, fee = 1L, nonce = 0L,
                publicKey = kp.publicKey, privateKey = kp.privateKey,
                version = Tx.TRANSFER_V1,
            )
        } catch (e: IllegalArgumentException) {
            geworfen = true
        }
        assertTrue("Fassung 1 darf nicht an ysr1p… zahlen", geworfen)
    }

    @Test
    fun fassungswechselUndSendepause() {
        val h = Params.V6_HEIGHT
        assertEquals(Tx.TRANSFER_V1, Tx.transferVersionAt(h - 1))
        assertEquals(Tx.TRANSFER_V2, Tx.transferVersionAt(h))
        assertFalse(Tx.sendepause(h - 4))
        assertTrue(Tx.sendepause(h - 3))
        assertTrue(Tx.sendepause(h - 1))
        assertFalse("ab der Hoehe gilt Fassung 2, keine Pause mehr", Tx.sendepause(h))
    }
}
