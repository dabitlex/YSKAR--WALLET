import { registerPlugin } from '@capacitor/core';
import { App } from '@capacitor/app';
import { Browser } from '@capacitor/browser';
import { Clipboard } from '@capacitor/clipboard';
import { Haptics, ImpactStyle, NotificationType } from '@capacitor/haptics';
import { Share } from '@capacitor/share';
import { istNativ, fehlerMerken } from './plattform.ts';
import { einstellungenLager } from '../wallet/einstellungen.ts';

/*
  Haptik. Jede Rueckmeldung laeuft durch diese drei Funktionen; ist in den
  Einstellungen "Vibration" aus, vibriert die App nirgends. Im Browser passiert nichts.
*/
const vibrierenErlaubt = (): boolean => istNativ() && einstellungenLager.lesen().vibration;

/** Kurzes, dezentes Signal. */
export function tippGefuehl(): void {
  if (vibrierenErlaubt()) Haptics.impact({ style: ImpactStyle.Light }).catch(() => {});
}
export function erfolgGefuehl(): void {
  if (vibrierenErlaubt()) Haptics.notification({ type: NotificationType.Success }).catch(() => {});
}
export function fehlerGefuehl(): void {
  if (vibrierenErlaubt()) Haptics.notification({ type: NotificationType.Error }).catch(() => {});
}

export async function kopieren(text: string): Promise<boolean> {
  try {
    if (istNativ()) await Clipboard.write({ string: text });
    else await navigator.clipboard.writeText(text);
    return true;
  } catch (e) { fehlerMerken('kopieren', e); return false; }
}

export async function einfuegen(): Promise<string> {
  try {
    if (istNativ()) return (await Clipboard.read()).value ?? '';
    return await navigator.clipboard.readText();
  } catch { return ''; }
}

/** Ueber das Teilen-Menue des Geraets weitergeben. false = nicht moeglich. */
export async function teilen(text: string, titel: string): Promise<boolean> {
  try {
    if (istNativ()) { await Share.share({ text, title: titel, dialogTitle: titel }); return true; }
    if (navigator.share) { await navigator.share({ text, title: titel }); return true; }
  } catch { return true; /* vom Nutzer geschlossen */ }
  return false;
}

export function imBrowserOeffnen(url: string): void {
  if (istNativ()) Browser.open({ url }).catch(e => fehlerMerken('browser', e));
  else window.open(url, '_blank', 'noopener');
}

export function appSchliessen(): void {
  if (istNativ()) App.minimizeApp().catch(() => {});
}

/** Zurueck-Taste von Android. Gibt die Abmeldung zurueck. */
export function beiZurueck(f: () => void): () => void {
  if (!istNativ()) return () => {};
  const h = App.addListener('backButton', f);
  return () => { h.then(x => x.remove()).catch(() => {}); };
}

/** App geht in den Hintergrund oder kommt zurueck. */
export function beiSichtbarkeit(f: (sichtbar: boolean) => void): () => void {
  const web = () => f(document.visibilityState === 'visible');
  document.addEventListener('visibilitychange', web);
  let ab = () => {};
  if (istNativ()) {
    const h = App.addListener('appStateChange', s => f(s.isActive));
    ab = () => { h.then(x => x.remove()).catch(() => {}); };
  }
  return () => { document.removeEventListener('visibilitychange', web); ab(); };
}

/*
  Die App wurde ueber einen yskar:-Link geoeffnet.

  Der Link kann ankommen, waehrend die Wallet gesperrt ist. Deshalb wird er
  hier gemerkt und erst abgeholt, wenn die Oberflaeche offen ist -- genau
  einmal, nicht nach jedem Entsperren wieder.
*/
let wartenderLink: string | null = null;
const linkHoerer = new Set<() => void>();

export function linksVerbinden(): void {
  if (!istNativ()) return;
  const merken = (url: string) => { wartenderLink = url; linkHoerer.forEach(h => h()); };
  App.getLaunchUrl().then(r => { if (r?.url) merken(r.url); }).catch(() => {});
  App.addListener('appUrlOpen', e => merken(e.url)).catch(() => {});
}

export function linkAbholen(): string | null {
  const l = wartenderLink;
  wartenderLink = null;
  return l;
}

export function beiLink(f: () => void): () => void {
  linkHoerer.add(f);
  return () => { linkHoerer.delete(f); };
}

/*
  Sichtschutz -- und er kann auf den beiden Plattformen NICHT dasselbe.

  Android (SichtschutzPlugin.java): setzt FLAG_SECURE. Bildschirmfotos und
  Bildschirmaufnahmen sind dann gesperrt, die Uebersicht zeigt Schwarz.
  Eine echte Sperre.

  iOS (SichtschutzPlugin.swift): Es gibt keine oeffentliche Schnittstelle,
  um ein Bildschirmfoto zu verhindern -- keine. Moeglich und umgesetzt
  sind: die Uebersicht verdecken und ein gemachtes Foto hinterher melden.

  Deshalb wird die Faehigkeit ABGEFRAGT und nicht angenommen. Die
  Oberflaeche darf auf einem iPhone nicht behaupten, Bildschirmfotos
  seien gesperrt. Siehe docs/IOS.md.
*/
export interface SichtschutzKann {
  /** Verhindert die Plattform Bildschirmfotos wirklich? */
  sperrt: boolean;
  verdecktUebersicht: boolean;
  meldetFoto: boolean;
}
interface SichtschutzPlugin {
  setzen(o: { an: boolean }): Promise<void>;
  faehigkeiten?(): Promise<SichtschutzKann>;
}
const Sichtschutz = registerPlugin<SichtschutzPlugin>('Sichtschutz');

/* Im Browser und auf Android, dessen Plugin die Abfrage (noch) nicht
   kennt, gilt das Android-Verhalten: dort sperrt FLAG_SECURE wirklich. */
const VORGABE: SichtschutzKann =
  { sperrt: true, verdecktUebersicht: true, meldetFoto: false };

let kann: SichtschutzKann | null = null;

/** Was der Sichtschutz auf dieser Plattform leistet. */
export async function sichtschutzKann(): Promise<SichtschutzKann> {
  if (kann) return kann;
  if (!istNativ() || !Sichtschutz.faehigkeiten) { kann = VORGABE; return kann; }
  try {
    const a = await Sichtschutz.faehigkeiten();
    kann = {
      sperrt: a?.sperrt === true,
      verdecktUebersicht: a?.verdecktUebersicht !== false,
      meldetFoto: a?.meldetFoto === true,
    };
  } catch {
    kann = VORGABE;
  }
  return kann;
}

/*
  Zwei Quellen: Screens mit den 12 Woertern verlangen den Schutz immer
  (sichtschutz(true) beim Oeffnen, sichtschutz(false) beim Schliessen), und der
  Schalter in den Einstellungen schuetzt alle Bildschirme. Gezaehlt wird, damit
  das Schliessen der Woerter einen eingeschalteten Schalter nicht aufhebt.
*/
let verlangt = 0;
let gesetzt: boolean | null = null;

function sichtschutzAnwenden(): void {
  const an = verlangt > 0 || einstellungenLager.lesen().sichtschutz;
  if (an === gesetzt) return;
  gesetzt = an;
  if (istNativ()) Sichtschutz.setzen({ an }).catch(e => { gesetzt = null; fehlerMerken('sichtschutz', e); });
}

/** Ein Screen mit den 12 Woertern oeffnet (true) oder schliesst (false). */
export function sichtschutz(an: boolean): void {
  verlangt = Math.max(0, verlangt + (an ? 1 : -1));
  sichtschutzAnwenden();
}

/** Nach dem Start und nach jeder Aenderung des Schalters aufrufen. */
export function sichtschutzEinstellungAnwenden(): void {
  sichtschutzAnwenden();
}
