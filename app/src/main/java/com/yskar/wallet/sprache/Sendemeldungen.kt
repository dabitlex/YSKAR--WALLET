package com.yskar.wallet.sprache

/**
 * Uebersetzt die Meldungen aus `netz/Senden.kt`.
 *
 * `Senden.kt` prueft und unterschreibt; die Datei bleibt, wie sie ist, und
 * meldet auf Deutsch. Angezeigt wird ihre Meldung ueber diese Funktion.
 * Kennt sie eine Meldung nicht, gibt sie sie unveraendert zurueck - lieber
 * ein deutscher Satz als keiner. Sie wirft nie und verschluckt nichts.
 *
 * DIE DEUTSCHEN SAETZE HIER MUESSEN MIT `netz/Senden.kt` SCHRITT HALTEN.
 * Sie stehen dort als Textliterale und hier noch einmal, Zeichen fuer
 * Zeichen gleich. Aendert dort jemand einen Satz, findet ihn diese Datei
 * nicht mehr - die Meldung erscheint dann auf Deutsch, verloren geht sie
 * nicht. `SendemeldungenTest` bemerkt es: Er laesst `Senden.pruefe`,
 * `Senden.unterschreibenUndSenden` und `Senden.klartext` die Saetze selbst
 * sagen und vergleicht.
 *
 * Feste Saetze werden woertlich gesucht ([FEST]). Saetze mit Werten darin
 * - Staubgrenze, Laenge der Notiz, Gesamt und Fehlbetrag, Groessen in Byte,
 * HTTP-Status - ueber ein Muster ([MUSTER]); die Werte wandern hinueber.
 * Betraege schreibt `Senden.kt` deutsch ("1.284,57310946"); im englischen
 * Satz stehen sie englisch ("1,284.57310946"). Umgeschrieben werden nur die
 * Trennzeichen, keine Ziffer - gerechnet wird hier nichts.
 *
 * "angenommen" bleibt "accepted" und "eingereicht" bleibt "submitted":
 * Keiner dieser Saetze sagt auf Englisch mehr, als der deutsche sagt
 * (REGELN.md, Regel 20).
 */
object Sendemeldungen {

    fun uebersetze(deutsch: String, sprache: Sprache): String = when (sprache) {
        Sprache.DEUTSCH -> deutsch
        Sprache.ENGLISCH -> englisch(deutsch)
    }

    private fun englisch(deutsch: String): String {
        FEST[deutsch]?.let { return it }
        for (m in MUSTER) {
            val treffer = m.deutsch.matchEntire(deutsch) ?: continue
            return m.aufEnglisch(treffer.groupValues)
        }
        return deutsch
    }

    // ------------------------------------------------------- feste Saetze

    /** Deutsch, wie es in `netz/Senden.kt` steht -> Englisch. */
    private val FEST: Map<String, String> = mapOf(
        // --- Senden.pruefe
        "Der Kontostand ist unbekannt. Lade ihn zuerst, sonst stimmt " +
            "die Nonce nicht." to
            "The balance is unknown. Load it first, otherwise the nonce " +
            "will be wrong.",

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
            "A payment is still waiting for a block. Wait until it is " +
            "in - otherwise the nonces collide.",

        // --- Senden.unterschreibenUndSenden
        "Die Merkwörter aus dem Tresor sind unbrauchbar." to
            "The twelve words from the vault are unusable.",

        "Der Schlüssel passt nicht zur angezeigten Adresse. " +
            "Es wird nichts gesendet." to
            "The key does not match the address shown. " +
            "Nothing is sent.",

        "Die eigene Signatur ließ sich nicht bestätigen." to
            "The wallet's own signature could not be verified.",

        "Keine Verbindung zum Knoten. Die Überweisung wurde NICHT eingereicht." to
            "No connection to the node. The transfer was NOT submitted.",

        // --- Senden.klartext: die Ablehnungsgruende des Knotens
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

    // --------------------------------------------------- Saetze mit Werten

    /**
     * Ein deutscher Satz mit Werten darin und sein englisches Gegenstueck.
     * [aufEnglisch] bekommt die Gruppen des Treffers; Gruppe 0 ist der ganze Satz.
     */
    private class Muster(val deutsch: Regex, val aufEnglisch: (List<String>) -> String)

    /** Ein Betrag, wie `Betrag.genau` ihn deutsch schreibt: "1.284,57310946". */
    private const val BETRAG = "(-?[0-9]+(?:\\.[0-9]{3})*(?:,[0-9]+)?)"

    /** Eine ganze Zahl ohne Trenner: Bytes, HTTP-Status. */
    private const val ZAHL = "(-?[0-9]+)"

    private val PLATZHALTER = Regex("\\{(betrag|zahl)\\}")

    /**
     * Aus einer Vorlage ein Muster: `{betrag}` und `{zahl}` werden zu
     * Gruppen, alles andere gilt woertlich - auch Punkte und Klammern.
     */
    private fun muster(vorlage: String): Regex {
        val sb = StringBuilder()
        var pos = 0
        for (p in PLATZHALTER.findAll(vorlage)) {
            sb.append(Regex.escape(vorlage.substring(pos, p.range.first)))
            sb.append(if (p.groupValues[1] == "betrag") BETRAG else ZAHL)
            pos = p.range.last + 1
        }
        sb.append(Regex.escape(vorlage.substring(pos)))
        return Regex(sb.toString())
    }

    /**
     * Ein deutsch geschriebener Betrag in englischer Schreibweise: Punkt und
     * Komma tauschen die Plaetze. Die Ziffern bleiben, wie sie sind.
     */
    private fun betragEnglisch(deutsch: String): String = buildString(deutsch.length) {
        for (c in deutsch) {
            append(
                when (c) {
                    '.' -> ','
                    ',' -> '.'
                    else -> c
                },
            )
        }
    }

    /** Die Vorlagen: deutsch, wie in `netz/Senden.kt` zusammengesetzt. */
    private val MUSTER: List<Muster> = listOf(
        // --- Senden.pruefe
        Muster(
            muster("Der Betrag liegt unter der Staubgrenze von {betrag} YSR."),
        ) { g -> "The amount is below the dust limit of ${betragEnglisch(g[1])} YSR." },

        Muster(
            muster(
                "Die Notiz ist länger als {zahl} Byte. Umlaute zählen zwei, " +
                    "ein Emoji vier.",
            ),
        ) { g ->
            "The note is longer than ${g[1]} bytes. Accented letters count as two, " +
                "an emoji as four."
        },

        Muster(
            muster(
                "Das Guthaben reicht nicht. Betrag und Gebühr ergeben " +
                    "{betrag} YSR, es fehlen {betrag} YSR.",
            ),
        ) { g ->
            "The balance is not sufficient. Amount and fee add up to " +
                "${betragEnglisch(g[1])} YSR, ${betragEnglisch(g[2])} YSR are missing."
        },

        // --- Senden.unterschreibenUndSenden
        Muster(
            muster(
                "Die Überweisung ist größer als berechnet " +
                    "({zahl} statt {zahl} Byte). " +
                    "Die Gebühr wäre zu niedrig - es wird nichts gesendet.",
            ),
        ) { g ->
            "The transfer is larger than calculated " +
                "(${g[1]} instead of ${g[2]} bytes). " +
                "The fee would be too low - nothing is sent."
        },

        Muster(
            muster("Der Knoten antwortet nicht wie erwartet ({zahl})."),
        ) { g -> "The node is not responding as expected (${g[1]})." },
    )
}
