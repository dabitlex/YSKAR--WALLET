import { V6_HEIGHT, V6_HEIGHT_UNSET, MIN_FEE } from '../kern/params.ts';
import { ADDRESS_BYTES } from '../kern/params.ts';
import { transferBytesAt, transferVersionAt, sendepause, TX_VERSION, TRANSFER_V2 } from '../kern/tx.ts';
import { decodeAddress } from '../kern/address.ts';
import type { Markt, Stufe } from '../netz/api.ts';

/**
 * Welche Ueberweisung die Wallet jetzt bauen darf -- Konsensfassung 6.
 *
 * Ab V6_HEIGHT gilt nur noch Fassung 2 einer Ueberweisung, davor nur
 * Fassung 1 (docs/CONSENSUS_V6.md 3 und 6.3, keine Uebergangsfrist). In
 * den drei Bloecken vor der Hoehe sendet die Wallet nichts: Eine Fassung 1,
 * die an der Grenze noch wartet, faellt aus dem Mempool; eine Fassung 2
 * nimmt vor der Hoehe niemand an.
 *
 * Quelle ist zuerst die Gebuehrenauskunft (/api/v2/fees): Der Knoten nennt
 * dort die Fassung und die Sendepause fuer den naechsten Block. Fehlt sie
 * dort, rechnet die Wallet aus der bekannten Hoehe der Kette. Ist auch die
 * unbekannt, wird nicht geraten und nicht gesendet.
 */
export interface SendeStand {
  /** Fassung der Ueberweisung, die jetzt zu bauen ist. */
  fassung: typeof TX_VERSION | typeof TRANSFER_V2;
  /** Sendepause vor der Aktivierungshoehe. */
  pause: boolean;
  /** Weder Auskunft noch Hoehe: nicht raten, nicht senden. */
  unbekannt: boolean;
}

export function sendeStand(markt: Markt | null, spitze: number | null, v6Height = V6_HEIGHT): SendeStand {
  if (markt && (markt.fassung === 1 || markt.fassung === 2)) {
    return { fassung: markt.fassung === 2 ? TRANSFER_V2 : TX_VERSION, pause: markt.sendepause === true, unbekannt: false };
  }
  if (spitze !== null) {
    const naechste = spitze + 1;
    return { fassung: transferVersionAt(naechste, v6Height) === TRANSFER_V2 ? TRANSFER_V2 : TX_VERSION,
             pause: sendepause(naechste, v6Height), unbekannt: false };
  }
  return { fassung: TX_VERSION, pause: false, unbekannt: v6Height !== V6_HEIGHT_UNSET };
}

/** Laenge der Rohadresse: 20 Byte fuer ysr1…, 33 fuer ysr1p…; null bei ungueltiger Adresse. */
export function empfaengerBytes(adresse: string): number | null {
  try { return decodeAddress(adresse).length; } catch { return null; }
}

/** Darf an diese Adresse mit dieser Fassung gesendet werden? ysr1p… erst ab Fassung 2. */
export const empfaengerErlaubt = (bytes: number, fassung: number): boolean =>
  bytes === ADDRESS_BYTES || fassung === TRANSFER_V2;

/** Groesse der Ueberweisung in Byte, so wie sie der naechste Block misst. */
export function ueberweisungBytes(stand: SendeStand, markt: Markt | null, notizBytes: number, empfaenger = ADDRESS_BYTES): number {
  const v6 = stand.fassung === TRANSFER_V2 ? 0 : V6_HEIGHT_UNSET;
  if (markt?.basisBytes) {
    // Die Auskunft nennt die Groesse ohne Notiz fuer einen Empfaenger der Version 0.
    const mehr = transferBytesAt(1, 0, v6, empfaenger) - transferBytesAt(1, 0, v6);
    return markt.basisBytes + mehr + notizBytes;
  }
  return transferBytesAt(1, notizBytes, v6, empfaenger);
}

/**
 * Gebuehr fuer eine Stufe. Ohne Auskunft die alte Mindestgebuehr -- sie
 * deckt jede Regel. Mit Auskunft die Stufe des Marktes (je Byte, wenn der
 * Knoten einen Satz nennt), aber nie unter dem Satz je Byte fuer genau
 * diese Groesse.
 */
export function gebuehrFuer(markt: Markt | null, stufe: Stufe, bytes: number): bigint {
  if (!markt) return MIN_FEE;
  const s = markt.stufen[stufe];
  const stufenGebuehr = s.jeByte ? BigInt(s.jeByte) * BigInt(bytes) : BigInt(s.fee);
  const boden = markt.mindestJeByte ? BigInt(markt.mindestJeByte) * BigInt(bytes) : 0n;
  return stufenGebuehr > boden ? stufenGebuehr : boden;
}
