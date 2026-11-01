import { useMemo } from 'react';
import { useDaten } from '../zustand.tsx';
import { useGesendet } from '../wallet/gesendet.ts';
import { ausEintrag, ausWartend, type Zahlung } from '../wallet/zahlungen.ts';

/** Wartende und bestaetigte Zahlungen des Kontos, fertig fuer die Anzeige. */
export function useZahlungen(): { wartend: Zahlung[]; verlauf: Zahlung[] } {
  const { konto, wartende } = useDaten();
  const eigene = useGesendet();
  return useMemo(() => {
    const zeiten = new Map(eigene.map(g => [g.txid, g.zeit]));
    // Fuehrt der Knoten die wartende Zahlung? Nur seine eigene Liste zaehlt (pendingSource "fullnode").
    const gelistet = new Set((konto?.pending ?? []).map(p => p.txid));
    const liste = konto?.pendingSource === 'fullnode';
    return {
      wartend: wartende.map(w => ({
        ...ausWartend(w, zeiten.get(w.txid) ?? null),
        mempool: !konto ? null : gelistet.has(w.txid) ? true : liste ? false : null,
      })),
      verlauf: (konto?.history ?? []).map(ausEintrag),
    };
  }, [konto, wartende, eigene]);
}
