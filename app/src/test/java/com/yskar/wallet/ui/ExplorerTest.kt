package com.yskar.wallet.ui

import com.yskar.wallet.kern.Address
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Die Verweise in den Explorer - und dass nichts Ungeprueftes hineinkommt.
 *
 * Kennung und Adresse hier sind oeffentlich: aus dem mitgeschnittenen
 * Verlauf der Pruefadresse (`test/resources/netz`).
 */
class ExplorerTest {

    private val txid = "f5ac506f58f8d6f181053415bf165a15bed345925b3df33c9ad4fb88d7359cae"
    private val adresse = "ysr17g5ywa80hf2nyjkp59ha6a3ycs0jtsule8a6v4"

    @Test
    fun `eine Ueberweisung oeffnet sich ueber ihre Kennung`() {
        assertEquals("https://www.yskar.app/explorer#tx-$txid", Explorer.ueberweisung(txid))
    }

    @Test
    fun `ein Konto oeffnet sich ueber seine Adresse`() {
        assertTrue("Die Probeadresse taugt nichts", Address.isValid(adresse))
        assertEquals("https://www.yskar.app/explorer#adr-$adresse", Explorer.konto(adresse))
    }

    @Test
    fun `ein Block oeffnet sich ueber seine Hoehe`() {
        assertEquals("https://www.yskar.app/explorer#block-8670", Explorer.block(8_670))
        assertEquals("https://www.yskar.app/explorer#block-0", Explorer.block(0))
        assertNull(Explorer.block(-1))
    }

    @Test
    fun `was keine Kennung ist, bekommt keinen Verweis`() {
        // Die Kennung kommt vom Spiegel, also aus dem Netz. Nur genau 64
        // kleine Hex-Zeichen duerfen in die Adresse.
        val keine = listOf(
            "",
            txid.dropLast(1),
            txid + "0",
            txid.uppercase(),
            txid.replaceRange(0, 1, "g"),
            "$txid?x=1",
            "$txid/../../konto",
            " $txid",
            "$txid\n",
            "https://boese.example/#tx-$txid",
        )
        for (k in keine) assertNull("\"$k\"", Explorer.ueberweisung(k))
    }

    @Test
    fun `was keine Adresse ist, bekommt keinen Verweis`() {
        val keine = listOf(
            "",
            "ysr1",
            adresse.dropLast(1),
            adresse.replaceRange(10, 11, "b"),
            adresse.uppercase() + "x",
            "$adresse#tx-$txid",
            "$adresse?weiter=https://boese.example",
            "btc1qxy2kgdygjrsqtzq2n0yrf2493p83kkfjhx0wlh",
        )
        for (k in keine) assertNull("\"$k\"", Explorer.konto(k))
    }

    @Test
    fun `jeder Verweis bleibt beim Explorer`() {
        for (v in listOf(Explorer.ueberweisung(txid), Explorer.konto(adresse), Explorer.block(1))) {
            assertTrue(v, v!!.startsWith("https://www.yskar.app/explorer#"))
        }
    }
}
