import { createContext, useCallback, useContext, useEffect, useLayoutEffect, useMemo, useRef, useState, type ReactNode } from 'react';
import { createPortal } from 'react-dom';
import QRCode from 'qrcode';
import { useT } from '../i18n/index.ts';
import { betragStellen, betragAnzeige, dezimalZeichen } from '../wallet/betrag.ts';
import { useEinstellungen } from '../wallet/einstellungen.ts';
import { vierer, kurzAdresse } from '../wallet/adresse.ts';
import { kuerzel, useKontakte } from '../wallet/kontakte.ts';
import { bestaetigungen, nichtGelistet, type Zahlung } from '../wallet/zahlungen.ts';
import * as tresor from '../wallet/tresor.ts';
import { tippGefuehl, fehlerGefuehl, beiZurueck } from '../geraet/geraet.ts';
import { useDaten, useNav, REITER, type Reiter } from '../zustand.tsx';
import { YMarke, YBaender, ZahlZeichen } from './zeichen.tsx';
import zeichenBild from '../bilder/zeichen.svg';

/* ----------------------------------------------------------------- Symbole */

const PFADE = {
  hoch: <><path d="M12 19V5" /><path d="m6 11 6-6 6 6" /></>,
  runter: <><path d="M12 5v14" /><path d="m6 13 6 6 6-6" /></>,
  scan: <><path d="M4 8V6a2 2 0 0 1 2-2h2" /><path d="M16 4h2a2 2 0 0 1 2 2v2" /><path d="M20 16v2a2 2 0 0 1-2 2h-2" /><path d="M8 20H6a2 2 0 0 1-2-2v-2" /><path d="M7 12h10" /></>,
  block: <><path d="M12 3 4 7.5v9L12 21l8-4.5v-9L12 3z" /><path d="M4 7.5 12 12l8-4.5" /><path d="M12 12v9" /></>,
  schloss: <><rect x="4" y="11" width="16" height="10" rx="2" /><path d="M8 11V7a4 4 0 0 1 8 0v4" /></>,
  kopie: <><rect x="9" y="9" width="11" height="11" rx="2" /><path d="M5 15V6a2 2 0 0 1 2-2h9" /></>,
  licht: <><path d="M8 2h8l-1 6H9z" /><path d="M9 8l1 14h4l1-14" /></>,
  auge: <><path d="M2.5 12S6 5.5 12 5.5 21.5 12 21.5 12 18 18.5 12 18.5 2.5 12 2.5 12z" /><circle cx="12" cy="12" r="3" /></>,
  augeZu: <><path d="M2.5 12S6 5.5 12 5.5 21.5 12 21.5 12 18 18.5 12 18.5 2.5 12 2.5 12z" /><circle cx="12" cy="12" r="3" /><path d="M4 4l16 16" /></>,
  zurueck: <path d="M15 5l-7 7 7 7" />,
  weiter: <path d="m9 5 7 7-7 7" />,
  pfeil: <><path d="M5 12h14" /><path d="m13 6 6 6-6 6" /></>,
  doppel: <><path d="m6 6 6 6-6 6" /><path d="m13 6 6 6-6 6" /></>,
  haus: <path d="M4 10.5 12 4l8 6.5V19a1 1 0 0 1-1 1h-4v-6h-6v6H5a1 1 0 0 1-1-1v-8.5z" />,
  leute: <><circle cx="9" cy="8.5" r="3.5" /><path d="M2.5 19.5a6.5 6.5 0 0 1 13 0" /><path d="M16 5.3a3.5 3.5 0 0 1 0 6.4" /><path d="M18.5 14.6a6.5 6.5 0 0 1 3 4.9" /></>,
  uhr: <><circle cx="12" cy="12" r="8.5" /><path d="M12 7.5V12l3 2" /></>,
  regler: <><path d="M5 7h9" /><path d="M18 7h1" /><circle cx="16" cy="7" r="2" /><path d="M5 17h1" /><path d="M10 17h9" /><circle cx="8" cy="17" r="2" /></>,
  lupe: <><circle cx="11" cy="11" r="6.5" /><path d="m20 20-4.2-4.2" /></>,
  plus: <><path d="M12 5v14" /><path d="M5 12h14" /></>,
  haken: <path d="M20 6 9 17l-5-5" />,
  einfuegen: <><rect x="6" y="5" width="12" height="16" rx="2" /><path d="M9.5 5V4a1 1 0 0 1 1-1h3a1 1 0 0 1 1 1v1" /><path d="M9.5 11h5" /><path d="M9.5 15h3" /></>,
  extern: <><path d="M14 5h5v5" /><path d="M19 5 10 14" /><path d="M19 14v4a1 1 0 0 1-1 1H6a1 1 0 0 1-1-1V6a1 1 0 0 1 1-1h4" /></>,
  info: <><circle cx="12" cy="12" r="8.5" /><path d="M12 11v5" /><path d="M12 7.6v.4" /></>,
  teilen: <><circle cx="6.5" cy="12" r="2.5" /><circle cx="17.5" cy="6" r="2.5" /><circle cx="17.5" cy="18" r="2.5" /><path d="m8.7 10.8 6.6-3.6" /><path d="m8.7 13.2 6.6 3.6" /></>,
  stift: <><path d="M4 20h4l10.5-10.5a2.1 2.1 0 0 0-3-3L5 17v3z" /><path d="m14 8 3 3" /></>,
  warnung: <><path d="M12 4 3 19h18L12 4z" /><path d="M12 10v4" /><path d="M12 16.6v.4" /></>,
  welt: <><circle cx="12" cy="12" r="8.5" /><path d="M3.5 12h17" /><path d="M12 3.5c2.5 2.6 2.5 14.4 0 17" /><path d="M12 3.5c-2.5 2.6-2.5 14.4 0 17" /></>,
  finger: <><path d="M6.5 6.2A8 8 0 0 1 20 12v1.5" /><path d="M4 12a8 8 0 0 1 .6-3" /><path d="M8 12a4 4 0 0 1 8 0v2.5c0 1.6.4 3.1 1.1 4.3" /><path d="M12 12v2.5c0 2.3.8 4.4 2 6" /><path d="M8 15.5c.3 1.9 1 3.6 2.1 5" /><path d="M4.4 15.5c.2 1.2.6 2.4 1.1 3.5" /></>,
  weg: <><path d="M8.5 5H20a1 1 0 0 1 1 1v12a1 1 0 0 1-1 1H8.5L2.5 12l6-7z" /><path d="m12 9.5 5 5" /><path d="m17 9.5-5 5" /></>,
  kreuz: <><path d="M6 6l12 12" /><path d="M18 6 6 18" /></>,
  gross: <path d="M12 4 4 12.5h4.5V19h7v-6.5H20L12 4z" />,
  tausch: <><path d="M4 7h13" /><path d="m13 3 4 4-4 4" /><path d="M20 17H7" /><path d="m11 13-4 4 4 4" /></>,
  minus: <path d="M5 12h14" />,
  glocke: <><path d="M6 16V11a6 6 0 0 1 12 0v5l1.5 2h-15L6 16z" /><path d="M10 21h4" /></>,
  // Zahlungsarten: schraege Pfeile fuer raus und rein, Schichten fuer den Pool-Anteil, ein Edelstein fuer die Blockbelohnung.
  raus: <><path d="M7 17 17 7" /><path d="M9 7h8v8" /></>,
  rein: <><path d="M17 7 7 17" /><path d="M15 17H7V9" /></>,
  pool: <><path d="m12 3 9 5-9 5-9-5 9-5z" /><path d="m3 12.5 9 5 9-5" /><path d="m3 17 9 5 9-5" /></>,
  belohnung: <><path d="M7 3h10l4 6-9 12L3 9l4-6z" /><path d="M3 9h18" /><path d="m10 3 2 6 2-6" /><path d="m12 9-3 12" /><path d="m12 9 3 12" /></>,
  kurve: <path d="M3 17l5-6 4 3 4-6 5 4" />,
  /* Leiste (Designsystem 4.2): Wallet, Aktivitaet, Kontakte, Einstellungen. */
  wallet: <><rect x="3" y="6" width="18" height="14" rx="4" /><path d="M16 13h2" /></>,
  aktivitaet: <path d="M4 12h3l3-7 4 14 3-7h3" />,
  kontakt: <><circle cx="12" cy="8" r="4" /><path d="M4 20c1.5-4 4.5-6 8-6s6.5 2 8 6" /></>,
  zahnrad: <><circle cx="12" cy="12" r="3" /><path d="M12 2.5v3M12 18.5v3M4.6 4.6l2.1 2.1M17.3 17.3l2.1 2.1M2.5 12h3M18.5 12h3M4.6 19.4l2.1-2.1M17.3 6.7l2.1-2.1" /></>,
  // Einstellungen
  schluessel: <><circle cx="8" cy="15" r="4" /><path d="m11 12 9-9" /><path d="m16 7 3 3" /><path d="m14 9 2 2" /></>,
  buch: <><path d="M4 5a2 2 0 0 1 2-2h13v16H6a2 2 0 0 0-2 2V5z" /><path d="M4 19a2 2 0 0 1 2-2h13" /></>,
  blase: <><path d="M4 5h16v11H9l-5 4V5z" /><path d="M8 9.5h8" /><path d="M8 12.5h5" /></>,
  flieger: <><path d="M21 3 3 10.5l7 2.5 2.5 7L21 3z" /><path d="m10 13 4.5-4.5" /></>,
  dokument: <><path d="M6 3h8l4 4v14H6V3z" /><path d="M14 3v4h4" /><path d="M9 12h6" /><path d="M9 16h4" /></>,
  // Signatur pruefen: Schild mit Haken.
  schild: <><path d="M12 3 5 6v5c0 4.5 3 8.2 7 10 4-1.8 7-5.5 7-10V6l-7-3z" /><path d="m9 12 2 2 4-4" /></>,
};
export type SymbolName = keyof typeof PFADE;

export function S({ n, g = 20, w = 2, klasse }: { n: SymbolName; g?: number; w?: number; klasse?: string }) {
  return (
    <svg className={klasse} width={g} height={g} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={w}
         strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">{PFADE[n]}</svg>
  );
}

/* ------------------------------------------------------------ Zurueck-Taste */

/*
  Die Zurueck-Taste von Android schliesst zuerst, was obenauf liegt (ein
  Blatt, den Scanner), sonst geht sie eine Seite zurueck.
*/
const obenauf: { h: () => void; rang: number }[] = [];

/** rang 0 = der Grund (Seitenwechsel), 1 = eine Seite oder was auf ihr liegt. */
export function useZurueck(f: () => void, aktiv = true, rang = 1): void {
  const ref = useRef(f);
  ref.current = f;
  useEffect(() => {
    if (!aktiv) return;
    const eintrag = { h: () => ref.current(), rang };
    obenauf.push(eintrag);
    return () => { const i = obenauf.indexOf(eintrag); if (i >= 0) obenauf.splice(i, 1); };
  }, [aktiv, rang]);
}

export function zurueckTasteVerbinden(sonst: () => void): () => void {
  return beiZurueck(() => {
    let oben: { h: () => void; rang: number } | null = null;
    for (const e of obenauf) if (!oben || e.rang >= oben.rang) oben = e;
    if (oben) oben.h(); else sonst();
  });
}

/* ----------------------------------------------------------------- Meldung */

const MeldungCtx = createContext<(text: string) => void>(() => {});
export const useMeldung = () => useContext(MeldungCtx);

export function MeldungProvider({ children }: { children: ReactNode }) {
  const [m, setM] = useState<{ text: string; nr: number } | null>(null);
  const zeige = useCallback((text: string) => setM(alt => ({ text, nr: (alt?.nr ?? 0) + 1 })), []);
  useEffect(() => {
    if (!m) return;
    const u = setTimeout(() => setM(null), 2200);
    return () => clearTimeout(u);
  }, [m]);
  return (
    <MeldungCtx.Provider value={zeige}>
      {children}
      {m && <div key={m.nr} className="meldung" role="status">{m.text}</div>}
    </MeldungCtx.Provider>
  );
}

/* ------------------------------------------------------------ kleine Teile */

/**
 * Ein Betrag in grosser Schrift, mit "YSR" daneben -- immer in EINER Zeile.
 * Passt er nicht in die Breite, wird die Schrift so weit kleiner, bis er passt.
 *
 * `ziel`: Zaehlt `wert` erst zu einem Endwert hoch, richten sich Schrift und
 * Platz von Anfang an nach diesem Endwert. Sonst spraenge beim Zaehlen die
 * Schriftgroesse, und alles darunter rutschte, sobald die Zahl laenger wird.
 */
export function Betrag({ wert, vz, klasse, ziel, uebersicht }: { wert: bigint; vz?: '+' | '−' | ''; klasse?: string; ziel?: bigint; uebersicht?: boolean }) {
  const t = useT();
  const z = dezimalZeichen(t.code);
  // `uebersicht`: Nachkommastellen wie eingestellt (Guthaben). Sonst exakt (Senden, Details).
  const { stellen: eingestellt } = useEinstellungen();
  const stellenWahl = uebersicht ? eingestellt : 8;
  const mass = ziel === undefined ? null : betragAnzeige(ziel, t.code, stellenWahl);
  // Unterwegs zum Endwert: so viele Nachkommastellen wie der Endwert, sonst wackelt die Breite beim Zaehlen.
  const text = useMemo(() => {
    if (mass === null || wert === ziel) return betragAnzeige(wert, t.code, stellenWahl);
    const i = mass.lastIndexOf(z);
    const stellen = i < 0 ? 0 : mass.length - i - 1;
    return betragStellen(wert, t.code, stellen);
  }, [wert, ziel, mass, z, t.code, stellenWahl]);
  const huelle = useRef<HTMLDivElement>(null);
  const gross = useRef<HTMLDivElement>(null);
  const teile = (x: string) => { const i = x.lastIndexOf(z); return i < 0 ? x : <>{x.slice(0, i)}<i>{x.slice(i)}</i></>; };

  // Gemessen wird der Text, der den Platz bestimmt: der Endwert, sonst der Wert selbst.
  const bestimmt = mass ?? text;
  useLayoutEffect(() => {
    const h = huelle.current, g = gross.current;
    if (!h || !g) return;
    const passe = () => {
      g.style.fontSize = '';
      let schrift = parseFloat(getComputedStyle(g).fontSize);
      // In Schritten: Die Breite waechst nicht genau mit der Schriftgroesse (Zeichen werden auf ganze Punkte gerundet).
      for (let i = 0; i < 16 && h.scrollWidth > h.clientWidth + 0.5 && g.offsetWidth > 0 && schrift > 16; i++) {
        const fest = h.scrollWidth - g.offsetWidth;            // "YSR" und der Abstand davor
        const gerechnet = Math.floor((schrift * (h.clientWidth - fest)) / g.offsetWidth * 2) / 2;
        schrift = Math.max(16, Math.min(schrift - 0.5, gerechnet));
        g.style.fontSize = `${schrift}px`;
      }
    };
    passe();
    // Neu messen, wenn sich die Breite aendert oder die Schrift erst spaeter geladen ist.
    let breite = h.parentElement?.clientWidth ?? 0;
    const waechter = new ResizeObserver(() => {
      const neu = h.parentElement?.clientWidth ?? 0;
      if (neu !== breite) { breite = neu; passe(); }
    });
    if (h.parentElement) waechter.observe(h.parentElement);
    let lebt = true;
    document.fonts?.ready.then(() => { if (lebt) passe(); }).catch(() => {});
    return () => { lebt = false; waechter.disconnect(); };
  }, [bestimmt, vz, klasse]);

  return (
    <div ref={huelle} className={`betrag ${klasse ?? ''}`}>
      <div ref={gross} className={`gross ${mass === null ? '' : 'zaehlt'}`}>
        {mass === null
          ? <>{vz}{teile(text)}</>
          : <><span className="mass" aria-hidden="true">{vz}{teile(mass)}</span><span className="sicht">{vz}{teile(text)}</span></>}
      </div>
      <div className="einheit">YSR</div>
    </div>
  );
}

/** Volle Adresse in Vierergruppen; Anfang und Ende sind hervorgehoben. */
export function Vierer({ adresse, klasse }: { adresse: string; klasse?: string }) {
  const g = vierer(adresse);
  return (
    <div className={`vierer waehlbar ${klasse ?? ''}`}>
      {g.map((s, i) => <span key={i} className={i < 2 || i >= g.length - 2 ? "rand" : undefined}>{s}</span>)}
    </div>
  );
}

export function Kopf({ titel, rechts }: { titel: string; rechts?: ReactNode }) {
  return <div className="kopf"><h1>{titel}</h1>{rechts && <div className="kopf-rechts">{rechts}</div>}</div>;
}

export function KopfZurueck({ titel, zurueck, rechts, mitte }: { titel?: string; zurueck: () => void; rechts?: ReactNode; mitte?: ReactNode }) {
  const t = useT();
  return (
    <div className="kopf-zurueck">
      <button type="button" className="klein sinkt" aria-label={t.allg.zurueck} onClick={zurueck}><S n="zurueck" w={2.2} /></button>
      {mitte ?? <h1>{titel}</h1>}
      {rechts}
    </div>
  );
}

export function Kasten({ art, symbol, children }: { art: 'warn' | 'ruhig' | 'rot'; symbol: SymbolName; children: ReactNode }) {
  return <div className={`kasten ${art}`}><S n={symbol} /><div>{children}</div></div>;
}

/** Vier ruhige Farbtoene, fest je Name -- derselbe Kontakt sieht ueberall gleich aus. */
function ton(name: string): string {
  let h = 0;
  for (const c of name) h = (h * 31 + c.codePointAt(0)!) >>> 0;
  const n = h % 4;
  return n === 0 ? '' : `t${n}`;
}

export function Bild({ name, g = 40 }: { name: string; g?: number }) {
  return <span className={`bild ${ton(name)}`} aria-hidden="true" style={{ width: g, height: g, fontSize: Math.round(g * 0.35) }}>{kuerzel(name)}</span>;
}

export function Wartering({ gross }: { gross?: boolean }) {
  const g = gross ? 68 : 44, r = gross ? 30 : 19.5, b = gross ? 4 : 3, u = 2 * Math.PI * r;
  return (
    <div className={`wartering ${gross ? 'gross' : ''}`}>
      <svg className="ring" width={g} height={g} viewBox={`0 0 ${g} ${g}`} aria-hidden="true">
        <circle cx={g / 2} cy={g / 2} r={r} fill="none" stroke="#3A3E45" strokeWidth={b} />
        <circle cx={g / 2} cy={g / 2} r={r} fill="none" stroke="#F5B65B" strokeWidth={b} strokeLinecap="round" strokeDasharray={`${u * 0.28} ${u}`} />
      </svg>
      <S n="hoch" g={gross ? 26 : 16} w={2.2} />
    </div>
  );
}

/** Schritt n von 3 als drei verjuengte Baender (Senden, 4.2). */
export function Schritte({ n, label }: { n: 1 | 2 | 3; label: string }) {
  return (
    <span className="schritte" role="img" aria-label={label}>
      {[1, 2, 3].map(i => <i key={i} className={i <= n ? 'an' : undefined} />)}
    </span>
  );
}

/** Wer oder was steht in der Zeile: Name des Kontakts, sonst die gekuerzte Adresse. */
export function useWer() {
  const { name } = useKontakte();
  return (adresse: string | null): string => name(adresse) ?? kurzAdresse(adresse, '?');
}

export function useZeit() {
  const t = useT();
  return useMemo(() => {
    const uhr = new Intl.DateTimeFormat(t.code, { hour: '2-digit', minute: '2-digit' });
    const tagKurz = new Intl.DateTimeFormat(t.code, { day: 'numeric', month: 'short' });
    const tagLang = new Intl.DateTimeFormat(t.code, { day: 'numeric', month: 'long' });
    const jahr = new Intl.DateTimeFormat(t.code, { day: 'numeric', month: 'long', year: 'numeric' });
    const zahl = new Intl.NumberFormat(t.code);
    const tageHer = (s: number) => {
      const d = new Date(s * 1000), h = new Date();
      return Math.round((new Date(h.getFullYear(), h.getMonth(), h.getDate()).getTime()
        - new Date(d.getFullYear(), d.getMonth(), d.getDate()).getTime()) / 86_400_000);
    };
    return {
      uhr: (s: number) => uhr.format(new Date(s * 1000)),
      /** "heute, 09:12" / "gestern" / "3. Okt." */
      kurz: (s: number) => { const n = tageHer(s); return n === 0 ? `${t.allg.heute.toLowerCase()}, ${uhr.format(new Date(s * 1000))}` : n === 1 ? t.allg.gestern.toLowerCase() : tagKurz.format(new Date(s * 1000)); },
      /** Ueberschrift eines Tages: "Heute" / "Gestern" / "3. Oktober" */
      tag: (s: number) => { const n = tageHer(s); const d = new Date(s * 1000); return n === 0 ? t.allg.heute : n === 1 ? t.allg.gestern : d.getFullYear() === new Date().getFullYear() ? tagLang.format(d) : jahr.format(d); },
      zahl: (n: number) => zahl.format(n),
    };
  }, [t]);
}

/**
 * Eine Zahlung in der Liste. Ein bekannter Kontakt steht mit seinen
 * Initialen da, alles andere mit einem runden Symbol. Eine wartende Zahlung
 * traegt eine kleine Pille statt eines Satzes.
 */
export function Zahlungszeile({ z, onTipp, mitUhr }: { z: Zahlung; onTipp: () => void; mitUhr?: boolean }) {
  const t = useT();
  const { name } = useKontakte();
  const { spitze } = useDaten();
  const zeit = useZeit();
  const ein = z.art !== 'aus';
  const belohnung = z.art === 'belohnung' || z.art === 'pool';
  const kontakt = z.gegen ? name(z.gegen) : null;
  const anzeige = useAnzeige();
  const titel = z.art === 'belohnung' ? t.zahlung.belohnung : z.art === 'pool' ? t.zahlung.pool
    : kontakt ?? kurzAdresse(z.gegen, '?');
  const wartet = z.hoehe === null;
  const weg = nichtGelistet(z);
  const wann = z.zeit === null ? '' : mitUhr ? zeit.uhr(z.zeit) : zeit.kurz(z.zeit);
  // Zweite Angabe (4.2): Notiz, sonst bei Mining der Block (in der Aktivitaet beim Pool die Zahl der Miner),
  // in der Aktivitaet bei Zahlungen die Bestaetigungen.
  const n = bestaetigungen(z, spitze);
  const extra = z.notiz ? t.zahlung.notiz(z.notiz)
    : z.art === 'pool' && mitUhr && z.anteile ? t.zahlung.miner(zeit.zahl(z.anteile))
    : belohnung ? t.zahlung.block(zeit.zahl(z.hoehe ?? 0))
    : mitUhr && n > 0 ? t.zahlung.bestaetigungen(zeit.zahl(n)) : '';
  const zusatz = [wann, extra].filter(Boolean).join(' · ');
  // Zeichen nach Art der Zahlung (Designsystem 4.2): verjuengte Pfeile, Pool-Anteil als drei zusammenlaufende Baender.
  const art = z.art === 'pool' ? 'pool' : z.art === 'belohnung' ? 'belohnung' : ein ? 'ein' : 'aus';
  const ton = wartet ? 'bern' : belohnung ? 'block' : ein ? 'ein' : 'aus';
  return (
    <button type="button" className="zeile" onClick={onTipp}>
      <div className={`kachel art ${ton}`}><ZahlZeichen art={art} /></div>
      <div className="mitte">
        <b>{titel}</b>
        <small>{wartet
          ? <><YBaender stand={['erledigt', 'wartet', 'offen']} label={weg ? t.zahlung.nichtGelistet : t.zahlung.wartetBlock} />
              <span className="wartet">{weg ? t.zahlung.nichtGelistet : t.zahlung.wartetBlock}</span></>
          : zusatz}</small>
      </div>
      <span className="rechts">
        <span className={`wert ${ein ? 'plus' : ''}`}>{ein ? '+' : '−'}<Stellen text={anzeige(z.betrag)} /></span>
      </span>
    </button>
  );
}

/** Betrag in einer Zeile: die Nachkommastellen blasser, damit der ganze Teil sofort ins Auge faellt. */
/** Betrag fuer die Uebersicht mit den eingestellten Nachkommastellen (abgeschnitten). */
export function useAnzeige(): (wert: bigint) => string {
  const t = useT();
  const { stellen } = useEinstellungen();
  return (wert: bigint) => betragAnzeige(wert, t.code, stellen);
}

export function Stellen({ text }: { text: string }) {
  const t = useT();
  const i = text.lastIndexOf(dezimalZeichen(t.code));
  return i < 0 ? <>{text}</> : <>{text.slice(0, i + 1)}<i className="nk">{text.slice(i + 1)}</i></>;
}

/* ------------------------------------------------------------- Navigation */

const REITER_BILD: Record<Reiter, SymbolName> = { start: 'wallet', verlauf: 'aktivitaet', kontakte: 'kontakt', einstellungen: 'zahnrad' };

export function Nav() {
  const t = useT();
  const nav = useNav();
  const namen: Record<Reiter, string> = { start: t.start.titel, kontakte: t.kontakte.titel, verlauf: t.verlauf.titel, einstellungen: t.einst.kurz };
  return (
    <>
      <div className="schleier" aria-hidden="true" />
      <nav className="nav">
        {REITER.map(r => {
          const an = nav.ort.n === r;
          return (
            <button key={r} type="button" aria-current={an ? 'page' : undefined}
                    onClick={() => { if (!an) nav.reiter(r); }}>
              <S n={REITER_BILD[r]} g={20} w={1.6} /><span>{namen[r]}</span>
            </button>
          );
        })}
      </nav>
    </>
  );
}

/* ------------------------------------------------------------------ Blatt */

export function Blatt({ titel, onZu, children }: { titel?: string; onZu: () => void; children: ReactNode }) {
  useZurueck(onZu);
  return obenauflegen(
    <div className="blatt-grund" onClick={e => { if (e.target === e.currentTarget) onZu(); }}>
      <div className="blatt" role="dialog" aria-modal="true" aria-label={titel}>
        {titel && <h2>{titel}</h2>}
        {children}
      </div>
    </div>,
  );
}

/** Ueber die ganze App legen, unabhaengig davon, wie weit die Seite gerollt ist. */
export function obenauflegen(inhalt: ReactNode) {
  return createPortal(inhalt, document.getElementById('ebene') ?? document.body);
}

/* ------------------------------------------------------------------- Logo */

/** Die Muenze der Website: dunkle Scheibe, blauer Ring, das Zeichen. Alles CSS -- siehe .muenze. */
export function Muenze({ groesse = 'gross', spielt }: { groesse?: 'gross' | 'mittel' | 'klein'; spielt?: boolean }) {
  return (
    <div className={`muenze ${groesse} ${spielt ? 'spielt' : ''}`} role="img" aria-label="YSKAR">
      <img src={zeichenBild} alt="" draggable={false} />
    </div>
  );
}

/** Das Zeichen allein, z. B. in einer Kachel. */
export function Zeichen({ g = 24 }: { g?: number }) {
  return <img src={zeichenBild} alt="" width={g} height={g} draggable={false} />;
}

/* -------------------------------------------------------------------- PIN */

export type PinAntwort = 'ok' | 'falsch' | 'gebremst' | { fehler: string };

/**
 * Sechs Ziffern ueber das eigene Tastenfeld. Die Systemtastatur bleibt zu:
 * So liest keine fremde Tastatur-App mit.
 */
export function PinFeld({ titel, unter, logo, onPin, bio, fuss, zurueck, kopf, oben }: {
  titel: string; unter?: string; logo?: boolean;
  /** Titel in der Kopfzeile neben "Zurueck" (nur mit `zurueck`). */
  kopf?: string;
  /** Steht oben unter der Kopfzeile; Titel und Punkte folgen direkt darunter, die Tasten unten (Senden bestaetigen, 4.2). */
  oben?: ReactNode;
  onPin: (pin: string) => Promise<PinAntwort>;
  /** Wenn gesetzt, zeigt das Feld die Fingerabdruck-Taste. */
  bio?: () => void;
  fuss?: ReactNode;
  zurueck?: () => void;
}) {
  const t = useT();
  const [pin, setPin] = useState('');
  const [stand, setStand] = useState<'frei' | 'prueft' | 'gut' | 'falsch'>('frei');
  const [fehler, setFehler] = useState('');
  const [warten, setWarten] = useState(tresor.wartezeit());

  useEffect(() => {
    if (warten <= 0) return;
    const u = setInterval(() => setWarten(tresor.wartezeit()), 500);
    return () => clearInterval(u);
  }, [warten]);

  const pruefen = async (fertig: string) => {
    setStand('prueft');
    // Erst zeichnen lassen, dann rechnen: Das Oeffnen des Tresors dauert spuerbar.
    await new Promise(r => setTimeout(r, 30));
    const r = await onPin(fertig);
    if (r === 'ok') { setStand('gut'); return; }
    fehlerGefuehl();
    setStand('falsch');
    setFehler(typeof r === 'object' ? r.fehler : r === 'falsch' ? t.pin.falsch : '');
    setWarten(tresor.wartezeit());
    setTimeout(() => { setPin(''); setStand('frei'); }, 420);
  };

  const tipp = (z: string) => {
    if (stand !== 'frei' || warten > 0 || pin.length >= 6) return;
    tippGefuehl();
    setFehler('');
    const neu = pin + z;
    setPin(neu);
    if (neu.length === 6) pruefen(neu);
  };

  // Blass sind die Tasten nur, solange die Bremse laeuft. Waehrend die PIN geprueft wird,
  // nehmen sie nichts an (siehe tipp), bleiben aber, wie sie sind -- sonst flackert das Feld bei jedem Entsperren.
  const gesperrt = warten > 0;
  const frei = stand === 'frei' && warten <= 0;
  return (
    <div className="seite pinseite">
      {zurueck && <div style={{ alignSelf: 'stretch', margin: '0 -12px' }}><KopfZurueck titel={kopf ?? ''} zurueck={zurueck} /></div>}
      {/* Der freie Platz liegt oben: mit Logo fuellt es ihn, ohne bleibt er leer. Titel und Punkte stehen dicht ueber den Tasten.
          Mit `oben` steht alles oben, und der freie Platz liegt ueber den Tasten. */}
      {oben ? <div className="pin-oben">{oben}</div> : logo ? <div className="logo-platz"><Muenze groesse="mittel" /></div> : <div className="fuell" />}
      <h1>{titel}</h1>
      {unter && <p className="unter">{unter}</p>}
      <div className={`punkte ${stand === 'gut' ? 'gut' : ''} ${stand === 'falsch' ? 'falsch' : ''}`} role="img" aria-label={t.pin.stellen(pin.length)}>
        {Array.from({ length: 6 }, (_, i) => <span key={i} className={i < pin.length ? 'voll' : undefined} />)}
      </div>
      <div className="fehlertext" role="alert" style={{ textAlign: 'center' }}>
        {warten > 0 ? t.pin.gebremst(warten) : stand === 'prueft' ? <span style={{ color: 'var(--leise)' }}>{t.pin.pruefe}</span> : fehler}
      </div>
      {oben && <div className="fuell" />}
      <div className="tasten">
        {['1', '2', '3', '4', '5', '6', '7', '8', '9'].map(z =>
          <button key={z} type="button" className="taste sinkt" disabled={gesperrt} onClick={() => tipp(z)}>{z}</button>)}
        {bio
          ? <button type="button" className="taste leer finger sinkt" aria-label={t.pin.finger} disabled={gesperrt} onClick={() => { if (frei) bio(); }}><S n="finger" g={30} w={1.8} /></button>
          : <span />}
        <button type="button" className="taste sinkt" disabled={gesperrt} onClick={() => tipp('0')}>0</button>
        <button type="button" className="taste leer sinkt" aria-label={t.pin.loeschen} disabled={gesperrt || !pin}
                onClick={() => { if (!frei) return; tippGefuehl(); setPin(p => p.slice(0, -1)); }}><S n="weg" g={28} w={1.8} /></button>
      </div>
      {fuss ?? <div style={{ height: 44 }} />}
    </div>
  );
}

/* --------------------------------------------------------------- Schieber */

/**
 * Zum Senden schieben -- der einzige Weg zu senden. Ausgeloest wird nur am
 * Ende der Bahn; wer frueher loslaesst, sendet nichts und der Knopf federt
 * zurueck. Antippen tut nichts. Fuer Vorlesefunktion und Tastatur gibt es
 * einen unsichtbaren Regler in vier Schritten; erst der letzte sendet.
 */
export function Schieber({ text, label, onFertig, gesperrt }: { text: string; label: string; onFertig: () => void; gesperrt?: boolean }) {
  const t = useT();
  const bahn = useRef<HTMLDivElement>(null);
  const [lage, setLage] = useState(0);
  const [art, setArt] = useState<'' | 'zurueck' | 'fertig'>('');
  const [stufe, setStufe] = useState(0);
  const zug = useRef({ an: false, x0: 0, lage0: 0 });
  const weg = () => Math.max(1, (bahn.current?.clientWidth ?? 350) - 12 - 56);

  const fertig = () => { setArt('fertig'); setLage(weg()); tippGefuehl(); onFertig(); };

  // Nur Schieben bis ans Ende loest aus (Entscheidung 01.11.2026): Antippen tut nichts.
  const runter = (e: React.PointerEvent) => {
    if (gesperrt || art === 'fertig') return;
    zug.current = { an: true, x0: e.clientX, lage0: lage };
    setArt('');
    try { e.currentTarget.setPointerCapture(e.pointerId); } catch { /* egal */ }
  };
  const bewegt = (e: React.PointerEvent) => {
    const z = zug.current;
    if (!z.an) return;
    setLage(Math.max(0, Math.min(weg(), z.lage0 + e.clientX - z.x0)));
  };
  const los = () => {
    const z = zug.current;
    if (!z.an) return;
    z.an = false;
    if (lage >= weg() * 0.98) fertig();
    else { setArt('zurueck'); setLage(0); }
  };
  // Bildschirmleser und Tastatur: ein Regler in vier Schritten. Erst der letzte Schritt sendet --
  // auch hier braucht es mehrere bewusste Bewegungen, ein einzelnes Tippen genuegt nie.
  const regler = (gewuenscht: number) => {
    if (gesperrt || art === 'fertig') return;
    // Hoechstens eine Stufe je Bewegung: Ende-, Bild- oder Sprungtasten koennen nicht direkt senden.
    const wert = Math.min(gewuenscht, stufe + 25);
    setStufe(wert);
    if (wert >= 100) fertig(); else { setArt(''); setLage(weg() * wert / 100); }
  };

  const w = weg();
  return (
    <div ref={bahn} className={`schieber ${art} ${gesperrt ? 'gesperrt' : ''}`}>
      <div className="spur" style={{ width: lage + 62 }} />
      <div className="wort-mitte" aria-hidden="true" style={{ opacity: Math.max(0, 1 - (lage / w) * 1.6) }}>{text}</div>
      <div className="pfeile" aria-hidden="true"><S n="weiter" g={18} /></div>
      <div className="knauf" aria-hidden="true" style={{ transform: `translateX(${lage}px)` }}
           onPointerDown={runter} onPointerMove={bewegt} onPointerUp={los} onPointerCancel={los}>
        {art === 'fertig' ? <S n="haken" g={24} w={2.4} /> : <YMarke g={28} farbe="#fff" />}
      </div>
      <input type="range" className="nur-leser" min={0} max={100} step={25} value={stufe} aria-label={label}
             aria-valuetext={t.senden.schiebenStufe(stufe / 25)}
             disabled={gesperrt || art === 'fertig'} onChange={e => regler(Number(e.target.value))} />
    </div>
  );
}

/* --------------------------------------------------------------------- QR */

/** Der QR-Code mit dem Logo in der Mitte. Stufe H verkraftet die verdeckten Module. */
export function QrBild({ text, label }: { text: string; label: string }) {
  const { n, d } = useMemo(() => {
    const q = QRCode.create(text, { errorCorrectionLevel: 'H' });
    const groesse = q.modules.size, daten = q.modules.data;
    let pfad = '';
    for (let y = 0; y < groesse; y++) {
      for (let x = 0; x < groesse;) {
        if (!daten[y * groesse + x]) { x++; continue; }
        const s = x;
        while (x < groesse && daten[y * groesse + x]) x++;
        pfad += `M${s} ${y}h${x - s}v1h-${x - s}z`;
      }
    }
    return { n: groesse, d: pfad };
  }, [text]);
  return (
    <div className="qrplatte">
      <svg viewBox={`0 0 ${n} ${n}`} shapeRendering="crispEdges" role="img" aria-label={label}><path fill="#06112A" d={d} /></svg>
      <span className="qr-logo" aria-hidden="true"><img src={zeichenBild} alt="" draggable={false} /></span>
    </div>
  );
}
