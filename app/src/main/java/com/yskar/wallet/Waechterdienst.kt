package com.yskar.wallet

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.yskar.wallet.netz.Betrag
import com.yskar.wallet.netz.Waechter
import com.yskar.wallet.netz.spiegelMainnet
import com.yskar.wallet.sprache.texteFuer
import com.yskar.wallet.tresor.tresorFuer
import com.yskar.wallet.ui.Mitteilungsziel
import com.yskar.wallet.ui.adresseKurz
import java.util.concurrent.TimeUnit

/**
 * Nachsehen, waehrend die App zu ist.
 *
 * KEIN PUSH, UND DAS IST DER PUNKT. Firebase Cloud Messaging wuerde
 * bedeuten: Ein Server kennt die Adresse dieses Geraets und beobachtet sie.
 * Diese Wallet redet mit genau einem Gegenueber - dem Spiegel -, und
 * niemand sonst weiss, welche Adresse hier liegt. Also wacht das Geraet
 * selbst auf und fragt denselben Spiegel, den die App ohnehin fragt.
 *
 * WAS ER DAFUER BRAUCHT, IST NUR DIE ADRESSE. Die steht unverschluesselt in
 * der Tresorakte, weil sie oeffentlich ist. Der Waechter oeffnet keinen
 * Tresor, kennt keine PIN und sieht kein Wort. Er koennte nichts
 * unterschreiben, selbst wenn er wollte.
 *
 * WAS ER KOSTET, UND WARUM ER AUS IST: Akku und ein regelmaessiger Abruf
 * genau dieser Adresse beim Spiegel. Das ist eine Entscheidung des
 * Besitzers, keine Voreinstellung - siehe [Vorlieben.benachrichtigungen].
 *
 * Fuenfzehn Minuten sind nicht gewaehlt, sondern die Untergrenze von
 * WorkManager. Bei einer Blockzeit von rund zehn Minuten passt das: Wer es
 * genauer wissen will, oeffnet die App, und die fragt alle dreissig
 * Sekunden.
 */
class Waechterdienst(
    context: Context,
    parameter: WorkerParameters,
) : CoroutineWorker(context, parameter) {

    override suspend fun doWork(): Result {
        val ctx = applicationContext
        val vorlieben = Vorlieben(ctx)
        if (!vorlieben.benachrichtigungen) return Result.success()

        val adresse = tresorFuer(ctx).adresse() ?: return Result.success()

        val konto = try {
            spiegelMainnet().konto(adresse)
        } catch (e: Exception) {
            // Ein Funkloch ist kein Fehler, sondern der Normalfall eines
            // Telefons. Beim naechsten Mal wieder.
            return Result.retry()
        }

        val befund = Waechter.pruefe(
            konto,
            Waechter.Gedaechtnis(vorlieben.gesehenesGuthaben, vorlieben.geseheneTxids),
        )

        // ERST MERKEN, DANN MELDEN waere falsch herum - aber andersherum
        // auch: Faellt das Melden aus, soll es nicht doppelt kommen. Darum
        // merken wir sofort und nehmen in Kauf, dass eine verpasste
        // Mitteilung verpasst bleibt. Die Bewegung steht ja im Verlauf.
        vorlieben.gesehenesGuthaben = befund.gedaechtnis.guthaben
        vorlieben.geseheneTxids = befund.gedaechtnis.txids

        for (m in befund.meldungen.take(HOECHSTENS_AUF_EINMAL)) melde(ctx, m)
        return Result.success()
    }

    private fun melde(ctx: Context, m: Waechter.Meldung) {
        kanalAnlegen(ctx)

        // Die Sprache der App, nicht die des Telefons: dieselbe, in der die
        // Bewegung nachher im Verlauf steht.
        val t = texteFuer(Vorlieben(ctx).geltendeSprache)

        val wohin = if (m.gegenseite == null) ""
        else if (m.eingehend) t.waechter.von(adresseKurz(m.gegenseite))
        else t.waechter.an(adresseKurz(m.gegenseite))

        val bau = NotificationCompat.Builder(ctx, KANAL)
            .setSmallIcon(R.drawable.symbol_mitteilung)
            .setContentTitle(
                if (m.eingehend) t.waechter.zahlungEingegangen
                else t.waechter.ueberweisungBestaetigt,
            )
            .setContentText(
                (if (m.eingehend) "+" else "−") +
                    Betrag.genau(m.betrag, t.locale) + " YSR" + wohin,
            )
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(oeffnen(ctx, m))
            .setAutoCancel(true)

        // Ohne die Erlaubnis wirft `notify` nicht, es passiert nur nichts.
        // Trotzdem abfangen: Hersteller halten sich nicht immer daran.
        // Nummer je EINTRAG, nicht je txid: Eine Ueberweisung an sich selbst
        // bringt zwei Mitteilungen, und die zweite ersetzte sonst die erste.
        runCatching {
            NotificationManagerCompat.from(ctx).notify(m.schluessel.hashCode(), bau.build())
        }
    }

    /**
     * Was ein Tipp auf die Mitteilung tut: die App oeffnen und - nach dem
     * Entsperren - genau diese Zahlung zeigen ([Mitteilungsziel]).
     *
     * Ausdruecklich an MainActivity gerichtet und unveraenderlich
     * (`FLAG_IMMUTABLE`): Niemand kann die Absicht unterwegs umschreiben.
     * Die Anfragenummer ist je Eintrag verschieden, sonst teilten sich zwei
     * Mitteilungen eine PendingIntent und oeffneten dieselbe Zahlung.
     */
    private fun oeffnen(ctx: Context, m: Waechter.Meldung): PendingIntent {
        val absicht = Intent(ctx, MainActivity::class.java)
            .setAction(Mitteilungsziel.AKTION)
            .putExtra(Mitteilungsziel.SCHLUESSEL, m.schluessel)
        return PendingIntent.getActivity(
            ctx,
            m.schluessel.hashCode(),
            absicht,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    companion object {
        private const val KANAL = "yskar.bewegungen"
        private const val ARBEIT = "yskar.waechter"
        private const val HOECHSTENS_AUF_EINMAL = 5

        /**
         * Den Waechter einschalten oder abstellen.
         *
         * `KEEP` statt `UPDATE`: Ein bereits laufender Plan soll beim
         * Oeffnen der Einstellungen nicht jedes Mal neu beginnen - sonst
         * verschoebe sich der naechste Lauf immer weiter nach hinten.
         */
        fun planen(context: Context, an: Boolean) {
            val wm = WorkManager.getInstance(context.applicationContext)
            if (!an) {
                wm.cancelUniqueWork(ARBEIT)
                return
            }
            val auftrag = PeriodicWorkRequestBuilder<Waechterdienst>(15, TimeUnit.MINUTES)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build(),
                )
                .build()
            wm.enqueueUniquePeriodicWork(ARBEIT, ExistingPeriodicWorkPolicy.KEEP, auftrag)
        }

        /**
         * Den Kanal anlegen.
         *
         * Ab Android 8 muss er existieren, bevor die erste Mitteilung
         * kommt - sonst wird sie stillschweigend verworfen.
         *
         * Ein zweiter Aufruf mit demselben Kanal aendert Name und
         * Beschreibung - so wechselt der Kanal die Sprache mit der App.
         */
        fun kanalAnlegen(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val t = texteFuer(Vorlieben(context).geltendeSprache)
            val kanal = NotificationChannel(
                KANAL,
                t.waechter.kanalName,
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = t.waechter.kanalBeschreibung
            }
            context.getSystemService(NotificationManager::class.java)
                ?.createNotificationChannel(kanal)
        }
    }
}
