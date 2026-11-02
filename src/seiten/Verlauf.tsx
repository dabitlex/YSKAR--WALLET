import { useEffect, useMemo, useRef, useState } from 'react';
import { useT } from '../i18n/index.ts';
import { useDaten, useNav, useWallet } from '../zustand.tsx';
import { verlaufLaden } from '../netz/api.ts';
import { ausEintrag, istMining, nachTagen, tagessumme, type Zahlung } from '../wallet/zahlungen.ts';
import { useKontakte } from '../wallet/kontakte.ts';
import { isValidAddress } from '../kern/address.ts';
import { fehlerMerken } from '../geraet/plattform.ts';
import { useZahlungen } from '../ui/daten.ts';
import { tippeText } from '../wallet/tasten.ts';
import { S, Kasten, Platzhalterzeilen, Zahlungszeile, Stellen, useAnzeige, useZeit } from '../ui/teile.tsx';
import { Tippfeld, useTastatur } from '../ui/tastatur.tsx';

type Filter = 'alle' | 'ein' | 'aus' | 'mining';

/** Mining: so viele Eintraege sollen nach einem Tipp auf "Mehr laden" mindestens dazukommen. */
const MINING_ZIEL = 12;
/** ... aber hoechstens so viele Seiten je Tipp, damit ein Konto ohne Mining nicht endlos laedt. */
const MINING_SEITEN = 5;

/**
 * Die ganze Aktivitaet, seitenweise (Designsystem 4.2).
 *
 * Eingang und Ausgang filtert der Spiegel. Den Filter "Mining" gibt es dort
 * nicht: Er laeuft hier, auf den Seiten des ungefilterten Verlaufs, und
 * zeigt nur `reward` (Blockbelohnung) und `pool` (Pool-Anteil). Weil eine
 * Seite auch ganz ohne Mining sein kann, laedt "Mehr laden" so lange weiter,
 * bis genug dazukommt oder der Verlauf zu Ende ist -- es entsteht keine Luecke.
 *
 * Wer nach einem Namen sucht, sucht nach der Adresse dieses Kontakts -- der Server kennt keine Namen.
 */
export function Verlauf() {
  const t = useT();
  const nav = useNav();
  const zeit = useZeit();
  const anzeige = useAnzeige();
  const tast = useTastatur();
  const { adresse } = useWallet();
  const { konto, offline } = useDaten();
  const { wartend, verlauf } = useZahlungen();
  const { kontakte } = useKontakte();
  const [filter, setFilter] = useState<Filter>('alle');
  const [suchOffen, setSuchOffen] = useState(false);
  const [suche, setSuche] = useState('');
  const [q, setQ] = useState('');
  const [erste, setErste] = useState<{ liste: Zahlung[]; weiter: string | null } | null>(null);
  const [mehr, setMehr] = useState<Zahlung[]>([]);
  const [weiter, setWeiter] = useState<string | null | undefined>(undefined);
  const [laedt, setLaedt] = useState(false);
  const [fehler, setFehler] = useState(false);
  const lauf = useRef(0);

  // Tippen abwarten, dann suchen.
  useEffect(() => { const u = setTimeout(() => setQ(suche.trim()), 350); return () => clearTimeout(u); }, [suche]);

  const frage = useMemo(() => {
    if (!q) return '';
    if (isValidAddress(q.toLowerCase())) return q.toLowerCase();
    const treffer = Object.entries(kontakte).find(([, name]) => name.toLowerCase().includes(q.toLowerCase()));
    return treffer ? treffer[0] : q;
  }, [q, kontakte]);

  // Was der Spiegel filtert: Eingang und Ausgang. Mining fragt den ungefilterten Verlauf.
  const richtung = filter === 'ein' || filter === 'aus' ? filter : 'alle';
  const passt = (z: Zahlung) => filter !== 'mining' || istMining(z);
  // Ohne Spiegel-Filter und ohne Suche ist die erste Seite die des Kontos -- sie bleibt aktuell.
  const vomKonto = richtung === 'alle' && frage === '';

  useEffect(() => {
    setMehr([]); setWeiter(undefined); setFehler(false);
    if (vomKonto || !adresse) { setErste(null); return; }
    const nr = ++lauf.current;
    setLaedt(true);
    verlaufLaden(adresse, { richtung, q: frage })
      .then(s => { if (nr === lauf.current) setErste({ liste: s.eintraege.map(ausEintrag), weiter: s.weiter }); })
      .catch(e => { fehlerMerken('verlauf', e); if (nr === lauf.current) { setErste({ liste: [], weiter: null }); setFehler(true); } })
      .finally(() => { if (nr === lauf.current) setLaedt(false); });
  }, [vomKonto, richtung, frage, adresse]);

  const basis = vomKonto ? verlauf : (erste?.liste ?? []);
  const cursor = weiter !== undefined ? weiter : vomKonto ? (konto?.historyWeiter ?? null) : (erste?.weiter ?? null);
  const bekannt = new Set(basis.map(z => z.txid + z.art));
  const alle = [...basis, ...mehr.filter(z => !bekannt.has(z.txid + z.art))];
  const bestaetigt = alle.filter(passt);
  const offene = filter === 'mining' ? [] : wartend.filter(z => (filter === 'alle' || (filter === 'ein') === (z.art === 'ein'))
    && (!frage || z.gegen === frage));
  const tage = nachTagen(bestaetigt);

  const nachladen = async () => {
    if (!adresse || !cursor || laedt) return;
    const nr = lauf.current;
    setLaedt(true);
    try {
      let vor: string | null = cursor;
      let neu: Zahlung[] = [];
      // Ohne Mining-Filter genuegt eine Seite. Mit Filter weiter, bis genug passt oder der Verlauf endet.
      for (let i = 0; vor && i < (filter === 'mining' ? MINING_SEITEN : 1); i++) {
        const s = await verlaufLaden(adresse, { richtung, q: frage, vor });
        neu = [...neu, ...s.eintraege.map(ausEintrag)];
        vor = s.weiter;
        if (filter !== 'mining' || neu.filter(istMining).length >= MINING_ZIEL) break;
      }
      if (nr !== lauf.current) return;
      setMehr(m => [...m, ...neu]);
      setWeiter(vor);
    } catch (e) { fehlerMerken('verlauf weiter', e); setFehler(true); }
    finally { setLaedt(false); }
  };

  // Mining: Steht auf der ersten Seite wenig davon, gleich weiterladen statt eine fast leere Liste zu zeigen.
  const angestossen = useRef('');
  useEffect(() => {
    const schluessel = `${filter}|${frage}`;
    if (filter !== 'mining' || laedt || !cursor || bestaetigt.length >= MINING_ZIEL || angestossen.current === schluessel) return;
    angestossen.current = schluessel;
    nachladen();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [filter, frage, laedt, cursor, bestaetigt.length]);
  useEffect(() => { if (filter !== 'mining') angestossen.current = ''; }, [filter]);

  const teil = (f: Filter, name: string) =>
    <button type="button" aria-pressed={filter === f} onClick={() => setFilter(f)}>{name}</button>;
  const summe = (liste: Zahlung[]) => {
    const s = tagessumme(liste);
    return <span className="sf-klein ziffern">{s < 0n ? '−' : '+'}<Stellen text={anzeige(s < 0n ? -s : s)} /></span>;
  };

  return (
    <div className="seite mit-nav st">
      <div className="kopf">
        <h1>{t.verlauf.titel}</h1>
        <button type="button" className="klein sinkt" aria-pressed={suchOffen} aria-label={suchOffen ? t.verlauf.sucheZu : t.verlauf.suchen}
                onClick={() => { if (suchOffen) { setSuche(''); tast.oeffne(null); } setSuchOffen(!suchOffen); }}>
          <S n={suchOffen ? 'kreuz' : 'lupe'} g={19} w={1.6} />
        </button>
      </div>
      {suchOffen && (
        <div className="suche">
          <S n="lupe" />
          <Tippfeld id="suche" art="text" label={t.verlauf.sucheLabel} platz={t.verlauf.suche} wert={suche} auto
                    onTaste={z => setSuche(x => tippeText(x, z))} einfuegen={x => setSuche(x.replace(/\s+/g, ' ').trim().slice(0, 64))}
                    leiste={suche !== '' && <button type="button" className="tknopf" onClick={() => setSuche('')}>{t.tast.leeren}</button>} />
        </div>
      )}
      <div className="segment vier" role="group" aria-label={t.verlauf.filter}>
        {teil('alle', t.verlauf.alle)}{teil('ein', t.verlauf.eingang)}{teil('aus', t.verlauf.ausgang)}{teil('mining', t.verlauf.mining)}
      </div>

      {offline && <div className="st-meldung" role="status"><Kasten art="warn" symbol="warnung"><b>{t.start.offlineTitel}</b> <span className="leise">{t.start.offlineText}</span></Kasten></div>}
      {konto?.pendingSource === 'unavailable' && filter !== 'mining' && <p className="sf-xs st-hinweis">{t.verlauf.mempoolUnbekannt}</p>}

      {offene.length > 0 && (
        <section>
          <div className="sf-abschnitt st-abschnitt"><span className="sf-lab bern">{t.verlauf.ausstehend}</span></div>
          <div className="liste">{offene.map(z => <Zahlungszeile key={z.txid} z={z} mitUhr onTipp={() => nav.gehe({ n: 'detail', zahlung: z })} />)}</div>
        </section>
      )}

      {tage.map(tag => (
        <section key={tag.schluessel}>
          <div className="sf-abschnitt st-abschnitt">
            <span className="sf-lab">{tag.zeit === null ? '' : zeit.tag(tag.zeit)}</span>
            {summe(tag.zahlungen)}
          </div>
          <div className="liste">{tag.zahlungen.map(z => <Zahlungszeile key={z.txid + z.art} z={z} mitUhr onTipp={() => nav.gehe({ n: 'detail', zahlung: z })} />)}</div>
        </section>
      ))}

      {/* Erste Seite des Kontos noch nicht da: laden, nicht "keine Zahlungen". */}
      {vomKonto && !konto
        ? (offline ? <div className="leerstand">{t.verlauf.fehler}</div> : <div className="st-laden"><Platzhalterzeilen n={5} /></div>)
        : bestaetigt.length === 0 && offene.length === 0 && !laedt &&
          <div className="leerstand">{fehler ? t.verlauf.fehler : filter !== 'alle' || frage ? t.verlauf.nichts : t.verlauf.leer}</div>}
      {laedt && (bestaetigt.length === 0 ? <div className="st-laden"><Platzhalterzeilen n={5} /></div> : <div className="leerstand"><div className="dreher" style={{ margin: '0 auto' }} /></div>)}
      {cursor && !laedt && <button type="button" className="knopf zweit sinkt st-mehr" onClick={nachladen}>{t.verlauf.mehr}</button>}
    </div>
  );
}
