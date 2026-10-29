// Prueft das fertig gebaute Buendel (dist/), bevor daraus eine APK wird.
//
//   1. Nur die Hauptkette: Kein Wort von einem Test- oder Entwicklernetz und
//      keine Adresse eines Entwicklerrechners darf im ausgelieferten Code stehen.
//   2. Nur die bekannten Gegenstellen: Der Code darf keine anderen Server
//      ansprechen als Spiegel, Knoten und Explorer.
//   3. Nichts wird nachgeladen: Die Startseite bindet nur eigene Dateien ein.
import { readFileSync, readdirSync, statSync } from 'node:fs';
import { join } from 'node:path';

const WURZEL = new URL('../dist/', import.meta.url).pathname;
const ERLAUBT = new Set(['yskar.vercel.app', 'pool.yskar.app', 'www.yskar.app', 'react.dev', 'www.w3.org']);
const VERBOTEN = [/regtest/i, /testnet/i, /localhost/i, /127\.0\.0\.1/, /VITE_SPIEGEL/, /VITE_KNOTEN/];

const dateien = [];
(function lauf(ordner) {
  for (const n of readdirSync(ordner)) {
    const p = join(ordner, n);
    if (statSync(p).isDirectory()) lauf(p); else dateien.push(p);
  }
})(WURZEL);

const maengel = [];
let netz = false;
for (const p of dateien.filter(d => /\.(js|html|css)$/.test(d))) {
  const text = readFileSync(p, 'utf8');
  const kurz = p.slice(WURZEL.length);
  for (const v of VERBOTEN) if (v.test(text)) maengel.push(`${kurz}: enthaelt ${v}`);
  for (const m of text.matchAll(/https?:\/\/([a-zA-Z0-9.-]+)/g)) {
    if (!ERLAUBT.has(m[1])) maengel.push(`${kurz}: fremde Gegenstelle ${m[1]}`);
    if (m[0].startsWith('http://') && m[1] !== 'www.w3.org') maengel.push(`${kurz}: unverschluesselt ${m[0]}`);
  }
  if (text.includes('yskar-main-1')) netz = true;
}
const start = readFileSync(join(WURZEL, 'index.html'), 'utf8');
for (const m of start.matchAll(/(?:src|href)="([^"]+)"/g)) {
  if (/^(https?:)?\/\//.test(m[1])) maengel.push(`index.html laedt von aussen: ${m[1]}`);
}
if (!netz) maengel.push('Die Kennung der Hauptkette (yskar-main-1) fehlt im Buendel.');

if (maengel.length) {
  console.error('Buendel NICHT in Ordnung:\n  ' + maengel.join('\n  '));
  process.exit(1);
}
console.log(`Buendel in Ordnung: ${dateien.length} Dateien, nur Hauptkette, nur bekannte Gegenstellen.`);
