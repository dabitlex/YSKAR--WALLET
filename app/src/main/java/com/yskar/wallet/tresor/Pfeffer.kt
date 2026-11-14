package com.yskar.wallet.tresor

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.yskar.wallet.kern.Hashes
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.Mac

/**
 * Der Pfeffer - der Teil des Tresorschluessels, der das Geraet nie verlaesst.
 *
 * Ein HMAC-SHA256-Schluessel im Android Keystore. Er ist nicht auslesbar:
 * Die App kann mit ihm rechnen, aber ihn nicht herausholen. Auf Geraeten mit
 * Sicherheitsprozessor (StrongBox) liegt er in eigener Hardware.
 *
 * Deshalb nuetzt eine kopierte Tresordatei allein nichts. Erst Telefon plus
 * PIN ergeben den Schluessel.
 *
 * KEINE Nutzerbestaetigung fuer diesen Schluessel: Er ist kein Ersatz fuer
 * die PIN, sondern ihre Ergaenzung. Wer ihn benutzen kann, hat damit noch
 * keinen Tresor geoeffnet.
 *
 * WAS PASSIERT, WENN ER WEG IST - und das muss man wissen, bevor man sich
 * darauf verlaesst: Werden die App-Daten geloescht, die App deinstalliert
 * oder das Geraet zurueckgesetzt, ist der Pfeffer fort und die Tresordatei
 * nicht mehr zu oeffnen. Ein Sicherungskopie der DATEI ist damit wertlos.
 * Die Sicherung sind und bleiben die aufgeschriebenen zwoelf Woerter.
 * Diese Wahrheit gehoert in die Oberflaeche, nicht ins Kleingedruckte.
 */
interface Pfeffer {
    /** 32 Byte, fest an dieses Salz gebunden. */
    fun pfeffer(salz: ByteArray): ByteArray
}

class KeystorePfeffer(
    private val alias: String = STANDARD_ALIAS,
) : Pfeffer {

    companion object {
        const val STANDARD_ALIAS = "yskar.pfeffer.v1"
        private const val KEYSTORE = "AndroidKeyStore"
        private val ZWECK = "yskar-pfeffer-v1".toByteArray(Charsets.UTF_8)
    }

    override fun pfeffer(salz: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(schluessel())
        return mac.doFinal(ZWECK + salz)
    }

    /** Legt den Schluessel beim ersten Aufruf an. */
    private fun schluessel(): java.security.Key {
        val ks = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        ks.getKey(alias, null)?.let { return it }

        val bauer = KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_SIGN)
            .setDigests(KeyProperties.DIGEST_SHA256)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            // Nur bei entsperrtem Geraet benutzbar. Kostet nichts und
            // schliesst den Zugriff aus, waehrend das Telefon gesperrt ist.
            bauer.setUnlockedDeviceRequired(true)
        }

        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_HMAC_SHA256, KEYSTORE)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            // StrongBox ist der eigene Sicherheitsprozessor. Es gibt ihn
            // nicht ueberall, und wo es ihn nicht gibt, wirft das Anlegen -
            // dann ohne, statt die App unbrauchbar zu machen.
            try {
                gen.init(bauer.setIsStrongBoxBacked(true).build())
                return gen.generateKey()
            } catch (e: Exception) {
                bauer.setIsStrongBoxBacked(false)
            }
        }

        gen.init(bauer.build())
        return gen.generateKey()
    }
}

/**
 * Pfeffer fuer den JVM-Test, wo es keinen Keystore gibt.
 *
 * NUR FUER TESTS. Im Betrieb waere das ein fest eingebauter Schluessel -
 * also gar kein Schutz.
 */
class FesterPfeffer(private val geheimnis: ByteArray) : Pfeffer {
    override fun pfeffer(salz: ByteArray): ByteArray =
        Hashes.hmacSha256(geheimnis, "yskar-pfeffer-v1".toByteArray(Charsets.UTF_8) + salz)
}
