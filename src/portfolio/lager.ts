import { lager, useLager } from '../wallet/lager.ts';
import { mengeAusText, mengeText, GESPERRT, type ManuellePosition } from './modell.ts';
import { verlaufErgaenzen, type VerlaufPunkt } from './rechnen.ts';

/**
 * Portfolio -- Ablage auf dem Geraet.
 *
 * Kein Geheimnis: Hier stehen keine Schluessel, sondern was der Nutzer
 * selbst eingetragen hat. Es muss nur einen Neustart ueberleben.
 *
 * Beim Lesen wird JEDE Position noch einmal geprueft. Eine Datei auf dem
 * Geraet kann veraendert worden sein, und eine YSR-Position, die sich
 * nachtraeglich hineinschreiben liesse, wuerde den echten Bestand doppelt
 * zaehlen. Deshalb fliegt sie hier genauso raus wie beim Anlegen.
 */

export interface PortfolioStand {
  positionen: ManuellePosition[];
  verlauf: VerlaufPunkt[];
}

const VORGABE: PortfolioStand = { positionen: [], verlauf: [] };

function positionPruefen(roh: unknown): ManuellePosition | null {
  const r = (roh && typeof roh === 'object' ? roh : {}) as Partial<ManuellePosition>;
  const symbol = typeof r.symbol === 'string' ? r.symbol.trim().toUpperCase() : '';
  const coinId = typeof r.coinId === 'string' ? r.coinId.trim() : '';
  const menge = typeof r.menge === 'string' ? mengeAusText(r.menge) : null;
  if (!symbol || !coinId || menge === null || menge === 0n) return null;
  // Dieselbe Sperre wie beim Anlegen -- auch gegen eine veraenderte Ablage.
  if (GESPERRT.has(symbol)) return null;
  const einstand = typeof r.einstandJeStueck === 'string' ? mengeAusText(r.einstandJeStueck) : null;
  return {
    quelle: 'manuell',
    id: typeof r.id === 'string' && r.id ? r.id.slice(0, 40) : `p${Math.random().toString(36).slice(2, 10)}`,
    coinId: coinId.slice(0, 80),
    symbol: symbol.slice(0, 16),
    name: (typeof r.name === 'string' && r.name ? r.name : symbol).slice(0, 60),
    menge: mengeText(menge),
    einstandJeStueck: einstand === null ? null : mengeText(einstand),
    angelegt: typeof r.angelegt === 'number' && Number.isFinite(r.angelegt) ? r.angelegt : Date.now(),
  };
}

export const portfolioLager = lager<PortfolioStand>('yskar.portfolio.v1', VORGABE, roh => {
  const r = (roh && typeof roh === 'object' ? roh : {}) as Partial<PortfolioStand>;
  const positionen = Array.isArray(r.positionen)
    ? r.positionen.map(positionPruefen).filter((p): p is ManuellePosition => p !== null).slice(0, 200)
    : [];
  const verlauf = Array.isArray(r.verlauf)
    ? r.verlauf.filter((p): p is VerlaufPunkt =>
        !!p && typeof p === 'object'
        && typeof (p as VerlaufPunkt).tag === 'string'
        && /^\d{4}-\d{2}-\d{2}$/.test((p as VerlaufPunkt).tag)
        && typeof (p as VerlaufPunkt).cent === 'number'
        && Number.isFinite((p as VerlaufPunkt).cent)).slice(-400)
    : [];
  return { positionen, verlauf };
});

export const usePortfolio = (): PortfolioStand => useLager(portfolioLager);

export function positionHinzufuegen(p: ManuellePosition): void {
  portfolioLager.aendern(alt => ({ ...alt, positionen: [...alt.positionen, p] }));
}

export function positionEntfernen(id: string): void {
  portfolioLager.aendern(alt => ({ ...alt, positionen: alt.positionen.filter(p => p.id !== id) }));
}

export function positionAendern(id: string, teil: Partial<ManuellePosition>): void {
  portfolioLager.aendern(alt => ({
    ...alt,
    positionen: alt.positionen.map(p => (p.id === id ? { ...p, ...teil, quelle: 'manuell', id } : p)),
  }));
}

/** Den heutigen Wert festhalten. Je Tag genau einer. */
export function verlaufMerken(cent: number, zeit = Date.now()): void {
  portfolioLager.aendern(alt => ({ ...alt, verlauf: verlaufErgaenzen(alt.verlauf, cent, 400, zeit) }));
}
