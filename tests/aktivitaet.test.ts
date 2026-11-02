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

/*
  Sammelzahlung (Typ 3, Fassung 6), Antworten wie dabitlex/YSKAR 92b4893
  (ReadApi.ts): Ausgang ohne einzelnen Empfaenger, wartender Eingang nur mit
  der Summe aller Empfaenger -- dieser Betrag darf nicht als eigener gelten.
*/
const sammelWartend = (kind: Wartend['kind'], to: string | null, amount: string): Wartend =>
  ({ txid: 'c'.repeat(64), kind, from: 'ysr1qabsender', to, amount, fee: '9000', nonce: '1', memo: null });

test('Sammelzahlung: Ausgang im Block ohne Gegenpartei heisst mehrere Empfaenger', () => {
  const z = ausEintrag({ ...eintrag('out', '900000000'), counterparty: null });
  assert.equal(z.mehrere, true); assert.equal(z.betrag, 900000000n); assert.equal(z.betragOffen, undefined);
  assert.equal(ausEintrag({ ...eintrag('in', '5'), counterparty: 'ysr1qabsender' }).mehrere, false);
  assert.equal(ausEintrag({ ...eintrag('pool', '5'), counterparty: null }).mehrere, false);
});

test('Sammelzahlung: wartender Eingang zeigt keinen Betrag, wartender Ausgang die Summe', () => {
  const ein = ausWartend(sammelWartend('in', null, '50000000000'));
  assert.equal(ein.betragOffen, true); assert.equal(ein.betrag, 0n); assert.equal(ein.gegen, 'ysr1qabsender'); assert.equal(ein.mehrere, false);
  const aus = ausWartend(sammelWartend('out', null, '700000000'));
  assert.equal(aus.mehrere, true); assert.equal(aus.betragOffen, false); assert.equal(aus.betrag, 700000000n);
  const normal = ausWartend(sammelWartend('in', 'ysr1qich', '42'));
  assert.equal(normal.betragOffen, false); assert.equal(normal.mehrere, false); assert.equal(normal.betrag, 42n);
});
