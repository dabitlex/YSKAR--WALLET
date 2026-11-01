import test from 'node:test';
import assert from 'node:assert/strict';
import { netzbild, imNetzGesehen, schritte, yStand } from '../src/wallet/sendestatus.ts';
import { gebuehrFuer, ueberweisungBytes, sendeStand } from '../src/wallet/fassung.ts';
import type { Konto, Markt, Stufe } from '../src/netz/api.ts';

/*
  Senden (Phase B1): Die fuenf Schritte nach dem Einreichen gelten nur, wenn
  eine Antwort sie belegt. Eine angenommene Zahlung ist nicht bestaetigt, und
  "endgueltig" gibt es nicht.
*/

const TX = 'ab'.repeat(32);
const konto = (teil: Partial<Konto>): Konto => ({
  address: 'ysr1x', balance: '0', nonce: '0', nextNonce: '0',
  pending: [], pendingSource: 'fullnode', history: [], historyWeiter: null, ...teil,
});
const wartend = { txid: TX, kind: 'out' as const, from: 'ysr1x', to: 'ysr1y', amount: '1', fee: '1', nonce: '0', memo: null };
const eintrag = (height: number) => ({ txid: TX, height, idx: 1, timestamp: '1', kind: 'out' as const, counterparty: 'ysr1y', amount: '1', fee: '1', memo: null });

test('Nur angenommen: Mempool wartet, nichts weiter ist belegt', () => {
  const b = netzbild(konto({}), 100, TX);
  assert.deepEqual(b, { imMempool: false, hoehe: null, bestaetigungen: 0 });
  const s = schritte(b, false);
  assert.deepEqual(s, { signiert: 'erledigt', angenommen: 'erledigt', mempool: 'wartet', block: 'offen', weitere: 'offen' });
  assert.deepEqual(yStand(s), ['erledigt', 'wartet', 'offen']);
  assert.equal(imNetzGesehen(b), false);
});

test('In der Liste des Knotens: Mempool erledigt, Block wartet', () => {
  const b = netzbild(konto({ pending: [wartend] }), 100, TX);
  assert.equal(b.imMempool, true);
  assert.equal(imNetzGesehen(b), true);
  const s = schritte(b, true);
  assert.equal(s.mempool, 'erledigt');
  assert.equal(s.block, 'wartet');
  assert.equal(s.weitere, 'offen');
});

test('Mempool nicht abrufbar: kein Urteil, die Zahlung bleibt sichtbar (T-A2)', () => {
  const b = netzbild(konto({ pending: [wartend], pendingSource: 'unavailable' }), 100, TX);
  assert.equal(b.imMempool, null);
  assert.equal(schritte(b, false).mempool, 'wartet');
  // Schon einmal gesehen: bleibt erledigt, auch wenn die Liste gerade fehlt.
  assert.equal(schritte(b, true).mempool, 'erledigt');
  assert.equal(netzbild(null, 100, TX).imMempool, null);
});

test('Gesehen, dann weg, aber in keinem Block: wieder wartend', () => {
  const s = schritte(netzbild(konto({}), 100, TX), true);
  assert.equal(s.mempool, 'wartet');
  assert.equal(s.block, 'offen');
});

test('Im Block: eine Bestaetigung, Stamm des Y wartet', () => {
  const b = netzbild(konto({ history: [eintrag(100)] }), 100, TX);
  assert.deepEqual(b, { imMempool: false, hoehe: 100, bestaetigungen: 1 });
  const s = schritte(b, false);
  assert.equal(s.mempool, 'erledigt');
  assert.equal(s.block, 'erledigt');
  assert.equal(s.weitere, 'wartet');
  assert.deepEqual(yStand(s), ['erledigt', 'erledigt', 'wartet']);
});

test('Ab zwei Bestaetigungen ist Schritt 5 erledigt, Hoehe unbekannt zaehlt als eine', () => {
  const zwei = netzbild(konto({ history: [eintrag(100)] }), 101, TX);
  assert.equal(zwei.bestaetigungen, 2);
  assert.deepEqual(yStand(schritte(zwei, false)), ['erledigt', 'erledigt', 'erledigt']);
  assert.equal(netzbild(konto({ history: [eintrag(100)] }), null, TX).bestaetigungen, 1);
  // Spiegel noch hinter dem Block: nie weniger als eine.
  assert.equal(netzbild(konto({ history: [eintrag(100)] }), 99, TX).bestaetigungen, 1);
});

test('Gebuehrenstufen: jede liegt ueber der Mindestgebuehr je Byte (T-S1)', () => {
  const m: Markt = {
    minFee: '1000', mindestJeByte: '10', wartend: 0, andrang: false, basisBytes: 174, fassung: 1, hoehe: 7358, sendepause: false,
    stufen: { langsam: { fee: '100', block: 3, jeByte: null }, normal: { fee: '1740', block: 1, jeByte: '10' }, schnell: { fee: '0', block: 1, jeByte: '12' } },
  };
  const stand = sendeStand(m, 7357);
  for (const notiz of [0, 40]) {
    const bytes = ueberweisungBytes(stand, m, notiz);
    for (const s of ['langsam', 'normal', 'schnell'] as Stufe[]) {
      assert.ok(gebuehrFuer(m, s, bytes) >= 10n * BigInt(bytes), `${s} mit ${notiz} Byte Notiz`);
    }
    assert.ok(gebuehrFuer(m, 'schnell', bytes) >= gebuehrFuer(m, 'normal', bytes));
  }
});
