package com.yskar.wallet.kontakte

import com.yskar.wallet.kontakte.Kontakte.Ablehnung
import com.yskar.wallet.kontakte.Kontakte.Ergebnis
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Kontakte: ein Name fuer eine Adresse - und die Regeln, die verhindern,
 * dass aus der Lesehilfe eine Falle wird.
 *
 * Die Adressen sind die oeffentlichen Pruefadressen der anderen Tests;
 * dahinter liegt nichts, was diesem Test gehoert.
 */
class KontakteTest {

    private val anna = "ysr1j8nnl4kzwdck9u6ddltp8dmvauhg6rez967syq"
    private val ben = "ysr17g5ywa80hf2nyjkp59ha6a3ycs0jtsule8a6v4"

    private fun ok(e: Ergebnis): List<Kontakt> {
        assertTrue("erwartet Ok, war $e", e is Ergebnis.Ok)
        return (e as Ergebnis.Ok).liste
    }

    private fun grund(e: Ergebnis): Ablehnung {
        assertTrue("erwartet Ablehnung, war $e", e is Ergebnis.Abgelehnt)
        return (e as Ergebnis.Abgelehnt).grund
    }

    // ------------------------------------------------------------- Anlegen

    @Test
    fun `ein Name und eine gueltige Adresse ergeben einen Kontakt`() {
        val liste = ok(Kontakte.speichern(emptyList(), "Anna", anna))
        assertEquals(listOf(Kontakt("Anna", anna)), liste)
        assertEquals("Anna", Kontakte.nameFuer(liste, anna))
        assertNull(Kontakte.nameFuer(liste, ben))
        assertNull(Kontakte.nameFuer(liste, null))
    }

    @Test
    fun `ohne Namen kein Kontakt`() {
        assertEquals(Ablehnung.NAME_LEER, grund(Kontakte.speichern(emptyList(), "", anna)))
        assertEquals(Ablehnung.NAME_LEER, grund(Kontakte.speichern(emptyList(), "   \t ", anna)))
    }

    @Test
    fun `ein Name hat hoechstens vierzig Zeichen`() {
        ok(Kontakte.speichern(emptyList(), "a".repeat(40), anna))
        assertEquals(Ablehnung.NAME_ZU_LANG, grund(Kontakte.speichern(emptyList(), "a".repeat(41), anna)))
    }

    @Test
    fun `gezaehlt werden Zeichen, nicht Bytes`() {
        // Vierzig Umlaute sind vierzig Zeichen, auch wenn sie achtzig Bytes sind.
        ok(Kontakte.speichern(emptyList(), "ä".repeat(40), anna))
    }

    @Test
    fun `eine ungueltige Adresse wird nicht gespeichert`() {
        // Ein gekipptes Zeichen: Die Pruefsumme der Adresse merkt es.
        val gekippt = anna.dropLast(1) + (if (anna.last() == 'q') 'p' else 'q')
        assertEquals(Ablehnung.ADRESSE_UNGUELTIG, grund(Kontakte.speichern(emptyList(), "Anna", gekippt)))
        assertEquals(Ablehnung.ADRESSE_UNGUELTIG, grund(Kontakte.speichern(emptyList(), "Anna", "")))
        assertEquals(Ablehnung.ADRESSE_UNGUELTIG, grund(Kontakte.speichern(emptyList(), "Anna", "Anna")))
        // Ein Zahlungslink ist keine Adresse.
        assertEquals(
            Ablehnung.ADRESSE_UNGUELTIG,
            grund(Kontakte.speichern(emptyList(), "Anna", "yskar:$anna?amount=5")),
        )
    }

    @Test
    fun `Leerraum um die Adresse stoert nicht`() {
        val liste = ok(Kontakte.speichern(emptyList(), "Anna", "  $anna\n"))
        assertEquals(anna, liste.single().adresse)
    }

    @Test
    fun `eine Adresse hat nur einen Namen - und die Ablehnung nennt ihn`() {
        val liste = ok(Kontakte.speichern(emptyList(), "Anna", anna))
        val e = Kontakte.speichern(liste, "Annas Zweitname", anna)
        assertEquals(Ablehnung.ADRESSE_SCHON_DA, grund(e))
        assertEquals(Kontakt("Anna", anna), (e as Ergebnis.Abgelehnt).vorhanden)
    }

    @Test
    fun `zwei Adressen duerfen denselben Namen tragen`() {
        // Zwei Menschen koennen Anna heissen. Verwechselt wird trotzdem
        // nichts: Beim Pruefen steht die Adresse da.
        val liste = ok(Kontakte.speichern(ok(Kontakte.speichern(emptyList(), "Anna", anna)), "Anna", ben))
        assertEquals(2, liste.size)
    }

    // -------------------------------------------------------------- Aendern

    @Test
    fun `umbenennen behaelt die Adresse`() {
        val liste = ok(Kontakte.speichern(emptyList(), "Anna", anna))
        val neu = ok(Kontakte.speichern(liste, "Anna Schmidt", anna, bisher = anna))
        assertEquals(listOf(Kontakt("Anna Schmidt", anna)), neu)
    }

    @Test
    fun `die Adresse eines Kontakts laesst sich aendern`() {
        val liste = ok(Kontakte.speichern(emptyList(), "Anna", anna))
        val neu = ok(Kontakte.speichern(liste, "Anna", ben, bisher = anna))
        assertEquals(listOf(Kontakt("Anna", ben)), neu)
    }

    @Test
    fun `beim Aendern darf die Adresse nicht die eines anderen werden`() {
        var liste = ok(Kontakte.speichern(emptyList(), "Anna", anna))
        liste = ok(Kontakte.speichern(liste, "Ben", ben))
        assertEquals(
            Ablehnung.ADRESSE_SCHON_DA,
            grund(Kontakte.speichern(liste, "Ben", anna, bisher = ben)),
        )
    }

    @Test
    fun `entfernen nimmt genau den einen`() {
        var liste = ok(Kontakte.speichern(emptyList(), "Anna", anna))
        liste = ok(Kontakte.speichern(liste, "Ben", ben))
        assertEquals(listOf(Kontakt("Ben", ben)), Kontakte.entfernen(liste, anna))
        // Eine unbekannte Adresse aendert nichts.
        assertEquals(liste, Kontakte.entfernen(liste, "ysr1unbekannt"))
    }

    @Test
    fun `die Liste steht nach Namen, gross und klein gleich`() {
        var liste = ok(Kontakte.speichern(emptyList(), "ben", ben))
        liste = ok(Kontakte.speichern(liste, "Anna", anna))
        assertEquals(listOf("Anna", "ben"), liste.map { it.name })
    }

    // ---------------------------------------------------------------- Namen

    @Test
    fun `ein Name verliert Raender und doppelten Leerraum`() {
        assertEquals("Anna Schmidt", Kontakte.nameBereinigt("  Anna \t  Schmidt\n"))
    }

    @Test
    fun `Zeichen, die die Schreibrichtung drehen, kommen nicht in einen Namen`() {
        // U+202E dreht alles Folgende um: Aus "Anna" + U+202E + "1rsy"
        // wuerde auf dem Bildschirm "Annaysr1". So saehe ein Name aus wie
        // der Anfang einer Adresse.
        assertEquals("Anna1rsy", Kontakte.nameBereinigt("Anna‮1rsy"))
        assertEquals("Anna", Kontakte.nameBereinigt("⁦An​na⁩﻿"))
    }

    @Test
    fun `ein Name aus lauter Unsichtbarem ist leer`() {
        assertEquals(Ablehnung.NAME_LEER, grund(Kontakte.speichern(emptyList(), "​‮\u0007", anna)))
    }

    @Test
    fun `Umlaute, Emoji und andere Schriften bleiben`() {
        assertEquals("Jörg 🚀 Ольга", Kontakte.nameBereinigt("Jörg 🚀 Ольга"))
    }

    // --------------------------------------------------------------- Ablage

    @Test
    fun `was geschrieben wird, kommt genauso zurueck`() {
        var liste = ok(Kontakte.speichern(emptyList(), "Anna Schmidt", anna))
        liste = ok(Kontakte.speichern(liste, "Ben · Büro", ben))
        assertEquals(liste, Kontakte.ausText(Kontakte.alsText(liste)))
    }

    @Test
    fun `eine leere Liste ist ein leerer Text`() {
        assertEquals("", Kontakte.alsText(emptyList()))
        assertEquals(emptyList<Kontakt>(), Kontakte.ausText(""))
    }

    @Test
    fun `beim Lesen wird jede Zeile geprueft wie eine neue Eingabe`() {
        val text = listOf(
            "$anna\tAnna",
            "ysr1kaputt\tMallory",          // ungueltige Adresse
            "$ben\t‮",                 // Name aus Unsichtbarem
            "ohne Tabulator",
            "$anna\tNoch einmal Anna",      // dieselbe Adresse zum zweiten Mal
            "$ben\tBen",
        ).joinToString("\n")
        assertEquals(
            listOf(Kontakt("Anna", anna), Kontakt("Ben", ben)),
            Kontakte.ausText(text),
        )
    }
}
