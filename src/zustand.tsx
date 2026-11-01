import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState, type ReactNode } from 'react';
import * as tresor from './wallet/tresor.ts';
import { createMnemonic, keypairFromMnemonic, isValidMnemonic, normalizeMnemonic } from './kern/wallet.ts';
import { toHex } from './kern/codec.ts';
import { bioAusschalten, bioAktiv } from './wallet/biometrie.ts';
import { kontakteLager } from './wallet/kontakte.ts';
import { anforderungenLager } from './wallet/anforderungen.ts';
import { einstellungenLager } from './wallet/einstellungen.ts';
import { gesendetLager, gesendetAufraeumen, useGesendet } from './wallet/gesendet.ts';
import * as speicher from './geraet/speicher.ts';
import { beiSichtbarkeit } from './geraet/geraet.ts';
import { fehlerMerken } from './geraet/plattform.ts';
import { kontoLaden, kopfLaden, type Konto, type Wartend } from './netz/api.ts';
import type { Zahlung } from './wallet/zahlungen.ts';

/* ------------------------------------------------------------------ Wallet */

/**
 * Vier Lagen:
 *   laden        -> der Tresor wird gesucht
 *   kein_tresor  -> Einstieg: erstellen oder wiederherstellen
 *   gesperrt     -> PIN oder Fingerabdruck
 *   offen        -> die Adresse ist bekannt, die Oberflaeche ist frei
 *
 * Der private Schluessel liegt NIE im Speicher der laufenden App. Zum
 * Senden werden die 12 Woerter frisch aus dem Tresor geholt (PIN oder
 * Fingerabdruck), der Schluessel abgeleitet, signiert und verworfen.
 */
export type Phase = 'laden' | 'kein_tresor' | 'gesperrt' | 'offen';
export type PinErgebnis = { ok: true; woerter: string } | { ok: false; grund: 'falsch' | 'gebremst' };

interface WalletZustand {
  phase: Phase;
  adresse: string | null;
  /** Neue Woerter, solange sie noch nicht bestaetigt und versiegelt sind. */
  frisch: string | null;
  erstellen(): string;
  verwerfen(): void;
  versiegeln(pin: string): Promise<void>;
  wiederherstellen(woerter: string, pin: string): Promise<boolean>;
  pinPruefen(pin: string): Promise<PinErgebnis>;
  entsperren(pin: string): Promise<PinErgebnis>;
  pinAendern(alt: string, neu: string): Promise<boolean>;
  sperren(): void;
  vergessen(): Promise<void>;
}

const WalletCtx = createContext<WalletZustand | null>(null);

export function WalletProvider({ children }: { children: ReactNode }) {
  const [phase, setPhase] = useState<Phase>('laden');
  const [adresse, setAdresse] = useState<string | null>(null);
  const [frisch, setFrisch] = useState<string | null>(null);
  const phaseRef = useRef(phase);
  phaseRef.current = phase;

  useEffect(() => {
    tresor.laden().then(t => {
      if (!t) { setPhase('kein_tresor'); return; }
      setAdresse(t.address);
      setPhase('gesperrt');
    }).catch(e => { fehlerMerken('tresor laden', e); setPhase('kein_tresor'); });
  }, []);

  // Automatisch sperren, wenn die App lange genug im Hintergrund war.
  useEffect(() => {
    let weg = 0;
    return beiSichtbarkeit(sichtbar => {
      if (phaseRef.current !== 'offen') { weg = 0; return; }
      const nach = einstellungenLager.lesen().sperreNach * 1000;
      if (!sichtbar) {
        if (nach === 0) setPhase('gesperrt'); else if (!weg) weg = Date.now();
      } else {
        if (weg && Date.now() - weg >= nach) setPhase('gesperrt');
        weg = 0;
      }
    });
  }, []);

  const erstellen = useCallback(() => { const m = createMnemonic(12); setFrisch(m); return m; }, []);
  const verwerfen = useCallback(() => setFrisch(null), []);

  const einrichten = useCallback(async (woerter: string, pin: string) => {
    const kp = keypairFromMnemonic(woerter);
    await tresor.speichern(await tresor.versiegeln(woerter, pin, kp.address));
    tresor.richtigePin();
    setAdresse(kp.address);
    setFrisch(null);
    setPhase('offen');
  }, []);

  const versiegeln = useCallback(async (pin: string) => {
    if (!frisch) throw new Error('keine Woerter vorhanden');
    await einrichten(frisch, pin);
  }, [frisch, einrichten]);

  const wiederherstellen = useCallback(async (woerter: string, pin: string) => {
    const sauber = normalizeMnemonic(woerter);
    if (!isValidMnemonic(sauber)) return false;
    await einrichten(sauber, pin);
    return true;
  }, [einrichten]);

  const pinPruefen = useCallback(async (pin: string): Promise<PinErgebnis> => {
    if (tresor.wartezeit() > 0) return { ok: false, grund: 'gebremst' };
    const t = await tresor.laden();
    const woerter = t ? await tresor.oeffnen(t, pin) : null;
    if (!woerter) { tresor.falschePin(); return { ok: false, grund: tresor.wartezeit() > 0 ? 'gebremst' : 'falsch' }; }
    tresor.richtigePin();
    return { ok: true, woerter };
  }, []);

  const entsperren = useCallback(async (pin: string): Promise<PinErgebnis> => {
    const r = await pinPruefen(pin);
    if (r.ok) {
      // Die Adresse steht im Tresor; hier wird sie gegen die Woerter geprueft.
      const kp = keypairFromMnemonic(r.woerter);
      setAdresse(kp.address);
      setPhase('offen');
    }
    return r;
  }, [pinPruefen]);

  const pinAendern = useCallback(async (alt: string, neu: string) => {
    const r = await pinPruefen(alt);
    if (!r.ok) return false;
    const kp = keypairFromMnemonic(r.woerter);
    await tresor.speichern(await tresor.versiegeln(r.woerter, neu, kp.address));
    // Die hinterlegte PIN fuer den Fingerabdruck waere jetzt falsch.
    if (bioAktiv()) await speicher.schreiben('yskar.tresor.pin', neu);
    return true;
  }, [pinPruefen]);

  const sperren = useCallback(() => setPhase('gesperrt'), []);

  const vergessen = useCallback(async () => {
    await tresor.loeschen();
    await bioAusschalten();
    tresor.richtigePin();
    kontakteLager.leeren(); anforderungenLager.leeren(); gesendetLager.leeren(); einstellungenLager.leeren();
    setAdresse(null); setFrisch(null); setPhase('kein_tresor');
  }, []);

  const wert = useMemo(() => ({ phase, adresse, frisch, erstellen, verwerfen, versiegeln, wiederherstellen,
    pinPruefen, entsperren, pinAendern, sperren, vergessen }),
    [phase, adresse, frisch, erstellen, verwerfen, versiegeln, wiederherstellen, pinPruefen, entsperren, pinAendern, sperren, vergessen]);
  return <WalletCtx.Provider value={wert}>{children}</WalletCtx.Provider>;
}

export function useWallet(): WalletZustand {
  const c = useContext(WalletCtx);
  if (!c) throw new Error('useWallet ausserhalb des WalletProvider');
  return c;
}

/* ------------------------------------------------------------------- Daten */

interface DatenZustand {
  konto: Konto | null;
  /** Hoehe der Kette, fuer die Zahl der Bestaetigungen. */
  spitze: number | null;
  /** Der letzte Abruf ist gescheitert; was da ist, kann veraltet sein. */
  offline: boolean;
  /** Wartende Zahlungen: vom Knoten gemeldete plus eigene, die er (noch) nicht nennt. */
  wartende: Wartend[];
  /** Guthaben abzueglich der eigenen wartenden Zahlungen. */
  verfuegbar: bigint;
  /** Die Nonce, die die naechste Zahlung tragen muss. */
  naechsteNonce: bigint;
  neuLaden(): Promise<void>;
}

const DatenCtx = createContext<DatenZustand | null>(null);
const TAKT = 15_000;

export function DatenProvider({ children }: { children: ReactNode }) {
  const { adresse } = useWallet();
  const [konto, setKonto] = useState<Konto | null>(null);
  const [spitze, setSpitze] = useState<number | null>(null);
  const [offline, setOffline] = useState(false);
  const eigene = useGesendet();
  const laeuft = useRef(false);

  const neuLaden = useCallback(async () => {
    if (!adresse || laeuft.current) return;
    laeuft.current = true;
    try {
      const [k, kopf] = await Promise.all([kontoLaden(adresse), kopfLaden().catch(() => null)]);
      if (k.address !== adresse) throw new Error('Antwort fuer eine andere Adresse');
      setKonto(k);
      if (kopf && typeof kopf.height === 'number') setSpitze(kopf.height);
      setOffline(false);
      gesendetAufraeumen(new Set(k.history.map(e => e.txid)), BigInt(k.nonce));
    } catch (e) {
      fehlerMerken('konto laden', e);
      setOffline(true);
    } finally { laeuft.current = false; }
  }, [adresse]);

  useEffect(() => {
    setKonto(null); setOffline(false);
    if (!adresse) return;
    neuLaden();
    const uhr = setInterval(() => { if (document.visibilityState === 'visible') neuLaden(); }, TAKT);
    const ab = beiSichtbarkeit(s => { if (s) neuLaden(); });
    return () => { clearInterval(uhr); ab(); };
  }, [adresse, neuLaden]);

  const wert = useMemo<DatenZustand>(() => {
    const gemeldet = konto?.pending ?? [];
    const bekannt = new Set([...gemeldet.map(p => p.txid), ...(konto?.history ?? []).map(e => e.txid)]);
    const kontoNonce = BigInt(konto?.nonce ?? '0');
    const nurHier: Wartend[] = adresse ? eigene
      .filter(g => !bekannt.has(g.txid) && BigInt(g.nonce) >= kontoNonce)
      .map(g => ({ txid: g.txid, kind: 'out' as const, from: adresse, to: g.an, amount: g.betrag, fee: g.gebuehr,
                   nonce: g.nonce, memo: g.notiz ? toHex(new TextEncoder().encode(g.notiz)) : null })) : [];
    const wartende = [...nurHier, ...gemeldet];
    const raus = wartende.filter(p => p.kind === 'out');
    const gebunden = raus.reduce((s, p) => s + BigInt(p.amount) + BigInt(p.fee), 0n);
    const stand = BigInt(konto?.balance ?? '0');
    let nonce = BigInt(konto?.nextNonce ?? konto?.nonce ?? '0');
    for (const p of raus) { const n = BigInt(p.nonce) + 1n; if (n > nonce) nonce = n; }
    return { konto, spitze, offline, wartende, verfuegbar: stand > gebunden ? stand - gebunden : 0n, naechsteNonce: nonce, neuLaden };
  }, [konto, spitze, offline, eigene, adresse, neuLaden]);

  return <DatenCtx.Provider value={wert}>{children}</DatenCtx.Provider>;
}

export function useDaten(): DatenZustand {
  const c = useContext(DatenCtx);
  if (!c) throw new Error('useDaten ausserhalb des DatenProvider');
  return c;
}

/* -------------------------------------------------------------- Navigation */

export type Ort =
  | { n: 'start' } | { n: 'kontakte' } | { n: 'verlauf' } | { n: 'einstellungen' }
  | { n: 'senden'; an?: string; betrag?: bigint | null; notiz?: string | null; scan?: boolean }
  | { n: 'empfangen'; fuer?: string }
  | { n: 'detail'; zahlung: Zahlung }
  | { n: 'kontakt'; adresse: string }
  | { n: 'kontaktBearbeiten'; adresse?: string }
  | { n: 'pinAendern' } | { n: 'woerterZeigen' } | { n: 'sicherheit' } | { n: 'einfuehrung' }
  | { n: 'signieren' } | { n: 'signaturPruefen' } | { n: 'anmeldenQr'; code?: string };

export type Reiter = 'start' | 'kontakte' | 'verlauf' | 'einstellungen';
export const REITER: Reiter[] = ['start', 'verlauf', 'kontakte', 'einstellungen'];

interface NavZustand {
  ort: Ort;
  tiefe: number;
  gehe(o: Ort): void;
  ersetze(o: Ort): void;
  zurueck(): void;
  /** Auf einen Reiter wechseln und alles darueber schliessen. */
  reiter(r: Reiter): void;
}

const NavCtx = createContext<NavZustand | null>(null);

export function NavProvider({ children }: { children: ReactNode }) {
  const [stapel, setStapel] = useState<Ort[]>([{ n: 'start' }]);
  const gehe = useCallback((o: Ort) => setStapel(s => [...s, o]), []);
  const ersetze = useCallback((o: Ort) => setStapel(s => [...s.slice(0, -1), o]), []);
  const zurueck = useCallback(() => setStapel(s => (s.length > 1 ? s.slice(0, -1) : s)), []);
  const reiter = useCallback((r: Reiter) => setStapel([{ n: r }]), []);
  const wert = useMemo(() => ({ ort: stapel[stapel.length - 1], tiefe: stapel.length, gehe, ersetze, zurueck, reiter }),
    [stapel, gehe, ersetze, zurueck, reiter]);
  return <NavCtx.Provider value={wert}>{children}</NavCtx.Provider>;
}

export function useNav(): NavZustand {
  const c = useContext(NavCtx);
  if (!c) throw new Error('useNav ausserhalb des NavProvider');
  return c;
}
