package com.yskar.wallet.ui

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.yskar.wallet.sprache.Texte
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.crypto.Cipher
import kotlin.coroutines.resume

/**
 * Den Sensor fragen.
 *
 * Eine Stelle fuer alle: Sperrbildschirm, Einstellungen und Selbsttest
 * benutzen denselben Weg. Der Cipher geht als CryptoObject mit - erst
 * dadurch ist die Bestaetigung kryptografisch gebunden und nicht nur eine
 * Entscheidung unseres Codes.
 */
object SensorFrage {

    const val STARK = BiometricManager.Authenticators.BIOMETRIC_STRONG

    /** null = alles in Ordnung, sonst der Grund im Klartext. */
    fun hindernis(activity: FragmentActivity, t: Texte): String? =
        when (BiometricManager.from(activity).canAuthenticate(STARK)) {
            BiometricManager.BIOMETRIC_SUCCESS -> null
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE ->
                t.allgemein.sensorFehlt

            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE ->
                t.allgemein.sensorNichtVerfuegbar

            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED ->
                t.allgemein.sensorOhneFinger

            BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED ->
                t.allgemein.sensorBrauchtUpdate

            else -> t.allgemein.sensorNichtDa
        }

    fun moeglich(activity: FragmentActivity): Boolean =
        BiometricManager.from(activity).canAuthenticate(STARK) == BiometricManager.BIOMETRIC_SUCCESS

    sealed interface Antwort {
        data class Ok(val cipher: Cipher?) : Antwort
        data class Abbruch(val code: Int, val text: String) : Antwort
    }

    /**
     * Dialog zeigen und warten.
     *
     * Muss vom Hauptthread aus aufgerufen werden - BiometricPrompt baut
     * seinen Dialog in die Activity.
     */
    suspend fun frage(
        activity: FragmentActivity,
        titel: String,
        cipher: Cipher,
        t: Texte,
    ): Antwort = suspendCancellableCoroutine { fortsetzung ->
        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(
                    result: BiometricPrompt.AuthenticationResult,
                ) {
                    fortsetzung.einmal(Antwort.Ok(result.cryptoObject?.cipher))
                }

                override fun onAuthenticationError(code: Int, text: CharSequence) {
                    fortsetzung.einmal(Antwort.Abbruch(code, text.toString()))
                }
                // onAuthenticationFailed heisst "Finger nicht erkannt" und
                // beendet den Dialog nicht - hier also nichts zu tun.
            },
        )

        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(titel)
            .setNegativeButtonText(t.allgemein.abbrechen)
            .setAllowedAuthenticators(STARK)
            .setConfirmationRequired(false)
            .build()

        prompt.authenticate(info, BiometricPrompt.CryptoObject(cipher))
        fortsetzung.invokeOnCancellation { prompt.cancelAuthentication() }
    }

    private fun CancellableContinuation<Antwort>.einmal(a: Antwort) {
        if (isActive) resume(a)
    }
}
