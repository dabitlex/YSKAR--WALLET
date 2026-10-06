import { useEffect, useMemo, useRef, useState } from 'react';
import { useT } from '../i18n/index.ts';
import { useDaten, useNav, useWallet } from '../zustand.tsx';
import { verlaufLaden } from '../netz/api.ts';
import { ausEintrag, nachTagen, type Zahlung } from '../wallet/zahlungen.ts';
import { useKontakte } from '../wallet/kontakte.ts';
import { isValidAddress } from '../kern/address.ts';
import { fehlerMerken } from '../geraet/plattform.ts';
import { useZahlungen } from '../ui/daten.ts';
import { S, Kopf, Zahlungszeile, useZeit } from '../ui/teile.tsx';

type Richtung = 'alle' | 'ein' | 'aus';

/**
 * Der ganze Verlauf, seitenweise. Ohne Filter kommt die erste Seite mit dem
 * Konto; Filter und Suche fragen den Spiegel. Wer nach einem Namen sucht,
 * sucht nach der Adresse dieses Kontakts -- der Server kennt keine Namen.
 */
export function Verlauf() {
  const t = useT();
  const nav = useNav();
  const zeit = useZeit();
  const { adresse } = useWallet();
  const { konto } = useDaten();
  const { wartend, verlauf } = useZahlungen();
  const { kontakte } = useKontakte();
  const [richtung, setRichtung] = useState<Richtung>('alle');
  const [suche, setSuche] = useState('');
  const [q, setQ] = useState('');
  const [seiten, setSeiten] = useState<{ liste: Zahlung[]; weiter: string | null } | null>(null);
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

  const gefiltert = richtung !== 'alle' || frage !== '';

  useEffect(() => {
    setMehr([]); setWeiter(undefined); setFehler(false);
    if (!gefiltert || !adresse) { setSeiten(null); return; }
    const nr = ++lauf.current;
    setLaedt(true);
    verlaufLaden(adresse, { richtung, q: frage })
      .then(s => { if (nr === lauf.current) setSeiten({ liste: s.eintraege.map(ausEintrag), weiter: s.weiter }); })
      .catch(e => { fehlerMerken('verlauf', e); if (nr === lauf.current) { setSeiten({ liste: [], weiter: null }); setFehler(true); } })
      .finally(() => { if (nr === lauf.current) setLaedt(false); });
  }, [gefiltert, richtung, frage, adresse]);

  const basis = gefiltert ? (seiten?.liste ?? []) : verlauf;
  const cursor = weiter !== undefined ? weiter : gefiltert ? (seiten?.weiter ?? null) : (konto?.historyWeiter ?? null);
  const bekannt = new Set(basis.map(z => z.txid));
  const bestaetigt = [...basis, ...mehr.filter(z => !bekannt.has(z.txid))];
  const offene = wartend.filter(z => (richtung === 'alle' || (richtung === 'ein') === (z.art === 'ein'))
    && (!frage || z.gegen === frage));
  const tage = nachTagen(bestaetigt);

  const nachladen = async () => {
    if (!adresse || !cursor || laedt) return;
    setLaedt(true);
    try {
      const s = await verlaufLaden(adresse, { richtung, q: frage, vor: cursor });
      setMehr(m => [...m, ...s.eintraege.map(ausEintrag)]);
      setWeiter(s.weiter);
    } catch (e) { fehlerMerken('verlauf weiter', e); setFehler(true); }
    finally { setLaedt(false); }
  };

  const chip = (r: Richtung, name: string) =>
    <button type="button" className="chip sinkt" aria-pressed={richtung === r} onClick={() => setRichtung(r)}>{name}</button>;

  return (
    <div className="seite mit-nav eng">
      <Kopf titel={t.verlauf.titel} />
      <div className="suche">
        <label htmlFor="suche" className="nur-leser">{t.verlauf.sucheLabel}</label>
        <S n="lupe" />
        <input id="suche" type="search" className="vertieft" placeholder={t.verlauf.suche} value={suche} onChange={e => setSuche(e.target.value)}
               autoCapitalize="none" autoCorrect="off" spellCheck={false} enterKeyHint="search" />
      </div>
      <div className="chips">{chip('alle', t.verlauf.alle)}{chip('ein', t.verlauf.eingang)}{chip('aus', t.verlauf.ausgang)}</div>

      {offene.length > 0 && <>
        <div className="rubrik">{t.verlauf.wartend}</div>
        <div className="karte">{offene.map(z => <Zahlungszeile key={z.txid} z={z} onTipp={() => nav.gehe({ n: 'detail', zahlung: z })} />)}</div>
      </>}

      {tage.map(tag => (
        <div key={tag.schluessel} style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
          <div className="rubrik">{tag.zeit === null ? '' : zeit.tag(tag.zeit)}</div>
          <div className="karte">{tag.zahlungen.map(z => <Zahlungszeile key={z.txid + z.art} z={z} mitUhr onTipp={() => nav.gehe({ n: 'detail', zahlung: z })} />)}</div>
        </div>
      ))}

      {bestaetigt.length === 0 && offene.length === 0 && !laedt &&
        <div className="leerstand">{fehler ? t.verlauf.fehler : gefiltert ? t.verlauf.nichts : t.verlauf.leer}</div>}
      {laedt && <div className="leerstand"><div className="dreher" style={{ margin: '0 auto' }} /></div>}
      {cursor && !laedt && <button type="button" className="knopf zweit sinkt" onClick={nachladen}>{t.verlauf.mehr}</button>}
    </div>
  );
}
