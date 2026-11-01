import { wordlist } from '@scure/bip39/wordlists/english.js';
import { isValidAddress } from '../kern/address.ts';

/**
 * Was eine Taste der eigenen Tastatur mit einer Eingabe macht.
 *
 * Betrag, Adresse und die 12 Woerter tippt man ueber die Tastatur der App,
 * nicht ueber die des Telefons: So liest keine fremde Tastatur-App mit, und
 * es gibt nur Tasten, die an dieser Stelle gueltig sind.
 */
export const LOESCHEN = '⌫';

/** Zeichen einer YSKAR-Adresse nach dem Anfang "ysr1" (bech32: ohne 1, b, i, o). */
export const ADRESS_ZEICHEN = '023456789acdefghjklmnpqrstuvwxyz';
const ANFANG = 'ysr1';
/** Version 0 hat 42 Zeichen, Version 1 (ysr1p…, ab Fassung 6) 63. */
const ADRESSE_V0 = 42;
export const ADRESSE_LAENGE = 63;

/** Betrag: hoechstens zwoelf Stellen vor und `stellen` nach dem Trennzeichen. */
export function tippeBetrag(alt: string, taste: string, zeichen: string, stellen = 8): string {
  if (taste === LOESCHEN) return alt.slice(0, -1);
  const [ganz, bruch] = alt.split(zeichen) as [string, string | undefined];
  if (taste === zeichen) return bruch !== undefined ? alt : `${ganz || '0'}${zeichen}`;
  if (!/^\d$/.test(taste)) return alt;
  if (bruch !== undefined) return bruch.length < stellen ? alt + taste : alt;
  if (ganz === '0') return taste;                 // keine fuehrenden Nullen
  return ganz.length < 12 ? alt + taste : alt;
}

/** Adresse: Der Anfang "ysr1" steht von selbst da, sobald das erste Zeichen getippt wird. */
export function tippeAdresse(alt: string, taste: string): string {
  if (taste === LOESCHEN) {
    const neu = alt.slice(0, -1);
    return neu.length <= ANFANG.length && ANFANG.startsWith(neu) ? '' : neu;
  }
  if (!ADRESS_ZEICHEN.includes(taste)) return alt;
  if (alt === '') return ANFANG + taste;
  // Eine fertige Adresse der Version 0 waechst nicht weiter; laenger geht es nur fuer ysr1p….
  if (alt.length === ADRESSE_V0 && isValidAddress(alt)) return alt;
  return alt.length < ADRESSE_LAENGE ? alt + taste : alt;
}

/** Woerter der Liste, die so anfangen. Ein Wort, das genau so heisst, steht vorn. */
export function wortVorschlaege(anfang: string, max = 4): string[] {
  const a = anfang.trim().toLowerCase();
  if (!a) return [];
  const treffer: string[] = [];
  for (const w of wordlist) {
    if (w.startsWith(a)) { treffer.push(w); if (treffer.length >= max + 1) break; }
  }
  return treffer.sort((x, y) => Number(y === a) - Number(x === a)).slice(0, max);
}

export const istWort = (w: string): boolean => wordlist.includes(w);

/** Ein Buchstabe fuer ein Wort; die laengsten Woerter der Liste haben acht. */
export function tippeWort(alt: string, taste: string): string {
  if (taste === LOESCHEN) return alt.slice(0, -1);
  return /^[a-z]$/.test(taste) && alt.length < 8 ? alt + taste : alt;
}

/** Eingefuegten Text auf die Felder verteilen: Kleinbuchstaben, an Leerraum getrennt. */
export function woerterAusText(text: string, anzahl: number): string[] {
  const teile = text.normalize('NFKD').toLowerCase().split(/[^a-z]+/).filter(Boolean).slice(0, anzahl);
  return Array.from({ length: anzahl }, (_, i) => teile[i] ?? '');
}

/**
 * Freier Text (Notiz, Name, Suche): ein Zeichen anhaengen oder das letzte
 * loeschen. Kein Leerzeichen am Anfang und keine zwei hintereinander.
 */
export function tippeText(alt: string, taste: string, max = 64): string {
  const zeichen = Array.from(alt);
  if (taste === LOESCHEN) return zeichen.slice(0, -1).join('');
  if (taste === ' ' && (alt === '' || alt.endsWith(' '))) return alt;
  return zeichen.length < max ? alt + taste : alt;
}

/** Welche Taste der Text-Tastatur gross geschrieben beginnt. */
export function grossAmAnfang(wert: string, art: 'anfang' | 'woerter' | undefined): boolean {
  if (art === 'woerter') return wert === '' || wert.endsWith(' ');
  return art === 'anfang' && wert === '';
}
