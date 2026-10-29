import { CapacitorHttp } from '@capacitor/core';
import { istNativ } from '../geraet/plattform.ts';
import { alsJson, hostErlaubt, type Signiert } from '../wallet/nachricht.ts';

/**
 * Die Antwort auf eine Anmeldung per QR: genau ein POST an die Plattform.
 *
 * Nur https, nur erlaubte Hosts, keine Weiterleitungen, hoechstens 15
 * Sekunden. Gesendet wird die fertige Signatur -- der Schluessel bleibt auf
 * dem Geraet. Diese Datei kann sonst nichts: kein Senden von YSR, kein Lesen
 * von Guthaben.
 *
 * Antwort der Plattform:
 *   Erfolg  HTTP 2xx  {"ok":true}
 *   Fehler  HTTP 4xx  {"ok":false,"error":"<Text>"}
 */

export const ZEIT_MS = 15_000;
const TEXT_MAX = 200;

export type RueckrufErgebnis =
  | { ok: true }
  | { ok: false; art: 'netz' | 'weiterleitung' | 'abgelehnt'; text: string | null; status: number };

/** Fehlertext der Plattform: nur reiner Text, ohne Steuerzeichen, gekuerzt. */
function textAus(daten: unknown): string | null {
  const e = (daten as { error?: unknown } | null)?.error;
  if (typeof e !== 'string') return null;
  const sauber = e.replace(/[\u0000-\u001f\u007f-\u009f]/g, ' ').replace(/\s+/g, ' ').trim();
  if (!sauber) return null;
  const z = Array.from(sauber);
  return z.length > TEXT_MAX ? z.slice(0, TEXT_MAX - 1).join('') + '…' : sauber;
}

export function antwortLesen(status: number, daten: unknown): RueckrufErgebnis {
  if (status >= 300 && status < 400) return { ok: false, art: 'weiterleitung', text: null, status };
  if (status >= 200 && status < 300 && (daten as { ok?: unknown } | null)?.ok === true) return { ok: true };
  return { ok: false, art: 'abgelehnt', text: textAus(daten), status };
}

type Transport = (url: string, koerper: string) => Promise<{ status: number; daten: unknown }>;

const nativ: Transport = async (url, koerper) => {
  const r = await CapacitorHttp.request({
    url, method: 'POST', data: JSON.parse(koerper),
    headers: { 'content-type': 'application/json', accept: 'application/json' },
    connectTimeout: ZEIT_MS, readTimeout: ZEIT_MS, disableRedirects: true,
  });
  let d: unknown = r.data;
  if (typeof d === 'string') { try { d = JSON.parse(d); } catch { /* kein JSON */ } }
  return { status: r.status, daten: d };
};

const browser: Transport = async (url, koerper) => {
  const stopp = new AbortController();
  const uhr = setTimeout(() => stopp.abort(), ZEIT_MS);
  try {
    const r = await fetch(url, {
      method: 'POST', body: koerper, redirect: 'manual', cache: 'no-store', credentials: 'omit', signal: stopp.signal,
      headers: { 'content-type': 'application/json', accept: 'application/json' },
    });
    // Eine abgefangene Weiterleitung meldet der Browser als Status 0 ("opaqueredirect").
    if (r.type === 'opaqueredirect') return { status: 302, daten: null };
    let d: unknown = null;
    try { d = await r.json(); } catch { /* kein JSON */ }
    return { status: r.status, daten: d };
  } finally { clearTimeout(uhr); }
};

/** Den POST ausfuehren. `transport` nur fuer Tests; sonst Android oder Browser. */
export async function posten(url: string, s: Signiert, transport?: Transport): Promise<RueckrufErgebnis> {
  const weg = transport ?? (istNativ() ? nativ : browser);
  try {
    const r = await weg(url, alsJson(s));
    return antwortLesen(r.status, r.daten);
  } catch {
    return { ok: false, art: 'netz', text: null, status: 0 };
  }
}

/** Die Signatur an den Rueckruf senden -- nur an https und erlaubte Hosts. */
export async function rueckrufSenden(url: string, s: Signiert): Promise<RueckrufErgebnis> {
  let u: URL;
  try { u = new URL(url); } catch { return { ok: false, art: 'abgelehnt', text: null, status: 0 }; }
  if (u.protocol !== 'https:' || u.port !== '' || u.username !== '' || !hostErlaubt(u.hostname)) {
    return { ok: false, art: 'abgelehnt', text: null, status: 0 };
  }
  return posten(u.toString(), s);
}
