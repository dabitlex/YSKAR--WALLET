import { CapacitorHttp } from '@capacitor/core';
import { istNativ } from '../geraet/plattform.ts';
import * as cache from './cache.ts';
import * as schluessel from './schluessel.ts';
import {
  MarktFehler, type CoinDetail, type CoinKurz, type Chartreihe, type ListeOptionen,
  type MarktAnbieter, type Marktueberblick, type Waehrung, type Zeitraum,
} from './typen.ts';

/**
 * CoinGecko als Marktdatenanbieter.
 *
 * WARUM COINGECKO UND NICHT COINMARKETCAP: Geprueft wurden beide. CoinGecko
 * ist der einzige der beiden, der ohne eingebetteten API-Schluessel
 * ueberhaupt antwortet -- und "kein Schluessel in der APK" ist eine harte
 * Vorgabe. CoinMarketCap verlangt bei jedem Aufruf einen Schluessel und
 * erzwingt damit entweder einen Verstoss gegen diese Vorgabe oder einen
 * eigenen Server davor.
 *
 * WARUM EIN EIGENER HTTP-AUFRUF statt dem aus netz/api.ts: Markt und Kette
 * bleiben getrennt (docs/ARCHITEKTUR.md, geprueft in tests/grenzen.test.ts).
 * Diese Schicht hat andere Zeitgrenzen, eine eigene Sperre nach 429 und
 * darf unter keinen Umstaenden Zugriff auf den Wallet-Teil bekommen. Die
 * paar doppelten Zeilen sind der Preis dieser Grenze und gut angelegt.
 *
 * Die Antworten kommen von einem fremden Server. Jedes Feld wird geprueft,
 * bevor es in die Oberflaeche geht; was nicht zweifelsfrei lesbar ist,
 * wird null.
 */

const BASIS = 'https://api.coingecko.com/api/v3';

/*
  Anfragen in die Reihe stellen, mit Mindestabstand.

  Beobachtet beim Testen: Die Markets-Seite holt Liste und Ueberblick
  gleichzeitig. Zwei Anfragen im selben Augenblick -- eine kam mit 429
  zurueck und sperrte damit AUCH den Chart, den der Nutzer danach
  oeffnete. Die Sperre war richtig; falsch war, das 429 ueberhaupt zu
  provozieren.

  Deshalb geht immer nur eine Anfrage hinaus, mit Abstand dazwischen. Das
  kostet beim ersten Aufbau eine knappe Sekunde und spart die 35 Sekunden
  Sperre, die ein 429 nach sich zieht.

  Mit eigenem Schluessel ist das Kontingent um ein Vielfaches groesser;
  dann genuegt ein kurzer Abstand.
*/
const ABSTAND_OHNE_SCHLUESSEL = 1500;
const ABSTAND_MIT_SCHLUESSEL = 300;

let letzterRuf = 0;
let reihe: Promise<unknown> = Promise.resolve();

function anstellen<T>(fn: () => Promise<T>, abstand: number): Promise<T> {
  const dran = reihe.then(async () => {
    const warten = abstand - (Date.now() - letzterRuf);
    if (warten > 0) await new Promise(r => setTimeout(r, warten));
    letzterRuf = Date.now();
  });
  // Ein Fehler darf die Reihe nicht abreissen lassen.
  reihe = dran.catch(() => undefined);
  return dran.then(fn);
}

/* ------------------------------------------------------------ Pruefen */

const obj = (v: unknown): Record<string, unknown> | null =>
  v && typeof v === 'object' && !Array.isArray(v) ? (v as Record<string, unknown>) : null;

/** Endliche Zahl, sonst null. Lehnt NaN, Unendlich und Text ab. */
function zahl(v: unknown): number | null {
  return typeof v === 'number' && Number.isFinite(v) ? v : null;
}

function text(v: unknown): string | null {
  return typeof v === 'string' && v.trim() ? v.trim() : null;
}

/** Ganze Zahl > 0, sonst null -- fuer Raenge. */
function rang(v: unknown): number | null {
  return typeof v === 'number' && Number.isInteger(v) && v > 0 ? v : null;
}

/** Nur https. Ein http-Link oder ein javascript:-Link wird verworfen. */
function httpsLink(v: unknown): string | null {
  const s = text(v);
  if (!s) return null;
  try {
    const u = new URL(s);
    return u.protocol === 'https:' ? u.toString() : null;
  } catch { return null; }
}

/* -------------------------------------------------------------- Holen */

async function hol(pfad: string): Promise<unknown> {
  const warten = cache.sperrzeit();
  if (warten > 0) throw new MarktFehler('gebremst', 429, warten);

  const key = await schluessel.lesen();
  const kopf: Record<string, string> = { accept: 'application/json' };
  if (key) kopf['x-cg-demo-api-key'] = key;
  const url = BASIS + pfad;

  return anstellen(() => senden(url, kopf), key ? ABSTAND_MIT_SCHLUESSEL : ABSTAND_OHNE_SCHLUESSEL);
}

async function senden(url: string, kopf: Record<string, string>): Promise<unknown> {
  let status = 0;
  let daten: unknown = null;
  let retryAfter = 0;

  if (istNativ()) {
    try {
      const r = await CapacitorHttp.request({
        url, method: 'GET', headers: kopf, connectTimeout: 12_000, readTimeout: 20_000,
      });
      status = r.status;
      daten = typeof r.data === 'string' ? sicherJson(r.data) : r.data;
      retryAfter = Number((r.headers as Record<string, string> | undefined)?.['retry-after']) || 0;
    } catch (e) { throw new MarktFehler('offline', 0, 0, String((e as Error)?.message ?? e)); }
  } else {
    const stopp = new AbortController();
    const uhr = setTimeout(() => stopp.abort(), 20_000);
    try {
      const r = await fetch(url, { headers: kopf, signal: stopp.signal, cache: 'no-store' });
      status = r.status;
      retryAfter = Number(r.headers.get('retry-after')) || 0;
      daten = await r.json().catch(() => null);
    } catch (e) { throw new MarktFehler('offline', 0, 0, String((e as Error)?.message ?? e)); }
    finally { clearTimeout(uhr); }
  }

  if (status === 429) {
    // Die Sperre gilt fuer ALLE Endpunkte: Das Kontingent haengt an der
    // Adresse, nicht am Pfad. Weiter zu fragen verlaengert sie nur.
    cache.sperren(retryAfter || 35);
    throw new MarktFehler('gebremst', 429, cache.sperrzeit());
  }
  if (status < 200 || status >= 300) throw new MarktFehler('antwort', status);
  return daten;
}

function sicherJson(s: string): unknown {
  try { return JSON.parse(s); } catch { return null; }
}

/* ------------------------------------------------------------- Umbauen */

function coinAusMarkt(roh: unknown): CoinKurz | null {
  const o = obj(roh);
  const id = o && text(o.id);
  const symbol = o && text(o.symbol);
  const name = o && text(o.name);
  if (!o || !id || !symbol || !name) return null;
  const spark = obj(o.sparkline_in_7d)?.price;
  return {
    id, symbol: symbol.toUpperCase(), name,
    bild: httpsLink(o.image),
    rang: rang(o.market_cap_rank),
    preis: zahl(o.current_price),
    aenderung24h: zahl(o.price_change_percentage_24h),
    marktkapital: zahl(o.market_cap),
    volumen24h: zahl(o.total_volume),
    spark: Array.isArray(spark) ? spark.filter((x): x is number => typeof x === 'number' && Number.isFinite(x)) : null,
  };
}

/** Zeitraum -> was CoinGecko als `days` versteht. */
function tage(z: Zeitraum): string {
  switch (z) {
    case '1h': case '24h': return '1';
    case '7d': return '7';
    case '30d': return '30';
    case '90d': return '90';
    case '1j': return '365';
    case 'max': return 'max';
  }
}

export class CoinGecko implements MarktAnbieter {
  readonly name = 'CoinGecko';

  async ueberblick(waehrung: Waehrung): Promise<Marktueberblick> {
    const d = obj(obj(await hol('/global'))?.data);
    const kap = obj(d?.total_market_cap);
    const vol = obj(d?.total_volume);
    const dom = obj(d?.market_cap_percentage);
    return {
      marktkapital: zahl(kap?.[waehrung]),
      // CoinGecko fuehrt die Veraenderung nur in USD. Als Prozentwert ist
      // sie in jeder Waehrung nahezu gleich; die Benennung bleibt ehrlich.
      marktkapitalAenderung24h: zahl(d?.market_cap_change_percentage_24h_usd),
      volumen24h: zahl(vol?.[waehrung]),
      btcDominanz: zahl(dom?.btc),
      ethDominanz: zahl(dom?.eth),
      anzahlCoins: rang(d?.active_cryptocurrencies),
      anzahlBoersen: rang(d?.markets),
    };
  }

  async liste(o: ListeOptionen): Promise<CoinKurz[]> {
    const p = new URLSearchParams({
      vs_currency: o.waehrung,
      order: 'market_cap_desc',
      per_page: String(Math.min(250, Math.max(1, o.proSeite ?? 100))),
      page: String(Math.max(1, o.seite ?? 1)),
      sparkline: 'true',
      price_change_percentage: '24h',
    });
    const roh = await hol('/coins/markets?' + p);
    return Array.isArray(roh) ? roh.map(coinAusMarkt).filter((c): c is CoinKurz => c !== null) : [];
  }

  async detail(id: string, waehrung: Waehrung): Promise<CoinDetail> {
    const p = new URLSearchParams({
      localization: 'false', tickers: 'false', market_data: 'true',
      community_data: 'false', developer_data: 'false', sparkline: 'false',
    });
    const o = obj(await hol('/coins/' + encodeURIComponent(id) + '?' + p));
    if (!o) throw new MarktFehler('antwort', 0, 0, 'leere Antwort');
    const m = obj(o.market_data);
    const w = (feld: unknown) => zahl(obj(feld)?.[waehrung]);
    // CoinGecko fuehrt Beschreibungen praktisch nur auf Englisch; die
    // deutsche ist leer (geprueft am 09.11.2026). Die Oberflaeche zeigt
    // deshalb die englische und schreibt dazu, dass sie englisch ist.
    const besch = text(obj(o.description)?.de) ?? text(obj(o.description)?.en);
    const istDe = !!text(obj(o.description)?.de);
    const seiten = obj(o.links)?.homepage;
    return {
      id: text(o.id) ?? id,
      symbol: (text(o.symbol) ?? '').toUpperCase(),
      name: text(o.name) ?? id,
      bild: httpsLink(obj(o.image)?.large) ?? httpsLink(obj(o.image)?.small),
      rang: rang(o.market_cap_rank),
      preis: w(m?.current_price),
      aenderung24h: zahl(m?.price_change_percentage_24h),
      marktkapital: w(m?.market_cap),
      volumen24h: w(m?.total_volume),
      spark: null,
      beschreibung: besch ? besch.replace(/<[^>]*>/g, '') : null,
      beschreibungSprache: besch ? (istDe ? 'de' : 'en') : null,
      seite: Array.isArray(seiten) ? (seiten.map(httpsLink).find(Boolean) ?? null) : null,
      umlauf: zahl(m?.circulating_supply),
      gesamtmenge: zahl(m?.total_supply),
      hoechstmenge: zahl(m?.max_supply),
      ath: w(m?.ath),
    };
  }

  async verlauf(id: string, zeitraum: Zeitraum, waehrung: Waehrung): Promise<Chartreihe> {
    const p = new URLSearchParams({ vs_currency: waehrung, days: tage(zeitraum) });
    const o = obj(await hol('/coins/' + encodeURIComponent(id) + '/market_chart?' + p));
    const roh = o?.prices;
    let punkte: [number, number][] = Array.isArray(roh)
      ? roh.flatMap(p2 => {
          const a = Array.isArray(p2) ? zahl(p2[0]) : null;
          const b = Array.isArray(p2) ? zahl(p2[1]) : null;
          return a !== null && b !== null ? [[a, b] as [number, number]] : [];
        })
      : [];
    // Fuer "1 Stunde" gibt es keinen eigenen Zeitraum: days=1 liefert
    // 5-Minuten-Punkte, davon die letzte Stunde. Keine erfundene Dichte --
    // die Punkte sind echt, es sind nur weniger.
    if (zeitraum === '1h' && punkte.length > 1) {
      const bis = punkte[punkte.length - 1][0];
      punkte = punkte.filter(([t]) => t >= bis - 3_600_000);
    }
    return { zeitraum, punkte };
  }

  async suche(begriff: string): Promise<CoinKurz[]> {
    const q = begriff.trim();
    if (!q) return [];
    const o = obj(await hol('/search?query=' + encodeURIComponent(q)));
    const treffer = o?.coins;
    if (!Array.isArray(treffer)) return [];
    return treffer.flatMap(roh => {
      const c = obj(roh);
      const id = c && text(c.id);
      const symbol = c && text(c.symbol);
      const name = c && text(c.name);
      if (!c || !id || !symbol || !name) return [];
      // /search liefert bewusst keine Kurse. Die Liste holt sie nach --
      // hier eine 0 einzusetzen waere eine erfundene Zahl.
      return [{
        id, symbol: symbol.toUpperCase(), name,
        bild: httpsLink(c.large) ?? httpsLink(c.thumb),
        rang: rang(c.market_cap_rank),
        preis: null, aenderung24h: null, marktkapital: null, volumen24h: null, spark: null,
      } as CoinKurz];
    });
  }
}

export const coingecko = new CoinGecko();
