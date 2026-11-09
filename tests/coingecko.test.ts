import test from 'node:test';
import assert from 'node:assert/strict';

/*
  CoinGecko-Adapter.

  Getestet wird gegen die ECHTEN Antwortformate, abgefragt am 09.11.2026
  von api.coingecko.com -- gekuerzt, aber Feld fuer Feld in der Form, in
  der sie wirklich kommen.

  Worum es geht: Die Antworten stammen von einem fremden Server. Ein
  fehlendes, falsch getipptes oder boshaftes Feld darf nicht als Zahl in
  einer Finanz-Oberflaeche landen. Alles, was nicht zweifelsfrei lesbar
  ist, muss null werden -- und null heisst in der Oberflaeche "nicht
  verfuegbar", nie "0".
*/

const ablage = new Map<string, string>();
(globalThis as { localStorage?: unknown }).localStorage = {
  getItem: (k: string) => ablage.get(k) ?? null,
  setItem: (k: string, v: string) => { ablage.set(k, String(v)); },
  removeItem: (k: string) => { ablage.delete(k); },
  clear: () => ablage.clear(),
};

let antwort: { status: number; body: unknown; headers?: Record<string, string> } =
  { status: 200, body: null };
let letzteUrl = '';
let letzteKopf: Record<string, string> = {};

globalThis.fetch = (async (url: string | URL, o?: { headers?: Record<string, string> }) => {
  letzteUrl = String(url);
  letzteKopf = o?.headers ?? {};
  return {
    status: antwort.status,
    headers: { get: (n: string) => antwort.headers?.[n.toLowerCase()] ?? null },
    json: async () => antwort.body,
  };
}) as unknown as typeof fetch;

const { coingecko } = await import('../src/markt/coingecko.ts');
const cache = await import('../src/markt/cache.ts');
const { MarktFehler } = await import('../src/markt/typen.ts');

const setze = (body: unknown, status = 200, headers?: Record<string, string>) => {
  antwort = { status, body, headers };
  cache.sperreLoesen();
};

/* ------------------------------------------------------------ Ueberblick */

const GLOBAL = {
  data: {
    active_cryptocurrencies: 22869, markets: 1522,
    total_market_cap: { eur: 2205429989769.5, usd: 2483096714063.3 },
    total_volume: { eur: 62328862751.2, usd: 70000000000 },
    market_cap_percentage: { btc: 60.19044474375607, eth: 10.380421950058976 },
    market_cap_change_percentage_24h_usd: -5.036227604879935,
  },
};

test('Marktueberblick in der gewaehlten Waehrung', async () => {
  setze(GLOBAL);
  const e = await coingecko.ueberblick('eur');
  assert.equal(e.marktkapital, 2205429989769.5);
  assert.equal(e.volumen24h, 62328862751.2);
  assert.equal(e.btcDominanz, 60.19044474375607);
  assert.equal(e.ethDominanz, 10.380421950058976);
  assert.equal(e.anzahlCoins, 22869);
  assert.equal(e.anzahlBoersen, 1522);
  assert.equal(e.marktkapitalAenderung24h, -5.036227604879935);

  setze(GLOBAL);
  assert.equal((await coingecko.ueberblick('usd')).marktkapital, 2483096714063.3);
});

test('Ueberblick ohne Felder liefert null statt 0', async () => {
  setze({ data: {} });
  const e = await coingecko.ueberblick('eur');
  assert.equal(e.marktkapital, null);
  assert.equal(e.btcDominanz, null);
  assert.equal(e.anzahlCoins, null);
});

/* ----------------------------------------------------------------- Liste */

const MARKT = [{
  id: 'bitcoin', symbol: 'btc', name: 'Bitcoin',
  image: 'https://coin-images.coingecko.com/coins/images/1/large/bitcoin.png?1696501400',
  current_price: 66898, market_cap: 1332237757513, market_cap_rank: 1,
  total_volume: 24025687596, price_change_percentage_24h: -1.491,
  sparkline_in_7d: { price: [70000, 69000, 68000] },
}];

test('Coin-Liste wird vollstaendig gelesen', async () => {
  setze(MARKT);
  const [c] = await coingecko.liste({ waehrung: 'eur' });
  assert.equal(c.id, 'bitcoin');
  assert.equal(c.symbol, 'BTC', 'Symbol wird grossgeschrieben');
  assert.equal(c.name, 'Bitcoin');
  assert.equal(c.rang, 1);
  assert.equal(c.preis, 66898);
  assert.equal(c.aenderung24h, -1.491);
  assert.equal(c.marktkapital, 1332237757513);
  assert.equal(c.volumen24h, 24025687596);
  assert.deepEqual(c.spark, [70000, 69000, 68000]);
  assert.ok(c.bild?.startsWith('https://'));
});

test('Eintraege ohne id, symbol oder name fallen weg', async () => {
  setze([{ id: 'a', symbol: 's', name: 'N' }, { id: 'b' }, { symbol: 'x', name: 'Y' }, null, 'text']);
  const l = await coingecko.liste({ waehrung: 'eur' });
  assert.equal(l.length, 1, 'nur der vollstaendige Eintrag bleibt');
  assert.equal(l[0].id, 'a');
});

test('unbrauchbare Zahlen werden null', async () => {
  setze([{ id: 'a', symbol: 's', name: 'N', current_price: 'viel', market_cap: null,
           total_volume: NaN, price_change_percentage_24h: Infinity, market_cap_rank: 0 }]);
  const [c] = await coingecko.liste({ waehrung: 'eur' });
  assert.equal(c.preis, null);
  assert.equal(c.marktkapital, null);
  assert.equal(c.volumen24h, null);
  assert.equal(c.aenderung24h, null);
  assert.equal(c.rang, null, 'Rang 0 gibt es nicht');
});

test('nur https-Bilder werden uebernommen', async () => {
  setze([{ id: 'a', symbol: 's', name: 'N', image: 'http://unsicher.example/x.png' },
         { id: 'b', symbol: 's', name: 'N', image: 'javascript:alert(1)' }]);
  const l = await coingecko.liste({ waehrung: 'eur' });
  assert.equal(l[0].bild, null, 'http wird verworfen');
  assert.equal(l[1].bild, null, 'javascript: wird verworfen');
});

test('die Liste fragt die richtige Waehrung ab', async () => {
  setze([]);
  await coingecko.liste({ waehrung: 'usd', seite: 2, proSeite: 50 });
  assert.match(letzteUrl, /vs_currency=usd/);
  assert.match(letzteUrl, /page=2/);
  assert.match(letzteUrl, /per_page=50/);
  assert.match(letzteUrl, /sparkline=true/);
});

/* ---------------------------------------------------------------- Detail */

const DETAIL = {
  id: 'bitcoin', symbol: 'btc', name: 'Bitcoin', market_cap_rank: 1,
  image: { large: 'https://coin-images.coingecko.com/coins/images/1/large/bitcoin.png' },
  description: { en: "Bitcoin is the world's <b>first</b> decentralized cryptocurrency.", de: '' },
  links: { homepage: ['http://www.bitcoin.org', 'https://bitcoin.org', ''] },
  market_data: {
    current_price: { eur: 66248 }, market_cap: { eur: 1332237757513 },
    total_volume: { eur: 24025687596 }, ath: { eur: 107662 },
    circulating_supply: 20109831, total_supply: 20109831, max_supply: 21000000,
    price_change_percentage_24h: -2.1157,
  },
};

test('Coin-Detail wird vollstaendig gelesen', async () => {
  setze(DETAIL);
  const d = await coingecko.detail('bitcoin', 'eur');
  assert.equal(d.preis, 66248);
  assert.equal(d.umlauf, 20109831);
  assert.equal(d.hoechstmenge, 21000000);
  assert.equal(d.ath, 107662);
  assert.equal(d.aenderung24h, -2.1157);
});

test('HTML in der Beschreibung wird entfernt', async () => {
  setze(DETAIL);
  const d = await coingecko.detail('bitcoin', 'eur');
  assert.ok(!d.beschreibung?.includes('<b>'), 'kein Markup in die Oberflaeche');
  assert.match(d.beschreibung ?? '', /first decentralized/);
});

test('leere deutsche Beschreibung faellt auf Englisch zurueck und sagt es', async () => {
  // CoinGecko fuehrt praktisch nur englische Texte; description.de ist leer.
  // Die Oberflaeche muss das kennzeichnen statt es zu verschweigen.
  setze(DETAIL);
  const d = await coingecko.detail('bitcoin', 'eur');
  assert.equal(d.beschreibungSprache, 'en');

  setze({ ...DETAIL, description: { en: 'English', de: 'Deutscher Text' } });
  assert.equal((await coingecko.detail('bitcoin', 'eur')).beschreibungSprache, 'de');
});

test('von mehreren Projektseiten wird die erste https-Adresse genommen', async () => {
  setze(DETAIL);
  const d = await coingecko.detail('bitcoin', 'eur');
  assert.equal(d.seite, 'https://bitcoin.org/', 'die http-Adresse davor wird uebersprungen');
});

/* ----------------------------------------------------------------- Chart */

test('Chartpunkte werden gelesen, unbrauchbare verworfen', async () => {
  setze({ prices: [[1791630000000, 73900.5], [1791633600000, 'kaputt'], [1791637200000, 66217.9], 'unsinn'] });
  const r = await coingecko.verlauf('bitcoin', '30d', 'eur');
  assert.equal(r.punkte.length, 2);
  assert.deepEqual(r.punkte[0], [1791630000000, 73900.5]);
  assert.equal(r.zeitraum, '30d');
  assert.match(letzteUrl, /days=30/);
});

test('eine Stunde wird aus den 5-Minuten-Punkten von days=1 geschnitten', async () => {
  // Es gibt keinen Endpunkt fuer eine Stunde. days=1 liefert alle 5 Minuten
  // einen Punkt; davon die letzte Stunde. Die Punkte sind echt, es sind
  // nur weniger -- keine erfundene Dichte.
  const bis = 1_800_000_000_000;
  const punkte: [number, number][] = [];
  for (let i = 288; i >= 0; i--) punkte.push([bis - i * 300_000, 100 + i]);
  setze({ prices: punkte });
  const r = await coingecko.verlauf('bitcoin', '1h', 'eur');
  assert.match(letzteUrl, /days=1/, 'gefragt wird nach einem Tag');
  assert.equal(r.punkte.length, 13, '60 Minuten in 5-Minuten-Schritten, plus der letzte');
  assert.equal(r.punkte[r.punkte.length - 1][0], bis);
  assert.ok(r.punkte[0][0] >= bis - 3_600_000);
});

/* ----------------------------------------------------------------- Suche */

test('Suche liefert Treffer ohne erfundene Kurse', async () => {
  setze({ coins: [{ id: 'bitcoin', symbol: 'btc', name: 'Bitcoin', market_cap_rank: 1,
                    large: 'https://coin-images.coingecko.com/coins/images/1/large/bitcoin.png' }] });
  const [c] = await coingecko.suche('bit');
  assert.equal(c.id, 'bitcoin');
  assert.equal(c.preis, null, '/search liefert keine Kurse -- hier steht dann auch keiner');
  assert.equal(c.marktkapital, null);
});

test('leere Suche fragt gar nicht erst', async () => {
  letzteUrl = '';
  assert.deepEqual(await coingecko.suche('   '), []);
  assert.equal(letzteUrl, '', 'kein Netzaufruf');
});

/* ------------------------------------------------------------ 429-Sperre */

test('429 setzt die Sperre und uebernimmt retry-after', async () => {
  setze({}, 429, { 'retry-after': '35' });
  await assert.rejects(() => coingecko.ueberblick('eur'), (e: unknown) => {
    assert.ok(e instanceof MarktFehler);
    assert.equal(e.art, 'gebremst');
    assert.equal(e.status, 429);
    return true;
  });
  assert.ok(cache.sperrzeit() > 30, 'die Sperre laeuft');

  // Waehrend der Sperre wird gar nicht erst gesendet.
  letzteUrl = '';
  await assert.rejects(() => coingecko.liste({ waehrung: 'eur' }), /gebremst/);
  assert.equal(letzteUrl, '', 'kein weiterer Netzaufruf waehrend der Sperre');
  cache.sperreLoesen();
});

test('die Sperre gilt fuer alle Endpunkte, nicht nur den getroffenen', async () => {
  setze({}, 429, { 'retry-after': '35' });
  await assert.rejects(() => coingecko.detail('bitcoin', 'eur'));
  letzteUrl = '';
  await assert.rejects(() => coingecko.verlauf('ethereum', '7d', 'eur'), /gebremst/);
  assert.equal(letzteUrl, '', 'das Kontingent haengt an der Adresse, nicht am Pfad');
  cache.sperreLoesen();
});

test('andere Fehlerstatus werden gemeldet, sperren aber nicht', async () => {
  setze({}, 500);
  await assert.rejects(() => coingecko.ueberblick('eur'), /antwort/);
  assert.equal(cache.sperrzeit(), 0, 'ein 500 ist kein Kontingentproblem');
});

/* -------------------------------------------------------------- Schluessel */

test('ohne Schluessel wird kein Schluesselkopf gesendet', async () => {
  setze({ data: {} });
  await coingecko.ueberblick('eur');
  assert.equal(letzteKopf['x-cg-demo-api-key'], undefined);
});

test('ein gesetzter Schluessel geht nur an CoinGecko', async () => {
  const schluessel = await import('../src/markt/schluessel.ts');
  // Der geschuetzte Speicher ist in Node nicht da; der Test setzt den
  // gemerkten Wert ueber die oeffentliche Schnittstelle.
  await schluessel.setzen('CG-TestSchluessel12345').catch(() => {});
  setze({ data: {} });
  await coingecko.ueberblick('eur');
  assert.match(letzteUrl, /^https:\/\/api\.coingecko\.com\//, 'nur an CoinGecko');
  await schluessel.entfernen().catch(() => {});
});

test('die Form eines Demo-Schluessels wird geprueft', async () => {
  const { siehtAus, verkuerzt } = await import('../src/markt/schluessel.ts');
  assert.ok(siehtAus('CG-abcdefghij1234'));
  assert.ok(!siehtAus('abcdefghij1234'), 'ohne CG- nicht');
  assert.ok(!siehtAus('CG-kurz'), 'zu kurz nicht');
  assert.ok(!siehtAus('CG-mit leerzeichen 123456'), 'mit Leerzeichen nicht');
  assert.equal(verkuerzt('CG-abcdefghij1234'), 'CG-…1234', 'nur die letzten vier Zeichen');
  assert.equal(verkuerzt(null), null);
});

test('Anfragen gehen nacheinander hinaus, nicht gleichzeitig', async () => {
  // Beim Testen beobachtet: Liste und Ueberblick starteten im selben
  // Augenblick, eine Anfrage kam mit 429 zurueck -- und die Sperre traf
  // dann auch den Chart, den der Nutzer danach oeffnete. Seitdem geht
  // immer nur eine Anfrage hinaus.
  setze({ data: {} });
  const zeiten: number[] = [];
  const vorher = globalThis.fetch;
  globalThis.fetch = (async (url: string | URL) => {
    zeiten.push(Date.now());
    letzteUrl = String(url);
    return { status: 200, headers: { get: () => null }, json: async () => ({ data: {} }) };
  }) as unknown as typeof fetch;

  const start = Date.now();
  await Promise.all([
    coingecko.ueberblick('eur'),
    coingecko.ueberblick('usd'),
  ]);
  globalThis.fetch = vorher;

  assert.equal(zeiten.length, 2);
  const abstand = zeiten[1] - zeiten[0];
  assert.ok(abstand >= 1400, `zwischen den Anfragen lagen nur ${abstand} ms`);
  assert.ok(Date.now() - start < 6000, 'aber es dauert auch nicht ewig');
});
