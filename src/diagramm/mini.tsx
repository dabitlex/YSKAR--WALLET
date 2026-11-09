import { useMemo } from 'react';
import { skala, linienPfad, duennen, ausReihe } from './pfad.ts';

/**
 * Mini-Chart fuer eine Listenzeile: nur die Linie, keine Achsen, keine
 * Bedienung. Steht neben Kurs und Veraenderung und zeigt die Form der
 * letzten sieben Tage.
 *
 * Die Farbe richtet sich nach der Richtung -- aber sie ist nie die
 * einzige Information: In derselben Zeile steht die Veraenderung mit
 * Vorzeichen. Wer Farben schlecht unterscheidet, liest die Zahl.
 */
export function MiniChart({ werte, hoch, breite = 64, hoehe = 28 }: {
  werte: number[]; hoch: boolean | null; breite?: number; hoehe?: number;
}) {
  const d = useMemo(() => {
    if (!werte || werte.length < 2) return null;
    // Mehr als 40 Punkte sind auf 64 Bildpunkten nicht zu sehen und
    // kosten nur Rechenzeit, mal der Laenge der Liste.
    const p = duennen(ausReihe(werte), 40);
    const s = skala(p, { breite, hoehe, polster: 3 });
    return s ? linienPfad(p, s) : null;
  }, [werte, breite, hoehe]);

  if (!d) return <span className="mini-leer" aria-hidden="true" />;
  const farbe = hoch === null ? 'var(--leise)' : hoch ? 'var(--gruen)' : 'var(--rot)';
  return (
    <svg className="mini" width={breite} height={hoehe} viewBox={`0 0 ${breite} ${hoehe}`}
         fill="none" aria-hidden="true" focusable="false">
      <path d={d} stroke={farbe} strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  );
}
