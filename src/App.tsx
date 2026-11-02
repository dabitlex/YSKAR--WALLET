import { useEffect, useState } from 'react';
import { WalletProvider, DatenProvider, NavProvider, useWallet, useNav, REITER, type Reiter } from './zustand.tsx';
import { zahlungAusCode } from './wallet/qr.ts';
import { appSchliessen, beiLink, linkAbholen, sichtschutzEinstellungAnwenden } from './geraet/geraet.ts';
import { useEinstellungen } from './wallet/einstellungen.ts';
import { eingangGeoeffnet } from './geraet/eingang.ts';
import { MeldungProvider, Nav, useZurueck, zurueckTasteVerbinden } from './ui/teile.tsx';
import { Startbild } from './ui/startbild.tsx';
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
  // Start: erst der Startbildschirm, dann (bei Sperre) der Uebergang, in dem er das Zeichen an die Sperre uebergibt.
  const [start, setStart] = useState<'startbild' | 'uebergang' | 'fertig'>('startbild');
  // Die Zurueck-Taste von Android: was obenauf liegt, sonst die App verlassen.
  useEffect(() => zurueckTasteVerbinden(appSchliessen), []);
  // Sichtschutz-Schalter: beim Start und bei jeder Aenderung anwenden.
  const { sichtschutz } = useEinstellungen();
  useEffect(() => { sichtschutzEinstellungAnwenden(); }, [sichtschutz]);
  return (
    <>
      {wallet.phase === 'kein_tresor' && <Einstieg />}
      {/* Die Sperre haengt erst im Uebergang ein; den Fingerabdruck bietet sie erst an, wenn sie ganz zu sehen ist. */}
      {start !== 'startbild' && wallet.phase === 'gesperrt' &&
        <Sperre auftritt={start === 'uebergang' ? 'kommt' : undefined} eingeblendet={start === 'fertig'} />}
      {wallet.phase === 'offen' && <NavProvider><Seiten /></NavProvider>}
      {/* Wartet nur auf die lokale Lage der Wallet (Tresor gefunden oder nicht), nie auf Netz oder Kontostand. */}
      {start !== 'fertig' && <Startbild bereit={wallet.phase !== 'laden'} zurSperre={wallet.phase === 'gesperrt'}
        onUebergang={() => setStart('uebergang')} onFertig={() => setStart('fertig')} />}
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
