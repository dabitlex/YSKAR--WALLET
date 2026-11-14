package com.yskar.wallet.tresor

import com.yskar.wallet.kern.Bip39
import com.yskar.wallet.kern.Wallet
import java.security.SecureRandom
import java.time.Instant
import javax.crypto.Cipher

/**
 * Der Tresor - die zwoelf Woerter, verschluesselt auf diesem Geraet.
 *
 * Diese Klasse kennt keine Android-API. Alles Geraetegebundene steckt
 * hinter zwei schmalen Schnittstellen: [AktenAblage] fuer die Datei und
 * [Pfeffer] fuer den Keystore. Dadurch laesst sich der gesamte Ablauf -
 * Einrichten, Oeffnen, falsche PIN, Bremse, PIN aendern, Biometrie - im
 * JVM-Unittest pruefen und nicht nur auf dem Telefon.
 *
 * Der Schluessel entsteht aus PIN UND Pfeffer (siehe [Umschlag]). Die PIN
 * wird nirgends gespeichert, auch nicht fuer die Biometrie.
 */
class Tresor(
    private val ablage: AktenAblage,
    private val pfeffer: Pfeffer,
    private val bremse: Bremse,
    private val zufall: SecureRandom = SecureRandom(),
    /**
     * Runden fuer NEUE Tresore. Ein bestehender Tresor nennt seine eigene
     * Zahl in der Akte - damit kann dieser Wert spaeter steigen, ohne alte
     * Tresore unlesbar zu machen. Der Unittest setzt ihn herunter, sonst
     * dauerte jeder einzelne Testfall eine halbe Sekunde.
     */
    private val runden: Int = Umschlag.RUNDEN,
    private val jetzt: () -> String = { Instant.now().toString() },
) {

    /** Wo die Tresorakte liegt. */
    interface AktenAblage {
        fun lesen(): String?
        fun schreiben(text: String)
        fun loeschen()
    }

    sealed interface Ergebnis {
        /**
         * Offen. [woerter] ist UTF-8 und sollte nach Gebrauch mit
         * `woerter.fill(0)` ueberschrieben werden.
         */
        class Offen(val woerter: ByteArray, val adresse: String) : Ergebnis

        /** PIN falsch. [wartezeit] in Sekunden, 0 = sofort wieder erlaubt. */
        data class FalschePin(val fehlversuche: Int, val wartezeit: Long) : Ergebnis

        data class Gesperrt(val wartezeit: Long) : Ergebnis

        data object KeinTresor : Ergebnis

        /** Biometrie muss neu eingerichtet werden; der Tresor ist unversehrt. */
        data object BiometrieNeuEinrichten : Ergebnis

        data class Fehler(val grund: String) : Ergebnis
    }

    fun istEingerichtet(): Boolean = akte() != null

    /** Die oeffentliche Adresse, ohne den Tresor zu oeffnen. */
    fun adresse(): String? = akte()?.adresse

    fun bioEingeschaltet(): Boolean = akte()?.bio != null

    private fun akte(): TresorAkte? {
        val roh = ablage.lesen() ?: return null
        return runCatching { TresorAkte.ausJson(roh) }.getOrNull()
    }

    /**
     * Warum die Akte nicht lesbar ist - fuer den Fall, dass eine Datei da
     * ist, aber nicht passt. Dann darf die App NICHT so tun, als haette es
     * nie eine Wallet gegeben.
     */
    fun aktenFehler(): String? {
        val roh = ablage.lesen() ?: return null
        return runCatching { TresorAkte.ausJson(roh); null }
            .getOrElse { it.message ?: it::class.java.simpleName }
    }

    // ------------------------------------------------------------ Einrichten

    /**
     * Woerter pruefen, verschluesseln, ablegen.
     *
     * Ueberschreibt einen bestehenden Tresor NICHT - das muesste ein
     * bewusstes [loeschen] sein. Sonst reichte ein Fehlgriff in der
     * Oberflaeche, um eine Wallet zu verlieren.
     */
    fun einrichten(woerter: String, pin: CharArray): Ergebnis {
        if (istEingerichtet()) {
            return Ergebnis.Fehler("Es gibt bereits einen Tresor auf diesem Geraet")
        }
        if (!Bip39.isValid(woerter)) {
            return Ergebnis.Fehler("Merkwoerter ungueltig - Pruefsumme stimmt nicht")
        }
        if (pin.size < 4) return Ergebnis.Fehler("PIN zu kurz")

        val sauber = Bip39.normalize(woerter)
        val kp = Wallet.fromMnemonic(sauber)
        val adresse = kp.address
        kp.wipe()

        val salz = ByteArray(Umschlag.SALZ_BYTES).also { zufall.nextBytes(it) }
        val iv = ByteArray(Umschlag.IV_BYTES).also { zufall.nextBytes(it) }

        val vk = Umschlag.tresorSchluessel(pin, salz, pfeffer.pfeffer(salz), runden)
        val klartext = sauber.toByteArray(Charsets.UTF_8)
        try {
            val geheim = Umschlag.versiegeln(vk, iv, klartext)
            schreiben(
                TresorAkte(
                    version = Umschlag.VERSION,
                    salz = salz,
                    iv = iv,
                    geheim = geheim,
                    runden = runden,
                    adresse = adresse,
                    erstellt = jetzt(),
                )
            )
        } catch (e: Exception) {
            return Ergebnis.Fehler(e.message ?: e::class.java.simpleName)
        } finally {
            vk.fill(0)
            klartext.fill(0)
        }

        bremse.erfolg()
        return Ergebnis.Offen(sauber.toByteArray(Charsets.UTF_8), adresse)
    }

    // --------------------------------------------------------------- Oeffnen

    fun oeffnen(pin: CharArray): Ergebnis {
        val a = akte() ?: return ergebnisOhneAkte()

        val warte = bremse.wartezeit()
        if (warte > 0) return Ergebnis.Gesperrt(warte)

        val vk = Umschlag.tresorSchluessel(pin, a.salz, pfeffer.pfeffer(a.salz), a.runden)
        val klar = try {
            Umschlag.oeffnen(vk, a.iv, a.geheim)
        } finally {
            vk.fill(0)
        }

        if (klar == null) {
            val sek = bremse.fehlversuch()
            return Ergebnis.FalschePin(bremse.fehlversuche(), sek)
        }

        bremse.erfolg()
        return Ergebnis.Offen(klar, a.adresse)
    }

    private fun ergebnisOhneAkte(): Ergebnis {
        val fehler = aktenFehler()
        return if (fehler == null) Ergebnis.KeinTresor else Ergebnis.Fehler(fehler)
    }

    // ------------------------------------------------------------- Biometrie

    /**
     * Biometrie einschalten.
     *
     * [cipher] muss ein ueber BiometricPrompt bestaetigter
     * Verschluesselungs-Cipher aus [BioSchluessel.zumVerschluesseln] sein.
     * Die PIN wird hier gebraucht, um den Tresorschluessel zu gewinnen -
     * gespeichert wird sie nicht.
     */
    fun bioEinschalten(pin: CharArray, cipher: Cipher): Ergebnis {
        val a = akte() ?: return ergebnisOhneAkte()

        val vk = Umschlag.tresorSchluessel(pin, a.salz, pfeffer.pfeffer(a.salz), a.runden)
        try {
            // Erst pruefen, ob die PIN ueberhaupt stimmt: Sonst umschloessen
            // wir einen falschen Schluessel und die Biometrie oeffnete nie.
            val probe = Umschlag.oeffnen(vk, a.iv, a.geheim)
                ?: run {
                    val sek = bremse.fehlversuch()
                    return Ergebnis.FalschePin(bremse.fehlversuche(), sek)
                }
            probe.fill(0)

            val huelle = cipher.doFinal(vk)
            val iv = cipher.iv ?: return Ergebnis.Fehler("Cipher liefert keinen IV")
            schreiben(a.copy(bio = TresorAkte.BioHuelle(iv = iv, geheim = huelle)))
        } catch (e: Exception) {
            return Ergebnis.Fehler(e.message ?: e::class.java.simpleName)
        } finally {
            vk.fill(0)
        }

        bremse.erfolg()
        return Ergebnis.Offen(ByteArray(0), a.adresse)
    }

    fun bioAusschalten() {
        val a = akte() ?: return
        if (a.bio != null) schreiben(a.copy(bio = null))
    }

    /** Der IV, den [BioSchluessel.zumEntschluesseln] braucht. */
    fun bioIv(): ByteArray? = akte()?.bio?.iv

    /**
     * Mit Biometrie oeffnen. [cipher] muss der ueber BiometricPrompt
     * bestaetigte Entschluesselungs-Cipher sein.
     */
    fun bioOeffnen(cipher: Cipher): Ergebnis {
        val a = akte() ?: return ergebnisOhneAkte()
        val bio = a.bio ?: return Ergebnis.BiometrieNeuEinrichten

        var vk: ByteArray? = null
        try {
            vk = cipher.doFinal(bio.geheim)
            val klar = Umschlag.oeffnen(vk, a.iv, a.geheim)
                ?: return Ergebnis.Fehler("Tresorschluessel passt nicht zum Tresor")
            return Ergebnis.Offen(klar, a.adresse)
        } catch (e: Exception) {
            return Ergebnis.Fehler(e.message ?: e::class.java.simpleName)
        } finally {
            vk?.fill(0)
        }
    }

    // ------------------------------------------------------------ PIN aendern

    /**
     * Neue PIN. Schreibt den Tresor mit frischem Salz und IV neu.
     *
     * Eine eingeschaltete Biometrie wird dabei abgeschaltet: Die alte
     * Huelle enthaelt den alten Tresorschluessel. Sie danach einfach
     * stehenzulassen waere ein stiller Zweitschluessel zur alten PIN.
     */
    fun pinAendern(alt: CharArray, neu: CharArray): Ergebnis {
        if (neu.size < 4) return Ergebnis.Fehler("PIN zu kurz")

        val offen = oeffnen(alt)
        if (offen !is Ergebnis.Offen) return offen

        val a = akte() ?: return Ergebnis.KeinTresor
        val salz = ByteArray(Umschlag.SALZ_BYTES).also { zufall.nextBytes(it) }
        val iv = ByteArray(Umschlag.IV_BYTES).also { zufall.nextBytes(it) }
        val vk = Umschlag.tresorSchluessel(neu, salz, pfeffer.pfeffer(salz), runden)
        try {
            val geheim = Umschlag.versiegeln(vk, iv, offen.woerter)
            schreiben(
                a.copy(
                    salz = salz,
                    iv = iv,
                    geheim = geheim,
                    runden = runden,
                    bio = null,
                )
            )
        } catch (e: Exception) {
            return Ergebnis.Fehler(e.message ?: e::class.java.simpleName)
        } finally {
            vk.fill(0)
        }
        return offen
    }

    // --------------------------------------------------------------- Loeschen

    /**
     * Tresor entfernen.
     *
     * Danach ist die Wallet auf diesem Geraet weg und NUR ueber die zwoelf
     * Woerter wiederherstellbar. Das gehoert so deutlich in die Oberflaeche.
     */
    fun loeschen() {
        ablage.loeschen()
        bremse.erfolg()
    }

    private fun schreiben(a: TresorAkte) {
        ablage.schreiben(a.alsJson())
    }
}
