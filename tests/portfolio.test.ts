import test from 'node:test';
import assert from 'node:assert/strict';
import {
  mengeAusText, mengeText, manuellAnlegen, PositionAbgelehnt, GESPERRT, YSR_POSITION,
  type ManuellePosition,
} from '../src/portfolio/modell.ts';
import {
  bewerten, wertCent, verlaufErgaenzen, tagesVeraenderung, tagesSchluessel,
} from '../src/portfolio/rechnen.ts';

/*
  Portfolio.

  Zwei Dinge duerfen hier niemals passieren, und beide stehen als Test:

    1. YSR darf nicht doppelt gezaehlt werden -- einmal als echter
       Bestand von der Kette und einmal als Zahl von Hand.
    2. Der YSR-Bestand darf nicht in eine Fiat-Summe einfliessen. YSR hat
       keinen Kurs; eine Summe darueber waere erfunden.
*/

const pos = (teil: Partial<ManuellePosition> & { coinId: string; menge: string }): ManuellePosition => ({
  quelle: 'manuell', id: 'x', symbol: 'BTC', name: 'Bitcoin',
  einstandJeStueck: null, angelegt: 0, ...teil,
});

/* --------------------------------------------------------------- Mengen */

test('Mengen gehen exakt hin und zurueck', () => {
  // Jeder dieser Texte ist bereits die kuerzeste Schreibweise, also muss
  // mengeText ihn unveraendert zurueckgeben.
  for (const s of ['1', '0.1', '0.00000001', '21000000', '123.45678901']) {
    const e = mengeAusText(s);
    assert.notEqual(e, null, s);
    assert.equal(mengeText(e!), s, s);
  }
  // Ueberfluessige Nullen fallen weg, der Wert bleibt derselbe.
  assert.equal(mengeText(mengeAusText('1.50000000')!), '1.5');
  assert.equal(mengeText(mengeAusText('0.00')!), '0');
});

test('Mengen werden nicht in Fliesskomma gerechnet', () => {
  // 0.1 + 0.2 ist in Fliesskomma nicht 0.3. In bigint schon.
  const a = mengeAusText('0.1')!;
  const b = mengeAusText('0.2')!;
  assert.equal(mengeText(a + b), '0.3');
});

test('das Komma wird wie der Punkt gelesen', () => {
  assert.equal(mengeAusText('1,5'), mengeAusText('1.5'));
});

test('unbrauchbare Mengen werden abgelehnt', () => {
  for (const s of ['', ' ', 'viel', '-1', '1e5', '0.123456789', '1.2.3', 'NaN', 'Infinity']) {
    assert.equal(mengeAusText(s), null, s);
  }
});

test('mehr als acht Nachkommastellen werden nicht stillschweigend gerundet', () => {
  // Lieber abweisen als heimlich etwas anderes speichern, als eingegeben wurde.
  assert.equal(mengeAusText('0.123456789'), null);
});

/* ------------------------------------------------------- Doppelzaehlung */

test('YSR kann nicht von Hand eingetragen werden', () => {
  // Sonst stuende der echte Bestand neben einer erfundenen Zahl.
  for (const symbol of ['YSR', 'ysr', 'Ysr', 'YSKAR']) {
    assert.throws(
      () => manuellAnlegen({ coinId: 'yskar', symbol, name: 'YSKAR', menge: '10' }),
      (e: unknown) => e instanceof PositionAbgelehnt && e.grund === 'gesperrt',
      symbol);
  }
});

test('die Sperrliste enthaelt beide Schreibweisen', () => {
  assert.ok(GESPERRT.has('YSR'));
  assert.ok(GESPERRT.has('YSKAR'));
});

test('eine On-Chain-Position traegt keine Menge', () => {
  // Der Typ laesst es gar nicht erst zu -- hier nur die Gegenprobe, dass
  // das Modell keine Hintertuer hat.
  assert.equal('menge' in YSR_POSITION, false);
  assert.equal(YSR_POSITION.quelle, 'onchain');
});

test('andere Coins lassen sich anlegen', () => {
  const p = manuellAnlegen({ coinId: 'bitcoin', symbol: 'btc', name: 'Bitcoin', menge: '0.5' });
  assert.equal(p.quelle, 'manuell');
  assert.equal(p.symbol, 'BTC', 'Symbol wird grossgeschrieben');
  assert.equal(p.menge, '0.5');
  assert.equal(p.einstandJeStueck, null);
});

test('Menge 0 oder Unsinn wird abgelehnt', () => {
  for (const m of ['0', '', 'viel', '-5']) {
    assert.throws(() => manuellAnlegen({ coinId: 'c', symbol: 'X', name: 'X', menge: m }),
                  PositionAbgelehnt, m);
  }
});

test('ein unbrauchbarer Einstand wird abgelehnt, ein leerer erlaubt', () => {
  assert.throws(() => manuellAnlegen({ coinId: 'c', symbol: 'X', name: 'X', menge: '1', einstandJeStueck: 'viel' }),
                (e: unknown) => e instanceof PositionAbgelehnt && e.grund === 'einstand');
  assert.equal(manuellAnlegen({ coinId: 'c', symbol: 'X', name: 'X', menge: '1', einstandJeStueck: '' }).einstandJeStueck, null);
});

/* ----------------------------------------------------------- Bewertung */

test('YSR geht nicht in die Fiat-Summe ein', () => {
  // Die Kernregel des ganzen Moduls.
  const b = bewerten([pos({ coinId: 'bitcoin', menge: '1' })], { bitcoin: 50000 }, 123_400_000_00n);
  assert.equal(b.summeCent, 5_000_000, 'nur Bitcoin: 50.000 EUR in Cent');
  assert.equal(b.ysrEinheiten, 123_400_000_00n, 'der YSR-Bestand steht daneben');
  // Es gibt bewusst kein Feld, das beides addiert.
  assert.equal('gesamtCent' in b, false);
});

test('Positionen ohne Kurs zaehlen nicht als 0', () => {
  // Sonst saehe das Portfolio bei einem Netzausfall ploetzlich aermer aus.
  const b = bewerten(
    [pos({ coinId: 'bitcoin', menge: '1' }), pos({ coinId: 'unbekannt', symbol: 'UNK', menge: '100' })],
    { bitcoin: 50000 }, null);
  assert.equal(b.summeCent, 5_000_000, 'die Summe bleibt bei Bitcoin');
  assert.equal(b.ohneKurs, 1, 'und die zweite wird gezaehlt');
  const unk = b.positionen.find(p => p.position.coinId === 'unbekannt')!;
  assert.equal(unk.wertCent, null, 'kein Wert, keine 0');
  assert.equal(unk.anteil, null);
});

test('Gewinn und Verlust je Position', () => {
  const b = bewerten([pos({ coinId: 'bitcoin', menge: '2', einstandJeStueck: '40000' })],
                     { bitcoin: 50000 }, null);
  const p = b.positionen[0];
  assert.equal(p.wertCent, 10_000_000, '2 x 50.000');
  assert.equal(p.einstandCent, 8_000_000, '2 x 40.000');
  assert.equal(p.gvCent, 2_000_000);
  assert.equal(p.gvProzent, 25);
});

test('ohne Einstand gibt es keinen Gewinn, nicht null Gewinn', () => {
  const b = bewerten([pos({ coinId: 'bitcoin', menge: '1' })], { bitcoin: 50000 }, null);
  assert.equal(b.positionen[0].einstandCent, null);
  assert.equal(b.positionen[0].gvCent, null);
  assert.equal(b.gvCent, 0, 'die Gesamtsumme zaehlt diese Position schlicht nicht mit');
  assert.equal(b.gvProzent, null, 'und ohne Einstand gibt es keine Prozent');
});

test('Anteile ergeben zusammen eins', () => {
  const b = bewerten(
    [pos({ coinId: 'a', menge: '1' }), pos({ coinId: 'b', menge: '3' })],
    { a: 100, b: 100 }, null);
  const summe = b.positionen.reduce((s, p) => s + (p.anteil ?? 0), 0);
  assert.ok(Math.abs(summe - 1) < 1e-9);
  assert.equal(b.positionen[0].anteil, 0.75, 'die groessere zuerst');
});

test('die groesste Position steht oben, Positionen ohne Kurs unten', () => {
  const b = bewerten(
    [pos({ coinId: 'klein', menge: '1' }), pos({ coinId: 'ohne', menge: '1' }), pos({ coinId: 'gross', menge: '10' })],
    { klein: 100, gross: 100 }, null);
  assert.deepEqual(b.positionen.map(p => p.position.coinId), ['gross', 'klein', 'ohne']);
});

test('ein leeres Portfolio rechnet ohne Division durch null', () => {
  const b = bewerten([], {}, null);
  assert.equal(b.summeCent, 0);
  assert.equal(b.gvProzent, null);
  assert.equal(b.ohneKurs, 0);
});

test('Wert in Cent wird gerundet, damit Summen nicht driften', () => {
  assert.equal(wertCent(mengeAusText('3')!, 0.1), 30);
  assert.equal(wertCent(mengeAusText('1')!, 0.005), 1, 'auf den Cent gerundet');
  assert.equal(wertCent(mengeAusText('1')!, null), null);
  assert.equal(wertCent(mengeAusText('1')!, NaN), null);
  assert.equal(wertCent(mengeAusText('1')!, -5), null, 'ein negativer Kurs ist keiner');
});

test('sehr grosse Mengen bleiben genau', () => {
  const b = bewerten([pos({ coinId: 'a', menge: '21000000' })], { a: 1 }, null);
  assert.equal(b.summeCent, 2_100_000_000);
});

/* ------------------------------------------------------------- Verlauf */

test('je Tag genau ein Punkt, der letzte gewinnt', () => {
  const t = Date.parse('2026-11-09T10:00:00');
  let v = verlaufErgaenzen([], 1000, 400, t);
  v = verlaufErgaenzen(v, 2000, 400, t + 3_600_000);
  assert.equal(v.length, 1, 'derselbe Tag ersetzt');
  assert.equal(v[0].cent, 2000, 'der spaetere Wert gilt');
});

test('ein neuer Tag kommt dazu', () => {
  const t = Date.parse('2026-11-09T10:00:00');
  let v = verlaufErgaenzen([], 1000, 400, t);
  v = verlaufErgaenzen(v, 1500, 400, t + 86_400_000);
  assert.equal(v.length, 2);
  assert.equal(tagesVeraenderung(v), 500);
});

test('ohne Vortag gibt es keine Tagesveraenderung', () => {
  assert.equal(tagesVeraenderung([]), null);
  assert.equal(tagesVeraenderung([{ tag: '2026-11-09', cent: 100 }]), null);
});

test('der Verlauf waechst nicht unbegrenzt', () => {
  let v: { tag: string; cent: number }[] = [];
  const start = Date.parse('2024-01-01T12:00:00');
  for (let i = 0; i < 500; i++) v = verlaufErgaenzen(v, i, 400, start + i * 86_400_000);
  assert.equal(v.length, 400, 'die aeltesten fallen weg');
  assert.equal(v[v.length - 1].cent, 499, 'der neueste bleibt');
});

test('der Tagesschluessel folgt dem Kalender des Geraets', () => {
  assert.match(tagesSchluessel(Date.parse('2026-11-09T23:30:00')), /^\d{4}-\d{2}-\d{2}$/);
  assert.notEqual(tagesSchluessel(Date.parse('2026-11-09T12:00:00')),
                  tagesSchluessel(Date.parse('2026-11-10T12:00:00')));
});

test('eine veraenderte Ablage kann keine YSR-Position einschmuggeln', async () => {
  // Die Datei auf dem Geraet laesst sich bearbeiten. Eine dort
  // hineingeschriebene YSR-Position wuerde den echten Bestand doppelt
  // zaehlen -- deshalb greift die Sperre auch beim LESEN.
  const ablage = new Map<string, string>();
  (globalThis as { localStorage?: unknown }).localStorage = {
    getItem: (k: string) => ablage.get(k) ?? null,
    setItem: (k: string, v: string) => { ablage.set(k, String(v)); },
    removeItem: (k: string) => { ablage.delete(k); },
  };
  ablage.set('yskar.portfolio.v1', JSON.stringify({
    positionen: [
      { quelle: 'manuell', id: 'a', coinId: 'yskar', symbol: 'YSR', name: 'YSKAR', menge: '999999', angelegt: 0 },
      { quelle: 'manuell', id: 'b', coinId: 'bitcoin', symbol: 'BTC', name: 'Bitcoin', menge: '1', angelegt: 0 },
      { quelle: 'manuell', id: 'c', coinId: 'x', symbol: 'X', menge: 'unsinn', angelegt: 0 },
    ],
    verlauf: [{ tag: '2026-11-09', cent: 100 }, { tag: 'kaputt', cent: 1 }, { tag: '2026-11-10', cent: 'viel' }],
  }));
  const { portfolioLager } = await import('../src/portfolio/lager.ts');
  const stand = portfolioLager.lesen();
  assert.deepEqual(stand.positionen.map(p => p.symbol), ['BTC'],
                   'YSR und die unbrauchbare Position fliegen beim Lesen raus');
  assert.deepEqual(stand.verlauf, [{ tag: '2026-11-09', cent: 100 }],
                   'nur brauchbare Verlaufspunkte bleiben');
});
