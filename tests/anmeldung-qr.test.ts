/*
  Anmelden per QR: Code lesen, Regeln fuer den Rueckruf, Antwort der Plattform
  und ein ganzer Durchlauf gegen einen kleinen lokalen Test-Server.

  Den Test-Server gibt es, weil die Plattform (YSKAR Lab) noch nicht steht.
  Er prueft die Signatur so, wie die Plattform es tun wird, und nimmt jeden
  Nonce nur einmal an.
*/
import test from 'node:test';
import assert from 'node:assert/strict';
import { createServer } from 'node:http';
import type { AddressInfo } from 'node:net';
import { keypairFromMnemonic } from '../src/kern/wallet.ts';
import {
  KOPF, anmeldungAusCode, istAnmeldeCode, textBase64url, base64urlText, signieren, pruefen, rueckrufErlaubt,
  type Signiert,
} from '../src/wallet/nachricht.ts';
import { posten, antwortLesen, rueckrufSenden } from '../src/netz/rueckruf.ts';

const kp = keypairFromMnemonic('abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about');
const ANDERE = keypairFromMnemonic('zoo zoo zoo zoo zoo zoo zoo zoo zoo zoo zoo wrong');
const JETZT = Date.parse('2026-11-02T11:51:00Z');
const CB = 'https://lab.yskar.app/api/auth/wallet';

function nachricht(f: { domain?: string; address?: string; nonce?: string; expires?: string } = {}): string {
  return [KOPF, 'purpose: login', `domain: ${f.domain ?? 'lab.yskar.app'}`, `address: ${f.address ?? kp.address}`,
    `nonce: ${f.nonce ?? '9f2c4e1ab37d4c6e8a05b1d2e3f4a5b6'}`, 'issued: 2026-11-02T11:50:00Z', `expires: ${f.expires ?? '2026-11-02T11:55:00Z'}`].join('\n');
}
const code = (msg: string, cb = CB) => `yskar:login?msg=${textBase64url(msg)}&cb=${textBase64url(cb)}`;
const grund = (c: string, jetzt = JETZT) => { const p = anmeldungAusCode(c, kp.address, jetzt); return p.ok ? 'ok' : p.grund; };

/* ------------------------------------------------------------- Code lesen */

test('base64url hin und zurueck, auch mit Umlauten; Unsinn wird abgelehnt', () => {
  for (const s of ['', 'a', 'Grüße ✓', nachricht()]) assert.equal(base64urlText(textBase64url(s)), s);
  assert.equal(base64urlText('ab+/'), null, 'kein base64url');
  assert.equal(base64urlText('a'), null, 'falsche Laenge');
  assert.equal(base64urlText(textBase64url('x').replace(/./, '_') + '__'), null, 'kein UTF-8');
});

test('Gueltiger Code: Nachricht, Anmeldung und Rueckruf', () => {
  assert.ok(istAnmeldeCode(code(nachricht())));
  assert.ok(istAnmeldeCode('YSKAR://login?msg=a&cb=b'));
  assert.ok(!istAnmeldeCode(`yskar:${kp.address}?amount=1`));
  const p = anmeldungAusCode(code(nachricht()), kp.address, JETZT);
  assert.ok(p.ok);
  assert.equal(p.nachricht, nachricht());
  assert.equal(p.rueckruf, CB);
  assert.equal(p.anmeldung.domain, 'lab.yskar.app');
});

/* ------------------------------------------------------- Ablehnungsregeln */

test('Ablehnung: Rueckruf ueber http, mit Port oder Benutzername', () => {
  assert.equal(grund(code(nachricht(), 'http://lab.yskar.app/api/auth/wallet')), 'rueckruf_https');
  assert.equal(grund(code(nachricht(), 'https://lab.yskar.app:8443/api')), 'rueckruf_https');
  assert.equal(grund(code(nachricht(), 'https://user:pw@lab.yskar.app/api')), 'rueckruf_https');
  assert.equal(grund(code(nachricht(), 'kein link')), 'rueckruf_https');
});

test('Ablehnung: Host des Rueckrufs passt nicht genau zur Domain', () => {
  assert.equal(grund(code(nachricht(), 'https://evil.com/api')), 'rueckruf_host');
  assert.equal(grund(code(nachricht(), 'https://yskar.app/api')), 'rueckruf_host', 'andere Subdomain zaehlt nicht');
  assert.equal(grund(code(nachricht(), 'https://lab.yskar.app.evil.com/api')), 'rueckruf_host');
  const p = anmeldungAusCode(code(nachricht(), 'https://evil.com/x'), kp.address, JETZT);
  assert.ok(!p.ok && p.domain === 'lab.yskar.app' && p.rueckrufHost === 'evil.com');
  assert.equal(rueckrufErlaubt('https://lab.yskar.app/a?b=1', 'lab.yskar.app'), null);
});

test('Ablehnung: falsche Domain in der Nachricht, auch wenn der Rueckruf dazu passt', () => {
  assert.equal(grund(code(nachricht({ domain: 'evil.com' }), 'https://evil.com/api')), 'domain');
});

test('Ablehnung: abgelaufen, laenger als 15 Minuten gueltig, fremde Adresse', () => {
  assert.equal(grund(code(nachricht()), Date.parse('2026-11-02T11:56:00Z')), 'abgelaufen');
  assert.equal(grund(code(nachricht({ expires: '2026-11-02T12:30:00Z' }))), 'zu_lange');
  assert.equal(grund(code(nachricht({ address: ANDERE.address }))), 'adresse_fremd');
});

test('Ablehnung: fehlende Kopfzeile, Zeilenenden \\r\\n, freier Text statt Anmeldung', () => {
  assert.equal(grund(code(nachricht().split('\n').slice(1).join('\n'))), 'kopf_fehlt');
  assert.equal(grund(code(nachricht().replace(/\n/g, '\r\n'))), 'zeilenende');
  assert.equal(grund(code(`${KOPF}\npurpose: text\n\nhallo`)), 'kein_login');
});

test('Ablehnung: kaputter oder doppelter Code', () => {
  assert.equal(grund('yskar:login?msg=%%%&cb=x'), 'qr_format');
  assert.equal(grund(`yskar:login?msg=${textBase64url(nachricht())}`), 'qr_format', 'cb fehlt');
  assert.equal(grund(code(nachricht()) + `&cb=${textBase64url('https://evil.com')}`), 'qr_format', 'cb doppelt');
  assert.equal(grund(`yskar:${kp.address}`), 'qr_format');
});

/* -------------------------------------------------------- Antwort lesen */

test('Antwort der Plattform: nur 2xx mit ok:true ist Erfolg', () => {
  assert.deepEqual(antwortLesen(200, { ok: true }), { ok: true });
  assert.equal(antwortLesen(200, { ok: false }).ok, false);
  assert.equal(antwortLesen(200, 'ok').ok, false);
  const f = antwortLesen(400, { ok: false, error: 'This sign-in code was already used.' });
  assert.ok(!f.ok && f.art === 'abgelehnt' && f.text === 'This sign-in code was already used.');
  const lang = antwortLesen(400, { error: 'x'.repeat(500) + '\u0007' });
  assert.ok(!lang.ok && Array.from(lang.text!).length === 200, 'auf 200 Zeichen gekuerzt');
  const steuer = antwortLesen(403, { error: '<b>\u0000böse</b>\n' });
  assert.ok(!steuer.ok && steuer.text === '<b> böse</b>', 'Steuerzeichen raus; angezeigt wird reiner Text');
  const w = antwortLesen(302, null);
  assert.ok(!w.ok && w.art === 'weiterleitung');
});

test('rueckrufSenden geht nie an http oder fremde Hosts', async () => {
  const s = signieren(nachricht(), kp.privateKey, kp.publicKey);
  for (const url of ['http://lab.yskar.app/x', 'https://evil.com/x', 'https://lab.yskar.app:444/x']) {
    const r = await rueckrufSenden(url, s);
    assert.ok(!r.ok && r.art === 'abgelehnt' && r.status === 0, url);
  }
});

/* --------------------------------------------- Durchlauf mit Test-Server */

function testServer() {
  const verbraucht = new Set<string>();
  const erhalten: Signiert[] = [];
  const server = createServer((req, res) => {
    let roh = '';
    req.on('data', c => { roh += c; });
    req.on('end', () => {
      const antworte = (status: number, body: unknown) => { res.writeHead(status, { 'content-type': 'application/json' }); res.end(JSON.stringify(body)); };
      if (req.url === '/umleiten') { res.writeHead(302, { location: 'https://evil.com/' }); res.end(); return; }
      let s: Signiert;
      try { s = JSON.parse(roh); } catch { antworte(400, { ok: false, error: 'Invalid JSON' }); return; }
      erhalten.push(s);
      // Wie die Plattform: Signatur, Adresse zum Schluessel, Domain, Nonce nur einmal.
      if (pruefen(s) !== 'gueltig') { antworte(401, { ok: false, error: 'Invalid signature' }); return; }
      const nonce = /\nnonce: ([0-9a-f]{32})\n/.exec(s.message)?.[1];
      if (!nonce || !s.message.includes('\ndomain: lab.yskar.app\n')) { antworte(400, { ok: false, error: 'Wrong message' }); return; }
      if (verbraucht.has(nonce)) { antworte(409, { ok: false, error: 'This sign-in code was already used.' }); return; }
      verbraucht.add(nonce);
      antworte(200, { ok: true });
    });
  });
  return new Promise<{ url: string; erhalten: Signiert[]; zu: () => void }>(fertig => {
    server.listen(0, '127.0.0.1', () => {
      const port = (server.address() as AddressInfo).port;
      fertig({ url: `http://127.0.0.1:${port}`, erhalten, zu: () => server.close() });
    });
  });
}

test('Ganzer Durchlauf: QR lesen, signieren, an den Test-Server senden, Nonce nur einmal', async () => {
  const srv = await testServer();
  try {
    const p = anmeldungAusCode(code(nachricht()), kp.address, JETZT);
    assert.ok(p.ok);
    const s = signieren(p.nachricht, kp.privateKey, kp.publicKey);
    // posten() direkt, weil der Test-Server kein https unter yskar.app hat.
    assert.deepEqual(await posten(srv.url + '/api/auth/wallet', s), { ok: true });
    assert.deepEqual(Object.keys(srv.erhalten[0]), ['address', 'publicKey', 'signature', 'message']);
    assert.equal(srv.erhalten[0].message, nachricht(), 'die Nachricht kommt Byte fuer Byte an');

    const zweimal = await posten(srv.url + '/api/auth/wallet', s);
    assert.ok(!zweimal.ok && zweimal.art === 'abgelehnt' && zweimal.text === 'This sign-in code was already used.');

    const falsch = await posten(srv.url + '/api/auth/wallet', { ...s, signature: s.signature.replace(/^./, c => (c === '0' ? '1' : '0')) });
    assert.ok(!falsch.ok && falsch.text === 'Invalid signature');

    const um = await posten(srv.url + '/umleiten', s);
    assert.ok(!um.ok && um.art === 'weiterleitung', 'Weiterleitung wird nicht verfolgt');
  } finally { srv.zu(); }
});

test('Server nicht erreichbar: Netzfehler statt Absturz', async () => {
  const s = signieren(nachricht(), kp.privateKey, kp.publicKey);
  const r = await posten('http://127.0.0.1:9/', s);
  assert.ok(!r.ok && r.art === 'netz');
});
