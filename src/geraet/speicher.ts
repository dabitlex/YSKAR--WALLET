import { SecureStorage, KeychainAccess } from '@aparajita/capacitor-secure-storage';
import { Capacitor } from '@capacitor/core';

/**
 * Geschuetzter Speicher des Geraets.
 *
 * Android: Keystore (verschluesselte Einstellungen, der Schluessel
 * verlaesst das Geraet nicht). iOS: Keychain. Im Browser, nur zum
 * Entwickeln, faellt das Plugin auf localStorage zurueck.
 *
 * WICHTIG: Ein Capacitor-Plugin ist ein Proxy. Gibt eine async-Funktion ihn
 * direkt zurueck, prueft JavaScript beim Aufloesen `.then` -- der Proxy
 * deutet das als Plugin-Methode und wirft. Deshalb wird er hier nie aus
 * einer async-Funktion herausgereicht, sondern nur benutzt.
 *
 * ------------------------------------------------------------------
 * iOS: DER TRESOR DARF DAS GERAET NICHT VERLASSEN
 *
 * Auf Android ist das in der Huelle geregelt: allowBackup="false" und
 * dataExtractionRules schliessen alles aus -- weder Cloud-Sicherung noch
 * Geraeteuebertragung nehmen den Tresor mit.
 *
 * Auf iOS gilt das NICHT von allein. Die Vorgabe des Keychains ist
 * `whenUnlocked`, und deren eigene Beschreibung lautet: "Items with this
 * attribute migrate to a new device when using encrypted backups."
 * Dazu kommt die iCloud-Schluesselbundsynchronisierung.
 *
 * Ohne die beiden Zeilen unten waere die Zusage aus der README --
 * "die Woerter wandern nicht in Cloud-Sicherungen" -- auf iPhones
 * schlicht falsch. Deshalb:
 *
 *   setSynchronize(false)               kein iCloud-Schluesselbund
 *   whenUnlockedThisDeviceOnly          wandert in keine Sicherung mit
 *
 * Beides wird einmal beim Start gesetzt, bevor irgendetwas gelesen oder
 * geschrieben wird.
 */

let vorbereitet: Promise<void> | null = null;

function vorbereiten(): Promise<void> {
  if (vorbereitet) return vorbereitet;
  vorbereitet = (async () => {
    if (Capacitor.getPlatform() !== 'ios') return;
    await SecureStorage.setSynchronize(false);
    await SecureStorage.setDefaultKeychainAccess(KeychainAccess.whenUnlockedThisDeviceOnly);
  })().catch(e => {
    // Scheitert das, ist der Tresor moeglicherweise sicherungsfaehig --
    // das darf nicht stillschweigend passieren.
    vorbereitet = null;
    throw e;
  });
  return vorbereitet;
}

export async function lesen(schluessel: string): Promise<string | null> {
  await vorbereiten();
  const wert = await SecureStorage.get(schluessel);
  return typeof wert === 'string' ? wert : null;
}

export async function schreiben(schluessel: string, wert: string): Promise<void> {
  await vorbereiten();
  await SecureStorage.set(schluessel, wert);
}

export async function entfernen(schluessel: string): Promise<void> {
  try { await vorbereiten(); await SecureStorage.remove(schluessel); } catch { /* war nie da */ }
}
