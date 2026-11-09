import { useT } from '../i18n/index.ts';
import { useDaten } from '../zustand.tsx';
import { hashrateText, zahlText, umlaufAnteil, blockAbstand } from '../netz/netzdaten.ts';
import { V6_HEIGHT } from '../kern/params.ts';
import { betragGenau } from '../wallet/betrag.ts';
import { S } from './teile.tsx';

/**
 * Der Zustand des YSKAR-Netzes auf der Wallet-Seite.
 *
 * Alle Werte kommen aus /api/v2/summary und /api/v2/supply und werden nur
 * angezeigt, wenn sie wirklich da sind. Fehlt ein Feld, faellt die Kachel
 * weg -- sie zeigt nie eine 0, die wie eine Messung aussieht.
 *
 * Der Hinweis zum Kurs steht bewusst hier und bewusst so deutlich: YSR
 * wird an keiner Boerse gehandelt. Es gibt keinen Kurs, also steht hier
 * auch keiner. Eine Wallet, die an dieser Stelle eine Zahl erfindet,
 * waere in allem anderen auch nicht zu trauen.
 */

function Kachel({ lab, wert, unter }: { lab: string; wert: string; unter?: string }) {
  return (
    <div className="nk-kachel">
      <span className="nk-lab">{lab}</span>
      <b className="nk-wert">{wert}</b>
      {unter && <span className="nk-unter">{unter}</span>}
    </div>
  );
}

export function Netzkarte() {
  const t = useT();
  const { netz, umlauf, offline } = useDaten();

  // Ohne einen einzigen gelungenen Abruf gibt es nichts zu zeigen.
  if (!netz || netz.hoehe === null) {
    return offline ? (
      <section className="karte nk">
        <div className="nk-kopf"><h2>{t.netz.titel}</h2></div>
        <p className="nk-leer">{t.netz.nichtErreichbar}</p>
      </section>
    ) : null;
  }

  const hr = hashrateText(netz.hashrate, t.code);
  const anteil = umlaufAnteil(netz.gesamtmenge, netz.hoechstmenge);
  const bis6 = blockAbstand(netz.naechsteHoehe ?? netz.hoehe, V6_HEIGHT, netz.zielBlockzeit);

  // Grobe, ehrlich gekennzeichnete Schaetzung -- Bloecke kommen unregelmaessig.
  const dauer = (sek: number | null): string | null => {
    if (sek === null) return null;
    const std = Math.round(sek / 3600);
    if (std < 1) return t.netz.bald;
    return std < 48 ? t.netz.stunden(std) : t.netz.tage(Math.round(std / 24));
  };

  return (
    <section className="karte nk">
      <div className="nk-kopf">
        <h2>{t.netz.titel}</h2>
        <span className={`nk-puls ${offline ? 'alt' : ''}`}>
          <i aria-hidden="true" />{offline ? t.netz.alt : t.netz.live}
        </span>
      </div>

      <div className="nk-gitter">
        <Kachel lab={t.netz.block} wert={'#' + (zahlText(netz.hoehe, t.code) ?? '')} />
        {hr && <Kachel lab={t.netz.hashrate} wert={hr} />}
        {netz.aktiveMiner !== null && (
          <Kachel lab={t.netz.miner} wert={zahlText(netz.aktiveMiner, t.code) ?? ''}
                  unter={netz.knoten !== null ? t.netz.knoten(netz.knoten) : undefined} />
        )}
        {netz.naechsteBelohnung !== null && (
          // betragGenau rechnet die Einheiten in YSR um (1 YSR = 10^8) und
          // laesst Nullen am Ende weg: "875", nach Halvings auch "0,42".
          <Kachel lab={t.netz.belohnung} wert={betragGenau(netz.naechsteBelohnung, t.code, 8, 0) + ' YSR'} />
        )}
      </div>

      {anteil !== null && (() => {
        // Prozent ueber Intl: Deutsch setzt ein schmales Leerzeichen vor das
        // Zeichen, Englisch keines. Von Hand angehaengt waere eines davon falsch.
        const prozent = anteil.toLocaleString(t.code, { style: 'percent', maximumFractionDigits: 1 });
        return (
        <div className="nk-umlauf">
          <div className="nk-umlauf-kopf">
            <span className="nk-lab">{t.netz.umlauf}</span>
            <span className="nk-anteil">{t.netz.anteil(prozent)}</span>
          </div>
          <div className="nk-balken" role="img" aria-label={t.netz.anteil(prozent)}>
            <b style={{ width: (anteil * 100).toFixed(2) + '%' }} />
          </div>
          {umlauf?.umlauf && umlauf.hoechst && (
            <span className="nk-unter">{t.netz.umlaufVon(
              Number(umlauf.umlauf).toLocaleString(t.code, { maximumFractionDigits: 0 }),
              Number(umlauf.hoechst).toLocaleString(t.code, { maximumFractionDigits: 0 }))}</span>
          )}
        </div>
        );
      })()}

      {bis6 && (
        <div className="nk-zeile">
          <S n="info" g={17} w={1.75} />
          <span><b>{t.netz.fassung6}</b>
            <small>{t.netz.fassung6Text(zahlText(bis6.bloecke, t.code) ?? '', dauer(bis6.sekunden) ?? '')}</small>
          </span>
        </div>
      )}

      <div className="nk-kurs">
        <b>{t.netz.keinKurs}</b>
        <span>{t.netz.keinKursText}</span>
      </div>
    </section>
  );
}
