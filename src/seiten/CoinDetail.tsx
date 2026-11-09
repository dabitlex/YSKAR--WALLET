import { useState } from 'react';
import { useT } from '../i18n/index.ts';
import { useEinstellungen } from '../wallet/einstellungen.ts';
import { useNav } from '../zustand.tsx';
import { coinAusListe, useCoinDetail, useVerlauf } from '../markt/haken.ts';
import { kompakt, kompaktZahl, kurs, prozent, richtung, alter } from '../markt/format.ts';
import { ZEITRAEUME, type Zeitraum } from '../markt/typen.ts';
import { useFavoriten, umschalten, istFavorit } from '../watchlist/favoriten.ts';
import { LinienChart } from '../diagramm/linie.tsx';
import { veraenderung } from '../diagramm/pfad.ts';
import { S, KopfZurueck } from '../ui/teile.tsx';
import { imBrowserOeffnen } from '../geraet/geraet.ts';

/**
 * Eine Kryptowaehrung im Detail: Kurs, Verlauf, Kennzahlen, Beschreibung.
 *
 * Der Chart zeigt den gewaehlten Zeitraum und laesst sich mit dem Finger
 * abtasten. Die Prozentzahl ueber dem Chart gilt FUER DEN GEZEIGTEN
 * ZEITRAUM, nicht pauschal fuer 24 Stunden -- sonst stuende beim
 * Jahreschart eine Tagesveraenderung, was niemand erwartet.
 */

function zeitraumName(z: Zeitraum, t: ReturnType<typeof useT>): string {
  switch (z) {
    case '1h': return t.markt.z1h;
    case '24h': return t.markt.z24h;
    case '7d': return t.markt.z7d;
    case '30d': return t.markt.z30d;
    case '90d': return t.markt.z90d;
    case '1j': return t.markt.z1j;
    case 'max': return t.markt.zmax;
  }
}

function Kennzahl({ lab, wert }: { lab: string; wert: string | null }) {
  const t = useT();
  return (
    <div className="cd-kennzahl">
      <span className="nk-lab">{lab}</span>
      <b>{wert ?? t.markt.unbekannt}</b>
    </div>
  );
}

export function CoinDetail({ id }: { id: string }) {
  const t = useT();
  const nav = useNav();
  const { waehrung } = useEinstellungen();
  const fav = useFavoriten();
  const [zeitraum, setZeitraum] = useState<Zeitraum>('7d');

  const { daten: d, laedt, fehler } = useCoinDetail(id, waehrung);
  const v = useVerlauf(id, zeitraum, waehrung);
  // Die Liste hat Name, Symbol, Bild, Rang und Kurs schon geholt. Solange
  // die Stammdaten fehlen -- etwa waehrend einer Sperre nach 429 --, wird
  // damit gezeigt, was da ist, statt "nicht verfuegbar" zu melden.
  const basis = coinAusListe(id, waehrung);
  const voll = d?.wert ?? null;
  const coin = voll ?? (basis ? { ...basis, beschreibung: null, beschreibungSprache: null,
    seite: null, umlauf: null, gesamtmenge: null, hoechstmenge: null, ath: null } : null);
  const punkte = v.daten?.wert.punkte ?? [];

  // Die Veraenderung gilt fuer den gezeigten Zeitraum. Nur bei 24 Stunden
  // ist das dieselbe Zahl wie die des Anbieters.
  const chg = punkte.length > 1 ? veraenderung(punkte) : coin?.aenderung24h ?? null;
  const hoch = richtung(chg);
  const gemerkt = istFavorit(fav, id);

  const zeitText = (ms: number) => new Date(ms).toLocaleString(t.code, {
    day: '2-digit', month: 'short',
    ...(zeitraum === '1h' || zeitraum === '24h' ? { hour: '2-digit', minute: '2-digit' } : {}),
  });

  return (
    <div className="seite cd">
      <KopfZurueck titel={coin?.name ?? ''} zurueck={() => nav.zurueck()} rechts={
        <button type="button" className={`klein leer sinkt ${gemerkt ? 'an' : ''}`}
                aria-pressed={gemerkt} aria-label={gemerkt ? t.markt.vergessen : t.markt.merken}
                onClick={() => umschalten(id)}>
          <S n={gemerkt ? 'haken' : 'plus'} g={20} w={1.9} />
        </button>
      } />

      {!coin && laedt && <p className="leerstand">{t.markt.laedt}</p>}
      {!coin && fehler && <p className="leerstand">{t.markt.offline}</p>}

      {coin && (
        <>
          <div className="cd-kopf">
            {coin.bild && <img src={coin.bild} alt="" width={44} height={44} decoding="async" />}
            <div>
              <span className="cd-symbol">{coin.symbol}{coin.rang !== null && <i className="mk-rang">{coin.rang}</i>}</span>
              <b className="cd-preis">{kurs(coin.preis, waehrung, t.code) ?? t.markt.unbekannt}</b>
            </div>
          </div>

          <div className="cd-chg">
            {prozent(chg, t.code) && (
              <span className={hoch === null ? '' : hoch ? 'auf' : 'ab'}>{prozent(chg, t.code)}</span>
            )}
            <small>{zeitraumName(zeitraum, t)}</small>
          </div>

          {punkte.length > 1 ? (
            <LinienChart punkte={punkte} hoch={hoch} hoehe={210}
                         preisText={w => kurs(w, waehrung, t.code) ?? ''}
                         zeitText={zeitText}
                         beschriftung={`${coin.name} · ${zeitraumName(zeitraum, t)}`} />
          ) : (
            <div className="chart-leer" style={{ height: 210 }}>
              <span>{v.laedt ? t.markt.laedt : t.markt.verlaufLeer}</span>
            </div>
          )}

          <div className="cd-zeit" role="tablist" aria-label={t.markt.titel}>
            {ZEITRAEUME.map(z => (
              <button key={z} type="button" role="tab" className="chip sinkt"
                      aria-pressed={zeitraum === z} aria-selected={zeitraum === z}
                      onClick={() => setZeitraum(z)}>{zeitraumName(z, t)}</button>
            ))}
          </div>

          <div className="cd-gitter">
            <Kennzahl lab={t.markt.kapital} wert={kompakt(coin.marktkapital, waehrung, t.code)} />
            <Kennzahl lab={t.markt.volumen} wert={kompakt(coin.volumen24h, waehrung, t.code)} />
            <Kennzahl lab={t.markt.umlauf} wert={kompaktZahl(coin.umlauf, t.code)} />
            <Kennzahl lab={t.markt.hoechstmenge} wert={kompaktZahl(coin.hoechstmenge, t.code)} />
            <Kennzahl lab={t.markt.ath} wert={kurs(coin.ath, waehrung, t.code)} />
          </div>

          {coin.beschreibung && (
            <section className="cd-text">
              <h2>{t.markt.ueber}</h2>
              {/* CoinGecko fuehrt praktisch nur englische Texte. Das wird
                  gesagt, statt eine Uebersetzung vorzutaeuschen. */}
              {coin.beschreibungSprache === 'en' && t.code.startsWith('de') && (
                <p className="cd-sprache">{t.markt.nurEnglisch}</p>
              )}
              <p>{coin.beschreibung}</p>
            </section>
          )}

          {coin.seite && (
            <button type="button" className="knopf zweit sinkt cd-link"
                    onClick={() => imBrowserOeffnen(coin.seite!)}>
              <S n="extern" g={18} w={1.75} />{t.markt.projektseite}
            </button>
          )}

          <p className="mk-quelle">
            {t.markt.quelle('CoinGecko')}
            {d?.veraltet && ' · ' + t.markt.standVon(alter(d.standVon, t.code))}
          </p>
        </>
      )}
    </div>
  );
}
