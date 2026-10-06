import { registerPlugin } from '@capacitor/core';
import { App } from '@capacitor/app';
import { Browser } from '@capacitor/browser';
import { Clipboard } from '@capacitor/clipboard';
import { Haptics, ImpactStyle, NotificationType } from '@capacitor/haptics';
import { Share } from '@capacitor/share';
import { istNativ, fehlerMerken } from './plattform.ts';

/** Kurzes Vibrieren. Im Browser passiert nichts. */
export function tippGefuehl(): void {
  if (istNativ()) Haptics.impact({ style: ImpactStyle.Light }).catch(() => {});
}
export function erfolgGefuehl(): void {
  if (istNativ()) Haptics.notification({ type: NotificationType.Success }).catch(() => {});
}
export function fehlerGefuehl(): void {
  if (istNativ()) Haptics.notification({ type: NotificationType.Error }).catch(() => {});
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
  Sichtschutz: Solange die 12 Woerter auf dem Bildschirm stehen, sperrt
  Android Bildschirmfotos und zeigt die App in der Uebersicht schwarz.
  Eigenes kleines Plugin in der Android-Huelle (SichtschutzPlugin.java).
*/
interface SichtschutzPlugin { setzen(o: { an: boolean }): Promise<void> }
const Sichtschutz = registerPlugin<SichtschutzPlugin>('Sichtschutz');

export function sichtschutz(an: boolean): void {
  if (istNativ()) Sichtschutz.setzen({ an }).catch(e => fehlerMerken('sichtschutz', e));
}
