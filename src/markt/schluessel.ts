import * as speicher from '../geraet/speicher.ts';

/**
 * Der Schluessel fuer die Markt-API.
 *
 * ER STEHT NICHT IM BUILD. Das ist eine harte Vorgabe und hat einen
 * einfachen Grund: Was in der APK liegt, kann jeder auslesen -- eine APK
 * ist eine ZIP-Datei. Ein eingebauter Schluessel waere binnen Minuten
 * oeffentlich, und dann hinge das Kontingent aller Nutzer an einem
 * Schluessel, den Fremde mitbenutzen.
 *
 * Stattdessen: Der Nutzer traegt seinen eigenen kostenlosen Demo-Schluessel
 * einmal ein, und er liegt im geschuetzten Speicher des Geraets (Android
 * Keystore) -- am selben Ort wie der Tresor, aber in einem eigenen Eintrag.
 *
 * OHNE SCHLUESSEL LAEUFT DIE APP TROTZDEM. Dann gegen die oeffentliche
 * API, die laut Messung vom 09.11.2026 rund die Haelfte der Anfragen mit
 * HTTP 429 ablehnt. Die Oberflaeche sagt das, statt langsam zu wirken.
 *
 * Der Schluessel geht ausschliesslich an api.coingecko.com, niemals an
 * YSKAR, nie in ein Log und nie vollstaendig auf den Bildschirm.
 */

const ORT = 'yskar.markt.schluessel';

let zwischen: string | null | undefined;

export async function lesen(): Promise<string | null> {
  if (zwischen !== undefined) return zwischen;
  try {
    const w = await speicher.lesen(ORT);
    zwischen = w && w.trim() ? w.trim() : null;
  } catch { zwischen = null; }
  return zwischen;
}

/** Die Form eines CoinGecko-Demo-Schluessels: "CG-" und danach Buchstaben und Ziffern. */
export function siehtAus(wie: string): boolean {
  return /^CG-[A-Za-z0-9]{10,60}$/.test(wie.trim());
}

export async function setzen(wert: string): Promise<void> {
  const sauber = wert.trim();
  if (!sauber) { await entfernen(); return; }
  await speicher.schreiben(ORT, sauber);
  zwischen = sauber;
}

export async function entfernen(): Promise<void> {
  await speicher.entfernen(ORT);
  zwischen = null;
}

/**
 * Zum Anzeigen in den Einstellungen: nur die letzten vier Zeichen.
 * "CG-…x7Qa". Der ganze Schluessel wird nie gezeigt -- ein Screenshot der
 * Einstellungsseite soll ihn nicht verraten.
 */
export function verkuerzt(schluessel: string | null): string | null {
  if (!schluessel) return null;
  const s = schluessel.trim();
  return s.length <= 6 ? '••••' : 'CG-…' + s.slice(-4);
}

/** Nur fuer Tests: den gemerkten Wert vergessen. */
export function _vergessen(): void { zwischen = undefined; }
