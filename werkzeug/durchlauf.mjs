/*
  Durchlauf durch die ganze App -- jeder Bildschirm, jeder Uebergang.

  Was hier geprueft wird, prueft kein Unit-Test: dass die Seiten
  tatsaechlich erscheinen, dass die Zurueck-Taste zurueckfuehrt und dass
  dabei nirgends ein Fehler in die Konsole faellt. Ein Typfehler faellt
  beim Bauen auf; eine Seite, die nach einem Reiterwechsel weiss bleibt,
  nicht.

  Aufruf:
      node werkzeug/durchlauf.mjs                  # mit festen Beispieldaten
      node werkzeug/durchlauf.mjs --live           # gegen die echte Markt-API
      node werkzeug/durchlauf.mjs --bilder         # Screenshots nach /tmp/yskar-shots

  Voraussetzung: "npm run dev" laeuft auf Port 5180, oder --port angeben.

  Die Wallet wird aus dem OEFFENTLICHEN BIP39-Testvektor wiederhergestellt
  ("abandon ... about"). Diese Woerter sind weltweit bekannt und halten
  kein Guthaben. Es wird nichts gesendet -- keine Zeile dieses Skripts
  ruft POST /api/v2/tx auf.
*/
import { mkdirSync } from 'node:fs';

const arg = (n, vor) => {
  const i = process.argv.indexOf(n);
  return i >= 0 ? (process.argv[i + 1] ?? vor) : vor;
};
const hat = n => process.argv.includes(n);

const PORT = arg('--port', '5180');
const BASIS = `http://127.0.0.1:${PORT}/`;
const LIVE = hat('--live');
const BILDER = hat('--bilder');
const BILDORT = '/tmp/yskar-shots';

const WOERTER = ['abandon','abandon','abandon','abandon','abandon','abandon',
                 'abandon','abandon','abandon','abandon','abandon','about'].join(' ');
const PIN = '246810';

/* Beispielantworten fuer die Markt-API. Fest, damit der Durchlauf
   unabhaengig vom Kontingent des Anbieters immer dasselbe prueft.
   Mit --live wird stattdessen wirklich gefragt. */
const SPARK = Array.from({ length: 168 }, (_, i) => 60000 + Math.sin(i / 9) * 2500);
const BEISPIEL = {
  global: { data: {
    active_cryptocurrencies: 22869, markets: 1522,
    total_market_cap: { eur: 2205429989769, usd: 2483096714063 },
    total_volume: { eur: 62328862751, usd: 70000000000 },
    market_cap_percentage: { btc: 60.19, eth: 10.38 },
    market_cap_change_percentage_24h_usd: -5.04,
  } },
  markets: [
    { id: 'bitcoin', symbol: 'btc', name: 'Bitcoin', image: 'https://coin-images.coingecko.com/coins/images/1/large/bitcoin.png',
      current_price: 66248, market_cap: 1332237757513, market_cap_rank: 1, total_volume: 24025687596,
      price_change_percentage_24h: -2.12, sparkline_in_7d: { price: SPARK } },
    { id: 'ethereum', symbol: 'eth', name: 'Ethereum', image: 'https://coin-images.coingecko.com/coins/images/279/large/ethereum.png',
      current_price: 1882, market_cap: 227000000000, market_cap_rank: 2, total_volume: 12000000000,
      price_change_percentage_24h: -6.01, sparkline_in_7d: { price: SPARK.map(x => x / 35) } },
  ],
  detail: {
    id: 'bitcoin', symbol: 'btc', name: 'Bitcoin', market_cap_rank: 1,
    image: { large: 'https://coin-images.coingecko.com/coins/images/1/large/bitcoin.png' },
    description: { en: 'Bitcoin is the first decentralized cryptocurrency.', de: '' },
    links: { homepage: ['https://bitcoin.org'] },
    market_data: { current_price: { eur: 66248, usd: 74000 }, market_cap: { eur: 1332237757513, usd: 1480000000000 },
      total_volume: { eur: 24025687596, usd: 27000000000 }, ath: { eur: 107662, usd: 120000 },
      circulating_supply: 20109831, total_supply: 20109831, max_supply: 21000000,
      price_change_percentage_24h: -2.12 },
  },
  chart: { prices: Array.from({ length: 168 }, (_, i) => [Date.now() - (167 - i) * 3600000, SPARK[i]]) },
  search: { coins: [{ id: 'ethereum', symbol: 'eth', name: 'Ethereum', market_cap_rank: 2,
                      large: 'https://coin-images.coingecko.com/coins/images/279/large/ethereum.png' }] },
};

const pw = await import('playwright').catch(() =>
  import('/opt/node22/lib/node_modules/playwright/index.js'));
const { chromium } = pw.default ?? pw;

const fehler = [];
const bericht = [];
let schritt = 0;

const browser = await chromium.launch();
const ctx = await browser.newContext({ viewport: { width: 412, height: 915 }, deviceScaleFactor: 1 });
await ctx.grantPermissions(['clipboard-read', 'clipboard-write'], { origin: BASIS.replace(/\/$/, '') });

if (!LIVE) {
  await ctx.route('**/api.coingecko.com/**', route => {
    const u = route.request().url();
    const b = u.includes('/global') ? BEISPIEL.global
      : u.includes('/search') ? BEISPIEL.search
      : u.includes('market_chart') ? BEISPIEL.chart
      : u.includes('/coins/markets') ? BEISPIEL.markets
      : u.includes('/coins/') ? BEISPIEL.detail : [];
    route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(b) });
  });
}

const p = await ctx.newPage();
p.on('pageerror', e => fehler.push(`PAGEERROR ${e.message}`));
p.on('console', m => { if (m.type() === 'error') fehler.push(`KONSOLE ${m.text().slice(0, 160)}`); });

if (BILDER) mkdirSync(BILDORT, { recursive: true });

const warte = ms => p.waitForTimeout(ms);
const kopf = () => p.evaluate(() => {
  const el = document.querySelector('h1, h2, .sf-titel, .kopf-zurueck h1');
  return (el?.innerText || document.body.innerText || '').trim().split('\n')[0].slice(0, 48);
});
const klick = async re => {
  for (const b of await p.$$('button')) {
    const t = ((await b.innerText().catch(() => '')) || '').trim();
    if (t && re.test(t)) { await b.click().catch(() => {}); return t; }
  }
  return null;
};
/* Zu einem Reiter. Unterseiten haben keine Leiste -- erst so weit zurueck,
   bis sie wieder da ist. Genau das tut auf dem Geraet die Zurueck-Taste. */
const zuReiter = async (i) => {
  for (let v = 0; v < 6; v++) {
    const leiste = await p.$$('.nav button');
    if (leiste.length === 4) { await leiste[i].click(); await warte(1800); return true; }
    const z = await p.$('.kopf-zurueck button, .sf-kopf button, .st-kopf button, .blatt-grund');
    if (!z) break;
    await z.click().catch(() => {});
    await warte(800);
  }
  fehler.push(`Reiter ${i} nicht erreichbar -- Leiste fehlt`);
  return false;
};
const tippe = async s => {
  for (const c of s) {
    const el = await p.$(`button:text-is("${c}")`);
    if (el) { await el.click().catch(() => {}); await warte(60); }
  }
};
const pruefe = async (name, erwartet) => {
  schritt++;
  await warte(900);
  const k = await kopf();
  const leer = await p.evaluate(() => (document.body.innerText || '').trim().length < 10);
  const ok = !leer && (!erwartet || erwartet.test(k));
  bericht.push({ nr: schritt, name, kopf: k, ok });
  if (BILDER) await p.screenshot({ path: `${BILDORT}/${String(schritt).padStart(2, '0')}-${name.replace(/\W+/g, '-')}.png` });
  if (!ok) fehler.push(`SEITE "${name}": Kopf "${k}"${leer ? ' (Seite leer)' : ''}`);
  return ok;
};

/* ------------------------------------------------------------ Einstieg */
await p.goto(BASIS, { waitUntil: 'networkidle' });
await p.waitForSelector('.startbild', { state: 'detached', timeout: 25000 }).catch(() => {});
await warte(900);
await pruefe('Einstieg');

await klick(/already have a wallet|bestehende Wallet/i);
await warte(800);
await pruefe('Wiederherstellen');
await p.evaluate(w => navigator.clipboard.writeText(w), WOERTER);
await klick(/^Paste$|^Einf/i);
await warte(1100);
await klick(/^Continue$|^Weiter$/i);
await warte(1100);
await pruefe('PIN setzen');
await tippe(PIN); await warte(600); await tippe(PIN); await warte(2600);
await klick(/Open wallet|Zur Wallet/i);
await warte(2600);

/* -------------------------------------------------------------- Reiter */
await pruefe('Wallet', /Wallet/i);

const reiter = await p.$$('.nav button');
const namen = [];
for (const r of reiter) namen.push(((await r.innerText()) || '').trim());
if (namen.length !== 4) fehler.push(`Navigation hat ${namen.length} Reiter statt 4: ${namen.join(', ')}`);

// Markets
await zuReiter(1);
await pruefe('Markets', /Markets/i);
const zeilen = await p.$$eval('.mk-haupt', x => x.length);
if (zeilen === 0) fehler.push('Markets: keine Coin-Zeilen');

// Coin-Detail und zurueck
if (zeilen > 0) {
  await (await p.$$('.mk-haupt'))[0].click(); await warte(2600);
  await pruefe('Coin-Detail', /Bitcoin|Ethereum/i);
  const pfade = await p.$$eval('.chart svg path', x => x.length).catch(() => 0);
  if (pfade < 2) fehler.push(`Coin-Detail: Chart hat ${pfade} Pfade statt 2`);
  // Zeitraum wechseln
  const chips = await p.$$('.cd-zeit .chip');
  if (chips.length >= 4) { await chips[3].click(); await warte(2200); }
  await pruefe('Coin-Detail (30 Tage)', /Bitcoin|Ethereum/i);
  await klick(/^$/) ;
  const zurueck = await p.$('.kopf-zurueck button, .zurueck');
  if (zurueck) { await zurueck.click(); await warte(1600); }
  await pruefe('zurueck zu Markets', /Markets/i);
}

// Portfolio
await klick(/^Portfolio/); await warte(2200);
await pruefe('Portfolio', /Portfolio/i);
await klick(/Add position|Position hinzu/i); await warte(1600);
await pruefe('Position anlegen', /New position|Neue Position/i);
const zur = await p.$('.kopf-zurueck button, .sf-kopf button');
if (zur) { await zur.click(); await warte(1400); }

// Watchlist
const zur2 = await p.$('.kopf-zurueck button, .sf-kopf button');
if (zur2) { await zur2.click(); await warte(1400); }
await klick(/^Watchlist/); await warte(2000);
await pruefe('Watchlist (leer)', /Watchlist/i);
const zur3 = await p.$('.kopf-zurueck button, .sf-kopf button');
if (zur3) { await zur3.click(); await warte(1400); }

// Activity
await zuReiter(2);
await pruefe('Activity', /Activity|Aktivit/i);
const zeit = await p.$$('.vl-zeit .chip');
if (zeit.length !== 4) fehler.push(`Activity: ${zeit.length} Zeitraum-Chips statt 4`);
if (zeit.length > 1) { await zeit[1].click(); await warte(1600); }
await pruefe('Activity (30 Tage)', /Activity|Aktivit/i);

// Settings und seine Blaetter
await zuReiter(3);
await pruefe('Settings', /Settings|Einstellungen/i);
// Ein Blatt schliesst ueber die Hintergrundebene (Blatt in ui/teile.tsx:
// onClick, wenn target === currentTarget) oder die Zurueck-Taste.
const blattZu = async () => {
  await p.$eval('.blatt-grund', el => el.click()).catch(() => {});
  await p.waitForSelector('.blatt-grund', { state: 'detached', timeout: 4000 }).catch(() => {});
  await warte(500);
};
for (const [name, re] of [['Währung', /Currency|Währung/], ['Marktdaten', /Market data|Marktdaten/],
                          ['Sprache', /^Language|^Sprache/], ['Recht', /^Legal|^Recht/]]) {
  await klick(re); await warte(1400);
  await pruefe(`Settings / ${name}`);
  await blattZu();
}

// Sicherheit
await zuReiter(3);
await klick(/Security|Sicherheit/i); await warte(1800);
await pruefe('Sicherheit', /Security|Sicherheit/i);

// Zurueck bis zur Wallet
await zuReiter(0);
await pruefe('zurueck auf Wallet', /Wallet/i);

// Senden und Empfangen (ohne zu senden)
await klick(/^Receive$|^Empfangen$/); await warte(2200);
await pruefe('Empfangen', /Receive|Empfangen/i);
const z4 = await p.$('.kopf-zurueck button, .sf-kopf button');
if (z4) { await z4.click(); await warte(1400); }

// Neustart: bleibt alles?
await p.reload({ waitUntil: 'networkidle' });
await warte(1600);
await tippe(PIN);
await warte(3000);
await pruefe('nach Neustart gesperrt und entsperrt', /Wallet/i);

/* -------------------------------------------------------------- Bericht */
await browser.close();

const breite = Math.max(...bericht.map(b => b.name.length), 10);
console.log('\nDurchlauf' + (LIVE ? ' (live)' : ' (feste Beispieldaten)') + '\n');
for (const b of bericht) {
  console.log(`  ${b.ok ? 'ok  ' : 'FEHL'} ${String(b.nr).padStart(2)}  ${b.name.padEnd(breite)}  ${b.kopf}`);
}
const schlecht = bericht.filter(b => !b.ok).length;
console.log(`\n  ${bericht.length} Bildschirme, ${schlecht} ohne Inhalt, ${fehler.length} Meldungen`);
if (fehler.length) {
  console.log('\nMeldungen:');
  for (const f of [...new Set(fehler)]) console.log('  - ' + f);
}
process.exit(fehler.length ? 1 : 0);
