import { useEffect, useMemo, useRef, useState, type ReactNode } from 'react';
import { useT, type Woerterbuch } from '../i18n/index.ts';
import { useDaten, useNav, useWallet } from '../zustand.tsx';
import { isValidAddress, decodeAddress } from '../kern/address.ts';
import { buildTransfer, serializeTx, txidHex, checkTransfer } from '../kern/tx.ts';
import { sendeStand, empfaengerBytes, empfaengerErlaubt, ueberweisungBytes, gebuehrFuer } from '../wallet/fassung.ts';
import { keypairFromMnemonic } from '../kern/wallet.ts';
import { toHex } from '../kern/codec.ts';
import { DUST_LIMIT, DECIMALS, V6_HEIGHT } from '../kern/params.ts';
import { marktLaden, senden, EXPLORER, type Markt, type Stufe } from '../netz/api.ts';
import { betragGenau, betragText, dezimalZeichen, eingabeEinheiten, einheitenEingabe } from '../wallet/betrag.ts';
import { NOTIZ_BYTES, notizBytes, notizKuerzen } from '../wallet/notiz.ts';
import { vierer, kurzAdresse } from '../wallet/adresse.ts';
import { tippeAdresse, tippeBetrag, tippeText } from '../wallet/tasten.ts';
import { zahlungAusText } from '../wallet/qr.ts';
import { useKontakte } from '../wallet/kontakte.ts';
import { bioAktiv, bioPin } from '../wallet/biometrie.ts';
import { gesendetMerken } from '../wallet/gesendet.ts';
import { netzbild, imNetzGesehen, schritte, yStand, type Teil } from '../wallet/sendestatus.ts';
import { einfuegen, erfolgGefuehl, fehlerGefuehl, imBrowserOeffnen } from '../geraet/geraet.ts';
import { fehlerMerken } from '../geraet/plattform.ts';
import { S, Vierer, Kasten, Schieber, PinFeld, Bild, Schritte, useMeldung, useZurueck, useZeit, type PinAntwort } from '../ui/teile.tsx';
import { YMarke, YStatus, ZahlZeichen } from '../ui/zeichen.tsx';
import { Scanner } from '../ui/scanner.tsx';
import { istAnmeldeCode } from '../wallet/nachricht.ts';
import { Tippfeld, Ziffernfeld, useTastatur } from '../ui/tastatur.tsx';

/*
  Senden (Designsystem 4.2): An wen? -> Betrag -> Pruefen -> PIN -> Uebermittelt
  oder Nicht gesendet.

  Die Ueberweisung wird VOLLSTAENDIG auf dem Geraet gebaut und signiert. Was
  im Pruefschritt steht, wird signiert -- nichts anderes: Die Gebuehr wird
  beim Wechsel in den Pruefschritt festgehalten und aendert sich dort nicht
  mehr, auch wenn der Knoten inzwischen eine andere Lage meldet.

  Nach dem Einreichen zeigt die Seite fuenf Schritte (wallet/sendestatus.ts).
  Jeder gilt erst, wenn eine Antwort ihn belegt; "angenommen" heisst nicht
  "bestaetigt".
*/

type Schritt = 'an' | 'betrag' | 'pruefen' | 'pin' | 'status' | 'fehler';
const STUFEN: Stufe[] = ['langsam', 'normal', 'schnell'];

/** Eine signierte Zahlung dieser Sitzung. Nur im Speicher. */
interface Sendung {
  txid: string;
  hex: string;
  an: string;
  betrag: bigint;
  gebuehr: bigint;
  notiz: string;
  stufe: Stufe;
  /** Unix-Sekunden. */
  signiertUm: number;
  nonce: bigint;
  /** Wie der Knoten sie angenommen hat; null = noch keine Antwort. */
  annahme: 'angenommen' | 'doppelt' | 'spaeter' | null;
}

/**
 * Warum nicht gesendet:
 *   vorher     -- vor dem Einreichen gescheitert; beim Knoten ist nichts angekommen
 *   abgelehnt  -- der Knoten hat geantwortet und abgelehnt
 *   unklar     -- keine (verstaendliche) Antwort: ob sie ankam, ist offen
 */
interface Fehlschlag { art: 'vorher' | 'abgelehnt' | 'unklar'; text: string; signiert: boolean }

function fehlerText(t: Woerterbuch, grund: string): string {
  const bekannt = t.fehler as unknown as Record<string, unknown>;
  const text = bekannt[grund];
  return typeof text === 'string' ? text : t.fehler.sonst(grund);
}

const jetztSek = () => Math.floor(Date.now() / 1000);

export function Senden({ an, betrag: vorBetrag, notiz: vorNotiz, scan }: { an?: string; betrag?: bigint | null; notiz?: string | null; scan?: boolean }) {
  const t = useT();
  const nav = useNav();
  const wallet = useWallet();
  const daten = useDaten();
  const melde = useMeldung();
  const tast = useTastatur();
  const zeit = useZeit();
  const { kontakte, name } = useKontakte();
  const zeichen = dezimalZeichen(t.code);
  const startMitAn = !!an && isValidAddress(an.trim().toLowerCase());

  const [schritt, setSchritt] = useState<Schritt>(startMitAn ? 'betrag' : 'an');
  const [ziel, setZiel] = useState(an ?? '');
  const [betrag, setBetrag] = useState(vorBetrag ? einheitenEingabe(vorBetrag, zeichen, DECIMALS, DECIMALS) : '');
  const [alles, setAlles] = useState(false);
  const [notiz, setNotiz] = useState(vorNotiz ?? '');
  const [notizOffen, setNotizOffen] = useState(false);
  const [scannt, setScannt] = useState(!!scan);
  const [markt, setMarkt] = useState<Markt | null>(null);
  const [marktUm, setMarktUm] = useState(0);
  const [stufe, setStufe] = useState<Stufe>('normal');
  const [festeGebuehr, setFesteGebuehr] = useState<bigint | null>(null);
  const [arbeitet, setArbeitet] = useState(false);
  const [versuch, setVersuch] = useState(0);
  const [sendung, setSendung] = useState<Sendung | null>(null);
  const [fehlschlag, setFehlschlag] = useState<Fehlschlag | null>(null);
  const [gesehenUm, setGesehenUm] = useState<number | null>(null);
  const [, setTakt] = useState(0);

  // Gebuehren, Fassung und Sendepause des naechsten Blocks: einmal je Minute,
  // nach einem Fehler alle 15 s, damit eine alte Auskunft die Sendepause nicht verpasst.
  useEffect(() => {
    let lebt = true;
    let wieder: ReturnType<typeof setTimeout> | undefined;
    const holen = () => {
      marktLaden().then(m => { if (lebt) { setMarkt(m); setMarktUm(Date.now()); } })
        .catch(e => { fehlerMerken('gebuehren', e); if (lebt) { setMarkt(null); wieder = setTimeout(holen, 15_000); } });
    };
    holen();
    const uhr = setInterval(holen, 60_000);
    return () => { lebt = false; clearInterval(uhr); if (wieder) clearTimeout(wieder); };
  }, []);
  // "vor n Min." bleibt aktuell.
  useEffect(() => { const u = setInterval(() => setTakt(x => x + 1), 20_000); return () => clearInterval(u); }, []);

  const adresse = ziel.trim().toLowerCase();
  const zielGueltig = isValidAddress(adresse);
  const eigene = zielGueltig && adresse === wallet.adresse;
  const zielName = zielGueltig ? name(adresse) : null;
  const wer = zielName ?? kurzAdresse(adresse, '?');

  // Fassung 6: welche Ueberweisung der naechste Block verlangt (wallet/fassung.ts).
  const stand = sendeStand(markt, daten.spitze);
  const zielBytes = zielGueltig ? empfaengerBytes(adresse) : null;
  const zuFrueh = zielBytes !== null && !empfaengerErlaubt(zielBytes, stand.fassung);
  const zielErlaubt = zielGueltig && !eigene && !zuFrueh;
  const bytes = ueberweisungBytes(stand, markt, notizBytes(notiz), zielBytes ?? undefined);
  const gebuehrJe = useMemo(() => Object.fromEntries(STUFEN.map(s => [s, gebuehrFuer(markt, s, bytes)])) as Record<Stufe, bigint>,
    [markt, bytes]);
  const gebuehr = gebuehrJe[stufe];
  // Im Pruefschritt gilt die festgehaltene Gebuehr.
  const gebuehrJetzt = schritt === 'betrag' || festeGebuehr === null ? gebuehr : festeGebuehr;

  const guthaben = daten.verfuegbar;
  const einheiten = useMemo(() => eingabeEinheiten(betrag, DECIMALS), [betrag]);

  // "Alles" bleibt alles, auch wenn Notiz oder Stufe die Gebuehr veraendern -- aber nur bei der Eingabe.
  useEffect(() => {
    if (!alles || schritt !== 'betrag') return;
    const frei = guthaben > gebuehr ? guthaben - gebuehr : 0n;
    setBetrag(einheitenEingabe(frei, zeichen, DECIMALS, DECIMALS));
  }, [alles, schritt, guthaben, gebuehr, zeichen]);

  const staub = !!markt?.mindestJeByte && einheiten > 0n && einheiten < DUST_LIMIT;
  const summe = einheiten + gebuehrJetzt;
  const reicht = einheiten > 0n && summe <= guthaben;
  const gesperrt = stand.pause || stand.unbekannt;
  const bereit = zielErlaubt && !gesperrt && reicht && !staub && daten.konto !== null;
  const genau = (v: bigint) => betragGenau(v, t.code, DECIMALS, 2);
  // Ein frueherer Versuch ohne Antwort: Dieselben Bytes werden noch einmal uebermittelt.
  const nochmal = sendung !== null && fehlschlag?.art === 'unklar';

  // An diese Adresse schon einmal gesendet? Nur ein Hinweis, kein Urteil.
  const bekannt = !!zielName || (daten.konto?.history ?? []).some(e => e.kind === 'out' && e.counterparty === adresse)
    || daten.wartende.some(p => p.kind === 'out' && p.to === adresse);

  /* ------------------------------------------------- Stand im Netz verfolgen */
  const bild = sendung ? netzbild(daten.konto, daten.spitze, sendung.txid) : null;

  // In der Liste des Knotens gesehen: Uhrzeit merken, nur im Speicher.
  useEffect(() => {
    if (bild?.imMempool) setGesehenUm(jetztSek());
  }, [daten.konto, bild?.imMempool]);

  // Ohne Antwort eingereicht, aber inzwischen beim Knoten oder in der Kette: Sie ist angekommen.
  useEffect(() => {
    if (schritt !== 'fehler' || !nochmal || !sendung || !bild || !imNetzGesehen(bild)) return;
    const s: Sendung = { ...sendung, annahme: 'spaeter' };
    merken(s);
    setSendung(s); setFehlschlag(null); setSchritt('status');
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [schritt, nochmal, bild?.imMempool, bild?.hoehe]);

  // "Im Block": dezentes Erfolgssignal, einmal, und nur wenn es waehrend des Zusehens passiert.
  const warImBlock = useRef<boolean | null>(null);
  useEffect(() => {
    if (schritt !== 'status' || !bild) { warImBlock.current = null; return; }
    const jetzt = bild.hoehe !== null;
    if (warImBlock.current === false && jetzt) erfolgGefuehl();
    warImBlock.current = jetzt;
  }, [schritt, bild?.hoehe]);

  /* ------------------------------------------------------------ Uebergaenge */
  const zumBetrag = (a: string): boolean => {
    const x = a.trim().toLowerCase();
    if (!isValidAddress(x) || x === wallet.adresse) return false;
    const b = empfaengerBytes(x);
    if (b === null || !empfaengerErlaubt(b, stand.fassung)) return false;
    tast.oeffne(null);
    setSchritt('betrag');
    return true;
  };

  const uebernehmen = (text: string): boolean => {
    // Ein Anmelde-Code einer YSKAR-Seite: weiter zur Anmeldung, nicht zum Senden.
    if (istAnmeldeCode(text)) { nav.ersetze({ n: 'anmeldenQr', code: text.trim() }); return true; }
    const z = zahlungAusText(text, DECIMALS);
    if (!z) return false;
    setZiel(z.adresse);
    if (z.betrag) { setAlles(false); setBetrag(einheitenEingabe(z.betrag, zeichen, DECIMALS, DECIMALS)); }
    if (z.notiz) setNotiz(z.notiz);
    zumBetrag(z.adresse);
    return true;
  };

  const zumPruefen = () => {
    tast.oeffne(null);
    setFesteGebuehr(gebuehr);
    setSendung(null); setFehlschlag(null);
    setSchritt('pruefen');
  };

  const zurueck = () => {
    if (arbeitet) return;
    if (schritt === 'an') nav.zurueck();
    else if (schritt === 'betrag') { if (startMitAn) nav.zurueck(); else setSchritt('an'); }
    else if (schritt === 'pruefen') { setSendung(null); setFehlschlag(null); setSchritt('betrag'); }
    else if (schritt === 'pin') setSchritt('pruefen');
    else if (schritt === 'status') nav.reiter('start');
    else nochmalVersuchen();
  };
  useZurueck(zurueck, schritt !== 'an' && !(schritt === 'betrag' && startMitAn));

  const merken = (s: Sendung) => gesendetMerken({
    txid: s.txid, an: s.an, betrag: s.betrag.toString(), gebuehr: s.gebuehr.toString(),
    nonce: s.nonce.toString(), notiz: s.notiz, zeit: s.signiertUm });

  /** Fertig signierte Bytes an den Knoten. */
  const uebermitteln = async (s: Sendung) => {
    setSendung(s);
    try {
      const antwort = await senden(s.hex);
      // "duplicate": genau diese Ueberweisung liegt schon beim Knoten -- ein frueherer Versuch kam an.
      if (antwort.ok || antwort.grund === 'duplicate') {
        const fertig: Sendung = { ...s, annahme: antwort.ok ? 'angenommen' : 'doppelt' };
        merken(fertig);
        setSendung(fertig); setFehlschlag(null);
        setSchritt('status');
        daten.neuLaden();
        return;
      }
      fehlerGefuehl();
      setSendung(null);
      setFehlschlag({ art: 'abgelehnt', text: fehlerText(t, antwort.grund), signiert: true });
      setSchritt('fehler');
    } catch (e) {
      // Ohne Antwort weiss niemand, ob die Bytes angekommen sind. Die Sendung bleibt,
      // damit ein neuer Versuch genau dieselbe Zahlung uebermittelt -- nie eine zweite.
      fehlerMerken('senden', e);
      fehlerGefuehl();
      setFehlschlag({ art: 'unklar', text: t.senden.keineAntwortText, signiert: true });
      setSchritt('fehler');
      daten.neuLaden();
    }
  };

  /** PIN pruefen, auf dem Geraet signieren, uebermitteln. */
  const sendeMit = async (pin: string): Promise<PinAntwort> => {
    const r = await wallet.pinPruefen(pin);
    if (!r.ok) return r.grund;
    if (festeGebuehr === null) return 'ok';
    setArbeitet(true);
    let signiert = false;
    try {
      const kp = keypairFromMnemonic(r.woerter);
      if (kp.address !== wallet.adresse) throw new Error('Tresor passt nicht zur Adresse');
      const nonce = daten.naechsteNonce;
      const tx = buildTransfer({
        from: kp.addressRaw, to: decodeAddress(adresse), amount: einheiten, fee: festeGebuehr, nonce,
        memo: notiz ? new TextEncoder().encode(notizKuerzen(notiz)) : undefined,
        publicKey: kp.publicKey, privateKey: kp.privateKey,
        version: stand.fassung,
      });
      signiert = true;
      // Dieselbe Pruefung, die der Knoten macht -- vor dem Senden, auf dem Geraet.
      // Ohne bekannte Hoehe waere die Gebuehrenregel geraten -- dann prueft nur der Knoten.
      const mangel = daten.spitze !== null ? checkTransfer(tx, daten.spitze + 1) : null;
      if (mangel) throw new Error(mangel);
      await uebermitteln({
        txid: txidHex(tx), hex: toHex(serializeTx(tx)), an: adresse, betrag: einheiten, gebuehr: festeGebuehr,
        notiz: notizKuerzen(notiz), stufe, signiertUm: jetztSek(), nonce, annahme: null,
      });
    } catch (e) {
      fehlerMerken('senden', e);
      fehlerGefuehl();
      setSendung(null);
      setFehlschlag({ art: 'vorher', text: fehlerText(t, String((e as Error)?.message ?? e)), signiert });
      setSchritt('fehler');
    } finally { setArbeitet(false); }
    return 'ok';
  };

  const geschoben = async () => {
    if (nochmal && sendung) {
      setArbeitet(true);
      try { await uebermitteln(sendung); } finally { setArbeitet(false); }
      return;
    }
    if (bioAktiv()) {
      const pin = await bioPin(t.senden.bioGrund(genau(einheiten)), t.allg.abbrechen);
      if (pin) { const r = await sendeMit(pin); if (r === 'ok') return; }
    }
    setSchritt('pin');
  };

  function nochmalVersuchen() {
    // Ohne Antwort: dieselbe Sendung noch einmal. Abgelehnt: neu bauen, mit der Gebuehr von jetzt.
    if (!nochmal) { setSendung(null); setFehlschlag(null); setFesteGebuehr(gebuehr); }
    setVersuch(v => v + 1);
    setSchritt('pruefen');
  }

  /* ---------------------------------------------------------------- Teile */
  const kopf = (mitte: ReactNode, n?: 1 | 2 | 3, zurueckLabel?: string) => (
    <div className="sf-kopf">
      <button type="button" className="klein sinkt" aria-label={zurueckLabel ?? t.allg.zurueck} onClick={zurueck}><S n="zurueck" w={2.2} /></button>
      <div className="sf-mitte">{mitte}</div>
      {n ? <Schritte n={n} label={t.senden.schritt(n)} /> : <span className="sf-platz" />}
    </div>
  );
  const empfaengerChip = (
    <span className="sf-chip">
      {zielName ? <Bild name={zielName} g={26} /> : <span className="sf-chip-zeichen"><ZahlZeichen art="aus" g={14} /></span>}
      <span className="sf-chip-name">{t.senden.an(zielName ?? kurzAdresse(adresse, '?'))}</span>
      {zielName && <span className="mono leise">…{adresse.slice(-4)}</span>}
    </span>
  );
  const zahl = (wert: bigint, klasse: string) => {
    const text = genau(wert);
    const i = text.lastIndexOf(zeichen);
    return <span className={`sf-zahl ${klasse}`}>{i < 0 ? text : <>{text.slice(0, i)}<span className="nk">{text.slice(i)}</span></>}</span>;
  };
  const einheit = <span className="sf-einheit"><YMarke g={13} farbe="var(--leise)" />YSR</span>;
  const zeile = (titel: string, text: string, teil: Teil) => (
    <div className={`sf-schritt ${teil}`}>
      <i aria-hidden="true" />
      <span><b>{titel}</b>{text && <span>{text}</span>}</span>
    </div>
  );

  /* ------------------------------------------------------ Uebermittelt */
  if (schritt === 'status' && sendung && bild) {
    const st = schritte(bild, gesehenUm !== null);
    const wem = name(sendung.an) ?? kurzAdresse(sendung.an, '?');
    const lage = bild.hoehe === null ? t.senden.nochOffen
      : bild.bestaetigungen >= 2 ? t.senden.bestaetigungen(bild.bestaetigungen) : t.senden.imBlock(zeit.zahl(bild.hoehe));
    const s2 = sendung.annahme === 'doppelt' ? t.senden.s2Doppelt : sendung.annahme === 'spaeter' ? t.senden.s2Spaeter : t.senden.s2Text;
    const s3 = bild.hoehe !== null ? (gesehenUm ? t.senden.s3Gesehen(zeit.uhr(gesehenUm)) : t.senden.s3Vorbei)
      : bild.imMempool === true ? t.senden.s3Gesehen(zeit.uhr(gesehenUm ?? jetztSek()))
      : bild.imMempool === null ? (gesehenUm ? t.senden.s3Gesehen(zeit.uhr(gesehenUm)) : t.senden.s3Unbekannt)
      : gesehenUm ? t.senden.s3Weg(zeit.uhr(gesehenUm)) : t.senden.s3Noch;
    const s4 = bild.hoehe !== null ? t.senden.s4Da(zeit.zahl(bild.hoehe)) : t.senden.s4Wartet;
    const s5 = bild.bestaetigungen >= 2 ? t.senden.s5Da(zeit.zahl(bild.bestaetigungen)) : t.senden.s5Text;
    return (
      <div key="status" className="seite sf">
        {kopf(<span className="sf-titel">{t.senden.titelStatus}</span>)}
        <div className="sf-statuskopf">
          <YStatus g={76} stand={yStand(st)} label={`${t.senden.titelStatus}: ${lage}`} />
          <span>
            {zahl(sendung.betrag, 'mittel')}
            <span className="sf-klein">{t.senden.unterzeile(wem, lage)}</span>
          </span>
        </div>
        <div className="karte sf-schritte" role="list">
          {zeile(t.senden.s1, t.senden.s1Text(zeit.uhr(sendung.signiertUm)), st.signiert)}
          {zeile(t.senden.s2, s2, st.angenommen)}
          {zeile(t.senden.s3, s3, st.mempool)}
          {zeile(t.senden.s4, s4, st.block)}
          {zeile(t.senden.s5, s5, st.weitere)}
        </div>
        <p className="sf-fuss">{t.senden.endgueltig}</p>
        <div className="fuell" />
        <div className="paar-knoepfe">
          <button type="button" className="knopf zweit sinkt" onClick={() => imBrowserOeffnen(`${EXPLORER}#tx-${sendung.txid}`)}>
            {t.senden.explorer}<S n="extern" g={17} />
          </button>
          <button type="button" className="knopf sinkt sf-fertig" onClick={() => nav.reiter('start')}>{t.allg.fertig}</button>
        </div>
      </div>
    );
  }

  /* ------------------------------------------------------ Nicht gesendet */
  if (schritt === 'fehler' && fehlschlag) {
    const unklar = fehlschlag.art === 'unklar';
    return (
      <div key="fehler" className="seite sf">
        {kopf(<span className="sf-titel">{t.senden.titel}</span>)}
        <div className="sf-fehlerkopf" role="alert">
          <span className={`sf-warnkachel ${unklar ? 'bern' : ''}`}><S n="warnung" g={28} w={1.8} /></span>
          <h1>{unklar ? t.senden.keineAntwort : t.senden.nichtGesendet}</h1>
          <p>{fehlschlag.text}</p>
        </div>
        <div className="karte sf-schritte" role="list">
          {fehlschlag.signiert && zeile(t.senden.s1, sendung ? t.senden.s1Text(zeit.uhr(sendung.signiertUm)) : '', 'erledigt')}
          {unklar
            ? zeile(t.senden.s2, t.senden.offen, 'wartet')
            : zeile(fehlschlag.art === 'abgelehnt' ? t.senden.abgelehnt : t.senden.nichtUebermittelt, t.senden.unveraendert, 'fehler')}
        </div>
        <div className="fuell" />
        <div className="stapel">
          <button type="button" className="knopf sinkt" onClick={nochmalVersuchen}>{t.senden.nochmal}</button>
          <button type="button" className="knopf zweit sinkt" onClick={() => nav.reiter('start')}>{t.allg.abbrechen}</button>
        </div>
      </div>
    );
  }

  /* ------------------------------------------------------------------ PIN */
  if (schritt === 'pin') {
    const bio = bioAktiv() ? async () => {
      const pin = await bioPin(t.senden.bioGrund(genau(einheiten)), t.allg.abbrechen);
      if (pin) await sendeMit(pin);
    } : undefined;
    return (
      <PinFeld kopf={t.senden.bestaetigen} titel={t.pin.eingeben} unter={arbeitet ? t.senden.arbeitet : t.senden.pinText}
               oben={<span className="sf-chip">{zielName ? <Bild name={zielName} g={26} /> : <span className="sf-chip-zeichen"><ZahlZeichen art="aus" g={14} /></span>}
                       <span className="sf-chip-name">{t.senden.chip(genau(einheiten), wer)}</span></span>}
               zurueck={zurueck} onPin={sendeMit} bio={bio} />
    );
  }

  /* -------------------------------------------------------------- Pruefen */
  if (schritt === 'pruefen') {
    const sperrt = arbeitet || gesperrt || (!nochmal && !reicht);
    return (
      <div key="pruefen" className="seite sf">
        {kopf(<span className="sf-titel">{t.senden.pruefen}</span>, 3)}
        <div className="sf-empfaenger">
          {zielName ? <Bild name={zielName} g={52} /> : <span className="kachel art aus sf-kachel"><ZahlZeichen art="aus" g={24} /></span>}
          <span className="sf-wer">{t.senden.an(zielName ?? kurzAdresse(adresse, '?'))}</span>
          {zahl(einheiten, 'pruefen')}
          {einheit}
        </div>
        <div className="karte sf-linien">
          <div className="sf-linie oben"><span>{t.senden.anKurz}</span><Vierer adresse={adresse} klasse="sf-adresse" /></div>
          <div className="sf-linie"><span>{t.senden.gebuehrStufe(t.senden.stufe[sendung?.stufe ?? stufe])}</span><span className="ziffern">{genau(gebuehrJetzt)}</span></div>
          {notiz && <div className="sf-linie"><span>{t.senden.notiz}</span><span className="sf-notiz">{notizKuerzen(notiz)}</span></div>}
          <div className="sf-linie summe"><span>{t.senden.gesamt}</span><span className="ziffern">{genau(summe)} YSR</span></div>
        </div>
        {!bekannt && !nochmal && <div className="hinweis bern"><S n="info" g={18} /><span>{t.senden.neueAdresse}</span></div>}
        {gesperrt && <Kasten art="ruhig" symbol="info">{stand.pause ? sendepauseText() : t.senden.fassungUnbekannt}</Kasten>}
        <p className="sf-hinweis">{nochmal ? t.senden.nochmalText : bioAktiv() ? t.senden.danachBio : t.senden.danachPin}</p>
        <div className="fuell" />
        <Schieber key={versuch} text={arbeitet ? (nochmal ? t.senden.uebermittelt : t.senden.arbeitet) : t.senden.schieben}
                  label={t.senden.schiebenLabel} onFertig={geschoben} gesperrt={sperrt} />
      </div>
    );
  }

  function sendepauseText(): ReactNode {
    const h = markt?.hoehe ? t.senden.hoeheKnoten(zeit.zahl(markt.hoehe - 1)) : daten.spitze !== null ? t.senden.hoehe(zeit.zahl(daten.spitze)) : null;
    return <>{t.senden.sendepause(zeit.zahl(V6_HEIGHT - 1))}{h && <span className="sf-klein ziffern">{h}</span>}</>;
  }

  /* --------------------------------------------------------------- Betrag */
  if (schritt === 'betrag') {
    const i = betrag.indexOf(zeichen);
    const groesse = betrag.length > 13 ? 'l3' : betrag.length > 10 ? 'l2' : betrag.length > 7 ? 'l1' : '';
    const minuten = marktUm ? Math.floor((Date.now() - marktUm) / 60_000) : 0;
    const meldung = !zielErlaubt ? (zuFrueh ? t.senden.adresseAbV6 : null)
      : einheiten > 0n && staub ? t.senden.staub
      : einheiten > 0n && !reicht && daten.konto !== null ? t.senden.zuViel
      : null;
    return (
      <div key="betrag" className="seite sf">
        {kopf(empfaengerChip, 2)}
        <div className="sf-betrag">
          <span className="sf-lab" id="sf-betrag-lab">{t.senden.betrag}</span>
          <span className={`sf-eingabe ${groesse}`} role="textbox" aria-readonly="true" aria-labelledby="sf-betrag-lab" aria-live="polite">
            {betrag === '' ? <span className="leer">0</span> : i < 0 ? betrag : <>{betrag.slice(0, i)}<span>{betrag.slice(i)}</span></>}
            {!notizOffen && <i className="strich" aria-hidden="true" />}
          </span>
          {einheit}
          <span className="sf-verfuegbar">
            {t.senden.verfuegbar} <b className="ziffern">{betragText(guthaben, t.code)}</b> · <button type="button" className="sf-link" aria-pressed={alles} onClick={() => setAlles(true)}>{t.senden.alles}</button>
          </span>
          {meldung && <span className="gutzeile rot sf-meldung"><S n="warnung" g={15} />{meldung}</span>}
        </div>

        {gesperrt
          ? <div className="sf-gebuehr"><Kasten art="ruhig" symbol="info">{stand.pause ? sendepauseText() : t.senden.fassungUnbekannt}</Kasten></div>
          : <div className="sf-gebuehr">
              <div className="sf-gebuehrkopf"><span className="sf-lab" id="sf-gebuehr-lab">{t.senden.netzgebuehr}</span>
                {markt && <span className="sf-xs">{t.senden.gebuehrStand(minuten)}</span>}</div>
              {markt
                ? <div className="sf-stufen" role="radiogroup" aria-labelledby="sf-gebuehr-lab">
                    {STUFEN.map(s => {
                      const an = s === stufe;
                      const block = markt.stufen[s]?.block ?? 1;
                      return (
                        <button key={s} type="button" role="radio" aria-checked={an} className="sinkt" onClick={() => setStufe(s)}>
                          {t.senden.stufe[s]}
                          <small>{an ? genau(gebuehrJe[s]) : block <= 1 ? t.senden.naechsterBlock : t.senden.inBloecken(block)}</small>
                        </button>
                      );
                    })}
                  </div>
                : <div className="sf-ohne"><b className="ziffern">{genau(gebuehr)} YSR</b><span>{t.senden.gebuehrOhne}</span></div>}
            </div>}

        {notizOffen || notiz
          ? <div className="sf-notizfeld">
              <div className="sf-gebuehrkopf"><span className="sf-lab">{t.senden.notiz} <span className="leise">{t.allg.optional}</span></span>
                <span className="sf-xs">{notizBytes(notiz)} / {NOTIZ_BYTES}</span></div>
              <Tippfeld id="notiz" art="text" klasse="feld" gross="anfang" label={t.senden.notiz} wert={notiz} auto={notizOffen && !notiz}
                        onTaste={z => setNotiz(n => notizKuerzen(tippeText(n, z)))}
                        einfuegen={x => setNotiz(n => notizKuerzen((n + x).replace(/[\u0000-\u001f\u007f]/g, ' ').trimStart()))} />
              <span className="sf-xs">{t.senden.notizHinweis}</span>
            </div>
          : <button type="button" className="sf-link sf-notizlink" onClick={() => setNotizOffen(true)}><S n="stift" g={14} />{t.senden.notizHinzu}</button>}

        <div className="fuell" />
        {daten.konto === null && <p className="sf-xs mittig">{t.senden.kontoLaedt}</p>}
        <Ziffernfeld label={t.senden.betragLabel} onTaste={z => { setAlles(false); setBetrag(b => tippeBetrag(b, z, zeichen, DECIMALS)); }} />
        <button type="button" className="knopf sinkt sf-weiter" disabled={!bereit} onClick={zumPruefen}>{t.allg.weiter}</button>
      </div>
    );
  }

  /* -------------------------------------------------------------- An wen? */
  const geschichte = new Map<string, { zeit: number; art: 'in' | 'out' }>();
  for (const e of daten.konto?.history ?? []) {
    if (!e.counterparty || (e.kind !== 'in' && e.kind !== 'out')) continue;
    const z = e.timestamp && /^\d+$/.test(e.timestamp) ? Number(e.timestamp) : 0;
    const alt = geschichte.get(e.counterparty);
    if (!alt || z > alt.zeit) geschichte.set(e.counterparty, { zeit: z, art: e.kind });
  }
  const suche = adresse;
  const liste = Object.entries(kontakte)
    .filter(([a, n]) => !suche || zielGueltig || a.startsWith(suche) || n.toLowerCase().includes(suche))
    .sort((x, y) => (geschichte.get(y[0])?.zeit ?? 0) - (geschichte.get(x[0])?.zeit ?? 0) || x[1].localeCompare(y[1], t.code));
  const meldungAn = adresse === '' ? null
    : eigene ? <div className="gutzeile rot"><S n="warnung" g={15} />{t.senden.eigene}</div>
    : zuFrueh ? <div className="gutzeile rot"><S n="warnung" g={15} />{t.senden.adresseAbV6}</div>
    : zielGueltig ? <div className="gutzeile"><S n="haken" g={15} w={2.6} />{zielName ? t.senden.an(zielName) : t.senden.gueltig}</div>
    // Solange noch getippt wird, ist eine halbe Adresse kein Fehler.
    : tast.aktiv === 'an' && (adresse.length < 42 || (adresse.startsWith('ysr1p') && adresse.length < 63)) ? <div className="gutzeile leise">{t.senden.zeichen(adresse.length)}</div>
    : <div className="gutzeile rot"><S n="warnung" g={15} />{t.senden.ungueltig}</div>;
  return (
    <div key="an" className="seite sf">
      {kopf(<span className="sf-titel">{t.senden.titel}</span>, 1)}
      <h1 className="sf-t1">{t.senden.anWen}</h1>
      <div className={`sf-adressfeld ${tast.aktiv === 'an' ? 'aktiv' : ''}`}>
        <Tippfeld id="an" art="adresse" klasse="sf-adresstext" label={t.senden.adresse} wert={ziel} platz={t.senden.platz}
                  zeige={vierer(ziel).map((g, k) => <span key={k}>{g}</span>)}
                  onTaste={z => setZiel(a => tippeAdresse(a, z))}
                  leiste={ziel !== '' && <button type="button" className="tknopf" onClick={() => setZiel('')}>{t.tast.leeren}</button>}
                  weiter={zielErlaubt ? () => zumBetrag(adresse) : undefined} />
      </div>
      {meldungAn && <div className="sf-meldungan">{meldungAn}</div>}
      <div className="paar-knoepfe sf-wege">
        <button type="button" className="knopf zweit sinkt" onClick={async () => { const x = await einfuegen(); if (!uebernehmen(x)) melde(t.senden.keinCode); }}>
          <S n="einfuegen" g={18} />{t.senden.einfuegen}
        </button>
        <button type="button" className="knopf zweit sinkt" onClick={() => setScannt(true)}><S n="scan" g={18} />{t.senden.scannen}</button>
      </div>
      {zielErlaubt && tast.aktiv !== 'an' && (
        <button type="button" className="knopf sinkt sf-weiter-an" onClick={() => zumBetrag(adresse)}>{t.allg.weiter}</button>
      )}
      {Object.keys(kontakte).length > 0 && !zielGueltig && (
        <>
          <div className="sf-abschnitt"><span className="sf-lab">{t.senden.kontakte}</span></div>
          {liste.length === 0
            ? <p className="sf-xs">{t.senden.keinKontakt}</p>
            : <div className="sf-kontakte">
                {liste.map(([a, n]) => {
                  const g = geschichte.get(a);
                  const ok = (() => { const b = empfaengerBytes(a); return b !== null && empfaengerErlaubt(b, stand.fassung) && a !== wallet.adresse; })();
                  return (
                    <button key={a} type="button" className="zeile" onClick={() => { setZiel(a); if (!ok) return; zumBetrag(a); }}>
                      <Bild name={n} g={40} />
                      <span className="mitte">
                        <b>{n}</b>
                        <small>{g && g.zeit ? (g.art === 'out' ? t.senden.zuletztGesendet(zeit.kurz(g.zeit)) : t.senden.zuletztEmpfangen(zeit.kurz(g.zeit)))
                          : <span className="mono">{kurzAdresse(a, '')}</span>}</small>
                      </span>
                      <S n="weiter" g={18} klasse="pfeil" />
                    </button>
                  );
                })}
              </div>}
        </>
      )}
      {scannt && <Scanner onZu={() => setScannt(false)} onText={text => { if (!uebernehmen(text)) return false; setScannt(false); return true; }} />}
    </div>
  );
}
