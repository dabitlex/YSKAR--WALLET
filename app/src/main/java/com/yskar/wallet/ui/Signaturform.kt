package com.yskar.wallet.ui

import com.yskar.wallet.signatur.Nachricht
import com.yskar.wallet.sprache.Texte
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Was der Bildschirm zum Signieren in Worten sagt - reine Funktionen, ohne
 * Compose (`SignaturformTest`).
 */

private val UHR = DateTimeFormatter.ofPattern("HH:mm")

/**
 * "in 4 Min. · 12:55" - wann eine Anmeldung ablaeuft, in Minuten und als
 * Uhrzeit dieses Geraets. Ist sie vorbei: "abgelaufen".
 *
 * Aufgerundet: Bei 61 Sekunden steht "in 2 Min.", nie eine Minute zu wenig.
 */
fun ablaufText(ablaufMs: Long, jetztMs: Long, zone: ZoneId, t: Texte): String {
    val ms = ablaufMs - jetztMs
    if (ms <= 0) return t.signatur.abgelaufen
    val minuten = (ms + 59_999) / 60_000
    val uhrzeit = UHR.format(Instant.ofEpochMilli(ablaufMs).atZone(zone))
    return "${t.signatur.inMinuten(minuten)} · $uhrzeit"
}

/**
 * Was ein gescannter Code fuer die Anmeldung ist: der Code selbst, wenn er
 * ein Anmelde-Code sein KOENNTE - sonst null, und der Scanner sucht weiter.
 * Geprueft ist damit noch nichts; das tut `Nachricht.anmeldungAusCode`.
 */
fun anmeldecodeAus(roh: String): String? =
    roh.trim().takeIf { it.length <= 4_000 && Nachricht.istAnmeldeCode(it) }
