/*
  Der Kern (Schluessel, Adressen, Ueberweisungen) ist unveraendert aus dem
  Hauptrepo uebernommen. Zwei Pruefungen halten das fest:

  1. Die Dateien sind Byte fuer Byte die aus dem genannten Commit.
  2. Sie liefern dieselben Ergebnisse wie das Hauptrepo: Die Vergleichswerte
     in vergleichswerte.json hat der Code des Hauptrepos erzeugt.

  Schlaegt eine davon fehl, ergibt dieselbe Wortfolge hier womoeglich eine
  andere Adresse -- dann darf keine APK entstehen.
*/
import test from 'node:test';
import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { readFileSync, readdirSync } from 'node:fs';
import { keypairFromMnemonic, isValidMnemonic, createMnemonic } from '../src/kern/wallet.ts';
import { buildTransfer, serializeTx, txidHex, checkTransfer, deserializeTx, transferBytes } from '../src/kern/tx.ts';
import { decodeAddress, isValidAddress } from '../src/kern/address.ts';
import { toHex, fromHex } from '../src/kern/codec.ts';
import { NETWORK, RELAY_FEE_RATE, DECIMALS } from '../src/kern/params.ts';

const werte = JSON.parse(readFileSync(new URL('./vergleichswerte.json', import.meta.url), 'utf8')) as {
  woerter: string; adresse: string; oeffentlich: string; pfad: string; adresse1: string;
  roh: string; txid: string; pruefung: string | null; roh0: string; txid0: string;
}[];

test('Kern-Dateien sind unveraendert aus dem Hauptrepo', () => {
  const herkunft = JSON.parse(readFileSync(new URL('../src/kern/HERKUNFT.json', import.meta.url), 'utf8'));
  const dateien = readdirSync(new URL('../src/kern/', import.meta.url)).filter(f => f.endsWith('.ts')).sort();
  assert.deepEqual(dateien, Object.keys(herkunft.dateien).sort());
  for (const f of dateien) {
    const summe = createHash('sha256').update(readFileSync(new URL('../src/kern/' + f, import.meta.url))).digest('hex');
    assert.equal(summe, herkunft.dateien[f], `${f} weicht vom Hauptrepo ab`);
  }
});

test('dieselben 12 Woerter ergeben dieselbe Adresse wie im Hauptrepo', () => {
  assert.equal(werte.length, 5);
  for (const w of werte) {
    const k = keypairFromMnemonic(w.woerter);
    assert.equal(k.address, w.adresse);
    assert.equal(toHex(k.publicKey), w.oeffentlich);
    assert.equal(k.path, w.pfad);
    assert.equal(keypairFromMnemonic(w.woerter, '', 0, 1).address, w.adresse1);
  }
});

test('eine Ueberweisung ist Byte fuer Byte dieselbe wie im Hauptrepo', () => {
  for (const w of werte) {
    const k = keypairFromMnemonic(w.woerter);
    const k2 = keypairFromMnemonic(w.woerter, '', 0, 1);
    const tx = buildTransfer({ from: k.addressRaw, to: k2.addressRaw, amount: 2500000000n, fee: 1740n, nonce: 7n,
      memo: new TextEncoder().encode('Kaffee'), publicKey: k.publicKey, privateKey: k.privateKey });
    assert.equal(toHex(serializeTx(tx)), w.roh);
    assert.equal(txidHex(tx), w.txid);
    assert.equal(checkTransfer(tx, 5000), w.pruefung);
    const tx0 = buildTransfer({ from: k.addressRaw, to: decodeAddress('ysr1lfn8dw6st25jxv653vypcp929xs9lzg44954zz'),
      amount: 1n, fee: 1680n, nonce: 0n, validUntil: 5000, publicKey: k.publicKey, privateKey: k.privateKey });
    assert.equal(toHex(serializeTx(tx0)), w.roh0);
    assert.equal(txidHex(tx0), w.txid0);
    // Hin und zurueck: was gesendet wird, liest der Knoten genauso.
    assert.equal(toHex(serializeTx(deserializeTx(fromHex(w.roh)))), w.roh);
  }
});

test('Grundwerte der Hauptkette', () => {
  assert.equal(NETWORK, 'yskar-main-1');
  assert.equal(DECIMALS, 8);
  assert.equal(RELAY_FEE_RATE * BigInt(transferBytes(0)), 1680n);
  assert.equal(RELAY_FEE_RATE * BigInt(transferBytes(6)), 1740n);
  assert.ok(isValidAddress('ysr1lfn8dw6st25jxv653vypcp929xs9lzg44954zz'));
  assert.ok(!isValidAddress('ysr1lfn8dw6st25jxv653vypcp929xs9lzg44954zy'));
  const neu = createMnemonic(12);
  assert.equal(neu.split(' ').length, 12);
  assert.ok(isValidMnemonic(neu));
});
