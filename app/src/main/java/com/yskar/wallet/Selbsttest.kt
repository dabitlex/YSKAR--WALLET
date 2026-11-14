package com.yskar.wallet

import android.content.Context
import android.util.Log
import com.yskar.wallet.kern.Hex
import com.yskar.wallet.kern.Tx
import com.yskar.wallet.kern.Wallet
import com.yskar.wallet.sprache.Texte
import com.yskar.wallet.tresor.Bremse
import com.yskar.wallet.tresor.DateiAblage
import com.yskar.wallet.tresor.KeystorePfeffer
import com.yskar.wallet.tresor.Tresor
import com.yskar.wallet.tresor.TresorAkte

/**
 * Selbsttests, die nur auf dem Geraet etwas beweisen.
 *
 * Der Kern ist bereits im JVM-Unittest gegen die Vergleichswerte geprueft.
 * Was dort NICHT geprueft werden kann:
 *
 *   - rechnet BouncyCastles Ed25519 auf arm64 dasselbe,
 *   - funktioniert der Android Keystore als Pfeffer,
 *   - wie lange braucht PBKDF2 mit 400.000 Runden auf echter Hardware.
 *
 * Die verwendeten Merkwoerter sind der oeffentliche BIP39-Testvektor
 * "abandon … about". Dahinter liegt kein Guthaben. ECHTE Merkwoerter stehen
 * NIEMALS im Quellcode.
 *
 * Die Berichte sind fuer den Bildschirm geschrieben, also in der Sprache
 * der App: Die Namen der Zeilen und ihre Werte kommen aus
 * `sprache/SelbsttestTexte.kt`. WAS geprueft wird, haengt von keiner
 * Sprache ab.
 */
object Selbsttest {

    const val WOERTER =
        "abandon abandon abandon abandon abandon abandon " +
            "abandon abandon abandon abandon abandon about"

    const val ADRESSE = "ysr1pcy6c8vw2fpp49qh4pg3w348qz6nn6vw2fg7f0"

    private const val PUBKEY =
        "34792b244cfe851d8b3fc62ccb3033297ea2ca5bc31119b397bc3ec4132a751d"
    private const val TXID =
        "aaa2fded1b8ca1d782360bdc924566db8ce69b9dd7a9eb26e3fcd702c6334368"
    private const val ROH =
        "0100010e09ac1d8e52421a9417a8511746a700b539e98e417ac5b716bbeef1b0018eddbdd" +
            "f30319b4f4d0000f9029500000000cc0600000000000007000000000000000000000006" +
            "4b616666656534792b244cfe851d8b3fc62ccb3033297ea2ca5bc31119b397bc3ec4132" +
            "a751db98017c634e3cc8324d3e78c1552083299faeaa547a40219dec903f515cae4ab7f" +
            "f1a3cc7aba3ff51947deebb360ac0e82ecf6362758362f9362ff9623dbe001"

    /** Eigene Datei, damit ein spaeterer echter Tresor nie beruehrt wird. */
    private const val TEST_DATEI = "selbsttest.tresor.json"
    private val TEST_PIN = "123456".toCharArray()
    private val FALSCHE_PIN = "654321".toCharArray()

    const val TAG = "YskarSelbsttest"

    /**
     * Eine Zeile des Berichts. [eigen]: Der Wert gehoert zur Wallet auf
     * diesem Geraet (ihre Adresse, ihr Guthaben) und nicht zum Testvektor.
     * Er steht auf dem Bildschirm, aber NICHT im Log - dort stehen nur
     * Testwerte (REGELN.md, Regel 9).
     */
    data class Zeile(
        val ok: Boolean,
        val name: String,
        val wert: String,
        val eigen: Boolean = false,
    )

    data class Bericht(
        val zeilen: List<Zeile>,
        val dauerMs: Long,
    ) {
        val allesGruen: Boolean get() = zeilen.all { it.ok }
    }

    private class Sammler {
        val zeilen = ArrayList<Zeile>()
        fun pruefe(name: String, ist: String, soll: String) {
            zeilen.add(Zeile(ist == soll, name, ist))
        }

        fun melde(name: String, ok: Boolean, wert: String) {
            zeilen.add(Zeile(ok, name, wert))
        }
    }

    // ------------------------------------------------------------------ Kern

    fun kern(t: Texte): Bericht {
        val w = t.selbsttest
        val start = System.currentTimeMillis()
        val s = Sammler()
        try {
            val kp = Wallet.fromMnemonic(WOERTER)
            s.pruefe(w.kernAdresse, kp.address, ADRESSE)
            s.pruefe(w.kernOeffentlicherSchluessel, Hex.toHex(kp.publicKey), PUBKEY)

            val bytes = Hex.fromHex(ROH)
            val tx = Tx.deserializeTransfer(bytes)
            s.pruefe("txid", Tx.txidHex(tx), TXID)
            s.pruefe(w.kernLeitungsform, Hex.toHex(Tx.serialize(tx)), Hex.toHex(bytes))
            s.melde(w.kernSignaturGilt, Tx.verifyOwnSignature(tx), w.kernGeprueft)

            val neu = Tx.buildTransfer(
                from = tx.from, to = tx.to, amount = tx.amount, fee = tx.fee, nonce = tx.nonce,
                publicKey = kp.publicKey, privateKey = kp.privateKey,
                validUntil = tx.validUntil, memo = tx.memo, version = tx.version,
            )
            s.pruefe(w.kernNeuUnterschrieben, Hex.toHex(neu.signature), Hex.toHex(tx.signature))
            kp.wipe()
        } catch (e: Throwable) {
            s.melde(w.ausnahme, false, e::class.java.simpleName + ": " + e.message)
        }
        return Bericht(s.zeilen, System.currentTimeMillis() - start)
    }

    // ---------------------------------------------------------------- Tresor

    /**
     * Legt einen Testtresor an, oeffnet ihn, prueft die falsche PIN und
     * raeumt wieder auf. Benutzt den ECHTEN Keystore-Pfeffer - genau das
     * ist hier der Punkt.
     */
    fun tresor(context: Context, t: Texte): Bericht {
        val w = t.selbsttest
        val start = System.currentTimeMillis()
        val s = Sammler()
        val ablage = DateiAblage(context, TEST_DATEI)
        val pruefling = testTresor(context, ablage)

        try {
            ablage.loeschen()

            val t0 = System.currentTimeMillis()
            val ein = pruefling.einrichten(WOERTER, TEST_PIN)
            val einrichtenMs = System.currentTimeMillis() - t0
            s.melde(w.tresorEinrichten, ein is Tresor.Ergebnis.Offen, beschreibe(ein, t))
            s.melde(w.tresorDauerEinrichten, einrichtenMs < 5_000, "$einrichtenMs ms")

            s.pruefe(w.tresorAdresseOhnePin, pruefling.adresse() ?: "null", ADRESSE)

            val datei = ablage.lesen() ?: ""
            s.melde(
                w.tresorWoerterNichtInDatei,
                !datei.contains("abandon") && !datei.contains("123456"),
                w.tresorZeichenGeheimtext(datei.length),
            )
            s.melde(
                w.tresorRunden,
                runCatching { TresorAkte.ausJson(datei).runden }.getOrNull() == 400_000,
                runCatching { TresorAkte.ausJson(datei).runden.toString() }.getOrDefault("?"),
            )

            val t1 = System.currentTimeMillis()
            val auf = pruefling.oeffnen(TEST_PIN)
            val oeffnenMs = System.currentTimeMillis() - t1
            val woerterOk = auf is Tresor.Ergebnis.Offen &&
                String(auf.woerter, Charsets.UTF_8) == WOERTER
            if (auf is Tresor.Ergebnis.Offen) auf.woerter.fill(0)
            s.melde(w.tresorOeffnenRichtigePin, woerterOk, beschreibe(auf, t))
            s.melde(w.tresorDauerOeffnen, oeffnenMs < 5_000, "$oeffnenMs ms")

            val falsch = pruefling.oeffnen(FALSCHE_PIN)
            s.melde(
                w.tresorFalschePinAbgewiesen,
                falsch is Tresor.Ergebnis.FalschePin,
                beschreibe(falsch, t),
            )

            pruefling.loeschen()
            s.melde(w.aufgeraeumt, !pruefling.istEingerichtet(), w.tresorEntfernt)
        } catch (e: Throwable) {
            s.melde(w.ausnahme, false, e::class.java.simpleName + ": " + e.message)
            runCatching { ablage.loeschen() }
        }

        return Bericht(s.zeilen, System.currentTimeMillis() - start)
    }

    /** Fuer die Biometrie-Pruefung: derselbe Testtresor, aber ohne Aufraeumen. */
    fun testTresor(context: Context, ablage: DateiAblage = DateiAblage(context, TEST_DATEI)) =
        Tresor(
            ablage = ablage,
            pfeffer = KeystorePfeffer(),
            // Eigene Bremse im Arbeitsspeicher: Der Selbsttest soll die
            // echte Sperre des Nutzers nicht anfassen.
            bremse = Bremse(object : Bremse.Ablage {
                private var stand = Bremse.Stand()
                override fun lesen() = stand
                override fun schreiben(stand: Bremse.Stand) {
                    this.stand = stand
                }
            }),
        )

    fun testAblage(context: Context) = DateiAblage(context, TEST_DATEI)

    val testPin: CharArray get() = TEST_PIN.copyOf()

    /**
     * Den Bericht ins Logcat schreiben.
     *
     * Noetig, weil HyperOS "adb shell input" verbietet (INJECT_EVENTS) -
     * eine lange Ergebnisliste laesst sich darum nicht von aussen
     * durchblaettern. Ueber das Log kommt sie vollstaendig heraus.
     *
     * Hier stehen nur Testwerte und oeffentliche Adressen. Schluesselmaterial
     * gehoert NIE in ein Log.
     *
     * Der Rahmen ("BESTANDEN", "OK", "FEHLER") bleibt deutsch - das Log liest
     * der Entwickler, nicht der Besitzer. Die Zeilen selbst stehen so da,
     * wie der Bericht sie fuer den Bildschirm geschrieben hat.
     */
    fun protokolliere(abschnitt: String, b: Bericht) {
        Log.i(TAG, "=== $abschnitt: " +
            (if (b.allesGruen) "BESTANDEN" else "FEHLGESCHLAGEN") +
            " (${b.dauerMs} ms) ===")
        for (z in b.zeilen) {
            val wert = if (z.eigen) "(eigene Wallet - nicht im Log)" else z.wert
            Log.i(TAG, (if (z.ok) "OK     | " else "FEHLER | ") + z.name + " | " + wert)
        }
    }

    fun beschreibe(e: Tresor.Ergebnis, t: Texte): String = when (e) {
        is Tresor.Ergebnis.Offen -> t.selbsttest.standOffen(e.adresse)
        is Tresor.Ergebnis.FalschePin -> t.selbsttest.standFalschePin(e.fehlversuche)
        is Tresor.Ergebnis.Gesperrt -> t.selbsttest.standGesperrt(e.wartezeit)
        Tresor.Ergebnis.KeinTresor -> t.selbsttest.standKeinTresor
        Tresor.Ergebnis.BiometrieNeuEinrichten -> t.selbsttest.standBiometrieNeu
        is Tresor.Ergebnis.Fehler -> t.selbsttest.standFehler(e.grund)
    }
}
