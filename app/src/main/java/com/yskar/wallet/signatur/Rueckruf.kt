package com.yskar.wallet.signatur

import com.yskar.wallet.netz.NetzFehler
import com.yskar.wallet.netz.Spiegel
import org.json.JSONObject

/**
 * Die Antwort auf eine Anmeldung per QR: genau EIN POST an die Plattform.
 *
 * Port von `src/netz/rueckruf.ts` der bestehenden Wallet.
 *
 * Nur https, nur erlaubte Hosts, keine Weiterleitungen, hoechstens 15
 * Sekunden. Gesendet wird die fertige Signatur - der Schluessel bleibt auf
 * dem Geraet. Diese Datei kann sonst nichts: kein Senden von YSR, kein Lesen
 * von Guthaben.
 *
 * Antwort der Plattform:
 *   Erfolg  HTTP 2xx  {"ok":true}
 *   Fehler  HTTP 4xx  {"ok":false,"error":"<Text>"}
 *
 * Der Transport ist derselbe wie beim Spiegel (`HttpTransport`: nur https,
 * folgt keiner Weiterleitung). Im Test ist er eine Attrappe - kein Test
 * spricht mit dem echten Netz (REGELN.md, Regel 18).
 */
object Rueckruf {

    const val ZEIT_MS = 15_000
    private const val TEXT_MAX = 200

    sealed interface Ergebnis {
        data object Ok : Ergebnis

        /** Nichts kam an oder nichts kam zurueck. */
        data object Netz : Ergebnis

        /** Die Plattform wollte umleiten. Dem folgt die Wallet nicht. */
        data class Weiterleitung(val status: Int) : Ergebnis

        /** Die Plattform hat abgelehnt; [text] ist, was sie dazu sagt - oder null. */
        data class Abgelehnt(val text: String?, val status: Int) : Ergebnis
    }

    private val STEUERZEICHEN = Regex("[\\u0000-\\u001f\\u007f-\\u009f]")
    private val LEERRAUM = Regex("\\s+")

    /**
     * Der Fehlertext der Plattform: nur reiner Text, ohne Steuerzeichen,
     * gekuerzt. Er kommt aus dem Netz und wird angezeigt - mehr als ein Satz
     * darf er nicht werden.
     */
    private fun textAus(daten: JSONObject?): String? {
        val e = daten?.opt("error") as? String ?: return null
        val sauber = e.replace(STEUERZEICHEN, " ").replace(LEERRAUM, " ").trim()
        if (sauber.isEmpty()) return null
        val punkte = sauber.codePoints().toArray()
        return if (punkte.size > TEXT_MAX) String(punkte, 0, TEXT_MAX - 1) + "…" else sauber
    }

    /** Was die Antwort der Plattform bedeutet. Reine Funktion. */
    fun antwortLesen(status: Int, rumpf: String): Ergebnis {
        if (status in 300..399) return Ergebnis.Weiterleitung(status)
        val daten = runCatching { JSONObject(rumpf) }.getOrNull()
        if (status in 200..299 && daten?.opt("ok") == true) return Ergebnis.Ok
        return Ergebnis.Abgelehnt(textAus(daten), status)
    }

    /**
     * Die Signatur an den Rueckruf senden - nur an https und an den Host, fuer
     * den die Nachricht gilt.
     *
     * GEPRUEFT WIRD HIER NOCH EINMAL, unabhaengig davon, was der Aufrufer
     * schon geprueft hat: Die Adresse bestimmt, wohin eine Signatur geht.
     * [domain] ist die Domain aus der signierten Nachricht.
     */
    fun senden(url: String, domain: String, s: Nachricht.Signiert, transport: Spiegel.Transport): Ergebnis {
        if (Nachricht.rueckrufErlaubt(url, domain) != null) return Ergebnis.Abgelehnt(null, 0)
        return try {
            val a = transport.anfrage("POST", url, Nachricht.alsJson(s))
            antwortLesen(a.status, a.text)
        } catch (e: NetzFehler) {
            Ergebnis.Netz
        } catch (e: Exception) {
            Ergebnis.Netz
        }
    }
}
