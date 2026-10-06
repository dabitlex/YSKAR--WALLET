import { lager, useLager } from './lager.ts';

/**
 * Was diese App selbst gesendet hat und noch nicht in einem Block steht.
 *
 * Wartende Zahlungen meldet sonst nur der Knoten. Ist er gerade nicht
 * erreichbar, wuesste die App nichts mehr von einer eben gesendeten
 * Zahlung -- und liesse eine zweite mit derselben Nonce zu. Deshalb merkt
 * sie sich die eigenen, bis sie bestaetigt sind oder verfallen.
 */
export interface Gesendet {
  txid: string;
  an: string;
  betrag: string;
  gebuehr: string;
  nonce: string;
  notiz: string;
  /** Unix-Sekunden. */
  zeit: number;
}

export const gesendetLager = lager<Gesendet[]>('yskar.gesendet', [], roh =>
  Array.isArray(roh) ? roh.filter((g): g is Gesendet => !!g && typeof g.txid === 'string' && typeof g.an === 'string').slice(-50) : []);

export const gesendetMerken = (g: Gesendet): void => gesendetLager.aendern(alt => [...alt.filter(x => x.txid !== g.txid), g].slice(-50));

/** Aufraeumen: bestaetigte Zahlungen und alles, was aelter als zwei Tage ist. */
export function gesendetAufraeumen(bestaetigt: Set<string>, kontoNonce: bigint): void {
  const grenze = Math.floor(Date.now() / 1000) - 2 * 86400;
  const alt = gesendetLager.lesen();
  const neu = alt.filter(g => !bestaetigt.has(g.txid) && g.zeit > grenze && BigInt(g.nonce) >= kontoNonce);
  if (neu.length !== alt.length) gesendetLager.setzen(neu);
}

export const useGesendet = (): Gesendet[] => useLager(gesendetLager);
