import { Capacitor } from '@capacitor/core';

/** Laeuft die Oberflaeche in der Android-App oder (zum Entwickeln) im Browser? */
export const istNativ = (): boolean => Capacitor.isNativePlatform();

/*
  Die letzten Fehler, fuer die Diagnose in den Einstellungen. Tester koennen
  sie kopieren und schicken -- ohne Adresse, ohne Betraege.
*/
const FEHLER: string[] = [];

export function fehlerMerken(quelle: string, e: unknown): void {
  const text = e instanceof Error ? `${e.name}: ${e.message}` : String(e);
  FEHLER.push(`${new Date().toISOString().slice(11, 19)} ${quelle}: ${text}`.slice(0, 300));
  if (FEHLER.length > 30) FEHLER.shift();
}

export const letzteFehler = (): string[] => [...FEHLER];

export function fehlerspeicherInstallieren(): void {
  window.addEventListener('error', e => fehlerMerken('fenster', e.error ?? e.message));
  window.addEventListener('unhandledrejection', e => fehlerMerken('zusage', e.reason));
}
