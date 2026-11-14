package com.yskar.wallet.signatur

import com.yskar.wallet.kern.Address
import com.yskar.wallet.kern.Wallet
import org.json.JSONObject
import java.net.URI
import java.net.URLDecoder
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle
import java.util.Base64

/**
 * Nachrichten signieren - fuer die Anmeldung auf YSKAR Lab und fuer freie
 * Texte.
 *
 * Port von `src/wallet/nachricht.ts` der bestehenden Wallet
 * (reference/YSKAR-WALLET). Das Format ist dort festgelegt, nicht hier; eine
 * Plattform, die Anmeldungen der einen Wallet annimmt, nimmt die der anderen.
 *
 * WAS SIGNIERT WIRD: die rohen UTF-8-Bytes der vollstaendigen Nachricht, mit
 * Ed25519, ohne zusaetzliches Hashen und ohne verstecktes Praefix. Jede
 * Nachricht beginnt SICHTBAR mit der Zeile "YSKAR Signed Message". Damit
 * kann eine signierte Nachricht nie als Ueberweisung gelten: Ueberweisungen
 * sind Binaerdaten mit der Kennung der Kette, eine Nachricht ist Text mit
 * dieser Kopfzeile. Rohdaten (Hex) signiert die Wallet nicht.
 *
 *   Anmeldung (feste Zeilen, genau diese Reihenfolge):
 *     YSKAR Signed Message
 *     purpose: login
 *     domain: lab.yskar.app
 *     address: ysr1...
 *     nonce: <32 Hex-Zeichen>
 *     issued: 2026-11-02T11:50:00Z
 *     expires: 2026-11-02T11:55:00Z
 *
 *   Freier Text:
 *     YSKAR Signed Message
 *     purpose: text
 *     <Leerzeile>
 *     <Text>
 *
 * Diese Datei kennt weder den Sendepfad noch das Netz, und sie benutzt keine
 * Android-API - so laufen ihre Tests auf der JVM (`NachrichtTest`,
 * `AnmeldecodeTest`). Sie bekommt einen Schluessel, gibt eine Signatur
 * zurueck und sonst nichts.
 */
object Nachricht {

    const val KOPF = "YSKAR Signed Message"

    /**
     * Wofuer die Wallet eine Anmeldung signiert: diese Hosts und ihre
     * Subdomains. Weitere Hosts nur hier eintragen.
     */
    val ERLAUBTE_HOSTS: List<String> = listOf("yskar.app")

    /** Hoechstens so viele Zeichen Text in einer freien Nachricht. */
    const val TEXT_MAX = 1000

    /** Eine Anmeldung darf hoechstens so lange in der Zukunft ablaufen. */
    const val GUELTIG_MAX_MS = 15 * 60 * 1000L

    /** Warum etwas nicht signiert wird. */
    enum class Ablehnung {
        LEER,             // nichts eingegeben
        ZU_LANG,          // mehr als TEXT_MAX Zeichen
        KOPF_FEHLT,       // ohne Kopfzeile, wo sie Pflicht ist
        ZEILENENDE,       // \r in einer Nachricht mit Kopfzeile
        LEERZEICHEN,      // Leerzeichen am Zeilenende in einer Nachricht mit Kopfzeile
        ZWECK_UNBEKANNT,  // purpose weder login noch text
        AUFBAU,           // Zeilen fehlen, falsche Reihenfolge, falsches Format
        DOMAIN,           // Domain nicht erlaubt
        ADRESSE_FREMD,    // Adresse ist nicht die dieser Wallet
        ABGELAUFEN,       // expires liegt in der Vergangenheit
        ZU_LANGE,         // expires liegt mehr als 15 Minuten in der Zukunft
        HEX,              // Text sieht aus wie Hex-Daten

        // nur bei QR-Codes und yskar:-Links
        QR_FORMAT,        // kein lesbarer Anmelde-Code
        KEIN_LOGIN,       // Code enthaelt keine Anmeldung
        RUECKRUF_HTTPS,   // Rueckruf nicht ueber https, mit Port oder Benutzername
        RUECKRUF_HOST,    // Rueckruf geht nicht an die Domain der Nachricht
    }

    data class Anmeldung(
        val domain: String,
        val address: String,
        val nonce: String,
        val issued: Instant,
        val expires: Instant,
    )

    sealed interface Pruefung {
        /** Eine Anmeldung; [nachricht] wird Byte fuer Byte so signiert, wie sie kam. */
        data class Login(val nachricht: String, val anmeldung: Anmeldung) : Pruefung

        /** Freier Text; [ergaenzt]: Die Kopfzeilen hat die Wallet davorgesetzt. */
        data class Text(val nachricht: String, val text: String, val ergaenzt: Boolean) : Pruefung

        data class Abgelehnt(val grund: Ablehnung) : Pruefung
    }

    data class Signiert(
        val address: String,
        val publicKey: String,   // 64 Hex-Zeichen
        val signature: String,   // 128 Hex-Zeichen
        val message: String,
    )

    // ------------------------------------------------------------- Bausteine

    private val HOST = Regex("^[a-z0-9]([a-z0-9-]*[a-z0-9])?(\\.[a-z0-9]([a-z0-9-]*[a-z0-9])?)+$")

    /** yskar.app und jede Subdomain davon. Gross/klein egal, kein Port, kein Pfad. */
    fun hostErlaubt(host: String): Boolean {
        val h = host.lowercase()
        if (!HOST.matches(h)) return false
        return ERLAUBTE_HOSTS.any { h == it || h.endsWith(".$it") }
    }

    private val NUR_HEX = Regex("^[0-9a-fA-F]+$")
    private val LEERRAUM = Regex("\\s+")
    private val NULL_X = Regex("^0x", RegexOption.IGNORE_CASE)

    /** Sieht aus wie Hex-Daten: nur Hex-Zeichen (mit Leerraum, optional 0x) und mindestens 32 davon. */
    private fun wieHex(text: String): Boolean {
        val ohne = text.replace(LEERRAUM, "").replace(NULL_X, "")
        return ohne.length >= 32 && NUR_HEX.matches(ohne)
    }

    private val ZEIT = Regex("^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}Z$")
    private val ZEITFORM = DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss'Z'")
        .withResolverStyle(ResolverStyle.STRICT)

    /** Genau `2026-11-02T11:50:00Z`, und ein Tag, den es gibt. Sonst null. */
    private fun zeitLesen(s: String): Instant? {
        if (!ZEIT.matches(s)) return null
        return runCatching { LocalDateTime.parse(s, ZEITFORM).toInstant(ZoneOffset.UTC) }.getOrNull()
    }

    private fun feld(zeile: String?, name: String): String? {
        val vorn = "$name: "
        return if (zeile != null && zeile.startsWith(vorn) && zeile.length > vorn.length) {
            zeile.substring(vorn.length)
        } else null
    }

    private fun textPruefen(text: String): Ablehnung? = when {
        text.isBlank() -> Ablehnung.LEER
        text.codePointCount(0, text.length) > TEXT_MAX -> Ablehnung.ZU_LANG
        wieHex(text) -> Ablehnung.HEX
        else -> null
    }

    private val ENDE_LEER = Regex("[ \\t]+$")
    private val ENDET_LEER = Regex("[ \\t]$")

    // ------------------------------------------------------------ Vorbereiten

    /**
     * Pruefen, was signiert werden soll, und die genaue Nachricht bauen.
     *
     * Mit Kopfzeile gilt die Nachricht Byte fuer Byte, wie sie ist: kein \r,
     * keine Leerzeichen am Zeilenende, bekannte Zwecke. Ohne Kopfzeile ist es
     * freier Text: \r und Leerzeichen am Zeilenende werden entfernt, dann
     * setzt die Wallet die Kopfzeilen davor. Bei einem QR-Code ([qr]) ist die
     * Kopfzeile Pflicht.
     *
     * [jetztMs]: die Uhr dieses Geraets in Millisekunden. Hereingereicht,
     * damit sich das Ablaufen pruefen laesst, ohne zu warten.
     */
    fun vorbereiten(
        eingabe: String,
        eigeneAdresse: String,
        jetztMs: Long,
        qr: Boolean = false,
    ): Pruefung {
        if (eingabe.isBlank()) return Pruefung.Abgelehnt(Ablehnung.LEER)

        // Kopfzeile = genau diese erste Zeile. "YSKAR Signed Messages ..." ist freier Text.
        val hatKopf = eingabe == KOPF || eingabe.startsWith("$KOPF\n") || eingabe.startsWith("$KOPF\r")
        if (!hatKopf) {
            if (qr) return Pruefung.Abgelehnt(Ablehnung.KOPF_FEHLT)
            val text = eingabe.replace("\r", "")
                .split("\n").joinToString("\n") { it.replace(ENDE_LEER, "") }
                .trimStart('\n').trimEnd('\n')
            textPruefen(text)?.let { return Pruefung.Abgelehnt(it) }
            return Pruefung.Text("$KOPF\npurpose: text\n\n$text", text, ergaenzt = true)
        }

        if (eingabe.contains('\r')) return Pruefung.Abgelehnt(Ablehnung.ZEILENENDE)
        val zeilen = eingabe.split("\n")
        if (zeilen[0] != KOPF) return Pruefung.Abgelehnt(Ablehnung.AUFBAU)
        if (zeilen.any { ENDET_LEER.containsMatchIn(it) }) return Pruefung.Abgelehnt(Ablehnung.LEERZEICHEN)

        val zweck = feld(zeilen.getOrNull(1), "purpose") ?: return Pruefung.Abgelehnt(Ablehnung.AUFBAU)

        if (zweck == "text") {
            if (zeilen.getOrNull(2) != "" || zeilen.size < 4) return Pruefung.Abgelehnt(Ablehnung.AUFBAU)
            val text = zeilen.drop(3).joinToString("\n")
            textPruefen(text)?.let { return Pruefung.Abgelehnt(it) }
            return Pruefung.Text(eingabe, text, ergaenzt = false)
        }

        if (zweck != "login") return Pruefung.Abgelehnt(Ablehnung.ZWECK_UNBEKANNT)

        if (zeilen.size != 7) return Pruefung.Abgelehnt(Ablehnung.AUFBAU)
        val domain = feld(zeilen[2], "domain")
        val address = feld(zeilen[3], "address")
        val nonce = feld(zeilen[4], "nonce")
        val issuedText = feld(zeilen[5], "issued")
        val expiresText = feld(zeilen[6], "expires")
        if (domain == null || address == null || nonce == null || issuedText == null || expiresText == null) {
            return Pruefung.Abgelehnt(Ablehnung.AUFBAU)
        }
        if (!NONCE.matches(nonce)) return Pruefung.Abgelehnt(Ablehnung.AUFBAU)
        val issued = zeitLesen(issuedText)
        val expires = zeitLesen(expiresText)
        if (issued == null || expires == null || issued.isAfter(expires)) {
            return Pruefung.Abgelehnt(Ablehnung.AUFBAU)
        }
        if (!hostErlaubt(domain) || domain != domain.lowercase()) return Pruefung.Abgelehnt(Ablehnung.DOMAIN)
        if (!Address.isValid(address) || address != eigeneAdresse) {
            return Pruefung.Abgelehnt(Ablehnung.ADRESSE_FREMD)
        }
        if (expires.toEpochMilli() <= jetztMs) return Pruefung.Abgelehnt(Ablehnung.ABGELAUFEN)
        if (expires.toEpochMilli() - jetztMs > GUELTIG_MAX_MS) return Pruefung.Abgelehnt(Ablehnung.ZU_LANGE)

        return Pruefung.Login(eingabe, Anmeldung(domain, address, nonce, issued, expires))
    }

    private val NONCE = Regex("^[0-9a-f]{32}$")

    // --------------------------------------------------------------- Signieren

    /**
     * Die fertige Nachricht signieren. Prueft vorher noch einmal Kopfzeile
     * und Zeilenenden - was hier ankommt, ist schon durch [vorbereiten]
     * gegangen. Ohne Kopfzeile wird NIE signiert: Rohe Bytes einer
     * Ueberweisung haben keine.
     */
    fun signieren(nachricht: String, privateKey: ByteArray, publicKey: ByteArray): Signiert {
        require(nachricht.startsWith("$KOPF\n") && !nachricht.contains('\r')) { "keine gueltige Nachricht" }
        val signatur = Wallet.sign(nachricht.toByteArray(Charsets.UTF_8), privateKey)
        return Signiert(
            address = Address.forPublicKey(publicKey),
            publicKey = hex(publicKey),
            signature = hex(signatur),
            message = nachricht,
        )
    }

    /**
     * Alles zusammen zum Kopieren und zum Senden an den Rueckruf, Felder in
     * fester Reihenfolge. Von Hand gebaut: `org.json` haelt die Reihenfolge
     * nicht ueberall.
     */
    fun alsJson(s: Signiert): String =
        "{\"address\":${JSONObject.quote(s.address)}," +
            "\"publicKey\":${JSONObject.quote(s.publicKey)}," +
            "\"signature\":${JSONObject.quote(s.signature)}," +
            "\"message\":${JSONObject.quote(s.message)}}"

    /** Eingefuegtes JSON lesen. null, wenn es nicht die vier Felder als Text enthaelt. */
    fun ausJson(text: String): Signiert? = runCatching {
        val o = JSONObject(text.trim())
        val felder = listOf("address", "publicKey", "signature", "message").map { o.opt(it) as? String }
        if (felder.any { it == null }) null
        else Signiert(felder[0]!!, felder[1]!!, felder[2]!!, felder[3]!!)
    }.getOrNull()

    enum class Echtheit { GUELTIG, UNGUELTIG, ADRESSE_PASST_NICHT, FORMAT }

    private val HEX64 = Regex("^[0-9a-f]{64}$")
    private val HEX128 = Regex("^[0-9a-f]{128}$")

    /**
     * Eine Signatur pruefen: Schluessel und Adresse muessen zusammenpassen,
     * die Signatur muss ueber genau diese Nachricht gehen. Die Nachricht wird
     * nicht veraendert, auch nicht an den Zeilenenden.
     *
     * Geprueft wird nach RFC 8032 (BouncyCastle). Das genuegt fuer die
     * EIGENE Signatur gleich nach dem Unterschreiben; ueber fremde
     * Signaturen in Grenzfaellen urteilt die Wallet damit nicht (REGELN.md,
     * Regel 4 gilt sinngemaess).
     */
    fun pruefen(s: Signiert): Echtheit {
        val pk = s.publicKey.trim().lowercase()
        val sig = s.signature.trim().lowercase()
        val adr = s.address.trim()
        if (!HEX64.matches(pk) || !HEX128.matches(sig) || !Address.isValid(adr) || s.message.isEmpty()) {
            return Echtheit.FORMAT
        }
        val schluessel = ausHex(pk)
        if (Address.forPublicKey(schluessel) != adr) return Echtheit.ADRESSE_PASST_NICHT
        return if (Wallet.verifyRaw(ausHex(sig), s.message.toByteArray(Charsets.UTF_8), schluessel)) {
            Echtheit.GUELTIG
        } else Echtheit.UNGUELTIG
    }

    // ------------------------------------------------------- Anmelden per QR

    /*
      Die Plattform zeigt einen QR-Code (oder einen Link auf demselben Handy):

        yskar:login?msg=<Nachricht base64url>&cb=<https-Rueckruf base64url>

      Die Wallet signiert nur, wenn die Nachricht eine gueltige Anmeldung
      fuer diese Wallet ist und der Rueckruf ueber https genau an die Domain
      der Nachricht geht. Erst nach PIN oder Fingerabdruck geht die Antwort
      hinaus.
    */

    sealed interface QrPruefung {
        data class Ok(val nachricht: String, val anmeldung: Anmeldung, val rueckruf: String) : QrPruefung

        /** [domain] und [rueckrufHost] nur, wenn der Rueckruf nicht zur Nachricht passt. */
        data class Abgelehnt(
            val grund: Ablehnung,
            val domain: String? = null,
            val rueckrufHost: String? = null,
        ) : QrPruefung
    }

    private val ANMELDECODE = Regex("^yskar:(//)?login\\?", RegexOption.IGNORE_CASE)

    /** Schnelle Erkennung, ohne zu pruefen: Ist das ein Anmelde-Code? */
    fun istAnmeldeCode(text: String): Boolean = ANMELDECODE.containsMatchIn(text.trim())

    private val BASE64URL = Regex("^[A-Za-z0-9_-]*$")

    /** base64url (ohne Auffuellung) streng lesen und als UTF-8 deuten. null bei Fehlern. */
    fun base64urlText(s: String): String? {
        if (!BASE64URL.matches(s) || s.length % 4 == 1) return null
        return runCatching {
            val bytes = Base64.getUrlDecoder().decode(s)
            // Streng: Ungueltiges UTF-8 ist ein Fehler, kein Ersatzzeichen.
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes)).toString()
        }.getOrNull()
    }

    /** Fuer Tests und die Plattform: Text als base64url ohne Auffuellung. */
    fun textBase64url(text: String): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(text.toByteArray(Charsets.UTF_8))

    /**
     * Der Rueckruf, zerlegt und geprueft. STRENGER als die Vorlage, mit
     * Absicht: Die Adresse muss so schlicht sein, dass es nichts zu deuten
     * gibt - `https://`, ein Host aus Kleinbuchstaben, Ziffern, Punkt und
     * Bindestrich, dann ein Pfad und hoechstens eine Abfrage aus
     * unverfaenglichen Zeichen. Kein Port, kein Benutzername, kein
     * Rueckstrich, kein `#`. Was zwei Leser verschieden deuten koennten,
     * kommt gar nicht erst durch.
     */
    private val RUECKRUF = Regex(
        "^https://([a-z0-9.-]+)(/[A-Za-z0-9._~!$&'()*+,;=:@%/-]*)?(\\?[A-Za-z0-9._~!$&'()*+,;=:@%/?-]*)?$",
    )

    /** Host und Pfad des Rueckrufs zum Anzeigen ("lab.yskar.app/api/login"), null wenn unlesbar. */
    fun rueckrufZiel(url: String): String? {
        val m = RUECKRUF.matchEntire(url) ?: return null
        return m.groupValues[1] + m.groupValues[2]
    }

    /** Darf an diese Adresse geantwortet werden? https, kein Port, kein Benutzername, erlaubter Host. */
    fun rueckrufErlaubt(url: String, domain: String): Ablehnung? {
        val m = RUECKRUF.matchEntire(url) ?: return Ablehnung.RUECKRUF_HTTPS
        val host = m.groupValues[1]
        // Zweite Meinung: Auch der Leser der Plattform muss denselben Host sehen.
        val uri = runCatching { URI(url) }.getOrNull() ?: return Ablehnung.RUECKRUF_HTTPS
        if (uri.scheme != "https" || uri.port != -1 || uri.rawUserInfo != null || uri.host != host) {
            return Ablehnung.RUECKRUF_HTTPS
        }
        if (host != domain || !hostErlaubt(host)) return Ablehnung.RUECKRUF_HOST
        return null
    }

    /** Der Host eines abgelehnten Rueckrufs, nur zum ANZEIGEN ("geht an: boese.example"). */
    private fun hostZumZeigen(url: String): String? =
        runCatching { URI(url).let { u -> u.host?.let { h -> if (u.port == -1) h else "$h:${u.port}" } } }
            .getOrNull()

    /** `msg=...&cb=...` zerlegen. null, wenn etwas daran nicht lesbar ist. */
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
     * Einen Anmelde-Code pruefen - vom QR-Code oder aus einem `yskar:`-Link.
     * EIN GESCANNTER CODE IST KEIN BEWEIS (REGELN.md, Regel 22): Jeder kann
     * einen drucken. Signiert wird nur, was hier ganz durchkommt.
     */
    fun anmeldungAusCode(text: String, eigeneAdresse: String, jetztMs: Long): QrPruefung {
        val t = text.trim()
        if (!istAnmeldeCode(t)) return QrPruefung.Abgelehnt(Ablehnung.QR_FORMAT)
        val p = abfrage(t.substring(t.indexOf('?') + 1)) ?: return QrPruefung.Abgelehnt(Ablehnung.QR_FORMAT)
        val msg = p["msg"]
        val cb = p["cb"]
        if (msg?.size != 1 || cb?.size != 1) return QrPruefung.Abgelehnt(Ablehnung.QR_FORMAT)
        val nachricht = base64urlText(msg[0])
        val rueckruf = base64urlText(cb[0])
        if (nachricht == null || rueckruf == null) return QrPruefung.Abgelehnt(Ablehnung.QR_FORMAT)

        return when (val v = vorbereiten(nachricht, eigeneAdresse, jetztMs, qr = true)) {
            is Pruefung.Abgelehnt -> QrPruefung.Abgelehnt(v.grund)
            is Pruefung.Text -> QrPruefung.Abgelehnt(Ablehnung.KEIN_LOGIN)
            is Pruefung.Login -> {
                val fehler = rueckrufErlaubt(rueckruf, v.anmeldung.domain)
                if (fehler != null) {
                    QrPruefung.Abgelehnt(fehler, v.anmeldung.domain, hostZumZeigen(rueckruf))
                } else {
                    QrPruefung.Ok(v.nachricht, v.anmeldung, rueckruf)
                }
            }
        }
    }

    // ------------------------------------------------------------------- Hex

    private const val ZIFFERN = "0123456789abcdef"

    private fun hex(bytes: ByteArray): String {
        val s = StringBuilder(bytes.size * 2)
        for (b in bytes) {
            s.append(ZIFFERN[(b.toInt() shr 4) and 0xF]).append(ZIFFERN[b.toInt() and 0xF])
        }
        return s.toString()
    }

    private fun ausHex(text: String): ByteArray =
        ByteArray(text.length / 2) { i -> text.substring(2 * i, 2 * i + 2).toInt(16).toByte() }
}
