package com.yskar.wallet

import com.yskar.wallet.kern.Tx
import com.yskar.wallet.netz.Betrag
import com.yskar.wallet.netz.Fassung
import com.yskar.wallet.netz.NetzFehler
import com.yskar.wallet.netz.Stufe
import com.yskar.wallet.netz.spiegelMainnet
import com.yskar.wallet.sprache.Texte

/**
 * Live-Pruefung gegen den echten Spiegel - vom Geraet aus.
 *
 * Die Unittests pruefen den Parser gegen mitgeschnittene Antworten. Was sie
 * NICHT pruefen koennen:
 *
 *   - haelt HttpURLConnection unter Android die TLS-Verbindung,
 *   - greift die INTERNET-Berechtigung,
 *   - antwortet der Spiegel heute noch so wie beim Mitschnitt.
 *
 * Nur LESEN. Diese Pruefung reicht nichts ein und unterschreibt nichts.
 *
 * Die geprueften Werte sind oeffentlich: Blockhoehe, Gebuehrenlage und das
 * Konto DIESER WALLET - dieselbe Adresse, nach der die App den Spiegel
 * ohnehin fragt. (Bis zum 11.11.2026 stand hier eine feste Pruefadresse aus
 * der Entwicklung; wer den Selbsttest oeffnete, sah ein fremdes Konto.)
 * Nichts davon passiert ungefragt - der Selbsttest laeuft, wenn jemand ihn
 * oeffnet.
 *
 * Die Zeilen ueber das eigene Konto sind als `eigen` gekennzeichnet und
 * kommen nicht ins Log (REGELN.md, Regel 9).
 *
 * Der Bericht steht in der Sprache der App (`sprache/SelbsttestTexte.kt`),
 * Betraege in ihrer Schreibweise. Was geprueft wird, haengt davon nicht ab.
 */
object NetzSelbsttest {

    /**
     * [adresse]: die Adresse der Wallet auf diesem Geraet; null, wenn es
     * keine gibt - dann wird das Netz geprueft, aber kein Konto gelesen.
     */
    fun pruefen(adresse: String?, t: Texte): Selbsttest.Bericht {
        val w = t.selbsttest
        val start = System.currentTimeMillis()
        val zeilen = ArrayList<Selbsttest.Zeile>()
        fun melde(name: String, ok: Boolean, wert: String, eigen: Boolean = false) {
            zeilen.add(Selbsttest.Zeile(ok, name, wert, eigen))
        }

        val spiegel = spiegelMainnet()

        try {
            val kopf = spiegel.kopf()
            melde(
                w.netzHoehe,
                (kopf.hoehe ?: 0) > 0,
                w.netzHoeheWert(kopf.hoehe, kopf.mempool),
            )

            val markt = spiegel.markt()
            melde(
                w.netzGebuehrenlage,
                markt.mindestGebuehr > 0 && markt.fassung in 1..2,
                w.netzGebuehrenlageWert(
                    Betrag.mitEinheit(markt.mindestGebuehr, t.locale, min = 0),
                    markt.fassung,
                    markt.basisBytes,
                ),
            )

            // Querprobe: Was der Knoten als Groesse nennt, muss der Port
            // selbst ausrechnen koennen.
            melde(
                w.netzBasisgroesse,
                markt.basisBytes == Tx.TRANSFER_BASE_BYTES,
                w.netzBasisgroesseWert(markt.basisBytes, Tx.TRANSFER_BASE_BYTES),
            )

            val stand = Fassung.sendeStand(markt, kopf.hoehe)
            melde(
                w.netzSendefassung,
                !stand.unbekannt,
                w.netzSendefassungWert(stand.fassung, stand.pause, stand.darfSenden),
            )

            val bytes = Fassung.ueberweisungBytes(stand, markt, notizBytes = 0)
            val gebuehr = Fassung.gebuehrFuer(markt, Stufe.NORMAL, bytes)
            melde(
                w.netzGebuehr,
                gebuehr >= markt.mindestGebuehr,
                w.netzGebuehrWert(bytes, Betrag.mitEinheit(gebuehr, t.locale, min = 0)),
            )

            val knoten = spiegel.knotenAdresse()
            melde(
                w.netzKnotenErkannt,
                knoten.startsWith("https://"),
                knoten,
            )

            if (adresse == null) {
                melde(w.netzKontoGelesen, true, w.netzOhneWallet)
            } else {
                val konto = spiegel.konto(adresse)
                melde(
                    w.netzKontoGelesen,
                    konto.adresse == adresse,
                    konto.adresse,
                    eigen = true,
                )
                melde(
                    w.netzGuthaben,
                    konto.guthaben >= 0,
                    Betrag.mitEinheit(konto.guthaben, t.locale),
                    eigen = true,
                )
                melde(
                    w.netzNonce,
                    konto.naechsteNonce >= konto.nonce,
                    w.netzNonceWert(konto.nonce, konto.naechsteNonce),
                    eigen = true,
                )
                // Ein leerer Verlauf ist kein Fehler: Eine neue Wallet hat
                // noch keinen. Geprueft ist, dass die Antwort lesbar war.
                melde(
                    w.netzVerlauf,
                    true,
                    w.netzVerlaufWert(konto.verlauf.size, konto.wartend.size, konto.wartendQuelle),
                    eigen = true,
                )

                konto.verlauf.firstOrNull()?.let { e ->
                    melde(
                        w.netzJuengsterEintrag,
                        e.txid.length == 64,
                        w.netzJuengsterEintragWert(
                            e.art.toString(),
                            Betrag.mitEinheit(e.betrag, t.locale),
                            e.height,
                            e.notiz,
                        ),
                        eigen = true,
                    )
                }

                val seite = spiegel.verlauf(adresse, limit = 3)
                melde(
                    w.netzBlaettern,
                    seite.eintraege.size <= 3,
                    w.netzBlaetternWert(seite.eintraege.size, seite.weiter),
                    eigen = true,
                )
            }
        } catch (e: NetzFehler) {
            melde(
                if (e.offline) w.netzKeineVerbindung else w.netzAntwortDesServers,
                false,
                e.message ?: w.netzUnbekannt,
            )
        } catch (e: Throwable) {
            melde(w.ausnahme, false, e::class.java.simpleName + ": " + e.message)
        }

        return Selbsttest.Bericht(zeilen, System.currentTimeMillis() - start)
    }
}
