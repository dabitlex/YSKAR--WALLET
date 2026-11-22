package com.yskar.wallet.otc

import com.yskar.wallet.kern.Address
import com.yskar.wallet.kern.Hex
import com.yskar.wallet.kern.Otc
import com.yskar.wallet.kern.Params
import com.yskar.wallet.kern.SkriptAusgabe
import com.yskar.wallet.kern.Wallet
import com.yskar.wallet.signatur.Nachricht
import org.json.JSONObject
import java.net.URLDecoder
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle

/**
 * Auftraege von otc.yskar.app an die Wallet (freigegeben 22.11.2026, Plan A):
 *
 *   yskar:otc?v=1&auftrag=hash|sperren|einloesen|zurueck&d=<JSON base64url>[&cb=<Rueckruf base64url>]
 *
 * Rueckruf nur bei `hash`; alles andere sieht der Waechter in der Kette.
 *
 * EIN AUFTRAG IST KEIN BEWEIS (Regel 22). Die Wallet uebernimmt aus ihm nur
 * Teile, die sie selbst nachpruefen kann: Sie baut das Sperrskript neu und
 * vergleicht die Adresse, sie findet ihren eigenen Schluessel im richtigen
 * Zweig, sie leitet das Geheimnis selbst ab. Was der Knoten weiss
 * (Hoehe, Guthaben, Bestaetigungen), fragt die Wallet beim Knoten -- diese
 * Zahlen kommen hier als Argumente herein, damit alles ohne Netz testbar
 * bleibt (Regel 18). Kein Android, keine Netzaufrufe: reine Funktionen.
 *
 * Die Felder von `d` je Auftrag (alle Pflicht, keine weiteren):
 *
 *   hash:      kennung, adresse, ysr, gegen, gegenMenge, ablauf
 *   sperren:   kennung, kaeufer, verkaeufer, hash, frist, schluss, n, ysr,
 *              skriptAdresse, gegen, gegenMenge, gegenSperre
 *   einloesen: kennung, kaeufer, verkaeufer, hash, frist, schluss, n, ysr, skriptAdresse
 *   zurueck:   kennung, kaeufer, verkaeufer, hash, frist, schluss, ysr, skriptAdresse
 *
 *   kennung, kaeufer, verkaeufer, hash: 64 Hex-Zeichen, klein
 *   frist, schluss, n: ganze Zahlen; ysr: kleinste Einheiten als Text
 *   gegen: USDC | BTC | LTC; gegenMenge: Dezimaltext, nur zum Anzeigen
 *   gegenSperre: Kennung der Gegen-Sperre, nur zum Anzeigen
 *   ablauf: 2026-11-22T12:00:00Z
 */
object Auftrag {

    /** Nur diese Seite bekommt Antworten. */
    const val DOMAIN = "otc.yskar.app"

    /** Bestaetigungen, die eine Sperre mindestens braucht, und hoechstens. */
    const val N_MIN = 6L
    const val N_MAX = 144L

    /** Hoechstens so viele Bloecke bindet der Verkaeufer seine YSR (~50 h). */
    const val SPERRE_MAX_BLOECKE = 300L

    /**
     * Abstand zum Einloese-Schluss: Das Einloesen muss mindestens so viele
     * Bloecke vorher eingereicht werden. Sonst koennte das Geheimnis im
     * Wartebereich stehen, waehrend die Transaktion verfaellt.
     */
    const val EINLOESE_RAND = 6L

    /** Ein hash-Auftrag gilt hoechstens so lange. */
    const val GUELTIG_MAX_MS = 15 * 60 * 1000L

    val GEGEN = listOf("USDC", "BTC", "LTC")

    /** Feste Groesse der zwei Witness-Formen (Otc.witnessEinloesen / witnessZurueck). */
    const val WITNESS_EINLOESEN = 101
    const val WITNESS_ZURUECK = 69

    enum class Art(val wort: String) {
        HASH("hash"), SPERREN("sperren"), EINLOESEN("einloesen"), ZURUECK("zurueck");

        companion object {
            fun aus(text: String): Art? = entries.firstOrNull { it.wort == text }
        }
    }

    enum class Fehler {
        FORMAT,          // kein lesbarer yskar:otc-Code
        VERSION,         // v ist nicht 1
        ART,             // unbekannter Auftrag
        DATEN,           // d fehlt, ist kein JSON oder Felder fehlen/zu viel/falsche Form
        RUECKRUF,        // Rueckruf fehlt, wo er Pflicht ist, steht da, wo er nicht hingehoert, oder ist kein https
        RUECKRUF_HOST,   // Rueckruf geht nicht an otc.yskar.app
        ADRESSE_FREMD,   // hash: Adresse ist nicht die dieser Wallet
        NICHT_BETEILIGT, // eigener Schluessel steht nicht im passenden Zweig
        SKRIPT,          // nachgebautes Skript ergibt eine andere Adresse
        FRISTEN,         // Schluss nicht genau Frist - 12, oder Hoehen ausserhalb u32
        ABGELAUFEN,      // hash: Ablauf vorbei
        ZU_LANGE,        // hash: Ablauf mehr als 15 min voraus
        BETRAG,          // ysr fehlt/0, oder anders als beim hash-Auftrag gemerkt
        GEHEIMNIS,       // einloesen: SHA-256(eigenes x) ist nicht der Hash im Skript
        ZU_KNAPP,        // sperren: Kaeufer haette keine Zeit mehr zum Einloesen
        ZU_LANG,         // sperren: YSR waeren zu lange gebunden
        UNBESTAETIGT,    // einloesen: Sperre hat noch nicht genug Bestaetigungen
        GUTHABEN,        // Skriptkonto haelt zu wenig
        WARTEND,         // Skriptkonto hat noch etwas im Wartebereich
        ZU_SPAET,        // einloesen: Schluss zu nah oder vorbei
        ZU_FRUEH,        // zurueck: Frist noch nicht erreicht
    }

    /** Die Teile einer Sperre, wie sie im Auftrag stehen -- bereits nachgebaut und verglichen. */
    class Sperre(
        val kennung: ByteArray,
        val teile: Otc.Teile,
        val ysr: Long,
        val skriptAdresse: String,
    ) {
        val skript: ByteArray get() = Otc.skript(teile)
        val kennungHex: String get() = Hex.toHex(kennung)
    }

    sealed interface Gelesen {
        val art: Art

        class Hash(
            val kennung: ByteArray,
            val adresse: String,
            val ysr: Long,
            val gegen: String,
            val gegenMenge: String,
            val ablauf: Instant,
            val ablaufText: String,
            val rueckruf: String,
        ) : Gelesen {
            override val art = Art.HASH
        }

        class Sperren(
            val sperre: Sperre,
            val n: Long,
            val gegen: String,
            val gegenMenge: String,
            val gegenSperre: String,
        ) : Gelesen {
            override val art = Art.SPERREN
        }

        class Einloesen(val sperre: Sperre, val n: Long) : Gelesen {
            override val art = Art.EINLOESEN
        }

        class Zurueck(val sperre: Sperre) : Gelesen {
            override val art = Art.ZURUECK
        }
    }

    sealed interface Lesung {
        class Ok(val auftrag: Gelesen) : Lesung
        data class Abgelehnt(val fehler: Fehler) : Lesung
    }

    // ------------------------------------------------------------- Lesen

    private val CODE = Regex("^yskar:(//)?otc\\?", RegexOption.IGNORE_CASE)

    /** Schnelle Erkennung, ohne zu pruefen. */
    fun istOtcCode(text: String): Boolean = CODE.containsMatchIn(text.trim())

    private class Abbruch(val fehler: Fehler) : Exception()

    private fun pruefe(bedingung: Boolean, fehler: Fehler) {
        if (!bedingung) throw Abbruch(fehler)
    }

    private val HEX64 = Regex("^[0-9a-f]{64}$")
    private val YSR = Regex("^[1-9][0-9]{0,18}$")
    private val MENGE = Regex("^[0-9]{1,15}(\\.[0-9]{1,18})?$")
    private val GEGEN_SPERRE = Regex("^[0-9A-Za-z:_-]{1,100}$")
    private val ZEIT = Regex("^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}Z$")
    private val ZEITFORM = DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss'Z'")
        .withResolverStyle(ResolverStyle.STRICT)

    private val FELDER = mapOf(
        Art.HASH to setOf("kennung", "adresse", "ysr", "gegen", "gegenMenge", "ablauf"),
        Art.SPERREN to setOf(
            "kennung", "kaeufer", "verkaeufer", "hash", "frist", "schluss", "n", "ysr",
            "skriptAdresse", "gegen", "gegenMenge", "gegenSperre",
        ),
        Art.EINLOESEN to setOf(
            "kennung", "kaeufer", "verkaeufer", "hash", "frist", "schluss", "n", "ysr", "skriptAdresse",
        ),
        Art.ZURUECK to setOf(
            "kennung", "kaeufer", "verkaeufer", "hash", "frist", "schluss", "ysr", "skriptAdresse",
        ),
    )

    private fun text(o: JSONObject, k: String): String = o.opt(k) as? String ?: throw Abbruch(Fehler.DATEN)

    private fun hex32(o: JSONObject, k: String): ByteArray {
        val s = text(o, k)
        pruefe(HEX64.matches(s), Fehler.DATEN)
        return Hex.fromHex(s)
    }

    /** Ganze Zahl als JSON-Zahl, ohne Nachkommastellen, 0..u32. */
    private fun hoehe(o: JSONObject, k: String): Long {
        val v = o.opt(k)
        pruefe(v is Int || v is Long, Fehler.DATEN)
        val n = (v as Number).toLong()
        pruefe(n in 0..0xffff_ffffL, Fehler.FRISTEN)
        return n
    }

    private fun ysr(o: JSONObject): Long {
        val s = text(o, "ysr")
        pruefe(YSR.matches(s), Fehler.BETRAG)
        return s.toLongOrNull()?.takeIf { it > 0 } ?: throw Abbruch(Fehler.BETRAG)
    }

    private fun gegen(o: JSONObject): Pair<String, String> {
        val g = text(o, "gegen")
        val m = text(o, "gegenMenge")
        pruefe(g in GEGEN && MENGE.matches(m), Fehler.DATEN)
        return g to m
    }

    /** Sperre nachbauen und Adresse vergleichen. Prueft NICHT, wer man selbst ist. */
    private fun sperre(o: JSONObject): Sperre {
        val kennung = hex32(o, "kennung")
        val kaeufer = hex32(o, "kaeufer")
        val verkaeufer = hex32(o, "verkaeufer")
        val hash = hex32(o, "hash")
        val frist = hoehe(o, "frist")
        val schluss = hoehe(o, "schluss")
        val adresse = text(o, "skriptAdresse")
        pruefe(!kaeufer.contentEquals(verkaeufer), Fehler.DATEN)
        // Nur das eine Muster von otc.yskar.app: Schluss genau 12 vor der Frist.
        pruefe(schluss >= 1 && frist - schluss == Otc.EINLOESE_PUFFER.toLong(), Fehler.FRISTEN)
        val teile = Otc.Teile(kaeufer, verkaeufer, hash, frist, schluss)
        pruefe(Address.isValid(adresse) && Otc.adresse(teile) == adresse, Fehler.SKRIPT)
        return Sperre(kennung, teile, ysr(o), adresse)
    }

    private fun bestaetigungsZahl(o: JSONObject): Long {
        val v = o.opt("n")
        pruefe(v is Int || v is Long, Fehler.DATEN)
        val n = (v as Number).toLong()
        pruefe(n in N_MIN..N_MAX, Fehler.DATEN)
        return n
    }

    private fun abfrage(text: String): Map<String, List<String>>? = runCatching {
        val teile = LinkedHashMap<String, MutableList<String>>()
        for (stueck in text.split("&")) {
            if (stueck.isEmpty()) continue
            val gleich = stueck.indexOf('=')
            val name = if (gleich < 0) stueck else stueck.substring(0, gleich)
            val wert = if (gleich < 0) "" else stueck.substring(gleich + 1)
            teile.getOrPut(URLDecoder.decode(name, "UTF-8")) { mutableListOf() }
                .add(URLDecoder.decode(wert, "UTF-8"))
        }
        teile
    }.getOrNull()

    /**
     * Einen Auftrag lesen und alles pruefen, was ohne Knoten geht.
     *
     * [eigeneAdresse] ist die der Wallet (oeffentlich). Den eigenen
     * Schluessel erkennt die Wallet an ihr: Adresse = Hash des Schluessels.
     * So geht das Lesen ohne Entsperren. [jetztMs]: Uhr des Geraets.
     */
    fun lesen(text: String, eigeneAdresse: String, jetztMs: Long): Lesung = try {
        Lesung.Ok(lesenOderAbbruch(text.trim(), eigeneAdresse, jetztMs))
    } catch (a: Abbruch) {
        Lesung.Abgelehnt(a.fehler)
    } catch (e: IllegalArgumentException) {
        Lesung.Abgelehnt(Fehler.DATEN)
    }

    private fun istEigen(schluessel: ByteArray, eigeneAdresse: String): Boolean =
        Address.forPublicKey(schluessel) == eigeneAdresse

    private fun lesenOderAbbruch(t: String, eigeneAdresse: String, jetztMs: Long): Gelesen {
        pruefe(istOtcCode(t), Fehler.FORMAT)
        val p = abfrage(t.substring(t.indexOf('?') + 1)) ?: throw Abbruch(Fehler.FORMAT)
        pruefe(p.keys.all { it in setOf("v", "auftrag", "d", "cb") }, Fehler.FORMAT)
        pruefe(p.values.all { it.size == 1 }, Fehler.FORMAT)
        pruefe(p["v"]?.single() == "1", Fehler.VERSION)
        val art = p["auftrag"]?.single()?.let { Art.aus(it) } ?: throw Abbruch(Fehler.ART)
        val djson = p["d"]?.single()?.let { Nachricht.base64urlText(it) } ?: throw Abbruch(Fehler.DATEN)
        val o = runCatching { JSONObject(djson) }.getOrNull() ?: throw Abbruch(Fehler.DATEN)
        pruefe(o.keys().asSequence().toSet() == FELDER.getValue(art), Fehler.DATEN)

        val cb = p["cb"]?.single()
        if (art != Art.HASH) pruefe(cb == null, Fehler.RUECKRUF)

        return when (art) {
            Art.HASH -> {
                val rueckruf = cb?.let { Nachricht.base64urlText(it) } ?: throw Abbruch(Fehler.RUECKRUF)
                when (Nachricht.rueckrufErlaubt(rueckruf, DOMAIN)) {
                    null -> Unit
                    Nachricht.Ablehnung.RUECKRUF_HOST -> throw Abbruch(Fehler.RUECKRUF_HOST)
                    else -> throw Abbruch(Fehler.RUECKRUF)
                }
                val kennung = hex32(o, "kennung")
                val adresse = text(o, "adresse")
                pruefe(Address.isValid(adresse) && adresse == eigeneAdresse, Fehler.ADRESSE_FREMD)
                val betrag = ysr(o)
                val (g, m) = gegen(o)
                val ablaufText = text(o, "ablauf")
                pruefe(ZEIT.matches(ablaufText), Fehler.DATEN)
                val ablauf = runCatching { LocalDateTime.parse(ablaufText, ZEITFORM).toInstant(ZoneOffset.UTC) }
                    .getOrNull() ?: throw Abbruch(Fehler.DATEN)
                pruefe(ablauf.toEpochMilli() > jetztMs, Fehler.ABGELAUFEN)
                pruefe(ablauf.toEpochMilli() - jetztMs <= GUELTIG_MAX_MS, Fehler.ZU_LANGE)
                Gelesen.Hash(kennung, adresse, betrag, g, m, ablauf, ablaufText, rueckruf)
            }
            Art.SPERREN -> {
                val s = sperre(o)
                pruefe(istEigen(s.teile.verkaeufer, eigeneAdresse), Fehler.NICHT_BETEILIGT)
                val n = bestaetigungsZahl(o)
                val (g, m) = gegen(o)
                val gs = text(o, "gegenSperre")
                pruefe(GEGEN_SPERRE.matches(gs), Fehler.DATEN)
                Gelesen.Sperren(s, n, g, m, gs)
            }
            Art.EINLOESEN -> {
                val s = sperre(o)
                pruefe(istEigen(s.teile.kaeufer, eigeneAdresse), Fehler.NICHT_BETEILIGT)
                Gelesen.Einloesen(s, bestaetigungsZahl(o))
            }
            Art.ZURUECK -> {
                val s = sperre(o)
                pruefe(istEigen(s.teile.verkaeufer, eigeneAdresse), Fehler.NICHT_BETEILIGT)
                Gelesen.Zurueck(s)
            }
        }
    }

    // ------------------------------------------------------------- hash

    /**
     * Die Nachricht, die der Kaeufer fuer `hash` unterschreibt. Gleiche Form
     * wie die Anmeldung (Kopfzeile, feste Zeilen), eigener Zweck -- eine
     * Anmeldung ist sie damit nie. Signiert und gesendet wird sie mit
     * [Nachricht.signieren] und `Rueckruf.senden(url, DOMAIN, ...)`.
     *
     * Heraus geht nur h, nie x.
     */
    fun hashNachricht(a: Gelesen.Hash, otcWurzel: ByteArray): String {
        val h = Otc.fingerabdruck(Otc.geheimnis(otcWurzel, a.kennung))
        return "${Nachricht.KOPF}\n" +
            "purpose: otc-hash\n" +
            "domain: $DOMAIN\n" +
            "address: ${a.adresse}\n" +
            "swap: ${Hex.toHex(a.kennung)}\n" +
            "hash: ${Hex.toHex(h)}\n" +
            "expires: ${a.ablaufText}"
    }

    // ------------------------------------------------------------- sperren

    /**
     * Darf der Verkaeufer bei dieser Hoehe sperren? [spitze]: letzter Block.
     * Die Sperre landet fruehestens im naechsten Block.
     *
     * Gesendet wird danach eine gewoehnliche Ueberweisung (Typ 1) an
     * [Sperre.skriptAdresse] ueber `Senden.pruefe` -- mit allen Pruefungen,
     * die jede Ueberweisung hat.
     */
    fun sperrenPruefen(a: Gelesen.Sperren, spitze: Long): Fehler? {
        val naechste = spitze + 1
        val t = a.sperre.teile
        if (naechste + a.n + EINLOESE_RAND > t.einloeseSchluss) return Fehler.ZU_KNAPP
        if (t.frist - naechste > SPERRE_MAX_BLOECKE) return Fehler.ZU_LANG
        return null
    }

    // ------------------------------------------------------- einloesen / zurueck

    /** Was der Knoten ueber das Skriptkonto sagt. */
    class Skriptkonto(
        val guthaben: Long,
        val naechsteNonce: Long,
        /** Hoehen der Bloecke mit Eingaengen auf das Skriptkonto. */
        val eingaenge: List<Long>,
        val wartend: Boolean,
    ) {
        /** Bestaetigungen des JUENGSTEN Eingangs -- der knappste zaehlt. 0 ohne Eingang. */
        fun bestaetigungen(spitze: Long): Long {
            val juengster = eingaenge.maxOrNull() ?: return 0
            return if (juengster > spitze) 0 else spitze - juengster + 1
        }
    }

    sealed interface Bau {
        /** [einloesen]: Kaeufer-Zweig (Witness mit Geheimnis), sonst Zurueckholen. */
        class Ok(val entwurf: SkriptAusgabe.Entwurf, val einloesen: Boolean, val bytes: Int) : Bau
        data class Abgelehnt(val fehler: Fehler) : Bau
    }

    /** Groesse der fertigen Transaktion -- die Witness-Form hat feste Laenge. */
    fun groesse(e: SkriptAusgabe.Entwurf, einloesen: Boolean): Int =
        SkriptAusgabe.serialisieren(e, ByteArray(if (einloesen) WITNESS_EINLOESEN else WITNESS_ZURUECK)).size

    /**
     * Das Geheimnis fuer diesen Tausch -- erst nach dem Entsperren, aus
     * [Wallet.otcWurzel]. null, wenn es nicht zum Hash im Skript passt
     * (anderes Konto, andere Wallet): dann [Fehler.GEHEIMNIS].
     */
    fun geheimnisFuer(a: Gelesen.Einloesen, otcWurzel: ByteArray): ByteArray? {
        val x = Otc.geheimnis(otcWurzel, a.sperre.kennung)
        return if (Otc.passt(x, a.sperre.teile.hash)) x else null
    }

    /**
     * Einloesen bauen (Kaeufer). Ziel ist immer die eigene Adresse, Betrag
     * das ganze Guthaben des Skriptkontos abzueglich [gebuehr]. Das Geheimnis
     * gehoert nicht in den Entwurf (er wird ohne Witness signiert); es kommt
     * erst beim [unterschreiben] dazu.
     *
     * [erwartetYsr]: der Betrag, den die Wallet sich beim hash-Auftrag
     * gemerkt hat (null, wenn sie keinen kennt).
     */
    fun einloesenBauen(
        a: Gelesen.Einloesen,
        eigeneAdresse: String,
        konto: Skriptkonto,
        spitze: Long,
        gebuehr: Long,
        erwartetYsr: Long?,
    ): Bau {
        val s = a.sperre
        if (erwartetYsr != null && erwartetYsr != s.ysr) return Bau.Abgelehnt(Fehler.BETRAG)
        if (spitze + 1 + EINLOESE_RAND > s.teile.einloeseSchluss) return Bau.Abgelehnt(Fehler.ZU_SPAET)
        if (konto.wartend) return Bau.Abgelehnt(Fehler.WARTEND)
        if (konto.bestaetigungen(spitze) < a.n) return Bau.Abgelehnt(Fehler.UNBESTAETIGT)
        if (konto.guthaben < s.ysr) return Bau.Abgelehnt(Fehler.GUTHABEN)
        return bauen(s, eigeneAdresse, konto, gebuehr, einloesen = true, validFrom = 0, validUntil = s.teile.einloeseSchluss)
    }

    /** Zurueckholen bauen (Verkaeufer). Erst ab der Frist; Ziel die eigene Adresse. */
    fun zurueckBauen(
        a: Gelesen.Zurueck,
        eigeneAdresse: String,
        konto: Skriptkonto,
        spitze: Long,
        gebuehr: Long,
    ): Bau {
        val s = a.sperre
        if (spitze + 1 < s.teile.frist) return Bau.Abgelehnt(Fehler.ZU_FRUEH)
        if (konto.wartend) return Bau.Abgelehnt(Fehler.WARTEND)
        return bauen(s, eigeneAdresse, konto, gebuehr, einloesen = false, validFrom = s.teile.frist, validUntil = 0)
    }

    private fun bauen(
        s: Sperre,
        eigeneAdresse: String,
        konto: Skriptkonto,
        gebuehr: Long,
        einloesen: Boolean,
        validFrom: Long,
        validUntil: Long,
    ): Bau {
        require(gebuehr >= 0) { "Gebuehr negativ" }
        val betrag = konto.guthaben - gebuehr
        if (betrag < Params.DUST_LIMIT) return Bau.Abgelehnt(Fehler.GUTHABEN)
        val e = SkriptAusgabe.Entwurf(
            skript = s.skript,
            an = Address.decode(eigeneAdresse),
            betrag = betrag,
            gebuehr = gebuehr,
            nonce = konto.naechsteNonce,
            validFrom = validFrom,
            validUntil = validUntil,
        )
        return Bau.Ok(e, einloesen, groesse(e, einloesen))
    }

    // ------------------------------------------------------------ Unterschrift

    class Fertig(val roh: ByteArray, val txidHex: String)

    /**
     * Unterschreiben -- der Entwurf geht unveraendert hinein (Regel 19).
     * Drei Gegenproben (Regel 21), jede bricht ab statt zu senden:
     *   1. das Skript des Entwurfs ergibt die angezeigte Skriptadresse,
     *   2. die eigene Signatur ist gueltig,
     *   3. die echte Groesse ist nicht groesser als die berechnete.
     * [geheimnis]: genau beim Einloesen, aus [geheimnisFuer].
     */
    fun unterschreiben(
        s: Sperre,
        bau: Bau.Ok,
        privateKey: ByteArray,
        publicKey: ByteArray,
        geheimnis: ByteArray?,
    ): Fertig {
        val e = bau.entwurf
        check(Address.forScript(e.skript) == s.skriptAdresse) { "Gegenprobe 1: Skriptadresse weicht ab" }
        val einloesen = bau.einloesen
        check((geheimnis != null) == einloesen) { "Geheimnis nur beim Einloesen" }
        if (geheimnis != null) check(Otc.passt(geheimnis, s.teile.hash)) { "Geheimnis passt nicht" }
        val eigener = if (einloesen) s.teile.kaeufer else s.teile.verkaeufer
        check(publicKey.contentEquals(eigener)) { "Schluessel gehoert nicht in diesen Zweig" }
        val sighash = SkriptAusgabe.sighash(e)
        val sig = Wallet.sign(sighash, privateKey)
        check(Wallet.verifyRaw(sig, sighash, publicKey)) { "Gegenprobe 2: eigene Signatur ungueltig" }
        val witness = if (einloesen) Otc.witnessEinloesen(sig, geheimnis!!) else Otc.witnessZurueck(sig)
        val roh = SkriptAusgabe.serialisieren(e, witness)
        check(roh.size <= bau.bytes) { "Gegenprobe 3: groesser als berechnet" }
        return Fertig(roh, Hex.toHex(SkriptAusgabe.txid(roh)))
    }
}
