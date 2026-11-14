package com.yskar.wallet.ui

import com.yskar.wallet.netz.Art
import com.yskar.wallet.netz.VerlaufEintrag
import com.yskar.wallet.sprache.Deutsch
import com.yskar.wallet.sprache.Englisch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * "Heute", "Gestern", "Aktualisiert 18:17" - und wo diese Woerter kippen.
 *
 * Geprueft wird an den Kanten: eine Minute vor und eine nach Mitternacht,
 * der Jahreswechsel, die fehlende Zeit. In der Mitte des Tages irrt sich
 * niemand.
 */
class ZeitenTest {

    private val berlin = ZoneId.of("Europe/Berlin")
    private val heute = LocalDate.of(2026, 11, 10)

    private fun um(jahr: Int, monat: Int, tag: Int, stunde: Int, minute: Int): Long =
        LocalDateTime.of(jahr, monat, tag, stunde, minute).atZone(berlin).toEpochSecond()

    private fun eintrag(txid: String, zeit: Long?) = VerlaufEintrag(
        txid = txid,
        height = 9472,
        idx = 0,
        zeit = zeit,
        art = Art.EIN,
        gegenseite = "ysr1j8nnl4kzwdck9u6ddltp8dmvauhg6rez967syq",
        anteile = null,
        betrag = 100_000,
        gebuehr = 16_800,
        notiz = "",
    )

    // ------------------------------------------------------- Tagesgruppen

    @Test
    fun `eine Minute nach Mitternacht ist heute`() {
        assertEquals(Tagesgruppe.HEUTE, tagesgruppe(um(2026, 11, 10, 0, 1), heute, berlin))
    }

    @Test
    fun `eine Minute vor Mitternacht ist gestern`() {
        assertEquals(Tagesgruppe.GESTERN, tagesgruppe(um(2026, 11, 9, 23, 59), heute, berlin))
    }

    @Test
    fun `vorgestern ist frueher`() {
        assertEquals(Tagesgruppe.FRUEHER, tagesgruppe(um(2026, 11, 8, 23, 59), heute, berlin))
    }

    @Test
    fun `ohne Zeit steht eine Bewegung unter frueher`() {
        assertEquals(Tagesgruppe.FRUEHER, tagesgruppe(null, heute, berlin))
    }

    @Test
    fun `eine Zeit in der Zukunft zaehlt als heute`() {
        // Die Uhr des Geraets geht nach; die Bewegung ist trotzdem da.
        assertEquals(Tagesgruppe.HEUTE, tagesgruppe(um(2026, 11, 11, 3, 0), heute, berlin))
    }

    @Test
    fun `der Tag haengt an der Zone, nicht an UTC`() {
        // 00:30 in Berlin ist 23:30 UTC am Vortag.
        val zeit = um(2026, 11, 10, 0, 30)
        assertEquals(Tagesgruppe.HEUTE, tagesgruppe(zeit, heute, berlin))
        assertEquals(Tagesgruppe.GESTERN, tagesgruppe(zeit, heute, ZoneId.of("UTC")))
    }

    @Test
    fun `gestern ueber den Monatswechsel`() {
        val erster = LocalDate.of(2026, 12, 1)
        assertEquals(Tagesgruppe.GESTERN, tagesgruppe(um(2026, 11, 30, 12, 0), erster, berlin))
    }

    @Test
    fun `nachTagen ordnet Heute vor Gestern vor Frueher und laesst leere Gruppen weg`() {
        val a = eintrag("a", um(2026, 11, 10, 10, 52))
        val b = eintrag("b", um(2026, 11, 8, 9, 52))
        val c = eintrag("c", um(2026, 11, 10, 8, 0))
        val gruppen = nachTagen(listOf(a, b, c), heute, berlin)

        assertEquals(listOf(Tagesgruppe.HEUTE, Tagesgruppe.FRUEHER), gruppen.map { it.first })
        // Innerhalb der Gruppe bleibt die Reihenfolge, wie sie kam.
        assertEquals(listOf("a", "c"), gruppen[0].second.map { it.txid })
        assertEquals(listOf("b"), gruppen[1].second.map { it.txid })
    }

    @Test
    fun `nachTagen verliert keinen Eintrag`() {
        val alle = listOf(
            eintrag("a", um(2026, 11, 10, 10, 0)),
            eintrag("b", um(2026, 11, 9, 10, 0)),
            eintrag("c", null),
            eintrag("d", um(2025, 1, 1, 0, 0)),
        )
        assertEquals(4, nachTagen(alle, heute, berlin).sumOf { it.second.size })
    }

    // ------------------------------------------------------ Zeit je Gruppe

    @Test
    fun `unter Heute und Gestern steht nur die Uhrzeit`() {
        val zeit = um(2026, 11, 10, 10, 52)
        assertEquals("10:52", zeitInGruppe(zeit, Tagesgruppe.HEUTE, berlin, Deutsch))
        assertEquals("10:52", zeitInGruppe(zeit, Tagesgruppe.GESTERN, berlin, Deutsch))
    }

    @Test
    fun `unter Frueher steht das Datum dabei`() {
        assertEquals(
            "08.11.2026, 09:52",
            zeitInGruppe(um(2026, 11, 8, 9, 52), Tagesgruppe.FRUEHER, berlin, Deutsch),
        )
    }

    @Test
    fun `ohne Zeit steht da, dass sie unbekannt ist`() {
        assertEquals("Zeit unbekannt", zeitInGruppe(null, Tagesgruppe.FRUEHER, berlin, Deutsch))
    }

    // --------------------------------------------------------------- Stand

    @Test
    fun `ein Stand von heute nennt nur die Uhrzeit`() {
        assertEquals("Aktualisiert 18:17", standText(um(2026, 11, 10, 18, 17), heute, berlin, Deutsch))
    }

    @Test
    fun `ein Stand von gestern nennt das Datum`() {
        // Auch eine Minute vor Mitternacht: "Aktualisiert 23:59" saehe aus wie heute.
        assertEquals("Aktualisiert 09.11., 23:59", standText(um(2026, 11, 9, 23, 59), heute, berlin, Deutsch))
    }

    @Test
    fun `ein Stand aus dem Vorjahr nennt das Jahr`() {
        val neujahr = LocalDate.of(2027, 1, 1)
        assertEquals(
            "Aktualisiert 31.12.2026, 23:59",
            standText(um(2026, 12, 31, 23, 59), neujahr, berlin, Deutsch),
        )
    }

    @Test
    fun `ohne bekannten Stand gibt es keinen Text`() {
        assertNull(standText(null, heute, berlin, Deutsch))
    }

    // -------------------------------------------------------------- Zaehler

    @Test
    fun `solange Seiten fehlen, heisst es geladen`() {
        assertEquals("4 geladen", zaehlerText(4, vollstaendig = false, t = Deutsch))
        assertEquals("1 geladen", zaehlerText(1, vollstaendig = false, t = Deutsch))
    }

    @Test
    fun `ist der Verlauf vollstaendig, sind es Eintraege`() {
        assertEquals("4 Einträge", zaehlerText(4, vollstaendig = true, t = Deutsch))
        assertEquals("1 Eintrag", zaehlerText(1, vollstaendig = true, t = Deutsch))
        assertEquals("0 Einträge", zaehlerText(0, vollstaendig = true, t = Deutsch))
    }

    @Test
    fun `Blocknummern bekommen den Tausenderpunkt`() {
        assertEquals("9.472", zahlText(9_472, Deutsch))
        assertEquals("572", zahlText(572, Deutsch))
        assertEquals("1.234.567", zahlText(1_234_567, Deutsch))
    }
}

/**
 * Was neben den fuenf Schritten einer Ueberweisung steht - und vor allem,
 * was dort NICHT steht, wenn es niemand weiss.
 */
class SchrittAngabeTest {

    private val berlin = ZoneId.of("Europe/Berlin")
    private val um181702 =
        LocalDateTime.of(2026, 11, 10, 18, 17, 2).atZone(berlin).toEpochSecond()

    private val zeiten = Sendezeiten(
        unterschrieben = um181702,
        angenommen = um181702 + 1,
        warteliste = um181702 + 1,
    )

    private fun angabe(
        nr: Int,
        teil: com.yskar.wallet.netz.Sendestatus.Teil?,
        zeiten: Sendezeiten = this.zeiten,
        block: Long? = 8_602,
        bestaetigungen: Int = 2,
        geprueft: Long? = um181702 + 2,
        jetzt: Long = um181702 + 10,
    ) = schrittAngabe(nr, teil, zeiten, block, bestaetigungen, geprueft, jetzt, berlin, Deutsch)

    private val erledigt = com.yskar.wallet.netz.Sendestatus.Teil.ERLEDIGT
    private val wartet = com.yskar.wallet.netz.Sendestatus.Teil.WARTET
    private val offen = com.yskar.wallet.netz.Sendestatus.Teil.OFFEN

    @Test
    fun `die ersten drei Schritte nennen die Uhrzeit des Geraets`() {
        assertEquals("18:17:02", angabe(0, erledigt))
        assertEquals("18:17:03", angabe(1, erledigt))
        assertEquals("18:17:03", angabe(2, erledigt))
    }

    @Test
    fun `der Block nennt seine Nummer mit Tausenderpunkt`() {
        assertEquals("Block 8.602", angabe(3, erledigt))
    }

    @Test
    fun `die Bestaetigungen nennen ihre Zahl`() {
        assertEquals("2", angabe(4, erledigt))
        assertEquals("30", angabe(4, erledigt, bestaetigungen = 30))
    }

    @Test
    fun `ohne bekannte Zeit bleibt es beim Wort`() {
        // Die App wurde zwischendurch beendet: Die Zeiten sind weg.
        val leer = Sendezeiten()
        assertEquals("erledigt", angabe(0, erledigt, zeiten = leer))
        assertEquals("erledigt", angabe(1, erledigt, zeiten = leer))
        assertEquals("erledigt", angabe(2, erledigt, zeiten = leer))
    }

    @Test
    fun `ein Schritt erbt keine Zeit vom naechsten`() {
        // Ging die Ueberweisung in einen Block, ohne je sichtbar in der
        // Warteliste zu stehen, hat die Warteliste keine Uhrzeit - auch
        // wenn davor und danach welche stehen.
        val ohneWarteliste = zeiten.copy(warteliste = null)
        assertEquals("erledigt", angabe(2, erledigt, zeiten = ohneWarteliste))
        assertEquals("18:17:03", angabe(1, erledigt, zeiten = ohneWarteliste))
    }

    @Test
    fun `ohne bekannten Block bleibt es beim Wort`() {
        assertEquals("erledigt", angabe(3, erledigt, block = null))
    }

    @Test
    fun `ein wartender Schritt sagt, wie lange die letzte Nachfrage her ist`() {
        assertEquals("geprüft vor 8 s", angabe(2, wartet))
        assertEquals("wartet", angabe(2, wartet, geprueft = null))
    }

    @Test
    fun `ein offener Schritt heisst offen`() {
        assertEquals("offen", angabe(3, offen))
        assertEquals("offen", angabe(4, offen))
    }

    @Test
    fun `ohne Schritte steht ein Strich da`() {
        assertEquals("—", angabe(0, null))
    }

    @Test
    fun `das Alter der Nachfrage in Sekunden, Minuten, Stunden`() {
        assertEquals("geprüft eben", geprueftText(0, Deutsch))
        assertEquals("geprüft eben", geprueftText(-3, Deutsch))
        assertEquals("geprüft vor 1 s", geprueftText(1, Deutsch))
        assertEquals("geprüft vor 59 s", geprueftText(59, Deutsch))
        assertEquals("geprüft vor 1 min", geprueftText(60, Deutsch))
        assertEquals("geprüft vor 59 min", geprueftText(3_599, Deutsch))
        assertEquals("geprüft vor 1 h", geprueftText(3_600, Deutsch))
    }
}

/** Der Betrag auf dem Sendebildschirm: gross, und nie abgeschnitten. */
class BetragSchriftgroesseTest {

    @Test
    fun `ein kurzer Betrag steht in voller Groesse`() {
        assertEquals(54f, betragSchriftgroesse(0), 0f)
        assertEquals(54f, betragSchriftgroesse(4), 0f)
        assertEquals(54f, betragSchriftgroesse(7), 0f)
    }

    @Test
    fun `ein laengerer Betrag wird kleiner und behaelt seine Breite`() {
        // Zeichen mal Groesse bleibt gleich: 7 * 54 = 378.
        for (n in 8..15) {
            assertEquals("$n Zeichen", 378f, n * betragSchriftgroesse(n), 0.01f)
        }
        assertEquals(27f, betragSchriftgroesse(14), 0f)
    }

    @Test
    fun `kleiner als 24 wird die Schrift nicht`() {
        assertEquals(24f, betragSchriftgroesse(16), 0f)
        assertEquals(24f, betragSchriftgroesse(40), 0f)
    }

    @Test
    fun `die Groesse faellt nie, wenn ein Zeichen wegfaellt`() {
        for (n in 1..40) {
            assert(betragSchriftgroesse(n - 1) >= betragSchriftgroesse(n)) { "bei $n" }
        }
    }
}

/**
 * Dieselben Zeiten auf Englisch: andere Woerter, das Datum mit
 * ausgeschriebenem Monat, die Zahl mit Komma als Tausendertrenner. Die
 * Uhrzeit bleibt bei 24 Stunden.
 */
class ZeitenEnglischTest {

    private val berlin = ZoneId.of("Europe/Berlin")
    private val heute = LocalDate.of(2026, 11, 10)

    private fun um(jahr: Int, monat: Int, tag: Int, stunde: Int, minute: Int): Long =
        LocalDateTime.of(jahr, monat, tag, stunde, minute).atZone(berlin).toEpochSecond()

    @Test
    fun `die Gruppen heissen Today, Yesterday, Earlier`() {
        assertEquals("Today", Tagesgruppe.HEUTE.titel(Englisch))
        assertEquals("Yesterday", Tagesgruppe.GESTERN.titel(Englisch))
        assertEquals("Earlier", Tagesgruppe.FRUEHER.titel(Englisch))
        assertEquals("Heute", Tagesgruppe.HEUTE.titel(Deutsch))
        assertEquals("Gestern", Tagesgruppe.GESTERN.titel(Deutsch))
        assertEquals("Früher", Tagesgruppe.FRUEHER.titel(Deutsch))
    }

    @Test
    fun `das Datum steht mit ausgeschriebenem Monat`() {
        assertEquals(
            "8 Nov 2026, 09:52",
            zeitInGruppe(um(2026, 11, 8, 9, 52), Tagesgruppe.FRUEHER, berlin, Englisch),
        )
        assertEquals("Time unknown", zeitInGruppe(null, Tagesgruppe.FRUEHER, berlin, Englisch))
    }

    @Test
    fun `der Stand - heute, dieses Jahr, frueher`() {
        assertEquals("Updated 18:17", standText(um(2026, 11, 10, 18, 17), heute, berlin, Englisch))
        assertEquals(
            "Updated 9 Nov, 23:59",
            standText(um(2026, 11, 9, 23, 59), heute, berlin, Englisch),
        )
        assertEquals(
            "Updated 31 Dec 2025, 23:59",
            standText(um(2025, 12, 31, 23, 59), heute, berlin, Englisch),
        )
    }

    @Test
    fun `Zaehler und Zahlen`() {
        assertEquals("4 loaded", zaehlerText(4, vollstaendig = false, t = Englisch))
        assertEquals("1 entry", zaehlerText(1, vollstaendig = true, t = Englisch))
        assertEquals("4 entries", zaehlerText(4, vollstaendig = true, t = Englisch))
        assertEquals("0 entries", zaehlerText(0, vollstaendig = true, t = Englisch))
        assertEquals("9,472", zahlText(9_472, Englisch))
        assertEquals("1,234,567", zahlText(1_234_567, Englisch))
    }

    @Test
    fun `wie lange die letzte Nachfrage her ist`() {
        assertEquals("checked just now", geprueftText(0, Englisch))
        assertEquals("checked 59 s ago", geprueftText(59, Englisch))
        assertEquals("checked 1 min ago", geprueftText(60, Englisch))
        assertEquals("checked 1 h ago", geprueftText(3_600, Englisch))
    }

    @Test
    fun `die Angabe neben einem Schritt`() {
        val keine = Sendezeiten()
        fun angabe(nr: Int, teil: com.yskar.wallet.netz.Sendestatus.Teil?, block: Long? = null) =
            schrittAngabe(nr, teil, keine, block, 0, null, 0, berlin, Englisch)

        assertEquals("not yet", angabe(4, com.yskar.wallet.netz.Sendestatus.Teil.OFFEN))
        assertEquals("waiting", angabe(3, com.yskar.wallet.netz.Sendestatus.Teil.WARTET))
        assertEquals("done", angabe(0, com.yskar.wallet.netz.Sendestatus.Teil.ERLEDIGT))
        assertEquals(
            "Block 9,472",
            angabe(3, com.yskar.wallet.netz.Sendestatus.Teil.ERLEDIGT, block = 9_472),
        )
    }
}

/**
 * Das Betragsfeld zeigt immer das Dezimalzeichen der Sprache - und die Zahl
 * dahinter bleibt dieselbe.
 */
class BetragImFeldTest {

    private fun einheiten(text: String) = com.yskar.wallet.netz.Betrag.ausEingabe(text)

    @Test
    fun `auf Deutsch wird der Punkt zum Komma`() {
        assertEquals("1,5", betragImFeld("1.5", Deutsch))
        assertEquals("1,5", betragImFeld("1,5", Deutsch))
        assertEquals("1500", betragImFeld("1500", Deutsch))
        assertEquals("", betragImFeld("", Deutsch))
    }

    @Test
    fun `auf Englisch wird das Komma zum Punkt`() {
        assertEquals("1.5", betragImFeld("1,5", Englisch))
        assertEquals("1.5", betragImFeld("1.5", Englisch))
        assertEquals("1500", betragImFeld("1500", Englisch))
    }

    @Test
    fun `wer einen Tausendertrenner tippt, sieht sofort ein Dezimalzeichen`() {
        // Deutsch "1.500" und englisch "1,500" sind fuer `Betrag.ausEingabe`
        // anderthalb. Im Feld steht darum, was gelesen wird.
        assertEquals("1,500", betragImFeld("1.500", Deutsch))
        assertEquals("1.500", betragImFeld("1,500", Englisch))
    }

    @Test
    fun `die Zahl dahinter aendert sich nicht`() {
        for (roh in listOf("1.5", "1,5", "0,00000001", "0.00000001", "1284,57310946", "25", "")) {
            assertEquals("deutsch $roh", einheiten(roh), einheiten(betragImFeld(roh, Deutsch)))
            assertEquals("englisch $roh", einheiten(roh), einheiten(betragImFeld(roh, Englisch)))
        }
    }

    @Test
    fun `ein eingefuegter Betrag mit beiden Zeichen wird unlesbar statt geraten`() {
        assertEquals("1,284,57", betragImFeld("1.284,57", Deutsch))
        assertEquals("1.284.57", betragImFeld("1,284.57", Englisch))
        // Unlesbar heisst fuer den Pruefschritt: null - "Der Betrag fehlt
        // oder ist nicht lesbar."
        assertEquals(0L, einheiten(betragImFeld("1.284,57", Deutsch)))
        assertEquals(0L, einheiten(betragImFeld("1,284.57", Englisch)))
    }
}

/**
 * `betragGeteilt`: Ein Betrag wird in zwei Teilen GESETZT - die ganze Zahl
 * gross, alles hinter dem Komma klein -, aber nie veraendert. Beide Teile
 * hintereinander muessen Zeichen fuer Zeichen ergeben, was hineinging
 * (REGELN.md, Regel 11).
 */
class BetragGeteiltTest {

    private val de = java.util.Locale.GERMANY
    private val en = java.util.Locale.UK

    @Test
    fun `die ganze Zahl vorn, alle Nachkommastellen dahinter`() {
        assertEquals("1.284" to ",57310946", betragGeteilt("1.284,57310946", de))
        assertEquals("1,284" to ".57310946", betragGeteilt("1,284.57310946", en))
    }

    @Test
    fun `das Vorzeichen gehoert zur ganzen Zahl`() {
        assertEquals("+1" to ",00", betragGeteilt("+1,00", de))
        assertEquals("−25" to ",00", betragGeteilt("−25,00", de))
    }

    @Test
    fun `die kleinste Einheit verschwindet nicht`() {
        assertEquals("0" to ",00000001", betragGeteilt("0,00000001", de))
        assertEquals("0" to ",001", betragGeteilt("0,001", de))
    }

    @Test
    fun `der Tausenderpunkt ist kein Komma`() {
        // Deutsch: "1.284" ist eine ganze Zahl, der Punkt trennt Tausender.
        assertEquals("1.284" to "", betragGeteilt("1.284", de))
        // Englisch: dasselbe mit dem Komma.
        assertEquals("1,284" to "", betragGeteilt("1,284", en))
    }

    @Test
    fun `beide Teile zusammen sind Zeichen fuer Zeichen der Betrag`() {
        for (locale in listOf(de, en)) {
            for (einheiten in listOf(1L, 168L, 16_800L, 100_000L, 150_000_000L, 128_457_310_946L, 127_645_903_218_574L)) {
                val genau = com.yskar.wallet.netz.Betrag.genau(einheiten, locale)
                val (ganz, bruch) = betragGeteilt(genau, locale)
                assertEquals(genau, ganz + bruch)
            }
        }
    }

    @Test
    fun `was keine Zahl ist, bleibt wie es ist`() {
        assertEquals("••••" to "", betragGeteilt("••••", de))
        assertEquals("—" to "", betragGeteilt("—", de))
    }
}
