package com.yskar.wallet.otc

/**
 * Ein strenger Leser fuer das `d` eines OTC-Auftrags (REGELN.md, Regel 43).
 *
 * WARUM NICHT org.json: Die App bekommt das org.json von Android, die
 * Unittests das aus Maven - und die beiden lesen verschieden. Android nimmt
 * doppelte Felder (der letzte Wert gilt) und liest `0x48` hexadezimal und
 * `0110` oktal. Ein Auftrag, den jeder korrekte JSON-Leser ablehnt, kam so
 * durch (AuftragJsonTest). Dieser Leser ist reines Kotlin und rechnet auf
 * der JVM und auf dem Geraet dasselbe.
 *
 * Er kennt nur, was ein Auftrag braucht:
 *
 *   genau EIN flaches Objekt   ws '{' ws [paar (ws ',' ws paar)*] ws '}' ws
 *   Schluessel                 JSON-String, jeder hoechstens einmal
 *   Werte                      JSON-String oder ganze Zahl
 *   String                     Escapes \" \\ \/ \b \f \n \r \t \uXXXX,
 *                              keine rohen Zeichen unter 0x20
 *   Zahl                       0 | [1-9][0-9]*, passt in Long
 *
 * Alles andere - true, false, null, Array, Objekt, Vorzeichen, Punkt,
 * Exponent, 0x, fuehrende Null, Text hinter der Klammer - ist ein Fehler.
 * Hoechstens [MAX_ZEICHEN] Zeichen. Ergebnis: Werte als String oder Long,
 * in der Reihenfolge des Textes; null bei jedem Fehler.
 */
object FlachJson {

    const val MAX_ZEICHEN = 2000

    fun lesen(text: String): Map<String, Any>? {
        if (text.length > MAX_ZEICHEN) return null
        return try {
            Leser(text).objekt()
        } catch (e: Fehler) {
            null
        }
    }

    private class Fehler : Exception()

    private class Leser(private val t: String) {
        private var i = 0

        private fun fehler(): Nothing = throw Fehler()

        private fun leerraum() {
            while (i < t.length && (t[i] == ' ' || t[i] == '\t' || t[i] == '\n' || t[i] == '\r')) i++
        }

        private fun erwarte(c: Char) {
            if (i >= t.length || t[i] != c) fehler()
            i++
        }

        fun objekt(): Map<String, Any> {
            val ergebnis = LinkedHashMap<String, Any>()
            leerraum()
            erwarte('{')
            leerraum()
            if (i < t.length && t[i] == '}') {
                i++
            } else {
                while (true) {
                    leerraum()
                    val schluessel = zeichenkette()
                    leerraum()
                    erwarte(':')
                    leerraum()
                    val wert = wert()
                    if (ergebnis.containsKey(schluessel)) fehler()
                    ergebnis[schluessel] = wert
                    leerraum()
                    if (i >= t.length) fehler()
                    when (t[i]) {
                        ',' -> i++
                        '}' -> { i++; break }
                        else -> fehler()
                    }
                }
            }
            leerraum()
            if (i != t.length) fehler()
            return ergebnis
        }

        private fun wert(): Any {
            if (i >= t.length) fehler()
            return when (t[i]) {
                '"' -> zeichenkette()
                in '0'..'9' -> zahl()
                else -> fehler()
            }
        }

        /** 0 | [1-9][0-9]* - was danach steht, prueft der Aufrufer (`,` `}` oder Leerraum). */
        private fun zahl(): Long {
            val anfang = i
            if (t[i] == '0') {
                i++
            } else {
                while (i < t.length && t[i] in '0'..'9') i++
            }
            return t.substring(anfang, i).toLongOrNull() ?: fehler()
        }

        private fun zeichenkette(): String {
            erwarte('"')
            val sb = StringBuilder()
            while (true) {
                if (i >= t.length) fehler()
                val c = t[i++]
                when {
                    c == '"' -> return sb.toString()
                    c < ' ' -> fehler()
                    c == '\\' -> {
                        if (i >= t.length) fehler()
                        when (t[i++]) {
                            '"' -> sb.append('"')
                            '\\' -> sb.append('\\')
                            '/' -> sb.append('/')
                            'b' -> sb.append('\b')
                            'f' -> sb.append('\u000C')
                            'n' -> sb.append('\n')
                            'r' -> sb.append('\r')
                            't' -> sb.append('\t')
                            'u' -> {
                                if (i + 4 > t.length) fehler()
                                var code = 0
                                repeat(4) {
                                    // Nur ASCII: Character.digit naehme auch Ziffern anderer Schriften.
                                    val z = t[i++]
                                    val ziffer = when (z) {
                                        in '0'..'9' -> z - '0'
                                        in 'a'..'f' -> z - 'a' + 10
                                        in 'A'..'F' -> z - 'A' + 10
                                        else -> fehler()
                                    }
                                    code = code * 16 + ziffer
                                }
                                sb.append(code.toChar())
                            }
                            else -> fehler()
                        }
                    }
                    else -> sb.append(c)
                }
            }
        }
    }
}
