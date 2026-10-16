import { useEffect, useState } from 'react';
import { useT, sprachCode } from '../i18n/index.ts';
import { useDaten, useNav, useWallet } from '../zustand.tsx';
import { einstellen, useEinstellungen, type Sprache } from '../wallet/einstellungen.ts';
import { bioAktiv, bioAusschalten, bioEinschalten, bioStand } from '../wallet/biometrie.ts';
import { kopieren, sichtschutz } from '../geraet/geraet.ts';
import { istNativ, letzteFehler } from '../geraet/plattform.ts';
import { S, Kopf, KopfZurueck, Blatt, Kasten, PinFeld, useMeldung, type PinAntwort } from '../ui/teile.tsx';
import { Wortkarte } from './Einstieg.tsx';

declare const __VERSION__: string;

export function Einstellungen() {
  const t = useT();
  const nav = useNav();
  const wallet = useWallet();
  const daten = useDaten();
  const melde = useMeldung();
  const e = useEinstellungen();
  const [bio, setBio] = useState(bioAktiv());
  const [blatt, setBlatt] = useState<null | 'sperre' | 'sprache' | 'entfernen'>(null);
  const [pinFuer, setPinFuer] = useState<null | 'bio' | 'entfernen'>(null);

  const sperrText = (s: number) => (s === 0 ? t.einst.sofort : s === 60 ? t.einst.min1 : t.einst.min5);
  const sprachText = (s: Sprache) => (s === 'system' ? t.einst.system : s === 'de' ? 'Deutsch' : 'English');

  const fingerUmschalten = async () => {
    if (bio) { await bioAusschalten(); setBio(false); return; }
    const stand = await bioStand();
    if (!stand.da) { melde(t.bio.fehlt(stand.grund)); return; }
    setPinFuer('bio');
  };

  const diagnose = async () => {
    const text = [
      `YSKAR Wallet ${__VERSION__}`, `App: ${istNativ() ? 'Android' : 'Browser'}`, `Sprache: ${sprachCode(e.sprache)}`,
      `Netz: ${daten.offline ? 'keine Verbindung' : 'verbunden'}, Hoehe ${daten.spitze ?? '?'}`,
      `Wartende vom Knoten: ${daten.konto?.pendingSource ?? '?'}`, `Fingerabdruck: ${bio ? 'an' : 'aus'}`,
      '', ...letzteFehler(),
    ].join('\n');
    if (await kopieren(text)) melde(t.einst.diagnoseKopiert);
  };

  if (pinFuer) {
    return <PinFeld titel={t.pin.eingeben} unter={pinFuer === 'bio' ? t.einst.bioPin : t.einst.entfernenPin} zurueck={() => setPinFuer(null)}
      onPin={async (pin): Promise<PinAntwort> => {
        const r = await wallet.pinPruefen(pin);
        if (!r.ok) return r.grund;
        if (pinFuer === 'bio') {
          const ok = await bioEinschalten(pin, t.bio.grund, t.allg.abbrechen);
          setBio(ok); if (!ok) melde(t.bio.nichtGeklappt);
          setPinFuer(null);
        } else {
          await wallet.vergessen();
        }
        return 'ok';
      }} />;
  }

  const pfeil = <S n="weiter" g={18} w={2.2} />;
  const kachel = (n: Parameters<typeof S>[0]['n'], rot = false) => <span className={`kachel klein-kachel ${rot ? 'rot' : ''}`}><S n={n} g={18} /></span>;
  return (
    <div className="seite mit-nav eng">
      <Kopf titel={t.einst.titel} />

      <div className="rubrik">{t.einst.sicherheit}</div>
      <div className="karte innen" style={{ padding: '0 16px' }}>
        <button type="button" className="einstellzeile" onClick={() => nav.gehe({ n: 'pinAendern' })}>{kachel('schloss')}<span>{t.einst.pinAendern}</span><span className="rechts">{pfeil}</span></button>
        <div className="einstellzeile">
          {kachel('finger')}<span id="l-finger">{t.einst.finger}</span>
          <button type="button" role="switch" className="schalter" aria-checked={bio} aria-labelledby="l-finger" onClick={fingerUmschalten}><span /></button>
        </div>
        <button type="button" className="einstellzeile" onClick={() => nav.gehe({ n: 'woerterZeigen' })}>{kachel('auge')}<span>{t.einst.woerter}</span><span className="rechts">{pfeil}</span></button>
        <button type="button" className="einstellzeile" onClick={() => setBlatt('sperre')}>{kachel('uhr')}<span>{t.einst.sperre}</span><span className="rechts"><span>{sperrText(e.sperreNach)}</span>{pfeil}</span></button>
      </div>

      <div className="rubrik">{t.einst.allgemein}</div>
      <div className="karte innen" style={{ padding: '0 16px' }}>
        <button type="button" className="einstellzeile" onClick={() => setBlatt('sprache')}>{kachel('welt')}<span>{t.einst.sprache}</span><span className="rechts"><span>{sprachText(e.sprache)}</span>{pfeil}</span></button>
      </div>

      <div className="rubrik">{t.einst.ueber}</div>
      <div className="karte innen" style={{ padding: '0 16px' }}>
        <div className="einstellzeile">{kachel('info')}<span>{t.einst.version}</span><span className="rechts ziffern" style={{ fontSize: 14 }}>{__VERSION__}</span></div>
        <div className="einstellzeile">{kachel('block')}<span>{t.einst.netz}</span><span className="rechts">{t.einst.hauptkette}</span></div>
        <button type="button" className="einstellzeile" onClick={diagnose}>{kachel('kopie')}<span>{t.einst.diagnose}</span><span className="rechts"><S n="kopie" g={18} /></span></button>
      </div>

      <div className="rubrik">{t.einst.wallet}</div>
      <div className="karte innen" style={{ padding: '0 16px' }}>
        <button type="button" className="einstellzeile rot" onClick={() => setBlatt('entfernen')}>{kachel('weg', true)}<span>{t.einst.entfernen}</span></button>
      </div>

      {blatt === 'sperre' && (
        <Blatt titel={t.einst.sperreTitel} onZu={() => setBlatt(null)}>
          <p>{t.einst.sperreText}</p>
          <div className="karte innen" style={{ padding: '0 16px' }}>
            {[0, 60, 300].map(s => (
              <button key={s} type="button" className="einstellzeile" aria-pressed={e.sperreNach === s} onClick={() => { einstellen({ sperreNach: s }); setBlatt(null); }}>
                <span>{sperrText(s)}</span>{e.sperreNach === s && <span className="rechts" style={{ color: 'var(--blau-hell)' }}><S n="haken" g={18} w={2.6} /></span>}
              </button>
            ))}
          </div>
        </Blatt>
      )}
      {blatt === 'sprache' && (
        <Blatt titel={t.einst.sprache} onZu={() => setBlatt(null)}>
          <div className="karte innen" style={{ padding: '0 16px' }}>
            {(['system', 'de', 'en'] as Sprache[]).map(s => (
              <button key={s} type="button" className="einstellzeile" aria-pressed={e.sprache === s} onClick={() => { einstellen({ sprache: s }); setBlatt(null); }}>
                <span>{sprachText(s)}</span>{e.sprache === s && <span className="rechts" style={{ color: 'var(--blau-hell)' }}><S n="haken" g={18} w={2.6} /></span>}
              </button>
            ))}
          </div>
        </Blatt>
      )}
      {blatt === 'entfernen' && (
        <Blatt titel={t.einst.entfernenTitel} onZu={() => setBlatt(null)}>
          <Kasten art="rot" symbol="warnung">{t.einst.entfernenText}</Kasten>
          <div className="stapel">
            <button type="button" className="knopf gefahr sinkt" onClick={() => { setBlatt(null); setPinFuer('entfernen'); }}>{t.einst.entfernenJa}</button>
            <button type="button" className="knopf zweit sinkt" onClick={() => setBlatt(null)}>{t.allg.abbrechen}</button>
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
