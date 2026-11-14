package com.yskar.wallet.sprache

/** Texte: Einstieg, PIN festlegen, die zwoelf Woerter, Wiederherstellen, Sperre, Fingerabdruck-Abfrage. */
interface EinrichtenTexte {
    // Auf mehreren Seiten rund um die zwoelf Woerter.
    val weiter: String
    val fotosperre: String

    // Einstieg.
    /** Zwei Zeilen, mit Zeilenumbruch dazwischen. */
    val einstiegLeitsatz: String
    val einstiegWoerterSindGuthaben: String
    val einstiegNiemandSetztZurueck: String
    val neueWalletAnlegen: String
    val ausWoerternWiederherstellen: String

    // Die zwoelf Woerter beim Anlegen zeigen.
    val woerterAufschreibenTitel: String
    val woerterAufschreibenAbsatz: String
    val woerterAufgeschrieben: String

    // Die zwoelf Woerter noch einmal ansehen (unter "Einstellungen").
    val woerterAnsehenTitel: String
    val woerterAnsehenAbsatz: String
    val fertig: String

    // Die Gegenprobe: drei Woerter eintragen.
    val gegenprobeTitel: String
    val gegenprobeAbsatz: String

    /** Was der Pfeil oben links tut - fuer den Bildschirmleser. */
    val woerterNochEinmalZeigen: String

    /** Ueber einem Feld der Gegenprobe; [nummer] zaehlt ab 1. */
    fun wortNummer(nummer: Int): String

    // Wiederherstellen.
    val wiederherstellenTitel: String
    val wiederherstellenAbsatz: String
    fun woerterVon(n: Int, von: Int): String

    /** [uebrig] Woerter haben in den zwoelf Feldern keinen Platz gefunden. */
    fun zuVieleWoerter(uebrig: Int): String

    // PIN festlegen.
    val pinFestlegen: String
    val pinWiederholen: String
    val pinFestlegenAbsatz: String
    val pinWiederholenAbsatz: String
    val pinNichtGleich: String
    val tresorWirdVerschluesselt: String
    val zeichenFuelltSich: String

    // Sperre.
    val pinEingeben: String
    val entsperrtGerade: String
    val mitFingerabdruckEntsperren: String
}

object EinrichtenDe : EinrichtenTexte {
    override val weiter = "Weiter"
    override val fotosperre = "Bildschirmfotos sind hier gesperrt."

    override val einstiegLeitsatz = "Deine Wallet.\nDein Schlüssel."
    override val einstiegWoerterSindGuthaben = "Die zwölf Wörter sind das Guthaben."
    override val einstiegNiemandSetztZurueck = "Es gibt niemanden, der sie zurücksetzen kann."
    override val neueWalletAnlegen = "Neue Wallet anlegen"
    override val ausWoerternWiederherstellen = "Aus zwölf Wörtern wiederherstellen"

    override val woerterAufschreibenTitel = "Schreib diese zwölf Wörter auf"
    override val woerterAufschreibenAbsatz =
        "Auf Papier, in dieser Reihenfolge. Nicht abfotografieren, nicht " +
            "in eine Notiz-App tippen, nicht verschicken."
    override val woerterAufgeschrieben = "Ich habe sie aufgeschrieben"

    override val woerterAnsehenTitel = "Deine zwölf Wörter"
    override val woerterAnsehenAbsatz =
        "Wer sie hat, hat das Guthaben. Vergleiche sie mit deiner " +
            "Notiz und zeig sie niemandem. Kein Support fragt je danach."
    override val fertig = "Fertig"

    override val gegenprobeTitel = "Kurze Kontrolle"
    override val gegenprobeAbsatz =
        "Trag diese Wörter aus deiner Notiz ein. So ist sicher, dass " +
            "sie stimmt."
    override val woerterNochEinmalZeigen = "Wörter noch einmal zeigen"
    override fun wortNummer(nummer: Int) = "Wort Nummer $nummer"

    override val wiederherstellenTitel = "Wallet wiederherstellen"
    override val wiederherstellenAbsatz =
        "Gib deine Wörter in ihrer Reihenfolge ein, eines je Feld. " +
            "Groß- und Kleinschreibung ist egal."
    override fun woerterVon(n: Int, von: Int) = "$n von $von Wörtern"
    override fun zuVieleWoerter(uebrig: Int) =
        "Das waren mehr als zwölf Wörter. " +
            (if (uebrig == 1) "Eines hat" else "$uebrig haben") +
            " keinen Platz gefunden."

    override val pinFestlegen = "PIN festlegen"
    override val pinWiederholen = "PIN wiederholen"
    override val pinFestlegenAbsatz = "Sechs Ziffern. Sie schützen die Wallet auf diesem Gerät."
    override val pinWiederholenAbsatz = "Noch einmal, zur Sicherheit."
    override val pinNichtGleich = "Die beiden Eingaben sind nicht gleich."
    override val tresorWirdVerschluesselt = "Deine Wallet wird gesichert. Das dauert ein paar Sekunden."
    override val zeichenFuelltSich = "Das Zeichen füllt sich mit jeder Ziffer."

    override val pinEingeben = "PIN eingeben"
    override val entsperrtGerade = "entsperrt …"
    override val mitFingerabdruckEntsperren = "Mit Fingerabdruck entsperren"
}

object EinrichtenEn : EinrichtenTexte {
    override val weiter = "Continue"
    override val fotosperre = "Screenshots are blocked here."

    override val einstiegLeitsatz = "Your wallet.\nYour key."
    override val einstiegWoerterSindGuthaben = "The twelve words are the balance."
    override val einstiegNiemandSetztZurueck = "There is no one who can reset them."
    override val neueWalletAnlegen = "Create new wallet"
    override val ausWoerternWiederherstellen = "Restore from twelve words"

    override val woerterAufschreibenTitel = "Write down these twelve words"
    override val woerterAufschreibenAbsatz =
        "On paper, in this order. Do not photograph them, do not type them " +
            "into a notes app, do not send them to anyone."
    override val woerterAufgeschrieben = "I have written them down"

    override val woerterAnsehenTitel = "Your twelve words"
    override val woerterAnsehenAbsatz =
        "Whoever has them has the funds. Compare them with your note " +
            "and show them to no one. No support team will ever ask for " +
            "them."
    override val fertig = "Done"

    override val gegenprobeTitel = "Quick check"
    override val gegenprobeAbsatz =
        "Enter these words from your note. That way you know it is " +
            "right."
    override val woerterNochEinmalZeigen = "Show the words again"
    override fun wortNummer(nummer: Int) = "Word number $nummer"

    override val wiederherstellenTitel = "Restore wallet"
    override val wiederherstellenAbsatz =
        "Enter your words in their order, one per field. " +
            "Upper and lower case do not matter."
    override fun woerterVon(n: Int, von: Int) = "$n of $von words"
    override fun zuVieleWoerter(uebrig: Int) =
        "That was more than twelve words. " +
            (if (uebrig == 1) "One" else "$uebrig") +
            " did not fit."

    override val pinFestlegen = "Set PIN"
    override val pinWiederholen = "Repeat PIN"
    override val pinFestlegenAbsatz = "Six digits. They protect the wallet on this device."
    override val pinWiederholenAbsatz = "Once more, to be safe."
    override val pinNichtGleich = "The two entries do not match."
    override val tresorWirdVerschluesselt = "Your wallet is being secured. This takes a few seconds."
    override val zeichenFuelltSich = "The emblem fills in with every digit."

    override val pinEingeben = "Enter PIN"
    override val entsperrtGerade = "unlocking …"
    override val mitFingerabdruckEntsperren = "Unlock with fingerprint"
}
