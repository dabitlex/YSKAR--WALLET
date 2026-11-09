# PROJECT_STATUS – YSKAR Financial Wallet

**Stand:** 09.11.2026 · **Zweig:** `feature/yskar-finance-v3` · **Basis:** `main` @ `2f218d0`
**Aktuelle Phase:** 1–3 abgeschlossen · 4 als Nächstes

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

**Am bestehenden Code wurde in Phase 1 nichts geändert.** Auftragsgemäß.

### Phase 2 – Produktkonzept ✅

- Modulkarte, erlaubte Abhängigkeitsrichtung und vier verbotene Importpfade
  festgeschrieben (werden in Phase 8 per Test erzwungen).
- Navigation auf vier Bereiche festgelegt.
- Schnittstelle `MarktAnbieter` und Hülle `MitHerkunft<T>` entworfen – jeder
  angezeigte Wert trägt Quelle und Alter.
- Portfolio-Datenmodell: `quelle` ist Pflichtfeld, YSR als manuelle Position
  gesperrt. Doppelzählung ist damit strukturell ausgeschlossen.
- Entscheidung Charts: eigene SVG-Umsetzung statt Bibliothek (Begründung in
  `docs/ARCHITEKTUR.md` §7).
- Ergebnis: **[`docs/ARCHITEKTUR.md`](docs/ARCHITEKTUR.md)**

### Phase 3 – Designsystem ✅

- Neue Markenpalette umgesetzt; Blau führt wieder (das Konzept „Ruhe" hatte
  es bewusst unterdrückt).
- 17 Farbregeln umgestellt, 48 harte Farbwerte auf Tokens gelegt, 33
  Cyanwerte des alten Zweitakzents auf die Marke geführt. Keine Reste.
- **Kontraste gerechnet, nicht geschätzt.** Dabei fiel auf, dass `#2F7BFF`
  weißen Text nur mit 3,62 : 1 trägt und damit unter AA liegt – gelöst über
  drei Blaurollen, ohne die Markenfarbe zu verlassen.
- Nativer Starthintergrund mitgezogen, sonst springt die Farbe beim Start.
- Ergebnis: **[`docs/DESIGNSYSTEM.md`](docs/DESIGNSYSTEM.md)**

---

## Aktueller Entwicklungsstand

| Bereich | Stand |
|---|---|
| Analyse | ✅ abgeschlossen |
| Produktarchitektur | ✅ abgeschlossen |
| Designsystem | ✅ abgeschlossen |
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
| Durchlauf in Chromium (412 × 915) | Einstieg, Wiederherstellen, PIN, Wallet, Activity, Kontakte, Einstellungen – **ohne Konsolenfehler** |
| Live-Daten im Durchlauf | `200 /api/v2/account/…`, `200 /api/v2/summary` |
| Native APK in dieser Sitzung | **nicht möglich** – kein Android SDK; Bau läuft über GitHub Actions |

---

## Bekannte Probleme

| # | Problem | Schwere | Status |
|---|---|---|---|
| P1 | **Konsensfassung 6 aktiviert bei Block 10 000**, Netz steht bei 8 410 → ~11 Tage. | hoch | Wallet ist vorbereitet (`src/wallet/fassung.ts`, getestet). Sendepfad wird **nicht** umgebaut. Übergang am Gerät beobachten. |
| P2 | **YSR hat keinen Marktpreis.** Nicht bei CoinGecko gelistet, keine Preisquelle im YSKAR-Projekt. | hoch | Produktentscheidung: YSR-Bestand in YSR, Fiat-Summe nur über bepreiste Positionen, Quelle je Wert gekennzeichnet. Keine erfundenen Kurse. |
| P3 | **CoinGecko ohne API-Schlüssel unzuverlässig**: gemessen 6 × HTTP 200 / 6 × HTTP 429 bei 12 sequenziellen Aufrufen. | hoch | **Entschieden:** Demo-Schlüssel, in der App eingegeben und im sicheren Speicher gehalten – nicht im Build. Ohne Schlüssel läuft die App gegen die öffentliche API, mit Hinweis. Cache + Coalescing + Backoff in jedem Fall. |
| P4 | **Kein Android SDK / kein adb** in dieser Umgebung. | mittel | Bau über die bestehende GitHub-Actions-Pipeline. Oberflächentests hier über Chromium/Playwright bei 412 × 915. |
| P5 | ~~Designkonzept „Ruhe" unterdrückt Blau~~ | – | **Erledigt in Phase 3.** |
| P7 | `#2F7BFF` trägt weißen Text nur mit 3,62 : 1 und liegt damit unter WCAG AA. | mittel | Gelöst über drei Blaurollen: Marke/UI `#2F7BFF`, Knopffüllung `#2563D6` (5,48 : 1), Blau-als-Text `#6BA2FF`. |
| P6 | PIN-Bremse liegt in `localStorage`, nicht im sicheren Speicher – zurücksetzbar. | niedrig | Kein kritischer Fund (Schutz hängt an PBKDF2/400 000). Bei Überarbeitung mitnehmen. |

---

## Offene Aufgaben

**Phase 4 – Wallet** (nächster Schritt)
- [ ] Netzdaten anbinden: Höhe, Hashrate, aktive Miner, Belohnung, Umlauf
      (liegen bereits im Spiegel, werden heute nur nicht gelesen)
- [ ] Navigation auf Wallet / Markets / Activity / Profile umstellen;
      Kontakte ziehen in den Wallet-Bereich
- [ ] Wallet-Übersicht nach Entwurf (YSR groß, Netzband, getrennter Fiat-Teil)
- [ ] Sendepfad **unverändert** übernehmen

**Phase 5–10** – siehe Auftrag.

---

## Nächste Entwicklungsschritte

**Getroffene Entscheidungen (09.11.2026):**

1. **Marktdaten:** CoinGecko mit kostenlosem Demo-Schlüssel. Der Schlüssel
   wird in der App eingegeben und im sicheren Speicher gehalten, **nie im
   Build**. Ohne Schlüssel arbeitet die App gegen die öffentliche API und
   weist auf die eingeschränkte Zuverlässigkeit hin.
2. **YSR-Wert:** getrennter Ausweis. YSR-Bestand groß in YSR, daneben die
   Netzdaten; die Fiat-Summe umfasst ausschließlich bepreiste Positionen.
   An der Kursstelle steht, dass YSR noch nicht notiert ist. Es gibt bewusst
   keine Gesamtsumme über beides – sie wäre erfunden.

**Als Nächstes:** Phase 4 (Wallet).

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
