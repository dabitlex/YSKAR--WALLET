import { useCallback, useEffect, useRef, useState, type ReactNode } from 'react';
import { useT, type Woerterbuch } from '../i18n/index.ts';
import { useNav, useWallet, type Ort } from '../zustand.tsx';
import { einstellen } from '../wallet/einstellungen.ts';
import { imBrowserOeffnen } from '../geraet/geraet.ts';
import { S, QrBild, useZurueck } from '../ui/teile.tsx';
import zeichenBild from '../bilder/zeichen.svg';

/*
  Einfuehrung in die Wallet: acht Karten im Story-Stil.

  Jede Karte laeuft 6 Sekunden, dann kommt die naechste. Tippen links geht
  zurueck, rechts weiter, gedrueckt halten pausiert. Dazu sichtbare Pfeile
  und ein X. Der Knopf einer Karte fuehrt direkt zur passenden Funktion.

  Die Bilder sind flache Ausschnitte der echten Bildschirme mit
  Beispielwerten -- keine fremden Waehrungen, keine erfundenen Zahlen.
*/

export const DAUER_MS = 6000;
const KANAL = 'https://t.me/yskar_official';

/* ---------------------------------------------------------------- Muenze */

/** Die YSKAR-Muenze, leicht gekippt, mit geriffelter Kante und Schatten am Boden. */
export function Muenze3D() {
  const riffel: ReactNode[] = [];
  for (let i = 0; i < 120; i++) {
    const a = (2 * Math.PI * i) / 120;
    riffel.push(<line key={i} x1={100 + 93 * Math.cos(a)} y1={100 + 93 * Math.sin(a)} x2={100 + 98 * Math.cos(a)} y2={100 + 98 * Math.sin(a)}
                      stroke="#0A1F5C" strokeOpacity={0.45} strokeWidth={1} />);
  }
  const kante: ReactNode[] = [];
  for (let i = 16; i > 0; i--) kante.push(<ellipse key={i} cx={100 + i} cy={100} rx={78} ry={98} fill={i % 2 ? '#0A1F5C' : '#1B4FD1'} />);
  return (
    <svg className="ef-muenze" viewBox="-20 -10 260 270" role="img" aria-label="YSKAR">
      <defs>
        <linearGradient id="ef-ring" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stopColor="#8FE3FF" /><stop offset=".35" stopColor="#2F7BFF" /><stop offset=".7" stopColor="#1B4FD1" /><stop offset="1" stopColor="#0E2E8A" /></linearGradient>
        <radialGradient id="ef-flaeche" cx=".38" cy=".3" r=".9"><stop offset="0" stopColor="#1A2C5E" /><stop offset=".55" stopColor="#0A1330" /><stop offset="1" stopColor="#03060F" /></radialGradient>
        <linearGradient id="ef-fase" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stopColor="#BFEFFF" stopOpacity=".9" /><stop offset=".5" stopColor="#2F7BFF" stopOpacity=".2" /><stop offset="1" stopColor="#000" stopOpacity=".8" /></linearGradient>
        <linearGradient id="ef-glanz" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stopColor="#fff" stopOpacity=".10" /><stop offset=".45" stopColor="#fff" stopOpacity="0" /></linearGradient>
        <filter id="ef-praeg" x="-20%" y="-20%" width="140%" height="140%">
          <feOffset in="SourceAlpha" dx="-1.5" dy="-1.5" result="o1" /><feFlood floodColor="#BFEFFF" floodOpacity=".55" /><feComposite in2="o1" operator="in" result="hell" />
          <feOffset in="SourceAlpha" dx="2" dy="2.5" result="o2" /><feFlood floodColor="#000" floodOpacity=".75" /><feComposite in2="o2" operator="in" result="dunkel" />
          <feMerge><feMergeNode in="dunkel" /><feMergeNode in="hell" /><feMergeNode in="SourceGraphic" /></feMerge>
        </filter>
        <filter id="ef-boden" x="-50%" y="-200%" width="200%" height="500%"><feGaussianBlur stdDeviation="9" /></filter>
      </defs>
      <ellipse cx="108" cy="222" rx="70" ry="11" fill="#000" fillOpacity=".65" filter="url(#ef-boden)" />
      {kante}
      <g transform="translate(22 0) scale(.78 1)">
        <circle cx="100" cy="100" r="98" fill="url(#ef-ring)" />
        {riffel}
        <circle cx="100" cy="100" r="86" fill="url(#ef-flaeche)" />
        <circle cx="100" cy="100" r="86" fill="none" stroke="url(#ef-fase)" strokeWidth="2.2" />
        <image href={zeichenBild} x="56" y="54" width="88" height="88" filter="url(#ef-praeg)" />
        <circle cx="100" cy="100" r="86" fill="url(#ef-glanz)" />
      </g>
    </svg>
  );
}

/* ---------------------------------------------------------------- Bilder */

const Mini = ({ children }: { children: ReactNode }) => <div className="ef-mini">{children}</div>;
const Paar = ({ l, r }: { l: string; r: string }) => <div><span>{l}</span><b>{r}</b></div>;

function Bild({ nr, t, adresse }: { nr: number; t: Woerterbuch; adresse: string }) {
  const e = t.einf;
  switch (nr) {
    case 0: return <Muenze3D />;
    case 1: {
      const w = ['river', 'smile', 'orbit', '', '', '', 'canvas', '', '', '', '', ''];
      return <div className="ef-woerter" aria-hidden="true">{w.map((x, i) => <span key={i} className={x ? '' : 'zu'}><b>{i + 1}.</b>{x || '•••••'}</span>)}</div>;
    }
    case 2: return (
      <Mini>
        <div className="ef-t">{t.start.empfangen}</div>
        <div className="ef-qr"><QrBild text={`yskar:${adresse}`} label={e.k3.titel} /></div>
        <div className="ef-chip">{adresse.slice(0, 12)}…{adresse.slice(-6)}</div>
        <div className="ef-p"><div><span>{e.bild.anfordern}</span><b className="blau">{t.allg.optional.replace(/[()]/g, '')}</b></div></div>
      </Mini>
    );
    case 3: return (
      <Mini>
        <div className="ef-k">{e.bild.duSendest}</div>
        <div className="ef-b">25<i>,50</i><u>YSR</u></div>
        <div className="ef-p"><Paar l={e.bild.empfaenger} r="ysr1q2ry…mzl" /><Paar l={e.bild.gebuehr} r="0,00000174" /><Paar l={e.bild.gesamt} r="25,50000174" /></div>
        <div className="ef-sch"><span><S n="doppel" g={18} /></span>{e.bild.schieben}</div>
      </Mini>
    );
    case 4: return (
      <div className="ef-stapel">
        <div className="ef-note"><img src={zeichenBild} alt="" /><span><b>{e.bild.neuerEingang}</b>+12,30 YSR · ysr1q2ry…mzl</span></div>
        <Mini>
          <div className="ef-t links">{t.verlauf.titel}</div>
          <div className="ef-p">
            <div className="ef-zl"><span className="ef-i bern"><S n="hoch" g={14} /></span><span className="ef-m"><b>ysr1k8d3…w0a5 <em>{e.bild.wartet}</em></b><small>{e.bild.geradeEben}</small></span><span className="ef-w">−25,50</span></div>
            <div className="ef-zl"><span className="ef-i gruen"><S n="runter" g={14} /></span><span className="ef-m"><b>ysr1q2ry…mzl</b><small>{e.bild.imBlock}</small></span><span className="ef-w gruen">+12,30</span></div>
          </div>
        </Mini>
      </div>
    );
    case 5: return (
      <Mini>
        <div className="ef-finger"><S n="finger" g={40} w={1.6} /></div>
        <div className="ef-t">{e.bild.entsperren}</div>
        <div className="ef-punkte"><i /><i /><i /><i className="l" /><i className="l" /><i className="l" /></div>
        <div className="ef-p"><div><span>{t.einst.finger}</span><span className="ef-sw" /></div><div><span>{t.einst.sperre}</span><b className="blau">{t.einst.min1Kurz}</b></div></div>
      </Mini>
    );
    case 6: return (
      <Mini>
        <div className="ef-welt"><S n="welt" g={22} /></div>
        <div className="ef-k">{t.qr.frage}</div>
        <div className="ef-t gross">lab.yskar.app?</div>
        <div className="ef-p"><Paar l={t.sig.adresse} r={`${adresse.slice(0, 8)}…${adresse.slice(-6)}`} /><Paar l={t.sig.laeuftAb} r={t.sig.inMin(4)} /></div>
        <div className="ef-knopf">{t.sig.anmelden}</div>
      </Mini>
    );
    default: return (
      <div className="ef-tg">
        <div><span className="a"><img src={zeichenBild} alt="" /></span><span><b>YSKAR</b><small>{e.bild.kanal}</small></span><span className="o"><S n="flieger" g={20} /></span></div>
        <div><span className="a"><img src={zeichenBild} alt="" /></span><span><b>YSKAR Community</b><small>{e.bild.gruppe}</small></span><span className="o"><S n="blase" g={20} /></span></div>
      </div>
    );
  }
}

/* --------------------------------------------------------------- Ablauf */

export function Einfuehrung() {
  const t = useT();
  const nav = useNav();
  const wallet = useWallet();
  const karten = [t.einf.k1, t.einf.k2, t.einf.k3, t.einf.k4, t.einf.k5, t.einf.k6, t.einf.k7, t.einf.k8];
  const [nr, setNr] = useState(0);
  const [pause, setPause] = useState(false);
  const [fortschritt, setFortschritt] = useState(0);   // 0..1 der aktuellen Karte
  const gelaufen = useRef(0);
  const druck = useRef<{ zeit: number; x: number } | null>(null);

  useEffect(() => { einstellen({ einfuehrungGesehen: true }); }, []);
  const schliessen = useCallback(() => nav.zurueck(), [nav]);
  useZurueck(schliessen);

  const gehe = useCallback((n: number) => {
    if (n < 0) n = 0;
    if (n > 7) { schliessen(); return; }
    gelaufen.current = 0; setFortschritt(0); setNr(n);
  }, [schliessen]);

  // Die Uhr der aktuellen Karte. Laeuft nicht, solange pausiert ist.
  useEffect(() => {
    if (pause) return;
    let letzte = performance.now(), rahmen = 0;
    const schritt = (jetzt: number) => {
      gelaufen.current += jetzt - letzte; letzte = jetzt;
      const f = Math.min(1, gelaufen.current / DAUER_MS);
      setFortschritt(f);
      if (f >= 1) { gehe(nr + 1); return; }
      rahmen = requestAnimationFrame(schritt);
    };
    rahmen = requestAnimationFrame(schritt);
    return () => cancelAnimationFrame(rahmen);
  }, [nr, pause, gehe]);

  // Kurz tippen: links zurueck, rechts weiter. Gedrueckt halten: Pause.
  const runter = (x: number) => { druck.current = { zeit: Date.now(), x }; setPause(true); };
  const hoch = (breite: number) => {
    const d = druck.current; druck.current = null; setPause(false);
    if (!d || Date.now() - d.zeit > 250) return;
    gehe(d.x < breite / 2 ? nr - 1 : nr + 1);
  };

  const ziel = (o: Ort) => nav.ersetze(o);
  const aktion = () => {
    switch (nr) {
      case 0: gehe(1); break;
      case 1: ziel({ n: 'woerterZeigen' }); break;
      case 2: ziel({ n: 'empfangen' }); break;
      case 3: ziel({ n: 'senden' }); break;
      case 4: nav.reiter('einstellungen'); break;
      case 5: ziel({ n: 'sicherheit' }); break;
      case 6: ziel({ n: 'signieren' }); break;
      default: imBrowserOeffnen(KANAL); schliessen();
    }
  };

  const k = karten[nr];
  return (
    <div className="ef" role="dialog" aria-modal="true" aria-label={t.einf.titel}>
      <div className="ef-balken" aria-hidden="true">
        {karten.map((_, i) => <i key={i}><b style={{ width: `${i < nr ? 100 : i === nr ? fortschritt * 100 : 0}%` }} /></i>)}
      </div>
      <button type="button" className="ef-zu sinkt" aria-label={t.allg.schliessen} onClick={schliessen}><S n="kreuz" g={20} w={2.4} /></button>

      <div className="ef-flaeche"
           onPointerDown={e => runter(e.clientX - e.currentTarget.getBoundingClientRect().left)}
           onPointerUp={e => hoch(e.currentTarget.getBoundingClientRect().width)}
           onPointerLeave={() => { if (druck.current) { druck.current = null; setPause(false); } }}>
        <div key={nr} className="ef-bild"><Bild nr={nr} t={t} adresse={wallet.adresse ?? ''} /></div>
      </div>

      <div key={`t${nr}`} className="ef-text" aria-live="polite">
        <h2>{k.titel}</h2>
        <p>{k.text}</p>
        <div className="ef-unten">
          <button type="button" className="ef-aktion sinkt" onClick={aktion}>{k.knopf}</button>
          <button type="button" className="ef-pfeil sinkt" aria-label={t.einf.zurueck} disabled={nr === 0} onClick={() => gehe(nr - 1)}><S n="zurueck" g={20} w={2.2} /></button>
          <button type="button" className="ef-pfeil sinkt" aria-label={t.einf.weiter} disabled={nr === 7} onClick={() => gehe(nr + 1)}><S n="weiter" g={20} w={2.2} /></button>
        </div>
      </div>
    </div>
  );
}

/** Karte auf dem Startbildschirm, bis die Einfuehrung einmal geoeffnet oder die Karte geschlossen wurde. */
export function EinfuehrungKarte() {
  const t = useT();
  const nav = useNav();
  return (
    <div className="ef-karte auf">
      <button type="button" className="ef-karte-los" onClick={() => nav.gehe({ n: 'einfuehrung' })}>
        <span className="ef-karte-text"><b>{t.einf.karteTitel}</b><span>{t.einf.karteText} ›</span></span>
        <span className="ef-karte-bild"><img src={zeichenBild} alt="" /></span>
      </button>
      <button type="button" className="ef-karte-zu" aria-label={t.allg.schliessen} onClick={() => einstellen({ einfuehrungGesehen: true })}><S n="kreuz" g={14} w={2.4} /></button>
    </div>
  );
}
