package com.yskar.wallet.sprache

/**
 * Texte: die acht Karten der Einfuehrung.
 *
 * Eine Einfuehrung verspricht etwas. Die englische Fassung verspricht
 * dasselbe wie die deutsche - nicht mehr und nicht weniger. Beide prueft
 * `EinfuehrungstexteTest`.
 *
 * Die Karten heissen hier nach dem, was sie zeigen, und stehen in der
 * Reihenfolge, in der `einfuehrungskarten` (ui/Einfuehrungstexte.kt) sie
 * aufreiht. Je Karte: erste Zeile der Ueberschrift, zweite Zeile (im hellen
 * Blau), der Absatz, und was auf dem Bild zu sehen ist - das letzte fuer
 * den Bildschirmleser.
 */
interface EinfuehrungTexte {
    // Kopf und Fuss.
    val ueberspringen: String
    val vorigeKarte: String
    val weiter: String
    val walletOeffnen: String

    /** "3 von 8" - [nummer] zaehlt ab eins. */
    fun zaehler(nummer: Int, von: Int): String

    /** "Karte 3 von 8" - der Fortschritt in Worten, fuer den Bildschirmleser. */
    fun ansage(nummer: Int, von: Int): String

    // Karte 1: die Wallet.
    val walletTitel: String
    val walletTitelBlau: String
    val walletText: String
    val walletBild: String

    // Karte 2: empfangen.
    val empfangenTitel: String
    val empfangenTitelBlau: String
    val empfangenText: String
    val empfangenBild: String

    // Karte 3: senden.
    val sendenTitel: String
    val sendenTitelBlau: String
    val sendenText: String
    val sendenBild: String

    // Karte 4: das Y zeigt den Fortschritt.
    val fortschrittTitel: String
    val fortschrittTitelBlau: String
    val fortschrittText: String
    val fortschrittBild: String

    // Karte 5: Verlauf und Diagramm.
    val ueberblickTitel: String
    val ueberblickTitelBlau: String
    val ueberblickText: String
    val ueberblickBild: String

    // Karte 6: die zwoelf Woerter.
    val woerterTitel: String
    val woerterTitelBlau: String
    val woerterText: String
    val woerterBild: String

    // Karte 7: PIN, Fingerabdruck, Benachrichtigungen.
    val sicherTitel: String
    val sicherTitelBlau: String
    val sicherText: String
    val sicherBild: String

    // Karte 8: Schluss.
    val kontrolleTitel: String
    val kontrolleTitelBlau: String
    val kontrolleText: String
    val kontrolleBild: String

    // Die Marken, die ueber den Bildern schweben.
    val markeEmpfangen: String
    val markeGesendet: String
    val markeAngenommen: String
    val markeWoerterPapier: String
    val markeNiemalsWeitergeben: String
    val markeFingerabdruck: String
    val markePin: String
    val markeSchluesselAufGeraet: String

    // Die kleine Guthabenkarte (Karte 1) und das kleine Diagramm (Karte 5).
    val kaertchenGuthaben: String
    val diagrammTitel: String
    val diagrammSpanne: String

    // Karte 4: die drei Stufen unter dem Y.
    val stufeAngenommen: String
    val stufeImBlock: String
    val stufeZweimalBestaetigt: String

    // Die Beispielzahlen der Vorlage, in der Schreibweise der Sprache: auf
    // Deutsch mit Komma, auf Englisch mit Punkt. `betragText` teilt sie am
    // Dezimalzeichen der Sprache.

    /** Marke auf Karte 2: 12,5 YSR empfangen. */
    val beispielEmpfangen: String

    /** Marke auf Karte 5: 1 YSR empfangen. */
    val beispielEingang: String

    /** Marke auf Karte 5: 25 YSR gesendet. */
    val beispielAusgang: String

    /** Guthabenkarte: 1284,57310946 YSR - der Teil vor dem Dezimalzeichen. */
    val beispielGuthabenGanz: String

    /** Guthabenkarte: das Dezimalzeichen und die Stellen dahinter. */
    val beispielGuthabenBruch: String

    /** Diagramm: was in den 30 Tagen hereinkam. */
    val beispielSummeEingang: String

    /** Diagramm: was in den 30 Tagen hinausging. */
    val beispielSummeAusgang: String
}

object EinfuehrungDe : EinfuehrungTexte {
    override val ueberspringen = "Überspringen"
    override val vorigeKarte = "Vorige Karte"
    override val weiter = "Weiter"
    override val walletOeffnen = "Wallet öffnen"
    override fun zaehler(nummer: Int, von: Int) = "$nummer von $von"
    override fun ansage(nummer: Int, von: Int) = "Karte $nummer von $von"

    override val walletTitel = "Das ist deine"
    override val walletTitelBlau = "Wallet."
    override val walletText =
        "Hier siehst du auf einen Blick, wie viele YSR du hast. Darunter " +
            "sendest und empfängst du, öffnest den Verlauf und die Einstellungen."
    override val walletBild =
        "Blick über die Schulter auf ein Smartphone in der Hand, auf dem " +
            "die YSKAR Wallet geöffnet ist"

    override val empfangenTitel = "YSR"
    override val empfangenTitelBlau = "empfangen."
    override val empfangenText =
        "Zeig deinen QR-Code oder schick deine Adresse. Sie funktioniert " +
            "wie eine Kontonummer und bleibt immer gleich."
    override val empfangenBild =
        "Zwei Menschen an einem runden Tisch, von oben gesehen: Eine Person " +
            "zeigt ihren QR-Code, die andere scannt ihn"

    override val sendenTitel = "YSR"
    override val sendenTitelBlau = "senden."
    override val sendenText =
        "Empfänger und Betrag eingeben, in Ruhe prüfen. Erst mit deiner PIN " +
            "oder deinem Fingerabdruck geht die Zahlung los – ein versehentliches " +
            "Tippen reicht nie."
    override val sendenBild =
        "Eine Hand hält ein Smartphone, der Daumen berührt gleich den " +
            "Bildschirm; gegenüber hält eine zweite Person ihr Telefon"

    override val fortschrittTitel = "Das Y zeigt den"
    override val fortschrittTitelBlau = "Fortschritt."
    override val fortschrittText =
        "Nach dem Senden füllt sich das Y Schritt für Schritt: vom Knoten " +
            "angenommen, in einem Block, zweimal bestätigt. „Endgültig“ nennt die " +
            "Wallet eine Zahlung nie."
    override val fortschrittBild =
        "Das Y in drei Stufen: angenommen, in einem Block, zweimal bestätigt"

    override val ueberblickTitel = "Alles im"
    override val ueberblickTitelBlau = "Blick."
    override val ueberblickText =
        "Jede Zahlung findest du im Verlauf – grün für Eingänge, grau für " +
            "Ausgänge. Das Diagramm zeigt, wie sich dein Guthaben entwickelt hat."
    override val ueberblickBild =
        "Ein Smartphone liegt neben einem aufgeschlagenen Notizbuch und " +
            "einer Kaffeetasse auf dem Tisch"

    override val woerterTitel = "Deine 12"
    override val woerterTitelBlau = "Wörter."
    override val woerterText =
        "Diese zwölf Wörter sind der Schlüssel zu deiner Wallet. Mit ihnen " +
            "holst du sie auf jedes neue Handy zurück – und wer sie kennt, kann " +
            "über deine YSR verfügen. Schreib sie auf Papier und gib sie niemals " +
            "weiter."
    override val woerterBild =
        "Eine Hand schreibt mit einem Stift auf eine kleine weiße Karte, " +
            "daneben liegt ein Smartphone mit dem Bildschirm nach unten"

    // Der Bildschirm hiess frueher "Mehr"; seit er "Einstellungen" heisst,
    // nennt ihn die Karte so.
    override val sicherTitel = "Sicher"
    override val sicherTitelBlau = "bleiben."
    override val sicherText =
        "Deine PIN und auf Wunsch dein Fingerabdruck schützen die Wallet. " +
            "Benachrichtigungen schaltest du unter „Einstellungen“ ein. Sie kommen " +
            "ohne Push-Dienst: Dein Gerät fragt selbst nach."
    override val sicherBild =
        "Zwei Hände halten ein Smartphone mit dem Sperrbildschirm der YSKAR " +
            "Wallet, der Daumen liegt auf dem unteren Teil des Bildschirms"

    override val kontrolleTitel = "Deine Wallet."
    override val kontrolleTitelBlau = "Deine Kontrolle."
    override val kontrolleText =
        "Deine YSR bleiben unter deiner Kontrolle. Deine Schlüssel werden " +
            "lokal auf deinem Gerät verwahrt. Willkommen im YSKAR-Netzwerk."
    override val kontrolleBild =
        "Zwei Menschen geben sich über einem runden Tisch die Hand, die " +
            "Smartphones liegen daneben"

    override val markeEmpfangen = "Empfangen"
    override val markeGesendet = "Gesendet"
    override val markeAngenommen = "Angenommen"
    override val markeWoerterPapier = "12 Wörter · nur auf Papier"
    override val markeNiemalsWeitergeben = "Niemals weitergeben – auch nicht an YSKAR"
    override val markeFingerabdruck = "Fingerabdruck"
    override val markePin = "PIN"
    override val markeSchluesselAufGeraet = "Schlüssel nur auf diesem Gerät"

    override val kaertchenGuthaben = "Guthaben"
    override val diagrammTitel = "Guthaben · 30 Tage"
    override val diagrammSpanne = "30 T"

    override val stufeAngenommen = "angenommen"
    override val stufeImBlock = "in einem Block"
    override val stufeZweimalBestaetigt = "zweimal bestätigt"

    override val beispielEmpfangen = "+12,50"
    override val beispielEingang = "+1,00"
    override val beispielAusgang = "−25,00"
    override val beispielGuthabenGanz = "1.284"
    override val beispielGuthabenBruch = ",57310946"
    override val beispielSummeEingang = "+307,54160751"
    override val beispielSummeAusgang = "−25,000168"
}

object EinfuehrungEn : EinfuehrungTexte {
    override val ueberspringen = "Skip"
    override val vorigeKarte = "Previous card"
    override val weiter = "Continue"
    override val walletOeffnen = "Open wallet"
    override fun zaehler(nummer: Int, von: Int) = "$nummer of $von"
    override fun ansage(nummer: Int, von: Int) = "Card $nummer of $von"

    override val walletTitel = "This is your"
    override val walletTitelBlau = "wallet."
    override val walletText =
        "Here you can see at a glance how many YSR you have. Below that, you " +
            "send and receive, and open the history and the settings."
    override val walletBild =
        "View over a shoulder at a smartphone held in a hand, with the " +
            "YSKAR wallet open on it"

    override val empfangenTitel = "Receive"
    override val empfangenTitelBlau = "YSR."
    override val empfangenText =
        "Show your QR code or send your address. It works like an account " +
            "number and always stays the same."
    override val empfangenBild =
        "Two people at a round table, seen from above: one person shows " +
            "their QR code, the other scans it"

    override val sendenTitel = "Send"
    override val sendenTitelBlau = "YSR."
    override val sendenText =
        "Enter the recipient and the amount, then check them in your own time. " +
            "The payment only goes out with your PIN or your fingerprint – an " +
            "accidental tap is never enough."
    override val sendenBild =
        "A hand holds a smartphone, the thumb about to touch the screen; " +
            "opposite, a second person holds their phone"

    override val fortschrittTitel = "The Y shows the"
    override val fortschrittTitelBlau = "progress."
    override val fortschrittText =
        "After sending, the Y fills in step by step: accepted by the node, " +
            "in a block, confirmed twice. The wallet never calls a payment “final”."
    override val fortschrittBild =
        "The Y in three stages: accepted, in a block, confirmed twice"

    override val ueberblickTitel = "Everything"
    override val ueberblickTitelBlau = "at a glance."
    override val ueberblickText =
        "You will find every payment in the history – green for incoming, grey " +
            "for outgoing. The chart shows how your balance has developed."
    override val ueberblickBild =
        "A smartphone lies on the table next to an open notebook and a " +
            "cup of coffee"

    override val woerterTitel = "Your 12"
    override val woerterTitelBlau = "words."
    override val woerterText =
        "These twelve words are the key to your wallet. With them you bring it " +
            "back on any new phone – and anyone who knows them can spend your " +
            "YSR. Write them down on paper and never share them."
    override val woerterBild =
        "A hand writes with a pen on a small white card; next to it lies a " +
            "smartphone, screen down"

    override val sicherTitel = "Stay"
    override val sicherTitelBlau = "safe."
    override val sicherText =
        "Your PIN and, if you like, your fingerprint protect the wallet. You " +
            "switch on notifications under “Settings”. They work without a push " +
            "service: your device asks by itself."
    override val sicherBild =
        "Two hands hold a smartphone showing the lock screen of the YSKAR " +
            "wallet, the thumb resting on the lower part of the screen"

    override val kontrolleTitel = "Your wallet."
    override val kontrolleTitelBlau = "Your control."
    override val kontrolleText =
        "Your YSR stay under your control. Your keys are kept locally on your " +
            "device. Welcome to the YSKAR network."
    override val kontrolleBild =
        "Two people shake hands across a round table, their smartphones " +
            "lying beside them"

    override val markeEmpfangen = "Received"
    override val markeGesendet = "Sent"
    override val markeAngenommen = "Accepted"
    override val markeWoerterPapier = "12 words · on paper only"
    override val markeNiemalsWeitergeben = "Never share them – not even with YSKAR"
    override val markeFingerabdruck = "Fingerprint"
    override val markePin = "PIN"
    override val markeSchluesselAufGeraet = "Keys only on this device"

    override val kaertchenGuthaben = "Balance"
    override val diagrammTitel = "Balance · 30 days"
    override val diagrammSpanne = "30D"

    override val stufeAngenommen = "accepted"
    override val stufeImBlock = "in a block"
    override val stufeZweimalBestaetigt = "confirmed twice"

    override val beispielEmpfangen = "+12.50"
    override val beispielEingang = "+1.00"
    override val beispielAusgang = "−25.00"
    override val beispielGuthabenGanz = "1,284"
    override val beispielGuthabenBruch = ".57310946"
    override val beispielSummeEingang = "+307.54160751"
    override val beispielSummeAusgang = "−25.000168"
}
