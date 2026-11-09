import { useState } from 'react';
import { useT } from '../i18n/index.ts';
import { useEinstellungen } from '../wallet/einstellungen.ts';
import { useNav } from '../zustand.tsx';
import { usePortfolio, positionHinzufuegen, positionEntfernen, positionAendern } from '../portfolio/lager.ts';
import { manuellAnlegen, PositionAbgelehnt, mengeAusText } from '../portfolio/modell.ts';
import { useSuche } from '../markt/haken.ts';
import { kurs as kursText } from '../markt/format.ts';
import type { CoinKurz } from '../markt/typen.ts';
import { Tippfeld } from '../ui/tastatur.tsx';
import { tippeText } from '../wallet/tasten.ts';
import { S, KopfZurueck, Kasten } from '../ui/teile.tsx';

/**
 * Position anlegen oder aendern.
 *
 * Die Kryptowaehrung wird GESUCHT, nicht getippt: Nur so steht hinter der
 * Position eine Kennung, zu der es auch einen Kurs gibt. Ein frei
 * eingetippter Name waere eine Position, die nie bewertet werden kann.
 *
 * YSR fehlt in der Auswahl mit Absicht -- der echte Bestand kommt von der
 * Kette. Wer es trotzdem versucht, bekommt den Grund zu lesen.
 */
export function PositionBearbeiten({ id }: { id?: string }) {
  const t = useT();
  const nav = useNav();
  const { waehrung } = useEinstellungen();
  const { positionen } = usePortfolio();
  const vorhanden = id ? positionen.find(p => p.id === id) ?? null : null;

  const [coin, setCoin] = useState<CoinKurz | null>(
    vorhanden ? { id: vorhanden.coinId, symbol: vorhanden.symbol, name: vorhanden.name,
                  bild: null, rang: null, preis: null, aenderung24h: null,
                  marktkapital: null, volumen24h: null, spark: null } : null);
  const [suchtext, setSuchtext] = useState('');
  const [menge, setMenge] = useState(vorhanden?.menge ?? '');
  const [einstand, setEinstand] = useState(vorhanden?.einstandJeStueck ?? '');
  const [fehler, setFehler] = useState<string | null>(null);

  const treffer = useSuche(suchtext);
  const mengeOk = mengeAusText(menge) !== null && mengeAusText(menge) !== 0n;

  const speichern = () => {
    if (!coin) return;
    try {
      const neu = manuellAnlegen({
        coinId: coin.id, symbol: coin.symbol, name: coin.name,
        menge, einstandJeStueck: einstand,
      });
      if (vorhanden) {
        positionAendern(vorhanden.id, { coinId: neu.coinId, symbol: neu.symbol, name: neu.name,
                                        menge: neu.menge, einstandJeStueck: neu.einstandJeStueck });
      } else {
        positionHinzufuegen(neu);
      }
      nav.zurueck();
    } catch (e) {
      if (e instanceof PositionAbgelehnt) {
        setFehler(e.grund === 'gesperrt' ? t.pf.fehlerGesperrt
          : e.grund === 'menge' ? t.pf.fehlerMenge : t.pf.fehlerEinstand);
      } else {
        setFehler(String(e));
      }
    }
  };

  return (
    <div className="seite pb">
      <KopfZurueck titel={vorhanden ? t.pf.bearbeiten : t.pf.neu} zurueck={() => nav.zurueck()} />

      {fehler && <div className="pb-feld"><Kasten art="warn" symbol="warnung">{fehler}</Kasten></div>}

      {/* Schritt 1: welche Kryptowaehrung */}
      {!coin ? (
        <>
          <div className="suche pb-suche">
            <S n="lupe" g={18} w={1.75} />
            <Tippfeld id="psuche" art="text" label={t.pf.coinWaehlen} platz={t.pf.coinWaehlen}
                      wert={suchtext} onTaste={z => setSuchtext(x => tippeText(x, z, 40))} />
          </div>
          <div className="pb-treffer">
            {(treffer.daten?.wert ?? []).map(c => (
              <button key={c.id} type="button" className="pb-treffer-zeile sinkt" onClick={() => setCoin(c)}>
                {c.bild
                  ? <img src={c.bild} alt="" width={28} height={28} loading="lazy" />
                  : <i className="mk-platz" aria-hidden="true">{c.symbol.slice(0, 2)}</i>}
                <span><b>{c.name}</b><small>{c.symbol}</small></span>
              </button>
            ))}
            {treffer.laedt && <p className="leerstand">{t.markt.laedt}</p>}
          </div>
        </>
      ) : (
        <>
          <button type="button" className="pb-gewaehlt sinkt" onClick={() => { setCoin(null); setFehler(null); }}>
            <span><b>{coin.name}</b><small>{coin.symbol}</small></span>
            <S n="tausch" g={18} w={1.75} />
          </button>

          <div className="pb-feld">
            <label className="nk-lab" htmlFor="pmenge">{t.pf.mengeLabel}</label>
            <Tippfeld id="pmenge" art="ziffern" label={t.pf.mengeLabel} platz="0" wert={menge}
                      onTaste={z => setMenge(x => tippeText(x, z, 20))} />
          </div>

          <div className="pb-feld">
            <label className="nk-lab" htmlFor="peinstand">
              {t.pf.einstandLabel} ({waehrung.toUpperCase()}) <i className="pb-opt">{t.allg.optional}</i>
            </label>
            <Tippfeld id="peinstand" art="ziffern" label={t.pf.einstandLabel} platz="0" wert={einstand}
                      onTaste={z => setEinstand(x => tippeText(x, z, 20))} />
            <p className="pb-hilfe">{t.pf.einstandHinweis}</p>
            {coin.preis !== null && (
              <p className="pb-hilfe">{t.markt.kapital === '' ? '' : `${kursText(coin.preis, waehrung, t.code)} · ${t.pf.jeStueck}`}</p>
            )}
          </div>

          <button type="button" className="knopf sinkt pb-los" disabled={!mengeOk} onClick={speichern}>
            {t.pf.speichern}
          </button>

          {vorhanden && (
            <button type="button" className="knopf gefahr zweit sinkt pb-weg"
                    onClick={() => { positionEntfernen(vorhanden.id); nav.zurueck(); }}>
              {t.pf.entfernen}
            </button>
          )}
        </>
      )}
    </div>
  );
}
