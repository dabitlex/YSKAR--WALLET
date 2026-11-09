/**
 * Netzdaten von YSKAR -- Typen, Pruefung, Darstellung.
 *
 * Dieses Modul holt NICHTS. Es enthaelt ausschliesslich reine Funktionen:
 * die Antwort des Spiegels pruefen und in Zahlen verwandeln, mit denen die
 * Oberflaeche rechnen darf. Geholt wird in netz/api.ts.
 *
 * WARUM GETRENNT: Ohne Netzzugriff ist jede Zeile hier im Test nachpruefbar.
 * Und api.ts darf dieses Modul importieren, ohne dass ein Zyklus entsteht.
 *
 * WARUM SO MISSTRAUISCH: Die Felder kommen von einem Server. Ein fehlendes,
 * falsch typisiertes oder unsinniges Feld darf die Wallet nicht umwerfen und
 * erst recht nicht als Zahl in der Oberflaeche landen. Alles, was nicht
 * zweifelsfrei lesbar ist, wird null -- und null zeigt die Oberflaeche als
 * "nicht verfuegbar", nicht als 0.
 */

/** Der Stand des Netzes, wie ihn /api/v2/summary liefert -- geprueft. */
export interface Netzstand {
  /** Hoehe des letzten Blocks. */
  hoehe: number | null;
  /** Hoehe des naechsten Blocks -- die Hoehe, fuer die eine Zahlung gilt. */
  naechsteHoehe: number | null;
  /** Zahl der wartenden Transaktionen beim Knoten. */
  mempool: number;
  /** Schwierigkeit des naechsten Blocks. */
  schwierigkeit: bigint | null;
  /** Hashrate des Netzes in Hashes je Sekunde, aus der Schwierigkeit gerechnet. */
  hashrate: number | null;
  /** Hashrate, die die Miner selbst melden. Kann von hashrate abweichen. */
  minerHashrate: number | null;
  /** Miner, die der Knoten gerade zaehlt. */
  aktiveMiner: number | null;
  /** Offene Mining-Sitzungen. */
  sitzungen: number | null;
  /** Knoten, deren Miner mitgezaehlt werden. */
  knoten: number | null;
  /** Blockbelohnung des naechsten Blocks in Einheiten (1 YSR = 100.000.000). */
  naechsteBelohnung: bigint | null;
  /** Zielzeit je Block in Sekunden. */
  zielBlockzeit: number | null;
  /** Bisher erzeugte Menge in Einheiten. */
  gesamtmenge: bigint | null;
  /** Hoechstmenge in Einheiten. */
  hoechstmenge: bigint | null;
}

/** Die Mengen aus /api/v2/supply -- Dezimaltext in YSR, nicht gerundet. */
export interface Umlauf {
  hoehe: number | null;
  /** Umlaufende Menge in YSR als Dezimaltext. */
  umlauf: string | null;
  /** Hoechstmenge in YSR als Dezimaltext. */
  hoechst: string | null;
  /** Insgesamt erzeugte Menge in YSR als Dezimaltext. */
  erzeugt: string | null;
  /** Auf der Nulladresse gesperrte Menge in YSR als Dezimaltext. */
  gesperrt: string | null;
}

const feld = (o: unknown, name: string): unknown =>
  o && typeof o === 'object' ? (o as Record<string, unknown>)[name] : undefined;

/** Ganze Zahl >= 0, sonst null. Lehnt NaN, Unendlich und Kommazahlen ab. */
function ganz(v: unknown): number | null {
  const n = typeof v === 'string' && /^\d+$/.test(v) ? Number(v) : v;
  return typeof n === 'number' && Number.isInteger(n) && n >= 0 ? n : null;
}

/** Endliche Zahl >= 0, sonst null. Fuer Hashraten, die als Kommazahl kommen. */
function zahl(v: unknown): number | null {
  const n = typeof v === 'string' && /^\d+(\.\d+)?$/.test(v) ? Number(v) : v;
  return typeof n === 'number' && Number.isFinite(n) && n >= 0 ? n : null;
}

/**
 * Betrag in Einheiten. Kommt als Dezimaltext, weil die Zahlen groesser
 * werden koennen, als eine JavaScript-Zahl genau traegt -- deshalb bigint
 * und kein Number.
 */
function gross(v: unknown): bigint | null {
  if (typeof v === 'bigint') return v >= 0n ? v : null;
  const s = typeof v === 'number' && Number.isInteger(v) ? String(v) : v;
  if (typeof s !== 'string' || !/^\d+$/.test(s)) return null;
  try { return BigInt(s); } catch { return null; }
}

/** Dezimaltext in YSR, unveraendert durchgereicht. Nie in eine Zahl gewandelt. */
function dezimal(v: unknown): string | null {
  return typeof v === 'string' && /^\d+(\.\d+)?$/.test(v) ? v : null;
}

/** Antwort von /api/v2/summary pruefen. Wirft nie. */
export function netzstandAus(roh: unknown): Netzstand {
  return {
    hoehe: ganz(feld(roh, 'height')),
    naechsteHoehe: ganz(feld(roh, 'nextHeight')),
    mempool: ganz(feld(roh, 'mempool')) ?? 0,
    schwierigkeit: gross(feld(roh, 'difficultyWert')) ?? gross(feld(roh, 'difficulty')),
    hashrate: zahl(feld(roh, 'hashrate')),
    minerHashrate: zahl(feld(roh, 'minerHashrate')),
    aktiveMiner: ganz(feld(roh, 'activeMiners')),
    sitzungen: ganz(feld(roh, 'miningSessions')),
    knoten: ganz(feld(roh, 'knoten')),
    naechsteBelohnung: gross(feld(roh, 'nextReward')),
    zielBlockzeit: ganz(feld(roh, 'targetBlockTime')),
    gesamtmenge: gross(feld(roh, 'totalSupply')),
    hoechstmenge: gross(feld(roh, 'maxSupply')),
  };
}

/** Antwort von /api/v2/supply pruefen. Wirft nie. */
export function umlaufAus(roh: unknown): Umlauf {
  return {
    hoehe: ganz(feld(roh, 'height')),
    umlauf: dezimal(feld(roh, 'circulating')),
    hoechst: dezimal(feld(roh, 'max')),
    erzeugt: dezimal(feld(roh, 'minted')),
    gesperrt: dezimal(feld(roh, 'unspendable')),
  };
}

/* ------------------------------------------------------------ Darstellung */

const STUFEN = ['H/s', 'kH/s', 'MH/s', 'GH/s', 'TH/s', 'PH/s', 'EH/s'] as const;

/**
 * Hashrate lesbar: 41219230519 -> "41,2 GH/s".
 *
 * Eine Stelle nach dem Komma genuegt -- die Hashrate schwankt ohnehin von
 * Block zu Block, mehr Stellen taeuschten eine Genauigkeit vor, die es
 * nicht gibt.
 */
export function hashrateText(hs: number | null, locale: string): string | null {
  if (hs === null || !Number.isFinite(hs)) return null;
  if (hs < 1) return '0 H/s';
  let i = 0;
  let v = hs;
  while (v >= 1000 && i < STUFEN.length - 1) { v /= 1000; i++; }
  const stellen = i === 0 ? 0 : 1;
  return v.toLocaleString(locale, { minimumFractionDigits: stellen, maximumFractionDigits: stellen }) + ' ' + STUFEN[i];
}

/** Ganze Zahl mit Tausendertrennung der jeweiligen Sprache. */
export function zahlText(n: number | null, locale: string): string | null {
  return n === null || !Number.isFinite(n) ? null : n.toLocaleString(locale);
}

/**
 * Anteil der umlaufenden Menge an der Hoechstmenge, 0 bis 1.
 * Gerechnet wird in bigint; erst das Ergebnis ist eine Kommazahl.
 */
export function umlaufAnteil(umlauf: bigint | null, hoechst: bigint | null): number | null {
  if (umlauf === null || hoechst === null || hoechst <= 0n) return null;
  if (umlauf >= hoechst) return 1;
  // Auf vier Stellen genau, ohne bigint zu verlassen.
  return Number((umlauf * 10_000n) / hoechst) / 10_000;
}

/**
 * Bloecke bis zu einer Hoehe, und wie lange das bei der Zielzeit dauert.
 * Eine Schaetzung und als solche zu kennzeichnen: Bloecke kommen nicht
 * gleichmaessig.
 */
export function blockAbstand(
  jetzt: number | null, ziel: number, zielBlockzeit: number | null,
): { bloecke: number; sekunden: number | null } | null {
  if (jetzt === null || jetzt >= ziel) return null;
  const bloecke = ziel - jetzt;
  return { bloecke, sekunden: zielBlockzeit === null ? null : bloecke * zielBlockzeit };
}
