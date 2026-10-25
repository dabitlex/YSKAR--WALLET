import { useEffect, useId, useMemo, useState } from 'react';
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
 * Die Kurve wie auf der Website: eine Flaeche, die nach unten ausblendet, eine
 * Linie im Markenverlauf (Tiefblau -> Cyan) und ein Punkt am aktuellen Wert.
 * Klein (in der Token-Zeile) nur eine Linie in Gruen oder Rot.
 */
export function Kurve({ werte, hoehe = 150, klein }: { werte: number[]; hoehe?: number; klein?: boolean }) {
  const id = useId().replace(/:/g, '');
  const b = klein ? 44 : 336;
  const d = useMemo(() => {
    if (werte.length < 2) return { linie: '', flaeche: '', ende: null as null | readonly [number, number] };
    const min = Math.min(...werte), max = Math.max(...werte), spanne = max - min || 1;
    const rand = klein ? 2 : 10;
    const p = werte.map((v, i) => [(i / (werte.length - 1)) * b, hoehe - rand - ((v - min) / spanne) * (hoehe - 2 * rand)] as const);
    const linie = p.map(([x, y], i) => `${i ? 'L' : 'M'}${x.toFixed(1)} ${y.toFixed(1)}`).join(' ');
    return { linie, flaeche: `${linie} L${b} ${hoehe} L0 ${hoehe} Z`, ende: p[p.length - 1] };
  }, [werte, b, hoehe, klein]);
  const steigt = werte.length > 1 && werte[werte.length - 1] >= werte[0];
  const farbe = steigt ? 'var(--gruen)' : 'var(--rot)';
  if (klein) {
    return (
      <svg className="kurve" viewBox={`0 0 ${b} ${hoehe}`} width={b} height={hoehe} preserveAspectRatio="none" aria-hidden="true">
        <path d={d.linie} fill="none" stroke={farbe} strokeWidth={2} strokeLinecap="round" strokeLinejoin="round" vectorEffect="non-scaling-stroke" />
      </svg>
    );
  }
  return (
    <svg className="kurve" viewBox={`0 0 ${b} ${hoehe}`} height={hoehe} preserveAspectRatio="none" aria-hidden="true">
      <defs>
        <linearGradient id={`f${id}`} x1="0" y1="0" x2="0" y2="1">
          <stop offset="0" stopColor="#2F7BFF" stopOpacity=".35" /><stop offset="1" stopColor="#2F7BFF" stopOpacity="0" />
        </linearGradient>
        <linearGradient id={`l${id}`} x1="0" y1="0" x2="1" y2="0">
          <stop offset="0" stopColor="#1B4FD1" /><stop offset="1" stopColor="#63D8FF" />
        </linearGradient>
      </defs>
      {[1, 2, 3].map(i => <line key={i} x1={0} x2={b} y1={(hoehe / 4) * i} y2={(hoehe / 4) * i} stroke="rgba(140,170,255,.12)" strokeDasharray="3 5" vectorEffect="non-scaling-stroke" />)}
      <path d={d.flaeche} fill={`url(#f${id})`} />
      <path d={d.linie} fill="none" stroke={`url(#l${id})`} strokeWidth={2.5} strokeLinecap="round" strokeLinejoin="round" vectorEffect="non-scaling-stroke" />
      {d.ende && <circle cx={d.ende[0]} cy={d.ende[1]} r={5} fill="#63D8FF" stroke="#0A1122" strokeWidth={2} vectorEffect="non-scaling-stroke" style={{ filter: 'drop-shadow(0 0 6px rgba(99,216,255,.7))' }} />}
    </svg>
  );
}

/** Die Kurs-Seite: Preis, Verlauf in der Karte, Eckdaten als Raster -- und Kaufen/Verkaufen, sobald es das gibt. */
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

      <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
        <div className="etikett">{t.kurs.kurs}</div>
        <div style={{ display: 'flex', alignItems: 'baseline', gap: 12, flexWrap: 'wrap' }}>
          <span className="gross-titel" style={{ fontSize: 46, fontWeight: 300 }}>{preis === null ? '–' : fmt(preis)}</span>
          {diff !== null && prozent !== null && (
            <span className={`pille ${diff >= 0 ? 'gruen' : 'rot'}`}>{vz(diff)}{fmt(Math.abs(diff))} · {vz(prozent)}{Math.abs(prozent).toLocaleString(t.code, { maximumFractionDigits: 1 })} %</span>
          )}
        </div>
        {kurs?.demo && <span className="fuss ziffern" style={{ textAlign: 'left', fontSize: 12 }}>{t.kurs.demoHinweis}</span>}
      </div>

      <div className="karte chartkarte">
        <div className="ttl"><span>{t.kurs.verlauf}</span><small>{t.kurs.zeitraumLang[zeitraum]} · {waehrung === 'eur' ? '€' : '$'}</small></div>
        <Kurve werte={werte} />
        <div className="segment" role="group">
          {ZEITRAEUME.map(z => <button key={z} type="button" aria-pressed={zeitraum === z} onClick={() => setZeitraum(z)}>{t.kurs.zeitraum[z]}</button>)}
        </div>
      </div>

      <div className="eckdaten">
        <div><span className="k">{t.kurs.bestand}</span><span className="v">{betragText(bestand, t.code)} YSR</span></div>
        <div><span className="k">{t.kurs.wert}</span><span className="v">{bestandWert === null ? '–' : fmt(bestandWert)}</span></div>
        {werte.length > 1 && <>
          <div><span className="k">{t.kurs.hoch(t.kurs.zeitraum[zeitraum])}</span><span className="v">{fmt(Math.max(...werte))}</span></div>
          <div><span className="k">{t.kurs.tief(t.kurs.zeitraum[zeitraum])}</span><span className="v">{fmt(Math.min(...werte))}</span></div>
        </>}
      </div>

      <div className="fuell" />
      <div className="paar-knoepfe">
        <button type="button" className="knopf sinkt" onClick={() => setBald('kaufen')}><S n="plus" g={20} w={2.4} /><span>{t.kurs.kaufen}</span></button>
        <button type="button" className="knopf zweit sinkt" onClick={() => setBald('verkaufen')}><S n="runter" g={20} w={2.2} /><span>{t.kurs.verkaufen}</span></button>
      </div>

      {bald && <KommtBald titel={bald === 'kaufen' ? t.kurs.kaufen : t.kurs.verkaufen} onZu={() => setBald(null)} />}
    </div>
  );
}
