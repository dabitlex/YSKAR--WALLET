import { useCallback, useEffect, useRef, useState } from 'react';
import { coingecko } from './coingecko.ts';
import { holen, lesenRoh, sperrzeit } from './cache.ts';
import {
  MarktFehler, type Chartreihe, type CoinDetail, type CoinKurz, type MitHerkunft,
  type Marktueberblick, type Waehrung, type Zeitraum,
} from './typen.ts';

/**
 * Marktdaten fuer die Oberflaeche.
 *
 * Das Verhalten ist ueberall gleich und bewusst so gewaehlt, dass die
 * Oberflaeche NIE auf das Netz wartet, wenn sie etwas zeigen kann:
 *
 *   1. Was im Cache liegt, erscheint sofort -- mit seinem Alter.
 *   2. Ist es zu alt, wird im Hintergrund nachgeladen und ersetzt.
 *   3. Scheitert das, bleibt der alte Wert stehen und wird als alt
 *      gekennzeichnet. Kein Sprung auf leer, kein Platzhalter.
 *
 * Frische-Schwellen nach Datenart, nicht geraten:
 *   Kurse und Liste    60 s   -- soll leben, aber nicht flackern
 *   Marktueberblick     5 min -- globale Kennzahlen bewegen sich langsam
 *   Stammdaten         24 h   -- Beschreibung und Links aendern sich kaum
 *   Verlauf            je Zeitraum (1 h -> 2 min, 1 Jahr -> 6 h)
 */

export const FRISCHE = {
  liste: 60_000,
  ueberblick: 300_000,
  stammdaten: 86_400_000,
  suche: 600_000,
} as const;

export function frischeFuer(z: Zeitraum): number {
  switch (z) {
    case '1h': return 120_000;
    case '24h': return 300_000;
    case '7d': return 1_800_000;
    case '30d': case '90d': return 3_600_000;
    default: return 21_600_000;
  }
}

export interface MarktZustand<T> {
  daten: MitHerkunft<T> | null;
  laedt: boolean;
  fehler: MarktFehler | null;
  /** Sekunden, die die Sperre nach einem 429 noch laeuft. 0 = frei. */
  gebremst: number;
  neuLaden(): void;
}

function useMarktDaten<T>(
  schluessel: string | null, laden: () => Promise<T>, frischeMs: number,
): MarktZustand<T> {
  const [daten, setDaten] = useState<MitHerkunft<T> | null>(null);
  const [laedt, setLaedt] = useState(false);
  const [fehler, setFehler] = useState<MarktFehler | null>(null);
  const [runde, setRunde] = useState(0);
  // laden aendert sich bei jedem Rendern; die Abhaengigkeit ist der Schluessel.
  const ladenRef = useRef(laden);
  ladenRef.current = laden;

  useEffect(() => {
    if (!schluessel) { setDaten(null); setLaedt(false); return; }
    let weg = false;
    setLaedt(true);
    setFehler(null);
    holen<T>(schluessel, () => ladenRef.current(), { frischeMs })
      .then(r => { if (!weg) { setDaten(r); setLaedt(false); } })
      .catch((e: unknown) => {
        if (weg) return;
        setFehler(e instanceof MarktFehler ? e : new MarktFehler('antwort', 0, 0, String(e)));
        setLaedt(false);
      });
    return () => { weg = true; };
  }, [schluessel, frischeMs, runde]);

  const neuLaden = useCallback(() => setRunde(r => r + 1), []);
  return { daten, laedt, fehler, gebremst: sperrzeit(), neuLaden };
}

export function useUeberblick(w: Waehrung): MarktZustand<Marktueberblick> {
  return useMarktDaten(`ueberblick:${w}`, () => coingecko.ueberblick(w), FRISCHE.ueberblick);
}

export function useListe(w: Waehrung, seite = 1): MarktZustand<CoinKurz[]> {
  return useMarktDaten(`liste:${w}:${seite}`, () => coingecko.liste({ waehrung: w, seite, proSeite: 100 }), FRISCHE.liste);
}

export function useCoinDetail(id: string | null, w: Waehrung): MarktZustand<CoinDetail> {
  return useMarktDaten(id ? `detail:${id}:${w}` : null, () => coingecko.detail(id!, w), FRISCHE.stammdaten);
}

/**
 * Die Eckdaten eines Coins aus der bereits geholten Liste.
 *
 * Warum es das gibt: Wer in der Liste auf einen Coin tippt, hat Name,
 * Symbol, Bild, Rang und Kurs schon vor sich. Laeuft gerade eine Sperre
 * nach einem 429, zeigte die Detailseite trotzdem "nicht verfuegbar" --
 * obwohl die Daten im Cache liegen. Das war in der Praxis sofort zu
 * sehen und ist genau der Fall, fuer den der Cache da ist.
 *
 * Gelesen wird nur, was ohnehin gespeichert ist; es entsteht kein
 * Netzaufruf.
 */
export function coinAusListe(id: string, w: Waehrung): CoinKurz | null {
  for (let seite = 1; seite <= 3; seite++) {
    const e = lesenRoh<CoinKurz[]>(`liste:${w}:${seite}`);
    const treffer = e?.wert?.find(c => c.id === id);
    if (treffer) return treffer;
  }
  return null;
}

export function useVerlauf(id: string | null, z: Zeitraum, w: Waehrung): MarktZustand<Chartreihe> {
  return useMarktDaten(id ? `verlauf:${id}:${z}:${w}` : null, () => coingecko.verlauf(id!, z, w), frischeFuer(z));
}

/**
 * Suche. Wartet 350 ms nach dem letzten Tastendruck, sonst entstuende je
 * Buchstabe eine Anfrage -- und nach fuenf Buchstaben waere das Kontingent
 * weg (gemessen: jede zweite Anfrage wird abgelehnt).
 */
export function useSuche(text: string, verzoegerung = 350): MarktZustand<CoinKurz[]> {
  const [ruhig, setRuhig] = useState(text);
  useEffect(() => {
    const uhr = setTimeout(() => setRuhig(text), verzoegerung);
    return () => clearTimeout(uhr);
  }, [text, verzoegerung]);
  const q = ruhig.trim();
  return useMarktDaten(q.length >= 2 ? `suche:${q.toLowerCase()}` : null, () => coingecko.suche(q), FRISCHE.suche);
}
