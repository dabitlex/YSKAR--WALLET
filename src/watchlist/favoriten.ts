import { lager, useLager } from '../wallet/lager.ts';

/**
 * Favoriten -- die Grundlage der Watchlist.
 *
 * Liegt in localStorage und nicht im geschuetzten Speicher: Welche
 * Kryptowaehrungen jemand beobachtet, ist kein Geheimnis im Sinne eines
 * Schluessels. Es muss nur einen Neustart ueberleben.
 *
 * Die REIHENFOLGE ist Teil der Daten, nicht alphabetisch erzwungen: Der
 * Nutzer soll sie aendern koennen (Phase 6).
 */

export interface Favoriten {
  /** Coin-Kennungen des Anbieters, in der Reihenfolge des Nutzers. */
  ids: string[];
}

const VORGABE: Favoriten = { ids: [] };

export const favoritenLager = lager<Favoriten>('yskar.favoriten.v1', VORGABE, roh => {
  const r = (roh && typeof roh === 'object' ? roh : {}) as Partial<Favoriten>;
  const ids = Array.isArray(r.ids)
    ? r.ids.filter((x): x is string => typeof x === 'string' && x.length > 0 && x.length < 100)
    : [];
  // Doppelte entfernen, Reihenfolge behalten.
  return { ids: [...new Set(ids)].slice(0, 300) };
});

export const useFavoriten = (): Favoriten => useLager(favoritenLager);

export function umschalten(id: string): void {
  favoritenLager.aendern(alt => ({
    ids: alt.ids.includes(id) ? alt.ids.filter(x => x !== id) : [...alt.ids, id],
  }));
}

export const istFavorit = (f: Favoriten, id: string): boolean => f.ids.includes(id);

/** Reihenfolge aendern: das Element von `von` nach `nach` schieben. */
export function verschieben(von: number, nach: number): void {
  favoritenLager.aendern(alt => {
    if (von < 0 || von >= alt.ids.length || nach < 0 || nach >= alt.ids.length) return alt;
    const ids = [...alt.ids];
    const [x] = ids.splice(von, 1);
    ids.splice(nach, 0, x);
    return { ids };
  });
}
