package com.yskar.wallet.sprache

/**
 * Texte: der Selbsttest unter Einstellungen.
 *
 * Der Bildschirm (ui/SelbsttestScreen.kt) und die Zeilen der vier Berichte:
 * `Selbsttest.kern`, `Selbsttest.tresor`, `NetzSelbsttest.pruefen` und
 * `BiometriePruefung.pruefen`. Eine Zeile hat einen Namen (was geprueft
 * wurde) und einen Wert (was herauskam); beides steht hier.
 *
 * NUR DIE WORTE. Was geprueft wird, steht in den vier Funktionen und haengt
 * von keiner Sprache ab.
 *
 * Was NICHT hier steht, weil es in jeder Sprache gleich heisst: "txid",
 * Zeiten in "ms", die Namen der Ausnahmen, Adressen und Hexwerte.
 */
interface SelbsttestTexte {
    // Der Bildschirm.
    val titel: String
    val karteKern: String
    val karteTresor: String
    val karteNetz: String
    val karteBiometrie: String
    val biometrieHinweis: String
    val biometriePruefen: String
    val wartetAufSensor: String
    val fussnote: String

    // Das Urteil ueber einen Bericht und seine Zeilen.
    val rechnet: String
    fun bestanden(ms: Long): String
    fun fehlgeschlagen(ms: Long): String

    /** Was ein Bildschirmleser statt des Hakens sagt. */
    val ok: String

    /** Eine Zeile, die nicht bestanden hat: das Wort "FEHLER" vor ihrem Namen. */
    fun fehlerZeile(name: String): String

    // In mehreren Berichten.
    val ausnahme: String
    val aufgeraeumt: String

    // Bericht 1: der Kryptokern.
    val kernAdresse: String
    val kernOeffentlicherSchluessel: String
    val kernLeitungsform: String
    val kernSignaturGilt: String
    val kernGeprueft: String
    val kernNeuUnterschrieben: String

    // Bericht 2: der Tresor.
    val tresorEinrichten: String
    val tresorDauerEinrichten: String
    val tresorAdresseOhnePin: String
    val tresorWoerterNichtInDatei: String
    fun tresorZeichenGeheimtext(zeichen: Int): String
    val tresorRunden: String
    val tresorOeffnenRichtigePin: String
    val tresorDauerOeffnen: String
    val tresorFalschePinAbgewiesen: String
    val tresorEntfernt: String

    // Was der Tresor geantwortet hat (`Selbsttest.beschreibe`).
    fun standOffen(adresse: String): String
    fun standFalschePin(versuch: Int): String
    fun standGesperrt(sekunden: Long): String
    val standKeinTresor: String
    val standBiometrieNeu: String

    /** [grund] kommt aus `tresor/`, auf Deutsch und ohne Umlaute. */
    fun standFehler(grund: String): String

    // Bericht 3: das Netz. Zu jedem Namen der Wert, wo er Worte enthaelt.
    // Betraege kommen schon fertig geschrieben an, mit "YSR" dahinter.
    val netzHoehe: String
    fun netzHoeheWert(hoehe: Long?, mempool: Int): String
    val netzGebuehrenlage: String
    fun netzGebuehrenlageWert(mindestens: String, fassung: Int?, basisBytes: Int?): String
    val netzBasisgroesse: String
    fun netzBasisgroesseWert(basisBytes: Int?, port: Int): String
    val netzSendefassung: String
    fun netzSendefassungWert(fassung: Int, pause: Boolean, darfSenden: Boolean): String
    val netzGebuehr: String
    fun netzGebuehrWert(bytes: Int, gebuehr: String): String
    val netzKnotenErkannt: String
    val netzKontoGelesen: String
    val netzOhneWallet: String
    val netzGuthaben: String
    val netzNonce: String
    fun netzNonceWert(nonce: Long, naechste: Long): String
    val netzVerlauf: String

    /** [quelle] ist das Wort des Spiegels: "fullnode" oder "unavailable". */
    fun netzVerlaufWert(eintraege: Int, wartend: Int, quelle: String): String
    val netzJuengsterEintrag: String

    /** [art] ist der Name aus `netz/Modelle.kt`; [notiz] darf leer sein. */
    fun netzJuengsterEintragWert(art: String, betrag: String, block: Long, notiz: String): String
    val netzBlaettern: String

    /** [weiter] ist der Zeiger auf die naechste Seite, null = Ende. */
    fun netzBlaetternWert(eintraege: Int, weiter: String?): String
    val netzKeineVerbindung: String
    val netzAntwortDesServers: String
    val netzUnbekannt: String

    // Bericht 4: die Biometrie.
    val bioSensorVerfuegbar: String
    val bioStarkeBiometrie: String
    val bioSchluesselAnlegen: String
    val bioSchluesselLaden: String

    /** Titel des Systemdialogs bei der ersten Bestaetigung. */
    val bioFrageEinschalten: String

    /** Titel des Systemdialogs bei der zweiten Bestaetigung. */
    val bioFrageOeffnen: String

    /** "Bestätigung 1" - die Zeile, wenn die Bestaetigung ausblieb. */
    fun bioBestaetigung(nummer: Int): String
    val bioBestaetigungEinschalten: String
    val bioBestaetigungOeffnen: String
    val bioAbgebrochen: String
    val bioBestaetigt: String
    val bioSchluesselUmschlossen: String
    val bioEingeschaltet: String
    val bioInAkteVermerkt: String
    val bioIvInAkte: String
    val bioFehlt: String
    val bioOeffnenOhnePin: String
    val bioWoerterStimmen: String
    val bioZwoelfWoerterZurueck: String
    val bioNein: String
    val bioEntfernt: String
}

object SelbsttestDe : SelbsttestTexte {
    override val titel = "Selbsttest"
    override val karteKern = "1 - Kryptokern"
    override val karteTresor = "2 - Tresor mit Keystore-Pfeffer"
    override val karteNetz = "3 - Netz, live gegen den Spiegel"
    override val karteBiometrie = "4 - Biometrie"
    override val biometrieHinweis =
        "Zwei Bestätigungen am Sensor: einmal zum Einschalten, " +
            "einmal zum Öffnen ohne PIN."
    override val biometriePruefen = "Biometrie prüfen"
    override val wartetAufSensor = "wartet auf den Sensor …"
    override val fussnote =
        "Abschnitte 1 und 2 rechnen mit dem öffentlichen BIP39-Testvektor, nicht mit " +
            "deiner Wallet - dahinter liegt kein Guthaben. Abschnitt 3 liest das Konto " +
            "deiner Wallet. Der Testtresor wird nach jedem Lauf gelöscht."

    override val rechnet = "rechnet …"
    override fun bestanden(ms: Long) = "bestanden ($ms ms)"
    override fun fehlgeschlagen(ms: Long) = "FEHLGESCHLAGEN ($ms ms)"
    override val ok = "OK"
    override fun fehlerZeile(name: String) = "FEHLER  -  $name"

    override val ausnahme = "Ausnahme"
    override val aufgeraeumt = "Aufgeräumt"

    override val kernAdresse = "Adresse des Testvektors"
    override val kernOeffentlicherSchluessel = "öffentlicher Schlüssel"
    override val kernLeitungsform = "Leitungsform"
    override val kernSignaturGilt = "Signatur der Vorlage gilt"
    override val kernGeprueft = "geprüft"
    override val kernNeuUnterschrieben = "neu unterschrieben"

    override val tresorEinrichten = "Einrichten"
    override val tresorDauerEinrichten = "Dauer Einrichten"
    override val tresorAdresseOhnePin = "Testadresse ohne PIN sichtbar"
    override val tresorWoerterNichtInDatei = "Wörter stehen nicht in der Datei"
    override fun tresorZeichenGeheimtext(zeichen: Int) = "$zeichen Zeichen Geheimtext"
    override val tresorRunden = "Runden in der Akte"
    override val tresorOeffnenRichtigePin = "öffnen mit richtiger PIN"
    override val tresorDauerOeffnen = "Dauer öffnen"
    override val tresorFalschePinAbgewiesen = "falsche PIN wird abgewiesen"
    override val tresorEntfernt = "Testtresor entfernt"

    override fun standOffen(adresse: String) = "offen, $adresse"
    override fun standFalschePin(versuch: Int) = "falsche PIN ($versuch. Versuch)"
    override fun standGesperrt(sekunden: Long) = "gesperrt für $sekunden s"
    override val standKeinTresor = "kein Tresor"
    override val standBiometrieNeu = "Biometrie neu einrichten"
    override fun standFehler(grund: String) = "Fehler: $grund"

    override val netzHoehe = "Höhe der Kette"
    override fun netzHoeheWert(hoehe: Long?, mempool: Int) = "Block $hoehe, Mempool $mempool"
    override val netzGebuehrenlage = "Gebührenlage"
    override fun netzGebuehrenlageWert(mindestens: String, fassung: Int?, basisBytes: Int?) =
        "mindestens $mindestens, Fassung $fassung, $basisBytes Byte"
    override val netzBasisgroesse = "Basisgröße stimmt mit dem Port"
    override fun netzBasisgroesseWert(basisBytes: Int?, port: Int) =
        "$basisBytes Byte, Port sagt $port"
    override val netzSendefassung = "Sendefassung"
    override fun netzSendefassungWert(fassung: Int, pause: Boolean, darfSenden: Boolean) =
        "Fassung $fassung" +
            (if (pause) ", SENDEPAUSE" else "") +
            (if (darfSenden) ", senden erlaubt" else ", senden gesperrt")
    override val netzGebuehr = "Gebühr für eine Überweisung"
    override fun netzGebuehrWert(bytes: Int, gebuehr: String) = "$bytes Byte kosten $gebuehr"
    override val netzKnotenErkannt = "Full Node erkannt"
    override val netzKontoGelesen = "Konto dieser Wallet gelesen"
    override val netzOhneWallet = "keine Wallet auf diesem Gerät - Konto nicht geprüft"
    override val netzGuthaben = "Guthaben"
    override val netzNonce = "Nonce"
    override fun netzNonceWert(nonce: Long, naechste: Long) = "$nonce, nächste $naechste"
    override val netzVerlauf = "Verlauf"
    override fun netzVerlaufWert(eintraege: Int, wartend: Int, quelle: String) =
        "$eintraege Einträge, $wartend wartend ($quelle)"
    override val netzJuengsterEintrag = "jüngster Eintrag"
    override fun netzJuengsterEintragWert(art: String, betrag: String, block: Long, notiz: String) =
        "$art $betrag bei Block $block" +
            (if (notiz.isNotEmpty()) " - \"$notiz\"" else "")
    override val netzBlaettern = "Verlauf blättern"
    override fun netzBlaetternWert(eintraege: Int, weiter: String?) =
        "$eintraege Einträge, weiter bei ${weiter ?: "Ende"}"
    override val netzKeineVerbindung = "keine Verbindung"
    override val netzAntwortDesServers = "Antwort des Servers"
    override val netzUnbekannt = "unbekannt"

    override val bioSensorVerfuegbar = "Sensor verfügbar"
    override val bioStarkeBiometrie = "starke Biometrie"
    override val bioSchluesselAnlegen = "Schlüssel anlegen"
    override val bioSchluesselLaden = "Schlüssel laden"
    override val bioFrageEinschalten = "Biometrie einschalten"
    override val bioFrageOeffnen = "Tresor mit Biometrie öffnen"
    override fun bioBestaetigung(nummer: Int) = "Bestätigung $nummer"
    override val bioBestaetigungEinschalten = "Bestätigung 1 (einschalten)"
    override val bioBestaetigungOeffnen = "Bestätigung 2 (öffnen)"
    override val bioAbgebrochen = "abgebrochen"
    override val bioBestaetigt = "bestätigt"
    override val bioSchluesselUmschlossen = "Tresorschlüssel umschlossen"
    override val bioEingeschaltet = "Biometrie ist eingeschaltet"
    override val bioInAkteVermerkt = "in der Akte vermerkt"
    override val bioIvInAkte = "IV in der Akte"
    override val bioFehlt = "fehlt"
    override val bioOeffnenOhnePin = "öffnen ohne PIN"
    override val bioWoerterStimmen = "Wörter stimmen"
    override val bioZwoelfWoerterZurueck = "zwölf Wörter zurück"
    override val bioNein = "NEIN"
    override val bioEntfernt = "Testtresor und Schlüssel entfernt"
}

object SelbsttestEn : SelbsttestTexte {
    override val titel = "Self-test"
    override val karteKern = "1 - Crypto core"
    override val karteTresor = "2 - Vault with keystore pepper"
    override val karteNetz = "3 - Network, live against the mirror"
    override val karteBiometrie = "4 - Biometrics"
    override val biometrieHinweis =
        "Two confirmations at the sensor: once to switch it on, " +
            "once to open without the PIN."
    override val biometriePruefen = "Check biometrics"
    override val wartetAufSensor = "waiting for the sensor …"
    override val fussnote =
        "Sections 1 and 2 use the public BIP39 test vector, not your wallet - " +
            "there is no balance behind it. Section 3 reads your wallet's account. " +
            "The test vault is deleted after every run."

    override val rechnet = "working …"
    override fun bestanden(ms: Long) = "passed ($ms ms)"
    override fun fehlgeschlagen(ms: Long) = "FAILED ($ms ms)"
    override val ok = "OK"
    override fun fehlerZeile(name: String) = "ERROR  -  $name"

    override val ausnahme = "Exception"
    override val aufgeraeumt = "Cleaned up"

    override val kernAdresse = "Test vector address"
    override val kernOeffentlicherSchluessel = "Public key"
    override val kernLeitungsform = "Wire format"
    override val kernSignaturGilt = "Signature of the sample is valid"
    override val kernGeprueft = "checked"
    override val kernNeuUnterschrieben = "Signed again"

    override val tresorEinrichten = "Set up"
    override val tresorDauerEinrichten = "Time to set up"
    override val tresorAdresseOhnePin = "Test address visible without PIN"
    override val tresorWoerterNichtInDatei = "Words are not in the file"
    override fun tresorZeichenGeheimtext(zeichen: Int) = "$zeichen characters of ciphertext"
    override val tresorRunden = "Rounds in the vault file"
    override val tresorOeffnenRichtigePin = "Open with the correct PIN"
    override val tresorDauerOeffnen = "Time to open"
    override val tresorFalschePinAbgewiesen = "Wrong PIN is rejected"
    override val tresorEntfernt = "test vault removed"

    override fun standOffen(adresse: String) = "open, $adresse"
    override fun standFalschePin(versuch: Int) = "wrong PIN (attempt $versuch)"
    override fun standGesperrt(sekunden: Long) = "locked for $sekunden s"
    override val standKeinTresor = "no vault"
    override val standBiometrieNeu = "set up biometrics again"
    override fun standFehler(grund: String) = "Error: $grund"

    override val netzHoehe = "Chain height"
    override fun netzHoeheWert(hoehe: Long?, mempool: Int) = "block $hoehe, mempool $mempool"
    override val netzGebuehrenlage = "Fee conditions"
    override fun netzGebuehrenlageWert(mindestens: String, fassung: Int?, basisBytes: Int?) =
        "at least $mindestens, version $fassung, ${byte(basisBytes)}"
    override val netzBasisgroesse = "Base size matches the app's calculation"
    override fun netzBasisgroesseWert(basisBytes: Int?, port: Int) =
        "${byte(basisBytes)}, the app calculates $port"
    override val netzSendefassung = "Version for sending"
    override fun netzSendefassungWert(fassung: Int, pause: Boolean, darfSenden: Boolean) =
        "version $fassung" +
            (if (pause) ", SENDING PAUSED" else "") +
            (if (darfSenden) ", sending allowed" else ", sending blocked")
    override val netzGebuehr = "Fee for a transfer"
    override fun netzGebuehrWert(bytes: Int, gebuehr: String) =
        if (bytes == 1) "1 byte costs $gebuehr" else "$bytes bytes cost $gebuehr"
    override val netzKnotenErkannt = "Full node detected"
    override val netzKontoGelesen = "This wallet's account read"
    override val netzOhneWallet = "no wallet on this device - account not checked"
    override val netzGuthaben = "Balance"
    override val netzNonce = "Nonce"
    override fun netzNonceWert(nonce: Long, naechste: Long) = "$nonce, next $naechste"
    override val netzVerlauf = "History"
    override fun netzVerlaufWert(eintraege: Int, wartend: Int, quelle: String) =
        "${eintrag(eintraege)}, $wartend pending ($quelle)"
    override val netzJuengsterEintrag = "Latest entry"
    override fun netzJuengsterEintragWert(art: String, betrag: String, block: Long, notiz: String) =
        "$art $betrag at block $block" +
            (if (notiz.isNotEmpty()) " - \"$notiz\"" else "")
    override val netzBlaettern = "Paging through the history"
    override fun netzBlaetternWert(eintraege: Int, weiter: String?) =
        "${eintrag(eintraege)}, " + (if (weiter != null) "continues at $weiter" else "end")
    override val netzKeineVerbindung = "No connection"
    override val netzAntwortDesServers = "Response from the server"
    override val netzUnbekannt = "unknown"

    override val bioSensorVerfuegbar = "Sensor available"
    override val bioStarkeBiometrie = "strong biometrics"
    override val bioSchluesselAnlegen = "Create key"
    override val bioSchluesselLaden = "Load key"
    override val bioFrageEinschalten = "Switch on biometrics"
    override val bioFrageOeffnen = "Open vault with biometrics"
    override fun bioBestaetigung(nummer: Int) = "Confirmation $nummer"
    override val bioBestaetigungEinschalten = "Confirmation 1 (switch on)"
    override val bioBestaetigungOeffnen = "Confirmation 2 (open)"
    override val bioAbgebrochen = "cancelled"
    override val bioBestaetigt = "confirmed"
    override val bioSchluesselUmschlossen = "Vault key wrapped"
    override val bioEingeschaltet = "Biometrics are switched on"
    override val bioInAkteVermerkt = "recorded in the vault file"
    override val bioIvInAkte = "IV in the vault file"
    override val bioFehlt = "missing"
    override val bioOeffnenOhnePin = "Open without PIN"
    override val bioWoerterStimmen = "Words match"
    override val bioZwoelfWoerterZurueck = "twelve words returned"
    override val bioNein = "NO"
    override val bioEntfernt = "test vault and key removed"

    private fun byte(anzahl: Int?) = if (anzahl == 1) "1 byte" else "$anzahl bytes"

    private fun eintrag(anzahl: Int) = if (anzahl == 1) "1 entry" else "$anzahl entries"
}
