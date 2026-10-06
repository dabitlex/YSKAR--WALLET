import { createContext, useCallback, useContext, useEffect, useLayoutEffect, useMemo, useRef, useState, type ReactNode } from 'react';
import { useT } from '../i18n/index.ts';
import { dezimalZeichen } from '../wallet/betrag.ts';
import { LOESCHEN, ADRESS_ZEICHEN, grossAmAnfang } from '../wallet/tasten.ts';
import { tippGefuehl, einfuegen as ausZwischenablage } from '../geraet/geraet.ts';
import { S, obenauflegen, useZurueck } from './teile.tsx';

/*
  Die Tastatur der App.

  Kein Eingabefeld der Wallet oeffnet die Tastatur des Telefons. Getippt wird
  ueber dieses Feld am unteren Rand, wie bei der PIN:

    ziffern   Betraege: 0-9 und das Trennzeichen der Sprache
    adresse   nur die Zeichen, die in einer YSKAR-Adresse vorkommen
    wort      a-z fuer die 12 Woerter, mit Vorschlaegen aus der Wortliste
    text      Notiz, Name, Suche: Buchstaben, Ziffern und Satzzeichen

  So liest keine fremde Tastatur-App mit, was hier getippt wird, und nichts
  davon landet in deren Woerterbuch oder Vorschlagsleiste.

  Die Felder selbst sind keine <input>-Elemente: Ein Tippfeld zeigt seinen
  Wert an und meldet der Seite jede Taste. Geschrieben wird am Ende, geloescht
  von hinten.
*/

export type TastArt = 'ziffern' | 'adresse' | 'wort' | 'text';

interface Stand {
  aktiv: string | null;
  hoehe: number;
  oeffne: (id: string | null) => void;
  /** Schliessen, aber nur, wenn dieses Feld gerade dran ist. */
  lasse: (id: string) => void;
  melde: (hoehe: number) => void;
  /** Stand vor diesem Zeichnen schon eine Tastatur da? Dann faehrt die neue nicht noch einmal herein. */
  stand: { current: boolean };
}

const Ctx = createContext<Stand>({ aktiv: null, hoehe: 0, oeffne: () => {}, lasse: () => {}, melde: () => {}, stand: { current: false } });

/** Welches Feld gerade getippt wird, und ein Feld von aussen oeffnen oder schliessen. */
export function useTastatur(): { aktiv: string | null; oeffne: (id: string | null) => void } {
  const s = useContext(Ctx);
  return useMemo(() => ({ aktiv: s.aktiv, oeffne: s.oeffne }), [s.aktiv, s.oeffne]);
}

/**
 * Der Rahmen der App. Solange getippt wird, endet der Inhalt ueber der
 * Tastatur (--tastatur), damit sich jedes Feld in den sichtbaren Teil
 * rollen laesst.
 */
export function TastaturRahmen({ children }: { children: ReactNode }) {
  const [aktiv, setAktiv] = useState<string | null>(null);
  const [hoehe, setHoehe] = useState(0);
  const stand = useRef(false);
  const seit = useRef(0);
  const aktivRef = useRef(aktiv);
  aktivRef.current = aktiv;
  useEffect(() => { stand.current = aktiv !== null; });

  const oeffne = useCallback((id: string | null) => { seit.current = performance.now(); setAktiv(id); }, []);
  const lasse = useCallback((id: string) => setAktiv(a => (a === id ? null : a)), []);

  // Ein Tipp neben Feld und Tastatur schliesst sie. "click" statt "pointerdown":
  // Die Seite wird erst danach wieder groesser, der getippte Knopf rutscht also
  // nicht unter dem Finger weg. Der Tipp, der die Tastatur geoeffnet hat, zaehlt nicht.
  useEffect(() => {
    const h = (e: MouseEvent) => {
      if (aktivRef.current === null || e.timeStamp <= seit.current) return;
      const ziel = e.target instanceof Element ? e.target : null;
      if (ziel?.closest('.tastatur, [data-tippfeld]')) return;
      setAktiv(null);
    };
    document.addEventListener('click', h);
    return () => document.removeEventListener('click', h);
  }, []);

  const wert = useMemo<Stand>(() => ({ aktiv, hoehe, oeffne, lasse, melde: setHoehe, stand }), [aktiv, hoehe, oeffne, lasse]);
  return (
    <Ctx.Provider value={wert}>
      <div className={`app ${aktiv !== null ? 'tippt' : ''}`} style={{ ['--tastatur' as string]: `${aktiv !== null ? hoehe : 0}px` }}>
        {children}
      </div>
    </Ctx.Provider>
  );
}

/* --------------------------------------------------------------- Tippfeld */

/**
 * Ein Eingabefeld ohne Systemtastatur. Die Seite haelt den Wert und bekommt
 * jede Taste ueber `onTaste` (ein Zeichen oder LOESCHEN).
 */
export function Tippfeld({ id, art, wert, onTaste, label, klasse, platz, vor, zeige, leiste, weiter, gross, einfuegen, auto }: {
  id: string;
  art: TastArt;
  wert: string;
  onTaste: (taste: string) => void;
  label: string;
  klasse?: string;
  /** Blasser Text, solange das Feld leer ist. */
  platz?: string;
  /** Steht vor dem Wert, zum Beispiel die Nummer eines Wortes. */
  vor?: ReactNode;
  /** Wie der Wert aussieht, wenn nicht einfach als Text. */
  zeige?: ReactNode;
  /** Links in der Leiste ueber den Tasten: Vorschlaege, "Leeren". */
  leiste?: ReactNode;
  /** Wenn gesetzt, heisst der Knopf rechts "Weiter" und ruft das hier. */
  weiter?: () => void;
  gross?: 'anfang' | 'woerter';
  /** Wenn gesetzt, gibt es in der Leiste einen Knopf zum Einfuegen. */
  einfuegen?: (text: string) => void;
  /** Die Tastatur gleich oeffnen, wenn das Feld erscheint. */
  auto?: boolean;
}) {
  const tast = useContext(Ctx);
  const aktiv = tast.aktiv === id;
  const ref = useRef<HTMLDivElement>(null);
  const { oeffne, lasse } = tast;

  useEffect(() => {
    if (auto) oeffne(id);
    return () => lasse(id);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [id]);

  // Das Feld, in das getippt wird, muss ueber der Tastatur zu sehen sein.
  useEffect(() => {
    if (!aktiv) return;
    const u = setTimeout(() => ref.current?.scrollIntoView({ block: 'nearest', behavior: 'smooth' }), 60);
    return () => clearTimeout(u);
  }, [aktiv, tast.hoehe]);

  const strich = aktiv ? <i className="strich" aria-hidden="true" /> : null;
  return (
    <>
      <div ref={ref} id={id} role="textbox" tabIndex={0} aria-label={label} data-tippfeld=""
           className={`tippfeld ${klasse ?? ''} ${aktiv ? 'aktiv' : ''}`} onClick={() => { if (!aktiv) oeffne(id); }}>
        {vor}
        {wert === '' ? <>{strich}{platz && <span className="platz">{platz}</span>}</> : <>{zeige ?? wert}{strich}</>}
      </div>
      {aktiv && obenauflegen(
        <Tastatur art={art} wert={wert} onTaste={onTaste} leiste={leiste} weiter={weiter} gross={gross} onEinfuegen={einfuegen} onZu={() => lasse(id)} />,
      )}
    </>
  );
}

/** Ein langer Betrag wird kleiner geschrieben, damit er ganz ins Feld passt. */
export function betragKlasse(text: string): string {
  return text.length > 17 ? 'lang3' : text.length > 13 ? 'lang2' : text.length > 9 ? 'lang1' : '';
}

/* --------------------------------------------------------------- Tastatur */

const BUCHSTABEN = {
  de: ['qwertzuiop', 'asdfghjkl', 'yxcvbnm'],
  en: ['qwertyuiop', 'asdfghjkl', 'zxcvbnm'],
};
/** Fuer freien Text hat die deutsche Tastatur die Umlaute und das scharfe S. */
const TEXT = {
  de: ['qwertzuiopü', 'asdfghjklöä', 'yxcvbnmß'],
  en: BUCHSTABEN.en,
};
const ZEICHEN = ['1234567890', '-/:;()€&@"', '.,?!\'#+*%'];
const ADRESS_ZIFFERN = '023456789';

/** Was eine Taste einer echten Tastatur (Bluetooth, Rechner) hier bedeutet; null = nichts. */
function vonAussen(art: TastArt, taste: string, zeichen: string): string | null {
  if (art === 'ziffern') return /^\d$/.test(taste) ? taste : taste === ',' || taste === '.' ? zeichen : null;
  const klein = taste.toLowerCase();
  if (art === 'adresse') return ADRESS_ZEICHEN.includes(klein) ? klein : null;
  if (art === 'wort') return /^[a-z]$/.test(klein) ? klein : null;
  return taste;
}

function Tastatur({ art, wert, onTaste, leiste, weiter, gross, onEinfuegen, onZu }: {
  art: TastArt; wert: string; onTaste: (taste: string) => void; leiste?: ReactNode; weiter?: () => void;
  gross?: 'anfang' | 'woerter'; onEinfuegen?: (text: string) => void; onZu: () => void;
}) {
  const t = useT();
  const tast = useContext(Ctx);
  const ref = useRef<HTMLDivElement>(null);
  const [steht] = useState(tast.stand.current);
  const [ebene, setEbene] = useState<'abc' | '123'>('abc');
  const [umschalt, setUmschalt] = useState<boolean | null>(null);
  const sprache = t.code.startsWith('de') ? 'de' : 'en';
  const zeichen = dezimalZeichen(t.code);

  useZurueck(onZu, true, 2);
  const { melde } = tast;
  useLayoutEffect(() => { melde(ref.current?.offsetHeight ?? 0); });

  const hoch = art === 'text' && ebene === 'abc' && (umschalt ?? grossAmAnfang(wert, gross));
  const druck = (z: string) => { tippGefuehl(); onTaste(z); if (z !== LOESCHEN) setUmschalt(null); };
  const naechstes = weiter ?? onZu;

  // Eine angeschlossene Tastatur tippt in dasselbe Feld.
  const jetzt = useRef({ art, zeichen, onTaste, naechstes, onZu });
  jetzt.current = { art, zeichen, onTaste, naechstes, onZu };
  useEffect(() => {
    const h = (e: KeyboardEvent) => {
      if (e.ctrlKey || e.metaKey || e.altKey) return;
      const j = jetzt.current;
      if (e.key === 'Escape') { j.onZu(); return; }
      if (e.key === 'Enter' || (e.key === ' ' && j.art === 'wort')) { e.preventDefault(); j.naechstes(); return; }
      const z = e.key === 'Backspace' ? LOESCHEN : e.key.length === 1 ? vonAussen(j.art, e.key, j.zeichen) : null;
      if (z === null) return;
      e.preventDefault();
      j.onTaste(z);
    };
    window.addEventListener('keydown', h);
    return () => window.removeEventListener('keydown', h);
  }, []);

  const zeichentaste = (z: string) => {
    const gezeigt = hoch && z !== 'ß' ? z.toUpperCase() : z;
    return <Taste key={z} zeige={gezeigt} vorschau={art === 'ziffern' ? undefined : gezeigt} onDruck={() => druck(gezeigt)} />;
  };
  const luecke = (breit: number, k: string) => <i key={k} className="tluecke" style={{ flexGrow: breit }} />;
  const weg = (breit = 1.5) =>
    <Taste key="weg" klasse="dunkel" breit={breit} wiederholt label={t.tast.loeschen} zeige={<S n="weg" g={24} w={1.8} />} onDruck={() => druck(LOESCHEN)} />;

  let reihen: ReactNode;
  if (art === 'ziffern') {
    reihen = (
      <div className="tblock ziffernblock">
        {['123', '456', '789'].map(r => <div key={r} className="treihe">{[...r].map(zeichentaste)}</div>)}
        <div className="treihe">
          <Taste klasse="dunkel" zeige={zeichen} label={t.tast.trennzeichen} onDruck={() => druck(zeichen)} />
          {zeichentaste('0')}
          {weg(1)}
        </div>
      </div>
    );
  } else if (art === 'adresse') {
    // Die gewohnte Anordnung, ohne die Zeichen, die es in einer Adresse nicht gibt (1, b, i, o).
    const r = BUCHSTABEN[sprache].map(x => [...x].filter(z => ADRESS_ZEICHEN.includes(z)));
    reihen = (
      <div className="tblock">
        <div className="treihe">{luecke(.5, 'l')}{[...ADRESS_ZIFFERN].map(zeichentaste)}{luecke(.5, 'r')}</div>
        <div className="treihe">{luecke(1, 'l')}{r[0].map(zeichentaste)}{luecke(1, 'r')}</div>
        <div className="treihe">{luecke(.5, 'l')}{r[1].map(zeichentaste)}{luecke(.5, 'r')}</div>
        <div className="treihe">{luecke(1.5, 'l')}{r[2].map(zeichentaste)}{luecke(1, 'r')}{weg()}</div>
      </div>
    );
  } else if (art === 'wort') {
    const r = BUCHSTABEN[sprache];
    reihen = (
      <div className="tblock">
        <div className="treihe">{[...r[0]].map(zeichentaste)}</div>
        <div className="treihe">{luecke(.5, 'l')}{[...r[1]].map(zeichentaste)}{luecke(.5, 'r')}</div>
        <div className="treihe">{luecke(1.5, 'l')}{[...r[2]].map(zeichentaste)}{weg()}</div>
      </div>
    );
  } else if (ebene === 'abc') {
    const r = TEXT[sprache];
    const breite = r[0].length;
    reihen = (
      <div className="tblock">
        <div className="treihe">{[...r[0]].map(zeichentaste)}</div>
        <div className="treihe">{r[1].length < breite && luecke(.5, 'l')}{[...r[1]].map(zeichentaste)}{r[1].length < breite && luecke(.5, 'r')}</div>
        <div className="treihe">
          <Taste klasse={`dunkel ${hoch ? 'an' : ''}`} breit={1.5} label={t.tast.gross} gedrueckt={hoch} zeige={<S n="gross" g={22} w={1.9} />}
                 onDruck={() => { tippGefuehl(); setUmschalt(!hoch); }} />
          {[...r[2]].map(zeichentaste)}
          {weg()}
        </div>
        <div className="treihe">
          <Taste klasse="dunkel klein-schrift" breit={1.5} label={t.tast.zeichen} zeige="123" onDruck={() => { tippGefuehl(); setEbene('123'); }} />
          {zeichentaste(',')}
          <Taste klasse="leertaste" breit={breite - 3.5} label={t.tast.leer} zeige="" onDruck={() => druck(' ')} />
          {zeichentaste('.')}
        </div>
      </div>
    );
  } else {
    reihen = (
      <div className="tblock">
        <div className="treihe">{[...ZEICHEN[0]].map(zeichentaste)}</div>
        <div className="treihe">{[...ZEICHEN[1]].map(zeichentaste)}</div>
        <div className="treihe">{[...ZEICHEN[2]].map(zeichentaste)}{weg(1)}</div>
        <div className="treihe">
          <Taste klasse="dunkel klein-schrift" breit={1.5} label={t.tast.buchstaben} zeige="ABC" onDruck={() => { tippGefuehl(); setEbene('abc'); }} />
          {zeichentaste('_')}
          <Taste klasse="leertaste" breit={6.5} label={t.tast.leer} zeige="" onDruck={() => druck(' ')} />
          {zeichentaste('=')}
        </div>
      </div>
    );
  }

  return (
    <div ref={ref} className={`tastatur t-${art} ${steht ? 'steht' : ''}`} role="group" aria-label={t.tast.name}>
      <div className="tleiste">
        <div className="tlinks">
          {onEinfuegen && (
            <button type="button" className="tknopf" aria-label={t.allg.einfuegen}
                    onClick={async () => { const x = await ausZwischenablage(); if (x) onEinfuegen(x); }}>
              <S n="einfuegen" g={18} />
            </button>
          )}
          {leiste}
        </div>
        <button type="button" className="tfertig" onClick={naechstes}>{weiter ? t.allg.weiter : t.allg.fertig}</button>
      </div>
      {reihen}
    </div>
  );
}

/*
  Eine Taste loest beim Beruehren aus, nicht erst beim Loslassen. Den Klick,
  den der Browser nach dem Loslassen nachschickt, darf dann nichts mehr
  bekommen: Ist die Tastatur inzwischen zu (das zwoelfte Wort ist fertig)
  oder steht eine andere da, traefe er den Knopf, der an dieser Stelle
  darunter liegt. Deshalb wird genau dieser eine Klick verschluckt.
*/
let beruehrt = false;
let schluckBis = 0;
let schluckOrt = { x: 0, y: 0 };
let letzterDruck = 0;
if (typeof window !== 'undefined') {
  const los = (e: PointerEvent) => {
    if (!beruehrt) return;
    beruehrt = false;
    schluckBis = performance.now() + 300;
    schluckOrt = { x: e.clientX, y: e.clientY };
  };
  window.addEventListener('pointerup', los, true);
  window.addEventListener('pointercancel', los, true);
  window.addEventListener('click', e => {
    // detail 0: von Vorlesefunktion oder Eingabetaste ausgeloest, nicht von einer Beruehrung.
    if (e.detail === 0 || performance.now() >= schluckBis) return;
    // Der Nachklick kommt an der Stelle der Beruehrung. Ein schneller Tipp woanders ist ein eigener.
    if (Math.abs(e.clientX - schluckOrt.x) > 30 || Math.abs(e.clientY - schluckOrt.y) > 30) return;
    schluckBis = 0;
    e.stopPropagation();
    e.preventDefault();
  }, true);
}

/**
 * Eine Taste. Die Loeschtaste wiederholt, solange man sie haelt.
 */
function Taste({ zeige, label, klasse, breit, wiederholt, gedrueckt, vorschau, onDruck }: {
  zeige: ReactNode; label?: string; klasse?: string; breit?: number; wiederholt?: boolean; gedrueckt?: boolean;
  /** Das Zeichen, das beim Druecken gross ueber der Taste erscheint -- der Finger verdeckt die Taste selbst. */
  vorschau?: string;
  onDruck: () => void;
}) {
  const f = useRef(onDruck);
  f.current = onDruck;
  const knopf = useRef<HTMLButtonElement>(null);
  const lauf = useRef<{ warten?: ReturnType<typeof setTimeout>; takt?: ReturnType<typeof setInterval>; hoch?: ReturnType<typeof setTimeout> }>({});
  const halt = () => {
    clearTimeout(lauf.current.warten); clearInterval(lauf.current.takt);
    letzterDruck = performance.now();
    // Kurz stehen lassen: Ein schneller Tipp waere sonst nicht zu sehen.
    clearTimeout(lauf.current.hoch);
    lauf.current.hoch = setTimeout(() => knopf.current?.classList.remove('unten'), 90);
  };
  useEffect(() => () => { clearTimeout(lauf.current.warten); clearInterval(lauf.current.takt); clearTimeout(lauf.current.hoch); }, []);
  return (
    <button ref={knopf} type="button" className={`ttaste ${klasse ?? ''}`} aria-label={label} aria-pressed={gedrueckt} data-z={vorschau}
            style={breit ? { flexGrow: breit } : undefined}
            onPointerDown={e => {
              if (e.button !== 0) return;
              e.preventDefault();
              clearTimeout(lauf.current.warten); clearInterval(lauf.current.takt); clearTimeout(lauf.current.hoch);
              beruehrt = true;
              letzterDruck = performance.now();
              knopf.current?.classList.add('unten');
              f.current();
              if (wiederholt) lauf.current.warten = setTimeout(() => { lauf.current.takt = setInterval(() => { letzterDruck = performance.now(); f.current(); }, 70); }, 420);
            }}
            onPointerUp={halt} onPointerCancel={halt} onPointerLeave={halt}
            onContextMenu={e => e.preventDefault()}
            // Ohne Beruehrung ausgeloest (Vorlesefunktion, Eingabetaste): Dann zaehlt der Klick.
            onClick={() => { if (performance.now() - letzterDruck > 700) f.current(); }}>
      {zeige}
    </button>
  );
}
