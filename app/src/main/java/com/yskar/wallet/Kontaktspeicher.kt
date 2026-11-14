package com.yskar.wallet

import android.content.Context
import com.yskar.wallet.kontakte.Kontakt
import com.yskar.wallet.kontakte.Kontakte

/**
 * Wo die Kontakte liegen: in eigenen SharedPreferences dieser App, auf
 * diesem Geraet und nirgends sonst.
 *
 * NICHT IM TRESOR, und das ist ueberlegt: Kontakte sind keine Schluessel.
 * Wer sie liest, kann nichts abheben. Sie sagen aber, mit wem der Besitzer
 * zu tun hat - darum liegen sie im privaten Speicher der App (fuer andere
 * Apps nicht lesbar), gehen in kein Backup (`allowBackup="false"` im
 * Manifest), stehen in keinem Log und werden geloescht, wenn die Wallet
 * vom Geraet entfernt wird ([leeren]).
 *
 * Eine eigene Datei statt eines Felds in `Vorlieben`: Die Vorlieben liest
 * auch der Waechter im Hintergrund. Der braucht keine Kontakte und soll
 * sie nicht in die Hand bekommen.
 *
 * Gelesen wird mit `Kontakte.ausText`, das jede Zeile prueft wie eine neue
 * Eingabe. Eine beschaedigte Datei ergibt weniger Kontakte, nie falsche.
 */
class Kontaktspeicher(context: Context) {

    private val p = context.applicationContext
        .getSharedPreferences("yskar.kontakte", Context.MODE_PRIVATE)

    var liste: List<Kontakt>
        get() = Kontakte.ausText(p.getString("liste", "") ?: "")
        set(wert) = p.edit().putString("liste", Kontakte.alsText(wert)).apply()

    fun leeren() = p.edit().clear().apply()
}
