import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync, readdirSync, statSync } from 'node:fs';
import { join, relative } from 'node:path';

/*
  Modulgrenzen (docs/ARCHITEKTUR.md Abschnitt 2).

  Die wichtigste Regel dieses Projekts steht hier als Test und nicht nur
  im Dokument: Die MARKTSCHICHT DARF NIE AN SCHLUESSELMATERIAL. Sie
  spricht mit einem fremden Server; wenn sie den Tresor, die Woerter oder
  die Signierfunktion erreichen kann, ist das eine Luecke, die kein
  Codereview dauerhaft zuhaelt.

  Geprueft wird die Importzeile, nicht die Absicht. Eine Vereinbarung, die
  niemand nachmisst, haelt genau bis zum ersten eiligen Commit.
*/

const WURZEL = new URL('../src/', import.meta.url).pathname;

function dateien(ordner: string): string[] {
  const voll = join(WURZEL, ordner);
  let eintraege: string[];
  try { eintraege = readdirSync(voll); } catch { return []; }
  return eintraege.flatMap(name => {
    const p = join(voll, name);
    if (statSync(p).isDirectory()) return dateien(join(ordner, name));
    return /\.tsx?$/.test(name) ? [join(ordner, name)] : [];
  });
}

/** Alle importierten Pfade einer Datei. */
function importe(datei: string): string[] {
  const inhalt = readFileSync(join(WURZEL, datei), 'utf8');
  const treffer = [...inhalt.matchAll(/(?:^|\n)\s*(?:import|export)[^;\n]*?from\s*['"]([^'"]+)['"]/g)];
  const dyn = [...inhalt.matchAll(/\bimport\(\s*['"]([^'"]+)['"]\s*\)/g)];
  return [...treffer, ...dyn].map(m => m[1]);
}

/** Auf welchen Ordner unter src/ zeigt ein Import? */
function zielOrdner(datei: string, pfad: string): string | null {
  if (!pfad.startsWith('.')) return null;
  const basis = join(WURZEL, datei, '..');
  const ziel = relative(WURZEL, join(basis, pfad));
  if (ziel.startsWith('..')) return null;
  return ziel.split('/')[0];
}

const VERBOTEN: { von: string; nach: string[]; warum: string }[] = [
  {
    von: 'markt',
    nach: ['wallet', 'kern'],
    warum: 'Die Marktschicht spricht mit einem fremden Server und darf nie an Tresor, Woerter oder Signieren.',
  },
  {
    von: 'markt',
    nach: ['netz'],
    warum: 'Markt und YSKAR-Kette bleiben unabhaengig: getrennte Fehler, getrennte Sperren.',
  },
  {
    von: 'netz',
    nach: ['markt'],
    warum: 'Ein Ausfall der Markt-API darf die Kette nicht beruehren.',
  },
  {
    von: 'kern',
    nach: ['wallet', 'netz', 'markt', 'ui', 'seiten', 'geraet', 'i18n'],
    warum: 'src/kern ist per Pruefsumme an dabitlex/YSKAR gebunden und steht fuer sich.',
  },
];

for (const regel of VERBOTEN) {
  test(`${regel.von}/ importiert nicht aus ${regel.nach.join(', ')}/`, () => {
    const verstoesse: string[] = [];
    for (const datei of dateien(regel.von)) {
      for (const pfad of importe(datei)) {
        const ziel = zielOrdner(datei, pfad);
        if (ziel && regel.nach.includes(ziel)) verstoesse.push(`${datei} -> ${pfad}`);
      }
    }
    assert.deepEqual(verstoesse, [], `${regel.warum}\nGefunden:\n  ${verstoesse.join('\n  ')}`);
  });
}

test('die Marktschicht nennt kein Wort aus dem Wallet-Teil', () => {
  // Zweiter Riegel neben der Importpruefung: Auch ein Zugriff ueber einen
  // Umweg (globaler Zustand, Zeichenkette) soll auffallen.
  const verdaechtig = /\b(tresor|mnemonic|seedFromMnemonic|privateKey|keypairFrom|woerter|versiegeln|oeffnen)\b/i;
  const treffer: string[] = [];
  for (const datei of dateien('markt')) {
    const inhalt = readFileSync(join(WURZEL, datei), 'utf8');
    for (const zeile of inhalt.split('\n')) {
      // Kommentare duerfen die Begriffe erklaeren, Code nicht.
      const code = zeile.replace(/\/\/.*$/, '').replace(/\/\*.*?\*\//g, '');
      if (verdaechtig.test(code) && !/^\s*\*/.test(zeile)) treffer.push(`${datei}: ${zeile.trim()}`);
    }
  }
  assert.deepEqual(treffer, [], 'Die Marktschicht darf Schluesselmaterial nicht einmal benennen.');
});

test('kern/ bleibt unveraendert gegenueber dem Hauptrepo', () => {
  // HERKUNFT.json nagelt jede Kerndatei auf eine Pruefsumme. Hier nur die
  // Gegenprobe, dass die Liste vollstaendig ist -- die Pruefsummen selbst
  // prueft kern.test.ts.
  const herkunft = JSON.parse(readFileSync(join(WURZEL, 'kern/HERKUNFT.json'), 'utf8')) as
    { dateien: Record<string, string> };
  const genannt = Object.keys(herkunft.dateien).sort();
  const da = dateien('kern').map(d => d.replace(/^kern\//, '')).sort();
  assert.deepEqual(da, genannt, 'Jede Datei in kern/ muss in HERKUNFT.json stehen.');
});

test('der Markt-Schluessel wird nur in der Marktschicht benutzt', () => {
  // Er geht an api.coingecko.com und sonst nirgendwohin -- vor allem nicht
  // an den YSKAR-Spiegel oder den Full Node. Geprueft wird, wer das Modul
  // ueberhaupt importiert und wo der Kopfzeilenname auftaucht.
  const nutzer: string[] = [];
  for (const ordner of ['netz', 'wallet', 'kern', 'geraet', 'portfolio', 'watchlist']) {
    for (const datei of dateien(ordner)) {
      if (importe(datei).some(p2 => p2.includes('markt/schluessel'))) nutzer.push(datei);
    }
  }
  assert.deepEqual(nutzer, [], 'ausserhalb von markt/ und seiten/ darf niemand den Schluessel lesen');

  const kopf = 'x-cg-demo-api-key';
  const stellen: string[] = [];
  for (const ordner of ['netz', 'wallet', 'kern', 'geraet', 'portfolio', 'watchlist', 'markt']) {
    for (const datei of dateien(ordner)) {
      if (readFileSync(join(WURZEL, datei), 'utf8').includes(kopf)) stellen.push(datei);
    }
  }
  assert.deepEqual(stellen, ['markt/coingecko.ts'],
                   'der Schluesselkopf steht an genau einer Stelle');
});
