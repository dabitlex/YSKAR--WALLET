import { useEffect, useRef } from 'react';
import { useT } from '../i18n/index.ts';
import { useDaten, useNav } from '../zustand.tsx';
import { EXPLORER } from '../netz/api.ts';
import { ausEintrag, bestaetigungen, nichtGelistet, type Zahlung } from '../wallet/zahlungen.ts';
import { betragGenau, dezimalZeichen } from '../wallet/betrag.ts';
import { useKontakte } from '../wallet/kontakte.ts';
import { erfolgGefuehl, imBrowserOeffnen, kopieren } from '../geraet/geraet.ts';
import { useZahlungen } from '../ui/daten.ts';
import { S, Vierer, useMeldung, useWer, useZeit } from '../ui/teile.tsx';
import { YStatus, type Teilstand } from '../ui/zeichen.tsx';

/**
 * Eine Zahlung (Designsystem 4.2): Betrag, Stand im Netz mit dem Y-Zeichen,
 * dann die Angaben. Der Stand gilt nur, soweit eine Antwort ihn belegt:
 * wartend (beim Knoten oder nicht mehr in seiner Liste), im Block, und die
 * Zahl der Bestaetigungen. "Endgueltig" gibt es nicht.
 */
export function Detail({ zahlung }: { zahlung: Zahlung }) {
  const t = useT();
  const nav = useNav();
  const zeit = useZeit();
  const melde = useMeldung();
  const wer = useWer();
  const { konto, spitze } = useDaten();
  const { wartend } = useZahlungen();
  const { name } = useKontakte();

  // Den Stand von jetzt zeigen: Eine wartende Zahlung kann inzwischen in einem Block stehen.
  const imBlock = zahlung.hoehe === null ? konto?.history.find(e => e.txid === zahlung.txid) : undefined;
  const z = imBlock ? ausEintrag(imBlock) : zahlung.hoehe === null ? (wartend.find(w => w.txid === zahlung.txid) ?? zahlung) : zahlung;
  const wartet = z.hoehe === null;
  const ein = z.art !== 'aus';
  const belohnung = z.art === 'belohnung' || z.art === 'pool';
  const n = bestaetigungen(z, spitze);
  const kontakt = name(z.gegen);
  const wann = z.zeit === null ? '' : `${zeit.tag(z.zeit)}, ${zeit.uhr(z.zeit)}`;
  const weg = nichtGelistet(z);

  // "Im Block": dezentes Erfolgssignal, wenn es waehrend des Zusehens passiert -- einmal.
  const warWartend = useRef(wartet);
  useEffect(() => {
    if (warWartend.current && !wartet) erfolgGefuehl();
    warWartend.current = wartet;
  }, [wartet]);

  // Y-Zeichen: Band 1 im Netz, Band 2 im Block, Stamm ab zwei Bestaetigungen.
  const y: [Teilstand, Teilstand, Teilstand] = wartet
    ? [weg ? 'offen' : 'erledigt', 'wartet', 'offen']
    : ['erledigt', 'erledigt', n >= 2 ? 'erledigt' : 'wartet'];
  const titel = wartet ? (weg ? t.detail.statusNichtGelistet : t.detail.statusWartet) : t.detail.imBlock(zeit.zahl(z.hoehe!));
  const unter = wartet
    ? (weg ? t.detail.statusNichtGelistetText : z.mempool === true ? t.detail.statusImMempool : z.mempool === null ? t.detail.statusUnbekannt : t.detail.s2Text)
    : t.zahlung.bestaetigungen(zeit.zahl(n));

  const text = betragGenau(z.betrag, t.code, 8, 2);
  const i = text.lastIndexOf(dezimalZeichen(t.code));
  const wem = z.art === 'pool' ? `YSR · ${t.zahlung.pool}` : z.art === 'belohnung' ? `YSR · ${t.zahlung.belohnung}`
    : ein ? t.detail.vonWem(wer(z.gegen)) : t.detail.anWem(wer(z.gegen));

  return (
    <div className="seite sf">
      <div className="sf-kopf">
        <button type="button" className="klein sinkt" aria-label={t.allg.zurueck} onClick={nav.zurueck}><S n="zurueck" w={2.2} /></button>
        <div className="sf-mitte"><span className="sf-titel">{t.detail.titel}</span></div>
        <span className="sf-platz" />
      </div>

      <div className="dt-betrag">
        <span className={`sf-zahl dt ${ein ? 'plus' : ''}`}>{ein ? '+' : '−'}{i < 0 ? text : <>{text.slice(0, i)}<span className="nk">{text.slice(i)}</span></>}</span>
        <span className="sf-klein">{wem}</span>
      </div>

      <div className={`karte dt-status ${weg ? 'weg' : ''}`} role="status">
        <YStatus g={34} stand={y} label={titel} />
        <span><b>{titel}</b><span className="sf-klein">{unter}</span></span>
      </div>
      <p className="sf-fuss">{t.detail.endgueltig}</p>

      <div className="karte sf-linien dt-linien">
        {wann && <div className="sf-linie"><span>{t.detail.zeit}</span><span className="ziffern">{wann}</span></div>}
        {belohnung && z.hoehe !== null && (
          <div className="sf-linie"><span>{t.detail.block}</span>
            <span className="ziffern">#{zeit.zahl(z.hoehe)}{z.art === 'pool' && z.anteile ? ` · ${t.detail.anteile(z.anteile)}` : ''}</span></div>
        )}
        {z.gegen && (
          <div className="sf-linie oben"><span>{ein ? t.detail.von : t.detail.an}</span><Vierer adresse={z.gegen} klasse="sf-adresse" /></div>
        )}
        {!belohnung && !ein && <div className="sf-linie"><span>{t.detail.netzgebuehr}</span><span className="ziffern">{betragGenau(z.gebuehr, t.code, 8, 2)} YSR</span></div>}
        {z.notiz && <div className="sf-linie"><span>{t.detail.notiz}</span><span className="waehlbar sf-notiz">{z.notiz}</span></div>}
        <div className="sf-linie">
          <span>{t.detail.transaktion}</span>
          <button type="button" className="dt-tx sinkt" aria-label={t.detail.txKopieren}
                  onClick={async () => { if (await kopieren(z.txid)) melde(t.allg.kopiert); }}>
            <span className="mono">{z.txid.slice(0, 4)}…{z.txid.slice(-4)}</span><S n="kopie" g={15} w={1.6} />
          </button>
        </div>
      </div>

      <div className="fuell" />
      <div className={z.gegen ? 'paar-knoepfe' : 'stapel'}>
        {z.gegen && (kontakt
          ? <button type="button" className="knopf zweit sinkt" onClick={() => nav.gehe({ n: 'kontakt', adresse: z.gegen! })}>{t.detail.zumKontakt}</button>
          : <button type="button" className="knopf zweit sinkt" onClick={() => nav.gehe({ n: 'kontaktBearbeiten', adresse: z.gegen! })}>{t.detail.alsKontakt}</button>)}
        <button type="button" className="knopf zweit sinkt" onClick={() => imBrowserOeffnen(`${EXPLORER}#tx-${z.txid}`)}>
          <span>{t.detail.explorer}</span><S n="extern" g={17} />
        </button>
      </div>
    </div>
  );
}
