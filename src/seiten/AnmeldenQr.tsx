import { useState } from 'react';
import { useT } from '../i18n/index.ts';
import { useNav, useWallet } from '../zustand.tsx';
import { keypairFromMnemonic } from '../kern/wallet.ts';
import { bioAktiv, bioPin } from '../wallet/biometrie.ts';
import { erfolgGefuehl, fehlerGefuehl } from '../geraet/geraet.ts';
import { anmeldungAusCode, signieren, textBase64url, type QrPruefung } from '../wallet/nachricht.ts';
import { rueckrufSenden, type RueckrufErgebnis } from '../netz/rueckruf.ts';
import { S, KopfZurueck, Kasten, PinFeld, Vierer, useZurueck, type PinAntwort } from '../ui/teile.tsx';
import { Scanner } from '../ui/scanner.tsx';
import { Genau, ablaufText, useJetzt } from './Signieren.tsx';

/*
  Anmelden per QR-Code (oder yskar:login-Link auf demselben Handy).

  scannen -> pruefen -> PIN oder Fingerabdruck -> senden -> angemeldet / Fehler

  Vor der Bestaetigung geht nichts ins Netz. Danach genau ein POST an den
  Rueckruf, dessen Host vorher gegen die Domain der Nachricht geprueft wurde.
*/

type Schritt = 'scannen' | 'pruefen' | 'pin' | 'sendet' | 'fertig' | 'fehler' | 'abgelehnt';

export function AnmeldenQr({ code }: { code?: string }) {
  const t = useT();
  const nav = useNav();
  const wallet = useWallet();
  const adresse = wallet.adresse ?? '';
  const erstePruefung = (c: string) => anmeldungAusCode(c, adresse, Date.now());
  const [pruefung, setPruefung] = useState<QrPruefung | null>(() => (code ? erstePruefung(code) : null));
  const [schritt, setSchritt] = useState<Schritt>(() => (!code ? 'scannen' : pruefung?.ok ? 'pruefen' : 'abgelehnt'));
  const [antwort, setAntwort] = useState<RueckrufErgebnis | null>(null);
  const jetzt = useJetzt(schritt === 'pruefen');

  const zurueck = () => {
    if (schritt === 'sendet') return;
    if (schritt === 'pin') setSchritt('pruefen');
    else nav.zurueck();
  };
  useZurueck(zurueck, schritt !== 'scannen' && schritt !== 'sendet');

  const gescannt = (text: string): boolean => {
    const p = erstePruefung(text);
    // Ein fremder QR-Code (z. B. eine Adresse) ist kein Fehler: weitersuchen.
    if (!p.ok && p.grund === 'qr_format') return false;
    setPruefung(p);
    setSchritt(p.ok ? 'pruefen' : 'abgelehnt');
    if (!p.ok) fehlerGefuehl();
    return true;
  };

  const anmeldenMit = async (pin: string): Promise<PinAntwort> => {
    if (!pruefung?.ok) return 'ok';
    const r = await wallet.pinPruefen(pin);
    if (!r.ok) return r.grund;
    // Kurz vor dem Signieren noch einmal pruefen: Die Anfrage kann inzwischen abgelaufen sein.
    const nochmal = anmeldungAusCode(
      `yskar:login?msg=${textBase64url(pruefung.nachricht)}&cb=${textBase64url(pruefung.rueckruf)}`,
      adresse, Date.now());
    if (!nochmal.ok) { setPruefung(nochmal); setSchritt('abgelehnt'); fehlerGefuehl(); return 'ok'; }
    const kp = keypairFromMnemonic(r.woerter);
    if (kp.address !== adresse) throw new Error('Tresor passt nicht zur Adresse');
    const s = signieren(pruefung.nachricht, kp.privateKey, kp.publicKey);
    setSchritt('sendet');
    const a = await rueckrufSenden(pruefung.rueckruf, s);
    setAntwort(a);
    if (a.ok) { erfolgGefuehl(); setSchritt('fertig'); } else { fehlerGefuehl(); setSchritt('fehler'); }
    return 'ok';
  };

  const bestaetigen = async () => {
    if (bioAktiv()) {
      const pin = await bioPin(t.sig.bioGrund, t.allg.abbrechen);
      if (pin && (await anmeldenMit(pin)) === 'ok') return;
    }
    setSchritt('pin');
  };

  if (schritt === 'scannen') {
    return (
      <div className="seite">
        <Scanner onZu={() => nav.zurueck()} onText={gescannt} zweck={t.scan.anmeldung} />
      </div>
    );
  }

  if (schritt === 'pin') {
    return <PinFeld titel={t.sig.pinTitel} unter={t.sig.pinText} zurueck={zurueck} onPin={anmeldenMit} />;
  }

  const domain = pruefung?.ok ? pruefung.anmeldung.domain : (pruefung?.domain ?? '');

  if (schritt === 'sendet') {
    return (
      <div className="seite">
        <div className="fuell" />
        <div className="mitte-block">
          <div className="kachel aus gross atmet"><S n="welt" g={28} /></div>
          <small>{t.qr.sendet(domain)}</small>
        </div>
        <div className="hinweis" style={{ justifyContent: 'center' }}><S n="info" g={16} /><div>{t.qr.sendetHinweis}</div></div>
        <div className="fuell" />
      </div>
    );
  }

  if (schritt === 'fertig') {
    return (
      <div className="seite">
        <div className="fuell" />
        <div className="mitte-block">
          <div className="kachel ein gross"><S n="haken" g={28} w={2.4} /></div>
          <div className="titel2">{t.qr.angemeldet}</div>
          <p className="fliess mittig" style={{ fontSize: 15 }}>{t.qr.angemeldetText(domain)}</p>
        </div>
        <div className="fuell" />
        <button type="button" className="knopf sinkt" onClick={() => nav.zurueck()}>{t.allg.fertig}</button>
      </div>
    );
  }

  if (schritt === 'fehler' && antwort && !antwort.ok) {
    const text = antwort.art === 'netz' ? t.qr.netz : antwort.art === 'weiterleitung' ? t.qr.weiterleitung : null;
    return (
      <div className="seite">
        <KopfZurueck titel={t.qr.titel} zurueck={() => nav.zurueck()} />
        <div className="mitte-block">
          <div className="kachel gross rotkachel"><S n="warnung" g={28} /></div>
          <small>{t.qr.nichtAngenommen(domain)}</small>
        </div>
        <Kasten art="rot" symbol="warnung">
          {text ?? (antwort.text ? <><b style={{ color: '#fff' }}>{t.qr.vonSeite}</b> {t.qr.zitat(antwort.text)}</> : t.qr.keinText)}
        </Kasten>
        <div className="hinweis"><S n="info" g={16} /><div>{t.qr.neuerCode}</div></div>
        <div className="fuell" />
        <button type="button" className="knopf zweit sinkt" onClick={() => nav.zurueck()}>{t.allg.schliessen}</button>
      </div>
    );
  }

  if (schritt === 'abgelehnt' && pruefung && !pruefung.ok) {
    return (
      <div className="seite">
        <KopfZurueck titel={t.qr.titel} zurueck={() => nav.zurueck()} />
        <div className="mitte-block">
          <div className="kachel gross rotkachel"><S n="warnung" g={28} /></div>
          <small>{t.qr.abgelehnt}</small>
        </div>
        <Kasten art="rot" symbol="warnung"><b style={{ color: '#fff' }}>{t.sig.gruende[pruefung.grund]}</b> {t.sig.nichtsSigniert}</Kasten>
        {pruefung.domain && pruefung.rueckrufHost && (
          <div className="karte innen">
            <div className="paar"><span>{t.qr.seite}</span><span className="ziffern">{pruefung.domain}</span></div>
            <div className="paar"><span>{t.qr.antwortAn}</span><span className="ziffern" style={{ color: 'var(--rot)', overflowWrap: 'anywhere', textAlign: 'right' }}>{pruefung.rueckrufHost}</span></div>
          </div>
        )}
        <div className="fuell" />
        <button type="button" className="knopf zweit sinkt" onClick={() => nav.zurueck()}>{t.allg.schliessen}</button>
      </div>
    );
  }

  if (!pruefung?.ok) return null;
  const a = pruefung.anmeldung;
  const vorbei = a.expires.getTime() <= jetzt;
  const ziel = (() => { try { const u = new URL(pruefung.rueckruf); return u.host + u.pathname; } catch { return pruefung.rueckruf; } })();
  return (
    <div className="seite">
      <KopfZurueck titel={t.qr.titel} zurueck={zurueck} />
      <div className="mitte-block">
        <div className="kachel aus gross"><S n="welt" g={28} /></div>
        <small>{t.qr.frage}</small>
        <div className="domain">{a.domain}?</div>
      </div>
      <div className="karte innen">
        <div className="paar rechts"><span>{t.sig.adresse}</span>
          <div><Vierer adresse={a.address} klasse="klein-schrift" /><span className="gutzeile winzig"><S n="haken" g={13} w={2.6} />{t.sig.dieseWallet}</span></div>
        </div>
        <div className="paar"><span>{t.sig.laeuftAb}</span>
          <span className="ziffern" style={{ textAlign: 'right', color: vorbei ? 'var(--rot)' : undefined }}>{ablaufText(t, a.expires, jetzt)}</span></div>
        <div className="paar"><span>{t.qr.antwortAn}</span>
          <span className="ziffern" style={{ textAlign: 'right', fontSize: 12, overflowWrap: 'anywhere' }}>{ziel}</span></div>
      </div>
      <div className="rubrik">{t.sig.genau}</div>
      <Genau text={pruefung.nachricht} />
      {vorbei && <Kasten art="rot" symbol="warnung">{t.sig.gruende.abgelaufen}</Kasten>}
      <div className="fuell" />
      <div className="stapel">
        <button type="button" className="knopf sinkt" disabled={vorbei} onClick={bestaetigen}>{t.sig.anmelden}</button>
        <button type="button" className="knopf zweit sinkt" onClick={() => nav.zurueck()}>{t.qr.abbrechen}</button>
        <div className="unterzeile">{bioAktiv() ? t.sig.danach : t.sig.danachPin}</div>
      </div>
    </div>
  );
}
