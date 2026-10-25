import { BiometricAuth } from '@aparajita/capacitor-biometric-auth';
import { istNativ } from '../geraet/plattform.ts';
import * as speicher from '../geraet/speicher.ts';

/**
 * Entsperren und Senden mit Fingerabdruck oder Gesicht.
 *
 * WIE ES FUNKTIONIERT, EHRLICH: Der Tresor bleibt mit der PIN
 * verschluesselt. Wer Biometrie einschaltet, legt seine PIN im geschuetzten
 * Speicher der App ab (Android Keystore). Die App verlangt zuerst die
 * Bestaetigung des Systems und liest die PIN erst danach aus.
 *
 * Das schuetzt das entsperrte Telefon in fremder Hand. Es schuetzt nicht
 * vor einem Geraet mit Root-Zugriff. Wer das nicht will, laesst es aus.
 */

const ORT = 'yskar.tresor.pin';
const MERKER = 'yskar.biometrie';

export type BioStand = { da: false; grund: string } | { da: true };

export const bioAktiv = (): boolean => {
  try { return localStorage.getItem(MERKER) === '1'; } catch { return false; }
};

export async function bioStand(): Promise<BioStand> {
  if (!istNativ()) return { da: false, grund: 'nur in der App' };
  try {
    const r = await BiometricAuth.checkBiometry();
    return r.isAvailable ? { da: true } : { da: false, grund: `${r.reason ?? ''} ${r.code ?? ''}`.trim() || 'nicht eingerichtet' };
  } catch (e) {
    return { da: false, grund: String((e as Error)?.message ?? e) };
  }
}

async function bestaetigen(grund: string, abbrechen: string): Promise<boolean> {
  if (!istNativ()) return false;
  try {
    // Android zeigt App-Symbol und App-Namen selbst im Kopf des Dialogs. Deshalb
    // nur eine Zeile von uns: der Grund als Titel, kein Untertitel, keine Beschreibung --
    // sonst stehen Name und Grund doppelt da.
    await BiometricAuth.authenticate({
      androidTitle: grund, cancelTitle: abbrechen,
      // Kein Geraete-Code als Ersatz: Der Ersatz ist die eigene PIN der Wallet.
      allowDeviceCredential: false,
    });
    return true;
  } catch { return false; }
}

/** Die PIN muss vorher gegen den Tresor geprueft sein -- hier wird sie nur abgelegt. */
export async function bioEinschalten(pin: string, grund: string, abbrechen: string): Promise<boolean> {
  if (!(await bestaetigen(grund, abbrechen))) return false;
  try {
    await speicher.schreiben(ORT, pin);
    localStorage.setItem(MERKER, '1');
    return true;
  } catch { return false; }
}

export async function bioAusschalten(): Promise<void> {
  await speicher.entfernen(ORT);
  try { localStorage.removeItem(MERKER); } catch { /* egal */ }
}

/** Sensor verlangen und die PIN herausgeben. null = abgebrochen oder nichts hinterlegt. */
export async function bioPin(grund: string, abbrechen: string): Promise<string | null> {
  if (!istNativ() || !bioAktiv()) return null;
  if (!(await bestaetigen(grund, abbrechen))) return null;
  try {
    const wert = await speicher.lesen(ORT);
    return wert && /^\d{6}$/.test(wert) ? wert : null;
  } catch { return null; }
}
