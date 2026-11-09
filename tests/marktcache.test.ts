import test from 'node:test';
import assert from 'node:assert/strict';

/*
  Cache der Marktschicht.

  Hintergrund ist eine Messung vom 09.11.2026: 12 Aufrufe der oeffentlichen
  CoinGecko-API nacheinander ergaben 6x HTTP 200 und 6x HTTP 429 mit
  "retry-after: 35". Die Schicht muss das aushalten, ohne dass die
  Oberflaeche leer wird -- und ohne die Sperre durch Nachfragen zu
  verlaengern.

  localStorage gibt es in Node nicht. Die Tests setzen ein einfaches
  Doppel, bevor das Modul geladen wird.
*/

const ablage = new Map<string, string>();
(globalThis as { localStorage?: unknown }).localStorage = {
  getItem: (k: string) => ablage.get(k) ?? null,
  setItem: (k: string, v: string) => { ablage.set(k, String(v)); },
  removeItem: (k: string) => { ablage.delete(k); },
  clear: () => ablage.clear(),
};

const { holen, schreiben, lesenRoh, leeren, sperren, sperrzeit, sperreLoesen, vereinen } =
  await import('../src/markt/cache.ts');

const frisch = (ms: number) => ({ frischeMs: ms });

test('frischer Wert wird nicht geholt', async () => {
  leeren(); sperreLoesen();
  let rufe = 0;
  const laden = async () => { rufe++; return { preis: 1 }; };
  const a = await holen('k', laden, frisch(60_000));
  const b = await holen('k', laden, frisch(60_000));
  assert.equal(rufe, 1, 'der zweite Aufruf kommt aus dem Cache');
  assert.equal(a?.veraltet, false);
  assert.equal(b?.veraltet, false);
  assert.deepEqual(b?.wert, { preis: 1 });
});

test('abgelaufener Wert wird neu geholt', async () => {
  leeren(); sperreLoesen();
  let rufe = 0;
  const laden = async () => { rufe++; return rufe; };
  await holen('k', laden, { frischeMs: 1000, jetzt: 0 });
  const zweit = await holen('k', laden, { frischeMs: 1000, jetzt: 5000 });
  assert.equal(rufe, 2);
  assert.equal(zweit?.wert, 2);
  assert.equal(zweit?.veraltet, false);
});

test('scheitert der Abruf, gilt der letzte Stand -- als veraltet', async () => {
  leeren(); sperreLoesen();
  schreiben('k', { preis: 42 }, 0);
  const r = await holen('k', async () => { throw new Error('Netz weg'); },
                        { frischeMs: 1000, jetzt: 99_000 });
  assert.deepEqual(r?.wert, { preis: 42 }, 'der alte Wert bleibt stehen');
  assert.equal(r?.veraltet, true, 'und ist als alt gekennzeichnet');
  assert.equal(r?.standVon, 0, 'mit dem Zeitpunkt von damals');
});

test('ohne jeden Stand wird der Fehler durchgereicht', async () => {
  leeren(); sperreLoesen();
  await assert.rejects(
    () => holen('leer', async () => { throw new Error('Netz weg'); }, frisch(1000)),
    /Netz weg/);
});

test('Sperre nach 429: es wird nichts gesendet', async () => {
  leeren(); sperreLoesen();
  schreiben('k', 'alt', 0);
  sperren(35, 0);
  let rufe = 0;
  const r = await holen('k', async () => { rufe++; return 'neu'; },
                        { frischeMs: 1, jetzt: 10_000 });
  assert.equal(rufe, 0, 'waehrend der Sperre kein Netzaufruf');
  assert.equal(r?.wert, 'alt');
  assert.equal(r?.veraltet, true);
});

test('Sperre ohne Stand liefert null statt zu werfen', async () => {
  leeren(); sperreLoesen();
  sperren(35, 0);
  const r = await holen('nix', async () => 'neu', { frischeMs: 1, jetzt: 1000 });
  assert.equal(r, null);
});

test('Sperre laeuft ab', async () => {
  sperreLoesen();
  sperren(35, 0);
  assert.equal(sperrzeit(0), 35);
  assert.equal(sperrzeit(20_000), 15);
  assert.equal(sperrzeit(35_000), 0, 'nach 35 s wieder frei');
});

test('Nachfragen verlaengert die Sperre nur, verkuerzt sie nie', () => {
  sperreLoesen();
  sperren(60, 0);
  sperren(5, 0);
  assert.equal(sperrzeit(0), 60, 'die kuerzere Sperre gewinnt nicht');
  sperren(120, 0);
  assert.equal(sperrzeit(0), 120, 'die laengere schon');
});

test('Sperre ist nach oben begrenzt', () => {
  sperreLoesen();
  sperren(9999, 0);
  assert.ok(sperrzeit(0) <= 300, 'hoechstens 5 Minuten, sonst haengt die App');
});

test('gleiche Anfrage gleichzeitig wird zu einem Netzaufruf', async () => {
  leeren(); sperreLoesen();
  let rufe = 0;
  const laden = async () => {
    rufe++;
    await new Promise(r => setTimeout(r, 20));
    return 'wert';
  };
  const [a, b, c] = await Promise.all([
    vereinen('gleich', laden), vereinen('gleich', laden), vereinen('gleich', laden),
  ]);
  assert.equal(rufe, 1, 'drei Aufrufer, ein Netzaufruf');
  assert.deepEqual([a, b, c], ['wert', 'wert', 'wert']);
  // Danach wieder frei.
  await vereinen('gleich', laden);
  assert.equal(rufe, 2);
});

test('verschiedene Anfragen werden nicht zusammengelegt', async () => {
  let rufe = 0;
  const laden = async () => { rufe++; return rufe; };
  await Promise.all([vereinen('a', laden), vereinen('b', laden)]);
  assert.equal(rufe, 2);
});

test('der Cache uebersteht einen Neustart', async () => {
  leeren(); sperreLoesen();
  schreiben('bleibt', { x: 7 }, 1000);
  // Modul neu laden: wie ein Neustart der App, localStorage bleibt.
  const neu = await import('../src/markt/cache.ts?neu=1');
  assert.deepEqual(neu.lesenRoh('bleibt')?.wert, { x: 7 });
});

test('unlesbarer Speicher wirft nicht', async () => {
  ablage.set('yskar.markt.cache.v1', '{kaputt');
  const neu = await import('../src/markt/cache.ts?kaputt=1');
  assert.equal(neu.lesenRoh('egal'), null, 'leer anfangen statt raten');
  ablage.clear();
});

test('lesenRoh liefert den Zeitpunkt mit', () => {
  leeren();
  schreiben('z', 'w', 12345);
  assert.deepEqual(lesenRoh('z'), { wert: 'w', zeit: 12345 });
});
