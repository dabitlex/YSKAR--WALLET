package com.yskar.wallet.sprache

/** Texte: der Bildschirm "Einstellungen", die Sprachwahl und seine PIN-Abfragen. */
interface EinstellungenTexte {
    val titel: String

    // Sicherheit
    val sicherheit: String
    val fingerabdruck: String
    val fingerabdruckHinweis: String
    val fingerabdruckFehlt: String
    val pinAendern: String
    val woerterAnzeigen: String
    val woerterAnzeigenHinweis: String
    val autoSperre: String
    val autoSperreWert: String
    val autoSperreHinweis: String

    // Allgemein
    val allgemein: String
    val sprache: String
    val guthabenVerbergen: String
    val guthabenVerbergenHinweis: String
    val vibration: String
    val vibrationHinweis: String

    // Benachrichtigungen
    val benachrichtigungen: String
    val imHintergrundNachsehen: String
    val nachsehenAn: String
    val nachsehenAus: String
    val keinPush: String

    // Diese Wallet
    val dieseWallet: String
    val adresse: String
    val netz: String
    val explorer: String
    val explorerHinweis: String
    val einfuehrungAnsehen: String
    val selbsttest: String
    val selbsttestHinweis: String

    // Entfernen
    val walletEntfernen: String
    val walletEntfernenHinweis: String
    val wirklichEntfernen: String
    val wirklichEntfernenText: String
    val jaEntfernen: String

    fun version(nummer: String): String

    // Sprachwahl
    val spracheHinweis: String
    val gewaehlt: String

    // PIN-Abfragen
    val pinBestaetigen: String
    val pinEingeben: String
    val bisherigePin: String
    val neuePin: String
    val neuePinWiederholen: String
    val bioErklaerung: String
    val woerterErklaerung: String
    val eingabenUngleich: String
    val pinGeaendert: String

    /** Die Gruppe fuer das, was nicht jeder braucht: Explorer, Netz, Selbsttest. */
    val erweitert: String
}

object EinstellungenDe : EinstellungenTexte {
    override val titel = "Einstellungen"

    override val sicherheit = "Sicherheit"
    override val fingerabdruck = "Fingerabdruck"
    override val fingerabdruckHinweis = "Zum Entsperren und Bestätigen. Die PIN gilt weiter."
    override val fingerabdruckFehlt = "Auf diesem Gerät nicht eingerichtet."
    override val pinAendern = "PIN ändern"
    override val woerterAnzeigen = "Zwölf Wörter anzeigen"
    override val woerterAnzeigenHinweis =
        "Fragt vorher nach PIN oder Fingerabdruck. Bildschirmfotos sind dort gesperrt."
    override val autoSperre = "Automatisch sperren"
    override val autoSperreWert = "Beim Verlassen"
    override val autoSperreHinweis =
        "Sobald du die App verlässt, sperrt sich die Wallet. In der App-Übersicht " +
            "ist ihr Inhalt verdeckt."

    override val allgemein = "Allgemein"
    override val sprache = "Sprache"
    override val guthabenVerbergen = "Guthaben verbergen"
    override val guthabenVerbergenHinweis = "Zeigt Punkte statt Zahlen, bis du es wieder einblendest."
    override val vibration = "Vibration"
    override val vibrationHinweis = "Kurzes Rückmelden am Ziffernfeld und beim Scannen."

    override val benachrichtigungen = "Benachrichtigungen"
    override val imHintergrundNachsehen = "Im Hintergrund nachsehen"
    override val nachsehenAn = "Meldet Eingänge und bestätigte Ausgänge."
    override val nachsehenAus = "Aus. Die Wallet fragt nur, während sie offen ist."
    override val keinPush =
        "Kein Server beobachtet deine Adresse. Dein Telefon fragt " +
            "selbst alle paar Minuten nach."

    override val dieseWallet = "Diese Wallet"
    override val adresse = "Adresse"
    override val netz = "Netz"
    override val explorer = "Im Explorer ansehen"
    override val explorerHinweis = "Öffnet deine Adresse im Browser. Die Wallet sperrt sich dabei."
    override val einfuehrungAnsehen = "Einführung ansehen"
    override val selbsttest = "Selbsttest"
    override val selbsttestHinweis =
        "Prüft Verschlüsselung, Speicher und Verbindung auf diesem " +
            "Gerät."

    override val walletEntfernen = "Wallet von diesem Gerät entfernen"
    override val walletEntfernenHinweis =
        "Zurückholen kannst du sie danach nur mit deinen zwölf Wörtern."
    override val wirklichEntfernen = "Wirklich entfernen?"
    override val wirklichEntfernenText =
        "Danach ist die Wallet auf diesem Gerät weg. " +
            "Zurückholen kannst du sie NUR mit deinen zwölf Wörtern. " +
            "Hast du sie wirklich aufgeschrieben?"
    override val jaEntfernen = "Ja, entfernen"

    override fun version(nummer: String) = "YSKAR · Version $nummer"

    override val spracheHinweis = "Gilt sofort, auch für Zahlen und Datum."
    override val gewaehlt = "gewählt"

    override val pinBestaetigen = "PIN bestätigen"
    override val pinEingeben = "PIN eingeben"
    override val bisherigePin = "Bisherige PIN"
    override val neuePin = "Neue PIN"
    override val neuePinWiederholen = "Neue PIN wiederholen"
    override val bioErklaerung =
        "Zum Einschalten braucht die Wallet einmal deine PIN. " +
            "Gespeichert wird sie nicht."
    override val woerterErklaerung =
        "Gleich stehen die zwölf Wörter auf dem Bildschirm. Achte " +
            "darauf, dass niemand mitliest."
    override val eingabenUngleich = "Die beiden Eingaben sind nicht gleich."
    override val pinGeaendert = "PIN geändert."

    override val erweitert = "Erweitert"
}

object EinstellungenEn : EinstellungenTexte {
    override val titel = "Settings"

    override val sicherheit = "Security"
    override val fingerabdruck = "Fingerprint"
    override val fingerabdruckHinweis = "For unlocking and confirming. Your PIN still works."
    override val fingerabdruckFehlt = "Not set up on this device."
    override val pinAendern = "Change PIN"
    override val woerterAnzeigen = "Show the twelve words"
    override val woerterAnzeigenHinweis =
        "Asks for your PIN or fingerprint first. Screenshots are blocked there."
    override val autoSperre = "Lock automatically"
    override val autoSperreWert = "On leaving"
    override val autoSperreHinweis =
        "As soon as you leave the app, the wallet locks. In the app overview " +
            "its content is hidden."

    override val allgemein = "General"
    override val sprache = "Language"
    override val guthabenVerbergen = "Hide balance"
    override val guthabenVerbergenHinweis = "Shows dots instead of numbers until you reveal it again."
    override val vibration = "Vibration"
    override val vibrationHinweis = "Brief feedback on the keypad and when scanning."

    override val benachrichtigungen = "Notifications"
    override val imHintergrundNachsehen = "Check in the background"
    override val nachsehenAn = "Reports incoming payments and confirmed outgoing transfers."
    override val nachsehenAus = "Off. The wallet only asks while it is open."
    override val keinPush =
        "No server watches your address. Your phone checks by itself " +
            "every few minutes."

    override val dieseWallet = "This wallet"
    override val adresse = "Address"
    override val netz = "Network"
    override val explorer = "View in explorer"
    override val explorerHinweis =
        "Opens your address in the browser. The wallet locks while " +
            "you are there."
    override val einfuehrungAnsehen = "View introduction"
    override val selbsttest = "Self-test"
    override val selbsttestHinweis = "Checks encryption, storage and connection on this device."

    override val walletEntfernen = "Remove wallet from this device"
    override val walletEntfernenHinweis =
        "Afterwards you can only get it back with your twelve words."
    override val wirklichEntfernen = "Really remove?"
    override val wirklichEntfernenText =
        "After that the wallet is gone from this device. " +
            "You can ONLY get it back with your twelve words. " +
            "Have you really written them down?"
    override val jaEntfernen = "Yes, remove"

    override fun version(nummer: String) = "YSKAR · Version $nummer"

    override val spracheHinweis = "Applies at once, to numbers and dates as well."
    override val gewaehlt = "selected"

    override val pinBestaetigen = "Confirm PIN"
    override val pinEingeben = "Enter PIN"
    override val bisherigePin = "Current PIN"
    override val neuePin = "New PIN"
    override val neuePinWiederholen = "Repeat new PIN"
    override val bioErklaerung =
        "To switch this on, the wallet needs your PIN once. It is not " +
            "stored."
    override val woerterErklaerung =
        "The twelve words are about to appear on screen. Make sure " +
            "nobody is reading along."
    override val eingabenUngleich = "The two entries do not match."
    override val pinGeaendert = "PIN changed."

    override val erweitert = "Advanced"
}
