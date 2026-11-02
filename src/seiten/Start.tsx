import { useT } from '../i18n/index.ts';
import { useDaten, useNav, useWallet } from '../zustand.tsx';
import { einstellen, useEinstellungen } from '../wallet/einstellungen.ts';
import { nichtGelistet } from '../wallet/zahlungen.ts';
import { kopieren, tippGefuehl } from '../geraet/geraet.ts';
import { useZahlungen } from '../ui/daten.ts';
import { S, Betrag, Kasten, Platzhalterzeilen, Zahlungszeile, useAnzeige, useMeldung, useWer } from '../ui/teile.tsx';
import { YMarke, YBaender } from '../ui/zeichen.tsx';
import { EinfuehrungKarte } from './Einfuehrung.tsx';

/** "ysr1…3h8q": nur zum Wiedererkennen. Kopiert wird immer die volle Adresse. */
const kurz = (a: string | null) => (a ? `${a.slice(0, 4)}…${a.slice(-4)}` : '');

/**
 * Wallet (Designsystem 4.2): Adresse oben links, das Guthaben in der Mitte,
 * darunter eine wartende Zahlung, die drei Aktionen und die letzten Zahlungen.
 * Kein Block- oder Synchronstand auf dieser Seite; ohne Verbindung ein Hinweis.
 */
export function Start() {
  const t = useT();
  const nav = useNav();
  const wallet = useWallet();
  const { konto, offline } = useDaten();
  const { wartend, verlauf } = useZahlungen();
  const { verborgen, einfuehrungGesehen } = useEinstellungen();
  const melde = useMeldung();
  const anzeige = useAnzeige();
  const wer = useWer();
  const stand = konto ? BigInt(konto.balance) : null;
  const liste = [...wartend, ...verlauf].slice(0, 5);
  const erste = wartend[0];

  const aktion = (label: string, symbol: Parameters<typeof S>[0]['n'], onTipp: () => void, blau = false) => (
    <button type="button" className={`rund sinkt ${blau ? 'blau' : ''}`} onClick={onTipp}><span className="kreis"><S n={symbol} g={22} w={1.8} /></span><span>{label}</span></button>
  );

  return (
    <div className="seite mit-nav st">
      <div className="st-kopf">
        <button type="button" className="sf-chip sinkt" aria-label={t.start.adresseKopieren}
                onClick={async () => { if (wallet.adresse && await kopieren(wallet.adresse)) { tippGefuehl(); melde(t.start.adresseKopiert); } }}>
          <span className="sf-chip-zeichen"><YMarke g={14} farbe="var(--blau-hell)" /></span>
          <span className="mono">{kurz(wallet.adresse)}</span>
        </button>
        <span className="st-knoepfe">
          <button type="button" className="klein sinkt" aria-pressed={verborgen} aria-label={verborgen ? t.start.zeigen : t.start.verbergen}
                  onClick={() => einstellen({ verborgen: !verborgen })}><S n={verborgen ? 'augeZu' : 'auge'} g={19} w={1.6} /></button>
          <button type="button" className="klein sinkt" aria-label={t.start.sperren} onClick={wallet.sperren}><S n="schloss" g={19} w={1.6} /></button>
        </span>
      </div>

      {offline && <div className="st-meldung" role="status"><Kasten art="warn" symbol="warnung"><b>{t.start.offlineTitel}</b> <span className="leise">{t.start.offlineText}</span></Kasten></div>}

      <div className={`st-guthaben auf ${offline ? 'alt' : ''}`}>
        <span className="sf-lab">{t.start.guthaben}</span>
        {verborgen
          ? <div className="betrag start"><div className="gross" aria-label={t.start.zeigen}>••••<i>,••</i></div></div>
          : stand === null
            ? (offline ? <div className="st-laedt">–</div> : <span className="st-platzhalter" aria-hidden="true"><i /></span>)
            : <Betrag wert={stand} uebersicht klasse="start" />}
        <span className="sf-einheit"><YMarke g={13} farbe="var(--leise)" />YSR</span>
        <span className="st-teilung" aria-hidden="true"><i /><i /></span>
        {stand === null && !offline && !verborgen && <span className="sf-klein st-laedt-text" role="status">{t.start.laedt}</span>}
        {erste && (
          <button type="button" className="st-wartet sinkt" onClick={() => nav.gehe({ n: 'detail', zahlung: erste })}>
            <YBaender stand={['erledigt', 'wartet', 'offen']} label={nichtGelistet(erste) ? t.zahlung.nichtGelistet : t.zahlung.wartetBlock} />
            <span>{wartend.length > 1 ? t.start.wartenMehrere(wartend.length)
              : <><b>{anzeige(erste.betrag)}</b> {erste.art === 'aus' ? t.start.wartetAn(wer(erste.gegen)) : t.start.wartetVon(wer(erste.gegen))}</>}</span>
          </button>
        )}
      </div>

      <nav className="st-aktionen auf" aria-label={t.start.aktionen} style={{ animationDelay: '.06s' }}>
        {aktion(t.start.senden, 'hoch', () => nav.gehe({ n: 'senden' }), true)}
        {aktion(t.start.empfangen, 'runter', () => nav.gehe({ n: 'empfangen' }))}
        {aktion(t.start.scannen, 'scan', () => nav.gehe({ n: 'senden', scan: true }))}
      </nav>

      {!einfuehrungGesehen && <EinfuehrungKarte />}

      <div className="st-liste auf" style={{ animationDelay: '.1s' }}>
        <div className="sf-abschnitt st-abschnitt">
          <span className="sf-lab">{t.start.zuletzt}</span>
          {liste.length > 0 && <button type="button" className="verweis" onClick={() => nav.reiter('verlauf')}>{t.start.alle}</button>}
        </div>
        {liste.length === 0
          ? (konto ? <div className="leerstand">{t.start.leer}</div> : offline ? <div className="leerstand">–</div> : <Platzhalterzeilen />)
          : <div className="liste">{liste.map(z => <Zahlungszeile key={z.txid} z={z} onTipp={() => nav.gehe({ n: 'detail', zahlung: z })} />)}</div>}
      </div>
    </div>
  );
}
