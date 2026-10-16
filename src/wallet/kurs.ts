import { useEffect, useState } from 'react';
import { betragZahl } from './betrag.ts';

/*
  Der Kurs von YSR in Euro und Dollar, und sein Verlauf.

  Solange YSR an keiner Boerse gelistet ist, liefert dieses Modul DEMO-Werte,
  die in der App auch so gekennzeichnet sind. Spaeter wird nur `quelle`
  ausgetauscht (zum Beispiel gegen eine Abfrage beim Spiegel oder Explorer);
  Start, Kurs-Seite und Einstellungen bleiben, wie sie sind.
*/

export type Waehrung = 'eur' | 'usd';
export type Zeitraum = '1t' | '1w' | '1m' | '1j' | 'alle';
export const ZEITRAEUME: Zeitraum[] = ['1t', '1w', '1m', '1j', 'alle'];

export interface Kurs {
  /** Preis einer YSR je Waehrung. */
  preis: Record<Waehrung, number>;
  /** Veraenderung in den letzten 24 Stunden, in Prozent (z. B. 4.2). */
  aenderung24h: number;
  /** Zeitpunkt des Kurses, Sekunden. */
  stand: number;
  /** true: erfundene Zahlen, kein Markt dahinter. */
  demo: boolean;
}

export interface Kursquelle {
  jetzt(): Promise<Kurs>;
  /** Verlauf fuer den Zeitraum, aelteste zuerst; der letzte Wert ist der aktuelle Preis. */
  verlauf(zeitraum: Zeitraum, waehrung: Waehrung): Promise<number[]>;
}

/* ------------------------------------------------------------------ Demo */

const DEMO_EUR = 46.78;
/** Demo-Wechselkurs, nur fuer die Dollar-Ansicht. */
const DEMO_USD_JE_EUR = 1.08;

/** Fester Zufall: derselbe Verlauf bei jedem Oeffnen. */
function zufall(seed: number): () => number {
  let s = seed >>> 0;
  return () => { s = (s * 1664525 + 1013904223) >>> 0; return s / 4294967296; };
}

/** Ein Zufallsweg mit `n` Punkten, der genau bei `ende` ankommt. */
function weg(n: number, ende: number, seed: number, schwankung: number): number[] {
  const z = zufall(seed);
  const w: number[] = [];
  let v = ende * 0.82;
  for (let i = 0; i < n; i++) { v *= 1 + (z() - 0.48) * schwankung; w.push(v); }
  const f = ende / w[w.length - 1];
  return w.map(x => Math.round(x * f * 100) / 100);
}

const demoQuelle: Kursquelle = {
  async jetzt() {
    return { preis: { eur: DEMO_EUR, usd: Math.round(DEMO_EUR * DEMO_USD_JE_EUR * 100) / 100 }, aenderung24h: 4.2, stand: Math.floor(Date.now() / 1000), demo: true };
  },
  async verlauf(zeitraum, waehrung) {
    const ende = (await this.jetzt()).preis[waehrung];
    const form: Record<Zeitraum, [number, number, number]> = { '1t': [48, 11, 0.012], '1w': [56, 7, 0.03], '1m': [60, 31, 0.05], '1j': [72, 365, 0.09], alle: [80, 999, 0.12] };
    const [n, seed, schwankung] = form[zeitraum];
    return weg(n, ende, seed, schwankung);
  },
};

/** Die Quelle, die die App benutzt. Hier spaeter die echte eintragen. */
export const quelle: Kursquelle = demoQuelle;

/* ---------------------------------------------------------------- Anzeige */

export const WAEHRUNG_ZEICHEN: Record<Waehrung, string> = { eur: '€', usd: '$' };
export const WAEHRUNG_CODE: Record<Waehrung, string> = { eur: 'EUR', usd: 'USD' };

/** Einen Betrag in Fiat formatieren: "60.088,91 €". */
export function fiatText(betrag: number, waehrung: Waehrung, locale: string): string {
  return new Intl.NumberFormat(locale, { style: 'currency', currency: WAEHRUNG_CODE[waehrung], maximumFractionDigits: 2 }).format(betrag);
}

/** Der Wert von YSR-Einheiten (8 Nachkommastellen) in Fiat. */
export function wert(einheiten: bigint | string | number | null | undefined, kurs: Kurs | null, waehrung: Waehrung): number | null {
  if (!kurs) return null;
  return betragZahl(einheiten) * kurs.preis[waehrung];
}

/** Der aktuelle Kurs, einmal geladen und dann alle fuenf Minuten erneuert. */
export function useKurs(): Kurs | null {
  const [kurs, setKurs] = useState<Kurs | null>(gemerkt);
  useEffect(() => {
    let lebt = true;
    const laden = () => quelle.jetzt().then(k => { gemerkt = k; if (lebt) setKurs(k); }).catch(() => {});
    laden();
    const u = setInterval(laden, 5 * 60_000);
    return () => { lebt = false; clearInterval(u); };
  }, []);
  return kurs;
}
let gemerkt: Kurs | null = null;
