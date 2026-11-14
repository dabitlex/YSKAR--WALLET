package com.yskar.wallet.sprache

/**
 * Was mehrere Bildschirme gemeinsam haben: die Bausteine aus ui/Teile.kt
 * und, weiter unten, die Zeiten aus ui/Zeiten.kt.
 */
interface AllgemeinTexte {
    val zurueck: String
    val abbrechen: String
    fun schrittVon(schritt: Int, von: Int): String
    val adresseKopieren: String
    val mitFingerabdruck: String
    val letzteZifferLoeschen: String
    val qrNichtDarstellbar: String
    val qrDerAdresse: String
    val nochKeineZiffer: String
    fun ziffernEingegeben(getippt: Int, von: Int): String
    val einenMoment: String

    /** Der Knopf, der eine Ueberweisung oder ein Konto im Explorer oeffnet. */
    val imExplorer: String

    /** Was ein Bildschirmleser sagt, solange der Sichtschutz liegt. */
    val sichtschutz: String

    // Warum der Fingerabdruck nicht geht (ui/SensorFrage.kt).
    val sensorFehlt: String
    val sensorNichtVerfuegbar: String
    val sensorOhneFinger: String
    val sensorBrauchtUpdate: String
    val sensorNichtDa: String
    /** Der Aufklapper fuer das, was nicht jeder braucht. */
    val einzelheiten: String
    val einzelheitenAuf: String
    val einzelheitenZu: String
}

object AllgemeinDe : AllgemeinTexte {
    override val zurueck = "Zurück"
    override val abbrechen = "Abbrechen"
    override fun schrittVon(schritt: Int, von: Int) = "Schritt $schritt von $von"
    override val adresseKopieren = "Adresse kopieren"
    override val mitFingerabdruck = "Mit Fingerabdruck"
    override val letzteZifferLoeschen = "Letzte Ziffer löschen"
    override val qrNichtDarstellbar = "QR-Code nicht darstellbar"
    override val qrDerAdresse = "QR-Code der Adresse"
    override val nochKeineZiffer = "Noch keine Ziffer eingegeben"
    override fun ziffernEingegeben(getippt: Int, von: Int) = "$getippt von $von Ziffern eingegeben"
    override val einenMoment = "einen Moment …"
    override val imExplorer = "Im Explorer ansehen"
    override val sichtschutz = "Inhalt verdeckt, solange die App im Hintergrund ist"
    override val sensorFehlt = "Dieses Gerät hat keinen Sensor für starke Biometrie."
    override val sensorNichtVerfuegbar = "Der Sensor ist gerade nicht verfügbar."
    override val sensorOhneFinger = "Es ist kein Fingerabdruck eingerichtet."
    override val sensorBrauchtUpdate =
        "Android verlangt ein Sicherheitsupdate, bevor der Sensor nutzbar ist."
    override val sensorNichtDa = "Starke Biometrie steht nicht zur Verfügung."
    override val einzelheiten = "Einzelheiten"
    override val einzelheitenAuf = "Einzelheiten zeigen"
    override val einzelheitenZu = "Einzelheiten verbergen"
}

object AllgemeinEn : AllgemeinTexte {
    override val zurueck = "Back"
    override val abbrechen = "Cancel"
    override fun schrittVon(schritt: Int, von: Int) = "Step $schritt of $von"
    override val adresseKopieren = "Copy address"
    override val mitFingerabdruck = "Use fingerprint"
    override val letzteZifferLoeschen = "Delete last digit"
    override val qrNichtDarstellbar = "QR code cannot be displayed"
    override val qrDerAdresse = "QR code of the address"
    override val nochKeineZiffer = "No digit entered yet"
    override fun ziffernEingegeben(getippt: Int, von: Int) = "$getippt of $von digits entered"
    override val einenMoment = "one moment …"
    override val imExplorer = "View in explorer"
    override val sichtschutz = "Content hidden while the app is in the background"
    override val sensorFehlt = "This device has no sensor for strong biometrics."
    override val sensorNichtVerfuegbar = "The sensor is not available right now."
    override val sensorOhneFinger = "No fingerprint has been set up."
    override val sensorBrauchtUpdate =
        "Android requires a security update before the sensor can be used."
    override val sensorNichtDa = "Strong biometrics are not available."
    override val einzelheiten = "Details"
    override val einzelheitenAuf = "Show details"
    override val einzelheitenZu = "Hide details"
}

/** Zeiten und Zaehler in Worten - die Texte zu ui/Zeiten.kt. */
interface ZeitTexte {
    val heute: String
    val gestern: String
    val frueher: String
    val zeitUnbekannt: String

    /** Muster fuer `DateTimeFormatter`: Tag und Monat, ohne Jahr. */
    val musterTag: String

    /** Muster fuer `DateTimeFormatter`: Tag, Monat und Jahr. */
    val musterTagJahr: String

    /** "Stand 18:17" - [zeit] ist schon fertig geschrieben. */
    fun stand(zeit: String): String
    fun geladen(anzahl: Int): String
    fun eintraege(anzahl: Int): String
    val geprueftEben: String
    fun geprueftVorSekunden(n: Long): String
    fun geprueftVorMinuten(n: Long): String
    fun geprueftVorStunden(n: Long): String

    /** Neben einem Schritt des Sendestatus. */
    val schrittOffen: String
    val schrittWartet: String
    val schrittErledigt: String

    /** "Block 9.472" - [nummer] ist schon fertig geschrieben. */
    fun block(nummer: String): String
}

object ZeitDe : ZeitTexte {
    override val heute = "Heute"
    override val gestern = "Gestern"
    override val frueher = "Früher"
    override val zeitUnbekannt = "Zeit unbekannt"
    override val musterTag = "dd.MM."
    override val musterTagJahr = "dd.MM.yyyy"
    override fun stand(zeit: String) = "Aktualisiert $zeit"
    override fun geladen(anzahl: Int) = "$anzahl geladen"
    override fun eintraege(anzahl: Int) = if (anzahl == 1) "1 Eintrag" else "$anzahl Einträge"
    override val geprueftEben = "geprüft eben"
    override fun geprueftVorSekunden(n: Long) = "geprüft vor $n s"
    override fun geprueftVorMinuten(n: Long) = "geprüft vor $n min"
    override fun geprueftVorStunden(n: Long) = "geprüft vor $n h"
    override val schrittOffen = "offen"
    override val schrittWartet = "wartet"
    override val schrittErledigt = "erledigt"
    override fun block(nummer: String) = "Block $nummer"
}

object ZeitEn : ZeitTexte {
    override val heute = "Today"
    override val gestern = "Yesterday"
    override val frueher = "Earlier"
    override val zeitUnbekannt = "Time unknown"
    override val musterTag = "d MMM"
    override val musterTagJahr = "d MMM yyyy"
    override fun stand(zeit: String) = "Updated $zeit"
    override fun geladen(anzahl: Int) = "$anzahl loaded"
    override fun eintraege(anzahl: Int) = if (anzahl == 1) "1 entry" else "$anzahl entries"
    override val geprueftEben = "checked just now"
    override fun geprueftVorSekunden(n: Long) = "checked $n s ago"
    override fun geprueftVorMinuten(n: Long) = "checked $n min ago"
    override fun geprueftVorStunden(n: Long) = "checked $n h ago"
    override val schrittOffen = "not yet"
    override val schrittWartet = "waiting"
    override val schrittErledigt = "done"
    override fun block(nummer: String) = "Block $nummer"
}
