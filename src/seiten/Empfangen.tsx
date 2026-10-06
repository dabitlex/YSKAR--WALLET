import { useState } from 'react';
import { useT } from '../i18n/index.ts';
import { useNav, useWallet } from '../zustand.tsx';
import { zahlungsCode } from '../wallet/qr.ts';
import { betragGenau, dezimalZeichen, eingabeEinheiten } from '../wallet/betrag.ts';
import { NOTIZ_BYTES, notizBytes, notizKuerzen } from '../wallet/notiz.ts';
import { useKontakte } from '../wallet/kontakte.ts';
import { anforderungMerken } from '../wallet/anforderungen.ts';
import { DECIMALS } from '../kern/params.ts';
import { kopieren, teilen } from '../geraet/geraet.ts';
import { S, KopfZurueck, Vierer, QrBild, Blatt, useMeldung } from '../ui/teile.tsx';

/**
 * Empfangen: die eigene Adresse als QR-Code -- wahlweise mit Betrag und
 * Notiz. `fuer` ist die Adresse eines Kontakts, wenn die Anforderung aus
 * dessen Verlauf heraus erstellt wird; dann wird sie dort vermerkt.
 */
export function Empfangen({ fuer }: { fuer?: string }) {
  const t = useT();
  const nav = useNav();
  const wallet = useWallet();
  const melde = useMeldung();
  const { name } = useKontakte();
  const zeichen = dezimalZeichen(t.code);
  const adresse = wallet.adresse ?? '';
  const [blatt, setBlatt] = useState(!!fuer);
  const [eingabe, setEingabe] = useState('');
  const [notizEingabe, setNotizEingabe] = useState('');
  const [anforderung, setAnforderung] = useState<{ betrag: bigint; notiz: string } | null>(null);
  const [vermerkt, setVermerkt] = useState(false);

  const einheiten = eingabeEinheiten(eingabe, DECIMALS);
  const code = anforderung ? zahlungsCode(adresse, anforderung.betrag, anforderung.notiz, DECIMALS) : adresse;
  const fuerName = fuer ? name(fuer) : null;

  const weitergeben = async () => {
    const text = anforderung
      ? t.empfangen.teilText(betragGenau(anforderung.betrag, t.code, DECIMALS, 2), adresse, code, anforderung.notiz)
      : t.empfangen.teilAdresse(adresse);
    if (!(await teilen(text, t.empfangen.teilTitel))) { await kopieren(text); melde(t.empfangen.nichtTeilbar); }
    if (anforderung && fuer && !vermerkt) { anforderungMerken(fuer, anforderung.betrag, anforderung.notiz); setVermerkt(true); }
  };

  return (
    <div className="seite">
      <KopfZurueck titel={fuerName ? t.empfangen.fuer(fuerName) : t.empfangen.titel} zurueck={nav.zurueck} />

      <div className="karte qrkarte">
        <QrBild text={code} label={anforderung ? t.empfangen.qrBetrag : t.empfangen.qr} />
        {anforderung && (
          <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 4 }}>
            <div className="etikett">{t.empfangen.angefordert}</div>
            <div className="ziffern" style={{ fontSize: 20, fontWeight: 500 }}>{betragGenau(anforderung.betrag, t.code, DECIMALS, 2)} <small style={{ fontSize: 13, color: 'var(--leise)' }}>YSR</small></div>
            {anforderung.notiz && <div style={{ fontSize: 14, color: 'var(--lesen)' }}>{anforderung.notiz}</div>}
          </div>
        )}
        <div style={{ alignSelf: 'stretch', height: 1, background: 'var(--linie)' }} />
        <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 6 }}>
          <div className="etikett">{t.empfangen.deine}</div>
          <Vierer adresse={adresse} klasse="mittig" />
        </div>
      </div>

      <div className="paar-knoepfe">
        <button type="button" className="knopf sinkt" style={{ fontSize: 16 }} onClick={async () => { if (await kopieren(anforderung ? code : adresse)) melde(t.allg.kopiert); }}>
          <S n="kopie" g={19} /><span>{t.empfangen.kopieren}</span>
        </button>
        <button type="button" className="knopf zweit sinkt" onClick={weitergeben}><S n="teilen" g={19} /><span>{t.empfangen.teilen}</span></button>
      </div>

      {anforderung
        ? <button type="button" className="knopf zweit sinkt" onClick={() => { setAnforderung(null); setVermerkt(false); }}>{t.empfangen.ohneBetrag}</button>
        : <button type="button" className="knopf zweit sinkt" style={{ justifyContent: 'space-between', fontSize: 15, fontWeight: 600 }} onClick={() => setBlatt(true)}>
            <span>{t.empfangen.anfordern}</span><S n="weiter" g={18} w={2.2} />
          </button>}

      <div className="fuell" />
      <div className="hinweis"><S n="info" g={18} /><div>{t.empfangen.hinweis}</div></div>

      {blatt && (
        <Blatt titel={t.empfangen.blattTitel} onZu={() => setBlatt(false)}>
          <p>{t.empfangen.blattText}</p>
          <div className="feldgruppe">
            <label htmlFor="anf-betrag">{t.senden.betrag}</label>
            <div className="betragfeld">
              <div className="oben">
                <input id="anf-betrag" type="text" inputMode="decimal" placeholder="0" autoComplete="off" value={eingabe}
                       onChange={e => {
                         const v = e.target.value.replace(/[^\d.,]/g, '').replace(/[.,]/g, zeichen);
                         const teile = v.split(zeichen);
                         setEingabe(teile.length > 1 ? `${teile[0].slice(0, 12)}${zeichen}${teile.slice(1).join('').slice(0, DECIMALS)}` : v.slice(0, 12));
                       }} />
                <span>YSR</span>
              </div>
            </div>
          </div>
          <div className="feldgruppe">
            <div className="feldkopf">
              <label htmlFor="anf-notiz">{t.senden.notiz} <span style={{ fontWeight: 500, color: 'var(--leise)' }}>{t.allg.optional}</span></label>
              <small>{notizBytes(notizEingabe)} / {NOTIZ_BYTES}</small>
            </div>
            <input id="anf-notiz" type="text" className="feld vertieft" value={notizEingabe}
                   onChange={e => setNotizEingabe(notizKuerzen(e.target.value.replace(/[\u0000-\u001f\u007f]/g, ' ')))} />
          </div>
          <button type="button" className="knopf sinkt" disabled={einheiten <= 0n}
                  onClick={() => { setAnforderung({ betrag: einheiten, notiz: notizKuerzen(notizEingabe.trim()) }); setVermerkt(false); setBlatt(false); }}>
            {t.empfangen.erstellen}
          </button>
        </Blatt>
      )}
    </div>
  );
}
