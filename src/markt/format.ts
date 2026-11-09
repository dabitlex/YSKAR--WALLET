import type { Waehrung } from './typen.ts';

/**
 * Zahlen fuer die Marktansicht.
 *
 * Alles laeuft ueber Intl. Von Hand angehaengte Zeichen sind der sichere
 * Weg in einen Fehler: Deutsch schreibt "1.234,56 €", Englisch "€1,234.56"
 * -- Trennzeichen, Reihenfolge und Leerzeichen sind je Sprache anders.
 *
 * Kurse spannen viele Groessenordnungen: Bitcoin kostet Zehntausende,
 * eine Shitcoin 0,00000012. Eine feste Zahl von Nachkommastellen ist fuer
 * das eine zu grob und fuer das andere Unsinn, deshalb richtet sie sich
 * nach der Groesse.
 */

const ZEICHEN: Record<Waehrung, string> = { eur: 'EUR', usd: 'USD' };

/** Nachkommastellen nach Groessenordnung des Kurses. */
function stellenFuer(wert: number): number {
  const a = Math.abs(wert);
  if (a === 0) return 2;
  if (a >= 1000) return 0;
  if (a >= 1) return 2;
  if (a >= 0.01) return 4;
  if (a >= 0.0001) return 6;
  return 8;
}

/** Kurs mit Waehrungszeichen, Stellen nach Groessenordnung. */
export function kurs(wert: number | null, w: Waehrung, locale: string): string | null {
  if (wert === null || !Number.isFinite(wert)) return null;
  const s = stellenFuer(wert);
  return wert.toLocaleString(locale, {
    style: 'currency', currency: ZEICHEN[w],
    minimumFractionDigits: s, maximumFractionDigits: s,
  });
}

/**
 * Grosse Betraege kurz: "2,21 Bio. €".
 *
 * Intl kennt dafuer `notation: 'compact'` und setzt die Abkuerzung je
 * Sprache richtig -- Deutsch "Bio.", Englisch "T". Selbst gebaute Stufen
 * waeren in einer der beiden Sprachen falsch.
 */
export function kompakt(wert: number | null, w: Waehrung, locale: string): string | null {
  if (wert === null || !Number.isFinite(wert)) return null;
  return wert.toLocaleString(locale, {
    style: 'currency', currency: ZEICHEN[w],
    notation: 'compact', maximumFractionDigits: 2,
  });
}

/** Grosse Stueckzahlen kurz, ohne Waehrung: "20,1 Mio." */
export function kompaktZahl(wert: number | null, locale: string): string | null {
  if (wert === null || !Number.isFinite(wert)) return null;
  return wert.toLocaleString(locale, { notation: 'compact', maximumFractionDigits: 2 });
}

/**
 * Veraenderung mit Vorzeichen: "+1,49 %" / "−2,12 %".
 *
 * Das Vorzeichen steht IMMER da, auch im Plus. Es ist die zweite
 * Information neben der Farbe -- wer Gruen und Rot schlecht
 * unterscheidet, liest das Zeichen.
 */
export function prozent(wert: number | null, locale: string): string | null {
  if (wert === null || !Number.isFinite(wert)) return null;
  return (wert / 100).toLocaleString(locale, {
    style: 'percent', signDisplay: 'exceptZero',
    minimumFractionDigits: 2, maximumFractionDigits: 2,
  });
}

/** Richtung einer Veraenderung. null, wenn es keine gibt. */
export function richtung(wert: number | null): boolean | null {
  return wert === null || !Number.isFinite(wert) || wert === 0 ? null : wert > 0;
}

/** Alter eines Standes in Worten -- fuer "Stand von vorhin". */
export function alter(standVon: number, locale: string, jetzt = Date.now()): string {
  const sek = Math.max(0, Math.round((jetzt - standVon) / 1000));
  const rel = new Intl.RelativeTimeFormat(locale, { numeric: 'auto' });
  if (sek < 60) return rel.format(-sek, 'second');
  if (sek < 3600) return rel.format(-Math.round(sek / 60), 'minute');
  if (sek < 86400) return rel.format(-Math.round(sek / 3600), 'hour');
  return rel.format(-Math.round(sek / 86400), 'day');
}
