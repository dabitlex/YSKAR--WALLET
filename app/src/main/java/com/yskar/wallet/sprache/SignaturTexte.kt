package com.yskar.wallet.sprache

import com.yskar.wallet.signatur.Nachricht.Ablehnung
import com.yskar.wallet.signatur.Unterschrift.Stoerung

/**
 * Texte: Nachrichten signieren und Anmelden per QR-Code.
 *
 * Wortlaut nach der bestehenden Wallet (reference/YSKAR-WALLET, i18n `sig`
 * und `qr`) - wer beide Wallets kennt, soll dieselben Saetze lesen.
 */
interface SignaturTexte {
    // Eintraege unter "Einstellungen"
    val rubrik: String
    val eintragQr: String
    val eintragQrHinweis: String
    val eintragSignieren: String
    val einleitung: String

    // Eingabe
    val nachricht: String
    val platzhalter: String
    val einfuegen: String
    val kopfHinweis: String
    val weiter: String

    // Anmeldung ansehen
    val anmeldungTitel: String
    val anmeldenBei: String
    val adresse: String
    val dieseWallet: String
    val laeuftAb: String
    fun inMinuten(minuten: Long): String
    val abgelaufen: String
    val antwortAn: String
    val genau: String
    val anmeldeHinweis: String
    val anmelden: String

    // Freien Text ansehen
    val textTitel: String
    val duSignierst: String
    val ergaenztHinweis: String
    val signierenKnopf: String

    // Bestaetigen
    val danach: String
    val danachPin: String
    val pinTitel: String
    val pinText: String
    val bioGrund: String

    // Ergebnis
    val ergebnisTitel: String
    val signiert: String
    val signiertMit: String
    val schluessel: String
    val signatur: String
    val allesKopieren: String
    val kopiert: String
    val signaturHinweis: String

    // Abgelehnt
    val abgelehntTitel: String
    val nichtsSigniert: String
    fun grund(ablehnung: Ablehnung): String
    fun stoerung(stoerung: Stoerung): String

    // Anmelden per QR-Code
    val qrTitel: String
    val scannerTitel: String
    val scannerKeinCode: String
    fun sendet(domain: String): String
    val sendetHinweis: String
    val angemeldet: String
    fun angemeldetText(domain: String): String
    fun nichtAngenommen(domain: String): String
    val vonSeite: String
    fun zitat(text: String): String
    val keinText: String
    val weiterleitung: String
    val netz: String
    val neuerCode: String
    val qrAbgelehnt: String
    val seite: String
    val schliessen: String
    val fertig: String
}

object SignaturDe : SignaturTexte {
    override val rubrik = "Signaturen"
    override val eintragQr = "Mit QR-Code anmelden"
    override val eintragQrHinweis = "Auf einer YSKAR-Seite, zum Beispiel YSKAR Lab."
    override val eintragSignieren = "Nachricht signieren"
    override val einleitung =
        "Beweise, dass dir diese Wallet gehört. Mit einer Signatur lassen sich keine YSR bewegen."

    override val nachricht = "Nachricht"
    override val platzhalter = "Nachricht einfügen oder tippen …"
    override val einfuegen = "Einfügen"
    override val kopfHinweis =
        "Text ohne die erste Zeile „YSKAR Signed Message“ wird als freie Textnachricht signiert. " +
            "Vor dem Signieren siehst du genau, was signiert wird."
    override val weiter = "Weiter"

    override val anmeldungTitel = "Anmeldeanfrage"
    override val anmeldenBei = "Anmelden bei"
    override val adresse = "Adresse"
    override val dieseWallet = "Diese Wallet"
    override val laeuftAb = "Läuft ab"
    override fun inMinuten(minuten: Long) = if (minuten <= 1) "in unter 1 Min." else "in $minuten Min."
    override val abgelaufen = "abgelaufen"
    override val antwortAn = "Antwort geht an"
    override val genau = "Genaue Nachricht"
    override val anmeldeHinweis =
        "Melde dich nur auf Seiten an, die du selbst geöffnet hast. Die Signatur beweist, " +
            "dass dir die Adresse gehört; YSR senden kann sie nicht."
    override val anmelden = "Anmelden"

    override val textTitel = "Nachricht prüfen"
    override val duSignierst = "Du signierst diese Nachricht"
    override val ergaenztHinweis =
        "Die ersten beiden Zeilen hat die Wallet ergänzt. Sie kennzeichnen eine Textsignatur, " +
            "die nie mit einer Anmeldung oder Zahlung verwechselt werden kann."
    override val signierenKnopf = "Signieren"

    override val danach = "Mit Fingerabdruck oder PIN bestätigen"
    override val danachPin = "Mit deiner PIN bestätigen"
    override val pinTitel = "Signatur bestätigen"
    override val pinText = "Gib deine PIN ein, um die Nachricht zu signieren"
    override val bioGrund = "Nachricht signieren"

    override val ergebnisTitel = "Signatur"
    override val signiert = "Nachricht signiert"
    override val signiertMit = "Signiert mit"
    override val schluessel = "Öffentlicher Schlüssel"
    override val signatur = "Signatur"
    override val allesKopieren = "Alles kopieren (JSON)"
    override val kopiert = "Kopiert"
    override val signaturHinweis =
        "Füge das Kopierte dort ein, wo die Signatur verlangt wird. Dein Schlüssel ist nicht darin."

    override val abgelehntTitel = "Diese Anfrage kann nicht signiert werden"
    override val nichtsSigniert = "Es wurde nichts signiert."

    override fun grund(ablehnung: Ablehnung) = when (ablehnung) {
        Ablehnung.LEER -> "Die Nachricht ist leer."
        Ablehnung.ZU_LANG -> "Die Nachricht ist zu lang (höchstens 1.000 Zeichen)."
        Ablehnung.KOPF_FEHLT -> "Die erste Zeile „YSKAR Signed Message“ fehlt."
        Ablehnung.ZEILENENDE ->
            "Die Nachricht enthält Windows-Zeilenenden. Sie muss Zeichen für Zeichen stimmen."
        Ablehnung.LEERZEICHEN ->
            "Eine Zeile endet mit einem Leerzeichen. Die Nachricht muss Zeichen für Zeichen stimmen."
        Ablehnung.ZWECK_UNBEKANNT -> "Diese Art von Nachricht kennt die Wallet nicht."
        Ablehnung.AUFBAU -> "Die Nachricht ist nicht richtig aufgebaut."
        Ablehnung.DOMAIN ->
            "Die Seite gehört nicht zu YSKAR. Anmeldungen signiert die Wallet nur für yskar.app."
        Ablehnung.ADRESSE_FREMD ->
            "Die Adresse in der Nachricht ist nicht deine. Vielleicht versucht jemand, " +
                "sich mit einer anderen Wallet anzumelden."
        Ablehnung.ABGELAUFEN ->
            "Die Anfrage ist abgelaufen. Lade die Seite neu und versuche es noch einmal."
        Ablehnung.ZU_LANGE -> "Die Anfrage gilt zu lange (höchstens 15 Minuten)."
        Ablehnung.HEX -> "Das sieht aus wie Rohdaten. Solche Daten signiert die Wallet nicht."
        Ablehnung.QR_FORMAT -> "Das ist kein gültiger Anmelde-Code."
        Ablehnung.KEIN_LOGIN -> "Der Code enthält keine Anmeldung."
        Ablehnung.RUECKRUF_HTTPS -> "Die Antwort-Adresse ist nicht sicher (nur https, ohne Port)."
        Ablehnung.RUECKRUF_HOST ->
            "Die Antwort-Adresse gehört nicht zur Seite in der Nachricht. " +
                "Der Code wurde vielleicht verändert."
    }

    override fun stoerung(stoerung: Stoerung) = when (stoerung) {
        Stoerung.WOERTER_UNBRAUCHBAR -> "Die Wörter aus dem Tresor sind unbrauchbar."
        Stoerung.SCHLUESSEL_PASST_NICHT -> "Der Schlüssel passt nicht zur angezeigten Adresse."
        Stoerung.GEGENPROBE -> "Die eigene Signatur ließ sich nicht bestätigen."
    }

    override val qrTitel = "Anmelden"
    override val scannerTitel = "Anmeldung scannen"
    override val scannerKeinCode = "Das ist kein Anmelde-Code einer YSKAR-Seite."
    override fun sendet(domain: String) = "Anmeldung bei $domain …"
    override val sendetHinweis =
        "Signiert auf diesem Gerät. Gesendet wird nur die Signatur, nie dein Schlüssel."
    override val angemeldet = "Angemeldet"
    override fun angemeldetText(domain: String) =
        "Du bist bei $domain angemeldet. Du kannst zurück in deinen Browser."
    override fun nichtAngenommen(domain: String) = "$domain hat die Anmeldung nicht angenommen"
    override val vonSeite = "Nachricht der Seite:"
    override fun zitat(text: String) = "„$text“"
    override val keinText = "Die Seite hat die Anmeldung abgelehnt."
    override val weiterleitung =
        "Die Seite wollte die Antwort an eine andere Adresse umleiten. Die Wallet folgt dem nicht."
    override val netz = "Die Seite war nicht erreichbar. Prüfe die Verbindung."
    override val neuerCode = "Lade die Seite im Browser neu und scanne den neuen Code."
    override val qrAbgelehnt = "Dieser Code kann nicht verwendet werden"
    override val seite = "Seite"
    override val schliessen = "Schließen"
    override val fertig = "Fertig"
}

object SignaturEn : SignaturTexte {
    override val rubrik = "Signatures"
    override val eintragQr = "Sign in with QR code"
    override val eintragQrHinweis = "On a YSKAR site, for example YSKAR Lab."
    override val eintragSignieren = "Sign message"
    override val einleitung = "Prove that you own this wallet. A signature cannot move YSR."

    override val nachricht = "Message"
    override val platzhalter = "Paste or type a message …"
    override val einfuegen = "Paste"
    override val kopfHinweis =
        "Text without the first line “YSKAR Signed Message” is signed as a plain text message. " +
            "You will see the exact message before signing."
    override val weiter = "Continue"

    override val anmeldungTitel = "Sign-in request"
    override val anmeldenBei = "Sign in to"
    override val adresse = "Address"
    override val dieseWallet = "This wallet"
    override val laeuftAb = "Expires"
    override fun inMinuten(minuten: Long) = if (minuten <= 1) "in under 1 min" else "in $minuten min"
    override val abgelaufen = "expired"
    override val antwortAn = "Reply goes to"
    override val genau = "Exact message"
    override val anmeldeHinweis =
        "Only sign in on sites you opened yourself. Signing proves ownership of the address; " +
            "it cannot send YSR."
    override val anmelden = "Sign in"

    override val textTitel = "Check message"
    override val duSignierst = "You are signing this message"
    override val ergaenztHinweis =
        "The first two lines were added by the wallet. They mark this as a text signature, " +
            "so it can never be mistaken for a sign-in or a payment."
    override val signierenKnopf = "Sign"

    override val danach = "Confirm with fingerprint or PIN"
    override val danachPin = "Confirm with your PIN"
    override val pinTitel = "Confirm signature"
    override val pinText = "Enter your PIN to sign the message"
    override val bioGrund = "Sign message"

    override val ergebnisTitel = "Signature"
    override val signiert = "Message signed"
    override val signiertMit = "Signed with"
    override val schluessel = "Public key"
    override val signatur = "Signature"
    override val allesKopieren = "Copy all (JSON)"
    override val kopiert = "Copied"
    override val signaturHinweis =
        "Paste what you copied where the signature is asked for. Your key is not in it."

    override val abgelehntTitel = "This request can't be signed"
    override val nichtsSigniert = "Nothing was signed."

    override fun grund(ablehnung: Ablehnung) = when (ablehnung) {
        Ablehnung.LEER -> "The message is empty."
        Ablehnung.ZU_LANG -> "The message is too long (1,000 characters at most)."
        Ablehnung.KOPF_FEHLT -> "The first line “YSKAR Signed Message” is missing."
        Ablehnung.ZEILENENDE ->
            "The message contains Windows line endings. It has to match character for character."
        Ablehnung.LEERZEICHEN ->
            "A line ends with a space. The message has to match character for character."
        Ablehnung.ZWECK_UNBEKANNT -> "The wallet does not know this kind of message."
        Ablehnung.AUFBAU -> "The message is not built correctly."
        Ablehnung.DOMAIN ->
            "This site does not belong to YSKAR. The wallet only signs sign-ins for yskar.app."
        Ablehnung.ADRESSE_FREMD ->
            "The address in the message is not yours. Someone may be trying to sign in " +
                "with another wallet."
        Ablehnung.ABGELAUFEN -> "The request has expired. Reload the page and try again."
        Ablehnung.ZU_LANGE -> "The request is valid for too long (15 minutes at most)."
        Ablehnung.HEX -> "This looks like raw data. The wallet does not sign data like this."
        Ablehnung.QR_FORMAT -> "This is not a valid sign-in code."
        Ablehnung.KEIN_LOGIN -> "The code does not contain a sign-in."
        Ablehnung.RUECKRUF_HTTPS -> "The reply address is not secure (https only, no port)."
        Ablehnung.RUECKRUF_HOST ->
            "The reply address does not belong to the site in the message. " +
                "The code may have been tampered with."
    }

    override fun stoerung(stoerung: Stoerung) = when (stoerung) {
        Stoerung.WOERTER_UNBRAUCHBAR -> "The words from the vault are unusable."
        Stoerung.SCHLUESSEL_PASST_NICHT -> "The key does not match the address shown."
        Stoerung.GEGENPROBE -> "The wallet could not confirm its own signature."
    }

    override val qrTitel = "Sign in"
    override val scannerTitel = "Scan sign-in"
    override val scannerKeinCode = "This is not a sign-in code of a YSKAR site."
    override fun sendet(domain: String) = "Signing in to $domain …"
    override val sendetHinweis = "Signed on this device. Only the signature is sent, never your key."
    override val angemeldet = "Signed in"
    override fun angemeldetText(domain: String) =
        "You are signed in to $domain. You can go back to your browser."
    override fun nichtAngenommen(domain: String) = "$domain did not accept the sign-in"
    override val vonSeite = "Message from the site:"
    override fun zitat(text: String) = "“$text”"
    override val keinText = "The site declined the sign-in."
    override val weiterleitung =
        "The site tried to redirect the reply to another address. The wallet does not follow that."
    override val netz = "The site could not be reached. Check your connection."
    override val neuerCode = "Reload the page in your browser and scan the new code."
    override val qrAbgelehnt = "This code can't be used"
    override val seite = "Site"
    override val schliessen = "Close"
    override val fertig = "Done"
}
