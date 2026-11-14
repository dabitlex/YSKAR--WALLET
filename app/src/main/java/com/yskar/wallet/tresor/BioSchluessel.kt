package com.yskar.wallet.tresor

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Der Schluessel hinter dem Fingerabdruck.
 *
 * Ein AES-256-GCM-Schluessel im Android Keystore, der NUR nach einer
 * Bestaetigung am Sensor benutzbar ist. Er umschliesst den Tresorschluessel.
 *
 * WAS HIER ANDERS IST ALS IN DER VORLAGE. Die bestehende Wallet legt bei
 * aktiver Biometrie die PIN im geschuetzten Speicher ab und liest sie nach
 * der Bestaetigung aus (wallet/biometrie.ts). Das ist ehrlich dokumentiert,
 * aber die Bestaetigung ist dabei eine Entscheidung der App: Wer die App
 * umgeht, kommt an die abgelegte PIN.
 *
 * Hier gibt es keine abgelegte PIN. Der Tresorschluessel ist mit einem
 * Schluessel verschluesselt, den der Keystore ohne Bestaetigung am Sensor
 * nicht herausrueckt. Die Pruefung findet im System statt, nicht in unserem
 * Code - sie laesst sich nicht wegprogrammieren.
 *
 * ERNEUTE EINRICHTUNG NACH FINGERABDRUCK-AENDERUNG. Der Schluessel wird
 * ungueltig, sobald jemand einen Fingerabdruck hinzufuegt oder entfernt
 * (setInvalidatedByBiometricEnrollment). Das ist gewollt: Sonst koennte
 * jemand, der das entsperrte Telefon hat, einfach den eigenen Finger
 * hinzufuegen und damit an den Tresor.
 *
 * Verloren geht dabei NICHTS - der Tresor haengt weiter an der PIN. Es ist
 * nur die Bequemlichkeit, die neu eingerichtet werden muss.
 */
class BioSchluessel(private val alias: String = STANDARD_ALIAS) {

    companion object {
        const val STANDARD_ALIAS = "yskar.biometrie.v1"
        private const val KEYSTORE = "AndroidKeyStore"
        private const val TAG_BITS = 128
        private const val UMWANDLUNG = "AES/GCM/NoPadding"
    }

    /** Was beim Vorbereiten herauskam. */
    sealed interface Stand {
        /** Der Cipher muss jetzt durch BiometricPrompt bestaetigt werden. */
        data class Bereit(val cipher: Cipher) : Stand

        /**
         * Der Schluessel ist ungueltig geworden, weil sich die
         * Fingerabdruecke geaendert haben. Der Tresor ist unversehrt;
         * Biometrie muss neu eingeschaltet werden.
         */
        data object NeuEinrichten : Stand

        data class Fehler(val grund: String) : Stand
    }

    fun vorhanden(): Boolean =
        runCatching {
            KeyStore.getInstance(KEYSTORE).apply { load(null) }.containsAlias(alias)
        }.getOrDefault(false)

    fun loeschen() {
        runCatching {
            KeyStore.getInstance(KEYSTORE).apply { load(null) }.deleteEntry(alias)
        }
    }

    /**
     * Cipher zum Umschliessen. Legt den Schluessel neu an - ein frisch
     * eingeschaltetes Biometrie-Entsperren bekommt immer einen frischen
     * Schluessel.
     */
    fun zumVerschluesseln(): Stand = try {
        loeschen()
        val c = Cipher.getInstance(UMWANDLUNG)
        c.init(Cipher.ENCRYPT_MODE, anlegen())
        Stand.Bereit(c)
    } catch (e: Exception) {
        Stand.Fehler(e::class.java.simpleName + ": " + e.message)
    }

    /** Cipher zum Oeffnen, mit dem IV aus der Tresorakte. */
    fun zumEntschluesseln(iv: ByteArray): Stand = try {
        val ks = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        val key = ks.getKey(alias, null) as? SecretKey
            ?: return Stand.NeuEinrichten
        val c = Cipher.getInstance(UMWANDLUNG)
        c.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
        Stand.Bereit(c)
    } catch (e: KeyPermanentlyInvalidatedException) {
        // Fingerabdruecke wurden geaendert. Kein Datenverlust - der Tresor
        // haengt an der PIN.
        loeschen()
        Stand.NeuEinrichten
    } catch (e: Exception) {
        Stand.Fehler(e::class.java.simpleName + ": " + e.message)
    }

    private fun anlegen(): SecretKey {
        val bauer = KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(true)
            // Jede Aenderung an den Fingerabdruecken macht den Schluessel
            // ungueltig. Siehe Klassenkommentar - das ist Absicht.
            .setInvalidatedByBiometricEnrollment(true)
            // Keystore waehlt den IV selbst. Ein wiederverwendeter IV waere
            // bei GCM ein katastrophaler Fehler; so kann er nicht passieren.
            .setRandomizedEncryptionRequired(true)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // 0 Sekunden Gueltigkeit = Bestaetigung fuer JEDE Benutzung.
            // Nur starke Biometrie, kein Geraete-Code: Der Ersatz ist die
            // eigene PIN der Wallet, wie in der Vorlage.
            bauer.setUserAuthenticationParameters(
                0,
                KeyProperties.AUTH_BIOMETRIC_STRONG,
            )
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            bauer.setUnlockedDeviceRequired(true)
        }

        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        gen.init(bauer.build())
        return gen.generateKey()
    }
}
