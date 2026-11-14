package com.yskar.wallet.netz

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * Verbindung zum Netz.
 *
 * LESEN geht an den Spiegel: Guthaben, Verlauf, Hoehe, Gebuehrenlage.
 * SENDEN geht an den Full Node, weil nur er Ueberweisungen in Bloecke
 * bringt. Beides wie in der bestehenden Wallet (src/netz/api.ts).
 *
 * Die Wallet baut und signiert jede Ueberweisung auf dem Geraet. Was hier
 * hinausgeht, sind fertige Bytes: Kein Server kann Betrag oder Empfaenger
 * aendern, ohne die Signatur zu brechen.
 *
 * WO DER KNOTEN STEHT, SAGT DER SPIEGEL. Seine stillgelegte Route
 * POST /api/v2/tx antwortet mit HTTP 410 und nennt die Adresse im Feld
 * `fullnode`. So braucht ein Umzug des Knotens keine neue APK. Geprueft am
 * 10.11.2026: liefert "https://pool.yskar.app".
 *
 * WAS DIE ECHTE API ANDERS MACHT ALS DIE TYPESCRIPT-TYPEN. Nachgemessen an
 * den Antworten vom 10.11.2026 - wer sich auf die Typen verlaesst, baut
 * einen Parser, der an echten Daten scheitert:
 *
 *   `balance`      Zahl, nicht Zeichenkette (82345092565)
 *   `nonce`        Zahl - aber `nextNonce` daneben eine Zeichenkette
 *   `timestamp`    Unixsekunden als Zeichenkette ("1794261818"),
 *                  nicht ISO-8601
 *   `memo`         HEX der Rohbytes ("59534b4152204d41494e" = "YSKAR MAIN"),
 *                  nicht lesbarer Text
 *   `amount`,`fee` Zeichenketten
 *
 * Darum liest [zahl] beide Formen und [Notiz.ausHex] die Notiz. Die App
 * sieht danach nur noch eine Form.
 */
class Spiegel(
    private val transport: Transport,
    private val spiegelAdresse: String = STANDARD_SPIEGEL,
    /** Nur fuer den Probelauf beim Entwickeln. Sonst fragt die App den Spiegel. */
    private val knotenFest: String? = null,
) {

    companion object {
        const val STANDARD_SPIEGEL = "https://yskar.vercel.app"

        /** Gilt nur, wenn der Spiegel die Adresse des Knotens nicht nennt. */
        const val KNOTEN_VORGABE = "https://pool.yskar.app"

        const val EXPLORER = "https://www.yskar.app/explorer"

        private val KNOTEN_MUSTER = Regex("^https://[a-z0-9.-]+(:\\d{1,5})?/?$", RegexOption.IGNORE_CASE)
    }

    /** Was die App zum Reden braucht - austauschbar, damit der Parser testbar bleibt. */
    interface Transport {
        /** @return Status und Rumpf. Wirft NetzFehler(OFFLINE), wenn nichts ankommt. */
        fun anfrage(methode: String, url: String, rumpf: String? = null): Antwort
    }

    data class Antwort(val status: Int, val text: String)

    private var knotenGemerkt: String? = null

    // ----------------------------------------------------------------- Lesen

    private fun lesen(pfad: String): JSONObject {
        val a = transport.anfrage("GET", spiegelAdresse.trimEnd('/') + pfad)
        if (a.status !in 200..299) {
            throw NetzFehler(NetzFehler.Ursache.ANTWORT, a.status)
        }
        return try {
            JSONObject(a.text)
        } catch (e: JSONException) {
            throw NetzFehler(NetzFehler.Ursache.ANTWORT, a.status, "Antwort ist kein JSON-Objekt")
        }
    }

    fun kopf(): Kopf = kopfAus(lesen("/api/v2/summary"))

    fun markt(): Markt = marktAus(lesen("/api/v2/fees"))

    fun konto(adresse: String): Konto = kontoAus(lesen("/api/v2/account/$adresse"))

    /**
     * Eine Seite des Verlaufs.
     *
     * [richtung]: null = alles, "ein" = eingehend (Belohnung, Pool, Eingang),
     * "aus" = ausgehend. Geprueft am 10.11.2026: beide Filter wirken.
     * [vor]: der Zeiger aus der vorigen Seite.
     */
    fun verlauf(
        adresse: String,
        richtung: String? = null,
        vor: String? = null,
        suche: String? = null,
        limit: Int = 40,
    ): VerlaufSeite {
        val p = StringBuilder("/api/v2/account/$adresse/verlauf?limit=$limit")
        if (richtung != null && richtung != "alle") p.append("&richtung=").append(richtung)
        if (!vor.isNullOrEmpty()) p.append("&vor=").append(vor)
        if (!suche.isNullOrEmpty()) p.append("&q=").append(kodiere(suche))
        return verlaufSeiteAus(lesen(p.toString()))
    }

    private fun kodiere(s: String): String =
        java.net.URLEncoder.encode(s, "UTF-8")

    // ---------------------------------------------------------------- Senden

    /**
     * Die Adresse des Full Node.
     *
     * Einmal ermittelt, dann gemerkt. Antwortet der Spiegel nicht oder
     * nennt er eine Adresse, die nicht wie eine Adresse aussieht, gilt die
     * Vorgabe - lieber ein bekannter Knoten als gar keiner.
     */
    fun knotenAdresse(): String {
        knotenFest?.let { return it.trimEnd('/') }
        knotenGemerkt?.let { return it }

        val gefunden = try {
            val a = transport.anfrage("POST", spiegelAdresse.trimEnd('/') + "/api/v2/tx", "{}")
            val f = runCatching { JSONObject(a.text).optString("fullnode", "") }.getOrDefault("")
            if (f.isNotEmpty() && KNOTEN_MUSTER.matches(f)) f.trimEnd('/') else null
        } catch (e: NetzFehler) {
            null
        }

        val knoten = gefunden ?: KNOTEN_VORGABE
        if (gefunden != null) knotenGemerkt = knoten
        return knoten
    }

    /** Fertig signierte Ueberweisung (Hex) an den Full Node geben. */
    fun senden(rohHex: String): SendeErgebnis {
        val a = transport.anfrage(
            "POST",
            knotenAdresse() + "/api/v2/tx",
            JSONObject().put("raw", rohHex).toString(),
        )
        val o = try {
            JSONObject(a.text)
        } catch (e: JSONException) {
            throw NetzFehler(NetzFehler.Ursache.ANTWORT, a.status, "Antwort ist kein JSON-Objekt")
        }

        if (o.optBoolean("accepted", false)) {
            val txid = o.optString("txid", "").removePrefix("\\x").removePrefix("0x")
            return SendeErgebnis.Angenommen(txid.ifEmpty { null })
        }
        return SendeErgebnis.Abgelehnt(
            grund = o.optString("reason", "").ifEmpty { "http_${a.status}" },
            detail = o.optString("detail", "").ifEmpty { null },
        )
    }

    // ------------------------------------------------------------- Umwandeln

    /**
     * Zahl aus einem Feld, das als Zahl ODER als Zeichenkette kommen kann.
     *
     * Genau hier scheitert ein Parser, der den TypeScript-Typen glaubt:
     * `balance` ist eine Zahl, `nextNonce` daneben eine Zeichenkette.
     */
    private fun zahl(o: JSONObject, schluessel: String, standard: Long = 0L): Long {
        if (!o.has(schluessel) || o.isNull(schluessel)) return standard
        return when (val v = o.get(schluessel)) {
            is Number -> v.toLong()
            is String -> v.trim().toLongOrNull() ?: standard
            else -> standard
        }
    }

    private fun zahlOderNull(o: JSONObject, schluessel: String): Long? {
        if (!o.has(schluessel) || o.isNull(schluessel)) return null
        return when (val v = o.get(schluessel)) {
            is Number -> v.toLong()
            is String -> v.trim().toLongOrNull()
            else -> null
        }
    }

    private fun textOderNull(o: JSONObject, schluessel: String): String? {
        if (!o.has(schluessel) || o.isNull(schluessel)) return null
        val s = o.optString(schluessel, "")
        return s.ifEmpty { null }
    }

    private fun ganzOderNull(o: JSONObject, schluessel: String): Int? =
        zahlOderNull(o, schluessel)?.toInt()

    internal fun kopfAus(o: JSONObject) = Kopf(
        hoehe = zahlOderNull(o, "height"),
        mempool = zahl(o, "mempool").toInt(),
    )

    internal fun marktAus(o: JSONObject): Markt {
        val stufenObj = o.optJSONObject("stufen")
        val stufen = HashMap<Stufe, StufenPreis>()
        for (s in Stufe.entries) {
            val e = stufenObj?.optJSONObject(s.schluessel) ?: continue
            stufen[s] = StufenPreis(
                gebuehr = zahl(e, "fee"),
                block = zahl(e, "block").toInt(),
                jeByte = zahlOderNull(e, "jeByte"),
            )
        }
        return Markt(
            mindestGebuehr = zahl(o, "minFee"),
            mindestJeByte = zahlOderNull(o, "mindestJeByte"),
            wartend = zahl(o, "wartend").toInt(),
            andrang = o.optBoolean("andrang", false),
            stufen = stufen,
            hoehe = zahlOderNull(o, "hoehe"),
            fassung = ganzOderNull(o, "fassung"),
            basisBytes = ganzOderNull(o, "basisBytes"),
            sendepause = o.optBoolean("sendepause", false),
        )
    }

    internal fun eintragAus(o: JSONObject) = VerlaufEintrag(
        txid = o.optString("txid", ""),
        height = zahl(o, "height"),
        idx = zahl(o, "idx").toInt(),
        zeit = zahlOderNull(o, "timestamp"),
        art = Art.aus(textOderNull(o, "kind")),
        gegenseite = textOderNull(o, "counterparty"),
        anteile = ganzOderNull(o, "shares"),
        betrag = zahl(o, "amount"),
        gebuehr = zahl(o, "fee"),
        notiz = Notiz.ausHex(textOderNull(o, "memo")),
    )

    internal fun wartendAus(o: JSONObject) = Wartend(
        txid = o.optString("txid", ""),
        art = Art.aus(textOderNull(o, "kind")),
        von = o.optString("from", ""),
        an = textOderNull(o, "to"),
        betrag = zahl(o, "amount"),
        gebuehr = zahl(o, "fee"),
        nonce = zahl(o, "nonce"),
        notiz = Notiz.ausHex(textOderNull(o, "memo")),
    )

    internal fun kontoAus(o: JSONObject): Konto {
        val wartend = liste(o.optJSONArray("pending")) { wartendAus(it) }
        val verlauf = liste(o.optJSONArray("history")) { eintragAus(it) }
        return Konto(
            adresse = o.optString("address", ""),
            guthaben = zahl(o, "balance"),
            nonce = zahl(o, "nonce"),
            naechsteNonce = zahl(o, "nextNonce"),
            wartend = wartend,
            wartendQuelle = o.optString("pendingSource", "unavailable"),
            verlauf = verlauf,
            verlaufWeiter = textOderNull(o, "historyWeiter"),
        )
    }

    internal fun verlaufSeiteAus(o: JSONObject) = VerlaufSeite(
        eintraege = liste(o.optJSONArray("eintraege")) { eintragAus(it) },
        weiter = textOderNull(o, "weiter"),
    )

    private fun <T> liste(arr: JSONArray?, baue: (JSONObject) -> T): List<T> {
        if (arr == null) return emptyList()
        val out = ArrayList<T>(arr.length())
        for (i in 0 until arr.length()) {
            arr.optJSONObject(i)?.let { out.add(baue(it)) }
        }
        return out
    }
}
