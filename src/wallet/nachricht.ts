import { sign, verifySignature } from '../kern/wallet.ts';
import { addressFor, isValidAddress } from '../kern/address.ts';
import { toHex, fromHex } from '../kern/codec.ts';

/**
 * Nachrichten signieren -- fuer die Anmeldung auf YSKAR Lab und fuer freie Texte.
 *
 * Signiert werden die rohen UTF-8-Bytes der vollstaendigen Nachricht mit
 * Ed25519, ohne zusaetzliches Hashen und ohne verstecktes Praefix. Jede
 * Nachricht beginnt sichtbar mit der Zeile "YSKAR Signed Message". Damit kann
 * eine signierte Nachricht nie als Ueberweisung gelten: Ueberweisungen sind
 * Binaerdaten mit der Kennung der Kette, eine Nachricht ist Text mit dieser
 * Kopfzeile.
 *
 * Diese Datei kennt weder den Sendepfad noch das Netz. Sie bekommt einen
 * Schluessel, gibt eine Signatur zurueck und sonst nichts.
 *
 *   Anmeldung (feste Zeilen, genau diese Reihenfolge):
 *     YSKAR Signed Message
 *     purpose: login
 *     domain: lab.yskar.app
 *     address: ysr1...
 *     nonce: <32 Hex-Zeichen>
 *     issued: 2026-11-02T11:50:00Z
 *     expires: 2026-11-02T11:55:00Z
 *
 *   Freier Text:
 *     YSKAR Signed Message
 *     purpose: text
 *     <Leerzeile>
 *     <Text>
 */

export const KOPF = 'YSKAR Signed Message';

/**
 * Wofuer die Wallet eine Anmeldung signiert: diese Hosts und ihre Subdomains.
 * Weitere Hosts nur hier eintragen.
 */
export const ERLAUBTE_HOSTS: readonly string[] = ['yskar.app'];

/** Hoechstens so viele Zeichen Text in einer freien Nachricht. */
export const TEXT_MAX = 1000;

/** Eine Anmeldung darf hoechstens so lange in der Zukunft ablaufen. */
export const GUELTIG_MAX_MS = 15 * 60 * 1000;

export type Ablehnung =
  | 'leer'               // nichts eingegeben
  | 'zu_lang'            // mehr als TEXT_MAX Zeichen
  | 'kopf_fehlt'         // ohne Kopfzeile, wo sie Pflicht ist
  | 'zeilenende'         // \r in einer Nachricht mit Kopfzeile
  | 'leerzeichen'        // Leerzeichen am Zeilenende in einer Nachricht mit Kopfzeile
  | 'zweck_unbekannt'    // purpose weder login noch text
  | 'aufbau'             // Zeilen fehlen, falsche Reihenfolge, falsches Format
  | 'domain'             // Domain nicht erlaubt
  | 'adresse_fremd'      // Adresse ist nicht die dieser Wallet
  | 'abgelaufen'         // expires liegt in der Vergangenheit
  | 'zu_lange'           // expires liegt mehr als 15 Minuten in der Zukunft
  | 'hex'                // Text sieht aus wie Hex-Daten
  // nur bei QR-Codes und yskar:-Links
  | 'qr_format'          // kein lesbarer Anmelde-Code
  | 'kein_login'         // Code enthaelt keine Anmeldung
  | 'rueckruf_https'     // Rueckruf nicht ueber https, mit Port oder Benutzername
  | 'rueckruf_host';     // Rueckruf geht nicht an die Domain der Nachricht

export interface Anmeldung {
  domain: string;
  address: string;
  nonce: string;
  issued: Date;
  expires: Date;
}

export type Pruefung =
  | { ok: true; art: 'login'; nachricht: string; anmeldung: Anmeldung }
  | { ok: true; art: 'text'; nachricht: string; text: string; ergaenzt: boolean }
  | { ok: false; grund: Ablehnung };

export interface Signiert {
  address: string;
  publicKey: string;   // 64 Hex-Zeichen
  signature: string;   // 128 Hex-Zeichen
  message: string;
}

/** yskar.app und jede Subdomain davon. Gross/klein egal, kein Port, kein Pfad. */
export function hostErlaubt(host: string): boolean {
  const h = host.toLowerCase();
  if (!/^[a-z0-9]([a-z0-9-]*[a-z0-9])?(\.[a-z0-9]([a-z0-9-]*[a-z0-9])?)+$/.test(h)) return false;
  return ERLAUBTE_HOSTS.some(b => h === b || h.endsWith('.' + b));
}

/** Sieht aus wie Hex-Daten: nur Hex-Zeichen (mit Leerraum, optional 0x) und mindestens 32 davon. */
function wieHex(text: string): boolean {
  const ohne = text.replace(/\s+/g, '').replace(/^0x/i, '');
  return ohne.length >= 32 && /^[0-9a-fA-F]+$/.test(ohne);
}

const ZEIT = /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}Z$/;

function zeitLesen(s: string): Date | null {
  if (!ZEIT.test(s)) return null;
  const d = new Date(s);
  // Ungueltige Kalendertage (z. B. 31.02.) rechnet Date um; dann passt die Rueckrichtung nicht.
  return Number.isNaN(d.getTime()) || d.toISOString().slice(0, 19) + 'Z' !== s ? null : d;
}

function feld(zeile: string | undefined, name: string): string | null {
  const vorn = name + ': ';
  return zeile !== undefined && zeile.startsWith(vorn) && zeile.length > vorn.length ? zeile.slice(vorn.length) : null;
}

function textPruefen(text: string): Ablehnung | null {
  if (text.trim() === '') return 'leer';
  if (Array.from(text).length > TEXT_MAX) return 'zu_lang';
  if (wieHex(text)) return 'hex';
  return null;
}

/**
 * Pruefen, was signiert werden soll, und die genaue Nachricht bauen.
 *
 * Mit Kopfzeile gilt die Nachricht Byte fuer Byte, wie sie ist: kein \r,
 * keine Leerzeichen am Zeilenende, bekannte Zwecke. Ohne Kopfzeile ist es
 * freier Text: \r und Leerzeichen am Zeilenende werden entfernt, dann setzt
 * die Wallet die Kopfzeilen davor. Bei einem QR-Code ist die Kopfzeile Pflicht.
 */
export function vorbereiten(eingabe: string, eigeneAdresse: string, jetzt: number, o: { qr?: boolean } = {}): Pruefung {
  if (eingabe.trim() === '') return { ok: false, grund: 'leer' };

  // Kopfzeile = genau diese erste Zeile. "YSKAR Signed Messages …" ist freier Text.
  const hatKopf = eingabe === KOPF || eingabe.startsWith(KOPF + '\n') || eingabe.startsWith(KOPF + '\r');
  if (!hatKopf) {
    if (o.qr) return { ok: false, grund: 'kopf_fehlt' };
    const text = eingabe.replace(/\r/g, '').split('\n').map(z => z.replace(/[ \t]+$/, '')).join('\n')
      .replace(/^\n+/, '').replace(/\n+$/, '');
    const fehler = textPruefen(text);
    if (fehler) return { ok: false, grund: fehler };
    return { ok: true, art: 'text', nachricht: `${KOPF}\npurpose: text\n\n${text}`, text, ergaenzt: true };
  }

  if (eingabe.includes('\r')) return { ok: false, grund: 'zeilenende' };
  const zeilen = eingabe.split('\n');
  if (zeilen[0] !== KOPF) return { ok: false, grund: 'aufbau' };
  if (zeilen.some(z => /[ \t]$/.test(z))) return { ok: false, grund: 'leerzeichen' };

  const zweck = feld(zeilen[1], 'purpose');
  if (zweck === null) return { ok: false, grund: 'aufbau' };

  if (zweck === 'text') {
    if (zeilen[2] !== '' || zeilen.length < 4) return { ok: false, grund: 'aufbau' };
    const text = zeilen.slice(3).join('\n');
    const fehler = textPruefen(text);
    if (fehler) return { ok: false, grund: fehler };
    return { ok: true, art: 'text', nachricht: eingabe, text, ergaenzt: false };
  }

  if (zweck !== 'login') return { ok: false, grund: 'zweck_unbekannt' };

  if (zeilen.length !== 7) return { ok: false, grund: 'aufbau' };
  const domain = feld(zeilen[2], 'domain');
  const address = feld(zeilen[3], 'address');
  const nonce = feld(zeilen[4], 'nonce');
  const issuedText = feld(zeilen[5], 'issued');
  const expiresText = feld(zeilen[6], 'expires');
  if (domain === null || address === null || nonce === null || issuedText === null || expiresText === null) return { ok: false, grund: 'aufbau' };
  if (!/^[0-9a-f]{32}$/.test(nonce)) return { ok: false, grund: 'aufbau' };
  const issued = zeitLesen(issuedText), expires = zeitLesen(expiresText);
  if (!issued || !expires || issued.getTime() > expires.getTime()) return { ok: false, grund: 'aufbau' };
  if (!hostErlaubt(domain) || domain !== domain.toLowerCase()) return { ok: false, grund: 'domain' };
  if (!isValidAddress(address) || address !== eigeneAdresse) return { ok: false, grund: 'adresse_fremd' };
  if (expires.getTime() <= jetzt) return { ok: false, grund: 'abgelaufen' };
  if (expires.getTime() - jetzt > GUELTIG_MAX_MS) return { ok: false, grund: 'zu_lange' };

  return { ok: true, art: 'login', nachricht: eingabe, anmeldung: { domain, address, nonce, issued, expires } };
}

/**
 * Die fertige Nachricht signieren. Prueft vorher noch einmal Kopfzeile und
 * Zeilenenden -- was hier ankommt, ist schon durch vorbereiten() gegangen.
 */
export function signieren(nachricht: string, privateKey: Uint8Array, publicKey: Uint8Array): Signiert {
  if (!nachricht.startsWith(KOPF + '\n') || nachricht.includes('\r')) throw new Error('keine gueltige Nachricht');
  const signatur = sign(new TextEncoder().encode(nachricht), privateKey);
  return { address: addressFor(publicKey), publicKey: toHex(publicKey), signature: toHex(signatur), message: nachricht };
}

/** Alles zusammen zum Kopieren, Felder in fester Reihenfolge. */
export function alsJson(s: Signiert): string {
  return JSON.stringify({ address: s.address, publicKey: s.publicKey, signature: s.signature, message: s.message });
}

/** Eingefuegtes JSON lesen. null, wenn es nicht die vier Felder als Text enthaelt. */
export function ausJson(text: string): Signiert | null {
  try {
    const o = JSON.parse(text.trim()) as Record<string, unknown>;
    const { address, publicKey, signature, message } = o;
    if ([address, publicKey, signature, message].some(v => typeof v !== 'string')) return null;
    return { address, publicKey, signature, message } as Signiert;
  } catch { return null; }
}

export type PruefErgebnis = 'gueltig' | 'ungueltig' | 'adresse_passt_nicht' | 'format';

/**
 * Eine Signatur pruefen: Schluessel und Adresse muessen zusammenpassen, die
 * Signatur muss ueber genau diese Nachricht gehen. Die Nachricht wird nicht
 * veraendert, auch nicht an den Zeilenenden.
 */
export function pruefen(s: Signiert): PruefErgebnis {
  const pk = s.publicKey.trim().toLowerCase(), sig = s.signature.trim().toLowerCase(), adr = s.address.trim();
  if (!/^[0-9a-f]{64}$/.test(pk) || !/^[0-9a-f]{128}$/.test(sig) || !isValidAddress(adr) || s.message === '') return 'format';
  const schluessel = fromHex(pk);
  if (addressFor(schluessel) !== adr) return 'adresse_passt_nicht';
  return verifySignature(fromHex(sig), new TextEncoder().encode(s.message), schluessel) ? 'gueltig' : 'ungueltig';
}

/* -------------------------------------------------------- Anmelden per QR */

/*
  Die Plattform zeigt einen QR-Code (oder einen Link auf demselben Handy):

    yskar:login?msg=<Nachricht base64url>&cb=<https-Rueckruf base64url>

  Die Wallet signiert nur, wenn die Nachricht eine gueltige Anmeldung fuer
  diese Wallet ist und der Rueckruf ueber https genau an die Domain der
  Nachricht geht. Erst nach PIN oder Fingerabdruck geht die Antwort hinaus.
*/

export type QrPruefung =
  | { ok: true; nachricht: string; anmeldung: Anmeldung; rueckruf: string }
  | { ok: false; grund: Ablehnung; domain?: string; rueckrufHost?: string };

/** Schnelle Erkennung, ohne zu pruefen: Ist das ein Anmelde-Code? */
export function istAnmeldeCode(text: string): boolean {
  return /^yskar:(\/\/)?login\?/i.test(text.trim());
}

/** base64url (ohne Auffuellung) streng lesen und als UTF-8 deuten. null bei Fehlern. */
export function base64urlText(s: string): string | null {
  if (!/^[A-Za-z0-9_-]*$/.test(s) || s.length % 4 === 1) return null;
  try {
    const b64 = s.replace(/-/g, '+').replace(/_/g, '/') + '='.repeat((4 - (s.length % 4)) % 4);
    const bytes = Uint8Array.from(atob(b64), c => c.charCodeAt(0));
    return new TextDecoder('utf-8', { fatal: true }).decode(bytes);
  } catch { return null; }
}

/** Fuer Tests und die Plattform: Text als base64url ohne Auffuellung. */
export function textBase64url(text: string): string {
  const bytes = new TextEncoder().encode(text);
  let s = '';
  for (const b of bytes) s += String.fromCharCode(b);
  return btoa(s).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}

/** Darf an diese Adresse geantwortet werden? https, kein Port, kein Benutzername, erlaubter Host. */
export function rueckrufErlaubt(url: string, domain: string): Ablehnung | null {
  let u: URL;
  try { u = new URL(url); } catch { return 'rueckruf_https'; }
  if (u.protocol !== 'https:' || u.port !== '' || u.username !== '' || u.password !== '') return 'rueckruf_https';
  if (u.hostname !== domain || !hostErlaubt(u.hostname)) return 'rueckruf_host';
  return null;
}

export function anmeldungAusCode(text: string, eigeneAdresse: string, jetzt: number): QrPruefung {
  const t = text.trim();
  if (!istAnmeldeCode(t)) return { ok: false, grund: 'qr_format' };
  let p: URLSearchParams;
  try { p = new URLSearchParams(t.slice(t.indexOf('?') + 1)); } catch { return { ok: false, grund: 'qr_format' }; }
  if (p.getAll('msg').length !== 1 || p.getAll('cb').length !== 1) return { ok: false, grund: 'qr_format' };
  const nachricht = base64urlText(p.get('msg')!);
  const rueckruf = base64urlText(p.get('cb')!);
  if (nachricht === null || rueckruf === null) return { ok: false, grund: 'qr_format' };

  const v = vorbereiten(nachricht, eigeneAdresse, jetzt, { qr: true });
  if (!v.ok) return { ok: false, grund: v.grund };
  if (v.art !== 'login') return { ok: false, grund: 'kein_login' };

  const fehler = rueckrufErlaubt(rueckruf, v.anmeldung.domain);
  if (fehler) {
    let host: string | undefined;
    try { host = new URL(rueckruf).host; } catch { host = undefined; }
    return { ok: false, grund: fehler, domain: v.anmeldung.domain, rueckrufHost: host };
  }
  return { ok: true, nachricht: v.nachricht, anmeldung: v.anmeldung, rueckruf };
}
