import { isValidAddress } from '../kern/address.ts';
import { lager, useLager } from './lager.ts';

/**
 * Kontakte: Namen fuer Adressen.
 *
 * Sie liegen NUR auf diesem Geraet. Kein Server kennt sie, und sie kommen
 * nicht mit, wenn die Wallet auf einem anderen Geraet mit den 12 Woertern
 * wiederhergestellt wird. Dasselbe Format wie in der bisherigen App.
 *
 * Ein Name ist eine Merkhilfe, kein Sicherheitsmerkmal: Beim Senden zeigt
 * die Wallet trotzdem immer die volle Adresse.
 */
export const NAME_MAX = 24;
export type Kontakte = Record<string, string>;

const LEER: Kontakte = Object.freeze({}) as Kontakte;

export const kontakteLager = lager<Kontakte>('yskar.kontakte', LEER, roh => {
  if (!roh || typeof roh !== 'object' || Array.isArray(roh)) return LEER;
  const sauber: Kontakte = {};
  for (const [adresse, name] of Object.entries(roh as Record<string, unknown>)) {
    if (typeof name === 'string' && name.trim() && isValidAddress(adresse)) sauber[adresse] = name.trim().slice(0, NAME_MAX);
  }
  return sauber;
});

/** Name setzen oder aendern. Ein leerer Name entfernt den Kontakt. */
export function kontaktSetzen(adresse: string, name: string): boolean {
  const a = adresse.trim().toLowerCase();
  if (!isValidAddress(a)) return false;
  const n = name.trim().slice(0, NAME_MAX);
  const neu = { ...kontakteLager.lesen() };
  if (n) neu[a] = n; else delete neu[a];
  kontakteLager.setzen(neu);
  return true;
}

export function useKontakte() {
  const kontakte = useLager(kontakteLager);
  const name = (adresse: string | null | undefined): string | null => (adresse && kontakte[adresse.toLowerCase()]) || null;
  return { kontakte, name };
}

/** Anfangsbuchstaben fuer den Kreis: "Jonas Weber" -> "JW". */
export function kuerzel(name: string): string {
  const teile = name.trim().split(/\s+/).filter(Boolean);
  const a = [...(teile[0] ?? '')][0] ?? '?';
  const b = teile.length > 1 ? [...teile[teile.length - 1]][0] ?? '' : '';
  return (a + b).toUpperCase();
}
