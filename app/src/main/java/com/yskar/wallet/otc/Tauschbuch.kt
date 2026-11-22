package com.yskar.wallet.otc

import com.yskar.wallet.kern.Hex
import com.yskar.wallet.signatur.Nachricht
import org.json.JSONArray
import org.json.JSONObject

/**
 * Die offenen Tausche dieser Wallet -- nur auf diesem Geraet (Plan C,
 * freigegeben 22.11.2026).
 *
 * Wozu: Einloesen und Zurueckholen sollen auch gehen, wenn otc.yskar.app
 * nicht erreichbar ist. Dafuer merkt sich die Wallet die Teile einer Sperre,
 * sobald sie sie einmal geprueft hat, und baut daraus spaeter selbst den
 * Auftrag -- der dann wieder ganz durch [Auftrag.lesen] geht. Was hier
 * liegt, ist also nie ein Beweis, nur eine Abkuerzung zum selben Weg.
 *
 * Kein Geheimnis liegt hier: x wird jedes Mal neu aus den zwoelf Woertern
 * abgeleitet. Was hier steht, steht ohnehin in der Kette (Schluessel, Hash,
 * Fristen) oder auf otc.yskar.app (Betraege).
 *
 * Reine Funktionen, keine Android-API (`TauschbuchTest`).
 */
object Tauschbuch {

    /** Hoechstens so viele Eintraege; die aeltesten abgeschlossenen gehen zuerst. */
    const val MAX = 50

    enum class Rolle(val wort: String) {
        KAEUFER("kaeufer"), VERKAEUFER("verkaeufer");

        companion object {
            fun aus(s: String?) = entries.firstOrNull { it.wort == s }
        }
    }

    /** Was die Wallet zuletzt selbst getan hat. Was die Kette sagt, fragt sie neu. */
    enum class Stand(val wort: String) {
        GEMERKT("gemerkt"),         // hash gesendet bzw. Sperre geprueft
        GESPERRT("gesperrt"),       // Verkaeufer: Sperre eingereicht
        EINGELOEST("eingeloest"),   // Kaeufer: Einloesen eingereicht
        ZURUECK("zurueck");         // Verkaeufer: Zurueckholen eingereicht

        val abgeschlossen: Boolean get() = this == EINGELOEST || this == ZURUECK

        companion object {
            fun aus(s: String?) = entries.firstOrNull { it.wort == s }
        }
    }

    class Eintrag(
        val kennung: String,
        val rolle: Rolle,
        val ysr: Long,
        val gegen: String?,
        val gegenMenge: String?,
        /** Die Felder einer Sperre wie in `d` eines einloesen-Auftrags, ohne n; null, solange unbekannt. */
        val sperre: JSONObject?,
        val n: Long?,
        val stand: Stand,
        val txid: String?,
        /** Unixsekunden dieses Geraets. */
        val seit: Long,
    ) {
        val skriptAdresse: String? get() = sperre?.optString("skriptAdresse")?.ifEmpty { null }
        val frist: Long? get() = sperre?.optLong("frist")
        val schluss: Long? get() = sperre?.optLong("schluss")

        fun mit(
            sperre: JSONObject? = this.sperre,
            n: Long? = this.n,
            stand: Stand = this.stand,
            txid: String? = this.txid,
            gegen: String? = this.gegen,
            gegenMenge: String? = this.gegenMenge,
        ) = Eintrag(kennung, rolle, ysr, gegen, gegenMenge, sperre, n, stand, txid, seit)
    }

    // ------------------------------------------------------------ Merken

    fun sperrFelder(s: Auftrag.Sperre): JSONObject = JSONObject().apply {
        put("kennung", s.kennungHex)
        put("kaeufer", Hex.toHex(s.teile.kaeufer))
        put("verkaeufer", Hex.toHex(s.teile.verkaeufer))
        put("hash", Hex.toHex(s.teile.hash))
        put("frist", s.teile.frist)
        put("schluss", s.teile.einloeseSchluss)
        put("ysr", s.ysr.toString())
        put("skriptAdresse", s.skriptAdresse)
    }

    private fun ersetze(buch: List<Eintrag>, neu: Eintrag): List<Eintrag> =
        kuerzen(listOf(neu) + buch.filterNot { it.kennung == neu.kennung && it.rolle == neu.rolle })

    private fun finde(buch: List<Eintrag>, kennung: String, rolle: Rolle) =
        buch.firstOrNull { it.kennung == kennung && it.rolle == rolle }

    /** Kaeufer nach dem hash-Auftrag: Kennung, Betrag, Gegenwaehrung. */
    fun merkeHash(buch: List<Eintrag>, a: Auftrag.Gelesen.Hash, jetzt: Long): List<Eintrag> {
        val k = Hex.toHex(a.kennung)
        val alt = finde(buch, k, Rolle.KAEUFER)
        // Schon bekannt: Betrag bleibt der zuerst gemerkte. Ein zweiter hash-
        // Auftrag mit anderem Betrag aendert ihn nicht -- das Einloesen
        // vergleicht gegen den ersten.
        if (alt != null) return buch
        return ersetze(buch, Eintrag(k, Rolle.KAEUFER, a.ysr, a.gegen, a.gegenMenge, null, null, Stand.GEMERKT, null, jetzt))
    }

    /**
     * Eine gepruefte Sperre merken. [rolle]: wer man in ihr ist. Ein schon
     * vorhandener Eintrag behaelt Betrag, Gegenwaehrung und Stand.
     */
    fun merkeSperre(
        buch: List<Eintrag>,
        s: Auftrag.Sperre,
        rolle: Rolle,
        n: Long?,
        jetzt: Long,
        gegen: String? = null,
        gegenMenge: String? = null,
    ): List<Eintrag> {
        val alt = finde(buch, s.kennungHex, rolle)
        val felder = sperrFelder(s)
        val neu = alt?.mit(
            sperre = felder,
            n = n ?: alt.n,
            gegen = alt.gegen ?: gegen,
            gegenMenge = alt.gegenMenge ?: gegenMenge,
        ) ?: Eintrag(s.kennungHex, rolle, s.ysr, gegen, gegenMenge, felder, n, Stand.GEMERKT, null, jetzt)
        return ersetze(buch, neu)
    }

    fun setzeStand(buch: List<Eintrag>, kennung: String, rolle: Rolle, stand: Stand, txid: String?): List<Eintrag> {
        val alt = finde(buch, kennung, rolle) ?: return buch
        return ersetze(buch, alt.mit(stand = stand, txid = txid ?: alt.txid))
    }

    /** Der beim hash-Auftrag gemerkte Betrag -- fuer den Vergleich beim Einloesen. */
    fun erwartetYsr(buch: List<Eintrag>, kennung: ByteArray): Long? =
        finde(buch, Hex.toHex(kennung), Rolle.KAEUFER)?.ysr

    fun offen(buch: List<Eintrag>): List<Eintrag> = buch.filter { !it.stand.abgeschlossen }.sortedByDescending { it.seit }

    private fun kuerzen(buch: List<Eintrag>): List<Eintrag> {
        if (buch.size <= MAX) return buch
        val weg = buch.filter { it.stand.abgeschlossen }.sortedBy { it.seit }.take(buch.size - MAX).toSet()
        return buch.filterNot { it in weg }.take(MAX)
    }

    // ------------------------------------------------------------ Auftrag

    /**
     * Den Auftrag fuer diesen Eintrag bauen -- als Text, der danach ganz
     * normal durch [Auftrag.lesen] geht. null, wenn die Teile fehlen.
     */
    fun auftrag(e: Eintrag): String? {
        val felder = e.sperre ?: return null
        val d = JSONObject(felder.toString())
        val art = when (e.rolle) {
            Rolle.KAEUFER -> {
                d.put("n", e.n ?: return null)
                "einloesen"
            }
            Rolle.VERKAEUFER -> "zurueck"
        }
        return "yskar:otc?v=1&auftrag=$art&d=${Nachricht.textBase64url(d.toString())}"
    }

    // ------------------------------------------------------------ Speichern

    private val HEX64 = Regex("^[0-9a-f]{64}$")

    fun alsText(buch: List<Eintrag>): String = JSONArray().apply {
        for (e in buch) {
            put(JSONObject().apply {
                put("kennung", e.kennung)
                put("rolle", e.rolle.wort)
                put("ysr", e.ysr.toString())
                e.gegen?.let { put("gegen", it) }
                e.gegenMenge?.let { put("gegenMenge", it) }
                e.sperre?.let { put("sperre", it) }
                e.n?.let { put("n", it) }
                put("stand", e.stand.wort)
                e.txid?.let { put("txid", it) }
                put("seit", e.seit)
            })
        }
    }.toString()

    /** Lesen; eine kaputte Zeile faellt weg, statt falsch gelesen zu werden. */
    fun ausText(text: String): List<Eintrag> {
        val a = runCatching { JSONArray(text) }.getOrNull() ?: return emptyList()
        val aus = ArrayList<Eintrag>()
        for (i in 0 until a.length()) {
            val o = a.optJSONObject(i) ?: continue
            val e = runCatching {
                val kennung = o.getString("kennung").takeIf { HEX64.matches(it) } ?: return@runCatching null
                val rolle = Rolle.aus(o.optString("rolle")) ?: return@runCatching null
                val stand = Stand.aus(o.optString("stand")) ?: return@runCatching null
                val ysr = o.getString("ysr").toLongOrNull()?.takeIf { it > 0 } ?: return@runCatching null
                Eintrag(
                    kennung = kennung,
                    rolle = rolle,
                    ysr = ysr,
                    gegen = o.optString("gegen").ifEmpty { null },
                    gegenMenge = o.optString("gegenMenge").ifEmpty { null },
                    sperre = o.optJSONObject("sperre"),
                    n = if (o.has("n")) o.getLong("n") else null,
                    stand = stand,
                    txid = o.optString("txid").ifEmpty { null },
                    seit = o.getLong("seit"),
                )
            }.getOrNull() ?: continue
            if (aus.none { it.kennung == e.kennung && it.rolle == e.rolle }) aus.add(e)
        }
        return aus
    }
}
