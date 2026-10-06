import * as speicher from '../geraet/speicher.ts';

/**
 * Schluesselverwahrung im Geraet.
 *
 * Die 12 Woerter werden NICHT im Klartext abgelegt. Sie werden mit der PIN
 * verschluesselt:
 *
 *   PIN -> PBKDF2-SHA256, 400.000 Runden, 16 Byte Salz -> AES-256-GCM
 *
 * Dasselbe Verfahren und dasselbe Format wie in der bisherigen App. Neu
 * ist der Ort: Der verschluesselte Tresor liegt hier im geschuetzten
 * Speicher des Geraets (Android Keystore) und nicht im Speicher der
 * Webansicht.
 *
 * WIE STARK IST DAS -- ohne Schoenrechnerei: Sechs Ziffern sind eine
 * Million Moeglichkeiten. Wer den verschluesselten Tresor in die Hand
 * bekommt (dafuer braucht es Root-Zugriff auf das Geraet), probiert sie in
 * Stunden durch. Die PIN schuetzt das entsperrte Telefon in fremder Hand.
 * Der eigentliche Schutz sind die aufgeschriebenen 12 Woerter.
 */

const ORT = 'yskar.tresor.v1';
const RUNDEN = 400_000;

export interface Tresor {
  version: 1;
  salt: string;        // base64
  iv: string;          // base64
  ciphertext: string;  // base64
  address: string;     // im Klartext -- die Adresse ist oeffentlich
  createdAt: string;
}

const enc = new TextEncoder();
const dec = new TextDecoder();
const toB64 = (b: Uint8Array) => btoa(String.fromCharCode(...b));
const fromB64 = (s: string) => Uint8Array.from(atob(s), c => c.charCodeAt(0));

async function schluessel(pin: string, salt: Uint8Array): Promise<CryptoKey> {
  const basis = await crypto.subtle.importKey('raw', enc.encode(pin), 'PBKDF2', false, ['deriveKey']);
  return crypto.subtle.deriveKey(
    { name: 'PBKDF2', salt: salt as BufferSource, iterations: RUNDEN, hash: 'SHA-256' },
    basis, { name: 'AES-GCM', length: 256 }, false, ['encrypt', 'decrypt'],
  );
}

export async function versiegeln(woerter: string, pin: string, address: string): Promise<Tresor> {
  const salt = crypto.getRandomValues(new Uint8Array(16));
  const iv = crypto.getRandomValues(new Uint8Array(12));
  const k = await schluessel(pin, salt);
  const geheim = new Uint8Array(await crypto.subtle.encrypt(
    { name: 'AES-GCM', iv: iv as BufferSource }, k, enc.encode(woerter)));
  return { version: 1, salt: toB64(salt), iv: toB64(iv), ciphertext: toB64(geheim),
           address, createdAt: new Date().toISOString() };
}

/** Die Woerter, oder null bei falscher PIN. AES-GCM prueft die Echtheit mit. */
export async function oeffnen(t: Tresor, pin: string): Promise<string | null> {
  try {
    const k = await schluessel(pin, fromB64(t.salt));
    const klar = await crypto.subtle.decrypt(
      { name: 'AES-GCM', iv: fromB64(t.iv) as BufferSource }, k, fromB64(t.ciphertext) as BufferSource);
    return dec.decode(klar);
  } catch { return null; }
}

export async function laden(): Promise<Tresor | null> {
  const roh = await speicher.lesen(ORT);
  if (!roh) return null;
  try {
    const t = JSON.parse(roh) as Tresor;
    return t.version === 1 && t.ciphertext && t.address ? t : null;
  } catch { return null; }
}

export const speichern = (t: Tresor): Promise<void> => speicher.schreiben(ORT, JSON.stringify(t));
export const loeschen = (): Promise<void> => speicher.entfernen(ORT);

/*
  Bremse gegen Durchprobieren am entsperrten Geraet: Nach fuenf falschen
  Eingaben eine Wartezeit, die sich mit jeder weiteren verdoppelt.
*/
const BREMSE = 'yskar.pin.bremse';
interface Bremse { fehl: number; bis: number }

function bremseLesen(): Bremse {
  try {
    const b = JSON.parse(localStorage.getItem(BREMSE) ?? '{}');
    return { fehl: Number(b.fehl) || 0, bis: Number(b.bis) || 0 };
  } catch { return { fehl: 0, bis: 0 }; }
}

/** Sekunden, die noch zu warten sind. 0 = Eingabe erlaubt. */
export function wartezeit(): number {
  return Math.max(0, Math.ceil((bremseLesen().bis - Date.now()) / 1000));
}

export function falschePin(): void {
  const b = bremseLesen();
  const fehl = b.fehl + 1;
  const sek = fehl < 5 ? 0 : Math.min(900, 30 * 2 ** (fehl - 5));
  try { localStorage.setItem(BREMSE, JSON.stringify({ fehl, bis: sek ? Date.now() + sek * 1000 : 0 })); } catch { /* egal */ }
}

export function richtigePin(): void {
  try { localStorage.removeItem(BREMSE); } catch { /* egal */ }
}
