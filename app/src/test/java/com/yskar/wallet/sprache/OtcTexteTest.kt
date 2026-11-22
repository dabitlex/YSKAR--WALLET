package com.yskar.wallet.sprache

import com.yskar.wallet.otc.Ablauf
import com.yskar.wallet.otc.Auftrag
import com.yskar.wallet.otc.Tauschbuch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Jeder Ablehnungsgrund, jede Stoerung, jede Rolle und jeder Stand hat in beiden Sprachen einen eigenen Satz. */
class OtcTexteTest {

    @Test
    fun jederWertHatEinenEigenenText() {
        for (t in listOf(OtcDe, OtcEn)) {
            val fehler = Auftrag.Fehler.entries.map { t.fehler(it) }
            assertTrue(fehler.all { it.isNotBlank() })
            assertEquals("doppelt: $fehler", fehler.size, fehler.toSet().size)
            val stoerungen = Ablauf.Stoerung.entries.map { t.stoerung(it) }
            assertEquals(stoerungen.size, stoerungen.toSet().size)
            assertEquals(2, Tauschbuch.Rolle.entries.map { t.rolle(it) }.toSet().size)
            assertEquals(4, Tauschbuch.Stand.entries.map { t.stand(it) }.toSet().size)
        }
    }

    @Test
    fun blockzeitInStundenOderMinuten() {
        assertEquals("Block 100 · in ca. 60 Min.", OtcDe.blockIn(100, 6))
        assertEquals("Block 200 · in ca. 24 Std.", OtcDe.blockIn(200, 144))
        assertEquals("Block 200", OtcDe.blockIn(200, 0))
        assertEquals("Block 200 · in about 24 h", OtcEn.blockIn(200, 144))
    }
}
