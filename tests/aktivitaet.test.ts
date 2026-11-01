import test from 'node:test';
import assert from 'node:assert/strict';
import { ausEintrag, ausWartend, istMining, nichtGelistet, tagessumme, bestaetigungen } from '../src/wallet/zahlungen.ts';
import type { VerlaufEintrag, Wartend } from '../src/netz/api.ts';

/*
  Wallet und Aktivitaet (Phase B2): Mining-Filter nur aus vorhandenen Daten
  (reward, pool), Tagessumme, und wann eine wartende Zahlung als "nicht in
  der Liste des Knotens" gilt.
*/

const eintrag = (kind: VerlaufEintrag['kind'], amount: string, height = 10): VerlaufEintrag =>
  ({ txid: kind + amount, height, idx: 0, timestamp: '1', kind, counterparty: kind === 'in' || kind === 'out' ? 'ysr1x' : null, amount, fee: '7', memo: null });
const wartend: Wartend = { txid: 'w', kind: 'out', from: 'ysr1a', to: 'ysr1b', amount: '5', fee: '1', nonce: '3', memo: null };

test('Mining-Filter: nur Blockbelohnung und Pool-Anteil (T-M1)', () => {
  const liste = (['reward', 'pool', 'in', 'out'] as const).map(k => ausEintrag(eintrag(k, '1')));
  assert.deepEqual(liste.filter(istMining).map(z => z.art), ['belohnung', 'pool']);
  assert.equal(istMining(ausWartend(wartend)), false);
});

test('Tagessumme: Eingaenge plus, Ausgaenge minus, ohne Gebuehr', () => {
  const tag = [ausEintrag(eintrag('pool', '412')), ausEintrag(eintrag('in', '500')), ausEintrag(eintrag('out', '200'))];
  assert.equal(tagessumme(tag), 712n);
  assert.equal(tagessumme([ausEintrag(eintrag('out', '9'))]), -9n);
});

test('Nicht in der Liste des Knotens: nur bei bekannter Antwort und nach zwei Minuten (T-A2)', () => {
  const jetzt = 1_000_000;
  const z = (mempool: boolean | null, zeit: number | null) => ({ ...ausWartend(wartend, zeit), mempool });
  assert.equal(nichtGelistet(z(false, jetzt - 300), jetzt), true);
  assert.equal(nichtGelistet(z(false, jetzt - 60), jetzt), false, 'frisch gesendet: Liste kann hinterherhinken');
  assert.equal(nichtGelistet(z(null, jetzt - 300), jetzt), false, 'Liste nicht abrufbar: kein Urteil');
  assert.equal(nichtGelistet(z(true, jetzt - 300), jetzt), false);
  assert.equal(nichtGelistet(z(false, null), jetzt), false, 'ohne Zeit kein Urteil');
  assert.equal(nichtGelistet({ ...ausEintrag(eintrag('out', '1')), mempool: false }, jetzt), false, 'im Block nie');
});

test('Bestaetigungen: der Block zaehlt als erste, wartend null', () => {
  assert.equal(bestaetigungen(ausEintrag(eintrag('in', '1', 100)), 103), 4);
  assert.equal(bestaetigungen(ausEintrag(eintrag('in', '1', 100)), null), 1);
  assert.equal(bestaetigungen(ausWartend(wartend), 103), 0);
});
