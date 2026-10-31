import { lager, useLager } from './lager.ts';

export type Sprache = 'system' | 'de' | 'en';

/** Nachkommastellen in der Uebersicht: 8 = exakt (wie die Kette), 4 oder 2 = abgeschnitten. */
export type AnzeigeStellen = 8 | 4 | 2;

export interface Einstellungen {
  sprache: Sprache;
  /** Sekunden im Hintergrund, nach denen die Wallet sperrt. 0 = sofort. */
  sperreNach: number;
  /** Guthaben auf dem Startbildschirm verbergen. */
  verborgen: boolean;
  /** Die Einfuehrung wurde geoeffnet oder ihre Karte auf dem Startbildschirm geschlossen. */
  einfuehrungGesehen: boolean;
  /** Nachkommastellen fuer Guthaben, Verlauf und Kontakte. Senden und Details bleiben immer exakt. */
  stellen: AnzeigeStellen;
}

const VORGABE: Einstellungen = { sprache: 'system', sperreNach: 60, verborgen: false, einfuehrungGesehen: false, stellen: 8 };

export const einstellungenLager = lager<Einstellungen>('yskar.einstellungen', VORGABE, roh => {
  const r = (roh && typeof roh === 'object' ? roh : {}) as Partial<Einstellungen>;
  return {
    sprache: r.sprache === 'de' || r.sprache === 'en' ? r.sprache : 'system',
    sperreNach: [0, 60, 300].includes(Number(r.sperreNach)) ? Number(r.sperreNach) : 60,
    verborgen: r.verborgen === true,
    einfuehrungGesehen: r.einfuehrungGesehen === true,
    stellen: r.stellen === 4 || r.stellen === 2 ? r.stellen : 8,
  };
});

export const useEinstellungen = (): Einstellungen => useLager(einstellungenLager);
export const einstellen = (teil: Partial<Einstellungen>): void => einstellungenLager.aendern(alt => ({ ...alt, ...teil }));
