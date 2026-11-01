import type { Konto } from '../netz/api.ts';

/**
 * Wo steht eine eben gesendete Zahlung? Fuenf Schritte, und jeder gilt erst,
 * wenn eine Antwort ihn belegt:
 *
 *   1 lokal signiert         -- die App hat sie auf dem Geraet gebaut und signiert
 *   2 vom Knoten angenommen  -- der Knoten hat auf das Einreichen "angenommen" geantwortet
 *   3 im Mempool             -- der Knoten fuehrt sie in seiner Liste (pendingSource "fullnode")
 *   4 in einem Block         -- der Verlauf nennt sie mit einer Hoehe
 *   5 weitere Bestaetigungen -- ab zwei Bestaetigungen (der Block selbst zaehlt als erste)
 *
 * Eine erfolgreiche Antwort auf das Einreichen ist Schritt 2, nicht mehr.
 * "Endgueltig" gibt es nicht: Das Netz hat keine Checkpoints und keine
 * Grenze fuer Reorganisationen.
 */
export type Teil = 'erledigt' | 'wartet' | 'offen' | 'fehler';

export interface Netzbild {
  /** true/false nach der Liste des Knotens; null = nicht abrufbar. */
  imMempool: boolean | null;
  /** Hoehe des Blocks, in dem sie steht; null = in keinem bekannten Block. */
  hoehe: number | null;
  /** Bestaetigungen; 0 ohne Block. */
  bestaetigungen: number;
}

export function netzbild(konto: Konto | null, spitze: number | null, txid: string): Netzbild {
  const eintrag = konto?.history.find(e => e.txid === txid) ?? null;
  const hoehe = eintrag ? eintrag.height : null;
  const bestaetigungen = hoehe === null ? 0 : spitze === null || spitze < hoehe ? 1 : spitze - hoehe + 1;
  const imMempool = !konto || konto.pendingSource !== 'fullnode' ? null : konto.pending.some(p => p.txid === txid);
  return { imMempool, hoehe, bestaetigungen };
}

/** Steht die Zahlung irgendwo, wo der Knoten oder die Kette sie zeigt? */
export const imNetzGesehen = (n: Netzbild): boolean => n.imMempool === true || n.hoehe !== null;

export interface Schritte {
  signiert: Teil;
  angenommen: Teil;
  mempool: Teil;
  block: Teil;
  weitere: Teil;
}

/**
 * Die fuenf Schritte nach dem, was belegt ist.
 * `gesehen`: Die App hat sie in dieser Sitzung schon einmal in der Liste des Knotens gesehen.
 */
export function schritte(n: Netzbild, gesehen: boolean): Schritte {
  const imBlock = n.hoehe !== null;
  const mempool: Teil = imBlock || n.imMempool === true || (gesehen && n.imMempool === null) ? 'erledigt' : 'wartet';
  return {
    signiert: 'erledigt',
    angenommen: 'erledigt',
    mempool,
    block: imBlock ? 'erledigt' : mempool === 'erledigt' ? 'wartet' : 'offen',
    weitere: n.bestaetigungen >= 2 ? 'erledigt' : imBlock ? 'wartet' : 'offen',
  };
}

/** Das Y-Zeichen: Band 1 angenommen, Band 2 im Block, Stamm ab zwei Bestaetigungen. */
export function yStand(s: Schritte): [Teil, Teil, Teil] {
  return [s.angenommen, s.block === 'erledigt' ? 'erledigt' : 'wartet', s.weitere === 'erledigt' ? 'erledigt' : s.block === 'erledigt' ? 'wartet' : 'offen'];
}
