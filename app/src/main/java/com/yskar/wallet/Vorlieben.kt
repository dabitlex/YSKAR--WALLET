package com.yskar.wallet

import android.content.Context
import com.yskar.wallet.sprache.Sprache
import java.util.Locale

/**
 * Was der Besitzer eingestellt hat - und was der Waechter zuletzt sah.
 *
 * NICHTS HIER IST GEHEIM, und darum liegt es in gewoehnlichen
 * SharedPreferences statt im Tresor. Es steht kein Schluessel darin, keine
 * PIN und kein Wort. Die Adresse steht ohnehin schon in der Tresorakte und
 * ist oeffentlich.
 *
 * Der zweite Teil - [gesehenesGuthaben] und [geseheneTxids] - ist das
 * Gedaechtnis des Waechters. Ohne es koennte er nicht sagen, ob eine
 * Bewegung neu ist, und wuerde bei jedem Aufwachen dasselbe melden.
 */
class Vorlieben(context: Context) {

    private val p = context.applicationContext
        .getSharedPreferences("yskar.vorlieben", Context.MODE_PRIVATE)

    /**
     * Darf der Waechter im Hintergrund nachsehen?
     *
     * AUS, bis der Besitzer es einschaltet. Das ist kein Zoegern: Der
     * Waechter kostet Akku und fragt regelmaessig nach genau dieser
     * Adresse. Wer das nicht will, soll es nicht bekommen, ohne gefragt
     * worden zu sein.
     */
    var benachrichtigungen: Boolean
        get() = p.getBoolean("benachrichtigungen", false)
        set(wert) = p.edit().putBoolean("benachrichtigungen", wert).apply()

    /** Kurzes Rueckmelden am Ziffernfeld, beim Scannen, beim Senden. */
    var vibration: Boolean
        get() = p.getBoolean("vibration", true)
        set(wert) = p.edit().putBoolean("vibration", wert).apply()


    /**
     * Guthaben und Betraege auf dem Hauptbildschirm verbergen.
     *
     * Sichtschutz gegen den Blick ueber die Schulter, KEINE
     * Sicherheitsfunktion: Wer die entsperrte App in der Hand haelt, tippt
     * einmal auf das Auge. Darum darf es hier bei den Vorlieben liegen.
     */
    var guthabenVerborgen: Boolean
        get() = p.getBoolean("guthaben_verborgen", false)
        set(wert) = p.edit().putBoolean("guthaben_verborgen", wert).apply()

    /**
     * Der Zeitraum des Guthabenverlaufs auf dem Hauptbildschirm, in Tagen
     * (7, 30 oder 365). Eine Vorliebe wie das Auge darueber: Sie sagt nur,
     * was gezeigt wird.
     */
    var zeitraumTage: Int
        get() = p.getInt("zeitraum_tage", 30)
        set(wert) = p.edit().putInt("zeitraum_tage", wert).apply()

    /**
     * Die gewaehlte Sprache als Kennung ("de", "en") - null, solange niemand
     * gewaehlt hat. Dann richtet sich die App nach der Sprache des Geraets
     * (`Sprache.fuerSystem`). Bleibt stehen, wenn die Wallet entfernt wird:
     * Die Sprache gehoert zum Geraet und zu dem, der es haelt.
     */
    var sprache: String?
        get() = p.getString("sprache", null)
        set(wert) = p.edit().putString("sprache", wert).apply()

    /** Die Sprache, die gilt: die gewaehlte, sonst die des Geraets. */
    val geltendeSprache: Sprache
        get() = Sprache.ausKennung(sprache) ?: Sprache.fuerSystem(Locale.getDefault())

    // ----------------------------------------- Der zuletzt bekannte Stand
    //
    // Was die App im Vordergrund zuletzt vom Spiegel gehoert hat, und wann.
    // Damit steht nach dem Entsperren sofort eine Zahl da - beschriftet mit
    // ihrem Alter -, statt eines Strichs, bis das Netz antwortet.
    //
    // NUR ZUM ANZEIGEN. Gesendet wird nie gegen diesen Wert: `Senden.pruefe`
    // bekommt das frisch geladene Konto oder gar keins.
    //
    // Getrennt vom Gedaechtnis des Waechters darunter, obwohl dort auch ein
    // Guthaben steht: Jenes sagt "schon gemeldet", dieses "zuletzt gesehen".
    // Zwei Bedeutungen in einem Feld waeren der naechste Fehler.

    /** Guthaben in kleinsten Einheiten; -1, solange nichts bekannt ist. */
    val standGuthaben: Long
        get() = p.getLong("stand_guthaben", -1L)

    /** Unixsekunden nach der Uhr dieses Geraets; 0, solange nichts bekannt ist. */
    val standZeit: Long
        get() = p.getLong("stand_zeit", 0L)

    fun standMerken(guthaben: Long, unixSekunden: Long) {
        p.edit()
            .putLong("stand_guthaben", guthaben)
            .putLong("stand_zeit", unixSekunden)
            .apply()
    }
    // ------------------------------------------- Gedaechtnis des Waechters

    var gesehenesGuthaben: Long
        get() = p.getLong("gesehenes_guthaben", -1L)
        set(wert) = p.edit().putLong("gesehenes_guthaben", wert).apply()

    /**
     * Die Kennungen, die der Waechter schon gemeldet hat.
     *
     * Begrenzt auf [MERKGRENZE] Stueck: Eine Wallet mit langem Verlauf
     * soll diese Liste nicht endlos wachsen lassen. Mehr als die letzten
     * paar Dutzend braucht niemand - aeltere Bewegungen tauchen nicht
     * ploetzlich als neu wieder auf.
     */
    var geseheneTxids: Set<String>
        get() = p.getStringSet("gesehene_txids", emptySet()) ?: emptySet()
        set(wert) {
            p.edit().putStringSet("gesehene_txids", wert.take(MERKGRENZE).toSet()).apply()
        }

    /**
     * Alles vergessen, was von dieser Adresse gesehen wurde - das Gedaechtnis
     * des Waechters und den zuletzt bekannten Stand.
     *
     * Noetig beim Entfernen der Wallet UND beim Wiederherstellen einer
     * anderen: Sonst gaelte der Verlauf der alten Adresse als "schon
     * gesehen", und die erste echte Bewegung der neuen bliebe stumm.
     */
    fun gedaechtnisLeeren() {
        p.edit()
            .remove("gesehenes_guthaben")
            .remove("gesehene_txids")
            .remove("stand_guthaben")
            .remove("stand_zeit")
            .apply()
    }

    private companion object {
        const val MERKGRENZE = 64
    }
}
