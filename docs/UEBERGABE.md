# Technische Übergabe

Stand: 09.11.2026 · Zweig `feature/yskar-finance-v3` · Basis `main` @ `2f218d0`

---

## 1. Was gemacht wurde

Aus der bestehenden YSKAR Wallet ist eine Finanzplattform geworden, ohne
die Wallet selbst anzutasten. **Der Sendepfad, der Tresor und der Kern sind
unverändert.**

| | |
|---|---|
| Geänderte Dateien | 18 |
| Neue Dateien | 35 |
| Zeilen | +6 229 / −95 |
| Tests | **183** in 18 Dateien (vorher 78) |
| Gebaute Größe | 609 kB JS (**199 kB gzip**), 105 kB CSS (20 kB gzip) |
| Zuwachs gegenüber `main` | **+17 kB gzip** für Markets, Portfolio, Watchlist und Charts zusammen |

### Neu

- **Markets** – Marktüberblick, 100 Kryptowährungen mit Mini-Charts, sechs
  Filter, Suche, Coin-Detailseite mit sieben Zeiträumen.
- **Charts** – eigene SVG-Umsetzung, interaktiv mit Fadenkreuz. Keine
  Chart-Bibliothek (Begründung: `ARCHITEKTUR.md` §7).
- **Portfolio** – On-Chain-Bestand und manuelle Positionen, strikt getrennt.
- **Watchlist** – eigene Reihenfolge, lokal gespeichert.
- **Netzdaten in der Wallet** – Block, Hashrate, Miner, Belohnung, Umlauf.
- **Einstellungen** – Fiat-Währung, Marktdaten-Schlüssel.
- **Aktivität** – Zeitraumfilter.
- **Designsystem** – neue Markenpalette, Blau führt.

### Unverändert, mit Absicht

| Bereich | Warum |
|---|---|
| `src/kern/*` | Per Prüfsumme an `dabitlex/YSKAR` gebunden |
| `src/wallet/fassung.ts` + Sendepfad | Entscheidet über die Funktionsfähigkeit nach dem Hard Fork |
| `src/wallet/tresor.ts` | Format `yskar.tresor.v1` – bestehende Wallets müssen wiederherstellbar bleiben |
| Eigene Tastatur, Sichtschutz, Keystore | Sicherheitsmaßnahmen, die nicht nachgebaut werden |

---

## 2. Die drei Entscheidungen, die alles prägen

### YSR hat keinen Kurs – und das steht überall so da

Belegt: nicht bei CoinGecko gelistet, keine Preisquelle im gesamten
YSKAR-Projekt (nur Mengen-Endpunkte „für Datenseiten wie CoinMarketCap").

Konsequenz an jeder Stelle:
- Die Wallet zeigt YSR in **YSR**, nie in Euro.
- Das Portfolio weist **zwei Zahlen nebeneinander** aus und bildet
  **bewusst keine Gesamtsumme**.
- In Markets steht YSKAR an erster Stelle, mit Blockhöhe statt Kurs.

Gibt es später eine offizielle Preisquelle, wird sie als weiterer Anbieter
in dieselbe Schnittstelle gesteckt. Die Architektur sieht das vor.

### Kein API-Schlüssel im Build

Eine APK ist eine ZIP-Datei; ein eingebauter Schlüssel wäre binnen Minuten
öffentlich und das Kontingent aller Nutzer würde an ihm hängen. Deshalb
trägt der Nutzer seinen eigenen kostenlosen Demo-Schlüssel ein; er liegt im
Android Keystore. **Ohne Schlüssel läuft die App trotzdem** – gegen die
öffentliche Schnittstelle, mit sichtbarem Hinweis.

### Markt und Wallet sind getrennt – und das wird geprüft

`tests/grenzen.test.ts` verbietet, dass die Marktschicht Tresor, Wörter
oder Signierfunktionen erreicht – per Import **und** dem Namen nach. Der
Test wurde gegengeprüft: Mit einem absichtlich eingebauten Verstoß schlägt
er fehl.

---

## 3. Was geprüft ist – und was nicht

### Automatisch

| Prüfung | Ergebnis |
|---|---|
| `npm test` | **183 / 183** |
| `npm run build` | erfolgreich, `tsc --noEmit` sauber |
| `node werkzeug/buendel-pruefen.mjs` | in Ordnung, nur bekannte Gegenstellen |
| `npm run durchlauf` | **22 Bildschirme, 0 ohne Inhalt, 0 Meldungen** |
| `npm run durchlauf:live` | 22 Bildschirme; der Chart bleibt bei HTTP 429 leer – ohne Schlüssel erwartbar |

### Nicht geprüft – braucht Hardware

Fingerabdruck, Keystore, Sichtschutz, Kamera, Benachrichtigungen,
Installation über eine ältere Fassung. **Liste: `docs/GERAETETEST.md`.**
Sie ist vor dem Veröffentlichen einmal vollständig durchzugehen.

### Nicht geprüft – braucht einen echten Vorgang

`POST /api/v2/tx` wurde **nie** aufgerufen. Es wurde keine Überweisung in
der produktiven Kette gesendet, auch nicht zum Testen.

---

## 4. Zeitkritisch: Konsensfassung 6

**Aktivierung bei Block 10 000.** Am 09.11.2026 stand das Netz bei **8 499**
– rund **1 500 Blöcke**, bei 600 s Zielzeit etwa **10 Tage**.

Die Wallet ist vorbereitet (`src/wallet/fassung.ts`, `tests/fassung.test.ts`):
Sie holt die Fassung vom Knoten, pausiert in den letzten drei Blöcken davor
und **sperrt das Senden, wenn der Knoten nicht antwortet**, statt zu raten.

**Daran darf nichts geändert werden, ohne `tests/fassung.test.ts` zu
verstehen.** Abschnitt 8 des Gerätetests gehört an den Aktivierungstag.

---

## 5. Veröffentlichen – die Schritte

Das ist bewusst **nicht** automatisch passiert: Ein Release geht an echte
Nutzer einer echten Wallet.

1. **Gerätetest** durchführen (`docs/GERAETETEST.md`), besonders 1.2.
2. Zweig prüfen und nach `main` bringen (Pull Request oder Merge).
3. In `package.json` die Version erhöhen, z. B. `2.1.0`.
4. Tag setzen:

```bash
git tag v2.1.0 && git push origin v2.1.0
```

5. GitHub baut, signiert und legt das Release mit Prüfsumme und
   Fingerabdruck an.

Dazwischen entstehen jederzeit Test-APKs: **Actions → App bauen → Run
workflow**, Feld „veroeffentlichen" leer lassen.

---

## 6. Wo was liegt

```
src/kern/        Krypto, Adressen, Transaktionen   UNVERÄNDERT
src/wallet/      Tresor, Biometrie, Fassung        Sendepfad unverändert
src/netz/        YSKAR-Knoten: Konto, Verlauf, Gebühren, Netzdaten
src/markt/       Marktdaten: Anbieter, Cache, Schlüssel, Format
src/portfolio/   Modell, Bewertung, Ablage
src/watchlist/   Favoriten
src/diagramm/    Charts (Rechnung in pfad.ts, getestet)
src/seiten/      Bildschirme
src/ui/          Bausteine
werkzeug/        buendel-pruefen.mjs, durchlauf.mjs
docs/            BESTANDSAUFNAHME, ARCHITEKTUR, DESIGNSYSTEM,
                 GERAETETEST, ENTWICKLUNG, UEBERGABE
```

---

## 7. Offene Punkte

| # | Punkt | Einordnung |
|---|---|---|
| 1 | Gerätetest nicht durchgeführt | **Vor dem Release erledigen** |
| 2 | Verhalten ab Block 10 000 real ungeprüft | Am Aktivierungstag beobachten |
| 3 | Ohne Markt-Schlüssel oft HTTP 429 | Gemessen; das Schlüsselfeld löst es |
| 4 | Kein historischer Portfolio-Verlauf vor heute | Bewusst: wäre Hochrechnung, keine Messung |
| 5 | CoinGecko führt keine deutschen Coin-Beschreibungen | Englischer Text wird als solcher gekennzeichnet |
| 6 | PIN-Bremse liegt in `localStorage` | Kein kritischer Fund; Schutz hängt an PBKDF2/400 000 |
| 7 | Durchlauf läuft nicht in CI | Chromium kostet Minuten; Begründung in `ENTWICKLUNG.md` |

---

## 8. Wenn jemand hier weiterarbeitet

- `main` wurde nicht angefasst. Alles liegt auf `feature/yskar-finance-v3`.
- Jede Phase ist ein eigener Commit mit Begründung in der Nachricht.
- Vor jeder Änderung: `npm test && npm run build && node werkzeug/buendel-pruefen.mjs`
- Nach Oberflächenänderungen zusätzlich: `npm run durchlauf`
- Eine neue Gegenstelle **muss** in `werkzeug/buendel-pruefen.mjs`
  eingetragen werden. Die Prüfung hat das in diesem Projekt zweimal
  korrekt bemerkt – sie ist kein Formalismus.
