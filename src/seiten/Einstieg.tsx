import { useEffect, useMemo, useState } from 'react';
import { useT, sprachCode } from '../i18n/index.ts';
import { useWallet } from '../zustand.tsx';
import { einstellen, useEinstellungen } from '../wallet/einstellungen.ts';
import { isValidMnemonic, keypairFromMnemonic, normalizeMnemonic } from '../kern/wallet.ts';
import { bioStand, bioEinschalten, bioAusschalten } from '../wallet/biometrie.ts';
import { einfuegen, sichtschutz } from '../geraet/geraet.ts';
import { fehlerMerken } from '../geraet/plattform.ts';
import { LOESCHEN, tippeWort, wortVorschlaege, istWort, woerterAusText } from '../wallet/tasten.ts';
import { S, Kasten, PinFeld, Schritte, Vierer, useZurueck, type PinAntwort } from '../ui/teile.tsx';
import { YMarke } from '../ui/zeichen.tsx';
import { Tippfeld, useTastatur } from '../ui/tastatur.tsx';

type Schritt = 'willkommen' | 'wahl' | 'woerter' | 'pruefen' | 'eingabe' | 'pin' | 'pinNochmal' | 'bio' | 'fertig';

/** Zu leicht zu erraten: sechs gleiche Ziffern oder eine einfache Reihe. */
function schwach(pin: string): boolean {
  if (/^(\d)\1{5}$/.test(pin)) return true;
  return ['123456', '654321', '012345', '543210', '234567', '345678', '456789', '987654'].includes(pin);
}

/**
 * Einstieg: neue Wallet erstellen oder eine bestehende wiederherstellen.
 *
 *   neu:    12 Woerter aufschreiben -> drei davon eintippen -> PIN (zweimal) -> Fingerabdruck? -> fertig
 *   wieder: 12 Woerter eingeben -> PIN (zweimal) -> Fingerabdruck? -> fertig
 *
 * Gespeichert wird erst ganz am Ende, mit "Zur Wallet". Wer vorher abbricht,
 * hinterlaesst nichts auf dem Geraet.
 */
export function Einstieg() {
  const t = useT();
  const wallet = useWallet();
  const sprache = useEinstellungen().sprache;
  const [schritt, setSchritt] = useState<Schritt>('willkommen');
  const [weg, setWeg] = useState<'neu' | 'wieder'>('neu');
  const [pin, setPin] = useState('');
  const [mitBio, setMitBio] = useState(false);
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
  const neueAdresse = useMemo(() => (wallet.frisch ? keypairFromMnemonic(wallet.frisch).address : null), [wallet.frisch]);

  // Solange Woerter zu sehen oder einzugeben sind: keine Bildschirmfotos.
  const geheim = schritt === 'woerter' || schritt === 'pruefen' || schritt === 'eingabe';
  useEffect(() => { if (!geheim) return; sichtschutz(true); return () => sichtschutz(false); }, [geheim]);

  const anfang = () => { wallet.verwerfen(); setPin(''); setMitBio(false); setEingabe(Array<string>(12).fill('')); setOffen(false); setFehler(''); setSchritt('willkommen'); };
  useZurueck(() => {
    if (schritt === 'wahl') setSchritt('willkommen');
    else if (schritt === 'pruefen') setSchritt('woerter');
    else if (schritt === 'pin') setSchritt(weg === 'neu' ? 'pruefen' : 'eingabe');
    else if (schritt === 'pinNochmal') setSchritt('pin');
    else anfang();
  }, schritt !== 'willkommen' && schritt !== 'fertig' && !arbeitet);

  /** Der letzte Schritt: Tresor anlegen. Der Fingerabdruck ist dann schon eingerichtet, wenn gewuenscht. */
  const abschliessen = async () => {
    if (arbeitet) return;
    setArbeitet(true); setFehler('');
    try {
      if (weg === 'neu') await wallet.versiegeln(pin);
      else if (!(await wallet.wiederherstellen(sauber, pin))) throw new Error('Woerter ungueltig');
    } catch (e) {
      fehlerMerken('einrichten', e);
      await bioAusschalten();
      setFehler(String((e as Error)?.message ?? e));
      setArbeitet(false);
    }
  };

  const nachPin = async () => {
    const stand = await bioStand();
    setSchritt(stand.da ? 'bio' : 'fertig');
  };

  const fingerEinschalten = async () => {
    if (arbeitet) return;
    setArbeitet(true); setFehler('');
    const ok = await bioEinschalten(pin, t.bio.grund, t.allg.abbrechen);
    setArbeitet(false);
    if (!ok) { setFehler(t.bio.nichtGeklappt); return; }
    setMitBio(true); setSchritt('fertig');
  };

  const neuStarten = () => { setWeg('neu'); if (!wallet.frisch) wallet.erstellen(); setSchritt('woerter'); };
  const wiederStarten = () => { setWeg('wieder'); setSchritt('eingabe'); };

  if (schritt === 'willkommen' || schritt === 'wahl') {
    return (
      <div className="seite sf ew">
        <div className="ew-oben">
          <button type="button" className="sprachknopf sinkt" onClick={() => einstellen({ sprache: sprachCode(sprache) === 'de' ? 'en' : 'de' })}>
            <S n="welt" g={16} w={1.6} /><span>{sprachCode(sprache) === 'de' ? 'Deutsch' : 'English'}</span>
          </button>
        </div>
        <div className="ew-marke"><YMarke g={56} farbe="var(--blau-hell)" /></div>
        <h1 className="ew-titel">{t.willkommen.titel1}<span>{t.willkommen.titel2}</span></h1>
        <p className="ew-text">{t.willkommen.text}</p>
        <span className="band-linie ew-band" aria-hidden="true" />
        <div className="fuell" />
        <button type="button" className="knopf sinkt" onClick={neuStarten}>{t.willkommen.neu}</button>
        <button type="button" className="knopf zweit sinkt ew-zweit" onClick={wiederStarten}>{t.willkommen.habe}</button>
        <p className="ew-fuss"><S n="schloss" g={15} w={1.6} />{t.willkommen.fuss}</p>
      </div>
    );
  }

  if (schritt === 'pin') {
    return <PinFeld key="pin" kopf={weg === 'neu' ? t.woerter.kopf : t.wieder.kopf} titel={t.pin.festlegen} unter={t.pin.festlegenText} zurueck={() => setSchritt(weg === 'neu' ? 'pruefen' : 'eingabe')}
      onPin={async (p): Promise<PinAntwort> => {
        if (schwach(p)) return { fehler: t.pin.schwach };
        setPin(p); setTimeout(() => setSchritt('pinNochmal'), 250);
        return 'ok';
      }} />;
  }

  if (schritt === 'pinNochmal') {
    return <PinFeld key="nochmal" kopf={weg === 'neu' ? t.woerter.kopf : t.wieder.kopf} titel={t.pin.wiederholen} unter={t.pin.wiederholenText} zurueck={() => setSchritt('pin')}
      onPin={async (p): Promise<PinAntwort> => {
        if (p !== pin) { setTimeout(() => setSchritt('pin'), 1400); return { fehler: t.pin.ungleich }; }
        setTimeout(nachPin, 250);
        return 'ok';
      }} />;
  }

  if (schritt === 'woerter') {
    return (
      <div className="seite sf ew">
        <Kopfzeile titel={t.woerter.kopf} zurueck={anfang} rechts={<Schritte n={1} label={t.senden.schritt(1)} />} />
        <h1 className="sf-t1">{t.woerter.titel}</h1>
        <p className="ew-unter">{t.woerter.text}</p>
        <Wortkarte woerter={woerter} offen={offen} umschalten={() => setOffen(o => !o)} />
        <div className="ew-warn"><Kasten art="warn" symbol="warnung"><b>{t.woerter.warnStark}</b><br /><span className="leise">{t.woerter.warnText}</span></Kasten></div>
        <div className="fuell" />
        <button type="button" className="knopf sinkt" disabled={!offen} onClick={() => { setOffen(false); setSchritt('pruefen'); }}>{t.woerter.weiter}</button>
      </div>
    );
  }

  if (schritt === 'pruefen') {
    return <Pruefung woerter={woerter} zurueck={() => setSchritt('woerter')} fertig={() => setSchritt('pin')} arbeitet={arbeitet} fehler={fehler} />;
  }

  if (schritt === 'eingabe') {
    return (
      <div className="seite sf ew">
        <Kopfzeile titel={t.wieder.kopf} zurueck={anfang} rechts={<span className="ew-zaehler ziffern" aria-label={t.wieder.zaehler(anzahl)}>{anzahl}/12</span>} />
        <h1 className="sf-t1">{t.wieder.titel}</h1>
        <p className="ew-unter">{t.wieder.text}</p>
        <div className="ew-woerter">
          {/* Fertige Woerter verdeckt (4.2). Ergeben alle 12 keine gueltige Wallet, wieder offen -- sonst sieht man den Fehler nicht. */}
          <Wortfelder kennung="w" werte={eingabe} nummern={eingabe.map((_, i) => i + 1)} auto verdeckt={!(anzahl === 12 && !gueltig)}
                      setze={(i, w) => setEingabe(e => e.map((x, j) => (j === i ? w : x)))} />
        </div>
        <div className="paar-knoepfe ew-knoepfe">
          <button type="button" className="knopf zweit sinkt" onClick={async () => { const x = await einfuegen(); if (x.trim()) setEingabe(woerterAusText(x, 12)); }}>
            <S n="einfuegen" g={18} w={1.6} /><span>{t.wieder.einfuegen}</span>
          </button>
          <button type="button" className="knopf zweit sinkt" disabled={anzahl === 0} onClick={() => setEingabe(Array<string>(12).fill(''))}>{t.wieder.leeren}</button>
        </div>
        <div className="fehlertext" role="alert">{wortFehler(eingabe, tast.aktiv, 'w', t) ?? (anzahl === 12 && !gueltig ? t.wieder.ungueltig : '')}</div>
        {adresse && (
          <div className="karte ew-adresse">
            <span className="sf-lab">{t.wieder.adresse}</span>
            <Vierer adresse={adresse} klasse="mono" />
            <span className="sf-klein">{t.wieder.adresseText}</span>
          </div>
        )}
        <div className="fuell" />
        <button type="button" className="knopf sinkt" disabled={!gueltig} onClick={() => setSchritt('pin')}>{t.allg.weiter}</button>
      </div>
    );
  }

  if (schritt === 'bio') {
    return (
      <div className="seite sf ew">
        <Kopfzeile titel={weg === 'neu' ? t.woerter.kopf : t.wieder.kopf} zurueck={() => setSchritt('pin')} />
        <div className="fuell" />
        <div className="grosskreis"><S n="finger" g={68} w={1.6} /></div>
        <div style={{ display: 'flex', flexDirection: 'column', gap: 10, alignItems: 'center' }}>
          <h1 className="titel2 mittig">{t.bio.titel}</h1>
          <p className="fliess mittig" style={{ fontSize: 15, maxWidth: 340 }}>{t.bio.text}</p>
        </div>
        <div className="fehlertext" role="alert" style={{ textAlign: 'center' }}>{fehler}</div>
        <div className="fuell" />
        <div className="fuell" />
        <div className="stapel">
          <button type="button" className="knopf sinkt" disabled={arbeitet} onClick={fingerEinschalten}>{t.bio.ja}</button>
          <button type="button" className="knopf text sinkt" disabled={arbeitet} onClick={() => { setMitBio(false); setSchritt('fertig'); }}>{t.bio.nein}</button>
        </div>
      </div>
    );
  }

  // schritt === 'fertig'
  const fertigAdresse = weg === 'neu' ? neueAdresse : adresse;
  return (
    <div className="seite" style={{ alignItems: 'center' }}>
      <div className="fuell" />
      <div className="gruenfeld" style={{ width: 140, height: 140 }}>
        <svg width="96" height="96" viewBox="0 0 64 64" aria-hidden="true"><circle className="ring" cx="32" cy="32" r="30" transform="rotate(-90 32 32)" /><path className="strich" d="M19 33l9 9 17-19" /></svg>
      </div>
      <div className="auf" style={{ display: 'flex', flexDirection: 'column', gap: 10, alignItems: 'center', animationDelay: '.5s' }}>
        <h1 className="titel2 mittig">{t.willkommen.fertigTitel}</h1>
        <p className="fliess mittig" style={{ fontSize: 15, maxWidth: 340 }}>{t.willkommen.fertigText}{mitBio ? '' : ''}</p>
      </div>
      {fertigAdresse && <span className="adress-pille auf" style={{ height: 36, fontSize: 14, animationDelay: '.6s' }}>{fertigAdresse.slice(0, 12)}…{fertigAdresse.slice(-4)}</span>}
      <div className="fehlertext" role="alert" style={{ textAlign: 'center' }}>{fehler}</div>
      <div className="fuell" />
      <div className="fuell" />
      <button type="button" className="knopf sinkt auf" style={{ animationDelay: '.7s' }} disabled={arbeitet} onClick={abschliessen}>{t.willkommen.zurWallet}</button>
    </div>
  );
}

/**
 * Die 12 Woerter in zwei Spalten. Zuerst verdeckt -- der Blick muss gewollt sein (4.2:
 * graue Balken, in der Mitte "Woerter anzeigen").
 */
export function Wortkarte({ woerter, offen, umschalten }: { woerter: string[]; offen: boolean; umschalten: () => void }) {
  const t = useT();
  const breiten = [58, 44, 66, 52, 40, 60, 48, 70, 54, 46, 62, 50];
  return (
    <div className={`karte wortkarte ${offen ? 'offen' : ''}`}>
      <div className="wortliste" aria-hidden={!offen}>
        {woerter.map((w, i) => (
          <div key={i}>
            <small className="ziffern">{i + 1}</small>
            {offen ? <b className="mono">{w}</b> : <i style={{ width: breiten[i % 12] }} />}
          </div>
        ))}
      </div>
      <button type="button" className={`wortkarte-knopf sinkt ${offen ? 'unten' : ''}`} aria-pressed={offen} onClick={umschalten}>
        <S n={offen ? 'augeZu' : 'auge'} g={18} w={1.6} /><span>{offen ? t.woerter.verbergen : t.woerter.zeigen}</span>
      </button>
    </div>
  );
}

/** Kopfzeile der Einrichtung: Zurueck, Titel in der Mitte, rechts Schritte oder Zaehler. */
function Kopfzeile({ titel, zurueck, rechts }: { titel: string; zurueck: () => void; rechts?: React.ReactNode }) {
  const t = useT();
  return (
    <div className="sf-kopf">
      <button type="button" className="klein sinkt" aria-label={t.allg.zurueck} onClick={zurueck}><S n="zurueck" w={2.2} /></button>
      <div className="sf-mitte"><span className="sf-titel">{titel}</span></div>
      {rechts ?? <span className="sf-platz" />}
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
function Wortfelder({ kennung, werte, nummern, setze, auto, einspaltig, verdeckt, richtig }: {
  kennung: string; werte: string[]; nummern: number[]; setze: (i: number, wort: string) => void; auto?: boolean; einspaltig?: boolean;
  /** Fertige Woerter verdecken, solange nicht an ihnen getippt wird (4.2). Unbekannte bleiben sichtbar und rot. */
  verdeckt?: boolean;
  /** Pruefung: stimmt das Wort an dieser Stelle? null = noch nicht fertig. */
  richtig?: (i: number, wort: string) => boolean | null;
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
        const stimmt = !dran && w && richtig ? richtig(i, w) : null;
        const zu = !dran && istWort(w) && (verdeckt || stimmt === true);
        return (
          <Tippfeld key={id} id={id} art="wort" klasse={`wort ${zu ? 'fertig' : ''}`} label={t.woerter.wort(nummern[i])} wert={w} auto={auto && i === 0 && werte.every(x => x === '')}
                    vor={<small className="ziffern">{einspaltig ? t.woerter.wort(nummern[i]) : nummern[i]}</small>}
                    zeige={<>
                      <b className={`mono ${(!dran && !istWort(w)) || stimmt === false ? 'falsch' : ''}`}>{zu ? '••••' : w}</b>
                      {stimmt === true && <span className="wort-gut"><S n="haken" g={16} w={2.4} /></span>}
                    </>}
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
    <div className="seite sf ew">
      <Kopfzeile titel={t.woerter.kopf} zurueck={zurueck} rechts={<Schritte n={2} label={t.senden.schritt(2)} />} />
      <h1 className="sf-t1">{t.woerter.pruefTitel}</h1>
      <p className="ew-unter">{t.woerter.pruefText}</p>
      <div className="ew-woerter">
        <Wortfelder kennung="wort" werte={werte} nummern={stellen.map(s => s + 1)} einspaltig auto
                    richtig={(i, w) => (istWort(w) ? w === woerter[stellen[i]] : null)}
                    setze={(i, w) => { setFalsch(false); setWerte(alt => alt.map((x, j) => (j === i ? w : x))); }} />
      </div>
      <div className="fehlertext" role="alert">{falsch ? t.woerter.pruefFalsch : fehler}</div>
      <div className="fuell" />
      <button type="button" className="knopf sinkt" disabled={!voll || arbeitet} onClick={pruefen}>{t.woerter.einrichten}</button>
    </div>
  );
}
