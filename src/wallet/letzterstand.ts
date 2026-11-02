import { lager } from './lager.ts';
import type { Konto, VerlaufEintrag, Wartend } from '../netz/api.ts';

/**
 * Der letzte Kontostand, den der Spiegel geliefert hat -- damit die Wallet
 * nach einem Start ohne Netz nicht leer dasteht.
 *
 * Nur oeffentliche Kettendaten (Guthaben, Nonce, wartende und letzte
 * Zahlungen), nichts Geheimes. Er dient NUR der Anzeige: Senden wartet auf
 * einen frischen Abruf (DatenZustand.ausSpeicher), damit keine Ueberweisung
 * mit einer alten Nonce oder Fassung entsteht. Die Hoehe der Kette wird
 * bewusst nicht gemerkt -- aus ihr waehlt Senden die Fassung.
 */
export interface LetzterStand {
  adresse: string;
  /** Wann der Stand abgerufen wurde, Unix-Sekunden. */
  zeit: number;
  konto: Konto;
}

/** So viele Zahlungen reichen fuer Startseite und den Anfang der Aktivitaet. */
export const VERLAUF_MAX = 20;

const text = (v: unknown): v is string => typeof v === 'string';
const zahl = (v: unknown): v is string => text(v) && /^\d{1,40}$/.test(v);
/**
 * Betraege und Nonces kommen als Text -- Guthaben und Nonce des Kontos aber
 * direkt aus Datenbankspalten vom Typ bigint, also als JSON-Zahl. Beides
 * gilt; gemerkt und weitergegeben wird immer Text.
 */
const ganz = (v: unknown): string | null =>
  zahl(v) ? v : typeof v === 'number' && Number.isSafeInteger(v) && v >= 0 ? String(v) : null;

function eintrag(e: unknown): VerlaufEintrag | null {
  const x = e as VerlaufEintrag | null;
  if (!x || !text(x.txid) || typeof x.height !== 'number' || !['reward', 'pool', 'in', 'out'].includes(x.kind)) return null;
  const amount = ganz(x.amount), fee = ganz(x.fee);
  return amount === null || fee === null ? null : { ...x, amount, fee };
}
function wartend(w: unknown): Wartend | null {
  const x = w as Wartend | null;
  if (!x || !text(x.txid) || (x.kind !== 'in' && x.kind !== 'out') || !text(x.from)) return null;
  const amount = ganz(x.amount), fee = ganz(x.fee), nonce = ganz(x.nonce);
  return amount === null || fee === null || nonce === null ? null : { ...x, amount, fee, nonce };
}
/** Alle oder keiner: ein unbrauchbarer Eintrag verwirft den ganzen Stand. */
function alle<T>(liste: unknown[], f: (x: unknown) => T | null): T[] | null {
  const aus: T[] = [];
  for (const x of liste) { const y = f(x); if (y === null) return null; aus.push(y); }
  return aus;
}

export function saeubern(roh: unknown): LetzterStand | null {
  const s = roh as LetzterStand | null;
  const k = s?.konto;
  if (!s || !text(s.adresse) || typeof s.zeit !== 'number' || !k || k.address !== s.adresse
    || !Array.isArray(k.history) || !Array.isArray(k.pending)) return null;
  const balance = ganz(k.balance), nonce = ganz(k.nonce);
  const history = alle(k.history, eintrag), pending = alle(k.pending, wartend);
  if (balance === null || nonce === null || history === null || pending === null) return null;
  return { ...s, konto: { ...k, balance, nonce, nextNonce: ganz(k.nextNonce) ?? nonce, history, pending } };
}

export const letzterStandLager = lager<LetzterStand | null>('yskar.letzterstand', null, saeubern);

/** Nach jedem frischen Abruf: Stand mit gekuerztem Verlauf merken. */
export function standMerken(konto: Konto, jetzt = Math.floor(Date.now() / 1000)): void {
  letzterStandLager.setzen({ adresse: konto.address, zeit: jetzt, konto: { ...konto, history: konto.history.slice(0, VERLAUF_MAX) } });
}

/** Der gemerkte Stand -- nur, wenn er zu dieser Adresse gehoert. */
export function standLesen(adresse: string): LetzterStand | null {
  const s = letzterStandLager.lesen();
  return s && s.adresse === adresse ? s : null;
}
