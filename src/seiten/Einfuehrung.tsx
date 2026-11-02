import { useCallback, useEffect, useMemo, useRef, useState, type CSSProperties, type ReactNode } from 'react';
import { useT, type Woerterbuch } from '../i18n/index.ts';
import { useNav, type Ort } from '../zustand.tsx';
import { einstellen } from '../wallet/einstellungen.ts';
import { imBrowserOeffnen } from '../geraet/geraet.ts';
import { S, useZurueck } from '../ui/teile.tsx';
import { LeuchtZeichen, YMarke, YStatus, type Teilstand } from '../ui/zeichen.tsx';
import walletDe from '../bilder/einf/wallet-de.webp';
import walletEn from '../bilder/einf/wallet-en.webp';
import sendenDe from '../bilder/einf/senden-de.webp';
import sendenEn from '../bilder/einf/senden-en.webp';
import aktivitaetDe from '../bilder/einf/aktivitaet-de.webp';
import aktivitaetEn from '../bilder/einf/aktivitaet-en.webp';
import einstellungenDe from '../bilder/einf/einstellungen-de.webp';
import einstellungenEn from '../bilder/einf/einstellungen-en.webp';

/*
  Einfuehrung in die Wallet: elf Karten.

  Der Nutzer blaettert selbst: Pfeile, Tippen links/rechts, Ueberspringen
  oder X. Nichts laeuft von allein weiter. Der Knopf einer Karte fuehrt
  direkt zur passenden Funktion; die letzte Karte schliesst den Rundgang ab.

  Alle Bilder zeigen feste Demo-Daten: Ausschnitte aus Aufnahmen einer
  Testwallet (src/bilder/einf/) und eigene Grafiken. Keine eigene Adresse,
  kein echter QR-Code, nie echte Woerter.
*/

const KANAL = 'https://t.me/yskar_official';
const ANZAHL = 11;
/** Feste Demo-Adresse fuer die Grafiken (keine echte Wallet). */
const DEMO = 'ysr1q2ry…r4mzl';

const AUFNAHMEN = {
  wallet: { de: walletDe, en: walletEn },
  senden: { de: sendenDe, en: sendenEn },
  aktivitaet: { de: aktivitaetDe, en: aktivitaetEn },
  einstellungen: { de: einstellungenDe, en: einstellungenEn },
};
type Aufnahme = keyof typeof AUFNAHMEN;
/** Rechteck in Anteilen des aufgenommenen Bildschirms (412 × 915): x, y, Breite, Hoehe. */
type Feld = [number, number, number, number];
const SEITE = 915 / 412;

/* --------------------------------------------------------------- Bausteine */

/**
 * Ausschnitt einer Aufnahme in voller Kartenbreite; `mark` hebt das Wichtige
 * hervor. Er passt sich der freien Hoehe an (Container-Einheiten der Buehne).
 */
function Ausschnitt({ bild, a: [x, y, w, h], mark, klein }: { bild: string; a: Feld; mark?: Feld; klein?: boolean }) {
  const verhaeltnis = w / (h * SEITE);
  const stil: CSSProperties = { aspectRatio: `${w} / ${h * SEITE}`, width: `min(100%, ${klein ? 340 : 360}px, ${(klein ? 36 : 100) * verhaeltnis}cqh)` };
  return (
    <div className="ef-aus" style={stil} aria-hidden="true">
      <img src={bild} alt="" draggable={false} style={{ width: `${100 / w}%`, left: `${-x / w * 100}%`, top: `${-y / h * 100}%` }} />
      {mark && <i className="ef-mark" style={{ left: `${(mark[0] - x) / w * 100}%`, top: `${(mark[1] - y) / h * 100}%`, width: `${mark[2] / w * 100}%`, height: `${mark[3] / h * 100}%` }} />}
    </div>
  );
}

/** Ein QR-artiges Muster als Bild -- bewusst nicht lesbar, nur Demo. */
function DemoQr() {
  const pfad = useMemo(() => {
    const n = 25; let s = 7, d = '';
    const zufall = () => (s = (s * 9301 + 49297) % 233280) / 233280;
    const finder = (x: number, y: number) => (x < 7 && y < 7) || (x >= n - 7 && y < 7) || (x < 7 && y >= n - 7);
    for (let y = 0; y < n; y++) for (let x = 0; x < n; x++) {
      if (finder(x, y)) {
        const fx = x < 7 ? x : x - (n - 7), fy = y < 7 ? y : y - (n - 7);
        if (fx === 0 || fx === 6 || fy === 0 || fy === 6 || (fx >= 2 && fx <= 4 && fy >= 2 && fy <= 4)) d += `M${x} ${y}h1v1h-1z`;
      } else if (Math.abs(x - 12) < 3.5 && Math.abs(y - 12) < 3.5) { /* Platz fuer das Zeichen */ }
      else if (zufall() > .52) d += `M${x} ${y}h1v1h-1z`;
    }
    return d;
  }, []);
  return (
    <div className="qrplatte">
      <svg viewBox="0 0 25 25" shapeRendering="crispEdges" aria-hidden="true"><path fill="#06112A" d={pfad} /></svg>
      <span className="qr-logo" aria-hidden="true"><YMarke g={26} farbe="var(--blau-hell)" /></span>
    </div>
  );
}

/** Das Y fuellt sich in drei Stufen und beginnt dann von vorn (eigene Uhr, unabhaengig vom Blaettern). */
function StatusAnimation({ b }: { b: Woerterbuch['einf']['bild'] }) {
  const still = typeof matchMedia === 'function' && matchMedia('(prefers-reduced-motion: reduce)').matches;
  const [phase, setPhase] = useState(still ? 2 : 0);
  useEffect(() => {
    if (still) return;
    const dauer = [1400, 1400, 2400];
    const u = setTimeout(() => setPhase(p => (p + 1) % 3), dauer[phase]);
    return () => clearTimeout(u);
  }, [phase, still]);
  const stand: [Teilstand, Teilstand, Teilstand][] = [['erledigt', 'wartet', 'offen'], ['erledigt', 'erledigt', 'offen'], ['erledigt', 'erledigt', 'erledigt']];
  const unter = [b.unterwegs, b.eingetragen, b.bestaetigt][phase];
  return (
    <div className="ef-spalte">
      <div className="ef-stand">
        <YStatus g={120} stand={stand[phase]} label={`${b.anAnna} · ${unter}`} />
        <div><div className="ef-betrag">12<i>,50</i></div><div className="ef-klein">{b.anAnna}<br />{unter}</div></div>
      </div>
      <div className="ef-zeilen glas">
        {[b.angenommen, b.imBlock, b.weitere].map((x, i) => <span key={i} className={stand[phase][i]}><i />{x}</span>)}
      </div>
    </div>
  );
}

const Akzent = ({ k }: { k: { titel: string; akzent: string } }) => <>{k.titel} <em>{k.akzent}</em></>;

/* ----------------------------------------------------------------- Bilder */

function Bild({ nr, t, sprache }: { nr: number; t: Woerterbuch; sprache: 'de' | 'en' }) {
  const b = t.einf.bild;
  const aufnahme = (n: Aufnahme) => AUFNAHMEN[n][sprache];
  const mitte = (inhalt: ReactNode) => <div className="ef-zent">{inhalt}</div>;
  switch (nr) {
    case 0: return mitte(<Ausschnitt bild={aufnahme('wallet')} a={[0, .02, 1, .51]} mark={[.035, .405, .93, .1]} />);
    case 1: return mitte(
      <div className="ef-spalte">
        <div className="ef-qr glas">
          <DemoQr />
          <span className="ef-mono">{DEMO}</span>
          <span className="ef-eingang"><S n="runter" g={14} w={2.2} />+12,30 YSR</span>
        </div>
        <span className="ef-chip">{b.anfordern} <b>{b.optional}</b></span>
      </div>);
    case 2: return mitte(
      <div className="ef-spalte voll">
        <Ausschnitt bild={aufnahme('senden')} a={[0, .09, 1, .2]} klein />
        <Ausschnitt bild={aufnahme('senden')} a={[.02, .88, .96, .09]} mark={[.03, .887, .94, .066]} />
        <span className="ef-marke">{b.tippen}</span>
      </div>);
    case 3: return mitte(<StatusAnimation b={b} />);
    case 4: return mitte(<Ausschnitt bild={aufnahme('aktivitaet')} a={[0, .02, 1, .46]} mark={[.03, .172, .94, .098]} />);
    case 5: return mitte(
      <div className="ef-spalte voll">
        {[['A', 'Anna', DEMO], ['BK', 'Bäckerei Kern', b.zuletzt], ['L', 'Lena', b.empfangenHeute]].map(([k, n, u], i) => (
          <div key={n} className={`ef-kontakt glas ${i === 1 ? 'hell' : ''}`}>
            <span className="av">{k}</span><span className="m">{n}<small className={i === 0 ? 'ef-mono' : ''}>{u}</small></span>
            {i === 1 && <span className="los">{b.senden}</span>}
          </div>))}
        <span className="ef-chip"><S n="schloss" g={14} />{b.nurHier}</span>
      </div>);
    case 6: return mitte( // Nur verdeckte Platzhalter -- nie Woerter.
      <div className="ef-spalte voll">
        <div className="ef-woerter glas">
          {Array.from({ length: 12 }, (_, i) => <span key={i}><b>{i + 1}</b><i style={{ width: `${[52, 38, 60, 44, 34, 56, 40, 62, 46, 36, 54, 42][i]}%` }} /></span>)}
          <em className="ef-schloss"><S n="schluessel" g={20} /></em>
        </div>
        <div className="ef-hinweis"><S n="warnung" g={18} /><span><b>{b.schluessel}</b>{b.nieTeilen}</span></div>
      </div>);
    case 7: return mitte(
      <div className="ef-sperre glas">
        <LeuchtZeichen baender={['an', 'halb', 'aus']} klasse="ef-sperre-zeichen" />
        <div className="ef-punkte"><i className="v" /><i className="v" /><i className="v" /><i /><i /><i /></div>
        <div className="ef-tabelle">
          <div><span><S n="finger" g={16} />{b.finger}</span><b className="blau">{b.an}</b></div>
          <div><span><S n="uhr" g={16} />{b.sperre}</span><b>{b.minute}</b></div>
        </div>
      </div>);
    case 8: return mitte( // Das Y als Siegel unter einer signierten Demo-Nachricht.
      <div className="ef-siegel glas">
        <div className="ef-klein">{b.nachricht}</div>
        <div className="ef-satz">{b.nachrichtText}</div>
        <div className="ef-siegel-mitte"><i /><LeuchtZeichen baender={['an', 'an', 'an']} /><i /></div>
        <div className="ef-paar"><span>{b.signiertMit}</span><b className="ef-mono">{DEMO}</b></div>
        <div className="ef-paar"><span>{b.signatur}</span><b className="ef-mono blau">3f9a…c21e</b></div>
      </div>);
    case 9: return mitte(<Ausschnitt bild={aufnahme('einstellungen')} a={[0, .065, 1, .5]} mark={[.03, .33, .94, .215]} />);
    default: return ( // Abschluss: das Zeichen setzt sich noch einmal zusammen, wie beim Start der Wallet.
      <div className="ef-zent">
        <div className="ef-ende-zeichen"><LeuchtZeichen baender={['an', 'an', 'an']} /></div>
      </div>);
  }
}

/* --------------------------------------------------------------- Ablauf */

export function Einfuehrung() {
  const t = useT();
  const nav = useNav();
  const karten = [t.einf.k1, t.einf.k2, t.einf.k3, t.einf.k4, t.einf.k5, t.einf.k6, t.einf.k7, t.einf.k8, t.einf.k9, t.einf.k10, t.einf.k11];
  const [nr, setNr] = useState(0);
  const druck = useRef<number | null>(null);
  const sprache = t.code.startsWith('de') ? 'de' : 'en';
  const ende = nr === ANZAHL - 1;

  useEffect(() => { einstellen({ einfuehrungGesehen: true }); }, []);
  const schliessen = useCallback(() => nav.zurueck(), [nav]);
  useZurueck(schliessen);

  const gehe = useCallback((n: number) => setNr(Math.max(0, Math.min(ANZAHL - 1, n))), []);

  // Kurz tippen: links zurueck, rechts weiter. Nichts blaettert von allein.
  const runter = (x: number) => { druck.current = x; };
  const hoch = (breite: number) => {
    const x = druck.current; druck.current = null;
    if (x === null) return;
    gehe(x < breite / 2 ? nr - 1 : nr + 1);
  };

  const ziel = (o: Ort) => nav.ersetze(o);
  const aktion = () => {
    switch (nr) {
      case 0: case 3: gehe(nr + 1); break;
      case 1: ziel({ n: 'empfangen' }); break;
      case 2: ziel({ n: 'senden' }); break;
      case 4: nav.reiter('verlauf'); break;
      case 5: nav.reiter('kontakte'); break;
      case 6: ziel({ n: 'woerterZeigen' }); break;
      case 7: ziel({ n: 'sicherheit' }); break;
      case 8: ziel({ n: 'signieren' }); break;
      case 9: nav.reiter('einstellungen'); break;
      default: nav.reiter('start');
    }
  };

  const k = karten[nr];
  return (
    <div className={`ef ${ende ? 'ende' : ''}`} role="dialog" aria-modal="true" aria-label={t.einf.titel}>
      <div className="ef-balken" aria-hidden="true">
        {karten.map((_, i) => <i key={i}><b style={{ width: i <= nr ? '100%' : '0%' }} /></i>)}
      </div>
      <div className="ef-kopf">
        <span><YMarke g={16} farbe="var(--blau-hell)" />{t.einf.titel} · {t.einf.von(nr + 1, ANZAHL)}</span>
        {!ende && <button type="button" className="ef-ueber sinkt" onClick={schliessen}>{t.einf.ueberspringen}</button>}
        <button type="button" className="ef-zu sinkt" aria-label={t.allg.schliessen} onClick={schliessen}><S n="kreuz" g={20} w={2.4} /></button>
      </div>

      <div className="ef-flaeche"
           onPointerDown={e => runter(e.clientX - e.currentTarget.getBoundingClientRect().left)}
           onPointerUp={e => hoch(e.currentTarget.getBoundingClientRect().width)}
           onPointerLeave={() => { druck.current = null; }}>
        <div key={nr} className="ef-bild"><Bild nr={nr} t={t} sprache={sprache} /></div>
      </div>

      <div key={`t${nr}`} className="ef-text" aria-live="polite">
        <h2><Akzent k={k} /></h2>
        <p>{k.text}</p>
        <div className="ef-unten">
          <button type="button" className="ef-aktion sinkt" onClick={aktion}>{k.knopf}</button>
          {!ende && <>
            <button type="button" className="ef-pfeil sinkt" aria-label={t.einf.zurueck} disabled={nr === 0} onClick={() => gehe(nr - 1)}><S n="zurueck" g={20} w={2.2} /></button>
            <button type="button" className="ef-pfeil sinkt" aria-label={t.einf.weiter} onClick={() => gehe(nr + 1)}><S n="weiter" g={20} w={2.2} /></button>
          </>}
        </div>
        {ende && <div className="ef-ende-fuss">
          <button type="button" className="ef-verweis sinkt" onClick={() => gehe(nr - 1)}><S n="zurueck" g={16} w={2.2} />{t.einf.zurueck}</button>
          <button type="button" className="ef-verweis sinkt" onClick={() => { imBrowserOeffnen(KANAL); schliessen(); }}><S n="flieger" g={16} />{t.einf.bild.telegram}</button>
        </div>}
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
        <span className="ef-karte-text"><b>{t.einf.karteTitel} <em>{t.einf.karteAkzent}</em></b><span>{t.einf.karteText} ›</span></span>
        <span className="ef-karte-bild"><LeuchtZeichen baender={['an', 'an', 'an']} /></span>
      </button>
      <button type="button" className="ef-karte-zu" aria-label={t.allg.schliessen} onClick={() => einstellen({ einfuehrungGesehen: true })}><S n="kreuz" g={14} w={2.4} /></button>
    </div>
  );
}
