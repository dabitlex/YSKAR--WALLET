import { useEffect, useRef, useState } from 'react';
import { useT } from '../i18n/index.ts';
import { useWallet } from '../zustand.tsx';
import { bioAktiv, bioPin } from '../wallet/biometrie.ts';
import { Blatt, PinFeld, type PinAntwort } from '../ui/teile.tsx';
import { kurzAdresse } from '../wallet/adresse.ts';

/**
 * Gesperrt: PIN oder Fingerabdruck.
 * `eingeblendet`: erst dann wird der Fingerabdruck angeboten (beim Start kommt
 * die Sperre aus dem Startbildschirm; bei spaeterem Sperren ist sie sofort da).
 */
export function Sperre({ auftritt, eingeblendet = true }: { auftritt?: 'kommt'; eingeblendet?: boolean }) {
  const t = useT();
  const wallet = useWallet();
  const [vergessen, setVergessen] = useState(false);
  const [sicher, setSicher] = useState(false);
  const gefragt = useRef(false);
  // Kam die Sperre aus dem Startbildschirm, spielt sie die Seiten-Einblendung danach nicht noch einmal ab.
  const vomStart = useRef(auftritt === 'kommt');

  const mitFinger = async () => {
    const pin = await bioPin(t.pin.bioOeffnen, t.allg.abbrechen);
    if (pin) await wallet.entsperren(pin);
  };

  // Sobald die Sperre ganz zu sehen ist, den Sensor anbieten -- einmal, nicht in einer Schleife.
  useEffect(() => {
    if (!eingeblendet || gefragt.current || !bioAktiv()) return;
    gefragt.current = true;
    const u = setTimeout(mitFinger, 350);
    return () => clearTimeout(u);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [eingeblendet]);

  return (
    <>
      <PinFeld titel={<>{t.pin.willkommen} <em>{t.pin.zurueck}</em></>} logo auftritt={auftritt ?? (vomStart.current ? 'da' : undefined)}
        unter={<span className="sperre-adresse"><i aria-hidden="true" /><span className="mono">{kurzAdresse(wallet.adresse, '')}</span></span>}
        bio={bioAktiv() ? mitFinger : undefined}
        onPin={async (pin): Promise<PinAntwort> => { const r = await wallet.entsperren(pin); return r.ok ? 'ok' : r.grund; }}
        fuss={<button type="button" className="verweis" onClick={() => { setSicher(false); setVergessen(true); }}>{t.pin.vergessen}</button>} />
      {vergessen && (
        <Blatt titel={t.pin.vergessenTitel} onZu={() => setVergessen(false)}>
          <p>{t.pin.vergessenText}</p>
          {sicher && <p style={{ color: 'var(--rot)' }}>{t.einst.entfernenText}</p>}
          <div className="stapel">
            <button type="button" className="knopf gefahr sinkt" onClick={() => { if (sicher) wallet.vergessen(); else setSicher(true); }}>
              {sicher ? t.einst.entfernenJa : t.einst.entfernen}
            </button>
            <button type="button" className="knopf zweit sinkt" onClick={() => setVergessen(false)}>{t.allg.abbrechen}</button>
          </div>
        </Blatt>
      )}
    </>
  );
}
