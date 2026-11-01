import type { VerlaufEintrag, Wartend } from '../netz/api.ts';
import { notizAusHex } from './notiz.ts';

/** Eine Zahlung, wie die Oberflaeche sie zeigt -- bestaetigt oder wartend. */
export interface Zahlung {
  txid: string;
  art: 'ein' | 'aus' | 'belohnung' | 'pool';
  /** Die andere Seite; null bei einer Belohnung. */
  gegen: string | null;
  betrag: bigint;
  gebuehr: bigint;
  notiz: string;
  /** Unix-Sekunden; null, solange die Zeit nicht bekannt ist. */
  zeit: number | null;
  /** Blockhoehe; null = wartet noch auf den ersten Block. */
  hoehe: number | null;
  anteile?: number;
  /**
   * Nur bei wartenden Zahlungen: fuehrt der Knoten sie in seiner Liste?
   * true/false nach seiner Liste, null = nicht abrufbar oder unbekannt.
   */
  mempool?: boolean | null;
}

const gross = (s: string | null | undefined): bigint => { try { return BigInt(s ?? '0'); } catch { return 0n; } };

export function ausEintrag(e: VerlaufEintrag): Zahlung {
  const belohnung = e.kind === 'reward' || e.kind === 'pool';
  return {
    txid: e.txid,
    art: e.kind === 'reward' ? 'belohnung' : e.kind === 'pool' ? 'pool' : e.kind === 'in' ? 'ein' : 'aus',
    gegen: e.counterparty,
    betrag: gross(e.amount), gebuehr: gross(e.fee),
    // Bei einer Belohnung stehen dort Bytes des Miners, keine Nachricht.
    notiz: belohnung ? '' : notizAusHex(e.memo),
    zeit: e.timestamp && /^\d+$/.test(e.timestamp) ? Number(e.timestamp) : null,
    hoehe: e.height, anteile: e.shares,
  };
}

export function ausWartend(w: Wartend, zeit: number | null = null): Zahlung {
  return {
    txid: w.txid, art: w.kind === 'in' ? 'ein' : 'aus', gegen: w.kind === 'in' ? w.from : w.to,
    betrag: gross(w.amount), gebuehr: gross(w.fee), notiz: notizAusHex(w.memo), zeit, hoehe: null,
  };
}

/** Bestaetigungen: Der Block selbst zaehlt als die erste. */
export function bestaetigungen(z: Zahlung, spitze: number | null): number {
  if (z.hoehe === null) return 0;
  return spitze === null || spitze < z.hoehe ? 1 : spitze - z.hoehe + 1;
}

/**
 * Eine eigene wartende Zahlung, die der Knoten nicht (mehr) fuehrt. Erst nach
 * zwei Minuten: Direkt nach dem Senden kann seine Liste noch hinterherhinken.
 * Ob sie noch in einen Block kommt, ist offen -- das ist kein "abgelehnt".
 */
export function nichtGelistet(z: Zahlung, jetzt = Math.floor(Date.now() / 1000)): boolean {
  return z.hoehe === null && z.mempool === false && z.zeit !== null && jetzt - z.zeit > 120;
}

/** Mining-Filter: nur Blockbelohnungen und Pool-Anteile (kind reward und pool). */
export const istMining = (z: Zahlung): boolean => z.art === 'belohnung' || z.art === 'pool';

/** Summe eines Tages: Eingaenge plus, Ausgaenge minus, ohne Gebuehren. */
export function tagessumme(liste: Zahlung[]): bigint {
  return liste.reduce((s, z) => (z.art === 'aus' ? s - z.betrag : s + z.betrag), 0n);
}

export interface Tag { schluessel: string; zeit: number | null; zahlungen: Zahlung[] }

/** Nach Kalendertag des Geraets gruppieren, Reihenfolge bleibt. */
export function nachTagen(liste: Zahlung[]): Tag[] {
  const tage: Tag[] = [];
  for (const z of liste) {
    const d = z.zeit === null ? null : new Date(z.zeit * 1000);
    const k = d ? `${d.getFullYear()}-${d.getMonth()}-${d.getDate()}` : 'offen';
    const letzter = tage[tage.length - 1];
    if (letzter && letzter.schluessel === k) letzter.zahlungen.push(z);
    else tage.push({ schluessel: k, zeit: z.zeit, zahlungen: [z] });
  }
  return tage;
}
