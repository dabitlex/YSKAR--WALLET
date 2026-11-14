package com.yskar.wallet.ui

import com.yskar.wallet.netz.Art
import com.yskar.wallet.netz.Konto
import com.yskar.wallet.netz.VerlaufEintrag
import com.yskar.wallet.netz.Waechter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * Was ein Tipp auf eine Mitteilung des Waechters oeffnet.
 *
 * Alle Kennungen hier sind erfunden (Regel 42).
 */
class MitteilungszielTest {

    private val kennung = "3f9c0d1e7a2b4c5d6e7f8091a2b3c4d5e6f708192a3b4c5d6e7f8091a2b3c4d5"
    private val andere = "c0ffee00" + "1".repeat(56)

    private fun eintrag(txid: String, art: Art, idx: Int = 0) = VerlaufEintrag(
        txid = txid,
        height = 9472,
        idx = idx,
        zeit = 1_794_000_000L,
        art = art,
        gegenseite = "ysr1j8nnl4kzwdck9u6ddltp8dmvauhg6rez967syq",
        anteile = null,
        betrag = 250_000L,
        gebuehr = 16_800,
        notiz = "",
    )

    @Test
    fun `ein Schluessel des Waechters wird angenommen`() {
        val s = Waechter.schluessel(eintrag(kennung, Art.EIN, idx = 3))
        assertEquals("$kennung:3:EIN", s)
        assertEquals(s, Mitteilungsziel.gueltig(s))
    }

    @Test
    fun `alles andere wird verworfen`() {
        listOf(
            null,
            "",
            kennung,                              // nur die txid
            "${kennung.uppercase()}:0:EIN",       // grosse Hex-Zeichen
            "${kennung.drop(1)}:0:EIN",           // 63 Zeichen
            "$kennung:0:ein",                     // Art klein
            "$kennung:0:SENDEN",                  // keine Art
            "$kennung:-1:EIN",                    // negativer Platz
            "$kennung:1234567890:EIN",            // zu lang
            "$kennung:0:EIN\n",                   // Zeilenende
            "$kennung:0:EIN:x",                   // angehaengt
            "yskar:login?msg=x&cb=https://yskar.app",
            "https://yskar.app/explorer#tx-$kennung",
        ).forEach { assertNull("angenommen: $it", Mitteilungsziel.gueltig(it)) }
    }

    @Test
    fun `bei einer Ueberweisung an sich selbst trifft er die richtige Zeile`() {
        val ein = eintrag(kennung, Art.EIN, idx = 1)
        val aus = eintrag(kennung, Art.AUS, idx = 0)
        val verlauf = listOf(eintrag(andere, Art.EIN), aus, ein)

        assertSame(ein, Mitteilungsziel.finde(verlauf, Waechter.schluessel(ein)))
        assertSame(aus, Mitteilungsziel.finde(verlauf, Waechter.schluessel(aus)))
    }

    @Test
    fun `was nicht im Verlauf steht, oeffnet nichts`() {
        val verlauf = listOf(eintrag(andere, Art.EIN))
        assertNull(Mitteilungsziel.finde(verlauf, "$kennung:0:EIN"))
        assertNull(Mitteilungsziel.finde(emptyList(), "$kennung:0:EIN"))
    }

    @Test
    fun `die Meldung des Waechters fuehrt zu ihrem Eintrag`() {
        val alt = eintrag(andere, Art.AUS)
        val neu = eintrag(kennung, Art.EIN, idx = 2)
        fun konto(vararg e: VerlaufEintrag) = Konto(
            adresse = "ysr17g5ywa80hf2nyjkp59ha6a3ycs0jtsule8a6v4",
            guthaben = 1_000_000,
            nonce = 1,
            naechsteNonce = 2,
            wartend = emptyList(),
            wartendQuelle = "fullnode",
            verlauf = e.toList(),
            verlaufWeiter = null,
        )
        val erster = Waechter.pruefe(konto(alt), Waechter.Gedaechtnis())
        val befund = Waechter.pruefe(konto(neu, alt), erster.gedaechtnis)

        val schluessel = Mitteilungsziel.gueltig(befund.meldungen.single().schluessel)!!
        assertSame(neu, Mitteilungsziel.finde(listOf(neu, alt), schluessel))
    }
}
