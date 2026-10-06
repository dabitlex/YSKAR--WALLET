import { SecureStorage } from '@aparajita/capacitor-secure-storage';

/**
 * Geschuetzter Speicher des Geraets.
 *
 * In der App liegt er im Android Keystore (verschluesselte Einstellungen,
 * der Schluessel verlaesst das Geraet nicht). Im Browser, nur zum
 * Entwickeln, faellt das Plugin auf localStorage zurueck.
 *
 * WICHTIG: Ein Capacitor-Plugin ist ein Proxy. Gibt eine async-Funktion ihn
 * direkt zurueck, prueft JavaScript beim Aufloesen `.then` -- der Proxy
 * deutet das als Plugin-Methode und wirft. Deshalb wird er hier nie aus
 * einer async-Funktion herausgereicht, sondern nur benutzt.
 */

export async function lesen(schluessel: string): Promise<string | null> {
  const wert = await SecureStorage.get(schluessel);
  return typeof wert === 'string' ? wert : null;
}

export async function schreiben(schluessel: string, wert: string): Promise<void> {
  await SecureStorage.set(schluessel, wert);
}

export async function entfernen(schluessel: string): Promise<void> {
  try { await SecureStorage.remove(schluessel); } catch { /* war nie da */ }
}
