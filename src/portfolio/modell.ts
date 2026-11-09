/**
 * Portfolio -- Datenmodell.
 *
 * DIE WICHTIGSTE REGEL steht im Typ, nicht in einer Pruefung:
 *
 *   Eine Position ist ENTWEDER ein echter Bestand auf der Kette ODER
 *   eine von Hand eingetragene Zahl. Beides zugleich gibt es nicht.
 *
 * Eine On-Chain-Position traegt deshalb KEINE Menge: Die kommt immer vom
 * Knoten. Wer sie von Hand setzen koennte, koennte sein Guthaben
 * schoenrechnen -- und genau das soll eine Wallet nicht erlauben.
 *
 * YSR ist als manuelle Position gesperrt. Sonst stuende der echte Bestand
 * neben einer erfundenen Zahl und beides wuerde addiert.
 *
 * Mengen sind DEZIMALTEXT, nicht number. Wer 0,00000001 eintraegt, soll
 * genau das wiederfinden. Gerechnet wird in bigint-Einheiten.
 */

export const MENGE_STELLEN = 8;
const BASIS = 10n ** BigInt(MENGE_STELLEN);

export type PositionsQuelle = 'onchain' | 'manuell';

/** Bestand auf der YSKAR-Kette. Die Menge kommt vom Knoten, nie von Hand. */
export interface OnchainPosition {
  quelle: 'onchain';
  kette: 'yskar';
  symbol: 'YSR';
}

/** Von Hand eingetragene Position einer anderen Kryptowaehrung. */
export interface ManuellePosition {
  quelle: 'manuell';
  /** Eigene Kennung dieser Position -- es darf mehrere je Coin geben. */
  id: string;
  /** Kennung beim Marktdatenanbieter, fuer den Kurs. */
  coinId: string;
  symbol: string;
  name: string;
  /** Dezimaltext, bis zu 8 Nachkommastellen. */
  menge: string;
  /** Einstandspreis je Stueck als Dezimaltext. null = nicht angegeben. */
  einstandJeStueck: string | null;
  angelegt: number;
}

export type Position = OnchainPosition | ManuellePosition;

/** Symbole, die nicht von Hand eingetragen werden duerfen. */
export const GESPERRT = new Set(['YSR', 'YSKAR']);

export class PositionAbgelehnt extends Error {
  readonly grund: 'gesperrt' | 'menge' | 'einstand';
  constructor(grund: 'gesperrt' | 'menge' | 'einstand', text: string) {
    super(text);
    this.grund = grund;
  }
}

/* --------------------------------------------------------------- Mengen */

/**
 * Dezimaltext in Einheiten (8 Stellen). null, wenn der Text keine
 * brauchbare Menge ist.
 *
 * Bewusst ohne parseFloat: 0.1 + 0.2 ist in Fliesskomma nicht 0.3, und
 * bei Geld faellt so etwas irgendwann jemandem auf.
 */
export function mengeAusText(text: string): bigint | null {
  const s = text.trim().replace(',', '.');
  if (!/^\d{1,18}(\.\d{1,8})?$/.test(s)) return null;
  const [ganz, bruch = ''] = s.split('.');
  const voll = ganz + bruch.padEnd(MENGE_STELLEN, '0');
  try {
    const v = BigInt(voll);
    return v >= 0n ? v : null;
  } catch { return null; }
}

/** Einheiten zurueck in Dezimaltext, ohne Nullen am Ende. */
export function mengeText(einheiten: bigint): string {
  const ganz = einheiten / BASIS;
  const bruch = (einheiten % BASIS).toString().padStart(MENGE_STELLEN, '0').replace(/0+$/, '');
  return bruch ? `${ganz}.${bruch}` : String(ganz);
}

/** Einheiten als Zahl -- nur fuer die Bewertung, nie fuer die Ablage. */
export const mengeZahl = (einheiten: bigint): number => Number(einheiten) / Number(BASIS);

/* ------------------------------------------------------------- Anlegen */

let zaehler = 0;

/**
 * Eine manuelle Position anlegen. Wirft, wenn sie nicht zulaessig ist --
 * absichtlich, damit ein Fehler nicht als stille 0 durchrutscht.
 */
export function manuellAnlegen(e: {
  coinId: string; symbol: string; name: string;
  menge: string; einstandJeStueck?: string | null; jetzt?: number;
}): ManuellePosition {
  const symbol = e.symbol.trim().toUpperCase();
  if (GESPERRT.has(symbol)) {
    throw new PositionAbgelehnt('gesperrt',
      'YSR steht bereits als echter Bestand im Portfolio und kann nicht von Hand eingetragen werden.');
  }
  const menge = mengeAusText(e.menge);
  if (menge === null || menge === 0n) {
    throw new PositionAbgelehnt('menge', 'Die Menge muss eine Zahl groesser als 0 sein.');
  }
  let einstand: string | null = null;
  if (e.einstandJeStueck != null && e.einstandJeStueck.trim() !== '') {
    const p = mengeAusText(e.einstandJeStueck);
    if (p === null) throw new PositionAbgelehnt('einstand', 'Der Einstandspreis ist keine gueltige Zahl.');
    einstand = mengeText(p);
  }
  const jetzt = e.jetzt ?? Date.now();
  return {
    quelle: 'manuell',
    id: `p${jetzt.toString(36)}${(zaehler++).toString(36)}`,
    coinId: e.coinId.trim(),
    symbol,
    name: e.name.trim() || symbol,
    menge: mengeText(menge),
    einstandJeStueck: einstand,
    angelegt: jetzt,
  };
}

/** Die eine On-Chain-Position. Es gibt genau eine, und sie ist unveraenderlich. */
export const YSR_POSITION: OnchainPosition = { quelle: 'onchain', kette: 'yskar', symbol: 'YSR' };
