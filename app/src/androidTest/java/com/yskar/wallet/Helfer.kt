package com.yskar.wallet

import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onAllNodesWithText

/**
 * Gemeinsame Helfer der instrumentierten Tests.
 *
 * `gibtEs` sucht als Teilzeichenkette und wirft nicht, wenn gerade nichts
 * passt - anders als `onNodeWithText`, das bei null Treffern eine Ausnahme
 * auslöst. Für "darf NICHT da sein" ist das der einzige brauchbare Weg.
 */
fun ComposeTestRule.gibtEs(text: String): Boolean =
    onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()
