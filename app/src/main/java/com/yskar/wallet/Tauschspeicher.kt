package com.yskar.wallet

import android.content.Context
import com.yskar.wallet.otc.Tauschbuch

/**
 * Wo die offenen OTC-Tausche liegen: eigene SharedPreferences dieser App,
 * nur auf diesem Geraet -- wie die Kontakte (`Kontaktspeicher`), aus
 * denselben Gruenden: kein Schluessel, kein Geheimnis, aber privat; kein
 * Backup (`allowBackup="false"`), kein Log, geloescht mit der Wallet
 * ([leeren]).
 *
 * Gelesen wird mit `Tauschbuch.ausText`, das jeden Eintrag einzeln prueft.
 * Und was daraus wieder ein Auftrag wird, geht ohnehin ganz durch
 * `Auftrag.lesen` (REGELN.md, Regel 43).
 */
class Tauschspeicher(context: Context) {

    private val p = context.applicationContext
        .getSharedPreferences("yskar.tausche", Context.MODE_PRIVATE)

    var buch: List<Tauschbuch.Eintrag>
        get() = Tauschbuch.ausText(p.getString("buch", "") ?: "")
        set(wert) = p.edit().putString("buch", Tauschbuch.alsText(wert)).apply()

    fun leeren() = p.edit().clear().apply()
}
