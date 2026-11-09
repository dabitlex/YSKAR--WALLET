/**
 * Marktdaten -- Typen.
 *
 * Diese Schicht ist vom YSKAR-Netz vollstaendig getrennt. Sie kennt keinen
 * Tresor, keine Woerter, keine Adresse und keine Signierfunktion, und sie
 * darf sie nie kennenlernen: tests/grenzen.test.ts prueft das.
 *
 * JEDER WERT TRAEGT SEINE HERKUNFT. Ein Preis ohne Quelle und ohne Alter
 * wird nicht angezeigt. Das steckt im Typ und nicht in einer Absprache,
 * damit es niemand vergessen kann.
 */

export type Waehrung = 'eur' | 'usd';

/** Woher ein Wert stammt. Steht in der Oberflaeche an jedem Kurs. */
export type Quelle =
  | 'coingecko'   // externer Marktdatenanbieter
  | 'yskar'       // der YSKAR-Knoten selbst
  | 'eigene';     // vom Nutzer eingetragen (manuelle Position)

export interface MitHerkunft<T> {
  wert: T;
  quelle: Quelle;
  /** Unix-Millisekunden des Abrufs. */
  standVon: number;
  /** Ueber der Frische-Schwelle: anzeigen, aber als alt kennzeichnen. */
  veraltet: boolean;
}

/** Eine Kryptowaehrung in der Liste. */
export interface CoinKurz {
  id: string;
  symbol: string;
  name: string;
  bild: string | null;
  rang: number | null;
  preis: number | null;
  aenderung24h: number | null;
  marktkapital: number | null;
  volumen24h: number | null;
  /** Punkte fuer den Mini-Chart, 7 Tage. */
  spark: number[] | null;
  /**
   * YSKAR: an keiner Boerse gehandelt. Preis, Marktkapital und Volumen
   * sind null und bleiben es -- die Oberflaeche zeigt dort einen Hinweis
   * statt einer Zahl.
   */
  ohneKurs?: boolean;
}

export interface CoinDetail extends CoinKurz {
  beschreibung: string | null;
  /** Sprache der Beschreibung. CoinGecko fuehrt nur Englisch. */
  beschreibungSprache: 'en' | 'de' | null;
  seite: string | null;
  umlauf: number | null;
  gesamtmenge: number | null;
  hoechstmenge: number | null;
  ath: number | null;
}

export interface Marktueberblick {
  marktkapital: number | null;
  marktkapitalAenderung24h: number | null;
  volumen24h: number | null;
  btcDominanz: number | null;
  ethDominanz: number | null;
  anzahlCoins: number | null;
  anzahlBoersen: number | null;
}

export type Zeitraum = '1h' | '24h' | '7d' | '30d' | '90d' | '1j' | 'max';

export const ZEITRAEUME: Zeitraum[] = ['1h', '24h', '7d', '30d', '90d', '1j', 'max'];

/** Punkte als [Unix-Millisekunden, Preis]. */
export interface Chartreihe {
  zeitraum: Zeitraum;
  punkte: [number, number][];
}

export type Sortierung = 'rang' | 'gewinner' | 'verlierer' | 'marktkapital' | 'volumen' | 'favoriten';

export interface ListeOptionen {
  waehrung: Waehrung;
  seite?: number;
  proSeite?: number;
}

/**
 * Was ein Marktdatenanbieter koennen muss. Ein Wechsel kostet eine Datei:
 * Die Oberflaeche kennt nur dieses Interface, nie CoinGecko.
 */
export interface MarktAnbieter {
  readonly name: string;
  ueberblick(waehrung: Waehrung): Promise<Marktueberblick>;
  liste(o: ListeOptionen): Promise<CoinKurz[]>;
  detail(id: string, waehrung: Waehrung): Promise<CoinDetail>;
  verlauf(id: string, zeitraum: Zeitraum, waehrung: Waehrung): Promise<Chartreihe>;
  suche(text: string): Promise<CoinKurz[]>;
}

/** Fehler der Marktschicht. Wird nie in der Wallet sichtbar. */
export class MarktFehler extends Error {
  readonly art: 'offline' | 'gebremst' | 'antwort';
  readonly status: number;
  /** Bei 'gebremst': Sekunden bis zum naechsten Versuch. */
  readonly wartenSek: number;
  constructor(art: 'offline' | 'gebremst' | 'antwort', status = 0, wartenSek = 0, text = '') {
    super(text || art);
    this.art = art; this.status = status; this.wartenSek = wartenSek;
  }
}
