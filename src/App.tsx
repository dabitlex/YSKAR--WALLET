import { useEffect, useRef, useState } from 'react';
import { useT } from './i18n/index.ts';
import { WalletProvider, DatenProvider, NavProvider, useWallet, useDaten, useNav, REITER, type Reiter } from './zustand.tsx';
import { kopfLaden } from './netz/api.ts';
import { zahlungAusCode } from './wallet/qr.ts';
import { appSchliessen, beiLink, linkAbholen, sichtschutzEinstellungAnwenden } from './geraet/geraet.ts';
import { useEinstellungen } from './wallet/einstellungen.ts';
import { eingangGeoeffnet } from './geraet/eingang.ts';
import { MeldungProvider, Nav, Zeichen, useZurueck, zurueckTasteVerbinden } from './ui/teile.tsx';
import { TastaturRahmen } from './ui/tastatur.tsx';
import { Einstieg } from './seiten/Einstieg.tsx';
import { Sperre } from './seiten/Sperre.tsx';
import { Start } from './seiten/Start.tsx';
import { Verlauf } from './seiten/Verlauf.tsx';
import { Detail } from './seiten/Detail.tsx';
import { Senden } from './seiten/Senden.tsx';
import { Empfangen } from './seiten/Empfangen.tsx';
import { Kontakte, Kontakt, KontaktBearbeiten } from './seiten/Kontakte.tsx';
import { Einstellungen, Sicherheit, PinAendern, WoerterZeigen } from './seiten/Einstellungen.tsx';
import { Signieren, SignaturPruefen } from './seiten/Signieren.tsx';
import { AnmeldenQr } from './seiten/AnmeldenQr.tsx';
import { Einfuehrung } from './seiten/Einfuehrung.tsx';
import { istAnmeldeCode } from './wallet/nachricht.ts';

/**
 * Der Ladebildschirm: Das Logo wird ins Grau gedrueckt, darunter laeuft der
 * Balken durch die Schritte, die wirklich passieren. Er wartet auf nichts
 * laenger als ein paar Sekunden -- ohne Netz geht es mit dem letzten Stand
 * weiter.
 */
function Lader({ onFertig }: { onFertig: () => void }) {
  const t = useT();
  const wallet = useWallet();
  const daten = useDaten();
  const [stufe, setStufe] = useState(0);            // 0 Tresor, 1 Netz, 2 Guthaben, 3 fertig
  const [sichtbar, setSichtbar] = useState(false);
  const [geht, setGeht] = useState(false);
  const beginn = useRef(Date.now());
  const stand = useRef({ phase: wallet.phase, konto: daten.konto, offline: daten.offline });
  stand.current = { phase: wallet.phase, konto: daten.konto, offline: daten.offline };

  useEffect(() => {
    let lebt = true;
    const warte = (ms: number) => new Promise(r => setTimeout(r, ms));
    const bis = async (f: () => boolean, hoechstens: number) => { const t0 = Date.now(); while (lebt && !f() && Date.now() - t0 < hoechstens) await warte(60); };
    (async () => {
      await warte(950);
      if (!lebt) return;
      setSichtbar(true);
      await bis(() => stand.current.phase !== 'laden', 4000);
      setStufe(1);
      await Promise.race([kopfLaden().catch(() => null), warte(2500)]);
      if (!lebt) return;
      setStufe(2);
      if (stand.current.phase === 'gesperrt') await bis(() => stand.current.konto !== null || stand.current.offline, 2500);
      setStufe(3);
      await warte(Math.max(450, 2000 - (Date.now() - beginn.current)));
      if (!lebt) return;
      setGeht(true);
      await warte(300);
      if (lebt) onFertig();
    })();
    return () => { lebt = false; };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const text = stufe === 0 ? t.lader.wallet : stufe === 1 ? t.lader.netz : t.lader.guthaben;
  const prozent = [30, 60, 85, 100][stufe];
  return (
    <div className={`lader-seite ${geht ? 'geht' : ''}`}>
      <div className="schein" aria-hidden="true" style={{ top: '50%', transform: 'translate(-50%,-50%)', opacity: .18 }} />
      {/* Die Marke: nur das grosse Zeichen, es blendet weich ein und waechst dabei leicht. */}
      <div className="marke" role="img" aria-label="YSKAR"><Zeichen g={240} /></div>
      <div className="motto" aria-hidden="true">proof, not promise</div>
      <div className={`lader ${sichtbar ? 'sichtbar' : ''}`}>
        <div className="lader-text" aria-live="polite">{text}</div>
        <div className="rille" role="progressbar" aria-label={t.lader.fortschritt} aria-valuemin={0} aria-valuemax={100} aria-valuenow={prozent}>
          <div style={{ width: sichtbar ? `${prozent}%` : 0 }} />
        </div>
      </div>
    </div>
  );
}

function Seiten() {
  const nav = useNav();
  const o = nav.ort;
  const schluessel = `${nav.tiefe}:${o.n}`;

  // Zurueck-Taste: eine Seite zurueck; ganz unten zuerst auf Start, dann aus der App.
  useZurueck(() => {
    if (nav.tiefe > 1) nav.zurueck();
    else if (nav.ort.n !== 'start') nav.reiter('start');
    else appSchliessen();
  }, true, 0);

  // Ueber eine Eingangs-Meldung geoeffnet: zum Verlauf, wo der Eingang oben steht.
  useEffect(() => {
    const pruefen = () => { eingangGeoeffnet().then(txid => { if (txid) nav.reiter('verlauf'); }); };
    pruefen();
    const sicht = () => { if (document.visibilityState === 'visible') pruefen(); };
    document.addEventListener('visibilitychange', sicht);
    return () => document.removeEventListener('visibilitychange', sicht);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // Ueber einen yskar:-Link geoeffnet: Anmeldung oder Senden mit den Angaben aus dem Link.
  useEffect(() => {
    const pruefen = () => {
      const url = linkAbholen();
      // Anmeldung auf einer YSKAR-Seite auf demselben Handy: yskar:login?msg=…&cb=…
      if (url && istAnmeldeCode(url)) { nav.gehe({ n: 'anmeldenQr', code: url }); return; }
      const z = url ? zahlungAusCode(url) : null;
      if (z) nav.gehe({ n: 'senden', an: z.adresse, betrag: z.betrag, notiz: z.notiz });
    };
    pruefen();
    return beiLink(pruefen);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  let seite;
  switch (o.n) {
    case 'start': seite = <Start />; break;
    case 'kontakte': seite = <Kontakte />; break;
    case 'verlauf': seite = <Verlauf />; break;
    case 'einstellungen': seite = <Einstellungen />; break;
    case 'senden': seite = <Senden an={o.an} betrag={o.betrag} notiz={o.notiz} scan={o.scan} />; break;
    case 'empfangen': seite = <Empfangen fuer={o.fuer} />; break;
    case 'detail': seite = <Detail zahlung={o.zahlung} />; break;
    case 'kontakt': seite = <Kontakt adresse={o.adresse} />; break;
    case 'kontaktBearbeiten': seite = <KontaktBearbeiten adresse={o.adresse} />; break;
    case 'sicherheit': seite = <Sicherheit />; break;
    case 'einfuehrung': seite = <Einfuehrung />; break;
    case 'pinAendern': seite = <PinAendern />; break;
    case 'woerterZeigen': seite = <WoerterZeigen />; break;
    case 'signieren': seite = <Signieren />; break;
    case 'signaturPruefen': seite = <SignaturPruefen />; break;
    case 'anmeldenQr': seite = <AnmeldenQr code={o.code} />; break;
  }
  return (
    <>
      <div key={schluessel} style={{ position: 'absolute', inset: 0 }}>{seite}</div>
      {REITER.includes(o.n as Reiter) && <Nav />}
    </>
  );
}

function Inhalt() {
  const wallet = useWallet();
  const [geladen, setGeladen] = useState(false);
  // Die Zurueck-Taste von Android: was obenauf liegt, sonst die App verlassen.
  useEffect(() => zurueckTasteVerbinden(appSchliessen), []);
  // Sichtschutz-Schalter: beim Start und bei jeder Aenderung anwenden.
  const { sichtschutz } = useEinstellungen();
  useEffect(() => { sichtschutzEinstellungAnwenden(); }, [sichtschutz]);
  return (
    <>
      {wallet.phase === 'kein_tresor' && <Einstieg />}
      {/* Erst nach dem Ladebildschirm: Sonst erscheint die Abfrage des Fingerabdrucks ueber dem Logo. */}
      {geladen && wallet.phase === 'gesperrt' && <Sperre />}
      {wallet.phase === 'offen' && <NavProvider><Seiten /></NavProvider>}
      {!geladen && <Lader onFertig={() => setGeladen(true)} />}
    </>
  );
}

export function App() {
  return (
    <TastaturRahmen>
      <WalletProvider>
        <DatenProvider>
          <MeldungProvider>
            <Inhalt />
            <div id="ebene" />
          </MeldungProvider>
        </DatenProvider>
      </WalletProvider>
    </TastaturRahmen>
  );
}
