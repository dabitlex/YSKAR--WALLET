import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { sendeStand, empfaengerBytes, empfaengerErlaubt, ueberweisungBytes, gebuehrFuer } from '../src/wallet/fassung.ts';
import { tippeAdresse } from '../src/wallet/tasten.ts';
import { zahlungAusText } from '../src/wallet/qr.ts';
import { keypairFromMnemonic } from '../src/kern/wallet.ts';
import { rawAddress, encodeAddress, decodeAddress, isValidAddress, ADDRESS_V1 } from '../src/kern/address.ts';
import { buildTransfer, checkTransfer, txBytes, TX_VERSION, TRANSFER_V2 } from '../src/kern/tx.ts';
import { V6_HEIGHT, MIN_FEE, DECIMALS } from '../src/kern/params.ts';
import type { Markt } from '../src/netz/api.ts';

/*
  Konsensfassung 6 in der Wallet: welche Ueberweisung sie baut, wann sie
  pausiert, an wen sie zahlen darf und wie gross die Ueberweisung fuer die
  Gebuehr ist. Grundlage: docs/CONSENSUS_V6.md im Hauptrepo.
*/

const werte = JSON.parse(readFileSync(new URL('./vergleichswerte.json', import.meta.url), 'utf8')) as { woerter: string }[];
const ich = keypairFromMnemonic(werte[0].woerter);
const du = keypairFromMnemonic(werte[1].woerter);
const gemeinsam = encodeAddress(rawAddress(ADDRESS_V1, new Uint8Array(32).fill(7)));

const markt = (teil: Partial<Markt>): Markt => ({
  minFee: '0', mindestJeByte: '10', wartend: 0, andrang: false,
  stufen: { langsam: { fee: '1000', block: 3 }, normal: { fee: '2000', block: 1 }, schnell: { fee: '4000', block: 1 } },
  ...teil,
});

test('Fassung 6 liegt bei Block 10.000', () => {
  assert.equal(V6_HEIGHT, 10_000);
});

test('Fassung aus der bekannten Hoehe: v1 davor, Pause in den letzten drei Bloecken, v2 ab der Hoehe', () => {
  // `spitze` ist der letzte Block; gebaut wird fuer den naechsten.
  assert.deepEqual(sendeStand(null, 9_995), { fassung: TX_VERSION, pause: false, unbekannt: false });
  assert.deepEqual(sendeStand(null, 9_996), { fassung: TX_VERSION, pause: true, unbekannt: false });
  assert.deepEqual(sendeStand(null, 9_998), { fassung: TX_VERSION, pause: true, unbekannt: false });
  assert.deepEqual(sendeStand(null, 9_999), { fassung: TRANSFER_V2, pause: false, unbekannt: false });
  assert.deepEqual(sendeStand(null, 12_000), { fassung: TRANSFER_V2, pause: false, unbekannt: false });
});

test('die Auskunft des Knotens geht vor, ohne Auskunft und Hoehe wird nicht gesendet', () => {
  assert.deepEqual(sendeStand(markt({ fassung: 2 }), 5), { fassung: TRANSFER_V2, pause: false, unbekannt: false });
  assert.deepEqual(sendeStand(markt({ fassung: 1, sendepause: true }), 5), { fassung: TX_VERSION, pause: true, unbekannt: false });
  // Ein aelterer Knoten nennt keine Fassung: dann zaehlt die Hoehe.
  assert.equal(sendeStand(markt({}), 9_999).fassung, TRANSFER_V2);
  assert.equal(sendeStand(null, null).unbekannt, true);
});

test('gemeinsame Konten (ysr1p…) haben 63 Zeichen und sind erst ab Fassung 2 als Empfaenger erlaubt', () => {
  assert.equal(gemeinsam.length, 63);
  assert.ok(gemeinsam.startsWith('ysr1p'));
  assert.ok(isValidAddress(gemeinsam));
  assert.equal(empfaengerBytes(gemeinsam), 33);
  assert.equal(empfaengerBytes(du.address), 20);
  assert.equal(empfaengerBytes('ysr1kaputt'), null);
  assert.equal(empfaengerErlaubt(33, TX_VERSION), false);
  assert.equal(empfaengerErlaubt(33, TRANSFER_V2), true);
  assert.equal(empfaengerErlaubt(20, TX_VERSION), true);
});

test('die eigene Tastatur nimmt eine Adresse der Version 1 vollstaendig auf, eine fertige Version 0 nicht laenger', () => {
  let a = '';
  for (const z of gemeinsam.slice(4)) a = tippeAdresse(a, z);
  assert.equal(a, gemeinsam);
  assert.equal(tippeAdresse(a, 'q'), gemeinsam);
  assert.equal(tippeAdresse(du.address, 'q'), du.address);
});

test('Zahlungscodes an ysr1p… werden erkannt', () => {
  const z = zahlungAusText(`yskar:${gemeinsam}?amount=1.5`, DECIMALS);
  assert.equal(z?.adresse, gemeinsam);
});

test('Ueberweisung Fassung 1 gilt bis 9.999, Fassung 2 ab 10.000 -- nie umgekehrt', () => {
  const basis = { from: ich.addressRaw, to: decodeAddress(du.address), amount: 1_000_000n, fee: MIN_FEE, nonce: 0n,
                  publicKey: ich.publicKey, privateKey: ich.privateKey };
  const v1 = buildTransfer({ ...basis, version: TX_VERSION });
  const v2 = buildTransfer({ ...basis, version: TRANSFER_V2 });
  assert.equal(checkTransfer(v1, 9_999), null);
  assert.equal(checkTransfer(v1, 10_000), 'bad_version');
  assert.equal(checkTransfer(v2, 10_000), null);
  assert.notEqual(checkTransfer(v2, 9_999), null);
});

test('an ysr1p… geht nur Fassung 2, und die Groesse fuer die Gebuehr stimmt mit der echten Ueberweisung', () => {
  const notiz = new TextEncoder().encode('Miete');
  const basis = { from: ich.addressRaw, amount: 1_000_000n, fee: MIN_FEE, nonce: 3n, memo: notiz,
                  publicKey: ich.publicKey, privateKey: ich.privateKey };
  for (const [ziel, fassung] of [[du.address, TX_VERSION], [du.address, TRANSFER_V2], [gemeinsam, TRANSFER_V2]] as const) {
    const tx = buildTransfer({ ...basis, to: decodeAddress(ziel), version: fassung });
    const stand = { fassung, pause: false, unbekannt: false } as const;
    assert.equal(ueberweisungBytes(stand, null, notiz.length, empfaengerBytes(ziel)!), txBytes(tx), `${fassung} an ${ziel.slice(0, 6)}`);
    assert.equal(checkTransfer(tx, fassung === TRANSFER_V2 ? 10_000 : 9_999), null);
  }
  // Mit Auskunft: Grundgroesse vom Knoten, Aufschlag fuer ysr1p… von der Wallet.
  const v2 = { fassung: TRANSFER_V2, pause: false, unbekannt: false } as const;
  assert.equal(ueberweisungBytes(v2, markt({ basisBytes: 174 }), 5, 33), 174 + 12 + 5);
});

test('Gebuehr: Stufe je Byte, nie unter dem Mindestsatz, ohne Auskunft die alte Mindestgebuehr', () => {
  assert.equal(gebuehrFuer(null, 'normal', 200), MIN_FEE);
  assert.equal(gebuehrFuer(markt({}), 'normal', 100), 2_000n);
  assert.equal(gebuehrFuer(markt({}), 'normal', 300), 3_000n);          // Boden 10 je Byte
  const jeByte = markt({ stufen: { langsam: { fee: '0', block: 3, jeByte: '10' }, normal: { fee: '0', block: 1, jeByte: '20' }, schnell: { fee: '0', block: 1, jeByte: '40' } } });
  assert.equal(gebuehrFuer(jeByte, 'schnell', 180), 7_200n);
  assert.equal(gebuehrFuer(jeByte, 'langsam', 180), 1_800n);
});
