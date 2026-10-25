import { useEffect, useMemo, useState } from 'react';
import { useT } from '../i18n/index.ts';
import { useDaten, useNav, useWallet } from '../zustand.tsx';
import { einstellen, useEinstellungen } from '../wallet/einstellungen.ts';
import { kurzAdresse } from '../wallet/adresse.ts';
import { betragText } from '../wallet/betrag.ts';
import { kopieren } from '../geraet/geraet.ts';
import { useZahlungen } from '../ui/daten.ts';
import { S, Kopf, Betrag, Zahlungszeile, useMeldung } from '../ui/teile.tsx';

/** Das Guthaben zaehlt beim ersten Erscheinen hoch, danach springt es nicht mehr. */
let gezaehlt = false;
/** Nach dem naechsten Entsperren zaehlt das Guthaben wieder einmal hoch. */
export const hochzaehlenNeu = (): void => { gezaehlt = false; };

function useHochzaehlen(ziel: bigint | null): bigint | null {
  const [wert, setWert] = useState<bigint | null>(null);
  useEffect(() => {
    if (ziel === null) return;
    if (gezaehlt || ziel === 0n || window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
      gezaehlt = true; setWert(ziel); return;
    }
    gezaehlt = true;
    let bild = 0, t0 = 0;
    const lauf = (t: number) => {
      if (!t0) t0 = t;
      const p = Math.min(1, (t - t0) / 800), e = 1 - (1 - p) ** 3;
      setWert(p >= 1 ? ziel : (ziel * BigInt(Math.round(e * 10_000))) / 10_000n);
      if (p < 1) bild = requestAnimationFrame(lauf);
    };
    bild = requestAnimationFrame(lauf);
    return () => cancelAnimationFrame(bild);
  }, [ziel]);
  return wert;
}

/**
 * Start: das Guthaben in der Mitte, darunter die Bewegung des Tages und die
 * Adresse, dann die Aktionen und die letzten Zahlungen.
 */
export function Start() {
  const t = useT();
  const nav = useNav();
  const wallet = useWallet();
  const { konto, offline } = useDaten();
  const { wartend, verlauf } = useZahlungen();
  const { verborgen } = useEinstellungen();
  const melde = useMeldung();
  const stand = useHochzaehlen(konto ? BigInt(konto.balance) : null);
  const liste = [...wartend, ...verlauf].slice(0, 6);

  // Was heute dazukam oder wegging -- nur aus den Zahlungen, die schon in Bloecken stehen.
  const heute = useMemo(() => {
    const h = new Date(), anfang = new Date(h.getFullYear(), h.getMonth(), h.getDate()).getTime() / 1000;
    let summe = 0n;
    for (const z of verlauf) if (z.zeit !== null && z.zeit >= anfang) summe += z.art === 'aus' ? -(z.betrag + z.gebuehr) : z.betrag;
    return summe;
  }, [verlauf]);

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
            : <Betrag wert={stand} ziel={BigInt(konto?.balance ?? stand)} />}
        <div className="unterzeile-start">
          {!verborgen && heute !== 0n && (
            <span className={`pille ${heute > 0n ? 'gruen' : 'blau'}`}><i />{heute > 0n ? '+' : '−'}{betragText(heute < 0n ? -heute : heute, t.code)} YSR {t.start.heute}</span>
          )}
        </div>
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
