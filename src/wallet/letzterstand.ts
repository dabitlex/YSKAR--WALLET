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

function eintragOk(e: unknown): e is VerlaufEintrag {
  const x = e as VerlaufEintrag;
  return !!x && text(x.txid) && typeof x.height === 'number' && ['reward', 'pool', 'in', 'out'].includes(x.kind)
    && zahl(x.amount) && zahl(x.fee);
}
function wartendOk(w: unknown): w is Wartend {
  const x = w as Wartend;
  return !!x && text(x.txid) && (x.kind === 'in' || x.kind === 'out') && text(x.from) && zahl(x.amount) && zahl(x.fee) && zahl(x.nonce);
}

export function saeubern(roh: unknown): LetzterStand | null {
  const s = roh as LetzterStand | null;
  const k = s?.konto;
  if (!s || !text(s.adresse) || typeof s.zeit !== 'number' || !k || k.address !== s.adresse
    || !zahl(k.balance) || !zahl(k.nonce) || !Array.isArray(k.history) || !Array.isArray(k.pending)) return null;
  if (!k.history.every(eintragOk) || !k.pending.every(wartendOk)) return null;
  return s;
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
