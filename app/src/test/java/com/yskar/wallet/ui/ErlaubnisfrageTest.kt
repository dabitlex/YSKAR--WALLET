package com.yskar.wallet.ui

import androidx.fragment.app.FragmentActivity
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * Die Frage nach Kamera und Mitteilungen darf die App nicht beenden.
 *
 * Am 14.11.2026 stuerzte die Release-Fassung bei jeder solchen Frage ab:
 * fragment 1.2.5 (ueber biometric 1.1.0 und appcompat 1.2.0 hereingezogen)
 * prueft in FragmentActivity, dass eine Anfragenummer in 16 Bit passt, und
 * activity 1.13 vergibt fuer rememberLauncherForActivityResult Nummern ab
 * 0x10000. Die Debug-Fassung fiel nie auf, weil sie beides schon erlaubt
 * hatte und darum nie fragte.
 *
 * Der Test sieht in die FragmentActivity, die wirklich im Klassenpfad liegt,
 * und sucht die Meldung der alten Pruefung. Faellt die Zeile
 * `androidx.fragment:fragment` in build.gradle.kts weg, wird er rot.
 */
class ErlaubnisfrageTest {

    @Test
    fun `FragmentActivity lehnt grosse Anfragenummern nicht ab`() {
        val strom = FragmentActivity::class.java.classLoader!!
            .getResourceAsStream("androidx/fragment/app/FragmentActivity.class")
        assertNotNull("FragmentActivity nicht im Klassenpfad", strom)
        val inhalt = strom!!.use { it.readBytes() }.toString(Charsets.ISO_8859_1)
        assertFalse(
            "Alte FragmentActivity mit 16-Bit-Pruefung im Klassenpfad",
            inhalt.contains("lower 16 bits"),
        )
    }
}
