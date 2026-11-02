import { useId, type CSSProperties, type Ref } from 'react';
import { STAMM, BAND_LINKS, BAND_RECHTS, LOGO_ANSICHT, type BandZustand } from './zeichenform.ts';

export { baenderFuerPin, type BandZustand } from './zeichenform.ts';

/**
 * YSKAR-Formensprache (Designsystem 4.2, Abschnitt "YSKAR-Formensprache").
 *
 * Alles hier kommt aus einer Form: dem Band des Logos, das von einer Spitze
 * zur vollen Staerke waechst. Die Transaktionszeichen sind Polygone aus
 * genau dieser Verjuengung, das Y ist das Logo selbst (src/bilder/zeichen.svg).
 *
 * Reine Darstellung: keine Daten, kein Netz, kein Zustand.
 */

/* Die drei Teile des Logos: ui/zeichenform.ts (unveraendert aus src/bilder/zeichen.svg). */

/** Das Y als Einheitszeichen und Marke. Unter 28 px nur als ruhiges Zeichen, nie als Statusanzeige. */
export function YMarke({ g = 14, farbe = 'currentColor' }: { g?: number; farbe?: string }) {
  return (
    <svg width={g} height={g} viewBox={LOGO_ANSICHT} aria-hidden="true" focusable="false">
      <path fill={farbe} d={`${BAND_LINKS} ${BAND_RECHTS} ${STAMM}`} />
    </svg>
  );
}

/* ------------------------------------------------- Leuchtendes Zeichen */

const LEUCHT_BAENDER = [BAND_LINKS, BAND_RECHTS, STAMM] as const;

/**
 * Das grosse, leuchtende Zeichen (Startbildschirm, Sperre). Jedes Band hat
 * eine ruhende Grundflaeche, darueber Markenfarbe und Fehlerfarbe; ein Wechsel
 * blendet nur die Deckkraft (siehe .leucht in stil.css). Die Baender werden
 * hoechstens als Ganzes verschoben (Zusammensetzen beim Start), nie verformt.
 */
export function LeuchtZeichen({ baender, klasse, stil, bandRefs, glanzRef }: {
  baender: BandZustand[]; klasse?: string; stil?: CSSProperties;
  /** Startbildschirm: die drei Baender einzeln bewegen. */
  bandRefs?: Ref<SVGGElement>[];
  /** Startbildschirm: eine Lichtkante, die einmal ueber das Zeichen laeuft. */
  glanzRef?: Ref<SVGRectElement>;
}) {
  const id = useId().replace(/[^a-zA-Z0-9]/g, '');
  return (
    <svg className={`leucht ${klasse ?? ''}`} style={stil} viewBox={LOGO_ANSICHT} aria-hidden="true" focusable="false">
      <defs>
        <linearGradient id={`${id}l`} x1="0" y1="0" x2="1" y2="1"><stop offset="0" stopColor="#9BE8FF" /><stop offset=".55" stopColor="#63D8FF" /><stop offset="1" stopColor="#2F7BFF" /></linearGradient>
        <linearGradient id={`${id}r`} x1="1" y1="0" x2="0" y2="1"><stop offset="0" stopColor="#63D8FF" /><stop offset="1" stopColor="#1B4FD1" /></linearGradient>
        <linearGradient id={`${id}s`} x1="0" y1="0" x2="0" y2="1"><stop offset="0" stopColor="#2F7BFF" /><stop offset="1" stopColor="#1B4FD1" /></linearGradient>
        <linearGradient id={`${id}f`} x1="0" y1="0" x2="0" y2="1"><stop offset="0" stopColor="#FFB0A6" /><stop offset="1" stopColor="#C8372F" /></linearGradient>
        {glanzRef && <>
          <linearGradient id={`${id}g`} x1="0" y1="0" x2="1" y2="0"><stop offset="0" stopColor="#fff" stopOpacity="0" /><stop offset=".5" stopColor="#fff" stopOpacity=".5" /><stop offset="1" stopColor="#fff" stopOpacity="0" /></linearGradient>
          <clipPath id={`${id}c`}><path d={LEUCHT_BAENDER.join(' ')} /></clipPath>
        </>}
      </defs>
      {LEUCHT_BAENDER.map((d, k) => (
        <g key={k} ref={bandRefs?.[k]} className={`band ${baender[k]}`}>
          <path className="grund" d={d} />
          <path className="licht" d={d} fill={`url(#${id}${'lrs'[k]})`} />
          <path className="fehler" d={d} fill={`url(#${id}f)`} />
        </g>
      ))}
      {glanzRef && (
        <g clipPath={`url(#${id}c)`}>
          <g transform="rotate(25 627 623)">
            <rect ref={glanzRef} x="-340" y="-200" width="320" height="1700" fill={`url(#${id}g)`} opacity="0" />
          </g>
        </g>
      )}
    </svg>
  );
}

/* ------------------------------------------------------------- Status-Y */

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
