package com.yskar.wallet.sprache

import com.yskar.wallet.otc.Ablauf.Stoerung
import com.yskar.wallet.otc.Auftrag.Fehler
import com.yskar.wallet.otc.Tauschbuch.Rolle
import com.yskar.wallet.otc.Tauschbuch.Stand

/**
 * Texte: OTC-Tausch mit otc.yskar.app (Wallet 3.1).
 *
 * Saetze kurz, Zahlen vollstaendig (Regel 29: gekuerzt wird die Form, nie
 * die Wahrheit). Kein Vergleich mit anderen Waehrungen ausser dem Namen
 * der Gegenwaehrung des Tauschs.
 */
interface OtcTexte {
    // Einstiege unter "Einstellungen"
    val rubrik: String
    val eintragScannen: String
    val eintragScannenHinweis: String
    val eintragOffen: String

    // Scanner
    val scannerTitel: String
    val scannerAnleitung: String
    val scannerKeinCode: String

    // Titel je Auftrag
    val titelHash: String
    val titelSperren: String
    val titelEinloesen: String
    val titelZurueck: String

    // Zeilen
    val tausch: String
    val duBekommst: String
    val duGibst: String
    val gegenwert: String
    val sperradresse: String
    val vonSkript: String
    val anDich: String
    val gebuehr: String
    val gesamt: String
    val einloesenBis: String
    val zurueckAb: String
    val bestaetigungen: String
    fun bestaetigungenVon(ist: Long, soll: Long): String
    fun block(hoehe: Long): String
    fun blockIn(hoehe: Long, bloecke: Long): String
    val gegenSperre: String
    val laeuftAb: String
    fun inMinuten(minuten: Long): String
    val antwortAn: String

    // Erklaerungen
    val hashErklaerung: String
    val sperrenGeprueft: String
    val sperrenLautSeite: String
    val einloesenGeprueft: String
    val einloesenOeffentlich: String
    val zurueckGeprueft: String

    // Knoepfe
    val knopfHash: String
    val knopfSperren: String
    val knopfEinloesen: String
    val knopfZurueck: String
    val nochmal: String
    val fertig: String
    val schliessen: String

    // PIN
    val pinTitel: String
    val worumHash: String
    val worumEinloesen: String
    val worumZurueck: String
    val bioGrund: String

    // Laufen und Ergebnis
    val fragtKnoten: String
    val sendet: String
    val hashGesendet: String
    val hashGesendetText: String
    val angenommen: String
    val angenommenText: String
    val kennung: String
    val abgelehntTitel: String
    val nichtsUnterschrieben: String
    fun knotenLehntAb(grund: String): String
    val seiteSagt: String
    fun zitat(text: String): String
    val seiteLehntAb: String
    val seiteNichtErreichbar: String
    val seiteWeiterleitung: String
    val keinKnoten: String
    fun knotenAntwortet(status: Int): String
    val skriptNichtGefunden: String
    val fassungFehlt: String

    fun fehler(f: Fehler): String
    fun stoerung(s: Stoerung): String

    // Offene Tausche
    val offenTitel: String
    val offenLeer: String
    val offenHinweis: String
    fun rolle(r: Rolle): String
    fun stand(s: Stand): String
    val wartetAufSperre: String
    val oeffnen: String
}

object OtcDe : OtcTexte {
    override val rubrik = "OTC-Tausch"
    override val eintragScannen = "Tausch-Auftrag scannen"
    override val eintragScannenHinweis = "QR-Code von otc.yskar.app"
    override val eintragOffen = "Offene Tausche"

    override val scannerTitel = "Tausch-Auftrag scannen"
    override val scannerAnleitung = "Halte die Kamera auf den QR-Code auf otc.yskar.app."
    override val scannerKeinCode = "Das ist kein Tausch-Auftrag von otc.yskar.app."

    override val titelHash = "Tausch vorbereiten"
    override val titelSperren = "YSR sperren"
    override val titelEinloesen = "YSR einlösen"
    override val titelZurueck = "YSR zurückholen"

    override val tausch = "Tausch"
    override val duBekommst = "Du bekommst"
    override val duGibst = "Du sperrst"
    override val gegenwert = "Gegenwert"
    override val sperradresse = "Sperradresse"
    override val vonSkript = "Aus der Sperre"
    override val anDich = "An dich"
    override val gebuehr = "Gebühr"
    override val gesamt = "Gesamt"
    override val einloesenBis = "Einlösen bis"
    override val zurueckAb = "Zurückholen ab"
    override val bestaetigungen = "Bestätigungen"
    override fun bestaetigungenVon(ist: Long, soll: Long) = "$ist von $soll"
    override fun block(hoehe: Long) = "Block $hoehe"
    override fun blockIn(hoehe: Long, bloecke: Long) =
        if (bloecke <= 0) "Block $hoehe" else "Block $hoehe · in ca. ${stunden(bloecke)}"
    override val gegenSperre = "Gegen-Sperre"
    override val laeuftAb = "Läuft ab"
    override fun inMinuten(minuten: Long) = if (minuten <= 1) "in 1 Minute" else "in $minuten Minuten"
    override val antwortAn = "Antwort an"

    override val hashErklaerung =
        "Die Wallet leitet aus deinen 12 Wörtern ein Geheimnis für diesen Tausch ab. " +
            "An otc.yskar.app geht nur sein Fingerabdruck – das Geheimnis bleibt auf diesem Gerät."
    override val sperrenGeprueft =
        "Selbst nachgebaut: Löst der Käufer nicht ein, holst nur du die YSR zurück."
    override val sperrenLautSeite =
        "Die Gegen-Sperre kann die Wallet nicht selbst prüfen. Sie steht so auf otc.yskar.app – " +
            "sieh dort nach, bevor du sperrst."
    override val einloesenGeprueft =
        "Sperre, Betrag und Bestätigungen selbst geprüft."
    override val einloesenOeffentlich =
        "Mit dem Einlösen wird das Geheimnis öffentlich. Damit holt sich der Verkäufer den Gegenwert."
    override val zurueckGeprueft =
        "Die Frist ist erreicht. Die YSR gehen zurück an deine Adresse."

    override val knopfHash = "Fingerabdruck senden"
    override val knopfSperren = "Weiter zur Prüfung"
    override val knopfEinloesen = "Einlösen"
    override val knopfZurueck = "Zurückholen"
    override val nochmal = "Neu laden"
    override val fertig = "Fertig"
    override val schliessen = "Schließen"

    override val pinTitel = "Bestätigen"
    override val worumHash = "Fingerabdruck an otc.yskar.app"
    override val worumEinloesen = "YSR einlösen"
    override val worumZurueck = "YSR zurückholen"
    override val bioGrund = "OTC-Tausch bestätigen"

    override val fragtKnoten = "Frage den Knoten …"
    override val sendet = "Wird gesendet …"
    override val hashGesendet = "An otc.yskar.app gesendet"
    override val hashGesendetText = "Die Seite zeigt jetzt den nächsten Schritt."
    override val angenommen = "Vom Knoten angenommen"
    override val angenommenText =
        "Das ist noch nicht im Block. Den Stand siehst du unter „Offene Tausche“ und auf otc.yskar.app."
    override val kennung = "Kennung"
    override val abgelehntTitel = "Nicht unterschrieben"
    override val nichtsUnterschrieben = "Es wurde nichts unterschrieben."
    override fun knotenLehntAb(grund: String) = "Der Knoten hat abgelehnt ($grund). Es wurde nichts ausgegeben."
    override val seiteSagt = "Nachricht der Seite:"
    override fun zitat(text: String) = "„$text“"
    override val seiteLehntAb = "Die Seite hat den Fingerabdruck abgelehnt."
    override val seiteNichtErreichbar = "otc.yskar.app war nicht erreichbar. Prüfe die Verbindung und scanne neu."
    override val seiteWeiterleitung = "Die Seite wollte umleiten. Dem folgt die Wallet nicht."
    override val keinKnoten = "Keine Verbindung zum Knoten."
    override fun knotenAntwortet(status: Int) = "Der Knoten antwortet nicht wie erwartet ($status)."
    override val skriptNichtGefunden = "Der Knoten kennt diese Sperradresse noch nicht."
    override val fassungFehlt = "Das Netz nimmt Ausgaben aus Sperren gerade nicht an. Später noch einmal."

    override fun fehler(f: Fehler) = when (f) {
        Fehler.FORMAT -> "Das ist kein lesbarer Tausch-Auftrag."
        Fehler.VERSION -> "Diese Fassung des Auftrags kennt die Wallet nicht. Bitte die Wallet aktualisieren."
        Fehler.ART -> "Diesen Auftrag kennt die Wallet nicht."
        Fehler.DATEN -> "Im Auftrag fehlt etwas oder es ist falsch geschrieben."
        Fehler.RUECKRUF -> "Die Antwortadresse fehlt oder ist nicht sicher (nur https)."
        Fehler.RUECKRUF_HOST -> "Die Antwort ginge nicht an otc.yskar.app."
        Fehler.ADRESSE_FREMD -> "Der Auftrag ist für eine andere Adresse."
        Fehler.NICHT_BETEILIGT -> "Dein Schlüssel kommt in dieser Sperre nicht an der richtigen Stelle vor."
        Fehler.SKRIPT -> "Die Sperradresse passt nicht zu den Teilen des Auftrags."
        Fehler.FRISTEN -> "Die Fristen der Sperre passen nicht."
        Fehler.ABGELAUFEN -> "Der Auftrag ist abgelaufen. Lade die Seite neu."
        Fehler.ZU_LANGE -> "Der Auftrag gilt zu lange."
        Fehler.BETRAG -> "Der Betrag fehlt oder ist ein anderer als beim Vorbereiten."
        Fehler.GEHEIMNIS -> "Das Geheimnis dieser Wallet passt nicht zu dieser Sperre."
        Fehler.ZU_KNAPP -> "Für den Käufer bliebe zu wenig Zeit zum Einlösen. Lade die Seite neu."
        Fehler.ZU_LANG -> "Die YSR wären zu lange gebunden (mehr als 300 Blöcke)."
        Fehler.UNBESTAETIGT -> "Die Sperre hat noch nicht genug Bestätigungen. Warte noch."
        Fehler.GUTHABEN -> "In der Sperre liegt nicht genug."
        Fehler.WARTEND -> "Auf der Sperre wartet noch eine Transaktion auf einen Block."
        Fehler.ZU_SPAET -> "Für das Einlösen ist es zu spät – die Frist ist zu nah."
        Fehler.ZU_FRUEH -> "Die Frist ist noch nicht erreicht."
    }

    override fun stoerung(s: Stoerung) = when (s) {
        Stoerung.WOERTER_UNBRAUCHBAR -> "Aus den Wörtern im Tresor ließ sich kein Schlüssel ableiten."
        Stoerung.SCHLUESSEL_PASST_NICHT -> "Der Schlüssel passt nicht zur angezeigten Adresse."
        Stoerung.GEGENPROBE -> "Eine Gegenprobe vor dem Senden ist gescheitert."
    }

    override val offenTitel = "Offene Tausche"
    override val offenLeer = "Keine offenen Tausche auf diesem Gerät."
    override val offenHinweis =
        "Liegt nur auf diesem Gerät. Einlösen und Zurückholen gehen von hier auch ohne otc.yskar.app."
    override fun rolle(r: Rolle) = when (r) {
        Rolle.KAEUFER -> "Du kaufst YSR"
        Rolle.VERKAEUFER -> "Du verkaufst YSR"
    }
    override fun stand(s: Stand) = when (s) {
        Stand.GEMERKT -> "vorbereitet"
        Stand.GESPERRT -> "gesperrt"
        Stand.EINGELOEST -> "eingelöst"
        Stand.ZURUECK -> "zurückgeholt"
    }
    override val wartetAufSperre = "wartet auf die Sperre"
    override val oeffnen = "Öffnen"
}

object OtcEn : OtcTexte {
    override val rubrik = "OTC swap"
    override val eintragScannen = "Scan swap order"
    override val eintragScannenHinweis = "QR code from otc.yskar.app"
    override val eintragOffen = "Open swaps"

    override val scannerTitel = "Scan swap order"
    override val scannerAnleitung = "Point the camera at the QR code on otc.yskar.app."
    override val scannerKeinCode = "This is not a swap order from otc.yskar.app."

    override val titelHash = "Prepare swap"
    override val titelSperren = "Lock YSR"
    override val titelEinloesen = "Claim YSR"
    override val titelZurueck = "Reclaim YSR"

    override val tausch = "Swap"
    override val duBekommst = "You receive"
    override val duGibst = "You lock"
    override val gegenwert = "Counter value"
    override val sperradresse = "Lock address"
    override val vonSkript = "From the lock"
    override val anDich = "To you"
    override val gebuehr = "Fee"
    override val gesamt = "Total"
    override val einloesenBis = "Claim until"
    override val zurueckAb = "Reclaim from"
    override val bestaetigungen = "Confirmations"
    override fun bestaetigungenVon(ist: Long, soll: Long) = "$ist of $soll"
    override fun block(hoehe: Long) = "Block $hoehe"
    override fun blockIn(hoehe: Long, bloecke: Long) =
        if (bloecke <= 0) "Block $hoehe" else "Block $hoehe · in about ${hours(bloecke)}"
    override val gegenSperre = "Counter lock"
    override val laeuftAb = "Expires"
    override fun inMinuten(minuten: Long) = if (minuten <= 1) "in 1 minute" else "in $minuten minutes"
    override val antwortAn = "Reply to"

    override val hashErklaerung =
        "The wallet derives a secret for this swap from your 12 words. " +
            "Only its fingerprint goes to otc.yskar.app – the secret stays on this device."
    override val sperrenGeprueft =
        "Rebuilt by the wallet: if the buyer doesn't claim, only you can reclaim the YSR."
    override val sperrenLautSeite =
        "The wallet cannot check the counter lock itself. This is what otc.yskar.app shows – " +
            "look there before you lock."
    override val einloesenGeprueft =
        "Lock, amount and confirmations checked by the wallet."
    override val einloesenOeffentlich =
        "Claiming makes the secret public. The seller uses it to collect the counter value."
    override val zurueckGeprueft =
        "The deadline has passed. The YSR go back to your address."

    override val knopfHash = "Send fingerprint"
    override val knopfSperren = "Continue to review"
    override val knopfEinloesen = "Claim"
    override val knopfZurueck = "Reclaim"
    override val nochmal = "Reload"
    override val fertig = "Done"
    override val schliessen = "Close"

    override val pinTitel = "Confirm"
    override val worumHash = "Fingerprint to otc.yskar.app"
    override val worumEinloesen = "Claim YSR"
    override val worumZurueck = "Reclaim YSR"
    override val bioGrund = "Confirm OTC swap"

    override val fragtKnoten = "Asking the node …"
    override val sendet = "Sending …"
    override val hashGesendet = "Sent to otc.yskar.app"
    override val hashGesendetText = "The site now shows the next step."
    override val angenommen = "Accepted by the node"
    override val angenommenText =
        "It is not in a block yet. You can follow it under “Open swaps” and on otc.yskar.app."
    override val kennung = "ID"
    override val abgelehntTitel = "Not signed"
    override val nichtsUnterschrieben = "Nothing was signed."
    override fun knotenLehntAb(grund: String) = "The node rejected it ($grund). Nothing was spent."
    override val seiteSagt = "Message from the site:"
    override fun zitat(text: String) = "“$text”"
    override val seiteLehntAb = "The site rejected the fingerprint."
    override val seiteNichtErreichbar = "otc.yskar.app could not be reached. Check the connection and scan again."
    override val seiteWeiterleitung = "The site tried to redirect. The wallet does not follow."
    override val keinKnoten = "No connection to the node."
    override fun knotenAntwortet(status: Int) = "The node is not answering as expected ($status)."
    override val skriptNichtGefunden = "The node does not know this lock address yet."
    override val fassungFehlt = "The network does not accept spends from locks right now. Try again later."

    override fun fehler(f: Fehler) = when (f) {
        Fehler.FORMAT -> "This is not a readable swap order."
        Fehler.VERSION -> "The wallet does not know this order version. Please update the wallet."
        Fehler.ART -> "The wallet does not know this order."
        Fehler.DATEN -> "Something in the order is missing or malformed."
        Fehler.RUECKRUF -> "The reply address is missing or not secure (https only)."
        Fehler.RUECKRUF_HOST -> "The reply would not go to otc.yskar.app."
        Fehler.ADRESSE_FREMD -> "The order is for a different address."
        Fehler.NICHT_BETEILIGT -> "Your key is not in the right place in this lock."
        Fehler.SKRIPT -> "The lock address does not match the parts of the order."
        Fehler.FRISTEN -> "The deadlines of the lock do not fit."
        Fehler.ABGELAUFEN -> "The order has expired. Reload the site."
        Fehler.ZU_LANGE -> "The order is valid for too long."
        Fehler.BETRAG -> "The amount is missing or differs from the one when preparing."
        Fehler.GEHEIMNIS -> "This wallet's secret does not match this lock."
        Fehler.ZU_KNAPP -> "The buyer would have too little time to claim. Reload the site."
        Fehler.ZU_LANG -> "The YSR would be locked for too long (more than 300 blocks)."
        Fehler.UNBESTAETIGT -> "The lock does not have enough confirmations yet. Please wait."
        Fehler.GUTHABEN -> "The lock does not hold enough."
        Fehler.WARTEND -> "A transaction on the lock is still waiting for a block."
        Fehler.ZU_SPAET -> "It is too late to claim – the deadline is too close."
        Fehler.ZU_FRUEH -> "The deadline has not been reached yet."
    }

    override fun stoerung(s: Stoerung) = when (s) {
        Stoerung.WOERTER_UNBRAUCHBAR -> "No key could be derived from the words in the vault."
        Stoerung.SCHLUESSEL_PASST_NICHT -> "The key does not match the address shown."
        Stoerung.GEGENPROBE -> "A check before sending failed."
    }

    override val offenTitel = "Open swaps"
    override val offenLeer = "No open swaps on this device."
    override val offenHinweis =
        "Stored on this device only. You can claim and reclaim from here even without otc.yskar.app."
    override fun rolle(r: Rolle) = when (r) {
        Rolle.KAEUFER -> "You buy YSR"
        Rolle.VERKAEUFER -> "You sell YSR"
    }
    override fun stand(s: Stand) = when (s) {
        Stand.GEMERKT -> "prepared"
        Stand.GESPERRT -> "locked"
        Stand.EINGELOEST -> "claimed"
        Stand.ZURUECK -> "reclaimed"
    }
    override val wartetAufSperre = "waiting for the lock"
    override val oeffnen = "Open"
}

/** Bloecke zu je rund zehn Minuten, als Stunden oder Minuten. */
private fun stunden(bloecke: Long): String {
    val min = bloecke * 10
    return if (min < 90) "$min Min." else "${(min + 30) / 60} Std."
}

private fun hours(bloecke: Long): String {
    val min = bloecke * 10
    return if (min < 90) "$min min" else "${(min + 30) / 60} h"
}
