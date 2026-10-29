import test from 'node:test';
import assert from 'node:assert/strict';
import { betragText, betragStellen, betragGenau, eingabeEinheiten, einheitenEingabe } from '../src/wallet/betrag.ts';
import { notizBytes, notizKuerzen, notizAusHex } from '../src/wallet/notiz.ts';
import { zahlungsCode, zahlungAusCode, zahlungAusText, adresseAusCode } from '../src/wallet/qr.ts';
import { ausEintrag, ausWartend, bestaetigungen, nachTagen } from '../src/wallet/zahlungen.ts';
import { de } from '../src/i18n/de.ts';
import { en } from '../src/i18n/en.ts';

const A = 'ysr1pcy6c8vw2fpp49qh4pg3w348qz6nn6vw2fg7f0';
const B = 'ysr1lfn8dw6st25jxv653vypcp929xs9lzg44954zz';

test('Betraege: abgeschnitten, nie gerundet; Eingabe exakt', () => {
  assert.equal(betragText(128450000000n, 'de-DE'), '1.284,50');
  // Exakt bis zur 8. Stelle, Nullen am Ende weg, mindestens zwei Stellen.
  assert.equal(betragText(99999999n, 'de-DE'), '0,99999999');
  assert.equal(betragText(1n, 'de-DE'), '0,00000001');
  assert.equal(betragText(80000000004500n, 'de-DE'), '800.000,000045');
  assert.equal(betragText(80000000004500n, 'en-GB'), '800,000.000045');
  assert.equal(betragText(87500000000n, 'de-DE'), '875,00');
  assert.equal(betragText(2099999999999999n, 'de-DE'), '20.999.999,99999999');
  assert.equal(betragText(0n, 'de-DE'), '0,00');
  assert.equal(betragText(-87512345n, 'de-DE'), '0,87512345', 'ohne Vorzeichen');
  // Hochzaehlen: feste Stellenzahl, abgeschnitten, nie gerundet.
  assert.equal(betragStellen(99999999n, 'de-DE', 6), '0,999999');
  assert.equal(betragStellen(80000000004500n, 'de-DE', 6), '800.000,000045');
  assert.equal(betragStellen(123n, 'de-DE', 0), '0');
  assert.equal(betragGenau(2500001740n, 'de-DE', 8, 4), '25,0000174');
  assert.equal(eingabeEinheiten('25,5'), 2550000000n);
  assert.equal(eingabeEinheiten('0.1') + eingabeEinheiten('0.2'), eingabeEinheiten('0.3'));
  assert.equal(eingabeEinheiten('abc'), 0n);
  assert.equal(eingabeEinheiten('-5'), 0n);
  assert.equal(einheitenEingabe(128449900000n, ',', 8, 8), '1284,499');
  assert.equal(eingabeEinheiten(einheitenEingabe(128449900001n, ',', 8, 8)), 128449900001n);
});

test('Notiz: 32 Byte, nie mitten im Zeichen gekuerzt', () => {
  assert.equal(notizBytes('Kaffee'), 6);
  assert.equal(notizBytes('ä'), 2);
  const lang = 'ä'.repeat(20);
  assert.equal(notizBytes(notizKuerzen(lang)), 32);
  assert.equal(notizKuerzen(lang), 'ä'.repeat(16));
  assert.equal(notizAusHex('4b6166666565'), 'Kaffee');
  assert.equal(notizAusHex('ff00'), '');
});

test('Zahlungsanforderung: Code hin und zurueck', () => {
  const code = zahlungsCode(A, 4000000000n, 'Konzertkarte');
  assert.equal(code, `yskar:${A}?amount=40&memo=Konzertkarte`);
  assert.deepEqual(zahlungAusCode(code), { adresse: A, betrag: 4000000000n, notiz: 'Konzertkarte' });
  assert.equal(zahlungsCode(A), A);
  assert.equal(adresseAusCode(`YSKAR:${A.toUpperCase()}`), A);
  assert.equal(zahlungAusCode('https://example.org'), null);
  assert.equal(zahlungAusCode(`yskar:${A}?amount=0.000000001`)!.betrag, null);
});

test('der geteilte Text laesst sich als Ganzes einfuegen', () => {
  for (const t of [de, en]) {
    const code = zahlungsCode(A, 2550000000n, 'Für Pizza & Co');
    const text = t.empfangen.teilText('25,50', A, code, 'Für Pizza & Co');
    assert.deepEqual(zahlungAusText(text), { adresse: A, betrag: 2550000000n, notiz: 'Für Pizza & Co' });
    assert.deepEqual(zahlungAusText(t.empfangen.teilAdresse(A)), { adresse: A, betrag: null, notiz: null });
  }
  // Zwei verschiedene Adressen im Text: keine gilt.
  assert.equal(zahlungAusText(`${A} oder ${B}`), null);
});

test('Zahlungen fuer die Anzeige', () => {
  const ein = ausEintrag({ txid: 'aa', height: 3840, idx: 1, timestamp: '1791280000', kind: 'in', counterparty: B, amount: '12000000000', fee: '100000', memo: '44616e6b65' });
  assert.equal(ein.art, 'ein'); assert.equal(ein.notiz, 'Danke'); assert.equal(ein.betrag, 12000000000n);
  const lohn = ausEintrag({ txid: 'bb', height: 3731, idx: 0, timestamp: '1791200000', kind: 'reward', counterparty: null, amount: '87500000000', fee: '0', memo: '706f6f6c' });
  assert.equal(lohn.art, 'belohnung'); assert.equal(lohn.notiz, '');
  const offen = ausWartend({ txid: 'cc', kind: 'out', from: A, to: B, amount: '2500000000', fee: '100000', nonce: '2', memo: null });
  assert.equal(offen.hoehe, null); assert.equal(offen.gegen, B);
  // Ab dem ersten Block gilt eine Zahlung als bestaetigt.
  assert.equal(bestaetigungen(offen, 3850), 0);
  assert.equal(bestaetigungen(ein, 3840), 1);
  assert.equal(bestaetigungen(ein, 3850), 11);
  assert.equal(bestaetigungen(ein, null), 1);
  assert.equal(nachTagen([ein, lohn]).length, 2);
});

test('beide Sprachen haben dieselben Texte', () => {
  const form = (o: unknown, pfad = ''): string[] => Object.entries(o as Record<string, unknown>).flatMap(([k, v]) =>
    v && typeof v === 'object' ? form(v, `${pfad}${k}.`) : [`${pfad}${k}:${typeof v}`]);
  assert.deepEqual(form(en).sort(), form(de).sort());
});
