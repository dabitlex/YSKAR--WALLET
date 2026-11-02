import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { BAND_LINKS, BAND_RECHTS, STAMM, LOGO_ANSICHT, baenderFuerPin } from '../src/ui/zeichenform.ts';

/* Startbildschirm und Sperre zeichnen das Logo aus drei Baendern: Es muessen exakt die Originalpfade sein. */
test('Zeichen: exakt die Pfade und der Ausschnitt aus zeichen.svg', () => {
  const svg = readFileSync(new URL('../src/bilder/zeichen.svg', import.meta.url), 'utf8');
  const d = svg.match(/<path d="([^"]+)"/)![1];
  assert.equal([STAMM, BAND_LINKS, BAND_RECHTS].join(' '), d);
  assert.ok(svg.includes(`viewBox="${LOGO_ANSICHT}"`));
});

test('Zeichen: leuchtet mit der PIN-Eingabe Band fuer Band auf (nur Anzeige)', () => {
  assert.deepEqual(baenderFuerPin(0, 'frei'), ['aus', 'aus', 'aus']);
  assert.deepEqual(baenderFuerPin(1, 'frei'), ['halb', 'aus', 'aus']);
  assert.deepEqual(baenderFuerPin(2, 'frei'), ['an', 'aus', 'aus']);
  assert.deepEqual(baenderFuerPin(3, 'frei'), ['an', 'halb', 'aus']);
  assert.deepEqual(baenderFuerPin(4, 'frei'), ['an', 'an', 'aus']);
  assert.deepEqual(baenderFuerPin(5, 'frei'), ['an', 'an', 'halb']);
  assert.deepEqual(baenderFuerPin(6, 'prueft'), ['an', 'an', 'an']);
  assert.deepEqual(baenderFuerPin(6, 'falsch'), ['rot', 'rot', 'rot']);
  assert.deepEqual(baenderFuerPin(0, 'gut'), ['an', 'an', 'an']);
});
