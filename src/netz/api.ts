import { CapacitorHttp } from '@capacitor/core';
import { istNativ } from '../geraet/plattform.ts';

/**
 * Verbindung zum Netz.
 *
 * LESEN geht an den Spiegel (yskar.vercel.app): Guthaben, Verlauf, Hoehe,
 * Gebuehrenlage. SENDEN geht an den Full Node, weil nur er Ueberweisungen
 * in Bloecke bringt -- genau wie in der bisherigen App.
 *
 * Die Wallet baut und signiert jede Ueberweisung auf dem Geraet. Was hier
 * hinausgeht, sind fertige Bytes: Kein Server kann Betrag oder Empfaenger
 * aendern, ohne die Signatur zu brechen.
 *
 * In der App laufen die Anfragen ueber das Netz des Geraets (CapacitorHttp)
 * statt ueber die Webansicht. Die Adressen stehen fest im Programm; die
 * beiden VITE_-Werte gibt es nur fuer den Probelauf beim Entwickeln.
 */
const SPIEGEL = ((import.meta.env.VITE_SPIEGEL as string | undefined) ?? '').replace(/\/+$/, '') || 'https://yskar.vercel.app';
const KNOTEN_PROBE = ((import.meta.env.VITE_KNOTEN as string | undefined) ?? '').replace(/\/+$/, '');
/** Gilt nur, wenn der Spiegel die Adresse des Knotens gerade nicht nennen kann. */
const KNOTEN_VORGABE = 'https://yskar-main.dynv6.net';

export const EXPLORER = 'https://www.yskar.app/explorer';

export interface VerlaufEintrag {
  txid: string; height: number; idx: number; timestamp: string | null;
  kind: 'reward' | 'pool' | 'in' | 'out';
  counterparty: string | null; shares?: number;
  amount: string; fee: string; memo: string | null;
}
export interface Wartend {
  txid: string; kind: 'in' | 'out'; from: string; to: string;
  amount: string; fee: string; nonce: string; memo: string | null;
}
export interface Konto {
  address: string; balance: string; nonce: string; nextNonce: string;
  pending: Wartend[]; pendingSource: 'fullnode' | 'unavailable';
  history: VerlaufEintrag[]; historyWeiter: string | null;
}
export interface VerlaufSeite { eintraege: VerlaufEintrag[]; weiter: string | null }
export interface Markt {
  minFee: string; mindestJeByte?: string | null; wartend: number; andrang: boolean;
  stufen: Record<'langsam' | 'normal' | 'schnell', { fee: string; block: number }>;
}
export interface Kopf { height: number | null; mempool: number }

export class NetzFehler extends Error {
  readonly art: 'offline' | 'antwort';
  readonly status: number;
  constructor(art: 'offline' | 'antwort', status = 0, text = '') {
    super(text || (art === 'offline' ? 'keine Verbindung' : `HTTP ${status}`));
    this.art = art; this.status = status;
  }
}

interface Antwort { status: number; daten: unknown }

async function anfrage(methode: 'GET' | 'POST', url: string, daten?: unknown): Promise<Antwort> {
  if (istNativ()) {
    try {
      const r = await CapacitorHttp.request({
        url, method: methode, data: daten,
        headers: { accept: 'application/json', ...(daten !== undefined ? { 'content-type': 'application/json' } : {}) },
        connectTimeout: 12_000, readTimeout: 20_000,
      });
      let d: unknown = r.data;
      if (typeof d === 'string') { try { d = JSON.parse(d); } catch { /* kein JSON */ } }
      return { status: r.status, daten: d };
    } catch (e) { throw new NetzFehler('offline', 0, String((e as Error)?.message ?? e)); }
  }
  const stopp = new AbortController();
  const uhr = setTimeout(() => stopp.abort(), 20_000);
  try {
    const r = await fetch(url, {
      method: methode, signal: stopp.signal, cache: 'no-store',
      headers: daten !== undefined ? { 'content-type': 'application/json' } : undefined,
      body: daten !== undefined ? JSON.stringify(daten) : undefined,
    });
    let d: unknown = null;
    try { d = await r.json(); } catch { /* kein JSON */ }
    return { status: r.status, daten: d };
  } catch (e) { throw new NetzFehler('offline', 0, String((e as Error)?.message ?? e)); }
  finally { clearTimeout(uhr); }
}

async function lesen<T>(pfad: string): Promise<T> {
  const r = await anfrage('GET', SPIEGEL + pfad);
  if (r.status < 200 || r.status >= 300 || !r.daten || typeof r.daten !== 'object') throw new NetzFehler('antwort', r.status);
  return r.daten as T;
}

export const kontoLaden = (adresse: string): Promise<Konto> => lesen<Konto>(`/api/v2/account/${adresse}`);
export const kopfLaden = (): Promise<Kopf> => lesen<Kopf>('/api/v2/summary');
export const marktLaden = (): Promise<Markt> => lesen<Markt>('/api/v2/fees');

export function verlaufLaden(adresse: string, o: { richtung?: 'alle' | 'ein' | 'aus'; vor?: string | null; q?: string; limit?: number } = {}): Promise<VerlaufSeite> {
  const p = new URLSearchParams();
  if (o.richtung && o.richtung !== 'alle') p.set('richtung', o.richtung);
  if (o.vor) p.set('vor', o.vor);
  if (o.q) p.set('q', o.q);
  p.set('limit', String(o.limit ?? 40));
  return lesen<VerlaufSeite>(`/api/v2/account/${adresse}/verlauf?${p}`);
}

/*
  Wo der Full Node steht, sagt der Spiegel: Seine stillgelegte Route
  /api/v2/tx antwortet mit 410 und nennt die Adresse. So braucht ein Umzug
  des Knotens keine neue APK.
*/
let knoten: string | null = null;

export async function knotenAdresse(): Promise<string> {
  if (KNOTEN_PROBE) return KNOTEN_PROBE;
  if (knoten) return knoten;
  try {
    const r = await anfrage('POST', SPIEGEL + '/api/v2/tx', {});
    const f = (r.daten as { fullnode?: unknown } | null)?.fullnode;
    if (typeof f === 'string' && /^https:\/\/[a-z0-9.-]+(:\d{1,5})?\/?$/i.test(f)) {
      knoten = f.replace(/\/+$/, '');
      return knoten;
    }
  } catch { /* dann die Vorgabe */ }
  return KNOTEN_VORGABE;
}

export type SendeErgebnis = { ok: true; txid: string | null } | { ok: false; grund: string; detail?: string };

/** Fertig signierte Ueberweisung (Hex) an den Full Node geben. */
export async function senden(rohHex: string): Promise<SendeErgebnis> {
  const basis = await knotenAdresse();
  const r = await anfrage('POST', basis + '/api/v2/tx', { raw: rohHex });
  const d = r.daten as { accepted?: boolean; txid?: string; reason?: string; detail?: string } | null;
  if (!d || typeof d !== 'object') throw new NetzFehler('antwort', r.status);
  if (d.accepted === true) return { ok: true, txid: typeof d.txid === 'string' ? d.txid.replace(/^(\\x|0x)/, '') : null };
  return { ok: false, grund: String(d.reason ?? `http_${r.status}`), detail: d.detail };
}
