import { useEffect, useMemo, useState } from 'react';
import { useT, sprachCode } from '../i18n/index.ts';
import { useWallet } from '../zustand.tsx';
import { einstellen, useEinstellungen } from '../wallet/einstellungen.ts';
import { isValidMnemonic, keypairFromMnemonic, normalizeMnemonic } from '../kern/wallet.ts';
import { bioStand, bioEinschalten, bioAusschalten } from '../wallet/biometrie.ts';
import { einfuegen, sichtschutz } from '../geraet/geraet.ts';
import { fehlerMerken } from '../geraet/plattform.ts';
import { LOESCHEN, tippeWort, wortVorschlaege, istWort, woerterAusText } from '../wallet/tasten.ts';
import { S, KopfZurueck, Schritte, Kasten, PinFeld, Praegung, Vierer, useZurueck, type PinAntwort } from '../ui/teile.tsx';
import { Tippfeld, useTastatur } from '../ui/tastatur.tsx';

type Schritt = 'willkommen' | 'pin' | 'pinNochmal' | 'woerter' | 'pruefen' | 'eingabe' | 'bio';

/** Zu leicht zu erraten: sechs gleiche Ziffern oder eine einfache Reihe. */
function schwach(pin: string): boolean {
  if (/^(\d)\1{5}$/.test(pin)) return true;
  return ['123456', '654321', '012345', '543210', '234567', '345678', '456789', '987654'].includes(pin);
}

/**
 * Einstieg: neue Wallet erstellen oder eine bestehende wiederherstellen.
 *
 *   neu:    PIN (zweimal) -> 12 Woerter aufschreiben -> drei davon eintippen
 *   wieder: 12 Woerter eingeben -> PIN (zweimal)
 *
 * Gespeichert wird erst ganz am Ende. Wer vorher abbricht, hinterlaesst
 * nichts auf dem Geraet.
 */
export function Einstieg() {
  const t = useT();
  const wallet = useWallet();
  const sprache = useEinstellungen().sprache;
  const [schritt, setSchritt] = useState<Schritt>('willkommen');
  const [weg, setWeg] = useState<'neu' | 'wieder'>('neu');
  const [pin, setPin] = useState('');
  const [eingabe, setEingabe] = useState<string[]>(() => Array<string>(12).fill(''));
  const [offen, setOffen] = useState(false);
  const [arbeitet, setArbeitet] = useState(false);
  const [fehler, setFehler] = useState('');

  const woerter = wallet.frisch?.split(' ') ?? [];
  const sauber = useMemo(() => normalizeMnemonic(eingabe.join(' ')), [eingabe]);
  const anzahl = eingabe.filter(Boolean).length;
  const gueltig = anzahl === 12 && isValidMnemonic(sauber);
  const tast = useTastatur();
  const adresse = useMemo(() => (gueltig ? keypairFromMnemonic(sauber).address : null), [gueltig, sauber]);

  // Solange Woerter zu sehen oder einzugeben sind: keine Bildschirmfotos.
  const geheim = schritt === 'woerter' || schritt === 'pruefen' || schritt === 'eingabe';
  useEffect(() => { if (!geheim) return; sichtschutz(true); return () => sichtschutz(false); }, [geheim]);

  const anfang = () => { wallet.verwerfen(); setPin(''); setEingabe(Array<string>(12).fill('')); setOffen(false); setFehler(''); setSchritt('willkommen'); };
  useZurueck(() => {
    if (schritt === 'pinNochmal') setSchritt('pin');
    else if (schritt === 'pruefen') setSchritt('woerter');
    else anfang();
  }, schritt !== 'willkommen' && !arbeitet);

  const abschliessen = async (mitBio: boolean) => {
    if (arbeitet) return;
    setArbeitet(true); setFehler('');
    try {
      if (mitBio && !(await bioEinschalten(pin, t.bio.grund, t.allg.abbrechen))) {
        setFehler(t.bio.nichtGeklappt); setArbeitet(false); return;
      }
      if (weg === 'neu') await wallet.versiegeln(pin);
      else if (!(await wallet.wiederherstellen(sauber, pin))) throw new Error('Woerter ungueltig');
    } catch (e) {
      fehlerMerken('einrichten', e);
      await bioAusschalten();
      setFehler(String((e as Error)?.message ?? e));
      setArbeitet(false);
    }
  };

  const nachPruefung = async () => {
    const stand = await bioStand();
    if (stand.da) setSchritt('bio'); else await abschliessen(false);
  };

  if (schritt === 'willkommen') {
    return (
      <div className="seite" style={{ paddingLeft: 24, paddingRight: 24 }}>
        <div className="kopf" style={{ justifyContent: 'flex-end' }}>
          <button type="button" className="klein sinkt" style={{ width: 'auto', padding: '0 14px', gap: 8, fontSize: 14, fontWeight: 700 }}
                  onClick={() => einstellen({ sprache: sprachCode(sprache) === 'de' ? 'en' : 'de' })}>
            <S n="welt" g={18} /><span>{sprachCode(sprache) === 'de' ? 'Deutsch' : 'English'}</span>
          </button>
        </div>
        <div className="fuell" />
        <div style={{ alignSelf: 'center', margin: '-16px 0' }}><Praegung /></div>
        <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
          <h1 className="gross-titel">{t.willkommen.titel1}<br />{t.willkommen.titel2}</h1>
          <p className="fliess" style={{ fontSize: 17 }}>{t.willkommen.text}</p>
        </div>
        <div className="punktliste">
          {[t.willkommen.punkt1, t.willkommen.punkt2, t.willkommen.punkt3].map(p =>
            <div key={p}><span><S n="haken" g={16} w={2.4} /></span>{p}</div>)}
        </div>
        <div className="fuell" />
        <div className="stapel">
          <button type="button" className="knopf sinkt" onClick={() => { setWeg('neu'); setSchritt('pin'); }}>{t.willkommen.neu}</button>
          <button type="button" className="knopf zweit sinkt" onClick={() => { setWeg('wieder'); setSchritt('eingabe'); }}>{t.willkommen.wieder}</button>
        </div>
      </div>
    );
  }

  if (schritt === 'pin') {
    return <PinFeld key="pin" titel={t.pin.festlegen} unter={t.pin.festlegenText} zurueck={weg === 'neu' ? anfang : () => setSchritt('eingabe')}
      onPin={async (p): Promise<PinAntwort> => {
        if (schwach(p)) return { fehler: t.pin.schwach };
        setPin(p); setTimeout(() => setSchritt('pinNochmal'), 250);
        return 'ok';
      }} />;
  }

  if (schritt === 'pinNochmal') {
    return <PinFeld key="nochmal" titel={t.pin.wiederholen} unter={t.pin.wiederholenText} zurueck={() => setSchritt('pin')}
      onPin={async (p): Promise<PinAntwort> => {
        if (p !== pin) { setTimeout(() => setSchritt('pin'), 1400); return { fehler: t.pin.ungleich }; }
        if (weg === 'neu') { if (!wallet.frisch) wallet.erstellen(); setTimeout(() => setSchritt('woerter'), 250); }
        else setTimeout(nachPruefung, 250);
        return 'ok';
      }} />;
  }

  if (schritt === 'woerter') {
    return (
      <div className="seite" style={{ paddingLeft: 24, paddingRight: 24, gap: 20 }}>
        <KopfZurueck zurueck={anfang} mitte={<Schritte n={2} von={3} />} />
        <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
          <h1 className="titel2">{t.woerter.titel}</h1>
          <p className="fliess">{t.woerter.text}</p>
        </div>
        <Wortkarte woerter={woerter} offen={offen} umschalten={() => setOffen(o => !o)} />
        <Kasten art="warn" symbol="warnung"><strong>{t.woerter.warnStark}</strong> {t.woerter.warnText}</Kasten>
        <div className="fuell" />
        <button type="button" className="knopf sinkt" onClick={() => { setOffen(false); setSchritt('pruefen'); }}>{t.woerter.weiter}</button>
      </div>
    );
  }

  if (schritt === 'pruefen') {
    return <Pruefung woerter={woerter} zurueck={() => setSchritt('woerter')} fertig={nachPruefung} arbeitet={arbeitet} fehler={fehler} />;
  }

  if (schritt === 'eingabe') {
    return (
      <div className="seite" style={{ paddingLeft: 24, paddingRight: 24, gap: 20 }}>
        <KopfZurueck zurueck={anfang} mitte={<Schritte n={1} von={2} />} />
        <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
          <h1 className="titel2">{t.wieder.titel}</h1>
          <p className="fliess">{t.wieder.text}</p>
        </div>
        <div className="feldgruppe">
          <div className="feldkopf"><span className="feldname">{t.wieder.feld}</span><small>{t.wieder.zaehler(anzahl)}</small></div>
          <Wortfelder kennung="w" werte={eingabe} nummern={eingabe.map((_, i) => i + 1)} auto
                      setze={(i, w) => setEingabe(e => e.map((x, j) => (j === i ? w : x)))} />
          <div className="paar-knoepfe">
            <button type="button" className="tonknopf sinkt" onClick={async () => { const x = await einfuegen(); if (x.trim()) setEingabe(woerterAusText(x, 12)); }}>
              <S n="einfuegen" g={19} /><span>{t.wieder.einfuegen}</span>
            </button>
            <button type="button" className="tonknopf grau sinkt" disabled={anzahl === 0} onClick={() => setEingabe(Array<string>(12).fill(''))}>{t.wieder.leeren}</button>
          </div>
          <div className="fehlertext" role="alert">{wortFehler(eingabe, tast.aktiv, 'w', t) ?? (anzahl === 12 && !gueltig ? t.wieder.ungueltig : '')}</div>
        </div>
        {adresse && (
          <div className="karte innen" style={{ padding: '14px 18px', gap: 6 }}>
            <div className="etikett">{t.wieder.adresse.toUpperCase()}</div>
            <Vierer adresse={adresse} />
            <div style={{ fontSize: 13, color: 'var(--leise)' }}>{t.wieder.adresseText}</div>
          </div>
        )}
        <div className="fuell" />
        <button type="button" className="knopf sinkt" disabled={!gueltig} onClick={() => setSchritt('pin')}>{t.allg.weiter}</button>
      </div>
    );
  }

  // schritt === 'bio'
  return (
    <div className="seite" style={{ paddingLeft: 24, paddingRight: 24, gap: 20 }}>
      <div className="fuell" />
      <div style={{ alignSelf: 'center', width: 96, height: 96, borderRadius: 32, background: 'var(--blau-flaeche)', color: 'var(--blau-hell)', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
        <S n="finger" g={48} w={1.6} />
      </div>
      <div style={{ display: 'flex', flexDirection: 'column', gap: 10, textAlign: 'center' }}>
        <h1 className="titel2">{t.bio.titel}</h1>
        <p className="fliess">{t.bio.text}</p>
      </div>
      <div className="fehlertext" role="alert" style={{ textAlign: 'center' }}>{fehler}</div>
      <div className="fuell" />
      <div className="stapel">
        <button type="button" className="knopf sinkt" disabled={arbeitet} onClick={() => abschliessen(true)}>{t.bio.ja}</button>
        <button type="button" className="knopf zweit sinkt" disabled={arbeitet} onClick={() => abschliessen(false)}>{t.bio.nein}</button>
      </div>
    </div>
  );
}

export function Wortkarte({ woerter, offen, umschalten }: { woerter: string[]; offen: boolean; umschalten: () => void }) {
  const t = useT();
  return (
    <div className="karte" style={{ padding: 16, gap: 14, borderRadius: 24 }}>
      <div className="wortraster">
        {woerter.map((w, i) => (
          <div key={i} className="wort">
            <small>{String(i + 1).padStart(2, '0')}</small>
            <b className={offen ? undefined : 'zu'}>{offen ? w : '••••••'}</b>
          </div>
        ))}
      </div>
      <button type="button" className="tonknopf sinkt" aria-pressed={offen} onClick={umschalten}>
        <S n={offen ? 'augeZu' : 'auge'} g={19} /><span>{offen ? t.woerter.verbergen : t.woerter.zeigen}</span>
      </button>
    </div>
  );
}

/** Das erste Wort, das nicht in der Liste steht -- aber nicht das, an dem gerade getippt wird. */
function wortFehler(werte: string[], aktiv: string | null, kennung: string, t: ReturnType<typeof useT>): string | null {
  const i = werte.findIndex((w, j) => w !== '' && !istWort(w) && aktiv !== `${kennung}${j}`);
  return i < 0 ? null : t.wieder.unbekannt(i + 1);
}

/**
 * Felder fuer Woerter der Wiederherstellung. Getippt wird ueber die Tastatur
 * der App: nur a-z, mit Vorschlaegen aus der Wortliste. Ist ein Wort
 * eindeutig fertig, geht es von selbst ins naechste leere Feld.
 */
function Wortfelder({ kennung, werte, nummern, setze, auto, einspaltig }: {
  kennung: string; werte: string[]; nummern: number[]; setze: (i: number, wort: string) => void; auto?: boolean; einspaltig?: boolean;
}) {
  const t = useT();
  const tast = useTastatur();
  const weiter = (i: number, stand: string[]) => {
    for (let k = 1; k < stand.length; k++) {
      const j = (i + k) % stand.length;
      if (!stand[j]) { tast.oeffne(`${kennung}${j}`); return; }
    }
    tast.oeffne(null);
  };
  const nimm = (i: number, wort: string) => { setze(i, wort); weiter(i, werte.map((x, j) => (j === i ? wort : x))); };
  return (
    <div className={`wortraster ${einspaltig ? 'eins' : ''}`}>
      {werte.map((w, i) => {
        const id = `${kennung}${i}`;
        const dran = tast.aktiv === id;
        const vorschlaege = dran ? wortVorschlaege(w, 4) : [];
        return (
          <Tippfeld key={id} id={id} art="wort" klasse="wort" label={t.woerter.wort(nummern[i])} wert={w} auto={auto && i === 0 && werte.every(x => x === '')}
                    vor={<small>{String(nummern[i]).padStart(2, '0')}</small>}
                    zeige={<b className={!dran && !istWort(w) ? 'falsch' : undefined}>{w}</b>}
                    onTaste={z => {
                      if (z === LOESCHEN && w === '') { if (i > 0) tast.oeffne(`${kennung}${i - 1}`); return; }
                      const neu = tippeWort(w, z);
                      // Kein anderes Wort faengt so an: fertig, weiter zum naechsten.
                      const fertig = z !== LOESCHEN && wortVorschlaege(neu, 2).join() === neu;
                      if (fertig) nimm(i, neu); else setze(i, neu);
                    }}
                    weiter={() => weiter(i, werte)}
                    leiste={vorschlaege.length > 0
                      ? vorschlaege.map(v => <button key={v} type="button" className={`tknopf vorschlag ${v === w ? 'genau' : ''}`} onClick={() => nimm(i, v)}>{v}</button>)
                      : <span className={`thinweis ${w ? 'rot' : ''}`}>{w ? t.tast.keinWort : t.woerter.wort(nummern[i])}</span>} />
        );
      })}
    </div>
  );
}

function Pruefung({ woerter, zurueck, fertig, arbeitet, fehler }: { woerter: string[]; zurueck: () => void; fertig: () => void; arbeitet: boolean; fehler: string }) {
  const t = useT();
  // Drei zufaellige Stellen, aufsteigend. Einmal gewaehlt, bleiben sie.
  const stellen = useMemo(() => {
    const alle = Array.from({ length: woerter.length }, (_, i) => i);
    const z = crypto.getRandomValues(new Uint32Array(alle.length));
    return alle.sort((a, b) => z[a] - z[b]).slice(0, 3).sort((a, b) => a - b);
  }, [woerter.length]);
  const [werte, setWerte] = useState(['', '', '']);
  const [falsch, setFalsch] = useState(false);
  const voll = werte.every(w => w);
  const pruefen = () => {
    if (stellen.every((s, i) => werte[i] === woerter[s])) fertig();
    else setFalsch(true);
  };
  return (
    <div className="seite" style={{ paddingLeft: 24, paddingRight: 24, gap: 20 }}>
      <KopfZurueck zurueck={zurueck} mitte={<Schritte n={3} von={3} />} />
      <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
        <h1 className="titel2">{t.woerter.pruefTitel}</h1>
        <p className="fliess">{t.woerter.pruefText}</p>
      </div>
      <Wortfelder kennung="wort" werte={werte} nummern={stellen.map(s => s + 1)} einspaltig auto
                  setze={(i, w) => { setFalsch(false); setWerte(alt => alt.map((x, j) => (j === i ? w : x))); }} />
      <div className="fehlertext" role="alert">{falsch ? t.woerter.pruefFalsch : fehler}</div>
      <div className="fuell" />
      <button type="button" className="knopf sinkt" disabled={!voll || arbeitet} onClick={pruefen}>{t.woerter.einrichten}</button>
    </div>
  );
}
