package com.yskar.wallet.sprache

/**
 * Texte: Meldungen, die das Modell (ui/WalletModel.kt) erzeugt, und die
 * Titel der Fingerabdruck-Abfrage des Systems (MainActivity).
 */
interface ModellTexte {
    // Einrichten
    fun tresordateiUnlesbar(grund: String): String
    val stimmtNochNicht: String
    fun woerterAnzahl(anzahl: Int): String
    val pruefsummeFalsch: String
    val walletAngelegt: String
    val walletWiederhergestellt: String

    // PIN und Fingerabdruck
    val pinFalsch: String
    fun pinFalschWarten(sekunden: Long): String
    fun pinFalschVersuch(nummer: Int): String
    fun nochGesperrt(sekunden: Long): String
    val fingerabdrueckeGeaendert: String
    val schluesselNichtAngelegt: String
    val fingerabdruckEingerichtet: String
    val fingerabdruckAbgeschaltet: String
    val fingerabdruckNeuEinrichten: String
    val pinGeaendertBioNeu: String

    // Netz
    val keineVerbindungAlterStand: String
    val keineVerbindung: String
    fun spiegelAntwortetNicht(status: Int): String

    // Wallet
    val keineWalletHier: String
    val walletEntfernt: String

    /** Was der Tresor als Grund nennt - [grund] kommt aus `tresor/`, auf Deutsch und ohne Umlaute. */
    fun tresorFehler(grund: String): String

    // Benachrichtigungen
    val waechterAn: String
    val waechterAus: String
    val ohneErlaubnis: String

    // Titel der Fingerabdruck-Abfrage
    val bioEntsperren: String
    val bioUnterschreiben: String
    val bioWoerter: String
    val bioEinrichten: String
}

/**
 * Die Gruende, die `tresor/Tresor.kt` nennt, wortgleich - und was daraus
 * angezeigt wird. Der Tresor bleibt, wie er ist; er schreibt ohne Umlaute.
 * Was hier nicht steht (Namen von Ausnahmen aus dem Keystore), wird
 * unveraendert angehaengt.
 */
private val TRESORGRUENDE: Map<String, Pair<String, String>> = mapOf(
    "Es gibt bereits einen Tresor auf diesem Geraet" to
        ("Auf diesem Gerät liegt bereits eine Wallet." to "There is already a wallet on this device."),
    "Merkwoerter ungueltig - Pruefsumme stimmt nicht" to
        ("Die Wörter sind ungültig - die Prüfsumme stimmt nicht." to
            "The words are invalid - the checksum does not match."),
    "PIN zu kurz" to ("Die PIN ist zu kurz." to "The PIN is too short."),
    "Cipher liefert keinen IV" to
        ("Der Schlüssel des Fingerabdrucks ließ sich nicht verwenden." to
            "The fingerprint key could not be used."),
    "Tresorschluessel passt nicht zum Tresor" to
        ("Der Fingerabdruck passt nicht mehr zu dieser Wallet. Entsperre mit der PIN." to
            "The fingerprint no longer matches this wallet. Unlock with your PIN."),
)

object ModellDe : ModellTexte {
    override fun tresordateiUnlesbar(grund: String) = "Tresordatei unlesbar: $grund"
    override val stimmtNochNicht = "Das stimmt noch nicht. Sieh noch einmal nach."
    override fun woerterAnzahl(anzahl: Int) =
        if (anzahl == 1) "Es ist 1 Wort - erwartet werden 12."
        else "Es sind $anzahl Wörter - erwartet werden 12."
    override val pruefsummeFalsch =
        "Die Wörter passen nicht zusammen. Eines ist falsch oder " +
            "vertauscht."
    override val walletAngelegt = "Wallet angelegt. Bewahre die zwölf Wörter sicher auf."
    override val walletWiederhergestellt = "Wallet wiederhergestellt."

    override val pinFalsch = "PIN falsch."
    override fun pinFalschWarten(sekunden: Long) = "PIN falsch. Nächster Versuch in $sekunden Sekunden."
    override fun pinFalschVersuch(nummer: Int) = "PIN falsch. $nummer. Versuch."
    override fun nochGesperrt(sekunden: Long) = "Noch $sekunden Sekunden gesperrt."
    override val fingerabdrueckeGeaendert =
        "Die Fingerabdrücke haben sich geändert. " +
            "Entsperre einmal mit der PIN; dann lässt sich " +
            "der Fingerabdruck neu einrichten."
    override val schluesselNichtAngelegt = "Der Schlüssel ließ sich nicht anlegen."
    override val fingerabdruckEingerichtet = "Fingerabdruck eingerichtet."
    override val fingerabdruckAbgeschaltet = "Fingerabdruck abgeschaltet."
    override val fingerabdruckNeuEinrichten = "Der Fingerabdruck muss neu eingerichtet werden."
    override val pinGeaendertBioNeu = "PIN geändert. Der Fingerabdruck muss neu eingerichtet werden."

    override val keineVerbindungAlterStand =
        "Keine Verbindung. Angezeigt wird der letzte bekannte Stand."
    override val keineVerbindung = "Keine Verbindung."
    override fun spiegelAntwortetNicht(status: Int) =
        "Das Netz antwortet nicht wie erwartet ($status)."

    override val keineWalletHier = "Auf diesem Gerät liegt keine Wallet."
    override val walletEntfernt = "Die Wallet wurde von diesem Gerät entfernt."

    override fun tresorFehler(grund: String) =
        TRESORGRUENDE[grund]?.first ?: "Der Tresor meldet einen Fehler: $grund"

    override val waechterAn = "Die Wallet sieht ab jetzt alle fünfzehn Minuten nach."
    override val waechterAus = "Im Hintergrund wird nicht mehr nachgesehen."
    override val ohneErlaubnis =
        "Ohne die Erlaubnis für Mitteilungen bleibt das aus. " +
            "Du kannst sie in den Android-Einstellungen nachreichen."

    override val bioEntsperren = "Wallet entsperren"
    override val bioUnterschreiben = "Überweisung bestätigen"
    override val bioWoerter = "Zwölf Wörter anzeigen"
    override val bioEinrichten = "Fingerabdruck einrichten"
}

object ModellEn : ModellTexte {
    override fun tresordateiUnlesbar(grund: String) = "Vault file unreadable: $grund"
    override val stimmtNochNicht = "That is not right yet. Check again."
    override fun woerterAnzahl(anzahl: Int) =
        if (anzahl == 1) "That is 1 word - 12 are expected."
        else "That is $anzahl words - 12 are expected."
    override val pruefsummeFalsch =
        "The words do not fit together. One of them is wrong or out " +
            "of order."
    override val walletAngelegt = "Wallet created. Keep the twelve words safe."
    override val walletWiederhergestellt = "Wallet restored."

    override val pinFalsch = "Wrong PIN."
    override fun pinFalschWarten(sekunden: Long) = "Wrong PIN. Next attempt in $sekunden seconds."
    override fun pinFalschVersuch(nummer: Int) = "Wrong PIN. Attempt $nummer."
    override fun nochGesperrt(sekunden: Long) = "Locked for another $sekunden seconds."
    override val fingerabdrueckeGeaendert =
        "The fingerprints on this device have changed. " +
            "Unlock once with your PIN; then the fingerprint " +
            "can be set up again."
    override val schluesselNichtAngelegt = "The key could not be created."
    override val fingerabdruckEingerichtet = "Fingerprint set up."
    override val fingerabdruckAbgeschaltet = "Fingerprint turned off."
    override val fingerabdruckNeuEinrichten = "The fingerprint has to be set up again."
    override val pinGeaendertBioNeu = "PIN changed. The fingerprint has to be set up again."

    override val keineVerbindungAlterStand = "No connection. Showing the last known balance."
    override val keineVerbindung = "No connection."
    override fun spiegelAntwortetNicht(status: Int) =
        "The network did not respond as expected ($status)."

    override val keineWalletHier = "There is no wallet on this device."
    override val walletEntfernt = "The wallet has been removed from this device."

    override fun tresorFehler(grund: String) =
        TRESORGRUENDE[grund]?.second ?: "The vault reports an error: $grund"

    override val waechterAn = "From now on the wallet checks every fifteen minutes."
    override val waechterAus = "The wallet no longer checks in the background."
    override val ohneErlaubnis =
        "Without permission for notifications this stays off. " +
            "You can grant it later in the Android settings."

    override val bioEntsperren = "Unlock wallet"
    override val bioUnterschreiben = "Confirm transfer"
    override val bioWoerter = "Show the twelve words"
    override val bioEinrichten = "Set up fingerprint"
}
