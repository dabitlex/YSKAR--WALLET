import { lager, useLager } from './lager.ts';
import type { Waehrung } from './kurs.ts';

export type Sprache = 'system' | 'de' | 'en';

export interface Einstellungen {
  sprache: Sprache;
  /** Sekunden im Hintergrund, nach denen die Wallet sperrt. 0 = sofort. */
  sperreNach: number;
  /** Guthaben auf dem Startbildschirm verbergen. */
  verborgen: boolean;
  /** In dieser Waehrung wird der Wert des Guthabens gezeigt. */
  waehrung: Waehrung;
}

const VORGABE: Einstellungen = { sprache: 'system', sperreNach: 60, verborgen: false, waehrung: 'eur' };

export const einstellungenLager = lager<Einstellungen>('yskar.einstellungen', VORGABE, roh => {
  const r = (roh && typeof roh === 'object' ? roh : {}) as Partial<Einstellungen>;
  return {
    sprache: r.sprache === 'de' || r.sprache === 'en' ? r.sprache : 'system',
    sperreNach: [0, 60, 300].includes(Number(r.sperreNach)) ? Number(r.sperreNach) : 60,
    verborgen: r.verborgen === true,
    waehrung: r.waehrung === 'usd' ? 'usd' : 'eur',
  };
});

export const useEinstellungen = (): Einstellungen => useLager(einstellungenLager);
export const einstellen = (teil: Partial<Einstellungen>): void => einstellungenLager.aendern(alt => ({ ...alt, ...teil }));
