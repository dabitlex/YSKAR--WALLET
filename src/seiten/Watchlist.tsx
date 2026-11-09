import { useMemo } from 'react';
import { useT } from '../i18n/index.ts';
import { useEinstellungen } from '../wallet/einstellungen.ts';
import { useNav } from '../zustand.tsx';
import { useFavoriten, umschalten, verschieben } from '../watchlist/favoriten.ts';
import { useListe } from '../markt/haken.ts';
import { kurs, prozent, richtung } from '../markt/format.ts';
import { MiniChart } from '../diagramm/mini.tsx';
import { S, KopfZurueck } from '../ui/teile.tsx';

/**
 * Watchlist -- was der Nutzer dauerhaft im Blick behalten will.
 *
 * Die REIHENFOLGE gehoert ihm: Sie wird gespeichert und nicht nach Rang
 * oder Alphabet ueberschrieben. Verschoben wird ueber zwei Knoepfe je
 * Zeile statt per Ziehen -- das funktioniert auch mit Screenreader und
 * mit grossen Fingern auf einem kleinen Telefon.
 *
 * Ein Wert, zu dem es gerade keinen Kurs gibt, bleibt in der Liste und
 * zeigt einen Strich. Er verschwindet nicht, nur weil das Netz klemmt.
 */
export function Watchlist() {
  const t = useT();
  const nav = useNav();
  const { waehrung } = useEinstellungen();
  const fav = useFavoriten();
  const liste = useListe(waehrung);

  const zeilen = useMemo(() => {
    const nach = new Map((liste.daten?.wert ?? []).map(c => [c.id, c]));
    return fav.ids.map(id => ({ id, coin: nach.get(id) ?? null }));
  }, [fav.ids, liste.daten]);

  return (
    <div className="seite wl">
      <KopfZurueck titel={t.wl.titel} zurueck={() => nav.zurueck()}
                   mitte={<span className="wl-zahl">{t.wl.anzahl(fav.ids.length)}</span>} />

      {zeilen.length === 0 ? (
        <div className="leerstand pf-leer">
          <b>{t.wl.leer}</b>
          <span>{t.wl.leerText}</span>
          <button type="button" className="knopf sinkt" onClick={() => nav.reiter('markets')}>
            {t.markt.titel}
          </button>
        </div>
      ) : (
        <div className="wl-liste">
          {zeilen.map((z, i) => {
            const c = z.coin;
            const hoch = c ? richtung(c.aenderung24h) : null;
            return (
              <div key={z.id} className="wl-zeile">
                <button type="button" className="wl-haupt sinkt" disabled={!c}
                        onClick={() => c && nav.gehe({ n: 'coin', id: c.id })}>
                  <span className="mk-bild">
                    {c?.bild
                      ? <img src={c.bild} alt="" width={30} height={30} loading="lazy" />
                      : <i className="mk-platz" aria-hidden="true">{(c?.symbol ?? z.id).slice(0, 2).toUpperCase()}</i>}
                  </span>
                  <span className="mk-namen">
                    <b>{c?.name ?? z.id}</b>
                    <small>{c?.symbol ?? ''}</small>
                  </span>
                  {c?.spark && c.spark.length > 1 && <MiniChart werte={c.spark} hoch={hoch} breite={52} hoehe={24} />}
                  <span className="mk-werte">
                    <b>{c ? kurs(c.preis, waehrung, t.code) ?? '–' : '–'}</b>
                    {c && prozent(c.aenderung24h, t.code) && (
                      <small className={hoch === null ? '' : hoch ? 'auf' : 'ab'}>{prozent(c.aenderung24h, t.code)}</small>
                    )}
                  </span>
                </button>
                <span className="wl-knoepfe">
                  <button type="button" className="wl-pfeil sinkt" aria-label={t.wl.hoch}
                          disabled={i === 0} onClick={() => verschieben(i, i - 1)}>
                    <S n="hoch" g={16} w={2} />
                  </button>
                  <button type="button" className="wl-pfeil sinkt" aria-label={t.wl.runter}
                          disabled={i === zeilen.length - 1} onClick={() => verschieben(i, i + 1)}>
                    <S n="runter" g={16} w={2} />
                  </button>
                  <button type="button" className="wl-pfeil sinkt" aria-label={t.wl.entfernen}
                          onClick={() => umschalten(z.id)}>
                    <S n="kreuz" g={16} w={2} />
                  </button>
                </span>
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
}
