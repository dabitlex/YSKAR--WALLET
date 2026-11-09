import { useMemo, useState } from 'react';
import { useT } from '../i18n/index.ts';
import { useEinstellungen } from '../wallet/einstellungen.ts';
import { useNav, useDaten } from '../zustand.tsx';
import { useListe, useSuche, useUeberblick } from '../markt/haken.ts';
import { kompakt, kurs, prozent, richtung, alter } from '../markt/format.ts';
import { useFavoriten, umschalten, istFavorit } from '../watchlist/favoriten.ts';
import type { CoinKurz, Sortierung } from '../markt/typen.ts';
import { MiniChart } from '../diagramm/mini.tsx';
import { S, Kopf } from '../ui/teile.tsx';
import { Tippfeld } from '../ui/tastatur.tsx';
import { tippeText } from '../wallet/tasten.ts';
import { YMarke } from '../ui/zeichen.tsx';

/**
 * Markets -- der Blick auf den gesamten Kryptomarkt.
 *
 * YSKAR steht an erster Stelle, aber OHNE KURS: YSR wird an keiner Boerse
 * gehandelt. Statt einer erfundenen Zahl steht dort der Grund und ein Weg
 * zu den echten Netzdaten.
 *
 * Faellt der Marktdatendienst aus, bleibt der letzte bekannte Stand mit
 * seinem Alter stehen. Die Wallet ist davon nie betroffen -- das steht so
 * auch in der Meldung, damit niemand um sein Guthaben fuerchtet.
 */

const FILTER: Sortierung[] = ['rang', 'gewinner', 'verlierer', 'marktkapital', 'volumen', 'favoriten'];

function sortieren(liste: CoinKurz[], wie: Sortierung, favIds: string[]): CoinKurz[] {
  const mit = (f: (c: CoinKurz) => number | null) =>
    [...liste].sort((a, b) => {
      const x = f(a), y = f(b);
      if (x === null && y === null) return 0;
      if (x === null) return 1;   // Ohne Wert immer ans Ende, nie als 0 mitsortiert.
      if (y === null) return -1;
      return y - x;
    });
  switch (wie) {
    case 'gewinner': return mit(c => c.aenderung24h);
    case 'verlierer': return mit(c => (c.aenderung24h === null ? null : -c.aenderung24h));
    case 'marktkapital': return mit(c => c.marktkapital);
    case 'volumen': return mit(c => c.volumen24h);
    case 'favoriten': {
      const drin = liste.filter(c => favIds.includes(c.id));
      return drin.sort((a, b) => favIds.indexOf(a.id) - favIds.indexOf(b.id));
    }
    default: return mit(c => (c.rang === null ? null : -c.rang));
  }
}

function Zeile({ c, onTipp }: { c: CoinKurz; onTipp(): void }) {
  const t = useT();
  const { waehrung } = useEinstellungen();
  const fav = useFavoriten();
  const hoch = richtung(c.aenderung24h);
  const p = prozent(c.aenderung24h, t.code);
  const k = kurs(c.preis, waehrung, t.code);
  const gemerkt = istFavorit(fav, c.id);
  return (
    <div className="mk-zeile">
      <button type="button" className="mk-haupt sinkt" onClick={onTipp}>
        <span className="mk-bild">
          {c.bild
            ? <img src={c.bild} alt="" width={32} height={32} loading="lazy" decoding="async" />
            : <i className="mk-platz" aria-hidden="true">{c.symbol.slice(0, 2)}</i>}
        </span>
        <span className="mk-namen">
          <b>{c.name}</b>
          <small>{c.rang !== null && <i className="mk-rang">{c.rang}</i>}{c.symbol}</small>
        </span>
        {c.spark && c.spark.length > 1 && <MiniChart werte={c.spark} hoch={hoch} />}
        <span className="mk-werte">
          <b>{k ?? '–'}</b>
          {p && <small className={hoch === null ? '' : hoch ? 'auf' : 'ab'}>{p}</small>}
        </span>
      </button>
      <button type="button" className={`mk-stern sinkt ${gemerkt ? 'an' : ''}`}
              aria-pressed={gemerkt} aria-label={gemerkt ? t.markt.vergessen : t.markt.merken}
              onClick={() => umschalten(c.id)}>
        <S n={gemerkt ? 'haken' : 'plus'} g={17} w={2} />
      </button>
    </div>
  );
}

/** YSKAR in der Liste: ganz oben, ohne Kurs, mit Begruendung. */
function YskarZeile({ onTipp }: { onTipp(): void }) {
  const t = useT();
  const { netz } = useDaten();
  return (
    <button type="button" className="mk-zeile mk-ysr sinkt" onClick={onTipp}>
      <span className="mk-bild"><YMarke g={30} /></span>
      <span className="mk-namen">
        <b>YSKAR</b><small>YSR</small>
      </span>
      <span className="mk-werte mk-ysr-werte">
        <b>{netz?.hoehe !== null && netz?.hoehe !== undefined ? `#${netz.hoehe.toLocaleString(t.code)}` : '–'}</b>
        <small>{t.markt.ysrHinweis}</small>
      </span>
    </button>
  );
}

function Ueberblick() {
  const t = useT();
  const { waehrung } = useEinstellungen();
  const { daten } = useUeberblick(waehrung);
  const u = daten?.wert;
  if (!u) return null;
  const kap = kompakt(u.marktkapital, waehrung, t.code);
  const vol = kompakt(u.volumen24h, waehrung, t.code);
  const ch = prozent(u.marktkapitalAenderung24h, t.code);
  const hoch = richtung(u.marktkapitalAenderung24h);
  return (
    <section className="mk-ueberblick">
      <div className="mk-gross">
        <span className="nk-lab">{t.markt.gesamtkapital}</span>
        <b>{kap ?? '–'}</b>
        {ch && <span className={`mk-ch ${hoch === null ? '' : hoch ? 'auf' : 'ab'}`}>{ch}</span>}
      </div>
      <div className="mk-klein">
        {vol && <span><i>{t.markt.volumen24}</i><b>{vol}</b></span>}
        {u.btcDominanz !== null && (
          <span><i>{t.markt.dominanz}</i><b>
            BTC {(u.btcDominanz / 100).toLocaleString(t.code, { style: 'percent', maximumFractionDigits: 1 })}
            {u.ethDominanz !== null && ` · ETH ${(u.ethDominanz / 100).toLocaleString(t.code, { style: 'percent', maximumFractionDigits: 1 })}`}
          </b></span>
        )}
        {u.anzahlCoins !== null && <span><i>{t.markt.anzahlCoins}</i><b>{u.anzahlCoins.toLocaleString(t.code)}</b></span>}
      </div>
    </section>
  );
}

export function Markets() {
  const t = useT();
  const nav = useNav();
  const { waehrung } = useEinstellungen();
  const fav = useFavoriten();
  const [filter, setFilter] = useState<Sortierung>('rang');
  const [suche, setSuche] = useState('');
  const [sucheAuf, setSucheAuf] = useState(false);

  const liste = useListe(waehrung);
  const treffer = useSuche(suche);
  const suchModus = suche.trim().length >= 2;

  const zeigen = useMemo(() => {
    const roh = suchModus ? (treffer.daten?.wert ?? []) : (liste.daten?.wert ?? []);
    return suchModus ? roh : sortieren(roh, filter, fav.ids);
  }, [suchModus, treffer.daten, liste.daten, filter, fav.ids]);

  const z = suchModus ? treffer : liste;
  const stand = z.daten;
  const oeffnen = (id: string) => nav.gehe({ n: 'coin', id });

  return (
    <div className="seite mit-nav mk">
      <div className="haft">
        <Kopf titel={t.markt.titel} rechts={
          <button type="button" className="klein leer sinkt" aria-label={t.markt.suchenKurz}
                  aria-pressed={sucheAuf}
                  onClick={() => { setSucheAuf(a => !a); if (sucheAuf) setSuche(''); }}>
            <S n="lupe" g={21} w={1.75} />
          </button>
        } />
      </div>

      {sucheAuf && (
        <div className="suche mk-suche">
          <S n="lupe" g={18} w={1.75} />
          <Tippfeld id="msuche" art="text" label={t.markt.suchen} platz={t.markt.suchen} wert={suche}
                    onTaste={z => setSuche(x => tippeText(x, z, 40))}
                    leiste={suche !== '' && (
                      <button type="button" className="tknopf" onClick={() => setSuche('')}>{t.tast.leeren}</button>
                    )} />
        </div>
      )}

      {!suchModus && <Ueberblick />}

      {!suchModus && (
        <div className="mk-wege">
          <button type="button" className="mk-weg sinkt" onClick={() => nav.gehe({ n: 'portfolio' })}>
            <S n="kurve" g={18} w={1.75} /><span>{t.pf.titel}</span>
          </button>
          <button type="button" className="mk-weg sinkt" onClick={() => nav.gehe({ n: 'watchlist' })}>
            <S n="haken" g={18} w={1.75} /><span>{t.wl.titel}</span>
            {fav.ids.length > 0 && <i className="mk-weg-zahl">{fav.ids.length}</i>}
          </button>
        </div>
      )}

      {!suchModus && (
        <div className="mk-filter" role="tablist" aria-label={t.markt.titel}>
          {FILTER.map(f => (
            <button key={f} type="button" role="tab" className="chip sinkt"
                    aria-pressed={filter === f} aria-selected={filter === f}
                    onClick={() => setFilter(f)}>
              {f === 'rang' ? t.markt.fRang : f === 'gewinner' ? t.markt.fGewinner
                : f === 'verlierer' ? t.markt.fVerlierer : f === 'marktkapital' ? t.markt.fKapital
                : f === 'volumen' ? t.markt.fVolumen : t.markt.fFavoriten}
            </button>
          ))}
        </div>
      )}

      {/* Der Marktdienst bremst oder ist weg. Beides beruehrt die Wallet nicht,
          und genau das steht da -- sonst fuerchtet jemand um sein Guthaben. */}
      {z.gebremst > 0 && (
        <div className="kasten warn mk-hinweis">
          <S n="uhr" g={18} w={1.75} />
          <span><b>{t.markt.gebremst(z.gebremst)}</b><small>{t.markt.gebremstHilfe}</small></span>
        </div>
      )}
      {z.fehler && z.fehler.art === 'offline' && !stand && (
        <div className="kasten ruhig mk-hinweis"><S n="welt" g={18} w={1.75} /><span>{t.markt.offline}</span></div>
      )}

      <div className="mk-liste">
        {!suchModus && filter !== 'favoriten' && <YskarZeile onTipp={() => nav.reiter('start')} />}
        {zeigen.map(c => <Zeile key={c.id} c={c} onTipp={() => oeffnen(c.id)} />)}

        {zeigen.length === 0 && !z.laedt && (
          <p className="leerstand">
            {suchModus ? t.markt.keineTreffer : filter === 'favoriten' ? t.markt.keineFavoriten : t.markt.laedt}
          </p>
        )}
        {z.laedt && zeigen.length === 0 && <p className="leerstand">{t.markt.laedt}</p>}
      </div>

      {stand && (
        <p className="mk-quelle">
          {t.markt.quelle('CoinGecko')}
          {stand.veraltet && ' · ' + t.markt.standVon(alter(stand.standVon, t.code))}
        </p>
      )}
    </div>
  );
}
