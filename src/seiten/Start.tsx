import { useT } from '../i18n/index.ts';
import { useDaten, useNav, useWallet } from '../zustand.tsx';
import { einstellen, useEinstellungen } from '../wallet/einstellungen.ts';
import { nichtGelistet } from '../wallet/zahlungen.ts';
import { kopieren, tippGefuehl } from '../geraet/geraet.ts';
import { useZahlungen } from '../ui/daten.ts';
import { S, Betrag, Platzhalterzeilen, Zahlungszeile, useAnzeige, useMeldung, useWer, useZeit } from '../ui/teile.tsx';
import { YMarke, YBaender, ZahlZeichen } from '../ui/zeichen.tsx';
import { EinfuehrungKarte } from './Einfuehrung.tsx';

/** "ysr1…3h8q": nur zum Wiedererkennen. Kopiert wird immer die volle Adresse. */
const kurz = (a: string | null) => (a ? `${a.slice(0, 4)}…${a.slice(-4)}` : '');

/**
 * Wallet (Konzept "Ruhe", Variante C): oben leise die Adresse, rechts Scannen
 * und Sperren. In der Mitte das Guthaben mit dem Y als Zeichen, darunter zwei
 * Knoepfe -- Senden hell, Empfangen auf Flaeche; bei einer neuen Wallet ist
 * Empfangen der helle Knopf. Ein Hinweis steht als eine Zeile darunter, dann
 * die letzten Zahlungen. Kein Kurs, kein Tausch: nur, was die Wallet kann.
 */
export function Start() {
  const t = useT();
  const nav = useNav();
  const wallet = useWallet();
  const { konto, offline, ausSpeicher } = useDaten();
  const zeit = useZeit();
  const { wartend, verlauf } = useZahlungen();
  const { verborgen, einfuehrungGesehen } = useEinstellungen();
  const melde = useMeldung();
  const anzeige = useAnzeige();
  const wer = useWer();
  const stand = konto ? BigInt(konto.balance) : null;
  const liste = [...wartend, ...verlauf].slice(0, 5);
  const erste = wartend[0];
  // Neue Wallet: nichts da, nichts unterwegs -- dann fuehrt Empfangen.
  const neu = konto !== null && stand === 0n && verlauf.length === 0 && wartend.length === 0;
  // Ohne Netz fuehrt Senden nirgends hin; der Grund steht in der Zeile darunter.
  const sendenAus = offline || neu;

  return (
    <div className="seite mit-nav st">
      <div className="st-kopf">
        <button type="button" className="st-adresse sinkt" aria-label={t.start.adresseKopieren}
                onClick={async () => { if (wallet.adresse && await kopieren(wallet.adresse)) { tippGefuehl(); melde(t.start.adresseKopiert); } }}>
          <span className="mono">{kurz(wallet.adresse)}</span><S n="kopie" g={16} w={1.75} />
        </button>
        <span className="st-knoepfe">
          <button type="button" className="klein leer sinkt" aria-label={t.scan.titel}
                  onClick={() => nav.gehe({ n: 'senden', scan: true })}><S n="scan" g={22} w={1.75} /></button>
          <button type="button" className="klein leer sinkt" aria-label={t.start.sperren} onClick={wallet.sperren}><S n="schloss" g={21} w={1.75} /></button>
        </span>
      </div>

      <div className={`st-guthaben auf ${offline || ausSpeicher !== null ? 'alt' : ''}`}>
        {ausSpeicher !== null
          ? <span className="st-lab" role="status">{t.start.standVom(`${zeit.tag(ausSpeicher)}, ${zeit.uhr(ausSpeicher)}`)}</span>
          : <button type="button" className="st-lab sinkt" aria-pressed={verborgen} aria-label={verborgen ? t.start.zeigen : t.start.verbergen}
                    onClick={() => einstellen({ verborgen: !verborgen })}>
              {t.start.guthaben}<S n={verborgen ? 'augeZu' : 'auge'} g={16} w={1.75} />
            </button>}
        <div className="st-wert">
          <YMarke g={34} />
          {verborgen
            ? <div className="betrag start"><div className="gross" aria-label={t.start.zeigen}>••••<i>,••</i></div></div>
            : stand === null
              ? (offline ? <div className="st-laedt">–</div> : <span className="st-platzhalter" aria-hidden="true"><i /></span>)
              : <Betrag wert={stand} uebersicht klasse="start" />}
        </div>
        {stand === null && !offline && !verborgen && <span className="sf-klein st-laedt-text" role="status">{t.start.laedt}</span>}
      </div>

      <nav className="st-aktionen st-zwei auf" aria-label={t.start.aktionen} style={{ animationDelay: '.06s' }}>
        <button type="button" className={`knopf sinkt ${neu ? 'zweit' : ''}`} disabled={sendenAus} onClick={() => nav.gehe({ n: 'senden' })}>
          <ZahlZeichen art="aus" g={20} />{t.start.senden}
        </button>
        <button type="button" className={`knopf sinkt ${neu ? '' : 'zweit'}`} onClick={() => nav.gehe({ n: 'empfangen' })}>
          <ZahlZeichen art="ein" g={20} />{t.start.empfangen}
        </button>
      </nav>

      {offline
        ? <div className="st-zeile bern" role="status"><S n="warnung" g={18} w={1.75} /><span>{konto ? t.start.offlineZeile : `${t.start.offlineTitel} ${t.start.offlineOhneStand}`}</span></div>
        : erste && (
          <button type="button" className="st-wartet sinkt" onClick={() => nav.gehe({ n: 'detail', zahlung: erste })}>
            <YBaender stand={['erledigt', 'wartet', 'offen']} label={nichtGelistet(erste) ? t.zahlung.nichtGelistet : t.zahlung.wartetBlock} />
            <span>{wartend.length > 1 ? t.start.wartenMehrere(wartend.length)
              : erste.betragOffen ? t.start.sammelVon(wer(erste.gegen))
              : <><b>{anzeige(erste.betrag)}</b> {erste.art === 'aus' ? t.start.wartetAn(erste.mehrere ? t.zahlung.mehrereKlein : wer(erste.gegen)) : t.start.wartetVon(wer(erste.gegen))}</>}</span>
          </button>
        )}

      {!einfuehrungGesehen && <div className="st-einf"><EinfuehrungKarte /></div>}

      <div className="st-liste auf" style={{ animationDelay: '.1s' }}>
        <div className="sf-abschnitt st-abschnitt">
          <span className="sf-lab">{t.start.zuletzt}</span>
          {liste.length > 0 && <button type="button" className="verweis" onClick={() => nav.reiter('verlauf')}>{t.start.alle}</button>}
        </div>
        {liste.length === 0
          ? (konto ? <div className="leerstand">{neu ? <><b>{t.start.leer}</b><br /><span>{t.einf.k2.text}</span></> : t.start.leer}</div> : offline ? <div className="leerstand">–</div> : <Platzhalterzeilen />)
          : <div className="liste">{liste.map(z => <Zahlungszeile key={z.txid} z={z} onTipp={() => nav.gehe({ n: 'detail', zahlung: z })} />)}</div>}
      </div>
    </div>
  );
}
