package com.yskar.wallet.tresor

import android.content.Context
import java.io.File
import java.io.FileOutputStream

/**
 * Die geraetegebundenen Haelften von [Tresor] und [Bremse].
 *
 * Alles hier benutzt Android-API und laeuft darum nicht im JVM-Unittest.
 * Entsprechend wenig Logik steht hier drin: Dateien und Einstellungen,
 * sonst nichts.
 */

/**
 * Die Tresorakte als Datei im privaten Verzeichnis der App.
 *
 * ATOMAR SCHREIBEN. Erst in eine Nebendatei, dann umbenennen. Ein Absturz
 * mitten im Schreiben hinterlaesst sonst eine halbe Tresordatei - und damit
 * eine Wallet, die nur noch ueber die zwoelf Woerter zurueckkommt. Das ist
 * der Aufwand von drei Zeilen wert.
 *
 * Die Datei ist nicht zusaetzlich verschluesselt, und das ist kein
 * Versehen: Ihr Inhalt IST der Geheimtext. Ohne PIN und ohne den Pfeffer
 * aus dem Keystore dieses Geraets ist sie Rauschen.
 */
class DateiAblage(
    context: Context,
    dateiname: String = "tresor.json",
) : Tresor.AktenAblage {

    private val datei = File(context.filesDir, dateiname)
    private val neben = File(context.filesDir, "$dateiname.neu")

    override fun lesen(): String? =
        if (datei.exists()) runCatching { datei.readText(Charsets.UTF_8) }.getOrNull() else null

    override fun schreiben(text: String) {
        FileOutputStream(neben).use { aus ->
            aus.write(text.toByteArray(Charsets.UTF_8))
            aus.flush()
            // Bis auf den Datentraeger durchschreiben, bevor umbenannt wird.
            aus.fd.sync()
        }
        if (!neben.renameTo(datei)) {
            neben.delete()
            throw IllegalStateException("Tresor konnte nicht geschrieben werden")
        }
    }

    override fun loeschen() {
        datei.delete()
        neben.delete()
    }
}

/**
 * Fehlzahl und Sperrzeit in den App-Einstellungen.
 *
 * Nicht geheim, nur laestig zu umgehen: Wer die App-Daten loeschen kann,
 * loescht damit auch den Tresor. Die Bremse schuetzt das entsperrte
 * Telefon in fremder Hand, nicht gegen einen Angreifer mit Root-Zugriff.
 */
class PrefsBremseAblage(context: Context) : Bremse.Ablage {

    private val prefs = context.getSharedPreferences("yskar.bremse", Context.MODE_PRIVATE)

    override fun lesen(): Bremse.Stand =
        Bremse.Stand(
            fehl = prefs.getInt("fehl", 0),
            bis = prefs.getLong("bis", 0L),
        )

    override fun schreiben(stand: Bremse.Stand) {
        prefs.edit().putInt("fehl", stand.fehl).putLong("bis", stand.bis).apply()
    }
}

/** Den einsatzbereiten Tresor dieses Geraets bauen. */
fun tresorFuer(context: Context): Tresor {
    val app = context.applicationContext
    return Tresor(
        ablage = DateiAblage(app),
        pfeffer = KeystorePfeffer(),
        bremse = Bremse(PrefsBremseAblage(app)),
    )
}
