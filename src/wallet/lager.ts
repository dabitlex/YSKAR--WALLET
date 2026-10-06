import { useSyncExternalStore } from 'react';

/**
 * Kleiner Speicher fuer alles, was NICHT geheim ist: Kontakte,
 * Einstellungen, Anforderungen. Liegt im Speicher der App (localStorage)
 * und nur auf diesem Geraet.
 *
 * Geheimes (die 12 Woerter, die PIN) liegt nie hier -- siehe tresor.ts.
 */
export interface Lager<T> {
  lesen(): T;
  setzen(neu: T): void;
  aendern(f: (alt: T) => T): void;
  leeren(): void;
  abonnieren(h: () => void): () => void;
}

export function lager<T>(schluessel: string, leer: T, saeubern: (roh: unknown) => T): Lager<T> {
  let stand: T | null = null;
  const hoerer = new Set<() => void>();

  const laden = (): T => {
    try {
      const roh = localStorage.getItem(schluessel);
      return roh === null ? leer : saeubern(JSON.parse(roh));
    } catch { return leer; }
  };
  const lesen = (): T => (stand === null ? (stand = laden()) : stand);
  const setzen = (neu: T) => {
    stand = neu;
    try { localStorage.setItem(schluessel, JSON.stringify(neu)); } catch { /* voll: gilt bis zum Neustart */ }
    hoerer.forEach(h => h());
  };
  return {
    lesen, setzen,
    aendern: f => setzen(f(lesen())),
    leeren: () => {
      stand = leer;
      try { localStorage.removeItem(schluessel); } catch { /* egal */ }
      hoerer.forEach(h => h());
    },
    abonnieren: h => { hoerer.add(h); return () => { hoerer.delete(h); }; },
  };
}

export function useLager<T>(l: Lager<T>): T {
  return useSyncExternalStore(l.abonnieren, l.lesen, l.lesen);
}
