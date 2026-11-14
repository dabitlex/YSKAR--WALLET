package com.yskar.wallet.sprache

/**
 * Texte: Mitteilungen des Waechterdienstes.
 *
 * Der Dienst laeuft ohne Oberflaeche. Er liest die Sprache aus den
 * Vorlieben, nicht aus einer `CompositionLocal`.
 */
interface WaechterTexte {
    val zahlungEingegangen: String
    val ueberweisungBestaetigt: String

    /** Haengt an den Betrag: " von ysr1abc…def". Mit fuehrendem Leerzeichen. */
    fun von(adresse: String): String

    /** Haengt an den Betrag: " an ysr1abc…def". Mit fuehrendem Leerzeichen. */
    fun an(adresse: String): String

    // Der Kanal, wie er in den Android-Einstellungen heisst.
    val kanalName: String
    val kanalBeschreibung: String
}

object WaechterDe : WaechterTexte {
    override val zahlungEingegangen = "Zahlung eingegangen"
    override val ueberweisungBestaetigt = "Überweisung bestätigt"
    override fun von(adresse: String) = " von $adresse"
    override fun an(adresse: String) = " an $adresse"
    override val kanalName = "Zahlungen"
    override val kanalBeschreibung = "Eingänge und bestätigte Ausgänge dieser Wallet."
}

object WaechterEn : WaechterTexte {
    override val zahlungEingegangen = "Payment received"
    override val ueberweisungBestaetigt = "Transfer confirmed"
    override fun von(adresse: String) = " from $adresse"
    override fun an(adresse: String) = " to $adresse"
    override val kanalName = "Payments"
    override val kanalBeschreibung = "Incoming payments and confirmed outgoing transfers of this wallet."
}
