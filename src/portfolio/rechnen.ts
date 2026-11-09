import { mengeAusText, mengeZahl, type ManuellePosition } from './modell.ts';

/**
 * Portfolio -- die Bewertung.
 *
 * DIE ENTSCHEIDENDE REGEL: Der YSR-Bestand geht NICHT in die Fiat-Summe
 * ein. YSR wird an keiner Boerse gehandelt; es gibt keinen Kurs. Eine
 * Summe aus "1.234 YSR + 1.842 EUR" waere entweder falsch oder erfunden.
 *
 * Deshalb liefert diese Rechnung immer ZWEI Zahlen nebeneinander:
 *   - den YSR-Bestand in YSR
 *   - den Fiat-Wert der bepreisten Positionen
 * und nie eine Gesamtsumme ueber beides.
 *
 * Positionen ohne Kurs (Anbieter kennt den Coin nicht, Sperre nach 429)
 * zaehlen NICHT als 0 mit, sondern werden getrennt gezaehlt und
 * ausgewiesen. Sonst saehe ein Portfolio bei einem Netzausfall aus, als
 * waere es ploetzlich weniger wert.
 */

/** Kurse je Coin-Kennung, wie der Anbieter sie liefert. */
export type Kurse = Record<string, number | null | undefined>;

export interface BewertetePosition {
  position: ManuellePosition;
  /** Aktueller Kurs je Stueck. null = kein Kurs verfuegbar. */
  kurs: number | null;
  /** Wert in Cent. null, wenn kein Kurs da ist. */
  wertCent: number | null;
  /** Einstand gesamt in Cent. null, wenn kein Einstand angegeben ist. */
  einstandCent: number | null;
  /** Gewinn/Verlust in Cent. null, wenn Kurs oder Einstand fehlt. */
  gvCent: number | null;
  /** Gewinn/Verlust in Prozent. null wie oben, und nie bei Einstand 0. */
  gvProzent: number | null;
  /** Anteil am bepreisten Gesamtwert, 0 bis 1. null ohne Kurs. */
  anteil: number | null;
}

export interface Bewertung {
  positionen: BewertetePosition[];
  /** Summe der bepreisten Positionen in Cent. */
  summeCent: number;
  /** Summe der Einstaende, nur wo beides bekannt ist. */
  einstandCent: number;
  /** Gewinn/Verlust ueber die Positionen, bei denen beides bekannt ist. */
  gvCent: number;
  gvProzent: number | null;
  /** Wie viele Positionen keinen Kurs haben -- wird ausgewiesen, nicht verschwiegen. */
  ohneKurs: number;
  /** Der echte Bestand auf der Kette, in Einheiten. Geht NIE in summeCent ein. */
  ysrEinheiten: bigint | null;
}

/** Wert einer Menge zum Kurs, in Cent. Gerundet, damit Summen nicht driften. */
export function wertCent(menge: bigint, kurs: number | null): number | null {
  if (kurs === null || !Number.isFinite(kurs) || kurs < 0) return null;
  return Math.round(mengeZahl(menge) * kurs * 100);
}

export function bewerten(
  positionen: ManuellePosition[], kurse: Kurse, ysrEinheiten: bigint | null,
): Bewertung {
  const bewertet: BewertetePosition[] = [];
  let summe = 0;
  let einstandSumme = 0;
  let gvSumme = 0;
  let ohneKurs = 0;

  for (const p of positionen) {
    const menge = mengeAusText(p.menge) ?? 0n;
    const roh = kurse[p.coinId];
    const kurs = typeof roh === 'number' && Number.isFinite(roh) ? roh : null;
    const wert = wertCent(menge, kurs);
    const einstandStueck = p.einstandJeStueck === null ? null : mengeAusText(p.einstandJeStueck);
    const einstand = einstandStueck === null ? null
      : Math.round(mengeZahl(menge) * mengeZahl(einstandStueck) * 100);

    const gv = wert !== null && einstand !== null ? wert - einstand : null;
    const gvPz = gv !== null && einstand !== null && einstand !== 0
      ? (gv / einstand) * 100 : null;

    if (wert === null) ohneKurs++; else summe += wert;
    if (wert !== null && einstand !== null) { einstandSumme += einstand; gvSumme += gv!; }

    bewertet.push({ position: p, kurs, wertCent: wert, einstandCent: einstand,
                    gvCent: gv, gvProzent: gvPz, anteil: null });
  }

  // Anteile erst, wenn die Summe feststeht.
  for (const b of bewertet) {
    b.anteil = b.wertCent !== null && summe > 0 ? b.wertCent / summe : null;
  }
  // Groesste Position zuerst; ohne Kurs ans Ende, nie als 0 einsortiert.
  bewertet.sort((a, b) => {
    if (a.wertCent === null && b.wertCent === null) return 0;
    if (a.wertCent === null) return 1;
    if (b.wertCent === null) return -1;
    return b.wertCent - a.wertCent;
  });

  return {
    positionen: bewertet,
    summeCent: summe,
    einstandCent: einstandSumme,
    gvCent: gvSumme,
    gvProzent: einstandSumme !== 0 ? (gvSumme / einstandSumme) * 100 : null,
    ohneKurs,
    ysrEinheiten,
  };
}

/* ------------------------------------------------------- Verlauf */

/**
 * Taegliche Punkte des Portfoliowerts.
 *
 * WAS HIER NICHT PASSIERT: Die Vergangenheit wird nicht rekonstruiert.
 * Dazu braeuchte es historische Kurse FUER JEDE Position ab ihrem
 * Kaufdatum -- und was dabei herauskaeme, waere eine Hochrechnung, keine
 * Messung. Stattdessen wird ab heute ein Wert je Tag festgehalten. Der
 * Verlauf beginnt also, wenn der Nutzer anfaengt, und ist dafuer echt.
 */
export interface VerlaufPunkt { tag: string; cent: number }

/** Kalendertag des Geraets als "2026-11-09". */
export function tagesSchluessel(zeit = Date.now()): string {
  const d = new Date(zeit);
  const z = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${z(d.getMonth() + 1)}-${z(d.getDate())}`;
}

/**
 * Den heutigen Punkt setzen oder ersetzen. Je Tag genau einer -- der
 * letzte des Tages gewinnt, weil er dem Tagesschluss am naechsten ist.
 */
export function verlaufErgaenzen(
  verlauf: VerlaufPunkt[], cent: number, hoechstens = 400, zeit = Date.now(),
): VerlaufPunkt[] {
  const tag = tagesSchluessel(zeit);
  const ohneHeute = verlauf.filter(p => p.tag !== tag);
  const neu = [...ohneHeute, { tag, cent }];
  neu.sort((a, b) => (a.tag < b.tag ? -1 : a.tag > b.tag ? 1 : 0));
  return neu.length > hoechstens ? neu.slice(neu.length - hoechstens) : neu;
}

/** Veraenderung gegenueber dem Vortag, in Cent. null ohne Vortag. */
export function tagesVeraenderung(verlauf: VerlaufPunkt[]): number | null {
  if (verlauf.length < 2) return null;
  return verlauf[verlauf.length - 1].cent - verlauf[verlauf.length - 2].cent;
}
