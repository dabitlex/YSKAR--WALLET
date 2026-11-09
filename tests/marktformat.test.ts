import test from 'node:test';
import assert from 'node:assert/strict';
import { kurs, kompakt, kompaktZahl, prozent, richtung, alter } from '../src/markt/format.ts';

/*
  Zahlenformate der Marktansicht.

  In Phase 4 ist genau hier ein Fehler passiert: ein von Hand angehaengtes
  Prozentzeichen, richtig fuer Deutsch ("35,3 %") und falsch fuer Englisch
  ("35.3%"). Deshalb laeuft alles ueber Intl, und deshalb steht jede
  Sprache hier im Test.
*/

test('Kurs mit Waehrung, in beiden Sprachen', () => {
  // Deutsch stellt das Zeichen hinten hin, Englisch vorne. Das macht nur Intl richtig.
  assert.match(kurs(66898, 'eur', 'de-DE')!, /^66\.898\s?€$/);
  assert.match(kurs(66898, 'eur', 'en-GB')!, /^€66,898$/);
  // en-GB schreibt US-Dollar als "US$" -- eindeutig gegenueber anderen
  // Dollarwaehrungen. Das ist Absicht von Intl und bleibt so.
  assert.match(kurs(1.23, 'usd', 'en-GB')!, /^US\$1\.23$/);
});

test('Nachkommastellen richten sich nach der Groessenordnung', () => {
  // Eine feste Stellenzahl waere fuer Bitcoin zu grob und fuer eine
  // Kleinstwaehrung sinnlos.
  assert.match(kurs(66898, 'eur', 'de-DE')!, /66\.898/, 'ueber 1000: keine Nachkommastellen');
  assert.match(kurs(12.3456, 'eur', 'de-DE')!, /12,35/, 'ueber 1: zwei Stellen');
  assert.match(kurs(0.1234567, 'eur', 'de-DE')!, /0,1235/, 'unter 1: vier Stellen');
  assert.match(kurs(0.00012345, 'eur', 'de-DE')!, /0,000123/, 'sehr klein: sechs Stellen');
  assert.match(kurs(0.000000123, 'eur', 'de-DE')!, /0,00000012/, 'winzig: acht Stellen');
});

test('ohne Wert kein Text -- keine 0 als Ersatz', () => {
  assert.equal(kurs(null, 'eur', 'de-DE'), null);
  assert.equal(kurs(NaN, 'eur', 'de-DE'), null);
  assert.equal(kurs(Infinity, 'eur', 'de-DE'), null);
  assert.equal(kompakt(null, 'eur', 'de-DE'), null);
  assert.equal(prozent(null, 'de-DE'), null);
  assert.equal(kompaktZahl(null, 'de-DE'), null);
});

test('grosse Betraege werden je Sprache richtig abgekuerzt', () => {
  const de = kompakt(2_205_429_989_769, 'eur', 'de-DE')!;
  const en = kompakt(2_205_429_989_769, 'eur', 'en-GB')!;
  assert.match(de, /Bio/, 'Deutsch kuerzt Billionen mit "Bio."');
  assert.match(en, /tn/, 'Englisch mit "tn" fuer trillion');
  assert.notEqual(de, en, 'die beiden Sprachen kuerzen verschieden');
});

test('Veraenderung traegt immer ein Vorzeichen', () => {
  // Das Vorzeichen ist die zweite Information neben der Farbe.
  assert.match(prozent(1.49, 'de-DE')!, /^\+1,49/);
  assert.match(prozent(-2.12, 'de-DE')!, /^-2,12/);
  assert.match(prozent(1.49, 'en-GB')!, /^\+1\.49/);
  assert.ok(!prozent(0, 'de-DE')!.startsWith('+'), 'bei null kein Pluszeichen');
});

test('Prozentzeichen steht je Sprache richtig', () => {
  // Der Fehler aus Phase 4, hier festgehalten.
  assert.match(prozent(35.3, 'de-DE')!, /\s%$/, 'Deutsch mit Leerzeichen davor');
  assert.match(prozent(35.3, 'en-GB')!, /\d%$/, 'Englisch ohne');
});

test('Richtung einer Veraenderung', () => {
  assert.equal(richtung(1.5), true);
  assert.equal(richtung(-1.5), false);
  assert.equal(richtung(0), null, 'unveraendert ist weder hoch noch runter');
  assert.equal(richtung(null), null);
  assert.equal(richtung(NaN), null);
});

test('Alter eines Standes in Worten', () => {
  const jetzt = 1_800_000_000_000;
  assert.match(alter(jetzt - 30_000, 'de-DE', jetzt), /Sekunden/);
  assert.match(alter(jetzt - 300_000, 'de-DE', jetzt), /Minuten/);
  assert.match(alter(jetzt - 7_200_000, 'de-DE', jetzt), /Stunden/);
  assert.match(alter(jetzt - 172_800_000, 'de-DE', jetzt), /Tagen|vorgestern/);
  assert.match(alter(jetzt - 300_000, 'en-GB', jetzt), /minutes/);
});

test('ein Stand aus der Zukunft wirft nicht', () => {
  const jetzt = 1_800_000_000_000;
  assert.equal(typeof alter(jetzt + 60_000, 'de-DE', jetzt), 'string');
});
