package com.yskar.wallet.tresor

import org.json.JSONObject
import java.util.Base64

/**
 * Der Inhalt der Tresordatei.
 *
 * Alles hier ist entweder oeffentlich (die Adresse) oder ohne den
 * Tresorschluessel wertlos (Salz, IV, Geheimtext). Die Woerter und die PIN
 * stehen NIE darin.
 *
 * Die Adresse liegt bewusst im Klartext: So kann die App Guthaben und
 * Verlauf anzeigen, ohne dass jemand die PIN eintippt. Eine Adresse ist
 * oeffentlich - sie steht ohnehin in jeder Transaktion.
 *
 * Als JSON, nicht als Binaerformat: Ein Tresor, den man im Notfall mit
 * einem Texteditor ansehen kann, ist mehr wert als ein paar gesparte Byte.
 *
 * Base64 statt Hex, weil die Felder nur transportiert und nie gerechnet
 * werden. java.util.Base64 gibt es ab API 26 - das ist unser minSdk.
 */
data class TresorAkte(
    val version: Int,
    val salz: ByteArray,
    val iv: ByteArray,
    val geheim: ByteArray,
    val runden: Int,
    /** Oeffentlich: die Adresse zu Index 0. */
    val adresse: String,
    val erstellt: String,
    /** Nur gesetzt, wenn Biometrie eingeschaltet ist. */
    val bio: BioHuelle? = null,
) {
    /** Der mit dem Biometrie-Schluessel des Keystores umschlossene Tresorschluessel. */
    data class BioHuelle(val iv: ByteArray, val geheim: ByteArray) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is BioHuelle) return false
            return iv.contentEquals(other.iv) && geheim.contentEquals(other.geheim)
        }

        override fun hashCode(): Int = 31 * iv.contentHashCode() + geheim.contentHashCode()
    }

    fun alsJson(): String {
        val o = JSONObject()
        o.put("version", version)
        o.put("salz", b64(salz))
        o.put("iv", b64(iv))
        o.put("geheim", b64(geheim))
        o.put("runden", runden)
        o.put("adresse", adresse)
        o.put("erstellt", erstellt)
        if (bio != null) {
            val b = JSONObject()
            b.put("iv", b64(bio.iv))
            b.put("geheim", b64(bio.geheim))
            o.put("bio", b)
        }
        return o.toString(2)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is TresorAkte) return false
        return version == other.version &&
            salz.contentEquals(other.salz) &&
            iv.contentEquals(other.iv) &&
            geheim.contentEquals(other.geheim) &&
            runden == other.runden &&
            adresse == other.adresse &&
            erstellt == other.erstellt &&
            bio == other.bio
    }

    override fun hashCode(): Int {
        var r = version
        r = 31 * r + salz.contentHashCode()
        r = 31 * r + iv.contentHashCode()
        r = 31 * r + geheim.contentHashCode()
        r = 31 * r + runden
        r = 31 * r + adresse.hashCode()
        r = 31 * r + erstellt.hashCode()
        r = 31 * r + (bio?.hashCode() ?: 0)
        return r
    }

    /** Nie Schluesselmaterial in Logs. */
    override fun toString(): String = "TresorAkte(v$version, $adresse, bio=${bio != null})"

    companion object {
        private fun b64(b: ByteArray): String = Base64.getEncoder().encodeToString(b)
        private fun vonB64(s: String): ByteArray = Base64.getDecoder().decode(s)

        /**
         * Liest eine Akte. Wirft mit Begruendung, statt still null zu
         * liefern: Eine unlesbare Tresordatei ist ein Ereignis, das der
         * Nutzer erfahren muss - sonst steht er vor einer App, die so tut,
         * als haette er nie eine Wallet gehabt.
         */
        fun ausJson(text: String): TresorAkte {
            val o = JSONObject(text)
            val version = o.getInt("version")
            if (version != Umschlag.VERSION) {
                throw IllegalArgumentException(
                    "Tresor der Fassung $version - diese App kennt nur ${Umschlag.VERSION}"
                )
            }

            val salz = vonB64(o.getString("salz"))
            val iv = vonB64(o.getString("iv"))
            val geheim = vonB64(o.getString("geheim"))
            if (salz.size != Umschlag.SALZ_BYTES) {
                throw IllegalArgumentException("Salz hat ${salz.size} Byte")
            }
            if (iv.size != Umschlag.IV_BYTES) {
                throw IllegalArgumentException("IV hat ${iv.size} Byte")
            }
            if (geheim.isEmpty()) throw IllegalArgumentException("Geheimtext ist leer")

            val bioObj = o.optJSONObject("bio")
            val bio = if (bioObj == null) null else BioHuelle(
                iv = vonB64(bioObj.getString("iv")),
                geheim = vonB64(bioObj.getString("geheim")),
            )

            return TresorAkte(
                version = version,
                salz = salz,
                iv = iv,
                geheim = geheim,
                runden = o.getInt("runden"),
                adresse = o.getString("adresse"),
                erstellt = o.getString("erstellt"),
                bio = bio,
            )
        }
    }
}
