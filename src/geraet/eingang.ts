import { registerPlugin } from '@capacitor/core';
import { istNativ } from './plattform.ts';

/*
  Benachrichtigung bei Eingang.

  Die App fragt im Hintergrund etwa alle 15 Minuten den Spiegel nach dem
  Konto -- auch wenn sie nicht laeuft (Android WorkManager, siehe
  EingangPlugin.java und EingangArbeit.java). Steht ein neuer Eingang in
  einem Block, meldet Android ihn. Kein Server kennt dafuer das Geraet.
*/

export interface EingangStand { an: boolean; berechtigung: 'granted' | 'denied' | 'prompt'; akkuFrei: boolean }
export interface EingangTexte { sprache: string; kanal: string; kanalText: string; von: string; belohnung: string; pool: string; block: string; gesperrt: string }

interface EingangPlugin {
  einschalten(o: { adresse: string; texte: EingangTexte }): Promise<EingangStand>;
  ausschalten(): Promise<EingangStand>;
  stand(): Promise<EingangStand>;
  akkuEinstellung(): Promise<void>;
  jetzt(): Promise<void>;
  geoeffnetMit(): Promise<{ txid: string | null }>;
}
const Eingang = registerPlugin<EingangPlugin>('Eingang');

const AUS: EingangStand = { an: false, berechtigung: 'denied', akkuFrei: false };

export const eingangMoeglich = (): boolean => istNativ();
export const eingangStand = (): Promise<EingangStand> => istNativ() ? Eingang.stand().catch(() => AUS) : Promise.resolve(AUS);
export const eingangEinschalten = (adresse: string, texte: EingangTexte): Promise<EingangStand> => istNativ() ? Eingang.einschalten({ adresse, texte }).catch(() => AUS) : Promise.resolve(AUS);
export const eingangAusschalten = (): Promise<EingangStand> => istNativ() ? Eingang.ausschalten().catch(() => AUS) : Promise.resolve(AUS);
export const eingangAkku = (): Promise<void> => istNativ() ? Eingang.akkuEinstellung().catch(() => {}) : Promise.resolve();
/** Ueber eine Meldung geoeffnet? Dann die TXID -- genau einmal. */
export const eingangGeoeffnet = async (): Promise<string | null> => istNativ() ? (await Eingang.geoeffnetMit().catch(() => ({ txid: null }))).txid : null;
