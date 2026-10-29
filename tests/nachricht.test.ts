/*
  Nachrichten signieren: Format, Ablehnungsregeln und feste Testvektoren.

  Die Gegenprobe laeuft mit der Ed25519-Umsetzung von Node selbst (node:crypto),
  also unabhaengig von der Bibliothek, mit der die Wallet signiert.
*/
import test from 'node:test';
import assert from 'node:assert/strict';
import { createPublicKey, verify as nodeVerify, createPrivateKey, sign as nodeSign } from 'node:crypto';
import { keypairFromMnemonic } from '../src/kern/wallet.ts';
import { toHex, fromHex } from '../src/kern/codec.ts';
import {
  KOPF, vorbereiten, signieren, pruefen, alsJson, ausJson, hostErlaubt, GUELTIG_MAX_MS, TEXT_MAX,
} from '../src/wallet/nachricht.ts';

const WOERTER = 'abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about';
const kp = keypairFromMnemonic(WOERTER);
const ANDERE = keypairFromMnemonic('zoo zoo zoo zoo zoo zoo zoo zoo zoo zoo zoo wrong');
const JETZT = Date.parse('2026-11-02T11:51:00Z');

function anmeldung(f: Partial<Record<'domain' | 'address' | 'nonce' | 'issued' | 'expires', string>> = {}): string {
  return [
    KOPF, 'purpose: login',
    `domain: ${f.domain ?? 'lab.yskar.app'}`,
    `address: ${f.address ?? kp.address}`,
    `nonce: ${f.nonce ?? '9f2c4e1ab37d4c6e8a05b1d2e3f4a5b6'}`,
    `issued: ${f.issued ?? '2026-11-02T11:50:00Z'}`,
    `expires: ${f.expires ?? '2026-11-02T11:55:00Z'}`,
  ].join('\n');
}

/* ---------------------------------------------------------------- Vektoren */

// Feste Erwartung: Ed25519 ist deterministisch, dieselbe Nachricht ergibt mit
// demselben Schluessel immer dieselbe Signatur.
const VEKTOR = {
  address: 'ysr1pcy6c8vw2fpp49qh4pg3w348qz6nn6vw2fg7f0',
  publicKey: '34792b244cfe851d8b3fc62ccb3033297ea2ca5bc31119b397bc3ec4132a751d',
  textNachricht: `${KOPF}\npurpose: text\n\nHello YSKAR`,
};

function nodePruefen(nachricht: string, sigHex: string, pkHex: string): boolean {
  // SPKI-Huelle fuer einen rohen Ed25519-Schluessel (RFC 8410).
  const spki = Buffer.concat([Buffer.from('302a300506032b6570032100', 'hex'), Buffer.from(pkHex, 'hex')]);
  const schluessel = createPublicKey({ key: spki, format: 'der', type: 'spki' });
  return nodeVerify(null, Buffer.from(nachricht, 'utf8'), schluessel, Buffer.from(sigHex, 'hex'));
}

test('Testschluessel: Adresse und oeffentlicher Schluessel wie erwartet', () => {
  assert.equal(kp.address, VEKTOR.address);
  assert.equal(toHex(kp.publicKey), VEKTOR.publicKey);
});

test('Signatur ueber die rohen UTF-8-Bytes, Gegenprobe mit node:crypto', () => {
  const s = signieren(VEKTOR.textNachricht, kp.privateKey, kp.publicKey);
  assert.equal(s.address, VEKTOR.address);
  assert.equal(s.publicKey, VEKTOR.publicKey);
  assert.match(s.signature, /^[0-9a-f]{128}$/);
  assert.equal(s.message, VEKTOR.textNachricht);
  assert.ok(nodePruefen(s.message, s.signature, s.publicKey), 'node:crypto bestaetigt die Signatur');

  // Node signiert dieselben Bytes mit demselben Schluessel -> dieselbe Signatur.
  const pkcs8 = Buffer.concat([Buffer.from('302e020100300506032b657004220420', 'hex'), Buffer.from(kp.privateKey)]);
  const nodeSig = nodeSign(null, Buffer.from(VEKTOR.textNachricht, 'utf8'), createPrivateKey({ key: pkcs8, format: 'der', type: 'pkcs8' }));
  assert.equal(s.signature, nodeSig.toString('hex'));
});

test('Umlaute und Emoji: signiert werden die UTF-8-Bytes, nicht UTF-16', () => {
  const n = `${KOPF}\npurpose: text\n\nGrüße aus Hessen ✓`;
  const s = signieren(n, kp.privateKey, kp.publicKey);
  assert.ok(nodePruefen(n, s.signature, s.publicKey));
});

test('Anmeldenachricht: Signatur von node:crypto bestaetigt, verify gueltig', () => {
  const n = anmeldung();
  const p = vorbereiten(n, kp.address, JETZT);
  assert.ok(p.ok && p.art === 'login');
  const s = signieren(p.nachricht, kp.privateKey, kp.publicKey);
  assert.equal(s.message, n, 'Login wird Byte fuer Byte so signiert, wie er kam');
  assert.ok(nodePruefen(n, s.signature, s.publicKey));
  assert.equal(pruefen(s), 'gueltig');
});

/* -------------------------------------------------------- Format und Pruefen */

test('Freier Text: Wallet setzt die Kopfzeilen davor', () => {
  const p = vorbereiten('Hello YSKAR', kp.address, JETZT);
  assert.ok(p.ok && p.art === 'text');
  assert.equal(p.nachricht, VEKTOR.textNachricht);
  assert.equal(p.ergaenzt, true);
});

test('Freier Text: \\r und Leerzeichen am Zeilenende werden entfernt', () => {
  const p = vorbereiten('Zeile eins  \r\nZeile zwei\t\r\n', kp.address, JETZT);
  assert.ok(p.ok);
  assert.equal(p.nachricht, `${KOPF}\npurpose: text\n\nZeile eins\nZeile zwei`);
});

test('Freier Text mit eigener Kopfzeile bleibt unveraendert', () => {
  const p = vorbereiten(VEKTOR.textNachricht, kp.address, JETZT);
  assert.ok(p.ok && p.art === 'text');
  assert.equal(p.nachricht, VEKTOR.textNachricht);
  assert.equal(p.ergaenzt, false);
});

test('Text, der nur mit den Worten der Kopfzeile beginnt, ist freier Text', () => {
  const p = vorbereiten('YSKAR Signed Messages are useful', kp.address, JETZT);
  assert.ok(p.ok && p.art === 'text' && p.ergaenzt);
});

test('JSON zum Kopieren: feste Reihenfolge, wieder lesbar, verify gueltig', () => {
  const s = signieren(VEKTOR.textNachricht, kp.privateKey, kp.publicKey);
  const j = alsJson(s);
  assert.deepEqual(Object.keys(JSON.parse(j)), ['address', 'publicKey', 'signature', 'message']);
  const zurueck = ausJson(j);
  assert.deepEqual(zurueck, s);
  assert.equal(pruefen(zurueck!), 'gueltig');
});

test('verify erkennt veraenderte Nachricht, fremden Schluessel und Unsinn', () => {
  const s = signieren(VEKTOR.textNachricht, kp.privateKey, kp.publicKey);
  assert.equal(pruefen({ ...s, message: s.message + '!' }), 'ungueltig');
  assert.equal(pruefen({ ...s, message: s.message.replace(/\n/g, '\r\n') }), 'ungueltig');
  assert.equal(pruefen({ ...s, address: ANDERE.address }), 'adresse_passt_nicht');
  assert.equal(pruefen({ ...s, publicKey: toHex(ANDERE.publicKey), address: ANDERE.address }), 'ungueltig');
  assert.equal(pruefen({ ...s, signature: 'zz' }), 'format');
  assert.equal(ausJson('{"address":1}'), null);
  assert.equal(ausJson('kein json'), null);
});

/* -------------------------------------------------------- Ablehnungsregeln */

const grund = (eingabe: string, o: { qr?: boolean; jetzt?: number; adresse?: string } = {}) => {
  const p = vorbereiten(eingabe, o.adresse ?? kp.address, o.jetzt ?? JETZT, { qr: o.qr });
  return p.ok ? 'ok' : p.grund;
};

test('Ablehnung: falsche oder fremde Domain', () => {
  assert.equal(grund(anmeldung({ domain: 'evil.com' })), 'domain');
  assert.equal(grund(anmeldung({ domain: 'yskar.app.evil.com' })), 'domain');
  assert.equal(grund(anmeldung({ domain: 'notyskar.app' })), 'domain');
  assert.equal(grund(anmeldung({ domain: 'LAB.YSKAR.APP' })), 'domain');
  assert.equal(grund(anmeldung({ domain: 'lab.yskar.app:443' })), 'domain');
  assert.equal(grund(anmeldung({ domain: 'yskar.app' })), 'ok');
  assert.equal(grund(anmeldung({ domain: 'lab.yskar.app' })), 'ok');
});

test('Erlaubte Hosts: nur yskar.app und Subdomains', () => {
  assert.ok(hostErlaubt('yskar.app'));
  assert.ok(hostErlaubt('lab.yskar.app'));
  assert.ok(!hostErlaubt('yskar.app.example.com'));
  assert.ok(!hostErlaubt('xyskar.app'));
  assert.ok(!hostErlaubt('https://lab.yskar.app'));
});

test('Ablehnung: abgelaufen und zu lange gueltig', () => {
  assert.equal(grund(anmeldung(), { jetzt: Date.parse('2026-11-02T11:55:00Z') }), 'abgelaufen');
  assert.equal(grund(anmeldung(), { jetzt: Date.parse('2026-11-02T12:30:00Z') }), 'abgelaufen');
  const spaet = new Date(JETZT + GUELTIG_MAX_MS + 1000).toISOString().slice(0, 19) + 'Z';
  assert.equal(grund(anmeldung({ expires: spaet })), 'zu_lange');
  const genau = new Date(JETZT + GUELTIG_MAX_MS).toISOString().slice(0, 19) + 'Z';
  assert.equal(grund(anmeldung({ expires: genau })), 'ok');
});

test('Ablehnung: fremde Adresse', () => {
  assert.equal(grund(anmeldung({ address: ANDERE.address })), 'adresse_fremd');
  assert.equal(grund(anmeldung({ address: 'ysr1kaputt' })), 'adresse_fremd');
});

test('Ablehnung: fehlende Kopfzeile beim QR-Code', () => {
  assert.equal(grund(anmeldung().split('\n').slice(1).join('\n'), { qr: true }), 'kopf_fehlt');
  assert.equal(grund('Hello', { qr: true }), 'kopf_fehlt');
});

test('Ablehnung: Zeilenenden \\r\\n und Leerzeichen am Zeilenende bei Nachrichten mit Kopfzeile', () => {
  assert.equal(grund(anmeldung().replace(/\n/g, '\r\n')), 'zeilenende');
  assert.equal(grund(anmeldung().replace('purpose: login', 'purpose: login ')), 'leerzeichen');
  assert.equal(grund(VEKTOR.textNachricht + ' '), 'leerzeichen');
});

test('Ablehnung: unbekannter Zweck', () => {
  assert.equal(grund(`${KOPF}\npurpose: transfer\n\nx`), 'zweck_unbekannt');
  assert.equal(grund(anmeldung().replace('purpose: login', 'purpose: Login')), 'zweck_unbekannt');
});

test('Ablehnung: falscher Aufbau der Anmeldung', () => {
  const z = anmeldung().split('\n');
  assert.equal(grund([z[0], z[1], z[3], z[2], z[4], z[5], z[6]].join('\n')), 'aufbau', 'Reihenfolge');
  assert.equal(grund(z.slice(0, 6).join('\n')), 'aufbau', 'Zeile fehlt');
  assert.equal(grund([...z, 'extra: 1'].join('\n')), 'aufbau', 'Zeile zu viel');
  assert.equal(grund(anmeldung({ nonce: 'abc' })), 'aufbau', 'nonce zu kurz');
  assert.equal(grund(anmeldung({ nonce: '9F2C4E1AB37D4C6E8A05B1D2E3F4A5B6' })), 'aufbau', 'nonce gross');
  assert.equal(grund(anmeldung({ expires: '2026-11-02 11:55' })), 'aufbau', 'Zeitformat');
  assert.equal(grund(anmeldung({ expires: '2026-02-31T11:55:00Z' })), 'aufbau', 'kein Kalendertag');
  assert.equal(grund(anmeldung({ issued: '2026-11-02T11:56:00Z' })), 'aufbau', 'issued nach expires');
  assert.equal(grund(`${KOPF}\npurpose: text\nText ohne Leerzeile`), 'aufbau');
});

test('Ablehnung: Hex-Daten und Laenge', () => {
  assert.equal(grund('a'.repeat(64)), 'hex');
  assert.equal(grund('0x' + '0123456789abcdef'.repeat(4)), 'hex');
  assert.equal(grund('0123 4567 89ab cdef 0123 4567 89ab cdef'), 'hex');
  assert.equal(grund(`${KOPF}\npurpose: text\n\n${'ab'.repeat(40)}`), 'hex');
  assert.equal(grund('cafe'), 'ok', 'kurzes Wort aus Hex-Buchstaben ist erlaubt');
  assert.equal(grund('x'.repeat(TEXT_MAX)), 'ok');
  assert.equal(grund('x'.repeat(TEXT_MAX + 1)), 'zu_lang');
  assert.equal(grund('   \n  '), 'leer');
});

test('signieren() lehnt alles ohne Kopfzeile oder mit \\r ab', () => {
  assert.throws(() => signieren('Hello', kp.privateKey, kp.publicKey));
  assert.throws(() => signieren(VEKTOR.textNachricht.replace(/\n/g, '\r\n'), kp.privateKey, kp.publicKey));
  // Rohe Transaktionsbytes als Text sind ohne Kopfzeile -> nie signierbar.
  assert.throws(() => signieren(Buffer.from(fromHex('0100' + '00'.repeat(40))).toString('latin1'), kp.privateKey, kp.publicKey));
});
