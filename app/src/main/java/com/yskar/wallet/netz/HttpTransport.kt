package com.yskar.wallet.netz

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Der Transport - HttpURLConnection, ohne zusaetzliche Bibliothek.
 *
 * Warum keine HTTP-Bibliothek: Diese App spricht mit genau zwei Adressen
 * und holt dort JSON ab. Dafuer eine Abhaengigkeit mehr in eine Wallet zu
 * ziehen, waere Angriffsflaeche ohne Gegenwert. Androids
 * HttpURLConnection wird seit Jahren von OkHttp bedient - es ist also
 * ohnehin ein erprobter Unterbau, nur ohne eigenes Paket.
 *
 * NUR HTTPS. Eine Klartextverbindung wird abgelehnt, nicht stillschweigend
 * aufgebaut: Wer die Adresse des Knotens aus einer Antwort uebernimmt
 * (siehe Spiegel.knotenAdresse), darf dabei nie auf http zurueckfallen.
 *
 * DIESE AUFRUFE BLOCKIEREN. Sie gehoeren auf einen Hintergrund-Dispatcher,
 * nie auf den Hauptthread - Android wirft dort NetworkOnMainThreadException.
 */
class HttpTransport(
    private val verbindenMs: Int = 12_000,
    private val lesenMs: Int = 20_000,
) : Spiegel.Transport {

    override fun anfrage(methode: String, url: String, rumpf: String?): Spiegel.Antwort {
        val ziel = URL(url)
        if (!ziel.protocol.equals("https", ignoreCase = true)) {
            throw NetzFehler(
                NetzFehler.Ursache.ANTWORT, 0,
                "nur https erlaubt, war: ${ziel.protocol}",
            )
        }

        var verbindung: HttpURLConnection? = null
        try {
            verbindung = (ziel.openConnection() as HttpURLConnection).apply {
                requestMethod = methode
                connectTimeout = verbindenMs
                readTimeout = lesenMs
                useCaches = false
                instanceFollowRedirects = false
                setRequestProperty("accept", "application/json")
                if (rumpf != null) {
                    doOutput = true
                    setRequestProperty("content-type", "application/json")
                }
            }

            if (rumpf != null) {
                verbindung.outputStream.use { it.write(rumpf.toByteArray(Charsets.UTF_8)) }
            }

            val status = verbindung.responseCode
            // Bei 4xx und 5xx liegt der Rumpf im Fehlerstrom - und genau
            // dort steht die Adresse des Full Node (HTTP 410).
            val strom = if (status in 200..299) verbindung.inputStream else verbindung.errorStream
            val text = strom?.use { it.readBytes().toString(Charsets.UTF_8) } ?: ""

            return Spiegel.Antwort(status, text)
        } catch (e: IOException) {
            throw NetzFehler(NetzFehler.Ursache.OFFLINE, 0, e.message ?: "keine Verbindung")
        } finally {
            verbindung?.disconnect()
        }
    }
}

/** Der einsatzbereite Spiegel fuer das Mainnet. */
fun spiegelMainnet(): Spiegel = Spiegel(HttpTransport())
