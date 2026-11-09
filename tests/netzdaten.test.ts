import test from 'node:test';
import assert from 'node:assert/strict';
import {
  netzstandAus, umlaufAus, hashrateText, zahlText, umlaufAnteil, blockAbstand,
} from '../src/netz/netzdaten.ts';
import { betragGenau } from '../src/wallet/betrag.ts';
import { INITIAL_REWARD } from '../src/kern/params.ts';

/*
  Netzdaten: Die Felder kommen von einem Server. Hier wird geprueft, dass
  die Wallet eine unvollstaendige, falsch getippte oder boshafte Antwort
  uebersteht -- und dass ein fehlender Wert als null ankommt und nicht
  als 0. Der Unterschied ist in der Oberflaeche der zwischen "weiss ich
  nicht" und "ist keiner da".

  Die Beispielantwort unten ist die echte Antwort von yskar.vercel.app
  vom 09.11.2026, Hoehe 8.409.
*/

const ECHT = {
  token: { token_name: 'YSKAR', token_symbol: 'YSR', decimals: 8 },
  height: 8409, nextHeight: 8410,
  difficulty: 370115996, difficultyWert: '370115996',
  hashrate: 41219230519.2586, targetBlockTime: 600,
  tipHash: '000000000004fcfc359a5b8a9678854c63dc4506ca5435fd512fc5be6c8a8534',
  stateHeight: 8409, totalSupply: '735875000000000', maxSupply: '2100000000000000',
  nextReward: '87500000000', mempool: 0,
  minerHashrate: 64083533467.112854, miningSessions: 30, activeMiners: 19, knoten: 22,
};

test('echte Antwort des Spiegels wird vollstaendig gelesen', () => {
  const n = netzstandAus(ECHT);
  assert.equal(n.hoehe, 8409);
  assert.equal(n.naechsteHoehe, 8410);
  assert.equal(n.mempool, 0);
  assert.equal(n.schwierigkeit, 370115996n);
  assert.equal(n.hashrate, 41219230519.2586);
  assert.equal(n.minerHashrate, 64083533467.112854);
  assert.equal(n.aktiveMiner, 19);
  assert.equal(n.sitzungen, 30);
  assert.equal(n.knoten, 22);
  assert.equal(n.naechsteBelohnung, 87500000000n);
  assert.equal(n.zielBlockzeit, 600);
  assert.equal(n.gesamtmenge, 735875000000000n);
  assert.equal(n.hoechstmenge, 2100000000000000n);
});

test('fehlende Felder werden null, nicht 0', () => {
  const n = netzstandAus({ height: 100 });
  assert.equal(n.hoehe, 100);
  assert.equal(n.hashrate, null);
  assert.equal(n.aktiveMiner, null);
  assert.equal(n.naechsteBelohnung, null);
  // Nur der Mempool faellt auf 0 zurueck: "keine wartenden" ist dort die
  // richtige Aussage, und die Zahl steht nie allein in der Oberflaeche.
  assert.equal(n.mempool, 0);
});

test('Unsinn wirft nicht und wird null', () => {
  for (const roh of [null, undefined, 42, 'text', [], { height: 'viele' },
                     { height: -5 }, { height: 1.5 }, { hashrate: NaN },
                     { hashrate: Infinity }, { nextReward: '-1' }, { nextReward: '0x10' }]) {
    const n = netzstandAus(roh);
    assert.equal(typeof n, 'object');
    assert.ok(n.hoehe === null || Number.isInteger(n.hoehe));
  }
});

test('difficultyWert hat Vorrang vor difficulty', () => {
  // Ab sehr hohen Werten traegt eine JavaScript-Zahl nicht mehr genau;
  // der Spiegel liefert daneben den Text. Der Text gewinnt.
  const n = netzstandAus({ difficulty: 9007199254740993, difficultyWert: '9007199254740993' });
  assert.equal(n.schwierigkeit, 9007199254740993n);
});

test('Mengen kommen als Text und werden nicht gerundet', () => {
  const u = umlaufAus({
    height: 8409, circulating: '6291772.61595773', total: '6291772.61595773',
    max: '21000000', minted: '7358750', unspendable: '1066977.38404227',
  });
  assert.equal(u.umlauf, '6291772.61595773');
  assert.equal(u.hoechst, '21000000');
  assert.equal(u.gesperrt, '1066977.38404227');
  assert.equal(umlaufAus({ circulating: 1234.5 }).umlauf, null, 'Zahl statt Text wird abgelehnt');
});

test('Hashrate wird lesbar, mit einer Stelle', () => {
  assert.equal(hashrateText(41219230519.2586, 'de-DE'), '41,2 GH/s');
  assert.equal(hashrateText(41219230519.2586, 'en-GB'), '41.2 GH/s');
  assert.equal(hashrateText(0, 'de-DE'), '0 H/s');
  assert.equal(hashrateText(999, 'de-DE'), '999 H/s');
  assert.equal(hashrateText(1500, 'de-DE'), '1,5 kH/s');
  assert.equal(hashrateText(2.5e12, 'de-DE'), '2,5 TH/s');
  assert.equal(hashrateText(null, 'de-DE'), null);
});

test('Zahlen mit Tausendertrennung der Sprache', () => {
  assert.equal(zahlText(8409, 'de-DE'), '8.409');
  assert.equal(zahlText(8409, 'en-GB'), '8,409');
  assert.equal(zahlText(null, 'de-DE'), null);
});

test('Umlaufanteil wird in bigint gerechnet', () => {
  assert.equal(umlaufAnteil(735875000000000n, 2100000000000000n), 0.3504);
  assert.equal(umlaufAnteil(0n, 2100000000000000n), 0);
  assert.equal(umlaufAnteil(3n, 2n), 1, 'nie ueber 1');
  assert.equal(umlaufAnteil(1n, 0n), null, 'keine Division durch 0');
  assert.equal(umlaufAnteil(null, 2n), null);
});

test('Abstand zur Aktivierung von Fassung 6', () => {
  const a = blockAbstand(8409, 10000, 600);
  assert.equal(a?.bloecke, 1591);
  assert.equal(a?.sekunden, 954600);
  assert.equal(blockAbstand(10000, 10000, 600), null, 'erreicht = kein Abstand');
  assert.equal(blockAbstand(10001, 10000, 600), null, 'darueber = kein Abstand');
  assert.equal(blockAbstand(8409, 10000, null)?.sekunden, null, 'ohne Zielzeit keine Zeit');
});

test('Blockbelohnung wird von Einheiten in YSR umgerechnet', () => {
  // Der Spiegel liefert nextReward in EINHEITEN (1 YSR = 10^8). Wer das
  // direkt anzeigt, schreibt "87.500.000.000 YSR" statt "875 YSR" --
  // ein Faktor 100 Millionen. Genau das ist beim Bauen der Netzkarte
  // einmal passiert, deshalb steht es hier fest.
  const roh = netzstandAus(ECHT).naechsteBelohnung;
  assert.equal(roh, 87500000000n, 'Einheiten bleiben Einheiten');
  assert.equal(betragGenau(roh, 'de-DE', 8, 0), '875', 'angezeigt werden YSR');
  assert.equal(betragGenau(roh, 'en-GB', 8, 0), '875');
  // Nach dem ersten Halving: 437,5 YSR.
  assert.equal(betragGenau(43750000000n, 'de-DE', 8, 0), '437,5');
  // INITIAL_REWARD aus den Konsensparametern, zur Gegenprobe.
  assert.equal(roh, INITIAL_REWARD, 'passt zu kern/params.ts');
});
