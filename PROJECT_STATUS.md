# PROJECT_STATUS – YSKAR Financial Wallet

**Stand:** 09.11.2026 · **Zweig:** `feature/yskar-finance-v3` · **Basis:** `main` @ `2f218d0`
**Aktuelle Phase:** 1 abgeschlossen · 2 vorbereitet

---

## Abgeschlossene Arbeiten

### Phase 1 – Technische Analyse ✅

- Repository vollständig analysiert (`src/` 12 000 Zeilen, 8 Kerndateien,
  12 Seiten, 11 Testdateien).
- Zweites Repository `dabitlex/YSKAR` (Kern + Node + Doku, 4 132 Zeilen
  Konsensdokumentation) als Referenz hinzugezogen.
- Baseline **verifiziert**, nicht angenommen: 78/78 Tests grün, Bau
  erfolgreich, Bündelprüfung in Ordnung.
- YSKAR-Node-Schnittstellen **live geprüft** gegen `yskar.vercel.app`.
- Marktdatenanbieter CoinGecko / CoinMarketCap geprüft, Rate-Limit **gemessen**.
- Entwicklungsumgebung inventarisiert, Build-Strategie festgelegt.
- Ergebnis: **[`docs/BESTANDSAUFNAHME.md`](docs/BESTANDSAUFNAHME.md)**

**Am bestehenden Code wurde nichts geändert.** Auftragsgemäß.

---

## Aktueller Entwicklungsstand

| Bereich | Stand |
|---|---|
| Analyse | ✅ abgeschlossen |
| Produktarchitektur | 🔵 in Arbeit |
| Designsystem | ⚪ offen |
| Wallet | ✅ funktionsfähig (Bestand, unverändert) |
| Markets | ⚪ offen |
| Portfolio / Watchlist | ⚪ offen |
| Activity | 🟡 Bestand vorhanden, Ausbau offen |
| Profile / Settings | 🟡 Bestand vorhanden, Ausbau offen |

---

## Testergebnisse

Ausgeführt am 09.11.2026 auf `feature/yskar-finance-v3`:

| Prüfung | Ergebnis |
|---|---|
| `npm test` | **78 / 78 bestanden** (1,2 s) |
| `npm run build` (inkl. `tsc --noEmit`) | **erfolgreich** (2,7 s) |
| `node werkzeug/buendel-pruefen.mjs` | **in Ordnung** – 25 Dateien |
| Gebaute Größe | 556 kB JS (182 kB gzip), 91 kB CSS (18 kB gzip) |
| Live-Prüfung `yskar.vercel.app` | `summary`, `supply`, `fees` je **HTTP 200** |
| Native APK in dieser Sitzung | **nicht möglich** – kein Android SDK (siehe unten) |

---

## Bekannte Probleme

| # | Problem | Schwere | Status |
|---|---|---|---|
| P1 | **Konsensfassung 6 aktiviert bei Block 10 000**, Netz steht bei 8 410 → ~11 Tage. | hoch | Wallet ist vorbereitet (`src/wallet/fassung.ts`, getestet). Sendepfad wird **nicht** umgebaut. Übergang am Gerät beobachten. |
| P2 | **YSR hat keinen Marktpreis.** Nicht bei CoinGecko gelistet, keine Preisquelle im YSKAR-Projekt. | hoch | Produktentscheidung: YSR-Bestand in YSR, Fiat-Summe nur über bepreiste Positionen, Quelle je Wert gekennzeichnet. Keine erfundenen Kurse. |
| P3 | **CoinGecko ohne API-Schlüssel unzuverlässig**: gemessen 6 × HTTP 200 / 6 × HTTP 429 bei 12 sequenziellen Aufrufen. | hoch | Cache + Coalescing + Backoff zwingend. Produktive Bezugsquelle: **Entscheidung offen** (siehe Nächste Schritte). |
| P4 | **Kein Android SDK / kein adb** in dieser Umgebung. | mittel | Bau über die bestehende GitHub-Actions-Pipeline. Oberflächentests hier über Chromium/Playwright bei 412 × 915. |
| P5 | Designkonzept „Ruhe" unterdrückt Blau bewusst; neue Richtung verlangt Blau durchgehend. | mittel | Richtungsumkehr, keine reine Variablenänderung. In Phase 3 einzuplanen. |
| P6 | PIN-Bremse liegt in `localStorage`, nicht im sicheren Speicher – zurücksetzbar. | niedrig | Kein kritischer Fund (Schutz hängt an PBKDF2/400 000). Bei Überarbeitung mitnehmen. |

---

## Offene Aufgaben

**Phase 2 – Produktkonzept** (nächster Schritt)
- [ ] Modulgrenzen und Datenfluss festschreiben
- [ ] Navigationsstruktur 4 Bereiche (Wallet, Markets, Activity, Profile)
- [ ] Schnittstelle `MarktAnbieter` definieren (austauschbar)
- [ ] Portfolio-Datenmodell mit Pflichtfeld „onchain | manuell"

**Phase 3 – Designsystem**
- [ ] Palette, Typografie, Abstände, Komponenten
- [ ] Screens

**Phase 4–10** – siehe Auftrag.

---

## Nächste Entwicklungsschritte

1. **Entscheidung notwendig (blockiert Phase 5):** Bezugsquelle der Marktdaten.
   - **A** Nur öffentliche CoinGecko-API, kein Schlüssel. Kein Betriebsaufwand,
     aber gemessen 50 % Fehlerquote; Charts und Listen laden teils verzögert
     oder zeigen den letzten bekannten Stand.
   - **B** Kostenloser CoinGecko-Demo-Schlüssel, in der App einmal eingegeben
     und im sicheren Speicher gehalten – **nicht** im Build. Zuverlässig,
     aber der Nutzer muss einen Schlüssel besorgen.
   - **C** Eigener Proxy neben `yskar.vercel.app`, Schlüssel serverseitig,
     Antworten zentral zwischengespeichert. Technisch die beste Lösung,
     erfordert aber Betrieb und Deployment.
   - **Empfehlung: C**, mit A als automatischem Rückfall. Das hält die Vorgabe
     „kein Schlüssel in der APK" ein, liefert verlässliche Daten und lässt die
     App auch ohne Proxy funktionieren.
2. Phase 2 ausarbeiten (unabhängig von dieser Entscheidung).
3. Phase 3 Designsystem.

---

## Arbeitsregeln in diesem Projekt

- `main` wird nicht angefasst. Arbeit ausschließlich auf
  `feature/yskar-finance-v3`.
- `src/kern/*` wird nicht von Hand geändert (Prüfsummenbindung an
  `dabitlex/YSKAR`).
- `src/wallet/fassung.ts` und der Sendepfad bleiben unverändert – sie
  entscheiden über die Funktionsfähigkeit nach dem Hard Fork.
- Tresorformat `yskar.tresor.v1` bleibt kompatibel. Bestehende Wallets müssen
  nach dem Update wiederherstellbar sein.
- Keine erfundenen Endpunkte, keine erfundenen Kurse, keine simulierten
  Transaktionen. Fehlt ein Wert, wird das angezeigt.
- Kleine, nachvollziehbare Commits. Tests nach jeder Phase.
