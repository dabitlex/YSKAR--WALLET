package com.yskar.wallet.sprache

/**
 * Texte: die Sendebildschirme und der Scanner.
 *
 * Die deutschen Texte stehen hier WOERTLICH so, wie sie vorher in
 * ui/SendenScreens.kt und ui/Scanner.kt standen - die Geraetetests suchen
 * ihre Knoepfe an diesen Woertern (etwa [notizHinzufuegen],
 * [weiterZumPruefen] und [notizEntfernen]).
 *
 * ZWEI DINGE, DIE BEIM UEBERSETZEN NICHT VERRUTSCHEN DUERFEN:
 *  - "angenommen" ist "accepted", nie "done" oder "final". Der Knoten hat
 *    genickt, mehr nicht - das ist Schritt 2 von fuenf (REGELN.md,
 *    Regel 20). Darum heisst auch der Knopf "Fertig" auf Englisch "Close":
 *    "done" bleibt dem Wort "erledigt" neben den Schritten vorbehalten.
 *  - Die englischen Texte der vier Sendebildschirme sind nicht laenger als
 *    die deutschen, wo es eng ist. Kein Sendebildschirm darf rollen muessen
 *    (REGELN.md, Regel 29).
 *
 * Die Meldungen aus `netz/Senden.kt` stehen NICHT hier, sondern in
 * [Sendemeldungen].
 */
interface SendenTexte {
    // --- Auf mehreren Sendebildschirmen
    val betrag: String
    val gebuehr: String
    val gesamt: String
    val notiz: String

    /** Titel des Ergebnisses und Ueberschrift, solange der Stand noch fehlt. */
    val eingereicht: String

    // --- Eingabe
    val eingabeTitel: String
    val weiterZumPruefen: String

    /** Was ein Bildschirmleser zum Tipp auf die Flaeche um den Betrag sagt. */
    val betragEingeben: String

    /** Mit dem Dezimalzeichen der Sprache: "0,00" und "0.00". */
    val betragPlatzhalter: String
    val verfuegbar: String
    val adresseDesEmpfaengers: String
    val qrCodeScannen: String
    val einfuegen: String
    val notizHinzufuegen: String
    val notizEntfernen: String
    val notizPlatzhalter: String
    val notizFuerImmer: String

    /** Der Zaehler unter der Notiz: "12 / 32 Byte". */
    fun notizBytes(n: Int, max: Int): String

    // --- Pruefschritt
    val pruefenTitel: String
    val nichtRueckgaengig: String
    val unterschreibenUndSenden: String
    val imNaechstenSchritt: String
    val duSendest: String
    val empfaenger: String
    val zeichenVergleichen: String
    val danachVerfuegbar: String
    val notizInDerKette: String

    /** Die Notiz in Anfuehrungszeichen - der Wortlaut bleibt unberuehrt. */
    fun notizWoertlich(text: String): String

    /** Fuss des Belegs: die laufende Nummer (Nonce). */
    fun laufendeNummer(nonce: Long): String

    /** Fuss des Belegs: die Fassung der Ueberweisung. */
    fun fassung(nummer: Int): String

    /** Fuss des Belegs: die Groesse der Ueberweisung. */
    fun bytes(n: Int): String

    // --- Unterschreiben
    val unterschreibenTitel: String
    val an: String
    val wirdUnterschrieben: String
    val pinEingeben: String
    val mitFingerabdruckUnterschreiben: String
    val schluesselNurJetzt: String

    // --- Ergebnis
    val abgelehnt: String
    val nichtGesendet: String

    /** Der Knopf oben rechts. Er schliesst den Bildschirm, mehr sagt er nicht. */
    val fertig: String
    val grundDesKnotens: String
    val einzelheit: String
    val nichtsAbgebucht: String
    val kontostandPruefen: String
    val endgueltigGibtEsNicht: String
    val standJetztAbfragen: String
    val kennungDerUeberweisung: String
    val kennungKopieren: String

    /** "1,50 YSR an ysr1abcd…wxyz" - [betrag] ist schon fertig geschrieben. */
    fun betragAn(betrag: String, adresse: String): String

    // --- Ergebnis: was das Zeichen sagt (Ueberschrift und, was noch aussteht)
    val standImBlock: String
    val standAngenommen: String
    val standMitZweiBestaetigungen: String
    val standBestaetigungenFehlen: String
    val standNochNichtImBlock: String
    val standWirdAbgefragt: String

    /** Was ein Bildschirmleser zum Zeichen sagt. */
    fun standDerZahlung(ueberschrift: String, unterzeile: String): String

    // --- Ergebnis: die drei Schilder unter dem Zeichen
    val teilLinkesBand: String
    val teilRechtesBand: String
    val teilStamm: String
    val teilAngenommen: String
    val teilImBlock: String
    val teilZweimalBestaetigt: String

    // --- Ergebnis: die fuenf Schritte, in der Reihenfolge von `Sendestatus`
    val schrittUnterschrieben: String
    val schrittAngenommen: String
    val schrittWarteliste: String
    val schrittBlock: String
    val schrittBestaetigungen: String

    // --- Ergebnis, einfach: ein Wort, ein Satz, drei Schritte
    val wegAbgeschickt: String
    val wegImNetz: String
    val wegBestaetigt: String
    fun satzUnterwegs(betrag: String): String
    val satzImNetz: String
    val satzBestaetigt: String
    val appSchliessen: String

    // --- Scanner
    val scannerTitel: String
    val scannerOhneKamera: String
    val scannerWartetAufFreigabe: String
    val scannerAnleitung: String
    val scannerZuDenEinstellungen: String
    val scannerNichtsGespeichert: String
    val scannerLichtAn: String
    val scannerLichtAus: String
    val scannerKeineAdresse: String
}

object SendenDe : SendenTexte {
    // --- Auf mehreren Sendebildschirmen
    override val betrag = "Betrag"
    override val gebuehr = "Netzgebühr"
    override val gesamt = "Gesamt"
    override val notiz = "Notiz"
    override val eingereicht = "Eingereicht"

    // --- Eingabe
    override val eingabeTitel = "Senden"
    override val weiterZumPruefen = "Weiter"
    override val betragEingeben = "Betrag eingeben"
    override val betragPlatzhalter = "0,00"
    override val verfuegbar = "Verfügbar"
    override val adresseDesEmpfaengers = "Adresse des Empfängers"
    override val qrCodeScannen = "QR-Code scannen"
    override val einfuegen = "Einfügen"
    override val notizHinzufuegen = "Notiz hinzufügen"
    override val notizEntfernen = "Notiz entfernen"
    override val notizPlatzhalter = "z. B. Danke!"
    override val notizFuerImmer = "Jeder kann sie lesen, und sie bleibt für immer."
    override fun notizBytes(n: Int, max: Int) = "$n / $max Byte"

    // --- Pruefschritt
    override val pruefenTitel = "Prüfen"
    override val nichtRueckgaengig = "Eine Überweisung lässt sich nicht zurückholen."
    override val unterschreibenUndSenden = "Jetzt senden"
    override val imNaechstenSchritt = "Danach fragt die Wallet nach PIN oder Fingerabdruck."
    override val duSendest = "Du sendest"
    override val empfaenger = "An diese Adresse"
    override val zeichenVergleichen = "Zeichen für Zeichen vergleichen"
    override val danachVerfuegbar = "Danach verfügbar"
    override val notizInDerKette = "Notiz · öffentlich, für immer"
    override fun notizWoertlich(text: String) = "„$text“"
    override fun laufendeNummer(nonce: Long) = "Nr. $nonce"
    override fun fassung(nummer: Int) = "Fassung $nummer"
    override fun bytes(n: Int) = "$n Byte"

    // --- Unterschreiben
    override val unterschreibenTitel = "Bestätigen"
    override val an = "An"
    override val wirdUnterschrieben = "wird gesendet …"
    override val pinEingeben = "PIN eingeben"
    override val mitFingerabdruckUnterschreiben = "Mit Fingerabdruck bestätigen"
    override val schluesselNurJetzt = "Deine PIN bestätigt genau diese eine Überweisung."

    // --- Ergebnis
    override val abgelehnt = "Abgelehnt"
    override val nichtGesendet = "Nicht gesendet"
    override val fertig = "Fertig"
    override val grundDesKnotens = "Antwort des Netzes"
    override val einzelheit = "Einzelheit"
    override val nichtsAbgebucht = "Es wurde nichts abgebucht."
    override val kontostandPruefen =
        "Prüfe dein Guthaben, bevor du es noch einmal versuchst. " +
            "Vielleicht ist die Überweisung doch angekommen."
    override val endgueltigGibtEsNicht =
        "„Endgültig“ gibt es nicht: Das Netz kennt keine Checkpoints."
    override val standJetztAbfragen = "Stand jetzt abfragen"
    override val kennungDerUeberweisung = "Kennung der Überweisung"
    override val kennungKopieren = "Kennung kopieren"
    override fun betragAn(betrag: String, adresse: String) = "$betrag YSR an $adresse"

    // --- Ergebnis: was das Zeichen sagt
    override val standImBlock = "In einem Block"
    override val standAngenommen = "Angenommen"
    override val standMitZweiBestaetigungen = "mit zwei Bestätigungen"
    override val standBestaetigungenFehlen = "die Bestätigungen fehlen noch"
    override val standNochNichtImBlock = "noch nicht in einem Block"
    override val standWirdAbgefragt = "der Stand wird noch abgefragt"
    override fun standDerZahlung(ueberschrift: String, unterzeile: String) =
        "Stand der Zahlung: $ueberschrift, $unterzeile"

    // --- Ergebnis: die drei Schilder unter dem Zeichen
    override val teilLinkesBand = "LINKES BAND"
    override val teilRechtesBand = "RECHTES BAND"
    override val teilStamm = "STAMM"
    override val teilAngenommen = "angenommen"
    override val teilImBlock = "im Block"
    override val teilZweimalBestaetigt = "2× bestätigt"

    // --- Ergebnis: die fuenf Schritte
    override val schrittUnterschrieben = "Auf dem Gerät unterschrieben"
    override val schrittAngenommen = "Vom Knoten angenommen"
    override val schrittWarteliste = "In der Warteliste"
    override val schrittBlock = "In einem Block"
    override val schrittBestaetigungen = "Mindestens zwei Bestätigungen"

    // --- Ergebnis, einfach: ein Wort, ein Satz, drei Schritte.
    // REGEL 20: "Bestaetigt" steht erst nach zwei Bestaetigungen da, und
    // keines dieser Woerter heisst "erledigt" oder "endgueltig".
    override val wegAbgeschickt = "Abgeschickt"
    override val wegImNetz = "Im Netz angekommen"
    override val wegBestaetigt = "Bestätigt"
    override fun satzUnterwegs(betrag: String) =
        "$betrag YSR sind unterwegs. Das Netz bestätigt sie meist in wenigen Minuten."
    override val satzImNetz = "Die Überweisung steht in einem Block und wird gerade bestätigt."
    override val satzBestaetigt = "Das Netz hat die Überweisung zweimal bestätigt."
    override val appSchliessen = "Du kannst die App schließen. Die Überweisung läuft weiter."

    // --- Scanner
    override val scannerTitel = "Adresse scannen"
    override val scannerOhneKamera =
        "Ohne Zugriff auf die Kamera kann die App keinen Code lesen. " +
            "Du kannst die Adresse stattdessen einfügen oder tippen."
    override val scannerWartetAufFreigabe = "Warte auf die Freigabe der Kamera …"
    override val scannerAnleitung =
        "Halte den QR-Code in den Rahmen. Die Adresse siehst du " +
            "danach noch einmal."
    override val scannerZuDenEinstellungen = "Zu den App-Einstellungen"
    override val scannerNichtsGespeichert = "Nichts wird gespeichert, nichts verlässt das Gerät."
    override val scannerLichtAn = "Licht an"
    override val scannerLichtAus = "Licht aus"
    override val scannerKeineAdresse = "Das ist keine YSKAR-Adresse."
}

object SendenEn : SendenTexte {
    // --- Auf mehreren Sendebildschirmen
    override val betrag = "Amount"
    override val gebuehr = "Network fee"
    override val gesamt = "Total"
    override val notiz = "Note"
    override val eingereicht = "Submitted"

    // --- Eingabe
    override val eingabeTitel = "Send"
    override val weiterZumPruefen = "Continue"
    override val betragEingeben = "Enter amount"
    override val betragPlatzhalter = "0.00"
    override val verfuegbar = "Available"
    override val adresseDesEmpfaengers = "Recipient's address"
    override val qrCodeScannen = "Scan QR code"
    override val einfuegen = "Paste"
    override val notizHinzufuegen = "Add note"
    override val notizEntfernen = "Remove note"
    override val notizPlatzhalter = "e.g. Thanks!"
    override val notizFuerImmer = "Anyone can read it, and it stays forever."
    override fun notizBytes(n: Int, max: Int) = "$n / $max bytes"

    // --- Pruefschritt
    override val pruefenTitel = "Review"
    override val nichtRueckgaengig = "A transfer cannot be taken back."
    override val unterschreibenUndSenden = "Send now"
    override val imNaechstenSchritt = "Next, the wallet asks for your PIN or fingerprint."
    override val duSendest = "You are sending"
    override val empfaenger = "To this address"
    override val zeichenVergleichen = "Compare every character"
    override val danachVerfuegbar = "Available after"
    override val notizInDerKette = "Note · public, forever"
    override fun notizWoertlich(text: String) = "“$text”"
    override fun laufendeNummer(nonce: Long) = "Nonce $nonce"
    override fun fassung(nummer: Int) = "Version $nummer"
    override fun bytes(n: Int) = if (n == 1) "1 byte" else "$n bytes"

    // --- Unterschreiben
    override val unterschreibenTitel = "Confirm"
    override val an = "To"
    override val wirdUnterschrieben = "sending …"
    override val pinEingeben = "Enter PIN"
    override val mitFingerabdruckUnterschreiben = "Confirm with fingerprint"
    override val schluesselNurJetzt = "Your PIN confirms this one transfer only."

    // --- Ergebnis
    override val abgelehnt = "Rejected"
    override val nichtGesendet = "Not sent"
    override val fertig = "Close"
    override val grundDesKnotens = "Reply from the network"
    override val einzelheit = "Detail"
    override val nichtsAbgebucht = "Nothing was deducted."
    override val kontostandPruefen =
        "Check your balance before you try again. The transfer may " +
            "have gone through after all."
    override val endgueltigGibtEsNicht =
        "There is no “final”: the network has no checkpoints."
    override val standJetztAbfragen = "Check status now"
    override val kennungDerUeberweisung = "Transaction ID"
    override val kennungKopieren = "Copy transaction ID"
    override fun betragAn(betrag: String, adresse: String) = "$betrag YSR to $adresse"

    // --- Ergebnis: was das Zeichen sagt
    override val standImBlock = "In a block"
    override val standAngenommen = "Accepted"
    override val standMitZweiBestaetigungen = "with two confirmations"
    override val standBestaetigungenFehlen = "confirmations still pending"
    override val standNochNichtImBlock = "not yet in a block"
    override val standWirdAbgefragt = "status is still being checked"
    override fun standDerZahlung(ueberschrift: String, unterzeile: String) =
        "Payment status: $ueberschrift, $unterzeile"

    // --- Ergebnis: die drei Schilder unter dem Zeichen
    override val teilLinkesBand = "LEFT BAND"
    override val teilRechtesBand = "RIGHT BAND"
    override val teilStamm = "STEM"
    override val teilAngenommen = "accepted"
    override val teilImBlock = "in a block"
    override val teilZweimalBestaetigt = "2× confirmed"

    // --- Ergebnis: die fuenf Schritte
    override val schrittUnterschrieben = "Signed on the device"
    override val schrittAngenommen = "Accepted by the node"
    override val schrittWarteliste = "In the waiting list"
    override val schrittBlock = "In a block"
    override val schrittBestaetigungen = "At least two confirmations"

    // --- Result, simple: one word, one sentence, three steps.
    override val wegAbgeschickt = "Sent"
    override val wegImNetz = "In the network"
    override val wegBestaetigt = "Confirmed"
    override fun satzUnterwegs(betrag: String) =
        "$betrag YSR are on their way. The network usually confirms them within a few minutes."
    override val satzImNetz = "The transfer is in a block and is being confirmed."
    override val satzBestaetigt = "The network has confirmed the transfer twice."
    override val appSchliessen = "You can close the app. The transfer carries on."

    // --- Scanner
    override val scannerTitel = "Scan address"
    override val scannerOhneKamera =
        "Without access to the camera, the app cannot read a code. " +
            "You can paste or type the address instead."
    override val scannerWartetAufFreigabe = "Waiting for camera permission …"
    override val scannerAnleitung =
        "Hold the QR code inside the frame. You will see the address " +
            "once more afterwards."
    override val scannerZuDenEinstellungen = "Open app settings"
    override val scannerNichtsGespeichert = "Nothing is stored, nothing leaves the device."
    override val scannerLichtAn = "Light on"
    override val scannerLichtAus = "Light off"
    override val scannerKeineAdresse = "This is not a YSKAR address."
}
