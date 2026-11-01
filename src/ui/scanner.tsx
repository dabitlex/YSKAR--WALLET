import { useEffect, useRef, useState } from 'react';
import { useT } from '../i18n/index.ts';
import { S, useZurueck, obenauflegen } from './teile.tsx';
import { fehlerMerken } from '../geraet/plattform.ts';

interface Detektor { detect(q: CanvasImageSource): Promise<{ rawValue: string }[]> }

/**
 * QR-Code mit der Kamera lesen.
 *
 * Das Bild bleibt auf dem Geraet: Erkannt wird mit der eingebauten
 * BarcodeDetector-Funktion der Webansicht, sonst mit jsQR. `onText` sagt,
 * ob der gelesene Text brauchbar war; sonst sucht der Scanner weiter.
 */
/** Ein durchsichtiges Bild von 1x1 Pixel. */
const LEER = 'data:image/gif;base64,R0lGODlhAQABAIAAAAAAAP///yH5BAEAAAAALAAAAAABAAEAAAIBRAA7';

export function Scanner({ onText, onZu, zweck, einfuegen }: {
  onText: (text: string) => boolean; onZu: () => void;
  /** Was gescannt werden soll; Vorgabe: Adresse oder Zahlungsanforderung. */
  zweck?: string;
  /** Wenn gesetzt: Knopf "Adresse stattdessen einfuegen" (4.2). */
  einfuegen?: () => void;
}) {
  const t = useT();
  const video = useRef<HTMLVideoElement>(null);
  const spur = useRef<MediaStreamTrack | null>(null);
  const [fehler, setFehler] = useState('');
  const [hinweis, setHinweis] = useState('');
  const [laeuft, setLaeuft] = useState(false);
  // Licht nur anbieten, wenn die Kamera es selbst meldet.
  const [lichtDa, setLichtDa] = useState(false);
  const [licht, setLicht] = useState(false);
  const rueckruf = useRef(onText);
  rueckruf.current = onText;
  useZurueck(onZu);

  useEffect(() => {
    let lebt = true, strom: MediaStream | null = null, uhr = 0;
    (async () => {
      if (!navigator.mediaDevices?.getUserMedia) { setFehler(t.scan.keineKamera); return; }
      try {
        strom = await navigator.mediaDevices.getUserMedia({
          audio: false, video: { facingMode: { ideal: 'environment' }, width: { ideal: 1280 }, height: { ideal: 720 } },
        });
      } catch (e) {
        fehlerMerken('kamera', e);
        const name = (e as Error)?.name ?? '';
        setFehler(name === 'NotAllowedError' || name === 'SecurityError' ? t.scan.keinZugriff : t.scan.keineKamera);
        return;
      }
      if (!lebt) { strom.getTracks().forEach(s => s.stop()); return; }
      spur.current = strom.getVideoTracks()[0] ?? null;
      try {
        const k = spur.current?.getCapabilities?.() as (MediaTrackCapabilities & { torch?: boolean }) | undefined;
        if (k?.torch) setLichtDa(true);
      } catch { /* ohne Licht */ }
      const v = video.current!;
      v.srcObject = strom;
      await v.play().catch(() => {});

      let detektor: Detektor | null = null;
      const Klasse = (window as unknown as { BarcodeDetector?: new (o: { formats: string[] }) => Detektor }).BarcodeDetector;
      if (Klasse) { try { detektor = new Klasse({ formats: ['qr_code'] }); } catch { detektor = null; } }
      const jsQR = detektor ? null : (await import('jsqr')).default;
      const leinwand = document.createElement('canvas');
      const stift = leinwand.getContext('2d', { willReadFrequently: true });
      let letzter = '';

      const lies = async () => {
        if (!lebt) return;
        try {
          let text = '';
          if (v.readyState >= 2 && v.videoWidth > 0) {
            if (detektor) {
              text = (await detektor.detect(v))[0]?.rawValue ?? '';
            } else if (jsQR && stift) {
              const g = Math.min(v.videoWidth, v.videoHeight, 720);
              leinwand.width = g; leinwand.height = g;
              stift.drawImage(v, (v.videoWidth - g) / 2, (v.videoHeight - g) / 2, g, g, 0, 0, g, g);
              text = jsQR(stift.getImageData(0, 0, g, g).data, g, g, { inversionAttempts: 'dontInvert' })?.data ?? '';
            }
          }
          if (text && text !== letzter) {
            letzter = text;
            if (rueckruf.current(text)) return;
            setHinweis(t.scan.keinCode);
          }
        } catch (e) { fehlerMerken('scannen', e); }
        uhr = window.setTimeout(lies, 160);
      };
      lies();
    })();
    return () => { lebt = false; clearTimeout(uhr); strom?.getTracks().forEach(s => s.stop()); };
  }, [t]);

  const lichtUmschalten = async () => {
    const an = !licht;
    try {
      await spur.current?.applyConstraints({ advanced: [{ torch: an } as MediaTrackConstraintSet] });
      setLicht(an);
    } catch (e) { fehlerMerken('licht', e); setLichtDa(false); }
  };

  return obenauflegen(
    <div className="scanner" role="dialog" aria-modal="true" aria-label={t.scan.titel}>
      {/* Ohne eigenes Vorschaubild zeigt die Android-Webansicht ein graues Feld mit Play-Symbol,
          bis das erste Kamerabild da ist. Deshalb: leeres Vorschaubild, sichtbar erst ab dem ersten Bild. */}
      <video ref={video} playsInline muted poster={LEER} className={laeuft ? 'laeuft' : undefined}
             onPlaying={() => setLaeuft(true)} />
      {!fehler && <div className="rahmen" aria-hidden="true"><i /><i /><i /><i /><span className="st-teilung"><i /><i /></span></div>}
      <div className="oben">
        <button type="button" className="klein sinkt" aria-label={t.allg.schliessen} onClick={onZu}><S n="kreuz" g={19} w={1.6} /></button>
        <h1>{t.scan.kurz}</h1>
        {lichtDa
          ? <button type="button" className="klein sinkt" aria-pressed={licht} aria-label={licht ? t.scan.lichtAus : t.scan.licht} onClick={lichtUmschalten}><S n="licht" g={19} w={1.6} /></button>
          : <span className="sf-platz" />}
      </div>
      <div className="text" role="status">
        <p>{fehler || hinweis || zweck || t.scan.zahlung}</p>
        {!fehler && <p className="sf-klein">{t.scan.lokal}</p>}
      </div>
      {einfuegen && <div className="unten"><button type="button" className="knopf zweit sinkt" onClick={einfuegen}>{t.scan.einfuegen}</button></div>}
    </div>,
  );
}
