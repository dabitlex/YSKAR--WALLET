import { useEffect, useMemo, useState } from 'react';
import { useT } from '../i18n/index.ts';
import { useDaten, useNav } from '../zustand.tsx';
import { useEinstellungen } from '../wallet/einstellungen.ts';
import { quelle, useKurs, fiatText, wert, ZEITRAEUME, type Zeitraum } from '../wallet/kurs.ts';
import { betragText } from '../wallet/betrag.ts';
import { S, KopfZurueck, Blatt } from '../ui/teile.tsx';

/** Ein Blatt, das sagt: noch nicht. Fuer Swap, Kaufen, Verkaufen. */
export function KommtBald({ titel, onZu }: { titel: string; onZu: () => void }) {
  const t = useT();
  return (
    <Blatt titel={titel} onZu={onZu}>
      <div className="grosskreis" style={{ width: 96, height: 96 }}><S n="uhr" g={44} w={1.8} /></div>
      <div style={{ display: 'flex', flexDirection: 'column', gap: 8, alignItems: 'center' }}>
        <h2 className="mittig">{t.start.baldTitel}</h2>
        <p className="mittig">{t.start.baldText}</p>
      </div>
      <button type="button" className="knopf zweit sinkt" onClick={onZu}>{t.allg.schliessen}</button>
    </Blatt>
  );
}

/**
 * Die Kurve als Flaeche mit Linie. Nur SVG, keine Bibliothek: Punkte werden
 * in die Breite gestreckt, die Hoehe laeuft zwischen Tief und Hoch.
 */
export function Kurve({ werte, hoehe = 180, klein }: { werte: number[]; hoehe?: number; klein?: boolean }) {
  const b = klein ? 44 : 372;
  const d = useMemo(() => {
    if (werte.length < 2) return { linie: '', flaeche: '' };
    const min = Math.min(...werte), max = Math.max(...werte), spanne = max - min || 1;
    const rand = klein ? 2 : 6;
    const p = werte.map((v, i) => [(i / (werte.length - 1)) * b, hoehe - rand - ((v - min) / spanne) * (hoehe - 2 * rand)] as const);
    const linie = p.map(([x, y], i) => `${i ? 'L' : 'M'}${x.toFixed(1)} ${y.toFixed(1)}`).join(' ');
    return { linie, flaeche: `${linie} L${b} ${hoehe} L0 ${hoehe} Z` };
  }, [werte, b, hoehe, klein]);
  const steigt = werte.length > 1 && werte[werte.length - 1] >= werte[0];
  const farbe = steigt ? 'var(--gruen)' : 'var(--rot)';
  return (
    <svg className="kurve" viewBox={`0 0 ${b} ${hoehe}`} width={klein ? b : undefined} height={hoehe} preserveAspectRatio="none" aria-hidden="true">
      <path d={d.flaeche} fill={klein ? farbe : 'var(--blau)'} opacity={klein ? .18 : .14} />
      <path d={d.linie} fill="none" stroke={klein ? farbe : 'var(--blau)'} strokeWidth={klein ? 2 : 2.5} strokeLinecap="round" strokeLinejoin="round" vectorEffect="non-scaling-stroke" />
    </svg>
  );
}

/** Die Kurs-Seite: Preis, Verlauf, Bestand -- und Kaufen/Verkaufen, sobald es das gibt. */
export function Kurs() {
  const t = useT();
  const nav = useNav();
  const { konto } = useDaten();
  const { waehrung } = useEinstellungen();
  const kurs = useKurs();
  const [zeitraum, setZeitraum] = useState<Zeitraum>('1w');
  const [werte, setWerte] = useState<number[]>([]);
  const [bald, setBald] = useState<null | 'kaufen' | 'verkaufen'>(null);

  useEffect(() => {
    let lebt = true;
    quelle.verlauf(zeitraum, waehrung).then(w => { if (lebt) setWerte(w); }).catch(() => {});
    return () => { lebt = false; };
  }, [zeitraum, waehrung]);

  const preis = kurs ? kurs.preis[waehrung] : null;
  const diff = kurs && werte.length > 1 ? werte[werte.length - 1] - werte[0] : null;
  const prozent = diff !== null && werte[0] ? (diff / werte[0]) * 100 : null;
  const bestand = konto ? BigInt(konto.balance) : 0n;
  const bestandWert = wert(bestand, kurs, waehrung);
  const fmt = (n: number) => fiatText(n, waehrung, t.code);
  const vz = (n: number) => (n >= 0 ? '+' : '−');

  return (
    <div className="seite" style={{ gap: 18 }}>
      <KopfZurueck titel={t.kurs.titel} zurueck={nav.zurueck} />

      <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
        <div className="etikett">{t.kurs.kurs}</div>
        <div style={{ display: 'flex', alignItems: 'baseline', gap: 12, flexWrap: 'wrap' }}>
          <span className="gross-titel" style={{ fontSize: 44 }}>{preis === null ? '–' : fmt(preis)}</span>
          {diff !== null && prozent !== null && (
            <span className={`pille ${diff >= 0 ? 'gruen' : 'rot'}`}>{vz(diff)}{fmt(Math.abs(diff))} · {vz(prozent)}{Math.abs(prozent).toLocaleString(t.code, { maximumFractionDigits: 1 })} %</span>
          )}
        </div>
        {kurs?.demo && <span className="fuss" style={{ textAlign: 'left' }}>{t.kurs.demoHinweis}</span>}
      </div>

      <Kurve werte={werte} />

      <div className="segment" role="group">
        {ZEITRAEUME.map(z => <button key={z} type="button" aria-pressed={zeitraum === z} onClick={() => setZeitraum(z)}>{t.kurs.zeitraum[z]}</button>)}
      </div>

      <div className="karte innen">
        <div className="paar rechts">
          <span>{t.kurs.bestand}</span>
          <div><span className="ziffern" style={{ fontSize: 15, fontWeight: 500 }}>{betragText(bestand, t.code)} YSR</span>{bestandWert !== null && <small style={{ fontSize: 13, color: 'var(--leise)' }}>≈ {fmt(bestandWert)}</small>}</div>
        </div>
        {werte.length > 1 && <>
          <div className="paar"><span>{t.kurs.hoch(t.kurs.zeitraum[zeitraum])}</span><span className="ziffern">{fmt(Math.max(...werte))}</span></div>
          <div className="paar"><span>{t.kurs.tief(t.kurs.zeitraum[zeitraum])}</span><span className="ziffern">{fmt(Math.min(...werte))}</span></div>
        </>}
        <div className="paar"><span>{t.kurs.blockzeit}</span><span className="ziffern">{t.kurs.minuten(10)}</span></div>
      </div>

      <div className="fuell" />
      <div className="paar-knoepfe">
        <button type="button" className="knopf sinkt" onClick={() => setBald('kaufen')}><S n="plus" g={20} w={2.4} /><span>{t.kurs.kaufen}</span></button>
        <button type="button" className="knopf zweit sinkt" onClick={() => setBald('verkaufen')}><S n="minus" g={20} w={2.4} /><span>{t.kurs.verkaufen}</span></button>
      </div>

      {bald && <KommtBald titel={bald === 'kaufen' ? t.kurs.kaufen : t.kurs.verkaufen} onZu={() => setBald(null)} />}
    </div>
  );
}
