import { useMemo } from 'react';
import { useT } from '../i18n/index.ts';
import { useEinstellungen } from '../wallet/einstellungen.ts';
import { useNav } from '../zustand.tsx';
import { usePortfolio } from '../portfolio/lager.ts';
import { bewerten, type Kurse } from '../portfolio/rechnen.ts';
import { useListe } from '../markt/haken.ts';
import { kurs } from '../markt/format.ts';
import { S } from './teile.tsx';

/**
 * Der Weg von der Wallet ins Portfolio.
 *
 * Hier steht der Fiat-Wert der anderen Positionen -- und NICHT eine Summe
 * mit dem YSR-Guthaben darueber. YSR wird an keiner Boerse gehandelt; eine
 * gemeinsame Zahl waere erfunden. Deshalb traegt die Zeile den Zusatz
 * "ausserhalb dieser Wallet": Sie ergaenzt das Guthaben, sie fasst es
 * nicht zusammen.
 */
export function PortfolioZeile() {
  const t = useT();
  const nav = useNav();
  const { waehrung } = useEinstellungen();
  const { positionen } = usePortfolio();
  // Die Liste liegt im Cache, wenn Markets schon offen war. Ist sie nicht
  // da, steht hier kein Wert -- und keine 0.
  const liste = useListe(waehrung);

  const summe = useMemo(() => {
    if (positionen.length === 0) return null;
    const k: Kurse = {};
    for (const c of liste.daten?.wert ?? []) k[c.id] = c.preis;
    const b = bewerten(positionen, k, null);
    return b.ohneKurs === positionen.length ? null : b.summeCent;
  }, [positionen, liste.daten]);

  const leer = positionen.length === 0;
  return (
    <button type="button" className="st-pf sinkt" onClick={() => nav.gehe({ n: 'portfolio' })}>
      <span className="st-pf-ico"><S n="kurve" g={19} w={1.75} /></span>
      <span className="st-pf-text">
        <b>{leer ? t.start.pfLeer : t.start.pfZeile}</b>
        <small>{leer ? t.start.pfLeerUnter : t.start.pfUnter(positionen.length)}</small>
      </span>
      {!leer && summe !== null && (
        <span className="st-pf-wert">{kurs(summe / 100, waehrung, t.code)}</span>
      )}
      <S n="weiter" g={16} w={1.75} />
    </button>
  );
}
