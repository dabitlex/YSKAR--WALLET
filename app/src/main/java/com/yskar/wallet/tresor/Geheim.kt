package com.yskar.wallet.tresor

/**
 * Geheimes aus dem Speicher raeumen.
 *
 * `CharArray.fill(0)` gibt es nicht - `fill` verlangt dort ein `Char`. Das
 * ist leicht zu uebersehen und faellt erst beim Uebersetzen auf. Mit einem
 * eigenen Namen steht ausserdem im Quelltext, WARUM ueberschrieben wird,
 * statt nur WAS.
 *
 * Grenze der Methode, ehrlich gesagt: Ein `String` laesst sich in Java
 * nicht loeschen. Darum reisen PIN und Merkwoerter als `CharArray` bzw.
 * `ByteArray` durch den Code, und nur dort, wo eine Bibliothek einen
 * String verlangt, entsteht eine Kopie, die liegen bleibt, bis der
 * Speicherbereiniger sie holt.
 */
fun CharArray.loeschen() {
    fill(Char(0))
}

fun ByteArray.loeschen() {
    fill(0)
}
