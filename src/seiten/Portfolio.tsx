import { useEffect, useMemo } from 'react';
import { useT } from '../i18n/index.ts';
import { useEinstellungen } from '../wallet/einstellungen.ts';
import { useDaten, useNav } from '../zustand.tsx';
import { usePortfolio, verlaufMerken } from '../portfolio/lager.ts';
import { bewerten, tagesVeraenderung, type Kurse } from '../portfolio/rechnen.ts';
import { useListe } from '../markt/haken.ts';
import { kurs as kursText, prozent, richtung } from '../markt/format.ts';
import { betragGenau } from '../wallet/betrag.ts';
import { MiniChart } from '../diagramm/mini.tsx';
import { S, KopfZurueck } from '../ui/teile.tsx';
import { YMarke } from '../ui/zeichen.tsx';

/**
 * Portfolio.
 *
 * Zwei Zahlen stehen NEBENEINANDER und werden nie addiert:
 *   - der YSR-Bestand, in YSR, direkt von der Kette
 *   - der Fiat-Wert der bepreisten Positionen
 *
 * YSR wird an keiner Boerse gehandelt. Eine Gesamtsumme ueber beides
 * waere erfunden, und das steht auch so auf dem Bildschirm.
 *
 * Positionen ohne Kurs zaehlen nicht als 0 mit, sondern werden
 * ausgewiesen -- sonst saehe das Portfolio bei einem Netzausfall
 * ploetzlich aermer aus, als es ist.
 */

/** Cent in einen Betrag der gewaehlten Waehrung. */
function cent(c: number | null, w: 'eur' | 'usd', locale: string): string | null {
  return c === null ? null : kursText(c / 100, w, locale);
}

export function Portfolio() {
  const t = useT();
  const nav = useNav();
  const { waehrung } = useEinstellungen();
  const { konto } = useDaten();
  const { positionen, verlauf } = usePortfolio();
  const liste = useListe(waehrung);

  const kurse: Kurse = useMemo(() => {
    const k: Kurse = {};
    for (const c of liste.daten?.wert ?? []) k[c.id] = c.preis;
    return k;
  }, [liste.daten]);

  const ysr = konto ? BigInt(konto.balance) : null;
  const b = useMemo(() => bewerten(positionen, kurse, ysr), [positionen, kurse, ysr]);

  // Den heutigen Wert festhalten -- aber nur, wenn wirklich etwas bewertet
  // werden konnte. Sonst stuende nach einem Netzausfall eine 0 im Verlauf.
  const merkwert = b.summeCent;
  const merkbar = positionen.length > 0 && b.ohneKurs < positionen.length;
  useEffect(() => {
    if (merkbar) verlaufMerken(merkwert);
  }, [merkwert, merkbar]);

  const heute = tagesVeraenderung(verlauf);
  const heuteHoch = richtung(heute);
  const gvHoch = richtung(b.gvCent);

  return (
    <div className="seite pf">
      <KopfZurueck titel={t.pf.titel} zurueck={() => nav.zurueck()} rechts={
        <button type="button" className="klein leer sinkt" aria-label={t.pf.hinzu}
                onClick={() => nav.gehe({ n: 'positionNeu' })}>
          <S n="plus" g={21} w={1.9} />
        </button>
      } />

      {/* Der echte Bestand von der Kette. Immer zuerst, immer in YSR. */}
      <section className="pf-ysr">
        <span className="pf-lab"><YMarke g={15} />{t.pf.ysrBestand}<i className="pf-tag">{t.pf.aufDerKette}</i></span>
        <b>{ysr === null ? '–' : betragGenau(ysr, t.code, 8, 2)} <small>YSR</small></b>
        <span className="pf-hinweis">{t.pf.ysrGetrennt}</span>
      </section>

      {/* Der Fiat-Teil. Getrennt, mit eigener Ueberschrift. */}
      <section className="pf-wert">
        <span className="nk-lab">{t.pf.wert}</span>
        <b>{cent(b.summeCent, waehrung, t.code) ?? '–'}</b>
        <div className="pf-zeile2">
          {heute !== null
            ? <span className={heuteHoch === null ? '' : heuteHoch ? 'auf' : 'ab'}>
                {(heute > 0 ? '+' : '') + (cent(heute, waehrung, t.code) ?? '')} · {t.pf.heute}
              </span>
            : <span className="pf-start">{t.pf.keinVortag}</span>}
          {b.gvProzent !== null && (
            <span className={gvHoch === null ? '' : gvHoch ? 'auf' : 'ab'}>
              {prozent(b.gvProzent, t.code)} · {t.pf.einstand}
            </span>
          )}
        </div>
        {verlauf.length > 1 && (
          <div className="pf-verlauf">
            <MiniChart werte={verlauf.map(p => p.cent)} hoch={heuteHoch} breite={280} hoehe={44} />
          </div>
        )}
      </section>

      {b.ohneKurs > 0 && (
        <div className="kasten ruhig mk-hinweis"><S n="info" g={18} w={1.75} />
          <span>{t.pf.ohneKurs(b.ohneKurs)}</span></div>
      )}

      {positionen.length === 0 ? (
        <div className="leerstand pf-leer">
          <b>{t.pf.leer}</b>
          <span>{t.pf.leerText}</span>
          <button type="button" className="knopf sinkt" onClick={() => nav.gehe({ n: 'positionNeu' })}>
            {t.pf.hinzu}
          </button>
        </div>
      ) : (
        <div className="pf-liste">
          {b.positionen.map(p => {
            const hoch = richtung(p.gvProzent);
            return (
              <button key={p.position.id} type="button" className="pf-pos sinkt"
                      onClick={() => nav.gehe({ n: 'positionBearbeiten', id: p.position.id })}>
                <span className="pf-pos-kopf">
                  <b>{p.position.name}</b>
                  <i className="pf-tag">{t.pf.vonHand}</i>
                </span>
                <span className="pf-pos-menge">{p.position.menge} {p.position.symbol}</span>
                <span className="pf-pos-werte">
                  <b>{cent(p.wertCent, waehrung, t.code) ?? '–'}</b>
                  {p.gvProzent !== null && (
                    <small className={hoch === null ? '' : hoch ? 'auf' : 'ab'}>{prozent(p.gvProzent, t.code)}</small>
                  )}
                </span>
                {p.anteil !== null && (
                  <span className="pf-balken" aria-hidden="true"><i style={{ width: (p.anteil * 100).toFixed(1) + '%' }} /></span>
                )}
              </button>
            );
          })}
        </div>
      )}
    </div>
  );
}
