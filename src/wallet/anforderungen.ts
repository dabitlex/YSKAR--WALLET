import { lager, useLager } from './lager.ts';

/**
 * Anforderungen: "Bitte sende mir X YSR".
 *
 * Wallets koennen sich untereinander keine Nachricht schicken -- dafuer
 * braeuchte es einen Server dazwischen. Eine Anforderung ist deshalb ein
 * Link oder QR-Code, den der Nutzer selbst weitergibt. Hier wird nur
 * festgehalten, DASS er sie erstellt hat, damit sie im Verlauf mit dem
 * Kontakt steht. Das liegt nur auf diesem Geraet.
 *
 * "Bezahlt" ist eine Vermutung der App, kein Wissen der Kette: Sie gilt,
 * sobald von dieser Adresse nach der Anforderung ein Eingang mit
 * mindestens dem Betrag ankommt.
 */
export interface Anforderung {
  id: string;
  /** Adresse dessen, der zahlen soll. */
  von: string;
  /** Ganze Einheiten als Text (BigInt laesst sich nicht als JSON ablegen). */
  betrag: string;
  notiz: string;
  /** Unix-Sekunden. */
  zeit: number;
}

export const anforderungenLager = lager<Anforderung[]>('yskar.anforderungen', [], roh =>
  Array.isArray(roh)
    ? roh.filter((a): a is Anforderung => !!a && typeof a.id === 'string' && typeof a.von === 'string'
        && /^\d+$/.test(String(a.betrag)) && typeof a.zeit === 'number').slice(-200)
    : []);

export function anforderungMerken(von: string, betrag: bigint, notiz: string): void {
  const id = crypto.getRandomValues(new Uint32Array(2)).join('');
  anforderungenLager.aendern(alt => [...alt, { id, von: von.toLowerCase(), betrag: betrag.toString(), notiz, zeit: Math.floor(Date.now() / 1000) }].slice(-200));
}

export function anforderungEntfernen(id: string): void {
  anforderungenLager.aendern(alt => alt.filter(a => a.id !== id));
}

export const useAnforderungen = (): Anforderung[] => useLager(anforderungenLager);
