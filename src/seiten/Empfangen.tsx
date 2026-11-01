import { useState } from 'react';
import { useT } from '../i18n/index.ts';
import { useNav, useWallet } from '../zustand.tsx';
import { zahlungsCode } from '../wallet/qr.ts';
import { betragGenau, dezimalZeichen, eingabeEinheiten } from '../wallet/betrag.ts';
import { NOTIZ_BYTES, notizBytes, notizKuerzen } from '../wallet/notiz.ts';
import { useKontakte } from '../wallet/kontakte.ts';
import { anforderungMerken } from '../wallet/anforderungen.ts';
import { DECIMALS } from '../kern/params.ts';
import { kopieren, teilen, tippGefuehl } from '../geraet/geraet.ts';
import { tippeBetrag, tippeText } from '../wallet/tasten.ts';
import { S, Vierer, QrBild, Blatt, useMeldung } from '../ui/teile.tsx';
import { Tippfeld, betragKlasse } from '../ui/tastatur.tsx';

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

  const kopiert = async () => {
    if (await kopieren(anforderung ? code : adresse)) { tippGefuehl(); melde(t.allg.kopiert); }
  };

  return (
    <div className="seite sf">
      <div className="sf-kopf">
        <button type="button" className="klein sinkt" aria-label={t.allg.zurueck} onClick={nav.zurueck}><S n="zurueck" w={2.2} /></button>
        <div className="sf-mitte"><span className="sf-titel">{fuerName ? t.empfangen.fuer(fuerName) : t.empfangen.titel}</span></div>
        <span className="sf-platz" />
      </div>

      <div className="ep-titel">
        <h1 className="sf-t1">{t.empfangen.deine}</h1>
        <span className="sf-klein">{anforderung
          ? <><b className="ziffern">{t.empfangen.angefordertBetrag(betragGenau(anforderung.betrag, t.code, DECIMALS, 2))}</b>{anforderung.notiz && <> · {anforderung.notiz}</>}</>
          : t.empfangen.unter}</span>
      </div>

      <div className="ep-qr"><QrBild text={code} label={anforderung ? t.empfangen.qrBetrag : t.empfangen.qr} /></div>

      <div className="ep-adresse">
        <Vierer adresse={adresse} klasse="mittig ep-vierer" />
        <span className="st-teilung ep-teilung" aria-hidden="true"><i /><i /></span>
      </div>

      <div className="fuell" />
      <div className="paar-knoepfe">
        <button type="button" className="knopf zweit sinkt" onClick={kopiert}><S n="kopie" g={18} w={1.6} /><span>{t.empfangen.kopieren}</span></button>
        <button type="button" className="knopf zweit sinkt" onClick={weitergeben}><S n="teilen" g={18} w={1.6} /><span>{t.empfangen.teilen}</span></button>
      </div>
      <button type="button" className="knopf sinkt ep-anfordern" onClick={() => setBlatt(true)}>{anforderung ? t.empfangen.aendern : t.empfangen.anfordernKurz}</button>
      {anforderung && <button type="button" className="knopf text sinkt" onClick={() => { setAnforderung(null); setVermerkt(false); }}>{t.empfangen.ohneBetrag}</button>}

      {blatt && (
        <Blatt titel={t.empfangen.blattTitel} onZu={() => setBlatt(false)}>
          <p>{t.empfangen.blattText}</p>
          <div className="feldgruppe">
            <span className="feldname">{t.senden.betrag}</span>
            <div className="betragblock" style={{ padding: 0 }}>
              <div className="oben">
                <Tippfeld id="anf-betrag" art="ziffern" label={t.senden.betrag} wert={eingabe} platz="0" klasse={betragKlasse(eingabe)} auto
                          onTaste={z => setEingabe(b => tippeBetrag(b, z, zeichen, DECIMALS))} />
                <span>YSR</span>
              </div>
            </div>
          </div>
          <div className="feldgruppe">
            <div className="feldkopf">
              <span className="feldname">{t.senden.notiz} <span style={{ fontWeight: 500, color: 'var(--leise)' }}>{t.allg.optional}</span></span>
              <small>{notizBytes(notizEingabe)} / {NOTIZ_BYTES}</small>
            </div>
            <Tippfeld id="anf-notiz" art="text" klasse="feld" gross="anfang" label={t.senden.notiz} wert={notizEingabe}
                      onTaste={z => setNotizEingabe(n => notizKuerzen(tippeText(n, z)))} />
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
