import { useT } from '../i18n/index.ts';
import { useDaten, useNav } from '../zustand.tsx';
import { EXPLORER } from '../netz/api.ts';
import { ausEintrag, bestaetigungen, type Zahlung } from '../wallet/zahlungen.ts';
import { betragGenau } from '../wallet/betrag.ts';
import { useKontakte } from '../wallet/kontakte.ts';
import { imBrowserOeffnen, kopieren } from '../geraet/geraet.ts';
import { S, KopfZurueck, Betrag, Bild, Vierer, Wartering, useMeldung, useZeit } from '../ui/teile.tsx';

export function Detail({ zahlung }: { zahlung: Zahlung }) {
  const t = useT();
  const nav = useNav();
  const zeit = useZeit();
  const melde = useMeldung();
  const { konto, spitze } = useDaten();
  const { name } = useKontakte();

  // Eine wartende Zahlung, die inzwischen in einem Block steht, zeigt den neuen Stand.
  const neu = zahlung.hoehe === null ? konto?.history.find(e => e.txid === zahlung.txid) : undefined;
  const z = neu ? ausEintrag(neu) : zahlung;
  const wartet = z.hoehe === null;
  const ein = z.art !== 'aus';
  const belohnung = z.art === 'belohnung' || z.art === 'pool';
  const n = bestaetigungen(z, spitze);
  const wer = name(z.gegen);
  const wann = z.zeit === null ? '' : `${zeit.tag(z.zeit)}, ${zeit.uhr(z.zeit)}`;

  const stufe = (stand: 'fertig' | 'dran' | 'offen', titel: string, text: string, letzte = false) => (
    <div className="stufe">
      <div className="saeule">
        <div className={`punkt ${stand}`}>{stand === 'fertig' && <S n="haken" g={14} w={3} />}</div>
        {!letzte && <div className="strich" />}
      </div>
      <div className="worte"><b className={stand === 'offen' ? 'matt' : undefined}>{titel}</b><small>{text}</small></div>
    </div>
  );

  return (
    <div className="seite" style={{ gap: 14 }}>
      <KopfZurueck titel={t.detail.titel} zurueck={nav.zurueck} />

      <div className="mitte-block" style={{ gap: 12 }}>
        {wartet && !ein ? <Wartering gross />
          : wer && !belohnung ? <Bild name={wer} g={64} />
          : <div className={`kachel gross ${wartet ? 'bern' : ein ? 'ein' : 'aus'}`}>
              <S n={belohnung ? 'block' : ein ? 'runter' : 'hoch'} g={28} w={2.2} />
            </div>}
        {wer && !belohnung && <small>{ein ? t.zahlung.von(wer) : t.zahlung.an(wer)}</small>}
        <Betrag wert={z.betrag} vz={ein ? '+' : '−'} klasse="mittel" />
        <div className={`pille ${wartet ? 'bern' : 'gruen'}`}><i />{wartet ? t.detail.wartet : t.detail.s3Da(zeit.zahl(n))}</div>
      </div>

      {!belohnung && (
        <div className="karte stufen">
          {stufe('fertig', ein ? t.detail.s1Ein : t.detail.s1, wartet ? (z.zeit ? zeit.kurz(z.zeit) : '') : '')}
          {wartet
            ? stufe('dran', t.detail.s2, t.detail.s2Text)
            : stufe('fertig', t.detail.s2Da(zeit.zahl(z.hoehe!)), wann)}
          {wartet
            ? stufe('offen', t.detail.s3, t.detail.s3Text, true)
            : stufe('fertig', t.detail.s3, t.detail.s3Da(zeit.zahl(n)), true)}
        </div>
      )}

      <div className="karte innen">
        {belohnung && (
          <div className="paar"><span>{z.art === 'pool' ? t.zahlung.pool : t.zahlung.belohnung}</span>
            <span>{t.zahlung.block(zeit.zahl(z.hoehe!))}{z.art === 'pool' && z.anteile ? `, ${t.detail.anteile(z.anteile)}` : ''}</span></div>
        )}
        {belohnung && wann && <div className="paar"><span>{t.detail.zeit}</span><span>{wann}</span></div>}
        {z.gegen && (
          <div className="paar rechts">
            <span>{ein ? t.detail.von : t.detail.an}</span>
            <div>
              {wer && <b style={{ fontSize: 15, fontWeight: 700 }}>{wer}</b>}
              <Vierer adresse={z.gegen} klasse="klein-schrift" />
            </div>
          </div>
        )}
        {!belohnung && !ein && <div className="paar"><span>{t.detail.netzgebuehr}</span><span className="ziffern">{betragGenau(z.gebuehr, t.code, 8, 4)} YSR</span></div>}
        {z.notiz && <div className="paar"><span>{t.detail.notiz}</span><span className="waehlbar" style={{ fontWeight: 600, textAlign: 'right', overflowWrap: 'anywhere' }}>{z.notiz}</span></div>}
        <div className="paar" style={{ alignItems: 'center', padding: '4px 0' }}>
          <span>{t.detail.transaktion}</span>
          <button type="button" className="verweis ziffern" style={{ color: 'var(--text)', fontWeight: 400, gap: 8 }} aria-label={t.detail.txKopieren}
                  onClick={async () => { if (await kopieren(z.txid)) melde(t.allg.kopiert); }}>
            <span>{z.txid.slice(0, 8)}…{z.txid.slice(-6)}</span><S n="kopie" g={16} />
          </button>
        </div>
      </div>

      <div className="fuell" />
      <div className={z.gegen ? 'paar-knoepfe' : 'stapel'}>
        {z.gegen && (wer
          ? <button type="button" className="knopf zweit sinkt" style={{ fontSize: 15 }} onClick={() => nav.gehe({ n: 'kontakt', adresse: z.gegen! })}>{t.detail.zumKontakt}</button>
          : <button type="button" className="knopf zweit sinkt" style={{ fontSize: 15 }} onClick={() => nav.gehe({ n: 'kontaktBearbeiten', adresse: z.gegen! })}>{t.detail.alsKontakt}</button>)}
        <button type="button" className="knopf zweit sinkt" style={{ fontSize: 15 }} onClick={() => imBrowserOeffnen(`${EXPLORER}#tx-${z.txid}`)}>
          <span>{t.detail.explorer}</span><S n="extern" g={18} />
        </button>
      </div>
    </div>
  );
}
