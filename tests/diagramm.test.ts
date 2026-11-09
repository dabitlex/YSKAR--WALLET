import test from 'node:test';
import assert from 'node:assert/strict';
import {
  skala, linienPfad, flaechenPfad, punktBeiX, veraenderung, duennen, ausReihe, type Punkt,
} from '../src/diagramm/pfad.ts';

/*
  Die Rechnung hinter den Charts.

  Ein Chart, der die falsche Linie zeichnet, ist in einer Finanz-App ein
  Fehler wie ein falscher Betrag -- nur schwerer zu bemerken. Deshalb ist
  die Mathematik von der Darstellung getrennt und hier vollstaendig
  geprueft.
*/

const R = { breite: 300, hoehe: 100, polster: 6 };
const reihe = (werte: number[]): Punkt[] => werte.map((w, i) => [i * 1000, w] as Punkt);

test('die Skala spannt ueber die ganze Breite', () => {
  const p = reihe([10, 20, 30]);
  const s = skala(p, R)!;
  assert.equal(s.x(0), 0, 'der erste Punkt ganz links');
  assert.equal(s.x(2000), 300, 'der letzte ganz rechts');
  assert.equal(s.x(1000), 150, 'der mittlere in der Mitte');
});

test('hohe Werte liegen oben -- die Y-Achse ist umgedreht', () => {
  const s = skala(reihe([10, 20]), R)!;
  assert.ok(s.y(20) < s.y(10), 'der groessere Wert hat die kleinere Bildkoordinate');
  assert.equal(s.y(20), R.polster, 'das Hoch sitzt am oberen Polster');
  assert.equal(s.y(10), R.hoehe - R.polster, 'das Tief am unteren');
});

test('die Y-Achse beginnt nicht bei null', () => {
  // Bei einem Kurs zwischen 66.000 und 67.000 waere eine Achse ab null
  // eine gerade Linie -- und verschwiege genau die Bewegung, um die es geht.
  const s = skala(reihe([66000, 66500, 67000]), R)!;
  assert.equal(s.minWert, 66000);
  assert.equal(s.maxWert, 67000);
  assert.ok(s.y(66000) - s.y(67000) > 50, 'die Spanne nutzt die Hoehe wirklich aus');
});

test('lauter gleiche Werte ergeben eine Linie in der Mitte', () => {
  const s = skala(reihe([5, 5, 5]), R)!;
  assert.equal(s.y(5), 50, 'keine Division durch null, keine verschwundene Linie');
});

test('ein einzelner Punkt steht in der Mitte', () => {
  const s = skala([[1000, 42]], R)!;
  assert.equal(s.x(1000), 150);
  assert.equal(linienPfad([[1000, 42]], s), 'M 150.00 50.00');
});

test('ohne Punkte gibt es keine Skala', () => {
  assert.equal(skala([], R), null);
  assert.equal(linienPfad([], skala(reihe([1, 2]), R)!), '');
});

test('der Linienpfad hat genau einen Punkt je Messwert', () => {
  const p = reihe([10, 20, 15, 30]);
  const d = linienPfad(p, skala(p, R)!);
  assert.equal((d.match(/[ML]/g) ?? []).length, 4);
  assert.ok(d.startsWith('M '), 'beginnt mit einem Absetzen');
  assert.equal((d.match(/M/g) ?? []).length, 1, 'und setzt nur einmal ab');
});

test('die Linie wird nicht geglaettet', () => {
  // Eine Glaettung erfindet zwischen zwei Messpunkten Werte, die es nie
  // gab, und kann ueber ein Hoch hinwegschwingen. Bei Kursen ist das falsch.
  const p = reihe([10, 20, 15]);
  const d = linienPfad(p, skala(p, R)!);
  assert.ok(!/[CQS]/.test(d), 'keine Kurvenbefehle im Pfad');
});

test('die Flaeche ist unten geschlossen', () => {
  const p = reihe([10, 20, 15]);
  const s = skala(p, R)!;
  const f = flaechenPfad(p, s, R.hoehe);
  assert.ok(f.endsWith('Z'), 'geschlossen');
  assert.ok(f.includes('L 300.00 100.00'), 'rechts bis zur Grundlinie');
  assert.ok(f.includes('L 0.00 100.00'), 'und links zurueck');
});

test('das Fadenkreuz findet den naechsten Punkt', () => {
  const p = reihe([10, 20, 30, 40, 50]);
  const s = skala(p, R)!;
  assert.equal(punktBeiX(p, s, 0), 0, 'ganz links');
  assert.equal(punktBeiX(p, s, 300), 4, 'ganz rechts');
  assert.equal(punktBeiX(p, s, 150), 2, 'in der Mitte');
  assert.equal(punktBeiX(p, s, -50), 0, 'links daneben bleibt beim ersten');
  assert.equal(punktBeiX(p, s, 999), 4, 'rechts daneben beim letzten');
});

test('das Fadenkreuz rundet zum wirklich naechsten Punkt', () => {
  const p = reihe([10, 20, 30]);
  const s = skala(p, R)!;
  // Punkte liegen bei x = 0, 150, 300.
  assert.equal(punktBeiX(p, s, 70), 0, '70 ist naeher an 0 als an 150');
  assert.equal(punktBeiX(p, s, 80), 1, '80 ist naeher an 150');
});

test('das Fadenkreuz bleibt auch bei vielen Punkten schnell', () => {
  const viele: Punkt[] = [];
  for (let i = 0; i < 721; i++) viele.push([i * 3_600_000, 60000 + Math.sin(i / 10) * 500]);
  const s = skala(viele, R)!;
  const start = process.hrtime.bigint();
  for (let x = 0; x < 300; x++) punktBeiX(viele, s, x);
  const ms = Number(process.hrtime.bigint() - start) / 1e6;
  assert.ok(ms < 50, `300 Suchen in 721 Punkten brauchten ${ms.toFixed(1)} ms`);
});

test('Veraenderung in Prozent', () => {
  assert.equal(veraenderung(reihe([100, 110])), 10);
  assert.equal(veraenderung(reihe([100, 90])), -10);
  assert.equal(veraenderung(reihe([100, 120, 50])), -50, 'gerechnet wird erster zu letztem');
  assert.equal(veraenderung(reihe([42])), null, 'ein Punkt ergibt keine Veraenderung');
  assert.equal(veraenderung([]), null);
  assert.equal(veraenderung(reihe([0, 50])), null, 'keine Prozent gegenueber null');
});

test('Duennen behaelt immer den letzten Punkt', () => {
  // Sonst zeigte der Chart am rechten Rand einen Kurs von vorhin.
  const p = reihe([1, 2, 3, 4, 5, 6, 7, 8, 9, 10]);
  const d = duennen(p, 4);
  assert.equal(d.length, 4);
  assert.deepEqual(d[0], p[0], 'der erste bleibt');
  assert.deepEqual(d[d.length - 1], p[p.length - 1], 'und der letzte auch');
});

test('Duennen laesst kurze Reihen in Ruhe', () => {
  const p = reihe([1, 2, 3]);
  assert.equal(duennen(p, 10), p);
  assert.equal(duennen(p, 3), p);
});

test('Duennen behaelt die Reihenfolge', () => {
  const p = reihe([1, 2, 3, 4, 5, 6, 7, 8, 9, 10]);
  const d = duennen(p, 5);
  for (let i = 1; i < d.length; i++) assert.ok(d[i][0] > d[i - 1][0], 'Zeit steigt');
});

test('eine Zahlenreihe wird zu Punkten mit gleichem Abstand', () => {
  assert.deepEqual(ausReihe([5, 7, 9]), [[0, 5], [1, 7], [2, 9]]);
  assert.deepEqual(ausReihe([]), []);
});
