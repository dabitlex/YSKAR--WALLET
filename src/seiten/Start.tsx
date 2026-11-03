import { useEffect, useRef, useState, type ReactNode } from 'react';
import { useT } from '../i18n/index.ts';
import { useDaten, useNav, useWallet } from '../zustand.tsx';
import { einstellen, useEinstellungen } from '../wallet/einstellungen.ts';
import { nichtGelistet, type Zahlung } from '../wallet/zahlungen.ts';
import { sendeStand } from '../wallet/fassung.ts';
import { V6_HEIGHT } from '../kern/params.ts';
import { kopieren, teilen, tippGefuehl } from '../geraet/geraet.ts';
import { useZahlungen } from '../ui/daten.ts';
import { S, Betrag, Platzhalterzeilen, Zahlungszeile, useAnzeige, useMeldung, useWer, useZeit } from '../ui/teile.tsx';
import { YMarke, YBaender, ZahlZeichen, type Teilstand } from '../ui/zeichen.tsx';
import { EinfuehrungKarte } from './Einfuehrung.tsx';

/** "ysr1…3h8q": nur zum Wiedererkennen. Kopiert wird immer die volle Adresse. */
const kurz = (a: string | null) => (a ? `${a.slice(0, 4)}…${a.slice(-4)}` : '');

/** So lange gilt eine eigene Zahlung als "eben gesendet", danach wartet sie auf den Block. */
const GESENDET_SEK = 45;
/** So lange bleibt "bestaetigt" im Band stehen. */
const BESTAETIGT_MS = 6000;

interface Meldung {
  ton: '' | 'bern' | 'gruen' | 'rot';
  sym: ReactNode;
  titel: ReactNode;
  unter: ReactNode;
  onTipp?: () => void;
  aktion?: { text: string; onTipp: () => void };
}

/**
 * Wallet (Konzept "Ruhe", Variante C): oben leise die Adresse, rechts Scannen
 * und Sperren, das Guthaben mit dem Y, darunter Senden und Empfangen. Oberer
 * Teil und "Zuletzt" sind zwei Flaechen mit runden Ecken; dazwischen schaut
 * ein graues Band hervor. Es ist immer da und traegt hoechstens eine Meldung,
 * die wichtigste zuerst: keine Verbindung, Sendepause, nicht mehr beim Knoten,
 * bestaetigt, gesendet, wartet, neue Wallet. Ohne Meldung bleibt es leer.
 */
export function Start() {
  const t = useT();
  const nav = useNav();
  const wallet = useWallet();
  const { konto, offline, ausSpeicher, spitze } = useDaten();
  const zeit = useZeit();
  const { wartend, verlauf } = useZahlungen();
  const { verborgen, einfuehrungGesehen } = useEinstellungen();
  const melde = useMeldung();
  const anzeige = useAnzeige();
  const wer = useWer();
  const stand = konto ? BigInt(konto.balance) : null;
  // Hoechstens vier, damit die Wallet auf einen Bildschirm passt; der Rest steht in der Aktivitaet.
  const liste = [...wartend, ...verlauf].slice(0, 4);
  // Neue Wallet: nichts da, nichts unterwegs -- dann fuehrt Empfangen.
  const neu = konto !== null && stand === 0n && verlauf.length === 0 && wartend.length === 0;
  // Sendepause vor Fassung 6: aus der bekannten Blockhoehe, so wie die Senden-Seite rechnet.
  const pause = !offline && sendeStand(null, spitze).pause;
  const sendenAus = offline || pause || neu;

  // Eben bestaetigt: eine Zahlung, die gerade noch wartete und jetzt im Verlauf steht.
  const [bestaetigt, setBestaetigt] = useState<Zahlung | null>(null);
  const vorher = useRef<Set<string> | null>(null);
  useEffect(() => {
    const jetzt = new Set(wartend.map(z => z.txid));
    const alt = vorher.current;
    vorher.current = jetzt;
    if (!alt) return;
    const drin = verlauf.find(z => alt.has(z.txid) && !jetzt.has(z.txid) && z.hoehe !== null);
    if (drin) setBestaetigt(drin);
  }, [wartend, verlauf]);
  useEffect(() => {
    if (!bestaetigt) return;
    const u = setTimeout(() => setBestaetigt(null), BESTAETIGT_MS);
    return () => clearTimeout(u);
  }, [bestaetigt]);

  // "Gesendet" geht nach GESENDET_SEK in "wartet" ueber: dann einmal neu zeichnen.
  const jetztSek = Math.floor(Date.now() / 1000);
  const frisch = wartend.find(z => z.art === 'aus' && z.zeit !== null && jetztSek - z.zeit < GESENDET_SEK && z.mempool !== false);
  const [, setTakt] = useState(0);
  const frischBis = frisch?.zeit != null ? frisch.zeit + GESENDET_SEK : null;
  useEffect(() => {
    if (frischBis === null) return;
    const u = setTimeout(() => setTakt(x => x + 1), Math.max(0, frischBis * 1000 - Date.now()) + 50);
    return () => clearTimeout(u);
  }, [frischBis]);

  const zeile = (s: [Teilstand, Teilstand, Teilstand], text: string) =>
    <span className="st-band-zeile"><YBaender stand={s} label={text} /><span>{text}</span></span>;
  const zuDetail = (z: Zahlung) => () => nav.gehe({ n: 'detail', zahlung: z });
  const werText = (z: Zahlung) => (z.mehrere ? t.zahlung.mehrereKlein : wer(z.gegen));

  const meldung = ((): Meldung | null => {
    if (offline) {
      return { ton: 'bern', sym: <S n="warnung" g={20} w={1.75} />, titel: t.start.bandOffline,
        unter: !konto ? t.start.bandOhneStand : ausSpeicher !== null ? `${t.start.bandOfflineUnter} · ${zeit.uhr(ausSpeicher)}` : t.start.bandOfflineUnter };
    }
    if (pause) {
      return { ton: 'bern', sym: <S n="uhr" g={20} w={1.75} />, titel: t.start.bandPause, unter: t.start.bandPauseUnter(zeit.zahl(V6_HEIGHT)) };
    }
    const weg = wartend.find(z => z.art === 'aus' && nichtGelistet(z));
    if (weg) {
      return { ton: 'rot', sym: <S n="warnung" g={20} w={1.75} />, titel: t.zahlung.nichtGelistet,
        unter: `${anzeige(weg.betrag)} ${t.start.bandAn(werText(weg))}`, onTipp: zuDetail(weg),
        aktion: { text: t.start.bandDetails, onTipp: zuDetail(weg) } };
    }
    if (bestaetigt) {
      return { ton: 'gruen', sym: <S n="haken" g={20} w={2.2} />,
        titel: bestaetigt.art === 'ein' ? t.start.bandEinBestaetigt(werText(bestaetigt)) : t.start.bandAusBestaetigt(werText(bestaetigt)),
        unter: t.start.bandImBlock(zeit.zahl(bestaetigt.hoehe ?? 0)), onTipp: zuDetail(bestaetigt) };
    }
    if (frisch && wartend.length === 1) {
      return { ton: '', sym: <ZahlZeichen art="aus" g={20} />, titel: t.start.bandGesendet(anzeige(frisch.betrag), werText(frisch)),
        unter: zeile(['erledigt', 'offen', 'offen'], t.start.bandAngenommen), onTipp: zuDetail(frisch) };
    }
    if (wartend.length > 1) {
      return { ton: 'bern', sym: <S n="aktivitaet" g={20} w={1.75} />, titel: t.start.bandMehrere(wartend.length),
        unter: zeile(['erledigt', 'wartet', 'offen'], t.zahlung.wartetBlock), onTipp: () => nav.reiter('verlauf') };
    }
    const z = wartend[0];
    if (z) {
      const titel = z.betragOffen ? t.start.bandAnteil(werText(z))
        : z.art === 'ein' ? `+${anzeige(z.betrag)} ${t.start.bandVon(werText(z))}` : `${anzeige(z.betrag)} ${t.start.bandAn(werText(z))}`;
      return { ton: 'bern', sym: <ZahlZeichen art={z.art === 'ein' ? 'ein' : 'aus'} g={20} />, titel,
        unter: zeile(['erledigt', 'wartet', 'offen'], nichtGelistet(z) ? t.zahlung.nichtGelistet : t.zahlung.wartetBlock), onTipp: zuDetail(z) };
    }
    if (neu) {
      const adresseTeilen = async () => {
        if (!wallet.adresse) return;
        if (!(await teilen(wallet.adresse, t.start.adresse)) && await kopieren(wallet.adresse)) melde(t.start.adresseKopiert);
      };
      return { ton: '', sym: <ZahlZeichen art="ein" g={20} />, titel: t.start.bandNeu, unter: t.start.bandNeuUnter,
        onTipp: () => nav.gehe({ n: 'empfangen' }), aktion: { text: t.empfangen.teilen, onTipp: adresseTeilen } };
    }
    return null;
  })();

  const inhalt = meldung && <>
    <span className="st-band-sym">{meldung.sym}</span>
    <span className="st-band-text"><b>{meldung.titel}</b><small>{meldung.unter}</small></span>
  </>;

  return (
    <div className="seite mit-nav st st-flaechen">
      <div className="st-oben">
        <div className="st-kopf">
          <button type="button" className="st-adresse sinkt" aria-label={t.start.adresseKopieren}
                  onClick={async () => { if (wallet.adresse && await kopieren(wallet.adresse)) { tippGefuehl(); melde(t.start.adresseKopiert); } }}>
            <span className="mono">{kurz(wallet.adresse)}</span><S n="kopie" g={16} w={1.75} />
          </button>
          <span className="st-knoepfe">
            <button type="button" className="klein leer sinkt" aria-label={t.scan.titel}
                    onClick={() => nav.gehe({ n: 'senden', scan: true })}><S n="scan" g={22} w={1.75} /></button>
            <button type="button" className="klein leer sinkt" aria-label={t.start.sperren} onClick={wallet.sperren}><S n="schloss" g={21} w={1.75} /></button>
          </span>
        </div>

        <div className={`st-guthaben auf ${offline || ausSpeicher !== null ? 'alt' : ''}`}>
          {ausSpeicher !== null
            ? <span className="st-lab" role="status">{t.start.standVom(`${zeit.tag(ausSpeicher)}, ${zeit.uhr(ausSpeicher)}`)}</span>
            : <button type="button" className="st-lab sinkt" aria-pressed={verborgen} aria-label={verborgen ? t.start.zeigen : t.start.verbergen}
                      onClick={() => einstellen({ verborgen: !verborgen })}>
                {t.start.guthaben}<S n={verborgen ? 'augeZu' : 'auge'} g={16} w={1.75} />
              </button>}
          <div className="st-wert">
            <YMarke g={34} />
            {verborgen
              ? <div className="betrag start"><div className="gross" aria-label={t.start.zeigen}>••••<i>,••</i></div></div>
              : stand === null
                ? (offline ? <div className="st-laedt">–</div> : <span className="st-platzhalter" aria-hidden="true"><i /></span>)
                : <Betrag wert={stand} uebersicht klasse="start" />}
          </div>
          {stand === null && !offline && !verborgen && <span className="sf-klein st-laedt-text" role="status">{t.start.laedt}</span>}
        </div>

        <nav className="st-aktionen st-zwei auf" aria-label={t.start.aktionen} style={{ animationDelay: '.06s' }}>
          <button type="button" className={`knopf sinkt ${neu ? 'zweit' : ''}`} disabled={sendenAus} onClick={() => nav.gehe({ n: 'senden' })}>
            <ZahlZeichen art="aus" g={20} />{t.start.senden}
          </button>
          <button type="button" className={`knopf sinkt ${neu ? '' : 'zweit'}`} onClick={() => nav.gehe({ n: 'empfangen' })}>
            <ZahlZeichen art="ein" g={20} />{t.start.empfangen}
          </button>
        </nav>
      </div>

      <div className={`st-band ${meldung ? `an ${meldung.ton}` : ''}`} role="status" aria-live="polite">
        <div className="st-band-innen">
          {meldung && (
            <div className="st-band-inhalt">
              {meldung.onTipp
                ? <button type="button" className="st-band-meldung sinkt" onClick={meldung.onTipp}>
                    {inhalt}
                    {!meldung.aktion && <span className="st-band-pfeil"><S n="weiter" g={16} w={1.75} /></span>}
                  </button>
                : <div className="st-band-meldung">{inhalt}</div>}
              {meldung.aktion && <button type="button" className="st-band-aktion sinkt" onClick={meldung.aktion.onTipp}>{meldung.aktion.text}</button>}
            </div>
          )}
        </div>
      </div>

      <div className="st-unten">
        {!einfuehrungGesehen && <div className="st-einf"><EinfuehrungKarte /></div>}

        <div className="st-liste auf" style={{ animationDelay: '.1s' }}>
          <div className="sf-abschnitt st-abschnitt">
            <span className="sf-lab">{t.start.zuletzt}</span>
            {liste.length > 0 && <button type="button" className="verweis" onClick={() => nav.reiter('verlauf')}>{t.start.alle}</button>}
          </div>
          {liste.length === 0
            ? (konto ? <div className="leerstand">{neu ? <><b>{t.start.leer}</b><br /><span>{t.einf.k2.text}</span></> : t.start.leer}</div> : offline ? <div className="leerstand">–</div> : <Platzhalterzeilen />)
            : <div className="liste">{liste.map(z => <Zahlungszeile key={z.txid} z={z} onTipp={() => nav.gehe({ n: 'detail', zahlung: z })} />)}</div>}
        </div>
      </div>
    </div>
  );
}
