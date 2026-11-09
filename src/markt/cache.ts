import type { MitHerkunft, Quelle } from './typen.ts';

/**
 * Cache der Marktschicht -- die Antwort auf eine Messung.
 *
 * Am 09.11.2026 wurden 12 Aufrufe der oeffentlichen CoinGecko-API
 * nacheinander abgesetzt: 6 kamen mit 200 zurueck, 6 mit 429 und
 * `retry-after: 35`. Eine Oberflaeche, die bei jedem Scrollen fragt, waere
 * damit die Haelfte der Zeit leer.
 *
 * Vier Mittel, in dieser Reihenfolge wirksam:
 *
 *   1. ZUSAMMENFASSEN  Dieselbe Anfrage zur gleichen Zeit -> ein Netzaufruf.
 *   2. FRISCHE         Was jung genug ist, wird gar nicht erst geholt.
 *   3. SPERRE          Nach einem 429 wird bis `retry-after` nichts gesendet.
 *                      Noch einmal zu fragen verlaengert die Sperre nur.
 *   4. LETZTER STAND   Scheitert ein Abruf, gilt der gespeicherte Wert
 *                      weiter -- mit seinem Alter, sichtbar in der
 *                      Oberflaeche. Lieber eine Zahl von vorhin, ehrlich
 *                      beschriftet, als ein leerer Bildschirm.
 *
 * Der Speicher ist localStorage und damit ausdruecklich KEIN Geheimnis:
 * Hier liegen oeffentliche Kurse. Der Schluessel der Markt-API liegt
 * woanders (markt/schluessel.ts, geschuetzter Speicher).
 */

const ORT = 'yskar.markt.cache.v1';
/** Mehr als das passt nicht sinnvoll in localStorage. Aeltestes fliegt zuerst. */
const MAX_EINTRAEGE = 120;

interface Eintrag { wert: unknown; zeit: number }

let speicher: Map<string, Eintrag> | null = null;

function laden(): Map<string, Eintrag> {
  if (speicher) return speicher;
  speicher = new Map();
  try {
    const roh = localStorage.getItem(ORT);
    if (roh) {
      const o = JSON.parse(roh) as Record<string, Eintrag>;
      for (const [k, v] of Object.entries(o)) {
        if (v && typeof v.zeit === 'number') speicher.set(k, v);
      }
    }
  } catch { /* unlesbar -> leer anfangen, nie raten */ }
  return speicher;
}

function sichern(): void {
  if (!speicher) return;
  try {
    // Aelteste zuerst verwerfen, damit der Platz nicht unbegrenzt waechst.
    if (speicher.size > MAX_EINTRAEGE) {
      const nach = [...speicher.entries()].sort((a, b) => b[1].zeit - a[1].zeit).slice(0, MAX_EINTRAEGE);
      speicher = new Map(nach);
    }
    localStorage.setItem(ORT, JSON.stringify(Object.fromEntries(speicher)));
  } catch { /* voll oder gesperrt -- der Cache im Arbeitsspeicher reicht */ }
}

export function lesenRoh<T>(schluessel: string): { wert: T; zeit: number } | null {
  const e = laden().get(schluessel);
  return e ? { wert: e.wert as T, zeit: e.zeit } : null;
}

export function schreiben<T>(schluessel: string, wert: T, jetzt = Date.now()): void {
  laden().set(schluessel, { wert, zeit: jetzt });
  sichern();
}

export function leeren(): void {
  speicher = new Map();
  try { localStorage.removeItem(ORT); } catch { /* egal */ }
}

/* ------------------------------------------------------------- Sperre */

let gesperrtBis = 0;

/** Sekunden bis zum naechsten erlaubten Versuch. 0 = frei. */
export function sperrzeit(jetzt = Date.now()): number {
  return gesperrtBis > jetzt ? Math.ceil((gesperrtBis - jetzt) / 1000) : 0;
}

/**
 * Nach einem 429 gesperrt. `retry-after` wird uebernommen, sonst 35
 * Sekunden -- der Wert, den CoinGecko in der Messung selbst genannt hat.
 * Eine laufende Sperre wird nur verlaengert, nie verkuerzt.
 */
export function sperren(sekunden = 35, jetzt = Date.now()): void {
  const bis = jetzt + Math.max(1, Math.min(300, sekunden)) * 1000;
  if (bis > gesperrtBis) gesperrtBis = bis;
}

export function sperreLoesen(): void { gesperrtBis = 0; }

/* ------------------------------------------------- Anfragen zusammenfassen */

const laufend = new Map<string, Promise<unknown>>();

/**
 * Gleiche Anfrage zur gleichen Zeit -> ein Netzaufruf, alle warten darauf.
 * Ohne das erzeugt eine Liste, die beim Scrollen nachlaedt, eine Lawine.
 */
export function vereinen<T>(schluessel: string, fn: () => Promise<T>): Promise<T> {
  const da = laufend.get(schluessel);
  if (da) return da as Promise<T>;
  const p = fn().finally(() => { laufend.delete(schluessel); });
  laufend.set(schluessel, p);
  return p;
}

/* ------------------------------------------------------------- Holen */

export interface HolOptionen {
  /** So lange gilt ein Wert als frisch und wird gar nicht geholt. */
  frischeMs: number;
  /** Herkunft, die der Wert in der Oberflaeche traegt. */
  quelle?: Quelle;
  jetzt?: number;
}

/**
 * Wert holen, mit allen vier Mitteln.
 *
 * Reihenfolge der Entscheidungen:
 *   frisch im Cache          -> sofort zurueck, kein Netz
 *   gesperrt und etwas da    -> der alte Wert, als veraltet gekennzeichnet
 *   gesperrt und nichts da   -> null
 *   sonst                    -> holen; scheitert es, der alte Wert
 */
export async function holen<T>(
  schluessel: string, laden: () => Promise<T>, o: HolOptionen,
): Promise<MitHerkunft<T> | null> {
  const jetzt = o.jetzt ?? Date.now();
  const quelle = o.quelle ?? 'coingecko';
  const alt = lesenRoh<T>(schluessel);

  if (alt && jetzt - alt.zeit < o.frischeMs) {
    return { wert: alt.wert, quelle, standVon: alt.zeit, veraltet: false };
  }
  const warten = sperrzeit(jetzt);
  if (warten > 0) {
    return alt ? { wert: alt.wert, quelle, standVon: alt.zeit, veraltet: true } : null;
  }
  try {
    const frisch = await vereinen(schluessel, laden);
    schreiben(schluessel, frisch, jetzt);
    return { wert: frisch, quelle, standVon: jetzt, veraltet: false };
  } catch (e) {
    if (alt) return { wert: alt.wert, quelle, standVon: alt.zeit, veraltet: true };
    throw e;
  }
}
