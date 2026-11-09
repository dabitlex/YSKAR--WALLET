/**
 * Diagramm -- die Rechnung.
 *
 * Reine Funktionen, kein React, kein DOM. Deshalb vollstaendig testbar:
 * Ein Chart, der die falsche Linie zeichnet, ist in einer Finanz-App ein
 * Fehler wie ein falscher Betrag.
 *
 * Warum eigene Pfade und keine Chart-Bibliothek: siehe
 * docs/ARCHITEKTUR.md Abschnitt 7. Kurz -- eigene Gestaltung ohne fremdes
 * Markup, kein dreistelliger kB-Aufschlag und eine Abhaengigkeit weniger
 * in einer Wallet.
 */

export type Punkt = [zeit: number, wert: number];

export interface Rahmen {
  breite: number;
  hoehe: number;
  /** Platz oben und unten, damit die Linie nicht am Rand klebt. */
  polster: number;
}

export interface Skala {
  x(zeit: number): number;
  y(wert: number): number;
  minWert: number;
  maxWert: number;
  minZeit: number;
  maxZeit: number;
}

/**
 * Abbildung von Werten auf Bildpunkte.
 *
 * Die Y-Achse beginnt NICHT bei null. Bei einem Kurs, der zwischen 66.000
 * und 67.000 schwankt, waere eine Achse ab null eine gerade Linie und
 * verschwiege genau die Bewegung, die interessiert. Boersencharts machen
 * das ueberall so; wichtig ist nur, dass die Achse beschriftet ist.
 *
 * Sind alle Werte gleich, liegt die Linie in der Mitte statt durch eine
 * Division durch null zu verschwinden.
 */
export function skala(punkte: Punkt[], r: Rahmen): Skala | null {
  if (punkte.length === 0) return null;
  let minW = Infinity, maxW = -Infinity, minZ = Infinity, maxZ = -Infinity;
  for (const [z, w] of punkte) {
    if (w < minW) minW = w;
    if (w > maxW) maxW = w;
    if (z < minZ) minZ = z;
    if (z > maxZ) maxZ = z;
  }
  if (!Number.isFinite(minW) || !Number.isFinite(maxW)) return null;

  const spanneW = maxW - minW;
  const spanneZ = maxZ - minZ;
  const nutz = Math.max(1, r.hoehe - 2 * r.polster);

  return {
    minWert: minW, maxWert: maxW, minZeit: minZ, maxZeit: maxZ,
    x: (z: number) => (spanneZ === 0 ? r.breite / 2 : ((z - minZ) / spanneZ) * r.breite),
    y: (w: number) => (spanneW === 0 ? r.hoehe / 2 : r.polster + (1 - (w - minW) / spanneW) * nutz),
  };
}

/**
 * Linie als SVG-Pfad.
 *
 * Bewusst gerade Strecken statt geglaetteter Kurven: Eine Glaettung
 * erfindet zwischen zwei Messpunkten Werte, die es nicht gab, und kann
 * ueber ein Hoch oder Tief hinwegschwingen. Bei Kursen ist das die
 * falsche Entscheidung.
 */
export function linienPfad(punkte: Punkt[], s: Skala): string {
  if (punkte.length === 0) return '';
  if (punkte.length === 1) {
    const [z, w] = punkte[0];
    return `M ${s.x(z).toFixed(2)} ${s.y(w).toFixed(2)}`;
  }
  let d = '';
  for (let i = 0; i < punkte.length; i++) {
    const [z, w] = punkte[i];
    d += (i === 0 ? 'M ' : ' L ') + s.x(z).toFixed(2) + ' ' + s.y(w).toFixed(2);
  }
  return d;
}

/** Dieselbe Linie, unten geschlossen -- fuer die getoente Flaeche darunter. */
export function flaechenPfad(punkte: Punkt[], s: Skala, hoehe: number): string {
  if (punkte.length === 0) return '';
  const linie = linienPfad(punkte, s);
  const ersteX = s.x(punkte[0][0]).toFixed(2);
  const letzteX = s.x(punkte[punkte.length - 1][0]).toFixed(2);
  return `${linie} L ${letzteX} ${hoehe.toFixed(2)} L ${ersteX} ${hoehe.toFixed(2)} Z`;
}

/**
 * Der Punkt, der einer Bildschirmposition am naechsten liegt -- fuer das
 * Fadenkreuz beim Wischen. Binaere Suche, damit auch 721 Punkte bei jeder
 * Fingerbewegung ohne Ruckeln gefunden werden.
 */
export function punktBeiX(punkte: Punkt[], s: Skala, bildX: number): number {
  if (punkte.length === 0) return -1;
  let lo = 0, hi = punkte.length - 1;
  while (lo < hi) {
    const mitte = (lo + hi) >> 1;
    if (s.x(punkte[mitte][0]) < bildX) lo = mitte + 1; else hi = mitte;
  }
  // Der Nachbar links kann naeher liegen als der gefundene.
  if (lo > 0) {
    const a = Math.abs(s.x(punkte[lo - 1][0]) - bildX);
    const b = Math.abs(s.x(punkte[lo][0]) - bildX);
    if (a <= b) return lo - 1;
  }
  return lo;
}

/**
 * Veraenderung vom ersten zum letzten Punkt, in Prozent.
 * null, wenn der Anfangswert 0 ist -- eine Veraenderung gegenueber null
 * ist keine Prozentzahl, sondern eine Division durch null.
 */
export function veraenderung(punkte: Punkt[]): number | null {
  if (punkte.length < 2) return null;
  const a = punkte[0][1];
  const b = punkte[punkte.length - 1][1];
  if (a === 0) return null;
  return ((b - a) / Math.abs(a)) * 100;
}

/**
 * Zu viele Punkte duennen -- gleichmaessig, aber der LETZTE bleibt immer
 * erhalten. Ohne das zeigte der Chart am rechten Rand einen Kurs von
 * vorhin statt den aktuellen.
 */
export function duennen(punkte: Punkt[], hoechstens: number): Punkt[] {
  if (hoechstens < 2 || punkte.length <= hoechstens) return punkte;
  const schritt = (punkte.length - 1) / (hoechstens - 1);
  const raus: Punkt[] = [];
  for (let i = 0; i < hoechstens - 1; i++) raus.push(punkte[Math.round(i * schritt)]);
  raus.push(punkte[punkte.length - 1]);
  return raus;
}

/** Zahlenreihe (Sparkline) in Punkte mit gleichem Abstand. */
export function ausReihe(werte: number[]): Punkt[] {
  return werte.map((w, i) => [i, w] as Punkt);
}
