package com.yskar.wallet.sprache

import com.yskar.wallet.kontakte.Kontakte

/**
 * Die Texte der Kontakte - Namen statt Adressen (Wunsch des Besitzers,
 * 12.11.2026).
 *
 * Kurz und ohne Fachwort (REGELN.md, Regel 40). Ein Satz steht in jeder
 * Fassung dieser Texte, weil er der Kern der Sache ist: Der Name ist eine
 * Lesehilfe, geprueft wird die Adresse.
 */
interface KontakteTexte {
    val titel: String
    val hinzufuegen: String
    val neuTitel: String
    val bearbeitenTitel: String
    val waehlenTitel: String

    /** Die Zeile in den Einstellungen und der Verweis am Adressfeld. */
    val eintrag: String
    val eintragHinweis: String

    val leerTitel: String
    val leerText: String

    val name: String
    val namePlatzhalter: String
    val adresse: String
    val speichern: String
    val entfernen: String
    val wirklichEntfernen: String
    val nurAufDiesemGeraet: String

    /** "An Anna" und "Von Anna": im Verlauf, in der Zahlung, beim Pruefen. */
    fun an(name: String): String
    fun von(name: String): String

    val alsKontaktSpeichern: String
    val kontaktWaehlen: String

    fun grund(a: Kontakte.Ablehnung): String

    /** Die Adresse hat schon einen Namen - und der Satz nennt ihn. */
    fun schonGespeichertAls(name: String): String
}

object KontakteDe : KontakteTexte {
    override val titel = "Kontakte"
    override val hinzufuegen = "Kontakt hinzufügen"
    override val neuTitel = "Neuer Kontakt"
    override val bearbeitenTitel = "Kontakt"
    override val waehlenTitel = "Kontakt wählen"

    override val eintrag = "Kontakte"
    override val eintragHinweis = "Namen für Adressen, an die du öfter sendest."

    override val leerTitel = "Noch keine Kontakte"
    override val leerText =
        "Gib einer Adresse einen Namen. Dann steht im Verlauf und beim Senden " +
            "der Name statt der Adresse."

    override val name = "Name"
    override val namePlatzhalter = "z. B. Anna"
    override val adresse = "Adresse"
    override val speichern = "Speichern"
    override val entfernen = "Kontakt entfernen"
    override val wirklichEntfernen = "Zum Entfernen noch einmal tippen"
    override val nurAufDiesemGeraet =
        "Kontakte liegen nur auf diesem Gerät. Beim Senden prüfst du weiter die Adresse."

    override fun an(name: String) = "An $name"
    override fun von(name: String) = "Von $name"

    override val alsKontaktSpeichern = "Als Kontakt speichern"
    override val kontaktWaehlen = "Kontakte"

    override fun grund(a: Kontakte.Ablehnung) = when (a) {
        Kontakte.Ablehnung.NAME_LEER -> "Gib einen Namen ein."
        Kontakte.Ablehnung.NAME_ZU_LANG ->
            "Der Name ist zu lang. Höchstens ${Kontakte.NAME_MAX} Zeichen."
        Kontakte.Ablehnung.ADRESSE_UNGUELTIG -> "Das ist keine gültige YSKAR-Adresse."
        Kontakte.Ablehnung.ADRESSE_SCHON_DA -> "Diese Adresse ist schon gespeichert."
        Kontakte.Ablehnung.ZU_VIELE ->
            "Mehr als ${Kontakte.ANZAHL_MAX} Kontakte fasst die Liste nicht."
    }

    override fun schonGespeichertAls(name: String) =
        "Diese Adresse ist schon gespeichert, als „$name“."
}

object KontakteEn : KontakteTexte {
    override val titel = "Contacts"
    override val hinzufuegen = "Add contact"
    override val neuTitel = "New contact"
    override val bearbeitenTitel = "Contact"
    override val waehlenTitel = "Choose a contact"

    override val eintrag = "Contacts"
    override val eintragHinweis = "Names for addresses you send to often."

    override val leerTitel = "No contacts yet"
    override val leerText =
        "Give an address a name. The name then appears in your history and " +
            "when you send, instead of the address."

    override val name = "Name"
    override val namePlatzhalter = "e.g. Anna"
    override val adresse = "Address"
    override val speichern = "Save"
    override val entfernen = "Remove contact"
    override val wirklichEntfernen = "Tap again to remove"
    override val nurAufDiesemGeraet =
        "Contacts stay on this device. When you send, you still check the address."

    override fun an(name: String) = "To $name"
    override fun von(name: String) = "From $name"

    override val alsKontaktSpeichern = "Save as contact"
    override val kontaktWaehlen = "Contacts"

    override fun grund(a: Kontakte.Ablehnung) = when (a) {
        Kontakte.Ablehnung.NAME_LEER -> "Enter a name."
        Kontakte.Ablehnung.NAME_ZU_LANG ->
            "The name is too long. ${Kontakte.NAME_MAX} characters at most."
        Kontakte.Ablehnung.ADRESSE_UNGUELTIG -> "That is not a valid YSKAR address."
        Kontakte.Ablehnung.ADRESSE_SCHON_DA -> "This address is already saved."
        Kontakte.Ablehnung.ZU_VIELE ->
            "The list holds no more than ${Kontakte.ANZAHL_MAX} contacts."
    }

    override fun schonGespeichertAls(name: String) =
        "This address is already saved, as “$name”."
}
