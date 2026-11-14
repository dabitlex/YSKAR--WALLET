package com.yskar.wallet.sprache

import com.yskar.wallet.netz.Betrag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.Locale

/**
 * Die Texte beider Sprachen, alle auf einmal.
 *
 * Dass in keiner Sprache ein Text FEHLT, prueft schon der Compiler: Jeder
 * Bereich ist ein Interface mit zwei Fassungen. Hier wird geprueft, was er
 * nicht sieht - ein leerer Text, ein deutscher Satz in der englischen
 * Fassung, eine Zahl, die in der Uebersetzung verloren ging.
 *
 * Die Bereiche werden ueber `Texte` selbst gefunden, nicht aufgezaehlt: Ein
 * neuer Bereich ist ohne Zutun dabei.
 */
class TexteTest {

    /** Alle Bereiche einer Sprache: Name des Bereichs und sein Objekt. */
    private fun bereiche(t: Texte): Map<String, Any> =
        Texte::class.java.methods
            .filter { it.parameterCount == 0 && it.name.startsWith("get") }
            .filter { it.returnType.isInterface && it.returnType.name.endsWith("Texte") }
            .associate { it.name.removePrefix("get") to it.invoke(t)!! }

    /** Die Texte eines Bereichs: Funktionen seines Interfaces, die eine Zeichenkette liefern. */
    private fun textquellen(bereich: Any): List<Method> =
        bereich.javaClass.interfaces
            .flatMap { it.methods.toList() }
            .filter { it.returnType == String::class.java && !Modifier.isStatic(it.modifiers) }
            .sortedBy { it.name + it.parameterCount }

    /**
     * Beispielwerte fuer Texte mit Platzhaltern. Das Wort "QQQ" kommt in
     * keinem Text von selbst vor - so laesst sich sehen, ob es ankommt.
     */
    private fun beispiele(m: Method, zahl: Int): Array<Any> = m.parameterTypes.map { typ ->
        when (typ) {
            // Auch die verpackten Typen: Ein `Int?` kommt als `Integer` an.
            java.lang.Integer.TYPE, java.lang.Integer::class.java -> zahl
            java.lang.Long.TYPE, java.lang.Long::class.java -> zahl.toLong()
            java.lang.Boolean.TYPE, java.lang.Boolean::class.java -> true
            String::class.java -> "QQQ"
            // Ein Text je Wert einer Aufzaehlung (ein Grund, eine Stoerung):
            // hier der erste; alle Werte prueft `SignaturTexteTest`.
            else -> if (typ.isEnum) typ.enumConstants!![0]
            else error("Beispielwert fehlt fuer ${typ.name} in ${m.name}")
        }
    }.toTypedArray()

    private fun text(bereich: Any, m: Method, zahl: Int = 7): String {
        m.isAccessible = true
        return m.invoke(bereich, *beispiele(m, zahl)) as String
    }

    private fun alleTexte(t: Texte): Map<String, String> {
        val alle = linkedMapOf<String, String>()
        for ((name, bereich) in bereiche(t)) {
            for (m in textquellen(bereich)) alle["$name.${m.name}"] = text(bereich, m)
        }
        return alle
    }

    @Test
    fun `beide Sprachen haben dieselben Bereiche und dieselben Texte`() {
        assertEquals(alleTexte(Deutsch).keys, alleTexte(Englisch).keys)
        assertTrue("zu wenige Texte gefunden", alleTexte(Deutsch).size > 200)
    }

    @Test
    fun `kein Text ist leer`() {
        for (t in listOf(Deutsch, Englisch)) {
            for ((name, text) in alleTexte(t)) {
                assertTrue("${t.sprache} $name ist leer", text.isNotBlank())
            }
        }
    }

    @Test
    fun `im Englischen steht kein Umlaut`() {
        // Ein Umlaut in einem englischen Text ist ein deutscher Satz, der
        // beim Uebersetzen stehen geblieben ist.
        val umlaut = Regex("[äöüÄÖÜß]")
        for ((name, text) in alleTexte(Englisch)) {
            assertFalse("Englisch $name: $text", umlaut.containsMatchIn(text))
        }
    }

    @Test
    fun `das Deutsche schreibt Umlaute aus`() {
        // "fuer", "ueber", "koennen" in einem angezeigten Text waeren ein
        // Kommentar, der in ein Textliteral gerutscht ist (REGELN.md, Regel 16).
        val ersatz = Regex(
            "\\b(fuer|ueber|koenn|waehr|zurueck|pruef|geraet|woert|zwoelf|aender|loesch|gueltig)",
            RegexOption.IGNORE_CASE,
        )
        for ((name, text) in alleTexte(Deutsch)) {
            assertFalse("Deutsch $name: $text", ersatz.containsMatchIn(text))
        }
    }

    @Test
    fun `was hineingegeben wird, kommt in beiden Sprachen im Satz an`() {
        for (t in listOf(Deutsch, Englisch)) {
            for ((name, bereich) in bereiche(t)) {
                for (m in textquellen(bereich)) {
                    if (m.parameterCount == 0) continue
                    val satz = text(bereich, m, 7)
                    val stelle = "${t.sprache} $name.${m.name}: $satz"

                    if (m.parameterTypes.any { it == String::class.java }) {
                        assertTrue("Wort geht verloren - $stelle", satz.contains("QQQ"))
                    }
                    // Ein Text darf eine Zahl auch in Worten sagen ("ein
                    // Wort", "one did not fit") - verlangt wird nur, dass er
                    // sich mit dem Wert AENDERT.
                    val hatZahl = m.parameterTypes.any {
                        it == java.lang.Integer.TYPE || it == java.lang.Long.TYPE ||
                            it == java.lang.Integer::class.java || it == java.lang.Long::class.java
                    }
                    if (hatZahl) {
                        assertTrue("Zahl geht verloren - $stelle", text(bereich, m, 8) != satz)
                    }
                }
            }
        }
    }

    @Test
    fun `wo beide Sprachen dasselbe sagen, ist es ein Name und kein Satz`() {
        // Gleiche Texte sind erlaubt - "Explorer", "PIN", "Vibration", ein
        // Datumsmuster. Ein ganzer Satz, der in beiden Sprachen gleich ist,
        // ist ein vergessener.
        val de = alleTexte(Deutsch)
        val en = alleTexte(Englisch)
        val saetze = de.filter { (name, text) ->
            en[name] == text && text.any { it.isLetter() } && text.trim().count { it == ' ' } >= 4
        }
        assertTrue("In beiden Sprachen gleich, aber ein ganzer Satz: $saetze", saetze.isEmpty())
    }
}

class SpracheTest {

    @Test
    fun `die Kennung findet die Sprache wieder`() {
        for (s in Sprache.entries) assertSame(s, Sprache.ausKennung(s.kennung))
    }

    @Test
    fun `nichts Gespeichertes und Unbekanntes sind keine Wahl`() {
        assertNull(Sprache.ausKennung(null))
        assertNull(Sprache.ausKennung(""))
        assertNull(Sprache.ausKennung("fr"))
    }

    @Test
    fun `ein deutsches Geraet beginnt deutsch, jedes andere englisch`() {
        assertSame(Sprache.DEUTSCH, Sprache.fuerSystem(Locale.GERMANY))
        assertSame(Sprache.DEUTSCH, Sprache.fuerSystem(Locale.forLanguageTag("de-AT")))
        assertSame(Sprache.DEUTSCH, Sprache.fuerSystem(Locale.forLanguageTag("de-CH")))
        assertSame(Sprache.ENGLISCH, Sprache.fuerSystem(Locale.UK))
        assertSame(Sprache.ENGLISCH, Sprache.fuerSystem(Locale.US))
        assertSame(Sprache.ENGLISCH, Sprache.fuerSystem(Locale.FRANCE))
        assertSame(Sprache.ENGLISCH, Sprache.fuerSystem(Locale.forLanguageTag("tr-TR")))
    }

    @Test
    fun `jede Sprache hat ihre Texte, und die kennen ihre Sprache`() {
        for (s in Sprache.entries) assertSame(s, texteFuer(s).sprache)
        assertSame(Deutsch, texteFuer(Sprache.DEUTSCH))
        assertSame(Englisch, texteFuer(Sprache.ENGLISCH))
    }

    @Test
    fun `Zahlen stehen deutsch mit Komma, englisch mit Punkt`() {
        val betrag = 128_457_310_946L
        assertEquals("1.284,57310946", Betrag.genau(betrag, Deutsch.locale))
        assertEquals("1,284.57310946", Betrag.genau(betrag, Englisch.locale))
    }

    @Test
    fun `die Meldungen des Tresors stehen in beiden Sprachen mit richtigen Zeichen`() {
        // Der Tresor schreibt seine Gruende ohne Umlaute; angezeigt werden sie
        // uebersetzt. Ein unbekannter Grund geht nicht verloren.
        assertEquals("Die PIN ist zu kurz.", Deutsch.modell.tresorFehler("PIN zu kurz"))
        assertEquals("The PIN is too short.", Englisch.modell.tresorFehler("PIN zu kurz"))
        assertEquals(
            "Auf diesem Gerät liegt bereits eine Wallet.",
            Deutsch.modell.tresorFehler("Es gibt bereits einen Tresor auf diesem Geraet"),
        )
        assertTrue(Deutsch.modell.tresorFehler("KeyStoreException: x").endsWith("KeyStoreException: x"))
        assertTrue(Englisch.modell.tresorFehler("KeyStoreException: x").endsWith("KeyStoreException: x"))
    }

    @Test
    fun `ein Wort ist ein Wort`() {
        assertEquals("Es ist 1 Wort - erwartet werden 12.", Deutsch.modell.woerterAnzahl(1))
        assertEquals("Es sind 11 Wörter - erwartet werden 12.", Deutsch.modell.woerterAnzahl(11))
        assertEquals("That is 1 word - 12 are expected.", Englisch.modell.woerterAnzahl(1))
        assertEquals("That is 13 words - 12 are expected.", Englisch.modell.woerterAnzahl(13))
    }
}

/** Jeder Grund, aus dem nicht signiert wird, hat in beiden Sprachen seinen eigenen Satz. */
class SignaturTexteTest {

    @Test
    fun `jede Ablehnung hat in beiden Sprachen einen eigenen Satz`() {
        for (t in listOf(Deutsch, Englisch)) {
            val saetze = com.yskar.wallet.signatur.Nachricht.Ablehnung.entries.map { t.signatur.grund(it) }
            assertTrue(saetze.all { it.isNotBlank() })
            assertEquals("${t.sprache}: zwei Gruende mit demselben Satz", saetze.size, saetze.toSet().size)
        }
    }

    @Test
    fun `jede Stoerung hat in beiden Sprachen einen eigenen Satz`() {
        for (t in listOf(Deutsch, Englisch)) {
            val saetze = com.yskar.wallet.signatur.Unterschrift.Stoerung.entries.map { t.signatur.stoerung(it) }
            assertTrue(saetze.all { it.isNotBlank() })
            assertEquals(saetze.size, saetze.toSet().size)
        }
    }

    @Test
    fun `kein englischer Satz enthaelt einen Umlaut`() {
        val umlaut = Regex("[äöüÄÖÜß„]")
        for (a in com.yskar.wallet.signatur.Nachricht.Ablehnung.entries) {
            assertFalse(umlaut.containsMatchIn(Englisch.signatur.grund(a)))
        }
        for (s in com.yskar.wallet.signatur.Unterschrift.Stoerung.entries) {
            assertFalse(umlaut.containsMatchIn(Englisch.signatur.stoerung(s)))
        }
    }

    @Test
    fun `wann eine Anmeldung ablaeuft`() {
        assertEquals("in unter 1 Min.", Deutsch.signatur.inMinuten(1))
        assertEquals("in 4 Min.", Deutsch.signatur.inMinuten(4))
        assertEquals("in under 1 min", Englisch.signatur.inMinuten(0))
        assertEquals("in 15 min", Englisch.signatur.inMinuten(15))
    }
}
