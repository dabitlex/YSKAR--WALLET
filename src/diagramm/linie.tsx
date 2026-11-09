import { useId, useMemo, useRef, useState, type PointerEvent as ReactPointerEvent } from 'react';
import { skala, linienPfad, flaechenPfad, punktBeiX, duennen, type Punkt } from './pfad.ts';

/**
 * Der grosse, bedienbare Kursverlauf.
 *
 * Mit dem Finger ueber den Chart: Ein Fadenkreuz folgt und zeigt Kurs und
 * Zeitpunkt des naechstgelegenen echten Messpunktes. Es wird NICHT
 * zwischen Punkten interpoliert -- angezeigt wird, was gemessen wurde.
 *
 * Kein Bilddiagramm, kein Canvas: SVG laesst sich mit den Farbtokens des
 * Designsystems gestalten, skaliert auf jedem Bildschirm und bleibt im
 * DOM pruefbar.
 */

export interface LinienChartProps {
  punkte: Punkt[];
  /** Steigt der Verlauf? Faerbt Linie und Flaeche. null = neutral. */
  hoch: boolean | null;
  hoehe?: number;
  /** Kurs formatieren, z. B. als Waehrungsbetrag. */
  preisText(wert: number): string;
  /** Zeitpunkt formatieren. */
  zeitText(ms: number): string;
  /** Beschreibung fuer Screenreader. */
  beschriftung: string;
}

export function LinienChart({ punkte, hoch, hoehe = 200, preisText, zeitText, beschriftung }: LinienChartProps) {
  const id = useId();
  const huelle = useRef<HTMLDivElement>(null);
  const [zeiger, setZeiger] = useState<number | null>(null);
  // Fester Zeichenraum; SVG skaliert ihn auf die wirkliche Breite.
  const B = 1000;

  const { d, flaeche, s, p } = useMemo(() => {
    // Mehr Punkte als Bildspalten bringen nichts und kosten bei jeder
    // Fingerbewegung Rechenzeit.
    const p2 = duennen(punkte, 400);
    const s2 = skala(p2, { breite: B, hoehe, polster: 10 });
    return {
      p: p2, s: s2,
      d: s2 ? linienPfad(p2, s2) : '',
      flaeche: s2 ? flaechenPfad(p2, s2, hoehe) : '',
    };
  }, [punkte, hoehe]);

  if (!s || p.length === 0) {
    return <div className="chart-leer" style={{ height: hoehe }} role="img" aria-label={beschriftung} />;
  }

  const farbe = hoch === null ? 'var(--blau)' : hoch ? 'var(--gruen)' : 'var(--rot)';
  const i = zeiger === null ? -1 : punktBeiX(p, s, zeiger);
  const gewaehlt = i >= 0 ? p[i] : null;

  const bewegen = (e: ReactPointerEvent<HTMLDivElement>) => {
    const k = huelle.current?.getBoundingClientRect();
    if (!k || k.width === 0) return;
    setZeiger(((e.clientX - k.left) / k.width) * B);
  };

  return (
    <div className="chart" ref={huelle}
         onPointerDown={e => { e.currentTarget.setPointerCapture(e.pointerId); bewegen(e); }}
         onPointerMove={e => { if (e.buttons || e.pointerType === 'touch') bewegen(e); }}
         onPointerUp={() => setZeiger(null)}
         onPointerCancel={() => setZeiger(null)}
         onPointerLeave={() => setZeiger(null)}>
      {/* Der abgelesene Wert steht ueber dem Chart, nicht im Weg des Fingers. */}
      <div className={`chart-ablesung ${gewaehlt ? 'an' : ''}`} role="status" aria-live="off">
        {gewaehlt && <><b>{preisText(gewaehlt[1])}</b><span>{zeitText(gewaehlt[0])}</span></>}
      </div>

      <svg viewBox={`0 0 ${B} ${hoehe}`} height={hoehe} width="100%" preserveAspectRatio="none"
           role="img" aria-label={beschriftung} focusable="false">
        <defs>
          <linearGradient id={`v${id}`} x1="0" y1="0" x2="0" y2="1">
            <stop offset="0%" stopColor={farbe} stopOpacity=".22" />
            <stop offset="100%" stopColor={farbe} stopOpacity="0" />
          </linearGradient>
        </defs>
        <path d={flaeche} fill={`url(#v${id})`} />
        <path d={d} fill="none" stroke={farbe} strokeWidth="2"
              vectorEffect="non-scaling-stroke" strokeLinecap="round" strokeLinejoin="round" />
        {gewaehlt && (
          <g>
            <line x1={s.x(gewaehlt[0])} y1="0" x2={s.x(gewaehlt[0])} y2={hoehe}
                  stroke="var(--rand-hell)" strokeWidth="1" vectorEffect="non-scaling-stroke" />
            <circle cx={s.x(gewaehlt[0])} cy={s.y(gewaehlt[1])} r="4.5"
                    fill="var(--grund)" stroke={farbe} strokeWidth="2" vectorEffect="non-scaling-stroke" />
          </g>
        )}
      </svg>
    </div>
  );
}
