import test from 'node:test';
import assert from 'node:assert/strict';
import { LOESCHEN, ADRESS_ZEICHEN, tippeBetrag, tippeAdresse, tippeWort, tippeText, grossAmAnfang, wortVorschlaege, istWort, woerterAusText } from '../src/wallet/tasten.ts';
import { eingabeEinheiten } from '../src/wallet/betrag.ts';
import { isValidAddress } from '../src/kern/address.ts';

const tippe = (f: (a: string, t: string) => string, tasten: string[], start = '') => tasten.reduce(f, start);

test('Betrag ueber die eigene Tastatur', () => {
  const b = (a: string, t: string) => tippeBetrag(a, t, ',');
  assert.equal(tippe(b, [...'25']), '25');
  assert.equal(tippe(b, [...'25,5']), '25,5');
  assert.equal(tippe(b, [',', '5']), '0,5');
  assert.equal(tippe(b, [...'0', '0', '7']), '7');
  assert.equal(tippe(b, [...'1,,2']), '1,2');
  assert.equal(tippe(b, [...'1,123456789']), '1,12345678');
  assert.equal(tippe(b, [...'1234567890123']), '123456789012');
  assert.equal(tippe(b, [...'12,5', LOESCHEN, LOESCHEN]), '12');
  assert.equal(tippe(b, [LOESCHEN]), '');
  assert.equal(eingabeEinheiten(tippe(b, [...'25,5'])), 2550000000n);
  assert.equal(tippeBetrag('3', '.', '.'), '3.');
});

test('Adresse ueber die eigene Tastatur', () => {
  const ziel = 'ysr1lfn8dw6st25jxv653vypcp929xs9lzg44954zz';
  assert.equal(ADRESS_ZEICHEN.length, 32);
  assert.equal(tippeAdresse('', 'l'), 'ysr1l');
  const getippt = tippe(tippeAdresse, [...ziel.slice(4)]);
  assert.equal(getippt, ziel);
  assert.ok(isValidAddress(getippt));
  assert.equal(tippeAdresse(ziel, 'q'), ziel, 'nicht laenger als eine Adresse');
  assert.equal(tippeAdresse('ysr1l', LOESCHEN), '', 'ohne Zeichen bleibt auch der Anfang nicht stehen');
  assert.equal(tippeAdresse('ysr1lf', LOESCHEN), 'ysr1l');
  for (const z of ['1', 'b', 'i', 'o', 'A', ' ']) assert.equal(tippeAdresse('ysr1l', z), 'ysr1l');
});

test('Woerter: Vorschlaege aus der Liste', () => {
  assert.deepEqual(wortVorschlaege('aban'), ['abandon']);
  assert.equal(wortVorschlaege('act')[0], 'act', 'das genaue Wort steht vorn');
  assert.ok(wortVorschlaege('act').includes('action'));
  assert.deepEqual(wortVorschlaege(''), []);
  assert.deepEqual(wortVorschlaege('zzz'), []);
  assert.ok(wortVorschlaege('a').length === 4);
  assert.ok(istWort('zoo') && !istWort('zooo'));
  assert.equal(tippe(tippeWort, [...'abandon']), 'abandon');
  assert.equal(tippe(tippeWort, [...'abcdefghij']), 'abcdefgh');
  assert.equal(tippeWort('ab', LOESCHEN), 'a');
});

test('eingefuegte Woerter werden verteilt', () => {
  const w = woerterAusText('  Legal winner\nthank, year 5. wave ', 12);
  assert.equal(w.length, 12);
  assert.deepEqual(w.slice(0, 6), ['legal', 'winner', 'thank', 'year', 'wave', '']);
  assert.equal(woerterAusText('a b c d', 3).join(' '), 'a b c');
});

test('freier Text ueber die eigene Tastatur', () => {
  const x = (a: string, t: string) => tippeText(a, t, 8);
  assert.equal(tippe(x, [...'Hallo']), 'Hallo');
  assert.equal(tippe(x, [' ', 'a', ' ', ' ', 'b']), 'a b', 'kein Leerzeichen vorn, keine zwei hintereinander');
  assert.equal(tippe(x, [...'123456789']), '12345678', 'nicht laenger als erlaubt');
  assert.equal(tippeText('Grüße', LOESCHEN), 'Grüß');
  assert.equal(tippeText('a😀', LOESCHEN), 'a', 'ein Zeichen aus zwei Haelften geht als Ganzes');
  assert.equal(tippeText('', LOESCHEN), '');
  assert.ok(grossAmAnfang('', 'anfang') && !grossAmAnfang('a', 'anfang') && !grossAmAnfang('a ', 'anfang'));
  assert.ok(grossAmAnfang('', 'woerter') && grossAmAnfang('Anna ', 'woerter') && !grossAmAnfang('Anna', 'woerter'));
  assert.ok(!grossAmAnfang('', undefined));
});
