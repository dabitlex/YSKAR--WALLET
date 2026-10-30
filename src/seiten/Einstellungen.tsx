import { useEffect, useRef, useState, type ReactNode } from 'react';
import { useT, sprachCode } from '../i18n/index.ts';
import { useDaten, useNav, useWallet } from '../zustand.tsx';
import { einstellen, useEinstellungen, type Sprache } from '../wallet/einstellungen.ts';
import { bioAktiv, bioAusschalten, bioEinschalten, bioStand } from '../wallet/biometrie.ts';
import { kopieren, sichtschutz, imBrowserOeffnen } from '../geraet/geraet.ts';
import { istNativ, letzteFehler } from '../geraet/plattform.ts';
import { eingangMoeglich, eingangStand, eingangEinschalten, eingangAusschalten, eingangAkku, type EingangStand } from '../geraet/eingang.ts';
import { S, Kopf, KopfZurueck, Blatt, Kasten, PinFeld, Zeichen, useMeldung, type PinAntwort } from '../ui/teile.tsx';
import { Wortkarte } from './Einstieg.tsx';

declare const __VERSION__: string;

/* Links der Gruppe "Hilfe": nur Seiten, die es gibt. */
const LINKS = {
  anleitungen: 'https://www.yskar.app/anleitungen',
  community: 'https://t.me/+GxAmtTMeG1w1M2Yy',
  news: 'https://t.me/yskar_official',
  datenschutz: 'https://www.yskar.app/datenschutz',
  impressum: 'https://www.yskar.app/impressum',
};

type SymbolName = Parameters<typeof S>[0]['n'];

/** Eine Zeile der Einstellungen: Bezeichnung links, rechts Wert oder Symbol. */
function Zeile({ text, symbol, wert, rot, onTipp, rechts }: {
  text: string; symbol?: SymbolName; wert?: string; rot?: boolean; onTipp?: () => void; rechts?: ReactNode;
}) {
  const inhalt = <>
    <span className="ez-text">{text}</span>
    {rechts ?? (wert !== undefined ? <span className="ez-wert">{wert}</span> : symbol && <span className="ez-symbol"><S n={symbol} g={21} /></span>)}
  </>;
  return onTipp
    ? <button type="button" className={`ez sinkt ${rot ? 'rot' : ''}`} onClick={onTipp}>{inhalt}</button>
    : <div className={`ez ${rot ? 'rot' : ''}`}>{inhalt}</div>;
}

export function Einstellungen() {
  const t = useT();
  const nav = useNav();
  const wallet = useWallet();
  const daten = useDaten();
  const melde = useMeldung();
  const e = useEinstellungen();
  const [blatt, setBlatt] = useState<null | 'sprache' | 'meldung' | 'recht' | 'entfernen'>(null);
  const [meldung, setMeldung] = useState<EingangStand | null>(null);
  const [entfernenPin, setEntfernenPin] = useState(false);
  const tipps = useRef<{ n: number; zeit: number }>({ n: 0, zeit: 0 });
  useEffect(() => { if (eingangMoeglich()) eingangStand().then(setMeldung); }, []);
  // Nach dem Rueckweg aus den Android-Einstellungen den Stand neu lesen.
  useEffect(() => {
    const neu = () => { if (document.visibilityState === 'visible' && eingangMoeglich()) eingangStand().then(setMeldung); };
    document.addEventListener('visibilitychange', neu);
    return () => document.removeEventListener('visibilitychange', neu);
  }, []);
  const meldungTexte = () => ({
    sprache: t.code, kanal: t.einst.meldungKanal, kanalText: t.einst.meldungKanalText, von: t.einst.meldungVon,
    belohnung: t.zahlung.belohnung, pool: t.zahlung.pool, block: t.einst.meldungBlock, gesperrt: t.einst.meldungGesperrt,
  });
  const meldungUmschalten = async () => {
    if (!wallet.adresse) return;
    const r = meldung?.an ? await eingangAusschalten() : await eingangEinschalten(wallet.adresse, meldungTexte());
    setMeldung(r);
    if (r.an && r.berechtigung !== 'granted') melde(t.einst.meldungVerweigert);
  };

  const sprachText = (s: Sprache) => (s === 'system' ? t.einst.systemKurz : s === 'de' ? 'Deutsch' : 'English');

  /** Fuer Tester: fuenfmal schnell auf die Version tippen kopiert die Diagnose. */
  const versionTipp = async () => {
    const jetzt = Date.now();
    const t0 = tipps.current;
    tipps.current = jetzt - t0.zeit < 1500 ? { n: t0.n + 1, zeit: jetzt } : { n: 1, zeit: jetzt };
    if (tipps.current.n < 5) return;
    tipps.current = { n: 0, zeit: 0 };
    const text = [
      `YSKAR Wallet ${__VERSION__}`, `App: ${istNativ() ? 'Android' : 'Browser'}`, `Sprache: ${sprachCode(e.sprache)}`,
      `Netz: ${daten.offline ? 'keine Verbindung' : 'verbunden'}, Hoehe ${daten.spitze ?? '?'}`,
      `Wartende vom Knoten: ${daten.konto?.pendingSource ?? '?'}`, `Fingerabdruck: ${bioAktiv() ? 'an' : 'aus'}`,
      '', ...letzteFehler(),
    ].join('\n');
    if (await kopieren(text)) melde(t.einst.diagnoseKopiert);
  };

  if (entfernenPin) {
    return <PinFeld titel={t.pin.eingeben} unter={t.einst.entfernenPin} zurueck={() => setEntfernenPin(false)}
      onPin={async (pin): Promise<PinAntwort> => {
        const r = await wallet.pinPruefen(pin);
        if (!r.ok) return r.grund;
        if (eingangMoeglich()) await eingangAusschalten();
        await wallet.vergessen();
        return 'ok';
      }} />;
  }

  const meldungAn = !!meldung?.an && meldung.berechtigung === 'granted';
  return (
    <div className="seite mit-nav eng">
      <Kopf titel={t.einst.titel} />

      <div className="ez-gruppe">
        <Zeile text={t.einst.sicherheitZeile} symbol="schloss" onTipp={() => nav.gehe({ n: 'sicherheit' })} />
        <Zeile text={t.einst.woerter} symbol="schluessel" onTipp={() => nav.gehe({ n: 'woerterZeigen' })} />
        {eingangMoeglich() && <Zeile text={t.einst.meldungKurz} wert={meldungAn ? t.allg.an : t.allg.aus} onTipp={() => setBlatt('meldung')} />}
        <Zeile text={t.einst.sprache} wert={sprachText(e.sprache)} onTipp={() => setBlatt('sprache')} />
      </div>

      <div className="ez-gruppe">
        <Zeile text={t.sig.signieren} symbol="stift" onTipp={() => nav.gehe({ n: 'signieren' })} />
        <Zeile text={t.sig.pruefen} symbol="schild" onTipp={() => nav.gehe({ n: 'signaturPruefen' })} />
        <Zeile text={t.qr.eintrag} symbol="scan" onTipp={() => nav.gehe({ n: 'anmeldenQr' })} />
      </div>

      <div className="ez-gruppe">
        <Zeile text={t.einst.anleitungen} symbol="buch" onTipp={() => imBrowserOeffnen(LINKS.anleitungen)} />
        <Zeile text={t.einst.community} symbol="blase" onTipp={() => imBrowserOeffnen(LINKS.community)} />
        <Zeile text={t.einst.news} symbol="flieger" onTipp={() => imBrowserOeffnen(LINKS.news)} />
        <Zeile text={t.einst.recht} symbol="dokument" onTipp={() => setBlatt('recht')} />
      </div>

      <div className="ez-gruppe">
        <Zeile text={t.einst.entfernen} symbol="weg" rot onTipp={() => setBlatt('entfernen')} />
      </div>

      <button type="button" className="ez-fuss" onClick={versionTipp} aria-label={`${t.einst.version} ${__VERSION__}`}>
        <Zeichen g={34} />
        <b>YSKAR Wallet</b>
        <span>{t.einst.version} {__VERSION__}</span>
      </button>

      {blatt === 'sprache' && (
        <Blatt titel={t.einst.sprache} onZu={() => setBlatt(null)}>
          <div className="ez-gruppe">
            {(['system', 'de', 'en'] as Sprache[]).map(s => (
              <Zeile key={s} text={s === 'system' ? t.einst.system : sprachText(s)} onTipp={() => { einstellen({ sprache: s }); setBlatt(null); }}
                     rechts={e.sprache === s ? <span className="ez-symbol"><S n="haken" g={20} w={2.6} /></span> : <span />} />
            ))}
          </div>
        </Blatt>
      )}
      {blatt === 'recht' && (
        <Blatt titel={t.einst.recht} onZu={() => setBlatt(null)}>
          <div className="ez-gruppe">
            <Zeile text={t.einst.datenschutz} symbol="extern" onTipp={() => imBrowserOeffnen(LINKS.datenschutz)} />
            <Zeile text={t.einst.impressum} symbol="extern" onTipp={() => imBrowserOeffnen(LINKS.impressum)} />
            <Zeile text={t.einst.lizenz} wert="MIT" />
          </div>
        </Blatt>
      )}
      {blatt === 'meldung' && (
        <Blatt titel={t.einst.meldungTitel} onZu={() => setBlatt(null)}>
          <p>{t.einst.meldungText}</p>
          <div className="ez-gruppe">
            <Zeile text={meldung?.an ? t.einst.meldungAn : t.einst.meldungAus}
                   rechts={<button type="button" role="switch" className="schalter" aria-checked={!!meldung?.an} aria-label={t.einst.meldungKurz} onClick={meldungUmschalten}><span /></button>} />
          </div>
          {meldung?.an && meldung.berechtigung !== 'granted' && <Kasten art="warn" symbol="warnung">{t.einst.meldungVerweigert}</Kasten>}
          {meldung?.an && meldung.berechtigung === 'granted' && (
            meldung.akkuFrei
              ? <div className="gutzeile"><S n="haken" g={16} w={2.6} />{t.einst.meldungAkkuFrei}</div>
              : <>
                  <Kasten art="ruhig" symbol="info">{t.einst.meldungAkku} {t.einst.meldungVerzoegert}</Kasten>
                  <button type="button" className="knopf zweit sinkt" onClick={() => eingangAkku()}>{t.einst.meldungAkkuKnopf}</button>
                </>
          )}
        </Blatt>
      )}
      {blatt === 'entfernen' && (
        <Blatt titel={t.einst.entfernenTitel} onZu={() => setBlatt(null)}>
          <Kasten art="rot" symbol="warnung">{t.einst.entfernenText}</Kasten>
          <div className="stapel">
            <button type="button" className="knopf gefahr sinkt" onClick={() => { setBlatt(null); setEntfernenPin(true); }}>{t.einst.entfernenJa}</button>
            <button type="button" className="knopf zweit sinkt" onClick={() => setBlatt(null)}>{t.allg.abbrechen}</button>
          </div>
        </Blatt>
      )}
    </div>
  );
}

/** Sicherheit: PIN, Fingerabdruck, automatisch sperren. */
export function Sicherheit() {
  const t = useT();
  const nav = useNav();
  const wallet = useWallet();
  const melde = useMeldung();
  const e = useEinstellungen();
  const [bio, setBio] = useState(bioAktiv());
  const [blatt, setBlatt] = useState(false);
  const [pinFuerBio, setPinFuerBio] = useState(false);
  const sperrText = (s: number) => (s === 0 ? t.einst.sofort : s === 60 ? t.einst.min1 : t.einst.min5);
  const sperrKurz = (s: number) => (s === 0 ? t.einst.sofort : s === 60 ? t.einst.min1Kurz : t.einst.min5Kurz);

  const fingerUmschalten = async () => {
    if (bio) { await bioAusschalten(); setBio(false); return; }
    const stand = await bioStand();
    if (!stand.da) { melde(t.bio.fehlt(stand.grund)); return; }
    setPinFuerBio(true);
  };

  if (pinFuerBio) {
    return <PinFeld titel={t.pin.eingeben} unter={t.einst.bioPin} zurueck={() => setPinFuerBio(false)}
      onPin={async (pin): Promise<PinAntwort> => {
        const r = await wallet.pinPruefen(pin);
        if (!r.ok) return r.grund;
        const ok = await bioEinschalten(pin, t.bio.grund, t.allg.abbrechen);
        setBio(ok); if (!ok) melde(t.bio.nichtGeklappt);
        setPinFuerBio(false);
        return 'ok';
      }} />;
  }

  return (
    <div className="seite eng">
      <KopfZurueck titel={t.einst.sicherheitZeile} zurueck={() => nav.zurueck()} />
      <div className="ez-gruppe">
        <Zeile text={t.einst.pinAendern} symbol="schloss" onTipp={() => nav.gehe({ n: 'pinAendern' })} />
        <Zeile text={t.einst.finger}
               rechts={<button type="button" role="switch" className="schalter" aria-checked={bio} aria-label={t.einst.finger} onClick={fingerUmschalten}><span /></button>} />
        <Zeile text={t.einst.sperre} wert={sperrKurz(e.sperreNach)} onTipp={() => setBlatt(true)} />
      </div>
      <p className="ez-hinweis">{t.einst.fingerText}</p>

      {blatt && (
        <Blatt titel={t.einst.sperreTitel} onZu={() => setBlatt(false)}>
          <p>{t.einst.sperreText}</p>
          <div className="ez-gruppe">
            {[0, 60, 300].map(s => (
              <Zeile key={s} text={sperrText(s)} onTipp={() => { einstellen({ sperreNach: s }); setBlatt(false); }}
                     rechts={e.sperreNach === s ? <span className="ez-symbol"><S n="haken" g={20} w={2.6} /></span> : <span />} />
            ))}
          </div>
        </Blatt>
      )}
    </div>
  );
}

/** PIN aendern: alte PIN, neue PIN, neue PIN noch einmal. */
export function PinAendern() {
  const t = useT();
  const nav = useNav();
  const wallet = useWallet();
  const melde = useMeldung();
  const [schritt, setSchritt] = useState<'alt' | 'neu' | 'nochmal'>('alt');
  const [alt, setAlt] = useState('');
  const [neu, setNeu] = useState('');

  if (schritt === 'alt') {
    return <PinFeld key="alt" titel={t.pin.alt} unter={t.pin.altText} zurueck={nav.zurueck}
      onPin={async (p): Promise<PinAntwort> => {
        const r = await wallet.pinPruefen(p);
        if (!r.ok) return r.grund;
        setAlt(p); setTimeout(() => setSchritt('neu'), 250);
        return 'ok';
      }} />;
  }
  if (schritt === 'neu') {
    return <PinFeld key="neu" titel={t.pin.neu} unter={t.pin.festlegenText} zurueck={nav.zurueck}
      onPin={async (p): Promise<PinAntwort> => {
        if (/^(\d)\1{5}$/.test(p) || ['123456', '654321'].includes(p)) return { fehler: t.pin.schwach };
        setNeu(p); setTimeout(() => setSchritt('nochmal'), 250);
        return 'ok';
      }} />;
  }
  return <PinFeld key="nochmal" titel={t.pin.wiederholen} unter={t.pin.wiederholenText} zurueck={() => setSchritt('neu')}
    onPin={async (p): Promise<PinAntwort> => {
      if (p !== neu) { setTimeout(() => setSchritt('neu'), 1400); return { fehler: t.pin.ungleich }; }
      if (!(await wallet.pinAendern(alt, neu))) return 'falsch';
      melde(t.pin.geaendert);
      setTimeout(nav.zurueck, 250);
      return 'ok';
    }} />;
}

/** Die 12 Woerter erneut zeigen -- nur gegen PIN, mit Sichtschutz. */
export function WoerterZeigen() {
  const t = useT();
  const nav = useNav();
  const wallet = useWallet();
  const [woerter, setWoerter] = useState<string[] | null>(null);
  const [offen, setOffen] = useState(false);

  useEffect(() => { if (!woerter) return; sichtschutz(true); return () => sichtschutz(false); }, [woerter]);

  if (!woerter) {
    return <PinFeld titel={t.pin.eingeben} unter={t.einst.woerterPin} zurueck={nav.zurueck}
      onPin={async (p): Promise<PinAntwort> => {
        const r = await wallet.pinPruefen(p);
        if (!r.ok) return r.grund;
        setTimeout(() => setWoerter(r.woerter.split(' ')), 250);
        return 'ok';
      }} />;
  }
  return (
    <div className="seite" style={{ gap: 20 }}>
      <KopfZurueck titel={t.woerter.ansehen} zurueck={nav.zurueck} />
      <Wortkarte woerter={woerter} offen={offen} umschalten={() => setOffen(o => !o)} />
      <Kasten art="warn" symbol="warnung"><strong>{t.woerter.warnStark}</strong> {t.woerter.warnText}</Kasten>
      <div className="fuell" />
      <button type="button" className="knopf sinkt" onClick={nav.zurueck}>{t.allg.fertig}</button>
    </div>
  );
}
