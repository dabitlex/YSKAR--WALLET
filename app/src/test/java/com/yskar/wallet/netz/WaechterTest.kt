package com.yskar.wallet.netz

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Wann der Waechter meldet - und wann er still bleibt.
 *
 * Das Stillbleiben ist der schwierigere Teil und darum der, der hier die
 * meisten Tests bekommt. Eine Mitteilung, die ausbleibt, faellt dem
 * Besitzer auf. Eine, die zweimal kommt oder den halben Verlauf
 * nachtraegt, auch - nur aergerlicher.
 */
class WaechterTest {

    private val eigene = "ysr17g5ywa80hf2nyjkp59ha6a3ycs0jtsule8a6v4"
    private val fremde = "ysr1j8nnl4kzwdck9u6ddltp8dmvauhg6rez967syq"

    private fun eintrag(
        txid: String,
        art: Art,
        betrag: Long = 100_000L,
        idx: Int = 0,
    ) = VerlaufEintrag(
        txid = txid,
        height = 9472,
        idx = idx,
        zeit = 1_762_000_000L,
        art = art,
        gegenseite = fremde,
        anteile = null,
        betrag = betrag,
        gebuehr = 16_800,
        notiz = "",
    )

    private fun konto(guthaben: Long, vararg eintraege: VerlaufEintrag) = Konto(
        adresse = eigene,
        guthaben = guthaben,
        nonce = 1,
        naechsteNonce = 2,
        wartend = emptyList(),
        wartendQuelle = "fullnode",
        verlauf = eintraege.toList(),
        verlaufWeiter = null,
    )

    @Test
    fun `der erste Durchgang meldet nichts`() {
        // Wer die Benachrichtigungen einschaltet, soll nicht als Erstes
        // seinen gesamten Verlauf als Mitteilungen bekommen.
        val k = konto(500_000, eintrag("aa", Art.EIN), eintrag("bb", Art.AUS))
        val befund = Waechter.pruefe(k, Waechter.Gedaechtnis())

        assertTrue(befund.meldungen.isEmpty())
        assertEquals(500_000L, befund.gedaechtnis.guthaben)
        assertEquals(2, befund.gedaechtnis.txids.size)
    }

    @Test
    fun `ein neuer Eingang wird gemeldet`() {
        val alt = Waechter.pruefe(konto(0), Waechter.Gedaechtnis()).gedaechtnis
        val neu = konto(100_000, eintrag("aa", Art.EIN, betrag = 100_000))

        val befund = Waechter.pruefe(neu, alt)

        assertEquals(1, befund.meldungen.size)
        assertEquals("aa", befund.meldungen[0].txid)
        assertTrue(befund.meldungen[0].eingehend)
        assertEquals(100_000L, befund.meldungen[0].betrag)
    }

    @Test
    fun `derselbe Eintrag wird nicht zweimal gemeldet`() {
        val k = konto(100_000, eintrag("aa", Art.EIN))
        val erst = Waechter.pruefe(k, Waechter.pruefe(konto(0), Waechter.Gedaechtnis()).gedaechtnis)
        assertEquals(1, erst.meldungen.size)

        val zweit = Waechter.pruefe(k, erst.gedaechtnis)
        assertTrue(zweit.meldungen.isEmpty())
    }

    @Test
    fun `ein Ausgang wird gemeldet, sobald er im Verlauf steht`() {
        // Im Verlauf heisst: in einem Block. Genau das ist "bestaetigt" -
        // nicht "abgeschickt".
        val alt = Waechter.pruefe(konto(500_000), Waechter.Gedaechtnis()).gedaechtnis
        val befund = Waechter.pruefe(konto(380_000, eintrag("cc", Art.AUS)), alt)

        assertEquals(1, befund.meldungen.size)
        assertTrue(!befund.meldungen[0].eingehend)
    }

    @Test
    fun `eine Ueberweisung an sich selbst ergibt zwei Meldungen`() {
        // Sie steht zweimal im Verlauf, einmal als Ein- und einmal als
        // Ausgang. Mit der txid allein als Kennung fiele eine davon weg.
        val alt = Waechter.pruefe(konto(500_000), Waechter.Gedaechtnis()).gedaechtnis
        val befund = Waechter.pruefe(
            konto(
                495_000,
                eintrag("dd", Art.AUS, idx = 3),
                eintrag("dd", Art.EIN, idx = 3),
            ),
            alt,
        )

        assertEquals(2, befund.meldungen.size)
        assertEquals(setOf(true, false), befund.meldungen.map { it.eingehend }.toSet())
    }

    @Test
    fun `das Gedaechtnis behaelt Kennungen, die aus der ersten Seite rutschen`() {
        // Der Spiegel liefert nur die erste Seite. Faellt ein Eintrag
        // daraus heraus, darf er spaeter nicht als neu wieder auftauchen -
        // sonst meldet eine Wallet mit regem Verlauf alte Bewegungen neu.
        val alt = Waechter.pruefe(
            konto(100_000, eintrag("alt1", Art.EIN), eintrag("alt2", Art.EIN)),
            Waechter.Gedaechtnis(),
        ).gedaechtnis

        val befund = Waechter.pruefe(konto(200_000, eintrag("neu", Art.EIN)), alt)

        assertEquals(1, befund.meldungen.size)
        assertEquals("neu", befund.meldungen[0].txid)
        assertTrue(befund.gedaechtnis.txids.any { it.startsWith("alt1") })
        assertTrue(befund.gedaechtnis.txids.any { it.startsWith("alt2") })
    }

    @Test
    fun `ein leerer Verlauf meldet nichts und vergisst nichts`() {
        val alt = Waechter.pruefe(
            konto(100_000, eintrag("aa", Art.EIN)),
            Waechter.Gedaechtnis(),
        ).gedaechtnis

        val befund = Waechter.pruefe(konto(100_000), alt)

        assertTrue(befund.meldungen.isEmpty())
        assertEquals(alt.txids, befund.gedaechtnis.txids)
    }

    @Test
    fun `Blockertrag und Poolanteil gelten als Eingang`() {
        val alt = Waechter.pruefe(konto(0), Waechter.Gedaechtnis()).gedaechtnis
        val befund = Waechter.pruefe(
            konto(
                999,
                eintrag("rr", Art.REWARD, idx = 0),
                eintrag("pp", Art.POOL, idx = 1),
            ),
            alt,
        )

        assertEquals(2, befund.meldungen.size)
        assertTrue(befund.meldungen.all { it.eingehend })
    }
}
