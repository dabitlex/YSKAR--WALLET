import { useEffect, useLayoutEffect, useRef } from 'react';
import { LeuchtZeichen } from './zeichen.tsx';

/*
  Der Startbildschirm (Splash A, "YSKAR Signature").

  Die Animation startet sofort und laeuft neben der echten Initialisierung
  (Tresor suchen, WalletProvider). Sie laeuft immer ganz durch -- bewusst
  gewaehlte Gestaltungszeit (GESAMT_MS + FLUG_MS, zusammen 2,5 s), damit
  man das Zusammensetzen sieht. Braucht die Wallet laenger, bleibt das fertige
  Zeichen stehen, bis sie weiss, in welcher Lage sie ist.

  Netz und Kontostand spielen keine Rolle: Die laden im DatenProvider
  unabhaengig im Hintergrund.

  Ist das Ziel die Sperre, fliegt das Zeichen an seinen Platz dort und dimmt
  dabei ab; sonst blendet der Startbildschirm einfach aus.
*/

const KURVE = 'cubic-bezier(.2,0,0,1)';
/** Zusammensetzen, Lichtkante und kurzes Halten des fertigen Zeichens. */
const GESAMT_MS = 2000;
const FLUG_MS = 500;

/** Jedes Band kommt entlang seiner eigenen Richtung (Logo-Einheiten), leicht versetzt nacheinander. */
const HERKUNFT: [number, number][] = [[-95, -80], [75, -55], [0, 95]];
const BEGINN = [0, 250, 500];
const BAND_MS = 600;

const naechstesBild = () => new Promise<void>(r => requestAnimationFrame(() => requestAnimationFrame(() => r())));

export function Startbild({ bereit, zurSperre, onUebergang, onFertig }: {
  /** Die Wallet kennt ihre Lage (Tresor gefunden oder nicht). */
  bereit: boolean;
  /** Danach kommt die Sperre (sonst der Einstieg). */
  zurSperre: boolean;
  /** Jetzt die Sperre darunter einhaengen; sie blendet selbst ein. */
  onUebergang: () => void;
  onFertig: () => void;
}) {
  const ganz = useRef<HTMLDivElement>(null);
  const grund = useRef<HTMLDivElement>(null);
  const schein = useRef<HTMLDivElement>(null);
  const flieger = useRef<HTMLDivElement>(null);
  const licht = useRef<HTMLDivElement>(null);
  const ruhe = useRef<HTMLDivElement>(null);
  const wort = useRef<HTMLDivElement>(null);
  const b0 = useRef<SVGGElement>(null), b1 = useRef<SVGGElement>(null), b2 = useRef<SVGGElement>(null);
  const glanz = useRef<SVGRectElement>(null);
  const laeuft = useRef<Animation[]>([]);
  const ziel = useRef(zurSperre);
  ziel.current = zurSperre;
  const still = typeof matchMedia === 'function' && matchMedia('(prefers-reduced-motion: reduce)').matches;

  // Zusammensetzen: sofort beim ersten Bild.
  useLayoutEffect(() => {
    const opt = (delay: number, duration: number): KeyframeAnimationOptions =>
      ({ delay: still ? 0 : delay, duration: still ? 0 : duration, easing: KURVE, fill: 'both' });
    const a: Animation[] = [];
    [b0, b1, b2].forEach((r, k) => {
      const [x, y] = HERKUNFT[k];
      if (r.current) a.push(r.current.animate(
        [{ transform: `translate(${x}px,${y}px)`, opacity: 0 }, { transform: 'translate(0px,0px)', opacity: 1 }], opt(BEGINN[k], BAND_MS)));
    });
    if (schein.current) a.push(schein.current.animate([{ opacity: 0 }, { opacity: 1 }], opt(400, 600)));
    if (glanz.current && !still) a.push(glanz.current.animate(
      [{ transform: 'translateX(0px)', opacity: 1 }, { transform: 'translateX(1300px)', opacity: 1 }], { delay: 1150, duration: 500, easing: 'ease-in-out', fill: 'both' }));
    if (wort.current) a.push(wort.current.animate([{ opacity: 0, transform: 'translateY(6px)' }, { opacity: 1, transform: 'none' }], opt(1300, 350)));
    // Das fertige Zeichen steht noch einen Moment, bevor es weiterfliegt.
    if (ganz.current && !still) a.push(ganz.current.animate([{ opacity: 1 }, { opacity: 1 }], { duration: GESAMT_MS }));
    laeuft.current = a;
    return () => a.forEach(x => x.cancel());
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  useEffect(() => {
    if (!bereit) return;
    let lebt = true;
    (async () => {
      // Bereit: die Animation zu Ende laufen lassen (sie hat schon beim Start begonnen).
      await Promise.all(laeuft.current.map(a => a.finished.catch(() => undefined)));
      if (!lebt || !ganz.current) return;

      const zeichen = ziel.current ? (onUebergang(), await naechstesBild(), document.querySelector('.pinseite .leucht')) : null;
      if (!lebt || !ganz.current) return;
      ganz.current.style.pointerEvents = 'none';
      const z = zeichen?.getBoundingClientRect();
      const q = flieger.current?.getBoundingClientRect();
      if (!z || !q || !z.width || still) {
        // Kein Platz fuer das Zeichen (sehr niedriger Bildschirm), ruhige Bewegung gewuenscht oder Einstieg: nur ausblenden.
        await ganz.current.animate([{ opacity: 1 }, { opacity: 0 }], { duration: still ? 120 : FLUG_MS, easing: KURVE, fill: 'forwards' }).finished.catch(() => undefined);
        if (lebt) onFertig();
        return;
      }
      const dx = z.left + z.width / 2 - (q.left + q.width / 2), dy = z.top + z.height / 2 - (q.top + q.height / 2), s = z.width / q.width;
      const flug: Animation[] = [
        flieger.current!.animate([{ transform: 'none' }, { transform: `translate(${dx}px,${dy}px) scale(${s})` }], { duration: FLUG_MS, easing: KURVE, fill: 'forwards' }),
        licht.current!.animate([{ opacity: 1 }, { opacity: 0 }], { duration: FLUG_MS, easing: 'ease-in', fill: 'forwards' }),
        ruhe.current!.animate([{ opacity: 0 }, { opacity: 1 }], { duration: FLUG_MS, easing: 'ease-out', fill: 'forwards' }),
        wort.current!.animate([{ opacity: 1 }, { opacity: 0 }], { duration: 150, fill: 'forwards' }),
        grund.current!.animate([{ opacity: 1 }, { opacity: 0 }], { delay: 120, duration: 300, easing: KURVE, fill: 'forwards' }),
      ];
      await Promise.all(flug.map(a => a.finished.catch(() => undefined)));
      if (lebt) onFertig();
    })();
    return () => { lebt = false; };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [bereit]);

  return (
    <div ref={ganz} className="startbild" role="img" aria-label="YSKAR">
      <div ref={grund} className="startbild-grund"><div ref={schein} className="startbild-schein" /></div>
      <div ref={flieger} className="startbild-zeichen">
        <div ref={ruhe} className="ebene" style={{ opacity: 0 }}><LeuchtZeichen baender={['aus', 'aus', 'aus']} /></div>
        <div ref={licht} className="ebene"><LeuchtZeichen baender={['an', 'an', 'an']} bandRefs={[b0, b1, b2]} glanzRef={glanz} /></div>
      </div>
      <div ref={wort} className="startbild-wort">YSKAR</div>
    </div>
  );
}
