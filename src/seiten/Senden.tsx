import { useEffect, useMemo, useState } from 'react';
import { useT, type Woerterbuch } from '../i18n/index.ts';
import { useDaten, useNav, useWallet } from '../zustand.tsx';
import { isValidAddress, decodeAddress } from '../kern/address.ts';
import { buildTransfer, serializeTx, txidHex, transferBytes, checkTransfer } from '../kern/tx.ts';
import { keypairFromMnemonic } from '../kern/wallet.ts';
import { toHex } from '../kern/codec.ts';
import { MIN_FEE, DUST_LIMIT, DECIMALS } from '../kern/params.ts';
import { marktLaden, senden, NetzFehler, type Markt } from '../netz/api.ts';
import { betragGenau, betragText, dezimalZeichen, eingabeEinheiten, einheitenEingabe } from '../wallet/betrag.ts';
import { NOTIZ_BYTES, notizBytes, notizKuerzen } from '../wallet/notiz.ts';
import { zahlungAusText } from '../wallet/qr.ts';
import { useKontakte } from '../wallet/kontakte.ts';
import { bioAktiv, bioPin } from '../wallet/biometrie.ts';
import { gesendetMerken } from '../wallet/gesendet.ts';
import { einfuegen, erfolgGefuehl, fehlerGefuehl } from '../geraet/geraet.ts';
import { fehlerMerken } from '../geraet/plattform.ts';
import { S, KopfZurueck, Betrag, Vierer, Kasten, Schieber, PinFeld, Bild, useMeldung, useZurueck, type PinAntwort } from '../ui/teile.tsx';
import { Scanner } from '../ui/scanner.tsx';
import type { Zahlung } from '../wallet/zahlungen.ts';

type Schritt = 'eingabe' | 'pruefen' | 'pin' | 'fertig';

function fehlerText(t: Woerterbuch, grund: string): string {
  const bekannt = t.fehler as unknown as Record<string, unknown>;
  const text = bekannt[grund];
  return typeof text === 'string' ? text : t.fehler.sonst(grund);
}

/**
 * Senden: Eingabe -> Pruefen -> Bestaetigen -> Gesendet.
 *
 * Die Ueberweisung wird VOLLSTAENDIG auf dem Geraet gebaut und signiert.
 * Was hier im Pruefschritt steht, wird signiert -- nichts anderes.
 */
export function Senden({ an, betrag: vorBetrag, notiz: vorNotiz, scan }: { an?: string; betrag?: bigint | null; notiz?: string | null; scan?: boolean }) {
  const t = useT();
  const nav = useNav();
  const wallet = useWallet();
  const daten = useDaten();
  const melde = useMeldung();
  const { kontakte, name } = useKontakte();
  const zeichen = dezimalZeichen(t.code);

  const [schritt, setSchritt] = useState<Schritt>('eingabe');
  const [ziel, setZiel] = useState(an ?? '');
  const [betrag, setBetrag] = useState(vorBetrag ? einheitenEingabe(vorBetrag, zeichen, DECIMALS, DECIMALS) : '');
  const [alles, setAlles] = useState(false);
  const [notiz, setNotiz] = useState(vorNotiz ?? '');
  const [scannt, setScannt] = useState(!!scan);
  const [markt, setMarkt] = useState<Markt | null>(null);
  const [fehler, setFehler] = useState('');
  const [arbeitet, setArbeitet] = useState(false);
  const [versuch, setVersuch] = useState(0);
  const [ergebnis, setErgebnis] = useState<Zahlung | null>(null);

  useEffect(() => { marktLaden().then(setMarkt).catch(e => fehlerMerken('gebuehren', e)); }, []);

  const adresse = ziel.trim().toLowerCase();
  const zielGueltig = isValidAddress(adresse);
  const eigene = zielGueltig && adresse === wallet.adresse;
  const zielName = zielGueltig ? name(adresse) : null;

  // Ohne Auskunft vom Knoten gilt die alte Mindestgebuehr: Sie deckt jede Regel.
  // Mit Auskunft: Stufe "normal", aber nie unter dem Satz je Byte fuer diese Groesse.
  const gebuehr = useMemo(() => {
    if (!markt) return MIN_FEE;
    const boden = markt.mindestJeByte ? BigInt(markt.mindestJeByte) * BigInt(transferBytes(notizBytes(notiz))) : 0n;
    const stufe = BigInt(markt.stufen.normal.fee);
    return stufe > boden ? stufe : boden;
  }, [markt, notiz]);

  const guthaben = daten.verfuegbar;
  const einheiten = useMemo(() => eingabeEinheiten(betrag, DECIMALS), [betrag]);

  // "Alles" bleibt alles, auch wenn eine Notiz die Gebuehr veraendert -- aber nur bei der Eingabe.
  useEffect(() => {
    if (!alles || schritt !== 'eingabe') return;
    const frei = guthaben > gebuehr ? guthaben - gebuehr : 0n;
    setBetrag(einheitenEingabe(frei, zeichen, DECIMALS, DECIMALS));
  }, [alles, schritt, guthaben, gebuehr, zeichen]);

  const staub = !!markt?.mindestJeByte && einheiten > 0n && einheiten < DUST_LIMIT;
  const summe = einheiten + gebuehr;
  const reicht = einheiten > 0n && summe <= guthaben;
  const bereit = zielGueltig && !eigene && reicht && !staub && daten.konto !== null;
  const genau = (v: bigint) => betragGenau(v, t.code, DECIMALS, 4);

  // An diese Adresse schon einmal gesendet? Nur ein Hinweis, kein Urteil.
  const bekannt = !!zielName || (daten.konto?.history ?? []).some(e => e.kind === 'out' && e.counterparty === adresse)
    || daten.wartende.some(p => p.kind === 'out' && p.to === adresse);

  const uebernehmen = (text: string): boolean => {
    const z = zahlungAusText(text, DECIMALS);
    if (!z) return false;
    setZiel(z.adresse);
    if (z.betrag) { setAlles(false); setBetrag(einheitenEingabe(z.betrag, zeichen, DECIMALS, DECIMALS)); }
    if (z.notiz) setNotiz(z.notiz);
    return true;
  };

  const zurueck = () => {
    if (arbeitet) return;
    if (schritt === 'pruefen') { setFehler(''); setSchritt('eingabe'); }
    else if (schritt === 'pin') setSchritt('pruefen');
    else if (schritt === 'fertig') nav.reiter('start');
    else nav.zurueck();
  };
  useZurueck(zurueck, schritt !== 'eingabe');

  /** Signieren und senden. Gibt zurueck, ob die PIN stimmte. */
  const sendeMit = async (pin: string): Promise<PinAntwort> => {
    const r = await wallet.pinPruefen(pin);
    if (!r.ok) return r.grund;
    setArbeitet(true); setFehler('');
    try {
      const kp = keypairFromMnemonic(r.woerter);
      if (kp.address !== wallet.adresse) throw new Error('Tresor passt nicht zur Adresse');
      const tx = buildTransfer({
        from: kp.addressRaw, to: decodeAddress(adresse), amount: einheiten, fee: gebuehr, nonce: daten.naechsteNonce,
        memo: notiz ? new TextEncoder().encode(notizKuerzen(notiz)) : undefined,
        publicKey: kp.publicKey, privateKey: kp.privateKey,
      });
      // Dieselbe Pruefung, die der Knoten macht -- vor dem Senden, auf dem Geraet.
      // Ohne bekannte Hoehe waere die Gebuehrenregel geraten -- dann prueft nur der Knoten.
      const mangel = daten.spitze !== null ? checkTransfer(tx, daten.spitze + 1) : null;
      if (mangel) throw new Error(mangel);
      const id = txidHex(tx);
      const antwort = await senden(toHex(serializeTx(tx)));
      // "duplicate": genau diese Ueberweisung liegt schon im Netz -- ein frueherer Versuch kam an.
      if (!antwort.ok && antwort.grund !== 'duplicate') {
        fehlerGefuehl();
        setFehler(fehlerText(t, antwort.grund));
        setVersuch(v => v + 1); setSchritt('pruefen');
        return 'ok';
      }
      const jetzt = Math.floor(Date.now() / 1000);
      gesendetMerken({ txid: id, an: adresse, betrag: einheiten.toString(), gebuehr: gebuehr.toString(),
                       nonce: daten.naechsteNonce.toString(), notiz: notizKuerzen(notiz), zeit: jetzt });
      setErgebnis({ txid: id, art: 'aus', gegen: adresse, betrag: einheiten, gebuehr, notiz: notizKuerzen(notiz), zeit: jetzt, hoehe: null });
      erfolgGefuehl();
      setSchritt('fertig');
      daten.neuLaden();
    } catch (e) {
      fehlerMerken('senden', e);
      fehlerGefuehl();
      setFehler(e instanceof NetzFehler ? (e.art === 'offline' ? t.fehler.offline : t.fehler.antwort) : fehlerText(t, String((e as Error)?.message ?? e)));
      setVersuch(v => v + 1); setSchritt('pruefen');
    } finally { setArbeitet(false); }
    return 'ok';
  };

  const geschoben = async () => {
    if (bioAktiv()) {
      const pin = await bioPin(t.senden.bioGrund(genau(einheiten)), t.allg.abbrechen);
      if (pin) { const r = await sendeMit(pin); if (r === 'ok') return; }
    }
    setSchritt('pin');
  };

  /* ---------------------------------------------------------------- fertig */
  if (schritt === 'fertig' && ergebnis) {
    const bestaetigt = (daten.konto?.history ?? []).some(e => e.txid === ergebnis.txid);
    const wer = name(ergebnis.gegen);
    return (
      <div className="seite">
        <div className="fuell" />
        <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 22 }}>
          <div className="gruenfeld">
            <svg width="64" height="64" viewBox="0 0 64 64" aria-hidden="true"><circle className="ring" cx="32" cy="32" r="30" transform="rotate(-90 32 32)" /><path className="strich" d="M19 33l9 9 17-19" /></svg>
          </div>
          <div className="auf" style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 10, animationDelay: '.5s' }}>
            <h1 className="titel2">{t.senden.gesendet}</h1>
            <Betrag wert={ergebnis.betrag} klasse="mittel" />
            <p style={{ fontSize: 15, color: 'var(--leise)' }}>{t.senden.anWen}{' '}
              {wer ? <b style={{ color: 'var(--lesen)' }}>{wer}</b> : <span className="ziffern" style={{ color: 'var(--lesen)' }}>{adresse.slice(0, 8)}…{adresse.slice(-4)}</span>}</p>
          </div>
          <div className="karte auf" style={{ alignSelf: 'stretch', flexDirection: 'row', gap: 14, padding: '16px 18px', alignItems: 'flex-start', borderRadius: 24, animationDelay: '.62s' }}>
            <div className={`kachel ${bestaetigt ? 'ein' : 'bern'}`}><S n={bestaetigt ? 'haken' : 'uhr'} g={20} w={bestaetigt ? 2.6 : 2} /></div>
            <div>
              <b style={{ display: 'block', fontSize: 15 }}>{bestaetigt ? t.senden.bestaetigtTitel : t.senden.wartetTitel}</b>
              <span style={{ display: 'block', marginTop: 3, fontSize: 14, lineHeight: 1.45, color: 'var(--leise)' }}>{bestaetigt ? t.senden.bestaetigtText : t.senden.wartetText}</span>
            </div>
          </div>
        </div>
        <div className="fuell" />
        <div className="stapel auf" style={{ animationDelay: '.74s' }}>
          <button type="button" className="knopf sinkt" onClick={() => nav.reiter('start')}>{t.senden.uebersicht}</button>
          <button type="button" className="knopf zweit sinkt" onClick={() => nav.ersetze({ n: 'detail', zahlung: ergebnis })}>{t.senden.ansehen}</button>
        </div>
      </div>
    );
  }

  /* ------------------------------------------------------------------- PIN */
  if (schritt === 'pin') {
    return <PinFeld titel={t.pin.eingeben} unter={arbeitet ? t.senden.sendet : t.senden.pinText} zurueck={zurueck} onPin={sendeMit} />;
  }

  /* --------------------------------------------------------------- pruefen */
  if (schritt === 'pruefen') {
    return (
      <div className="seite">
        <KopfZurueck titel={t.senden.pruefen} zurueck={zurueck} />
        <div className="mitte-block"><small>{t.senden.duSendest}</small><Betrag wert={einheiten} /></div>
        <div className="karte innen" style={{ borderRadius: 24 }}>
          <div className="paar hoch">
            <span>{t.senden.an}</span>
            {zielName && <b>{zielName}</b>}
            <Vierer adresse={adresse} />
          </div>
          <div className="paar"><span>{t.senden.betrag}</span><span className="ziffern">{genau(einheiten)} YSR</span></div>
          <div className="paar"><span>{t.senden.netzgebuehr}</span><span className="ziffern">{genau(gebuehr)} YSR</span></div>
          {notiz && <div className="paar"><span>{t.senden.notiz}</span><span style={{ fontWeight: 600, textAlign: 'right', overflowWrap: 'anywhere' }}>{notizKuerzen(notiz)}</span></div>}
          <div className="paar summe"><b>{t.senden.gesamt}</b><span className="ziffern">{genau(summe)} YSR</span></div>
        </div>
        {fehler
          ? <Kasten art="rot" symbol="warnung">{fehler}</Kasten>
          : <Kasten art="warn" symbol="warnung"><strong>{t.senden.warnung}</strong>{!bekannt && <> {t.senden.neueAdresse}</>}</Kasten>}
        <div className="fuell" />
        <div className="stapel">
          <Schieber key={versuch} text={arbeitet ? t.senden.sendet : t.senden.schieben} label={t.senden.schiebenLabel} onFertig={geschoben} gesperrt={arbeitet || !reicht} />
          <div className="unterzeile">{!reicht ? t.senden.zuViel : bioAktiv() ? t.senden.danach : t.senden.danachPin}</div>
        </div>
      </div>
    );
  }

  /* --------------------------------------------------------------- eingabe */
  const kontaktliste = Object.entries(kontakte).sort((a, b) => a[1].localeCompare(b[1], t.code));
  return (
    <div className="seite">
      <KopfZurueck titel={t.senden.titel} zurueck={zurueck} />

      <div className="feldgruppe">
        <label htmlFor="an">{t.senden.empfaenger}</label>
        <div className="feld-huelle vertieft">
          <input id="an" type="text" className="ziffern" value={ziel} placeholder={t.senden.platz} autoCapitalize="none" autoCorrect="off"
                 autoComplete="off" spellCheck={false} onChange={e => { const v = e.target.value; if (!(/\s/.test(v.trim()) && uebernehmen(v))) setZiel(v); }} />
          <button type="button" className="klein im-feld sinkt" aria-label={t.allg.einfuegen}
                  onClick={async () => { const x = await einfuegen(); if (!uebernehmen(x)) melde(t.senden.keinCode); }}><S n="einfuegen" g={19} /></button>
          <button type="button" className="klein im-feld sinkt" aria-label={t.allg.scannen} onClick={() => setScannt(true)}><S n="scan" g={19} /></button>
        </div>
        {adresse === '' ? (kontaktliste.length > 0 &&
            <div className="chips" style={{ overflowX: 'auto', margin: '0 -20px', padding: '0 20px' }}>
              {kontaktliste.map(([a, n]) =>
                <button key={a} type="button" className="chip sinkt" style={{ display: 'flex', alignItems: 'center', gap: 8, paddingLeft: 8, flexShrink: 0 }} onClick={() => setZiel(a)}>
                  <Bild name={n} g={28} /><span>{n}</span>
                </button>)}
            </div>)
          : eigene ? <div className="gutzeile rot"><S n="warnung" g={15} />{t.senden.eigene}</div>
          : zielGueltig ? <div className="gutzeile"><S n="haken" g={15} w={2.6} />{zielName ? t.senden.kontakt(zielName) : t.senden.gueltig}</div>
          : <div className="gutzeile rot"><S n="warnung" g={15} />{t.senden.ungueltig}</div>}
      </div>

      <div className="feldgruppe">
        <label htmlFor="betrag">{t.senden.betrag}</label>
        <div className="betragfeld">
          <div className="oben">
            <input id="betrag" type="text" inputMode="decimal" value={betrag} placeholder="0" autoComplete="off"
                   onChange={e => {
                     const v = e.target.value.replace(/[^\d.,]/g, '').replace(/[.,]/g, zeichen);
                     const teile = v.split(zeichen);
                     setAlles(false);
                     setBetrag(teile.length > 1 ? `${teile[0].slice(0, 12)}${zeichen}${teile.slice(1).join('').slice(0, DECIMALS)}` : v.slice(0, 12));
                   }} />
            <span>YSR</span>
          </div>
          <div className="trenn" style={{ margin: 0 }} />
          <div className="unten">
            <span>{t.senden.verfuegbar} <b>{betragText(guthaben, t.code)} YSR</b></span>
            <button type="button" className="alles sinkt" aria-pressed={alles} onClick={() => setAlles(true)}>{t.senden.alles}</button>
          </div>
        </div>
        {einheiten > 0n && (staub || !reicht) && <div className="gutzeile rot"><S n="warnung" g={15} />{staub ? t.senden.staub : t.senden.zuViel}</div>}
      </div>

      <div className="feldgruppe">
        <div className="feldkopf">
          <label htmlFor="notiz">{t.senden.notiz} <span style={{ fontWeight: 500, color: 'var(--leise)' }}>{t.allg.optional}</span></label>
          <small>{notizBytes(notiz)} / {NOTIZ_BYTES}</small>
        </div>
        <input id="notiz" type="text" className="feld vertieft" value={notiz} onChange={e => setNotiz(notizKuerzen(e.target.value.replace(/[\u0000-\u001f\u007f]/g, ' ')))} />
        {notiz && <div className="hinweis"><S n="info" g={16} />{t.senden.notizHinweis}</div>}
      </div>

      <div className="karte gebuehr">
        <div><b>{t.senden.netzgebuehr}</b><span className="ziffern">{genau(gebuehr)} YSR</span></div>
        <small>{t.senden.gebuehrText}</small>
      </div>

      <div className="fuell" />
      <button type="button" className="knopf sinkt" disabled={!bereit} onClick={() => { setFehler(''); setSchritt('pruefen'); }}>{t.allg.weiter}</button>

      {scannt && <Scanner onZu={() => setScannt(false)} onText={text => { if (!uebernehmen(text)) return false; setScannt(false); return true; }} />}
    </div>
  );
}
