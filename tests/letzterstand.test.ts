import test from 'node:test';
import assert from 'node:assert/strict';
import { saeubern, standLesen, standMerken, letzterStandLager, VERLAUF_MAX } from '../src/wallet/letzterstand.ts';
import type { Konto, VerlaufEintrag } from '../src/netz/api.ts';

/*
  Letzter Stand: nur zur Anzeige nach einem Start ohne Netz. Nur zur eigenen
  Adresse, Verlauf gekuerzt, Unbrauchbares wird verworfen. Fiktive Daten.
*/
const eintrag = (i: number): VerlaufEintrag =>
  ({ txid: 't' + i, height: 100 - i, idx: 0, timestamp: '1', kind: 'in', counterparty: 'ysr1qabsender', amount: '5', fee: '1', memo: null });
const konto = (n = 3): Konto => ({ address: 'ysr1qich', balance: '1000', nonce: '2', nextNonce: '2', pendingSource: 'fullnode',
  pending: [{ txid: 'w', kind: 'out', from: 'ysr1qich', to: 'ysr1qdu', amount: '5', fee: '1', nonce: '2', memo: null }],
  history: Array.from({ length: n }, (_, i) => eintrag(i)), historyWeiter: null });

test('Letzter Stand: gemerkt mit gekuerztem Verlauf, nur fuer die eigene Adresse', () => {
  standMerken(konto(50), 1700000000);
  const s = standLesen('ysr1qich');
  assert.ok(s); assert.equal(s.zeit, 1700000000); assert.equal(s.konto.balance, '1000');
  assert.equal(s.konto.history.length, VERLAUF_MAX); assert.equal(s.konto.pending.length, 1);
  assert.equal(standLesen('ysr1qandere'), null);
  letzterStandLager.leeren(); assert.equal(standLesen('ysr1qich'), null);
});

test('Letzter Stand: Unbrauchbares wird verworfen', () => {
  const gut = { adresse: 'ysr1qich', zeit: 1, konto: konto() };
  assert.ok(saeubern(gut));
  assert.equal(saeubern(null), null);
  assert.equal(saeubern({ ...gut, adresse: 'ysr1qandere' }), null);
  assert.equal(saeubern({ ...gut, konto: { ...konto(), balance: '-5' } }), null);
  assert.equal(saeubern({ ...gut, konto: { ...konto(), history: [{ txid: 'x' }] } }), null);
  assert.equal(saeubern({ ...gut, zeit: 'gestern' }), null);
  assert.equal(saeubern({ ...gut, konto: { ...konto(), balance: -5 } }), null);
  assert.equal(saeubern({ ...gut, konto: { ...konto(), balance: 1.5 } }), null);
});

test('Letzter Stand: Guthaben und Nonce als JSON-Zahl (bigint-Spalten des Spiegels)', () => {
  // So liefert /api/v2/account echte Konten: balance und nonce als Zahl, Betraege als Text.
  const echt = JSON.parse(JSON.stringify({ ...konto(), balance: 128450000000, nonce: 4, nextNonce: '5' }));
  standMerken(echt, 1700000000);
  // Neustart nachstellen: der Stand kommt aus localStorage, nicht aus dem Arbeitsspeicher.
  const roh = JSON.parse(JSON.stringify(letzterStandLager.lesen()));
  const s = saeubern(roh);
  assert.ok(s, 'echter Stand darf nicht verworfen werden');
  assert.equal(s.konto.balance, '128450000000'); assert.equal(s.konto.nonce, '4'); assert.equal(s.konto.nextNonce, '5');
  const ohneNaechste = saeubern({ ...roh, konto: { ...roh.konto, nextNonce: undefined } });
  assert.equal(ohneNaechste?.konto.nextNonce, '4');
  const zahlen = saeubern({ ...roh, konto: { ...roh.konto, pending: [{ ...roh.konto.pending[0], amount: 5, fee: 1, nonce: 2 }] } });
  assert.equal(zahlen?.konto.pending[0].amount, '5');
  letzterStandLager.leeren();
});
