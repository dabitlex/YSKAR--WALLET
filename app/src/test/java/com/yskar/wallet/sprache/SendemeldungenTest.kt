package com.yskar.wallet.sprache

import com.yskar.wallet.kern.Address
import com.yskar.wallet.kern.Tx
import com.yskar.wallet.netz.Art
import com.yskar.wallet.netz.Betrag
import com.yskar.wallet.netz.Konto
import com.yskar.wallet.netz.Markt
import com.yskar.wallet.netz.NetzFehler
import com.yskar.wallet.netz.Senden
import com.yskar.wallet.netz.Spiegel
import com.yskar.wallet.netz.Stufe
import com.yskar.wallet.netz.StufenPreis
import com.yskar.wallet.netz.Wartend
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Die Meldungen aus `netz/Senden.kt` auf Englisch.
 *
 * `Senden.kt` meldet auf Deutsch und bleibt, wie es ist. Uebersetzt wird am
 * Wortlaut - also faellt eine Uebersetzung still aus, sobald dort jemand
 * einen Satz aendert. Dieser Test ist dafuer da, dass es nicht still
 * bleibt: Ausser den abgeschriebenen Saetzen laesst er `Senden.pruefe`,
 * `Senden.unterschreibenUndSenden` und `Senden.klartext` ihre Saetze selbst
 * sagen und verlangt fuer jeden die englische Fassung.
 *
 * KEIN TEST HIER SPRICHT MIT DEM ECHTEN NETZ, UND KEINER SENDET. Der
 * Transport ist eine Attrappe, die nichts wegschickt (REGELN.md, Regel 18).
 * Die Woerter sind der oeffentliche BIP39-Vektor; dahinter liegt kein
 * Guthaben (Regel 3).
 */
private const val WOERTER =
    "abandon abandon abandon abandon abandon abandon " +
        "abandon abandon abandon abandon abandon about"

private const val EIGENE = "ysr1pcy6c8vw2fpp49qh4pg3w348qz6nn6vw2fg7f0"
private const val EMPFAENGER = "ysr135sh9v4zxsp9rc8hx3st4alkskrh7mf6f73h8q"

private fun markt(fassung: Int = 1, sendepause: Boolean = false) = Markt(
    mindestGebuehr = 1680,
    mindestJeByte = 10,
    wartend = 0,
    andrang = false,
    stufen = mapOf(
        Stufe.LANGSAM to StufenPreis(1680, 1, 10),
        Stufe.NORMAL to StufenPreis(1680, 1, 10),
        Stufe.SCHNELL to StufenPreis(1680, 1, 10),
    ),
    hoehe = 8561,
    fassung = fassung,
    basisBytes = if (fassung >= 2) Tx.TRANSFER_V2_BASE_BYTES else Tx.TRANSFER_BASE_BYTES,
    sendepause = sendepause,
)

private fun konto(
    guthaben: Long = 500_000_000L,
    wartend: List<Wartend> = emptyList(),
) = Konto(
    adresse = EIGENE,
    guthaben = guthaben,
    nonce = 23,
    naechsteNonce = 23,
    wartend = wartend,
    wartendQuelle = "fullnode",
    verlauf = emptyList(),
    verlaufWeiter = null,
)

private fun pruefe(
    empfaenger: String = EMPFAENGER,
    betrag: String = "1",
    notiz: String = "",
    k: Konto? = konto(),
    m: Markt? = markt(),
    spitze: Long? = 8560,
) = Senden.pruefe(EIGENE, k, m, spitze, empfaenger, betrag, notiz)

/** Der deutsche Satz, mit dem `Senden.pruefe` ablehnt. */
private fun grund(p: Senden.Pruefung): String {
    assertTrue("sollte abgelehnt werden, war: $p", p is Senden.Pruefung.Problem)
    return (p as Senden.Pruefung.Problem).grund
}

private fun en(deutsch: String) = Sendemeldungen.uebersetze(deutsch, Sprache.ENGLISCH)

private fun de(deutsch: String) = Sendemeldungen.uebersetze(deutsch, Sprache.DEUTSCH)

/**
 * Die festen Saetze: links woertlich aus `netz/Senden.kt` abgeschrieben,
 * rechts, was auf Englisch dastehen soll.
 */
private val FESTE_SAETZE: List<Pair<String, String>> = listOf(
    // --- Senden.pruefe
    "Der Kontostand ist unbekannt. Lade ihn zuerst, sonst stimmt " +
        "die Nonce nicht." to
        "The balance is unknown. Load it first, otherwise the nonce will be wrong.",

    "Weder Gebührenauskunft noch Blockhöhe sind bekannt. " +
        "Welche Fassung gilt, lässt sich nicht raten - es wird nicht gesendet." to
        "Neither the fee information nor the block height is known. " +
        "Which version applies cannot be guessed - nothing is sent.",

    "Das Netz wechselt gerade die Konsensfassung. In diesem kurzen " +
        "Fenster nimmt kein Knoten eine Überweisung an. Gleich wieder versuchen." to
        "The network is switching the consensus version right now. In this " +
        "short window no node accepts a transfer. Try again in a moment.",

    "Es fehlt die Adresse des Empfängers." to
        "The recipient's address is missing.",

    "Diese Adresse ist nicht gültig. Prüfsumme oder Präfix stimmen nicht." to
        "This address is not valid. The checksum or the prefix is wrong.",

    "An ein gemeinsames Konto (ysr1p…) kann erst ab Konsensfassung 6 " +
        "gezahlt werden. Das Netz steht noch davor." to
        "A shared account (ysr1p…) can only be paid from consensus version 6 " +
        "onwards. The network has not reached it yet.",

    "Das ist deine eigene Adresse. Die Überweisung würde nur die Gebühr kosten." to
        "This is your own address. The transfer would only cost the fee.",

    "Der Betrag fehlt oder ist nicht lesbar." to
        "The amount is missing or cannot be read.",

    "Es wartet noch eine Zahlung auf einen Block. Warte, bis sie " +
        "drin ist - sonst kollidieren die Nonces." to
        "A payment is still waiting for a block. Wait until it is in - " +
        "otherwise the nonces collide.",

    // --- Senden.unterschreibenUndSenden
    "Die Merkwörter aus dem Tresor sind unbrauchbar." to
        "The twelve words from the vault are unusable.",

    "Der Schlüssel passt nicht zur angezeigten Adresse. " +
        "Es wird nichts gesendet." to
        "The key does not match the address shown. Nothing is sent.",

    "Die eigene Signatur ließ sich nicht bestätigen." to
        "The wallet's own signature could not be verified.",

    "Keine Verbindung zum Knoten. Die Überweisung wurde NICHT eingereicht." to
        "No connection to the node. The transfer was NOT submitted.",

    // --- Senden.klartext
    "Die laufende Nummer passt nicht. Lade den Kontostand neu und versuche es erneut." to
        "The nonce does not match. Reload the balance and try again.",

    "Das Guthaben reicht nicht - vielleicht ist in der Zwischenzeit etwas abgegangen." to
        "The balance is not sufficient - perhaps something went out in the meantime.",

    "Die Gebühr ist zu niedrig für den nächsten Block." to
        "The fee is too low for the next block.",

    "Die Signatur wurde nicht anerkannt. Das darf nicht passieren - bitte melden." to
        "The signature was not recognised. This must not happen - please report it.",

    "Der Betrag liegt unter der Staubgrenze." to
        "The amount is below the dust limit.",

    "Die Warteliste des Knotens ist voll. Später noch einmal." to
        "The node's waiting list is full. Try again later.",

    "Die Überweisung ist zu groß. Kürze die Notiz." to
        "The transfer is too large. Shorten the note.",

    "Die Überweisung ist abgelaufen, bevor sie in einen Block kam." to
        "The transfer expired before it got into a block.",

    "Diese Überweisung kennt der Knoten schon." to
        "The node already knows this transfer.",

    "Die Fassung passt nicht zur aktuellen Höhe. Lade die Gebührenauskunft neu." to
        "The version does not match the current height. Reload the fee information.",

    "Der Knoten hat sie abgelehnt." to
        "The node rejected it.",
)

/**
 * Die Saetze mit Werten darin, mit Werten, wie sie vorkommen - darunter
 * Betraege mit Tausenderpunkt.
 */
private val SAETZE_MIT_WERTEN: List<Pair<String, String>> = listOf(
    "Der Betrag liegt unter der Staubgrenze von 0,00000100 YSR." to
        "The amount is below the dust limit of 0.00000100 YSR.",

    "Die Notiz ist länger als 32 Byte. Umlaute zählen zwei, " +
        "ein Emoji vier." to
        "The note is longer than 32 bytes. Accented letters count as two, " +
        "an emoji as four.",

    "Das Guthaben reicht nicht. Betrag und Gebühr ergeben " +
        "1.284,57310946 YSR, es fehlen 284,5731 YSR." to
        "The balance is not sufficient. Amount and fee add up to " +
        "1,284.57310946 YSR, 284.5731 YSR are missing.",

    "Das Guthaben reicht nicht. Betrag und Gebühr ergeben " +
        "21.000.000,0000168 YSR, es fehlen 20.999.995,0000168 YSR." to
        "The balance is not sufficient. Amount and fee add up to " +
        "21,000,000.0000168 YSR, 20,999,995.0000168 YSR are missing.",

    "Das Guthaben reicht nicht. Betrag und Gebühr ergeben " +
        "6,0000168 YSR, es fehlen 1,0000168 YSR." to
        "The balance is not sufficient. Amount and fee add up to " +
        "6.0000168 YSR, 1.0000168 YSR are missing.",

    "Die Überweisung ist größer als berechnet " +
        "(181 statt 173 Byte). " +
        "Die Gebühr wäre zu niedrig - es wird nichts gesendet." to
        "The transfer is larger than calculated (181 instead of 173 bytes). " +
        "The fee would be too low - nothing is sent.",

    "Der Knoten antwortet nicht wie erwartet (502)." to
        "The node is not responding as expected (502).",

    // Ohne Antwort mit Status meldet `NetzFehler` die Null.
    "Der Knoten antwortet nicht wie erwartet (0)." to
        "The node is not responding as expected (0).",
)

class SendemeldungenTest {

    // ------------------------------------------------ abgeschriebene Saetze

    @Test
    fun `jeder feste Satz aus Senden hat seine englische Fassung`() {
        assertEquals("so viele feste Saetze kennt netz/Senden.kt", 24, FESTE_SAETZE.size)
        for ((deutsch, englisch) in FESTE_SAETZE) {
            assertEquals(deutsch, englisch, en(deutsch))
        }
    }

    @Test
    fun `Saetze mit Werten tragen die Werte hinueber`() {
        for ((deutsch, englisch) in SAETZE_MIT_WERTEN) {
            assertEquals(deutsch, englisch, en(deutsch))
        }
    }

    @Test
    fun `ein deutscher Betrag wird englisch geschrieben, Ziffer fuer Ziffer gleich`() {
        // Gegen den Formatierer selbst: Was `Betrag.genau` deutsch schreibt,
        // muss im englischen Satz so stehen, wie `Betrag.genau` es englisch
        // schreibt - ohne dass hier jemand rechnet.
        val englischeZahlen = Sprache.ENGLISCH.locale
        val betraege = listOf(
            1L, 100L, 99_999_999L, 100_000_000L, 128_457_310_946L,
            150_000_001_680L, 2_100_000_000_000_000L, 123_456_789_012_345L,
        )
        for (einheiten in betraege) {
            val deutsch = "Der Betrag liegt unter der Staubgrenze von " +
                "${Betrag.genau(einheiten, min = 8)} YSR."
            val englisch = "The amount is below the dust limit of " +
                "${Betrag.genau(einheiten, englischeZahlen, 8)} YSR."
            assertEquals(englisch, en(deutsch))
        }
        assertEquals(
            "The amount is below the dust limit of 1,284.57310946 YSR.",
            en("Der Betrag liegt unter der Staubgrenze von 1.284,57310946 YSR."),
        )
    }

    // ------------------------------------------------- Deutsch und Fremdes

    @Test
    fun `Deutsch bleibt Deutsch`() {
        for ((deutsch, _) in FESTE_SAETZE + SAETZE_MIT_WERTEN) {
            assertSame(deutsch, de(deutsch))
        }
        val fremd = "Etwas, das Senden nie gesagt hat."
        assertSame(fremd, de(fremd))
    }

    @Test
    fun `ein unbekannter Satz geht unveraendert durch`() {
        val fremde = listOf(
            "",
            " ",
            "Etwas, das Senden nie gesagt hat.",
            "Read timed out",
            "IllegalStateException",
            // Fast ein bekannter Satz - aber eben nur fast.
            "Der Knoten hat sie abgelehnt",
            "Der Knoten hat sie abgelehnt. ",
            "Der Betrag liegt unter der Staubgrenze von viel YSR.",
            "Der Knoten antwortet nicht wie erwartet (HTTP 502).",
            "Die Notiz ist länger als 32 Byte.",
        )
        for (text in fremde) {
            assertSame("'$text'", text, en(text))
        }
    }

    @Test
    fun `schon englische Saetze werden nicht noch einmal angefasst`() {
        // Das Modell uebersetzt manche Meldung, bevor sie den Bildschirm
        // erreicht. Laeuft sie dort ein zweites Mal durch, bleibt sie.
        for ((_, englisch) in FESTE_SAETZE + SAETZE_MIT_WERTEN) {
            assertSame(englisch, en(englisch))
        }
    }

    // -------------------------------------- was Senden.pruefe wirklich sagt

    @Test
    fun `was pruefe meldet, steht auf Englisch da`() {
        val wartet = Wartend("ab", Art.AUS, EIGENE, EMPFAENGER, 1, 1, 23, "")
        val skript = Address.encode(Address.rawFromScript(ByteArray(8)))

        val faelle = listOf(
            pruefe(k = null) to
                "The balance is unknown. Load it first, otherwise the nonce will be wrong.",
            pruefe(m = null, spitze = null) to
                "Neither the fee information nor the block height is known. " +
                "Which version applies cannot be guessed - nothing is sent.",
            pruefe(m = markt(sendepause = true)) to
                "The network is switching the consensus version right now. In this " +
                "short window no node accepts a transfer. Try again in a moment.",
            pruefe(empfaenger = "") to
                "The recipient's address is missing.",
            pruefe(empfaenger = "kein ysr") to
                "This address is not valid. The checksum or the prefix is wrong.",
            pruefe(empfaenger = skript) to
                "A shared account (ysr1p…) can only be paid from consensus version 6 " +
                "onwards. The network has not reached it yet.",
            pruefe(empfaenger = EIGENE) to
                "This is your own address. The transfer would only cost the fee.",
            pruefe(betrag = "abc") to
                "The amount is missing or cannot be read.",
            pruefe(betrag = "0,00000099") to
                "The amount is below the dust limit of 0.00000100 YSR.",
            pruefe(notiz = "a".repeat(33)) to
                "The note is longer than 32 bytes. Accented letters count as two, " +
                "an emoji as four.",
            pruefe(k = konto(wartend = listOf(wartet))) to
                "A payment is still waiting for a block. Wait until it is in - " +
                "otherwise the nonces collide.",
        )
        for ((pruefung, englisch) in faelle) {
            val deutsch = grund(pruefung)
            assertEquals(deutsch, englisch, en(deutsch))
        }
    }

    @Test
    fun `fehlende Deckung nennt Gesamt und Fehlbetrag in englischer Schreibweise`() {
        // 1.500 YSR bei 5 YSR Guthaben: beide Zahlen haben einen
        // Tausendertrenner. Was "Gesamt" ist, sagt der Entwurf selbst.
        val reich = konto(guthaben = 1_000_000_000_000L)
        val gesamt = (pruefe(betrag = "1500", k = reich) as Senden.Pruefung.Bereit).entwurf.gesamt
        val guthaben = 500_000_000L
        val deutsch = grund(pruefe(betrag = "1500", k = konto(guthaben = guthaben)))

        val zahlen = Sprache.ENGLISCH.locale
        val englisch = en(deutsch)
        assertEquals(
            "The balance is not sufficient. Amount and fee add up to " +
                "${Betrag.genau(gesamt, zahlen)} YSR, " +
                "${Betrag.genau(gesamt - guthaben, zahlen)} YSR are missing.",
            englisch,
        )
        assertTrue(englisch, englisch.contains("add up to 1,500."))
        assertTrue(englisch, englisch.contains(", 1,495."))
    }

    // ------------------- was Senden.unterschreibenUndSenden wirklich sagt

    private val woerter = WOERTER.toByteArray(Charsets.UTF_8)

    private fun entwurf(): Senden.Entwurf = (pruefe() as Senden.Pruefung.Bereit).entwurf

    /** Schickt nichts weg: antwortet immer dasselbe oder gar nicht. */
    private fun spiegel(status: Int = 200, antwort: String? = """{"accepted":true}""") =
        Spiegel(
            object : Spiegel.Transport {
                override fun anfrage(
                    methode: String,
                    url: String,
                    rumpf: String?,
                ): Spiegel.Antwort =
                    if (antwort == null) throw NetzFehler(NetzFehler.Ursache.OFFLINE)
                    else Spiegel.Antwort(status, antwort)
            },
            knotenFest = "https://pool.yskar.app",
        )

    private fun fehler(e: Senden.Ergebnis): String {
        assertTrue("sollte scheitern, war: $e", e is Senden.Ergebnis.Fehler)
        return (e as Senden.Ergebnis.Fehler).grund
    }

    @Test
    fun `was beim Unterschreiben scheitert, steht auf Englisch da`() {
        val unbrauchbar = fehler(
            Senden.unterschreibenUndSenden(
                entwurf(), "kein gueltiger satz".toByteArray(), EIGENE, spiegel(),
            ),
        )
        assertEquals("The twelve words from the vault are unusable.", en(unbrauchbar))

        val fremdeAdresse = fehler(
            Senden.unterschreibenUndSenden(entwurf(), woerter, EMPFAENGER, spiegel()),
        )
        assertEquals(
            "The key does not match the address shown. Nothing is sent.",
            en(fremdeAdresse),
        )

        val ohneNetz = fehler(
            Senden.unterschreibenUndSenden(entwurf(), woerter, EIGENE, spiegel(antwort = null)),
        )
        assertEquals(
            "No connection to the node. The transfer was NOT submitted.",
            en(ohneNetz),
        )

        val keinJson = fehler(
            Senden.unterschreibenUndSenden(
                entwurf(), woerter, EIGENE, spiegel(status = 502, antwort = "Bad Gateway"),
            ),
        )
        assertEquals("The node is not responding as expected (502).", en(keinJson))
    }

    @Test
    fun `jeder Ablehnungsgrund des Knotens steht auf Englisch da`() {
        val gruende = mapOf(
            "bad_nonce" to "The nonce does not match. Reload the balance and try again.",
            "insufficient_funds" to
                "The balance is not sufficient - perhaps something went out in the meantime.",
            "fee_too_low" to "The fee is too low for the next block.",
            "bad_signature" to
                "The signature was not recognised. This must not happen - please report it.",
            "dust" to "The amount is below the dust limit.",
            "mempool_full" to "The node's waiting list is full. Try again later.",
            "tx_too_large" to "The transfer is too large. Shorten the note.",
            "expired" to "The transfer expired before it got into a block.",
            "duplicate" to "The node already knows this transfer.",
            "bad_version" to
                "The version does not match the current height. Reload the fee information.",
            "voellig_neu" to "The node rejected it.",
        )
        for ((code, englisch) in gruende) {
            assertEquals(code, englisch, en(Senden.klartext(code)))
        }

        // Und durch den ganzen Weg: Der Knoten lehnt ab, `klartext` wird
        // gesetzt, die Uebersetzung findet ihn.
        val abgelehnt = Senden.unterschreibenUndSenden(
            entwurf(), woerter, EIGENE,
            spiegel(status = 400, antwort = """{"accepted":false,"reason":"bad_nonce"}"""),
        )
        assertTrue("sollte abgelehnt sein: $abgelehnt", abgelehnt is Senden.Ergebnis.Abgelehnt)
        assertEquals(
            "The nonce does not match. Reload the balance and try again.",
            en((abgelehnt as Senden.Ergebnis.Abgelehnt).klartext),
        )
    }

    // ---------------------------------------------------------- Wortwahl

    private fun alleEnglischen(): List<String> =
        (FESTE_SAETZE + SAETZE_MIT_WERTEN).map { (deutsch, _) -> en(deutsch) }

    @Test
    fun `kein englischer Satz enthaelt noch einen Umlaut`() {
        val umlaute = Regex("[äöüÄÖÜß]")
        for (satz in alleEnglischen()) {
            assertFalse(satz, umlaute.containsMatchIn(satz))
        }
    }

    @Test
    fun `kein englischer Satz behauptet mehr als der deutsche`() {
        // "angenommen" ist Schritt 2 von fuenf. Keine dieser Meldungen darf
        // auf Englisch nach "erledigt" oder "endgueltig" klingen.
        val zuViel = Regex("\\b(done|final|finali[sz]ed|completed?)\\b", RegexOption.IGNORE_CASE)
        for (satz in alleEnglischen()) {
            assertFalse(satz, zuViel.containsMatchIn(satz))
        }
    }
}
