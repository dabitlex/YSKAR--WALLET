package com.yskar.wallet.sprache

/** Texte: Hauptbildschirm, Guthabenverlauf, Verlauf, Bewegung, Empfangen. */
interface WalletTexte {
    // --- Gemeinsam: Art, Zeit und Notiz einer Bewegung -------------------
    val artBlockertrag: String
    val artPoolAnteil: String
    val artEmpfangen: String
    val artGesendet: String
    val artUnbekannt: String

    /** "Heute, 18:17" - [uhrzeit] ist schon fertig geschrieben. */
    fun zeitHeute(uhrzeit: String): String
    fun zeitGestern(uhrzeit: String): String

    /** Die Notiz in den Anfuehrungszeichen der Sprache. */
    fun notizZitat(notiz: String): String

    // --- Hauptbildschirm -------------------------------------------------
    val guthabenZeigen: String
    val guthabenVerbergen: String
    val sperren: String
    val guthaben: String
    val aktualisieren: String
    val laedt: String
    fun zahlungWartet(anzahl: Int): String
    val knopfSenden: String
    val knopfEmpfangen: String
    val knopfVerlauf: String
    val knopfEinstellungen: String
    val zuletzt: String
    val alleAnzeigen: String
    val keineDaten: String
    val keineDatenRat: String
    val nichtsPassiert: String
    val nichtsPassiertRat: String

    // --- Guthabenverlauf (Karte mit der Linie) ---------------------------
    val zeitraum7Tage: String
    val zeitraum30Tage: String
    val zeitraum1Jahr: String
    val zeitraumKurz7Tage: String
    val zeitraumKurz30Tage: String
    val zeitraumKurz1Jahr: String

    /** "Guthaben · 30 Tage" - [zeitraum] ist eines der drei Woerter oben. */
    fun kurventitelZeitraum(zeitraum: String): String
    val kurventitelNichtBelegt: String

    /** "Guthaben · belegt ab 04.11." - [ab] ist Uhrzeit oder Datum, fertig geschrieben. */
    fun kurventitelBelegtAb(ab: String): String
    val kurveVerborgen: String
    val kurveUnstimmig: String

    // Die Linie in einem Satz, fuer den Bildschirmleser. [von] und [auf]
    // sind Betraege, fertig geschrieben und ohne Einheit.
    val kurveVorgelesenUnstimmig: String
    fun kurveVorgelesenZeitraum(zeitraum: String, von: String, auf: String): String
    val kurveVorgelesenNichtBelegt: String
    fun kurveVorgelesenSeit(seit: String, von: String, auf: String): String
    val summeEingang: String
    val summeAusgang: String

    // --- Verlauf ---------------------------------------------------------
    val verlaufTitel: String
    val mehrLaden: String
    val verlaufAnfang: String

    // --- Empfangen -------------------------------------------------------
    val empfangenTitel: String
    val deineAdresse: String
    val kopieren: String
    val teilen: String
    val adresseOeffentlich: String

    /** Titel des Teilen-Blatts von Android. */
    val adresseTeilen: String

    // --- Bewegung --------------------------------------------------------
    val bewegungTitel: String
    val vonDieserAdresse: String
    val anDieseAdresse: String
    val notizInDerKette: String
    val zeileBetrag: String
    val zeileGebuehrDesAbsenders: String
    val zeileGebuehr: String
    val zeileVomKontoAbgegangen: String
    val zeileAnteile: String
    val zeileBlock: String
    val zeilePlatzImBlock: String
    val zeileBestaetigungen: String
    val hoeheUnbekannt: String
    val bestaetigungBlockSelbst: String
    val kennungDerUeberweisung: String
    val kennungKopieren: String
    val kennungNachschlagen: String
    // --- Die einzelne Zahlung, einfach
    val zeileWann: String
    val zeileStand: String
    val standBestaetigt: String
    val standWirdBestaetigt: String
}

object WalletDe : WalletTexte {
    override val artBlockertrag = "Mining-Ertrag"
    override val artPoolAnteil = "Mining-Anteil (Pool)"
    override val artEmpfangen = "Empfangen"
    override val artGesendet = "Gesendet"
    override val artUnbekannt = "Unbekannt"
    override fun zeitHeute(uhrzeit: String) = "Heute, $uhrzeit"
    override fun zeitGestern(uhrzeit: String) = "Gestern, $uhrzeit"
    override fun notizZitat(notiz: String) = "„$notiz“"

    override val guthabenZeigen = "Guthaben zeigen"
    override val guthabenVerbergen = "Guthaben verbergen"
    override val sperren = "Sperren"
    override val guthaben = "Guthaben"
    override val aktualisieren = "Aktualisieren"
    override val laedt = "lädt …"
    override fun zahlungWartet(anzahl: Int) =
        if (anzahl == 1) "1 Zahlung ist unterwegs"
        else "$anzahl Zahlungen sind unterwegs"
    override val knopfSenden = "Senden"
    override val knopfEmpfangen = "Empfangen"
    override val knopfVerlauf = "Verlauf"
    override val knopfEinstellungen = "Einstellungen"
    override val zuletzt = "Zuletzt"
    override val alleAnzeigen = "Alle anzeigen"
    override val keineDaten = "Noch keine Daten vom Netz."
    override val keineDatenRat = "Tippe oben auf die zwei Pfeile."
    override val nichtsPassiert = "Hier ist noch nichts passiert."
    override val nichtsPassiertRat = "Sobald etwas eingeht, steht es hier."

    override val zeitraum7Tage = "7 Tage"
    override val zeitraum30Tage = "30 Tage"
    override val zeitraum1Jahr = "1 Jahr"
    override val zeitraumKurz7Tage = "7 T"
    override val zeitraumKurz30Tage = "30 T"
    override val zeitraumKurz1Jahr = "1 J"
    override fun kurventitelZeitraum(zeitraum: String) = "Guthaben · $zeitraum"
    override val kurventitelNichtBelegt = "Guthaben · noch keine Daten"
    override fun kurventitelBelegtAb(ab: String) = "Guthaben · seit $ab"
    override val kurveVerborgen = "Guthabenverlauf verborgen"
    override val kurveUnstimmig =
        "Der geladene Verlauf erklärt das Guthaben nicht. " +
            "Darum steht hier keine Linie."
    override val kurveVorgelesenUnstimmig =
        "Guthabenverlauf: Der geladene Verlauf erklärt das Guthaben nicht."
    override fun kurveVorgelesenZeitraum(zeitraum: String, von: String, auf: String) =
        "Guthaben über $zeitraum: von $von auf $auf YSR"
    override val kurveVorgelesenNichtBelegt = "Guthabenverlauf: noch nicht belegt"
    override fun kurveVorgelesenSeit(seit: String, von: String, auf: String) =
        "Guthaben seit $seit: von $von auf $auf YSR"
    override val summeEingang = "Eingang"
    override val summeAusgang = "Ausgang"

    override val verlaufTitel = "Verlauf"
    override val mehrLaden = "Mehr laden"
    override val verlaufAnfang = "Das ist der Anfang."

    override val empfangenTitel = "Empfangen"
    override val deineAdresse = "Deine YSKAR-Adresse"
    override val kopieren = "Kopieren"
    override val teilen = "Teilen"
    override val adresseOeffentlich =
        "Diese Adresse darf jeder sehen. Sie zu kennen genügt nicht, " +
            "um etwas abzuheben."
    override val adresseTeilen = "Adresse teilen"

    override val bewegungTitel = "Zahlung"
    override val vonDieserAdresse = "Von dieser Adresse"
    override val anDieseAdresse = "An diese Adresse"
    override val notizInDerKette = "Notiz · öffentlich, für immer"
    override val zeileBetrag = "Betrag"
    override val zeileGebuehrDesAbsenders = "Netzgebühr des Absenders"
    override val zeileGebuehr = "Netzgebühr"
    override val zeileVomKontoAbgegangen = "Vom Konto abgegangen"
    override val zeileAnteile = "Anteile"
    override val zeileBlock = "Block"
    override val zeilePlatzImBlock = "Platz im Block"
    override val zeileBestaetigungen = "Bestätigungen"
    override val hoeheUnbekannt = "Höhe des Netzes unbekannt"
    override val bestaetigungBlockSelbst = "1 (der Block selbst)"
    override val kennungDerUeberweisung = "Kennung der Überweisung"
    override val kennungKopieren = "Kennung kopieren"
    override val kennungNachschlagen = "Mit der Kennung findest du die Zahlung im Explorer."
    override val zeileWann = "Wann"
    override val zeileStand = "Stand"
    override val standBestaetigt = "Bestätigt"
    override val standWirdBestaetigt = "Wird bestätigt"
}

object WalletEn : WalletTexte {
    override val artBlockertrag = "Mining reward"
    override val artPoolAnteil = "Mining share (pool)"
    override val artEmpfangen = "Received"
    override val artGesendet = "Sent"
    override val artUnbekannt = "Unknown"
    override fun zeitHeute(uhrzeit: String) = "Today, $uhrzeit"
    override fun zeitGestern(uhrzeit: String) = "Yesterday, $uhrzeit"
    override fun notizZitat(notiz: String) = "“$notiz”"

    override val guthabenZeigen = "Show balance"
    override val guthabenVerbergen = "Hide balance"
    override val sperren = "Lock"
    override val guthaben = "Balance"
    override val aktualisieren = "Refresh"
    override val laedt = "loading …"
    override fun zahlungWartet(anzahl: Int) =
        if (anzahl == 1) "1 payment is on its way"
        else "$anzahl payments are on their way"
    override val knopfSenden = "Send"
    override val knopfEmpfangen = "Receive"
    override val knopfVerlauf = "History"
    override val knopfEinstellungen = "Settings"
    override val zuletzt = "Recent"
    override val alleAnzeigen = "Show all"
    override val keineDaten = "No data from the network yet."
    override val keineDatenRat = "Tap the two arrows above."
    override val nichtsPassiert = "Nothing has happened here yet."
    override val nichtsPassiertRat = "As soon as something comes in, it will appear here."

    override val zeitraum7Tage = "7 days"
    override val zeitraum30Tage = "30 days"
    override val zeitraum1Jahr = "1 year"
    override val zeitraumKurz7Tage = "7D"
    override val zeitraumKurz30Tage = "30D"
    override val zeitraumKurz1Jahr = "1Y"
    override fun kurventitelZeitraum(zeitraum: String) = "Balance · $zeitraum"
    override val kurventitelNichtBelegt = "Balance · no data yet"
    override fun kurventitelBelegtAb(ab: String) = "Balance · since $ab"
    override val kurveVerborgen = "Balance history hidden"
    override val kurveUnstimmig =
        "The loaded history does not account for the balance. " +
            "That is why no line is shown here."
    override val kurveVorgelesenUnstimmig =
        "Balance history: The loaded history does not account for the balance."
    override fun kurveVorgelesenZeitraum(zeitraum: String, von: String, auf: String) =
        "Balance over $zeitraum: from $von to $auf YSR"
    override val kurveVorgelesenNichtBelegt = "Balance history: not yet documented"
    override fun kurveVorgelesenSeit(seit: String, von: String, auf: String) =
        "Balance since $seit: from $von to $auf YSR"
    override val summeEingang = "Incoming"
    override val summeAusgang = "Outgoing"

    override val verlaufTitel = "History"
    override val mehrLaden = "Load more"
    override val verlaufAnfang = "This is the beginning."

    override val empfangenTitel = "Receive"
    override val deineAdresse = "Your YSKAR address"
    override val kopieren = "Copy"
    override val teilen = "Share"
    override val adresseOeffentlich =
        "Anyone may see this address. Knowing it is not enough " +
            "to withdraw anything."
    override val adresseTeilen = "Share address"

    override val bewegungTitel = "Payment"
    override val vonDieserAdresse = "From this address"
    override val anDieseAdresse = "To this address"
    override val notizInDerKette = "Note · public, forever"
    override val zeileBetrag = "Amount"
    override val zeileGebuehrDesAbsenders = "Sender's network fee"
    override val zeileGebuehr = "Network fee"
    override val zeileVomKontoAbgegangen = "Debited from the account"
    override val zeileAnteile = "Shares"
    override val zeileBlock = "Block"
    override val zeilePlatzImBlock = "Position in block"
    override val zeileBestaetigungen = "Confirmations"
    override val hoeheUnbekannt = "Network height unknown"
    override val bestaetigungBlockSelbst = "1 (the block itself)"
    override val kennungDerUeberweisung = "Transaction ID"
    override val kennungKopieren = "Copy transaction ID"
    override val kennungNachschlagen = "Use the ID to find the payment in the explorer."
    override val zeileWann = "When"
    override val zeileStand = "Status"
    override val standBestaetigt = "Confirmed"
    override val standWirdBestaetigt = "Being confirmed"
}
