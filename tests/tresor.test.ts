import test from 'node:test';
import assert from 'node:assert/strict';

/*
  Der Tresor: PIN -> PBKDF2 -> AES-256-GCM. Im Test gibt es kein Android;
  der geschuetzte Speicher wird deshalb nicht angefasst, nur das Verfahren.
*/
const speicher = new Map<string, string>();
(globalThis as Record<string, unknown>).localStorage = {
  getItem: (k: string) => speicher.get(k) ?? null,
  setItem: (k: string, v: string) => { speicher.set(k, v); },
  removeItem: (k: string) => { speicher.delete(k); },
};
const tresor = await import('../src/wallet/tresor.ts');

const WOERTER = 'legal winner thank year wave sausage worth useful legal winner thank yellow';

test('versiegeln und oeffnen', async () => {
  const t = await tresor.versiegeln(WOERTER, '135790', 'ysr1test');
  assert.equal(t.version, 1);
  assert.equal(t.address, 'ysr1test');
  assert.ok(!JSON.stringify(t).includes('winner'), 'die Woerter stehen nicht im Klartext im Tresor');
  assert.equal(await tresor.oeffnen(t, '135790'), WOERTER);
  assert.equal(await tresor.oeffnen(t, '135791'), null);
  // Beschaedigte Daten oeffnen sich nicht, auch nicht mit der richtigen PIN.
  assert.equal(await tresor.oeffnen({ ...t, ciphertext: t.ciphertext.slice(0, -4) + 'AAAA' }, '135790'), null);
});

test('zweimal versiegeln ergibt verschiedene Daten (frisches Salz)', async () => {
  const a = await tresor.versiegeln(WOERTER, '135790', 'x');
  const b = await tresor.versiegeln(WOERTER, '135790', 'x');
  assert.notEqual(a.ciphertext, b.ciphertext);
  assert.notEqual(a.salt, b.salt);
});

test('Bremse nach fuenf falschen Eingaben', () => {
  tresor.richtigePin();
  for (let i = 0; i < 4; i++) tresor.falschePin();
  assert.equal(tresor.wartezeit(), 0);
  tresor.falschePin();
  assert.ok(tresor.wartezeit() > 25 && tresor.wartezeit() <= 30);
  tresor.falschePin();
  assert.ok(tresor.wartezeit() > 55 && tresor.wartezeit() <= 60);
  tresor.richtigePin();
  assert.equal(tresor.wartezeit(), 0);
});
