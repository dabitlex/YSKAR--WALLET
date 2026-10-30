import { useEffect, useMemo, useRef, useState } from 'react';
import { useT } from '../i18n/index.ts';
import { useDaten, useNav, useWallet } from '../zustand.tsx';
import { isValidAddress } from '../kern/address.ts';
import { verlaufLaden } from '../netz/api.ts';
import { kontaktSetzen, useKontakte, NAME_MAX } from '../wallet/kontakte.ts';
import { anforderungEntfernen, useAnforderungen } from '../wallet/anforderungen.ts';
import { ausEintrag, type Zahlung } from '../wallet/zahlungen.ts';
import { betragGenau } from '../wallet/betrag.ts';
import { kurzAdresse, vierer } from '../wallet/adresse.ts';
import { tippeAdresse, tippeText } from '../wallet/tasten.ts';
import { adresseAusCode } from '../wallet/qr.ts';
import { einfuegen } from '../geraet/geraet.ts';
import { fehlerMerken } from '../geraet/plattform.ts';
import { useZahlungen } from '../ui/daten.ts';
import { S, Kopf, KopfZurueck, Bild, Kasten, Blatt, useMeldung, useZeit, Stellen, useAnzeige } from '../ui/teile.tsx';
import { Scanner } from '../ui/scanner.tsx';
import { Tippfeld, useTastatur } from '../ui/tastatur.tsx';

/* ------------------------------------------------------------------ Liste */

export function Kontakte() {
  const t = useT();
  const anzeige = useAnzeige();
  const nav = useNav();
  const zeit = useZeit();
  const { kontakte } = useKontakte();
  const { wartend, verlauf } = useZahlungen();
  const [suche, setSuche] = useState('');

  const alle = Object.entries(kontakte).sort((a, b) => a[1].localeCompare(b[1], t.code));
  const s = suche.trim().toLowerCase();
  const liste = s ? alle.filter(([a, n]) => n.toLowerCase().includes(s) || a.includes(s)) : alle;

  // Die juengste Zahlung je Kontakt, soweit sie auf der ersten Seite des Verlaufs steht.
  const zuletzt = useMemo(() => {
    const m = new Map<string, Zahlung>();
    for (const z of [...wartend, ...verlauf]) if (z.gegen && !m.has(z.gegen)) m.set(z.gegen, z);
    return m;
  }, [wartend, verlauf]);

  return (
    <div className="seite mit-nav eng">
      <Kopf titel={t.kontakte.titel} rechts={
        <button type="button" className="klein sinkt" aria-label={t.kontakte.neu} onClick={() => nav.gehe({ n: 'kontaktBearbeiten' })}><S n="plus" /></button>} />
      {alle.length > 0 && (
        <div className="suche">
          <S n="lupe" />
          <Tippfeld id="ksuche" art="text" label={t.kontakte.sucheLabel} platz={t.kontakte.suche} wert={suche}
                    onTaste={z => setSuche(x => tippeText(x, z))}
                    leiste={suche !== '' && <button type="button" className="tknopf" onClick={() => setSuche('')}>{t.tast.leeren}</button>} />
        </div>
      )}
      {alle.length > 0 && <div className="rubrik">{t.kontakte.anzahl(alle.length)}</div>}
      {liste.length > 0
        ? <div className="karte">
            {liste.map(([a, n]) => {
              const z = zuletzt.get(a);
              return (
                <button key={a} type="button" className="zeile" onClick={() => nav.gehe({ n: 'kontakt', adresse: a })}>
                  <Bild name={n} />
                  <div className="mitte">
                    <b>{n}</b>
                    <small>{z ? `${z.art === 'aus' ? '−' : '+'}${anzeige(z.betrag)} YSR · ${z.zeit ? zeit.kurz(z.zeit) : t.zahlung.wartet}` : kurzAdresse(a, '')}</small>
                  </div>
                  <S n="weiter" g={18} w={2.2} klasse="pfeil" />
                </button>
              );
            })}
          </div>
        : <div className="leerstand">{alle.length === 0 ? t.kontakte.leer : t.kontakte.nichts}</div>}
      <div className="hinweis"><S n="info" g={18} /><div>{t.kontakte.hinweis}</div></div>
    </div>
  );
}

/* ----------------------------------------------- hinzufuegen und bearbeiten */

export function KontaktBearbeiten({ adresse: vor }: { adresse?: string }) {
  const t = useT();
  const nav = useNav();
  const wallet = useWallet();
  const melde = useMeldung();
  const tast = useTastatur();
  const { kontakte, name: nameVon } = useKontakte();
  const bestehend = vor ? nameVon(vor) : null;
  const [name, setName] = useState(bestehend ?? '');
  const [ziel, setZiel] = useState(vor ?? '');
  const [scannt, setScannt] = useState(false);
  const [fragt, setFragt] = useState(false);

  const a = ziel.trim().toLowerCase();
  const gueltig = isValidAddress(a);
  const eigene = gueltig && a === wallet.adresse;
  const andere = gueltig && a !== vor ? kontakte[a] : undefined;
  const bereit = gueltig && !eigene && !andere && name.trim().length > 0;
  const uebernehmen = (text: string): boolean => { const x = adresseAusCode(text); if (!x) return false; setZiel(x); return true; };

  const speichern = () => {
    // Die Adresse eines bestehenden Kontakts laesst sich aendern: alten Eintrag entfernen.
    if (bestehend && vor && vor !== a) kontaktSetzen(vor, '');
    kontaktSetzen(a, name);
    nav.zurueck();
  };

  return (
    <div className="seite">
      <KopfZurueck titel={bestehend ? t.kontakte.bearbeiten : t.kontakte.neu} zurueck={nav.zurueck} />
      <div className="feldgruppe">
        <span className="feldname">{t.kontakte.name}</span>
        <Tippfeld id="kname" art="text" klasse="feld" gross="woerter" label={t.kontakte.name} wert={name} auto={!bestehend}
                  onTaste={z => setName(n => tippeText(n, z, NAME_MAX))}
                  weiter={ziel === '' ? () => tast.oeffne('kadresse') : undefined} />
      </div>
      <div className="feldgruppe">
        <span className="feldname">{t.kontakte.adresse}</span>
        <div className={`feld-huelle ${tast.aktiv === 'kadresse' ? 'aktiv' : ''}`}>
          <Tippfeld id="kadresse" art="adresse" klasse="ziffern" label={t.kontakte.adresse} wert={ziel} platz={t.senden.platz}
                    zeige={vierer(ziel).map((g, i) => <span key={i}>{g}</span>)}
                    onTaste={z => setZiel(x => tippeAdresse(x, z))}
                    leiste={ziel !== '' && <button type="button" className="tknopf" onClick={() => setZiel('')}>{t.tast.leeren}</button>} />
          <button type="button" className="klein im-feld sinkt" aria-label={t.allg.einfuegen}
                  onClick={async () => { if (!uebernehmen(await einfuegen())) melde(t.senden.keinCode); }}><S n="einfuegen" g={19} /></button>
          <button type="button" className="klein im-feld sinkt" aria-label={t.allg.scannen} onClick={() => setScannt(true)}><S n="scan" g={19} /></button>
        </div>
        {a === '' ? null
          : eigene ? <div className="gutzeile rot"><S n="warnung" g={15} />{t.kontakte.eigene}</div>
          : andere ? <div className="gutzeile rot"><S n="warnung" g={15} />{t.kontakte.vergeben(andere)}</div>
          : gueltig ? <div className="gutzeile"><S n="haken" g={15} w={2.6} />{t.senden.gueltig}</div>
          : tast.aktiv === 'kadresse' && a.length < 42 ? <div className="gutzeile leise">{t.senden.zeichen(a.length)}</div>
          : <div className="gutzeile rot"><S n="warnung" g={15} />{t.senden.ungueltig}</div>}
      </div>
      <Kasten art="ruhig" symbol="info">{t.kontakte.hinweisNeu}</Kasten>
      <div className="fuell" />
      <div className="stapel">
        <button type="button" className="knopf sinkt" disabled={!bereit} onClick={speichern}>{t.kontakte.speichern}</button>
        {bestehend && <button type="button" className="knopf text sinkt" style={{ color: 'var(--rot)' }} onClick={() => setFragt(true)}>{t.kontakte.loeschen}</button>}
      </div>
      {scannt && <Scanner onZu={() => setScannt(false)} onText={x => { if (!uebernehmen(x)) return false; setScannt(false); return true; }} />}
      {fragt && vor && (
        <Blatt titel={t.kontakte.loeschenFrage} onZu={() => setFragt(false)}>
          <p>{t.kontakte.loeschenText}</p>
          <div className="stapel">
            <button type="button" className="knopf gefahr sinkt" onClick={() => { kontaktSetzen(vor, ''); nav.reiter('kontakte'); }}>{t.kontakte.loeschen}</button>
            <button type="button" className="knopf zweit sinkt" onClick={() => setFragt(false)}>{t.allg.abbrechen}</button>
          </div>
        </Blatt>
      )}
    </div>
  );
}

/* --------------------------------------- alle Zahlungen mit einem Kontakt */

type Stueck = { art: 'zahlung'; z: Zahlung; zeit: number } | { art: 'anforderung'; id: string; betrag: bigint; notiz: string; zeit: number; bezahlt: boolean };

export function Kontakt({ adresse }: { adresse: string }) {
  const t = useT();
  const anzeige = useAnzeige();
  const nav = useNav();
  const zeit = useZeit();
  const wallet = useWallet();
  const { konto } = useDaten();
  const { name } = useKontakte();
  const { wartend } = useZahlungen();
  const anforderungen = useAnforderungen();
  const [liste, setListe] = useState<Zahlung[] | null>(null);
  const [fehler, setFehler] = useState(false);
  const [gewaehlt, setGewaehlt] = useState<string | null>(null);
  const ende = useRef<HTMLDivElement>(null);
  const wer = name(adresse) ?? kurzAdresse(adresse, '');

  // Neu laden, sobald im Konto eine neue Zahlung oben steht.
  const oben = konto?.history[0]?.txid ?? '';
  useEffect(() => {
    if (!wallet.adresse) return;
    let lebt = true;
    verlaufLaden(wallet.adresse, { q: adresse, limit: 100 })
      .then(s => { if (lebt) { setListe(s.eintraege.map(ausEintrag).filter(z => z.gegen === adresse)); setFehler(false); } })
      .catch(e => { fehlerMerken('kontakt', e); if (lebt) { setListe(l => l ?? []); setFehler(true); } });
    return () => { lebt = false; };
  }, [wallet.adresse, adresse, oben]);

  const stuecke = useMemo(() => {
    const zahlungen = [...(liste ?? [])];
    const jetzt = Math.floor(Date.now() / 1000);
    const offen = wartend.filter(z => z.gegen === adresse && !zahlungen.some(x => x.txid === z.txid));
    const alle: Stueck[] = [
      ...zahlungen.map(z => ({ art: 'zahlung' as const, z, zeit: z.zeit ?? 0 })),
      ...offen.map(z => ({ art: 'zahlung' as const, z, zeit: z.zeit ?? jetzt })),
      ...anforderungen.filter(a => a.von === adresse).map(a => {
        const betrag = BigInt(a.betrag);
        return { art: 'anforderung' as const, id: a.id, betrag, notiz: a.notiz, zeit: a.zeit,
                 bezahlt: zahlungen.some(z => z.art === 'ein' && (z.zeit ?? 0) >= a.zeit && z.betrag >= betrag) };
      }),
    ];
    return alle.sort((x, y) => x.zeit - y.zeit);
  }, [liste, wartend, anforderungen, adresse]);

  useEffect(() => { ende.current?.scrollIntoView({ block: 'end' }); }, [stuecke.length]);

  let letzterTag = '';
  return (
    <div className="seite" style={{ padding: 0, gap: 0, overflow: 'hidden' }}>
      <div className="kontaktkopf">
        <button type="button" className="klein sinkt" aria-label={t.allg.zurueck} onClick={nav.zurueck}><S n="zurueck" w={2.2} /></button>
        <Bild name={wer} g={44} />
        <div style={{ flex: 1, minWidth: 0 }}>
          <h1>{wer}</h1>
          <small>{kurzAdresse(adresse, '')}</small>
        </div>
        <button type="button" className="klein sinkt" aria-label={t.kontakte.bearbeiten} onClick={() => nav.gehe({ n: 'kontaktBearbeiten', adresse })}><S n="stift" /></button>
      </div>

      <div className="gespraech">
        {liste === null && <div className="leerstand"><div className="dreher" style={{ margin: '0 auto' }} /></div>}
        {liste !== null && stuecke.length === 0 && <div className="leerstand">{fehler ? t.verlauf.fehler : t.kontakte.keineZahlung}</div>}
        {stuecke.map(s => {
          const tag = s.zeit ? zeit.tag(s.zeit) : '';
          const marke = tag && tag !== letzterTag ? <div key={`t${s.zeit}`} className="tagmarke">{tag}</div> : null;
          if (tag) letzterTag = tag;
          if (s.art === 'anforderung') {
            return [marke,
              <button key={s.id} type="button" className="blase frage" onClick={() => setGewaehlt(s.id)}>
                <span className="marke">{t.kontakte.angefordert}</span>
                <span className="summe"><Stellen text={betragGenau(s.betrag, t.code, 8, 2)} /> <small>YSR</small></span>
                {s.notiz && <span className="text">{s.notiz}</span>}
                <span className="zeit">{zeit.uhr(s.zeit)} · {s.bezahlt ? t.kontakte.bezahlt : t.kontakte.offen}</span>
              </button>];
          }
          const z = s.z, ein = z.art !== 'aus', wartet = z.hoehe === null;
          return [marke,
            <button key={z.txid} type="button" className={`blase ${ein ? 'l' : 'r'}`} onClick={() => nav.gehe({ n: 'detail', zahlung: z })}>
              <span className="summe">{ein ? '+' : '−'}<Stellen text={anzeige(z.betrag)} /> <small>YSR</small></span>
              {z.notiz && <span className="text">{z.notiz}</span>}
              <span className={`zeit ${wartet ? 'wartet' : ''}`}>{z.zeit ? `${zeit.uhr(z.zeit)} · ` : ''}{wartet ? t.zahlung.wartet : t.zahlung.bestaetigt}</span>
            </button>];
        })}
        <div ref={ende} />
      </div>

      <div className="fussleiste">
        <button type="button" className="knopf sinkt" style={{ fontSize: 16 }} onClick={() => nav.gehe({ n: 'senden', an: adresse })}><S n="hoch" w={2.2} /><span>{t.kontakte.senden}</span></button>
        <button type="button" className="knopf zweit sinkt" onClick={() => nav.gehe({ n: 'empfangen', fuer: adresse })}><S n="runter" w={2.2} /><span>{t.kontakte.anfordern}</span></button>
      </div>

      {gewaehlt && (
        <Blatt titel={t.kontakte.angefordert} onZu={() => setGewaehlt(null)}>
          <div className="stapel">
            <button type="button" className="knopf gefahr sinkt" onClick={() => { anforderungEntfernen(gewaehlt); setGewaehlt(null); }}>{t.kontakte.anforderungEntfernen}</button>
            <button type="button" className="knopf zweit sinkt" onClick={() => setGewaehlt(null)}>{t.allg.schliessen}</button>
          </div>
        </Blatt>
      )}
    </div>
  );
}
