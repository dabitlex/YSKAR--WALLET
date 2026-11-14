package com.yskar.wallet.ui

import androidx.compose.runtime.staticCompositionLocalOf
import com.yskar.wallet.sprache.Deutsch
import com.yskar.wallet.sprache.Texte

/**
 * Die Texte fuer alles, was gezeichnet wird.
 *
 * `MainActivity` stellt hier die gewaehlte Sprache bereit. Ohne das - im
 * Testrahmen `Bildschirmbilder` und in den Tests, die einzelne Bildschirme
 * zeichnen - ist es Deutsch: Die Tests suchen ihre Knoepfe an deutschen
 * Woertern. `OberflaecheTest` startet die ganze App und legt die Sprache
 * darum selbst fest, bevor sie startet.
 *
 * Gelesen wird so:
 *
 *     val t = LocalTexte.current
 *     Text(t.senden.eingabeTitel)
 */
val LocalTexte = staticCompositionLocalOf<Texte> { Deutsch }
