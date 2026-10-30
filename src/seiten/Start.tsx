import { useT } from '../i18n/index.ts';
import { useDaten, useNav, useWallet } from '../zustand.tsx';
import { einstellen, useEinstellungen } from '../wallet/einstellungen.ts';
import { kurzAdresse } from '../wallet/adresse.ts';
import { kopieren } from '../geraet/geraet.ts';
import { useZahlungen } from '../ui/daten.ts';
import { S, Kopf, Betrag, Zahlungszeile, useMeldung } from '../ui/teile.tsx';
import { EinfuehrungKarte } from './Einfuehrung.tsx';

/**
 * Start: das Guthaben in der Mitte, darunter die Adresse, dann die Aktionen
 * und die letzten Zahlungen.
 */
export function Start() {
  const t = useT();
  const nav = useNav();
  const wallet = useWallet();
  const { konto, offline } = useDaten();
  const { wartend, verlauf } = useZahlungen();
  const { verborgen, einfuehrungGesehen } = useEinstellungen();
  const melde = useMeldung();
  const stand = konto ? BigInt(konto.balance) : null;
  const liste = [...wartend, ...verlauf].slice(0, 6);

  const rund = (label: string, symbol: Parameters<typeof S>[0]['n'], onTipp: () => void, blau = false) => (
    <button type="button" className={`rund sinkt ${blau ? 'blau' : ''}`} onClick={onTipp}><span className="kreis"><S n={symbol} g={24} w={2.2} /></span><span>{label}</span></button>
  );

  return (
    <div className="seite mit-nav">
      <div className="schein" aria-hidden="true" />
      <Kopf titel={t.start.titel} rechts={
        <button type="button" className="klein sinkt" aria-label={t.start.sperren} onClick={wallet.sperren}><S n="schloss" /></button>} />

      {offline && <div className="band" role="status">{t.start.offline}</div>}

      <div className="guthaben auf">
        <div className="etikettzeile">
          <span className="etikett">{t.start.gesamt}</span>
          <button type="button" className="klein leer sinkt" aria-pressed={verborgen}
                  aria-label={verborgen ? t.start.zeigen : t.start.verbergen} onClick={() => einstellen({ verborgen: !verborgen })}>
            <S n={verborgen ? 'augeZu' : 'auge'} g={18} />
          </button>
        </div>
        {verborgen
          ? <div className="betrag"><div className="gross" aria-hidden="true">••••<i>••</i></div><div className="einheit">YSR</div></div>
          : stand === null
            ? <div style={{ color: 'var(--leise)', fontSize: 15, minHeight: 56, display: 'flex', alignItems: 'center' }}>{offline ? '–' : t.start.laedt}</div>
            : <Betrag wert={stand} uebersicht />}
        <div className="unterzeile-start">
          <button type="button" className="adress-pille sinkt" aria-label={t.start.adresseKopieren}
                  onClick={async () => { if (wallet.adresse && await kopieren(wallet.adresse)) melde(t.start.adresseKopiert); }}>
            <span>{kurzAdresse(wallet.adresse, '')}</span><S n="kopie" g={14} />
          </button>
        </div>
      </div>

      <div className="rund-reihe auf" style={{ animationDelay: '.06s' }}>
        {rund(t.start.senden, 'hoch', () => nav.gehe({ n: 'senden' }), true)}
        {rund(t.start.empfangen, 'runter', () => nav.gehe({ n: 'empfangen' }))}
        {rund(t.start.scannen, 'scan', () => nav.gehe({ n: 'senden', scan: true }))}
      </div>

      {!einfuehrungGesehen && <EinfuehrungKarte />}

      <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }} className="auf">
        <div className="ueber">
          <b>{t.start.letzte}</b>
          {liste.length > 0 && <button type="button" className="verweis" onClick={() => nav.reiter('verlauf')}>{t.start.alle}</button>}
        </div>
        {liste.length === 0
          ? <div className="karte"><div className="leerstand">{konto ? t.start.leer : offline ? t.start.offline : t.start.laedt}</div></div>
          : <div className="karte">{liste.map(z => <Zahlungszeile key={z.txid} z={z} onTipp={() => nav.gehe({ n: 'detail', zahlung: z })} />)}</div>}
      </div>
    </div>
  );
}
