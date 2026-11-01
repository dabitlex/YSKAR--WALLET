/**
 * YSKAR-Formensprache (Designsystem 4.2, Abschnitt "YSKAR-Formensprache").
 *
 * Alles hier kommt aus einer Form: dem Band des Logos, das von einer Spitze
 * zur vollen Staerke waechst. Die Transaktionszeichen sind Polygone aus
 * genau dieser Verjuengung, das Y ist das Logo selbst (src/bilder/zeichen.svg).
 *
 * Reine Darstellung: keine Daten, kein Netz, kein Zustand.
 */

/* Die drei Teile des Logos, unveraendert aus src/bilder/zeichen.svg. */
const STAMM = 'M688.0 969.2 C688.0 850.6 687.5 845.0 673.8 817.5 C659.4 788.7 647.4 777.8 562.0 716.5 C479.0 656.9 470.2 650.8 468.8 651.6 C467.2 652.6 467.8 790.6 469.5 803.3 C475.2 846.9 493.4 880.1 528.5 910.7 C533.5 915.0 545.6 925.8 555.5 934.6 C565.4 943.4 584.5 960.2 598.0 972.0 C641.5 1010.0 659.5 1027.9 677.2 1050.8 C681.6 1056.4 685.8 1061.0 686.6 1061.0 C687.8 1061.0 688.0 1048.0 688.0 969.2 Z';
const BAND_LINKS = 'M727.9 847.0 C797.3 765.8 810.3 667.4 760.3 600.9 C746.5 582.5 735.3 573.0 680.5 532.7 C669.5 524.7 644.3 506.1 624.5 491.4 C604.7 476.8 584.7 462.0 580.0 458.6 C482.6 387.3 367.5 300.7 338.0 276.6 C309.8 253.6 277.6 223.1 254.6 197.5 C249.1 191.4 244.2 186.5 243.6 186.5 C242.0 186.5 241.5 301.3 243.0 317.9 C247.5 367.3 262.8 415.1 285.7 451.2 C311.5 491.7 341.0 518.7 416.5 571.0 C447.3 592.3 445.7 591.2 495.5 626.5 C615.1 711.4 644.9 733.8 665.5 754.4 C695.2 784.2 712.4 815.2 716.1 845.5 C717.9 859.9 717.0 859.8 727.9 847.0 Z';
const BAND_RECHTS = 'M816.3 594.8 C829.6 582.7 835.3 577.9 854.0 562.9 C934.5 498.6 960.5 472.6 982.2 435.1 C997.2 409.2 1007.7 377.3 1010.9 347.8 C1012.3 335.3 1012.4 254.3 1011.0 253.5 C1010.5 253.2 1003.6 258.2 995.8 264.6 C966.5 288.7 916.7 324.5 833.0 381.5 C786.7 413.1 688.1 481.9 684.2 485.3 C680.9 488.4 681.3 489.1 690.8 496.0 C772.8 556.1 786.1 568.4 800.6 597.2 C804.9 605.7 804.1 605.8 816.3 594.8 Z';
const LOGO_ANSICHT = '187.5 184 879 879';

/** Das Y als Einheitszeichen und Marke. Unter 28 px nur als ruhiges Zeichen, nie als Statusanzeige. */
export function YMarke({ g = 14, farbe = 'currentColor' }: { g?: number; farbe?: string }) {
  return (
    <svg width={g} height={g} viewBox={LOGO_ANSICHT} aria-hidden="true" focusable="false">
      <path fill={farbe} d={`${BAND_LINKS} ${BAND_RECHTS} ${STAMM}`} />
    </svg>
  );
}

export type Teilstand = 'erledigt' | 'wartet' | 'offen' | 'fehler';
const TEILFARBE: Record<Teilstand, string> = {
  erledigt: 'var(--blau-hell)', wartet: 'var(--bern)', offen: 'rgba(238,242,248,.12)', fehler: 'var(--rot)',
};

/**
 * Das Y in drei Farben (ab 28 px): Band 1 = vom Knoten angenommen,
 * Band 2 = in einen Block aufgenommen, Stamm = weitere Bestaetigungen.
 */
export function YStatus({ g = 76, stand, label }: { g?: number; stand: [Teilstand, Teilstand, Teilstand]; label: string }) {
  return (
    <svg width={g} height={g} viewBox={LOGO_ANSICHT} role="img" aria-label={label}>
      <path fill={TEILFARBE[stand[0]]} d={BAND_LINKS} />
      <path fill={TEILFARBE[stand[1]]} d={BAND_RECHTS} />
      <path fill={TEILFARBE[stand[2]]} d={STAMM} />
    </svg>
  );
}

/** Derselbe Status klein: drei verjuengte Baender (unter 28 px ist das Y nicht lesbar). */
export function YBaender({ stand, label }: { stand: [Teilstand, Teilstand, Teilstand]; label: string }) {
  return (
    <span className="y3" role="img" aria-label={label}>
      {stand.map((s, i) => <i key={i} className={s} />)}
    </span>
  );
}

/* ---------------------------------------------------- Transaktionszeichen */

type Punkt = [number, number];

/** Ein Band von der Spitze (duenn) zum Ende (volle Staerke `breite`), im 24er-Raster. */
function band(spitze: Punkt, ende: Punkt, breite: number): string {
  const [sx, sy] = spitze, [ex, ey] = ende;
  const dx = ex - sx, dy = ey - sy, l = Math.hypot(dx, dy);
  const px = (-dy / l) * breite / 2, py = (dx / l) * breite / 2;
  const r = (n: number) => Math.round(n * 10) / 10;
  return `${r(sx)},${r(sy)} ${r(ex + px)},${r(ey + py)} ${r(ex - px)},${r(ey - py)}`;
}

/** Pfeil mit verjuengtem Schaft: Schaft von `fuss` zur Spitze, zwei kurze Baender bilden die Spitze. */
function pfeil(spitze: Punkt, fuss: Punkt, arm1: Punkt, arm2: Punkt): string[] {
  const ende: Punkt = [spitze[0] - (spitze[0] - fuss[0]) * 0.12, spitze[1] - (spitze[1] - fuss[1]) * 0.12];
  return [band(fuss, ende, 3.8), band(arm1, spitze, 3.4), band(arm2, spitze, 3.4)];
}

const FORMEN = {
  aus: pfeil([18.5, 5.5], [5, 19], [9.5, 5.5], [18.5, 14.5]),
  ein: pfeil([5.5, 18.5], [19, 5], [14.5, 18.5], [5.5, 9.5]),
  /* Drei Baender laufen zusammen: ein geteilter Block. */
  pool: [band([3.5, 3.5], [11, 12], 4.2), band([20.5, 3.5], [13, 12], 4.2), band([12, 21.5], [12, 13.6], 4.2)],
  /* Drei parallele Baender: ein Anteil aus einer Zahlung an viele. */
  sammel: [band([13, 3], [4, 12], 3.6), band([17, 7], [8, 16], 3.6), band([21, 11], [12, 20], 3.6)],
} as const;

export type ZahlArt = 'aus' | 'ein' | 'pool' | 'belohnung' | 'sammel';

/** Das Zeichen einer Zahlungsart, 20 px, in der Farbe des umgebenden Textes. */
export function ZahlZeichen({ art, g = 20 }: { art: ZahlArt; g?: number }) {
  if (art === 'belohnung') {
    // Blockbelohnung: der Block selbst (Sechseck mit Kanten), gefunden von dieser Wallet.
    return (
      <svg width={g} height={g} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={1.6} strokeLinejoin="round" aria-hidden="true">
        <path d="M12 2.8l8 4.6v9.2l-8 4.6-8-4.6V7.4z" /><path d="M4.4 7.6L12 12l7.6-4.4M12 12v8.8" />
      </svg>
    );
  }
  return (
    <svg width={g} height={g} viewBox="0 0 24 24" aria-hidden="true">
      {FORMEN[art].map(p => <polygon key={p} points={p} fill="currentColor" />)}
    </svg>
  );
}
