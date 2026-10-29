import { useEffect, useState, type ReactNode } from 'react';
import { useT, type Woerterbuch } from '../i18n/index.ts';
import { useNav, useWallet } from '../zustand.tsx';
import { keypairFromMnemonic } from '../kern/wallet.ts';
import { bioAktiv, bioPin } from '../wallet/biometrie.ts';
import { kopieren, einfuegen, erfolgGefuehl, fehlerGefuehl } from '../geraet/geraet.ts';
import { tippeText, tippeAdresse } from '../wallet/tasten.ts';
import {
  vorbereiten, signieren, alsJson, ausJson, pruefen, TEXT_MAX,
  type Pruefung, type Signiert, type Ablehnung,
} from '../wallet/nachricht.ts';
import { S, KopfZurueck, Kasten, PinFeld, Vierer, useMeldung, useZurueck, type PinAntwort } from '../ui/teile.tsx';
import { Tippfeld, useTastatur } from '../ui/tastatur.tsx';

/*
  Nachricht signieren -- fuer die Anmeldung auf YSKAR Lab und fuer freie Texte.

  eingabe -> pruefen (genaue Nachricht sehen) -> PIN oder Fingerabdruck -> fertig

  Diese Seite hat keinen Zugriff auf das Senden und macht keine Anfrage ins
  Netz. Der Schluessel wird nur fuer die eine Signatur aus dem Tresor geholt.
*/

/** Die Nachricht so zeigen, wie sie signiert wird: Zeile fuer Zeile, Feldnamen blass. */
export function Genau({ text, blassBis = 0 }: { text: string; blassBis?: number }) {
  return (
    <div className="genau waehlbar" aria-label={text}>
      {text.split('\n').map((z, i) => {
        if (i < blassBis) return <div key={i} className="blass">{z || ' '}</div>;
        const k = i > 0 && blassBis === 0 ? z.indexOf(': ') : -1;
        return <div key={i}>{k > 0 ? <><span className="blass">{z.slice(0, k + 1)}</span>{z.slice(k + 1)}</> : (z || ' ')}</div>;
      })}
    </div>
  );
}

export function ablaufText(t: Woerterbuch, bis: Date, jetzt: number): string {
  const ms = bis.getTime() - jetzt;
  if (ms <= 0) return t.sig.abgelaufen;
  return `${t.sig.inMin(Math.ceil(ms / 60000))} · ${bis.toISOString().slice(11, 16)} UTC`;
}

/** Laufende Uhr fuer die Ablaufzeit, ohne die Seite staendig neu zu zeichnen. */
export function useJetzt(aktiv: boolean): number {
  const [jetzt, setJetzt] = useState(Date.now());
  useEffect(() => {
    if (!aktiv) return;
    const u = setInterval(() => setJetzt(Date.now()), 5000);
    return () => clearInterval(u);
  }, [aktiv]);
  return jetzt;
}

export function Signieren() {
  const t = useT();
  const nav = useNav();
  const wallet = useWallet();
  const melde = useMeldung();
  const tast = useTastatur();
  const [eingabe, setEingabe] = useState('');
  const [schritt, setSchritt] = useState<'eingabe' | 'pruefen' | 'abgelehnt' | 'pin' | 'fertig'>('eingabe');
  const [vorlage, setVorlage] = useState<Pruefung | null>(null);
  const [ergebnis, setErgebnis] = useState<Signiert | null>(null);
  const [arbeitet, setArbeitet] = useState(false);
  const jetzt = useJetzt(schritt === 'pruefen');
  const adresse = wallet.adresse ?? '';

  const zurueck = () => {
    if (arbeitet) return;
    if (schritt === 'pruefen' || schritt === 'abgelehnt') setSchritt('eingabe');
    else if (schritt === 'pin') setSchritt('pruefen');
    else nav.zurueck();
  };
  useZurueck(zurueck, schritt !== 'eingabe');

  const weiter = () => {
    tast.oeffne(null);
    const p = vorbereiten(eingabe, adresse, Date.now());
    setVorlage(p);
    setSchritt(p.ok ? 'pruefen' : 'abgelehnt');
    if (!p.ok) fehlerGefuehl();
  };

  /** Mit der PIN den Schluessel holen und genau die gezeigte Nachricht signieren. */
  const signiereMit = async (pin: string): Promise<PinAntwort> => {
    if (!vorlage?.ok) return 'ok';
    const r = await wallet.pinPruefen(pin);
    if (!r.ok) return r.grund;
    setArbeitet(true);
    try {
      // Kurz vor dem Signieren noch einmal pruefen: Eine Anmeldung kann inzwischen abgelaufen sein.
      const nochmal = vorbereiten(vorlage.nachricht, adresse, Date.now());
      if (!nochmal.ok || nochmal.nachricht !== vorlage.nachricht) {
        setVorlage(nochmal.ok ? vorlage : nochmal); setSchritt('abgelehnt'); fehlerGefuehl();
        return 'ok';
      }
      const kp = keypairFromMnemonic(r.woerter);
      if (kp.address !== adresse) throw new Error('Tresor passt nicht zur Adresse');
      setErgebnis(signieren(vorlage.nachricht, kp.privateKey, kp.publicKey));
      erfolgGefuehl();
      setSchritt('fertig');
    } finally { setArbeitet(false); }
    return 'ok';
  };

  const bestaetigen = async () => {
    if (bioAktiv()) {
      const pin = await bioPin(t.sig.bioGrund, t.allg.abbrechen);
      if (pin && (await signiereMit(pin)) === 'ok') return;
    }
    setSchritt('pin');
  };

  const kopiere = async (text: string) => { if (await kopieren(text)) melde(t.sig.kopiert); };

  /* ------------------------------------------------------------------- PIN */
  if (schritt === 'pin') {
    return <PinFeld titel={t.sig.pinTitel} unter={t.sig.pinText} zurueck={zurueck} onPin={signiereMit} />;
  }

  /* ---------------------------------------------------------------- fertig */
  if (schritt === 'fertig' && ergebnis) {
    const kopierKnopf = (text: string, label: string) => (
      <button type="button" className="klein im-feld sinkt" aria-label={label} onClick={() => kopiere(text)}><S n="kopie" g={17} /></button>
    );
    return (
      <div className="seite">
        <KopfZurueck titel={t.sig.ergebnisTitel} zurueck={() => nav.zurueck()} />
        <div className="mitte-block">
          <div className="kachel ein gross"><S n="haken" g={28} w={2.4} /></div>
          <small>{t.sig.signiert}</small>
        </div>
        <div className="karte innen">
          <div className="paar hoch"><span>{t.sig.adresse}</span><Vierer adresse={ergebnis.address} /></div>
          <div className="paar hoch"><span>{t.sig.schluessel}</span>
            <div className="hexzeile"><span className="ziffern hex waehlbar">{ergebnis.publicKey}</span>{kopierKnopf(ergebnis.publicKey, t.sig.schluessel)}</div></div>
          <div className="paar hoch"><span>{t.sig.signatur}</span>
            <div className="hexzeile"><span className="ziffern hex waehlbar">{ergebnis.signature}</span>{kopierKnopf(ergebnis.signature, t.sig.signatur)}</div></div>
        </div>
        <div className="fuell" />
        <div className="stapel">
          <button type="button" className="knopf mit-symbol sinkt" onClick={() => kopiere(alsJson(ergebnis))}><S n="kopie" g={18} /><span>{t.sig.allesKopieren}</span></button>
          <button type="button" className="knopf zweit sinkt" onClick={() => nav.zurueck()}>{t.allg.fertig}</button>
        </div>
      </div>
    );
  }

  /* ------------------------------------------------------------- abgelehnt */
  if (schritt === 'abgelehnt' && vorlage && !vorlage.ok) {
    const grund: Ablehnung = vorlage.grund;
    const istAnmeldung = /\npurpose: login(\r?\n|$)/.test(eingabe);
    return (
      <div className="seite">
        <KopfZurueck titel={istAnmeldung ? t.sig.anmeldungTitel : t.sig.signieren} zurueck={zurueck} />
        <div className="mitte-block">
          <div className="kachel gross rotkachel"><S n="warnung" g={28} /></div>
          <small>{t.sig.abgelehntTitel}</small>
        </div>
        <Kasten art="rot" symbol="warnung"><b style={{ color: '#fff' }}>{t.sig.gruende[grund]}</b> {t.sig.nichtsSigniert}</Kasten>
        {grund !== 'leer' && grund !== 'zu_lang' && <>
          <div className="rubrik">{t.sig.erhalten}</div>
          <Genau text={eingabe.replace(/\r/g, '␍')} />
        </>}
        <div className="fuell" />
        <button type="button" className="knopf zweit sinkt" onClick={zurueck}>{t.allg.schliessen}</button>
      </div>
    );
  }

  /* --------------------------------------------------------------- pruefen */
  if (schritt === 'pruefen' && vorlage?.ok) {
    const fuss = (knopf: string, abgelaufen = false) => (
      <div className="stapel">
        <button type="button" className="knopf sinkt" disabled={arbeitet || abgelaufen} onClick={bestaetigen}>{knopf}</button>
        <div className="unterzeile">{bioAktiv() ? t.sig.danach : t.sig.danachPin}</div>
      </div>
    );
    if (vorlage.art === 'login') {
      const a = vorlage.anmeldung;
      const vorbei = a.expires.getTime() <= jetzt;
      return (
        <div className="seite">
          <KopfZurueck titel={t.sig.anmeldungTitel} zurueck={zurueck} />
          <div className="mitte-block">
            <div className="kachel aus gross"><S n="welt" g={28} /></div>
            <small>{t.sig.anmeldenBei}</small>
            <div className="domain">{a.domain}</div>
          </div>
          <div className="karte innen">
            <div className="paar rechts"><span>{t.sig.adresse}</span>
              <div><Vierer adresse={a.address} klasse="klein-schrift" /><span className="gutzeile winzig"><S n="haken" g={13} w={2.6} />{t.sig.dieseWallet}</span></div>
            </div>
            <div className="paar"><span>{t.sig.laeuftAb}</span>
              <span className="ziffern" style={{ textAlign: 'right', color: vorbei ? 'var(--rot)' : undefined }}>{ablaufText(t, a.expires, jetzt)}</span></div>
          </div>
          <div className="rubrik">{t.sig.genau}</div>
          <Genau text={vorlage.nachricht} />
          {vorbei
            ? <Kasten art="rot" symbol="warnung">{t.sig.gruende.abgelaufen}</Kasten>
            : <div className="hinweis"><S n="info" g={16} /><div>{t.sig.anmeldeHinweis}</div></div>}
          <div className="fuell" />
          {fuss(t.sig.anmelden, vorbei)}
        </div>
      );
    }
    return (
      <div className="seite">
        <KopfZurueck titel={t.sig.textTitel} zurueck={zurueck} />
        <div className="mitte-block">
          <div className="kachel aus gross"><S n="stift" g={28} /></div>
          <small>{t.sig.duSignierst}</small>
        </div>
        <div className="rubrik">{t.sig.genau}</div>
        <Genau text={vorlage.nachricht} blassBis={vorlage.ergaenzt ? 2 : 0} />
        {vorlage.ergaenzt && <div className="hinweis"><S n="info" g={16} /><div>{t.sig.ergaenztHinweis}</div></div>}
        <div className="karte innen">
          <div className="paar rechts"><span>{t.sig.signiertMit}</span><div><Vierer adresse={adresse} klasse="klein-schrift" /></div></div>
        </div>
        <div className="fuell" />
        {fuss(t.sig.signierenKnopf)}
      </div>
    );
  }

  /* --------------------------------------------------------------- eingabe */
  const zeichen = Array.from(eingabe).length;
  return (
    <div className="seite">
      <KopfZurueck titel={t.sig.signieren} zurueck={() => nav.zurueck()} />
      <p className="fliess" style={{ fontSize: 15 }}>{t.sig.einleitung}</p>
      <div className="feldgruppe">
        <div className="feldkopf"><span className="feldname">{t.sig.nachricht}</span><small>{zeichen} / {TEXT_MAX}</small></div>
        <Tippfeld id="nachricht" art="text" klasse="feld nachrichtfeld" label={t.sig.nachricht} wert={eingabe} platz={t.sig.platz} gross="anfang"
                  onTaste={z => setEingabe(n => tippeText(n, z, TEXT_MAX))}
                  einfuegen={x => setEingabe(x)}
                  leiste={eingabe !== '' && <button type="button" className="tknopf" onClick={() => setEingabe('')}>{t.sig.leeren}</button>} />
      </div>
      <button type="button" className="knopf zweit mit-symbol sinkt" onClick={async () => { const x = await einfuegen(); if (x) setEingabe(x); }}>
        <S n="einfuegen" g={19} /><span>{t.sig.einfuegen}</span>
      </button>
      <div className="hinweis"><S n="info" g={16} /><div>{t.sig.kopfHinweis}</div></div>
      <div className="fuell" />
      <button type="button" className="knopf sinkt" disabled={eingabe.trim() === ''} onClick={weiter}>{t.allg.weiter}</button>
    </div>
  );
}

/* ================================================================ Pruefen */

function Feld({ id, name, wert, art, onTaste, onEinfuegen, klasse }: {
  id: string; name: string; wert: string; art: 'text' | 'adresse';
  onTaste: (z: string) => void; onEinfuegen: (x: string) => void; klasse?: string;
}): ReactNode {
  const t = useT();
  return (
    <div className="feldgruppe" style={{ gap: 6 }}>
      <div className="feldkopf"><span className="feldname">{name}</span></div>
      <div className="feld-huelle">
        <Tippfeld id={id} art={art} klasse={`ziffern ${klasse ?? ''}`} label={name} wert={wert} onTaste={onTaste} einfuegen={onEinfuegen}
                  leiste={wert !== '' && <button type="button" className="tknopf" onClick={() => onEinfuegen('')}>{t.sig.leeren}</button>} />
        <button type="button" className="klein im-feld sinkt" aria-label={t.allg.einfuegen}
                onClick={async () => { const x = await einfuegen(); if (x) onEinfuegen(x); }}><S n="einfuegen" g={18} /></button>
      </div>
    </div>
  );
}

export function SignaturPruefen() {
  const t = useT();
  const nav = useNav();
  const melde = useMeldung();
  const [s, setS] = useState<Signiert>({ address: '', publicKey: '', signature: '', message: '' });
  const setze = (f: keyof Signiert) => (x: string) => setS(a => ({ ...a, [f]: x }));
  const tippe = (f: keyof Signiert, max: number) => (z: string) => setS(a => ({ ...a, [f]: tippeText(a[f], z, max) }));
  const alleDa = s.address !== '' && s.publicKey !== '' && s.signature !== '' && s.message !== '';
  const ergebnis = alleDa ? pruefen(s) : null;

  const jsonHolen = async () => {
    const j = ausJson(await einfuegen());
    if (j) setS(j); else melde(t.sig.keinJson);
  };

  const anzeige = ergebnis === 'gueltig' ? { art: 'gruen', titel: t.sig.gueltig, text: t.sig.gueltigText, n: 'haken' as const }
    : ergebnis === 'ungueltig' ? { art: 'rot', titel: t.sig.ungueltig, text: t.sig.ungueltigText, n: 'warnung' as const }
    : ergebnis === 'adresse_passt_nicht' ? { art: 'rot', titel: t.sig.passtNicht, text: t.sig.passtNichtText, n: 'warnung' as const }
    : ergebnis === 'format' ? { art: 'ruhig', titel: t.sig.format, text: t.sig.formatText, n: 'info' as const }
    : null;

  return (
    <div className="seite">
      <KopfZurueck titel={t.sig.pruefTitel} zurueck={() => nav.zurueck()} />
      <button type="button" className="knopf zweit mit-symbol sinkt" onClick={jsonHolen}><S n="einfuegen" g={19} /><span>{t.sig.jsonEinfuegen}</span></button>
      <Feld id="p-nachricht" name={t.sig.nachricht} art="text" klasse="mehrzeilig" wert={s.message} onTaste={tippe('message', 4000)} onEinfuegen={setze('message')} />
      <Feld id="p-adresse" name={t.sig.adresse} art="adresse" wert={s.address} onTaste={z => setS(a => ({ ...a, address: tippeAdresse(a.address, z) }))} onEinfuegen={x => setze('address')(x.trim())} />
      <Feld id="p-schluessel" name={t.sig.schluessel} art="text" wert={s.publicKey} onTaste={tippe('publicKey', 64)} onEinfuegen={x => setze('publicKey')(x.trim())} />
      <Feld id="p-signatur" name={t.sig.signatur} art="text" wert={s.signature} onTaste={tippe('signature', 128)} onEinfuegen={x => setze('signature')(x.trim())} />
      {anzeige && (
        <div className={`kasten ${anzeige.art === 'gruen' ? 'gruen' : anzeige.art}`} role="status">
          <S n={anzeige.n} g={20} w={anzeige.n === 'haken' ? 2.6 : 2} /><div><b style={{ color: '#fff' }}>{anzeige.titel}</b> {anzeige.text}</div>
        </div>
      )}
    </div>
  );
}
