package com.yskar.wallet

import androidx.fragment.app.FragmentActivity
import com.yskar.wallet.sprache.Texte
import com.yskar.wallet.tresor.BioSchluessel
import com.yskar.wallet.tresor.Tresor
import com.yskar.wallet.ui.SensorFrage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Pruefung des Biometrie-Entsperrens auf dem Geraet.
 *
 * Der ganze Weg, einmal durchgespielt: Testtresor anlegen, Biometrie
 * einschalten (Sensor), Tresor allein mit dem Fingerabdruck wieder oeffnen
 * (Sensor), Woerter vergleichen, aufraeumen.
 *
 * Es sind ZWEI Bestaetigungen noetig, und das ist kein Umweg: Der
 * Keystore-Schluessel verlangt die Bestaetigung bei JEDER Benutzung - einmal
 * zum Umschliessen des Tresorschluessels, einmal zum Oeffnen. Genau dieses
 * Verhalten soll der Test belegen.
 *
 * Der Dialog selbst kommt aus [SensorFrage] - dieselbe Stelle, die auch
 * der Sperrbildschirm und die Einstellungen benutzen. Ein zweiter,
 * leicht abweichender Aufruf waere genau die Art von Doppelung, bei der
 * spaeter eine Haelfte repariert wird und die andere nicht.
 */
object BiometriePruefung {

    suspend fun pruefen(activity: FragmentActivity, t: Texte): Selbsttest.Bericht {
        val w = t.selbsttest
        val start = System.currentTimeMillis()
        val zeilen = ArrayList<Selbsttest.Zeile>()
        fun melde(name: String, ok: Boolean, wert: String) {
            zeilen.add(Selbsttest.Zeile(ok, name, wert))
        }

        val hindernis = SensorFrage.hindernis(activity, t)
        if (hindernis != null) {
            melde(w.bioSensorVerfuegbar, false, hindernis)
            return Selbsttest.Bericht(zeilen, System.currentTimeMillis() - start)
        }
        melde(w.bioSensorVerfuegbar, true, w.bioStarkeBiometrie)

        val ablage = Selbsttest.testAblage(activity)
        val tresor = Selbsttest.testTresor(activity, ablage)
        val bio = BioSchluessel()

        try {
            withContext(Dispatchers.Default) {
                ablage.loeschen()
                tresor.einrichten(Selbsttest.WOERTER, Selbsttest.testPin)
            }

            // --- Einschalten -------------------------------------------------
            val zumSchliessen = bio.zumVerschluesseln()
            if (zumSchliessen !is BioSchluessel.Stand.Bereit) {
                melde(w.bioSchluesselAnlegen, false, zumSchliessen.toString())
                return fertig(zeilen, start, tresor, bio, t)
            }

            val a1 = SensorFrage.frage(activity, w.bioFrageEinschalten, zumSchliessen.cipher, t)
            val c1 = (a1 as? SensorFrage.Antwort.Ok)?.cipher
            if (c1 == null) {
                melde(
                    w.bioBestaetigung(1),
                    false,
                    (a1 as? SensorFrage.Antwort.Abbruch)?.text ?: w.bioAbgebrochen,
                )
                return fertig(zeilen, start, tresor, bio, t)
            }
            melde(w.bioBestaetigungEinschalten, true, w.bioBestaetigt)

            val ein = withContext(Dispatchers.Default) {
                tresor.bioEinschalten(Selbsttest.testPin, c1)
            }
            melde(
                w.bioSchluesselUmschlossen,
                ein is Tresor.Ergebnis.Offen,
                Selbsttest.beschreibe(ein, t),
            )
            melde(w.bioEingeschaltet, tresor.bioEingeschaltet(), w.bioInAkteVermerkt)

            // --- Oeffnen ------------------------------------------------------
            val iv = tresor.bioIv()
            if (iv == null) {
                melde(w.bioIvInAkte, false, w.bioFehlt)
                return fertig(zeilen, start, tresor, bio, t)
            }

            val zumOeffnen = bio.zumEntschluesseln(iv)
            if (zumOeffnen !is BioSchluessel.Stand.Bereit) {
                melde(w.bioSchluesselLaden, false, zumOeffnen.toString())
                return fertig(zeilen, start, tresor, bio, t)
            }

            val a2 = SensorFrage.frage(activity, w.bioFrageOeffnen, zumOeffnen.cipher, t)
            val c2 = (a2 as? SensorFrage.Antwort.Ok)?.cipher
            if (c2 == null) {
                melde(
                    w.bioBestaetigung(2),
                    false,
                    (a2 as? SensorFrage.Antwort.Abbruch)?.text ?: w.bioAbgebrochen,
                )
                return fertig(zeilen, start, tresor, bio, t)
            }
            melde(w.bioBestaetigungOeffnen, true, w.bioBestaetigt)

            val auf = withContext(Dispatchers.Default) { tresor.bioOeffnen(c2) }
            val stimmt = auf is Tresor.Ergebnis.Offen &&
                String(auf.woerter, Charsets.UTF_8) == Selbsttest.WOERTER
            if (auf is Tresor.Ergebnis.Offen) auf.woerter.fill(0)
            melde(w.bioOeffnenOhnePin, stimmt, Selbsttest.beschreibe(auf, t))
            melde(
                w.bioWoerterStimmen,
                stimmt,
                if (stimmt) w.bioZwoelfWoerterZurueck else w.bioNein,
            )
        } catch (e: Throwable) {
            melde(w.ausnahme, false, e::class.java.simpleName + ": " + e.message)
        }

        return fertig(zeilen, start, tresor, bio, t)
    }

    private fun fertig(
        zeilen: MutableList<Selbsttest.Zeile>,
        start: Long,
        tresor: Tresor,
        bio: BioSchluessel,
        t: Texte,
    ): Selbsttest.Bericht {
        runCatching {
            tresor.loeschen()
            bio.loeschen()
        }
        zeilen.add(Selbsttest.Zeile(true, t.selbsttest.aufgeraeumt, t.selbsttest.bioEntfernt))
        return Selbsttest.Bericht(zeilen, System.currentTimeMillis() - start)
    }
}
